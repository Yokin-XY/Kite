package com.kite.app.feature.resources

import android.content.Context
import com.kite.app.R
import com.kite.app.action.KiteInstallPlanActionCoordinator
import com.kite.app.action.KiteInstallPlanActionIntent
import com.kite.app.application.resources.ResourceFeatureRunSnapshot
import com.kite.app.resources.KiteResourceInstallStepUiProjection
import com.kite.app.resources.KiteResourceInstallStepUiProjector
import com.kite.app.resources.KiteResourceInstallStore
import com.kite.app.resources.KiteResourceInstallOutput
import com.kite.app.run.CardRunStatus
import com.kite.app.run.CardRunSurface

internal enum class ResourceInstallWizardPlanActionResult {
    Accepted,
    Deferred,
    Rejected,
}

internal enum class ResourceInstallWizardHeaderState {
    Syncing,
    Running,
    Failure,
    Pending,
    Completed,
}

internal data class ResourceInstallWizardRunRequest(
    val resourceId: String,
    val operation: String,
    val instanceId: String,
    val surface: CardRunSurface,
)

internal data class ResourceInstallWizardRowViewState(
    val resourceId: String,
    val name: String,
    val sourceLabel: String,
    val index: Int,
    val total: Int,
    val isActive: Boolean,
    val isCalibrating: Boolean,
    val projection: KiteResourceInstallStepUiProjection,
    val operation: String,
    val run: ResourceFeatureRunSnapshot?
)

internal data class ResourceInstallWizardViewState(
    val targetResourceId: String,
    val title: String,
    val detail: String,
    val completedCount: Int,
    val totalCount: Int,
    val primaryLabel: String,
    val primaryEnabled: Boolean,
    val primaryIntent: KiteInstallPlanActionIntent?,
    val headerState: ResourceInstallWizardHeaderState,
    val rows: List<ResourceInstallWizardRowViewState>
)

internal object ResourceInstallWizardPresenter {
    fun project(
        context: Context,
        state: ResourceFeatureUiState,
        requestedTargetResourceId: String,
        seedResourceIds: List<String>
    ): ResourceInstallWizardViewState {
        val factPlanOwnsPresentation = state.plan.isPreparing || state.plan.isActive
        val targetResourceId = if (factPlanOwnsPresentation) {
            state.plan.targetResourceId.ifBlank { requestedTargetResourceId }
        } else {
            requestedTargetResourceId.ifBlank { state.plan.targetResourceId }
        }
        val resourceIds = if (factPlanOwnsPresentation) {
            state.plan.resourceIds
        } else {
            seedResourceIds
                .filter(String::isNotBlank)
                .distinct()
                .ifEmpty { state.plan.resourceIds }
                .ifEmpty { state.plan.pendingResourceIds }
                .ifEmpty { listOfNotNull(targetResourceId.takeIf(String::isNotBlank)) }
        }
        val stepsById = state.plan.steps.associateBy(ResourcePlanStepUiState::resourceId)
        val itemsById = state.items.associateBy(ResourceItemUiState::resourceId)
        val initialRows = resourceIds.mapIndexed { index, resourceId ->
            val item = itemsById[resourceId]
            val step = stepsById[resourceId]
            val isCalibrating = (item == null && step == null) || (
                item?.phase == ResourceItemPhase.NotInstalled &&
                    step == null &&
                    resourceId !in state.plan.pendingResourceIds
            )
            val operation = step?.operation
                ?.takeIf(String::isNotBlank)
                ?: item?.operation?.takeIf(String::isNotBlank)
                ?: KiteResourceInstallStore.OP_INSTALL
            ResourceInstallWizardRowViewState(
                resourceId = resourceId,
                name = item?.presentation(context)?.name ?: resourceId,
                sourceLabel = item?.presentation(context)?.sourceLabel.orEmpty()
                    .ifBlank { context.getString(R.string.resource_wizard_fallback_resource) },
                index = index,
                total = resourceIds.size,
                isActive = false,
                isCalibrating = isCalibrating,
                projection = step?.projection ?: fallbackProjection(item, resourceId in resourceIds),
                operation = operation,
                run = step?.run ?: item?.operationRun
            )
        }
        val hasRunningStep = initialRows.any { row ->
            row.resourceId in state.plan.runningResourceIds || row.projection.statusLabel == "获取中"
        }
        val hasUninstallingStep = initialRows.any { it.projection.uninstalling }
        val hasFailure = initialRows.any { it.projection.failed && !it.projection.uninstalling }
        val isPreparingPlan = state.plan.isPreparing
        val isCalibrating = !isPreparingPlan && initialRows.any(ResourceInstallWizardRowViewState::isCalibrating)
        val pendingIds = state.plan.pendingResourceIds.filter(resourceIds::contains)
        val hasPending = pendingIds.isNotEmpty() && !hasFailure
        val activeResourceId = initialRows.firstOrNull { row ->
            row.resourceId in state.plan.runningResourceIds || row.projection.statusLabel == "获取中"
        }?.resourceId
            ?: initialRows.firstOrNull { it.projection.uninstalling }?.resourceId
            ?: initialRows.firstOrNull { it.projection.failed }?.resourceId
            ?: pendingIds.firstOrNull()
        val rows = initialRows.map { row -> row.copy(isActive = row.resourceId == activeResourceId) }
        val completedCount = rows.count { row ->
            !row.projection.failed && (
                row.projection.statusLabel == "已完成" ||
                    itemsById[row.resourceId]?.phase in installedPhases
                )
        }
        val targetName = itemsById[targetResourceId]?.presentation(context)?.name
            ?: targetResourceId.ifBlank { context.getString(R.string.resource_wizard_install_task) }
        val detail = when {
            isPreparingPlan -> context.getString(R.string.resource_wizard_detail_preparing)
            isCalibrating -> context.getString(R.string.resource_wizard_detail_syncing)
            hasRunningStep -> context.getString(
                R.string.resource_wizard_detail_installing,
                rows.firstOrNull { it.resourceId == activeResourceId }?.name.orEmpty()
            )
            hasUninstallingStep -> context.getString(
                R.string.resource_wizard_detail_uninstalling,
                rows.firstOrNull { it.resourceId == activeResourceId }?.name.orEmpty()
            )
            hasFailure -> context.getString(R.string.resource_wizard_detail_failure)
            hasPending -> context.getString(R.string.resource_wizard_detail_pending, pendingIds.size)
            else -> context.getString(R.string.resource_wizard_detail_completed)
        }
        val action = KiteInstallPlanActionCoordinator.plan(
            hasRunningStep = hasRunningStep,
            hasUninstallingStep = hasUninstallingStep,
            hasPending = hasPending,
            hasFailure = hasFailure
        )
        return ResourceInstallWizardViewState(
            targetResourceId = targetResourceId,
            title = targetName,
            detail = detail,
            completedCount = completedCount.coerceIn(0, resourceIds.size),
            totalCount = resourceIds.size,
            primaryLabel = when {
                isPreparingPlan -> context.getString(R.string.resource_state_preparing)
                isCalibrating -> context.getString(R.string.resource_wizard_status_syncing)
                hasUninstallingStep -> context.getString(R.string.resource_state_uninstalling)
                hasFailure -> context.getString(R.string.resource_wizard_detail_failure)
                hasRunningStep -> context.getString(R.string.resource_state_installing)
                hasPending -> context.getString(R.string.resource_wizard_action_start)
                else -> context.getString(R.string.resource_wizard_action_complete)
            },
            primaryEnabled = action.enabled && !isPreparingPlan && !isCalibrating,
            primaryIntent = action.intent.takeUnless { isPreparingPlan || isCalibrating },
            headerState = when {
                isPreparingPlan -> ResourceInstallWizardHeaderState.Syncing
                isCalibrating -> ResourceInstallWizardHeaderState.Syncing
                hasRunningStep || hasUninstallingStep -> ResourceInstallWizardHeaderState.Running
                hasFailure -> ResourceInstallWizardHeaderState.Failure
                hasPending -> ResourceInstallWizardHeaderState.Pending
                else -> ResourceInstallWizardHeaderState.Completed
            },
            rows = rows
        )
    }

    private fun fallbackProjection(
        item: ResourceItemUiState?,
        isActive: Boolean
    ): KiteResourceInstallStepUiProjection {
        val failedOperation = if (item?.phase == ResourceItemPhase.UninstallFailed) {
            KiteResourceInstallStore.OP_UNINSTALL
        } else {
            KiteResourceInstallStore.OP_INSTALL
        }
        return KiteResourceInstallStepUiProjector.project(
            uninstalling = item?.phase == ResourceItemPhase.Uninstalling,
            failed = item?.phase == ResourceItemPhase.InstallFailed ||
                item?.phase == ResourceItemPhase.UninstallFailed,
            failedOperation = failedOperation,
            planStepStatus = "",
            installed = item?.phase in installedPhases,
            isActive = isActive
        )
    }

    private val installedPhases = setOf(
        ResourceItemPhase.Installed,
        ResourceItemPhase.Starting,
        ResourceItemPhase.Running,
        ResourceItemPhase.Stopping
    )
}

internal fun ResourceFeatureRunSnapshot.isLiveForWizard(): Boolean =
    status == CardRunStatus.Starting ||
        status == CardRunStatus.Stopping ||
        status == CardRunStatus.Running ||
        status == CardRunStatus.WaitingTerminal ||
        status == CardRunStatus.AlreadyRunning ||
        status == CardRunStatus.Opened

internal fun ResourceInstallWizardRowViewState.runRequest(
    surface: CardRunSurface,
): ResourceInstallWizardRunRequest? = run?.let { currentRun ->
    ResourceInstallWizardRunRequest(
        resourceId = resourceId,
        operation = operation,
        instanceId = currentRun.instanceId,
        surface = surface,
    )
}

internal fun ResourceInstallWizardRowViewState.subtitle(context: Context, now: Long): String {
    val base = "$sourceLabel · ${index + 1}/$total"
    val currentRun = run ?: return base
    val endAt = if (currentRun.isLiveForWizard()) now else currentRun.updatedAt
    val seconds = ((endAt - currentRun.startedAt).coerceAtLeast(0L) / 1000L)
    val elapsed = when {
        seconds < 60L * 60L -> String.format("%02d:%02d", seconds / 60L, seconds % 60L)
        seconds < 24L * 60L * 60L -> context.getString(
            R.string.resource_wizard_duration_hours,
            seconds / (60L * 60L)
        )
        else -> context.getString(R.string.resource_wizard_duration_days, seconds / (24L * 60L * 60L))
    }
    val timing = when {
        currentRun.isLiveForWizard() -> context.getString(R.string.resource_wizard_subtitle_running, base, elapsed)
        currentRun.status == CardRunStatus.Completed ->
            context.getString(R.string.resource_wizard_subtitle_duration, base, elapsed)
        currentRun.status == CardRunStatus.Failed || currentRun.status == CardRunStatus.BridgeUnavailable ->
            context.getString(R.string.resource_wizard_subtitle_failed, base, elapsed)
        currentRun.status == CardRunStatus.Stopped ->
            context.getString(R.string.resource_wizard_subtitle_stopped, base, elapsed)
        else -> base
    }
    val progress = currentRun.progressText.trim()
    val detail = KiteResourceInstallOutput.progressDetail(currentRun.reportText)
    return listOf(timing, progress, detail)
        .filterNotNull()
        .filter(String::isNotBlank)
        .distinct()
        .joinToString("\n")
}
