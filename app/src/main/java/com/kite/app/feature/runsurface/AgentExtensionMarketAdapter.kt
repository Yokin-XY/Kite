package com.kite.app.feature.runsurface

import android.content.Context
import android.graphics.Typeface
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.kite.app.agent.market.AgentExtensionMarketItem
import com.kite.app.theme.ThemeTokens
import com.kite.app.ui.UiKit
import java.util.Locale

/** 市场列表只展示来源给出的候选；安装能力由当前 Agent 的统一配置层判断。 */
internal class AgentExtensionMarketAdapter(
    context: Context,
    private val tokens: ThemeTokens,
    private val onClick: (AgentExtensionMarketItem) -> Unit,
) : ListAdapter<AgentExtensionMarketItem, AgentExtensionMarketAdapter.Holder>(Diff) {
    private val ui = UiKit(context, tokens)
    private val appContext = context

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val title = TextView(appContext)
        val source = TextView(appContext)
        val description = TextView(appContext)
        val row = LinearLayout(appContext).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(ui.dp(16), ui.dp(12), ui.dp(16), ui.dp(12))
            background = ui.roundedBox(
                tokens.cardBackground,
                android.graphics.Color.TRANSPARENT,
                ui.dp(20).toFloat(),
            )
            addView(title.apply {
                textSize = 14.5f
                typeface = Typeface.DEFAULT_BOLD
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                setTextColor(tokens.textPrimary)
            })
            addView(source.apply {
                textSize = 11.5f
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                setTextColor(tokens.textTertiary)
                setPadding(0, ui.dp(4), 0, 0)
            })
            addView(description.apply {
                textSize = 12.5f
                maxLines = 2
                ellipsize = TextUtils.TruncateAt.END
                setLineSpacing(ui.dp(2).toFloat(), 1f)
                setTextColor(tokens.textSecondary)
                setPadding(0, ui.dp(5), 0, 0)
            })
            isClickable = true
            isFocusable = true
        }
        row.layoutParams = RecyclerView.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { setMargins(0, ui.dp(4), 0, ui.dp(4)) }
        return Holder(row, title, source, description)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val item = getItem(position)
        holder.title.text = item.title
        holder.source.text = buildList {
            add(item.sourceLabel)
            item.versionLabel?.let { add(if (it.startsWith("v", ignoreCase = true)) it else "v$it") }
            item.downloads?.let { add("${formatCount(it)} 下载") }
            item.stars?.takeIf { it > 0 }?.let { add("${formatCount(it)} 收藏") }
        }.joinToString(" · ")
        holder.description.text = item.description.ifBlank { "没有提供说明" }
        holder.itemView.contentDescription = "${item.title}，${holder.source.text}，${holder.description.text}"
        holder.itemView.setOnClickListener { onClick(item) }
    }

    internal class Holder(
        itemView: View,
        val title: TextView,
        val source: TextView,
        val description: TextView,
    ) : RecyclerView.ViewHolder(itemView)

    private object Diff : DiffUtil.ItemCallback<AgentExtensionMarketItem>() {
        override fun areItemsTheSame(oldItem: AgentExtensionMarketItem, newItem: AgentExtensionMarketItem): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: AgentExtensionMarketItem, newItem: AgentExtensionMarketItem): Boolean =
            oldItem == newItem
    }

    private companion object {
        fun formatCount(value: Long): String = when {
            value < 10_000L -> value.toString()
            value < 100_000_000L ->
                "${String.format(Locale.ROOT, "%.1f", value / 10_000.0).removeSuffix(".0")}万"
            else ->
                "${String.format(Locale.ROOT, "%.1f", value / 100_000_000.0).removeSuffix(".0")}亿"
        }
    }
}
