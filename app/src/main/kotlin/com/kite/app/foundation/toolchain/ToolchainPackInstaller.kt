package com.kite.app.foundation.toolchain

import android.content.Context
import android.system.Os
import com.kite.app.foundation.capability.CapabilityCallerType
import com.kite.app.foundation.capability.CapabilityDomain
import com.kite.app.foundation.capability.CapabilityGate
import com.kite.app.foundation.capability.CapabilityOutputLevel
import com.kite.app.foundation.capability.CapabilityRequest
import com.kite.app.foundation.logging.Logger
import com.kite.app.foundation.runtime.RuntimeBootstrapProgress
import com.kite.app.foundation.workspace.WorkSurfaceRuntimeBridge
import com.kite.app.foundation.workspace.WorkspaceBuildSupport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

object ToolchainPackInstaller {
    private const val LOG_TAG = "ToolchainPackInstaller"
    private const val PACK_ID = "ai-dev-pack"
    private const val ASSET_ROOT = "toolchain/ai-dev-pack"
    private const val LOG_FILE = "toolchain-install.log"
    private const val STATUS_FILE = "status.json"
    private const val INSTALL_TIMEOUT_SECONDS = 900L
    private const val OUTPUT_LIMIT = 48_000
    private const val BOOTSTRAP_MAXIMUM_CONCURRENCY = 2
    const val BOOTSTRAP_RESOURCE_RUN_PREFIX = "bootstrap:"
    const val RESOURCE_NODEJS = "kite.nodejs"
    private const val RESOURCE_PYTHON = "kite.python"
    private const val RESOURCE_UV = "kite.uv"
    private const val RESOURCE_GIT = "kite.git"
    private const val RESOURCE_CURL = "kite.curl"
    private const val RESOURCE_TOOL_ENV = "kite.tool.env"
    private const val NODE_VERSION = "26.4.0"
    private const val PYTHON_VERSION = "3.14.6"
    private const val UV_VERSION = "0.11.25"
    private val BOOTSTRAP_RESOURCES = listOf(
        BootstrapResource(
            resourceId = RESOURCE_NODEJS,
            mode = "--install-node",
            version = NODE_VERSION,
            label = "Node.js",
            requiredPaths = listOf(".kf/bin/node", ".kf/bin/npm", ".kf/bin/npx"),
            anyRuntimePaths = listOf(
                ".kf/software/kite.nodejs/node-v26.4.0/bin/node",
                ".kf/components/kite.nodejs/node-v26.4.0/bin/node",
                ".kf/toolchains/node-v26.4.0/bin/node"
            )
        ),
        BootstrapResource(
            resourceId = RESOURCE_PYTHON,
            mode = "--install-python",
            version = PYTHON_VERSION,
            label = "Python",
            requiredPaths = listOf(".kf/bin/python3"),
            anyRuntimePaths = listOf(
                ".kf/software/kite.python/python-3.14.6/bin/python3.14",
                ".kf/toolchains/python-3.14.6/bin/python3.14"
            )
        ),
        BootstrapResource(
            resourceId = RESOURCE_UV,
            mode = "--install-uv",
            version = UV_VERSION,
            label = "uv",
            requiredPaths = listOf(
                ".kf/software/kite.uv/uv-0.11.25/uv",
                ".kf/bin/uv"
            )
        ),
        BootstrapResource(
            resourceId = RESOURCE_GIT,
            mode = "--install-git",
            version = "rootfs",
            label = "Git",
            requiredPaths = listOf(".kf/software/kite.git/bin/git", ".kf/bin/git")
        ),
        BootstrapResource(
            resourceId = RESOURCE_CURL,
            mode = "--install-curl",
            version = "rootfs",
            label = "curl",
            requiredPaths = listOf(".kf/software/kite.curl/bin/curl", ".kf/bin/curl")
        ),
        BootstrapResource(
            resourceId = RESOURCE_TOOL_ENV,
            mode = "--install-system-tools",
            version = "v16",
            label = "系统工具集合",
            dependencies = setOf(RESOURCE_NODEJS),
            requiredPaths = listOf(
                ".kf/software/kite.tool.env/pnpm-11.9.0/package/bin/pnpm.cjs",
                ".kf/bin/pnpm",
                ".kf/bin/wget",
                ".kf/bin/jq",
                ".kf/bin/rg",
                ".kf/bin/fd",
                ".kf/bin/zip"
            )
        )
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val running = AtomicBoolean(false)
    private val logLock = Any()
    private val _state = MutableStateFlow(ToolchainInstallState())

    val state: StateFlow<ToolchainInstallState> = _state.asStateFlow()

    fun prepareAiEnv(context: Context) {
        runPack(context, ToolchainAction.PREPARE)
    }

    fun prepareAiEnvForBootstrap(context: Context): ToolchainInstallState {
        return runPackBlocking(context, ToolchainAction.PREPARE, reportBootstrapProgress = true)
    }

    fun prepareNode(context: Context) {
        runPack(context, ToolchainAction.NODE)
    }

    fun doctor(context: Context) {
        runPack(context, ToolchainAction.DOCTOR)
    }

    fun stageLocalResourcePack(context: Context, resourceId: String): ToolchainPackManifest {
        val appContext = context.applicationContext
        val manifest = extractRuntimePack(appContext)
        mirrorPackIntoSharedResourceCache(appContext, resourceId)
        return manifest
    }

    @Suppress("UNUSED_PARAMETER")
    fun resourcePackWorkspacePath(resourceId: String): String {
        return "/workspace/.kf/cache/shared/$PACK_ID"
    }

    fun isNodeRuntimeInstalled(context: Context): Boolean {
        val workspaceDir = workspaceDirOrNull(context.applicationContext) ?: return false
        val newNode = File(workspaceDir, ".kf/software/kite.nodejs/node-v26.4.0/bin/node")
        val componentNode = File(workspaceDir, ".kf/components/kite.nodejs/node-v26.4.0/bin/node")
        val legacyNode = File(workspaceDir, ".kf/toolchains/node-v26.4.0/bin/node")
        return (workspacePathExists(newNode, workspaceDir) ||
            workspacePathExists(componentNode, workspaceDir) ||
            workspacePathExists(legacyNode, workspaceDir)) &&
            workspacePathExists(File(workspaceDir, ".kf/bin/node"), workspaceDir) &&
            workspacePathExists(File(workspaceDir, ".kf/bin/npm"), workspaceDir) &&
            workspacePathExists(File(workspaceDir, ".kf/bin/npx"), workspaceDir)
    }

    fun isPythonRuntimeInstalled(context: Context): Boolean {
        val workspaceDir = workspaceDirOrNull(context.applicationContext) ?: return false
        val resourcePython = File(workspaceDir, ".kf/software/kite.python/python-3.14.6/bin/python3.14")
        val legacyPython = File(workspaceDir, ".kf/toolchains/python-3.14.6/bin/python3.14")
        return (workspacePathExists(resourcePython, workspaceDir) ||
            workspacePathExists(legacyPython, workspaceDir)) &&
            workspacePathExists(File(workspaceDir, ".kf/bin/python3"), workspaceDir)
    }

    fun isToolchainPackInstalled(context: Context): Boolean {
        val workspaceDir = workspaceDirOrNull(context.applicationContext) ?: return false
        return isPythonRuntimeInstalled(context) &&
            isNodeRuntimeInstalled(context) &&
            workspacePathExists(File(workspaceDir, ".kf/bin/pnpm"), workspaceDir) &&
            workspacePathExists(File(workspaceDir, ".kf/bin/uv"), workspaceDir) &&
            workspacePathExists(File(workspaceDir, ".kf/bin/curl"), workspaceDir) &&
            workspacePathExists(File(workspaceDir, ".kf/bin/git"), workspaceDir) &&
            workspacePathExists(File(workspaceDir, ".kf/bin/wget"), workspaceDir) &&
            workspacePathExists(File(workspaceDir, ".kf/bin/jq"), workspaceDir) &&
            workspacePathExists(File(workspaceDir, ".kf/bin/rg"), workspaceDir) &&
            workspacePathExists(File(workspaceDir, ".kf/bin/fd"), workspaceDir) &&
            workspacePathExists(File(workspaceDir, ".kf/bin/zip"), workspaceDir)
    }

    fun bootstrapResourcesSettled(context: Context): Boolean {
        return runCatching {
            val port = ToolchainResourcePortHost.get()
            val environmentId = port.currentEnvironmentId(context.applicationContext)
            val ids = BOOTSTRAP_RESOURCES.map { it.resourceId }
            ids.all { id ->
                bootstrapResourceStatusSettled(port.statusOf(context.applicationContext, id, environmentId))
            }
        }.getOrDefault(false)
    }

    internal fun bootstrapResourceStatusSettled(status: String): Boolean =
        status == ToolchainResourcePort.STATUS_INSTALLED || status == ToolchainResourcePort.STATUS_FAILED

    fun refreshState(context: Context) {
        _state.value = readStatus(context.applicationContext)
    }

    fun logFile(context: Context): File {
        return File(WorkSurfaceRuntimeBridge.getLogsDir(context.applicationContext), LOG_FILE)
    }

    private fun runPack(context: Context, action: ToolchainAction) {
        val appContext = context.applicationContext
        auditToolchainCapability(action)
        if (!running.compareAndSet(false, true)) {
            Logger.i(LOG_TAG, "Ignore $action request while installer is already running")
            return
        }
        scope.launch {
            try {
                runPackLocked(appContext, action)
            } finally {
                running.set(false)
            }
        }
    }

    private fun runPackBlocking(
        context: Context,
        action: ToolchainAction,
        reportBootstrapProgress: Boolean = false
    ): ToolchainInstallState {
        val appContext = context.applicationContext
        auditToolchainCapability(action)
        if (!running.compareAndSet(false, true)) {
            Logger.i(LOG_TAG, "Ignore blocking $action request while installer is already running")
            return _state.value
        }
        return try {
            runPackLocked(appContext, action, reportBootstrapProgress)
        } finally {
            running.set(false)
        }
    }

    private fun runPackLocked(
        appContext: Context,
        action: ToolchainAction,
        reportBootstrapProgress: Boolean = false
    ): ToolchainInstallState {
        val startedAt = System.currentTimeMillis()
        _state.value = ToolchainInstallState(
            phase = ToolchainInstallPhase.RUNNING,
            action = action.name.lowercase(),
            startedAt = startedAt,
            updatedAt = startedAt,
            summary = "Preparing $PACK_ID"
        )
        appendLog(appContext, "== $PACK_ID ${action.name.lowercase()} start ==")
        if (action == ToolchainAction.PREPARE) {
            return runPreparePackLocked(appContext, startedAt, reportBootstrapProgress)
        }
        return runCatching {
            val manifest = extractRuntimePack(appContext)
            val workspacePackDir = mirrorPackIntoWorkspace(appContext)
            val result = executeInstallScript(
                context = appContext,
                action = action,
                workspacePackDir = workspacePackDir
            )
            val phase = if (result.exitCode == 0 && !result.timedOut) {
                ToolchainInstallPhase.SUCCEEDED
            } else {
                ToolchainInstallPhase.FAILED
            }
            val summary = result.summaryLine()
                ?: "exitCode=${result.exitCode} timedOut=${result.timedOut}"
            ToolchainInstallState(
                phase = phase,
                action = action.name.lowercase(),
                packId = manifest.packId,
                packVersion = manifest.version,
                startedAt = startedAt,
                updatedAt = System.currentTimeMillis(),
                exitCode = result.exitCode,
                timedOut = result.timedOut,
                summary = summary,
                logPath = logFile(appContext).absolutePath,
                outputPreview = result.output.takeLast(4_000)
            ).also { status ->
                _state.value = status
                writeStatus(appContext, status)
                appendLog(appContext, result.toLogBlock())
                Logger.i(LOG_TAG, "$PACK_ID ${action.name.lowercase()} complete: $summary")
            }
        }.getOrElse { error ->
            ToolchainInstallState(
                phase = ToolchainInstallPhase.FAILED,
                action = action.name.lowercase(),
                startedAt = startedAt,
                updatedAt = System.currentTimeMillis(),
                summary = error.message ?: error::class.java.simpleName,
                logPath = logFile(appContext).absolutePath,
                outputPreview = error.stackTraceToString().take(4_000)
            ).also { status ->
                _state.value = status
                writeStatus(appContext, status)
                appendLog(appContext, "== $PACK_ID ${action.name.lowercase()} failed ==\n${error.stackTraceToString()}")
                Logger.e(LOG_TAG, "$PACK_ID ${action.name.lowercase()} failed: ${error.message}")
            }
        }
    }

    private fun runPreparePackLocked(
        appContext: Context,
        startedAt: Long,
        reportBootstrapProgress: Boolean
    ): ToolchainInstallState {
        return runCatching {
            val manifest = extractRuntimePack(appContext)
            val totalSteps = BOOTSTRAP_RESOURCES.size
            val installRunner = BootstrapInstallRunner(ToolchainResourcePortHost.get())
            val resourcesById = BOOTSTRAP_RESOURCES.associateBy(BootstrapResource::resourceId)
            val decision = DependencyBatchScheduler.executeOrdered(
                tasks = BOOTSTRAP_RESOURCES.mapIndexed { index, resource ->
                    DependencyBatchTask(
                        key = resource.resourceId,
                        dependencies = resource.dependencies,
                    ) {
                        val stepIndex = index + 1
                        if (reportBootstrapProgress) {
                            RuntimeBootstrapProgress.bundledToolStarted(resource.label, stepIndex, totalSteps)
                        }
                        val runId = "$BOOTSTRAP_RESOURCE_RUN_PREFIX${resource.resourceId}"
                        val result = installRunner.run(
                            context = appContext,
                            resourceId = resource.resourceId,
                            targetVersion = resource.version,
                            runId = runId,
                            alreadyReady = isBootstrapResourceReady(appContext, resource)
                        ) {
                            val resourcePackDir = mirrorPackIntoSharedResourceCache(appContext, resource.resourceId)
                            executeInstallScript(
                                context = appContext,
                                mode = resource.mode,
                                workspacePackDir = resourcePackDir,
                                workspacePackPath = resourcePackWorkspacePath(resource.resourceId),
                                toolchainDir = "/workspace/.kf/software/${safeResourceId(resource.resourceId)}",
                                binDir = WorkspaceBuildSupport.CONTAINER_HELPER_BIN_PATH
                            )
                        }
                        if (reportBootstrapProgress) {
                            RuntimeBootstrapProgress.bundledToolCompleted(
                                resource.label,
                                stepIndex,
                                totalSteps,
                                result.exitCode != 0 || result.timedOut
                            )
                        }
                        appendLog(appContext, result.toLogBlock(resource.resourceId))
                        result
                    }
                },
                maximumConcurrency = BOOTSTRAP_MAXIMUM_CONCURRENCY,
                isSuccessful = { result -> result.exitCode == 0 && !result.timedOut },
            )
            val report = when (decision) {
                is DependencyBatchDecision.Completed -> decision.report
                is DependencyBatchDecision.Blocked -> error(decision.reason)
            }
            val resourceResults = report.outcomes.map { outcome ->
                val resource = checkNotNull(resourcesById[outcome.key])
                val result = when (outcome) {
                    is DependencyBatchTaskOutcome.Executed -> outcome.value
                        ?: installRunner.failWithoutExecution(
                            context = appContext,
                            resourceId = resource.resourceId,
                            runId = "$BOOTSTRAP_RESOURCE_RUN_PREFIX${resource.resourceId}",
                            reason = outcome.failureReason ?: "dependency_batch_task_failed",
                        )
                    is DependencyBatchTaskOutcome.DependencyBlocked -> installRunner.failWithoutExecution(
                        context = appContext,
                        resourceId = resource.resourceId,
                        runId = "$BOOTSTRAP_RESOURCE_RUN_PREFIX${resource.resourceId}",
                        reason = "dependency_failed:${outcome.failedDependencies.joinToString(",")}",
                    )
                }
                val executedWithResult = outcome is DependencyBatchTaskOutcome.Executed && outcome.value != null
                if (!executedWithResult && reportBootstrapProgress) {
                    RuntimeBootstrapProgress.bundledToolCompleted(
                        resource.label,
                        BOOTSTRAP_RESOURCES.indexOf(resource) + 1,
                        totalSteps,
                        failed = true,
                    )
                }
                if (!executedWithResult) {
                    appendLog(appContext, result.toLogBlock(resource.resourceId))
                }
                resource to result
            }

            val failedResources = resourceResults
                .filter { (_, result) -> result.exitCode != 0 || result.timedOut }
                .map { (resource, _) -> resource.resourceId }
            val phase = resolveBootstrapPhase(failedResources.size)
            val summary = buildString {
                append("SUMMARY resources=")
                append(BOOTSTRAP_RESOURCES.size)
                append(" failed=")
                append(failedResources.size)
                if (failedResources.isNotEmpty()) append(" ids=${failedResources.joinToString(",")}")
            }
            ToolchainInstallState(
                phase = phase,
                action = ToolchainAction.PREPARE.name.lowercase(),
                packId = manifest.packId,
                packVersion = manifest.version,
                startedAt = startedAt,
                updatedAt = System.currentTimeMillis(),
                exitCode = if (failedResources.isNotEmpty()) 2 else 0,
                timedOut = resourceResults.any { (_, result) -> result.timedOut },
                summary = summary,
                logPath = logFile(appContext).absolutePath,
                outputPreview = resourceResults.map { it.second }
                    .joinToString("\n") { it.output.takeLast(1_000) }
                    .takeLast(4_000)
            ).also { status ->
                _state.value = status
                writeStatus(appContext, status)
                Logger.i(LOG_TAG, "$PACK_ID prepare complete: $summary")
            }
        }.getOrElse { error ->
            markBootstrapResourcesFailed(appContext, error.message ?: error::class.java.simpleName)
            ToolchainInstallState(
                phase = ToolchainInstallPhase.FAILED,
                action = ToolchainAction.PREPARE.name.lowercase(),
                startedAt = startedAt,
                updatedAt = System.currentTimeMillis(),
                summary = error.message ?: error::class.java.simpleName,
                logPath = logFile(appContext).absolutePath,
                outputPreview = error.stackTraceToString().take(4_000)
            ).also { status ->
                _state.value = status
                writeStatus(appContext, status)
                appendLog(appContext, "== $PACK_ID prepare failed ==\n${error.stackTraceToString()}")
                Logger.e(LOG_TAG, "$PACK_ID prepare failed: ${error.message}")
            }
        }
    }

    private fun markBootstrapResourcesFailed(context: Context, reason: String) {
        runCatching {
            val port = ToolchainResourcePortHost.get()
            val environmentId = port.currentEnvironmentId(context)
            BOOTSTRAP_RESOURCES.forEach { resource ->
                port.markFailed(context, resource.resourceId, null, reason, environmentId)
            }
        }.onFailure { error ->
            Logger.e(LOG_TAG, "Failed to mark bootstrap resources failed: ${error.message}")
        }
    }

    internal fun resolveBootstrapResourceRegistration(
        resourceId: String,
        version: String,
        exitCode: Int,
        timedOut: Boolean,
        summary: String
    ): BootstrapResourceRegistration =
        BootstrapResourceRegistration(
            resourceId = resourceId,
            version = version,
            installed = exitCode == 0 && !timedOut,
            summary = summary
        )

    internal fun resolveBootstrapPhase(failedResourceCount: Int): ToolchainInstallPhase =
        if (failedResourceCount == 0) ToolchainInstallPhase.SUCCEEDED else ToolchainInstallPhase.FAILED

    internal data class BootstrapResourceRegistration(
        val resourceId: String,
        val version: String,
        val installed: Boolean,
        val summary: String
    )

    internal data class BootstrapResourceSchedulingContract(
        val resourceId: String,
        val mode: String,
        val dependencies: Set<String>,
    )

    internal fun bootstrapResourceSchedulingContracts(): List<BootstrapResourceSchedulingContract> =
        BOOTSTRAP_RESOURCES.map { resource ->
            BootstrapResourceSchedulingContract(
                resourceId = resource.resourceId,
                mode = resource.mode,
                dependencies = resource.dependencies,
            )
        }

    private data class BootstrapResource(
        val resourceId: String,
        val mode: String,
        val version: String,
        val label: String,
        val dependencies: Set<String> = emptySet(),
        val requiredPaths: List<String>,
        val anyRuntimePaths: List<String> = emptyList()
    )

    private fun isBootstrapResourceReady(context: Context, resource: BootstrapResource): Boolean {
        val workspaceDir = workspaceDirOrNull(context) ?: return false
        val requiredReady = resource.requiredPaths.all { relativePath ->
            workspacePathExists(File(workspaceDir, relativePath), workspaceDir)
        }
        val runtimeReady = resource.anyRuntimePaths.isEmpty() || resource.anyRuntimePaths.any { relativePath ->
            workspacePathExists(File(workspaceDir, relativePath), workspaceDir)
        }
        return requiredReady && runtimeReady
    }

    internal class BootstrapInstallRunner(
        private val port: ToolchainResourcePort
    ) {
        fun run(
            context: Context,
            resourceId: String,
            targetVersion: String,
            runId: String,
            alreadyReady: Boolean,
            install: () -> ToolchainCommandResult
        ): ToolchainCommandResult {
            val environmentId = port.currentEnvironmentId(context)
            val currentStatus = port.statusOf(context, resourceId, environmentId)
            val currentVersion = port.versionOf(context, resourceId, environmentId)
            if (alreadyReady) {
                val reused = ToolchainCommandResult(
                    exitCode = 0,
                    timedOut = false,
                    durationMs = 0L,
                    output = "SUMMARY PASS=1 WARN=0 FAIL=0 reused=$resourceId\n"
                )
                if (currentStatus != ToolchainResourcePort.STATUS_INSTALLED || currentVersion != targetVersion) {
                    val registrationError = runCatching {
                        port.markInstalled(
                            context,
                            resourceId,
                            targetVersion,
                            runId,
                            "已复用通过真实文件校验的系统组件",
                            environmentId
                        )
                    }.exceptionOrNull()
                    return if (registrationError == null) reused else reused.withFailure("register", registrationError)
                }
                return reused
            }
            port.markInstalling(context, resourceId, runId, environmentId)
            val result = runCatching { install() }
                .getOrElse { error -> transactionFailure("execute", error) }
            if (result.exitCode != 0 || result.timedOut) {
                return failInstall(context, resourceId, runId, environmentId, result)
            }

            val registration = resolveBootstrapResourceRegistration(
                resourceId = resourceId,
                version = targetVersion,
                exitCode = result.exitCode,
                timedOut = result.timedOut,
                summary = result.summaryLine() ?: "exitCode=${result.exitCode} timedOut=${result.timedOut}"
            )
            val registrationError = runCatching {
                port.markInstalled(
                    context,
                    registration.resourceId,
                    registration.version,
                    runId,
                    registration.summary,
                    environmentId
                )
            }.exceptionOrNull()
            if (registrationError != null) {
                return result.withFailure("register", registrationError)
            }
            return result
        }

        fun failWithoutExecution(
            context: Context,
            resourceId: String,
            runId: String,
            reason: String,
        ): ToolchainCommandResult {
            val result = ToolchainCommandResult(
                exitCode = 2,
                timedOut = false,
                durationMs = 0L,
                output = "FAIL\tdependency-batch\t$reason\nSUMMARY PASS=0 WARN=0 FAIL=1\n",
            )
            val registrationError = runCatching {
                val environmentId = port.currentEnvironmentId(context)
                port.markFailed(context, resourceId, runId, reason, environmentId)
            }.exceptionOrNull()
            return if (registrationError == null) result else result.withFailure("register", registrationError)
        }

        private fun failInstall(
            context: Context,
            resourceId: String,
            runId: String,
            environmentId: String,
            result: ToolchainCommandResult
        ): ToolchainCommandResult {
            runCatching {
                port.markFailed(
                    context,
                    resourceId,
                    runId,
                    result.summaryLine() ?: "exitCode=${result.exitCode} timedOut=${result.timedOut}",
                    environmentId
                )
            }.onFailure { error ->
                Logger.e(LOG_TAG, "Failed to register bootstrap failure $resourceId: ${error.message}")
            }
            return result
        }

        private fun transactionFailure(stage: String, error: Throwable): ToolchainCommandResult =
            ToolchainCommandResult(
                exitCode = -1,
                timedOut = false,
                durationMs = 0L,
                output = "KITE_RESOURCE_TRANSACTION_FAILED stage=$stage reason=${error.message ?: error.javaClass.simpleName}\n"
            )

        private fun ToolchainCommandResult.withFailure(stage: String, error: Throwable): ToolchainCommandResult =
            copy(
                exitCode = if (exitCode == 0) -1 else exitCode,
                output = (output + "\nKITE_RESOURCE_TRANSACTION_FAILED stage=$stage reason=${error.message ?: error.javaClass.simpleName}\n")
                    .takeLast(OUTPUT_LIMIT)
            )
    }

    private fun auditToolchainCapability(action: ToolchainAction) {
        CapabilityGate.evaluate(
            CapabilityRequest(
                callerName = "ToolchainPackInstaller",
                callerType = CapabilityCallerType.LEGACY,
                actionName = "toolchain.${action.name.lowercase()}",
                capabilityDomains = setOf(
                    CapabilityDomain.PROOT,
                    CapabilityDomain.UBUNTU,
                    CapabilityDomain.OUTPUT
                ),
                requiresContainer = true,
                longRunning = true,
                expectedOutputLevel = CapabilityOutputLevel.HIGH,
                concurrencyKey = "toolchain:${action.name.lowercase()}",
                sourcePath = "foundation/toolchain/ToolchainPackInstaller.kt",
                sourceModule = "toolchain",
                legacyDirectCall = true
            )
        )
    }

    private fun extractRuntimePack(context: Context): ToolchainPackManifest {
        val expectedManifestText = context.assets.open("$ASSET_ROOT/manifest.json")
            .bufferedReader()
            .use { it.readText() }
        val runtimePackDir = runtimePackDir(context)
        if (!bundledPackDirectoryIsComplete(runtimePackDir, expectedManifestText)) {
            val pendingDir = File(runtimePackDir.parentFile, "$PACK_ID.pending")
            pendingDir.deleteRecursively()
            pendingDir.mkdirs()
            copyAssetTree(context, ASSET_ROOT, pendingDir)
            normalizeShellScripts(pendingDir)
            cleanupUndeclaredBundledPackageFiles(pendingDir, expectedManifestText)
            check(bundledPackDirectoryIsComplete(pendingDir, expectedManifestText)) {
                "Bundled toolchain pack is incomplete after extraction"
            }
            runtimePackDir.deleteRecursively()
            check(pendingDir.renameTo(runtimePackDir)) {
                "Unable to publish runtime toolchain pack at ${runtimePackDir.absolutePath}"
            }
        }
        val manifestFile = File(runtimePackDir, "manifest.json")
        val manifest = ToolchainPackManifest.fromJson(JSONObject(manifestFile.readText()))
        appendLog(context, "Prepared ${manifest.packId} v${manifest.version} at ${runtimePackDir.absolutePath}")
        return manifest
    }

    private fun mirrorPackIntoWorkspace(context: Context): File {
        val container = WorkSurfaceRuntimeBridge.ensureDefaultContainer(context)
        val workspaceDir = File(container.workspacePath).also { it.mkdirs() }
        WorkspaceBuildSupport.ensure(workspaceDir)
        val workspacePackDir = File(workspaceDir, ".kf/toolchains/$PACK_ID")
        if (workspacePackDir.exists()) {
            workspacePackDir.deleteRecursively()
        }
        runtimePackDir(context).copyRecursively(workspacePackDir, overwrite = true)
        normalizeShellScripts(workspacePackDir)
        appendLog(context, "Mirrored $PACK_ID to ${workspacePackDir.absolutePath}")
        return workspacePackDir
    }

    private fun mirrorPackIntoSharedResourceCache(context: Context, resourceId: String): File {
        val container = WorkSurfaceRuntimeBridge.ensureDefaultContainer(context)
        val workspaceDir = File(container.workspacePath).also { it.mkdirs() }
        WorkspaceBuildSupport.ensure(workspaceDir)
        val sourcePackDir = runtimePackDir(context)
        val sourceManifest = File(sourcePackDir, "manifest.json").readText()
        val workspacePackDir = File(workspaceDir, ".kf/cache/shared/$PACK_ID")
        if (!bundledPackDirectoryIsComplete(workspacePackDir, sourceManifest)) {
            val pendingDir = File(workspacePackDir.parentFile, "$PACK_ID.pending")
            pendingDir.deleteRecursively()
            sourcePackDir.copyRecursively(pendingDir, overwrite = true)
            normalizeShellScripts(pendingDir)
            workspacePackDir.deleteRecursively()
            check(pendingDir.renameTo(workspacePackDir)) {
                "Unable to publish shared toolchain pack at ${workspacePackDir.absolutePath}"
            }
        }
        val reclaimed = cleanupLegacyResourcePackCopies(workspaceDir)
        appendLog(
            context,
            "Staged shared $PACK_ID for ${safeResourceId(resourceId)} at ${workspacePackDir.absolutePath}; reclaimedLegacyBytes=$reclaimed"
        )
        return workspacePackDir
    }

    private fun cleanupLegacyResourcePackCopies(workspaceDir: File): Long {
        val resourcesDir = File(workspaceDir, ".kf/cache/resources")
        if (!resourcesDir.isDirectory) return 0L
        var reclaimed = 0L
        resourcesDir.listFiles().orEmpty().forEach { resourceDir ->
            val legacyPack = File(resourceDir, PACK_ID)
            if (!legacyPack.exists()) return@forEach
            reclaimed += legacyPack.walkTopDown().filter(File::isFile).sumOf(File::length)
            legacyPack.deleteRecursively()
        }
        return reclaimed
    }

    private fun executeInstallScript(
        context: Context,
        action: ToolchainAction,
        workspacePackDir: File
    ): ToolchainCommandResult {
        val mode = when (action) {
            ToolchainAction.PREPARE -> error("PREPARE uses the fixed bootstrap resource flow")
            ToolchainAction.NODE -> "--install-node"
            ToolchainAction.DOCTOR -> "--doctor"
        }
        return executeInstallScript(
            context = context,
            mode = mode,
            workspacePackDir = workspacePackDir,
            workspacePackPath = "/workspace/.kf/toolchains/$PACK_ID",
            toolchainDir = "/workspace/.kf/toolchains",
            binDir = WorkspaceBuildSupport.CONTAINER_HELPER_BIN_PATH
        )
    }

    private fun executeInstallScript(
        context: Context,
        mode: String,
        workspacePackDir: File,
        workspacePackPath: String,
        toolchainDir: String,
        binDir: String
    ): ToolchainCommandResult {
        val scriptPath = "$workspacePackPath/install.sh"
        val payload = """
            export KF_TOOLCHAIN_PACK_DIR=$workspacePackPath
            export KF_TOOLCHAIN_DIR=$toolchainDir
            export KF_TOOLCHAIN_BIN_DIR=$binDir
            export UV_LINK_MODE=copy
            chmod +x "$scriptPath" 2>/dev/null || true
            bash "$scriptPath" "$mode"
        """.trimIndent()
        val config = WorkSurfaceRuntimeBridge.buildShellExecConfig(
            context = context,
            workingDirectory = "/workspace",
            payload = payload,
            loginShell = true
        )

        val output = StringBuilder()
        val startedAt = System.currentTimeMillis()
        val process = ProcessBuilder(config.command)
            .redirectErrorStream(true)
            .apply {
                environment().putAll(config.env)
                environment()["KF_TOOLCHAIN_PACK_HOST_DIR"] = workspacePackDir.absolutePath
            }
            .start()
        val reader = thread(start = true, isDaemon = true, name = "ToolchainPackReader") {
            runCatching {
                process.inputStream.bufferedReader().useLines { lines ->
                    lines.forEach { line ->
                        appendLog(context, line)
                        if (output.length < OUTPUT_LIMIT) {
                            output.append(line).append('\n')
                        }
                    }
                }
            }
        }
        val finished = process.waitFor(INSTALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        reader.join(1500L)
        if (!finished) {
            process.destroyForcibly()
        }
        return ToolchainCommandResult(
            exitCode = if (finished) process.exitValue() else -1,
            timedOut = !finished,
            durationMs = System.currentTimeMillis() - startedAt,
            output = output.toString()
        )
    }

    private fun copyAssetTree(context: Context, assetPath: String, destination: File) {
        val children = context.assets.list(assetPath).orEmpty()
        if (children.isEmpty()) {
            destination.parentFile?.mkdirs()
            context.assets.open(assetPath).use { input ->
                destination.outputStream().use { output -> input.copyTo(output) }
            }
            return
        }
        destination.mkdirs()
        children.forEach { child ->
            copyAssetTree(
                context = context,
                assetPath = "$assetPath/$child",
                destination = File(destination, child)
            )
        }
    }

    private fun normalizeShellScripts(root: File) {
        root.walkTopDown()
            .filter { it.isFile && it.extension.equals("sh", ignoreCase = true) }
            .forEach { script ->
                runCatching {
                    val original = script.readText(Charsets.UTF_8)
                    val normalized = original.replace("\r\n", "\n").replace("\r", "\n")
                    if (normalized != original) {
                        script.writeText(normalized, Charsets.UTF_8)
                    }
                }.onFailure { error ->
                    Logger.e(LOG_TAG, "Failed to normalize shell script ${script.absolutePath}: ${error.message}")
                }
            }
    }

    private fun runtimePackDir(context: Context): File {
        return File(WorkSurfaceRuntimeBridge.getRuntimeRoot(context), "toolchain-packs/$PACK_ID")
    }

    private fun workspaceDirOrNull(context: Context): File? {
        return runCatching {
            val container = WorkSurfaceRuntimeBridge.ensureDefaultContainer(context.applicationContext)
            File(container.workspacePath)
        }.getOrNull()
    }

    private fun workspacePathExists(file: File, workspaceDir: File): Boolean {
        if (file.exists()) return true
        val target = runCatching { Os.readlink(file.absolutePath) }.getOrNull()
            ?: return false
        if (!target.startsWith("/workspace/")) return true
        val projectedTarget = File(workspaceDir, target.removePrefix("/workspace/"))
        return projectedTarget.exists() ||
            runCatching { Os.readlink(projectedTarget.absolutePath) }.isSuccess
    }

    private fun safeResourceId(resourceId: String): String {
        return resourceId.trim()
            .lowercase()
            .replace(Regex("[^a-z0-9._-]+"), "-")
            .trim('-')
            .ifBlank { "resource" }
    }

    private fun statusFile(context: Context): File {
        return File(runtimePackDir(context), STATUS_FILE)
    }

    private fun appendLog(context: Context, text: String) {
        runCatching {
            val file = logFile(context).also { it.parentFile?.mkdirs() }
            synchronized(logLock) {
                file.appendText("[${System.currentTimeMillis()}] $text\n")
            }
        }.onFailure { error ->
            Logger.e(LOG_TAG, "Failed to append toolchain log: ${error.message}")
        }
    }

    private fun writeStatus(context: Context, state: ToolchainInstallState) {
        runCatching {
            statusFile(context).also { it.parentFile?.mkdirs() }.writeText(state.toJson().toString(2))
        }.onFailure { error ->
            Logger.e(LOG_TAG, "Failed to write toolchain status: ${error.message}")
        }
    }

    private fun readStatus(context: Context): ToolchainInstallState {
        return runCatching {
            val file = statusFile(context)
            if (!file.exists()) {
                ToolchainInstallState(logPath = logFile(context).absolutePath)
            } else {
                ToolchainInstallState.fromJson(JSONObject(file.readText()))
            }
        }.getOrElse {
            ToolchainInstallState(
                phase = ToolchainInstallPhase.FAILED,
                summary = "Failed to read toolchain status: ${it.message}",
                logPath = logFile(context).absolutePath
            )
        }
    }

    private fun ToolchainCommandResult.summaryLine(): String? {
        return bootstrapSummaryLine(output)
    }

    internal fun bootstrapSummaryLine(output: String): String? =
        output.lineSequence().lastOrNull { it.startsWith("FAIL\t") || it.startsWith("FAIL ") }
            ?: output.lineSequence().lastOrNull { it.startsWith("SUMMARY ") }

    private fun ToolchainCommandResult.toLogBlock(label: String = PACK_ID): String {
        return buildString {
            appendLine("== $label result ==")
            appendLine("exitCode=$exitCode timedOut=$timedOut durationMs=$durationMs")
            appendLine(output.take(OUTPUT_LIMIT))
        }
    }

    private enum class ToolchainAction {
        PREPARE,
        NODE,
        DOCTOR
    }

    internal data class ToolchainCommandResult(
        val exitCode: Int,
        val timedOut: Boolean,
        val durationMs: Long,
        val output: String
    )
}

internal fun bundledPackDirectoryIsComplete(root: File, expectedManifestText: String): Boolean {
    if (!root.isDirectory) return false
    return runCatching {
        val expected = JSONObject(expectedManifestText)
        val actual = JSONObject(File(root, "manifest.json").readText())
        if (expected.optString("packId") != actual.optString("packId") ||
            expected.optInt("version") != actual.optInt("version")
        ) {
            return@runCatching false
        }
        val installScript = expected.optString("installScript", "install.sh")
        if (!File(root, installScript).isFile) return@runCatching false
        val declaredFiles = declaredBundledPackageFiles(expected)
        val declaredFilesPresent = declaredFiles.all { relativePath ->
            File(root, relativePath).let { it.isFile && it.length() > 0L }
        }
        val actualPackageFiles = File(root, "packages")
            .walkTopDown()
            .filter(File::isFile)
            .map { file -> file.relativeTo(root).invariantSeparatorsPath }
            .toSet()
        declaredFilesPresent && actualPackageFiles.all { it in declaredFiles }
    }.getOrDefault(false)
}

internal fun cleanupUndeclaredBundledPackageFiles(root: File, manifestText: String): Long {
    val declaredFiles = runCatching { declaredBundledPackageFiles(JSONObject(manifestText)) }
        .getOrDefault(emptySet())
    if (declaredFiles.isEmpty()) return 0L
    var reclaimed = 0L
    File(root, "packages").walkTopDown().filter(File::isFile).forEach { file ->
        val relativePath = file.relativeTo(root).invariantSeparatorsPath
        if (relativePath !in declaredFiles) {
            reclaimed += file.length()
            file.delete()
        }
    }
    return reclaimed
}

private fun declaredBundledPackageFiles(manifest: JSONObject): Set<String> {
    val packages = manifest.optJSONObject("packages") ?: return emptySet()
    return packages.keys().asSequence()
        .mapNotNull { key -> packages.optJSONObject(key)?.optString("file")?.takeIf(String::isNotBlank) }
        .toSet()
}

enum class ToolchainInstallPhase {
    IDLE,
    RUNNING,
    SUCCEEDED,
    FAILED
}

data class ToolchainInstallState(
    val phase: ToolchainInstallPhase = ToolchainInstallPhase.IDLE,
    val action: String = "",
    val packId: String = "ai-dev-pack",
    val packVersion: Int = 0,
    val startedAt: Long? = null,
    val updatedAt: Long? = null,
    val exitCode: Int? = null,
    val timedOut: Boolean = false,
    val summary: String = "Not installed",
    val logPath: String = "",
    val outputPreview: String = ""
) {
    fun toJson(): JSONObject {
        return JSONObject()
            .put("phase", phase.name)
            .put("action", action)
            .put("packId", packId)
            .put("packVersion", packVersion)
            .put("startedAt", startedAt)
            .put("updatedAt", updatedAt)
            .put("exitCode", exitCode)
            .put("timedOut", timedOut)
            .put("summary", summary)
            .put("logPath", logPath)
            .put("outputPreview", outputPreview)
    }

    companion object {
        fun fromJson(json: JSONObject): ToolchainInstallState {
            return ToolchainInstallState(
                phase = runCatching {
                    ToolchainInstallPhase.valueOf(json.optString("phase", ToolchainInstallPhase.IDLE.name))
                }.getOrDefault(ToolchainInstallPhase.IDLE),
                action = json.optString("action"),
                packId = json.optString("packId", "ai-dev-pack"),
                packVersion = json.optInt("packVersion", 0),
                startedAt = json.optLongOrNull("startedAt"),
                updatedAt = json.optLongOrNull("updatedAt"),
                exitCode = json.optIntOrNull("exitCode"),
                timedOut = json.optBoolean("timedOut", false),
                summary = json.optString("summary", "Not installed"),
                logPath = json.optString("logPath"),
                outputPreview = json.optString("outputPreview")
            )
        }
    }
}

private fun JSONObject.optLongOrNull(name: String): Long? {
    return if (has(name) && !isNull(name)) optLong(name) else null
}

private fun JSONObject.optIntOrNull(name: String): Int? {
    return if (has(name) && !isNull(name)) optInt(name) else null
}
