package com.kite.app.foundation.runtime

internal data class ProotUnifiedActualHealthSnapshot(
    val state: UnifiedProotCapacityState,
    val effectiveGlobalMax: Int,
    val shortActiveCount: Int,
    val longActiveCount: Int,
    val totalActiveCount: Int,
    val shortQueuedCount: Int,
    val longQueuedCount: Int,
    val totalQueuedCount: Int,
    val remainingCapacity: Int,
    val longAdmissionMax: Int,
    val longAdmissionRemaining: Int,
    val shortHeadroomCapacity: Int,
    val shortHeadroomProtected: Boolean,
    val restoredLongOwnerTotal: Long,
    val contractBlockCount: Int,
) {
    companion object {
        fun empty() = ProotUnifiedActualHealthSnapshot(
            state = UnifiedProotCapacityState.READY,
            effectiveGlobalMax = 1,
            shortActiveCount = 0,
            longActiveCount = 0,
            totalActiveCount = 0,
            shortQueuedCount = 0,
            longQueuedCount = 0,
            totalQueuedCount = 0,
            remainingCapacity = 1,
            longAdmissionMax = 1,
            longAdmissionRemaining = 1,
            shortHeadroomCapacity = 0,
            shortHeadroomProtected = false,
            restoredLongOwnerTotal = 0L,
            contractBlockCount = 0,
        )
    }
}

/** 从唯一 actual admission 同锁快照生成短任务、长期 owner 与统一总量。 */
internal object ProotUnifiedActualHealthProjection {
    fun project(admission: ProotJobAdmissionSnapshot): ProotUnifiedActualHealthSnapshot {
        val countsValid = admission.effectiveGlobalMax > 0 &&
            admission.activeCount >= 0 && admission.queuedCount >= 0 &&
            admission.managedOwnerActiveCount in 0..admission.activeCount &&
            admission.managedOwnerQueuedCount in 0..admission.queuedCount &&
            admission.activeByLane.values.sum() == admission.activeCount &&
            admission.queuedByLane.values.sum() == admission.queuedCount &&
            admission.activeByLane.keys.containsAll(RuntimeLaneKind.entries) &&
            admission.queuedByLane.keys.containsAll(RuntimeLaneKind.entries) &&
            admission.managedOwnerAdmissionMax in 1..admission.effectiveGlobalMax &&
            admission.restoredCount >= 0L && admission.contractBlockCount >= 0
        val state = when {
            !countsValid || admission.contractBlockCount > 0 ->
                UnifiedProotCapacityState.CONTRACT_MISMATCH
            admission.activeCount > admission.effectiveGlobalMax ->
                UnifiedProotCapacityState.OVERCOMMITTED
            admission.activeCount == admission.effectiveGlobalMax ->
                UnifiedProotCapacityState.FULL
            else -> UnifiedProotCapacityState.READY
        }
        val longActive = admission.managedOwnerActiveCount.coerceAtLeast(0)
        val longQueued = admission.managedOwnerQueuedCount.coerceAtLeast(0)
        val shortActive = (admission.activeCount - longActive).coerceAtLeast(0)
        val shortQueued = (admission.queuedCount - longQueued).coerceAtLeast(0)
        return ProotUnifiedActualHealthSnapshot(
            state = state,
            effectiveGlobalMax = admission.effectiveGlobalMax,
            shortActiveCount = shortActive,
            longActiveCount = longActive,
            totalActiveCount = admission.activeCount,
            shortQueuedCount = shortQueued,
            longQueuedCount = longQueued,
            totalQueuedCount = admission.queuedCount,
            remainingCapacity = if (state == UnifiedProotCapacityState.READY) {
                (admission.effectiveGlobalMax - admission.activeCount).coerceAtLeast(0)
            } else {
                0
            },
            longAdmissionMax = admission.managedOwnerAdmissionMax.coerceAtLeast(0),
            longAdmissionRemaining = if (countsValid) {
                (admission.managedOwnerAdmissionMax - longActive).coerceAtLeast(0)
            } else {
                0
            },
            shortHeadroomCapacity = if (countsValid) {
                (admission.effectiveGlobalMax - admission.managedOwnerAdmissionMax).coerceAtLeast(0)
            } else {
                0
            },
            shortHeadroomProtected = countsValid &&
                admission.effectiveGlobalMax > admission.managedOwnerAdmissionMax &&
                longActive >= admission.managedOwnerAdmissionMax,
            restoredLongOwnerTotal = admission.restoredCount,
            contractBlockCount = admission.contractBlockCount,
        )
    }
}

internal fun ProotUnifiedActualHealthSnapshot.toRuntimeHealthEnvText(): String = buildString {
    appendLine("proot_long_actual_schema=managed_proot_owner_v2")
    appendLine("proot_long_actual_source=shared_proot_admission_controller")
    appendLine("proot_long_actual_scope=actual_not_planned")
    appendLine("proot_long_actual_active_owner_count=$longActiveCount")
    appendLine("proot_long_actual_queued_owner_count=$longQueuedCount")
    appendLine("proot_long_actual_admission_max=$longAdmissionMax")
    appendLine("proot_long_actual_admission_remaining=$longAdmissionRemaining")
    appendLine("proot_long_actual_restored_owner_total=$restoredLongOwnerTotal")
    appendLine("proot_long_actual_contract_block_count=$contractBlockCount")
    appendLine("proot_unified_actual_schema=shared_proot_capacity_v2")
    appendLine("proot_unified_actual_source=shared_proot_admission_controller")
    appendLine("proot_unified_actual_scope=actual_not_planned")
    appendLine("proot_unified_actual_state=${state.name}")
    appendLine("proot_unified_actual_effective_global_max=$effectiveGlobalMax")
    appendLine("proot_unified_actual_short_active_count=$shortActiveCount")
    appendLine("proot_unified_actual_long_active_count=$longActiveCount")
    appendLine("proot_unified_actual_total_active_count=$totalActiveCount")
    appendLine("proot_unified_actual_short_queued_count=$shortQueuedCount")
    appendLine("proot_unified_actual_long_queued_count=$longQueuedCount")
    appendLine("proot_unified_actual_total_queued_count=$totalQueuedCount")
    appendLine("proot_unified_actual_remaining_capacity=$remainingCapacity")
    appendLine("proot_unified_actual_short_headroom_capacity=$shortHeadroomCapacity")
    appendLine("proot_unified_actual_short_headroom_protected=$shortHeadroomProtected")
}
