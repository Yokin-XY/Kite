package com.kite.app.platform.runs

import com.kite.app.application.runs.RunStartRequest
import com.kite.app.application.runs.RunStateGateway
import com.kite.app.application.runs.RunStateMutation
import com.kite.app.recipe.KiteRecipe
import com.kite.app.run.CardRunState
import com.kite.app.run.CardRunStore

/** 将纯编排合同适配到现有 CardRunStore，运行事实仍只有一份。 */
internal class AndroidRunStateGateway : RunStateGateway {
    override fun register(recipe: KiteRecipe) {
        CardRunStore.registerRecipe(recipe)
    }

    override fun recipe(recipeId: String): KiteRecipe? =
        CardRunStore.registeredRecipe(recipeId)

    override fun state(instanceId: String): CardRunState? =
        CardRunStore.get(instanceId)

    override fun current(recipeId: String): CardRunState? =
        CardRunStore.currentForRecipe(recipeId)

    override fun start(request: RunStartRequest): CardRunState =
        CardRunStore.start(
            recipe = request.recipe,
            instanceId = request.instanceId,
            parentInstanceId = request.parentInstanceId,
            ownerKind = request.ownerKind,
            stepId = request.stepId
        )

    override fun update(
        recipe: KiteRecipe,
        instanceId: String,
        mutation: RunStateMutation
    ): CardRunState = CardRunStore.update(
        recipe = recipe,
        status = mutation.status,
        instanceId = instanceId,
        surface = mutation.surface,
        currentStepIndex = mutation.currentStepIndex,
        runId = mutation.runId,
        terminalSessionId = mutation.terminalSessionId,
        pid = mutation.pid,
        rootPid = mutation.rootPid,
        processGroupId = mutation.processGroupId,
        systemSessionId = mutation.systemSessionId,
        lastMeaningfulOutput = mutation.lastMeaningfulOutput,
        lastError = mutation.lastError,
        shellReportText = mutation.shellReportText,
        nextActionUrl = mutation.nextActionUrl,
        x11Display = mutation.x11Display,
        x11SocketPath = mutation.x11SocketPath,
        clearRunBinding = mutation.clearRunBinding,
        clearTerminalSession = mutation.clearTerminalSession,
        clearNextActionUrl = mutation.clearNextActionUrl
    )
}
