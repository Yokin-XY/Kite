package com.kftest.app.foundation.runtime

import android.content.Context
import com.kftest.app.foundation.logging.Logger
import com.kftest.app.foundation.service.BackgroundRuntimeKind
import com.kftest.app.foundation.service.RuntimeRetentionClass
import com.kftest.app.foundation.workspace.WorkSurfaceRuntimeBridge
import com.kftest.app.foundation.workspace.WorkspaceBuildSupport
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

enum class RuntimeProcessUnitTier {
    SYSTEM_CORE,
    PROOT_CORE,
    USER_LOCKED,
    FOREGROUND,
    LEASE,
    UNMANAGED,
    QUARANTINE
}

enum class RuntimeProcessUnitStopMode {
    UBUNTU_SIGNAL,
    RUNTIME_HOST,
    NONE
}

enum class RuntimeProcessUnitRestartMode {
    NEVER,
    ON_FAILURE,
    ALWAYS,
    WAIT_CONFIRM
}

enum class RuntimeProcessUnitManualKillPolicy {
    RESPECT_USER_KILL,
    WAIT_CONFIRM,
    AUTO_RESTART,
    CORE_RECOVER
}

enum class RuntimeProcessUnitObservationState {
    RUNNING,
    STOPPED_EXPECTED,
    STOPPED_MANUAL_KILL_UNKNOWN,
    STOPPED_CRASH_SUSPECTED,
    WAIT_CONFIRM_RESTART,
    AUTO_RESTART_ALLOWED,
    CORE_RECOVERY_REQUIRED
}

data class RuntimeProcessUnitMatch(
    val commandContains: List<String> = emptyList(),
    val exactCommand: String? = null,
    val pidFile: String? = null,
    val runtimeId: String? = null,
    val processGroupId: Int? = null
) {
    fun matches(root: RuntimeRootSnapshot): Boolean {
        runtimeId
            ?.takeIf { it.isNotBlank() }
            ?.let { expected ->
                if (root.ownerId == expected) return true
            }
        processGroupId
            ?.takeIf { it > 0 }
            ?.let { expected ->
                if (root.rootProcessGroupId == expected) return true
            }
        exactCommand
            ?.takeIf { it.isNotBlank() }
            ?.let { expected ->
                if (root.commandLine == expected || root.title == expected) return true
            }
        val text = "${root.title} ${root.commandLine}".lowercase()
        if (commandContains.any { needle -> needle.lowercase() in text }) {
            return true
        }
        return false
    }

    companion object {
        fun fromJson(json: JSONObject?): RuntimeProcessUnitMatch {
            if (json == null) return RuntimeProcessUnitMatch()
            return RuntimeProcessUnitMatch(
                commandContains = json.optStringOrArray("commandContains"),
                exactCommand = json.optNullableString("exactCommand"),
                pidFile = json.optNullableString("pidFile"),
                runtimeId = json.optNullableString("runtimeId"),
                processGroupId = json.optInt("processGroup").takeIf { json.hasNonNull("processGroup") && it > 0 }
                    ?: json.optInt("processGroupId").takeIf { json.hasNonNull("processGroupId") && it > 0 }
            )
        }
    }
}

data class RuntimeProcessUnitExec(
    val startCommand: String? = null,
    val stopMode: RuntimeProcessUnitStopMode = RuntimeProcessUnitStopMode.NONE,
    val restartMode: RuntimeProcessUnitRestartMode = RuntimeProcessUnitRestartMode.NEVER,
    val workingDirectory: String? = null,
    val env: Map<String, String> = emptyMap()
) {
    companion object {
        fun fromJson(json: JSONObject?): RuntimeProcessUnitExec {
            if (json == null) return RuntimeProcessUnitExec()
            return RuntimeProcessUnitExec(
                startCommand = json.optNullableString("startCommand"),
                stopMode = parseEnum(
                    raw = json.optNullableString("stopMode"),
                    default = RuntimeProcessUnitStopMode.NONE
                ),
                restartMode = parseEnum(
                    raw = json.optNullableString("restartMode"),
                    default = RuntimeProcessUnitRestartMode.NEVER
                ),
                workingDirectory = json.optNullableString("workingDirectory"),
                env = json.optJSONObject("env")?.toStringMap().orEmpty()
            )
        }
    }
}

data class RuntimeProcessUnitResource(
    val expectedMemoryLimitKb: Long? = null,
    val unlimitedMemory: Boolean = false,
    val warningThresholdRatio: Double = 0.9,
    val restartThresholdRatio: Double = 1.0,
    val quarantineAfterFailures: Int = 3
) {
    companion object {
        fun fromJson(json: JSONObject?): RuntimeProcessUnitResource {
            if (json == null) return RuntimeProcessUnitResource()
            val unlimited = json.optBoolean("unlimitedMemory", false)
            return RuntimeProcessUnitResource(
                expectedMemoryLimitKb = json.optLong("expectedMemoryLimitKb")
                    .takeIf { json.hasNonNull("expectedMemoryLimitKb") && it > 0L && !unlimited },
                unlimitedMemory = unlimited,
                warningThresholdRatio = json.optDouble("warningThresholdRatio", 0.9).coerceIn(0.0, 10.0),
                restartThresholdRatio = json.optDouble("restartThresholdRatio", 1.0).coerceIn(0.0, 10.0),
                quarantineAfterFailures = json.optInt("quarantineAfterFailures", 3).coerceAtLeast(0)
            )
        }
    }
}

data class RuntimeProcessUnitLease(
    val enabled: Boolean = false,
    val initialLeaseMs: Long = 0L,
    val renewMs: Long = 0L,
    val maxTotalLeaseMs: Long = 0L
) {
    companion object {
        fun fromJson(json: JSONObject?): RuntimeProcessUnitLease {
            if (json == null) return RuntimeProcessUnitLease()
            return RuntimeProcessUnitLease(
                enabled = json.optBoolean("enabled", false),
                initialLeaseMs = json.optLong("initialLeaseMs", 0L).coerceAtLeast(0L),
                renewMs = json.optLong("renewMs", 0L).coerceAtLeast(0L),
                maxTotalLeaseMs = json.optLong("maxTotalLeaseMs", 0L).coerceAtLeast(0L)
            )
        }
    }
}

data class RuntimeProcessUnitProtection(
    val userEditable: Boolean = true,
    val allowReclaim: Boolean = false,
    val allowKill: Boolean = false,
    val allowRestart: Boolean = false,
    val requiresMemoryAdmission: Boolean = false
) {
    companion object {
        fun fromJson(json: JSONObject?, tier: RuntimeProcessUnitTier): RuntimeProcessUnitProtection {
            val defaults = defaultFor(tier)
            if (json == null) return defaults
            return RuntimeProcessUnitProtection(
                userEditable = json.optBoolean("userEditable", defaults.userEditable),
                allowReclaim = json.optBoolean("allowReclaim", defaults.allowReclaim),
                allowKill = json.optBoolean("allowKill", defaults.allowKill),
                allowRestart = json.optBoolean("allowRestart", defaults.allowRestart),
                requiresMemoryAdmission = json.optBoolean(
                    "requiresMemoryAdmission",
                    defaults.requiresMemoryAdmission
                )
            )
        }

        fun defaultFor(tier: RuntimeProcessUnitTier): RuntimeProcessUnitProtection {
            return when (tier) {
                RuntimeProcessUnitTier.SYSTEM_CORE,
                RuntimeProcessUnitTier.PROOT_CORE -> RuntimeProcessUnitProtection(
                    userEditable = false,
                    allowReclaim = false,
                    allowKill = false,
                    allowRestart = tier == RuntimeProcessUnitTier.SYSTEM_CORE,
                    requiresMemoryAdmission = tier == RuntimeProcessUnitTier.PROOT_CORE
                )
                RuntimeProcessUnitTier.USER_LOCKED,
                RuntimeProcessUnitTier.FOREGROUND -> RuntimeProcessUnitProtection(
                    userEditable = true,
                    allowReclaim = false,
                    allowKill = false,
                    allowRestart = false,
                    requiresMemoryAdmission = false
                )
                RuntimeProcessUnitTier.LEASE -> RuntimeProcessUnitProtection(
                    userEditable = true,
                    allowReclaim = true,
                    allowKill = true,
                    allowRestart = false,
                    requiresMemoryAdmission = false
                )
                RuntimeProcessUnitTier.UNMANAGED -> RuntimeProcessUnitProtection(
                    userEditable = true,
                    allowReclaim = false,
                    allowKill = false,
                    allowRestart = false,
                    requiresMemoryAdmission = false
                )
                RuntimeProcessUnitTier.QUARANTINE -> RuntimeProcessUnitProtection(
                    userEditable = false,
                    allowReclaim = false,
                    allowKill = false,
                    allowRestart = false,
                    requiresMemoryAdmission = false
                )
            }
        }
    }
}

data class RuntimeProcessUnitDefinition(
    val id: String,
    val displayName: String,
    val match: RuntimeProcessUnitMatch,
    val tier: RuntimeProcessUnitTier,
    val exec: RuntimeProcessUnitExec = RuntimeProcessUnitExec(),
    val resource: RuntimeProcessUnitResource = RuntimeProcessUnitResource(),
    val lease: RuntimeProcessUnitLease = RuntimeProcessUnitLease(),
    val manualKillPolicy: RuntimeProcessUnitManualKillPolicy,
    val protection: RuntimeProcessUnitProtection,
    val source: String = "manifest"
) {
    fun matches(root: RuntimeRootSnapshot): Boolean = match.matches(root)

    fun toClassification(reason: String): RuntimeProcessUnitClassification {
        return RuntimeProcessUnitClassification(
            unitId = id,
            displayName = displayName,
            tier = tier,
            source = source,
            expectedMemoryLimitKb = resource.expectedMemoryLimitKb,
            unlimitedMemory = resource.unlimitedMemory,
            warningThresholdRatio = resource.warningThresholdRatio,
            restartThresholdRatio = resource.restartThresholdRatio,
            quarantineAfterFailures = resource.quarantineAfterFailures,
            manualKillPolicy = manualKillPolicy,
            userEditable = protection.userEditable,
            allowReclaim = protection.allowReclaim,
            allowKill = protection.allowKill,
            allowRestart = protection.allowRestart,
            requiresMemoryAdmission = protection.requiresMemoryAdmission,
            reason = reason
        )
    }

    companion object {
        fun fromJson(json: JSONObject): RuntimeProcessUnitDefinition? {
            val id = json.optString("id").trim().takeIf { it.isNotBlank() } ?: return null
            val tier = parseEnum(
                raw = json.optNullableString("tier"),
                default = RuntimeProcessUnitTier.UNMANAGED
            )
            val resource = RuntimeProcessUnitResource.fromJson(json.optJSONObject("resource"))
            val manualKillPolicy = parseEnum(
                raw = json.optNullableString("manualKillPolicy"),
                default = defaultManualKillPolicy(tier)
            )
            return RuntimeProcessUnitDefinition(
                id = id,
                displayName = json.optString("displayName", id).ifBlank { id },
                match = RuntimeProcessUnitMatch.fromJson(json.optJSONObject("match")),
                tier = tier,
                exec = RuntimeProcessUnitExec.fromJson(json.optJSONObject("exec")),
                resource = resource,
                lease = RuntimeProcessUnitLease.fromJson(json.optJSONObject("lease")),
                manualKillPolicy = manualKillPolicy,
                protection = RuntimeProcessUnitProtection.fromJson(json.optJSONObject("protection"), tier)
            )
        }
    }
}

data class RuntimeProcessUnitClassification(
    val unitId: String,
    val displayName: String,
    val tier: RuntimeProcessUnitTier,
    val source: String,
    val expectedMemoryLimitKb: Long? = null,
    val unlimitedMemory: Boolean = false,
    val warningThresholdRatio: Double = 0.9,
    val restartThresholdRatio: Double = 1.0,
    val quarantineAfterFailures: Int = 3,
    val manualKillPolicy: RuntimeProcessUnitManualKillPolicy,
    val userEditable: Boolean,
    val allowReclaim: Boolean,
    val allowKill: Boolean,
    val allowRestart: Boolean,
    val requiresMemoryAdmission: Boolean,
    val reason: String
)

data class RuntimeProcessUnitStateSnapshot(
    val rootKey: String,
    val unitId: String,
    val displayName: String,
    val tier: RuntimeProcessUnitTier,
    val matchSource: RuntimeProcessUnitMatchSource = RuntimeProcessUnitMatchSource.NONE,
    val matchConfidence: RuntimeProcessUnitMatchConfidence = RuntimeProcessUnitMatchConfidence.NONE,
    val matchState: RuntimeProcessUnitMatchState = RuntimeProcessUnitMatchState.UNMANAGED_OBSERVED,
    val matchedPid: Int? = null,
    val matchedPgid: Int? = null,
    val matchedSid: Int? = null,
    val conflictUnitIds: List<String> = emptyList(),
    val fallbackReason: String = "none",
    val observedState: RuntimeProcessUnitObservationState,
    val manualKillPolicy: RuntimeProcessUnitManualKillPolicy,
    val running: Boolean,
    val autoRestartAllowed: Boolean,
    val expectedMemoryLimitKb: Long? = null,
    val unlimitedMemory: Boolean = false,
    val reason: String
)

data class RuntimeProcessUnitManifestSnapshot(
    val mode: String = "runtime_process_unit_manifest_v0",
    val enforcementMode: String = "declaration_only",
    val enforcementEnabled: Boolean = false,
    val path: String? = null,
    val examplePath: String? = null,
    val loadStatus: String = "not_loaded",
    val loadError: String? = null,
    val manifestLoaded: Boolean = false,
    val manifestVersion: Int = 1,
    val unitCount: Int = 0,
    val ignoredUnitCount: Int = 0,
    val matchedRootCount: Int = 0,
    val builtInMatchedRootCount: Int = 0,
    val userLockedCount: Int = 0,
    val prootCoreCount: Int = 0,
    val unlimitedMemoryCount: Int = 0,
    val expectedMemoryLimitCount: Int = 0,
    val waitConfirmRestartCount: Int = 0,
    val autoRestartAllowedCount: Int = 0,
    val coreRecoveryRequiredCount: Int = 0,
    val validationWarningCount: Int = 0,
    val validationErrorCount: Int = 0,
    val coreOverrideAttempt: Boolean = false,
    val prootCoreOverrideAttempt: Boolean = false,
    val ambiguousUserLockedCount: Int = 0,
    val weakCommandContainsWarningCount: Int = 0,
    val sourceOfTruthSummary: String =
        "built_in_core>proot_core>background_runtime_registry>expected_stop>manifest_declaration>workload_resident_reclaimer_policy>process_snapshot",
    val validationUnits: List<RuntimeProcessUnitValidationUnit> = emptyList(),
    val validationMessages: List<RuntimeProcessUnitValidationMessage> = emptyList(),
    val boundary: String = "observe_only_no_direct_start_stop_reclaim_restart_or_quarantine"
) {
    fun toEnvText(maxItems: Int = 0, states: List<RuntimeProcessUnitStateSnapshot> = emptyList()): String {
        return buildString {
            appendLine("runtime_process_unit_manifest_mode=${mode.toProcessUnitEnvValue()}")
            appendLine("runtime_process_unit_manifest_enforcement_mode=${enforcementMode.toProcessUnitEnvValue()}")
            appendLine("runtime_process_unit_manifest_enforcement_enabled=$enforcementEnabled")
            appendLine("runtime_process_unit_manifest_path=${path.toProcessUnitEnvValue()}")
            appendLine("runtime_process_unit_manifest_example_path=${examplePath.toProcessUnitEnvValue()}")
            appendLine("runtime_process_unit_manifest_load_status=${loadStatus.toProcessUnitEnvValue()}")
            appendLine("runtime_process_unit_manifest_load_error=${loadError.toProcessUnitEnvValue()}")
            appendLine("runtime_process_unit_manifest_loaded=$manifestLoaded")
            appendLine("runtime_process_unit_manifest_version=$manifestVersion")
            appendLine("runtime_process_unit_manifest_unit_count=$unitCount")
            appendLine("runtime_process_unit_manifest_ignored_unit_count=$ignoredUnitCount")
            appendLine("runtime_process_unit_manifest_matched_root_count=$matchedRootCount")
            appendLine("runtime_process_unit_manifest_builtin_matched_root_count=$builtInMatchedRootCount")
            appendLine("runtime_process_unit_manifest_user_locked_count=$userLockedCount")
            appendLine("runtime_process_unit_manifest_proot_core_count=$prootCoreCount")
            appendLine("runtime_process_unit_manifest_unlimited_memory_count=$unlimitedMemoryCount")
            appendLine("runtime_process_unit_manifest_expected_memory_limit_count=$expectedMemoryLimitCount")
            appendLine("runtime_process_unit_manifest_wait_confirm_restart_count=$waitConfirmRestartCount")
            appendLine("runtime_process_unit_manifest_auto_restart_allowed_count=$autoRestartAllowedCount")
            appendLine("runtime_process_unit_manifest_core_recovery_required_count=$coreRecoveryRequiredCount")
            appendLine("runtime_process_unit_manifest_validation_warning_count=$validationWarningCount")
            appendLine("runtime_process_unit_manifest_validation_error_count=$validationErrorCount")
            appendLine("runtime_process_unit_manifest_core_override_attempt=$coreOverrideAttempt")
            appendLine("runtime_process_unit_manifest_proot_core_override_attempt=$prootCoreOverrideAttempt")
            appendLine("runtime_process_unit_manifest_ambiguous_user_locked_count=$ambiguousUserLockedCount")
            appendLine("runtime_process_unit_manifest_weak_command_contains_warning_count=$weakCommandContainsWarningCount")
            appendLine("runtime_process_unit_manifest_source_of_truth=${sourceOfTruthSummary.toProcessUnitEnvValue()}")
            appendLine("runtime_process_unit_manifest_boundary=${boundary.toProcessUnitEnvValue()}")
            validationUnits.take(maxItems).forEachIndexed { index, unit ->
                val prefix = "runtime_process_unit_validation_${index + 1}"
                appendLine("${prefix}_id=${unit.unitId.toProcessUnitEnvValue()}")
                appendLine("${prefix}_status=${unit.status.name}")
                appendLine("${prefix}_match_runtime_id_present=${unit.matchRuntimeIdPresent}")
                appendLine("${prefix}_match_exact_command_present=${unit.matchExactCommandPresent}")
                appendLine("${prefix}_match_pid_file_present=${unit.matchPidFilePresent}")
                appendLine("${prefix}_match_process_group_present=${unit.matchProcessGroupPresent}")
                appendLine("${prefix}_match_command_contains_count=${unit.matchCommandContainsCount}")
                appendLine("${prefix}_messages=${unit.messages.joinToString(";") { it.toEnvMessage() }.toProcessUnitEnvValue()}")
            }
            validationMessages.take(maxItems).forEachIndexed { index, message ->
                val prefix = "runtime_process_unit_validation_message_${index + 1}"
                appendLine("${prefix}=${message.toEnvMessage().toProcessUnitEnvValue()}")
            }
            states.take(maxItems).forEachIndexed { index, state ->
                val prefix = "runtime_process_unit_${index + 1}"
                appendLine("${prefix}_id=${state.unitId.toProcessUnitEnvValue()}")
                appendLine("${prefix}_tier=${state.tier.name}")
                appendLine("${prefix}_match_source=${state.matchSource.name}")
                appendLine("${prefix}_match_confidence=${state.matchConfidence.name}")
                appendLine("${prefix}_match_state=${state.matchState.name}")
                appendLine("${prefix}_matched_pid=${state.matchedPid ?: 0}")
                appendLine("${prefix}_matched_pgid=${state.matchedPgid ?: 0}")
                appendLine("${prefix}_matched_sid=${state.matchedSid ?: 0}")
                appendLine("${prefix}_conflict_unit_ids=${state.conflictUnitIds.joinToString(",").toProcessUnitEnvValue()}")
                appendLine("${prefix}_fallback_reason=${state.fallbackReason.toProcessUnitEnvValue()}")
                appendLine("${prefix}_state=${state.observedState.name}")
                appendLine("${prefix}_manual_kill_policy=${state.manualKillPolicy.name}")
                appendLine("${prefix}_auto_restart_allowed=${state.autoRestartAllowed}")
                appendLine("${prefix}_unlimited_memory=${state.unlimitedMemory}")
                appendLine("${prefix}_expected_memory_limit_kb=${state.expectedMemoryLimitKb ?: 0L}")
                appendLine("${prefix}_reason=${state.reason.toProcessUnitEnvValue()}")
            }
        }
    }
}

data class RuntimeProcessUnitManifest(
    val version: Int = 1,
    val authority: String = "ubuntu_advisory",
    val boundary: String = "declaration_only_android_observes_no_direct_kill_restart_or_quarantine",
    val units: List<RuntimeProcessUnitDefinition> = emptyList(),
    val path: String? = null,
    val examplePath: String? = null,
    val loadStatus: String = "default",
    val loadError: String? = null,
    val validationReport: RuntimeProcessUnitValidationReport = RuntimeProcessUnitValidationReport.empty()
) {
    fun classify(root: RuntimeRootSnapshot): RuntimeProcessUnitClassification? {
        return RuntimeProcessUnitMatcher.evaluateRoot(
            manifest = this,
            root = root
        ).classification
    }

    fun applyToRoot(root: RuntimeRootSnapshot): RuntimeRootSnapshot {
        return applyToRoots(listOf(root)).single()
    }

    fun applyToRoots(
        roots: List<RuntimeRootSnapshot>,
        pidFileReader: RuntimeProcessUnitPidFileReader = RuntimeProcessUnitPidFileReader.noop()
    ): List<RuntimeRootSnapshot> {
        return RuntimeProcessUnitMatcher.applyToRoots(
            manifest = this,
            roots = roots,
            pidFileReader = pidFileReader
        )
    }

    fun snapshot(
        roots: List<RuntimeRootSnapshot>,
        states: List<RuntimeProcessUnitStateSnapshot> = emptyList()
    ): RuntimeProcessUnitManifestSnapshot {
        val matchedRoots = roots.filter { it.processUnitId != null }
        return RuntimeProcessUnitManifestSnapshot(
            path = path,
            examplePath = examplePath,
            loadStatus = loadStatus,
            loadError = loadError,
            manifestLoaded = loadStatus == "loaded",
            manifestVersion = version,
            unitCount = units.size,
            ignoredUnitCount = validationReport.ignoredUnitCount,
            matchedRootCount = matchedRoots.size,
            builtInMatchedRootCount = matchedRoots.count { it.processUnitSource == BUILT_IN_SOURCE },
            userLockedCount = matchedRoots.count { it.processUnitTier == RuntimeProcessUnitTier.USER_LOCKED },
            prootCoreCount = matchedRoots.count { it.processUnitTier == RuntimeProcessUnitTier.PROOT_CORE },
            unlimitedMemoryCount = matchedRoots.count { it.processUnitUnlimitedMemory },
            expectedMemoryLimitCount = matchedRoots.count { it.processUnitExpectedMemoryLimitKb != null },
            waitConfirmRestartCount =
                states.count { it.observedState == RuntimeProcessUnitObservationState.WAIT_CONFIRM_RESTART },
            autoRestartAllowedCount =
                states.count { it.observedState == RuntimeProcessUnitObservationState.AUTO_RESTART_ALLOWED },
            coreRecoveryRequiredCount =
                states.count { it.observedState == RuntimeProcessUnitObservationState.CORE_RECOVERY_REQUIRED },
            validationWarningCount = validationReport.warningCount,
            validationErrorCount = validationReport.errorCount,
            coreOverrideAttempt = validationReport.coreOverrideAttempt,
            prootCoreOverrideAttempt = validationReport.prootCoreOverrideAttempt,
            ambiguousUserLockedCount = matchedRoots.count {
                it.processUnitTier == RuntimeProcessUnitTier.USER_LOCKED &&
                    it.processUnitMatchState == RuntimeProcessUnitMatchState.MATCH_AMBIGUOUS
            },
            weakCommandContainsWarningCount = validationReport.weakCommandContainsWarningCount,
            validationUnits = validationReport.units,
            validationMessages = validationReport.messages
        )
    }

    companion object {
        private const val BUILT_IN_SOURCE = "android_builtin"

        fun default(
            path: String? = null,
            examplePath: String? = null,
            loadStatus: String = "default",
            loadError: String? = null,
            validationReport: RuntimeProcessUnitValidationReport = RuntimeProcessUnitValidationReport.empty()
        ): RuntimeProcessUnitManifest {
            return RuntimeProcessUnitManifest(
                path = path,
                examplePath = examplePath,
                loadStatus = loadStatus,
                loadError = loadError,
                validationReport = validationReport
            )
        }

        fun fromJson(json: JSONObject, path: String?, loadStatus: String = "loaded"): RuntimeProcessUnitManifest {
            val validated = RuntimeProcessUnitManifestValidator.validate(json)
            return RuntimeProcessUnitManifest(
                version = validated.version,
                authority = validated.authority,
                boundary = validated.boundary,
                units = validated.units,
                path = path,
                examplePath = path?.let { File(it).parentFile?.let(WorkspaceBuildSupport::runtimeProcessManifestExampleFile)?.absolutePath },
                loadStatus = loadStatus,
                validationReport = validated.report
            )
        }

        fun builtInClassification(root: RuntimeRootSnapshot): RuntimeProcessUnitClassification? {
            if (root.ownerKind != RuntimeRootOwnerKind.BACKGROUND_RUNTIME) return null
            if (root.runtimeKind == BackgroundRuntimeKind.CONTAINER_SUPERVISOR ||
                root.retentionClass == RuntimeRetentionClass.CRITICAL_CORE
            ) {
                return builtIn(
                    root = root,
                    unitId = root.ownerId ?: "builtin:container_supervisor",
                    displayName = root.title.ifBlank { "KF system core" },
                    tier = RuntimeProcessUnitTier.SYSTEM_CORE,
                    manualKillPolicy = RuntimeProcessUnitManualKillPolicy.CORE_RECOVER,
                    reason = "android_builtin_system_core_cannot_be_downgraded_by_manifest"
                )
            }
            if (root.runtimeKind == BackgroundRuntimeKind.PROOT_CAPACITY_WORKER &&
                root.prootCapacityIndexForProcessUnit() == 1
            ) {
                return builtIn(
                    root = root,
                    unitId = root.ownerId ?: "builtin:proot_capacity_worker_1",
                    displayName = root.title.ifBlank { "PRoot #1" },
                    tier = RuntimeProcessUnitTier.PROOT_CORE,
                    manualKillPolicy = RuntimeProcessUnitManualKillPolicy.CORE_RECOVER,
                    reason = "android_builtin_proot_1_core_uses_runtime_host_boundary"
                )
            }
            if (root.isBuiltInResidentDaemon()) {
                return builtIn(
                    root = root,
                    unitId = root.ownerId ?: "builtin:resident_daemon",
                    displayName = root.title.ifBlank { "Resident daemon" },
                    tier = RuntimeProcessUnitTier.USER_LOCKED,
                    manualKillPolicy = RuntimeProcessUnitManualKillPolicy.WAIT_CONFIRM,
                    reason = "android_builtin_resident_daemon_uses_user_locked_protection"
                )
            }
            return null
        }

        private fun builtIn(
            root: RuntimeRootSnapshot,
            unitId: String,
            displayName: String,
            tier: RuntimeProcessUnitTier,
            manualKillPolicy: RuntimeProcessUnitManualKillPolicy,
            reason: String
        ): RuntimeProcessUnitClassification {
            val protection = RuntimeProcessUnitProtection.defaultFor(tier)
            return RuntimeProcessUnitClassification(
                unitId = unitId,
                displayName = displayName,
                tier = tier,
                source = BUILT_IN_SOURCE,
                manualKillPolicy = manualKillPolicy,
                userEditable = false,
                allowReclaim = protection.allowReclaim,
                allowKill = protection.allowKill,
                allowRestart = protection.allowRestart && root.runtimeKind != BackgroundRuntimeKind.PROOT_CAPACITY_WORKER,
                requiresMemoryAdmission = protection.requiresMemoryAdmission,
                reason = reason
            )
        }
    }
}

private fun RuntimeRootSnapshot.isBuiltInResidentDaemon(): Boolean {
    if (retentionClass != RuntimeRetentionClass.RESIDENT) return false
    return runtimeKind == BackgroundRuntimeKind.OPENCLAW_GATEWAY ||
        runtimeKind == BackgroundRuntimeKind.FEISHU_GATEWAY ||
        runtimeKind == BackgroundRuntimeKind.ADB_WORKER ||
        runtimeKind == BackgroundRuntimeKind.ACCESSIBILITY_WORKER
}

object RuntimeProcessUnitManifestStore {
    private const val LOG_TAG = "RuntimeProcessUnitManifest"

    fun load(context: Context): RuntimeProcessUnitManifest {
        val workspacePath = WorkSurfaceRuntimeBridge.getSavedContainer(context)?.workspacePath
            ?.takeIf { it.isNotBlank() }
            ?: return RuntimeProcessUnitManifest.default(loadStatus = "workspace_missing")
        val workspaceDir = File(workspacePath)
        ensureExampleManifest(workspaceDir)
        val file = WorkspaceBuildSupport.runtimeProcessManifestFile(workspaceDir)
        val examplePath = WorkspaceBuildSupport.runtimeProcessManifestExampleFile(workspaceDir).absolutePath
        return load(file, examplePath)
    }

    fun load(file: File): RuntimeProcessUnitManifest {
        return load(
            file = file,
            examplePath = file.parentFile
                ?.let { File(it, "runtime-process-manifest.example.json").absolutePath }
        )
    }

    private fun load(file: File, examplePath: String?): RuntimeProcessUnitManifest {
        val path = runCatching { file.canonicalPath }.getOrElse { file.absolutePath }
        if (!file.exists()) {
            return RuntimeProcessUnitManifest.default(
                path = path,
                examplePath = examplePath,
                loadStatus = "missing_default"
            )
        }
        if (file.length() <= 0L || file.readText().isBlank()) {
            return RuntimeProcessUnitManifest.default(
                path = path,
                examplePath = examplePath,
                loadStatus = "empty_default"
            )
        }
        return runCatching {
            RuntimeProcessUnitManifest.fromJson(
                json = JSONObject(file.readText()),
                path = path
            ).copy(examplePath = examplePath)
        }.getOrElse { error ->
            runCatching {
                Logger.e(LOG_TAG, "failed to load runtime process unit manifest: ${error.message}")
            }
            RuntimeProcessUnitManifest.default(
                path = path,
                examplePath = examplePath,
                loadStatus = "error_default",
                loadError = error.message
            )
        }
    }

    private fun ensureExampleManifest(workspaceDir: File) {
        val example = WorkspaceBuildSupport.runtimeProcessManifestExampleFile(workspaceDir)
        if (example.exists()) return
        runCatching {
            example.parentFile?.mkdirs()
            example.writeText(WorkspaceBuildSupport.buildRuntimeProcessManifestExampleTemplate())
        }
    }

}

fun RuntimeProcessUnitManifest.Companion.evaluateObservationState(
    root: RuntimeRootSnapshot
): RuntimeProcessUnitObservationState {
    return RuntimeProcessStopReconciliation.evaluate(root).observedState
}

private fun defaultManualKillPolicy(tier: RuntimeProcessUnitTier): RuntimeProcessUnitManualKillPolicy {
    return when (tier) {
        RuntimeProcessUnitTier.SYSTEM_CORE,
        RuntimeProcessUnitTier.PROOT_CORE -> RuntimeProcessUnitManualKillPolicy.CORE_RECOVER
        RuntimeProcessUnitTier.USER_LOCKED,
        RuntimeProcessUnitTier.QUARANTINE -> RuntimeProcessUnitManualKillPolicy.WAIT_CONFIRM
        RuntimeProcessUnitTier.FOREGROUND,
        RuntimeProcessUnitTier.LEASE,
        RuntimeProcessUnitTier.UNMANAGED -> RuntimeProcessUnitManualKillPolicy.RESPECT_USER_KILL
    }
}

private inline fun <reified T : Enum<T>> parseEnum(raw: String?, default: T): T {
    val normalized = raw
        ?.trim()
        ?.replace('-', '_')
        ?.uppercase()
        ?: return default
    return enumValues<T>().firstOrNull { it.name == normalized } ?: default
}

private fun JSONArray.toObjectList(parser: (JSONObject) -> RuntimeProcessUnitDefinition?): List<RuntimeProcessUnitDefinition> {
    return buildList {
        for (index in 0 until length()) {
            val json = optJSONObject(index) ?: continue
            parser(json)?.let(::add)
        }
    }
}

private fun JSONObject.optNullableString(key: String): String? {
    if (!hasNonNull(key)) return null
    return optString(key).trim().takeIf { it.isNotBlank() }
}

private fun JSONObject.optStringOrArray(key: String): List<String> {
    if (!hasNonNull(key)) return emptyList()
    optJSONArray(key)?.let { array ->
        return buildList {
            for (index in 0 until array.length()) {
                array.optString(index).trim().takeIf { it.isNotBlank() }?.let(::add)
            }
        }
    }
    return optString(key).trim().takeIf { it.isNotBlank() }?.let(::listOf).orEmpty()
}

private fun JSONObject.hasNonNull(key: String): Boolean {
    val sentinel = "\u0000__kf_missing__"
    return optString(key, sentinel) != sentinel
}

private fun JSONObject.toStringMap(): Map<String, String> {
    return keys().asSequence()
        .mapNotNull { key ->
            optString(key).takeIf { it.isNotBlank() }?.let { key to it }
        }
        .toMap()
}

private fun RuntimeRootSnapshot.prootCapacityIndexForProcessUnit(): Int {
    return ownerId
        ?.substringAfterLast("-proot-capacity-worker-", "")
        ?.toIntOrNull()
        ?.takeIf { it > 0 }
        ?: Int.MAX_VALUE
}

private fun RuntimeProcessUnitValidationMessage.toEnvMessage(): String {
    return "${severity.name}:$unitId:$code:$message"
}

private fun String?.toProcessUnitEnvValue(): String {
    if (this.isNullOrBlank()) return "none"
    return trim()
        .replace(Regex("[\\r\\n\\t ]+"), "_")
        .replace(Regex("[^A-Za-z0-9._:@/+,-]"), "_")
        .take(200)
}
