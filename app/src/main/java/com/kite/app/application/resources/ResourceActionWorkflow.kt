package com.kite.app.application.resources

import com.kite.app.action.KiteResourceActionIntent
import com.kite.app.action.KiteResourceActionRequest

internal sealed interface ResourceActionEffect {
    data class OpenRun(
        val recipeId: String,
        val instanceId: String,
        val autoStart: Boolean
    ) : ResourceActionEffect

    data class OpenInstallWizard(
        val recipeId: String,
        val instanceId: String,
        val targetResourceId: String,
        val planResourceIds: List<String>
    ) : ResourceActionEffect

    data class Message(val text: String) : ResourceActionEffect
}

internal interface ResourceActionGateway {
    suspend fun install(resourceId: String): List<ResourceActionEffect>
    suspend fun reopenInstall(resourceId: String): List<ResourceActionEffect>
    suspend fun open(resourceId: String): List<ResourceActionEffect>
    suspend fun stop(resourceId: String): List<ResourceActionEffect>
    suspend fun uninstall(resourceId: String): List<ResourceActionEffect>
    suspend fun cancelInstall(resourceId: String): List<ResourceActionEffect>
    suspend fun cancelFailedInstall(resourceId: String): List<ResourceActionEffect>
    suspend fun cancelPlan(targetResourceId: String, planResourceIds: List<String>): List<ResourceActionEffect>
    suspend fun createHomeCard(resourceId: String): List<ResourceActionEffect>
    suspend fun installDirect(resourceId: String): List<ResourceActionEffect>
}

/**
 * 资源动作统一入口。Feature 只提交稳定意图，Shell 只解释返回 Effect；
 * 安装、运行和清理事实由 Gateway 后面的既有状态拥有者写入。
 */
internal class ResourceActionWorkflowCoordinator(
    private val gateway: ResourceActionGateway
) {
    suspend fun dispatch(request: KiteResourceActionRequest): List<ResourceActionEffect> =
        when (request.intent) {
            KiteResourceActionIntent.Install -> gateway.install(request.resourceId)
            KiteResourceActionIntent.ReopenInstall -> gateway.reopenInstall(request.resourceId)
            KiteResourceActionIntent.Open -> gateway.open(request.resourceId)
            KiteResourceActionIntent.Stop -> gateway.stop(request.resourceId)
            KiteResourceActionIntent.Uninstall -> gateway.uninstall(request.resourceId)
            KiteResourceActionIntent.CancelInstall -> gateway.cancelInstall(request.resourceId)
            KiteResourceActionIntent.CancelFailedInstall -> gateway.cancelFailedInstall(request.resourceId)
            KiteResourceActionIntent.BusyStatus -> listOf(ResourceActionEffect.Message("资源正在卸载"))
            KiteResourceActionIntent.Unsupported -> listOf(ResourceActionEffect.Message("资源动作暂不可用"))
        }

    suspend fun cancelPlan(
        targetResourceId: String,
        planResourceIds: List<String>
    ): List<ResourceActionEffect> = gateway.cancelPlan(targetResourceId, planResourceIds)

    suspend fun createHomeCard(resourceId: String): List<ResourceActionEffect> =
        gateway.createHomeCard(resourceId)

    suspend fun installDirect(resourceId: String): List<ResourceActionEffect> =
        gateway.installDirect(resourceId)
}
