package com.kite.app.feature.resources

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.kite.app.run.CardRunHistoryEntry
import com.kite.app.run.CardRunStatus
import com.kite.app.theme.KiteTheme
import java.util.Calendar

internal class ResourceMoreScreen(
    private val context: Context,
    onBack: () -> Unit,
    private val onCreateHomeCard: () -> Unit,
    private val onOpenHistory: (String) -> Unit
) {
    private val tokens = ResourceFeatureTheme.tokens(context)
    private val factory = ResourceFeatureViewFactory(context, tokens, {}, {})
    private val content = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(22), dp(18), dp(22), dp(34))
    }
    private var signature: Int? = null

    val root: LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(tokens.pageBackground)
        addView(com.kite.app.ui.UiKit(context, tokens).topBar(context, "资源管理", onBack))
        addView(ScrollView(context).apply { addView(content) }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            0,
            1f
        ))
    }

    fun render(item: ResourceItemUiState?, history: List<CardRunHistoryEntry>) {
        val nextSignature = 31 * (item?.hashCode() ?: 0) + history.hashCode()
        if (signature == nextSignature) return
        signature = nextSignature
        content.removeAllViews()
        if (item == null) {
            content.addView(factory.stateBlock("正在读取资源", "稍后会显示资源管理选项。", loading = true))
            return
        }
        content.addView(header(item))
        content.addView(createHomeCardRow(item))
        content.addView(historyPanel(history))
    }

    private fun header(item: ResourceItemUiState): View = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(factory.icon(item, dp(48), dp(6), dp(14).toFloat(), 14f).apply {
            layoutParams = LinearLayout.LayoutParams(dp(48), dp(48)).apply { setMargins(0, 0, dp(12), 0) }
        })
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(context).apply {
                text = item.name
                textSize = 18f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(tokens.textPrimary)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            })
            addView(TextView(context).apply {
                text = item.descriptor.manifest?.description.orEmpty()
                textSize = 12.5f
                setTextColor(tokens.textSecondary)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                setPadding(0, dp(4), 0, 0)
            })
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
    }

    private fun createHomeCardRow(item: ResourceItemUiState): View {
        val manifest = item.descriptor.manifest
        val canCreate = manifest?.homeCards?.isNotEmpty() == true || manifest?.openRecipe != null
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), 0, dp(16), 0)
            alpha = if (canCreate) 1f else 0.56f
            background = factory.roundedBox(tokens.cardBackground, tokens.border, dp(18).toFloat())
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(68)).apply {
                setMargins(0, dp(22), 0, 0)
            }
            addView(TextView(context).apply {
                text = "+"
                textSize = 21f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                includeFontPadding = false
                setTextColor(tokens.primaryStrong)
                background = factory.roundedBox(tokens.primarySubtle, tokens.primarySoft, dp(14).toFloat())
            }, LinearLayout.LayoutParams(dp(42), dp(42)).apply { setMargins(0, 0, dp(14), 0) })
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(TextView(context).apply {
                    text = "创建首页卡片"
                    textSize = 15f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(tokens.textPrimary)
                })
                addView(TextView(context).apply {
                    text = if (canCreate) "把这个资源的打开卡片固定到首页" else "这个资源还没有可创建的首页模板"
                    textSize = 12f
                    setTextColor(tokens.textSecondary)
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                    setPadding(0, dp(4), 0, 0)
                })
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(TextView(context).apply {
                text = "›"
                textSize = 24f
                gravity = Gravity.CENTER
                setTextColor(tokens.textTertiary)
            }, LinearLayout.LayoutParams(dp(24), dp(42)))
            if (canCreate) setOnClickListener { onCreateHomeCard() }
        }
    }

    private fun historyPanel(history: List<CardRunHistoryEntry>): View = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(0, dp(18), 0, dp(4))
        addView(TextView(context).apply {
            text = "最近获取日志"
            textSize = 13.5f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(tokens.textPrimary)
            setPadding(0, 0, 0, dp(10))
        })
        if (history.isEmpty()) addView(emptyHistory()) else history.forEachIndexed { index, entry ->
            addView(historyRow(entry, index + 1))
        }
    }

    private fun emptyHistory(): View = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(12), dp(16), dp(12))
        background = factory.roundedBox(tokens.cardBackground, tokens.border, dp(18).toFloat())
        addView(TextView(context).apply {
            text = "还没有获取日志"
            textSize = 14.5f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(tokens.textPrimary)
        })
        addView(TextView(context).apply {
            text = "资源获取或失败后，这里会保留对应资源自己的步骤和 SH 报告。"
            textSize = 12f
            setTextColor(tokens.textSecondary)
            setPadding(0, dp(8), 0, 0)
        })
    }

    private fun historyRow(entry: CardRunHistoryEntry, ordinal: Int): View = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(14), dp(11), dp(12), dp(11))
        background = factory.roundedBox(tokens.cardBackground, tokens.border, dp(16).toFloat())
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(0, 0, 0, dp(8)) }
        setOnClickListener { onOpenHistory(entry.historyId) }
        addView(TextView(context).apply {
            text = ordinal.toString()
            textSize = 10.5f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            includeFontPadding = false
            setTextColor(Color.WHITE)
            background = factory.roundedBox(statusColor(entry), Color.TRANSPARENT, dp(11).toFloat())
        }, LinearLayout.LayoutParams(dp(22), dp(22)).apply { setMargins(0, 0, dp(11), 0) })
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(context).apply {
                text = "${entry.status.label} · ${duration(entry)} · ${progress(entry)}"
                textSize = 12.2f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(tokens.textPrimary)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            })
            addView(TextView(context).apply {
                text = timeline(entry)
                textSize = 10.5f
                setTextColor(tokens.textSecondary)
                setPadding(0, dp(3), 0, 0)
            })
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(TextView(context).apply {
            text = "›"
            textSize = 24f
            setTextColor(tokens.textTertiary)
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(dp(22), ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    private fun progress(entry: CardRunHistoryEntry): String {
        val total = entry.stepCount.takeIf { it > 0 } ?: entry.steps.size
        if (total <= 0) return "无步骤"
        val done = when {
            entry.status == CardRunStatus.Completed -> total
            entry.currentStepIndex < 0 -> 0
            entry.isClosed() -> (entry.currentStepIndex + 1).coerceIn(0, total)
            else -> entry.currentStepIndex.coerceIn(0, total - 1) + 1
        }
        return "步骤 $done/$total"
    }

    private fun duration(entry: CardRunHistoryEntry): String {
        val endAt = entry.endedAt ?: if (entry.isClosed()) entry.updatedAt else System.currentTimeMillis()
        val seconds = ((endAt - entry.startedAt).coerceAtLeast(0L) / 1000L)
        return if (seconds < 3600L) String.format("%02d:%02d", seconds / 60L, seconds % 60L)
        else if (seconds < 86400L) "${seconds / 3600L}小时" else "${seconds / 86400L}天"
    }

    private fun timeline(entry: CardRunHistoryEntry): String {
        val end = entry.endedAt ?: entry.updatedAt.takeIf { entry.isClosed() }
        return "开始 ${clock(entry.startedAt)} · ${end?.let { "结束 ${clock(it)}" } ?: "进行中"}"
    }

    private fun clock(timestamp: Long): String {
        val then = Calendar.getInstance().apply { timeInMillis = timestamp }
        return String.format("%02d:%02d", then.get(Calendar.HOUR_OF_DAY), then.get(Calendar.MINUTE))
    }

    private fun statusColor(entry: CardRunHistoryEntry): Int = when (entry.status) {
        CardRunStatus.Failed, CardRunStatus.BridgeUnavailable -> tokens.danger
        CardRunStatus.Completed -> tokens.success
        CardRunStatus.Stopped -> tokens.info
        CardRunStatus.Starting, CardRunStatus.Running, CardRunStatus.WaitingTerminal,
        CardRunStatus.AlreadyRunning, CardRunStatus.Opened -> tokens.primaryStrong
        else -> tokens.textSecondary
    }

    private fun dp(value: Int): Int = factory.dp(value)
}
