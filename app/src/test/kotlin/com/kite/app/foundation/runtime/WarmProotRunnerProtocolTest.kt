package com.kite.app.foundation.runtime

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class WarmProotRunnerProtocolTest {
    @Test
    fun `run 帧固定 job argv cwd timeout 和排序后的白名单环境`() {
        val encoded = WarmProotRunnerProtocol.encodeRun(
            WarmProotJobRequest(
                jobId = "job-1",
                argv = listOf("/usr/bin/printf", "%s", "ok"),
                workingDirectory = "/workspace/project",
                environment = linkedMapOf("TERM" to "xterm", "LANG" to "C.UTF-8"),
                timeoutMs = 2300L,
            )
        )
        val source = DataInputStream(ByteArrayInputStream(encoded))
        assertEquals("KFR1", ByteArray(4).also(source::readFully).toString(Charsets.US_ASCII))
        assertEquals(1, source.readUnsignedByte())
        assertEquals(1, source.readUnsignedByte())
        assertEquals(0, source.readUnsignedShort())
        val payloadLength = source.readInt()
        assertEquals(payloadLength, source.available())
        assertEquals("job-1", source.readWireString())
        assertEquals("/workspace/project", source.readWireString())
        assertEquals(2300, source.readInt())
        assertEquals(3, source.readUnsignedShort())
        assertEquals(listOf("/usr/bin/printf", "%s", "ok"), List(3) { source.readWireString() })
        assertEquals(2, source.readUnsignedShort())
        assertEquals("LANG", source.readWireString())
        assertEquals("C.UTF-8", source.readWireString())
        assertEquals("TERM", source.readWireString())
        assertEquals("xterm", source.readWireString())
        assertEquals(0, source.available())
    }

    @Test
    fun `响应帧保留二进制输出并区分 stdout stderr 与结束原因`() {
        val output = byteArrayOf(0, 1, 2, 0x7f)
        val stdout = WarmProotRunnerProtocol.readFrame(
            ByteArrayInputStream(responseFrame(102) { writeWireString("job-1"); write(output) })
        ) as WarmProotRunnerFrame.Output
        val stderr = WarmProotRunnerProtocol.readFrame(
            ByteArrayInputStream(responseFrame(103) { writeWireString("job-1"); write(output) })
        ) as WarmProotRunnerFrame.Output
        val exited = WarmProotRunnerProtocol.readFrame(
            ByteArrayInputStream(responseFrame(104) {
                writeWireString("job-1")
                writeInt(-1)
                writeInt(15)
                writeByte(3)
            })
        ) as WarmProotRunnerFrame.Exited

        assertEquals(WarmProotOutputStream.STDOUT, stdout.stream)
        assertEquals(WarmProotOutputStream.STDERR, stderr.stream)
        assertArrayEquals(output, stdout.bytes)
        assertArrayEquals(output, stderr.bytes)
        assertEquals(-1, exited.exitCode)
        assertEquals(15, exited.termSignal)
        assertTrue(exited.cancelled)
        assertTrue(exited.timedOut)
    }

    @Test
    fun `非法 job cwd env timeout 和输出上限在写入前拒绝`() {
        val invalid = listOf(
            request(jobId = "bad id"),
            request(workingDirectory = "/etc"),
            request(workingDirectory = "/workspace/../etc"),
            request(environment = mapOf("PATH" to "/tmp")),
            request(environment = mapOf("KF_JOB_bad" to "value")),
            request(timeoutMs = 0L),
            request(maxOutputBytesPerStream = 0),
            request(argv = emptyList()),
        )

        invalid.forEach { request ->
            assertThrows(IllegalArgumentException::class.java) { WarmProotRunnerProtocol.encodeRun(request) }
        }
    }

    @Test
    fun `错误 magic version 长度 未知类型和尾随数据均 fail closed`() {
        val badMagic = responseFrame(100) { writeInt(1) }.also { it[0] = 'X'.code.toByte() }
        val badVersion = responseFrame(100) { writeInt(1) }.also { it[4] = 2 }
        val badLength = responseFrame(100) { writeInt(1) }.also {
            DataOutputStream(ByteArrayOutputStream()).use { _ -> }
            it[8] = 0x7f
        }
        val unknown = responseFrame(99) {}
        val trailing = responseFrame(100) { writeInt(1); writeByte(1) }

        listOf(badMagic, badVersion, badLength, unknown, trailing).forEach { frame ->
            assertThrows(WarmProotRunnerProtocolException::class.java) {
                WarmProotRunnerProtocol.readFrame(ByteArrayInputStream(frame))
            }
        }
    }

    @Test
    fun `cancel shutdown 与 ping 都是独立有界帧`() {
        val cancel = WarmProotRunnerProtocol.encodeCancel("job-1")
        val shutdown = WarmProotRunnerProtocol.encodeShutdown()
        val ping = WarmProotRunnerProtocol.encodePing()

        assertEquals(2, cancel[5].toInt())
        assertEquals(3, shutdown[5].toInt())
        assertEquals(4, ping[5].toInt())
        assertEquals(0, DataInputStream(ByteArrayInputStream(shutdown, 8, 4)).readInt())
        assertEquals(0, DataInputStream(ByteArrayInputStream(ping, 8, 4)).readInt())
    }

    private fun request(
        jobId: String = "job-1",
        argv: List<String> = listOf("/bin/true"),
        workingDirectory: String = "/workspace",
        environment: Map<String, String> = emptyMap(),
        timeoutMs: Long = 1000L,
        maxOutputBytesPerStream: Int = 1024,
    ) = WarmProotJobRequest(
        jobId = jobId,
        argv = argv,
        workingDirectory = workingDirectory,
        environment = environment,
        timeoutMs = timeoutMs,
        maxOutputBytesPerStream = maxOutputBytesPerStream,
    )

    private fun responseFrame(type: Int, payloadWriter: DataOutputStream.() -> Unit): ByteArray {
        val payload = ByteArrayOutputStream().use { bytes ->
            DataOutputStream(bytes).use { output -> output.payloadWriter() }
            bytes.toByteArray()
        }
        return ByteArrayOutputStream().use { bytes ->
            DataOutputStream(bytes).use { output ->
                output.writeBytes("KFR1")
                output.writeByte(1)
                output.writeByte(type)
                output.writeShort(0)
                output.writeInt(payload.size)
                output.write(payload)
            }
            bytes.toByteArray()
        }
    }

    private fun DataInputStream.readWireString(): String =
        ByteArray(readUnsignedShort()).also(::readFully).toString(Charsets.UTF_8)

    private fun DataOutputStream.writeWireString(value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        writeShort(bytes.size)
        write(bytes)
    }
}
