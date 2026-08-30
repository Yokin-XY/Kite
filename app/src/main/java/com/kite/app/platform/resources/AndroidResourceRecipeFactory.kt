package com.kite.app.platform.resources

import com.kite.app.foundation.runtime.AndroidNativeArchiveCapabilityProvider
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
import com.kite.app.resources.KiteResourceSourcePreferences
import com.kite.app.resources.KiteResourceSourcePolicy
import java.net.URI
import org.json.JSONObject

/** 把资源清单编译成有限运行配方，不读取页面模型。 */
internal class AndroidResourceRecipeFactory(
    private val manifestLoader: KiteResourceManifestLoader,
    private val sourcePreferencesProvider: () -> KiteResourceSourcePreferences = {
        KiteResourceSourcePreferences()
    },
) {
    fun recipe(resourceId: String, operation: String, targetVersion: String? = null): KiteRecipe? {
        val manifest = manifestLoader.requestManifest(resourceId) ?: return null
        val sourcePlan = KiteResourceSourcePlanFactory.plan(manifest, targetVersion)
        val sourcePreferences = sourcePreferencesProvider()
        val actions = when (operation) {
            KiteResourceInstallRecipes.OP_INSTALL,
            KiteResourceInstallRecipes.OP_UPDATE,
            KiteResourceInstallRecipes.OP_REINSTALL,
            KiteResourceInstallRecipes.OP_REPAIR -> sourcePlan.installActions
            KiteResourceInstallRecipes.OP_UNINSTALL -> sourcePlan.uninstallActions
            else -> return null
        }
        val routedActions = actions.map { action ->
            KiteResourceSourcePolicy.apply(action, sourcePreferences)
        }
        val steps = routedActions.flatMapIndexed { index, action ->
            actionSteps(manifest, operation, action, targetVersion, index, sourcePreferences)
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
            KiteResourceInstallRecipes.OP_REINSTALL,
            KiteResourceInstallRecipes.OP_REPAIR -> sourcePlan.installActions
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

    fun declaredWorkingBytes(
        resourceId: String,
        operation: String,
        targetVersion: String? = null,
    ): Long {
        val manifest = manifestLoader.requestManifest(resourceId) ?: return 0L
        val sourcePlan = KiteResourceSourcePlanFactory.plan(manifest, targetVersion)
        val actions = when (operation) {
            KiteResourceInstallRecipes.OP_INSTALL,
            KiteResourceInstallRecipes.OP_UPDATE,
            KiteResourceInstallRecipes.OP_REINSTALL,
            KiteResourceInstallRecipes.OP_REPAIR -> sourcePlan.installActions
            else -> emptyList()
        }
        val stepBytes = actions.asSequence()
            .flatMap(KiteResourceShellAction::installSteps)
            .map { step -> step.maxBytes.coerceAtLeast(0L) }
            .fold(0L, ::saturatingAdd)
        return maxOf(manifest.source.maxBytes.coerceAtLeast(0L), stepBytes)
    }

    private fun actionSteps(
        manifest: KiteResourceManifest,
        operation: String,
        action: KiteResourceShellAction,
        targetVersion: String?,
        actionIndex: Int,
        sourcePreferences: KiteResourceSourcePreferences,
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
            cmd = actionCommand(manifest, operation, shellAction, targetVersion, sourcePreferences),
            surfaceMode = surfaceMode,
            workdir = workdir,
            timeoutMs = timeoutMs,
        )
        val handoffSteps = shellAction.androidPackageHandoff?.let { handoff ->
            val params = JSONObject()
                .put(PARAM_APK_PATH, handoff.path)
                .put(PARAM_PACKAGE_NAME, handoff.packageName)
                .put(PARAM_WAIT_TIMEOUT_MS, handoff.waitTimeoutMs)
            listOf(
                KiteRecipeStep(
                    id = "${stepId}_open_android_installer",
                    type = KiteRecipe.STEP_ANDROID_ACTION,
                    action = KiteRecipe.ANDROID_ACTION_INSTALL_APK,
                    params = params,
                    surfaceMode = KiteRecipe.SURFACE_MODE_PANEL,
                    timeoutMs = handoff.waitTimeoutMs,
                ),
                KiteRecipeStep(
                    id = "${stepId}_await_android_package",
                    type = KiteRecipe.STEP_ANDROID_ACTION,
                    action = KiteRecipe.ANDROID_ACTION_AWAIT_PACKAGE,
                    params = params,
                    surfaceMode = KiteRecipe.SURFACE_MODE_PANEL,
                    timeoutMs = handoff.waitTimeoutMs,
                ),
            )
        }.orEmpty()
        return nativePlan?.steps.orEmpty() + shellStep + handoffSteps
    }

    /**
     * 只提升能够完整静态表达的前置下载。动态 URL、动态目标或无尺寸上限时保留原 PRoot 编译器。
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
            val urls = step.urls.takeIf { values ->
                values.isNotEmpty() && values.all(::isStaticHttpsUrl)
            } ?: return null
            if (urls.size > 1 && step.sha256.isBlank()) return null
            val installRelativePath = installRelativePath(step.destination) ?: return null
            if (step.maxBytes <= 0L) return null
            val safeStepId = KiteResourceInstallRecipes.safeId(step.id)
            val cacheDestination =
                "${KiteResourceInstallRecipes.resourceCachePath(resourceId)}/native-downloads/" +
                    "${recipeStepId}_${index + 1}_$safeStepId.payload"
            val params = JSONObject()
                .put(AndroidNativeDownloadCapabilityProvider.PARAM_DESTINATION, cacheDestination)
                .put(AndroidNativeDownloadCapabilityProvider.PARAM_MAX_BYTES, step.maxBytes.toString())
                .put(AndroidNativeDownloadCapabilityProvider.PARAM_MAX_ATTEMPTS, step.retryAttempts.toString())
                .put(
                    AndroidNativeDownloadCapabilityProvider.PARAM_RETRY_DELAY_MS,
                    (step.retryDelaySeconds * 1_000L).toString(),
                )
                .put(AndroidNativeDownloadCapabilityProvider.PARAM_REPLACE_EXISTING, "true")
            if (urls.size == 1) {
                params.put(AndroidNativeDownloadCapabilityProvider.PARAM_URL, urls.single())
            } else {
                params.put(AndroidNativeDownloadCapabilityProvider.PARAM_URLS, urls.joinToString("\n"))
                params.put(AndroidNativeDownloadCapabilityProvider.PARAM_CONNECT_TIMEOUT_MS, "6000")
            }
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
            CompiledNativeDownload(step, cacheDestination, nativeStep, importStep)
        }
        val artifactsByInstallPath = compiled.associateBy { it.original.destination.trim() }
        val remaining = action.installSteps.drop(leadingDownloads.size)
        val leadingArchives = remaining.takeWhile {
            it.type == KiteResourceInstallPlanCompiler.STEP_ARCHIVE
        }
        val compiledArchives = leadingArchives.mapIndexed { index, step ->
            val sourceDownload = artifactsByInstallPath[step.path.trim()] ?: return null
            val format = step.archiveFormat.takeIf { it in ARCHIVE_FORMATS } ?: return null
            val destinationRelative = installRelativeDirectory(step.destination) ?: return null
            if (
                sourceDownload.original.sha256.isBlank() ||
                step.maximumEntries <= 0 || step.maximumTotalBytes <= 0L ||
                step.maximumFileBytes <= 0L || step.maximumDepth <= 0 ||
                step.maximumExpansionRatio <= 0
            ) return null
            val safeStepId = KiteResourceInstallRecipes.safeId(step.id)
            val digestKey = sourceDownload.original.sha256.lowercase()
            val cacheDestination =
                "${KiteResourceInstallRecipes.resourceCachePath(resourceId)}/native-archives/" +
                    "${recipeStepId}_${index + 1}_${safeStepId}_${digestKey.take(16)}"
            val params = JSONObject()
                .put(AndroidNativeArchiveCapabilityProvider.PARAM_SOURCE, sourceDownload.cacheDestination)
                .put(AndroidNativeArchiveCapabilityProvider.PARAM_DESTINATION, cacheDestination)
                .put(AndroidNativeArchiveCapabilityProvider.PARAM_FORMAT, format)
                .put(
                    AndroidNativeArchiveCapabilityProvider.PARAM_MAX_ARCHIVE_BYTES,
                    sourceDownload.original.maxBytes.toString(),
                )
                .put(AndroidNativeArchiveCapabilityProvider.PARAM_MAX_ENTRIES, step.maximumEntries.toString())
                .put(AndroidNativeArchiveCapabilityProvider.PARAM_MAX_TOTAL_BYTES, step.maximumTotalBytes.toString())
                .put(AndroidNativeArchiveCapabilityProvider.PARAM_MAX_FILE_BYTES, step.maximumFileBytes.toString())
                .put(AndroidNativeArchiveCapabilityProvider.PARAM_MAX_DEPTH, step.maximumDepth.toString())
                .put(
                    AndroidNativeArchiveCapabilityProvider.PARAM_MAX_EXPANSION_RATIO,
                    step.maximumExpansionRatio.toString(),
                )
                .put(AndroidNativeArchiveCapabilityProvider.PARAM_EXPECTED_SHA256, digestKey)
                .put(AndroidNativeArchiveCapabilityProvider.PARAM_SPECIAL_ENTRY_POLICY, step.specialEntryPolicy)
                .put(AndroidNativeArchiveCapabilityProvider.PARAM_REUSE_KEY, "v1:$format:$digestKey")
            val nativeStep = KiteRecipeStep(
                id = "${recipeStepId}_native_archive_${index + 1}_$safeStepId",
                type = KiteRecipe.STEP_NATIVE_CAPABILITY,
                action = AndroidNativeArchiveCapabilityProvider.CAPABILITY_ID,
                params = params,
                surfaceMode = action.surfaceMode.ifBlank { KiteRecipe.SURFACE_MODE_PANEL },
                workdir = action.workdir.ifBlank { "/workspace" },
                timeoutMs = action.timeoutMs,
            )
            CompiledNativeArchive(
                sourceDownload = sourceDownload,
                nativeStep = nativeStep,
                importStep = step.copy(
                    type = KiteResourceInstallPlanCompiler.STEP_SHELL,
                    cmd = nativeArchiveImportCommand(cacheDestination, destinationRelative),
                ),
            )
        }
        val archivedDownloadIds = compiledArchives.mapTo(hashSetOf()) { it.sourceDownload.original.id }
        val rewritten = action.copy(
            installSteps =
                compiled.filterNot { it.original.id in archivedDownloadIds }.map { it.importStep } +
                    compiledArchives.map { it.importStep } +
                    remaining.drop(leadingArchives.size)
        )
        return NativeDownloadPlan(
            steps = compiled.map { it.nativeStep } + compiledArchives.map { it.nativeStep },
            rewrittenAction = rewritten,
        )
    }

    private fun actionCommand(
        manifest: KiteResourceManifest,
        operation: String,
        action: KiteResourceShellAction,
        targetVersion: String?,
        sourcePreferences: KiteResourceSourcePreferences,
    ): String = when (operation) {
        KiteResourceInstallRecipes.OP_INSTALL,
        KiteResourceInstallRecipes.OP_UPDATE,
        KiteResourceInstallRecipes.OP_REINSTALL,
        KiteResourceInstallRecipes.OP_REPAIR -> {
            val bundled = if (manifest.sourceType == "bundled") {
                KiteResourceInstallPlanCompiler.bundledCommand(action)
                    ?.let { localBundledCommand(manifest.id, it, cleanInstallRoot = false) }
                    ?: localBundledCommand(manifest.id, action.cmd, cleanInstallRoot = true)
            } else {
                null
            }
            val installCommand = bundled ?: KiteResourceInstallPlanCompiler.compile(action, sourcePreferences)
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
                    operation == KiteResourceInstallRecipes.OP_REINSTALL ||
                    operation == KiteResourceInstallRecipes.OP_REPAIR,
                operation = operation,
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
        KiteResourceInstallRecipes.OP_REPAIR -> "修复"
        else -> "获取"
    }

    private fun saturatingAdd(left: Long, right: Long): Long =
        if (right > Long.MAX_VALUE - left) Long.MAX_VALUE else left + right

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

    private fun installRelativeDirectory(value: String): String? {
        val clean = value.trim().trimEnd('/')
        if (clean == "${'$'}install_root") return ""
        return installRelativePath(clean)
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

    private fun nativeArchiveImportCommand(cacheDestination: String, installRelativePath: String): String {
        val destination = if (installRelativePath.isBlank()) {
            "${'$'}install_root"
        } else {
            "${'$'}install_root/$installRelativePath"
        }
        return """
            native_archive_cache=${shellLiteral(cacheDestination)}
            native_archive_destination="$destination"
            test -f "${'$'}native_archive_cache/.kite-archive-ready" || { echo "KITE_RESOURCE_FAILURE stage=acquire step=import-native-archive reason=missing"; exit 66; }
            mkdir -p "${'$'}native_archive_destination"
            cp -a "${'$'}native_archive_cache/." "${'$'}native_archive_destination/"
            rm -f "${'$'}native_archive_destination/.kite-archive-ready"
            echo "KITE_RESOURCE_STEP acquire-complete import-native-archive"
        """.trimIndent()
    }

    private fun shellLiteral(value: String): String =
        "'" + value.replace("'", "'\"'\"'") + "'"

    private data class NativeDownloadPlan(
        val steps: List<KiteRecipeStep>,
        val rewrittenAction: KiteResourceShellAction,
    )

    private data class CompiledNativeDownload(
        val original: com.kite.app.resources.KiteResourceInstallStep,
        val cacheDestination: String,
        val nativeStep: KiteRecipeStep,
        val importStep: com.kite.app.resources.KiteResourceInstallStep,
    )

    private data class CompiledNativeArchive(
        val sourceDownload: CompiledNativeDownload,
        val nativeStep: KiteRecipeStep,
        val importStep: com.kite.app.resources.KiteResourceInstallStep,
    )

    private companion object {
        val RESOURCE_RELATIVE_PATH = Regex("[A-Za-z0-9._-]+(?:/[A-Za-z0-9._-]+)*")
        val ARCHIVE_FORMATS = setOf("zip", "tar", "tar.gz", "tar.xz")
        const val PARAM_APK_PATH = "path"
        const val PARAM_PACKAGE_NAME = "packageName"
        const val PARAM_WAIT_TIMEOUT_MS = "waitTimeoutMs"
    }
}
