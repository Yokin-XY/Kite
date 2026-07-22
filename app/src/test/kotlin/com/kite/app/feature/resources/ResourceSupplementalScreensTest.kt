package com.kite.app.feature.resources

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.kite.app.R
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
        val context = screen.root.context
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
            view.isClickable && view.texts().any {
                it.contains(context.getString(R.string.runtime_management_status_completed))
            }
        }
        historyRow.performClick()

        assertEquals(listOf("history"), opened)
        assertTrue(screen.root.texts().contains(context.getString(R.string.resource_manage_title)))
        assertTrue(screen.root.texts().contains(context.getString(R.string.resource_more_create_card)))
    }

    @Test
    fun `raw json screen replaces loading with descriptor snapshot`() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val screen = ResourceRawJsonScreen(activity) {}
        activity.setContentView(screen.root)
        val context = screen.root.context

        screen.render(null)
        assertTrue(screen.root.texts().contains(context.getString(R.string.resource_raw_json_loading)))
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
