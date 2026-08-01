package com.kite.app.foundation.runtime

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import com.kite.app.foundation.contracts.ContainerExecConfig
import java.security.MessageDigest
import kotlin.concurrent.thread

/** RF2020 固定冷进程首份配置基线；不接收 ADB 自定义参数，也不启动构造出的命令。 */
class OrdinaryLaunchPreparationBenchmarkReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val mode = when (intent?.action) {
            BASELINE_ACTION -> Mode.BASELINE
            CANDIDATE_ACTION -> Mode.CANDIDATE
            COUNTEREXAMPLE_ACTION -> Mode.COUNTEREXAMPLE
            else -> return
        }
        val pending = goAsync()
        thread(name = "KiteOrdinaryLaunchBaseline", isDaemon = true) {
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
        const val BASELINE_ACTION = "com.kite.app.debug.ORDINARY_LAUNCH_PREPARATION_BASELINE"
        const val CANDIDATE_ACTION = "com.kite.app.debug.ORDINARY_LAUNCH_PREPARATION_CANDIDATE"
        const val COUNTEREXAMPLE_ACTION =
            "com.kite.app.debug.ORDINARY_LAUNCH_PREPARATION_COUNTEREXAMPLE"
        const val LOG_TAG = "KiteOrdinaryLaunchPrep"

        private const val EXPECTED_PAIRED_COLD_ROUNDS = 3
        private const val MIN_REDUCTION_PERCENT = 30.0
        private const val MIN_REDUCTION_MS = 300L
        private const val MAX_CANDIDATE_P95_MS = 500L
        private val FIXED_ARGV = listOf("/bin/true")

        private enum class Mode { BASELINE, CANDIDATE, COUNTEREXAMPLE }

        private fun runFixed(context: Context, mode: Mode): String {
            if (mode == Mode.COUNTEREXAMPLE) return runCounterexample(context)
            val ready = KFContainerManager.defaultContainerColdReuseDecision(context)
                as? DefaultContainerColdReuseDecision.Ready
                ?: error("default_container_not_ready")
            val before = KFContainerManager.runtimeLaunchPreparationCacheSnapshot()
            check(!before.hasEntry) { "cold_process_cache_not_empty" }

            val startedAt = SystemClock.elapsedRealtimeNanos()
            val config = when (mode) {
                Mode.BASELINE -> KFContainerManager.buildContainerArgvExecConfigFullPreparationForBenchmark(
                    context = context,
                    argv = FIXED_ARGV,
                )
                Mode.CANDIDATE -> KFContainerManager.buildContainerArgvExecConfigColdReuseCandidateForBenchmark(
                    context = context,
                    argv = FIXED_ARGV,
                )
                Mode.COUNTEREXAMPLE -> error("counterexample is handled before timing")
            }
            val durationMs = (SystemClock.elapsedRealtimeNanos() - startedAt) / 1_000_000L
            val after = KFContainerManager.runtimeLaunchPreparationCacheSnapshot()
            val sameIdentity = ready.identity.containerId == config.container.id &&
                ready.identity.rootfsPath == config.container.rootfsPath &&
                ready.identity.workspacePath == config.container.workspacePath
            val cacheExpected = mode == Mode.BASELINE ||
                (after.hasEntry && after.rebuildCount == before.rebuildCount + 1L)
            val correctnessGate = sameIdentity && cacheExpected
            return "status=complete suite=rf2020_ordinary_launch_preparation " +
                "mode=${mode.name.lowercase()} " +
                "expectedPairedColdRounds=$EXPECTED_PAIRED_COLD_ROUNDS durationMs=$durationMs " +
                "sameIdentity=$sameIdentity cacheRebuilt=${after.rebuildCount == before.rebuildCount + 1L} " +
                "correctnessGate=$correctnessGate businessProcessStarted=false " +
                "configDigest=${digest(config)} commandCount=${config.command.size} envCount=${config.env.size} " +
                "requiredReductionPercent=$MIN_REDUCTION_PERCENT requiredReductionMs=$MIN_REDUCTION_MS " +
                "maxCandidateP95Ms=$MAX_CANDIDATE_P95_MS adbOverrides=false"
        }

        private fun runCounterexample(context: Context): String {
            val viewDecision = KFContainerManager.defaultContainerColdReuseDecision(
                context = context,
                requestedProotViewId = "rf2020-fixed-view",
            )
            val environmentDecision = KFContainerManager.defaultContainerColdReuseDecision(
                context = context,
                requestedProotEnvironmentId = "rf2020-fixed-environment",
            )
            val viewFallback = viewDecision is DefaultContainerColdReuseDecision.Unsupported &&
                viewDecision.reason == "explicit_view_or_environment"
            val environmentFallback = environmentDecision is DefaultContainerColdReuseDecision.Unsupported &&
                environmentDecision.reason == "explicit_view_or_environment"
            return "status=complete suite=rf2020_ordinary_launch_preparation mode=counterexample " +
                "viewFallback=$viewFallback environmentFallback=$environmentFallback " +
                "correctnessGate=${viewFallback && environmentFallback} businessProcessStarted=false " +
                "adbOverrides=false"
        }

        private fun digest(config: ContainerExecConfig): String {
            val canonical = buildString {
                append(config.container.id).append('\n')
                append(config.container.rootfsPath).append('\n')
                append(config.container.workspacePath).append('\n')
                append(config.workingDirectory).append('\n')
                config.command.forEach { append("cmd=").append(it).append('\n') }
                config.env.toSortedMap().forEach { (key, value) ->
                    append("env=").append(key).append('=').append(value).append('\n')
                }
            }
            return MessageDigest.getInstance("SHA-256")
                .digest(canonical.toByteArray())
                .joinToString("") { byte -> "%02x".format(byte) }
        }

        private fun safe(value: String): String = value.take(220).map { character ->
            if (character.isLetterOrDigit() || character in "-_.:=,/%") character else '_'
        }.joinToString("")
    }
}
