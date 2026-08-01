package com.kite.app.platform.resources

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.kite.app.action.KiteResourceActionIntent
import com.kite.app.action.KiteResourceActionRequest
import com.kite.app.action.KiteResourceActionSource
import com.kite.app.resources.KiteResourceManifest
import com.kite.app.resources.KiteResourceSourcePlanFactory
import com.kite.app.shell.KiteAppGraph
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Debug-only 生产调用链证明；ADB 只能触发，不能指定资源或车道。 */
class ResourceVersionProductionRouteProofReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION) return
        runCatching {
            context.startService(Intent(context, ResourceVersionProductionRouteProofService::class.java))
        }.onFailure { error ->
            Log.e(LOG_TAG, "status=rejected reason=${safe(error.message)}")
        }
    }

    internal companion object {
        const val ACTION = "com.kite.app.debug.RESOURCE_VERSION_PRODUCTION_ROUTE_PROOF"
        const val LOG_TAG = "KiteVersionProof"

        fun safe(value: String?): String = value.orEmpty().take(180).map { character ->
            if (character.isLetterOrDigit() || character in "-_.:=/%") character else '_'
        }.joinToString("")
    }
}

class ResourceVersionProductionRouteProofService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!running.compareAndSet(false, true)) return START_NOT_STICKY
        scope.launch {
            try {
                runProof(applicationContext)
            } catch (error: Throwable) {
                Log.e(
                    ResourceVersionProductionRouteProofReceiver.LOG_TAG,
                    "status=failed reason=${ResourceVersionProductionRouteProofReceiver.safe(error.message)}",
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
            .map { manifest -> VersionRouteCandidate(manifest) }
            .filter { candidate -> candidate.supported }
            .sortedBy { candidate -> candidate.manifest.id }
            .toList()
        val native = candidates.firstOrNull { candidate -> candidate.structured }
            ?: error("native_installed_candidate_missing")
        val fallback = candidates.firstOrNull { candidate -> !candidate.structured }
            ?: error("fallback_installed_candidate_missing")
        Log.i(
            ResourceVersionProductionRouteProofReceiver.LOG_TAG,
            "status=selected installed=${candidates.size} nativeReady=true fallbackReady=true adbOverrides=false",
        )
        val nativeEffects = graph.resourceActionWorkflowCoordinator.dispatch(native.request())
        Log.i(
            ResourceVersionProductionRouteProofReceiver.LOG_TAG,
            "status=checked expected=native effectCount=${nativeEffects.size}",
        )
        val fallbackEffects = graph.resourceActionWorkflowCoordinator.dispatch(fallback.request())
        Log.i(
            ResourceVersionProductionRouteProofReceiver.LOG_TAG,
            "status=checked expected=proot_fallback effectCount=${fallbackEffects.size}",
        )
        Log.i(
            ResourceVersionProductionRouteProofReceiver.LOG_TAG,
            "status=complete nativeChecks=1 fallbackChecks=1 adbOverrides=false",
        )
    }

    private data class VersionRouteCandidate(
        val manifest: KiteResourceManifest,
    ) {
        private val plan = KiteResourceSourcePlanFactory.versionCheckPlan(manifest)
        val supported: Boolean = plan.supported
        val structured: Boolean = plan.installed?.structuredMetadata != null

        fun request() = KiteResourceActionRequest(
            resourceId = manifest.id,
            intent = KiteResourceActionIntent.CheckUpdate,
            source = KiteResourceActionSource.Automation,
        )
    }

    private companion object {
        val running = AtomicBoolean(false)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
