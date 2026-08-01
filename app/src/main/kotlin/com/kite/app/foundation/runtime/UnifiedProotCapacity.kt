package com.kite.app.foundation.runtime

internal enum class UnifiedProotCapacityState {
    READY,
    FULL,
    OVERCOMMITTED,
    EXCLUSIVE_MAINTENANCE_ACTIVE,
    CONTRACT_MISMATCH,
}

/**
 * RF910 统一容量只读合同。
 *
 * 短任务仍由生产 controller 持有，长期记录由未来后台桥提供；本快照不保存 owner、命令、路径或
 * 进程身份，也不执行准入、启动、停止和恢复。
 */
internal data class UnifiedProotCapacitySnapshot(
    val scope: String = "unified_contract_not_production",
    val state: UnifiedProotCapacityState,
    val effectiveGlobalMax: Int,
    val shortActiveCount: Int,
    val longActiveCount: Int,
    val totalActiveCount: Int,
    val shortQueuedCount: Int,
    val longQueuedCount: Int,
    val totalQueuedCount: Int,
    val remainingCapacity: Int,
    val activeSharedWrite: Boolean,
    val activeExclusiveMaintenance: Boolean,
    val activeByLane: Map<RuntimeLaneKind, Int>,
    val queuedByLane: Map<RuntimeLaneKind, Int>,
    val duplicateOwnerCount: Int,
    val conflictingProcessIdentityCount: Int,
) {
    val allowsNewAdmission: Boolean
        get() = state == UnifiedProotCapacityState.READY && remainingCapacity > 0
}

internal object UnifiedProotCapacityProjection {
    fun project(
        shortTasks: ProotJobAdmissionSnapshot,
        longLivedRecords: List<LongLivedProotLeaseRecord>,
    ): UnifiedProotCapacitySnapshot {
        val liveRecords = longLivedRecords.filter { it.phase != LongLivedProotLeasePhase.RELEASED }
        val activeLong = liveRecords.filter(LongLivedProotLeaseRecord::holdsCapacity)
        val queuedLong = liveRecords.filter { it.phase == LongLivedProotLeasePhase.REQUESTED }
        val duplicateOwnerCount = liveRecords
            .groupingBy { it.spec.owner }
            .eachCount()
            .values
            .count { it > 1 }
        val conflictingProcessIdentityCount = activeLong
            .mapNotNull(LongLivedProotLeaseRecord::processIdentity)
            .groupingBy { it }
            .eachCount()
            .values
            .count { it > 1 }
        val activeExclusiveCount = activeLong.count {
            it.spec.filesystemPosture == LongLivedProotFilesystemPosture.EXCLUSIVE_MAINTENANCE
        }
        val shortLaneContractValid = shortTasks.activeByLane.values.sum() == shortTasks.activeCount &&
            shortTasks.queuedByLane.values.sum() == shortTasks.queuedCount &&
            shortTasks.activeByLane.keys.containsAll(RuntimeLaneKind.entries) &&
            shortTasks.queuedByLane.keys.containsAll(RuntimeLaneKind.entries)
        val totalActive = shortTasks.activeCount + activeLong.size
        val totalQueued = shortTasks.queuedCount + queuedLong.size
        val exclusiveContractValid = activeExclusiveCount == 0 ||
            (activeExclusiveCount == 1 && totalActive == 1 && !shortTasks.activeSharedWrite)
        val contractValid = shortLaneContractValid && duplicateOwnerCount == 0 &&
            conflictingProcessIdentityCount == 0 && exclusiveContractValid
        val state = when {
            !contractValid -> UnifiedProotCapacityState.CONTRACT_MISMATCH
            activeExclusiveCount == 1 -> UnifiedProotCapacityState.EXCLUSIVE_MAINTENANCE_ACTIVE
            totalActive > shortTasks.effectiveGlobalMax -> UnifiedProotCapacityState.OVERCOMMITTED
            totalActive == shortTasks.effectiveGlobalMax -> UnifiedProotCapacityState.FULL
            else -> UnifiedProotCapacityState.READY
        }
        val remaining = if (state == UnifiedProotCapacityState.READY) {
            (shortTasks.effectiveGlobalMax - totalActive).coerceAtLeast(0)
        } else {
            0
        }

        return UnifiedProotCapacitySnapshot(
            state = state,
            effectiveGlobalMax = shortTasks.effectiveGlobalMax,
            shortActiveCount = shortTasks.activeCount,
            longActiveCount = activeLong.size,
            totalActiveCount = totalActive,
            shortQueuedCount = shortTasks.queuedCount,
            longQueuedCount = queuedLong.size,
            totalQueuedCount = totalQueued,
            remainingCapacity = remaining,
            activeSharedWrite = shortTasks.activeSharedWrite,
            activeExclusiveMaintenance = activeExclusiveCount == 1,
            activeByLane = RuntimeLaneKind.entries.associateWith { lane ->
                (shortTasks.activeByLane[lane] ?: 0) + activeLong.count { it.spec.lane == lane }
            },
            queuedByLane = RuntimeLaneKind.entries.associateWith { lane ->
                (shortTasks.queuedByLane[lane] ?: 0) + queuedLong.count { it.spec.lane == lane }
            },
            duplicateOwnerCount = duplicateOwnerCount,
            conflictingProcessIdentityCount = conflictingProcessIdentityCount,
        )
    }
}
