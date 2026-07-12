package com.kite.app.platform.browser

import com.kite.app.application.browser.BrowserHandoffCallbackPreparation
import com.kite.app.application.browser.BrowserHandoffGateway
import com.kite.app.application.browser.BrowserHandoffTargetUpdate
import com.kite.app.browser.BrowserAuthSession
import com.kite.app.browser.BrowserAuthSessionKind
import com.kite.app.browser.BrowserAuthSessionStore
import com.kite.app.browser.BrowserHandoffDecision
import com.kite.app.browser.BrowserHandoffPolicy
import com.kite.app.browser.BrowserHandoffRequest
import com.kite.app.browser.BrowserLoopbackCallbackBridge
import com.kite.app.diagnostics.KiteDiagnostics
import com.kite.app.recipe.KiteRecipe
import com.kite.app.run.CardRunStatus
import com.kite.app.run.CardRunStore
import com.kite.app.run.CardRunSurface

/** 把认证桥合同接到 Android session store、loopback 和外部浏览器。 */
internal class AndroidBrowserHandoffGateway(
    private val sessions: BrowserAuthSessionStore,
    private val loopbackBridge: BrowserLoopbackCallbackBridge,
    private val diagnostics: KiteDiagnostics,
    private val recipeResolver: (String) -> KiteRecipe?,
    private val openExternal: (String) -> Boolean
) : BrowserHandoffGateway {
    override fun findPending(request: BrowserHandoffRequest): BrowserAuthSession? =
        sessions.findPending(request.instanceId, request.url)

    override fun createPending(
        request: BrowserHandoffRequest,
        decision: BrowserHandoffDecision
    ): BrowserAuthSession = sessions.createPending(request, decision)

    override fun updateWaiting(
        session: BrowserAuthSession,
        request: BrowserHandoffRequest
    ): BrowserHandoffTargetUpdate? {
        val recipeId = request.recipeId?.takeIf { it.isNotBlank() } ?: return null
        val instanceId = request.instanceId?.takeIf { it.isNotBlank() } ?: return null
        val recipe = recipeResolver(recipeId) ?: CardRunStore.registeredRecipe(recipeId) ?: return null
        val existing = CardRunStore.get(instanceId)
        val status = when (existing?.status) {
            CardRunStatus.Starting,
            CardRunStatus.Running,
            CardRunStatus.WaitingTerminal -> existing.status
            else -> CardRunStatus.Opened
        }
        val message = when (session.kind) {
            BrowserAuthSessionKind.CliLoopback -> "已打开安全浏览器，等待登录发起方接收回调"
            BrowserAuthSessionKind.AppRedirect -> "已打开安全浏览器，等待登录返回 Kite"
            BrowserAuthSessionKind.ExternalOnly -> "已打开系统浏览器"
        }
        val preserveTerminal = session.kind == BrowserAuthSessionKind.CliLoopback &&
            existing?.surface == CardRunSurface.Terminal &&
            !existing.terminalSessionId.isNullOrBlank()
        val updated = CardRunStore.update(
            recipe = recipe,
            status = status,
            instanceId = instanceId,
            surface = if (preserveTerminal) CardRunSurface.Terminal else CardRunSurface.Web,
            currentStepIndex = existing?.currentStepIndex,
            runId = existing?.runId,
            terminalSessionId = existing?.terminalSessionId,
            pid = existing?.pid,
            rootPid = existing?.rootPid,
            processGroupId = existing?.processGroupId,
            systemSessionId = existing?.systemSessionId,
            lastMeaningfulOutput = message,
            lastError = existing?.lastError,
            shellReportText = existing?.shellReportText,
            nextActionUrl = request.url.takeUnless { preserveTerminal },
            x11Display = existing?.x11Display,
            x11SocketPath = existing?.x11SocketPath
        )
        return BrowserHandoffTargetUpdate(recipe, updated)
    }

    override fun prepareCallback(session: BrowserAuthSession): BrowserHandoffCallbackPreparation? {
        if (session.kind != BrowserAuthSessionKind.CliLoopback) return null
        val preparation = loopbackBridge.prepare(session)
        return BrowserHandoffCallbackPreparation(
            mode = preparation.mode.name,
            port = preparation.port
        )
    }

    override fun openExternal(url: String): Boolean = openExternal.invoke(url)

    override fun recordOpened(
        session: BrowserAuthSession,
        request: BrowserHandoffRequest,
        preparation: BrowserHandoffCallbackPreparation?
    ) {
        val recipe = request.recipeId?.takeIf { it.isNotBlank() }?.let(recipeResolver)
            ?: request.recipeId?.let(CardRunStore::registeredRecipe)
        diagnostics.logRecipeEvent(
            "browser_auth_handoff_opened",
            recipe,
            mapOf(
                "instanceId" to request.instanceId.orEmpty(),
                "sessionId" to session.sessionId,
                "kind" to session.kind.name,
                "source" to request.source.orEmpty(),
                "url" to BrowserHandoffPolicy.redactedUrlForDiagnostics(request.url),
                "callbackChannel" to (preparation?.mode ?: "app_redirect"),
                "callbackPort" to preparation?.port?.toString().orEmpty()
            )
        )
    }

    override fun fail(session: BrowserAuthSession, reason: String) {
        loopbackBridge.stop(session.sessionId)
        sessions.markFailed(session.sessionId, reason)
        val recipe = session.recipeId?.takeIf { it.isNotBlank() }?.let(recipeResolver)
            ?: session.recipeId?.let(CardRunStore::registeredRecipe)
        diagnostics.logRecipeEvent(
            "browser_auth_handoff_open_failed",
            recipe,
            mapOf(
                "instanceId" to session.instanceId.orEmpty(),
                "sessionId" to session.sessionId,
                "kind" to session.kind.name,
                "source" to session.source.orEmpty(),
                "url" to session.originalUrl,
                "reason" to reason
            )
        )
    }
}
