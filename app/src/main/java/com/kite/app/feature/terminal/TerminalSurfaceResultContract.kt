package com.kite.app.feature.terminal

import android.os.Bundle
import androidx.fragment.app.Fragment
import com.kite.app.application.surface.SurfaceChromeMode
import com.kite.app.application.surface.SurfaceEffect

/** 终端只发送通用显示面 effect，不识别 MainActivity 或 CardRunActivity。 */
object TerminalSurfaceResultContract {
    const val REQUEST_KEY = "kite.terminal.surface.effect"

    fun send(fragment: Fragment, effect: SurfaceEffect) {
        fragment.parentFragmentManager.setFragmentResult(REQUEST_KEY, encode(effect))
    }

    fun parse(bundle: Bundle): SurfaceEffect? = when (bundle.getString(KEY_KIND)) {
        KIND_CHROME -> bundle.getString(KEY_MODE)
            ?.let { value -> runCatching { SurfaceChromeMode.valueOf(value) }.getOrNull() }
            ?.let(SurfaceEffect::SetChromeMode)
        KIND_BACK -> SurfaceEffect.RequestBack
        else -> null
    }

    private fun encode(effect: SurfaceEffect): Bundle = Bundle().apply {
        when (effect) {
            is SurfaceEffect.SetChromeMode -> {
                putString(KEY_KIND, KIND_CHROME)
                putString(KEY_MODE, effect.mode.name)
            }
            SurfaceEffect.RequestBack -> putString(KEY_KIND, KIND_BACK)
        }
    }

    private const val KEY_KIND = "kind"
    private const val KEY_MODE = "mode"
    private const val KIND_CHROME = "chrome"
    private const val KIND_BACK = "back"
}
