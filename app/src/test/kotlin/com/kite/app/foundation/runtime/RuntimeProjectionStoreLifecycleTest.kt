package com.kite.app.foundation.runtime

import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import com.kite.app.foundation.contracts.ManagedTerminalKind
import com.kite.app.foundation.contracts.ManagedTerminalRecord
import com.kite.app.foundation.terminal.TerminalRuntimeRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RuntimeProjectionStoreLifecycleTest {

    private lateinit var baseContext: Context

    @Before
    fun setUp() {
        runBlocking {
            baseContext = ApplicationProvider.getApplicationContext()
            releaseAll(baseContext).forEach { it.join() }
            TerminalRuntimeRegistry.replaceAll(emptyList(), baseContext.cacheDir, null)
        }
    }

    @After
    fun tearDown() {
        runBlocking {
            releaseAll(baseContext).forEach { it.join() }
            TerminalRuntimeRegistry.replaceAll(emptyList(), baseContext.cacheDir, null)
        }
    }

    @Test
    fun `同一 Application 幂等启动且新 Application 可替换四层生命周期`() {
        runBlocking {
            val oldParentJob = SupervisorJob()
            val oldParentScope = CoroutineScope(oldParentJob + Dispatchers.Default)
            val oldOwner = ApplicationIdentityContext(baseContext)

            val oldJobs = startAll(oldOwner, oldParentScope)
            val repeatedJobs = startAll(oldOwner, oldParentScope)
            oldJobs.zip(repeatedJobs).forEach { (first, repeated) -> assertSame(first, repeated) }

            val newParentJob = SupervisorJob()
            val newParentScope = CoroutineScope(newParentJob + Dispatchers.Default)
            val newOwner = ApplicationIdentityContext(baseContext)
            val newJobs = startAll(newOwner, newParentScope)
            oldJobs.forEach { joinJob(it) }

            assertNull(RuntimeOverviewStore.release(oldOwner))
            assertNull(ProotTelemetryStore.release(oldOwner))
            assertNull(RuntimeHealthStore.release(oldOwner))
            assertNull(TaskManagerStore.release(oldOwner))
            newJobs.forEach { assertTrue(it.isActive) }

            val releasedNewJobs = listOfNotNull(
                RuntimeOverviewStore.release(newOwner),
                ProotTelemetryStore.release(newOwner),
                RuntimeHealthStore.release(newOwner),
                TaskManagerStore.release(newOwner),
            )
            releasedNewJobs.forEach { joinJob(it) }
            newJobs.zip(releasedNewJobs).forEach { (started, released) -> assertSame(started, released) }
            newJobs.forEach { assertTrue(it.isCompleted) }

            oldParentJob.cancel()
            newParentJob.cancel()
        }
    }

    @Test
    fun `四层生命周期根任务随 Application 父任务取消`() {
        runBlocking {
            val parentJob = SupervisorJob()
            val owner = ApplicationIdentityContext(baseContext)
            val jobs = startAll(
                owner,
                CoroutineScope(parentJob + Dispatchers.Default),
            )

            parentJob.cancel()
            jobs.forEach { joinJob(it) }

            jobs.forEach { assertTrue(it.isCompleted) }
            val releasedJobs = listOfNotNull(
                RuntimeOverviewStore.release(owner),
                ProotTelemetryStore.release(owner),
                RuntimeHealthStore.release(owner),
                TaskManagerStore.release(owner),
            )
            jobs.zip(releasedJobs).forEach { (started, released) -> assertSame(started, released) }
        }
    }

    @Test
    fun `Overview release 后旧 collector 不再响应且新 Application 能重新订阅`() {
        runBlocking {
            val oldParentJob = SupervisorJob()
            val oldOwner = ApplicationIdentityContext(baseContext)
            val oldJob = RuntimeOverviewStore.start(
                oldOwner,
                CoroutineScope(oldParentJob + Dispatchers.Default),
            )

            val sessionId = "lifecycle-old-session"
            val spaceId = RuntimeOverviewStore.snapshot.value.spaceId ?: "lifecycle-space"
            TerminalRuntimeRegistry.replaceAll(
                records = listOf(
                    ManagedTerminalRecord(
                        id = sessionId,
                        spaceId = spaceId,
                        title = "old collector",
                        kind = ManagedTerminalKind.SHELL,
                        createdAt = 1L,
                    )
                ),
                transcriptDir = baseContext.cacheDir,
                currentViewedSessionId = null,
            )
            awaitCondition {
                RuntimeOverviewStore.snapshot.value.terminalSessions.any { it.sessionId == sessionId }
            }

            val releasedOldJob = RuntimeOverviewStore.release(oldOwner)
            assertSame(oldJob, releasedOldJob)
            joinJob(requireNotNull(releasedOldJob))
            TerminalRuntimeRegistry.replaceAll(emptyList(), baseContext.cacheDir, null)
            assertTrue(RuntimeOverviewStore.snapshot.value.terminalSessions.any { it.sessionId == sessionId })

            val newParentJob = SupervisorJob()
            val newOwner = ApplicationIdentityContext(baseContext)
            val newJob = RuntimeOverviewStore.start(
                newOwner,
                CoroutineScope(newParentJob + Dispatchers.Default),
            )
            awaitCondition {
                RuntimeOverviewStore.snapshot.value.terminalSessions.none { it.sessionId == sessionId }
            }

            assertFalse(RuntimeOverviewStore.snapshot.value.terminalSessions.any { it.sessionId == sessionId })
            assertNull(RuntimeOverviewStore.release(oldOwner))
            assertTrue(newJob.isActive)

            val releasedNewJob = RuntimeOverviewStore.release(newOwner)
            joinJob(requireNotNull(releasedNewJob))
            oldParentJob.cancel()
            newParentJob.cancel()
        }
    }

    @Test
    fun `Health 旧 Application 的迟到 attach 不会覆盖新 owner`() {
        runBlocking {
            val oldParentJob = SupervisorJob()
            val oldOwner = BlockingApplicationIdentityContext(baseContext)
            RuntimeHealthStore.start(oldOwner, CoroutineScope(oldParentJob + Dispatchers.Default))
            oldOwner.armSecondApplicationContextRead()

            val executor = Executors.newSingleThreadExecutor()
            try {
                val oldAttach = executor.submit { RuntimeHealthStore.attachContext(oldOwner) }
                assertTrue(oldOwner.awaitBlockedRead())

                val newParentJob = SupervisorJob()
                val newOwner = ApplicationIdentityContext(baseContext)
                val newJob = RuntimeHealthStore.start(
                    newOwner,
                    CoroutineScope(newParentJob + Dispatchers.Default),
                )
                oldOwner.releaseBlockedRead()
                oldAttach.get(5, TimeUnit.SECONDS)

                assertNull(runtimeHealthAttachedContext())
                assertNull(RuntimeHealthStore.release(oldOwner))
                assertTrue(newJob.isActive)

                val releasedNewJob = RuntimeHealthStore.release(newOwner)
                joinJob(requireNotNull(releasedNewJob))
                newParentJob.cancel()
            } finally {
                oldOwner.releaseBlockedRead()
                executor.shutdownNow()
                oldParentJob.cancel()
            }
        }
    }

    @Test
    fun `Telemetry release 等待 reader 屏障且返回后旧刷新停止`() {
        runBlocking {
            val parentJob = SupervisorJob()
            val owner = ApplicationIdentityContext(baseContext)
            val rootJob = ProotTelemetryStore.start(
                owner,
                CoroutineScope(parentJob + Dispatchers.Default),
            )
            val readerLock = prootTelemetryReaderLock()
            val readerEntered = CountDownLatch(1)
            val releaseReader = CountDownLatch(1)
            val executor = Executors.newFixedThreadPool(2)
            try {
                val readerHolder = executor.submit {
                    synchronized(readerLock) {
                        readerEntered.countDown()
                        releaseReader.await(5, TimeUnit.SECONDS)
                    }
                }
                assertTrue(readerEntered.await(5, TimeUnit.SECONDS))

                ProotTelemetryStore.startAutoRefresh(owner)
                ProotTelemetryStore.refresh(owner)
                val releaseFuture = executor.submit<Job?> { ProotTelemetryStore.release(owner) }
                delay(50L)
                assertFalse(releaseFuture.isDone)

                releaseReader.countDown()
                readerHolder.get(5, TimeUnit.SECONDS)
                val releasedJob = releaseFuture.get(5, TimeUnit.SECONDS)
                assertSame(rootJob, releasedJob)
                joinJob(requireNotNull(releasedJob))

                val snapshotAfterRelease = ProotTelemetryStore.snapshot.value
                delay(100L)
                assertEquals(snapshotAfterRelease, ProotTelemetryStore.snapshot.value)
                assertNull(ProotTelemetryStore.release(owner))
            } finally {
                releaseReader.countDown()
                executor.shutdownNow()
                parentJob.cancel()
            }
        }
    }

    @Test
    fun `Health release 等待 collector 提交共用的执行屏障`() {
        runBlocking {
            val parentJob = SupervisorJob()
            val owner = ApplicationIdentityContext(baseContext)
            val rootJob = RuntimeHealthStore.start(
                owner,
                CoroutineScope(parentJob + Dispatchers.Default),
            )
            RuntimeHealthStore.attachContext(owner)
            val readyReason = "health-release-ready-${System.nanoTime()}"
            RuntimeHealthStore.markReconciliation(readyReason)
            awaitCondition { RuntimeHealthStore.snapshot.value.reconciliationReason == readyReason }

            val executionLock = runtimeHealthLifecycleExecutionLock()
            val blockedReason = "health-release-blocked-${System.nanoTime()}"
            val lockHeld = CountDownLatch(1)
            val releaseLock = CountDownLatch(1)
            val releaseStarted = CountDownLatch(1)
            val executor = Executors.newFixedThreadPool(2)
            try {
                val lockHolder = executor.submit {
                    synchronized(executionLock) {
                        lockHeld.countDown()
                        releaseLock.await(5, TimeUnit.SECONDS)
                    }
                }
                assertTrue(lockHeld.await(5, TimeUnit.SECONDS))
                RuntimeHealthStore.markReconciliation(blockedReason)

                val releaseFuture = executor.submit<Job?> {
                    releaseStarted.countDown()
                    RuntimeHealthStore.release(owner)
                }
                assertTrue(releaseStarted.await(5, TimeUnit.SECONDS))
                delay(50L)
                assertFalse(releaseFuture.isDone)

                releaseLock.countDown()
                lockHolder.get(5, TimeUnit.SECONDS)
                val releasedJob = releaseFuture.get(5, TimeUnit.SECONDS)
                assertSame(rootJob, releasedJob)
                joinJob(requireNotNull(releasedJob))

                val snapshotAfterRelease = RuntimeHealthStore.snapshot.value
                RuntimeHealthStore.markReconciliation("health-after-release-${System.nanoTime()}")
                delay(100L)
                assertEquals(snapshotAfterRelease, RuntimeHealthStore.snapshot.value)
            } finally {
                releaseLock.countDown()
                RuntimeHealthStore.release(owner)?.let { joinJob(it) }
                executor.shutdownNow()
                parentJob.cancel()
            }
        }
    }

    @Test
    fun `TaskManager 旧 owner 的 settled 回调不能修改新生命周期`() {
        runBlocking {
            val oldParentJob = SupervisorJob()
            val oldOwner = ApplicationIdentityContext(baseContext)
            val oldJob = TaskManagerStore.start(
                oldOwner,
                CoroutineScope(oldParentJob + Dispatchers.Default),
            )
            val newParentJob = SupervisorJob()
            val newOwner = ApplicationIdentityContext(baseContext)
            val newJob = TaskManagerStore.start(
                newOwner,
                CoroutineScope(newParentJob + Dispatchers.Default),
            )
            try {
                joinJob(oldJob)

                val nextEvent = async(start = CoroutineStart.UNDISPATCHED) {
                    withTimeout(5_000L) { TaskManagerStore.confirmedStoppedOwnerEvents.first() }
                }
                try {
                    TaskManagerStore.confirmOwnersStopped(
                        ownerIds = listOf("terminal:stale-owner"),
                        owner = oldOwner,
                        rootJob = oldJob,
                    )
                    delay(50L)
                    assertFalse(nextEvent.isCompleted)

                    TaskManagerStore.confirmOwnersStopped(
                        ownerIds = listOf("terminal:current-owner"),
                        owner = newOwner,
                        rootJob = newJob,
                    )
                    assertEquals(setOf("terminal:current-owner"), nextEvent.await())
                } finally {
                    nextEvent.cancel()
                }
            } finally {
                TaskManagerStore.release(newOwner)?.let { joinJob(it) }
                oldParentJob.cancel()
                newParentJob.cancel()
            }
        }
    }

    private suspend fun joinJob(job: Job) {
        withTimeout(5_000L) { job.join() }
    }

    private suspend fun awaitCondition(condition: () -> Boolean) {
        withTimeout(5_000L) {
            while (!condition()) {
                delay(10L)
            }
        }
    }

    private fun startAll(context: Context, parentScope: CoroutineScope): List<Job> = listOf(
        RuntimeOverviewStore.start(context, parentScope),
        ProotTelemetryStore.start(context, parentScope),
        RuntimeHealthStore.start(context, parentScope),
        TaskManagerStore.start(context, parentScope),
    )

    private fun releaseAll(context: Context): List<Job> = listOfNotNull(
        TaskManagerStore.release(context),
        RuntimeHealthStore.release(context),
        ProotTelemetryStore.release(context),
        RuntimeOverviewStore.release(context),
    )

    private fun prootTelemetryReaderLock(): Any {
        return requireNotNull(
            ProotTelemetryStore::class.java
                .getDeclaredField("readerLock")
                .apply { isAccessible = true }
                .get(ProotTelemetryStore)
        )
    }

    private fun runtimeHealthAttachedContext(): Context? {
        return RuntimeHealthStore::class.java
            .getDeclaredField("applicationContext")
            .apply { isAccessible = true }
            .get(RuntimeHealthStore) as Context?
    }

    private fun runtimeHealthLifecycleExecutionLock(): Any {
        return requireNotNull(
            RuntimeHealthStore::class.java
                .getDeclaredField("lifecycleExecutionLock")
                .apply { isAccessible = true }
                .get(RuntimeHealthStore)
        )
    }

    private class ApplicationIdentityContext(base: Context) : ContextWrapper(base) {
        override fun getApplicationContext(): Context = this
    }

    private class BlockingApplicationIdentityContext(base: Context) : ContextWrapper(base) {
        private val armed = AtomicBoolean(false)
        private val applicationContextReads = AtomicInteger(0)
        private val blockedReadEntered = CountDownLatch(1)
        private val blockedReadRelease = CountDownLatch(1)

        override fun getApplicationContext(): Context {
            if (armed.get() && applicationContextReads.incrementAndGet() == 2) {
                blockedReadEntered.countDown()
                blockedReadRelease.await(5, TimeUnit.SECONDS)
            }
            return this
        }

        fun armSecondApplicationContextRead() {
            applicationContextReads.set(0)
            armed.set(true)
        }

        fun awaitBlockedRead(): Boolean = blockedReadEntered.await(5, TimeUnit.SECONDS)

        fun releaseBlockedRead() {
            blockedReadRelease.countDown()
        }
    }
}
