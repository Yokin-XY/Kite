package com.kite.app.foundation.runtime

import android.content.Context
import com.kite.app.foundation.workspace.WorkSurfaceRuntimeBridge
import com.kite.app.foundation.workspace.WorkspaceBuildSupport
import java.io.File
import org.json.JSONObject

data class RuntimeProotCapacityExecutorPolicy(
    val path: String = WorkspaceBuildSupport.CONTAINER_PROOT_CAPACITY_EXECUTOR_POLICY_PATH,
    val loadStatus: String = "workspace_missing",
    val loadError: String = "none",
    val enabled: Boolean = false,
    val maxProots: Int = 3,
    val baseProotMemoryKb: Long = 64L * 1024L,
    val estimatedTaskMemoryKb: Long? = null,
    val safetyMarginKb: Long = 512L * 1024L,
    val idleGraceMs: Long = 60_000L,
    val minLifetimeMs: Long = 120_000L,
    val scaleOutCooldownMs: Long = 120_000L,
    val capacityRuntimeIds: List<String> = emptyList(),
    val secondProotRuntimeId: String = "",
    val downlineRuntimeIds: Set<String> = emptySet(),
    val allowQueueCreation: Boolean = false
) {
    val expansionRuntimeIds: List<String>
        get() = capacityRuntimeIds.ifEmpty {
            listOf(secondProotRuntimeId)
        }.map { it.trim() }.filter { it.isNotBlank() }

    val hasCapacityBinding: Boolean
        get() = expansionRuntimeIds.isNotEmpty()

    val hasSecondProotBinding: Boolean
        get() = hasCapacityBinding
}

object RuntimeProotCapacityExecutorPolicyStore {
    fun load(context: Context): RuntimeProotCapacityExecutorPolicy {
        val file = resolveFile(context) ?: return RuntimeProotCapacityExecutorPolicy()
        if (!file.exists()) {
            return RuntimeProotCapacityExecutorPolicy(loadStatus = "missing")
        }
        return runCatching {
            fromJson(JSONObject(file.readText()))
                .copy(loadStatus = "loaded", loadError = "none")
        }.getOrElse { error ->
            RuntimeProotCapacityExecutorPolicy(
                loadStatus = "parse_error",
                loadError = error.message.orEmpty().ifBlank { error::class.java.simpleName }
            )
        }
    }

    private fun fromJson(json: JSONObject): RuntimeProotCapacityExecutorPolicy {
        val capacityIds = buildList {
            val array = json.optJSONArray("capacityRuntimeIds")
            if (array != null) {
                for (index in 0 until array.length()) {
                    val value = array.optString(index).trim()
                    if (value.isNotBlank()) {
                        add(value)
                    }
                }
            }
        }
        val downlineIds = buildSet {
            val array = json.optJSONArray("downlineRuntimeIds")
            if (array != null) {
                for (index in 0 until array.length()) {
                    val value = array.optString(index).trim()
                    if (value.isNotBlank()) {
                        add(value)
                    }
                }
            }
        }
        return RuntimeProotCapacityExecutorPolicy(
            enabled = json.optBoolean("enabled", false),
            maxProots = json.optPositiveInt("maxProots", 3).coerceAtLeast(1),
            baseProotMemoryKb = json.optPositiveLong("baseProotMemoryKb", 64L * 1024L),
            estimatedTaskMemoryKb = json.optNullablePositiveLong("estimatedTaskMemoryKb"),
            safetyMarginKb = json.optPositiveLong("safetyMarginKb", 512L * 1024L),
            idleGraceMs = json.optPositiveLong("idleGraceMs", 60_000L),
            minLifetimeMs = json.optPositiveLong("minLifetimeMs", 120_000L),
            scaleOutCooldownMs = json.optPositiveLong("scaleOutCooldownMs", 120_000L),
            capacityRuntimeIds = capacityIds,
            secondProotRuntimeId = json.optString("secondProotRuntimeId")
                .takeIf { !json.isNull("secondProotRuntimeId") }
                ?.trim()
                .orEmpty(),
            downlineRuntimeIds = downlineIds,
            allowQueueCreation = json.optBoolean("allowQueueCreation", false)
        )
    }

    private fun resolveFile(context: Context): File? {
        val workspacePath = WorkSurfaceRuntimeBridge.getSavedContainer(context)?.workspacePath
            ?: return null
        return WorkspaceBuildSupport.prootCapacityExecutorPolicyFile(File(workspacePath))
    }
}

private fun JSONObject.optPositiveInt(name: String, fallback: Int): Int {
    val value = optInt(name, fallback)
    return if (value > 0) value else fallback
}

private fun JSONObject.optPositiveLong(name: String, fallback: Long): Long {
    val value = optLong(name, fallback)
    return if (value > 0L) value else fallback
}

private fun JSONObject.optNullablePositiveLong(name: String): Long? {
    if (!has(name) || isNull(name)) return null
    val value = optLong(name, 0L)
    return value.takeIf { it > 0L }
}
