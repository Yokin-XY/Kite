package com.kite.app.feature.resources

import android.content.Context
import android.graphics.Typeface
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.kite.app.R
import com.kite.app.ui.UiKit
import org.json.JSONArray
import org.json.JSONObject

internal class ResourceRawJsonScreen(
    private val context: Context,
    onBack: () -> Unit
) {
    private val environment = ResourceFeatureTheme.environment(context)
    private val tokens = environment.tokens
    private val ui = UiKit(context, environment)
    private var signature: String? = null
    private val content = TextView(context).apply {
        textSize = 14f
        setTextColor(tokens.textPrimary)
        setPadding(ui.dp(24), ui.dp(20), ui.dp(24), ui.dp(28))
        typeface = Typeface.MONOSPACE
    }

    val root: LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(tokens.pageBackground)
        addView(ui.topBar(context, context.getString(R.string.resource_raw_json_title), onBack))
        addView(ScrollView(context).apply { addView(content) }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            0,
            1f
        ))
    }

    fun render(item: ResourceItemUiState?) {
        val next = item?.rawJsonForUi() ?: context.getString(R.string.resource_raw_json_loading)
        if (signature == next) return
        signature = next
        content.text = next
    }

    private fun ResourceItemUiState.rawJsonForUi(): String =
        descriptor.manifest?.rawJson?.toString(2) ?: JSONObject()
            .put("id", resourceId)
            .put("name", name)
            .put("steps", JSONArray())
            .toString(2)
}
