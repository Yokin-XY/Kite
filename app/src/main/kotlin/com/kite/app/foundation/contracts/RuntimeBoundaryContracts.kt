package com.kite.app.foundation.contracts

import java.io.File

/**
 * Runtime 边界的纯数据契约:路径角色、动作类型、动作路由、边界快照。
 *
 * 从 foundation.runtime.RuntimeBoundary 抽出,供 runtime 与 workspace 共享,
 * 消除两个子包之间的双向依赖。带行为的 RuntimeBoundary object 仍留在 runtime。
 */
enum class RuntimePathRole(val label: String) {
    RUNTIME_PRIVATE("runtime 私有区"),
    BASE_IMAGE("只读骨架"),
    CONTAINER_ROOTFS("容器系统层"),
    WORKSPACE("工作区热路径"),
    WORKSPACE_BUILD_SUPPORT("工作区构建辅助区"),
    ANDROID_SHARED_STORAGE("安卓共享存储"),
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
    val androidSharedStorageDirs: List<File>,
    val logsDir: File,
    val tmpDir: File
)
