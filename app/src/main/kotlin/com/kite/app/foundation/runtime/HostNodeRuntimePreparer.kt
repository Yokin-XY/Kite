package com.kite.app.foundation.runtime

import android.content.Context
import android.net.ConnectivityManager
import com.kite.app.foundation.contracts.ContainerRecord
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

internal sealed interface HostNodeRuntimePreparation {
    data class Ready(val assets: HostNodeRuntimeAssets) : HostNodeRuntimePreparation
    data class Fallback(val reason: String) : HostNodeRuntimePreparation
}

/**
 * 准备通用 Node 宿主快速通道所需的 Kite 自有资产。
 *
 * Ubuntu rootfs 和 Node 安装目录仍是事实来源；这里只发布启动器、预载层、DNS 配置，
 * 并生成一份不修改 rootfs 的 libc 运行副本。任一门槛不满足时，上层继续走 PRoot。
 */
internal object HostNodeRuntimePreparer {
    private const val ASSET_LAUNCHER = "node-runtime/kite-node-host-launcher-arm64"
    private const val ASSET_PRELOAD = "node-runtime/kite-node-host-runtime.cjs"
    private const val ASSET_GLIBC_COMPAT = "node-runtime/libkite-node-glibc-compat.so"
    private const val MARKER_SCHEMA = "kite_node_host_assets_v5"
    private val loaderRelativeCandidates = listOf(
        "usr/lib/aarch64-linux-gnu/ld-linux-aarch64.so.1",
        "lib/aarch64-linux-gnu/ld-linux-aarch64.so.1",
    )
    private val libcRelativeCandidates = listOf(
        "usr/lib/aarch64-linux-gnu/libc.so.6",
        "lib/aarch64-linux-gnu/libc.so.6",
    )

    @Synchronized
    fun prepare(
        context: Context,
        container: ContainerRecord,
        workspaceDirectory: File = File(container.workspacePath),
        workspaceControlDirectory: File = File(workspaceDirectory, ".kf"),
    ): HostNodeRuntimePreparation {
        val appContext = context.applicationContext
        val workspace = workspaceDirectory
        val rootfs = File(container.rootfsPath)
        if (!workspace.isDirectory) return HostNodeRuntimePreparation.Fallback("workspace_missing")
        if (!rootfs.isDirectory) return HostNodeRuntimePreparation.Fallback("rootfs_missing")

        val dnsServers = resolveDnsServers(appContext)
        if (dnsServers.isEmpty()) return HostNodeRuntimePreparation.Fallback("android_dns_missing")

        val sourceLibc = libcRelativeCandidates
            .asSequence()
            .map { File(rootfs, it) }
            .firstOrNull(File::isFile)
            ?: return HostNodeRuntimePreparation.Fallback("glibc_libc_missing")
        val sourceLoader = loaderRelativeCandidates
            .asSequence()
            .map { File(rootfs, it) }
            .firstOrNull(File::isFile)
            ?: return HostNodeRuntimePreparation.Fallback("glibc_loader_missing")
        val launcherBytes = readAsset(appContext, ASSET_LAUNCHER)
            ?: return HostNodeRuntimePreparation.Fallback("host_launcher_asset_missing")
        val preloadBytes = readAsset(appContext, ASSET_PRELOAD)
            ?: return HostNodeRuntimePreparation.Fallback("host_preload_asset_missing")
        val compatBytes = readAsset(appContext, ASSET_GLIBC_COMPAT)
            ?: return HostNodeRuntimePreparation.Fallback("host_compat_asset_missing")

        val runtimeRoot = File(workspaceControlDirectory, "system/node-runtime/host")
        val launcher = File(runtimeRoot, "kite-node-host")
        val preload = File(runtimeRoot, "kite-node-host-runtime.cjs")
        val patchedLoader = File(runtimeRoot, "glibc/ld-linux-aarch64.so.1")
        val patchedLibc = File(runtimeRoot, "glibc/libc.so.6")
        val compatLibrary = File(runtimeRoot, "glibc/libkite-node-glibc-compat.so")
        val resolvConf = File(runtimeRoot, "resolv.conf")
        val marker = File(runtimeRoot, "assets.identity")
        val identity = buildIdentity(sourceLoader, sourceLibc, launcherBytes, preloadBytes, compatBytes)

        val published = runCatching {
            if (marker.readTextOrNull() != identity ||
                !launcher.isFile || !preload.isFile || !patchedLoader.isFile || !patchedLibc.isFile ||
                !compatLibrary.isFile
            ) {
                val patchedLoaderBytes = patchSetRobustListSyscalls(
                    sourceLoader.readBytes(),
                    expectedReplacements = EXPECTED_LOADER_SET_ROBUST_LIST_CALLS,
                )
                val patchedLibcBytes = patchClone3Syscalls(
                    patchSetRobustListSyscalls(
                        patchResolverPath(
                            sourceLibc.readBytes(),
                            expectedReplacements = EXPECTED_LIBC_RESOLVER_PATHS,
                        ),
                        expectedReplacements = EXPECTED_LIBC_SET_ROBUST_LIST_CALLS,
                    ),
                    expectedReplacements = EXPECTED_LIBC_CLONE3_CALLS,
                )
                writeBytesAtomic(launcher, launcherBytes)
                check(launcher.setExecutable(true, false) || launcher.canExecute()) {
                    "host launcher is not executable"
                }
                writeBytesAtomic(preload, preloadBytes)
                writeBytesAtomic(patchedLoader, patchedLoaderBytes)
                check(patchedLoader.setExecutable(true, false) || patchedLoader.canExecute()) {
                    "patched glibc loader is not executable"
                }
                writeBytesAtomic(patchedLibc, patchedLibcBytes)
                writeBytesAtomic(compatLibrary, compatBytes)
                writeBytesAtomic(marker, identity.toByteArray(StandardCharsets.UTF_8))
            } else if (!launcher.canExecute() || !patchedLoader.canExecute()) {
                check(launcher.setExecutable(true, false) || launcher.canExecute()) {
                    "host launcher is not executable"
                }
                check(patchedLoader.setExecutable(true, false) || patchedLoader.canExecute()) {
                    "patched glibc loader is not executable"
                }
            }
            writeBytesAtomic(
                resolvConf,
                ContainerDnsPolicy.renderResolvConf(dnsServers).toByteArray(StandardCharsets.UTF_8),
            )
        }
        if (published.isFailure) {
            return HostNodeRuntimePreparation.Fallback("host_assets_publish_failed")
        }
        return HostNodeRuntimePreparation.Ready(
            HostNodeRuntimeAssets(
                launcher = launcher,
                preloadScript = preload,
                patchedLoader = patchedLoader,
                patchedLibc = patchedLibc,
                compatLibrary = compatLibrary,
                resolvConf = resolvConf,
            )
        )
    }

    internal fun patchResolverPath(
        source: ByteArray,
        expectedReplacements: Int = EXPECTED_LIBC_RESOLVER_PATHS,
    ): ByteArray {
        val original = "/etc/resolv.conf".toByteArray(StandardCharsets.US_ASCII)
        val replacement = "/proc/self/fd/99".toByteArray(StandardCharsets.US_ASCII)
        check(original.size == replacement.size)
        val patched = source.copyOf()
        var replacements = 0
        var index = 0
        while (index <= patched.size - original.size) {
            var matches = true
            for (offset in original.indices) {
                if (patched[index + offset] != original[offset]) {
                    matches = false
                    break
                }
            }
            if (matches) {
                replacement.copyInto(patched, destinationOffset = index)
                replacements += 1
                index += original.size
            } else {
                index += 1
            }
        }
        check(replacements == expectedReplacements) {
            "glibc resolver path marker count mismatch: expected=$expectedReplacements actual=$replacements"
        }
        return patched
    }

    /**
     * Android 应用 seccomp 会以 SIGSYS 拒绝 Ubuntu glibc 的 set_robust_list(99)。
     * 宿主快速通道不承诺 robust pthread mutex，因此把该可选注册改为返回成功；
     * 普通 futex/pthread 与 Node 工作线程不受影响，无法接受此边界的程序继续走 PRoot。
     */
    internal fun patchSetRobustListSyscalls(
        source: ByteArray,
        expectedReplacements: Int,
    ): ByteArray {
        val patched = source.copyOf()
        var replacements = 0
        executableFileRanges(patched).forEach { range ->
            var offset = alignInstructionOffset(range.first)
            while (offset <= range.last - (AARCH64_INSTRUCTION_BYTES - 1)) {
                if (readInstruction(patched, offset) == AARCH64_MOV_X8_SET_ROBUST_LIST) {
                    val limit = minOf(
                        range.last - (AARCH64_INSTRUCTION_BYTES - 1),
                        offset + AARCH64_SET_ROBUST_LIST_SCAN_BYTES,
                    )
                    var candidate = offset + AARCH64_INSTRUCTION_BYTES
                    while (candidate <= limit) {
                        if (readInstruction(patched, candidate) == AARCH64_SVC_ZERO) {
                            writeInstruction(patched, candidate, AARCH64_MOV_X0_ZERO)
                            replacements += 1
                            break
                        }
                        candidate += AARCH64_INSTRUCTION_BYTES
                    }
                }
                offset += AARCH64_INSTRUCTION_BYTES
            }
        }
        check(replacements == expectedReplacements) {
            "glibc set_robust_list marker count mismatch: expected=$expectedReplacements actual=$replacements"
        }
        return patched
    }

    /**
     * Android 应用 seccomp 会以 SIGSYS 拒绝 clone3(435)。glibc 已经为旧内核实现了
     * clone 回退，因此让 clone3 返回 -ENOSYS，保留 glibc 自己的兼容选择。
     */
    internal fun patchClone3Syscalls(
        source: ByteArray,
        expectedReplacements: Int,
    ): ByteArray {
        val patched = source.copyOf()
        var replacements = 0
        executableFileRanges(patched).forEach { range ->
            var offset = alignInstructionOffset(range.first)
            while (offset <= range.last - (AARCH64_INSTRUCTION_BYTES - 1)) {
                if (readInstruction(patched, offset) == AARCH64_MOV_X8_CLONE3) {
                    val limit = minOf(
                        range.last - (AARCH64_INSTRUCTION_BYTES - 1),
                        offset + AARCH64_CLONE3_SCAN_BYTES,
                    )
                    var candidate = offset + AARCH64_INSTRUCTION_BYTES
                    while (candidate <= limit) {
                        if (readInstruction(patched, candidate) == AARCH64_SVC_ZERO) {
                            writeInstruction(patched, candidate, AARCH64_MOV_X0_NEGATIVE_ENOSYS)
                            replacements += 1
                            break
                        }
                        candidate += AARCH64_INSTRUCTION_BYTES
                    }
                }
                offset += AARCH64_INSTRUCTION_BYTES
            }
        }
        check(replacements == expectedReplacements) {
            "glibc clone3 marker count mismatch: expected=$expectedReplacements actual=$replacements"
        }
        return patched
    }

    internal fun executableFileRanges(source: ByteArray): List<IntRange> {
        check(source.size >= ELF64_HEADER_BYTES) { "glibc ELF header is truncated" }
        check(
            source[0] == 0x7f.toByte() && source[1] == 'E'.code.toByte() &&
                source[2] == 'L'.code.toByte() && source[3] == 'F'.code.toByte() &&
                source[4] == ELF_CLASS_64 && source[5] == ELF_DATA_LITTLE_ENDIAN
        ) { "glibc asset is not little-endian ELF64" }
        check(readUnsigned16(source, ELF_MACHINE_OFFSET) == ELF_MACHINE_AARCH64) {
            "glibc ELF machine is not AArch64"
        }
        val programHeaderOffset = readUnsigned64(source, ELF_PROGRAM_HEADER_OFFSET).toBoundedInt(source.size)
        val programHeaderEntrySize = readUnsigned16(source, ELF_PROGRAM_HEADER_ENTRY_SIZE_OFFSET)
        val programHeaderCount = readUnsigned16(source, ELF_PROGRAM_HEADER_COUNT_OFFSET)
        check(programHeaderEntrySize >= ELF64_PROGRAM_HEADER_BYTES && programHeaderCount > 0) {
            "glibc ELF program headers are invalid"
        }
        val tableEnd = programHeaderOffset.toLong() + programHeaderEntrySize.toLong() * programHeaderCount
        check(tableEnd <= source.size.toLong()) { "glibc ELF program header table is truncated" }
        return buildList {
            repeat(programHeaderCount) { index ->
                val header = programHeaderOffset + index * programHeaderEntrySize
                val type = readUnsigned32(source, header)
                val flags = readUnsigned32(source, header + ELF_PROGRAM_HEADER_FLAGS_OFFSET)
                if (type != ELF_PROGRAM_TYPE_LOAD || flags and ELF_PROGRAM_FLAG_EXECUTE == 0L) return@repeat
                val fileOffset = readUnsigned64(source, header + ELF_PROGRAM_HEADER_FILE_OFFSET).toBoundedInt(source.size)
                val fileSize = readUnsigned64(source, header + ELF_PROGRAM_HEADER_FILE_SIZE_OFFSET)
                val endExclusive = fileOffset.toLong() + fileSize
                check(fileSize > 0L && endExclusive <= source.size.toLong()) {
                    "glibc executable segment is outside the asset"
                }
                add(fileOffset until endExclusive.toInt())
            }
        }.also { check(it.isNotEmpty()) { "glibc ELF has no executable load segment" } }
    }

    private fun resolveDnsServers(context: Context): List<String> {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val raw = manager
            ?.activeNetwork
            ?.let(manager::getLinkProperties)
            ?.dnsServers
            ?.mapNotNull { it.hostAddress?.trim() }
            .orEmpty()
        return ContainerDnsPolicy.normalize(raw)
    }

    private fun readAsset(context: Context, path: String): ByteArray? = runCatching {
        context.assets.open(path).use { it.readBytes() }
    }.getOrNull()

    private fun buildIdentity(
        sourceLoader: File,
        sourceLibc: File,
        launcher: ByteArray,
        preload: ByteArray,
        compat: ByteArray,
    ): String = buildString {
        appendLine(MARKER_SCHEMA)
        appendLine("loader=${sourceLoader.canonicalPath}")
        appendLine("loaderSha256=${sha256(sourceLoader)}")
        appendLine("libc=${sourceLibc.canonicalPath}")
        appendLine("libcSha256=${sha256(sourceLibc)}")
        appendLine("launcherSha256=${sha256(launcher)}")
        appendLine("preloadSha256=${sha256(preload)}")
        appendLine("compatSha256=${sha256(compat)}")
    }

    private fun readInstruction(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xff) or
            ((bytes[offset + 1].toInt() and 0xff) shl 8) or
            ((bytes[offset + 2].toInt() and 0xff) shl 16) or
            ((bytes[offset + 3].toInt() and 0xff) shl 24)

    private fun writeInstruction(bytes: ByteArray, offset: Int, instruction: Int) {
        bytes[offset] = instruction.toByte()
        bytes[offset + 1] = (instruction ushr 8).toByte()
        bytes[offset + 2] = (instruction ushr 16).toByte()
        bytes[offset + 3] = (instruction ushr 24).toByte()
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { byte -> "%02x".format(byte) }

    private fun sha256(file: File): String {
        val stamp = SourceStamp(file.canonicalPath, file.length(), file.lastModified())
        return sourceDigestCache.getOrPut(stamp) {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().buffered().use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (count > 0) digest.update(buffer, 0, count)
                }
            }
            digest.digest().joinToString("") { byte -> "%02x".format(byte) }
        }
    }

    private fun alignInstructionOffset(offset: Int): Int =
        (offset + AARCH64_INSTRUCTION_BYTES - 1) and -AARCH64_INSTRUCTION_BYTES

    private fun readUnsigned16(bytes: ByteArray, offset: Int): Int {
        check(offset >= 0 && offset + 2 <= bytes.size) { "glibc ELF read is outside the asset" }
        return (bytes[offset].toInt() and 0xff) or ((bytes[offset + 1].toInt() and 0xff) shl 8)
    }

    private fun readUnsigned32(bytes: ByteArray, offset: Int): Long {
        check(offset >= 0 && offset + 4 <= bytes.size) { "glibc ELF read is outside the asset" }
        return (bytes[offset].toLong() and 0xffL) or
            ((bytes[offset + 1].toLong() and 0xffL) shl 8) or
            ((bytes[offset + 2].toLong() and 0xffL) shl 16) or
            ((bytes[offset + 3].toLong() and 0xffL) shl 24)
    }

    private fun readUnsigned64(bytes: ByteArray, offset: Int): Long {
        check(offset >= 0 && offset + 8 <= bytes.size) { "glibc ELF read is outside the asset" }
        var value = 0L
        repeat(8) { index -> value = value or ((bytes[offset + index].toLong() and 0xffL) shl (index * 8)) }
        check(value >= 0L) { "glibc ELF 64-bit field exceeds supported range" }
        return value
    }

    private fun Long.toBoundedInt(assetSize: Int): Int {
        check(this in 0..assetSize.toLong()) { "glibc ELF offset is outside the asset" }
        return toInt()
    }

    private fun File.readTextOrNull(): String? = runCatching {
        takeIf(File::isFile)?.readText()
    }.getOrNull()

    private fun writeBytesAtomic(target: File, bytes: ByteArray) {
        if (target.isFile && runCatching { target.readBytes().contentEquals(bytes) }.getOrDefault(false)) return
        val parent = target.parentFile ?: error("host runtime asset has no parent")
        check(parent.mkdirs() || parent.isDirectory) { "cannot create host runtime directory" }
        val pending = File(parent, ".${target.name}.pending-${UUID.randomUUID()}")
        try {
            FileOutputStream(pending).use { output ->
                output.write(bytes)
                output.fd.sync()
            }
            try {
                Files.move(
                    pending.toPath(),
                    target.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(pending.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            if (pending.exists()) pending.delete()
        }
    }

    private const val AARCH64_INSTRUCTION_BYTES = 4
    private const val AARCH64_SET_ROBUST_LIST_SCAN_BYTES = 32
    private const val AARCH64_CLONE3_SCAN_BYTES = 32
    private const val AARCH64_MOV_X8_SET_ROBUST_LIST = 0xd2800c68.toInt()
    private const val AARCH64_MOV_X8_CLONE3 = 0xd2803668.toInt()
    private const val AARCH64_SVC_ZERO = 0xd4000001.toInt()
    private const val AARCH64_MOV_X0_ZERO = 0xd2800000.toInt()
    private const val AARCH64_MOV_X0_NEGATIVE_ENOSYS = 0x928004a0.toInt()
    private const val EXPECTED_LOADER_SET_ROBUST_LIST_CALLS = 1
    private const val EXPECTED_LIBC_SET_ROBUST_LIST_CALLS = 2
    private const val EXPECTED_LIBC_CLONE3_CALLS = 1
    private const val EXPECTED_LIBC_RESOLVER_PATHS = 1
    private const val ELF64_HEADER_BYTES = 64
    private const val ELF64_PROGRAM_HEADER_BYTES = 56
    private const val ELF_CLASS_64 = 2.toByte()
    private const val ELF_DATA_LITTLE_ENDIAN = 1.toByte()
    private const val ELF_MACHINE_OFFSET = 18
    private const val ELF_MACHINE_AARCH64 = 0xb7
    private const val ELF_PROGRAM_HEADER_OFFSET = 32
    private const val ELF_PROGRAM_HEADER_ENTRY_SIZE_OFFSET = 54
    private const val ELF_PROGRAM_HEADER_COUNT_OFFSET = 56
    private const val ELF_PROGRAM_HEADER_FLAGS_OFFSET = 4
    private const val ELF_PROGRAM_HEADER_FILE_OFFSET = 8
    private const val ELF_PROGRAM_HEADER_FILE_SIZE_OFFSET = 32
    private const val ELF_PROGRAM_TYPE_LOAD = 1L
    private const val ELF_PROGRAM_FLAG_EXECUTE = 1L

    private data class SourceStamp(val path: String, val length: Long, val modifiedAt: Long)
    private val sourceDigestCache = ConcurrentHashMap<SourceStamp, String>()
}
