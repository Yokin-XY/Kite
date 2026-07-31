package com.kite.app.agent.config

import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AtomicConfigFileStoreTest {
    private lateinit var root: File
    private lateinit var store: AtomicConfigFileStore

    @Before
    fun setUp() {
        root = Files.createTempDirectory("kite-agent-config").toFile()
        store = AtomicConfigFileStore(now = { 1234L })
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun appliesWithBackupAndReturnsNewRevision() {
        val target = File(root, "agent.jsonc").apply { writeText("{old:true}\n") }
        val original = store.read(target)
        val next = "{old:true,new:true}\n".toByteArray()

        val result = store.replace(target, original.revision, next) { null }

        assertTrue(result is AtomicConfigFileWriteResult.Applied)
        val applied = result as AtomicConfigFileWriteResult.Applied
        assertArrayEquals(next, target.readBytes())
        assertTrue(applied.revision.exists)
        assertNotNull(applied.backupReference)
        assertArrayEquals(original.bytes, File(requireNotNull(applied.backupReference)).readBytes())
    }

    @Test
    fun reportsConflictWithoutOverwritingExternalChange() {
        val target = File(root, "agent.json").apply { writeText("old") }
        val expected = store.read(target).revision
        target.writeText("external")

        val result = store.replace(target, expected, "kite".toByteArray()) { null }

        assertTrue(result is AtomicConfigFileWriteResult.Conflict)
        assertEquals("external", target.readText())
        assertFalse(File(root, ".kite-backups").exists())
    }

    @Test
    fun failedPostWriteValidationRestoresOriginalBytes() {
        val target = File(root, "agent.json").apply { writeText("original") }
        val original = store.read(target)
        var validations = 0

        val result = store.replace(target, original.revision, "next".toByteArray()) {
            validations += 1
            if (validations == 1) null else "post-write invalid"
        }

        assertTrue(result is AtomicConfigFileWriteResult.Failed)
        assertTrue((result as AtomicConfigFileWriteResult.Failed).restored)
        assertArrayEquals(original.bytes, target.readBytes())
        assertEquals(original.revision, store.read(target).revision)
    }

    @Test
    fun rejectedContentNeverMutatesMissingTarget() {
        val target = File(root, "new.jsonc")

        val result = store.replace(target, AtomicConfigFileStore.MISSING_REVISION, "bad".toByteArray()) {
            "invalid"
        }

        assertTrue(result is AtomicConfigFileWriteResult.Rejected)
        assertFalse(target.exists())
    }

    @Test
    fun multiFileTransactionAppliesBothFilesWithBackups() {
        val config = File(root, "config.json").apply { writeText("config-old") }
        val auth = File(root, "auth.json").apply { writeText("auth-old") }

        val result = store.replaceAll(listOf(
            AtomicConfigFileUpdate(config, store.read(config).revision, "config-new".toByteArray()) { null },
            AtomicConfigFileUpdate(auth, store.read(auth).revision, "auth-new".toByteArray()) { null }
        ))

        assertTrue(result is AtomicConfigFilesWriteResult.Applied)
        assertEquals("config-new", config.readText())
        assertEquals("auth-new", auth.readText())
        assertEquals(2, (result as AtomicConfigFilesWriteResult.Applied).backupReferences.size)
    }

    @Test
    fun multiFileConflictLeavesEveryTargetUntouched() {
        val config = File(root, "config.json").apply { writeText("config-old") }
        val auth = File(root, "auth.json").apply { writeText("auth-old") }
        val configRevision = store.read(config).revision
        val authRevision = store.read(auth).revision
        auth.writeText("external")

        val result = store.replaceAll(listOf(
            AtomicConfigFileUpdate(config, configRevision, "config-new".toByteArray()) { null },
            AtomicConfigFileUpdate(auth, authRevision, "auth-new".toByteArray()) { null }
        ))

        assertTrue(result is AtomicConfigFilesWriteResult.Conflict)
        assertEquals("config-old", config.readText())
        assertEquals("external", auth.readText())
    }

    @Test
    fun multiFilePostValidationFailureRestoresBothFiles() {
        val config = File(root, "config.json").apply { writeText("config-old") }
        val auth = File(root, "auth.json").apply { writeText("auth-old") }
        var authValidations = 0

        val result = store.replaceAll(listOf(
            AtomicConfigFileUpdate(config, store.read(config).revision, "config-new".toByteArray()) { null },
            AtomicConfigFileUpdate(auth, store.read(auth).revision, "auth-new".toByteArray()) {
                authValidations += 1
                if (authValidations == 1) null else "post-write invalid"
            }
        ))

        assertTrue(result is AtomicConfigFilesWriteResult.Failed)
        assertTrue((result as AtomicConfigFilesWriteResult.Failed).restored)
        assertEquals("config-old", config.readText())
        assertEquals("auth-old", auth.readText())
    }
}
