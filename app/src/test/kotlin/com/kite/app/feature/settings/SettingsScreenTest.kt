package com.kite.app.feature.settings

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.kite.app.browser.BrowserRuntimeMode
import com.kite.app.theme.KiteTheme
import com.kite.app.theme.ThemeConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SettingsScreenTest {
    @Test
    fun `ordinary state projection updates bindings without rebuilding page`() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        var switchCallbacks = 0
        val screen = SettingsScreen(
            context = activity,
            initialState = state(),
            onBack = {},
            onOpenTheme = {},
            onSelectBrowserMode = {},
            onRestoreLastScreen = { switchCallbacks += 1 },
            onHideMainTask = { switchCallbacks += 1 },
            onNotificationState = { switchCallbacks += 1 },
            onOpenDropZone = {}
        )
        activity.setContentView(screen.root)
        val firstChild = (screen.root as ViewGroup).getChildAt(0)

        screen.render(state().copy(
            browserRuntimeMode = BrowserRuntimeMode.AutomationBrowser,
            notificationsEnabled = true,
            notificationSubtitle = "通知已校准",
            dropZoneMessage = "共享区可用",
            revision = 2L
        ))

        assertSame(firstChild, (screen.root as ViewGroup).getChildAt(0))
        assertEquals(0, switchCallbacks)
        val texts = screen.root.allTexts()
        assertTrue(texts.contains(BrowserRuntimeMode.AutomationBrowser.title))
        assertTrue(texts.contains("通知已校准"))
        assertTrue(texts.contains("共享区可用"))
    }

    @Test
    fun `theme screen only rebuilds when theme identity changes`() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val screen = ThemeSettingsScreen(activity, {}, {}, {})
        val initial = state()

        screen.render(initial)
        val firstPage = screen.root.getChildAt(0)
        screen.render(initial.copy(revision = 2L))
        val samePage = screen.root.getChildAt(0)
        screen.render(initial.copy(
            theme = ThemeConfig(0x123456, initial.theme.backgroundColor),
            revision = 3L
        ))

        assertSame(firstPage, samePage)
        assertNotSame(firstPage, screen.root.getChildAt(0))
    }

    private fun state() = SettingsUiState(
        theme = ThemeConfig(KiteTheme.defaultThemeColor, KiteTheme.defaultBackgroundColor),
        browserRuntimeMode = BrowserRuntimeMode.Default,
        restoreLastScreen = true,
        hideMainTaskFromRecents = false,
        notificationsEnabled = false,
        notificationSubtitle = "通知未开启",
        dropZoneAvailable = false,
        dropZoneMessage = "投放区尚未检查",
        revision = 1L
    )

    private fun View.allTexts(): List<String> = buildList {
        if (this@allTexts is TextView) add(text.toString())
        if (this@allTexts is ViewGroup) {
            repeat(childCount) { index -> addAll(getChildAt(index).allTexts()) }
        }
    }
}
