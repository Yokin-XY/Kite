package com.kite.app.feature.home

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.kite.app.R
import com.kite.app.recipe.KiteRecipe
import com.kite.app.recipe.KiteRecipeIcon
import com.kite.app.run.CardRunState
import com.kite.app.run.CardRunStatus
import com.kite.app.run.KiteRunPrimaryAction
import com.kite.app.run.KiteRunUiTone
import com.kite.app.theme.KiteTheme
import com.kite.app.theme.ThemeComponentRecipes
import com.kite.app.theme.ThemeFoundations
import com.kite.app.theme.ThemeTokens
import com.kite.app.ui.theme.kiteThemeEnvironment
import com.kite.app.ui.RecipeIconBitmapRepository
import com.kite.app.ui.UiKit
import java.util.Date
import java.util.Locale

internal object HomeFeatureTheme {
    fun environment(context: Context) = context.kiteThemeEnvironment()
}

internal data class HomeCardBinding(
    val recipeId: String,
    val root: View,
    val statusHost: FrameLayout,
    val cueView: TextView,
    val actionButton: TextView,
    val actionSubline: TextView,
    var item: HomeRecipeItemUiState
)

/** 首页卡片的视图工厂；只消费 UiState，不读取文件目录或运行 Store。 */
internal class HomeFeatureViewFactory(
    private val context: Context,
    internal val tokens: ThemeTokens,
    internal val foundations: ThemeFoundations = KiteTheme.foundations,
    internal val components: ThemeComponentRecipes = KiteTheme.catalog.stylePacks.first().components,
    private val onOpenEditor: (String) -> Unit,
    private val onPrimaryAction: (String) -> Unit
) {
    private val ui = UiKit(context, tokens)

    fun dp(value: Int): Int = ui.dp(value)

    fun roundedBox(
        fill: Int,
        stroke: Int,
        radius: Float,
        strokeWidth: Int = dp(1),
        dashWidth: Float = 0f,
        dashGap: Float = 0f
    ) = ui.roundedBox(fill, stroke, radius, strokeWidth, dashWidth, dashGap)

    fun stateBlock(title: String, detail: String, retry: (() -> Unit)? = null): View =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(18), dp(34), dp(18), dp(30))
            addView(TextView(context).apply {
                text = title
                textSize = 16f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setTextColor(tokens.textPrimary)
            })
            addView(TextView(context).apply {
                text = detail
                textSize = 12.5f
                gravity = Gravity.CENTER
                setTextColor(tokens.textSecondary)
                setPadding(0, dp(7), 0, 0)
            })
            retry?.let { action ->
                addView(TextView(context).apply {
                    text = context.getString(R.string.common_retry)
                    textSize = 13f
                    typeface = Typeface.DEFAULT_BOLD
                    gravity = Gravity.CENTER
                    setTextColor(tokens.primaryStrong)
                    background = roundedBox(tokens.primarySubtle, Color.TRANSPARENT, dp(16).toFloat())
                    setPadding(dp(22), dp(9), dp(22), dp(9))
                    setOnClickListener { action() }
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply { setMargins(0, dp(16), 0, 0) }
                })
            }
        }

    fun card(item: HomeRecipeItemUiState, groupLabel: String): HomeCardBinding {
        val statusHost = FrameLayout(context)
        val cueView = TextView(context)
        val actionButton = TextView(context)
        val actionSubline = TextView(context)
        val root = FrameLayout(context).apply {
            background = roundedBox(
                tokens.cardBackground,
                tokens.border,
                dp(components.interactiveCard.radius).toFloat(),
                dp(components.interactiveCard.strokeWidth),
            )
            elevation = dp(components.interactiveCard.elevation).toFloat()
            isClickable = true
            isFocusable = true
            setOnClickListener { onOpenEditor(item.recipeId) }
            addView(recipeIcon(item.recipe, dp(38), 18f), FrameLayout.LayoutParams(
                dp(38),
                dp(38),
                Gravity.START or Gravity.TOP
            ).apply { setMargins(dp(13), dp(13), 0, 0) })
            addView(statusHost, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                dp(26),
                Gravity.END or Gravity.TOP
            ).apply { setMargins(0, dp(14), dp(13), 0) })
            addView(recipeName(item.recipe.name), FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.START or Gravity.TOP
            ).apply { setMargins(dp(15), dp(58), dp(15), 0) })
            addView(TextView(context).apply {
                text = groupLabel.ifBlank { context.getString(R.string.home_ungrouped) }
                textSize = 9.8f
                includeFontPadding = false
                setTextColor(tokens.textTertiary)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            }, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.START or Gravity.TOP
            ).apply { setMargins(dp(15), dp(78), dp(15), 0) })
            addView(cueView, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.START or Gravity.BOTTOM
            ).apply { setMargins(dp(15), 0, dp(96), dp(20)) })
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                addView(actionButton, LinearLayout.LayoutParams(dp(66), dp(31)).apply {
                    setMargins(0, 0, 0, dp(4))
                })
                addView(actionSubline, LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(13)
                ))
            }, FrameLayout.LayoutParams(dp(82), dp(50), Gravity.END or Gravity.BOTTOM).apply {
                setMargins(0, 0, dp(11), dp(8))
            })
        }
        return HomeCardBinding(
            recipeId = item.recipeId,
            root = root,
            statusHost = statusHost,
            cueView = cueView,
            actionButton = actionButton,
            actionSubline = actionSubline,
            item = item
        ).also { bind(it, item) }
    }

    fun bind(binding: HomeCardBinding, item: HomeRecipeItemUiState) {
        binding.item = item
        val badge = localizedBadge(item)
        val action = localizedAction(item.projection.primaryAction)
        binding.root.contentDescription = if (badge == null) {
            context.getString(R.string.home_card_description_no_status, item.recipe.name, action)
        } else {
            context.getString(R.string.home_card_description, item.recipe.name, badge, action)
        }
        bindStatus(binding.statusHost, item)
        bindCue(binding.cueView, item)
        bindAction(binding.actionButton, item)
        bindSubline(binding.actionSubline, item.run)
    }

    fun acknowledge(binding: HomeCardBinding?) {
        binding ?: return
        binding.actionButton.apply {
            text = when (binding.item.projection.primaryAction) {
                KiteRunPrimaryAction.Stop,
                KiteRunPrimaryAction.ContinueStop -> context.getString(R.string.home_action_stopping)
                KiteRunPrimaryAction.Start,
                KiteRunPrimaryAction.Retry -> context.getString(R.string.home_action_starting)
                KiteRunPrimaryAction.Busy,
                KiteRunPrimaryAction.Blocked -> context.getString(R.string.home_action_busy)
            }
            isEnabled = false
            alpha = 0.62f
            setOnClickListener(null)
        }
    }

    fun refreshElapsed(binding: HomeCardBinding) {
        bindSubline(binding.actionSubline, binding.item.run)
    }

    private fun bindStatus(host: FrameLayout, item: HomeRecipeItemUiState) {
        host.removeAllViews()
        val label = localizedBadge(item) ?: return
        val tone = colors(item.projection.tone)
        host.addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(8), 0, dp(9), 0)
            background = roundedBox(tone.background, Color.TRANSPARENT, dp(13).toFloat())
            addView(View(context).apply {
                background = roundedBox(tone.text, Color.TRANSPARENT, dp(4).toFloat())
            }, LinearLayout.LayoutParams(dp(6), dp(6)).apply { setMargins(0, 0, dp(5), 0) })
            addView(TextView(context).apply {
                text = label
                textSize = 10.5f
                includeFontPadding = false
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(tone.text)
            })
        }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(26)))
    }

    private fun bindCue(view: TextView, item: HomeRecipeItemUiState) {
        view.apply {
            text = stepCue(item.recipe, item.run)
            textSize = 10.4f
            includeFontPadding = false
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(
                when {
                    item.projection.problem -> tokens.danger
                    item.projection.live -> tokens.success
                    else -> tokens.textSecondary
                }
            )
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
    }

    private fun bindAction(view: TextView, item: HomeRecipeItemUiState) {
        val projection = item.projection
        val fill = when (projection.primaryAction) {
            KiteRunPrimaryAction.Retry -> tokens.danger
            KiteRunPrimaryAction.Stop,
            KiteRunPrimaryAction.ContinueStop -> tokens.warning
            else -> tokens.primaryStrong
        }
        view.apply {
            text = localizedAction(projection.primaryAction)
            textSize = 12.5f
            includeFontPadding = false
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            alpha = if (projection.primaryActionEnabled) 1f else 0.62f
            isEnabled = projection.primaryActionEnabled
            background = roundedBox(fill, fill, dp(16).toFloat())
            setOnClickListener(null)
            if (projection.primaryActionEnabled) {
                setOnClickListener { onPrimaryAction(item.recipeId) }
            }
        }
    }

    private fun bindSubline(view: TextView, state: CardRunState) {
        view.apply {
            text = when {
                state.status == CardRunStatus.Unknown -> ""
                state.status == CardRunStatus.CleanupPending ->
                    context.getString(R.string.home_run_cleanup_pending)
                state.status == CardRunStatus.Failed || state.status == CardRunStatus.BridgeUnavailable ->
                    context.getString(R.string.home_run_stopped_elapsed, formatElapsed(state))
                state.isBusy() || state.isActive() || state.status == CardRunStatus.Opened ->
                    context.getString(R.string.home_run_active_elapsed, formatElapsed(state))
                else -> context.getString(R.string.home_run_last, formatLastRunTime(state.updatedAt))
            }
            textSize = 9.5f
            includeFontPadding = false
            gravity = Gravity.CENTER
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setTextColor(
                when (state.status) {
                    CardRunStatus.Failed, CardRunStatus.BridgeUnavailable -> tokens.danger
                    CardRunStatus.CleanupPending -> tokens.warning
                    else -> tokens.textSecondary
                }
            )
        }
    }

    private fun recipeName(raw: String): TextView = TextView(context).apply {
        val title = compactTitle(raw)
        text = title.first
        textSize = title.second
        includeFontPadding = false
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(tokens.textPrimary)
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.MIDDLE
    }

    private fun compactTitle(raw: String): Pair<String, Float> {
        val value = raw.trim().ifBlank { context.getString(R.string.home_unnamed_card) }
        val bytes = value.toByteArray(Charsets.UTF_8).size
        val progress = ((bytes - 12).coerceAtLeast(0).toFloat() / 8f).coerceIn(0f, 1f)
        val size = 13.2f - (3.2f * progress)
        if (bytes <= 20) return value to size
        var prefix = value
        while (prefix.toByteArray(Charsets.UTF_8).size > 12 && prefix.isNotEmpty()) prefix = prefix.dropLast(1)
        var suffix = value
        while (suffix.toByteArray(Charsets.UTF_8).size > 7 && suffix.isNotEmpty()) suffix = suffix.drop(1)
        return "$prefix…$suffix" to size
    }

    private fun stepCue(recipe: KiteRecipe, state: CardRunState): String {
        val steps = recipe.steps
        if (steps.isEmpty()) return context.getString(R.string.home_no_steps)
        val total = (state.stepCount.takeIf { it > 0 } ?: steps.size).coerceAtLeast(1)
        return if (state.isBusy() || state.isActive() || state.status == CardRunStatus.Opened ||
            state.status == CardRunStatus.Failed || state.status == CardRunStatus.BridgeUnavailable ||
            state.status == CardRunStatus.CleanupPending
        ) {
            val index = state.currentStepIndex.coerceIn(0, total - 1)
            context.getString(
                R.string.home_step_progress,
                stepKind(steps.getOrNull(index) ?: steps.first()),
                index + 1,
                total
            )
        } else {
            val first = stepKind(steps.first())
            if (total == 1) first else context.getString(R.string.home_step_count, first, total)
        }
    }

    private fun stepKind(step: com.kite.app.recipe.KiteRecipeStep): String = when (step.type) {
        KiteRecipe.STEP_SHELL -> context.getString(R.string.home_step_command)
        KiteRecipe.STEP_TERMINAL -> context.getString(R.string.home_step_terminal)
        KiteRecipe.STEP_OPEN_WEB -> context.getString(R.string.home_step_web)
        KiteRecipe.STEP_ANDROID_ACTION -> context.getString(R.string.home_step_native)
        else -> context.getString(R.string.home_step_card)
    }

    private fun recipeIcon(recipe: KiteRecipe, size: Int, fallbackTextSize: Float): View {
        val tone = KiteTheme.accent(recipe.card.accent.ifBlank { "primary" }, tokens)
        val fallback = TextView(context).apply {
            text = iconGlyph(recipe.icon.name)
            textSize = fallbackTextSize
            includeFontPadding = false
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(tone.strong)
            background = roundedBox(
                KiteTheme.tint(tone.strong, 0.88f),
                tone.border,
                dp(components.iconTile.radius).toFloat()
            )
        }
        if (recipe.icon.type != KiteRecipeIcon.TYPE_IMAGE || recipe.icon.source.isBlank()) return fallback
        return FrameLayout(context).apply {
            background = roundedBox(
                tokens.surface,
                tone.border,
                dp(components.iconTile.radius).toFloat()
            )
            clipToOutline = true
            addView(fallback, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            ))
            RecipeIconBitmapRepository.load(context, recipe.icon.source, size) { bitmap ->
                if (parent == null) return@load
                removeAllViews()
                addView(ImageView(context).apply {
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    setImageBitmap(bitmap)
                }, FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                ))
            }
        }
    }

    private fun iconGlyph(name: String): String = when (name) {
        "terminal" -> ">_"
        "web" -> "◎"
        "bot" -> "AI"
        "file" -> context.getString(R.string.home_icon_file)
        "music" -> "♪"
        "shopping" -> context.getString(R.string.home_icon_shopping)
        "logs" -> context.getString(R.string.home_icon_logs)
        "tools" -> "⚙"
        "code" -> "{ }"
        "server" -> "▷"
        "more" -> "…"
        else -> "◎"
    }

    private fun colors(tone: KiteRunUiTone): SemanticColors = when (tone) {
        KiteRunUiTone.Info -> SemanticColors(tokens.info, tokens.infoSoft)
        KiteRunUiTone.Success -> SemanticColors(tokens.success, tokens.successSoft)
        KiteRunUiTone.Warning -> SemanticColors(tokens.warning, tokens.warningSoft)
        KiteRunUiTone.Danger -> SemanticColors(tokens.danger, tokens.dangerSoft)
        KiteRunUiTone.Neutral -> SemanticColors(tokens.textSecondary, tokens.surface)
    }

    private fun formatElapsed(state: CardRunState): String {
        val endAt = if (state.isBusy() || state.isActive() || state.status == CardRunStatus.Opened) {
            System.currentTimeMillis()
        } else {
            state.updatedAt
        }
        val seconds = ((endAt - state.createdAt).coerceAtLeast(0L) / 1000L)
        return when {
            seconds < 3600L -> String.format(Locale.US, "%02d:%02d", seconds / 60L, seconds % 60L)
            seconds < 86400L -> context.getString(R.string.home_duration_hours, seconds / 3600L)
            else -> context.getString(R.string.home_duration_days, seconds / 86400L)
        }
    }

    private fun formatLastRunTime(timestamp: Long): String {
        val ageMs = (System.currentTimeMillis() - timestamp).coerceAtLeast(0L)
        if (ageMs < 60_000L) return context.getString(R.string.home_just_now)
        if (ageMs < 30L * 60_000L) {
            return context.getString(R.string.home_minutes_ago, ageMs / 60_000L)
        }
        val nowDay = java.time.LocalDate.now()
        val thenDay = java.time.Instant.ofEpochMilli(timestamp)
            .atZone(java.time.ZoneId.systemDefault())
            .toLocalDate()
        if (nowDay == thenDay) {
            return android.text.format.DateFormat.getTimeFormat(context).format(Date(timestamp))
        }
        if (nowDay.minusDays(1L) == thenDay) return context.getString(R.string.terminal_time_yesterday)
        return android.text.format.DateFormat.getDateFormat(context).format(Date(timestamp))
    }

    private fun localizedBadge(item: HomeRecipeItemUiState): String? = when {
        item.projection.problem -> context.getString(R.string.home_status_failed)
        item.run.status == CardRunStatus.CleanupPending -> context.getString(R.string.home_status_cleanup_pending)
        item.run.status == CardRunStatus.WaitingTerminal || item.run.status == CardRunStatus.Opened ->
            context.getString(R.string.home_status_manual_action)
        item.projection.live -> context.getString(R.string.home_status_running)
        else -> null
    }

    private fun localizedAction(action: KiteRunPrimaryAction): String = context.getString(
        when (action) {
            KiteRunPrimaryAction.Start -> R.string.home_action_start
            KiteRunPrimaryAction.Stop -> R.string.home_action_stop
            KiteRunPrimaryAction.ContinueStop -> R.string.home_action_continue_stop
            KiteRunPrimaryAction.Retry -> R.string.home_action_retry
            KiteRunPrimaryAction.Busy -> R.string.home_action_busy
            KiteRunPrimaryAction.Blocked -> R.string.home_action_wait
        }
    )

    private data class SemanticColors(val text: Int, val background: Int)
}
