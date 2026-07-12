package com.kite.app.feature.runsurface

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.kite.app.run.KiteX11SurfacePlan
import com.kite.app.run.KiteX11SurfaceServer
import com.kite.app.theme.ThemeTokens
import com.kite.app.ui.UiKit

/** X11 绑定只拥有可见 LorieView，不停止或重启对应运行实例。 */
internal class RunX11SurfaceBinding(
    context: Context,
    private val tokens: ThemeTokens
) : RunSurfaceBinding {
    private val ui = UiKit(context, tokens)
    private var renderedKey = ""

    override val root: FrameLayout = FrameLayout(context).apply {
        setBackgroundColor(Color.BLACK)
    }

    override fun render(state: RunSurfaceUiState) {
        val content = state.content as? RunSurfaceContent.X11 ?: return
        val key = "${content.display.orEmpty()}|${content.socketPath.orEmpty()}"
        if (key == renderedKey) return
        renderedKey = key
        root.removeAllViews()

        val display = content.display?.takeIf { it.isNotBlank() }
        if (display == null) {
            root.addView(
                placeholder(state.title, "DISPLAY=待分配"),
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.TOP
                ).apply { setMargins(ui.dp(16), ui.dp(16), ui.dp(16), 0) }
            )
            return
        }

        val binding = KiteX11SurfacePlan.binding(display)
        runCatching { KiteX11SurfaceServer.surfaceView(root.context, binding) }
            .onSuccess { surface ->
                root.addView(
                    surface,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                )
            }
            .onFailure { error ->
                root.addView(
                    placeholder(
                        state.title,
                        listOf(
                            "DISPLAY=${binding.display}",
                            "socket=${binding.socketPath}",
                            "native X11 启动失败：${error.message.orEmpty()}"
                        ).joinToString("\n")
                    ),
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        Gravity.TOP
                    ).apply { setMargins(ui.dp(16), ui.dp(16), ui.dp(16), 0) }
                )
            }
    }

    override fun dispose() {
        renderedKey = ""
        root.removeAllViews()
    }

    private fun placeholder(title: String, detail: String): View = LinearLayout(root.context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(ui.dp(16), ui.dp(15), ui.dp(16), ui.dp(15))
        background = ui.roundedBox(tokens.surfaceElevated, tokens.border, ui.dp(8).toFloat())
        addView(TextView(context).apply {
            text = title.ifBlank { "X11" }
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(tokens.textPrimary)
        })
        addView(TextView(context).apply {
            text = detail
            textSize = 12.5f
            setTextColor(tokens.textSecondary)
            setPadding(0, ui.dp(10), 0, 0)
        })
    }
}
