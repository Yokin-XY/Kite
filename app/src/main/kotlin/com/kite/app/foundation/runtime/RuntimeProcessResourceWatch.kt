package com.kite.app.foundation.runtime

import kotlin.math.ceil

enum class RuntimeProcessResourceMemoryState {
    NO_LIMIT,
    NO_MEMORY_DATA,
    WITHIN_LIMIT,
    NEAR_LIMIT,
    OVER_LIMIT,
    OVER_LIMIT_BUT_UNLIMITED,
    AMBIGUOUS_MATCH_NO_ENFORCEMENT,
    CORE_PROTECTED_NO_ENFORCEMENT,
    DRY_RUN_ONLY
}

enum class RuntimeProcessResourceRecommendedAction {
    OBSERVE,
    WARN_ONLY,
    RESTART_CANDIDATE_DRY_RUN,
    QUARANTINE_CANDIDATE_DRY_RUN,
    RECLAIM_CANDIDATE_DRY_RUN,
    NO_ACTION_UNLIMITED,
    NO_ACTION_CORE_PROTECTED,
    NO_ACTION_AMBIGUOUS
}

data class RuntimeProcessResourceWatchEntry(
    val rootKey: String,
    val unitId: String,
    val effectiveTier: RuntimeLifecycleAuthorityTier,
    val matchedPid: Int? = null,
    val matchedPgid: Int? = null,
    val matchedSid: Int? = null,
    val memoryCurrentKb: Long = 0L,
    val memoryTreeKb: Long = 0L,
    val expectedMemoryLimitKb: Long? = null,
    val unlimitedMemory: Boolean = false,
    val warningThresholdKb: Long? = null,
    val restartThresholdKb: Long? = null,
    val memoryState: RuntimeProcessResourceMemoryState,
    val recommendedResourceAction: RuntimeProcessResourceRecommendedAction,
    val actionMode: RuntimeLifecycleMatrixActionMode,
    val resourceSuppressionReason: String = "none"
)

data class RuntimeProcessResourceWatchSnapshot(
    val mode: String = "runtime_process_resource_watch_v0",
    val enforcementMode: String = "warning_only_dry_run",
    val resourceWatchEnabled: Boolean = true,
    val enforcementEnabled: Boolean = false,
    val unitResourceCount: Int = 0,
    val unitsNearLimitCount: Int = 0,
    val unitsOverLimitCount: Int = 0,
    val unlimitedUnitCount: Int = 0,
    val ambiguousNoEnforcementCount: Int = 0,
    val coreProtectedNoEnforcementCount: Int = 0,
    val restartCandidateDryRunCount: Int = 0,
    val quarantineCandidateDryRunCount: Int = 0,
    val reclaimCandidateDryRunCount: Int = 0,
    val entries: List<RuntimeProcessResourceWatchEntry> = emptyList(),
    val boundary: String = "warning_only_no_restart_reclaim_kill_quarantine_or_proot_capacity_execution"
) {
    fun toEnvText(maxItems: Int = 8): String {
        return buildString {
            appendLine("runtime_process_resource_watch_mode=${mode.toResourceWatchEnvValue()}")
            appendLine("runtime_process_resource_watch_enforcement_mode=${enforcementMode.toResourceWatchEnvValue()}")
            appendLine("runtime_process_resource_watch_enabled=$resourceWatchEnabled")
            appendLine("runtime_process_resource_watch_enforcement_enabled=$enforcementEnabled")
            appendLine("runtime_process_resource_watch_unit_resource_count=$unitResourceCount")
            appendLine("runtime_process_resource_watch_units_near_limit_count=$unitsNearLimitCount")
            appendLine("runtime_process_resource_watch_units_over_limit_count=$unitsOverLimitCount")
            appendLine("runtime_process_resource_watch_unlimited_unit_count=$unlimitedUnitCount")
            appendLine("runtime_process_resource_watch_ambiguous_no_enforcement_count=$ambiguousNoEnforcementCount")
            appendLine("runtime_process_resource_watch_core_protected_no_enforcement_count=$coreProtectedNoEnforcementCount")
            appendLine("runtime_process_resource_watch_restart_candidate_dry_run_count=$restartCandidateDryRunCount")
            appendLine("runtime_process_resource_watch_quarantine_candidate_dry_run_count=$quarantineCandidateDryRunCount")
            appendLine("runtime_process_resource_watch_reclaim_candidate_dry_run_count=$reclaimCandidateDryRunCount")
            entries.take(maxItems).forEachIndexed { index, entry ->
                val prefix = "runtime_process_resource_watch_unit_${index + 1}"
                appendLine("${prefix}_id=${entry.unitId.toResourceWatchEnvValue()}")
                appendLine("${prefix}_effective_tier=${entry.effectiveTier.name}")
                appendLine("${prefix}_matched_pid=${entry.matchedPid ?: 0}")
                appendLine("${prefix}_matched_pgid=${entry.matchedPgid ?: 0}")
                appendLine("${prefix}_matched_sid=${entry.matchedSid ?: 0}")
                appendLine("${prefix}_memory_current_kb=${entry.memoryCurrentKb}")
                appendLine("${prefix}_memory_tree_kb=${entry.memoryTreeKb}")
                appendLine("${prefix}_expected_memory_limit_kb=${entry.expectedMemoryLimitKb ?: 0L}")
                appendLine("${prefix}_unlimited_memory=${entry.unlimitedMemory}")
                appendLine("${prefix}_warning_threshold_kb=${entry.warningThresholdKb ?: 0L}")
                appendLine("${prefix}_restart_threshold_kb=${entry.restartThresholdKb ?: 0L}")
                appendLine("${prefix}_memory_state=${entry.memoryState.name}")
                appendLine("${prefix}_recommended_resource_action=${entry.recommendedResourceAction.name}")
                appendLine("${prefix}_action_mode=${entry.actionMode.name}")
                appendLine("${prefix}_suppression_reason=${entry.resourceSuppressionReason.toResourceWatchEnvValue()}")
            }
            appendLine("runtime_process_resource_watch_boundary=${boundary.toResourceWatchEnvValue()}")
        }
    }
}

object RuntimeProcessResourceWatch {
    fun evaluate(
        roots: List<RuntimeRootSnapshot>,
        authorityMatrix: RuntimeLifecycleAuthorityMatrixSnapshot =
            RuntimeLifecycleAuthorityMatrix.evaluate(roots)
    ): RuntimeProcessResourceWatchSnapshot {
        val authorityByRoot = authorityMatrix.entries.associateBy { it.rootKey }
        val entries = roots.map { root ->
            evaluateRoot(
                root = root,
                authorityEntry = authorityByRoot[root.ownershipKey]
                    ?: RuntimeLifecycleAuthorityMatrix.evaluateRoot(root)
            )
        }
        return RuntimeProcessResourceWatchSnapshot(
            unitResourceCount = entries.size,
            unitsNearLimitCount = entries.count {
                it.memoryState == RuntimeProcessResourceMemoryState.NEAR_LIMIT
            },
            unitsOverLimitCount = entries.count {
                it.memoryState == RuntimeProcessResourceMemoryState.OVER_LIMIT ||
                    it.memoryState == RuntimeProcessResourceMemoryState.OVER_LIMIT_BUT_UNLIMITED
            },
            unlimitedUnitCount = entries.count { it.unlimitedMemory },
            ambiguousNoEnforcementCount = entries.count {
                it.memoryState == RuntimeProcessResourceMemoryState.AMBIGUOUS_MATCH_NO_ENFORCEMENT
            },
            coreProtectedNoEnforcementCount = entries.count {
                it.memoryState == RuntimeProcessResourceMemoryState.CORE_PROTECTED_NO_ENFORCEMENT
            },
            restartCandidateDryRunCount = entries.count {
                it.recommendedResourceAction ==
                    RuntimeProcessResourceRecommendedAction.RESTART_CANDIDATE_DRY_RUN
            },
            quarantineCandidateDryRunCount = entries.count {
                it.recommendedResourceAction ==
                    RuntimeProcessResourceRecommendedAction.QUARANTINE_CANDIDATE_DRY_RUN
            },
            reclaimCandidateDryRunCount = entries.count {
                it.recommendedResourceAction ==
                    RuntimeProcessResourceRecommendedAction.RECLAIM_CANDIDATE_DRY_RUN
            },
            entries = entries
        )
    }

    fun evaluateRoot(
        root: RuntimeRootSnapshot,
        authorityEntry: RuntimeLifecycleAuthorityMatrixEntry =
            RuntimeLifecycleAuthorityMatrix.evaluateRoot(root)
    ): RuntimeProcessResourceWatchEntry {
        val limitKb = root.processUnitExpectedMemoryLimitKb?.coerceAtLeast(1L)
        val warningThresholdKb = limitKb?.let {
            thresholdKb(it, root.processUnitWarningThresholdRatio)
        }
        val restartThresholdKb = limitKb?.let {
            thresholdKb(it, root.processUnitRestartThresholdRatio)
        }
        val currentKb = root.rssKb.coerceAtLeast(0L)
        val treeKb = root.rssKb.coerceAtLeast(0L)
        val base = RuntimeProcessResourceWatchEntry(
            rootKey = root.ownershipKey,
            unitId = root.processUnitId ?: "unmanaged:${root.ownershipKey}",
            effectiveTier = authorityEntry.effectiveTier,
            matchedPid = root.processUnitMatchedPid ?: root.observedPid ?: root.rootPid ?: root.expectedPid,
            matchedPgid = root.processUnitMatchedPgid ?: root.rootProcessGroupId,
            matchedSid = root.processUnitMatchedSid ?: root.rootSessionId,
            memoryCurrentKb = currentKb,
            memoryTreeKb = treeKb,
            expectedMemoryLimitKb = limitKb,
            unlimitedMemory = root.processUnitUnlimitedMemory,
            warningThresholdKb = warningThresholdKb,
            restartThresholdKb = restartThresholdKb,
            memoryState = RuntimeProcessResourceMemoryState.DRY_RUN_ONLY,
            recommendedResourceAction = RuntimeProcessResourceRecommendedAction.OBSERVE,
            actionMode = RuntimeLifecycleMatrixActionMode.OBSERVE_ONLY
        )

        if (authorityEntry.effectiveTier == RuntimeLifecycleAuthorityTier.SYSTEM_CORE ||
            authorityEntry.effectiveTier == RuntimeLifecycleAuthorityTier.PROOT_CORE
        ) {
            return base.copy(
                memoryState = RuntimeProcessResourceMemoryState.CORE_PROTECTED_NO_ENFORCEMENT,
                recommendedResourceAction =
                    RuntimeProcessResourceRecommendedAction.NO_ACTION_CORE_PROTECTED,
                actionMode = RuntimeLifecycleMatrixActionMode.DRY_RUN,
                resourceSuppressionReason =
                    "core_or_proot_1_memory_is_protected_by_built_in_lifecycle_not_manifest_resource_limit"
            )
        }

        if (root.processUnitMatchState == RuntimeProcessUnitMatchState.MATCH_AMBIGUOUS) {
            return base.copy(
                memoryState = RuntimeProcessResourceMemoryState.AMBIGUOUS_MATCH_NO_ENFORCEMENT,
                recommendedResourceAction =
                    RuntimeProcessResourceRecommendedAction.NO_ACTION_AMBIGUOUS,
                actionMode = RuntimeLifecycleMatrixActionMode.DRY_RUN,
                resourceSuppressionReason = "ambiguous_process_unit_match_blocks_resource_enforcement"
            )
        }

        if (authorityEntry.effectiveTier == RuntimeLifecycleAuthorityTier.PROOT_ELASTIC) {
            return base.copy(
                memoryState = RuntimeProcessResourceMemoryState.DRY_RUN_ONLY,
                recommendedResourceAction = RuntimeProcessResourceRecommendedAction.OBSERVE,
                actionMode = RuntimeLifecycleMatrixActionMode.OBSERVE_ONLY,
                resourceSuppressionReason =
                    "proot_elastic_memory_is_owned_by_proot_capacity_and_memory_admission"
            )
        }

        if (root.processUnitUnlimitedMemory) {
            val overAdvisoryLimit = limitKb != null &&
                restartThresholdKb != null &&
                currentKb >= restartThresholdKb
            return base.copy(
                memoryState = if (overAdvisoryLimit) {
                    RuntimeProcessResourceMemoryState.OVER_LIMIT_BUT_UNLIMITED
                } else {
                    RuntimeProcessResourceMemoryState.NO_LIMIT
                },
                recommendedResourceAction =
                    RuntimeProcessResourceRecommendedAction.NO_ACTION_UNLIMITED,
                actionMode = RuntimeLifecycleMatrixActionMode.OBSERVE_ONLY,
                resourceSuppressionReason =
                    "unlimited_memory_declared_observe_only_global_protection_may_review_later"
            )
        }

        if (limitKb == null) {
            return base.copy(
                memoryState = RuntimeProcessResourceMemoryState.NO_LIMIT,
                recommendedResourceAction = RuntimeProcessResourceRecommendedAction.OBSERVE,
                actionMode = RuntimeLifecycleMatrixActionMode.OBSERVE_ONLY,
                resourceSuppressionReason = "no_expected_memory_limit_declared"
            )
        }

        if (currentKb <= 0L) {
            return base.copy(
                memoryState = RuntimeProcessResourceMemoryState.NO_MEMORY_DATA,
                recommendedResourceAction = RuntimeProcessResourceRecommendedAction.OBSERVE,
                actionMode = RuntimeLifecycleMatrixActionMode.OBSERVE_ONLY,
                resourceSuppressionReason = "root_memory_snapshot_has_no_rss_data"
            )
        }

        if (restartThresholdKb != null && currentKb >= restartThresholdKb) {
            return base.copy(
                memoryState = RuntimeProcessResourceMemoryState.OVER_LIMIT,
                recommendedResourceAction = recommendedOverLimitAction(
                    tier = authorityEntry.effectiveTier,
                    root = root
                ),
                actionMode = RuntimeLifecycleMatrixActionMode.DRY_RUN,
                resourceSuppressionReason = overLimitSuppressionReason(authorityEntry.effectiveTier, root)
            )
        }

        if (warningThresholdKb != null && currentKb >= warningThresholdKb) {
            return base.copy(
                memoryState = RuntimeProcessResourceMemoryState.NEAR_LIMIT,
                recommendedResourceAction = RuntimeProcessResourceRecommendedAction.WARN_ONLY,
                actionMode = RuntimeLifecycleMatrixActionMode.DRY_RUN,
                resourceSuppressionReason = "warning_threshold_reached_warning_only"
            )
        }

        return base.copy(
            memoryState = RuntimeProcessResourceMemoryState.WITHIN_LIMIT,
            recommendedResourceAction = RuntimeProcessResourceRecommendedAction.OBSERVE,
            actionMode = RuntimeLifecycleMatrixActionMode.OBSERVE_ONLY,
            resourceSuppressionReason = "within_expected_memory_limit"
        )
    }

    private fun recommendedOverLimitAction(
        tier: RuntimeLifecycleAuthorityTier,
        root: RuntimeRootSnapshot
    ): RuntimeProcessResourceRecommendedAction {
        return when (tier) {
            RuntimeLifecycleAuthorityTier.LEASE ->
                RuntimeProcessResourceRecommendedAction.RECLAIM_CANDIDATE_DRY_RUN
            RuntimeLifecycleAuthorityTier.FOREGROUND ->
                RuntimeProcessResourceRecommendedAction.WARN_ONLY
            RuntimeLifecycleAuthorityTier.USER_LOCKED -> {
                val threshold = root.processUnitQuarantineAfterFailures.coerceAtLeast(1)
                if (root.restartFailureCount + 1 >= threshold) {
                    RuntimeProcessResourceRecommendedAction.QUARANTINE_CANDIDATE_DRY_RUN
                } else {
                    RuntimeProcessResourceRecommendedAction.RESTART_CANDIDATE_DRY_RUN
                }
            }
            RuntimeLifecycleAuthorityTier.UNMANAGED,
            RuntimeLifecycleAuthorityTier.QUARANTINE ->
                RuntimeProcessResourceRecommendedAction.OBSERVE
            RuntimeLifecycleAuthorityTier.SYSTEM_CORE,
            RuntimeLifecycleAuthorityTier.PROOT_CORE ->
                RuntimeProcessResourceRecommendedAction.NO_ACTION_CORE_PROTECTED
            RuntimeLifecycleAuthorityTier.PROOT_ELASTIC ->
                RuntimeProcessResourceRecommendedAction.OBSERVE
        }
    }

    private fun overLimitSuppressionReason(
        tier: RuntimeLifecycleAuthorityTier,
        root: RuntimeRootSnapshot
    ): String {
        return when (tier) {
            RuntimeLifecycleAuthorityTier.USER_LOCKED ->
                "over_limit_user_locked_restart_candidate_dry_run_only_no_execution"
            RuntimeLifecycleAuthorityTier.FOREGROUND ->
                "foreground_over_limit_warning_only_no_reclaim_while_foreground"
            RuntimeLifecycleAuthorityTier.LEASE ->
                "lease_over_limit_reclaim_candidate_dry_run_only_existing_reclaimer_boundary_unchanged"
            RuntimeLifecycleAuthorityTier.UNMANAGED ->
                "unmanaged_observe_only_no_auto_registration_or_kill"
            RuntimeLifecycleAuthorityTier.QUARANTINE ->
                "quarantine_tier_observe_only_no_real_quarantine_execution"
            RuntimeLifecycleAuthorityTier.PROOT_ELASTIC ->
                "proot_elastic_observe_only_capacity_policy_owns_memory"
            RuntimeLifecycleAuthorityTier.SYSTEM_CORE,
            RuntimeLifecycleAuthorityTier.PROOT_CORE ->
                "core_or_proot_core_protected"
        } + ",quarantineAfterFailures=${root.processUnitQuarantineAfterFailures.coerceAtLeast(0)}"
    }

    private fun thresholdKb(limitKb: Long, ratio: Double): Long {
        val safeRatio = ratio.takeIf { it.isFinite() }?.coerceAtLeast(0.0) ?: 1.0
        return ceil(limitKb.toDouble() * safeRatio)
            .toLong()
            .coerceAtLeast(1L)
    }
}

private fun String?.toResourceWatchEnvValue(): String {
    if (this.isNullOrBlank()) return "none"
    return trim()
        .replace(Regex("[\\r\\n\\t ]+"), "_")
        .replace(Regex("[^A-Za-z0-9._:@/+,-]"), "_")
        .take(240)
}
