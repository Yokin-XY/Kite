package com.kite.app.foundation.runtime

import com.kite.app.foundation.service.BackgroundRuntimeKind

/**
 * Lease pool admission and reclaim command contract.
 *
 * The lease pool is the lowest-privilege lifecycle tier. System/core runtimes, PRoot
 * capacity workers, user-locked units, foreground-active units, ambiguous roots, and
 * quarantine semantic units are excluded before lease settlement. Ordinary PRoot-observed
 * Ubuntu roots can enter the pool when they are running and have a stable process identity.
 */
object RuntimeLifecycleLeasePoolAdmission {
    const val RECLAIMER_ACTION = "runtime_reclaimer_existing_reclaim_path"

    fun admissionBlockReason(
        root: RuntimeRootSnapshot,
        plan: RuntimeLifecycleActionPlanEntry,
        item: RuntimeLifecycleReclaimItem,
        config: RuntimeLifecycleStrategyActivationConfig,
        now: Long
    ): String? {
        when (plan.effectiveTier) {
            RuntimeLifecycleAuthorityTier.SYSTEM_CORE ->
                return "system_core_excluded_from_lease_pool"
            RuntimeLifecycleAuthorityTier.PROOT_CORE ->
                return "proot_core_excluded_from_lease_pool"
            RuntimeLifecycleAuthorityTier.PROOT_ELASTIC ->
                return "proot_elastic_owned_by_proot_capacity"
            RuntimeLifecycleAuthorityTier.USER_LOCKED ->
                return if (plan.resourceState == RuntimeProcessResourceMemoryState.OVER_LIMIT) {
                    "user_locked_over_limit_ignored_excluded_from_lease_pool"
                } else {
                    "user_locked_excluded_from_lease_pool"
                }
            RuntimeLifecycleAuthorityTier.FOREGROUND ->
                return if (root.isRunning) {
                    "foreground_active_excluded_from_lease_pool"
                } else {
                    "foreground_inactive_waiting_for_lease_handoff"
                }
            RuntimeLifecycleAuthorityTier.UNMANAGED ->
                return "unmanaged_ubuntu_observe_only_not_lease_pool"
            RuntimeLifecycleAuthorityTier.QUARANTINE ->
                return "quarantine_semantic_only_not_lease_pool"
            RuntimeLifecycleAuthorityTier.LEASE -> Unit
        }

        if (root.runtimeKind == BackgroundRuntimeKind.CONTAINER_SUPERVISOR ||
            root.runtimeKind == BackgroundRuntimeKind.PROOT_CAPACITY_WORKER
        ) {
            return "proot_capacity_owned_by_proot_boundary"
        }
        if (root.ownerKind == RuntimeRootOwnerKind.UNATTRIBUTED &&
            !root.isOrdinaryUbuntuLeaseAdmission(item)
        ) {
            return "unmanaged_ubuntu_observe_only_not_lease_pool"
        }
        if (!config.defaultStrategy.allowsWorkload(item)) {
            return "workload_not_temporary_or_ephemeral_lease"
        }
        if (config.minCandidateAgeMs > 0L) {
            val startedAt = root.lastStartedAt ?: root.lastSeenAt ?: 0L
            if (startedAt <= 0L || now - startedAt < config.minCandidateAgeMs) {
                return "candidate_younger_than_min_candidate_age"
            }
        }
        return null
    }

    fun reclaimCommandBlockReason(
        root: RuntimeRootSnapshot,
        plan: RuntimeLifecycleActionPlanEntry,
        item: RuntimeLifecycleReclaimItem,
        snapshot: RuntimeHealthSnapshot,
        config: RuntimeLifecycleStrategyActivationConfig,
        reclaimerPolicy: RuntimeReclaimerPolicy,
        now: Long,
        requirePlannerCandidate: Boolean,
        requireThreshold: Boolean
    ): String? {
        if (!root.isRunning) {
            return when (root.stopReconciliationState) {
                RuntimeProcessUnitObservationState.STOPPED_EXPECTED ->
                    "expected_stop_not_reclaimed"
                RuntimeProcessUnitObservationState.CORE_RECOVERY_REQUIRED ->
                    "core_recovery_required_not_lease_reclaim"
                else -> "external_or_stale_stop_not_system_reclaim"
            }
        }
        if (root.ownerKind != RuntimeRootOwnerKind.BACKGROUND_RUNTIME &&
            root.ownerKind != RuntimeRootOwnerKind.UNATTRIBUTED
        ) {
            return "not_registered_background_runtime"
        }
        if (requirePlannerCandidate &&
            plan.finalAction != RuntimeLifecycleFinalAction.RECLAIM_CANDIDATE_DRY_RUN
        ) {
            return "planner_not_lease_reclaim_candidate"
        }
        if (config.requireReclaimerBoundary &&
            plan.executorBoundary != RuntimeLifecycleExecutorBoundary.RUNTIME_RECLAIMER
        ) {
            return "executor_boundary_not_runtime_reclaimer"
        }
        if (config.requireNonAmbiguousMatch &&
            plan.matchState == RuntimeProcessUnitMatchState.MATCH_AMBIGUOUS
        ) {
            return "ambiguous_match_never_triggers_real_reclaim"
        }
        if (root.processUnitMatchSource == RuntimeProcessUnitMatchSource.COMMAND_CONTAINS ||
            root.processUnitMatchState == RuntimeProcessUnitMatchState.MATCHED_COMMAND_CONTAINS
        ) {
            return "command_contains_weak_match_never_triggers_real_reclaim"
        }
        if (requireThreshold) {
            leaseReclaimBlockReason(item)?.let { return it }
        }
        return null
    }

    fun leaseReclaimThresholdReached(item: RuntimeLifecycleReclaimItem): Boolean {
        return leaseReclaimBlockReason(item) == null
    }

    fun leaseReclaimBlockReason(item: RuntimeLifecycleReclaimItem): String? {
        if (!item.isLeaseExpired()) return "lease_time_not_expired"
        if (item.leaseExpiredSettlementCount < REQUIRED_EXPIRED_SETTLEMENTS) {
            return "lease_expired_settlement_confirmation_required"
        }
        return null
    }

    fun RuntimeLifecycleReclaimItem.isLeaseExpired(): Boolean {
        return disposition == RuntimeLifecycleReclaimDisposition.WOULD_EXPIRE_LEASE ||
            activityState == RuntimeLifecycleActivityState.EXPIRED ||
            leaseRemainingMs <= 0L
    }

    private const val REQUIRED_EXPIRED_SETTLEMENTS = 2

    private fun RuntimeRootSnapshot.isOrdinaryUbuntuLeaseAdmission(
        item: RuntimeLifecycleReclaimItem
    ): Boolean {
        if (!isRunning || observedPid == null || observedPid <= 0) return false
        if (resident) return false
        if (runtimeKind == BackgroundRuntimeKind.CONTAINER_SUPERVISOR ||
            runtimeKind == BackgroundRuntimeKind.PROOT_CAPACITY_WORKER
        ) {
            return false
        }
        if (processUnitMatchState == RuntimeProcessUnitMatchState.MATCH_AMBIGUOUS ||
            processUnitMatchState == RuntimeProcessUnitMatchState.MATCHED_COMMAND_CONTAINS ||
            processUnitMatchSource == RuntimeProcessUnitMatchSource.COMMAND_CONTAINS
        ) {
            return false
        }
        if (commandLine.isBlank() && title.isBlank()) return false
        return item.layer == RuntimeLifecycleLayer.LEASE_POOL &&
            item.retention == RuntimeWorkloadRetention.LEASE
    }
}
