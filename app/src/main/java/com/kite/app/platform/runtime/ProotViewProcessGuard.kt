package com.kite.app.platform.runtime

/**
 * View 层进程守卫——已退役。
 * quiesce 空实现（不再有 View 层需要安静处理）。
 */
class ProotViewProcessGuard {
    fun quiesce(@Suppress("UNUSED_PARAMETER") environmentId: String): Result<Unit> = Result.success(Unit)
    fun ensureGuarded(@Suppress("UNUSED_PARAMETER") record: Any) { /* no-op */ }
}
