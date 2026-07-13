package com.kite.app.feature.recipeeditor

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.kite.app.theme.KiteTheme
import com.kite.app.theme.ThemeConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RecipeRawJsonScreenTest {
    @Test
    fun `screen replaces loading with raw json without activity host`() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val screen = screen(activity)
        activity.setContentView(screen.root)

        screen.renderLoading()
        assertTrue(screen.root.texts().contains("正在读取配方…"))
        screen.renderJson("{\"id\":\"demo\"}")

        assertTrue(screen.root.texts().contains("{\"id\":\"demo\"}"))
        assertTrue(screen.root.texts().none { it.contains("正在读取") })
    }

    @Test
    fun `close result and error remain data contracts`() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        var backCount = 0
        val screen = screen(activity) { backCount += 1 }
        activity.setContentView(screen.root)
        screen.root.findByText("‹")?.performClick()
        screen.renderError("missing")

        assertEquals(1, backCount)
        assertTrue(screen.root.texts().single { it.contains("无法加载配方") }.contains("missing"))
        assertEquals(
            RecipeEditorRequest.CloseRawJson,
            RecipeEditorResultContract.parse(Bundle().apply { putString("kind", "close_raw_json") })
        )
    }

    private fun screen(activity: Activity, onBack: () -> Unit = {}) = RecipeRawJsonScreen(
        context = activity,
        tokens = KiteTheme.resolve(
            ThemeConfig(KiteTheme.defaultThemeColor, KiteTheme.defaultBackgroundColor)
        ),
        onBack = onBack
    )

    private fun View.texts(): List<String> = buildList {
        if (this@texts is TextView) add(text.toString())
        if (this@texts is ViewGroup) repeat(childCount) { addAll(getChildAt(it).texts()) }
    }

    private fun View.findByText(value: String): View? {
        if (this is TextView && text.toString() == value) return this
        if (this is ViewGroup) repeat(childCount) { index ->
            getChildAt(index).findByText(value)?.let { return it }
        }
        return null
    }
}
