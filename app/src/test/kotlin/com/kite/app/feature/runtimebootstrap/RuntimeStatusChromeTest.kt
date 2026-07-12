package com.kite.app.feature.runtimebootstrap

import android.app.Activity
import android.view.View
import android.widget.FrameLayout
import com.kite.app.theme.KiteTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RuntimeStatusChromeTest {
    @Test
    fun `gate reuses one binding and follows suppression without rebuilding host`() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val rootHost = FrameLayout(activity)
        activity.setContentView(rootHost)
        val chrome = chrome(activity, rootHost)
        val blocked = RuntimeStatusUiState(
            title = "正在部署",
            detail = "等待",
            blocksUbuntuActions = true,
            isProblem = false
        )

        chrome.render(blocked, suppressTransient = false)
        val gate = chrome.gateRootForTesting()
        val childCount = rootHost.childCount
        chrome.render(blocked.copy(detail = "继续等待"), suppressTransient = false)

        assertSame(gate, chrome.gateRootForTesting())
        assertEquals(childCount, rootHost.childCount)
        assertEquals(View.VISIBLE, gate?.visibility)

        chrome.render(blocked, suppressTransient = true)
        assertEquals(View.GONE, gate?.visibility)
    }

    @Test
    fun `open panel binds updated counts without recreating dialog`() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val rootHost = FrameLayout(activity)
        activity.setContentView(rootHost)
        val chrome = chrome(activity, rootHost)
        val ready = RuntimeStatusUiState(
            title = "",
            detail = "",
            blocksUbuntuActions = false,
            isProblem = false,
            visible = false,
            counts = RuntimeStatusCounts(1, 2, 3)
        )
        chrome.render(ready, suppressTransient = false)
        chrome.showPanel(auto = false)
        val dialog = chrome.dialogForTesting()

        chrome.render(ready.copy(counts = RuntimeStatusCounts(4, 5, 6)), suppressTransient = false)

        assertNotNull(dialog)
        assertSame(dialog, chrome.dialogForTesting())
        assertEquals(RuntimeStatusCounts(4, 5, 6), chrome.panelCountsForTesting())
    }

    private fun chrome(activity: Activity, rootHost: FrameLayout) = RuntimeStatusChrome(
        activity = activity,
        rootHost = rootHost,
        tokens = KiteTheme.resolve(
            com.kite.app.theme.ThemeConfig(
                KiteTheme.defaultThemeColor,
                KiteTheme.defaultBackgroundColor
            )
        ),
        onRefresh = {},
        onPrimaryAction = {}
    )
}
