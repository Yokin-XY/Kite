package com.kite.app.platform.resources

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidResourceVersionRoutingContractTest {
    @Test
    fun `已安装版本只由结构化合同选路而不解析命令`() {
        val source = locate(
            "src/main/java/com/kite/app/platform/resources/AndroidResourceVersionGateway.kt",
            "app/src/main/java/com/kite/app/platform/resources/AndroidResourceVersionGateway.kt",
        ).readText()
        val installedReader = source.substringAfter("override suspend fun readInstalledVersion")
            .substringBefore("override suspend fun readLatestVersion")

        assertTrue(installedReader.contains("probe.structuredMetadata"))
        assertTrue(installedReader.contains("AndroidNativeStructuredJsonStringProvider.prepare"))
        assertTrue(installedReader.contains("fallbackToCommand"))
        assertFalse(installedReader.contains("probe.command"))
        assertFalse(installedReader.contains("split("))
        assertFalse(installedReader.contains("Regex("))
    }

    @Test
    fun `原生 Provider 不读取资源包命令或页面标识`() {
        val source = locate(
            "src/main/kotlin/com/kite/app/foundation/runtime/AndroidNativeStructuredJsonStringProvider.kt",
            "app/src/main/kotlin/com/kite/app/foundation/runtime/AndroidNativeStructuredJsonStringProvider.kt",
        ).readText()

        assertTrue(source.contains("StructuredJsonStringRequest"))
        assertTrue(source.contains("RuntimeProviderDecision.Ready"))
        assertTrue(source.contains("RuntimeProviderDecision.Unsupported"))
        assertTrue(source.contains("RuntimeProviderDecision.Blocked"))
        assertFalse(source.contains("resourceId"))
        assertFalse(source.contains("packageName"))
        assertFalse(source.contains("probe.command"))
        assertFalse(source.contains("kite.openclaw"))
    }

    private fun locate(vararg candidates: String): File = candidates
        .asSequence()
        .map(::File)
        .first(File::isFile)
}
