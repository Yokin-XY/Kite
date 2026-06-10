package com.kftest.app.foundation.workspace

import android.content.Context
import com.kftest.app.foundation.runtime.AssetExtractor
import com.kftest.app.foundation.runtime.ContainerRecord
import com.kftest.app.foundation.runtime.ContainerExecConfig
import com.kftest.app.foundation.runtime.ContainerLaunchConfig
import com.kftest.app.foundation.runtime.ExternalExchangeManager
import com.kftest.app.foundation.runtime.KFContainerManager
import com.kftest.app.foundation.runtime.RuntimeActionKind
import com.kftest.app.foundation.runtime.RuntimeBoundary
import com.kftest.app.foundation.runtime.RuntimeBoundarySnapshot
import com.kftest.app.foundation.runtime.RuntimePathRole
import java.io.File

internal data class WorkSurfaceRuntimeDefaults(
    val workspaceDir: String,
    val rootHomeDir: String,
    val defaultProjectDir: String
)

/**
 * 工作面层访问建房层的唯一正确 facade。
 *
 * 只放：工作面进入底层所需的稳定入口与默认值。
 * 不放：UI 路由、任务分发、具体业务编排。
 */
object WorkSurfaceRuntimeBridge {

    internal val defaults = WorkSurfaceRuntimeDefaults(
        workspaceDir = WorkspaceBuildSupport.CONTAINER_WORKSPACE_ROOT,
        rootHomeDir = RuntimeBoundary.CONTAINER_ROOT_HOME,
        defaultProjectDir = WorkspaceBuildSupport.DEFAULT_PROJECT_DIR
    )

    val containerState
        get() = KFContainerManager.containerState

    fun getSavedContainer(context: Context): ContainerRecord? {
        return KFContainerManager.getSavedContainer(context.applicationContext)
    }

    fun ensureBaseImageReady(context: Context) {
        KFContainerManager.ensureBaseImageReady(context.applicationContext)
    }

    fun isBaseImageReady(context: Context): Boolean {
        return AssetExtractor.isBaseImageReady(context.applicationContext)
    }

    fun getRuntimeLayout(context: Context): AssetExtractor.RuntimeLayout {
        return KFContainerManager.getRuntimeLayout(context.applicationContext)
    }

    fun getRuntimeRoot(context: Context): File {
        return getRuntimeLayout(context).runtimeRoot
    }

    fun getLogsDir(context: Context): File {
        return getRuntimeLayout(context).logsDir
    }

    fun ensureExchangeDir(context: Context): File {
        return ExternalExchangeManager.ensureExchangeDir(context.applicationContext)
    }

    fun resolveRuntimeSnapshot(
        context: Context,
        container: ContainerRecord? = null
    ): RuntimeBoundarySnapshot {
        return RuntimeBoundary.resolveSnapshot(
            context = context.applicationContext,
            container = container
        )
    }

    fun describeHostPath(
        context: Context,
        path: String?,
        container: ContainerRecord? = null
    ): String {
        return RuntimeBoundary.describeHostPath(
            context = context.applicationContext,
            path = path,
            container = container
        )
    }

    fun describeContainerPath(path: String?): String {
        return RuntimeBoundary.describeContainerPath(path)
    }

    fun buildPathHint(
        context: Context,
        path: String,
        isEmptyDirectory: Boolean,
        container: ContainerRecord? = null
    ): String {
        return when (RuntimeBoundary.classifyHostPath(context.applicationContext, path, container)) {
            RuntimePathRole.WORKSPACE -> {
                if (!isEmptyDirectory) {
                    defaultPathHint()
                } else {
                    """
                    首次安装提示：
                    1. 当前 `${defaults.workspaceDir}` 为空通常是正常的，新设备只安装了 APK，不会自动带上旧设备里的项目和工作区数据。
                    2. 如果你是要继续开发，先把项目放进外部投递区，再从终端复制到 `${defaults.workspaceDir}`；或者直接在终端里 `git clone` 到 `${defaults.workspaceDir}`。
                    3. 外部投递区在手机文件管理器里通常可见，对应宿主路径见下方。
                    4. 如果容器基础环境刚初始化完，等一会再刷新一次也可以。

                    建议下一步：
                    - 点上面的“投递区”查看手机可见目录
                    - 然后去终端页把项目放进 `${defaults.workspaceDir}`
                    """.trimIndent()
                }
            }

            RuntimePathRole.WORKSPACE_BUILD_SUPPORT -> {
                """
                提示：
                1. 这里是工作区的构建辅助区，用来放 `kf-gradle`、Gradle 缓存和 Android 用户目录。
                2. 它属于高频构建热路径的一部分，不建议在文件页里大范围手工清空。
                3. 如果需要跑手机端构建，优先去主终端执行 `kf-gradle doctor|compile|assemble`。
                """.trimIndent()
            }

            RuntimePathRole.EXCHANGE -> {
                """
                提示：
                1. 这里是外部投递区，通常可以直接在手机文件管理器里看到。
                2. 它会挂载到容器内的 `${ExternalExchangeManager.CONTAINER_MOUNT_PATH}`。
                3. 如果要长期开发或执行命令，建议把项目复制到 `${defaults.workspaceDir}` 后继续。
                """.trimIndent()
            }

            RuntimePathRole.CONTAINER_ROOTFS -> {
                """
                提示：
                1. 这里是当前空间的系统层 rootfs，用来承载 Linux 用户态环境。
                2. 它不是项目主工作区，不建议在文件页直接做大范围修改。
                3. 如果只是开发项目、跑脚本、改代码，优先回到 `${defaults.workspaceDir}`。
                """.trimIndent()
            }

            RuntimePathRole.LOGS -> {
                """
                提示：
                1. 这里是 runtime 日志区，用来留终端、后台运行项和进程动作的证据。
                2. 适合查看和导出，不适合作为普通工作目录。
                """.trimIndent()
            }

            RuntimePathRole.BASE_IMAGE,
            RuntimePathRole.RUNTIME_PRIVATE,
            RuntimePathRole.TMP -> {
                """
                提示：
                1. 这里属于底层 runtime 私有区，主要服务于 `PRoot / rootfs / 启动资产 / 临时文件`。
                2. 正常开发、构建和 AI 工作流不应该把这里当日常工作目录。
                3. 如果只是项目开发，优先回到 `${defaults.workspaceDir}`；如果是投递资料，优先使用 `${ExternalExchangeManager.CONTAINER_MOUNT_PATH}`。
                """.trimIndent()
            }

            else -> defaultPathHint()
        }
    }

    fun actionRouteLabel(action: RuntimeActionKind): String {
        return RuntimeBoundary.routeFor(action).label
    }

    fun hostPathAliases(container: ContainerRecord): Set<String> {
        return RuntimeBoundary.hostPathAliases(container)
    }

    fun hostPathAliases(rootfsPath: String, workspacePath: String): Set<String> {
        return RuntimeBoundary.hostPathAliases(rootfsPath, workspacePath)
    }

    fun containerPathAliases(): Set<String> {
        return RuntimeBoundary.containerPathAliases()
    }

    private fun defaultPathHint(): String {
        return """
            提示：
            1. `${defaults.workspaceDir}` 是容器与 Android 侧共享的工作区。
            2. 外部投递区会挂载到容器内的 `${ExternalExchangeManager.CONTAINER_MOUNT_PATH}`，适合先投喂材料。
            3. rootfs 主要用于检查环境，不建议在文件页直接做大范围修改。
            4. 如果需要编辑、安装或跑脚本，优先切到终端页操作。
            """.trimIndent()
    }

    fun ensureDefaultContainer(context: Context): ContainerRecord {
        return KFContainerManager.ensureDefaultContainer(context.applicationContext)
    }

    fun resolveActiveContainer(context: Context): ContainerRecord {
        return getSavedContainer(context) ?: ensureDefaultContainer(context)
    }

    fun resetDefaultContainer(context: Context, wipeWorkspace: Boolean = false): ContainerRecord {
        return KFContainerManager.resetDefaultContainer(
            context = context.applicationContext,
            wipeWorkspace = wipeWorkspace
        )
    }

    fun markContainerStarting(context: Context) {
        KFContainerManager.markStarting(context.applicationContext)
    }

    fun markContainerRunning(context: Context, pid: Int) {
        KFContainerManager.markRunning(context.applicationContext, pid)
    }

    fun markContainerStopped(context: Context) {
        KFContainerManager.markStopped(context.applicationContext)
    }

    fun markContainerError(context: Context, message: String) {
        KFContainerManager.markError(context.applicationContext, message)
    }

    fun isCommandAvailable(context: Context, rawCommand: String): Boolean {
        return KFContainerManager.isCommandAvailable(context.applicationContext, rawCommand)
    }

    fun buildTerminalLaunchConfig(context: Context): ContainerLaunchConfig {
        return KFContainerManager.buildLaunchConfig(context.applicationContext)
    }

    fun buildShellExecConfig(
        context: Context,
        workingDirectory: String = defaults.workspaceDir,
        payload: String,
        loginShell: Boolean = true
    ): ContainerExecConfig {
        return KFContainerManager.buildContainerExecConfig(
            context = context.applicationContext,
            workingDirectory = workingDirectory,
            payload = payload,
            loginShell = loginShell
        )
    }

    fun buildArgvExecConfig(
        context: Context,
        workingDirectory: String = defaults.workspaceDir,
        argv: List<String>
    ): ContainerExecConfig {
        return KFContainerManager.buildContainerArgvExecConfig(
            context = context.applicationContext,
            workingDirectory = workingDirectory,
            argv = argv
        )
    }
}
