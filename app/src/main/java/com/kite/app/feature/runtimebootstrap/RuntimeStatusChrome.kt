package com.kite.app.feature.runtimebootstrap

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import com.kite.app.theme.KiteTheme
import com.kite.app.theme.ThemeTokens
import com.kite.app.ui.UiKit

/** 运行状态的可见 chrome 所有者：状态胶囊、内联提示、准入遮罩和状态弹层。 */
internal class RuntimeStatusChrome(
    private val activity: Activity,
    private val rootHost: FrameLayout,
    private val tokens: ThemeTokens,
    private val onRefresh: () -> Unit,
    private val onPrimaryAction: () -> Unit
) {
    private val ui = UiKit(activity, tokens)
    private var latestState = RuntimeStatusUiState.checking()
    private var suppressTransientChrome = false
    private var dialog: Dialog? = null
    private var gate: GateBinding? = null
    private var panel: PanelBinding? = null
    private var lastAutoOpenGeneration = 0L
    private var disposed = false

    fun render(state: RuntimeStatusUiState, suppressTransient: Boolean) {
        if (disposed) return
        latestState = state
        suppressTransientChrome = suppressTransient
        bindGate(state)
        panel?.let { bindPanel(it, state) }
        if (
            state.autoOpenPanel &&
            state.autoOpenGeneration > 0L &&
            state.autoOpenGeneration != lastAutoOpenGeneration &&
            !state.shouldShowGate &&
            !suppressTransient
        ) {
            lastAutoOpenGeneration = state.autoOpenGeneration
            showPanel(auto = true)
        }
    }

    fun showPanel(auto: Boolean, anchor: View? = null) {
        if (disposed) return
        if (auto && (latestState.shouldShowGate || suppressTransientChrome)) return
        val existing = dialog
        if (existing?.isShowing == true) {
            onRefresh()
            panel?.let { bindPanel(it, latestState) }
            return
        }
        onRefresh()
        createPanel(anchor, auto)
    }

    fun dismissPanel() {
        dialog?.dismiss()
    }

    fun bindStatusPill(view: TextView, state: RuntimeStatusUiState = latestState) {
        val color = when {
            state.isProblem -> tokens.danger
            state.requiresPermission || state.blocksUbuntuActions -> tokens.primaryStrong
            state.visible -> tokens.textSecondary
            else -> tokens.success
        }
        view.text = state.statusLabel
        view.textSize = 10.5f
        view.typeface = Typeface.DEFAULT_BOLD
        view.gravity = Gravity.CENTER
        view.includeFontPadding = false
        view.setTextColor(color)
        view.setPadding(dp(8), 0, dp(8), 0)
        view.background = rounded(tint(color, 0.88f), tint(color, 0.72f), 13)
    }

    fun createInlineBanner(state: RuntimeStatusUiState = latestState): View? {
        if (!state.visible) return null
        val border = if (state.isProblem) tokens.danger else tokens.border
        val titleColor = if (state.isProblem) tokens.danger else tokens.textPrimary
        return LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(10), dp(14), dp(10))
            background = rounded(tokens.surfaceElevated, border, 16)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(dp(18), 0, dp(18), dp(10)) }
            addView(TextView(context).apply {
                text = state.title
                textSize = 13.5f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(titleColor)
            })
            addView(TextView(context).apply {
                text = state.detail
                textSize = 12f
                setTextColor(tokens.textSecondary)
                setPadding(0, dp(4), 0, 0)
            })
            addView(progressView(state, compact = true))
            setOnClickListener { showPanel(auto = false) }
        }
    }

    fun dispose() {
        disposed = true
        dialog?.dismiss()
        dialog = null
        panel = null
        gate?.root?.let(rootHost::removeView)
        gate = null
    }

    internal fun gateRootForTesting(): View? = gate?.root

    internal fun dialogForTesting(): Dialog? = dialog

    internal fun panelCountsForTesting(): RuntimeStatusCounts? = panel?.let { binding ->
        RuntimeStatusCounts(
            runningCards = binding.cardCount.text.toString().toIntOrNull() ?: 0,
            runningTerminals = binding.terminalCount.text.toString().toIntOrNull() ?: 0,
            runningProcesses = binding.processCount.text.toString().toIntOrNull() ?: 0
        )
    }

    private fun bindGate(state: RuntimeStatusUiState) {
        val visible = state.shouldShowGate && !suppressTransientChrome
        if (!visible) {
            gate?.root?.visibility = View.GONE
            return
        }
        val binding = gate ?: createGate().also { created ->
            gate = created
            rootHost.addView(
                created.root,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
        }
        binding.root.visibility = View.VISIBLE
        binding.root.bringToFront()
        binding.root.setBackgroundColor(withAlpha(tokens.pageBackground, 238))
        binding.title.text = state.title.ifBlank { "正在准备 Ubuntu" }
        binding.title.setTextColor(if (state.isProblem) tokens.danger else tokens.textPrimary)
        binding.detail.text = state.detail
        binding.detail.visibility = if (state.detail.isBlank()) View.GONE else View.VISIBLE
        bindProgress(binding.progress, binding.progressText, state, hideProblemBar = true)
        val actionVisible = state.primaryAction != RuntimeStatusAction.OpenProcessManagement
        binding.action.visibility = if (actionVisible) View.VISIBLE else View.GONE
        binding.action.text = state.primaryActionLabel
        binding.action.setOnClickListener { onPrimaryAction() }
    }

    private fun createGate(): GateBinding {
        val title = TextView(activity)
        val detail = TextView(activity)
        val progress = ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal)
        val progressText = TextView(activity)
        val action = TextView(activity)
        val root = FrameLayout(activity).apply {
            isClickable = true
            isFocusable = true
            visibility = View.GONE
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(22), dp(22), dp(22), dp(20))
                background = rounded(tokens.cardBackground, tokens.border, 22)
                elevation = dp(10).toFloat()
                addView(title.apply {
                    textSize = 20f
                    typeface = Typeface.DEFAULT_BOLD
                    includeFontPadding = false
                })
                addView(detail.apply {
                    textSize = 13f
                    setTextColor(tokens.textSecondary)
                    setLineSpacing(dp(3).toFloat(), 1f)
                    setPadding(0, dp(12), 0, 0)
                })
                addView(progress.apply {
                    max = 100
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(7)).apply {
                        setMargins(0, dp(18), 0, 0)
                    }
                })
                addView(progressText.apply {
                    textSize = 12f
                    setTextColor(tokens.textSecondary)
                    setPadding(0, dp(8), 0, 0)
                })
                addView(action.apply {
                    textSize = 14f
                    typeface = Typeface.DEFAULT_BOLD
                    gravity = Gravity.CENTER
                    setTextColor(tokens.buttonText)
                    background = rounded(tokens.primaryStrong, tokens.primaryStrong, 16)
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)).apply {
                        setMargins(0, dp(18), 0, 0)
                    }
                })
            }, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            ).apply { setMargins(dp(24), 0, dp(24), 0) })
        }
        return GateBinding(root, title, detail, progress, progressText, action)
    }

    private fun createPanel(anchor: View?, auto: Boolean) {
        val screenWidth = activity.resources.displayMetrics.widthPixels
        val panelWidth = (screenWidth - dp(36)).coerceAtMost(dp(560))
        val panelLeft = ((screenWidth - panelWidth) / 2).coerceAtLeast(dp(8))
        val pointerSize = dp(22)
        val pointerMinLeft = dp(24)
        val pointerMaxLeft = (panelWidth - dp(24) - pointerSize).coerceAtLeast(pointerMinLeft)
        val (anchorCenterX, anchorBottomY) = anchorPoint(
            anchor,
            panelLeft,
            panelWidth,
            if (auto) dp(56) else dp(58)
        )
        val pointerLeft = (anchorCenterX - panelLeft - pointerSize / 2).coerceIn(pointerMinLeft, pointerMaxLeft)
        val panelTop = (anchorBottomY + dp(3)).coerceAtLeast(dp(6))

        val title = TextView(activity)
        val detail = TextView(activity)
        val cardCount = metricValue()
        val terminalCount = metricValue()
        val processCount = metricValue()
        val progress = ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal)
        val progressText = TextView(activity)
        val action = TextView(activity)
        val chevron = TextView(activity)
        val actionRow = row().apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), 0, dp(8), 0)
            setOnClickListener { onPrimaryAction() }
            addView(action.apply {
                textSize = 13f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
            })
            addView(chevron.apply {
                text = "›"
                textSize = 30f
                includeFontPadding = false
                gravity = Gravity.CENTER
                setTextColor(tokens.textSecondary)
                layoutParams = LinearLayout.LayoutParams(dp(28), ViewGroup.LayoutParams.MATCH_PARENT)
            })
        }
        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), 0)
            background = rounded(tokens.cardBackground, tokens.border, 22)
            elevation = dp(8).toFloat()
            addView(row().apply {
                gravity = Gravity.CENTER_VERTICAL
                addView(View(context).apply {
                    background = rounded(tokens.success, Color.TRANSPARENT, 5, 0)
                }, LinearLayout.LayoutParams(dp(10), dp(10)).apply { setMargins(0, 0, dp(10), 0) })
                addView(TextView(context).apply {
                    text = "运行状态"
                    textSize = 15.5f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(tokens.textPrimary)
                }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            })
            addView(title.apply {
                textSize = 13.5f
                setTextColor(tokens.textPrimary)
                setPadding(0, dp(14), 0, 0)
            })
            addView(detail.apply {
                textSize = 12f
                setTextColor(tokens.textSecondary)
                setLineSpacing(dp(2).toFloat(), 1f)
                setPadding(0, dp(5), 0, 0)
            })
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(18), 0, dp(16))
                addView(metric("▣", "卡片", tokens.primaryStrong, cardCount))
                addView(metric(">_", "终端", Color.rgb(0, 150, 136), terminalCount))
                addView(metric("⌁", "进程", tokens.warning, processCount))
            })
            addView(progress.apply {
                max = 100
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(7))
            })
            addView(progressText.apply {
                textSize = 12f
                setTextColor(tokens.textSecondary)
                setPadding(0, dp(7), 0, 0)
            })
            addView(View(context).apply { setBackgroundColor(tokens.border) },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)).apply {
                    setMargins(-dp(18), dp(18), -dp(18), 0)
                })
            addView(actionRow, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)))
        }
        val popover = FrameLayout(activity).apply {
            clipChildren = false
            clipToPadding = false
            alpha = 0f
            scaleX = 0.92f
            scaleY = 0.92f
            pivotX = pointerLeft + pointerSize / 2f
            pivotY = dp(13).toFloat()
            addView(View(context).apply {
                rotation = 45f
                background = rounded(tokens.cardBackground, Color.TRANSPARENT, 2, 0)
            }, FrameLayout.LayoutParams(pointerSize, pointerSize, Gravity.TOP or Gravity.START).apply {
                leftMargin = pointerLeft
                topMargin = dp(2)
            })
            addView(content, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(11) })
        }
        val created = Dialog(activity)
        dialog = created
        panel = PanelBinding(title, detail, cardCount, terminalCount, processCount, progress, progressText, actionRow, action, chevron)
        created.setContentView(popover)
        created.setOnDismissListener {
            if (dialog == created) {
                dialog = null
                panel = null
            }
        }
        created.show()
        created.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setDimAmount(0f)
            setGravity(Gravity.TOP or Gravity.START)
            attributes = attributes.apply {
                x = panelLeft
                y = panelTop
                dimAmount = 0f
            }
            setLayout(panelWidth, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        popover.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(180L)
            .setInterpolator(PathInterpolator(0.16f, 1f, 0.3f, 1f))
            .start()
        panel?.let { bindPanel(it, latestState) }
    }

    private fun bindPanel(binding: PanelBinding, state: RuntimeStatusUiState) {
        binding.title.text = if (state.visible) state.title else "Ubuntu 环境可用"
        binding.title.setTextColor(if (state.isProblem) tokens.danger else tokens.textPrimary)
        binding.detail.text = if (state.visible) state.detail else ""
        binding.detail.visibility = if (binding.detail.text.isNullOrBlank()) View.GONE else View.VISIBLE
        binding.cardCount.text = state.counts.runningCards.toString()
        binding.terminalCount.text = state.counts.runningTerminals.toString()
        binding.processCount.text = state.counts.runningProcesses.toString()
        bindProgress(binding.progress, binding.progressText, state, hideProblemBar = false)
        val primary = state.primaryAction != RuntimeStatusAction.OpenProcessManagement
        binding.actionRow.background = if (primary) {
            rounded(tokens.primaryStrong, tokens.primaryStrong, 14)
        } else null
        binding.action.text = state.primaryActionLabel
        binding.action.setTextColor(if (primary) tokens.buttonText else tokens.textPrimary)
        binding.chevron.visibility = if (primary) View.GONE else View.VISIBLE
    }

    private fun bindProgress(
        bar: ProgressBar,
        label: TextView,
        state: RuntimeStatusUiState,
        hideProblemBar: Boolean
    ) {
        val showBar = state.showProgress && !(hideProblemBar && state.isProblem)
        bar.visibility = if (showBar) View.VISIBLE else View.GONE
        bar.isIndeterminate = state.progressPercent == null
        bar.progress = state.progressPercent ?: 0
        bar.progressDrawable?.setTint(if (state.isProblem) tokens.danger else tokens.primaryStrong)
        bar.indeterminateDrawable?.setTint(if (state.isProblem) tokens.danger else tokens.primaryStrong)
        label.text = state.progressText.ifBlank {
            if (showBar) "正在执行首次准备" else ""
        }
        label.visibility = if (label.text.isNullOrBlank()) View.GONE else View.VISIBLE
    }

    private fun progressView(state: RuntimeStatusUiState, compact: Boolean): View =
        LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            visibility = if (state.showProgress) View.VISIBLE else View.GONE
            setPadding(0, dp(if (compact) 8 else 14), 0, 0)
            addView(ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
                max = 100
                progress = state.progressPercent ?: 0
                isIndeterminate = state.progressPercent == null
                progressDrawable?.setTint(if (state.isProblem) tokens.danger else tokens.primaryStrong)
                indeterminateDrawable?.setTint(if (state.isProblem) tokens.danger else tokens.primaryStrong)
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(if (compact) 5 else 7)))
            if (state.progressText.isNotBlank()) {
                addView(TextView(context).apply {
                    text = state.progressText
                    textSize = if (compact) 10.5f else 12f
                    setTextColor(tokens.textSecondary)
                    setPadding(0, dp(5), 0, 0)
                })
            }
        }

    private fun metric(icon: String, label: String, accent: Int, value: TextView): View = row().apply {
        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        addView(TextView(context).apply {
            text = icon
            textSize = 18f
            gravity = Gravity.CENTER
            includeFontPadding = false
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(accent)
            background = rounded(tint(accent, 0.88f), Color.TRANSPARENT, 13, 0)
        }, LinearLayout.LayoutParams(dp(48), dp(48)).apply { setMargins(0, 0, dp(12), 0) })
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(context).apply {
                text = label
                textSize = 12f
                setTextColor(tokens.textSecondary)
            })
            addView(value)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
    }

    private fun metricValue(): TextView = TextView(activity).apply {
        textSize = 22f
        typeface = Typeface.DEFAULT_BOLD
        includeFontPadding = false
        setTextColor(tokens.textPrimary)
        setPadding(0, dp(2), 0, 0)
    }

    private fun anchorPoint(
        anchor: View?,
        panelLeft: Int,
        panelWidth: Int,
        fallbackBottomY: Int
    ): Pair<Int, Int> {
        val fallback = (panelLeft + panelWidth / 2) to fallbackBottomY
        if (anchor == null || anchor.width <= 0 || !anchor.isAttachedToWindow) return fallback
        val location = IntArray(2)
        return runCatching {
            anchor.getLocationOnScreen(location)
            val visibleFrame = android.graphics.Rect()
            activity.window.decorView.getWindowVisibleDisplayFrame(visibleFrame)
            (location[0] + anchor.width / 2) to (location[1] + anchor.height - visibleFrame.top)
        }.getOrDefault(fallback)
    }

    private fun row(): LinearLayout = ui.rowWith(activity) {}

    private fun rounded(fill: Int, stroke: Int, radiusDp: Int, strokeWidth: Int = dp(1)) =
        ui.roundedBox(fill, stroke, dp(radiusDp).toFloat(), strokeWidth)

    private fun tint(color: Int, fraction: Float): Int = KiteTheme.tint(color, fraction)

    private fun withAlpha(color: Int, alpha: Int): Int =
        Color.argb(alpha.coerceIn(0, 255), Color.red(color), Color.green(color), Color.blue(color))

    private fun dp(value: Int): Int = ui.dp(value)

    private data class GateBinding(
        val root: FrameLayout,
        val title: TextView,
        val detail: TextView,
        val progress: ProgressBar,
        val progressText: TextView,
        val action: TextView
    )

    private data class PanelBinding(
        val title: TextView,
        val detail: TextView,
        val cardCount: TextView,
        val terminalCount: TextView,
        val processCount: TextView,
        val progress: ProgressBar,
        val progressText: TextView,
        val actionRow: LinearLayout,
        val action: TextView,
        val chevron: TextView
    )
}
