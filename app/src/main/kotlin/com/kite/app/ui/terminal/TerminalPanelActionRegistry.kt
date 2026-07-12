package com.kite.app.ui.terminal

import android.view.View
import com.kite.app.R

interface TerminalPanelActionHost {
    fun sendInput(input: String)
    fun adjustFont(step: Int)
    fun pasteClipboard()
    fun showThemeMenu(anchor: View)
    fun themeLabel(): String
}

fun interface TerminalPanelActionHandler {
    fun execute(host: TerminalPanelActionHost, anchor: View)
}

data class TerminalPanelAction(
    val id: String,
    val title: String,
    val subtitle: String = "",
    val iconRes: Int? = null,
    val subtitleProvider: ((TerminalPanelActionHost) -> String)? = null,
    val handler: TerminalPanelActionHandler
) {
    fun resolvedSubtitle(host: TerminalPanelActionHost): String =
        subtitleProvider?.invoke(host) ?: subtitle
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
                    inputAction("interrupt", "Ctrl+C", "中断", "\u0003", R.drawable.ic_terminal_interrupt),
                    inputAction("clear", "Ctrl+L", "清屏", "\u000c", R.drawable.ic_terminal_clear_line),
                    fontAction("font-smaller", "A-", "缩小字体", -1),
                    fontAction("font-larger", "A+", "放大字体", 1)
                )
            ),
            TerminalPanelPage(
                id = "utility",
                actions = listOf(
                    inputAction("escape", "Esc", "取消", "\u001b"),
                    inputAction("tab", "Tab", "补全", "\t"),
                    TerminalPanelAction(
                        id = "paste",
                        title = "粘贴",
                        subtitle = "剪贴板",
                        handler = TerminalPanelActionHandler { host, _ -> host.pasteClipboard() }
                    ),
                    TerminalPanelAction(
                        id = "theme",
                        title = "主题",
                        subtitleProvider = { host -> host.themeLabel() },
                        handler = TerminalPanelActionHandler { host, anchor -> host.showThemeMenu(anchor) }
                    )
                )
            )
        )
    }

    private fun inputAction(
        id: String,
        title: String,
        subtitle: String,
        input: String,
        iconRes: Int? = null
    ): TerminalPanelAction {
        return TerminalPanelAction(
            id = id,
            title = title,
            subtitle = subtitle,
            iconRes = iconRes,
            handler = TerminalPanelActionHandler { host, _ -> host.sendInput(input) }
        )
    }

    private fun fontAction(
        id: String,
        title: String,
        subtitle: String,
        step: Int
    ): TerminalPanelAction {
        return TerminalPanelAction(
            id = id,
            title = title,
            subtitle = subtitle,
            handler = TerminalPanelActionHandler { host, _ -> host.adjustFont(step) }
        )
    }
}
