package com.kite.app.resources

import android.content.Context
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
                      "runtimeDependencies": [{
                        "id": "python-background",
                        "argv": ["python3", "background.py"],
                        "runtimeGuarantees": ["no_child_process", "verified_native_imports"]
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

        val profile = valid.agentProfiles.single()
        assertEquals(setOf("no_child_process", "verified_native_imports"), profile.runtimeGuarantees)
        assertEquals(profile.runtimeGuarantees, profile.runtimeDependencies.single().runtimeGuarantees)
        assertEquals(
            profile.runtimeGuarantees,
            (AgentResourceRegistrationMapper.registrations(valid).single().launch as AgentLaunchSpec.Managed)
                .runtimeGuarantees,
        )
        assertTrue(invalid.agentProfiles.isEmpty())
    }

    @Test
    fun codexUsesOfficialNpmPackageWithDeclaredDependencies() {
        val manifestFile = File(resourceRoot(), "kite.codex.cli/manifest.json")
        val manifest = KiteResourceManifestLoader(context).parseManifestJson(manifestFile.readText())
        val sourcePlan = KiteResourceSourcePlanFactory.plan(manifest)
        val installAction = sourcePlan.installActions.single()
        val installStep = installAction.installSteps.single()
        val uninstallAction = sourcePlan.uninstallActions.single()

        assertEquals("npm", manifest.sourceType)
        assertTrue(manifest.installActions.isEmpty())
        assertTrue(sourcePlan.generatedFromSource)
        assertTrue(sourcePlan.capabilities.update)
        assertEquals(listOf("kite.nodejs", "kite.git"), manifest.baseRequirements)
        assertEquals(KiteResourceInstallPlanCompiler.STEP_NPM, installStep.type)
        assertEquals(
            listOf("@openai/codex@latest", "@agentclientprotocol/codex-acp@latest"),
            installStep.packages
        )
        assertEquals(5, installStep.retryAttempts)
        assertEquals(3, installStep.retryDelaySeconds)
        assertEquals(listOf("codex", "codex-acp"), installAction.managedCommands)
        assertTrue(installAction.verifications.any { it.cmd.contains("codex --version") })
        assertTrue(installAction.verifications.any { it.cmd.contains("command -v 'codex-acp'") })
        assertEquals(
            listOf("@openai/codex", "@agentclientprotocol/codex-acp"),
            uninstallAction.npmUninstallPackages
        )
        val openStep = manifest.openRecipe?.optJSONArray("recipe")?.optJSONObject(0)
        assertEquals("agent", openStep?.optString("type"))
        assertEquals("codex", openStep?.optString("agentId"))
        assertEquals(openStep?.toString(), manifest.homeCards.single().recipe.optJSONArray("recipe")?.optJSONObject(0)?.toString())
    }

    @Test
    fun openClawOpensRegisteredAgentSurfaceWithGenericNodeDependency() {
        val manifestFile = File(resourceRoot(), "kite.openclaw/manifest.json")
        val manifest = KiteResourceManifestLoader(context).parseManifestJson(manifestFile.readText())
        val openStep = manifest.openRecipe
            ?.optJSONArray("recipe")
            ?.optJSONObject(0)
        val homeStep = manifest.homeCards.single().recipe
            .optJSONArray("recipe")
            ?.optJSONObject(0)
        val profile = manifest.agentProfiles.single()
        val dependency = profile.runtimeDependencies.single()

        assertEquals(listOf("kite.nodejs", "kite.git"), manifest.baseRequirements)
        assertEquals(listOf("openclaw", "acp"), profile.argv)
        assertEquals(
            "/workspace/.kf/secrets/kite.openclaw-gateway-token",
            profile.environmentFiles["OPENCLAW_GATEWAY_TOKEN"],
        )
        assertEquals("openclaw-gateway", dependency.id)
        assertEquals("openclaw", dependency.argv.first())
        assertEquals(listOf("--auth", "token"), dependency.argv.takeLast(2))
        assertEquals("127.0.0.1", dependency.bindAddress)
        assertEquals(18789, dependency.bindPort)
        assertEquals("/readyz", dependency.healthHttpPath)
        assertEquals(profile.environmentFiles, dependency.environmentFiles)
        val tokenStep = manifest.installActions.single().installSteps.single {
            it.id == "prepare-openclaw-gateway-token"
        }
        assertTrue(tokenStep.cmd.contains("tr -d '\\n-'"))
        assertFalse(tokenStep.cmd.contains("tr -d '-\\n'"))
        assertTrue(tokenStep.cmd.contains("chmod 600"))
        assertEquals("agent", openStep?.optString("type"))
        assertEquals("openclaw", openStep?.optString("agentId"))
        assertEquals("/workspace", openStep?.optString("workdir"))
        assertEquals(openStep?.toString(), homeStep?.toString())
    }

    @Test
    fun claudeCodeUsesOfficialNpmPackageWithDeclaredDependencies() {
        val manifestFile = File(resourceRoot(), "kite.claude.code/manifest.json")
        val manifest = KiteResourceManifestLoader(context).parseManifestJson(manifestFile.readText())
        val sourcePlan = KiteResourceSourcePlanFactory.plan(manifest)
        val installAction = sourcePlan.installActions.single()
        val installStep = installAction.installSteps.single()
        val uninstallAction = sourcePlan.uninstallActions.single()

        assertEquals("npm", manifest.sourceType)
        assertTrue(manifest.installActions.isEmpty())
        assertTrue(sourcePlan.generatedFromSource)
        assertTrue(sourcePlan.capabilities.update)
        assertEquals(listOf("kite.nodejs", "kite.git"), manifest.baseRequirements)
        assertEquals(KiteResourceInstallPlanCompiler.STEP_NPM, installStep.type)
        assertEquals(
            listOf("@anthropic-ai/claude-code@latest", "@agentclientprotocol/claude-agent-acp@latest"),
            installStep.packages
        )
        assertEquals(listOf("--allow-scripts=@anthropic-ai/claude-code"), installStep.arguments)
        assertEquals(5, installStep.retryAttempts)
        assertEquals(3, installStep.retryDelaySeconds)
        assertEquals(listOf("claude", "claude-agent-acp"), installAction.managedCommands)
        assertTrue(installAction.verifications.any { it.cmd.contains("claude --version") })
        assertTrue(installAction.verifications.any { it.cmd.contains("command -v 'claude-agent-acp'") })
        assertEquals(
            listOf("@anthropic-ai/claude-code", "@agentclientprotocol/claude-agent-acp"),
            uninstallAction.npmUninstallPackages
        )
    }

    @Test
    fun mainstreamAgentResourcesExposeManagedAcpProfilesAndAgentCards() {
        data class Expected(
            val resourceId: String,
            val agentId: String,
            val displayName: String,
            val argv: List<String>,
            val configAdapterId: String
        )

        val expected = listOf(
            Expected("kite.codex.cli", "codex", "Codex", listOf("codex-acp"), "codex"),
            Expected("kite.claude.code", "claude-code", "Claude Code", listOf("claude-agent-acp"), "claude-code"),
            Expected("kite.hermes.core", "hermes", "Hermes", listOf("hermes", "acp"), "hermes"),
            Expected("kite.openclaw", "openclaw", "OpenClaw", listOf("openclaw", "acp"), "openclaw"),
            Expected("kite.mimo.code", "mimo", "MiMo Code", listOf("mimo", "acp"), "mimo-code")
        )

        expected.forEach { item ->
            val manifest = KiteResourceManifestLoader(context).parseManifestJson(
                File(resourceRoot(), "${item.resourceId}/manifest.json").readText()
            )
            val profile = manifest.agentProfiles.single()
            val registration = AgentResourceRegistrationMapper.registrations(manifest).single()
            val openStep = manifest.openRecipe?.optJSONArray("recipe")?.optJSONObject(0)
            val homeStep = manifest.homeCards.single().recipe.optJSONArray("recipe")?.optJSONObject(0)

            assertEquals(item.agentId, profile.agentId)
            assertEquals(item.displayName, profile.displayName)
            assertEquals("managed", profile.launchMode)
            assertEquals(item.agentId, profile.providerId)
            assertEquals("acp", profile.protocol)
            assertEquals("stdio", profile.transport)
            assertEquals(item.argv, profile.argv)
            assertEquals(item.configAdapterId, profile.configAdapterId)
            assertFalse(profile.configurationRequired)
            assertEquals(item.agentId, registration.definition.agentId)
            assertEquals(item.configAdapterId, registration.configAdapterId)
            assertEquals(AgentRegistrationSource.Resource(item.resourceId), registration.source)
            assertTrue(registration.launch is AgentLaunchSpec.Managed)
            assertEquals("agent", openStep?.optString("type"))
            assertEquals(item.agentId, openStep?.optString("agentId"))
            assertFalse(openStep?.has("providerId") == true)
            assertEquals("/workspace", openStep?.optString("workdir"))
            assertEquals(openStep?.toString(), homeStep?.toString())
        }

        val hermes = KiteResourceManifestLoader(context).parseManifestJson(
            File(resourceRoot(), "kite.hermes.core/manifest.json").readText()
        )
        assertEquals(listOf("home"), hermes.management.preservePaths)
        assertTrue(hermes.installActions.single().installSteps.any { it.id == "install-hermes-acp-extra" })
        assertTrue(hermes.installActions.single().verifications.any { it.cmd.contains("hermes acp --help") })
    }

    @Test
    fun reasonixUsesPublishedNpmCommandAndGeneratedLifecycle() {
        val manifestFile = File(resourceRoot(), "kite.reasonix/manifest.json")
        val manifest = KiteResourceManifestLoader(context).parseManifestJson(manifestFile.readText())
        val sourcePlan = KiteResourceSourcePlanFactory.plan(manifest)
        val installAction = sourcePlan.installActions.single()
        val uninstallAction = sourcePlan.uninstallActions.single()

        assertEquals("npm", manifest.sourceType)
        assertEquals("reasonix", manifest.source.packageName)
        assertEquals(listOf("reasonix"), manifest.management.managedCommands)
        assertTrue(manifest.installActions.isEmpty())
        assertTrue(manifest.uninstallActions.isEmpty())
        assertTrue(sourcePlan.generatedFromSource)
        assertEquals(listOf("reasonix@latest"), installAction.installSteps.single().packages)
        assertEquals(listOf("reasonix"), installAction.managedCommands)
        assertEquals(listOf("command-reasonix"), installAction.verifications.map { it.id })
        assertEquals(listOf("reasonix"), uninstallAction.managedCommands)
        assertEquals(listOf("reasonix"), uninstallAction.npmUninstallPackages)
    }

    @Test
    fun `首页只定义版面并由各 manifest 投影当前顺序和标签页`() {
        val homeJson = File(resourceRoot(), "home.json").readText()
        val debugLayout = KiteResourceManifestLoader(context, isDebugBuild = true).requestHomeLayout()
        val releaseLayout = KiteResourceManifestLoader(context, isDebugBuild = false).requestHomeLayout()

        assertFalse("home.json must not register resource ids with items", homeJson.contains("\"items\""))
        assertEquals(
            listOf(
                "kite.opencode",
                "kite.openclaw",
                "kite.reasonix",
                "kite.hermes.core"
            ),
            debugLayout?.sections?.first { it.id == "ai-community" }?.items
        )
        assertEquals(
            listOf(
                "kite.codex.cli",
                "kite.claude.code",
                "kite.kimi.code",
                "kite.qwen.code",
                "kite.google.antigravity",
                "kite.zai.coding.helper",
                "kite.mimo.code"
            ),
            debugLayout?.sections?.first { it.id == "ai-vendor" }?.items
        )
        assertEquals(
            listOf("kite.nodejs", "kite.python", "kite.git", "kite.uv", "kite.curl"),
            debugLayout?.sections?.first { it.id == "foundation" }?.items
        )
        assertTrue(debugLayout?.sections?.none { it.id == "more" } == true)
        assertEquals(
            listOf(
                "kite.opencode",
                "kite.openclaw",
                "kite.reasonix",
                "kite.hermes.core",
                "kite.codex.cli",
                "kite.claude.code",
                "kite.kimi.code",
                "kite.qwen.code",
                "kite.google.antigravity",
                "kite.zai.coding.helper",
                "kite.mimo.code"
            ),
            debugLayout?.tabs?.first { it.id == "angel-cli" }?.sections?.single()?.items
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

        assertEquals(17, manifests.size)
        manifests.forEach { manifestFile ->
            val resourceId = manifestFile.parentFile?.name.orEmpty()
            val loaded = loader.parseManifestJson(manifestFile.readText())
            val sourcePlan = KiteResourceSourcePlanFactory.plan(loaded)
            assertNotNull("Manifest did not load: $resourceId", loaded)
            assertEquals(resourceId, loaded.id)
            assertTrue("No resolved install action: $resourceId", sourcePlan.installActions.isNotEmpty())
            if (loaded.management.userLifecycleEnabled) {
                assertTrue("No resolved uninstall action: $resourceId", sourcePlan.uninstallActions.isNotEmpty())
            }
            sourcePlan.installActions.forEach { action ->
                assertEquals("Legacy install action remains: $resourceId", KiteResourceInstallPlanCompiler.ACTION_MANAGED, action.type)
                assertTrue("Managed action has no steps: $resourceId", action.installSteps.isNotEmpty())
                assertTrue(
                    "Managed action has no success contract: $resourceId",
                    action.managedCommands.isNotEmpty() || action.verifications.isNotEmpty()
                )
                assertTrue(KiteResourceInstallPlanCompiler.compile(action).isNotBlank())
                assertTrue(KiteResourceInstallPlanCompiler.compileVerification(action).isNotBlank())
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
