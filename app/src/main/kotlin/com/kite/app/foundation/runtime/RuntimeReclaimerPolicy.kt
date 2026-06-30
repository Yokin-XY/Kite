package com.kite.app.foundation.runtime

import android.content.Context
import com.kite.app.foundation.logging.Logger
import com.kite.app.foundation.service.RuntimeRetentionClass
import com.kite.app.foundation.workspace.WorkSurfaceRuntimeBridge
import com.kite.app.foundation.workspace.WorkspaceBuildSupport
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

enum class RuntimeReclaimerProfile(
    val label: String,
    val description: String,
    val minPressureLevel: RuntimePressureLevel?,
    val allowBatchAtHigh: Boolean,
    val allowBatchAtCritical: Boolean,
    val allowUnknownAtHigh: Boolean,
    val allowUnknownAtCritical: Boolean,
    val minReclaimIntervalMs: Long,
    val batchMinAgeMs: Long
) {
    OBSERVE_ONLY(
        label = "Observe only",
        description = "Do not auto reclaim anything. Only publish pressure and candidates.",
        minPressureLevel = null,
        allowBatchAtHigh = false,
        allowBatchAtCritical = false,
        allowUnknownAtHigh = false,
        allowUnknownAtCritical = false,
        minReclaimIntervalMs = 60_000L,
        batchMinAgeMs = 10 * 60_000L
    ),
    CONSERVATIVE(
        label = "Conservative",
        description = "Only reclaim when pressure is critical. Keep unknown roots manual-only.",
        minPressureLevel = RuntimePressureLevel.CRITICAL,
        allowBatchAtHigh = false,
        allowBatchAtCritical = true,
        allowUnknownAtHigh = false,
        allowUnknownAtCritical = false,
        minReclaimIntervalMs = 90_000L,
        batchMinAgeMs = 10 * 60_000L
    ),
    BALANCED(
        label = "Balanced",
        description = "Reclaim registered ephemeral roots at high pressure, batch at critical, and only classified unknown roots.",
        minPressureLevel = RuntimePressureLevel.HIGH,
        allowBatchAtHigh = false,
        allowBatchAtCritical = true,
        allowUnknownAtHigh = false,
        allowUnknownAtCritical = true,
        minReclaimIntervalMs = 60_000L,
        batchMinAgeMs = 5 * 60_000L
    ),
    AGGRESSIVE(
        label = "Aggressive",
        description = "Reclaim registered ephemeral roots at high pressure, batch sooner, and honor classified unknown auto-reclaim rules.",
        minPressureLevel = RuntimePressureLevel.HIGH,
        allowBatchAtHigh = true,
        allowBatchAtCritical = true,
        allowUnknownAtHigh = true,
        allowUnknownAtCritical = true,
        minReclaimIntervalMs = 45_000L,
        batchMinAgeMs = 3 * 60_000L
    );

    fun allowsReclaim(level: RuntimePressureLevel): Boolean {
        val minimum = minPressureLevel ?: return false
        return level.ordinal >= minimum.ordinal
    }

    fun allowsBatch(level: RuntimePressureLevel): Boolean {
        return when (level) {
            RuntimePressureLevel.CRITICAL -> allowBatchAtCritical
            RuntimePressureLevel.HIGH -> allowBatchAtHigh
            else -> false
        }
    }

    fun allowsUnknown(level: RuntimePressureLevel): Boolean {
        return when (level) {
            RuntimePressureLevel.CRITICAL -> allowUnknownAtCritical
            RuntimePressureLevel.HIGH -> allowUnknownAtHigh
            else -> false
        }
    }
}

enum class UnknownProcessMatchField {
    TITLE,
    COMMAND,
    COMMAND_LINE,
    SOURCE_LABEL
}

enum class UnknownProcessMatchMode {
    CONTAINS,
    EXACT,
    PREFIX,
    SUFFIX
}

data class UnknownProcessRule(
    val id: String,
    val enabled: Boolean = true,
    val matchField: UnknownProcessMatchField = UnknownProcessMatchField.COMMAND_LINE,
    val matchMode: UnknownProcessMatchMode = UnknownProcessMatchMode.CONTAINS,
    val pattern: String,
    val retentionClass: RuntimeRetentionClass = RuntimeRetentionClass.BATCH,
    val reclaimPriority: Int? = null,
    val resident: Boolean? = null,
    val autoReclaimAllowed: Boolean = false,
    val note: String? = null
) {
    fun toJson(): JSONObject {
        return JSONObject()
            .put("id", id)
            .put("enabled", enabled)
            .put("matchField", matchField.name)
            .put("matchMode", matchMode.name)
            .put("pattern", pattern)
            .put("retentionClass", retentionClass.name)
            .put("reclaimPriority", reclaimPriority)
            .put("resident", resident)
            .put("autoReclaimAllowed", autoReclaimAllowed)
            .put("note", note)
    }

    fun matches(process: ContainerProcessRecord): Boolean {
        if (!enabled) return false
        if (pattern.isBlank()) return false
        val haystack = when (matchField) {
            UnknownProcessMatchField.TITLE -> process.title
            UnknownProcessMatchField.COMMAND -> process.command
            UnknownProcessMatchField.COMMAND_LINE -> process.commandLine
            UnknownProcessMatchField.SOURCE_LABEL -> process.sourceLabel
        }.trim()
        if (haystack.isBlank()) return false
        val needle = pattern.trim()
        return when (matchMode) {
            UnknownProcessMatchMode.CONTAINS -> haystack.contains(needle, ignoreCase = true)
            UnknownProcessMatchMode.EXACT -> haystack.equals(needle, ignoreCase = true)
            UnknownProcessMatchMode.PREFIX -> haystack.startsWith(needle, ignoreCase = true)
            UnknownProcessMatchMode.SUFFIX -> haystack.endsWith(needle, ignoreCase = true)
        }
    }

    fun toClassification(): RuntimeUnknownProcessClassification {
        val resolvedResident = resident ?: retentionClass.resident
        val resolvedPriority = reclaimPriority ?: retentionClass.reclaimPriority
        return RuntimeUnknownProcessClassification(
            retentionClass = retentionClass,
            resident = resolvedResident,
            reclaimPriority = resolvedPriority,
            autoReclaimAllowed = autoReclaimAllowed,
            classificationSource = "policy:$id",
            classificationReason = note?.takeIf { it.isNotBlank() }
                ?: "${matchField.name.lowercase()}.${matchMode.name.lowercase()}=${pattern.trim()}"
        )
    }

    companion object {
        fun fromJson(json: JSONObject): UnknownProcessRule {
            return UnknownProcessRule(
                id = json.optString("id", "rule-${System.currentTimeMillis()}"),
                enabled = json.optBoolean("enabled", true),
                matchField = UnknownProcessMatchField.entries.firstOrNull {
                    it.name == json.optString("matchField", UnknownProcessMatchField.COMMAND_LINE.name)
                } ?: UnknownProcessMatchField.COMMAND_LINE,
                matchMode = UnknownProcessMatchMode.entries.firstOrNull {
                    it.name == json.optString("matchMode", UnknownProcessMatchMode.CONTAINS.name)
                } ?: UnknownProcessMatchMode.CONTAINS,
                pattern = json.optString("pattern", ""),
                retentionClass = RuntimeRetentionClass.entries.firstOrNull {
                    it.name == json.optString("retentionClass", RuntimeRetentionClass.BATCH.name)
                } ?: RuntimeRetentionClass.BATCH,
                reclaimPriority = json.optInt("reclaimPriority").takeIf {
                    !json.isNull("reclaimPriority")
                },
                resident = json.optBoolean("resident").takeIf { !json.isNull("resident") },
                autoReclaimAllowed = json.optBoolean("autoReclaimAllowed", false),
                note = json.optString("note").takeIf { !json.isNull("note") }
            )
        }
    }
}

data class RuntimeUnknownProcessClassification(
    val retentionClass: RuntimeRetentionClass,
    val resident: Boolean,
    val reclaimPriority: Int,
    val autoReclaimAllowed: Boolean,
    val classificationSource: String,
    val classificationReason: String
)

data class RuntimeMemoryPressurePolicy(
    val memoryBudgetKb: Long? = null,
    val elevatedRssPercent: Int = 50,
    val highRssPercent: Int = 70,
    val criticalRssPercent: Int = 85,
    val elevatedHostAvailableKb: Long = 1280L * 1024L,
    val highHostAvailableKb: Long = 768L * 1024L,
    val criticalHostAvailableKb: Long = 384L * 1024L
) {
    fun toJson(): JSONObject {
        return JSONObject()
            .put("memoryBudgetKb", memoryBudgetKb)
            .put("elevatedRssPercent", elevatedRssPercent)
            .put("highRssPercent", highRssPercent)
            .put("criticalRssPercent", criticalRssPercent)
            .put("elevatedHostAvailableKb", elevatedHostAvailableKb)
            .put("highHostAvailableKb", highHostAvailableKb)
            .put("criticalHostAvailableKb", criticalHostAvailableKb)
    }

    companion object {
        fun fromJson(json: JSONObject?): RuntimeMemoryPressurePolicy {
            if (json == null) return RuntimeMemoryPressurePolicy()
            return RuntimeMemoryPressurePolicy(
                memoryBudgetKb = json.optNullableLong("memoryBudgetKb")?.takeIf { it > 0L },
                elevatedRssPercent = json.optInt("elevatedRssPercent", 50).coerceIn(1, 99),
                highRssPercent = json.optInt("highRssPercent", 70).coerceIn(1, 99),
                criticalRssPercent = json.optInt("criticalRssPercent", 85).coerceIn(1, 99),
                elevatedHostAvailableKb = json.optLong("elevatedHostAvailableKb", 1280L * 1024L).coerceAtLeast(0L),
                highHostAvailableKb = json.optLong("highHostAvailableKb", 768L * 1024L).coerceAtLeast(0L),
                criticalHostAvailableKb = json.optLong("criticalHostAvailableKb", 384L * 1024L).coerceAtLeast(0L)
            )
        }
    }
}

data class RuntimeReclaimerPolicy(
    val version: Int = 1,
    val activeProfile: RuntimeReclaimerProfile = RuntimeReclaimerProfile.BALANCED,
    val memoryPressure: RuntimeMemoryPressurePolicy = RuntimeMemoryPressurePolicy(),
    val unknownProcessRules: List<UnknownProcessRule> = emptyList(),
    val policyPath: String? = null,
    val loadedAtMs: Long = System.currentTimeMillis(),
    val loadStatus: String = "default",
    val loadError: String? = null
) {
    val unknownRuleCount: Int
        get() = unknownProcessRules.size

    fun toJson(): JSONObject {
        return JSONObject()
            .put("version", version)
            .put("activeProfile", activeProfile.name)
            .put("memoryPressure", memoryPressure.toJson())
            .put(
                "unknownProcessRules",
                JSONArray().apply {
                    unknownProcessRules.forEach { rule -> put(rule.toJson()) }
                }
            )
    }

    companion object {
        fun default(policyPath: String? = null): RuntimeReclaimerPolicy {
            return RuntimeReclaimerPolicy(
                version = 1,
                activeProfile = RuntimeReclaimerProfile.BALANCED,
                memoryPressure = RuntimeMemoryPressurePolicy(),
                unknownProcessRules = emptyList(),
                policyPath = policyPath
            )
        }

        fun fromJson(json: JSONObject, policyPath: String?, loadStatus: String = "loaded"): RuntimeReclaimerPolicy {
            val rules = json.optJSONArray("unknownProcessRules")
                ?.let { array ->
                    buildList {
                        for (index in 0 until array.length()) {
                            val ruleJson = array.optJSONObject(index) ?: continue
                            add(UnknownProcessRule.fromJson(ruleJson))
                        }
                    }
                }
                ?: emptyList()
            return RuntimeReclaimerPolicy(
                version = json.optInt("version", 1).coerceAtLeast(1),
                activeProfile = RuntimeReclaimerProfile.entries.firstOrNull {
                    it.name == json.optString("activeProfile", RuntimeReclaimerProfile.BALANCED.name)
                } ?: RuntimeReclaimerProfile.BALANCED,
                memoryPressure = RuntimeMemoryPressurePolicy.fromJson(json.optJSONObject("memoryPressure")),
                unknownProcessRules = rules,
                policyPath = policyPath,
                loadStatus = loadStatus
            )
        }
    }
}

object RuntimeReclaimerPolicyStore {

    private const val LOG_TAG = "RuntimeReclaimerPolicy"

    fun load(context: Context): RuntimeReclaimerPolicy {
        val file = resolveFile(context)
        if (file == null) {
            return RuntimeReclaimerPolicy.default().copy(loadStatus = "workspace_missing")
        }
        if (!file.exists()) {
            val defaultPolicy = RuntimeReclaimerPolicy.default(file.absolutePath)
            writeDefault(file, defaultPolicy)
            return defaultPolicy.copy(loadStatus = "bootstrapped_default")
        }
        return runCatching {
            RuntimeReclaimerPolicy.fromJson(
                json = JSONObject(file.readText()),
                policyPath = file.absolutePath
            )
        }.getOrElse { error ->
            Logger.e(LOG_TAG, "failed to load runtime reclaimer policy: ${error.message}")
            RuntimeReclaimerPolicy.default(file.absolutePath).copy(
                loadStatus = "error_default",
                loadError = error.message
            )
        }
    }

    fun classifyUnknownProcess(
        process: ContainerProcessRecord,
        policy: RuntimeReclaimerPolicy
    ): RuntimeUnknownProcessClassification? {
        return policy.unknownProcessRules
            .firstOrNull { rule -> rule.matches(process) }
            ?.toClassification()
    }

    private fun writeDefault(file: File, policy: RuntimeReclaimerPolicy) {
        runCatching {
            file.parentFile?.let { parent ->
                if (!parent.exists()) {
                    parent.mkdirs()
                }
            }
            file.writeText(policy.toJson().toString(2) + "\n")
        }.onFailure { error ->
            Logger.e(LOG_TAG, "failed to bootstrap runtime reclaimer policy: ${error.message}")
        }
    }

    private fun resolveFile(context: Context): File? {
        val workspacePath = WorkSurfaceRuntimeBridge.getSavedContainer(context)?.workspacePath
            ?.takeIf { it.isNotBlank() }
            ?: return null
        return WorkspaceBuildSupport.runtimeReclaimerPolicyFile(File(workspacePath))
    }
}

private fun JSONObject.optNullableLong(name: String): Long? {
    if (!has(name) || isNull(name)) return null
    return runCatching { optLong(name) }.getOrNull()
}
