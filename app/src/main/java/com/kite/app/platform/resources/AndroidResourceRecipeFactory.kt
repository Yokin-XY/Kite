package com.kite.app.platform.resources

import com.kite.app.foundation.runtime.AndroidNativeDownloadCapabilityProvider
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
import java.net.URI
import org.json.JSONObject

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
        val steps = actions.flatMapIndexed { index, action ->
            actionSteps(manifest, operation, action, targetVersion, index)
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

    fun writeScopes(resourceId: String, operation: String, targetVersion: String? = null): Set<String> {
        val manifest = manifestLoader.requestManifest(resourceId)
            ?: return setOf("resource:${KiteResourceInstallRecipes.safeId(resourceId)}")
        val sourcePlan = KiteResourceSourcePlanFactory.plan(manifest, targetVersion)
        val actions = when (operation) {
            KiteResourceInstallRecipes.OP_INSTALL,
            KiteResourceInstallRecipes.OP_UPDATE,
            KiteResourceInstallRecipes.OP_REINSTALL -> sourcePlan.installActions
            KiteResourceInstallRecipes.OP_UNINSTALL -> sourcePlan.uninstallActions
            else -> emptyList()
        }
        return buildSet {
            add("resource:${KiteResourceInstallRecipes.safeId(manifest.id)}")
            actions.flatMap(KiteResourceShellAction::writeScopes).forEach(::add)
            val managedCommands = actions.flatMap(KiteResourceShellAction::managedCommands)
                .map(KiteResourceInstallRecipes::safeId)
                .filter(String::isNotBlank)
                .distinct()
            managedCommands.forEach { command -> add("command:$command") }
            if (managedCommands.isEmpty()) add("command:auto-discovery")
        }
    }

    private fun actionSteps(
        manifest: KiteResourceManifest,
        operation: String,
        action: KiteResourceShellAction,
        targetVersion: String?,
        actionIndex: Int,
    ): List<KiteRecipeStep> {
        val stepId = "${operation}_${KiteResourceInstallRecipes.safeId(manifest.id)}_${actionIndex + 1}"
        val surfaceMode = action.surfaceMode.ifBlank { KiteRecipe.SURFACE_MODE_PANEL }
        val workdir = action.workdir.ifBlank { "/workspace" }
        val timeoutMs = action.timeoutMs.takeIf { it > 0L } ?: 1_800_000L
        val nativePlan = nativeDownloadPlan(manifest.id, action, stepId)
        val shellAction = nativePlan?.rewrittenAction ?: action
        val shellStep = KiteRecipeStep(
            id = stepId,
            type = KiteRecipe.STEP_SHELL,
            cmd = actionCommand(manifest, operation, shellAction, targetVersion),
            surfaceMode = surfaceMode,
            workdir = workdir,
            timeoutMs = timeoutMs,
        )
        return nativePlan?.steps.orEmpty() + shellStep
    }

    /**
     * 只提升能够完整静态表达的前置下载。动态 URL、动态目标、多镜像或无尺寸上限时保留原 PRoot 编译器。
     * 下载发生在资源缓存；活动安装根仍只在后续资源事务持锁期间修改。
     */
    private fun nativeDownloadPlan(
        resourceId: String,
        action: KiteResourceShellAction,
        recipeStepId: String,
    ): NativeDownloadPlan? {
        if (action.type != KiteResourceInstallPlanCompiler.ACTION_MANAGED) return null
        val leadingDownloads = action.installSteps.takeWhile {
            it.type == KiteResourceInstallPlanCompiler.STEP_DOWNLOAD
        }
        if (leadingDownloads.isEmpty()) return null
        val compiled = leadingDownloads.mapIndexed { index, step ->
            val url = step.urls.singleOrNull()?.takeIf(::isStaticHttpsUrl) ?: return null
            val installRelativePath = installRelativePath(step.destination) ?: return null
            if (step.maxBytes <= 0L) return null
            val safeStepId = KiteResourceInstallRecipes.safeId(step.id)
            val cacheDestination =
                "${KiteResourceInstallRecipes.resourceCachePath(resourceId)}/native-downloads/" +
                    "${recipeStepId}_${index + 1}_$safeStepId.payload"
            val params = JSONObject()
                .put(AndroidNativeDownloadCapabilityProvider.PARAM_URL, url)
                .put(AndroidNativeDownloadCapabilityProvider.PARAM_DESTINATION, cacheDestination)
                .put(AndroidNativeDownloadCapabilityProvider.PARAM_MAX_BYTES, step.maxBytes.toString())
                .put(AndroidNativeDownloadCapabilityProvider.PARAM_MAX_ATTEMPTS, step.retryAttempts.toString())
                .put(
                    AndroidNativeDownloadCapabilityProvider.PARAM_RETRY_DELAY_MS,
                    (step.retryDelaySeconds * 1_000L).toString(),
                )
                .put(AndroidNativeDownloadCapabilityProvider.PARAM_REPLACE_EXISTING, "true")
            if (step.sha256.isNotBlank()) {
                params.put(AndroidNativeDownloadCapabilityProvider.PARAM_EXPECTED_SHA256, step.sha256)
            }
            val nativeStep = KiteRecipeStep(
                id = "${recipeStepId}_native_${index + 1}_$safeStepId",
                type = KiteRecipe.STEP_NATIVE_CAPABILITY,
                action = AndroidNativeDownloadCapabilityProvider.CAPABILITY_ID,
                params = params,
                surfaceMode = action.surfaceMode.ifBlank { KiteRecipe.SURFACE_MODE_PANEL },
                workdir = action.workdir.ifBlank { "/workspace" },
                timeoutMs = action.timeoutMs,
            )
            val importStep = step.copy(
                id = "import-$safeStepId",
                type = KiteResourceInstallPlanCompiler.STEP_SHELL,
                cmd = nativeImportCommand(cacheDestination, installRelativePath),
            )
            nativeStep to importStep
        }
        val rewritten = action.copy(
            installSteps = compiled.map { it.second } + action.installSteps.drop(leadingDownloads.size)
        )
        return NativeDownloadPlan(compiled.map { it.first }, rewritten)
    }

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

    private fun isStaticHttpsUrl(value: String): Boolean {
        val trimmed = value.trim()
        if ('$' in trimmed || '`' in trimmed || '\n' in trimmed || '\r' in trimmed) return false
        val uri = runCatching { URI(trimmed) }.getOrNull() ?: return false
        return uri.scheme.equals("https", ignoreCase = true) &&
            !uri.host.isNullOrBlank() && uri.userInfo == null && uri.fragment == null
    }

    private fun installRelativePath(value: String): String? {
        val prefix = "${'$'}install_root/"
        val relative = value.trim().takeIf { it.startsWith(prefix) }?.removePrefix(prefix) ?: return null
        return relative.takeIf { RESOURCE_RELATIVE_PATH.matches(it) }
    }

    private fun nativeImportCommand(cacheDestination: String, installRelativePath: String): String =
        """
            native_cache=${shellLiteral(cacheDestination)}
            native_destination="${'$'}install_root/$installRelativePath"
            test -s "${'$'}native_cache" || { echo "KITE_RESOURCE_FAILURE stage=acquire step=import-native-cache reason=missing"; exit 66; }
            mkdir -p "${'$'}(dirname "${'$'}native_destination")"
            mv -f "${'$'}native_cache" "${'$'}native_destination"
            echo "KITE_RESOURCE_STEP acquire-complete import-native-cache bytes=${'$'}(wc -c < "${'$'}native_destination")"
        """.trimIndent()

    private fun shellLiteral(value: String): String =
        "'" + value.replace("'", "'\"'\"'") + "'"

    private data class NativeDownloadPlan(
        val steps: List<KiteRecipeStep>,
        val rewrittenAction: KiteResourceShellAction,
    )

    private companion object {
        val RESOURCE_RELATIVE_PATH = Regex("[A-Za-z0-9._-]+(?:/[A-Za-z0-9._-]+)*")
    }
}
