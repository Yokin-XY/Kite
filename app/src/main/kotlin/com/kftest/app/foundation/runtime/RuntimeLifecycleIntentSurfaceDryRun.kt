package com.kftest.app.foundation.runtime

import com.kftest.app.foundation.workspace.WorkspaceBuildSupport
import java.io.File
import org.json.JSONObject

enum class RuntimeLifecycleIntentSurfaceState {
    WORKSPACE_MISSING,
    NO_INTENT_FILE,
    EMPTY,
    INTENT_LOADED,
    INTENT_ERROR_IGNORED
}

enum class RuntimeLifecycleIntentSurfaceRecommendation {
    WAIT_FOR_WORKSPACE,
    KEEP_NO_INTENT,
    REVIEW_ADVISORY_INTENT,
    REPAIR_INTENT_JSON
}

data class RuntimeLifecycleIntentEntry(
    val intentId: String,
    val action: String,
    val workloadClass: RuntimeWorkloadClass?,
    val lane: RuntimeLaneKind?,
    val retention: RuntimeWorkloadRetention?,
    val advisoryAccepted: Boolean,
    val directActionRequested: Boolean,
    val reason: String
)

data class RuntimeLifecycleIntentSurfaceDryRunSnapshot(
    val mode: String = "ubuntu_lifecycle_intent_surface_observe_v0",
    val enforcementMode: String = "observe_only",
    val enforcementEnabled: Boolean = false,
    val generatedAtMs: Long = 0L,
    val state: RuntimeLifecycleIntentSurfaceState =
        RuntimeLifecycleIntentSurfaceState.WORKSPACE_MISSING,
    val recommendation: RuntimeLifecycleIntentSurfaceRecommendation =
        RuntimeLifecycleIntentSurfaceRecommendation.WAIT_FOR_WORKSPACE,
    val authority: String = "android_control_plane",
    val intentPath: String? = null,
    val loadStatus: String = "unknown",
    val loadError: String? = null,
    val androidExecutionOwner: Boolean = true,
    val ubuntuIntentAdvisory: Boolean = true,
    val ubuntuDirectStartAllowed: Boolean = false,
    val ubuntuDirectQueueAllowed: Boolean = false,
    val ubuntuDirectReclaimAllowed: Boolean = false,
    val prootDirectLaneControlAllowed: Boolean = false,
    val declaredIntentCount: Int = 0,
    val acceptedAdvisoryCount: Int = 0,
    val ignoredDirectActionCount: Int = 0,
    val invalidIntentCount: Int = 0,
    val workloadClassHintCount: Int = 0,
    val laneHintCount: Int = 0,
    val retentionHintCount: Int = 0,
    val entries: List<RuntimeLifecycleIntentEntry> = emptyList()
) {
    fun summary(): String {
        return "mode=$mode state=$state recommendation=$recommendation " +
            "path=${intentPath ?: "none"} loadStatus=$loadStatus " +
            "androidOwner=$androidExecutionOwner ubuntuAdvisory=$ubuntuIntentAdvisory " +
            "directStart=$ubuntuDirectStartAllowed directQueue=$ubuntuDirectQueueAllowed " +
            "declared=$declaredIntentCount accepted=$acceptedAdvisoryCount " +
            "ignoredDirect=$ignoredDirectActionCount invalid=$invalidIntentCount " +
            "enforcement=$enforcementEnabled"
    }

    fun toEnvText(maxEntries: Int = 8): String {
        return buildString {
            appendLine("lifecycle_intent_surface_mode=${mode.toLifecycleIntentEnvValue()}")
            appendLine("lifecycle_intent_surface_enforcement_mode=${enforcementMode.toLifecycleIntentEnvValue()}")
            appendLine("lifecycle_intent_surface_enforcement_enabled=$enforcementEnabled")
            appendLine("lifecycle_intent_surface_generated_at=$generatedAtMs")
            appendLine("lifecycle_intent_surface_state=${state.name}")
            appendLine("lifecycle_intent_surface_recommendation=${recommendation.name}")
            appendLine("lifecycle_intent_surface_authority=${authority.toLifecycleIntentEnvValue()}")
            appendLine("lifecycle_intent_surface_intent_path=${intentPath.toLifecycleIntentEnvValue()}")
            appendLine("lifecycle_intent_surface_load_status=${loadStatus.toLifecycleIntentEnvValue()}")
            appendLine("lifecycle_intent_surface_load_error=${loadError.toLifecycleIntentEnvValue()}")
            appendLine("lifecycle_intent_surface_android_execution_owner=$androidExecutionOwner")
            appendLine("lifecycle_intent_surface_ubuntu_intent_advisory=$ubuntuIntentAdvisory")
            appendLine("lifecycle_intent_surface_ubuntu_direct_start_allowed=$ubuntuDirectStartAllowed")
            appendLine("lifecycle_intent_surface_ubuntu_direct_queue_allowed=$ubuntuDirectQueueAllowed")
            appendLine("lifecycle_intent_surface_ubuntu_direct_reclaim_allowed=$ubuntuDirectReclaimAllowed")
            appendLine("lifecycle_intent_surface_proot_direct_lane_control_allowed=$prootDirectLaneControlAllowed")
            appendLine("lifecycle_intent_surface_declared_intent_count=$declaredIntentCount")
            appendLine("lifecycle_intent_surface_accepted_advisory_count=$acceptedAdvisoryCount")
            appendLine("lifecycle_intent_surface_ignored_direct_action_count=$ignoredDirectActionCount")
            appendLine("lifecycle_intent_surface_invalid_intent_count=$invalidIntentCount")
            appendLine("lifecycle_intent_surface_workload_class_hint_count=$workloadClassHintCount")
            appendLine("lifecycle_intent_surface_lane_hint_count=$laneHintCount")
            appendLine("lifecycle_intent_surface_retention_hint_count=$retentionHintCount")
            entries.take(maxEntries).forEachIndexed { index, entry ->
                val prefix = "lifecycle_intent_surface_entry_${index + 1}"
                appendLine("${prefix}_id=${entry.intentId.toLifecycleIntentEnvValue()}")
                appendLine("${prefix}_action=${entry.action.toLifecycleIntentEnvValue()}")
                appendLine("${prefix}_class=${entry.workloadClass?.name.toLifecycleIntentEnvValue()}")
                appendLine("${prefix}_lane=${entry.lane?.name.toLifecycleIntentEnvValue()}")
                appendLine("${prefix}_retention=${entry.retention?.name.toLifecycleIntentEnvValue()}")
                appendLine("${prefix}_advisory_accepted=${entry.advisoryAccepted}")
                appendLine("${prefix}_direct_action_requested=${entry.directActionRequested}")
                appendLine("${prefix}_reason=${entry.reason.toLifecycleIntentEnvValue()}")
            }
            appendLine("lifecycle_intent_surface_boundary=observe_only_intent_contract_no_direct_start_no_queue_no_reclaim_no_pool_resize_no_lane_control_no_enforcement")
        }
    }
}

object RuntimeLifecycleIntentSurfaceDryRun {
    fun evaluate(
        workspacePath: String?,
        now: Long = System.currentTimeMillis()
    ): RuntimeLifecycleIntentSurfaceDryRunSnapshot {
        val workspaceDir = workspacePath?.takeIf { it.isNotBlank() }?.let(::File)
            ?: return RuntimeLifecycleIntentSurfaceDryRunSnapshot(
                generatedAtMs = now,
                state = RuntimeLifecycleIntentSurfaceState.WORKSPACE_MISSING,
                recommendation = RuntimeLifecycleIntentSurfaceRecommendation.WAIT_FOR_WORKSPACE,
                loadStatus = "workspace_missing"
            )
        val file = WorkspaceBuildSupport.runtimeWorkloadIntentFile(workspaceDir)
        if (!file.exists()) {
            return RuntimeLifecycleIntentSurfaceDryRunSnapshot(
                generatedAtMs = now,
                state = RuntimeLifecycleIntentSurfaceState.NO_INTENT_FILE,
                recommendation = RuntimeLifecycleIntentSurfaceRecommendation.KEEP_NO_INTENT,
                intentPath = file.absolutePath,
                loadStatus = "missing"
            )
        }
        return runCatching {
            val json = JSONObject(file.readText())
            val entries = parseEntries(json)
            val state = if (entries.isEmpty()) {
                RuntimeLifecycleIntentSurfaceState.EMPTY
            } else {
                RuntimeLifecycleIntentSurfaceState.INTENT_LOADED
            }
            RuntimeLifecycleIntentSurfaceDryRunSnapshot(
                generatedAtMs = now,
                state = state,
                recommendation = recommendationFor(state),
                intentPath = file.absolutePath,
                loadStatus = "loaded",
                declaredIntentCount = entries.size,
                acceptedAdvisoryCount = entries.count { it.advisoryAccepted },
                ignoredDirectActionCount = entries.count { it.directActionRequested },
                invalidIntentCount = entries.count { !it.advisoryAccepted },
                workloadClassHintCount = entries.count { it.workloadClass != null },
                laneHintCount = entries.count { it.lane != null },
                retentionHintCount = entries.count { it.retention != null },
                entries = entries
            )
        }.getOrElse { error ->
            RuntimeLifecycleIntentSurfaceDryRunSnapshot(
                generatedAtMs = now,
                state = RuntimeLifecycleIntentSurfaceState.INTENT_ERROR_IGNORED,
                recommendation = RuntimeLifecycleIntentSurfaceRecommendation.REPAIR_INTENT_JSON,
                intentPath = file.absolutePath,
                loadStatus = "error_ignored",
                loadError = error.message
            )
        }
    }

    private fun parseEntries(json: JSONObject): List<RuntimeLifecycleIntentEntry> {
        val array = json.optJSONArray("intents") ?: return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val action = item.optString("action", item.optString("intent", "DECLARE"))
                    .ifBlank { "DECLARE" }
                val workloadClass = RuntimeWorkloadClass.entries.firstOrNull {
                    it.name == item.optString("workloadClass", item.optString("class"))
                }
                val lane = RuntimeLaneKind.entries.firstOrNull {
                    it.name == item.optString("lane")
                }
                val retention = RuntimeWorkloadRetention.entries.firstOrNull {
                    it.name == item.optString("retention", item.optString("defaultRetention"))
                }
                val directActionRequested = action.requestsDirectExecution()
                val advisoryAccepted = !directActionRequested &&
                    (workloadClass != null || lane != null || retention != null)
                add(
                    RuntimeLifecycleIntentEntry(
                        intentId = item.optString("id", "intent_${index + 1}"),
                        action = action.uppercase(),
                        workloadClass = workloadClass,
                        lane = lane,
                        retention = retention,
                        advisoryAccepted = advisoryAccepted,
                        directActionRequested = directActionRequested,
                        reason = when {
                            directActionRequested ->
                                "direct_execution_request_rejected_advisory_only"
                            advisoryAccepted ->
                                "advisory_hint_visible_android_kf_decides"
                            else ->
                                "no_recognized_class_lane_or_retention_hint"
                        }
                    )
                )
            }
        }
    }

    private fun recommendationFor(
        state: RuntimeLifecycleIntentSurfaceState
    ): RuntimeLifecycleIntentSurfaceRecommendation {
        return when (state) {
            RuntimeLifecycleIntentSurfaceState.WORKSPACE_MISSING ->
                RuntimeLifecycleIntentSurfaceRecommendation.WAIT_FOR_WORKSPACE
            RuntimeLifecycleIntentSurfaceState.NO_INTENT_FILE,
            RuntimeLifecycleIntentSurfaceState.EMPTY ->
                RuntimeLifecycleIntentSurfaceRecommendation.KEEP_NO_INTENT
            RuntimeLifecycleIntentSurfaceState.INTENT_LOADED ->
                RuntimeLifecycleIntentSurfaceRecommendation.REVIEW_ADVISORY_INTENT
            RuntimeLifecycleIntentSurfaceState.INTENT_ERROR_IGNORED ->
                RuntimeLifecycleIntentSurfaceRecommendation.REPAIR_INTENT_JSON
        }
    }
}

private fun String.requestsDirectExecution(): Boolean {
    val normalized = uppercase()
    return listOf(
        "START",
        "QUEUE",
        "RECLAIM",
        "CLEANUP",
        "FREEZE",
        "KILL",
        "TERMINATE",
        "RESTART",
        "SPAWN",
        "POOL",
        "LANE_CONTROL",
        "ENFORCE"
    ).any { it in normalized }
}

private fun String?.toLifecycleIntentEnvValue(): String {
    if (this.isNullOrBlank()) return "none"
    return trim()
        .replace(Regex("[\\r\\n\\t ]+"), "_")
        .replace(Regex("[^A-Za-z0-9._:@/+,-]"), "_")
        .take(240)
}
