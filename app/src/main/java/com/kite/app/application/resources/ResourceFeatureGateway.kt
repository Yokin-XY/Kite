package com.kite.app.application.resources

import com.kite.app.resources.KiteResourceHomeLayout
import com.kite.app.resources.KiteResourceManifest
import com.kite.app.resources.KiteResourcePlanSnapshot
import com.kite.app.resources.KiteResourceRegistryEntry
import com.kite.app.run.CardRunStatus
import kotlinx.coroutines.flow.Flow

data class ResourceFeatureDescriptor(
    val id: String,
    val name: String,
    val baselineInstalled: Boolean = false,
    val idleStateLabel: String = "未获取",
    val manifest: KiteResourceManifest? = null
)

data class ResourceFeatureChange(
    val reason: String,
    val affectedResourceIds: Set<String> = emptySet(),
    val catalogInvalidated: Boolean = false
)

interface ResourceFeatureGateway {
    val changes: Flow<ResourceFeatureChange>

    suspend fun loadCatalog(forceRefresh: Boolean): List<ResourceFeatureDescriptor>

    fun registrySnapshot(resourceIds: Collection<String>): Map<String, KiteResourceRegistryEntry>

    fun planSnapshot(): KiteResourcePlanSnapshot

    fun openRunStatus(resourceId: String): CardRunStatus?

    fun homeLayout(): KiteResourceHomeLayout?
}

interface ResourceFeatureDependenciesOwner {
    val resourceFeatureGateway: ResourceFeatureGateway
}
