package com.kite.app.agent.architecture

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 锁住“固定显示层 -> SDK 端口 -> 兼容 Adapter”的单向依赖。 */
class AgentConfigurationArchitectureTest {
    private val repositoryRoot: File = generateSequence(
        File(requireNotNull(System.getProperty("user.dir"))),
    ) { it.parentFile }
        .first { File(it, "app/src/main/java").isDirectory }

    @Test
    fun `runsurface cannot import adapter spi or raw native mutations`() {
        val forbiddenImports = listOf(
            "com.kite.app.agent.config.AgentConfigAdapter",
            "com.kite.app.agent.config.AgentConfigAdapterRegistry",
            "com.kite.app.agent.config.AgentConfigApplyRequest",
            "com.kite.app.agent.config.AgentPersistentConfigChange",
            "com.kite.app.agent.config.AgentProviderPresetCatalog",
        )
        val violations = kotlinSources("app/src/main/java/com/kite/app/feature/runsurface")
            .flatMap { file ->
                file.readLines()
                    .filter { line -> line.trimStart().startsWith("import ") }
                    .filter { line -> forbiddenImports.any(line::contains) }
                    .map { line -> "${file.relativeTo(repositoryRoot).path}: ${line.trim()}" }
            }

        assertTrue(
            "显示层不得绕过 AgentConfigurationApi：\n${violations.joinToString("\n")}",
            violations.isEmpty(),
        )
    }

    @Test
    fun `sdk configuration and fixed controls stay product agnostic`() {
        val productTokens = listOf("OpenCode", "OpenClaw", "ClaudeCode", "Codex", "Hermes", "Kimi", "MiMo")
        val fixedUiFiles = listOf(
            source("app/src/main/java/com/kite/app/feature/runsurface/AgentFixedSessionControlStrip.kt"),
            source("app/src/main/java/com/kite/app/feature/runsurface/AgentMcpEditorPolicy.kt"),
        )
        val violations = (
            kotlinSources("app/src/main/java/com/kite/app/agent/sdk/configuration") + fixedUiFiles
        ).flatMap { file ->
            productTokens.filter { token -> file.readText().contains(token) }
                .map { token -> "${file.relativeTo(repositoryRoot).path}: $token" }
        }

        assertTrue(
            "公共 SDK 与固定控件不得包含具体工具映射：\n${violations.joinToString("\n")}",
            violations.isEmpty(),
        )
    }

    @Test
    fun `reasoning mappings live beside their agent adapters`() {
        val profiles = mapOf(
            "app/src/main/java/com/kite/app/agent/config/opencode/OpenCodeReasoningProfile.kt" to
                "openCodeReasoningControl",
            "app/src/main/java/com/kite/app/agent/config/native/OpenClawReasoningProfile.kt" to
                "openClawReasoningControl",
            "app/src/main/java/com/kite/app/agent/config/native/ClaudeCodeReasoningProfile.kt" to
                "claudeCodeReasoningControl",
            "app/src/main/java/com/kite/app/agent/config/native/CodexReasoningProfile.kt" to
                "codexReasoningControl",
            "app/src/main/java/com/kite/app/agent/config/native/HermesReasoningProfile.kt" to
                "hermesReasoningControl",
        )
        profiles.forEach { (path, declaration) ->
            val file = source(path)
            assertTrue("缺少独立推理映射文件：$path", file.isFile)
            assertTrue("推理映射声明缺失：$declaration", file.readText().contains("val $declaration"))
        }

        val shared = source("app/src/main/java/com/kite/app/agent/config/AgentReasoningControl.kt").readText()
        profiles.values.forEach { declaration ->
            assertFalse("公共推理合同不能重新收纳产品映射：$declaration", shared.contains(declaration))
        }
    }

    private fun kotlinSources(relativeDirectory: String): List<File> =
        source(relativeDirectory).walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

    private fun source(relativePath: String): File = File(repositoryRoot, relativePath)
}
