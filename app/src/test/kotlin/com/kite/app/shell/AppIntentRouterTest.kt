package com.kite.app.shell

import android.content.Intent
import android.net.Uri
import com.kite.app.CardRunIntents
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AppIntentRouterTest {
    @Test
    fun `认证回跳优先于同一个 Intent 中的其他入口`() {
        val intent = Intent()
            .setData(Uri.parse("kite-auth://callback?code=ok&state=state-1"))
            .putExtra(AppIntentRouter.EXTRA_RUNTIME_ACTION, "dump_diagnostics")
            .putExtra(CardRunIntents.EXTRA_RECIPE_ID, "card-1")

        assertEquals(
            AppIntentRequest.BrowserAuthRedirect("kite-auth://callback?code=ok&state=state-1"),
            AppIntentRouter.classify(intent)
        )
    }

    @Test
    fun `自动化动作优先于运行窗口入口`() {
        val intent = Intent()
            .putExtra(AppIntentRouter.EXTRA_RUNTIME_ACTION, "  dump_diagnostics  ")
            .putExtra(CardRunIntents.EXTRA_RECIPE_ID, "card-1")

        assertEquals(
            AppIntentRequest.RuntimeAutomation("dump_diagnostics"),
            AppIntentRouter.classify(intent)
        )
    }

    @Test
    fun `卡片实例入口保留目标配方`() {
        val intent = Intent().putExtra(CardRunIntents.EXTRA_RECIPE_ID, " card-1 ")

        assertEquals(AppIntentRequest.CardRun("card-1"), AppIntentRouter.classify(intent))
    }

    @Test
    fun `普通启动不制造应用动作`() {
        assertEquals(AppIntentRequest.None, AppIntentRouter.classify(Intent()))
        assertEquals(AppIntentRequest.None, AppIntentRouter.classify(null))
    }

    @Test
    fun `分发只调用分类命中的处理器`() {
        val calls = mutableListOf<String>()
        val handled = AppIntentRouter.dispatch(
            Intent().putExtra(AppIntentRouter.EXTRA_RUNTIME_ACTION, "dump_diagnostics"),
            onBrowserAuthRedirect = { calls += "browser"; true },
            onRuntimeAutomation = { calls += "automation"; true },
            onCardRun = { calls += "card"; true }
        )

        assertEquals(true, handled)
        assertEquals(listOf("automation"), calls)
    }
}
