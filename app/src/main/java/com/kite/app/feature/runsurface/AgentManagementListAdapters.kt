package com.kite.app.feature.runsurface

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.kite.app.R
import com.kite.app.agent.config.AgentMcpConnectionState
import com.kite.app.agent.config.AgentMcpSummary
import com.kite.app.agent.config.AgentSkillSummary
import com.kite.app.theme.ThemeTokens
import com.kite.app.ui.UiKit

internal data class AgentMcpListItem(
    val server: AgentMcpSummary,
    val connectionState: AgentMcpConnectionState,
    val connectionMessage: String?,
    val pending: Boolean,
)

/** MCP 管理列表只负责呈现 SDK 已给出的状态和用户意图。 */
internal class AgentMcpListAdapter(
    private val context: Context,
    private val tokens: ThemeTokens,
    private val onClick: (AgentMcpListItem) -> Unit,
    private val onConnectionCheck: (AgentMcpListItem) -> Unit,
) : ListAdapter<AgentMcpListItem, AgentMcpListAdapter.Holder>(Diff) {
    private val ui = UiKit(context, tokens)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val title = TextView(context)
        val summary = TextView(context)
        val detail = TextView(context)
        val checkAction = TextView(context)
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(ui.dp(15), 0, ui.dp(9), 0)
            background = ui.roundedBox(
                tokens.cardBackground,
                android.graphics.Color.TRANSPARENT,
                ui.dp(21).toFloat(),
            )
            addView(ImageView(context).apply {
                setImageResource(R.drawable.ic_mcp_light)
                imageTintList = ColorStateList.valueOf(tokens.textSecondary)
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                setPadding(ui.dp(9), ui.dp(9), ui.dp(9), ui.dp(9))
                background = ui.roundedBox(
                    tokens.inputBackground,
                    android.graphics.Color.TRANSPARENT,
                    ui.dp(20).toFloat(),
                )
            }, LinearLayout.LayoutParams(ui.dp(40), ui.dp(40)))
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_VERTICAL
                addView(LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    addView(title.apply {
                        textSize = 14.5f
                        typeface = Typeface.DEFAULT_BOLD
                        maxLines = 1
                        ellipsize = TextUtils.TruncateAt.END
                        setTextColor(tokens.textPrimary)
                    }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                    addView(summary.apply {
                        textSize = 11.5f
                        maxLines = 1
                        setTextColor(tokens.textSecondary)
                        setPadding(ui.dp(8), 0, 0, 0)
                    })
                })
                addView(detail.apply {
                    textSize = 12f
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                    setTextColor(tokens.textSecondary)
                    setPadding(0, ui.dp(3), 0, 0)
                })
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(ui.dp(13), 0, ui.dp(4), 0)
            })
            addView(checkAction.apply {
                text = "检查"
                textSize = 12.5f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setTextColor(tokens.primaryStrong)
                setPadding(ui.dp(8), ui.dp(7), ui.dp(8), ui.dp(7))
                isClickable = true
                isFocusable = true
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ui.dp(40)))
            addView(ImageView(context).apply {
                setImageResource(R.drawable.ic_chevron_right_light)
                imageTintList = ColorStateList.valueOf(tokens.textTertiary)
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                contentDescription = null
            }, LinearLayout.LayoutParams(ui.dp(40), ui.dp(40)))
            isClickable = true
            isFocusable = true
        }
        row.layoutParams = RecyclerView.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ui.dp(74),
        ).apply { setMargins(0, ui.dp(4), 0, ui.dp(4)) }
        return Holder(row, title, summary, detail, checkAction)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val item = getItem(position)
        val server = item.server
        holder.title.text = server.id
        holder.summary.text = AgentMcpUiPolicy.transportLabel(server)
        holder.detail.text = if (item.pending) {
            "正在更新…"
        } else {
            val status = AgentMcpUiPolicy.connectionLabel(server, item.connectionState)
            item.connectionMessage?.let { "$status · $it" } ?: status
        }
        holder.itemView.isEnabled = !item.pending
        holder.itemView.alpha = if (item.pending) 0.68f else 1f
        holder.itemView.contentDescription = "${server.id}，${holder.summary.text}，${holder.detail.text}"
        holder.itemView.setOnClickListener { if (!item.pending) onClick(item) }
        holder.checkAction.visibility = if (
            !item.pending && AgentMcpUiPolicy.supportsConnectionCheck(server)
        ) View.VISIBLE else View.GONE
        holder.checkAction.contentDescription = "检查 ${server.id} 的连接"
        holder.checkAction.setOnClickListener {
            if (!item.pending && AgentMcpUiPolicy.supportsConnectionCheck(server)) onConnectionCheck(item)
        }
    }

    internal class Holder(
        itemView: View,
        val title: TextView,
        val summary: TextView,
        val detail: TextView,
        val checkAction: TextView,
    ) : RecyclerView.ViewHolder(itemView)

    private object Diff : DiffUtil.ItemCallback<AgentMcpListItem>() {
        override fun areItemsTheSame(oldItem: AgentMcpListItem, newItem: AgentMcpListItem): Boolean =
            oldItem.server.id == newItem.server.id

        override fun areContentsTheSame(oldItem: AgentMcpListItem, newItem: AgentMcpListItem): Boolean =
            oldItem == newItem
    }
}

/** Skill 管理列表只显示统一摘要，不判断具体 Agent 的 Skill 规则。 */
internal class AgentSkillListAdapter(
    private val context: Context,
    private val tokens: ThemeTokens,
    private val onClick: (AgentSkillSummary) -> Unit,
) : ListAdapter<AgentSkillSummary, AgentSkillListAdapter.Holder>(Diff) {
    private val ui = UiKit(context, tokens)
    private var pendingSkillId: String? = null

    fun submit(skills: List<AgentSkillSummary>, pendingSkillId: String?) {
        this.pendingSkillId = pendingSkillId
        submitList(skills.sortedBy { it.displayName.lowercase() }) {
            notifyDataSetChanged()
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val title = TextView(context)
        val summary = TextView(context)
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(ui.dp(15), 0, ui.dp(9), 0)
            background = ui.roundedBox(
                tokens.cardBackground,
                android.graphics.Color.TRANSPARENT,
                ui.dp(21).toFloat(),
            )
            addView(ImageView(context).apply {
                setImageResource(R.drawable.ic_skill_light)
                imageTintList = ColorStateList.valueOf(tokens.textSecondary)
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                setPadding(ui.dp(10), ui.dp(10), ui.dp(10), ui.dp(10))
                background = ui.roundedBox(
                    tokens.inputBackground,
                    android.graphics.Color.TRANSPARENT,
                    ui.dp(20).toFloat(),
                )
            }, LinearLayout.LayoutParams(ui.dp(40), ui.dp(40)))
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_VERTICAL
                addView(title.apply {
                    textSize = 14.5f
                    typeface = Typeface.DEFAULT_BOLD
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                    setTextColor(tokens.textPrimary)
                })
                addView(summary.apply {
                    textSize = 12f
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                    setTextColor(tokens.textSecondary)
                    setPadding(0, ui.dp(3), 0, 0)
                })
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(ui.dp(13), 0, ui.dp(4), 0)
            })
            addView(ImageView(context).apply {
                setImageResource(R.drawable.ic_chevron_right_light)
                imageTintList = ColorStateList.valueOf(tokens.textTertiary)
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                contentDescription = null
            }, LinearLayout.LayoutParams(ui.dp(40), ui.dp(40)))
            isClickable = true
            isFocusable = true
        }
        row.layoutParams = RecyclerView.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ui.dp(70),
        ).apply { setMargins(0, ui.dp(4), 0, ui.dp(4)) }
        return Holder(row, title, summary)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val skill = getItem(position)
        val pending = pendingSkillId == skill.id
        val operationPending = pendingSkillId != null
        holder.title.text = skill.displayName
        holder.summary.text = if (pending) "正在更新…" else AgentSkillUiPolicy.summary(skill)
        holder.itemView.isEnabled = !operationPending
        holder.itemView.alpha = if (operationPending && !pending) 0.55f else 1f
        holder.itemView.contentDescription = "${skill.displayName}，${holder.summary.text}"
        holder.itemView.setOnClickListener { if (!operationPending) onClick(skill) }
    }

    internal class Holder(
        itemView: View,
        val title: TextView,
        val summary: TextView,
    ) : RecyclerView.ViewHolder(itemView)

    private object Diff : DiffUtil.ItemCallback<AgentSkillSummary>() {
        override fun areItemsTheSame(oldItem: AgentSkillSummary, newItem: AgentSkillSummary): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: AgentSkillSummary, newItem: AgentSkillSummary): Boolean =
            oldItem == newItem
    }
}
