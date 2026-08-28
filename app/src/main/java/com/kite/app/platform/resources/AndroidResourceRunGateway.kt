package com.kite.app.platform.resources

import android.content.Context
import com.kite.app.application.resources.ResourceRunGateway
import com.kite.app.application.resources.ResourceRunLaunchRequest
import com.kite.app.diagnostics.KiteDiagnostics
import com.kite.app.foundation.toolchain.ToolchainPackInstaller
import com.kite.app.foundation.workspace.WorkSurfaceRuntimeBridge
import com.kite.app.resources.KiteResourceInstallRecipes
import com.kite.app.resources.KiteResourceInstallStore
import com.kite.app.resources.KiteResourceManifestLoader
import com.kite.app.resources.KiteResourceSourcePlanFactory
import com.kite.app.application.resources.ResourceVersionParser
import com.kite.app.run.CardRunState
import com.kite.app.run.CardRunStatus
import com.kite.app.run.CardRunStore
import com.kite.app.run.CardRunSurface
import org.json.JSONObject
import java.io.File
import java.util.UUID
import kotlin.concurrent.thread

internal class AndroidResourceRunGateway(
    context: Context,
    private val installStore: KiteResourceInstallStore,
    private val manifestLoader: KiteResourceManifestLoader,
    private val recipeFactory: AndroidResourceRecipeFactory,
    private val candidateCoordinator: ResourceInstallCandidateCoordinator,
    private val capacityGuard: ResourceInstallCapacityGuard = ResourceInstallCapacityGuard(),
    private val diagnostics: KiteDiagnostics
) : ResourceRunGateway {
    private val appContext = context.applicationContext

    override fun recipe(resourceId: String, operation: String, targetVersion: String?) =
        recipeFactory.recipe(resourceId, operation, targetVersion)

    override fun isBundled(resourceId: String): Boolean =
        recipeFactory.isBundled(resourceId)

    override fun writeScopes(request: ResourceRunLaunchRequest): Set<String> =
        recipeFactory.writeScopes(request.resourceId, request.operation, request.targetVersion)

    override fun currentEnvironmentId(): String = installStore.currentEnvironmentId()

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
            stepId = request.resourceId,
            environmentId = request.environmentId
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
            shellReportText = "资源：${request.recipe.name}\n结果：正在准备资源",
            environmentId = request.environmentId
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

    override fun prepare(
        request: ResourceRunLaunchRequest,
        instanceId: String,
        callback: (Result<Unit>) -> Unit
    ) {
        val needsTransaction = request.operation in TRANSACTION_OPERATIONS
        if (!request.stageBundledResource && !needsTransaction) {
            callback(Result.success(Unit))
            return
        }
        thread(name = "KiteResourcePrepare-${request.resourceId}", isDaemon = true) {
            callback(
                runCatching {
                    if (request.stageBundledResource) {
                        stageBundledResource(request.resourceId)
                    }
                    if (needsTransaction) {
                        val container = WorkSurfaceRuntimeBridge.ensureDefaultContainer(appContext)
                        capacityGuard.requireCapacity(
                            workspaceDirectory = File(container.workspacePath),
                            resourceId = request.resourceId,
                            declaredWorkingBytes = recipeFactory.declaredWorkingBytes(
                                request.resourceId,
                                request.operation,
                                request.targetVersion,
                            ),
                        )
                        val manifest = manifestLoader.requestManifest(request.resourceId)
                            ?: error("resource_manifest_missing:${request.resourceId}")
                        candidateCoordinator.begin(
                            workspaceDirectory = File(container.workspacePath),
                            resourceId = request.resourceId,
                            runInstanceId = instanceId,
                            guestInstallRoot = manifest.installRoot,
                            preservePaths = manifest.management.preservePaths,
                            operation = request.operation,
                            targetVersion = request.targetVersion.orEmpty(),
                            previousVersion = installStore
                                .registryEntry(request.resourceId, request.environmentId)
                                ?.version
                                .orEmpty(),
                        ).getOrThrow()
                    }
                    Unit
                }
            )
        }
    }

    override fun commitMutation(request: ResourceRunLaunchRequest, instanceId: String): Result<Unit> =
        if (request.operation in TRANSACTION_OPERATIONS) {
            candidateCoordinator.commit(request.resourceId, instanceId)
        } else {
            Result.success(Unit)
        }

    override fun markMutationInstalling(
        request: ResourceRunLaunchRequest,
        instanceId: String,
    ): Result<Unit> = if (request.operation in TRANSACTION_OPERATIONS) {
        candidateCoordinator.markInstalling(request.resourceId, instanceId)
    } else {
        Result.success(Unit)
    }

    override fun markMutationVerified(
        request: ResourceRunLaunchRequest,
        instanceId: String,
    ): Result<Unit> = if (request.operation in TRANSACTION_OPERATIONS) {
        candidateCoordinator.markVerified(request.resourceId, instanceId)
    } else {
        Result.success(Unit)
    }

    override fun finalizeMutation(request: ResourceRunLaunchRequest, instanceId: String): Result<Unit> =
        if (request.operation in TRANSACTION_OPERATIONS) {
            candidateCoordinator.finalize(request.resourceId, instanceId)
        } else {
            Result.success(Unit)
        }

    override fun rollbackMutation(request: ResourceRunLaunchRequest, instanceId: String): Result<Unit> =
        if (request.operation in TRANSACTION_OPERATIONS) {
            candidateCoordinator.rollback(request.resourceId, instanceId)
        } else {
            Result.success(Unit)
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
            shellReportText = "资源：${request.recipe.name}\n结果：$message",
            environmentId = request.environmentId
        )
    }

    override fun markOperationStarted(
        resourceId: String,
        operation: String,
        instanceId: String,
        environmentId: String,
    ) {
        when (operation) {
            KiteResourceInstallRecipes.OP_INSTALL,
            KiteResourceInstallRecipes.OP_UPDATE,
            KiteResourceInstallRecipes.OP_REINSTALL,
            KiteResourceInstallRecipes.OP_REPAIR ->
                installStore.markInstalling(
                    resourceId,
                    runId = instanceId,
                    operation = operation,
                    environmentId = environmentId,
                )
            KiteResourceInstallRecipes.OP_UNINSTALL -> installStore.markUninstalling(
                resourceId,
                runId = instanceId,
                environmentId = environmentId,
            )
        }
    }

    private fun stageBundledResource(resourceId: String) {
        val asset = manifestLoader.requestManifest(resourceId)?.source?.asset.orEmpty()
        require(asset.isNotBlank()) { "Bundled resource $resourceId has no source.asset" }
        if (asset == TOOLCHAIN_PACK_ASSET) {
            ToolchainPackInstaller.stageLocalResourcePack(appContext, resourceId)
        } else {
            BundledResourceAssetStager.stage(appContext, resourceId, asset)
        }
    }

    override fun markInstalled(
        resourceId: String,
        versionHint: String?,
        runId: String?,
        summary: String?,
        evidence: String?,
        environmentId: String
    ) {
        val manifest = manifestLoader.requestManifest(resourceId)
        val installedProbe = manifest?.let(KiteResourceSourcePlanFactory::versionCheckPlan)?.installed
        val observedRaw = evidence.orEmpty().lineSequence()
            .map(String::trim)
            .lastOrNull { it.startsWith(INSTALLED_VERSION_MARKER) }
            ?.removePrefix(INSTALLED_VERSION_MARKER)
            .orEmpty()
        val observedVersion = installedProbe?.let { probe ->
            ResourceVersionParser.installed(observedRaw, probe)
        }
        val existingVersion = installStore.registryEntry(resourceId, environmentId)?.version.orEmpty()
        val version = observedVersion ?: versionHint ?: existingVersion
        val installedSummary = observedVersion?.let { "已验证安装版本 $it" } ?: summary
        installStore.markInstalled(resourceId, version, runId, installedSummary, environmentId)
        if (!versionHint.isNullOrBlank()) {
            if (observedVersion == null || equivalentVersion(observedVersion, versionHint)) {
                installStore.markUpdateCurrent(resourceId, version, versionHint, environmentId)
            } else {
                installStore.markMaintenanceFailed(
                    resourceId,
                    KiteResourceInstallRecipes.OP_UPDATE,
                    "目标版本校验不一致",
                    environmentId
                )
            }
        }
    }

    override fun saveInstalledSnapshot(resourceId: String, environmentId: String) {
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
            manifestJson = manifest.rawJson.toString(),
            environmentId = environmentId
        )
    }

    override fun markFailed(
        resourceId: String,
        operation: String,
        runId: String?,
        reason: String,
        environmentId: String
    ) {
        if (operation in setOf(
                KiteResourceInstallRecipes.OP_UPDATE,
                KiteResourceInstallRecipes.OP_REINSTALL,
                KiteResourceInstallRecipes.OP_REPAIR,
            ) &&
            installStore.registryEntry(resourceId, environmentId)?.version?.isNotBlank() == true
        ) {
            installStore.markMaintenanceFailed(resourceId, operation, reason, environmentId)
        } else {
            installStore.markFailed(resourceId, operation, runId, reason, environmentId)
        }
    }

    override fun clearResource(resourceId: String, environmentId: String) {
        installStore.clear(resourceId, environmentId)
    }

    override fun advancePlanAfter(resourceId: String, environmentId: String): List<String> =
        installStore.advancePlanAfter(resourceId, environmentId)

    override fun failPlanAt(resourceId: String, environmentId: String) {
        installStore.failPlanAt(resourceId, environmentId)
    }

    override fun clearPlan(environmentId: String) {
        installStore.clearPlan(environmentId)
    }

    override fun resumePlanFrom(resourceId: String, environmentId: String): Boolean =
        installStore.resumePlanFrom(resourceId, environmentId)

    override fun isInstalled(resourceId: String, environmentId: String): Boolean =
        installStore.isInstalled(resourceId, environmentId)

    override fun markPlanStepRunning(resourceId: String, environmentId: String): Boolean =
        installStore.markPlanStepRunning(resourceId, environmentId)

    override fun pendingPlanResourceIds(environmentId: String): List<String> =
        installStore.pendingPlanResourceIds(environmentId)

    override fun plannedInstall(
        resourceId: String,
        parentInstanceId: String?,
        environmentId: String
    ): ResourceRunLaunchRequest? {
        val recipe = recipeFactory.recipe(resourceId, KiteResourceInstallRecipes.OP_INSTALL) ?: return null
        return ResourceRunLaunchRequest(
            resourceId = resourceId,
            recipe = recipe,
            operation = KiteResourceInstallRecipes.OP_INSTALL,
            stageBundledResource = recipeFactory.isBundled(resourceId),
            parentInstanceId = parentInstanceId,
            environmentId = environmentId
        )
    }

    private fun newInstanceId(resourceId: String, recipeId: String): String =
        "resource-run-${KiteResourceInstallRecipes.safeId(resourceId)}-${KiteResourceInstallRecipes.safeId(recipeId)}-${UUID.randomUUID().toString().replace("-", "")}"

    private fun equivalentVersion(left: String, right: String): Boolean =
        left.removePrefix("v") == right.removePrefix("v")

    companion object {
        private const val TOOLCHAIN_PACK_ASSET = "toolchain/ai-dev-pack"
        private const val INSTALLED_VERSION_MARKER = "KITE_RESOURCE_INSTALLED_VERSION "
        private val TRANSACTION_OPERATIONS = setOf(
            KiteResourceInstallRecipes.OP_INSTALL,
            KiteResourceInstallRecipes.OP_UPDATE,
            KiteResourceInstallRecipes.OP_REINSTALL,
            KiteResourceInstallRecipes.OP_REPAIR,
        )
    }
}
