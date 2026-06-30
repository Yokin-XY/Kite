package com.kite.app.foundation.runtime

enum class RuntimeLifecycleFinalAction {
    OBSERVE,
    KEEP_RUNNING,
    WAIT_FOR_USER_CONFIRMATION,
    WARN_RESOURCE_NEAR_LIMIT,
    RESTART_CANDIDATE_DRY_RUN,
    QUARANTINE_CANDIDATE_DRY_RUN,
    RECLAIM_CANDIDATE_DRY_RUN,
    CORE_RECOVERY_DRY_RUN,
    EXPECTED_STOP_CONFIRMED,
    NO_ACTION_UNMANAGED,
    NO_ACTION_UNLIMITED,
    NO_ACTION_AMBIGUOUS,
    NO_ACTION_CORE_PROTECTED
}

enum class RuntimeLifecycleFinalActionMode {
    OBSERVE_ONLY,
    HEALTH_ONLY,
    DRY_RUN,
    REAL_RUNTIME_EXISTING_PATH_ONLY,
    BLOCKED
}

enum class RuntimeLifecycleExecutorBoundary {
    NONE_OBSERVE_ONLY,
    BACKGROUND_RUNTIME_HOST,
    RUNTIME_RECLAIMER,
    MEMORY_ADMISSION,
    PROOT_CAPACITY_LIFECYCLE,
    USER_CONFIRMATION_REQUIRED,
    NOT_EXECUTABLE_AMBIGUOUS,
    NOT_EXECUTABLE_CORE_PROTECTED,
    NOT_EXECUTABLE_UNMANAGED
}

data class RuntimeLifecycleActionPlanEntry(
    val rootKey: String,
    val unitId: String,
    val effectiveTier: RuntimeLifecycleAuthorityTier,
    val matchState: RuntimeProcessUnitMatchState,
    val authoritySource: String,
    val lifecycleState: RuntimeLifecycleMatrixState,
    val resourceState: RuntimeProcessResourceMemoryState =
        RuntimeProcessResourceMemoryState.DRY_RUN_ONLY,
    val episodeState: RuntimeResourceEpisodeState = RuntimeResourceEpisodeState.NONE,
    val finalAction: RuntimeLifecycleFinalAction,
    val finalActionMode: RuntimeLifecycleFinalActionMode,
    val primaryReason: String,
    val suppressionReasons: List<String> = emptyList(),
    val blockedActions: List<String> = emptyList(),
    val allowedFutureActions: List<String> = emptyList(),
    val requiresUserConfirmation: Boolean = false,
    val isExecutableNow: Boolean = false,
    val executorBoundary: RuntimeLifecycleExecutorBoundary
)

data class RuntimeLifecycleActionPlannerSnapshot(
    val mode: String = "runtime_lifecycle_action_planner_v0",
    val enabled: Boolean = true,
    val enforcementEnabled: Boolean = false,
    val actionPlanCount: Int = 0,
    val observeOnlyCount: Int = 0,
    val dryRunActionCount: Int = 0,
    val blockedActionCount: Int = 0,
    val requiresUserConfirmationCount: Int = 0,
    val resourceWarningActionCount: Int = 0,
    val restartCandidateDryRunCount: Int = 0,
    val quarantineCandidateDryRunCount: Int = 0,
    val reclaimCandidateDryRunCount: Int = 0,
    val coreRecoveryDryRunCount: Int = 0,
    val entries: List<RuntimeLifecycleActionPlanEntry> = emptyList(),
    val boundary: String =
        "planner_outputs_dry_run_only_no_executor_calls_or_policy_enforcement"
) {
    fun toEnvText(maxItems: Int = 8): String {
        return buildString {
            appendLine("runtime_lifecycle_action_planner_mode=${mode.toActionPlannerEnvValue()}")
            appendLine("runtime_lifecycle_action_planner_enabled=$enabled")
            appendLine("runtime_lifecycle_action_planner_enforcement_enabled=$enforcementEnabled")
            appendLine("runtime_lifecycle_action_planner_action_plan_count=$actionPlanCount")
            appendLine("runtime_lifecycle_action_planner_observe_only_count=$observeOnlyCount")
            appendLine("runtime_lifecycle_action_planner_dry_run_action_count=$dryRunActionCount")
            appendLine("runtime_lifecycle_action_planner_blocked_action_count=$blockedActionCount")
            appendLine("runtime_lifecycle_action_planner_requires_user_confirmation_count=$requiresUserConfirmationCount")
            appendLine("runtime_lifecycle_action_planner_resource_warning_action_count=$resourceWarningActionCount")
            appendLine("runtime_lifecycle_action_planner_restart_candidate_dry_run_count=$restartCandidateDryRunCount")
            appendLine("runtime_lifecycle_action_planner_quarantine_candidate_dry_run_count=$quarantineCandidateDryRunCount")
            appendLine("runtime_lifecycle_action_planner_reclaim_candidate_dry_run_count=$reclaimCandidateDryRunCount")
            appendLine("runtime_lifecycle_action_planner_core_recovery_dry_run_count=$coreRecoveryDryRunCount")
            entries.take(maxItems).forEachIndexed { index, entry ->
                val prefix = "runtime_lifecycle_action_planner_unit_${index + 1}"
                appendLine("${prefix}_id=${entry.unitId.toActionPlannerEnvValue()}")
                appendLine("${prefix}_effective_tier=${entry.effectiveTier.name}")
                appendLine("${prefix}_match_state=${entry.matchState.name}")
                appendLine("${prefix}_authority_source=${entry.authoritySource.toActionPlannerEnvValue()}")
                appendLine("${prefix}_lifecycle_state=${entry.lifecycleState.name}")
                appendLine("${prefix}_resource_state=${entry.resourceState.name}")
                appendLine("${prefix}_episode_state=${entry.episodeState.name}")
                appendLine("${prefix}_final_action=${entry.finalAction.name}")
                appendLine("${prefix}_final_action_mode=${entry.finalActionMode.name}")
                appendLine("${prefix}_primary_reason=${entry.primaryReason.toActionPlannerEnvValue()}")
                appendLine("${prefix}_suppression_reasons=${entry.suppressionReasons.joinToString(",").toActionPlannerEnvValue()}")
                appendLine("${prefix}_blocked_actions=${entry.blockedActions.joinToString(",").toActionPlannerEnvValue()}")
                appendLine("${prefix}_allowed_future_actions=${entry.allowedFutureActions.joinToString(",").toActionPlannerEnvValue()}")
                appendLine("${prefix}_requires_user_confirmation=${entry.requiresUserConfirmation}")
                appendLine("${prefix}_is_executable_now=${entry.isExecutableNow}")
                appendLine("${prefix}_executor_boundary=${entry.executorBoundary.name}")
            }
            appendLine("runtime_lifecycle_action_planner_boundary=${boundary.toActionPlannerEnvValue()}")
        }
    }
}

object RuntimeLifecycleActionPlanner {
    fun evaluate(
        roots: List<RuntimeRootSnapshot>,
        authorityMatrix: RuntimeLifecycleAuthorityMatrixSnapshot =
            RuntimeLifecycleAuthorityMatrix.evaluate(roots),
        resourceWatch: RuntimeProcessResourceWatchSnapshot =
            RuntimeProcessResourceWatch.evaluate(roots, authorityMatrix),
        resourceEventLedger: RuntimeResourceEventLedgerSnapshot =
            RuntimeResourceEventLedgerSnapshot()
    ): RuntimeLifecycleActionPlannerSnapshot {
        val authorityByRoot = authorityMatrix.entries.associateBy { it.rootKey }
        val resourceByRoot = resourceWatch.entries.associateBy { it.rootKey }
        val episodeByUnit = resourceEventLedger.entries.associateBy { it.unitId }
        val entries = roots.map { root ->
            val authority = authorityByRoot[root.ownershipKey]
                ?: RuntimeLifecycleAuthorityMatrix.evaluateRoot(root)
            val resource = resourceByRoot[root.ownershipKey]
            val unitId = resource?.unitId
                ?: authority.unitId.takeIf { it != "none" }
                ?: root.processUnitId
                ?: "unmanaged:${root.ownershipKey}"
            val episode = episodeByUnit[unitId]
                ?: resource?.let { episodeByUnit[it.unitId] }
            planRoot(
                root = root,
                unitId = unitId,
                authority = authority,
                resource = resource,
                episode = episode
            )
        }
        return RuntimeLifecycleActionPlannerSnapshot(
            actionPlanCount = entries.size,
            observeOnlyCount = entries.count {
                it.finalActionMode == RuntimeLifecycleFinalActionMode.OBSERVE_ONLY
            },
            dryRunActionCount = entries.count {
                it.finalActionMode == RuntimeLifecycleFinalActionMode.DRY_RUN ||
                    it.finalActionMode == RuntimeLifecycleFinalActionMode.REAL_RUNTIME_EXISTING_PATH_ONLY
            },
            blockedActionCount = entries.count {
                it.finalActionMode == RuntimeLifecycleFinalActionMode.BLOCKED
            },
            requiresUserConfirmationCount = entries.count { it.requiresUserConfirmation },
            resourceWarningActionCount = entries.count {
                it.finalAction == RuntimeLifecycleFinalAction.WARN_RESOURCE_NEAR_LIMIT
            },
            restartCandidateDryRunCount = entries.count {
                it.finalAction == RuntimeLifecycleFinalAction.RESTART_CANDIDATE_DRY_RUN
            },
            quarantineCandidateDryRunCount = entries.count {
                it.finalAction == RuntimeLifecycleFinalAction.QUARANTINE_CANDIDATE_DRY_RUN
            },
            reclaimCandidateDryRunCount = entries.count {
                it.finalAction == RuntimeLifecycleFinalAction.RECLAIM_CANDIDATE_DRY_RUN
            },
            coreRecoveryDryRunCount = entries.count {
                it.finalAction == RuntimeLifecycleFinalAction.CORE_RECOVERY_DRY_RUN
            },
            entries = entries
        )
    }

    fun planRoot(
        root: RuntimeRootSnapshot,
        unitId: String,
        authority: RuntimeLifecycleAuthorityMatrixEntry,
        resource: RuntimeProcessResourceWatchEntry?,
        episode: RuntimeResourceEventLedgerEntry?
    ): RuntimeLifecycleActionPlanEntry {
        val decision = decide(root, authority, resource, episode)
        return RuntimeLifecycleActionPlanEntry(
            rootKey = root.ownershipKey,
            unitId = unitId,
            effectiveTier = authority.effectiveTier,
            matchState = authority.matchState,
            authoritySource = authority.authoritySource,
            lifecycleState = authority.lifecycleState,
            resourceState = resource?.memoryState ?: RuntimeProcessResourceMemoryState.DRY_RUN_ONLY,
            episodeState = episode?.episodeState ?: RuntimeResourceEpisodeState.NONE,
            finalAction = decision.finalAction,
            finalActionMode = decision.finalActionMode,
            primaryReason = decision.primaryReason,
            suppressionReasons = suppressionReasons(authority, resource, episode),
            blockedActions = blockedActions(root, authority, resource, episode, decision.finalAction),
            allowedFutureActions = allowedFutureActions(authority, resource, episode, decision.finalAction),
            requiresUserConfirmation =
                decision.finalAction == RuntimeLifecycleFinalAction.WAIT_FOR_USER_CONFIRMATION,
            isExecutableNow = false,
            executorBoundary = decision.executorBoundary
        )
    }

    private fun decide(
        root: RuntimeRootSnapshot,
        authority: RuntimeLifecycleAuthorityMatrixEntry,
        resource: RuntimeProcessResourceWatchEntry?,
        episode: RuntimeResourceEventLedgerEntry?
    ): PlannerDecision {
        if (authority.lifecycleState == RuntimeLifecycleMatrixState.CORE_RECOVERY_REQUIRED) {
            return PlannerDecision(
                finalAction = RuntimeLifecycleFinalAction.CORE_RECOVERY_DRY_RUN,
                finalActionMode = RuntimeLifecycleFinalActionMode.REAL_RUNTIME_EXISTING_PATH_ONLY,
                executorBoundary = if (authority.effectiveTier == RuntimeLifecycleAuthorityTier.PROOT_CORE) {
                    RuntimeLifecycleExecutorBoundary.PROOT_CAPACITY_LIFECYCLE
                } else {
                    RuntimeLifecycleExecutorBoundary.BACKGROUND_RUNTIME_HOST
                },
                primaryReason = "core_missing_existing_recovery_path_only_planner_does_not_execute"
            )
        }

        if (isCoreProtected(authority.effectiveTier)) {
            return PlannerDecision(
                finalAction = RuntimeLifecycleFinalAction.NO_ACTION_CORE_PROTECTED,
                finalActionMode = RuntimeLifecycleFinalActionMode.BLOCKED,
                executorBoundary = RuntimeLifecycleExecutorBoundary.NOT_EXECUTABLE_CORE_PROTECTED,
                primaryReason =
                    "system_core_or_proot_core_protected_from_manifest_resource_reclaimer_actions"
            )
        }

        if (authority.lifecycleState == RuntimeLifecycleMatrixState.STOPPED_EXPECTED) {
            return PlannerDecision(
                finalAction = RuntimeLifecycleFinalAction.EXPECTED_STOP_CONFIRMED,
                finalActionMode = RuntimeLifecycleFinalActionMode.HEALTH_ONLY,
                executorBoundary = RuntimeLifecycleExecutorBoundary.NONE_OBSERVE_ONLY,
                primaryReason = "expected_stop_token_or_policy_confirmed_no_crash_recovery"
            )
        }

        if (authority.lifecycleState == RuntimeLifecycleMatrixState.WAIT_CONFIRM_RESTART) {
            return PlannerDecision(
                finalAction = RuntimeLifecycleFinalAction.WAIT_FOR_USER_CONFIRMATION,
                finalActionMode = RuntimeLifecycleFinalActionMode.BLOCKED,
                executorBoundary = RuntimeLifecycleExecutorBoundary.USER_CONFIRMATION_REQUIRED,
                primaryReason = "manual_kill_policy_wait_confirm_blocks_auto_recovery"
            )
        }

        if (authority.matchState == RuntimeProcessUnitMatchState.MATCH_AMBIGUOUS ||
            resource?.memoryState == RuntimeProcessResourceMemoryState.AMBIGUOUS_MATCH_NO_ENFORCEMENT ||
            episode?.episodeState == RuntimeResourceEpisodeState.SUPPRESSED_AMBIGUOUS
        ) {
            return PlannerDecision(
                finalAction = RuntimeLifecycleFinalAction.NO_ACTION_AMBIGUOUS,
                finalActionMode = RuntimeLifecycleFinalActionMode.BLOCKED,
                executorBoundary = RuntimeLifecycleExecutorBoundary.NOT_EXECUTABLE_AMBIGUOUS,
                primaryReason = "ambiguous_match_blocks_real_recovery_reclaim_and_resource_enforcement"
            )
        }

        if (resource?.unlimitedMemory == true ||
            resource?.memoryState == RuntimeProcessResourceMemoryState.OVER_LIMIT_BUT_UNLIMITED ||
            episode?.episodeState == RuntimeResourceEpisodeState.SUPPRESSED_UNLIMITED
        ) {
            return PlannerDecision(
                finalAction = RuntimeLifecycleFinalAction.NO_ACTION_UNLIMITED,
                finalActionMode = RuntimeLifecycleFinalActionMode.OBSERVE_ONLY,
                executorBoundary = RuntimeLifecycleExecutorBoundary.NONE_OBSERVE_ONLY,
                primaryReason = "unlimited_memory_declared_resource_limits_do_not_trigger_enforcement"
            )
        }

        if (authority.effectiveTier == RuntimeLifecycleAuthorityTier.UNMANAGED) {
            return PlannerDecision(
                finalAction = RuntimeLifecycleFinalAction.NO_ACTION_UNMANAGED,
                finalActionMode = RuntimeLifecycleFinalActionMode.OBSERVE_ONLY,
                executorBoundary = RuntimeLifecycleExecutorBoundary.NOT_EXECUTABLE_UNMANAGED,
                primaryReason = "unmanaged_ubuntu_process_observe_only_no_auto_registration"
            )
        }

        if (episode?.episodeState == RuntimeResourceEpisodeState.QUARANTINE_CANDIDATE_DRY_RUN ||
            resource?.recommendedResourceAction ==
            RuntimeProcessResourceRecommendedAction.QUARANTINE_CANDIDATE_DRY_RUN
        ) {
            return PlannerDecision(
                finalAction = RuntimeLifecycleFinalAction.QUARANTINE_CANDIDATE_DRY_RUN,
                finalActionMode = RuntimeLifecycleFinalActionMode.DRY_RUN,
                executorBoundary = RuntimeLifecycleExecutorBoundary.BACKGROUND_RUNTIME_HOST,
                primaryReason = "resource_episode_reached_quarantine_candidate_dry_run_only"
            )
        }

        if (episode?.episodeState == RuntimeResourceEpisodeState.RESTART_CANDIDATE_DRY_RUN ||
            resource?.recommendedResourceAction ==
            RuntimeProcessResourceRecommendedAction.RESTART_CANDIDATE_DRY_RUN
        ) {
            return PlannerDecision(
                finalAction = RuntimeLifecycleFinalAction.RESTART_CANDIDATE_DRY_RUN,
                finalActionMode = RuntimeLifecycleFinalActionMode.DRY_RUN,
                executorBoundary = RuntimeLifecycleExecutorBoundary.BACKGROUND_RUNTIME_HOST,
                primaryReason = "resource_over_limit_restart_candidate_dry_run_only"
            )
        }

        if (authority.lifecycleState == RuntimeLifecycleMatrixState.AUTO_RESTART_ALLOWED ||
            authority.lifecycleState == RuntimeLifecycleMatrixState.STOPPED_CRASH_SUSPECTED
        ) {
            return PlannerDecision(
                finalAction = RuntimeLifecycleFinalAction.RESTART_CANDIDATE_DRY_RUN,
                finalActionMode = RuntimeLifecycleFinalActionMode.REAL_RUNTIME_EXISTING_PATH_ONLY,
                executorBoundary = RuntimeLifecycleExecutorBoundary.BACKGROUND_RUNTIME_HOST,
                primaryReason = "stop_reconciliation_allows_existing_background_recovery_path_only"
            )
        }

        if (resource?.recommendedResourceAction ==
            RuntimeProcessResourceRecommendedAction.RECLAIM_CANDIDATE_DRY_RUN ||
            authority.recommendedAction == RuntimeLifecycleRecommendedAction.REVIEW_RECLAIM ||
            authority.recommendedAction == RuntimeLifecycleRecommendedAction.REVIEW_LEASE_CLEANUP
        ) {
            return PlannerDecision(
                finalAction = RuntimeLifecycleFinalAction.RECLAIM_CANDIDATE_DRY_RUN,
                finalActionMode = RuntimeLifecycleFinalActionMode.DRY_RUN,
                executorBoundary = RuntimeLifecycleExecutorBoundary.RUNTIME_RECLAIMER,
                primaryReason = "lease_or_reclaimer_candidate_dry_run_existing_reclaimer_boundary_only"
            )
        }

        if (resource?.memoryState == RuntimeProcessResourceMemoryState.NEAR_LIMIT ||
            resource?.recommendedResourceAction == RuntimeProcessResourceRecommendedAction.WARN_ONLY
        ) {
            return PlannerDecision(
                finalAction = RuntimeLifecycleFinalAction.WARN_RESOURCE_NEAR_LIMIT,
                finalActionMode = RuntimeLifecycleFinalActionMode.HEALTH_ONLY,
                executorBoundary = RuntimeLifecycleExecutorBoundary.NONE_OBSERVE_ONLY,
                primaryReason = "resource_warning_health_only_no_notification_or_reclaim"
            )
        }

        if (root.isRunning || authority.lifecycleState == RuntimeLifecycleMatrixState.RUNNING) {
            return PlannerDecision(
                finalAction = RuntimeLifecycleFinalAction.KEEP_RUNNING,
                finalActionMode = RuntimeLifecycleFinalActionMode.OBSERVE_ONLY,
                executorBoundary = RuntimeLifecycleExecutorBoundary.NONE_OBSERVE_ONLY,
                primaryReason = "running_with_no_higher_priority_action"
            )
        }

        return PlannerDecision(
            finalAction = RuntimeLifecycleFinalAction.OBSERVE,
            finalActionMode = RuntimeLifecycleFinalActionMode.OBSERVE_ONLY,
            executorBoundary = RuntimeLifecycleExecutorBoundary.NONE_OBSERVE_ONLY,
            primaryReason = "no_action_required_observe_only"
        )
    }

    private fun isCoreProtected(tier: RuntimeLifecycleAuthorityTier): Boolean {
        return tier == RuntimeLifecycleAuthorityTier.SYSTEM_CORE ||
            tier == RuntimeLifecycleAuthorityTier.PROOT_CORE
    }

    private fun suppressionReasons(
        authority: RuntimeLifecycleAuthorityMatrixEntry,
        resource: RuntimeProcessResourceWatchEntry?,
        episode: RuntimeResourceEventLedgerEntry?
    ): List<String> {
        return listOfNotNull(
            authority.suppressionReason,
            resource?.resourceSuppressionReason,
            episode?.suppressionReason
        )
            .filter { it.isNotBlank() && it != "none" }
            .distinct()
    }

    private fun blockedActions(
        root: RuntimeRootSnapshot,
        authority: RuntimeLifecycleAuthorityMatrixEntry,
        resource: RuntimeProcessResourceWatchEntry?,
        episode: RuntimeResourceEventLedgerEntry?,
        finalAction: RuntimeLifecycleFinalAction
    ): List<String> {
        val blocked = linkedSetOf<String>()
        if (isCoreProtected(authority.effectiveTier)) {
            blocked += "manifest_override"
            blocked += "resource_enforcement"
            blocked += "lease_reclaim"
        }
        if (authority.lifecycleState == RuntimeLifecycleMatrixState.WAIT_CONFIRM_RESTART ||
            root.processUnitManualKillPolicy == RuntimeProcessUnitManualKillPolicy.WAIT_CONFIRM
        ) {
            blocked += "auto_recovery_until_user_confirmation"
        }
        if (authority.matchState == RuntimeProcessUnitMatchState.MATCH_AMBIGUOUS ||
            episode?.episodeState == RuntimeResourceEpisodeState.SUPPRESSED_AMBIGUOUS
        ) {
            blocked += "restart_due_to_ambiguous_match"
            blocked += "reclaim_due_to_ambiguous_match"
            blocked += "quarantine_due_to_ambiguous_match"
        }
        if (resource?.unlimitedMemory == true ||
            episode?.episodeState == RuntimeResourceEpisodeState.SUPPRESSED_UNLIMITED
        ) {
            blocked += "memory_enforcement_for_unlimited_unit"
        }
        if (authority.effectiveTier == RuntimeLifecycleAuthorityTier.UNMANAGED) {
            blocked += "auto_registration"
            blocked += "auto_recovery"
            blocked += "runtime_reclaim"
        }
        if (authority.effectiveTier == RuntimeLifecycleAuthorityTier.FOREGROUND) {
            blocked += "lease_cleanup_while_foreground"
        }
        if (finalAction == RuntimeLifecycleFinalAction.QUARANTINE_CANDIDATE_DRY_RUN) {
            blocked += "real_quarantine_execution_this_phase"
        }
        if (finalAction == RuntimeLifecycleFinalAction.RESTART_CANDIDATE_DRY_RUN &&
            resource?.memoryState == RuntimeProcessResourceMemoryState.OVER_LIMIT
        ) {
            blocked += "real_user_locked_over_limit_restart_this_phase"
        }
        return blocked.toList()
    }

    private fun allowedFutureActions(
        authority: RuntimeLifecycleAuthorityMatrixEntry,
        resource: RuntimeProcessResourceWatchEntry?,
        episode: RuntimeResourceEventLedgerEntry?,
        finalAction: RuntimeLifecycleFinalAction
    ): List<String> {
        val allowed = linkedSetOf<String>()
        when (finalAction) {
            RuntimeLifecycleFinalAction.WAIT_FOR_USER_CONFIRMATION ->
                allowed += "ui_may_confirm_restart_or_unlock_later"
            RuntimeLifecycleFinalAction.CORE_RECOVERY_DRY_RUN -> {
                if (authority.effectiveTier == RuntimeLifecycleAuthorityTier.PROOT_CORE) {
                    allowed += "proot_capacity_lifecycle_may_review_default_proot_recovery"
                } else {
                    allowed += "background_runtime_host_existing_core_recovery_path"
                }
            }
            RuntimeLifecycleFinalAction.RESTART_CANDIDATE_DRY_RUN -> {
                if (authority.recoveryAllowed) {
                    allowed += "background_runtime_host_existing_recovery_path_after_policy_gate"
                }
            }
            RuntimeLifecycleFinalAction.RECLAIM_CANDIDATE_DRY_RUN -> {
                if (authority.reclaimAllowed) {
                    allowed += RuntimeLifecycleLeasePoolAdmission.RECLAIMER_ACTION
                }
            }
            RuntimeLifecycleFinalAction.WARN_RESOURCE_NEAR_LIMIT ->
                allowed += "health_warning_only"
            RuntimeLifecycleFinalAction.NO_ACTION_UNMANAGED ->
                allowed += "ubuntu_user_controls_process_with_ps_kill_pkill"
            RuntimeLifecycleFinalAction.NO_ACTION_UNLIMITED ->
                allowed += "observe_unlimited_memory_unit"
            RuntimeLifecycleFinalAction.KEEP_RUNNING ->
                allowed += "continue_observation"
            else -> Unit
        }
        if (episode?.episodeState == RuntimeResourceEpisodeState.RECOVERED) {
            allowed += "resource_episode_recovery_cooldown_observation"
        }
        if (resource?.recommendedResourceAction == RuntimeProcessResourceRecommendedAction.WARN_ONLY) {
            allowed += "resource_watch_warning_only"
        }
        return allowed.toList()
    }

    private data class PlannerDecision(
        val finalAction: RuntimeLifecycleFinalAction,
        val finalActionMode: RuntimeLifecycleFinalActionMode,
        val executorBoundary: RuntimeLifecycleExecutorBoundary,
        val primaryReason: String
    )
}

private fun String?.toActionPlannerEnvValue(): String {
    if (this.isNullOrBlank()) return "none"
    return trim()
        .replace(Regex("[\\r\\n\\t ]+"), "_")
        .replace(Regex("[^A-Za-z0-9._:@/+,-]"), "_")
        .take(240)
}
