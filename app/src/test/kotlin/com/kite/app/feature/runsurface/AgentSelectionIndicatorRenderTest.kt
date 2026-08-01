package com.kite.app.feature.runsurface

import android.app.Activity
import android.widget.ImageView
import com.kite.app.theme.KiteTheme
import com.kite.app.ui.UiKit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AgentSelectionIndicatorRenderTest {
    @Test
    fun `选中状态为外圈提供独立的实心内圆前景`() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val environment = KiteTheme.resolve(KiteTheme.defaultSelection, systemDark = false)
        val ui = UiKit(activity, environment)
        val palette = AgentSelectionVisualPolicy.palette(isDark = false)
        val indicator = ImageView(activity)

        renderArchivedSelectionIndicator(
            indicator = indicator,
            ui = ui,
            palette = palette,
            state = AgentArchivedProjectSelectionState.Checked,
        )

        assertNotNull(indicator.background)
        assertNotNull(indicator.drawable)
        assertEquals(
            ui.dp(
                (AgentSelectionVisualPolicy.TOUCH_TARGET_DP -
                    AgentSelectionVisualPolicy.CHECKED_DOT_SIZE_DP) / 2,
            ),
            indicator.paddingLeft,
        )
    }

    @Test
    fun `未选中状态仅保留空心外圈`() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val environment = KiteTheme.resolve(KiteTheme.defaultSelection, systemDark = false)
        val ui = UiKit(activity, environment)
        val palette = AgentSelectionVisualPolicy.palette(isDark = false)
        val indicator = ImageView(activity)

        renderArchivedSelectionIndicator(
            indicator = indicator,
            ui = ui,
            palette = palette,
            state = AgentArchivedProjectSelectionState.Unchecked,
        )

        assertNotNull(indicator.background)
        assertNull(indicator.drawable)
        assertEquals(0, indicator.paddingLeft)
    }
}
