package com.kftest.app.foundation.runtime

enum class RuntimeLifecyclePolicyProfileSurfaceState {
    WORKSPACE_MISSING,
    DEFAULT_PROFILE_ACTIVE,
    USER_PROFILE_LOADED,
    CUSTOM_PROFILE_REVIEW,
    POLICY_ERROR_DEFAULT
}

enum class RuntimeLifecyclePolicyProfileSurfaceRecommendation {
    WAIT_FOR_WORKSPACE,
    KEEP_DEFAULT_PROFILE,
    REVIEW_POLICY_PROFILE,
    REVIEW_CUSTOM_POLICY_PROFILE,
    REPAIR_POLICY_JSON
}

enum class RuntimeLifecyclePolicyProfileGroup(
    val label: String,
    val description: String,
    val suggestedReclaimerProfile: String,
    val suggestedResidentProfile: String,
    val suggestedWorkloadPolicy: String,
    val builtIn: Boolean
) {
    DEFAULT_BALANCED(
        label = "Default balanced",
        description = "Balanced defaults for normal foreground work, resident recovery, and lease cleanup review.",
        suggestedReclaimerProfile = RuntimeReclaimerProfile.BALANCED.name,
        suggestedResidentProfile = RuntimeResidentProfile.BALANCED.name,
        suggestedWorkloadPolicy = "DEFAULT_WORKLOAD_POLICY",
        builtIn = true
    ),
    LOW_POWER(
        label = "Low power",
        description = "Conservative reclaim and resident behavior for battery or thermal pressure.",
        suggestedReclaimerProfile = RuntimeReclaimerProfile.CONSERVATIVE.name,
        suggestedResidentProfile = RuntimeResidentProfile.CORE_ONLY.name,
        suggestedWorkloadPolicy = "BACKGROUND_RESTRICTED_WORKLOAD_POLICY",
        builtIn = true
    ),
    HIGH_PERFORMANCE(
        label = "High performance",
        description = "Aggressive resident recovery and pressure headroom for short high-throughput sessions.",
        suggestedReclaimerProfile = RuntimeReclaimerProfile.AGGRESSIVE.name,
        suggestedResidentProfile = RuntimeResidentProfile.AGGRESSIVE.name,
        suggestedWorkloadPolicy = "FOREGROUND_PERFORMANCE_WORKLOAD_POLICY",
        builtIn = true
    ),
    CUSTOM(
        label = "Custom",
        description = "User-edited policy mix. Android/KF still owns execution and enforcement.",
        suggestedReclaimerProfile = "CUSTOM",
        suggestedResidentProfile = "CUSTOM",
        suggestedWorkloadPolicy = "CUSTOM_WORKLOAD_POLICY",
        builtIn = false
    )
}

data class RuntimeLifecyclePolicyProfileSurfaceDryRunSnapshot(
    val mode: String = "ubuntu_lifecycle_policy_profile_surface_observe_v0",
    val enforcementMode: String = "observe_only",
    val enforcementEnabled: Boolean = false,
    val generatedAtMs: Long = 0L,
    val state: RuntimeLifecyclePolicyProfileSurfaceState =
        RuntimeLifecyclePolicyProfileSurfaceState.WORKSPACE_MISSING,
    val recommendation: RuntimeLifecyclePolicyProfileSurfaceRecommendation =
        RuntimeLifecyclePolicyProfileSurfaceRecommendation.WAIT_FOR_WORKSPACE,
    val authority: String = "android_control_plane",
    val profileSelectorKind: String = "policy_group_config_not_runtime_command",
    val androidExecutionOwner: Boolean = true,
    val ubuntuProfileAdvisory: Boolean = true,
    val ubuntuProfileEditable: Boolean = false,
    val ubuntuDirectExecutionAllowed: Boolean = false,
    val ubuntuDirectStartAllowed: Boolean = false,
    val ubuntuDirectQueueAllowed: Boolean = false,
    val ubuntuDirectReclaimAllowed: Boolean = false,
    val prootDirectPoolControlAllowed: Boolean = false,
    val prootDirectLaneControlAllowed: Boolean = false,
    val activeProfileGroup: RuntimeLifecyclePolicyProfileGroup =
        RuntimeLifecyclePolicyProfileGroup.DEFAULT_BALANCED,
    val activeProfileSource: String = "runtime_reclaimer_policy+runtime_resident_policy+runtime_workload_policy",
    val executionModel: String = "android_owned_policy_group_resolves_to_lane_envelope_budget_rules",
    val concurrencyPolicy: String = "lane_max_background_max_effective_max",
    val queuePolicy: String = "priority_then_declaration_order_android_queue_only",
    val queueBacklogOrder: String = "lane_priority_then_intent_order",
    val kfSafetyRule: String = "interactive_first_kf_survival_over_workload",
    val defaultLatencyBias: String = "protect_ui_then_process_backlog",
    val directExecutionPolicy: String = "declared_policy_only_android_decides",
    val compactSurfaceMode: String = "client_compact_policy_knobs_v0",
    val compactFieldContract: String = "few_knobs_for_ui_full_env_for_engineering_only",
    val compactKnobCount: Int = 4,
    val compactDefaultSummary: String = "ui_first_balanced_parallelism_background_low_priority_off",
    val compactUiProtection: String = "STRICT",
    val compactParallelism: String = "BALANCED",
    val compactBackgroundWork: String = "LOW_PRIORITY_BACKGROUND_OFF",
    val compactCleanup: String = "REVIEW_ONLY",
    val tuningMethod: String = "binary_search_observe_then_adjust",
    val tuningPrimarySignals: String = "proot_pressure_score,budget_state,queue_backlog,lifecycle_pending_review",
    val tuningFirstAxis: String = "background_low_priority_capacity",
    val tuningSafeDefaultRule: String = "do_not_raise_background_low_priority_until_ui_and_pressure_are_stable",
    val foregroundInteractiveProtected: Boolean = true,
    val lowPriorityDeferEnabled: Boolean = false,
    val backgroundZeroLaneCount: Int = 0,
    val serialLaneCount: Int = 0,
    val burstLaneCount: Int = 0,
    val highPriorityWatchLaneCount: Int = 0,
    val lowPriorityReviewLaneCount: Int = 0,
    val backgroundGraceMs: Long = 0L,
    val transientCleanupMs: Long = 0L,
    val serviceOnlyMs: Long = 0L,
    val lowActivityMs: Long = 0L,
    val pressureAcceleratesBackgroundReview: Boolean = false,
    val buildLeaseMaxRuntimeMs: Long = 0L,
    val probeLeaseMaxRuntimeMs: Long = 0L,
    val ephemeralLeaseMaxRuntimeMs: Long = 0L,
    val strayCleanupMaxRuntimeMs: Long = 0L,
    val activeLanes: List<RuntimeLanePolicy> = emptyList(),
    val userFacingProfileGroupCount: Int = RuntimeLifecyclePolicyProfileGroup.entries.size,
    val builtInProfileGroupCount: Int =
        RuntimeLifecyclePolicyProfileGroup.entries.count { it.builtIn },
    val customProfileGroupAvailable: Boolean = true,
    val policyFileCount: Int = 0,
    val reclaimerPolicyPath: String? = null,
    val reclaimerLoadStatus: String = "unknown",
    val reclaimerActiveProfile: RuntimeReclaimerProfile = RuntimeReclaimerProfile.BALANCED,
    val reclaimerUnknownRuleCount: Int = 0,
    val residentPolicyPath: String? = null,
    val residentActiveProfile: RuntimeResidentProfile = RuntimeResidentProfile.BALANCED,
    val residentOverrideCount: Int = 0,
    val workloadPolicyPath: String? = null,
    val workloadPolicyState: RuntimeLifecyclePolicySurfaceState =
        RuntimeLifecyclePolicySurfaceState.WORKSPACE_MISSING,
    val workloadLoadStatus: String = "unknown",
    val workloadPolicyVersion: Int = 1,
    val workloadLanePolicyCount: Int = 0,
    val workloadEnvelopePolicyCount: Int = 0,
    val workloadBudgetStatePolicyCount: Int = 0,
    val workloadSafetyFloorApplied: Boolean = false,
    val lowPowerProfileAvailable: Boolean = true,
    val defaultProfileAvailable: Boolean = true,
    val highPerformanceProfileAvailable: Boolean = true,
    val customPolicyEditableFromUbuntu: Boolean = false,
    val groups: List<RuntimeLifecyclePolicyProfileGroup> =
        RuntimeLifecyclePolicyProfileGroup.entries
) {
    fun summary(): String {
        return "mode=$mode state=$state recommendation=$recommendation group=$activeProfileGroup " +
            "selector=$profileSelectorKind androidOwner=$androidExecutionOwner " +
            "ubuntuAdvisory=$ubuntuProfileAdvisory ubuntuDirect=$ubuntuDirectExecutionAllowed " +
            "reclaimer=${reclaimerActiveProfile.name} resident=${residentActiveProfile.name} " +
            "workload=${workloadPolicyState.name}/${workloadLoadStatus} enforcement=$enforcementEnabled"
    }

    fun toEnvText(maxGroups: Int = 8): String {
        return buildString {
            appendLine("lifecycle_policy_profile_surface_mode=${mode.toLifecycleProfileSurfaceEnvValue()}")
            appendLine("lifecycle_policy_profile_surface_enforcement_mode=${enforcementMode.toLifecycleProfileSurfaceEnvValue()}")
            appendLine("lifecycle_policy_profile_surface_enforcement_enabled=$enforcementEnabled")
            appendLine("lifecycle_policy_profile_surface_generated_at=$generatedAtMs")
            appendLine("lifecycle_policy_profile_surface_state=${state.name}")
            appendLine("lifecycle_policy_profile_surface_recommendation=${recommendation.name}")
            appendLine("lifecycle_policy_profile_surface_authority=${authority.toLifecycleProfileSurfaceEnvValue()}")
            appendLine("lifecycle_policy_profile_surface_profile_selector_kind=${profileSelectorKind.toLifecycleProfileSurfaceEnvValue()}")
            appendLine("lifecycle_policy_profile_surface_android_execution_owner=$androidExecutionOwner")
            appendLine("lifecycle_policy_profile_surface_ubuntu_profile_advisory=$ubuntuProfileAdvisory")
            appendLine("lifecycle_policy_profile_surface_ubuntu_profile_editable=$ubuntuProfileEditable")
            appendLine("lifecycle_policy_profile_surface_ubuntu_direct_execution_allowed=$ubuntuDirectExecutionAllowed")
            appendLine("lifecycle_policy_profile_surface_ubuntu_direct_start_allowed=$ubuntuDirectStartAllowed")
            appendLine("lifecycle_policy_profile_surface_ubuntu_direct_queue_allowed=$ubuntuDirectQueueAllowed")
            appendLine("lifecycle_policy_profile_surface_ubuntu_direct_reclaim_allowed=$ubuntuDirectReclaimAllowed")
            appendLine("lifecycle_policy_profile_surface_proot_direct_pool_control_allowed=$prootDirectPoolControlAllowed")
            appendLine("lifecycle_policy_profile_surface_proot_direct_lane_control_allowed=$prootDirectLaneControlAllowed")
            appendLine("lifecycle_policy_profile_surface_active_profile_group=${activeProfileGroup.name}")
            appendLine("lifecycle_policy_profile_surface_active_profile_label=${activeProfileGroup.label.toLifecycleProfileSurfaceEnvValue()}")
            appendLine("lifecycle_policy_profile_surface_active_profile_description=${activeProfileGroup.description.toLifecycleProfileSurfaceEnvValue()}")
            appendLine("lifecycle_policy_profile_surface_active_profile_source=${activeProfileSource.toLifecycleProfileSurfaceEnvValue()}")
            appendLine("lifecycle_policy_profile_surface_execution_model=${executionModel.toLifecycleProfileSurfaceEnvValue()}")
            appendLine("lifecycle_policy_profile_surface_concurrency_policy=${concurrencyPolicy.toLifecycleProfileSurfaceEnvValue()}")
            appendLine("lifecycle_policy_profile_surface_queue_policy=${queuePolicy.toLifecycleProfileSurfaceEnvValue()}")
            appendLine("lifecycle_policy_profile_surface_queue_backlog_order=${queueBacklogOrder.toLifecycleProfileSurfaceEnvValue()}")
            appendLine("lifecycle_policy_profile_surface_kf_safety_rule=${kfSafetyRule.toLifecycleProfileSurfaceEnvValue()}")
            appendLine("lifecycle_policy_profile_surface_default_latency_bias=${defaultLatencyBias.toLifecycleProfileSurfaceEnvValue()}")
            appendLine("lifecycle_policy_profile_surface_direct_execution_policy=${directExecutionPolicy.toLifecycleProfileSurfaceEnvValue()}")
            appendLine("lifecycle_policy_profile_surface_compact_surface_mode=${compactSurfaceMode.toLifecycleProfileSurfaceEnvValue()}")
            appendLine("lifecycle_policy_profile_surface_compact_field_contract=${compactFieldContract.toLifecycleProfileSurfaceEnvValue()}")
            appendLine("lifecycle_policy_profile_surface_compact_knob_count=$compactKnobCount")
            appendLine("lifecycle_policy_profile_surface_compact_default_summary=${compactDefaultSummary.toLifecycleProfileSurfaceEnvValue()}")
            appendLine("lifecycle_policy_profile_surface_compact_ui_protection=${compactUiProtection.toLifecycleProfileSurfaceEnvValue()}")
            appendLine("lifecycle_policy_profile_surface_compact_parallelism=${compactParallelism.toLifecycleProfileSurfaceEnvValue()}")
            appendLine("lifecycle_policy_profile_surface_compact_background_work=${compactBackgroundWork.toLifecycleProfileSurfaceEnvValue()}")
            appendLine("lifecycle_policy_profile_surface_compact_cleanup=${compactCleanup.toLifecycleProfileSurfaceEnvValue()}")
            appendLine("lifecycle_policy_profile_surface_tuning_method=${tuningMethod.toLifecycleProfileSurfaceEnvValue()}")
            appendLine("lifecycle_policy_profile_surface_tuning_primary_signals=${tuningPrimarySignals.toLifecycleProfileSurfaceEnvValue()}")
            appendLine("lifecycle_policy_profile_surface_tuning_first_axis=${tuningFirstAxis.toLifecycleProfileSurfaceEnvValue()}")
            appendLine("lifecycle_policy_profile_surface_tuning_safe_default_rule=${tuningSafeDefaultRule.toLifecycleProfileSurfaceEnvValue()}")
            appendLine("lifecycle_policy_profile_surface_foreground_interactive_protected=$foregroundInteractiveProtected")
            appendLine("lifecycle_policy_profile_surface_low_priority_defer_enabled=$lowPriorityDeferEnabled")
            appendLine("lifecycle_policy_profile_surface_background_zero_lane_count=$backgroundZeroLaneCount")
            appendLine("lifecycle_policy_profile_surface_serial_lane_count=$serialLaneCount")
            appendLine("lifecycle_policy_profile_surface_burst_lane_count=$burstLaneCount")
            appendLine("lifecycle_policy_profile_surface_high_priority_watch_lane_count=$highPriorityWatchLaneCount")
            appendLine("lifecycle_policy_profile_surface_low_priority_review_lane_count=$lowPriorityReviewLaneCount")
            appendLine("lifecycle_policy_profile_surface_background_grace_ms=$backgroundGraceMs")
            appendLine("lifecycle_policy_profile_surface_transient_cleanup_ms=$transientCleanupMs")
            appendLine("lifecycle_policy_profile_surface_service_only_ms=$serviceOnlyMs")
            appendLine("lifecycle_policy_profile_surface_low_activity_ms=$lowActivityMs")
            appendLine("lifecycle_policy_profile_surface_pressure_accelerates_background_review=$pressureAcceleratesBackgroundReview")
            appendLine("lifecycle_policy_profile_surface_build_lease_max_runtime_ms=$buildLeaseMaxRuntimeMs")
            appendLine("lifecycle_policy_profile_surface_probe_lease_max_runtime_ms=$probeLeaseMaxRuntimeMs")
            appendLine("lifecycle_policy_profile_surface_ephemeral_lease_max_runtime_ms=$ephemeralLeaseMaxRuntimeMs")
            appendLine("lifecycle_policy_profile_surface_stray_cleanup_max_runtime_ms=$strayCleanupMaxRuntimeMs")
            appendLine("lifecycle_policy_profile_surface_user_facing_profile_group_count=$userFacingProfileGroupCount")
            appendLine("lifecycle_policy_profile_surface_builtin_profile_group_count=$builtInProfileGroupCount")
            appendLine("lifecycle_policy_profile_surface_custom_profile_group_available=$customProfileGroupAvailable")
            appendLine("lifecycle_policy_profile_surface_policy_file_count=$policyFileCount")
            appendLine("lifecycle_policy_profile_surface_reclaimer_policy_path=${reclaimerPolicyPath.toLifecycleProfileSurfaceEnvValue()}")
            appendLine("lifecycle_policy_profile_surface_reclaimer_load_status=${reclaimerLoadStatus.toLifecycleProfileSurfaceEnvValue()}")
            appendLine("lifecycle_policy_profile_surface_reclaimer_active_profile=${reclaimerActiveProfile.name}")
            appendLine("lifecycle_policy_profile_surface_reclaimer_unknown_rule_count=$reclaimerUnknownRuleCount")
            appendLine("lifecycle_policy_profile_surface_resident_policy_path=${residentPolicyPath.toLifecycleProfileSurfaceEnvValue()}")
            appendLine("lifecycle_policy_profile_surface_resident_active_profile=${residentActiveProfile.name}")
            appendLine("lifecycle_policy_profile_surface_resident_override_count=$residentOverrideCount")
            appendLine("lifecycle_policy_profile_surface_workload_policy_path=${workloadPolicyPath.toLifecycleProfileSurfaceEnvValue()}")
            appendLine("lifecycle_policy_profile_surface_workload_policy_state=${workloadPolicyState.name}")
            appendLine("lifecycle_policy_profile_surface_workload_load_status=${workloadLoadStatus.toLifecycleProfileSurfaceEnvValue()}")
            appendLine("lifecycle_policy_profile_surface_workload_policy_version=$workloadPolicyVersion")
            appendLine("lifecycle_policy_profile_surface_workload_lane_policy_count=$workloadLanePolicyCount")
            appendLine("lifecycle_policy_profile_surface_workload_envelope_policy_count=$workloadEnvelopePolicyCount")
            appendLine("lifecycle_policy_profile_surface_workload_budget_state_policy_count=$workloadBudgetStatePolicyCount")
            appendLine("lifecycle_policy_profile_surface_workload_safety_floor_applied=$workloadSafetyFloorApplied")
            appendLine("lifecycle_policy_profile_surface_low_power_profile_available=$lowPowerProfileAvailable")
            appendLine("lifecycle_policy_profile_surface_default_profile_available=$defaultProfileAvailable")
            appendLine("lifecycle_policy_profile_surface_high_performance_profile_available=$highPerformanceProfileAvailable")
            appendLine("lifecycle_policy_profile_surface_custom_policy_editable_from_ubuntu=$customPolicyEditableFromUbuntu")
            activeLanes.take(maxGroups).forEachIndexed { index, lane ->
                val prefix = "lifecycle_policy_profile_surface_active_lane_${index + 1}"
                appendLine("${prefix}_name=${lane.lane.name}")
                appendLine("${prefix}_max=${lane.maxConcurrency}")
                appendLine("${prefix}_background_max=${lane.backgroundMaxConcurrency}")
                appendLine("${prefix}_serial=${lane.serial}")
                appendLine("${prefix}_allow_burst=${lane.allowBurst}")
                appendLine("${prefix}_priority=${lane.priority}")
                appendLine("${prefix}_role=${lane.roleForProfile().toLifecycleProfileSurfaceEnvValue()}")
            }
            groups.take(maxGroups).forEachIndexed { index, group ->
                val prefix = "lifecycle_policy_profile_surface_group_${index + 1}"
                appendLine("${prefix}_key=${group.name}")
                appendLine("${prefix}_label=${group.label.toLifecycleProfileSurfaceEnvValue()}")
                appendLine("${prefix}_builtin=${group.builtIn}")
                appendLine("${prefix}_suggested_reclaimer_profile=${group.suggestedReclaimerProfile}")
                appendLine("${prefix}_suggested_resident_profile=${group.suggestedResidentProfile}")
                appendLine("${prefix}_suggested_workload_policy=${group.suggestedWorkloadPolicy.toLifecycleProfileSurfaceEnvValue()}")
                appendLine("${prefix}_description=${group.description.toLifecycleProfileSurfaceEnvValue()}")
            }
            appendLine("lifecycle_policy_profile_surface_boundary=observe_only_profile_contract_no_direct_start_no_queue_no_reclaim_no_pool_resize_no_lane_control_no_enforcement")
        }
    }
}

object RuntimeLifecyclePolicyProfileSurfaceDryRun {
    fun evaluate(
        reclaimerPolicy: RuntimeReclaimerPolicy,
        residentPolicy: RuntimeResidentPolicy,
        workloadPolicy: RuntimeWorkloadPolicy,
        lifecyclePolicySurface: RuntimeLifecyclePolicySurfaceDryRunSnapshot,
        now: Long = System.currentTimeMillis()
    ): RuntimeLifecyclePolicyProfileSurfaceDryRunSnapshot {
        val activeGroup = inferProfileGroup(
            reclaimerProfile = reclaimerPolicy.activeProfile,
            residentProfile = residentPolicy.activeProfile
        )
        val workspaceMissing = lifecyclePolicySurface.state ==
            RuntimeLifecyclePolicySurfaceState.WORKSPACE_MISSING
        val policyError = lifecyclePolicySurface.state ==
            RuntimeLifecyclePolicySurfaceState.POLICY_ERROR_DEFAULT ||
            reclaimerPolicy.loadStatus == "error_default"
        val state = when {
            workspaceMissing -> RuntimeLifecyclePolicyProfileSurfaceState.WORKSPACE_MISSING
            policyError -> RuntimeLifecyclePolicyProfileSurfaceState.POLICY_ERROR_DEFAULT
            activeGroup == RuntimeLifecyclePolicyProfileGroup.CUSTOM ->
                RuntimeLifecyclePolicyProfileSurfaceState.CUSTOM_PROFILE_REVIEW
            reclaimerPolicy.policyPath != null ||
                residentPolicy.policyPath != null ||
                workloadPolicy.policyPath != null ->
                RuntimeLifecyclePolicyProfileSurfaceState.USER_PROFILE_LOADED
            else -> RuntimeLifecyclePolicyProfileSurfaceState.DEFAULT_PROFILE_ACTIVE
        }
        return RuntimeLifecyclePolicyProfileSurfaceDryRunSnapshot(
            generatedAtMs = now,
            state = state,
            recommendation = recommendationFor(state),
            ubuntuProfileEditable = !workspaceMissing,
            activeProfileGroup = activeGroup,
            policyFileCount = listOf(
                reclaimerPolicy.policyPath,
                residentPolicy.policyPath,
                workloadPolicy.policyPath
            ).count { !it.isNullOrBlank() },
            reclaimerPolicyPath = reclaimerPolicy.policyPath,
            reclaimerLoadStatus = reclaimerPolicy.loadStatus,
            reclaimerActiveProfile = reclaimerPolicy.activeProfile,
            reclaimerUnknownRuleCount = reclaimerPolicy.unknownRuleCount,
            residentPolicyPath = residentPolicy.policyPath,
            residentActiveProfile = residentPolicy.activeProfile,
            residentOverrideCount = residentPolicy.runtimeOverrides.size,
            workloadPolicyPath = workloadPolicy.policyPath,
            workloadPolicyState = lifecyclePolicySurface.state,
            workloadLoadStatus = workloadPolicy.loadStatus,
            workloadPolicyVersion = workloadPolicy.version,
            workloadLanePolicyCount = workloadPolicy.lanes.size,
            workloadEnvelopePolicyCount = workloadPolicy.envelopes.size,
            workloadBudgetStatePolicyCount = workloadPolicy.budgetStates.size,
            workloadSafetyFloorApplied = lifecyclePolicySurface.safetyFloorApplied,
            lowPriorityDeferEnabled = workloadPolicy.lanes.any {
                it.priority >= 60 && it.serial && !it.allowBurst
            },
            backgroundZeroLaneCount = workloadPolicy.lanes.count {
                it.backgroundMaxConcurrency == 0
            },
            serialLaneCount = workloadPolicy.lanes.count { it.serial },
            burstLaneCount = workloadPolicy.lanes.count { it.allowBurst },
            highPriorityWatchLaneCount = workloadPolicy.lanes.count { it.priority <= 20 },
            lowPriorityReviewLaneCount = workloadPolicy.lanes.count { it.priority >= 60 },
            backgroundGraceMs = workloadPolicy.backgroundDecay.graceMs,
            transientCleanupMs = workloadPolicy.backgroundDecay.transientCleanupMs,
            serviceOnlyMs = workloadPolicy.backgroundDecay.serviceOnlyMs,
            lowActivityMs = workloadPolicy.backgroundDecay.lowActivityMs,
            pressureAcceleratesBackgroundReview = workloadPolicy.backgroundDecay.pressureAccelerates,
            buildLeaseMaxRuntimeMs = workloadPolicy.maxRuntimeFor(RuntimeWorkloadClass.BUILD),
            probeLeaseMaxRuntimeMs = workloadPolicy.maxRuntimeFor(RuntimeWorkloadClass.PROBE),
            ephemeralLeaseMaxRuntimeMs = workloadPolicy.maxRuntimeFor(RuntimeWorkloadClass.EPHEMERAL),
            strayCleanupMaxRuntimeMs = workloadPolicy.maxRuntimeFor(RuntimeWorkloadClass.STRAY),
            activeLanes = workloadPolicy.lanes.sortedBy { it.priority },
            customPolicyEditableFromUbuntu = !workspaceMissing
        )
    }

    private fun inferProfileGroup(
        reclaimerProfile: RuntimeReclaimerProfile,
        residentProfile: RuntimeResidentProfile
    ): RuntimeLifecyclePolicyProfileGroup {
        return when {
            reclaimerProfile == RuntimeReclaimerProfile.BALANCED &&
                residentProfile == RuntimeResidentProfile.BALANCED ->
                RuntimeLifecyclePolicyProfileGroup.DEFAULT_BALANCED
            reclaimerProfile == RuntimeReclaimerProfile.CONSERVATIVE &&
                residentProfile == RuntimeResidentProfile.CORE_ONLY ->
                RuntimeLifecyclePolicyProfileGroup.LOW_POWER
            reclaimerProfile == RuntimeReclaimerProfile.AGGRESSIVE &&
                residentProfile == RuntimeResidentProfile.AGGRESSIVE ->
                RuntimeLifecyclePolicyProfileGroup.HIGH_PERFORMANCE
            else -> RuntimeLifecyclePolicyProfileGroup.CUSTOM
        }
    }

    private fun recommendationFor(
        state: RuntimeLifecyclePolicyProfileSurfaceState
    ): RuntimeLifecyclePolicyProfileSurfaceRecommendation {
        return when (state) {
            RuntimeLifecyclePolicyProfileSurfaceState.WORKSPACE_MISSING ->
                RuntimeLifecyclePolicyProfileSurfaceRecommendation.WAIT_FOR_WORKSPACE
            RuntimeLifecyclePolicyProfileSurfaceState.DEFAULT_PROFILE_ACTIVE ->
                RuntimeLifecyclePolicyProfileSurfaceRecommendation.KEEP_DEFAULT_PROFILE
            RuntimeLifecyclePolicyProfileSurfaceState.USER_PROFILE_LOADED ->
                RuntimeLifecyclePolicyProfileSurfaceRecommendation.REVIEW_POLICY_PROFILE
            RuntimeLifecyclePolicyProfileSurfaceState.CUSTOM_PROFILE_REVIEW ->
                RuntimeLifecyclePolicyProfileSurfaceRecommendation.REVIEW_CUSTOM_POLICY_PROFILE
            RuntimeLifecyclePolicyProfileSurfaceState.POLICY_ERROR_DEFAULT ->
                RuntimeLifecyclePolicyProfileSurfaceRecommendation.REPAIR_POLICY_JSON
        }
    }
}

private fun RuntimeWorkloadPolicy.maxRuntimeFor(workloadClass: RuntimeWorkloadClass): Long {
    return envelopes.firstOrNull { it.workloadClass == workloadClass }?.maxRuntimeMs ?: 0L
}

private fun RuntimeLanePolicy.roleForProfile(): String {
    return when {
        priority <= 0 -> "foreground_ui_first_burst_headroom"
        priority <= 20 -> "pinned_service_and_resident_watch"
        serial && backgroundMaxConcurrency == 0 -> "low_priority_serial_background_defer"
        else -> "managed_workload_lane"
    }
}

private fun String?.toLifecycleProfileSurfaceEnvValue(): String {
    if (this.isNullOrBlank()) return "none"
    return trim()
        .replace(Regex("[\\r\\n\\t ]+"), "_")
        .replace(Regex("[^A-Za-z0-9._:@/+,-]"), "_")
        .take(240)
}
