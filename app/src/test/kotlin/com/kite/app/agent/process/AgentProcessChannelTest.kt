package com.kite.app.agent.process

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.InterruptedIOException
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class AgentProcessChannelTest {
    @Test
    fun processFactoryAppliesThePlannedHostWorkingDirectory() {
        val directory = Files.createTempDirectory("kite-agent-workdir").toFile()
        try {
            val builder = JavaAgentProcessFactory().buildProcessBuilder(
                AgentProcessLaunch(
                    command = listOf("agent", "acp"),
                    environment = mapOf("KITE_TEST" to "ready"),
                    workingDirectory = directory.absolutePath,
                )
            )

            assertEquals(directory.absoluteFile, builder.directory().absoluteFile)
            assertEquals("ready", builder.environment()["KITE_TEST"])
        } finally {
            directory.delete()
        }
    }

    @Test
    fun keepsStdoutAndStderrSeparateAndFramesWritesAsSingleLines() = runBlocking {
        val process = FakeProcess(
            stdout = "{\"jsonrpc\":\"2.0\"}\n{\"id\":1}\n",
            stderr = "diagnostic only\n"
        )
        val channel = JavaAgentProcessChannel(process)

        channel.writeLine("{\"method\":\"initialize\"}")

        assertEquals(
            listOf("{\"jsonrpc\":\"2.0\"}", "{\"id\":1}"),
            channel.stdoutLines.toList()
        )
        assertEquals(listOf("diagnostic only"), channel.stderrLines.toList())
        assertEquals(
            "{\"method\":\"initialize\"}${System.lineSeparator()}",
            process.stdin.toString(StandardCharsets.UTF_8.name())
        )
    }

    @Test
    fun rejectsMultiLineWritesSoProtocolFramingCannotBeCorrupted() {
        val channel = JavaAgentProcessChannel(FakeProcess())

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { channel.writeLine("first\nsecond") }
        }
    }

    @Test
    fun stopClosesTheOwnedProcess() = runBlocking {
        val process = FakeProcess()
        val channel = JavaAgentProcessChannel(process)

        assertEquals(0, channel.stop(10L))
        assertFalse(channel.isAlive)
        assertFalse(process.isAlive)
    }

    @Test
    fun awaitExitCanBeCancelledWhileTheOwnedProcessIsStillRunning() = runBlocking {
        val process = BlockingProcess()
        val channel = JavaAgentProcessChannel(process)
        val job = launch(Dispatchers.Default) { channel.awaitExit() }
        check(process.waitStarted.await(1, TimeUnit.SECONDS)) { "wait_not_started" }

        withTimeout(2_000L) { job.cancelAndJoin() }

        assertEquals(true, job.isCancelled)
        channel.close()
    }

    @Test
    fun idleProcessOutputCollectionCanBeCancelled() = runBlocking {
        val stdout = InterruptibleBlockingInputStream()
        val process = FakeProcess(stdoutStream = stdout)
        val channel = JavaAgentProcessChannel(process)
        val job = launch(Dispatchers.Default) { channel.stdoutLines.collect() }
        check(stdout.readStarted.await(1, TimeUnit.SECONDS)) { "read_not_started" }

        withTimeout(2_000L) { job.cancelAndJoin() }

        assertEquals(true, job.isCancelled)
        channel.close()
    }

    @Test
    fun processTreeStopperSignalsOwnedDescendantsBeforeRootOnly() {
        val procRoot = Files.createTempDirectory("kite-process-tree").toFile()
        try {
            writeProcessStatus(procRoot, pid = 10, parentPid = 1)
            writeProcessStatus(procRoot, pid = 11, parentPid = 10)
            writeProcessStatus(procRoot, pid = 12, parentPid = 11)
            writeProcessStatus(procRoot, pid = 13, parentPid = 1)
            val signalled = mutableListOf<Int>()
            val stopper = ProcOwnedProcessTreeStopper(
                procRoot = procRoot,
                currentPid = { 99 },
                sendSignal = { pid, _ ->
                    signalled += pid
                    File(procRoot, pid.toString()).deleteRecursively()
                },
                sleep = {},
            )

            assertTrue(stopper.stop(rootPid = 10, gracefulWaitMs = 0L, killWaitMs = 0L))
            assertEquals(listOf(12, 11, 10), signalled)
            assertTrue(File(procRoot, "13").exists())
        } finally {
            procRoot.deleteRecursively()
        }
    }

    @Test
    fun processTreeStopperUsesUniqueEnvironmentOwnerAcrossProotDescendants() {
        val procRoot = Files.createTempDirectory("kite-process-owner").toFile()
        try {
            writeProcessEnvironment(procRoot, pid = 20, ownerId = "login-1")
            writeProcessEnvironment(procRoot, pid = 21, ownerId = "login-1")
            writeProcessEnvironment(procRoot, pid = 22, ownerId = "agent-session")
            val signalled = mutableListOf<Int>()
            val stopper = ProcOwnedProcessTreeStopper(
                procRoot = procRoot,
                currentPid = { 99 },
                sendSignal = { pid, _ ->
                    signalled += pid
                    File(procRoot, pid.toString()).deleteRecursively()
                },
                sleep = {},
            )

            assertTrue(stopper.stopOwner("login-1", gracefulWaitMs = 0L, killWaitMs = 0L))
            assertEquals(setOf(20, 21), signalled.toSet())
            assertTrue(File(procRoot, "22").exists())
        } finally {
            procRoot.deleteRecursively()
        }
    }

    private class FakeProcess(
        stdout: String = "",
        stderr: String = "",
        stdoutStream: InputStream? = null,
    ) : Process() {
        val stdin = ByteArrayOutputStream()
        private val stdoutStream = stdoutStream
            ?: ByteArrayInputStream(stdout.toByteArray(StandardCharsets.UTF_8))
        private val stderrStream = ByteArrayInputStream(stderr.toByteArray(StandardCharsets.UTF_8))
        private var alive = true

        override fun getOutputStream(): OutputStream = stdin
        override fun getInputStream(): InputStream = stdoutStream
        override fun getErrorStream(): InputStream = stderrStream
        override fun waitFor(): Int {
            alive = false
            return 0
        }
        override fun exitValue(): Int {
            check(!alive) { "still_running" }
            return 0
        }
        override fun destroy() {
            alive = false
        }
        override fun isAlive(): Boolean = alive
        override fun destroyForcibly(): Process {
            alive = false
            return this
        }
        override fun waitFor(timeout: Long, unit: java.util.concurrent.TimeUnit): Boolean {
            alive = false
            return true
        }
    }

    private class BlockingProcess : Process() {
        val waitStarted = CountDownLatch(1)
        private val release = CountDownLatch(1)
        private val stdin = ByteArrayOutputStream()
        private val stdout = ByteArrayInputStream(ByteArray(0))
        private val stderr = ByteArrayInputStream(ByteArray(0))
        @Volatile private var alive = true

        override fun getOutputStream(): OutputStream = stdin
        override fun getInputStream(): InputStream = stdout
        override fun getErrorStream(): InputStream = stderr
        override fun waitFor(): Int {
            waitStarted.countDown()
            release.await()
            return 130
        }
        override fun exitValue(): Int {
            check(!alive) { "still_running" }
            return 130
        }
        override fun destroy() {
            alive = false
            release.countDown()
        }
        override fun isAlive(): Boolean = alive
        override fun destroyForcibly(): Process = apply { destroy() }
        override fun waitFor(timeout: Long, unit: TimeUnit): Boolean = release.await(timeout, unit)
    }

    private class InterruptibleBlockingInputStream : InputStream() {
        val readStarted = CountDownLatch(1)

        override fun read(): Int {
            readStarted.countDown()
            return try {
                CountDownLatch(1).await()
                -1
            } catch (error: InterruptedException) {
                Thread.currentThread().interrupt()
                throw InterruptedIOException("read_cancelled").apply { initCause(error) }
            }
        }
    }

    private fun writeProcessStatus(procRoot: File, pid: Int, parentPid: Int) {
        val directory = File(procRoot, pid.toString()).apply { mkdirs() }
        File(directory, "status").writeText("Name:\ttest-$pid\nPPid:\t$parentPid\n")
    }

    private fun writeProcessEnvironment(procRoot: File, pid: Int, ownerId: String) {
        val directory = File(procRoot, pid.toString()).apply { mkdirs() }
        File(directory, "environ").writeBytes(
            "PATH=/usr/bin\u0000$PROCESS_OWNER_ENV=$ownerId\u0000".toByteArray(),
        )
    }
}
