package com.kite.app.ui.terminal

import android.content.Context
import android.view.View
import androidx.test.core.app.ApplicationProvider
import com.kite.app.R
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
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

    @Test
    fun customPagesAreAppendedAndReplacedWithoutRemovingDefaults() {
        val firstPage = TerminalPanelPage(
            id = "custom-0",
            actions = listOf(testAction("first", R.string.terminal_ctrl_c))
        )
        val replacementPage = TerminalPanelPage(
            id = "custom-0",
            actions = listOf(testAction("replacement", R.string.terminal_ctrl_l))
        )

        TerminalPanelActionRegistry.setCustomPages(listOf(firstPage))
        assertEquals(
            listOf("control", "utility", "custom-0"),
            TerminalPanelActionRegistry.snapshot().map { it.id }
        )

        TerminalPanelActionRegistry.setCustomPages(listOf(replacementPage))
        val pages = TerminalPanelActionRegistry.snapshot()
        assertEquals(listOf("control", "utility", "custom-0"), pages.map { it.id })
        assertEquals(
            listOf("replacement"),
            pages.single { it.id == "custom-0" }.actions.map { it.id }
        )
    }

    @Test
    fun customShortcutActionSendsEncodedInputAndOpensMenuOnLongPress() {
        val host = RecordingHost()
        val shortcut = TerminalShortcutDefinition(
            setOf(TerminalShortcutModifier.CTRL),
            TerminalShortcutKey.R,
        )
        val action = TerminalPanelActionRegistry.customShortcutAction(shortcut)

        assertEquals("Ctrl+R", action.id)
        assertEquals("Ctrl+R", action.resolvedTitle(host) { error("title should come from provider") })
        val anchor = View(ApplicationProvider.getApplicationContext<Context>())
        action.handler.execute(host, anchor)
        action.longPressHandler?.execute(host, anchor)

        assertEquals(listOf("\u0012"), host.inputs)
        assertEquals(listOf("Ctrl+R"), host.customShortcutMenuIds)
    }

    private fun testAction(id: String, titleRes: Int): TerminalPanelAction {
        return TerminalPanelAction(
            id = id,
            titleRes = titleRes,
            handler = TerminalPanelActionHandler { _, _ -> Unit }
        )
    }

    private class RecordingHost : TerminalPanelActionHost {
        val inputs = mutableListOf<String>()
        val customShortcutMenuIds = mutableListOf<String>()

        override fun sendInput(input: String) {
            inputs.add(input)
        }

        override fun applyComposerEffect(effect: TerminalComposerEffect) = Unit

        override fun adjustFont(step: Int) = Unit

        override fun pasteClipboard() = Unit

        override fun showThemeMenu(anchor: View) = Unit

        override fun showCustomShortcutEditor() = Unit

        override fun showCustomShortcutMenu(shortcutId: String) {
            customShortcutMenuIds.add(shortcutId)
        }

        override fun themeLabel(): String = ""
    }
}
