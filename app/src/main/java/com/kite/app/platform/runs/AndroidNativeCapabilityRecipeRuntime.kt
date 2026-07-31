package com.kite.app.platform.runs

import android.content.Context
import com.kite.app.application.runs.RecipeExecutionEvent
import com.kite.app.application.runs.RecipeStepExecutionRequest
import com.kite.app.application.runs.RunStateMutation
import com.kite.app.foundation.runtime.AndroidNativeCapabilityContext
import com.kite.app.foundation.runtime.AndroidNativeArchiveCapabilityProvider
import com.kite.app.foundation.runtime.AndroidNativeArchiveExecutor
import com.kite.app.foundation.runtime.AndroidNativeArchivePlan
import com.kite.app.foundation.runtime.AndroidNativeDownloadCapabilityProvider
import com.kite.app.foundation.runtime.AndroidNativeDownloadExecutor
import com.kite.app.foundation.runtime.AndroidNativeFileCapabilityContext
import com.kite.app.foundation.runtime.AndroidNativeFileCapabilityProvider
import com.kite.app.foundation.runtime.AndroidNativeFileExecutor
import com.kite.app.foundation.runtime.AndroidNativeFilePlan
import com.kite.app.foundation.runtime.NativeCapabilityDestinationRoot
import com.kite.app.foundation.runtime.NativeArchiveExecutionResult
import com.kite.app.foundation.runtime.NativeArchiveProgressListener
import com.kite.app.foundation.runtime.NativeDownloadCancellationSignal
import com.kite.app.foundation.runtime.NativeDownloadExecutionResult
import com.kite.app.foundation.runtime.NativeDownloadProgressListener
import com.kite.app.foundation.runtime.NativeFileCancellation
import com.kite.app.foundation.runtime.NativeFileCancellationSignal
import com.kite.app.foundation.runtime.NativeFileCapabilityRoot
import com.kite.app.foundation.runtime.NativeFileExecutionResult
import com.kite.app.foundation.runtime.NativeFilePermission
import com.kite.app.foundation.runtime.NativeFileProgressListener
import com.kite.app.foundation.runtime.RuntimeExecutionPayload
import com.kite.app.foundation.runtime.RuntimeExecutionRequest
import com.kite.app.foundation.runtime.RuntimeExecutionRequirement
import com.kite.app.foundation.runtime.RuntimeFallbackPolicy
import com.kite.app.foundation.runtime.RuntimeProviderDecision
import com.kite.app.foundation.runtime.KFContainerManager
import com.kite.app.recipe.KiteRecipe
import com.kite.app.run.CardRunStatus
import com.kite.app.run.CardRunSurface
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread

internal interface NativeCapabilityRecipeRuntime {
    fun execute(
        request: RecipeStepExecutionRequest,
        callback: (RecipeExecutionEvent) -> Unit,
    )

    fun owns(instanceId: String, generation: Long): Boolean

    fun stop(instanceId: String, generation: Long, callback: (Boolean) -> Unit)
}

internal fun interface NativeFileExecutionGateway {
    fun execute(
        plan: AndroidNativeFilePlan,
        cancellation: NativeFileCancellation,
        progress: NativeFileProgressListener,
    ): NativeFileExecutionResult
}

internal fun interface NativeArchiveExecutionGateway {
    fun execute(
        plan: AndroidNativeArchivePlan,
        cancellation: NativeFileCancellation,
        progress: NativeArchiveProgressListener,
    ): NativeArchiveExecutionResult
}

/** 把结构化原生能力接入同一 Run；不持有页面或 CardRunStore。 */
internal class AndroidNativeCapabilityRecipeRuntime(
    context: Context,
    private val downloadExecutor: AndroidNativeDownloadExecutor = AndroidNativeDownloadExecutor(),
    capabilityContextProvider: (() -> AndroidNativeCapabilityContext)? = null,
    fileExecutor: AndroidNativeFileExecutor = AndroidNativeFileExecutor(),
    fileCapabilityContextProvider: (() -> AndroidNativeFileCapabilityContext)? = null,
    private val fileExecutionGateway: NativeFileExecutionGateway = NativeFileExecutionGateway(fileExecutor::execute),
    archiveExecutor: AndroidNativeArchiveExecutor = AndroidNativeArchiveExecutor(),
    private val archiveExecutionGateway: NativeArchiveExecutionGateway =
        NativeArchiveExecutionGateway(archiveExecutor::execute),
) : NativeCapabilityRecipeRuntime {
    private data class ExecutionKey(
        val instanceId: String,
        val generation: Long,
        val stepIndex: Int,
    )

    private data class PendingExecution(
        val downloadCancellation: NativeDownloadCancellationSignal = NativeDownloadCancellationSignal(),
        val fileCancellation: NativeFileCancellationSignal = NativeFileCancellationSignal(),
    ) {
        fun cancel() {
            downloadCancellation.cancel()
            fileCancellation.cancel()
        }
    }

    private val appContext = context.applicationContext
    private val capabilityContextProvider = capabilityContextProvider ?: {
        AndroidNativeCapabilityContext(
            listOf(
                NativeCapabilityDestinationRoot(
                    containerPath = "/workspace",
                    directory = KFContainerManager.resolveWorkspaceDirectory(appContext),
                )
            )
        )
    }
    private val fileCapabilityContextProvider = fileCapabilityContextProvider ?: {
        val workspace = KFContainerManager.resolveWorkspaceDirectory(appContext)
        val cache = java.io.File(workspace, ".kf/cache")
        AndroidNativeFileCapabilityContext(
            listOf(
                NativeFileCapabilityRoot(
                    containerPath = "/workspace/.kf/cache",
                    directory = cache,
                    permissions = NativeFilePermission.entries.toSet(),
                ),
                NativeFileCapabilityRoot(
                    containerPath = "/workspace",
                    directory = workspace,
                    permissions = setOf(
                        NativeFilePermission.READ,
                        NativeFilePermission.CREATE,
                        NativeFilePermission.REPLACE,
                    ),
                ),
            )
        )
    }
    private val pending = ConcurrentHashMap<ExecutionKey, PendingExecution>()

    override fun execute(
        request: RecipeStepExecutionRequest,
        callback: (RecipeExecutionEvent) -> Unit,
    ) {
        val capabilityId = request.step.action?.trim().orEmpty()
        if (capabilityId.isBlank()) {
            callback(request.failed("native_capability_id_missing"))
            return
        }
        val parameters = runCatching {
            request.step.params?.let { json ->
                buildMap<String, String> {
                    json.keys().forEach { key ->
                        val value = json.opt(key)
                        if (value !is String) error("native_capability_parameter_not_string:$key")
                        put(key, value)
                    }
                }
            }.orEmpty()
        }.getOrElse { error ->
            callback(request.failed(error.message ?: "native_capability_parameters_invalid"))
            return
        }
        val key = ExecutionKey(request.instanceId, request.generation, request.stepIndex)
        val execution = PendingExecution()
        if (pending.putIfAbsent(key, execution) != null) {
            callback(request.failed("native_capability_already_running"))
            return
        }
        callback(request.progress("正在准备安卓原生能力", "android_native", null))
        thread(name = "KiteNativeCapability-${request.instanceId.take(20)}", isDaemon = true) {
            try {
                val runtimeRequest = RuntimeExecutionRequest(
                    payload = RuntimeExecutionPayload.NativeCapability(capabilityId, parameters),
                    requirements = setOf(RuntimeExecutionRequirement.ANDROID_NATIVE),
                    fallbackPolicy = RuntimeFallbackPolicy.DISABLED,
                )
                when (val decision = AndroidNativeDownloadCapabilityProvider.prepare(
                    capabilityContextProvider(),
                    runtimeRequest,
                )) {
                    is RuntimeProviderDecision.Ready -> executeDownload(
                        request,
                        execution,
                        decision.plan,
                        decision.reason,
                        callback,
                    )
                    is RuntimeProviderDecision.Unsupported -> executeFileCapability(
                        request,
                        execution,
                        runtimeRequest,
                        callback,
                    )
                    is RuntimeProviderDecision.Blocked -> callback(
                        request.failed(decision.reason, decision.reason)
                    )
                }
            } catch (error: Throwable) {
                callback(request.failed(error.message ?: "native_capability_execution_failed"))
            } finally {
                pending.remove(key, execution)
            }
        }
    }

    override fun owns(instanceId: String, generation: Long): Boolean =
        pending.keys.any { key -> key.instanceId == instanceId && key.generation == generation }

    override fun stop(instanceId: String, generation: Long, callback: (Boolean) -> Unit) {
        val matches = pending.entries.filter { (key, _) ->
            key.instanceId == instanceId && key.generation == generation
        }
        if (matches.isEmpty()) {
            callback(true)
            return
        }
        matches.forEach { (_, execution) -> execution.cancel() }
        thread(name = "KiteNativeCapabilityStop-${instanceId.take(20)}", isDaemon = true) {
            val deadline = System.nanoTime() + STOP_CONFIRMATION_TIMEOUT_MS * 1_000_000L
            while (owns(instanceId, generation) && System.nanoTime() < deadline) {
                Thread.sleep(STOP_POLL_MS)
            }
            callback(!owns(instanceId, generation))
        }
    }

    private fun executeDownload(
        request: RecipeStepExecutionRequest,
        execution: PendingExecution,
        plan: com.kite.app.foundation.runtime.AndroidNativeDownloadPlan,
        readyReason: String,
        callback: (RecipeExecutionEvent) -> Unit,
    ) {
        var lastPublishedBytes = 0L
        var lastPublishedAt = 0L
        val result = downloadExecutor.execute(
            plan = plan,
            cancellation = execution.downloadCancellation,
            progress = NativeDownloadProgressListener { written, total ->
                val now = System.currentTimeMillis()
                if (
                    lastPublishedAt == 0L ||
                    written - lastPublishedBytes >= PROGRESS_BYTES ||
                    now - lastPublishedAt >= PROGRESS_INTERVAL_MS
                ) {
                    lastPublishedBytes = written
                    lastPublishedAt = now
                    val totalText = total?.let { "/$it" }.orEmpty()
                    callback(
                        request.progress(
                            message = "原生下载中：$written$totalText 字节",
                            lane = "android_native",
                            reason = readyReason,
                        )
                    )
                }
            },
        )
        when (result) {
            is NativeDownloadExecutionResult.Success -> {
                val summary = "原生下载完成：${result.bytesWritten} 字节，SHA-256 ${result.actualSha256}"
                callback(
                    RecipeExecutionEvent.Completed(
                        request.instanceId,
                        request.generation,
                        request.stepIndex,
                        RunStateMutation(
                            status = CardRunStatus.Running,
                            surface = CardRunSurface.Report,
                            currentStepIndex = request.stepIndex,
                            runtimeLane = "android_native",
                            runtimeFallbackReason = readyReason,
                            lastMeaningfulOutput = summary,
                            shellReportText = summary,
                            clearNextActionUrl = true,
                        ),
                    )
                )
            }
            is NativeDownloadExecutionResult.Failure -> callback(
                request.failed(result.reason, result.reason)
            )
            is NativeDownloadExecutionResult.Cancelled -> callback(
                request.failed("native_download_cancelled", "native_download_cancelled")
            )
        }
    }

    private fun executeFileCapability(
        request: RecipeStepExecutionRequest,
        execution: PendingExecution,
        runtimeRequest: RuntimeExecutionRequest,
        callback: (RecipeExecutionEvent) -> Unit,
    ) {
        when (val decision = AndroidNativeFileCapabilityProvider.prepare(
            fileCapabilityContextProvider(),
            runtimeRequest,
        )) {
            is RuntimeProviderDecision.Ready -> executeFile(
                request,
                execution,
                decision.plan,
                decision.reason,
                callback,
            )
            is RuntimeProviderDecision.Unsupported -> executeArchiveCapability(
                request,
                execution,
                runtimeRequest,
                callback,
            )
            is RuntimeProviderDecision.Blocked -> callback(
                request.failed(decision.reason, decision.reason)
            )
        }
    }

    private fun executeArchiveCapability(
        request: RecipeStepExecutionRequest,
        execution: PendingExecution,
        runtimeRequest: RuntimeExecutionRequest,
        callback: (RecipeExecutionEvent) -> Unit,
    ) {
        when (val decision = AndroidNativeArchiveCapabilityProvider.prepare(
            fileCapabilityContextProvider(),
            runtimeRequest,
        )) {
            is RuntimeProviderDecision.Ready -> executeArchive(
                request,
                execution,
                decision.plan,
                decision.reason,
                callback,
            )
            is RuntimeProviderDecision.Unsupported -> callback(
                request.failed(
                    message = decision.reason,
                    reason = "fallback_disabled:${decision.reason}",
                )
            )
            is RuntimeProviderDecision.Blocked -> callback(
                request.failed(decision.reason, decision.reason)
            )
        }
    }

    private fun executeArchive(
        request: RecipeStepExecutionRequest,
        execution: PendingExecution,
        plan: AndroidNativeArchivePlan,
        readyReason: String,
        callback: (RecipeExecutionEvent) -> Unit,
    ) {
        var lastPublishedBytes = 0L
        var lastPublishedAt = 0L
        val result = archiveExecutionGateway.execute(
            plan,
            execution.fileCancellation,
            NativeArchiveProgressListener { entries, bytes ->
                val now = System.currentTimeMillis()
                if (
                    lastPublishedAt == 0L ||
                    bytes - lastPublishedBytes >= PROGRESS_BYTES ||
                    now - lastPublishedAt >= PROGRESS_INTERVAL_MS
                ) {
                    lastPublishedBytes = bytes
                    lastPublishedAt = now
                    callback(
                        request.progress(
                            message = "原生 ZIP 解包中：$entries 项，$bytes 字节",
                            lane = "android_native",
                            reason = readyReason,
                        )
                    )
                }
            },
        )
        when (result) {
            is NativeArchiveExecutionResult.Success -> {
                val summary = "原生 ZIP 解包完成：${result.entriesExtracted} 项，${result.bytesExtracted} 字节"
                callback(
                    RecipeExecutionEvent.Completed(
                        request.instanceId,
                        request.generation,
                        request.stepIndex,
                        RunStateMutation(
                            status = CardRunStatus.Running,
                            surface = CardRunSurface.Report,
                            currentStepIndex = request.stepIndex,
                            runtimeLane = "android_native",
                            runtimeFallbackReason = readyReason,
                            lastMeaningfulOutput = summary,
                            shellReportText = summary,
                            clearNextActionUrl = true,
                        ),
                    )
                )
            }
            is NativeArchiveExecutionResult.Failure -> callback(
                request.failed(result.reason, result.reason)
            )
            is NativeArchiveExecutionResult.Cancelled -> callback(
                request.failed("native_archive_cancelled", "native_archive_cancelled")
            )
        }
    }

    private fun executeFile(
        request: RecipeStepExecutionRequest,
        execution: PendingExecution,
        plan: AndroidNativeFilePlan,
        readyReason: String,
        callback: (RecipeExecutionEvent) -> Unit,
    ) {
        var lastPublishedBytes = 0L
        var lastPublishedAt = 0L
        val result = fileExecutionGateway.execute(
            plan,
            execution.fileCancellation,
            NativeFileProgressListener { copied, total ->
                val now = System.currentTimeMillis()
                if (
                    lastPublishedAt == 0L ||
                    copied - lastPublishedBytes >= PROGRESS_BYTES ||
                    now - lastPublishedAt >= PROGRESS_INTERVAL_MS
                ) {
                    lastPublishedBytes = copied
                    lastPublishedAt = now
                    callback(
                        request.progress(
                            message = "原生文件复制中：$copied/$total 字节",
                            lane = "android_native",
                            reason = readyReason,
                        )
                    )
                }
            },
        )
        when (result) {
            is NativeFileExecutionResult.Success -> {
                val summary = "原生文件操作完成：${result.capabilityId}，${result.bytesAffected} 字节"
                callback(
                    RecipeExecutionEvent.Completed(
                        request.instanceId,
                        request.generation,
                        request.stepIndex,
                        RunStateMutation(
                            status = CardRunStatus.Running,
                            surface = CardRunSurface.Report,
                            currentStepIndex = request.stepIndex,
                            runtimeLane = "android_native",
                            runtimeFallbackReason = readyReason,
                            lastMeaningfulOutput = summary,
                            shellReportText = summary,
                            clearNextActionUrl = true,
                        ),
                    )
                )
            }
            is NativeFileExecutionResult.Failure -> callback(
                request.failed(result.reason, result.reason)
            )
            is NativeFileExecutionResult.Cancelled -> callback(
                request.failed("native_file_cancelled", "native_file_cancelled")
            )
        }
    }

    private fun RecipeStepExecutionRequest.progress(
        message: String,
        lane: String,
        reason: String?,
    ): RecipeExecutionEvent.Progress = RecipeExecutionEvent.Progress(
        instanceId,
        generation,
        stepIndex,
        RunStateMutation(
            status = CardRunStatus.Running,
            surface = CardRunSurface.Report,
            currentStepIndex = stepIndex,
            runtimeLane = lane,
            runtimeFallbackReason = reason,
            lastMeaningfulOutput = message,
            shellReportText = message,
            clearNextActionUrl = true,
        ),
    )

    private fun RecipeStepExecutionRequest.failed(
        message: String,
        reason: String? = null,
    ): RecipeExecutionEvent.Failed = RecipeExecutionEvent.Failed(
        instanceId = instanceId,
        generation = generation,
        stepIndex = stepIndex,
        message = message,
        mutation = RunStateMutation(
            status = CardRunStatus.Failed,
            surface = CardRunSurface.Report,
            currentStepIndex = stepIndex,
            runtimeLane = "android_native",
            runtimeFallbackReason = reason,
            lastError = message,
            shellReportText = "原生能力失败：$message",
            clearNextActionUrl = true,
        ),
    )

    private companion object {
        const val PROGRESS_BYTES = 1024L * 1024L
        const val PROGRESS_INTERVAL_MS = 500L
        const val STOP_CONFIRMATION_TIMEOUT_MS = 2_000L
        const val STOP_POLL_MS = 25L
    }
}
