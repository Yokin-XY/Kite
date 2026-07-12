package com.kite.app.feature.runtimemanagement

import android.app.AlertDialog
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
import com.kite.app.run.KiteRunUiTone
import com.kite.app.theme.KiteTheme
import com.kite.app.theme.ThemeConfig
import com.kite.app.theme.ThemeTokens
import com.kite.app.ui.UiKit

/** 运行管理真实视图所有者。普通状态只绑定已有行，结构变化才重建滚动内容。 */
internal class RuntimeManagementScreen(
    private val context: Context,
    initialScrollY: Int,
    private val onBack: () -> Unit,
    private val onRefresh: () -> Unit,
    private val onAction: (RuntimeManagementActionUiState) -> Unit
) {
    private val tokens = RuntimeManagementTheme.tokens(context)
    private val ui = UiKit(context, tokens)
    private val scroll = ScrollView(context).apply { isFillViewport = true }
    private val contentHost = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(18), dp(8), dp(18), dp(28))
    }
    private val cardCount = summaryValue("卡片")
    private val terminalCount = summaryValue("终端")
    private val processCount = summaryValue("进程")
    private val runBindings = linkedMapOf<String, RunBinding>()
    private val processBindings = linkedMapOf<String, ProcessBinding>()
    private val expandedRunIds = mutableSetOf<String>()
    private var latestState = RuntimeManagementUiState()
    private var structureSignature = ""
    private var bodyRebuildCount = 0
    private var disposed = false
    private var restoredScrollY = initialScrollY.coerceAtLeast(0)

    val root: View = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(tokens.pageBackground)
        addView(header())
        addView(summaryRow())
        scroll.addView(contentHost)
        addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        post { scroll.scrollTo(0, restoredScrollY) }
    }

    fun render(state: RuntimeManagementUiState) {
        if (disposed) return
        latestState = state
        cardCount.text = state.summary.runningCards.toString()
        terminalCount.text = state.summary.runningTerminals.toString()
        processCount.text = state.summary.runningProcesses.toString()
        val nextSignature = state.structureSignature()
        if (nextSignature != structureSignature || contentHost.childCount == 0) {
            structureSignature = nextSignature
            rebuildBody(state)
            return
        }
        state.runs.forEach { run -> runBindings[run.instanceId]?.let { bindRun(it, run) } }
        val processes = state.allProcesses().associateBy(RuntimeManagementProcessUiState::key)
        processBindings.forEach { (key, binding) -> processes[key]?.let { bindProcess(binding, it) } }
    }

    fun scrollY(): Int = scroll.scrollY

    fun dispose() {
        disposed = true
        restoredScrollY = scroll.scrollY
        runBindings.clear()
        processBindings.clear()
        contentHost.removeAllViews()
    }

    internal fun bodyRebuildCountForTesting(): Int = bodyRebuildCount

    internal fun runRootForTesting(instanceId: String): View? = runBindings[instanceId]?.root

    private fun rebuildBody(state: RuntimeManagementUiState) {
        bodyRebuildCount += 1
        runBindings.clear()
        processBindings.clear()
        contentHost.removeAllViews()
        if (state.isEmpty) {
            contentHost.addView(TextView(context).apply {
                text = "当前没有运行中的卡片或进程"
                textSize = 13.5f
                gravity = Gravity.CENTER
                setTextColor(tokens.textSecondary)
                setPadding(0, dp(46), 0, dp(20))
            })
            return
        }
        state.runs.forEach { run ->
            val binding = createRunCard(run)
            runBindings[run.instanceId] = binding
            contentHost.addView(binding.root)
        }
        state.otherProcessSections.forEach { section ->
            contentHost.addView(sectionTitle("${section.title} · ${section.processes.size}"))
            section.processes.forEach { process -> contentHost.addView(createProcessRow(process).root) }
        }
    }

    private fun createRunCard(run: RuntimeManagementRunUiState): RunBinding {
        val title = TextView(context)
        val subtitle = TextView(context)
        val status = TextView(context)
        val stop = TextView(context)
        val chevron = TextView(context)
        val details = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(13), dp(14), dp(13))
            background = rounded(tokens.cardBackground, tokens.border, 8)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, dp(10)) }
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                isClickable = true
                isFocusable = true
                setOnClickListener { toggleRun(run.instanceId) }
                addView(TextView(context).apply {
                    text = ownerSymbol(run.sourceLabel)
                    textSize = 12f
                    typeface = Typeface.DEFAULT_BOLD
                    gravity = Gravity.CENTER
                    setTextColor(tokens.primaryStrong)
                    background = rounded(tokens.primarySubtle, Color.TRANSPARENT, 8)
                }, LinearLayout.LayoutParams(dp(34), dp(34)).apply { setMargins(0, 0, dp(12), 0) })
                addView(LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(title.apply {
                        textSize = 15f
                        typeface = Typeface.DEFAULT_BOLD
                        setTextColor(tokens.textPrimary)
                        maxLines = 1
                        ellipsize = TextUtils.TruncateAt.END
                    })
                    addView(subtitle.apply {
                        textSize = 11.5f
                        setTextColor(tokens.textSecondary)
                        setPadding(0, dp(4), 0, 0)
                        maxLines = 1
                        ellipsize = TextUtils.TruncateAt.END
                    })
                }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                addView(status.apply {
                    textSize = 12f
                    typeface = Typeface.DEFAULT_BOLD
                    gravity = Gravity.CENTER
                    includeFontPadding = false
                    minWidth = dp(58)
                    setPadding(dp(10), 0, dp(10), 0)
                }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(26)).apply {
                    setMargins(dp(8), 0, 0, 0)
                })
                addView(chevron.apply {
                    textSize = 18f
                    gravity = Gravity.CENTER
                    includeFontPadding = false
                    setTextColor(tokens.textTertiary)
                }, LinearLayout.LayoutParams(dp(24), dp(28)).apply { setMargins(dp(4), 0, 0, 0) })
            })
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.END or Gravity.CENTER_VERTICAL
                addView(stop, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(32)).apply {
                    setMargins(0, dp(10), 0, 0)
                })
            })
            addView(details)
        }
        return RunBinding(root, title, subtitle, status, stop, chevron, details).also { bindRun(it, run) }
    }

    private fun bindRun(binding: RunBinding, run: RuntimeManagementRunUiState) {
        binding.item = run
        binding.title.text = run.title
        binding.subtitle.text = "${run.sourceLabel} · 进程 ${run.processCount}"
        binding.status.text = run.statusLabel
        val colors = statusColors(run.statusTone)
        binding.status.setTextColor(colors.text)
        binding.status.background = rounded(colors.background, colors.border, 7)
        bindAction(binding.stop, run.stopAction)
        val expanded = run.instanceId in expandedRunIds
        binding.chevron.text = if (expanded) "⌃" else "›"
        val detailsSignature = if (expanded) run.detailsSignature() else "collapsed"
        if (binding.detailsSignature != detailsSignature) {
            binding.detailsSignature = detailsSignature
            rebuildRunDetails(binding, run, expanded)
        }
    }

    private fun rebuildRunDetails(binding: RunBinding, run: RuntimeManagementRunUiState, expanded: Boolean) {
        binding.processKeys.forEach(processBindings::remove)
        binding.processKeys.clear()
        binding.details.removeAllViews()
        binding.details.visibility = if (expanded) View.VISIBLE else View.GONE
        if (!expanded) return
        binding.details.setPadding(dp(46), dp(8), 0, 0)
        run.surfaces.forEach { surface ->
            binding.details.addView(detailRow(
                title = surface.title,
                subtitle = surface.caption,
                action = surface.openAction
            ))
        }
        listOfNotNull(run.mainProcess).plus(run.childProcesses).forEach { process ->
            val processBinding = createProcessRow(process)
            binding.processKeys += process.key
            binding.details.addView(processBinding.root)
        }
    }

    private fun createProcessRow(process: RuntimeManagementProcessUiState): ProcessBinding {
        val title = TextView(context)
        val subtitle = TextView(context)
        val action = TextView(context)
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(6), dp(8), 0, dp(8))
            isClickable = true
            isFocusable = true
            addView(TextView(context).apply {
                text = "PID"
                textSize = 10f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                includeFontPadding = false
                setTextColor(tokens.primaryStrong)
                background = rounded(tokens.primarySubtle, Color.TRANSPARENT, 7)
            }, LinearLayout.LayoutParams(dp(32), dp(28)).apply { setMargins(0, 0, dp(10), 0) })
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(title.apply {
                    textSize = 13f
                    setTextColor(tokens.textPrimary)
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                })
                addView(subtitle.apply {
                    textSize = 11.5f
                    setTextColor(tokens.textSecondary)
                    setPadding(0, dp(3), 0, 0)
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                })
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(action, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(30)).apply {
                setMargins(dp(8), 0, 0, 0)
            })
        }
        return ProcessBinding(root, title, subtitle, action, process).also {
            processBindings[process.key] = it
            bindProcess(it, process)
        }
    }

    private fun bindProcess(binding: ProcessBinding, process: RuntimeManagementProcessUiState) {
        binding.item = process
        binding.title.text = process.title
        binding.subtitle.text = process.subtitle
        binding.root.setOnClickListener { showProcessDialog(binding.item) }
        bindAction(binding.action, process.stopAction)
    }

    private fun detailRow(
        title: String,
        subtitle: String,
        action: RuntimeManagementActionUiState
    ): View = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(6), dp(8), 0, dp(8))
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(context).apply {
                text = title
                textSize = 13f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(tokens.textPrimary)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            })
            addView(TextView(context).apply {
                text = subtitle
                textSize = 11.5f
                setTextColor(tokens.textSecondary)
                setPadding(0, dp(3), 0, 0)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            })
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(TextView(context).apply { bindAction(this, action) }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            dp(30)
        ))
    }

    private fun bindAction(view: TextView, action: RuntimeManagementActionUiState?) {
        view.visibility = if (action == null) View.GONE else View.VISIBLE
        if (action == null) {
            view.setOnClickListener(null)
            return
        }
        view.text = action.label
        view.textSize = 11.5f
        view.typeface = Typeface.DEFAULT_BOLD
        view.gravity = Gravity.CENTER
        view.includeFontPadding = false
        view.isEnabled = action.enabled
        view.alpha = if (action.enabled) 1f else 0.62f
        view.setTextColor(if (action.danger) tokens.warning else tokens.textPrimary)
        view.background = rounded(
            if (action.danger) tokens.warningSoft else tokens.surface,
            if (action.danger) tokens.warningBorder else tokens.border,
            7
        )
        view.setPadding(dp(10), 0, dp(10), 0)
        view.setOnClickListener { if (action.enabled) onAction(action) }
    }

    private fun toggleRun(instanceId: String) {
        if (!expandedRunIds.add(instanceId)) expandedRunIds.remove(instanceId)
        val run = latestState.runs.firstOrNull { it.instanceId == instanceId } ?: return
        runBindings[instanceId]?.let { binding ->
            binding.detailsSignature = ""
            bindRun(binding, run)
        }
    }

    private fun showProcessDialog(process: RuntimeManagementProcessUiState) {
        val message = buildString {
            appendLine("PID: ${process.pid}")
            if (process.parentPid > 0) appendLine("PPID: ${process.parentPid}")
            appendLine("归属: ${process.ownerLabel}")
            append("用途: ${process.purpose}")
        }
        AlertDialog.Builder(context)
            .setTitle(process.title)
            .setMessage(message)
            .setNegativeButton("关闭", null)
            .apply {
                process.stopAction?.let { action ->
                    setPositiveButton(action.label) { _, _ -> if (action.enabled) onAction(action) }
                }
            }
            .show()
            .getButton(AlertDialog.BUTTON_POSITIVE)
            ?.setTextColor(tokens.danger)
    }

    private fun header(): View = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(16), dp(10), dp(16), dp(4))
        addView(iconButton("‹", onBack))
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(context).apply {
                text = "运行管理"
                textSize = 20f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(tokens.textPrimary)
                includeFontPadding = false
            })
            addView(TextView(context).apply {
                text = "卡片、终端与进程"
                textSize = 12.5f
                setTextColor(tokens.textSecondary)
                setPadding(0, dp(4), 0, 0)
            })
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(iconButton("↻", onRefresh))
    }

    private fun summaryRow(): View = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(24), dp(8), dp(24), dp(8))
        addView(summaryMetric("▣", "卡片", cardCount), LinearLayout.LayoutParams(0, dp(56), 1f))
        addView(summaryMetric(">_", "终端", terminalCount), LinearLayout.LayoutParams(0, dp(56), 1f))
        addView(summaryMetric("⌁", "进程", processCount), LinearLayout.LayoutParams(0, dp(56), 1f))
    }

    private fun summaryMetric(icon: String, label: String, value: TextView): View =
        LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            addView(TextView(context).apply {
                text = icon
                textSize = 13f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(tokens.primaryStrong)
                gravity = Gravity.CENTER
            }, LinearLayout.LayoutParams(dp(30), dp(30)))
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(TextView(context).apply {
                    text = label
                    textSize = 10.5f
                    setTextColor(tokens.textSecondary)
                })
                addView(value)
            })
        }

    private fun summaryValue(label: String): TextView = TextView(context).apply {
        text = "0"
        contentDescription = "$label 数量"
        textSize = 17f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(tokens.textPrimary)
        includeFontPadding = false
    }

    private fun sectionTitle(title: String): TextView = TextView(context).apply {
        text = title
        textSize = 12.5f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(tokens.textPrimary)
        setPadding(0, dp(14), 0, dp(7))
    }

    private fun iconButton(symbol: String, onClick: () -> Unit): TextView = TextView(context).apply {
        text = symbol
        textSize = 22f
        gravity = Gravity.CENTER
        setTextColor(tokens.textPrimary)
        contentDescription = if (symbol == "‹") "返回" else "刷新"
        isClickable = true
        isFocusable = true
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(dp(42), dp(42))
    }

    private fun RuntimeManagementUiState.structureSignature(): String = buildString {
        runs.forEach { run ->
            append("run:").append(run.instanceId)
            run.surfaces.forEach { append('|').append(it.key) }
            run.mainProcess?.let { append("|main:").append(it.key) }
            run.childProcesses.forEach { append("|child:").append(it.key) }
            append(';')
        }
        otherProcessSections.forEach { section ->
            append("section:").append(section.key)
            section.processes.forEach { append('|').append(it.key) }
            append(';')
        }
    }

    private fun RuntimeManagementRunUiState.detailsSignature(): String = buildString {
        surfaces.forEach { append(it.key).append(':').append(it.title).append(':').append(it.caption).append('|') }
        mainProcess?.let { append("main:").append(it.key).append('|') }
        childProcesses.forEach { append("child:").append(it.key).append('|') }
    }

    private fun RuntimeManagementUiState.allProcesses(): List<RuntimeManagementProcessUiState> =
        runs.flatMap { listOfNotNull(it.mainProcess) + it.childProcesses } +
            otherProcessSections.flatMap(RuntimeManagementProcessSectionUiState::processes)

    private fun ownerSymbol(sourceLabel: String): String = when (sourceLabel) {
        "资源" -> "≡"
        "终端" -> ">_"
        "网页" -> "↗"
        else -> "▦"
    }

    private fun statusColors(tone: KiteRunUiTone): StatusColors = when (tone) {
        KiteRunUiTone.Info -> StatusColors(tokens.info, tokens.infoSoft, tokens.infoBorder)
        KiteRunUiTone.Success -> StatusColors(tokens.success, tokens.successSoft, tokens.successBorder)
        KiteRunUiTone.Warning -> StatusColors(tokens.warning, tokens.warningSoft, tokens.warningBorder)
        KiteRunUiTone.Danger -> StatusColors(tokens.danger, tokens.dangerSoft, tokens.dangerBorder)
        KiteRunUiTone.Neutral -> StatusColors(tokens.textSecondary, tokens.surface, tokens.border)
    }

    private fun rounded(fill: Int, stroke: Int, radiusDp: Int) =
        ui.roundedBox(fill, stroke, dp(radiusDp).toFloat())

    private fun dp(value: Int): Int = ui.dp(value)

    private data class RunBinding(
        val root: LinearLayout,
        val title: TextView,
        val subtitle: TextView,
        val status: TextView,
        val stop: TextView,
        val chevron: TextView,
        val details: LinearLayout,
        val processKeys: MutableSet<String> = linkedSetOf(),
        var detailsSignature: String = "",
        var item: RuntimeManagementRunUiState? = null
    )

    private data class ProcessBinding(
        val root: LinearLayout,
        val title: TextView,
        val subtitle: TextView,
        val action: TextView,
        var item: RuntimeManagementProcessUiState
    )

    private data class StatusColors(val text: Int, val background: Int, val border: Int)
}

private object RuntimeManagementTheme {
    fun tokens(context: Context): ThemeTokens {
        val store = context.getSharedPreferences("kite_theme", Context.MODE_PRIVATE)
        return KiteTheme.resolve(
            ThemeConfig(
                themeColor = store.getInt("theme_color", KiteTheme.defaultThemeColor),
                backgroundColor = store.getInt("background_color", KiteTheme.defaultBackgroundColor)
            )
        )
    }
}
