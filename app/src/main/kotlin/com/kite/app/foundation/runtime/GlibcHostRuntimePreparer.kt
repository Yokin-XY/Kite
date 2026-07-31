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

internal data class GlibcHostRuntimeAssets(
    val launcher: File,
    val patchedLoader: File,
    val patchedLibc: File,
    val compatLibrary: File,
    val resolvConf: File,
)

internal sealed interface GlibcHostRuntimePreparation {
    data class Ready(val assets: GlibcHostRuntimeAssets) : GlibcHostRuntimePreparation
    data class Unsupported(val reason: String) : GlibcHostRuntimePreparation
}

/**
 * 准备入口无关的 glibc Host 资产。它只发布 Kite 自有启动器、兼容层和 rootfs 身份副本，
 * 不查找解释器、不创建业务进程，也不读取资源 ID。
 */
internal object GlibcHostRuntimePreparer {
    private const val ASSET_LAUNCHER = "glibc-runtime/kite-glibc-host-launcher-arm64"
    private const val ASSET_GLIBC_COMPAT = "glibc-runtime/libkite-glibc-compat.so"
    private const val MARKER_SCHEMA = "kite_glibc_host_assets_v1"
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
    ): GlibcHostRuntimePreparation {
        val appContext = context.applicationContext
        val rootfs = File(container.rootfsPath)
        if (!workspaceDirectory.isDirectory) return unsupported("workspace_missing")
        if (!workspaceControlDirectory.isDirectory) return unsupported("workspace_control_missing")
        if (!rootfs.isDirectory) return unsupported("rootfs_missing")

        val dnsServers = resolveDnsServers(appContext)
        if (dnsServers.isEmpty()) return unsupported("android_dns_missing")
        val sourceLibc = libcRelativeCandidates.asSequence()
            .map { File(rootfs, it) }
            .firstOrNull(File::isFile)
            ?: return unsupported("glibc_libc_missing")
        val sourceLoader = loaderRelativeCandidates.asSequence()
            .map { File(rootfs, it) }
            .firstOrNull(File::isFile)
            ?: return unsupported("glibc_loader_missing")
        val launcherBytes = readAsset(appContext, ASSET_LAUNCHER)
            ?: return unsupported("host_launcher_asset_missing")
        val compatBytes = readAsset(appContext, ASSET_GLIBC_COMPAT)
            ?: return unsupported("host_compat_asset_missing")

        val runtimeRoot = File(workspaceControlDirectory, "system/glibc-runtime/host")
        val launcher = File(runtimeRoot, "kite-glibc-host")
        val patchedLoader = File(runtimeRoot, "glibc/ld-linux-aarch64.so.1")
        val patchedLibc = File(runtimeRoot, "glibc/libc.so.6")
        val compatLibrary = File(runtimeRoot, "glibc/libkite-glibc-compat.so")
        val resolvConf = File(runtimeRoot, "resolv.conf")
        val marker = File(runtimeRoot, "assets.identity")
        val identity = buildIdentity(sourceLoader, sourceLibc, launcherBytes, compatBytes)

        val published = runCatching {
            if (marker.readTextOrNull() != identity ||
                !launcher.isFile || !patchedLoader.isFile || !patchedLibc.isFile || !compatLibrary.isFile
            ) {
                val patchedLoaderBytes = HostNodeRuntimePreparer.patchSetRobustListSyscalls(
                    sourceLoader.readBytes(),
                    expectedReplacements = 1,
                )
                val patchedLibcBytes = HostNodeRuntimePreparer.patchClone3Syscalls(
                    HostNodeRuntimePreparer.patchSetRobustListSyscalls(
                        HostNodeRuntimePreparer.patchResolverPath(sourceLibc.readBytes()),
                        expectedReplacements = 2,
                    ),
                    expectedReplacements = 1,
                )
                writeBytesAtomic(launcher, launcherBytes)
                check(launcher.setExecutable(true, false) || launcher.canExecute()) {
                    "glibc host launcher is not executable"
                }
                writeBytesAtomic(patchedLoader, patchedLoaderBytes)
                check(patchedLoader.setExecutable(true, false) || patchedLoader.canExecute()) {
                    "patched glibc loader is not executable"
                }
                writeBytesAtomic(patchedLibc, patchedLibcBytes)
                writeBytesAtomic(compatLibrary, compatBytes)
                writeBytesAtomic(marker, identity.toByteArray(StandardCharsets.UTF_8))
            } else if (!launcher.canExecute() || !patchedLoader.canExecute()) {
                check(launcher.setExecutable(true, false) || launcher.canExecute()) {
                    "glibc host launcher is not executable"
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
        if (published.isFailure) return unsupported("host_assets_publish_failed")
        return GlibcHostRuntimePreparation.Ready(
            GlibcHostRuntimeAssets(
                launcher = launcher,
                patchedLoader = patchedLoader,
                patchedLibc = patchedLibc,
                compatLibrary = compatLibrary,
                resolvConf = resolvConf,
            )
        )
    }

    private fun unsupported(reason: String) = GlibcHostRuntimePreparation.Unsupported(reason)

    private fun resolveDnsServers(context: Context): List<String> {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val raw = manager?.activeNetwork
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
        compat: ByteArray,
    ): String = buildString {
        appendLine(MARKER_SCHEMA)
        appendLine("loader=${sourceLoader.canonicalPath}")
        appendLine("loaderSha256=${sha256(sourceLoader)}")
        appendLine("libc=${sourceLibc.canonicalPath}")
        appendLine("libcSha256=${sha256(sourceLibc)}")
        appendLine("launcherSha256=${sha256(launcher)}")
        appendLine("compatSha256=${sha256(compat)}")
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

    private fun File.readTextOrNull(): String? = runCatching {
        takeIf(File::isFile)?.readText()
    }.getOrNull()

    private fun writeBytesAtomic(target: File, bytes: ByteArray) {
        if (target.isFile && runCatching { target.readBytes().contentEquals(bytes) }.getOrDefault(false)) return
        val parent = target.parentFile ?: error("glibc host asset has no parent")
        check(parent.mkdirs() || parent.isDirectory) { "cannot create glibc host runtime directory" }
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

    private data class SourceStamp(val path: String, val length: Long, val modifiedAt: Long)
    private val sourceDigestCache = ConcurrentHashMap<SourceStamp, String>()
}
