package com.kite.app.feature.runsurface

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.kite.app.R
import com.kite.app.agent.contract.AgentConfigCategory
import com.kite.app.agent.contract.AgentConfigOption
import com.kite.app.agent.contract.AgentConfigValue
import com.kite.app.theme.ThemeTokens
import com.kite.app.ui.UiKit

/**
 * 会话即时配置选择器。
 *
 * 这里只拥有弹层导航和临时供应商选择；配置事实仍由运行会话与调用方持有。
 */
internal class AgentSessionConfigurationPanel(
    private val context: Context,
    private val tokens: ThemeTokens,
    private val overlay: FrameLayout,
    private val optionsProvider: () -> List<AgentConfigOption>,
    private val pendingProvider: () -> Boolean,
    private val viewportProvider: () -> AgentSessionConfigurationViewport,
    private val onUpdateConfiguration: (String, AgentConfigValue) -> Unit,
) {
    private val ui = UiKit(context, tokens)
    private var route = Route.Overview
    private var selectedModelGroupId: String? = null
    private var contentHost: FrameLayout? = null

    val isVisible: Boolean
        get() = overlay.visibility == View.VISIBLE

    fun show() {
        if (pendingProvider()) return
        route = Route.Overview
        selectedModelGroupId = resolveSelectedGroup(
            options = optionsProvider(),
            requestedGroupId = null,
        )?.id
        rebuild(animateContent = false)
        overlay.apply {
            visibility = View.VISIBLE
            alpha = 0f
            setOnClickListener { close() }
            animate()
                .alpha(1f)
                .setDuration(150L)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }
    }

    fun close(animate: Boolean = true) {
        if (!isVisible) return
        if (!animate) {
            overlay.animate().cancel()
            overlay.visibility = View.GONE
            overlay.removeAllViews()
            contentHost = null
            return
        }
        overlay.animate()
            .alpha(0f)
            .setDuration(110L)
            .withEndAction {
                overlay.visibility = View.GONE
                overlay.removeAllViews()
                contentHost = null
            }
            .start()
    }

    fun refresh(animateContent: Boolean) {
        if (!isVisible && animateContent) return
        rebuild(animateContent)
    }

    fun showOverview(animateContent: Boolean) {
        route = Route.Overview
        if (isVisible) rebuild(animateContent)
    }

    private fun rebuild(animateContent: Boolean) {
        val options = optionsProvider()
        val viewport = viewportProvider()
        val availableWidth = viewport.availableWidth - ui.dp(36)
        val primaryPanelWidth = minOf(availableWidth, ui.dp(220))
        val panelWidth = minOf(availableWidth, ui.dp(286))
        val maxHeight = AgentSurfaceNavigationPolicy.sessionPanelMaxHeight(
            viewportHeight = viewport.viewportHeight,
            composerHeight = viewport.composerHeight,
            topBarHeight = viewport.topBarHeight,
            preferredHeight = ui.dp(360),
            minimumHeight = ui.dp(180),
            outerSpacing = ui.dp(32),
        )
        contentHost?.takeIf { isVisible }?.let { host ->
            host.removeAllViews()
            host.addView(
                buildPanelContent(options, maxHeight, primaryPanelWidth, animateContent),
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                ),
            )
            return
        }
        val newContentHost = FrameLayout(context).apply {
            addView(
                buildPanelContent(options, maxHeight, primaryPanelWidth, animateChild = false),
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                ),
            )
        }
        contentHost = newContentHost
        overlay.removeAllViews()
        overlay.addView(FrameLayout(context).apply {
            elevation = ui.dp(12).toFloat()
            background = ColorDrawable(android.graphics.Color.TRANSPARENT)
            addView(
                newContentHost,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                ),
            )
        }, FrameLayout.LayoutParams(
            panelWidth,
            maxHeight,
            Gravity.START or Gravity.BOTTOM,
        ).apply {
            marginStart = ui.dp(28)
            bottomMargin = viewport.composerHeight + ui.dp(14)
        })
    }

    private fun buildPanelContent(
        options: List<AgentConfigOption>,
        maxHeight: Int,
        primaryPanelWidth: Int,
        animateChild: Boolean,
    ): View {
        val overview = SessionConfigurationScrollView(context, maxHeight).apply {
            isFillViewport = false
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            elevation = ui.dp(8).toFloat()
            background = ui.roundedBox(
                tokens.surfaceElevated,
                android.graphics.Color.TRANSPARENT,
                ui.dp(20).toFloat(),
            )
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(ui.dp(6), ui.dp(6), ui.dp(6), ui.dp(6))
                buildOverview(this, options)
            }, ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ))
        }
        return FrameLayout(context).apply {
            addView(overview, FrameLayout.LayoutParams(
                primaryPanelWidth,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.START or Gravity.BOTTOM,
            ))
            val child = when (route) {
                Route.Overview -> null
                Route.ModelProviders -> buildProviders(options, maxHeight)
                Route.Models -> buildModels(options, maxHeight)
            }
            child?.let { childPanel ->
                addView(childPanel, FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.START or Gravity.BOTTOM,
                ).apply { marginStart = ui.dp(8) })
                if (animateChild) {
                    childPanel.alpha = 0f
                    childPanel.translationY = ui.dp(10).toFloat()
                    childPanel.post {
                        childPanel.animate()
                            .alpha(1f)
                            .translationY(0f)
                            .setDuration(150L)
                            .setInterpolator(DecelerateInterpolator())
                            .start()
                    }
                }
            }
        }
    }

    private fun buildOverview(host: LinearLayout, options: List<AgentConfigOption>) {
        val thoughtLevel = options.firstOrNull { it.category == AgentConfigCategory.ThoughtLevel }
        val model = options.filterIsInstance<AgentConfigOption.Select>()
            .firstOrNull { it.category == AgentConfigCategory.Model }
        val groups = model?.let(::modelGroups).orEmpty()
        val selectedGroup = AgentSurfaceNavigationPolicy.resolveModelChoiceGroup(
            groups = groups,
            currentModelValue = model?.currentValue,
            requestedGroupId = selectedModelGroupId,
        )
        if (selectedModelGroupId == null || groups.none { it.id == selectedModelGroupId }) {
            selectedModelGroupId = selectedGroup?.id
        }

        host.addView(sectionLabel("推理强度"))
        thoughtLevel?.let { option ->
            when (option) {
                is AgentConfigOption.Select -> option.choices.forEach { choice ->
                    host.addView(choiceRow(
                        title = choice.name,
                        description = choice.description,
                        selected = choice.value == option.currentValue,
                        contentDescription = "${option.settingTitle()}，${choice.name}",
                        onClick = {
                            onUpdateConfiguration(option.id, AgentConfigValue.Select(choice.value))
                        },
                    ))
                }
                is AgentConfigOption.Toggle -> host.addView(choiceRow(
                    title = option.settingTitle(),
                    description = option.description ?: if (option.currentValue) "当前已开启" else "当前已关闭",
                    selected = option.currentValue,
                    contentDescription = "${option.settingTitle()}，${option.valueLabel()}",
                    onClick = {
                        onUpdateConfiguration(option.id, AgentConfigValue.Toggle(!option.currentValue))
                    },
                ))
            }
        }
        host.addView(View(context).apply {
            setBackgroundColor(tokens.border)
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(1)).apply {
            setMargins(ui.dp(13), ui.dp(7), ui.dp(13), ui.dp(7))
        })
        host.addView(navigationRow(
            title = "模型",
            value = model?.valueLabel() ?: "暂无可选模型",
            enabled = model != null && model.choices.isNotEmpty(),
            onClick = {
                route = Route.Models
                rebuild(animateContent = true)
            },
        ))
        host.addView(navigationRow(
            title = "供应商",
            value = selectedGroup?.name ?: "暂无可选供应商",
            enabled = groups.isNotEmpty(),
            onClick = {
                route = Route.ModelProviders
                rebuild(animateContent = true)
            },
        ))
    }

    private fun buildProviders(options: List<AgentConfigOption>, maxHeight: Int): View {
        val model = options.filterIsInstance<AgentConfigOption.Select>()
            .firstOrNull { it.category == AgentConfigCategory.Model }
        val groups = model?.let(::modelGroups).orEmpty()
        val selectedGroup = AgentSurfaceNavigationPolicy.resolveModelChoiceGroup(
            groups = groups,
            currentModelValue = model?.currentValue,
            requestedGroupId = selectedModelGroupId,
        )
        return childPanel("供应商", selectedGroup?.name ?: "暂无可选供应商", maxHeight) { host ->
            if (model == null || groups.isEmpty()) {
                host.addView(message("当前 Agent 没有可选供应商"))
                return@childPanel
            }
            groups.forEach { group ->
                host.addView(choiceRow(
                    title = group.name,
                    description = null,
                    selected = group.id == selectedGroup?.id,
                    contentDescription = "供应商 ${group.name}",
                    onClick = {
                        selectedModelGroupId = group.id
                        route = Route.Models
                        rebuild(animateContent = true)
                    },
                ))
            }
        }
    }

    private fun buildModels(options: List<AgentConfigOption>, maxHeight: Int): View {
        val model = options.filterIsInstance<AgentConfigOption.Select>()
            .firstOrNull { it.category == AgentConfigCategory.Model }
        val groups = model?.let(::modelGroups).orEmpty()
        val group = AgentSurfaceNavigationPolicy.resolveModelChoiceGroup(
            groups = groups,
            currentModelValue = model?.currentValue,
            requestedGroupId = selectedModelGroupId,
        )
        val choices = group?.choices ?: model?.choices.orEmpty()
        selectedModelGroupId = group?.id
        return childPanel("模型", model?.valueLabel() ?: "暂无可选模型", maxHeight) { host ->
            if (model == null || choices.isEmpty()) {
                host.addView(message("当前供应商没有可选模型"))
                return@childPanel
            }
            choices.forEach { choice ->
                host.addView(choiceRow(
                    title = choice.name,
                    description = choice.description,
                    selected = choice.value == model.currentValue,
                    contentDescription = "模型 ${choice.name}",
                    onClick = {
                        close()
                        onUpdateConfiguration(model.id, AgentConfigValue.Select(choice.value))
                    },
                ))
            }
        }
    }

    private fun resolveSelectedGroup(
        options: List<AgentConfigOption>,
        requestedGroupId: String?,
    ): AgentModelChoiceGroup? {
        val model = options.filterIsInstance<AgentConfigOption.Select>()
            .firstOrNull { it.category == AgentConfigCategory.Model }
        return AgentSurfaceNavigationPolicy.resolveModelChoiceGroup(
            groups = model?.let(::modelGroups).orEmpty(),
            currentModelValue = model?.currentValue,
            requestedGroupId = requestedGroupId,
        )
    }

    private fun modelGroups(model: AgentConfigOption.Select): List<AgentModelChoiceGroup> =
        AgentSurfaceNavigationPolicy.modelChoiceGroups(model).ifEmpty {
            listOf(AgentModelChoiceGroup("__current_agent__", "当前 Agent", model.choices))
        }

    private fun childPanel(
        title: String,
        value: String,
        maxHeight: Int,
        buildRows: (LinearLayout) -> Unit,
    ): View = SessionConfigurationScrollView(context, maxHeight).apply {
        isFillViewport = false
        isVerticalScrollBarEnabled = false
        overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
        elevation = ui.dp(14).toFloat()
        background = ui.roundedBox(
            tokens.surfaceElevated,
            android.graphics.Color.TRANSPARENT,
            ui.dp(22).toFloat(),
        )
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ui.dp(8), ui.dp(8), ui.dp(8), ui.dp(8))
            addView(subpanelHeader(title, value))
            addView(View(context).apply {
                setBackgroundColor(tokens.border)
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(1)).apply {
                setMargins(ui.dp(8), ui.dp(3), ui.dp(8), ui.dp(5))
            })
            buildRows(this)
        }, ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ))
    }

    private fun subpanelHeader(title: String, value: String): View = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = ui.dp(58)
        setPadding(ui.dp(13), ui.dp(5), ui.dp(5), ui.dp(5))
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(context).apply {
                text = title
                textSize = 13f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(tokens.textPrimary)
            })
            addView(TextView(context).apply {
                text = value
                textSize = 12f
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                setTextColor(tokens.textSecondary)
                setPadding(0, ui.dp(2), 0, 0)
            })
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(ImageView(context).apply {
            setImageResource(R.drawable.ic_chevron_right_light)
            rotation = 90f
            imageTintList = ColorStateList.valueOf(tokens.textPrimary)
            setPadding(ui.dp(10), ui.dp(10), ui.dp(10), ui.dp(10))
        }, LinearLayout.LayoutParams(ui.dp(40), ui.dp(40)))
        contentDescription = "收起$title"
        isClickable = true
        isFocusable = true
        setOnClickListener {
            route = Route.Overview
            rebuild(animateContent = true)
        }
    }

    private fun sectionLabel(label: String): View = TextView(context).apply {
        text = label
        textSize = 11.5f
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
        setTextColor(tokens.textTertiary)
        setPadding(ui.dp(13), ui.dp(11), ui.dp(13), ui.dp(4))
    }

    private fun navigationRow(
        title: String,
        value: String,
        enabled: Boolean,
        onClick: () -> Unit,
    ): View = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = ui.dp(60)
        setPadding(ui.dp(14), ui.dp(7), ui.dp(5), ui.dp(7))
        background = ColorDrawable(android.graphics.Color.TRANSPARENT)
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(context).apply {
                text = title
                textSize = 12.5f
                typeface = Typeface.DEFAULT_BOLD
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                setTextColor(tokens.textSecondary)
            })
            addView(TextView(context).apply {
                text = value
                textSize = 14.5f
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                setTextColor(tokens.textPrimary)
                setPadding(0, ui.dp(2), 0, 0)
            })
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(ImageView(context).apply {
            setImageResource(R.drawable.ic_chevron_right_light)
            imageTintList = ColorStateList.valueOf(tokens.textSecondary)
            setPadding(ui.dp(11), ui.dp(11), ui.dp(11), ui.dp(11))
        }, LinearLayout.LayoutParams(ui.dp(42), ui.dp(42)))
        contentDescription = "$title，$value"
        isClickable = enabled && !pendingProvider()
        isFocusable = enabled
        alpha = if (enabled) 1f else 0.52f
        setOnClickListener { if (enabled) onClick() }
    }.also { row ->
        row.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { setMargins(ui.dp(2), ui.dp(2), ui.dp(2), ui.dp(5)) }
    }

    private fun choiceRow(
        title: String,
        description: String?,
        selected: Boolean,
        contentDescription: String,
        onClick: () -> Unit,
    ): View = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = ui.dp(52)
        setPadding(ui.dp(14), ui.dp(7), ui.dp(6), ui.dp(7))
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(context).apply {
                text = title
                textSize = 14.5f
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                setTextColor(tokens.textPrimary)
            })
            description?.takeIf(String::isNotBlank)?.let { detail ->
                addView(TextView(context).apply {
                    text = detail
                    textSize = 11.5f
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                    setTextColor(tokens.textSecondary)
                    setPadding(0, ui.dp(2), 0, 0)
                })
            }
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        if (selected) {
            addView(ImageView(context).apply {
                setImageResource(R.drawable.ic_check_light)
                imageTintList = ColorStateList.valueOf(tokens.textPrimary)
                setPadding(ui.dp(10), ui.dp(10), ui.dp(10), ui.dp(10))
            }, LinearLayout.LayoutParams(ui.dp(40), ui.dp(40)))
        }
        this.contentDescription = contentDescription
        isClickable = !pendingProvider()
        isFocusable = true
        alpha = if (pendingProvider()) 0.55f else 1f
        setOnClickListener { if (!pendingProvider()) onClick() }
    }.also { row ->
        row.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { setMargins(ui.dp(2), ui.dp(1), ui.dp(2), ui.dp(1)) }
    }

    private fun message(text: String): View = TextView(context).apply {
        this.text = text
        textSize = 13f
        setTextColor(tokens.textSecondary)
        setPadding(ui.dp(14), ui.dp(12), ui.dp(14), ui.dp(12))
    }

    private fun AgentConfigOption.valueLabel(): String = when (this) {
        is AgentConfigOption.Select -> choices.firstOrNull { it.value == currentValue }?.name ?: currentValue
        is AgentConfigOption.Toggle -> if (currentValue) "开启" else "关闭"
    }

    private fun AgentConfigOption.settingTitle(): String = when (category) {
        AgentConfigCategory.Model -> "模型"
        AgentConfigCategory.ThoughtLevel -> "推理强度"
        AgentConfigCategory.Mode -> "工作模式"
        AgentConfigCategory.Permission -> "权限"
        AgentConfigCategory.ModelConfiguration -> "模型设置"
        else -> name
    }

    private enum class Route {
        Overview,
        ModelProviders,
        Models,
    }
}

internal data class AgentSessionConfigurationViewport(
    val availableWidth: Int,
    val viewportHeight: Int,
    val composerHeight: Int,
    val topBarHeight: Int,
)

private class SessionConfigurationScrollView(
    context: Context,
    private val maximumHeight: Int,
) : ScrollView(context) {
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val cappedHeight = MeasureSpec.makeMeasureSpec(maximumHeight, MeasureSpec.AT_MOST)
        super.onMeasure(widthMeasureSpec, cappedHeight)
    }
}
