package com.kite.app.resources

import android.content.Context
import androidx.test.core.app.ApplicationProvider
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
    fun codexUsesOfficialNpmPackageWithDeclaredDependencies() {
        val manifestFile = File(resourceRoot(), "kite.codex.cli/manifest.json")
        val manifest = KiteResourceManifestLoader(context).parseManifestJson(manifestFile.readText())
        val installAction = manifest.installActions.single()
        val installStep = installAction.installSteps.single()
        val uninstallAction = manifest.uninstallActions.single()

        assertEquals("npm", manifest.sourceType)
        assertEquals(listOf("kite.nodejs", "kite.git"), manifest.baseRequirements)
        assertEquals(KiteResourceInstallPlanCompiler.STEP_NPM, installStep.type)
        assertEquals(listOf("@openai/codex@latest"), installStep.packages)
        assertEquals(5, installStep.retryAttempts)
        assertEquals(3, installStep.retryDelaySeconds)
        assertEquals(listOf("codex"), installAction.managedCommands)
        assertTrue(installAction.verifications.any { it.cmd.contains("codex --version") })
        assertEquals(listOf("@openai/codex"), uninstallAction.npmUninstallPackages)
        val openCommand = manifest.openRecipe
            ?.optJSONArray("recipe")
            ?.optJSONObject(0)
            ?.optString("text")
            .orEmpty()
        assertTrue(openCommand.contains("Codex CLI 首次启动会要求登录 ChatGPT 或配置 API Key。"))
        assertTrue(openCommand.lines().any { it.trim() == "codex" })
        assertFalse(openCommand.contains("codex login"))
        assertFalse(openCommand.contains("kite-auth-run"))
        assertEquals(openCommand, manifest.homeCards.single().recipe.optJSONArray("recipe").optJSONObject(0).optString("text"))
    }

    @Test
    fun claudeCodeUsesOfficialNpmPackageWithDeclaredDependencies() {
        val manifestFile = File(resourceRoot(), "kite.claude.code/manifest.json")
        val manifest = KiteResourceManifestLoader(context).parseManifestJson(manifestFile.readText())
        val installAction = manifest.installActions.single()
        val installStep = installAction.installSteps.single()
        val uninstallAction = manifest.uninstallActions.single()

        assertEquals("npm", manifest.sourceType)
        assertEquals(listOf("kite.nodejs", "kite.git"), manifest.baseRequirements)
        assertEquals(KiteResourceInstallPlanCompiler.STEP_NPM, installStep.type)
        assertEquals(listOf("@anthropic-ai/claude-code@latest"), installStep.packages)
        assertEquals(listOf("--allow-scripts=@anthropic-ai/claude-code"), installStep.arguments)
        assertEquals(5, installStep.retryAttempts)
        assertEquals(3, installStep.retryDelaySeconds)
        assertEquals(listOf("claude"), installAction.managedCommands)
        assertTrue(installAction.verifications.any { it.cmd.contains("claude --version") })
        assertEquals(listOf("@anthropic-ai/claude-code"), uninstallAction.npmUninstallPackages)
    }

    @Test
    fun everyBundledResourceUsesManagedInstallProtocol() {
        val loader = KiteResourceManifestLoader(context)
        val generatedScripts = File("build/generated-resource-install-scripts").also {
            it.deleteRecursively()
            it.mkdirs()
        }
        val manifests = resourceRoot().listFiles().orEmpty()
            .map { File(it, "manifest.json") }
            .filter { it.isFile }
            .sortedBy { it.parentFile?.name }

        assertEquals(21, manifests.size)
        manifests.forEach { manifestFile ->
            val resourceId = manifestFile.parentFile?.name.orEmpty()
            val loaded = loader.parseManifestJson(manifestFile.readText())
            assertNotNull("Manifest did not load: $resourceId", loaded)
            assertEquals(resourceId, loaded.id)
            assertTrue("No install action: $resourceId", loaded.installActions.isNotEmpty())
            loaded.installActions.forEach { action ->
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
                    verificationCommand = KiteResourceInstallPlanCompiler.compileVerification(action)
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
    }
}
