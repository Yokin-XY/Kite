package com.kite.app.agent.process

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets

class AgentProcessChannelTest {
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

    private class FakeProcess(
        stdout: String = "",
        stderr: String = ""
    ) : Process() {
        val stdin = ByteArrayOutputStream()
        private val stdoutStream = ByteArrayInputStream(stdout.toByteArray(StandardCharsets.UTF_8))
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
}
