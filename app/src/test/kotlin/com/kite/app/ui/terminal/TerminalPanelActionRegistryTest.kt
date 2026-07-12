package com.kite.app.ui.terminal

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TerminalPanelActionRegistryTest {
    @After
    fun tearDown() {
        TerminalPanelActionRegistry.resetToDefaults()
    }

    @Test
    fun defaultsExposeStablePagesAndActions() {
        val pages = TerminalPanelActionRegistry.snapshot()

        assertEquals(listOf("control", "utility"), pages.map { it.id })
        assertTrue(pages.first().showDpad)
        assertEquals(
            listOf("interrupt", "clear", "font-smaller", "font-larger"),
            pages.first().actions.map { it.id }
        )
        assertEquals(listOf("escape", "tab", "paste", "theme"), pages.last().actions.map { it.id })
    }

    @Test
    fun registerAddsAndReplacesActionWithoutChangingPageOrder() {
        val first = testAction("custom", "自定义")
        val replacement = testAction("custom", "已替换")

        TerminalPanelActionRegistry.register("utility", first)
        TerminalPanelActionRegistry.register("utility", replacement)

        val pages = TerminalPanelActionRegistry.snapshot()
        val utility = pages.single { it.id == "utility" }
        assertEquals(listOf("control", "utility"), pages.map { it.id })
        assertEquals(1, utility.actions.count { it.id == "custom" })
        assertEquals("已替换", utility.actions.single { it.id == "custom" }.title)
        assertFalse(utility.showDpad)
    }

    private fun testAction(id: String, title: String): TerminalPanelAction {
        return TerminalPanelAction(
            id = id,
            title = title,
            handler = TerminalPanelActionHandler { _, _ -> Unit }
        )
    }
}
