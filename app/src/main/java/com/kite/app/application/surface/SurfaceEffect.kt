package com.kite.app.application.surface

enum class SurfaceChromeMode {
    Standard,
    Immersive
}

sealed interface SurfaceEffect {
    data class SetChromeMode(val mode: SurfaceChromeMode) : SurfaceEffect
    data object RequestBack : SurfaceEffect
}
