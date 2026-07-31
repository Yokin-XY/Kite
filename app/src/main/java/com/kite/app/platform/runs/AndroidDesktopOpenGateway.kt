package com.kite.app.platform.runs

import android.content.Context
import com.kite.app.application.runs.CardRunSpecialRecipes
import com.kite.app.application.runs.DesktopOpenGateway
import com.kite.app.application.runs.DesktopOpenRequest
import com.kite.app.application.runs.DesktopOpenResult
import com.kite.app.diagnostics.KiteDiagnostics
import com.kite.app.recipe.KiteRecipe
import com.kite.app.run.CardRunState
import com.kite.app.run.CardRunStatus
import com.kite.app.run.CardRunStore
import com.kite.app.run.CardRunSurface
import com.kite.app.run.KiteX11SurfacePlan
import com.kite.app.run.KiteX11SurfaceServer
import java.util.UUID

/** 本地桌面请求的 X11/Store 适配器；窗口跳转仍由 Shell 解释结果。 */
internal class AndroidDesktopOpenGateway(
    context: Context,
    private val diagnostics: KiteDiagnostics,
    private val recipeResolver: (String) -> KiteRecipe?,
    private val environmentIdProvider: () -> String = { CardRunState.DEFAULT_ENVIRONMENT_ID }
) : DesktopOpenGateway {
    private val appContext = context.applicationContext

    override fun open(request: DesktopOpenRequest): DesktopOpenResult {
        val environmentId = environmentIdProvider()
        val existing = request.instanceId
            ?.takeIf(String::isNotBlank)
            ?.let { CardRunStore.get(it, environmentId) }
        val recipeId = request.recipeId?.takeIf(String::isNotBlank)
            ?: existing?.recipeId
            ?: "temp_desktop_${UUID.randomUUID().toString().replace("-", "")}"
        val recipe = CardRunStore.registeredRecipe(recipeId)
            ?: recipeResolver(recipeId)
            ?: CardRunSpecialRecipes.temporaryDesktop(
                recipeId,
                request.command,
                request.title?.takeIf(String::isNotBlank) ?: "临时桌面"
            )
        val instanceId = request.instanceId?.takeIf(String::isNotBlank)
            ?: "run_${recipe.id}_${UUID.randomUUID().toString().replace("-", "")}"
        val binding = existing?.x11Display?.let(KiteX11SurfacePlan::binding)
            ?: KiteX11SurfacePlan.allocate(
                instanceId,
                CardRunStore.snapshot()
                    .filter { it.environmentId == environmentId }
                    .filterNot { it.instanceId == instanceId }
                    .mapNotNull(CardRunState::x11Display)
                    .toSet()
            )
        CardRunStore.registerRecipe(recipe)
        CardRunStore.start(
            recipe = recipe,
            instanceId = instanceId,
            ownerKind = CardRunState.OWNER_KIND_X11,
            stepId = DESKTOP_REQUEST_STEP,
            environmentId = environmentId
        )
        CardRunStore.update(
            recipe = recipe,
            status = CardRunStatus.Running,
            instanceId = instanceId,
            ownerKind = CardRunState.OWNER_KIND_X11,
            stepId = DESKTOP_REQUEST_STEP,
            surface = CardRunSurface.Report,
            currentStepIndex = 0,
            lastMeaningfulOutput = "正在准备 X11 桌面：${request.command.take(120)}",
            x11Display = binding.display,
            x11SocketPath = binding.socketPath,
            clearNextActionUrl = true,
            environmentId = environmentId
        )
        val started = KiteX11SurfaceServer.ensureStarted(appContext, binding)
        if (started.isFailure) {
            val message = started.exceptionOrNull()?.message ?: "native X11 启动失败"
            CardRunStore.update(
                recipe = recipe,
                status = CardRunStatus.Failed,
                instanceId = instanceId,
                ownerKind = CardRunState.OWNER_KIND_X11,
                stepId = DESKTOP_REQUEST_STEP,
                surface = CardRunSurface.Report,
                currentStepIndex = 0,
                lastError = message,
                x11Display = binding.display,
                x11SocketPath = binding.socketPath,
                environmentId = environmentId
            )
            diagnostics.logRecipeAction(
                recipe,
                "desktop_request_x11_failed",
                mapOf(
                    "instanceId" to instanceId,
                    "source" to request.source,
                    "display" to binding.display,
                    "error" to message
                )
            )
            return DesktopOpenResult(
                accepted = false,
                recipeId = recipe.id,
                instanceId = instanceId,
                error = message,
                openRunTask = request.instanceId.isNullOrBlank()
            )
        }
        CardRunStore.update(
            recipe = recipe,
            status = CardRunStatus.Running,
            instanceId = instanceId,
            ownerKind = CardRunState.OWNER_KIND_X11,
            stepId = DESKTOP_REQUEST_STEP,
            surface = CardRunSurface.X11,
            currentStepIndex = 0,
            lastMeaningfulOutput = "Ubuntu 请求桌面：${request.command.take(120)}",
            x11Display = binding.display,
            x11SocketPath = binding.socketPath,
            clearNextActionUrl = true,
            environmentId = environmentId
        )
        diagnostics.logRecipeAction(
            recipe,
            "desktop_request_accepted",
            mapOf(
                "instanceId" to instanceId,
                "source" to request.source,
                "display" to binding.display,
                "command" to request.command.take(500)
            )
        )
        return DesktopOpenResult(
            accepted = true,
            recipeId = recipe.id,
            instanceId = instanceId,
            display = binding.display,
            socketPath = binding.socketPath,
            openRunTask = request.instanceId.isNullOrBlank()
        )
    }

    private companion object {
        const val DESKTOP_REQUEST_STEP = "desktop_request"
    }
}
