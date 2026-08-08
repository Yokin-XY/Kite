package com.kite.app.foundation.bootstrap

import com.kite.app.foundation.runtime.ProotTelemetryStore
import com.kite.app.foundation.runtime.RuntimeHealthStore
import com.kite.app.foundation.runtime.RuntimeOverviewStore
import com.kite.app.foundation.runtime.TaskManagerStore
import com.kite.app.shell.KiteAppGraph
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
        val applicationScope = CoroutineScope(applicationJob + Dispatchers.Default)
        val overviewJob = RuntimeOverviewStore.start(application, applicationScope)
        val telemetryJob = ProotTelemetryStore.start(application, applicationScope)
        val healthJob = RuntimeHealthStore.start(application, applicationScope)
        val taskManagerJob = TaskManagerStore.start(application, applicationScope)

        assertTrue(applicationJob.isActive)
        assertTrue(processJob.isActive)
        assertTrue(overviewJob.isActive)
        assertTrue(telemetryJob.isActive)
        assertTrue(healthJob.isActive)
        assertTrue(taskManagerJob.isActive)

        application.onTerminate()

        assertTrue(applicationJob.isCompleted)
        assertTrue(processJob.isCompleted)
        assertTrue(overviewJob.isCompleted)
        assertTrue(telemetryJob.isCompleted)
        assertTrue(healthJob.isCompleted)
        assertTrue(taskManagerJob.isCompleted)
        assertNull(KiteAppGraph.release(application))
        assertNull(RuntimeOverviewStore.release(application))
        assertNull(ProotTelemetryStore.release(application))
        assertNull(RuntimeHealthStore.release(application))
        assertNull(TaskManagerStore.release(application))
    }
}
