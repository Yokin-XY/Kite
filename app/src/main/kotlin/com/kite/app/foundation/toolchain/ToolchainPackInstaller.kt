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
    const val BOOTSTRAP_RESOURCE_RUN_PREFIX = "bootstrap:"
    private const val RESOURCE_NODEJS = "kite.nodejs"
    private const val RESOURCE_PYTHON = "kite.python"
    private const val RESOURCE_UV = "kite.uv"
    private const val RESOURCE_GIT = "kite.git"
    private const val RESOURCE_CURL = "kite.curl"
    private const val RESOURCE_TOOL_ENV = "kite.tool.env"
    private const val NODE_VERSION = "26.4.0"
    private const val PYTHON_VERSION = "3.14.6"
    private const val UV_VERSION = "0.11.25"
    private val BOOTSTRAP_RESOURCES = listOf(
        BootstrapResource(RESOURCE_NODEJS, "--install-node", NODE_VERSION, "Node.js"),
        BootstrapResource(RESOURCE_PYTHON, "--install-python", PYTHON_VERSION, "Python"),
        BootstrapResource(RESOURCE_UV, "--install-uv", UV_VERSION, "uv"),
        BootstrapResource(RESOURCE_GIT, "--install-git", "rootfs", "Git"),
        BootstrapResource(RESOURCE_CURL, "--install-curl", "rootfs", "curl"),
        BootstrapResource(RESOURCE_TOOL_ENV, "--install-system-tools", "v16", "系统工具集合")
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
        mirrorPackIntoResource(appContext, resourceId)
        return manifest
    }

    fun resourcePackWorkspacePath(resourceId: String): String {
        return "/workspace/.kf/cache/resources/${safeResourceId(resourceId)}/$PACK_ID"
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
            val ids = BOOTSTRAP_RESOURCES.map { it.resourceId }
            ids.all { id -> bootstrapResourceStatusSettled(port.statusOf(context.applicationContext, id)) }
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
            val resourceResults = BOOTSTRAP_RESOURCES.mapIndexed { index, resource ->
                val stepIndex = index + 1
                markBootstrapResourceInstalling(appContext, resource)
                if (reportBootstrapProgress) {
                    RuntimeBootstrapProgress.bundledToolStarted(resource.label, stepIndex, totalSteps)
                }
                val result = runCatching {
                    val resourcePackDir = mirrorPackIntoResource(appContext, resource.resourceId)
                    executeInstallScript(
                        context = appContext,
                        mode = resource.mode,
                        workspacePackDir = resourcePackDir,
                        workspacePackPath = resourcePackWorkspacePath(resource.resourceId),
                        toolchainDir = "/workspace/.kf/software/${safeResourceId(resource.resourceId)}",
                        binDir = WorkspaceBuildSupport.CONTAINER_HELPER_BIN_PATH
                    )
                }.getOrElse { error ->
                    ToolchainCommandResult(
                        exitCode = -1,
                        timedOut = false,
                        durationMs = 0L,
                        output = error.stackTraceToString().take(OUTPUT_LIMIT)
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
                recordBootstrapResourceResult(appContext, resource, result)
                appendLog(appContext, result.toLogBlock(resource.resourceId))
                resource to result
            }

            val failedResources = resourceResults
                .filter { (_, result) -> result.exitCode != 0 || result.timedOut }
                .map { (resource, _) -> resource.resourceId }
            val phase = ToolchainInstallPhase.SUCCEEDED
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

    private fun markBootstrapResourceInstalling(context: Context, resource: BootstrapResource) {
        runCatching {
            ToolchainResourcePortHost.get().markInstalling(
                context,
                resource.resourceId,
                "$BOOTSTRAP_RESOURCE_RUN_PREFIX${resource.resourceId}"
            )
        }.onFailure { error ->
            Logger.e(LOG_TAG, "Failed to mark bootstrap resource ${resource.resourceId} installing: ${error.message}")
        }
    }

    private fun markBootstrapResourcesFailed(context: Context, reason: String) {
        runCatching {
            val port = ToolchainResourcePortHost.get()
            BOOTSTRAP_RESOURCES.forEach { resource ->
                port.markFailed(context, resource.resourceId, null, reason)
            }
        }.onFailure { error ->
            Logger.e(LOG_TAG, "Failed to mark bootstrap resources failed: ${error.message}")
        }
    }

    private fun recordBootstrapResourceResult(
        context: Context,
        resource: BootstrapResource,
        result: ToolchainCommandResult
    ) {
        runCatching {
            val port = ToolchainResourcePortHost.get()
            val registration = resolveBootstrapResourceRegistration(
                resourceId = resource.resourceId,
                version = resource.version,
                exitCode = result.exitCode,
                timedOut = result.timedOut,
                summary = result.summaryLine() ?: "exitCode=${result.exitCode} timedOut=${result.timedOut}"
            )
            if (registration.installed) {
                port.markInstalled(context, registration.resourceId, registration.version, null, registration.summary)
            } else {
                port.markFailed(
                    context,
                    registration.resourceId,
                    null,
                    registration.summary
                )
            }
        }.onFailure { error ->
            Logger.e(LOG_TAG, "Failed to register bootstrap resource ${resource.resourceId}: ${error.message}")
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

    internal data class BootstrapResourceRegistration(
        val resourceId: String,
        val version: String,
        val installed: Boolean,
        val summary: String
    )

    private data class BootstrapResource(
        val resourceId: String,
        val mode: String,
        val version: String,
        val label: String
    )

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
        val runtimePackDir = runtimePackDir(context).also { it.mkdirs() }
        copyAssetTree(context, ASSET_ROOT, runtimePackDir)
        normalizeShellScripts(runtimePackDir)
        val manifestFile = File(runtimePackDir, "manifest.json")
        val manifest = ToolchainPackManifest.fromJson(JSONObject(manifestFile.readText()))
        appendLog(context, "Extracted ${manifest.packId} v${manifest.version} to ${runtimePackDir.absolutePath}")
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

    private fun mirrorPackIntoResource(context: Context, resourceId: String): File {
        val container = WorkSurfaceRuntimeBridge.ensureDefaultContainer(context)
        val workspaceDir = File(container.workspacePath).also { it.mkdirs() }
        WorkspaceBuildSupport.ensure(workspaceDir)
        val workspacePackDir = File(workspaceDir, ".kf/cache/resources/${safeResourceId(resourceId)}/$PACK_ID")
        if (workspacePackDir.exists()) {
            workspacePackDir.deleteRecursively()
        }
        runtimePackDir(context).copyRecursively(workspacePackDir, overwrite = true)
        normalizeShellScripts(workspacePackDir)
        appendLog(context, "Staged $PACK_ID resource ${safeResourceId(resourceId)} to ${workspacePackDir.absolutePath}")
        return workspacePackDir
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

    private data class ToolchainCommandResult(
        val exitCode: Int,
        val timedOut: Boolean,
        val durationMs: Long,
        val output: String
    )
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
