package com.kite.app.ui.terminal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class TerminalShortcutCodecTest {
    @Test
    fun encodesCtrlLettersToControlBytes() {
        assertEquals("\u0001", TerminalShortcutCodec.encode(shortcut(TerminalShortcutModifier.CTRL, TerminalShortcutKey.A)))
        assertEquals("\u0004", TerminalShortcutCodec.encode(shortcut(TerminalShortcutModifier.CTRL, TerminalShortcutKey.D)))
        assertEquals("\u0012", TerminalShortcutCodec.encode(shortcut(TerminalShortcutModifier.CTRL, TerminalShortcutKey.R)))
    }

    @Test
    fun encodesAltByPrefixingEscape() {
        assertEquals("\u001bb", TerminalShortcutCodec.encode(shortcut(TerminalShortcutModifier.ALT, TerminalShortcutKey.B)))
    }

    @Test
    fun encodesCtrlAltByPrefixingEscapeToControlByte() {
        assertEquals(
            "\u001b\u0002",
            TerminalShortcutCodec.encode(
                TerminalShortcutDefinition(
                    setOf(TerminalShortcutModifier.CTRL, TerminalShortcutModifier.ALT),
                    TerminalShortcutKey.B,
                )
            )
        )
    }

    @Test
    fun encodesShiftLetterAsUppercase() {
        assertEquals("R", TerminalShortcutCodec.encode(shortcut(TerminalShortcutModifier.SHIFT, TerminalShortcutKey.R)))
    }

    @Test
    fun encodesShiftTabAsBacktabSequence() {
        assertEquals("\u001b[Z", TerminalShortcutCodec.encode(shortcut(TerminalShortcutModifier.SHIFT, TerminalShortcutKey.TAB)))
    }

    @Test
    fun encodesPlainFunctionKeys() {
        assertEquals("\u001b", TerminalShortcutCodec.encode(shortcut(emptySet(), TerminalShortcutKey.ESCAPE)))
        assertEquals("\r", TerminalShortcutCodec.encode(shortcut(emptySet(), TerminalShortcutKey.ENTER)))
        assertEquals("\u001bOP", TerminalShortcutCodec.encode(shortcut(emptySet(), TerminalShortcutKey.F1)))
    }

    @Test
    fun rejectsUnsupportedModifierOnFunctionKey() {
        val definition = shortcut(TerminalShortcutModifier.CTRL, TerminalShortcutKey.F1)

        assertNull(TerminalShortcutCodec.encode(definition))
        assertFalse(TerminalShortcutCodec.isSupported(definition))
    }

    private fun shortcut(
        modifier: TerminalShortcutModifier,
        key: TerminalShortcutKey,
    ) = shortcut(setOf(modifier), key)

    private fun shortcut(
        modifiers: Set<TerminalShortcutModifier>,
        key: TerminalShortcutKey,
    ) = TerminalShortcutDefinition(modifiers, key)
}
