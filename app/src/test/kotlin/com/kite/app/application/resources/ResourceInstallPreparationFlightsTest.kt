package com.kite.app.application.resources

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResourceInstallPreparationFlightsTest {
    @Test
    fun `同一环境只运行一份准备任务且不同代次不能覆盖`() = runTest {
        val flights = ResourceInstallPreparationFlights(this)
        val release = CompletableDeferred<Unit>()
        val first = token(target = "first", generation = 1L)
        val duplicate = first.copy(generation = 2L)

        assertTrue(flights.launch(first) { release.await() })
        runCurrent()
        assertTrue(flights.isCurrent(first))
        assertFalse(flights.launch(duplicate) { })
        assertFalse(flights.isCurrent(duplicate))

        var committed = false
        assertTrue(flights.commitIfCurrent(first) { committed = true })
        assertTrue(committed)

        release.complete(Unit)
        runCurrent()
        assertFalse(flights.isCurrent(first))
        assertTrue(flights.launch(duplicate) { })
        runCurrent()
    }

    @Test
    fun `取消先撤销当前代次令迟到结果失去写回资格`() = runTest {
        val flights = ResourceInstallPreparationFlights(this)
        val started = CompletableDeferred<Unit>()
        val never = CompletableDeferred<Unit>()
        val current = token(target = "target", generation = 7L)

        assertTrue(
            flights.launch(current) {
                started.complete(Unit)
                never.await()
            }
        )
        runCurrent()
        assertTrue(started.isCompleted)
        assertFalse(flights.cancel(current.environmentId, current.targetResourceId, generation = 6L))
        assertTrue(flights.isCurrent(current))

        assertTrue(flights.cancel(current.environmentId, current.targetResourceId, current.generation))
        assertFalse(flights.isCurrent(current))
        assertFalse(flights.commitIfCurrent(current) { error("旧代次不应写回") })
        runCurrent()
    }

    private fun token(target: String, generation: Long) = ResourceInstallPreparationToken(
        environmentId = "default",
        targetResourceId = target,
        instanceId = "wizard-$target",
        generation = generation,
    )
}
