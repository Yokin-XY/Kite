package com.kite.app.foundation.runtime

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.kite.app.foundation.logging.Logger

class RuntimeControlledLeaseProbeRegistrationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_REGISTER_CONTROLLED_LEASE_PROBE) {
            setResultCode(Activity.RESULT_CANCELED)
            setResultData("ignored_controlled_lease_probe_registration_action")
            return
        }
        val appContext = context.applicationContext
        val requestedSpaceId = intent.getStringExtra(EXTRA_SPACE_ID)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
        val record = if (requestedSpaceId == null) {
            RuntimeControlledLeaseProbeRegistration.registerForCurrentSpace(appContext)
        } else {
            RuntimeControlledLeaseProbeRegistration.register(appContext, requestedSpaceId)
        }
        Logger.i(
            LOG_TAG,
            "registered controlled lease probe runtime=${record.id} space=${record.spaceId} retention=${record.retentionClass.name}"
        )
        setResultCode(Activity.RESULT_OK)
        setResultData("registered runtime=${record.id} unit=${RuntimeControlledLeaseProbeRegistration.UNIT_ID} space=${record.spaceId}")
    }

    companion object {
        const val ACTION_REGISTER_CONTROLLED_LEASE_PROBE =
            "com.kite.app.action.REGISTER_CONTROLLED_LEASE_PROBE"
        const val EXTRA_SPACE_ID = "space_id"
        private const val LOG_TAG = "ControlledLeaseProbe"

        fun buildIntent(spaceId: String? = null): Intent {
            return Intent(ACTION_REGISTER_CONTROLLED_LEASE_PROBE).apply {
                setPackage("com.kite.app")
                spaceId?.trim()?.takeIf { it.isNotBlank() }?.let {
                    putExtra(EXTRA_SPACE_ID, it)
                }
            }
        }

        fun kfRuntimeRegistrationHelperCommand(spaceId: String? = null): String {
            return buildString {
                append("kf-runtime lease-probe register")
                spaceId?.trim()?.takeIf { it.isNotBlank() }?.let {
                    append(" --space-id ")
                    append(it)
                }
            }
        }

        fun debugAdbBroadcastCommand(spaceId: String? = null): String {
            return buildString {
                append("adb shell am broadcast -a ")
                append(ACTION_REGISTER_CONTROLLED_LEASE_PROBE)
                append(" -p com.kite.app")
                spaceId?.trim()?.takeIf { it.isNotBlank() }?.let {
                    append(" --es ")
                    append(EXTRA_SPACE_ID)
                    append(' ')
                    append(it)
                }
            }
        }
    }
}
