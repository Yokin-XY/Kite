package com.kftest.app.foundation.runtime

enum class RuntimeLifecycleDiagnosticReviewStatus {
    PASS,
    PASS_WITH_WARNINGS,
    BLOCKED_BY_DESIGN,
    FAILED_INVARIANT,
    NEEDS_REAL_DEVICE_VALIDATION
}

data class RuntimeLifecycleDiagnosticReviewEntry(
    val reviewStatus: RuntimeLifecycleDiagnosticReviewStatus,
    val scenarioName: String,
    val unitId: String,
    val effectiveTier: RuntimeLifecycleAuthorityTier? = null,
    val matchState: RuntimeProcessUnitMatchState? = null,
    val authoritySource: String = "none",
    val lifecycleState: RuntimeLifecycleMatrixState? = null,
    val memoryState: RuntimeProcessResourceMemoryState? = null,
    val episodeState: RuntimeResourceEpisodeState? = null,
    val finalAction: RuntimeLifecycleFinalAction? = null,
    val inboxStatus: String = "none",
    val executorBoundary: RuntimeLifecycleExecutorBoundary? = null,
    val isExecutableNow: Boolean = false,
    val ubuntuCompatibilityStatus: String,
    val coreProtectionStatus: String,
    val warnings: List<String> = emptyList(),
    val blockingIssues: List<String> = emptyList(),
    val invariantChecks: List<String> = RuntimeLifecycleDiagnosticReview.DEFAULT_INVARIANT_CHECKS,
    val realDeviceValidationMarkers: List<String> = emptyList()
)

data class RuntimeLifecycleDiagnosticReviewSnapshot(
    val mode: String = "runtime_lifecycle_diagnostic_review_v0",
    val enabled: Boolean = true,
    val enforcementEnabled: Boolean = false,
    val scenarioReviewCount: Int = 0,
    val scenarioPassCount: Int = 0,
    val scenarioPassWithWarningsCount: Int = 0,
    val scenarioBlockedByDesignCount: Int = 0,
    val scenarioFailedInvariantCount: Int = 0,
    val scenarioNeedsRealDeviceValidationCount: Int = 0,
    val entries: List<RuntimeLifecycleDiagnosticReviewEntry> = emptyList(),
    val boundary: String =
        "review_summary_only_no_host_terminator_proot_capacity_executor_or_runtime_action_calls"
) {
    fun toEnvText(maxItems: Int = 12): String {
        return buildString {
            appendLine("runtime_lifecycle_diagnostic_review_mode=${mode.toDiagnosticReviewEnvValue()}")
            appendLine("runtime_lifecycle_diagnostic_review_enabled=$enabled")
            appendLine("runtime_lifecycle_diagnostic_review_enforcement_enabled=$enforcementEnabled")
            appendLine("runtime_lifecycle_diagnostic_review_scenario_review_count=$scenarioReviewCount")
            appendLine("runtime_lifecycle_diagnostic_review_scenario_pass_count=$scenarioPassCount")
            appendLine("runtime_lifecycle_diagnostic_review_scenario_pass_with_warnings_count=$scenarioPassWithWarningsCount")
            appendLine("runtime_lifecycle_diagnostic_review_scenario_blocked_by_design_count=$scenarioBlockedByDesignCount")
            appendLine("runtime_lifecycle_diagnostic_review_scenario_failed_invariant_count=$scenarioFailedInvariantCount")
            appendLine("runtime_lifecycle_diagnostic_review_scenario_needs_real_device_validation_count=$scenarioNeedsRealDeviceValidationCount")
            entries.take(maxItems).forEachIndexed { index, entry ->
                val prefix = "runtime_lifecycle_diagnostic_review_scenario_${index + 1}"
                appendLine("${prefix}_name=${entry.scenarioName.toDiagnosticReviewEnvValue()}")
                appendLine("${prefix}_review_status=${entry.reviewStatus.name}")
                appendLine("${prefix}_unit_id=${entry.unitId.toDiagnosticReviewEnvValue()}")
                appendLine("${prefix}_effective_tier=${entry.effectiveTier?.name ?: "none"}")
                appendLine("${prefix}_match_state=${entry.matchState?.name ?: "none"}")
                appendLine("${prefix}_authority_source=${entry.authoritySource.toDiagnosticReviewEnvValue()}")
                appendLine("${prefix}_lifecycle_state=${entry.lifecycleState?.name ?: "none"}")
                appendLine("${prefix}_memory_state=${entry.memoryState?.name ?: "none"}")
                appendLine("${prefix}_episode_state=${entry.episodeState?.name ?: "none"}")
                appendLine("${prefix}_final_action=${entry.finalAction?.name ?: "none"}")
                appendLine("${prefix}_inbox_status=${entry.inboxStatus.toDiagnosticReviewEnvValue()}")
                appendLine("${prefix}_executor_boundary=${entry.executorBoundary?.name ?: "none"}")
                appendLine("${prefix}_is_executable_now=${entry.isExecutableNow}")
                appendLine("${prefix}_ubuntu_compatibility_status=${entry.ubuntuCompatibilityStatus.toDiagnosticReviewEnvValue()}")
                appendLine("${prefix}_core_protection_status=${entry.coreProtectionStatus.toDiagnosticReviewEnvValue()}")
                appendLine("${prefix}_warnings=${entry.warnings.joinToString(";").toDiagnosticReviewEnvValue()}")
                appendLine("${prefix}_blocking_issues=${entry.blockingIssues.joinToString(";").toDiagnosticReviewEnvValue()}")
                appendLine("${prefix}_invariant_checks=${entry.invariantChecks.joinToString(";").toDiagnosticReviewEnvValue()}")
                appendLine("${prefix}_real_device_validation_markers=${entry.realDeviceValidationMarkers.joinToString(";").toDiagnosticReviewEnvValue()}")
            }
            appendLine("runtime_lifecycle_diagnostic_review_boundary=${boundary.toDiagnosticReviewEnvValue()}")
        }
    }
}

object RuntimeLifecycleDiagnosticReview {
    val DEFAULT_INVARIANT_CHECKS = listOf(
        "host_process_terminator_not_called_by_diagnostic_review",
        "proot_capacity_executor_not_called_by_diagnostic_review",
        "proot_capacity_actuator_not_called_by_diagnostic_review",
        "is_executable_now_false",
        "manifest_not_execution_authorization",
        "unmanaged_ubuntu_process_not_auto_privatized",
        "core_and_proot_core_not_downgraded_by_manifest",
        "ambiguous_match_not_real_enforcement",
        "weak_command_contains_not_real_restart_or_reclaim",
        "inbox_is_pending_state_not_executor",
        "health_env_explains_final_authority_source"
    )

    fun evaluate(
        roots: List<RuntimeRootSnapshot>,
        manifest: RuntimeProcessUnitManifest = RuntimeProcessUnitManifest.default(),
        authorityMatrix: RuntimeLifecycleAuthorityMatrixSnapshot =
            RuntimeLifecycleAuthorityMatrix.evaluate(roots),
        resourceWatch: RuntimeProcessResourceWatchSnapshot =
            RuntimeProcessResourceWatch.evaluate(roots, authorityMatrix),
        resourceEventLedger: RuntimeResourceEventLedgerSnapshot =
            RuntimeResourceEventLedgerSnapshot(),
        actionPlanner: RuntimeLifecycleActionPlannerSnapshot =
            RuntimeLifecycleActionPlanner.evaluate(
                roots = roots,
                authorityMatrix = authorityMatrix,
                resourceWatch = resourceWatch,
                resourceEventLedger = resourceEventLedger
            ),
        actionInbox: RuntimeLifecycleActionInboxSnapshot =
            RuntimeLifecycleActionInbox.record(planner = actionPlanner),
        scenarioNameOverrides: Map<String, String> = emptyMap()
    ): RuntimeLifecycleDiagnosticReviewSnapshot {
        val rootsByKey = roots.associateBy { it.ownershipKey }
        val authorityByRoot = authorityMatrix.entries.associateBy { it.rootKey }
        val resourceByRoot = resourceWatch.entries.associateBy { it.rootKey }
        val episodeByUnit = resourceEventLedger.entries.associateBy { it.unitId }
        val entries = actionPlanner.entries.map { plan ->
            val root = rootsByKey[plan.rootKey]
            val authority = authorityByRoot[plan.rootKey]
            val resource = resourceByRoot[plan.rootKey]
            val episode = episodeByUnit[plan.unitId]
            val inboxItem = actionInbox.items.firstOrNull {
                it.unitId == plan.unitId && it.finalAction == plan.finalAction
            }
            val scenarioName = scenarioNameOverrides[plan.rootKey]
                ?: scenarioNameFor(
                    root = root,
                    authority = authority,
                    resource = resource,
                    episode = episode,
                    plan = plan,
                    manifest = manifest
                )
            reviewEntry(
                scenarioName = scenarioName,
                root = root,
                manifest = manifest,
                authority = authority,
                resource = resource,
                episode = episode,
                plan = plan,
                inboxItem = inboxItem
            )
        } + manifestValidationEntry(manifest)
        return snapshot(entries.filterNotNull())
    }

    fun evaluateScenario(
        scenarioName: String,
        roots: List<RuntimeRootSnapshot>,
        manifest: RuntimeProcessUnitManifest = RuntimeProcessUnitManifest.default(),
        previousLedger: RuntimeResourceEventLedgerSnapshot = RuntimeResourceEventLedgerSnapshot(),
        now: Long = 100L
    ): RuntimeLifecycleDiagnosticReviewSnapshot {
        val authority = RuntimeLifecycleAuthorityMatrix.evaluate(roots)
        val watch = RuntimeProcessResourceWatch.evaluate(
            roots = roots,
            authorityMatrix = authority
        )
        val ledger = RuntimeResourceEventLedger.record(
            previous = previousLedger,
            resourceWatch = watch,
            roots = roots,
            now = now
        )
        val planner = RuntimeLifecycleActionPlanner.evaluate(
            roots = roots,
            authorityMatrix = authority,
            resourceWatch = watch,
            resourceEventLedger = ledger
        )
        val inbox = RuntimeLifecycleActionInbox.record(
            planner = planner,
            now = now
        )
        return evaluate(
            roots = roots,
            manifest = manifest,
            authorityMatrix = authority,
            resourceWatch = watch,
            resourceEventLedger = ledger,
            actionPlanner = planner,
            actionInbox = inbox,
            scenarioNameOverrides = roots.associate { it.ownershipKey to scenarioName }
        )
    }

    fun unmanagedUbuntuProcessKilledScenario(): RuntimeLifecycleDiagnosticReviewSnapshot {
        return snapshot(
            listOf(
                reviewEntry(
                    scenarioName = "unmanaged_ubuntu_process_killed",
                    root = null,
                    manifest = RuntimeProcessUnitManifest.default(),
                    authority = null,
                    resource = null,
                    episode = null,
                    plan = null,
                    inboxItem = null,
                    explicitUnitId = "unmanaged:disappeared",
                    explicitTier = RuntimeLifecycleAuthorityTier.UNMANAGED,
                    explicitMatchState = RuntimeProcessUnitMatchState.UNMANAGED_OBSERVED,
                    explicitFinalAction = RuntimeLifecycleFinalAction.NO_ACTION_UNMANAGED,
                    explicitUbuntuCompatibilityStatus =
                        "ubuntu_kill_pkill_removes_process_from_snapshot_no_auto_registration_or_recovery"
                )
            )
        )
    }

    fun snapshot(
        entries: List<RuntimeLifecycleDiagnosticReviewEntry>
    ): RuntimeLifecycleDiagnosticReviewSnapshot {
        val bounded = entries.distinctBy { it.scenarioName to it.unitId }
        return RuntimeLifecycleDiagnosticReviewSnapshot(
            scenarioReviewCount = bounded.size,
            scenarioPassCount = bounded.count {
                it.reviewStatus == RuntimeLifecycleDiagnosticReviewStatus.PASS
            },
            scenarioPassWithWarningsCount = bounded.count {
                it.reviewStatus == RuntimeLifecycleDiagnosticReviewStatus.PASS_WITH_WARNINGS
            },
            scenarioBlockedByDesignCount = bounded.count {
                it.reviewStatus == RuntimeLifecycleDiagnosticReviewStatus.BLOCKED_BY_DESIGN
            },
            scenarioFailedInvariantCount = bounded.count {
                it.reviewStatus == RuntimeLifecycleDiagnosticReviewStatus.FAILED_INVARIANT
            },
            scenarioNeedsRealDeviceValidationCount = bounded.count {
                it.reviewStatus == RuntimeLifecycleDiagnosticReviewStatus.NEEDS_REAL_DEVICE_VALIDATION ||
                    it.realDeviceValidationMarkers.isNotEmpty()
            },
            entries = bounded
        )
    }

    private fun reviewEntry(
        scenarioName: String,
        root: RuntimeRootSnapshot?,
        manifest: RuntimeProcessUnitManifest,
        authority: RuntimeLifecycleAuthorityMatrixEntry?,
        resource: RuntimeProcessResourceWatchEntry?,
        episode: RuntimeResourceEventLedgerEntry?,
        plan: RuntimeLifecycleActionPlanEntry?,
        inboxItem: RuntimeLifecycleActionInboxItem?,
        explicitUnitId: String? = null,
        explicitTier: RuntimeLifecycleAuthorityTier? = null,
        explicitMatchState: RuntimeProcessUnitMatchState? = null,
        explicitFinalAction: RuntimeLifecycleFinalAction? = null,
        explicitUbuntuCompatibilityStatus: String? = null
    ): RuntimeLifecycleDiagnosticReviewEntry {
        val tier = explicitTier ?: plan?.effectiveTier ?: authority?.effectiveTier
        val matchState = explicitMatchState ?: plan?.matchState ?: authority?.matchState
        val finalAction = explicitFinalAction ?: plan?.finalAction
        val isExecutableNow = (plan?.isExecutableNow == true) || (inboxItem?.isExecutableNow == true)
        val blockingIssues = blockingIssuesFor(
            root = root,
            authority = authority,
            resource = resource,
            plan = plan,
            inboxItem = inboxItem,
            tier = tier,
            matchState = matchState,
            finalAction = finalAction
        )
        val warnings = warningsFor(
            scenarioName = scenarioName,
            manifest = manifest,
            authority = authority,
            resource = resource,
            episode = episode,
            plan = plan
        )
        val realDeviceValidationMarkers = realDeviceMarkersFor(
            scenarioName = scenarioName,
            root = root,
            resource = resource,
            plan = plan,
            matchState = matchState
        )
        val status = statusFor(
            scenarioName = scenarioName,
            warnings = warnings,
            blockingIssues = blockingIssues,
            finalActionMode = plan?.finalActionMode,
            realDeviceValidationMarkers = realDeviceValidationMarkers
        )
        return RuntimeLifecycleDiagnosticReviewEntry(
            reviewStatus = status,
            scenarioName = scenarioName,
            unitId = explicitUnitId
                ?: plan?.unitId
                ?: authority?.unitId
                ?: root?.processUnitId
                ?: "none",
            effectiveTier = tier,
            matchState = matchState,
            authoritySource = plan?.authoritySource ?: authority?.authoritySource ?: "none",
            lifecycleState = plan?.lifecycleState ?: authority?.lifecycleState,
            memoryState = plan?.resourceState ?: resource?.memoryState,
            episodeState = plan?.episodeState ?: episode?.episodeState,
            finalAction = finalAction,
            inboxStatus = inboxItem?.severity?.name ?: "none",
            executorBoundary = plan?.executorBoundary,
            isExecutableNow = isExecutableNow,
            ubuntuCompatibilityStatus = explicitUbuntuCompatibilityStatus
                ?: ubuntuCompatibilityStatus(tier, root, finalAction),
            coreProtectionStatus = coreProtectionStatus(tier, manifest),
            warnings = warnings,
            blockingIssues = blockingIssues,
            invariantChecks = invariantChecksFor(isExecutableNow),
            realDeviceValidationMarkers = realDeviceValidationMarkers
        )
    }

    private fun manifestValidationEntry(
        manifest: RuntimeProcessUnitManifest
    ): RuntimeLifecycleDiagnosticReviewEntry? {
        val report = manifest.validationReport
        if (report.warningCount == 0 && report.errorCount == 0 && report.ignoredUnitCount == 0) {
            return null
        }
        val warnings = buildList {
            add("manifest_validation_warning_count=${report.warningCount}")
            add("manifest_validation_error_count=${report.errorCount}")
            add("manifest_ignored_unit_count=${report.ignoredUnitCount}")
            report.messages.take(12).forEach { message ->
                add("${message.severity.name}:${message.unitId}:${message.code}")
            }
        }
        return RuntimeLifecycleDiagnosticReviewEntry(
            reviewStatus = RuntimeLifecycleDiagnosticReviewStatus.PASS_WITH_WARNINGS,
            scenarioName = "invalid_manifest_partial_fallback",
            unitId = "manifest",
            authoritySource = "runtime_process_unit_manifest_validation",
            finalAction = RuntimeLifecycleFinalAction.OBSERVE,
            inboxStatus = "none",
            executorBoundary = RuntimeLifecycleExecutorBoundary.NONE_OBSERVE_ONLY,
            ubuntuCompatibilityStatus =
                "invalid_manifest_units_ignored_valid_units_continue_no_runtime_crash",
            coreProtectionStatus = when {
                report.coreOverrideAttempt && report.prootCoreOverrideAttempt ->
                    "system_core_and_proot_core_override_attempts_ignored"
                report.coreOverrideAttempt -> "system_core_override_attempt_ignored"
                report.prootCoreOverrideAttempt -> "proot_core_override_attempt_ignored"
                else -> "not_core"
            },
            warnings = warnings,
            invariantChecks = invariantChecksFor(isExecutableNow = false)
        )
    }

    private fun scenarioNameFor(
        root: RuntimeRootSnapshot?,
        authority: RuntimeLifecycleAuthorityMatrixEntry?,
        resource: RuntimeProcessResourceWatchEntry?,
        episode: RuntimeResourceEventLedgerEntry?,
        plan: RuntimeLifecycleActionPlanEntry,
        manifest: RuntimeProcessUnitManifest
    ): String {
        return when {
            authority?.effectiveTier == RuntimeLifecycleAuthorityTier.SYSTEM_CORE &&
                authority.lifecycleState == RuntimeLifecycleMatrixState.CORE_RECOVERY_REQUIRED ->
                "system_core_disappeared"
            authority?.effectiveTier == RuntimeLifecycleAuthorityTier.PROOT_CORE &&
                manifest.validationReport.prootCoreOverrideAttempt ->
                "proot_core_manifest_override_attempt"
            plan.lifecycleState == RuntimeLifecycleMatrixState.WAIT_CONFIRM_RESTART ->
                "user_locked_wait_confirm_killed"
            plan.effectiveTier == RuntimeLifecycleAuthorityTier.USER_LOCKED &&
                plan.lifecycleState == RuntimeLifecycleMatrixState.AUTO_RESTART_ALLOWED ->
                "user_locked_auto_restart_crash_suspected"
            plan.effectiveTier == RuntimeLifecycleAuthorityTier.USER_LOCKED &&
                resource?.memoryState == RuntimeProcessResourceMemoryState.NEAR_LIMIT ->
                "user_locked_near_limit"
            plan.effectiveTier == RuntimeLifecycleAuthorityTier.USER_LOCKED &&
                (
                    plan.finalAction == RuntimeLifecycleFinalAction.QUARANTINE_CANDIDATE_DRY_RUN ||
                        episode?.episodeState == RuntimeResourceEpisodeState.QUARANTINE_CANDIDATE_DRY_RUN
                    ) ->
                "user_locked_over_limit_repeated"
            plan.effectiveTier == RuntimeLifecycleAuthorityTier.USER_LOCKED &&
                plan.finalAction == RuntimeLifecycleFinalAction.NO_ACTION_UNLIMITED ->
                "user_locked_unlimited_memory"
            plan.effectiveTier == RuntimeLifecycleAuthorityTier.USER_LOCKED &&
                plan.matchState == RuntimeProcessUnitMatchState.MATCH_AMBIGUOUS ->
                "ambiguous_user_locked_match"
            plan.effectiveTier == RuntimeLifecycleAuthorityTier.LEASE &&
                plan.finalAction == RuntimeLifecycleFinalAction.RECLAIM_CANDIDATE_DRY_RUN ->
                "lease_memory_pressure"
            plan.effectiveTier == RuntimeLifecycleAuthorityTier.FOREGROUND &&
                (
                    resource?.memoryState == RuntimeProcessResourceMemoryState.NEAR_LIMIT ||
                        resource?.memoryState == RuntimeProcessResourceMemoryState.OVER_LIMIT
                    ) ->
                "foreground_active_memory_pressure"
            plan.effectiveTier == RuntimeLifecycleAuthorityTier.UNMANAGED && root?.isRunning == false ->
                "unmanaged_ubuntu_process_killed"
            plan.effectiveTier == RuntimeLifecycleAuthorityTier.UNMANAGED ->
                "unmanaged_ubuntu_process_observed"
            else -> "runtime_lifecycle_${plan.unitId}"
        }
    }

    private fun blockingIssuesFor(
        root: RuntimeRootSnapshot?,
        authority: RuntimeLifecycleAuthorityMatrixEntry?,
        resource: RuntimeProcessResourceWatchEntry?,
        plan: RuntimeLifecycleActionPlanEntry?,
        inboxItem: RuntimeLifecycleActionInboxItem?,
        tier: RuntimeLifecycleAuthorityTier?,
        matchState: RuntimeProcessUnitMatchState?,
        finalAction: RuntimeLifecycleFinalAction?
    ): List<String> {
        val issues = linkedSetOf<String>()
        if (plan?.isExecutableNow == true) {
            issues += "planner_marked_executable_now"
        }
        if (inboxItem?.isExecutableNow == true) {
            issues += "inbox_marked_executable_now"
        }
        if (root?.runtimeKind == com.kftest.app.foundation.service.BackgroundRuntimeKind.CONTAINER_SUPERVISOR &&
            tier != RuntimeLifecycleAuthorityTier.SYSTEM_CORE
        ) {
            issues += "system_core_downgraded_by_non_core_authority"
        }
        if (root?.runtimeKind == com.kftest.app.foundation.service.BackgroundRuntimeKind.PROOT_CAPACITY_WORKER &&
            root.ownerId?.substringAfterLast("-proot-capacity-worker-", "")?.toIntOrNull() == 1 &&
            tier != RuntimeLifecycleAuthorityTier.PROOT_CORE
        ) {
            issues += "proot_core_downgraded_by_manifest_or_snapshot"
        }
        if (matchState == RuntimeProcessUnitMatchState.MATCH_AMBIGUOUS &&
            (authority?.recoveryAllowed == true || authority?.reclaimAllowed == true)
        ) {
            issues += "ambiguous_match_allowed_recovery_or_reclaim"
        }
        if (matchState == RuntimeProcessUnitMatchState.MATCH_AMBIGUOUS &&
            finalAction in setOf(
                RuntimeLifecycleFinalAction.RESTART_CANDIDATE_DRY_RUN,
                RuntimeLifecycleFinalAction.RECLAIM_CANDIDATE_DRY_RUN,
                RuntimeLifecycleFinalAction.QUARANTINE_CANDIDATE_DRY_RUN
            ) &&
            plan?.isExecutableNow == true
        ) {
            issues += "ambiguous_match_entered_real_enforcement"
        }
        if (root?.processUnitMatchSource == RuntimeProcessUnitMatchSource.COMMAND_CONTAINS &&
            finalAction in setOf(
                RuntimeLifecycleFinalAction.RESTART_CANDIDATE_DRY_RUN,
                RuntimeLifecycleFinalAction.RECLAIM_CANDIDATE_DRY_RUN
            ) &&
            plan?.isExecutableNow == true
        ) {
            issues += "weak_command_contains_entered_real_restart_or_reclaim"
        }
        if (tier == RuntimeLifecycleAuthorityTier.UNMANAGED &&
            finalAction !in setOf(
                RuntimeLifecycleFinalAction.NO_ACTION_UNMANAGED,
                RuntimeLifecycleFinalAction.EXPECTED_STOP_CONFIRMED,
                RuntimeLifecycleFinalAction.OBSERVE,
                RuntimeLifecycleFinalAction.KEEP_RUNNING
            )
        ) {
            issues += "unmanaged_process_received_private_runtime_action"
        }
        if (resource?.recommendedResourceAction in setOf(
                RuntimeProcessResourceRecommendedAction.RESTART_CANDIDATE_DRY_RUN,
                RuntimeProcessResourceRecommendedAction.RECLAIM_CANDIDATE_DRY_RUN,
                RuntimeProcessResourceRecommendedAction.QUARANTINE_CANDIDATE_DRY_RUN
            ) &&
            plan?.isExecutableNow == true
        ) {
            issues += "resource_watch_diagnostic_became_executable"
        }
        return issues.toList()
    }

    private fun warningsFor(
        scenarioName: String,
        manifest: RuntimeProcessUnitManifest,
        authority: RuntimeLifecycleAuthorityMatrixEntry?,
        resource: RuntimeProcessResourceWatchEntry?,
        episode: RuntimeResourceEventLedgerEntry?,
        plan: RuntimeLifecycleActionPlanEntry?
    ): List<String> {
        val warnings = linkedSetOf<String>()
        if (scenarioName == "proot_core_manifest_override_attempt" &&
            manifest.validationReport.prootCoreOverrideAttempt
        ) {
            warnings += "manifest_proot_core_override_attempt_ignored"
        }
        if (manifest.validationReport.coreOverrideAttempt &&
            authority?.effectiveTier == RuntimeLifecycleAuthorityTier.SYSTEM_CORE
        ) {
            warnings += "manifest_system_core_override_attempt_ignored"
        }
        when (plan?.finalAction) {
            RuntimeLifecycleFinalAction.WARN_RESOURCE_NEAR_LIMIT ->
                warnings += "resource_warning_health_only"
            RuntimeLifecycleFinalAction.RESTART_CANDIDATE_DRY_RUN ->
                warnings += "restart_candidate_dry_run_no_executor_call"
            RuntimeLifecycleFinalAction.QUARANTINE_CANDIDATE_DRY_RUN ->
                warnings += "quarantine_candidate_dry_run_no_quarantine_execution"
            RuntimeLifecycleFinalAction.RECLAIM_CANDIDATE_DRY_RUN ->
                warnings += "reclaim_candidate_dry_run_no_reclaimer_execution"
            RuntimeLifecycleFinalAction.CORE_RECOVERY_DRY_RUN ->
                warnings += "core_recovery_is_reviewed_but_not_executed_by_diagnostic_review"
            else -> Unit
        }
        resource?.resourceSuppressionReason
            ?.takeIf { it.isNotBlank() && it != "none" }
            ?.let { warnings += it }
        episode?.suppressionReason
            ?.takeIf { it.isNotBlank() && it != "none" }
            ?.let { warnings += it }
        plan?.suppressionReasons
            ?.filter { it.isNotBlank() && it != "none" }
            ?.forEach { warnings += it }
        return warnings.toList()
    }

    private fun statusFor(
        scenarioName: String,
        warnings: List<String>,
        blockingIssues: List<String>,
        finalActionMode: RuntimeLifecycleFinalActionMode?,
        realDeviceValidationMarkers: List<String>
    ): RuntimeLifecycleDiagnosticReviewStatus {
        if (blockingIssues.isNotEmpty()) {
            return RuntimeLifecycleDiagnosticReviewStatus.FAILED_INVARIANT
        }
        if (scenarioName == "unmanaged_ubuntu_process_killed" &&
            realDeviceValidationMarkers.isNotEmpty()
        ) {
            return RuntimeLifecycleDiagnosticReviewStatus.NEEDS_REAL_DEVICE_VALIDATION
        }
        if (finalActionMode == RuntimeLifecycleFinalActionMode.BLOCKED) {
            return RuntimeLifecycleDiagnosticReviewStatus.BLOCKED_BY_DESIGN
        }
        return if (warnings.isEmpty()) {
            RuntimeLifecycleDiagnosticReviewStatus.PASS
        } else {
            RuntimeLifecycleDiagnosticReviewStatus.PASS_WITH_WARNINGS
        }
    }

    private fun realDeviceMarkersFor(
        scenarioName: String,
        root: RuntimeRootSnapshot?,
        resource: RuntimeProcessResourceWatchEntry?,
        plan: RuntimeLifecycleActionPlanEntry?,
        matchState: RuntimeProcessUnitMatchState?
    ): List<String> {
        val markers = linkedSetOf<String>()
        if (scenarioName == "unmanaged_ubuntu_process_killed") {
            markers += "ps_output_format_compatibility"
            markers += "host_pid_container_pid_correlation"
        }
        if (root?.rootProcessGroupId != null || root?.rootSessionId != null) {
            markers += "pgid_sid_stability_under_android_proot"
        }
        if (matchState == RuntimeProcessUnitMatchState.MATCHED_PID_FILE ||
            root?.processUnitMatchSource == RuntimeProcessUnitMatchSource.PID_FILE
        ) {
            markers += "pid_file_mapped_path_against_real_rootfs"
        }
        if (root != null) {
            markers += "ps_output_format_compatibility"
            markers += "host_pid_container_pid_correlation"
        }
        if (resource?.memoryState in setOf(
                RuntimeProcessResourceMemoryState.NEAR_LIMIT,
                RuntimeProcessResourceMemoryState.OVER_LIMIT,
                RuntimeProcessResourceMemoryState.OVER_LIMIT_BUT_UNLIMITED
            )
        ) {
            markers += "memory_rss_vmsize_accuracy_under_android_proot"
        }
        if (plan?.executorBoundary == RuntimeLifecycleExecutorBoundary.RUNTIME_RECLAIMER ||
            root?.rootProcessGroupId != null
        ) {
            markers += "future_process_group_kill_behavior_reliability"
        }
        return markers.toList()
    }

    private fun ubuntuCompatibilityStatus(
        tier: RuntimeLifecycleAuthorityTier?,
        root: RuntimeRootSnapshot?,
        finalAction: RuntimeLifecycleFinalAction?
    ): String {
        return when {
            tier == RuntimeLifecycleAuthorityTier.UNMANAGED ->
                "ubuntu_observe_only_no_auto_registration_or_recovery"
            root?.processUnitMatchSource == RuntimeProcessUnitMatchSource.COMMAND_CONTAINS ->
                "ubuntu_command_contains_match_is_weak_dry_run_only"
            finalAction == RuntimeLifecycleFinalAction.RECLAIM_CANDIDATE_DRY_RUN ->
                "ubuntu_process_semantics_preserved_reclaim_is_dry_run_only"
            else -> "ubuntu_ps_kill_pkill_script_semantics_preserved_review_only"
        }
    }

    private fun coreProtectionStatus(
        tier: RuntimeLifecycleAuthorityTier?,
        manifest: RuntimeProcessUnitManifest
    ): String {
        return when (tier) {
            RuntimeLifecycleAuthorityTier.SYSTEM_CORE ->
                if (manifest.validationReport.coreOverrideAttempt) {
                    "system_core_manifest_override_ignored"
                } else {
                    "system_core_authority_protected"
                }
            RuntimeLifecycleAuthorityTier.PROOT_CORE ->
                if (manifest.validationReport.prootCoreOverrideAttempt) {
                    "proot_core_manifest_override_ignored"
                } else {
                    "proot_core_authority_protected"
                }
            else -> "not_core"
        }
    }

    private fun invariantChecksFor(isExecutableNow: Boolean): List<String> {
        return if (isExecutableNow) {
            DEFAULT_INVARIANT_CHECKS.filterNot { it == "is_executable_now_false" } +
                "is_executable_now_true_failed_invariant"
        } else {
            DEFAULT_INVARIANT_CHECKS
        }
    }
}

private fun String?.toDiagnosticReviewEnvValue(): String {
    if (this.isNullOrBlank()) return "none"
    return trim()
        .replace(Regex("[\\r\\n\\t ]+"), "_")
        .replace(Regex("[^A-Za-z0-9._:@/+,-]"), "_")
        .take(260)
}
