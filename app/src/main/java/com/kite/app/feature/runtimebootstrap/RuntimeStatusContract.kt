package com.kite.app.feature.runtimebootstrap

import com.kite.app.application.runtimebootstrap.RuntimePermissionKind

internal enum class RuntimeStatusAction {
    RequestRuntimePermissions,
    OpenAllFilesSettings,
    RetryDeployment,
    OpenProcessManagement
}

internal data class RuntimeStatusCounts(
    val runningCards: Int = 0,
    val runningTerminals: Int = 0,
    val runningProcesses: Int = 0
)

internal data class RuntimePermissionOnboardingUiInput(
    val active: Boolean = false,
    val missingPermissions: Set<RuntimePermissionKind> = emptySet(),
    val needsAllFilesAccess: Boolean = false
)

internal data class RuntimeStatusUiState(
    val title: String,
    val detail: String,
    val blocksUbuntuActions: Boolean,
    val isProblem: Boolean,
    val visible: Boolean = true,
    val progressPercent: Int? = null,
    val progressText: String = "",
    val showProgress: Boolean = false,
    val autoOpenPanel: Boolean = false,
    val permissionOnboarding: Boolean = false,
    val primaryAction: RuntimeStatusAction = RuntimeStatusAction.OpenProcessManagement,
    val primaryActionLabel: String = "查看进程",
    val counts: RuntimeStatusCounts = RuntimeStatusCounts(),
    val autoOpenGeneration: Long = 0L
) {
    val requiresPermission: Boolean
        get() = primaryAction == RuntimeStatusAction.RequestRuntimePermissions ||
            primaryAction == RuntimeStatusAction.OpenAllFilesSettings

    val firstRunPermissionOnboarding: Boolean
        get() = permissionOnboarding

    val shouldShowGate: Boolean
        get() = visible && (blocksUbuntuActions || requiresPermission || firstRunPermissionOnboarding || isProblem)

    val statusLabel: String
        get() = when {
            isProblem -> "异常"
            requiresPermission -> "待授权"
            showProgress && progressPercent != null -> "解压 $progressPercent%"
            blocksUbuntuActions -> "部署中"
            visible -> "未部署"
            else -> "就绪"
        }

    companion object {
        fun checking(counts: RuntimeStatusCounts = RuntimeStatusCounts()): RuntimeStatusUiState =
            RuntimeStatusUiState(
                title = "正在检查 Ubuntu",
                detail = "正在确认系统镜像、权限和基础运行环境。",
                blocksUbuntuActions = true,
                isProblem = false,
                counts = counts
            )
    }
}
