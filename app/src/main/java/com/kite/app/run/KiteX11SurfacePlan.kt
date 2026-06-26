package com.kite.app.run

data class KiteX11SurfaceBinding(
    val display: String,
    val socketPath: String
) {
    fun environment(): Map<String, String> = mapOf(
        "DISPLAY" to display,
        "KITE_X11_DISPLAY" to display,
        "KITE_X11_SOCKET" to socketPath
    )
}

object KiteX11SurfacePlan {
    private const val DISPLAY_BASE = 20
    private const val DISPLAY_LIMIT = 180

    fun allocate(
        instanceId: String,
        occupiedDisplays: Set<String> = emptySet()
    ): KiteX11SurfaceBinding {
        val cleanInstanceId = instanceId.trim().ifBlank { "kite" }
        val occupied = occupiedDisplays
            .mapNotNull { it.normalizedDisplayOrNull() }
            .toSet()
        val start = DISPLAY_BASE + Math.floorMod(cleanInstanceId.hashCode(), DISPLAY_LIMIT)
        repeat(DISPLAY_LIMIT) { offset ->
            val number = DISPLAY_BASE + Math.floorMod(start - DISPLAY_BASE + offset, DISPLAY_LIMIT)
            val display = ":$number"
            if (display !in occupied) return binding(display)
        }
        error("No free X11 display slots")
    }

    fun binding(display: String): KiteX11SurfaceBinding {
        val normalized = display.normalizedDisplayOrNull() ?: ":$DISPLAY_BASE"
        return KiteX11SurfaceBinding(
            display = normalized,
            socketPath = "/tmp/.X11-unix/X${normalized.removePrefix(":")}"
        )
    }

    private fun String.normalizedDisplayOrNull(): String? {
        val raw = trim()
        val number = when {
            raw.startsWith(":") -> raw.drop(1)
            raw.startsWith("unix:") -> raw.removePrefix("unix:").removePrefix(":")
            else -> raw
        }.takeWhile { it.isDigit() }
        return number.toIntOrNull()?.takeIf { it >= 0 }?.let { ":$it" }
    }
}
