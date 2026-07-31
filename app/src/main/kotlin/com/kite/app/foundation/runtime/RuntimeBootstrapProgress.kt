package com.kite.app.foundation.runtime

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
    private var reportingDepth = 0

    @Synchronized
    fun beginBootstrapRun() {
        reportingDepth += 1
        _snapshot.value = RuntimeBootstrapProgressSnapshot.idle()
    }

    @Synchronized
    fun endBootstrapRun() {
        reportingDepth = (reportingDepth - 1).coerceAtLeast(0)
    }

    @Synchronized
    fun stageStarted(stage: String) {
        if (!isReporting()) return
        mappedStage(stage, completed = false)?.let { publish(it) }
    }

    @Synchronized
    fun stageCompleted(stage: String) {
        if (!isReporting()) return
        mappedStage(stage, completed = true)?.let { publish(it) }
    }

    @Synchronized
    fun bundledToolStarted(name: String, index: Int, total: Int) {
        if (!isReporting()) return
        publish(bundledToolSnapshot(name, index, total, completed = false, failed = false))
    }

    @Synchronized
    fun bundledToolCompleted(name: String, index: Int, total: Int, failed: Boolean) {
        if (!isReporting()) return
        publish(bundledToolSnapshot(name, index, total, completed = true, failed = failed))
    }

    @Synchronized
    fun failed(message: String) {
        if (!isReporting()) return
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
    fun ready(detail: String = "系统镜像、基础工具和工作区已经准备好。") {
        publish(
            RuntimeBootstrapProgressSnapshot(
                active = false,
                title = "Ubuntu 初始化完成",
                detail = detail,
                percent = 100
            )
        )
    }

    @Synchronized
    internal fun resetForTesting() {
        reportingDepth = 0
        _snapshot.value = RuntimeBootstrapProgressSnapshot.idle()
    }

    private fun isReporting(): Boolean = reportingDepth > 0

    private fun publish(snapshot: RuntimeBootstrapProgressSnapshot) {
        val previous = _snapshot.value
        val nextPercent = if (previous.active && snapshot.active && previous.percent != null && snapshot.percent != null) {
            snapshot.percent.coerceAtLeast(previous.percent)
        } else {
            snapshot.percent
        }
        _snapshot.value = snapshot.copy(percent = nextPercent, updatedAt = System.currentTimeMillis())
    }

    private fun mappedStage(stage: String, completed: Boolean): RuntimeBootstrapProgressSnapshot? {
        fun snapshot(title: String, detail: String, percent: Int) =
            RuntimeBootstrapProgressSnapshot(active = true, title = title, detail = detail, percent = percent)

        return when {
            stage.startsWith("prepareRuntime") ->
                snapshot("正在准备 Ubuntu 运行时", "正在检查 PRoot 和系统镜像资源。", if (completed) 50 else 4)
            stage == "ensureBaseImageBootstrap" ->
                snapshot("正在初始化基础环境", "系统镜像已经可用，正在检查基础工具链。", if (completed) 55 else 52)
            stage == "ensurePackageManagerFiles(base-image)" ->
                snapshot("正在初始化基础环境", "正在准备离线系统工作目录。", if (completed) 55 else 53)
            stage == "ensureAndroidHostGroups(base-image)" ->
                snapshot("正在初始化基础环境", "正在同步 Android 宿主用户组。", if (completed) 56 else 55)
            stage == "writeRuntimeResolvConf(base-image)" ->
                snapshot("正在初始化基础环境", "正在写入容器网络配置。", if (completed) 57 else 56)
            stage == "ensureContainerTimeZone(base-image)" ->
                snapshot("正在初始化基础环境", "正在同步容器时区。", if (completed) 58 else 57)
            stage == "normalizeSyntheticHostLinks(base-image)" ->
                snapshot("正在收尾基础环境", "正在整理基础镜像的宿主链接。", if (completed) 58 else 57)
            stage.startsWith("ensureBaseImageReady(default-container)") ->
                snapshot("正在准备默认容器", "正在确认基础镜像可用。", if (completed) 58 else 56)
            stage.startsWith("loadRegistry") ->
                snapshot("正在准备默认容器", "正在读取容器登记信息。", if (completed) 59 else 58)
            stage.startsWith("ensureContainerFilesystem") ->
                snapshot("正在准备默认容器", "正在检查当前空间的 rootfs 和工作区。", if (completed) 62 else 59)
            stage.startsWith("cloneBaseImage") ->
                snapshot("正在创建默认容器", "正在从基础镜像复制当前空间 rootfs。", if (completed) 62 else 59)
            stage.startsWith("ensureContainerBootstrap") ->
                snapshot("正在准备默认容器", "正在校验当前空间的离线基础环境。", if (completed) 64 else 62)
            stage.startsWith("writeContainerRootfsReady") ->
                snapshot("正在准备默认容器", "正在记录当前空间 rootfs 状态。", if (completed) 65 else 64)
            stage.startsWith("ensureWorkspace(") ->
                snapshot("正在准备工作区", "正在创建默认工作区目录。", if (completed) 67 else 65)
            stage.startsWith("ensureWorkspaceBuildSupport") ->
                snapshot("正在准备工作区", "正在准备构建辅助目录。", if (completed) 70 else 67)
            stage.startsWith("ensureWorkspaceSystemComponents") ->
                snapshot("正在安装 Kite 系统命令", "正在写入 Kite 内置命令和桥接脚本。", if (completed) 76 else 70)
            stage == "ensureRuntimeOperational" ->
                snapshot("正在启动 Ubuntu", "正在用 PRoot 执行最小 shell。", if (completed) 80 else 78)
            stage == "installBundledToolchain" ->
                snapshot("正在安装内置工具包", "正在按内置清单安装工具。", if (completed) 99 else 80)
            else -> null
        }
    }

    private fun bundledToolSnapshot(
        name: String,
        index: Int,
        total: Int,
        completed: Boolean,
        failed: Boolean
    ): RuntimeBootstrapProgressSnapshot {
        val safeTotal = total.coerceAtLeast(1)
        val safeIndex = index.coerceIn(1, safeTotal)
        val start = 80
        val end = 97
        val slotStart = start + ((safeIndex - 1) * (end - start) / safeTotal)
        val slotEnd = start + (safeIndex * (end - start) / safeTotal)
        val percent = if (completed) slotEnd else slotStart
        val status = when {
            completed && failed -> "已记录失败，继续下一项"
            completed -> "已完成，继续下一项"
            else -> "正在安装"
        }
        return RuntimeBootstrapProgressSnapshot(
            active = true,
            title = "正在安装内置工具包",
            detail = "第 $safeIndex/$safeTotal 项：$name，$status。",
            percent = percent
        )
    }
}
