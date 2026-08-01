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
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import android.text.style.URLSpan
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
import com.kite.app.agent.config.AgentConfigAdapterRegistry
import com.kite.app.agent.config.AgentConfigAdapter
import com.kite.app.agent.config.AgentConfigApplyRequest
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
import com.kite.app.agent.config.AgentPersistentConfigChange
import com.kite.app.agent.config.AgentPermissionProfileSummary
import com.kite.app.agent.config.AgentProviderCredentialChange
import com.kite.app.agent.config.AgentProviderDraft
import com.kite.app.agent.config.AgentProviderModelSummary
import com.kite.app.agent.config.AgentProviderPreset
import com.kite.app.agent.config.AgentProviderPresetCatalog
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
import com.kite.app.agent.store.AgentConversationTurnState
import com.kite.app.agent.store.AgentDraftCapabilityCacheStore
import com.kite.app.agent.store.AgentModelLibraryStore
import com.kite.app.agent.store.AgentProject
import com.kite.app.agent.store.AgentArchivedSessionMetadata
import com.kite.app.agent.store.AgentArchivedSessionSourceState
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

internal fun renderArchivedSelectionIndicator(
    indicator: ImageView,
    ui: UiKit,
    palette: AgentSelectionPalette,
    state: AgentArchivedProjectSelectionState,
) {
    val outerInset = ui.dp(
        (AgentSelectionVisualPolicy.TOUCH_TARGET_DP - AgentSelectionVisualPolicy.INDICATOR_SIZE_DP) / 2,
    )
    indicator.background = InsetDrawable(
        ui.roundedBox(
            palette.indicatorSurface,
            palette.unselectedIndicatorStroke,
            ui.dp(AgentSelectionVisualPolicy.INDICATOR_SIZE_DP / 2).toFloat(),
            ui.dp(1),
        ),
        outerInset,
    )
    val dotSize = when (state) {
        AgentArchivedProjectSelectionState.Unchecked -> 0
        AgentArchivedProjectSelectionState.Partial -> AgentSelectionVisualPolicy.PARTIAL_DOT_SIZE_DP
        AgentArchivedProjectSelectionState.Checked -> AgentSelectionVisualPolicy.CHECKED_DOT_SIZE_DP
    }
    if (dotSize == 0) {
        indicator.setImageDrawable(null)
        indicator.setPadding(0, 0, 0, 0)
    } else {
        val dotInset = ui.dp((AgentSelectionVisualPolicy.TOUCH_TARGET_DP - dotSize) / 2)
        indicator.setImageDrawable(
            ui.roundedBox(
                fill = palette.selectedIndicator,
                stroke = android.graphics.Color.TRANSPARENT,
                radius = ui.dp(dotSize / 2).toFloat(),
                strokeWidth = 0,
            ),
        )
        indicator.setPadding(dotInset, dotInset, dotInset, dotInset)
    }
    indicator.imageTintList = null
    indicator.scaleType = ImageView.ScaleType.FIT_CENTER
    indicator.contentDescription = when (state) {
        AgentArchivedProjectSelectionState.Unchecked -> "未选择"
        AgentArchivedProjectSelectionState.Partial -> "已选择部分"
        AgentArchivedProjectSelectionState.Checked -> "已选择"
    }
}

internal class ArchivedSessionAdapter(
    private val context: Context,
    private val tokens: ThemeTokens,
    private val onClick: (AgentSessionSummary) -> Unit,
    private val onLongClick: (AgentSessionSummary) -> Unit,
    private val onUnavailableClick: (AgentArchivedSessionMetadata) -> Unit,
    private val onUnavailableLongClick: (AgentArchivedSessionMetadata) -> Unit,
    private val onGroupToggle: (String) -> Unit,
    private val onProjectRestore: (AgentProject) -> Unit,
    private val onProjectSelect: (AgentArchivedRow.GroupHeader) -> Unit,
    private val onProjectLongClick: (AgentArchivedRow.GroupHeader) -> Unit,
) : ListAdapter<AgentArchivedRow, RecyclerView.ViewHolder>(DIFF) {
    private val ui = UiKit(context, tokens)
    private val selectionPalette = AgentSelectionVisualPolicy.palette(
        isDark = tokens.pageBackground == android.graphics.Color.BLACK,
    )
    private var selectionMode = false
    private var selectedIds: Set<String> = emptySet()

    fun setSelectionState(
        selectionMode: Boolean,
        selectedIds: Set<String>,
    ) {
        val nextIds = selectedIds.toSet()
        if (this.selectionMode == selectionMode && this.selectedIds == nextIds) return
        this.selectionMode = selectionMode
        this.selectedIds = nextIds
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        is AgentArchivedRow.GroupHeader -> TYPE_GROUP
        is AgentArchivedRow.Session -> TYPE_SESSION
        is AgentArchivedRow.UnavailableSession -> TYPE_SESSION
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder =
        if (viewType == TYPE_GROUP) {
            GroupHolder(LinearLayout(context))
        } else {
            Holder(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(ui.dp(30), ui.dp(8), ui.dp(12), ui.dp(8))
                layoutParams = RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, ui.dp(2), 0, ui.dp(2)) }
            })
        }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = getItem(position)) {
            is AgentArchivedRow.GroupHeader -> (holder as GroupHolder).bind(row)
            is AgentArchivedRow.Session -> (holder as Holder).bind(
                row.summary,
                selectionMode,
                row.summary.id in selectedIds
            )
            is AgentArchivedRow.UnavailableSession -> (holder as Holder).bindUnavailable(
                row.metadata,
                selectionMode,
                row.metadata.sessionId in selectedIds,
            )
        }
    }

    private fun renderSelectionIndicator(
        indicator: ImageView,
        state: AgentArchivedProjectSelectionState,
    ) {
        renderArchivedSelectionIndicator(
            indicator = indicator,
            ui = ui,
            palette = selectionPalette,
            state = state,
        )
    }

    private fun renderSelectionIndicator(indicator: ImageView, selected: Boolean) {
        renderSelectionIndicator(
            indicator,
            if (selected) AgentArchivedProjectSelectionState.Checked
            else AgentArchivedProjectSelectionState.Unchecked,
        )
    }

    inner class GroupHolder(private val container: LinearLayout) : RecyclerView.ViewHolder(container) {
        private val chevron = ImageView(context).apply {
            setImageResource(R.drawable.ic_chevron_right_light)
            imageTintList = ColorStateList.valueOf(tokens.textSecondary)
            setPadding(ui.dp(9), ui.dp(9), ui.dp(9), ui.dp(9))
        }
        private val selector = ImageView(context).apply {
            scaleType = ImageView.ScaleType.CENTER
        }
        private val title = TextView(context).apply {
            textSize = 14.5f
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setTextColor(tokens.textPrimary)
        }
        private val subtitle = TextView(context).apply {
            textSize = 11.5f
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.MIDDLE
            setTextColor(tokens.textSecondary)
            setPadding(0, ui.dp(2), 0, 0)
        }
        private val restoreAction = TextView(context).apply {
            text = "恢复"
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(tokens.primaryStrong)
            setPadding(ui.dp(10), ui.dp(8), ui.dp(10), ui.dp(8))
            isClickable = true
            isFocusable = true
        }

        init {
            container.orientation = LinearLayout.HORIZONTAL
            container.gravity = Gravity.CENTER_VERTICAL
            container.setPadding(ui.dp(2), ui.dp(8), ui.dp(12), ui.dp(8))
            container.layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, ui.dp(3), 0, ui.dp(3)) }
            container.addView(selector, LinearLayout.LayoutParams(
                ui.dp(AgentSelectionVisualPolicy.TOUCH_TARGET_DP),
                ui.dp(AgentSelectionVisualPolicy.TOUCH_TARGET_DP),
            ))
            container.addView(chevron, LinearLayout.LayoutParams(ui.dp(40), ui.dp(40)))
            container.addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(title)
                addView(subtitle)
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            container.addView(restoreAction, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ui.dp(40),
            ))
        }

        fun bind(row: AgentArchivedRow.GroupHeader) {
            val projectSelectionState = AgentArchivedSelectionPolicy.projectSelectionState(
                selected = selectedIds.mapTo(linkedSetOf(), AgentArchivedSelectionKey::Session),
                childSessionIds = row.selectableSessionIds,
            )
            val showProjectSelector = selectionMode && row.selectableSessionIds.isNotEmpty()
            selector.visibility = if (showProjectSelector) View.VISIBLE else View.GONE
            renderSelectionIndicator(selector, projectSelectionState)
            chevron.rotation = if (row.expanded) 90f else 0f
            chevron.visibility = if (showProjectSelector) {
                View.GONE
            } else if (row.count > 0) {
                View.VISIBLE
            } else {
                View.INVISIBLE
            }
            title.text = row.title
            subtitle.text = row.subtitle.orEmpty()
            subtitle.visibility = if (row.subtitle.isNullOrBlank()) View.GONE else View.VISIBLE
            restoreAction.visibility = if (row.archivedProject != null && !selectionMode) View.VISIBLE else View.GONE
            restoreAction.contentDescription = "恢复项目 ${row.title}"
            restoreAction.setOnClickListener {
                row.archivedProject?.let(onProjectRestore)
            }
            container.contentDescription = if (row.count == 0) {
                "已归档项目 ${row.title}"
            } else if (row.expanded) {
                "收起 ${row.title} 的归档会话"
            } else {
                "展开 ${row.title} 的归档会话"
            }
            container.setOnClickListener {
                if (showProjectSelector) {
                    onProjectSelect(row)
                } else if (row.count > 0) {
                    onGroupToggle(row.cwd)
                }
            }
            container.setOnLongClickListener {
                if (row.selectableSessionIds.isEmpty()) {
                    false
                } else {
                    onProjectLongClick(row)
                    true
                }
            }
        }
    }

    inner class Holder(private val container: LinearLayout) : RecyclerView.ViewHolder(container) {
        private val selector = ImageView(context).apply {
            scaleType = ImageView.ScaleType.CENTER
            isClickable = false
            isFocusable = false
        }
        private val title = TextView(context).apply {
            textSize = 14.5f
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setTextColor(tokens.textPrimary)
        }
        private val subtitle = TextView(context).apply {
            textSize = 11.5f
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setTextColor(tokens.textSecondary)
            setPadding(0, ui.dp(3), 0, 0)
        }

        init {
            container.addView(selector, LinearLayout.LayoutParams(
                ui.dp(AgentSelectionVisualPolicy.TOUCH_TARGET_DP),
                ui.dp(AgentSelectionVisualPolicy.TOUCH_TARGET_DP),
            ))
            container.addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(title)
                addView(subtitle)
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(ui.dp(4), 0, 0, 0)
            })
        }

        fun bind(session: AgentSessionSummary, selectionMode: Boolean, selected: Boolean) {
            selector.visibility = if (selectionMode) View.VISIBLE else View.GONE
            renderSelectionIndicator(selector, selected)
            title.text = session.title?.takeIf(String::isNotBlank) ?: "未命名会话"
            title.typeface = if (selected) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            subtitle.text = buildString {
                append(session.cwd.ifBlank { session.id })
                append(" · ").append(AgentSessionRelativeTimeFormatter.format(session.updatedAt))
            }
            container.background = ui.roundedBox(
                if (selected) selectionPalette.selectedRow else android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT,
                ui.dp(16).toFloat()
            )
            container.contentDescription = when {
                selectionMode && selected -> "取消选择，${title.text}"
                selectionMode -> "选择，${title.text}"
                else -> "管理归档会话，${title.text}"
            }
            container.setOnClickListener { onClick(session) }
            container.setOnLongClickListener {
                onLongClick(session)
                true
            }
        }

        fun bindUnavailable(
            metadata: AgentArchivedSessionMetadata,
            selectionMode: Boolean,
            selected: Boolean,
        ) {
            val sourceDeleted = metadata.sourceState == AgentArchivedSessionSourceState.Deleted
            selector.visibility = if (selectionMode) View.VISIBLE else View.GONE
            renderSelectionIndicator(selector, selected)
            title.text = if (sourceDeleted) "源会话已删除" else "尚未确认的归档会话"
            title.typeface = if (selected) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            subtitle.text = "会话 ID · ${metadata.sessionId}"
            container.background = ui.roundedBox(
                if (selected) selectionPalette.selectedRow else android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT,
                ui.dp(16).toFloat()
            )
            container.contentDescription = "管理${title.text}，${metadata.sessionId}"
            container.setOnClickListener { onUnavailableClick(metadata) }
            container.setOnLongClickListener {
                onUnavailableLongClick(metadata)
                true
            }
        }
    }

    private companion object {
        const val TYPE_GROUP = 1
        const val TYPE_SESSION = 2

        val DIFF = object : DiffUtil.ItemCallback<AgentArchivedRow>() {
            override fun areItemsTheSame(oldItem: AgentArchivedRow, newItem: AgentArchivedRow): Boolean =
                oldItem.key == newItem.key

            override fun areContentsTheSame(oldItem: AgentArchivedRow, newItem: AgentArchivedRow): Boolean =
                oldItem == newItem
        }
    }
}
internal class AgentSessionDrawerAdapter(
    private val context: Context,
    private val tokens: ThemeTokens,
    private val onSessionClick: (AgentSessionSummary) -> Unit,
    private val onSessionMenu: (View, AgentSessionSummary) -> Unit,
    private val onProjectToggle: (String) -> Unit,
    private val onProjectMenu: (View, AgentSessionProjectGroup) -> Unit,
    private val onAction: (AgentDrawerAction) -> Unit
) : ListAdapter<AgentDrawerRow, RecyclerView.ViewHolder>(DIFF) {
    private val ui = UiKit(context, tokens)
    private val selectionPalette = AgentSelectionVisualPolicy.palette(
        isDark = tokens.pageBackground == android.graphics.Color.BLACK,
    )
    var selectedSessionId: String? = null
        set(value) {
            if (field == value) return
            field = value
            notifyDataSetChanged()
        }

    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        is AgentDrawerRow.SectionHeader -> TYPE_SECTION
        is AgentDrawerRow.ProjectHeader -> TYPE_PROJECT
        is AgentDrawerRow.Session -> TYPE_SESSION
        is AgentDrawerRow.Empty -> TYPE_EMPTY
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder = when (viewType) {
        TYPE_SECTION -> SectionHolder(LinearLayout(context))
        TYPE_PROJECT -> ProjectHolder(LinearLayout(context))
        TYPE_SESSION -> SessionHolder(LinearLayout(context))
        else -> EmptyHolder(TextView(context))
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = getItem(position)) {
            is AgentDrawerRow.SectionHeader -> (holder as SectionHolder).bind(row)
            is AgentDrawerRow.ProjectHeader -> (holder as ProjectHolder).bind(row)
            is AgentDrawerRow.Session -> (holder as SessionHolder).bind(
                row,
                selected = row.summary.id == selectedSessionId
            )
            is AgentDrawerRow.Empty -> (holder as EmptyHolder).bind(row)
        }
    }

    private inner class SectionHolder(private val container: LinearLayout) : RecyclerView.ViewHolder(container) {
        private val title = TextView(context).apply {
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(tokens.textSecondary)
            includeFontPadding = false
        }
        private val action = ImageButton(context).apply {
            setImageResource(R.drawable.ic_compose_outline)
            imageTintList = ColorStateList.valueOf(tokens.textTertiary)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(ui.dp(11), ui.dp(11), ui.dp(11), ui.dp(11))
            background = ui.roundedBox(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT,
                ui.dp(20).toFloat()
            )
        }

        init {
            container.orientation = LinearLayout.HORIZONTAL
            container.gravity = Gravity.CENTER_VERTICAL
            container.setPadding(ui.dp(8), ui.dp(10), ui.dp(2), ui.dp(4))
            container.layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            container.addView(title, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            container.addView(action, LinearLayout.LayoutParams(ui.dp(40), ui.dp(40)))
        }

        fun bind(row: AgentDrawerRow.SectionHeader) {
            title.text = row.title
            action.contentDescription = row.actionDescription
            action.setOnClickListener { onAction(row.action) }
        }
    }

    private inner class ProjectHolder(private val container: LinearLayout) : RecyclerView.ViewHolder(container) {
        private val folder = ImageView(context).apply {
            setImageResource(R.drawable.ic_folder_closed_outline)
            imageTintList = ColorStateList.valueOf(tokens.textTertiary)
            setPadding(ui.dp(6), ui.dp(8), ui.dp(6), ui.dp(8))
        }
        private val title = TextView(context).apply {
            textSize = 14.5f
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setTextColor(tokens.textPrimary)
        }
        private val add = ImageButton(context).apply {
            setImageResource(R.drawable.ic_compose_outline)
            imageTintList = ColorStateList.valueOf(tokens.textTertiary)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(ui.dp(11), ui.dp(11), ui.dp(11), ui.dp(11))
            background = ui.roundedBox(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT,
                ui.dp(20).toFloat()
            )
        }
        private val more = ImageButton(context).apply {
            setImageResource(R.drawable.ic_more_horizontal_light)
            imageTintList = ColorStateList.valueOf(tokens.textTertiary)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(ui.dp(11), ui.dp(11), ui.dp(11), ui.dp(11))
            background = ui.roundedBox(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT,
                ui.dp(20).toFloat()
            )
        }

        init {
            container.orientation = LinearLayout.HORIZONTAL
            container.gravity = Gravity.CENTER_VERTICAL
            container.setPadding(0, ui.dp(6), ui.dp(2), ui.dp(6))
            container.layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, ui.dp(2), 0, ui.dp(2)) }
            container.addView(folder, LinearLayout.LayoutParams(ui.dp(36), ui.dp(40)))
            container.addView(title, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            container.addView(more, LinearLayout.LayoutParams(ui.dp(40), ui.dp(40)))
            container.addView(add, LinearLayout.LayoutParams(ui.dp(40), ui.dp(40)))
        }

        fun bind(row: AgentDrawerRow.ProjectHeader) {
            folder.setImageResource(
                if (row.expanded) R.drawable.ic_folder_open_outline else R.drawable.ic_folder_closed_outline
            )
            title.text = row.project.name
            add.contentDescription = "在 ${row.project.name} 中新建会话"
            add.setOnClickListener { onAction(AgentDrawerAction.NewDraft(row.project.cwd)) }
            more.contentDescription = "${row.project.name} 项目操作"
            more.setOnClickListener { onProjectMenu(it, row.project) }
            container.contentDescription = if (row.expanded) {
                "收起项目 ${row.project.name}"
            } else {
                "展开项目 ${row.project.name}"
            }
            container.setOnClickListener { onProjectToggle(row.project.cwd) }
        }
    }

    private inner class SessionHolder(private val container: LinearLayout) : RecyclerView.ViewHolder(container) {
        private val title = TextView(context).apply {
            textSize = 14.5f
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setTextColor(tokens.textPrimary)
        }
        private val subtitle = TextView(context).apply {
            textSize = 11.5f
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setTextColor(tokens.textSecondary)
            setPadding(0, ui.dp(3), 0, 0)
        }
        private val more = ImageButton(context).apply {
            setImageResource(R.drawable.ic_more_horizontal_light)
            imageTintList = ColorStateList.valueOf(tokens.textTertiary)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(ui.dp(11), ui.dp(11), ui.dp(11), ui.dp(11))
            background = ui.roundedBox(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT,
                ui.dp(20).toFloat(),
            )
        }

        init {
            container.orientation = LinearLayout.HORIZONTAL
            container.gravity = Gravity.CENTER_VERTICAL
            container.layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, ui.dp(2), 0, ui.dp(2)) }
            container.addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(title)
                addView(subtitle)
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            container.addView(more, LinearLayout.LayoutParams(ui.dp(40), ui.dp(40)))
        }

        fun bind(row: AgentDrawerRow.Session, selected: Boolean) {
            val session = row.summary
            container.setPadding(
                ui.dp(if (row.inProject) 46 else 14),
                ui.dp(6),
                ui.dp(4),
                ui.dp(6)
            )
            title.text = session.title?.takeIf(String::isNotBlank) ?: "未命名会话"
            title.typeface = if (selected) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            subtitle.text = AgentSessionRelativeTimeFormatter.format(session.updatedAt)
            container.background = ui.roundedBox(
                if (selected) selectionPalette.selectedRow else android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT,
                ui.dp(16).toFloat()
            )
            container.contentDescription = if (selected) {
                "当前会话，${title.text}"
            } else {
                "打开会话，${title.text}"
            }
            more.contentDescription = "${title.text} 会话操作"
            more.setOnClickListener { onSessionMenu(it, session) }
            container.setOnClickListener { onSessionClick(session) }
        }
    }

    private inner class EmptyHolder(private val text: TextView) : RecyclerView.ViewHolder(text) {
        init {
            text.textSize = 12.5f
            text.setTextColor(tokens.textTertiary)
            text.setPadding(ui.dp(14), ui.dp(6), ui.dp(14), ui.dp(12))
            text.layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        fun bind(row: AgentDrawerRow.Empty) {
            text.text = row.label
        }
    }

    private companion object {
        const val TYPE_SECTION = 1
        const val TYPE_PROJECT = 2
        const val TYPE_SESSION = 3
        const val TYPE_EMPTY = 4

        val DIFF = object : DiffUtil.ItemCallback<AgentDrawerRow>() {
            override fun areItemsTheSame(oldItem: AgentDrawerRow, newItem: AgentDrawerRow): Boolean =
                oldItem.key == newItem.key

            override fun areContentsTheSame(oldItem: AgentDrawerRow, newItem: AgentDrawerRow): Boolean =
                oldItem == newItem
        }
    }
}

internal class AgentSessionAdapter(
    private val context: Context,
    private val tokens: ThemeTokens,
    private val onClick: (AgentSessionSummary) -> Unit
) : ListAdapter<AgentSessionSummary, AgentSessionAdapter.Holder>(DIFF) {
    private val ui = UiKit(context, tokens)
    var selectedSessionId: String? = null
        set(value) {
            if (field == value) return
            field = value
            notifyDataSetChanged()
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder = Holder(
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ui.dp(14), ui.dp(11), ui.dp(14), ui.dp(11))
            layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, ui.dp(2), 0, ui.dp(2)) }
        }
    )

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.bind(getItem(position), getItem(position).id == selectedSessionId)
    }

    inner class Holder(private val container: LinearLayout) : RecyclerView.ViewHolder(container) {
        private val title = TextView(context).apply {
            textSize = 14.5f
            maxLines = 1
            setTextColor(tokens.textPrimary)
        }
        private val subtitle = TextView(context).apply {
            textSize = 11.5f
            maxLines = 1
            setTextColor(tokens.textSecondary)
            setPadding(0, ui.dp(3), 0, 0)
        }

        init {
            container.addView(title)
            container.addView(subtitle)
        }

        fun bind(session: AgentSessionSummary, selected: Boolean) {
            title.text = session.title?.takeIf(String::isNotBlank) ?: "未命名会话"
            title.typeface = if (selected) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            subtitle.text = buildString {
                append(session.cwd.ifBlank { session.id })
                append(" · ").append(AgentSessionRelativeTimeFormatter.format(session.updatedAt))
            }
            container.background = ui.roundedBox(
                if (selected) tokens.primarySubtle else android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT,
                ui.dp(16).toFloat()
            )
            container.contentDescription = if (selected) {
                "当前会话，${title.text}"
            } else {
                "打开会话，${title.text}"
            }
            container.setOnClickListener { onClick(session) }
        }
    }

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<AgentSessionSummary>() {
            override fun areItemsTheSame(oldItem: AgentSessionSummary, newItem: AgentSessionSummary): Boolean =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: AgentSessionSummary, newItem: AgentSessionSummary): Boolean =
                oldItem == newItem
        }
    }
}
