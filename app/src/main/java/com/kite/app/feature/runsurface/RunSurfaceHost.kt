package com.kite.app.feature.runsurface

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.kite.app.theme.ThemeTokens

/** 当前显示面的可选工具栏；正文、输入区和主流程动作不属于这个合同。 */
internal interface RunSurfaceToolbarOwner {
    fun setSurfaceToolbarVisible(visible: Boolean): Boolean = false

    fun toggleSurfaceToolbar(): Boolean = false
}

internal interface RunSurfaceBinding : RunSurfaceToolbarOwner {
    val root: View

    fun render(state: RunSurfaceUiState)

    fun tick(now: Long): Boolean = false

    fun handleBack(): Boolean = false

    fun reload(): Boolean = false

    fun goForward(): Boolean = false

    fun stopLoading(): Boolean = false

    fun reconcile(): Boolean = false

    fun dispose() = Unit
}

internal class StaticRunSurfaceBinding(
    override val root: View
) : RunSurfaceBinding {
    override fun render(state: RunSurfaceUiState) = Unit
}

/**
 * 运行窗口的显示生命周期拥有者。它只替换显示绑定，不停止或推进底层任务。
 */
internal class RunSurfaceHost(
    context: Context,
    tokens: ThemeTokens
) {
    private val contentHost = FrameLayout(context)
    private val overlayHost = FrameLayout(context)
    private var structureKey = ""
    private var binding: RunSurfaceBinding? = null

    private val reportFactory: () -> RunSurfaceBinding = {
        RunReportScreen(
            context = context,
            tokens = tokens
        )
    }

    val root: FrameLayout = FrameLayout(context).apply {
        addView(
            contentHost,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        addView(
            overlayHost,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
    }

    fun render(
        state: RunSurfaceUiState,
        externalFactory: (RunSurfaceUiState) -> RunSurfaceBinding
    ): Boolean {
        val changed = state.structureKey != structureKey || binding == null
        if (changed) {
            binding?.dispose()
            contentHost.removeAllViews()
            val next = when (state.content) {
                is RunSurfaceContent.Report -> reportFactory()
                else -> externalFactory(state)
            }
            binding = next
            structureKey = state.structureKey
            contentHost.addView(
                next.root,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
        }
        binding?.render(state)
        return changed
    }

    fun setOverlay(view: View?) {
        overlayHost.removeAllViews()
        if (view != null) {
            overlayHost.addView(
                view,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
        }
    }

    fun tick(now: Long = System.currentTimeMillis()): Boolean = binding?.tick(now) == true

    fun handleBack(): Boolean = binding?.handleBack() == true

    fun reload(): Boolean = binding?.reload() == true

    fun goForward(): Boolean = binding?.goForward() == true

    fun stopLoading(): Boolean = binding?.stopLoading() == true

    fun setSurfaceToolbarVisible(visible: Boolean): Boolean =
        binding?.setSurfaceToolbarVisible(visible) == true

    fun toggleSurfaceToolbar(): Boolean = binding?.toggleSurfaceToolbar() == true

    fun reconcile(): Boolean = binding?.reconcile() == true

    fun dispose() {
        binding?.dispose()
        binding = null
        structureKey = ""
        contentHost.removeAllViews()
        overlayHost.removeAllViews()
    }
}
