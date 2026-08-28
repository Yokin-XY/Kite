package com.kite.app.resources

import java.nio.file.Files
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResourceInstallTransactionRecoveryTest {

    @Test
    fun `应用中断后恢复更新前资源目录`() {
        val workspace = Files.createTempDirectory("kite-resource-recovery-")
        try {
            val software = workspace.resolve(".kf/software").createDirectories()
            val installRoot = software.resolve("kite.hermes.core").createDirectories()
            installRoot.resolve("new-version.txt").writeText("partial")
            val backupRoot = software.resolve("kite.hermes.core.kite-backup").createDirectories()
            backupRoot.resolve("old-version.txt").writeText("usable")
            software.resolve("kite.hermes.core.kite-transaction").writeText(
                """
                schema=1
                phase=active
                resource_id=kite.hermes.core
                operation=update
                target_version=0.2.0
                """.trimIndent(),
            )
            val lock = software.resolve("kite.hermes.core.kite-update-lock").createDirectories()
            lock.resolve("owner").writeText("12345\nold-process-generation\n")

            val result = ResourceInstallTransactionRecovery(processStartTime = { null })
                .recover(workspace.toFile(), "kite.hermes.core")

            assertEquals(ResourceInstallRecoveryDisposition.RESTORED, result.disposition)
            assertTrue(installRoot.resolve("old-version.txt").toFile().isFile)
            assertFalse(installRoot.resolve("new-version.txt").toFile().exists())
            assertFalse(backupRoot.toFile().exists())
            assertFalse(software.resolve("kite.hermes.core.kite-transaction").toFile().exists())
            assertFalse(lock.toFile().exists())
        } finally {
            workspace.toFile().deleteRecursively()
        }
    }
}
