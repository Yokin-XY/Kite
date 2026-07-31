package com.kite.app.feature.resources

import com.kite.app.action.KiteResourceActionIntent
import com.kite.app.action.KiteResourceActionRequest
import com.kite.app.action.KiteResourceActionSource
import com.kite.app.application.resources.ResourceFeatureDescriptor
import com.kite.app.application.resources.ResourceFeatureGateway
import com.kite.app.application.resources.ResourceFeatureRunSnapshot
import com.kite.app.resources.KiteResourceInstallStepUiProjection
import com.kite.app.resources.KiteResourceHomeLayout
import com.kite.app.resources.KiteResourceUiProjection

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

internal data class ResourceMaintenanceUiState(
    val userLifecycleEnabled: Boolean = false,
    val installedVersion: String = "",
    val latestVersion: String = "",
    val updateStatus: String = "",
    val statusSummary: String = "",
    val checkUpdateEnabled: Boolean = false,
    val updateEnabled: Boolean = false,
    val reinstallEnabled: Boolean = false,
    val uninstallEnabled: Boolean = false
)

internal data class ResourceItemUiState(
    val descriptor: ResourceFeatureDescriptor,
    val phase: ResourceItemPhase,
    val projection: KiteResourceUiProjection,
    val primaryIntent: KiteResourceActionIntent,
    val secondaryIntent: KiteResourceActionIntent?,
    val operation: String = "",
    val operationRun: ResourceFeatureRunSnapshot? = null,
    val registrySummary: String = "",
    val registryUpdatedAt: Long = 0L,
    val maintenance: ResourceMaintenanceUiState = ResourceMaintenanceUiState()
) {
    val resourceId: String get() = descriptor.id
    val name: String get() = descriptor.name
}

internal data class ResourcePlanStepUiState(
    val resourceId: String,
    val projection: KiteResourceInstallStepUiProjection,
    val operation: String = "",
    val run: ResourceFeatureRunSnapshot? = null
)

internal data class ResourcePlanUiState(
    val targetResourceId: String = "",
    val resourceIds: List<String> = emptyList(),
    val runningResourceIds: List<String> = emptyList(),
    val pendingResourceIds: List<String> = emptyList(),
    val steps: List<ResourcePlanStepUiState> = emptyList()
)

internal data class ResourceFeatureUiState(
    val phase: ResourceCatalogPhase = ResourceCatalogPhase.Idle,
    val items: List<ResourceItemUiState> = emptyList(),
    val homeLayout: KiteResourceHomeLayout? = null,
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
    data class Explicit(
        val resourceId: String,
        val intent: KiteResourceActionIntent,
        val source: KiteResourceActionSource
    ) : ResourceFeatureAction
}

internal sealed interface ResourceFeatureEffect {
    data class ActionRequested(val request: KiteResourceActionRequest) : ResourceFeatureEffect
    data class ActionUnavailable(val resourceId: String, val reason: String) : ResourceFeatureEffect
}
