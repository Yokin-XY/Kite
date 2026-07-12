package com.kite.app.feature.resources

import com.kite.app.action.KiteInstallPlanActionCoordinator
import com.kite.app.action.KiteInstallPlanActionIntent
import com.kite.app.application.resources.ResourceFeatureRunSnapshot
import com.kite.app.resources.KiteResourceInstallStepUiProjection
import com.kite.app.resources.KiteResourceInstallStepUiProjector
import com.kite.app.resources.KiteResourceInstallStore
import com.kite.app.run.CardRunSurface
import com.kite.app.run.CardRunStatus

internal data class ResourceInstallWizardRunRequest(
    val resourceId: String,
    val operation: String,
    val instanceId: String,
    val surface: CardRunSurface
)

internal data class ResourceInstallWizardRowViewState(
    val resourceId: String,
    val name: String,
    val sourceLabel: String,
    val index: Int,
    val total: Int,
    val isActive: Boolean,
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
    val rows: List<ResourceInstallWizardRowViewState>
)

internal object ResourceInstallWizardPresenter {
    fun project(
        state: ResourceFeatureUiState,
        requestedTargetResourceId: String,
        seedResourceIds: List<String>
    ): ResourceInstallWizardViewState {
        val targetResourceId = requestedTargetResourceId.ifBlank { state.plan.targetResourceId }
        val resourceIds = seedResourceIds
            .filter(String::isNotBlank)
            .distinct()
            .ifEmpty { state.plan.resourceIds }
            .ifEmpty { state.plan.pendingResourceIds }
        val stepsById = state.plan.steps.associateBy(ResourcePlanStepUiState::resourceId)
        val itemsById = state.items.associateBy(ResourceItemUiState::resourceId)
        val initialRows = resourceIds.mapIndexed { index, resourceId ->
            val item = itemsById[resourceId]
            val step = stepsById[resourceId]
            val operation = step?.operation
                ?.takeIf(String::isNotBlank)
                ?: item?.operation?.takeIf(String::isNotBlank)
                ?: KiteResourceInstallStore.OP_INSTALL
            ResourceInstallWizardRowViewState(
                resourceId = resourceId,
                name = item?.presentation()?.name ?: resourceId,
                sourceLabel = item?.presentation()?.sourceLabel.orEmpty().ifBlank { "资源" },
                index = index,
                total = resourceIds.size,
                isActive = false,
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
        val targetName = itemsById[targetResourceId]?.presentation()?.name
            ?: targetResourceId.ifBlank { "获取任务" }
        val detail = when {
            hasRunningStep -> "正在获取：${rows.firstOrNull { it.resourceId == activeResourceId }?.name.orEmpty()}"
            hasUninstallingStep -> "正在卸载：${rows.firstOrNull { it.resourceId == activeResourceId }?.name.orEmpty()}"
            hasFailure -> "发现异常请手动处理"
            hasPending -> "将按顺序获取 ${resourceIds.size} 个资源"
            else -> "执行队列已完成"
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
            primaryLabel = action.label,
            primaryEnabled = action.enabled,
            primaryIntent = action.intent,
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

internal fun ResourceInstallWizardRowViewState.subtitle(now: Long): String {
    val base = "$sourceLabel · ${index + 1}/$total"
    val currentRun = run ?: return base
    val endAt = if (currentRun.isLiveForWizard()) now else currentRun.updatedAt
    val seconds = ((endAt - currentRun.startedAt).coerceAtLeast(0L) / 1000L)
    val elapsed = when {
        seconds < 60L * 60L -> String.format("%02d:%02d", seconds / 60L, seconds % 60L)
        seconds < 24L * 60L * 60L -> "${seconds / (60L * 60L)}小时"
        else -> "${seconds / (24L * 60L * 60L)}天"
    }
    return when {
        currentRun.isLiveForWizard() -> "$base · 运行 $elapsed"
        currentRun.status == CardRunStatus.Completed -> "$base · 用时 $elapsed"
        currentRun.status == CardRunStatus.Failed || currentRun.status == CardRunStatus.BridgeUnavailable ->
            "$base · 失败 $elapsed"
        currentRun.status == CardRunStatus.Stopped -> "$base · 已停止 $elapsed"
        else -> base
    }
}
