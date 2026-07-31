package com.kite.app.foundation.service

import com.kite.app.foundation.runtime.ProcessExitSemantics

fun BackgroundRuntimeStatus.isActiveStatus(): Boolean {
    return this == BackgroundRuntimeStatus.RUNNING ||
        this == BackgroundRuntimeStatus.STARTING
}

fun BackgroundRuntimeStatus.isTerminalStatus(): Boolean {
    return this == BackgroundRuntimeStatus.STOPPED ||
        this == BackgroundRuntimeStatus.ERROR
}

fun BackgroundRuntimeRecord.isActiveRuntime(): Boolean {
    return status.isActiveStatus()
}

internal object BackgroundRuntimeSpacePolicy {
    fun mayStart(recordSpaceId: String, activeSpaceId: String): Boolean {
        return recordSpaceId.isNotBlank() && recordSpaceId == activeSpaceId
    }

    fun confirmedStopped(record: BackgroundRuntimeRecord, stoppedAt: Long): BackgroundRuntimeRecord {
        return record.copy(
            status = if (record.status.isActiveStatus()) {
                BackgroundRuntimeStatus.STOPPED
            } else {
                record.status
            },
            healthStatus = BackgroundRuntimeHealthStatus.INACTIVE,
            pid = null,
            processBootId = null,
            processStartTicks = null,
            lastStoppedAt = if (record.status.isActiveStatus()) stoppedAt else record.lastStoppedAt,
            lastAdmissionDeferredAt = null,
            lastAdmissionSource = null,
            lastAdmissionReason = null,
        )
    }
}

internal object BackgroundRuntimeRestartGate {
    fun blocksAutomaticStart(record: BackgroundRuntimeRecord): Boolean {
        return ProcessExitSemantics.isCommandUnavailableExit(record.lastExitCode)
    }
}

object BackgroundRuntimeHealthText {
    const val STARTING = "启动中，等待健康探测"
    const val WAITING_FOR_PROBE = "进程已拉起，等待健康探测"
    const val EXISTING_WAITING_FOR_PROBE = "进程已存在，等待健康探测"
    const val UNCONFIGURED = "未配置健康探测"
    const val STOPPED = "进程已停止"
    const val NOT_RUNNING = "进程未运行"
    const val STOPPING_PENDING = "已请求停止，等待进程退出确认"
    const val STOPPING_REVIEW = "已请求停止，进程身份待确认"
    const val IDENTITY_REVIEW = "进程身份待确认"
}
