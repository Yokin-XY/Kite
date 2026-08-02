package com.kite.app.feature.runsurface

import android.graphics.Typeface
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.kite.app.agent.sdk.configuration.AgentControlCatalog
import com.kite.app.theme.ThemeTokens
import com.kite.app.ui.UiKit

/**
 * 会话输入区固定的模型与权限组件。
 *
 * 它只消费 Kite Agent SDK 的类型化目录，不接触 AgentConfigAdapter、原生 ID 规则或产品名称。
 * 推理强度继续位于模型配置弹层中，与这里消费同一份 SDK 目录。
 */
internal class AgentFixedSessionControlStrip(
    context: android.content.Context,
    private val tokens: ThemeTokens,
    private val onModelClick: () -> Unit,
    private val onPermissionClick: () -> Unit,
) {
    private val ui = UiKit(context, tokens)
    private val modelHost = host(context)
    private val permissionHost = host(context)
    private val modelEntry = entry(context, "模型", ui.dp(118), onModelClick)
    private val permissionEntry = entry(context, "权限", ui.dp(104), onPermissionClick)

    val view: View = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(modelHost, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ui.dp(34)).also {
            modelHost.addView(modelEntry, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ui.dp(34)))
        })
        addView(permissionHost, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ui.dp(34)).apply {
            marginStart = ui.dp(5)
        }.also {
            permissionHost.addView(
                permissionEntry,
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ui.dp(34)),
            )
        })
    }

    fun render(catalog: AgentControlCatalog, pending: Boolean) {
        val model = catalog.model
        modelHost.visibility = if (model == null) View.GONE else View.VISIBLE
        if (model != null) {
            val label = model.choices.firstOrNull {
                it.selection.nativeValue == model.current.nativeValue
            }?.displayName ?: "模型"
            val style = AgentSurfaceNavigationPolicy.composerModelTextStyle(label)
            bind(
                modelEntry,
                label,
                "选择模型，当前$label",
                pending || model.choices.size <= 1,
                ui.dp(style.maximumWidthDp),
                style.textSizeSp,
            )
        }

        val permission = catalog.permission
        permissionHost.visibility = if (permission == null) View.GONE else View.VISIBLE
        if (permission != null) {
            val label = permission.choices.firstOrNull {
                it.profileId == permission.currentProfileId
            }?.level?.displayName ?: "权限"
            bind(
                permissionEntry,
                label,
                "选择权限，当前$label",
                pending || permission.choices.size <= 1,
                ui.dp(104),
            )
        }
        view.visibility = if (model == null && permission == null) View.GONE else View.VISIBLE
    }

    internal fun identities(): Pair<Int, Int> =
        System.identityHashCode(modelEntry) to System.identityHashCode(permissionEntry)

    internal fun childCounts(): Pair<Int, Int> = modelHost.childCount to permissionHost.childCount

    private fun host(context: android.content.Context) = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }

    private fun entry(
        context: android.content.Context,
        label: String,
        maximumWidth: Int,
        onClick: () -> Unit,
    ): TextView = TextView(context).apply {
        text = label
        textSize = 13f
        typeface = Typeface.DEFAULT_BOLD
        minWidth = ui.dp(58)
        maxWidth = maximumWidth
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
        gravity = Gravity.CENTER
        setTextColor(tokens.textPrimary)
        setPadding(ui.dp(13), 0, ui.dp(13), 0)
        background = ui.roundedBox(
            tokens.cardBackground,
            android.graphics.Color.TRANSPARENT,
            ui.dp(18).toFloat(),
        )
        isClickable = true
        isFocusable = true
        setOnClickListener { onClick() }
    }

    private fun bind(
        view: TextView,
        label: String,
        description: String,
        disabled: Boolean,
        maximumWidth: Int,
        textSizeSp: Float = 13f,
    ) {
        if (!TextUtils.equals(view.text, label)) view.text = label
        val expectedTextSizePx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            textSizeSp,
            view.resources.displayMetrics,
        )
        if (view.textSize != expectedTextSizePx) view.textSize = textSizeSp
        if (view.maxWidth != maximumWidth) view.maxWidth = maximumWidth
        if (view.contentDescription != description) view.contentDescription = description
        if (view.isClickable == disabled) view.isClickable = !disabled
        val nextAlpha = if (disabled) 0.55f else 1f
        if (view.alpha != nextAlpha) view.alpha = nextAlpha
    }
}
