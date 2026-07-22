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
    val confirmed: Boolean,
    val cleanupPending: Boolean,
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
            runtimeOwnerIds = state.runtimeOwnerIdsForStop(),
            runId = state.runId,
            terminalSessionId = terminalSessionId,
            pid = state.pid,
            rootPid = state.rootPid,
            processGroupId = state.processGroupId,
            systemSessionId = state.systemSessionId,
            interruptTerminal = terminalSessionId != null
        )
        if (terminalSessionId == null && !request.hasBridgeProcessBinding()) {
            return StopPlan.CompleteLocally("已关闭")
        }
        return StopPlan.Execute(request)
    }

    /** 只有控制层明确确认无残留，卡片才可以清除 owner 和进程绑定。 */
    fun resolve(result: StopExecutionResult): StopResolution {
        val hasRemaining = result.remainingProcessIds.any { value ->
            value.trim().matches(Regex("\\d+"))
        }
        val confirmed = result.outcome == StopExecutionOutcome.Confirmed && !hasRemaining
        return StopResolution(
            summary = if (confirmed) {
                "已关闭"
            } else {
                result.message.trim().ifBlank { "未能确认所有进程已经结束，请重试" }
            },
            confirmed = confirmed,
            cleanupPending = !confirmed,
        )
    }
}
