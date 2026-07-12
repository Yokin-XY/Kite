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
import com.kite.app.theme.ThemeTokens
import com.kite.app.ui.UiKit

/** 运行窗口的轻量控制层；只提交用户动作，不解释运行结果。 */
internal class RunActivityChrome(
    context: Context,
    private val tokens: ThemeTokens,
    private val onComplete: () -> Unit,
    private val onStop: () -> Unit,
    private val onReload: () -> Unit,
    private val onClose: () -> Unit
) {
    private val ui = UiKit(context, tokens)
    private val panel: LinearLayout
    private val completeButton: TextView
    private val stopButton: TextView
    private val reloadButton: TextView
    private var autoOpenedKey = ""

    val root: FrameLayout = FrameLayout(context).apply {
        isClickable = false
        isFocusable = false
    }

    init {
        completeButton = action("继续", onComplete)
        stopButton = action("停止", onStop, danger = true)
        reloadButton = action("刷新", onReload)
        panel = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            visibility = View.GONE
            elevation = ui.dp(6).toFloat()
            isClickable = true
            setPadding(ui.dp(5), ui.dp(4), ui.dp(5), ui.dp(4))
            background = ui.roundedBox(
                Color.argb(242, 255, 255, 255),
                Color.argb(52, 100, 116, 139),
                ui.dp(8).toFloat(),
                ui.dp(1)
            )
            addView(reloadButton, actionParams())
            addView(completeButton, actionParams())
            addView(stopButton, actionParams())
            addView(action("关闭", onClose), actionParams())
        }
        root.addView(
            panel,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ui.dp(46), Gravity.TOP or Gravity.RIGHT).apply {
                setMargins(0, ui.dp(12), ui.dp(12), 0)
            }
        )
        root.addView(
            action("", { toggle() }).apply {
                contentDescription = "运行窗口控制"
                background = ui.roundedBox(
                    Color.argb(176, 255, 255, 255),
                    Color.argb(56, 100, 116, 139),
                    ui.dp(3).toFloat(),
                    ui.dp(1)
                )
            },
            FrameLayout.LayoutParams(ui.dp(8), ui.dp(54), Gravity.RIGHT or Gravity.CENTER_VERTICAL).apply {
                setMargins(0, 0, ui.dp(4), 0)
            }
        )
    }

    fun render(state: RunSurfaceUiState) {
        completeButton.visibility = if (state.canCompleteCurrentStep) View.VISIBLE else View.GONE
        stopButton.visibility = if (state.canStop) View.VISIBLE else View.GONE
        reloadButton.visibility = if (state.content is RunSurfaceContent.Web) View.VISIBLE else View.GONE
        val key = "${state.target.instanceId}:${state.currentStepIndex}:${state.canCompleteCurrentStep}"
        if (state.canCompleteCurrentStep && autoOpenedKey != key) {
            autoOpenedKey = key
            panel.visibility = View.VISIBLE
        }
    }

    fun setChromeVisible(visible: Boolean) {
        root.visibility = if (visible) View.VISIBLE else View.GONE
        if (!visible) panel.visibility = View.GONE
    }

    fun dispose() {
        root.removeAllViews()
    }

    private fun toggle() {
        panel.visibility = if (panel.visibility == View.VISIBLE) View.GONE else View.VISIBLE
    }

    private fun action(label: String, action: () -> Unit, danger: Boolean = false): TextView =
        TextView(root.context).apply {
            text = label
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            includeFontPadding = false
            isClickable = true
            isFocusable = true
            setTextColor(if (danger) tokens.danger else tokens.textPrimary)
            setOnClickListener { action() }
        }

    private fun actionParams(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(ui.dp(56), ViewGroup.LayoutParams.MATCH_PARENT)
}
