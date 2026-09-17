package com.kite.app.foundation.workspace

import android.content.Context
import com.kite.app.foundation.runtime.AssetExtractor
import com.kite.app.foundation.contracts.ContainerRecord
import com.kite.app.foundation.contracts.ContainerExecConfig
import com.kite.app.foundation.contracts.ContainerLaunchConfig
import com.kite.app.foundation.runtime.KFContainerManager
import com.kite.app.foundation.runtime.ManagedCommandVerificationBasis
import com.kite.app.foundation.runtime.JavaWarmProotRunnerProcess
import com.kite.app.foundation.runtime.WarmProotRunnerProcess
import com.kite.app.foundation.runtime.ProotEnvironmentWorkspace
import com.kite.app.foundation.runtime.ProotCompatibilityPlan
import com.kite.app.foundation.runtime.ProotCompatibilityRuntimeProvider
import com.kite.app.foundation.runtime.ProotBindMount
import com.kite.app.foundation.runtime.RuntimeExecutionRequest
import com.kite.app.foundation.runtime.RuntimeExecutionPayload
import com.kite.app.foundation.runtime.applyToProotCommand
import com.kite.app.foundation.runtime.ProotViewStore
import com.kite.app.foundation.contracts.RuntimeActionKind
import com.kite.app.foundation.runtime.RuntimeBoundary
import com.kite.app.foundation.contracts.RuntimeBoundarySnapshot
import com.kite.app.foundation.contracts.RuntimePathRole
import java.io.File

internal data class WorkSurfaceRuntimeDefaults(
    val workspaceDir: String,
    val rootHomeDir: String,
    val defaultProjectDir: String
)

internal data class ActiveWorkspaceEnvironment(
    val environmentId: String,
    val containerId: String,
    val workspacePath: String,
)

/**
 * 工作面层访问建房层的唯一正确 facade。
 *
 * 只放：工作面进入底层所需的稳定入口与默认值。
 * 不放：UI 路由、任务分发、具体业务编排。
 */
object WorkSurfaceRuntimeBridge : com.kite.app.foundation.contracts.WorkSurfaceContract {

    internal val defaults = WorkSurfaceRuntimeDefaults(
        workspaceDir = WorkspaceBuildSupport.CONTAINER_WORKSPACE_ROOT,
        rootHomeDir = RuntimeBoundary.CONTAINER_ROOT_HOME,
        defaultProjectDir = WorkspaceBuildSupport.DEFAULT_PROJECT_DIR
    )

    val containerState
        get() = KFContainerManager.containerState

    override fun getSavedContainer(context: Context): ContainerRecord? {
        return KFContainerManager.getSavedContainer(context.applicationContext)
    }

    fun ensureBaseImageReady(context: Context) {
        KFContainerManager.ensureBaseImageReady(context.applicationContext)
    }

    fun isBaseImageReady(context: Context): Boolean {
        return AssetExtractor.isBaseImageReady(context.applicationContext)
    }

    fun isDefaultContainerReady(context: Context): Boolean {
        return KFContainerManager.isDefaultContainerReady(context.applicationContext)
    }

    fun ensureRuntimeOperational(context: Context) {
        KFContainerManager.ensureRuntimeOperational(context.applicationContext)
    }

    fun getRuntimeLayout(context: Context): AssetExtractor.RuntimeLayout {
        return KFContainerManager.getRuntimeLayout(context.applicationContext)
    }

    fun getRuntimeRoot(context: Context): File {
        return getRuntimeLayout(context).runtimeRoot
    }

    override fun getLogsDir(context: Context): File {
        return getRuntimeLayout(context).logsDir
    }

    override fun resolveRuntimeSnapshot(
        context: Context,
        container: ContainerRecord?
    ): RuntimeBoundarySnapshot {
        return RuntimeBoundary.resolveSnapshot(
            context = context.applicationContext,
            container = container
        )
    }

    override fun describeHostPath(
        context: Context,
        path: String?,
        container: ContainerRecord?
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
                    2. 如果你是要继续开发，可在系统文件选择器的“Kite Ubuntu”中直接放入项目，或者在终端里 `git clone` 到 `${defaults.workspaceDir}`。
                    3. 如果容器基础环境刚初始化完，等一会再刷新一次也可以。

                    建议下一步：
                    - 从 Agent 的“项目 +”选择或新建 Ubuntu 项目
                    - 或从系统文件选择器打开“Kite Ubuntu”管理同一份文件
                    """.trimIndent()
                }
            }

            RuntimePathRole.WORKSPACE_BUILD_SUPPORT -> {
                """
                提示：
                1. 这里是工作区的构建辅助区，用来放 `kf-gradle`、Gradle 缓存和 Android 用户目录。
                2. 它属于高频构建热路径的一部分，不建议在文件页里大范围手工清空。
                3. 如果需要跑手机端构建，优先去终端页执行 `kf-gradle doctor|compile|assemble`。
                """.trimIndent()
            }

            RuntimePathRole.ANDROID_SHARED_STORAGE -> {
                """
                提示：
                1. 这里是安卓共享存储，Agent 操作的是手机上的真实文件，不是同步副本。
                2. 适合整理下载、图片、文档和交付结果，不作为 Agent 项目工作区。
                3. Git、依赖安装和正式构建仍然放在 `${defaults.workspaceDir}`。
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
                3. 如果只是项目开发，优先回到 `${defaults.workspaceDir}`；手机资料直接使用其 `/storage/...` 真实路径。
                """.trimIndent()
            }

            else -> defaultPathHint()
        }
    }

    override fun actionRouteLabel(action: RuntimeActionKind): String {
        return RuntimeBoundary.routeFor(action).label
    }

    override fun hostPathAliases(container: ContainerRecord): Set<String> {
        return RuntimeBoundary.hostPathAliases(container)
    }

    override fun hostPathAliases(rootfsPath: String, workspacePath: String): Set<String> {
        return RuntimeBoundary.hostPathAliases(rootfsPath, workspacePath)
    }

    override fun containerPathAliases(): Set<String> {
        return RuntimeBoundary.containerPathAliases()
    }

    private fun defaultPathHint(): String {
        return """
            提示：
            1. `${defaults.workspaceDir}` 是容器与 Android 侧共享的工作区。
            2. 安卓共享存储在 Agent 中保持 `/storage/...` 真实路径，不经过复制或同步。
            3. rootfs 主要用于检查环境，不建议在文件页直接做大范围修改。
            4. 如果需要编辑、安装或跑脚本，优先切到终端页操作。
            """.trimIndent()
    }

    fun ensureDefaultContainer(context: Context): ContainerRecord {
        return KFContainerManager.ensureDefaultContainer(context.applicationContext)
    }

    /** 正式工作面固定使用普通 PRoot 的 default 共享工作区；显式 View 不改变全局工作面。 */
    internal fun resolveActiveWorkspaceEnvironment(context: Context): ActiveWorkspaceEnvironment {
        val appContext = context.applicationContext
        val container = ensureDefaultContainer(appContext)
        return resolveActiveWorkspaceEnvironment(container)
    }

    /** Runtime Prep 已经确认默认容器时，直接由该容器解析工作区，避免同一启动链再次准备 Base/容器。 */
    internal fun resolveActiveWorkspaceEnvironment(container: ContainerRecord): ActiveWorkspaceEnvironment {
        val workspace = ProotEnvironmentWorkspace.plan(container, binding = null).also { it.ensureReady() }
        return ActiveWorkspaceEnvironment(
            environmentId = ProotViewStore.DEFAULT_ENVIRONMENT_ID,
            containerId = container.id,
            workspacePath = workspace.workspaceDirectory.absolutePath,
        )
    }

    override fun resolveActiveContainer(context: Context): ContainerRecord {
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

    override fun markContainerStopped(context: Context) {
        KFContainerManager.markStopped(context.applicationContext)
    }

    fun markContainerError(context: Context, message: String) {
        KFContainerManager.markError(context.applicationContext, message)
    }

    fun isCommandAvailable(context: Context, rawCommand: String): Boolean {
        return KFContainerManager.isCommandAvailable(context.applicationContext, rawCommand)
    }

    /** 只读当前普通 PRoot 的 runtime/命令文件身份；不执行命令，不触发运行时准备。 */
    internal fun managedCommandVerificationBasis(
        context: Context,
        commands: Collection<String>,
    ): ManagedCommandVerificationBasis? = KFContainerManager.managedCommandVerificationBasis(
        context = context.applicationContext,
        rawCommands = commands,
    )

    fun buildTerminalLaunchConfig(
        context: Context,
        requestedProotViewId: String? = null,
        requestedProotEnvironmentId: String? = null
    ): ContainerLaunchConfig {
        return KFContainerManager.buildLaunchConfig(
            context = context.applicationContext,
            requestedProotViewId = requestedProotViewId,
            requestedProotEnvironmentId = requestedProotEnvironmentId
        )
    }

    fun buildShellExecConfig(
        context: Context,
        workingDirectory: String = defaults.workspaceDir,
        payload: String,
        loginShell: Boolean = true,
        requestedProotViewId: String? = null,
        requestedProotEnvironmentId: String? = null,
        extraBindMounts: List<ProotBindMount> = emptyList(),
    ): ContainerExecConfig {
        return KFContainerManager.buildContainerExecConfig(
            context = context.applicationContext,
            workingDirectory = workingDirectory,
            payload = payload,
            loginShell = loginShell,
            requestedProotViewId = requestedProotViewId,
            requestedProotEnvironmentId = requestedProotEnvironmentId,
            extraBindMounts = extraBindMounts,
        )
    }

    fun buildArgvExecConfig(
        context: Context,
        workingDirectory: String = defaults.workspaceDir,
        argv: List<String>,
        requestedProotViewId: String? = null,
        requestedProotEnvironmentId: String? = null,
        extraBindMounts: List<ProotBindMount> = emptyList(),
    ): ContainerExecConfig {
        return KFContainerManager.buildContainerArgvExecConfig(
            context = context.applicationContext,
            workingDirectory = workingDirectory,
            argv = argv,
            requestedProotViewId = requestedProotViewId,
            requestedProotEnvironmentId = requestedProotEnvironmentId,
            extraBindMounts = extraBindMounts,
        )
    }

    /** 把标准 PRoot 逻辑计划交给既有物理构造器；不在 Provider 中复制 rootfs/bind/network 规则。 */
    internal fun buildProotExecConfig(
        context: Context,
        plan: ProotCompatibilityPlan,
    ): ContainerExecConfig {
        require(!plan.interactivePty) { "proot_exec_plan_cannot_be_interactive" }
        val config = when (val payload = plan.payload) {
            is RuntimeExecutionPayload.CommandLine -> buildShellExecConfig(
                context = context,
                workingDirectory = plan.workingDirectory,
                payload = payload.command,
                loginShell = plan.loginShell,
                requestedProotViewId = plan.requestedProotViewId,
                requestedProotEnvironmentId = plan.requestedProotEnvironmentId,
                extraBindMounts = plan.filesystemBindings.map { binding ->
                    ProotBindMount(
                        sourcePath = binding.sourcePath,
                        targetPath = binding.targetPath,
                        role = binding.role,
                    )
                },
            )
            is RuntimeExecutionPayload.Argv -> buildArgvExecConfig(
                context = context,
                workingDirectory = plan.workingDirectory,
                argv = listOf(payload.executable) + payload.arguments,
                requestedProotViewId = plan.requestedProotViewId,
                requestedProotEnvironmentId = plan.requestedProotEnvironmentId,
                extraBindMounts = plan.filesystemBindings.map { binding ->
                    ProotBindMount(
                        sourcePath = binding.sourcePath,
                        targetPath = binding.targetPath,
                        role = binding.role,
                    )
                },
            )
            is RuntimeExecutionPayload.NativeCapability -> error("proot_native_capability_plan_forbidden")
        }
        val command = plan.hardLinkMode.applyToProotCommand(config.command)
        return config.copy(command = command, env = config.env + plan.environment)
    }

    /** 显式兼容入口的统一短路径：先经最终 Provider，再交给同一个物理构造器。 */
    internal fun buildRequiredProotExecConfig(
        context: Context,
        request: RuntimeExecutionRequest,
        selectionReason: String,
        loginShell: Boolean = true,
    ): ContainerExecConfig = buildProotExecConfig(
        context = context,
        plan = ProotCompatibilityRuntimeProvider.requirePlan(
            request = request,
            selectionReason = selectionReason,
            loginShell = loginShell,
        ),
    )

    /** 交互 PRoot 仍使用原终端登录壳配置，只把标准计划中的 View 与环境事实传进去。 */
    internal fun buildProotTerminalLaunchConfig(
        context: Context,
        plan: ProotCompatibilityPlan,
    ): ContainerLaunchConfig {
        require(plan.interactivePty) { "proot_terminal_plan_requires_interactive_pty" }
        val config = buildTerminalLaunchConfig(
            context = context,
            requestedProotViewId = plan.requestedProotViewId,
            requestedProotEnvironmentId = plan.requestedProotEnvironmentId,
        )
        val environment = linkedMapOf<String, String>()
        config.env.forEach { entry ->
            environment[entry.substringBefore('=')] = entry.substringAfter('=', "")
        }
        environment.putAll(plan.environment)
        return config.copy(env = environment.map { (key, value) -> "$key=$value" }.toTypedArray())
    }

    /** 启动一个由 Android 持有 stdio 的温热 runner PRoot；不登记后台服务，也不自动恢复。 */
    internal fun startWarmProotRunnerProcess(context: Context): WarmProotRunnerProcess {
        val config = buildArgvExecConfig(
            context = context.applicationContext,
            workingDirectory = defaults.workspaceDir,
            argv = listOf(WorkspaceBuildSupport.CONTAINER_KITE_RUNNER_PATH, "--server"),
        )
        val process = ProcessBuilder(config.command)
            .redirectErrorStream(false)
            .apply { environment().putAll(config.env) }
            .start()
        return JavaWarmProotRunnerProcess(process)
    }
}
