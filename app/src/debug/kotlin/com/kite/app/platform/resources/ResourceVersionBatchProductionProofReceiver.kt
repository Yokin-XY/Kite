package com.kite.app.platform.resources

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.kite.app.resources.KiteResourceManifest
import com.kite.app.resources.KiteResourceInstallStore
import com.kite.app.resources.KiteResourceRemoteVersionProbe
import com.kite.app.resources.KiteResourceSourcePlanFactory
import com.kite.app.shell.KiteAppGraph
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Debug-only 真实批量链证明；ADB 只能触发，目标从正式已安装事实中按结构化合同选择。 */
class ResourceVersionBatchProductionProofReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION) return
        runCatching {
            context.startService(Intent(context, ResourceVersionBatchProductionProofService::class.java))
        }.onFailure { error ->
            Log.e(LOG_TAG, "status=rejected reason=${safe(error.message)}")
        }
    }

    internal companion object {
        const val ACTION = "com.kite.app.debug.RESOURCE_VERSION_BATCH_PRODUCTION_PROOF"
        const val LOG_TAG = "KiteVersionBatchProof"

        fun safe(value: String?): String = value.orEmpty().take(180).map { character ->
            if (character.isLetterOrDigit() || character in "-_.:=/%") character else '_'
        }.joinToString("")
    }
}

class ResourceVersionBatchProductionProofService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!running.compareAndSet(false, true)) return START_NOT_STICKY
        scope.launch {
            try {
                runProof(applicationContext)
            } catch (error: Throwable) {
                Log.e(
                    ResourceVersionBatchProductionProofReceiver.LOG_TAG,
                    "status=failed reason=${ResourceVersionBatchProductionProofReceiver.safe(error.message)}",
                    error,
                )
            } finally {
                running.set(false)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private suspend fun runProof(context: Context) {
        val graph = KiteAppGraph.from(context)
        val candidates = graph.resourceInstallStore.registrySnapshot().values.asSequence()
            .filter { entry -> entry.installed }
            .mapNotNull { entry -> graph.resourceManifestLoader.requestManifest(entry.resourceId) }
            .map(::BatchCandidate)
            .filter(BatchCandidate::supported)
            .sortedBy { candidate -> candidate.manifest.id }
            .toList()
        val structured = candidates.firstOrNull(BatchCandidate::declaresStructuredNativeRemote)
            ?: error("structured_installed_candidate_missing")
        val compatibility = candidates.firstOrNull { candidate ->
            !candidate.declaresStructuredNativeRemote
        } ?: error("compatibility_installed_candidate_missing")
        val selected = listOf(structured, compatibility).distinctBy { candidate -> candidate.manifest.id }
        check(selected.size == 2) { "distinct_candidate_count_invalid" }
        Log.i(
            ResourceVersionBatchProductionProofReceiver.LOG_TAG,
            "status=selected installed=${candidates.size} selected=${selected.size} distinct=${selected.size} " +
                "declaredStructuredNativeRemote=1 declaredCompatibility=1 adbOverrides=false",
        )
        val effects = graph.resourceActionWorkflowCoordinator.checkUpdates(
            selected.map { candidate -> candidate.manifest.id },
        )
        val checkingRemaining = selected.count { candidate ->
            graph.resourceInstallStore.registryEntry(candidate.manifest.id)?.updateStatus ==
                KiteResourceInstallStore.UPDATE_STATUS_CHECKING
        }
        check(checkingRemaining == 0) { "checking_state_not_resolved" }
        Log.i(
            ResourceVersionBatchProductionProofReceiver.LOG_TAG,
            "status=complete selected=${selected.size} distinct=${selected.size} effectCount=${effects.size} " +
                "checkingRemaining=$checkingRemaining productionWorkflow=true adbOverrides=false",
        )
    }

    private data class BatchCandidate(
        val manifest: KiteResourceManifest,
    ) {
        private val plan = KiteResourceSourcePlanFactory.versionCheckPlan(manifest)
        val supported: Boolean = plan.supported
        val declaresStructuredNativeRemote: Boolean =
            plan.installed?.structuredMetadata != null && plan.latest is KiteResourceRemoteVersionProbe
    }

    private companion object {
        val running = AtomicBoolean(false)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
