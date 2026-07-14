package com.kite.app.feature.runsurface

import android.app.Activity
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.kite.app.recipe.KiteExecution
import com.kite.app.recipe.KiteRecipe
import com.kite.app.recipe.KiteRecipeStep
import com.kite.app.run.CardRunState
import com.kite.app.run.CardRunStatus
import com.kite.app.run.CardRunSurface
import com.kite.app.theme.KiteTheme
import com.kite.app.theme.ThemeConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
class RunActivityChromeTest {
    @Test
    fun `侧边把手展开成熟控制模型并能进入真实窗口总览`() {
        val fixture = fixture()
        fixture.chrome.render(
            RunSurfaceProjector.project(
                recipe(KiteRecipeStep(id = "terminal", type = KiteRecipe.STEP_TERMINAL, cmd = "bash")),
                state(
                    surface = CardRunSurface.Terminal,
                    status = CardRunStatus.WaitingTerminal,
                    terminalSessionId = "terminal-1"
                )
            )
        )

        val handle = fixture.chrome.root.findByDescription("运行窗口控制")
        assertNotNull(handle)
        handle!!.performClick()

        assertTrue(fixture.chrome.expandedForTesting())
        assertNotNull(fixture.chrome.root.findByText("完成并继续"))

        fixture.chrome.root.findByDescription("实例窗口")!!.performClick()

        assertTrue(fixture.chrome.overviewVisibleForTesting())
        assertFalse(fixture.chrome.expandedForTesting())
    }

    @Test
    fun `网页控制条同步前进后退加载和地址状态`() {
        val fixture = fixture()
        fixture.chrome.render(
            RunSurfaceProjector.project(
                recipe(KiteRecipeStep(id = "web", type = KiteRecipe.STEP_OPEN_WEB, url = "https://example.com")),
                state(
                    surface = CardRunSurface.Web,
                    status = CardRunStatus.Opened,
                    nextActionUrl = "https://example.com/start"
                )
            )
        )

        fixture.chrome.updateWebNavigation(
            RunWebNavigationUiState(
                url = "https://example.com/next",
                canGoBack = true,
                canGoForward = false,
                loading = true,
                progress = 42
            )
        )

        assertEquals("https://example.com/next", fixture.chrome.webAddressForTesting().text.toString())
        assertTrue(fixture.chrome.root.findByDescription("后退")!!.isEnabled)
        assertFalse(fixture.chrome.root.findByDescription("前进")!!.isEnabled)
        assertNotNull(fixture.chrome.root.findByDescription("停止加载"))
    }

    @Test
    fun `完成动作未获状态确认时明确进入可重试状态`() {
        val fixture = fixture()
        fixture.chrome.render(
            RunSurfaceProjector.project(
                recipe(KiteRecipeStep(id = "terminal", type = KiteRecipe.STEP_TERMINAL, cmd = "bash")),
                state(
                    surface = CardRunSurface.Terminal,
                    status = CardRunStatus.WaitingTerminal,
                    terminalSessionId = "terminal-1"
                )
            )
        )

        fixture.chrome.root.findByText("完成并继续")!!.performClick()
        assertNotNull(fixture.chrome.root.findByText("处理中"))

        shadowOf(Looper.getMainLooper()).idleFor(1801L, TimeUnit.MILLISECONDS)

        assertNotNull(fixture.chrome.root.findByText("请重试"))
    }

    private fun fixture(): Fixture {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val host = FrameLayout(activity)
        activity.setContentView(host)
        val chrome = RunActivityChrome(
            context = activity,
            tokens = KiteTheme.resolve(
                ThemeConfig(KiteTheme.defaultThemeColor, KiteTheme.defaultBackgroundColor)
            ),
            actions = RunActivityChromeActions(
                onComplete = {},
                onStop = {},
                onCloseWindow = {},
                onSelectSurface = {},
                onOpenWeb = {},
                onWebBack = {},
                onWebForward = {},
                onWebReload = {},
                onWebStopLoading = {},
                onSubmitWebUrl = {}
            )
        )
        host.addView(chrome.root)
        return Fixture(chrome)
    }

    private fun recipe(step: KiteRecipeStep): KiteRecipe = KiteRecipe(
        id = "recipe-1",
        name = "测试运行",
        description = "",
        type = KiteRecipe.TYPE_START_SERVICE,
        defaultUrl = "",
        shortcut = false,
        execution = KiteExecution.steps(listOf(step))
    )

    private fun state(
        surface: CardRunSurface,
        status: CardRunStatus,
        terminalSessionId: String? = null,
        nextActionUrl: String? = null
    ): CardRunState = CardRunState(
        instanceId = "instance-1",
        recipeId = "recipe-1",
        recipeName = "测试运行",
        status = status,
        surface = surface,
        currentStepIndex = 0,
        stepCount = 1,
        terminalSessionId = terminalSessionId,
        nextActionUrl = nextActionUrl,
        createdAt = 1L,
        updatedAt = 1L
    )

    private fun View.findByDescription(value: String): View? {
        if (contentDescription?.toString() == value) return this
        if (this !is ViewGroup) return null
        repeat(childCount) { index ->
            getChildAt(index).findByDescription(value)?.let { return it }
        }
        return null
    }

    private fun View.findByText(value: String): View? {
        if (this is android.widget.TextView && text?.toString() == value) return this
        if (this !is ViewGroup) return null
        repeat(childCount) { index ->
            getChildAt(index).findByText(value)?.let { return it }
        }
        return null
    }

    private data class Fixture(val chrome: RunActivityChrome)
}
