package com.kite.app.foundation.runtime

import android.system.OsConstants
import com.kite.app.foundation.jni.KFJni
import kotlinx.coroutines.delay
import java.io.File

data class HostTerminationOutcome(
    val pid: Int,
    val usedProcessGroup: Boolean,
    val sentHangup: Boolean,
    val sentTerminate: Boolean,
    val sentKill: Boolean,
    val exited: Boolean
)

object HostProcessTerminator {

    private const val TERM_GRACE_MS = 900L
    private const val KILL_GRACE_MS = 350L
    private const val POLL_INTERVAL_MS = 75L

    suspend fun terminateTerminalProcessGroup(
        pid: Int,
        log: (String) -> Unit = {}
    ): HostTerminationOutcome {
        return terminateInternal(
            pid = pid,
            preferProcessGroup = true,
            sendHangupFirst = true,
            immediateKill = false,
            log = log
        )
    }

    suspend fun terminateHostProcess(
        pid: Int,
        log: (String) -> Unit = {}
    ): HostTerminationOutcome {
        return terminateInternal(
            pid = pid,
            preferProcessGroup = false,
            sendHangupFirst = false,
            immediateKill = false,
            log = log
        )
    }

    /**
     * 对应用重启后只剩 PID 的进程，每次发信号和等待前都重验启动代次。
     * guard 失配代表原代次已退出；此时不得向复用该 PID 的新进程发信号。
     */
    suspend fun terminateExactHostProcess(
        pid: Int,
        isExactProcess: () -> Boolean,
        log: (String) -> Unit = {},
    ): HostTerminationOutcome {
        return terminateInternal(
            pid = pid,
            preferProcessGroup = false,
            sendHangupFirst = false,
            immediateKill = false,
            identityGuard = isExactProcess,
            log = log,
        )
    }

    suspend fun killHostProcess(
        pid: Int,
        log: (String) -> Unit = {}
    ): HostTerminationOutcome {
        return terminateInternal(
            pid = pid,
            preferProcessGroup = false,
            sendHangupFirst = false,
            immediateKill = true,
            log = log
        )
    }

    private suspend fun terminateInternal(
        pid: Int,
        preferProcessGroup: Boolean,
        sendHangupFirst: Boolean,
        immediateKill: Boolean,
        identityGuard: (() -> Boolean)? = null,
        log: (String) -> Unit
    ): HostTerminationOutcome {
        if (pid <= 0) {
            return HostTerminationOutcome(
                pid = pid,
                usedProcessGroup = preferProcessGroup,
                sentHangup = false,
                sentTerminate = false,
                sentKill = false,
                exited = true
            )
        }

        if (!isAlive(pid, identityGuard)) {
            log("进程已退出，无需终止: pid=$pid")
            return HostTerminationOutcome(
                pid = pid,
                usedProcessGroup = preferProcessGroup,
                sentHangup = false,
                sentTerminate = false,
                sentKill = false,
                exited = true
            )
        }

        val signalTarget = if (preferProcessGroup) -pid else pid
        val sentHangup = if (sendHangupFirst) {
            sendSignal(signalTarget, pid, OsConstants.SIGHUP, identityGuard, log)
        } else {
            false
        }

        if (sentHangup) {
            delay(120L)
            if (!isAlive(pid, identityGuard)) {
                return HostTerminationOutcome(
                    pid = pid,
                    usedProcessGroup = preferProcessGroup,
                    sentHangup = true,
                    sentTerminate = false,
                    sentKill = false,
                    exited = true
                )
            }
        }

        val sentTerminate = if (immediateKill) {
            false
        } else {
            sendSignal(signalTarget, pid, OsConstants.SIGTERM, identityGuard, log)
        }
        if (!immediateKill && waitForExit(pid, TERM_GRACE_MS, identityGuard)) {
            return HostTerminationOutcome(
                pid = pid,
                usedProcessGroup = preferProcessGroup,
                sentHangup = sentHangup,
                sentTerminate = sentTerminate,
                sentKill = false,
                exited = true
            )
        }

        val sentKill = sendSignal(signalTarget, pid, OsConstants.SIGKILL, identityGuard, log)
        val exited = waitForExit(pid, KILL_GRACE_MS, identityGuard)
        return HostTerminationOutcome(
            pid = pid,
            usedProcessGroup = preferProcessGroup,
            sentHangup = sentHangup,
            sentTerminate = sentTerminate,
            sentKill = sentKill,
            exited = exited
        )
    }

    private fun sendSignal(
        primaryTarget: Int,
        fallbackPid: Int,
        signal: Int,
        identityGuard: (() -> Boolean)?,
        log: (String) -> Unit
    ): Boolean {
        if (identityGuard?.invoke() == false) {
            log("进程身份已变化，取消信号: pid=$fallbackPid signal=$signal")
            return false
        }
        val primaryResult = runCatching {
            KFJni.sendSignal(primaryTarget, signal)
        }.getOrElse {
            log("发送信号异常: target=$primaryTarget signal=$signal error=${it.message}")
            false
        }
        if (primaryResult) {
            return true
        }

        if (primaryTarget < 0 && fallbackPid > 0) {
            val fallbackResult = runCatching {
                KFJni.sendSignal(fallbackPid, signal)
            }.getOrElse {
                log("发送回退信号异常: target=$fallbackPid signal=$signal error=${it.message}")
                false
            }
            if (fallbackResult) {
                log("进程组信号失败，已回退到单 PID: pid=$fallbackPid signal=$signal")
            }
            return fallbackResult
        }

        return false
    }

    private suspend fun waitForExit(
        pid: Int,
        timeoutMs: Long,
        identityGuard: (() -> Boolean)?,
    ): Boolean {
        val attempts = (timeoutMs / POLL_INTERVAL_MS).toInt().coerceAtLeast(1)
        repeat(attempts) {
            if (!isAlive(pid, identityGuard)) {
                return true
            }
            delay(POLL_INTERVAL_MS)
        }
        return !isAlive(pid, identityGuard)
    }

    private fun isAlive(pid: Int, identityGuard: (() -> Boolean)?): Boolean {
        return identityGuard?.invoke() != false && File("/proc/$pid").exists()
    }
}
