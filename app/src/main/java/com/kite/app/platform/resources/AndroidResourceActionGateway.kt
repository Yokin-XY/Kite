package com.kite.app.platform.resources

import android.content.Context
import com.kite.app.application.recipes.RecipeFeatureGateway
import com.kite.app.application.resources.ResourceActionEffect
import com.kite.app.application.resources.ResourceActionGateway
import com.kite.app.application.resources.ResourceActionMessagePresentation
import com.kite.app.application.resources.FailedResourceRecoveryPolicy
import com.kite.app.application.resources.ResourceDependencyGuard
import com.kite.app.application.resources.ResourceInstallPreparationFlights
import com.kite.app.application.resources.ResourceInstallPreparationToken
import com.kite.app.application.resources.ResourcePlanLifecycleGate
import com.kite.app.application.resources.ResourcePlanCancellationPolicy
import com.kite.app.application.resources.ResourceVersionCheckResult
import com.kite.app.application.resources.ResourceVersionBatchScheduler
import com.kite.app.application.resources.ResourceVersionBatchSummary
import com.kite.app.application.resources.ResourceVersionCoordinator
import com.kite.app.application.resources.ResourceUpdateBatchPolicy
import com.kite.app.application.resources.ResourceRunContinuation
import com.kite.app.application.resources.ResourceRunCoordinator
import com.kite.app.application.resources.ResourceRunLaunchRequest
import com.kite.app.application.resources.ResourceRunLaunchResult
import com.kite.app.application.runs.RunCommandResult
import com.kite.app.application.runs.RUN_NOTIFICATIONS_REQUIRED
import com.kite.app.application.runs.RunOrchestrator
import com.kite.app.application.runs.RunStartRequest
import com.kite.app.bridge.KiteBridgeClient
import com.kite.app.diagnostics.KiteDiagnostics
import com.kite.app.application.runs.CardRunSpecialRecipes
import com.kite.app.foundation.runtime.RuntimeOwnerIdentity
import com.kite.app.foundation.runtime.RuntimeOwnerNamespace
import com.kite.app.foundation.runtime.RuntimeLaunchTrace
import com.kite.app.foundation.logging.Logger
import com.kite.app.foundation.toolchain.ToolchainPackInstaller
import com.kite.app.foundation.workspace.WorkSurfaceRuntimeBridge
import com.kite.app.recipe.KiteExecution
import com.kite.app.recipe.KiteLaunchConfig
import com.kite.app.recipe.KiteRecipe
import com.kite.app.recipe.KiteRecipeIcon
import com.kite.app.recipe.KiteRecipeLoader
import com.kite.app.recipe.KiteRecipeStep
import com.kite.app.resources.KiteResourceInstallRecipes
import com.kite.app.resources.KiteResourceInstallContract
import com.kite.app.resources.KiteResourceInstallContractResolution
import com.kite.app.resources.KiteResourceInstallStore
import com.kite.app.resources.KiteResourceRegistry
import com.kite.app.resources.KiteResourceManagementMode
import com.kite.app.resources.KiteResourceManifest
import com.kite.app.resources.KiteResourceManifestLoader
import com.kite.app.resources.KiteResourcePlanSnapshot
import com.kite.app.resources.KiteResourceRequestPolicy
import com.kite.app.resources.KiteResourceSourcePlanFactory
import com.kite.app.run.CardRunState
import com.kite.app.run.CardRunStatus
import com.kite.app.run.CardRunStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.util.UUID
import kotlin.coroutines.resume

/** 资源动作的 Android/Store 适配器；不持有 Activity、View 或 Feature 状态。 */
internal class AndroidResourceActionGateway(
    context: Context,
    private val installStore: KiteResourceInstallStore,
    private val manifestLoader: KiteResourceManifestLoader,
    private val runCoordinator: ResourceRunCoordinator,
    private val runOrchestrator: RunOrchestrator,
    private val recipeLoader: KiteRecipeLoader,
    private val recipeFeatureGateway: RecipeFeatureGateway,
    private val bridgeClient: KiteBridgeClient,
    private val diagnostics: KiteDiagnostics,
    private val versionCoordinator: ResourceVersionCoordinator,
    private val versionBatchObserver: (ResourceVersionBatchSummary) -> Unit = {},
    private val backgroundScope: CoroutineScope,
    private val environmentFor: (String) -> Map<String, String> = { emptyMap() },
    private val installedStateProbe: ResourceInstalledStateProbe =
        AndroidResourceInstalledStateProbe(bridgeClient),
    private val managedCommandEvidence: ResourceManagedCommandEvidenceCoordinator =
        ResourceManagedCommandEvidenceCoordinator { message ->
            Logger.i("ResourceManagedCommandProof", message)
        },
) : ResourceActionGateway {
    private data class PlanCancelOutcome(
        val accepted: Boolean,
        val effects: List<ResourceActionEffect>,
    )

    private data class OpenInstallPreflight(
        val repairResourceIds: Set<String>,
        val updateResourceIds: Set<String>,
        val missingResourceIds: Set<String>,
        val unresolvedRequirements: Set<String>,
    ) {
        val requiresRepair: Boolean
            get() = repairResourceIds.isNotEmpty()

        val requiresUpdate: Boolean
            get() = updateResourceIds.isNotEmpty()

        val requiresInstall: Boolean
            get() = missingResourceIds.isNotEmpty() || unresolvedRequirements.isNotEmpty()
    }

    private val appContext = context.applicationContext
    private val systemManagedResourceFactsReconciler = SystemManagedResourceFactsReconciler(
        installStore = installStore,
        installedStateProbe = installedStateProbe,
    )
    private val androidPackageResourceFactsReconciler = AndroidPackageResourceFactsReconciler(
        androidContext = appContext,
        installStore = installStore,
    )
    private val preparationFlights = ResourceInstallPreparationFlights(backgroundScope)
    private val planLifecycleGate = ResourcePlanLifecycleGate()
    private val openRunStarter = ResourceOpenRunStarter(
        startRun = runOrchestrator::start,
        stateFor = { instanceId, environmentId -> CardRunStore.get(instanceId, environmentId) },
    )

    override suspend fun install(resourceId: String): List<ResourceActionEffect> {
        val environmentId = installStore.currentEnvironmentId()
        return planLifecycleGate.withEnvironment(environmentId) {
            installLocked(resourceId, environmentId)
        }
    }

    private suspend fun installLocked(
        resourceId: String,
        environmentId: String,
    ): List<ResourceActionEffect> {
        val target = target(resourceId) ?: return message("资源目录正在更新，请稍后重试")
        if (!target.manifest.management.userLifecycleEnabled) {
            return message("${target.name} 是 Kite 管理的系统组件，无需单独获取")
        }
        if (installStore.isFailed(target.id, environmentId) &&
            installStore.failedOperation(target.id, environmentId) == KiteResourceInstallStore.OP_UNINSTALL
        ) {
            return recoverFailedInstallLocked(
                target = target,
                parentInstanceId = null,
                environmentId = environmentId,
            )
        }
        if (installStore.isFailed(target.id, environmentId) &&
            installStore.failedOperation(target.id, environmentId) != KiteResourceInstallStore.OP_UNINSTALL
        ) {
            return uninstall(
                target = target,
                continuation = ResourceRunContinuation.Reinstall,
                environmentId = environmentId,
                restartInstall = { installLocked(target.id, environmentId) },
            )
        }

        val currentPlan = reconcileInstalledPlanSteps(
            installStore.planSnapshot(environmentId),
            environmentId,
        )
        if (currentPlan.targetResourceId.isNotBlank()) {
            return openCurrentInstallPlan(target, currentPlan, environmentId)
        }
        if (!installStore.beginPreparingPlan(target.id, environmentId)) {
            return openCurrentInstallPlan(target, installStore.planSnapshot(environmentId), environmentId)
        }
        installStore.markPreparing(target.id, environmentId)
        val effect = installWizardEffect(
            target = target,
            planResourceIds = emptyList(),
            environmentId = environmentId,
            reuseCurrent = false,
        )
        launchInstallPreparation(target, effect, environmentId)
        return listOf(effect)
    }

    override suspend fun reopenInstall(resourceId: String): List<ResourceActionEffect> {
        val environmentId = installStore.currentEnvironmentId()
        return planLifecycleGate.withEnvironment(environmentId) {
            val target = target(resourceId)
                ?: return@withEnvironment message("资源目录正在更新，请稍后重试")
            openCurrentInstallPlan(target, installStore.planSnapshot(environmentId), environmentId)
        }
    }

    override suspend fun open(resourceId: String): List<ResourceActionEffect> {
        val environmentId = installStore.currentEnvironmentId()
        val target = target(resourceId) ?: return message("资源目录正在更新，请稍后重试")
        val recipe = openRecipe(target) ?: return message("${target.name} 的打开动作稍后接入")
        CardRunStore.registerRecipe(recipe)
        val existing = CardRunStore.currentForRecipe(recipe.id, environmentId)
        val instanceId = existing?.instanceId
            ?: CardRunState.instanceIdForEnvironment(recipe.id, environmentId)
        RuntimeLaunchTrace.begin(instanceId, RuntimeLaunchTrace.ACTION_RECEIVED)
        if (existing?.status == CardRunStatus.Stopping) {
            return message("${target.name} 正在停止，请稍后再启动")
        }
        if (existing != null && existing.isReusableOpenRun()) {
            RuntimeLaunchTrace.mark(instanceId, RuntimeLaunchTrace.EXISTING_RUN_REUSED)
            return listOf(
                resourceOpenEffect(existing),
                ResourceActionEffect.Message("${target.name} 已在运行，正在打开原实例")
            )
        }
        RuntimeLaunchTrace.mark(instanceId, RuntimeLaunchTrace.RESOURCE_PREFLIGHT_STARTED)
        val preflight = withContext(Dispatchers.IO) {
            reconcileOpenInstallation(target, environmentId)
        }
        RuntimeLaunchTrace.mark(instanceId, RuntimeLaunchTrace.RESOURCE_PREFLIGHT_COMPLETED)
        if (preflight.requiresRepair) {
            RuntimeLaunchTrace.mark(instanceId, RuntimeLaunchTrace.RESOURCE_PREFLIGHT_INVALIDATED)
            val repairNames = preflight.repairResourceIds.joinToString("、") { resourceId ->
                manifestLoader.requestManifest(resourceId)?.name?.ifBlank { resourceId } ?: resourceId
            }
            return message("检测到需要修复的安装：$repairNames。原有版本仍保留，请先点击“修复”")
        }
        if (preflight.requiresUpdate) {
            RuntimeLaunchTrace.mark(instanceId, RuntimeLaunchTrace.RESOURCE_PREFLIGHT_INVALIDATED)
            val updateNames = preflight.updateResourceIds.joinToString("、") { resourceId ->
                manifestLoader.requestManifest(resourceId)?.name?.ifBlank { resourceId } ?: resourceId
            }
            return message("检测到可用更新：$updateNames。请先点击“更新”")
        }
        if (preflight.requiresInstall) {
            RuntimeLaunchTrace.mark(instanceId, RuntimeLaunchTrace.RESOURCE_PREFLIGHT_INVALIDATED)
            return listOf(ResourceActionEffect.Message("检测到 ${target.name} 的运行依赖尚未就绪，正在准备")) +
                install(target.id)
        }
        RuntimeLaunchTrace.mark(instanceId, RuntimeLaunchTrace.ACTION_DISPATCHED)
        return openRunStarter.start(
            request = RunStartRequest(
                recipe = recipe,
                instanceId = instanceId,
                ownerKind = CardRunState.OWNER_KIND_RESOURCE,
                stepId = target.id,
                environmentId = environmentId
            ),
            resourceName = target.name,
            opensRunSurface = shouldOpenRunTask(recipe),
        )
    }

    override suspend fun stop(resourceId: String): List<ResourceActionEffect> {
        val environmentId = installStore.currentEnvironmentId()
        val target = target(resourceId) ?: return message("资源目录正在更新，请稍后重试")
        val recipe = openRecipe(target) ?: return message("${target.name} 的运行入口不存在")
        CardRunStore.registerRecipe(recipe)
        val state = CardRunStore.currentForRecipe(recipe.id, environmentId)?.takeIf { it.isReusableOpenRun() }
            ?: return message("${target.name} 没有运行中的实例")
        return when (val result = runOrchestrator.stop(state.instanceId)) {
            is RunCommandResult.Accepted -> message("正在中止 ${target.name}")
            is RunCommandResult.Ignored -> message("无法中止 ${target.name}：${result.reason}")
        }
    }

    override suspend fun uninstall(resourceId: String): List<ResourceActionEffect> {
        val target = target(resourceId) ?: return message("资源目录正在更新，请稍后重试")
        if (target.manifest.management.mode == KiteResourceManagementMode.SYSTEM_COMPONENT) {
            return message("${target.name} 是 Kite 管理的系统组件，不能单独卸载")
        }
        val blockers = uninstallBlockers(target.id)
        if (blockers.isNotEmpty()) {
            val names = blockers.map { it.resourceName }.distinct().joinToString("、")
            return message("${target.name} 正被 $names 使用，请先卸载这些资源")
        }
        return uninstall(target, ResourceRunContinuation.None)
    }

    override suspend fun checkUpdate(resourceId: String): List<ResourceActionEffect> {
        val environmentId = installStore.currentEnvironmentId()
        val target = target(resourceId) ?: return message("资源目录正在更新，请稍后重试")
        if (!target.manifest.management.userLifecycleEnabled) {
            return message("${target.name} 是 Kite 管理的系统组件")
        }
        if (!installStore.isInstalled(target.id, environmentId)) return message("请先获取 ${target.name}")
        installStore.markUpdateChecking(target.id, environmentId)
        val result = withContext(Dispatchers.IO) {
            versionCoordinator.check(target.manifest, environmentId)
        }
        return message(applyUpdateCheckResult(target, result, environmentId).message)
    }

    override suspend fun checkUpdates(resourceIds: List<String>): List<ResourceActionEffect> {
        val environmentId = installStore.currentEnvironmentId()
        val targets = resourceIds.asSequence()
            .map(String::trim)
            .filter(String::isNotBlank)
            .distinct()
            .mapNotNull(::target)
            .filter { target ->
                ResourceUpdateBatchPolicy.isEligible(
                    target.manifest,
                    installStore.isInstalled(target.id, environmentId)
                )
            }
            .toList()
        if (targets.isEmpty()) return message("已获取资源中暂无可检查更新的项目")

        // 先一次性写入所有目标的乐观状态，再开始任何网络或命令探测。
        targets.forEach { target -> installStore.markUpdateChecking(target.id, environmentId) }
        val results = withContext(Dispatchers.IO) {
            // 所有车道先在业务进程前完成结构化预检；事实不足的整项进入单槽兼容队列。
            val prepared = targets.map { target ->
                target to versionCoordinator.prepareBatchCheck(target.manifest, environmentId)
            }
            ResourceVersionBatchScheduler.executeOrdered(
                requests = prepared,
                laneOf = { preparedRequest -> preparedRequest.second.lane },
                observer = versionBatchObserver,
            ) { preparedRequest ->
                preparedRequest.first to versionCoordinator.check(preparedRequest.second)
            }
        }.map { (target, result) -> applyUpdateCheckResult(target, result, environmentId) }

        val available = results.count { it.outcome == UpdateCheckOutcome.Available }
        val current = results.count { it.outcome == UpdateCheckOutcome.Current }
        val failed = results.size - available - current
        val summary = buildList {
            add("已检查 ${results.size} 项")
            if (available > 0) add("$available 项可更新")
            if (current > 0) add("$current 项已是最新")
            if (failed > 0) add("$failed 项未完成")
        }.joinToString("，")
        return message(summary)
    }

    private fun applyUpdateCheckResult(
        target: ResourceTarget,
        result: ResourceVersionCheckResult,
        environmentId: String
    ): AppliedUpdateCheckResult = when (result) {
            is ResourceVersionCheckResult.UpdateAvailable -> {
                installStore.markUpdateAvailable(target.id, result.installedVersion, result.latestVersion, environmentId)
                AppliedUpdateCheckResult(
                    UpdateCheckOutcome.Available,
                    "${target.name} 可更新：${result.installedVersion} → ${result.latestVersion}"
                )
            }
            is ResourceVersionCheckResult.Current -> {
                installStore.markUpdateCurrent(target.id, result.installedVersion, result.latestVersion, environmentId)
                AppliedUpdateCheckResult(
                    UpdateCheckOutcome.Current,
                    if (result.locallyAhead) "${target.name} 当前版本高于发布版本" else "${target.name} 已是最新版本"
                )
            }
            is ResourceVersionCheckResult.Unsupported -> {
                installStore.markUpdateUnsupported(target.id, result.reason, environmentId)
                AppliedUpdateCheckResult(
                    UpdateCheckOutcome.Unsupported,
                    "${target.name} 暂不支持自动检查更新"
                )
            }
            is ResourceVersionCheckResult.Failed -> {
                installStore.markUpdateCheckFailed(target.id, result.reason, environmentId)
                AppliedUpdateCheckResult(
                    UpdateCheckOutcome.Failed,
                    "${target.name} 更新检查失败：${result.reason}"
                )
            }
        }

    override suspend fun update(resourceId: String): List<ResourceActionEffect> {
        val environmentId = installStore.currentEnvironmentId()
        val target = target(resourceId) ?: return maintenanceUnavailable(
            resourceId, "资源目录正在更新，请稍后重试", environmentId
        )
        if (!target.manifest.management.userLifecycleEnabled) return maintenanceUnavailable(
            target.id, "${target.name} 是 Kite 管理的系统组件", environmentId
        )
        if (!installStore.isInstalled(target.id, environmentId)) return maintenanceUnavailable(
            target.id, "请先获取 ${target.name}", environmentId
        )
        val entry = installStore.registryEntry(target.id, environmentId)
        val targetVersion = entry?.latestVersion.orEmpty()
        if (entry?.updateStatus != KiteResourceInstallStore.UPDATE_STATUS_AVAILABLE || targetVersion.isBlank()) {
            return maintenanceUnavailable(target.id, "请先检查 ${target.name} 的可用更新", environmentId)
        }
        if (!KiteResourceSourcePlanFactory.plan(target.manifest, targetVersion).capabilities.update) {
            return maintenanceUnavailable(target.id, "${target.name} 的来源暂不支持确定性更新", environmentId)
        }
        val recipe = runCoordinator.recipe(target.id, KiteResourceInstallRecipes.OP_UPDATE, targetVersion)
            ?: return maintenanceUnavailable(target.id, "${target.name} 的更新入口不存在", environmentId)
        return startManagedOperation(target, recipe, KiteResourceInstallRecipes.OP_UPDATE, targetVersion, environmentId)
    }

    override suspend fun reinstall(resourceId: String): List<ResourceActionEffect> {
        val environmentId = installStore.currentEnvironmentId()
        val target = target(resourceId) ?: return maintenanceUnavailable(
            resourceId, "资源目录正在更新，请稍后重试", environmentId
        )
        if (!target.manifest.management.userLifecycleEnabled) return maintenanceUnavailable(
            target.id, "${target.name} 是 Kite 管理的系统组件", environmentId
        )
        if (!installStore.isInstalled(target.id, environmentId)) return maintenanceUnavailable(
            target.id, "请先获取 ${target.name}", environmentId
        )
        val capabilities = KiteResourceSourcePlanFactory.plan(target.manifest).capabilities
        if (!capabilities.install || !capabilities.uninstall) return maintenanceUnavailable(
            target.id, "${target.name} 暂不支持重新安装", environmentId
        )
        val recipe = runCoordinator.recipe(target.id, KiteResourceInstallRecipes.OP_REINSTALL)
            ?: return maintenanceUnavailable(target.id, "${target.name} 的重新安装入口不存在", environmentId)
        return startManagedOperation(target, recipe, KiteResourceInstallRecipes.OP_REINSTALL, environmentId = environmentId)
    }

    override suspend fun repair(resourceId: String): List<ResourceActionEffect> {
        val environmentId = installStore.currentEnvironmentId()
        val target = target(resourceId) ?: return maintenanceUnavailable(
            resourceId, "资源目录正在更新，请稍后重试", environmentId
        )
        if (!target.manifest.management.userLifecycleEnabled) return maintenanceUnavailable(
            target.id, "${target.name} 是 Kite 管理的系统组件", environmentId
        )
        if (!installStore.isInstalled(target.id, environmentId)) return maintenanceUnavailable(
            target.id, "请先获取 ${target.name}", environmentId
        )
        if (!KiteResourceSourcePlanFactory.plan(target.manifest).capabilities.install) {
            return maintenanceUnavailable(target.id, "${target.name} 暂不支持修复安装", environmentId)
        }
        val recipe = runCoordinator.recipe(target.id, KiteResourceInstallRecipes.OP_REPAIR)
            ?: return maintenanceUnavailable(target.id, "${target.name} 的修复入口不存在", environmentId)
        return startManagedOperation(target, recipe, KiteResourceInstallRecipes.OP_REPAIR, environmentId = environmentId)
    }

    override suspend fun reopenOperation(resourceId: String): List<ResourceActionEffect> {
        val environmentId = installStore.currentEnvironmentId()
        val target = target(resourceId) ?: return explicitResult("资源目录正在更新，请稍后重试")
        val entry = installStore.registryEntry(target.id, environmentId)
            ?: return explicitResult("${target.name} 当前没有维护任务")
        if (!entry.installing || entry.operation !in KiteResourceInstallRecipes.MAINTENANCE_OPERATIONS) {
            return explicitResult("${target.name} 当前没有正在执行的维护任务")
        }
        val recipeId = KiteResourceInstallRecipes.recipeId(target.id, entry.operation)
        val run = entry.runId.takeIf(String::isNotBlank)
            ?.let { instanceId -> CardRunStore.get(instanceId, environmentId) }
            ?.takeIf { state -> state.recipeId == recipeId }
            ?: CardRunStore.currentForRecipe(recipeId, environmentId)
        if (run == null) {
            installStore.markMaintenanceFailed(
                resourceId = target.id,
                operation = entry.operation,
                explanation = "维护任务运行记录缺失，已恢复原有安装状态",
                environmentId = environmentId,
            )
            return explicitResult("${target.name} 的维护任务已经中断，原有安装仍可使用")
        }
        return listOf(resourceOpenEffect(run))
    }

    override suspend fun cancelInstall(resourceId: String): List<ResourceActionEffect> {
        val environmentId = installStore.currentEnvironmentId()
        val plan = installStore.planSnapshot(environmentId)
        val planIds = plan.resourceIds.takeIf { resourceId in it || resourceId == plan.targetResourceId }.orEmpty()
            .ifEmpty { listOf(resourceId) }
        val targetId = plan.targetResourceId.takeIf(String::isNotBlank) ?: resourceId
        return cancelPlanForEnvironment(
            targetResourceId = targetId,
            planResourceIds = planIds,
            environmentId = environmentId,
            expectedPlanTargetResourceId = plan.targetResourceId,
            expectedPlanGeneration = plan.generation,
        ).effects
    }

    override suspend fun cancelFailedInstall(resourceId: String): List<ResourceActionEffect> {
        val environmentId = installStore.currentEnvironmentId()
        val target = target(resourceId) ?: return message("资源目录正在更新，请稍后重试")
        return uninstall(target, ResourceRunContinuation.CancelFailedInstall, environmentId)
    }

    override suspend fun recoverFailedInstall(
        resourceId: String,
        parentInstanceId: String?,
    ): List<ResourceActionEffect> {
        val environmentId = installStore.currentEnvironmentId()
        return planLifecycleGate.withEnvironment(environmentId) {
            val target = target(resourceId)
                ?: return@withEnvironment message("资源目录正在更新，请稍后重试")
            recoverFailedInstallLocked(target, parentInstanceId, environmentId)
        }
    }

    private suspend fun recoverFailedInstallLocked(
        target: ResourceTarget,
        parentInstanceId: String?,
        environmentId: String,
    ): List<ResourceActionEffect> {
        val failedOperation = installStore.failedOperation(target.id, environmentId)
        if (installStore.isFailed(target.id, environmentId) &&
            failedOperation != KiteResourceInstallStore.OP_UNINSTALL
        ) {
            val continuation = FailedResourceRecoveryPolicy.continuation(
                target.id,
                installStore.planSnapshot(environmentId),
            )
            return uninstall(
                target = target,
                continuation = continuation,
                environmentId = environmentId,
                parentInstanceId = parentInstanceId,
            )
        }

        val cleanupAttempted = installStore.isFailed(target.id, environmentId) &&
            failedOperation == KiteResourceInstallStore.OP_UNINSTALL
        val cleanupComplete = if (cleanupAttempted) {
            cleanupCancelledResources(listOf(target.id), environmentId)
        } else {
            true
        }
        if (cleanupAttempted) installStore.clear(target.id, environmentId)
        return resumeOrRestartInstall(
            target = target,
            parentInstanceId = parentInstanceId,
            environmentId = environmentId,
            cleanupResult = cleanupComplete.takeIf { cleanupAttempted },
        )
    }

    private suspend fun resumeOrRestartInstall(
        target: ResourceTarget,
        parentInstanceId: String?,
        environmentId: String,
        cleanupResult: Boolean?,
    ): List<ResourceActionEffect> {
        val before = installStore.planSnapshot(environmentId)
        if (target.id in before.resourceIds) installStore.resumePlanFrom(target.id, environmentId)
        val plan = installStore.planSnapshot(environmentId)
        val notice = explicitResult(
            when (cleanupResult) {
                true -> "${target.name} 的失败残留已清理，正在全量重新获取"
                false -> "${target.name} 标准卸载失败，已切换为全量重新获取"
                null -> "${target.name} 的失败状态已重置，正在重新获取"
            }
        )
        if (target.id in plan.resourceIds && plan.isActive) {
            val planTarget = target(plan.targetResourceId) ?: target
            val wizard = installWizardEffect(planTarget, plan.resourceIds, environmentId)
            val visibleEffects = if (parentInstanceId == null) listOf(wizard) else emptyList()
            val runParentInstanceId = parentInstanceId ?: wizard.instanceId
            if (runCoordinator.startRejectionReason() == RUN_NOTIFICATIONS_REQUIRED) {
                return visibleEffects + notice + ResourceActionEffect.RequireNotifications
            }
            return if (runCoordinator.startNextPlannedInstall(runParentInstanceId)) {
                visibleEffects + notice
            } else {
                visibleEffects + notice + explicitResult("获取队列已恢复，但下一项未能启动，请在向导中重试")
            }
        }
        if (plan.targetResourceId.isBlank()) {
            clearInstallTask(target.id, listOf(target.id), environmentId)
        }
        return notice + installLocked(target.id, environmentId)
    }

    override suspend fun cancelPlan(
        targetResourceId: String,
        planResourceIds: List<String>
    ): List<ResourceActionEffect> {
        val environmentId = installStore.currentEnvironmentId()
        val plan = installStore.planSnapshot(environmentId)
        if (plan.targetResourceId.isBlank()) return message("当前获取任务已经结束")
        return cancelPlanForEnvironment(
            targetResourceId = targetResourceId,
            planResourceIds = planResourceIds,
            environmentId = environmentId,
            expectedPlanTargetResourceId = plan.targetResourceId,
            expectedPlanGeneration = plan.generation,
        ).effects
    }

    override suspend fun cancelInstallWizard(
        targetResourceId: String,
        planResourceIds: List<String>,
        environmentId: String,
        instanceId: String,
        expectedGeneration: Long,
    ): Boolean {
        val currentPlan = installStore.planSnapshot(environmentId)
        if (currentPlan.targetResourceId != targetResourceId) return false
        return cancelPlanForEnvironment(
            targetResourceId = targetResourceId,
            planResourceIds = planResourceIds,
            environmentId = environmentId,
            expectedPlanTargetResourceId = currentPlan.targetResourceId,
            expectedPlanGeneration = currentPlan.generation,
            expectedWizardInstanceId = instanceId,
            expectedWizardGeneration = expectedGeneration,
        ).accepted
    }

    private suspend fun cancelPlanForEnvironment(
        targetResourceId: String,
        planResourceIds: List<String>,
        environmentId: String,
        expectedPlanTargetResourceId: String,
        expectedPlanGeneration: Long,
        expectedWizardInstanceId: String? = null,
        expectedWizardGeneration: Long? = null,
    ): PlanCancelOutcome = planLifecycleGate.withEnvironment(environmentId) {
        val currentPlan = installStore.planSnapshot(environmentId)
        if (!ResourcePlanCancellationPolicy.owns(
                current = currentPlan,
                expectedTargetResourceId = expectedPlanTargetResourceId,
                expectedGeneration = expectedPlanGeneration,
                requestedTargetResourceId = targetResourceId,
            )
        ) {
            return@withEnvironment PlanCancelOutcome(
                accepted = false,
                effects = message("获取任务已经变化，本次取消未执行"),
            )
        }
        if (expectedWizardInstanceId != null && expectedWizardGeneration != null) {
            val wizard = CardRunStore.get(expectedWizardInstanceId, environmentId)
                ?.takeIf { state ->
                    state.createdAt == expectedWizardGeneration &&
                        state.ownerKind == CardRunState.OWNER_KIND_INSTALL_WIZARD &&
                        state.stepId == targetResourceId &&
                        state.status !in INSTALL_WIZARD_ENDED_STATUSES
                }
            val currentWizard = wizard?.let {
                CardRunStore.currentForRecipe(it.recipeId, environmentId)
            }
            if (
                wizard == null ||
                currentWizard == null ||
                currentWizard.instanceId != wizard.instanceId ||
                currentWizard.createdAt != wizard.createdAt
            ) {
                return@withEnvironment PlanCancelOutcome(
                    accepted = false,
                    effects = message("获取向导已经变化，本次关闭未执行"),
                )
            }
        }
        if (currentPlan.isPreparing && currentPlan.targetResourceId == targetResourceId) {
            preparationFlights.cancel(
                environmentId = environmentId,
                targetResourceId = currentPlan.targetResourceId,
                instanceId = expectedWizardInstanceId,
                generation = expectedWizardGeneration,
            )
            if (!installStore.isInstalled(currentPlan.targetResourceId, environmentId)) {
                installStore.clear(currentPlan.targetResourceId, environmentId)
            }
            clearInstallTask(
                targetId = currentPlan.targetResourceId,
                resourceIds = listOf(currentPlan.targetResourceId),
                environmentId = environmentId,
            )
            return@withEnvironment PlanCancelOutcome(true, message("获取任务已取消"))
        }
        if (ResourcePlanCancellationPolicy.canCancelBeforeFirstStart(currentPlan, targetResourceId)) {
            clearInstallTask(
                targetId = currentPlan.targetResourceId,
                resourceIds = currentPlan.resourceIds,
                environmentId = environmentId,
            )
            return@withEnvironment PlanCancelOutcome(true, message("获取任务已取消"))
        }
        val resourceIds = currentPlan.resourceIds
            .ifEmpty { planResourceIds.filter(String::isNotBlank) }
            .ifEmpty { listOfNotNull(targetResourceId.takeIf(String::isNotBlank)) }
            .distinct()
        val unfinished = resourceIds.filterNot { installStore.isInstalled(it, environmentId) }
        unfinished.forEach { resourceId ->
            listOf(KiteResourceInstallRecipes.OP_INSTALL, KiteResourceInstallRecipes.OP_UNINSTALL)
                .mapNotNull { operation ->
                    CardRunStore.currentForRecipe(
                        KiteResourceInstallRecipes.recipeId(resourceId, operation),
                        environmentId
                    )
                }
                .filter { state -> state.isBusy() || state.isActive() || state.hasRunBinding() }
                .forEach { state -> runOrchestrator.stop(state.instanceId) }
        }
        val cleanupComplete = if (unfinished.isNotEmpty()) {
            delay(CANCEL_SETTLE_MS)
            cleanupCancelledResources(unfinished, environmentId)
        } else true
        clearInstallTask(targetResourceId, resourceIds, environmentId)
        val effects = if (unfinished.isEmpty()) {
            message("获取任务已取消")
        } else if (cleanupComplete) {
            message("获取任务已取消，临时内容已清理")
        } else {
            message("获取任务已取消，部分临时内容稍后继续清理")
        }
        PlanCancelOutcome(true, effects)
    }

    override suspend fun createHomeCard(resourceId: String): List<ResourceActionEffect> =
        withContext(Dispatchers.IO) {
            val target = target(resourceId) ?: return@withContext explicitResult("资源目录正在更新，请稍后重试")
            val template = homeCardTemplate(target) ?: return@withContext explicitResult("${target.name} 暂无首页卡片模板")
            runCatching {
                val managedRecipeId = KiteResourceInstallRecipes.recipeId(target.id, "open")
                val base = template.optJSONObject("base") ?: JSONObject().also { template.put("base", it) }
                base.put("id", managedRecipeId)
                recipeLoader.addManagedSharedRecipeTemplate(
                    template = template,
                    fileStem = "${KiteResourceInstallRecipes.safeId(target.id)}-home",
                    ownerKind = MANAGED_OWNER_RESOURCE_HOME,
                    ownerId = target.id
                )
                recipeFeatureGateway.invalidateCatalog("resource_home_card_added")
            }.fold(
                onSuccess = { explicitResult("已添加 ${target.name} 到首页") },
                onFailure = { explicitResult("添加失败：${it.message ?: it.javaClass.simpleName}") }
            )
        }

    override suspend fun installDirect(resourceId: String): List<ResourceActionEffect> {
        val targetId = KiteResourceInstallRecipes.safeId(resourceId)
        val recipe = runCoordinator.recipe(targetId, KiteResourceInstallRecipes.OP_INSTALL)
            ?: return message("资源获取入口不存在")
        diagnostics.logRecipeEvent(
            "kite_runtime_automation_resource_install_start",
            recipe,
            mapOf("resourceId" to targetId)
        )
        return when (val result = runCoordinator.start(
            ResourceRunLaunchRequest(
                resourceId = targetId,
                recipe = recipe,
                operation = KiteResourceInstallRecipes.OP_INSTALL,
                stageBundledResource = runCoordinator.isBundled(targetId)
            )
        )) {
            is ResourceRunLaunchResult.Accepted -> emptyList()
            is ResourceRunLaunchResult.Rejected -> result.asResourceStartEffect("资源获取未启动")
        }
    }

    private suspend fun openCurrentInstallPlan(
        requestedTarget: ResourceTarget,
        plan: KiteResourcePlanSnapshot,
        environmentId: String,
    ): List<ResourceActionEffect> {
        val targetId = plan.targetResourceId.takeIf(String::isNotBlank)
            ?: return message("${requestedTarget.name} 当前没有可恢复的获取任务")
        val belongsToPlan = requestedTarget.id == targetId || requestedTarget.id in plan.resourceIds
        val planTarget = target(targetId)
        if (planTarget == null) {
            clearInstallTask(targetId, plan.resourceIds, environmentId)
            return explicitResult("旧获取任务缺少资源定义，已自动清理") +
                installLocked(requestedTarget.id, environmentId)
        }
        val effect = installWizardEffect(planTarget, plan.resourceIds, environmentId)
        if (plan.isPreparing) {
            launchInstallPreparation(planTarget, effect, environmentId)
        }
        val conflictNotice: List<ResourceActionEffect> = if (belongsToPlan) {
            emptyList()
        } else {
            explicitResult("${planTarget.name} 仍有未完成的获取任务，请先完成或取消")
        }
        return listOf(effect) + conflictNotice
    }

    private fun reconcileInstalledPlanSteps(
        snapshot: KiteResourcePlanSnapshot,
        environmentId: String,
    ): KiteResourcePlanSnapshot {
        var current = snapshot
        while (current.isActive) {
            val nextUnsettled = current.resourceIds.firstOrNull { resourceId ->
                current.stepStatus(resourceId) != KiteResourceInstallStore.PLAN_STEP_DONE
            }
            if (nextUnsettled == null) {
                installStore.clearPlan(environmentId)
                return installStore.planSnapshot(environmentId)
            }
            val status = current.stepStatus(nextUnsettled)
            if (status != KiteResourceInstallStore.PLAN_STEP_RUNNING &&
                status != KiteResourceInstallStore.PLAN_STEP_PENDING
            ) break
            if (!installStore.isInstalled(nextUnsettled, environmentId)) break
            val previous = current
            installStore.advancePlanAfter(nextUnsettled, environmentId)
            current = installStore.planSnapshot(environmentId)
            if (current == previous) break
        }
        return current
    }

    private fun launchInstallPreparation(
        target: ResourceTarget,
        effect: ResourceActionEffect.OpenInstallWizard,
        environmentId: String,
    ) {
        val wizard = CardRunStore.get(effect.instanceId, environmentId)
            ?.takeIf { state ->
                state.createdAt == effect.generation &&
                    state.ownerKind == CardRunState.OWNER_KIND_INSTALL_WIZARD &&
                    state.stepId == target.id &&
                    state.status !in INSTALL_WIZARD_ENDED_STATUSES
            }
            ?: return
        val token = ResourceInstallPreparationToken(
            environmentId = environmentId,
            targetResourceId = target.id,
            instanceId = wizard.instanceId,
            generation = wizard.createdAt,
        )
        preparationFlights.launch(token) { current ->
            prepareInstallPlan(target, current)
        }
    }

    private suspend fun prepareInstallPlan(
        target: ResourceTarget,
        token: ResourceInstallPreparationToken,
    ) {
        try {
            if (!ToolchainPackInstaller.awaitBootstrapResourcesSettledIfRunning()) {
                commitPreparationFailure(token, target, "基础组件仍在准备，请稍后重试")
                return
            }
            val plan = withContext(Dispatchers.IO) {
                buildInstallPlan(target, token.environmentId)
            }
            preparationFlights.commitIfCurrent(token) {
                if (!ownsPreparingPlan(token)) return@commitIfCurrent
                acceptPreparedInstallPlan(target, token, plan)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            commitPreparationFailure(
                token = token,
                target = target,
                reason = error.message ?: error.javaClass.simpleName,
            )
        }
    }

    private fun acceptPreparedInstallPlan(
        target: ResourceTarget,
        token: ResourceInstallPreparationToken,
        plan: PreparedInstallPlan,
    ) {
        val recipe = CardRunSpecialRecipes.installWizard(target.id, target.name)
        when {
            plan.missing.isNotEmpty() -> {
                val reason = "缺少可获取的基础层：${plan.missing.distinct().joinToString("、")}"
                installStore.clearPlan(token.environmentId)
                installStore.markFailed(
                    target.id,
                    KiteResourceInstallStore.OP_INSTALL,
                    null,
                    reason,
                    token.environmentId,
                )
                CardRunStore.update(
                    recipe = recipe,
                    status = CardRunStatus.Failed,
                    instanceId = token.instanceId,
                    ownerKind = CardRunState.OWNER_KIND_INSTALL_WIZARD,
                    stepId = target.id,
                    surface = com.kite.app.run.CardRunSurface.InstallWizard,
                    lastMeaningfulOutput = reason,
                    lastError = reason,
                    environmentId = token.environmentId,
                )
            }

            plan.resourceIds.isEmpty() -> {
                installStore.clear(target.id, token.environmentId)
                installStore.clearPlan(token.environmentId)
                CardRunStore.update(
                    recipe = recipe,
                    status = CardRunStatus.Completed,
                    instanceId = token.instanceId,
                    ownerKind = CardRunState.OWNER_KIND_INSTALL_WIZARD,
                    stepId = target.id,
                    surface = com.kite.app.run.CardRunSurface.InstallWizard,
                    lastMeaningfulOutput = "${target.name} 已经就绪",
                    environmentId = token.environmentId,
                )
            }

            else -> {
                resetPlanTransientState(plan.resourceIds, token.environmentId)
                if (installStore.activatePreparedPlan(target.id, plan.resourceIds, token.environmentId)) {
                    CardRunStore.update(
                        recipe = recipe,
                        status = CardRunStatus.Opened,
                        instanceId = token.instanceId,
                        ownerKind = CardRunState.OWNER_KIND_INSTALL_WIZARD,
                        stepId = target.id,
                        surface = com.kite.app.run.CardRunSurface.InstallWizard,
                        lastMeaningfulOutput = "获取内容已准备",
                        environmentId = token.environmentId,
                    )
                }
            }
        }
    }

    private fun commitPreparationFailure(
        token: ResourceInstallPreparationToken,
        target: ResourceTarget,
        reason: String,
    ) {
        preparationFlights.commitIfCurrent(token) {
            if (!ownsPreparingPlan(token)) return@commitIfCurrent
            installStore.clearPlan(token.environmentId)
            installStore.markFailed(
                target.id,
                KiteResourceInstallStore.OP_INSTALL,
                null,
                reason,
                token.environmentId,
            )
            val recipe = CardRunSpecialRecipes.installWizard(target.id, target.name)
            CardRunStore.update(
                recipe = recipe,
                status = CardRunStatus.Failed,
                instanceId = token.instanceId,
                ownerKind = CardRunState.OWNER_KIND_INSTALL_WIZARD,
                stepId = target.id,
                surface = com.kite.app.run.CardRunSurface.InstallWizard,
                lastMeaningfulOutput = reason,
                lastError = reason,
                environmentId = token.environmentId,
            )
        }
    }

    private fun ownsPreparingPlan(token: ResourceInstallPreparationToken): Boolean {
        val plan = installStore.planSnapshot(token.environmentId)
        val wizard = CardRunStore.get(token.instanceId, token.environmentId)
        return plan.isPreparing &&
            plan.targetResourceId == token.targetResourceId &&
            wizard?.createdAt == token.generation &&
            wizard.ownerKind == CardRunState.OWNER_KIND_INSTALL_WIZARD &&
            wizard.stepId == token.targetResourceId &&
            wizard.status !in INSTALL_WIZARD_ENDED_STATUSES
    }

    private fun installWizardEffect(
        target: ResourceTarget,
        planResourceIds: List<String>,
        environmentId: String = installStore.currentEnvironmentId(),
        reuseCurrent: Boolean = true,
    ): ResourceActionEffect.OpenInstallWizard {
        val recipe = CardRunSpecialRecipes.installWizard(target.id, target.name)
        CardRunStore.registerRecipe(recipe)
        val current = if (reuseCurrent) {
            CardRunStore.currentForRecipe(recipe.id, environmentId)
                ?.takeIf { state ->
                    state.ownerKind == CardRunState.OWNER_KIND_INSTALL_WIZARD &&
                        state.stepId == target.id &&
                        state.status !in INSTALL_WIZARD_ENDED_STATUSES
                }
        } else null
        val root = current ?: run {
            val instanceId =
                "resource-install-wizard-${KiteResourceInstallRecipes.safeId(target.id)}-${UUID.randomUUID().toString().replace("-", "")}"
            CardRunStore.update(
                recipe = recipe,
                status = CardRunStatus.Opened,
                instanceId = instanceId,
                ownerKind = CardRunState.OWNER_KIND_INSTALL_WIZARD,
                stepId = target.id,
                surface = com.kite.app.run.CardRunSurface.InstallWizard,
                currentStepIndex = 0,
                lastMeaningfulOutput = "正在准备获取内容",
                environmentId = environmentId
            )
        }
        return installWizardOpenEffect(root, target.id, planResourceIds)
    }

    private suspend fun uninstall(
        target: ResourceTarget,
        continuation: ResourceRunContinuation,
        environmentId: String = installStore.currentEnvironmentId(),
        restartInstall: (suspend () -> List<ResourceActionEffect>)? = null,
        parentInstanceId: String? = null,
    ): List<ResourceActionEffect> {
        val recipe = runCoordinator.recipe(target.id, KiteResourceInstallRecipes.OP_UNINSTALL)
        if (recipe == null) {
            installStore.clear(target.id, environmentId)
            return when (continuation) {
                ResourceRunContinuation.Reinstall -> restartInstall?.invoke() ?: install(target.id)
                ResourceRunContinuation.CancelFailedInstall -> {
                    installStore.clearPlan(environmentId)
                    message("已取消 ${target.name} 的失败获取")
                }
                ResourceRunContinuation.ResumeInstallWizard -> resumeOrRestartInstall(
                    target = target,
                    parentInstanceId = parentInstanceId,
                    environmentId = environmentId,
                    cleanupResult = null,
                )
                else -> message("已移除 ${target.name} 的获取记录")
            }
        }
        return when (val result = runCoordinator.start(
            ResourceRunLaunchRequest(
                resourceId = target.id,
                recipe = recipe,
                operation = KiteResourceInstallRecipes.OP_UNINSTALL,
                continuation = continuation,
                parentInstanceId = parentInstanceId,
                environmentId = environmentId
            )
        )) {
            is ResourceRunLaunchResult.Accepted -> message("正在卸载 ${target.name}")
            is ResourceRunLaunchResult.Rejected -> result.asResourceStartEffect("资源运行未启动")
        }
    }

    private fun startManagedOperation(
        target: ResourceTarget,
        recipe: KiteRecipe,
        operation: String,
        targetVersion: String? = null,
        environmentId: String = installStore.currentEnvironmentId()
    ): List<ResourceActionEffect> {
        val result = runCoordinator.start(
            ResourceRunLaunchRequest(
                resourceId = target.id,
                recipe = recipe,
                operation = operation,
                targetVersion = targetVersion,
                stageBundledResource = runCoordinator.isBundled(target.id),
                environmentId = environmentId,
            )
        )
        if (result is ResourceRunLaunchResult.Rejected) {
            installStore.republish(
                resourceId = target.id,
                reason = "maintenanceOperationRejected",
                environmentId = environmentId,
            )
        }
        return managedOperationStartEffects(
            result = result,
            resourceName = target.name,
            operationLabel = operationLabel(operation),
        )
    }

    private fun operationLabel(operation: String): String = when (operation) {
        KiteResourceInstallRecipes.OP_UPDATE -> "更新"
        KiteResourceInstallRecipes.OP_REINSTALL -> "重新安装"
        KiteResourceInstallRecipes.OP_REPAIR -> "修复"
        KiteResourceInstallRecipes.OP_UNINSTALL -> "卸载"
        else -> "获取"
    }

    private suspend fun buildInstallPlan(target: ResourceTarget, environmentId: String): PreparedInstallPlan {
        manifestLoader.invalidate()
        val allManifests = manifestLoader.manifests().values
        val manifests = allManifests.filter { it.sections.isNotEmpty() }
        val systemConvergence = systemManagedResourceFactsReconciler
            .reconcile(allManifests, environmentId)
            .getOrElse { error ->
                Logger.i(
                    "SystemManagedResourceFacts",
                    "convergence skipped environment=$environmentId reason=${error.message ?: error.javaClass.simpleName}",
                )
                SystemManagedResourceFactsConvergence()
            }
        val androidPackageConvergence = androidPackageResourceFactsReconciler.reconcile(
            manifests = allManifests,
            environmentId = environmentId,
        )
        reconcileInstalledResources(
            manifests = manifests,
            environmentId = environmentId,
            alreadyVerifiedResourceIds = systemConvergence.readyResourceIds +
                androidPackageConvergence.readyResourceIds,
        )
        val byId = manifests.associateBy(KiteResourceManifest::id)
        val installedIds = manifests.filter { isInstalled(it, environmentId) }
            .mapTo(linkedSetOf(), KiteResourceManifest::id)
        val capabilities = installedIds.flatMap { byId[it]?.provides.orEmpty() }.toSet()
        val serverPlan = manifestLoader.requestInstallPlan(target.id, installedIds, capabilities)
        if (serverPlan != null) {
            installStore.putPageCache(
                KiteResourceRequestPolicy.installPlanKey(target.id),
                serverPlan.rawJson.toString(),
                KiteResourceRequestPolicy.INSTALL_PLAN_CACHE_MS
            )
            val unknown = serverPlan.resourceIds.filterNot(byId::containsKey)
            val ids = pendingInstallPlanResourceIds(
                resourceIds = serverPlan.resourceIds.filter(byId::containsKey),
                targetResourceId = target.id,
                installedResourceIds = installedIds,
            )
            return PreparedInstallPlan(ids, serverPlan.missing.map { it.requirement } + unknown)
        }
        val ordered = linkedSetOf<String>()
        val missing = mutableListOf<String>()
        val visiting = mutableSetOf<String>()
        fun visit(id: String) {
            if (!visiting.add(id)) return
            manifestLoader.requestRelationTargets(id).let { relations ->
                (relations.base + relations.defaults).forEach { requirement ->
                    val providers = requirement.providerIds.mapNotNull(byId::get)
                    if (providers.any { isInstalled(it, environmentId) }) return@forEach
                    val provider = providers.firstOrNull()
                    if (provider == null) missing += requirement.requirement else visit(provider.id)
                }
            }
            visiting.remove(id)
            val manifest = byId[id]
            if (manifest != null && (!isInstalled(manifest, environmentId) || id == target.id)) ordered += id
        }
        visit(target.id)
        return PreparedInstallPlan(
            resourceIds = pendingInstallPlanResourceIds(
                resourceIds = ordered,
                targetResourceId = target.id,
                installedResourceIds = installedIds,
            ),
            missing = missing,
        )
    }

    private fun maintenanceUnavailable(
        resourceId: String,
        explanation: String,
        environmentId: String,
    ): List<ResourceActionEffect> {
        installStore.republish(
            resourceId = resourceId,
            reason = "maintenanceOperationUnavailable",
            environmentId = environmentId,
        )
        return explicitResult(explanation)
    }

    private suspend fun reconcileInstalledResources(
        manifests: Collection<KiteResourceManifest>,
        environmentId: String,
        alreadyVerifiedResourceIds: Set<String> = emptySet(),
    ): Set<String> {
        val requirements = ResourceManagedCommandProbeProtocol.normalize(
            manifests
                .asSequence()
                .filter { manifest -> installStore.isInstalled(manifest.id, environmentId) }
                .filterNot { manifest -> manifest.id in alreadyVerifiedResourceIds }
                .map { manifest ->
                    ResourceManagedCommandRequirement(
                        resourceId = manifest.id,
                        commands = manifest.management.managedCommands
                    )
                }
                .filter { requirement -> requirement.commands.isNotEmpty() }
                .toList()
        )
        if (requirements.isEmpty()) return emptySet()

        val verificationBasis = WorkSurfaceRuntimeBridge.managedCommandVerificationBasis(
            context = appContext,
            commands = requirements.flatMap(ResourceManagedCommandRequirement::commands),
        )
        val requests = requirements.map { requirement ->
            val entry = installStore.registryEntry(requirement.resourceId, environmentId)
            val identity = buildResourceManagedCommandEvidenceIdentity(
                environmentId = environmentId,
                requirement = requirement,
                installedVersion = entry?.version.orEmpty(),
                installedAtMs = entry?.installedAt ?: 0L,
                installRunId = entry?.runId.orEmpty(),
                isInstalled = entry?.installed == true,
                verificationBasis = verificationBasis,
            )
            ResourceManagedCommandEvidenceRequest(
                requirement = requirement,
                identity = identity,
                nativeProof = buildResourceManagedCommandNativeProof(
                    identity = identity,
                    nativeEnvironmentEligible = environmentId == KiteResourceRegistry.DEFAULT_ENVIRONMENT_ID,
                ),
            )
        }
        val missing = managedCommandEvidence.missingResourceIds(requests) { pendingRequirements ->
            installedStateProbe.missingResourceIds(pendingRequirements)
        }.getOrElse { return emptySet() }
        if (missing.isNotEmpty()) {
            installStore.markRepairRequired(
                resourceIds = missing,
                explanation = "托管命令缺失，需要修复安装",
                environmentId = environmentId,
            )
        }
        return missing
    }

    private suspend fun reconcileOpenInstallation(
        target: ResourceTarget,
        environmentId: String,
    ): OpenInstallPreflight {
        manifestLoader.invalidate()
        val manifests = manifestLoader.manifests().values
        val installedResourceIds = manifests.asSequence()
            .filter { manifest -> installStore.isInstalled(manifest.id, environmentId) }
            .mapTo(linkedSetOf(), KiteResourceManifest::id)
        val closure = ResourceOpenDependencyResolver.resolve(
            targetResourceId = target.id,
            manifests = manifests,
            installedResourceIds = installedResourceIds,
            relationTargetsFor = manifestLoader::requestRelationTargets,
        )
        val contractResolutions = closure.manifests.asSequence()
            .filter { manifest -> manifest.id in installedResourceIds }
            .filter { manifest -> manifest.management.userLifecycleEnabled }
            .map { manifest ->
                manifest to KiteResourceInstallContract.resolve(
                    currentManifest = manifest.rawJson,
                    installedManifestJson = installStore.installedSnapshotManifestJson(manifest.id, environmentId),
                )
            }
            .toList()
        val updateContracts = contractResolutions.mapNotNullTo(linkedSetOf()) { (manifest, resolution) ->
            (resolution as? KiteResourceInstallContractResolution.UpdateAvailable)?.let { update ->
                installStore.markDefinitionUpdateAvailable(
                    resourceId = manifest.id,
                    installedVersion = update.installedVersion,
                    latestVersion = update.currentVersion,
                    environmentId = environmentId,
                )
                manifest.id
            }
        }
        val repairContracts = contractResolutions.mapNotNullTo(linkedSetOf()) { (manifest, resolution) ->
            manifest.id.takeIf { resolution == KiteResourceInstallContractResolution.RepairRequired }
        }
        if (repairContracts.isNotEmpty()) {
            installStore.markRepairRequired(
                resourceIds = repairContracts,
                explanation = "资源定义已变化，需要修复安装",
                environmentId = environmentId,
            )
        }
        val missingCommands = reconcileInstalledResources(
            manifests = closure.manifests,
            environmentId = environmentId,
        )
        val repairResourceIds = repairContracts + missingCommands
        return OpenInstallPreflight(
            repairResourceIds = repairResourceIds,
            updateResourceIds = updateContracts - repairResourceIds,
            missingResourceIds = closure.missingInstalledResourceIds,
            unresolvedRequirements = closure.unresolvedRequirements,
        )
    }

    private fun isInstalled(
        manifest: KiteResourceManifest,
        environmentId: String = installStore.currentEnvironmentId()
    ): Boolean = installStore.isInstalled(manifest.id, environmentId)

    private fun uninstallBlockers(resourceId: String) =
        manifestLoader.manifests().values.let { manifests ->
            ResourceDependencyGuard(manifestLoader::providerIdsFor).blockers(
                targetResourceId = resourceId,
                manifests = manifests,
                installedResourceIds = manifests.filter(::isInstalled).mapTo(linkedSetOf(), KiteResourceManifest::id)
            )
        }

    private fun resetPlanTransientState(resourceIds: List<String>, environmentId: String) {
        val recipeIds = resourceIds.distinct().map { resourceId ->
            if (installStore.status(resourceId, environmentId) != null &&
                installStore.status(resourceId, environmentId) != KiteResourceInstallStore.STATUS_PREPARING &&
                !installStore.isInstalled(resourceId, environmentId)
            ) installStore.clear(resourceId, environmentId)
            KiteResourceInstallRecipes.recipeId(resourceId, KiteResourceInstallRecipes.OP_INSTALL)
        }
        CardRunStore.removeRunStatesForRecipes(
            recipeIds,
            removeOpenHistory = true,
            environmentId = environmentId
        )
    }

    private fun clearInstallTask(targetId: String, resourceIds: List<String>, environmentId: String) {
        resourceIds.forEach { resourceId ->
            if (installStore.status(resourceId, environmentId) != null &&
                !installStore.isInstalled(resourceId, environmentId) &&
                !installStore.isFailed(resourceId, environmentId)
            ) installStore.clear(resourceId, environmentId)
        }
        val recipeIds = resourceIds.flatMap { resourceId ->
            listOf(
                KiteResourceInstallRecipes.recipeId(resourceId, KiteResourceInstallRecipes.OP_INSTALL),
                KiteResourceInstallRecipes.recipeId(resourceId, KiteResourceInstallRecipes.OP_UNINSTALL)
            )
        } + CardRunSpecialRecipes.installWizard(targetId, targetId).id
        CardRunStore.removeRunStatesForRecipes(
            recipeIds.distinct(),
            removeOpenHistory = true,
            environmentId = environmentId
        )
        // 计划最后清除；在这之前同一环境的新获取仍会被既有计划挡住，
        // 避免旧取消流程按相同 recipeId 误删刚创建的新代次。
        installStore.clearPlan(environmentId)
    }

    private suspend fun cleanupCancelledResources(
        resourceIds: List<String>,
        environmentId: String
    ): Boolean {
        val recipe = cancelCleanupRecipe(resourceIds)
        val runtimeOwner = RuntimeOwnerIdentity.operation(
            namespace = RuntimeOwnerNamespace.Resource,
            instanceId = recipe.id,
            generation = System.currentTimeMillis(),
            operationId = "cancel-cleanup"
        )
        val commandEnvironment = runCatching { environmentFor(environmentId) }.getOrElse { return false }
        return withTimeoutOrNull(CLEANUP_TIMEOUT_MS) {
            suspendCancellableCoroutine { continuation ->
                bridgeClient.runRecipe(
                    recipe,
                    extraEnv = commandEnvironment + runtimeOwner.environment()
                ) { result ->
                    if (continuation.isActive) continuation.resume(result.ok || result.accepted)
                }
            }
        } ?: false
    }

    private fun cancelCleanupRecipe(resourceIds: List<String>): KiteRecipe {
        val ids = resourceIds.map(KiteResourceInstallRecipes::safeId).distinct()
        val step = KiteRecipeStep(
            id = "cancel_cleanup",
            type = KiteRecipe.STEP_SHELL,
            cmd = KiteResourceInstallRecipes.cancelCleanupCommand(ids),
            surfaceMode = KiteRecipe.SURFACE_MODE_SILENT,
            workdir = "/workspace",
            timeoutMs = 120_000L
        )
        return KiteRecipe(
            id = "resource-cancel-cleanup-${UUID.randomUUID()}",
            name = "资源取消清理",
            description = ids.joinToString("、"),
            type = KiteRecipe.TYPE_START_SERVICE,
            category = "resource",
            defaultUrl = "",
            shortcut = false,
            icon = KiteRecipeIcon(name = KiteRecipeIcon.ICON_TOOLS),
            launch = KiteLaunchConfig(openInstance = false),
            execution = KiteExecution.steps(listOf(step)),
            runtimeSource = CardRunSpecialRecipes.RESOURCE_INSTALL_WIZARD_RUNTIME_SOURCE
        )
    }

    private fun target(resourceId: String): ResourceTarget? =
        manifestLoader.requestManifest(resourceId.trim())?.let(::ResourceTarget)

    private fun openRecipe(target: ResourceTarget): KiteRecipe? =
        manifestLoader.requestOpenRecipeTemplate(target.id)?.let { template ->
            val json = hydrateTemplate(target, template)
            val base = json.optJSONObject("base") ?: JSONObject().also { json.put("base", it) }
            base.put("id", KiteResourceInstallRecipes.recipeId(target.id, "open"))
            KiteRecipe.fromJson(json, runtimeSource = KiteRecipe.SOURCE_USER)
                .copy(runtimeSource = RESOURCE_OPEN_RUNTIME_SOURCE)
        }

    private fun homeCardTemplate(target: ResourceTarget): JSONObject? =
        (manifestLoader.requestFirstHomeCardRecipeTemplate(target.id)
            ?: manifestLoader.requestOpenRecipeTemplate(target.id))
            ?.let { hydrateTemplate(target, it) }

    private fun hydrateTemplate(target: ResourceTarget, template: JSONObject): JSONObject {
        val json = JSONObject(template.toString())
        val base = json.optJSONObject("base") ?: JSONObject().also { json.put("base", it) }
        if (base.optString("name").isBlank()) base.put("name", target.name)
        if (base.optString("description").isBlank()) base.put("description", target.manifest.description)
        if (target.manifest.iconAsset.isNotBlank()) {
            base.put("icon", JSONObject()
                .put("type", KiteRecipeIcon.TYPE_IMAGE)
                .put("name", target.manifest.iconText.ifBlank { "resource" })
                .put("source", target.manifest.iconAsset))
        }
        val card = json.optJSONObject("card") ?: JSONObject().also { json.put("card", it) }
        if (card.optString("accent").isBlank() || card.optString("accent").equals("primary", true)) {
            card.put("accent", target.manifest.displayAccent.ifBlank { "primary" })
        }
        return json
    }

    private fun shouldOpenRunTask(recipe: KiteRecipe): Boolean {
        val step = recipe.steps.firstOrNull() ?: return recipe.launch.openInstance
        return when (KiteRecipe.normalizeSurfaceMode(step.surfaceMode)) {
            KiteRecipe.SURFACE_MODE_PANEL -> true
            KiteRecipe.SURFACE_MODE_SILENT -> false
            else -> recipe.launch.openInstance && step.type in setOf(
                KiteRecipe.STEP_OPEN_WEB,
                KiteRecipe.STEP_TERMINAL,
                KiteRecipe.STEP_X11,
                KiteRecipe.STEP_AGENT,
                KiteRecipe.STEP_SHELL
            )
        }
    }

    private fun CardRunState.isReusableOpenRun(): Boolean = status in setOf(
        CardRunStatus.Starting,
        CardRunStatus.WaitingTerminal,
        CardRunStatus.Running,
        CardRunStatus.AlreadyRunning,
        CardRunStatus.Opened
    )

    private fun message(text: String): List<ResourceActionEffect> =
        listOf(ResourceActionEffect.Message(text))

    private fun explicitResult(text: String): List<ResourceActionEffect> =
        listOf(
            ResourceActionEffect.Message(
                text = text,
                presentation = com.kite.app.application.resources.ResourceActionMessagePresentation.ExplicitResult,
            )
        )

    private fun RunCommandResult.Ignored.asResourceStartEffect(prefix: String): List<ResourceActionEffect> =
        if (reason == RUN_NOTIFICATIONS_REQUIRED) {
            listOf(ResourceActionEffect.RequireNotifications)
        } else {
            message("$prefix：$reason")
        }

    private fun ResourceRunLaunchResult.Rejected.asResourceStartEffect(prefix: String): List<ResourceActionEffect> =
        if (reason == RUN_NOTIFICATIONS_REQUIRED) {
            listOf(ResourceActionEffect.RequireNotifications)
        } else {
            message("$prefix：$reason")
        }

    private data class ResourceTarget(val manifest: KiteResourceManifest) {
        val id: String get() = manifest.id
        val name: String get() = manifest.name.ifBlank { manifest.id }
    }

    private data class PreparedInstallPlan(
        val resourceIds: List<String>,
        val missing: List<String>
    )

    private data class AppliedUpdateCheckResult(
        val outcome: UpdateCheckOutcome,
        val message: String
    )

    private enum class UpdateCheckOutcome {
        Available,
        Current,
        Unsupported,
        Failed
    }

    companion object {
        private const val MANAGED_OWNER_RESOURCE_HOME = "resource_home"
        private const val RESOURCE_OPEN_RUNTIME_SOURCE = "resource_open"
        private const val CANCEL_SETTLE_MS = 800L
        private const val CLEANUP_TIMEOUT_MS = 130_000L
        private val INSTALL_WIZARD_ENDED_STATUSES = setOf(
            CardRunStatus.Unknown,
            CardRunStatus.Stopped,
            CardRunStatus.Completed,
            CardRunStatus.Failed,
            CardRunStatus.Stopping,
            CardRunStatus.BridgeUnavailable
        )
    }
}

/**
 * 把资源 Open 的执行接受结果交接为同一 CardRun root 的可见 Effect。
 * 可见页面只在编排器已接受且状态拥有者可返回确定代次后打开。
 */
internal class ResourceOpenRunStarter(
    private val startRun: (RunStartRequest) -> RunCommandResult,
    private val stateFor: (instanceId: String, environmentId: String) -> CardRunState?,
) {
    fun start(
        request: RunStartRequest,
        resourceName: String,
        opensRunSurface: Boolean,
    ): List<ResourceActionEffect> = when (val result = startRun(request)) {
        is RunCommandResult.Accepted -> {
            if (!opensRunSurface) {
                listOf(ResourceActionEffect.Message("正在打开 $resourceName"))
            } else {
                val root = stateFor(result.instanceId, request.environmentId)
                    ?.takeIf { state ->
                        state.instanceId == result.instanceId &&
                            state.environmentId == request.environmentId &&
                            state.recipeId == request.recipe.id
                    }
                if (root == null) {
                    listOf(ResourceActionEffect.Message("资源运行页面暂不可打开：运行状态未创建"))
                } else {
                    listOf(
                        resourceOpenEffect(root),
                        ResourceActionEffect.Message("正在打开 $resourceName"),
                    )
                }
            }
        }
        is RunCommandResult.Ignored -> if (result.reason == RUN_NOTIFICATIONS_REQUIRED) {
            listOf(ResourceActionEffect.RequireNotifications)
        } else {
            listOf(ResourceActionEffect.Message("资源运行未启动：${result.reason}"))
        }
    }
}

internal fun resourceOpenEffect(root: CardRunState): ResourceActionEffect.OpenRun =
    ResourceActionEffect.OpenRun(
        recipeId = root.recipeId,
        instanceId = root.instanceId,
        generation = root.createdAt,
        autoStart = false,
    )

internal fun managedOperationStartEffects(
    result: ResourceRunLaunchResult,
    resourceName: String,
    operationLabel: String,
): List<ResourceActionEffect> = when (result) {
    is ResourceRunLaunchResult.Accepted -> listOf(
        resourceOpenEffect(result.state),
        ResourceActionEffect.Message("正在$operationLabel $resourceName"),
    )
    is ResourceRunLaunchResult.Rejected -> if (result.reason == RUN_NOTIFICATIONS_REQUIRED) {
        listOf(ResourceActionEffect.RequireNotifications)
    } else {
        listOf(
            ResourceActionEffect.Message(
                text = "资源${operationLabel}未启动：${result.reason}",
                presentation = ResourceActionMessagePresentation.ExplicitResult,
            )
        )
    }
}

internal fun installWizardOpenEffect(
    root: CardRunState,
    targetResourceId: String,
    planResourceIds: List<String>,
): ResourceActionEffect.OpenInstallWizard = ResourceActionEffect.OpenInstallWizard(
    recipeId = root.recipeId,
    instanceId = root.instanceId,
    generation = root.createdAt,
    targetResourceId = targetResourceId,
    planResourceIds = planResourceIds,
)
