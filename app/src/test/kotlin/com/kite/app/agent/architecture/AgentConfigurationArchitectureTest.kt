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
            "显示层不得绕过 Agent SDK 端口：\n${violations.joinToString("\n")}",
            violations.isEmpty(),
        )
    }

    @Test
    fun `runsurface provider actions use catalog api instead of native configuration api`() {
        val source = source(
            "app/src/main/java/com/kite/app/feature/runsurface/RunAgentSurfaceBinding.kt",
        ).readText()
        val forbidden = listOf(
            "AgentConfigurationIntent.ConfigureProvider",
            "AgentConfigurationIntent.RemoveProvider",
            "AgentConfigurationIntent.SelectModel",
        )
        val violations = forbidden.filter(source::contains)

        assertTrue(
            "Provider 保存、删除和选择只能进入 AgentProviderCatalogApi：$violations",
            violations.isEmpty(),
        )
        assertTrue(
            "显示层必须依赖统一 Provider 目录端口",
            source.contains("AgentProviderCatalogApi"),
        )
        assertFalse(
            "显示层不得直接扫描 Agent 免费目录",
            source.contains("scanFreeProviderCatalog"),
        )
        assertTrue(
            "供应商页下拉刷新必须经过统一 Provider 目录端口",
            source.contains("agentProviderCatalogApi.refreshFreeProviderCatalog(target)"),
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
            "app/src/main/java/com/kite/app/agent/config/native/openclaw/OpenClawReasoningProfile.kt" to
                "openClawReasoningControl",
            "app/src/main/java/com/kite/app/agent/config/native/claudecode/ClaudeCodeReasoningProfile.kt" to
                "claudeCodeReasoningControl",
            "app/src/main/java/com/kite/app/agent/config/native/codex/CodexReasoningProfile.kt" to
                "codexReasoningControl",
            "app/src/main/java/com/kite/app/agent/config/native/hermes/HermesReasoningProfile.kt" to
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

    @Test
    fun `work mode mappings live beside their agent adapters`() {
        val profile = source(
            "app/src/main/java/com/kite/app/agent/config/opencode/OpenCodeWorkModeProfile.kt",
        )
        assertTrue("缺少 OpenCode 独立工作模式映射", profile.isFile)
        assertTrue(profile.readText().contains("openCodeWorkModeCatalog"))

        val sharedSources = kotlinSources("app/src/main/java/com/kite/app/agent/sdk/configuration") +
            kotlinSources("app/src/main/java/com/kite/app/feature/runsurface")
        val violations = sharedSources.filter { file ->
            val source = file.readText()
            source.contains("OpenCodeWorkMode") || source.contains("\"build\"") || source.contains("\"plan\"")
        }
        assertTrue(
            "公共 SDK 与显示层不得写入 OpenCode 工作模式 ID：${violations.map { it.name }}",
            violations.isEmpty(),
        )
    }

    @Test
    fun `native agent adapters remain physically split by tool`() {
        val adapterFiles = listOf(
            "app/src/main/java/com/kite/app/agent/config/native/kimi/KimiCodeAgentConfigAdapter.kt",
            "app/src/main/java/com/kite/app/agent/config/native/mimo/MiMoCodeAgentConfigAdapter.kt",
            "app/src/main/java/com/kite/app/agent/config/native/openclaw/OpenClawAgentConfigAdapter.kt",
            "app/src/main/java/com/kite/app/agent/config/native/claudecode/ClaudeCodeAgentConfigAdapter.kt",
            "app/src/main/java/com/kite/app/agent/config/native/codex/CodexAgentConfigAdapter.kt",
            "app/src/main/java/com/kite/app/agent/config/native/hermes/HermesAgentConfigAdapter.kt",
        )
        adapterFiles.forEach { path ->
            val file = source(path)
            assertTrue("缺少独立 Agent Adapter 文件：$path", file.isFile)
            assertTrue("单个 Agent Adapter 文件应继续拆分：$path", file.readLines().size < 800)
        }
        assertFalse(
            "不得恢复多个工具共用的 NativeAgentConfigAdapters.kt",
            source("app/src/main/java/com/kite/app/agent/config/native/NativeAgentConfigAdapters.kt").exists(),
        )
    }

    private fun kotlinSources(relativeDirectory: String): List<File> =
        source(relativeDirectory).walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

    private fun source(relativePath: String): File = File(repositoryRoot, relativePath)
}
