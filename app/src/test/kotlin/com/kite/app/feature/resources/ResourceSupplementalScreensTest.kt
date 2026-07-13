package com.kite.app.feature.resources

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.kite.app.action.KiteResourceActionIntent
import com.kite.app.application.resources.ResourceFeatureDescriptor
import com.kite.app.resources.KiteResourceUiProjection
import com.kite.app.run.CardRunHistoryEntry
import com.kite.app.run.CardRunStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ResourceSupplementalScreensTest {
    @Test
    fun `more screen keeps resource and history actions as data callbacks`() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val opened = mutableListOf<String>()
        val screen = ResourceMoreScreen(activity, {}, {}, opened::add)
        activity.setContentView(screen.root)
        val history = CardRunHistoryEntry(
            historyId = "history",
            recipeId = "install",
            recipeName = "Install",
            instanceId = "instance",
            status = CardRunStatus.Completed,
            startedAt = 1L,
            endedAt = 2L
        )

        screen.render(item(), listOf(history))
        val historyRow = screen.root.views().first { view ->
            view.isClickable && view.texts().any { it.contains("已完成") }
        }
        historyRow.performClick()

        assertEquals(listOf("history"), opened)
        assertTrue(screen.root.texts().contains("资源管理"))
        assertTrue(screen.root.texts().contains("创建首页卡片"))
    }

    @Test
    fun `raw json screen replaces loading with descriptor snapshot`() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val screen = ResourceRawJsonScreen(activity) {}
        activity.setContentView(screen.root)

        screen.render(null)
        assertTrue(screen.root.texts().contains("正在读取资源清单..."))
        screen.render(item())
        assertTrue(screen.root.texts().any { it.contains("\"id\": \"tool\"") })
    }

    private fun item() = ResourceItemUiState(
        descriptor = ResourceFeatureDescriptor("tool", "Tool"),
        phase = ResourceItemPhase.Installed,
        projection = KiteResourceUiProjection("已获取", "打开", true, null),
        primaryIntent = KiteResourceActionIntent.Open,
        secondaryIntent = null
    )

    private fun View.views(): List<View> = buildList {
        add(this@views)
        if (this@views is ViewGroup) repeat(childCount) { addAll(getChildAt(it).views()) }
    }

    private fun View.texts(): List<String> = buildList {
        if (this@texts is TextView) add(text.toString())
        if (this@texts is ViewGroup) repeat(childCount) { addAll(getChildAt(it).texts()) }
    }
}
