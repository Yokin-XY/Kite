package com.kftest.app.foundation.runtime

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class RuntimeBootstrapProgressSnapshot(
    val active: Boolean = false,
    val title: String = "",
    val detail: String = "",
    val percent: Int? = null,
    val updatedAt: Long = System.currentTimeMillis()
) {
    companion object {
        fun idle(): RuntimeBootstrapProgressSnapshot = RuntimeBootstrapProgressSnapshot()
    }
}

object RuntimeBootstrapProgress {
    private val _snapshot = MutableStateFlow(RuntimeBootstrapProgressSnapshot.idle())
    val snapshot: StateFlow<RuntimeBootstrapProgressSnapshot> = _snapshot

    @Synchronized
    fun stageStarted(stage: String) {
        mappedStage(stage, completed = false)?.let { publish(it) }
    }

    @Synchronized
    fun stageCompleted(stage: String) {
        mappedStage(stage, completed = true)?.let { publish(it) }
    }

    @Synchronized
    fun baseBootstrapOutput(line: String) {
        val clean = line.trim().take(180)
        if (clean.isBlank()) return
        val current = _snapshot.value
        val percent = when {
            clean.startsWith("Get:", ignoreCase = true) -> bump(current.percent, 62, 72)
            clean.startsWith("Hit:", ignoreCase = true) -> bump(current.percent, 60, 68)
            clean.contains("Reading package lists", ignoreCase = true) -> 66
            clean.contains("Building dependency tree", ignoreCase = true) -> 68
            clean.contains("Need to get", ignoreCase = true) -> 70
            clean.startsWith("Fetched", ignoreCase = true) -> 74
            clean.startsWith("Unpacking", ignoreCase = true) -> bump(current.percent, 76, 84)
            clean.startsWith("Setting up", ignoreCase = true) -> bump(current.percent, 84, 90)
            clean.startsWith("Processing triggers", ignoreCase = true) -> bump(current.percent, 90, 94)
            clean.contains("dpkg --configure", ignoreCase = true) -> 59
            else -> current.percent ?: 58
        }
        publish(
            RuntimeBootstrapProgressSnapshot(
                active = true,
                title = "正在补齐 Ubuntu 基础工具",
                detail = clean,
                percent = percent
            )
        )
    }

    @Synchronized
    fun failed(message: String) {
        publish(
            RuntimeBootstrapProgressSnapshot(
                active = false,
                title = "Ubuntu 初始化失败",
                detail = message,
                percent = _snapshot.value.percent
            )
        )
    }

    @Synchronized
    fun ready() {
        publish(
            RuntimeBootstrapProgressSnapshot(
                active = false,
                title = "Ubuntu 初始化完成",
                detail = "系统镜像、基础工具和工作区已经准备好。",
                percent = 100
            )
        )
    }

    private fun publish(snapshot: RuntimeBootstrapProgressSnapshot) {
        _snapshot.value = snapshot.copy(updatedAt = System.currentTimeMillis())
    }

    private fun mappedStage(stage: String, completed: Boolean): RuntimeBootstrapProgressSnapshot? {
        fun snapshot(title: String, detail: String, percent: Int) =
            RuntimeBootstrapProgressSnapshot(active = true, title = title, detail = detail, percent = percent)

        return when {
            stage.startsWith("prepareRuntime") ->
                snapshot("正在准备 Ubuntu 运行时", "正在检查 PRoot 和系统镜像资源。", if (completed) 12 else 4)
            stage == "ensureBaseImageBootstrap" ->
                snapshot("正在初始化基础环境", "系统镜像已经可用，正在检查基础工具链。", if (completed) 56 else 52)
            stage == "ensurePackageManagerFiles(base-image)" ->
                snapshot("正在初始化基础环境", "正在准备 apt/dpkg 工作目录。", if (completed) 55 else 53)
            stage == "ensureAndroidHostGroups(base-image)" ->
                snapshot("正在初始化基础环境", "正在同步 Android 宿主用户组。", if (completed) 56 else 55)
            stage == "writeRuntimeResolvConf(base-image)" ->
                snapshot("正在初始化基础环境", "正在写入容器网络配置。", if (completed) 57 else 56)
            stage == "ensureContainerTimeZone(base-image)" ->
                snapshot("正在初始化基础环境", "正在同步容器时区。", if (completed) 58 else 57)
            stage == "installBootstrapPackages(base-image)" ->
                snapshot("正在补齐 Ubuntu 基础工具", "apt/dpkg 正在安装命令行基础工具。", if (completed) 94 else 58)
            stage == "normalizeSyntheticHostLinks(base-image)" ->
                snapshot("正在收尾基础环境", "正在整理基础镜像的宿主链接。", if (completed) 95 else 94)
            stage.startsWith("ensureBaseImageReady(default-container)") ->
                snapshot("正在准备默认容器", "正在确认基础镜像可用。", if (completed) 86 else 84)
            stage.startsWith("loadRegistry") ->
                snapshot("正在准备默认容器", "正在读取容器登记信息。", if (completed) 87 else 86)
            stage.startsWith("ensureContainerFilesystem") ->
                snapshot("正在准备默认容器", "正在检查当前空间的 rootfs 和工作区。", if (completed) 94 else 88)
            stage.startsWith("cloneBaseImage") ->
                snapshot("正在创建默认容器", "正在从基础镜像复制当前空间 rootfs。", if (completed) 91 else 88)
            stage.startsWith("ensureContainerBootstrap") ->
                snapshot("正在准备默认容器", "正在补齐当前空间的容器基础环境。", if (completed) 94 else 91)
            stage.startsWith("ensureWorkspace(") ->
                snapshot("正在准备工作区", "正在创建默认工作区目录。", if (completed) 96 else 95)
            stage.startsWith("ensureWorkspaceBuildSupport") ->
                snapshot("正在准备工作区", "正在准备构建辅助目录。", if (completed) 97 else 96)
            stage.startsWith("ensureWorkspaceSystemComponents") ->
                snapshot("正在准备工作区", "正在安装工作区系统组件。", if (completed) 98 else 97)
            stage.startsWith("ensureExternalExchange") ->
                snapshot("正在准备投递区", "正在确认 Android 与 Ubuntu 的共享投递目录。", if (completed) 99 else 98)
            else -> null
        }
    }

    private fun bump(current: Int?, floor: Int, ceiling: Int): Int {
        val base = (current ?: floor).coerceAtLeast(floor)
        return (base + 1).coerceAtMost(ceiling)
    }
}
