package com.kite.app.foundation.service

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackgroundRuntimeStartSingleFlightTest {
    @Test
    fun `same runtime admits only one start until lease closes`() {
        val gate = BackgroundRuntimeStartSingleFlight()
        val first = gate.tryAcquire("runtime-a")

        assertNotNull(first)
        assertNull(gate.tryAcquire("runtime-a"))
        assertTrue(gate.isInFlight("runtime-a"))
        first!!.close()
        assertFalse(gate.isInFlight("runtime-a"))
        assertNotNull(gate.tryAcquire("runtime-a"))
    }

    @Test
    fun `different runtimes remain independently startable`() {
        val gate = BackgroundRuntimeStartSingleFlight()

        val first = gate.tryAcquire("runtime-a")
        val second = gate.tryAcquire("runtime-b")

        assertNotNull(first)
        assertNotNull(second)
        first!!.close()
        second!!.close()
    }

    @Test
    fun `concurrent callers produce exactly one lease`() {
        val gate = BackgroundRuntimeStartSingleFlight()
        val ready = CountDownLatch(16)
        val release = CountDownLatch(1)
        val granted = AtomicInteger(0)
        val executor = Executors.newFixedThreadPool(16)
        repeat(16) {
            executor.execute {
                ready.countDown()
                release.await(1, TimeUnit.SECONDS)
                gate.tryAcquire("runtime-a")?.let {
                    granted.incrementAndGet()
                }
            }
        }

        assertTrue(ready.await(1, TimeUnit.SECONDS))
        release.countDown()
        executor.shutdown()
        assertTrue(executor.awaitTermination(2, TimeUnit.SECONDS))
        assertTrue(gate.isInFlight("runtime-a"))
        assertTrue("only one concurrent start may enter", granted.get() == 1)
    }

    @Test
    fun `late old lease cannot clear a newer generation after forget`() {
        val gate = BackgroundRuntimeStartSingleFlight()
        val old = gate.tryAcquire("runtime-a")!!
        gate.forget("runtime-a")
        val current = gate.tryAcquire("runtime-a")!!

        old.close()

        assertTrue(gate.isInFlight("runtime-a"))
        assertNull(gate.tryAcquire("runtime-a"))
        current.close()
        assertFalse(gate.isInFlight("runtime-a"))
    }
}
