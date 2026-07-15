package com.kite.app.application.runs

import com.kite.app.recipe.KiteRecipe
import com.kite.app.run.CardRunState
import com.kite.app.run.CardRunStatus

internal sealed interface StopPlan {
    data class Ignore(val reason: String) : StopPlan
    data class CompleteLocally(val summary: String) : StopPlan
    data class Execute(val request: RecipeStopRequest) : StopPlan
}

internal sealed interface StopResolution {
    data class Stopped(val summary: String) : StopResolution
    data class Restore(val status: CardRunStatus, val error: String) : StopResolution
}

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

    fun resolve(previousState: CardRunState, result: StopExecutionResult): StopResolution {
        val remaining = result.remainingProcessIds
            .map { it.trim() }
            .filter { it.matches(Regex("\\d+")) }
            .distinct()
        if (remaining.isNotEmpty()) {
            return StopResolution.Restore(
                previousState.status,
                "停止后仍有进程残留：${remaining.joinToString(",")}"
            )
        }
        if (result.residueMarkerObserved) {
            return StopResolution.Stopped("已停止，未发现进程残留")
        }
        if (result.outcome == StopExecutionOutcome.Confirmed || result.manualKillObserved) {
            return StopResolution.Stopped(result.message.ifBlank { "已停止" })
        }
        val error = when (result.outcome) {
            StopExecutionOutcome.Timeout -> "停止超时，Bridge 未及时响应"
            StopExecutionOutcome.ConnectionError -> "Bridge 连接失败"
            StopExecutionOutcome.Unsupported -> "停止接口暂不可用"
            StopExecutionOutcome.ParseError -> "停止响应解析失败"
            StopExecutionOutcome.Failed,
            StopExecutionOutcome.Confirmed -> result.message.ifBlank { "Bridge 返回停止失败" }
        }
        return StopResolution.Restore(previousState.status, error)
    }
}
