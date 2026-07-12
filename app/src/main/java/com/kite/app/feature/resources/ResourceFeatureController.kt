package com.kite.app.feature.resources

import com.kite.app.action.KiteResourceActionCoordinator
import com.kite.app.action.KiteResourceActionIntent
import com.kite.app.action.KiteResourceActionRequest
import com.kite.app.application.resources.ResourceFeatureDescriptor
import com.kite.app.application.resources.ResourceFeatureGateway
import com.kite.app.resources.KiteResourceInstallStepUiProjector
import com.kite.app.resources.KiteResourceInstallStore
import com.kite.app.resources.KiteResourceRuntimeFacts
import com.kite.app.resources.KiteResourceRuntimeFactsProjector
import com.kite.app.resources.KiteResourceUiProjector
import com.kite.app.run.CardRunStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** 资源页面共享的纯状态控制器，不持有 Context、View 或导航器。 */
internal class ResourceFeatureController(
    private val gateway: ResourceFeatureGateway
) {
    private val mutableState = MutableStateFlow(ResourceFeatureUiState())
    private val dispatchMutex = Mutex()
    val state: StateFlow<ResourceFeatureUiState> = mutableState.asStateFlow()

    suspend fun dispatch(action: ResourceFeatureAction): ResourceFeatureEffect? =
        dispatchMutex.withLock {
            when (action) {
                is ResourceFeatureAction.Refresh -> {
                    refresh(action.forceCatalogRefresh)
                    null
                }
                ResourceFeatureAction.ReconcileFacts -> {
                    reconcileFacts()
                    null
                }
                is ResourceFeatureAction.Primary -> requestPrimary(action)
                is ResourceFeatureAction.Secondary -> requestSecondary(action)
            }
        }

    private suspend fun refresh(forceCatalogRefresh: Boolean) {
        mutableState.value = mutableState.value.copy(
            phase = ResourceCatalogPhase.Loading,
            errorMessage = null
        )
        runCatching { gateway.loadCatalog(forceCatalogRefresh) }
            .onSuccess { descriptors -> publish(descriptors, ResourceCatalogPhase.Ready, null) }
            .onFailure { error ->
                mutableState.value = mutableState.value.copy(
                    phase = ResourceCatalogPhase.Failed,
                    revision = mutableState.value.revision + 1L,
                    errorMessage = error.message ?: error.javaClass.simpleName
                )
            }
    }

    private fun reconcileFacts() {
        val descriptors = mutableState.value.items.map(ResourceItemUiState::descriptor)
        if (descriptors.isEmpty()) return
        publish(descriptors, ResourceCatalogPhase.Ready, null)
    }

    private fun publish(
        descriptors: List<ResourceFeatureDescriptor>,
        phase: ResourceCatalogPhase,
        errorMessage: String?
    ) {
        val cleanDescriptors = descriptors
            .filter { it.id.isNotBlank() }
            .distinctBy { it.id }
        val registry = gateway.registrySnapshot(cleanDescriptors.map(ResourceFeatureDescriptor::id))
        val plan = gateway.planSnapshot()
        val itemStates = cleanDescriptors.map { descriptor ->
            projectItem(
                descriptor = descriptor,
                registryEntry = registry[descriptor.id],
                plan = plan,
                openRunStatus = gateway.openRunStatus(descriptor.id)
            )
        }
        mutableState.value = ResourceFeatureUiState(
            phase = phase,
            items = itemStates,
            homeLayout = gateway.homeLayout(),
            plan = projectPlan(plan, itemStates),
            revision = mutableState.value.revision + 1L,
            errorMessage = errorMessage
        )
    }

    private fun projectItem(
        descriptor: ResourceFeatureDescriptor,
        registryEntry: com.kite.app.resources.KiteResourceRegistryEntry?,
        plan: com.kite.app.resources.KiteResourcePlanSnapshot,
        openRunStatus: CardRunStatus?
    ): ResourceItemUiState {
        val facts = KiteResourceRuntimeFactsProjector.project(
            resourceId = descriptor.id,
            registryEntry = registryEntry,
            plan = plan,
            baselineInstalled = descriptor.baselineInstalled,
            idleStateLabel = descriptor.idleStateLabel
        )
        val projection = KiteResourceUiProjector.project(
            installed = facts.installed,
            preparing = facts.preparing,
            installing = facts.installing,
            uninstalling = facts.uninstalling,
            failed = facts.failed,
            failedOperation = facts.failedOperation,
            idleStateLabel = facts.idleStateLabel,
            openRunStatus = openRunStatus,
            extraBusy = facts.extraBusy
        )
        val operation = when {
            facts.uninstalling -> KiteResourceInstallStore.OP_UNINSTALL
            facts.failed && facts.failedOperation == KiteResourceInstallStore.OP_UNINSTALL ->
                KiteResourceInstallStore.OP_UNINSTALL
            else -> KiteResourceInstallStore.OP_INSTALL
        }
        val inPlan = descriptor.id == plan.targetResourceId || descriptor.id in plan.resourceIds
        return ResourceItemUiState(
            descriptor = descriptor,
            phase = resourcePhase(facts, openRunStatus),
            projection = projection,
            primaryIntent = KiteResourceActionCoordinator.primaryIntent(
                projection.actionLabel,
                reopenInstall = inPlan
            ),
            secondaryIntent = secondaryIntent(projection.secondaryActionLabel, facts),
            operation = operation,
            operationRun = gateway.operationRunSnapshot(descriptor.id, operation),
            registrySummary = registryEntry?.summary.orEmpty(),
            registryUpdatedAt = registryEntry?.updatedAt ?: 0L
        )
    }

    private fun projectPlan(
        plan: com.kite.app.resources.KiteResourcePlanSnapshot,
        itemStates: List<ResourceItemUiState>
    ): ResourcePlanUiState {
        val byId = itemStates.associateBy(ResourceItemUiState::resourceId)
        return ResourcePlanUiState(
            targetResourceId = plan.targetResourceId,
            resourceIds = plan.resourceIds,
            runningResourceIds = plan.runningResourceIds,
            pendingResourceIds = plan.pendingResourceIds,
            steps = plan.resourceIds.map { resourceId ->
                val item = byId[resourceId]
                val failedOperation = when (item?.phase) {
                    ResourceItemPhase.UninstallFailed -> KiteResourceInstallStore.OP_UNINSTALL
                    else -> KiteResourceInstallStore.OP_INSTALL
                }
                ResourcePlanStepUiState(
                    resourceId = resourceId,
                    projection = KiteResourceInstallStepUiProjector.project(
                        uninstalling = item?.phase == ResourceItemPhase.Uninstalling,
                        failed = item?.phase == ResourceItemPhase.InstallFailed ||
                            item?.phase == ResourceItemPhase.UninstallFailed,
                        failedOperation = failedOperation,
                        planStepStatus = plan.stepStatus(resourceId),
                        installed = item?.phase in setOf(
                            ResourceItemPhase.Installed,
                            ResourceItemPhase.Starting,
                            ResourceItemPhase.Running,
                            ResourceItemPhase.Stopping
                        ),
                        isActive = resourceId == plan.targetResourceId || resourceId in plan.resourceIds
                    ),
                    operation = item?.operation.orEmpty(),
                    run = item?.operationRun
                )
            }
        )
    }

    private fun requestPrimary(action: ResourceFeatureAction.Primary): ResourceFeatureEffect {
        val item = state.value.item(action.resourceId)
            ?: return ResourceFeatureEffect.ActionUnavailable(action.resourceId, "resource_not_in_catalog")
        if (!item.projection.actionEnabled && item.primaryIntent != KiteResourceActionIntent.ReopenInstall) {
            return ResourceFeatureEffect.ActionUnavailable(action.resourceId, "action_busy")
        }
        if (item.primaryIntent == KiteResourceActionIntent.Unsupported ||
            item.primaryIntent == KiteResourceActionIntent.BusyStatus
        ) {
            return ResourceFeatureEffect.ActionUnavailable(action.resourceId, "action_unavailable")
        }
        return ResourceFeatureEffect.ActionRequested(
            KiteResourceActionRequest(action.resourceId, item.primaryIntent, action.source)
        )
    }

    private fun requestSecondary(action: ResourceFeatureAction.Secondary): ResourceFeatureEffect {
        val item = state.value.item(action.resourceId)
            ?: return ResourceFeatureEffect.ActionUnavailable(action.resourceId, "resource_not_in_catalog")
        val intent = item.secondaryIntent
            ?: return ResourceFeatureEffect.ActionUnavailable(action.resourceId, "secondary_action_unavailable")
        return ResourceFeatureEffect.ActionRequested(
            KiteResourceActionRequest(action.resourceId, intent, action.source)
        )
    }

    private fun secondaryIntent(
        label: String?,
        facts: KiteResourceRuntimeFacts
    ): KiteResourceActionIntent? = when (label) {
        "取消" -> if (facts.failed) {
            KiteResourceActionIntent.CancelFailedInstall
        } else {
            KiteResourceActionIntent.CancelInstall
        }
        "中止" -> KiteResourceActionIntent.Stop
        "卸载" -> KiteResourceActionIntent.Uninstall
        else -> null
    }

    private fun resourcePhase(
        facts: KiteResourceRuntimeFacts,
        openRunStatus: CardRunStatus?
    ): ResourceItemPhase = when {
        facts.preparing -> ResourceItemPhase.Preparing
        facts.uninstalling -> ResourceItemPhase.Uninstalling
        facts.failed && facts.failedOperation == KiteResourceInstallStore.OP_UNINSTALL ->
            ResourceItemPhase.UninstallFailed
        facts.failed -> ResourceItemPhase.InstallFailed
        facts.installing -> ResourceItemPhase.Installing
        openRunStatus == CardRunStatus.Starting -> ResourceItemPhase.Starting
        openRunStatus == CardRunStatus.Stopping -> ResourceItemPhase.Stopping
        openRunStatus == CardRunStatus.Running ||
            openRunStatus == CardRunStatus.AlreadyRunning ||
            openRunStatus == CardRunStatus.Opened ||
            openRunStatus == CardRunStatus.WaitingTerminal -> ResourceItemPhase.Running
        facts.installed -> ResourceItemPhase.Installed
        facts.extraBusy -> ResourceItemPhase.Busy
        else -> ResourceItemPhase.NotInstalled
    }
}
