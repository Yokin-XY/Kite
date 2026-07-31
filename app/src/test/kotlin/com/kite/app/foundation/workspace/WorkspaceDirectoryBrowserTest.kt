package com.kite.app.foundation.workspace

import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class WorkspaceDirectoryBrowserTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `root lists only visible selectable directories`() {
        val root = temporaryFolder.newFolder("workspace")
        root.resolve("Beta").mkdir()
        root.resolve("alpha").mkdir()
        root.resolve("alpha/child").mkdir()
        root.resolve("alpha/note.txt").writeText("x")
        root.resolve("alpha/.private").writeText("hidden")
        root.resolve(".kf").mkdir()
        root.resolve(".hidden").mkdir()
        root.resolve("note.txt").writeText("x")

        val entries = WorkspaceDirectoryBrowser.listDirectories(root, "/workspace")

        assertEquals(listOf("alpha", "Beta"), entries.map { it.name })
        assertEquals(listOf("/workspace/alpha", "/workspace/Beta"), entries.map { it.containerPath })
        assertEquals(listOf(2, 0), entries.map { it.itemCount })
    }

    @Test
    fun `create directory remains inside workspace and becomes selectable`() {
        val root = temporaryFolder.newFolder("workspace")

        val created = WorkspaceDirectoryBrowser.createDirectory(root, "/workspace", "Kite")

        assertEquals("/workspace/Kite", created.containerPath)
        assertEquals(0, created.itemCount)
        assertTrue(root.resolve("Kite").isDirectory)
        assertEquals("/workspace", WorkspaceDirectoryBrowser.parentPath(created.containerPath))
    }

    @Test
    fun `reserved hidden and invalid names are rejected`() {
        val root = temporaryFolder.newFolder("workspace")

        listOf(".kf", ".secret", "../outside", "a/b", "a\\b").forEach { name ->
            assertThrows(IllegalArgumentException::class.java) {
                WorkspaceDirectoryBrowser.createDirectory(root, "/workspace", name)
            }
        }
        assertFalse(root.resolve(".kf").exists())
    }

    @Test
    fun `outside symbolic directory is never listed when supported`() {
        val root = temporaryFolder.newFolder("workspace")
        val outside = temporaryFolder.newFolder("outside")
        val link = root.toPath().resolve("outside-link")
        val linked = runCatching { Files.createSymbolicLink(link, outside.toPath()) }.isSuccess
        if (!linked) return

        assertTrue(WorkspaceDirectoryBrowser.listDirectories(root, "/workspace").isEmpty())
    }
}
