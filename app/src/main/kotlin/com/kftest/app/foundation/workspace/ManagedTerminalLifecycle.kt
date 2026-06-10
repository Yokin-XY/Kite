package com.kftest.app.foundation.workspace

fun ManagedTerminalStatus.isArchivedStatus(): Boolean {
    return this == ManagedTerminalStatus.EXITED ||
        this == ManagedTerminalStatus.FAILED ||
        this == ManagedTerminalStatus.STOPPED
}

fun ManagedTerminalStatus.isLiveProcessStatus(): Boolean {
    return this == ManagedTerminalStatus.RUNNING ||
        this == ManagedTerminalStatus.ATTACHED
}

fun ManagedTerminalStatus.isOpenableStatus(): Boolean {
    return !isArchivedStatus()
}

fun ManagedTerminalRecord.isArchivedRecord(): Boolean {
    return status.isArchivedStatus()
}
