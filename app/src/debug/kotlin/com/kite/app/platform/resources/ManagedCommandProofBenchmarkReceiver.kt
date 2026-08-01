package com.kite.app.platform.resources

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.SystemClock
import android.system.Os
import android.util.Log
import com.kite.app.foundation.workspace.WorkSurfaceRuntimeBridge
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Debug-only 固定受管命令证明矩阵；不接收命令、路径、轮数、资源或环境参数。 */
class ManagedCommandProofBenchmarkReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION) return
        runCatching {
            context.startService(Intent(context, ManagedCommandProofBenchmarkService::class.java))
        }.onFailure { error ->
            Log.e(LOG_TAG, "status=rejected requiresForeground=true reason=${safe(error.message)}")
        }
    }

    internal companion object {
        const val ACTION = "com.kite.app.debug.MANAGED_COMMAND_PROOF_BENCHMARK"
        const val LOG_TAG = "[KFShell]ManagedCommandProof"

        fun safe(value: String?): String = value.orEmpty().take(180).map { character ->
            if (character.isLetterOrDigit() || character in "-_.:=/%") character else '_'
        }.joinToString("")
    }
}

class ManagedCommandProofBenchmarkService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!running.compareAndSet(false, true)) return START_NOT_STICKY
        scope.launch {
            try {
                ManagedCommandProofBenchmark.run(applicationContext).forEach { report ->
                    Log.i(ManagedCommandProofBenchmarkReceiver.LOG_TAG, report)
                }
            } catch (error: Throwable) {
                Log.e(
                    ManagedCommandProofBenchmarkReceiver.LOG_TAG,
                    "status=failed reason=${ManagedCommandProofBenchmarkReceiver.safe(error.message)}",
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

private object ManagedCommandProofBenchmark {
    private const val ROUNDS = 9
    private const val PROCESS_TIMEOUT_MS = 10_000L
    private const val PREFIX = "kite-rf1520"
    private val COMMANDS = listOf(
        "$PREFIX-present",
        "$PREFIX-missing",
        "$PREFIX-broken",
        "$PREFIX-nonexec",
    )

    private data class ShellExecution(
        val durationMs: Long,
        val available: Set<String>,
        val exitCode: Int,
        val stderr: String,
    )

    fun run(context: Context): List<String> {
        val container = WorkSurfaceRuntimeBridge.ensureDefaultContainer(context)
        val workspaceBin = File(container.workspacePath, ".kf/bin")
        check(workspaceBin.mkdirs() || workspaceBin.isDirectory) { "workspace_bin_unavailable" }
        val fixtures = COMMANDS.associateWith { command -> File(workspaceBin, command) }
        check(fixtures.values.none { file -> Files.exists(file.toPath(), LinkOption.NOFOLLOW_LINKS) }) {
            "fixture_collision"
        }
        prepareFixtures(fixtures)
        val measurements = try {
            shellProbe(context)
            nativeProbe(context)

            val shell = List(ROUNDS) { shellProbe(context) }
            val nativeDurationsUs = List(ROUNDS) {
                val started = SystemClock.elapsedRealtimeNanos()
                nativeProbe(context)
                (SystemClock.elapsedRealtimeNanos() - started) / 1_000L
            }
            val nativeAvailable = nativeProbe(context)
            val shellAvailable = shell.last().available
            val expected = setOf("$PREFIX-present")
            val shellFailures = shell.count { execution ->
                execution.exitCode != 0 || execution.available != expected
            }
            val nativeFalsePositives = nativeAvailable - expected
            val nativeFalseNegatives = expected - nativeAvailable
            val shellP50Ms = percentile(shell.map(ShellExecution::durationMs), 0.50)
            val shellP95Ms = percentile(shell.map(ShellExecution::durationMs), 0.95)
            val nativeP50Us = percentile(nativeDurationsUs, 0.50)
            val nativeP95Us = percentile(nativeDurationsUs, 0.95)
            listOf(
                "status=contract suite=rf1520_managed_command_proof " +
                    "expected=${expected.sorted().joinToString(",")} " +
                    "shell=${shellAvailable.sorted().joinToString(",")} " +
                    "native=${nativeAvailable.sorted().joinToString(",")} " +
                    "nativeFalsePositive=${nativeFalsePositives.sorted().joinToString(",").ifBlank { "none" }} " +
                    "nativeFalseNegative=${nativeFalseNegatives.sorted().joinToString(",").ifBlank { "none" }}",
                "status=metric suite=rf1520_managed_command_proof rounds=$ROUNDS " +
                    "shellP50Ms=$shellP50Ms shellP95Ms=$shellP95Ms " +
                    "nativeP50Us=$nativeP50Us nativeP95Us=$nativeP95Us " +
                    "shellFailures=$shellFailures",
            )
        } finally {
            fixtures.values.forEach { file -> runCatching { Files.deleteIfExists(file.toPath()) } }
        }
        val fixturesCleanedOnExit = fixtures.values.none { file ->
            Files.exists(file.toPath(), LinkOption.NOFOLLOW_LINKS)
        }
        return measurements + (
            "status=complete suite=rf1520_managed_command_proof " +
                "productionChanged=false fixturesCleanedOnExit=$fixturesCleanedOnExit"
            )
    }

    private fun prepareFixtures(fixtures: Map<String, File>) {
        fixtures.getValue("$PREFIX-present").let { file ->
            file.writeText("#!/bin/sh\nexit 0\n")
            Os.chmod(file.absolutePath, 0b111101101)
        }
        fixtures.getValue("$PREFIX-nonexec").let { file ->
            file.writeText("#!/bin/sh\nexit 0\n")
            Os.chmod(file.absolutePath, 0b110100100)
        }
        fixtures.getValue("$PREFIX-broken").let { file ->
            Os.symlink("/workspace/.kf/bin/$PREFIX-absent-target", file.absolutePath)
        }
    }

    private fun nativeProbe(context: Context): Set<String> =
        WorkSurfaceRuntimeBridge.managedCommandVerificationBasis(context, COMMANDS)
            ?.commandFiles
            .orEmpty()
            .mapTo(linkedSetOf()) { commandFile -> commandFile.command }

    private fun shellProbe(context: Context): ShellExecution {
        val command = buildString {
            append("PATH=/workspace/.kf/bin:\$PATH; export PATH; ")
            COMMANDS.forEach { name ->
                append("if command -v '").append(name).append("' >/dev/null 2>&1; then ")
                append("printf '%s\\n' '").append(name).append("'; fi; ")
            }
        }
        val config = WorkSurfaceRuntimeBridge.buildArgvExecConfig(
            context = context,
            argv = listOf("/bin/sh", "-lc", command),
        )
        val startedAt = SystemClock.elapsedRealtime()
        val process = ProcessBuilder(config.command)
            .redirectErrorStream(false)
            .apply { environment().putAll(config.env) }
            .start()
        val stdout = ByteArrayOutputStream()
        val stderr = ByteArrayOutputStream()
        val stdoutThread = Thread { process.inputStream.use { it.copyTo(stdout) } }.apply { start() }
        val stderrThread = Thread { process.errorStream.use { it.copyTo(stderr) } }.apply { start() }
        val completed = process.waitFor(PROCESS_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        if (!completed) process.destroyForcibly()
        stdoutThread.join(PROCESS_TIMEOUT_MS)
        stderrThread.join(PROCESS_TIMEOUT_MS)
        check(completed) { "shell_probe_timeout" }
        return ShellExecution(
            durationMs = SystemClock.elapsedRealtime() - startedAt,
            available = stdout.toString(Charsets.UTF_8.name())
                .lineSequence()
                .map(String::trim)
                .filter(String::isNotBlank)
                .toCollection(linkedSetOf()),
            exitCode = process.exitValue(),
            stderr = stderr.toString(Charsets.UTF_8.name()).trim(),
        ).also { execution ->
            check(execution.exitCode == 0) { "shell_probe_failed_${execution.stderr}" }
        }
    }

    private fun percentile(values: List<Long>, ratio: Double): Long {
        val sorted = values.sorted()
        val index = kotlin.math.ceil(sorted.size * ratio).toInt().coerceIn(1, sorted.size) - 1
        return sorted[index]
    }
}
