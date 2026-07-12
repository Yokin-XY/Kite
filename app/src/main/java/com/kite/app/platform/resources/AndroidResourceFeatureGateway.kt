package com.kite.app.platform.resources

import com.kite.app.application.resources.ResourceFeatureDescriptor
import com.kite.app.application.resources.ResourceFeatureChange
import com.kite.app.application.resources.ResourceFeatureGateway
import com.kite.app.application.resources.ResourceFeatureRunSnapshot
import com.kite.app.foundation.toolchain.ToolchainPackInstaller
import com.kite.app.resources.KiteResourceHomeLayout
import com.kite.app.resources.KiteResourceInstallRecipes
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.withContext

internal class AndroidResourceFeatureGateway(
    private val manifestLoader: KiteResourceManifestLoader,
    private val installStore: KiteResourceInstallStore,
    private val nodeRuntimeInstalled: () -> Boolean
) : ResourceFeatureGateway {
    override val changes: Flow<ResourceFeatureChange> = merge(
        installStore.signals.drop(1).map { signal ->
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
            KiteResourceInstallRecipes.recipeId(resourceId, KiteResourceInstallRecipes.OP_OPEN)
        )?.status

    override fun operationRunSnapshot(
        resourceId: String,
        operation: String
    ): ResourceFeatureRunSnapshot? =
        CardRunStore.currentForRecipe(
            KiteResourceInstallRecipes.recipeId(resourceId, operation)
        )?.let { run ->
            ResourceFeatureRunSnapshot(
                instanceId = run.instanceId,
                operation = operation,
                status = run.status,
                surface = run.surface,
                startedAt = run.createdAt,
                updatedAt = run.updatedAt
            )
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

    private fun KiteResourceManifest.providesNodeRuntime(): Boolean =
        provides.any { it.startsWith("runtime.node") }

    companion object {
        fun create(
            manifestLoader: KiteResourceManifestLoader,
            installStore: KiteResourceInstallStore,
            nodeRuntimeInstalled: () -> Boolean
        ): AndroidResourceFeatureGateway =
            AndroidResourceFeatureGateway(manifestLoader, installStore, nodeRuntimeInstalled)
    }
}
