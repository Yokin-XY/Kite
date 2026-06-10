package com.kftest.app.foundation.service

import android.content.Context
import com.kftest.app.foundation.logging.Logger
import com.kftest.app.foundation.runtime.ContainerRecord
import com.kftest.app.foundation.runtime.HostProcessInspector
import com.kftest.app.foundation.runtime.HostProcessRecord
import com.kftest.app.foundation.runtime.HostStopAuditor
import com.kftest.app.foundation.runtime.HostProcessTerminator
import com.kftest.app.foundation.runtime.KFContainerManager
import com.kftest.app.foundation.runtime.ProcessExitSemantics
import com.kftest.app.foundation.runtime.RuntimeAdmissionGuard
import com.kftest.app.foundation.runtime.RuntimeFrameCoordinator
import com.kftest.app.foundation.runtime.RuntimeHealthStore
import com.kftest.app.foundation.runtime.RuntimePressureLevel
import com.kftest.app.foundation.runtime.RuntimeProcessStopReconciliation
import com.kftest.app.foundation.runtime.RuntimeProcessStopReconciliationDecision
import com.kftest.app.foundation.runtime.RuntimeProotMemoryAdmission
import com.kftest.app.foundation.runtime.RuntimeReclaimerPolicyStore
import com.kftest.app.foundation.runtime.RuntimeRecoveryTrigger
import com.kftest.app.foundation.runtime.RuntimeStartSource
import com.kftest.app.foundation.runtime.RuntimeResidentPolicyStore
import com.kftest.app.foundation.runtime.isContainerLikeProcess
import com.kftest.app.foundation.workspace.KFWorkspaceManager
import com.kftest.app.foundation.workspace.WorkSurfaceRuntimeBridge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.io.InterruptedIOException
import java.io.IOException
import java.util.LinkedHashMap
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.math.min

/**
 * 工作面层对象，主责是托管空间里的后台运行项与 one-shot 命令。
 *
 * 主归属是工作面层，次归属是任务入口层适配：
 * - 负责 runtime 生命周期、日志、健康检查
 * - 需要进容器执行时，通过建房层拿执行配置，不在这里重新拼底层 bind / rootfs 细节
 */
object BackgroundRuntimeHost {

    private const val LOG_TAG = "BackgroundRuntimeHost"
    private const val SERVICE_COMMAND_TIMEOUT_SECONDS = 45L
    private const val PROCESS_STATUS_TIMEOUT_SECONDS = 8L
    private const val PROCESS_REFRESH_DELAY_SHORT_MS = 1200L
    private const val PROCESS_REFRESH_DELAY_LONG_MS = 3200L
    private const val PROCESS_START_GRACE_MS = 30000L
    private const val RUNTIME_STATUS_RECHECK_LONG_MS = 6800L
    private const val RUNTIME_HEALTH_REFRESH_MIN_INTERVAL_MS = 15_000L
    private const val EXTERNAL_RUNTIME_PROBE_MIN_INTERVAL_MS = 10_000L
    private const val RUNTIME_STATUS_SWEEP_MIN_INTERVAL_MS = 5_000L
    private const val PROOT_CAPACITY_STATUS_REFRESH_MAX_INDEX = 3
    private const val RESTART_BACKOFF_BASE_MS = 4_000L
    private const val RESTART_BACKOFF_MAX_MS = 5 * 60 * 1000L
    private const val RESTART_FAILURE_WINDOW_MS = 15 * 60 * 1000L
    private const val RESTART_MAX_NON_CORE_FAILURES = 5
    private const val DEFERRED_RETRY_MIN_INTERVAL_MS = 10_000L
    private const val DEFERRED_RETRY_MAX_PER_PASS = 3
    private const val MANUAL_STOP_REASON = "manual-stop"

    private data class RuntimeHandle(
        val runtimeId: String,
        val process: Process,
        val logFile: File,
        val readerJob: Job,
        val monitorJob: Job
    )

    private data class CommandResult(
        val exitCode: Int,
        val output: String
    )

    private data class ExternalRuntimeProbe(
        val alive: Boolean,
        val hostPid: Int? = null
    )

    private data class CachedExternalRuntimeProbe(
        val checkedAtMs: Long,
        val probe: ExternalRuntimeProbe
    )

    private data class RuntimeCapabilityGate(
        val missingCapabilities: List<BackgroundRuntimeCapability>,
        val summary: String
    )

    private data class AutoRestartDecision(
        val shouldRestart: Boolean,
        val delayMs: Long = 0L,
        val summary: String
    )

    private val hostScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val handles = LinkedHashMap<String, RuntimeHandle>()
    private val startingRuntimeIds = LinkedHashMap<String, Long>()
    private val stoppingRuntimeIds = LinkedHashMap<String, Long>()
    private val runtimeHealthCheckedAt = LinkedHashMap<String, Long>()
    private val externalRuntimeProbeCache = LinkedHashMap<String, CachedExternalRuntimeProbe>()

    @Volatile
    private var initialized = false
    @Volatile
    private var runtimeStatusRefreshInFlight = false
    @Volatile
    private var lastRuntimeStatusRefreshStartedAtMs = 0L

    @Synchronized
    fun ensureInitialized(context: Context) {
        if (initialized) {
            return
        }
        val appContext = context.applicationContext
        Logger.i(LOG_TAG, "ensureInitialized: 开始解析空间")
        val space = resolveSpace(appContext)
        Logger.i(LOG_TAG, "ensureInitialized: 当前空间=${space.id}")
        Logger.i(LOG_TAG, "ensureInitialized: 开始确保内置运行项")
        BackgroundRuntimeRegistry.ensureBuiltinRuntimes(appContext, space.id)
        Logger.i(LOG_TAG, "ensureInitialized: 内置运行项已就绪")
        Logger.i(LOG_TAG, "ensureInitialized: 开始回放持久状态")
        reconcilePersistedStates(appContext)
        Logger.i(LOG_TAG, "ensureInitialized: 持久状态回放完成")
        initialized = true
        Logger.i(LOG_TAG, "ensureInitialized: 初始化完成")
    }

    fun listRuntimes(context: Context, spaceId: String? = null): List<BackgroundRuntimeRecord> {
        val appContext = context.applicationContext
        ensureInitialized(appContext)
        val currentSpaceId = spaceId ?: resolveSpace(appContext).id
        BackgroundRuntimeRegistry.ensureProotCapacityWorkerHeadroom(appContext, currentSpaceId)
        return BackgroundRuntimeRegistry.list(appContext, spaceId)
    }

    fun ensureCoreRuntimes(context: Context) {
        val appContext = context.applicationContext
        ensureInitialized(appContext)
        val space = resolveSpace(appContext)
        ensureCoreRuntimesStarted(appContext, space.id)
    }

    fun ensureResidentRuntimes(context: Context, reason: String = "manual") {
        val appContext = context.applicationContext
        ensureInitialized(appContext)
        hostScope.launch {
            ensureResidentRuntimesInternal(appContext, reason)
        }
    }

    private fun resolveSpace(appContext: Context) =
        KFWorkspaceManager.getCurrentSpace(appContext)
            ?: KFWorkspaceManager.listSpaces(appContext).firstOrNull()
            ?: KFWorkspaceManager.ensureDefaultSpace(appContext)

    fun refreshRuntimeStatuses(context: Context, spaceId: String? = null) {
        val appContext = context.applicationContext
        ensureInitialized(appContext)
        if (!reserveRuntimeStatusRefresh()) {
            Logger.i(LOG_TAG, "后台运行项状态刷新已合并: space=${spaceId ?: "current"}")
            return
        }
        hostScope.launch {
            try {
                refreshRuntimeStatusesInternal(appContext, spaceId)
            } finally {
                releaseRuntimeStatusRefresh()
            }
        }
    }

    fun startRuntime(context: Context, runtimeId: String) {
        val appContext = context.applicationContext
        ensureInitialized(appContext)
        // 任务入口层只能把“启动哪个运行项”交给工作面宿主，不能直接下沉去拼容器启动细节。
        hostScope.launch {
            startRuntimeInternal(appContext, runtimeId)
        }
    }

    fun stopRuntime(context: Context, runtimeId: String) {
        val appContext = context.applicationContext
        ensureInitialized(appContext)
        hostScope.launch {
            stopRuntimeInternal(appContext, runtimeId)
        }
    }

    fun reclaimRuntime(context: Context, runtimeId: String, reason: String) {
        val appContext = context.applicationContext
        ensureInitialized(appContext)
        hostScope.launch {
            val record = BackgroundRuntimeRegistry.get(appContext, runtimeId) ?: return@launch
            val logFile = BackgroundRuntimeRegistry.buildLogFile(appContext, runtimeId)
            BackgroundRuntimeRegistry.updateReclaimState(
                context = appContext,
                runtimeId = runtimeId,
                reclaimedAt = System.currentTimeMillis(),
                reclaimSource = "runtime_reclaimer",
                reclaimReason = reason
            )
            RuntimeProcessStopReconciliation.markExpectedStop(
                context = appContext,
                runtimeId = runtimeId,
                source = "runtime_reclaimer",
                reason = reason
            )
            Logger.i(
                LOG_TAG,
                "memory reclaimer requested stop: runtime=$runtimeId retention=${record.retentionClass.name} reason=$reason"
            )
            writeLog(
                logFile,
                "== Runtime memory reclaimer ==\n" +
                    "reason=$reason\n" +
                    "retention=${record.retentionClass.name}\n" +
                    "linuxLike=${record.retentionClass.linuxLikeLabel}\n" +
                    "resident=${record.retentionClass.resident}\n" +
                    "reclaimPriority=${record.retentionClass.reclaimPriority}\n"
            )
            stopRuntimeInternal(appContext, runtimeId)
        }
    }

    fun restartRuntime(context: Context, runtimeId: String) {
        val appContext = context.applicationContext
        ensureInitialized(appContext)
        hostScope.launch {
            stopRuntimeInternal(appContext, runtimeId)
            delay(300L)
            startRuntimeInternal(
                appContext = appContext,
                runtimeId = runtimeId,
                recoverySource = "manual_restart",
                recoveryReason = "manual-restart"
            )
        }
    }

    fun scheduleAutoRecovery(context: Context, runtimeId: String, reason: String) {
        val appContext = context.applicationContext
        ensureInitialized(appContext)
        hostScope.launch {
            val record = BackgroundRuntimeRegistry.get(appContext, runtimeId) ?: return@launch
            scheduleAutoRestartIfEligible(appContext, record, reason)
        }
    }

    fun readRecentLog(context: Context, runtimeId: String, maxChars: Int = 6000): String {
        ensureInitialized(context)
        val logFile = BackgroundRuntimeRegistry.buildLogFile(context.applicationContext, runtimeId)
        if (!logFile.exists()) {
            return ""
        }
        val content = logFile.readText()
        return if (content.length <= maxChars) content else content.takeLast(maxChars)
    }

    private fun reconcilePersistedStates(appContext: Context) {
        val activeProcessRuntimeIds = mutableListOf<String>()
        BackgroundRuntimeRegistry.list(appContext).forEach { record ->
            when (record.mode) {
                BackgroundRuntimeMode.PROCESS -> {
                    if (record.status.isActiveStatus()) {
                        // 宿主重建后不能直接把进程型运行项判死，否则下一轮启动会重复拉起
                        // 已经在容器里活着的 supervisor / shell 应该先走一次外部存活探测。
                        BackgroundRuntimeRegistry.updateStatus(
                            context = appContext,
                            runtimeId = record.id,
                            status = record.status,
                            pid = record.pid,
                            lastError = null
                        )
                        activeProcessRuntimeIds += record.id
                    }
                }

                BackgroundRuntimeMode.SERVICE -> {
                    if (record.status.isActiveStatus()) {
                        hostScope.launch {
                            refreshServiceRuntimeStatus(appContext, record.id)
                        }
                    }
                }
            }
        }
        activeProcessRuntimeIds.forEach { runtimeId ->
            hostScope.launch {
                val latest = BackgroundRuntimeRegistry.get(appContext, runtimeId) ?: return@launch
                refreshProcessRuntimeStatus(appContext, latest)
            }
        }
    }

    private suspend fun refreshRuntimeStatusesInternal(appContext: Context, spaceId: String?) {
        BackgroundRuntimeRegistry.refreshFromDisk(appContext)
        val records = BackgroundRuntimeRegistry.list(appContext, spaceId)
        val refreshableRecords = records.filter(::shouldRefreshRuntimeStatus)
        val skippedCount = records.size - refreshableRecords.size
        if (skippedCount > 0) {
            Logger.i(LOG_TAG, "后台运行项状态刷新跳过非活跃记录: skipped=$skippedCount, total=${records.size}")
        }
        refreshableRecords.forEach { record ->
            when (record.mode) {
                BackgroundRuntimeMode.PROCESS -> refreshProcessRuntimeStatus(appContext, record)
                BackgroundRuntimeMode.SERVICE -> refreshServiceRuntimeStatus(appContext, record.id)
            }
        }
        val effectiveSpaceId = spaceId ?: runCatching { resolveSpace(appContext).id }.getOrNull()
        if (effectiveSpaceId != null) {
            val supervisorId = BackgroundRuntimeRegistry.builtinContainerSupervisorId(effectiveSpaceId)
            val supervisorRecord = BackgroundRuntimeRegistry.get(appContext, supervisorId)
            if (supervisorRecord == null || !supervisorRecord.status.isActiveStatus()) {
                ensureCoreRuntimesStarted(appContext, effectiveSpaceId)
            }
        }
        retryDeferredAdmissionsIfEligible(appContext, spaceId)
    }

    @Synchronized
    private fun reserveRuntimeStatusRefresh(): Boolean {
        val now = System.currentTimeMillis()
        if (
            runtimeStatusRefreshInFlight ||
            now - lastRuntimeStatusRefreshStartedAtMs < RUNTIME_STATUS_SWEEP_MIN_INTERVAL_MS
        ) {
            return false
        }
        runtimeStatusRefreshInFlight = true
        lastRuntimeStatusRefreshStartedAtMs = now
        return true
    }

    @Synchronized
    private fun releaseRuntimeStatusRefresh() {
        runtimeStatusRefreshInFlight = false
    }

    private fun shouldRefreshRuntimeStatus(record: BackgroundRuntimeRecord): Boolean {
        if (record.mode != BackgroundRuntimeMode.PROCESS) {
            return true
        }
        if (
            record.kind == BackgroundRuntimeKind.PROOT_CAPACITY_WORKER &&
            record.prootCapacityWorkerIndex() > PROOT_CAPACITY_STATUS_REFRESH_MAX_INDEX &&
            handles[record.id] == null
        ) {
            return false
        }
        return record.status.isActiveStatus() ||
            record.pid != null ||
            record.restartPolicy == BackgroundRuntimeRestartPolicy.ALWAYS_CORE
    }

    private fun ensureCoreRuntimesStarted(appContext: Context, spaceId: String) {
        val supervisorId = BackgroundRuntimeRegistry.builtinContainerSupervisorId(spaceId)
        val persistedRecord = BackgroundRuntimeRegistry.get(appContext, supervisorId) ?: return
        val effectiveRecord = if (persistedRecord.status.isActiveStatus()) {
            refreshProcessRuntimeStatus(appContext, persistedRecord)
            BackgroundRuntimeRegistry.get(appContext, supervisorId) ?: persistedRecord
        } else {
            persistedRecord
        }
        if (effectiveRecord.status.isActiveStatus()) {
            scheduleProcessRefreshBurst(appContext)
            return
        }
        Logger.i(LOG_TAG, "core container supervisor is not running; starting directly")
        hostScope.launch {
            startRuntimeInternal(
                appContext = appContext,
                runtimeId = effectiveRecord.id,
                recoverySource = "core_ensure",
                recoveryReason = "core-runtime-missing"
            )
        }
    }

    private suspend fun ensureResidentRuntimesInternal(
        appContext: Context,
        reason: String
    ) {
        val space = resolveSpace(appContext)
        ensureCoreRuntimesStarted(appContext, space.id)
        val policy = RuntimeResidentPolicyStore.load(appContext)
        val trigger = RuntimeRecoveryTrigger.fromResidentReason(reason)
        val targets = BackgroundRuntimeRegistry.list(appContext, space.id)
            .filter { record ->
                record.mode == BackgroundRuntimeMode.PROCESS &&
                    record.id != BackgroundRuntimeRegistry.builtinContainerSupervisorId(space.id)
            }
            .mapNotNull { record ->
                val decision = RuntimeResidentPolicyStore.evaluate(record, policy)
                when {
                    !decision.keepResident -> null
                    isRuntimeExplicitlyManuallyStopped(record) -> null
                    record.status.isActiveStatus() -> null
                    shouldSuppressResidentRecovery(appContext, record, reason) -> null
                    decision.allowsTrigger(trigger) -> record to decision
                    else -> null
                }
            }

        if (targets.isEmpty()) {
            Logger.i(
                LOG_TAG,
                "resident runtime ensure skipped: reason=$reason trigger=${trigger.name} profile=${policy.activeProfile.name}"
            )
            return
        }

        Logger.i(
            LOG_TAG,
            "resident runtime ensure: reason=$reason trigger=${trigger.name} profile=${policy.activeProfile.name} targets=${targets.joinToString { it.first.id }}"
        )
        targets.forEach { (record, decision) ->
            val logFile = BackgroundRuntimeRegistry.buildLogFile(appContext, record.id)
            writeLog(
                logFile,
                "== Resident runtime ensure ==\nreason=$reason\ntrigger=${trigger.name}\nsource=${decision.source}\n${decision.reason}\npolicy=${decision.allowedTriggerSummary()}\n"
            )
            startRuntimeInternal(
                appContext = appContext,
                runtimeId = record.id,
                recoverySource = "resident_policy",
                recoveryReason = reason
            )
        }
    }

    private fun scheduleProcessRefreshBurst(appContext: Context) {
        RuntimeFrameCoordinator.scheduleProcessRefreshBurst(
            context = appContext,
            reason = "background-runtime"
        )
    }

    private fun scheduleRuntimeStatusRefreshBurst(appContext: Context, runtimeId: String) {
        listOf(PROCESS_REFRESH_DELAY_SHORT_MS, RUNTIME_STATUS_RECHECK_LONG_MS).forEach { delayMs ->
            hostScope.launch {
                delay(delayMs)
                val record = BackgroundRuntimeRegistry.get(appContext, runtimeId) ?: return@launch
                when (record.mode) {
                    BackgroundRuntimeMode.PROCESS -> refreshProcessRuntimeStatus(appContext, record)
                    BackgroundRuntimeMode.SERVICE -> refreshServiceRuntimeStatus(appContext, runtimeId)
                }
            }
        }
    }

    private fun shouldSuppressResidentRecovery(
        appContext: Context,
        record: BackgroundRuntimeRecord,
        reason: String
    ): Boolean {
        val stopReconciliation = reconcileStoppedRuntimeForRecovery(
            appContext = appContext,
            record = record,
            reason = "resident-ensure:$reason"
        )
        if (!stopReconciliation.suppressAutoRecovery) {
            return false
        }
        val logFile = BackgroundRuntimeRegistry.buildLogFile(appContext, record.id)
        writeLog(
            logFile,
            "== Resident recovery suppressed by stop reconciliation ==\n" +
                "reason=$reason\n" +
                "state=${stopReconciliation.observedState.name}\n" +
                "reconciliation=${stopReconciliation.reason}\n"
        )
        Logger.i(
            LOG_TAG,
            "resident recovery suppressed by stop reconciliation: runtime=${record.id}, " +
                "state=${stopReconciliation.observedState.name}, reason=${stopReconciliation.reason}"
        )
        return true
    }

    private fun reconcileStoppedRuntimeForRecovery(
        appContext: Context,
        record: BackgroundRuntimeRecord,
        reason: String
    ): RuntimeProcessStopReconciliationDecision {
        val stopReconciliation = RuntimeProcessStopReconciliation.evaluate(
            context = appContext,
            record = record,
            reason = reason
        )
        BackgroundRuntimeRegistry.updateStopReconciliationState(
            context = appContext,
            runtimeId = record.id,
            state = stopReconciliation.observedState,
            reason = stopReconciliation.reason,
            autoRecoverySuppressed = stopReconciliation.suppressAutoRecovery
        )
        return stopReconciliation
    }

    private fun scheduleAutoRestartIfEligible(
        appContext: Context,
        record: BackgroundRuntimeRecord,
        reason: String
    ) {
        val latest = BackgroundRuntimeRegistry.get(appContext, record.id) ?: record
        val stopReconciliation = reconcileStoppedRuntimeForRecovery(
            appContext = appContext,
            record = latest,
            reason = reason
        )
        val logFile = BackgroundRuntimeRegistry.buildLogFile(appContext, latest.id)
        if (stopReconciliation.suppressAutoRecovery) {
            writeLog(
                logFile,
                "== Auto recovery suppressed by stop reconciliation ==\n" +
                    "reason=$reason\n" +
                    "state=${stopReconciliation.observedState.name}\n" +
                    "reconciliation=${stopReconciliation.reason}\n"
            )
            Logger.i(
                LOG_TAG,
                "auto recovery suppressed by stop reconciliation: runtime=${latest.id}, " +
                    "state=${stopReconciliation.observedState.name}, reason=${stopReconciliation.reason}"
            )
            return
        }
        val residentPolicy = RuntimeResidentPolicyStore.load(appContext)
        val residentDecision = RuntimeResidentPolicyStore.evaluate(latest, residentPolicy)
        val trigger = RuntimeRecoveryTrigger.fromAutoRestartReason(reason)
        if (residentDecision.keepResident && !residentDecision.allowsTrigger(trigger)) {
            val blockedLogFile = BackgroundRuntimeRegistry.buildLogFile(appContext, latest.id)
            writeLog(
                blockedLogFile,
                "== Auto recovery skipped by resident policy ==\nreason=$reason\ntrigger=${trigger.name}\nsource=${residentDecision.source}\n${residentDecision.reason}\npolicy=${residentDecision.allowedTriggerSummary()}\n"
            )
            Logger.i(
                LOG_TAG,
                "auto recovery skipped by resident policy: runtime=${latest.id}, trigger=${trigger.name}, profile=${residentPolicy.activeProfile.name}"
            )
            return
        }
        val decision = resolveAutoRestartDecision(latest, reason)
        val residentPolicyLog = "== Resident recovery gate ==\ntrigger=${trigger.name}\npolicy=${residentDecision.allowedTriggerSummary()}\n"
        writeLog(logFile, residentPolicyLog)
        writeLog(
            logFile,
            "== 自动恢复评估 ==\nreason=$reason policy=${latest.restartPolicy.name} decision=${decision.summary}\n"
        )
        if (!decision.shouldRestart) {
            Logger.i(LOG_TAG, "自动恢复跳过: runtime=${latest.id}, reason=$reason, ${decision.summary}")
            return
        }

        val now = System.currentTimeMillis()
        val previousAttemptRecently = latest.lastRestartAt?.let {
            now - it < RESTART_FAILURE_WINDOW_MS
        } == true
        val nextFailureCount = if (previousAttemptRecently) {
            latest.restartFailureCount + 1
        } else {
            1
        }
        val nextAllowedAt = now + decision.delayMs
        val updated = BackgroundRuntimeRegistry.updateRestartState(
            context = appContext,
            runtimeId = latest.id,
            restartFailureCount = nextFailureCount,
            lastRestartAt = now,
            nextRestartAllowedAt = nextAllowedAt,
            lastRestartReason = reason
        ) ?: latest

        Logger.i(
            LOG_TAG,
            "自动恢复已排队: runtime=${latest.id}, delay=${decision.delayMs}, failures=$nextFailureCount, reason=$reason"
        )
        writeLog(
            logFile,
            "== 自动恢复排队 ==\ndelayMs=${decision.delayMs}\nfailureCount=$nextFailureCount\nnextAllowedAt=$nextAllowedAt\n"
        )
        hostScope.launch {
            delay(decision.delayMs)
            val candidate = BackgroundRuntimeRegistry.get(appContext, latest.id) ?: return@launch
            if (candidate.status.isActiveStatus()) {
                writeLog(logFile, "== 自动恢复取消：运行项已恢复 status=${candidate.status.name} ==\n")
                return@launch
            }
            resolveRuntimeCapabilityGate(candidate)?.let { gate ->
                writeLog(logFile, "== 自动恢复取消：${gate.summary} ==\n")
                applyRuntimeCapabilityGate(appContext, candidate, gate)
                return@launch
            }
            writeLog(logFile, "== 自动恢复启动 ==\nreason=$reason\n")
            startRuntimeInternal(
                appContext = appContext,
                runtimeId = updated.id,
                recoverySource = "auto_restart",
                recoveryReason = reason
            )
        }
    }

    private fun resolveAutoRestartDecision(
        record: BackgroundRuntimeRecord,
        reason: String
    ): AutoRestartDecision {
        if (record.mode != BackgroundRuntimeMode.PROCESS) {
            return AutoRestartDecision(false, summary = "service-mode runtime is not process-supervised")
        }
        if (record.restartPolicy == BackgroundRuntimeRestartPolicy.NEVER) {
            return AutoRestartDecision(false, summary = "restart policy is NEVER")
        }
        if (record.status.isActiveStatus()) {
            return AutoRestartDecision(false, summary = "runtime is already ${record.status.name}")
        }
        synchronized(stoppingRuntimeIds) {
            if (stoppingRuntimeIds.containsKey(record.id)) {
                return AutoRestartDecision(false, summary = "manual stop is in progress")
            }
        }
        val now = System.currentTimeMillis()
        val nextAllowed = record.nextRestartAllowedAt
        if (nextAllowed != null && now < nextAllowed) {
            return AutoRestartDecision(
                false,
                summary = "backoff active for ${nextAllowed - now}ms"
            )
        }
        if (record.isCoreSupervisorRuntime() &&
            record.restartPolicy == BackgroundRuntimeRestartPolicy.ALWAYS_CORE
        ) {
            val delay = computeRestartDelayMs(record.restartFailureCount + 1)
            return AutoRestartDecision(
                shouldRestart = true,
                delayMs = delay,
                summary = "core supervisor is always restarted in ${delay}ms for $reason"
            )
        }
        if (record.status == BackgroundRuntimeStatus.STOPPED &&
            record.lastError.isNullOrBlank() &&
            (
                record.lastExitCode == null ||
                    record.lastExitCode == 0 ||
                    ProcessExitSemantics.isManagedStopExit(record.lastExitCode)
                ) &&
            (
                record.restartPolicy != BackgroundRuntimeRestartPolicy.ALWAYS_CORE ||
                    record.lastRestartReason == MANUAL_STOP_REASON
                )
        ) {
            return AutoRestartDecision(false, summary = "clean/manual stop is not restarted")
        }
        if (record.restartPolicy != BackgroundRuntimeRestartPolicy.ALWAYS_CORE &&
            record.restartFailureCount >= RESTART_MAX_NON_CORE_FAILURES
        ) {
            return AutoRestartDecision(
                false,
                summary = "restart suspended after ${record.restartFailureCount} failed attempts"
            )
        }
        if (resolveRuntimeCapabilityGate(record) != null) {
            return AutoRestartDecision(false, summary = "blocked by runtime capability gate")
        }

        val delay = computeRestartDelayMs(record.restartFailureCount + 1)
        return AutoRestartDecision(
            shouldRestart = true,
            delayMs = delay,
            summary = "restart scheduled in ${delay}ms for $reason"
        )
    }

    private fun isRuntimeExplicitlyManuallyStopped(record: BackgroundRuntimeRecord): Boolean {
        return record.status == BackgroundRuntimeStatus.STOPPED &&
            record.lastRestartReason == MANUAL_STOP_REASON
    }

    private fun BackgroundRuntimeRecord.isCoreSupervisorRuntime(): Boolean {
        return kind == BackgroundRuntimeKind.CONTAINER_SUPERVISOR ||
            id == BackgroundRuntimeRegistry.builtinContainerSupervisorId(spaceId)
    }

    private fun computeRestartDelayMs(failureCount: Int): Long {
        val multiplier = 1L shl (failureCount - 1).coerceIn(0, 6)
        return min(RESTART_BACKOFF_BASE_MS * multiplier, RESTART_BACKOFF_MAX_MS)
    }

    private fun resetRuntimeRestartBackoff(
        appContext: Context,
        runtimeId: String,
        reason: String
    ) {
        val current = BackgroundRuntimeRegistry.get(appContext, runtimeId) ?: return
        if (current.restartFailureCount == 0 &&
            current.nextRestartAllowedAt == null &&
            current.lastRestartReason == null
        ) {
            return
        }
        BackgroundRuntimeRegistry.updateRestartState(
            context = appContext,
            runtimeId = runtimeId,
            restartFailureCount = 0,
            lastRestartReason = "reset:$reason",
            clearBackoff = true
        )
        Logger.i(LOG_TAG, "自动恢复 backoff 已清零: runtime=$runtimeId reason=$reason")
    }

    private suspend fun startRuntimeInternal(
        appContext: Context,
        runtimeId: String,
        recoverySource: String? = null,
        recoveryReason: String? = null
    ) {
        val record = BackgroundRuntimeRegistry.get(appContext, runtimeId) ?: return
        // 已在激活中（启动中或运行中）则跳过，避免重复拉起
        if (record.status.isActiveStatus()) {
            return
        }
        ensureRuntimePrerequisites(appContext, record)
        if (!record.status.isActiveStatus()) {
            resolveRuntimeCapabilityGate(record)?.let { gate ->
                applyRuntimeCapabilityGate(appContext, record, gate)
                return
            }
        }
        resolveRuntimeAdmissionDecision(appContext, record, recoverySource)?.let { decision ->
            val logFile = BackgroundRuntimeRegistry.buildLogFile(appContext, record.id)
            val admissionSource = recoverySource?.trim()?.takeIf { it.isNotBlank() }
                ?: decision.startSource.label
            writeLog(
                logFile,
                "== Runtime admission deferred ==\n${decision.summary}\nrecoverySource=${recoverySource.orEmpty()}\nrecoveryReason=${recoveryReason.orEmpty()}\n"
            )
            BackgroundRuntimeRegistry.updateAdmissionState(
                context = appContext,
                runtimeId = record.id,
                deferredAt = System.currentTimeMillis(),
                admissionSource = admissionSource,
                admissionReason = decision.summary
            )
            BackgroundRuntimeRegistry.updateStatus(
                context = appContext,
                runtimeId = record.id,
                status = record.status,
                pid = record.pid,
                lastError = decision.summary
            )
            BackgroundRuntimeRegistry.updateHealth(
                context = appContext,
                runtimeId = record.id,
                healthStatus = BackgroundRuntimeHealthStatus.BLOCKED,
                lastHealthSummary = decision.summary,
                lastHealthCheckedAt = null
            )
            Logger.i(LOG_TAG, "runtime start deferred: runtime=${record.id} ${decision.summary}")
            return
        }
        BackgroundRuntimeRegistry.clearAdmissionState(appContext, record.id)
        when (record.mode) {
            BackgroundRuntimeMode.PROCESS -> startProcessRuntime(
                appContext = appContext,
                record = record,
                recoverySource = recoverySource,
                recoveryReason = recoveryReason
            )
            BackgroundRuntimeMode.SERVICE -> startServiceRuntime(
                appContext = appContext,
                record = record,
                recoverySource = recoverySource,
                recoveryReason = recoveryReason
            )
        }
    }

    private suspend fun stopRuntimeInternal(appContext: Context, runtimeId: String) {
        val record = BackgroundRuntimeRegistry.get(appContext, runtimeId) ?: return
        // 已在终态（停止或错误）则跳过，避免重复停止
        if (record.status.isTerminalStatus()) {
            return
        }
        if (record.isCoreSupervisorRuntime()) {
            val logFile = BackgroundRuntimeRegistry.buildLogFile(appContext, record.id)
            writeLog(
                logFile,
                "== Core supervisor stop blocked ==\n" +
                    "reason=core-runtime-owned-by-android-control-plane\n"
            )
            Logger.i(LOG_TAG, "blocked stop for core supervisor runtime=${record.id}")
            ensureCoreRuntimesStarted(appContext, record.spaceId)
            return
        }
        if (record.isDefaultProotCapacityRuntime()) {
            val logFile = BackgroundRuntimeRegistry.buildLogFile(appContext, record.id)
            writeLog(
                logFile,
                "== Default PRoot stop blocked ==\n" +
                    "reason=proot-1-is-resident-capacity-baseline\n"
            )
            Logger.i(LOG_TAG, "blocked stop for default PRoot capacity runtime=${record.id}")
            startRuntimeInternal(
                appContext = appContext,
                runtimeId = record.id,
                recoverySource = "resident_policy",
                recoveryReason = "default-proot-1-stop-blocked"
            )
            return
        }
        when (record.mode) {
            BackgroundRuntimeMode.PROCESS -> stopProcessRuntime(appContext, record)
            BackgroundRuntimeMode.SERVICE -> stopServiceRuntime(appContext, record)
        }
    }

    private fun ensureRuntimePrerequisites(
        appContext: Context,
        record: BackgroundRuntimeRecord
    ) {
        val supervisorId = BackgroundRuntimeRegistry.builtinContainerSupervisorId(record.spaceId)
        // builtin container supervisor（即 "kfcontainerd"）本身不承担用户任务负载，
        // 它的前置条件（proot/rootfs/workspace）由其自举 bootstrap 链路自行保证，
        // 不需要与普通工作 runtime 统一的前置条件检查框架，故 bypass。
        if (record.id == supervisorId) {
            return
        }
        ensureCoreRuntimesStarted(appContext, record.spaceId)
    }

    private fun resolveRuntimeCapabilityGate(
        record: BackgroundRuntimeRecord
    ): RuntimeCapabilityGate? {
        val missing = record.requiredCapabilities.filterNot(::isRuntimeCapabilityAvailable)
        if (missing.isEmpty()) {
            return null
        }
        return RuntimeCapabilityGate(
            missingCapabilities = missing,
            summary = buildRuntimeCapabilitySummary(missing)
        )
    }

    private fun isRuntimeCapabilityAvailable(capability: BackgroundRuntimeCapability): Boolean {
        // 枚举穷举写法：新增 BackgroundRuntimeCapability 时，
        // Kotlin 编译器会强制要求在此处声明返回值，防止静默遗漏。
        return when (capability) {
            BackgroundRuntimeCapability.MDNS -> false
        }
    }

    private fun buildRuntimeCapabilitySummary(
        capabilities: List<BackgroundRuntimeCapability>
    ): String {
        return if (capabilities.size == 1) {
            "${capabilities.first().label} 尚未接入系统底层，当前运行项已被门禁阻止"
        } else {
            "缺少前置能力: ${capabilities.joinToString("、") { it.label }}"
        }
    }

    private fun resolveRuntimeAdmissionDecision(
        appContext: Context,
        record: BackgroundRuntimeRecord,
        recoverySource: String?
    ) = RuntimeAdmissionGuard.evaluate(
        record = record,
        pressure = RuntimeHealthStore.snapshot.value.pressure,
        policy = RuntimeReclaimerPolicyStore.load(appContext),
        startSource = RuntimeStartSource.fromRecoverySource(recoverySource)
    ).takeIf { !it.allowed }

    private suspend fun retryDeferredAdmissionsIfEligible(
        appContext: Context,
        spaceId: String?
    ) {
        val pressure = RuntimeHealthStore.snapshot.value.pressure
        if (pressure.level == RuntimePressureLevel.UNKNOWN ||
            pressure.level.ordinal > RuntimePressureLevel.ELEVATED.ordinal
        ) {
            Logger.i(
                LOG_TAG,
                "deferred runtime retry skipped: pressure=${pressure.level.name}"
            )
            return
        }

        val now = System.currentTimeMillis()
        val targets = BackgroundRuntimeRegistry.list(appContext, spaceId)
            .asSequence()
            .filter { !it.status.isActiveStatus() }
            .filter { !isRuntimeExplicitlyManuallyStopped(it) }
            .filter { !it.lastAdmissionSource.isNullOrBlank() }
            .filter {
                !RuntimeStartSource.fromRecoverySource(it.lastAdmissionSource).manualBypass
            }
            .filter { !shouldSuppressResidentRecovery(appContext, it, "deferred-runtime-retry") }
            .filter {
                val deferredAt = it.lastAdmissionDeferredAt ?: return@filter false
                now - deferredAt >= DEFERRED_RETRY_MIN_INTERVAL_MS
            }
            .sortedBy { it.lastAdmissionDeferredAt ?: Long.MAX_VALUE }
            .take(DEFERRED_RETRY_MAX_PER_PASS)
            .toList()

        if (targets.isEmpty()) {
            return
        }

        Logger.i(
            LOG_TAG,
            "deferred runtime retry pass: pressure=${pressure.level.name} targets=${targets.joinToString { it.id }}"
        )
        targets.forEach { record ->
            val logFile = BackgroundRuntimeRegistry.buildLogFile(appContext, record.id)
            writeLog(
                logFile,
                "== Deferred runtime retry ==\n" +
                    "pressure=${pressure.level.name}\n" +
                    "source=${record.lastAdmissionSource.orEmpty()}\n" +
                    "deferredAt=${record.lastAdmissionDeferredAt ?: 0L}\n" +
                    "previous=${record.lastAdmissionReason.orEmpty()}\n"
            )
            startRuntimeInternal(
                appContext = appContext,
                runtimeId = record.id,
                recoverySource = record.lastAdmissionSource,
                recoveryReason = "deferred-retry:${pressure.level.name.lowercase()}"
            )
        }
    }

    private fun applyRuntimeCapabilityGate(
        appContext: Context,
        record: BackgroundRuntimeRecord,
        gate: RuntimeCapabilityGate
    ) {
        BackgroundRuntimeRegistry.clearAdmissionState(appContext, record.id)
        Logger.i(
            LOG_TAG,
            "后台运行项被能力门禁阻止: ${record.id}, missing=${gate.missingCapabilities.joinToString { it.name }}"
        )
        val nextStatus = when (record.status) {
            BackgroundRuntimeStatus.ERROR -> BackgroundRuntimeStatus.STOPPED
            else -> if (record.status.isActiveStatus()) {
                BackgroundRuntimeStatus.STOPPED
            } else {
                record.status
            }
        }
        BackgroundRuntimeRegistry.updateStatus(
            context = appContext,
            runtimeId = record.id,
            status = nextStatus,
            pid = null,
            lastError = gate.summary
        )
        BackgroundRuntimeRegistry.updateHealth(
            context = appContext,
            runtimeId = record.id,
            healthStatus = BackgroundRuntimeHealthStatus.BLOCKED,
            lastHealthSummary = gate.summary,
            lastHealthCheckedAt = null
        )
        clearRuntimeHealthProbe(record.id)
    }

    private fun startProcessRuntime(
        appContext: Context,
        record: BackgroundRuntimeRecord,
        recoverySource: String? = null,
        recoveryReason: String? = null
    ) {
        synchronized(startingRuntimeIds) {
            if (startingRuntimeIds.containsKey(record.id)) {
                Logger.i(LOG_TAG, "忽略重复启动请求: ${record.id}")
                return
            }
            startingRuntimeIds[record.id] = System.currentTimeMillis()
        }
        Logger.i(
            LOG_TAG,
            "进入后台进程启动链: ${record.id}, route=${backgroundRuntimeRouteLabel()}, workdir=${runtimeWorkingDirectoryLabel(record)}"
        )
        val existingHandle = handles[record.id]
        if (existingHandle != null && existingHandle.process.isAlive) {
            BackgroundRuntimeRegistry.updateStatus(
                context = appContext,
                runtimeId = record.id,
                status = BackgroundRuntimeStatus.RUNNING,
                pid = existingHandle.process.safePid(),
                lastError = null
            )
            BackgroundRuntimeRegistry.updateHealth(
                context = appContext,
                runtimeId = record.id,
                healthStatus = BackgroundRuntimeHealthStatus.UNKNOWN,
                lastHealthSummary = BackgroundRuntimeHealthText.EXISTING_WAITING_FOR_PROBE,
                lastHealthCheckedAt = null
            )
            resetRuntimeRestartBackoff(appContext, record.id, "existing-process-alive")
            scheduleProcessRefreshBurst(appContext)
            scheduleRuntimeStatusRefreshBurst(appContext, record.id)
            synchronized(startingRuntimeIds) {
                startingRuntimeIds.remove(record.id)
            }
            return
        }

        val logFile = BackgroundRuntimeRegistry.buildLogFile(appContext, record.id)
        Logger.i(
            LOG_TAG,
            "后台日志文件已定位: ${record.id}, path=${runtimeLogFileLabel(appContext, logFile)}"
        )
        writeLog(logFile, buildRuntimeLogHeader("启动", record, record.startCommand))
        Logger.i(LOG_TAG, "后台启动日志已写入: ${record.id}")
        BackgroundRuntimeRegistry.updateStatus(
            context = appContext,
            runtimeId = record.id,
            status = BackgroundRuntimeStatus.STARTING,
            pid = null,
            lastError = null
        )
        BackgroundRuntimeRegistry.updateHealth(
            context = appContext,
            runtimeId = record.id,
            healthStatus = BackgroundRuntimeHealthStatus.UNKNOWN,
            lastHealthSummary = BackgroundRuntimeHealthText.STARTING,
            lastHealthCheckedAt = null
        )
        clearRuntimeHealthProbe(record.id)
        Logger.i(LOG_TAG, "后台运行项已标记为 STARTING: ${record.id}")

        runCatching {
            Logger.i(LOG_TAG, "开始构建运行命令: ${record.id}")
            // 进程型 runtime 仍由工作面层决策，但底层 exec 配置统一交给 bridge 向建房层取。
            val config = WorkSurfaceRuntimeBridge.buildShellExecConfig(
                context = appContext,
                workingDirectory = record.workingDirectory,
                payload = record.startCommand
            )
            Logger.i(LOG_TAG, "运行命令构建完成，准备启动进程: ${record.id}")
            ProcessBuilder(config.command)
                .redirectErrorStream(true)
                .apply {
                    environment().putAll(config.env)
                }
                .start()
        }.onSuccess { process ->
            val launchedPid = process.safePid()
            Logger.i(LOG_TAG, "后台进程已启动: ${record.id}, pid=$launchedPid")
            val readerJob = hostScope.launch {
                runCatching {
                    process.inputStream.bufferedReader().useLines { lines ->
                        lines.forEach { line -> writeLog(logFile, "$line\n") }
                    }
                }.onFailure { error ->
                    val canIgnore = error is InterruptedIOException ||
                        (error is IOException && !process.isAlive)
                    if (!canIgnore) {
                        writeLog(logFile, "== 读取后台输出失败 ==\n${error.stackTraceToString()}\n")
                    }
                }
            }
            val monitorJob = hostScope.launch {
                val exitCode = process.waitFor()
                readerJob.join()
                handles.remove(record.id)
                synchronized(startingRuntimeIds) {
                    startingRuntimeIds.remove(record.id)
                }
                val stopRequested = synchronized(stoppingRuntimeIds) {
                    stoppingRuntimeIds.remove(record.id) != null
                }
                if (stopRequested) {
                    BackgroundRuntimeRegistry.updateStatus(
                        context = appContext,
                        runtimeId = record.id,
                        status = BackgroundRuntimeStatus.STOPPED,
                        pid = null,
                        lastExitCode = exitCode,
                        lastError = null
                    )
                    BackgroundRuntimeRegistry.updateHealth(
                        context = appContext,
                        runtimeId = record.id,
                        healthStatus = BackgroundRuntimeHealthStatus.INACTIVE,
                        lastHealthSummary = BackgroundRuntimeHealthText.STOPPED,
                        lastHealthCheckedAt = null
                    )
                    releaseProotCapacityBudgetIfTerminal(record.id)
                    markRuntimeManuallyStopped(appContext, record.id)
                    RuntimeProcessStopReconciliation.markExpectedStop(
                        context = appContext,
                        runtimeId = record.id,
                        source = RuntimeProcessStopReconciliation.MANUAL_STOP_REASON
                    )
                    clearRuntimeHealthProbe(record.id)
                    writeLog(logFile, "== ${record.title} 已按请求停止，exitCode=$exitCode ==\n")
                    return@launch
                }
                val detachedPid = if (
                    exitCode != 0 &&
                    launchedPid != null &&
                    isRuntimeHostPidAlive(appContext, record, launchedPid)
                ) {
                    launchedPid
                } else {
                    null
                }
                if (detachedPid != null && detachedPid > 0) {
                    Logger.i(
                        LOG_TAG,
                        "后台句柄已退出但运行项仍存活: ${record.id}, wrapperExit=$exitCode, pid=$detachedPid"
                    )
                    BackgroundRuntimeRegistry.updateStatus(
                        context = appContext,
                        runtimeId = record.id,
                        status = BackgroundRuntimeStatus.RUNNING,
                        pid = detachedPid,
                        lastExitCode = exitCode,
                        lastError = null
                    )
                    writeLog(
                        logFile,
                        "== 宿主句柄已退出但进程仍存活，wrapperExit=$exitCode, pid=$detachedPid ==\n"
                    )
                    recordRuntimeRecovery(
                        appContext = appContext,
                        runtimeId = record.id,
                        recoverySource = recoverySource,
                        recoveryReason = recoveryReason
                    )
                    resetRuntimeRestartBackoff(appContext, record.id, "detached-process-alive")
                    scheduleProcessRefreshBurst(appContext)
                    scheduleRuntimeStatusRefreshBurst(appContext, record.id)
                } else {
                    val finalStatus = ProcessExitSemantics.backgroundFinalStatus(exitCode)
                    BackgroundRuntimeRegistry.updateStatus(
                        context = appContext,
                        runtimeId = record.id,
                        status = finalStatus,
                        pid = null,
                        lastExitCode = exitCode,
                        lastError = ProcessExitSemantics.backgroundExitError(record.title, exitCode)
                    )
                    BackgroundRuntimeRegistry.updateHealth(
                        context = appContext,
                        runtimeId = record.id,
                        healthStatus = BackgroundRuntimeHealthStatus.INACTIVE,
                        lastHealthSummary = BackgroundRuntimeHealthText.NOT_RUNNING,
                        lastHealthCheckedAt = null
                    )
                    releaseProotCapacityBudgetIfTerminal(record.id)
                    clearRuntimeHealthProbe(record.id)
                    writeLog(logFile, "== ${record.title} 已退出，exitCode=$exitCode ==\n")
                    BackgroundRuntimeRegistry.get(appContext, record.id)?.let { latest ->
                        scheduleAutoRestartIfEligible(
                            appContext = appContext,
                            record = latest,
                            reason = "process-exit:$exitCode"
                        )
                    }
                }
            }

            handles[record.id] = RuntimeHandle(
                runtimeId = record.id,
                process = process,
                logFile = logFile,
                readerJob = readerJob,
                monitorJob = monitorJob
            )
            BackgroundRuntimeRegistry.updateStatus(
                context = appContext,
                runtimeId = record.id,
                status = BackgroundRuntimeStatus.RUNNING,
                pid = launchedPid,
                lastError = null
            )
            BackgroundRuntimeRegistry.updateHealth(
                context = appContext,
                runtimeId = record.id,
                healthStatus = BackgroundRuntimeHealthStatus.UNKNOWN,
                lastHealthSummary = BackgroundRuntimeHealthText.WAITING_FOR_PROBE,
                lastHealthCheckedAt = null
            )
            recordRuntimeRecovery(
                appContext = appContext,
                runtimeId = record.id,
                recoverySource = recoverySource,
                recoveryReason = recoveryReason
            )
            RuntimeFrameCoordinator.refreshProcessSnapshot(
                context = appContext,
                reason = "background-runtime-started:${record.id}"
            )
            scheduleProcessRefreshBurst(appContext)
            scheduleRuntimeStatusRefreshBurst(appContext, record.id)
            synchronized(startingRuntimeIds) {
                startingRuntimeIds.remove(record.id)
            }
        }.onFailure { error ->
            Logger.e(LOG_TAG, "启动后台进程失败: ${record.id}, ${error.message}")
            writeLog(logFile, "== 启动失败 ==\n${error.stackTraceToString()}\n")
            BackgroundRuntimeRegistry.updateStatus(
                context = appContext,
                runtimeId = record.id,
                status = BackgroundRuntimeStatus.ERROR,
                pid = null,
                lastError = error.message ?: "后台进程启动失败"
            )
            BackgroundRuntimeRegistry.updateHealth(
                context = appContext,
                runtimeId = record.id,
                healthStatus = BackgroundRuntimeHealthStatus.INACTIVE,
                lastHealthSummary = BackgroundRuntimeHealthText.NOT_RUNNING,
                lastHealthCheckedAt = null
            )
            releaseProotCapacityBudgetIfTerminal(record.id)
            clearRuntimeHealthProbe(record.id)
            BackgroundRuntimeRegistry.get(appContext, record.id)?.let { latest ->
                scheduleAutoRestartIfEligible(
                    appContext = appContext,
                    record = latest,
                    reason = "start-failure"
                )
            }
            synchronized(startingRuntimeIds) {
                startingRuntimeIds.remove(record.id)
            }
        }
    }

    private fun stopProcessRuntime(appContext: Context, record: BackgroundRuntimeRecord) {
        synchronized(startingRuntimeIds) {
            startingRuntimeIds.remove(record.id)
        }
        synchronized(stoppingRuntimeIds) {
            stoppingRuntimeIds[record.id] = System.currentTimeMillis()
        }
        val handle = handles.remove(record.id)
        val logFile = handle?.logFile ?: BackgroundRuntimeRegistry.buildLogFile(appContext, record.id)
        if (handle == null) {
            Logger.i(LOG_TAG, "后台停止请求未命中本地句柄，尝试按外部进程补偿: ${record.id}")
        }

        writeLog(logFile, "== 收到停止请求 ==\n")
        val hostPid = handle?.process?.safePid() ?: resolveRuntimeHostPid(appContext, record)
        val stopAuditSeed = HostStopAuditor.capture(hostPid ?: -1, LOG_TAG)
        if (hostPid != null && hostPid > 0) {
            val outcome = kotlinx.coroutines.runBlocking {
                HostProcessTerminator.terminateHostProcess(hostPid) { message ->
                    Logger.i(LOG_TAG, "后台停止补偿: runtime=${record.id} pid=$hostPid $message")
                }
            }
            writeLog(
                logFile,
                "== 停止补偿 pid=$hostPid exited=${outcome.exited} term=${outcome.sentTerminate} kill=${outcome.sentKill} ==\n"
            )
        } else {
            writeLog(logFile, "== 停止补偿未命中宿主 pid ==\n")
        }
        if (handle?.process?.isAlive == true) {
            handle.process.destroy()
        }
        if (handle?.process?.isAlive == true) {
            handle.process.destroyForcibly()
        }
        kotlinx.coroutines.runBlocking {
            HostStopAuditor.audit(stopAuditSeed, LOG_TAG)
        }?.let { report ->
            Logger.i(LOG_TAG, "后台停止诊断: runtime=${record.id} ${report.toCompactSummary()}")
            writeLog(logFile, report.toLogBlock("停止诊断 ${record.title}"))
        }
        BackgroundRuntimeRegistry.updateStatus(
            context = appContext,
            runtimeId = record.id,
            status = BackgroundRuntimeStatus.STOPPED,
            pid = null,
            lastError = null
        )
        BackgroundRuntimeRegistry.updateHealth(
            context = appContext,
            runtimeId = record.id,
            healthStatus = BackgroundRuntimeHealthStatus.INACTIVE,
            lastHealthSummary = BackgroundRuntimeHealthText.STOPPED,
            lastHealthCheckedAt = null
        )
        releaseProotCapacityBudgetIfTerminal(record.id)
        markRuntimeManuallyStopped(appContext, record.id)
        RuntimeProcessStopReconciliation.markExpectedStop(
            context = appContext,
            runtimeId = record.id,
            source = RuntimeProcessStopReconciliation.MANUAL_STOP_REASON
        )
        clearRuntimeHealthProbe(record.id)
        RuntimeFrameCoordinator.refreshProcessSnapshot(
            context = appContext,
            reason = "background-runtime-stopped:${record.id}"
        )
        scheduleProcessRefreshBurst(appContext)
    }

    private fun refreshProcessRuntimeStatus(appContext: Context, record: BackgroundRuntimeRecord) {
        val handle = handles[record.id]
        val isAlive = handle?.process?.isAlive == true
        val handlePid = handle?.process?.safePid()
        val withinStartingGrace =
            record.status == BackgroundRuntimeStatus.STARTING &&
                record.lastStartedAt != null &&
                (System.currentTimeMillis() - record.lastStartedAt) < PROCESS_START_GRACE_MS
        val externalProbe = if (!shouldProbeProcessRuntimeExternally(record, isAlive, withinStartingGrace)) {
            ExternalRuntimeProbe(alive = false)
        } else {
            probeProcessRuntimeExternally(appContext, record)
        }
        val externalAlive = externalProbe.alive
        val externalPid = externalProbe.hostPid
        val nextStatus = when {
            isAlive || externalAlive -> BackgroundRuntimeStatus.RUNNING
            withinStartingGrace -> BackgroundRuntimeStatus.STARTING
            record.status.isActiveStatus() -> BackgroundRuntimeStatus.STOPPED
            else -> record.status
        }
        val updatedRecord = BackgroundRuntimeRegistry.updateStatus(
            context = appContext,
            runtimeId = record.id,
            status = nextStatus,
            pid = when {
                isAlive -> handlePid ?: externalPid ?: record.pid
                externalAlive -> externalPid ?: record.pid
                withinStartingGrace -> handlePid ?: record.pid
                else -> null
            },
            lastError = resolvedRuntimeRefreshError(
                record = record,
                nextStatus = nextStatus,
                isAlive = isAlive,
                externalAlive = externalAlive,
                withinStartingGrace = withinStartingGrace
            )
        )
        when (nextStatus) {
            BackgroundRuntimeStatus.RUNNING -> {
                resetRuntimeRestartBackoff(appContext, record.id, "runtime-status-running")
                refreshRuntimeHealth(appContext, updatedRecord ?: record.copy(status = nextStatus))
            }

            BackgroundRuntimeStatus.STARTING -> {
                BackgroundRuntimeRegistry.updateHealth(
                    context = appContext,
                    runtimeId = record.id,
                    healthStatus = BackgroundRuntimeHealthStatus.UNKNOWN,
                    lastHealthSummary = BackgroundRuntimeHealthText.STARTING,
                    lastHealthCheckedAt = null
                )
            }

            else -> {
                val effectiveRecord = updatedRecord ?: record.copy(status = nextStatus)
                val gate = resolveRuntimeCapabilityGate(effectiveRecord)
                if (gate != null) {
                    applyRuntimeCapabilityGate(appContext, effectiveRecord, gate)
                } else {
                    BackgroundRuntimeRegistry.updateHealth(
                        context = appContext,
                        runtimeId = record.id,
                        healthStatus = BackgroundRuntimeHealthStatus.INACTIVE,
                        lastHealthSummary = BackgroundRuntimeHealthText.NOT_RUNNING,
                        lastHealthCheckedAt = null
                    )
                    clearRuntimeHealthProbe(record.id)
                }
            }
        }
        RuntimeFrameCoordinator.refreshProcessSnapshot(
            context = appContext,
            reason = "background-runtime-status:${record.id}"
        )
        if (nextStatus == BackgroundRuntimeStatus.RUNNING) {
            scheduleProcessRefreshBurst(appContext)
        } else if (nextStatus == BackgroundRuntimeStatus.STOPPED || nextStatus == BackgroundRuntimeStatus.ERROR) {
            releaseProotCapacityBudgetIfTerminal(record.id)
            (updatedRecord ?: BackgroundRuntimeRegistry.get(appContext, record.id))?.let { latest ->
                scheduleAutoRestartIfEligible(
                    appContext = appContext,
                    record = latest,
                    reason = "status-refresh:${nextStatus.name}"
                )
            }
        }
    }

    private fun shouldProbeProcessRuntimeExternally(
        record: BackgroundRuntimeRecord,
        isAlive: Boolean,
        withinStartingGrace: Boolean
    ): Boolean {
        if (isAlive || withinStartingGrace) {
            return false
        }
        return record.status.isActiveStatus() ||
            record.pid != null ||
            record.restartPolicy == BackgroundRuntimeRestartPolicy.ALWAYS_CORE
    }

    private fun resolvedRuntimeRefreshError(
        record: BackgroundRuntimeRecord,
        nextStatus: BackgroundRuntimeStatus,
        isAlive: Boolean,
        externalAlive: Boolean,
        withinStartingGrace: Boolean
    ): String? {
        if (isAlive || externalAlive || withinStartingGrace) {
            return null
        }
        return when (nextStatus) {
            BackgroundRuntimeStatus.STOPPED -> {
                if (
                    record.status == BackgroundRuntimeStatus.STOPPED ||
                    ProcessExitSemantics.isManagedStopExit(record.lastExitCode)
                ) {
                    null
                } else {
                    "${record.title} 当前未运行"
                }
            }

            BackgroundRuntimeStatus.ERROR -> record.lastError
            else -> null
        }
    }

    private fun probeProcessRuntimeExternally(
        appContext: Context,
        record: BackgroundRuntimeRecord
    ): ExternalRuntimeProbe {
        readExternalRuntimeProbeCache(record.id)?.let { cached ->
            Logger.i(LOG_TAG, "复用外部存活探测结果: ${record.id}, alive=${cached.alive}, pid=${cached.hostPid ?: 0}")
            return cached
        }

        val hostSnapshot = HostProcessInspector.readSnapshot(
            logTag = LOG_TAG,
            timeoutSeconds = PROCESS_STATUS_TIMEOUT_SECONDS
        )
        resolveRuntimeHostPid(appContext, record, hostSnapshot)?.let { hostPid ->
            Logger.i(LOG_TAG, "命中持久宿主 pid: ${record.id}, pid=$hostPid")
            return ExternalRuntimeProbe(alive = true, hostPid = hostPid).also {
                writeExternalRuntimeProbeCache(record.id, it)
            }
        }

        val statusCommand = record.statusCommand?.trim().orEmpty()
        if (statusCommand.isBlank()) {
            return ExternalRuntimeProbe(alive = false)
        }
        val logFile = BackgroundRuntimeRegistry.buildLogFile(appContext, record.id)
        Logger.i(LOG_TAG, "开始外部存活探测: ${record.id}, command=$statusCommand")
        val result = executeOneShotCommand(
            appContext = appContext,
            record = record,
            command = statusCommand,
            logFile = logFile,
            timeoutSeconds = PROCESS_STATUS_TIMEOUT_SECONDS
        )
        val alive = result.exitCode == 0
        Logger.i(
            LOG_TAG,
            "外部存活探测完成: ${record.id}, exit=${result.exitCode}, alive=$alive, output=${result.output.take(120)}"
        )
        return ExternalRuntimeProbe(
            alive = alive,
            hostPid = resolveRuntimeHostPid(appContext, record, hostSnapshot)
        ).also { writeExternalRuntimeProbeCache(record.id, it) }
    }

    private fun readExternalRuntimeProbeCache(runtimeId: String): ExternalRuntimeProbe? {
        val now = System.currentTimeMillis()
        synchronized(externalRuntimeProbeCache) {
            val cached = externalRuntimeProbeCache[runtimeId] ?: return null
            if (now - cached.checkedAtMs <= EXTERNAL_RUNTIME_PROBE_MIN_INTERVAL_MS) {
                return cached.probe
            }
            externalRuntimeProbeCache.remove(runtimeId)
            return null
        }
    }

    private fun writeExternalRuntimeProbeCache(runtimeId: String, probe: ExternalRuntimeProbe) {
        synchronized(externalRuntimeProbeCache) {
            externalRuntimeProbeCache[runtimeId] = CachedExternalRuntimeProbe(
                checkedAtMs = System.currentTimeMillis(),
                probe = probe
            )
        }
    }

    private fun resolveRuntimeHostPid(
        appContext: Context,
        record: BackgroundRuntimeRecord
    ): Int? {
        return resolveRuntimeHostPid(
            appContext = appContext,
            record = record,
            hostSnapshot = HostProcessInspector.readSnapshot(
                logTag = LOG_TAG,
                timeoutSeconds = PROCESS_STATUS_TIMEOUT_SECONDS
            )
        )
    }

    private fun resolveRuntimeHostPid(
        appContext: Context,
        record: BackgroundRuntimeRecord,
        hostSnapshot: com.kftest.app.foundation.runtime.HostProcessSnapshot
    ): Int? {
        val persistedPid = record.pid?.takeIf { it > 0 } ?: return null
        return persistedPid.takeIf { isRuntimeHostPidAlive(appContext, record, it, hostSnapshot) }
    }

    private fun isRuntimeHostPidAlive(
        appContext: Context,
        record: BackgroundRuntimeRecord,
        pid: Int
    ): Boolean {
        return isRuntimeHostPidAlive(
            appContext = appContext,
            record = record,
            pid = pid,
            hostSnapshot = HostProcessInspector.readSnapshot(
                logTag = LOG_TAG,
                timeoutSeconds = PROCESS_STATUS_TIMEOUT_SECONDS
            )
        )
    }

    private fun isRuntimeHostPidAlive(
        appContext: Context,
        record: BackgroundRuntimeRecord,
        pid: Int,
        hostSnapshot: com.kftest.app.foundation.runtime.HostProcessSnapshot
    ): Boolean {
        if (pid <= 0) {
            return false
        }
        val container = WorkSurfaceRuntimeBridge.resolveActiveContainer(appContext)
        val hostProcess = hostSnapshot.appProcess(pid) ?: return false
        return matchesRuntimeHostProcess(record, hostProcess, container)
    }

    private fun matchesRuntimeHostProcess(
        record: BackgroundRuntimeRecord,
        process: HostProcessRecord,
        container: ContainerRecord
    ): Boolean {
        if (!process.isContainerLikeProcess(container, RUNTIME_HOST_COMMANDS)) {
            return false
        }

        val normalizedCommand = process.command.lowercase()
        val normalizedArgs = process.commandLine.lowercase()
        val runtimeTokens = record.runtimeIdentityTokens()
        return runtimeTokens.isEmpty() ||
            runtimeTokens.any { token ->
                normalizedCommand == token || normalizedArgs.contains(token)
            }
    }

    private fun refreshRuntimeHealth(
        appContext: Context,
        record: BackgroundRuntimeRecord,
        force: Boolean = false
    ) {
        val latestRecord = BackgroundRuntimeRegistry.get(appContext, record.id) ?: record
        if (!isRuntimeReadyForHealthProbe(appContext, latestRecord)) {
            BackgroundRuntimeRegistry.updateHealth(
                context = appContext,
                runtimeId = latestRecord.id,
                healthStatus = BackgroundRuntimeHealthStatus.INACTIVE,
                lastHealthSummary = BackgroundRuntimeHealthText.NOT_RUNNING,
                lastHealthCheckedAt = null
            )
            clearRuntimeHealthProbe(latestRecord.id)
            return
        }

        val healthCommand = latestRecord.healthCommand?.trim().orEmpty()
        if (healthCommand.isBlank()) {
            BackgroundRuntimeRegistry.updateHealth(
                context = appContext,
                runtimeId = latestRecord.id,
                healthStatus = BackgroundRuntimeHealthStatus.UNKNOWN,
                lastHealthSummary = BackgroundRuntimeHealthText.UNCONFIGURED,
                lastHealthCheckedAt = null
            )
            clearRuntimeHealthProbe(latestRecord.id)
            return
        }

        val startedAt = latestRecord.lastStartedAt
        val startupDelayMs = latestRecord.healthCheckStartupDelayMs ?: 0L
        if (startedAt != null && startupDelayMs > 0L) {
            val elapsedMs = System.currentTimeMillis() - startedAt
            if (elapsedMs in 0 until startupDelayMs) {
                BackgroundRuntimeRegistry.updateHealth(
                    context = appContext,
                    runtimeId = latestRecord.id,
                    healthStatus = BackgroundRuntimeHealthStatus.UNKNOWN,
                    lastHealthSummary = BackgroundRuntimeHealthText.STARTING,
                    lastHealthCheckedAt = null
                )
                return
            }
        }

        if (!shouldRunRuntimeHealthProbe(latestRecord.id, force)) {
            return
        }

        val logFile = BackgroundRuntimeRegistry.buildLogFile(appContext, latestRecord.id)
        writeLog(logFile, buildRuntimeLogHeader("健康探测", latestRecord, healthCommand))
        val result = executeOneShotCommand(
            appContext = appContext,
            record = latestRecord,
            command = healthCommand,
            logFile = logFile,
            timeoutSeconds = PROCESS_STATUS_TIMEOUT_SECONDS
        )
        val currentRecord = BackgroundRuntimeRegistry.get(appContext, latestRecord.id) ?: latestRecord
        if (!isRuntimeReadyForHealthProbe(appContext, currentRecord)) {
            BackgroundRuntimeRegistry.updateHealth(
                context = appContext,
                runtimeId = currentRecord.id,
                healthStatus = BackgroundRuntimeHealthStatus.INACTIVE,
                lastHealthSummary = BackgroundRuntimeHealthText.NOT_RUNNING,
                lastHealthCheckedAt = null
            )
            clearRuntimeHealthProbe(currentRecord.id)
            return
        }
        val checkedAt = System.currentTimeMillis()
        val healthStatus = if (result.exitCode == 0) {
            BackgroundRuntimeHealthStatus.HEALTHY
        } else {
            BackgroundRuntimeHealthStatus.UNHEALTHY
        }
        BackgroundRuntimeRegistry.updateHealth(
            context = appContext,
            runtimeId = currentRecord.id,
            healthStatus = healthStatus,
            lastHealthSummary = summarizeHealthOutput(result.exitCode, result.output),
            lastHealthCheckedAt = checkedAt
        )
    }

    private fun isRuntimeReadyForHealthProbe(
        appContext: Context,
        record: BackgroundRuntimeRecord
    ): Boolean {
        if (record.status != BackgroundRuntimeStatus.RUNNING) {
            return false
        }
        return when (record.mode) {
            BackgroundRuntimeMode.PROCESS -> {
                val handleAlive = handles[record.id]?.process?.isAlive == true
                handleAlive || resolveRuntimeHostPid(appContext, record) != null
            }

            BackgroundRuntimeMode.SERVICE -> true
        }
    }

    private fun shouldRunRuntimeHealthProbe(runtimeId: String, force: Boolean): Boolean {
        val now = System.currentTimeMillis()
        synchronized(runtimeHealthCheckedAt) {
            val lastCheckedAt = runtimeHealthCheckedAt[runtimeId]
            if (!force &&
                lastCheckedAt != null &&
                (now - lastCheckedAt) < RUNTIME_HEALTH_REFRESH_MIN_INTERVAL_MS
            ) {
                return false
            }
            runtimeHealthCheckedAt[runtimeId] = now
            return true
        }
    }

    private fun clearRuntimeHealthProbe(runtimeId: String) {
        synchronized(runtimeHealthCheckedAt) {
            runtimeHealthCheckedAt.remove(runtimeId)
        }
        synchronized(externalRuntimeProbeCache) {
            externalRuntimeProbeCache.remove(runtimeId)
        }
    }

    private fun summarizeHealthOutput(exitCode: Int, output: String): String {
        val normalized = output
            .replace(ANSI_ESCAPE_REGEX, "")
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .take(3)
            .joinToString(" | ")
            .trim()
        if (normalized.isNotBlank()) {
            return normalized.take(240)
        }
        return if (exitCode == 0) {
            "健康探测通过"
        } else {
            "健康探测失败，exitCode=$exitCode"
        }
    }

    private fun startServiceRuntime(
        appContext: Context,
        record: BackgroundRuntimeRecord,
        recoverySource: String? = null,
        recoveryReason: String? = null
    ) {
        // one-shot/service 属于工作面层编排；真正进入容器执行时仍统一借建房层配置。
        val logFile = BackgroundRuntimeRegistry.buildLogFile(appContext, record.id)
        writeLog(logFile, buildRuntimeLogHeader("启动服务", record, record.startCommand))
        BackgroundRuntimeRegistry.updateStatus(
            context = appContext,
            runtimeId = record.id,
            status = BackgroundRuntimeStatus.STARTING,
            pid = null,
            lastError = null
        )
        BackgroundRuntimeRegistry.updateHealth(
            context = appContext,
            runtimeId = record.id,
            healthStatus = BackgroundRuntimeHealthStatus.UNKNOWN,
            lastHealthSummary = BackgroundRuntimeHealthText.STARTING,
            lastHealthCheckedAt = null
        )
        clearRuntimeHealthProbe(record.id)

        val result = executeOneShotCommand(appContext, record, record.startCommand, logFile)
        if (result.exitCode != 0) {
            BackgroundRuntimeRegistry.updateStatus(
                context = appContext,
                runtimeId = record.id,
                status = BackgroundRuntimeStatus.ERROR,
                pid = null,
                lastExitCode = result.exitCode,
                lastError = "${record.title} 启动失败，exitCode=${result.exitCode}"
            )
            BackgroundRuntimeRegistry.updateHealth(
                context = appContext,
                runtimeId = record.id,
                healthStatus = BackgroundRuntimeHealthStatus.INACTIVE,
                lastHealthSummary = BackgroundRuntimeHealthText.NOT_RUNNING,
                lastHealthCheckedAt = null
            )
            markRuntimeManuallyStopped(appContext, record.id)
            clearRuntimeHealthProbe(record.id)
            return
        }

        refreshServiceRuntimeStatus(appContext, record.id, logFile)
        BackgroundRuntimeRegistry.get(appContext, record.id)
            ?.takeIf { it.status == BackgroundRuntimeStatus.RUNNING }
            ?.let {
                recordRuntimeRecovery(
                    appContext = appContext,
                    runtimeId = it.id,
                    recoverySource = recoverySource,
                    recoveryReason = recoveryReason
                )
            }
        scheduleProcessRefreshBurst(appContext)
        scheduleRuntimeStatusRefreshBurst(appContext, record.id)
    }

    private fun stopServiceRuntime(appContext: Context, record: BackgroundRuntimeRecord) {
        val logFile = BackgroundRuntimeRegistry.buildLogFile(appContext, record.id)
        if (record.stopCommand.isNullOrBlank()) {
            BackgroundRuntimeRegistry.updateStatus(
                context = appContext,
                runtimeId = record.id,
                status = BackgroundRuntimeStatus.STOPPED,
                pid = null,
                lastError = null
            )
            BackgroundRuntimeRegistry.updateHealth(
                context = appContext,
                runtimeId = record.id,
                healthStatus = BackgroundRuntimeHealthStatus.INACTIVE,
                lastHealthSummary = BackgroundRuntimeHealthText.NOT_RUNNING,
                lastHealthCheckedAt = null
            )
            markRuntimeManuallyStopped(appContext, record.id)
            RuntimeProcessStopReconciliation.markExpectedStop(
                context = appContext,
                runtimeId = record.id,
                source = RuntimeProcessStopReconciliation.MANUAL_STOP_REASON
            )
            clearRuntimeHealthProbe(record.id)
            return
        }

        writeLog(logFile, buildRuntimeLogHeader("停止服务", record, record.stopCommand))
        val result = executeOneShotCommand(appContext, record, record.stopCommand, logFile)
        if (result.exitCode != 0) {
            BackgroundRuntimeRegistry.updateStatus(
                context = appContext,
                runtimeId = record.id,
                status = BackgroundRuntimeStatus.ERROR,
                pid = null,
                lastExitCode = result.exitCode,
                lastError = "${record.title} 停止失败，exitCode=${result.exitCode}"
            )
            refreshRuntimeHealth(appContext, record, force = true)
            return
        }

        refreshServiceRuntimeStatus(appContext, record.id, logFile)
        markRuntimeManuallyStopped(appContext, record.id)
        RuntimeProcessStopReconciliation.markExpectedStop(
            context = appContext,
            runtimeId = record.id,
            source = RuntimeProcessStopReconciliation.MANUAL_STOP_REASON
        )
        scheduleProcessRefreshBurst(appContext)
    }

    private fun markRuntimeManuallyStopped(appContext: Context, runtimeId: String) {
        BackgroundRuntimeRegistry.updateRestartState(
            context = appContext,
            runtimeId = runtimeId,
            restartFailureCount = 0,
            lastRestartReason = MANUAL_STOP_REASON,
            clearBackoff = true
        )
    }

    private fun recordRuntimeRecovery(
        appContext: Context,
        runtimeId: String,
        recoverySource: String?,
        recoveryReason: String?
    ) {
        val source = recoverySource?.trim().orEmpty()
        if (source.isBlank()) {
            return
        }
        BackgroundRuntimeRegistry.updateRecoveryState(
            context = appContext,
            runtimeId = runtimeId,
            recoveredAt = System.currentTimeMillis(),
            recoverySource = source,
            recoveryReason = recoveryReason?.trim()?.takeIf { it.isNotBlank() }
        )
    }

    private fun refreshServiceRuntimeStatus(
        appContext: Context,
        runtimeId: String,
        logFile: File = BackgroundRuntimeRegistry.buildLogFile(appContext, runtimeId)
    ) {
        val record = BackgroundRuntimeRegistry.get(appContext, runtimeId) ?: return
        resolveRuntimeCapabilityGate(record)?.let { gate ->
            applyRuntimeCapabilityGate(appContext, record, gate)
            return
        }
        val statusCommand = record.statusCommand
        if (statusCommand.isNullOrBlank()) {
            BackgroundRuntimeRegistry.updateStatus(
                context = appContext,
                runtimeId = runtimeId,
                status = BackgroundRuntimeStatus.RUNNING,
                pid = null,
                lastError = null
            )
            refreshRuntimeHealth(appContext, record, force = true)
            return
        }

        writeLog(logFile, buildRuntimeLogHeader("探测服务状态", record, statusCommand))
        val result = executeOneShotCommand(appContext, record, statusCommand, logFile)
        val status = if (result.exitCode == 0) {
            BackgroundRuntimeStatus.RUNNING
        } else {
            BackgroundRuntimeStatus.STOPPED
        }
        val updatedRecord = BackgroundRuntimeRegistry.updateStatus(
            context = appContext,
            runtimeId = runtimeId,
            status = status,
            pid = null,
            lastExitCode = result.exitCode,
            lastError = if (status == BackgroundRuntimeStatus.RUNNING) {
                null
            } else {
                "${record.title} 当前未运行"
            }
        )
        when (status) {
            BackgroundRuntimeStatus.RUNNING -> {
                refreshRuntimeHealth(
                    appContext,
                    updatedRecord ?: record.copy(status = BackgroundRuntimeStatus.RUNNING),
                    force = true
                )
            }

            else -> {
                BackgroundRuntimeRegistry.updateHealth(
                    context = appContext,
                    runtimeId = runtimeId,
                    healthStatus = BackgroundRuntimeHealthStatus.INACTIVE,
                    lastHealthSummary = BackgroundRuntimeHealthText.NOT_RUNNING,
                    lastHealthCheckedAt = null
                )
                clearRuntimeHealthProbe(runtimeId)
            }
        }
    }

    private fun executeOneShotCommand(
        appContext: Context,
        record: BackgroundRuntimeRecord,
        command: String,
        logFile: File,
        timeoutSeconds: Long = SERVICE_COMMAND_TIMEOUT_SECONDS
    ): CommandResult {
        return runCatching {
            // one-shot 统一经 work-surface bridge 进入建房层，不在这里重写 PRoot 细节。
            val config = WorkSurfaceRuntimeBridge.buildShellExecConfig(
                context = appContext,
                workingDirectory = record.workingDirectory,
                payload = command,
                loginShell = false
            )
            val process = ProcessBuilder(config.command)
                .redirectErrorStream(true)
                .apply {
                    environment().putAll(config.env)
                }
                .start()
            val outputBuffer = StringBuilder()
            val readerThread = thread(start = true, isDaemon = true, name = "BackgroundRuntimeReader") {
                runCatching {
                    process.inputStream.bufferedReader().useLines { lines ->
                        lines.forEach { line ->
                            outputBuffer.append(line).append('\n')
                        }
                    }
                }
            }
            val finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
            readerThread.join(2000L)
            val output = outputBuffer.toString()
            if (!finished) {
                process.destroyForcibly()
                writeLog(logFile, "$output\n== 命令超时(${timeoutSeconds}s)，已强制终止 ==\n")
                CommandResult(-1, output)
            } else {
                writeLog(logFile, output)
                CommandResult(process.exitValue(), output)
            }
        }.getOrElse { error ->
            Logger.e(LOG_TAG, "执行后台服务命令失败: ${record.id}, ${error.message}")
            writeLog(logFile, "${error.stackTraceToString()}\n")
            CommandResult(-1, error.message ?: "执行失败")
        }
    }

    private fun buildRuntimeLogHeader(
        actionLabel: String,
        record: BackgroundRuntimeRecord,
        command: String?
    ): String {
        return buildString {
            appendLine("== $actionLabel ${record.title} ==")
            appendLine("路由: ${backgroundRuntimeRouteLabel()}")
            appendLine("工作目录: ${runtimeWorkingDirectoryLabel(record)}")
            appendLine("命令: ${command.orEmpty()}")
        }
    }

    private fun backgroundRuntimeRouteLabel(): String {
        return WorkSurfaceRuntimeBridge.actionRouteLabel(
            com.kftest.app.foundation.runtime.RuntimeActionKind.BACKGROUND_RUNTIME
        )
    }

    private fun runtimeWorkingDirectoryLabel(record: BackgroundRuntimeRecord): String {
        return WorkSurfaceRuntimeBridge.describeContainerPath(record.workingDirectory)
    }

    private fun runtimeLogFileLabel(appContext: Context, logFile: File): String {
        return WorkSurfaceRuntimeBridge.describeHostPath(appContext, logFile.absolutePath)
    }

    private fun writeLog(logFile: File, content: String) {
        logFile.parentFile?.mkdirs()
        logFile.appendText(content)
    }

    private fun BackgroundRuntimeRecord.runtimeIdentityTokens(): Set<String> {
        val normalizedStart = startCommand.lowercase()
        return when (kind) {
            BackgroundRuntimeKind.CONTAINER_SUPERVISOR -> setOf("supervisord", "supervisord.conf")
            BackgroundRuntimeKind.PROOT_CAPACITY_WORKER ->
                setOf(
                    "kf_proot_capacity_worker_id",
                    id.lowercase(),
                    "kf-proot-capacity-worker",
                    "worker-${prootCapacityWorkerIndex()}.pid"
                )
            else -> buildSet {
                sequenceOf(
                    startCommand.substringAfterLast("exec ", ""),
                    startCommand.substringAfterLast("/"),
                    title
                )
                    .map { it.trim().lowercase() }
                    .filter { it.isNotBlank() }
                    .forEach { token ->
                        add(token.substringBefore(' '))
                        add(token.substringAfterLast('/'))
                    }
                if (normalizedStart.contains("openclaw")) {
                    add("openclaw")
                }
            }
        }
    }

    private fun BackgroundRuntimeRecord.isDefaultProotCapacityRuntime(): Boolean {
        return kind == BackgroundRuntimeKind.PROOT_CAPACITY_WORKER &&
            prootCapacityWorkerIndex() == 1
    }

    private fun BackgroundRuntimeRecord.prootCapacityWorkerIndex(): Int {
        return id.substringAfterLast("-proot-capacity-worker-", "")
            .toIntOrNull()
            ?.takeIf { it > 0 }
            ?: Int.MAX_VALUE
    }

    private fun releaseProotCapacityBudgetIfTerminal(runtimeId: String) {
        RuntimeProotMemoryAdmission.release(runtimeId)
    }

    private fun Process.safePid(): Int? {
        val methodPid = runCatching {
            java.lang.Process::class.java.getMethod("pid").invoke(this)
        }.getOrNull()
        when (methodPid) {
            is Long -> return methodPid.toInt()
            is Int -> return methodPid
        }
        return sequenceOf("pid", "mPid", "mProcessId")
            .mapNotNull { fieldName ->
                runCatching {
                    javaClass.getDeclaredField(fieldName).apply {
                        isAccessible = true
                    }.get(this@safePid)
                }.getOrNull()
            }
            .mapNotNull { value -> value.toString().toIntOrNull() }
            .firstOrNull()
    }

    private val RUNTIME_HOST_COMMANDS = setOf(
        "supervisord"
    )

    private val ANSI_ESCAPE_REGEX = Regex("""\u001B\[[0-9;?]*[ -/]*[@-~]""")
}
