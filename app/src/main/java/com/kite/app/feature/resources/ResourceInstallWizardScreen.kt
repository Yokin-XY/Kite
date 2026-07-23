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
import com.kite.app.R
import com.kite.app.action.KiteInstallPlanActionIntent
import com.kite.app.resources.KiteResourceInstallStore
import com.kite.app.resources.KiteResourceStepTone
import com.kite.app.run.CardRunSurface

/** CardRun 内安装向导的真实视图所有者，只消费 ResourceFeatureUiState。 */
internal class ResourceInstallWizardScreen(
    context: Context,
    private val requestedTargetResourceId: String,
    private val seedResourceIds: List<String>,
    private val onPlanAction: (
        KiteInstallPlanActionIntent,
        (ResourceInstallWizardPlanActionResult) -> Unit,
    ) -> Unit,
    private val onOpenRun: (ResourceInstallWizardRunRequest) -> Unit,
    private val onUninstallFailedResource: (String) -> Unit,
    private val onReportUnavailable: (String) -> Unit,
    private val onRetry: () -> Unit,
    private val onLiveTickRequired: () -> Unit
) {
    private val factory = ResourceFeatureViewFactory(
        context = context,
        tokens = ResourceFeatureTheme.tokens(context),
        onOpenDetail = {},
        onPrimaryAction = {}
    )
    private val contentHost = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(factory.dp(22), factory.dp(16), factory.dp(22), factory.dp(34))
    }
    private val rowBindings = linkedMapOf<String, RowBinding>()
    private var headerBinding: HeaderBinding? = null
    private var primaryButton: TextView? = null
    private var structureSignature = ""
    private var currentState: ResourceInstallWizardViewState? = null
    private var pendingPlanAction: KiteInstallPlanActionIntent? = null

    val root: View = ScrollView(context).apply {
        isFillViewport = true
        contentDescription = context.getString(R.string.resource_wizard_description)
        setBackgroundColor(factory.tokens.pageBackground)
        addView(contentHost)
    }

    fun render(state: ResourceFeatureUiState) {
        if (state.items.isEmpty() && state.phase in setOf(ResourceCatalogPhase.Idle, ResourceCatalogPhase.Loading)) {
            renderStateBlock(
                root.context.getString(R.string.resource_wizard_loading_title),
                root.context.getString(R.string.resource_wizard_loading_summary)
            )
            return
        }
        if (state.items.isEmpty() && state.phase == ResourceCatalogPhase.Failed) {
            renderStateBlock(
                title = root.context.getString(R.string.resource_wizard_failed_title),
                detail = state.errorMessage.orEmpty()
                    .ifBlank { root.context.getString(R.string.resource_wizard_failed_summary) },
                retry = onRetry
            )
            return
        }
        val next = ResourceInstallWizardPresenter.project(
            context = root.context,
            state = state,
            requestedTargetResourceId = requestedTargetResourceId,
            seedResourceIds = seedResourceIds
        )
        ensureStructure(next)
        currentState = next
        bind(next, System.currentTimeMillis())
        if (next.rows.any { it.run?.isLiveForWizard() == true }) onLiveTickRequired()
    }

    fun tick(now: Long = System.currentTimeMillis()): Boolean {
        val state = currentState ?: return false
        state.rows.forEach { row ->
            rowBindings[row.resourceId]?.subtitle?.text = row.subtitle(root.context, now)
        }
        return state.rows.any { it.run?.isLiveForWizard() == true }
    }

    fun dispose() {
        currentState = null
        rowBindings.clear()
        headerBinding = null
        primaryButton = null
    }

    private fun ensureStructure(state: ResourceInstallWizardViewState) {
        val signature = listOf(
            state.targetResourceId,
            state.title,
            state.rows.joinToString("|") { "${it.resourceId}:${it.name}:${it.sourceLabel}" }
        ).joinToString("#")
        if (signature == structureSignature) return
        structureSignature = signature
        rowBindings.clear()
        contentHost.removeAllViews()
        val title = TextView(root.context)
        val detail = TextView(root.context)
        val progress = TextView(root.context)
        contentHost.addView(header(title, detail, progress))
        headerBinding = HeaderBinding(title, detail, progress)
        primaryButton = actionButton().also(contentHost::addView)
        contentHost.addView(factory.sectionTitle(root.context.getString(R.string.resource_wizard_queue_title)).apply {
            setPadding(0, factory.dp(24), 0, factory.dp(12))
        })
        state.rows.forEach { row ->
            val binding = row(row)
            rowBindings[row.resourceId] = binding
            contentHost.addView(binding.root)
        }
    }

    private fun bind(state: ResourceInstallWizardViewState, now: Long) {
        headerBinding?.apply {
            title.text = state.title
            detail.text = state.detail
            progress.text = if (state.totalCount > 0) {
                "${state.completedCount}/${state.totalCount}"
            } else {
                "--"
            }
        }
        bindPrimaryAction(state)
        state.rows.forEach { row -> rowBindings[row.resourceId]?.let { bindRow(it, row, now) } }
    }

    private fun bindPrimaryAction(state: ResourceInstallWizardViewState) {
        val button = primaryButton ?: return
        pendingPlanAction?.let { pending ->
            if (state.primaryIntent != pending || !state.primaryEnabled) pendingPlanAction = null
        }
        val pending = pendingPlanAction
        button.apply {
            text = if (pending == KiteInstallPlanActionIntent.StartNext) {
                root.context.getString(R.string.resource_state_preparing)
            } else {
                state.primaryLabel
            }
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            includeFontPadding = false
            val enabled = state.primaryEnabled && pending == null
            setTextColor(if (enabled) factory.tokens.buttonText else factory.tokens.textSecondary)
            background = factory.roundedBox(
                if (enabled) factory.tokens.primaryStrong else factory.tokens.surface,
                Color.TRANSPARENT,
                factory.dp(18).toFloat()
            )
            alpha = if (enabled) 1f else 0.72f
            isEnabled = enabled
            isClickable = enabled
            setOnClickListener(if (enabled) View.OnClickListener {
                state.primaryIntent?.let { intent ->
                    pendingPlanAction = intent
                    if (intent == KiteInstallPlanActionIntent.StartNext) {
                        text = root.context.getString(R.string.resource_state_preparing)
                        isEnabled = false
                        isClickable = false
                        alpha = 0.72f
                    }
                    onPlanAction(intent, ::acknowledgePlanAction)
                }
            } else null)
        }
    }

    private fun acknowledgePlanAction(result: ResourceInstallWizardPlanActionResult) {
        if (result != ResourceInstallWizardPlanActionResult.Rejected) return
        pendingPlanAction = null
        currentState?.let(::bindPrimaryAction)
    }

    private fun bindRow(binding: RowBinding, row: ResourceInstallWizardRowViewState, now: Long) {
        val tone = toneColor(row.projection.tone)
        val statusLabel = localizedStatusLabel(row)
        binding.root.apply {
            contentDescription = root.context.getString(
                R.string.resource_wizard_row_description,
                row.name,
                statusLabel
            )
            background = factory.roundedBox(
                factory.tokens.cardBackground,
                if (row.isActive) factory.tokens.primarySoft else factory.tokens.border,
                factory.dp(16).toFloat()
            )
            isClickable = true
            isFocusable = true
            setOnClickListener {
                val run = row.run
                if (run == null) {
                    onReportUnavailable(row.resourceId)
                } else {
                    onOpenRun(row.runRequest(CardRunSurface.Report))
                }
            }
        }
        binding.number.apply {
            text = (row.index + 1).toString()
            setTextColor(tone)
            background = factory.roundedBox(colorWithAlpha(tone, 0.11f), Color.TRANSPARENT, factory.dp(12).toFloat())
        }
        binding.subtitle.text = row.subtitle(root.context, now)
        binding.status.apply {
            text = statusLabel
            setTextColor(tone)
            background = factory.roundedBox(colorWithAlpha(tone, 0.11f), Color.TRANSPARENT, factory.dp(11).toFloat())
            isClickable = false
            isFocusable = false
            setOnClickListener(null)
        }
        bindSecondaryAction(binding, row)
    }

    private fun bindSecondaryAction(binding: RowBinding, row: ResourceInstallWizardRowViewState) {
        val surface = row.run?.surface
        val canRecoverFailure = row.projection.failed && !row.projection.uninstalling
        val key = "${row.operation}|${row.run?.instanceId.orEmpty()}|${surface?.name.orEmpty()}|$canRecoverFailure"
        if (binding.secondaryKey == key) return
        binding.secondaryKey = key
        binding.secondaryHost.removeAllViews()
        val label = when (surface) {
            CardRunSurface.Report -> root.context.getString(R.string.resource_wizard_open_report)
            CardRunSurface.Terminal -> root.context.getString(R.string.resource_wizard_open_terminal)
            CardRunSurface.Web -> root.context.getString(R.string.resource_wizard_open_web)
            else -> null
        }
        if (label != null && row.run != null) {
            binding.secondaryHost.addView(inlineButton(label) {
                onOpenRun(row.runRequest(requireNotNull(surface)))
            })
        }
        if (canRecoverFailure) {
            binding.secondaryHost.addView(
                inlineButton(
                    label = root.context.getString(R.string.resource_wizard_cleanup_and_retry),
                    danger = true,
                ) { onUninstallFailedResource(row.resourceId) }
            )
        }
        binding.secondaryHost.visibility = if (binding.secondaryHost.childCount > 0) View.VISIBLE else View.GONE
    }

    private fun ResourceInstallWizardRowViewState.runRequest(surface: CardRunSurface): ResourceInstallWizardRunRequest {
        val snapshot = requireNotNull(run)
        return ResourceInstallWizardRunRequest(
            resourceId = resourceId,
            operation = operation,
            instanceId = snapshot.instanceId,
            surface = surface
        )
    }

    private fun header(title: TextView, detail: TextView, progress: TextView): View =
        LinearLayout(root.context).apply {
            orientation = LinearLayout.VERTICAL
            minimumHeight = factory.dp(136)
            setPadding(factory.dp(18), factory.dp(18), factory.dp(18), factory.dp(18))
            background = factory.roundedBox(
                factory.tokens.cardBackground,
                factory.tokens.border,
                factory.dp(18).toFloat()
            )
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(factory.icon("↓", "teal", "", "", factory.dp(54), factory.dp(8), factory.dp(14).toFloat(), 15f).apply {
                    layoutParams = LinearLayout.LayoutParams(factory.dp(54), factory.dp(54)).apply {
                        setMargins(0, 0, factory.dp(14), 0)
                    }
                })
                addView(LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(title.apply {
                        textSize = 20f
                        typeface = Typeface.DEFAULT_BOLD
                        setTextColor(factory.tokens.textPrimary)
                        maxLines = 1
                        ellipsize = TextUtils.TruncateAt.END
                    })
                    addView(detail.apply {
                        textSize = 12.5f
                        setTextColor(factory.tokens.textSecondary)
                        setPadding(0, factory.dp(6), 0, 0)
                        maxLines = 2
                        ellipsize = TextUtils.TruncateAt.END
                    })
                }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            })
            addView(progress.apply {
                textSize = 13f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(factory.tokens.primaryStrong)
                setPadding(0, factory.dp(14), 0, 0)
            })
        }

    private fun actionButton(): TextView = TextView(root.context).apply {
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, factory.dp(52)).apply {
            setMargins(0, factory.dp(16), 0, 0)
        }
    }

    private fun row(state: ResourceInstallWizardRowViewState): RowBinding {
        val number = TextView(root.context)
        val subtitle = TextView(root.context)
        val status = TextView(root.context)
        val secondaryHost = LinearLayout(root.context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(factory.dp(46), factory.dp(10), 0, 0)
            visibility = View.GONE
        }
        val rootView = LinearLayout(root.context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(factory.dp(14), factory.dp(13), factory.dp(14), factory.dp(13))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, factory.dp(10)) }
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(number.apply {
                    textSize = 13f
                    typeface = Typeface.DEFAULT_BOLD
                    gravity = Gravity.CENTER
                    includeFontPadding = false
                }, LinearLayout.LayoutParams(factory.dp(34), factory.dp(34)).apply {
                    setMargins(0, 0, factory.dp(12), 0)
                })
                addView(LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(TextView(context).apply {
                        text = state.name
                        textSize = 15f
                        typeface = Typeface.DEFAULT_BOLD
                        setTextColor(factory.tokens.textPrimary)
                        maxLines = 1
                        ellipsize = TextUtils.TruncateAt.END
                    })
                    addView(subtitle.apply {
                        textSize = 11.5f
                        setTextColor(factory.tokens.textSecondary)
                        setPadding(0, factory.dp(4), 0, 0)
                    })
                }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                addView(status.apply {
                    textSize = 12.5f
                    typeface = Typeface.DEFAULT_BOLD
                    gravity = Gravity.CENTER
                    includeFontPadding = false
                    setPadding(factory.dp(12), 0, factory.dp(12), 0)
                    minWidth = factory.dp(58)
                }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, factory.dp(26)).apply {
                    setMargins(factory.dp(12), 0, 0, 0)
                })
            })
            addView(secondaryHost)
        }
        return RowBinding(rootView, number, subtitle, status, secondaryHost)
    }

    private fun inlineButton(
        label: String,
        danger: Boolean = false,
        onClick: () -> Unit,
    ): View = TextView(root.context).apply {
        text = label
        contentDescription = label
        textSize = 12f
        typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER
        includeFontPadding = false
        setTextColor(if (danger) factory.tokens.danger else factory.tokens.primaryStrong)
        background = factory.roundedBox(
            if (danger) factory.tokens.dangerSoft else factory.tokens.primarySubtle,
            if (danger) factory.tokens.dangerBorder else factory.tokens.primarySoft,
            factory.dp(13).toFloat()
        )
        setPadding(factory.dp(10), 0, factory.dp(10), 0)
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, factory.dp(28)).apply {
            setMargins(0, 0, factory.dp(8), 0)
        }
        isFocusable = true
        setOnClickListener { onClick() }
    }

    private fun renderStateBlock(title: String, detail: String, retry: (() -> Unit)? = null) {
        val signature = "state:$title:$detail:${retry != null}"
        if (signature == structureSignature) return
        structureSignature = signature
        currentState = null
        rowBindings.clear()
        headerBinding = null
        primaryButton = null
        contentHost.removeAllViews()
        contentHost.addView(factory.stateBlock(title, detail, loading = retry == null, retry = retry))
    }

    private fun toneColor(tone: KiteResourceStepTone): Int = when (tone) {
        KiteResourceStepTone.Primary -> factory.tokens.primaryStrong
        KiteResourceStepTone.Success -> factory.tokens.success
        KiteResourceStepTone.Danger -> factory.tokens.danger
        KiteResourceStepTone.Neutral -> factory.tokens.textSecondary
    }

    private fun localizedStatusLabel(row: ResourceInstallWizardRowViewState): String = when {
        row.projection.uninstalling -> root.context.getString(R.string.resource_state_uninstalling)
        row.projection.failed && row.operation == KiteResourceInstallStore.OP_UNINSTALL ->
            root.context.getString(R.string.resource_state_uninstall_failed)
        row.projection.failed -> root.context.getString(R.string.resource_state_install_failed)
        row.projection.statusLabel == "待获取" -> root.context.getString(R.string.resource_manage_queue_waiting)
        row.projection.statusLabel == "准备中" -> root.context.getString(R.string.resource_state_preparing)
        row.projection.statusLabel == "获取中" -> root.context.getString(R.string.resource_state_installing)
        row.projection.statusLabel == "已完成" -> root.context.getString(R.string.resource_manage_queue_completed)
        row.projection.statusLabel == "已停止" -> root.context.getString(R.string.resource_manage_queue_stopped)
        else -> row.projection.statusLabel
    }

    private fun colorWithAlpha(color: Int, alpha: Float): Int = Color.argb(
        (255 * alpha.coerceIn(0f, 1f)).toInt(),
        Color.red(color),
        Color.green(color),
        Color.blue(color)
    )

    private data class HeaderBinding(
        val title: TextView,
        val detail: TextView,
        val progress: TextView
    )

    private data class RowBinding(
        val root: LinearLayout,
        val number: TextView,
        val subtitle: TextView,
        val status: TextView,
        val secondaryHost: LinearLayout,
        var secondaryKey: String = ""
    )
}
