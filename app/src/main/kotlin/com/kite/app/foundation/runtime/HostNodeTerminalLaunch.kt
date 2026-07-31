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
        if (RuntimeExecutionRequirement.FULL_LINUX in request.requirements) {
            return unsupported("full_linux_required")
        }
        if (RuntimeExecutionRequirement.FILESYSTEM_VIEW in request.requirements) {
            return unsupported("filesystem_view_required")
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
        return RuntimeProviderDecision.Ready(
            provider = kind,
            plan = buildConfig(container, layout, invocation, workingDirectory, request.environment),
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
            "NODE_OPTIONS" to "--require=${layout.assets.preloadScript.absolutePath}",
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
