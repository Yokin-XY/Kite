package com.kite.app.feature.runhistory

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.kite.app.run.CardRunHistoryEntry
import com.kite.app.run.CardRunHistoryStep
import com.kite.app.run.CardRunStatus
import com.kite.app.theme.KiteTheme
import com.kite.app.theme.ThemeConfig
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RunHistoryScreenTest {
    @Test
    fun `list detail and report project one history snapshot`() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val screen = RunHistoryScreen(
            context = activity,
            theme = ThemeConfig(KiteTheme.defaultThemeColor, KiteTheme.defaultBackgroundColor),
            listTitle = "运行历史",
            emptyTitle = "空",
            emptyDetail = "暂无",
            onBack = {},
            onOpenEntry = {},
            onOpenReport = {}
        )
        activity.setContentView(screen.root)
        val entry = CardRunHistoryEntry(
            historyId = "h",
            recipeId = "r",
            recipeName = "R",
            instanceId = "i",
            status = CardRunStatus.Completed,
            startedAt = 1L,
            endedAt = 2L,
            stepCount = 1,
            currentStepIndex = 0,
            steps = listOf(CardRunHistoryStep(0, "shell", "执行", "echo ok", "输出：ok"))
        )

        screen.render(RunHistoryUiState("r", listOf(entry)))
        assertTrue(screen.root.texts().any { it.contains("已完成") })
        screen.render(RunHistoryUiState("r", listOf(entry), RunHistoryPage.Detail, "h"))
        assertTrue(screen.root.texts().contains("流程快照"))
        screen.render(RunHistoryUiState("r", listOf(entry), RunHistoryPage.Report, "h", 0))
        assertTrue(screen.root.texts().contains("只读 SH 报告"))
        assertTrue(screen.root.texts().any { it.contains("ok") })
    }

    private fun View.texts(): List<String> = buildList {
        if (this@texts is TextView) add(text.toString())
        if (this@texts is ViewGroup) repeat(childCount) { addAll(getChildAt(it).texts()) }
    }
}
