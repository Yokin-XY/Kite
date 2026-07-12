package com.kite.app.foundation.terminal

/** 结束普通终端时可以回到列表中的其他会话；结束卡片内嵌终端时不能顺带启动别的会话。 */
internal object TerminalSessionEndPolicy {
    fun shouldSelectManagedFallback(
        targetIsActive: Boolean,
        targetIsManaged: Boolean
    ): Boolean = targetIsActive && targetIsManaged
}
