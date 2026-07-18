package com.kite.app.localization

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalizationResourcesTest {
    @Test
    fun `english catalog has the same string keys as the default catalog`() {
        val resourceRoot = resourceRoot()
        val defaultKeys = stringKeys(File(resourceRoot, "values/strings.xml"))
        val englishKeys = stringKeys(File(resourceRoot, "values-en/strings.xml"))

        assertEquals(
            "English is missing: ${defaultKeys - englishKeys}; English-only: ${englishKeys - defaultKeys}",
            defaultKeys,
            englishKeys
        )
    }

    @Test
    fun `automatic locale config uses simplified chinese as the unqualified locale`() {
        val properties = File(resourceRoot(), "resources.properties")

        assertTrue(properties.isFile)
        assertTrue(properties.readText().lineSequence().any {
            it.trim() == "unqualifiedResLocale=zh-CN"
        })
    }

    private fun resourceRoot(): File {
        val workingDirectory = File(System.getProperty("user.dir").orEmpty())
        return sequenceOf(
            File(workingDirectory, "src/main/res"),
            File(workingDirectory, "app/src/main/res")
        ).firstOrNull(File::isDirectory)
            ?: error("找不到 app/src/main/res，当前目录：${workingDirectory.absolutePath}")
    }

    private fun stringKeys(file: File): Set<String> {
        val document = DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(file)
        val nodes = document.getElementsByTagName("string")
        return buildSet {
            repeat(nodes.length) { index ->
                val name = nodes.item(index).attributes?.getNamedItem("name")?.nodeValue.orEmpty()
                if (name.isNotBlank()) add(name)
            }
        }
    }
}
