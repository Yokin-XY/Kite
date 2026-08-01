package com.kite.app.foundation.runtime

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import java.io.File
import kotlin.concurrent.thread
import org.json.JSONObject

/** RF1930 固定生产/完整路径冷进程矩阵；两种动作均不接收 ADB 自定义参数。 */
class DefaultContainerColdReuseProductionBenchmarkReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val mode = when (intent?.action) {
            PRODUCTION_ACTION -> Mode.PRODUCTION
            FULL_ACTION -> Mode.FULL
            COUNTEREXAMPLE_ACTION -> Mode.COUNTEREXAMPLE
            else -> return
        }
        val pending = goAsync()
        thread(name = "KiteDefaultContainerProduction", isDaemon = true) {
            try {
                Log.i(LOG_TAG, runFixed(context.applicationContext, mode))
            } catch (error: Throwable) {
                Log.e(LOG_TAG, "status=failed reason=${safe(error.message ?: error.javaClass.simpleName)}", error)
            } finally {
                pending.finish()
            }
        }
    }

    internal companion object {
        const val PRODUCTION_ACTION = "com.kite.app.debug.DEFAULT_CONTAINER_COLD_REUSE_PRODUCTION"
        const val FULL_ACTION = "com.kite.app.debug.DEFAULT_CONTAINER_COLD_REUSE_FULL"
        const val COUNTEREXAMPLE_ACTION =
            "com.kite.app.debug.DEFAULT_CONTAINER_COLD_REUSE_COUNTEREXAMPLE"
        const val LOG_TAG = "KiteContainerProduction"

        private const val EXPECTED_PAIRED_COLD_ROUNDS = 3
        private const val MIN_REDUCTION_PERCENT = 50.0
        private const val MIN_REDUCTION_MS = 700L

        private enum class Mode { PRODUCTION, FULL, COUNTEREXAMPLE }

        private data class FileStamp(
            val path: String,
            val exists: Boolean,
            val length: Long,
            val lastModified: Long,
        )

        private fun runFixed(context: Context, mode: Mode): String {
            if (mode == Mode.COUNTEREXAMPLE) return runCounterexample(context)
            val beforeDecision = KFContainerManager.defaultContainerColdReuseDecision(context)
            val baseBefore = baseProofFiles(context).map(::stamp)
            val startedAt = SystemClock.elapsedRealtimeNanos()
            val container = when (mode) {
                Mode.PRODUCTION -> KFContainerManager.ensureDefaultContainer(context)
                Mode.FULL -> KFContainerManager.ensureDefaultContainerFullPreparationForBenchmark(context)
                Mode.COUNTEREXAMPLE -> error("counterexample is handled before timing")
            }
            val durationMs = (SystemClock.elapsedRealtimeNanos() - startedAt) / 1_000_000L
            val baseAfter = baseProofFiles(context).map(::stamp)
            val afterDecision = KFContainerManager.defaultContainerColdReuseDecision(context)
            val beforeReady = beforeDecision as? DefaultContainerColdReuseDecision.Ready
            val afterReady = afterDecision as? DefaultContainerColdReuseDecision.Ready
            val sameIdentity = beforeReady != null &&
                beforeReady.identity == afterReady?.identity &&
                beforeReady.identity.containerId == container.id &&
                beforeReady.identity.rootfsPath == container.rootfsPath &&
                beforeReady.identity.workspacePath == container.workspacePath
            val baseUnchanged = baseBefore == baseAfter
            val correctnessGate = sameIdentity && (mode == Mode.FULL || baseUnchanged)
            return "status=complete suite=rf1930_default_container_cold_reuse " +
                "mode=${mode.name.lowercase()} expectedPairedColdRounds=$EXPECTED_PAIRED_COLD_ROUNDS " +
                "durationMs=$durationMs sameIdentity=$sameIdentity baseUnchanged=$baseUnchanged " +
                "correctnessGate=$correctnessGate requiredReductionPercent=$MIN_REDUCTION_PERCENT " +
                "requiredReductionMs=$MIN_REDUCTION_MS adbOverrides=false"
        }

        private fun runCounterexample(context: Context): String {
            val container = checkNotNull(KFContainerManager.getSavedContainer(context))
            val receipt = File(container.rootfsPath, ".kf-container-cold-reuse-receipt.json")
            val original = receipt.readText()
            var repaired = false
            try {
                val altered = JSONObject(original)
                    .put("hostTimeZoneId", "rf1930-fixed-invalid-time-zone")
                    .toString(2) + "\n"
                receipt.writeText(altered)
                val invalidDecision = KFContainerManager.defaultContainerColdReuseDecision(context)
                val fallbackTriggered = invalidDecision is DefaultContainerColdReuseDecision.Unsupported &&
                    invalidDecision.reason.contains("mutable_repair")
                val repairedContainer = KFContainerManager.ensureDefaultContainer(context)
                val afterReady = KFContainerManager.defaultContainerColdReuseDecision(context)
                    as? DefaultContainerColdReuseDecision.Ready
                repaired = afterReady != null &&
                    afterReady.identity.containerId == repairedContainer.id &&
                    afterReady.identity.rootfsPath == repairedContainer.rootfsPath &&
                    afterReady.identity.workspacePath == repairedContainer.workspacePath
                return "status=complete suite=rf1930_default_container_cold_reuse " +
                    "mode=counterexample fallbackTriggered=$fallbackTriggered repaired=$repaired " +
                    "correctnessGate=${fallbackTriggered && repaired} adbOverrides=false"
            } finally {
                if (!repaired) receipt.writeText(original)
            }
        }

        private fun baseProofFiles(context: Context): List<File> {
            val layout = AssetExtractor.getRuntimeLayout(context)
            return listOf(
                layout.prootRuntimeDescriptorFile,
                File(layout.baseImageDir, ".kf-rootfs-ready"),
                File(layout.baseImageDir, "bin/bash"),
                File(layout.baseImageDir, "var/lib/dpkg/status"),
            )
        }

        private fun stamp(file: File): FileStamp = FileStamp(
            path = file.absolutePath,
            exists = file.exists(),
            length = if (file.isFile) file.length() else 0L,
            lastModified = file.lastModified(),
        )

        private fun safe(value: String): String = value.take(220).map { character ->
            if (character.isLetterOrDigit() || character in "-_.:=,/%") character else '_'
        }.joinToString("")
    }
}
