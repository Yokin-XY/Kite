package com.kite.app.foundation.runtime

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import com.kite.app.foundation.contracts.ContainerExecConfig
import com.kite.app.foundation.workspace.WorkSurfaceRuntimeBridge
import java.io.ByteArrayOutputStream
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlin.math.ceil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Debug-only 固定启动窗口矩阵；不接收命令、路径、并发、窗口或业务身份。 */
class ProotLaunchWindowBenchmarkReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION) return
        context.startService(Intent(context, ProotLaunchWindowBenchmarkService::class.java))
    }

    internal companion object {
        const val ACTION = "com.kite.app.debug.PROOT_LAUNCH_WINDOW_BENCHMARK"
        const val LOG_TAG = "[KFShell]ProotLaunchWindow"

        fun safe(value: String): String = value.take(180).map { character ->
            if (character.isLetterOrDigit() || character in "-_.:") character else '_'
        }.joinToString("")
    }
}

class ProotLaunchWindowBenchmarkService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!running.compareAndSet(false, true)) return START_NOT_STICKY
        scope.launch {
            try {
                ProotLaunchWindowBenchmark.run(applicationContext).forEach { report ->
                    Log.i(ProotLaunchWindowBenchmarkReceiver.LOG_TAG, report)
                }
            } catch (error: Throwable) {
                Log.e(
                    ProotLaunchWindowBenchmarkReceiver.LOG_TAG,
                    "status=failed reason=${ProotLaunchWindowBenchmarkReceiver.safe(error.message ?: error.javaClass.simpleName)}",
                    error,
                )
            } finally {
                running.set(false)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private companion object {
        val running = AtomicBoolean(false)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}

private object ProotLaunchWindowBenchmark {
    private const val READY_TOKEN = "KF_LAUNCH_READY"
    private const val ROUNDS = 3
    private const val READY_TIMEOUT_MS = 8_000L
    private const val EXIT_TIMEOUT_MS = 8_000L
    private const val FUTURE_TIMEOUT_MS = 15_000L
    private val CONCURRENCY_LEVELS = listOf(1, 2, 4, 8)
    private val WINDOW_WIDTHS = listOf(1, 2, 4)
    private val COMMAND = listOf(
        "/bin/sh",
        "-c",
        "printf '$READY_TOKEN\\n'; /bin/sleep 0.25",
    )

    private enum class ReleaseBoundary(val value: String) {
        UNBOUNDED("unbounded"),
        START_RETURN("start_return"),
        READY("ready"),
    }

    private data class Case(
        val boundary: ReleaseBoundary,
        val concurrency: Int,
        val window: Int,
    ) {
        val key: String get() = "${boundary.value}:$concurrency:$window"
    }

    private data class Item(
        val queueWaitMs: Long,
        val startCallMs: Long,
        val readyFromRequestMs: Long,
        val totalMs: Long,
        val succeeded: Boolean,
        val residual: Boolean,
        val reason: String,
    )

    private data class Batch(
        val wallMs: Long,
        val items: List<Item>,
    )

    fun run(context: Context): List<String> {
        repeat(4) { index ->
            val warmup = runBatch(
                configs = configs(context, 1),
                case = Case(ReleaseBoundary.UNBOUNDED, 1, 1),
            )
            check(warmup.items.single().succeeded) { "rf1120_warmup_failed_$index" }
        }

        val cases = buildList {
            CONCURRENCY_LEVELS.forEach { concurrency ->
                add(Case(ReleaseBoundary.UNBOUNDED, concurrency, concurrency))
                WINDOW_WIDTHS.forEach { width ->
                    add(Case(ReleaseBoundary.START_RETURN, concurrency, width))
                }
                WINDOW_WIDTHS.forEach { width ->
                    add(Case(ReleaseBoundary.READY, concurrency, width))
                }
            }
        }
        val batches = linkedMapOf<String, MutableList<Batch>>()
        repeat(ROUNDS) { round ->
            val offset = (round * 9) % cases.size
            val ordered = cases.drop(offset) + cases.take(offset)
            ordered.forEach { case ->
                val batch = runBatch(configs(context, case.concurrency), case)
                batches.getOrPut(case.key) { mutableListOf() } += batch
                Thread.sleep(50L)
            }
        }

        return buildList {
            cases.forEach { case ->
                val measured = checkNotNull(batches[case.key])
                val items = measured.flatMap(Batch::items)
                val failures = items.count { !it.succeeded }
                val residual = items.count(Item::residual)
                check(failures == 0) {
                    "rf1120_case_failed_${case.key}_${items.firstOrNull { !it.succeeded }?.reason}"
                }
                check(residual == 0) { "rf1120_residual_process_${case.key}_$residual" }
                add(
                    "status=ok case=launch_window mode=${case.boundary.value} " +
                        "concurrency=${case.concurrency} window=${case.window} rounds=$ROUNDS " +
                        "wallMedianMs=${median(measured.map(Batch::wallMs))} " +
                        "readyP50Ms=${percentile(items.map(Item::readyFromRequestMs), 0.50)} " +
                        "readyP95Ms=${percentile(items.map(Item::readyFromRequestMs), 0.95)} " +
                        "queueP95Ms=${percentile(items.map(Item::queueWaitMs), 0.95)} " +
                        "startCallP95Ms=${percentile(items.map(Item::startCallMs), 0.95)} " +
                        "totalP95Ms=${percentile(items.map(Item::totalMs), 0.95)} " +
                        "failures=$failures residual=$residual"
                )
            }
            add("status=ok suite=rf1120_launch_window cases=${cases.size} rounds=$ROUNDS")
        }
    }

    private fun configs(context: Context, count: Int): List<ContainerExecConfig> =
        List(count) {
            WorkSurfaceRuntimeBridge.buildArgvExecConfig(
                context = context,
                argv = COMMAND,
            )
        }

    private fun runBatch(configs: List<ContainerExecConfig>, case: Case): Batch {
        check(configs.size == case.concurrency)
        val startSignal = CountDownLatch(1)
        val gate = if (case.boundary == ReleaseBoundary.UNBOUNDED) {
            null
        } else {
            Semaphore(case.window.coerceAtMost(case.concurrency), true)
        }
        val batchStarted = SystemClock.elapsedRealtime()
        val executor = Executors.newFixedThreadPool(case.concurrency)
        return try {
            val futures = configs.mapIndexed { index, config ->
                executor.submit(Callable {
                    startSignal.await()
                    executeItem(config, case.boundary, gate, batchStarted, index)
                })
            }
            startSignal.countDown()
            val items = futures.map { future ->
                future.get(FUTURE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            }
            Batch(
                wallMs = SystemClock.elapsedRealtime() - batchStarted,
                items = items,
            )
        } finally {
            executor.shutdownNow()
        }
    }

    private fun executeItem(
        config: ContainerExecConfig,
        boundary: ReleaseBoundary,
        gate: Semaphore?,
        batchStarted: Long,
        index: Int,
    ): Item {
        val requestedAt = SystemClock.elapsedRealtime()
        var permitHeld = false
        var process: Process? = null
        var stderrReader: Thread? = null
        var stdoutReader: Thread? = null
        val readyLine = AtomicReference<String?>(null)
        val readerFailure = AtomicReference<String?>(null)
        val readyAt = AtomicLong(0L)
        val readySignal = CountDownLatch(1)

        fun releasePermit() {
            if (permitHeld) {
                gate?.release()
                permitHeld = false
            }
        }

        return try {
            if (gate != null) {
                check(gate.tryAcquire(READY_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                    "launch_window_queue_timeout_$index"
                }
                permitHeld = true
            }
            val acquiredAt = SystemClock.elapsedRealtime()
            val started = ProcessBuilder(config.command)
                .redirectErrorStream(false)
                .apply { environment().putAll(config.env) }
                .start()
            process = started
            val startReturnedAt = SystemClock.elapsedRealtime()
            if (boundary == ReleaseBoundary.START_RETURN) releasePermit()

            stdoutReader = thread(start = true, isDaemon = true, name = "ProotLaunchReady-$index") {
                runCatching {
                    started.inputStream.bufferedReader().use { reader ->
                        readyLine.set(reader.readLine())
                        readyAt.set(SystemClock.elapsedRealtime())
                        readySignal.countDown()
                        while (reader.readLine() != null) Unit
                    }
                }.onFailure { error ->
                    readerFailure.set(error.javaClass.simpleName)
                    readySignal.countDown()
                }
            }
            stderrReader = thread(start = true, isDaemon = true, name = "ProotLaunchError-$index") {
                runCatching { started.errorStream.use { input -> input.copyTo(ByteArrayOutputStream()) } }
            }

            val readyObserved = readySignal.await(READY_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            if (boundary == ReleaseBoundary.READY) releasePermit()
            val readyValid = readyObserved && readyLine.get() == READY_TOKEN && readerFailure.get() == null
            val finished = started.waitFor(EXIT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            if (!finished) {
                started.destroyForcibly()
                started.waitFor(1_000L, TimeUnit.MILLISECONDS)
            }
            stdoutReader?.join(1_000L)
            stderrReader?.join(1_000L)
            val residual = started.isAlive
            val succeeded = readyValid && finished && started.exitValue() == 0 && !residual
            val observedReadyAt = readyAt.get().takeIf { it > 0L } ?: SystemClock.elapsedRealtime()
            Item(
                queueWaitMs = acquiredAt - requestedAt,
                startCallMs = startReturnedAt - acquiredAt,
                readyFromRequestMs = observedReadyAt - batchStarted,
                totalMs = SystemClock.elapsedRealtime() - batchStarted,
                succeeded = succeeded,
                residual = residual,
                reason = when {
                    !readyObserved -> "ready_timeout"
                    readyLine.get() != READY_TOKEN -> "ready_token_mismatch"
                    readerFailure.get() != null -> "reader_${readerFailure.get()}"
                    !finished -> "exit_timeout"
                    started.exitValue() != 0 -> "exit_${started.exitValue()}"
                    residual -> "residual"
                    else -> "none"
                },
            )
        } catch (error: Throwable) {
            process?.destroyForcibly()
            process?.waitFor(1_000L, TimeUnit.MILLISECONDS)
            Item(
                queueWaitMs = 0L,
                startCallMs = 0L,
                readyFromRequestMs = SystemClock.elapsedRealtime() - batchStarted,
                totalMs = SystemClock.elapsedRealtime() - batchStarted,
                succeeded = false,
                residual = process?.isAlive == true,
                reason = error.message ?: error.javaClass.simpleName,
            )
        } finally {
            releasePermit()
        }
    }

    private fun median(values: List<Long>): Long = percentile(values, 0.50)

    private fun percentile(values: List<Long>, ratio: Double): Long {
        require(values.isNotEmpty())
        val sorted = values.sorted()
        val index = ceil(sorted.size * ratio).toInt().coerceIn(1, sorted.size) - 1
        return sorted[index]
    }
}
