package com.kite.app.feature.recipeeditor

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.kite.app.theme.ThemeTokens
import com.kite.app.ui.UiKit

internal class RecipeRawJsonScreen(
    context: Context,
    tokens: ThemeTokens,
    onBack: () -> Unit
) {
    private val ui = UiKit(context, tokens)
    private val content = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }

    val root: View = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(Color.parseColor("#0F1115"))
        addView(ui.topBar(context, "原始 JSON", onBack))
        addView(ScrollView(context).apply { addView(content) }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            0,
            1f
        ))
    }

    fun renderLoading() = renderText("正在读取配方…", monospace = false)

    fun renderJson(json: String) = renderText(json, monospace = true)

    fun renderError(recipeKey: String) = renderText(
        "无法加载配方（recipe=$recipeKey）",
        monospace = false
    )

    private fun renderText(value: String, monospace: Boolean) {
        content.removeAllViews()
        content.addView(TextView(content.context).apply {
            text = value
            textSize = 13f
            setTextColor(Color.parseColor("#C8CDD6"))
            setPadding(ui.dp(24), ui.dp(20), ui.dp(24), ui.dp(28))
            typeface = if (monospace) Typeface.MONOSPACE else Typeface.DEFAULT
        })
    }
}
