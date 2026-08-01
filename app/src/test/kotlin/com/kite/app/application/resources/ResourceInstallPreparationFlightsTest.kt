package com.kite.app.application.resources

import com.kite.app.resources.KiteResourcePlanSnapshot
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
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
        assertFalse(
            flights.cancel(
                current.environmentId,
                current.targetResourceId,
                instanceId = "other-wizard",
                generation = current.generation,
            )
        )
        assertFalse(flights.cancel(current.environmentId, current.targetResourceId, generation = 6L))
        assertTrue(flights.isCurrent(current))

        assertTrue(
            flights.cancel(
                current.environmentId,
                current.targetResourceId,
                instanceId = current.instanceId,
                generation = current.generation,
            )
        )
        assertFalse(flights.isCurrent(current))
        assertFalse(flights.commitIfCurrent(current) { error("旧代次不应写回") })
        runCurrent()
    }

    @Test
    fun `同一环境的计划生命周期操作严格串行`() = runTest {
        val gate = ResourcePlanLifecycleGate()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()

        val first = async {
            gate.withEnvironment("profile") {
                events += "first-enter"
                entered.complete(Unit)
                release.await()
                events += "first-exit"
            }
        }
        entered.await()
        val second = async {
            gate.withEnvironment("profile") { events += "second-enter" }
        }
        runCurrent()

        assertFalse(second.isCompleted)
        release.complete(Unit)
        first.await()
        second.await()
        assertEquals(listOf("first-enter", "first-exit", "second-enter"), events)
    }

    @Test
    fun `旧计划代次不能认领同目标的新计划`() {
        val old = KiteResourcePlanSnapshot(
            targetResourceId = "target",
            status = "active",
            generation = 11L,
        )
        val replacement = old.copy(generation = 12L)

        assertTrue(ResourcePlanCancellationPolicy.owns(old, "target", 11L, "target"))
        assertFalse(ResourcePlanCancellationPolicy.owns(replacement, "target", 11L, "target"))
        assertFalse(ResourcePlanCancellationPolicy.owns(old, "other", 11L, "target"))
    }

    private fun token(target: String, generation: Long) = ResourceInstallPreparationToken(
        environmentId = "default",
        targetResourceId = target,
        instanceId = "wizard-$target",
        generation = generation,
    )
}
