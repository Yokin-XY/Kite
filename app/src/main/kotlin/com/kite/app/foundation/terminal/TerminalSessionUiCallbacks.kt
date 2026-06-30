package com.kite.app.foundation.terminal

import com.termux.terminal.TerminalSession

/**
 * 终端会话控制器和终端页面之间的最小交互面。
 *
 * 控制器只负责“会话怎么跑”，不负责“页面怎么画”。
 * 页面通过这些回调把控制器发来的事件转成具体 UI 行为。
 */
interface TerminalSessionUiCallbacks {

    fun showSessionNote(message: String)

    fun attachSession(session: TerminalSession)

    fun onManagedSessionsChanged()

    fun refreshTerminalView()

    fun copyTextToClipboard(text: String)

    fun pasteTextFromClipboard()

    fun performBellFeedback()

    fun refreshTerminalColors()

    fun updateCursorState(state: Boolean)
}
