package com.kite.app.foundation.runtime

import com.kite.app.foundation.contracts.NetworkMode

import org.json.JSONArray
import org.json.JSONObject

enum class ProotLaunchPlanKind {
    CONTAINER,
    BOOTSTRAP
}

enum class ProotLaunchLane {
    INTERACTIVE,
    EXEC,
    BOOTSTRAP,
    UNKNOWN
}

data class ProotBindMount(
    val sourcePath: String,
    val targetPath: String,
    val role: String,
    val writable: Boolean = true
) {
    fun toArgv(): List<String> {
        val spec = if (sourcePath == targetPath) {
            sourcePath
        } else {
            "$sourcePath:$targetPath"
        }
        return listOf("-b", spec)
    }

    fun toJson(): JSONObject {
        return JSONObject()
            .put("sourcePath", sourcePath)
            .put("targetPath", targetPath)
            .put("role", role)
            .put("writable", writable)
    }
}

data class ProotLaunchPlan(
    val version: Int = 1,
    val kind: ProotLaunchPlanKind,
    val authority: String = AUTHORITY_ANDROID_CONTROL_PLANE,
    val executablePath: String,
    val rootfsPath: String,
    val workingDirectory: String,
    val flags: List<String>,
    val bindMounts: List<ProotBindMount>,
    val lane: ProotLaunchLane,
    val purpose: String,
    val networkMode: NetworkMode,
    val networkSemantics: RuntimeNetworkSemantics,
    val includeNetworkModeFlag: Boolean,
    val tmpDirPath: String,
    val loaderMode: String,
    val loaderPath: String,
    val loader32Path: String,
    val prootRuntime: JSONObject = JSONObject(),
    val telemetryMode: String = TELEMETRY_DEBUG_JSONL_LIFECYCLE_V0,
    val telemetryFuture: String = TELEMETRY_OUTLET_FUTURE,
    val createdAtUnixMs: Long = System.currentTimeMillis()
) {
    fun baseArgv(): MutableList<String> {
        return buildList {
            add(executablePath)
            addAll(flags)
            add("-r")
            add(rootfsPath)
            add("-w")
            add(workingDirectory)
            bindMounts.forEach { mount -> addAll(mount.toArgv()) }
            if (includeNetworkModeFlag) {
                networkMode.prootFlag?.let { add(it) }
            }
        }.toMutableList()
    }

    fun toContractJson(): JSONObject {
        return JSONObject()
            .put("version", version)
            .put("kind", kind.name)
            .put("authority", authority)
            .put("owner", "android_apk")
            .put("boundary", "ubuntu_declares_android_launches")
            .put("contractMode", "android_generated_fact")
            .put("ubuntuExecutable", false)
            .put("createdAtUnixMs", createdAtUnixMs)
            .put("lane", lane.name)
            .put("purpose", purpose)
            .put("executablePath", executablePath)
            .put("rootfsPath", rootfsPath)
            .put("workingDirectory", workingDirectory)
            .put("flags", flags.toJsonArray())
            .put("bindMounts", JSONArray().also { array ->
                bindMounts.forEach { mount -> array.put(mount.toJson()) }
            })
            .put("network", JSONObject()
                .put("mode", networkMode.name)
                .put("prootFlag", networkMode.prootFlag ?: JSONObject.NULL)
                .put("includeNetworkModeFlag", includeNetworkModeFlag)
                .put("semantics", networkSemantics.toJson())
            )
            .put("runtime", JSONObject()
                .put("tmpDirPath", tmpDirPath)
                .put("loaderMode", loaderMode)
                .put("loaderPath", loaderPath)
                .put("loader32Path", loader32Path)
                .put("proot", prootRuntime)
            )
            .put("telemetry", JSONObject()
                .put("mode", telemetryMode)
                .put("requestedMode", TELEMETRY_DEBUG_JSONL_LIFECYCLE_V0)
                .put("future", telemetryFuture)
                .put(
                    "supportStatus",
                    if (telemetryMode == TELEMETRY_DEBUG_JSONL_LIFECYCLE_V0) {
                        "runtime_descriptor_supports_requested_telemetry"
                    } else {
                        "runtime_descriptor_does_not_support_requested_telemetry"
                    }
                )
                .put(
                    "note",
                    if (telemetryMode == TELEMETRY_DEBUG_JSONL_LIFECYCLE_V0) {
                        "Lifecycle-only debug jsonl telemetry is enabled when KF_PROOT_TELEMETRY_PATH is present."
                    } else {
                        "The current PRoot runtime descriptor does not advertise lifecycle debug jsonl telemetry support."
                    }
                )
            )
            .put("ubuntuIntent", JSONObject()
                .put("requestPath", "/workspace/.kf/proot-launch-request.json")
                .put("mode", "advisory_only")
                .put("appliedByAndroid", false)
                .put("note", "Ubuntu may write launch intent here, but Android does not treat it as launch truth in this phase.")
            )
    }

    companion object {
        const val AUTHORITY_ANDROID_CONTROL_PLANE = "android_control_plane"
        const val TELEMETRY_NONE_CURRENT = "none_current"
        const val TELEMETRY_DEBUG_JSONL_LIFECYCLE_V0 = "debug_jsonl_lifecycle_v0"
        const val TELEMETRY_OUTLET_FUTURE = "proot_telemetry_outlet_v0"
    }
}

private fun RuntimeNetworkSemantics.toJson(): JSONObject {
    return JSONObject()
        .put("topology", topology.name)
        .put("loopback", loopback.name)
        .put("portPolicy", portPolicy.name)
        .put("controlBoundary", controlBoundary.name)
        .put("summary", compactSummary())
        .put("notes", notes.toJsonArray())
}

private fun List<String>.toJsonArray(): JSONArray {
    return JSONArray().also { array ->
        forEach { value -> array.put(value) }
    }
}
