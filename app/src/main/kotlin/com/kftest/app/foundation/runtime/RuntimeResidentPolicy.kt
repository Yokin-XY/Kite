package com.kftest.app.foundation.runtime

import android.content.ComponentCallbacks2
import android.content.Context
import com.kftest.app.foundation.logging.Logger
import com.kftest.app.foundation.service.BackgroundRuntimeRecord
import com.kftest.app.foundation.workspace.WorkSurfaceRuntimeBridge
import com.kftest.app.foundation.workspace.WorkspaceBuildSupport
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

enum class RuntimeResidentProfile(
    val autoStartResidents: Boolean,
    val autoRecoverResidents: Boolean,
    val trimProcessRefreshMinLevel: Int,
    val trimTaskRefreshMinLevel: Int
) {
    OBSERVE_ONLY(
        autoStartResidents = false,
        autoRecoverResidents = false,
        trimProcessRefreshMinLevel = Int.MAX_VALUE,
        trimTaskRefreshMinLevel = Int.MAX_VALUE
    ),
    CORE_ONLY(
        autoStartResidents = false,
        autoRecoverResidents = false,
        trimProcessRefreshMinLevel = ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL,
        trimTaskRefreshMinLevel = Int.MAX_VALUE
    ),
    BALANCED(
        autoStartResidents = true,
        autoRecoverResidents = true,
        trimProcessRefreshMinLevel = ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW,
        trimTaskRefreshMinLevel = ComponentCallbacks2.TRIM_MEMORY_BACKGROUND
    ),
    AGGRESSIVE(
        autoStartResidents = true,
        autoRecoverResidents = true,
        trimProcessRefreshMinLevel = ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE,
        trimTaskRefreshMinLevel = ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW
    )
}

enum class RuntimeRecoveryTrigger(
    val policyKey: String,
    val manualBypass: Boolean = false
) {
    SERVICE_START("service_start"),
    TRIM_MEMORY("trim_memory"),
    HEALTH_RECONCILE("health_reconcile"),
    AUTO_RESTART("auto_restart"),
    CORE_ENSURE("core_ensure"),
    MANUAL("manual", manualBypass = true),
    MANUAL_RESTART("manual_restart", manualBypass = true),
    UNKNOWN("unknown");

    companion object {
        fun fromResidentReason(reason: String): RuntimeRecoveryTrigger {
            val normalized = reason.trim().lowercase()
            return when {
                normalized.startsWith("service-start") -> SERVICE_START
                normalized.startsWith("trim-memory:") -> TRIM_MEMORY
                normalized.startsWith("health-reconciler") -> HEALTH_RECONCILE
                normalized.startsWith("core-runtime-missing") -> CORE_ENSURE
                normalized == "manual" || normalized.startsWith("manual:") -> MANUAL
                normalized.startsWith("manual-restart") -> MANUAL_RESTART
                else -> UNKNOWN
            }
        }

        fun fromAutoRestartReason(reason: String): RuntimeRecoveryTrigger {
            val normalized = reason.trim().lowercase()
            return when {
                normalized.startsWith("process-exit:") -> AUTO_RESTART
                normalized.startsWith("start-failure") -> AUTO_RESTART
                normalized.startsWith("status-refresh:") -> AUTO_RESTART
                normalized.startsWith("health-reconciler") -> HEALTH_RECONCILE
                normalized.startsWith("core-runtime-missing") -> CORE_ENSURE
                normalized.startsWith("manual-restart") -> MANUAL_RESTART
                else -> UNKNOWN
            }
        }
    }
}

data class RuntimeResidentOverride(
    val runtimeId: String,
    val enabled: Boolean = true,
    val keepResident: Boolean? = null,
    val allowAutoStart: Boolean? = null,
    val allowAutoRecover: Boolean? = null,
    val allowedRecoveryTriggers: Set<RuntimeRecoveryTrigger>? = null,
    val blockedRecoveryTriggers: Set<RuntimeRecoveryTrigger> = emptySet(),
    val note: String? = null
) {
    fun toJson(): JSONObject {
        return JSONObject()
            .put("runtimeId", runtimeId)
            .put("enabled", enabled)
            .put("keepResident", keepResident)
            .put("allowAutoStart", allowAutoStart)
            .put("allowAutoRecover", allowAutoRecover)
            .put(
                "allowedRecoveryTriggers",
                allowedRecoveryTriggers?.let { triggers ->
                    JSONArray().apply {
                        triggers.forEach { trigger -> put(trigger.name) }
                    }
                } ?: JSONObject.NULL
            )
            .put(
                "blockedRecoveryTriggers",
                JSONArray().apply {
                    blockedRecoveryTriggers.forEach { trigger -> put(trigger.name) }
                }
            )
            .put("note", note)
    }

    companion object {
        fun fromJson(json: JSONObject): RuntimeResidentOverride {
            return RuntimeResidentOverride(
                runtimeId = json.optString("runtimeId").trim(),
                enabled = json.optBoolean("enabled", true),
                keepResident = json.optBoolean("keepResident").takeIf { !json.isNull("keepResident") },
                allowAutoStart = json.optBoolean("allowAutoStart").takeIf { !json.isNull("allowAutoStart") },
                allowAutoRecover = json.optBoolean("allowAutoRecover").takeIf { !json.isNull("allowAutoRecover") },
                allowedRecoveryTriggers = json.optJSONArray("allowedRecoveryTriggers")
                    ?.toRuntimeRecoveryTriggerSet(),
                blockedRecoveryTriggers = json.optJSONArray("blockedRecoveryTriggers")
                    ?.toRuntimeRecoveryTriggerSet()
                    ?: emptySet(),
                note = json.optString("note").takeIf { !json.isNull("note") }
            )
        }
    }
}

data class RuntimeResidentDecision(
    val keepResident: Boolean,
    val allowAutoStart: Boolean,
    val allowAutoRecover: Boolean,
    val defaultAllowedTriggers: Set<RuntimeRecoveryTrigger>,
    val overrideAllowedTriggers: Set<RuntimeRecoveryTrigger>?,
    val blockedTriggers: Set<RuntimeRecoveryTrigger>,
    val source: String,
    val reason: String
) {
    fun allowsTrigger(trigger: RuntimeRecoveryTrigger): Boolean {
        if (!keepResident) {
            return false
        }
        if (trigger.manualBypass) {
            return true
        }
        if (trigger in blockedTriggers) {
            return false
        }
        val effectiveAllowed = overrideAllowedTriggers ?: defaultAllowedTriggers
        return trigger in effectiveAllowed
    }

    fun allowedTriggerSummary(): String {
        val allowed = (overrideAllowedTriggers ?: defaultAllowedTriggers)
            .joinToString(",") { it.name }
            .ifBlank { "<none>" }
        val blocked = blockedTriggers
            .joinToString(",") { it.name }
            .ifBlank { "<none>" }
        return "allowed=$allowed blocked=$blocked"
    }
}

data class RuntimeResidentPolicy(
    val version: Int = 2,
    val activeProfile: RuntimeResidentProfile = RuntimeResidentProfile.BALANCED,
    val runtimeOverrides: List<RuntimeResidentOverride> = emptyList(),
    val policyPath: String? = null
) {
    fun toJson(): JSONObject {
        return JSONObject()
            .put("version", version)
            .put("activeProfile", activeProfile.name)
            .put(
                "runtimeOverrides",
                JSONArray().apply {
                    runtimeOverrides.forEach { override ->
                        put(override.toJson())
                    }
                }
            )
    }

    companion object {
        fun default(policyPath: String? = null): RuntimeResidentPolicy {
            return RuntimeResidentPolicy(
                version = 2,
                activeProfile = RuntimeResidentProfile.BALANCED,
                runtimeOverrides = emptyList(),
                policyPath = policyPath
            )
        }

        fun fromJson(json: JSONObject, policyPath: String?): RuntimeResidentPolicy {
            val overrides = json.optJSONArray("runtimeOverrides")
                ?.let { array ->
                    buildList {
                        for (index in 0 until array.length()) {
                            val entry = array.optJSONObject(index) ?: continue
                            val parsed = RuntimeResidentOverride.fromJson(entry)
                            if (parsed.runtimeId.isNotBlank()) {
                                add(parsed)
                            }
                        }
                    }
                }
                ?: emptyList()
            return RuntimeResidentPolicy(
                version = json.optInt("version", 2),
                activeProfile = RuntimeResidentProfile.entries.firstOrNull {
                    it.name == json.optString("activeProfile", RuntimeResidentProfile.BALANCED.name)
                } ?: RuntimeResidentProfile.BALANCED,
                runtimeOverrides = overrides,
                policyPath = policyPath
            )
        }
    }
}

object RuntimeResidentPolicyStore {

    private const val LOG_TAG = "RuntimeResidentPolicy"

    fun load(context: Context): RuntimeResidentPolicy {
        val file = resolvePolicyFile(context) ?: return RuntimeResidentPolicy.default()
        if (!file.exists()) {
            val defaultPolicy = RuntimeResidentPolicy.default(file.absolutePath)
            writeDefault(file, defaultPolicy)
            return defaultPolicy
        }
        return runCatching {
            RuntimeResidentPolicy.fromJson(
                JSONObject(file.readText()),
                file.absolutePath
            )
        }.getOrElse { error ->
            Logger.e(LOG_TAG, "load failed: ${error.message}")
            RuntimeResidentPolicy.default(file.absolutePath)
        }
    }

    fun evaluate(
        record: BackgroundRuntimeRecord,
        policy: RuntimeResidentPolicy
    ): RuntimeResidentDecision {
        val profile = policy.activeProfile
        val matchedOverride = policy.runtimeOverrides.firstOrNull {
            it.enabled && it.runtimeId == record.id
        }

        val baseKeepResident = record.retentionClass.resident
        val keepResident = matchedOverride?.keepResident ?: baseKeepResident
        val defaultAutoStart = when {
            record.retentionClass == com.kftest.app.foundation.service.RuntimeRetentionClass.CRITICAL_CORE -> true
            else -> keepResident && profile.autoStartResidents
        }
        val defaultAutoRecover = when {
            record.retentionClass == com.kftest.app.foundation.service.RuntimeRetentionClass.CRITICAL_CORE -> true
            else -> keepResident && profile.autoRecoverResidents
        }
        val allowAutoStart = if (record.retentionClass ==
            com.kftest.app.foundation.service.RuntimeRetentionClass.CRITICAL_CORE
        ) {
            true
        } else {
            (matchedOverride?.allowAutoStart ?: defaultAutoStart) && keepResident
        }
        val allowAutoRecover = if (record.retentionClass ==
            com.kftest.app.foundation.service.RuntimeRetentionClass.CRITICAL_CORE
        ) {
            true
        } else {
            (matchedOverride?.allowAutoRecover ?: defaultAutoRecover) && keepResident
        }
        val defaultAllowedTriggers = buildSet {
            if (allowAutoStart) {
                add(RuntimeRecoveryTrigger.SERVICE_START)
            }
            if (allowAutoRecover) {
                add(RuntimeRecoveryTrigger.TRIM_MEMORY)
                add(RuntimeRecoveryTrigger.HEALTH_RECONCILE)
                add(RuntimeRecoveryTrigger.AUTO_RESTART)
            }
            if (record.retentionClass ==
                com.kftest.app.foundation.service.RuntimeRetentionClass.CRITICAL_CORE
            ) {
                add(RuntimeRecoveryTrigger.CORE_ENSURE)
            }
        }
        val overrideAllowedTriggers = matchedOverride?.allowedRecoveryTriggers
        val blockedTriggers = matchedOverride?.blockedRecoveryTriggers ?: emptySet()

        val source = when {
            matchedOverride != null -> "policy:runtime_override"
            record.retentionClass ==
                com.kftest.app.foundation.service.RuntimeRetentionClass.CRITICAL_CORE ->
                "builtin:critical_core"
            else -> "builtin:retention_class"
        }
        val reason = buildString {
            append("profile=${profile.name}")
            append(" retention=${record.retentionClass.name}")
            append(" resident=$keepResident")
            append(" autoStart=$allowAutoStart")
            append(" autoRecover=$allowAutoRecover")
            append(" triggers=")
            append(
                (overrideAllowedTriggers ?: defaultAllowedTriggers)
                    .joinToString(",") { it.name }
                    .ifBlank { "<none>" }
            )
            append(" blocked=")
            append(blockedTriggers.joinToString(",") { it.name }.ifBlank { "<none>" })
            matchedOverride?.note
                ?.takeIf { it.isNotBlank() }
                ?.let { append(" note=").append(it) }
        }
        return RuntimeResidentDecision(
            keepResident = keepResident,
            allowAutoStart = allowAutoStart,
            allowAutoRecover = allowAutoRecover,
            defaultAllowedTriggers = defaultAllowedTriggers,
            overrideAllowedTriggers = overrideAllowedTriggers,
            blockedTriggers = blockedTriggers,
            source = source,
            reason = reason
        )
    }

    private fun resolvePolicyFile(context: Context): File? {
        val workspacePath = WorkSurfaceRuntimeBridge.getSavedContainer(context)?.workspacePath
            ?.takeIf { it.isNotBlank() }
            ?: return null
        return WorkspaceBuildSupport.runtimeResidentPolicyFile(File(workspacePath))
    }

    private fun writeDefault(file: File, policy: RuntimeResidentPolicy) {
        runCatching {
            file.parentFile?.mkdirs()
            file.writeText(policy.toJson().toString(2))
        }.onFailure { error ->
            Logger.e(LOG_TAG, "write default failed: ${error.message}")
        }
    }
}

private fun JSONArray.toRuntimeRecoveryTriggerSet(): Set<RuntimeRecoveryTrigger> {
    return buildSet {
        for (index in 0 until length()) {
            val raw = optString(index).trim()
            RuntimeRecoveryTrigger.entries
                .firstOrNull { it.name == raw }
                ?.let(::add)
        }
    }
}
