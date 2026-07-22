package com.kite.app.ui

import android.app.Activity
import android.view.ViewGroup
import android.widget.TextView
import com.kite.app.theme.KiteTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class UiKitTest {
    @Test
    fun `标准顶栏和文字角色来自同一主题环境`() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val environment = KiteTheme.resolve(KiteTheme.defaultSelection, systemDark = false)
        val ui = UiKit(activity, environment)
        val topBar = ui.topBar(activity, "运行管理", onBack = {}) as ViewGroup
        val title = topBar.getChildAt(1) as TextView

        assertEquals(environment.foundations.typography.pageTitle, title.textSize / activity.resources.displayMetrics.scaledDensity)
        assertEquals(
            environment.foundations.minimumTouchTarget.toFloat(),
            ui.dp(environment.foundations.minimumTouchTarget) / activity.resources.displayMetrics.density,
            0.01f,
        )
        assertTrue(topBar.getChildAt(0).contentDescription.isNotBlank())
    }

    @Test
    fun `辅助文字角色不依赖文字内容猜测样式`() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val environment = KiteTheme.resolve(KiteTheme.defaultSelection, systemDark = false)
        val ui = UiKit(activity, environment)
        val first = ui.applyTextRole(TextView(activity).apply { text = "保存" }, UiTextRole.Supporting)
        val second = ui.applyTextRole(TextView(activity).apply { text = "+" }, UiTextRole.Supporting)

        assertEquals(first.textSize, second.textSize, 0f)
        assertEquals(first.currentTextColor, second.currentTextColor)
    }
}
