package com.kite.app.application.browser

import com.kite.app.browser.BrowserAuthSession
import com.kite.app.browser.BrowserHandoffDecision
import com.kite.app.browser.BrowserHandoffPolicy
import com.kite.app.browser.BrowserHandoffRequest
import com.kite.app.recipe.KiteRecipe
import com.kite.app.run.CardRunState

internal data class BrowserHandoffCallbackPreparation(
    val mode: String,
    val port: Int?
)

internal data class BrowserHandoffTargetUpdate(
    val recipe: KiteRecipe,
    val state: CardRunState
)

internal interface BrowserHandoffGateway {
    fun findPending(request: BrowserHandoffRequest): BrowserAuthSession?
    fun createPending(request: BrowserHandoffRequest, decision: BrowserHandoffDecision): BrowserAuthSession
    fun updateWaiting(session: BrowserAuthSession, request: BrowserHandoffRequest): BrowserHandoffTargetUpdate?
    fun prepareCallback(session: BrowserAuthSession): BrowserHandoffCallbackPreparation?
    fun openExternal(url: String): Boolean
    fun recordOpened(
        session: BrowserAuthSession,
        request: BrowserHandoffRequest,
        preparation: BrowserHandoffCallbackPreparation?
    )
    fun fail(session: BrowserAuthSession, reason: String)
}

internal sealed interface BrowserHandoffLaunchResult {
    val accepted: Boolean
    val targetUpdate: BrowserHandoffTargetUpdate?

    data class Reused(val session: BrowserAuthSession) : BrowserHandoffLaunchResult {
        override val accepted: Boolean = true
        override val targetUpdate: BrowserHandoffTargetUpdate? = null
    }

    data class Opened(
        val session: BrowserAuthSession,
        override val targetUpdate: BrowserHandoffTargetUpdate?
    ) : BrowserHandoffLaunchResult {
        override val accepted: Boolean = true
    }

    data class Failed(
        val session: BrowserAuthSession,
        val reason: String,
        override val targetUpdate: BrowserHandoffTargetUpdate?
    ) : BrowserHandoffLaunchResult {
        override val accepted: Boolean = false
    }

    data class Rejected(val reason: String) : BrowserHandoffLaunchResult {
        override val accepted: Boolean = false
        override val targetUpdate: BrowserHandoffTargetUpdate? = null
    }
}

/** 系统浏览器认证桥编排；不依赖 Activity、View、Custom Tabs 或 Android Intent。 */
internal class BrowserHandoffCoordinator(
    private val gateway: BrowserHandoffGateway
) {
    fun launch(
        request: BrowserHandoffRequest,
        decision: BrowserHandoffDecision,
        force: Boolean = false
    ): BrowserHandoffLaunchResult {
        if (!BrowserHandoffPolicy.isHandoff(decision)) {
            return BrowserHandoffLaunchResult.Rejected("not_handoff")
        }
        if (!force) {
            gateway.findPending(request)?.let { return BrowserHandoffLaunchResult.Reused(it) }
        }

        val session = gateway.createPending(request, decision)
        val targetUpdate = gateway.updateWaiting(session, request)
        val preparation = gateway.prepareCallback(session)
        if (!gateway.openExternal(request.url)) {
            gateway.fail(session, "external_browser_open_failed")
            return BrowserHandoffLaunchResult.Failed(
                session = session,
                reason = "external_browser_open_failed",
                targetUpdate = targetUpdate
            )
        }
        gateway.recordOpened(session, request, preparation)
        return BrowserHandoffLaunchResult.Opened(session, targetUpdate)
    }
}
