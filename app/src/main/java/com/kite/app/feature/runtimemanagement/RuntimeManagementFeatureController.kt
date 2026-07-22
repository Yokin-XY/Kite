package com.kite.app.feature.runtimemanagement

import com.kite.app.application.runtimemanagement.RuntimeManagementCommand
import com.kite.app.application.runtimemanagement.RuntimeManagementCommandPhase
import com.kite.app.application.runtimemanagement.RuntimeManagementCoordinator
import com.kite.app.application.runtimemanagement.RuntimeManagementGateway
import com.kite.app.application.runtimemanagement.RuntimeManagementSnapshot
import com.kite.app.application.runtimemanagement.RuntimeManagementSubmitResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 运行管理的纯状态控制器。页面只提交数据动作，Shell 只接收导航 Effect。 */
internal class RuntimeManagementFeatureController(
    private val gateway: RuntimeManagementGateway,
    private val coordinator: RuntimeManagementCoordinator,
    private val text: RuntimeManagementText = RuntimeManagementText.zhCn(),
) {
    private val mutableState = MutableStateFlow(
        RuntimeManagementProjector.project(gateway.currentSnapshot(), text = text)
    )
    val state: StateFlow<RuntimeManagementUiState> = mutableState.asStateFlow()

    fun reconcile(snapshot: RuntimeManagementSnapshot = gateway.currentSnapshot()) {
        coordinator.reconcile(snapshot)
        publish(snapshot)
    }

    suspend fun dispatch(action: RuntimeManagementFeatureAction): RuntimeManagementFeatureEffect? = when (action) {
        is RuntimeManagementFeatureAction.Refresh -> {
            coordinator.refresh(action.force)
            reconcile()
            null
        }
        is RuntimeManagementFeatureAction.DismissFailure -> {
            coordinator.dismissFailure(action.mutationKey)
            reconcile()
            null
        }
        is RuntimeManagementFeatureAction.Submit -> submit(action.action)
    }

    private suspend fun submit(action: RuntimeManagementActionUiState): RuntimeManagementFeatureEffect? {
        if (!action.enabled) return null
        val effect = when (val target = action.target) {
            RuntimeManagementActionTarget.Refresh -> {
                coordinator.refresh(force = true)
                null
            }
            is RuntimeManagementActionTarget.OpenSurface -> RuntimeManagementFeatureEffect.OpenSurface(
                recipeId = target.recipeId,
                instanceId = target.instanceId,
                surface = target.surface
            )
            is RuntimeManagementActionTarget.StopRun -> submitCommand(
                RuntimeManagementCommand.StopRun(target.instanceId, action.mutationKey)
            )
            is RuntimeManagementActionTarget.EndTerminal -> submitCommand(
                RuntimeManagementCommand.EndTerminal(target.sessionId, action.mutationKey)
            )
            is RuntimeManagementActionTarget.EndProcess -> submitCommand(
                RuntimeManagementCommand.EndProcess(
                    processId = target.processId,
                    pid = target.pid,
                    mutationKey = action.mutationKey
                )
            )
            is RuntimeManagementActionTarget.EndProcessTree -> submitCommand(
                RuntimeManagementCommand.EndProcessTree(
                    processIds = target.processIds,
                    mutationKey = action.mutationKey,
                )
            )
            is RuntimeManagementActionTarget.StopBackgroundRuntime -> submitCommand(
                RuntimeManagementCommand.StopBackgroundRuntime(target.runtimeId, action.mutationKey)
            )
            is RuntimeManagementActionTarget.RestartBackgroundRuntime -> submitCommand(
                RuntimeManagementCommand.RestartBackgroundRuntime(target.runtimeId, action.mutationKey)
            )
        }
        publish(gateway.currentSnapshot())
        return effect
    }

    private suspend fun submitCommand(command: RuntimeManagementCommand): RuntimeManagementFeatureEffect? =
        when (val result = coordinator.submit(command)) {
            is RuntimeManagementSubmitResult.Accepted -> null
            is RuntimeManagementSubmitResult.Ignored ->
                if (result.reason == "already_pending") null
                else RuntimeManagementFeatureEffect.ActionRejected(result.reason)
        }

    private fun publish(snapshot: RuntimeManagementSnapshot) {
        val mutations = coordinator.commands.value.mapValues { (key, command) ->
            RuntimeManagementMutation(
                key = key,
                phase = when (command.phase) {
                    RuntimeManagementCommandPhase.Requested -> RuntimeManagementMutationPhase.Requested
                    RuntimeManagementCommandPhase.AwaitingConfirmation ->
                        RuntimeManagementMutationPhase.AwaitingConfirmation
                    RuntimeManagementCommandPhase.Failed -> RuntimeManagementMutationPhase.Failed
                },
                message = command.message
            )
        }
        mutableState.value = RuntimeManagementProjector.project(snapshot, mutations, text)
    }
}
