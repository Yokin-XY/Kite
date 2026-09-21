package com.kite.app.resources

import java.io.File

/**
 * 声明式二进制补丁执行器（资源卡 manifest 的 binaryPatches 通用合同）。
 *
 * 背景（docs/architecture/ubuntu-simulation-doctrine.md 雷库）：App 域
 * seccomp 会拒绝部分新 syscall，而个别原生模块（Rust 内联直发）既不走
 * glibc PLT 也无降级路径，只能在制品字节层把调用点改写为安全形态。
 * 补丁以 manifest 声明驱动，执行器不认识任何具体程序；制品升级导致
 * 计数不符时干净失败并报错，不静默放行（纲领：探测→退路→干净报错）。
 *
 * 模式语法：十六进制字节串，`??` 为单字节通配（保持原字节的替换语义
 * 由 replacement 同位置的 `??` 表达）。pattern 与 replacement 必须等长。
 */
internal object KiteBinaryPatchApplier {

    sealed class Result {
        /** 全部补丁点已应用或此前已应用（幂等通过）。 */
        object Applied : Result()

        /** 声明与制品不符（计数或格式），必须干净失败。 */
        data class Mismatch(val reason: String) : Result()

        /** 目标文件缺失；调用方决定是否属于尚未安装的可忽略状态。 */
        data class Missing(val path: String) : Result()
    }

    fun apply(target: File, spec: KiteResourceBinaryPatchSpec): Result {
        if (!target.isFile) return Result.Missing(target.absolutePath)
        val pattern = parseHexPattern(spec.pattern)
        val replacement = parseHexPattern(spec.replacement)
        if (pattern == null || replacement == null) {
            return Result.Mismatch("pattern_not_hex path=${spec.path}")
        }
        if (pattern.size != replacement.size) {
            return Result.Mismatch("pattern_length_mismatch path=${spec.path}")
        }
        val bytes = target.readBytes()
        var applied = 0
        var index = 0
        while (index <= bytes.size - pattern.size) {
            if (!matchesAt(bytes, index, pattern)) {
                index += 1
                continue
            }
            for (offset in pattern.indices) {
                val byte = replacement[offset] ?: continue
                bytes[index + offset] = byte
            }
            applied += 1
            index += pattern.size
        }
        val alreadyPatched = applied == 0 &&
            matchesPatternCount(bytes, replacement, spec.expectedCount)
        if (applied == 0 && !alreadyPatched) {
            return Result.Mismatch(
                "patch_count_mismatch path=${spec.path} expected=${spec.expectedCount} actual=$applied"
            )
        }
        if (applied > 0 && applied != spec.expectedCount) {
            return Result.Mismatch(
                "patch_count_mismatch path=${spec.path} expected=${spec.expectedCount} actual=$applied"
            )
        }
        if (applied > 0) {
            val temporary = File(target.absolutePath + ".kite-patch.tmp")
            temporary.writeBytes(bytes)
            if (!temporary.renameTo(target)) {
                temporary.delete()
                target.writeBytes(bytes)
            }
        }
        return Result.Applied
    }

    /** 幂等校验：replacement 形态在文件里出现的次数（非通配位）是否等于期望。 */
    private fun matchesPatternCount(
        bytes: ByteArray,
        pattern: List<Byte?>,
        expectedCount: Int,
    ): Boolean {
        if (expectedCount <= 0) return true
        var count = 0
        var index = 0
        while (index <= bytes.size - pattern.size) {
            if (matchesAt(bytes, index, pattern)) {
                count += 1
                index += pattern.size
            } else {
                index += 1
            }
        }
        return count == expectedCount
    }

    private fun matchesAt(bytes: ByteArray, offset: Int, pattern: List<Byte?>): Boolean {
        for (index in pattern.indices) {
            val expected = pattern[index] ?: continue
            if (bytes[offset + index] != expected) return false
        }
        return true
    }

    /** "52803483??…" 或 "52 80 34 83" 形式 → 字节列表；?? → null。非法输入返回 null。 */
    private fun parseHexPattern(spec: String): List<Byte?>? {
        val cleaned = spec.replace(" ", "").lowercase()
        if (cleaned.isEmpty() || cleaned.length % 4 != 0 || !cleaned.matches(Regex("[0-9a-f?]+"))) {
            return null
        }
        val pattern = mutableListOf<Byte?>()
        for (chunk in chunked(cleaned, 2)) {
            pattern += if (chunk == "??") null else chunk.toInt(16).toByte()
        }
        return pattern
    }

    private fun chunked(value: String, size: Int): List<String> {
        if (value.length % size != 0) return emptyList()
        return value.indices.step(size).map { index -> value.substring(index, index + size) }
    }
}
