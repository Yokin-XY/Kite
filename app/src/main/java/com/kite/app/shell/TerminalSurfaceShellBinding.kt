package com.kite.app.shell

import androidx.fragment.app.FragmentManager
import androidx.lifecycle.LifecycleOwner
import com.kite.app.application.surface.SurfaceChromeMode
import com.kite.app.application.surface.SurfaceEffect
import com.kite.app.feature.terminal.TerminalSurfaceResultContract

/** 两种 Activity 壳共用的终端 Surface effect 解释入口。 */
internal object TerminalSurfaceShellBinding {
    fun register(
        fragmentManager: FragmentManager,
        lifecycleOwner: LifecycleOwner,
        onChromeMode: (SurfaceChromeMode) -> Unit,
        onBack: () -> Unit
    ) {
        fragmentManager.setFragmentResultListener(
            TerminalSurfaceResultContract.REQUEST_KEY,
            lifecycleOwner
        ) { _, bundle ->
            when (val effect = TerminalSurfaceResultContract.parse(bundle)) {
                is SurfaceEffect.SetChromeMode -> onChromeMode(effect.mode)
                SurfaceEffect.RequestBack -> onBack()
                null -> Unit
            }
        }
    }
}
