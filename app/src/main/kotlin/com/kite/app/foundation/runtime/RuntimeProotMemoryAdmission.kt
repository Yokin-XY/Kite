package com.kite.app.foundation.runtime

import java.util.LinkedHashMap

data class RuntimeProotMemoryAdmissionPolicy(
    val baseProotMemoryKb: Long,
    val estimatedTaskMemoryKb: Long?,
    val safetyMarginKb: Long
)

data class RuntimeProotMemoryAdmissionDecision(
    val requiredMemoryKb: Long,
    val baseProotMemoryKb: Long,
    val estimatedTaskMemoryKb: Long,
    val safetyMarginKb: Long,
    val reservedMemoryKb: Long,
    val availableAfterExistingReservationsKb: Long,
    val availableAfterNewReservationKb: Long,
    val canReserve: Boolean,
    val reason: String
)

object RuntimeProotMemoryAdmission {
    private val reservations = LinkedHashMap<String, Long>()

    @Synchronized
    fun evaluate(
        expansionRequested: Boolean,
        hostAvailableKb: Long,
        peakTasks: Int,
        defaultEstimatedTaskMemoryKb: Long,
        policy: RuntimeProotMemoryAdmissionPolicy,
        memorySignalsOk: Boolean,
        globalBudgetLedger: RuntimeProcessMemoryBudgetLedgerSnapshot? = null
    ): RuntimeProotMemoryAdmissionDecision {
        val safePeakTasks = peakTasks.coerceAtLeast(1)
        val baseKb = policy.baseProotMemoryKb.coerceAtLeast(0L)
        val estimatedTaskKb = (policy.estimatedTaskMemoryKb ?: defaultEstimatedTaskMemoryKb)
            .coerceAtLeast(1L)
        val safetyKb = policy.safetyMarginKb.coerceAtLeast(0L)
        val requiredKb = baseKb + (estimatedTaskKb * safePeakTasks.toLong()) + safetyKb
        val reservedKb = reservations.values.sum()
        val effectiveHostAvailableKb = globalBudgetLedger
            ?.availableForElasticProotBeforeProotReservationsKb
            ?: hostAvailableKb
        val availableAfterExistingKb = (effectiveHostAvailableKb - reservedKb).coerceAtLeast(0L)
        val availableAfterNewKb = (availableAfterExistingKb - requiredKb).coerceAtLeast(0L)
        val canReserve = expansionRequested &&
            memorySignalsOk &&
            hostAvailableKb > 0L &&
            effectiveHostAvailableKb > 0L &&
            availableAfterExistingKb >= requiredKb
        val reason = when {
            !expansionRequested -> "not_requested"
            !memorySignalsOk -> "memory_signals_not_healthy"
            hostAvailableKb <= 0L -> "host_available_memory_unknown"
            effectiveHostAvailableKb <= 0L -> "global_memory_budget_unavailable_for_proot"
            availableAfterExistingKb < requiredKb ->
                "insufficient_memory_after_existing_reservations"
            else -> "memory_budget_available"
        }
        return RuntimeProotMemoryAdmissionDecision(
            requiredMemoryKb = requiredKb,
            baseProotMemoryKb = baseKb,
            estimatedTaskMemoryKb = estimatedTaskKb,
            safetyMarginKb = safetyKb,
            reservedMemoryKb = reservedKb,
            availableAfterExistingReservationsKb = availableAfterExistingKb,
            availableAfterNewReservationKb = availableAfterNewKb,
            canReserve = canReserve,
            reason = reason
        )
    }

    @Synchronized
    fun reservedMemoryKb(): Long {
        return reservations.values.sum()
    }

    @Synchronized
    fun reserve(runtimeId: String, requiredMemoryKb: Long): Boolean {
        val normalized = runtimeId.trim().takeIf { it.isNotBlank() } ?: return false
        if (requiredMemoryKb <= 0L) {
            return false
        }
        if (reservations.containsKey(normalized)) {
            return false
        }
        reservations[normalized] = requiredMemoryKb
        return true
    }

    @Synchronized
    fun release(runtimeId: String) {
        reservations.remove(runtimeId.trim())
    }
}
