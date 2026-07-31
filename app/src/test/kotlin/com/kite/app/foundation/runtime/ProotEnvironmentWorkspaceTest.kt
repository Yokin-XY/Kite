package com.kite.app.foundation.runtime

import com.kite.app.foundation.contracts.ContainerRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ProotEnvironmentWorkspaceTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun defaultKeepsLegacyWorkspaceWithoutNestedControlBind() {
        val fixture = fixture()
        val plan = ProotEnvironmentWorkspace.plan(fixture.container, fixture.binding("default"))

        assertEquals(fixture.workspace.absolutePath, plan.workspaceDirectory.absolutePath)
        assertNull(plan.sharedControlDirectory)
        assertEquals(listOf("environment_workspace"), plan.workspaceBindMounts().map { it.role })
    }

    @Test
    fun nonDefaultGetsPrivateWorkspaceAndSharedControlBindInCorrectOrder() {
        val fixture = fixture()
        val plan = ProotEnvironmentWorkspace.plan(fixture.container, fixture.binding("profile_a"))
        val mounts = plan.workspaceBindMounts()

        assertTrue(plan.workspaceDirectory.absolutePath.contains(".environments"))
        assertTrue(plan.workspaceDirectory.absolutePath.endsWith("ubuntu-main${File.separator}profile_a${File.separator}workspace"))
        assertEquals(File(fixture.workspace, ".kf").absolutePath, plan.sharedControlDirectory?.absolutePath)
        assertEquals(listOf("environment_workspace", "shared_workspace_control"), mounts.map { it.role })
        assertEquals("/workspace", mounts[0].targetPath)
        assertEquals("/workspace/.kf", mounts[1].targetPath)
        assertFalse(plan.workspaceDirectory.exists())
    }

    @Test
    fun ensureReadyCreatesOnlyPrivateWorkspaceAndRequiresExistingControlNamespace() {
        val fixture = fixture(createControl = true)
        val plan = ProotEnvironmentWorkspace.plan(fixture.container, fixture.binding("profile_b"))

        plan.ensureReady()

        assertTrue(plan.workspaceDirectory.isDirectory)
        assertTrue(File(fixture.workspace, ".kf").isDirectory)
    }

    @Test
    fun unsafeIdentityCannotEscapeEnvironmentRoot() {
        val fixture = fixture()
        assertThrows(IllegalArgumentException::class.java) {
            ProotEnvironmentWorkspace.plan(fixture.container.copy(id = "../escape"), fixture.binding("profile_a"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            ProotEnvironmentWorkspace.plan(fixture.container, fixture.binding("../escape"))
        }
    }

    private fun fixture(createControl: Boolean = false): Fixture {
        val runtime = temporaryFolder.newFolder("runtime-${System.nanoTime()}")
        val rootfs = File(runtime, "containers/ubuntu-main/rootfs").apply { mkdirs() }
        val workspace = File(runtime, "shared/ubuntu-main").apply { mkdirs() }
        if (createControl) File(workspace, ".kf").mkdirs()
        val container = ContainerRecord(
            id = "ubuntu-main",
            displayName = "Ubuntu",
            imageName = "ubuntu-base",
            rootfsPath = rootfs.absolutePath,
            workspacePath = workspace.absolutePath,
            createdAt = 1L,
        )
        return Fixture(container, workspace)
    }

    private data class Fixture(val container: ContainerRecord, val workspace: File) {
        fun binding(environmentId: String) = ProotViewBinding(
            viewId = "view-$environmentId",
            baseRootPath = workspace.parentFile!!.parentFile!!.absolutePath,
            upperRootPath = File(workspace, "upper-$environmentId").absolutePath,
            whiteoutRootPath = File(workspace, "whiteout-$environmentId").absolutePath,
            controlFilePath = File(workspace, "control-$environmentId").absolutePath,
            writable = true,
            environmentId = environmentId,
        )
    }
}
