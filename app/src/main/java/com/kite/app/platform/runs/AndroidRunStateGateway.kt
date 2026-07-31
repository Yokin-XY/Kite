package com.kite.app.platform.runs

import com.kite.app.application.runs.RunStartRequest
import com.kite.app.application.runs.RunStateGateway
import com.kite.app.application.runs.RunStateMutation
import com.kite.app.recipe.KiteRecipe
import com.kite.app.run.CardRunState
import com.kite.app.run.CardRunStore

/** 将纯编排合同适配到现有 CardRunStore，运行事实仍只有一份。 */
internal class AndroidRunStateGateway(
    private val environmentIdProvider: () -> String = { CardRunState.DEFAULT_ENVIRONMENT_ID }
) : RunStateGateway {
    override fun register(recipe: KiteRecipe) {
        CardRunStore.registerRecipe(recipe)
    }

    override fun recipe(recipeId: String): KiteRecipe? =
        CardRunStore.registeredRecipe(recipeId)

    override fun state(instanceId: String): CardRunState? =
        CardRunStore.get(instanceId, environmentIdProvider())

    override fun current(recipeId: String): CardRunState? =
        CardRunStore.currentForRecipe(recipeId, environmentIdProvider())

    override fun start(request: RunStartRequest): CardRunState =
        CardRunStore.start(
            recipe = request.recipe,
            instanceId = request.instanceId,
            parentInstanceId = request.parentInstanceId,
            ownerKind = request.ownerKind,
            stepId = request.stepId,
            agentId = request.agentId,
            environmentId = request.environmentId.ifBlank(environmentIdProvider)
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
        runtimeRootOwnerId = mutation.runtimeRootOwnerId,
        runtimeOwnerId = mutation.runtimeOwnerId,
        runtimeUnitId = mutation.runtimeUnitId,
        ownedRuntimeOwnerIds = mutation.ownedRuntimeOwnerIds,
        runId = mutation.runId,
        terminalSessionId = mutation.terminalSessionId,
        pid = mutation.pid,
        rootPid = mutation.rootPid,
        processGroupId = mutation.processGroupId,
        systemSessionId = mutation.systemSessionId,
        runtimeLane = mutation.runtimeLane,
        runtimeFallbackReason = mutation.runtimeFallbackReason,
        lastMeaningfulOutput = mutation.lastMeaningfulOutput,
        lastError = mutation.lastError,
        shellReportText = mutation.shellReportText,
        nextActionUrl = mutation.nextActionUrl,
        x11Display = mutation.x11Display,
        x11SocketPath = mutation.x11SocketPath,
        agentId = mutation.agentId,
        agentBinding = mutation.agentBinding,
        clearRunBinding = mutation.clearRunBinding,
        clearTerminalSession = mutation.clearTerminalSession,
        clearNextActionUrl = mutation.clearNextActionUrl,
        clearAgentBinding = mutation.clearAgentBinding,
        environmentId = environmentIdProvider()
    )
}
