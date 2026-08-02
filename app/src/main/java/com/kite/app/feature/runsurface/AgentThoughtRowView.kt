package com.kite.app.feature.runsurface

import android.content.Context
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextUtils
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.Gravity
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.kite.app.R
import com.kite.app.theme.ThemeTokens
import kotlin.math.roundToInt

/** ACP 思考事件的统一紧凑呈现；默认只占两行，和具体 Agent 无关。 */
internal class AgentThoughtRowView(
    context: Context,
    private val tokens: ThemeTokens,
) : LinearLayout(context) {
    private val content = TextView(context).apply {
        textSize = THOUGHT_TEXT_SIZE_SP
        includeFontPadding = false
        setLineSpacing(0f, THOUGHT_LINE_SPACING)
        setTextColor(tokens.textSecondary)
    }.also {
        addView(it, LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
    }
    private val chevron = ImageView(context).apply {
        setImageResource(R.drawable.ic_chevron_right_light)
        setColorFilter(tokens.textTertiary)
        scaleType = ImageView.ScaleType.CENTER_INSIDE
    }.also {
        addView(it, LayoutParams(dp(CHEVRON_SIZE_DP), dp(CHEVRON_SIZE_DP)).apply {
            marginStart = dp(6)
        })
    }

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        isClickable = true
        isFocusable = true
        setPadding(0, dp(5), 0, dp(6))
    }

    fun bind(
        thought: String,
        expanded: Boolean,
        onToggle: () -> Unit,
    ) {
        content.text = styledThought(thought.trim())
        content.maxLines = if (expanded) Int.MAX_VALUE else COLLAPSED_LINES
        content.ellipsize = if (expanded) null else TextUtils.TruncateAt.END
        chevron.rotation = if (expanded) 90f else 0f
        contentDescription = if (expanded) "收起推理过程" else "展开推理过程"
        setOnClickListener { onToggle() }
    }

    private fun styledThought(thought: String): CharSequence {
        val prefix = if (thought.isEmpty()) LABEL else "$LABEL  $SEPARATOR  "
        return SpannableStringBuilder(prefix).apply {
            setSpan(
                StyleSpan(Typeface.BOLD),
                0,
                prefix.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
            setSpan(
                ForegroundColorSpan(tokens.textPrimary),
                0,
                prefix.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
            if (thought.isNotEmpty()) {
                val start = length
                append(thought)
                setSpan(
                    ForegroundColorSpan(tokens.textSecondary),
                    start,
                    length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
            }
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

    private companion object {
        const val LABEL = "推理"
        const val SEPARATOR = "•"
        const val COLLAPSED_LINES = 2
        const val THOUGHT_TEXT_SIZE_SP = 13f
        const val THOUGHT_LINE_SPACING = 1.28f
        const val CHEVRON_SIZE_DP = 18
    }
}
