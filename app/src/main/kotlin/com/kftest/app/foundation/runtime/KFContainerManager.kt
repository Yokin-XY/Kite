package com.kftest.app.foundation.runtime

import android.content.Context
import android.content.Context.CONNECTIVITY_SERVICE
import android.net.ConnectivityManager
import android.os.SystemClock
import com.kftest.app.foundation.logging.Logger
import com.kftest.app.foundation.workspace.WorkspaceBuildSupport
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.util.LinkedHashMap
import java.util.TimeZone

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
 */
object KFContainerManager {
    private const val PROOT_TELEMETRY_MODE = "debug_jsonl_lifecycle_v0"
    private const val PROOT_TELEMETRY_FILE_NAME = "kf-proot-telemetry.jsonl"

    /**
     * 容器网络配置的唯一真相源。
     *
     * 每次容器启动前由 [buildNetworkPlan] 构造，包含了：
     * - 当前生效的 DNS 服务器列表（已去重、已过滤明显无效地址）
     * - 运行时 resolv.conf 的写入路径（app 私有目录，非 rootfs）
     * - 网络模式标识
     * - 诊断用元数据
     *
     * 后续扩展字段（proxy / dns-over-https / 连接诊断）都应加在此处。
     */
    data class ContainerNetworkPlan(
        /** 当前生效的 DNS 服务器列表（IPv4） */
        val dnsServers: List<String>,
        /** 运行时 resolv.conf 的写入路径（app 私有目录） */
        val runtimeResolvConfPath: String,
        /** 容器内 resolv.conf 的挂载路径（固定为 /etc/resolv.conf） */
        val containerResolvConfTarget: String = "/etc/resolv.conf",
        /** 网络模式 */
        val networkMode: NetworkMode,
        /** 调试用：此次 DNS 来源描述 */
        val dnsSourceDescription: String,
        /** 预留扩展：HTTP 代理（暂为空 map） */
        val httpProxy: Map<String, String> = emptyMap(),
        /** 预留扩展：HTTPS 代理（暂为空 map） */
        val httpsProxy: Map<String, String> = emptyMap(),
        val semantics: RuntimeNetworkSemantics,
    ) {
        /** proot argv 中用于 bind-mount resolv.conf 的参数片段 */
        val resolvConfBindArgv: List<String>
            get() = listOf("-b", "$runtimeResolvConfPath:$containerResolvConfTarget")
    }

    private data class BuildPreparation(
        val layout: AssetExtractor.RuntimeLayout,
        val container: ContainerRecord,
        val exchangeDir: File,
        val shellPath: String,
        val networkPlan: ContainerNetworkPlan
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
            get() = hasPackages && hasDpkgPreconfigure

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
        "bin/bash",
        "usr/bin/env",
        "usr/bin/find",
        "usr/bin/grep",
        "usr/bin/sed",
        "usr/bin/git",
        "usr/bin/curl",
        "usr/bin/wget",
        "usr/bin/xz",
        "usr/bin/file",
        "usr/bin/unzip",
        "usr/bin/make",
        "usr/bin/gcc",
        "usr/bin/dig",
        "usr/bin/netstat",
        "usr/sbin/ip",
        "usr/bin/ps",
        "usr/bin/supervisord",
        "usr/bin/supervisorctl",
        "etc/ssl/certs/ca-certificates.crt"
    )

    private val _containerState = MutableStateFlow<ContainerRecord?>(null)
    val containerState: StateFlow<ContainerRecord?> = _containerState
    private val runtimeFilesRefreshedAt = LinkedHashMap<String, Long>()

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
        val layout = traceStage("ensureBaseImageReady(default-container)") {
            ensureBaseImageReady(context)
        }
        val registry = traceStage("loadRegistry(default-container)") {
            loadRegistry(layout.registryFile)
        }
        val existing = registry.firstOrNull { it.id == DEFAULT_CONTAINER_ID }
        val container = existing ?: createDefaultContainer(layout)

        traceStage("ensureContainerFilesystem(${container.id})") {
            ensureContainerFilesystem(context, layout, container)
        }

        val updatedRegistry = registry
            .filterNot { it.id == container.id }
            .toMutableList()
            .apply { add(container) }

        traceStage("saveRegistry(default-container)") {
            saveRegistry(layout.registryFile, updatedRegistry)
        }
        _containerState.value = container
        return container
    }

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

    @Synchronized
    fun buildLaunchConfig(context: Context): ContainerLaunchConfig {
        val prepared = prepareBuildContext(
            context = context,
            caller = "buildLaunchConfig",
            refreshRuntimeFiles = true
        )

        val args = buildContainerProotBaseArgv(
            layout = prepared.layout,
            container = prepared.container,
            exchangeDir = prepared.exchangeDir,
            workingDirectory = RuntimeBoundary.CONTAINER_ROOT_HOME,
            networkPlan = prepared.networkPlan,
            lane = ProotLaunchLane.INTERACTIVE,
            purpose = "terminal_login_shell"
        ).apply {
            add("/bin/bash")
            add("--login")
        }.toTypedArray()

        val env = buildContainerEnvironment(
            context = context,
            layout = prepared.layout,
            container = prepared.container,
            shellPath = prepared.shellPath
        )
            .map { (key, value) -> "$key=$value" }
            .toTypedArray()

        return ContainerLaunchConfig(
            container = prepared.container,
            executablePath = prepared.layout.prootFile.absolutePath,
            workingDirectory = prepared.container.workspacePath,
            args = args,
            env = env
        )
    }

    @Synchronized
    fun buildContainerShellCommand(
        context: Context,
        workingDirectory: String = RuntimeBoundary.CONTAINER_WORKSPACE_PATH,
        payload: String,
        loginShell: Boolean = true
    ): Array<String> {
        val prepared = prepareBuildContext(
            context = context,
            caller = "buildContainerShellCommand",
            refreshRuntimeFiles = true
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
            exchangeDir = prepared.exchangeDir,
            workingDirectory = safeWorkingDirectory,
            networkPlan = prepared.networkPlan,
            lane = ProotLaunchLane.EXEC,
            purpose = "container_shell_command"
        ).apply {
            addAll(innerArgv)
        }

        return arrayOf(
            "/system/bin/sh",
            "-c",
            buildInlineShellEnvironment(
                buildContainerEnvironment(
                    context = context,
                    layout = prepared.layout,
                    container = prepared.container,
                    shellPath = prepared.shellPath
                )
            ) + " " +
                buildInlineCommandArgv(commandArgv)
        )
    }

    @Synchronized
    fun buildContainerExecConfig(
        context: Context,
        workingDirectory: String = RuntimeBoundary.CONTAINER_WORKSPACE_PATH,
        payload: String,
        loginShell: Boolean = true
    ): ContainerExecConfig {
        val prepared = prepareBuildContext(
            context = context,
            caller = "buildContainerExecConfig"
        )
        val safeWorkingDirectory = workingDirectory.trim()
            .ifBlank { RuntimeBoundary.CONTAINER_WORKSPACE_PATH }

        val command = buildContainerProotBaseArgv(
            layout = prepared.layout,
            container = prepared.container,
            exchangeDir = prepared.exchangeDir,
            workingDirectory = safeWorkingDirectory,
            networkPlan = prepared.networkPlan,
            lane = ProotLaunchLane.EXEC,
            purpose = "container_exec_config"
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
            shellPath = prepared.shellPath
        )

        return ContainerExecConfig(
            container = prepared.container,
            workingDirectory = safeWorkingDirectory,
            command = command,
            env = env
        )
    }

    @Synchronized
    fun buildContainerArgvExecConfig(
        context: Context,
        workingDirectory: String = RuntimeBoundary.CONTAINER_WORKSPACE_PATH,
        argv: List<String>
    ): ContainerExecConfig {
        val prepared = prepareBuildContext(
            context = context,
            caller = "buildContainerArgvExecConfig"
        )
        val safeWorkingDirectory = workingDirectory.trim()
            .ifBlank { RuntimeBoundary.CONTAINER_WORKSPACE_PATH }

        val command = buildContainerProotBaseArgv(
            layout = prepared.layout,
            container = prepared.container,
            exchangeDir = prepared.exchangeDir,
            workingDirectory = safeWorkingDirectory,
            networkPlan = prepared.networkPlan,
            lane = ProotLaunchLane.EXEC,
            purpose = "container_argv_exec_config"
        ).apply {
            addAll(argv)
        }

        val env = buildContainerEnvironment(
            context = context,
            layout = prepared.layout,
            container = prepared.container,
            shellPath = prepared.shellPath
        )

        return ContainerExecConfig(
            container = prepared.container,
            workingDirectory = safeWorkingDirectory,
            command = command,
            env = env
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
    @Synchronized
    fun resetDefaultContainer(context: Context, wipeWorkspace: Boolean = false): ContainerRecord {
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
        return container
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
        val container = resolveLaunchContainer(context)
        return buildShellPath(container)
            .split(':')
            .mapNotNull { pathEntry -> resolveCommandHostFile(container, pathEntry, commandName) }
            .any { candidate -> candidate.exists() && candidate.isFile }
    }

    private fun resolveLaunchContainer(context: Context): ContainerRecord {
        val saved = getSavedContainer(context)
        if (saved != null && File(saved.rootfsPath).exists()) {
            return saved
        }
        return ensureDefaultContainer(context)
    }

    private fun prepareBuildContext(
        context: Context,
        caller: String,
        refreshRuntimeFiles: Boolean = false
    ): BuildPreparation {
        val layout = traceStage("prepareRuntime($caller)") {
            AssetExtractor.prepareRuntime(context)
        }
        val container = traceStage("resolveLaunchContainer($caller)") {
            resolveLaunchContainer(context)
        }
        if (refreshRuntimeFiles) {
            traceStage("refreshContainerRuntimeFiles($caller)") {
                refreshContainerRuntimeFiles(context, container)
            }
        }
        val exchangeDir = traceStage("ensureExternalExchange($caller)") {
            ExternalExchangeManager.ensureExchangeDir(context)
        }
        traceStage("ensureWorkspaceSystemComponents($caller)") {
            WorkspaceBuildSupport.installSystemComponents(context, containerFilesystem(container).workspaceDir)
        }
        val shellPath = traceStage("buildShellPath($caller)") {
            buildShellPath(container)
        }
        val networkPlan = traceStage("buildNetworkPlan($caller)") {
            buildNetworkPlan(context, layout, container)
        }
        return BuildPreparation(
            layout = layout,
            container = container,
            exchangeDir = exchangeDir,
            shellPath = shellPath,
            networkPlan = networkPlan
        )
    }

    private fun buildContainerEnvironment(
        context: Context,
        layout: AssetExtractor.RuntimeLayout,
        container: ContainerRecord,
        shellPath: String
    ): LinkedHashMap<String, String> {
        return buildBaseContainerEnvironment(layout).apply {
            // 暂留桥接：启动入口要直接拿到标准工作面 env，等独立 launcher 落地后再完全迁出建房层。
            putAll(WorkspaceBuildSupport.buildWorkSurfaceEnvironment())
            putAll(AdbBridgeContract.buildEnvironment(ShizukuBridgeStatus.snapshot(context)))
            put(
                "KF_PROCFS_PROJECTION_ROOT",
                WorkspaceBuildSupport.runtimeProcProjectionDir(File(container.workspacePath)).absolutePath
            )
            put("UV_LINK_MODE", "copy")
            put("PATH", shellPath)
        }
    }

    private fun buildBaseContainerEnvironment(
        layout: AssetExtractor.RuntimeLayout
    ): LinkedHashMap<String, String> {
        val prootTelemetryFile = prepareProotTelemetryFile(layout)
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
            "KF_PROOT_TELEMETRY_PATH" to prootTelemetryFile.absolutePath
        )
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
        exchangeDir: File,
        workingDirectory: String,
        networkPlan: ContainerNetworkPlan,
        lane: ProotLaunchLane,
        purpose: String
    ): MutableList<String> {
        val plan = buildContainerProotLaunchPlan(
            layout = layout,
            container = container,
            exchangeDir = exchangeDir,
            workingDirectory = workingDirectory,
            networkPlan = networkPlan,
            lane = lane,
            purpose = purpose
        )
        publishProotLaunchContract(container, plan)
        return plan.baseArgv()
    }

    private fun buildContainerProotLaunchPlan(
        layout: AssetExtractor.RuntimeLayout,
        container: ContainerRecord,
        exchangeDir: File,
        workingDirectory: String,
        networkPlan: ContainerNetworkPlan,
        lane: ProotLaunchLane,
        purpose: String
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
                buildContainerBindMounts(container, exchangeDir) +
                ProotBindMount(
                    sourcePath = networkPlan.runtimeResolvConfPath,
                    targetPath = networkPlan.containerResolvConfTarget,
                    role = "runtime_resolv_conf",
                    writable = false
                ),
            networkMode = container.networkMode,
            networkSemantics = networkPlan.semantics,
            includeNetworkModeFlag = true,
            tmpDirPath = layout.tmpDir.absolutePath,
            loaderMode = prootRuntime.optString("loaderMode").ifBlank { "external" },
            loaderPath = layout.prootLoaderFile.absolutePath,
            loader32Path = layout.prootLoader32File.absolutePath,
            prootRuntime = prootRuntime,
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
        container: ContainerRecord,
        exchangeDir: File
    ): List<ProotBindMount> {
        return listOf(
            ProotBindMount(
                sourcePath = container.workspacePath,
                targetPath = RuntimeBoundary.CONTAINER_WORKSPACE_PATH,
                role = "workspace"
            ),
            ProotBindMount(
                sourcePath = exchangeDir.absolutePath,
                targetPath = RuntimeBoundary.CONTAINER_EXCHANGE_PATH,
                role = "exchange"
            ),
            ProotBindMount(
                sourcePath = exchangeDir.absolutePath,
                targetPath = ExternalExchangeManager.CONTAINER_DELIVERY_PATH,
                role = "delivery_alias"
            ),
            ProotBindMount(
                sourcePath = ExternalExchangeManager.ensureAlbumDir().absolutePath,
                targetPath = ExternalExchangeManager.CONTAINER_ALBUM_PATH,
                role = "album_exchange"
            )
        )
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
            "KF_WORKSPACE_ROOT",
            "KF_PROJECT_DIR",
            "KF_GRADLE_HELPER",
            "KF_PROOT_TELEMETRY_PATH",
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
        if (!File(containerRootfs, "bin/bash").exists()) {
            traceStage("cloneBaseImage(${container.id})") {
                cloneBaseImage(layout.baseImageDir, containerRootfs)
            }
        }
        traceStage("ensureAndroidHostGroups(${container.id})") {
            ensureAndroidHostGroups(containerRootfs)
        }
        traceStage("ensurePackageManagerFiles(${container.id})") {
            ensurePackageManagerFiles(containerRootfs, layout.profile)
        }
        traceStage("ensureContainerTimeZone(${container.id})") {
            ensureContainerTimeZone(containerRootfs)
        }
        traceStage("ensureContainerBootstrap(${container.id})") {
            ensureContainerBootstrap(context, layout, containerRootfs, layout.profile)
        }

        traceStage("ensureWorkspace(${container.id})") {
            if (!filesystem.workspaceDir.exists()) {
                filesystem.workspaceDir.mkdirs()
            }
        }
        traceStage("ensureWorkspaceBuildSupport(${container.id})") {
            WorkspaceBuildSupport.ensure(filesystem.workspaceDir)
        }
        traceStage("ensureWorkspaceSystemComponents(${container.id})") {
            WorkspaceBuildSupport.installSystemComponents(context, filesystem.workspaceDir)
        }
        traceStage("ensureExternalExchange(${container.id})") {
            ExternalExchangeManager.ensureExchangeDir(context)
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
        if (marker.exists() && health.ready) {
            Logger.i("ContainerManager", "当前空间基础工具链已就绪，跳过重型补齐")
            return
        }

        Logger.i("ContainerManager", "开始为当前空间补齐基础工具链")
        runCatching {
            traceStage("installBootstrapPackages(container-bootstrap)") {
                installBootstrapPackages(context, layout, rootfsDir)
            }
            traceStage("normalizeSyntheticHostLinks(container-bootstrap)") {
                normalizeSyntheticHostLinks(rootfsDir)
            }
            val refreshedHealth = inspectBootstrapHealth(rootfsDir)
            Logger.i(
                "ContainerManager",
                "当前空间 bootstrap 补齐后状态: ${refreshedHealth.summary()}"
            )
            if (refreshedHealth.ready) {
                marker.writeText("ready\n")
                Logger.i("ContainerManager", "当前空间基础工具链已就绪")
            } else {
                Logger.e("ContainerManager", "当前空间补齐完成后仍缺少关键工具")
            }
        }.onFailure { error ->
            Logger.e("ContainerManager", "当前空间工具链补齐失败: ${error.message}")
        }
    }

    /**
     * 为容器构建网络配置计划。
     *
     * 这是容器网络配置的**唯一真相源**，所有启动路径（launch / exec / shell / bootstrap）
     * 都必须调用此函数获取网络配置，不允许自行计算 DNS 或拼装 resolv.conf。
     *
     * 策略：
     * 1. 优先使用系统返回的 DNS（去重 + 过滤无效地址）
     * 2. 若系统 DNS 为空或全部无效，追加公共 fallback DNS
     * 3. 运行时 resolv.conf 写入 app 私有目录（layout.tmpDir），不再污染 rootfs
     */
    private fun buildNetworkPlan(
        context: Context,
        layout: AssetExtractor.RuntimeLayout,
        container: ContainerRecord
    ): ContainerNetworkPlan {
        val rawDnsServers = resolveDnsServersFromSystem(context)
        val effectiveDnsServers = buildEffectiveDnsList(rawDnsServers)
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
                rawDnsServers.isEmpty() -> "system-empty:using-fallback"
                effectiveDnsServers.any { it in PUBLIC_DNS_SERVERS } -> "system+fallback"
                else -> "system-only"
            }
        )
    }

    /**
     * 从 Android 系统获取 DNS 服务器地址列表。
     */
    private fun resolveDnsServersFromSystem(context: Context): List<String> {
        val connectivityManager =
            context.getSystemService(CONNECTIVITY_SERVICE) as? ConnectivityManager
        return connectivityManager
            ?.activeNetwork
            ?.let(connectivityManager::getLinkProperties)
            ?.dnsServers
            ?.mapNotNull { it.hostAddress?.trim() }
            ?.filter { it.isNotBlank() }
            ?.distinct()
            .orEmpty()
    }

    /**
     * 构建生效的 DNS 列表。
     *
     * 策略（不过滤私网 DNS，只过滤明确无效地址）：
     * - 过滤掉 null/空串、loopback（127.0.0.0/8）、link-local（169.254.0.0/16）
     * - 如果系统 DNS 非空则返回去重后的系统列表
     * - 如果系统 DNS 为空则使用公共 fallback
     * - 如果系统 DNS 全部被过滤掉，追加公共 fallback
     */
    private fun buildEffectiveDnsList(systemDns: List<String>): List<String> {
        val valid = systemDns
            .filter { ip -> ip.isNotBlank() && !isClearlyInvalidDns(ip) }
            .distinct()

        return when {
            valid.isNotEmpty() -> valid
            systemDns.isEmpty() -> PUBLIC_DNS_SERVERS
            else -> {
                // 系统返回的 DNS 全部是无效地址，过滤掉并追加公共 DNS
                PUBLIC_DNS_SERVERS
            }
        }
    }

    /**
     * 判断一个 IP 地址是否"明显无效"。
     * 只排除极端无效场景，不做完整的 RFC 规范校验。
     */
    private fun isClearlyInvalidDns(ip: String): Boolean {
        return ip == "0.0.0.0" ||
            ip.startsWith("127.") ||      // loopback
            ip.startsWith("169.254.")      // link-local
    }

    /**
     * 将 resolv.conf 写入**运行时目录**（app 私有目录），而非 rootfs。
     * 这样 rootfs 保持干净，多个容器实例共享同一份运行时 resolv.conf。
     */
    private fun writeRuntimeResolvConf(runtimeResolvPath: String, dnsServers: List<String>) {
        val file = File(runtimeResolvPath)
        file.parentFile?.mkdirs()
        runCatching {
            val path = file.toPath()
            if (Files.isSymbolicLink(path) || (file.exists() && !file.isFile)) {
                Files.deleteIfExists(path)
            }
        }.onFailure { error ->
            Logger.i("ContainerManager", "清理旧 resolv.conf 路径失败，继续尝试覆盖: ${error.message}")
        }
        file.writeText(
            dnsServers
                .ifEmpty { PUBLIC_DNS_SERVERS }
                .joinToString(separator = "\n", postfix = "\n") { "nameserver $it" }
        )
    }

    /** 公共 fallback DNS（当系统 DNS 完全无效时使用） */
    private val PUBLIC_DNS_SERVERS = listOf("1.1.1.1", "8.8.8.8", "223.5.5.5")

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
        val profile = BaseImageProfile.fromImageName(container.imageName)
        val rootfsDir = containerFilesystem(container).rootfsDir

        // 写入系统工具文件（不影响网络）
        ensurePackageManagerFiles(rootfsDir, profile)

        // 构建网络配置计划（会写入 layout.tmpDir/resolv.conf）
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
        if (marker.exists() && health.ready) {
            Logger.i("ContainerManager", "基础镜像基础工具链已就绪，跳过重型补齐")
            return
        }

        Logger.i("ContainerManager", "开始为基础镜像补齐基础工具链")
        runCatching {
            traceStage("installBootstrapPackages(base-image)") {
                installBootstrapPackages(context, layout, layout.baseImageDir)
            }
            traceStage("normalizeSyntheticHostLinks(base-image)") {
                normalizeSyntheticHostLinks(layout.baseImageDir)
            }
            val refreshedHealth = inspectBootstrapHealth(layout.baseImageDir)
            Logger.i(
                "ContainerManager",
                "基础镜像 bootstrap 补齐后状态: ${refreshedHealth.summary()}"
            )
            if (refreshedHealth.ready) {
                marker.writeText("ready\n")
                Logger.i("ContainerManager", "基础镜像基础工具链已就绪")
            } else {
                Logger.e("ContainerManager", "基础镜像自举完成后仍缺少关键工具")
            }
        }.onFailure { error ->
            Logger.e("ContainerManager", "基础镜像自举失败: ${error.message}")
        }
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

    private fun currentHostTimeZoneId(): String {
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

    /**
     * 为 bootstrap 场景构建网络配置计划。
     *
     * bootstrap 过程中没有完整的 ContainerRecord，
     * 因此构造一个伪记录（仅用于 buildNetworkPlan），注入到 installBootstrapPackages 链路。
     */
    private fun buildNetworkPlanForBootstrap(
        context: Context,
        layout: AssetExtractor.RuntimeLayout,
        rootfsDir: File
    ): ContainerNetworkPlan {
        val pseudoContainer = ContainerRecord(
            id = "bootstrap",
            displayName = "bootstrap",
            imageName = layout.profile.imageName,
            rootfsPath = rootfsDir.absolutePath,
            workspacePath = layout.tmpDir.absolutePath,
            createdAt = 0L,
            networkMode = NetworkMode.HOST
        )
        return buildNetworkPlan(context, layout, pseudoContainer)
    }

    private fun installBootstrapPackages(
        context: Context,
        layout: AssetExtractor.RuntimeLayout,
        rootfsDir: File
    ) {
        // 获取 bootstrap 专用网络配置（包括 resolv.conf bind mount）
        val bootstrapNetworkPlan = buildNetworkPlanForBootstrap(context, layout, rootfsDir)

        val payload =
            "export DEBIAN_FRONTEND=noninteractive TMPDIR=/tmp; " +
                "mkdir -p /tmp /var/tmp; " +
                "chmod 1777 /tmp /var/tmp; " +
                "dpkg --configure -a; " +
                "apt-get update && " +
                "apt-get install -y " +
                "bash coreutils findutils sed grep " +
                "ca-certificates curl wget git xz-utils unzip file " +
                "zstd zip fd-find jq " +
                "build-essential procps iproute2 net-tools dnsutils supervisor"

        // 使用统一的 proot argv 生成，不再手写完整命令
        val prootArgv = buildBootstrapProotArgv(
            layout = layout,
            rootfsDir = rootfsDir,
            networkPlan = bootstrapNetworkPlan
        )

        val command = arrayOf(
            "/system/bin/sh",
            "-c",
            buildInlineShellEnvironment(buildBootstrapEnvironment(layout)) + " " +
                buildInlineCommandArgv(prootArgv + listOf("/bin/bash", "-lc", payload))
        )

        val process = ProcessBuilder(*command)
            .redirectErrorStream(true)
            .start()

        val output = StringBuilder()
        process.inputStream.bufferedReader().useLines { lines ->
            lines.forEach { line ->
                output.append(line).append('\n')
                RuntimeBootstrapProgress.baseBootstrapOutput(line)
            }
        }
        val exitCode = process.waitFor()
        if (exitCode != 0) {
            Logger.e("ContainerManager", "基础镜像自举安装失败，exitCode=$exitCode, output=$output")
            RuntimeBootstrapProgress.failed("基础镜像基础工具安装失败，exitCode=$exitCode")
            throw IllegalStateException("基础镜像基础工具安装失败")
        }
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
            val process = ProcessBuilder("/system/bin/chmod", mode, target.absolutePath)
                .redirectErrorStream(true)
                .start()
            process.inputStream.bufferedReader().use { it.readText() }
            process.waitFor()
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
        WorkspaceBuildSupport.ensure(workspaceDir)
        val extraPaths = mutableListOf<String>()

        if (WorkspaceBuildSupport.helperSystemBinDir(workspaceDir).exists()) {
            extraPaths += WorkspaceBuildSupport.CONTAINER_HELPER_SYSTEM_BIN_PATH
        }

        if (WorkspaceBuildSupport.helperBinDir(workspaceDir).exists()) {
            extraPaths += WorkspaceBuildSupport.CONTAINER_HELPER_BIN_PATH
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
