package com.kite.app.feature.runsurface

import android.app.Dialog
import android.content.Context
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.res.Configuration
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.InsetDrawable
import android.net.Uri
import android.provider.OpenableColumns
import android.text.Editable
import android.text.InputType
import android.text.method.LinkMovementMethod
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextUtils
import android.text.TextWatcher
import android.text.style.BackgroundColorSpan
import android.text.style.ClickableSpan
import android.text.style.StyleSpan
import android.text.style.StrikethroughSpan
import android.text.style.TypefaceSpan
import android.text.TextPaint
import android.util.Base64
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.kite.app.R
import com.kite.app.agent.contract.AgentContent
import com.kite.app.agent.contract.AgentCommand
import com.kite.app.agent.contract.AgentConfigCategory
import com.kite.app.agent.contract.AgentConfigChoice
import com.kite.app.agent.contract.AgentConfigOption
import com.kite.app.agent.contract.AgentConfigValue
import com.kite.app.agent.contract.AgentOperationResult
import com.kite.app.agent.contract.AgentPermissionOutcome
import com.kite.app.agent.contract.AgentSessionPhase
import com.kite.app.agent.contract.AgentSessionSummary
import com.kite.app.agent.config.AgentConfigApplyResult
import com.kite.app.agent.config.AgentConfigReadResult
import com.kite.app.agent.config.AgentConfigScope
import com.kite.app.agent.config.AgentCoreDocumentDescriptor
import com.kite.app.agent.config.AgentCoreDocumentListResult
import com.kite.app.agent.config.AgentCoreDocumentReadResult
import com.kite.app.agent.config.AgentCoreDocumentSemantics
import com.kite.app.agent.config.AgentCoreDocumentSnapshot
import com.kite.app.agent.config.AgentCoreDocumentWriteRequest
import com.kite.app.agent.config.AgentCoreDocumentWriteResult
import com.kite.app.agent.config.AgentCredentialPresence
import com.kite.app.agent.config.AgentLiveConfigSnapshot
import com.kite.app.agent.config.AgentMcpConnectionCheckResult
import com.kite.app.agent.config.AgentMcpConnectionState
import com.kite.app.agent.config.AgentMcpDraft
import com.kite.app.agent.config.AgentMcpOperation
import com.kite.app.agent.config.AgentMcpSummary
import com.kite.app.agent.config.AgentMcpTransport
import com.kite.app.agent.config.AgentPersistentConfigCapability
import com.kite.app.agent.config.AgentPermissionProfileSummary
import com.kite.app.agent.config.AgentProviderCredentialChange
import com.kite.app.agent.config.AgentProviderDraft
import com.kite.app.agent.config.AgentProviderModelSummary
import com.kite.app.agent.config.AgentProviderPreset
import com.kite.app.agent.config.AgentProviderSummary
import com.kite.app.agent.config.AgentSkillActivation
import com.kite.app.agent.config.AgentSkillImportStager
import com.kite.app.agent.config.AgentSkillOperation
import com.kite.app.agent.config.AgentSkillSummary
import com.kite.app.agent.registration.AgentConfigurationStatus
import com.kite.app.agent.registration.AgentInstallationStatus
import com.kite.app.agent.registration.AgentLaunchStatus
import com.kite.app.agent.registration.AgentRegistryEntry
import com.kite.app.agent.registration.AgentRegistrySnapshot
import com.kite.app.agent.registration.AgentRuntimeStatus
import com.kite.app.agent.registration.KiteAgentRegistry
import com.kite.app.agent.auth.AgentOfficialAccountManager
import com.kite.app.agent.auth.AgentOfficialAccountStatus
import com.kite.app.agent.runtime.AgentDraftCapabilityCatalog
import com.kite.app.agent.runtime.AgentDraftModelSelection
import com.kite.app.agent.runtime.AgentRuntimeRegistry
import com.kite.app.agent.runtime.AgentRuntimeSession
import com.kite.app.agent.store.AgentConversationItem
import com.kite.app.agent.store.AgentConversationHistoryStatus
import com.kite.app.agent.store.AgentConversationKey
import com.kite.app.agent.store.AgentConversationSnapshot
import com.kite.app.agent.store.AgentConversationStore
import com.kite.app.agent.store.AgentConversationTurn
import com.kite.app.agent.store.AgentDraftCapabilityCacheStore
import com.kite.app.agent.store.AgentModelLibraryStore
import com.kite.app.agent.store.AgentProject
import com.kite.app.agent.store.AgentProjectSaveResult
import com.kite.app.agent.store.AgentProjectStore
import com.kite.app.agent.store.AgentSessionMetadataStore
import com.kite.app.theme.ThemeTokens
import com.kite.app.ui.UiActionRole
import com.kite.app.ui.UiKit
import com.kite.app.ui.UiMenuItem
import com.kite.app.foundation.runtime.KFContainerManager
import com.kite.app.foundation.workspace.KiteStorageContract
import com.kite.app.platform.storage.AndroidDocumentPathResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

internal class ConversationAdapter(
    private val context: Context,
    private val tokens: ThemeTokens,
    private val scope: CoroutineScope
) : ListAdapter<AgentConversationDisplayItem, ConversationAdapter.DisplayHolder>(DIFF) {
    private val ui = UiKit(context, tokens)
    private val skillChipPalette = AgentSkillChipVisualPolicy.palette(
        context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES
    )
    private val mediaRepository = AgentConversationMediaRepository(context)
    private val projectionCache = linkedMapOf<String, Pair<AgentConversationItem, List<AgentConversationDisplayItem>>>()
    private val expandedThoughtIds = mutableSetOf<String>()
    private val expandedToolIds = mutableSetOf<String>()
    private val toolGroupExpansionOverrides = mutableMapOf<String, Boolean>()
    private val assistantBodyTypeface = Typeface.create(Typeface.DEFAULT, BODY_TEXT_WEIGHT, false)

    fun submitConversation(
        items: List<AgentConversationItem>,
        turns: List<AgentConversationTurn>,
        committed: () -> Unit,
    ) {
        val activeIds = items.mapTo(linkedSetOf()) { it.id }
        projectionCache.keys.retainAll(activeIds)
        val projectedById = items.associate { item ->
            item.id to (projectionCache[item.id]
                ?.takeIf { (source, _) -> source == item }
                ?.second
                ?: AgentConversationPresentation.project(listOf(item)).also { blocks ->
                    projectionCache[item.id] = item to blocks
                })
        }
        val projected = AgentConversationPresentation.composeTurns(items, turns) { item ->
            projectedById[item.id].orEmpty()
        }
        submitList(projected, committed)
    }

    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        is AgentConversationDisplayItem.UserMessage -> TYPE_USER
        is AgentConversationDisplayItem.AssistantText -> TYPE_ASSISTANT
        is AgentConversationDisplayItem.Code -> TYPE_CODE
        is AgentConversationDisplayItem.Rule -> TYPE_RULE
        is AgentConversationDisplayItem.Table -> TYPE_TABLE
        is AgentConversationDisplayItem.AnswerCopy -> TYPE_ANSWER_COPY
        is AgentConversationDisplayItem.Process -> TYPE_PROCESS
        is AgentConversationDisplayItem.Thought -> TYPE_THOUGHT
        is AgentConversationDisplayItem.Tool -> TYPE_TOOL
        is AgentConversationDisplayItem.Plan -> TYPE_PLAN
        is AgentConversationDisplayItem.Image -> TYPE_IMAGE
        is AgentConversationDisplayItem.Attachment -> TYPE_ATTACHMENT
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DisplayHolder = when (viewType) {
        TYPE_USER -> UserHolder()
        TYPE_ASSISTANT -> AssistantTextHolder()
        TYPE_CODE -> CodeHolder()
        TYPE_RULE -> RuleHolder()
        TYPE_TABLE -> TableHolder()
        TYPE_ANSWER_COPY -> AnswerCopyHolder()
        TYPE_PROCESS -> ProcessHolder()
        TYPE_THOUGHT -> ThoughtHolder()
        TYPE_TOOL -> ToolHolder()
        TYPE_PLAN -> PlanHolder()
        TYPE_IMAGE -> ImageHolder()
        else -> AttachmentHolder()
    }

    override fun onBindViewHolder(holder: DisplayHolder, position: Int) {
        holder.bind(getItem(position))
    }

    override fun onViewRecycled(holder: DisplayHolder) {
        holder.recycle()
        super.onViewRecycled(holder)
    }

    abstract inner class DisplayHolder(view: View) : RecyclerView.ViewHolder(view) {
        abstract fun bind(item: AgentConversationDisplayItem)
        open fun recycle() = Unit
    }

    inner class UserHolder : DisplayHolder(
        FrameLayout(context).apply { layoutParams = rowParams(top = 8, bottom = 12) }
    ) {
        private val frame = itemView as FrameLayout
        private val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.END
        }.also {
            frame.addView(it, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.END,
            ))
        }
        private val skillHost = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.END
        }.also(content::addView)
        private val text = messageTextView().apply {
            textSize = 15f
            setLineSpacing(0f, 1.3f)
            letterSpacing = CONVERSATION_LETTER_SPACING
            maxWidth = context.resources.displayMetrics.widthPixels - ui.dp(72)
            setPadding(ui.dp(15), ui.dp(10), ui.dp(15), ui.dp(10))
            background = ui.roundedBox(tokens.surfaceElevated, tokens.border, ui.dp(20).toFloat(), ui.dp(1))
        }.also(content::addView)

        override fun bind(item: AgentConversationDisplayItem) {
            item as AgentConversationDisplayItem.UserMessage
            skillHost.removeAllViews()
            item.skills.forEach { skillName ->
                skillHost.addView(TextView(context).apply {
                    text = skillName
                    textSize = 12.5f
                    typeface = Typeface.DEFAULT_BOLD
                    includeFontPadding = false
                    gravity = Gravity.CENTER
                    maxLines = 1
                    maxWidth = ui.dp(AgentSkillChipVisualPolicy.MAX_WIDTH_DP)
                    ellipsize = TextUtils.TruncateAt.END
                    setTextColor(skillChipPalette.text)
                    setPadding(ui.dp(12), 0, ui.dp(12), 0)
                    background = ui.roundedBox(
                        skillChipPalette.fill,
                        skillChipPalette.border,
                        ui.dp(AgentSkillChipVisualPolicy.HEIGHT_DP / 2).toFloat(),
                        ui.dp(1),
                    )
                }, LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ui.dp(AgentSkillChipVisualPolicy.HEIGHT_DP),
                ).apply { bottomMargin = ui.dp(5) })
            }
            text.text = item.text
            text.visibility = if (item.text.isBlank()) View.GONE else View.VISIBLE
            text.layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        }
    }

    inner class AssistantTextHolder : DisplayHolder(
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = rowParams(top = 2, bottom = 4)
        }
    ) {
        private val container = itemView as LinearLayout
        private val label = sectionLabel().also(container::addView)
        private val textRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
        }.also {
            container.addView(
                it,
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
            )
        }
        private val quoteBar = View(context).apply {
            setBackgroundColor(tokens.borderStrong)
            visibility = View.GONE
        }.also { textRow.addView(it, LinearLayout.LayoutParams(ui.dp(3), ViewGroup.LayoutParams.MATCH_PARENT).apply {
            marginEnd = ui.dp(12)
        }) }
        private val text = messageTextView().also {
            textRow.addView(it, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }

        override fun bind(item: AgentConversationDisplayItem) {
            item as AgentConversationDisplayItem.AssistantText
            label.visibility = View.GONE
            text.text = styledInlineText(item.inline)
            text.movementMethod = if (item.inline.any { AgentInlineTextSegment.Style.Link in it.styles }) {
                LinkMovementMethod.getInstance()
            } else {
                null
            }
            text.linksClickable = text.movementMethod != null
            text.highlightColor = android.graphics.Color.TRANSPARENT
            text.setLinkTextColor(tokens.primaryStrong)
            text.setTextColor(tokens.textPrimary)
            text.background = null
            text.setPadding(0, 0, 0, 0)
            quoteBar.visibility = View.GONE
            when (item.style) {
                AgentTextBlockStyle.Heading1 -> {
                    container.layoutParams = rowParams(top = 18, bottom = 7)
                    setTextStyle(21f, Typeface.DEFAULT_BOLD, 1.18f)
                }
                AgentTextBlockStyle.Heading2 -> {
                    container.layoutParams = rowParams(top = 16, bottom = 6)
                    setTextStyle(18.5f, Typeface.DEFAULT_BOLD, 1.2f)
                }
                AgentTextBlockStyle.Heading3 -> {
                    container.layoutParams = rowParams(top = 14, bottom = 5)
                    setTextStyle(16.5f, Typeface.DEFAULT_BOLD, 1.22f)
                }
                AgentTextBlockStyle.Heading4 -> {
                    container.layoutParams = rowParams(top = 12, bottom = 5)
                    setTextStyle(15.5f, Typeface.DEFAULT_BOLD, 1.24f)
                }
                AgentTextBlockStyle.Heading5 -> {
                    container.layoutParams = rowParams(top = 10, bottom = 4)
                    setTextStyle(14.5f, Typeface.DEFAULT_BOLD, 1.26f)
                }
                AgentTextBlockStyle.Heading6 -> {
                    container.layoutParams = rowParams(top = 9, bottom = 4)
                    setTextStyle(13.5f, Typeface.DEFAULT_BOLD, 1.28f)
                    text.setTextColor(tokens.textSecondary)
                }
                AgentTextBlockStyle.Quote -> {
                    container.layoutParams = rowParams(top = 8, bottom = 13)
                    setTextStyle(14.5f, assistantBodyTypeface, 1.36f, BODY_LETTER_SPACING)
                    text.setTextColor(tokens.textSecondary)
                    quoteBar.visibility = View.VISIBLE
                    text.setPadding(0, ui.dp(2), 0, ui.dp(2))
                }
                AgentTextBlockStyle.Bullet,
                AgentTextBlockStyle.Ordered -> {
                    container.layoutParams = rowParams(top = 2, bottom = 5)
                    setTextStyle(14.5f, assistantBodyTypeface, 1.36f, BODY_LETTER_SPACING)
                    text.setPadding(ui.dp(2 + item.listDepth * 16), 0, 0, 0)
                }
                AgentTextBlockStyle.Paragraph -> {
                    container.layoutParams = rowParams(top = 3, bottom = 13)
                    setTextStyle(14.5f, assistantBodyTypeface, 1.38f, BODY_LETTER_SPACING)
                }
            }
        }

        private fun setTextStyle(
            size: Float,
            typeface: Typeface,
            spacingMultiplier: Float,
            tracking: Float = 0f,
        ) {
            text.textSize = size
            text.typeface = typeface
            text.setLineSpacing(0f, spacingMultiplier)
            text.letterSpacing = tracking
        }
    }

    inner class CodeHolder : DisplayHolder(
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = rowParams(top = 7, bottom = 9)
            background = ui.roundedBox(tokens.surface, tokens.border, ui.dp(14).toFloat(), ui.dp(1))
        }
    ) {
        private val container = itemView as LinearLayout
        private val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(ui.dp(13), ui.dp(7), ui.dp(8), ui.dp(6))
        }.also(container::addView)
        private val language = TextView(context).apply {
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(tokens.textSecondary)
        }.also { header.addView(it, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)) }
        private val copy = copyIconButton("复制代码").also {
            header.addView(it, copyButtonLayoutParams())
        }
        private val code = messageTextView().apply {
            textSize = 14f
            typeface = Typeface.MONOSPACE
            setHorizontallyScrolling(true)
            setPadding(ui.dp(13), ui.dp(10), ui.dp(13), ui.dp(12))
            background = ui.roundedBox(tokens.surfaceElevated, android.graphics.Color.TRANSPARENT, ui.dp(12).toFloat())
        }.also { container.addView(it, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            setMargins(ui.dp(7), 0, ui.dp(7), ui.dp(7))
        }) }

        override fun bind(item: AgentConversationDisplayItem) {
            item as AgentConversationDisplayItem.Code
            language.text = buildString {
                append(item.language ?: "代码")
            }
            code.text = item.code
            copy.setOnClickListener { copyText("Agent 代码", item.code) }
        }
    }

    inner class RuleHolder : DisplayHolder(
        View(context).apply {
            layoutParams = rowParams(top = 11, bottom = 11).apply { height = ui.dp(1) }
            setBackgroundColor(tokens.border)
        }
    ) {
        override fun bind(item: AgentConversationDisplayItem) = Unit
    }

    inner class TableHolder : DisplayHolder(
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = rowParams(top = 8, bottom = 10)
            background = ui.roundedBox(tokens.surface, tokens.border, ui.dp(15).toFloat(), ui.dp(1))
        }
    ) {
        private val container = itemView as LinearLayout
        private val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(ui.dp(13), ui.dp(7), ui.dp(8), ui.dp(6))
        }.also(container::addView)
        private val label = TextView(context).apply {
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(tokens.textSecondary)
        }.also { header.addView(it, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)) }
        private val copy = copyIconButton("复制表格").also {
            header.addView(it, copyButtonLayoutParams())
        }
        private val table = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        init {
            container.addView(HorizontalScrollView(context).apply {
                isHorizontalScrollBarEnabled = false
                overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
                setPadding(ui.dp(7), 0, ui.dp(7), ui.dp(7))
                addView(table, ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ))
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }

        override fun bind(item: AgentConversationDisplayItem) {
            item as AgentConversationDisplayItem.Table
            label.text = "表格"
            copy.setOnClickListener { copyText("Agent 表格", item.copyText) }
            table.removeAllViews()
            table.addView(tableRow(item.headers, header = true))
            item.rows.forEach { row -> table.addView(tableRow(row, header = false)) }
        }

        private fun tableRow(values: List<String>, header: Boolean): View = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            background = ui.roundedBox(
                if (header) tokens.surfaceElevated else android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT,
                if (header) ui.dp(9).toFloat() else 0f,
            )
            values.forEach { value ->
                addView(TextView(context).apply {
                    text = value
                    textSize = if (header) 12.5f else 13f
                    typeface = if (header) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                    maxLines = 4
                    ellipsize = TextUtils.TruncateAt.END
                    setTextColor(if (header) tokens.textPrimary else tokens.textSecondary)
                    setPadding(ui.dp(11), ui.dp(9), ui.dp(11), ui.dp(9))
                }, LinearLayout.LayoutParams(ui.dp(132), ViewGroup.LayoutParams.WRAP_CONTENT))
            }
        }
    }

    inner class ProcessHolder : DisplayHolder(
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = rowParams(top = 5, bottom = 7)
        }
    ) {
        private val container = itemView as LinearLayout
        private val entries = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }.also(container::addView)

        override fun bind(item: AgentConversationDisplayItem) {
            item as AgentConversationDisplayItem.Process
            rebuildEntries(item)
        }

        private fun rebuildEntries(item: AgentConversationDisplayItem.Process) {
            entries.removeAllViews()
            var index = 0
            while (index < item.entries.size) {
                when (val entry = item.entries[index]) {
                    is AgentConversationDisplayItem.Thought -> {
                        entries.addView(thoughtRow(entry))
                        index += 1
                    }
                    is AgentConversationDisplayItem.Tool -> {
                        val tools = item.entries
                            .drop(index)
                            .takeWhile { it is AgentConversationDisplayItem.Tool }
                            .filterIsInstance<AgentConversationDisplayItem.Tool>()
                        entries.addView(toolGroup(item, tools))
                        index += tools.size
                    }
                    is AgentConversationDisplayItem.Plan -> {
                        entries.addView(planRows(entry))
                        index += 1
                    }
                    else -> index += 1
                }
            }
        }

        private fun thoughtRow(item: AgentConversationDisplayItem.Thought): View =
            AgentThoughtRowView(context, tokens).apply {
                val expanded = item.id in expandedThoughtIds
                bind(item.text, expanded) {
                    if (expanded) expandedThoughtIds.remove(item.id) else expandedThoughtIds.add(item.id)
                    bindingAdapterPosition.takeIf { it != RecyclerView.NO_POSITION }?.let(::notifyItemChanged)
                }
            }

        private fun toolRow(item: AgentConversationDisplayItem.Tool): View = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val expanded = item.id in expandedToolIds
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, ui.dp(4), 0, ui.dp(4))
                addView(ImageView(context).apply {
                    setImageResource(R.drawable.ic_terminal_prompt_light)
                    setColorFilter(if (item.status == "失败") tokens.danger else tokens.textTertiary)
                    scaleType = ImageView.ScaleType.CENTER_INSIDE
                }, LinearLayout.LayoutParams(ui.dp(18), ui.dp(18)).apply { marginEnd = ui.dp(8) })
                addView(TextView(context).apply {
                    text = buildString {
                        append(item.title)
                        if (item.status == "失败") append(" · 失败")
                    }
                    textSize = 13.5f
                    includeFontPadding = false
                    maxLines = 2
                    ellipsize = TextUtils.TruncateAt.END
                    setTextColor(if (item.status == "失败") tokens.danger else tokens.textSecondary)
                }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                if (!item.detail.isNullOrBlank()) {
                    addView(ImageView(context).apply {
                        setImageResource(R.drawable.ic_chevron_right_light)
                        setColorFilter(tokens.textTertiary)
                        rotation = if (expanded) 90f else 0f
                        scaleType = ImageView.ScaleType.CENTER_INSIDE
                    }, LinearLayout.LayoutParams(ui.dp(18), ui.dp(18)))
                }
                isClickable = !item.detail.isNullOrBlank()
                isFocusable = isClickable
                setOnClickListener {
                    if (item.detail.isNullOrBlank()) return@setOnClickListener
                    if (expanded) expandedToolIds.remove(item.id) else expandedToolIds.add(item.id)
                    bindingAdapterPosition.takeIf { it != RecyclerView.NO_POSITION }?.let(::notifyItemChanged)
                }
            })
            if (expanded && !item.detail.isNullOrBlank()) {
                addView(TextView(context).apply {
                    text = item.detail
                    textSize = 12.5f
                    typeface = Typeface.MONOSPACE
                    includeFontPadding = false
                    setLineSpacing(0f, 1.2f)
                    setTextColor(tokens.textTertiary)
                    setPadding(ui.dp(26), ui.dp(2), 0, ui.dp(7))
                    setTextIsSelectable(true)
                })
            }
        }

        private fun toolGroup(
            process: AgentConversationDisplayItem.Process,
            tools: List<AgentConversationDisplayItem.Tool>,
        ): View {
            if (tools.size == 1) return toolRow(tools.single())
            val groupId = "${process.id}:tools:${tools.first().id}"
            val expanded = toolGroupExpansionOverrides[groupId] ?: false
            return LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, ui.dp(4), 0, ui.dp(4))
                    addView(ImageView(context).apply {
                        setImageResource(R.drawable.ic_terminal_prompt_light)
                        setColorFilter(tokens.textTertiary)
                        scaleType = ImageView.ScaleType.CENTER_INSIDE
                    }, LinearLayout.LayoutParams(ui.dp(18), ui.dp(18)).apply { marginEnd = ui.dp(8) })
                    addView(TextView(context).apply {
                        text = if (tools.all { tool -> tool.kind?.lowercase()?.let { kind ->
                                "command" in kind || "terminal" in kind
                            } == true }) {
                            "运行了多个命令"
                        } else {
                            "调用了多个工具"
                        }
                        textSize = 13.5f
                        includeFontPadding = false
                        setTextColor(tokens.textSecondary)
                    }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                    addView(ImageView(context).apply {
                        setImageResource(R.drawable.ic_chevron_right_light)
                        setColorFilter(tokens.textTertiary)
                        rotation = if (expanded) 90f else 0f
                        scaleType = ImageView.ScaleType.CENTER_INSIDE
                    }, LinearLayout.LayoutParams(ui.dp(18), ui.dp(18)))
                    isClickable = true
                    isFocusable = true
                    contentDescription = if (expanded) "收起工具详情" else "展开工具详情"
                    setOnClickListener {
                        toolGroupExpansionOverrides[groupId] = !expanded
                        bindingAdapterPosition.takeIf { it != RecyclerView.NO_POSITION }?.let(::notifyItemChanged)
                    }
                })
                if (expanded) {
                    tools.forEach { tool ->
                        addView(toolRow(tool), LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                        ).apply { marginStart = ui.dp(24) })
                    }
                }
            }
        }

        private fun planRows(item: AgentConversationDisplayItem.Plan): View = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            item.entries.take(PLAN_ROWS).forEach { entry ->
                addView(TextView(context).apply {
                    text = "${planMark(entry.status)} ${entry.content}"
                    textSize = 13.5f
                    includeFontPadding = false
                    setTextColor(
                        if (entry.status.lowercase() in COMPLETED_STATUSES) tokens.textTertiary else tokens.textSecondary
                    )
                    setPadding(ui.dp(26), ui.dp(3), 0, ui.dp(3))
                })
            }
        }

        override fun recycle() {
            entries.removeAllViews()
        }
    }

    inner class AnswerCopyHolder : DisplayHolder(
        FrameLayout(context).apply {
            layoutParams = rowParams(top = 0, bottom = 8)
        }
    ) {
        private val copy = copyIconButton("复制回答").also { button ->
            (itemView as FrameLayout).addView(
                button,
                FrameLayout.LayoutParams(ui.dp(COPY_BUTTON_SIZE_DP), ui.dp(COPY_BUTTON_SIZE_DP), Gravity.START),
            )
        }

        override fun bind(item: AgentConversationDisplayItem) {
            item as AgentConversationDisplayItem.AnswerCopy
            copy.setOnClickListener { copyText("Agent 回答", item.copyText) }
        }
    }

    inner class ThoughtHolder : DisplayHolder(
        AgentThoughtRowView(context, tokens).apply { layoutParams = rowParams(top = 5, bottom = 7) }
    ) {
        override fun bind(item: AgentConversationDisplayItem) {
            item as AgentConversationDisplayItem.Thought
            val expanded = item.id in expandedThoughtIds
            (itemView as AgentThoughtRowView).bind(item.text, expanded) {
                if (expanded) expandedThoughtIds.remove(item.id) else expandedThoughtIds.add(item.id)
                bindingAdapterPosition.takeIf { it != RecyclerView.NO_POSITION }?.let(::notifyItemChanged)
            }
        }
    }

    inner class ToolHolder : DisplayHolder(
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = rowParams(top = 5, bottom = 7)
            setPadding(ui.dp(13), ui.dp(10), ui.dp(13), ui.dp(10))
            background = ui.roundedBox(tokens.surface, tokens.border, ui.dp(14).toFloat(), ui.dp(1))
        }
    ) {
        private val container = itemView as LinearLayout
        private val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }.also(container::addView)
        private val title = TextView(context).apply {
            textSize = 14.5f
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            setTextColor(tokens.textPrimary)
        }.also { header.addView(it, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)) }
        private val status = TextView(context).apply {
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(ui.dp(8), ui.dp(3), ui.dp(8), ui.dp(3))
        }.also(header::addView)
        private val detail = messageTextView().apply {
            textSize = 12.5f
            setTextColor(tokens.textSecondary)
            setPadding(0, ui.dp(6), 0, 0)
        }.also(container::addView)

        override fun bind(item: AgentConversationDisplayItem) {
            item as AgentConversationDisplayItem.Tool
            title.text = "工具 · ${item.title}"
            status.text = item.status
            val tone = when (item.status) {
                "已完成" -> Triple(tokens.success, tokens.successSoft, tokens.successBorder)
                "失败" -> Triple(tokens.danger, tokens.dangerSoft, tokens.dangerBorder)
                else -> Triple(tokens.warning, tokens.warningSoft, tokens.warningBorder)
            }
            status.setTextColor(tone.first)
            status.background = ui.roundedBox(tone.second, tone.third, ui.dp(10).toFloat(), ui.dp(1))
            detail.text = item.detail.orEmpty()
            detail.visibility = if (item.detail.isNullOrBlank()) View.GONE else View.VISIBLE
            val expanded = item.id in expandedToolIds
            detail.maxLines = if (expanded) Int.MAX_VALUE else COLLAPSED_TOOL_LINES
            detail.ellipsize = if (expanded) null else TextUtils.TruncateAt.END
            container.contentDescription = buildString {
                append(title.text)
                append("，")
                append(item.status)
                if (!item.detail.isNullOrBlank()) {
                    append(if (expanded) "，点击收起详情" else "，点击展开详情")
                }
            }
            container.setOnClickListener {
                if (item.detail.isNullOrBlank()) return@setOnClickListener
                if (expanded) expandedToolIds.remove(item.id) else expandedToolIds.add(item.id)
                bindingAdapterPosition.takeIf { it != RecyclerView.NO_POSITION }?.let(::notifyItemChanged)
            }
        }
    }

    inner class PlanHolder : DisplayHolder(
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = rowParams(top = 5, bottom = 7)
            setPadding(ui.dp(13), ui.dp(10), ui.dp(13), ui.dp(10))
            background = ui.roundedBox(tokens.surface, tokens.border, ui.dp(14).toFloat(), ui.dp(1))
        }
    ) {
        private val container = itemView as LinearLayout
        private val label = sectionLabel().apply { text = "执行计划" }.also(container::addView)
        private val lines = List(PLAN_ROWS) {
            TextView(context).apply {
                textSize = 13.5f
                includeFontPadding = false
                setTextColor(tokens.textSecondary)
                setPadding(0, ui.dp(3), 0, ui.dp(3))
            }.also(container::addView)
        }

        override fun bind(item: AgentConversationDisplayItem) {
            item as AgentConversationDisplayItem.Plan
            lines.forEachIndexed { index, text ->
                val entry = item.entries.getOrNull(index)
                text.visibility = if (entry == null) View.GONE else View.VISIBLE
                if (entry != null) {
                    text.text = "${planMark(entry.status)} ${entry.content}"
                    text.setTextColor(if (entry.status.lowercase() in COMPLETED_STATUSES) tokens.textTertiary else tokens.textSecondary)
                }
            }
            label.text = if (item.entries.size > PLAN_ROWS) {
                "执行计划 · ${item.entries.size} 项"
            } else {
                "执行计划"
            }
        }
    }

    inner class AttachmentHolder : DisplayHolder(
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = rowParams(top = 5, bottom = 7)
            setPadding(ui.dp(13), ui.dp(10), ui.dp(13), ui.dp(10))
            background = ui.roundedBox(tokens.surface, tokens.border, ui.dp(14).toFloat(), ui.dp(1))
        }
    ) {
        private val container = itemView as LinearLayout
        private var openJob: Job? = null
        private var boundId: String? = null
        private val title = TextView(context).apply {
            textSize = 14.5f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(tokens.textPrimary)
        }.also(container::addView)
        private val detail = TextView(context).apply {
            textSize = 12.5f
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            setTextColor(tokens.textSecondary)
            setPadding(0, ui.dp(3), 0, 0)
        }.also(container::addView)

        override fun bind(item: AgentConversationDisplayItem) {
            item as AgentConversationDisplayItem.Attachment
            openJob?.cancel()
            boundId = item.id
            title.text = item.title
            detail.text = "${item.detail} · 点击打开"
            container.contentDescription = "${item.title}，${item.detail}，点击打开"
            container.setOnClickListener { openAttachment(item) }
        }

        private fun openAttachment(item: AgentConversationDisplayItem.Attachment) {
            openJob?.cancel()
            detail.text = "${item.detail} · 正在准备"
            openJob = scope.launch {
                val result = runCatching {
                    mediaRepository.resolveOpenUri(
                        cacheKey = item.id,
                        displayName = item.title,
                        mimeType = item.mimeType,
                        source = item.source
                    )
                }
                if (boundId != item.id) return@launch
                result.onSuccess { uri ->
                    detail.text = "${item.detail} · 点击打开"
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        if (uri.scheme == "http" || uri.scheme == "https") {
                            data = uri
                        } else {
                            setDataAndType(uri, item.mimeType ?: "*/*")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                    }
                    runCatching { context.startActivity(intent) }
                        .onFailure {
                            Toast.makeText(context, "没有可打开此文件的应用", Toast.LENGTH_LONG).show()
                        }
                }.onFailure { error ->
                    detail.text = "${item.detail} · 无法打开"
                    Toast.makeText(
                        context,
                        error.message ?: "无法打开附件",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }

        override fun recycle() {
            openJob?.cancel()
            openJob = null
            boundId = null
        }
    }

    inner class ImageHolder : DisplayHolder(
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = rowParams(top = 5, bottom = 9)
            setPadding(ui.dp(7), ui.dp(7), ui.dp(7), ui.dp(9))
            background = ui.roundedBox(tokens.surface, tokens.border, ui.dp(15).toFloat(), ui.dp(1))
        }
    ) {
        private val container = itemView as LinearLayout
        private var loadJob: Job? = null
        private var boundId: String? = null
        private val image = ImageView(context).apply {
            adjustViewBounds = true
            minimumHeight = ui.dp(150)
            maxHeight = ui.dp(300)
            scaleType = ImageView.ScaleType.CENTER_CROP
            setBackgroundColor(tokens.surfaceElevated)
        }.also { container.addView(it, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(220))) }
        private val status = TextView(context).apply {
            textSize = 12.5f
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            setTextColor(tokens.textSecondary)
            setPadding(ui.dp(7), ui.dp(7), ui.dp(7), 0)
        }.also(container::addView)

        override fun bind(item: AgentConversationDisplayItem) {
            item as AgentConversationDisplayItem.Image
            loadJob?.cancel()
            boundId = item.id
            image.setImageDrawable(null)
            status.text = "${item.title} · 正在载入预览"
            container.contentDescription = "${item.title}，正在载入预览"
            container.setOnClickListener(null)
            loadJob = scope.launch {
                val result = runCatching {
                    mediaRepository.loadThumbnail(item.id, item.source, item.mimeType)
                }
                if (boundId != item.id) return@launch
                result.onSuccess { bitmap ->
                    image.setImageBitmap(bitmap)
                    status.text = "${item.title} · 点击查看"
                    container.contentDescription = "${item.title}，点击查看"
                    container.setOnClickListener { showImage(item.title, bitmap) }
                }.onFailure { error ->
                    status.text = "${item.title} · ${error.message ?: "预览失败"}"
                    container.contentDescription = status.text
                }
            }
        }

        override fun recycle() {
            loadJob?.cancel()
            loadJob = null
            boundId = null
            image.setImageDrawable(null)
        }
    }

    private fun messageTextView(): TextView = TextView(context).apply {
        textSize = 17f
        setTextColor(tokens.textPrimary)
        includeFontPadding = false
        setLineSpacing(0f, 1.22f)
        setTextIsSelectable(true)
    }

    private fun sectionLabel(): TextView = TextView(context).apply {
        textSize = 11.5f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(tokens.textTertiary)
        setPadding(0, 0, 0, ui.dp(5))
    }

    private fun rowParams(top: Int, bottom: Int): RecyclerView.LayoutParams =
        RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            setMargins(ui.dp(18), ui.dp(top), ui.dp(18), ui.dp(bottom))
        }

    private fun copyIconButton(description: String): ImageButton = ImageButton(context).apply {
        setImageResource(R.drawable.ic_paste_light)
        imageTintList = ColorStateList.valueOf(tokens.textSecondary)
        scaleType = ImageView.ScaleType.CENTER_INSIDE
        setPadding(ui.dp(8), ui.dp(8), ui.dp(8), ui.dp(8))
        background = null
        contentDescription = description
        minimumWidth = 0
        minimumHeight = 0
    }

    private fun copyButtonLayoutParams(): LinearLayout.LayoutParams = LinearLayout.LayoutParams(
        ui.dp(COPY_BUTTON_SIZE_DP),
        ui.dp(COPY_BUTTON_SIZE_DP),
    )

    private fun copyText(label: String, value: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, value))
        Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
    }

    private fun showImage(title: String, bitmap: Bitmap) {
        val image = ImageView(context).apply {
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
            setImageBitmap(bitmap)
            setPadding(ui.dp(10), ui.dp(8), ui.dp(10), ui.dp(8))
        }
        AlertDialog.Builder(context)
            .setTitle(title)
            .setView(image)
            .setPositiveButton("关闭", null)
            .show()
    }

    private fun styledInlineText(segments: List<AgentInlineTextSegment>): CharSequence {
        val result = SpannableStringBuilder()
        segments.forEach { segment ->
            val start = result.length
            result.append(segment.text)
            val end = result.length
            if (AgentInlineTextSegment.Style.Strong in segment.styles) result.setSpan(
                    StyleSpan(Typeface.BOLD),
                    start,
                    end,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            if (AgentInlineTextSegment.Style.Emphasis in segment.styles) result.setSpan(
                StyleSpan(Typeface.ITALIC),
                start,
                end,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
            if (AgentInlineTextSegment.Style.Strike in segment.styles) result.setSpan(
                StrikethroughSpan(),
                start,
                end,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
            if (AgentInlineTextSegment.Style.Code in segment.styles) {
                    result.setSpan(
                        TypefaceSpan("monospace"),
                        start,
                        end,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    result.setSpan(
                        BackgroundColorSpan(tokens.surfaceElevated),
                        start,
                        end,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
            }
            if (AgentInlineTextSegment.Style.Link in segment.styles) segment.link?.let { url ->
                    result.setSpan(
                        object : ClickableSpan() {
                            override fun onClick(widget: View) {
                                AgentMarkdownLinkRouter.open(widget.context, url)
                            }

                            override fun updateDrawState(drawState: TextPaint) {
                                drawState.color = tokens.primaryStrong
                                drawState.isUnderlineText = false
                            }
                        },
                        start,
                        end,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                    )
            }
        }
        return result
    }

    private fun planMark(status: String): String = when (status.lowercase()) {
        in COMPLETED_STATUSES -> "✓"
        "in_progress", "running" -> "◉"
        "failed", "error" -> "!"
        else -> "○"
    }

    private companion object {
        const val TYPE_USER = 1
        const val TYPE_ASSISTANT = 2
        const val TYPE_CODE = 3
        const val TYPE_THOUGHT = 4
        const val TYPE_TOOL = 5
        const val TYPE_PLAN = 6
        const val TYPE_IMAGE = 7
        const val TYPE_ATTACHMENT = 8
        const val TYPE_RULE = 9
        const val TYPE_TABLE = 10
        const val TYPE_PROCESS = 11
        const val TYPE_ANSWER_COPY = 12
        const val COPY_BUTTON_SIZE_DP = 36
        const val PLAN_ROWS = 6
        const val COLLAPSED_TOOL_LINES = 4
        const val BODY_TEXT_WEIGHT = 450
        const val CONVERSATION_LETTER_SPACING = 0.025f
        const val BODY_LETTER_SPACING = 0.03f
        val COMPLETED_STATUSES = setOf("completed", "complete", "success", "succeeded")

        val DIFF = object : DiffUtil.ItemCallback<AgentConversationDisplayItem>() {
            override fun areItemsTheSame(
                oldItem: AgentConversationDisplayItem,
                newItem: AgentConversationDisplayItem
            ): Boolean =
                oldItem.id == newItem.id

            override fun areContentsTheSame(
                oldItem: AgentConversationDisplayItem,
                newItem: AgentConversationDisplayItem
            ): Boolean =
                oldItem == newItem
        }
    }
}
