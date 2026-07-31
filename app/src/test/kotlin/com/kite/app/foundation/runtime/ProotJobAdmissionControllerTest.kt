package com.kite.app.foundation.runtime

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ProotJobAdmissionControllerTest {
    @Test
    fun `job contract requires an explicit owner`() {
        val controller = controller(profile = RuntimeLifecyclePolicyProfileGroup.DEFAULT_BALANCED)

        val error = assertThrows(IllegalArgumentException::class.java) {
            controller.acquireBlocking(request("missing-owner", RuntimeLaneKind.PROBE).copy(ownerId = ""))
        }

        assertEquals("admission_owner_id_invalid", error.message)
        controller.close()
    }

    @Test
    fun `profiles enforce one two and four active jobs`() {
        val expected = mapOf(
            RuntimeLifecyclePolicyProfileGroup.LOW_POWER to 1,
            RuntimeLifecyclePolicyProfileGroup.DEFAULT_BALANCED to 2,
            RuntimeLifecyclePolicyProfileGroup.HIGH_PERFORMANCE to 4,
        )

        expected.forEach { (profile, max) ->
            val controller = controller(profile = profile)
            val leases = (0 until max).map { index ->
                (controller.acquireBlocking(request("job-$index", RuntimeLaneKind.INTERACTIVE)) as
                    ProotJobAdmissionResult.Granted).lease
            }
            assertEquals(max, controller.snapshot().activeCount)
            assertEquals(max, controller.snapshot().effectiveGlobalMax)
            leases.forEach(AutoCloseable::close)
            controller.close()
        }
    }

    @Test
    fun `lane limit remains stricter than high performance global limit`() {
        val controller = ProotJobAdmissionController(
            ProotJobAdmissionPolicy(
                profileGroup = RuntimeLifecyclePolicyProfileGroup.HIGH_PERFORMANCE,
                pressure = RuntimePressureLevel.NORMAL,
            )
        )
        val first = granted(controller.acquireBlocking(request("first", RuntimeLaneKind.INTERACTIVE))).lease
        val second = granted(controller.acquireBlocking(request("second", RuntimeLaneKind.INTERACTIVE))).lease

        val rejected = controller.acquireBlocking(
            request("third", RuntimeLaneKind.INTERACTIVE, waitTimeoutMs = 5L)
        ) as ProotJobAdmissionResult.Rejected

        assertEquals("admission_lane_capacity_timeout", rejected.reason)
        first.close()
        second.close()
        controller.close()
    }

    @Test
    fun `calibration override is bounded to eight`() {
        val lanes = RuntimeWorkloadPolicy.defaultLanes().map {
            if (it.lane == RuntimeLaneKind.INTERACTIVE) it.copy(maxConcurrency = 32) else it
        }
        val controller = ProotJobAdmissionController(
            ProotJobAdmissionPolicy(
                profileGroup = RuntimeLifecyclePolicyProfileGroup.HIGH_PERFORMANCE,
                lanes = lanes,
                pressure = RuntimePressureLevel.NORMAL,
                globalMaxOverride = 99,
            )
        )

        assertEquals(8, controller.snapshot().effectiveGlobalMax)
        controller.close()
    }

    @Test
    fun `interactive job overtakes queued batch after current lease releases`() {
        val controller = controller(profile = RuntimeLifecyclePolicyProfileGroup.LOW_POWER)
        val first = granted(controller.acquireBlocking(request("first", RuntimeLaneKind.INTERACTIVE))).lease
        val order = mutableListOf<String>()
        val pool = Executors.newFixedThreadPool(2)
        val started = CountDownLatch(2)
        val done = CountDownLatch(2)
        pool.execute {
            started.countDown()
            granted(controller.acquireBlocking(request("batch", RuntimeLaneKind.BUILD))).lease.use {
                synchronized(order) { order += "batch" }
            }
            done.countDown()
        }
        pool.execute {
            started.countDown()
            granted(controller.acquireBlocking(request("interactive", RuntimeLaneKind.INTERACTIVE))).lease.use {
                synchronized(order) { order += "interactive" }
            }
            done.countDown()
        }

        assertTrue(started.await(1, TimeUnit.SECONDS))
        waitUntil { controller.snapshot().queuedCount == 2 }
        first.close()
        assertTrue(done.await(2, TimeUnit.SECONDS))
        assertEquals(listOf("interactive", "batch"), order)
        pool.shutdownNow()
        controller.close()
    }

    @Test
    fun `saturated lane does not block another lane with free capacity`() {
        val lanes = RuntimeWorkloadPolicy.defaultLanes().map { lane ->
            when (lane.lane) {
                RuntimeLaneKind.INTERACTIVE -> lane.copy(maxConcurrency = 1)
                RuntimeLaneKind.SERVICE -> lane.copy(maxConcurrency = 3)
                else -> lane
            }
        }
        val controller = ProotJobAdmissionController(
            ProotJobAdmissionPolicy(
                profileGroup = RuntimeLifecyclePolicyProfileGroup.HIGH_PERFORMANCE,
                lanes = lanes,
                pressure = RuntimePressureLevel.NORMAL,
            )
        )
        val activeInteractive = granted(
            controller.acquireBlocking(request("interactive-active", RuntimeLaneKind.INTERACTIVE))
        ).lease
        val releaseQueued = CountDownLatch(1)
        val serviceEntered = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)
        executor.execute {
            granted(
                controller.acquireBlocking(
                    request("interactive-queued", RuntimeLaneKind.INTERACTIVE)
                )
            ).lease.use { releaseQueued.await(2, TimeUnit.SECONDS) }
        }
        waitUntil { controller.snapshot().queuedCount == 1 }
        executor.execute {
            granted(controller.acquireBlocking(request("service", RuntimeLaneKind.SERVICE))).lease.use {
                serviceEntered.countDown()
            }
        }

        assertTrue(serviceEntered.await(1, TimeUnit.SECONDS))
        assertEquals(1, controller.snapshot().queuedCount)
        activeInteractive.close()
        releaseQueued.countDown()
        executor.shutdown()
        assertTrue(executor.awaitTermination(2, TimeUnit.SECONDS))
        controller.close()
    }

    @Test
    fun `shared write waits for readers and excludes following work`() {
        val controller = controller(profile = RuntimeLifecyclePolicyProfileGroup.HIGH_PERFORMANCE)
        val reader = granted(controller.acquireBlocking(request("reader", RuntimeLaneKind.SERVICE))).lease
        val writeEntered = CountDownLatch(1)
        val releaseWrite = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)
        executor.execute {
            granted(
                controller.acquireBlocking(
                    request("writer", RuntimeLaneKind.BUILD, ProotJobAccess.SHARED_WRITE)
                )
            ).lease.use {
                writeEntered.countDown()
                releaseWrite.await(2, TimeUnit.SECONDS)
            }
        }
        waitUntil { controller.snapshot().queuedCount == 1 }
        val followingEntered = CountDownLatch(1)
        executor.execute {
            granted(controller.acquireBlocking(request("following", RuntimeLaneKind.SERVICE))).lease.use {
                followingEntered.countDown()
            }
        }

        reader.close()
        assertTrue(writeEntered.await(1, TimeUnit.SECONDS))
        assertTrue(controller.snapshot().activeSharedWrite)
        assertEquals(1L, followingEntered.count)
        releaseWrite.countDown()
        assertTrue(followingEntered.await(1, TimeUnit.SECONDS))
        executor.shutdownNow()
        controller.close()
    }

    @Test
    fun `high pressure defers low priority but permits pressure essential maintenance`() {
        val controller = controller(
            profile = RuntimeLifecyclePolicyProfileGroup.HIGH_PERFORMANCE,
            pressure = RuntimePressureLevel.HIGH,
        )

        val rejected = controller.acquireBlocking(
            request("batch", RuntimeLaneKind.BUILD, waitTimeoutMs = 5L)
        ) as ProotJobAdmissionResult.Rejected
        assertEquals("admission_pressure_high", rejected.reason)
        granted(
            controller.acquireBlocking(
                request("essential", RuntimeLaneKind.PROBE, pressureEssential = true)
            )
        ).lease.close()
        assertEquals(1, controller.snapshot().effectiveGlobalMax)
        controller.close()
    }

    @Test
    fun `timed out waiter is removed without consuming capacity`() {
        val controller = controller(profile = RuntimeLifecyclePolicyProfileGroup.LOW_POWER)
        val active = granted(controller.acquireBlocking(request("active", RuntimeLaneKind.INTERACTIVE))).lease

        val rejected = controller.acquireBlocking(
            request("timeout", RuntimeLaneKind.SERVICE, waitTimeoutMs = 5L)
        ) as ProotJobAdmissionResult.Rejected

        assertEquals("admission_global_capacity_timeout", rejected.reason)
        assertEquals(0, controller.snapshot().queuedCount)
        assertEquals(1L, controller.snapshot().timedOutCount)
        active.close()
        controller.close()
    }

    private fun controller(
        profile: RuntimeLifecyclePolicyProfileGroup,
        pressure: RuntimePressureLevel = RuntimePressureLevel.NORMAL,
    ) = ProotJobAdmissionController(
        ProotJobAdmissionPolicy(
            profileGroup = profile,
            pressure = pressure,
            lanes = RuntimeWorkloadPolicy.defaultLanes().map { lane ->
                if (lane.lane == RuntimeLaneKind.INTERACTIVE) {
                    lane.copy(maxConcurrency = 8, backgroundMaxConcurrency = 8)
                } else {
                    lane
                }
            },
        )
    )

    private fun request(
        id: String,
        lane: RuntimeLaneKind,
        access: ProotJobAccess = ProotJobAccess.READ_ONLY,
        pressureEssential: Boolean = false,
        waitTimeoutMs: Long = 1_000L,
    ) = ProotJobAdmissionRequest(
        jobId = id,
        ownerId = "test:$id",
        lane = lane,
        access = access,
        cancellationMode = ProotJobCancellationMode.TIMEOUT_AND_OWNER,
        resultMode = ProotJobResultMode.CAPTURED_STDIO,
        pressureEssential = pressureEssential,
        waitTimeoutMs = waitTimeoutMs,
    )

    private fun granted(result: ProotJobAdmissionResult) = result as ProotJobAdmissionResult.Granted

    private fun waitUntil(condition: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2)
        while (!condition()) {
            check(System.nanoTime() < deadline) { "condition timed out" }
            Thread.yield()
        }
    }
}
