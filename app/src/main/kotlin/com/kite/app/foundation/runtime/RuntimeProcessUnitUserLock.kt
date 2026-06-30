package com.kite.app.foundation.runtime

import java.io.File
import org.json.JSONArray
import org.json.JSONObject

enum class RuntimeProcessUnitUserLockResultStatus {
    WRITTEN,
    REMOVED,
    NOT_FOUND,
    REJECTED,
    FAILED
}

data class RuntimeProcessUnitUserLockResult(
    val status: RuntimeProcessUnitUserLockResultStatus,
    val unitId: String,
    val reason: String,
    val manifestPath: String? = null
) {
    val changed: Boolean
        get() = status == RuntimeProcessUnitUserLockResultStatus.WRITTEN ||
            status == RuntimeProcessUnitUserLockResultStatus.REMOVED
}

data class RuntimeProcessUnitUserLockDeclaration(
    val unitId: String,
    val displayName: String,
    val match: RuntimeProcessUnitMatch,
    val expectedMemoryLimitKb: Long? = null,
    val unlimitedMemory: Boolean = false
)

object RuntimeProcessUnitUserLock {
    fun lockRegisteredRuntime(
        file: File,
        runtimeId: String,
        displayName: String = runtimeId,
        expectedMemoryLimitKb: Long? = null
    ): RuntimeProcessUnitUserLockResult {
        val normalizedRuntimeId = runtimeId.trim()
        if (normalizedRuntimeId.isBlank()) {
            return rejected("none", "runtime_id_required", file)
        }
        if (targetsBuiltInCore(normalizedRuntimeId)) {
            return rejected(unitIdFor("runtime", normalizedRuntimeId), "built_in_core_or_proot_one_cannot_be_user_locked", file)
        }
        return upsert(
            file = file,
            declaration = RuntimeProcessUnitUserLockDeclaration(
                unitId = unitIdFor("runtime", normalizedRuntimeId),
                displayName = displayName.ifBlank { normalizedRuntimeId },
                match = RuntimeProcessUnitMatch(runtimeId = normalizedRuntimeId),
                expectedMemoryLimitKb = expectedMemoryLimitKb
            )
        )
    }

    fun lockExactCommand(
        file: File,
        unitId: String,
        exactCommand: String,
        displayName: String = unitId,
        expectedMemoryLimitKb: Long? = null
    ): RuntimeProcessUnitUserLockResult {
        val normalizedUnitId = unitId.trim()
        val normalizedCommand = exactCommand.trim()
        if (normalizedUnitId.isBlank()) return rejected("none", "unit_id_required", file)
        if (normalizedCommand.isBlank()) return rejected(normalizedUnitId, "exact_command_required", file)
        if (targetsBuiltInCore(normalizedCommand)) {
            return rejected(normalizedUnitId, "built_in_core_or_proot_one_cannot_be_user_locked", file)
        }
        return upsert(
            file = file,
            declaration = RuntimeProcessUnitUserLockDeclaration(
                unitId = normalizedUnitId,
                displayName = displayName.ifBlank { normalizedUnitId },
                match = RuntimeProcessUnitMatch(exactCommand = normalizedCommand),
                expectedMemoryLimitKb = expectedMemoryLimitKb
            )
        )
    }

    fun lockPidFile(
        file: File,
        unitId: String,
        pidFile: String,
        displayName: String = unitId,
        exactCommand: String? = null,
        expectedMemoryLimitKb: Long? = null
    ): RuntimeProcessUnitUserLockResult {
        val normalizedUnitId = unitId.trim()
        val normalizedPidFile = pidFile.trim()
        if (normalizedUnitId.isBlank()) return rejected("none", "unit_id_required", file)
        if (!RuntimeProcessUnitPidFilePathPolicy.isAllowed(normalizedPidFile)) {
            return rejected(normalizedUnitId, "pid_file_path_not_allowed", file)
        }
        exactCommand?.takeIf { targetsBuiltInCore(it) }?.let {
            return rejected(normalizedUnitId, "built_in_core_or_proot_one_cannot_be_user_locked", file)
        }
        return upsert(
            file = file,
            declaration = RuntimeProcessUnitUserLockDeclaration(
                unitId = normalizedUnitId,
                displayName = displayName.ifBlank { normalizedUnitId },
                match = RuntimeProcessUnitMatch(
                    pidFile = normalizedPidFile,
                    exactCommand = exactCommand?.trim()?.takeIf { it.isNotBlank() }
                ),
                expectedMemoryLimitKb = expectedMemoryLimitKb
            )
        )
    }

    fun rejectCommandContainsOnly(
        file: File,
        unitId: String,
        commandContains: List<String>
    ): RuntimeProcessUnitUserLockResult {
        val normalizedUnitId = unitId.trim().ifBlank { "none" }
        return rejected(
            unitId = normalizedUnitId,
            reason = if (commandContains.any { it.isNotBlank() }) {
                "command_contains_only_not_allowed_for_user_lock_entry"
            } else {
                "strong_match_required_for_user_lock_entry"
            },
            file = file
        )
    }

    fun unlock(file: File, unitId: String): RuntimeProcessUnitUserLockResult {
        val normalizedUnitId = unitId.trim()
        if (normalizedUnitId.isBlank()) return rejected("none", "unit_id_required", file)
        return runCatching {
            val manifest = readOrCreateManifest(file)
            val units = manifest.optJSONArray("units") ?: JSONArray()
            val nextUnits = JSONArray()
            var removed = false
            for (index in 0 until units.length()) {
                val unit = units.optJSONObject(index) ?: continue
                if (unit.optString("id") == normalizedUnitId) {
                    removed = true
                } else {
                    nextUnits.put(unit)
                }
            }
            if (!removed) {
                return RuntimeProcessUnitUserLockResult(
                    status = RuntimeProcessUnitUserLockResultStatus.NOT_FOUND,
                    unitId = normalizedUnitId,
                    reason = "user_lock_declaration_not_found",
                    manifestPath = file.absolutePath
                )
            }
            manifest.put("units", nextUnits)
            writeManifest(file, manifest)
            RuntimeProcessUnitUserLockResult(
                status = RuntimeProcessUnitUserLockResultStatus.REMOVED,
                unitId = normalizedUnitId,
                reason = "user_lock_declaration_removed_no_runtime_action",
                manifestPath = file.absolutePath
            )
        }.getOrElse { error ->
            failed(normalizedUnitId, error, file)
        }
    }

    fun upsert(
        file: File,
        declaration: RuntimeProcessUnitUserLockDeclaration
    ): RuntimeProcessUnitUserLockResult {
        val validation = validateDeclaration(declaration, file)
        if (validation != null) return validation
        return runCatching {
            val manifest = readOrCreateManifest(file)
            val units = manifest.optJSONArray("units") ?: JSONArray()
            val nextUnits = JSONArray()
            var replaced = false
            for (index in 0 until units.length()) {
                val unit = units.optJSONObject(index) ?: continue
                if (unit.optString("id") == declaration.unitId) {
                    nextUnits.put(declaration.toJson())
                    replaced = true
                } else {
                    nextUnits.put(unit)
                }
            }
            if (!replaced) {
                nextUnits.put(declaration.toJson())
            }
            manifest.put("units", nextUnits)
            writeManifest(file, manifest)
            RuntimeProcessUnitUserLockResult(
                status = RuntimeProcessUnitUserLockResultStatus.WRITTEN,
                unitId = declaration.unitId,
                reason = if (replaced) {
                    "user_lock_declaration_updated_no_runtime_action"
                } else {
                    "user_lock_declaration_created_no_runtime_action"
                },
                manifestPath = file.absolutePath
            )
        }.getOrElse { error ->
            failed(declaration.unitId, error, file)
        }
    }

    private fun validateDeclaration(
        declaration: RuntimeProcessUnitUserLockDeclaration,
        file: File
    ): RuntimeProcessUnitUserLockResult? {
        if (declaration.unitId.isBlank()) return rejected("none", "unit_id_required", file)
        val match = declaration.match
        val hasRuntimeId = !match.runtimeId.isNullOrBlank()
        val hasPidFile = !match.pidFile.isNullOrBlank()
        val hasExactCommand = !match.exactCommand.isNullOrBlank()
        if (!hasRuntimeId && !hasPidFile && !hasExactCommand) {
            return rejected(declaration.unitId, "strong_match_required_for_user_lock_entry", file)
        }
        if (match.commandContains.isNotEmpty() && !hasRuntimeId && !hasPidFile && !hasExactCommand) {
            return rejected(declaration.unitId, "command_contains_only_not_allowed_for_user_lock_entry", file)
        }
        if (hasPidFile && !RuntimeProcessUnitPidFilePathPolicy.isAllowed(match.pidFile)) {
            return rejected(declaration.unitId, "pid_file_path_not_allowed", file)
        }
        val targetText = listOfNotNull(match.runtimeId, match.exactCommand).joinToString(" ")
        if (targetsBuiltInCore(targetText)) {
            return rejected(declaration.unitId, "built_in_core_or_proot_one_cannot_be_user_locked", file)
        }
        return null
    }

    private fun RuntimeProcessUnitUserLockDeclaration.toJson(): JSONObject {
        val unit = JSONObject()
        unit.put("id", unitId)
        unit.put("displayName", displayName)
        unit.put("tier", RuntimeProcessUnitTier.USER_LOCKED.name)
        unit.put("manualKillPolicy", RuntimeProcessUnitManualKillPolicy.WAIT_CONFIRM.name)
        unit.put("match", match.toJson())
        unit.put("exec", JSONObject().put("stopMode", "NONE").put("restartMode", "NEVER"))
        unit.put(
            "protection",
            JSONObject()
                .put("userEditable", true)
                .put("allowReclaim", false)
                .put("allowKill", false)
                .put("allowRestart", false)
                .put("requiresMemoryAdmission", false)
        )
        val resource = JSONObject().put("unlimitedMemory", unlimitedMemory)
        expectedMemoryLimitKb
            ?.takeIf { it > 0L && !unlimitedMemory }
            ?.let { resource.put("expectedMemoryLimitKb", it) }
        unit.put("resource", resource)
        return unit
    }

    private fun RuntimeProcessUnitMatch.toJson(): JSONObject {
        val json = JSONObject()
        runtimeId?.takeIf { it.isNotBlank() }?.let { json.put("runtimeId", it) }
        pidFile?.takeIf { it.isNotBlank() }?.let { json.put("pidFile", it) }
        exactCommand?.takeIf { it.isNotBlank() }?.let { json.put("exactCommand", it) }
        processGroupId?.takeIf { it > 0 }?.let { json.put("processGroup", it) }
        if (commandContains.isNotEmpty()) {
            json.put("commandContains", JSONArray(commandContains))
        }
        return json
    }

    private fun readOrCreateManifest(file: File): JSONObject {
        val existing = if (file.exists() && file.length() > 0L) {
            runCatching { JSONObject(file.readText()) }.getOrNull()
        } else {
            null
        }
        return (existing ?: JSONObject())
            .putIfMissing("version", 1)
            .putIfMissing("authority", "ubuntu_advisory")
            .putIfMissing("boundary", "declaration_only_android_observes_no_direct_kill_restart_or_quarantine")
            .also {
                if (it.optJSONArray("units") == null) {
                    it.put("units", JSONArray())
                }
            }
    }

    private fun writeManifest(file: File, manifest: JSONObject) {
        file.parentFile?.mkdirs()
        file.writeText(manifest.toString(2))
    }

    private fun JSONObject.putIfMissing(key: String, value: Any): JSONObject {
        if (!has(key)) put(key, value)
        return this
    }

    private fun rejected(
        unitId: String,
        reason: String,
        file: File
    ): RuntimeProcessUnitUserLockResult {
        return RuntimeProcessUnitUserLockResult(
            status = RuntimeProcessUnitUserLockResultStatus.REJECTED,
            unitId = unitId,
            reason = reason,
            manifestPath = file.absolutePath
        )
    }

    private fun failed(
        unitId: String,
        error: Throwable,
        file: File
    ): RuntimeProcessUnitUserLockResult {
        return RuntimeProcessUnitUserLockResult(
            status = RuntimeProcessUnitUserLockResultStatus.FAILED,
            unitId = unitId,
            reason = error.message ?: "user_lock_manifest_write_failed",
            manifestPath = file.absolutePath
        )
    }

    private fun unitIdFor(prefix: String, value: String): String {
        val safe = value
            .lowercase()
            .map { char -> if (char.isLetterOrDigit() || char == '-' || char == '_') char else '-' }
            .joinToString("")
            .replace(Regex("-+"), "-")
            .trim('-')
            .ifBlank { "runtime" }
        return "user-locked-$prefix-$safe"
    }

    private fun targetsBuiltInCore(text: String): Boolean {
        val normalized = text.lowercase()
        return "container-supervisor" in normalized ||
            "container_supervisor" in normalized ||
            "critical-core" in normalized ||
            "critical_core" in normalized ||
            "proot-capacity-worker-1" in normalized ||
            "proot_capacity_worker_1" in normalized ||
            normalized.endsWith("-proot-1")
    }
}
