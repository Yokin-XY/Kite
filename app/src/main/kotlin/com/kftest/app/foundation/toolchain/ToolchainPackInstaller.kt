package com.kftest.app.foundation.toolchain

import android.content.Context
import com.kftest.app.foundation.capability.CapabilityCallerType
import com.kftest.app.foundation.capability.CapabilityDomain
import com.kftest.app.foundation.capability.CapabilityGate
import com.kftest.app.foundation.capability.CapabilityOutputLevel
import com.kftest.app.foundation.capability.CapabilityRequest
import com.kftest.app.foundation.logging.Logger
import com.kftest.app.foundation.workspace.WorkSurfaceRuntimeBridge
import com.kftest.app.foundation.workspace.WorkspaceBuildSupport
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

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val running = AtomicBoolean(false)
    private val logLock = Any()
    private val _state = MutableStateFlow(ToolchainInstallState())

    val state: StateFlow<ToolchainInstallState> = _state.asStateFlow()

    fun prepareAiEnv(context: Context) {
        runPack(context, ToolchainAction.PREPARE)
    }

    fun doctor(context: Context) {
        runPack(context, ToolchainAction.DOCTOR)
    }

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
            val startedAt = System.currentTimeMillis()
            _state.value = ToolchainInstallState(
                phase = ToolchainInstallPhase.RUNNING,
                action = action.name.lowercase(),
                startedAt = startedAt,
                updatedAt = startedAt,
                summary = "Preparing $PACK_ID"
            )
            appendLog(appContext, "== $PACK_ID ${action.name.lowercase()} start ==")
            runCatching {
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
                val status = ToolchainInstallState(
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
                )
                _state.value = status
                writeStatus(appContext, status)
                appendLog(appContext, result.toLogBlock())
                Logger.i(LOG_TAG, "$PACK_ID ${action.name.lowercase()} complete: $summary")
            }.onFailure { error ->
                val status = ToolchainInstallState(
                    phase = ToolchainInstallPhase.FAILED,
                    action = action.name.lowercase(),
                    startedAt = startedAt,
                    updatedAt = System.currentTimeMillis(),
                    summary = error.message ?: error::class.java.simpleName,
                    logPath = logFile(appContext).absolutePath,
                    outputPreview = error.stackTraceToString().take(4_000)
                )
                _state.value = status
                writeStatus(appContext, status)
                appendLog(appContext, "== $PACK_ID ${action.name.lowercase()} failed ==\n${error.stackTraceToString()}")
                Logger.e(LOG_TAG, "$PACK_ID ${action.name.lowercase()} failed: ${error.message}")
            }
            running.set(false)
        }
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
        val runtimePackDir = runtimePackDir(context).also { it.mkdirs() }
        copyAssetTree(context, ASSET_ROOT, runtimePackDir)
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
        appendLog(context, "Mirrored $PACK_ID to ${workspacePackDir.absolutePath}")
        return workspacePackDir
    }

    private fun executeInstallScript(
        context: Context,
        action: ToolchainAction,
        workspacePackDir: File
    ): ToolchainCommandResult {
        val scriptPath = "/workspace/.kf/toolchains/$PACK_ID/install.sh"
        val mode = when (action) {
            ToolchainAction.PREPARE -> "--install"
            ToolchainAction.DOCTOR -> "--doctor"
        }
        val payload = """
            export KF_TOOLCHAIN_PACK_DIR=/workspace/.kf/toolchains/$PACK_ID
            export KF_TOOLCHAIN_DIR=/workspace/.kf/toolchains
            export KF_TOOLCHAIN_BIN_DIR=/workspace/.kf/bin
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

    private fun runtimePackDir(context: Context): File {
        return File(WorkSurfaceRuntimeBridge.getRuntimeRoot(context), "toolchain-packs/$PACK_ID")
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
        return output.lineSequence().lastOrNull { it.startsWith("SUMMARY ") }
    }

    private fun ToolchainCommandResult.toLogBlock(): String {
        return buildString {
            appendLine("== $PACK_ID result ==")
            appendLine("exitCode=$exitCode timedOut=$timedOut durationMs=$durationMs")
            appendLine(output.take(OUTPUT_LIMIT))
        }
    }

    private enum class ToolchainAction {
        PREPARE,
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
