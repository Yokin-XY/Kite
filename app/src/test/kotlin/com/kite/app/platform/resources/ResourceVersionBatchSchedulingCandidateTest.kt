package com.kite.app.platform.resources

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ResourceVersionBatchSchedulingCandidateTest {
    @Test
    fun `候选按输入顺序返回且两类槽位独立受限`() = runTest {
        val requests = listOf(
            Request("native-1", DebugResourceVersionBatchLane.STRUCTURED_NATIVE_REMOTE, "current"),
            Request("compat-1", DebugResourceVersionBatchLane.PROOT_COMPATIBILITY, "available"),
            Request("native-2", DebugResourceVersionBatchLane.STRUCTURED_NATIVE_REMOTE, "failed"),
            Request("compat-2", DebugResourceVersionBatchLane.PROOT_COMPATIBILITY, "unsupported"),
            Request("native-3", DebugResourceVersionBatchLane.STRUCTURED_NATIVE_REMOTE, "ahead"),
        )
        val structuredActive = AtomicInteger()
        val compatibilityActive = AtomicInteger()
        val structuredMax = AtomicInteger()
        val compatibilityMax = AtomicInteger()

        val outcomes = ResourceVersionBatchSchedulingCandidate.executeOrdered(
            requests = requests,
            laneOf = Request::lane,
        ) { request ->
            val active = when (request.lane) {
                DebugResourceVersionBatchLane.STRUCTURED_NATIVE_REMOTE -> structuredActive.incrementAndGet()
                DebugResourceVersionBatchLane.PROOT_COMPATIBILITY -> compatibilityActive.incrementAndGet()
            }
            when (request.lane) {
                DebugResourceVersionBatchLane.STRUCTURED_NATIVE_REMOTE -> updateMaximum(structuredMax, active)
                DebugResourceVersionBatchLane.PROOT_COMPATIBILITY -> updateMaximum(compatibilityMax, active)
            }
            try {
                delay(100L)
                request.outcome
            } finally {
                when (request.lane) {
                    DebugResourceVersionBatchLane.STRUCTURED_NATIVE_REMOTE -> structuredActive.decrementAndGet()
                    DebugResourceVersionBatchLane.PROOT_COMPATIBILITY -> compatibilityActive.decrementAndGet()
                }
            }
        }

        assertEquals(requests.map(Request::outcome), outcomes)
        assertEquals(3, structuredMax.get())
        assertEquals(1, compatibilityMax.get())
        assertEquals(0, structuredActive.get())
        assertEquals(0, compatibilityActive.get())
    }

    @Test
    fun `失败结果不抹掉其他结果`() = runTest {
        val requests = listOf("current", "failed:network", "available")

        val outcomes = ResourceVersionBatchSchedulingCandidate.executeOrdered(
            requests = requests,
            laneOf = { DebugResourceVersionBatchLane.STRUCTURED_NATIVE_REMOTE },
            execute = { outcome -> outcome },
        )

        assertEquals(requests, outcomes)
    }

    @Test
    fun `取消父任务会取消已启动和排队工作`() = runTest {
        val requests = List(5) { index -> index }
        val active = AtomicInteger()
        val started = AtomicInteger()
        val firstStarted = CompletableDeferred<Unit>()
        val job = launch {
            ResourceVersionBatchSchedulingCandidate.executeOrdered(
                requests = requests,
                laneOf = { DebugResourceVersionBatchLane.PROOT_COMPATIBILITY },
            ) {
                active.incrementAndGet()
                started.incrementAndGet()
                firstStarted.complete(Unit)
                try {
                    awaitCancellation()
                } finally {
                    active.decrementAndGet()
                }
            }
        }

        firstStarted.await()
        job.cancelAndJoin()

        assertTrue(started.get() > 0)
        assertEquals(0, active.get())
        assertTrue(started.get() < requests.size)
    }

    private data class Request(
        val id: String,
        val lane: DebugResourceVersionBatchLane,
        val outcome: String,
    )

    private fun updateMaximum(target: AtomicInteger, candidate: Int) {
        while (true) {
            val current = target.get()
            if (candidate <= current || target.compareAndSet(current, candidate)) return
        }
    }
}
