package com.kite.app.feature.runhistory

import com.kite.app.application.runs.RunHistoryGateway
import com.kite.app.run.CardRunHistoryEntry
import com.kite.app.run.CardRunHistoryStep
import com.kite.app.run.CardRunStatus
import kotlinx.coroutines.flow.MutableSharedFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RunHistoryControllerTest {
    @Test
    fun `initial entry restores detail and back stays inside feature before shell`() {
        val gateway = FakeGateway(listOf(entry("history-1")))
        val controller = RunHistoryController("recipe", "history-1", gateway)

        controller.refresh()
        assertEquals(RunHistoryPage.Detail, controller.state.value.page)
        assertEquals("history-1", controller.state.value.selectedHistoryId)

        controller.openReport(0)
        assertEquals(RunHistoryPage.Report, controller.state.value.page)
        assertTrue(controller.back())
        assertEquals(RunHistoryPage.Detail, controller.state.value.page)
        assertTrue(controller.back())
        assertEquals(RunHistoryPage.List, controller.state.value.page)
        controller.refresh()
        assertEquals(RunHistoryPage.List, controller.state.value.page)
        assertFalse(controller.back())
    }

    @Test
    fun `refresh drops stale selection instead of inventing history`() {
        val gateway = FakeGateway(listOf(entry("history-1")))
        val controller = RunHistoryController("recipe", null, gateway)
        controller.refresh()
        controller.openEntry("history-1")

        gateway.entries = emptyList()
        controller.refresh()

        assertEquals(RunHistoryPage.List, controller.state.value.page)
        assertNull(controller.state.value.selectedHistoryId)
    }

    private fun entry(id: String) = CardRunHistoryEntry(
        historyId = id,
        recipeId = "recipe",
        recipeName = "Recipe",
        instanceId = "instance",
        status = CardRunStatus.Completed,
        startedAt = 1L,
        endedAt = 2L,
        stepCount = 1,
        currentStepIndex = 0,
        steps = listOf(CardRunHistoryStep(0, "shell", "执行", "echo ok", "输出：ok"))
    )

    private class FakeGateway(var entries: List<CardRunHistoryEntry>) : RunHistoryGateway {
        override val changes = MutableSharedFlow<Unit>()
        override fun historyForRecipe(recipeId: String): List<CardRunHistoryEntry> = entries
    }
}
