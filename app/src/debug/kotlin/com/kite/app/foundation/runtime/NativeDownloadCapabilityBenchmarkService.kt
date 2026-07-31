package com.kite.app.foundation.runtime

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import com.kite.app.foundation.workspace.WorkSurfaceRuntimeBridge
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Debug-only 固定下载对照与压力服务；不接受外部 URL、路径、轮数或尺寸。 */
class NativeDownloadCapabilityBenchmarkService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!running.compareAndSet(false, true)) return START_NOT_STICKY
        scope.launch {
            try {
                NativeDownloadCapabilityBenchmark.run(applicationContext).forEach { report ->
                    Log.i(NativeDownloadCapabilityProbeReceiver.BENCHMARK_LOG_TAG, report)
                }
            } catch (error: Throwable) {
                Log.e(
                    NativeDownloadCapabilityProbeReceiver.BENCHMARK_LOG_TAG,
                    "status=failed reason=${safe(error.message ?: error.javaClass.simpleName)}",
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

        fun safe(value: String): String = value.take(160).map { character ->
            if (character.isLetterOrDigit() || character in "-_.:=/%") character else '_'
        }.joinToString("")
    }
}

private object NativeDownloadCapabilityBenchmark {
    private const val SMALL_URL = "https://www.rfc-editor.org/rfc/rfc20.txt"
    private const val SMALL_SHA256 = "714d11bfcbc001f98cd8a92291a19e3f670c2236ad02771092e0eea826acd13a"
    private const val SMALL_BYTES = 18_504L
    private const val LARGE_URL = "https://www.rfc-editor.org/rfc/rfc-index.txt"
    private const val ROUNDS = 3
    private const val TIMEOUT_MS = 60_000L
    private const val RESOURCE_ID = "kite.debug.native-download-benchmark"

    fun run(context: android.content.Context): List<String> {
        val workspace = KFContainerManager.resolveWorkspaceDirectory(context)
        val root = File(workspace, ".kf/cache/resources/$RESOURCE_ID")
        root.deleteRecursively()
        root.mkdirs()
        return try {
            val nativeTimes = mutableListOf<Long>()
            val prootTimes = mutableListOf<Long>()
            repeat(ROUNDS) { round ->
                val nativeFirst = round % 2 == 0
                if (nativeFirst) {
                    nativeTimes += runNativeSmall(context, root, round)
                    prootTimes += runProotSmall(context, workspace, round)
                } else {
                    prootTimes += runProotSmall(context, workspace, round)
                    nativeTimes += runNativeSmall(context, root, round)
                }
            }
            val retry = runNative(
                context = context,
                destination = "/workspace/.kf/cache/resources/$RESOURCE_ID/retry.bin",
                url = "https://127.0.0.1:1/unreachable",
                maximumBytes = 65_536L,
                expectedSha256 = null,
                maximumAttempts = 2,
                connectTimeoutMs = 1_000L,
                readTimeoutMs = 1_000L,
            ).result as? NativeDownloadExecutionResult.Failure
                ?: error("network_retry_did_not_fail")
            check(retry.attempts == 2) { "network_retry_attempts_${retry.attempts}" }
            check(!File(root, "retry.bin").exists()) { "network_retry_target_leaked" }

            val large = runNative(
                context = context,
                destination = "/workspace/.kf/cache/resources/$RESOURCE_ID/rfc-index.txt",
                url = LARGE_URL,
                maximumBytes = 16L * 1024L * 1024L,
                expectedSha256 = null,
                maximumAttempts = 2,
                connectTimeoutMs = 10_000L,
                readTimeoutMs = 30_000L,
            )
            val largeResult = large.result as? NativeDownloadExecutionResult.Success
                ?: error("large_download_failed_${large.result}")
            check(largeResult.bytesWritten >= 512L * 1024L) {
                "large_download_too_small_${largeResult.bytesWritten}"
            }
            listOf(
                "status=comparison rounds=$ROUNDS native_ms=${nativeTimes.joinToString(",")} " +
                    "proot_ms=${prootTimes.joinToString(",")} native_p50_ms=${p50(nativeTimes)} " +
                    "proot_p50_ms=${p50(prootTimes)} delta_pct=${formatDelta(nativeTimes, prootTimes)}",
                "status=network_retry reason=${retry.reason} attempts=${retry.attempts} cleanup=true",
                "status=large_complete bytes=${largeResult.bytesWritten} elapsed_ms=${large.elapsedMs} " +
                    "sha256=${largeResult.actualSha256} atomic=${largeResult.atomicMove}",
                "status=complete",
            )
        } finally {
            root.deleteRecursively()
        }
    }

    private fun runNativeSmall(context: android.content.Context, root: File, round: Int): Long {
        val file = File(root, "native-$round.txt")
        val execution = runNative(
            context = context,
            destination = "/workspace/.kf/cache/resources/$RESOURCE_ID/${file.name}",
            url = SMALL_URL,
            maximumBytes = 65_536L,
            expectedSha256 = SMALL_SHA256,
            maximumAttempts = 2,
            connectTimeoutMs = 10_000L,
            readTimeoutMs = 30_000L,
        )
        val result = execution.result as? NativeDownloadExecutionResult.Success
            ?: error("native_round_${round}_failed_${execution.result}")
        check(result.bytesWritten == SMALL_BYTES && file.length() == SMALL_BYTES) {
            "native_round_${round}_size_mismatch"
        }
        check(result.actualSha256 == SMALL_SHA256) { "native_round_${round}_digest_mismatch" }
        file.delete()
        return execution.elapsedMs
    }

    private fun runProotSmall(context: android.content.Context, workspace: File, round: Int): Long {
        val relative = ".kf/cache/resources/$RESOURCE_ID/proot-$round.txt"
        val hostFile = File(workspace, relative)
        hostFile.delete()
        val command =
            "set -e; curl -fsSL --connect-timeout 10 '$SMALL_URL' -o '/workspace/$relative'; " +
                "test \"${'$'}(sha256sum '/workspace/$relative' | cut -d ' ' -f 1)\" = '$SMALL_SHA256'"
        val execution = executeProot(context, listOf("/bin/bash", "-lc", command))
        check(execution.exitCode == 0) {
            "proot_round_${round}_failed_${execution.exitCode}_${safe(execution.stderr)}"
        }
        check(hostFile.length() == SMALL_BYTES) { "proot_round_${round}_size_mismatch" }
        hostFile.delete()
        return execution.elapsedMs
    }

    private fun runNative(
        context: android.content.Context,
        destination: String,
        url: String,
        maximumBytes: Long,
        expectedSha256: String?,
        maximumAttempts: Int,
        connectTimeoutMs: Long,
        readTimeoutMs: Long,
    ): NativeExecution {
        val started = SystemClock.elapsedRealtime()
        val parameters = buildMap {
            put(AndroidNativeDownloadCapabilityProvider.PARAM_URL, url)
            put(AndroidNativeDownloadCapabilityProvider.PARAM_DESTINATION, destination)
            put(AndroidNativeDownloadCapabilityProvider.PARAM_MAX_BYTES, maximumBytes.toString())
            put(AndroidNativeDownloadCapabilityProvider.PARAM_MAX_ATTEMPTS, maximumAttempts.toString())
            put(AndroidNativeDownloadCapabilityProvider.PARAM_RETRY_DELAY_MS, "100")
            put(AndroidNativeDownloadCapabilityProvider.PARAM_CONNECT_TIMEOUT_MS, connectTimeoutMs.toString())
            put(AndroidNativeDownloadCapabilityProvider.PARAM_READ_TIMEOUT_MS, readTimeoutMs.toString())
            put(AndroidNativeDownloadCapabilityProvider.PARAM_REPLACE_EXISTING, "true")
            expectedSha256?.let { put(AndroidNativeDownloadCapabilityProvider.PARAM_EXPECTED_SHA256, it) }
        }
        val decision = AndroidNativeDownloadCapabilityProvider.prepare(
            AndroidNativeCapabilityContext(
                listOf(NativeCapabilityDestinationRoot("/workspace", KFContainerManager.resolveWorkspaceDirectory(context)))
            ),
            RuntimeExecutionRequest(
                payload = RuntimeExecutionPayload.NativeCapability(
                    AndroidNativeDownloadCapabilityProvider.CAPABILITY_ID,
                    parameters,
                ),
                requirements = setOf(RuntimeExecutionRequirement.ANDROID_NATIVE),
            ),
        )
        val plan = (decision as? RuntimeProviderDecision.Ready)?.plan
            ?: error("native_provider_not_ready_${decision.reason}")
        val result = AndroidNativeDownloadExecutor().execute(plan)
        return NativeExecution(SystemClock.elapsedRealtime() - started, result)
    }

    private fun executeProot(
        context: android.content.Context,
        argv: List<String>,
    ): ProotExecution {
        val started = SystemClock.elapsedRealtime()
        val config = WorkSurfaceRuntimeBridge.buildArgvExecConfig(context = context, argv = argv)
        val process = ProcessBuilder(config.command)
            .redirectErrorStream(false)
            .apply { environment().putAll(config.env) }
            .start()
        val stdout = ByteArrayOutputStream()
        val stderr = ByteArrayOutputStream()
        val outReader = thread(start = true, isDaemon = true) { process.inputStream.use { it.copyTo(stdout) } }
        val errReader = thread(start = true, isDaemon = true) { process.errorStream.use { it.copyTo(stderr) } }
        val finished = process.waitFor(TIMEOUT_MS, TimeUnit.MILLISECONDS)
        if (!finished) process.destroyForcibly()
        outReader.join(1_000L)
        errReader.join(1_000L)
        return ProotExecution(
            elapsedMs = SystemClock.elapsedRealtime() - started,
            exitCode = if (finished) process.exitValue() else -1,
            stderr = stderr.toString(Charsets.UTF_8.name()),
        )
    }

    private fun p50(values: List<Long>): Long = values.sorted()[values.size / 2]

    private fun formatDelta(nativeTimes: List<Long>, prootTimes: List<Long>): String {
        val native = p50(nativeTimes).toDouble()
        val proot = p50(prootTimes).toDouble()
        return String.format(Locale.US, "%.1f", (proot - native) / proot * 100.0)
    }

    private fun safe(value: String): String = value.take(160).map { character ->
        if (character.isLetterOrDigit() || character in "-_.:=/%") character else '_'
    }.joinToString("")

    private data class NativeExecution(
        val elapsedMs: Long,
        val result: NativeDownloadExecutionResult,
    )

    private data class ProotExecution(
        val elapsedMs: Long,
        val exitCode: Int,
        val stderr: String,
    )
}
