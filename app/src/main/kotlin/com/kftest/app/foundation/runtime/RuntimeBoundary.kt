package com.kftest.app.foundation.runtime

import android.content.Context
import java.io.File

enum class RuntimePathRole(val label: String) {
    RUNTIME_PRIVATE("runtime 私有区"),
    BASE_IMAGE("只读骨架"),
    CONTAINER_ROOTFS("容器系统层"),
    WORKSPACE("工作区热路径"),
    WORKSPACE_BUILD_SUPPORT("工作区构建辅助区"),
    EXCHANGE("交换区"),
    LOGS("日志区"),
    TMP("临时区"),
    UNKNOWN("未知区域")
}

enum class RuntimeActionKind(val label: String) {
    CONTAINER_BOOTSTRAP("容器启动与自举"),
    TERMINAL_COMMAND("终端真实命令"),
    BACKGROUND_RUNTIME("后台运行项"),
    PROCESS_SAMPLING("真实进程采样"),
    WORKSPACE_BROWSE("工作区浏览"),
    LOG_VIEW("日志查看"),
    EXCHANGE_TRANSFER("交换区传输"),
    MOBILE_BUILD("手机端构建"),
    TOOL_ENTRY("工具入口")
}

enum class RuntimeActionRoute(val label: String) {
    PROOT("默认 PRoot"),
    NATIVE("默认原生"),
    BRIDGE("需要桥接"),
    DUAL("可双路")
}

data class RuntimeBoundarySnapshot(
    val runtimeRoot: File,
    val baseImageDir: File,
    val containerRootfsDir: File?,
    val workspaceDir: File?,
    val workspaceBuildSupportDir: File?,
    val exchangeDir: File,
    val logsDir: File,
    val tmpDir: File
)

/**
 * 建房层的路径与边界词典。
 *
 * 只放：底层路径角色、动作路由、snapshot/classify、路径别名。
 * 不放：UI 提示、工作面默认值、入口层流程。
 */
object RuntimeBoundary {

    const val CONTAINER_ROOT_HOME = "/root"
    const val CONTAINER_WORKSPACE_PATH = "/workspace"
    const val CONTAINER_EXCHANGE_PATH = ExternalExchangeManager.CONTAINER_MOUNT_PATH
    const val CONTAINER_DELIVERY_PATH = ExternalExchangeManager.CONTAINER_DELIVERY_PATH
    const val CONTAINER_ALBUM_PATH = ExternalExchangeManager.CONTAINER_ALBUM_PATH
    const val WORKSPACE_HELPER_ROOT_DIR = ".kf"
    const val WORKSPACE_HELPER_BIN_DIR = ".kf/bin"
    const val WORKSPACE_GRADLE_HELPER_NAME = "kf-gradle"
    const val WORKSPACE_GRADLE_USER_HOME_DIR = ".gradle-user"
    const val WORKSPACE_ANDROID_USER_HOME_DIR = ".android-user"
    const val WORKSPACE_ANDROID_DATA_DIR = ".android-data"
    const val CONTAINER_HELPER_BIN_PATH = "/workspace/.kf/bin"
    const val CONTAINER_GRADLE_HELPER_PATH = "/workspace/.kf/bin/kf-gradle"
    const val CONTAINER_GRADLE_USER_HOME = "/workspace/.gradle-user"
    const val CONTAINER_ANDROID_USER_HOME = "/workspace/.android-user"
    const val CONTAINER_ANDROID_DATA = "/workspace/.android-data"
    const val CONTAINER_DEFAULT_PROJECT_PATH = "/workspace/KFShell"

    fun routeFor(action: RuntimeActionKind): RuntimeActionRoute {
        return when (action) {
            RuntimeActionKind.CONTAINER_BOOTSTRAP,
            RuntimeActionKind.TERMINAL_COMMAND,
            RuntimeActionKind.BACKGROUND_RUNTIME,
            RuntimeActionKind.PROCESS_SAMPLING,
            RuntimeActionKind.MOBILE_BUILD -> RuntimeActionRoute.PROOT

            RuntimeActionKind.WORKSPACE_BROWSE,
            RuntimeActionKind.LOG_VIEW -> RuntimeActionRoute.NATIVE

            RuntimeActionKind.EXCHANGE_TRANSFER -> RuntimeActionRoute.BRIDGE
            RuntimeActionKind.TOOL_ENTRY -> RuntimeActionRoute.DUAL
        }
    }

    fun resolveSnapshot(
        context: Context,
        container: ContainerRecord? = null
    ): RuntimeBoundarySnapshot {
        val appContext = context.applicationContext
        val layout = AssetExtractor.getRuntimeLayout(appContext)
        val exchangeDir = ExternalExchangeManager.ensureExchangeDir(appContext)
        val workspaceDir = container?.workspacePath?.let(::File)
        val rootfsDir = container?.rootfsPath?.let(::File)
        val buildSupportDir = workspaceDir?.let { File(it, WORKSPACE_HELPER_ROOT_DIR) }
        return RuntimeBoundarySnapshot(
            runtimeRoot = layout.runtimeRoot,
            baseImageDir = layout.baseImageDir,
            containerRootfsDir = rootfsDir,
            workspaceDir = workspaceDir,
            workspaceBuildSupportDir = buildSupportDir,
            exchangeDir = exchangeDir,
            logsDir = layout.logsDir,
            tmpDir = layout.tmpDir
        )
    }

    fun classifyHostPath(
        context: Context,
        path: String,
        container: ContainerRecord? = null
    ): RuntimePathRole {
        if (path.isBlank()) return RuntimePathRole.UNKNOWN

        val snapshot = resolveSnapshot(context, container)
        val target = File(path).absoluteFile

        return when {
            isSameOrDescendant(target, snapshot.workspaceBuildSupportDir) ->
                RuntimePathRole.WORKSPACE_BUILD_SUPPORT

            isSameOrDescendant(target, snapshot.workspaceDir) ->
                RuntimePathRole.WORKSPACE

            isSameOrDescendant(target, snapshot.exchangeDir) ->
                RuntimePathRole.EXCHANGE

            isSameOrDescendant(target, snapshot.logsDir) ->
                RuntimePathRole.LOGS

            isSameOrDescendant(target, snapshot.containerRootfsDir) ->
                RuntimePathRole.CONTAINER_ROOTFS

            isSameOrDescendant(target, snapshot.baseImageDir) ->
                RuntimePathRole.BASE_IMAGE

            isSameOrDescendant(target, snapshot.tmpDir) ->
                RuntimePathRole.TMP

            isSameOrDescendant(target, snapshot.runtimeRoot) ->
                RuntimePathRole.RUNTIME_PRIVATE

            else -> RuntimePathRole.UNKNOWN
        }
    }

    fun classifyContainerPath(path: String): RuntimePathRole {
        if (path.isBlank()) return RuntimePathRole.UNKNOWN

        val normalizedPath = normalizeUnixPath(path)
        return when {
            isSameOrDescendant(normalizedPath, "$CONTAINER_WORKSPACE_PATH/$WORKSPACE_HELPER_ROOT_DIR") ||
                isSameOrDescendant(normalizedPath, CONTAINER_HELPER_BIN_PATH) ||
                isSameOrDescendant(normalizedPath, CONTAINER_GRADLE_USER_HOME) ||
                isSameOrDescendant(normalizedPath, CONTAINER_ANDROID_USER_HOME) ||
                isSameOrDescendant(normalizedPath, CONTAINER_ANDROID_DATA) -> {
                RuntimePathRole.WORKSPACE_BUILD_SUPPORT
            }

            isSameOrDescendant(normalizedPath, CONTAINER_WORKSPACE_PATH) -> RuntimePathRole.WORKSPACE
            isSameOrDescendant(normalizedPath, CONTAINER_EXCHANGE_PATH) ||
                isSameOrDescendant(normalizedPath, CONTAINER_DELIVERY_PATH) ||
                isSameOrDescendant(normalizedPath, CONTAINER_ALBUM_PATH) -> RuntimePathRole.EXCHANGE
            isSameOrDescendant(normalizedPath, "/tmp") -> RuntimePathRole.TMP
            normalizedPath.startsWith("/") -> RuntimePathRole.CONTAINER_ROOTFS
            else -> RuntimePathRole.UNKNOWN
        }
    }

    fun describeHostPath(
        context: Context,
        path: String?,
        container: ContainerRecord? = null
    ): String {
        if (path.isNullOrBlank()) {
            return "--"
        }
        return "${classifyHostPath(context, path, container).label}: $path"
    }

    fun describeContainerPath(path: String?): String {
        if (path.isNullOrBlank()) {
            return "--"
        }
        return "${classifyContainerPath(path).label}: $path"
    }

    fun hostPathAliases(container: ContainerRecord): Set<String> {
        return hostPathAliases(container.rootfsPath, container.workspacePath)
    }

    fun hostPathAliases(rootfsPath: String, workspacePath: String): Set<String> {
        return buildSet {
            add(rootfsPath.lowercase())
            add(rootfsPath.lowercase().replace("/data/data/", "/data/user/0/"))
            add(workspacePath.lowercase())
            add(workspacePath.lowercase().replace("/data/data/", "/data/user/0/"))
        }
    }

    fun containerPathAliases(): Set<String> {
        return setOf(
            CONTAINER_WORKSPACE_PATH,
            CONTAINER_DELIVERY_PATH,
            CONTAINER_ALBUM_PATH,
            CONTAINER_EXCHANGE_PATH,
            CONTAINER_DEFAULT_PROJECT_PATH
        )
    }

    private fun isSameOrDescendant(target: File, root: File?): Boolean {
        if (root == null) {
            return false
        }
        val normalizedTarget = target.absoluteFile.toPath().normalize()
        val normalizedRoot = root.absoluteFile.toPath().normalize()
        return normalizedTarget == normalizedRoot || normalizedTarget.startsWith(normalizedRoot)
    }

    private fun isSameOrDescendant(target: String, root: String): Boolean {
        val normalizedTarget = normalizeUnixPath(target)
        val normalizedRoot = normalizeUnixPath(root)
        return normalizedTarget == normalizedRoot || normalizedTarget.startsWith("$normalizedRoot/")
    }

    private fun normalizeUnixPath(path: String): String {
        val trimmed = path.trim()
        if (trimmed.isEmpty()) {
            return ""
        }
        if (trimmed == "/") {
            return trimmed
        }
        return trimmed.replace(Regex("/+"), "/").removeSuffix("/")
    }
}
