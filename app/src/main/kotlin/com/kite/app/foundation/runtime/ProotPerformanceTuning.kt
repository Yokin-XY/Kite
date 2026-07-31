package com.kite.app.foundation.runtime

/** PRoot 有界任务准入与温热池共同消费的唯一性能档参数。 */
internal data class ProotPerformanceTuning(
    val configuredGlobalMax: Int,
    val maxWarmRunners: Int,
    val idleTimeoutMs: Long,
)

internal object ProotPerformanceTunings {
    private const val PRODUCTION_MAX = 4

    fun resolve(
        profileGroup: RuntimeLifecyclePolicyProfileGroup,
        lanes: List<RuntimeLanePolicy>,
    ): ProotPerformanceTuning = when (profileGroup) {
        RuntimeLifecyclePolicyProfileGroup.LOW_POWER ->
            ProotPerformanceTuning(configuredGlobalMax = 1, maxWarmRunners = 1, idleTimeoutMs = 2_000L)

        RuntimeLifecyclePolicyProfileGroup.DEFAULT_BALANCED ->
            ProotPerformanceTuning(configuredGlobalMax = 2, maxWarmRunners = 2, idleTimeoutMs = 30_000L)

        RuntimeLifecyclePolicyProfileGroup.HIGH_PERFORMANCE ->
            ProotPerformanceTuning(configuredGlobalMax = 4, maxWarmRunners = 4, idleTimeoutMs = 120_000L)

        RuntimeLifecyclePolicyProfileGroup.CUSTOM -> {
            val configured = lanes.maxOfOrNull(RuntimeLanePolicy::maxConcurrency)
                ?.coerceIn(1, PRODUCTION_MAX)
                ?: 1
            ProotPerformanceTuning(
                configuredGlobalMax = configured,
                maxWarmRunners = configured,
                idleTimeoutMs = 30_000L,
            )
        }
    }
}
