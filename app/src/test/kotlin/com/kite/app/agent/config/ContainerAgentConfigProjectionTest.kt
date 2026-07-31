package com.kite.app.agent.config

import com.kite.app.foundation.contracts.ContainerRecord
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ContainerAgentConfigProjectionTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun workspacePathsFollowRuntimeBindSourceInsteadOfRootfsDirectory() {
        val fixture = fixture()
        val projection = ContainerAgentConfigProjection { fixture.container }

        val resolved = requireNotNull(
            projection.resolve("/workspace/.kf/software/kite.hermes.core/home/config.yaml")
        )

        val expected = File(
            fixture.workspace,
            ".kf/software/kite.hermes.core/home/config.yaml",
        ).absoluteFile.normalize()
        assertEquals(expected, resolved.baseFile)
        assertEquals(expected, resolved.readFile)
        assertEquals(expected, resolved.writeFile)
        assertFalse(resolved.readFile.toPath().startsWith(fixture.rootfs.toPath()))
    }

    @Test
    fun nonWorkspacePathsKeepUsingRootfsProjection() {
        val fixture = fixture()
        val projection = ContainerAgentConfigProjection { fixture.container }

        val resolved = requireNotNull(projection.resolve("/root/.config/agent/config.json"))

        assertEquals(
            File(fixture.rootfs, "root/.config/agent/config.json").canonicalFile,
            resolved.readFile,
        )
    }

    private fun fixture(): Fixture {
        val runtime = temporaryFolder.newFolder("runtime-${System.nanoTime()}")
        val rootfs = File(runtime, "containers/ubuntu-main/rootfs").apply { mkdirs() }.canonicalFile
        val workspace = File(runtime, "shared/ubuntu-main").apply { mkdirs() }.canonicalFile
        return Fixture(
            container = ContainerRecord(
                id = "ubuntu-main",
                displayName = "Ubuntu",
                imageName = "ubuntu-base",
                rootfsPath = rootfs.absolutePath,
                workspacePath = workspace.absolutePath,
                createdAt = 1L,
            ),
            rootfs = rootfs,
            workspace = workspace,
        )
    }

    private data class Fixture(
        val container: ContainerRecord,
        val rootfs: File,
        val workspace: File,
    )
}
