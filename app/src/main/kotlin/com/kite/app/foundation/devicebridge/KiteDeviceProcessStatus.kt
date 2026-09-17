package com.kite.app.foundation.devicebridge

data class KiteDeviceProcessStatus(
    val exitCode: Int,
    val state: String
) {
    fun encode(): String = "v${DeviceBridgeContract.PROTOCOL_VERSION} $state $exitCode\n"

    companion object {
        fun completed(exitCode: Int): KiteDeviceProcessStatus = KiteDeviceProcessStatus(exitCode, "completed")

        fun cancelled(): KiteDeviceProcessStatus = KiteDeviceProcessStatus(
            DeviceBridgeContract.EXIT_CANCELLED,
            "cancelled"
        )

        fun transportFailure(): KiteDeviceProcessStatus = KiteDeviceProcessStatus(
            DeviceBridgeContract.EXIT_TRANSPORT_ERROR,
            "transport_error"
        )

        fun parse(value: String): KiteDeviceProcessStatus? {
            val parts = value.trim().split(Regex("\\s+"))
            if (parts.size != 3 || parts[0] != "v${DeviceBridgeContract.PROTOCOL_VERSION}") return null
            val exitCode = parts[2].toIntOrNull() ?: return null
            return KiteDeviceProcessStatus(exitCode, parts[1])
        }
    }
}
