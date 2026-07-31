package com.kite.app.foundation.runtime

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WarmProotRunnerControllerTest {
    @Test
    fun `连续任务复用 runner 且输出与进程身份逐 job 隔离`() {
        val starts = AtomicInteger(0)
        val process = ScriptedProcess { input, output ->
            output.writeReady(42)
            repeat(2) { index ->
                val request = input.readRequest()
                assertEquals(1, request.type)
                val jobId = request.jobId()
                output.writeStarted(jobId, 100 + index)
                output.writeOutput(102, jobId, "stdout-$jobId".toByteArray())
                output.writeOutput(103, jobId, "stderr-$jobId".toByteArray())
                output.writeExited(jobId, 0)
            }
            assertEquals(3, input.readRequest().type)
        }
        val controller = WarmProotRunnerController({ starts.incrementAndGet(); process })

        val first = controller.executeBlocking(request("first"))
        val second = controller.executeBlocking(request("second"))
        controller.close()

        assertTrue(first.succeeded)
        assertTrue(second.succeeded)
        assertEquals(42L, first.runnerPid)
        assertEquals(42L, second.runnerPid)
        assertEquals(100L, first.rootPid)
        assertEquals(101L, second.rootPid)
        assertArrayEquals("stdout-first".toByteArray(), first.stdoutTail)
        assertArrayEquals("stderr-second".toByteArray(), second.stderrTail)
        assertEquals(1, starts.get())
    }

    @Test
    fun `输出尾部有界并记录丢弃字节`() {
        val process = ScriptedProcess { input, output ->
            output.writeReady(42)
            val jobId = input.readRequest().jobId()
            output.writeStarted(jobId, 100)
            output.writeOutput(102, jobId, "01234".toByteArray())
            output.writeOutput(102, jobId, "56789".toByteArray())
            output.writeExited(jobId, 0)
            input.readRequest()
        }
        val controller = WarmProotRunnerController(processFactory = { process })

        val result = controller.executeBlocking(request("bounded", maxOutputBytes = 5))
        controller.close()

        assertArrayEquals("56789".toByteArray(), result.stdoutTail)
        assertEquals(5L, result.stdoutDroppedBytes)
    }

    @Test
    fun `外部取消只发送当前 job 并等待 runner 明确结束`() {
        val started = CountDownLatch(1)
        val process = ScriptedProcess { input, output ->
            output.writeReady(42)
            val jobId = input.readRequest().jobId()
            output.writeStarted(jobId, 100)
            started.countDown()
            val cancel = input.readRequest()
            assertEquals(2, cancel.type)
            assertEquals(jobId, cancel.jobId())
            output.writeExited(jobId, -1, signal = 15, flags = 1)
            input.readRequest()
        }
        val controller = WarmProotRunnerController(processFactory = { process })
        val executor = Executors.newSingleThreadExecutor()
        val future = executor.submit<WarmProotJobExecution> {
            controller.executeBlocking(request("cancel-me", timeoutMs = 30_000L))
        }

        assertTrue(started.await(2, TimeUnit.SECONDS))
        assertTrue(controller.cancel("cancel-me"))
        assertFalse(controller.cancel("other"))
        val result = future.get(3, TimeUnit.SECONDS)
        controller.close()
        executor.shutdownNow()

        assertTrue(result.started)
        assertTrue(result.cancelled)
        assertFalse(result.fallbackAllowed)
    }

    @Test
    fun `Started 后 runner EOF 明确失败且禁止自动回退重放`() {
        val process = ScriptedProcess { input, output ->
            output.writeReady(42)
            val jobId = input.readRequest().jobId()
            output.writeStarted(jobId, 100)
        }
        val controller = WarmProotRunnerController(processFactory = { process })

        val result = controller.executeBlocking(request("crash"))

        assertEquals(WarmProotRunnerFailureKind.RUNNER_CRASHED, result.failureKind)
        assertTrue(result.started)
        assertFalse(result.fallbackAllowed)
        assertFalse(controller.isWarm())
    }

    @Test
    fun `runner 超时协议失灵时 Android watchdog 取消后强制回收`() {
        val cancelReceived = CountDownLatch(1)
        val process = ScriptedProcess { input, output ->
            output.writeReady(42)
            val jobId = input.readRequest().jobId()
            output.writeStarted(jobId, 100)
            assertEquals(2, input.readRequest().type)
            cancelReceived.countDown()
            Thread.sleep(500L)
        }
        val controller = WarmProotRunnerController(
            processFactory = { process },
            watchdogGraceMs = 40L,
            cancelGraceMs = 40L,
        )

        val result = controller.executeBlocking(request("watchdog", timeoutMs = 40L))

        assertTrue(cancelReceived.await(1, TimeUnit.SECONDS))
        assertEquals(WarmProotRunnerFailureKind.WATCHDOG_TIMEOUT, result.failureKind)
        assertTrue(result.started)
        assertFalse(result.fallbackAllowed)
        assertTrue(process.destroyed)
    }

    @Test
    fun `READY 前失败允许调用方回退独立 PRoot 并回收进程`() {
        val process = ScriptedProcess { _, _ -> }
        val controller = WarmProotRunnerController({ process }, startupTimeoutMs = 300L)

        val result = controller.executeBlocking(request("not-started"))

        assertEquals(WarmProotRunnerFailureKind.START_FAILED, result.failureKind)
        assertFalse(result.started)
        assertTrue(result.fallbackAllowed)
        assertTrue(process.destroyed)
    }

    @Test
    fun `并发调用在单 runner 前串行且不触发 busy`() {
        val firstReceived = CountDownLatch(1)
        val releaseFirst = CountDownLatch(1)
        val requestCount = AtomicInteger(0)
        val process = ScriptedProcess { input, output ->
            output.writeReady(42)
            val first = input.readRequest().jobId()
            requestCount.incrementAndGet()
            output.writeStarted(first, 100)
            firstReceived.countDown()
            releaseFirst.await(2, TimeUnit.SECONDS)
            output.writeExited(first, 0)
            val second = input.readRequest().jobId()
            requestCount.incrementAndGet()
            output.writeStarted(second, 101)
            output.writeExited(second, 0)
            input.readRequest()
        }
        val controller = WarmProotRunnerController(processFactory = { process })
        val executor = Executors.newFixedThreadPool(2)
        val first = executor.submit<WarmProotJobExecution> { controller.executeBlocking(request("first")) }
        assertTrue(firstReceived.await(2, TimeUnit.SECONDS))
        val second = executor.submit<WarmProotJobExecution> { controller.executeBlocking(request("second")) }
        Thread.sleep(120L)
        assertEquals(1, requestCount.get())
        releaseFirst.countDown()

        assertTrue(first.get(3, TimeUnit.SECONDS).succeeded)
        assertTrue(second.get(3, TimeUnit.SECONDS).succeeded)
        controller.close()
        executor.shutdownNow()
        assertEquals(2, requestCount.get())
    }

    @Test
    fun `错 job 输出触发协议失败并销毁整条 session`() {
        val process = ScriptedProcess { input, output ->
            output.writeReady(42)
            val jobId = input.readRequest().jobId()
            output.writeStarted(jobId, 100)
            output.writeOutput(102, "other-job", "leak".toByteArray())
        }
        val controller = WarmProotRunnerController(processFactory = { process })

        val result = controller.executeBlocking(request("owner"))

        assertEquals(WarmProotRunnerFailureKind.PROTOCOL_FAILED, result.failureKind)
        assertTrue(process.destroyed)
        assertFalse(result.fallbackAllowed)
    }

    private fun request(
        jobId: String,
        timeoutMs: Long = 2_000L,
        maxOutputBytes: Int = 1024,
    ) = WarmProotJobRequest(
        jobId = jobId,
        argv = listOf("/bin/true"),
        timeoutMs = timeoutMs,
        maxOutputBytesPerStream = maxOutputBytes,
    )

    private class ScriptedProcess(
        script: (DataInputStream, DataOutputStream) -> Unit,
    ) : WarmProotRunnerProcess {
        private val clientInput = PipedInputStream(64 * 1024)
        private val serverOutput = PipedOutputStream(clientInput)
        private val serverInput = PipedInputStream(64 * 1024)
        private val clientOutput = PipedOutputStream(serverInput)
        private val finished = CountDownLatch(1)

        @Volatile
        private var alive = true

        @Volatile
        var destroyed = false
            private set

        init {
            Thread({
                try {
                    DataInputStream(serverInput).use { input ->
                        DataOutputStream(serverOutput).use { output -> script(input, output) }
                    }
                } finally {
                    alive = false
                    runCatching { serverOutput.close() }
                    finished.countDown()
                }
            }, "FakeWarmProotRunner").apply { isDaemon = true; start() }
        }

        override val input: InputStream get() = clientInput
        override val output: OutputStream get() = clientOutput
        override val error: InputStream = ByteArrayInputStream(byteArrayOf())
        override val isAlive: Boolean get() = alive
        override fun waitFor(timeoutMs: Long): Boolean = finished.await(timeoutMs, TimeUnit.MILLISECONDS)
        override fun destroy() = closeAll()
        override fun destroyForcibly() = closeAll()

        private fun closeAll() {
            destroyed = true
            alive = false
            runCatching { clientOutput.close() }
            runCatching { serverInput.close() }
            runCatching { serverOutput.close() }
            runCatching { clientInput.close() }
            finished.countDown()
        }
    }

    private data class RequestFrame(val type: Int, val payload: ByteArray) {
        fun jobId(): String = DataInputStream(ByteArrayInputStream(payload)).use { input ->
            ByteArray(input.readUnsignedShort()).also(input::readFully).toString(Charsets.UTF_8)
        }
    }

    private fun DataInputStream.readRequest(): RequestFrame {
        assertEquals("KFR1", ByteArray(4).also(::readFully).toString(Charsets.US_ASCII))
        assertEquals(1, readUnsignedByte())
        val type = readUnsignedByte()
        readUnsignedShort()
        return RequestFrame(type, ByteArray(readInt()).also(::readFully))
    }

    private fun DataInputStream.readWireString(): String =
        ByteArray(readUnsignedShort()).also(::readFully).toString(Charsets.UTF_8)

    private fun DataOutputStream.writeReady(pid: Int) = writeFrame(100) { writeInt(pid) }

    private fun DataOutputStream.writeStarted(jobId: String, pid: Int) = writeFrame(101) {
        writeWireString(jobId)
        writeInt(pid)
        writeInt(pid)
        writeInt(pid)
    }

    private fun DataOutputStream.writeOutput(type: Int, jobId: String, bytes: ByteArray) = writeFrame(type) {
        writeWireString(jobId)
        write(bytes)
    }

    private fun DataOutputStream.writeExited(
        jobId: String,
        exitCode: Int,
        signal: Int = 0,
        flags: Int = 0,
    ) = writeFrame(104) {
        writeWireString(jobId)
        writeInt(exitCode)
        writeInt(signal)
        writeByte(flags)
    }

    private fun DataOutputStream.writeFrame(type: Int, payloadWriter: DataOutputStream.() -> Unit) {
        val payload = ByteArrayOutputStream().use { bytes ->
            DataOutputStream(bytes).use { output -> output.payloadWriter() }
            bytes.toByteArray()
        }
        synchronized(this) {
            writeBytes("KFR1")
            writeByte(1)
            writeByte(type)
            writeShort(0)
            writeInt(payload.size)
            write(payload)
            flush()
        }
    }

    private fun DataOutputStream.writeWireString(value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        writeShort(bytes.size)
        write(bytes)
    }
}
