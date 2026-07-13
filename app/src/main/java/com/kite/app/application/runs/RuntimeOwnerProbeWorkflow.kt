package com.kite.app.application.runs

import com.kite.app.resources.KiteResourceInstallRecipes

internal data class RuntimeOwnerProbeRequest(
    val resourceId: String,
    val instanceId: String?
)

internal fun interface RuntimeOwnerProbeGateway {
    fun start(resourceId: String, instanceId: String): RunCommandResult
}

internal class RuntimeOwnerProbeCoordinator(
    private val gateway: RuntimeOwnerProbeGateway
) {
    fun start(request: RuntimeOwnerProbeRequest): RunCommandResult {
        val resourceId = KiteResourceInstallRecipes.safeId(
            request.resourceId.trim().ifBlank { DEFAULT_RESOURCE_ID }
        )
        val instanceId = request.instanceId?.trim()?.takeIf(String::isNotBlank)
            ?: "resource-owner-probe-$resourceId"
        return gateway.start(resourceId, instanceId)
    }

    private companion object {
        const val DEFAULT_RESOURCE_ID = "kite.owner.telemetry.probe"
    }
}
