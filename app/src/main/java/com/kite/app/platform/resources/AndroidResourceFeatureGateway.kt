package com.kite.app.platform.resources

import com.kite.app.application.resources.ResourceFeatureDescriptor
import com.kite.app.application.resources.ResourceFeatureChange
import com.kite.app.application.resources.ResourceFeatureGateway
import com.kite.app.application.resources.ResourceFeatureRunSnapshot
import com.kite.app.foundation.toolchain.ToolchainPackInstaller
import com.kite.app.resources.KiteResourceHomeLayout
import com.kite.app.resources.KiteResourceInstallRecipes
import com.kite.app.resources.KiteResourceInstallContract
import com.kite.app.resources.KiteResourceInstallContractResolution
import com.kite.app.resources.KiteResourceInstallStore
import com.kite.app.resources.KiteResourceManifest
import com.kite.app.resources.KiteResourceManifestLoader
import com.kite.app.resources.KiteResourcePlanSnapshot
import com.kite.app.resources.KiteResourceRegistryEntry
import com.kite.app.run.CardRunStatus
import com.kite.app.run.CardRunStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.withContext

internal class AndroidResourceFeatureGateway(
    private val manifestLoader: KiteResourceManifestLoader,
    private val installStore: KiteResourceInstallStore,
    private val nodeRuntimeInstalled: () -> Boolean,
    private val activeResourceRunOwned: (String) -> Boolean,
) : ResourceFeatureGateway {
    init {
        reconcileInterruptedMaintenanceRuns()
    }

    override val changes: Flow<ResourceFeatureChange> = merge(
        installStore.signals.drop(1)
            .filter { signal -> signal.environmentId == installStore.currentEnvironmentId() }
            .map { signal ->
            ResourceFeatureChange(
                reason = signal.reason,
                affectedResourceIds = (
                    signal.affectedResourceIds +
                        listOfNotNull(signal.resourceId, signal.targetResourceId)
                    ).toSet()
            )
        },
        CardRunStore.runs.drop(1).map {
            ResourceFeatureChange(reason = "card_run_state")
        },
        ToolchainPackInstaller.state.drop(1).map {
            ResourceFeatureChange(reason = "toolchain_state", catalogInvalidated = true)
        }
    )

    override suspend fun loadCatalog(forceRefresh: Boolean): List<ResourceFeatureDescriptor> =
        withContext(Dispatchers.IO) {
            if (forceRefresh) manifestLoader.invalidate()
            val manifests = orderedVisibleManifests()
            reconcileInstalledContracts(manifests)
            val nodeInstalled = manifests.any { it.providesNodeRuntime() } &&
                runCatching(nodeRuntimeInstalled).getOrDefault(false)
            manifests.map { manifest ->
                ResourceFeatureDescriptor(
                    id = manifest.id,
                    name = manifest.name.ifBlank { manifest.id },
                    baselineInstalled = nodeInstalled && manifest.providesNodeRuntime(),
                    idleStateLabel = if (manifest.sourceType == "bundled") "本地包" else "未获取",
                    manifest = manifest
                )
            }
        }

    override fun registrySnapshot(resourceIds: Collection<String>): Map<String, KiteResourceRegistryEntry> =
        installStore.registrySnapshot(resourceIds)

    override fun planSnapshot(): KiteResourcePlanSnapshot = installStore.planSnapshot()

    override fun openRunStatus(resourceId: String): CardRunStatus? =
        CardRunStore.currentForRecipe(
            KiteResourceInstallRecipes.recipeId(resourceId, KiteResourceInstallRecipes.OP_OPEN),
            installStore.currentEnvironmentId()
        )?.status

    override fun operationRunSnapshot(
        resourceId: String,
        operation: String
    ): ResourceFeatureRunSnapshot? {
        val environmentId = installStore.currentEnvironmentId()
        val recipeId = KiteResourceInstallRecipes.recipeId(resourceId, operation)
        val registeredRun = installStore.registryEntry(resourceId, environmentId)
            ?.takeIf { entry -> entry.operation == operation }
            ?.runId
            ?.takeIf(String::isNotBlank)
            ?.let { instanceId -> CardRunStore.get(instanceId, environmentId) }
            ?.takeIf { run -> run.recipeId == recipeId }
        return (registeredRun ?: CardRunStore.latestForRecipe(recipeId, environmentId))?.let { run ->
            ResourceFeatureRunSnapshot(
                instanceId = run.instanceId,
                operation = operation,
                status = run.status,
                surface = run.surface,
                startedAt = run.createdAt,
                updatedAt = run.updatedAt,
                progressText = run.lastMeaningfulOutput.orEmpty(),
                reportText = run.shellReportText.orEmpty(),
            )
        }
    }

    override fun homeLayout(): KiteResourceHomeLayout? = manifestLoader.requestHomeLayout()

    private fun orderedVisibleManifests(): List<KiteResourceManifest> {
        val manifests = manifestLoader.manifests().values.filter { it.sections.isNotEmpty() }
        if (manifests.isEmpty()) return emptyList()
        val byId = manifests.associateBy(KiteResourceManifest::id)
        val homeOrder = manifestLoader.requestHomeLayout()
            ?.sections
            .orEmpty()
            .flatMap { it.items }
        return (homeOrder + manifests.map(KiteResourceManifest::id))
            .distinct()
            .mapNotNull(byId::get)
    }

    private fun reconcileInstalledContracts(manifests: List<KiteResourceManifest>) {
        val environmentId = installStore.currentEnvironmentId()
        manifests.asSequence()
            .filter { manifest -> manifest.management.userLifecycleEnabled }
            .mapNotNull { manifest ->
                val entry = installStore.registryEntry(manifest.id, environmentId)
                    ?.takeIf { it.installed && !it.busy }
                    ?: return@mapNotNull null
                Triple(
                    manifest,
                    entry,
                    KiteResourceInstallContract.resolve(
                        currentManifest = manifest.rawJson,
                        installedManifestJson = installStore.installedSnapshotManifestJson(
                            manifest.id,
                            environmentId,
                        ),
                    ),
                )
            }
            .forEach { (manifest, entry, resolution) ->
                when (resolution) {
                    KiteResourceInstallContractResolution.Current -> Unit
                    is KiteResourceInstallContractResolution.UpdateAvailable -> {
                        val alreadyCurrent =
                            entry.updateStatus == KiteResourceInstallStore.UPDATE_STATUS_AVAILABLE &&
                                entry.latestVersion == resolution.currentVersion &&
                                entry.operation == KiteResourceInstallRecipes.OP_UPDATE
                        if (!alreadyCurrent) {
                            installStore.markDefinitionUpdateAvailable(
                                resourceId = manifest.id,
                                installedVersion = resolution.installedVersion,
                                latestVersion = resolution.currentVersion,
                                environmentId = environmentId,
                            )
                        }
                    }
                    KiteResourceInstallContractResolution.RepairRequired -> {
                        val alreadyRequired =
                            entry.operation == KiteResourceInstallRecipes.OP_REPAIR &&
                                entry.updateStatus == KiteResourceInstallStore.UPDATE_STATUS_FAILED
                        if (!alreadyRequired) {
                            installStore.markRepairRequired(
                                resourceIds = setOf(manifest.id),
                                explanation = "资源定义已变化，需要修复安装",
                                environmentId = environmentId,
                            )
                        }
                    }
                }
            }
    }

    private fun reconcileInterruptedMaintenanceRuns() {
        installStore.registrySnapshot().values
            .filter { entry ->
                entry.installing && entry.operation in KiteResourceInstallRecipes.MAINTENANCE_OPERATIONS
            }
            .forEach { entry ->
                val recipeId = KiteResourceInstallRecipes.recipeId(entry.resourceId, entry.operation)
                val environmentId = installStore.currentEnvironmentId()
                if (entry.runId.isNotBlank() && activeResourceRunOwned(entry.runId)) {
                    return@forEach
                }
                val registered = entry.runId.takeIf(String::isNotBlank)
                    ?.let { instanceId -> CardRunStore.get(instanceId, environmentId) }
                    ?.takeIf { run -> run.recipeId == recipeId }
                val latestCurrent = registered ?: CardRunStore.currentForRecipe(recipeId, environmentId)
                val current = latestCurrent?.takeIf { run -> run.status in TERMINATED_FAILURE_STATUSES }
                val history = CardRunStore.historyForRecipe(recipeId, limit = 1, environmentId = environmentId)
                    .firstOrNull()
                    ?.takeIf { run ->
                        run.status in TERMINATED_FAILURE_STATUSES
                    }
                val failure = current?.let { run ->
                    run.lastError.orEmpty() to run.lastMeaningfulOutput.orEmpty()
                } ?: history?.takeIf { latestCurrent == null }?.let { run ->
                    run.error to run.summary
                }
                installStore.markMaintenanceFailed(
                    entry.resourceId,
                    entry.operation,
                    failure?.first.orEmpty().ifBlank {
                        failure?.second.orEmpty().ifBlank { "上次维护任务已中断，已恢复原有安装状态" }
                    },
                    environmentId,
                )
            }
    }

    private fun KiteResourceManifest.providesNodeRuntime(): Boolean =
        provides.any { it.startsWith("runtime.node") }

    companion object {
        private val TERMINATED_FAILURE_STATUSES = setOf(
            CardRunStatus.Failed,
            CardRunStatus.Stopped,
            CardRunStatus.BridgeUnavailable
        )

        fun create(
            manifestLoader: KiteResourceManifestLoader,
            installStore: KiteResourceInstallStore,
            nodeRuntimeInstalled: () -> Boolean,
            activeResourceRunOwned: (String) -> Boolean = { false },
        ): AndroidResourceFeatureGateway =
            AndroidResourceFeatureGateway(
                manifestLoader,
                installStore,
                nodeRuntimeInstalled,
                activeResourceRunOwned,
            )
    }
}
