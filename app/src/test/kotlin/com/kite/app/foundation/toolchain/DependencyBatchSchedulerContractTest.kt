package com.kite.app.foundation.toolchain

import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DependencyBatchSchedulerContractTest {
    @Test
    fun `returns input order while respecting dependency and two task limit`() {
        val active = AtomicInteger(0)
        val maximumActive = AtomicInteger(0)
        val completed = Collections.synchronizedSet(mutableSetOf<String>())
        val dependentSawPrerequisite = AtomicInteger(0)
        val tasks = listOf(
            task("first", 80L, active, maximumActive, completed),
            task("second", 15L, active, maximumActive, completed),
            task("dependent", 5L, active, maximumActive, completed, setOf("first")) {
                if ("first" in completed) dependentSawPrerequisite.incrementAndGet()
            },
            task("fourth", 10L, active, maximumActive, completed),
        )

        val decision = DependencyBatchScheduler.executeOrdered(tasks, 2, String::isNotBlank)
        val report = (decision as DependencyBatchDecision.Completed).report

        assertEquals(listOf("first", "second", "dependent", "fourth"), report.outcomes.map { it.key })
        assertTrue(report.outcomes.all { it is DependencyBatchTaskOutcome.Executed && it.successful })
        assertEquals(2, report.maximumActiveTasks)
        assertEquals(2, maximumActive.get())
        assertEquals(1, dependentSawPrerequisite.get())
    }

    @Test
    fun `failure blocks only its dependents and independent work completes`() {
        val executed = Collections.synchronizedList(mutableListOf<String>())
        val tasks = listOf(
            DependencyBatchTask("failed") { executed += "failed"; "" },
            DependencyBatchTask("blocked", setOf("failed")) { executed += "blocked"; "unexpected" },
            DependencyBatchTask("independent") { executed += "independent"; "ok" },
            DependencyBatchTask("after-independent", setOf("independent")) {
                executed += "after-independent"
                "ok"
            },
        )

        val decision = DependencyBatchScheduler.executeOrdered(tasks, 2, String::isNotBlank)
        val report = (decision as DependencyBatchDecision.Completed).report

        assertEquals(listOf("failed", "blocked", "independent", "after-independent"), report.outcomes.map { it.key })
        assertFalse("blocked" in executed)
        assertTrue("independent" in executed)
        assertTrue("after-independent" in executed)
        val blocked = report.outcomes[1] as DependencyBatchTaskOutcome.DependencyBlocked
        assertEquals(listOf("failed"), blocked.failedDependencies)
    }

    @Test
    fun `invalid graph blocks before any task side effect`() {
        val starts = AtomicInteger(0)
        val invalidGraphs = listOf(
            listOf(
                DependencyBatchTask("same") { starts.incrementAndGet(); "ok" },
                DependencyBatchTask("same") { starts.incrementAndGet(); "ok" },
            ),
            listOf(
                DependencyBatchTask("missing", setOf("unknown")) { starts.incrementAndGet(); "ok" },
            ),
            listOf(
                DependencyBatchTask("left", setOf("right")) { starts.incrementAndGet(); "ok" },
                DependencyBatchTask("right", setOf("left")) { starts.incrementAndGet(); "ok" },
            ),
        )

        val reasons = invalidGraphs.map { graph ->
            val decision = DependencyBatchScheduler.executeOrdered(graph, 2, String::isNotBlank)
            (decision as DependencyBatchDecision.Blocked).reason
        }

        assertEquals(
            listOf(
                "dependency_batch_duplicate_key",
                "dependency_batch_missing_dependency",
                "dependency_batch_cycle",
            ),
            reasons,
        )
        assertEquals(0, starts.get())
    }

    @Test
    fun `task exception is contained and does not cancel unrelated work`() {
        val independentCompleted = CountDownLatch(1)
        val tasks = listOf(
            DependencyBatchTask<String, String>("throws") { error("fixed_failure") },
            DependencyBatchTask("independent") {
                independentCompleted.countDown()
                "ok"
            },
        )

        val decision = DependencyBatchScheduler.executeOrdered(tasks, 2, String::isNotBlank)
        val report = (decision as DependencyBatchDecision.Completed).report
        val failure = report.outcomes.first() as DependencyBatchTaskOutcome.Executed

        assertFalse(failure.successful)
        assertEquals("fixed_failure", failure.failureReason)
        assertTrue(independentCompleted.await(1L, TimeUnit.SECONDS))
        assertTrue((report.outcomes.last() as DependencyBatchTaskOutcome.Executed).successful)
    }

    private fun task(
        key: String,
        delayMs: Long,
        active: AtomicInteger,
        maximumActive: AtomicInteger,
        completed: MutableSet<String>,
        dependencies: Set<String> = emptySet(),
        onStarted: () -> Unit = {},
    ): DependencyBatchTask<String, String> = DependencyBatchTask(key, dependencies) {
        val nowActive = active.incrementAndGet()
        maximumActive.updateAndGet { current -> maxOf(current, nowActive) }
        onStarted()
        try {
            Thread.sleep(delayMs)
            completed += key
            key
        } finally {
            active.decrementAndGet()
        }
    }
}
