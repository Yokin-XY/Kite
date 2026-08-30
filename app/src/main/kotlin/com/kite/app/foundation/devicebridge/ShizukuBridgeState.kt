package com.kite.app.foundation.devicebridge

enum class ShizukuPermissionState {
    Unknown,
    Required,
    Requesting,
    Granted,
    Denied
}
data class ShizukuBridgeState(
    val lifecycle: DeviceBridgeLifecycleStatus = DeviceBridgeLifecycleStatus.Unavailable,
    val managerInstalled: Boolean = false,
    val binderAlive: Boolean = false,
    val permission: ShizukuPermissionState = ShizukuPermissionState.Unknown,
    val identity: DeviceBridgeIdentity = DeviceBridgeIdentity.Unknown,
    val uid: Int? = null,
    val serverVersion: Int? = null,
    val requestInFlight: Boolean = false,
    val lastSignal: String = "initial",
    val error: String? = null
)

sealed interface ShizukuBridgeSignal {
    data class SnapshotObserved(
        val managerInstalled: Boolean,
        val binderAlive: Boolean,
        val permissionGranted: Boolean?,
        val uid: Int?,
        val serverVersion: Int?,
        val source: String
    ) : ShizukuBridgeSignal

    data object AuthorizationRequested : ShizukuBridgeSignal

    data class PermissionResult(val granted: Boolean) : ShizukuBridgeSignal

    data class BinderDied(val managerInstalled: Boolean) : ShizukuBridgeSignal

    data class ProbeFailed(
        val managerInstalled: Boolean,
        val source: String,
        val error: String
    ) : ShizukuBridgeSignal
}

/** 纯状态规约，Android/Shizuku 回调只负责产生信号。 */
object ShizukuBridgeStateReducer {
    fun reduce(current: ShizukuBridgeState, signal: ShizukuBridgeSignal): ShizukuBridgeState = when (signal) {
        is ShizukuBridgeSignal.SnapshotObserved -> fromSnapshot(signal)
        ShizukuBridgeSignal.AuthorizationRequested -> if (
            current.binderAlive && current.permission != ShizukuPermissionState.Granted
        ) {
            current.copy(
                lifecycle = DeviceBridgeLifecycleStatus.Connecting,
                permission = ShizukuPermissionState.Requesting,
                requestInFlight = true,
                lastSignal = "authorization_requested",
                error = null
            )
        } else {
            current
        }

        is ShizukuBridgeSignal.PermissionResult -> when {
            !current.binderAlive -> current.copy(
                lifecycle = if (current.managerInstalled) {
                    DeviceBridgeLifecycleStatus.InstalledButStopped
                } else {
                    DeviceBridgeLifecycleStatus.Unavailable
                },
                permission = ShizukuPermissionState.Unknown,
                identity = DeviceBridgeIdentity.Unknown,
                uid = null,
                serverVersion = null,
                requestInFlight = false,
                lastSignal = "permission_result_without_binder",
                error = null
            )

            signal.granted -> current.copy(
                lifecycle = DeviceBridgeLifecycleStatus.Ready,
                permission = ShizukuPermissionState.Granted,
                requestInFlight = false,
                lastSignal = "permission_granted",
                error = null
            )

            else -> current.copy(
                lifecycle = DeviceBridgeLifecycleStatus.Revoked,
                permission = ShizukuPermissionState.Denied,
                requestInFlight = false,
                lastSignal = "permission_denied",
                error = null
            )
        }

        is ShizukuBridgeSignal.BinderDied -> ShizukuBridgeState(
            lifecycle = if (signal.managerInstalled) {
                DeviceBridgeLifecycleStatus.InstalledButStopped
            } else {
                DeviceBridgeLifecycleStatus.Unavailable
            },
            managerInstalled = signal.managerInstalled,
            lastSignal = "binder_died"
        )

        is ShizukuBridgeSignal.ProbeFailed -> current.copy(
            lifecycle = DeviceBridgeLifecycleStatus.Failed,
            managerInstalled = signal.managerInstalled,
            requestInFlight = false,
            lastSignal = signal.source,
            error = signal.error
        )
    }

    private fun fromSnapshot(signal: ShizukuBridgeSignal.SnapshotObserved): ShizukuBridgeState {
        if (!signal.binderAlive) {
            return ShizukuBridgeState(
                lifecycle = if (signal.managerInstalled) {
                    DeviceBridgeLifecycleStatus.InstalledButStopped
                } else {
                    DeviceBridgeLifecycleStatus.Unavailable
                },
                managerInstalled = signal.managerInstalled,
                lastSignal = signal.source
            )
        }

        val granted = signal.permissionGranted == true
        return ShizukuBridgeState(
            lifecycle = if (granted) {
                DeviceBridgeLifecycleStatus.Ready
            } else {
                DeviceBridgeLifecycleStatus.PermissionRequired
            },
            managerInstalled = signal.managerInstalled,
            binderAlive = true,
            permission = if (granted) ShizukuPermissionState.Granted else ShizukuPermissionState.Required,
            identity = identityForUid(signal.uid),
            uid = signal.uid,
            serverVersion = signal.serverVersion,
            lastSignal = signal.source
        )
    }

    private fun identityForUid(uid: Int?): DeviceBridgeIdentity = when (uid) {
        0 -> DeviceBridgeIdentity.Root
        2_000 -> DeviceBridgeIdentity.Shell
        null -> DeviceBridgeIdentity.Unknown
        else -> DeviceBridgeIdentity.Unknown
    }
}
