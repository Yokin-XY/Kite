package com.kite.app.foundation.runtime

import android.content.Context
import com.kite.app.foundation.contracts.ContainerLaunchConfig
import com.kite.app.foundation.contracts.ContainerRecord
import com.kite.app.foundation.contracts.NetworkMode
import java.io.File
import java.util.TimeZone

internal data class HostNodeProviderContext(
    val androidContext: Context,
    val container: ContainerRecord,
    val workspaceDirectory: File,
)

/** 入口无关的 Node Runtime Provider；只生成计划，不创建进程、不持有生命周期状态。 */
internal object HostNodeRuntimeProvider :
    RuntimeExecutionProvider<HostNodeProviderContext, ContainerLaunchConfig> {
    override val kind: RuntimeProviderKind = RuntimeProviderKind.MANAGED_RUNTIME

    override fun prepare(
        context: HostNodeProviderContext,
        request: RuntimeExecutionRequest,
    ): RuntimeProviderDecision<ContainerLaunchConfig> {
        if (RuntimeExecutionRequirement.ANDROID_NATIVE in request.requirements) {
            return unsupported("android_native_required")
        }
        // 决策方向：宿主通道是默认车道，PRoot 是兼容兑底。
        // ANDROID_NATIVE：请求显式要求 Android 原生能力，不属于本通道。
        // FULL_LINUX：调用方显式声明需要完整 Linux 根（特权/服务型依赖），放行进容器。
        // FILESYSTEM_VIEW 不再拒绝：预载层（kite-node-host-runtime.cjs）已提供
        // /workspace、/root 与 rootfs 前缀的双向路径翻译，容器文件系统视图
        // 在宿主车道可用；TMPDIR 等根级路径由通道环境注入。
        if (RuntimeExecutionRequirement.FULL_LINUX in request.requirements) {
            return unsupported("full_linux_required")
        }
        val container = context.container
        if (container.networkMode != NetworkMode.HOST) {
            return unsupported("network_mode_requires_proot")
        }
        val workspaceControlDirectory = File(container.workspacePath, ".kf")
        val assets = when (val prepared = HostNodeRuntimePreparer.prepare(
            context.androidContext,
            container,
            context.workspaceDirectory,
            workspaceControlDirectory,
        )) {
            is HostNodeRuntimePreparation.Ready -> prepared.assets
            is HostNodeRuntimePreparation.Fallback -> return unsupported(prepared.reason)
        }
        val layout = when (val resolved = HostNodeRuntimeResolver.resolve(
            rootfsDirectory = File(container.rootfsPath),
            workspaceDirectory = context.workspaceDirectory,
            workspaceControlDirectory = workspaceControlDirectory,
            assets = assets,
        )) {
            is HostNodeRuntimeResolution.Ready -> resolved.layout
            is HostNodeRuntimeResolution.Fallback -> return unsupported(resolved.reason)
        }
        val invocation = when (val resolved = when (val payload = request.payload) {
            is RuntimeExecutionPayload.CommandLine -> HostNodeCommandResolver.resolve(payload.command, layout)
            is RuntimeExecutionPayload.Argv -> HostNodeCommandResolver.resolve(
                executable = payload.executable,
                arguments = payload.arguments,
                layout = layout,
            )
            is RuntimeExecutionPayload.NativeCapability -> {
                return unsupported("native_capability_required")
            }
        }) {
            is HostNodeCommandResolution.Ready -> resolved.invocation
            is HostNodeCommandResolution.Fallback -> return unsupported(resolved.reason)
        }
        val workingDirectory = layout.mapContainerPath(request.workingDirectory)
            ?.takeIf(File::isDirectory)
            ?: return unsupported("working_directory_invalid")
        // 声明 openat2_degrade 的调用（应用域雷区网关型依赖，典型是 openclaw）：
        // 雷已由地基拔除（glibc 雷补丁随 preparer 发布、直发调用方由资源补丁处理），
        // 车道只注入 /tmp 重写与兼容层定位环境，不再套运行时监护进程（tracer 仅诊断用）。
        val minefieldLane = request.guarantees.contains(RuntimeExecutionGuarantee.OPENAT2_DEGRADE)
        return RuntimeProviderDecision.Ready(
            provider = kind,
            plan = buildConfig(
                container,
                layout,
                invocation,
                workingDirectory,
                AndroidRuntimeHttpProxy.environment() + request.environment,
                minefieldLane = minefieldLane,
            ),
            reason = "host_node_ready",
        )
    }

    private fun unsupported(reason: String) = RuntimeProviderDecision.Unsupported(
        provider = kind,
        reason = reason,
    )

    internal fun buildConfig(
        container: ContainerRecord,
        layout: HostNodeRuntimeLayout,
        invocation: HostNodeInvocation,
        workingDirectory: File,
        additionalEnvironment: Map<String, String> = emptyMap(),
        minefieldLane: Boolean = false,
    ): ContainerLaunchConfig {
        val runtimeRoot = layout.assets.launcher.parentFile
        val tmpDirectory = File(runtimeRoot, "tmp").also(File::mkdirs)
        val home = File(layout.rootfsDirectory, "root")
        val certificateFile = File(layout.rootfsDirectory, "etc/ssl/certs/ca-certificates.crt")
        val environment = linkedMapOf(
            "HOME" to home.absolutePath,
            "USER" to "root",
            "LOGNAME" to "root",
            "TERM" to "xterm-256color",
            "COLORTERM" to "truecolor",
            "FORCE_COLOR" to "3",
            "CLICOLOR_FORCE" to "1",
            "LANG" to "C.UTF-8",
            "TZ" to TimeZone.getDefault().id,
            "TMPDIR" to tmpDirectory.absolutePath,
            "TMP" to tmpDirectory.absolutePath,
            "TEMP" to tmpDirectory.absolutePath,
            "PWD" to workingDirectory.absolutePath,
            "PATH" to containerPath(),
            // Android 应用 seccomp 不接受 Ubuntu glibc 启动时注册 rseq；关闭该可选优化后再交给 Node。
            "GLIBC_TUNABLES" to "glibc.pthread.rseq=0",
        )
        additionalEnvironment.forEach { (key, value) ->
            if (ENVIRONMENT_NAME.matches(key)) {
                environment[key] = value
            }
        }
        environment.putAll(linkedMapOf(
            // --no-warnings 是车道级通用策略：抑制 node 实验性/弃用警告刷屏，
            // 同时让 npm 包装脚本的 "重 exec 补警告 flag" 模式失去诱因（respawn 在
            // 宿主车道会重建链失败，见模拟态纲领；预置后 wrapper 判定无需 respawn）。
            "NODE_OPTIONS" to "--no-warnings --require=${layout.assets.preloadScript.absolutePath}",
            "KITE_NODE_HOST_LANE" to "direct_glibc_v1",
            "KITE_NODE_HOST_LAUNCHER" to layout.assets.launcher.absolutePath,
            "KITE_NODE_HOST_LOADER" to layout.loader.absolutePath,
            "KITE_NODE_HOST_LIBRARY_PATH" to layout.libraryPath,
            "KITE_NODE_HOST_COMPAT_LIBRARY" to layout.assets.compatLibrary.absolutePath,
            "KITE_NODE_HOST_BINARY" to layout.nodeBinary.absolutePath,
            "KITE_NODE_HOST_RESOLV_CONF" to layout.assets.resolvConf.absolutePath,
            "KITE_NODE_HOST_WORKSPACE" to layout.workspaceDirectory.absolutePath,
            "KITE_NODE_HOST_CONTROL" to layout.workspaceControlDirectory.absolutePath,
            "KITE_NODE_HOST_ROOTFS" to layout.rootfsDirectory.absolutePath,
        ))
        if (certificateFile.isFile) {
            environment["SSL_CERT_FILE"] = certificateFile.absolutePath
        }
        // GUEST_TMP 是宿主车道通用能力（AGENTS.md 运行车道策略），不再是 openclaw
        // 特权：Node fs 钩子层对一切 node 程序生效；C 兼容层三件套供动态 glibc
        // ELF 的 loader 路由使用。双层指向同一宿主 tmp 目录，任何一层缺失都会
        // 让硬编码 /tmp 的程序退回不存在的 /tmp 导致 ENOENT。
        environment["KITE_GLIBC_HOST_LOADER"] = layout.loader.absolutePath
        environment["KITE_GLIBC_HOST_LIBRARY_PATH"] = layout.libraryPath
        environment["KITE_GLIBC_HOST_GUEST_TMP"] = tmpDirectory.absolutePath
        environment["KITE_NODE_HOST_GUEST_TMP"] = tmpDirectory.absolutePath
        if (minefieldLane) {
            // 预留：雷区专用注入（当前为空；等价性测试覆盖后移除该条件）。
        }
        return ContainerLaunchConfig(
            container = container,
            executablePath = layout.assets.launcher.absolutePath,
            workingDirectory = workingDirectory.absolutePath,
            args = (listOf(layout.assets.launcher.absolutePath) + invocation.nodeArguments()).toTypedArray(),
            env = environment.map { (key, value) -> "$key=$value" }.toTypedArray(),
        )
    }

    private fun containerPath(): String = listOf(
        "/workspace/.kf/bin",
        "/workspace/.kf/system/bin",
        "/root/.local/bin",
        "/usr/local/sbin",
        "/usr/local/bin",
        "/usr/sbin",
        "/usr/bin",
        "/sbin",
        "/bin",
    ).joinToString(":")

    private val ENVIRONMENT_NAME = Regex("[A-Za-z_][A-Za-z0-9_]*")
}

/** 旧终端调用面的窄适配器；实际选择和配置均由统一 Provider 持有。 */
internal object HostNodeTerminalLaunchFactory {
    internal fun buildConfig(
        container: ContainerRecord,
        layout: HostNodeRuntimeLayout,
        invocation: HostNodeInvocation,
        workingDirectory: File,
    ): ContainerLaunchConfig = HostNodeRuntimeProvider.buildConfig(
        container = container,
        layout = layout,
        invocation = invocation,
        workingDirectory = workingDirectory,
    )
}
