package com.kite.app.ui.terminal

import com.kite.app.R
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
        assertEquals(
            TerminalComposerEffect.RESET_AFTER_ACTION,
            pages.first().actions.single { it.id == "interrupt" }.composerEffect,
        )
        assertEquals(
            TerminalComposerEffect.PRESERVE,
            pages.first().actions.single { it.id == "clear" }.composerEffect,
        )
        assertTrue(pages.flatMap { it.actions }.all { it.titleRes != 0 })
    }

    @Test
    fun registerAddsAndReplacesActionWithoutChangingPageOrder() {
        val first = testAction("custom", R.string.terminal_ctrl_c)
        val replacement = testAction("custom", R.string.terminal_clear)

        TerminalPanelActionRegistry.register("utility", first)
        TerminalPanelActionRegistry.register("utility", replacement)

        val pages = TerminalPanelActionRegistry.snapshot()
        val utility = pages.single { it.id == "utility" }
        assertEquals(listOf("control", "utility"), pages.map { it.id })
        assertEquals(1, utility.actions.count { it.id == "custom" })
        assertEquals(R.string.terminal_clear, utility.actions.single { it.id == "custom" }.titleRes)
        assertFalse(utility.showDpad)
    }

    private fun testAction(id: String, titleRes: Int): TerminalPanelAction {
        return TerminalPanelAction(
            id = id,
            titleRes = titleRes,
            handler = TerminalPanelActionHandler { _, _ -> Unit }
        )
    }
}
