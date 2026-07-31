package com.kite.app.foundation.runtime

internal sealed interface ManagedProotOwnerAdmissionResult {
    data class Granted(
        val ownerId: String,
        val generation: Long,
        val restored: Boolean,
        val existing: Boolean,
    ) : ManagedProotOwnerAdmissionResult

    data class Rejected(
        val reason: String,
    ) : ManagedProotOwnerAdmissionResult
}

internal data class ManagedProotOwnerAdmissionSnapshot(
    val activeOwnerCount: Int,
    val restoringOrAcquiringCount: Int,
    val restoredOwnerCount: Int,
)

/**
 * 同一实际 ProotJobAdmissionController 上的长期 lease 句柄表。
 * 它不保存业务状态、命令或进程身份；这些事实仍由各 owner 的 Store 持有。
 */
internal class ManagedProotOwnerAdmissionRegistry(
    private val admission: ProotJobAdmissionController,
) {
    private data class Held(
        val generation: Long,
        val restored: Boolean,
        val lease: ProotJobAdmissionLease,
    )

    private val lock = Any()
    private val inFlight = mutableSetOf<String>()
    private val held = linkedMapOf<String, Held>()

    fun acquireBlocking(
        request: ProotJobAdmissionRequest,
        generation: Long,
    ): ManagedProotOwnerAdmissionResult = acquire(
        request = request,
        generation = generation,
        restored = false,
    ) { admission.acquireBlocking(request) }

    fun restore(
        request: ProotJobAdmissionRequest,
        generation: Long,
    ): ManagedProotOwnerAdmissionResult = acquire(
        request = request,
        generation = generation,
        restored = true,
    ) { admission.restoreActive(request) }

    fun release(ownerId: String, generation: Long): Boolean {
        val normalizedOwner = ownerId.trim()
        val released = synchronized(lock) {
            val current = held[normalizedOwner] ?: return@synchronized null
            if (current.generation != generation) return@synchronized null
            held.remove(normalizedOwner)
        } ?: return false
        released.lease.close()
        return true
    }

    fun snapshot(): ManagedProotOwnerAdmissionSnapshot = synchronized(lock) {
        ManagedProotOwnerAdmissionSnapshot(
            activeOwnerCount = held.size,
            restoringOrAcquiringCount = inFlight.size,
            restoredOwnerCount = held.values.count(Held::restored),
        )
    }

    private fun acquire(
        request: ProotJobAdmissionRequest,
        generation: Long,
        restored: Boolean,
        action: () -> ProotJobAdmissionResult,
    ): ManagedProotOwnerAdmissionResult {
        validate(request, generation)
        val ownerId = request.ownerId.trim()
        synchronized(lock) {
            held[ownerId]?.let { current ->
                return if (current.generation == generation) {
                    ManagedProotOwnerAdmissionResult.Granted(
                        ownerId = ownerId,
                        generation = generation,
                        restored = current.restored,
                        existing = true,
                    )
                } else {
                    ManagedProotOwnerAdmissionResult.Rejected("managed_owner_generation_conflict")
                }
            }
            if (!inFlight.add(ownerId)) {
                return ManagedProotOwnerAdmissionResult.Rejected("managed_owner_admission_in_flight")
            }
        }

        val result = try {
            action()
        } catch (error: Throwable) {
            synchronized(lock) { inFlight.remove(ownerId) }
            throw error
        }
        return synchronized(lock) {
            inFlight.remove(ownerId)
            when (result) {
                is ProotJobAdmissionResult.Rejected ->
                    ManagedProotOwnerAdmissionResult.Rejected(result.reason)
                is ProotJobAdmissionResult.Granted -> {
                    val competing = held[ownerId]
                    if (competing != null) {
                        result.lease.close()
                        ManagedProotOwnerAdmissionResult.Rejected("managed_owner_admission_raced")
                    } else {
                        held[ownerId] = Held(generation, restored, result.lease)
                        ManagedProotOwnerAdmissionResult.Granted(
                            ownerId = ownerId,
                            generation = generation,
                            restored = restored,
                            existing = false,
                        )
                    }
                }
            }
        }
    }

    private fun validate(request: ProotJobAdmissionRequest, generation: Long) {
        require(generation > 0L) { "managed_owner_generation_invalid" }
        require(request.cancellationMode == ProotJobCancellationMode.MANAGED_OWNER) {
            "managed_owner_cancellation_mode_required"
        }
        require(
            request.resultMode == ProotJobResultMode.DETACHED_BINDING ||
                request.resultMode == ProotJobResultMode.MANAGED_CHANNEL
        ) { "managed_owner_result_mode_required" }
    }
}
