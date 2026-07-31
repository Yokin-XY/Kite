package com.kite.app.foundation.service

import com.kite.app.foundation.runtime.RuntimeExposureScope
import com.kite.app.foundation.runtime.HostProcessIdentityObservation
import com.kite.app.foundation.runtime.normalizeHostBootId
import com.kite.app.foundation.runtime.RuntimeOwnerIdentity
import com.kite.app.foundation.runtime.RuntimeProcessUnitObservationState
import com.kite.app.foundation.workspace.WorkSurfaceRuntimeBridge
import org.json.JSONArray
import org.json.JSONObject

enum class BackgroundRuntimeKind(val label: String) {
    CONTAINER_SUPERVISOR("容器骨架"),
    OPENCLAW_GATEWAY("OpenClaw 网关"),
    FEISHU_GATEWAY("飞书网关"),
    ADB_WORKER("ADB Worker"),
    ACCESSIBILITY_WORKER("无障碍 Worker"),
    PROOT_CAPACITY_WORKER("PRoot 容量工作器"),
    CUSTOM("自定义后台项")
}

enum class BackgroundRuntimeMode(val label: String) {
    PROCESS("宿主持有进程"),
    SERVICE("服务命令")
}

enum class BackgroundRuntimeStatus(val label: String) {
    REGISTERED("已登记"),
    STARTING("启动中"),
    RUNNING("运行中"),
    STOPPED("已停止"),
    ERROR("异常")
}

enum class BackgroundRuntimeCapability(val label: String) {
    MDNS("mDNS 服务发现")
}

enum class BackgroundRuntimeHealthStatus(val label: String) {
    INACTIVE("未运行"),
    BLOCKED("受阻"),
    UNKNOWN("未探测"),
    HEALTHY("健康"),
    UNHEALTHY("异常")
}

enum class BackgroundRuntimeRestartPolicy(val label: String) {
    NEVER("不自动重启"),
    ON_FAILURE("失败后自动重启"),
    ALWAYS_CORE("核心服务自动恢复")
}

enum class RuntimeRetentionClass(
    val label: String,
    val linuxLikeLabel: String,
    val resident: Boolean,
    val reclaimPriority: Int
) {
    UNKNOWN("Unknown", "unknown-root", false, 500),
    CRITICAL_CORE("Critical core", "critical-service", true, 0),
    RESIDENT("Resident", "resident-service", true, 100),
    INTERACTIVE("Interactive", "interactive-session", true, 300),
    BATCH("Batch", "batch-job", false, 700),
    EPHEMERAL("Ephemeral", "ephemeral-job", false, 900)
}

data class BackgroundRuntimeRecord(
    val id: String,
    val spaceId: String,
    val kind: BackgroundRuntimeKind,
    val mode: BackgroundRuntimeMode,
    val title: String,
    val workingDirectory: String,
    val startCommand: String,
    val startExecutable: String? = null,
    val startArguments: List<String> = emptyList(),
    val environment: Map<String, String> = emptyMap(),
    val runtimeGuarantees: Set<String> = emptySet(),
    val runtimeGuaranteeEvidence: Map<String, String> = emptyMap(),
    /** 环境变量名 -> 容器可见私有文件；只持久化路径，运行时才读取值。 */
    val environmentFiles: Map<String, String> = emptyMap(),
    val bindAddress: String? = null,
    val bindPort: Int? = null,
    val exposureScope: RuntimeExposureScope = RuntimeExposureScope.UNKNOWN,
    val requiredCapabilities: List<BackgroundRuntimeCapability> = emptyList(),
    val stopCommand: String? = null,
    val statusCommand: String? = null,
    val healthCommand: String? = null,
    val healthHttpPath: String? = null,
    val healthCheckStartupDelayMs: Long? = null,
    val logPath: String,
    val createdAt: Long,
    val lastStartedAt: Long? = null,
    val lastStoppedAt: Long? = null,
    val status: BackgroundRuntimeStatus = BackgroundRuntimeStatus.REGISTERED,
    val healthStatus: BackgroundRuntimeHealthStatus = BackgroundRuntimeHealthStatus.INACTIVE,
    val pid: Int? = null,
    val lastHealthSummary: String? = null,
    val lastHealthCheckedAt: Long? = null,
    val lastExitCode: Int? = null,
    val lastError: String? = null,
    val lastLaunchLane: String? = null,
    val lastLaunchReason: String? = null,
    val restartPolicy: BackgroundRuntimeRestartPolicy = BackgroundRuntimeRestartPolicy.NEVER,
    val restartFailureCount: Int = 0,
    val lastRestartAt: Long? = null,
    val nextRestartAllowedAt: Long? = null,
    val lastRestartReason: String? = null,
    val lastRecoveredAt: Long? = null,
    val lastRecoverySource: String? = null,
    val lastRecoveryReason: String? = null,
    val lastAdmissionDeferredAt: Long? = null,
    val lastAdmissionSource: String? = null,
    val lastAdmissionReason: String? = null,
    val lastReclaimedAt: Long? = null,
    val lastReclaimSource: String? = null,
    val lastReclaimReason: String? = null,
    val lastStopReconciliationState: RuntimeProcessUnitObservationState? = null,
    val lastStopReconciliationReason: String? = null,
    val lastStopReconciliationAt: Long? = null,
    val lastStopReconciliationAutoRecoverySuppressed: Boolean = false,
    val retentionClass: RuntimeRetentionClass = RuntimeRetentionClass.BATCH,
    val processBootId: String? = null,
    val processStartTicks: Long? = null,
) {
    internal fun processIdentityEnvironment(): Map<String, String> =
        RuntimeOwnerIdentity.backgroundRuntime(id, kind.name).environment()

    fun toJson(): JSONObject {
        return JSONObject()
            .put("id", id)
            .put("spaceId", spaceId)
            .put("kind", kind.name)
            .put("mode", mode.name)
            .put("title", title)
            .put("workingDirectory", workingDirectory)
            .put("startCommand", startCommand)
            .put("startExecutable", startExecutable)
            .put(
                "startArguments",
                JSONArray().apply { startArguments.forEach(::put) }
            )
            .put(
                "environment",
                JSONObject().apply { environment.forEach { (key, value) -> put(key, value) } }
            )
            .put(
                "runtimeGuarantees",
                JSONArray().apply { runtimeGuarantees.sorted().forEach(::put) }
            )
            .put(
                "runtimeGuaranteeEvidence",
                JSONObject().apply {
                    runtimeGuaranteeEvidence.toSortedMap().forEach { (key, value) -> put(key, value) }
                }
            )
            .put(
                "environmentFiles",
                JSONObject().apply { environmentFiles.forEach { (key, value) -> put(key, value) } }
            )
            .put("bindAddress", bindAddress)
            .put("bindPort", bindPort)
            .put("exposureScope", exposureScope.name)
            .put(
                "requiredCapabilities",
                JSONArray().apply {
                    requiredCapabilities.forEach { capability -> put(capability.name) }
                }
            )
            .put("stopCommand", stopCommand)
            .put("statusCommand", statusCommand)
            .put("healthCommand", healthCommand)
            .put("healthHttpPath", healthHttpPath)
            .put("healthCheckStartupDelayMs", healthCheckStartupDelayMs)
            .put("logPath", logPath)
            .put("createdAt", createdAt)
            .put("lastStartedAt", lastStartedAt)
            .put("lastStoppedAt", lastStoppedAt)
            .put("status", status.name)
            .put("healthStatus", healthStatus.name)
            .put("pid", pid)
            .put("processBootId", processBootId)
            .put("processStartTicks", processStartTicks)
            .put("lastHealthSummary", lastHealthSummary)
            .put("lastHealthCheckedAt", lastHealthCheckedAt)
            .put("lastExitCode", lastExitCode)
            .put("lastError", lastError)
            .put("lastLaunchLane", lastLaunchLane)
            .put("lastLaunchReason", lastLaunchReason)
            .put("restartPolicy", restartPolicy.name)
            .put("restartFailureCount", restartFailureCount)
            .put("lastRestartAt", lastRestartAt)
            .put("nextRestartAllowedAt", nextRestartAllowedAt)
            .put("lastRestartReason", lastRestartReason)
            .put("lastRecoveredAt", lastRecoveredAt)
            .put("lastRecoverySource", lastRecoverySource)
            .put("lastRecoveryReason", lastRecoveryReason)
            .put("lastAdmissionDeferredAt", lastAdmissionDeferredAt)
            .put("lastAdmissionSource", lastAdmissionSource)
            .put("lastAdmissionReason", lastAdmissionReason)
            .put("lastReclaimedAt", lastReclaimedAt)
            .put("lastReclaimSource", lastReclaimSource)
            .put("lastReclaimReason", lastReclaimReason)
            .put("lastStopReconciliationState", lastStopReconciliationState?.name)
            .put("lastStopReconciliationReason", lastStopReconciliationReason)
            .put("lastStopReconciliationAt", lastStopReconciliationAt)
            .put(
                "lastStopReconciliationAutoRecoverySuppressed",
                lastStopReconciliationAutoRecoverySuppressed
            )
            .put("retentionClass", retentionClass.name)
    }

    companion object {
        fun fromJson(json: JSONObject): BackgroundRuntimeRecord {
            return BackgroundRuntimeRecord(
                id = json.getString("id"),
                spaceId = json.getString("spaceId"),
                kind = BackgroundRuntimeKind.valueOf(
                    json.optString("kind", BackgroundRuntimeKind.CUSTOM.name)
                ),
                mode = BackgroundRuntimeMode.valueOf(
                    json.optString("mode", BackgroundRuntimeMode.SERVICE.name)
                ),
                title = json.optString("title", json.getString("id")),
                workingDirectory = json.optString(
                    "workingDirectory",
                    WorkSurfaceRuntimeBridge.defaults.workspaceDir
                ),
                startCommand = json.optString("startCommand", ""),
                startExecutable = json.optString("startExecutable").takeIf {
                    !json.isNull("startExecutable") && it.isNotBlank()
                },
                startArguments = json.optJSONArray("startArguments")
                    ?.let { array ->
                        buildList {
                            for (index in 0 until array.length()) add(array.optString(index))
                        }
                    }
                    ?: emptyList(),
                environment = json.optJSONObject("environment")
                    ?.let { environmentJson ->
                        buildMap {
                            environmentJson.keys().forEach { key ->
                                put(key, environmentJson.optString(key))
                            }
                        }
                    }
                    ?: emptyMap(),
                runtimeGuarantees = json.optJSONArray("runtimeGuarantees")
                    ?.let { array ->
                        buildSet {
                            for (index in 0 until array.length()) {
                                array.optString(index).trim().takeIf(String::isNotBlank)?.let(::add)
                            }
                        }
                    }
                    ?: emptySet(),
                runtimeGuaranteeEvidence = json.optJSONObject("runtimeGuaranteeEvidence")
                    ?.let { evidenceJson ->
                        buildMap {
                            evidenceJson.keys().forEach { key -> put(key, evidenceJson.optString(key)) }
                        }
                    }
                    ?: emptyMap(),
                environmentFiles = json.optJSONObject("environmentFiles")
                    ?.let { environmentFilesJson ->
                        buildMap {
                            environmentFilesJson.keys().forEach { key ->
                                put(key, environmentFilesJson.optString(key))
                            }
                        }
                    }
                    ?: emptyMap(),
                bindAddress = json.optString("bindAddress").takeIf { !json.isNull("bindAddress") },
                bindPort = json.optInt("bindPort").takeIf { !json.isNull("bindPort") },
                exposureScope = RuntimeExposureScope.entries.firstOrNull {
                    it.name == json.optString("exposureScope", RuntimeExposureScope.UNKNOWN.name)
                } ?: RuntimeExposureScope.UNKNOWN,
                requiredCapabilities =
                    json.optJSONArray("requiredCapabilities")
                        ?.let { array ->
                            buildList {
                                for (index in 0 until array.length()) {
                                    val raw = array.optString(index).trim()
                                    BackgroundRuntimeCapability.entries
                                        .firstOrNull { it.name == raw }
                                        ?.let(::add)
                                }
                            }
                        }
                        ?: emptyList(),
                stopCommand = json.optString("stopCommand").takeIf {
                    !json.isNull("stopCommand")
                },
                statusCommand = json.optString("statusCommand").takeIf {
                    !json.isNull("statusCommand")
                },
                healthCommand = json.optString("healthCommand").takeIf {
                    !json.isNull("healthCommand")
                },
                healthHttpPath = json.optString("healthHttpPath").takeIf {
                    !json.isNull("healthHttpPath")
                },
                healthCheckStartupDelayMs = json.optLong("healthCheckStartupDelayMs").takeIf {
                    !json.isNull("healthCheckStartupDelayMs")
                },
                logPath = json.optString("logPath", ""),
                createdAt = json.getLong("createdAt"),
                lastStartedAt = json.optLong("lastStartedAt").takeIf {
                    !json.isNull("lastStartedAt")
                },
                lastStoppedAt = json.optLong("lastStoppedAt").takeIf {
                    !json.isNull("lastStoppedAt")
                },
                status = BackgroundRuntimeStatus.valueOf(
                    json.optString("status", BackgroundRuntimeStatus.REGISTERED.name)
                ),
                healthStatus = BackgroundRuntimeHealthStatus.valueOf(
                    json.optString("healthStatus", BackgroundRuntimeHealthStatus.INACTIVE.name)
                ),
                pid = json.optInt("pid").takeIf { !json.isNull("pid") },
                lastHealthSummary = json.optString("lastHealthSummary").takeIf {
                    !json.isNull("lastHealthSummary")
                },
                lastHealthCheckedAt = json.optLong("lastHealthCheckedAt").takeIf {
                    !json.isNull("lastHealthCheckedAt")
                },
                lastExitCode = json.optInt("lastExitCode").takeIf {
                    !json.isNull("lastExitCode")
                },
                lastError = json.optString("lastError").takeIf { !json.isNull("lastError") },
                lastLaunchLane = json.optString("lastLaunchLane").takeIf {
                    !json.isNull("lastLaunchLane") && it.isNotBlank()
                },
                lastLaunchReason = json.optString("lastLaunchReason").takeIf {
                    !json.isNull("lastLaunchReason") && it.isNotBlank()
                },
                restartPolicy = BackgroundRuntimeRestartPolicy.entries.firstOrNull {
                    it.name == json.optString("restartPolicy", BackgroundRuntimeRestartPolicy.NEVER.name)
                } ?: BackgroundRuntimeRestartPolicy.NEVER,
                restartFailureCount = json.optInt("restartFailureCount", 0).coerceAtLeast(0),
                lastRestartAt = json.optLong("lastRestartAt").takeIf {
                    !json.isNull("lastRestartAt")
                },
                nextRestartAllowedAt = json.optLong("nextRestartAllowedAt").takeIf {
                    !json.isNull("nextRestartAllowedAt")
                },
                lastRestartReason = json.optString("lastRestartReason").takeIf {
                    !json.isNull("lastRestartReason")
                },
                lastRecoveredAt = json.optLong("lastRecoveredAt").takeIf {
                    !json.isNull("lastRecoveredAt")
                },
                lastRecoverySource = json.optString("lastRecoverySource").takeIf {
                    !json.isNull("lastRecoverySource")
                },
                lastRecoveryReason = json.optString("lastRecoveryReason").takeIf {
                    !json.isNull("lastRecoveryReason")
                },
                lastAdmissionDeferredAt = json.optLong("lastAdmissionDeferredAt").takeIf {
                    !json.isNull("lastAdmissionDeferredAt")
                },
                lastAdmissionSource = json.optString("lastAdmissionSource").takeIf {
                    !json.isNull("lastAdmissionSource")
                },
                lastAdmissionReason = json.optString("lastAdmissionReason").takeIf {
                    !json.isNull("lastAdmissionReason")
                },
                lastReclaimedAt = json.optLong("lastReclaimedAt").takeIf {
                    !json.isNull("lastReclaimedAt")
                },
                lastReclaimSource = json.optString("lastReclaimSource").takeIf {
                    !json.isNull("lastReclaimSource")
                },
                lastReclaimReason = json.optString("lastReclaimReason").takeIf {
                    !json.isNull("lastReclaimReason")
                },
                lastStopReconciliationState = RuntimeProcessUnitObservationState.entries.firstOrNull {
                    it.name == json.optString("lastStopReconciliationState", "")
                },
                lastStopReconciliationReason = json.optString("lastStopReconciliationReason").takeIf {
                    !json.isNull("lastStopReconciliationReason")
                },
                lastStopReconciliationAt = json.optLong("lastStopReconciliationAt").takeIf {
                    !json.isNull("lastStopReconciliationAt")
                },
                lastStopReconciliationAutoRecoverySuppressed =
                    json.optBoolean("lastStopReconciliationAutoRecoverySuppressed", false),
                retentionClass = RuntimeRetentionClass.entries.firstOrNull {
                    it.name == json.optString("retentionClass", RuntimeRetentionClass.BATCH.name)
                } ?: RuntimeRetentionClass.BATCH,
                processBootId = json.optString("processBootId")
                    .takeIf { !json.isNull("processBootId") }
                    ?.let(::normalizeHostBootId),
                processStartTicks = json.optLong("processStartTicks").takeIf {
                    !json.isNull("processStartTicks") && it > 0L
                },
            )
        }
    }
}

internal fun BackgroundRuntimeRecord.withProcessPid(nextPid: Int?): BackgroundRuntimeRecord {
    val normalizedPid = nextPid?.takeIf { it > 0 }
    val preserveIdentity = normalizedPid != null && normalizedPid == pid
    return copy(
        pid = normalizedPid,
        processBootId = processBootId.takeIf { preserveIdentity },
        processStartTicks = processStartTicks.takeIf { preserveIdentity },
    )
}

internal fun BackgroundRuntimeRecord.withObservedProcessIdentity(
    identity: HostProcessIdentityObservation,
): BackgroundRuntimeRecord? {
    if (!status.isActiveStatus() || pid != identity.hostPid) return null
    return copy(
        processBootId = identity.bootId,
        processStartTicks = identity.processStartTicks,
    )
}

internal fun BackgroundRuntimeRecord.persistedProcessIdentityOrNull(): HostProcessIdentityObservation? {
    val currentPid = pid?.takeIf { it > 0 } ?: return null
    val currentBootId = normalizeHostBootId(processBootId) ?: return null
    val currentStartTicks = processStartTicks?.takeIf { it > 0L } ?: return null
    return HostProcessIdentityObservation(
        bootId = currentBootId,
        hostPid = currentPid,
        processStartTicks = currentStartTicks,
    )
}
