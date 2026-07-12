package com.kite.app.platform.browser

import com.kite.app.application.browser.BrowserAuthRedirectGateway
import com.kite.app.application.browser.BrowserAuthRedirectTarget
import com.kite.app.browser.BrowserAuthRedirect
import com.kite.app.browser.BrowserAuthSession
import com.kite.app.browser.BrowserAuthSessionKind
import com.kite.app.browser.BrowserAuthSessionStore
import com.kite.app.browser.BrowserLoopbackCallbackBridge
import com.kite.app.diagnostics.KiteDiagnostics
import com.kite.app.recipe.KiteRecipe
import com.kite.app.run.CardRunStatus
import com.kite.app.run.CardRunStore
import com.kite.app.run.CardRunSurface

/** 把认证回跳协调器接到持久化 session、CardRunStore、loopback 与诊断。 */
internal class AndroidBrowserAuthRedirectGateway(
    private val sessions: BrowserAuthSessionStore,
    private val loopbackBridge: BrowserLoopbackCallbackBridge,
    private val diagnostics: KiteDiagnostics,
    private val recipeResolver: (String) -> KiteRecipe?
) : BrowserAuthRedirectGateway {
    override fun matchReturned(redirect: BrowserAuthRedirect): BrowserAuthSession? =
        sessions.markReturned(redirect)

    override fun resolveTarget(session: BrowserAuthSession): BrowserAuthRedirectTarget? {
        val instanceId = session.instanceId?.takeIf(String::isNotBlank) ?: return null
        val existing = CardRunStore.get(instanceId)
        val recipe = session.recipeId
            ?.takeIf(String::isNotBlank)
            ?.let(::resolveRecipe)
            ?: existing?.recipeId?.let(::resolveRecipe)
            ?: return null
        return BrowserAuthRedirectTarget(recipe, instanceId)
    }

    override fun projectDelivery(
        target: BrowserAuthRedirectTarget,
        session: BrowserAuthSession,
        redirect: BrowserAuthRedirect,
        failed: Boolean
    ): Boolean = runCatching {
        val summary = if (failed) {
            "浏览器登录返回失败：${redirect.error ?: session.failureReason ?: "unknown"}"
        } else {
            "浏览器登录已返回，等待发起方确认登录状态"
        }
        val report = buildString {
            appendLine(summary)
            appendLine("sessionId=${session.sessionId}")
            appendLine("kind=${session.kind.name}")
            appendLine("state=${if (redirect.state.isNullOrBlank()) "missing" else "matched"}")
            appendLine("code=${if (redirect.code.isNullOrBlank()) "missing" else "present"}")
            if (!redirect.error.isNullOrBlank()) appendLine("error=${redirect.error}")
        }.trim()
        CardRunStore.update(
            recipe = target.recipe,
            status = if (failed) CardRunStatus.Failed else CardRunStatus.Opened,
            instanceId = target.instanceId,
            surface = CardRunSurface.Report,
            lastMeaningfulOutput = summary,
            lastError = if (failed) summary else null,
            shellReportText = report,
            clearNextActionUrl = true
        )
    }.isSuccess

    override fun markDelivered(sessionId: String) {
        sessions.markDelivered(sessionId)
    }

    override fun markFailed(sessionId: String, reason: String) {
        sessions.markFailed(sessionId, reason)
    }

    override fun recordUnmatched(redirect: BrowserAuthRedirect) {
        diagnostics.logRecipeEvent(
            "browser_auth_redirect_unmatched",
            null,
            mapOf("hasState" to (!redirect.state.isNullOrBlank()).toString())
        )
    }

    override fun recordMissingTarget(session: BrowserAuthSession) {
        diagnostics.logRecipeEvent(
            "browser_auth_redirect_missing_target",
            session.recipeId?.let(::resolveRecipe),
            mapOf(
                "sessionId" to session.sessionId,
                "recipeId" to session.recipeId.orEmpty(),
                "instanceId" to session.instanceId.orEmpty()
            )
        )
    }

    override fun recordDelivered(
        target: BrowserAuthRedirectTarget,
        session: BrowserAuthSession,
        redirect: BrowserAuthRedirect,
        failed: Boolean
    ) {
        diagnostics.logRecipeAction(
            target.recipe,
            "browser_auth_redirect_delivered",
            mapOf(
                "instanceId" to target.instanceId,
                "sessionId" to session.sessionId,
                "kind" to session.kind.name,
                "failed" to failed.toString(),
                "hasCode" to (!redirect.code.isNullOrBlank()).toString(),
                "hasError" to (!redirect.error.isNullOrBlank()).toString()
            )
        )
    }

    override fun expirePending(): List<BrowserAuthSession> = sessions.expirePending()

    override fun stopCallback(sessionId: String) {
        loopbackBridge.stop(sessionId)
    }

    override fun forwardedNeedingRuntimeSync(): List<BrowserAuthSession> =
        sessions.forwardedLoopbackNeedingRuntimeSync()

    override fun synchronizeForwarded(session: BrowserAuthSession): Boolean {
        val instanceId = session.instanceId?.takeIf(String::isNotBlank) ?: return true
        val existing = CardRunStore.get(instanceId) ?: return true
        val recipe = session.recipeId?.takeIf(String::isNotBlank)?.let(::resolveRecipe)
            ?: resolveRecipe(existing.recipeId)
            ?: return false
        CardRunStore.update(
            recipe = recipe,
            status = existing.status,
            instanceId = instanceId,
            surface = existing.surface,
            currentStepIndex = existing.currentStepIndex,
            runId = existing.runId,
            terminalSessionId = existing.terminalSessionId,
            pid = existing.pid,
            rootPid = existing.rootPid,
            processGroupId = existing.processGroupId,
            systemSessionId = existing.systemSessionId,
            lastMeaningfulOutput = "浏览器回调已交给登录发起方，正在由发起方确认登录结果",
            lastError = existing.lastError,
            shellReportText = existing.shellReportText,
            nextActionUrl = existing.nextActionUrl
        )
        diagnostics.logRecipeEvent(
            "browser_loopback_callback_forwarded",
            recipe,
            mapOf(
                "instanceId" to instanceId,
                "sessionId" to session.sessionId,
                "channel" to session.callbackChannelStatus.name
            )
        )
        return true
    }

    override fun expiredNeedingRuntimeSync(): List<BrowserAuthSession> =
        sessions.expiredNeedingRuntimeSync()

    override fun synchronizeExpired(session: BrowserAuthSession): Boolean {
        val instanceId = session.instanceId?.takeIf(String::isNotBlank)
        if (instanceId == null) {
            diagnostics.logRecipeEvent(
                "browser_auth_session_expired_missing_instance",
                session.recipeId?.let(::resolveRecipe),
                mapOf(
                    "sessionId" to session.sessionId,
                    "kind" to session.kind.name,
                    "recipeId" to session.recipeId.orEmpty()
                )
            )
            return true
        }
        val existing = CardRunStore.get(instanceId)
        if (existing == null) {
            diagnostics.logRecipeEvent(
                "browser_auth_session_expired_no_active_run",
                session.recipeId?.let(::resolveRecipe),
                mapOf(
                    "instanceId" to instanceId,
                    "sessionId" to session.sessionId,
                    "kind" to session.kind.name,
                    "recipeId" to session.recipeId.orEmpty()
                )
            )
            return true
        }
        val recipe = session.recipeId?.takeIf(String::isNotBlank)?.let(::resolveRecipe)
            ?: resolveRecipe(existing.recipeId)
            ?: return false
        val preserveTerminal = session.kind == BrowserAuthSessionKind.CliLoopback &&
            existing.surface == CardRunSurface.Terminal &&
            !existing.terminalSessionId.isNullOrBlank()
        val summary = if (session.kind == BrowserAuthSessionKind.CliLoopback) {
            "未在等待时间内确认浏览器回调，登录结果请以发起方终端为准"
        } else {
            "浏览器登录等待超时，请重新打开登录页"
        }
        val report = buildString {
            appendLine(summary)
            appendLine("sessionId=${session.sessionId}")
            appendLine("kind=${session.kind.name}")
            appendLine("reason=${session.failureReason ?: "expired"}")
            appendLine("callbackChannel=${session.callbackChannelStatus.name}")
            session.redirectUri?.takeIf(String::isNotBlank)?.let { appendLine("redirectUri=$it") }
        }.trim()
        CardRunStore.update(
            recipe = recipe,
            status = if (preserveTerminal) existing.status else CardRunStatus.Failed,
            instanceId = instanceId,
            surface = if (preserveTerminal) CardRunSurface.Terminal else CardRunSurface.Report,
            currentStepIndex = existing.currentStepIndex,
            runId = existing.runId,
            terminalSessionId = existing.terminalSessionId,
            pid = existing.pid,
            rootPid = existing.rootPid,
            processGroupId = existing.processGroupId,
            systemSessionId = existing.systemSessionId,
            lastMeaningfulOutput = summary,
            lastError = if (preserveTerminal) null else summary,
            shellReportText = if (preserveTerminal) existing.shellReportText else report,
            clearNextActionUrl = true
        )
        diagnostics.logRecipeEvent(
            "browser_auth_session_expired",
            recipe,
            mapOf(
                "instanceId" to instanceId,
                "sessionId" to session.sessionId,
                "kind" to session.kind.name,
                "preserveTerminalSurface" to preserveTerminal.toString()
            )
        )
        return true
    }

    override fun markRuntimeNotified(sessionId: String) {
        sessions.markRuntimeNotified(sessionId)
    }

    private fun resolveRecipe(recipeId: String): KiteRecipe? =
        recipeResolver(recipeId) ?: CardRunStore.registeredRecipe(recipeId)
}
