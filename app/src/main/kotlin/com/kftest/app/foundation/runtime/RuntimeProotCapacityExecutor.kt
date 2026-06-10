package com.kftest.app.foundation.runtime

import com.kftest.app.foundation.service.BackgroundRuntimeKind
import com.kftest.app.foundation.service.BackgroundRuntimeRecord
import com.kftest.app.foundation.service.isActiveRuntime

enum class RuntimeProotCapacityExecutorState {
    IDLE,
    QUEUE_ON_SINGLE_PROOT_REVIEW,
    APPROVED_WAITING_FOR_POLICY_ENABLE,
    APPROVED_WAITING_FOR_LAUNCHER_BINDING,
    APPROVED_READY_FOR_ANDROID_LAUNCH,
    DOWNSCALE_READY_FOR_ANDROID_DOWNLINE,
    DOWNSCALE_WAITING_FOR_TARGET_BINDING,
    BLOCKED_BY_LIFECYCLE_BUDGET
}

enum class RuntimeProotCapacityExecutorRecommendation {
    KEEP_CURRENT_POOL,
    KEEP_QUEUE_ON_SINGLE_PROOT,
    ENABLE_CAPACITY_EXECUTOR_POLICY_BEFORE_EXECUTION,
    BIND_NEXT_PROOT_LAUNCHER_BEFORE_EXECUTION,
    START_BOUND_NEXT_PROOT_RUNTIME,
    DOWNLINE_BOUND_PROOT_RUNTIME,
    BIND_PROOT_DOWNLINE_TARGET_BEFORE_EXECUTION,
    HOLD_CAPACITY_ACTION
}

data class RuntimeProotCapacityExecutorSnapshot(
    val mode: String = "proot_capacity_executor_android_v0",
    val executionOwner: String = "android_control_plane",
    val executionEnabled: Boolean = false,
    val state: RuntimeProotCapacityExecutorState = RuntimeProotCapacityExecutorState.IDLE,
    val recommendation: RuntimeProotCapacityExecutorRecommendation =
        RuntimeProotCapacityExecutorRecommendation.KEEP_CURRENT_POOL,
    val requestedAction: String = "NONE",
    val lifecycleBudgetApproved: Boolean = false,
    val lifecycleBudgetState: RuntimeLifecycleProotExpansionBudgetState =
        RuntimeLifecycleProotExpansionBudgetState.NOT_REQUESTED,
    val policyPath: String = RuntimeProotCapacityExecutorPolicy().path,
    val policyLoadStatus: String = "workspace_missing",
    val policyEnabled: Boolean = false,
    val launchEligible: Boolean = false,
    val downlineEligible: Boolean = false,
    val targetBindingStatus: String = "not_requested",
    val singleProotQueueOwner: String = "android_start_queue_plan",
    val singleProotQueueMode: String = "logical_admission_queue_no_new_proot",
    val singleProotQueueStatus: String = "not_requested",
    val launcherBindingRequired: Boolean = false,
    val downlineTargetBindingRequired: Boolean = false,
    val configuredRuntimeTargetId: String = "none",
    val registeredRuntimeTargetId: String = "none",
    val registeredRuntimeTargetKind: String = "none",
    val targetBindingMode: String = "none",
    val launcherImplementationStatus: String = "not_requested",
    val activeCapacityWorkerCount: Int = 0,
    val registeredCapacityWorkerCount: Int = 0,
    val nextCapacityRuntimeIndex: Int = 0,
    val desiredCapacityWorkerCount: Int = 1,
    val maxProots: Int = 3,
    val maxProotsReached: Boolean = false,
    val memoryReservationRequiredKb: Long = 0L,
    val actualSecondProotStartCount: Int = 0,
    val actualNextProotStartCount: Int = 0,
    val actualProotDownlineCount: Int = 0,
    val actualQueueCreationCount: Int = 0,
    val reason: String = "waiting_for_capacity_request"
) {
    fun toEnvText(): String {
        val actuator = RuntimeProotCapacityActuator.executionSnapshot()
        val effectiveNextProotStartCount = maxOf(
            maxOf(actualNextProotStartCount, actualSecondProotStartCount),
            actuator.nextProotStartRequestCount
        )
        val effectiveProotDownlineCount = maxOf(
            actualProotDownlineCount,
            actuator.prootDownlineRequestCount
        )
        val effectiveQueueCreationCount = maxOf(
            actualQueueCreationCount,
            actuator.queueCreationRequestCount
        )
        return buildString {
            appendLine("proot_capacity_executor_mode=${mode.toProotCapacityExecutorEnvValue()}")
            appendLine("proot_capacity_executor_execution_owner=${executionOwner.toProotCapacityExecutorEnvValue()}")
            appendLine("proot_capacity_executor_execution_enabled=$executionEnabled")
            appendLine("proot_capacity_executor_state=${state.name}")
            appendLine("proot_capacity_executor_recommendation=${recommendation.name}")
            appendLine("proot_capacity_executor_requested_action=${requestedAction.toProotCapacityExecutorEnvValue()}")
            appendLine("proot_capacity_executor_lifecycle_budget_approved=$lifecycleBudgetApproved")
            appendLine("proot_capacity_executor_lifecycle_budget_state=${lifecycleBudgetState.name}")
            appendLine("proot_capacity_executor_policy_path=${policyPath.toProotCapacityExecutorEnvValue()}")
            appendLine("proot_capacity_executor_policy_load_status=${policyLoadStatus.toProotCapacityExecutorEnvValue()}")
            appendLine("proot_capacity_executor_policy_enabled=$policyEnabled")
            appendLine("proot_capacity_executor_launch_eligible=$launchEligible")
            appendLine("proot_capacity_executor_downline_eligible=$downlineEligible")
            appendLine("proot_capacity_executor_target_binding_status=${targetBindingStatus.toProotCapacityExecutorEnvValue()}")
            appendLine("proot_capacity_executor_single_proot_queue_owner=${singleProotQueueOwner.toProotCapacityExecutorEnvValue()}")
            appendLine("proot_capacity_executor_single_proot_queue_mode=${singleProotQueueMode.toProotCapacityExecutorEnvValue()}")
            appendLine("proot_capacity_executor_single_proot_queue_status=${singleProotQueueStatus.toProotCapacityExecutorEnvValue()}")
            appendLine("proot_capacity_executor_launcher_binding_required=$launcherBindingRequired")
            appendLine("proot_capacity_executor_downline_target_binding_required=$downlineTargetBindingRequired")
            appendLine("proot_capacity_executor_configured_runtime_target_id=${configuredRuntimeTargetId.toProotCapacityExecutorEnvValue()}")
            appendLine("proot_capacity_executor_registered_runtime_target_id=${registeredRuntimeTargetId.toProotCapacityExecutorEnvValue()}")
            appendLine("proot_capacity_executor_registered_runtime_target_kind=${registeredRuntimeTargetKind.toProotCapacityExecutorEnvValue()}")
            appendLine("proot_capacity_executor_target_binding_mode=${targetBindingMode.toProotCapacityExecutorEnvValue()}")
            appendLine("proot_capacity_executor_launcher_implementation_status=${launcherImplementationStatus.toProotCapacityExecutorEnvValue()}")
            appendLine("proot_capacity_executor_active_capacity_worker_count=$activeCapacityWorkerCount")
            appendLine("proot_capacity_executor_registered_capacity_worker_count=$registeredCapacityWorkerCount")
            appendLine("proot_capacity_executor_next_capacity_runtime_index=$nextCapacityRuntimeIndex")
            appendLine("proot_capacity_executor_desired_capacity_worker_count=$desiredCapacityWorkerCount")
            appendLine("proot_capacity_executor_max_proots=$maxProots")
            appendLine("proot_capacity_executor_max_proots_reached=$maxProotsReached")
            appendLine("proot_capacity_executor_memory_reservation_required_kb=$memoryReservationRequiredKb")
            appendLine("proot_capacity_executor_actual_next_proot_start_count=$effectiveNextProotStartCount")
            appendLine("proot_capacity_executor_actual_second_proot_start_count=$effectiveNextProotStartCount")
            appendLine("proot_capacity_executor_actual_proot_downline_count=$effectiveProotDownlineCount")
            appendLine("proot_capacity_executor_actual_queue_creation_count=$effectiveQueueCreationCount")
            appendLine("proot_capacity_executor_reason=${reason.toProotCapacityExecutorEnvValue()}")
            appendLine("proot_capacity_executor_boundary=android_control_plane_no_ubuntu_direct_control_no_unbound_proot_start")
        }
    }
}

object RuntimeProotCapacityExecutor {
    fun evaluate(
        lifecycleBudget: RuntimeLifecycleProotExpansionBudgetDryRunSnapshot,
        bindingPolicy: RuntimeProotCapacityExecutorPolicy = RuntimeProotCapacityExecutorPolicy(),
        backgroundRuntimes: List<BackgroundRuntimeRecord> = emptyList()
    ): RuntimeProotCapacityExecutorSnapshot {
        val requestedAction = lifecycleBudget.requestedAction.ifBlank { "NONE" }
        val approved = lifecycleBudget.lifecycleApprovesRequestedAction
        val maxProots = bindingPolicy.maxProots.coerceAtLeast(1)
        val capacityWorkers = backgroundRuntimes
            .filter { it.kind == BackgroundRuntimeKind.PROOT_CAPACITY_WORKER }
            .sortedWith(
                compareBy<BackgroundRuntimeRecord> { it.prootCapacityWorkerIndex() }
                    .thenBy { it.createdAt }
                    .thenBy { it.id }
            )
        val eligibleCapacityWorkers = capacityWorkers
            .filter { it.prootCapacityWorkerIndex() in 1..maxProots }
        val activeCapacityWorkerCount = eligibleCapacityWorkers.count { it.isActiveRuntime() }
        val rawDesiredCapacityWorkerCount = desiredCapacityWorkerCountFor(
            currentTasks = lifecycleBudget.currentTracees,
            peakTasks = lifecycleBudget.singleProotPeakTracees,
            scaleOutThreshold = lifecycleBudget.secondProotTriggerTracees
        )
        val desiredCapacityWorkerCount = rawDesiredCapacityWorkerCount.coerceIn(1, maxProots)
        val expansionStillNeeded = lifecycleBudget.expansionRequested &&
            activeCapacityWorkerCount < desiredCapacityWorkerCount
        val maxProotsReached = activeCapacityWorkerCount >= maxProots
        val expansionMustQueueAtMax = lifecycleBudget.expansionRequested &&
            rawDesiredCapacityWorkerCount > maxProots &&
            maxProotsReached
        val configuredTargetIds = bindingPolicy.expansionRuntimeIds
        fun isAutoTarget(targetId: String): Boolean {
            val normalized = targetId.trim()
            return normalized.isBlank() ||
                normalized.equals("auto", ignoreCase = true) ||
                normalized.equals("auto_registered_proot_capacity_worker", ignoreCase = true)
        }
        val usesAutoBinding = configuredTargetIds.isEmpty() || configuredTargetIds.any(::isAutoTarget)
        val configuredTargetId = when {
            usesAutoBinding -> "auto_registered_proot_capacity_worker"
            else -> configuredTargetIds.joinToString(",")
        }
        fun resolveCapacityTarget(targetId: String): BackgroundRuntimeRecord? {
            val normalized = targetId.trim()
            return if (isAutoTarget(normalized)) {
                eligibleCapacityWorkers
                    .filter { it.prootCapacityWorkerIndex() in 2..maxProots }
                    .firstOrNull { !it.isActiveRuntime() }
                    ?: eligibleCapacityWorkers
                        .filter { it.prootCapacityWorkerIndex() in 2..maxProots }
                        .firstOrNull()
            } else {
                eligibleCapacityWorkers.firstOrNull { it.id == normalized }
            }?.takeIf { it.kind == BackgroundRuntimeKind.PROOT_CAPACITY_WORKER }
        }
        val scaleOutCapacityWorkers = eligibleCapacityWorkers
            .filter { it.prootCapacityWorkerIndex() in 2..maxProots }
        val expansionCandidates = if (usesAutoBinding) {
            scaleOutCapacityWorkers
        } else {
            configuredTargetIds.mapNotNull(::resolveCapacityTarget)
        }
        val boundRuntime = expansionCandidates.firstOrNull { !it.isActiveRuntime() }
            ?: expansionCandidates.firstOrNull()
        val hasDedicatedCapacityTarget = boundRuntime?.kind == BackgroundRuntimeKind.PROOT_CAPACITY_WORKER &&
            boundRuntime.prootCapacityWorkerIndex() in 2..maxProots
        val nextCapacityRuntimeIndex = boundRuntime?.let { target ->
            target.prootCapacityWorkerIndex()
        } ?: 0
        val downlineTargetIds = bindingPolicy.downlineRuntimeIds.takeIf { it.isNotEmpty() }
            ?: expansionCandidates.map { it.id }.toSet()
        val hasDedicatedDownlineTarget = downlineTargetIds
            .mapNotNull(::resolveCapacityTarget)
            .any { it.prootCapacityWorkerIndex() > 1 && it.isActiveRuntime() }
        val expansionBindingStatus = when {
            bindingPolicy.policyUnavailable() -> "policy_${bindingPolicy.loadStatus}"
            expansionMustQueueAtMax -> "max_proots_reached_queue_required"
            lifecycleBudget.expansionRequested && !expansionStillNeeded ->
                "desired_proot_capacity_already_active"
            !bindingPolicy.enabled && hasDedicatedCapacityTarget ->
                "policy_disabled_bound_next_proot_runtime_ready"
            !bindingPolicy.enabled -> "policy_disabled_no_ready_next_proot_runtime"
            !bindingPolicy.hasCapacityBinding && !usesAutoBinding -> "missing_capacity_runtime_id"
            boundRuntime == null -> "registered_runtime_not_found"
            !hasDedicatedCapacityTarget -> "runtime_kind_not_dedicated_proot_capacity_worker"
            boundRuntime.isActiveRuntime() -> "all_bound_capacity_workers_already_active"
            else -> "bound_next_proot_runtime_ready"
        }
        val state = when {
            !lifecycleBudget.capacityReviewNeeded ->
                RuntimeProotCapacityExecutorState.IDLE
            !approved ->
                RuntimeProotCapacityExecutorState.BLOCKED_BY_LIFECYCLE_BUDGET
            lifecycleBudget.queueRequested || expansionMustQueueAtMax ->
                RuntimeProotCapacityExecutorState.QUEUE_ON_SINGLE_PROOT_REVIEW
            lifecycleBudget.expansionRequested && !expansionStillNeeded ->
                RuntimeProotCapacityExecutorState.IDLE
            (lifecycleBudget.expansionRequested ||
                lifecycleBudget.downscaleRequested ||
                lifecycleBudget.downlineRequested) && !bindingPolicy.enabled ->
                RuntimeProotCapacityExecutorState.APPROVED_WAITING_FOR_POLICY_ENABLE
            lifecycleBudget.expansionRequested && expansionStillNeeded && hasDedicatedCapacityTarget &&
                boundRuntime?.isActiveRuntime() == false ->
                RuntimeProotCapacityExecutorState.APPROVED_READY_FOR_ANDROID_LAUNCH
            lifecycleBudget.expansionRequested && expansionStillNeeded ->
                RuntimeProotCapacityExecutorState.APPROVED_WAITING_FOR_LAUNCHER_BINDING
            (lifecycleBudget.downscaleRequested || lifecycleBudget.downlineRequested) &&
                hasDedicatedDownlineTarget ->
                RuntimeProotCapacityExecutorState.DOWNSCALE_READY_FOR_ANDROID_DOWNLINE
            lifecycleBudget.downscaleRequested || lifecycleBudget.downlineRequested ->
                RuntimeProotCapacityExecutorState.DOWNSCALE_WAITING_FOR_TARGET_BINDING
            else ->
                RuntimeProotCapacityExecutorState.IDLE
        }
        val recommendation = when (state) {
            RuntimeProotCapacityExecutorState.IDLE ->
                RuntimeProotCapacityExecutorRecommendation.KEEP_CURRENT_POOL
            RuntimeProotCapacityExecutorState.QUEUE_ON_SINGLE_PROOT_REVIEW ->
                RuntimeProotCapacityExecutorRecommendation.KEEP_QUEUE_ON_SINGLE_PROOT
            RuntimeProotCapacityExecutorState.APPROVED_WAITING_FOR_POLICY_ENABLE ->
                RuntimeProotCapacityExecutorRecommendation.ENABLE_CAPACITY_EXECUTOR_POLICY_BEFORE_EXECUTION
            RuntimeProotCapacityExecutorState.APPROVED_WAITING_FOR_LAUNCHER_BINDING ->
                RuntimeProotCapacityExecutorRecommendation.BIND_NEXT_PROOT_LAUNCHER_BEFORE_EXECUTION
            RuntimeProotCapacityExecutorState.APPROVED_READY_FOR_ANDROID_LAUNCH ->
                RuntimeProotCapacityExecutorRecommendation.START_BOUND_NEXT_PROOT_RUNTIME
            RuntimeProotCapacityExecutorState.DOWNSCALE_READY_FOR_ANDROID_DOWNLINE ->
                RuntimeProotCapacityExecutorRecommendation.DOWNLINE_BOUND_PROOT_RUNTIME
            RuntimeProotCapacityExecutorState.DOWNSCALE_WAITING_FOR_TARGET_BINDING ->
                RuntimeProotCapacityExecutorRecommendation.BIND_PROOT_DOWNLINE_TARGET_BEFORE_EXECUTION
            RuntimeProotCapacityExecutorState.BLOCKED_BY_LIFECYCLE_BUDGET ->
                RuntimeProotCapacityExecutorRecommendation.HOLD_CAPACITY_ACTION
        }
        val targetBindingStatus = when (state) {
            RuntimeProotCapacityExecutorState.APPROVED_WAITING_FOR_POLICY_ENABLE ->
                expansionBindingStatus
            RuntimeProotCapacityExecutorState.APPROVED_WAITING_FOR_LAUNCHER_BINDING ->
                expansionBindingStatus
            RuntimeProotCapacityExecutorState.APPROVED_READY_FOR_ANDROID_LAUNCH ->
                "bound_next_proot_runtime_ready"
            RuntimeProotCapacityExecutorState.DOWNSCALE_READY_FOR_ANDROID_DOWNLINE ->
                "bound_proot_downline_runtime_ready"
            RuntimeProotCapacityExecutorState.DOWNSCALE_WAITING_FOR_TARGET_BINDING ->
                "missing_downline_runtime_target_binding"
            RuntimeProotCapacityExecutorState.QUEUE_ON_SINGLE_PROOT_REVIEW ->
                "single_proot_queue_owned_by_android_start_queue_plan"
            RuntimeProotCapacityExecutorState.BLOCKED_BY_LIFECYCLE_BUDGET ->
                "blocked_by_lifecycle_budget"
            RuntimeProotCapacityExecutorState.IDLE ->
                "not_requested"
        }
        val reason = when (state) {
            RuntimeProotCapacityExecutorState.IDLE -> when {
                lifecycleBudget.expansionRequested && !expansionStillNeeded ->
                    "requested_$requestedAction already satisfied by active_proots=$activeCapacityWorkerCount desired=$desiredCapacityWorkerCount"
                else -> "no_capacity_action_requested"
            }
            RuntimeProotCapacityExecutorState.BLOCKED_BY_LIFECYCLE_BUDGET ->
                "lifecycle_budget_did_not_approve_$requestedAction"
            RuntimeProotCapacityExecutorState.APPROVED_WAITING_FOR_POLICY_ENABLE ->
                "lifecycle_approved_$requestedAction but proot capacity executor policy is disabled"
            RuntimeProotCapacityExecutorState.APPROVED_WAITING_FOR_LAUNCHER_BINDING ->
                "lifecycle_approved_$requestedAction but next proot runtime target is not safely bound: $expansionBindingStatus"
            RuntimeProotCapacityExecutorState.APPROVED_READY_FOR_ANDROID_LAUNCH ->
                "lifecycle_approved_$requestedAction and dedicated PROOT_CAPACITY_WORKER target is bound"
            RuntimeProotCapacityExecutorState.DOWNSCALE_READY_FOR_ANDROID_DOWNLINE ->
                "lifecycle_approved_$requestedAction and bound PROOT_CAPACITY_WORKER can be downlined"
            RuntimeProotCapacityExecutorState.DOWNSCALE_WAITING_FOR_TARGET_BINDING ->
                "lifecycle_approved_$requestedAction but no concrete proot downline target is bound"
            RuntimeProotCapacityExecutorState.QUEUE_ON_SINGLE_PROOT_REVIEW ->
                if (expansionMustQueueAtMax) {
                    "max_proots_reached:$maxProots; keep later work in android start queue plan"
                } else {
                    "lifecycle_approved_single_proot_queue; keep work on current proot through android start queue plan"
                }
        }
        return RuntimeProotCapacityExecutorSnapshot(
            executionEnabled = bindingPolicy.enabled,
            state = state,
            recommendation = recommendation,
            requestedAction = requestedAction,
            lifecycleBudgetApproved = approved,
            lifecycleBudgetState = lifecycleBudget.state,
            policyPath = bindingPolicy.path,
            policyLoadStatus = bindingPolicy.loadStatus,
            policyEnabled = bindingPolicy.enabled,
            launchEligible = state == RuntimeProotCapacityExecutorState.APPROVED_READY_FOR_ANDROID_LAUNCH,
            downlineEligible =
                state == RuntimeProotCapacityExecutorState.DOWNSCALE_READY_FOR_ANDROID_DOWNLINE,
            targetBindingStatus = targetBindingStatus,
            singleProotQueueStatus = if (state == RuntimeProotCapacityExecutorState.QUEUE_ON_SINGLE_PROOT_REVIEW) {
                "keep_on_current_proot_no_capacity_change"
            } else {
                "not_requested"
            },
            launcherBindingRequired =
                state == RuntimeProotCapacityExecutorState.APPROVED_WAITING_FOR_LAUNCHER_BINDING,
            downlineTargetBindingRequired =
                state == RuntimeProotCapacityExecutorState.DOWNSCALE_WAITING_FOR_TARGET_BINDING,
            configuredRuntimeTargetId = configuredTargetId,
            registeredRuntimeTargetId = if (hasDedicatedCapacityTarget) boundRuntime?.id.orEmpty() else "none",
            registeredRuntimeTargetKind = boundRuntime?.kind?.name ?: "none",
            targetBindingMode = if (usesAutoBinding) {
                "auto_registered_dedicated_worker"
            } else {
                "explicit_policy_runtime_id"
            },
            launcherImplementationStatus = when {
                hasDedicatedCapacityTarget && boundRuntime?.isActiveRuntime() == false ->
                    "android_background_runtime_dedicated_proot_process"
                bindingPolicy.policyUnavailable() ->
                    "waiting_for_policy_file"
                else ->
                    "waiting_for_dedicated_proot_capacity_worker"
            },
            activeCapacityWorkerCount = activeCapacityWorkerCount,
            registeredCapacityWorkerCount = eligibleCapacityWorkers.size,
            nextCapacityRuntimeIndex = nextCapacityRuntimeIndex,
            desiredCapacityWorkerCount = desiredCapacityWorkerCount,
            maxProots = maxProots,
            maxProotsReached = maxProotsReached,
            memoryReservationRequiredKb = lifecycleBudget.secondProotRequiredMemoryKb,
            actualNextProotStartCount = lifecycleBudget.actualSecondProotStartCount,
            actualSecondProotStartCount = lifecycleBudget.actualSecondProotStartCount,
            actualProotDownlineCount = lifecycleBudget.actualProotDownlineCount,
            actualQueueCreationCount = lifecycleBudget.actualQueueCreationCount,
            reason = reason
        )
    }
}

private fun RuntimeProotCapacityExecutorPolicy.policyUnavailable(): Boolean {
    return loadStatus != "loaded"
}

private fun desiredCapacityWorkerCountFor(
    currentTasks: Int,
    peakTasks: Int,
    scaleOutThreshold: Int
): Int {
    val safePeak = peakTasks.coerceAtLeast(1)
    val threshold = scaleOutThreshold.coerceAtLeast(safePeak + 1)
    val current = currentTasks.coerceAtLeast(0)
    if (current < threshold) {
        return 1
    }
    return 2 + ((current - threshold) / safePeak)
}

private fun BackgroundRuntimeRecord.prootCapacityWorkerIndex(): Int {
    return id.substringAfterLast("-proot-capacity-worker-", "")
        .toIntOrNull()
        ?.takeIf { it > 0 }
        ?: Int.MAX_VALUE
}

private fun String?.toProotCapacityExecutorEnvValue(): String {
    if (this.isNullOrBlank()) return "none"
    return trim()
        .replace(Regex("[\\r\\n\\t ]+"), "_")
        .replace(Regex("[^A-Za-z0-9._:@/+,-]"), "_")
        .take(240)
}
