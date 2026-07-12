package com.kite.app.platform.resources

import android.content.Context
import com.kite.app.application.resources.ResourceRunGateway
import com.kite.app.application.resources.ResourceRunLaunchRequest
import com.kite.app.diagnostics.KiteDiagnostics
import com.kite.app.foundation.toolchain.ToolchainPackInstaller
import com.kite.app.resources.KiteResourceInstallRecipes
import com.kite.app.resources.KiteResourceInstallStore
import com.kite.app.resources.KiteResourceManifestLoader
import com.kite.app.run.CardRunState
import com.kite.app.run.CardRunStatus
import com.kite.app.run.CardRunStore
import com.kite.app.run.CardRunSurface
import org.json.JSONObject
import java.util.UUID
import kotlin.concurrent.thread

internal class AndroidResourceRunGateway(
    context: Context,
    private val installStore: KiteResourceInstallStore,
    private val manifestLoader: KiteResourceManifestLoader,
    private val recipeFactory: AndroidResourceRecipeFactory,
    private val diagnostics: KiteDiagnostics
) : ResourceRunGateway {
    private val appContext = context.applicationContext

    override fun beginRun(request: ResourceRunLaunchRequest): CardRunState {
        val instanceId = request.preferredInstanceId
            ?.takeIf { it.isNotBlank() }
            ?: newInstanceId(request.resourceId, request.recipe.id)
        CardRunStore.registerRecipe(request.recipe)
        val started = CardRunStore.start(
            recipe = request.recipe,
            instanceId = instanceId,
            parentInstanceId = request.parentInstanceId,
            ownerKind = CardRunState.OWNER_KIND_RESOURCE,
            stepId = request.resourceId
        )
        val state = CardRunStore.update(
            recipe = request.recipe,
            status = CardRunStatus.Starting,
            instanceId = started.instanceId,
            parentInstanceId = request.parentInstanceId,
            ownerKind = CardRunState.OWNER_KIND_RESOURCE,
            stepId = request.resourceId,
            surface = CardRunSurface.Report,
            currentStepIndex = 0,
            lastMeaningfulOutput = "正在准备资源：${request.recipe.name}",
            shellReportText = "资源：${request.recipe.name}\n结果：正在准备资源"
        )
        diagnostics.logRecipeAction(
            request.recipe,
            "resource_run_registered",
            mapOf(
                "resourceId" to request.resourceId,
                "operation" to request.operation,
                "instanceId" to state.instanceId
            )
        )
        return state
    }

    override fun prepare(request: ResourceRunLaunchRequest, callback: (Result<Unit>) -> Unit) {
        if (!request.stageBundledResource) {
            callback(Result.success(Unit))
            return
        }
        thread(name = "KiteResourcePrepare-${request.resourceId}", isDaemon = true) {
            callback(
                runCatching {
                    ToolchainPackInstaller.stageLocalResourcePack(
                        appContext,
                        request.resourceId
                    )
                    Unit
                }
            )
        }
    }

    override fun failRunPreparation(
        request: ResourceRunLaunchRequest,
        instanceId: String,
        message: String
    ) {
        CardRunStore.update(
            recipe = request.recipe,
            status = CardRunStatus.Failed,
            instanceId = instanceId,
            surface = CardRunSurface.Report,
            currentStepIndex = 0,
            lastError = message,
            shellReportText = "资源：${request.recipe.name}\n结果：$message"
        )
    }

    override fun markOperationStarted(resourceId: String, operation: String) {
        when (operation) {
            KiteResourceInstallRecipes.OP_INSTALL -> installStore.markInstalling(resourceId)
            KiteResourceInstallRecipes.OP_UNINSTALL -> installStore.markUninstalling(resourceId)
        }
    }

    override fun markInstalled(resourceId: String, runId: String?, summary: String?) {
        val version = manifestLoader.requestManifest(resourceId)?.version.orEmpty()
        installStore.markInstalled(resourceId, version, runId, summary)
    }

    override fun saveInstalledSnapshot(resourceId: String) {
        val manifest = manifestLoader.requestManifest(resourceId) ?: return
        val iconJson = JSONObject().apply {
            if (manifest.iconAsset.isNotBlank()) {
                put("type", "asset")
                put("value", manifest.iconAsset)
                put("fallbackText", manifest.iconText)
                if (manifest.iconFit.isNotBlank()) put("fit", manifest.iconFit)
            } else {
                put("type", "text")
                put("value", manifest.iconText)
            }
        }.toString()
        installStore.saveInstalledSnapshot(
            resourceId = resourceId,
            name = manifest.name,
            iconJson = iconJson,
            version = manifest.version,
            manifestJson = manifest.rawJson.toString()
        )
    }

    override fun markFailed(
        resourceId: String,
        operation: String,
        runId: String?,
        reason: String
    ) {
        installStore.markFailed(resourceId, operation, runId, reason)
    }

    override fun clearResource(resourceId: String) {
        installStore.clear(resourceId)
    }

    override fun advancePlanAfter(resourceId: String): List<String> =
        installStore.advancePlanAfter(resourceId)

    override fun failPlanAt(resourceId: String) {
        installStore.failPlanAt(resourceId)
    }

    override fun clearPlan() {
        installStore.clearPlan()
    }

    override fun resumePlanFrom(resourceId: String): Boolean =
        installStore.resumePlanFrom(resourceId)

    override fun isInstalled(resourceId: String): Boolean =
        installStore.isInstalled(resourceId)

    override fun markPlanStepRunning(resourceId: String): Boolean =
        installStore.markPlanStepRunning(resourceId)

    override fun plannedInstall(
        resourceId: String,
        parentInstanceId: String?
    ): ResourceRunLaunchRequest? {
        val recipe = recipeFactory.recipe(resourceId, KiteResourceInstallRecipes.OP_INSTALL) ?: return null
        return ResourceRunLaunchRequest(
            resourceId = resourceId,
            recipe = recipe,
            operation = KiteResourceInstallRecipes.OP_INSTALL,
            stageBundledResource = recipeFactory.isBundled(resourceId),
            parentInstanceId = parentInstanceId
        )
    }

    private fun newInstanceId(resourceId: String, recipeId: String): String =
        "resource-run-${KiteResourceInstallRecipes.safeId(resourceId)}-${KiteResourceInstallRecipes.safeId(recipeId)}-${UUID.randomUUID().toString().replace("-", "")}"
}
