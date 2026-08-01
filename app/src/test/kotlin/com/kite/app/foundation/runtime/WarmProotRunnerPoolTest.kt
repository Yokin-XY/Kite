package com.kite.app.foundation.runtime

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WarmProotRunnerPoolTest {
    @Test
    fun `execution timing separates runner wait from business job`() {
        var nowNanos = 0L
        val pool = pool(
            identityProvider = {
                nowNanos = TimeUnit.MILLISECONDS.toNanos(10L)
                identity("timed")
            },
            sessionFactory = {
                FakeSession { request ->
                    nowNanos = TimeUnit.MILLISECONDS.toNanos(35L)
                    success(request.jobId)
                }
            },
            monotonicNanos = { nowNanos },
        )

        val execution = pool.executeBlocking(admission("timed"), job("timed"))

        assertEquals(10L, execution.queueWaitMs)
        assertEquals(25L, execution.executeMs)
        assertEquals(35L, execution.totalMs)
        pool.close()
    }

    @Test
    fun `sequential jobs reuse one matching warm session`() {
        val factoryCount = AtomicInteger(0)
        val pool = pool(sessionFactory = {
            factoryCount.incrementAndGet()
            FakeSession { request -> success(request.jobId) }
        })

        assertTrue(pool.executeBlocking(admission("one"), job("one")).succeeded)
        assertTrue(pool.executeBlocking(admission("two"), job("two")).succeeded)

        assertEquals(1, factoryCount.get())
        assertEquals(1, pool.sessionCount())
        pool.close()
    }

    @Test
    fun `runtime identity change retires previous idle session`() {
        var identity = identity("one")
        val sessions = mutableListOf<FakeSession>()
        val pool = pool(
            identityProvider = { identity },
            sessionFactory = { FakeSession { request -> success(request.jobId) }.also(sessions::add) },
        )
        assertTrue(pool.executeBlocking(admission("one"), job("one")).succeeded)

        identity = identity("two")
        assertTrue(pool.executeBlocking(admission("two"), job("two")).succeeded)

        assertEquals(2, sessions.size)
        assertTrue(sessions.first().closed)
        assertFalse(sessions.last().closed)
        pool.close()
    }

    @Test
    fun `expired idle session is observable and retired before later work`() {
        var nowNanos = 0L
        val sessions = mutableListOf<FakeSession>()
        val pool = pool(
            sessionFactory = { FakeSession { request -> success(request.jobId) }.also(sessions::add) },
            tuningProvider = { WarmProotRunnerPoolTuning(maxWarmRunners = 2, idleTimeoutMs = 1L) },
            monotonicNanos = { nowNanos },
        )
        assertTrue(pool.executeBlocking(admission("first"), job("first")).succeeded)

        nowNanos = TimeUnit.MILLISECONDS.toNanos(2L)
        assertEquals(2L, pool.snapshot().oldestIdleAgeMs)
        assertTrue(pool.executeBlocking(admission("second"), job("second")).succeeded)

        assertEquals(2, sessions.size)
        assertTrue(sessions.first().closed)
        assertFalse(sessions.last().closed)
        assertEquals(1, pool.sessionCount())
        pool.close()
    }

    @Test
    fun `prestart failure uses fallback under the same admission lease`() {
        var fallbackCount = 0
        val pool = pool(sessionFactory = {
            FakeSession { request ->
                WarmProotJobExecution(
                    jobId = request.jobId,
                    started = false,
                    failureKind = WarmProotRunnerFailureKind.START_FAILED,
                    failureReason = "runner_start_failed",
                )
            }
        })

        val execution = pool.executeBlocking(
            admission("fallback"),
            job("fallback"),
            independentFallback = {
                fallbackCount += 1
                success("fallback")
            },
        )

        assertEquals(WarmProotExecutionRoute.INDEPENDENT_FALLBACK, execution.route)
        assertTrue(execution.succeeded)
        assertEquals(1, fallbackCount)
        pool.close()
    }

    @Test
    fun `failure after started is never replayed through fallback`() {
        var fallbackCount = 0
        val pool = pool(sessionFactory = {
            FakeSession { request ->
                WarmProotJobExecution(
                    jobId = request.jobId,
                    started = true,
                    runnerPid = 10,
                    rootPid = 11,
                    failureKind = WarmProotRunnerFailureKind.RUNNER_CRASHED,
                    failureReason = "runner_stdout_eof",
                )
            }
        })

        val execution = pool.executeBlocking(
            admission("started"),
            job("started"),
            independentFallback = {
                fallbackCount += 1
                success("started")
            },
        )

        assertEquals(WarmProotExecutionRoute.RUNNER_FAILED_AFTER_START, execution.route)
        assertFalse(execution.succeeded)
        assertEquals(0, fallbackCount)
        pool.close()
    }

    @Test
    fun `two admitted jobs use at most two warm sessions`() {
        val entered = CountDownLatch(2)
        val release = CountDownLatch(1)
        val factoryCount = AtomicInteger(0)
        val pool = pool(sessionFactory = {
            factoryCount.incrementAndGet()
            FakeSession { request ->
                entered.countDown()
                release.await(2, TimeUnit.SECONDS)
                success(request.jobId)
            }
        })
        val executor = Executors.newFixedThreadPool(2)
        val done = CountDownLatch(2)
        repeat(2) { index ->
            executor.execute {
                pool.executeBlocking(admission("parallel-$index"), job("parallel-$index"))
                done.countDown()
            }
        }

        assertTrue(entered.await(1, TimeUnit.SECONDS))
        assertEquals(2, pool.sessionCount())
        release.countDown()
        assertTrue(done.await(2, TimeUnit.SECONDS))
        assertEquals(2, factoryCount.get())
        executor.shutdownNow()
        pool.close()
    }

    @Test
    fun `invalidation retires a session that finishes creation afterward`() {
        val factoryEntered = CountDownLatch(1)
        val releaseFactory = CountDownLatch(1)
        val created = mutableListOf<FakeSession>()
        val pool = pool(sessionFactory = {
            factoryEntered.countDown()
            releaseFactory.await(2, TimeUnit.SECONDS)
            FakeSession { request -> success(request.jobId) }.also(created::add)
        })
        val executor = Executors.newSingleThreadExecutor()
        val completed = CountDownLatch(1)
        var route: WarmProotExecutionRoute? = null
        executor.execute {
            route = pool.executeBlocking(
                admission("generation"),
                job("generation"),
                independentFallback = { success("generation") },
            ).route
            completed.countDown()
        }

        assertTrue(factoryEntered.await(1, TimeUnit.SECONDS))
        pool.invalidate("container_reset")
        releaseFactory.countDown()
        assertTrue(completed.await(2, TimeUnit.SECONDS))
        assertEquals(WarmProotExecutionRoute.INDEPENDENT_FALLBACK, route)
        assertEquals(0, pool.sessionCount())
        assertTrue(created.single().closed)
        executor.shutdownNow()
        pool.close()
    }

    @Test
    fun `trimming active pool retires only excess sessions`() {
        val entered = CountDownLatch(2)
        val release = CountDownLatch(1)
        val pool = pool(sessionFactory = {
            FakeSession { request ->
                entered.countDown()
                release.await(2, TimeUnit.SECONDS)
                success(request.jobId)
            }
        })
        val executor = Executors.newFixedThreadPool(2)
        val done = CountDownLatch(2)
        repeat(2) { index ->
            executor.execute {
                pool.executeBlocking(admission("trim-$index"), job("trim-$index"))
                done.countDown()
            }
        }

        assertTrue(entered.await(1, TimeUnit.SECONDS))
        pool.trimTo(1)
        assertEquals(2, pool.snapshot().activeSessions)
        assertEquals(1, pool.snapshot().staleSessions)
        release.countDown()
        assertTrue(done.await(2, TimeUnit.SECONDS))
        assertEquals(1, pool.sessionCount())
        assertEquals(1, pool.snapshot().idleSessions)
        assertEquals(0, pool.snapshot().staleSessions)
        executor.shutdownNow()
        pool.close()
    }

    private fun pool(
        identityProvider: () -> WarmProotRunnerIdentity? = { identity("default") },
        sessionFactory: () -> WarmProotJobSession,
        tuningProvider: () -> WarmProotRunnerPoolTuning = {
            WarmProotRunnerPoolTuning(maxWarmRunners = 2, idleTimeoutMs = 60_000L)
        },
        monotonicNanos: () -> Long = System::nanoTime,
    ): WarmProotRunnerPool {
        val lanes = RuntimeWorkloadPolicy.defaultLanes().map { lane ->
            if (lane.lane == RuntimeLaneKind.INTERACTIVE) {
                lane.copy(maxConcurrency = 8, backgroundMaxConcurrency = 8)
            } else {
                lane
            }
        }
        return WarmProotRunnerPool(
            admission = ProotJobAdmissionController(
                ProotJobAdmissionPolicy(
                    profileGroup = RuntimeLifecyclePolicyProfileGroup.HIGH_PERFORMANCE,
                    lanes = lanes,
                    pressure = RuntimePressureLevel.NORMAL,
                )
            ),
            identityProvider = identityProvider,
            sessionFactory = sessionFactory,
            tuningProvider = tuningProvider,
            monotonicNanos = monotonicNanos,
        )
    }

    private fun admission(id: String) = ProotJobAdmissionRequest(
        jobId = id,
        ownerId = "test:$id",
        lane = RuntimeLaneKind.INTERACTIVE,
        cancellationMode = ProotJobCancellationMode.TIMEOUT_AND_OWNER,
        resultMode = ProotJobResultMode.CAPTURED_STDIO,
        waitTimeoutMs = 1_000L,
    )

    private fun job(id: String) = WarmProotJobRequest(
        jobId = id,
        argv = listOf("/bin/true"),
    )

    private fun identity(token: String) = WarmProotRunnerIdentity(
        runtime = RuntimeLaunchPreparationIdentity(
            runtimeRootPath = "/runtime/$token",
            runtimeDescriptorStamp = token.hashCode().toLong(),
            containerId = "container-$token",
            containerCreatedAtMs = 1L,
            rootfsPath = "/rootfs/$token",
            workspacePath = "/workspace/$token",
            networkMode = "HOST",
        ),
        runner = ManagedCommandHostFileStamp(
            command = "kf-runner",
            hostPath = "/runner/$token",
            canonicalPath = "/runner/$token",
            linkChain = emptyList(),
            lastModifiedMs = 1L,
            length = 100L,
            executable = true,
        ),
    )

    private fun success(jobId: String) = WarmProotJobExecution(
        jobId = jobId,
        started = true,
        runnerPid = 10,
        rootPid = 11,
        exitCode = 0,
        termSignal = 0,
    )

    private class FakeSession(
        private val execute: (WarmProotJobRequest) -> WarmProotJobExecution,
    ) : WarmProotJobSession {
        @Volatile
        var closed = false
            private set

        override fun executeBlocking(
            request: WarmProotJobRequest,
            onOutput: (WarmProotOutputStream, ByteArray) -> Unit,
        ): WarmProotJobExecution = execute(request)

        override fun isWarm(): Boolean = !closed

        override fun close() {
            closed = true
        }
    }
}
