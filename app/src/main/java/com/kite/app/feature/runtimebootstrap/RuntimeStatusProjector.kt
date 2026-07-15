package com.kite.app.feature.runtimebootstrap

import com.kite.app.application.runtimebootstrap.RuntimeBootstrapSnapshot
import com.kite.app.application.runtimebootstrap.RuntimeBootstrapStage
import com.kite.app.application.runtimebootstrap.RuntimePermissionKind
import com.kite.app.application.runtimebootstrap.RuntimeRootfsPhase
import java.util.Locale

/** 把权限、部署和运行数量投影成唯一运行状态显示合同。 */
internal object RuntimeStatusProjector {
    const val FIRST_RUN_PERMISSION_TITLE = "首次授权"

    fun project(
        snapshot: RuntimeBootstrapSnapshot,
        counts: RuntimeStatusCounts = RuntimeStatusCounts(),
        onboarding: RuntimePermissionOnboardingUiInput = RuntimePermissionOnboardingUiInput()
    ): RuntimeStatusUiState {
        if (onboarding.active) return onboardingState(onboarding, counts)
        permissionState(snapshot, counts)?.let { return it }
        rootfsState(snapshot, counts)?.let { return it }
        deploymentState(snapshot, counts)?.let { return it }
        bootstrapState(snapshot, counts)?.let { return it }
        return readinessState(snapshot, counts)
    }

    private fun onboardingState(
        onboarding: RuntimePermissionOnboardingUiInput,
        counts: RuntimeStatusCounts
    ): RuntimeStatusUiState {
        val labels = permissionLabels(
            onboarding.missingPermissions,
            onboarding.needsAllFilesAccess,
            onboarding.needsNotificationChannelSetup
        )
        return RuntimeStatusUiState(
            title = FIRST_RUN_PERMISSION_TITLE,
            detail = buildList {
                add("Kite 会先集中申请一次系统能力：${labels.joinToString("、")}。")
                if (onboarding.needsNotificationChannelSetup) {
                    add("系统会打开“首页卡片进度”类别；请允许横幅，以便等待下一步时及时提醒。")
                }
                add("如果你暂时拒绝，Kite 不会反复打扰；之后可从设置中的“首页卡片通知”重新配置。")
                add("完成授权后会继续检查 Ubuntu 基础环境。")
            }.joinToString("\n"),
            blocksUbuntuActions = true,
            isProblem = false,
            permissionOnboarding = true,
            primaryAction = when {
                onboarding.missingPermissions.isNotEmpty() -> RuntimeStatusAction.RequestRuntimePermissions
                onboarding.needsAllFilesAccess -> RuntimeStatusAction.OpenAllFilesSettings
                else -> RuntimeStatusAction.RetryDeployment
            },
            primaryActionLabel = when {
                onboarding.missingPermissions.isNotEmpty() -> "开始授权"
                onboarding.needsAllFilesAccess -> "打开文件设置"
                onboarding.needsNotificationChannelSetup -> "设置卡片通知"
                else -> "继续"
            },
            counts = counts
        )
    }

    private fun permissionState(
        snapshot: RuntimeBootstrapSnapshot,
        counts: RuntimeStatusCounts
    ): RuntimeStatusUiState? {
        if (snapshot.baseImageReady || snapshot.permissions.ready) return null
        val labels = permissionLabels(snapshot.permissions.missing, snapshot.permissions.needsAllFilesAccess)
        val action = if (snapshot.permissions.missing.isNotEmpty()) {
            RuntimeStatusAction.RequestRuntimePermissions
        } else {
            RuntimeStatusAction.OpenAllFilesSettings
        }
        return RuntimeStatusUiState(
            title = "需要完成首次授权",
            detail = listOf(
                "首次部署 Ubuntu 前需要先完成文件访问授权，否则共享投放区、导入目录和部分系统准备步骤可能被系统拦截。",
                "未完成：${labels.joinToString("、")}。",
                "点击下方按钮后，请按系统提示完成授权；返回 Kite 后会自动继续解压 Ubuntu。"
            ).joinToString("\n"),
            blocksUbuntuActions = true,
            isProblem = false,
            primaryAction = action,
            primaryActionLabel = if (action == RuntimeStatusAction.RequestRuntimePermissions) {
                "弹出权限请求"
            } else {
                "打开文件访问设置"
            },
            counts = counts
        )
    }

    private fun rootfsState(
        snapshot: RuntimeBootstrapSnapshot,
        counts: RuntimeStatusCounts
    ): RuntimeStatusUiState? {
        val rootfs = snapshot.rootfs
        val progressText = rootfsProgressText(rootfs.percent, rootfs.entriesExtracted, rootfs.bytesRead)
        return when (rootfs.phase) {
            RuntimeRootfsPhase.Preparing,
            RuntimeRootfsPhase.Extracting,
            RuntimeRootfsPhase.Verifying -> RuntimeStatusUiState(
                title = if (rootfs.phase == RuntimeRootfsPhase.Verifying) {
                    "正在校验 Ubuntu 系统镜像"
                } else {
                    "正在解压 Ubuntu 系统镜像"
                },
                detail = rootfs.message.ifBlank {
                    if (rootfs.entriesExtracted > 0) {
                        "已处理 ${rootfs.entriesExtracted} 个文件，完成后会自动继续启动。"
                    } else {
                        "正在准备系统镜像，完成后会自动继续启动。"
                    }
                },
                blocksUbuntuActions = true,
                isProblem = false,
                progressPercent = rootfs.percent?.let { 5 + (it * 45 / 100) },
                progressText = progressText,
                showProgress = true,
                autoOpenPanel = rootfs.phase != RuntimeRootfsPhase.Verifying,
                primaryAction = RuntimeStatusAction.OpenProcessManagement,
                primaryActionLabel = "查看进程",
                counts = counts,
                autoOpenGeneration = rootfs.startedAt
            )
            RuntimeRootfsPhase.Failed -> RuntimeStatusUiState(
                title = "Ubuntu 部署失败",
                detail = listOfNotNull(
                    rootfs.errorMessage?.takeIf(String::isNotBlank),
                    "未完成的 rootfs 不会被当作成功使用，下次启动会清理后重新解压。"
                ).joinToString("\n"),
                blocksUbuntuActions = false,
                isProblem = true,
                progressPercent = rootfs.percent,
                progressText = progressText,
                showProgress = progressText.isNotBlank(),
                primaryAction = RuntimeStatusAction.RetryDeployment,
                primaryActionLabel = "重新检查 / 继续部署",
                counts = counts,
                autoOpenGeneration = rootfs.startedAt
            )
            RuntimeRootfsPhase.Ready -> if (
                !snapshot.deployment.active &&
                snapshot.bootstrapStage in setOf(RuntimeBootstrapStage.RootfsExtracting, RuntimeBootstrapStage.BaseBootstrap)
            ) {
                RuntimeStatusUiState(
                    title = "正在初始化基础环境",
                    detail = "系统镜像已经解压完成，正在准备 PRoot、工作区和内置工具安装路径。",
                    blocksUbuntuActions = true,
                    isProblem = false,
                    progressPercent = 55,
                    progressText = "总进度 55%",
                    showProgress = true,
                    counts = counts
                )
            } else null
            RuntimeRootfsPhase.Idle -> null
        }
    }

    private fun deploymentState(
        snapshot: RuntimeBootstrapSnapshot,
        counts: RuntimeStatusCounts
    ): RuntimeStatusUiState? {
        val progress = snapshot.deployment
        if (!progress.active) return null
        return RuntimeStatusUiState(
            title = progress.title.ifBlank { "正在部署 Ubuntu" },
            detail = progress.detail.ifBlank { "正在执行当前初始化步骤。" },
            blocksUbuntuActions = true,
            isProblem = false,
            progressPercent = progress.percent,
            progressText = progress.percent?.let { "总进度 $it%" }.orEmpty(),
            showProgress = progress.percent != null,
            counts = counts
        )
    }

    private fun bootstrapState(
        snapshot: RuntimeBootstrapSnapshot,
        counts: RuntimeStatusCounts
    ): RuntimeStatusUiState? = when (snapshot.bootstrapStage) {
        RuntimeBootstrapStage.Failed -> RuntimeStatusUiState(
            title = "Ubuntu 部署失败",
            detail = snapshot.bootstrapError ?: "初始化过程中出现未知错误。",
            blocksUbuntuActions = false,
            isProblem = true,
            primaryAction = RuntimeStatusAction.RetryDeployment,
            primaryActionLabel = "重新检查 / 继续部署",
            counts = counts
        )
        RuntimeBootstrapStage.ServiceRequested,
        RuntimeBootstrapStage.RootfsExtracting,
        RuntimeBootstrapStage.BaseBootstrap,
        RuntimeBootstrapStage.SpaceReady -> RuntimeStatusUiState(
            title = when (snapshot.bootstrapStage) {
                RuntimeBootstrapStage.ServiceRequested -> "正在唤起 Ubuntu 运行环境"
                RuntimeBootstrapStage.RootfsExtracting -> "正在解压系统镜像"
                RuntimeBootstrapStage.BaseBootstrap -> "正在初始化基础环境"
                RuntimeBootstrapStage.SpaceReady -> "正在准备工作区"
                else -> "正在部署 Ubuntu"
            },
            detail = "部署期间 Ubuntu 卡片暂时锁定，完成后会自动恢复。",
            blocksUbuntuActions = true,
            isProblem = false,
            showProgress = snapshot.bootstrapStage == RuntimeBootstrapStage.RootfsExtracting,
            progressText = if (snapshot.bootstrapStage == RuntimeBootstrapStage.RootfsExtracting) "正在等待解压进度" else "",
            counts = counts
        )
        RuntimeBootstrapStage.Idle,
        RuntimeBootstrapStage.Ready -> null
    }

    private fun readinessState(
        snapshot: RuntimeBootstrapSnapshot,
        counts: RuntimeStatusCounts
    ): RuntimeStatusUiState = when {
        !snapshot.readinessProbeCompleted -> RuntimeStatusUiState.checking(counts)
        snapshot.baseImageReady && snapshot.defaultContainerReady && snapshot.bootstrapResourcesSettled ->
            RuntimeStatusUiState(
                title = "",
                detail = "",
                blocksUbuntuActions = false,
                isProblem = false,
                visible = false,
                counts = counts
            )
        snapshot.baseImageReady -> RuntimeStatusUiState(
            title = "Ubuntu 未部署",
            detail = "系统镜像已经解压完成，正在准备 PRoot、工作区和内置工具安装路径。",
            blocksUbuntuActions = true,
            isProblem = false,
            showProgress = true,
            progressText = "等待首次部署",
            primaryAction = RuntimeStatusAction.RetryDeployment,
            primaryActionLabel = "重新检查 / 继续部署",
            counts = counts
        )
        else -> RuntimeStatusUiState(
            title = "Ubuntu 未部署",
            detail = "首次启动 Ubuntu 卡片或终端时会先解压系统镜像。",
            blocksUbuntuActions = true,
            isProblem = false,
            showProgress = true,
            progressText = "等待首次部署",
            primaryAction = RuntimeStatusAction.RetryDeployment,
            primaryActionLabel = "重新检查 / 继续部署",
            counts = counts
        )
    }

    private fun permissionLabels(
        permissions: Set<RuntimePermissionKind>,
        needsAllFilesAccess: Boolean,
        needsNotificationChannelSetup: Boolean = false
    ): List<String> = buildList {
        if (needsAllFilesAccess) add("全部文件访问")
        if (needsNotificationChannelSetup) add("首页卡片通知")
        permissions.forEach { permission ->
            add(
                when (permission) {
                    RuntimePermissionKind.FileRead -> "文件读取"
                    RuntimePermissionKind.FileWrite -> "文件写入"
                    RuntimePermissionKind.Notifications -> "系统通知"
                }
            )
        }
    }.distinct().ifEmpty { listOf("当前所需权限") }

    private fun rootfsProgressText(percent: Int?, entriesExtracted: Int, bytesRead: Long): String = when {
        percent != null -> "rootfs 解压 $percent% · 已处理 $entriesExtracted 个文件"
        entriesExtracted > 0 -> "已处理 $entriesExtracted 个文件"
        bytesRead > 0L -> "已读取 ${formatBytes(bytesRead)}"
        else -> ""
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes < 1024L) return "${bytes}B"
        val kb = bytes / 1024.0
        if (kb < 1024.0) return String.format(Locale.US, "%.1fKB", kb)
        val mb = kb / 1024.0
        if (mb < 1024.0) return String.format(Locale.US, "%.1fMB", mb)
        return String.format(Locale.US, "%.1fGB", mb / 1024.0)
    }
}
