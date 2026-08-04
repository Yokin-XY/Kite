package com.kite.app.foundation.toolchain

import com.kite.app.resources.KiteResourceInstallRecipes
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ToolchainSharedPackTest {
    @Test
    fun bundledInstallerDoesNotShadowSystemServiceApplets() {
        val installScript = sequenceOf(
            File("../assets/toolchain/ai-dev-pack/install.sh"),
            File("assets/toolchain/ai-dev-pack/install.sh"),
        ).first(File::isFile).readText()

        assertFalse(installScript.contains("write_wrapper systemctl"))
        assertFalse(installScript.contains("write_wrapper service"))
        assertTrue(installScript.contains("remove_owned_legacy_wrapper systemctl"))
        assertTrue(installScript.contains("remove_owned_legacy_wrapper service"))
    }

    @Test
    fun resourceRecipesUseOneSharedPackAndKeepPrivateCachePaths() {
        val nodePack = KiteResourceInstallRecipes.localPackPath("kite.nodejs")
        val pythonPack = KiteResourceInstallRecipes.localPackPath("kite.python")

        assertTrue(nodePack == pythonPack)
        assertTrue(nodePack == "/workspace/.kf/cache/shared/ai-dev-pack")
        assertTrue(KiteResourceInstallRecipes.resourceCachePath("kite.nodejs").endsWith("/resources/kite.nodejs"))
        assertFalse(KiteResourceInstallRecipes.resourceCachePath("kite.nodejs").contains("/shared/"))
    }

    @Test
    fun sharedPackCompletenessRequiresManifestScriptAndDeclaredFiles() {
        val root = Files.createTempDirectory("kite-shared-pack-").toFile()
        try {
            val manifest = """
                {"packId":"ai-dev-pack","version":17,"installScript":"install.sh","packages":{"node":{"file":"packages/node.tar.xz"},"adb":{"version":"rootfs"}}}
            """.trimIndent()
            File(root, "manifest.json").writeText(manifest)
            File(root, "install.sh").writeText("#!/bin/sh")
            File(root, "packages").mkdirs()

            assertFalse(bundledPackDirectoryIsComplete(root, manifest))

            File(root, "packages/node.tar.xz").writeText("payload")
            assertTrue(bundledPackDirectoryIsComplete(root, manifest))

            val stale = File(root, "packages/node.tar").apply { writeText("expanded duplicate") }
            assertFalse(bundledPackDirectoryIsComplete(root, manifest))
            assertTrue(cleanupUndeclaredBundledPackageFiles(root, manifest) > 0L)
            assertFalse(stale.exists())
            assertTrue(bundledPackDirectoryIsComplete(root, manifest))
        } finally {
            root.deleteRecursively()
        }
    }
}
