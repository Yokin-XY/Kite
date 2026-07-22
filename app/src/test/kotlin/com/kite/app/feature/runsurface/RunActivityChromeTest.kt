package com.kite.app.feature.runsurface

import android.app.Activity
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.FrameLayout
import com.kite.app.R
import com.kite.app.recipe.KiteExecution
import com.kite.app.recipe.KiteRecipe
import com.kite.app.recipe.KiteRecipeStep
import com.kite.app.run.CardRunState
import com.kite.app.run.CardRunStatus
import com.kite.app.run.CardRunSurface
import com.kite.app.theme.KiteTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowDialog
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
class RunActivityChromeTest {
    @Test
    fun `单击竖条只转交当前显示面的工具栏显隐`() {
        val fixture = fixture()
        fixture.renderTerminal()

        dispatchTap(fixture.chrome.handleForTesting(), SystemClock.uptimeMillis())

        assertEquals(listOf("toggle-toolbar"), fixture.actionLog)
        assertFalse(fixture.chrome.overviewVisibleForTesting())
        assertNull(fixture.chrome.root.findByText("完成并继续"))
    }

    @Test
    fun `双击竖条恢复第一次显隐并进入实例窗口`() {
        val fixture = fixture()
        fixture.renderTerminal()
        val firstTapAt = SystemClock.uptimeMillis()

        dispatchTap(fixture.chrome.handleForTesting(), firstTapAt)
        dispatchTap(fixture.chrome.handleForTesting(), firstTapAt + 100L)

        assertEquals(listOf("toggle-toolbar", "toggle-toolbar"), fixture.actionLog)
        assertTrue(fixture.chrome.overviewVisibleForTesting())
    }

    @Test
    fun `长按拖动竖条不触发工具栏或实例窗口`() {
        val fixture = fixture()
        fixture.renderTerminal()
        val handle = fixture.chrome.handleForTesting()
        val downAt = SystemClock.uptimeMillis()
        handle.dispatchTouchEvent(MotionEvent.obtain(downAt, downAt, MotionEvent.ACTION_DOWN, 10f, 10f, 0))

        shadowOf(Looper.getMainLooper()).idleFor(
            ViewConfiguration.getLongPressTimeout().toLong() + 1L,
            TimeUnit.MILLISECONDS
        )
        handle.dispatchTouchEvent(MotionEvent.obtain(downAt, downAt + 600L, MotionEvent.ACTION_MOVE, 10f, 260f, 0))
        handle.dispatchTouchEvent(MotionEvent.obtain(downAt, downAt + 620L, MotionEvent.ACTION_UP, 10f, 260f, 0))

        val params = handle.layoutParams as FrameLayout.LayoutParams
        assertEquals(Gravity.RIGHT or Gravity.TOP, params.gravity)
        assertTrue(params.topMargin > 0)
        assertTrue(fixture.actionLog.isEmpty())
        assertFalse(fixture.chrome.overviewVisibleForTesting())
    }

    @Test
    fun `实例窗口只保留关闭新建返回且新建提供终端网页`() {
        val fixture = fixture()
        fixture.renderTerminal()
        val firstTapAt = SystemClock.uptimeMillis()
        dispatchTap(fixture.chrome.handleForTesting(), firstTapAt)
        dispatchTap(fixture.chrome.handleForTesting(), firstTapAt + 100L)
        fixture.actionLog.clear()

        assertNotNull(fixture.chrome.root.findByDescription("关闭"))
        assertNotNull(fixture.chrome.root.findByDescription("新建"))
        assertNotNull(fixture.chrome.root.findByDescription("返回"))
        assertNull(fixture.chrome.root.findByDescription("完成步骤"))

        fixture.chrome.root.findByDescription("新建")!!.performClick()
        shadowOf(Looper.getMainLooper()).idleFor(200L, TimeUnit.MILLISECONDS)
        val bubble = ShadowDialog.getLatestDialog()
        assertNotNull(bubble.findViewById<View>(android.R.id.content).findByDescription("新建终端"))
        assertNotNull(bubble.findViewById<View>(android.R.id.content).findByDescription("新建网页"))
        bubble.findViewById<View>(android.R.id.content).findByDescription("新建终端")!!.performClick()
        shadowOf(Looper.getMainLooper()).idleFor(200L, TimeUnit.MILLISECONDS)
        assertEquals(listOf("open-terminal"), fixture.actionLog)
    }

    @Test
    fun `关闭实例使用主题确认弹层并只在确认后提交动作`() {
        val fixture = fixture()
        fixture.renderTerminal()
        val firstTapAt = SystemClock.uptimeMillis()
        dispatchTap(fixture.chrome.handleForTesting(), firstTapAt)
        dispatchTap(fixture.chrome.handleForTesting(), firstTapAt + 100L)
        fixture.actionLog.clear()

        fixture.chrome.root.findByDescription("关闭")!!.performClick()
        shadowOf(Looper.getMainLooper()).idleFor(200L, TimeUnit.MILLISECONDS)
        val dialog = ShadowDialog.getLatestDialog()
        val content = dialog.findViewById<View>(android.R.id.content)

        assertFalse(dialog is android.app.AlertDialog)
        assertNotNull(content.findByText(fixture.chrome.root.context.getString(R.string.run_window_close_instance_title)))
        assertTrue(fixture.actionLog.isEmpty())
        content.findByText(fixture.chrome.root.context.getString(R.string.common_close))!!.performClick()
        assertEquals(listOf("close-instance"), fixture.actionLog)
    }

    private fun fixture(): Fixture {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val host = FrameLayout(activity)
        activity.setContentView(host)
        val actionLog = mutableListOf<String>()
        val chrome = RunActivityChrome(
            context = activity,
            tokens = testTokens(),
            actions = RunActivityChromeActions(
                onCloseInstance = { actionLog += "close-instance" },
                onSelectWindow = { windowId, surface -> actionLog += "select-$windowId-${surface.name}" },
                onRestartWindow = { actionLog += "restart-$it" },
                onCloseWindow = { actionLog += "close-$it" },
                onOpenWeb = { actionLog += "open-web" },
                onOpenTerminal = { actionLog += "open-terminal" },
                onToggleSurfaceToolbar = { actionLog += "toggle-toolbar" }
            )
        )
        host.addView(
            chrome.root,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        host.measure(
            View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY)
        )
        host.layout(0, 0, 1080, 1920)
        shadowOf(Looper.getMainLooper()).idle()
        return Fixture(chrome, actionLog)
    }

    private fun Fixture.renderTerminal() {
        chrome.render(
            RunSurfaceProjector.project(
                recipe(KiteRecipeStep(id = "terminal", type = KiteRecipe.STEP_TERMINAL, cmd = "bash")),
                state(
                    surface = CardRunSurface.Terminal,
                    status = CardRunStatus.WaitingTerminal,
                    terminalSessionId = "terminal-1"
                )
            )
        )
    }

    private fun dispatchTap(view: View, downAt: Long) {
        val down = MotionEvent.obtain(downAt, downAt, MotionEvent.ACTION_DOWN, 10f, 10f, 0)
        val up = MotionEvent.obtain(downAt, downAt + 20L, MotionEvent.ACTION_UP, 10f, 10f, 0)
        view.dispatchTouchEvent(down)
        view.dispatchTouchEvent(up)
        down.recycle()
        up.recycle()
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
        terminalSessionId: String? = null
    ): CardRunState = CardRunState(
        instanceId = "instance-1",
        recipeId = "recipe-1",
        recipeName = "测试运行",
        status = status,
        surface = surface,
        currentStepIndex = 0,
        stepCount = 1,
        terminalSessionId = terminalSessionId,
        createdAt = 1L,
        updatedAt = 1L
    )

    private data class Fixture(
        val chrome: RunActivityChrome,
        val actionLog: MutableList<String>
    )
}

@RunWith(RobolectricTestRunner::class)
class RunWebToolbarTest {
    @Test
    fun `网页工具栏独立同步导航状态并提交网址`() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val actionLog = mutableListOf<String>()
        val toolbar = RunWebToolbar(
            activity = activity,
            tokens = testTokens(),
            actions = RunWebToolbarActions(
                onBack = { actionLog += "back" },
                onForward = { actionLog += "forward" },
                onReload = { actionLog += "reload" },
                onSubmitUrl = { actionLog += "url:$it" }
            )
        )

        toolbar.render(
            RunWebNavigationUiState(
                url = "https://example.com/next",
                canGoBack = true,
                canGoForward = false,
                loading = true,
                progress = 42
            )
        )

        assertEquals("https://example.com/next", toolbar.addressForTesting().text.toString())
        assertTrue(toolbar.root.findByDescription("后退")!!.isEnabled)
        assertFalse(toolbar.root.findByDescription("前进")!!.isEnabled)
        assertNotNull(toolbar.root.findByDescription("停止加载"))

        toolbar.addressForTesting().setText("https://kite.test")
        toolbar.root.findByDescription("打开网址")!!.performClick()
        toolbar.root.findByDescription("停止加载")!!.performClick()

        assertEquals(listOf("url:https://kite.test", "reload"), actionLog)
        assertTrue(toolbar.toggle())
        assertEquals(View.VISIBLE, toolbar.root.visibility)
        shadowOf(Looper.getMainLooper()).idleFor(200L, TimeUnit.MILLISECONDS)
        assertEquals(View.GONE, toolbar.root.visibility)
        assertTrue(toolbar.toggle())
        assertEquals(View.VISIBLE, toolbar.root.visibility)
        shadowOf(Looper.getMainLooper()).idleFor(200L, TimeUnit.MILLISECONDS)
        assertEquals(1f, toolbar.root.scaleX)
        toolbar.dispose()
    }
}

private fun testTokens() = KiteTheme.resolve(
    KiteTheme.defaultSelection,
    systemDark = false,
).tokens

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
