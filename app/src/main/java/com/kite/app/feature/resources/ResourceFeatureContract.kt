package com.kite.app.feature.resources

import com.kite.app.action.KiteResourceActionIntent
import com.kite.app.action.KiteResourceActionRequest
import com.kite.app.action.KiteResourceActionSource
import com.kite.app.resources.KiteResourceInstallStepUiProjection
import com.kite.app.resources.KiteResourcePlanSnapshot
import com.kite.app.resources.KiteResourceRegistryEntry
import com.kite.app.resources.KiteResourceUiProjection
import com.kite.app.run.CardRunStatus

internal enum class ResourceCatalogPhase {
    Idle,
    Loading,
    Ready,
    Failed
}

internal enum class ResourceItemPhase {
    NotInstalled,
    Preparing,
    Installing,
    Installed,
    Starting,
    Running,
    Stopping,
    Uninstalling,
    InstallFailed,
    UninstallFailed,
    Busy
}

internal data class ResourceFeatureDescriptor(
    val id: String,
    val name: String,
    val baselineInstalled: Boolean = false,
    val idleStateLabel: String = "未获取"
)

internal data class ResourceItemUiState(
    val descriptor: ResourceFeatureDescriptor,
    val phase: ResourceItemPhase,
    val projection: KiteResourceUiProjection,
    val primaryIntent: KiteResourceActionIntent,
    val secondaryIntent: KiteResourceActionIntent?,
    val registrySummary: String = "",
    val registryUpdatedAt: Long = 0L
) {
    val resourceId: String get() = descriptor.id
    val name: String get() = descriptor.name
}

internal data class ResourcePlanStepUiState(
    val resourceId: String,
    val projection: KiteResourceInstallStepUiProjection
)

internal data class ResourcePlanUiState(
    val targetResourceId: String = "",
    val resourceIds: List<String> = emptyList(),
    val steps: List<ResourcePlanStepUiState> = emptyList()
)

internal data class ResourceFeatureUiState(
    val phase: ResourceCatalogPhase = ResourceCatalogPhase.Idle,
    val items: List<ResourceItemUiState> = emptyList(),
    val plan: ResourcePlanUiState = ResourcePlanUiState(),
    val revision: Long = 0L,
    val errorMessage: String? = null
) {
    fun item(resourceId: String): ResourceItemUiState? =
        items.firstOrNull { it.resourceId == resourceId }
}

internal sealed interface ResourceFeatureAction {
    data class Refresh(val forceCatalogRefresh: Boolean = false) : ResourceFeatureAction
    data object ReconcileFacts : ResourceFeatureAction
    data class Primary(
        val resourceId: String,
        val source: KiteResourceActionSource
    ) : ResourceFeatureAction
    data class Secondary(
        val resourceId: String,
        val source: KiteResourceActionSource
    ) : ResourceFeatureAction
}

internal sealed interface ResourceFeatureEffect {
    data class ActionRequested(val request: KiteResourceActionRequest) : ResourceFeatureEffect
    data class ActionUnavailable(val resourceId: String, val reason: String) : ResourceFeatureEffect
}

internal interface ResourceFeatureGateway {
    suspend fun loadCatalog(forceRefresh: Boolean): List<ResourceFeatureDescriptor>

    fun registrySnapshot(resourceIds: Collection<String>): Map<String, KiteResourceRegistryEntry>

    fun planSnapshot(): KiteResourcePlanSnapshot

    fun openRunStatus(resourceId: String): CardRunStatus?
}
