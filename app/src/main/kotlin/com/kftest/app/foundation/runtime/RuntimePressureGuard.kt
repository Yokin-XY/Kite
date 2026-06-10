package com.kftest.app.foundation.runtime

import com.kftest.app.foundation.service.RuntimeRetentionClass
import com.kftest.app.foundation.workspace.WorkspaceBuildSupport
import java.io.File

enum class RuntimePressureLevel {
    UNKNOWN,
    NORMAL,
    ELEVATED,
    HIGH,
    CRITICAL
}

enum class RuntimeReclaimability {
    PROTECTED,
    INTERACTIVE,
    RECLAIMABLE,
    UNKNOWN
}

data class RuntimePressureRoot(
    val ownerKind: RuntimeRootOwnerKind,
    val ownerId: String?,
    val title: String,
    val observedPid: Int?,
    val processCount: Int,
    val retentionClass: RuntimeRetentionClass,
    val resident: Boolean,
    val reclaimPriority: Int,
    val autoReclaimAllowed: Boolean,
    val classificationSource: String,
    val reclaimability: RuntimeReclaimability,
    val rssKb: Long,
    val oomScoreAdj: Int?,
    val reason: String
)

data class RuntimePressureSnapshot(
    val level: RuntimePressureLevel = RuntimePressureLevel.UNKNOWN,
    val activeProfile: String = RuntimeReclaimerProfile.BALANCED.name,
    val policyPath: String? = null,
    val policyLoadedAtMs: Long = 0L,
    val policyLoadStatus: String = "unknown",
    val policyLoadError: String? = null,
    val evaluatedAtMs: Long = 0L,
    val pressureBasis: String = "unknown",
    val memoryBudgetKb: Long? = null,
    val elevatedThresholdKb: Long = 0L,
    val highThresholdKb: Long = 0L,
    val criticalThresholdKb: Long = 0L,
    val hostMemTotalKb: Long? = null,
    val hostMemAvailableKb: Long? = null,
    val hostAvailableLevel: RuntimePressureLevel = RuntimePressureLevel.UNKNOWN,
    val totalRssKb: Long = 0L,
    val protectedRssKb: Long = 0L,
    val reclaimableRssKb: Long = 0L,
    val unknownRssRootCount: Int = 0,
    val classifiedUnknownRootCount: Int = 0,
    val candidates: List<RuntimePressureRoot> = emptyList()
) {
    val candidateCount: Int
        get() = candidates.size

    fun summary(): String {
        return "level=$level profile=$activeProfile total=${formatKb(totalRssKb)} protected=${formatKb(protectedRssKb)} " +
            "reclaimable=${formatKb(reclaimableRssKb)} candidates=$candidateCount unknown=$unknownRssRootCount"
    }

    fun toEnvText(): String {
        val builder = StringBuilder()
        builder.appendLine("KFSHELL_RUNTIME_PRESSURE_VERSION=1")
        builder.appendLine("source=android_runtime_health_store")
        builder.appendLine("level=${level.name}")
        builder.appendLine("active_profile=$activeProfile")
        builder.appendLine("policy_path=${policyPath.toEnvValue()}")
        builder.appendLine("policy_loaded_at_ms=$policyLoadedAtMs")
        builder.appendLine("policy_load_status=${policyLoadStatus.toEnvValue()}")
        builder.appendLine("policy_load_error=${policyLoadError.toEnvValue()}")
        builder.appendLine("evaluated_at_ms=$evaluatedAtMs")
        builder.appendLine("pressure_basis=$pressureBasis")
        builder.appendLine("memory_budget_kb=${memoryBudgetKb ?: 0L}")
        builder.appendLine("threshold_elevated_rss_kb=$elevatedThresholdKb")
        builder.appendLine("threshold_high_rss_kb=$highThresholdKb")
        builder.appendLine("threshold_critical_rss_kb=$criticalThresholdKb")
        builder.appendLine("host_mem_total_kb=${hostMemTotalKb ?: 0L}")
        builder.appendLine("host_mem_available_kb=${hostMemAvailableKb ?: 0L}")
        builder.appendLine("host_available_level=${hostAvailableLevel.name}")
        builder.appendLine("total_rss_kb=$totalRssKb")
        builder.appendLine("protected_rss_kb=$protectedRssKb")
        builder.appendLine("reclaimable_rss_kb=$reclaimableRssKb")
        builder.appendLine("unknown_rss_root_count=$unknownRssRootCount")
        builder.appendLine("classified_unknown_root_count=$classifiedUnknownRootCount")
        builder.appendLine("candidate_count=$candidateCount")
        candidates.take(5).forEachIndexed { index, candidate ->
            val ordinal = index + 1
            builder.appendLine("candidate_${ordinal}_owner_kind=${candidate.ownerKind.name}")
            builder.appendLine("candidate_${ordinal}_owner_id=${candidate.ownerId.toEnvValue()}")
            builder.appendLine("candidate_${ordinal}_title=${candidate.title.toEnvValue()}")
            builder.appendLine("candidate_${ordinal}_pid=${candidate.observedPid ?: 0}")
            builder.appendLine("candidate_${ordinal}_rss_kb=${candidate.rssKb}")
            builder.appendLine("candidate_${ordinal}_retention=${candidate.retentionClass.name}")
            builder.appendLine("candidate_${ordinal}_linux_like=${candidate.retentionClass.linuxLikeLabel}")
            builder.appendLine("candidate_${ordinal}_resident=${candidate.resident}")
            builder.appendLine("candidate_${ordinal}_reclaim_priority=${candidate.reclaimPriority}")
            builder.appendLine("candidate_${ordinal}_auto_reclaim=${candidate.autoReclaimAllowed}")
            builder.appendLine("candidate_${ordinal}_classification=${candidate.classificationSource.toEnvValue()}")
            builder.appendLine("candidate_${ordinal}_reason=${candidate.reason.toEnvValue()}")
        }
        builder.appendLine("policy_resident_classes=CRITICAL_CORE,RESIDENT,INTERACTIVE")
        builder.appendLine("policy_reclaim_order=registered_ephemeral_then_batch_then_classified_unknown")
        builder.appendLine("workload_policy_path=${WorkspaceBuildSupport.CONTAINER_RUNTIME_WORKLOAD_POLICY_PATH}")
        builder.appendLine("workload_policy_authority=android_control_plane")
        builder.appendLine("workload_policy_contract=proot_facts_kf_decides_ubuntu_declares")
        builder.appendLine("reclaimer_mode=android_control_plane_v0")
        builder.appendLine("reclaimer_trigger=depends_on_active_profile")
        builder.appendLine("admission_trigger=depends_on_active_profile")
        builder.appendLine("behavior_NORMAL=observe_only_no_auto_reclaim_no_admission_defer")
        builder.appendLine("behavior_ELEVATED=observe_and_refresh_low_priority_admission_may_defer_by_profile")
        builder.appendLine("behavior_HIGH=profile_may_reclaim_ephemeral_and_defer_low_priority")
        builder.appendLine("behavior_CRITICAL=profile_may_reclaim_batch_and_classified_unknown")
        builder.appendLine("unknown_rule_scope=unattributed_runtime_roots_only")
        builder.appendLine("unknown_rule_fields=TITLE,COMMAND,COMMAND_LINE,SOURCE_LABEL")
        builder.appendLine("unknown_rule_modes=CONTAINS,EXACT,PREFIX,SUFFIX")
        builder.appendLine("hint=active_profile_controls_auto_reclaim_and_low_priority_admission; unclassified_unknown_roots_are_manual_only")
        return builder.toString()
    }
}

object RuntimePressureGuard {
    private const val ELEVATED_TOTAL_RSS_KB = 512L * 1024L
    private const val HIGH_TOTAL_RSS_KB = 1024L * 1024L
    private const val CRITICAL_TOTAL_RSS_KB = 1536L * 1024L
    private const val PROC_MEMINFO = "/proc/meminfo"

    fun evaluate(
        roots: List<RuntimeRootSnapshot>,
        reclaimerPolicy: RuntimeReclaimerPolicy
    ): RuntimePressureSnapshot {
        val runningRoots = roots.filter { it.isRunning }
        val totalRssKb = runningRoots.sumOf { it.rssKb }
        val hostMemory = readHostMemory()
        val thresholds = resolveThresholds(reclaimerPolicy.memoryPressure)
        val rssLevel = levelForRss(totalRssKb, runningRoots.isNotEmpty(), thresholds)
        val hostAvailableLevel = levelForHostAvailable(hostMemory, reclaimerPolicy.memoryPressure)
        val level = maxPressureLevel(rssLevel, hostAvailableLevel)

        val pressureRoots = runningRoots.map { root -> root.toPressureRoot() }
        val protectedRssKb = pressureRoots
            .filter { it.reclaimability == RuntimeReclaimability.PROTECTED || it.reclaimability == RuntimeReclaimability.INTERACTIVE }
            .sumOf { it.rssKb }
        val reclaimableRssKb = pressureRoots
            .filter { it.reclaimability == RuntimeReclaimability.RECLAIMABLE }
            .sumOf { it.rssKb }

        return RuntimePressureSnapshot(
            level = level,
            activeProfile = reclaimerPolicy.activeProfile.name,
            policyPath = reclaimerPolicy.policyPath,
            policyLoadedAtMs = reclaimerPolicy.loadedAtMs,
            policyLoadStatus = reclaimerPolicy.loadStatus,
            policyLoadError = reclaimerPolicy.loadError,
            evaluatedAtMs = System.currentTimeMillis(),
            pressureBasis = buildPressureBasis(
                memoryBudgetKb = reclaimerPolicy.memoryPressure.memoryBudgetKb,
                rssLevel = rssLevel,
                hostAvailableLevel = hostAvailableLevel
            ),
            memoryBudgetKb = reclaimerPolicy.memoryPressure.memoryBudgetKb,
            elevatedThresholdKb = thresholds.elevatedKb,
            highThresholdKb = thresholds.highKb,
            criticalThresholdKb = thresholds.criticalKb,
            hostMemTotalKb = hostMemory.memTotalKb,
            hostMemAvailableKb = hostMemory.memAvailableKb,
            hostAvailableLevel = hostAvailableLevel,
            totalRssKb = totalRssKb,
            protectedRssKb = protectedRssKb,
            reclaimableRssKb = reclaimableRssKb,
            unknownRssRootCount = runningRoots.count { it.rssKb <= 0L },
            classifiedUnknownRootCount = pressureRoots.count {
                it.ownerKind == RuntimeRootOwnerKind.UNATTRIBUTED &&
                    it.classificationSource.startsWith("policy:")
            },
            candidates = pressureRoots
                .filter { it.reclaimability == RuntimeReclaimability.RECLAIMABLE }
                .sortedWith(
                    compareByDescending<RuntimePressureRoot> { it.reclaimPriority }
                        .thenByDescending { it.rssKb }
                        .thenBy { it.title }
                )
        )
    }

    private fun resolveThresholds(policy: RuntimeMemoryPressurePolicy): RuntimePressureThresholds {
        val budget = policy.memoryBudgetKb?.takeIf { it > 0L }
        if (budget == null) {
            return RuntimePressureThresholds(
                elevatedKb = ELEVATED_TOTAL_RSS_KB,
                highKb = HIGH_TOTAL_RSS_KB,
                criticalKb = CRITICAL_TOTAL_RSS_KB
            )
        }
        return RuntimePressureThresholds(
            elevatedKb = percentOf(budget, policy.elevatedRssPercent),
            highKb = percentOf(budget, policy.highRssPercent),
            criticalKb = percentOf(budget, policy.criticalRssPercent)
        )
    }

    private fun percentOf(value: Long, percent: Int): Long {
        return ((value * percent.coerceIn(1, 99)) / 100L).coerceAtLeast(1L)
    }

    private fun levelForRss(
        totalRssKb: Long,
        hasRunningRoots: Boolean,
        thresholds: RuntimePressureThresholds
    ): RuntimePressureLevel {
        return when {
            !hasRunningRoots || totalRssKb <= 0L -> RuntimePressureLevel.UNKNOWN
            totalRssKb >= thresholds.criticalKb -> RuntimePressureLevel.CRITICAL
            totalRssKb >= thresholds.highKb -> RuntimePressureLevel.HIGH
            totalRssKb >= thresholds.elevatedKb -> RuntimePressureLevel.ELEVATED
            else -> RuntimePressureLevel.NORMAL
        }
    }

    private fun levelForHostAvailable(
        memory: HostMemorySnapshot,
        policy: RuntimeMemoryPressurePolicy
    ): RuntimePressureLevel {
        val available = memory.memAvailableKb ?: return RuntimePressureLevel.UNKNOWN
        return when {
            policy.criticalHostAvailableKb > 0L && available <= policy.criticalHostAvailableKb ->
                RuntimePressureLevel.CRITICAL
            policy.highHostAvailableKb > 0L && available <= policy.highHostAvailableKb ->
                RuntimePressureLevel.HIGH
            policy.elevatedHostAvailableKb > 0L && available <= policy.elevatedHostAvailableKb ->
                RuntimePressureLevel.ELEVATED
            else -> RuntimePressureLevel.NORMAL
        }
    }

    private fun maxPressureLevel(
        first: RuntimePressureLevel,
        second: RuntimePressureLevel
    ): RuntimePressureLevel {
        if (first == RuntimePressureLevel.UNKNOWN) return second
        if (second == RuntimePressureLevel.UNKNOWN) return first
        return if (first.ordinal >= second.ordinal) first else second
    }

    private fun buildPressureBasis(
        memoryBudgetKb: Long?,
        rssLevel: RuntimePressureLevel,
        hostAvailableLevel: RuntimePressureLevel
    ): String {
        val budgetMode = if (memoryBudgetKb != null && memoryBudgetKb > 0L) {
            "policy_budget"
        } else {
            "legacy_absolute"
        }
        return "$budgetMode+host_available:rss=${rssLevel.name},host=${hostAvailableLevel.name}"
    }

    private fun readHostMemory(): HostMemorySnapshot {
        return runCatching {
            val values = File(PROC_MEMINFO)
                .readLines()
                .mapNotNull { line ->
                    val parts = line.split(Regex("\\s+"))
                    val key = parts.firstOrNull()?.removeSuffix(":") ?: return@mapNotNull null
                    val value = parts.getOrNull(1)?.toLongOrNull() ?: return@mapNotNull null
                    key to value
                }
                .toMap()
            HostMemorySnapshot(
                memTotalKb = values["MemTotal"],
                memAvailableKb = values["MemAvailable"]
            )
        }.getOrDefault(HostMemorySnapshot())
    }

    private fun RuntimeRootSnapshot.toPressureRoot(): RuntimePressureRoot {
        val reclaimability = when {
            retentionClass == RuntimeRetentionClass.UNKNOWN -> RuntimeReclaimability.UNKNOWN
            resident && retentionClass == RuntimeRetentionClass.INTERACTIVE ->
                RuntimeReclaimability.INTERACTIVE
            resident -> RuntimeReclaimability.PROTECTED
            autoReclaimAllowed -> RuntimeReclaimability.RECLAIMABLE
            else -> RuntimeReclaimability.UNKNOWN
        }
        val reason = when (reclaimability) {
            RuntimeReclaimability.PROTECTED -> "protected:${retentionClass.name}"
            RuntimeReclaimability.INTERACTIVE -> "interactive owner"
            RuntimeReclaimability.RECLAIMABLE -> "candidate:${retentionClass.name}:${classificationSource}"
            RuntimeReclaimability.UNKNOWN -> "unknown:${classificationSource}"
        }
        return RuntimePressureRoot(
            ownerKind = ownerKind,
            ownerId = ownerId,
            title = title,
            observedPid = observedPid,
            processCount = processCount,
            retentionClass = retentionClass,
            resident = resident,
            reclaimPriority = reclaimPriority,
            autoReclaimAllowed = autoReclaimAllowed,
            classificationSource = classificationSource,
            reclaimability = reclaimability,
            rssKb = rssKb,
            oomScoreAdj = maxOomScoreAdj,
            reason = reason
        )
    }

    private data class RuntimePressureThresholds(
        val elevatedKb: Long,
        val highKb: Long,
        val criticalKb: Long
    )

    private data class HostMemorySnapshot(
        val memTotalKb: Long? = null,
        val memAvailableKb: Long? = null
    )
}

private fun formatKb(kb: Long): String {
    return when {
        kb <= 0L -> "0"
        kb < 1024L -> "${kb}KB"
        else -> "${kb / 1024L}MB"
    }
}

private fun String?.toEnvValue(): String {
    if (this.isNullOrBlank()) return "none"
    return trim()
        .replace(Regex("[\\r\\n\\t ]+"), "_")
        .replace(Regex("[^A-Za-z0-9._:@/-]"), "_")
        .take(160)
}
