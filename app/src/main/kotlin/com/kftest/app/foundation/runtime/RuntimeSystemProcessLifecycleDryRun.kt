package com.kftest.app.foundation.runtime

import com.kftest.app.foundation.service.BackgroundRuntimeKind
import com.kftest.app.foundation.service.BackgroundRuntimeRecord
import com.kftest.app.foundation.service.isActiveRuntime
import java.io.File

enum class RuntimeSystemProcessTier {
    SYSTEM_CORE,
    USER_LOCKED,
    FOREGROUND,
    LEASE_POOL,
    QUARANTINE,
    RUNTIME_CORE_CAPACITY,
    RUNTIME_ELASTIC_CAPACITY
}

enum class RuntimeHostLifecycleState {
    RUNNING,
    STOPPED,
    SUSPENDED,
    RELEASED
}

data class RuntimeProcessMemoryBudgetLedgerSnapshot(
    val mode: String = "runtime_process_memory_budget_ledger_v0",
    val totalAvailableControllableMemoryKb: Long = 0L,
    val systemCoreObservedMemoryKb: Long = 0L,
    val userLockedObservedMemoryKb: Long = 0L,
    val foregroundObservedMemoryKb: Long = 0L,
    val leasePoolBudgetKb: Long = 0L,
    val leasePoolUsedMemoryKb: Long = 0L,
    val leasePoolOverBudget: Boolean = false,
    val quarantineObservedMemoryKb: Long = 0L,
    val prootReservedMemoryKb: Long = 0L,
    val safetyMarginKb: Long = 0L,
    val availableForElasticProotBeforeProotReservationsKb: Long = 0L,
    val reason: String = "waiting_for_runtime_process_memory_inputs"
) {
    fun toEnvText(prefix: String = "runtime_process_memory_budget_ledger"): String {
        return buildString {
            appendLine("${prefix}_mode=${mode.toSystemProcessLifecycleEnvValue()}")
            appendLine("${prefix}_available_controllable_kb=$totalAvailableControllableMemoryKb")
            appendLine("${prefix}_system_core_observed_kb=$systemCoreObservedMemoryKb")
            appendLine("${prefix}_user_locked_observed_kb=$userLockedObservedMemoryKb")
            appendLine("${prefix}_foreground_observed_kb=$foregroundObservedMemoryKb")
            appendLine("${prefix}_lease_pool_budget_kb=$leasePoolBudgetKb")
            appendLine("${prefix}_lease_pool_used_kb=$leasePoolUsedMemoryKb")
            appendLine("${prefix}_lease_pool_over_budget=$leasePoolOverBudget")
            appendLine("${prefix}_quarantine_observed_kb=$quarantineObservedMemoryKb")
            appendLine("${prefix}_proot_reserved_kb=$prootReservedMemoryKb")
            appendLine("${prefix}_safety_margin_kb=$safetyMarginKb")
            appendLine("${prefix}_elastic_proot_available_before_proot_reservations_kb=$availableForElasticProotBeforeProotReservationsKb")
            appendLine("${prefix}_reason=${reason.toSystemProcessLifecycleEnvValue()}")
        }
    }
}

object RuntimeProcessMemoryBudgetLedger {
    fun evaluate(
        pressure: RuntimePressureSnapshot,
        lifecycleReclaimPlan: RuntimeLifecycleReclaimPlanDryRunSnapshot,
        prootReservedMemoryKb: Long = 0L,
        safetyMarginKb: Long = 0L
    ): RuntimeProcessMemoryBudgetLedgerSnapshot {
        val availableKb = (pressure.hostMemAvailableKb ?: 0L).coerceAtLeast(0L)
        val systemCoreKb = lifecycleReclaimPlan.items
            .filter { it.layer == RuntimeLifecycleLayer.SYSTEM_CORE }
            .sumOf { it.rssKb.coerceAtLeast(0L) }
        val userLockedKb = lifecycleReclaimPlan.items
            .filter { it.layer == RuntimeLifecycleLayer.USER_LOCKED }
            .sumOf { it.rssKb.coerceAtLeast(0L) }
        val foregroundKb = lifecycleReclaimPlan.items
            .filter { it.layer == RuntimeLifecycleLayer.FOREGROUND_PRIORITY }
            .sumOf { it.rssKb.coerceAtLeast(0L) }
        val quarantineKb = lifecycleReclaimPlan.items
            .filter { it.layer == RuntimeLifecycleLayer.ANOMALY_POOL }
            .sumOf { it.rssKb.coerceAtLeast(0L) }
        val leaseBudgetKb = lifecycleReclaimPlan.leasePoolMemoryBudgetKb.coerceAtLeast(0L)
        val leaseUsedKb = lifecycleReclaimPlan.leasePoolRssKb.coerceAtLeast(0L)
        val availableForProotKb = (availableKb - leaseBudgetKb).coerceAtLeast(0L)
        return RuntimeProcessMemoryBudgetLedgerSnapshot(
            totalAvailableControllableMemoryKb = availableKb,
            systemCoreObservedMemoryKb = systemCoreKb,
            userLockedObservedMemoryKb = userLockedKb,
            foregroundObservedMemoryKb = foregroundKb,
            leasePoolBudgetKb = leaseBudgetKb,
            leasePoolUsedMemoryKb = leaseUsedKb,
            leasePoolOverBudget = lifecycleReclaimPlan.leasePoolOverBudget,
            quarantineObservedMemoryKb = quarantineKb,
            prootReservedMemoryKb = prootReservedMemoryKb.coerceAtLeast(0L),
            safetyMarginKb = safetyMarginKb.coerceAtLeast(0L),
            availableForElasticProotBeforeProotReservationsKb = availableForProotKb,
            reason = "availableKb=$availableKb,leasePoolBudgetKb=$leaseBudgetKb," +
                "leasePoolUsedKb=$leaseUsedKb,prootReservedKb=${prootReservedMemoryKb.coerceAtLeast(0L)}"
        )
    }
}

enum class RuntimeProotScaleOutLifecycleAction {
    KEEP_CURRENT_PROOT_POOL,
    RESERVE_BUDGET_AND_SPAWN_NEXT_PROOT,
    QUEUE_PENDING_MEMORY_OR_CAPACITY,
    HOLD_BY_LIFECYCLE_BUDGET
}

data class RuntimeProotScaleOutLifecycleDecision(
    val action: RuntimeProotScaleOutLifecycleAction,
    val scaleOutRequested: Boolean,
    val scaleOutApproved: Boolean,
    val reserveBudgetBeforeSpawn: Boolean,
    val spawnNewProot: Boolean,
    val queueInstead: Boolean,
    val defaultProotResident: Boolean,
    val targetProotIndex: Int,
    val targetRuntimeId: String,
    val reason: String
)

enum class RuntimeDefaultProotRecoveryAction {
    REBUILD_DEFAULT_PROOT,
    DO_NOT_REBUILD_DEFAULT_PROOT
}

data class RuntimeDefaultProotRecoveryDecision(
    val action: RuntimeDefaultProotRecoveryAction,
    val runtimeHostState: RuntimeHostLifecycleState,
    val defaultProotRunning: Boolean,
    val memoryAllowed: Boolean,
    val reason: String
)

data class RuntimeProcessLifecycleLease(
    val processId: String,
    val rssKb: Long,
    val remainingLeaseTimeMs: Long,
    val totalLeaseTimeMs: Long = 0L
)

data class RuntimeLeasePoolCleanupDecision(
    val overBudget: Boolean,
    val budgetKb: Long,
    val usedKb: Long,
    val cleanupCandidates: List<RuntimeProcessLifecycleLease>,
    val reason: String
)

enum class RuntimeUserLockedProcessAction {
    KEEP,
    WARN_NEAR_LIMIT,
    RESTART_OVER_LIMIT,
    QUARANTINE_OVER_LIMIT,
    REVIEW_SYSTEM_PROTECTION
}

data class RuntimeUserLockedProcess(
    val processId: String,
    val rssKb: Long,
    val declaredMemoryLimitKb: Long?,
    val consecutiveOverLimitCount: Int = 0,
    val systemProtectionThreat: Boolean = false
)

data class RuntimeUserLockedProcessDecision(
    val action: RuntimeUserLockedProcessAction,
    val processId: String,
    val memoryKb: Long,
    val declaredMemoryLimitKb: Long?,
    val consecutiveOverLimitCount: Int,
    val quarantineReason: String,
    val reason: String
)

data class RuntimeSystemProcessLifecycleDryRunSnapshot(
    val mode: String = "system_process_lifecycle_dry_run_v0",
    val enforcementMode: String = "dry_run_policy_review",
    val enforcementEnabled: Boolean = false,
    val managementModel: String =
        "system_core_user_locked_foreground_lease_pool_quarantine_proot_runtime_capacity",
    val executionBoundary: String =
        "dry_run_no_direct_start_stop_reclaim_restart_or_quarantine",
    val memoryLedger: RuntimeProcessMemoryBudgetLedgerSnapshot =
        RuntimeProcessMemoryBudgetLedgerSnapshot(),
    val runtimeHostState: RuntimeHostLifecycleState = RuntimeHostLifecycleState.STOPPED,
    val defaultProotRunning: Boolean = false,
    val defaultProotRecoveryAction: RuntimeDefaultProotRecoveryAction =
        RuntimeDefaultProotRecoveryAction.DO_NOT_REBUILD_DEFAULT_PROOT,
    val prootScaleOutAction: RuntimeProotScaleOutLifecycleAction =
        RuntimeProotScaleOutLifecycleAction.KEEP_CURRENT_PROOT_POOL,
    val prootScaleOutRequested: Boolean = false,
    val prootScaleOutApproved: Boolean = false,
    val prootScaleOutQueueInstead: Boolean = false,
    val leasePoolOverBudget: Boolean = false,
    val leasePoolCleanupCandidateCount: Int = 0,
    val userLockedLayerCount: Int = 0,
    val quarantineLayerCount: Int = 0,
    val processUnitManifestStatus: String = "not_loaded",
    val processUnitMatchedRootCount: Int = 0,
    val processUnitUserLockedCount: Int = 0,
    val processUnitUnlimitedMemoryCount: Int = 0,
    val processUnitExpectedMemoryLimitCount: Int = 0,
    val processUnitWaitConfirmRestartCount: Int = 0,
    val processUnitAutoRestartAllowedCount: Int = 0,
    val processUnitCoreRecoveryRequiredCount: Int = 0,
    val processUnitStates: List<RuntimeProcessUnitStateSnapshot> = emptyList(),
    val authorityMatrix: RuntimeLifecycleAuthorityMatrixSnapshot =
        RuntimeLifecycleAuthorityMatrixSnapshot(),
    val processResourceWatch: RuntimeProcessResourceWatchSnapshot =
        RuntimeProcessResourceWatchSnapshot(),
    val resourceEventLedger: RuntimeResourceEventLedgerSnapshot =
        RuntimeResourceEventLedgerSnapshot(),
    val lifecycleActionPlanner: RuntimeLifecycleActionPlannerSnapshot =
        RuntimeLifecycleActionPlannerSnapshot(),
    val lifecycleActionInbox: RuntimeLifecycleActionInboxSnapshot =
        RuntimeLifecycleActionInboxSnapshot(),
    val lifecycleDiagnosticReview: RuntimeLifecycleDiagnosticReviewSnapshot =
        RuntimeLifecycleDiagnosticReviewSnapshot(),
    val reason: String = "waiting_for_system_process_lifecycle_inputs"
) {
    fun summary(): String {
        return "mode=$mode host=$runtimeHostState proot1Running=$defaultProotRunning " +
            "proot1Recovery=$defaultProotRecoveryAction scaleOut=$prootScaleOutAction " +
            "leaseOver=$leasePoolOverBudget leaseCleanup=$leasePoolCleanupCandidateCount " +
            "userLocked=$userLockedLayerCount quarantine=$quarantineLayerCount " +
            "processUnits=$processUnitMatchedRootCount enforcement=$enforcementEnabled"
    }

    fun toEnvText(): String {
        return buildString {
            appendLine("system_process_lifecycle_mode=${mode.toSystemProcessLifecycleEnvValue()}")
            appendLine("system_process_lifecycle_enforcement_mode=${enforcementMode.toSystemProcessLifecycleEnvValue()}")
            appendLine("system_process_lifecycle_enforcement_enabled=$enforcementEnabled")
            appendLine("system_process_lifecycle_management_model=${managementModel.toSystemProcessLifecycleEnvValue()}")
            appendLine("system_process_lifecycle_execution_boundary=${executionBoundary.toSystemProcessLifecycleEnvValue()}")
            append(memoryLedger.toEnvText("system_process_lifecycle_memory_ledger"))
            appendLine("system_process_lifecycle_runtime_host_state=${runtimeHostState.name}")
            appendLine("system_process_lifecycle_default_proot_running=$defaultProotRunning")
            appendLine("system_process_lifecycle_default_proot_recovery_action=${defaultProotRecoveryAction.name}")
            appendLine("system_process_lifecycle_proot_scale_out_action=${prootScaleOutAction.name}")
            appendLine("system_process_lifecycle_proot_scale_out_requested=$prootScaleOutRequested")
            appendLine("system_process_lifecycle_proot_scale_out_approved=$prootScaleOutApproved")
            appendLine("system_process_lifecycle_proot_scale_out_queue_instead=$prootScaleOutQueueInstead")
            appendLine("system_process_lifecycle_lease_pool_over_budget=$leasePoolOverBudget")
            appendLine("system_process_lifecycle_lease_pool_cleanup_candidate_count=$leasePoolCleanupCandidateCount")
            appendLine("system_process_lifecycle_user_locked_layer_count=$userLockedLayerCount")
            appendLine("system_process_lifecycle_quarantine_layer_count=$quarantineLayerCount")
            appendLine("system_process_lifecycle_process_unit_manifest_status=${processUnitManifestStatus.toSystemProcessLifecycleEnvValue()}")
            appendLine("system_process_lifecycle_process_unit_matched_root_count=$processUnitMatchedRootCount")
            appendLine("system_process_lifecycle_process_unit_user_locked_count=$processUnitUserLockedCount")
            appendLine("system_process_lifecycle_process_unit_unlimited_memory_count=$processUnitUnlimitedMemoryCount")
            appendLine("system_process_lifecycle_process_unit_expected_memory_limit_count=$processUnitExpectedMemoryLimitCount")
            appendLine("system_process_lifecycle_process_unit_wait_confirm_restart_count=$processUnitWaitConfirmRestartCount")
            appendLine("system_process_lifecycle_process_unit_auto_restart_allowed_count=$processUnitAutoRestartAllowedCount")
            appendLine("system_process_lifecycle_process_unit_core_recovery_required_count=$processUnitCoreRecoveryRequiredCount")
            append(authorityMatrix.toEnvText())
            append(processResourceWatch.toEnvText())
            append(resourceEventLedger.toEnvText())
            append(lifecycleActionPlanner.toEnvText())
            append(lifecycleActionInbox.toEnvText())
            append(lifecycleDiagnosticReview.toEnvText())
            appendLine("system_process_lifecycle_reason=${reason.toSystemProcessLifecycleEnvValue()}")
        }
    }
}

object RuntimeSystemProcessLifecycleDryRun {
    fun evaluate(
        pressure: RuntimePressureSnapshot,
        lifecycleReclaimPlan: RuntimeLifecycleReclaimPlanDryRunSnapshot,
        lifecycleProotExpansionBudget: RuntimeLifecycleProotExpansionBudgetDryRunSnapshot,
        prootCapacityExecutor: RuntimeProotCapacityExecutorSnapshot,
        startQueuePlan: RuntimeStartQueuePlanDryRunSnapshot,
        backgroundRuntimes: List<BackgroundRuntimeRecord>,
        capacityPolicy: RuntimeProotCapacityExecutorPolicy,
        runtimeHostState: RuntimeHostLifecycleState,
        roots: List<RuntimeRootSnapshot> = emptyList(),
        processUnitManifest: RuntimeProcessUnitManifest = RuntimeProcessUnitManifest.default(),
        resourceEventLedgerFile: File? = null,
        lifecycleActionInboxFile: File? = null,
        now: Long = System.currentTimeMillis()
    ): RuntimeSystemProcessLifecycleDryRunSnapshot {
        val memoryLedger = RuntimeProcessMemoryBudgetLedger.evaluate(
            pressure = pressure,
            lifecycleReclaimPlan = lifecycleReclaimPlan,
            prootReservedMemoryKb = RuntimeProotMemoryAdmission.reservedMemoryKb(),
            safetyMarginKb = lifecycleProotExpansionBudget.safetyMarginKb
        )
        val defaultProotRunning = backgroundRuntimes.any {
            it.kind == BackgroundRuntimeKind.PROOT_CAPACITY_WORKER &&
                it.prootCapacityWorkerIndexForSystemProcessDryRun() == 1 &&
                it.isActiveRuntime()
        }
        val memorySignalsOk = pressure.hostAvailableLevel.ordinal <= RuntimePressureLevel.NORMAL.ordinal &&
            pressure.level.ordinal <= RuntimePressureLevel.NORMAL.ordinal
        val defaultRecoveryAdmission = RuntimeProotMemoryAdmission.evaluate(
            expansionRequested = !defaultProotRunning,
            hostAvailableKb = pressure.hostMemAvailableKb ?: 0L,
            peakTasks = lifecycleProotExpansionBudget.singleProotPeakTracees,
            defaultEstimatedTaskMemoryKb = lifecycleProotExpansionBudget.memoryWorkerRssKb.coerceAtLeast(1L),
            policy = RuntimeProotMemoryAdmissionPolicy(
                baseProotMemoryKb = capacityPolicy.baseProotMemoryKb,
                estimatedTaskMemoryKb = capacityPolicy.estimatedTaskMemoryKb,
                safetyMarginKb = lifecycleProotExpansionBudget.safetyMarginKb
            ),
            memorySignalsOk = memorySignalsOk,
            globalBudgetLedger = memoryLedger
        )
        val defaultRecovery = evaluateDefaultProotRecovery(
            runtimeHostState = runtimeHostState,
            defaultProotRunning = defaultProotRunning,
            memoryAdmission = defaultRecoveryAdmission
        )
        val scaleOut = evaluateProotScaleOut(
            lifecycleBudget = lifecycleProotExpansionBudget,
            executor = prootCapacityExecutor
        )
        val leasePool = evaluateLeasePool(
            memoryLedger = memoryLedger,
            leases = lifecycleReclaimPlan.items
                .filter { it.layer == RuntimeLifecycleLayer.LEASE_POOL }
                .map {
                    RuntimeProcessLifecycleLease(
                        processId = it.workloadId,
                        rssKb = it.rssKb,
                        remainingLeaseTimeMs = it.leaseRemainingMs,
                        totalLeaseTimeMs = it.leaseMaxTotalMs
                    )
                }
        )
        val processUnitStates = evaluateProcessUnits(roots)
        val authorityMatrix = RuntimeLifecycleAuthorityMatrix.evaluate(
            roots = roots,
            lifecycleReclaimPlan = lifecycleReclaimPlan
        )
        val processResourceWatch = RuntimeProcessResourceWatch.evaluate(
            roots = roots,
            authorityMatrix = authorityMatrix
        )
        val resourceEventLedger = RuntimeResourceEventLedgerStore.record(
            file = resourceEventLedgerFile,
            resourceWatch = processResourceWatch,
            roots = roots,
            now = now
        )
        val lifecycleActionPlanner = RuntimeLifecycleActionPlanner.evaluate(
            roots = roots,
            authorityMatrix = authorityMatrix,
            resourceWatch = processResourceWatch,
            resourceEventLedger = resourceEventLedger
        )
        val lifecycleActionInbox = RuntimeLifecycleActionInboxStore.record(
            file = lifecycleActionInboxFile,
            planner = lifecycleActionPlanner,
            now = now
        )
        val lifecycleDiagnosticReview = RuntimeLifecycleDiagnosticReview.evaluate(
            roots = roots,
            manifest = processUnitManifest,
            authorityMatrix = authorityMatrix,
            resourceWatch = processResourceWatch,
            resourceEventLedger = resourceEventLedger,
            actionPlanner = lifecycleActionPlanner,
            actionInbox = lifecycleActionInbox
        )
        return RuntimeSystemProcessLifecycleDryRunSnapshot(
            memoryLedger = memoryLedger,
            runtimeHostState = runtimeHostState,
            defaultProotRunning = defaultProotRunning,
            defaultProotRecoveryAction = defaultRecovery.action,
            prootScaleOutAction = scaleOut.action,
            prootScaleOutRequested = scaleOut.scaleOutRequested,
            prootScaleOutApproved = scaleOut.scaleOutApproved,
            prootScaleOutQueueInstead = scaleOut.queueInstead || startQueuePlan.dryRunBacklogCount > 0,
            leasePoolOverBudget = leasePool.overBudget,
            leasePoolCleanupCandidateCount = leasePool.cleanupCandidates.size,
            userLockedLayerCount = lifecycleReclaimPlan.userLockedLayerCount,
            quarantineLayerCount = lifecycleReclaimPlan.quarantineTierCount,
            processUnitManifestStatus = processUnitManifest.loadStatus,
            processUnitMatchedRootCount = roots.count { it.processUnitId != null },
            processUnitUserLockedCount =
                roots.count { it.processUnitTier == RuntimeProcessUnitTier.USER_LOCKED },
            processUnitUnlimitedMemoryCount = roots.count { it.processUnitUnlimitedMemory },
            processUnitExpectedMemoryLimitCount =
                roots.count { it.processUnitExpectedMemoryLimitKb != null },
            processUnitWaitConfirmRestartCount =
                processUnitStates.count {
                    it.observedState == RuntimeProcessUnitObservationState.WAIT_CONFIRM_RESTART
                },
            processUnitAutoRestartAllowedCount =
                processUnitStates.count {
                    it.observedState == RuntimeProcessUnitObservationState.AUTO_RESTART_ALLOWED
                },
            processUnitCoreRecoveryRequiredCount =
                processUnitStates.count {
                    it.observedState == RuntimeProcessUnitObservationState.CORE_RECOVERY_REQUIRED
                },
            processUnitStates = processUnitStates,
            authorityMatrix = authorityMatrix,
            processResourceWatch = processResourceWatch,
            resourceEventLedger = resourceEventLedger,
            lifecycleActionPlanner = lifecycleActionPlanner,
            lifecycleActionInbox = lifecycleActionInbox,
            lifecycleDiagnosticReview = lifecycleDiagnosticReview,
            reason = "now=$now,defaultProot=${defaultRecovery.reason},scaleOut=${scaleOut.reason}," +
                "leasePool=${leasePool.reason},processUnits=${processUnitStates.size}," +
                "authorityMatrix=${authorityMatrix.entryCount},resourceWatch=${processResourceWatch.unitResourceCount}," +
                "resourceLedger=${resourceEventLedger.resourceEpisodeCount}," +
                "actionPlanner=${lifecycleActionPlanner.actionPlanCount}," +
                "actionInbox=${lifecycleActionInbox.openActionCount}," +
                "diagnosticReview=${lifecycleDiagnosticReview.scenarioReviewCount}"
        )
    }

    fun evaluateProotScaleOut(
        lifecycleBudget: RuntimeLifecycleProotExpansionBudgetDryRunSnapshot,
        executor: RuntimeProotCapacityExecutorSnapshot
    ): RuntimeProotScaleOutLifecycleDecision {
        val requested = lifecycleBudget.expansionRequested
        val approved = lifecycleBudget.lifecycleApprovesRequestedAction &&
            lifecycleBudget.canReserveForSecondProot
        val launchReady = executor.launchEligible && approved
        val action = when {
            !requested -> RuntimeProotScaleOutLifecycleAction.KEEP_CURRENT_PROOT_POOL
            launchReady -> RuntimeProotScaleOutLifecycleAction.RESERVE_BUDGET_AND_SPAWN_NEXT_PROOT
            lifecycleBudget.state == RuntimeLifecycleProotExpansionBudgetState.QUEUE_FOR_MEMORY ||
                lifecycleBudget.state == RuntimeLifecycleProotExpansionBudgetState.RECLAIM_REVIEW ->
                RuntimeProotScaleOutLifecycleAction.QUEUE_PENDING_MEMORY_OR_CAPACITY
            lifecycleBudget.lifecycleApprovesRequestedAction || executor.maxProotsReached ->
                RuntimeProotScaleOutLifecycleAction.QUEUE_PENDING_MEMORY_OR_CAPACITY
            else -> RuntimeProotScaleOutLifecycleAction.HOLD_BY_LIFECYCLE_BUDGET
        }
        return RuntimeProotScaleOutLifecycleDecision(
            action = action,
            scaleOutRequested = requested,
            scaleOutApproved = approved,
            reserveBudgetBeforeSpawn =
                action == RuntimeProotScaleOutLifecycleAction.RESERVE_BUDGET_AND_SPAWN_NEXT_PROOT,
            spawnNewProot =
                action == RuntimeProotScaleOutLifecycleAction.RESERVE_BUDGET_AND_SPAWN_NEXT_PROOT,
            queueInstead = requested &&
                action != RuntimeProotScaleOutLifecycleAction.RESERVE_BUDGET_AND_SPAWN_NEXT_PROOT,
            defaultProotResident = true,
            targetProotIndex = executor.nextCapacityRuntimeIndex,
            targetRuntimeId = executor.registeredRuntimeTargetId,
            reason = "requested=$requested,approved=$approved,launchReady=$launchReady," +
                "executor=${executor.state.name},budget=${lifecycleBudget.state.name}"
        )
    }

    fun evaluateDefaultProotRecovery(
        runtimeHostState: RuntimeHostLifecycleState,
        defaultProotRunning: Boolean,
        memoryAdmission: RuntimeProotMemoryAdmissionDecision
    ): RuntimeDefaultProotRecoveryDecision {
        val action = when {
            defaultProotRunning ->
                RuntimeDefaultProotRecoveryAction.DO_NOT_REBUILD_DEFAULT_PROOT
            runtimeHostState == RuntimeHostLifecycleState.RUNNING && memoryAdmission.canReserve ->
                RuntimeDefaultProotRecoveryAction.REBUILD_DEFAULT_PROOT
            else ->
                RuntimeDefaultProotRecoveryAction.DO_NOT_REBUILD_DEFAULT_PROOT
        }
        val reason = when {
            defaultProotRunning -> "default_proot_1_already_running"
            runtimeHostState != RuntimeHostLifecycleState.RUNNING ->
                "runtime_host_${runtimeHostState.name.lowercase()}_no_default_proot_rebuild"
            !memoryAdmission.canReserve -> "memory_admission_${memoryAdmission.reason}"
            else -> "runtime_host_running_and_memory_budget_available"
        }
        return RuntimeDefaultProotRecoveryDecision(
            action = action,
            runtimeHostState = runtimeHostState,
            defaultProotRunning = defaultProotRunning,
            memoryAllowed = memoryAdmission.canReserve,
            reason = reason
        )
    }

    fun evaluateLeasePool(
        memoryLedger: RuntimeProcessMemoryBudgetLedgerSnapshot,
        leases: List<RuntimeProcessLifecycleLease>
    ): RuntimeLeasePoolCleanupDecision {
        val usedKb = if (memoryLedger.leasePoolUsedMemoryKb > 0L) {
            memoryLedger.leasePoolUsedMemoryKb
        } else {
            leases.sumOf { it.rssKb.coerceAtLeast(0L) }
        }
        val budgetKb = memoryLedger.leasePoolBudgetKb.coerceAtLeast(0L)
        val overBudget = memoryLedger.leasePoolOverBudget || (budgetKb > 0L && usedKb > budgetKb)
        val candidates = if (overBudget) {
            leases.sortedWith(
                compareBy<RuntimeProcessLifecycleLease> { it.remainingLeaseTimeMs.coerceAtLeast(0L) }
                    .thenByDescending { it.rssKb }
                    .thenBy { it.processId }
            )
        } else {
            emptyList()
        }
        return RuntimeLeasePoolCleanupDecision(
            overBudget = overBudget,
            budgetKb = budgetKb,
            usedKb = usedKb,
            cleanupCandidates = candidates,
            reason = if (overBudget) {
                "lease_pool_over_budget_cleanup_shortest_remaining_lease_first"
            } else {
                "lease_pool_within_budget"
            }
        )
    }

    fun evaluateUserLockedProcess(
        process: RuntimeUserLockedProcess
    ): RuntimeUserLockedProcessDecision {
        val memoryKb = process.rssKb.coerceAtLeast(0L)
        val limitKb = process.declaredMemoryLimitKb?.coerceAtLeast(1L)
        if (process.systemProtectionThreat) {
            return RuntimeUserLockedProcessDecision(
                action = RuntimeUserLockedProcessAction.REVIEW_SYSTEM_PROTECTION,
                processId = process.processId,
                memoryKb = memoryKb,
                declaredMemoryLimitKb = limitKb,
                consecutiveOverLimitCount = process.consecutiveOverLimitCount.coerceAtLeast(0),
                quarantineReason = "none",
                reason = "user_locked_process_threatens_system_protection"
            )
        }
        if (limitKb == null) {
            return RuntimeUserLockedProcessDecision(
                action = RuntimeUserLockedProcessAction.KEEP,
                processId = process.processId,
                memoryKb = memoryKb,
                declaredMemoryLimitKb = null,
                consecutiveOverLimitCount = process.consecutiveOverLimitCount.coerceAtLeast(0),
                quarantineReason = "none",
                reason = "user_locked_unlimited_kept_under_ordinary_budget_pressure"
            )
        }
        val overLimit = memoryKb > limitKb
        val nearLimit = !overLimit && memoryKb * 100L >= limitKb * 90L
        val nextOverLimitCount = if (overLimit) {
            process.consecutiveOverLimitCount.coerceAtLeast(0) + 1
        } else {
            0
        }
        val action = when {
            overLimit && nextOverLimitCount >= 3 -> RuntimeUserLockedProcessAction.QUARANTINE_OVER_LIMIT
            overLimit -> RuntimeUserLockedProcessAction.RESTART_OVER_LIMIT
            nearLimit -> RuntimeUserLockedProcessAction.WARN_NEAR_LIMIT
            else -> RuntimeUserLockedProcessAction.KEEP
        }
        val quarantineReason = if (action == RuntimeUserLockedProcessAction.QUARANTINE_OVER_LIMIT) {
            "user_locked_memory_overlimit_count=$nextOverLimitCount,memoryKb=$memoryKb,limitKb=$limitKb"
        } else {
            "none"
        }
        val reason = when (action) {
            RuntimeUserLockedProcessAction.QUARANTINE_OVER_LIMIT ->
                "declared_memory_limit_exceeded_three_times"
            RuntimeUserLockedProcessAction.RESTART_OVER_LIMIT ->
                "declared_memory_limit_exceeded_restart_allowed"
            RuntimeUserLockedProcessAction.WARN_NEAR_LIMIT ->
                "declared_memory_limit_near_warning"
            RuntimeUserLockedProcessAction.KEEP ->
                "user_locked_within_declared_memory_limit"
            RuntimeUserLockedProcessAction.REVIEW_SYSTEM_PROTECTION ->
                "user_locked_process_threatens_system_protection"
        }
        return RuntimeUserLockedProcessDecision(
            action = action,
            processId = process.processId,
            memoryKb = memoryKb,
            declaredMemoryLimitKb = limitKb,
            consecutiveOverLimitCount = nextOverLimitCount,
            quarantineReason = quarantineReason,
            reason = reason
        )
    }

    fun evaluateProcessUnits(
        roots: List<RuntimeRootSnapshot>
    ): List<RuntimeProcessUnitStateSnapshot> {
        return roots
            .filter { it.processUnitId != null }
            .map { root ->
                val reconciliation = RuntimeProcessStopReconciliation.evaluate(root)
                root.copy(processUnitObservedState = reconciliation.observedState).toProcessUnitStateSnapshot(
                    state = reconciliation.observedState,
                    autoRestartAllowed = reconciliation.autoRecoveryAllowed,
                    reason = reconciliation.reason
                )
            }
    }
}

private fun RuntimeRootSnapshot.toProcessUnitStateSnapshot(
    state: RuntimeProcessUnitObservationState,
    autoRestartAllowed: Boolean,
    reason: String
): RuntimeProcessUnitStateSnapshot {
    return RuntimeProcessUnitStateSnapshot(
        rootKey = ownershipKey,
        unitId = processUnitId ?: ownershipKey,
        displayName = processUnitDisplayName ?: title,
        tier = processUnitTier ?: RuntimeProcessUnitTier.UNMANAGED,
        matchSource = processUnitMatchSource,
        matchConfidence = processUnitMatchConfidence,
        matchState = processUnitMatchState,
        matchedPid = processUnitMatchedPid,
        matchedPgid = processUnitMatchedPgid,
        matchedSid = processUnitMatchedSid,
        conflictUnitIds = processUnitConflictUnitIds,
        fallbackReason = processUnitFallbackReason ?: "none",
        observedState = state,
        manualKillPolicy = processUnitManualKillPolicy ?: RuntimeProcessUnitManualKillPolicy.RESPECT_USER_KILL,
        running = isRunning,
        autoRestartAllowed = autoRestartAllowed,
        expectedMemoryLimitKb = processUnitExpectedMemoryLimitKb,
        unlimitedMemory = processUnitUnlimitedMemory,
        reason = reason
    )
}

private fun BackgroundRuntimeRecord.prootCapacityWorkerIndexForSystemProcessDryRun(): Int {
    return id.substringAfterLast("-proot-capacity-worker-", "")
        .toIntOrNull()
        ?.takeIf { it > 0 }
        ?: Int.MAX_VALUE
}

private fun String?.toSystemProcessLifecycleEnvValue(): String {
    if (this.isNullOrBlank()) return "none"
    return trim()
        .replace(Regex("[\\r\\n\\t ]+"), "_")
        .replace(Regex("[^A-Za-z0-9._:@/+,-]"), "_")
        .take(240)
}
