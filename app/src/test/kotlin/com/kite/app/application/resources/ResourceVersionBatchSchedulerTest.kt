package com.kite.app.application.resources

import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ResourceVersionBatchSchedulerTest {
    @Test
    fun `全部分类完成后才执行并按输入顺序汇总低基数事实`() = runTest {
        val requests = listOf(
            Request("one", ResourceVersionBatchLane.STRUCTURED_NATIVE_REMOTE),
            Request("two", ResourceVersionBatchLane.PROOT_COMPATIBILITY),
            Request("three", ResourceVersionBatchLane.STRUCTURED_NATIVE_REMOTE),
        )
        val classified = mutableListOf<String>()
        var summary: ResourceVersionBatchSummary? = null

        val results = ResourceVersionBatchScheduler.executeOrdered(
            requests = requests,
            laneOf = { request ->
                classified += request.value
                request.lane
            },
            observer = { observed -> summary = observed },
        ) { request ->
            assertEquals(requests.map(Request::value), classified)
            delay(50L)
            request.value
        }

        assertEquals(requests.map(Request::value), results)
        assertEquals(3, summary?.total)
        assertEquals(2, summary?.structuredNativeRemote)
        assertEquals(1, summary?.prootCompatibility)
        assertTrue((summary?.maxStructuredNativeRemote ?: 0) in 1..2)
        assertEquals(1, summary?.maxProotCompatibility)
    }

    @Test
    fun `分类失败不会执行部分请求`() = runTest {
        var executions = 0

        val result = runCatching {
            ResourceVersionBatchScheduler.executeOrdered(
                requests = listOf("one", "blocked", "three"),
                laneOf = { value ->
                    check(value != "blocked") { "classification_failed" }
                    ResourceVersionBatchLane.STRUCTURED_NATIVE_REMOTE
                },
                execute = { value ->
                    executions += 1
                    value
                },
            )
        }

        assertTrue(result.isFailure)
        assertEquals(0, executions)
    }

    private data class Request(
        val value: String,
        val lane: ResourceVersionBatchLane,
    )
}
