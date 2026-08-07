package com.kite.app.feature.runsurface

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentSurfaceWarmupTest {
    @Test
    fun `预热任务只调度一次且不在调用方同步执行`() {
        val pending = mutableListOf<Runnable>()
        var preloadCount = 0
        val warmup = SingleShotWarmup(pending::add)

        assertTrue(warmup.schedule { preloadCount += 1 })
        assertFalse(warmup.schedule { preloadCount += 1 })
        assertEquals(0, preloadCount)
        assertEquals(1, pending.size)

        pending.single().run()

        assertEquals(1, preloadCount)
    }
}
