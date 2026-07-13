package com.kite.app.platform.resources

import android.content.Context
import com.kite.app.application.recipes.RecipeFeatureGateway
import com.kite.app.application.resources.ResourceActionEffect
import com.kite.app.application.resources.ResourceActionGateway
import com.kite.app.application.resources.ResourceRunContinuation
import com.kite.app.application.resources.ResourceRunCoordinator
import com.kite.app.application.resources.ResourceRunLaunchRequest
import com.kite.app.application.resources.ResourceRunLaunchResult
import com.kite.app.application.runs.RunCommandResult
import com.kite.app.application.runs.RunOrchestrator
import com.kite.app.application.runs.RunStartRequest
import com.kite.app.bridge.KiteBridgeClient
import com.kite.app.application.runs.CardRunSpecialRecipes
import com.kite.app.foundation.toolchain.ToolchainPackInstaller
import com.kite.app.recipe.KiteExecution
import com.kite.app.recipe.KiteLaunchConfig
import com.kite.app.recipe.KiteRecipe
import com.kite.app.recipe.KiteRecipeIcon
import com.kite.app.recipe.KiteRecipeLoader
import com.kite.app.recipe.KiteRecipeStep
import com.kite.app.resources.KiteResourceInstallRecipes
import com.kite.app.resources.KiteResourceInstallStore
import com.kite.app.resources.KiteResourceManifest
import com.kite.app.resources.KiteResourceManifestLoader
import com.kite.app.resources.KiteResourceRequestPolicy
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
    private val bridgeClient: KiteBridgeClient
) : ResourceActionGateway {
    private val appContext = context.applicationContext

    override suspend fun install(resourceId: String): List<ResourceActionEffect> {
        val target = target(resourceId) ?: return message("资源目录正在更新，请稍后重试")
        if (installStore.isFailed(target.id) &&
            installStore.failedOperation(target.id) != KiteResourceInstallStore.OP_UNINSTALL
        ) {
            return uninstall(target, ResourceRunContinuation.Reinstall)
        }
        installStore.markPreparing(target.id)
        return runCatching { withContext(Dispatchers.IO) { buildInstallPlan(target) } }
            .fold(
                onSuccess = { plan -> acceptInstallPlan(target, plan) },
                onFailure = { error ->
                    val reason = error.message ?: error.javaClass.simpleName
                    installStore.markFailed(target.id, KiteResourceInstallStore.OP_INSTALL, null, reason)
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
        val target = target(resourceId) ?: return message("资源目录正在更新，请稍后重试")
        val recipe = openRecipe(target) ?: return message("${target.name} 的打开动作稍后接入")
        CardRunStore.registerRecipe(recipe)
        val existing = CardRunStore.currentForRecipe(recipe.id)
        if (existing?.status == CardRunStatus.Stopping) {
            return message("${target.name} 正在停止，请稍后再启动")
        }
        if (existing != null && existing.isReusableOpenRun()) {
            return listOf(
                ResourceActionEffect.OpenRun(recipe.id, existing.instanceId, autoStart = false),
                ResourceActionEffect.Message("${target.name} 已在运行，正在打开原实例")
            )
        }
        val instanceId = recipe.id
        CardRunStore.start(
            recipe = recipe,
            instanceId = instanceId,
            ownerKind = CardRunState.OWNER_KIND_RESOURCE,
            stepId = target.id
        )
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
                    stepId = target.id
                )
            )) {
                is RunCommandResult.Accepted -> message("正在打开 ${target.name}")
                is RunCommandResult.Ignored -> message("资源运行未启动：${result.reason}")
            }
        }
    }

    override suspend fun stop(resourceId: String): List<ResourceActionEffect> {
        val target = target(resourceId) ?: return message("资源目录正在更新，请稍后重试")
        val recipe = openRecipe(target) ?: return message("${target.name} 的运行入口不存在")
        CardRunStore.registerRecipe(recipe)
        val state = CardRunStore.currentForRecipe(recipe.id)?.takeIf { it.isReusableOpenRun() }
            ?: return message("${target.name} 没有运行中的实例")
        return when (val result = runOrchestrator.stop(state.instanceId)) {
            is RunCommandResult.Accepted -> message("正在中止 ${target.name}")
            is RunCommandResult.Ignored -> message("无法中止 ${target.name}：${result.reason}")
        }
    }

    override suspend fun uninstall(resourceId: String): List<ResourceActionEffect> {
        val target = target(resourceId) ?: return message("资源目录正在更新，请稍后重试")
        return uninstall(target, ResourceRunContinuation.None)
    }

    override suspend fun cancelInstall(resourceId: String): List<ResourceActionEffect> {
        val plan = installStore.planSnapshot()
        val planIds = plan.resourceIds.takeIf { resourceId in it || resourceId == plan.targetResourceId }.orEmpty()
            .ifEmpty { listOf(resourceId) }
        val targetId = plan.targetResourceId.takeIf(String::isNotBlank) ?: resourceId
        return cancelPlan(targetId, planIds)
    }

    override suspend fun cancelFailedInstall(resourceId: String): List<ResourceActionEffect> {
        val target = target(resourceId) ?: return message("资源目录正在更新，请稍后重试")
        return uninstall(target, ResourceRunContinuation.CancelFailedInstall)
    }

    override suspend fun cancelPlan(
        targetResourceId: String,
        planResourceIds: List<String>
    ): List<ResourceActionEffect> {
        val resourceIds = planResourceIds.filter(String::isNotBlank)
            .ifEmpty { installStore.planResourceIds() }
            .ifEmpty { listOfNotNull(targetResourceId.takeIf(String::isNotBlank)) }
            .distinct()
        val unfinished = resourceIds.filterNot(installStore::isInstalled)
        unfinished.forEach { resourceId ->
            listOf(KiteResourceInstallRecipes.OP_INSTALL, KiteResourceInstallRecipes.OP_UNINSTALL)
                .mapNotNull { operation ->
                    CardRunStore.currentForRecipe(KiteResourceInstallRecipes.recipeId(resourceId, operation))
                }
                .filter { state -> state.isBusy() || state.isActive() || state.hasRunBinding() }
                .forEach { state -> runOrchestrator.stop(state.instanceId) }
        }
        val cleanupComplete = if (unfinished.isNotEmpty()) {
            delay(CANCEL_SETTLE_MS)
            cleanupCancelledResources(unfinished)
        } else true
        clearInstallTask(targetResourceId, resourceIds)
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
                recipeLoader.addSharedRecipeTemplate(
                    template,
                    "${KiteResourceInstallRecipes.safeId(target.id)}-home"
                )
                recipeFeatureGateway.invalidateCatalog("resource_home_card_added")
            }.fold(
                onSuccess = { message("已添加 ${target.name} 到首页") },
                onFailure = { message("添加失败：${it.message ?: it.javaClass.simpleName}") }
            )
        }

    private fun acceptInstallPlan(
        target: ResourceTarget,
        plan: PreparedInstallPlan
    ): List<ResourceActionEffect> {
        if (plan.missing.isNotEmpty()) {
            installStore.clear(target.id)
            return message("缺少可获取的基础层：${plan.missing.distinct().joinToString("、")}")
        }
        if (plan.resourceIds.isEmpty()) {
            installStore.clear(target.id)
            return message("${target.name} 已经就绪")
        }
        resetPlanTransientState(plan.resourceIds)
        installStore.beginPlan(target.id, plan.resourceIds)
        return listOf(installWizardEffect(target, plan.resourceIds))
    }

    private fun installWizardEffect(
        target: ResourceTarget,
        planResourceIds: List<String>
    ): ResourceActionEffect.OpenInstallWizard {
        val recipe = CardRunSpecialRecipes.installWizard(target.id, target.name)
        CardRunStore.registerRecipe(recipe)
        val current = CardRunStore.currentForRecipe(recipe.id)
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
                lastMeaningfulOutput = "等待获取确认"
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
        continuation: ResourceRunContinuation
    ): List<ResourceActionEffect> {
        val recipe = runCoordinator.recipe(target.id, KiteResourceInstallRecipes.OP_UNINSTALL)
        if (recipe == null) {
            installStore.clear(target.id)
            return when (continuation) {
                ResourceRunContinuation.Reinstall -> install(target.id)
                ResourceRunContinuation.CancelFailedInstall -> {
                    installStore.clearPlan()
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
                continuation = continuation
            )
        )) {
            is ResourceRunLaunchResult.Accepted -> message("正在卸载 ${target.name}")
            is ResourceRunLaunchResult.Rejected -> message("资源运行未启动：${result.reason}")
        }
    }

    private fun buildInstallPlan(target: ResourceTarget): PreparedInstallPlan {
        manifestLoader.invalidate()
        val manifests = manifestLoader.manifests().values.filter { it.sections.isNotEmpty() }
        val byId = manifests.associateBy(KiteResourceManifest::id)
        val installedIds = manifests.filter(::isInstalled).mapTo(linkedSetOf(), KiteResourceManifest::id)
        val capabilities = installedIds.flatMap { byId[it]?.provides.orEmpty() }.toSet()
        val serverPlan = manifestLoader.requestInstallPlan(target.id, installedIds, capabilities)
        if (serverPlan != null) {
            installStore.putPageCache(
                KiteResourceRequestPolicy.installPlanKey(target.id),
                serverPlan.rawJson.toString(),
                KiteResourceRequestPolicy.INSTALL_PLAN_CACHE_MS
            )
            val unknown = serverPlan.resourceIds.filterNot(byId::containsKey)
            val ids = serverPlan.resourceIds.filter { id -> byId[id]?.let { !isInstalled(it) || id == target.id } == true }
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
                    if (providers.any(::isInstalled)) return@forEach
                    val provider = providers.firstOrNull()
                    if (provider == null) missing += requirement.requirement else visit(provider.id)
                }
            }
            visiting.remove(id)
            val manifest = byId[id]
            if (manifest != null && (!isInstalled(manifest) || id == target.id)) ordered += id
        }
        visit(target.id)
        return PreparedInstallPlan(ordered.toList(), missing)
    }

    private fun isInstalled(manifest: KiteResourceManifest): Boolean =
        installStore.isInstalled(manifest.id) ||
            (manifest.provides.any { it.startsWith("runtime.node") } &&
                ToolchainPackInstaller.isNodeRuntimeInstalled(appContext))

    private fun resetPlanTransientState(resourceIds: List<String>) {
        val recipeIds = resourceIds.distinct().map { resourceId ->
            if (installStore.status(resourceId) != null &&
                installStore.status(resourceId) != KiteResourceInstallStore.STATUS_PREPARING &&
                !installStore.isInstalled(resourceId)
            ) installStore.clear(resourceId)
            KiteResourceInstallRecipes.recipeId(resourceId, KiteResourceInstallRecipes.OP_INSTALL)
        }
        CardRunStore.removeRunStatesForRecipes(recipeIds, removeOpenHistory = true)
    }

    private fun clearInstallTask(targetId: String, resourceIds: List<String>) {
        resourceIds.forEach { resourceId ->
            if (installStore.status(resourceId) != null &&
                !installStore.isInstalled(resourceId) &&
                !installStore.isFailed(resourceId)
            ) installStore.clear(resourceId)
        }
        installStore.clearPlan()
        val recipeIds = resourceIds.flatMap { resourceId ->
            listOf(
                KiteResourceInstallRecipes.recipeId(resourceId, KiteResourceInstallRecipes.OP_INSTALL),
                KiteResourceInstallRecipes.recipeId(resourceId, KiteResourceInstallRecipes.OP_UNINSTALL)
            )
        } + CardRunSpecialRecipes.installWizard(targetId, targetId).id
        CardRunStore.removeRunStatesForRecipes(recipeIds.distinct(), removeOpenHistory = true)
    }

    private suspend fun cleanupCancelledResources(resourceIds: List<String>): Boolean {
        val recipe = cancelCleanupRecipe(resourceIds)
        return withTimeoutOrNull(CLEANUP_TIMEOUT_MS) {
            suspendCancellableCoroutine { continuation ->
                bridgeClient.runRecipe(recipe) { result ->
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

    private data class ResourceTarget(val manifest: KiteResourceManifest) {
        val id: String get() = manifest.id
        val name: String get() = manifest.name.ifBlank { manifest.id }
    }

    private data class PreparedInstallPlan(
        val resourceIds: List<String>,
        val missing: List<String>
    )

    companion object {
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
