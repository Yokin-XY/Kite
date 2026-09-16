package com.kite.app.run

/**
 * X11 显示分配——已退役（图形支持移除）。
 */
object KiteX11SurfacePlan {
    fun binding(@Suppress("UNUSED_PARAMETER") display: String): KiteX11SurfaceBinding =
        KiteX11SurfaceBinding(display = ":0", socketPath = "/tmp/.X11-unix/X0")

    fun allocate(
        @Suppress("UNUSED_PARAMETER") instanceId: String,
        @Suppress("UNUSED_PARAMETER") occupiedDisplays: Set<String>,
    ): KiteX11SurfaceBinding = KiteX11SurfaceBinding(display = ":0", socketPath = "/tmp/.X11-unix/X0")
}
