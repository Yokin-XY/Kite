package com.kite.app.foundation.runtime

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets

class HostNodeRuntimePreparerTest {
    @Test
    fun `patches glibc resolver path without changing binary length`() {
        val source = "prefix:/etc/resolv.conf:suffix".toByteArray(StandardCharsets.US_ASCII)

        val patched = HostNodeRuntimePreparer.patchResolverPath(source)

        assertTrue(String(patched, StandardCharsets.US_ASCII).contains("/proc/self/fd/99"))
        assertFalse(String(patched, StandardCharsets.US_ASCII).contains("/etc/resolv.conf"))
        assertTrue(source.size == patched.size)
        assertArrayEquals("source bytes stay immutable", "prefix:/etc/resolv.conf:suffix".toByteArray(), source)
    }

    @Test(expected = IllegalStateException::class)
    fun `rejects an incompatible libc without resolver marker`() {
        HostNodeRuntimePreparer.patchResolverPath("not-glibc".toByteArray())
    }

    @Test
    fun `patches nearby aarch64 set robust list syscall without mutating source`() {
        val source = executableElf(
            instructions = intArrayOf(
                0xd2800c68.toInt(),
                0xd503201f.toInt(),
                0xd503201f.toInt(),
                0xd503201f.toInt(),
                0xd503201f.toInt(),
                0xd503201f.toInt(),
                0xd503201f.toInt(),
                0xd4000001.toInt(),
            ),
        )

        val patched = HostNodeRuntimePreparer.patchSetRobustListSyscalls(source, expectedReplacements = 1)

        assertArrayEquals(instruction(0xd2800000.toInt()), patched.copyOfRange(CODE_OFFSET + 28, CODE_OFFSET + 32))
        assertArrayEquals(instruction(0xd4000001.toInt()), source.copyOfRange(CODE_OFFSET + 28, CODE_OFFSET + 32))
    }

    @Test(expected = IllegalStateException::class)
    fun `rejects glibc binary without set robust list syscall marker`() {
        HostNodeRuntimePreparer.patchSetRobustListSyscalls(
            executableElf(intArrayOf(0xd503201f.toInt())),
            expectedReplacements = 1,
        )
    }

    @Test(expected = IllegalStateException::class)
    fun `rejects a set robust marker outside executable ELF segments`() {
        val source = executableElf(
            instructions = intArrayOf(0xd503201f.toInt()),
            trailingDataInstructions = intArrayOf(0xd2800c68.toInt(), 0xd4000001.toInt()),
        )

        HostNodeRuntimePreparer.patchSetRobustListSyscalls(source, expectedReplacements = 1)
    }

    @Test(expected = IllegalStateException::class)
    fun `rejects unexpected set robust marker count`() {
        val source = executableElf(
            instructions = intArrayOf(
                0xd2800c68.toInt(), 0xd4000001.toInt(),
                0xd2800c68.toInt(), 0xd4000001.toInt(),
            ),
        )

        HostNodeRuntimePreparer.patchSetRobustListSyscalls(source, expectedReplacements = 1)
    }

    @Test
    fun `patches nearby aarch64 clone3 syscall to negative enosys without mutating source`() {
        val source = executableElf(
            instructions = intArrayOf(
                0xd2803668.toInt(),
                0xd503201f.toInt(),
                0xd503201f.toInt(),
                0xd503201f.toInt(),
                0xd503201f.toInt(),
                0xd503201f.toInt(),
                0xd503201f.toInt(),
                0xd4000001.toInt(),
            ),
        )

        val patched = HostNodeRuntimePreparer.patchClone3Syscalls(source, expectedReplacements = 1)

        assertArrayEquals(instruction(0x928004a0.toInt()), patched.copyOfRange(CODE_OFFSET + 28, CODE_OFFSET + 32))
        assertArrayEquals(instruction(0xd4000001.toInt()), source.copyOfRange(CODE_OFFSET + 28, CODE_OFFSET + 32))
    }

    @Test(expected = IllegalStateException::class)
    fun `rejects glibc binary without clone3 syscall marker`() {
        HostNodeRuntimePreparer.patchClone3Syscalls(
            executableElf(intArrayOf(0xd503201f.toInt())),
            expectedReplacements = 1,
        )
    }

    @Test(expected = IllegalStateException::class)
    fun `rejects malformed ELF program headers before scanning instructions`() {
        HostNodeRuntimePreparer.executableFileRanges(ByteArray(64))
    }

    private fun instruction(value: Int): ByteArray = ByteArray(4).also { writeInstruction(it, 0, value) }

    private fun writeInstruction(target: ByteArray, offset: Int, value: Int) {
        target[offset] = value.toByte()
        target[offset + 1] = (value ushr 8).toByte()
        target[offset + 2] = (value ushr 16).toByte()
        target[offset + 3] = (value ushr 24).toByte()
    }

    private fun executableElf(
        instructions: IntArray,
        trailingDataInstructions: IntArray = intArrayOf(),
    ): ByteArray {
        val codeSize = instructions.size * 4
        val source = ByteArray(CODE_OFFSET + codeSize + trailingDataInstructions.size * 4)
        source[0] = 0x7f
        source[1] = 'E'.code.toByte()
        source[2] = 'L'.code.toByte()
        source[3] = 'F'.code.toByte()
        source[4] = 2
        source[5] = 1
        writeUnsigned16(source, 18, 0xb7)
        writeUnsigned64(source, 32, PROGRAM_HEADER_OFFSET.toLong())
        writeUnsigned16(source, 54, PROGRAM_HEADER_SIZE)
        writeUnsigned16(source, 56, 1)
        writeUnsigned32(source, PROGRAM_HEADER_OFFSET, 1)
        writeUnsigned32(source, PROGRAM_HEADER_OFFSET + 4, 5)
        writeUnsigned64(source, PROGRAM_HEADER_OFFSET + 8, CODE_OFFSET.toLong())
        writeUnsigned64(source, PROGRAM_HEADER_OFFSET + 32, codeSize.toLong())
        instructions.forEachIndexed { index, value -> writeInstruction(source, CODE_OFFSET + index * 4, value) }
        trailingDataInstructions.forEachIndexed { index, value ->
            writeInstruction(source, CODE_OFFSET + codeSize + index * 4, value)
        }
        return source
    }

    private fun writeUnsigned16(target: ByteArray, offset: Int, value: Int) {
        target[offset] = value.toByte()
        target[offset + 1] = (value ushr 8).toByte()
    }

    private fun writeUnsigned32(target: ByteArray, offset: Int, value: Int) =
        writeInstruction(target, offset, value)

    private fun writeUnsigned64(target: ByteArray, offset: Int, value: Long) {
        repeat(8) { index -> target[offset + index] = (value ushr (index * 8)).toByte() }
    }

    private companion object {
        const val PROGRAM_HEADER_OFFSET = 64
        const val PROGRAM_HEADER_SIZE = 56
        const val CODE_OFFSET = 128
    }
}
