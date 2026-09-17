package com.kite.app.foundation.runtime

import com.kite.app.foundation.devicebridge.DeviceBridgeBackendMode
import com.kite.app.foundation.devicebridge.DeviceBridgeCatalog
import com.kite.app.foundation.devicebridge.DeviceBridgeContract
import com.kite.app.foundation.workspace.WorkspaceBuildSupport

/**
 * Container-visible ADB contract.
 *
 * Host-self ADB is intentionally modelled as an Android APK bridge, not as a
 * Linux/proot adb server that scans the phone back from inside the container.
 */
object AdbBridgeContract {
    const val MODE = "apk_bridge"
    const val HOST_SELF_SERIAL = "kf-host-self"
    const val HOST_SELF_SOURCE = "apk_bridge_contract_v0"
    const val PROOT_SERVER_DEFAULT = "disabled"
    const val BRIDGE_STATUS = "listed"
    const val BRIDGE_DIR = "/workspace/.kf/adb-bridge"

    fun buildEnvironment(
        status: ShizukuBridgeStatus.Snapshot = ShizukuBridgeStatus.Snapshot(
            status = BRIDGE_STATUS,
            available = false,
            permission = "unknown",
            source = "apk_bridge_contract_v0",
            uid = "",
            version = ""
        ),
        backendMode: DeviceBridgeBackendMode = DeviceBridgeBackendMode.Shizuku,
    ): LinkedHashMap<String, String> {
        return linkedMapOf(
            "KF_ADB_MODE" to MODE,
            "KF_ADB_HOST_SELF_SERIAL" to HOST_SELF_SERIAL,
            "KF_ADB_HOST_SELF_SOURCE" to HOST_SELF_SOURCE,
            "KF_ADB_PROOT_SERVER_DEFAULT" to PROOT_SERVER_DEFAULT,
            "KF_ADB_BRIDGE_STATUS" to status.status,
            "KF_ADB_BRIDGE_HELPER" to "kf-adb-bridge",
            "KF_ADB_BRIDGE_DIR" to BRIDGE_DIR,
            "KF_ADB_PERMISSION_SOURCE" to status.source,
            "KF_ADB_SHIZUKU_AVAILABLE" to status.available.toString(),
            "KF_ADB_SHIZUKU_PERMISSION" to status.permission,
            "KF_ADB_SHIZUKU_UID" to status.uid,
            "KF_ADB_SHIZUKU_VERSION" to status.version,
            "KF_ADB_SHIZUKU_ERROR" to (status.error ?: ""),
            "KF_DEVICE_BRIDGE_PROTOCOL_VERSION" to DeviceBridgeContract.PROTOCOL_VERSION.toString(),
            "KF_DEVICE_SELECTED_BACKEND" to backendMode.storageValue,
            "KF_DEVICE_IMPLEMENTED_CAPABILITIES" to
                DeviceBridgeCatalog.implementedCapabilityIds.joinToString(","),
            "KF_DEVICE_CLI" to WorkspaceBuildSupport.CONTAINER_KITE_DEVICE_PATH,
            "KF_DEVICE_CAPABILITY_CATALOG_PATH" to
                WorkspaceBuildSupport.CONTAINER_DEVICE_BRIDGE_CAPABILITY_CATALOG_PATH
        )
    }
}
