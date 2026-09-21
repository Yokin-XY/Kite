package com.kite.app.resources

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class KiteBinaryPatchApplierTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    // openat2 现场翻译点2的真实形态（点1同构，str 变体不同）：
    // mov w3,#24 ; orr w8,w8,w21 ; stur x8,[x29,#-24] ; mov w8,#437 ; svc
    private val pristine = bytes(
        "03038052", // mov w3, #24（arm64 小端存储）
        "0801152a", // orr w8, w8, w21
        "a8831ef8", // stur x8, [x29,#-24]
        "a8368052", // mov w8, #437
        "010000d4", // svc #0
    )

    private val patch = KiteResourceBinaryPatchSpec(
        path = "/target.bin",
        pattern = "03038052????????a8831ef8a8368052010000d4",
        replacement = "83348052????????e20308aa080780d2010000d4",
        expectedCount = 1,
    )

    @Test
    fun `applies patch with wildcard context preserved`() {
        val file = target(pristine)
        val result = KiteBinaryPatchApplier.apply(file, patch)
        assertTrue(result is KiteBinaryPatchApplier.Result.Applied)
        val patched = file.readBytes()
        assertArrayEquals(
            bytes(
                "83348052", // mov w3, #0o644
                "0801152a", // orr（通配位保持原字节）
                "e20308aa", // mov x2, x8
                "080780d2", // mov x8, #56 (openat)
                "010000d4",
            ),
            patched,
        )
    }

    @Test
    fun `is idempotent when replacement already present`() {
        val file = target(pristine)
        KiteBinaryPatchApplier.apply(file, patch)
        val patchedOnce = file.readBytes()
        val result = KiteBinaryPatchApplier.apply(file, patch)
        assertTrue(result is KiteBinaryPatchApplier.Result.Applied)
        assertArrayEquals(patchedOnce, file.readBytes())
    }

    @Test
    fun `mismatches when count differs from expectation`() {
        val doubled = pristine + pristine
        val file = target(doubled)
        val result = KiteBinaryPatchApplier.apply(file, patch)
        assertTrue(result is KiteBinaryPatchApplier.Result.Mismatch)
    }

    @Test
    fun `mismatches when upgraded artifact no longer contains marker`() {
        val file = target(bytes("d503201f", "d503201f"))
        val result = KiteBinaryPatchApplier.apply(file, patch)
        assertTrue(
            "unexpected: $result",
            result is KiteBinaryPatchApplier.Result.Mismatch,
        )
    }

    @Test
    fun `reports missing file without failing`() {
        val result = KiteBinaryPatchApplier.apply(File(temporaryFolder.root, "absent.bin"), patch)
        assertTrue(result is KiteBinaryPatchApplier.Result.Missing)
    }

    @Test
    fun `rejects malformed pattern before touching file`() {
        val file = target(pristine)
        val bad = patch.copy(pattern = "xyz")
        val result = KiteBinaryPatchApplier.apply(file, bad)
        assertTrue(result is KiteBinaryPatchApplier.Result.Mismatch)
        assertArrayEquals(pristine, file.readBytes())
    }

    @Test
    fun `rejects unequal pattern and replacement lengths`() {
        val file = target(pristine)
        val bad = patch.copy(replacement = "52803483")
        val result = KiteBinaryPatchApplier.apply(file, bad)
        assertTrue(result is KiteBinaryPatchApplier.Result.Mismatch)
    }

    private fun target(content: ByteArray): File =
        temporaryFolder.newFile("target.bin").apply { writeBytes(content) }

    private fun bytes(vararg hex: String): ByteArray =
        hex.joinToString("").chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}
