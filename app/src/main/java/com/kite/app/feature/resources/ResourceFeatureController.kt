package com.kite.app.feature.resources

import com.kite.app.action.KiteResourceActionCoordinator
import com.kite.app.action.KiteResourceActionIntent
import com.kite.app.action.KiteResourceActionRequest
import com.kite.app.application.resources.ResourceFeatureDescriptor
import com.kite.app.application.resources.ResourceFeatureGateway
import com.kite.app.resources.KiteResourceInstallStepUiProjector
import com.kite.app.resources.KiteResourceInstallRecipes
import com.kite.app.resources.KiteResourceInstallStore
import com.kite.app.resources.KiteResourceRuntimeFacts
import com.kite.app.resources.KiteResourceRuntimeFactsProjector
import com.kite.app.resources.KiteResourceSourcePlanFactory
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

    suspend fun dispatch(action: ResourceFeatureAction): ResourceFeatureEffect? = when (action) {
        is ResourceFeatureAction.Refresh -> dispatchMutex.withLock {
            refresh(action.forceCatalogRefresh)
            null
        }
        ResourceFeatureAction.ReconcileFacts -> dispatchMutex.withLock {
            reconcileFacts()
            null
        }
        is ResourceFeatureAction.Primary -> requestAction(action)
        is ResourceFeatureAction.Secondary -> requestAction(action)
        is ResourceFeatureAction.Explicit -> requestAction(action)
    }

    /** 用户动作只读取最近一次投影，不得排在目录刷新或事实校准之后。 */
    fun requestAction(action: ResourceFeatureAction.Primary): ResourceFeatureEffect =
        requestPrimary(action)

    fun requestAction(action: ResourceFeatureAction.Secondary): ResourceFeatureEffect =
        requestSecondary(action)

    fun requestAction(action: ResourceFeatureAction.Explicit): ResourceFeatureEffect =
        requestExplicit(action)

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
            installPlanInProgress = facts.installPlanInProgress,
            uninstalling = facts.uninstalling,
            failed = facts.failed,
            failedOperation = facts.failedOperation,
            currentOperation = facts.currentOperation,
            idleStateLabel = facts.idleStateLabel,
            updateAvailable = registryEntry?.updateStatus == KiteResourceInstallStore.UPDATE_STATUS_AVAILABLE,
            openRunStatus = openRunStatus,
            extraBusy = facts.extraBusy
        )
        val operation = when {
            facts.uninstalling -> KiteResourceInstallStore.OP_UNINSTALL
            facts.failed && facts.failedOperation == KiteResourceInstallStore.OP_UNINSTALL ->
                KiteResourceInstallStore.OP_UNINSTALL
            facts.currentOperation.isNotBlank() -> facts.currentOperation
            else -> KiteResourceInstallStore.OP_INSTALL
        }
        val repairRequired = registryEntry?.status == KiteResourceInstallStore.STATUS_INSTALLED &&
            registryEntry.operation == KiteResourceInstallRecipes.OP_REPAIR &&
            registryEntry.updateStatus == KiteResourceInstallStore.UPDATE_STATUS_FAILED
        return ResourceItemUiState(
            descriptor = descriptor,
            phase = resourcePhase(facts, openRunStatus),
            projection = projection,
            primaryIntent = if (repairRequired) {
                KiteResourceActionIntent.Repair
            } else {
                KiteResourceActionCoordinator.primaryIntent(
                    projection.actionLabel,
                    reopenInstall = facts.installPlanInProgress
                )
            },
            secondaryIntent = secondaryIntent(projection.secondaryActionLabel, facts),
            operation = operation,
            operationRun = gateway.operationRunSnapshot(descriptor.id, operation),
            registrySummary = registryEntry?.summary.orEmpty(),
            registryUpdatedAt = registryEntry?.updatedAt ?: 0L,
            maintenance = maintenanceState(descriptor, registryEntry, facts)
        )
    }

    private fun maintenanceState(
        descriptor: ResourceFeatureDescriptor,
        registryEntry: com.kite.app.resources.KiteResourceRegistryEntry?,
        facts: KiteResourceRuntimeFacts
    ): ResourceMaintenanceUiState {
        val manifest = descriptor.manifest ?: return ResourceMaintenanceUiState()
        val plan = KiteResourceSourcePlanFactory.plan(manifest)
        val userManaged = manifest.management.userLifecycleEnabled
        val idle = !facts.extraBusy && !facts.preparing && !facts.installing && !facts.uninstalling
        val repairRequired = registryEntry?.status == KiteResourceInstallStore.STATUS_INSTALLED &&
            registryEntry.operation == KiteResourceInstallRecipes.OP_REPAIR &&
            registryEntry.updateStatus == KiteResourceInstallStore.UPDATE_STATUS_FAILED
        return ResourceMaintenanceUiState(
            userLifecycleEnabled = userManaged,
            installedVersion = registryEntry?.version.orEmpty(),
            latestVersion = registryEntry?.latestVersion.orEmpty(),
            updateStatus = registryEntry?.updateStatus.orEmpty(),
            statusSummary = registryEntry?.summary.orEmpty(),
            checkUpdateEnabled = userManaged && facts.installed && idle && plan.capabilities.checkUpdate,
            updateEnabled = userManaged && facts.installed && idle && plan.capabilities.update &&
                registryEntry?.updateStatus == KiteResourceInstallStore.UPDATE_STATUS_AVAILABLE &&
                registryEntry.latestVersion.isNotBlank(),
            repairEnabled = userManaged && repairRequired && idle && plan.capabilities.install,
            reinstallEnabled = userManaged && facts.installed && idle && plan.capabilities.install && plan.capabilities.uninstall,
            uninstallEnabled = userManaged && facts.installed && idle && plan.capabilities.uninstall
        )
    }

    private fun projectPlan(
        plan: com.kite.app.resources.KiteResourcePlanSnapshot,
        itemStates: List<ResourceItemUiState>
    ): ResourcePlanUiState {
        val byId = itemStates.associateBy(ResourceItemUiState::resourceId)
        return ResourcePlanUiState(
            targetResourceId = plan.targetResourceId,
            isPreparing = plan.isPreparing,
            isActive = plan.isActive,
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
        if (!item.projection.actionEnabled && item.primaryIntent !in setOf(
                KiteResourceActionIntent.ReopenInstall,
                KiteResourceActionIntent.ReopenOperation,
            )
        ) {
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

    private fun requestExplicit(action: ResourceFeatureAction.Explicit): ResourceFeatureEffect {
        val item = state.value.item(action.resourceId)
            ?: return ResourceFeatureEffect.ActionUnavailable(action.resourceId, "resource_not_in_catalog")
        val allowed = when (action.intent) {
            KiteResourceActionIntent.CheckUpdate -> item.maintenance.checkUpdateEnabled
            KiteResourceActionIntent.Update -> item.maintenance.updateEnabled
            KiteResourceActionIntent.Repair -> item.maintenance.repairEnabled
            KiteResourceActionIntent.Reinstall -> item.maintenance.reinstallEnabled
            KiteResourceActionIntent.Uninstall -> item.maintenance.uninstallEnabled
            else -> false
        }
        if (!allowed) return ResourceFeatureEffect.ActionUnavailable(action.resourceId, "maintenance_action_unavailable")
        return ResourceFeatureEffect.ActionRequested(
            KiteResourceActionRequest(action.resourceId, action.intent, action.source)
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
        facts.installPlanInProgress -> ResourceItemPhase.Installing
        facts.preparing -> ResourceItemPhase.Preparing
        facts.uninstalling -> ResourceItemPhase.Uninstalling
        facts.failed && facts.failedOperation == KiteResourceInstallStore.OP_UNINSTALL ->
            ResourceItemPhase.UninstallFailed
        facts.failed -> ResourceItemPhase.InstallFailed
        facts.installing -> ResourceItemPhase.Installing
        openRunStatus == CardRunStatus.Starting -> ResourceItemPhase.Starting
        openRunStatus == CardRunStatus.Stopping -> ResourceItemPhase.Stopping
        openRunStatus == CardRunStatus.CleanupPending -> ResourceItemPhase.Stopping
        openRunStatus == CardRunStatus.Running ||
            openRunStatus == CardRunStatus.AlreadyRunning ||
            openRunStatus == CardRunStatus.Opened ||
            openRunStatus == CardRunStatus.WaitingTerminal -> ResourceItemPhase.Running
        facts.installed -> ResourceItemPhase.Installed
        facts.extraBusy -> ResourceItemPhase.Busy
        else -> ResourceItemPhase.NotInstalled
    }
}
