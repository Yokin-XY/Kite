package com.kite.app.foundation.runtime

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeLaunchTraceTest {
    @Test
    fun `同一实例使用单调时钟记录分段耗时`() {
        val now = AtomicLong(100L)
        val store = RuntimeLaunchTraceStore(elapsedRealtimeMs = now::get)

        store.mark("run-a", "action")
        now.set(145L)
        store.mark("run-a", "prep")
        now.set(180L)
        store.mark("run-a", "process")

        val events = store.snapshot("run-a")!!.events
        assertEquals(listOf("action", "prep", "process"), events.map { it.stage })
        assertEquals(listOf(0L, 45L, 80L), events.map { it.sinceStartMs })
        assertEquals(listOf(0L, 45L, 35L), events.map { it.sincePreviousMs })
    }

    @Test
    fun `缺少显式 begin 时首个阶段仍可建立追踪`() {
        val store = RuntimeLaunchTraceStore(elapsedRealtimeMs = { 23L })

        store.mark("run-a", RuntimeLaunchTrace.RUNTIME_PREP_READY)

        val event = store.snapshot("run-a")!!.events.single()
        assertEquals(RuntimeLaunchTrace.RUNTIME_PREP_READY, event.stage)
        assertEquals(0L, event.sinceStartMs)
    }

    @Test
    fun `同一稳定实例的新动作会重置旧分段和终端绑定`() {
        val now = AtomicLong(10L)
        val store = RuntimeLaunchTraceStore(elapsedRealtimeMs = now::get)

        store.begin("resource-run", RuntimeLaunchTrace.ACTION_RECEIVED)
        store.bindTerminal("resource-run", "old-terminal")
        now.set(50L)
        store.begin("resource-run", RuntimeLaunchTrace.ACTION_RECEIVED)

        val events = store.snapshot("resource-run")!!.events
        assertEquals(listOf(RuntimeLaunchTrace.ACTION_RECEIVED), events.map { it.stage })
        assertEquals(0L, events.single().sinceStartMs)
        assertNull(store.markTerminal("old-terminal", RuntimeLaunchTrace.TERMINAL_FIRST_OUTPUT))
    }

    @Test
    fun `并发实例互不串线`() {
        val now = AtomicLong(0L)
        val store = RuntimeLaunchTraceStore(elapsedRealtimeMs = now::incrementAndGet)
        val ready = CountDownLatch(8)
        val start = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(8)

        repeat(8) { index ->
            executor.execute {
                ready.countDown()
                start.await()
                repeat(4) { stage -> store.mark("run-$index", "stage-$stage") }
            }
        }

        assertTrue(ready.await(2, TimeUnit.SECONDS))
        start.countDown()
        executor.shutdown()
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS))
        assertEquals(8, store.runCount())
        repeat(8) { index ->
            assertEquals(4, store.snapshot("run-$index")!!.events.size)
        }
    }

    @Test
    fun `容量上限淘汰旧实例并限制单实例事件`() {
        val store = RuntimeLaunchTraceStore(maxRuns = 2, maxEventsPerRun = 2, elapsedRealtimeMs = { 1L })

        store.mark("run-a", "one")
        store.mark("run-a", "two")
        store.mark("run-a", "three")
        store.mark("run-b", "one")
        store.mark("run-c", "one")

        assertNull(store.snapshot("run-a"))
        assertEquals(2, store.runCount())
        assertEquals(1, store.snapshot("run-b")!!.events.size)
        assertEquals(1, store.snapshot("run-c")!!.events.size)
    }

    @Test
    fun `终端绑定后首字节阶段只记录一次`() {
        val store = RuntimeLaunchTraceStore(elapsedRealtimeMs = { 10L })

        assertTrue(store.bindTerminal("run-a", "terminal-a"))
        store.markTerminal("terminal-a", RuntimeLaunchTrace.TERMINAL_FIRST_OUTPUT, firstOnly = true)
        store.markTerminal("terminal-a", RuntimeLaunchTrace.TERMINAL_FIRST_OUTPUT, firstOnly = true)

        val stages = store.snapshot("run-a")!!.events.map { it.stage }
        assertEquals(
            listOf(RuntimeLaunchTrace.TERMINAL_BOUND, RuntimeLaunchTrace.TERMINAL_FIRST_OUTPUT),
            stages,
        )
        assertNull(store.markTerminal("missing", RuntimeLaunchTrace.TERMINAL_FIRST_OUTPUT))
    }

    @Test
    fun `空身份和空阶段不会创建诊断状态`() {
        val store = RuntimeLaunchTraceStore(elapsedRealtimeMs = { 0L })

        assertNull(store.mark(" ", "stage"))
        assertNull(store.mark("run", " "))
        assertFalse(store.bindTerminal(" ", "terminal"))
        assertEquals(0, store.runCount())
    }
}
