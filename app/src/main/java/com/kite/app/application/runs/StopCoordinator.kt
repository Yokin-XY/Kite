package com.kite.app.application.runs

import com.kite.app.recipe.KiteRecipe
import com.kite.app.run.CardRunState
import com.kite.app.run.CardRunStatus

internal sealed interface StopPlan {
    data class Ignore(val reason: String) : StopPlan
    data class CompleteLocally(val summary: String) : StopPlan
    data class Execute(val request: RecipeStopRequest) : StopPlan
}

internal data class StopResolution(
    val summary: String,
    val cleanupPending: Boolean
)

/** 只解释停止策略与确认结果，不调用 Bridge、终端或页面。 */
internal class StopCoordinator {
    fun plan(recipe: KiteRecipe, state: CardRunState): StopPlan {
        if (state.status == CardRunStatus.Stopping) return StopPlan.Ignore("already_stopping")
        if (state.status == CardRunStatus.Stopped) return StopPlan.Ignore("already_stopped")

        val terminalSessionId = state.terminalSessionId?.takeIf { it.isNotBlank() }
        val request = RecipeStopRequest(
            recipe = recipe,
            instanceId = state.instanceId,
            generation = state.createdAt,
            runtimeOwnerIds = state.ownedRuntimeOwnerIds,
            runId = state.runId,
            terminalSessionId = terminalSessionId,
            pid = state.pid,
            rootPid = state.rootPid,
            processGroupId = state.processGroupId,
            systemSessionId = state.systemSessionId,
            interruptTerminal = terminalSessionId != null
        )
        if (terminalSessionId == null && !request.hasBridgeProcessBinding() && state.status == CardRunStatus.Opened) {
            return StopPlan.CompleteLocally("网页实例已关闭")
        }
        if (terminalSessionId == null && !request.hasBridgeProcessBinding() && !state.isInterruptible()) {
            return StopPlan.Ignore("not_running")
        }
        return StopPlan.Execute(request)
    }

    /**
     * 用户停止是单向的逻辑关闭。执行层未及时收敛只表示旧代需要后台回收，
     * 不能把已经关闭的卡片、窗口和运行绑定恢复成 Running。
     */
    fun resolve(result: StopExecutionResult): StopResolution {
        val hasRemaining = result.remainingProcessIds.any { value ->
            value.trim().matches(Regex("\\d+"))
        }
        val cleanupPending = hasRemaining || (
            result.outcome != StopExecutionOutcome.Confirmed &&
                !result.manualKillObserved &&
                !result.residueMarkerObserved
            )
        return StopResolution(summary = "已关闭", cleanupPending = cleanupPending)
    }
}
