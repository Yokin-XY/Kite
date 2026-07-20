package com.kite.app.ui.terminal

import android.view.View
import com.kite.app.R

interface TerminalPanelActionHost {
    fun sendInput(input: String)
    fun applyComposerEffect(effect: TerminalComposerEffect)
    fun adjustFont(step: Int)
    fun pasteClipboard()
    fun showThemeMenu(anchor: View)
    fun themeLabel(): String
}

enum class TerminalComposerEffect {
    PRESERVE,
    RESET_AFTER_ACTION,
}

fun interface TerminalPanelActionHandler {
    fun execute(host: TerminalPanelActionHost, anchor: View)
}

data class TerminalPanelAction(
    val id: String,
    val titleRes: Int,
    val subtitleRes: Int? = null,
    val iconRes: Int? = null,
    val subtitleProvider: ((TerminalPanelActionHost) -> String)? = null,
    val composerEffect: TerminalComposerEffect = TerminalComposerEffect.PRESERVE,
    val handler: TerminalPanelActionHandler
) {
    fun resolvedSubtitle(host: TerminalPanelActionHost, resolveString: (Int) -> String): String =
        subtitleProvider?.invoke(host) ?: subtitleRes?.let(resolveString).orEmpty()

    fun execute(host: TerminalPanelActionHost, anchor: View) {
        handler.execute(host, anchor)
        host.applyComposerEffect(composerEffect)
    }
}

data class TerminalPanelPage(
    val id: String,
    val showDpad: Boolean = false,
    val actions: List<TerminalPanelAction> = emptyList()
)

object TerminalPanelActionRegistry {
    private val pages = LinkedHashMap<String, TerminalPanelPage>()

    init {
        resetToDefaults()
    }

    @Synchronized
    fun snapshot(): List<TerminalPanelPage> = pages.values.toList()

    @Synchronized
    fun register(
        pageId: String,
        action: TerminalPanelAction,
        showDpad: Boolean? = null
    ) {
        require(pageId.isNotBlank()) { "pageId must not be blank" }
        require(action.id.isNotBlank()) { "action id must not be blank" }
        val current = pages[pageId] ?: TerminalPanelPage(id = pageId)
        val index = current.actions.indexOfFirst { it.id == action.id }
        val actions = current.actions.toMutableList().apply {
            if (index >= 0) set(index, action) else add(action)
        }
        pages[pageId] = current.copy(
            showDpad = showDpad ?: current.showDpad,
            actions = actions
        )
    }

    @Synchronized
    fun unregister(pageId: String, actionId: String) {
        val current = pages[pageId] ?: return
        pages[pageId] = current.copy(actions = current.actions.filterNot { it.id == actionId })
    }

    @Synchronized
    internal fun resetToDefaults() {
        pages.clear()
        defaultPages().forEach { page -> pages[page.id] = page }
    }

    private fun defaultPages(): List<TerminalPanelPage> {
        return listOf(
            TerminalPanelPage(
                id = "control",
                showDpad = true,
                actions = listOf(
                    inputAction(
                        id = "interrupt",
                        titleRes = R.string.terminal_ctrl_c,
                        subtitleRes = R.string.terminal_action_interrupt,
                        input = "\u0003",
                        iconRes = R.drawable.ic_terminal_interrupt,
                        composerEffect = TerminalComposerEffect.RESET_AFTER_ACTION,
                    ),
                    inputAction(
                        id = "clear",
                        titleRes = R.string.terminal_ctrl_l,
                        subtitleRes = R.string.terminal_action_clear_screen,
                        input = "\u000c",
                        iconRes = R.drawable.ic_terminal_clear_line,
                    ),
                    fontAction(
                        "font-smaller",
                        R.string.terminal_font_smaller,
                        R.string.terminal_action_font_smaller,
                        -1,
                    ),
                    fontAction(
                        "font-larger",
                        R.string.terminal_font_larger,
                        R.string.terminal_action_font_larger,
                        1,
                    ),
                )
            ),
            TerminalPanelPage(
                id = "utility",
                actions = listOf(
                    inputAction(
                        "escape",
                        R.string.terminal_esc,
                        R.string.terminal_action_escape,
                        "\u001b",
                    ),
                    inputAction(
                        "tab",
                        R.string.terminal_tab,
                        R.string.terminal_action_tab,
                        "\t",
                    ),
                    TerminalPanelAction(
                        id = "paste",
                        titleRes = R.string.terminal_paste,
                        subtitleRes = R.string.terminal_action_clipboard,
                        handler = TerminalPanelActionHandler { host, _ -> host.pasteClipboard() }
                    ),
                    TerminalPanelAction(
                        id = "theme",
                        titleRes = R.string.terminal_theme_button,
                        subtitleProvider = { host -> host.themeLabel() },
                        handler = TerminalPanelActionHandler { host, anchor -> host.showThemeMenu(anchor) }
                    )
                )
            )
        )
    }

    private fun inputAction(
        id: String,
        titleRes: Int,
        subtitleRes: Int,
        input: String,
        iconRes: Int? = null,
        composerEffect: TerminalComposerEffect = TerminalComposerEffect.PRESERVE,
    ): TerminalPanelAction {
        return TerminalPanelAction(
            id = id,
            titleRes = titleRes,
            subtitleRes = subtitleRes,
            iconRes = iconRes,
            composerEffect = composerEffect,
            handler = TerminalPanelActionHandler { host, _ -> host.sendInput(input) }
        )
    }

    private fun fontAction(
        id: String,
        titleRes: Int,
        subtitleRes: Int,
        step: Int
    ): TerminalPanelAction {
        return TerminalPanelAction(
            id = id,
            titleRes = titleRes,
            subtitleRes = subtitleRes,
            handler = TerminalPanelActionHandler { host, _ -> host.adjustFont(step) }
        )
    }
}
