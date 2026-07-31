package com.kite.app.foundation.workspace

import android.content.Context
import android.system.Os
import com.kite.app.foundation.runtime.RuntimeControlledLeaseProbeRegistration
import com.kite.app.foundation.runtime.RuntimeControlledLeaseProbeRegistrationReceiver
import java.io.File
import java.nio.file.Files
import org.json.JSONArray
import org.json.JSONObject

/**
 * 工作面构建支撑层。
 *
 * 只放：`/workspace` 默认值、`kf-gradle`、构建缓存目录、工作面引导文件。
 * 不放：rootfs/bind/PRoot 细节、入口层动作。
 */
object WorkspaceBuildSupport {
    private const val CONTAINER_AAPT2_OVERRIDE = "/opt/android-sdk/build-tools/aapt2"
    const val CONTAINER_WORKSPACE_ROOT = "/workspace"
    const val DEFAULT_PROJECT_DIR = "/workspace/KFShell"
    const val HELPER_ROOT_DIR_NAME = ".kf"
    const val HELPER_BIN_DIR_NAME = ".kf/bin"
    const val HELPER_SYSTEM_DIR_NAME = ".kf/system"
    const val HELPER_SYSTEM_BIN_DIR_NAME = ".kf/system/bin"
    const val HELPER_SYSTEM_WRAPPERS_DIR_NAME = ".kf/system/wrappers"
    const val HELPER_SYSTEM_PROC_DIR_NAME = ".kf/system/state/proc"
    const val HELPER_SYSTEM_STATE_DIR_NAME = ".kf/system/state"
    const val HELPER_TOOLCHAIN_DIR_NAME = ".kf/toolchains"
    const val HELPER_SCRIPT_NAME = "kf-gradle"
    private const val FD_WRAPPER_NAME = "fd"
    private const val SS_SHIM_NAME = "ss"
    private const val PS_SHIM_NAME = "ps"
    private const val PGREP_SHIM_NAME = "pgrep"
    private const val PKILL_SHIM_NAME = "pkill"
    private const val KILL_SHIM_NAME = "kill"
    private const val PIDOF_APPLET_NAME = "pidof"
    private const val PSTREE_APPLET_NAME = "pstree"
    private const val FREE_APPLET_NAME = "free"
    private const val TOP_APPLET_NAME = "top"
    private const val RESOURCE_SAMPLER_APPLET_NAME = "kf-resource-sampler"
    private const val SYSTEM_PROCESS_APPLET_ASSET_PATH = "system/kf-procps-arm64"
    private const val KITE_RUNNER_ASSET_PATH = "system/kf-runner-arm64"
    private const val KITE_RUNNER_APPLET_NAME = "kf-runner"
    private const val PROOT_SHIM_NAME = "proot"
    private const val SYSTEMCTL_SHIM_NAME = "systemctl"
    private const val SERVICE_SHIM_NAME = "service"
    private const val SUPERVISORCTL_WRAPPER_NAME = "supervisorctl"
    private const val SUPERVISORD_HEALTH_SNAPSHOT_HELPER_NAME = "kf-supervisord-health-snapshot"
    private const val ADB_WRAPPER_NAME = "adb"
    private const val FASTBOOT_WRAPPER_NAME = "fastboot"
    private const val ADB_CHECK_SCRIPT_NAME = "kf-adb-check"
    private const val ADB_BRIDGE_SCRIPT_NAME = "kf-adb-bridge"
    private const val ANDROID_SHELL_BRIDGE_SCRIPT_NAME = "kf-android-sh"
    private const val HOST_SURFACE_SCRIPT_NAME = "kf-host"
    private const val ENV_SURFACE_SCRIPT_NAME = "kf-env"
    private const val RUNTIME_SURFACE_SCRIPT_NAME = "kf-runtime"
    private const val CONTROLLED_LEASE_PROBE_SCRIPT_NAME = "hermes-controlled-lease-probe.py"
    private const val HOST_CONTRACT_FILE_NAME = "host-contract.json"
    private const val PROOT_LAUNCH_CONTRACT_FILE_NAME = "proot-launch-contract.json"
    private const val PROOT_LAUNCH_REQUEST_FILE_NAME = "proot-launch-request.json"
    private const val RUNTIME_PRESSURE_FILE_NAME = "runtime-pressure.env"
    private const val RUNTIME_PROCESS_TABLE_FILE_NAME = "runtime-process-table.tsv"
    private const val RUNTIME_LIFECYCLE_LEDGER_FILE_NAME = "runtime-lifecycle-ledger.tsv"
    private const val RUNTIME_LIFECYCLE_LEDGER_DIR_NAME = "runtime-lifecycle-ledgers"
    private const val PROOT_TELEMETRY_EVENTS_FILE_NAME = "proot-telemetry-events.tsv"
    private const val RUNTIME_RECLAIMER_POLICY_FILE_NAME = "runtime-reclaimer-policy.json"
    private const val RUNTIME_RESIDENT_POLICY_FILE_NAME = "runtime-resident-policy.json"
    private const val RUNTIME_WORKLOAD_POLICY_FILE_NAME = "runtime-workload-policy.json"
    private const val RUNTIME_WORKLOAD_INTENT_FILE_NAME = "runtime-workload-intent.json"
    private const val RUNTIME_PROCESS_MANIFEST_FILE_NAME = "runtime-process-manifest.json"
    private const val RUNTIME_PROCESS_MANIFEST_EXAMPLE_FILE_NAME = "runtime-process-manifest.example.json"
    private const val RUNTIME_RESOURCE_EVENT_LEDGER_FILE_NAME = "runtime-resource-event-ledger.json"
    private const val RUNTIME_LIFECYCLE_ACTION_INBOX_FILE_NAME = "runtime-lifecycle-action-inbox.json"
    private const val PROOT_CAPACITY_EXECUTOR_POLICY_FILE_NAME = "proot-capacity-executor-policy.json"
    private const val PROOT_POOL_TUNING_LOG_FILE_NAME = "proot-pool-tuning.jsonl"
    private const val SYSTEM_COMPONENTS_INSTALL_MARKER_FILE_NAME = ".system-components-installed"
    private const val GENERATED_TOOL_WRAPPER_MARKER = "# KFShell generated tool wrapper"
    private val systemComponentsInstallLock = Any()
    private val installedSystemComponentKeys = mutableSetOf<String>()
    const val GRADLE_USER_HOME_DIR_NAME = ".gradle-user"
    const val ANDROID_USER_HOME_DIR_NAME = ".android-user"
    const val ANDROID_DATA_DIR_NAME = ".android-data"
    const val CONTAINER_HELPER_BIN_PATH = "/workspace/.kf/bin"
    const val CONTAINER_HELPER_SYSTEM_BIN_PATH = "/workspace/.kf/system/bin"
    const val CONTAINER_HELPER_SYSTEM_WRAPPERS_PATH = "/workspace/.kf/system/wrappers"
    const val CONTAINER_KITE_RUNNER_PATH = "/workspace/.kf/system/bin/kf-runner"
    const val CONTAINER_SUPERVISORD_HEALTH_SNAPSHOT_PATH =
        "/workspace/.kf/system/bin/kf-supervisord-health-snapshot"
    const val CONTAINER_HELPER_SYSTEM_PROC_PATH = "/workspace/.kf/system/state/proc"
    const val CONTAINER_HELPER_SYSTEM_STATE_PATH = "/workspace/.kf/system/state"
    const val CONTAINER_HELPER_TOOLCHAIN_PATH = "/workspace/.kf/toolchains"
    const val CONTAINER_HOST_CONTRACT_PATH = "/workspace/.kf/host-contract.json"
    const val CONTAINER_PROOT_LAUNCH_CONTRACT_PATH = "/workspace/.kf/proot-launch-contract.json"
    const val CONTAINER_PROOT_LAUNCH_REQUEST_PATH = "/workspace/.kf/proot-launch-request.json"
    const val CONTAINER_RUNTIME_PRESSURE_PATH = "/workspace/.kf/runtime-pressure.env"
    const val CONTAINER_RUNTIME_PROCESS_TABLE_PATH = "/workspace/.kf/system/state/runtime-process-table.tsv"
    const val CONTAINER_RUNTIME_LIFECYCLE_LEDGER_PATH =
        "/workspace/.kf/system/state/runtime-lifecycle-ledger.tsv"
    const val CONTAINER_RUNTIME_LIFECYCLE_LEDGER_DIR_PATH =
        "/workspace/.kf/system/state/runtime-lifecycle-ledgers"
    const val CONTAINER_RUNTIME_RESOURCE_SAMPLER_COMMAND =
        "/workspace/.kf/system/bin/kf-resource-sampler --update-table"
    const val CONTAINER_PROOT_TELEMETRY_EVENTS_PATH = "/workspace/.kf/system/state/proot-telemetry-events.tsv"
    const val CONTAINER_RUNTIME_RECLAIMER_POLICY_PATH = "/workspace/.kf/runtime-reclaimer-policy.json"
    const val CONTAINER_RUNTIME_RESIDENT_POLICY_PATH = "/workspace/.kf/runtime-resident-policy.json"
    const val CONTAINER_RUNTIME_WORKLOAD_POLICY_PATH = "/workspace/.kf/runtime-workload-policy.json"
    const val CONTAINER_RUNTIME_WORKLOAD_INTENT_PATH = "/workspace/.kf/runtime-workload-intent.json"
    const val CONTAINER_RUNTIME_PROCESS_MANIFEST_PATH = "/workspace/.kf/runtime-process-manifest.json"
    const val CONTAINER_RUNTIME_PROCESS_MANIFEST_EXAMPLE_PATH =
        "/workspace/.kf/runtime-process-manifest.example.json"
    const val CONTAINER_RUNTIME_RESOURCE_EVENT_LEDGER_PATH =
        "/workspace/.kf/runtime-resource-event-ledger.json"
    const val CONTAINER_RUNTIME_LIFECYCLE_ACTION_INBOX_PATH =
        "/workspace/.kf/runtime-lifecycle-action-inbox.json"
    const val CONTAINER_PROOT_CAPACITY_EXECUTOR_POLICY_PATH =
        "/workspace/.kf/proot-capacity-executor-policy.json"
    const val CONTAINER_PROOT_POOL_TUNING_LOG_PATH = "/workspace/.kf/proot-pool-tuning.jsonl"
    // T014b：Android helper 已迁到共享 .kf/system/bin；这两个路径供 env 注入，指向共享目录。
    const val CONTAINER_GRADLE_HELPER_PATH = "/workspace/.kf/system/bin/kf-gradle"
    const val CONTAINER_RUNTIME_SURFACE_PATH = "/workspace/.kf/system/bin/kf-runtime"
    const val CONTAINER_CONTROLLED_LEASE_PROBE_PATH = "/workspace/.kf/hermes-controlled-lease-probe.py"
    const val CONTAINER_GRADLE_USER_HOME = "/workspace/.gradle-user"
    const val CONTAINER_ANDROID_USER_HOME = "/workspace/.android-user"
    const val CONTAINER_ANDROID_DATA = "/workspace/.android-data"

    internal fun helperRootDir(workspaceDir: File): File = File(workspaceDir, HELPER_ROOT_DIR_NAME)
    internal fun helperBinDir(workspaceDir: File): File = File(workspaceDir, HELPER_BIN_DIR_NAME)
    internal fun helperSystemDir(workspaceDir: File): File = File(workspaceDir, HELPER_SYSTEM_DIR_NAME)
    internal fun helperSystemBinDir(workspaceDir: File): File = File(workspaceDir, HELPER_SYSTEM_BIN_DIR_NAME)
    internal fun helperSystemWrappersDir(workspaceDir: File): File =
        File(workspaceDir, HELPER_SYSTEM_WRAPPERS_DIR_NAME)
    fun helperSystemProcDir(workspaceDir: File): File = File(workspaceDir, HELPER_SYSTEM_PROC_DIR_NAME)
    internal fun helperSystemStateDir(workspaceDir: File): File = File(workspaceDir, HELPER_SYSTEM_STATE_DIR_NAME)
    internal fun helperToolchainDir(workspaceDir: File): File = File(workspaceDir, HELPER_TOOLCHAIN_DIR_NAME)
    internal fun gradleUserHomeDir(workspaceDir: File): File = File(workspaceDir, GRADLE_USER_HOME_DIR_NAME)
    internal fun androidUserHomeDir(workspaceDir: File): File = File(workspaceDir, ANDROID_USER_HOME_DIR_NAME)
    internal fun androidDataDir(workspaceDir: File): File = File(workspaceDir, ANDROID_DATA_DIR_NAME)
    fun runtimePressureFile(workspaceDir: File): File = File(helperRootDir(workspaceDir), RUNTIME_PRESSURE_FILE_NAME)
    fun runtimeProcessTableFile(workspaceDir: File): File =
        File(helperSystemStateDir(workspaceDir), RUNTIME_PROCESS_TABLE_FILE_NAME)
    fun runtimeLifecycleLedgerFile(workspaceDir: File): File =
        File(helperSystemStateDir(workspaceDir), RUNTIME_LIFECYCLE_LEDGER_FILE_NAME)
    fun runtimeLifecycleLedgerDir(workspaceDir: File): File =
        File(helperSystemStateDir(workspaceDir), RUNTIME_LIFECYCLE_LEDGER_DIR_NAME)
    fun prootTelemetryEventsFile(workspaceDir: File): File =
        File(helperSystemStateDir(workspaceDir), PROOT_TELEMETRY_EVENTS_FILE_NAME)
    fun runtimeProcProjectionDir(workspaceDir: File): File = helperSystemProcDir(workspaceDir)
    fun runtimeReclaimerPolicyFile(workspaceDir: File): File =
        File(helperRootDir(workspaceDir), RUNTIME_RECLAIMER_POLICY_FILE_NAME)
    fun runtimeResidentPolicyFile(workspaceDir: File): File =
        File(helperRootDir(workspaceDir), RUNTIME_RESIDENT_POLICY_FILE_NAME)
    fun runtimeWorkloadPolicyFile(workspaceDir: File): File =
        File(helperRootDir(workspaceDir), RUNTIME_WORKLOAD_POLICY_FILE_NAME)
    fun runtimeWorkloadIntentFile(workspaceDir: File): File =
        File(helperRootDir(workspaceDir), RUNTIME_WORKLOAD_INTENT_FILE_NAME)
    fun runtimeProcessManifestFile(workspaceDir: File): File =
        File(helperRootDir(workspaceDir), RUNTIME_PROCESS_MANIFEST_FILE_NAME)
    fun runtimeProcessManifestExampleFile(workspaceDir: File): File =
        File(helperRootDir(workspaceDir), RUNTIME_PROCESS_MANIFEST_EXAMPLE_FILE_NAME)
    fun runtimeResourceEventLedgerFile(workspaceDir: File): File =
        File(helperRootDir(workspaceDir), RUNTIME_RESOURCE_EVENT_LEDGER_FILE_NAME)
    fun runtimeLifecycleActionInboxFile(workspaceDir: File): File =
        File(helperRootDir(workspaceDir), RUNTIME_LIFECYCLE_ACTION_INBOX_FILE_NAME)
    fun prootCapacityExecutorPolicyFile(workspaceDir: File): File =
        File(helperRootDir(workspaceDir), PROOT_CAPACITY_EXECUTOR_POLICY_FILE_NAME)
    fun prootPoolTuningLogFile(workspaceDir: File): File =
        File(helperRootDir(workspaceDir), PROOT_POOL_TUNING_LOG_FILE_NAME)
    fun prootLaunchContractFile(workspaceDir: File): File =
        File(helperRootDir(workspaceDir), PROOT_LAUNCH_CONTRACT_FILE_NAME)
    fun prootLaunchRequestFile(workspaceDir: File): File =
        File(helperRootDir(workspaceDir), PROOT_LAUNCH_REQUEST_FILE_NAME)

    @Synchronized
    fun writeProotLaunchContract(workspaceDir: File, content: String) {
        writeTextIfChanged(prootLaunchContractFile(workspaceDir), content)
    }

    fun ensure(workspaceDir: File) {
        val helperRoot = helperRootDir(workspaceDir)
        val helperSystemDir = helperSystemDir(workspaceDir)
        val helperSystemBinDir = helperSystemBinDir(workspaceDir)
        val helperSystemWrappersDir = helperSystemWrappersDir(workspaceDir)
        val helperSystemProcDir = helperSystemProcDir(workspaceDir)
        val helperSystemStateDir = helperSystemStateDir(workspaceDir)
        val helperToolchainDir = helperToolchainDir(workspaceDir)
        val gradleUserHomeDir = gradleUserHomeDir(workspaceDir)
        val androidUserHomeDir = androidUserHomeDir(workspaceDir)
        val androidDataDir = androidDataDir(workspaceDir)

        // T014g：ensure 只准备 Android 持有的共享目录；绝不创建/删除/写 .kf/bin（环境变化层）。
        // .kf/bin 由 Base 封存前或经绑定 View 的 PRoot 进程创建。
        listOf(
            helperRoot,
            helperSystemDir,
            helperSystemBinDir,
            helperSystemWrappersDir,
            helperSystemProcDir,
            helperSystemStateDir,
            helperToolchainDir,
            gradleUserHomeDir,
            androidUserHomeDir,
            androidDataDir
        ).forEach { dir ->
            if (!dir.exists()) {
                dir.mkdirs()
            }
        }
        chmodIfPossible(helperSystemDir, 0b111101101)
        chmodIfPossible(helperSystemBinDir, 0b111101101)
        chmodIfPossible(helperSystemWrappersDir, 0b111101101)
        chmodIfPossible(helperSystemStateDir, 0b111101101)

        writeTextIfChanged(
            File(helperRoot, "README.txt"),
            """
            KF 构建辅助目录：
            1. `bin/kf-gradle`：手机容器内统一的 Gradle 入口，会自动固定 `GRADLE_USER_HOME` 和项目探测规则。
            2. `../.gradle-user`：Gradle 缓存目录，尽量保持在 `/workspace` 本地路径。
            3. `../.android-user` 与 `../.android-data`：给 Android 构建工具预留的稳定用户目录。
            """.trimIndent() + "\n"
        )
        writeWorkspaceRootReadme(workspaceDir)

        val helperScript = File(helperSystemBinDir, HELPER_SCRIPT_NAME)
        writeTextIfChanged(helperScript, buildWorkspaceGradleHelperScript())
        helperScript.setExecutable(true, false)

        val fdWrapper = File(helperSystemBinDir, FD_WRAPPER_NAME)
        writeTextIfChanged(fdWrapper, buildFdWrapperScript())
        fdWrapper.setExecutable(true, false)

        val ssShim = File(helperSystemBinDir, SS_SHIM_NAME)
        writeTextIfChanged(ssShim, buildNetlinkToolShimScript(SS_SHIM_NAME))
        ssShim.setExecutable(true, false)

        val prootShim = File(helperSystemBinDir, PROOT_SHIM_NAME)
        writeTextIfChanged(prootShim, buildProotBoundaryShimScript())
        prootShim.setExecutable(true, false)

        val supervisorctlWrapper = File(helperSystemBinDir, SUPERVISORCTL_WRAPPER_NAME)
        writeTextIfChanged(supervisorctlWrapper, buildSupervisorctlWrapperScript())
        supervisorctlWrapper.setExecutable(true, false)

        ensureSupervisordHealthSnapshotHelper(workspaceDir)

        val adbWrapper = File(helperSystemBinDir, ADB_WRAPPER_NAME)
        writeTextIfChanged(adbWrapper, buildAdbClientWrapperScript())
        adbWrapper.setExecutable(true, false)

        val adbCheckScript = File(helperSystemBinDir, ADB_CHECK_SCRIPT_NAME)
        writeTextIfChanged(adbCheckScript, buildAdbCheckScript())
        adbCheckScript.setExecutable(true, false)

        val adbBridgeScript = File(helperSystemBinDir, ADB_BRIDGE_SCRIPT_NAME)
        writeTextIfChanged(adbBridgeScript, buildAdbBridgeScript())
        adbBridgeScript.setExecutable(true, false)

        val fastbootWrapper = File(helperSystemBinDir, FASTBOOT_WRAPPER_NAME)
        writeTextIfChanged(fastbootWrapper, buildFastbootClientWrapperScript())
        fastbootWrapper.setExecutable(true, false)

        val androidShellBridgeScript = File(helperSystemBinDir, ANDROID_SHELL_BRIDGE_SCRIPT_NAME)
        writeTextIfChanged(androidShellBridgeScript, buildAndroidShellBridgeScript())
        androidShellBridgeScript.setExecutable(true, false)

        val hostSurfaceScript = File(helperSystemBinDir, HOST_SURFACE_SCRIPT_NAME)
        writeTextIfChanged(hostSurfaceScript, buildHostSurfaceScript())
        hostSurfaceScript.setExecutable(true, false)

        val envSurfaceScript = File(helperSystemBinDir, ENV_SURFACE_SCRIPT_NAME)
        writeTextIfChanged(envSurfaceScript, buildEnvSurfaceScript())
        envSurfaceScript.setExecutable(true, false)

        val runtimeSurfaceScript = File(helperSystemBinDir, RUNTIME_SURFACE_SCRIPT_NAME)
        writeTextIfChanged(runtimeSurfaceScript, buildRuntimeSurfaceScript())
        runtimeSurfaceScript.setExecutable(true, false)

        val controlledLeaseProbeScript = File(helperRoot, CONTROLLED_LEASE_PROBE_SCRIPT_NAME)
        writeTextIfChanged(controlledLeaseProbeScript, buildControlledLeaseProbeScript())
        controlledLeaseProbeScript.setExecutable(true, false)

        val runtimePressureFile = runtimePressureFile(workspaceDir)
        if (!runtimePressureFile.exists()) {
            writeTextIfChanged(runtimePressureFile, buildRuntimePressureUnknownSurface())
        }
        val runtimeProcessTableFile = runtimeProcessTableFile(workspaceDir)
        if (!runtimeProcessTableFile.exists()) {
            writeTextIfChanged(runtimeProcessTableFile, buildRuntimeProcessTableUnknownSurface())
        }
        val prootTelemetryEventsFile = prootTelemetryEventsFile(workspaceDir)
        if (!prootTelemetryEventsFile.exists()) {
            writeTextIfChanged(prootTelemetryEventsFile, buildProotTelemetryEventsUnknownSurface())
        }
        val runtimeReclaimerPolicyFile = runtimeReclaimerPolicyFile(workspaceDir)
        if (!runtimeReclaimerPolicyFile.exists()) {
            writeTextIfChanged(runtimeReclaimerPolicyFile, buildRuntimeReclaimerPolicyTemplate())
        } else {
            migrateLegacyRuntimeReclaimerPolicy(runtimeReclaimerPolicyFile)
        }
        val runtimeResidentPolicyFile = runtimeResidentPolicyFile(workspaceDir)
        if (!runtimeResidentPolicyFile.exists()) {
            writeTextIfChanged(runtimeResidentPolicyFile, buildRuntimeResidentPolicyTemplate())
        }
        val runtimeWorkloadPolicyFile = runtimeWorkloadPolicyFile(workspaceDir)
        if (!runtimeWorkloadPolicyFile.exists()) {
            writeTextIfChanged(runtimeWorkloadPolicyFile, buildRuntimeWorkloadPolicyTemplate())
        } else {
            migrateLegacyRuntimeWorkloadPolicy(runtimeWorkloadPolicyFile)
        }
        val runtimeWorkloadIntentFile = runtimeWorkloadIntentFile(workspaceDir)
        if (!runtimeWorkloadIntentFile.exists()) {
            writeTextIfChanged(runtimeWorkloadIntentFile, buildRuntimeWorkloadIntentTemplate())
        }
        val runtimeProcessManifestFile = runtimeProcessManifestFile(workspaceDir)
        if (!runtimeProcessManifestFile.exists()) {
            writeTextIfChanged(runtimeProcessManifestFile, buildRuntimeProcessManifestTemplate())
        }
        val runtimeProcessManifestExampleFile = runtimeProcessManifestExampleFile(workspaceDir)
        if (!runtimeProcessManifestExampleFile.exists()) {
            writeTextIfChanged(runtimeProcessManifestExampleFile, buildRuntimeProcessManifestExampleTemplate())
        }
        val prootCapacityExecutorPolicyFile = prootCapacityExecutorPolicyFile(workspaceDir)
        if (!prootCapacityExecutorPolicyFile.exists()) {
            writeTextIfChanged(prootCapacityExecutorPolicyFile, buildProotCapacityExecutorPolicyTemplate())
        } else {
            migrateLegacyProotCapacityExecutorPolicy(prootCapacityExecutorPolicyFile)
        }
        val prootLaunchContractFile = prootLaunchContractFile(workspaceDir)
        if (!prootLaunchContractFile.exists()) {
            writeTextIfChanged(prootLaunchContractFile, buildProotLaunchContractUnknownSurface())
        }
        val prootLaunchRequestFile = prootLaunchRequestFile(workspaceDir)
        if (!prootLaunchRequestFile.exists()) {
            writeTextIfChanged(prootLaunchRequestFile, buildProotLaunchRequestTemplate())
        }

        writeTextIfChanged(File(helperRoot, HOST_CONTRACT_FILE_NAME), buildHostContractJson())
        // T014g：工具链 wrapper 写入共享 .kf/system/wrappers，不再触碰环境变化目录 .kf/bin。
        // PATH 让 .kf/bin（环境命令）排在 wrappers 之前，用户安装的同名命令优先。
        syncToolchainCommandWrappers(workspaceDir, helperSystemWrappersDir)
        chmodIfPossible(helperSystemBinDir, 0b101101101)
        chmodIfPossible(helperSystemWrappersDir, 0b101101101)
    }

    /** 只校准 Supervisord 健康采集 helper，供高频刷新避免重跑整套 Workspace ensure。 */
    @Synchronized
    fun ensureSupervisordHealthSnapshotHelper(workspaceDir: File): File {
        val systemBin = helperSystemBinDir(workspaceDir)
        if (!systemBin.exists()) systemBin.mkdirs()
        val helper = File(systemBin, SUPERVISORD_HEALTH_SNAPSHOT_HELPER_NAME)
        writeTextIfChanged(helper, buildSupervisordHealthSnapshotScript())
        helper.setExecutable(true, false)
        return helper
    }

    fun installSystemComponents(
        context: Context,
        workspaceDir: File,
        // T014h：迁移清理是否允许，必须由调用方查询 ProotViewStore 真实封存状态提供。
        // 默认 false（未封存）仅适用于 bootstrap 期；普通启动路径必须传入真实 checker。
        // versionCode/layout marker 不能作为封存依据——App 升级后 View 可能早已封存。
        sealedChecker: () -> Boolean = { false },
    ) {
        synchronized(systemComponentsInstallLock) {
            ensure(workspaceDir)
            val systemDir = helperSystemDir(workspaceDir)
            val systemBinDir = helperSystemBinDir(workspaceDir)
            val systemProcDir = helperSystemProcDir(workspaceDir)
            val systemStateDir = helperSystemStateDir(workspaceDir)
            val installVersion = systemComponentsInstallVersion(context)
            val installKey = "${workspaceDir.absolutePath}|$installVersion"
            val installMarker = File(systemStateDir, SYSTEM_COMPONENTS_INSTALL_MARKER_FILE_NAME)
            if (
                installedSystemComponentKeys.contains(installKey) &&
                installMarker.exists() &&
                runCatching { installMarker.readText() }.getOrNull() == installVersion
            ) {
                return
            }
            if (
                installMarker.exists() &&
                runCatching { installMarker.readText() }.getOrNull() == installVersion &&
                processAppletCommandNames().all { commandName -> File(systemBinDir, commandName).canExecute() } &&
                optionalRunnerReady(context, systemBinDir)
            ) {
                installedSystemComponentKeys.add(installKey)
                return
            }
            listOf(systemDir, systemBinDir, systemProcDir, systemStateDir).forEach { dir ->
                if (!dir.exists()) {
                    dir.mkdirs()
                }
            }
            chmodIfPossible(systemDir, 0b111101101)
            chmodIfPossible(systemBinDir, 0b111101101)
            val appletBytes = context.assets.open(SYSTEM_PROCESS_APPLET_ASSET_PATH).use { input ->
                input.readBytes()
            }
            processAppletCommandNames().forEach { commandName ->
                val destination = File(systemBinDir, commandName)
                val shouldWrite = !destination.exists() ||
                    runCatching { !destination.readBytes().contentEquals(appletBytes) }.getOrDefault(true)
                if (shouldWrite) {
                    destination.setWritable(true, false)
                    destination.writeBytes(appletBytes)
                }
                destination.setExecutable(true, false)
                destination.setReadable(true, false)
                destination.setWritable(false, false)
                chmodIfPossible(destination, 0b101101101)
            }
            val runnerDestination = File(systemBinDir, KITE_RUNNER_APPLET_NAME)
            val runnerInstalled = copyAssetExecutableIfAvailable(
                context = context,
                assetPath = KITE_RUNNER_ASSET_PATH,
                destination = runnerDestination
            )
            if (!runnerInstalled && runnerDestination.exists()) {
                runnerDestination.setWritable(true, false)
                runnerDestination.delete()
            }
            // T014h：迁移清理门禁由 sealedChecker 真实封存状态决定。
            migrateLegacyEnvBinIfNeeded(workspaceDir, sealedChecker())
            writeTextIfChanged(installMarker, installVersion)
            chmodIfPossible(systemDir, 0b101101101)
            chmodIfPossible(systemBinDir, 0b101101101)
            chmodIfPossible(systemProcDir, 0b111101101)
            chmodIfPossible(systemStateDir, 0b111101101)
            installedSystemComponentKeys.add(installKey)
        }
    }

    private fun systemComponentsInstallVersion(context: Context): String {
        val versionCode = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode
        }.getOrDefault(-1L)
        val runnerState = if (assetExists(context, KITE_RUNNER_ASSET_PATH)) "present" else "absent"
        return "versionCode=$versionCode\nasset=$SYSTEM_PROCESS_APPLET_ASSET_PATH\nrunner=$KITE_RUNNER_ASSET_PATH:$runnerState\nlayout=v11_kf_runner_protocol_v1\n"
    }

    private fun processAppletCommandNames(): List<String> {
        return listOf(
            PS_SHIM_NAME,
            PGREP_SHIM_NAME,
            PKILL_SHIM_NAME,
            KILL_SHIM_NAME,
            PIDOF_APPLET_NAME,
            PSTREE_APPLET_NAME,
            FREE_APPLET_NAME,
            TOP_APPLET_NAME,
            RESOURCE_SAMPLER_APPLET_NAME,
            SYSTEMCTL_SHIM_NAME,
            SERVICE_SHIM_NAME
        )
    }

    private fun chmodIfPossible(file: File, mode: Int) {
        runCatching {
            Os.chmod(file.absolutePath, mode)
        }
    }

    private fun optionalRunnerReady(context: Context, systemBinDir: File): Boolean {
        return !assetExists(context, KITE_RUNNER_ASSET_PATH) ||
            File(systemBinDir, KITE_RUNNER_APPLET_NAME).canExecute()
    }

    private fun assetExists(context: Context, assetPath: String): Boolean {
        return runCatching {
            context.assets.open(assetPath).use { }
        }.isSuccess
    }

    private fun copyAssetExecutableIfAvailable(context: Context, assetPath: String, destination: File): Boolean {
        val bytes = runCatching {
            context.assets.open(assetPath).use { input -> input.readBytes() }
        }.getOrNull() ?: return false
        val shouldWrite = !destination.exists() ||
            runCatching { !destination.readBytes().contentEquals(bytes) }.getOrDefault(true)
        if (shouldWrite) {
            destination.setWritable(true, false)
            destination.writeBytes(bytes)
        }
        destination.setExecutable(true, false)
        destination.setReadable(true, false)
        destination.setWritable(false, false)
        chmodIfPossible(destination, 0b101101101)
        return true
    }

    private fun removeLegacyProcessShimScripts(helperBinDir: File) {
        listOf(
            PS_SHIM_NAME,
            PGREP_SHIM_NAME,
            PKILL_SHIM_NAME,
            KILL_SHIM_NAME,
            SYSTEMCTL_SHIM_NAME,
            SERVICE_SHIM_NAME
        ).forEach { name ->
            val file = File(helperBinDir, name)
            if (!file.isFile) return@forEach
            val text = runCatching { file.readText() }.getOrDefault("")
            val ownedLegacyShim = text.startsWith("#!/usr/bin/env sh") &&
                (
                    text.contains("KF_RUNTIME_PROCESS_TABLE_PATH") ||
                        text.contains("Ubuntu signal semantics") ||
                        text.contains("systemctl-compatible") ||
                        text.contains("supervisord backend")
                )
            if (ownedLegacyShim) {
                file.delete()
            }
        }
    }

    private fun removeLegacyAdbScripts(helperBinDir: File) {
        listOf(ADB_WRAPPER_NAME, ADB_CHECK_SCRIPT_NAME, ADB_BRIDGE_SCRIPT_NAME).forEach { name ->
            val file = File(helperBinDir, name)
            if (!file.isFile) return@forEach
            val text = runCatching { file.readText() }.getOrDefault("")
            if (
                text.contains("KFSHELL_ADB") ||
                text.contains("kf-adb-bridge") ||
                text.contains("KF_ADB_HOST_SELF_SERIAL")
            ) {
                file.delete()
            }
        }
    }

    /**
     * T014b：Android helper 已迁到共享 .kf/system/bin。这里清理旧版本留在环境变化目录
     * .kf/bin 的残留，避免幽灵文件污染 View Upper。KF 专有名直接删；通用名（fd/ss/proot/
     * supervisorctl）只有内容确认是 KF 生成的 helper 才删，绝不误删用户/资源安装的同名命令。
     */
    private fun removeLegacyAndroidHelpersFromEnvBin(helperBinDir: File) {
        // KF 专有名：不可能来自用户/资源，直接删。
        listOf(HELPER_SCRIPT_NAME, ANDROID_SHELL_BRIDGE_SCRIPT_NAME, HOST_SURFACE_SCRIPT_NAME,
            ENV_SURFACE_SCRIPT_NAME, RUNTIME_SURFACE_SCRIPT_NAME).forEach { name ->
            File(helperBinDir, name).takeIf { it.isFile }?.delete()
        }
        // 通用名：内容校验确认是 KF helper 才删。
        listOf(
            FD_WRAPPER_NAME to "exec fdfind",
            SS_SHIM_NAME to "KFShell runs Ubuntu",
            PROOT_SHIM_NAME to "KFSHELL_PROOT_SHIM_BEGIN",
            SUPERVISORCTL_WRAPPER_NAME to "supervisor daemon shutdown is blocked"
        ).forEach { (name, marker) ->
            val file = File(helperBinDir, name)
            if (!file.isFile) return@forEach
            val text = runCatching { file.readText() }.getOrDefault("")
            if (text.contains(marker)) file.delete()
        }
    }

    /**
     * T014g：清理旧版本写到环境变化目录 .kf/bin 的工具链 wrapper（内容以
     * GENERATED_TOOL_WRAPPER_MARKER 开头）。只在迁移（installSystemComponents 版本变化）时调用，
     * 不在 ensure 里调用；用户手写或资源安装的同名命令不含该标记，不会被误删。
     */
    private fun removeLegacyToolWrappersFromEnvBin(helperBinDir: File) {
        helperBinDir.listFiles()?.forEach { file ->
            if (!file.isFile) return@forEach
            val text = runCatching { file.readText() }.getOrDefault("")
            if (text.startsWith(GENERATED_TOOL_WRAPPER_MARKER)) {
                file.delete()
            }
        }
    }

    /**
     * T014h：迁移清理旧版本残留在环境变化目录 .kf/bin 的 Android helper/wrapper。
     *
     * 门禁：sealed 必须由 ProotViewStore 真实封存状态提供。
     *   - 未封存（首次创建/Base 准备期）：允许一次性清理内容标记确认的 Kite 遗留。
     *   - 已封存：绝不直接清理 .kf/bin；如需清理必须经绑定当前 View 的 PRoot 执行。
     * versionCode/installMarker 变化不代表 Base 未封存（App 升级后 View 可能早已封存）。
     * internal 以便审计测试直接覆盖迁移门，而不必依赖 Context/assets。
     */
    internal fun migrateLegacyEnvBinIfNeeded(workspaceDir: File, sealed: Boolean) {
        if (sealed) return
        val envBinDir = helperBinDir(workspaceDir)
        if (!envBinDir.isDirectory) return
        removeLegacyProcessShimScripts(envBinDir)
        removeLegacyAdbScripts(envBinDir)
        removeLegacyAndroidHelpersFromEnvBin(envBinDir)
        removeLegacyToolWrappersFromEnvBin(envBinDir)
    }

    fun buildWorkSurfaceEnvironment(): LinkedHashMap<String, String> {
        return linkedMapOf(
            "GRADLE_USER_HOME" to CONTAINER_GRADLE_USER_HOME,
            "ANDROID_USER_HOME" to CONTAINER_ANDROID_USER_HOME,
            "KF_WORKSPACE_ROOT" to CONTAINER_WORKSPACE_ROOT,
            "KF_PROJECT_DIR" to DEFAULT_PROJECT_DIR,
            "KF_SYSTEM_BIN" to CONTAINER_HELPER_SYSTEM_BIN_PATH,
            "KF_SYSTEM_PROC" to CONTAINER_HELPER_SYSTEM_PROC_PATH,
            "KF_SYSTEM_STATE" to CONTAINER_HELPER_SYSTEM_STATE_PATH,
            "KF_GRADLE_HELPER" to CONTAINER_GRADLE_HELPER_PATH,
            "KF_RUNTIME_HELPER" to CONTAINER_RUNTIME_SURFACE_PATH,
            "KF_HOST_NETWORK_MODE" to "shared_host_stack",
            "KF_HOST_LOOPBACK" to "shared_with_android",
            "KF_HOST_PORT_POLICY" to "prefer_127_0_0_1_and_ports_ge_1024",
            "KF_HOST_CONTROL_BOUNDARY" to "android_control_stays_in_apk",
            "KF_HOST_DEFAULT_EXPOSURE" to "LOOPBACK_ONLY",
            "KF_HOST_CONTRACT_PATH" to CONTAINER_HOST_CONTRACT_PATH,
            "KF_PROOT_LAUNCH_CONTRACT_PATH" to CONTAINER_PROOT_LAUNCH_CONTRACT_PATH,
            "KF_PROOT_LAUNCH_REQUEST_PATH" to CONTAINER_PROOT_LAUNCH_REQUEST_PATH,
            "KF_PROOT_LAUNCH_AUTHORITY" to "android_control_plane",
            "KF_PROOT_LAUNCH_OWNER" to "android_apk",
            "KF_PROOT_EXECUTION_BOUNDARY" to "ubuntu_declares_android_launches",
            "KF_PROOT_REQUEST_MODE" to "ubuntu_intent_advisory_only",
            "KF_PROOT_RUNTIME_DESCRIPTOR_SOURCE" to "launch_contract.runtime.proot",
            "KF_PROOT_TELEMETRY_MODE" to "debug_jsonl_lifecycle_v0",
            "KF_PROOT_SHIM_MODE" to "android_owned_refuse_direct_launch",
            "KF_RUNTIME_PRESSURE_PATH" to CONTAINER_RUNTIME_PRESSURE_PATH,
            "KF_RUNTIME_PROCESS_TABLE_PATH" to CONTAINER_RUNTIME_PROCESS_TABLE_PATH,
            "KF_RUNTIME_RESOURCE_SAMPLER_COMMAND" to CONTAINER_RUNTIME_RESOURCE_SAMPLER_COMMAND,
            "KF_PROOT_TELEMETRY_EVENTS_PATH" to CONTAINER_PROOT_TELEMETRY_EVENTS_PATH,
            "KF_PROCFS_PROJECTION_ROOT" to CONTAINER_HELPER_SYSTEM_PROC_PATH,
            "KF_RUNTIME_POLICY_PATH" to CONTAINER_RUNTIME_RECLAIMER_POLICY_PATH,
            "KF_RUNTIME_RESIDENT_POLICY_PATH" to CONTAINER_RUNTIME_RESIDENT_POLICY_PATH,
            "KF_RUNTIME_WORKLOAD_POLICY_PATH" to CONTAINER_RUNTIME_WORKLOAD_POLICY_PATH,
            "KF_RUNTIME_WORKLOAD_INTENT_PATH" to CONTAINER_RUNTIME_WORKLOAD_INTENT_PATH,
            "KF_RUNTIME_PROCESS_MANIFEST_PATH" to CONTAINER_RUNTIME_PROCESS_MANIFEST_PATH,
            "KF_RUNTIME_PROCESS_MANIFEST_EXAMPLE_PATH" to CONTAINER_RUNTIME_PROCESS_MANIFEST_EXAMPLE_PATH,
            "KF_RUNTIME_RESOURCE_EVENT_LEDGER_PATH" to CONTAINER_RUNTIME_RESOURCE_EVENT_LEDGER_PATH,
            "KF_RUNTIME_LIFECYCLE_ACTION_INBOX_PATH" to CONTAINER_RUNTIME_LIFECYCLE_ACTION_INBOX_PATH,
            "KF_PROOT_CAPACITY_EXECUTOR_POLICY_PATH" to CONTAINER_PROOT_CAPACITY_EXECUTOR_POLICY_PATH,
            "KF_PROOT_POOL_TUNING_LOG_PATH" to CONTAINER_PROOT_POOL_TUNING_LOG_PATH,
            "KF_RUNTIME_PROFILE_SURFACE_MODE" to "policy_group_advisory_only",
            "KF_RUNTIME_RECLAIMER_MODE" to "android_control_plane_v0",
            "KF_RUNTIME_RESIDENT_CLASSES" to "CRITICAL_CORE,RESIDENT,INTERACTIVE",
            "KF_RUNTIME_RECLAIM_ORDER" to "registered_ephemeral_then_batch_then_classified_unknown",
            "KF_ADB_MODE" to "apk_bridge",
            "KF_ADB_HOST_SELF_SERIAL" to "kf-host-self",
            "KF_ADB_HOST_SELF_SOURCE" to "apk_bridge_contract_v0",
            "KF_ADB_PROOT_SERVER_DEFAULT" to "disabled",
            "KF_ADB_BRIDGE_STATUS" to "listed",
            "KF_ADB_BRIDGE_HELPER" to "kf-adb-bridge",
            "KF_ADB_BRIDGE_DIR" to "/workspace/.kf/adb-bridge",
            "KF_ADB_PERMISSION_SOURCE" to "apk_bridge_contract_v0",
            "KF_ADB_SHIZUKU_AVAILABLE" to "false",
            "KF_ADB_SHIZUKU_PERMISSION" to "unknown",
            "KF_ADB_SHIZUKU_UID" to "",
            "KF_ADB_SHIZUKU_VERSION" to "",
            "KF_ADB_SHIZUKU_ERROR" to ""
        )
    }

    private fun buildWorkspaceGradleHelperScript(): String {
        return """
            |#!/usr/bin/env bash
            |set -euo pipefail
            |
            |WORKSPACE_ROOT="${'$'}{KF_WORKSPACE_ROOT:-${CONTAINER_WORKSPACE_ROOT}}"
            |DEFAULT_PROJECT_DIR="${'$'}{KF_PROJECT_DIR:-${DEFAULT_PROJECT_DIR}}"
            |
            |find_project_dir() {
            |  if [[ -f "${'$'}DEFAULT_PROJECT_DIR/gradlew" ]]; then
            |    echo "${'$'}DEFAULT_PROJECT_DIR"
            |    return 0
            |  fi
            |
            |  local probe="${'$'}PWD"
            |  while [[ "${'$'}probe" != "/" ]]; do
            |    if [[ -f "${'$'}probe/gradlew" ]]; then
            |      echo "${'$'}probe"
            |      return 0
            |    fi
            |    probe="$(dirname "${'$'}probe")"
            |  done
            |
            |  return 1
            |}
            |
            |PROJECT_DIR="${'$'}{KF_ACTIVE_PROJECT_DIR:-}"
            |if [[ -n "${'$'}PROJECT_DIR" && ! -f "${'$'}PROJECT_DIR/gradlew" ]]; then
            |  PROJECT_DIR=""
            |fi
            |
            |if [[ -z "${'$'}PROJECT_DIR" ]]; then
            |  PROJECT_DIR="$(find_project_dir || true)"
            |fi
            |
            |export GRADLE_USER_HOME="${'$'}{GRADLE_USER_HOME:-${CONTAINER_GRADLE_USER_HOME}}"
            |export ANDROID_USER_HOME="${'$'}{ANDROID_USER_HOME:-${CONTAINER_ANDROID_USER_HOME}}"
            |export ANDROID_DATA="${'$'}{ANDROID_DATA:-${CONTAINER_ANDROID_DATA}}"
            |
            |mkdir -p "${'$'}GRADLE_USER_HOME" "${'$'}ANDROID_USER_HOME" "${'$'}ANDROID_DATA"
            |
            |usage() {
            |  cat <<'EOF'
            |用法:
            |  kf-gradle doctor
            |  kf-gradle compile
            |  kf-gradle assemble
            |  kf-gradle prod
            |  kf-gradle status
            |  kf-gradle <任意 Gradle 参数...>
            |
            |默认行为:
            |  不传参数时等同于 assemble
            |EOF
            |}
            |
            |warn_android_storage() {
            |  case "${'$'}{PROJECT_DIR:-${'$'}PWD}" in
            |    /storage/*|/sdcard/*)
            |      echo "警告: 当前项目位于安卓共享存储，正式构建请迁移到 ${CONTAINER_WORKSPACE_ROOT} 后再执行。" >&2
            |      ;;
            |  esac
            |}
            |
            |run_gradle() {
            |  if [[ -z "${'$'}PROJECT_DIR" ]]; then
            |    echo "未找到 Gradle Wrapper。请先进入包含 gradlew 的项目目录，或把项目放到 ${DEFAULT_PROJECT_DIR}。" >&2
            |    exit 1
            |  fi
            |  "${'$'}PROJECT_DIR/gradlew" -p "${'$'}PROJECT_DIR" --console=plain \
            |    -Pandroid.overridePathCheck=true \
            |    -Pandroid.aapt2FromMavenOverride=${CONTAINER_AAPT2_OVERRIDE} \
            |    "${'$'}@"
            |}
            |
            |doctor() {
            |  echo "WORKSPACE_ROOT=${'$'}WORKSPACE_ROOT"
            |  echo "PROJECT_DIR=${'$'}{PROJECT_DIR:-<missing>}"
            |  echo "GRADLE_USER_HOME=${'$'}GRADLE_USER_HOME"
            |  echo "ANDROID_USER_HOME=${'$'}ANDROID_USER_HOME"
            |  echo "ANDROID_DATA=${'$'}ANDROID_DATA"
            |  echo "PWD=${'$'}PWD"
            |  warn_android_storage
            |  if [[ -n "${'$'}PROJECT_DIR" ]]; then
            |    run_gradle --status || true
            |  fi
            |}
            |
            |if [[ ${'$'}# -eq 0 ]]; then
            |  set -- assemble
            |fi
            |
            |COMMAND="${'$'}1"
            |shift || true
            |
            |case "${'$'}COMMAND" in
            |  doctor)
            |    doctor
            |    ;;
            |  compile)
            |    warn_android_storage
            |    run_gradle :app:compileDebugKotlin "${'$'}@"
            |    ;;
            |  assemble)
            |    warn_android_storage
            |    run_gradle :app:assembleDebug "${'$'}@"
            |    ;;
            |  prod)
            |    warn_android_storage
            |    run_gradle :app:assembleProdDebug "${'$'}@"
            |    ;;
            |  status)
            |    run_gradle --status "${'$'}@"
            |    ;;
            |  -h|--help|help)
            |    usage
            |    ;;
            |  *)
            |    warn_android_storage
            |    run_gradle "${'$'}COMMAND" "${'$'}@"
            |    ;;
            |esac
        """.trimMargin() + "\n"
    }

    private fun buildFdWrapperScript(): String {
        return """
            |#!/usr/bin/env sh
            |exec fdfind "${'$'}@"
        """.trimMargin() + "\n"
    }

    private fun buildNetlinkToolShimScript(commandName: String): String {
        return """
            |#!/usr/bin/env sh
            |if [ "${'$'}#" -gt 0 ]; then
            |  case " ${'$'}* " in
            |    *" --help "*|*" -h "*")
            |      exec /usr/bin/${commandName} "${'$'}@"
            |      ;;
            |  esac
            |fi
            |cat >&2 <<'EOF'
            |KFShell runs Ubuntu in Android/proot. Netlink socket introspection is restricted here.
            |The ss/ip/netstat family can return empty or misleading results even when services are running.
            |
            |Do not use this output to decide whether a service is listening.
            |Use one of these instead:
            |  curl http://127.0.0.1:<port>
            |  kf-host check-bind <address> <port>
            |  kf-env netlink
            |EOF
            |echo "requested command: ${commandName} ${'$'}*" >&2
            |exit 1
        """.trimMargin() + "\n"
    }

    private fun buildProotBoundaryShimScript(): String {
        return """
            |#!/usr/bin/env sh
            |set +e
            |
            |CONTRACT_FILE="${'$'}{KF_PROOT_LAUNCH_CONTRACT_PATH:-${CONTAINER_PROOT_LAUNCH_CONTRACT_PATH}}"
            |REQUEST_FILE="${'$'}{KF_PROOT_LAUNCH_REQUEST_PATH:-${CONTAINER_PROOT_LAUNCH_REQUEST_PATH}}"
            |SHIM_MODE="${'$'}{KF_PROOT_SHIM_MODE:-android_owned_refuse_direct_launch}"
            |
            |print_status() {
            |  cat <<EOF
            |KFSHELL_PROOT_SHIM_BEGIN
            |mode=${'$'}SHIM_MODE
            |authority=${'$'}{KF_PROOT_LAUNCH_AUTHORITY:-android_control_plane}
            |owner=${'$'}{KF_PROOT_LAUNCH_OWNER:-android_apk}
            |boundary=${'$'}{KF_PROOT_EXECUTION_BOUNDARY:-ubuntu_declares_android_launches}
            |request_mode=${'$'}{KF_PROOT_REQUEST_MODE:-ubuntu_intent_advisory_only}
            |contract_path=${'$'}CONTRACT_FILE
            |request_path=${'$'}REQUEST_FILE
            |direct_launch=disabled
            |reason=PRoot executable/rootfs/binds/network are owned by Android KF control plane.
            |hint=Use kf-env proot, proot contract, or proot request to inspect the boundary.
            |KFSHELL_PROOT_SHIM_END
            |EOF
            |}
            |
            |print_contract() {
            |  print_status
            |  echo "KFSHELL_PROOT_SHIM_CONTRACT_JSON_BEGIN"
            |  if [ -r "${'$'}CONTRACT_FILE" ]; then
            |    cat "${'$'}CONTRACT_FILE"
            |  else
            |    echo "state=contract_missing"
            |    echo "reason=Android has not published a PRoot launch contract yet."
            |  fi
            |  echo "KFSHELL_PROOT_SHIM_CONTRACT_JSON_END"
            |}
            |
            |print_request() {
            |  print_status
            |  echo "KFSHELL_PROOT_SHIM_REQUEST_JSON_BEGIN"
            |  if [ -r "${'$'}REQUEST_FILE" ]; then
            |    cat "${'$'}REQUEST_FILE"
            |  else
            |    echo "state=request_missing"
            |    echo "reason=Ubuntu advisory request file has not been created yet."
            |  fi
            |  echo "KFSHELL_PROOT_SHIM_REQUEST_JSON_END"
            |}
            |
            |usage() {
            |  cat <<'EOF'
            |KFShell owns PRoot launch from the Android/KF control plane.
            |
            |Allowed inspection commands:
            |  proot status
            |  proot contract
            |  proot request
            |  kf-env proot
            |
            |Direct Ubuntu-side PRoot launch is disabled to prevent multiple launch truth sources.
            |Write advisory intent to /workspace/.kf/proot-launch-request.json if a tool needs to declare lane/purpose.
            |EOF
            |}
            |
            |case "${'$'}{1:-status}" in
            |  status)
            |    print_status
            |    ;;
            |  contract)
            |    print_contract
            |    ;;
            |  request)
            |    print_request
            |    ;;
            |  -h|--help|help)
            |    usage
            |    ;;
            |  *)
            |    print_status >&2
            |    cat >&2 <<'EOF'
            |
            |KFShell refused direct proot launch from Ubuntu.
            |Android/KF owns executable selection, rootfs path, bind mounts, loader, cwd and network exposure.
            |This prevents AI/scripts from bypassing the launch contract and creating a second PRoot control plane.
            |EOF
            |    exit 125
            |    ;;
            |esac
        """.trimMargin() + "\n"
    }

    private fun buildSupervisorctlWrapperScript(): String {
        return """
            |#!/usr/bin/env sh
            |for arg in "${'$'}@"; do
            |  case "${'$'}arg" in
            |    shutdown)
            |      cat >&2 <<'EOF'
            |KFShell: supervisor daemon shutdown is blocked.
            |supervisord is the container core service and is owned by the Android control plane.
            |Use supervisorctl status/start/stop for child programs, or stop the KF runtime from the app.
            |EOF
            |      exit 1
            |      ;;
            |  esac
            |done
            |
            |exec /usr/bin/supervisorctl "${'$'}@"
        """.trimMargin() + "\n"
    }

    internal fun buildSupervisordHealthSnapshotScript(): String {
        return """
            |#!/usr/bin/env sh
            |# KF_GENERATED_SUPERVISORD_HEALTH_SNAPSHOT_VERSION=1
            |set +e
            |
            |if [ "${'$'}#" -ne 0 ]; then
            |  echo "kf-supervisord-health-snapshot accepts no arguments" >&2
            |  exit 64
            |fi
            |if [ ! -x /usr/bin/supervisorctl ]; then
            |  echo "supervisorctl missing"
            |  exit 127
            |fi
            |
            |/usr/bin/supervisorctl -c /etc/supervisor/supervisord.conf -s "http://127.0.0.1:19001" update >/dev/null 2>&1 || true
            |/usr/bin/supervisorctl -c /etc/supervisor/supervisord.conf -s "http://127.0.0.1:19001" status 2>&1
            |status_exit=${'$'}?
            |echo "__KF_SUPERVISOR_LOGS__"
            |for f in /var/log/supervisor/*.log; do
            |  [ -f "${'$'}f" ] || continue
            |  echo "__KF_LOG_FILE__:${'$'}f"
            |  /usr/bin/tail -n 8 "${'$'}f" 2>/dev/null
            |done
            |exit "${'$'}status_exit"
        """.trimMargin() + "\n"
    }

    private fun buildAdbCheckScript(): String {
        return """
            |#!/usr/bin/env sh
            |set +e
            |
            |echo "KFSHELL_ADB_CHECK_BEGIN"
            |if ! command -v adb >/dev/null 2>&1; then
            |  echo "FAIL adb.client missing"
            |  echo "hint: run KFShell tool environment repair to install the managed adb client."
            |  echo "KFSHELL_ADB_CHECK_END"
            |  exit 2
            |fi
            |
            |ADB_PATH="$(command -v adb)"
            |echo "PASS adb.path ${'$'}ADB_PATH"
            |if timeout -k 2s 5s adb version >/tmp/kf-adb-version.out 2>/tmp/kf-adb-version.err; then
            |  cat /tmp/kf-adb-version.out /tmp/kf-adb-version.err | sed 's/^/INFO adb.version /'
            |else
            |  echo "WARN adb.version_probe timeout_or_failed"
            |  cat /tmp/kf-adb-version.out /tmp/kf-adb-version.err | sed 's/^/INFO adb.version /'
            |fi
            |HOST_SELF_SERIAL="${'$'}{KF_ADB_HOST_SELF_SERIAL:-kf-host-self}"
            |if [ -n "${'$'}HOST_SELF_SERIAL" ]; then
            |  echo "INFO adb.host_self ${'$'}HOST_SELF_SERIAL"
            |  echo "INFO adb.mode ${'$'}{KF_ADB_MODE:-apk_bridge}"
            |  echo "INFO adb.bridge_status ${'$'}{KF_ADB_BRIDGE_STATUS:-listed}"
            |else
            |  echo "WARN adb.host_self missing_bridge_contract"
            |fi
            |echo "INFO adb.devices"
            |if ! timeout -k 2s 8s adb devices 2>&1; then
            |  echo "WARN adb.devices_probe timeout_or_failed"
            |fi
            |
            |DEVICE_LIST="$(timeout -k 2s 8s adb devices 2>/dev/null || true)"
            |HOST_SELF_DEVICE="$(printf '%s\n' "${'$'}DEVICE_LIST" | awk 'NR > 1 && ${'$'}1 == "kf-host-self" && ${'$'}2 == "device" { print ${'$'}1; exit }')"
            |STANDARD_DEVICE="$(printf '%s\n' "${'$'}DEVICE_LIST" | awk 'NR > 1 && ${'$'}1 != "kf-host-self" && ${'$'}1 != "host-self-adb" && ${'$'}2 == "device" { print ${'$'}1; exit }')"
            |if [ -n "${'$'}HOST_SELF_DEVICE" ]; then
            |  echo "PASS adb.host_self_listed ${'$'}HOST_SELF_DEVICE"
            |fi
            |if [ -n "${'$'}STANDARD_DEVICE" ]; then
            |  echo "PASS adb.standard_connected ${'$'}STANDARD_DEVICE"
            |  timeout -k 2s 8s adb -s "${'$'}STANDARD_DEVICE" shell echo ok 2>&1 | sed 's/^/INFO adb.standard_shell.echo /'
            |  echo "KFSHELL_ADB_CHECK_END"
            |  exit 0
            |fi
            |
            |echo "WARN adb.standard_connected none"
            |cat <<'EOF'
            |hint: kf-host-self is a virtual APK bridge target and should be routed through kf-adb-bridge.
            |hint: standard external devices still use normal adb pair/connect/devices semantics.
            |hint: if no external device is listed, connect one normally:
            |  adb pair <phone-ip>:<pairing-port>
            |  adb connect <phone-ip>:<adb-port>
            |  adb devices
            |
            |This diagnostic is intentionally read-only:
            |- it does not run adb kill-server
            |- it does not run adb tcpip
            |- it does not store pairing codes
            |- it does not remove existing PC/USB ADB authorizations
            |EOF
            |echo "KFSHELL_ADB_CHECK_END"
            |[ -n "${'$'}HOST_SELF_DEVICE" ] && exit 0
            |exit 1
        """.trimMargin() + "\n"
    }

    private fun buildAdbClientWrapperScript(): String {
        return """
            |#!/usr/bin/env sh
            |REAL_ADB="${'$'}{KF_REAL_ADB:-/usr/bin/adb}"
            |
            |first_subcommand() {
            |  expect_value=0
            |  for arg in "${'$'}@"; do
            |    if [ "${'$'}expect_value" -eq 1 ]; then
            |      expect_value=0
            |      continue
            |    fi
            |    case "${'$'}arg" in
            |      --)
            |        break
            |        ;;
            |      -s|-t|-H|-P|-L)
            |        expect_value=1
            |        ;;
            |      -a|-d|-e)
            |        ;;
            |      -*)
            |        ;;
            |      *)
            |        printf '%s' "${'$'}arg"
            |        return 0
            |        ;;
            |    esac
            |  done
            |  return 1
            |}
            |
            |selected_serial() {
            |  expect_serial=0
            |  for arg in "${'$'}@"; do
            |    if [ "${'$'}expect_serial" -eq 1 ]; then
            |      printf '%s' "${'$'}arg"
            |      return 0
            |    fi
            |    case "${'$'}arg" in
            |      -s)
            |        expect_serial=1
            |        ;;
            |      -s*)
            |        printf '%s' "${'$'}{arg#-s}"
            |        return 0
            |        ;;
            |    esac
            |  done
            |  return 1
            |}
            |
            |is_host_self_target() {
            |  case "${'$'}1" in
            |    "${'$'}{KF_ADB_HOST_SELF_SERIAL:-kf-host-self}"|kf-host-self|host-self-adb)
            |      return 0
            |      ;;
            |    *)
            |      return 1
            |      ;;
            |  esac
            |}
            |
            |print_devices_with_host_self() {
            |  echo "List of devices attached"
            |  printf '%s\tdevice\n' "${'$'}{KF_ADB_HOST_SELF_SERIAL:-kf-host-self}"
            |  if [ "${'$'}{KF_ADB_INCLUDE_STANDARD_DEVICES:-1}" = "1" ] && [ -x "${'$'}REAL_ADB" ]; then
            |    timeout -k 2s "${'$'}{KF_ADB_STANDARD_SCAN_TIMEOUT_SEC:-3}s" "${'$'}REAL_ADB" devices 2>/dev/null \
            |      | awk 'NR > 1 && ${'$'}1 != "kf-host-self" && ${'$'}1 != "host-self-adb" && NF > 0 { print }'
            |  fi
            |}
            |
            |subcommand="$(first_subcommand "${'$'}@" || true)"
            |target="$(selected_serial "${'$'}@" || true)"
            |
            |case "${'$'}subcommand" in
            |  devices)
            |    print_devices_with_host_self "${'$'}@"
            |    exit 0
            |    ;;
            |esac
            |
            |if [ -n "${'$'}target" ] && is_host_self_target "${'$'}target"; then
            |  exec kf-adb-bridge adb "${'$'}@"
            |fi
            |
            |if [ -x "${'$'}REAL_ADB" ]; then
            |  exec "${'$'}REAL_ADB" "${'$'}@"
            |fi
            |
            |echo "adb: real adb binary not found at ${'$'}REAL_ADB" >&2
            |echo "hint: run KFShell tool environment repair to install adb." >&2
            |exit 127
        """.trimMargin() + "\n"
    }

    private fun buildFastbootClientWrapperScript(): String {
        return """
            |#!/usr/bin/env sh
            |REAL_FASTBOOT="${'$'}{KF_REAL_FASTBOOT:-/usr/bin/fastboot}"
            |if [ -x "${'$'}REAL_FASTBOOT" ]; then
            |  exec "${'$'}REAL_FASTBOOT" "${'$'}@"
            |fi
            |echo "fastboot: real fastboot binary not found at ${'$'}REAL_FASTBOOT" >&2
            |echo "hint: rebuild Kite offline rootfs with fastboot included." >&2
            |exit 127
        """.trimMargin() + "\n"
    }

    private fun buildAdbBridgeScript(): String {
        return """
            |#!/usr/bin/env sh
            |set +e
            |
            |print_status() {
            |  echo "KFSHELL_ADB_BRIDGE_BEGIN"
            |  echo "mode=${'$'}{KF_ADB_MODE:-apk_bridge}"
            |  echo "host_self_serial=${'$'}{KF_ADB_HOST_SELF_SERIAL:-kf-host-self}"
            |  echo "host_self_source=${'$'}{KF_ADB_HOST_SELF_SOURCE:-apk_bridge_contract_v0}"
            |  echo "bridge_status=${'$'}{KF_ADB_BRIDGE_STATUS:-listed}"
            |  echo "bridge_dir=${'$'}{KF_ADB_BRIDGE_DIR:-/workspace/.kf/adb-bridge}"
            |  echo "permission_source=${'$'}{KF_ADB_PERMISSION_SOURCE:-apk_bridge_contract_v0}"
            |  echo "shizuku_available=${'$'}{KF_ADB_SHIZUKU_AVAILABLE:-false}"
            |  echo "shizuku_permission=${'$'}{KF_ADB_SHIZUKU_PERMISSION:-unknown}"
            |  echo "shizuku_uid=${'$'}{KF_ADB_SHIZUKU_UID:-}"
            |  echo "shizuku_version=${'$'}{KF_ADB_SHIZUKU_VERSION:-}"
            |  echo "shizuku_error=${'$'}{KF_ADB_SHIZUKU_ERROR:-}"
            |  echo "proot_server_default=${'$'}{KF_ADB_PROOT_SERVER_DEFAULT:-disabled}"
            |  echo "request_side=container"
            |  echo "receiver_side=android_apk"
            |  echo "scan_required=false"
            |  echo "standard_adb_client=$(command -v adb 2>/dev/null || true)"
            |  echo "KFSHELL_ADB_BRIDGE_END"
            |}
            |
            |print_devices() {
            |  cat <<EOF
            |List of KFShell ADB bridge targets
            |${'$'}{KF_ADB_HOST_SELF_SERIAL:-kf-host-self}	apk-bridge	${'$'}{KF_ADB_BRIDGE_STATUS:-listed}
            |EOF
            |}
            |
            |first_subcommand() {
            |  expect_value=0
            |  for arg in "${'$'}@"; do
            |    if [ "${'$'}expect_value" -eq 1 ]; then
            |      expect_value=0
            |      continue
            |    fi
            |    case "${'$'}arg" in
            |      --)
            |        break
            |        ;;
            |      -s|-t|-H|-P|-L)
            |        expect_value=1
            |        ;;
            |      -a|-d|-e)
            |        ;;
            |      -*)
            |        ;;
            |      *)
            |        printf '%s' "${'$'}arg"
            |        return 0
            |        ;;
            |    esac
            |  done
            |  return 1
            |}
            |
            |shell_command_after_shell() {
            |  seen_shell=0
            |  expect_value=0
            |  command=""
            |  for arg in "${'$'}@"; do
            |    if [ "${'$'}seen_shell" -eq 1 ]; then
            |      if [ -z "${'$'}command" ]; then
            |        command="${'$'}arg"
            |      else
            |        command="${'$'}command ${'$'}arg"
            |      fi
            |      continue
            |    fi
            |    if [ "${'$'}expect_value" -eq 1 ]; then
            |      expect_value=0
            |      continue
            |    fi
            |    case "${'$'}arg" in
            |      -s|-t|-H|-P|-L)
            |        expect_value=1
            |        ;;
            |      shell)
            |        seen_shell=1
            |        ;;
            |    esac
            |  done
            |  [ "${'$'}seen_shell" -eq 1 ] || return 1
            |  printf '%s' "${'$'}command"
            |}
            |
            |decode_b64_file_value() {
            |  key="${'$'}1"
            |  file="${'$'}2"
            |  value="$(grep "^${'$'}key=" "${'$'}file" | tail -n 1 | sed "s/^${'$'}key=//")"
            |  if command -v base64 >/dev/null 2>&1; then
            |    printf '%s' "${'$'}value" | base64 -d 2>/dev/null || printf '%s' "${'$'}value" | base64 --decode 2>/dev/null
            |  fi
            |}
            |
            |decode_b64_file_value_to_file() {
            |  key="${'$'}1"
            |  file="${'$'}2"
            |  target="${'$'}3"
            |  value="$(grep "^${'$'}key=" "${'$'}file" | tail -n 1 | sed "s/^${'$'}key=//")"
            |  if command -v base64 >/dev/null 2>&1; then
            |    printf '%s' "${'$'}value" | base64 -d > "${'$'}target" 2>/dev/null || printf '%s' "${'$'}value" | base64 --decode > "${'$'}target" 2>/dev/null
            |  fi
            |}
            |
            |encode_b64_text() {
            |  printf '%s' "${'$'}1" | base64 | tr -d '\n'
            |}
            |
            |encode_b64_file() {
            |  base64 "${'$'}1" | tr -d '\n'
            |}
            |
            |submit_bridge_request() {
            |  kind="${'$'}1"
            |  command="${'$'}2"
            |  remote="${'$'}3"
            |  data_file="${'$'}4"
            |  output_file="${'$'}5"
            |  bridge_dir="${'$'}{KF_ADB_BRIDGE_DIR:-/workspace/.kf/adb-bridge}"
            |  request_dir="${'$'}bridge_dir/requests"
            |  response_dir="${'$'}bridge_dir/responses"
            |  mkdir -p "${'$'}request_dir" "${'$'}response_dir"
            |  request_id="req-$(date +%s%N 2>/dev/null || date +%s)-${'$'}${'$'}"
            |  request_file="${'$'}request_dir/${'$'}request_id.req"
            |  response_file="${'$'}response_dir/${'$'}request_id.resp"
            |  stdout_stream="${'$'}response_dir/${'$'}request_id.stdout.stream"
            |  stderr_stream="${'$'}response_dir/${'$'}request_id.stderr.stream"
            |  cancel_file="${'$'}response_dir/${'$'}request_id.cancel"
            |  stdout_pos=0
            |  stderr_pos=0
            |
            |  flush_streams() {
            |    if [ -f "${'$'}stdout_stream" ]; then
            |      size="$(wc -c < "${'$'}stdout_stream" 2>/dev/null | tr -d ' ')"
            |      case "${'$'}size" in ''|*[!0-9]*) size=0 ;; esac
            |      if [ "${'$'}size" -gt "${'$'}stdout_pos" ]; then
            |        if [ -z "${'$'}output_file" ]; then
            |          tail -c +$((stdout_pos + 1)) "${'$'}stdout_stream"
            |        fi
            |        stdout_pos="${'$'}size"
            |      fi
            |    fi
            |    if [ -f "${'$'}stderr_stream" ]; then
            |      size="$(wc -c < "${'$'}stderr_stream" 2>/dev/null | tr -d ' ')"
            |      case "${'$'}size" in ''|*[!0-9]*) size=0 ;; esac
            |      if [ "${'$'}size" -gt "${'$'}stderr_pos" ]; then
            |        tail -c +$((stderr_pos + 1)) "${'$'}stderr_stream" >&2
            |        stderr_pos="${'$'}size"
            |      fi
            |    fi
            |  }
            |
            |  cancel_bridge_request() {
            |    touch "${'$'}cancel_file" 2>/dev/null || true
            |    rm -f "${'$'}request_file" "${'$'}request_file.tmp"
            |    flush_streams
            |    exit 130
            |  }
            |  trap cancel_bridge_request INT TERM
            |
            |  {
            |    echo "id=${'$'}request_id"
            |    echo "kind=${'$'}kind"
            |    [ -n "${'$'}command" ] && echo "command_b64=$(encode_b64_text "${'$'}command")"
            |    [ -n "${'$'}remote" ] && echo "remote_b64=$(encode_b64_text "${'$'}remote")"
            |    [ -n "${'$'}data_file" ] && echo "data_b64=$(encode_b64_file "${'$'}data_file")"
            |  } > "${'$'}request_file.tmp"
            |  mv "${'$'}request_file.tmp" "${'$'}request_file"
            |
            |  while :; do
            |    flush_streams
            |    if [ -f "${'$'}response_file" ]; then
            |      stdout_file="${'$'}response_file.stdout"
            |      stderr_file="${'$'}response_file.stderr"
            |      decode_b64_file_value_to_file stdout_b64 "${'$'}response_file" "${'$'}stdout_file"
            |      decode_b64_file_value_to_file stderr_b64 "${'$'}response_file" "${'$'}stderr_file"
            |      exit_code="$(grep '^exit_code=' "${'$'}response_file" | tail -n 1 | sed 's/^exit_code=//')"
            |      rm -f "${'$'}response_file"
            |      if [ -n "${'$'}output_file" ]; then
            |        cat "${'$'}stdout_file" > "${'$'}output_file"
            |      elif [ -s "${'$'}stdout_file" ]; then
            |        size="$(wc -c < "${'$'}stdout_file" 2>/dev/null | tr -d ' ')"
            |        case "${'$'}size" in ''|*[!0-9]*) size=0 ;; esac
            |        [ "${'$'}size" -gt "${'$'}stdout_pos" ] && tail -c +$((stdout_pos + 1)) "${'$'}stdout_file"
            |      fi
            |      if [ -s "${'$'}stderr_file" ]; then
            |        size="$(wc -c < "${'$'}stderr_file" 2>/dev/null | tr -d ' ')"
            |        case "${'$'}size" in ''|*[!0-9]*) size=0 ;; esac
            |        [ "${'$'}size" -gt "${'$'}stderr_pos" ] && tail -c +$((stderr_pos + 1)) "${'$'}stderr_file" >&2
            |      fi
            |      rm -f "${'$'}stdout_file" "${'$'}stderr_file" "${'$'}stdout_stream" "${'$'}stderr_stream" "${'$'}cancel_file"
            |      case "${'$'}exit_code" in
            |        ''|*[!0-9]*) return 125 ;;
            |        *) return "${'$'}exit_code" ;;
            |      esac
            |    fi
            |    sleep 0.15
            |  done
            |}
            |
            |submit_shell_request() {
            |  command="$(shell_command_after_shell "${'$'}@" || true)"
            |  if [ -z "${'$'}command" ]; then
            |    echo "error: interactive adb shell is not supported for ${'$'}{KF_ADB_HOST_SELF_SERIAL:-kf-host-self}" >&2
            |    return 125
            |  fi
            |  submit_bridge_request shell "${'$'}command" "" "" ""
            |}
            |
            |command_after_subcommand() {
            |  target_name="${'$'}1"
            |  shift
            |  seen=0
            |  command=""
            |  for arg in "${'$'}@"; do
            |    if [ "${'$'}seen" -eq 1 ]; then
            |      if [ -z "${'$'}command" ]; then command="${'$'}arg"; else command="${'$'}command ${'$'}arg"; fi
            |      continue
            |    fi
            |    [ "${'$'}arg" = "${'$'}target_name" ] && seen=1
            |  done
            |  printf '%s' "${'$'}command"
            |}
            |
            |local_basename() {
            |  path="${'$'}1"
            |  path="${'$'}{path%/}"
            |  base="${'$'}{path##*/}"
            |  [ -n "${'$'}base" ] && printf '%s' "${'$'}base" || printf '%s' "adb-pull.out"
            |}
            |
            |submit_pull_request() {
            |  remote="$(command_after_subcommand pull "${'$'}@" | awk '{ print ${'$'}1 }')"
            |  local_path="$(command_after_subcommand pull "${'$'}@" | awk '{ print ${'$'}2 }')"
            |  if [ -z "${'$'}remote" ]; then
            |    echo "error: adb pull requires a remote path" >&2
            |    return 125
            |  fi
            |  if [ -z "${'$'}local_path" ]; then
            |    local_path="$(local_basename "${'$'}remote")"
            |  elif [ -d "${'$'}local_path" ]; then
            |    local_path="${'$'}local_path/$(local_basename "${'$'}remote")"
            |  fi
            |  submit_bridge_request pull "" "${'$'}remote" "" "${'$'}local_path"
            |}
            |
            |submit_push_request() {
            |  args="$(command_after_subcommand push "${'$'}@")"
            |  local_path="$(printf '%s\n' "${'$'}args" | awk '{ print ${'$'}1 }')"
            |  remote="$(printf '%s\n' "${'$'}args" | awk '{ print ${'$'}2 }')"
            |  if [ -z "${'$'}local_path" ] || [ -z "${'$'}remote" ]; then
            |    echo "error: adb push requires local and remote paths" >&2
            |    return 125
            |  fi
            |  if [ ! -f "${'$'}local_path" ]; then
            |    echo "error: local file not found: ${'$'}local_path" >&2
            |    return 1
            |  fi
            |  submit_bridge_request push "" "${'$'}remote" "${'$'}local_path" ""
            |}
            |
            |submit_install_request() {
            |  args="$(command_after_subcommand install "${'$'}@")"
            |  apk=""
            |  flags=""
            |  for arg in ${'$'}args; do
            |    if [ -f "${'$'}arg" ]; then
            |      apk="${'$'}arg"
            |    else
            |      if [ -z "${'$'}flags" ]; then flags="${'$'}arg"; else flags="${'$'}flags ${'$'}arg"; fi
            |    fi
            |  done
            |  if [ -z "${'$'}apk" ]; then
            |    echo "error: adb install requires a local APK path" >&2
            |    return 125
            |  fi
            |  submit_bridge_request install "${'$'}flags" "" "${'$'}apk" ""
            |}
            |
            |bridge_adb() {
            |  subcommand="$(first_subcommand "${'$'}@" || true)"
            |  case "${'$'}subcommand" in
            |    get-state)
            |      echo "device"
            |      ;;
            |    get-serialno)
            |      echo "${'$'}{KF_ADB_HOST_SELF_SERIAL:-kf-host-self}"
            |      ;;
            |    get-devpath)
            |      echo "kf-host-self:apk-bridge"
            |      ;;
            |    devices)
            |      echo "List of devices attached"
            |      printf '%s\tdevice\n' "${'$'}{KF_ADB_HOST_SELF_SERIAL:-kf-host-self}"
            |      ;;
            |    shell)
            |      submit_shell_request "${'$'}@"
            |      ;;
            |    exec-out)
            |      command="$(command_after_subcommand exec-out "${'$'}@" || true)"
            |      [ -n "${'$'}command" ] || { echo "error: adb exec-out requires a command" >&2; return 125; }
            |      submit_bridge_request exec-out "${'$'}command" "" "" ""
            |      ;;
            |    logcat)
            |      command="logcat $(command_after_subcommand logcat "${'$'}@")"
            |      submit_bridge_request shell "${'$'}command" "" "" ""
            |      ;;
            |    bugreport)
            |      command="bugreport $(command_after_subcommand bugreport "${'$'}@")"
            |      submit_bridge_request shell "${'$'}command" "" "" ""
            |      ;;
            |    pull)
            |      submit_pull_request "${'$'}@"
            |      ;;
            |    push)
            |      submit_push_request "${'$'}@"
            |      ;;
            |    install)
            |      submit_install_request "${'$'}@"
            |      ;;
            |    uninstall)
            |      command="pm uninstall $(command_after_subcommand uninstall "${'$'}@")"
            |      submit_bridge_request shell "${'$'}command" "" "" ""
            |      ;;
            |    wait-for-device|wait-for-any|wait-for-local|wait-for-usb)
            |      return 0
            |      ;;
            |    reconnect)
            |      mode="$(command_after_subcommand reconnect "${'$'}@" || true)"
            |      case "${'$'}mode" in
            |        ""|device|offline)
            |          echo "reconnected ${'$'}{KF_ADB_HOST_SELF_SERIAL:-kf-host-self}"
            |          return 0
            |          ;;
            |        *)
            |          echo "error: unsupported host-self adb reconnect mode: ${'$'}mode" >&2
            |          return 125
            |          ;;
            |      esac
            |      ;;
            |    features)
            |      echo "shell_v2"
            |      echo "cmd"
            |      echo "stat_v2"
            |      echo "fixed_push_mkdir"
            |      ;;
            |    *)
            |      echo "error: unsupported host-self adb bridge command: ${'$'}subcommand" >&2
            |      echo "hint: supported now: devices, get-state, get-serialno, get-devpath, shell, exec-out, logcat, bugreport, pull, push, install, uninstall, wait-for-device, reconnect, features." >&2
            |      return 125
            |      ;;
            |  esac
            |}
            |
            |case "${'$'}{1:-status}" in
            |  status|doctor)
            |    print_status
            |    ;;
            |  devices|targets)
            |    print_devices
            |    ;;
            |  contract)
            |    cat <<'EOF'
            |Host-self ADB is not discovered by scanning from Ubuntu/proot.
            |The container is the request side; Android APK is the receiver side.
            |A future bridge may expose a standard adb-server compatible endpoint,
            |but proot adb fork-server is not the host-self control lane.
            |EOF
            |    ;;
            |  adb)
            |    shift
            |    bridge_adb "${'$'}@"
            |    ;;
            |  -h|--help|help)
            |    cat <<'EOF'
            |Usage:
            |  kf-adb-bridge status
            |  kf-adb-bridge devices
            |  kf-adb-bridge contract
            |EOF
            |    ;;
            |  *)
            |    echo "unknown subcommand: ${'$'}1" >&2
            |    exit 2
            |    ;;
            |esac
        """.trimMargin() + "\n"
    }

    private fun buildAndroidShellBridgeScript(): String {
        return """
            |#!/usr/bin/env sh
            |set +e
            |
            |BRIDGE_DIR="${'$'}{KF_ANDROID_SH_BRIDGE_DIR:-/workspace/.kf/android-shell-bridge}"
            |
            |usage() {
            |  cat <<'EOF'
            |Usage:
            |  kf-android-sh [-e KEY=VALUE] '<android shell command>'
            |  kf-android-sh [-e KEY=VALUE] /storage/emulated/0/Download/script.sh
            |
            |Runs the command or script with Android /system/bin/sh as the Kite APK user.
            |It is not ADB, root, or Shizuku.
            |EOF
            |}
            |
            |need_base64() {
            |  command -v base64 >/dev/null 2>&1 || {
            |    echo "error: kf-android-sh requires base64 in Ubuntu" >&2
            |    exit 127
            |  }
            |}
            |
            |encode_b64_text() {
            |  printf '%s' "${'$'}1" | base64 | tr -d '\n'
            |}
            |
            |encode_b64_file() {
            |  base64 "${'$'}1" | tr -d '\n'
            |}
            |
            |decode_b64_file_value_to_file() {
            |  key="${'$'}1"
            |  file="${'$'}2"
            |  target="${'$'}3"
            |  value="$(grep "^${'$'}key=" "${'$'}file" | tail -n 1 | sed "s/^${'$'}key=//")"
            |  printf '%s' "${'$'}value" | base64 -d > "${'$'}target" 2>/dev/null || printf '%s' "${'$'}value" | base64 --decode > "${'$'}target" 2>/dev/null
            |}
            |
            |submit_request() {
            |  mode="${'$'}1"
            |  command_text="${'$'}2"
            |  script_file="${'$'}3"
            |  env_text="${'$'}4"
            |  request_dir="${'$'}BRIDGE_DIR/requests"
            |  response_dir="${'$'}BRIDGE_DIR/responses"
            |  mkdir -p "${'$'}request_dir" "${'$'}response_dir"
            |  request_id="req-$(date +%s%N 2>/dev/null || date +%s)-${'$'}${'$'}"
            |  request_file="${'$'}request_dir/${'$'}request_id.req"
            |  response_file="${'$'}response_dir/${'$'}request_id.resp"
            |  stdout_stream="${'$'}response_dir/${'$'}request_id.stdout.stream"
            |  stderr_stream="${'$'}response_dir/${'$'}request_id.stderr.stream"
            |  cancel_file="${'$'}response_dir/${'$'}request_id.cancel"
            |  stdout_pos=0
            |  stderr_pos=0
            |
            |  flush_streams() {
            |    if [ -f "${'$'}stdout_stream" ]; then
            |      size="$(wc -c < "${'$'}stdout_stream" 2>/dev/null | tr -d ' ')"
            |      case "${'$'}size" in ''|*[!0-9]*) size=0 ;; esac
            |      if [ "${'$'}size" -gt "${'$'}stdout_pos" ]; then
            |        tail -c +$((stdout_pos + 1)) "${'$'}stdout_stream"
            |        stdout_pos="${'$'}size"
            |      fi
            |    fi
            |    if [ -f "${'$'}stderr_stream" ]; then
            |      size="$(wc -c < "${'$'}stderr_stream" 2>/dev/null | tr -d ' ')"
            |      case "${'$'}size" in ''|*[!0-9]*) size=0 ;; esac
            |      if [ "${'$'}size" -gt "${'$'}stderr_pos" ]; then
            |        tail -c +$((stderr_pos + 1)) "${'$'}stderr_stream" >&2
            |        stderr_pos="${'$'}size"
            |      fi
            |    fi
            |  }
            |
            |  cancel_request() {
            |    touch "${'$'}cancel_file" 2>/dev/null || true
            |    rm -f "${'$'}request_file" "${'$'}request_file.tmp"
            |    flush_streams
            |    exit 130
            |  }
            |  trap cancel_request INT TERM
            |
            |  {
            |    echo "id=${'$'}request_id"
            |    echo "mode=${'$'}mode"
            |    [ -n "${'$'}command_text" ] && echo "command_b64=$(encode_b64_text "${'$'}command_text")"
            |    [ -n "${'$'}script_file" ] && echo "script_b64=$(encode_b64_file "${'$'}script_file")"
            |    [ -n "${'$'}env_text" ] && echo "env_b64=$(encode_b64_text "${'$'}env_text")"
            |  } > "${'$'}request_file.tmp"
            |  mv "${'$'}request_file.tmp" "${'$'}request_file"
            |
            |  while :; do
            |    flush_streams
            |    if [ -f "${'$'}response_file" ]; then
            |      stdout_file="${'$'}response_file.stdout"
            |      stderr_file="${'$'}response_file.stderr"
            |      decode_b64_file_value_to_file stdout_b64 "${'$'}response_file" "${'$'}stdout_file"
            |      decode_b64_file_value_to_file stderr_b64 "${'$'}response_file" "${'$'}stderr_file"
            |      exit_code="$(grep '^exit_code=' "${'$'}response_file" | tail -n 1 | sed 's/^exit_code=//')"
            |      rm -f "${'$'}response_file"
            |      if [ -s "${'$'}stdout_file" ]; then
            |        size="$(wc -c < "${'$'}stdout_file" 2>/dev/null | tr -d ' ')"
            |        case "${'$'}size" in ''|*[!0-9]*) size=0 ;; esac
            |        [ "${'$'}size" -gt "${'$'}stdout_pos" ] && tail -c +$((stdout_pos + 1)) "${'$'}stdout_file"
            |      fi
            |      if [ -s "${'$'}stderr_file" ]; then
            |        size="$(wc -c < "${'$'}stderr_file" 2>/dev/null | tr -d ' ')"
            |        case "${'$'}size" in ''|*[!0-9]*) size=0 ;; esac
            |        [ "${'$'}size" -gt "${'$'}stderr_pos" ] && tail -c +$((stderr_pos + 1)) "${'$'}stderr_file" >&2
            |      fi
            |      rm -f "${'$'}stdout_file" "${'$'}stderr_file" "${'$'}stdout_stream" "${'$'}stderr_stream" "${'$'}cancel_file"
            |      case "${'$'}exit_code" in
            |        ''|*[!0-9]*) return 125 ;;
            |        *) return "${'$'}exit_code" ;;
            |      esac
            |    fi
            |    sleep 0.15
            |  done
            |}
            |
            |need_base64
            |env_text=""
            |while [ "${'$'}#" -gt 0 ]; do
            |  case "${'$'}1" in
            |    -e)
            |      shift
            |      [ "${'$'}#" -gt 0 ] || { echo "error: -e requires KEY=VALUE" >&2; exit 2; }
            |      case "${'$'}1" in
            |        *=*) ;;
            |        *) echo "error: -e requires KEY=VALUE" >&2; exit 2 ;;
            |      esac
            |      if [ -z "${'$'}env_text" ]; then
            |        env_text="${'$'}1"
            |      else
            |        env_text="${'$'}env_text
            |${'$'}1"
            |      fi
            |      shift
            |      ;;
            |    --)
            |      shift
            |      break
            |      ;;
            |    -h|--help|help)
            |      usage
            |      exit 0
            |      ;;
            |    *)
            |      break
            |      ;;
            |  esac
            |done
            |
            |[ "${'$'}#" -gt 0 ] || { usage >&2; exit 2; }
            |
            |if [ "${'$'}#" -eq 1 ] && [ -f "${'$'}1" ]; then
            |  submit_request script "" "${'$'}1" "${'$'}env_text"
            |else
            |  submit_request command "${'$'}*" "" "${'$'}env_text"
            |fi
        """.trimMargin() + "\n"
    }

    private fun buildHostSurfaceScript(): String {
        return """
            |#!/usr/bin/env sh
            |set +e
            |
            |CONTRACT_PATH="${'$'}{KF_HOST_CONTRACT_PATH:-${CONTAINER_HOST_CONTRACT_PATH}}"
            |
            |print_contract() {
            |  if [ -f "${'$'}CONTRACT_PATH" ]; then
            |    cat "${'$'}CONTRACT_PATH"
            |  else
            |    echo "missing contract: ${'$'}CONTRACT_PATH" >&2
            |    return 1
            |  fi
            |}
            |
            |print_endpoints() {
            |  cat <<'EOF'
            |container-supervisor 127.0.0.1:19001 LOOPBACK_ONLY
            |openclaw-gateway 127.0.0.1:18789 LOOPBACK_ONLY
            |host-self-adb kf-host-self APK_BRIDGE_CONTRACT
            |EOF
            |}
            |
            |policy() {
            |  cat <<'EOF'
            |KFSHELL_HOST_POLICY_BEGIN
            |network: shared_host_stack
            |loopback: 127.0.0.1 is shared with Android host
            |ports: prefer >=1024; ports <1024 are privileged/sensitive and should not be used by default
            |bind: prefer 127.0.0.1 for local WebUI and agent services
            |bind: 0.0.0.0 is allowed only for explicit LAN exposure and should be treated as sensitive
            |adb: host-self target is kf-host-self through APK bridge; external devices use normal adb pair/connect
            |guidance: run kf-host check-bind <address> <port> before starting a service
            |KFSHELL_HOST_POLICY_END
            |EOF
            |}
            |
            |check_bind() {
            |  addr="${'$'}{1:-127.0.0.1}"
            |  port="${'$'}{2:-}"
            |  echo "KFSHELL_HOST_BIND_CHECK_BEGIN"
            |  echo "bind_address=${'$'}addr"
            |  echo "bind_port=${'$'}port"
            |  if ! printf '%s' "${'$'}port" | grep -Eq '^[0-9]+${'$'}'; then
            |    echo "FAIL port.invalid"
            |    echo "hint: usage: kf-host check-bind <address> <port>"
            |    echo "KFSHELL_HOST_BIND_CHECK_END"
            |    return 2
            |  fi
            |  if [ "${'$'}port" -lt 1024 ]; then
            |    echo "WARN port.privileged_low"
            |    echo "hint: use an unprivileged port >=1024, for example 18080 or 19000."
            |  else
            |    echo "PASS port.unprivileged"
            |  fi
            |  case "${'$'}addr" in
            |    127.*|localhost)
            |      echo "PASS exposure.loopback_only"
            |      echo "hint: Android host and container share loopback; host browser can usually open this local service."
            |      ;;
            |    0.0.0.0|::|[::])
            |      echo "WARN exposure.lan_all_interfaces"
            |      echo "hint: 0.0.0.0 exposes to reachable LAN interfaces. Use only when LAN access is intentional."
            |      echo "hint: prefer 127.0.0.1 for local-only WebUI, agent APIs, and debug servers."
            |      ;;
            |    *)
            |      echo "WARN exposure.specific_interface"
            |      echo "hint: verify this address belongs to the Android host network stack before binding."
            |      ;;
            |  esac
            |  echo "KFSHELL_HOST_BIND_CHECK_END"
            |}
            |
            |post_local_server() {
            |  endpoint="${'$'}1"
            |  body="${'$'}2"
            |  url="${'$'}{KF_HOST_LOCAL_SERVER_URL:-http://127.0.0.1:8791}${'$'}endpoint"
            |  if command -v curl >/dev/null 2>&1; then
            |    curl -fsS -X POST --data-binary "${'$'}body" "${'$'}url"
            |    return "${'$'}?"
            |  fi
            |  if command -v wget >/dev/null 2>&1; then
            |    wget -qO- --post-data="${'$'}body" "${'$'}url"
            |    return "${'$'}?"
            |  fi
            |  echo "error: kf-host needs curl or wget to call Android local server" >&2
            |  return 127
            |}
            |
            |install_apk() {
            |  apk_path="${'$'}1"
            |  if [ -z "${'$'}apk_path" ]; then
            |    echo "hint: usage: kf-host install-apk /workspace/project/app.apk" >&2
            |    return 2
            |  fi
            |  post_local_server "/install-apk" "${'$'}apk_path"
            |}
            |
            |doctor() {
            |  echo "KFSHELL_HOST_SURFACE_BEGIN"
            |  echo "network_mode=${'$'}{KF_HOST_NETWORK_MODE:-shared_host_stack}"
            |  echo "loopback=${'$'}{KF_HOST_LOOPBACK:-shared_with_android}"
            |  echo "port_policy=${'$'}{KF_HOST_PORT_POLICY:-prefer_127_0_0_1_and_ports_ge_1024}"
            |  echo "control_boundary=${'$'}{KF_HOST_CONTROL_BOUNDARY:-android_control_stays_in_apk}"
            |  echo "default_exposure=${'$'}{KF_HOST_DEFAULT_EXPOSURE:-LOOPBACK_ONLY}"
            |  echo "contract_path=${'$'}CONTRACT_PATH"
            |  echo "PATH=${'$'}PATH"
            |  echo "endpoints:"
            |  print_endpoints | sed 's/^/  /'
            |  echo "helpers:"
            |  if command -v kf-adb-check >/dev/null 2>&1; then
            |    echo "  adb_check=kf-adb-check"
            |  else
            |    echo "  adb_check=missing"
            |  fi
            |  if command -v kf-adb-bridge >/dev/null 2>&1; then
            |    echo "  adb_bridge=kf-adb-bridge"
            |  else
            |    echo "  adb_bridge=missing"
            |  fi
            |  echo "KFSHELL_HOST_SURFACE_END"
            |}
            |
            |usage() {
            |  cat <<'EOF'
            |Usage:
            |  kf-host doctor
            |  kf-host contract
            |  kf-host endpoints
            |  kf-host policy
            |  kf-host check-bind <address> <port>
            |  kf-host install-apk <apk-path>
            |
            |This is the stable host integration surface for AI and shell workflows.
            |EOF
            |}
            |
            |case "${'$'}{1:-doctor}" in
            |  doctor)
            |    doctor
            |    ;;
            |  contract)
            |    print_contract
            |    ;;
            |  endpoints)
            |    print_endpoints
            |    ;;
            |  policy)
            |    policy
            |    ;;
            |  check-bind|check-port)
            |    shift
            |    check_bind "${'$'}@"
            |    ;;
            |  install-apk|install_apk)
            |    shift
            |    install_apk "${'$'}@"
            |    ;;
            |  -h|--help|help)
            |    usage
            |    ;;
            |  *)
            |    echo "unknown subcommand: ${'$'}1" >&2
            |    usage >&2
            |    exit 2
            |    ;;
            |esac
        """.trimMargin() + "\n"
    }

    private fun buildHostContractJson(): String {
        return """
            |{
            |  "surfaceVersion": "v0",
            |  "networkMode": "shared_host_stack",
            |  "loopback": "shared_with_android",
            |  "portPolicy": "prefer_127_0_0_1_and_ports_ge_1024",
            |  "controlBoundary": "android_control_stays_in_apk",
            |  "defaultExposure": "LOOPBACK_ONLY",
            |  "prootLaunch": {
            |    "authority": "android_control_plane",
            |    "owner": "android_apk",
            |    "contractPath": "${CONTAINER_PROOT_LAUNCH_CONTRACT_PATH}",
            |    "requestPath": "${CONTAINER_PROOT_LAUNCH_REQUEST_PATH}",
            |    "boundary": "ubuntu_declares_android_launches",
            |    "requestMode": "ubuntu_intent_advisory_only",
            |    "telemetryMode": "debug_jsonl_lifecycle_v0"
            |  },
            |  "notes": [
            |    "Container and Android host share one network stack.",
            |    "Prefer 127.0.0.1 and ports >= 1024 for container services.",
            |    "Run kf-host check-bind <address> <port> before starting a local service.",
            |    "Android-specific control stays in the APK layer and is bridged narrowly.",
            |    "PRoot argv, rootfs, bind mounts, loader and network mode are owned by the Android control plane.",
            |    "AI should prefer stable shell-visible contracts instead of private button flows.",
            |    "Host-self ADB is a request/receiver bridge: container requests, Android APK receives; scanning itself from proot is not required."
            |  ],
            |  "policies": {
            |    "serviceBindDefault": "127.0.0.1",
            |    "minimumDefaultPort": 1024,
            |    "lowPorts": {
            |      "range": "1-1023",
            |      "status": "sensitive",
            |      "guidance": "Do not use by default. Prefer >=1024 unless a tool explicitly requires a privileged port."
            |    },
            |    "allInterfaces": {
            |      "addresses": ["0.0.0.0", "::", "[::]"],
            |      "status": "sensitive_allowed",
            |      "guidance": "Use only for intentional LAN exposure. Prefer 127.0.0.1 for local WebUI/debug APIs."
            |    },
            |    "loopback": {
            |      "addresses": ["127.0.0.1", "localhost"],
            |      "status": "recommended_default",
            |      "guidance": "Shared with Android host under proot host network mode."
            |    }
            |  },
            |  "endpoints": [
            |    {
            |      "id": "host-self-adb",
            |      "serial": "kf-host-self",
            |      "mode": "apk_bridge",
            |      "status": "listed",
            |      "scanRequired": false,
            |      "requestSide": "container",
            |      "receiverSide": "android_apk"
            |    },
            |    {
            |      "id": "container-supervisor",
            |      "bindAddress": "127.0.0.1",
            |      "port": 19001,
            |      "exposureScope": "LOOPBACK_ONLY"
            |    },
            |    {
            |      "id": "openclaw-gateway",
            |      "bindAddress": "127.0.0.1",
            |      "port": 18789,
            |      "exposureScope": "LOOPBACK_ONLY"
            |    }
            |  ],
            |  "tools": {
            |    "doctor": "kf-host doctor",
            |    "contract": "kf-host contract",
            |    "endpoints": "kf-host endpoints",
            |    "policy": "kf-host policy",
            |    "checkBind": "kf-host check-bind <address> <port>",
            |    "installApk": "kf-host install-apk <apk-path>",
            |    "envDoctor": "kf-env doctor",
            |    "envLimits": "kf-env limits",
            |    "envRuntime": "kf-env runtime",
            |    "envProot": "kf-env proot",
            |    "systemBin": "${CONTAINER_HELPER_SYSTEM_BIN_PATH}",
            |    "systemProc": "${CONTAINER_HELPER_SYSTEM_PROC_PATH}",
            |    "systemState": "${CONTAINER_HELPER_SYSTEM_STATE_PATH}",
            |    "prootLaunchContract": "${CONTAINER_PROOT_LAUNCH_CONTRACT_PATH}",
            |    "prootLaunchRequest": "${CONTAINER_PROOT_LAUNCH_REQUEST_PATH}",
            |    "runtimePressure": "${CONTAINER_RUNTIME_PRESSURE_PATH}",
            |    "runtimeProcessTable": "${CONTAINER_RUNTIME_PROCESS_TABLE_PATH}",
            |    "prootTelemetryEvents": "${CONTAINER_PROOT_TELEMETRY_EVENTS_PATH}",
            |    "runtimePolicy": "${CONTAINER_RUNTIME_RECLAIMER_POLICY_PATH}",
            |    "runtimeResidentPolicy": "${CONTAINER_RUNTIME_RESIDENT_POLICY_PATH}",
            |    "runtimeWorkloadPolicy": "${CONTAINER_RUNTIME_WORKLOAD_POLICY_PATH}",
            |    "runtimeWorkloadIntent": "${CONTAINER_RUNTIME_WORKLOAD_INTENT_PATH}",
            |    "adbCheck": "kf-adb-check",
            |    "adbBridge": "kf-adb-bridge"
            |  }
            |}
        """.trimMargin() + "\n"
    }

    private fun buildRuntimePressureUnknownSurface(): String {
        return """
            |KFSHELL_RUNTIME_PRESSURE_VERSION=1
            |source=workspace_bootstrap
            |level=UNKNOWN
            |active_profile=BALANCED
            |policy_path=${CONTAINER_RUNTIME_RECLAIMER_POLICY_PATH}
            |policy_loaded_at_ms=0
            |policy_load_status=bootstrap_unknown
            |policy_load_error=none
            |evaluated_at_ms=0
            |pressure_basis=bootstrap_unknown
            |memory_budget_kb=0
            |threshold_elevated_rss_kb=524288
            |threshold_high_rss_kb=1048576
            |threshold_critical_rss_kb=1572864
            |host_mem_total_kb=0
            |host_mem_available_kb=0
            |host_available_level=UNKNOWN
            |total_rss_kb=0
            |protected_rss_kb=0
            |reclaimable_rss_kb=0
            |unknown_rss_root_count=0
            |classified_unknown_root_count=0
            |candidate_count=0
            |policy_resident_classes=CRITICAL_CORE,RESIDENT,INTERACTIVE
            |policy_reclaim_order=registered_ephemeral_then_batch_then_classified_unknown
            |workload_policy_path=${CONTAINER_RUNTIME_WORKLOAD_POLICY_PATH}
            |workload_intent_path=${CONTAINER_RUNTIME_WORKLOAD_INTENT_PATH}
            |workload_policy_authority=android_control_plane
            |workload_policy_contract=proot_facts_kf_decides_ubuntu_declares
            |reclaimer_mode=android_control_plane_v0
            |reclaimer_trigger=depends_on_active_profile
            |admission_trigger=depends_on_active_profile
            |behavior_NORMAL=observe_only_no_auto_reclaim_no_admission_defer
            |behavior_ELEVATED=observe_and_refresh_low_priority_admission_may_defer_by_profile
            |behavior_HIGH=profile_may_reclaim_ephemeral_and_defer_low_priority
            |behavior_CRITICAL=profile_may_reclaim_batch_and_classified_unknown
            |unknown_rule_scope=unattributed_runtime_roots_only
            |unknown_rule_fields=TITLE,COMMAND,COMMAND_LINE,SOURCE_LABEL
            |unknown_rule_modes=CONTAINS,EXACT,PREFIX,SUFFIX
            |reason=android_runtime_pressure_not_published_yet
            |hint=refresh_runtime_or_run_kf_env_doctor_after_terminal_start
        """.trimMargin() + "\n"
    }

    private fun buildRuntimeProcessTableUnknownSurface(): String {
        return """
            |pid	ppid	pgid	sid	stat	rss_kb	vm_size_kb	cpu_time_ticks	comm	args	source	proot_pid	tracee_vpid	runtime_id	unit_id	started_at_ms	last_seen_ms	state	last_event_type	exit_code	signal	exited_at_ms	signaled_at_ms
        """.trimMargin() + "\n"
    }

    private fun buildProotTelemetryEventsUnknownSurface(): String {
        return """
            |timestamp_ms	event_type	proot_pid	tracee_pid	tracee_vpid	parent_tracee_pid	parent_tracee_vpid	process_group_id	session_id	source_hook	cost_level	executable	argv_hash	argv_preview	cwd	kf_runtime_id	kf_unit_id	exit_code	signal
        """.trimMargin() + "\n"
    }

    private fun buildProotLaunchContractUnknownSurface(): String {
        return """
            |{
            |  "version": 1,
            |  "kind": "UNKNOWN",
            |  "authority": "android_control_plane",
            |  "owner": "android_apk",
            |  "boundary": "ubuntu_declares_android_launches",
            |  "state": "not_published_yet",
            |  "telemetry": {
            |    "mode": "debug_jsonl_lifecycle_v0",
            |    "future": "path_pressure_and_aggregation_future"
            |  },
            |  "reason": "PRoot launch contract is published by Android when a container launch/exec path is built.",
            |  "hint": "Run kf-env proot after terminal or exec startup. Ubuntu can read this contract but must not mutate PRoot argv/rootfs/bind ownership."
            |}
        """.trimMargin() + "\n"
    }

    private fun buildProotLaunchRequestTemplate(): String {
        return """
            |{
            |  "version": 1,
            |  "authority": "ubuntu_intent",
            |  "mode": "advisory_only",
            |  "appliedByAndroid": false,
            |  "status": "template",
            |  "requestedLane": null,
            |  "requestedPurpose": null,
            |  "requestedBackgroundAllowed": null,
            |  "requestedMaxConcurrency": null,
            |  "requestedTelemetry": null,
            |  "reason": "Ubuntu and AI may write desired launch intent here, but this file is not executable truth.",
            |  "boundary": [
            |    "Android/KF owns PRoot executable, rootfs, bind mounts, loader, network mode and final launch execution.",
            |    "Ubuntu may declare workload intent and policy hints only.",
            |    "Future Android control-plane code may validate and consume this file; current phase only publishes the boundary."
            |  ]
            |}
        """.trimMargin() + "\n"
    }

    private fun buildProotCapacityExecutorPolicyTemplate(): String {
        return """
            |{
            |  "version": 1,
            |  "authority": "android_control_plane",
            |  "mode": "disabled_until_task_dispatch",
            |  "enabled": false,
            |  "maxProots": 3,
            |  "baseProotMemoryKb": 65536,
            |  "estimatedTaskMemoryKb": null,
            |  "safetyMarginKb": 524288,
            |  "idleGraceMs": 60000,
            |  "minLifetimeMs": 120000,
            |  "scaleOutCooldownMs": 120000,
            |  "capacityRuntimeIds": ["auto_registered_proot_capacity_worker"],
            |  "secondProotRuntimeId": "auto_registered_proot_capacity_worker",
            |  "downlineRuntimeIds": [],
            |  "allowQueueCreation": false,
            |  "boundary": [
            |    "This file only binds Android-owned PRoot capacity targets.",
            |    "Every additional PRoot target must be a dedicated PROOT_CAPACITY_WORKER runtime, not a normal background task.",
            |    "Ubuntu may read this policy, but must not start, stop, resize or downline PRoot capacity directly."
            |  ],
            |  "note": "Capacity workers are diagnostic registrations only. Keep disabled until command dispatch, output, cancellation, ownership and cleanup are implemented."
            |}
        """.trimMargin() + "\n"
    }

    private fun migrateLegacyProotCapacityExecutorPolicy(file: File) {
        val json = runCatching { JSONObject(file.readText()) }.getOrNull() ?: return
        val enabled = json.optBoolean("enabled", false)
        val mode = json.optString("mode", "")
        val note = json.optString("note", "")
        val capacityIds = json.optJSONArray("capacityRuntimeIds")
        val usesAutoCapacityWorker = capacityIds == null || (0 until capacityIds.length()).any { index ->
            capacityIds.optString(index).trim().equals(
                "auto_registered_proot_capacity_worker",
                ignoreCase = true
            )
        }
        val missingGuardedDefaults = listOf(
            "maxProots",
            "baseProotMemoryKb",
            "safetyMarginKb",
            "idleGraceMs",
            "minLifetimeMs",
            "scaleOutCooldownMs",
            "capacityRuntimeIds",
            "secondProotRuntimeId"
        ).any { !json.has(it) }
        val legacyReviewDefault = !enabled &&
            mode == "manual_binding_required" &&
            usesAutoCapacityWorker &&
            (missingGuardedDefaults || note.contains("Keep enabled=false until manual review", ignoreCase = true))
        val generatedAutoDefault = enabled &&
            mode == "guarded_auto_bound_workers" &&
            usesAutoCapacityWorker &&
            (
                note.contains("Default target selection uses", ignoreCase = true) ||
                    note.contains("Migrated from the old review-only default", ignoreCase = true)
                )
        if (!legacyReviewDefault && !generatedAutoDefault) {
            return
        }
        json.put("mode", "disabled_until_task_dispatch")
        json.put("enabled", false)
        putIfMissing(json, "maxProots", 3)
        putIfMissing(json, "baseProotMemoryKb", 65_536)
        if (!json.has("estimatedTaskMemoryKb")) {
            json.put("estimatedTaskMemoryKb", JSONObject.NULL)
        }
        putIfMissing(json, "safetyMarginKb", 524_288)
        putIfMissing(json, "idleGraceMs", 60_000)
        putIfMissing(json, "minLifetimeMs", 120_000)
        putIfMissing(json, "scaleOutCooldownMs", 120_000)
        if (!json.has("capacityRuntimeIds")) {
            json.put("capacityRuntimeIds", JSONArray().put("auto_registered_proot_capacity_worker"))
        }
        putIfMissing(json, "secondProotRuntimeId", "auto_registered_proot_capacity_worker")
        if (!json.has("downlineRuntimeIds")) {
            json.put("downlineRuntimeIds", JSONArray())
        }
        putIfMissing(json, "allowQueueCreation", false)
        json.put(
            "note",
            "Capacity workers are diagnostic registrations only. Keep disabled until command dispatch, " +
                "output, cancellation, ownership and cleanup are implemented."
        )
        writeTextIfChanged(file, json.toString(2) + "\n")
    }

    private fun putIfMissing(json: JSONObject, name: String, value: Any) {
        if (!json.has(name)) {
            json.put(name, value)
        }
    }

    private fun migrateLegacyRuntimeWorkloadPolicy(file: File) {
        val json = runCatching { JSONObject(file.readText()) }.getOrNull() ?: return
        if (json.has("lifecycleManagementEnabled")) return
        json.put("lifecycleManagementEnabled", false)
        writeTextIfChanged(file, json.toString(2) + "\n")
    }

    private fun buildRuntimeReclaimerPolicyTemplate(): String {
        return """
            |{
            |  "version": 1,
            |  "activeProfile": "BALANCED",
            |  "memoryPressure": {
            |    "memoryBudgetKb": null,
            |    "elevatedRssPercent": 50,
            |    "highRssPercent": 70,
            |    "criticalRssPercent": 85,
            |    "elevatedHostAvailableKb": 1310720,
            |    "highHostAvailableKb": 786432,
            |    "criticalHostAvailableKb": 393216
            |  },
            |  "unknownProcessRules": [
            |    {
            |      "id": "example-resident-helper",
            |      "enabled": false,
            |      "matchField": "COMMAND_LINE",
            |      "matchMode": "CONTAINS",
            |      "pattern": "python -m my_resident_helper",
            |      "retentionClass": "RESIDENT",
            |      "resident": true,
            |      "autoReclaimAllowed": false,
            |      "note": "Mark a verified helper as resident so Android-side reclaim never targets it."
            |    },
            |    {
            |      "id": "example-ephemeral-cleanup",
            |      "enabled": false,
            |      "matchField": "COMMAND",
            |      "matchMode": "EXACT",
            |      "pattern": "curl",
            |      "retentionClass": "EPHEMERAL",
            |      "reclaimPriority": 950,
            |      "resident": false,
            |      "autoReclaimAllowed": true,
            |      "note": "Only enable after confirming this unknown process is disposable under pressure."
            |    }
            |  ]
            |}
        """.trimMargin() + "\n"
    }

    private fun migrateLegacyRuntimeReclaimerPolicy(file: File) {
        val json = runCatching { JSONObject(file.readText()) }.getOrNull() ?: return
        val activeProfile = json.optString("activeProfile", "BALANCED")
        if (activeProfile != "OBSERVE_ONLY") {
            return
        }
        json.put("activeProfile", "BALANCED")
        if (!json.has("memoryPressure") || json.isNull("memoryPressure")) {
            json.put(
                "memoryPressure",
                JSONObject()
                    .put("memoryBudgetKb", JSONObject.NULL)
                    .put("elevatedRssPercent", 50)
                    .put("highRssPercent", 70)
                    .put("criticalRssPercent", 85)
                    .put("elevatedHostAvailableKb", 1_310_720)
                    .put("highHostAvailableKb", 786_432)
                    .put("criticalHostAvailableKb", 393_216)
            )
        }
        if (!json.has("unknownProcessRules")) {
            json.put("unknownProcessRules", JSONArray())
        }
        json.put(
            "note",
            "Migrated from the old observe-only default. Lifecycle lease reclaim may execute " +
                "only through RuntimeReclaimer after system_core, proot_core, user_locked, " +
                "foreground, weak-match and unmanaged gates."
        )
        writeTextIfChanged(file, json.toString(2) + "\n")
    }

    private fun buildRuntimeResidentPolicyTemplate(): String {
        return """
            |{
            |  "version": 2,
            |  "activeProfile": "BALANCED",
            |  "runtimeOverrides": [
            |    {
            |      "runtimeId": "background-space-openclaw-gateway",
            |      "enabled": false,
            |      "keepResident": true,
            |      "allowAutoStart": true,
            |      "allowAutoRecover": true,
            |      "allowedRecoveryTriggers": [
            |        "SERVICE_START",
            |        "TRIM_MEMORY",
            |        "HEALTH_RECONCILE",
            |        "AUTO_RESTART"
            |      ],
            |      "blockedRecoveryTriggers": [
            |        "TRIM_MEMORY"
            |      ],
            |      "note": "blockedRecoveryTriggers wins over allowedRecoveryTriggers. Use this to keep a resident daemon alive on service start, but stop trim-memory from rehydrating it."
            |    },
            |    {
            |      "runtimeId": "background-space-scratch-batch",
            |      "enabled": false,
            |      "keepResident": false,
            |      "allowAutoStart": false,
            |      "allowAutoRecover": false,
            |      "allowedRecoveryTriggers": [],
            |      "blockedRecoveryTriggers": [
            |        "SERVICE_START",
            |        "TRIM_MEMORY",
            |        "HEALTH_RECONCILE",
            |        "AUTO_RESTART"
            |      ],
            |      "note": "Use to pin a runtime as disposable even if it was registered with a resident-style retention class."
            |    }
            |  ]
            |}
        """.trimMargin() + "\n"
    }

    private fun buildRuntimeWorkloadPolicyTemplate(): String {
        return """
            |{
            |  "version": 1,
            |  "lifecycleManagementEnabled": false,
            |  "authority": "android_control_plane",
            |  "telemetrySource": "proot_lifecycle_telemetry_v0+android_proc_snapshot_current",
            |  "lanes": [
            |    {
            |      "lane": "INTERACTIVE",
            |      "maxConcurrency": 2,
            |      "backgroundMaxConcurrency": 1,
            |      "serial": false,
            |      "allowBurst": true,
            |      "priority": 0
            |    },
            |    {
            |      "lane": "SERVICE",
            |      "maxConcurrency": 3,
            |      "backgroundMaxConcurrency": 2,
            |      "serial": false,
            |      "allowBurst": false,
            |      "priority": 20
            |    },
            |    {
            |      "lane": "BUILD",
            |      "maxConcurrency": 1,
            |      "backgroundMaxConcurrency": 0,
            |      "serial": true,
            |      "allowBurst": false,
            |      "priority": 60
            |    },
            |    {
            |      "lane": "PROBE",
            |      "maxConcurrency": 1,
            |      "backgroundMaxConcurrency": 0,
            |      "serial": true,
            |      "allowBurst": false,
            |      "priority": 90
            |    }
            |  ],
            |  "envelopes": [
            |    {
            |      "workloadClass": "SYSTEM_CORE",
            |      "defaultRetention": "KEEP",
            |      "backgroundAllowed": true,
            |      "maxChildren": 8,
            |      "maxRuntimeMs": 0,
            |      "maxIdleMs": 0,
            |      "restartAllowed": true,
            |      "autoQuarantineAllowed": false
            |    },
            |    {
            |      "workloadClass": "PINNED_SERVICE",
            |      "defaultRetention": "KEEP",
            |      "backgroundAllowed": true,
            |      "maxChildren": 6,
            |      "maxRuntimeMs": 0,
            |      "maxIdleMs": 0,
            |      "restartAllowed": true,
            |      "autoQuarantineAllowed": true
            |    },
            |    {
            |      "workloadClass": "INTERACTIVE",
            |      "defaultRetention": "KEEP",
            |      "backgroundAllowed": false,
            |      "maxChildren": 16,
            |      "maxRuntimeMs": 0,
            |      "maxIdleMs": 600000,
            |      "restartAllowed": false,
            |      "autoQuarantineAllowed": false
            |    },
            |    {
            |      "workloadClass": "BUILD",
            |      "defaultRetention": "LEASE",
            |      "backgroundAllowed": false,
            |      "maxChildren": 32,
            |      "maxRuntimeMs": 7200000,
            |      "maxIdleMs": 600000,
            |      "restartAllowed": false,
            |      "autoQuarantineAllowed": true
            |    },
            |    {
            |      "workloadClass": "PROBE",
            |      "defaultRetention": "LEASE",
            |      "backgroundAllowed": false,
            |      "maxChildren": 4,
            |      "maxRuntimeMs": 120000,
            |      "maxIdleMs": 30000,
            |      "restartAllowed": false,
            |      "autoQuarantineAllowed": true
            |    },
            |    {
            |      "workloadClass": "EPHEMERAL",
            |      "defaultRetention": "LEASE",
            |      "backgroundAllowed": false,
            |      "maxChildren": 8,
            |      "maxRuntimeMs": 300000,
            |      "maxIdleMs": 60000,
            |      "restartAllowed": false,
            |      "autoQuarantineAllowed": true
            |    },
            |    {
            |      "workloadClass": "STRAY",
            |      "defaultRetention": "CLEANUP_CANDIDATE",
            |      "backgroundAllowed": false,
            |      "maxChildren": 0,
            |      "maxRuntimeMs": 60000,
            |      "maxIdleMs": 30000,
            |      "restartAllowed": false,
            |      "autoQuarantineAllowed": true
            |    },
            |    {
            |      "workloadClass": "UNKNOWN",
            |      "defaultRetention": "CLEANUP_CANDIDATE",
            |      "backgroundAllowed": false,
            |      "maxChildren": 0,
            |      "maxRuntimeMs": 60000,
            |      "maxIdleMs": 30000,
            |      "restartAllowed": false,
            |      "autoQuarantineAllowed": true
            |    }
            |  ],
            |  "backgroundDecay": {
            |    "graceMs": 30000,
            |    "transientCleanupMs": 180000,
            |    "serviceOnlyMs": 600000,
            |    "lowActivityMs": 1800000,
            |    "pressureAccelerates": true
            |  },
            |  "budgetStates": [
            |    {
            |      "state": "HEALTHY",
            |      "actions": ["OBSERVE"],
            |      "note": "within budget"
            |    },
            |    {
            |      "state": "NEAR_BUDGET",
            |      "actions": ["WARN", "REQUEST_CLEANUP"],
            |      "note": "first pressure warning; do not restart on first sight"
            |    },
            |    {
            |      "state": "SOFT_PRESSURE",
            |      "actions": ["THROTTLE", "REQUEST_CLEANUP"],
            |      "note": "stop feeding new low priority work and ask workload to self-clean"
            |    },
            |    {
            |      "state": "HARD_PRESSURE",
            |      "actions": ["FREEZE_SHORT", "TERMINATE_CHILDREN", "RESTART_MAIN"],
            |      "note": "protect the registered root, cut abnormal children first"
            |    },
            |    {
            |      "state": "THREATENING_KF",
            |      "actions": ["RECOVERY_CUTOFF", "TERMINATE_WORKLOAD"],
            |      "note": "KF platform survival outranks a single workload"
            |    },
            |    {
            |      "state": "REPEAT_OFFENDER",
            |      "actions": ["QUARANTINE"],
            |      "note": "rapid relapse or repeated violations remove background rights"
            |    },
            |    {
            |      "state": "QUARANTINED",
            |      "actions": ["OBSERVE"],
            |      "note": "manual foreground recovery only"
            |    }
            |  ],
            |  "repeatOffender": {
            |    "quickRelapseMs": 60000,
            |    "restartWindowMs": 600000,
            |    "maxRestartsInWindow": 2,
            |    "violationWindowMs": 1800000,
            |    "maxViolationsInWindow": 3
            |  }
            |}
        """.trimMargin() + "\n"
    }

    private fun buildRuntimeWorkloadIntentTemplate(): String {
        return """
            |{
            |  "version": 1,
            |  "authority": "ubuntu_advisory",
            |  "boundary": "advisory_only_android_kf_decides",
            |  "notes": [
            |    "Ubuntu tools may declare workload intent here.",
            |    "Android/KF remains the execution owner for start, queue, lane, pool, cleanup, restart, terminate, and quarantine."
            |  ],
            |  "intents": []
            |}
        """.trimMargin() + "\n"
    }

    private fun buildRuntimeProcessManifestTemplate(): String {
        return """
            |{
            |  "version": 1,
            |  "authority": "ubuntu_advisory",
            |  "boundary": "declaration_only_android_observes_no_direct_kill_restart_or_quarantine",
            |  "notes": [
            |    "Declare ordinary process units here when Ubuntu-side tools need KF observation, budget labels, or user-lock semantics.",
            |    "System/Core and PRoot #1 units are Android built-ins and cannot be downgraded here.",
            |    "This file does not execute start, stop, restart, reclaim, memory enforcement, or quarantine."
            |  ],
            |  "units": []
            |}
        """.trimMargin() + "\n"
    }

    fun buildRuntimeProcessManifestExampleTemplate(): String {
        return """
            |{
            |  "version": 1,
            |  "authority": "ubuntu_advisory",
            |  "boundary": "declaration_only_android_observes_no_direct_kill_restart_or_quarantine",
            |  "notes": [
            |    "This example is advisory. It does not start, stop, restart, reclaim, quarantine, or resize PRoot.",
            |    "Do not declare CONTAINER_SUPERVISOR, PRoot #1, or any proot-capacity-worker-1 runtime here.",
            |    "Prefer pidFile, runtimeId, or exactCommand for user_locked services. Avoid broad commandContains for locked units.",
            |    "Ordinary Ubuntu processes that do not match a unit remain unmanaged observe-only and still follow normal ps/kill/pkill habits."
            |  ],
            |  "units": [
            |    {
            |      "id": "example-user-locked-service",
            |      "displayName": "Example user locked service",
            |      "tier": "USER_LOCKED",
            |      "manualKillPolicy": "WAIT_CONFIRM",
            |      "match": {
            |        "pidFile": "/workspace/.kf/example-service.pid",
            |        "exactCommand": "python /workspace/services/example_service.py"
            |      },
            |      "resource": {
            |        "expectedMemoryLimitKb": 262144,
            |        "unlimitedMemory": false
            |      },
            |      "protection": {
            |        "allowReclaim": false,
            |        "allowKill": false,
            |        "allowRestart": false
            |      }
            |    },
            |    {
            |      "id": "unit-hermes-controlled-lease-probe",
            |      "displayName": "Hermes controlled lease probe",
            |      "tier": "LEASE",
            |      "manualKillPolicy": "RESPECT_USER_KILL",
            |      "match": {
            |        "runtimeId": "background-hermes-controlled-lease-probe",
            |        "exactCommand": "python3 /workspace/.kf/hermes-controlled-lease-probe.py"
            |      },
            |      "lease": {
            |        "enabled": true,
            |        "initialLeaseMs": 300000,
            |        "renewMs": 60000,
            |        "maxTotalLeaseMs": 1800000
            |      },
            |      "protection": {
            |        "allowReclaim": true,
            |        "allowKill": false,
            |        "allowRestart": false
            |      }
            |    },
            |    {
            |      "id": "example-lease-worker",
            |      "displayName": "Example lease worker",
            |      "tier": "LEASE",
            |      "manualKillPolicy": "RESPECT_USER_KILL",
            |      "match": {
            |        "commandContains": "python /workspace/jobs/example_worker.py"
            |      },
            |      "lease": {
            |        "enabled": true,
            |        "initialLeaseMs": 300000,
            |        "renewMs": 60000,
            |        "maxTotalLeaseMs": 1800000
            |      },
            |      "protection": {
            |        "allowReclaim": true,
            |        "allowKill": true,
            |        "allowRestart": false
            |      }
            |    },
            |    {
            |      "id": "example-foreground-helper",
            |      "displayName": "Example foreground helper",
            |      "tier": "FOREGROUND",
            |      "manualKillPolicy": "RESPECT_USER_KILL",
            |      "match": {
            |        "exactCommand": "node /workspace/tools/foreground-helper.js"
            |      },
            |      "exec": {
            |        "restartMode": "NEVER"
            |      }
            |    },
            |    {
            |      "id": "example-unmanaged-observed-process",
            |      "displayName": "Example unmanaged observed process",
            |      "tier": "UNMANAGED",
            |      "manualKillPolicy": "RESPECT_USER_KILL",
            |      "match": {
            |        "exactCommand": "bash /workspace/scripts/observe-only.sh"
            |      },
            |      "protection": {
            |        "allowReclaim": false,
            |        "allowKill": false,
            |        "allowRestart": false
            |      }
            |    }
            |  ]
            |}
        """.trimMargin() + "\n"
    }

    private fun buildEnvSurfaceScript(): String {
        return """
            |#!/usr/bin/env sh
            |set +e
            |
            |print_limits() {
            |  cat <<'EOF'
            |KFSHELL_ENV_LIMITS_BEGIN
            |systemd=unavailable
            |service_manager=systemctl_compatible_kfshell_service_manager
            |service_backend=supervisord
            |network_introspection=netlink_restricted
            |port_low_range=1-1023 sensitive_unavailable_by_default
            |port_default_range=>=1024
            |proc=partial
            |root=proot_root_without_linux_capabilities
            |android_control=apk_bridge_or_adb_kf_host_self
            |runtime_memory_policy=android_reclaimer_resident_classes_protected
            |reboot_shutdown=not_supported_inside_container
            |KFSHELL_ENV_LIMITS_END
            |EOF
            |}
            |
            |print_shims() {
            |  echo "KFSHELL_ENV_SHIMS_BEGIN"
            |  for cmd in ps pgrep pkill kill pidof pstree free top kf-resource-sampler systemctl service supervisorctl ss proot adb fd claude opencode pnpm pnpx node npm npx uv uvx fastboot kf-host kf-runtime kf-adb-check kf-adb-bridge kf-android-sh kf-gradle; do
            |    path="$(command -v "${'$'}cmd" 2>/dev/null || true)"
            |    if [ -n "${'$'}path" ]; then
            |      echo "${'$'}cmd=${'$'}path"
            |    else
            |      echo "${'$'}cmd=missing"
            |    fi
            |  done
            |  echo "KFSHELL_ENV_SHIMS_END"
            |}
            |
            |print_capabilities() {
            |  echo "KFSHELL_ENV_CAPABILITIES_BEGIN"
            |  if command -v capsh >/dev/null 2>&1; then
            |    capsh --print 2>&1 | sed 's/^/capsh: /'
            |  else
            |    echo "capsh=missing"
            |  fi
            |  if [ -r /proc/self/status ]; then
            |    grep -E '^(Uid|Gid|Groups|Cap|NoNewPrivs|Seccomp):' /proc/self/status 2>/dev/null | sed 's/^/proc_self: /'
            |  else
            |    echo "proc_self_status=unreadable"
            |  fi
            |  echo "hint: uid=0 in proot does not imply Android root or Linux capabilities."
            |  echo "KFSHELL_ENV_CAPABILITIES_END"
            |}
            |
            |print_proc() {
            |  echo "KFSHELL_ENV_PROC_BEGIN"
            |  for path in /proc/self/status /proc/self/mountinfo /proc/1/status /proc/version /proc/modules /proc/sysrq-trigger; do
            |    if [ -r "${'$'}path" ]; then
            |      echo "PASS readable ${'$'}path"
            |    elif [ -e "${'$'}path" ]; then
            |      echo "WARN unreadable ${'$'}path"
            |    else
            |      echo "WARN missing_or_hidden ${'$'}path"
            |    fi
            |  done
            |  echo "hint: prefer /proc/self/* and uname -r; do not treat /proc/1/status absence as system failure."
            |  echo "KFSHELL_ENV_PROC_END"
            |}
            |
            |print_network_tools() {
            |  echo "KFSHELL_ENV_NETLINK_BEGIN"
            |  for cmd in ip ss netstat; do
            |    path="$(command -v "${'$'}cmd" 2>/dev/null || true)"
            |    if [ -n "${'$'}path" ]; then
            |      echo "${'$'}cmd=${'$'}path"
            |    else
            |      echo "${'$'}cmd=missing"
            |    fi
            |  done
            |  echo "WARN netlink_introspection_restricted"
            |  echo "hint: do not trust empty ip/ss/netstat output. Use curl http://127.0.0.1:<port> or kf-host check-bind."
            |  echo "KFSHELL_ENV_NETLINK_END"
            |}
            |
            |print_runtime() {
            |  echo "KFSHELL_ENV_RUNTIME_BEGIN"
            |  runtime_file="${'$'}{KF_RUNTIME_PRESSURE_PATH:-${CONTAINER_RUNTIME_PRESSURE_PATH}}"
            |  runtime_process_table="${'$'}{KF_RUNTIME_PROCESS_TABLE_PATH:-${CONTAINER_RUNTIME_PROCESS_TABLE_PATH}}"
            |  runtime_resource_sampler="${'$'}{KF_RUNTIME_RESOURCE_SAMPLER_COMMAND:-${CONTAINER_RUNTIME_RESOURCE_SAMPLER_COMMAND}}"
            |  proot_telemetry_events="${'$'}{KF_PROOT_TELEMETRY_EVENTS_PATH:-${CONTAINER_PROOT_TELEMETRY_EVENTS_PATH}}"
            |  procfs_projection_root="${'$'}{KF_PROCFS_PROJECTION_ROOT:-${CONTAINER_HELPER_SYSTEM_PROC_PATH}}"
            |  policy_file="${'$'}{KF_RUNTIME_POLICY_PATH:-${CONTAINER_RUNTIME_RECLAIMER_POLICY_PATH}}"
            |  resident_policy_file="${'$'}{KF_RUNTIME_RESIDENT_POLICY_PATH:-${CONTAINER_RUNTIME_RESIDENT_POLICY_PATH}}"
            |  workload_policy_file="${'$'}{KF_RUNTIME_WORKLOAD_POLICY_PATH:-${CONTAINER_RUNTIME_WORKLOAD_POLICY_PATH}}"
            |  workload_intent_file="${'$'}{KF_RUNTIME_WORKLOAD_INTENT_PATH:-${CONTAINER_RUNTIME_WORKLOAD_INTENT_PATH}}"
            |  process_manifest_file="${'$'}{KF_RUNTIME_PROCESS_MANIFEST_PATH:-${CONTAINER_RUNTIME_PROCESS_MANIFEST_PATH}}"
            |  process_manifest_example_file="${'$'}{KF_RUNTIME_PROCESS_MANIFEST_EXAMPLE_PATH:-${CONTAINER_RUNTIME_PROCESS_MANIFEST_EXAMPLE_PATH}}"
            |  resource_event_ledger_file="${'$'}{KF_RUNTIME_RESOURCE_EVENT_LEDGER_PATH:-${CONTAINER_RUNTIME_RESOURCE_EVENT_LEDGER_PATH}}"
            |  lifecycle_action_inbox_file="${'$'}{KF_RUNTIME_LIFECYCLE_ACTION_INBOX_PATH:-${CONTAINER_RUNTIME_LIFECYCLE_ACTION_INBOX_PATH}}"
            |  proot_pool_tuning_log="${'$'}{KF_PROOT_POOL_TUNING_LOG_PATH:-${CONTAINER_PROOT_POOL_TUNING_LOG_PATH}}"
            |  echo "runtime_pressure_path=${'$'}runtime_file"
            |  echo "runtime_process_table_path=${'$'}runtime_process_table"
            |  echo "runtime_resource_sampler_command=${'$'}runtime_resource_sampler"
            |  echo "runtime_resource_sampler_mode=internal_native_running_pid_list_no_proc_scan"
            |  echo "runtime_resource_sampler_standard_command_triggers=ps_aux,top"
            |  echo "runtime_resource_sampler_standard_command_cache_ttl_ms=2000"
            |  echo "runtime_resource_sampler_read_trigger_bucket_capacity=4"
            |  echo "runtime_resource_sampler_read_trigger_refill_interval_ms=2000"
            |  echo "runtime_resource_sampler_read_trigger_advice_cache_hit_threshold=5"
            |  echo "runtime_resource_sampler_read_trigger_advice_cooldown_ms=30000"
            |  echo "runtime_resource_sampler_force_min_interval_ms=250"
            |  echo "runtime_resource_sampler_high_frequency_entry=kf-resource-sampler --watch --interval-ms 500"
            |  echo "runtime_resource_sampler_high_frequency_hint=standard_commands_reuse_cache_use_kf_resource_sampler_for_explicit_curves"
            |  echo "proot_telemetry_events_path=${'$'}proot_telemetry_events"
            |  echo "procfs_projection_root=${'$'}procfs_projection_root"
            |  echo "runtime_policy_path=${'$'}policy_file"
            |  echo "runtime_resident_policy_path=${'$'}resident_policy_file"
            |  echo "runtime_workload_policy_path=${'$'}workload_policy_file"
            |  echo "runtime_workload_intent_path=${'$'}workload_intent_file"
            |  echo "runtime_process_manifest_path=${'$'}process_manifest_file"
            |  echo "runtime_process_manifest_example_path=${'$'}process_manifest_example_file"
            |  echo "runtime_resource_event_ledger_path=${'$'}resource_event_ledger_file"
            |  echo "runtime_lifecycle_action_inbox_path=${'$'}lifecycle_action_inbox_file"
            |  echo "proot_pool_tuning_log_path=${'$'}proot_pool_tuning_log"
            |  if [ -r "${'$'}runtime_file" ]; then
            |    cat "${'$'}runtime_file"
            |  else
            |    echo "KFSHELL_RUNTIME_PRESSURE_VERSION=1"
            |    echo "source=kf_env"
            |    echo "level=UNKNOWN"
            |    echo "active_profile=BALANCED"
            |    echo "policy_path=${'$'}policy_file"
            |    echo "reason=runtime_pressure_file_missing"
            |    echo "hint=runtime pressure is published by Android APK; open/refresh runtime once."
            |  fi
            |  echo "hint=policy profiles are advisory groups (default balanced, low power, high performance, custom); Android still owns execution."
            |  echo "hint=edit runtime_policy_path to tune reclaim profile and unknownProcessRules as advanced profile inputs."
            |  echo "hint=edit runtime_resident_policy_path to tune resident auto-start/auto-recover profile inputs."
            |  echo "hint=edit runtime_workload_policy_path to declare PRoot lanes, background decay, workload envelopes, and repeat-offender rules; Android still owns execution."
            |  echo "hint=edit runtime_workload_intent_path only for low-level workload hint tests; direct actions are rejected."
            |  echo "hint=edit runtime_process_manifest_path to declare KF Process Units for observation and dry-run lifecycle classification only."
            |  echo "hint=read runtime_resource_event_ledger_path for resource episode diagnostics; it is not a runtime enforcement config."
            |  echo "hint=read runtime_lifecycle_action_inbox_path for pending lifecycle diagnostics; confirmation does not execute actions in this phase."
            |  echo "hint=read proot_pool_tuning_log_path after crash/restart to inspect binary-search pool candidates and pressure outcome."
            |  echo "KFSHELL_ENV_RUNTIME_END"
            |}
            |
            |print_proot() {
            |  echo "KFSHELL_ENV_PROOT_BEGIN"
            |  contract_file="${'$'}{KF_PROOT_LAUNCH_CONTRACT_PATH:-${CONTAINER_PROOT_LAUNCH_CONTRACT_PATH}}"
            |  request_file="${'$'}{KF_PROOT_LAUNCH_REQUEST_PATH:-${CONTAINER_PROOT_LAUNCH_REQUEST_PATH}}"
            |  echo "proot_launch_contract_path=${'$'}contract_file"
            |  echo "proot_launch_request_path=${'$'}request_file"
            |  echo "proot_launch_authority=${'$'}{KF_PROOT_LAUNCH_AUTHORITY:-android_control_plane}"
            |  echo "proot_launch_owner=${'$'}{KF_PROOT_LAUNCH_OWNER:-android_apk}"
            |  echo "proot_execution_boundary=${'$'}{KF_PROOT_EXECUTION_BOUNDARY:-ubuntu_declares_android_launches}"
            |  echo "proot_request_mode=${'$'}{KF_PROOT_REQUEST_MODE:-ubuntu_intent_advisory_only}"
            |  echo "proot_runtime_descriptor_source=${'$'}{KF_PROOT_RUNTIME_DESCRIPTOR_SOURCE:-launch_contract.runtime.proot}"
            |  echo "proot_telemetry_mode=${'$'}{KF_PROOT_TELEMETRY_MODE:-debug_jsonl_lifecycle_v0}"
            |  echo "proot_telemetry_path=${'$'}{KF_PROOT_TELEMETRY_PATH:-}"
            |  echo "KFSHELL_ENV_PROOT_CONTRACT_JSON_BEGIN"
            |  if [ -r "${'$'}contract_file" ]; then
            |    cat "${'$'}contract_file"
            |  else
            |    echo "state=contract_missing"
            |    echo "reason=Android has not published a PRoot launch contract yet."
            |  fi
            |  echo "KFSHELL_ENV_PROOT_CONTRACT_JSON_END"
            |  echo "KFSHELL_ENV_PROOT_REQUEST_JSON_BEGIN"
            |  if [ -r "${'$'}request_file" ]; then
            |    cat "${'$'}request_file"
            |  else
            |    echo "state=request_missing"
            |    echo "reason=Ubuntu intent file has not been created yet."
            |  fi
            |  echo "KFSHELL_ENV_PROOT_REQUEST_JSON_END"
            |  echo "hint=PRoot argv, rootfs, bind mounts, loader and network mode are owned by Android APK."
            |  echo "hint=Ubuntu may edit the request file for intent/policy, but final launch execution stays in Android control plane."
            |  echo "KFSHELL_ENV_PROOT_END"
            |}
            |
            |doctor() {
            |  echo "KFSHELL_ENV_DOCTOR_BEGIN"
            |  echo "kernel=$(uname -r 2>/dev/null || echo unknown)"
            |  echo "machine=$(uname -m 2>/dev/null || echo unknown)"
            |  echo "user=$(id 2>/dev/null || echo unknown)"
            |  print_limits
            |  print_shims
            |  print_network_tools
            |  print_proc
            |  print_capabilities
            |  print_runtime
            |  print_proot
            |  echo "KFSHELL_ENV_DOCTOR_END"
            |}
            |
            |usage() {
            |  cat <<'EOF'
            |Usage:
            |  kf-env doctor
            |  kf-env limits
            |  kf-env shims
            |  kf-env proc
            |  kf-env capabilities
            |  kf-env netlink
            |  kf-env runtime
            |  kf-env proot
            |
            |This reports KFShell Ubuntu/proot environment boundaries for AI and shell scripts.
            |EOF
            |}
            |
            |case "${'$'}{1:-doctor}" in
            |  doctor)
            |    doctor
            |    ;;
            |  limits)
            |    print_limits
            |    ;;
            |  shims|wrappers)
            |    print_shims
            |    ;;
            |  proc)
            |    print_proc
            |    ;;
            |  capabilities|cap)
            |    print_capabilities
            |    ;;
            |  netlink|network-tools)
            |    print_network_tools
            |    ;;
            |  runtime|pressure|memory)
            |    print_runtime
            |    ;;
            |  proot|launch)
            |    print_proot
            |    ;;
            |  -h|--help|help)
            |    usage
            |    ;;
            |  *)
            |    echo "unknown subcommand: ${'$'}1" >&2
            |    usage >&2
            |    exit 2
            |    ;;
            |esac
        """.trimMargin() + "\n"
    }

    private fun buildControlledLeaseProbeScript(): String {
        return """
            |#!/usr/bin/env python3
            |import os
            |import pathlib
            |import time
            |
            |MODE = os.environ.get("KF_LEASE_PROBE_MODE", "idle").strip().lower() or "idle"
            |HEARTBEAT = pathlib.Path(os.environ.get(
            |    "KF_LEASE_PROBE_HEARTBEAT",
            |    "/workspace/.kf/hermes-controlled-lease-probe.heartbeat",
            |))
            |IO_LOG = pathlib.Path(os.environ.get(
            |    "KF_LEASE_PROBE_IO_LOG",
            |    "/workspace/.kf/logs/hermes-controlled-lease-probe.io.log",
            |))
            |SLEEP_SEC = float(os.environ.get("KF_LEASE_PROBE_SLEEP_SEC", "1.0"))
            |MAX_CHUNKS = int(os.environ.get("KF_LEASE_PROBE_MAX_CHUNKS", "24"))
            |
            |chunks = []
            |tick = 0
            |
            |print(
            |    "hermes_controlled_lease_probe_started "
            |    f"pid={os.getpid()} mode={MODE}",
            |    flush=True,
            |)
            |
            |while True:
            |    tick += 1
            |    HEARTBEAT.parent.mkdir(parents=True, exist_ok=True)
            |    HEARTBEAT.write_text(
            |        f"pid={os.getpid()}\nmode={MODE}\ntick={tick}\ntime={time.time()}\n",
            |        encoding="utf-8",
            |    )
            |
            |    if MODE == "cpu":
            |        end_at = time.time() + 0.25
            |        value = 0
            |        while time.time() < end_at:
            |            value = (value * 33 + tick) % 1000003
            |    elif MODE == "io":
            |        IO_LOG.parent.mkdir(parents=True, exist_ok=True)
            |        with IO_LOG.open("a", encoding="utf-8") as fp:
            |            fp.write(f"tick={tick} time={time.time()}\n")
            |    elif MODE == "rss-strong":
            |        if len(chunks) < MAX_CHUNKS:
            |            chunks.append(bytearray(1024 * 1024))
            |    elif MODE == "rss-jitter":
            |        bytearray(4096)
            |
            |    time.sleep(max(0.05, SLEEP_SEC))
        """.trimMargin() + "\n"
    }

    private fun buildRuntimeSurfaceScript(): String {
        val action = RuntimeControlledLeaseProbeRegistrationReceiver.ACTION_REGISTER_CONTROLLED_LEASE_PROBE
        val spaceExtra = RuntimeControlledLeaseProbeRegistrationReceiver.EXTRA_SPACE_ID
        val runtimeId = RuntimeControlledLeaseProbeRegistration.RUNTIME_ID
        val unitId = RuntimeControlledLeaseProbeRegistration.UNIT_ID
        val command = RuntimeControlledLeaseProbeRegistration.COMMAND
        return """
            |#!/usr/bin/env sh
            |set +e
            |
            |runtime_id="${runtimeId}"
            |unit_id="${unitId}"
            |probe_command="${command}"
            |register_action="${action}"
            |space_extra="${spaceExtra}"
            |workload_policy_path="${CONTAINER_RUNTIME_WORKLOAD_POLICY_PATH}"
            |
            |usage() {
            |  cat <<EOF
            |Usage:
            |  kf-runtime lease-probe command
            |  kf-runtime lease-probe status
            |  kf-runtime lease-probe register [--space-id SPACE]
            |  kf-runtime register-controlled-lease-probe [--space-id SPACE]
            |  kf-runtime lock runtime RUNTIME_ID [--name NAME] [--memory-kb KB]
            |  kf-runtime lock command UNIT_ID EXACT_COMMAND [--name NAME] [--memory-kb KB]
            |  kf-runtime lock pid-file UNIT_ID PID_FILE [--command EXACT_COMMAND] [--name NAME] [--memory-kb KB]
            |  kf-runtime unlock UNIT_ID
            |  kf-runtime lifecycle status
            |  kf-runtime lifecycle enable
            |  kf-runtime lifecycle disable
            |
            |Primary validation path:
            |  ${command}
            |
            |Start the controlled probe with the ordinary Ubuntu command above. KF then observes the
            |real process through the runtime process manifest exactCommand match and lease plan.
            |The register command is a debug registration helper only; empty registration does not create
            |a process root in runtime-pressure.env. No subcommand starts, stops, kills, reclaims,
            |restarts, or changes PRoot capacity.
            |
            |User lock:
            |  This is a KF-only declaration helper for Ubuntu processes that need lifecycle
            |  protection. It writes /workspace/.kf/runtime-process-manifest.json with
            |  tier=USER_LOCKED and manualKillPolicy=WAIT_CONFIRM. It requires a strong match
            |  (runtimeId, pidFile, or exactCommand), rejects broad commandContains-style
            |  locking, and never starts, stops, kills, reclaims, restarts, or changes PRoot.
            |  A runtimeId lock protects only the registered runtime root itself; child tracees
            |  that merely carry the same KF runtime attribution must be locked explicitly if
            |  they should be protected.
            |
            |Lifecycle switch:
            |  This toggles KF's lifecycle time-ledger judgment loop by editing
            |  /workspace/.kf/runtime-workload-policy.json. It does not execute cleanup by itself;
            |  it only decides whether monitoring snapshots may flow into the existing
            |  judgment -> Android/KF execution boundary.
            |EOF
            |}
            |
            |parse_space_id() {
            |  space_id=""
            |  while [ "${'$'}#" -gt 0 ]; do
            |    case "${'$'}1" in
            |      --space-id)
            |        shift
            |        space_id="${'$'}{1:-}"
            |        ;;
            |      --space-id=*)
            |        space_id="${'$'}{1#--space-id=}"
            |        ;;
            |      "")
            |        ;;
            |      *)
            |        echo "error: unsupported kf-runtime lease-probe option: ${'$'}1" >&2
            |        return 2
            |        ;;
            |    esac
            |    shift || true
            |  done
            |  printf '%s' "${'$'}space_id"
            |}
            |
            |parse_user_lock_options() {
            |  lock_name=""
            |  lock_memory_kb=""
            |  lock_exact_command=""
            |  while [ "${'$'}#" -gt 0 ]; do
            |    case "${'$'}1" in
            |      --name)
            |        shift
            |        lock_name="${'$'}{1:-}"
            |        ;;
            |      --name=*)
            |        lock_name="${'$'}{1#--name=}"
            |        ;;
            |      --memory-kb)
            |        shift
            |        lock_memory_kb="${'$'}{1:-}"
            |        ;;
            |      --memory-kb=*)
            |        lock_memory_kb="${'$'}{1#--memory-kb=}"
            |        ;;
            |      --command)
            |        shift
            |        lock_exact_command="${'$'}{1:-}"
            |        ;;
            |      --command=*)
            |        lock_exact_command="${'$'}{1#--command=}"
            |        ;;
            |      "")
            |        ;;
            |      *)
            |        echo "error: unsupported kf-runtime lock option: ${'$'}1" >&2
            |        return 2
            |        ;;
            |    esac
            |    shift || true
            |  done
            |  return 0
            |}
            |
            |user_lock_manifest_path() {
            |  printf '%s' "${'$'}{KF_RUNTIME_PROCESS_MANIFEST_PATH:-${CONTAINER_RUNTIME_PROCESS_MANIFEST_PATH}}"
            |}
            |
            |run_user_lock_writer() {
            |  if ! command -v python3 >/dev/null 2>&1; then
            |    echo "error: kf-runtime lock needs python3 to edit runtime-process-manifest.json safely" >&2
            |    return 127
            |  fi
            |  KF_USER_LOCK_MANIFEST="$(user_lock_manifest_path)" \
            |  KF_USER_LOCK_ACTION="${'$'}user_lock_action" \
            |  KF_USER_LOCK_UNIT_ID="${'$'}user_lock_unit_id" \
            |  KF_USER_LOCK_RUNTIME_ID="${'$'}user_lock_runtime_id" \
            |  KF_USER_LOCK_EXACT_COMMAND="${'$'}user_lock_exact_command" \
            |  KF_USER_LOCK_PID_FILE="${'$'}user_lock_pid_file" \
            |  KF_USER_LOCK_DISPLAY_NAME="${'$'}user_lock_display_name" \
            |  KF_USER_LOCK_MEMORY_KB="${'$'}user_lock_memory_kb" \
            |  python3 - <<'PY'
            |import json
            |import os
            |import posixpath
            |import re
            |import sys
            |
            |manifest_path = os.environ.get("KF_USER_LOCK_MANIFEST", "/workspace/.kf/runtime-process-manifest.json")
            |action = os.environ.get("KF_USER_LOCK_ACTION", "").strip()
            |unit_id = os.environ.get("KF_USER_LOCK_UNIT_ID", "").strip()
            |runtime_id = os.environ.get("KF_USER_LOCK_RUNTIME_ID", "").strip()
            |exact_command = os.environ.get("KF_USER_LOCK_EXACT_COMMAND", "").strip()
            |pid_file = os.environ.get("KF_USER_LOCK_PID_FILE", "").strip()
            |display_name = os.environ.get("KF_USER_LOCK_DISPLAY_NAME", "").strip()
            |memory_raw = os.environ.get("KF_USER_LOCK_MEMORY_KB", "").strip()
            |
            |def emit(status, unit, reason):
            |    print("kf_runtime_user_lock_status=" + status)
            |    print("kf_runtime_user_lock_unit_id=" + (unit or "none"))
            |    print("kf_runtime_user_lock_reason=" + reason)
            |    print("kf_runtime_user_lock_manifest_path=" + manifest_path)
            |
            |def reject(unit, reason, code=2):
            |    emit("rejected", unit, reason)
            |    sys.exit(code)
            |
            |def safe_unit_id(prefix, value):
            |    safe = re.sub(r"[^a-z0-9_-]+", "-", value.lower())
            |    safe = re.sub(r"-+", "-", safe).strip("-") or "runtime"
            |    return "user-locked-%s-%s" % (prefix, safe)
            |
            |def targets_builtin_core(text):
            |    n = (text or "").lower()
            |    return (
            |        "container-supervisor" in n or
            |        "container_supervisor" in n or
            |        "critical-core" in n or
            |        "critical_core" in n or
            |        "proot-capacity-worker-1" in n or
            |        "proot_capacity_worker_1" in n or
            |        n.endswith("-proot-1")
            |    )
            |
            |def allowed_pid_file(path):
            |    if not path.startswith("/"):
            |        return False
            |    normalized = posixpath.normpath(path)
            |    if normalized != path or "/../" in path or path.endswith("/.."):
            |        return False
            |    allowed = ("/run/", "/tmp/", "/workspace/", "/workspace/.kf/")
            |    return normalized in ("/run", "/tmp", "/workspace", "/workspace/.kf") or normalized.startswith(allowed)
            |
            |def read_manifest():
            |    if os.path.exists(manifest_path) and os.path.getsize(manifest_path) > 0:
            |        with open(manifest_path, "r", encoding="utf-8") as handle:
            |            try:
            |                value = json.load(handle)
            |            except Exception:
            |                value = {}
            |    else:
            |        value = {}
            |    value.setdefault("version", 1)
            |    value.setdefault("authority", "ubuntu_advisory")
            |    value.setdefault("boundary", "declaration_only_android_observes_no_direct_kill_restart_or_quarantine")
            |    units = value.get("units")
            |    if not isinstance(units, list):
            |        value["units"] = []
            |    return value
            |
            |def write_manifest(value):
            |    directory = os.path.dirname(manifest_path)
            |    if directory:
            |        os.makedirs(directory, exist_ok=True)
            |    tmp = manifest_path + ".tmp"
            |    with open(tmp, "w", encoding="utf-8") as handle:
            |        json.dump(value, handle, ensure_ascii=False, indent=2)
            |        handle.write("\n")
            |    os.replace(tmp, manifest_path)
            |
            |def memory_limit():
            |    if not memory_raw:
            |        return None
            |    try:
            |        value = int(memory_raw)
            |    except ValueError:
            |        reject(unit_id, "memory_kb_must_be_positive_integer")
            |    if value <= 0:
            |        reject(unit_id, "memory_kb_must_be_positive_integer")
            |    return value
            |
            |if action == "unlock":
            |    if not unit_id:
            |        reject("none", "unit_id_required")
            |    manifest = read_manifest()
            |    before = len(manifest["units"])
            |    manifest["units"] = [unit for unit in manifest["units"] if unit.get("id") != unit_id]
            |    if len(manifest["units"]) == before:
            |        emit("not_found", unit_id, "user_lock_declaration_not_found")
            |        sys.exit(1)
            |    write_manifest(manifest)
            |    emit("removed", unit_id, "user_lock_declaration_removed_no_runtime_action")
            |    sys.exit(0)
            |
            |if action not in ("lock-runtime", "lock-command", "lock-pid-file"):
            |    reject("none", "unsupported_user_lock_action")
            |
            |match = {}
            |if action == "lock-runtime":
            |    if not runtime_id:
            |        reject("none", "runtime_id_required")
            |    unit_id = safe_unit_id("runtime", runtime_id)
            |    if targets_builtin_core(runtime_id):
            |        reject(unit_id, "built_in_core_or_proot_one_cannot_be_user_locked")
            |    match["runtimeId"] = runtime_id
            |elif action == "lock-command":
            |    if not unit_id:
            |        reject("none", "unit_id_required")
            |    if not exact_command:
            |        reject(unit_id, "exact_command_required")
            |    if targets_builtin_core(exact_command):
            |        reject(unit_id, "built_in_core_or_proot_one_cannot_be_user_locked")
            |    match["exactCommand"] = exact_command
            |elif action == "lock-pid-file":
            |    if not unit_id:
            |        reject("none", "unit_id_required")
            |    if not pid_file:
            |        reject(unit_id, "pid_file_required")
            |    if not allowed_pid_file(pid_file):
            |        reject(unit_id, "pid_file_path_not_allowed")
            |    if exact_command:
            |        if targets_builtin_core(exact_command):
            |            reject(unit_id, "built_in_core_or_proot_one_cannot_be_user_locked")
            |        match["exactCommand"] = exact_command
            |    match["pidFile"] = pid_file
            |
            |limit = memory_limit()
            |resource = {"unlimitedMemory": False}
            |if limit:
            |    resource["expectedMemoryLimitKb"] = limit
            |
            |unit = {
            |    "id": unit_id,
            |    "displayName": display_name or unit_id,
            |    "tier": "USER_LOCKED",
            |    "manualKillPolicy": "WAIT_CONFIRM",
            |    "match": match,
            |    "exec": {"stopMode": "NONE", "restartMode": "NEVER"},
            |    "protection": {
            |        "userEditable": True,
            |        "allowReclaim": False,
            |        "allowKill": False,
            |        "allowRestart": False,
            |        "requiresMemoryAdmission": False
            |    },
            |    "resource": resource
            |}
            |
            |manifest = read_manifest()
            |replaced = False
            |next_units = []
            |for existing in manifest["units"]:
            |    if existing.get("id") == unit_id:
            |        next_units.append(unit)
            |        replaced = True
            |    else:
            |        next_units.append(existing)
            |if not replaced:
            |    next_units.append(unit)
            |manifest["units"] = next_units
            |write_manifest(manifest)
            |emit("written", unit_id, "user_lock_declaration_updated_no_runtime_action" if replaced else "user_lock_declaration_created_no_runtime_action")
            |PY
            |}
            |
            |lock_runtime() {
            |  runtime="${'$'}{1:-}"
            |  if [ -z "${'$'}runtime" ]; then
            |    echo "error: runtime id required" >&2
            |    return 2
            |  fi
            |  shift
            |  parse_user_lock_options "${'$'}@" || return "${'$'}?"
            |  user_lock_action="lock-runtime"
            |  user_lock_unit_id=""
            |  user_lock_runtime_id="${'$'}runtime"
            |  user_lock_exact_command=""
            |  user_lock_pid_file=""
            |  user_lock_display_name="${'$'}{lock_name:-${'$'}runtime}"
            |  user_lock_memory_kb="${'$'}lock_memory_kb"
            |  run_user_lock_writer
            |}
            |
            |lock_command() {
            |  unit="${'$'}{1:-}"
            |  exact="${'$'}{2:-}"
            |  if [ -z "${'$'}unit" ] || [ -z "${'$'}exact" ]; then
            |    echo "error: unit id and exact command required" >&2
            |    return 2
            |  fi
            |  shift 2
            |  parse_user_lock_options "${'$'}@" || return "${'$'}?"
            |  user_lock_action="lock-command"
            |  user_lock_unit_id="${'$'}unit"
            |  user_lock_runtime_id=""
            |  user_lock_exact_command="${'$'}exact"
            |  user_lock_pid_file=""
            |  user_lock_display_name="${'$'}{lock_name:-${'$'}unit}"
            |  user_lock_memory_kb="${'$'}lock_memory_kb"
            |  run_user_lock_writer
            |}
            |
            |lock_pid_file() {
            |  unit="${'$'}{1:-}"
            |  pid_file="${'$'}{2:-}"
            |  if [ -z "${'$'}unit" ] || [ -z "${'$'}pid_file" ]; then
            |    echo "error: unit id and pidFile required" >&2
            |    return 2
            |  fi
            |  shift 2
            |  parse_user_lock_options "${'$'}@" || return "${'$'}?"
            |  user_lock_action="lock-pid-file"
            |  user_lock_unit_id="${'$'}unit"
            |  user_lock_runtime_id=""
            |  user_lock_exact_command="${'$'}lock_exact_command"
            |  user_lock_pid_file="${'$'}pid_file"
            |  user_lock_display_name="${'$'}{lock_name:-${'$'}unit}"
            |  user_lock_memory_kb="${'$'}lock_memory_kb"
            |  run_user_lock_writer
            |}
            |
            |unlock_user_lock() {
            |  unit="${'$'}{1:-}"
            |  if [ -z "${'$'}unit" ]; then
            |    echo "error: unit id required" >&2
            |    return 2
            |  fi
            |  user_lock_action="unlock"
            |  user_lock_unit_id="${'$'}unit"
            |  user_lock_runtime_id=""
            |  user_lock_exact_command=""
            |  user_lock_pid_file=""
            |  user_lock_display_name=""
            |  user_lock_memory_kb=""
            |  run_user_lock_writer
            |}
            |
            |runtime_workload_policy_path() {
            |  printf '%s' "${'$'}{KF_RUNTIME_WORKLOAD_POLICY_PATH:-${CONTAINER_RUNTIME_WORKLOAD_POLICY_PATH}}"
            |}
            |
            |run_lifecycle_policy_writer() {
            |  lifecycle_action="${'$'}{1:-status}"
            |  if ! command -v python3 >/dev/null 2>&1; then
            |    echo "error: kf-runtime lifecycle needs python3 to edit runtime-workload-policy.json safely" >&2
            |    return 127
            |  fi
            |  KF_LIFECYCLE_POLICY="$(runtime_workload_policy_path)" \
            |  KF_LIFECYCLE_ACTION="${'$'}lifecycle_action" \
            |  python3 - <<'PY'
            |import json
            |import os
            |import sys
            |
            |policy_path = os.environ.get("KF_LIFECYCLE_POLICY", "/workspace/.kf/runtime-workload-policy.json")
            |action = os.environ.get("KF_LIFECYCLE_ACTION", "status").strip().lower()
            |
            |def read_policy():
            |    if os.path.exists(policy_path) and os.path.getsize(policy_path) > 0:
            |        try:
            |            with open(policy_path, "r", encoding="utf-8") as handle:
            |                value = json.load(handle)
            |                if isinstance(value, dict):
            |                    return value
            |        except Exception:
            |            pass
            |    return {}
            |
            |def write_policy(value):
            |    directory = os.path.dirname(policy_path)
            |    if directory:
            |        os.makedirs(directory, exist_ok=True)
            |    tmp = policy_path + ".tmp"
            |    with open(tmp, "w", encoding="utf-8") as handle:
            |        json.dump(value, handle, ensure_ascii=False, indent=2)
            |        handle.write("\n")
            |    os.replace(tmp, policy_path)
            |
            |def normalize(value):
            |    value.setdefault("version", 1)
            |    value.setdefault("lifecycleStrategyGroup", "balanced_default")
            |    value.setdefault("authority", "android_control_plane")
            |    value.setdefault("telemetrySource", "proot_lifecycle_telemetry_v0+android_proc_snapshot_current")
            |    return value
            |
            |if action in ("on", "enable", "enabled"):
            |    enabled = True
            |    changed = True
            |elif action in ("off", "disable", "disabled"):
            |    enabled = False
            |    changed = True
            |elif action in ("status", "show", ""):
            |    enabled = None
            |    changed = False
            |else:
            |    print("error: unsupported lifecycle command: " + action, file=sys.stderr)
            |    sys.exit(2)
            |
            |policy = normalize(read_policy())
            |if enabled is not None:
            |    policy["lifecycleManagementEnabled"] = enabled
            |    write_policy(policy)
            |
            |current = bool(policy.get("lifecycleManagementEnabled", False))
            |print("kf_runtime_lifecycle_status=" + ("enabled" if current else "disabled"))
            |print("kf_runtime_lifecycle_management_enabled=" + ("true" if current else "false"))
            |print("kf_runtime_lifecycle_action=" + (action or "status"))
            |print("kf_runtime_lifecycle_policy_path=" + policy_path)
            |print("kf_runtime_lifecycle_strategy_group=" + str(policy.get("lifecycleStrategyGroup", "balanced_default")))
            |print("kf_runtime_lifecycle_semantics=monitoring_to_time_ledger_judgment_to_android_kf_execution_boundary")
            |print("kf_runtime_lifecycle_runtime_action=none_no_start_no_stop_no_kill_no_reclaim_no_restart_no_proot")
            |PY
            |}
            |
            |lifecycle_command() {
            |  case "${'$'}{1:-status}" in
            |    enable|on)
            |      run_lifecycle_policy_writer enable
            |      ;;
            |    disable|off)
            |      run_lifecycle_policy_writer disable
            |      ;;
            |    status|show|"")
            |      run_lifecycle_policy_writer status
            |      ;;
            |    -h|--help|help)
            |      usage
            |      ;;
            |    *)
            |      echo "error: unsupported kf-runtime lifecycle command: ${'$'}1" >&2
            |      usage >&2
            |      exit 2
            |      ;;
            |  esac
            |}
            |
            |register_controlled_lease_probe() {
            |  space_id="$(parse_space_id "${'$'}@")" || return "${'$'}?"
            |  command="am broadcast -a ${'$'}register_action -p com.kite.app"
            |  if [ -n "${'$'}space_id" ]; then
            |    command="${'$'}command --es ${'$'}space_extra ${'$'}space_id"
            |  fi
            |
            |  if command -v kf-adb-bridge >/dev/null 2>&1; then
            |    kf-adb-bridge adb shell "${'$'}command"
            |  elif command -v adb >/dev/null 2>&1; then
            |    adb shell "${'$'}command"
            |  else
            |    echo "error: kf-runtime needs kf-adb-bridge or adb to reach the Android KF control plane" >&2
            |    return 127
            |  fi
            |  code="${'$'}?"
            |  if [ "${'$'}code" -eq 0 ]; then
            |    echo "kf_runtime_entrypoint=lease_probe_register"
            |    echo "kf_runtime_registered_runtime_id=${'$'}runtime_id"
            |    echo "kf_runtime_registered_unit_id=${'$'}unit_id"
            |    echo "kf_runtime_registration_semantics=debug_register_only_no_process_root_no_start_no_kill_no_reclaim_no_restart_no_proot"
            |    echo "kf_runtime_probe_start_command=${'$'}probe_command"
            |    echo "kf_runtime_next_check=kf-env runtime"
            |  fi
            |  return "${'$'}code"
            |}
            |
            |print_probe_command() {
            |  echo "kf_runtime_probe_start_command=${'$'}probe_command"
            |  echo "kf_runtime_probe_unit_id=${'$'}unit_id"
            |  echo "kf_runtime_probe_runtime_id=${'$'}runtime_id"
            |  echo "kf_runtime_probe_match=runtime_process_manifest_exactCommand"
            |  echo "kf_runtime_probe_semantics=ubuntu_command_creates_real_process_kf_observes_lease"
            |  echo "${'$'}probe_command"
            |}
            |
            |print_probe_status() {
            |  runtime_file="${'$'}{KF_RUNTIME_PRESSURE_PATH:-${CONTAINER_RUNTIME_PRESSURE_PATH}}"
            |  echo "kf_runtime_probe_start_command=${'$'}probe_command"
            |  echo "kf_runtime_probe_runtime_id=${'$'}runtime_id"
            |  echo "kf_runtime_probe_unit_id=${'$'}unit_id"
            |  echo "kf_runtime_probe_pressure_path=${'$'}runtime_file"
            |  if [ -r "${'$'}runtime_file" ]; then
            |    grep -E "(${runtimeId}|${unitId}|lifecycle_reclaim_plan_item_.*(lease_remaining|activity_state)|lifecycle_policy_surface_activity_|runtime_process_unit_.*(tier|match_source|match_state)|workload_.*(retention|class))" "${'$'}runtime_file" || true
            |  else
            |    echo "kf_runtime_probe_status=runtime_pressure_file_missing"
            |  fi
            |}
            |
            |case "${'$'}{1:-help}" in
            |  lease-probe)
            |    shift
            |    case "${'$'}{1:-help}" in
            |      command|start-command)
            |        print_probe_command
            |        ;;
            |      register)
            |        shift
            |        register_controlled_lease_probe "${'$'}@"
            |        ;;
            |      status)
            |        print_probe_status
            |        ;;
            |      -h|--help|help)
            |        usage
            |        ;;
            |      *)
            |        echo "error: unsupported kf-runtime lease-probe command: ${'$'}1" >&2
            |        usage >&2
            |        exit 2
            |        ;;
            |    esac
            |    ;;
            |  register-controlled-lease-probe)
            |    shift
            |    register_controlled_lease_probe "${'$'}@"
            |    ;;
            |  lock)
            |    shift
            |    case "${'$'}{1:-help}" in
            |      runtime)
            |        shift
            |        lock_runtime "${'$'}@"
            |        ;;
            |      command)
            |        shift
            |        lock_command "${'$'}@"
            |        ;;
            |      pid-file)
            |        shift
            |        lock_pid_file "${'$'}@"
            |        ;;
            |      -h|--help|help)
            |        usage
            |        ;;
            |      *|commandContains|command-contains)
            |        echo "error: user lock requires runtime, command, or pid-file strong match" >&2
            |        usage >&2
            |        exit 2
            |        ;;
            |    esac
            |    ;;
            |  unlock)
            |    shift
            |    unlock_user_lock "${'$'}@"
            |    ;;
            |  lifecycle)
            |    shift
            |    lifecycle_command "${'$'}@"
            |    ;;
            |  enable-lifecycle)
            |    run_lifecycle_policy_writer enable
            |    ;;
            |  disable-lifecycle)
            |    run_lifecycle_policy_writer disable
            |    ;;
            |  status)
            |    print_probe_status
            |    ;;
            |  -h|--help|help)
            |    usage
            |    ;;
            |  *)
            |    echo "error: unsupported kf-runtime command: ${'$'}1" >&2
            |    usage >&2
            |    exit 2
            |    ;;
            |esac
        """.trimMargin() + "\n"
    }

    private fun syncToolchainCommandWrappers(workspaceDir: File, helperBinDir: File) {
        val managedStaticNames = setOf(
            HELPER_SCRIPT_NAME,
            FD_WRAPPER_NAME,
            SS_SHIM_NAME,
            PS_SHIM_NAME,
            PGREP_SHIM_NAME,
            PKILL_SHIM_NAME,
            KILL_SHIM_NAME,
            PROOT_SHIM_NAME,
            SYSTEMCTL_SHIM_NAME,
            SERVICE_SHIM_NAME,
            SUPERVISORCTL_WRAPPER_NAME,
            ADB_WRAPPER_NAME,
            ADB_CHECK_SCRIPT_NAME,
            ADB_BRIDGE_SCRIPT_NAME,
            ANDROID_SHELL_BRIDGE_SCRIPT_NAME,
            HOST_SURFACE_SCRIPT_NAME,
            ENV_SURFACE_SCRIPT_NAME,
            RUNTIME_SURFACE_SCRIPT_NAME,
            "node",
            "npm",
            "npx",
            "pnpm",
            "pnpx",
            "uv",
            "corepack",
            "adb",
            "fastboot"
        )

        val toolCommands = linkedSetOf<String>()
        listOf(
            helperToolchainDir(workspaceDir),
            workspaceDir
        ).forEach { root ->
            root.listFiles()
                ?.filter { candidate ->
                    candidate.isDirectory &&
                        candidate.name.startsWith("node-v") &&
                        File(candidate, "bin").isDirectory
                }
                ?.sortedByDescending { it.name }
                ?.forEach { toolchainDir ->
                    toolchainDir.resolve("bin").listFiles()
                        ?.filter { entry ->
                            entry.isFile &&
                                entry.canExecute() &&
                                entry.name !in managedStaticNames
                        }
                        ?.forEach { entry ->
                            toolCommands += entry.name
                        }
                }
        }

        helperBinDir.listFiles()
            ?.filter { file ->
                file.isFile &&
                    file.name !in managedStaticNames &&
                    file.readText().startsWith(GENERATED_TOOL_WRAPPER_MARKER) &&
                    file.name !in toolCommands
            }
            ?.forEach { stale ->
                stale.delete()
            }

        toolCommands.forEach { commandName ->
            val wrapperFile = File(helperBinDir, commandName)
            writeTextIfChanged(wrapperFile, buildToolchainCommandWrapper(commandName))
            wrapperFile.setExecutable(true, false)
        }
    }

    private fun buildToolchainCommandWrapper(commandName: String): String {
        return """
            |#!/usr/bin/env sh
            |${GENERATED_TOOL_WRAPPER_MARKER}
            |for candidate in \
            |  /workspace/.kf/toolchains/node-v*/bin/${commandName} \
            |  /workspace/node-v*/bin/${commandName}
            |do
            |  if [ -x "${'$'}candidate" ]; then
            |    exec "${'$'}candidate" "${'$'}@"
            |  fi
            |done
            |echo "${commandName}: command not found in KFShell toolchains" >&2
            |exit 127
        """.trimMargin() + "\n"
    }

    private fun writeTextIfChanged(file: File, content: String) {
        file.parentFile?.let { parent ->
            if (!parent.exists()) {
                parent.mkdirs()
            }
        }
        if (Files.isSymbolicLink(file.toPath())) {
            file.delete()
        }
        if (file.exists() && file.readText() == content) {
            return
        }
        file.writeText(content)
    }

    private fun writeWorkspaceRootReadme(workspaceDir: File) {
        writeTextIfChanged(
            File(workspaceDir, "README.txt"),
            """
            欢迎使用 KF 工作区。
            这个目录会被挂载到容器内的 `${CONTAINER_WORKSPACE_ROOT}`。
            标准项目根默认是 `${DEFAULT_PROJECT_DIR}`。
            建议把脚本、源代码和调试输出都放在这里，方便在容器内外共享。
            如需在手机容器里构建 Android 工程，可优先使用 `kf-gradle doctor|compile|assemble`。
            """.trimIndent() + "\n"
        )
    }
}
