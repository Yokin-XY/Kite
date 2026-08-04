package com.kite.app.foundation.runtime

import com.kite.app.foundation.contracts.BaseImageProfile
import java.io.File
import java.util.zip.GZIPInputStream
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BundledArchivePackagingTest {
    @Test
    fun compressedTarAssetsUseTgzNamesAndValidGzipStreams() {
        val rootfs = projectFile(
            "src/main/assets/rootfs/ubuntu-base-24.04-arm64.tgz",
            "app/src/main/assets/rootfs/ubuntu-base-24.04-arm64.tgz"
        )
        val python = projectFile(
            "../assets/toolchain/ai-dev-pack/packages/cpython-3.14.6+20260623-aarch64-unknown-linux-gnu-install_only_stripped.tgz",
            "assets/toolchain/ai-dev-pack/packages/cpython-3.14.6+20260623-aarch64-unknown-linux-gnu-install_only_stripped.tgz"
        )
        val uv = projectFile(
            "../assets/toolchain/ai-dev-pack/packages/uv-aarch64-unknown-linux-gnu.tgz",
            "assets/toolchain/ai-dev-pack/packages/uv-aarch64-unknown-linux-gnu.tgz"
        )

        assertTrue(BaseImageProfile.NOBLE.rootfsAssetCandidates.first().endsWith(".tgz"))
        listOf(rootfs, python, uv).forEach { archive ->
            assertTrue("missing archive: $archive", archive.isFile)
            assertTrue("invalid gzip stream: $archive", archive.hasReadableGzipPayload())
        }

        val manifest = projectFile(
            "../assets/toolchain/ai-dev-pack/manifest.json",
            "assets/toolchain/ai-dev-pack/manifest.json"
        ).readText()
        assertTrue(manifest.contains("uv-aarch64-unknown-linux-gnu.tgz"))
        assertTrue(manifest.contains("install_only_stripped.tgz"))
        assertFalse(manifest.contains(".tar.gz"))
    }

    @Test
    fun bundledRootfsContainsSupervisordServiceBackend() {
        val rootfs = projectFile(
            "src/main/assets/rootfs/ubuntu-base-24.04-arm64.tgz",
            "app/src/main/assets/rootfs/ubuntu-base-24.04-arm64.tgz"
        )
        val backendFiles = setOf(
            "usr/bin/supervisord",
            "usr/bin/supervisorctl",
            "etc/supervisor/supervisord.conf"
        )

        assertTrue(
            "bundled rootfs must ship the backend advertised by systemctl",
            rootfs.containsTarEntries(backendFiles)
        )
        assertTrue(
            "base image readiness must invalidate installations missing supervisord",
            AssetExtractor.ROOTFS_REQUIRED_FILES.containsAll(backendFiles)
        )
        assertTrue(
            "container readiness must rebuild installations missing supervisord",
            KFContainerManager.CONTAINER_ROOTFS_REQUIRED_FILES.containsAll(backendFiles)
        )
    }

    private fun File.hasReadableGzipPayload(): Boolean =
        runCatching {
            GZIPInputStream(inputStream().buffered()).use { input -> input.read() >= 0 }
        }.getOrDefault(false)

    private fun File.containsTarEntries(required: Set<String>): Boolean =
        runCatching {
            val remaining = required.toMutableSet()
            TarArchiveInputStream(GZIPInputStream(inputStream().buffered())).use { archive ->
                while (remaining.isNotEmpty()) {
                    val entry = archive.nextTarEntry ?: break
                    remaining.remove(entry.name.removePrefix("./"))
                }
            }
            remaining.isEmpty()
        }.getOrDefault(false)

    private fun projectFile(vararg candidates: String): File =
        candidates.map(::File).first { it.isFile }
}
