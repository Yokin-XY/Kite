package com.kite.app.platform.resources

import com.kite.app.resources.KiteResourceInstallRecipes
import com.kite.app.resources.ResourceInstallRecoveryDisposition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ResourceInstallCandidateCoordinatorTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `不同资源拥有独立候选目录且提交不会覆盖另一项`() {
        val workspace = temporaryFolder.newFolder("workspace")
        val softwareRoot = File(workspace, ".kf/software").apply { mkdirs() }
        val binRoot = File(workspace, ".kf/bin").apply { mkdirs() }
        val first = "kite.hermes.core"
        val second = "kite.git"
        seedInstall(softwareRoot, binRoot, first, "hermes", "old-hermes")
        seedInstall(softwareRoot, binRoot, second, "git", "old-git")
        val coordinator = ResourceInstallCandidateCoordinator()

        val blockedGuard = ResourceInstallCapacityGuard(
            availableBytes = { 80L },
            directoryBytes = { 64L },
            minimumWorkingBytes = 64L,
            safetyReserveBytes = 32L,
        )
        assertThrows(IllegalStateException::class.java) {
            blockedGuard.requireCapacity(workspace, first, declaredWorkingBytes = 0L)
        }
        assertEquals("old-hermes", File(softwareRoot, "$first/version.txt").readText())
        assertFalse(File(softwareRoot, ".kite-pending").exists())

        coordinator.begin(
            workspace,
            first,
            "run-hermes",
            KiteResourceInstallRecipes.softwarePath(first),
            emptyList(),
        ).getOrThrow()
        coordinator.begin(
            workspace,
            second,
            "run-git",
            KiteResourceInstallRecipes.softwarePath(second),
            emptyList(),
        ).getOrThrow()

        val firstEnvironment = coordinator.environmentForRun("run-hermes")
        val secondEnvironment = coordinator.environmentForRun("run-git")
        assertEquals("1", firstEnvironment.variables[ResourceInstallCandidateCoordinator.CANDIDATE_ENV])
        val firstCandidate = File(firstEnvironment.filesystemBindings.first().sourcePath)
        val secondCandidate = File(secondEnvironment.filesystemBindings.first().sourcePath)
        assertTrue(firstCandidate.absolutePath != secondCandidate.absolutePath)
        File(firstCandidate, "version.txt").writeText("new-hermes")
        File(secondCandidate, "version.txt").writeText("new-git")
        File(firstCandidate, ".kite-managed-commands").writeText(
            "hermes\t${KiteResourceInstallRecipes.softwarePath(first)}/bin/hermes\n"
        )
        val firstCandidateBin = File(firstEnvironment.filesystemBindings.last().sourcePath)
        File(firstCandidateBin, "hermes").writeText("new-hermes-command")

        coordinator.markInstalling(first, "run-hermes").getOrThrow()
        coordinator.markVerified(first, "run-hermes").getOrThrow()
        coordinator.commit(first, "run-hermes").getOrThrow()

        assertEquals("new-hermes", File(softwareRoot, "$first/version.txt").readText())
        assertEquals("old-git", File(softwareRoot, "$second/version.txt").readText())
        assertEquals("new-hermes-command", File(binRoot, "hermes").readText())
        assertTrue(secondCandidate.exists())

        coordinator.finalize(first, "run-hermes").getOrThrow()
        coordinator.rollback(second, "run-git").getOrThrow()
        assertEquals("old-git", File(softwareRoot, "$second/version.txt").readText())
        assertFalse(secondCandidate.exists())
    }

    @Test
    fun `正式根切换后进程中断会在下次启动恢复旧版本`() {
        val workspace = temporaryFolder.newFolder("recovery-workspace")
        val softwareRoot = File(workspace, ".kf/software").apply { mkdirs() }
        val binRoot = File(workspace, ".kf/bin").apply { mkdirs() }
        val resourceId = "kite.hermes.core"
        seedInstall(softwareRoot, binRoot, resourceId, "hermes", "old-hermes")
        val interrupted = ResourceInstallCandidateCoordinator { checkpoint ->
            if (checkpoint == ResourceInstallCandidateCoordinator.CHECKPOINT_ROOT_ACTIVATED) {
                throw SimulatedProcessDeath()
            }
        }
        interrupted.begin(
            workspaceDirectory = workspace,
            resourceId = resourceId,
            runInstanceId = "run-interrupted",
            guestInstallRoot = KiteResourceInstallRecipes.softwarePath(resourceId),
            preservePaths = emptyList(),
            operation = KiteResourceInstallRecipes.OP_UPDATE,
            targetVersion = "2.0.0",
            previousVersion = "1.0.0",
        ).getOrThrow()
        val environment = interrupted.environmentForRun("run-interrupted")
        val candidateRoot = File(environment.filesystemBindings.first().sourcePath)
        val candidateBin = File(environment.filesystemBindings.last().sourcePath)
        File(candidateRoot, "version.txt").writeText("new-hermes")
        File(candidateRoot, ".kite-managed-commands").writeText(
            "hermes\t${KiteResourceInstallRecipes.softwarePath(resourceId)}/bin/hermes\n"
        )
        File(candidateBin, "hermes").writeText("new-hermes-command")
        interrupted.markInstalling(resourceId, "run-interrupted").getOrThrow()
        interrupted.markVerified(resourceId, "run-interrupted").getOrThrow()

        val commit = interrupted.commit(resourceId, "run-interrupted")
        assertTrue(commit.exceptionOrNull() is SimulatedProcessDeath)
        assertEquals("new-hermes", File(softwareRoot, "$resourceId/version.txt").readText())

        val recovery = ResourceInstallCandidateCoordinator()
            .recoverInterrupted(workspace)
            .single()

        assertEquals(ResourceInstallRecoveryDisposition.RESTORED, recovery.disposition)
        assertEquals("old-hermes", File(softwareRoot, "$resourceId/version.txt").readText())
        assertEquals("old-hermes-command", File(binRoot, "hermes").readText())
        assertFalse(File(softwareRoot, ".kite-pending").exists())
    }

    private fun seedInstall(
        softwareRoot: File,
        binRoot: File,
        resourceId: String,
        command: String,
        version: String,
    ) {
        val installRoot = File(softwareRoot, resourceId).apply { mkdirs() }
        File(installRoot, "version.txt").writeText(version)
        File(installRoot, ".kite-managed-commands").writeText(
            "$command\t${KiteResourceInstallRecipes.softwarePath(resourceId)}/bin/$command\n"
        )
        File(binRoot, command).writeText("old-$command-command")
    }

    private class SimulatedProcessDeath : Error("simulated_process_death")
}
