package com.kite.app.run

import android.content.Context

/**
 * X11 显示服务——已退役（图形支持移除）。
 */
object KiteX11SurfaceServer {
    fun ensureStarted(
        @Suppress("UNUSED_PARAMETER") context: Context,
        @Suppress("UNUSED_PARAMETER") binding: KiteX11SurfaceBinding,
    ): Result<KiteX11SurfaceBinding> =
        Result.failure(IllegalStateException("X11 图形支持已移除"))

    fun start(@Suppress("UNUSED_PARAMETER") binding: KiteX11SurfaceBinding): Boolean = false
    fun stop(@Suppress("UNUSED_PARAMETER") display: String) { /* retired */ }
    fun isRunning(@Suppress("UNUSED_PARAMETER") display: String): Boolean = false
}
