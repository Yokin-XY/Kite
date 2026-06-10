package com.kftest.app.foundation.runtime

import com.kftest.app.foundation.service.BackgroundRuntimeStatus
import com.kftest.app.foundation.workspace.ManagedTerminalStatus
import kotlin.math.abs

object ProcessExitSemantics {

    private val MANAGED_STOP_SIGNALS = setOf(
        1,  // SIGHUP
        2,  // SIGINT
        9,  // SIGKILL
        15  // SIGTERM
    )

    fun terminalFinalStatus(
        currentStatus: ManagedTerminalStatus,
        exitCode: Int?
    ): ManagedTerminalStatus {
        return when (currentStatus) {
            ManagedTerminalStatus.STOPPED -> ManagedTerminalStatus.STOPPED
            ManagedTerminalStatus.EXITED -> ManagedTerminalStatus.EXITED
            ManagedTerminalStatus.FAILED -> ManagedTerminalStatus.FAILED
            else -> when {
                exitCode == 0 -> ManagedTerminalStatus.EXITED
                isManagedStopExit(exitCode) -> ManagedTerminalStatus.STOPPED
                else -> ManagedTerminalStatus.FAILED
            }
        }
    }

    fun backgroundExitError(title: String, exitCode: Int?): String? {
        if (isCleanExit(exitCode) || isManagedStopExit(exitCode)) {
            return null
        }
        return if (isRecognizedErrorExit(exitCode)) {
            "$title 退出码: ${exitCode ?: "unknown"}"
        } else {
            // Exit code not in the standard recognized set — include raw value
            // to make debugging easier when the process exits with an unexpected code.
            "$title 退出码（未识别）: ${exitCode ?: "unknown"}"
        }
    }

    fun backgroundFinalStatus(exitCode: Int?): BackgroundRuntimeStatus {
        return if (isCleanExit(exitCode) || isManagedStopExit(exitCode)) {
            BackgroundRuntimeStatus.STOPPED
        } else {
            BackgroundRuntimeStatus.ERROR
        }
    }

    private val RECOGNIZED_ERROR_CODES = setOf(
        1,  // general error
        2,  // misuse of shell builtins
        126, // command not executable
        127, // command not found
        128, // exit out of range
        // 129..255: signal-induced exits (normalized via normalizedSignal)
        // Any other exit code is considered unrecognized and gets a diagnostic marker.
    )

    private fun isRecognizedErrorExit(exitCode: Int?): Boolean {
        if (exitCode == null) return false
        if (exitCode in MANAGED_STOP_SIGNALS) return false  // SIGINT/SIGTERM/etc — not an error
        if (exitCode == 0) return false  // clean exit
        val signal = normalizedSignal(exitCode)
        if (signal != null) return true  // signal-induced exit (e.g. 137 = SIGKILL)
        return exitCode in RECOGNIZED_ERROR_CODES
    }

    fun isManagedStopExit(exitCode: Int?): Boolean {
        val signal = normalizedSignal(exitCode) ?: return false
        return signal in MANAGED_STOP_SIGNALS
    }

    private fun isCleanExit(exitCode: Int?): Boolean {
        return exitCode == 0
    }

    private fun normalizedSignal(exitCode: Int?): Int? {
        val value = exitCode ?: return null
        if (value < 0) {
            return abs(value)
        }
        if (value in 129..255) {
            return value - 128
        }
        return null
    }
}
