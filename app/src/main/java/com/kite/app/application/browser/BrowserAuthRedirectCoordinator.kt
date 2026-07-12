package com.kite.app.application.browser

import com.kite.app.browser.BrowserAuthRedirect
import com.kite.app.browser.BrowserAuthRedirectParser
import com.kite.app.browser.BrowserAuthSession
import com.kite.app.browser.BrowserAuthSessionStatus
import com.kite.app.recipe.KiteRecipe

internal data class BrowserAuthRedirectTarget(
    val recipe: KiteRecipe,
    val instanceId: String
)

internal sealed interface BrowserAuthRedirectResult {
    data object NotRedirect : BrowserAuthRedirectResult
    data object Unmatched : BrowserAuthRedirectResult
    data class MissingTarget(val sessionId: String) : BrowserAuthRedirectResult
    data class DeliveryFailed(val sessionId: String, val reason: String) : BrowserAuthRedirectResult
    data class Delivered(
        val recipeId: String,
        val instanceId: String,
        val failed: Boolean
    ) : BrowserAuthRedirectResult
}

internal data class BrowserAuthReconcileSummary(
    val expiredCallbacksStopped: Int,
    val forwardedRunsSynchronized: Int,
    val expiredRunsSynchronized: Int
)

internal interface BrowserAuthRedirectGateway {
    fun matchReturned(redirect: BrowserAuthRedirect): BrowserAuthSession?
    fun resolveTarget(session: BrowserAuthSession): BrowserAuthRedirectTarget?
    fun projectDelivery(
        target: BrowserAuthRedirectTarget,
        session: BrowserAuthSession,
        redirect: BrowserAuthRedirect,
        failed: Boolean
    ): Boolean
    fun markDelivered(sessionId: String)
    fun markFailed(sessionId: String, reason: String)
    fun recordUnmatched(redirect: BrowserAuthRedirect)
    fun recordMissingTarget(session: BrowserAuthSession)
    fun recordDelivered(
        target: BrowserAuthRedirectTarget,
        session: BrowserAuthSession,
        redirect: BrowserAuthRedirect,
        failed: Boolean
    )
    fun expirePending(): List<BrowserAuthSession>
    fun stopCallback(sessionId: String)
    fun forwardedNeedingRuntimeSync(): List<BrowserAuthSession>
    fun synchronizeForwarded(session: BrowserAuthSession): Boolean
    fun expiredNeedingRuntimeSync(): List<BrowserAuthSession>
    fun synchronizeExpired(session: BrowserAuthSession): Boolean
    fun markRuntimeNotified(sessionId: String)
}

/** 认证回跳的唯一状态机；只编排 session 和目标运行事实，不依赖 Activity/Intent/View。 */
internal class BrowserAuthRedirectCoordinator(
    private val gateway: BrowserAuthRedirectGateway
) {
    fun handle(rawUrl: String?): BrowserAuthRedirectResult {
        val redirect = rawUrl
            ?.takeIf(String::isNotBlank)
            ?.let(BrowserAuthRedirectParser::parse)
            ?: return BrowserAuthRedirectResult.NotRedirect
        val session = gateway.matchReturned(redirect)
        if (session == null) {
            gateway.recordUnmatched(redirect)
            return BrowserAuthRedirectResult.Unmatched
        }
        val target = gateway.resolveTarget(session)
        if (target == null) {
            gateway.markFailed(session.sessionId, "missing_target")
            gateway.recordMissingTarget(session)
            return BrowserAuthRedirectResult.MissingTarget(session.sessionId)
        }
        val failed = session.status == BrowserAuthSessionStatus.Failed || !redirect.error.isNullOrBlank()
        if (!gateway.projectDelivery(target, session, redirect, failed)) {
            gateway.markFailed(session.sessionId, "target_projection_failed")
            return BrowserAuthRedirectResult.DeliveryFailed(session.sessionId, "target_projection_failed")
        }
        if (failed) {
            gateway.markFailed(
                session.sessionId,
                redirect.error ?: session.failureReason ?: "redirect_failed"
            )
        } else {
            gateway.markDelivered(session.sessionId)
        }
        gateway.recordDelivered(target, session, redirect, failed)
        return BrowserAuthRedirectResult.Delivered(
            recipeId = target.recipe.id,
            instanceId = target.instanceId,
            failed = failed
        )
    }

    fun reconcile(): BrowserAuthReconcileSummary {
        val expired = gateway.expirePending()
        expired.forEach { gateway.stopCallback(it.sessionId) }
        val forwardedCount = gateway.forwardedNeedingRuntimeSync().count { session ->
            gateway.synchronizeForwarded(session).also { synchronized ->
                if (synchronized) gateway.markRuntimeNotified(session.sessionId)
            }
        }
        val expiredCount = gateway.expiredNeedingRuntimeSync().count { session ->
            gateway.synchronizeExpired(session).also { synchronized ->
                if (synchronized) gateway.markRuntimeNotified(session.sessionId)
            }
        }
        return BrowserAuthReconcileSummary(
            expiredCallbacksStopped = expired.size,
            forwardedRunsSynchronized = forwardedCount,
            expiredRunsSynchronized = expiredCount
        )
    }
}
