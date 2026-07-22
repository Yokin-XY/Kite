package com.kite.app.ui.terminal

import android.app.Activity
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import com.kite.app.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import kotlin.math.roundToInt

@RunWith(RobolectricTestRunner::class)
class TerminalLayoutAccessibilityTest {
    @Test
    fun `终端列表核心图标动作具备标准触控面积和语义`() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val root = activity.inflate(R.layout.fragment_terminal)

        root.findViewById<View>(R.id.btnListAdd).assertAction(
            expectedSizeDp = 48,
            expectedDescription = activity.getString(R.string.terminal_new_session),
        )
        root.findViewById<View>(R.id.btnBackToSessions).assertAction(
            expectedSizeDp = 48,
            expectedDescription = activity.getString(R.string.common_back),
        )
        assertFalse(activity.getString(R.string.terminal_empty_sessions).contains("+"))
    }

    @Test
    fun `独立终端详情返回动作具备标准触控面积和语义`() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val root = activity.inflate(R.layout.fragment_terminal_detail)

        root.findViewById<View>(R.id.btnBackToSessions).assertAction(
            expectedSizeDp = 48,
            expectedDescription = activity.getString(R.string.common_back),
        )
    }

    private fun Activity.inflate(layoutRes: Int): View =
        LayoutInflater.from(this).inflate(layoutRes, FrameLayout(this), false)

    private fun View.assertAction(expectedSizeDp: Int, expectedDescription: String) {
        val expectedSizePx = (expectedSizeDp * resources.displayMetrics.density).roundToInt()
        assertEquals(expectedSizePx, layoutParams.width)
        assertEquals(expectedSizePx, layoutParams.height)
        assertEquals(expectedDescription, contentDescription.toString())
    }
}
