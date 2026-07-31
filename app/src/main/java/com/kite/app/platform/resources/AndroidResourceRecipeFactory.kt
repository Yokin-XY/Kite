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
import com.kite.app.resources.KiteResourceSourcePlanFactory

/** 把资源清单编译成有限运行配方，不读取页面模型。 */
internal class AndroidResourceRecipeFactory(
    private val manifestLoader: KiteResourceManifestLoader
) {
    fun recipe(resourceId: String, operation: String, targetVersion: String? = null): KiteRecipe? {
        val manifest = manifestLoader.requestManifest(resourceId) ?: return null
        val sourcePlan = KiteResourceSourcePlanFactory.plan(manifest, targetVersion)
        val actions = when (operation) {
            KiteResourceInstallRecipes.OP_INSTALL,
            KiteResourceInstallRecipes.OP_UPDATE,
            KiteResourceInstallRecipes.OP_REINSTALL -> sourcePlan.installActions
            KiteResourceInstallRecipes.OP_UNINSTALL -> sourcePlan.uninstallActions
            else -> return null
        }
        val steps = actions.mapIndexed { index, action ->
            KiteRecipeStep(
                id = "${operation}_${KiteResourceInstallRecipes.safeId(resourceId)}_${index + 1}",
                type = KiteRecipe.STEP_SHELL,
                cmd = actionCommand(manifest, operation, action, targetVersion),
                surfaceMode = action.surfaceMode.ifBlank { KiteRecipe.SURFACE_MODE_PANEL },
                workdir = action.workdir.ifBlank { "/workspace" },
                timeoutMs = action.timeoutMs.takeIf { it > 0L } ?: 1_800_000L
            )
        }
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
        action: KiteResourceShellAction,
        targetVersion: String?
    ): String = when (operation) {
        KiteResourceInstallRecipes.OP_INSTALL,
        KiteResourceInstallRecipes.OP_UPDATE,
        KiteResourceInstallRecipes.OP_REINSTALL -> {
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
                verificationCommand = KiteResourceInstallPlanCompiler.compileVerification(action),
                versionProbeCommand = KiteResourceSourcePlanFactory.versionCheckPlan(manifest).installed?.command.orEmpty(),
                expectedVersion = targetVersion,
                preservePaths = manifest.management.preservePaths,
                recordOwnership = operation != KiteResourceInstallRecipes.OP_UPDATE,
                protectExistingInstall = operation == KiteResourceInstallRecipes.OP_UPDATE ||
                    operation == KiteResourceInstallRecipes.OP_REINSTALL
            )
        }
        KiteResourceInstallRecipes.OP_UNINSTALL -> KiteResourceInstallRecipes.manifestUninstallCommand(
            resourceId = manifest.id,
            rawCommand = action.cmd,
            managedCommands = action.managedCommands,
            npmUninstallPackages = action.npmUninstallPackages,
            preservePaths = manifest.management.preservePaths
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

    private fun iconName(manifest: KiteResourceManifest): String = when {
        manifest.displayCategory == "AI" -> KiteRecipeIcon.ICON_BOT
        manifest.displayCategory in setOf("Node", "JavaScript", "Python", "系统工具") -> KiteRecipeIcon.ICON_CODE
        else -> KiteRecipeIcon.ICON_TOOLS
    }

    private fun operationLabel(operation: String): String = when (operation) {
        KiteResourceInstallRecipes.OP_UNINSTALL -> "卸载"
        KiteResourceInstallRecipes.OP_UPDATE -> "更新"
        KiteResourceInstallRecipes.OP_REINSTALL -> "重新安装"
        else -> "获取"
    }
}
