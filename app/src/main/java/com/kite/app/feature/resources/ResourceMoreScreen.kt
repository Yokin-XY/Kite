package com.kite.app.feature.resources

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ImageView
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
import com.kite.app.R
import com.kite.app.action.KiteResourceActionIntent
import com.kite.app.resources.KiteResourceInstallRecipes
import com.kite.app.resources.KiteResourceInstallStore
import com.kite.app.run.CardRunHistoryEntry
import com.kite.app.run.CardRunStatus
import com.kite.app.ui.UiKit
import com.kite.app.ui.UiTextRole
import java.util.Calendar

internal class ResourceMoreScreen(
    private val context: Context,
    onBack: () -> Unit,
    private val onCreateHomeCard: () -> Unit,
    private val onMaintenanceAction: (KiteResourceActionIntent) -> Unit,
    private val onOpenHistory: (String) -> Unit
) {
    private val environment = ResourceFeatureTheme.environment(context)
    private val tokens = environment.tokens
    private val ui = UiKit(context, environment)
    private val factory = ResourceFeatureViewFactory(context, tokens, {}, {})
    private val content = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(
            dp(environment.foundations.spacing.pageHorizontal),
            dp(environment.foundations.spacing.sectionGap),
            dp(environment.foundations.spacing.pageHorizontal),
            dp(96),
        )
    }
    private var structureSignature: Int? = null
    private var maintenanceSignature: Int? = null
    private var maintenanceStatus: TextView? = null
    private val maintenanceButtons = linkedMapOf<KiteResourceActionIntent, TextView>()

    val root: LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(tokens.pageBackground)
        addView(ui.topBar(context, context.getString(R.string.resource_manage_title), onBack))
        addView(ScrollView(context).apply { addView(content) }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            0,
            1f
        ))
    }

    fun render(item: ResourceItemUiState?, history: List<CardRunHistoryEntry>) {
        val nextStructureSignature = 31 * (item?.descriptor?.hashCode() ?: 0) + history.hashCode()
        if (structureSignature != nextStructureSignature) {
            structureSignature = nextStructureSignature
            maintenanceSignature = null
            maintenanceStatus = null
            maintenanceButtons.clear()
            content.removeAllViews()
        } else if (item != null) {
            bindMaintenance(item)
            return
        } else return
        if (item == null) {
            content.addView(factory.stateBlock(
                context.getString(R.string.resource_more_loading_title),
                context.getString(R.string.resource_more_loading_summary),
                loading = true,
            ))
            return
        }
        content.addView(header(item))
        content.addView(createHomeCardRow(item))
        content.addView(maintenancePanel(item))
        content.addView(historyPanel(history))
        bindMaintenance(item)
    }

    fun acknowledge(intent: KiteResourceActionIntent) {
        maintenanceButtons.values.forEach { button ->
            button.isEnabled = false
            button.alpha = 0.58f
        }
        maintenanceStatus?.text = context.getString(when (intent) {
            KiteResourceActionIntent.CheckUpdate -> R.string.resource_maintenance_checking
            KiteResourceActionIntent.Update -> R.string.resource_maintenance_updating
            KiteResourceActionIntent.Reinstall -> R.string.resource_maintenance_reinstalling
            KiteResourceActionIntent.Repair -> R.string.resource_maintenance_repairing
            KiteResourceActionIntent.Uninstall -> R.string.resource_state_uninstalling
            else -> R.string.resource_action_processing
        })
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
            background = ui.containerBackground(
                tokens.cardBackground,
                tokens.border,
                environment.components.interactiveCard,
            )
            elevation = dp(environment.components.interactiveCard.elevation).toFloat()
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(68)).apply {
                setMargins(0, dp(22), 0, 0)
            }
            addView(ImageView(context).apply {
                setImageDrawable(AppCompatResources.getDrawable(context, R.drawable.ic_material_add))
                imageTintList = ColorStateList.valueOf(tokens.primaryStrong)
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                setPadding(dp(10), dp(10), dp(10), dp(10))
                background = ui.containerBackground(
                    tokens.primarySubtle,
                    tokens.primarySoft,
                    environment.components.iconTile,
                )
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, LinearLayout.LayoutParams(dp(42), dp(42)).apply { setMargins(0, 0, dp(14), 0) })
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(TextView(context).apply {
                    text = context.getString(R.string.resource_more_create_card)
                    textSize = 15f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(tokens.textPrimary)
                })
                addView(TextView(context).apply {
                    text = context.getString(
                        if (canCreate) R.string.resource_more_create_card_summary
                        else R.string.resource_more_create_card_unavailable,
                    )
                    textSize = 12f
                    setTextColor(tokens.textSecondary)
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                    setPadding(0, dp(4), 0, 0)
                })
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(ImageView(context).apply {
                setImageDrawable(AppCompatResources.getDrawable(context, R.drawable.ic_chevron_right_light))
                imageTintList = ColorStateList.valueOf(tokens.textTertiary)
                scaleType = ImageView.ScaleType.CENTER
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, LinearLayout.LayoutParams(dp(24), dp(42)))
            if (canCreate) setOnClickListener { onCreateHomeCard() }
        }
    }

    private fun maintenancePanel(item: ResourceItemUiState): View = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(0, dp(22), 0, 0)
        addView(TextView(context).apply {
            text = context.getString(R.string.resource_maintenance_title)
            ui.applyTextRole(this, UiTextRole.SectionTitle)
            setPadding(0, 0, 0, dp(10))
        })
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = ui.containerBackground(tokens.cardBackground, tokens.border, environment.components.card)
            maintenanceStatus = TextView(context).apply {
                textSize = 13f
                setTextColor(tokens.textSecondary)
                setLineSpacing(dp(3).toFloat(), 1f)
            }
            addView(maintenanceStatus)
            if (item.maintenance.userLifecycleEnabled) {
                addView(maintenanceButton(KiteResourceActionIntent.CheckUpdate), LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(44)
                ).apply { setMargins(0, dp(13), 0, 0) })
                addView(maintenanceButton(KiteResourceActionIntent.Repair), LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(42)
                ).apply { setMargins(0, dp(9), 0, 0) })
                addView(LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    addView(maintenanceButton(KiteResourceActionIntent.Reinstall), actionLayoutParams())
                    addView(maintenanceButton(KiteResourceActionIntent.Uninstall, danger = true), actionLayoutParams(dp(8)))
                }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(42)).apply {
                    setMargins(0, dp(9), 0, 0)
                })
            }
        })
    }

    private fun maintenanceButton(intent: KiteResourceActionIntent, danger: Boolean = false): TextView =
        TextView(context).apply {
            textSize = 12.5f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            includeFontPadding = false
            setTextColor(if (danger) tokens.danger else tokens.primaryStrong)
            background = ui.containerBackground(
                if (danger) tokens.dangerSoft else tokens.primarySubtle,
                if (danger) tokens.dangerBorder else tokens.primarySoft,
                environment.components.control
            )
            maintenanceButtons[intent] = this
        }

    private fun actionLayoutParams(startMargin: Int = 0): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply {
            if (startMargin > 0) setMargins(startMargin, 0, 0, 0)
        }

    private fun bindMaintenance(item: ResourceItemUiState) {
        val state = item.maintenance
        val nextSignature = state.hashCode()
        if (maintenanceSignature == nextSignature) return
        maintenanceSignature = nextSignature
        maintenanceStatus?.text = when {
            !state.userLifecycleEnabled -> context.getString(R.string.resource_maintenance_system_managed)
            item.phase == ResourceItemPhase.NotInstalled -> context.getString(R.string.resource_maintenance_install_first)
            item.phase == ResourceItemPhase.Installing && item.operation == KiteResourceInstallRecipes.OP_UPDATE ->
                context.getString(R.string.resource_maintenance_updating)
            item.phase == ResourceItemPhase.Installing && item.operation == KiteResourceInstallRecipes.OP_REINSTALL ->
                context.getString(R.string.resource_maintenance_reinstalling)
            item.phase == ResourceItemPhase.Installing && item.operation == KiteResourceInstallRecipes.OP_REPAIR ->
                context.getString(R.string.resource_maintenance_repairing)
            item.phase == ResourceItemPhase.Uninstalling -> context.getString(R.string.resource_state_uninstalling)
            state.updateStatus == KiteResourceInstallStore.UPDATE_STATUS_CHECKING ->
                context.getString(R.string.resource_maintenance_checking)
            state.updateStatus == KiteResourceInstallStore.UPDATE_STATUS_AVAILABLE ->
                context.getString(
                    R.string.resource_maintenance_available,
                    state.installedVersion.ifBlank { context.getString(R.string.resource_maintenance_unknown_version) },
                    state.latestVersion
                )
            state.updateStatus == KiteResourceInstallStore.UPDATE_STATUS_CURRENT ->
                context.getString(R.string.resource_maintenance_current, state.installedVersion.ifBlank { state.latestVersion })
            state.updateStatus == KiteResourceInstallStore.UPDATE_STATUS_UNSUPPORTED ->
                context.getString(R.string.resource_maintenance_unsupported)
            state.updateStatus == KiteResourceInstallStore.UPDATE_STATUS_FAILED ->
                context.getString(
                    if (item.operation == KiteResourceInstallRecipes.OP_REINSTALL) {
                        R.string.resource_maintenance_reinstall_failed
                    } else if (item.operation == KiteResourceInstallRecipes.OP_REPAIR) {
                        R.string.resource_maintenance_repair_required
                    } else {
                        R.string.resource_maintenance_check_failed
                    },
                    state.statusSummary
                )
            else -> context.getString(
                R.string.resource_maintenance_installed_version,
                state.installedVersion.ifBlank { context.getString(R.string.resource_maintenance_unknown_version) }
            )
        }

        val primaryIntent = if (state.updateEnabled) KiteResourceActionIntent.Update else KiteResourceActionIntent.CheckUpdate
        maintenanceButtons[KiteResourceActionIntent.CheckUpdate]?.apply {
            val enabled = if (state.updateEnabled) true else state.checkUpdateEnabled
            text = when {
                item.phase == ResourceItemPhase.Installing && item.operation == KiteResourceInstallRecipes.OP_UPDATE ->
                    context.getString(R.string.resource_state_updating)
                state.updateEnabled -> context.getString(R.string.resource_maintenance_update_to, state.latestVersion)
                else -> context.getString(R.string.resource_action_check_update)
            }
            isEnabled = enabled
            alpha = if (enabled) 1f else 0.5f
            setOnClickListener(if (enabled) View.OnClickListener { onMaintenanceAction(primaryIntent) } else null)
        }
        maintenanceButtons[KiteResourceActionIntent.Repair]?.visibility =
            if (state.repairEnabled) View.VISIBLE else View.GONE
        bindMaintenanceButton(KiteResourceActionIntent.Repair, state.repairEnabled, R.string.resource_action_repair)
        bindMaintenanceButton(KiteResourceActionIntent.Reinstall, state.reinstallEnabled, R.string.resource_action_reinstall)
        bindMaintenanceButton(KiteResourceActionIntent.Uninstall, state.uninstallEnabled, R.string.resource_action_uninstall)
    }

    private fun bindMaintenanceButton(intent: KiteResourceActionIntent, enabled: Boolean, label: Int) {
        maintenanceButtons[intent]?.apply {
            text = context.getString(label)
            isEnabled = enabled
            alpha = if (enabled) 1f else 0.5f
            setOnClickListener(if (enabled) View.OnClickListener { onMaintenanceAction(intent) } else null)
        }
    }

    private fun historyPanel(history: List<CardRunHistoryEntry>): View = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(0, dp(18), 0, dp(4))
        addView(TextView(context).apply {
            text = context.getString(R.string.resource_more_history_title)
            ui.applyTextRole(this, UiTextRole.SectionTitle)
            setPadding(0, 0, 0, dp(10))
        })
        if (history.isEmpty()) addView(emptyHistory()) else history.forEachIndexed { index, entry ->
            addView(historyRow(entry, index + 1))
        }
    }

    private fun emptyHistory(): View = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(12), dp(16), dp(12))
        background = ui.containerBackground(tokens.cardBackground, tokens.border, environment.components.card)
        addView(TextView(context).apply {
            text = context.getString(R.string.resource_more_history_empty)
            textSize = 14.5f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(tokens.textPrimary)
        })
        addView(TextView(context).apply {
            text = context.getString(R.string.resource_more_history_empty_summary)
            textSize = 12f
            setTextColor(tokens.textSecondary)
            setPadding(0, dp(8), 0, 0)
        })
    }

    private fun historyRow(entry: CardRunHistoryEntry, ordinal: Int): View = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(14), dp(11), dp(12), dp(11))
        background = ui.containerBackground(
            tokens.cardBackground,
            tokens.border,
            environment.components.interactiveCard,
        )
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
                text = "${localizedRunStatus(entry.status)} · ${duration(entry)} · ${progress(entry)}"
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
        addView(ImageView(context).apply {
            setImageDrawable(AppCompatResources.getDrawable(context, R.drawable.ic_chevron_right_light))
            imageTintList = ColorStateList.valueOf(tokens.textTertiary)
            scaleType = ImageView.ScaleType.CENTER
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LinearLayout.LayoutParams(dp(22), ViewGroup.LayoutParams.MATCH_PARENT))
    }

    private fun progress(entry: CardRunHistoryEntry): String {
        val total = entry.stepCount.takeIf { it > 0 } ?: entry.steps.size
        if (total <= 0) return context.getString(R.string.resource_more_no_steps)
        val done = when {
            entry.status == CardRunStatus.Completed -> total
            entry.currentStepIndex < 0 -> 0
            entry.isClosed() -> (entry.currentStepIndex + 1).coerceIn(0, total)
            else -> entry.currentStepIndex.coerceIn(0, total - 1) + 1
        }
        return context.getString(R.string.resource_more_steps, done, total)
    }

    private fun duration(entry: CardRunHistoryEntry): String {
        val endAt = entry.endedAt ?: if (entry.isClosed()) entry.updatedAt else System.currentTimeMillis()
        val seconds = ((endAt - entry.startedAt).coerceAtLeast(0L) / 1000L)
        return if (seconds < 3600L) String.format("%02d:%02d", seconds / 60L, seconds % 60L)
        else if (seconds < 86400L) context.getString(R.string.resource_more_hours, seconds / 3600L)
        else context.getString(R.string.resource_more_days, seconds / 86400L)
    }

    private fun timeline(entry: CardRunHistoryEntry): String {
        val end = entry.endedAt ?: entry.updatedAt.takeIf { entry.isClosed() }
        return context.getString(
            R.string.resource_more_timeline,
            context.getString(R.string.resource_more_started, clock(entry.startedAt)),
            end?.let { context.getString(R.string.resource_more_ended, clock(it)) }
                ?: context.getString(R.string.resource_more_in_progress),
        )
    }

    private fun clock(timestamp: Long): String {
        val then = Calendar.getInstance().apply { timeInMillis = timestamp }
        return String.format("%02d:%02d", then.get(Calendar.HOUR_OF_DAY), then.get(Calendar.MINUTE))
    }

    private fun statusColor(entry: CardRunHistoryEntry): Int = when (entry.status) {
        CardRunStatus.Failed, CardRunStatus.BridgeUnavailable -> tokens.danger
        CardRunStatus.Completed -> tokens.success
        CardRunStatus.Stopped -> tokens.info
        CardRunStatus.CleanupPending -> tokens.warning
        CardRunStatus.Starting, CardRunStatus.Running, CardRunStatus.WaitingTerminal,
        CardRunStatus.AlreadyRunning, CardRunStatus.Opened -> tokens.primaryStrong
        else -> tokens.textSecondary
    }

    private fun localizedRunStatus(status: CardRunStatus): String = context.getString(when (status) {
        CardRunStatus.Unknown -> R.string.runtime_management_status_unknown
        CardRunStatus.Stopped -> R.string.runtime_management_status_stopped
        CardRunStatus.Starting -> R.string.runtime_management_status_starting
        CardRunStatus.Running -> R.string.runtime_management_status_running
        CardRunStatus.WaitingTerminal -> R.string.runtime_management_status_waiting_terminal
        CardRunStatus.AlreadyRunning -> R.string.runtime_management_status_already_running
        CardRunStatus.Opened -> R.string.runtime_management_status_opened
        CardRunStatus.Completed -> R.string.runtime_management_status_completed
        CardRunStatus.Failed -> R.string.runtime_management_status_failed
        CardRunStatus.Stopping -> R.string.runtime_management_status_stopping
        CardRunStatus.CleanupPending -> R.string.runtime_management_status_cleanup_pending
        CardRunStatus.BridgeUnavailable -> R.string.runtime_management_status_bridge_unavailable
    })

    private fun dp(value: Int): Int = factory.dp(value)
}
