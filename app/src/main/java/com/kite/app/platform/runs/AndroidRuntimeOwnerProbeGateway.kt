package com.kite.app.platform.runs

import com.kite.app.application.runs.CardRunSpecialRecipes
import com.kite.app.application.runs.RunCommandResult
import com.kite.app.application.runs.RunOrchestrator
import com.kite.app.application.runs.RunStartRequest
import com.kite.app.application.runs.RuntimeOwnerProbeGateway
import com.kite.app.diagnostics.KiteDiagnostics
import com.kite.app.run.CardRunState

/** Debug-gated owner 探针适配器，复用正式 RunOrchestrator 路径。 */
internal class AndroidRuntimeOwnerProbeGateway(
    private val orchestrator: RunOrchestrator,
    private val diagnostics: KiteDiagnostics
) : RuntimeOwnerProbeGateway {
    override fun start(resourceId: String, instanceId: String): RunCommandResult {
        val recipe = CardRunSpecialRecipes.resourceOwnerProbe(resourceId)
        diagnostics.logRecipeEvent(
            "kite_runtime_automation_start_resource_owner_probe",
            recipe,
            mapOf("resourceId" to resourceId, "instanceId" to instanceId)
        )
        return orchestrator.start(
            RunStartRequest(
                recipe = recipe,
                instanceId = instanceId,
                ownerKind = CardRunState.OWNER_KIND_RESOURCE,
                stepId = resourceId
            )
        )
    }
}
