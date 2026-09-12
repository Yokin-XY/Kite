package com.kite.app.ui.terminal

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TerminalCustomShortcutStoreTest {
    private val context by lazy { ApplicationProvider.getApplicationContext<Context>() }
    private lateinit var store: TerminalCustomShortcutStore

    @Before
    @After
    fun clearStore() {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun shortcutsPersistAcrossStoreInstances() {
        val shortcut = TerminalShortcutDefinition(setOf(TerminalShortcutModifier.CTRL), TerminalShortcutKey.R)

        assertTrue(store.add(shortcut) is TerminalCustomShortcutWriteResult.Accepted)

        store = TerminalCustomShortcutStore(context)
        assertEquals(listOf(shortcut), store.snapshot())
    }

    @Test
    fun duplicateCombinationIsRejected() {
        val shortcut = TerminalShortcutDefinition(setOf(TerminalShortcutModifier.CTRL), TerminalShortcutKey.R)

        assertTrue(store.add(shortcut) is TerminalCustomShortcutWriteResult.Accepted)
        assertTrue(store.add(shortcut) is TerminalCustomShortcutWriteResult.Rejected)
        assertEquals(listOf(shortcut), store.snapshot())
    }

    @Test
    fun updateReplacesShortcutWithoutTreatingItselfAsDuplicate() {
        val original = TerminalShortcutDefinition(setOf(TerminalShortcutModifier.CTRL), TerminalShortcutKey.R)
        val replacement = TerminalShortcutDefinition(setOf(TerminalShortcutModifier.CTRL), TerminalShortcutKey.D)

        assertTrue(store.add(original) is TerminalCustomShortcutWriteResult.Accepted)
        val result = store.update(original.id, replacement)

        assertTrue(result is TerminalCustomShortcutWriteResult.Accepted)
        assertEquals(listOf(replacement), store.snapshot())
    }

    @Test
    fun updateCannotReuseAnotherShortcutCombination() {
        val first = TerminalShortcutDefinition(setOf(TerminalShortcutModifier.CTRL), TerminalShortcutKey.R)
        val second = TerminalShortcutDefinition(setOf(TerminalShortcutModifier.CTRL), TerminalShortcutKey.D)

        assertTrue(store.add(first) is TerminalCustomShortcutWriteResult.Accepted)
        assertTrue(store.add(second) is TerminalCustomShortcutWriteResult.Accepted)
        assertTrue(store.update(first.id, second) is TerminalCustomShortcutWriteResult.Rejected)
        assertEquals(listOf(first, second), store.snapshot())
    }

    @Test
    fun removedShortcutDisappearsFromSnapshot() {
        val shortcut = TerminalShortcutDefinition(setOf(TerminalShortcutModifier.ALT), TerminalShortcutKey.B)

        assertTrue(store.add(shortcut) is TerminalCustomShortcutWriteResult.Accepted)
        assertTrue(store.remove(shortcut.id))

        assertTrue(store.snapshot().isEmpty())
        assertFalse(store.remove(shortcut.id))
    }

    @Test
    fun corruptedJsonFallsBackToEmptySnapshot() {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .putString("shortcuts", "{not-json")
            .commit()

        assertTrue(store.snapshot().isEmpty())
    }

    @Test
    fun unsupportedCombinationIsRejectedWithoutPersisting() {
        val shortcut = TerminalShortcutDefinition(setOf(TerminalShortcutModifier.CTRL), TerminalShortcutKey.F1)

        assertTrue(store.add(shortcut) is TerminalCustomShortcutWriteResult.Rejected)
        assertTrue(store.snapshot().isEmpty())
    }

    @Before
    fun setUpStore() {
        store = TerminalCustomShortcutStore(context)
    }

    private companion object {
        const val PREFERENCES = "kite_terminal_custom_shortcuts"
    }
}
