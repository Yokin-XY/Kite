package com.kite.app.agent.config

import java.io.File
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AgentSkillImportStagerTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private fun workspace(): File = temporaryFolder.newFolder("workspace")

    @Test
    fun copiesValidSkillWithoutMovingSourceAndCanDiscardStage() {
        val workspace = workspace()
        val stager = AgentSkillImportStager(workspace)
        val source = File(workspace, "source-skill").apply { mkdirs() }
        File(source, "SKILL.md").writeText("---\nname: imported-skill\ntitle: Imported\n---\n正文")
        File(source, "references").mkdirs()
        File(source, "references/readme.md").writeText("参考")

        val stage = stager.stage("/workspace/source-skill")

        assertEquals("imported-skill", stage.skillId)
        assertTrue(stage.sourceReference.startsWith("kite-import:import-"))
        assertTrue(File(source, "SKILL.md").isFile)
        val staged = File(workspace, ".kf/imports/skills/${stage.sourceReference.removePrefix("kite-import:")}")
        assertEquals("参考", File(staged, "references/readme.md").readText())

        stage.discard()
        assertTrue(!staged.exists())
        assertTrue(source.isDirectory)
    }

    @Test
    fun rejectsWorkspaceRootReservedDirectoryAndMissingMetadata() {
        val workspace = workspace()
        val stager = AgentSkillImportStager(workspace)
        assertThrows(IllegalArgumentException::class.java) { stager.stage("/workspace") }

        val reserved = File(workspace, ".kf/manual").apply { mkdirs() }
        File(reserved, "SKILL.md").writeText("---\nname: hidden\n---")
        assertThrows(IllegalArgumentException::class.java) { stager.stage("/workspace/.kf/manual") }

        File(workspace, "missing").mkdirs()
        assertThrows(IllegalArgumentException::class.java) { stager.stage("/workspace/missing") }
    }

    @Test
    fun rejectsUnsafeMetadataAndDoesNotCreateImportCopy() {
        val workspace = workspace()
        val stager = AgentSkillImportStager(workspace)
        val source = File(workspace, "unsafe").apply { mkdirs() }
        File(source, "SKILL.md").writeText("---\nname: ../escape\n---")

        assertThrows(IllegalArgumentException::class.java) { stager.stage("/workspace/unsafe") }
        assertTrue(!File(workspace, ".kf/imports/skills").exists())
    }

    @Test
    fun stagesOneSkillFromZipAndRejectsTraversal() {
        val workspace = workspace()
        val stager = AgentSkillImportStager(workspace)
        val valid = zipOf(
            "demo/SKILL.md" to "---\nname: demo-skill\ndescription: Demo\n---\n正文",
            "demo/references/readme.md" to "参考",
        )

        val stage = stager.stageArchive(ByteArrayInputStream(valid))

        assertEquals("demo-skill", stage.skillId)
        stage.discard()
        assertThrows(IllegalArgumentException::class.java) {
            stager.stageArchive(ByteArrayInputStream(zipOf("../escape/SKILL.md" to "---\nname: escape\n---")))
        }
        assertTrue(!File(workspace.parentFile, "escape").exists())
    }

    private fun zipOf(vararg files: Pair<String, String>): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { archive ->
            files.forEach { (path, content) ->
                archive.putNextEntry(ZipEntry(path))
                archive.write(content.toByteArray())
                archive.closeEntry()
            }
        }
        return output.toByteArray()
    }
}
