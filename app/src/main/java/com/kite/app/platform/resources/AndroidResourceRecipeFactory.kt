package com.kite.app.platform.resources

import com.kite.app.recipe.KiteRecipe
import com.kite.app.recipe.KiteRecipeIcon
import com.kite.app.recipe.KiteRecipeStep
import com.kite.app.resources.KiteResourceInstallPlanCompiler
import com.kite.app.resources.KiteResourceInstallRecipes
import com.kite.app.resources.KiteResourceInstallSpec
import com.kite.app.resources.KiteResourceManifest
import com.kite.app.resources.KiteResourceManifestLoader
import com.kite.app.resources.KiteResourceShellAction

/** 把资源清单编译成有限运行配方，不读取页面模型。 */
internal class AndroidResourceRecipeFactory(
    private val manifestLoader: KiteResourceManifestLoader
) {
    fun recipe(resourceId: String, operation: String): KiteRecipe? {
        val manifest = manifestLoader.requestManifest(resourceId) ?: return null
        val actions = when (operation) {
            KiteResourceInstallRecipes.OP_INSTALL -> manifest.installActions
            KiteResourceInstallRecipes.OP_UNINSTALL -> manifest.uninstallActions
            else -> return null
        }
        val steps = actions.mapIndexed { index, action ->
            KiteRecipeStep(
                id = "${operation}_${KiteResourceInstallRecipes.safeId(resourceId)}_${index + 1}",
                type = KiteRecipe.STEP_SHELL,
                cmd = actionCommand(manifest, operation, action),
                surfaceMode = action.surfaceMode.ifBlank { KiteRecipe.SURFACE_MODE_PANEL },
                workdir = action.workdir.ifBlank { "/workspace" },
                timeoutMs = action.timeoutMs.takeIf { it > 0L } ?: 1_800_000L
            )
        }.ifEmpty { legacyStep(resourceId, operation)?.let(::listOf).orEmpty() }
        if (steps.isEmpty()) return null
        return KiteResourceInstallRecipes.toRecipe(
            KiteResourceInstallSpec(
                id = resourceId,
                name = "${manifest.name.ifBlank { resourceId }} ${operationLabel(operation)}",
                description = manifest.description,
                category = manifest.displayCategory,
                iconName = iconName(manifest),
                operation = operation,
                actionLabel = operationLabel(operation),
                steps = steps
            )
        )
    }

    fun isBundled(resourceId: String): Boolean =
        manifestLoader.requestManifest(resourceId)?.sourceType == "bundled"

    private fun actionCommand(
        manifest: KiteResourceManifest,
        operation: String,
        action: KiteResourceShellAction
    ): String = when (operation) {
        KiteResourceInstallRecipes.OP_INSTALL -> {
            val bundled = if (manifest.sourceType == "bundled") {
                KiteResourceInstallPlanCompiler.bundledCommand(action)
                    ?.let { localBundledCommand(manifest.id, it, cleanInstallRoot = false) }
                    ?: localBundledCommand(manifest.id, action.cmd, cleanInstallRoot = true)
            } else {
                null
            }
            val installCommand = bundled ?: KiteResourceInstallPlanCompiler.compile(action)
            KiteResourceInstallRecipes.manifestInstallCommand(
                resourceId = manifest.id,
                displayName = manifest.name,
                rawCommand = installCommand,
                managedCommands = action.managedCommands,
                cleanInstallRoot = action.cleanInstallRoot,
                verificationCommand = KiteResourceInstallPlanCompiler.compileVerification(action)
            )
        }
        KiteResourceInstallRecipes.OP_UNINSTALL -> KiteResourceInstallRecipes.manifestUninstallCommand(
            resourceId = manifest.id,
            rawCommand = action.cmd,
            managedCommands = action.managedCommands,
            npmUninstallPackages = action.npmUninstallPackages
        )
        else -> action.cmd
    }

    private fun localBundledCommand(
        resourceId: String,
        command: String,
        cleanInstallRoot: Boolean
    ): String? {
        val trimmed = command.trim()
        if (trimmed != "install.sh" && !trimmed.startsWith("install.sh ")) return null
        val mode = trimmed.removePrefix("install.sh").trim().ifBlank { "--install" }
        if (!Regex("""--?[A-Za-z0-9][A-Za-z0-9_-]*""").matches(mode)) return null
        return KiteResourceInstallRecipes.localToolchainCommand(resourceId, mode, cleanInstallRoot)
    }

    private fun legacyStep(resourceId: String, operation: String): KiteRecipeStep? {
        val command = when (operation) {
            KiteResourceInstallRecipes.OP_INSTALL -> when (resourceId) {
                RESOURCE_NODE_RUNTIME -> KiteResourceInstallRecipes.localToolchainCommand(resourceId, "--install-node")
                RESOURCE_KF_TOOL_ENV -> KiteResourceInstallRecipes.localToolchainCommand(resourceId, "--install")
                RESOURCE_HERMES_WEBUI -> KiteResourceInstallRecipes.hermesWebUiInstallCommand()
                RESOURCE_GIT -> KiteResourceInstallRecipes.gitInstallCommand()
                RESOURCE_CURL -> KiteResourceInstallRecipes.curlInstallCommand()
                RESOURCE_PYTHON -> KiteResourceInstallRecipes.pythonInstallCommand()
                RESOURCE_UV -> KiteResourceInstallRecipes.localToolchainCommand(resourceId, "--install-uv")
                RESOURCE_HERMES_CORE -> KiteResourceInstallRecipes.hermesCoreInstallCommand()
                else -> null
            }
            KiteResourceInstallRecipes.OP_UNINSTALL -> when (resourceId) {
                RESOURCE_NODE_RUNTIME -> KiteResourceInstallRecipes.nodeUninstallCommand()
                RESOURCE_KF_TOOL_ENV -> KiteResourceInstallRecipes.toolEnvUninstallCommand()
                RESOURCE_HERMES_WEBUI -> KiteResourceInstallRecipes.hermesWebUiUninstallCommand()
                RESOURCE_GIT -> KiteResourceInstallRecipes.gitUninstallCommand()
                RESOURCE_CURL -> KiteResourceInstallRecipes.curlUninstallCommand()
                RESOURCE_PYTHON -> KiteResourceInstallRecipes.pythonUninstallCommand()
                RESOURCE_UV -> KiteResourceInstallRecipes.uvUninstallCommand()
                RESOURCE_HERMES_CORE -> KiteResourceInstallRecipes.hermesCoreUninstallCommand()
                else -> null
            }
            else -> null
        } ?: return null
        return KiteRecipeStep(
            id = "${operation}_${KiteResourceInstallRecipes.safeId(resourceId)}_legacy",
            type = KiteRecipe.STEP_SHELL,
            cmd = command,
            surfaceMode = KiteRecipe.SURFACE_MODE_PANEL,
            workdir = "/workspace",
            timeoutMs = if (resourceId == RESOURCE_HERMES_CORE && operation == KiteResourceInstallRecipes.OP_INSTALL) {
                1_800_000L
            } else {
                900_000L
            }
        )
    }

    private fun iconName(manifest: KiteResourceManifest): String = when {
        manifest.id in setOf(RESOURCE_GIT, RESOURCE_CURL, RESOURCE_UV) -> KiteRecipeIcon.ICON_CODE
        manifest.displayCategory == "AI" -> KiteRecipeIcon.ICON_BOT
        manifest.displayCategory in setOf("Node", "JavaScript", "Python") -> KiteRecipeIcon.ICON_CODE
        else -> KiteRecipeIcon.ICON_TOOLS
    }

    private fun operationLabel(operation: String): String = when (operation) {
        KiteResourceInstallRecipes.OP_UNINSTALL -> "卸载"
        else -> "获取"
    }

    companion object {
        private const val RESOURCE_NODE_RUNTIME = "kite.nodejs"
        private const val RESOURCE_KF_TOOL_ENV = "kite.tool.env"
        private const val RESOURCE_HERMES_CORE = "kite.hermes.core"
        private const val RESOURCE_HERMES_WEBUI = "kite.hermes.webui"
        private const val RESOURCE_GIT = "kite.git"
        private const val RESOURCE_CURL = "kite.curl"
        private const val RESOURCE_PYTHON = "kite.python"
        private const val RESOURCE_UV = "kite.uv"
    }
}
