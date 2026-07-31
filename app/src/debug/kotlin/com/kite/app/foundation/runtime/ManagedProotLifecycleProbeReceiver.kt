package com.kite.app.foundation.runtime

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.kite.app.foundation.service.BackgroundRuntimeHost
import com.kite.app.foundation.service.BackgroundRuntimeRegistry
import com.kite.app.foundation.service.BackgroundRuntimeProotLeaseCheckpointPolicy
import com.kite.app.foundation.service.BackgroundRuntimeProotLeaseCheckpointState

/** Debug-only 固定生命周期探针；不接收 runtime、命令、路径或并发参数。 */
class ManagedProotLifecycleProbeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val appContext = context.applicationContext
        when (intent?.action) {
            ACTION_START -> {
                if (BackgroundRuntimeRegistry.get(appContext, RUNTIME_ID) == null) {
                    RuntimeControlledLeaseProbeRegistration.registerForCurrentSpace(appContext)
                } else {
                    RuntimeControlledLeaseProbeRegistration.upsertActiveManifestUnit(appContext)
                }
                BackgroundRuntimeHost.startRuntime(appContext, RUNTIME_ID)
                Log.i(LOG_TAG, "action=start requested=true")
            }
            ACTION_STOP -> {
                BackgroundRuntimeHost.stopRuntime(appContext, RUNTIME_ID)
                Log.i(LOG_TAG, "action=stop requested=true")
            }
            ACTION_SNAPSHOT -> Log.i(LOG_TAG, snapshot(appContext))
        }
    }

    private fun snapshot(context: Context): String {
        val record = BackgroundRuntimeRegistry.get(context, RUNTIME_ID)
            ?: return "action=snapshot record=missing"
        val lease = when (val state = BackgroundRuntimeProotLeaseCheckpointPolicy.inspect(record)) {
            BackgroundRuntimeProotLeaseCheckpointState.Absent -> "absent"
            is BackgroundRuntimeProotLeaseCheckpointState.Malformed -> "malformed:${state.reason}"
            is BackgroundRuntimeProotLeaseCheckpointState.Ready ->
                "${state.checkpoint.generation}:${state.checkpoint.phase.name.lowercase()}"
        }
        val actual = WarmProotExecutionCoordinator.tuningSnapshot().unifiedActualCapacity
        return "action=snapshot status=${record.status.name.lowercase()} pid=${record.pid ?: 0} " +
            "boot=${record.processBootId?.take(12) ?: "none"} ticks=${record.processStartTicks ?: 0} " +
            "lease=$lease short=${actual.shortActiveCount} long=${actual.longActiveCount} " +
            "total=${actual.totalActiveCount} max=${actual.effectiveGlobalMax}"
    }

    private companion object {
        const val RUNTIME_ID = RuntimeControlledLeaseProbeRegistration.RUNTIME_ID
        const val LOG_TAG = "[KFShell]ManagedProotLifecycle"
        const val ACTION_START = "com.kite.app.debug.MANAGED_PROOT_LIFECYCLE_START"
        const val ACTION_STOP = "com.kite.app.debug.MANAGED_PROOT_LIFECYCLE_STOP"
        const val ACTION_SNAPSHOT = "com.kite.app.debug.MANAGED_PROOT_LIFECYCLE_SNAPSHOT"
    }
}
