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
import com.kite.app.run.CardRunHistoryEntry
import com.kite.app.run.CardRunStatus
import com.kite.app.ui.UiKit
import com.kite.app.ui.UiTextRole
import java.util.Calendar

internal class ResourceMoreScreen(
    private val context: Context,
    onBack: () -> Unit,
    private val onCreateHomeCard: () -> Unit,
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
    private var signature: Int? = null

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
        val nextSignature = 31 * (item?.hashCode() ?: 0) + history.hashCode()
        if (signature == nextSignature) return
        signature = nextSignature
        content.removeAllViews()
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
