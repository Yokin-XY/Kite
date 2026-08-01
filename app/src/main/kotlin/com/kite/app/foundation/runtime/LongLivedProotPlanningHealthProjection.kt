package com.kite.app.foundation.runtime

import java.util.Locale

/** RF600 规划态固定投影；不得与正式 `proot_actual_*` 字段混用。 */
internal object LongLivedProotPlanningHealthProjection {
    private const val PREFIX = "proot_long_planned_"

    fun project(
        admission: LongLivedProotAdmissionSnapshot,
        recovery: LongLivedProotRecoveryPlan? = null,
    ): Map<String, String> = buildMap {
        put("${PREFIX}scope", "planned_not_production")
        put("${PREFIX}pressure", admission.pressure.wireName())
        put("${PREFIX}configured_global_max", admission.configuredGlobalMax.toString())
        put("${PREFIX}effective_global_max", admission.effectiveGlobalMax.toString())
        put("${PREFIX}active_count", admission.activeCount.toString())
        put("${PREFIX}queued_count", admission.queuedCount.toString())
        put("${PREFIX}active_exclusive_maintenance", admission.activeExclusiveMaintenance.toString())
        put("${PREFIX}queued_exclusive_maintenance", admission.queuedExclusiveMaintenance.toString())

        RuntimeLaneKind.entries.forEach { lane ->
            put("${PREFIX}active_lane_${lane.wireName()}_count", (admission.activeByLane[lane] ?: 0).toString())
            put("${PREFIX}queued_lane_${lane.wireName()}_count", (admission.queuedByLane[lane] ?: 0).toString())
        }

        val decisions = recovery?.decisions.orEmpty()
        put("${PREFIX}recovery_available", (recovery != null).toString())
        put("${PREFIX}recovery_decision_count", decisions.size.toString())
        put("${PREFIX}recovery_process_starts_requested", (recovery?.processStartsRequested ?: 0).toString())
        put("${PREFIX}recovery_capacity_holder_count", decisions.count { it.record.holdsCapacity }.toString())
        put(
            "${PREFIX}recovery_discarded_older_generation_count",
            decisions.sumOf { it.discardedOlderGenerations }.toString(),
        )
        put(
            "${PREFIX}recovery_collapsed_exact_duplicate_count",
            decisions.sumOf { it.collapsedExactDuplicates }.toString(),
        )

        LongLivedProotLeasePhase.entries.forEach { phase ->
            put(
                "${PREFIX}recovery_phase_${phase.wireName()}_count",
                decisions.count { it.record.phase == phase }.toString(),
            )
        }
        LongLivedProotOwnerKind.entries.forEach { kind ->
            put(
                "${PREFIX}recovery_owner_kind_${kind.wireName()}_count",
                decisions.count { it.record.spec.owner.kind == kind }.toString(),
            )
        }
        RuntimeLaneKind.entries.forEach { lane ->
            put(
                "${PREFIX}recovery_lane_${lane.wireName()}_count",
                decisions.count { it.record.spec.lane == lane }.toString(),
            )
        }
        LongLivedProotRecoveryAction.entries.forEach { action ->
            put(
                "${PREFIX}recovery_action_${action.wireName()}_count",
                decisions.count { it.action == action }.toString(),
            )
        }
        LongLivedProotProcessMatch.entries.forEach { match ->
            put(
                "${PREFIX}recovery_process_match_${match.wireName()}_count",
                decisions.count { it.processMatch == match }.toString(),
            )
        }
    }.toSortedMap()

    private fun Enum<*>.wireName(): String = name.lowercase(Locale.ROOT)
}
