package com.kite.app.feature.resources

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.kite.app.R
import com.kite.app.action.KiteResourceActionIntent
import com.kite.app.theme.KiteTheme

/** 资源详情页面的真实视图所有者；静态内容与动态动作区分开绑定。 */
internal class ResourceDetailScreen(
    context: Context,
    private val resourceId: String,
    initialScrollY: Int,
    private val onBack: () -> Unit,
    private val onMore: (String) -> Unit,
    private val onRawJson: (String) -> Unit,
    private val onOpenDetail: (String) -> Unit,
    private val onPrimaryAction: (String) -> Unit,
    private val onSecondaryAction: (String) -> Unit,
    private val onRetry: () -> Unit
) {
    private val factory = ResourceFeatureViewFactory(
        context = context,
        tokens = ResourceFeatureTheme.tokens(context),
        onOpenDetail = onOpenDetail,
        onPrimaryAction = onPrimaryAction
    )
    private val contentHost = FrameLayout(context)
    private var scrollView: ScrollView? = null
    private var staticSignature = ""
    private var currentItem: ResourceItemUiState? = null
    private var primaryButton: TextView? = null
    private var secondaryButton: TextView? = null
    private var statusValue: TextView? = null
    private var restoredScrollY = initialScrollY.coerceAtLeast(0)

    val root: View = FrameLayout(context).apply {
        contentDescription = context.getString(R.string.resource_detail_description)
        setBackgroundColor(factory.tokens.pageBackground)
        addView(contentHost, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))
    }

    fun render(state: ResourceFeatureUiState) {
        val item = state.item(resourceId)
        when {
            item == null && state.phase in setOf(ResourceCatalogPhase.Idle, ResourceCatalogPhase.Loading) ->
                renderState(root.context.getString(R.string.resource_catalog_loading_title), resourceId, loading = true)
            item == null && state.phase == ResourceCatalogPhase.Failed ->
                renderState(
                    root.context.getString(R.string.resource_detail_request_failed_title),
                    state.errorMessage ?: resourceId,
                    retry = onRetry
                )
            item == null -> renderState(root.context.getString(R.string.resource_detail_unavailable_title), resourceId)
            else -> renderItem(state, item)
        }
    }

    fun acknowledgePrimary(intent: KiteResourceActionIntent) {
        acknowledge(primaryButton, intent)
    }

    fun acknowledgeSecondary(intent: KiteResourceActionIntent) {
        acknowledge(secondaryButton, intent)
    }

    fun scrollY(): Int = scrollView?.scrollY ?: restoredScrollY

    fun dispose() {
        currentItem = null
        primaryButton = null
        secondaryButton = null
        statusValue = null
    }

    private fun renderItem(state: ResourceFeatureUiState, item: ResourceItemUiState) {
        currentItem = item
        val detail = item.detailPresentation(root.context)
        if (staticSignature != detail.staticSignature || scrollView == null) {
            staticSignature = detail.staticSignature
            rebuildContent(state, item, detail)
        } else {
            bindDynamic(item)
        }
    }

    private fun rebuildContent(
        state: ResourceFeatureUiState,
        item: ResourceItemUiState,
        detail: ResourceDetailPresentation
    ) {
        primaryButton = null
        secondaryButton = null
        statusValue = null
        contentHost.removeAllViews()
        val scroll = ScrollView(root.context).apply {
            isFillViewport = true
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(factory.dp(22), factory.dp(8), factory.dp(22), factory.dp(34))
                addView(header(item, detail))
                addView(actionArea())
                addView(visual(item, detail))
                addView(infoBlock(context.getString(R.string.resource_detail_section_intro), detail.longDescription))
                recommendationBlock(state, detail)?.let(::addView)
                addView(sourceBlock(detail))
                if (detail.includes.isNotEmpty()) {
                    addView(bulletBlock(context.getString(R.string.resource_detail_section_includes), detail.includes))
                }
                if (detail.notes.isNotEmpty()) {
                    addView(bulletBlock(context.getString(R.string.resource_detail_section_notes), detail.notes))
                }
                addView(stepBlock(detail.steps))
                addView(requirementsBlock(detail))
            })
        }
        scrollView = scroll
        contentHost.addView(LinearLayout(root.context).apply {
            orientation = LinearLayout.VERTICAL
            addView(chrome(item.resourceId))
            addView(scroll, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            ))
        }, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))
        bindDynamic(item)
        if (restoredScrollY > 0) {
            val target = restoredScrollY
            restoredScrollY = 0
            scroll.post { scroll.scrollTo(0, target) }
        }
    }

    private fun renderState(
        title: String,
        detail: String,
        loading: Boolean = false,
        retry: (() -> Unit)? = null
    ) {
        if (currentItem == null && contentHost.childCount > 0 && staticSignature == "state:$title:$detail") return
        currentItem = null
        staticSignature = "state:$title:$detail"
        primaryButton = null
        secondaryButton = null
        statusValue = null
        scrollView = null
        contentHost.removeAllViews()
        contentHost.addView(LinearLayout(root.context).apply {
            orientation = LinearLayout.VERTICAL
            addView(chrome(resourceId, showMore = false))
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(factory.dp(22), 0, factory.dp(22), factory.dp(34))
                addView(factory.stateBlock(title, detail, loading, retry))
            }, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            ))
        }, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))
    }

    private fun bindDynamic(item: ResourceItemUiState) {
        val presentation = item.presentation(root.context)
        statusValue?.text = factory.stateLabel(item)
        val split = item.secondaryIntent != null
        primaryButton?.apply {
            layoutParams = LinearLayout.LayoutParams(0, factory.dp(46), if (split) 0.7f else 1f)
            text = factory.actionLabel(item.primaryIntent)
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            includeFontPadding = false
            setTextColor(factory.tokens.primaryStrong)
            alpha = if (presentation.actionEnabled) 1f else 0.58f
            isEnabled = presentation.actionEnabled
            background = factory.roundedBox(
                factory.tokens.primarySubtle,
                Color.TRANSPARENT,
                factory.dp(15).toFloat()
            )
            setOnClickListener(null)
            if (presentation.actionEnabled) setOnClickListener { onPrimaryAction(item.resourceId) }
        }
        secondaryButton?.apply {
            visibility = if (split) View.VISIBLE else View.GONE
            layoutParams = LinearLayout.LayoutParams(0, factory.dp(46), 0.3f).apply {
                setMargins(factory.dp(10), 0, 0, 0)
            }
            setOnClickListener(null)
            if (split) {
                text = item.secondaryIntent?.let(factory::actionLabel).orEmpty()
                textSize = 13f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                includeFontPadding = false
                setTextColor(factory.tokens.danger)
                alpha = if (presentation.actionEnabled) 1f else 0.58f
                isEnabled = presentation.actionEnabled
                background = factory.roundedBox(
                    factory.tokens.dangerSoft,
                    factory.tokens.dangerBorder,
                    factory.dp(15).toFloat()
                )
                if (presentation.actionEnabled) setOnClickListener { onSecondaryAction(item.resourceId) }
            }
        }
    }

    private fun acknowledge(button: TextView?, intent: KiteResourceActionIntent) {
        button?.apply {
            text = factory.acknowledgementLabel(intent)
            isEnabled = false
            alpha = 0.58f
            setOnClickListener(null)
        }
    }

    private fun chrome(resourceId: String, showMore: Boolean = true): View =
        factory.ui.topBar(
            context = root.context,
            title = root.context.getString(R.string.resource_catalog_title),
            onBack = onBack,
            trailingAction = if (showMore) {
                factory.ui.imageButton(
                    context = root.context,
                    iconRes = R.drawable.ic_more_vert_light,
                    contentDescription = root.context.getString(R.string.resource_detail_more_actions),
                    onClick = { onMore(resourceId) }
                )
            } else {
                null
            }
        )

    private fun header(item: ResourceItemUiState, detail: ResourceDetailPresentation): View {
        val presentation = detail.item
        return LinearLayout(root.context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, factory.dp(4), 0, 0)
            addView(factory.icon(item, factory.dp(78), factory.dp(9), factory.dp(18).toFloat(), 18f).apply {
                elevation = factory.dp(3).toFloat()
                layoutParams = LinearLayout.LayoutParams(factory.dp(78), factory.dp(78))
            })
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    setMargins(factory.dp(16), 0, 0, 0)
                }
                addView(TextView(context).apply {
                    text = presentation.name
                    textSize = 24f
                    typeface = Typeface.DEFAULT_BOLD
                    includeFontPadding = false
                    setTextColor(factory.tokens.textPrimary)
                    maxLines = 2
                    ellipsize = TextUtils.TruncateAt.END
                })
                addView(TextView(context).apply {
                    text = presentation.description
                    textSize = 14f
                    setTextColor(factory.tokens.textSecondary)
                    setPadding(0, factory.dp(6), 0, 0)
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                })
                addView(badge(detail.badge))
                addView(TextView(context).apply {
                    text = listOf(presentation.version, presentation.sizeLabel, presentation.category)
                        .filter(String::isNotBlank).joinToString(" · ")
                    textSize = 12.5f
                    setTextColor(factory.tokens.textTertiary)
                    setPadding(0, factory.dp(7), 0, 0)
                })
            })
        }
    }

    private fun badge(badge: ResourceDetailBadge): View = LinearLayout(root.context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(0, factory.dp(7), 0, 0)
        val tone = KiteTheme.accent(badge.accent, factory.tokens)
        addView(TextView(context).apply {
            text = badge.iconText
            textSize = 11f
            gravity = Gravity.CENTER
            includeFontPadding = false
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(factory.tokens.buttonText)
            background = factory.roundedBox(tone.strong, tone.strong, factory.dp(8).toFloat())
        }, LinearLayout.LayoutParams(factory.dp(16), factory.dp(16)))
        addView(TextView(context).apply {
            text = badge.label
            textSize = 12.5f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(tone.strong)
        }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(factory.dp(7), 0, 0, 0) })
    }

    private fun actionArea(): View = LinearLayout(root.context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            factory.dp(46)
        ).apply { setMargins(0, factory.dp(24), 0, 0) }
        val primary = TextView(context)
        val secondary = TextView(context)
        primaryButton = primary
        secondaryButton = secondary
        addView(primary)
        addView(secondary)
    }

    private fun visual(item: ResourceItemUiState, detail: ResourceDetailPresentation): View =
        if (detail.mediaAsset.isNotBlank()) {
            factory.mediaBanner(item, detail.mediaAsset, detail.mediaDescription)
        } else {
            previewStrip(detail.previews)
        }

    private fun previewStrip(previews: List<ResourceDetailPreview>): View =
        HorizontalScrollView(root.context).apply {
            isHorizontalScrollBarEnabled = false
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, factory.dp(20), 0, 0) }
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                previews.forEachIndexed { index, preview ->
                    addView(previewCard(preview), LinearLayout.LayoutParams(
                        factory.dp(176),
                        factory.dp(136)
                    ).apply {
                        if (index != previews.lastIndex) setMargins(0, 0, factory.dp(12), 0)
                    })
                }
            })
        }

    private fun previewCard(preview: ResourceDetailPreview): View =
        LinearLayout(root.context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(factory.dp(13), factory.dp(12), factory.dp(13), factory.dp(10))
            val tone = KiteTheme.accent(preview.accent, factory.tokens)
            background = factory.roundedBox(factory.tokens.cardBackground, tone.border, factory.dp(16).toFloat())
            addView(TextView(context).apply {
                text = preview.title
                textSize = 13.5f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(tone.strong)
            })
            addView(TextView(context).apply {
                text = preview.subtitle
                textSize = 11f
                setTextColor(factory.tokens.textSecondary)
                setPadding(0, factory.dp(4), 0, 0)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            })
            addView(FrameLayout(context).apply {
                background = factory.roundedBox(factory.tokens.surface, factory.tokens.border, factory.dp(13).toFloat())
                addView(factory.icon(
                    preview.symbol,
                    preview.accent,
                    preview.iconAsset,
                    preview.iconFit,
                    factory.dp(42),
                    factory.dp(6),
                    factory.dp(12).toFloat(),
                    15f
                ), FrameLayout.LayoutParams(factory.dp(42), factory.dp(42), Gravity.CENTER))
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).apply {
                setMargins(0, factory.dp(10), 0, 0)
            })
        }

    private fun infoBlock(title: String, body: String): View = LinearLayout(root.context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(0, factory.dp(24), 0, 0)
        addView(sectionTitle(title))
        addView(TextView(context).apply {
            text = body
            textSize = 14.5f
            setTextColor(factory.tokens.textSecondary)
            setPadding(0, factory.dp(13), 0, 0)
            setLineSpacing(factory.dp(5).toFloat(), 1f)
        })
    }

    private fun bulletBlock(title: String, items: List<String>): View =
        infoBlock(title, items.joinToString("\n") { "· $it" })

    private fun recommendationBlock(
        state: ResourceFeatureUiState,
        detail: ResourceDetailPresentation
    ): View? {
        val recommendations = detail.recommendations.mapNotNull { recommendation ->
            state.item(recommendation.resourceId)
                ?.takeIf { it.resourceId != resourceId }
                ?.let { recommendation to it }
        }
        if (recommendations.isEmpty()) return null
        return LinearLayout(root.context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, factory.dp(20), 0, 0)
            addView(sectionTitle(context.getString(R.string.resource_detail_section_recommendations)))
            addView(HorizontalScrollView(context).apply {
                isHorizontalScrollBarEnabled = false
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, factory.dp(10), 0, 0) }
                addView(LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    recommendations.forEachIndexed { index, (recommendation, target) ->
                        addView(recommendationCard(recommendation, target), LinearLayout.LayoutParams(
                            factory.dp(72),
                            factory.dp(82)
                        ).apply {
                            if (index != recommendations.lastIndex) setMargins(0, 0, factory.dp(12), 0)
                        })
                    }
                })
            })
        }
    }

    private fun recommendationCard(
        recommendation: ResourceDetailRecommendation,
        item: ResourceItemUiState
    ): View = LinearLayout(root.context).apply {
        val presentation = item.presentation(root.context)
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        contentDescription = "${presentation.name}，${recommendation.label}"
        isClickable = true
        setOnClickListener { onOpenDetail(item.resourceId) }
        addView(factory.icon(item, factory.dp(56), factory.dp(7), factory.dp(14).toFloat(), 15f))
        addView(TextView(context).apply {
            text = presentation.name
            textSize = 10.5f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(factory.tokens.textSecondary)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setPadding(0, factory.dp(5), 0, 0)
        })
    }

    private fun sourceBlock(detail: ResourceDetailPresentation): View = LinearLayout(root.context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(0, factory.dp(24), 0, 0)
        addView(sectionTitle(context.getString(R.string.resource_detail_section_source)))
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(factory.dp(14), factory.dp(12), factory.dp(14), factory.dp(14))
            background = factory.roundedBox(factory.tokens.cardBackground, factory.tokens.border, factory.dp(17).toFloat())
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, factory.dp(14), 0, 0) }
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                val tone = KiteTheme.accent(detail.item.accent, factory.tokens)
                addView(TextView(context).apply {
                    text = context.getString(R.string.resource_detail_source_mark)
                    textSize = 12f
                    typeface = Typeface.DEFAULT_BOLD
                    gravity = Gravity.CENTER
                    setTextColor(tone.strong)
                    background = factory.roundedBox(tone.soft, Color.TRANSPARENT, factory.dp(10).toFloat())
                }, LinearLayout.LayoutParams(factory.dp(34), factory.dp(34)))
                addView(LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(TextView(context).apply {
                        text = detail.sourceTitle
                        textSize = 14f
                        typeface = Typeface.DEFAULT_BOLD
                        setTextColor(factory.tokens.textPrimary)
                    })
                    addView(TextView(context).apply {
                        text = detail.sourceSubtitle
                        textSize = 11.5f
                        setTextColor(factory.tokens.textTertiary)
                        setPadding(0, factory.dp(4), 0, 0)
                    })
                }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    setMargins(factory.dp(12), 0, 0, 0)
                })
            })
            addView(factory.divider(), LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                factory.dp(1)
            ).apply { setMargins(0, factory.dp(12), 0, factory.dp(12)) })
            addView(TextView(context).apply {
                text = context.getString(R.string.resource_detail_raw_json_action)
                textSize = 14f
                setTextColor(factory.tokens.textPrimary)
                setPadding(0, factory.dp(8), 0, factory.dp(8))
                contentDescription = context.getString(R.string.resource_detail_raw_json_description)
                setOnClickListener { onRawJson(resourceId) }
            })
        })
    }

    private fun stepBlock(steps: List<ResourceDetailStep>): View = LinearLayout(root.context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(0, factory.dp(24), 0, 0)
        addView(sectionTitle(context.getString(R.string.resource_detail_section_execution)))
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(factory.dp(14), factory.dp(10), factory.dp(14), factory.dp(10))
            background = factory.roundedBox(factory.tokens.cardBackground, factory.tokens.border, factory.dp(17).toFloat())
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, factory.dp(14), 0, 0) }
            steps.forEachIndexed { index, step ->
                addView(TextView(context).apply {
                    text = step.title
                    textSize = 13.5f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(factory.tokens.textPrimary)
                })
                addView(TextView(context).apply {
                    text = step.preview
                    textSize = 11.5f
                    typeface = Typeface.MONOSPACE
                    setTextColor(factory.tokens.textSecondary)
                    maxLines = 3
                    ellipsize = TextUtils.TruncateAt.END
                    setPadding(0, factory.dp(5), 0, 0)
                })
                if (index != steps.lastIndex) addView(factory.divider(), LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    factory.dp(1)
                ).apply { setMargins(0, factory.dp(10), 0, factory.dp(10)) })
            }
        })
    }

    private fun requirementsBlock(detail: ResourceDetailPresentation): View =
        LinearLayout(root.context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, factory.dp(26), 0, 0)
            addView(sectionTitle(context.getString(R.string.resource_detail_section_requirements)))
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(factory.dp(16), factory.dp(8), factory.dp(16), factory.dp(8))
                background = factory.roundedBox(factory.tokens.cardBackground, factory.tokens.border, factory.dp(17).toFloat())
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, factory.dp(14), 0, 0) }
                val statusLabel = context.getString(R.string.resource_detail_status)
                val rows = detail.requirements + ResourceDetailRequirement(
                    statusLabel,
                    currentItem?.let(factory::stateLabel).orEmpty()
                )
                rows.forEachIndexed { index, requirement ->
                    addView(requirementRow(requirement, dynamicStatus = requirement.label == statusLabel))
                    if (index != rows.lastIndex) addView(factory.divider(), LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        factory.dp(1)
                    ))
                }
            })
        }

    private fun requirementRow(requirement: ResourceDetailRequirement, dynamicStatus: Boolean): View =
        LinearLayout(root.context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, factory.dp(9), 0, factory.dp(9))
            addView(TextView(context).apply {
                text = requirement.label
                textSize = 13.5f
                setTextColor(factory.tokens.textSecondary)
            }, LinearLayout.LayoutParams(factory.dp(96), ViewGroup.LayoutParams.WRAP_CONTENT))
            val value = TextView(context).apply {
                text = requirement.value
                textSize = 13.5f
                setTextColor(factory.tokens.textPrimary)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            }
            if (dynamicStatus) statusValue = value
            addView(value, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }

    private fun sectionTitle(title: String): TextView = TextView(root.context).apply {
        text = title
        textSize = 20f
        typeface = Typeface.DEFAULT_BOLD
        includeFontPadding = false
        setTextColor(factory.tokens.textPrimary)
    }
}
