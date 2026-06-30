package com.kite.app.foundation.runtime

import android.content.Context
import com.kite.app.foundation.logging.Logger
import com.kite.app.foundation.workspace.WorkSurfaceRuntimeBridge
import com.kite.app.foundation.workspace.WorkspaceBuildSupport
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

/**
 * Android-side contract for the two runtime management systems.
 *
 * Ubuntu may edit this policy to declare desired behavior. The APK remains the
 * execution owner: PRoot provides facts, this policy describes intent, and the
 * Android control plane decides admission, decay, reclaim, restart, or quarantine.
 */
enum class RuntimeWorkloadClass {
    SYSTEM_CORE,
    PINNED_SERVICE,
    INTERACTIVE,
    BUILD,
    PROBE,
    EPHEMERAL,
    STRAY,
    UNKNOWN
}

enum class RuntimeWorkloadOwner {
    KF,
    USER,
    AI,
    SERVICE,
    LEGACY,
    UNKNOWN
}

enum class RuntimeWorkloadRetention {
    KEEP,
    LEASE,
    CLEANUP_CANDIDATE,
    QUARANTINE
}

enum class RuntimeLaneKind {
    INTERACTIVE,
    SERVICE,
    BUILD,
    PROBE
}

enum class RuntimeBudgetState {
    HEALTHY,
    NEAR_BUDGET,
    SOFT_PRESSURE,
    HARD_PRESSURE,
    THREATENING_KF,
    REPEAT_OFFENDER,
    QUARANTINED
}

enum class RuntimeBudgetAction {
    OBSERVE,
    WARN,
    THROTTLE,
    REQUEST_CLEANUP,
    FREEZE_SHORT,
    TERMINATE_CHILDREN,
    TERMINATE_WORKLOAD,
    RESTART_MAIN,
    QUARANTINE,
    RECOVERY_CUTOFF
}

data class RuntimeLanePolicy(
    val lane: RuntimeLaneKind,
    val maxConcurrency: Int,
    val backgroundMaxConcurrency: Int,
    val serial: Boolean,
    val allowBurst: Boolean,
    val priority: Int
) {
    fun toJson(): JSONObject {
        return JSONObject()
            .put("lane", lane.name)
            .put("maxConcurrency", maxConcurrency)
            .put("backgroundMaxConcurrency", backgroundMaxConcurrency)
            .put("serial", serial)
            .put("allowBurst", allowBurst)
            .put("priority", priority)
    }

    companion object {
        fun fromJson(json: JSONObject): RuntimeLanePolicy {
            return RuntimeLanePolicy(
                lane = RuntimeLaneKind.entries.firstOrNull {
                    it.name == json.optString("lane")
                } ?: RuntimeLaneKind.PROBE,
                maxConcurrency = json.optInt("maxConcurrency", 1).coerceAtLeast(0),
                backgroundMaxConcurrency = json.optInt("backgroundMaxConcurrency", 0).coerceAtLeast(0),
                serial = json.optBoolean("serial", true),
                allowBurst = json.optBoolean("allowBurst", false),
                priority = json.optInt("priority", 100).coerceAtLeast(0)
            )
        }
    }
}

data class RuntimeWorkloadEnvelope(
    val workloadClass: RuntimeWorkloadClass,
    val defaultRetention: RuntimeWorkloadRetention,
    val backgroundAllowed: Boolean,
    val maxChildren: Int,
    val maxRuntimeMs: Long,
    val maxIdleMs: Long,
    val restartAllowed: Boolean,
    val autoQuarantineAllowed: Boolean
) {
    fun toJson(): JSONObject {
        return JSONObject()
            .put("workloadClass", workloadClass.name)
            .put("defaultRetention", defaultRetention.name)
            .put("backgroundAllowed", backgroundAllowed)
            .put("maxChildren", maxChildren)
            .put("maxRuntimeMs", maxRuntimeMs)
            .put("maxIdleMs", maxIdleMs)
            .put("restartAllowed", restartAllowed)
            .put("autoQuarantineAllowed", autoQuarantineAllowed)
    }

    companion object {
        fun fromJson(json: JSONObject): RuntimeWorkloadEnvelope {
            return RuntimeWorkloadEnvelope(
                workloadClass = RuntimeWorkloadClass.entries.firstOrNull {
                    it.name == json.optString("workloadClass")
                } ?: RuntimeWorkloadClass.UNKNOWN,
                defaultRetention = RuntimeWorkloadRetention.entries.firstOrNull {
                    it.name == json.optString("defaultRetention")
                } ?: RuntimeWorkloadRetention.CLEANUP_CANDIDATE,
                backgroundAllowed = json.optBoolean("backgroundAllowed", false),
                maxChildren = json.optInt("maxChildren", 0).coerceAtLeast(0),
                maxRuntimeMs = json.optLong("maxRuntimeMs", 0L).coerceAtLeast(0L),
                maxIdleMs = json.optLong("maxIdleMs", 0L).coerceAtLeast(0L),
                restartAllowed = json.optBoolean("restartAllowed", false),
                autoQuarantineAllowed = json.optBoolean("autoQuarantineAllowed", true)
            )
        }
    }
}

data class RuntimeBackgroundDecayPolicy(
    val graceMs: Long,
    val transientCleanupMs: Long,
    val serviceOnlyMs: Long,
    val lowActivityMs: Long,
    val pressureAccelerates: Boolean
) {
    fun toJson(): JSONObject {
        return JSONObject()
            .put("graceMs", graceMs)
            .put("transientCleanupMs", transientCleanupMs)
            .put("serviceOnlyMs", serviceOnlyMs)
            .put("lowActivityMs", lowActivityMs)
            .put("pressureAccelerates", pressureAccelerates)
    }

    companion object {
        fun fromJson(json: JSONObject?): RuntimeBackgroundDecayPolicy {
            if (json == null) return default()
            val defaults = default()
            val graceMs = json.positiveLongOrDefault("graceMs", defaults.graceMs)
            val transientCleanupMs = json
                .positiveLongOrDefault("transientCleanupMs", defaults.transientCleanupMs)
                .coerceAtLeast(graceMs)
            return RuntimeBackgroundDecayPolicy(
                graceMs = graceMs,
                transientCleanupMs = transientCleanupMs,
                serviceOnlyMs = json.positiveLongOrDefault("serviceOnlyMs", defaults.serviceOnlyMs),
                lowActivityMs = json.positiveLongOrDefault("lowActivityMs", defaults.lowActivityMs),
                pressureAccelerates = json.optBoolean("pressureAccelerates", true)
            )
        }

        fun default(): RuntimeBackgroundDecayPolicy {
            return RuntimeBackgroundDecayPolicy(
                graceMs = 30_000L,
                transientCleanupMs = 3 * 60_000L,
                serviceOnlyMs = 10 * 60_000L,
                lowActivityMs = 30 * 60_000L,
                pressureAccelerates = true
            )
        }
    }
}

data class RuntimeBudgetStatePolicy(
    val state: RuntimeBudgetState,
    val actions: List<RuntimeBudgetAction>,
    val note: String
) {
    fun toJson(): JSONObject {
        return JSONObject()
            .put("state", state.name)
            .put(
                "actions",
                JSONArray().apply {
                    actions.forEach { action -> put(action.name) }
                }
            )
            .put("note", note)
    }

    companion object {
        fun fromJson(json: JSONObject): RuntimeBudgetStatePolicy {
            val actions = json.optJSONArray("actions")
                ?.let { array ->
                    buildList {
                        for (index in 0 until array.length()) {
                            val raw = array.optString(index)
                            RuntimeBudgetAction.entries
                                .firstOrNull { it.name == raw }
                                ?.let(::add)
                        }
                    }
                }
                ?: emptyList()
            return RuntimeBudgetStatePolicy(
                state = RuntimeBudgetState.entries.firstOrNull {
                    it.name == json.optString("state")
                } ?: RuntimeBudgetState.HEALTHY,
                actions = actions,
                note = json.optString("note", "")
            )
        }
    }
}

data class RuntimeRepeatOffenderPolicy(
    val quickRelapseMs: Long,
    val restartWindowMs: Long,
    val maxRestartsInWindow: Int,
    val violationWindowMs: Long,
    val maxViolationsInWindow: Int
) {
    fun toJson(): JSONObject {
        return JSONObject()
            .put("quickRelapseMs", quickRelapseMs)
            .put("restartWindowMs", restartWindowMs)
            .put("maxRestartsInWindow", maxRestartsInWindow)
            .put("violationWindowMs", violationWindowMs)
            .put("maxViolationsInWindow", maxViolationsInWindow)
    }

    companion object {
        fun fromJson(json: JSONObject?): RuntimeRepeatOffenderPolicy {
            if (json == null) return default()
            return RuntimeRepeatOffenderPolicy(
                quickRelapseMs = json.optLong("quickRelapseMs", 60_000L).coerceAtLeast(0L),
                restartWindowMs = json.optLong("restartWindowMs", 10 * 60_000L).coerceAtLeast(0L),
                maxRestartsInWindow = json.optInt("maxRestartsInWindow", 2).coerceAtLeast(0),
                violationWindowMs = json.optLong("violationWindowMs", 30 * 60_000L).coerceAtLeast(0L),
                maxViolationsInWindow = json.optInt("maxViolationsInWindow", 3).coerceAtLeast(0)
            )
        }

        fun default(): RuntimeRepeatOffenderPolicy {
            return RuntimeRepeatOffenderPolicy(
                quickRelapseMs = 60_000L,
                restartWindowMs = 10 * 60_000L,
                maxRestartsInWindow = 2,
                violationWindowMs = 30 * 60_000L,
                maxViolationsInWindow = 3
            )
        }
    }
}

data class RuntimeLifecycleLeasePolicy(
    val foregroundLeasePoolBudgetPercent: Int,
    val hiddenLeasePoolBudgetPercent: Int,
    val backgroundPressureLeasePoolBudgetPercent: Int,
    val lowMemoryLeasePoolBudgetPercent: Int,
    val pressureLeasePoolBudgetPercent: Int,
    val settlementTickMs: Long,
    val memorySampleTickMs: Long,
    val memoryPressureSampleAvailablePercent: Int,
    val memoryPressureSampleCooldownMs: Long,
    val memoryPressureImmediateSettlement: Boolean,
    val activeLeaseTtlMs: Long,
    val weakActivityLeaseTtlMs: Long,
    val coolingLeaseTtlMs: Long,
    val maxTotalLeaseMs: Long,
    val cpuStrongDeltaTicks: Long,
    val cpuWeakDeltaTicks: Long,
    val ioStrongDeltaBytes: Long,
    val ioWeakDeltaBytes: Long,
    val rssMinDeltaKb: Long,
    val rssDeltaPercent: Int,
    val initialLeaseMs: Long = 5 * 60_000L,
    val memoryMaxExtensionMs: Long = 90_000L,
    val rssStrongDeltaKb: Long = 64L * 1024L,
    val rssStrongDeltaPercent: Int = 20,
    val processTreeBonusMs: Long = 60_000L,
    val foregroundBonusMs: Long = 60_000L,
    val cpuBonusMs: Long = 30_000L,
    val networkLikelyBonusMs: Long = 60_000L,
    val maxExtensionPerSettlementMs: Long = 3 * 60_000L,
    val expiredGraceMs: Long = 2 * 60_000L,
    val maxTotalUnlockAverageScorePercent: Int = 25,
    val maxTotalUnlockLatestScorePercent: Int = 55,
    val maxTotalUnlockInitialMs: Long = 5 * 60_000L,
    val trustedActivityExtensionMs: Long = 90_000L,
    val strongActivityExtensionMs: Long = 120_000L,
    val multiEvidenceActivityExtensionMs: Long = 150_000L,
    val highCostUpgradeMs: Long = 30_000L,
    val highCostRssKb: Long = 512L * 1024L,
    val highCostLeasePoolBudgetPercent: Int = 15,
    val cpuTicksPerSecond: Long = 100L,
    val cpuWeakPercent: Int = 1,
    val cpuTrustedPercent: Int = 3,
    val cpuStrongPercent: Int = 10,
    val maxTotalUnlockActiveSamplePercent: Int = 20
) {
    fun toJson(): JSONObject {
        return JSONObject()
            .put("foregroundLeasePoolBudgetPercent", foregroundLeasePoolBudgetPercent)
            .put("hiddenLeasePoolBudgetPercent", hiddenLeasePoolBudgetPercent)
            .put("backgroundPressureLeasePoolBudgetPercent", backgroundPressureLeasePoolBudgetPercent)
            .put("lowMemoryLeasePoolBudgetPercent", lowMemoryLeasePoolBudgetPercent)
            .put("pressureLeasePoolBudgetPercent", pressureLeasePoolBudgetPercent)
            .put("settlementTickMs", settlementTickMs)
            .put("memorySampleTickMs", memorySampleTickMs)
            .put("memoryPressureSampleAvailablePercent", memoryPressureSampleAvailablePercent)
            .put("memoryPressureSampleCooldownMs", memoryPressureSampleCooldownMs)
            .put("memoryPressureImmediateSettlement", memoryPressureImmediateSettlement)
            .put("activeLeaseTtlMs", activeLeaseTtlMs)
            .put("weakActivityLeaseTtlMs", weakActivityLeaseTtlMs)
            .put("coolingLeaseTtlMs", coolingLeaseTtlMs)
            .put("maxTotalLeaseMs", maxTotalLeaseMs)
            .put("cpuStrongDeltaTicks", cpuStrongDeltaTicks)
            .put("cpuWeakDeltaTicks", cpuWeakDeltaTicks)
            .put("ioStrongDeltaBytes", ioStrongDeltaBytes)
            .put("ioWeakDeltaBytes", ioWeakDeltaBytes)
            .put("rssMinDeltaKb", rssMinDeltaKb)
            .put("rssDeltaPercent", rssDeltaPercent)
            .put("initialLeaseMs", initialLeaseMs)
            .put("memoryMaxExtensionMs", memoryMaxExtensionMs)
            .put("rssStrongDeltaKb", rssStrongDeltaKb)
            .put("rssStrongDeltaPercent", rssStrongDeltaPercent)
            .put("processTreeBonusMs", processTreeBonusMs)
            .put("foregroundBonusMs", foregroundBonusMs)
            .put("cpuBonusMs", cpuBonusMs)
            .put("networkLikelyBonusMs", networkLikelyBonusMs)
            .put("maxExtensionPerSettlementMs", maxExtensionPerSettlementMs)
            .put("expiredGraceMs", expiredGraceMs)
            .put("maxTotalUnlockAverageScorePercent", maxTotalUnlockAverageScorePercent)
            .put("maxTotalUnlockLatestScorePercent", maxTotalUnlockLatestScorePercent)
            .put("maxTotalUnlockInitialMs", maxTotalUnlockInitialMs)
            .put("trustedActivityExtensionMs", trustedActivityExtensionMs)
            .put("strongActivityExtensionMs", strongActivityExtensionMs)
            .put("multiEvidenceActivityExtensionMs", multiEvidenceActivityExtensionMs)
            .put("highCostUpgradeMs", highCostUpgradeMs)
            .put("highCostRssKb", highCostRssKb)
            .put("highCostLeasePoolBudgetPercent", highCostLeasePoolBudgetPercent)
            .put("cpuTicksPerSecond", cpuTicksPerSecond)
            .put("cpuWeakPercent", cpuWeakPercent)
            .put("cpuTrustedPercent", cpuTrustedPercent)
            .put("cpuStrongPercent", cpuStrongPercent)
            .put("maxTotalUnlockActiveSamplePercent", maxTotalUnlockActiveSamplePercent)
    }

    companion object {
        fun fromJson(json: JSONObject?): RuntimeLifecycleLeasePolicy {
            val defaults = default()
            if (json == null) return defaults
            val activeLeaseTtlMs = json.positiveLongOrDefault("activeLeaseTtlMs", defaults.activeLeaseTtlMs)
            val weakActivityLeaseTtlMs =
                json.positiveLongOrDefault("weakActivityLeaseTtlMs", defaults.weakActivityLeaseTtlMs)
            val coolingLeaseTtlMs = json.positiveLongOrDefault("coolingLeaseTtlMs", defaults.coolingLeaseTtlMs)
            val initialLeaseMs = json.positiveLongOrDefault("initialLeaseMs", defaults.initialLeaseMs)
            val maxTotalFloor = maxOf(activeLeaseTtlMs, weakActivityLeaseTtlMs, coolingLeaseTtlMs, initialLeaseMs)
            val maxTotalLeaseMs = json.positiveLongOrDefault("maxTotalLeaseMs", defaults.maxTotalLeaseMs)
                .let { parsed ->
                    if (parsed < maxTotalFloor) {
                        defaults.maxTotalLeaseMs.coerceAtLeast(maxTotalFloor)
                    } else {
                        parsed
                    }
                }
            val cpuStrongDeltaTicks =
                json.positiveLongOrDefault("cpuStrongDeltaTicks", defaults.cpuStrongDeltaTicks)
            val cpuWeakDeltaTicks =
                json.positiveLongOrDefault("cpuWeakDeltaTicks", defaults.cpuWeakDeltaTicks)
                    .coerceAtMost(cpuStrongDeltaTicks)
            val ioStrongDeltaBytes =
                json.positiveLongOrDefault("ioStrongDeltaBytes", defaults.ioStrongDeltaBytes)
            val ioWeakDeltaBytes =
                json.positiveLongOrDefault("ioWeakDeltaBytes", defaults.ioWeakDeltaBytes)
                    .coerceAtMost(ioStrongDeltaBytes)
            val maxExtensionPerSettlementMs =
                json.positiveLongOrDefault(
                    "maxExtensionPerSettlementMs",
                    defaults.maxExtensionPerSettlementMs
                )
            val rssStrongDeltaKb =
                json.positiveLongOrDefault("rssStrongDeltaKb", defaults.rssStrongDeltaKb)
            val rssStrongDeltaPercent =
                json.optInt("rssStrongDeltaPercent", defaults.rssStrongDeltaPercent)
                    .coerceIn(1, 100)
            val trustedActivityExtensionMs =
                json.positiveLongOrDefault(
                    "trustedActivityExtensionMs",
                    defaults.trustedActivityExtensionMs
                ).coerceAtMost(maxExtensionPerSettlementMs)
            val strongActivityExtensionMs =
                json.positiveLongOrDefault(
                    "strongActivityExtensionMs",
                    defaults.strongActivityExtensionMs
                ).coerceAtMost(maxExtensionPerSettlementMs)
            val multiEvidenceActivityExtensionMs =
                json.positiveLongOrDefault(
                    "multiEvidenceActivityExtensionMs",
                    defaults.multiEvidenceActivityExtensionMs
                ).coerceAtMost(maxExtensionPerSettlementMs)
            val highCostUpgradeMs =
                json.positiveLongOrDefault("highCostUpgradeMs", defaults.highCostUpgradeMs)
                    .coerceAtMost(maxExtensionPerSettlementMs)
            val cpuWeakPercent = json.optInt("cpuWeakPercent", defaults.cpuWeakPercent).coerceIn(1, 100)
            val cpuTrustedPercent = json.optInt("cpuTrustedPercent", defaults.cpuTrustedPercent)
                .coerceIn(cpuWeakPercent, 100)
            val cpuStrongPercent = json.optInt("cpuStrongPercent", defaults.cpuStrongPercent)
                .coerceIn(cpuTrustedPercent, 100)
            return RuntimeLifecycleLeasePolicy(
                foregroundLeasePoolBudgetPercent =
                    json.optInt("foregroundLeasePoolBudgetPercent", defaults.foregroundLeasePoolBudgetPercent)
                        .coerceIn(1, 100),
                hiddenLeasePoolBudgetPercent =
                    json.optInt("hiddenLeasePoolBudgetPercent", defaults.hiddenLeasePoolBudgetPercent)
                        .coerceIn(1, 100),
                backgroundPressureLeasePoolBudgetPercent =
                    json.optInt("backgroundPressureLeasePoolBudgetPercent", defaults.backgroundPressureLeasePoolBudgetPercent)
                        .coerceIn(1, 100),
                lowMemoryLeasePoolBudgetPercent =
                    json.optInt("lowMemoryLeasePoolBudgetPercent", defaults.lowMemoryLeasePoolBudgetPercent)
                        .coerceIn(1, 100),
                pressureLeasePoolBudgetPercent =
                    json.optInt("pressureLeasePoolBudgetPercent", defaults.pressureLeasePoolBudgetPercent)
                        .coerceIn(1, 100),
                settlementTickMs =
                    json.positiveLongOrDefault("settlementTickMs", defaults.settlementTickMs),
                memorySampleTickMs =
                    json.positiveLongOrDefault("memorySampleTickMs", defaults.memorySampleTickMs),
                memoryPressureSampleAvailablePercent =
                    json.optInt(
                        "memoryPressureSampleAvailablePercent",
                        defaults.memoryPressureSampleAvailablePercent
                    ).takeIf { it in 1..99 } ?: defaults.memoryPressureSampleAvailablePercent,
                memoryPressureSampleCooldownMs =
                    json.positiveLongOrDefault(
                        "memoryPressureSampleCooldownMs",
                        defaults.memoryPressureSampleCooldownMs
                    ),
                memoryPressureImmediateSettlement =
                    json.optBoolean(
                        "memoryPressureImmediateSettlement",
                        defaults.memoryPressureImmediateSettlement
                    ),
                activeLeaseTtlMs = activeLeaseTtlMs,
                weakActivityLeaseTtlMs = weakActivityLeaseTtlMs,
                coolingLeaseTtlMs = coolingLeaseTtlMs,
                maxTotalLeaseMs = maxTotalLeaseMs,
                cpuStrongDeltaTicks = cpuStrongDeltaTicks,
                cpuWeakDeltaTicks = cpuWeakDeltaTicks,
                ioStrongDeltaBytes = ioStrongDeltaBytes,
                ioWeakDeltaBytes = ioWeakDeltaBytes,
                rssMinDeltaKb = json.positiveLongOrDefault("rssMinDeltaKb", defaults.rssMinDeltaKb),
                rssDeltaPercent = json.optInt("rssDeltaPercent", defaults.rssDeltaPercent).coerceIn(1, 100),
                initialLeaseMs = initialLeaseMs,
                memoryMaxExtensionMs =
                    json.positiveLongOrDefault("memoryMaxExtensionMs", defaults.memoryMaxExtensionMs)
                        .coerceAtMost(maxExtensionPerSettlementMs),
                rssStrongDeltaKb = rssStrongDeltaKb,
                rssStrongDeltaPercent = rssStrongDeltaPercent,
                processTreeBonusMs =
                    json.positiveLongOrDefault("processTreeBonusMs", defaults.processTreeBonusMs)
                        .coerceAtMost(maxExtensionPerSettlementMs),
                foregroundBonusMs =
                    json.positiveLongOrDefault("foregroundBonusMs", defaults.foregroundBonusMs)
                        .coerceAtMost(maxExtensionPerSettlementMs),
                cpuBonusMs =
                    json.positiveLongOrDefault("cpuBonusMs", defaults.cpuBonusMs)
                        .coerceAtMost(maxExtensionPerSettlementMs),
                networkLikelyBonusMs =
                    json.positiveLongOrDefault("networkLikelyBonusMs", defaults.networkLikelyBonusMs)
                        .coerceAtMost(maxExtensionPerSettlementMs),
                maxExtensionPerSettlementMs = maxExtensionPerSettlementMs,
                expiredGraceMs = json.positiveLongOrDefault("expiredGraceMs", defaults.expiredGraceMs),
                maxTotalUnlockAverageScorePercent =
                    json.optInt(
                        "maxTotalUnlockAverageScorePercent",
                        defaults.maxTotalUnlockAverageScorePercent
                    ).coerceIn(1, 100),
                maxTotalUnlockLatestScorePercent =
                    json.optInt(
                        "maxTotalUnlockLatestScorePercent",
                        defaults.maxTotalUnlockLatestScorePercent
                    ).coerceIn(1, 100),
                maxTotalUnlockInitialMs =
                    json.positiveLongOrDefault("maxTotalUnlockInitialMs", defaults.maxTotalUnlockInitialMs)
                        .coerceAtMost(maxTotalLeaseMs),
                trustedActivityExtensionMs = trustedActivityExtensionMs,
                strongActivityExtensionMs = strongActivityExtensionMs,
                multiEvidenceActivityExtensionMs = multiEvidenceActivityExtensionMs,
                highCostUpgradeMs = highCostUpgradeMs,
                highCostRssKb = json.positiveLongOrDefault("highCostRssKb", defaults.highCostRssKb),
                highCostLeasePoolBudgetPercent =
                    json.optInt(
                        "highCostLeasePoolBudgetPercent",
                        defaults.highCostLeasePoolBudgetPercent
                    ).coerceIn(1, 100),
                cpuTicksPerSecond =
                    json.positiveLongOrDefault("cpuTicksPerSecond", defaults.cpuTicksPerSecond),
                cpuWeakPercent = cpuWeakPercent,
                cpuTrustedPercent = cpuTrustedPercent,
                cpuStrongPercent = cpuStrongPercent,
                maxTotalUnlockActiveSamplePercent =
                    json.optInt(
                        "maxTotalUnlockActiveSamplePercent",
                        defaults.maxTotalUnlockActiveSamplePercent
                    ).coerceIn(1, 100)
            )
        }

        fun default(): RuntimeLifecycleLeasePolicy {
            return RuntimeLifecycleLeasePolicy(
                foregroundLeasePoolBudgetPercent = 30,
                hiddenLeasePoolBudgetPercent = 15,
                backgroundPressureLeasePoolBudgetPercent = 10,
                lowMemoryLeasePoolBudgetPercent = 5,
                pressureLeasePoolBudgetPercent = 8,
                settlementTickMs = 60_000L,
                memorySampleTickMs = 30_000L,
                memoryPressureSampleAvailablePercent = 15,
                memoryPressureSampleCooldownMs = 15_000L,
                memoryPressureImmediateSettlement = true,
                activeLeaseTtlMs = 3 * 60_000L,
                weakActivityLeaseTtlMs = 30_000L,
                coolingLeaseTtlMs = 60_000L,
                maxTotalLeaseMs = 30 * 60_000L,
                cpuStrongDeltaTicks = 50L,
                cpuWeakDeltaTicks = 10L,
                ioStrongDeltaBytes = 1L * 1024L * 1024L,
                ioWeakDeltaBytes = 128L * 1024L,
                rssMinDeltaKb = 8L * 1024L,
                rssDeltaPercent = 3,
                initialLeaseMs = 5 * 60_000L,
                memoryMaxExtensionMs = 90_000L,
                rssStrongDeltaKb = 64L * 1024L,
                rssStrongDeltaPercent = 20,
                processTreeBonusMs = 60_000L,
                foregroundBonusMs = 60_000L,
                cpuBonusMs = 30_000L,
                networkLikelyBonusMs = 60_000L,
                maxExtensionPerSettlementMs = 3 * 60_000L,
                expiredGraceMs = 60_000L,
                maxTotalUnlockAverageScorePercent = 25,
                maxTotalUnlockLatestScorePercent = 55,
                maxTotalUnlockInitialMs = 5 * 60_000L,
                trustedActivityExtensionMs = 90_000L,
                strongActivityExtensionMs = 120_000L,
                multiEvidenceActivityExtensionMs = 150_000L,
                highCostUpgradeMs = 30_000L,
                highCostRssKb = 512L * 1024L,
                highCostLeasePoolBudgetPercent = 15,
                cpuTicksPerSecond = 100L,
                cpuWeakPercent = 1,
                cpuTrustedPercent = 3,
                cpuStrongPercent = 10,
                maxTotalUnlockActiveSamplePercent = 20
            )
        }
    }
}

data class RuntimeWorkloadPolicy(
    val version: Int = 1,
    val lifecycleManagementEnabled: Boolean = false,
    val lifecycleStrategyGroup: String = "balanced_default",
    val authority: String = "android_control_plane",
    val telemetrySource: String = "proot_lifecycle_telemetry_v0+android_proc_snapshot_current",
    val lanes: List<RuntimeLanePolicy> = defaultLanes(),
    val envelopes: List<RuntimeWorkloadEnvelope> = defaultEnvelopes(),
    val backgroundDecay: RuntimeBackgroundDecayPolicy = RuntimeBackgroundDecayPolicy.default(),
    val lifecycleLease: RuntimeLifecycleLeasePolicy = RuntimeLifecycleLeasePolicy.default(),
    val budgetStates: List<RuntimeBudgetStatePolicy> = defaultBudgetStates(),
    val repeatOffender: RuntimeRepeatOffenderPolicy = RuntimeRepeatOffenderPolicy.default(),
    val policyPath: String? = null,
    val loadStatus: String = "default",
    val loadError: String? = null,
    val compatOverlayStatus: String = "none",
    val compatAddedLaneCount: Int = 0,
    val compatAddedEnvelopeCount: Int = 0,
    val compatAddedBudgetStateCount: Int = 0,
    val compatAddedEnvelopeClasses: List<RuntimeWorkloadClass> = emptyList()
) {
    fun toJson(): JSONObject {
        return JSONObject()
            .put("version", version)
            .put("lifecycleManagementEnabled", lifecycleManagementEnabled)
            .put("lifecycleStrategyGroup", lifecycleStrategyGroup)
            .put("authority", authority)
            .put("telemetrySource", telemetrySource)
            .put(
                "lanes",
                JSONArray().apply {
                    lanes.forEach { lane -> put(lane.toJson()) }
                }
            )
            .put(
                "envelopes",
                JSONArray().apply {
                    envelopes.forEach { envelope -> put(envelope.toJson()) }
                }
            )
            .put("backgroundDecay", backgroundDecay.toJson())
            .put("lifecycleLease", lifecycleLease.toJson())
            .put(
                "budgetStates",
                JSONArray().apply {
                    budgetStates.forEach { state -> put(state.toJson()) }
                }
            )
            .put("repeatOffender", repeatOffender.toJson())
    }

    companion object {
        fun default(policyPath: String? = null): RuntimeWorkloadPolicy {
            return RuntimeWorkloadPolicy(policyPath = policyPath)
        }

        fun fromJson(json: JSONObject, policyPath: String?, loadStatus: String = "loaded"): RuntimeWorkloadPolicy {
            val laneMerge = mergeWithSafetyFloor(
                parsed = json.optJSONArray("lanes")
                    ?.toList { RuntimeLanePolicy.fromJson(it) }
                    ?: emptyList(),
                defaults = defaultLanes()
            ) { it.lane }
            val envelopeMerge = mergeWithSafetyFloor(
                parsed = json.optJSONArray("envelopes")
                    ?.toList { RuntimeWorkloadEnvelope.fromJson(it) }
                    ?: emptyList(),
                defaults = defaultEnvelopes()
            ) { it.workloadClass }
            val budgetStateMerge = mergeWithSafetyFloor(
                parsed = json.optJSONArray("budgetStates")
                    ?.toList { RuntimeBudgetStatePolicy.fromJson(it) }
                    ?: emptyList(),
                defaults = defaultBudgetStates()
            ) { it.state }
            val overlayCount = laneMerge.addedDefaults.size +
                envelopeMerge.addedDefaults.size +
                budgetStateMerge.addedDefaults.size
            return RuntimeWorkloadPolicy(
                version = json.optInt("version", 1).coerceAtLeast(1),
                lifecycleManagementEnabled = json.optBoolean("lifecycleManagementEnabled", false),
                lifecycleStrategyGroup = json.optString("lifecycleStrategyGroup", "balanced_default")
                    .trim()
                    .takeIf { it.isNotBlank() }
                    ?: "balanced_default",
                authority = json.optString("authority", "android_control_plane"),
                telemetrySource = json.optString(
                    "telemetrySource",
                    "proot_lifecycle_telemetry_v0+android_proc_snapshot_current"
                ),
                lanes = laneMerge.items,
                envelopes = envelopeMerge.items,
                backgroundDecay = RuntimeBackgroundDecayPolicy.fromJson(json.optJSONObject("backgroundDecay")),
                lifecycleLease = RuntimeLifecycleLeasePolicy.fromJson(json.optJSONObject("lifecycleLease")),
                budgetStates = budgetStateMerge.items,
                repeatOffender = RuntimeRepeatOffenderPolicy.fromJson(json.optJSONObject("repeatOffender")),
                policyPath = policyPath,
                loadStatus = loadStatus,
                compatOverlayStatus = if (overlayCount > 0) {
                    "default_safety_floor_overlaid"
                } else {
                    "none"
                },
                compatAddedLaneCount = laneMerge.addedDefaults.size,
                compatAddedEnvelopeCount = envelopeMerge.addedDefaults.size,
                compatAddedBudgetStateCount = budgetStateMerge.addedDefaults.size,
                compatAddedEnvelopeClasses = envelopeMerge.addedDefaults.map { it.workloadClass }
            )
        }

        fun defaultLanes(): List<RuntimeLanePolicy> {
            return listOf(
                RuntimeLanePolicy(RuntimeLaneKind.INTERACTIVE, maxConcurrency = 2, backgroundMaxConcurrency = 1, serial = false, allowBurst = true, priority = 0),
                RuntimeLanePolicy(RuntimeLaneKind.SERVICE, maxConcurrency = 3, backgroundMaxConcurrency = 2, serial = false, allowBurst = false, priority = 20),
                RuntimeLanePolicy(RuntimeLaneKind.BUILD, maxConcurrency = 1, backgroundMaxConcurrency = 0, serial = true, allowBurst = false, priority = 60),
                RuntimeLanePolicy(RuntimeLaneKind.PROBE, maxConcurrency = 1, backgroundMaxConcurrency = 0, serial = true, allowBurst = false, priority = 90)
            )
        }

        fun defaultEnvelopes(): List<RuntimeWorkloadEnvelope> {
            return listOf(
                RuntimeWorkloadEnvelope(RuntimeWorkloadClass.SYSTEM_CORE, RuntimeWorkloadRetention.KEEP, backgroundAllowed = true, maxChildren = 8, maxRuntimeMs = 0L, maxIdleMs = 0L, restartAllowed = true, autoQuarantineAllowed = false),
                RuntimeWorkloadEnvelope(RuntimeWorkloadClass.PINNED_SERVICE, RuntimeWorkloadRetention.KEEP, backgroundAllowed = true, maxChildren = 6, maxRuntimeMs = 0L, maxIdleMs = 0L, restartAllowed = true, autoQuarantineAllowed = true),
                RuntimeWorkloadEnvelope(RuntimeWorkloadClass.INTERACTIVE, RuntimeWorkloadRetention.KEEP, backgroundAllowed = false, maxChildren = 16, maxRuntimeMs = 0L, maxIdleMs = 10 * 60_000L, restartAllowed = false, autoQuarantineAllowed = false),
                RuntimeWorkloadEnvelope(RuntimeWorkloadClass.BUILD, RuntimeWorkloadRetention.LEASE, backgroundAllowed = false, maxChildren = 32, maxRuntimeMs = 2 * 60 * 60_000L, maxIdleMs = 10 * 60_000L, restartAllowed = false, autoQuarantineAllowed = true),
                RuntimeWorkloadEnvelope(RuntimeWorkloadClass.PROBE, RuntimeWorkloadRetention.LEASE, backgroundAllowed = false, maxChildren = 4, maxRuntimeMs = 2 * 60_000L, maxIdleMs = 30_000L, restartAllowed = false, autoQuarantineAllowed = true),
                RuntimeWorkloadEnvelope(RuntimeWorkloadClass.EPHEMERAL, RuntimeWorkloadRetention.LEASE, backgroundAllowed = false, maxChildren = 8, maxRuntimeMs = 5 * 60_000L, maxIdleMs = 60_000L, restartAllowed = false, autoQuarantineAllowed = true),
                RuntimeWorkloadEnvelope(RuntimeWorkloadClass.STRAY, RuntimeWorkloadRetention.CLEANUP_CANDIDATE, backgroundAllowed = false, maxChildren = 0, maxRuntimeMs = 60_000L, maxIdleMs = 30_000L, restartAllowed = false, autoQuarantineAllowed = true),
                RuntimeWorkloadEnvelope(RuntimeWorkloadClass.UNKNOWN, RuntimeWorkloadRetention.CLEANUP_CANDIDATE, backgroundAllowed = false, maxChildren = 0, maxRuntimeMs = 60_000L, maxIdleMs = 30_000L, restartAllowed = false, autoQuarantineAllowed = true)
            )
        }

        fun defaultBudgetStates(): List<RuntimeBudgetStatePolicy> {
            return listOf(
                RuntimeBudgetStatePolicy(RuntimeBudgetState.HEALTHY, listOf(RuntimeBudgetAction.OBSERVE), "within budget"),
                RuntimeBudgetStatePolicy(RuntimeBudgetState.NEAR_BUDGET, listOf(RuntimeBudgetAction.WARN, RuntimeBudgetAction.REQUEST_CLEANUP), "first pressure warning; do not restart on first sight"),
                RuntimeBudgetStatePolicy(RuntimeBudgetState.SOFT_PRESSURE, listOf(RuntimeBudgetAction.THROTTLE, RuntimeBudgetAction.REQUEST_CLEANUP), "stop feeding new low priority work and ask workload to self-clean"),
                RuntimeBudgetStatePolicy(RuntimeBudgetState.HARD_PRESSURE, listOf(RuntimeBudgetAction.FREEZE_SHORT, RuntimeBudgetAction.TERMINATE_CHILDREN, RuntimeBudgetAction.RESTART_MAIN), "protect the registered root, cut abnormal children first"),
                RuntimeBudgetStatePolicy(RuntimeBudgetState.THREATENING_KF, listOf(RuntimeBudgetAction.RECOVERY_CUTOFF, RuntimeBudgetAction.TERMINATE_WORKLOAD), "KF platform survival outranks a single workload"),
                RuntimeBudgetStatePolicy(RuntimeBudgetState.REPEAT_OFFENDER, listOf(RuntimeBudgetAction.QUARANTINE), "rapid relapse or repeated violations remove background rights"),
                RuntimeBudgetStatePolicy(RuntimeBudgetState.QUARANTINED, listOf(RuntimeBudgetAction.OBSERVE), "manual foreground recovery only")
            )
        }

        private data class SafetyFloorMerge<T>(
            val items: List<T>,
            val addedDefaults: List<T>
        )

        private fun <T, K> mergeWithSafetyFloor(
            parsed: List<T>,
            defaults: List<T>,
            keyOf: (T) -> K
        ): SafetyFloorMerge<T> {
            val parsedByKey = parsed.associateBy(keyOf)
            val defaultKeys = defaults.map(keyOf).toSet()
            val merged = defaults.map { defaultItem ->
                parsedByKey[keyOf(defaultItem)] ?: defaultItem
            } + parsed.filter { item ->
                keyOf(item) !in defaultKeys
            }
            val addedDefaults = defaults.filter { defaultItem ->
                keyOf(defaultItem) !in parsedByKey
            }
            return SafetyFloorMerge(merged, addedDefaults)
        }
    }
}

object RuntimeWorkloadPolicyStore {
    private const val LOG_TAG = "RuntimeWorkloadPolicy"

    fun load(context: Context): RuntimeWorkloadPolicy {
        val file = resolveFile(context) ?: return RuntimeWorkloadPolicy.default().copy(loadStatus = "workspace_missing")
        if (!file.exists()) {
            val defaultPolicy = RuntimeWorkloadPolicy.default(file.absolutePath)
            writeDefault(file, defaultPolicy)
            return defaultPolicy.copy(loadStatus = "bootstrapped_default")
        }
        return runCatching {
            RuntimeWorkloadPolicy.fromJson(
                json = JSONObject(file.readText()),
                policyPath = file.absolutePath
            )
        }.getOrElse { error ->
            Logger.e(LOG_TAG, "failed to load runtime workload policy: ${error.message}")
            RuntimeWorkloadPolicy.default(file.absolutePath).copy(
                loadStatus = "error_default",
                loadError = error.message
            )
        }
    }

    private fun writeDefault(file: File, policy: RuntimeWorkloadPolicy) {
        runCatching {
            file.parentFile?.mkdirs()
            file.writeText(policy.toJson().toString(2) + "\n")
        }.onFailure { error ->
            Logger.e(LOG_TAG, "failed to bootstrap runtime workload policy: ${error.message}")
        }
    }

    private fun resolveFile(context: Context): File? {
        val workspacePath = WorkSurfaceRuntimeBridge.getSavedContainer(context)?.workspacePath
            ?.takeIf { it.isNotBlank() }
            ?: return null
        return WorkspaceBuildSupport.runtimeWorkloadPolicyFile(File(workspacePath))
    }
}

private fun <T> JSONArray.toList(parser: (JSONObject) -> T): List<T> {
    return buildList {
        for (index in 0 until length()) {
            val json = optJSONObject(index) ?: continue
            add(parser(json))
        }
    }
}

private fun JSONObject.positiveLongOrDefault(key: String, default: Long): Long {
    if (!has(key)) return default
    return optLong(key, default).takeIf { it > 0L } ?: default
}
