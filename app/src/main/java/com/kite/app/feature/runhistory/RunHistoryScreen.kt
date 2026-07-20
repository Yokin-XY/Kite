package com.kite.app.feature.runhistory

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextUtils
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.kite.app.recipe.KiteRecipe
import com.kite.app.run.CardRunHistoryEntry
import com.kite.app.run.CardRunHistoryStep
import com.kite.app.run.CardRunStatus
import com.kite.app.theme.KiteTheme
import com.kite.app.theme.ThemeSelection
import com.kite.app.theme.ThemeTokens
import com.kite.app.ui.theme.isSystemDarkTheme
import com.kite.app.ui.UiKit
import java.util.Calendar

internal class RunHistoryScreen(
    private val context: Context,
    theme: ThemeSelection,
    private val listTitle: String,
    private val emptyTitle: String,
    private val emptyDetail: String,
    private val onBack: () -> Unit,
    private val onOpenEntry: (String) -> Unit,
    private val onOpenReport: (Int) -> Unit
) {
    private val tokens: ThemeTokens = KiteTheme.resolve(
        theme,
        context.isSystemDarkTheme(),
    ).tokens
    private val ui = UiKit(context, tokens)
    private var renderSignature: Int? = null

    val root: LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(tokens.pageBackground)
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
    }

    fun render(state: RunHistoryUiState) {
        val signature = state.hashCode()
        if (renderSignature == signature) return
        renderSignature = signature
        root.removeAllViews()
        when (state.page) {
            RunHistoryPage.List -> renderList(state)
            RunHistoryPage.Detail -> state.selectedEntry?.let(::renderDetail) ?: renderList(state)
            RunHistoryPage.Report -> state.selectedStep?.let(::renderReport)
                ?: state.selectedEntry?.let(::renderDetail)
                ?: renderList(state)
        }
    }

    private fun renderList(state: RunHistoryUiState) {
        root.addView(ui.topBar(context, listTitle, onBack))
        root.addView(scrollBody {
            addView(label("最近运行"))
            if (state.entries.isEmpty()) {
                addView(emptyBlock())
            } else {
                state.entries.forEachIndexed { index, entry ->
                    addView(previewRow(entry, index + 1))
                }
            }
        }, weighted())
    }

    private fun renderDetail(entry: CardRunHistoryEntry) {
        root.addView(ui.topBar(context, "运行详情", onBack))
        root.addView(scrollBody {
            addView(detailHeader(entry))
            addView(label("流程快照", top = 20))
            if (entry.steps.isEmpty()) {
                addView(emptyBlock())
            } else {
                entry.steps.forEach { step -> addView(stepRow(entry, step)) }
            }
        }, weighted())
    }

    private fun renderReport(step: CardRunHistoryStep) {
        root.addView(ui.topBar(context, "历史 SH 报告", onBack))
        root.addView(scrollBody {
            addView(TextView(context).apply {
                text = "步骤 ${step.index + 1} · ${step.label}"
                textSize = 13f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(tokens.textPrimary)
            })
            addView(TextView(context).apply {
                text = stepDetail(step)
                textSize = 11.2f
                setTextColor(tokens.textSecondary)
                maxLines = 2
                ellipsize = TextUtils.TruncateAt.END
                setPadding(0, dp(5), 0, dp(12))
            })
            addView(readonlyReport(step))
        }, weighted())
    }

    private fun scrollBody(content: LinearLayout.() -> Unit): ScrollView = ScrollView(context).apply {
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(18), dp(24), dp(28))
            content()
        })
    }

    private fun label(text: String, top: Int = 0): TextView = TextView(context).apply {
        this.text = text
        textSize = 13.5f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(tokens.textPrimary)
        setPadding(0, dp(top), 0, dp(10))
    }

    private fun emptyBlock(): View = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(12), dp(16), dp(12))
        background = rounded(tokens.cardBackground, tokens.border, 18)
        addView(TextView(context).apply {
            text = emptyTitle
            textSize = 14.5f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(tokens.textPrimary)
        })
        addView(TextView(context).apply {
            text = emptyDetail
            textSize = 12f
            setTextColor(tokens.textSecondary)
            setPadding(0, dp(8), 0, 0)
        })
    }

    private fun previewRow(entry: CardRunHistoryEntry, ordinal: Int): View =
        LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(11), dp(12), dp(11))
            background = rounded(tokens.cardBackground, tokens.border, 16)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, dp(8)) }
            setOnClickListener { onOpenEntry(entry.historyId) }
            addView(TextView(context).apply {
                text = ordinal.toString()
                textSize = 10.5f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                includeFontPadding = false
                setTextColor(Color.WHITE)
                background = rounded(statusColor(entry), Color.TRANSPARENT, 11)
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
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
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

    private fun detailHeader(entry: CardRunHistoryEntry): View = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(14), dp(16), dp(14))
        background = rounded(tokens.cardBackground, tokens.border, 18)
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(TextView(context).apply {
                text = "本次运行"
                textSize = 14.5f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(tokens.textPrimary)
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(TextView(context).apply {
                text = entry.status.label
                textSize = 10.5f
                typeface = Typeface.DEFAULT_BOLD
                includeFontPadding = false
                gravity = Gravity.CENTER
                setTextColor(statusColor(entry))
                background = rounded(KiteTheme.tint(statusColor(entry)), Color.TRANSPARENT, 10)
                setPadding(dp(9), dp(5), dp(9), dp(5))
            })
        })
        addView(TextView(context).apply {
            text = "${timeline(entry)} · ${duration(entry)} · ${progress(entry)}"
            textSize = 11.5f
            setTextColor(tokens.textSecondary)
            setPadding(0, dp(7), 0, 0)
        })
        entry.error.ifBlank { entry.summary }.takeIf(String::isNotBlank)?.let { message ->
            addView(TextView(context).apply {
                text = message.take(160)
                textSize = 11.2f
                setTextColor(if (entry.error.isNotBlank()) tokens.danger else tokens.textSecondary)
                maxLines = 2
                ellipsize = TextUtils.TruncateAt.END
                setPadding(0, dp(8), 0, 0)
            })
        }
        if (entry.steps.isNotEmpty()) {
            addView(TextView(context).apply {
                text = "步骤快照来自本次运行，不会随当前卡片编排修改而改变。"
                textSize = 10.5f
                setTextColor(tokens.textTertiary)
                setPadding(0, dp(9), 0, 0)
            })
        }
    }

    private fun stepRow(entry: CardRunHistoryEntry, step: CardRunHistoryStep): View =
        LinearLayout(context).apply {
            val canOpenReport = step.type == KiteRecipe.STEP_SHELL && step.reportText.isNotBlank()
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.TOP
            setPadding(dp(14), dp(10), dp(14), dp(10))
            background = rounded(tokens.cardBackground, tokens.border, 16)
            isClickable = canOpenReport
            if (canOpenReport) setOnClickListener { onOpenReport(step.index) }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, dp(8)) }
            addView(TextView(context).apply {
                text = "${step.index + 1}"
                textSize = 10f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                includeFontPadding = false
                setTextColor(Color.WHITE)
                background = rounded(stepColor(entry, step.index), Color.TRANSPARENT, 9)
            }, LinearLayout.LayoutParams(dp(18), dp(18)).apply { setMargins(0, dp(1), dp(9), 0) })
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(TextView(context).apply {
                    text = "${step.label} · ${stepState(entry, step.index)}"
                    textSize = 11.8f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(tokens.textPrimary)
                })
                addView(TextView(context).apply {
                    text = stepDetail(step)
                    textSize = 10.8f
                    setTextColor(tokens.textSecondary)
                    maxLines = 2
                    ellipsize = TextUtils.TruncateAt.END
                    setPadding(0, dp(3), 0, 0)
                })
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            if (canOpenReport) {
                addView(TextView(context).apply {
                    text = "报告 ›"
                    textSize = 11.2f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(tokens.primaryStrong)
                    includeFontPadding = false
                    setPadding(dp(10), dp(3), 0, 0)
                })
            }
        }

    private fun readonlyReport(step: CardRunHistoryStep): View {
        val output = historicalOutput(step)
        val reportText = Color.rgb(17, 24, 39)
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(15), dp(16), dp(16))
            background = rounded(Color.WHITE, Color.rgb(232, 235, 240), 20)
            elevation = dp(1).toFloat()
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(TextView(context).apply {
                    text = "只读 SH 报告"
                    textSize = 18f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(reportText)
                }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                addView(TextView(context).apply {
                    text = "⧉  复制"
                    textSize = 12f
                    gravity = Gravity.CENTER
                    setTextColor(tokens.textSecondary)
                    setPadding(dp(8), dp(6), dp(8), dp(6))
                    setOnClickListener { copyReport(output) }
                })
            })
            addView(TextView(context).apply {
                text = lineNumbered(output)
                minimumHeight = dp(220)
                textSize = 13f
                typeface = Typeface.MONOSPACE
                setTextColor(reportText)
                setLineSpacing(dp(3).toFloat(), 1f)
                setPadding(dp(14), dp(14), dp(14), dp(14))
                background = rounded(Color.rgb(248, 250, 252), Color.TRANSPARENT, 16, 0)
            }, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, dp(12), 0, 0) })
        }
    }

    private fun copyReport(text: String) {
        context.getSystemService(ClipboardManager::class.java)
            ?.setPrimaryClip(ClipData.newPlainText("Kite 历史 SH 报告", text))
        Toast.makeText(context, "已复制 SH 报告", Toast.LENGTH_SHORT).show()
    }

    private fun historicalOutput(step: CardRunHistoryStep): String {
        val report = step.reportText.trim()
        val output = extractShellOutput(report).ifBlank { report }
        val command = step.detail.ifBlank {
            report.lineSequence().firstOrNull { it.startsWith("命令：") }
                ?.removePrefix("命令：")?.trim().orEmpty()
        }
        return buildString {
            if (command.isNotBlank()) append(command).append("\n\n")
            append(output.ifBlank { "没有可用的 SH 报告快照。" }.normalizeShellStream())
        }.trim()
    }

    private fun extractShellOutput(report: String): String {
        listOf("原始输出：", "有效输出：", "错误输出：", "输出：").forEach { marker ->
            val index = report.indexOf(marker)
            if (index >= 0) return report.substring(index + marker.length).trim()
        }
        return report.lineSequence().filterNot { line ->
            line.startsWith("命令：") || line.startsWith("结果：") ||
                line.startsWith("退出码：") || line.startsWith("匹配：")
        }.joinToString("\n").trim()
    }

    private fun String.normalizeShellStream(): String =
        replace(ANSI_ESCAPE_REGEX, "").replace('\r', '\n').lineSequence()
            .joinToString("\n") { it.trimEnd() }.trimEnd()

    private fun lineNumbered(text: String): CharSequence {
        val lines = text.ifBlank { "暂无输出。" }.lineSequence().toList().ifEmpty { listOf("暂无输出。") }
        val width = lines.size.toString().length.coerceAtLeast(2)
        return SpannableStringBuilder().apply {
            lines.forEachIndexed { index, line ->
                val start = length
                append((index + 1).toString().padStart(width, ' '))
                val end = length
                setSpan(ForegroundColorSpan(Color.rgb(152, 162, 179)), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                setSpan(RelativeSizeSpan(0.92f), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                append("  ").append(line)
                if (index != lines.lastIndex) append('\n')
            }
        }
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
        return when {
            seconds < 3600L -> String.format("%02d:%02d", seconds / 60L, seconds % 60L)
            seconds < 86400L -> "${seconds / 3600L}小时"
            else -> "${seconds / 86400L}天"
        }
    }

    private fun timeline(entry: CardRunHistoryEntry): String {
        val start = formatClock(entry.startedAt)
        val endAt = entry.endedAt ?: entry.updatedAt.takeIf { entry.isClosed() }
        return "开始 $start · ${endAt?.let { "结束 ${formatClock(it)}" } ?: "进行中"}"
    }

    private fun formatClock(timestamp: Long): String {
        val now = Calendar.getInstance()
        val then = Calendar.getInstance().apply { timeInMillis = timestamp }
        val time = String.format("%02d:%02d", then.get(Calendar.HOUR_OF_DAY), then.get(Calendar.MINUTE))
        return if (now.get(Calendar.YEAR) == then.get(Calendar.YEAR) &&
            now.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR)
        ) time else "${then.get(Calendar.MONTH) + 1}月${then.get(Calendar.DAY_OF_MONTH)}日 $time"
    }

    private fun statusColor(entry: CardRunHistoryEntry): Int = when (entry.status) {
        CardRunStatus.Failed, CardRunStatus.BridgeUnavailable -> tokens.danger
        CardRunStatus.Completed -> tokens.success
        CardRunStatus.Stopped -> tokens.info
        CardRunStatus.Starting, CardRunStatus.Running, CardRunStatus.WaitingTerminal,
        CardRunStatus.AlreadyRunning, CardRunStatus.Opened -> tokens.primaryStrong
        else -> tokens.textSecondary
    }

    private fun stepColor(entry: CardRunHistoryEntry, index: Int): Int = when (stepState(entry, index)) {
        "失败" -> tokens.danger
        "已完成" -> tokens.success
        "已停止" -> tokens.info
        "未执行" -> tokens.textTertiary
        else -> tokens.primaryStrong
    }

    private fun stepState(entry: CardRunHistoryEntry, index: Int): String = when {
        entry.status == CardRunStatus.Completed && index < entry.stepCount -> "已完成"
        index < entry.currentStepIndex -> "已完成"
        index > entry.currentStepIndex && entry.currentStepIndex >= 0 -> "未执行"
        entry.currentStepIndex < 0 -> if (entry.isClosed()) "未执行" else entry.status.label
        entry.status == CardRunStatus.Failed || entry.status == CardRunStatus.BridgeUnavailable -> "失败"
        entry.status == CardRunStatus.Stopped -> "已停止"
        entry.status == CardRunStatus.WaitingTerminal -> "等待终端"
        entry.status == CardRunStatus.Opened -> "已打开"
        else -> entry.status.label
    }

    private fun stepDetail(step: CardRunHistoryStep): String = step.detail.ifBlank {
        when (step.type) {
            KiteRecipe.STEP_TERMINAL -> "打开交互终端"
            KiteRecipe.STEP_OPEN_WEB -> "未记录网址"
            KiteRecipe.STEP_SHELL -> "未记录命令"
            else -> "无自动内容"
        }
    }

    private fun rounded(fill: Int, stroke: Int, radius: Int, strokeWidth: Int = dp(1)) =
        ui.roundedBox(fill, stroke, dp(radius).toFloat(), strokeWidth)

    private fun weighted() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)

    private fun dp(value: Int): Int = ui.dp(value)

    companion object {
        private val ANSI_ESCAPE_REGEX = Regex("""\u001B\[[0-9;?]*[ -/]*[@-~]""")
    }
}
