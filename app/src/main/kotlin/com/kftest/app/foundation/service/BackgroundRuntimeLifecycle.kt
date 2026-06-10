package com.kftest.app.foundation.service

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

object BackgroundRuntimeHealthText {
    const val STARTING = "启动中，等待健康探测"
    const val WAITING_FOR_PROBE = "进程已拉起，等待健康探测"
    const val EXISTING_WAITING_FOR_PROBE = "进程已存在，等待健康探测"
    const val UNCONFIGURED = "未配置健康探测"
    const val STOPPED = "进程已停止"
    const val NOT_RUNNING = "进程未运行"
}
