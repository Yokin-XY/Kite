package com.kite.app.foundation.runtime

import com.kite.app.foundation.contracts.ContainerStatus
import com.kite.app.foundation.contracts.NetworkMode
import com.kite.app.foundation.contracts.ContainerRecord
import com.kite.app.foundation.contracts.ContainerLaunchConfig
import com.kite.app.foundation.contracts.ContainerExecConfig
import com.kite.app.foundation.contracts.BaseImageProfile

import android.content.Context
import android.net.Network
import android.os.SystemClock
import android.system.Os
import com.kite.app.foundation.logging.Logger
import com.kite.app.foundation.workspace.WorkspaceBuildSupport
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Paths
import java.nio.file.attribute.BasicFileAttributes
import java.util.LinkedHashMap
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.thread
import kotlin.concurrent.write

/**
 * 建房层内核。
 *
 * 只放：rootfs/runtime 准备、容器解析、bind/mount、PRoot 启动与 exec 配置。
 * 不放：终端业务、AI 会话、后台运行项编排、UI/入口层动作。
 *
 * 网络装配统一由 [ContainerNetworkPlan] 驱动：
 * - 运行时 resolv.conf 生成于 app 私有目录，不再污染 rootfs
 * - proot 启动时通过 bind mount 将运行时 resolv.conf 挂入容器 /etc/resolv.conf
 * - 所有容器启动路径（launch / exec / shell / bootstrap）统一经过 [buildNetworkPlan]
 *   和 [buildContainerProotBaseArgv]，不存在旁路
 * - 长驻容器只跟随 Android 给 Kite 分配的默认网络，不识别或复制 VPN 配置
 */
object KFContainerManager {
    private const val MAX_MANAGED_COMMAND_SYMLINK_DEPTH = 8
    private const val PROOT_TELEMETRY_MODE = "lifecycle_v2_active_registry_v1"
    private const val PROOT_TELEMETRY_FILE_NAME = "kf-proot-telemetry.jsonl"
    private const val PROOT_ACTIVE_REGISTRY_DIR_NAME = "kf-proot-active"
    private const val NODE_COMPILE_CACHE_CONTAINER_PATH = "/workspace/.kf/cache/node-compile"
    private const val NODE_COMPILE_CACHE_WORKSPACE_PATH = ".kf/cache/node-compile"
    private const val RUNTIME_SMOKE_TOKEN = "KITE_RUNTIME_READY"
    private const val RUNTIME_SMOKE_TIMEOUT_MS = 12_000L
    private const val CONTAINER_ROOTFS_READY_MARKER = ".kf-container-rootfs-ready"
    private const val CONTAINER_ROOTFS_READY_MARKER_SCHEMA = "1"
    internal val CONTAINER_ROOTFS_REQUIRED_FILES = listOf(
        "bin/sh",
        "bin/bash",
        "usr/bin/env",
        "usr/bin/supervisord",
        "usr/bin/supervisorctl",
        "etc/supervisor/supervisord.conf"
    )

    /**
     * 容器网络配置的唯一真相源。
     *
     * 每次容器启动前由 [buildNetworkPlan] 构造，包含了：
     * - 当前生效的 DNS 服务器列表（已去重、已过滤明显无效地址）
     * - 运行时 resolv.conf 的写入路径（app 私有目录，非 rootfs）
     * - 网络模式标识
     * - 诊断用元数据
     *
     * 网络选择权属于 Android；这里不得增加 VPN、节点或 provider 特判。
     */
    data class ContainerNetworkPlan(
        /** Android 当前默认网络提供的 DNS 服务器列表 */
        val dnsServers: List<String>,
        /** 运行时 resolv.conf 的写入路径（app 私有目录） */
        val runtimeResolvConfPath: String,
        /** 容器内 resolv.conf 的挂载路径（固定为 /etc/resolv.conf） */
        val containerResolvConfTarget: String = "/etc/resolv.conf",
        /** 网络模式 */
        val networkMode: NetworkMode,
        /** 调试用：此次 DNS 来源描述 */
        val dnsSourceDescription: String,
        val semantics: RuntimeNetworkSemantics,
    ) {
        /** proot argv 中用于 bind-mount resolv.conf 的参数片段 */
        val resolvConfBindArgv: List<String>
            get() = listOf("-b", "$runtimeResolvConfPath:$containerResolvConfTarget")
    }

    private data class BuildPreparation(
        val layout: AssetExtractor.RuntimeLayout,
        val container: ContainerRecord,
        val androidStorage: AndroidSharedStorageSnapshot,
        val shellPath: String,
        val networkPlan: ContainerNetworkPlan,
        val viewBinding: ProotViewBinding?,
        val environmentWorkspace: ProotEnvironmentWorkspacePlan,
    )

    private data class OrdinaryLaunchPreparationCandidate(
        val layout: AssetExtractor.RuntimeLayout,
        val container: ContainerRecord,
        val identity: RuntimeLaunchPreparationIdentity,
    )

    private data class ContainerFilesystemView(
        val rootfsDir: File,
        val workspaceDir: File
    )

    private data class BootstrapHealth(
        val hasPackages: Boolean,
        val hasDpkgPreconfigure: Boolean,
        val missingFiles: List<String>
    ) {
        val ready: Boolean
            get() = hasPackages

        fun summary(): String {
            val missingSummary = if (missingFiles.isEmpty()) {
                "none"
            } else {
                missingFiles.joinToString(limit = 5)
            }
            return "ready=$ready, hasPackages=$hasPackages, hasDpkgPreconfigure=$hasDpkgPreconfigure, missing=$missingSummary"
        }
    }

    private const val DEFAULT_CONTAINER_ID = "ubuntu-main"
    private const val BASE_BOOTSTRAP_MARKER = ".kf-base-bootstrap-ready"
    private const val CONTAINER_BOOTSTRAP_MARKER = ".kf-container-bootstrap-ready"
    private const val RUNTIME_FILES_REFRESH_THROTTLE_MS = 5_000L
    private const val DEFAULT_TIME_ZONE_ID = "UTC"
    private val ANDROID_HOST_GROUP_NAMES = mapOf(
        1004 to "android_input",
        1007 to "android_log",
        1011 to "android_adb",
        1015 to "android_sdcard_rw",
        1028 to "android_sdcard_r",
        1078 to "android_ext_data_rw",
        1079 to "android_ext_obb_rw",
        3001 to "android_net_bt_admin",
        3002 to "android_net_bt",
        3003 to "android_inet",
        3006 to "android_net_bw_stats",
        3009 to "android_readproc",
        3011 to "android_uhid",
        3012 to "android_readtracefs",
        9997 to "android_everybody"
    )
    private val BOOTSTRAP_REQUIRED_FILES = listOf(
        "bin/sh",
        "bin/bash",
        "usr/bin/env",
        "usr/bin/find",
        "usr/bin/grep",
        "usr/bin/sed",
        "usr/bin/tar",
        "usr/bin/xz",
        "var/lib/dpkg/status"
    )

    private val _containerState = MutableStateFlow<ContainerRecord?>(null)
    val containerState: StateFlow<ContainerRecord?> = _containerState
    private val runtimeFilesRefreshedAt = LinkedHashMap<String, Long>()
    private val launchLifecycleLock = ReentrantReadWriteLock()
    private val ordinaryLaunchPreparationBootstrapLock = Any()
    private val ordinaryLaunchPreparationCache = RuntimeLaunchPreparationCache<BuildPreparation>()

    @Volatile
    private var preparedDefaultContainerIdentity: RuntimeLaunchPreparationIdentity? = null

    private inline fun <T> traceStage(stage: String, block: () -> T): T {
        val startedAt = SystemClock.elapsedRealtime()
        RuntimeBootstrapProgress.stageStarted(stage)
        Logger.i("ContainerManager", "阶段开始: $stage")
        return try {
            block().also {
                val durationMs = SystemClock.elapsedRealtime() - startedAt
                RuntimeBootstrapProgress.stageCompleted(stage)
                Logger.i("ContainerManager", "阶段完成: $stage, cost=${durationMs}ms")
            }
        } catch (error: Throwable) {
            val durationMs = SystemClock.elapsedRealtime() - startedAt
            RuntimeBootstrapProgress.failed(error.message ?: error.javaClass.simpleName)
            Logger.e(
                "ContainerManager",
                "阶段失败: $stage, cost=${durationMs}ms, error=${error.message}"
            )
            throw error
        }
    }

    @Synchronized
    fun ensureBaseImageReady(context: Context): AssetExtractor.RuntimeLayout {
        val layout = traceStage("prepareRuntime(base-image)") {
            AssetExtractor.prepareRuntime(context)
        }
        Logger.i("ContainerManager", "开始检查基础镜像就绪状态")
        traceStage("ensureBaseImageBootstrap") {
            ensureBaseImageBootstrap(context, layout)
        }
        return layout
    }

    @Synchronized
    fun ensureDefaultContainer(context: Context): ContainerRecord {
        val appContext = context.applicationContext
        resolveOrdinaryLaunchPreparationCandidate(appContext)?.let { candidate ->
            if (preparedDefaultContainerIdentity == candidate.identity) {
                Logger.i(
                    "ContainerManager",
                    "默认容器准备快照命中: id=${candidate.container.id}, createdAt=${candidate.container.createdAt}",
                )
                return candidate.container
            }

            // Provider 的跨进程收据已经核对本版本、时区、关键文件、权限和工作区事实；
            // 动态网络仍在每个新进程重新生成，不把网络状态写入静态收据。
            traceStage("buildNetworkPlan(cold-reuse:${candidate.container.id})") {
                buildNetworkPlan(appContext, candidate.layout, candidate.container)
            }
            _containerState.value = candidate.container
            preparedDefaultContainerIdentity = buildRuntimeLaunchPreparationIdentity(
                candidate.layout,
                candidate.container,
            )
            Logger.i(
                "ContainerManager",
                "默认容器冷进程复用: id=${candidate.container.id}, " +
                    "createdAt=${candidate.container.createdAt}, mutableRepair=receipt_verified",
            )
            return candidate.container
        }
        return ensureDefaultContainerFullPreparation(appContext)
    }

    private fun ensureDefaultContainerFullPreparation(appContext: Context): ContainerRecord {
        val layout = traceStage("ensureBaseImageReady(default-container)") {
            ensureBaseImageReady(appContext)
        }
        val registry = traceStage("loadRegistry(default-container)") {
            loadRegistry(layout.registryFile)
        }
        val existing = registry.firstOrNull { it.id == DEFAULT_CONTAINER_ID }
        val container = when {
            existing == null -> createDefaultContainer(layout)
            isDefaultContainerRecordCurrent(existing, layout) -> existing
            else -> {
                Logger.i(
                    "ContainerManager",
                    "默认容器记录已过期，重建系统层: id=${existing.id}, rootfs=${existing.rootfsPath}"
                )
                deleteContainerRootfsIfSafe(existing, layout)
                createDefaultContainer(layout)
            }
        }

        traceStage("ensureContainerFilesystem(${container.id})") {
            ensureContainerFilesystem(appContext, layout, container)
        }

        val updatedRegistry = registry
            .filterNot { it.id == container.id }
            .toMutableList()
            .apply { add(container) }

        traceStage("saveRegistry(default-container)") {
            saveRegistry(layout.registryFile, updatedRegistry)
        }
        _containerState.value = container
        preparedDefaultContainerIdentity = buildRuntimeLaunchPreparationIdentity(layout, container)
        return container
    }

    /** Debug 固定矩阵的原完整路径；不接受外部参数，也不改变正式入口的选择。 */
    @Synchronized
    internal fun ensureDefaultContainerFullPreparationForBenchmark(context: Context): ContainerRecord =
        ensureDefaultContainerFullPreparation(context.applicationContext)

    @Synchronized
    fun ensureNobleCanaryContainer(context: Context): ContainerRecord {
        val layout = traceStage("prepareRuntime(noble-canary)") {
            AssetExtractor.prepareRuntime(context, BaseImageProfile.NOBLE)
        }
        val registry = traceStage("loadRegistry(noble-canary)") {
            loadRegistry(layout.registryFile)
        }
        val canaryId = "ubuntu-noble-canary"
        val existing = registry.firstOrNull { it.id == canaryId }
        val container = existing ?: createCanaryContainer(layout, canaryId, BaseImageProfile.NOBLE)

        traceStage("ensureContainerFilesystem(${container.id})") {
            ensureContainerFilesystem(context, layout, container)
        }

        val updatedRegistry = registry
            .filterNot { it.id == container.id }
            .toMutableList()
            .apply { add(container) }

        traceStage("saveRegistry(noble-canary)") {
            saveRegistry(layout.registryFile, updatedRegistry)
        }
        _containerState.value = container
        return container
    }

    private fun createCanaryContainer(
        layout: AssetExtractor.RuntimeLayout,
        containerId: String,
        profile: BaseImageProfile
    ): ContainerRecord {
        val containerRoot = File(layout.containersDir, containerId)
        val workspaceDir = File(layout.sharedDir, containerId)
        val record = ContainerRecord(
            id = containerId,
            displayName = "Ubuntu 24.04 Canary",
            imageName = profile.imageName,
            baseProfile = profile.codename,
            rootfsPath = File(containerRoot, "rootfs").absolutePath,
            workspacePath = workspaceDir.absolutePath,
            createdAt = System.currentTimeMillis(),
            status = ContainerStatus.CREATED
        )
        Logger.i("ContainerManager", "创建 canary 容器记录: ${record.id} (${profile.label})")
        return record
    }

    fun getSavedContainer(context: Context): ContainerRecord? {
        val inMemory = _containerState.value
        if (inMemory != null) {
            return inMemory
        }

        val registryFile = AssetExtractor.getRuntimeLayout(context).registryFile
        return loadRegistry(registryFile).firstOrNull { it.id == DEFAULT_CONTAINER_ID }?.also {
            _containerState.value = it
        }
    }

    /**
     * 返回 `/workspace` 当前对应的真实宿主目录，但不启动或准备 Ubuntu runtime。
     * 文件提供器和目录选择器使用这个只读定位入口，避免为了浏览文件触发容器启动。
     */
    fun resolveWorkspaceDirectory(context: Context): File {
        val appContext = context.applicationContext
        return getSavedContainer(appContext)
            ?.workspacePath
            ?.takeIf(String::isNotBlank)
            ?.let(::File)
            ?: File(AssetExtractor.getRuntimeLayout(appContext).sharedDir, DEFAULT_CONTAINER_ID)
    }

    fun isDefaultContainerReady(context: Context): Boolean {
        val layout = AssetExtractor.getRuntimeLayout(context.applicationContext)
        if (!AssetExtractor.isBaseImageReady(context.applicationContext, layout.profile)) return false
        val saved = getSavedContainer(context.applicationContext) ?: return false
        return isDefaultContainerRecordCurrent(saved, layout) &&
            isContainerRootfsReady(File(saved.rootfsPath), layout.profile) &&
            File(saved.workspacePath).isDirectory
    }

    fun buildLaunchConfig(
        context: Context,
        requestedProotViewId: String? = null,
        requestedProotEnvironmentId: String? = null
    ): ContainerLaunchConfig {
        return launchLifecycleLock.read {
            val prepared = prepareBuildContext(
                context = context,
                caller = "buildLaunchConfig",
                refreshRuntimeFiles = true,
                requestedProotViewId = requestedProotViewId,
                requestedProotEnvironmentId = requestedProotEnvironmentId
            )

            val args = buildContainerProotBaseArgv(
                layout = prepared.layout,
                container = prepared.container,
                androidStorage = prepared.androidStorage,
                workingDirectory = RuntimeBoundary.CONTAINER_ROOT_HOME,
                networkPlan = prepared.networkPlan,
                lane = ProotLaunchLane.INTERACTIVE,
                purpose = "terminal_login_shell",
                viewBinding = prepared.viewBinding,
                environmentWorkspace = prepared.environmentWorkspace,
            ).apply {
                add("/bin/bash")
                add("--login")
            }.toTypedArray()

            val env = buildContainerEnvironment(
                context = context,
                layout = prepared.layout,
                container = prepared.container,
                shellPath = prepared.shellPath,
                viewBinding = prepared.viewBinding
            )
                .map { (key, value) -> "$key=$value" }
                .toTypedArray()

            ContainerLaunchConfig(
                container = prepared.container,
                executablePath = prepared.layout.prootFile.absolutePath,
                workingDirectory = prepared.environmentWorkspace.workspaceDirectory.absolutePath,
                args = args,
                env = env
            )
        }
    }

    fun buildContainerShellCommand(
        context: Context,
        workingDirectory: String = RuntimeBoundary.CONTAINER_WORKSPACE_PATH,
        payload: String,
        loginShell: Boolean = true,
        requestedProotViewId: String? = null,
        requestedProotEnvironmentId: String? = null
    ): Array<String> {
        return launchLifecycleLock.read {
            val prepared = prepareBuildContext(
                context = context,
                caller = "buildContainerShellCommand",
                refreshRuntimeFiles = true,
                requestedProotViewId = requestedProotViewId,
                requestedProotEnvironmentId = requestedProotEnvironmentId
            )
            val safeWorkingDirectory = workingDirectory.trim()
                .ifBlank { RuntimeBoundary.CONTAINER_WORKSPACE_PATH }
            val wrappedPayload =
                "cd '${shellQuote(safeWorkingDirectory)}' 2>/dev/null || cd ${RuntimeBoundary.CONTAINER_ROOT_HOME}; $payload"

            val innerArgv = if (loginShell) {
                listOf("/bin/bash", "-lc", wrappedPayload)
            } else {
                listOf("/bin/sh", "-c", wrappedPayload)
            }
            val commandArgv = buildContainerProotBaseArgv(
                layout = prepared.layout,
                container = prepared.container,
                androidStorage = prepared.androidStorage,
                workingDirectory = safeWorkingDirectory,
                networkPlan = prepared.networkPlan,
                lane = ProotLaunchLane.EXEC,
                purpose = "container_shell_command",
                viewBinding = prepared.viewBinding,
                environmentWorkspace = prepared.environmentWorkspace,
            ).apply {
                addAll(innerArgv)
            }

            arrayOf(
                "/system/bin/sh",
                "-c",
                buildInlineShellEnvironment(
                    buildContainerEnvironment(
                        context = context,
                        layout = prepared.layout,
                        container = prepared.container,
                        shellPath = prepared.shellPath,
                        viewBinding = prepared.viewBinding
                    )
                ) + " " +
                    buildInlineCommandArgv(commandArgv)
            )
        }
    }

    fun ensureRuntimeOperational(context: Context) {
        launchLifecycleLock.read {
            val config = buildContainerExecConfig(
                context = context,
                workingDirectory = RuntimeBoundary.CONTAINER_ROOT_HOME,
                payload = "printf '$RUNTIME_SMOKE_TOKEN\n'",
                loginShell = true
            )
            val process = ProcessBuilder(config.command)
                .redirectErrorStream(true)
                .apply { environment().putAll(config.env) }
                .start()
            val output = StringBuilder()
            val reader = thread(start = true, isDaemon = true, name = "KiteRuntimeSmokeReader") {
                runCatching {
                    process.inputStream.bufferedReader().useLines { lines ->
                        lines.forEach { line -> output.append(line).append('\n') }
                    }
                }
            }
            val completed = process.waitFor(RUNTIME_SMOKE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            if (!completed) {
                process.destroyForcibly()
                reader.join(500L)
                throw IllegalStateException("Ubuntu 启动校验超时")
            }
            reader.join(1_000L)
            val exitCode = process.exitValue()
            if (exitCode != 0 || !output.contains(RUNTIME_SMOKE_TOKEN)) {
                val message = output.toString().trim().take(360).ifBlank { "无输出" }
                throw IllegalStateException("Ubuntu 启动校验失败，exitCode=$exitCode，output=$message")
            }
        }
    }

    fun buildContainerExecConfig(
        context: Context,
        workingDirectory: String = RuntimeBoundary.CONTAINER_WORKSPACE_PATH,
        payload: String,
        loginShell: Boolean = true,
        requestedProotViewId: String? = null,
        requestedProotEnvironmentId: String? = null,
        extraBindMounts: List<ProotBindMount> = emptyList(),
    ): ContainerExecConfig {
        return launchLifecycleLock.read {
            val prepared = prepareBuildContext(
                context = context,
                caller = "buildContainerExecConfig",
                requestedProotViewId = requestedProotViewId,
                requestedProotEnvironmentId = requestedProotEnvironmentId
            )
            val safeWorkingDirectory = workingDirectory.trim()
                .ifBlank { RuntimeBoundary.CONTAINER_WORKSPACE_PATH }

            val command = buildContainerProotBaseArgv(
                layout = prepared.layout,
                container = prepared.container,
                androidStorage = prepared.androidStorage,
                workingDirectory = safeWorkingDirectory,
                networkPlan = prepared.networkPlan,
                lane = ProotLaunchLane.EXEC,
                purpose = "container_exec_config",
                viewBinding = prepared.viewBinding,
                environmentWorkspace = prepared.environmentWorkspace,
                extraBindMounts = extraBindMounts,
            ).apply {
                if (loginShell) {
                    add("/bin/bash")
                    add("-lc")
                    add(payload)
                } else {
                    add("/bin/sh")
                    add("-c")
                    add(payload)
                }
            }

            val env = buildContainerEnvironment(
                context = context,
                layout = prepared.layout,
                container = prepared.container,
                shellPath = prepared.shellPath,
                viewBinding = prepared.viewBinding
            )

            ContainerExecConfig(
                container = prepared.container,
                workingDirectory = safeWorkingDirectory,
                command = command,
                env = env
            )
        }
    }

    fun buildContainerArgvExecConfig(
        context: Context,
        workingDirectory: String = RuntimeBoundary.CONTAINER_WORKSPACE_PATH,
        argv: List<String>,
        requestedProotViewId: String? = null,
        requestedProotEnvironmentId: String? = null,
        extraBindMounts: List<ProotBindMount> = emptyList(),
    ): ContainerExecConfig {
        return launchLifecycleLock.read {
            val prepared = prepareBuildContext(
                context = context,
                caller = "buildContainerArgvExecConfig",
                requestedProotViewId = requestedProotViewId,
                requestedProotEnvironmentId = requestedProotEnvironmentId
            )
            val safeWorkingDirectory = workingDirectory.trim()
                .ifBlank { RuntimeBoundary.CONTAINER_WORKSPACE_PATH }
            assembleContainerArgvExecConfig(
                context,
                prepared,
                safeWorkingDirectory,
                argv,
                extraBindMounts,
            )
        }
    }

    /** RF2020 固定对照：强制走现有完整准备，但只构造配置，不创建业务进程。 */
    internal fun buildContainerArgvExecConfigFullPreparationForBenchmark(
        context: Context,
        argv: List<String>,
    ): ContainerExecConfig = launchLifecycleLock.read {
        val prepared = prepareBuildContextUncached(
            context = context.applicationContext,
            caller = "rf2020-full-benchmark",
        )
        assembleContainerArgvExecConfig(
            context = context.applicationContext,
            prepared = prepared,
            workingDirectory = RuntimeBoundary.CONTAINER_WORKSPACE_PATH,
            argv = argv,
        )
    }

    /** RF2020 未接生产的候选：复用 Ready 静态事实，动态启动投影仍逐冷进程构造。 */
    internal fun buildContainerArgvExecConfigColdReuseCandidateForBenchmark(
        context: Context,
        argv: List<String>,
    ): ContainerExecConfig = launchLifecycleLock.read {
        val appContext = context.applicationContext
        val candidate = checkNotNull(resolveOrdinaryLaunchPreparationCandidate(appContext)) {
            "ordinary_launch_preparation_candidate_not_ready"
        }
        val prepared = ordinaryLaunchPreparationCache.getOrBuild(
            identity = candidate.identity,
            buildReason = "rf2020-candidate-benchmark",
        ) {
            prepareBuildContextFromReadyCandidate(
                context = appContext,
                caller = "rf2020-candidate-benchmark",
                refreshRuntimeFiles = false,
                candidate = candidate,
            )
        }
        assembleContainerArgvExecConfig(
            context = appContext,
            prepared = prepared,
            workingDirectory = RuntimeBoundary.CONTAINER_WORKSPACE_PATH,
            argv = argv,
        )
    }

    private fun assembleContainerArgvExecConfig(
        context: Context,
        prepared: BuildPreparation,
        workingDirectory: String,
        argv: List<String>,
        extraBindMounts: List<ProotBindMount> = emptyList(),
    ): ContainerExecConfig {
        val command = buildContainerProotBaseArgv(
            layout = prepared.layout,
            container = prepared.container,
            androidStorage = prepared.androidStorage,
            workingDirectory = workingDirectory,
            networkPlan = prepared.networkPlan,
            lane = ProotLaunchLane.EXEC,
            purpose = "container_argv_exec_config",
            viewBinding = prepared.viewBinding,
            environmentWorkspace = prepared.environmentWorkspace,
            extraBindMounts = extraBindMounts,
        ).apply {
            addAll(argv)
        }
        val env = buildContainerEnvironment(
            context = context,
            layout = prepared.layout,
            container = prepared.container,
            shellPath = prepared.shellPath,
            viewBinding = prepared.viewBinding,
        )
        return ContainerExecConfig(
            container = prepared.container,
            workingDirectory = workingDirectory,
            command = command,
            env = env,
        )
    }

    @Synchronized
    fun markStarting(context: Context) {
        updateContainer(context) { current ->
            current.copy(
                status = ContainerStatus.STARTING,
                lastError = null
            )
        }
    }

    @Synchronized
    fun markRunning(context: Context, pid: Int) {
        updateContainer(context) { current ->
            current.copy(
                status = ContainerStatus.RUNNING,
                pid = pid,
                lastStartedAt = System.currentTimeMillis(),
                lastError = null
            )
        }
    }

    @Synchronized
    fun markStopped(context: Context) {
        updateContainer(context) { current ->
            current.copy(
                status = ContainerStatus.STOPPED,
                pid = null
            )
        }
    }

    @Synchronized
    fun markError(context: Context, message: String) {
        updateContainer(context) { current ->
            current.copy(
                status = ContainerStatus.ERROR,
                pid = null,
                lastError = message
            )
        }
    }

    /**
     * 按“基础镜像 -> 当前空间”的模型重建默认容器。
     *
     * 当前约定：
     * 1. `images/ubuntu-base` 是纯净样本，只在首次初始化或显式重建时参与克隆。
     * 2. `containers/ubuntu-main/rootfs` 是当前空间的系统层，正常升级安装不会被清空。
     * 3. `shared/ubuntu-main` 是当前空间的工作区，默认应该持续保留。
     *
     * 因此这里默认只重建系统层，不清空工作区；只有明确要求时才一起删除工作区。
     */
    fun resetDefaultContainer(context: Context, wipeWorkspace: Boolean = false): ContainerRecord {
        return launchLifecycleLock.write {
            ordinaryLaunchPreparationCache.invalidate("reset_default_container")
            WarmProotExecutionCoordinator.invalidate("reset_default_container")
            preparedDefaultContainerIdentity = null
            val layout = ensureBaseImageReady(context)
            getSavedContainer(context)?.let { container ->
                val containerRoot = File(container.rootfsPath).parentFile
                deleteRecursively(containerRoot)
                if (wipeWorkspace) {
                    deleteRecursively(File(container.workspacePath))
                }
            }

            val container = createDefaultContainer(layout)
            ensureContainerFilesystem(context, layout, container)
            saveRegistry(layout.registryFile, listOf(container))
            _containerState.value = container
            preparedDefaultContainerIdentity = buildRuntimeLaunchPreparationIdentity(layout, container)
            container
        }
    }

    fun getRuntimeLayout(context: Context): AssetExtractor.RuntimeLayout {
        return AssetExtractor.getRuntimeLayout(context)
    }

    fun isCommandAvailable(context: Context, rawCommand: String): Boolean {
        val commandName = rawCommand.trim().substringBefore(' ').trim()
        if (commandName.isBlank()) {
            return false
        }

        // 暂留桥接：入口层仍需要借建房层解析当前容器 PATH，判断工作面里是否已经装好命令。
        // 后续如果工作面拥有独立的命令索引或探测器，这段应迁出 KFContainerManager。
        val layout = ensureBaseImageReady(context)
        val container = resolveLaunchContainer(context, layout)
        return buildShellPath(container)
            .split(':')
            .mapNotNull { pathEntry -> resolveCommandHostFile(container, pathEntry, commandName) }
            .any { candidate -> candidate.exists() && candidate.isFile }
    }

    /**
     * 读取普通 PRoot 当前 PATH 中受管命令的宿主文件身份，不执行 PRoot，也不触发 Base/容器准备。
     *
     * 返回值允许只包含部分命令：调用方只有在目标资源的全部命令都有文件身份时，
     * 才能据此复用之前的 shell 正向证明。
     */
    internal fun managedCommandVerificationBasis(
        context: Context,
        rawCommands: Collection<String>,
    ): ManagedCommandVerificationBasis? {
        val commands = rawCommands
            .map { rawCommand -> rawCommand.trim().substringBefore(' ').trim() }
            .filter(String::isNotBlank)
            .distinct()
            .sorted()
        val candidate = resolveOrdinaryLaunchPreparationCandidate(context.applicationContext) ?: return null
        val shellPaths = buildShellPath(candidate.container).split(':')
        val commandFiles = commands.mapNotNull { command ->
            shellPaths
                .asSequence()
                .mapNotNull { pathEntry -> resolveCommandHostFile(candidate.container, pathEntry, command) }
                .mapNotNull { hostFile -> managedCommandHostFileStamp(candidate.container, command, hostFile) }
                .firstOrNull()
        }
        return ManagedCommandVerificationBasis(
            runtimeIdentity = candidate.identity,
            commandFiles = commandFiles,
        )
    }

    private fun managedCommandHostFileStamp(
        container: ContainerRecord,
        command: String,
        initialFile: File,
    ): ManagedCommandHostFileStamp? {
        var current = initialFile
        val visited = linkedSetOf<String>()
        val linkChain = mutableListOf<String>()
        repeat(MAX_MANAGED_COMMAND_SYMLINK_DEPTH) {
            val currentPath = current.toPath()
            val absolutePath = current.absolutePath
            if (!visited.add(absolutePath)) return null
            val attributes = runCatching {
                Files.readAttributes(
                    currentPath,
                    BasicFileAttributes::class.java,
                    LinkOption.NOFOLLOW_LINKS,
                )
            }.getOrNull() ?: return null
            if (!attributes.isSymbolicLink) {
                if (!attributes.isRegularFile || !Files.isExecutable(currentPath)) return null
                return ManagedCommandHostFileStamp(
                    command = command,
                    hostPath = initialFile.absolutePath,
                    canonicalPath = runCatching(current::getCanonicalPath).getOrDefault(current.absolutePath),
                    linkChain = linkChain.toList(),
                    lastModifiedMs = attributes.lastModifiedTime().toMillis(),
                    length = attributes.size(),
                    executable = true,
                )
            }

            val linkTarget = runCatching { Files.readSymbolicLink(currentPath).toString() }.getOrNull()
                ?: return null
            linkChain += "$absolutePath->$linkTarget"
            current = if (linkTarget.startsWith('/')) {
                resolveContainerHostPath(container, linkTarget) ?: return null
            } else {
                File(current.parentFile, linkTarget).normalize().takeIf { nextFile ->
                    isWithinContainerHostRoots(container, nextFile)
                } ?: return null
            }
        }
        return null
    }

    private fun resolveContainerHostPath(container: ContainerRecord, containerPath: String): File? {
        if (!containerPath.startsWith('/')) return null
        if (containerPath == RuntimeBoundary.CONTAINER_WORKSPACE_PATH) {
            return File(container.workspacePath)
        }
        if (containerPath.startsWith("${RuntimeBoundary.CONTAINER_WORKSPACE_PATH}/")) {
            return File(
                container.workspacePath,
                containerPath.removePrefix("${RuntimeBoundary.CONTAINER_WORKSPACE_PATH}/")
            )
        }
        return File(container.rootfsPath, containerPath.removePrefix("/"))
    }

    private fun isWithinContainerHostRoots(container: ContainerRecord, candidate: File): Boolean {
        val candidatePath = candidate.absoluteFile.normalize().path
        return listOf(container.rootfsPath, container.workspacePath).any { rootPath ->
            val normalizedRoot = File(rootPath).absoluteFile.normalize().path.trimEnd(File.separatorChar)
            candidatePath == normalizedRoot || candidatePath.startsWith("$normalizedRoot${File.separator}")
        }
    }

    private fun resolveLaunchContainer(
        context: Context,
        layout: AssetExtractor.RuntimeLayout = ensureBaseImageReady(context)
    ): ContainerRecord {
        val saved = getSavedContainer(context)
        if (saved != null &&
            isDefaultContainerRecordCurrent(saved, layout) &&
            isContainerRootfsReady(File(saved.rootfsPath), layout.profile)
        ) {
            return saved
        }
        return ensureDefaultContainer(context)
    }

    private fun prepareBuildContext(
        context: Context,
        caller: String,
        refreshRuntimeFiles: Boolean = false,
        requestedProotViewId: String? = null,
        requestedProotEnvironmentId: String? = null
    ): BuildPreparation {
        if (!RuntimeLaunchPreparationPolicy.isCacheEligible(
                requestedProotViewId = requestedProotViewId,
                requestedProotEnvironmentId = requestedProotEnvironmentId,
            )
        ) {
            Logger.i("ContainerManager", "启动准备绕过缓存: caller=$caller, reason=explicit_view_or_environment")
            return prepareBuildContextUncached(
                context = context,
                caller = caller,
                refreshRuntimeFiles = refreshRuntimeFiles,
                requestedProotViewId = requestedProotViewId,
                requestedProotEnvironmentId = requestedProotEnvironmentId,
            )
        }

        val appContext = context.applicationContext
        resolveOrdinaryLaunchPreparationCandidate(appContext)?.let { candidate ->
            return prepareOrdinaryBuildContext(
                context = appContext,
                caller = caller,
                refreshRuntimeFiles = refreshRuntimeFiles,
                candidate = candidate,
            )
        }

        // 首次安装或运行时被修复时，先让一个调用完成完整准备；其余并发调用随后直接命中快照。
        return synchronized(ordinaryLaunchPreparationBootstrapLock) {
            resolveOrdinaryLaunchPreparationCandidate(appContext)?.let { candidate ->
                return@synchronized prepareOrdinaryBuildContext(
                    context = appContext,
                    caller = caller,
                    refreshRuntimeFiles = refreshRuntimeFiles,
                    candidate = candidate,
                )
            }

            if (ordinaryLaunchPreparationCache.snapshot().hasEntry) {
                ordinaryLaunchPreparationCache.invalidate("ordinary_candidate_unavailable")
            }
            val prepared = prepareBuildContextUncached(
                context = appContext,
                caller = caller,
                refreshRuntimeFiles = refreshRuntimeFiles,
            )
            ordinaryLaunchPreparationCache.getOrBuild(
                identity = buildRuntimeLaunchPreparationIdentity(prepared.layout, prepared.container),
                buildReason = "$caller:bootstrap",
            ) { prepared }
        }
    }

    private fun prepareOrdinaryBuildContext(
        context: Context,
        caller: String,
        refreshRuntimeFiles: Boolean,
        candidate: OrdinaryLaunchPreparationCandidate,
    ): BuildPreparation {
        val before = ordinaryLaunchPreparationCache.snapshot()
        val startedAt = SystemClock.elapsedRealtime()
        var prepared = ordinaryLaunchPreparationCache.getOrBuild(
            identity = candidate.identity,
            buildReason = caller,
        ) {
            prepareBuildContextUncached(
                context = context,
                caller = caller,
                refreshRuntimeFiles = refreshRuntimeFiles,
            )
        }
        val after = ordinaryLaunchPreparationCache.snapshot()
        val result = if (after.rebuildCount > before.rebuildCount) "rebuild" else "hit"

        // 状态、pid、错误等动态字段仍由 ContainerRecord 的真相源持有，不进入静态快照。
        if (prepared.container != candidate.container) {
            prepared = prepared.copy(container = candidate.container)
        }
        if (refreshRuntimeFiles && result == "hit") {
            traceStage("refreshContainerRuntimeFiles($caller)") {
                refreshContainerRuntimeFiles(context, candidate.container)
            }
        }
        Logger.i(
            "ContainerManager",
            "启动准备缓存: caller=$caller, result=$result, generation=${after.generation}, " +
                "cost=${SystemClock.elapsedRealtime() - startedAt}ms",
        )
        return prepared
    }

    private fun resolveOrdinaryLaunchPreparationCandidate(
        context: Context,
    ): OrdinaryLaunchPreparationCandidate? {
        val inspection = inspectDefaultContainerColdReuse(context)
        val ready = inspection.decision as? DefaultContainerColdReuseDecision.Ready ?: return null
        val layout = inspection.layout ?: return null
        val container = inspection.container ?: return null
        return OrdinaryLaunchPreparationCandidate(
            layout = layout,
            container = container,
            identity = ready.identity,
        )
    }

    internal fun defaultContainerColdReuseDecision(
        context: Context,
        requestedProotViewId: String? = null,
        requestedProotEnvironmentId: String? = null,
    ): DefaultContainerColdReuseDecision = inspectDefaultContainerColdReuse(
        context = context.applicationContext,
        requestedProotViewId = requestedProotViewId,
        requestedProotEnvironmentId = requestedProotEnvironmentId,
    ).decision

    private data class DefaultContainerColdReuseInspection(
        val layout: AssetExtractor.RuntimeLayout?,
        val container: ContainerRecord?,
        val decision: DefaultContainerColdReuseDecision,
    )

    private fun inspectDefaultContainerColdReuse(
        context: Context,
        requestedProotViewId: String? = null,
        requestedProotEnvironmentId: String? = null,
    ): DefaultContainerColdReuseInspection {
        val layout = AssetExtractor.getRuntimeLayout(context)
        val container = getSavedContainer(context)
        val containerRecordCurrent = container != null && isDefaultContainerRecordCurrent(container, layout)
        val containerRootfsReady = containerRecordCurrent &&
            isContainerRootfsReady(File(checkNotNull(container).rootfsPath), layout.profile)
        val workspaceReady = containerRecordCurrent && File(checkNotNull(container).workspacePath).isDirectory
        val mutableRepairCurrent = containerRecordCurrent && DefaultContainerColdReuseReceipt.isCurrent(
            context = context,
            layout = layout,
            container = checkNotNull(container),
            hostTimeZoneId = currentHostTimeZoneId(),
        )
        val runtimeReady = mutableRepairCurrent &&
            layout.prootFile.isFile &&
            layout.prootLibtallocFile.isFile &&
            layout.prootLoaderFile.isFile &&
            layout.prootLoader32File.isFile &&
            layout.prootRuntimeDescriptorFile.isFile
        val identity = container
            ?.takeIf { containerRecordCurrent }
            ?.let { buildRuntimeLaunchPreparationIdentity(layout, it) }
        val decision = DefaultContainerColdReuseProvider.evaluate(
            DefaultContainerColdReuseFacts(
                ordinaryRequest = RuntimeLaunchPreparationPolicy.isCacheEligible(
                    requestedProotViewId = requestedProotViewId,
                    requestedProotEnvironmentId = requestedProotEnvironmentId,
                ),
                runtimeAssetsCurrent = runtimeReady,
                baseImageReady = AssetExtractor.isBaseImageReady(context, layout.profile),
                containerRecordCurrent = containerRecordCurrent,
                containerRootfsReady = containerRootfsReady,
                workspaceReady = workspaceReady,
                mutableRepairCurrent = mutableRepairCurrent,
                identity = identity,
            ),
        )
        return DefaultContainerColdReuseInspection(
            layout = layout,
            container = container,
            decision = decision,
        )
    }

    private fun buildRuntimeLaunchPreparationIdentity(
        layout: AssetExtractor.RuntimeLayout,
        container: ContainerRecord,
    ): RuntimeLaunchPreparationIdentity {
        val descriptor = layout.prootRuntimeDescriptorFile
        val descriptorStamp = descriptor.lastModified() * 31L + descriptor.length()
        return RuntimeLaunchPreparationIdentity(
            runtimeRootPath = layout.runtimeRoot.absolutePath,
            runtimeDescriptorStamp = descriptorStamp,
            containerId = container.id,
            containerCreatedAtMs = container.createdAt,
            rootfsPath = container.rootfsPath,
            workspacePath = container.workspacePath,
            networkMode = container.networkMode.name,
        )
    }

    internal fun runtimeLaunchPreparationCacheSnapshot(): RuntimeLaunchPreparationCacheSnapshot =
        ordinaryLaunchPreparationCache.snapshot()

    private fun prepareBuildContextUncached(
        context: Context,
        caller: String,
        refreshRuntimeFiles: Boolean = false,
        requestedProotViewId: String? = null,
        requestedProotEnvironmentId: String? = null,
    ): BuildPreparation {
        val layout = traceStage("prepareRuntime($caller)") {
            AssetExtractor.prepareRuntime(context)
        }
        val container = traceStage("resolveLaunchContainer($caller)") {
            resolveLaunchContainer(context, layout)
        }
        if (refreshRuntimeFiles) {
            traceStage("refreshContainerRuntimeFiles($caller)") {
                refreshContainerRuntimeFiles(context, container)
            }
        }
        val androidStorage = traceStage("resolveAndroidSharedStorage($caller)") {
            AndroidSharedStorageManager.snapshot(context)
        }
        traceStage("ensureWorkspaceSystemComponents($caller)") {
            WorkspaceBuildSupport.installSystemComponents(
                context = context,
                workspaceDir = containerFilesystem(container).workspaceDir,
                // T014h：普通启动路径必须查询 ProotViewStore 真实封存状态；
                // 已封存时迁移清理不得触碰 .kf/bin。
                sealedChecker = { isViewSealed(layout, container) },
            )
        }
        val shellPath = traceStage("buildShellPath($caller)") {
            buildShellPath(container)
        }
        val networkPlan = traceStage("buildNetworkPlan($caller)") {
            buildNetworkPlan(context, layout, container)
        }
        val viewBinding = traceStage("resolveProotView($caller)") {
            // 环境身份在此处一次性解析成确定 viewId，注入 native；native 热路径不查询 Android 注册表。
            ProotViewRuntime.resolveActiveBinding(
                container = container,
                runtimeDescriptor = readProotRuntimeDescriptor(layout),
                requestedViewId = requestedProotViewId,
                requestedEnvironmentId = requestedProotEnvironmentId
            )
        }
        traceStage("ensureNodeCompileCache($caller)") {
            val cacheDirectory = File(container.workspacePath, NODE_COMPILE_CACHE_WORKSPACE_PATH)
            check(cacheDirectory.mkdirs() || cacheDirectory.isDirectory) {
                "无法创建 Node 编译缓存目录：${cacheDirectory.absolutePath}"
            }
        }
        val environmentWorkspace = traceStage("resolveEnvironmentWorkspace($caller)") {
            ProotEnvironmentWorkspace.plan(container, viewBinding).also { it.ensureReady() }
        }
        return BuildPreparation(
            layout = layout,
            container = container,
            androidStorage = androidStorage,
            shellPath = shellPath,
            networkPlan = networkPlan,
            viewBinding = viewBinding,
            environmentWorkspace = environmentWorkspace,
        )
    }

    /**
     * 只允许由完整冷复用收据产生的普通候选进入。
     *
     * 收据已经逐文件证明 runtime、默认容器和工作区静态组件；这里仍逐冷进程解析
     * Android 共享存储、网络、View/环境工作区、Node 缓存与最终启动环境。
     */
    private fun prepareBuildContextFromReadyCandidate(
        context: Context,
        caller: String,
        refreshRuntimeFiles: Boolean,
        candidate: OrdinaryLaunchPreparationCandidate,
    ): BuildPreparation {
        if (refreshRuntimeFiles) {
            traceStage("refreshContainerRuntimeFiles($caller)") {
                refreshContainerRuntimeFiles(context, candidate.container)
            }
        }
        val androidStorage = traceStage("resolveAndroidSharedStorage($caller)") {
            AndroidSharedStorageManager.snapshot(context)
        }
        val shellPath = traceStage("buildShellPath($caller)") {
            buildShellPath(candidate.container)
        }
        val networkPlan = traceStage("buildNetworkPlan($caller)") {
            buildNetworkPlan(context, candidate.layout, candidate.container)
        }
        val viewBinding = traceStage("resolveProotView($caller)") {
            ProotViewRuntime.resolveActiveBinding(
                container = candidate.container,
                runtimeDescriptor = readProotRuntimeDescriptor(candidate.layout),
                requestedViewId = null,
                requestedEnvironmentId = null,
            )
        }
        traceStage("ensureNodeCompileCache($caller)") {
            val cacheDirectory = File(candidate.container.workspacePath, NODE_COMPILE_CACHE_WORKSPACE_PATH)
            check(cacheDirectory.mkdirs() || cacheDirectory.isDirectory) {
                "无法创建 Node 编译缓存目录：${cacheDirectory.absolutePath}"
            }
        }
        val environmentWorkspace = traceStage("resolveEnvironmentWorkspace($caller)") {
            ProotEnvironmentWorkspace.plan(candidate.container, viewBinding).also { it.ensureReady() }
        }
        return BuildPreparation(
            layout = candidate.layout,
            container = candidate.container,
            androidStorage = androidStorage,
            shellPath = shellPath,
            networkPlan = networkPlan,
            viewBinding = viewBinding,
            environmentWorkspace = environmentWorkspace,
        )
    }

    private fun buildContainerEnvironment(
        context: Context,
        layout: AssetExtractor.RuntimeLayout,
        container: ContainerRecord,
        shellPath: String,
        viewBinding: ProotViewBinding?
    ): LinkedHashMap<String, String> {
        return buildBaseContainerEnvironment(layout).apply {
            putAll(AndroidRuntimeHttpProxy.environment())
            // 暂留桥接：启动入口要直接拿到标准工作面 env，等独立 launcher 落地后再完全迁出建房层。
            putAll(WorkspaceBuildSupport.buildWorkSurfaceEnvironment())
            putAll(
                AdbBridgeContract.buildEnvironment(
                    ShizukuBridgeStatus.snapshot(context),
                    com.kite.app.foundation.devicebridge.DeviceBridgeBackendModeStore.current(context)
                )
            )
            put(
                "KF_PROCFS_PROJECTION_ROOT",
                WorkspaceBuildSupport.runtimeProcProjectionDir(File(container.workspacePath)).absolutePath
            )
            put("UV_LINK_MODE", "copy")
            put("NODE_COMPILE_CACHE", NODE_COMPILE_CACHE_CONTAINER_PATH)
            put("PATH", shellPath)
            if (viewBinding != null) {
                putAll(viewBinding.environment())
            }
        }
    }

    private fun buildBaseContainerEnvironment(
        layout: AssetExtractor.RuntimeLayout
    ): LinkedHashMap<String, String> {
        val prootTelemetryFile = prepareProotTelemetryFile(layout)
        val prootActiveRegistryRoot = prepareProotActiveRegistryRoot(layout)
        val env = linkedMapOf(
            "HOME" to "/root",
            "USER" to "root",
            "TERM" to "xterm-256color",
            "COLORTERM" to "truecolor",
            "FORCE_COLOR" to "3",
            "CLICOLOR_FORCE" to "1",
            "LANG" to "C.UTF-8",
            "TZ" to currentHostTimeZoneId(),
            "LD_LIBRARY_PATH" to layout.prootLibDir.absolutePath,
            "TMPDIR" to "/tmp",
            "TMP" to "/tmp",
            "TEMP" to "/tmp",
            "UV_LINK_MODE" to "copy",
            "PROOT_TMP_DIR" to layout.tmpDir.absolutePath,
            "KF_PROOT_TELEMETRY_MODE" to PROOT_TELEMETRY_MODE,
            "KF_PROOT_TELEMETRY_PATH" to prootTelemetryFile.absolutePath,
            "KF_PROOT_ACTIVE_REGISTRY_ROOT" to prootActiveRegistryRoot.absolutePath
        )
        env.putAll(ProotFileProtectionRuntime.activeEnvironment(layout))
        addProotLoaderEnvironmentIfNeeded(layout, env)
        return env
    }

    private fun prepareProotTelemetryFile(layout: AssetExtractor.RuntimeLayout): File {
        return File(layout.tmpDir, PROOT_TELEMETRY_FILE_NAME).also { file ->
            runCatching {
                file.parentFile?.mkdirs()
                if (!file.exists()) {
                    file.createNewFile()
                }
            }.onFailure { error ->
                Logger.e(
                    "KFContainerManager",
                    "无法预创建 PRoot telemetry 文件: ${file.absolutePath}, ${error.message}"
                )
            }
        }
    }

    private fun buildContainerProotBaseArgv(
        layout: AssetExtractor.RuntimeLayout,
        container: ContainerRecord,
        androidStorage: AndroidSharedStorageSnapshot,
        workingDirectory: String,
        networkPlan: ContainerNetworkPlan,
        lane: ProotLaunchLane,
        purpose: String,
        viewBinding: ProotViewBinding?,
        environmentWorkspace: ProotEnvironmentWorkspacePlan,
        extraBindMounts: List<ProotBindMount> = emptyList(),
    ): MutableList<String> {
        val plan = buildContainerProotLaunchPlan(
            layout = layout,
            container = container,
            androidStorage = androidStorage,
            workingDirectory = workingDirectory,
            networkPlan = networkPlan,
            lane = lane,
            purpose = purpose,
            viewBinding = viewBinding,
            environmentWorkspace = environmentWorkspace,
            extraBindMounts = extraBindMounts,
        )
        publishProotLaunchContract(container, plan)
        return plan.baseArgv()
    }

    private fun buildContainerProotLaunchPlan(
        layout: AssetExtractor.RuntimeLayout,
        container: ContainerRecord,
        androidStorage: AndroidSharedStorageSnapshot,
        workingDirectory: String,
        networkPlan: ContainerNetworkPlan,
        lane: ProotLaunchLane,
        purpose: String,
        viewBinding: ProotViewBinding?,
        environmentWorkspace: ProotEnvironmentWorkspacePlan,
        extraBindMounts: List<ProotBindMount> = emptyList(),
    ): ProotLaunchPlan {
        val prootRuntime = readProotRuntimeDescriptor(layout)
        return ProotLaunchPlan(
            kind = ProotLaunchPlanKind.CONTAINER,
            executablePath = layout.prootFile.absolutePath,
            rootfsPath = container.rootfsPath,
            workingDirectory = workingDirectory,
            flags = listOf("--link2symlink", "-0"),
            lane = lane,
            purpose = purpose,
            bindMounts = buildCommonProotBindMounts() +
                buildContainerBindMounts(environmentWorkspace) +
                AndroidSharedStorageManager.bindMounts(androidStorage) +
                ProotBindMount(
                    sourcePath = networkPlan.runtimeResolvConfPath,
                    targetPath = networkPlan.containerResolvConfTarget,
                    role = "runtime_resolv_conf",
                    writable = false
                ) + extraBindMounts,
            networkMode = container.networkMode,
            networkSemantics = networkPlan.semantics,
            includeNetworkModeFlag = true,
            tmpDirPath = layout.tmpDir.absolutePath,
            loaderMode = prootRuntime.optString("loaderMode").ifBlank { "external" },
            loaderPath = layout.prootLoaderFile.absolutePath,
            loader32Path = layout.prootLoader32File.absolutePath,
            prootRuntime = prootRuntime,
            filesystemView = viewBinding,
            telemetryMode = prootRuntime.optString("telemetryMode")
                .ifBlank { ProotLaunchPlan.TELEMETRY_NONE_CURRENT }
        )
    }

    private fun buildCommonProotBindMounts(): List<ProotBindMount> {
        return listOf(
            ProotBindMount("/dev", "/dev", "android_device_tree"),
            ProotBindMount("/proc", "/proc", "android_proc_view"),
            ProotBindMount("/sys", "/sys", "android_sys_view")
        )
    }

    private fun buildContainerBindMounts(
        environmentWorkspace: ProotEnvironmentWorkspacePlan
    ): List<ProotBindMount> {
        return environmentWorkspace.workspaceBindMounts()
    }

    private fun publishProotLaunchContract(container: ContainerRecord, plan: ProotLaunchPlan) {
        runCatching {
            WorkspaceBuildSupport.writeProotLaunchContract(
                workspaceDir = File(container.workspacePath),
                content = plan.toContractJson().toString(2) + "\n"
            )
        }.onFailure { error ->
            Logger.e("KFContainerManager", "Failed to publish PRoot launch contract: ${error.message}")
        }
    }

    private fun readProotRuntimeDescriptor(layout: AssetExtractor.RuntimeLayout): JSONObject {
        return runCatching {
            if (layout.prootRuntimeDescriptorFile.exists()) {
                JSONObject(layout.prootRuntimeDescriptorFile.readText())
            } else {
                JSONObject()
                    .put("component", "proot")
                    .put("assetId", "unknown")
                    .put("provider", "runtime_descriptor_missing")
                    .put("telemetryMode", "none_current")
                    .put(
                        "installed",
                        JSONObject()
                            .put("executablePath", layout.prootFile.absolutePath)
                            .put("descriptorPath", layout.prootRuntimeDescriptorFile.absolutePath)
                    )
            }
        }.getOrElse { error ->
            Logger.e("KFContainerManager", "Failed to read PRoot runtime descriptor: ${error.message}")
            JSONObject()
                .put("component", "proot")
                .put("assetId", "invalid_descriptor")
                .put("provider", "runtime_descriptor_parse_failed")
                .put("telemetryMode", "none_current")
        }
    }

    private fun prootLoaderMode(layout: AssetExtractor.RuntimeLayout): String {
        return readProotRuntimeDescriptor(layout)
            .optString("loaderMode")
            .ifBlank { "external" }
    }

    /**
     * T014h：查询容器 Base/View 是否已封存（ProotViewStore catalog 已初始化）。
     *
     * 迁移清理 .kf/bin 的门禁依据。runtime 不支持或查询异常时视为未封存（保守允许清理）。
     * 不依赖 versionCode/installMarker——App 升级后 View 可能早已封存。
     */
    private fun isViewSealed(
        layout: AssetExtractor.RuntimeLayout,
        container: com.kite.app.foundation.contracts.ContainerRecord,
    ): Boolean {
        return runCatching {
            val descriptor = readProotRuntimeDescriptor(layout)
            val capabilities = descriptor.optJSONArray("capabilities") ?: return@runCatching false
            val supported = (0 until capabilities.length()).any { capabilities.optString(it) == ProotViewStore.RUNTIME_CAPABILITY } &&
                (0 until capabilities.length()).any { capabilities.optString(it) == ProotViewStore.BLOCK_RUNTIME_CAPABILITY }
            if (!supported) return@runCatching false
            ProotViewStore.forContainer(container).recover() != null
        }.getOrDefault(false)
    }

    private fun addProotLoaderEnvironmentIfNeeded(
        layout: AssetExtractor.RuntimeLayout,
        env: MutableMap<String, String>
    ) {
        if (prootLoaderMode(layout).equals("external", ignoreCase = true)) {
            env["PROOT_LOADER"] = layout.prootLoaderFile.absolutePath
            env["PROOT_LOADER_32"] = layout.prootLoader32File.absolutePath
        }
    }

    private fun buildInlineShellEnvironment(env: Map<String, String>): String {
        val quotedKeys = setOf(
            "LD_LIBRARY_PATH",
            "PROOT_LOADER",
            "PROOT_LOADER_32",
            "PROOT_TMP_DIR",
            "GRADLE_USER_HOME",
            "ANDROID_USER_HOME",
            "NODE_COMPILE_CACHE",
            "KF_WORKSPACE_ROOT",
            "KF_PROJECT_DIR",
            "KF_GRADLE_HELPER",
            "KF_PROOT_TELEMETRY_PATH",
            "KF_PROOT_ACTIVE_REGISTRY_ROOT",
            ProotFileProtectionRuntime.CONTROL_ENV,
            ProotFileProtectionRuntime.OPERATION_ENV,
            "KF_PROCFS_PROJECTION_ROOT",
            "TZ",
            "PATH"
        )
        return env.entries.joinToString(" ") { (key, value) ->
            if (key in quotedKeys) {
                "$key='${shellQuote(value)}'"
            } else {
                "$key=$value"
            }
        }
    }

    private fun buildInlineCommandArgv(argv: List<String>): String {
        return argv.joinToString(" ") { "'${shellQuote(it)}'" }
    }

    private fun createDefaultContainer(layout: AssetExtractor.RuntimeLayout): ContainerRecord {
        val containerRoot = File(layout.containersDir, DEFAULT_CONTAINER_ID)
        val workspaceDir = File(layout.sharedDir, DEFAULT_CONTAINER_ID)
        val profile = layout.profile
        val record = ContainerRecord(
            id = DEFAULT_CONTAINER_ID,
            displayName = "Ubuntu 主容器",
            imageName = profile.imageName,
            baseProfile = profile.codename,
            rootfsPath = File(containerRoot, "rootfs").absolutePath,
            workspacePath = workspaceDir.absolutePath,
            createdAt = System.currentTimeMillis(),
            status = ContainerStatus.CREATED
        )
        Logger.i("ContainerManager", "创建默认容器记录: ${record.id}")
        return record
    }

    private fun ensureContainerFilesystem(
        context: Context,
        layout: AssetExtractor.RuntimeLayout,
        container: ContainerRecord
    ) {
        val filesystem = containerFilesystem(container)
        val containerRootfs = filesystem.rootfsDir
        Logger.i(
            "ContainerManager",
            "检查当前空间文件系统: id=${container.id}, rootfs=${containerRootfs.absolutePath}, bash=${File(containerRootfs, "bin/bash").exists()}"
        )
        if (!isContainerRootfsReady(containerRootfs, layout.profile)) {
            traceStage("cloneBaseImage(${container.id})") {
                cloneBaseImage(layout.baseImageDir, containerRootfs)
            }
        }
        // bootstrap 已依次执行 host groups、包管理目录、网络和时区校准；
        // 不在外层把同一组可变修复重复一遍。
        traceStage("ensureContainerBootstrap(${container.id})") {
            ensureContainerBootstrap(context, layout, containerRootfs, layout.profile)
        }
        traceStage("writeContainerRootfsReady(${container.id})") {
            writeContainerRootfsReadyMarker(containerRootfs, layout.profile)
        }

        traceStage("ensureWorkspace(${container.id})") {
            if (!filesystem.workspaceDir.exists()) {
                filesystem.workspaceDir.mkdirs()
            }
        }
        // installSystemComponents 首步就是 WorkspaceBuildSupport.ensure；保持一次完整校准即可。
        traceStage("ensureWorkspaceSystemComponents(${container.id})") {
            WorkspaceBuildSupport.installSystemComponents(context, filesystem.workspaceDir)
        }
        traceStage("writeDefaultContainerColdReuseReceipt(${container.id})") {
            DefaultContainerColdReuseReceipt.write(
                context = context,
                layout = layout,
                container = container,
                hostTimeZoneId = currentHostTimeZoneId(),
            )
        }
    }

    private fun ensureContainerBootstrap(
        context: Context,
        layout: AssetExtractor.RuntimeLayout,
        rootfsDir: File,
        profile: BaseImageProfile
    ) {
        traceStage("ensurePackageManagerFiles(container-bootstrap)") {
            ensurePackageManagerFiles(rootfsDir, profile)
        }
        traceStage("ensureAndroidHostGroups(container-bootstrap)") {
            ensureAndroidHostGroups(rootfsDir)
        }
        traceStage("writeRuntimeResolvConf(container-bootstrap)") {
            // 统一通过 buildNetworkPlan 写入运行时 resolv.conf，不再直接修改 rootfs
            val pseudoContainer = ContainerRecord(
                id = "container-bootstrap",
                displayName = "container-bootstrap",
                imageName = profile.imageName,
                rootfsPath = rootfsDir.absolutePath,
                workspacePath = layout.tmpDir.absolutePath,
                createdAt = 0L,
                networkMode = NetworkMode.HOST
            )
            val networkPlan = buildNetworkPlan(context, layout, pseudoContainer)
            Logger.i("ContainerManager", "容器 bootstrap resolv.conf 已写入: ${networkPlan.runtimeResolvConfPath}")
        }
        traceStage("ensureContainerTimeZone(container-bootstrap)") {
            ensureContainerTimeZone(rootfsDir)
        }
        val marker = File(rootfsDir, CONTAINER_BOOTSTRAP_MARKER)
        val health = inspectBootstrapHealth(rootfsDir)
        Logger.i(
            "ContainerManager",
            "当前空间 bootstrap 状态: marker=${marker.exists()}, ${health.summary()}"
        )
        if (health.ready) {
            if (!marker.exists()) {
                marker.writeText("ready\n")
            }
            Logger.i("ContainerManager", "当前空间基础工具链已就绪，跳过重型补齐")
            return
        }

        throw incompleteOfflineRootfs("当前空间", health)
    }

    /**
     * 为容器构建网络配置计划。
     *
     * 这是容器网络配置的**唯一真相源**，所有启动路径（launch / exec / shell / bootstrap）
     * 都必须调用此函数获取网络配置，不允许自行计算 DNS 或拼装 resolv.conf。
     *
     * 策略：
     * 1. 只使用 Android 给 Kite 当前默认网络返回的 DNS（去重 + 过滤无效地址）
     * 2. 系统没有可用 DNS 时明确保持无 DNS，不擅自追加公共 DNS 旁路
     * 3. 运行时 resolv.conf 写入 app 私有目录（layout.tmpDir），不再污染 rootfs
     */
    private fun buildNetworkPlan(
        context: Context,
        layout: AssetExtractor.RuntimeLayout,
        container: ContainerRecord
    ): ContainerNetworkPlan {
        AndroidDefaultNetworkAlignment.ensureStarted(context)
        val rawDnsServers = resolveDnsServersFromSystem(context)
        val effectiveDnsServers = ContainerDnsPolicy.normalize(rawDnsServers)
        val runtimeResolvPath = File(layout.tmpDir, "resolv.conf").absolutePath

        // 确保运行时 resolv.conf 已写入 app 私有目录
        writeRuntimeResolvConf(runtimeResolvPath, effectiveDnsServers)

        Logger.i(
            "ContainerManager",
            "容器网络配置: mode=${container.networkMode.label}, dnsSources=${
                effectiveDnsServers.joinToString()
            }, resolvConfRuntime=$runtimeResolvPath"
        )

        return ContainerNetworkPlan(
            dnsServers = effectiveDnsServers,
            runtimeResolvConfPath = runtimeResolvPath,
            networkMode = container.networkMode,
            semantics = container.networkMode.toRuntimeNetworkSemantics(),
            dnsSourceDescription = when {
                rawDnsServers.isEmpty() -> "android-default-network:no-dns"
                effectiveDnsServers.isEmpty() -> "android-default-network:no-usable-dns"
                else -> "android-default-network"
            }
        )
    }

    /**
     * 从 Android 系统获取 DNS 服务器地址列表。
     */
    private fun resolveDnsServersFromSystem(
        context: Context,
        preferredNetwork: Network? = null,
    ): List<String> = RuntimeDnsFilePublisher.resolveFromAndroidDefaultNetwork(context, preferredNetwork)

    /**
     * 将 resolv.conf 写入**运行时目录**（app 私有目录），而非 rootfs。
     * 这样 rootfs 保持干净，多个容器实例共享同一份运行时 resolv.conf。
     */
    @Synchronized
    private fun writeRuntimeResolvConf(runtimeResolvPath: String, dnsServers: List<String>): Boolean {
        return RuntimeDnsFilePublisher.publish(File(runtimeResolvPath), dnsServers)
    }

    /**
     * 默认网络变化时只重写已 bind-mount 的运行时 resolv.conf。
     * 不准备 rootfs、不重建终端，也不读取 VPN 或 provider 状态。
     */
    internal fun refreshAndroidDefaultNetworkResolver(
        context: Context,
        reason: String,
        preferredNetwork: Network? = null,
    ) {
        val appContext = context.applicationContext
        val dnsServers = ContainerDnsPolicy.normalize(
            resolveDnsServersFromSystem(appContext, preferredNetwork)
        )
        val runtimeResolvFile = RuntimeDnsFilePublisher.sharedResolverFile(appContext)
        val changed = writeRuntimeResolvConf(runtimeResolvFile.absolutePath, dnsServers)
        if (changed) {
            Logger.i(
                "ContainerNetwork",
                "Android 默认网络已对齐容器 DNS: reason=$reason dns=${dnsServers.joinToString()}"
            )
        }
    }

    private fun prepareProotActiveRegistryRoot(layout: AssetExtractor.RuntimeLayout): File {
        return File(layout.tmpDir, PROOT_ACTIVE_REGISTRY_DIR_NAME).also { root ->
            runCatching { root.mkdirs() }
                .onFailure { error ->
                    Logger.e(
                        "KFContainerManager",
                        "无法准备 PRoot 活跃注册表目录: ${root.absolutePath}, ${error.message}",
                    )
                }
        }
    }

    /**
     * 启动前刷新容器运行时文件。
     *
     * 仅刷新 DNS（写入运行时 resolv.conf），不再直接修改 rootfs。
     * 刷新结果通过 [buildNetworkPlan] 在下次容器启动时注入。
     */
    private fun refreshContainerRuntimeFiles(context: Context, container: ContainerRecord) {
        val now = SystemClock.elapsedRealtime()
        val shouldSkip = synchronized(runtimeFilesRefreshedAt) {
            val lastRefreshedAt = runtimeFilesRefreshedAt[container.id]
            if (lastRefreshedAt != null && now - lastRefreshedAt < RUNTIME_FILES_REFRESH_THROTTLE_MS) {
                true
            } else {
                runtimeFilesRefreshedAt[container.id] = now
                false
            }
        }
        if (shouldSkip) {
            Logger.i("ContainerManager", "跳过重复的容器运行时文件刷新: ${container.id}")
            return
        }

        val layout = AssetExtractor.getRuntimeLayout(context)

        // T014c：rootfs 系统文件（tmp/var/log 目录、sources.list、nsswitch.conf）只在 Base 封存前
        // 由 ensureContainerBootstrap 写一次；普通启动不再直接改 rootfs。动态网络配置仍走 bind 注入
        // （resolv.conf 写在 app 私有 layout.tmpDir，不污染 rootfs）。
        val networkPlan = buildNetworkPlan(context, layout, container)

        Logger.i(
            "ContainerManager",
            "已刷新容器运行时文件: dns=${networkPlan.dnsServers.joinToString()}, " +
                "resolvConfRuntime=${networkPlan.runtimeResolvConfPath}"
        )
    }

    private fun cloneBaseImage(sourceDir: File, destinationDir: File) {
        Logger.i("ContainerManager", "正在克隆基础镜像到 ${destinationDir.absolutePath}")
        deleteRecursively(destinationDir)
        destinationDir.mkdirs()

        val command = arrayOf(
            "/system/bin/sh",
            "-c",
            "mkdir -p '${shellQuote(destinationDir.absolutePath)}' && " +
                "cp -a '${shellQuote(sourceDir.absolutePath)}/.' '${shellQuote(destinationDir.absolutePath)}/'"
        )

        val process = ProcessBuilder(*command)
            .redirectErrorStream(true)
            .start()

        val output = process.inputStream.bufferedReader().use { it.readText() }
        val exitCode = process.waitFor()
        if (exitCode != 0 || !File(destinationDir, "bin/bash").exists()) {
            Logger.e("ContainerManager", "克隆基础镜像失败，exitCode=$exitCode, output=$output")
            throw IllegalStateException("默认容器创建失败")
        }
    }

    private fun ensureBaseImageBootstrap(
        context: Context,
        layout: AssetExtractor.RuntimeLayout
    ) {
        traceStage("ensurePackageManagerFiles(base-image)") {
            ensurePackageManagerFiles(layout.baseImageDir, layout.profile)
        }
        traceStage("ensureAndroidHostGroups(base-image)") {
            ensureAndroidHostGroups(layout.baseImageDir)
        }
        traceStage("writeRuntimeResolvConf(base-image)") {
            // 基础镜像也统一通过 buildNetworkPlan 写入运行时 resolv.conf，不再直接修改 rootfs
            val networkPlan = buildNetworkPlan(context, layout,
                ContainerRecord(
                    id = "base-image",
                    displayName = "base-image",
                    imageName = layout.profile.imageName,
                    rootfsPath = layout.baseImageDir.absolutePath,
                    workspacePath = layout.tmpDir.absolutePath,
                    createdAt = 0L,
                    networkMode = NetworkMode.HOST
                )
            )
            Logger.i("ContainerManager", "基础镜像 resolv.conf 已写入: ${networkPlan.runtimeResolvConfPath}")
        }
        traceStage("ensureContainerTimeZone(base-image)") {
            ensureContainerTimeZone(layout.baseImageDir)
        }
        val marker = File(layout.baseImageDir, BASE_BOOTSTRAP_MARKER)
        val health = inspectBootstrapHealth(layout.baseImageDir)
        Logger.i(
            "ContainerManager",
            "基础镜像 bootstrap 状态: marker=${marker.exists()}, ${health.summary()}"
        )
        if (health.ready) {
            if (!marker.exists()) {
                marker.writeText("ready\n")
            }
            Logger.i("ContainerManager", "基础镜像基础工具链已就绪，跳过重型补齐")
            return
        }

        throw incompleteOfflineRootfs("基础镜像", health)
    }

    private fun ensurePackageManagerFiles(rootfsDir: File, profile: BaseImageProfile) {
        val tmpDir = File(rootfsDir, "tmp")
        val varTmpDir = File(rootfsDir, "var/tmp")
        val runDir = File(rootfsDir, "run")
        val logDir = File(rootfsDir, "var/log")
        val supervisorLogDir = File(rootfsDir, "var/log/supervisor")
        val supervisorRunDir = File(rootfsDir, "run/supervisor")
        listOf(tmpDir, varTmpDir, runDir, logDir, supervisorLogDir, supervisorRunDir).forEach { directory ->
            if (!directory.exists()) {
                directory.mkdirs()
            }
            directory.setReadable(true, false)
            directory.setWritable(true, false)
            directory.setExecutable(true, false)
        }
        applyHostMode(tmpDir, "1777")
        applyHostMode(varTmpDir, "1777")
        applyHostMode(runDir, "0755")
        applyHostMode(logDir, "0755")
        applyHostMode(supervisorLogDir, "0755")
        applyHostMode(supervisorRunDir, "0755")

        val sourcesList = File(rootfsDir, "etc/apt/sources.list")
        val current = if (sourcesList.exists()) {
            sourcesList.readText()
        } else {
            ""
        }
        if (current.isBlank() || current.contains("ports.ubuntu.com/ubuntu-ports")) {
            sourcesList.parentFile?.mkdirs()
            sourcesList.writeText(profile.aptSources.trimIndent() + "\n")
        }

        val nsswitchConf = File(rootfsDir, "etc/nsswitch.conf")
        if (!nsswitchConf.exists()) {
            nsswitchConf.parentFile?.mkdirs()
            nsswitchConf.writeText(
                """
                passwd:         files
                group:          files
                shadow:         files
                hosts:          files dns
                networks:       files
                protocols:      db files
                services:       db files
                ethers:         db files
                rpc:            db files
                """.trimIndent() + "\n"
            )
        }
    }

    private fun ensureContainerTimeZone(rootfsDir: File) {
        runCatching {
            val requestedTimeZoneId = currentHostTimeZoneId()
            val requestedZoneFile = File(rootfsDir, "usr/share/zoneinfo/$requestedTimeZoneId")
            val fallbackZoneFile = File(rootfsDir, "usr/share/zoneinfo/$DEFAULT_TIME_ZONE_ID")
            val effectiveTimeZoneId = if (requestedZoneFile.isFile) requestedTimeZoneId else DEFAULT_TIME_ZONE_ID
            val sourceZoneFile = if (requestedZoneFile.isFile) requestedZoneFile else fallbackZoneFile
            val etcDir = File(rootfsDir, "etc")
            val localtimeFile = File(etcDir, "localtime")

            etcDir.mkdirs()
            File(etcDir, "timezone").writeText("$effectiveTimeZoneId\n")
            Files.deleteIfExists(localtimeFile.toPath())

            if (sourceZoneFile.isFile) {
                runCatching {
                    Files.createSymbolicLink(
                        localtimeFile.toPath(),
                        Paths.get("/usr/share/zoneinfo/$effectiveTimeZoneId")
                    )
                }.onFailure {
                    sourceZoneFile.copyTo(localtimeFile, overwrite = true)
                }
            }

            if (effectiveTimeZoneId != requestedTimeZoneId) {
                Logger.e(
                    "ContainerManager",
                    "Host timezone $requestedTimeZoneId is not available in container zoneinfo, fallback=$effectiveTimeZoneId"
                )
            } else {
                Logger.i("ContainerManager", "Container timezone synced to host: $effectiveTimeZoneId")
            }
        }.onFailure { error ->
            Logger.e("ContainerManager", "Failed to sync container timezone: ${error.message}")
        }
    }

    internal fun currentHostTimeZoneId(): String {
        val candidate = TimeZone.getDefault().id.trim()
        val valid = candidate.isNotBlank() &&
            !candidate.startsWith("/") &&
            !candidate.contains("..") &&
            candidate.matches(Regex("[A-Za-z0-9_+./-]+"))
        return if (valid) candidate else DEFAULT_TIME_ZONE_ID
    }

    private fun ensureAndroidHostGroups(rootfsDir: File) {
        runCatching {
            val groupFile = File(rootfsDir, "etc/group")
            groupFile.parentFile?.mkdirs()
            if (!groupFile.exists()) {
                groupFile.writeText("root:x:0:\n")
            }

            val current = groupFile.readLines()
            val existingNames = current
                .mapNotNull { it.substringBefore(':').trim().takeIf(String::isNotBlank) }
                .toSet()
            val existingGids = current
                .mapNotNull { line -> line.split(':').getOrNull(2)?.trim()?.toIntOrNull() }
                .toSet()
            val hostGids = readCurrentProcessGroups()
                .filter { it > 0 }
                .sorted()

            val missingEntries = hostGids.mapNotNull { gid ->
                if (gid in existingGids) {
                    return@mapNotNull null
                }
                val baseName = ANDROID_HOST_GROUP_NAMES[gid] ?: "android_gid_$gid"
                val name = uniqueGroupName(baseName, existingNames)
                "$name:x:$gid:"
            }

            if (missingEntries.isEmpty()) {
                return@runCatching
            }

            val separator = if (groupFile.readText().endsWith("\n")) "" else "\n"
            groupFile.appendText(separator + missingEntries.joinToString(separator = "\n", postfix = "\n"))
            Logger.i(
                "ContainerManager",
                "Ensured Android host group mappings in ${groupFile.absolutePath}: ${
                    missingEntries.joinToString { it.substringBefore(':') }
                }"
            )
        }.onFailure { error ->
            Logger.e("ContainerManager", "Failed to ensure Android host group mappings: ${error.message}")
        }
    }

    private fun uniqueGroupName(baseName: String, existingNames: Set<String>): String {
        if (baseName !in existingNames) {
            return baseName
        }
        var suffix = 2
        while ("${baseName}_$suffix" in existingNames) {
            suffix++
        }
        return "${baseName}_$suffix"
    }

    private fun readCurrentProcessGroups(): Set<Int> {
        return runCatching {
            File("/proc/self/status")
                .readLines()
                .firstOrNull { it.startsWith("Groups:") }
                ?.removePrefix("Groups:")
                ?.trim()
                ?.split(Regex("\\s+"))
                ?.mapNotNull { it.toIntOrNull() }
                ?.toSet()
                .orEmpty()
        }.getOrDefault(emptySet())
    }

    private fun hasBootstrapPackages(rootfsDir: File): Boolean {
        return BOOTSTRAP_REQUIRED_FILES.all { relativePath ->
            File(rootfsDir, relativePath).exists()
        }
    }

    private fun inspectBootstrapHealth(rootfsDir: File): BootstrapHealth {
        val missingFiles = BOOTSTRAP_REQUIRED_FILES.filterNot { relativePath ->
            File(rootfsDir, relativePath).exists()
        }
        return BootstrapHealth(
            hasPackages = missingFiles.isEmpty(),
            hasDpkgPreconfigure = File(rootfsDir, "usr/sbin/dpkg-preconfigure").exists(),
            missingFiles = missingFiles
        )
    }

    private fun isPackageManagerHealthy(rootfsDir: File): Boolean {
        return inspectBootstrapHealth(rootfsDir).ready
    }

    private fun incompleteOfflineRootfs(scope: String, health: BootstrapHealth): IllegalStateException {
        val missing = health.missingFiles.joinToString(limit = 12)
        val message = "$scope 定制 rootfs 缺少基础工具: $missing"
        Logger.e("ContainerManager", "$message; ${health.summary()}")
        RuntimeBootstrapProgress.failed(message)
        return IllegalStateException(message)
    }

    private fun hasSyntheticHostLinks(rootfsDir: File): Boolean {
        val rootPrefix = rootfsDir.absolutePath
        return rootfsDir.walkTopDown().any { entry ->
            val path = entry.toPath()
            Files.isSymbolicLink(path) && runCatching {
                val target = Files.readSymbolicLink(path).toString()
                target.contains(".l2s.") && target.startsWith(rootPrefix)
            }.getOrDefault(false)
        }
    }

    private fun normalizeSyntheticHostLinks(rootfsDir: File) {
        val rootPrefix = rootfsDir.absolutePath
        var normalizedCount = 0

        rootfsDir.walkTopDown().forEach { entry ->
            val entryPath = entry.toPath()
            if (!Files.isSymbolicLink(entryPath)) {
                return@forEach
            }

            val rawTarget = runCatching {
                Files.readSymbolicLink(entryPath).toString()
            }.getOrNull() ?: return@forEach

            if (!rawTarget.contains(".l2s.") || !rawTarget.startsWith(rootPrefix)) {
                return@forEach
            }

            val resolvedTarget = resolveSyntheticLinkTarget(rootfsDir, entry) ?: return@forEach
            if (!resolvedTarget.exists() || !resolvedTarget.isFile) {
                return@forEach
            }

            val bytes = runCatching { resolvedTarget.readBytes() }.getOrNull() ?: return@forEach
            Files.delete(entryPath)
            entry.writeBytes(bytes)
            entry.setReadable(true, false)
            entry.setWritable(true, false)
            entry.setExecutable(resolvedTarget.canExecute(), false)
            normalizedCount += 1
        }

        if (normalizedCount > 0) {
            Logger.i("ContainerManager", "已归正基础链接: $normalizedCount")
        }
    }

    private fun resolveSyntheticLinkTarget(rootfsDir: File, linkFile: File): File? {
        var current = linkFile.toPath()
        repeat(12) {
            if (!Files.isSymbolicLink(current)) {
                return current.toFile()
            }

            val rawTarget = Files.readSymbolicLink(current).toString()
            current = when {
                rawTarget.startsWith(rootfsDir.absolutePath) -> File(rawTarget).toPath()
                rawTarget.startsWith("/") -> File(rootfsDir, rawTarget.removePrefix("/")).toPath()
                else -> current.parent.resolve(rawTarget).normalize()
            }
        }
        return null
    }

    private fun isDefaultContainerRecordCurrent(
        container: ContainerRecord,
        layout: AssetExtractor.RuntimeLayout
    ): Boolean {
        val expectedRootfs = File(File(layout.containersDir, DEFAULT_CONTAINER_ID), "rootfs").absolutePath
        val expectedWorkspace = File(layout.sharedDir, DEFAULT_CONTAINER_ID).absolutePath
        return container.id == DEFAULT_CONTAINER_ID &&
            container.imageName == layout.profile.imageName &&
            container.baseProfile == layout.profile.codename &&
            File(container.rootfsPath).absolutePath == expectedRootfs &&
            File(container.workspacePath).absolutePath == expectedWorkspace
    }

    private fun isContainerRootfsReady(rootfsDir: File, profile: BaseImageProfile): Boolean {
        if (missingContainerRootfsFiles(rootfsDir).isNotEmpty()) return false
        val marker = File(rootfsDir, CONTAINER_ROOTFS_READY_MARKER)
        if (!marker.exists()) return false
        val lines = runCatching { marker.readLines().toSet() }.getOrDefault(emptySet())
        return containerRootfsReadyMarkerLines(profile).all { it in lines }
    }

    private fun missingContainerRootfsFiles(rootfsDir: File): List<String> =
        CONTAINER_ROOTFS_REQUIRED_FILES.filterNot { relativePath -> File(rootfsDir, relativePath).exists() }

    private fun writeContainerRootfsReadyMarker(rootfsDir: File, profile: BaseImageProfile) {
        File(rootfsDir, CONTAINER_ROOTFS_READY_MARKER)
            .writeText(containerRootfsReadyMarkerLines(profile).joinToString(separator = "\n", postfix = "\n"))
    }

    private fun containerRootfsReadyMarkerLines(profile: BaseImageProfile): List<String> =
        listOf(
            "schema=$CONTAINER_ROOTFS_READY_MARKER_SCHEMA",
            "profile=${profile.codename}",
            "versionId=${profile.versionId}",
            "imageName=${profile.imageName}",
            "imageDirName=${profile.imageDirName}",
            "requiredFiles=${CONTAINER_ROOTFS_REQUIRED_FILES.joinToString(",")}"
        )

    private fun deleteContainerRootfsIfSafe(
        container: ContainerRecord,
        layout: AssetExtractor.RuntimeLayout
    ) {
        val rootfsDir = File(container.rootfsPath).absoluteFile
        val containersDir = layout.containersDir.absoluteFile
        val insideRuntimeContainers = generateSequence(rootfsDir) { it.parentFile }
            .any { it == containersDir }
        if (!insideRuntimeContainers) {
            Logger.e(
                "ContainerManager",
                "跳过默认容器清理，rootfs 不在当前 runtime 容器目录内: ${rootfsDir.absolutePath}"
            )
            return
        }
        deleteRecursively(rootfsDir)
    }

    /**
     * 为 bootstrap 场景构建完整的 proot argv。
     * 所有参数统一从 networkPlan 读取，不允许硬编码。
     */
    private fun buildBootstrapProotArgv(
        layout: AssetExtractor.RuntimeLayout,
        rootfsDir: File,
        networkPlan: ContainerNetworkPlan
    ): List<String> {
        return buildBootstrapProotLaunchPlan(
            layout = layout,
            rootfsDir = rootfsDir,
            networkPlan = networkPlan
        ).baseArgv()
    }

    private fun buildBootstrapProotLaunchPlan(
        layout: AssetExtractor.RuntimeLayout,
        rootfsDir: File,
        networkPlan: ContainerNetworkPlan
    ): ProotLaunchPlan {
        val prootRuntime = readProotRuntimeDescriptor(layout)
        return ProotLaunchPlan(
            kind = ProotLaunchPlanKind.BOOTSTRAP,
            executablePath = layout.prootFile.absolutePath,
            rootfsPath = rootfsDir.absolutePath,
            workingDirectory = "/root",
            flags = listOf("--link2symlink", "-0"),
            lane = ProotLaunchLane.BOOTSTRAP,
            purpose = "bootstrap_package_install",
            bindMounts = buildCommonProotBindMounts() + ProotBindMount(
                sourcePath = networkPlan.runtimeResolvConfPath,
                targetPath = networkPlan.containerResolvConfTarget,
                role = "runtime_resolv_conf",
                writable = false
            ),
            networkMode = networkPlan.networkMode,
            networkSemantics = networkPlan.semantics,
            includeNetworkModeFlag = false,
            tmpDirPath = layout.tmpDir.absolutePath,
            loaderMode = prootRuntime.optString("loaderMode").ifBlank { "external" },
            loaderPath = layout.prootLoaderFile.absolutePath,
            loader32Path = layout.prootLoader32File.absolutePath,
            prootRuntime = prootRuntime,
            telemetryMode = prootRuntime.optString("telemetryMode")
                .ifBlank { ProotLaunchPlan.TELEMETRY_NONE_CURRENT }
        )
    }

    /**
     * bootstrap 专用的基础环境变量（不含网络配置，网络通过 buildNetworkPlan 统一注入）。
     */
    private fun buildBootstrapEnvironment(layout: AssetExtractor.RuntimeLayout): Map<String, String> {
        val env = linkedMapOf(
            "HOME" to "/root",
            "USER" to "root",
            "TERM" to "xterm-256color",
            "LANG" to "C.UTF-8",
            "TZ" to currentHostTimeZoneId(),
            "LD_LIBRARY_PATH" to layout.prootLibDir.absolutePath,
            "PROOT_TMP_DIR" to layout.tmpDir.absolutePath,
            "TMPDIR" to "/tmp",
            "UV_LINK_MODE" to "copy",
            "PATH" to "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"
        )
        addProotLoaderEnvironmentIfNeeded(layout, env)
        return env
    }

    private fun updateContainer(
        context: Context,
        transform: (ContainerRecord) -> ContainerRecord
    ) {
        val layout = AssetExtractor.getRuntimeLayout(context)
        val registry = loadRegistry(layout.registryFile)
        val current = registry.firstOrNull { it.id == DEFAULT_CONTAINER_ID }
            ?: getSavedContainer(context)
            ?: createDefaultContainer(layout)

        val updated = transform(current)
        val updatedRegistry = registry
            .filterNot { it.id == updated.id }
            .toMutableList()
            .apply { add(updated) }

        saveRegistry(layout.registryFile, updatedRegistry)
        _containerState.value = updated
    }

    private fun loadRegistry(registryFile: File): List<ContainerRecord> {
        if (!registryFile.exists()) {
            return emptyList()
        }

        return runCatching {
            val raw = registryFile.readText()
            if (raw.isBlank()) {
                emptyList()
            } else {
                val array = JSONArray(raw)
                buildList {
                    for (index in 0 until array.length()) {
                        add(ContainerRecord.fromJson(array.getJSONObject(index)))
                    }
                }
            }
        }.getOrElse { error ->
            Logger.e("ContainerManager", "读取容器注册表失败: ${error.message}")
            emptyList()
        }
    }

    private fun saveRegistry(registryFile: File, containers: List<ContainerRecord>) {
        val array = JSONArray()
        containers
            .sortedBy { it.createdAt }
            .forEach { array.put(it.toJson()) }

        registryFile.parentFile?.mkdirs()
        registryFile.writeText(array.toString(2))
    }

    private fun deleteRecursively(target: File?) {
        if (target == null || !target.exists()) {
            return
        }

        target.walkBottomUp().forEach { entry ->
            if (!entry.delete()) {
                Logger.d("ContainerManager", "跳过无法删除的路径: ${entry.absolutePath}")
            }
        }
    }

    private fun shellQuote(path: String): String {
        return path.replace("'", "'\"'\"'")
    }

    private fun applyHostMode(target: File, mode: String) {
        runCatching {
            Os.chmod(target.absolutePath, mode.toInt(radix = 8))
        }.onFailure { error ->
            Logger.d("ContainerManager", "chmod $mode 失败: ${target.absolutePath}, ${error.message}")
        }
    }

    private fun resolveCommandHostFile(
        container: ContainerRecord,
        pathEntry: String,
        commandName: String
    ): File? {
        if (pathEntry.isBlank()) {
            return null
        }

        if (pathEntry.startsWith(RuntimeBoundary.CONTAINER_WORKSPACE_PATH)) {
            val relative = pathEntry.removePrefix(RuntimeBoundary.CONTAINER_WORKSPACE_PATH).trimStart('/')
            return if (relative.isBlank()) {
                File(container.workspacePath, commandName)
            } else {
                File(File(container.workspacePath, relative), commandName)
            }
        }

        if (pathEntry.startsWith("/")) {
            return File(
                File(container.rootfsPath, pathEntry.removePrefix("/")),
                commandName
            )
        }

        return null
    }

    private fun containerFilesystem(container: ContainerRecord): ContainerFilesystemView {
        return ContainerFilesystemView(
            rootfsDir = File(container.rootfsPath),
            workspaceDir = File(container.workspacePath)
        )
    }

    /**
     * 为容器 shell 动态拼接 PATH。
     *
     * 这样用户通过工作区安装的 Node、npm 全局包和 Claude Code，
     * 可以在 App 前端终端里直接使用，不用每次手动 export PATH。
     */
    private fun buildShellPath(container: ContainerRecord): String {
        val workspaceDir = containerFilesystem(container).workspaceDir
        // T014g：buildShellPath 不再隐式调用 ensure（ensure 会写共享目录，但不应由 PATH 构造触发）。
        // 共享目录准备由 prepareBuildContext 的 ensureWorkspaceSystemComponents 阶段负责。
        val extraPaths = mutableListOf<String>()

        // 环境命令优先：.kf/bin（环境变化层，用户/资源安装）排在共享目录之前，
        // 避免共享 wrapper 错误覆盖用户安装的同名命令。
        // 受管命令目录即使尚未创建也必须进入 PATH；资源稍后安装后，既有终端即可直接发现命令。
        extraPaths += WorkspaceBuildSupport.CONTAINER_HELPER_BIN_PATH

        if (WorkspaceBuildSupport.helperSystemBinDir(workspaceDir).exists()) {
            extraPaths += WorkspaceBuildSupport.CONTAINER_HELPER_SYSTEM_BIN_PATH
        }

        if (WorkspaceBuildSupport.helperSystemWrappersDir(workspaceDir).exists()) {
            extraPaths += WorkspaceBuildSupport.CONTAINER_HELPER_SYSTEM_WRAPPERS_PATH
        }

        extraPaths += "/root/.local/bin"

        resolveLatestNodeToolchainBin(workspaceDir)?.let { extraPaths += it }

        if (File(workspaceDir, "npm-global/bin").exists()) {
            extraPaths += "${WorkspaceBuildSupport.CONTAINER_WORKSPACE_ROOT}/npm-global/bin"
        }

        extraPaths += listOf(
            "/usr/local/sbin",
            "/usr/local/bin",
            "/usr/sbin",
            "/usr/bin",
            "/sbin",
            "/bin",
            "/system/bin"
        )

        return extraPaths.joinToString(":")
    }

    private fun resolveLatestNodeToolchainBin(workspaceDir: File): String? {
        val candidates = buildList {
            val toolchainRoot = WorkspaceBuildSupport.helperToolchainDir(workspaceDir)
            toolchainRoot.listFiles()
                ?.filter { candidate ->
                    candidate.isDirectory &&
                        candidate.name.startsWith("node-v") &&
                        File(candidate, "bin/node").exists()
                }
                ?.sortedByDescending { it.name }
                ?.firstOrNull()
                ?.let { add("${WorkspaceBuildSupport.CONTAINER_HELPER_TOOLCHAIN_PATH}/${it.name}/bin") }

            workspaceDir.listFiles()
                ?.filter { candidate ->
                    candidate.isDirectory &&
                        candidate.name.startsWith("node-v") &&
                        File(candidate, "bin/node").exists()
                }
                ?.sortedByDescending { it.name }
                ?.firstOrNull()
                ?.let { add("${WorkspaceBuildSupport.CONTAINER_WORKSPACE_ROOT}/${it.name}/bin") }
        }

        return candidates.firstOrNull()
    }
}
