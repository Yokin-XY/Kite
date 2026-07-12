package com.kite.app.shell

import android.content.Intent
import com.kite.app.CardRunIntents
import com.kite.app.browser.BrowserAuthRedirectParser

internal sealed interface AppIntentRequest {
    data object None : AppIntentRequest
    data class BrowserAuthRedirect(val rawUrl: String) : AppIntentRequest
    data class RuntimeAutomation(val action: String) : AppIntentRequest
    data class CardRun(val recipeId: String) : AppIntentRequest
}

/** 只分类应用入口，不执行认证、自动化或运行实例业务。 */
internal object AppIntentRouter {
    const val EXTRA_RUNTIME_ACTION = "runtime_action"

    fun classify(intent: Intent?): AppIntentRequest {
        val source = intent ?: return AppIntentRequest.None
        val redirectUrl = source.dataString?.takeIf { BrowserAuthRedirectParser.parse(it) != null }
        if (redirectUrl != null) return AppIntentRequest.BrowserAuthRedirect(redirectUrl)

        val runtimeAction = source.getStringExtra(EXTRA_RUNTIME_ACTION)?.trim().orEmpty()
        if (runtimeAction.isNotBlank()) return AppIntentRequest.RuntimeAutomation(runtimeAction)

        val recipeId = source.getStringExtra(CardRunIntents.EXTRA_RECIPE_ID)?.trim().orEmpty()
        if (recipeId.isNotBlank()) return AppIntentRequest.CardRun(recipeId)

        return AppIntentRequest.None
    }

    fun dispatch(
        intent: Intent?,
        onBrowserAuthRedirect: (Intent?) -> Boolean,
        onRuntimeAutomation: (Intent?) -> Boolean,
        onCardRun: (Intent?) -> Boolean
    ): Boolean = when (classify(intent)) {
        AppIntentRequest.None -> false
        is AppIntentRequest.BrowserAuthRedirect -> onBrowserAuthRedirect(intent)
        is AppIntentRequest.RuntimeAutomation -> onRuntimeAutomation(intent)
        is AppIntentRequest.CardRun -> onCardRun(intent)
    }
}
