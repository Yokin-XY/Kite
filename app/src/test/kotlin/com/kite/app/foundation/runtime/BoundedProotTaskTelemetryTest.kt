package com.kite.app.foundation.runtime

import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BoundedProotTaskTelemetryTest {
    @Test
    fun `routes results and latency buckets remain distinguishable`() {
        val collector = BoundedProotTaskTelemetryCollector()
        collector.record(
            RuntimeLaneKind.PROBE,
            execution(WarmProotExecutionRoute.WARM_RUNNER, success(), 7L, 40L, 47L),
        )
        collector.record(
            RuntimeLaneKind.SERVICE,
            execution(WarmProotExecutionRoute.INDEPENDENT_FALLBACK, failedExit(), 60L, 700L, 760L),
        )
        collector.record(
            RuntimeLaneKind.BUILD,
            execution(WarmProotExecutionRoute.ADMISSION_REJECTED, null, 6_000L, 0L, 6_000L),
        )
        collector.record(
            RuntimeLaneKind.SERVICE,
            execution(WarmProotExecutionRoute.RUNNER_FAILED_AFTER_START, startedFailure(), 15L, 90L, 105L),
        )
        collector.record(
            RuntimeLaneKind.PROBE,
            execution(WarmProotExecutionRoute.FALLBACK_FAILED, null, 20L, 5L, 25L),
        )

        val snapshot = collector.snapshot()
        assertEquals(5L, snapshot.sampleCount)
        assertEquals(5, snapshot.entries.size)
        assertEquals(
            setOf(
                WarmProotExecutionRoute.WARM_RUNNER,
                WarmProotExecutionRoute.INDEPENDENT_FALLBACK,
                WarmProotExecutionRoute.ADMISSION_REJECTED,
                WarmProotExecutionRoute.RUNNER_FAILED_AFTER_START,
                WarmProotExecutionRoute.FALLBACK_FAILED,
            ),
            snapshot.entries.map { it.key.route }.toSet(),
        )
        assertTrue(snapshot.entries.any { it.key.result == BoundedProotTaskResultCategory.SUCCEEDED })
        assertTrue(snapshot.entries.any { it.key.result == BoundedProotTaskResultCategory.EXIT_NON_ZERO })
        assertTrue(snapshot.entries.any { it.key.result == BoundedProotTaskResultCategory.ADMISSION_REJECTED })
        assertTrue(snapshot.entries.any { it.key.result == BoundedProotTaskResultCategory.EXECUTION_FAILED })
        assertTrue(snapshot.entries.any { it.key.result == BoundedProotTaskResultCategory.FALLBACK_FAILED })
        val rejected = snapshot.entries.single { it.key.route == WarmProotExecutionRoute.ADMISSION_REJECTED }
        assertEquals(1L, rejected.queue.buckets[BoundedProotLatencyBucket.GT_5000_MS])
        assertEquals(1L, rejected.execute.buckets[BoundedProotLatencyBucket.LE_10_MS])
    }

    @Test
    fun `concurrent recording loses no completed samples`() {
        val collector = BoundedProotTaskTelemetryCollector()
        val executor = Executors.newFixedThreadPool(8)
        repeat(4_000) {
            executor.execute {
                collector.record(
                    RuntimeLaneKind.PROBE,
                    execution(WarmProotExecutionRoute.WARM_RUNNER, success(), 2L, 8L, 10L),
                )
            }
        }
        executor.shutdown()
        assertTrue(executor.awaitTermination(10L, TimeUnit.SECONDS))

        val snapshot = collector.snapshot()
        assertEquals(4_000L, snapshot.sampleCount)
        assertEquals(4_000L, snapshot.entries.single().count)
        assertEquals(4_000L, snapshot.entries.single().total.buckets[BoundedProotLatencyBucket.LE_10_MS])
    }

    @Test
    fun `runtime health projection contains only low cardinality facts`() {
        val collector = BoundedProotTaskTelemetryCollector()
        collector.record(
            RuntimeLaneKind.PROBE,
            execution(WarmProotExecutionRoute.WARM_RUNNER, success(), 3L, 12L, 15L),
        )

        val text = collector.snapshot().toRuntimeHealthEnvText()
        assertTrue(text.contains("proot_bounded_telemetry_sample_count=1"))
        assertTrue(text.contains("_lane=PROBE"))
        assertTrue(text.contains("_route=WARM_RUNNER"))
        assertTrue(text.contains("_result=SUCCEEDED"))
        listOf("argv", "cwd", "owner", "environment", "stdout", "stderr").forEach { forbidden ->
            assertFalse(text.contains(forbidden, ignoreCase = true))
        }
        assertFalse(BoundedProotTaskTelemetry::class.java.declaredMethods.any { it.name.contains("reset") })
    }

    @Test
    fun `timeouts and cancellation keep terminal result categories`() {
        val timedOut = execution(
            WarmProotExecutionRoute.WARM_RUNNER,
            WarmProotJobExecution(jobId = "timed", started = true, timedOut = true),
            1L,
            1_000L,
            1_001L,
        )
        val cancelled = execution(
            WarmProotExecutionRoute.WARM_RUNNER,
            WarmProotJobExecution(jobId = "cancelled", started = true, cancelled = true),
            1L,
            10L,
            11L,
        )

        assertEquals(BoundedProotTaskResultCategory.TIMED_OUT, timedOut.resultCategory())
        assertEquals(BoundedProotTaskResultCategory.CANCELLED, cancelled.resultCategory())
    }

    private fun execution(
        route: WarmProotExecutionRoute,
        result: WarmProotJobExecution?,
        queueMs: Long,
        executeMs: Long,
        totalMs: Long,
    ) = WarmProotPoolExecution(
        route = route,
        execution = result,
        reason = route.name.lowercase(),
        queueWaitMs = queueMs,
        executeMs = executeMs,
        totalMs = totalMs,
    )

    private fun success() = WarmProotJobExecution(
        jobId = "success",
        started = true,
        exitCode = 0,
        termSignal = 0,
    )

    private fun failedExit() = WarmProotJobExecution(
        jobId = "failed-exit",
        started = true,
        exitCode = 3,
        termSignal = 0,
    )

    private fun startedFailure() = WarmProotJobExecution(
        jobId = "started-failure",
        started = true,
        failureKind = WarmProotRunnerFailureKind.RUNNER_CRASHED,
        failureReason = "runner_crashed",
    )
}
