package com.kite.app.ui.terminal

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

sealed interface TerminalCustomShortcutWriteResult {
    data class Accepted(val shortcuts: List<TerminalShortcutDefinition>) : TerminalCustomShortcutWriteResult
    data class Rejected(val reason: String) : TerminalCustomShortcutWriteResult
}

/**
 * 用户自定义终端组合键的低频事实源。
 *
 * 只保存用户主动选择的键位合同，不保存会话状态或终端执行结果。
 */
class TerminalCustomShortcutStore(context: Context) {
    private val preferences = context.applicationContext
        .getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun snapshot(): List<TerminalShortcutDefinition> = runCatching {
        val root = JSONObject(preferences.getString(KEY_SHORTCUTS, "{}").orEmpty())
        val shortcuts = root.optJSONArray(KEY_SHORTCUTS) ?: JSONArray()
        buildList {
            for (index in 0 until shortcuts.length()) {
                parse(shortcuts.optJSONObject(index))?.let(::add)
            }
        }
    }.getOrDefault(emptyList())

    @Synchronized
    fun add(definition: TerminalShortcutDefinition): TerminalCustomShortcutWriteResult {
        if (!TerminalShortcutCodec.isSupported(definition)) {
            return TerminalCustomShortcutWriteResult.Rejected("不支持的组合键：${definition.label}")
        }
        val current = snapshot()
        if (current.any { it.id == definition.id }) {
            return TerminalCustomShortcutWriteResult.Rejected("组合键已存在：${definition.label}")
        }
        if (current.size >= MAX_SHORTCUTS) {
            return TerminalCustomShortcutWriteResult.Rejected("最多支持 $MAX_SHORTCUTS 个自定义组合键")
        }
        return persist(current + definition)
    }

    @Synchronized
    fun update(existingId: String, definition: TerminalShortcutDefinition): TerminalCustomShortcutWriteResult {
        if (!TerminalShortcutCodec.isSupported(definition)) {
            return TerminalCustomShortcutWriteResult.Rejected("不支持的组合键：${definition.label}")
        }
        val current = snapshot()
        if (current.none { it.id == existingId }) {
            return TerminalCustomShortcutWriteResult.Rejected("组合键不存在：$existingId")
        }
        if (current.any { it.id == definition.id && it.id != existingId }) {
            return TerminalCustomShortcutWriteResult.Rejected("组合键已存在：${definition.label}")
        }
        return persist(current.map { if (it.id == existingId) definition else it })
    }

    @Synchronized
    fun remove(id: String): Boolean {
        val current = snapshot()
        val next = current.filterNot { it.id == id }
        if (next.size == current.size) return false
        preferences.edit()
            .putString(KEY_SHORTCUTS, serialize(next))
            .commit()
        return true
    }

    private fun persist(shortcuts: List<TerminalShortcutDefinition>): TerminalCustomShortcutWriteResult.Accepted {
        preferences.edit()
            .putString(KEY_SHORTCUTS, serialize(shortcuts))
            .commit()
        return TerminalCustomShortcutWriteResult.Accepted(shortcuts)
    }

    private fun serialize(shortcuts: List<TerminalShortcutDefinition>): String {
        val root = JSONObject()
            .put(SCHEMA_VERSION, VERSION)
            .put(
                KEY_SHORTCUTS,
                JSONArray().apply {
                    shortcuts.forEach { shortcut ->
                        put(
                            JSONObject()
                                .put(KEY_ID, shortcut.id)
                                .put(
                                    KEY_MODIFIERS,
                                    JSONArray().apply {
                                        shortcut.modifiers
                                            .sortedBy { modifier -> modifier.ordinal }
                                            .forEach { modifier -> put(modifier.name) }
                                    },
                                )
                                .put(KEY_KEY, shortcut.key.name)
                        )
                    }
                },
            )
        return root.toString()
    }

    private fun parse(json: JSONObject?): TerminalShortcutDefinition? {
        if (json == null) return null
        val key = json.optString(KEY_KEY)
            .takeIf { it.isNotBlank() }
            ?.let { name -> runCatching { TerminalShortcutKey.valueOf(name) }.getOrNull() }
            ?: return null
        val modifierNames = json.optJSONArray(KEY_MODIFIERS) ?: JSONArray()
        val modifiers = buildSet {
            for (index in 0 until modifierNames.length()) {
                runCatching { TerminalShortcutModifier.valueOf(modifierNames.optString(index)) }
                    .getOrNull()
                    ?.let(::add)
            }
        }
        return TerminalShortcutDefinition(modifiers = modifiers, key = key)
    }

    private companion object {
        const val PREFERENCES = "kite_terminal_custom_shortcuts"
        const val KEY_SHORTCUTS = "shortcuts"
        const val KEY_ID = "id"
        const val KEY_MODIFIERS = "modifiers"
        const val KEY_KEY = "key"
        const val SCHEMA_VERSION = "schemaVersion"
        const val VERSION = 1
        const val MAX_SHORTCUTS = 128
    }
}
