package com.kite.app.foundation.workspace

import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContainerVisibleFileResolverTest {

    @Test
    fun `workspace path maps to same host workspace`() {
        val root = Files.createTempDirectory("kite-visible-workspace").toFile()
        try {
            assertEquals(
                root.resolve("project/app.apk").absolutePath,
                ContainerVisibleFileResolver.resolve(root, "/workspace/project/app.apk")?.absolutePath,
            )
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `real Android paths remain unchanged`() {
        val root = Files.createTempDirectory("kite-visible-android").toFile()
        try {
            assertTrue(
                ContainerVisibleFileResolver.resolve(root, "file:///storage/emulated/0/Download/app.apk")
                    ?.path?.replace('\\', '/')?.endsWith("/storage/emulated/0/Download/app.apk") == true
            )
            assertTrue(
                ContainerVisibleFileResolver.resolve(root, "/sdcard/Download/app.apk")
                    ?.path?.replace('\\', '/')?.endsWith("/sdcard/Download/app.apk") == true
            )
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `legacy aliases and escaped paths are rejected`() {
        val root = Files.createTempDirectory("kite-visible-reject").toFile()
        try {
            assertNull(ContainerVisibleFileResolver.resolve(root, "/exchange/app.apk"))
            assertNull(ContainerVisibleFileResolver.resolve(root, "/chuan/app.apk"))
            assertNull(ContainerVisibleFileResolver.resolve(root, "/workspace/../../data/app.apk"))
        } finally {
            root.deleteRecursively()
        }
    }
}
