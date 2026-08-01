package com.kite.app.foundation.runtime

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import java.io.File
import kotlin.concurrent.thread

/** Debug-only 固定冷进程矩阵；ADB 只能触发，不能提供路径、版本、容器或阈值。 */
class DefaultContainerColdReuseBenchmarkReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION) return
        val pending = goAsync()
        thread(name = "KiteDefaultContainerColdReuse", isDaemon = true) {
            try {
                Log.i(LOG_TAG, DefaultContainerColdReuseBenchmark.run(context.applicationContext))
            } catch (error: Throwable) {
                Log.e(LOG_TAG, "status=failed reason=${safe(error.message ?: error.javaClass.simpleName)}", error)
            } finally {
                pending.finish()
            }
        }
    }

    internal companion object {
        const val ACTION = "com.kite.app.debug.DEFAULT_CONTAINER_COLD_REUSE_BENCHMARK"
        const val LOG_TAG = "KiteContainerColdReuse"

        fun safe(value: String): String = value.take(220).map { character ->
            if (character.isLetterOrDigit() || character in "-_.:=,/%") character else '_'
        }.joinToString("")
    }
}

private object DefaultContainerColdReuseBenchmark {
    private const val SUITE = "rf1920_default_container_cold_reuse"
    private const val EXPECTED_COLD_ROUNDS = 3
    private const val MIN_REDUCTION_PERCENT = 50.0
    private const val MIN_REDUCTION_MS = 700L
    private const val MAX_CANDIDATE_P95_MS = 500L

    private data class FileStamp(
        val path: String,
        val exists: Boolean,
        val length: Long,
        val lastModified: Long,
    )

    fun run(context: Context): String {
        val proofFiles = proofFiles(context)
        val before = proofFiles.map(::stamp)
        val candidateStartedAt = SystemClock.elapsedRealtimeNanos()
        val candidate = KFContainerManager.defaultContainerColdReuseDecision(context)
        val candidateMs = elapsedMs(candidateStartedAt)
        val afterCandidate = proofFiles.map(::stamp)
        val noSideEffects = before == afterCandidate

        val baselineStartedAt = SystemClock.elapsedRealtimeNanos()
        val baselineContainer = KFContainerManager.ensureDefaultContainer(context)
        val baselineMs = elapsedMs(baselineStartedAt)
        val afterBaseline = KFContainerManager.defaultContainerColdReuseDecision(context)

        val candidateReady = candidate as? DefaultContainerColdReuseDecision.Ready
        val afterReady = afterBaseline as? DefaultContainerColdReuseDecision.Ready
        val sameIdentity = candidateReady != null &&
            candidateReady.identity == afterReady?.identity &&
            candidateReady.identity.containerId == baselineContainer.id &&
            candidateReady.identity.rootfsPath == baselineContainer.rootfsPath &&
            candidateReady.identity.workspacePath == baselineContainer.workspacePath
        val reductionMs = baselineMs - candidateMs
        val reductionPercent = if (baselineMs > 0L) {
            reductionMs.toDouble() * 100.0 / baselineMs.toDouble()
        } else {
            0.0
        }
        val correctnessGate = candidateReady != null && noSideEffects && sameIdentity
        val performanceGate = reductionMs >= MIN_REDUCTION_MS &&
            reductionPercent >= MIN_REDUCTION_PERCENT &&
            candidateMs <= MAX_CANDIDATE_P95_MS
        val reason = when (candidate) {
            is DefaultContainerColdReuseDecision.Ready -> "ready"
            is DefaultContainerColdReuseDecision.Unsupported -> candidate.reason
            is DefaultContainerColdReuseDecision.Blocked -> candidate.reason
        }
        return "status=complete suite=$SUITE expectedColdRounds=$EXPECTED_COLD_ROUNDS " +
            "candidate=${candidate::class.java.simpleName.lowercase()} " +
            "reason=${DefaultContainerColdReuseBenchmarkReceiver.safe(reason)} " +
            "candidateMs=$candidateMs baselineMs=$baselineMs reductionMs=$reductionMs " +
            "reductionPercent=${"%.1f".format(java.util.Locale.US, reductionPercent)} " +
            "noSideEffects=$noSideEffects sameIdentity=$sameIdentity " +
            "correctnessGate=$correctnessGate performanceGate=$performanceGate " +
            "adbOverrides=false"
    }

    private fun proofFiles(context: Context): List<File> {
        val layout = AssetExtractor.getRuntimeLayout(context)
        val container = KFContainerManager.getSavedContainer(context)
        return buildList {
            add(layout.prootFile)
            add(layout.prootLibtallocFile)
            add(layout.prootLoaderFile)
            add(layout.prootLoader32File)
            add(layout.prootRuntimeDescriptorFile)
            add(File(layout.baseImageDir, ".kf-rootfs-ready"))
            add(layout.registryFile)
            container?.let {
                add(File(it.rootfsPath, ".kf-container-rootfs-ready"))
                add(File(it.workspacePath))
            }
        }
    }

    private fun stamp(file: File): FileStamp = FileStamp(
        path = file.absolutePath,
        exists = file.exists(),
        length = if (file.isFile) file.length() else 0L,
        lastModified = file.lastModified(),
    )

    private fun elapsedMs(startedAtNanos: Long): Long =
        (SystemClock.elapsedRealtimeNanos() - startedAtNanos) / 1_000_000L
}
