package com.kite.app.run

/**
 * X11 显示绑定数据——已退役（图形支持移除）。
 */
data class KiteX11SurfaceBinding(
    val display: String = ":0",
    val socketPath: String = "/tmp/.X11-unix/X0",
) {
    fun environment(): Map<String, String> = mapOf(
        "DISPLAY" to display,
    )
}
