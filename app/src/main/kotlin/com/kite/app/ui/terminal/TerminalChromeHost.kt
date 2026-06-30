package com.kite.app.ui.terminal

/**
 * 终端页切到“会话详情”时，通知宿主 Activity 收起外围壳。
 */
interface TerminalChromeHost {
    fun setTerminalDetailMode(enabled: Boolean)
    fun openTerminalSession(sessionId: String)
}
