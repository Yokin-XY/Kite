package com.kite.app.resources

import android.content.Context
import android.graphics.BitmapFactory
import androidx.test.core.app.ApplicationProvider
import com.kite.app.agent.registration.AgentLaunchSpec
import com.kite.app.agent.registration.AgentRegistrationSource
import com.kite.app.agent.registration.AgentResourceRegistrationMapper
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class KiteResourceManifestProtocolTest {

    @Test
    fun everyNpmResourceRequestsLatestAndVerifiesTheSignedIntegrityWindow() {
        val npmManifests = resourceRoot().listFiles().orEmpty()
            .mapNotNull { directory ->
                File(directory, "manifest.json").takeIf(File::isFile)
            }
            .map { file -> KiteResourceManifestLoader(context).parseManifestJson(file.readText()) }
            .filter { manifest -> manifest.sourceType == "npm" }

        assertEquals(0, npmManifests.size)
        npmManifests.forEach { manifest ->
            assertTrue("${manifest.id} has no signed source window", manifest.source.latestVersionWindow.isNotEmpty())
            val plan = KiteResourceSourcePlanFactory.plan(manifest)
            val script = plan.installActions.joinToString("\n") { action ->
                KiteResourceInstallPlanCompiler.compile(action)
            }
            assertTrue("${manifest.id} does not request latest", script.contains("request=latest"))
            assertTrue("${manifest.id} does not verify package integrity", script.contains("dist.integrity"))
            assertTrue("${manifest.id} does not reject an out-of-window source", script.contains("source-unverified"))
        }
    }
    private val context by lazy { ApplicationProvider.getApplicationContext<Context>() }

    @Test
    fun openCodeRegistersReusableAcpStdioProfileAndAgentRecipe() {
        val manifestFile = File(resourceRoot(), "kite.opencode/manifest.json")
        val manifest = KiteResourceManifestLoader(context).parseManifestJson(manifestFile.readText())
        val profile = manifest.agentProfiles.single()

        assertEquals("opencode", profile.agentId)
        assertEquals("OpenCode", profile.displayName)
        assertEquals("managed", profile.launchMode)
        assertEquals("opencode", profile.providerId)
        assertEquals("acp", profile.protocol)
        assertEquals("stdio", profile.transport)
        assertEquals(listOf("opencode", "acp"), profile.argv)
        assertEquals(45_000L, profile.initializeTimeoutMs)
        assertEquals("opencode", profile.configAdapterId)
        assertEquals("opencode", profile.sessionAdapterId)
        val registration = AgentResourceRegistrationMapper.registrations(manifest).single()
        assertEquals("opencode", registration.definition.agentId)
        assertEquals(AgentRegistrationSource.Resource("kite.opencode"), registration.source)
        assertEquals("opencode", registration.configAdapterId)
        assertEquals("opencode", registration.sessionAdapterId)
        assertTrue(registration.launch is AgentLaunchSpec.Managed)

        val openStep = manifest.openRecipe?.optJSONArray("recipe")?.optJSONObject(0)
        val homeStep = manifest.homeCards.single().recipe.optJSONArray("recipe")?.optJSONObject(0)
        assertEquals("agent", openStep?.optString("type"))
        assertEquals("opencode", openStep?.optString("agentId"))
        assertFalse(openStep?.has("providerId") == true)
        assertEquals("/workspace", openStep?.optString("workdir"))
        assertEquals(openStep?.toString(), homeStep?.toString())
        assertFalse(openStep?.has("cmd") == true)
        assertFalse(openStep?.has("text") == true)
    }

    @Test
    fun githubCopilotRunsOfficialCommandInstall() {
        val manifestFile = File(resourceRoot(), "kite.github.copilot/manifest.json")
        val manifest = KiteResourceManifestLoader(context).parseManifestJson(manifestFile.readText())
        val plan = KiteResourceSourcePlanFactory.plan(manifest)
        assertEquals("official_command", manifest.sourceType)
        assertEquals(KiteResourceInstallPlanCompiler.STEP_SHELL, plan.installActions.single().type)
    }

    @Test
    fun kimiCodeUsesTheSameAcpRegistrationAndAgentRecipeContract() {
        val manifestFile = File(resourceRoot(), "kite.kimi.code/manifest.json")
        val manifest = KiteResourceManifestLoader(context).parseManifestJson(manifestFile.readText())
        val profile = manifest.agentProfiles.single()

        assertEquals("kimi", profile.agentId)
        assertEquals("Kimi Code", profile.displayName)
        assertEquals("managed", profile.launchMode)
        assertEquals("kimi", profile.providerId)
        assertEquals("acp", profile.protocol)
        assertEquals("stdio", profile.transport)
        assertEquals(listOf("kimi", "acp"), profile.argv)
        assertFalse(profile.configurationRequired)
        assertEquals("kimi-code", profile.configAdapterId)

        val registration = AgentResourceRegistrationMapper.registrations(manifest).single()
        assertEquals("kimi", registration.definition.agentId)
        assertEquals(AgentRegistrationSource.Resource("kite.kimi.code"), registration.source)
        assertEquals("kimi-code", registration.configAdapterId)
        assertTrue(registration.launch is AgentLaunchSpec.Managed)

        val openStep = manifest.openRecipe?.optJSONArray("recipe")?.optJSONObject(0)
        val homeStep = manifest.homeCards.single().recipe.optJSONArray("recipe")?.optJSONObject(0)
        assertEquals("agent", openStep?.optString("type"))
        assertEquals("kimi", openStep?.optString("agentId"))
        assertFalse(openStep?.has("providerId") == true)
        assertEquals("/workspace", openStep?.optString("workdir"))
        assertEquals(openStep?.toString(), homeStep?.toString())
        assertFalse(openStep?.has("cmd") == true)
        assertFalse(openStep?.has("text") == true)
    }

    @Test
    fun resourceCanDeclareMultipleAgentsAndLegacySingleAgentStillLoads() {
        val loader = KiteResourceManifestLoader(context)
        val multiple = loader.parseManifestJson(
            """
                {
                  "id": "kite.multi-agent",
                  "base": {"name": "多 Agent 资源"},
                  "agents": [
                    {
                      "id": "same-name-a",
                      "name": "同名 Agent",
                      "launch": {
                        "mode": "managed",
                        "providerId": "provider-a",
                        "protocol": "acp",
                        "transport": "stdio",
                        "argv": ["agent-a", "acp"]
                      }
                    },
                    {
                      "id": "same-name-b",
                      "name": "同名 Agent",
                      "configuration": {"required": true, "adapter": "shared-adapter"},
                      "launch": {
                        "mode": "attach",
                        "providerId": "provider-b",
                        "protocol": "acp",
                        "transport": "socket",
                        "connectionReference": "connections/provider-b"
                      }
                    }
                  ]
                }
            """.trimIndent()
        )
        val legacy = loader.parseManifestJson(
            """
                {
                  "id": "kite.legacy-agent",
                  "base": {"name": "旧资源"},
                  "agent": {
                    "providerId": "legacy-agent",
                    "protocol": "acp",
                    "transport": "stdio",
                    "argv": ["legacy", "acp"],
                    "title": "旧 Agent"
                  }
                }
            """.trimIndent()
        )

        assertEquals(listOf("same-name-a", "same-name-b"), multiple.agentProfiles.map { it.agentId })
        assertEquals(listOf("managed", "attach"), multiple.agentProfiles.map { it.launchMode })
        assertTrue(multiple.agentProfiles.last().configurationRequired)
        assertEquals("shared-adapter", multiple.agentProfiles.last().configAdapterId)
        assertEquals("legacy-agent", legacy.agentProfiles.single().agentId)
        assertEquals("旧 Agent", legacy.agentProfiles.single().displayName)
    }

    @Test
    fun runtimeGuaranteesAreClosedEnumsForAgentAndBackgroundDeclarations() {
        val loader = KiteResourceManifestLoader(context)
        val valid = loader.parseManifestJson(
            """
                {
                  "id": "kite.python-fixture",
                  "base": {"name": "Python Fixture"},
                  "agents": [{
                    "id": "python-fixture",
                    "name": "Python Fixture",
                    "launch": {
                      "mode": "managed",
                      "providerId": "python-fixture",
                      "protocol": "acp",
                      "transport": "stdio",
                      "argv": ["python3", "fixture.py"],
                      "runtimeGuarantees": ["NO_CHILD_PROCESS", "verified_native_imports"],
                      "runtimeGuaranteeEvidence": {"pythonAbi": "CPYTHON-314-AARCH64-LINUX-GNU"},
                      "runtimeDependencies": [{
                        "id": "python-background",
                        "argv": ["python3", "background.py"],
                        "runtimeGuarantees": ["no_child_process", "verified_native_imports"],
                        "runtimeGuaranteeEvidence": {"pythonAbi": "cpython-314-aarch64-linux-gnu"}
                      }]
                    }
                  }]
                }
            """.trimIndent()
        )
        val invalid = loader.parseManifestJson(
            """
                {
                  "id": "kite.python-invalid",
                  "base": {"name": "Python Invalid"},
                  "agents": [{
                    "id": "python-invalid",
                    "name": "Python Invalid",
                    "launch": {
                      "mode": "managed",
                      "providerId": "python-invalid",
                      "protocol": "acp",
                      "transport": "stdio",
                      "argv": ["python3", "fixture.py"],
                      "runtimeGuarantees": ["trust_me"]
                    }
                  }]
                }
            """.trimIndent()
        )
        val invalidEvidence = loader.parseManifestJson(
            """
                {
                  "id": "kite.python-invalid-evidence",
                  "base": {"name": "Python Invalid Evidence"},
                  "agents": [{
                    "id": "python-invalid-evidence",
                    "name": "Python Invalid Evidence",
                    "launch": {
                      "mode": "managed",
                      "providerId": "python-invalid-evidence",
                      "protocol": "acp",
                      "transport": "stdio",
                      "argv": ["python3", "fixture.py"],
                      "runtimeGuarantees": ["no_child_process", "verified_native_imports"],
                      "runtimeGuaranteeEvidence": {"package": "trusted"}
                    }
                  }]
                }
            """.trimIndent()
        )

        val profile = valid.agentProfiles.single()
        assertEquals(setOf("no_child_process", "verified_native_imports"), profile.runtimeGuarantees)
        assertEquals(
            mapOf("pythonAbi" to "cpython-314-aarch64-linux-gnu"),
            profile.runtimeGuaranteeEvidence,
        )
        assertEquals(profile.runtimeGuarantees, profile.runtimeDependencies.single().runtimeGuarantees)
        assertEquals(
            profile.runtimeGuaranteeEvidence,
            profile.runtimeDependencies.single().runtimeGuaranteeEvidence,
        )
        assertEquals(
            profile.runtimeGuarantees,
            (AgentResourceRegistrationMapper.registrations(valid).single().launch as AgentLaunchSpec.Managed)
                .runtimeGuarantees,
        )
        assertTrue(invalid.agentProfiles.isEmpty())
        assertTrue(invalidEvidence.agentProfiles.isEmpty())
    }

    @Test
    fun codexRunsOfficialCommandInstallWithVersionSignal() {
        val manifestFile = File(resourceRoot(), "kite.codex.cli/manifest.json")
        val manifest = KiteResourceManifestLoader(context).parseManifestJson(manifestFile.readText())
        val sourcePlan = KiteResourceSourcePlanFactory.plan(manifest)
        val installAction = sourcePlan.installActions.single()
        val uninstallAction = sourcePlan.uninstallActions.single()

        assertEquals("official_command", manifest.sourceType)
        assertTrue(manifest.installActions.isEmpty())
        assertTrue(sourcePlan.generatedFromSource)
        assertTrue(sourcePlan.capabilities.update)
        assertEquals(listOf("kite.nodejs", "kite.git", "kite.codex.relay"), manifest.baseRequirements)
        assertEquals(KiteResourceInstallPlanCompiler.STEP_SHELL, installAction.type)
        assertTrue(installAction.cmd.contains("npm install -g @openai/codex@latest"))
        assertTrue(
            "安装命令尾部必须输出 versionProbe 首行作为记账信号",
            installAction.cmd.contains("KITE_RESOURCE_INSTALLED_VERSION") &&
                installAction.cmd.contains("codex --version"),
        )
        assertTrue(
            "官方直装不得引用小房间 install_root",
            installAction.cmd.contains("$" + "install_root").not(),
        )
        assertTrue(installAction.verifications.any { it.cmd.contains("codex --version") })
        assertEquals(KiteResourceInstallPlanCompiler.STEP_SHELL, uninstallAction.type)
        assertTrue(uninstallAction.cmd.contains("npm uninstall -g @openai/codex"))
        val profile = manifest.agentProfiles.single()
        assertTrue(profile.runtimeDependencies.isEmpty())
        assertEquals("codex-app-server", profile.protocol)
        assertEquals(listOf("kite-codex-app-server"), profile.argv)
        val openStep = manifest.openRecipe?.optJSONArray("recipe")?.optJSONObject(0)
        assertEquals("agent", openStep?.optString("type"))
        assertEquals("codex", openStep?.optString("agentId"))
        assertEquals(openStep?.toString(), manifest.homeCards.single().recipe.optJSONArray("recipe")?.optJSONObject(0)?.toString())
    }

    @Test
    fun antigravityUsesDedicatedStreamJsonRegistrationAndAgentRecipe() {
        val manifestFile = File(resourceRoot(), "kite.google.antigravity/manifest.json")
        val manifest = KiteResourceManifestLoader(context).parseManifestJson(manifestFile.readText())
        val profile = manifest.agentProfiles.single()

        assertEquals("antigravity", profile.agentId)
        assertEquals("antigravity", profile.providerId)
        assertEquals("antigravity-stream-json", profile.protocol)
        assertEquals("stdio", profile.transport)
        assertEquals(listOf("agy"), profile.argv)
        assertEquals(60_000L, profile.initializeTimeoutMs)
        assertEquals("google-antigravity", profile.configAdapterId)
        assertFalse(profile.configurationRequired)
        assertEquals("1.1.22", manifest.version)
        assertEquals("version", manifest.source.latestJsonField)
        assertEquals(3, manifest.source.latestVersionWindow.size)
        val install = KiteResourceSourcePlanFactory.plan(manifest).installActions.single()
        assertEquals(
            KiteResourceInstallPlanCompiler.STEP_LATEST_DOWNLOAD,
            install.installSteps.first().type,
        )
        assertEquals("archive", install.installSteps[1].type)

        val registration = AgentResourceRegistrationMapper.registrations(manifest).single()
        assertEquals("antigravity", registration.definition.agentId)
        assertEquals(AgentRegistrationSource.Resource("kite.google.antigravity"), registration.source)
        assertEquals("google-antigravity", registration.configAdapterId)
        assertTrue(registration.launch is AgentLaunchSpec.Managed)

        val openStep = manifest.openRecipe?.optJSONArray("recipe")?.optJSONObject(0)
        val homeStep = manifest.homeCards.single().recipe.optJSONArray("recipe")?.optJSONObject(0)
        assertEquals("agent", openStep?.optString("type"))
        assertEquals("antigravity", openStep?.optString("agentId"))
        assertEquals("/workspace", openStep?.optString("workdir"))
        assertEquals(openStep?.toString(), homeStep?.toString())
    }

    @Test
    fun codexRelayUsesIsolatedUvToolInstallation() {
        val manifestFile = File(resourceRoot(), "kite.codex.relay/manifest.json")
        val manifest = KiteResourceManifestLoader(context).parseManifestJson(manifestFile.readText())
        val sourcePlan = KiteResourceSourcePlanFactory.plan(manifest)
        val installAction = sourcePlan.installActions.single()
        val installRelayStep = installAction.installSteps.single { it.id == "install-codex-relay" }
        val installLauncherStep = installAction.installSteps.single { it.id == "install-kite-codex-launcher" }
        val installAppServerStep = installAction.installSteps.single { it.id == "install-kite-codex-app-server" }

        assertEquals("pypi", manifest.sourceType)
        assertEquals(listOf("kite.python", "kite.uv"), manifest.baseRequirements)
        assertEquals(
            listOf("codex-relay", "kite-codex-acp", "kite-codex-app-server"),
            manifest.management.managedCommands,
        )
        assertEquals(manifest.management.managedCommands, installAction.managedCommands)
        assertEquals(KiteResourceInstallPlanCompiler.STEP_PYPI, installRelayStep.type)
        assertEquals(listOf("codex-relay"), installRelayStep.packages)
        assertEquals(listOf("0.5.8", "0.5.7", "0.5.6"), installRelayStep.latestVersionWindow.map { it.version })
        assertTrue(installRelayStep.latestVersionWindow.all { it.sha256.length == 64 })
        val installScript = KiteResourceInstallPlanCompiler.compile(installAction)
        assertTrue(installScript.contains("request=latest"))
        assertTrue(installScript.contains("latest-version-outside-window"))
        assertTrue(installScript.contains("timeout 300 env UV_DEFAULT_INDEX=\"\$pypi_index\""))
        assertTrue(installScript.contains("artifact-sha256-mismatch"))
        assertTrue(installLauncherStep.cmd.contains("codex-relay --bind 127.0.0.1 --port 4453"))
        assertTrue(installLauncherStep.cmd.contains("codex-acp \"\$@\""))
        assertTrue(installLauncherStep.cmd.contains("/dev/tcp/127.0.0.1/4453"))
        assertTrue(installLauncherStep.cmd.contains("KITE_CODEX_UPSTREAM=\"\$upstream\""))
        assertTrue(installLauncherStep.cmd.contains("require('https')"))
        assertFalse(installLauncherStep.cmd.contains("rejectUnauthorized: false"))
        assertTrue(installAppServerStep.cmd.contains("codex app-server \"\$@\""))
        assertTrue(installAppServerStep.cmd.contains("codex-relay --bind 127.0.0.1 --port 4453"))
        assertTrue(installAction.verifications.single().cmd.contains("codex-relay --version"))
        assertTrue(sourcePlan.capabilities.install)
        assertTrue(sourcePlan.capabilities.uninstall)
    }

    @Test
    fun openClawOpensRegisteredAgentSurface() {
        val manifestFile = File(resourceRoot(), "kite.openclaw/manifest.json")
        val manifest = KiteResourceManifestLoader(context).parseManifestJson(manifestFile.readText())
        val plan = KiteResourceSourcePlanFactory.plan(manifest)
        assertEquals("official_command", manifest.sourceType)
        assertEquals(KiteResourceInstallPlanCompiler.STEP_SHELL, plan.installActions.single().type)
        assertTrue(manifest.agentProfiles.isNotEmpty())
    }

    @Test
    fun claudeCodeRunsOfficialCommandInstallWithVersionSignal() {
        val manifestFile = File(resourceRoot(), "kite.claude.code/manifest.json")
        val manifest = KiteResourceManifestLoader(context).parseManifestJson(manifestFile.readText())
        val sourcePlan = KiteResourceSourcePlanFactory.plan(manifest)
        val installAction = sourcePlan.installActions.single()
        assertEquals("official_command", manifest.sourceType)
        assertEquals(KiteResourceInstallPlanCompiler.STEP_SHELL, installAction.type)
        assertTrue(installAction.cmd.contains("npm install -g @anthropic-ai/claude-code@latest"))
        assertTrue(installAction.cmd.contains("KITE_RESOURCE_INSTALLED_VERSION"))
    }

    @Test
    fun mainstreamAgentResourcesExposeManagedSdkProfilesAndAgentCards() {
        // official_command 资源不再 pin 具体版本；只验证协议身份。
        val loader = KiteResourceManifestLoader(context)
        val agentResourceIds = listOf(
            "kite.claude.code", "kite.codex.cli", "kite.opencode",
            "kite.openclaw", "kite.mimo.code",
        )
        agentResourceIds.forEach { rid ->
            val manifest = loader.parseManifestJson(
                File(resourceRoot(), "$rid/manifest.json").readText()
            )
            assertTrue("$rid must have agent profiles", manifest.agentProfiles.isNotEmpty())
        }
    }

    @Test
    fun officialAccountActionsStayInAgentRegistrationWithoutCredentials() {
        data class Expected(
            val resourceId: String,
            val agentId: String,
            val accountId: String,
            val accountName: String,
            val statusArgv: List<String>?,
            val loginArgv: List<String>,
            val logoutArgv: List<String>?,
        )

        listOf(
            Expected(
                "kite.codex.cli",
                "codex",
                "chatgpt",
                "ChatGPT 官方",
                listOf("codex", "login", "status"),
                listOf("codex", "login"),
                listOf("codex", "logout"),
            ),
            Expected(
                "kite.claude.code",
                "claude-code",
                "anthropic",
                "Anthropic 官方",
                listOf("claude", "auth", "status"),
                listOf("claude", "auth", "login"),
                listOf("claude", "auth", "logout"),
            ),
            Expected(
                "kite.hermes.core",
                "hermes",
                "nous",
                "Nous Portal 官方",
                listOf("hermes", "portal", "info"),
                listOf("hermes", "portal", "login"),
                listOf("hermes", "auth", "logout", "nous"),
            ),
            Expected(
                "kite.kimi.code",
                "kimi",
                "moonshot",
                "Kimi 官方",
                null,
                listOf("kimi", "login"),
                null,
            ),
        ).forEach { expected ->
            val raw = File(resourceRoot(), "${expected.resourceId}/manifest.json").readText()
            val manifest = KiteResourceManifestLoader(context).parseManifestJson(raw)
            val profile = manifest.agentProfiles.single()
            val registration = AgentResourceRegistrationMapper.registrations(manifest).single()
            val account = profile.officialAccounts.single()
            val registered = registration.officialAccounts.single()

            assertEquals(expected.agentId, profile.agentId)
            assertEquals(expected.accountId, account.id)
            assertEquals(expected.accountName, account.displayName)
            assertEquals(expected.statusArgv, account.status?.argv)
            assertEquals(expected.loginArgv, account.login.argv)
            assertEquals(expected.logoutArgv, account.logout?.argv)
            assertEquals(account.id, registered.id)
            assertEquals(account.status?.argv, registered.status?.argv)
            assertFalse(raw.contains("access_token", ignoreCase = true))
            assertFalse(raw.contains("refresh_token", ignoreCase = true))
        }
    }

    @Test
    fun reasonixRunsOfficialCommandInstall() {
        val manifestFile = File(resourceRoot(), "kite.reasonix/manifest.json")
        val manifest = KiteResourceManifestLoader(context).parseManifestJson(manifestFile.readText())
        val plan = KiteResourceSourcePlanFactory.plan(manifest)
        assertEquals("official_command", manifest.sourceType)
        assertEquals(KiteResourceInstallPlanCompiler.STEP_SHELL, plan.installActions.single().type)
    }

    @Test
    fun deepSeekHarnessRunsOfficialCommandInstall() {
        val manifestFile = File(resourceRoot(), "kite.deepseek.harness/manifest.json")
        val manifest = KiteResourceManifestLoader(context).parseManifestJson(manifestFile.readText())
        val plan = KiteResourceSourcePlanFactory.plan(manifest)
        assertEquals("official_command", manifest.sourceType)
        assertEquals(KiteResourceInstallPlanCompiler.STEP_SHELL, plan.installActions.single().type)
    }

    @Test
    fun `首页只定义版面并由各 manifest 投影当前顺序和标签页`() {
        val debugLayout = KiteResourceManifestLoader(context, isDebugBuild = true).requestHomeLayout()
        val releaseLayout = KiteResourceManifestLoader(context, isDebugBuild = false).requestHomeLayout()

        val recommendedSections = debugLayout?.tabs?.first { it.id == "recommended" }?.sections.orEmpty()
        assertEquals(
            listOf(
                "kite.codex.cli",
                "kite.claude.code",
                "kite.gemini.cli",
                "kite.opencode",
                "kite.hermes.core",
                "kite.openclaw",
                "kite.zcode",
                "kite.deepseek.harness"
            ),
            recommendedSections.take(2).flatMap(KiteResourceHomeSection::items)
        )
        assertEquals(listOf("shelf", "shelf", "list", "list"), recommendedSections.map(KiteResourceHomeSection::style))
        assertEquals(
            listOf(
                "kite.opencode",
                "kite.openclaw",
                "kite.reasonix",
                "kite.pi.coding.agent",
                "kite.hermes.core"
            ),
            debugLayout?.sections?.first { it.id == "ai-community" }?.items
        )
        assertEquals(
            listOf(
                "kite.codex.cli",
                "kite.claude.code",
                "kite.github.copilot",
                "kite.kimi.code",
                "kite.gemini.cli",
                "kite.qwen.code",
                "kite.google.antigravity",
                "kite.qoder.cli",
                "kite.trae.code",
                "kite.codebuddy.code",
                "kite.cursor.cli",
                "kite.zcode",
                "kite.devin.cli",
                "kite.deepseek.harness",
                "kite.mimo.code"
            ),
            debugLayout?.sections?.first { it.id == "ai-vendor" }?.items
        )
        assertEquals(
            listOf(
                "kite.nodejs",
                "kite.python",
                "kite.git",
                "kite.uv",
                "kite.curl",
                "kite.codex.relay",
            ),
            debugLayout?.sections?.first { it.id == "foundation" }?.items
        )
        assertEquals(
            listOf("kite.shizuku"),
            debugLayout?.sections?.first { it.id == "more" }?.items,
        )
        assertEquals(
            listOf(
                "kite.opencode",
                "kite.openclaw",
                "kite.reasonix",
                "kite.pi.coding.agent",
                "kite.hermes.core",
                "kite.codex.cli",
                "kite.claude.code",
                "kite.github.copilot",
                "kite.kimi.code",
                "kite.gemini.cli",
                "kite.qwen.code",
                "kite.google.antigravity",
                "kite.qoder.cli",
                "kite.trae.code",
                "kite.codebuddy.code",
                "kite.cursor.cli",
                "kite.zcode",
                "kite.devin.cli",
                "kite.deepseek.harness",
                "kite.mimo.code"
            ),
            debugLayout?.tabs?.first { it.id == "angel-cli" }?.sections?.single()?.items
        )
        assertEquals(
            listOf(
                "kite.nodejs",
                "kite.python",
                "kite.git",
                "kite.uv",
                "kite.curl",
                "kite.codex.relay",
            ),
            debugLayout?.tabs?.first { it.id == "foundation" }?.sections?.single()?.items
        )
        assertEquals(
            "plain-list",
            debugLayout?.tabs?.first { it.id == "angel-cli" }?.sections?.single()?.style
        )
        assertEquals(
            "plain-list",
            debugLayout?.tabs?.first { it.id == "foundation" }?.sections?.single()?.style
        )
        assertEquals(debugLayout?.sections, releaseLayout?.sections)
        assertEquals(debugLayout?.tabs, releaseLayout?.tabs)
    }

    @Test
    fun everyResourceResolvesToManagedInstallProtocol() {
        val loader = KiteResourceManifestLoader(context)
        val generatedScripts = File("build/generated-resource-install-scripts").also {
            it.deleteRecursively()
            it.mkdirs()
        }
        val manifests = resourceRoot().listFiles().orEmpty()
            .map { File(it, "manifest.json") }
            .filter { it.isFile }
            .sortedBy { it.parentFile?.name }

        assertEquals(28, manifests.size)
        manifests.forEach { manifestFile ->
            val resourceId = manifestFile.parentFile?.name.orEmpty()
            val loaded = loader.parseManifestJson(manifestFile.readText())
            val sourcePlan = KiteResourceSourcePlanFactory.plan(loaded)
            assertNotNull("Manifest did not load: $resourceId", loaded)
            assertEquals(resourceId, loaded.id)
            assertTrue("No resolved install action: $resourceId", sourcePlan.installActions.isNotEmpty())
            if (loaded.management.userLifecycleEnabled && loaded.source.type != "android_apk") {
                assertTrue("No resolved uninstall action: $resourceId", sourcePlan.uninstallActions.isNotEmpty())
            }
            sourcePlan.installActions.forEach { action ->
                if (loaded.source.type == "official_command") {
                    // official_command：直跑官方命令，不经过 managed 编译器与网络获取层。
                    assertEquals(
                        "official_command must run as shell: $resourceId",
                        KiteResourceInstallPlanCompiler.STEP_SHELL,
                        action.type,
                    )
                    assertTrue("official_command has empty cmd: $resourceId", action.cmd.isNotBlank())
                    return@forEach
                }
                assertEquals("Legacy install action remains: $resourceId", KiteResourceInstallPlanCompiler.ACTION_MANAGED, action.type)
                assertTrue("Managed action has no steps: $resourceId", action.installSteps.isNotEmpty())
                assertTrue(
                    "Managed action has no success contract: $resourceId",
                    action.managedCommands.isNotEmpty() ||
                        action.verifications.isNotEmpty() ||
                        action.androidPackageHandoff != null
                )
                assertTrue(KiteResourceInstallPlanCompiler.compileVerification(action).isNotBlank())
                if (action.installSteps.none { it.type == KiteResourceInstallPlanCompiler.STEP_ARCHIVE }) {
                    assertTrue(KiteResourceInstallPlanCompiler.compile(action).isNotBlank())
                    val bundledCommand = KiteResourceInstallPlanCompiler.bundledCommand(action)
                        ?.removePrefix("install.sh")
                        ?.trim()
                        ?.ifBlank { "--install" }
                        ?.let { mode -> KiteResourceInstallRecipes.localToolchainCommand(resourceId, mode, cleanInstallRoot = false) }
                    val rawCommand = bundledCommand ?: KiteResourceInstallPlanCompiler.compile(action)
                    val script = KiteResourceInstallRecipes.manifestInstallCommand(
                        resourceId = resourceId,
                        displayName = loaded.name,
                        rawCommand = rawCommand,
                        managedCommands = action.managedCommands,
                        cleanInstallRoot = action.cleanInstallRoot,
                        verificationCommand = KiteResourceInstallPlanCompiler.compileVerification(action),
                        versionProbeCommand = sourcePlan.versionCheck.installed?.command.orEmpty(),
                        preservePaths = loaded.management.preservePaths
                    )
                    assertTrue("Generated script has no commit gate: $resourceId", script.contains("KITE_RESOURCE_STEP commit-install"))
                    File(generatedScripts, "$resourceId.sh").writeText(script)
                }
            }

            val installJson = loaded.rawJson
                .optJSONObject("actions")
                ?.optJSONArray("install")
                ?.toString()
                .orEmpty()
            assertFalse("Pipe-to-shell installer remains: $resourceId", PIPE_INSTALL.containsMatchIn(installJson))
        }

        File(generatedScripts, "synthetic-exit-56.sh").writeText(
            KiteResourceInstallPlanCompiler.compile(
                syntheticAction(
                    KiteResourceInstallStep(
                        id = "synthetic-installer",
                        type = KiteResourceInstallPlanCompiler.STEP_SCRIPT,
                        interpreter = "sh",
                        path = "-c",
                        arguments = listOf("exit 56")
                    )
                )
            )
        )
        File(generatedScripts, "synthetic-download-failure.sh").writeText(
            KiteResourceInstallPlanCompiler.compile(
                syntheticAction(
                    KiteResourceInstallStep(
                        id = "synthetic-download",
                        type = KiteResourceInstallPlanCompiler.STEP_DOWNLOAD,
                        urls = listOf("http://127.0.0.1:1/unreachable"),
                        destination = "/tmp/kite-synthetic-download",
                        retryAttempts = 2,
                        retryDelaySeconds = 0
                    )
                )
            )
        )
    }

    @Test
    fun agentResourcesUsePackagedBitmapIconsInsteadOfTextPlaceholders() {
        val resourceIds = listOf(
            "kite.codebuddy.code",
            "kite.cursor.cli",
            "kite.deepseek.harness",
            "kite.devin.cli",
            "kite.gemini.cli",
            "kite.github.copilot",
            "kite.pi.coding.agent",
            "kite.qoder.cli",
            "kite.trae.code",
        )
        val loader = KiteResourceManifestLoader(context)

        resourceIds.forEach { resourceId ->
            val manifestFile = File(resourceRoot(), "$resourceId/manifest.json")
            val manifest = loader.parseManifestJson(manifestFile.readText())
            val expectedAsset = "resources/$resourceId/icon.png"
            val iconFile = File(assetRoot(), expectedAsset)

            assertEquals("Wrong icon asset for $resourceId", expectedAsset, manifest.iconAsset)
            assertEquals("Wrong icon fit for $resourceId", "fullBleed", manifest.iconFit)
            assertTrue("Missing icon asset for $resourceId", iconFile.isFile)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(iconFile.absolutePath, bounds)
            assertTrue("Unreadable icon width for $resourceId", bounds.outWidth > 0)
            assertTrue("Unreadable icon height for $resourceId", bounds.outHeight > 0)
        }
    }

    @Test
    fun everyResourceHasResolvableRelationsAssetsAndLifecycleCommands() {
        val loader = KiteResourceManifestLoader(context, isDebugBuild = true)
        val manifests = resourceRoot().listFiles().orEmpty()
            .map { File(it, "manifest.json") }
            .filter(File::isFile)
            .associate { file ->
                val manifest = loader.parseManifestJson(file.readText())
                manifest.id to manifest
            }
        val resourceIds = manifests.keys

        manifests.forEach { (resourceId, manifest) ->
            (manifest.baseRequirements + manifest.defaultRequirements).forEach { dependencyId ->
                assertTrue("Unknown dependency $dependencyId from $resourceId", dependencyId in resourceIds)
                assertFalse("Resource depends on itself: $resourceId", dependencyId == resourceId)
            }
            manifest.displayRecommendations.forEach { recommendation ->
                assertTrue(
                    "Unknown recommendation ${recommendation.resourceId} from $resourceId",
                    recommendation.resourceId in resourceIds
                )
            }
            listOf(manifest.iconAsset, manifest.displayMedia?.asset.orEmpty())
                .filter(String::isNotBlank)
                .forEach { assetPath ->
                    assertTrue("Missing asset $assetPath from $resourceId", File(assetRoot(), assetPath).exists())
                }
            if (manifest.source.type == "bundled") {
                assertTrue(
                    "Missing bundled source ${manifest.source.asset} from $resourceId",
                    File(assetRoot(), manifest.source.asset).exists()
                )
            }
            if (manifest.homeCards.isNotEmpty()) {
                assertNotNull("Home card has no open recipe: $resourceId", manifest.openRecipe)
            }
            if (manifest.management.userLifecycleEnabled) {
                val uninstallCommands = KiteResourceSourcePlanFactory.plan(manifest)
                    .uninstallActions
                    .flatMap { it.managedCommands }
                    .toSet()
                assertTrue(
                    "Uninstall does not own every managed command for $resourceId",
                    uninstallCommands.containsAll(manifest.management.managedCommands)
                )
            }
        }

        fun assertAcyclic(resourceId: String, path: LinkedHashSet<String>) {
            assertTrue("Dependency cycle: ${(path + resourceId).joinToString(" -> ")}", path.add(resourceId))
            manifests.getValue(resourceId).baseRequirements.forEach { dependencyId ->
                assertAcyclic(dependencyId, path)
            }
            path.remove(resourceId)
        }
        resourceIds.forEach { resourceId -> assertAcyclic(resourceId, linkedSetOf()) }
    }

    private fun syntheticAction(step: KiteResourceInstallStep): KiteResourceShellAction =
        KiteResourceShellAction(
            type = KiteResourceInstallPlanCompiler.ACTION_MANAGED,
            cmd = "",
            surfaceMode = "silent",
            workdir = "/tmp",
            timeoutMs = 30_000L,
            managedCommands = emptyList(),
            cleanInstallRoot = false,
            npmUninstallPackages = emptyList(),
            installSteps = listOf(step)
        )

    private companion object {
        val PIPE_INSTALL = Regex("(curl|wget)[^\\n]*\\|\\s*(bash|sh)", RegexOption.IGNORE_CASE)

        fun resourceRoot(): File = listOf(
            File("assets/resources"),
            File("../assets/resources")
        ).first { it.isDirectory }

        fun assetRoot(): File = resourceRoot().parentFile ?: error("Resource asset root is unavailable")
    }
}
