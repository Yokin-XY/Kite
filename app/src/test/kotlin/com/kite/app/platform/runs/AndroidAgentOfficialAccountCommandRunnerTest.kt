package com.kite.app.platform.runs

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.kite.app.agent.process.AgentProcessChannel
import com.kite.app.agent.process.AgentProcessLaunch
import com.kite.app.agent.registration.AgentOfficialAccountCommand
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class AndroidAgentOfficialAccountCommandRunnerTest {
    @Test
    fun `官方登录复用统一启动规划并打开CLI输出的授权地址`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val launched = AtomicReference<AgentProcessLaunch>()
        val openedUrls = mutableListOf<String>()
        val runner = AndroidAgentOfficialAccountCommandRunner(
            context = context,
            processFactory = { launch ->
                launched.set(launch)
                CompletedProcess(
                    stdoutLines = flowOf(
                        "Open this URL to continue:",
                        "https://auth.example.test/authorize?code=abc.",
                    ),
                    exitCode = 0,
                )
            },
            launchPlanner = { argv, workingDirectory, environment, guarantees, evidence ->
                assertEquals(listOf("codex", "login"), argv)
                assertEquals("/workspace", workingDirectory)
                assertEquals(emptyMap<String, String>(), environment)
                assertEquals(emptySet<String>(), guarantees)
                assertEquals(emptyMap<String, String>(), evidence)
                ManagedAgentProcessLaunch(
                    process = AgentProcessLaunch(
                        command = listOf("planned") + argv,
                        workingDirectory = workingDirectory,
                    ),
                    runtimeLane = "host_node",
                    fallbackReason = "",
                )
            },
            openExternal = { url -> openedUrls.add(url) },
        )

        val result = runner.run(
            AgentOfficialAccountCommand(
                argv = listOf("codex", "login"),
                timeoutMs = 5_000,
            ),
        )

        assertEquals(0, result.exitCode)
        assertEquals(listOf("planned", "codex", "login"), launched.get().command)
        assertEquals(listOf("https://auth.example.test/authorize?code=abc"), openedUrls)
        assertFalse(result.output.contains("access_token", ignoreCase = true))
    }

    @Test
    fun `取消官方登录会停止唯一进程并关闭通道`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val stopped = AtomicInteger(0)
        val stopGraceMs = AtomicLong(-1L)
        val closed = AtomicBoolean(false)
        val runner = AndroidAgentOfficialAccountCommandRunner(
            context = context,
            processFactory = {
                SuspendingProcess(stopped = stopped, stopGraceMs = stopGraceMs, closed = closed)
            },
            launchPlanner = { argv, workingDirectory, _, _, _ ->
                ManagedAgentProcessLaunch(
                    process = AgentProcessLaunch(
                        command = argv,
                        workingDirectory = workingDirectory,
                    ),
                    runtimeLane = "host_node",
                    fallbackReason = "",
                )
            },
            openExternal = { true },
        )

        val job = launch {
            runner.run(AgentOfficialAccountCommand(listOf("kimi", "login"), timeoutMs = 30_000))
        }
        runCurrent()
        job.cancelAndJoin()

        assertEquals(1, stopped.get())
        assertEquals(500L, stopGraceMs.get())
        assertEquals(true, closed.get())
    }

    private class CompletedProcess(
        override val stdoutLines: Flow<String>,
        private val exitCode: Int,
    ) : AgentProcessChannel {
        override val stderrLines: Flow<String> = emptyFlow()
        override val pid: Long? = 1L
        override val isAlive: Boolean = false

        override suspend fun writeLine(line: String) = Unit
        override suspend fun awaitExit(): Int = exitCode
        override suspend fun stop(gracePeriodMs: Long): Int = exitCode
        override fun close() = Unit
    }

    private class SuspendingProcess(
        private val stopped: AtomicInteger,
        private val stopGraceMs: AtomicLong,
        private val closed: AtomicBoolean,
    ) : AgentProcessChannel {
        private val exit = CompletableDeferred<Int>()
        override val stdoutLines: Flow<String> = emptyFlow()
        override val stderrLines: Flow<String> = emptyFlow()
        override val pid: Long? = 2L
        override val isAlive: Boolean = true

        override suspend fun writeLine(line: String) = Unit
        override suspend fun awaitExit(): Int = withContext(NonCancellable) { exit.await() }
        override suspend fun stop(gracePeriodMs: Long): Int {
            stopped.incrementAndGet()
            stopGraceMs.set(gracePeriodMs)
            exit.complete(130)
            return 130
        }
        override fun close() {
            closed.set(true)
        }
    }
}
