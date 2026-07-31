package com.kite.app.foundation.runtime

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import com.kite.app.foundation.workspace.WorkSurfaceRuntimeBridge
import java.io.ByteArrayOutputStream
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Debug-only、无参数、固定命令的 ADB 验收入口；不会接受或转发外部命令。 */
class WarmProotRunnerBenchmarkReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val report = WarmProotRunnerBenchmark.run(context.applicationContext)
                Log.i(LOG_TAG, report)
            } catch (error: Throwable) {
                Log.e(LOG_TAG, "status=failed reason=${safeToken(error.message ?: error.javaClass.simpleName)}", error)
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val ACTION = "com.kite.app.debug.WARM_PROOT_RUNNER_BENCHMARK"
        const val LOG_TAG = "[KFShell]WarmRunnerBenchmark"

        fun safeToken(value: String): String = value
            .take(160)
            .map { character -> if (character.isLetterOrDigit() || character in "-_.:") character else '_' }
            .joinToString("")
    }
}

private object WarmProotRunnerBenchmark {
    private const val ROUNDS = 8
    private const val EXPECTED = "KFR_BENCH_OK"
    private val COMMAND = listOf("/usr/bin/printf", EXPECTED)

    fun run(context: Context): String {
        val independent = List(ROUNDS) { executeIndependent(context) }
        val controller = WarmProotRunnerController(
            processFactory = { WorkSurfaceRuntimeBridge.startWarmProotRunnerProcess(context) }
        )
        return controller.use {
            val coldStarted = SystemClock.elapsedRealtime()
            val cold = controller.executeBlocking(request("cold"))
            val coldMs = SystemClock.elapsedRealtime() - coldStarted
            requireValid(cold)

            val warm = List(ROUNDS) { index ->
                val started = SystemClock.elapsedRealtime()
                val result = controller.executeBlocking(request("warm-$index"))
                val elapsed = SystemClock.elapsedRealtime() - started
                requireValid(result)
                elapsed
            }
            val timeout = controller.executeBlocking(
                WarmProotJobRequest(
                    jobId = "timeout-contract",
                    argv = listOf("/bin/sh", "-c", "sleep 5"),
                    timeoutMs = 100L,
                    maxOutputBytesPerStream = 1024,
                )
            )
            check(timeout.timedOut && timeout.started && !timeout.fallbackAllowed) { "timeout_contract_failed" }
            val recovery = controller.executeBlocking(request("after-timeout"))
            requireValid(recovery)

            val independentMedian = median(independent)
            val warmMedian = median(warm)
            val reduction = if (independentMedian <= 0L) 0.0 else
                (independentMedian - warmMedian) * 100.0 / independentMedian
            "status=ok rounds=$ROUNDS independentMs=${independent.joinToString(",")} " +
                "independentMedianMs=$independentMedian coldRunnerMs=$coldMs " +
                "warmMs=${warm.joinToString(",")} warmMedianMs=$warmMedian " +
                "reductionPct=${String.format(Locale.US, "%.1f", reduction)} " +
                "timeout=true recovery=true runnerPid=${recovery.runnerPid}"
        }
    }

    private fun request(jobId: String) = WarmProotJobRequest(
        jobId = jobId,
        argv = COMMAND,
        timeoutMs = 5_000L,
        maxOutputBytesPerStream = 1024,
    )

    private fun requireValid(result: WarmProotJobExecution) {
        check(result.succeeded) { "job_failed_${result.failureKind}_${result.exitCode}" }
        check(result.stdoutTail.toString(Charsets.UTF_8) == EXPECTED) { "stdout_mismatch" }
        check(result.stderrTail.isEmpty()) { "stderr_not_empty" }
    }

    private fun executeIndependent(context: Context): Long {
        val config = WorkSurfaceRuntimeBridge.buildArgvExecConfig(
            context = context,
            argv = COMMAND,
        )
        val started = SystemClock.elapsedRealtime()
        val process = ProcessBuilder(config.command)
            .redirectErrorStream(false)
            .apply { environment().putAll(config.env) }
            .start()
        val stdout = ByteArrayOutputStream()
        val stderr = ByteArrayOutputStream()
        val outReader = thread(start = true, isDaemon = true) { process.inputStream.use { it.copyTo(stdout) } }
        val errReader = thread(start = true, isDaemon = true) { process.errorStream.use { it.copyTo(stderr) } }
        val finished = process.waitFor(10L, TimeUnit.SECONDS)
        if (!finished) process.destroyForcibly()
        outReader.join(1000L)
        errReader.join(1000L)
        check(finished && process.exitValue() == 0) { "independent_failed" }
        check(stdout.toString(Charsets.UTF_8.name()) == EXPECTED) { "independent_stdout_mismatch" }
        check(stderr.size() == 0) { "independent_stderr_not_empty" }
        return SystemClock.elapsedRealtime() - started
    }

    private fun median(values: List<Long>): Long = values.sorted()[values.size / 2]
}
