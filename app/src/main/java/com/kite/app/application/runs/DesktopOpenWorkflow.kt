package com.kite.app.application.runs

internal data class DesktopOpenRequest(
    val command: String,
    val title: String?,
    val recipeId: String?,
    val instanceId: String?,
    val source: String
)

internal data class DesktopOpenResult(
    val accepted: Boolean,
    val recipeId: String? = null,
    val instanceId: String? = null,
    val display: String = "",
    val socketPath: String = "",
    val error: String = "",
    val openRunTask: Boolean = false
)

internal fun interface DesktopOpenGateway {
    fun open(request: DesktopOpenRequest): DesktopOpenResult
}

internal class DesktopOpenCoordinator(
    private val gateway: DesktopOpenGateway
) {
    fun open(request: DesktopOpenRequest): DesktopOpenResult {
        val normalized = request.copy(command = request.command.trim())
        if (normalized.command.isBlank()) {
            return DesktopOpenResult(
                accepted = false,
                recipeId = normalized.recipeId,
                instanceId = normalized.instanceId,
                error = "missing_command"
            )
        }
        return gateway.open(normalized)
    }
}
