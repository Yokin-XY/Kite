package com.kite.app.platform.resources

import android.content.Context
import com.kite.app.application.recipes.RecipeFeatureGateway
import com.kite.app.application.resources.ResourceActionEffect
import com.kite.app.application.resources.ResourceActionGateway
import com.kite.app.application.resources.ResourceDependencyGuard
import com.kite.app.application.resources.ResourceVersionCheckResult
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
import com.kite.app.foundation.workspace.WorkSurfaceRuntimeBridge
import com.kite.app.recipe.KiteExecution
import com.kite.app.recipe.KiteLaunchConfig
import com.kite.app.recipe.KiteRecipe
import com.kite.app.recipe.KiteRecipeIcon
import com.kite.app.recipe.KiteRecipeLoader
import com.kite.app.recipe.KiteRecipeStep
import com.kite.app.resources.KiteResourceInstallRecipes
import com.kite.app.resources.KiteResourceInstallStore
import com.kite.app.resources.KiteResourceRegistry
import com.kite.app.resources.KiteResourceManagementMode
import com.kite.app.resources.KiteResourceManifest
import com.kite.app.resources.KiteResourceManifestLoader
import com.kite.app.resources.KiteResourceRequestPolicy
import com.kite.app.resources.KiteResourceSourcePlanFactory
import com.kite.app.run.CardRunState
import com.kite.app.run.CardRunStatus
import com.kite.app.run.CardRunStore
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
    private val environmentFor: (String) -> Map<String, String> = { emptyMap() },
    private val installedStateProbe: ResourceInstalledStateProbe =
        AndroidResourceInstalledStateProbe(bridgeClient),
    private val managedCommandEvidence: ResourceManagedCommandEvidenceCoordinator =
        ResourceManagedCommandEvidenceCoordinator(),
) : ResourceActionGateway {
    private val appContext = context.applicationContext

    override suspend fun install(resourceId: String): List<ResourceActionEffect> {
        val environmentId = installStore.currentEnvironmentId()
        val target = target(resourceId) ?: return message("资源目录正在更新，请稍后重试")
        if (!target.manifest.management.userLifecycleEnabled) {
            return message("${target.name} 是 Kite 管理的系统组件，无需单独获取")
        }
        if (installStore.isFailed(target.id, environmentId) &&
            installStore.failedOperation(target.id, environmentId) != KiteResourceInstallStore.OP_UNINSTALL
        ) {
            return uninstall(target, ResourceRunContinuation.Reinstall, environmentId)
        }
        installStore.markPreparing(target.id, environmentId)
        return runCatching { withContext(Dispatchers.IO) { buildInstallPlan(target, environmentId) } }
            .fold(
                onSuccess = { plan -> acceptInstallPlan(target, plan, environmentId) },
                onFailure = { error ->
                    val reason = error.message ?: error.javaClass.simpleName
                    installStore.markFailed(target.id, KiteResourceInstallStore.OP_INSTALL, null, reason, environmentId)
                    message("执行队列准备失败：$reason")
                }
            )
    }

    override suspend fun reopenInstall(resourceId: String): List<ResourceActionEffect> {
        val target = target(resourceId) ?: return message("资源目录正在更新，请稍后重试")
        val plan = installStore.planSnapshot()
        val ids = plan.resourceIds.ifEmpty { installStore.planResourceIds() }
        val targetId = plan.targetResourceId.takeIf(String::isNotBlank)
        return if (targetId != null && ids.isNotEmpty() &&
            (target.id in ids || target.id == targetId)
        ) {
            val planTarget = target(targetId) ?: target
            listOf(installWizardEffect(planTarget, ids))
        } else {
            message("${target.name} 正在处理，获取向导暂不可恢复")
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
                ResourceActionEffect.OpenRun(recipe.id, existing.instanceId, autoStart = false),
                ResourceActionEffect.Message("${target.name} 已在运行，正在打开原实例")
            )
        }
        RuntimeLaunchTrace.mark(instanceId, RuntimeLaunchTrace.RESOURCE_PREFLIGHT_STARTED)
        val invalidated = withContext(Dispatchers.IO) {
            reconcileInstalledResources(listOf(target.manifest), environmentId)
        }
        RuntimeLaunchTrace.mark(instanceId, RuntimeLaunchTrace.RESOURCE_PREFLIGHT_COMPLETED)
        if (target.id in invalidated) {
            RuntimeLaunchTrace.mark(instanceId, RuntimeLaunchTrace.RESOURCE_PREFLIGHT_INVALIDATED)
            return listOf(ResourceActionEffect.Message("检测到 ${target.name} 的安装内容缺失，正在准备修复")) +
                install(target.id)
        }
        RuntimeLaunchTrace.mark(instanceId, RuntimeLaunchTrace.ACTION_DISPATCHED)
        return if (shouldOpenRunTask(recipe)) {
            listOf(
                ResourceActionEffect.OpenRun(recipe.id, instanceId, autoStart = true),
                ResourceActionEffect.Message("正在打开 ${target.name}")
            )
        } else {
            when (val result = runOrchestrator.start(
                RunStartRequest(
                    recipe = recipe,
                    instanceId = instanceId,
                    ownerKind = CardRunState.OWNER_KIND_RESOURCE,
                    stepId = target.id,
                    environmentId = environmentId
                )
            )) {
                is RunCommandResult.Accepted -> message("正在打开 ${target.name}")
                is RunCommandResult.Ignored -> result.asResourceStartEffect("资源运行未启动")
            }
        }
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
            targets.map { target ->
                target to versionCoordinator.check(target.manifest, environmentId)
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
        val target = target(resourceId) ?: return message("资源目录正在更新，请稍后重试")
        if (!target.manifest.management.userLifecycleEnabled) return message("${target.name} 是 Kite 管理的系统组件")
        if (!installStore.isInstalled(target.id, environmentId)) return message("请先获取 ${target.name}")
        val entry = installStore.registryEntry(target.id, environmentId)
        val targetVersion = entry?.latestVersion.orEmpty()
        if (entry?.updateStatus != KiteResourceInstallStore.UPDATE_STATUS_AVAILABLE || targetVersion.isBlank()) {
            return message("请先检查 ${target.name} 的可用更新")
        }
        if (!KiteResourceSourcePlanFactory.plan(target.manifest, targetVersion).capabilities.update) {
            return message("${target.name} 的来源暂不支持确定性更新")
        }
        val recipe = runCoordinator.recipe(target.id, KiteResourceInstallRecipes.OP_UPDATE, targetVersion)
            ?: return message("${target.name} 的更新入口不存在")
        return startManagedOperation(target, recipe, KiteResourceInstallRecipes.OP_UPDATE, targetVersion, environmentId)
    }

    override suspend fun reinstall(resourceId: String): List<ResourceActionEffect> {
        val environmentId = installStore.currentEnvironmentId()
        val target = target(resourceId) ?: return message("资源目录正在更新，请稍后重试")
        if (!target.manifest.management.userLifecycleEnabled) return message("${target.name} 是 Kite 管理的系统组件")
        if (!installStore.isInstalled(target.id, environmentId)) return message("请先获取 ${target.name}")
        val capabilities = KiteResourceSourcePlanFactory.plan(target.manifest).capabilities
        if (!capabilities.install || !capabilities.uninstall) return message("${target.name} 暂不支持重新安装")
        val recipe = runCoordinator.recipe(target.id, KiteResourceInstallRecipes.OP_REINSTALL)
            ?: return message("${target.name} 的重新安装入口不存在")
        return startManagedOperation(target, recipe, KiteResourceInstallRecipes.OP_REINSTALL, environmentId = environmentId)
    }

    override suspend fun cancelInstall(resourceId: String): List<ResourceActionEffect> {
        val environmentId = installStore.currentEnvironmentId()
        val plan = installStore.planSnapshot(environmentId)
        val planIds = plan.resourceIds.takeIf { resourceId in it || resourceId == plan.targetResourceId }.orEmpty()
            .ifEmpty { listOf(resourceId) }
        val targetId = plan.targetResourceId.takeIf(String::isNotBlank) ?: resourceId
        return cancelPlanForEnvironment(targetId, planIds, environmentId)
    }

    override suspend fun cancelFailedInstall(resourceId: String): List<ResourceActionEffect> {
        val environmentId = installStore.currentEnvironmentId()
        val target = target(resourceId) ?: return message("资源目录正在更新，请稍后重试")
        return uninstall(target, ResourceRunContinuation.CancelFailedInstall, environmentId)
    }

    override suspend fun cancelPlan(
        targetResourceId: String,
        planResourceIds: List<String>
    ): List<ResourceActionEffect> = cancelPlanForEnvironment(
        targetResourceId,
        planResourceIds,
        installStore.currentEnvironmentId()
    )

    private suspend fun cancelPlanForEnvironment(
        targetResourceId: String,
        planResourceIds: List<String>,
        environmentId: String
    ): List<ResourceActionEffect> {
        val resourceIds = planResourceIds.filter(String::isNotBlank)
            .ifEmpty { installStore.planResourceIds(environmentId) }
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
        return if (unfinished.isEmpty()) {
            message("获取任务已取消")
        } else if (cleanupComplete) {
            message("获取任务已取消，临时内容已清理")
        } else {
            message("获取任务已取消，部分临时内容稍后继续清理")
        }
    }

    override suspend fun createHomeCard(resourceId: String): List<ResourceActionEffect> =
        withContext(Dispatchers.IO) {
            val target = target(resourceId) ?: return@withContext message("资源目录正在更新，请稍后重试")
            val template = homeCardTemplate(target) ?: return@withContext message("${target.name} 暂无首页卡片模板")
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
                onSuccess = { message("已添加 ${target.name} 到首页") },
                onFailure = { message("添加失败：${it.message ?: it.javaClass.simpleName}") }
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

    private fun acceptInstallPlan(
        target: ResourceTarget,
        plan: PreparedInstallPlan,
        environmentId: String
    ): List<ResourceActionEffect> {
        if (plan.missing.isNotEmpty()) {
            installStore.clear(target.id, environmentId)
            return message("缺少可获取的基础层：${plan.missing.distinct().joinToString("、")}")
        }
        if (plan.resourceIds.isEmpty()) {
            installStore.clear(target.id, environmentId)
            return message("${target.name} 已经就绪")
        }
        resetPlanTransientState(plan.resourceIds, environmentId)
        installStore.beginPlan(target.id, plan.resourceIds, environmentId)
        return listOf(installWizardEffect(target, plan.resourceIds, environmentId))
    }

    private fun installWizardEffect(
        target: ResourceTarget,
        planResourceIds: List<String>,
        environmentId: String = installStore.currentEnvironmentId()
    ): ResourceActionEffect.OpenInstallWizard {
        val recipe = CardRunSpecialRecipes.installWizard(target.id, target.name)
        CardRunStore.registerRecipe(recipe)
        val current = CardRunStore.currentForRecipe(recipe.id, environmentId)
            ?.takeIf { state ->
                state.ownerKind == CardRunState.OWNER_KIND_INSTALL_WIZARD &&
                    state.stepId == target.id &&
                    state.status !in INSTALL_WIZARD_ENDED_STATUSES
            }
        val instanceId = current?.instanceId
            ?: "resource-install-wizard-${KiteResourceInstallRecipes.safeId(target.id)}-${UUID.randomUUID().toString().replace("-", "")}"
        if (current == null) {
            CardRunStore.update(
                recipe = recipe,
                status = CardRunStatus.Opened,
                instanceId = instanceId,
                ownerKind = CardRunState.OWNER_KIND_INSTALL_WIZARD,
                stepId = target.id,
                surface = com.kite.app.run.CardRunSurface.InstallWizard,
                currentStepIndex = 0,
                lastMeaningfulOutput = "等待获取确认",
                environmentId = environmentId
            )
        }
        return ResourceActionEffect.OpenInstallWizard(
            recipeId = recipe.id,
            instanceId = instanceId,
            targetResourceId = target.id,
            planResourceIds = planResourceIds
        )
    }

    private suspend fun uninstall(
        target: ResourceTarget,
        continuation: ResourceRunContinuation,
        environmentId: String = installStore.currentEnvironmentId()
    ): List<ResourceActionEffect> {
        val recipe = runCoordinator.recipe(target.id, KiteResourceInstallRecipes.OP_UNINSTALL)
        if (recipe == null) {
            installStore.clear(target.id, environmentId)
            return when (continuation) {
                ResourceRunContinuation.Reinstall -> install(target.id)
                ResourceRunContinuation.CancelFailedInstall -> {
                    installStore.clearPlan(environmentId)
                    message("已取消 ${target.name} 的失败获取")
                }
                else -> message("已移除 ${target.name} 的获取记录")
            }
        }
        return when (val result = runCoordinator.start(
            ResourceRunLaunchRequest(
                resourceId = target.id,
                recipe = recipe,
                operation = KiteResourceInstallRecipes.OP_UNINSTALL,
                continuation = continuation,
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
    ): List<ResourceActionEffect> = when (val result = runCoordinator.start(
        ResourceRunLaunchRequest(
            resourceId = target.id,
            recipe = recipe,
            operation = operation,
            targetVersion = targetVersion,
            stageBundledResource = runCoordinator.isBundled(target.id),
            environmentId = environmentId
        )
    )) {
        is ResourceRunLaunchResult.Accepted -> message("正在${operationLabel(operation)} ${target.name}")
        is ResourceRunLaunchResult.Rejected -> result.asResourceStartEffect("资源${operationLabel(operation)}未启动")
    }

    private fun operationLabel(operation: String): String = when (operation) {
        KiteResourceInstallRecipes.OP_UPDATE -> "更新"
        KiteResourceInstallRecipes.OP_REINSTALL -> "重新安装"
        KiteResourceInstallRecipes.OP_UNINSTALL -> "卸载"
        else -> "获取"
    }

    private suspend fun buildInstallPlan(target: ResourceTarget, environmentId: String): PreparedInstallPlan {
        manifestLoader.invalidate()
        val manifests = manifestLoader.manifests().values.filter { it.sections.isNotEmpty() }
        reconcileInstalledResources(manifests, environmentId)
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
            val ids = serverPlan.resourceIds.filter { id ->
                byId[id]?.let { !isInstalled(it, environmentId) || id == target.id } == true
            }
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
        return PreparedInstallPlan(ordered.toList(), missing)
    }

    private suspend fun reconcileInstalledResources(
        manifests: Collection<KiteResourceManifest>,
        environmentId: String
    ): Set<String> {
        val requirements = ResourceManagedCommandProbeProtocol.normalize(
            manifests
                .asSequence()
                .filter { manifest -> installStore.isInstalled(manifest.id, environmentId) }
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
            installStore.invalidateMissingInstallations(missing, environmentId)
        }
        return missing
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
        installStore.clearPlan(environmentId)
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
