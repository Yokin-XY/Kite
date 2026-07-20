package com.kite.app.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.kite.app.theme.ThemeTokens

/**
 * 通用 UI 工具层(T7.0,ADR-016):把 MainActivity 的命令式 UI 工具收口到此处,
 * 供 Fragment 和 Activity 复用,避免每个 Fragment 复刻 dp/顶栏/按钮/配色。
 *
 * 用法:在 Fragment/Activity 里 `val ui = UiKit(requireContext(), tokens)`,
 * 然后 `ui.topBar(...)`, `ui.dp(...)`, `ui.row { ... }`。
 *
 * 设计原则:
 * - 不持有 Activity 引用,只持有 Context(用 applicationContext 避免泄漏)。
 * - tokens 由调用方传入(KiteTheme.resolve 的结果),UiKit 不耦合主题加载逻辑。
 * - 这是从 MainActivity 抽出的第一批公共 UI 工具,后续按需扩充。
 */
class UiKit(
    context: Context,
    private val tokens: ThemeTokens
) {
    private val density = context.resources.displayMetrics.density

    /** dp → px 转换。 */
    fun dp(value: Int): Int = (value * density).toInt()

    /** 水平居中的行容器(LinearLayout HORIZONTAL)。 */
    fun rowWith(context: Context, content: LinearLayout.() -> Unit): LinearLayout =
        LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            content()
        }

    /** 圆角矩形背景。 */
    fun roundedBox(
        fill: Int,
        stroke: Int,
        radius: Float,
        strokeWidth: Int = dp(1),
        dashWidth: Float = 0f,
        dashGap: Float = 0f
    ): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
            setColor(fill)
            if (stroke != Color.TRANSPARENT) {
                if (dashWidth > 0f && dashGap > 0f) {
                    setStroke(strokeWidth, stroke, dashWidth, dashGap)
                } else {
                    setStroke(strokeWidth, stroke)
                }
            }
        }

    /** 图标/文字按钮。 */
    fun iconButton(
        context: Context,
        text: String,
        size: Int,
        fill: Int,
        textColor: Int,
        radius: Int,
        onClick: () -> Unit
    ): TextView = TextView(context).apply {
        this.text = text
        textSize = when (text) {
            "+" -> 38f
            "⌕" -> 37f
            "保存" -> 13f
            else -> 24f
        }
        gravity = Gravity.CENTER
        typeface = Typeface.DEFAULT_BOLD
        includeFontPadding = false
        setTextColor(textColor)
        background = roundedBox(fill, fill, radius.toFloat())
        if (fill != Color.TRANSPARENT) elevation = dp(4).toFloat()
        layoutParams = LinearLayout.LayoutParams(size, size)
        setOnClickListener { onClick() }
    }

    /** 标准顶栏:返回按钮 + 居中标题 + 右侧占位。 */
    fun topBar(context: Context, title: String, onBack: () -> Unit): View = rowWith(context) {
        setPadding(dp(18), dp(14), dp(18), dp(10))
        addView(iconButton(context, "‹", dp(44), Color.TRANSPARENT, tokens.textPrimary, dp(16)) { onBack() })
        addView(TextView(context).apply {
            text = title
            textSize = 22f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(tokens.textPrimary)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        addView(View(context), LinearLayout.LayoutParams(dp(44), dp(44)))
    }
}
