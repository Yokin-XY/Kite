package com.kite.app.ui.terminal

import android.app.Dialog
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.kite.app.ui.UiActionRole
import com.kite.app.ui.UiDialogAction
import com.kite.app.ui.UiKit
import com.kite.app.ui.UiTextRole
import kotlin.math.min

data class TerminalShortcutEditorStrings(
    val addTitle: String,
    val editTitle: String,
    val cancelLabel: String,
    val confirmLabel: String,
    val previewLabel: String,
    val modifierLabel: String,
    val keyLabel: String,
    val lettersLabel: String,
    val digitsLabel: String,
    val symbolsLabel: String,
    val functionsLabel: String,
    val selectKeyHint: String,
    val duplicateMessage: String,
    val unsupportedMessage: String,
)

/**
 * 应用内组合键编辑器。所有输入均通过点选完成，不拉起系统输入法。
 */
fun showTerminalShortcutEditor(
    context: Context,
    ui: UiKit,
    strings: TerminalShortcutEditorStrings,
    initial: TerminalShortcutDefinition?,
    existingLabels: Set<String>,
    onConfirm: (TerminalShortcutDefinition) -> Unit,
) {
    val selectedModifiers = LinkedHashSet<TerminalShortcutModifier>()
    initial?.let { selectedModifiers.addAll(it.modifiers) }
    var selectedKey = initial?.key

    lateinit var dialog: Dialog
    dialog = ui.showContentDialog(
        context = context,
        title = if (initial == null) strings.addTitle else strings.editTitle,
        dismissLabel = strings.cancelLabel,
        primaryAction = UiDialogAction(
            label = strings.confirmLabel,
            role = UiActionRole.Primary,
            dismissOnClick = false,
        ) {
            val key = selectedKey
            val definition = key?.let { TerminalShortcutDefinition(selectedModifiers.toSet(), it) }
            when {
                definition == null -> Unit
                definition.label in existingLabels -> Unit
                !TerminalShortcutCodec.isSupported(definition) -> Unit
                else -> {
                    onConfirm(definition)
                    dialog.dismiss()
                }
            }
        },
    ) {
        val editorContent = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        val editorScroll = ScrollView(context).apply {
            isVerticalScrollBarEnabled = true
            addView(
                editorContent,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
        }
        val preview = TextView(context).apply {
            gravity = Gravity.CENTER
            ui.applyTextRole(this, UiTextRole.CardTitle)
            text = selectedKey
                ?.let { key -> TerminalShortcutDefinition(selectedModifiers.toSet(), key).label }
                ?: strings.selectKeyHint
        }
        val message = TextView(context).apply {
            gravity = Gravity.CENTER
            ui.applyTextRole(this, UiTextRole.Supporting)
            setPadding(0, ui.dp(3), 0, 0)
        }
        val modifierButtons = LinkedHashMap<TerminalShortcutModifier, TextView>()
        val keyButtons = LinkedHashMap<TerminalShortcutKey, TextView>()

        fun chip(label: String): TextView = TextView(context).apply {
            gravity = Gravity.CENTER
            includeFontPadding = false
            maxLines = 1
            // contentDescription 只进无障碍树；不设 text 时芯片在屏幕上是空白块。
            text = label
            contentDescription = label
            ui.applyTextRole(this, UiTextRole.Action)
        }

        fun applyChipState(
            view: TextView,
            selected: Boolean,
            enabled: Boolean = true,
        ) {
            view.isEnabled = enabled
            view.alpha = if (enabled) 1f else 0.42f
            view.background = GradientDrawable().apply {
                cornerRadius = ui.dp(10).toFloat()
                setColor(
                    when {
                        selected -> ui.tokens.primaryStrong
                        else -> ui.tokens.inputBackground
                    },
                )
                setStroke(
                    ui.dp(1),
                    if (selected) ui.tokens.primaryStrong else ui.tokens.border,
                )
            }
            view.setTextColor(if (selected) ui.tokens.primaryText else ui.tokens.textPrimary)
        }

        fun currentDefinition(): TerminalShortcutDefinition? =
            selectedKey?.let { TerminalShortcutDefinition(selectedModifiers.toSet(), it) }

        fun refresh() {
            val definition = currentDefinition()
            preview.text = definition?.label ?: strings.selectKeyHint
            modifierButtons.forEach { (modifier, button) ->
                applyChipState(button, selected = modifier in selectedModifiers)
            }
            keyButtons.forEach { (key, button) ->
                val candidate = TerminalShortcutDefinition(selectedModifiers.toSet(), key)
                applyChipState(
                    button,
                    selected = key == selectedKey,
                    enabled = TerminalShortcutCodec.isSupported(candidate),
                )
            }
            message.text = when {
                definition == null -> ""
                definition.label in existingLabels -> strings.duplicateMessage
                !TerminalShortcutCodec.isSupported(definition) -> strings.unsupportedMessage
                else -> ""
            }
        }

        fun sectionLabel(text: String): TextView = TextView(context).apply {
            this.text = text
            ui.applyTextRole(this, UiTextRole.Supporting)
            setPadding(0, ui.dp(12), 0, ui.dp(3))
        }

        fun addGrid(
            values: List<TerminalShortcutKey>,
            columns: Int,
        ) {
            val grid = GridLayout(context).apply {
                columnCount = columns
            }
            values.forEachIndexed { index, key ->
                val button = chip(key.label).apply {
                    setOnClickListener {
                        selectedKey = key
                        refresh()
                    }
                }
                keyButtons[key] = button
                grid.addView(
                    button,
                    GridLayout.LayoutParams(
                        GridLayout.spec(index / columns),
                        GridLayout.spec(index % columns),
                    ).apply {
                        width = 0
                        height = ui.dp(40)
                        columnSpec = GridLayout.spec(index % columns, 1f)
                        rowSpec = GridLayout.spec(index / columns)
                        setMargins(ui.dp(2), ui.dp(2), ui.dp(2), ui.dp(2))
                    },
                )
            }
            editorContent.addView(
                grid,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
        }

        editorContent.addView(sectionLabel(strings.previewLabel))
        editorContent.addView(
            preview,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = ui.dp(10) },
        )
        editorContent.addView(
            message,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
        editorContent.addView(sectionLabel(strings.modifierLabel))
        val modifierGrid = GridLayout(context).apply { columnCount = 3 }
        TerminalShortcutModifier.values().forEach { modifier ->
            val button = chip(modifier.label).apply {
                setOnClickListener {
                    if (modifier in selectedModifiers) {
                        selectedModifiers.remove(modifier)
                    } else {
                        selectedModifiers.add(modifier)
                    }
                    selectedKey?.let { key ->
                        val candidate = TerminalShortcutDefinition(selectedModifiers.toSet(), key)
                        if (!TerminalShortcutCodec.isSupported(candidate)) selectedKey = null
                    }
                    refresh()
                }
            }
            modifierButtons[modifier] = button
            modifierGrid.addView(
                button,
                GridLayout.LayoutParams(
                    GridLayout.spec(0),
                    GridLayout.spec(modifier.ordinal),
                ).apply {
                    width = 0
                    height = ui.dp(42)
                    columnSpec = GridLayout.spec(modifier.ordinal, 1f)
                    setMargins(ui.dp(2), ui.dp(2), ui.dp(2), ui.dp(2))
                },
            )
        }
        editorContent.addView(
            modifierGrid,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
        editorContent.addView(sectionLabel(strings.keyLabel))

        val keys = TerminalShortcutKey.values().groupBy { it.category }
        editorContent.addView(sectionLabel(strings.lettersLabel))
        addGrid(keys.getValue(TerminalShortcutKeyCategory.LETTER), 6)
        editorContent.addView(sectionLabel(strings.digitsLabel))
        addGrid(keys.getValue(TerminalShortcutKeyCategory.DIGIT), 5)
        editorContent.addView(sectionLabel(strings.symbolsLabel))
        addGrid(keys.getValue(TerminalShortcutKeyCategory.SYMBOL), 5)
        editorContent.addView(sectionLabel(strings.functionsLabel))
        addGrid(keys.getValue(TerminalShortcutKeyCategory.FUNCTION), 4)
        refresh()
        val scrollHeight = min(
            (context.resources.displayMetrics.heightPixels * 0.62f).toInt(),
            ui.dp(500),
        )
        addView(
            editorScroll,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                scrollHeight,
            ).apply { topMargin = ui.dp(8) },
        )
    }
}
