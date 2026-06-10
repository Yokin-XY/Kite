package com.kftest.app.foundation.runtime

import org.json.JSONArray
import org.json.JSONObject

enum class RuntimeProcessUnitValidationSeverity {
    WARNING,
    ERROR
}

enum class RuntimeProcessUnitValidationStatus {
    VALID,
    WARNING,
    IGNORED
}

data class RuntimeProcessUnitValidationMessage(
    val unitId: String,
    val severity: RuntimeProcessUnitValidationSeverity,
    val code: String,
    val message: String
)

data class RuntimeProcessUnitValidationUnit(
    val unitId: String,
    val status: RuntimeProcessUnitValidationStatus,
    val matchRuntimeIdPresent: Boolean = false,
    val matchExactCommandPresent: Boolean = false,
    val matchPidFilePresent: Boolean = false,
    val matchProcessGroupPresent: Boolean = false,
    val matchCommandContainsCount: Int = 0,
    val messages: List<RuntimeProcessUnitValidationMessage> = emptyList()
)

data class RuntimeProcessUnitValidationReport(
    val manifestVersionMissing: Boolean = false,
    val manifestVersionUnsupported: Boolean = false,
    val ignoredUnitCount: Int = 0,
    val warningCount: Int = 0,
    val errorCount: Int = 0,
    val coreOverrideAttempt: Boolean = false,
    val prootCoreOverrideAttempt: Boolean = false,
    val weakCommandContainsWarningCount: Int = 0,
    val units: List<RuntimeProcessUnitValidationUnit> = emptyList(),
    val messages: List<RuntimeProcessUnitValidationMessage> = emptyList()
) {
    companion object {
        fun empty(): RuntimeProcessUnitValidationReport = RuntimeProcessUnitValidationReport()
    }
}

data class RuntimeProcessUnitManifestValidationResult(
    val version: Int,
    val authority: String,
    val boundary: String,
    val units: List<RuntimeProcessUnitDefinition>,
    val report: RuntimeProcessUnitValidationReport
)

object RuntimeProcessUnitManifestValidator {
    private val broadCommandNeedles = setOf(
        "sh",
        "bash",
        "python",
        "python3",
        "node",
        "java",
        "npm",
        "npx",
        "pnpm",
        "proot",
        "supervisor",
        "service"
    )

    fun validate(json: JSONObject): RuntimeProcessUnitManifestValidationResult {
        val messages = mutableListOf<RuntimeProcessUnitValidationMessage>()
        val unitReports = mutableListOf<RuntimeProcessUnitValidationUnit>()
        val acceptedUnits = mutableListOf<RuntimeProcessUnitDefinition>()
        val versionMissing = !json.hasNonNull("version")
        val rawVersion = json.optInt("version", 1)
        val version = rawVersion.coerceAtLeast(1)
        if (versionMissing) {
            messages += manifestMessage(
                severity = RuntimeProcessUnitValidationSeverity.WARNING,
                code = "manifest_version_missing",
                message = "manifest version is missing; assuming version 1"
            )
        } else if (rawVersion != 1) {
            messages += manifestMessage(
                severity = RuntimeProcessUnitValidationSeverity.ERROR,
                code = "manifest_version_unsupported",
                message = "manifest version $rawVersion is not supported by this runtime"
            )
        }

        val seenIds = mutableSetOf<String>()
        json.optJSONArray("units")?.forEachObjectIndexed { index, unitJson ->
            val unitMessages = mutableListOf<RuntimeProcessUnitValidationMessage>()
            val fallbackUnitId = "unit_$index"
            val id = unitJson.optNullableString("id") ?: fallbackUnitId
            if (id == fallbackUnitId && unitJson.optNullableString("id") == null) {
                unitMessages += unitMessage(
                    id,
                    RuntimeProcessUnitValidationSeverity.ERROR,
                    "missing_unit_id",
                    "process unit id is required"
                )
            }
            if (!seenIds.add(id)) {
                unitMessages += unitMessage(
                    id,
                    RuntimeProcessUnitValidationSeverity.ERROR,
                    "duplicate_unit_id",
                    "duplicate process unit id is ignored"
                )
            }

            val rawTier = unitJson.optNullableString("tier")
            val tier = parseEnumValue<RuntimeProcessUnitTier>(rawTier)
            if (rawTier == null) {
                unitMessages += unitMessage(
                    id,
                    RuntimeProcessUnitValidationSeverity.WARNING,
                    "tier_missing_default_unmanaged",
                    "tier is missing; unit will default to UNMANAGED"
                )
            } else if (tier == null) {
                unitMessages += unitMessage(
                    id,
                    RuntimeProcessUnitValidationSeverity.ERROR,
                    "illegal_tier",
                    "tier '$rawTier' is not supported"
                )
            }

            val effectiveTier = tier ?: RuntimeProcessUnitTier.UNMANAGED
            if (effectiveTier == RuntimeProcessUnitTier.SYSTEM_CORE) {
                unitMessages += unitMessage(
                    id,
                    RuntimeProcessUnitValidationSeverity.WARNING,
                    "manifest_system_core_declaration_ignored",
                    "system_core is Android built-in and cannot be declared by manifest"
                )
            }
            if (effectiveTier == RuntimeProcessUnitTier.PROOT_CORE) {
                unitMessages += unitMessage(
                    id,
                    RuntimeProcessUnitValidationSeverity.WARNING,
                    "manifest_proot_core_declaration_ignored",
                    "proot_core is Android built-in and cannot be declared by manifest"
                )
            }

            val rawManualKillPolicy = unitJson.optNullableString("manualKillPolicy")
            if (rawManualKillPolicy != null &&
                parseEnumValue<RuntimeProcessUnitManualKillPolicy>(rawManualKillPolicy) == null
            ) {
                unitMessages += unitMessage(
                    id,
                    RuntimeProcessUnitValidationSeverity.ERROR,
                    "illegal_manual_kill_policy",
                    "manualKillPolicy '$rawManualKillPolicy' is not supported"
                )
            }

            val execJson = unitJson.optJSONObject("exec")
            val rawStopMode = execJson?.optNullableString("stopMode")
            if (rawStopMode != null && parseEnumValue<RuntimeProcessUnitStopMode>(rawStopMode) == null) {
                unitMessages += unitMessage(
                    id,
                    RuntimeProcessUnitValidationSeverity.ERROR,
                    "illegal_stop_mode",
                    "stopMode '$rawStopMode' is not supported"
                )
            }
            val rawRestartMode = execJson?.optNullableString("restartMode")
            if (rawRestartMode != null && parseEnumValue<RuntimeProcessUnitRestartMode>(rawRestartMode) == null) {
                unitMessages += unitMessage(
                    id,
                    RuntimeProcessUnitValidationSeverity.ERROR,
                    "illegal_restart_mode",
                    "restartMode '$rawRestartMode' is not supported"
                )
            }

            val resourceJson = unitJson.optJSONObject("resource")
            val unlimitedMemory = resourceJson?.optBoolean("unlimitedMemory", false) == true
            if (resourceJson != null &&
                resourceJson.hasNonNull("expectedMemoryLimitKb") &&
                resourceJson.optLong("expectedMemoryLimitKb", 0L) < 0L
            ) {
                unitMessages += unitMessage(
                    id,
                    RuntimeProcessUnitValidationSeverity.ERROR,
                    "negative_expected_memory_limit",
                    "expectedMemoryLimitKb must be positive or omitted"
                )
            }
            if (unlimitedMemory &&
                resourceJson != null &&
                resourceJson.hasNonNull("expectedMemoryLimitKb") &&
                resourceJson.optLong("expectedMemoryLimitKb", 0L) > 0L
            ) {
                unitMessages += unitMessage(
                    id,
                    RuntimeProcessUnitValidationSeverity.WARNING,
                    "unlimited_memory_ignores_expected_limit",
                    "unlimitedMemory=true makes expectedMemoryLimitKb advisory text only and it will not be enforced"
                )
            }

            val matchJson = unitJson.optJSONObject("match")
            val match = RuntimeProcessUnitMatch.fromJson(matchJson)
            match.pidFile
                ?.takeIf { it.isNotBlank() }
                ?.let { pidFile ->
                    if (!RuntimeProcessUnitPidFilePathPolicy.isAllowed(pidFile)) {
                        unitMessages += unitMessage(
                            id,
                            RuntimeProcessUnitValidationSeverity.ERROR,
                            "unsafe_pid_file_path",
                            "pidFile must stay under /workspace, /workspace/.kf, /run, or /tmp"
                        )
                    }
                }
            if (matchJson != null &&
                matchJson.hasNonNull("commandContains") &&
                match.commandContains.isEmpty()
            ) {
                unitMessages += unitMessage(
                    id,
                    RuntimeProcessUnitValidationSeverity.WARNING,
                    "empty_command_contains",
                    "commandContains is present but empty"
                )
            }
            val hasAnyMatch =
                match.exactCommand.isNullOrBlank().not() ||
                    match.commandContains.isNotEmpty() ||
                    match.pidFile.isNullOrBlank().not() ||
                    match.runtimeId.isNullOrBlank().not() ||
                    match.processGroupId != null
            if (!hasAnyMatch) {
                unitMessages += unitMessage(
                    id,
                    RuntimeProcessUnitValidationSeverity.WARNING,
                    "missing_command_match",
                    "exactCommand and commandContains are both empty; prefer pidFile or exactCommand for stable matching"
                )
            }
            match.commandContains
                .filter { it.trim().lowercase() in broadCommandNeedles || it.trim().length < 3 }
                .forEach { broad ->
                    unitMessages += unitMessage(
                        id,
                        RuntimeProcessUnitValidationSeverity.WARNING,
                        "broad_command_contains",
                        "commandContains '$broad' is broad and may match unrelated Ubuntu processes"
                    )
                }
            if (effectiveTier == RuntimeProcessUnitTier.USER_LOCKED &&
                match.commandContains.isNotEmpty() &&
                match.exactCommand.isNullOrBlank() &&
                match.pidFile.isNullOrBlank() &&
                match.runtimeId.isNullOrBlank()
            ) {
                unitMessages += unitMessage(
                    id,
                    RuntimeProcessUnitValidationSeverity.WARNING,
                    "weak_command_contains_for_user_locked",
                    "user_locked should prefer pidFile, runtimeId, or exactCommand over weak commandContains"
                )
            }

            if (looksLikeSystemCoreOverride(match)) {
                unitMessages += unitMessage(
                    id,
                    RuntimeProcessUnitValidationSeverity.WARNING,
                    "system_core_override_attempt_ignored",
                    "manifest unit appears to target CONTAINER_SUPERVISOR and will be ignored"
                )
            }
            if (looksLikeProotCoreOverride(match)) {
                unitMessages += unitMessage(
                    id,
                    RuntimeProcessUnitValidationSeverity.WARNING,
                    "proot_core_override_attempt_ignored",
                    "manifest unit appears to target PRoot #1 and will be ignored"
                )
            }

            val ignoreUnit = unitMessages.any { it.severity == RuntimeProcessUnitValidationSeverity.ERROR } ||
                unitMessages.any {
                    it.code == "manifest_system_core_declaration_ignored" ||
                        it.code == "manifest_proot_core_declaration_ignored" ||
                        it.code == "system_core_override_attempt_ignored" ||
                        it.code == "proot_core_override_attempt_ignored"
                }
            if (!ignoreUnit) {
                RuntimeProcessUnitDefinition.fromJson(unitJson)?.let(acceptedUnits::add)
            }
            unitReports += RuntimeProcessUnitValidationUnit(
                unitId = id,
                status = when {
                    ignoreUnit -> RuntimeProcessUnitValidationStatus.IGNORED
                    unitMessages.isNotEmpty() -> RuntimeProcessUnitValidationStatus.WARNING
                    else -> RuntimeProcessUnitValidationStatus.VALID
                },
                matchRuntimeIdPresent = unitJson.optJSONObject("match")
                    ?.optString("runtimeId")
                    ?.isNotBlank() == true,
                matchExactCommandPresent = unitJson.optJSONObject("match")
                    ?.optString("exactCommand")
                    ?.isNotBlank() == true,
                matchPidFilePresent = unitJson.optJSONObject("match")
                    ?.optString("pidFile")
                    ?.isNotBlank() == true,
                matchProcessGroupPresent = unitJson.optJSONObject("match")
                    ?.let { groupMatchJson ->
                        (groupMatchJson.hasNonNull("processGroup") && groupMatchJson.optInt("processGroup") > 0) ||
                            (groupMatchJson.hasNonNull("processGroupId") && groupMatchJson.optInt("processGroupId") > 0)
                    } == true,
                matchCommandContainsCount = unitJson.optJSONObject("match")
                    ?.optStringOrArrayForValidation("commandContains")
                    ?.size
                    ?: 0,
                messages = unitMessages
            )
            messages += unitMessages
        }

        val report = RuntimeProcessUnitValidationReport(
            manifestVersionMissing = versionMissing,
            manifestVersionUnsupported = rawVersion != 1 && !versionMissing,
            ignoredUnitCount = unitReports.count { it.status == RuntimeProcessUnitValidationStatus.IGNORED },
            warningCount = messages.count { it.severity == RuntimeProcessUnitValidationSeverity.WARNING },
            errorCount = messages.count { it.severity == RuntimeProcessUnitValidationSeverity.ERROR },
            coreOverrideAttempt = messages.any {
                it.code == "manifest_system_core_declaration_ignored" ||
                    it.code == "system_core_override_attempt_ignored"
            },
            prootCoreOverrideAttempt = messages.any {
                it.code == "manifest_proot_core_declaration_ignored" ||
                    it.code == "proot_core_override_attempt_ignored"
            },
            weakCommandContainsWarningCount = messages.count {
                it.code == "weak_command_contains_for_user_locked" ||
                    it.code == "broad_command_contains"
            },
            units = unitReports,
            messages = messages
        )
        return RuntimeProcessUnitManifestValidationResult(
            version = version,
            authority = json.optString("authority", "ubuntu_advisory"),
            boundary = json.optString(
                "boundary",
                "declaration_only_android_observes_no_direct_kill_restart_or_quarantine"
            ),
            units = acceptedUnits,
            report = report
        )
    }

    private fun looksLikeSystemCoreOverride(match: RuntimeProcessUnitMatch): Boolean {
        val text = listOfNotNull(
            match.runtimeId,
            match.exactCommand,
            match.commandContains.joinToString(" ")
        ).joinToString(" ").lowercase()
        return "container-supervisor" in text || "container_supervisor" in text
    }

    private fun looksLikeProotCoreOverride(match: RuntimeProcessUnitMatch): Boolean {
        val text = listOfNotNull(
            match.runtimeId,
            match.exactCommand,
            match.commandContains.joinToString(" ")
        ).joinToString(" ").lowercase()
        return "proot-capacity-worker-1" in text ||
            "proot_capacity_worker_1" in text ||
            text.endsWith("-proot-1")
    }

    private fun manifestMessage(
        severity: RuntimeProcessUnitValidationSeverity,
        code: String,
        message: String
    ): RuntimeProcessUnitValidationMessage {
        return RuntimeProcessUnitValidationMessage(
            unitId = "manifest",
            severity = severity,
            code = code,
            message = message
        )
    }

    private fun unitMessage(
        unitId: String,
        severity: RuntimeProcessUnitValidationSeverity,
        code: String,
        message: String
    ): RuntimeProcessUnitValidationMessage {
        return RuntimeProcessUnitValidationMessage(
            unitId = unitId,
            severity = severity,
            code = code,
            message = message
        )
    }
}

private fun JSONObject.optStringOrArrayForValidation(key: String): List<String> {
    val value = opt(key) ?: return emptyList()
    return when (value) {
        is JSONArray -> (0 until value.length()).mapNotNull { index ->
            value.optString(index).trim().takeIf { it.isNotBlank() }
        }
        is String -> value.trim().takeIf { it.isNotBlank() }?.let(::listOf).orEmpty()
        else -> emptyList()
    }
}

private inline fun <reified T : Enum<T>> parseEnumValue(raw: String?): T? {
    val normalized = raw
        ?.trim()
        ?.replace('-', '_')
        ?.uppercase()
        ?: return null
    return enumValues<T>().firstOrNull { it.name == normalized }
}

private fun JSONArray.forEachObjectIndexed(block: (Int, JSONObject) -> Unit) {
    for (index in 0 until length()) {
        optJSONObject(index)?.let { block(index, it) }
    }
}

private fun JSONObject.optNullableString(key: String): String? {
    if (!hasNonNull(key)) return null
    return optString(key).trim().takeIf { it.isNotBlank() }
}

private fun JSONObject.hasNonNull(key: String): Boolean {
    val sentinel = "\u0000__kf_missing__"
    return optString(key, sentinel) != sentinel
}
