package com.kite.app.foundation.bootstrap

import com.kite.app.shell.KiteAppGraph
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class KFApplicationLifecycleTest {
    @Test
    fun `Application 终止返回时自身和组合根任务都已完成`() {
        val application = RuntimeEnvironment.getApplication() as KFApplication
        val graph = KiteAppGraph.from(application)
        val applicationJob = application.applicationJob
        val processJob = graph.processJob

        assertTrue(applicationJob.isActive)
        assertTrue(processJob.isActive)

        application.onTerminate()

        assertTrue(applicationJob.isCompleted)
        assertTrue(processJob.isCompleted)
        assertNull(KiteAppGraph.release(application))
    }
}
