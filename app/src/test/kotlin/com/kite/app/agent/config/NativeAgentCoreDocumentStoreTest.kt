package com.kite.app.agent.config

import com.kite.app.foundation.contracts.ContainerRecord
import com.kite.app.foundation.contracts.ContainerStatus
import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class NativeAgentCoreDocumentStoreTest {
    private lateinit var rootfs: File
    private lateinit var store: NativeAgentCoreDocumentStore

    @Before
    fun setUp() {
        rootfs = Files.createTempDirectory("kite-core-documents").toFile()
        File(rootfs, "workspace").mkdirs()
        val projection = ContainerAgentConfigProjection {
            ContainerRecord(
                id = "test",
                displayName = "Test",
                imageName = "ubuntu",
                rootfsPath = rootfs.absolutePath,
                workspacePath = File(rootfs, "workspace").absolutePath,
                createdAt = 1L,
                status = ContainerStatus.RUNNING
            )
        }
        store = NativeAgentCoreDocumentStore(projection::resolve, AtomicConfigFileStore())
    }

    @After
    fun tearDown() {
        rootfs.deleteRecursively()
    }

    @Test
    fun descriptorsNeverCarryDocumentContents() {
        val secretRule = "never expose this document in ordinary settings"
        nativeFile("root/.agent/AGENTS.md").writeText(secretRule)

        val descriptor = store.descriptors(listOf(globalSpec())).single()

        assertTrue(descriptor.exists)
        assertFalse(descriptor.toString().contains(secretRule))
        val snapshot = requireNotNull(store.read(listOf(globalSpec()), "global-agents"))
        assertEquals(secretRule, snapshot.content)
        assertFalse(snapshot.toString().contains(secretRule))
    }

    @Test
    fun writesWithRevisionBackupAndDetectsExternalConflict() {
        val target = nativeFile("root/.agent/AGENTS.md")
        target.writeText("before")
        val specs = listOf(globalSpec())
        val before = requireNotNull(store.read(specs, "global-agents"))

        val applied = store.write(
            specs,
            AgentCoreDocumentWriteRequest(
                agentId = "agent",
                documentId = "global-agents",
                workspacePath = null,
                expectedRevision = before.revision,
                content = "after"
            )
        ) as AgentCoreDocumentWriteResult.Applied

        assertEquals("after", target.readText())
        assertNotNull(applied.backupReference)
        assertEquals("after", applied.snapshot.content)

        target.writeText("changed elsewhere")
        val conflict = store.write(
            specs,
            AgentCoreDocumentWriteRequest(
                agentId = "agent",
                documentId = "global-agents",
                workspacePath = null,
                expectedRevision = applied.snapshot.revision,
                content = "must not overwrite"
            )
        )
        assertTrue(conflict is AgentCoreDocumentWriteResult.Conflict)
        assertEquals("changed elsewhere", target.readText())
    }

    @Test
    fun projectPathsRejectTraversalAndNormalizeRepeatedSeparators() {
        assertNull(NativeAgentCoreDocumentStore.projectPath("workspace", "AGENTS.md"))
        assertNull(NativeAgentCoreDocumentStore.projectPath("/workspace/../root", "AGENTS.md"))
        assertNull(NativeAgentCoreDocumentStore.projectPath("/workspace", "../AGENTS.md"))
        assertEquals(
            "/workspace/project/AGENTS.md",
            NativeAgentCoreDocumentStore.projectPath("/workspace//project/", "AGENTS.md")
        )
    }

    @Test
    fun requestStringDoesNotExposeDocumentBody() {
        val content = "private standing instructions"
        val request = AgentCoreDocumentWriteRequest("agent", "global-agents", null, "revision", content)

        assertFalse(request.toString().contains(content))
        assertTrue(request.toString().contains("contentLength=${content.length}"))
    }

    private fun globalSpec() = NativeAgentCoreDocumentSpec(
        id = "global-agents",
        displayName = "全局说明",
        fileName = "AGENTS.md",
        containerPath = "/root/.agent/AGENTS.md",
        scope = AgentConfigScope.User,
        semantics = AgentCoreDocumentSemantics.SupplementalInstructions,
        priorityDescription = "所有会话都会读取"
    )

    private fun nativeFile(relative: String): File = File(rootfs, relative).also { it.parentFile?.mkdirs() }
}
