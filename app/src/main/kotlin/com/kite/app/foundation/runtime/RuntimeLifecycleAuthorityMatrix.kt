package com.kite.app.foundation.runtime

import com.kite.app.foundation.service.BackgroundRuntimeKind
import com.kite.app.foundation.service.RuntimeRetentionClass

enum class RuntimeLifecycleAuthorityTier {
    SYSTEM_CORE,
    PROOT_CORE,
    PROOT_ELASTIC,
    USER_LOCKED,
    FOREGROUND,
    LEASE,
    UNMANAGED,
    QUARANTINE
}

enum class RuntimeLifecycleMatrixState {
    RUNNING,
    STOPPED_EXPECTED,
    WAIT_CONFIRM_RESTART,
    AUTO_RESTART_ALLOWED,
    CORE_RECOVERY_REQUIRED,
    STOPPED_CRASH_SUSPECTED,
    MEMORY_PRESSURE_RECLAIM_REVIEW,
    LEASE_CANDIDATE,
    QUARANTINE_REVIEW,
    OBSERVE_ONLY
}

enum class RuntimeLifecycleRecommendedAction {
    KEEP_RUNNING,
    KEEP_STOPPED,
    WAIT_FOR_CONFIRMATION,
    AUTO_RECOVERY_CANDIDATE,
    CORE_RECOVERY_REQUIRED,
    REVIEW_LEASE_CLEANUP,
    REVIEW_RECLAIM,
    REVIEW_QUARANTINE,
    OBSERVE_ONLY
}

enum class RuntimeLifecycleMatrixActionMode {
    OBSERVE_ONLY,
    DRY_RUN,
    REAL_RUNTIME
}

data class RuntimeLifecycleAuthorityMatrixEntry(
    val rootKey: String,
    val rootTitle: String,
    val unitId: String = "none",
    val matchSource: RuntimeProcessUnitMatchSource = RuntimeProcessUnitMatchSource.NONE,
    val matchConfidence: RuntimeProcessUnitMatchConfidence = RuntimeProcessUnitMatchConfidence.NONE,
    val matchState: RuntimeProcessUnitMatchState = RuntimeProcessUnitMatchState.UNMANAGED_OBSERVED,
    val matchedPid: Int? = null,
    val matchedPgid: Int? = null,
    val matchedSid: Int? = null,
    val conflictUnitIds: List<String> = emptyList(),
    val fallbackReason: String = "none",
    val effectiveTier: RuntimeLifecycleAuthorityTier,
    val authoritySource: String,
    val lifecycleState: RuntimeLifecycleMatrixState,
    val recommendedAction: RuntimeLifecycleRecommendedAction,
    val actionMode: RuntimeLifecycleMatrixActionMode,
    val suppressionReason: String = "none",
    val recoveryAllowed: Boolean = false,
    val reclaimAllowed: Boolean = false,
    val reason: String = "none"
)

data class RuntimeLifecycleAuthorityMatrixSnapshot(
    val mode: String = "runtime_lifecycle_authority_matrix_v0",
    val enforcementMode: String = "dry_run_matrix_only",
    val enforcementEnabled: Boolean = false,
    val authorityPriority: String =
        "built_in_core>proot_core>registry_identity>expected_stop>manifest>resident_policy>workload_policy>reclaimer_policy>process_snapshot",
    val entryCount: Int = 0,
    val systemCoreCount: Int = 0,
    val prootCoreCount: Int = 0,
    val prootElasticCount: Int = 0,
    val userLockedCount: Int = 0,
    val foregroundCount: Int = 0,
    val leaseCount: Int = 0,
    val unmanagedCount: Int = 0,
    val quarantineCount: Int = 0,
    val waitConfirmCount: Int = 0,
    val autoRecoveryCandidateCount: Int = 0,
    val coreRecoveryRequiredCount: Int = 0,
    val reclaimAllowedCount: Int = 0,
    val recoveryAllowedCount: Int = 0,
    val entries: List<RuntimeLifecycleAuthorityMatrixEntry> = emptyList()
) {
    fun toEnvText(maxItems: Int = 8): String {
        return buildString {
            appendLine("lifecycle_authority_matrix_mode=${mode.toLifecycleAuthorityEnvValue()}")
            appendLine("lifecycle_authority_matrix_enforcement_mode=${enforcementMode.toLifecycleAuthorityEnvValue()}")
            appendLine("lifecycle_authority_matrix_enforcement_enabled=$enforcementEnabled")
            appendLine("lifecycle_authority_matrix_authority_priority=${authorityPriority.toLifecycleAuthorityEnvValue()}")
            appendLine("lifecycle_authority_matrix_entry_count=$entryCount")
            appendLine("lifecycle_authority_matrix_system_core_count=$systemCoreCount")
            appendLine("lifecycle_authority_matrix_proot_core_count=$prootCoreCount")
            appendLine("lifecycle_authority_matrix_proot_elastic_count=$prootElasticCount")
            appendLine("lifecycle_authority_matrix_user_locked_count=$userLockedCount")
            appendLine("lifecycle_authority_matrix_foreground_count=$foregroundCount")
            appendLine("lifecycle_authority_matrix_lease_count=$leaseCount")
            appendLine("lifecycle_authority_matrix_unmanaged_count=$unmanagedCount")
            appendLine("lifecycle_authority_matrix_quarantine_count=$quarantineCount")
            appendLine("lifecycle_authority_matrix_wait_confirm_count=$waitConfirmCount")
            appendLine("lifecycle_authority_matrix_auto_recovery_candidate_count=$autoRecoveryCandidateCount")
            appendLine("lifecycle_authority_matrix_core_recovery_required_count=$coreRecoveryRequiredCount")
            appendLine("lifecycle_authority_matrix_reclaim_allowed_count=$reclaimAllowedCount")
            appendLine("lifecycle_authority_matrix_recovery_allowed_count=$recoveryAllowedCount")
            entries.take(maxItems).forEachIndexed { index, entry ->
                val prefix = "lifecycle_authority_matrix_entry_${index + 1}"
                appendLine("${prefix}_root_key=${entry.rootKey.toLifecycleAuthorityEnvValue()}")
                appendLine("${prefix}_title=${entry.rootTitle.toLifecycleAuthorityEnvValue()}")
                appendLine("${prefix}_unit_id=${entry.unitId.toLifecycleAuthorityEnvValue()}")
                appendLine("${prefix}_match_source=${entry.matchSource.name}")
                appendLine("${prefix}_match_confidence=${entry.matchConfidence.name}")
                appendLine("${prefix}_match_state=${entry.matchState.name}")
                appendLine("${prefix}_matched_pid=${entry.matchedPid ?: 0}")
                appendLine("${prefix}_matched_pgid=${entry.matchedPgid ?: 0}")
                appendLine("${prefix}_matched_sid=${entry.matchedSid ?: 0}")
                appendLine("${prefix}_conflict_unit_ids=${entry.conflictUnitIds.joinToString(",").toLifecycleAuthorityEnvValue()}")
                appendLine("${prefix}_fallback_reason=${entry.fallbackReason.toLifecycleAuthorityEnvValue()}")
                appendLine("${prefix}_effective_tier=${entry.effectiveTier.name}")
                appendLine("${prefix}_authority_source=${entry.authoritySource.toLifecycleAuthorityEnvValue()}")
                appendLine("${prefix}_lifecycle_state=${entry.lifecycleState.name}")
                appendLine("${prefix}_recommended_action=${entry.recommendedAction.name}")
                appendLine("${prefix}_action_mode=${entry.actionMode.name}")
                appendLine("${prefix}_suppression_reason=${entry.suppressionReason.toLifecycleAuthorityEnvValue()}")
                appendLine("${prefix}_recovery_allowed=${entry.recoveryAllowed}")
                appendLine("${prefix}_reclaim_allowed=${entry.reclaimAllowed}")
                appendLine("${prefix}_reason=${entry.reason.toLifecycleAuthorityEnvValue()}")
            }
            appendLine("lifecycle_authority_matrix_boundary=no_new_kill_restart_quarantine_or_proot_capacity_execution")
        }
    }
}

object RuntimeLifecycleAuthorityMatrix {
    fun evaluate(
        roots: List<RuntimeRootSnapshot>,
        lifecycleReclaimPlan: RuntimeLifecycleReclaimPlanDryRunSnapshot =
            RuntimeLifecycleReclaimPlanDryRunSnapshot()
    ): RuntimeLifecycleAuthorityMatrixSnapshot {
        val reclaimItemsById = lifecycleReclaimPlan.items.associateBy { it.workloadId }
        val entries = roots.map { root ->
            evaluateRoot(
                root = root,
                reclaimItem = root.candidateWorkloadIds()
                    .firstNotNullOfOrNull { reclaimItemsById[it] }
            )
        }
        return RuntimeLifecycleAuthorityMatrixSnapshot(
            entryCount = entries.size,
            systemCoreCount = entries.count { it.effectiveTier == RuntimeLifecycleAuthorityTier.SYSTEM_CORE },
            prootCoreCount = entries.count { it.effectiveTier == RuntimeLifecycleAuthorityTier.PROOT_CORE },
            prootElasticCount = entries.count { it.effectiveTier == RuntimeLifecycleAuthorityTier.PROOT_ELASTIC },
            userLockedCount = entries.count { it.effectiveTier == RuntimeLifecycleAuthorityTier.USER_LOCKED },
            foregroundCount = entries.count { it.effectiveTier == RuntimeLifecycleAuthorityTier.FOREGROUND },
            leaseCount = entries.count { it.effectiveTier == RuntimeLifecycleAuthorityTier.LEASE },
            unmanagedCount = entries.count { it.effectiveTier == RuntimeLifecycleAuthorityTier.UNMANAGED },
            quarantineCount = entries.count { it.effectiveTier == RuntimeLifecycleAuthorityTier.QUARANTINE },
            waitConfirmCount =
                entries.count { it.lifecycleState == RuntimeLifecycleMatrixState.WAIT_CONFIRM_RESTART },
            autoRecoveryCandidateCount =
                entries.count { it.recommendedAction == RuntimeLifecycleRecommendedAction.AUTO_RECOVERY_CANDIDATE },
            coreRecoveryRequiredCount =
                entries.count { it.lifecycleState == RuntimeLifecycleMatrixState.CORE_RECOVERY_REQUIRED },
            reclaimAllowedCount = entries.count { it.reclaimAllowed },
            recoveryAllowedCount = entries.count { it.recoveryAllowed },
            entries = entries
        )
    }

    fun evaluateRoot(
        root: RuntimeRootSnapshot,
        reclaimItem: RuntimeLifecycleReclaimItem? = null
    ): RuntimeLifecycleAuthorityMatrixEntry {
        val tier = root.effectiveAuthorityTier()
        val authority = root.authoritySourceFor(tier)
        val stopDecision = RuntimeProcessStopReconciliation.evaluate(root)
        val matchAmbiguous = root.processUnitMatchState == RuntimeProcessUnitMatchState.MATCH_AMBIGUOUS
        val reclaimAllowed = !matchAmbiguous && reclaimAllowedFor(root, tier)
        val recoveryAllowed = !matchAmbiguous && recoveryAllowedFor(tier, stopDecision)
        val protectedFromReclaim = tier == RuntimeLifecycleAuthorityTier.SYSTEM_CORE ||
            tier == RuntimeLifecycleAuthorityTier.PROOT_CORE ||
            tier == RuntimeLifecycleAuthorityTier.USER_LOCKED ||
            tier == RuntimeLifecycleAuthorityTier.FOREGROUND
        val leaseReclaimCandidate = reclaimItem != null &&
            reclaimAllowed &&
            !protectedFromReclaim &&
            (
                reclaimItem.reclaimRank > 0 ||
                    reclaimItem.disposition == RuntimeLifecycleReclaimDisposition.WOULD_EXPIRE_LEASE ||
                    reclaimItem.disposition == RuntimeLifecycleReclaimDisposition.WOULD_CLEANUP ||
                    reclaimItem.disposition == RuntimeLifecycleReclaimDisposition.WOULD_RECLAIM_CHILDREN ||
                    reclaimItem.disposition == RuntimeLifecycleReclaimDisposition.WOULD_TERMINATE_WORKLOAD
                )
        val pressureReclaimCandidate = leaseReclaimCandidate &&
            reclaimItem?.disposition != RuntimeLifecycleReclaimDisposition.WOULD_EXPIRE_LEASE

        val lifecycleState = when {
            tier == RuntimeLifecycleAuthorityTier.QUARANTINE ->
                RuntimeLifecycleMatrixState.QUARANTINE_REVIEW
            leaseReclaimCandidate && pressureReclaimCandidate ->
                RuntimeLifecycleMatrixState.MEMORY_PRESSURE_RECLAIM_REVIEW
            leaseReclaimCandidate -> RuntimeLifecycleMatrixState.LEASE_CANDIDATE
            root.isRunning -> RuntimeLifecycleMatrixState.RUNNING
            stopDecision.observedState == RuntimeProcessUnitObservationState.CORE_RECOVERY_REQUIRED ->
                RuntimeLifecycleMatrixState.CORE_RECOVERY_REQUIRED
            stopDecision.observedState == RuntimeProcessUnitObservationState.WAIT_CONFIRM_RESTART ->
                RuntimeLifecycleMatrixState.WAIT_CONFIRM_RESTART
            stopDecision.observedState == RuntimeProcessUnitObservationState.AUTO_RESTART_ALLOWED ->
                RuntimeLifecycleMatrixState.AUTO_RESTART_ALLOWED
            stopDecision.observedState == RuntimeProcessUnitObservationState.STOPPED_CRASH_SUSPECTED ->
                RuntimeLifecycleMatrixState.STOPPED_CRASH_SUSPECTED
            stopDecision.observedState == RuntimeProcessUnitObservationState.STOPPED_EXPECTED ->
                RuntimeLifecycleMatrixState.STOPPED_EXPECTED
            else -> RuntimeLifecycleMatrixState.OBSERVE_ONLY
        }
        val recommendedAction = when (lifecycleState) {
            RuntimeLifecycleMatrixState.RUNNING -> RuntimeLifecycleRecommendedAction.KEEP_RUNNING
            RuntimeLifecycleMatrixState.STOPPED_EXPECTED -> RuntimeLifecycleRecommendedAction.KEEP_STOPPED
            RuntimeLifecycleMatrixState.WAIT_CONFIRM_RESTART ->
                RuntimeLifecycleRecommendedAction.WAIT_FOR_CONFIRMATION
            RuntimeLifecycleMatrixState.AUTO_RESTART_ALLOWED,
            RuntimeLifecycleMatrixState.STOPPED_CRASH_SUSPECTED ->
                RuntimeLifecycleRecommendedAction.AUTO_RECOVERY_CANDIDATE
            RuntimeLifecycleMatrixState.CORE_RECOVERY_REQUIRED ->
                RuntimeLifecycleRecommendedAction.CORE_RECOVERY_REQUIRED
            RuntimeLifecycleMatrixState.LEASE_CANDIDATE ->
                RuntimeLifecycleRecommendedAction.REVIEW_LEASE_CLEANUP
            RuntimeLifecycleMatrixState.MEMORY_PRESSURE_RECLAIM_REVIEW ->
                RuntimeLifecycleRecommendedAction.REVIEW_RECLAIM
            RuntimeLifecycleMatrixState.QUARANTINE_REVIEW ->
                RuntimeLifecycleRecommendedAction.REVIEW_QUARANTINE
            RuntimeLifecycleMatrixState.OBSERVE_ONLY -> RuntimeLifecycleRecommendedAction.OBSERVE_ONLY
        }
        val actionMode = when {
            lifecycleState == RuntimeLifecycleMatrixState.RUNNING ||
                lifecycleState == RuntimeLifecycleMatrixState.OBSERVE_ONLY ->
                RuntimeLifecycleMatrixActionMode.OBSERVE_ONLY
            recommendedAction == RuntimeLifecycleRecommendedAction.REVIEW_LEASE_CLEANUP ||
                recommendedAction == RuntimeLifecycleRecommendedAction.REVIEW_RECLAIM ||
                recommendedAction == RuntimeLifecycleRecommendedAction.REVIEW_QUARANTINE ||
                matchAmbiguous ||
                tier == RuntimeLifecycleAuthorityTier.PROOT_CORE ->
                RuntimeLifecycleMatrixActionMode.DRY_RUN
            stopDecision.suppressAutoRecovery ||
                recommendedAction == RuntimeLifecycleRecommendedAction.AUTO_RECOVERY_CANDIDATE ||
                tier == RuntimeLifecycleAuthorityTier.SYSTEM_CORE ->
                RuntimeLifecycleMatrixActionMode.REAL_RUNTIME
            else -> RuntimeLifecycleMatrixActionMode.DRY_RUN
        }
        val suppressionReason = when {
            matchAmbiguous -> "match_ambiguous_blocks_real_recovery_reclaim"
            stopDecision.suppressAutoRecovery -> stopDecision.reason
            protectedFromReclaim && reclaimItem != null -> "protected_tier_blocks_reclaimer"
            else -> "none"
        }
        return RuntimeLifecycleAuthorityMatrixEntry(
            rootKey = root.ownershipKey,
            rootTitle = root.title,
            unitId = root.processUnitId ?: "none",
            matchSource = root.processUnitMatchSource,
            matchConfidence = root.processUnitMatchConfidence,
            matchState = root.processUnitMatchState,
            matchedPid = root.processUnitMatchedPid,
            matchedPgid = root.processUnitMatchedPgid,
            matchedSid = root.processUnitMatchedSid,
            conflictUnitIds = root.processUnitConflictUnitIds,
            fallbackReason = root.processUnitFallbackReason ?: "none",
            effectiveTier = tier,
            authoritySource = authority,
            lifecycleState = lifecycleState,
            recommendedAction = recommendedAction,
            actionMode = actionMode,
            suppressionReason = suppressionReason,
            recoveryAllowed = recoveryAllowed,
            reclaimAllowed = reclaimAllowed,
            reason = buildReason(root, stopDecision, reclaimItem, tier, authority)
        )
    }

    private fun reclaimAllowedFor(
        root: RuntimeRootSnapshot,
        tier: RuntimeLifecycleAuthorityTier
    ): Boolean {
        return when (tier) {
            RuntimeLifecycleAuthorityTier.SYSTEM_CORE,
            RuntimeLifecycleAuthorityTier.PROOT_CORE,
            RuntimeLifecycleAuthorityTier.PROOT_ELASTIC,
            RuntimeLifecycleAuthorityTier.USER_LOCKED,
            RuntimeLifecycleAuthorityTier.FOREGROUND,
            RuntimeLifecycleAuthorityTier.UNMANAGED,
            RuntimeLifecycleAuthorityTier.QUARANTINE -> false
            RuntimeLifecycleAuthorityTier.LEASE -> root.processUnitAllowReclaim ||
                root.processUnitTier == RuntimeProcessUnitTier.LEASE ||
                root.isOrdinaryUbuntuLeaseCandidate()
        }
    }

    private fun recoveryAllowedFor(
        tier: RuntimeLifecycleAuthorityTier,
        stopDecision: RuntimeProcessStopReconciliationDecision
    ): Boolean {
        return when (tier) {
            RuntimeLifecycleAuthorityTier.SYSTEM_CORE,
            RuntimeLifecycleAuthorityTier.PROOT_CORE -> true
            RuntimeLifecycleAuthorityTier.USER_LOCKED,
            RuntimeLifecycleAuthorityTier.FOREGROUND,
            RuntimeLifecycleAuthorityTier.LEASE,
            RuntimeLifecycleAuthorityTier.UNMANAGED,
            RuntimeLifecycleAuthorityTier.QUARANTINE,
            RuntimeLifecycleAuthorityTier.PROOT_ELASTIC -> stopDecision.autoRecoveryAllowed
        }
    }

    private fun buildReason(
        root: RuntimeRootSnapshot,
        stopDecision: RuntimeProcessStopReconciliationDecision,
        reclaimItem: RuntimeLifecycleReclaimItem?,
        tier: RuntimeLifecycleAuthorityTier,
        authority: String
    ): String {
        val reclaimReason = reclaimItem?.let {
            ",reclaimLayer=${it.layer.name},disposition=${it.disposition.name},rank=${it.reclaimRank}"
        }.orEmpty()
        return "tier=${tier.name},authority=$authority,stop=${stopDecision.observedState.name}," +
            "source=${root.processUnitSource ?: root.classificationSource}$reclaimReason"
    }

    private fun RuntimeRootSnapshot.effectiveAuthorityTier(): RuntimeLifecycleAuthorityTier {
        if (runtimeKind == BackgroundRuntimeKind.CONTAINER_SUPERVISOR ||
            retentionClass == RuntimeRetentionClass.CRITICAL_CORE ||
            processUnitTier == RuntimeProcessUnitTier.SYSTEM_CORE
        ) {
            return RuntimeLifecycleAuthorityTier.SYSTEM_CORE
        }
        if (runtimeKind == BackgroundRuntimeKind.PROOT_CAPACITY_WORKER) {
            return if (prootCapacityIndex() == 1) {
                RuntimeLifecycleAuthorityTier.PROOT_CORE
            } else {
                RuntimeLifecycleAuthorityTier.PROOT_ELASTIC
            }
        }
        return when (processUnitTier) {
            RuntimeProcessUnitTier.SYSTEM_CORE -> RuntimeLifecycleAuthorityTier.SYSTEM_CORE
            RuntimeProcessUnitTier.PROOT_CORE -> RuntimeLifecycleAuthorityTier.PROOT_CORE
            RuntimeProcessUnitTier.USER_LOCKED -> RuntimeLifecycleAuthorityTier.USER_LOCKED
            RuntimeProcessUnitTier.FOREGROUND -> RuntimeLifecycleAuthorityTier.FOREGROUND
            RuntimeProcessUnitTier.LEASE -> RuntimeLifecycleAuthorityTier.LEASE
            RuntimeProcessUnitTier.UNMANAGED -> RuntimeLifecycleAuthorityTier.UNMANAGED
            RuntimeProcessUnitTier.QUARANTINE -> RuntimeLifecycleAuthorityTier.QUARANTINE
            null -> when {
                ownerKind == RuntimeRootOwnerKind.CARD ||
                    ownerKind == RuntimeRootOwnerKind.RESOURCE -> RuntimeLifecycleAuthorityTier.FOREGROUND
                ownerKind == RuntimeRootOwnerKind.UNATTRIBUTED && isOrdinaryUbuntuLeaseCandidate() ->
                    RuntimeLifecycleAuthorityTier.LEASE
                ownerKind == RuntimeRootOwnerKind.UNATTRIBUTED -> RuntimeLifecycleAuthorityTier.UNMANAGED
                retentionClass == RuntimeRetentionClass.RESIDENT ||
                    retentionClass == RuntimeRetentionClass.INTERACTIVE -> RuntimeLifecycleAuthorityTier.FOREGROUND
                retentionClass == RuntimeRetentionClass.BATCH ||
                    retentionClass == RuntimeRetentionClass.EPHEMERAL -> RuntimeLifecycleAuthorityTier.LEASE
                else -> RuntimeLifecycleAuthorityTier.UNMANAGED
            }
        }
    }

    private fun RuntimeRootSnapshot.isOrdinaryUbuntuLeaseCandidate(): Boolean {
        if (!isRunning) return false
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
        if (observedPid == null || observedPid <= 0) return false
        if (commandLine.isBlank() && title.isBlank()) return false
        return true
    }

    private fun RuntimeRootSnapshot.authoritySourceFor(
        tier: RuntimeLifecycleAuthorityTier
    ): String {
        return when {
            tier == RuntimeLifecycleAuthorityTier.SYSTEM_CORE ->
                "built_in_core_registry"
            tier == RuntimeLifecycleAuthorityTier.PROOT_CORE ->
                "proot_core_protection"
            tier == RuntimeLifecycleAuthorityTier.PROOT_ELASTIC ->
                "background_runtime_registry+proot_capacity_policy"
            processUnitSource == "manifest" ->
                "runtime_process_unit_manifest"
            !processUnitSource.isNullOrBlank() ->
                processUnitSource
            ownerKind == RuntimeRootOwnerKind.BACKGROUND_RUNTIME ->
                "background_runtime_registry"
            ownerKind == RuntimeRootOwnerKind.CARD ||
                ownerKind == RuntimeRootOwnerKind.RESOURCE ->
                "proot_telemetry_owner_process_index"
            ownerKind == RuntimeRootOwnerKind.UNATTRIBUTED ->
                "process_snapshot_heuristic"
            else -> "runtime_health_store"
        }
    }

    private fun RuntimeRootSnapshot.candidateWorkloadIds(): List<String> {
        return listOfNotNull(
            ownerId,
            processUnitId,
            ownershipKey,
            rootPid?.toString(),
            title.takeIf { it.isNotBlank() }
        ).distinct()
    }

    private fun RuntimeRootSnapshot.prootCapacityIndex(): Int {
        return ownerId
            ?.substringAfterLast("-proot-capacity-worker-", "")
            ?.toIntOrNull()
            ?.takeIf { it > 0 }
            ?: Int.MAX_VALUE
    }
}

private fun String?.toLifecycleAuthorityEnvValue(): String {
    if (this.isNullOrBlank()) return "none"
    return trim()
        .replace(Regex("[\\r\\n\\t ]+"), "_")
        .replace(Regex("[^A-Za-z0-9._:@/+,-]"), "_")
        .take(240)
}
