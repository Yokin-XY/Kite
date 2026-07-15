package com.kite.app.feature.runsurface

import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.kite.app.run.CardRunStatus
import com.kite.app.theme.ThemeTokens
import com.kite.app.ui.UiKit

/** 报告显示面只消费 RunSurfaceUiState，并通过回调提交用户意图。 */
internal class RunReportScreen(
    private val context: Context,
    private val tokens: ThemeTokens
) : RunSurfaceBinding {
    private val ui = UiKit(context, tokens)
    private val content = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(ui.dp(16), ui.dp(12), ui.dp(16), ui.dp(28))
    }
    private val statusBadge = TextView(context)
    private val stepValue = TextView(context)
    private val elapsedValue = TextView(context)
    private val commandValue = TextView(context)
    private val insightHost = FrameLayout(context)
    private val outputText = TextView(context)
    private val outputScroll = ScrollView(context)
    private val footerRow = LinearLayout(context)
    private val footerText = TextView(context)
    private var currentState: RunSurfaceUiState? = null
    private var currentReport: RunSurfaceContent.Report? = null

    override val root: View = ScrollView(context).apply {
        isFillViewport = true
        setBackgroundColor(Color.rgb(246, 247, 249))
        addView(content)
    }

    init {
        content.addView(summaryCard())
        content.addView(insightHost, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            setMargins(0, ui.dp(12), 0, 0)
        })
        content.addView(outputCard())
    }

    override fun render(state: RunSurfaceUiState) {
        val report = state.content as? RunSurfaceContent.Report ?: return
        currentState = state
        currentReport = report
        bindSummary(state, report)
        bindInsight(report.insight)
        bindOutput(state, report)
    }

    override fun tick(now: Long): Boolean {
        val state = currentState ?: return false
        elapsedValue.text = RunReportPresenter.elapsedLabel(state, now)
        footerText.text = RunReportPresenter.footerLabel(state, now)
        bindStatusBadge(state, currentReport?.failed == true)
        return RunReportPresenter.isLive(state.status)
    }

    override fun dispose() {
        currentState = null
        currentReport = null
    }

    private fun summaryCard(): View = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        minimumHeight = ui.dp(142)
        setPadding(ui.dp(16), ui.dp(16), ui.dp(16), ui.dp(16))
        background = ui.roundedBox(Color.WHITE, REPORT_BORDER, ui.dp(20).toFloat())
        elevation = ui.dp(1).toFloat()
        addView(ui.rowWith(context) {
            gravity = Gravity.CENTER_VERTICAL
            addView(TextView(context).apply {
                text = ">_"
                textSize = 18f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                includeFontPadding = false
                setTextColor(Color.rgb(22, 163, 107))
                background = ui.roundedBox(Color.rgb(234, 248, 240), Color.TRANSPARENT, ui.dp(12).toFloat(), 0)
            }, LinearLayout.LayoutParams(ui.dp(40), ui.dp(40)).apply {
                setMargins(0, 0, ui.dp(14), 0)
            })
            addView(TextView(context).apply {
                text = "执行摘要"
                textSize = 17f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(REPORT_TEXT)
                includeFontPadding = false
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            configureBadge(statusBadge)
            addView(statusBadge, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ui.dp(22)).apply {
                setMargins(ui.dp(12), 0, 0, 0)
            })
        })
        addView(ui.rowWith(context) {
            gravity = Gravity.BOTTOM
            setPadding(0, ui.dp(14), 0, 0)
            val metrics = listOf(
                metric("步骤", stepValue),
                metric("已运行", elapsedValue),
                metric("当前命令", commandValue)
            )
            metrics.forEachIndexed { index, metric ->
                addView(metric, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                if (index != metrics.lastIndex) {
                    addView(View(context).apply {
                        setBackgroundColor(Color.argb(115, Color.red(REPORT_BORDER), Color.green(REPORT_BORDER), Color.blue(REPORT_BORDER)))
                    }, LinearLayout.LayoutParams(ui.dp(1), ui.dp(32)).apply {
                        setMargins(ui.dp(8), ui.dp(3), ui.dp(8), 0)
                    })
                }
            }
        })
    }

    private fun metric(label: String, value: TextView): View = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        addView(TextView(context).apply {
            text = label
            textSize = 11f
            setTextColor(Color.rgb(152, 162, 179))
            includeFontPadding = false
        })
        addView(value.apply {
            textSize = 15f
            setTextColor(REPORT_TEXT)
            includeFontPadding = false
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setPadding(0, ui.dp(7), 0, 0)
        })
    }

    private fun outputCard(): View = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(ui.dp(16), ui.dp(15), ui.dp(16), ui.dp(16))
        background = ui.roundedBox(Color.WHITE, REPORT_BORDER, ui.dp(20).toFloat())
        elevation = ui.dp(1).toFloat()
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            setMargins(0, ui.dp(18), 0, 0)
        }
        addView(ui.rowWith(context) {
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(34))
            addView(TextView(context).apply {
                text = "实时输出"
                textSize = 18f
                typeface = Typeface.DEFAULT_BOLD
                includeFontPadding = false
                setTextColor(REPORT_TEXT)
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(toolButton("⧉", "复制") {
                copyText("Kite SH 输出", currentReport?.outputText.orEmpty(), "已复制 SH 输出")
            })
        })
        outputText.apply {
            minimumHeight = ui.dp(260)
            textSize = 13f
            typeface = Typeface.MONOSPACE
            setTextColor(REPORT_TEXT)
            setLineSpacing(ui.dp(3).toFloat(), 1f)
            includeFontPadding = true
            setPadding(ui.dp(14), ui.dp(14), ui.dp(14), ui.dp(14))
        }
        outputScroll.apply {
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            background = ui.roundedBox(Color.rgb(248, 250, 252), Color.TRANSPARENT, ui.dp(16).toFloat(), 0)
            addView(outputText)
        }
        addView(outputScroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            (context.resources.displayMetrics.heightPixels * 0.42f).toInt().coerceIn(ui.dp(260), ui.dp(420))
        ).apply { setMargins(0, ui.dp(12), 0, 0) })
        footerRow.apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(ui.dp(14), ui.dp(12), ui.dp(14), 0)
            addView(TextView(context).apply {
                text = "●"
                textSize = 10f
                includeFontPadding = false
                setTextColor(tokens.success)
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, 0, ui.dp(8), 0)
            })
            addView(footerText.apply {
                textSize = 13f
                setTextColor(REPORT_SECONDARY)
            })
        }
        addView(footerRow)
    }

    private fun bindSummary(state: RunSurfaceUiState, report: RunSurfaceContent.Report) {
        stepValue.text = if (state.stepCount > 0) {
            "${(state.currentStepIndex + 1).coerceIn(1, state.stepCount)}/${state.stepCount}"
        } else {
            "--"
        }
        elapsedValue.text = RunReportPresenter.elapsedLabel(state)
        commandValue.text = report.currentCommand.ifBlank { "--" }
        commandValue.isClickable = report.fullCommand.isNotBlank()
        commandValue.setOnClickListener(if (report.fullCommand.isNotBlank()) View.OnClickListener {
            showCommandDialog(report.fullCommand)
        } else null)
        bindStatusBadge(state, report.failed)
    }

    private fun bindInsight(insight: RunReportInsight?) {
        insightHost.removeAllViews()
        if (insight == null) {
            insightHost.visibility = View.GONE
            return
        }
        val color = when (insight.tone) {
            RunReportInsightTone.Warning -> tokens.warning
            RunReportInsightTone.Danger -> tokens.danger
        }
        insightHost.visibility = View.VISIBLE
        insightHost.addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ui.dp(14), ui.dp(13), ui.dp(14), ui.dp(13))
            background = ui.roundedBox(tokens.surfaceElevated, tokens.border, ui.dp(17).toFloat())
            addView(ui.rowWith(context) {
                addView(TextView(context).apply {
                    text = insight.marker
                    textSize = 12f
                    typeface = Typeface.DEFAULT_BOLD
                    gravity = Gravity.CENTER
                    includeFontPadding = false
                    setTextColor(color)
                    background = ui.roundedBox(tintBackground(color), Color.TRANSPARENT, ui.dp(10).toFloat(), 0)
                }, LinearLayout.LayoutParams(ui.dp(34), ui.dp(34)).apply {
                    setMargins(0, 0, ui.dp(11), 0)
                })
                addView(LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(TextView(context).apply {
                        text = insight.title
                        textSize = 14f
                        typeface = Typeface.DEFAULT_BOLD
                        setTextColor(tokens.textPrimary)
                    })
                    addView(TextView(context).apply {
                        text = insight.detail
                        textSize = 12.2f
                        setTextColor(tokens.textSecondary)
                        setPadding(0, ui.dp(4), 0, 0)
                        setLineSpacing(ui.dp(3).toFloat(), 1f)
                    })
                }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            })
        })
    }

    private fun bindOutput(state: RunSurfaceUiState, report: RunSurfaceContent.Report) {
        val next = report.outputText.ifBlank { "暂无输出。" }
        val current = outputText.text?.toString().orEmpty()
        when {
            current == next -> Unit
            current == "暂无输出。" -> outputText.text = next
            next.startsWith(current) -> outputText.append(next.substring(current.length))
            else -> outputText.text = next
        }
        outputText.setTextColor(if (report.failed) tokens.danger else REPORT_TEXT)
        footerRow.visibility = if (RunReportPresenter.isLive(state.status)) View.VISIBLE else View.GONE
        footerText.text = RunReportPresenter.footerLabel(state)
        if (RunReportPresenter.isLive(state.status)) {
            outputScroll.post { outputScroll.fullScroll(View.FOCUS_DOWN) }
        }
    }

    private fun configureBadge(view: TextView) {
        view.textSize = 11f
        view.typeface = Typeface.DEFAULT_BOLD
        view.gravity = Gravity.CENTER
        view.includeFontPadding = false
        view.setPadding(ui.dp(8), 0, ui.dp(8), 0)
    }

    private fun bindStatusBadge(state: RunSurfaceUiState, failed: Boolean) {
        val isDone = state.status == CardRunStatus.Completed
        val isStopped = state.status == CardRunStatus.Stopped
        val isLive = RunReportPresenter.isLive(state.status)
        val color = when {
            failed -> tokens.danger
            isLive -> tokens.primaryStrong
            isDone -> Color.rgb(22, 163, 107)
            isStopped -> tokens.textSecondary
            else -> Color.rgb(124, 133, 149)
        }
        val fill = when {
            isDone -> Color.rgb(234, 248, 240)
            isStopped -> tokens.surface
            else -> tintBackground(color)
        }
        statusBadge.text = when {
            failed -> "失败"
            isLive -> "运行中"
            isDone -> "已完成"
            isStopped -> "已停止"
            else -> state.statusLabel
        }
        statusBadge.setTextColor(color)
        statusBadge.background = ui.roundedBox(fill, Color.TRANSPARENT, ui.dp(11).toFloat(), 0)
    }

    private fun toolButton(icon: String, label: String, onClick: () -> Unit): TextView = TextView(context).apply {
        text = "$icon  $label"
        textSize = 13f
        gravity = Gravity.CENTER
        includeFontPadding = false
        setTextColor(REPORT_SECONDARY)
        setPadding(ui.dp(7), 0, ui.dp(7), 0)
        background = ui.roundedBox(Color.TRANSPARENT, Color.TRANSPARENT, ui.dp(10).toFloat(), 0)
        setOnClickListener { onClick() }
    }

    private fun showCommandDialog(command: String) {
        val dialog = Dialog(context)
        val body = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ui.dp(18), ui.dp(18), ui.dp(18), ui.dp(18))
            background = ui.roundedBox(tokens.cardBackground, tokens.border, ui.dp(16).toFloat())
            addView(TextView(context).apply {
                text = "原始命令"
                textSize = 18f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(tokens.textPrimary)
            })
            addView(ScrollView(context).apply {
                background = ui.roundedBox(Color.rgb(248, 250, 252), Color.TRANSPARENT, ui.dp(14).toFloat(), 0)
                addView(TextView(context).apply {
                    text = command
                    textSize = 12.5f
                    typeface = Typeface.MONOSPACE
                    setTextColor(tokens.textPrimary)
                    setPadding(ui.dp(12), ui.dp(12), ui.dp(12), ui.dp(12))
                })
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(260)).apply {
                setMargins(0, ui.dp(12), 0, 0)
            })
            addView(TextView(context).apply {
                text = "复制命令"
                textSize = 14f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setTextColor(tokens.buttonText)
                background = ui.roundedBox(tokens.primaryStrong, Color.TRANSPARENT, ui.dp(14).toFloat(), 0)
                setOnClickListener { copyText("Kite 原始命令", command, "已复制原始命令") }
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(44)).apply {
                setMargins(0, ui.dp(14), 0, 0)
            })
        }
        dialog.setContentView(body)
        dialog.show()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    private fun copyText(label: String, text: String, toast: String) {
        context.getSystemService(ClipboardManager::class.java)
            ?.setPrimaryClip(ClipData.newPlainText(label, text))
        Toast.makeText(context, toast, Toast.LENGTH_SHORT).show()
    }

    private fun tintBackground(color: Int): Int = Color.rgb(
        Color.red(color) + ((255 - Color.red(color)) * 0.9f).toInt(),
        Color.green(color) + ((255 - Color.green(color)) * 0.9f).toInt(),
        Color.blue(color) + ((255 - Color.blue(color)) * 0.9f).toInt()
    )

    private companion object {
        val REPORT_BORDER: Int = Color.rgb(232, 235, 240)
        val REPORT_TEXT: Int = Color.rgb(17, 24, 39)
        val REPORT_SECONDARY: Int = Color.rgb(124, 133, 149)
    }
}
