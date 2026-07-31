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

/** ChatGPT Android 会话骨架在 Kite 运行窗口中的原生实现。 */
internal class RunAgentSurfaceBinding(
    private val context: Context,
    tokens: ThemeTokens,
    private val onCloseInstance: () -> Unit,
    private val onPickImages: () -> Unit,
    private val onPickFiles: () -> Unit,
    private val agentRegistry: KiteAgentRegistry,
    private val agentConfigAdapters: AgentConfigAdapterRegistry
) : RunSurfaceBinding {
    private val isDark = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
        Configuration.UI_MODE_NIGHT_YES
    private val tokens = AgentSurfaceThemePolicy.project(tokens, isDark)
    private val ui = UiKit(context, this.tokens)
    private val sessionMetadataStore = AgentSessionMetadataStore(context)
    private val projectStore = AgentProjectStore(context)
    private val agentPageBackground = this.tokens.pageBackground
    private val agentSurface = this.tokens.surface
    private val agentInputBackground = this.tokens.inputBackground
    private val agentSettingsSurface = this.tokens.cardBackground
    private val agentBorder = android.graphics.Color.TRANSPARENT
    private val lifecycleOwner = context as LifecycleOwner
    private val topBar = LinearLayout(context)
    private val agentTitleText = TextView(context)
    private val statusText = TextView(context)
    private val historyStatusText = TextView(context)
    private val list = RecyclerView(context)
    private val permissionHost = LinearLayout(context)
    private val composerArea = LinearLayout(context)
    private val attachmentHost = LinearLayout(context)
    private val composer = LinearLayout(context)
    private val sessionPermissionHost = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }
    private val sessionConfigurationHost = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }
    private val sessionConfigurationOverlay = FrameLayout(context).apply {
        visibility = View.GONE
        isClickable = true
    }
    private val composerExtensionOverlay = FrameLayout(context).apply {
        visibility = View.GONE
        isClickable = true
    }
    private val commandPaletteOverlay = FrameLayout(context).apply {
        visibility = View.GONE
        isClickable = false
    }
    private val input = EditText(context)
    private val actionButton = ImageButton(context)
    private val adapter = ConversationAdapter(context, tokens, lifecycleOwner.lifecycleScope)
    private val navigationHost = FrameLayout(context)
    private val drawerList = RecyclerView(context)
    private val drawerStatusText = TextView(context)
    private val drawerAdapter = AgentSessionDrawerAdapter(
        context = context,
        tokens = this.tokens,
        onSessionClick = ::loadDrawerSession,
        onProjectToggle = ::toggleDrawerProject,
        onProjectMenu = ::showProjectMenu,
        onAction = ::handleDrawerAction
    )
    private val sessionSearchInput = EditText(context)
    private val sessionSearchList = RecyclerView(context)
    private val sessionSearchStatusText = TextView(context)
    private val sessionSearchAdapter = AgentSessionAdapter(context, tokens, ::loadDrawerSession)
    private val settingsContentHost = LinearLayout(context)
    private val persistentConfigSettingsHost = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private val sessionDrawerView: View by lazy(LazyThreadSafetyMode.NONE, ::buildSessionDrawer)
    private val sessionSearchPageView: View by lazy(LazyThreadSafetyMode.NONE, ::buildSessionSearchPage)
    private val settingsPageView: View by lazy(LazyThreadSafetyMode.NONE, ::buildSettingsPage)
    private var observation: Job? = null
    private var navigationJob: Job? = null
    private var observedKey: AgentConversationKey? = null
    private var currentSnapshot: AgentConversationSnapshot? = null
    private var instanceId: String = ""
    private var generation: Long = 0L
    private var providerId: String? = null
    private var sessionId: String? = null
    private var agentId: String? = null
    private var agentDisplayName: String = "Agent"
    private var navigationScreen = AgentNavigationScreen.Main
    private var settingsReturnsToDrawer = false
    private var selectedSettingsAgentId: String? = null
    private var persistentConfigAgentId: String? = null
    private var persistentConfigResult: AgentConfigReadResult? = null
    private var defaultPermissionPendingProfileId: String? = null
    private var settingsRegistrySnapshot: AgentRegistrySnapshot? = null
    private var settingsLoadRevision: Long = 0L
    private var providerPageAgentId: String? = null
    private var providerPageAdapter: AgentConfigAdapter? = null
    private var providerPageSnapshot: AgentLiveConfigSnapshot? = null
    private var skillPageAgentId: String? = null
    private var skillPageAdapter: AgentConfigAdapter? = null
    private var skillPageSnapshot: AgentLiveConfigSnapshot? = null
    private var skillPageListAdapter: AgentSkillListAdapter? = null
    private var skillPageListView: RecyclerView? = null
    private var skillPageStatusText: TextView? = null
    private var mcpPageAgentId: String? = null
    private var mcpPageAdapter: AgentConfigAdapter? = null
    private var mcpPageSnapshot: AgentLiveConfigSnapshot? = null
    private var mcpPageListAdapter: AgentMcpListAdapter? = null
    private var mcpPageListView: RecyclerView? = null
    private var mcpPageStatusText: TextView? = null
    private val mcpConnectionStates = linkedMapOf<String, AgentMcpConnectionState>()
    private val mcpConnectionMessages = linkedMapOf<String, String>()
    private var mcpEditorStatusText: TextView? = null
    private var mcpEditorSaveAction: TextView? = null
    private var coreDocumentPageAgentId: String? = null
    private var coreDocumentPageAdapter: AgentConfigAdapter? = null
    private var coreDocumentWorkspacePath: String? = null
    private var coreDocumentDescriptors: List<AgentCoreDocumentDescriptor> = emptyList()
    private var coreDocumentListHost: LinearLayout? = null
    private var coreDocumentLoadRevision: Long = 0L
    private var coreDocumentEditorSnapshot: AgentCoreDocumentSnapshot? = null
    private var coreDocumentEditorInput: EditText? = null
    private var draftModelAgentId: String? = null
    private var draftModelSnapshot: AgentLiveConfigSnapshot? = null
    private var draftModelLoadRevision: Long = 0L
    private var draftModelLoadJob: Job? = null
    private var providerEditorStatusText: TextView? = null
    private var providerEditorSaveAction: TextView? = null
    private val providerListCardBindings = linkedMapOf<String, ProviderListCardBinding>()
    private var pendingSessionConfigId: String? = null
    private var sessionConfigurationRoute = SessionConfigurationRoute.Overview
    private var sessionConfigurationModelGroupId: String? = null
    private var composerExtensionRoute = ComposerExtensionRoute.Main
    private var drawerSessions: List<AgentSessionSummary> = emptyList()
    private var drawerSessionsKey: AgentSessionListKey? = null
    private var drawerLoadRevision: Long = 0L
    private val expandedProjectCwds = linkedSetOf<String>()
    private var drawerExpansionSeeded = false
    private var initialEntryDraftPrepared = false
    private var draftPreparationPending = false
    private var projectEditorDialog: AgentProjectEditorDialog? = null
    private var workspaceDirectoryPickerDialog: WorkspaceDirectoryPickerDialog? = null
    private var skillDirectoryPickerDialog: WorkspaceDirectoryPickerDialog? = null
    private var toolbarVisible = true
    private var permissionSignature: String? = null
    private val pendingAttachments = mutableListOf<PendingAttachment>()

    private val mainContent: View = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(agentPageBackground)
        addView(buildTopBar(context), LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ui.dp(66)
        ))
        addView(historyStatusText.apply {
            visibility = View.GONE
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(tokens.textSecondary)
            setPadding(ui.dp(18), ui.dp(4), ui.dp(18), ui.dp(6))
        }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        addView(list.apply {
            layoutManager = LinearLayoutManager(context).apply { stackFromEnd = true }
            adapter = this@RunAgentSurfaceBinding.adapter
            clipToPadding = false
            setPadding(0, ui.dp(6), 0, ui.dp(8))
            itemAnimator = null
            overScrollMode = View.OVER_SCROLL_NEVER
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    if (dy >= 0) return
                    val manager = recyclerView.layoutManager as? LinearLayoutManager ?: return
                    if (manager.findFirstVisibleItemPosition() > 2) return
                    observedKey?.let(AgentConversationStore::revealEarlier)
                }
            })
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        addView(permissionHost.apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(ui.dp(14), ui.dp(8), ui.dp(14), 0)
        })
        addView(buildComposerArea(context), LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(ui.dp(28), ui.dp(8), ui.dp(28), ui.dp(12)) })
    }

    override val root: View = FrameLayout(context).apply {
        setBackgroundColor(agentPageBackground)
        addView(mainContent, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))
        addView(navigationHost.apply { visibility = View.GONE }, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))
        addView(composerExtensionOverlay, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))
        addView(commandPaletteOverlay, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))
        addView(sessionConfigurationOverlay, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))
    }

    override fun render(state: RunSurfaceUiState) {
        val content = state.content as? RunSurfaceContent.Agent ?: return
        instanceId = state.target.instanceId
        generation = state.createdAt
        agentId = content.agentId
        agentDisplayName = state.title.ifBlank { content.agentId ?: "Agent" }
        agentTitleText.text = agentDisplayName
        providerId = content.providerId
        sessionId = content.sessionId
        statusText.text = content.statusMessage
            ?: content.connectionStatus?.let(::connectionStatusLabel)
            ?: state.statusLabel
        if (prepareInitialEntryDraftIfNeeded()) return
        subscribe(content.providerId, content.sessionId)
        if (content.sessionId == null) loadDraftModelCatalog()
        updateComposer()
    }

    override fun setSurfaceToolbarVisible(visible: Boolean): Boolean {
        toolbarVisible = visible
        topBar.visibility = if (visible) View.VISIBLE else View.GONE
        return true
    }

    override fun toggleSurfaceToolbar(): Boolean = setSurfaceToolbarVisible(!toolbarVisible)

    override fun dispose() {
        closeCommandPalette(animate = false)
        closeComposerExtensionMenu(animate = false)
        closeSessionConfigurationPanel(animate = false)
        observation?.cancel()
        observation = null
        navigationJob?.cancel()
        navigationJob = null
        draftModelLoadJob?.cancel()
        draftModelLoadJob = null
        projectEditorDialog?.dismiss()
        projectEditorDialog = null
        workspaceDirectoryPickerDialog?.dismiss()
        workspaceDirectoryPickerDialog = null
        skillDirectoryPickerDialog?.dismiss()
        skillDirectoryPickerDialog = null
        observedKey = null
    }

    override fun handleBack(): Boolean {
        if (composerExtensionOverlay.visibility == View.VISIBLE) {
            closeComposerExtensionMenu()
            return true
        }
        if (commandPaletteOverlay.visibility == View.VISIBLE) {
            closeCommandPalette()
            return true
        }
        if (sessionConfigurationOverlay.visibility == View.VISIBLE) {
            closeSessionConfigurationPanel()
            return true
        }
        return when (navigationScreen) {
        AgentNavigationScreen.ProviderEditor -> {
            showCurrentProviderList()
            true
        }
        AgentNavigationScreen.ProviderPresetPicker,
        AgentNavigationScreen.ProviderModelEditor -> {
            closeProviderEditorOverlay()
            true
        }
        AgentNavigationScreen.ProviderList -> {
            returnToAgentSettings()
            true
        }
        AgentNavigationScreen.DefaultPermission -> {
            returnToAgentSettings()
            true
        }
        AgentNavigationScreen.SkillList -> {
            returnToAgentSettings()
            true
        }
        AgentNavigationScreen.McpEditor -> {
            showCurrentMcpList()
            true
        }
        AgentNavigationScreen.McpList -> {
            returnToAgentSettings()
            true
        }
        AgentNavigationScreen.CoreDocumentEditor -> {
            returnFromCoreDocumentEditor()
            true
        }
        AgentNavigationScreen.CoreDocumentList -> {
            returnToAgentSettings()
            true
        }
        AgentNavigationScreen.ArchivedContent -> {
            returnToAgentSettings()
            true
        }
        AgentNavigationScreen.SessionSearch -> {
            showSessionDrawer()
            true
        }
        AgentNavigationScreen.Settings -> {
            if (settingsReturnsToDrawer) showSessionDrawer() else closeNavigation()
            true
        }
        AgentNavigationScreen.Drawer -> {
            closeNavigation()
            true
        }
        AgentNavigationScreen.Main -> false
        }
    }

    internal fun showSessionDrawerForTesting() = showSessionDrawer()

    internal fun showSessionSearchForTesting() = showSessionSearch()

    internal fun showSettingsForTesting(returnToDrawer: Boolean) = showAgentSettings(returnToDrawer)

    internal fun navigationScreenForTesting(): String = navigationScreen.name

    fun addAttachments(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val capabilities = AgentRuntimeRegistry.session(instanceId)?.capabilities?.prompt
        if (capabilities == null) {
            Toast.makeText(context, "Agent 会话尚未连接", Toast.LENGTH_SHORT).show()
            return
        }
        lifecycleOwner.lifecycleScope.launch {
            val results = withContext(Dispatchers.IO) {
                uris.map { uri -> runCatching { loadAttachment(uri, capabilities) } }
            }
            results.mapNotNull(Result<PendingAttachment>::exceptionOrNull).forEach { error ->
                Toast.makeText(
                    context,
                    "无法添加文件：${error.message ?: error.javaClass.simpleName}",
                    Toast.LENGTH_LONG
                ).show()
            }
            val loaded = results.mapNotNull(Result<PendingAttachment>::getOrNull)
            if (loaded.isNotEmpty()) {
                pendingAttachments += loaded
                renderAttachments()
                updateComposer()
            }
        }
    }

    private fun loadAttachment(
        uri: Uri,
        capabilities: com.kite.app.agent.contract.AgentPromptCapabilities
    ): PendingAttachment {
        val resolver = context.contentResolver
        val mimeType = resolver.getType(uri).orEmpty().ifBlank { "application/octet-stream" }
        val displayName = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
            ?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                val name = nameIndex.takeIf { it >= 0 }?.let(cursor::getString)
                val size = sizeIndex.takeIf { it >= 0 && !cursor.isNull(it) }?.let(cursor::getLong)
                name to size
            }
        val name = displayName?.first?.takeIf(String::isNotBlank) ?: "attachment-${System.currentTimeMillis()}"
        val size = displayName?.second
        if (mimeType.startsWith("image/") && capabilities.images) {
            if (size != null && size > MAX_INLINE_IMAGE_BYTES) error("图片超过 12 MB")
            val bytes = resolver.openInputStream(uri)?.use { it.readLimited(MAX_INLINE_IMAGE_BYTES + 1) }
                ?: error("无法读取图片")
            if (bytes.size > MAX_INLINE_IMAGE_BYTES) error("图片超过 12 MB")
            return PendingAttachment(
                name = name,
                content = AgentContent.Image(
                    data = Base64.encodeToString(bytes, Base64.NO_WRAP),
                    mimeType = mimeType,
                    uri = uri.toString()
                )
            )
        }
        if (capabilities.resourceLinks) {
            val directPath = AndroidDocumentPathResolver.resolveAgentVisiblePath(context, uri)
            val safeName = name.replace(Regex("[\\\\/:*?\"<>|]+"), "_").takeLast(120).ifBlank { "attachment" }
            val (containerPath, resourceSize) = if (directPath != null) {
                directPath to (File(directPath).takeIf(File::isFile)?.length() ?: size)
            } else {
                val workspaceRoot = KFContainerManager.resolveWorkspaceDirectory(context)
                val attachmentScope = listOf(agentId.orEmpty(), instanceId.ifBlank { "draft" })
                    .joinToString("-") { it.replace(Regex("[^a-zA-Z0-9._-]+"), "-") }
                    .take(96)
                    .ifBlank { "draft" }
                val relativeDir = "${KiteStorageContract.WORKSPACE_CONTROL_DIR_NAME}/attachments/$attachmentScope"
                val deliveryDir = File(workspaceRoot, relativeDir).apply {
                    if (!exists() && !mkdirs()) error("无法准备会话附件目录")
                }
                val target = uniqueAttachmentTarget(deliveryDir, safeName)
                resolver.openInputStream(uri)?.use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                } ?: error("无法读取文件")
                "${KiteStorageContract.CONTAINER_WORKSPACE_ROOT}/$relativeDir/${target.name}" to target.length()
            }
            val containerUri = Uri.Builder()
                .scheme("file")
                .path(containerPath)
                .build()
                .toString()
            return PendingAttachment(
                name = safeName,
                content = AgentContent.ResourceLink(
                    name = safeName,
                    uri = containerUri,
                    mimeType = mimeType,
                    size = resourceSize
                )
            )
        }
        if (mimeType.startsWith("text/") && capabilities.embeddedResources) {
            val text = resolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                ?: error("无法读取文本文件")
            return PendingAttachment(
                name = name,
                content = AgentContent.EmbeddedText(text, uri.toString(), mimeType)
            )
        }
        error("当前 Agent 不支持这种文件")
    }

    private fun uniqueAttachmentTarget(directory: File, name: String): File {
        val stem = name.substringBeforeLast('.', name)
        val suffix = name.substringAfterLast('.', "").takeIf(String::isNotBlank)?.let { ".$it" }.orEmpty()
        var target = File(directory, name)
        var index = 2
        while (target.exists()) {
            target = File(directory, "$stem-$index$suffix")
            index++
        }
        return target
    }

    private fun java.io.InputStream.readLimited(limit: Int): ByteArray {
        val output = java.io.ByteArrayOutputStream(minOf(limit, COPY_BUFFER_SIZE))
        val buffer = ByteArray(COPY_BUFFER_SIZE)
        var remaining = limit
        while (remaining > 0) {
            val count = read(buffer, 0, minOf(buffer.size, remaining))
            if (count < 0) break
            output.write(buffer, 0, count)
            remaining -= count
        }
        return output.toByteArray()
    }

    private fun renderAttachments() {
        attachmentHost.removeAllViews()
        attachmentHost.visibility = if (pendingAttachments.isEmpty()) View.GONE else View.VISIBLE
        pendingAttachments.forEach { attachment ->
            attachmentHost.addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = ui.roundedBox(tokens.surface, tokens.border, ui.dp(18).toFloat(), ui.dp(1))
                addView(TextView(context).apply {
                    text = attachment.name
                    textSize = 12f
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                    setTextColor(tokens.textPrimary)
                    setPadding(ui.dp(10), 0, ui.dp(2), 0)
                }, LinearLayout.LayoutParams(0, ui.dp(36), 1f))
                addView(iconButton(context, R.drawable.ic_close_light, "移除 ${attachment.name}") {
                    pendingAttachments.remove(attachment)
                    renderAttachments()
                    updateComposer()
                }, LinearLayout.LayoutParams(ui.dp(36), ui.dp(36)))
            }, LinearLayout.LayoutParams(0, ui.dp(36), 1f).apply {
                setMargins(ui.dp(2), 0, ui.dp(2), 0)
            })
        }
    }

    private fun buildTopBar(context: Context): View = topBar.apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(ui.dp(12), ui.dp(8), ui.dp(12), ui.dp(6))
        addView(iconButton(context, R.drawable.ic_material_menu, "会话列表") {
            showSessionDrawer()
        }.apply {
            background = InsetDrawable(
                ui.roundedBox(agentSurface, agentBorder, ui.dp(21).toFloat(), ui.dp(1)),
                ui.dp(4)
            )
            elevation = ui.dp(1).toFloat()
        }, LinearLayout.LayoutParams(ui.dp(50), ui.dp(50)))
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            addView(agentTitleText.apply {
                text = agentDisplayName
                textSize = 16.5f
                typeface = Typeface.DEFAULT_BOLD
                includeFontPadding = false
                gravity = Gravity.CENTER
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                setTextColor(tokens.textPrimary)
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(statusText.apply {
                textSize = 11f
                includeFontPadding = false
                gravity = Gravity.CENTER
                maxLines = 1
                setTextColor(tokens.textSecondary)
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, ui.dp(2), 0, 0)
            })
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            setMargins(ui.dp(8), 0, ui.dp(8), 0)
        })
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = ui.roundedBox(agentSurface, agentBorder, ui.dp(25).toFloat(), ui.dp(1))
            elevation = ui.dp(1).toFloat()
            addView(iconButton(context, R.drawable.ic_compose_outline, "新建会话") { createNewSession() },
                LinearLayout.LayoutParams(ui.dp(48), ui.dp(48)))
            addView(iconButtonWithAnchor(context, R.drawable.ic_more_vert_light, "更多操作") { anchor ->
                showMoreMenu(anchor)
            }, LinearLayout.LayoutParams(ui.dp(48), ui.dp(48)))
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ui.dp(50)))
    }

    private fun buildComposerArea(context: Context): View = composerArea.apply {
        orientation = LinearLayout.VERTICAL
        addView(attachmentHost.apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            visibility = View.GONE
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            setMargins(ui.dp(8), 0, ui.dp(8), ui.dp(6))
        })
        addView(buildComposer(context), LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))
    }

    private fun buildComposer(context: Context): View = composer.apply {
        orientation = LinearLayout.VERTICAL
        minimumHeight = ui.dp(96)
        setPadding(ui.dp(7), ui.dp(6), ui.dp(7), ui.dp(6))
        background = ui.roundedBox(agentInputBackground, agentBorder, ui.dp(27).toFloat(), ui.dp(1))
        elevation = ui.dp(2).toFloat()
        addView(input.apply {
            hint = "给 Agent 发消息"
            textSize = 16f
            includeFontPadding = false
            minHeight = ui.dp(42)
            maxLines = 6
            setTextColor(tokens.textPrimary)
            setHintTextColor(tokens.textTertiary)
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            setPadding(ui.dp(12), ui.dp(8), ui.dp(12), ui.dp(4))
            imeOptions = EditorInfo.IME_ACTION_SEND
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_SEND) {
                    submitOrCancel()
                    true
                } else {
                    false
                }
            }
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    updateComposer()
                    syncCommandPalette(s?.toString().orEmpty())
                }
                override fun afterTextChanged(s: Editable?) = Unit
            })
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(iconButtonWithAnchor(context, R.drawable.ic_add_light, "扩展与工作模式") {
                showComposerExtensionMenu()
            }, LinearLayout.LayoutParams(ui.dp(48), ui.dp(48)))
            addView(sessionConfigurationHost, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ui.dp(40)
            ))
            addView(sessionPermissionHost, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ui.dp(40)
            ).apply {
                marginStart = ui.dp(5)
            })
            addView(View(context), LinearLayout.LayoutParams(0, ui.dp(1), 1f))
            addView(actionButton.apply {
                contentDescription = "发送"
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                setPadding(ui.dp(12), ui.dp(12), ui.dp(12), ui.dp(12))
                setOnClickListener { submitOrCancel() }
            }, LinearLayout.LayoutParams(ui.dp(48), ui.dp(48)))
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    private fun iconButton(
        context: Context,
        icon: Int,
        description: String,
        action: () -> Unit
    ): ImageButton = iconButtonWithAnchor(context, icon, description) { action() }

    private fun iconButtonWithAnchor(
        context: Context,
        icon: Int,
        description: String,
        action: (View) -> Unit
    ): ImageButton = ImageButton(context).apply {
        setImageResource(icon)
        imageTintList = ColorStateList.valueOf(tokens.textPrimary)
        scaleType = ImageView.ScaleType.CENTER_INSIDE
        contentDescription = description
        setPadding(ui.dp(12), ui.dp(12), ui.dp(12), ui.dp(12))
        background = ui.roundedBox(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT, ui.dp(24).toFloat())
        setOnClickListener(action)
    }

    private fun subscribe(nextProviderId: String?, nextSessionId: String?) {
        val key = if (!nextProviderId.isNullOrBlank() && !nextSessionId.isNullOrBlank()) {
            AgentConversationKey(nextProviderId, nextSessionId)
        } else {
            null
        }
        if (key == observedKey) return
        observation?.cancel()
        observation = null
        observedKey = key
        currentSnapshot = null
        adapter.submitList(emptyList())
        historyStatusText.visibility = View.GONE
        renderPermission(null)
        renderSessionConfigurationControls()
        if (key == null) return
        observation = lifecycleOwner.lifecycleScope.launch {
            lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                AgentConversationStore.observe(key).collect { snapshot ->
                    if (snapshot != null) renderConversation(snapshot)
                }
            }
        }
    }

    private fun renderConversation(snapshot: AgentConversationSnapshot) {
        val nearBottom = !list.canScrollVertically(1)
        val configurationChanged = currentSnapshot?.configuration != snapshot.configuration
        currentSnapshot = snapshot
        statusText.text = snapshot.lastError
            ?: phaseLabel(snapshot.phase)
        adapter.submitConversation(snapshot.timeline, snapshot.turns) {
            if (nearBottom && adapter.itemCount > 0) list.scrollToPosition(adapter.itemCount - 1)
        }
        renderHistoryStatus(snapshot)
        renderPermission(snapshot)
        updateComposer()
        if (configurationChanged) renderSessionConfigurationControls()
        if (composerExtensionOverlay.visibility == View.VISIBLE) {
            rebuildComposerExtensionMenu(animateContent = false)
        }
        if (commandPaletteOverlay.visibility == View.VISIBLE) {
            syncCommandPalette(input.text?.toString().orEmpty())
        }
    }

    private fun renderHistoryStatus(snapshot: AgentConversationSnapshot) {
        val message = when (snapshot.history.status) {
            AgentConversationHistoryStatus.Loading -> "正在恢复历史记录…"
            AgentConversationHistoryStatus.Unavailable -> "此 Agent 未提供历史回放，仅显示本次连接后的消息"
            AgentConversationHistoryStatus.Live,
            AgentConversationHistoryStatus.Loaded -> snapshot.history.truncatedItems
                .takeIf { it > 0 }
                ?.let { "为保持流畅，最早 $it 项未保留" }
        }
        historyStatusText.text = message.orEmpty()
        historyStatusText.visibility = if (message == null) View.GONE else View.VISIBLE
    }

    private fun renderSessionConfigurationControls() {
        renderSessionPermissionControl()
        sessionConfigurationHost.removeAllViews()
        val options = sessionConfigurationOptions()
        val pending = pendingSessionConfigId != null
        sessionConfigurationHost.visibility = View.VISIBLE
        sessionConfigurationHost.addView(
            fixedComposerEntry(
                label = AgentSurfaceNavigationPolicy.composerModelLabel(options),
                contentDescription = if (options.isEmpty()) {
                    "模型，当前尚无可选项"
                } else {
                    "选择模型，当前${AgentSurfaceNavigationPolicy.composerModelLabel(options)}"
                },
                pending = pending,
                maximumWidth = ui.dp(150),
                onClick = ::showSessionConfigurationPanel
            ),
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ui.dp(36))
        )
        if (sessionConfigurationOverlay.visibility == View.VISIBLE) {
            rebuildSessionConfigurationPanel(animateContent = false)
        }
    }

    private fun renderSessionPermissionControl() {
        sessionPermissionHost.removeAllViews()
        val option = composerPermissionOption()
        val pending = pendingSessionConfigId != null
        sessionPermissionHost.visibility = View.VISIBLE
        sessionPermissionHost.addView(
            fixedComposerEntry(
                label = AgentSurfaceNavigationPolicy.composerPermissionLabel(option),
                contentDescription = if (option == null) "权限，当前尚无可选策略" else "选择权限策略",
                pending = pending,
                maximumWidth = ui.dp(132),
                onClick = { showComposerExtensionMenu(ComposerExtensionRoute.Permissions) }
            ),
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ui.dp(36))
        )
    }

    private fun fixedComposerEntry(
        label: String,
        contentDescription: String,
        pending: Boolean,
        maximumWidth: Int,
        onClick: () -> Unit
    ): TextView = TextView(context).apply {
        text = label
        textSize = 13f
        typeface = Typeface.DEFAULT_BOLD
        minWidth = ui.dp(58)
        maxWidth = maximumWidth
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
        gravity = Gravity.CENTER
        setTextColor(tokens.textPrimary)
        setPadding(ui.dp(13), 0, ui.dp(13), 0)
        background = ui.roundedBox(
            agentSettingsSurface,
            android.graphics.Color.TRANSPARENT,
            ui.dp(18).toFloat()
        )
        this.contentDescription = contentDescription
        isClickable = !pending
        isFocusable = true
        alpha = if (pending) 0.55f else 1f
        setOnClickListener { onClick() }
    }

    private fun renderPermission(snapshot: AgentConversationSnapshot?) {
        val request = snapshot?.pendingPermission
        val nextSignature = request?.let { pending ->
            buildString {
                append(pending.toolCall.id)
                append(':').append(pending.toolCall.title.orEmpty())
                pending.options.forEach { option ->
                    append(':').append(option.id).append('=').append(option.name)
                }
            }
        }
        if (nextSignature == permissionSignature) return
        permissionSignature = nextSignature
        if (request == null) {
            permissionHost.visibility = View.GONE
            permissionHost.removeAllViews()
            return
        }
        permissionHost.visibility = View.VISIBLE
        permissionHost.removeAllViews()
        val presentation = AgentPermissionPresentationPolicy.present(request)
        permissionHost.background = ui.roundedBox(tokens.warningSoft, tokens.warningBorder, ui.dp(22).toFloat(), ui.dp(1))
        permissionHost.addView(TextView(context).apply {
            text = "权限请求"
            textSize = 11.5f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(tokens.warning)
            setPadding(ui.dp(14), ui.dp(13), ui.dp(14), ui.dp(3))
        })
        permissionHost.addView(TextView(context).apply {
            text = presentation.title
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(tokens.textPrimary)
            setPadding(ui.dp(14), 0, ui.dp(14), ui.dp(3))
        })
        presentation.details.takeIf(List<String>::isNotEmpty)?.let { details ->
            permissionHost.addView(TextView(context).apply {
                text = details.joinToString("\n")
                textSize = 12.5f
                maxLines = 5
                ellipsize = TextUtils.TruncateAt.END
                setLineSpacing(0f, 1.16f)
                setTextColor(tokens.textSecondary)
                setTextIsSelectable(true)
                setPadding(ui.dp(14), ui.dp(2), ui.dp(14), ui.dp(7))
            })
        }
        permissionHost.addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ui.dp(8), ui.dp(2), ui.dp(8), ui.dp(9))
            presentation.options.forEach { option ->
                addView(LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    minimumHeight = ui.dp(52)
                    setPadding(ui.dp(13), ui.dp(6), ui.dp(7), ui.dp(6))
                    background = ui.roundedBox(
                        if (option.allow) tokens.primarySubtle else tokens.surface,
                        if (option.allow) android.graphics.Color.TRANSPARENT else tokens.border,
                        ui.dp(17).toFloat(),
                        if (option.allow) 0 else ui.dp(1)
                    )
                    addView(LinearLayout(context).apply {
                        orientation = LinearLayout.VERTICAL
                        addView(TextView(context).apply {
                            text = option.name
                            textSize = 14f
                            typeface = Typeface.DEFAULT_BOLD
                            maxLines = 1
                            ellipsize = TextUtils.TruncateAt.END
                            setTextColor(if (option.allow) tokens.primaryStrong else tokens.danger)
                        })
                        addView(TextView(context).apply {
                            text = option.scopeHint
                            textSize = 11.5f
                            maxLines = 1
                            setTextColor(tokens.textSecondary)
                            setPadding(0, ui.dp(2), 0, 0)
                        })
                    }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                    addView(ImageView(context).apply {
                        setImageResource(R.drawable.ic_chevron_right_light)
                        imageTintList = ColorStateList.valueOf(tokens.textTertiary)
                        setPadding(ui.dp(9), ui.dp(9), ui.dp(9), ui.dp(9))
                    }, LinearLayout.LayoutParams(ui.dp(36), ui.dp(36)))
                    contentDescription = "${option.name}，${option.scopeHint}"
                    isClickable = true
                    isFocusable = true
                    setOnClickListener {
                        AgentRuntimeRegistry.resolvePermission(
                            instanceId,
                            generation,
                            AgentPermissionOutcome.Selected(option.id)
                        )
                    }
                }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    setMargins(ui.dp(2), ui.dp(2), ui.dp(2), ui.dp(2))
                })
            }
        })
    }

    private fun submitOrCancel() {
        val phase = composerPhase()
        if (phase == AgentSessionPhase.Prompting || phase == AgentSessionPhase.Cancelling) {
            lifecycleOwner.lifecycleScope.launch {
                showOperationResult(AgentRuntimeRegistry.cancel(instanceId, generation), "已请求停止生成")
            }
            return
        }
        val text = input.text?.toString()?.trim().orEmpty()
        if ((text.isBlank() && pendingAttachments.isEmpty()) || phase != AgentSessionPhase.Ready) return
        val attachments = pendingAttachments.toList()
        val content = buildList {
            if (text.isNotBlank()) add(AgentContent.Text(text))
            addAll(attachments.map(PendingAttachment::content))
        }
        input.setText("")
        pendingAttachments.clear()
        renderAttachments()
        lifecycleOwner.lifecycleScope.launch {
            val result = AgentRuntimeRegistry.prompt(instanceId, generation, content)
            if (result !is AgentOperationResult.Success) {
                restoreComposerAfterFailure(text, attachments)
            }
            showOperationResult(result, null)
        }
    }

    private fun updateComposer() {
        val phase = composerPhase()
        val cancelling = phase == AgentSessionPhase.Prompting || phase == AgentSessionPhase.Cancelling
        val canSend = phase == AgentSessionPhase.Ready &&
            (!input.text.isNullOrBlank() || pendingAttachments.isNotEmpty())
        input.isEnabled = phase == AgentSessionPhase.Ready
        actionButton.isEnabled = cancelling || canSend
        actionButton.setImageResource(if (cancelling) R.drawable.ic_terminal_interrupt else R.drawable.ic_send_light)
        actionButton.imageTintList = ColorStateList.valueOf(tokens.buttonText)
        actionButton.background = ui.roundedBox(
            if (actionButton.isEnabled) tokens.primaryStrong else tokens.borderStrong,
            android.graphics.Color.TRANSPARENT,
            ui.dp(24).toFloat()
        )
        actionButton.contentDescription = if (cancelling) "停止生成" else "发送"
    }

    private fun createNewSession(cwd: String? = null) {
        if (instanceId.isBlank() || generation <= 0L) return
        if (draftPreparationPending) return
        val previousSessionId = sessionId
        val previousText = input.text?.toString().orEmpty()
        val previousAttachments = pendingAttachments.toList()
        draftPreparationPending = true
        enterDraftUi()
        statusText.text = "正在准备新会话…"
        updateComposer()
        lifecycleOwner.lifecycleScope.launch {
            when (val result = AgentRuntimeRegistry.prepareNewSession(instanceId, generation, cwd)) {
                is AgentOperationResult.Success -> {
                    draftPreparationPending = false
                    statusText.text = "可以开始新会话"
                    loadDraftModelCatalog(force = true)
                    input.requestFocus()
                    updateComposer()
                }
                is AgentOperationResult.Unsupported -> {
                    draftPreparationPending = false
                    restoreSessionUi(previousSessionId, previousText, previousAttachments)
                    Toast.makeText(root.context, "当前 Agent 不支持新建会话", Toast.LENGTH_SHORT).show()
                }
                is AgentOperationResult.Failure -> {
                    draftPreparationPending = false
                    restoreSessionUi(previousSessionId, previousText, previousAttachments)
                    Toast.makeText(root.context, result.message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    /** 新的显示绑定只打开 Agent，不替用户选择上次会话。 */
    private fun prepareInitialEntryDraftIfNeeded(): Boolean {
        if (initialEntryDraftPrepared || instanceId.isBlank() || generation <= 0L) return false
        val runtime = AgentRuntimeRegistry.session(instanceId)
            ?.takeIf { it.generation == generation }
            ?: return false
        val defaultCwd = AgentRuntimeRegistry.defaultCwd(instanceId, generation) ?: runtime.cwd
        initialEntryDraftPrepared = true
        if (runtime.isDraft && AgentSurfaceNavigationPolicy.sameCwd(runtime.cwd, defaultCwd)) return false
        draftPreparationPending = true
        enterDraftUi()
        statusText.text = "正在准备新会话…"
        updateComposer()
        lifecycleOwner.lifecycleScope.launch {
            when (val result = AgentRuntimeRegistry.prepareNewSession(instanceId, generation, defaultCwd)) {
                is AgentOperationResult.Success -> {
                    draftPreparationPending = false
                    statusText.text = "可以开始新会话"
                    loadDraftModelCatalog(force = true)
                    updateComposer()
                }
                is AgentOperationResult.Unsupported -> {
                    draftPreparationPending = false
                    statusText.text = "当前 Agent 暂不能进入新会话"
                    updateComposer()
                }
                is AgentOperationResult.Failure -> {
                    draftPreparationPending = false
                    statusText.text = result.message
                    updateComposer()
                }
            }
        }
        return true
    }

    private fun composerPhase(): AgentSessionPhase? = if (draftPreparationPending) {
        AgentSessionPhase.Preparing
    } else currentSnapshot?.phase
        ?: AgentRuntimeRegistry.session(instanceId)
            ?.takeIf { it.isDraft }
            ?.let { AgentSessionPhase.Ready }

    private fun enterDraftUi() {
        sessionId = null
        draftModelLoadRevision++
        draftModelLoadJob?.cancel()
        draftModelLoadJob = null
        draftModelSnapshot = null
        draftModelAgentId = agentId
        subscribe(providerId, null)
        input.setText("")
        pendingAttachments.clear()
        renderAttachments()
        statusText.text = "可以开始新会话"
        updateComposer()
    }

    private fun loadDraftModelCatalog(force: Boolean = false) {
        val targetAgentId = agentId?.takeIf(String::isNotBlank) ?: return
        val runtime = AgentRuntimeRegistry.session(instanceId)
            ?.takeIf { it.generation == generation && it.isDraft }
            ?: return
        if (
            !force &&
            draftModelAgentId == targetAgentId &&
            (draftModelSnapshot != null || draftModelLoadJob?.isActive == true)
        ) {
            if (draftModelSnapshot != null) renderSessionConfigurationControls()
            return
        }
        val requestRevision = ++draftModelLoadRevision
        draftModelAgentId = targetAgentId
        draftModelLoadJob?.cancel()
        draftModelLoadJob = lifecycleOwner.lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                val registration = agentRegistry.snapshot().entry(targetAgentId)?.registration
                registration
                    ?.let(agentConfigAdapters::adapterFor)
                    ?.readLive(targetAgentId)
            }
            if (
                requestRevision != draftModelLoadRevision ||
                agentId != targetAgentId ||
                AgentRuntimeRegistry.session(instanceId)?.takeIf {
                    it.generation == generation && it.isDraft
                } == null
            ) return@launch
            val snapshot = (result as? AgentConfigReadResult.Ready)?.snapshot
            draftModelSnapshot = snapshot
            if (snapshot != null) {
                val current = AgentRuntimeRegistry.draftModelSelection(instanceId, generation)
                val next = current?.takeIf { AgentDraftModelPolicy.contains(snapshot, it) }
                    ?: AgentDraftModelPolicy.defaultSelection(snapshot)
                next?.let { AgentRuntimeRegistry.selectDraftModel(instanceId, generation, it) }
            }
            renderSessionConfigurationControls()
        }
    }

    private fun usePersistentSnapshotAsDraftDefault(targetAgentId: String, snapshot: AgentLiveConfigSnapshot) {
        if (targetAgentId != agentId) return
        val runtime = AgentRuntimeRegistry.session(instanceId)
            ?.takeIf { it.generation == generation && it.isDraft }
            ?: return
        draftModelAgentId = targetAgentId
        draftModelSnapshot = snapshot
        AgentDraftModelPolicy.defaultSelection(snapshot)?.let {
            AgentRuntimeRegistry.selectDraftModel(runtime.instanceId, runtime.generation, it)
        }
        renderSessionConfigurationControls()
    }

    private fun restoreSessionUi(
        previousSessionId: String?,
        previousText: String,
        previousAttachments: List<PendingAttachment>
    ) {
        sessionId = previousSessionId
        subscribe(providerId, previousSessionId)
        input.setText(previousText)
        input.setSelection(input.text?.length ?: 0)
        pendingAttachments.clear()
        pendingAttachments += previousAttachments
        renderAttachments()
        updateComposer()
    }

    private fun restoreComposerAfterFailure(text: String, attachments: List<PendingAttachment>) {
        if (text.isNotBlank()) {
            val current = input.text?.toString().orEmpty()
            when {
                current.isBlank() -> input.setText(text)
                current != text -> input.setText("$text\n$current")
            }
            input.setSelection(input.text?.length ?: 0)
        }
        if (attachments.isNotEmpty()) {
            pendingAttachments.addAll(0, attachments.filterNot { it in pendingAttachments })
            renderAttachments()
        }
        updateComposer()
    }

    private fun showSessionDrawer() {
        closeCommandPalette(animate = false)
        closeComposerExtensionMenu(animate = false)
        closeSessionConfigurationPanel(animate = false)
        navigationJob?.cancel()
        navigationScreen = AgentNavigationScreen.Drawer
        navigationHost.removeAllViews()
        navigationHost.addView(sessionDrawerView, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))
        navigationHost.visibility = View.VISIBLE
        loadDrawerSessions()
    }

    private fun closeNavigation() {
        navigationJob?.cancel()
        navigationJob = null
        navigationScreen = AgentNavigationScreen.Main
        navigationHost.visibility = View.GONE
        navigationHost.removeAllViews()
    }

    private fun buildSessionDrawer(): View = FrameLayout(context).apply {
        setBackgroundColor(android.graphics.Color.TRANSPARENT)
        addView(View(context).apply {
            setBackgroundColor(if (isDark) 0x99000000.toInt() else 0x66000000)
            isClickable = true
            contentDescription = "关闭会话抽屉"
            setOnClickListener { closeNavigation() }
        }, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))
        val panelWidth = minOf(
            (context.resources.displayMetrics.widthPixels * 0.86f).toInt(),
            ui.dp(344)
        )
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(agentPageBackground)
            setPadding(ui.dp(20), ui.dp(18), ui.dp(16), ui.dp(16))
            elevation = ui.dp(12).toFloat()

            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(TextView(context).apply {
                        text = agentDisplayName
                        textSize = 24f
                        typeface = Typeface.DEFAULT_BOLD
                        setTextColor(tokens.textPrimary)
                        includeFontPadding = false
                    })
                    addView(TextView(context).apply {
                        text = agentId.orEmpty().ifBlank { "当前 Agent" }
                        textSize = 12f
                        setTextColor(tokens.textSecondary)
                        includeFontPadding = false
                        setPadding(0, ui.dp(5), 0, 0)
                    })
                }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                addView(iconButton(context, R.drawable.ic_material_search, "搜索会话", ::showSessionSearch).apply {
                    background = ui.roundedBox(agentSettingsSurface, android.graphics.Color.TRANSPARENT, ui.dp(24).toFloat())
                }, LinearLayout.LayoutParams(ui.dp(48), ui.dp(48)))
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                setMargins(ui.dp(4), ui.dp(8), 0, ui.dp(18))
            })

            addView(drawerStatusText.apply {
                textSize = 13f
                setTextColor(tokens.textSecondary)
                gravity = Gravity.CENTER
                setPadding(ui.dp(12), ui.dp(22), ui.dp(12), ui.dp(22))
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(drawerList.apply {
                layoutManager = LinearLayoutManager(context)
                adapter = drawerAdapter
                itemAnimator = null
                overScrollMode = View.OVER_SCROLL_NEVER
                visibility = View.GONE
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL or Gravity.END
                addView(iconButton(context, R.drawable.ic_material_settings, "Agent 设置") {
                    showAgentSettings(returnToDrawer = true)
                }.apply {
                    background = ui.roundedBox(agentSettingsSurface, android.graphics.Color.TRANSPARENT, ui.dp(24).toFloat())
                }, LinearLayout.LayoutParams(ui.dp(48), ui.dp(48)))
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(48)).apply {
                setMargins(0, ui.dp(10), 0, 0)
            })
        }, FrameLayout.LayoutParams(panelWidth, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.START))
    }

    private fun showSessionSearch() {
        closeCommandPalette(animate = false)
        closeComposerExtensionMenu(animate = false)
        closeSessionConfigurationPanel(animate = false)
        navigationJob?.cancel()
        navigationScreen = AgentNavigationScreen.SessionSearch
        navigationHost.removeAllViews()
        navigationHost.addView(sessionSearchPageView, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))
        navigationHost.visibility = View.VISIBLE
        sessionSearchInput.setText("")
        loadDrawerSessions()
        sessionSearchInput.post {
            if (navigationScreen != AgentNavigationScreen.SessionSearch) return@post
            sessionSearchInput.requestFocus()
            (context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)
                ?.showSoftInput(sessionSearchInput, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun buildSessionSearchPage(): View = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(agentPageBackground)
        addView(buildAgentSubpageHeader(
            title = "搜索会话",
            backDescription = "返回会话列表",
            onBack = ::showSessionDrawer
        ), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(64)))
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = ui.roundedBox(agentSettingsSurface, android.graphics.Color.TRANSPARENT, ui.dp(24).toFloat())
            addView(ImageView(context).apply {
                setImageResource(R.drawable.ic_material_search)
                imageTintList = ColorStateList.valueOf(tokens.textSecondary)
                setPadding(ui.dp(12), ui.dp(12), ui.dp(8), ui.dp(12))
                contentDescription = "搜索会话"
            }, LinearLayout.LayoutParams(ui.dp(44), ui.dp(48)))
            addView(sessionSearchInput.apply {
                hint = "搜索当前 Agent 的会话"
                textSize = 15f
                maxLines = 1
                isSingleLine = true
                setTextColor(tokens.textPrimary)
                setHintTextColor(tokens.textTertiary)
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                setPadding(0, 0, ui.dp(14), 0)
                if (tag != SESSION_SEARCH_WATCHER_TAG) {
                    addTextChangedListener(object : TextWatcher {
                        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                            renderSessionSearchResults()
                        }
                        override fun afterTextChanged(s: Editable?) = Unit
                    })
                    tag = SESSION_SEARCH_WATCHER_TAG
                }
            }, LinearLayout.LayoutParams(0, ui.dp(48), 1f))
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(48)).apply {
            setMargins(ui.dp(20), ui.dp(8), ui.dp(20), ui.dp(12))
        })
        addView(sessionSearchStatusText.apply {
            textSize = 13f
            setTextColor(tokens.textSecondary)
            gravity = Gravity.CENTER
            setPadding(ui.dp(20), ui.dp(26), ui.dp(20), ui.dp(26))
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        addView(sessionSearchList.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = sessionSearchAdapter
            itemAnimator = null
            overScrollMode = View.OVER_SCROLL_NEVER
            visibility = View.GONE
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).apply {
            setMargins(ui.dp(16), 0, ui.dp(16), ui.dp(12))
        })
    }

    private fun loadDrawerSessions() {
        val requestKey = AgentSessionListKey(instanceId, generation, providerId)
        val hasCachedSessions = drawerSessionsKey == requestKey
        val requestRevision = ++drawerLoadRevision
        if (hasCachedSessions) {
            renderDrawerSessions()
            renderSessionSearchResults()
        } else {
            drawerSessions = emptyList()
            drawerAdapter.submitList(emptyList())
            sessionSearchAdapter.submitList(emptyList())
            drawerList.visibility = View.GONE
            sessionSearchList.visibility = View.GONE
            drawerStatusText.visibility = View.GONE
            sessionSearchStatusText.visibility = View.GONE
        }
        val runtime = AgentRuntimeRegistry.session(instanceId)
        if (runtime?.capabilities?.sessions?.list != true) {
            drawerSessionsKey = requestKey
            renderDrawerSessions()
            sessionSearchStatusText.text = "当前 Agent 未提供历史会话列表"
            sessionSearchStatusText.visibility = View.VISIBLE
            return
        }
        navigationJob = lifecycleOwner.lifecycleScope.launch {
            val loadingFeedback = if (hasCachedSessions) {
                null
            } else {
                launch {
                    delay(SESSION_LOADING_FEEDBACK_DELAY_MS)
                    if (requestRevision != drawerLoadRevision) return@launch
                    drawerStatusText.text = "正在读取当前 Agent 的会话…"
                    drawerStatusText.visibility = View.VISIBLE
                    sessionSearchStatusText.text = "正在读取当前 Agent 的会话…"
                    sessionSearchStatusText.visibility = View.VISIBLE
                }
            }
            try {
                when (val result = AgentRuntimeRegistry.listSessions(instanceId, generation)) {
                    is AgentOperationResult.Success -> {
                        if (requestRevision != drawerLoadRevision) return@launch
                        drawerSessionsKey = requestKey
                        drawerSessions = result.value.sessions
                        renderDrawerSessions()
                        renderSessionSearchResults()
                    }
                    is AgentOperationResult.Unsupported -> {
                        if (requestRevision != drawerLoadRevision || hasCachedSessions) return@launch
                        drawerStatusText.text = "当前 Agent 未提供历史会话列表"
                        drawerStatusText.visibility = View.VISIBLE
                        sessionSearchStatusText.text = "当前 Agent 未提供历史会话列表"
                        sessionSearchStatusText.visibility = View.VISIBLE
                    }
                    is AgentOperationResult.Failure -> {
                        if (requestRevision != drawerLoadRevision || hasCachedSessions) return@launch
                        drawerStatusText.text = result.message
                        drawerStatusText.visibility = View.VISIBLE
                        sessionSearchStatusText.text = result.message
                        sessionSearchStatusText.visibility = View.VISIBLE
                    }
                }
            } finally {
                loadingFeedback?.cancel()
            }
        }
    }

    private fun renderDrawerSessions() {
        val archived = providerId?.let(sessionMetadataStore::archivedSessionIds).orEmpty()
        val visibleSessions = drawerSessions.filterNot { it.id in archived }
        val runtime = AgentRuntimeRegistry.session(instanceId)
        val defaultCwd = AgentRuntimeRegistry.defaultCwd(instanceId, generation)
            ?: runtime?.cwd
            ?: "/workspace"
        val groups = AgentSurfaceNavigationPolicy.groupSessions(
            visibleSessions,
            defaultCwd,
            agentId?.let(projectStore::projects).orEmpty(),
            agentId?.let(projectStore::archivedProjects)
                .orEmpty()
                .mapTo(linkedSetOf(), AgentProject::cwd),
        )
        if (!drawerExpansionSeeded) {
            runtime
                ?.takeIf { it.sessionId != null && !AgentSurfaceNavigationPolicy.sameCwd(it.cwd, defaultCwd) }
                ?.cwd
                ?.let(expandedProjectCwds::add)
            drawerExpansionSeeded = true
        }
        drawerAdapter.selectedSessionId = sessionId
        drawerAdapter.submitList(
            AgentSurfaceNavigationPolicy.drawerRows(groups, expandedProjectCwds)
        )
        drawerList.visibility = View.VISIBLE
        drawerStatusText.visibility = View.GONE
    }

    private fun renderSessionSearchResults() {
        val archived = providerId?.let(sessionMetadataStore::archivedSessionIds).orEmpty()
        val visibleSessions = drawerSessions.filterNot { it.id in archived }
        val filtered = AgentSurfaceNavigationPolicy.filterSessions(
            sessions = visibleSessions,
            query = sessionSearchInput.text?.toString().orEmpty()
        )
        sessionSearchAdapter.selectedSessionId = sessionId
        sessionSearchAdapter.submitList(filtered)
        sessionSearchList.visibility = if (filtered.isEmpty()) View.GONE else View.VISIBLE
        sessionSearchStatusText.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
        if (filtered.isEmpty()) {
            sessionSearchStatusText.text = when {
                drawerSessions.isEmpty() -> "当前 Agent 还没有历史会话"
                visibleSessions.isEmpty() -> "当前列表没有未归档会话"
                else -> "没有匹配的会话"
            }
        }
    }

    private fun loadDrawerSession(session: AgentSessionSummary) {
        val defaultCwd = AgentRuntimeRegistry.defaultCwd(instanceId, generation)
        if (defaultCwd != null && !AgentSurfaceNavigationPolicy.sameCwd(session.cwd, defaultCwd)) {
            expandedProjectCwds.add(AgentSurfaceNavigationPolicy.normalizeCwd(session.cwd))
        }
        closeNavigation()
        lifecycleOwner.lifecycleScope.launch {
            showOperationResult(
                AgentRuntimeRegistry.loadSession(instanceId, generation, session.id, session.cwd),
                "已切换会话"
            )
        }
    }

    private fun toggleDrawerProject(cwd: String) {
        val normalized = AgentSurfaceNavigationPolicy.normalizeCwd(cwd)
        if (!expandedProjectCwds.add(normalized)) expandedProjectCwds.remove(normalized)
        renderDrawerSessions()
    }

    private fun showProjectMenu(anchor: View, project: AgentSessionProjectGroup) {
        ui.showAnchoredMenu(
            context = context,
            anchor = anchor,
            widthDp = 172,
            items = listOf(
                UiMenuItem(
                    label = "归档项目",
                    onClick = { archiveProject(project) },
                )
            ),
        )
    }

    private fun archiveProject(project: AgentSessionProjectGroup) {
        val targetAgentId = agentId?.takeIf(String::isNotBlank) ?: return
        if (!projectStore.archive(targetAgentId, project.name, project.cwd)) return
        expandedProjectCwds.remove(AgentSurfaceNavigationPolicy.normalizeCwd(project.cwd))
        renderDrawerSessions()
        Toast.makeText(context, "已归档项目", Toast.LENGTH_SHORT).show()
    }

    private fun handleDrawerAction(action: AgentDrawerAction) {
        when (action) {
            AgentDrawerAction.ChooseProject -> showNewWorkspaceSession()
            is AgentDrawerAction.NewDraft -> {
                if (!AgentSurfaceNavigationPolicy.sameCwd(
                        action.cwd,
                        AgentRuntimeRegistry.defaultCwd(instanceId, generation).orEmpty()
                    )) {
                    expandedProjectCwds.add(AgentSurfaceNavigationPolicy.normalizeCwd(action.cwd))
                }
                closeNavigation()
                createNewSession(action.cwd)
            }
        }
    }

    private fun showAgentSettings(returnToDrawer: Boolean) {
        closeCommandPalette(animate = false)
        closeComposerExtensionMenu(animate = false)
        closeSessionConfigurationPanel(animate = false)
        navigationJob?.cancel()
        settingsReturnsToDrawer = returnToDrawer
        selectedSettingsAgentId = agentId
        showSettingsPage(reload = true)
    }

    private fun returnToAgentSettings() {
        navigationJob?.cancel()
        defaultPermissionPendingProfileId = null
        showSettingsPage(reload = settingsRegistrySnapshot == null)
    }

    private fun showSettingsPage(reload: Boolean) {
        navigationScreen = AgentNavigationScreen.Settings
        navigationHost.removeAllViews()
        navigationHost.addView(settingsPageView, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))
        navigationHost.visibility = View.VISIBLE
        if (reload) {
            loadAgentRegistry()
        } else {
            settingsRegistrySnapshot?.let(::renderAgentSettings) ?: loadAgentRegistry()
        }
    }

    private fun buildSettingsPage(): View = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(agentPageBackground)
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(ui.dp(14), ui.dp(10), ui.dp(14), ui.dp(6))
            addView(iconButton(context, R.drawable.ic_arrow_back_light, "返回") {
                if (settingsReturnsToDrawer) showSessionDrawer() else closeNavigation()
            }, LinearLayout.LayoutParams(ui.dp(48), ui.dp(48)))
            addView(TextView(context).apply {
                text = "Agent 设置"
                textSize = 19f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setTextColor(tokens.textPrimary)
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(iconButton(context, R.drawable.ic_refresh_light, "刷新 Agent 状态", ::loadAgentRegistry),
                LinearLayout.LayoutParams(ui.dp(48), ui.dp(48)))
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(64)))
        addView(ScrollView(context).apply {
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_NEVER
            addView(settingsContentHost.apply {
                orientation = LinearLayout.VERTICAL
                setPadding(ui.dp(16), ui.dp(10), ui.dp(16), ui.dp(24))
            }, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
    }

    private fun loadAgentRegistry() {
        settingsContentHost.removeAllViews()
        settingsContentHost.addView(settingsMessage("正在读取已登记 Agent…"))
        val requestedAgentId = selectedSettingsAgentId ?: agentId
        val requestRevision = ++settingsLoadRevision
        navigationJob = lifecycleOwner.lifecycleScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                val snapshot = agentRegistry.snapshot()
                val selected = snapshot.entry(requestedAgentId.orEmpty())
                    ?: snapshot.entry(agentId.orEmpty())
                    ?: snapshot.entries.firstOrNull()
                val registration = selected?.registration
                val configResult = registration
                    ?.let(agentConfigAdapters::adapterFor)
                    ?.readLive(registration.definition.agentId)
                AgentSettingsLoad(snapshot, registration?.definition?.agentId, configResult)
            }
            if (
                navigationScreen == AgentNavigationScreen.Settings &&
                requestRevision == settingsLoadRevision
            ) {
                persistentConfigAgentId = loaded.agentId
                persistentConfigResult = loaded.configResult
                settingsRegistrySnapshot = loaded.registry
                renderAgentSettings(loaded.registry)
                val migratedSnapshot = (loaded.configResult as? AgentConfigReadResult.Ready)?.snapshot
                if (migratedSnapshot?.runtimeReloadRequired == true && loaded.agentId != null) {
                    Toast.makeText(
                        context,
                        "旧版供应商配置已迁移；以后新建或重新连接的会话会使用新配置",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun renderAgentSettings(snapshot: AgentRegistrySnapshot) {
        settingsRegistrySnapshot = snapshot
        settingsContentHost.removeAllViews()
        val selected = snapshot.entry(selectedSettingsAgentId.orEmpty())
            ?: snapshot.entry(agentId.orEmpty())
            ?: snapshot.entries.firstOrNull()
        if (selected == null) {
            settingsContentHost.addView(settingsMessage("还没有已登记的 Agent"))
            return
        }
        selectedSettingsAgentId = selected.registration.definition.agentId
        settingsContentHost.addView(buildAgentSelector(selected, snapshot))
        renderPersistentConfigurationSection(selected)
        settingsContentHost.addView(persistentConfigSettingsHost)
        if (snapshot.conflicts.isNotEmpty()) {
            settingsContentHost.addView(buildSettingsSection(
                title = "登记冲突",
                description = "重复的稳定 ID 不会出现在可打开列表中。",
                rows = snapshot.conflicts.map { conflict ->
                    SettingsRow(conflict.agentId, conflict.message)
                }
            ))
        }
        val selectedProviderId = selected.registration.launch.providerId
        val archivedCount = sessionMetadataStore.archivedSessionIds(selectedProviderId).size
        val selectedAgentId = selected.registration.definition.agentId
        val archivedProjectCount = projectStore.archivedProjects(selectedAgentId).size
        settingsContentHost.addView(buildSettingsSection(
            title = "会话与项目",
            description = "管理从当前 Agent 列表归档的内容。",
            rows = listOf(SettingsRow(
                title = "归档内容管理",
                subtitle = when {
                    archivedCount == 0 && archivedProjectCount == 0 -> "暂无已归档内容"
                    archivedProjectCount == 0 -> "$archivedCount 个会话"
                    archivedCount == 0 -> "$archivedProjectCount 个项目"
                    else -> "$archivedCount 个会话 · $archivedProjectCount 个项目"
                },
                onClick = { showArchivedContentManager(selected) }
            ))
        ))
    }

    private fun buildAgentSelector(selected: AgentRegistryEntry, snapshot: AgentRegistrySnapshot): View =
        LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(ui.dp(16), ui.dp(14), ui.dp(8), ui.dp(14))
            background = ui.roundedBox(
                agentSettingsSurface,
                android.graphics.Color.TRANSPARENT,
                ui.dp(22).toFloat()
            )
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(TextView(context).apply {
                    text = selected.registration.definition.displayName
                    textSize = 18f
                    typeface = Typeface.DEFAULT_BOLD
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                    setTextColor(tokens.textPrimary)
                })
                addView(TextView(context).apply {
                    text = buildString {
                        append(selected.registration.definition.agentId)
                        append(" · ").append(selected.primaryStatusLabel())
                    }
                    textSize = 12f
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                    setTextColor(tokens.textSecondary)
                    setPadding(0, ui.dp(3), 0, 0)
                })
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(ImageView(context).apply {
                setImageResource(R.drawable.ic_chevron_right_light)
                imageTintList = ColorStateList.valueOf(tokens.textPrimary)
                contentDescription = "选择要管理的 Agent"
                setPadding(ui.dp(12), ui.dp(12), ui.dp(12), ui.dp(12))
            }, LinearLayout.LayoutParams(ui.dp(48), ui.dp(48)))
            isClickable = true
            isFocusable = true
            setOnClickListener { showAgentPicker(snapshot) }
        }.also { view ->
            view.layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, ui.dp(20)) }
        }

    private fun showAgentPicker(snapshot: AgentRegistrySnapshot) {
        val labels = snapshot.entries.map { entry ->
            "${entry.registration.definition.displayName} · ${entry.primaryStatusLabel()}"
        }
        val checked = snapshot.entries.indexOfFirst {
            it.registration.definition.agentId == selectedSettingsAgentId
        }
        ui.showChoiceDialog(
            context = context,
            title = "选择 Agent",
            options = labels,
            selectedIndex = checked,
            dismissLabel = "取消"
        ) { index ->
                selectedSettingsAgentId = snapshot.entries[index].registration.definition.agentId
                loadAgentRegistry()
        }
    }

    private fun showArchivedContentManager(selected: AgentRegistryEntry) {
        navigationJob?.cancel()
        val targetProviderId = selected.registration.launch.providerId
        val targetAgentId = selected.registration.definition.agentId
        navigationScreen = AgentNavigationScreen.ArchivedContent
        var editMode = false
        var archivedSessions = emptyList<AgentSessionSummary>()
        var archivedProjects = projectStore.archivedProjects(targetAgentId)
        var sessionStatusMessage: String? = "正在读取已归档会话…"
        val selectedIds = linkedSetOf<String>()
        val expandedArchivedCwds = linkedSetOf<String>()
        var archiveExpansionSeeded = false
        lateinit var refreshArchivedContent: () -> Unit
        lateinit var renderArchiveState: () -> Unit
        lateinit var renderEditState: () -> Unit
        lateinit var renderArchivedRows: () -> Unit
        val status = TextView(context).apply {
            text = "正在读取已归档会话…"
            textSize = 13.5f
            gravity = Gravity.CENTER
            setTextColor(tokens.textSecondary)
            setPadding(ui.dp(20), ui.dp(28), ui.dp(20), ui.dp(28))
        }
        val archivedList = RecyclerView(context).apply {
            layoutManager = LinearLayoutManager(context)
            itemAnimator = null
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        val archivedAdapter = ArchivedSessionAdapter(
            context = context,
            tokens = tokens,
            onClick = { archivedSession ->
                if (editMode) {
                    val next = AgentArchivedSelectionPolicy.toggle(selectedIds, archivedSession.id)
                    selectedIds.clear()
                    selectedIds.addAll(next)
                    renderEditState()
                } else {
                    showArchivedSessionActions(selected, archivedSession, refreshArchivedContent)
                }
            },
            onGroupToggle = { cwd ->
                val normalized = AgentSurfaceNavigationPolicy.normalizeCwd(cwd)
                if (!expandedArchivedCwds.add(normalized)) expandedArchivedCwds.remove(normalized)
                renderArchivedRows()
            },
            onProjectRestore = { project ->
                if (projectStore.restore(targetAgentId, project.cwd)) {
                    archivedProjects = projectStore.archivedProjects(targetAgentId)
                    Toast.makeText(context, "已恢复项目", Toast.LENGTH_SHORT).show()
                    renderArchivedRows()
                    renderArchiveState()
                }
            }
        )
        archivedList.adapter = archivedAdapter
        renderArchivedRows = {
            val defaultCwd = AgentRuntimeRegistry.defaultCwd(instanceId, generation)
                ?: AgentRuntimeRegistry.session(instanceId)?.cwd
                ?: "/workspace"
            val groups = AgentSurfaceNavigationPolicy.groupSessions(
                archivedSessions,
                defaultCwd,
                projectStore.projects(targetAgentId) + archivedProjects,
            )
            if (!archiveExpansionSeeded && (archivedSessions.isNotEmpty() || archivedProjects.isNotEmpty())) {
                when {
                    groups.defaultSessions.isNotEmpty() -> expandedArchivedCwds.add(groups.defaultCwd)
                    groups.projects.isNotEmpty() -> expandedArchivedCwds.add(groups.projects.first().cwd)
                }
                archiveExpansionSeeded = true
            }
            archivedAdapter.submitList(
                AgentSurfaceNavigationPolicy.archivedRows(groups, expandedArchivedCwds, archivedProjects)
            )
        }
        val editAction = TextView(context).apply {
            text = "编辑"
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(tokens.primaryStrong)
            visibility = View.INVISIBLE
            isClickable = true
            isFocusable = true
        }
        val selectAllIndicator = TextView(context).apply {
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }
        val selectAll = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(selectAllIndicator, LinearLayout.LayoutParams(ui.dp(30), ui.dp(30)).apply {
                setMargins(ui.dp(4), 0, ui.dp(8), 0)
            })
            addView(TextView(context).apply {
                text = "全选"
                textSize = 14f
                setTextColor(tokens.textPrimary)
            })
            isClickable = true
            isFocusable = true
        }
        val selectedCount = TextView(context).apply {
            textSize = 12.5f
            gravity = Gravity.CENTER_VERTICAL or Gravity.END
            setTextColor(tokens.textSecondary)
        }
        val selectionRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            visibility = View.GONE
            setPadding(ui.dp(18), 0, ui.dp(18), ui.dp(8))
            addView(selectAll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ui.dp(44)))
            addView(selectedCount, LinearLayout.LayoutParams(0, ui.dp(44), 1f))
        }
        val restoreSelected = actionOutlineButton("恢复") {
            val restoring = archivedSessions.filter { it.id in selectedIds }
            if (restoring.isEmpty()) return@actionOutlineButton
            restoring.forEach { sessionMetadataStore.restore(targetProviderId, it.id) }
            Toast.makeText(context, "已恢复 ${restoring.size} 个会话", Toast.LENGTH_SHORT).show()
            selectedIds.clear()
            refreshArchivedContent()
        }
        val deleteSelected = actionDangerButton("删除") {
            val deleting = archivedSessions.filter { it.id in selectedIds }
            if (deleting.isEmpty()) return@actionDangerButton
            showArchivedDeleteConfirmation(selected, deleting, refreshArchivedContent)
        }
        val batchActions = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            visibility = View.GONE
            setPadding(ui.dp(18), ui.dp(8), ui.dp(18), ui.dp(14))
            addView(restoreSelected, LinearLayout.LayoutParams(0, ui.dp(48), 1f))
            addView(deleteSelected, LinearLayout.LayoutParams(0, ui.dp(48), 1f).apply {
                setMargins(ui.dp(10), 0, 0, 0)
            })
        }
        renderEditState = {
            archivedAdapter.setSelectionState(editMode, selectedIds)
            selectionRow.visibility = if (editMode) View.VISIBLE else View.GONE
            batchActions.visibility = if (editMode) View.VISIBLE else View.GONE
            editAction.text = if (editMode) "完成" else "编辑"
            val allSelected = archivedSessions.isNotEmpty() && selectedIds.size == archivedSessions.size
            renderCircularSelection(selectAllIndicator, allSelected)
            selectAll.contentDescription = if (allSelected) "取消全选归档会话" else "全选归档会话"
            selectedCount.text = "已选择 ${selectedIds.size} 项"
            val hasSelection = selectedIds.isNotEmpty()
            restoreSelected.isEnabled = hasSelection
            restoreSelected.alpha = if (hasSelection) 1f else 0.38f
            val runtime = AgentRuntimeRegistry.session(instanceId)
            val canDelete = AgentArchivedSelectionPolicy.canDelete(
                selectedIds = selectedIds,
                currentSessionId = runtime?.sessionId,
                deleteSupported = runtime?.providerId == targetProviderId &&
                    runtime.capabilities.sessions.delete
            )
            deleteSelected.isEnabled = canDelete
            deleteSelected.alpha = if (canDelete) 1f else 0.38f
            deleteSelected.contentDescription = when {
                !hasSelection -> "删除归档会话，请先选择"
                runtime?.capabilities?.sessions?.delete != true -> "当前 Agent 不支持永久删除"
                runtime.sessionId in selectedIds -> "当前窗口使用中的会话不能永久删除"
                else -> "永久删除选中的 ${selectedIds.size} 个会话"
            }
        }
        renderArchiveState = {
            val hasItems = archivedSessions.isNotEmpty() || archivedProjects.isNotEmpty()
            archivedList.visibility = if (hasItems) View.VISIBLE else View.GONE
            editAction.visibility = if (archivedSessions.isEmpty()) View.INVISIBLE else View.VISIBLE
            if (archivedSessions.isEmpty()) {
                editMode = false
                selectedIds.clear()
            }
            status.text = sessionStatusMessage ?: if (hasItems) "" else "暂无已归档内容"
            status.visibility = if (status.text.isNullOrBlank()) View.GONE else View.VISIBLE
            renderEditState()
        }
        editAction.setOnClickListener {
            editMode = !editMode
            selectedIds.clear()
            renderEditState()
        }
        selectAll.setOnClickListener {
            val checked = archivedSessions.isNotEmpty() && selectedIds.size != archivedSessions.size
            selectedIds.clear()
            if (checked) selectedIds.addAll(AgentArchivedSelectionPolicy.selectAll(archivedSessions))
            renderEditState()
        }
        val page = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(agentPageBackground)
            addView(buildAgentSubpageHeader(
                title = "归档内容管理",
                backDescription = "返回 Agent 设置",
                onBack = ::returnToAgentSettings,
                trailingView = editAction
            ), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(64)))
            addView(TextView(context).apply {
                text = "${selected.registration.definition.displayName} · 项目归档只影响 Kite 列表，不会改动文件"
                textSize = 12.5f
                setTextColor(tokens.textSecondary)
                setPadding(ui.dp(22), ui.dp(8), ui.dp(22), ui.dp(12))
            })
            addView(selectionRow, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(status, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(archivedList, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).apply {
                setMargins(ui.dp(14), 0, ui.dp(14), ui.dp(8))
            })
            addView(batchActions, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        navigationHost.removeAllViews()
        navigationHost.addView(page, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))
        navigationHost.visibility = View.VISIBLE

        val runtime = AgentRuntimeRegistry.session(instanceId)
        if (runtime == null || runtime.providerId != targetProviderId) {
            sessionStatusMessage = "已显示归档项目；打开该 Agent 后可读取归档会话"
            renderArchivedRows()
            renderArchiveState()
            return
        }
        if (!runtime.capabilities.sessions.list) {
            sessionStatusMessage = "当前 Agent 未提供会话列表；项目仍可恢复"
            renderArchivedRows()
            renderArchiveState()
            return
        }
        refreshArchivedContent = {
            navigationJob?.cancel()
            archivedProjects = projectStore.archivedProjects(targetAgentId)
            sessionStatusMessage = "正在读取已归档会话…"
            renderArchivedRows()
            renderArchiveState()
            navigationJob = lifecycleOwner.lifecycleScope.launch {
                when (val result = AgentRuntimeRegistry.listSessions(instanceId, generation)) {
                    is AgentOperationResult.Success -> {
                        if (navigationScreen != AgentNavigationScreen.ArchivedContent) return@launch
                        val archivedIds = sessionMetadataStore.archivedSessionIds(targetProviderId)
                        archivedSessions = result.value.sessions.filter { it.id in archivedIds }
                        selectedIds.retainAll(archivedSessions.mapTo(linkedSetOf(), AgentSessionSummary::id))
                        sessionStatusMessage = null
                        renderArchivedRows()
                        renderArchiveState()
                    }
                    is AgentOperationResult.Unsupported -> {
                        sessionStatusMessage = "当前 Agent 未提供会话列表；项目仍可恢复"
                        renderArchiveState()
                    }
                    is AgentOperationResult.Failure -> {
                        sessionStatusMessage = result.message
                        renderArchiveState()
                    }
                }
            }
        }
        refreshArchivedContent()
    }

    private fun renderCircularSelection(indicator: TextView, selected: Boolean) {
        indicator.text = if (selected) "✓" else ""
        indicator.setTextColor(android.graphics.Color.WHITE)
        indicator.background = ui.roundedBox(
            if (selected) tokens.primaryStrong else android.graphics.Color.TRANSPARENT,
            if (selected) android.graphics.Color.TRANSPARENT else tokens.borderStrong,
            ui.dp(15).toFloat(),
            ui.dp(1)
        )
    }

    private fun showArchivedSessionActions(
        selected: AgentRegistryEntry,
        session: AgentSessionSummary,
        onChanged: () -> Unit
    ) {
        val targetProviderId = selected.registration.launch.providerId
        val runtime = AgentRuntimeRegistry.session(instanceId)
        val canDelete = runtime?.providerId == targetProviderId &&
            runtime.capabilities.sessions.delete &&
            runtime.sessionId != session.id
        val message = when {
            runtime?.sessionId == session.id -> "该会话仍在当前窗口使用。恢复可以立即执行；永久删除前请先切换到其他会话。"
            runtime?.capabilities?.sessions?.delete != true -> "恢复只取消 Kite 归档；当前 Agent 尚未提供永久删除能力。"
            else -> "恢复只取消 Kite 归档；删除会永久移除 Agent 原生会话。"
        }
        showAgentDialogCard(
            title = session.title?.takeIf(String::isNotBlank) ?: "未命名会话",
            message = message,
            actions = listOf(
                AgentDialogAction("取消", UiActionRole.Secondary) { dialog, _ -> dialog.dismiss() },
                AgentDialogAction("恢复", UiActionRole.Primary) { dialog, _ ->
                    dialog.dismiss()
                    sessionMetadataStore.restore(targetProviderId, session.id)
                    onChanged()
                },
                AgentDialogAction("删除", UiActionRole.Danger, enabled = canDelete) { dialog, _ ->
                    dialog.dismiss()
                    showArchivedDeleteConfirmation(selected, listOf(session), onChanged)
                }
            )
        )
    }

    private fun showAgentDialogCard(
        title: String,
        message: String,
        actions: List<AgentDialogAction>
    ): Dialog {
        val dialog = Dialog(context)
        val actionRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        actions.forEachIndexed { index, action ->
            actionRow.addView(TextView(context).apply {
                text = action.label
                textSize = 14f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                isEnabled = action.enabled
                alpha = if (action.enabled) 1f else 0.36f
                setTextColor(when (action.role) {
                    UiActionRole.Primary -> tokens.primaryStrong
                    UiActionRole.Secondary -> tokens.textSecondary
                    UiActionRole.Danger -> tokens.danger
                })
                background = ui.roundedBox(
                    when (action.role) {
                        UiActionRole.Primary -> tokens.primarySubtle
                        UiActionRole.Secondary -> agentSettingsSurface
                        UiActionRole.Danger -> tokens.dangerSoft
                    },
                    android.graphics.Color.TRANSPARENT,
                    ui.dp(16).toFloat()
                )
                setOnClickListener {
                    if (isEnabled) action.onClick(dialog, this)
                }
            }, LinearLayout.LayoutParams(0, ui.dp(48), 1f).apply {
                if (index > 0) setMargins(ui.dp(8), 0, 0, 0)
            })
        }
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ui.dp(22), ui.dp(22), ui.dp(22), ui.dp(18))
            background = ui.roundedBox(agentSurface, android.graphics.Color.TRANSPARENT, ui.dp(26).toFloat())
            elevation = ui.dp(10).toFloat()
            addView(TextView(context).apply {
                text = title
                textSize = 19f
                typeface = Typeface.DEFAULT_BOLD
                maxLines = 2
                ellipsize = TextUtils.TruncateAt.END
                setTextColor(tokens.textPrimary)
            })
            addView(TextView(context).apply {
                text = message
                textSize = 14f
                setLineSpacing(ui.dp(3).toFloat(), 1f)
                setTextColor(tokens.textSecondary)
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, ui.dp(12), 0, 0)
            })
            addView(actionRow, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, ui.dp(20), 0, 0)
            })
        }
        dialog.setContentView(content)
        dialog.show()
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
            setGravity(Gravity.CENTER)
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setDimAmount(if (isDark) 0.62f else 0.32f)
            setLayout(
                (context.resources.displayMetrics.widthPixels * 0.88f).toInt(),
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        return dialog
    }

    private fun showAgentChoiceCard(
        title: String,
        message: String,
        actions: List<AgentChoiceAction>,
    ): Dialog {
        val dialog = Dialog(context)
        val actionHost = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            actions.forEachIndexed { index, action ->
                addView(LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(ui.dp(16), 0, ui.dp(12), 0)
                    background = ui.roundedBox(
                        if (action.selected) tokens.primarySubtle else agentSettingsSurface,
                        android.graphics.Color.TRANSPARENT,
                        ui.dp(17).toFloat(),
                    )
                    addView(TextView(context).apply {
                        text = action.label
                        textSize = 14.5f
                        typeface = if (action.selected) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                        setTextColor(if (action.role == UiActionRole.Danger) tokens.danger else tokens.textPrimary)
                    }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                    addView(TextView(context).apply {
                        text = if (action.selected) "✓" else ""
                        textSize = 16f
                        typeface = Typeface.DEFAULT_BOLD
                        gravity = Gravity.CENTER
                        setTextColor(tokens.primaryStrong)
                    }, LinearLayout.LayoutParams(ui.dp(36), ui.dp(36)))
                    isClickable = true
                    isFocusable = true
                    setOnClickListener {
                        dialog.dismiss()
                        if (!action.selected) action.onClick()
                    }
                }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(54)).apply {
                    if (index > 0) setMargins(0, ui.dp(7), 0, 0)
                })
            }
        }
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ui.dp(22), ui.dp(22), ui.dp(22), ui.dp(18))
            background = ui.roundedBox(agentSurface, android.graphics.Color.TRANSPARENT, ui.dp(26).toFloat())
            elevation = ui.dp(10).toFloat()
            addView(TextView(context).apply {
                text = title
                textSize = 19f
                typeface = Typeface.DEFAULT_BOLD
                maxLines = 2
                ellipsize = TextUtils.TruncateAt.END
                setTextColor(tokens.textPrimary)
            })
            addView(TextView(context).apply {
                text = message
                textSize = 13f
                setTextColor(tokens.textSecondary)
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, ui.dp(8), 0, 0)
            })
            addView(actionHost, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { setMargins(0, ui.dp(16), 0, 0) })
        }
        dialog.setContentView(content)
        dialog.show()
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
            setGravity(Gravity.CENTER)
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setDimAmount(if (isDark) 0.62f else 0.32f)
            setLayout(
                (context.resources.displayMetrics.widthPixels * 0.88f).toInt(),
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        }
        return dialog
    }

    private fun showArchivedDeleteConfirmation(
        selected: AgentRegistryEntry,
        sessions: List<AgentSessionSummary>,
        onChanged: () -> Unit
    ) {
        val targetProviderId = selected.registration.launch.providerId
        val count = sessions.size
        showAgentDialogCard(
            title = if (count == 1) "永久删除会话？" else "永久删除 $count 个会话？",
            message = "删除后将由 ${selected.registration.definition.displayName} 永久移除，且无法恢复。",
            actions = listOf(
                AgentDialogAction("取消", UiActionRole.Secondary) { dialog, _ -> dialog.dismiss() },
                AgentDialogAction("删除", UiActionRole.Danger) { dialog, button ->
                    button.isEnabled = false
                    button.alpha = 0.48f
                    button.text = "删除中…"
                    lifecycleOwner.lifecycleScope.launch {
                        var deleted = 0
                        var failure: String? = null
                        for (session in sessions) {
                            when (val result = AgentRuntimeRegistry.deleteSession(
                                instanceId,
                                generation,
                                session.id
                            )) {
                                is AgentOperationResult.Success -> {
                                    sessionMetadataStore.remove(targetProviderId, session.id)
                                    deleted++
                                }
                                is AgentOperationResult.Unsupported -> {
                                    failure = "当前 Agent 未提供永久删除"
                                    break
                                }
                                is AgentOperationResult.Failure -> {
                                    failure = result.message
                                    break
                                }
                            }
                        }
                        dialog.dismiss()
                        onChanged()
                        val resultMessage = if (failure == null) {
                            "已永久删除 $deleted 个会话"
                        } else {
                            "已删除 $deleted 个；$failure"
                        }
                        Toast.makeText(
                            context,
                            resultMessage,
                            if (failure == null) Toast.LENGTH_SHORT else Toast.LENGTH_LONG
                        ).show()
                    }
                }
            )
        )
    }

    private fun renderPersistentConfigurationSection(selected: AgentRegistryEntry) {
        replaceSettingsSection(
            host = persistentConfigSettingsHost,
            title = "配置",
            description = "准备供应商、新会话默认权限、MCP、Skill 和核心设定。当前会话的权限只在聊天页切换。",
            rows = persistentConfigurationRows(selected)
        )
    }

    private fun replaceSettingsSection(
        host: LinearLayout,
        title: String,
        description: String,
        rows: List<SettingsRow>
    ) {
        host.removeAllViews()
        host.addView(buildSettingsSection(title, description, rows))
    }

    private fun persistentConfigurationRows(selected: AgentRegistryEntry): List<SettingsRow> {
        val registration = selected.registration
        val adapter = agentConfigAdapters.adapterFor(registration)
            ?: return listOf(
                SettingsRow("供应商配置", if (registration.configAdapterId == null) {
                    "当前 Agent 暂不支持在 Kite 中配置"
                } else {
                    "当前 Agent 的配置能力暂不可用"
                })
            )
        val result = persistentConfigResult.takeIf {
            persistentConfigAgentId == registration.definition.agentId
        } ?: return listOf(SettingsRow("供应商配置", "正在读取…"))
        return when (result) {
            is AgentConfigReadResult.Unavailable -> listOf(
                SettingsRow("供应商配置", result.discovery.warnings.firstOrNull() ?: "当前暂不可读取")
            )
            is AgentConfigReadResult.Failed -> listOf(
                SettingsRow("供应商配置", result.message)
            )
            is AgentConfigReadResult.Ready -> persistentSnapshotRows(selected, adapter, result.snapshot)
        }
    }

    private fun persistentSnapshotRows(
        selected: AgentRegistryEntry,
        adapter: AgentConfigAdapter,
        snapshot: AgentLiveConfigSnapshot
    ): List<SettingsRow> = buildList {
        if (adapter.capabilities().supports(AgentPersistentConfigCapability.Provider)) {
            add(SettingsRow(
                title = "供应商配置",
                subtitle = snapshot.providers.takeIf { it.isNotEmpty() }
                    ?.joinToString("、") { provider ->
                        "${provider.displayName}（${provider.models.size} 个模型）"
                    }
                    ?: snapshot.providerIds.takeIf { it.isNotEmpty() }?.joinToString("、")
                    ?: "尚未配置供应商",
                onClick = if (adapter.capabilities().supports(AgentPersistentConfigCapability.ProviderProfiles)) {
                    { showProviderManager(selected, adapter, snapshot) }
                } else null
            ))
        }
        if (adapter.capabilities().supports(AgentPersistentConfigCapability.PermissionProfiles)) {
            val active = snapshot.permissionProfiles.firstOrNull {
                it.id == snapshot.activePermissionProfileId
            }
            add(SettingsRow(
                title = "默认权限",
                subtitle = active?.let { profile ->
                    "${profile.level?.displayName ?: profile.displayName}·以后新建或重连的会话"
                } ?: "使用 Agent 默认或自定义规则",
                onClick = snapshot.permissionProfiles.takeIf { it.isNotEmpty() }?.let {
                    { showDefaultPermissionManager(selected, adapter, snapshot) }
                },
            ))
        }
        if (adapter.capabilities().supports(AgentPersistentConfigCapability.Mcp)) {
            add(SettingsRow(
                "MCP",
                snapshot.mcpServers.takeIf { it.isNotEmpty() }
                    ?.joinToString("、") { "${it.id}（${if (it.enabled) "启用" else "停用"}）" }
                    ?: "尚未配置 MCP",
                onClick = { showMcpManager(selected, adapter, snapshot) }
            ))
        }
        if (adapter.capabilities().supports(AgentPersistentConfigCapability.Skill)) {
            add(SettingsRow(
                "Skill",
                snapshot.skills.takeIf { it.isNotEmpty() }
                    ?.joinToString("、") { it.displayName }
                    ?: "尚未安装 Skill",
                onClick = { showSkillManager(selected, adapter, snapshot) }
            ))
        }
        if (adapter.capabilities().supports(AgentPersistentConfigCapability.CoreDocuments)) {
            add(SettingsRow(
                "核心设定",
                "管理当前 Agent 的全局说明、人格或固定工作区规则",
                onClick = { showCoreDocumentManager(selected, adapter) }
            ))
        }
        snapshot.warnings.firstOrNull()?.let { add(SettingsRow("注意", it)) }
    }

    private fun showDefaultPermissionManager(
        selected: AgentRegistryEntry,
        adapter: AgentConfigAdapter,
        snapshot: AgentLiveConfigSnapshot,
    ) {
        navigationScreen = AgentNavigationScreen.DefaultPermission
        navigationHost.removeAllViews()
        val sortedProfiles = snapshot.permissionProfiles.sortedWith(
            compareBy<AgentPermissionProfileSummary> { it.level?.order ?: Int.MAX_VALUE }
                .thenBy { it.displayName }
        )
        val page = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(agentPageBackground)
            addView(buildAgentSubpageHeader(
                title = "默认权限",
                backDescription = "返回 Agent 设置",
                onBack = ::returnToAgentSettings,
            ), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(64)))
            addView(ScrollView(context).apply {
                isFillViewport = true
                overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
                addView(LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(ui.dp(18), ui.dp(14), ui.dp(18), ui.dp(28))
                    addView(TextView(context).apply {
                        text = "这里设置以后新建或重新连接会话时的默认权限。已在运行的会话不会被强制改变；它们仍在聊天页单独切换。"
                        textSize = 13f
                        includeFontPadding = false
                        setLineSpacing(0f, 1.18f)
                        setTextColor(tokens.textSecondary)
                        setPadding(ui.dp(2), 0, ui.dp(2), ui.dp(14))
                    })
                    addView(LinearLayout(context).apply {
                        orientation = LinearLayout.VERTICAL
                        background = ui.roundedBox(
                            agentSettingsSurface,
                            android.graphics.Color.TRANSPARENT,
                            ui.dp(20).toFloat(),
                        )
                        sortedProfiles.forEachIndexed { index, profile ->
                            addView(sessionPermissionChoiceRow(
                                title = profile.level?.displayName ?: profile.displayName,
                                description = profile.description ?: profile.displayName,
                                selected = profile.id == snapshot.activePermissionProfileId,
                                contentDescription = "默认权限，${profile.level?.displayName ?: profile.displayName}",
                                onClick = {
                                    applyDefaultPermissionProfile(selected, adapter, snapshot, profile)
                                },
                            ).apply {
                                isClickable = defaultPermissionPendingProfileId == null
                                alpha = if (defaultPermissionPendingProfileId == null) 1f else 0.55f
                            })
                            if (index != sortedProfiles.lastIndex) addView(View(context).apply {
                                setBackgroundColor(tokens.border)
                            }, LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ui.dp(1),
                            ).apply { setMargins(ui.dp(16), 0, 0, 0) })
                        }
                    })
                }, ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ))
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        }
        navigationHost.addView(page, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        ))
        navigationHost.visibility = View.VISIBLE
    }

    private fun applyDefaultPermissionProfile(
        selected: AgentRegistryEntry,
        adapter: AgentConfigAdapter,
        snapshot: AgentLiveConfigSnapshot,
        profile: AgentPermissionProfileSummary,
    ) {
        if (defaultPermissionPendingProfileId != null || profile.id == snapshot.activePermissionProfileId) return
        val targetAgentId = selected.registration.definition.agentId
        val requestRevision = ++settingsLoadRevision
        defaultPermissionPendingProfileId = profile.id
        showDefaultPermissionManager(
            selected,
            adapter,
            snapshot.copy(activePermissionProfileId = profile.id),
        )
        navigationJob?.cancel()
        navigationJob = lifecycleOwner.lifecycleScope.launch {
            val outcome = withContext(Dispatchers.IO) {
                val result = adapter.apply(
                    AgentConfigApplyRequest(
                        agentId = targetAgentId,
                        expectedRevision = snapshot.revision,
                        changes = listOf(AgentPersistentConfigChange.SetPermissionProfile(profile.id)),
                    )
                )
                val refreshed = when (result) {
                    is AgentConfigApplyResult.Applied -> AgentConfigReadResult.Ready(result.snapshot)
                    else -> adapter.backfill(targetAgentId)
                }
                result to refreshed
            }
            if (requestRevision != settingsLoadRevision || navigationScreen != AgentNavigationScreen.DefaultPermission) {
                return@launch
            }
            defaultPermissionPendingProfileId = null
            persistentConfigAgentId = targetAgentId
            persistentConfigResult = outcome.second
            val refreshedSnapshot = (outcome.second as? AgentConfigReadResult.Ready)?.snapshot
            when (val result = outcome.first) {
                is AgentConfigApplyResult.Applied -> {
                    val applied = refreshedSnapshot ?: result.snapshot
                    showDefaultPermissionManager(selected, adapter, applied)
                    Toast.makeText(
                        context,
                        "默认权限已保存；当前会话保持不变",
                        Toast.LENGTH_SHORT,
                    ).show()
                }
                else -> {
                    showDefaultPermissionManager(selected, adapter, refreshedSnapshot ?: snapshot)
                    Toast.makeText(
                        context,
                        result.userMessage("默认权限已保存"),
                        Toast.LENGTH_LONG,
                    ).show()
                }
            }
        }
    }

    private fun showCoreDocumentManager(
        selected: AgentRegistryEntry,
        adapter: AgentConfigAdapter,
        reload: Boolean = true,
    ) {
        coreDocumentPageAgentId = selected.registration.definition.agentId
        coreDocumentPageAdapter = adapter
        // 通用设置没有绑定具体项目，不能把实例默认 cwd 伪装成“当前项目”。
        // 需要项目文件时，必须由未来明确绑定项目的入口把 workspacePath 传进来。
        coreDocumentWorkspacePath = null
        coreDocumentEditorSnapshot = null
        coreDocumentEditorInput = null
        navigationScreen = AgentNavigationScreen.CoreDocumentList
        navigationHost.removeAllViews()
        navigationHost.addView(
            buildCoreDocumentListPage(selected),
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        navigationHost.visibility = View.VISIBLE
        if (reload) loadCoreDocuments(selected, adapter) else renderCoreDocuments(coreDocumentDescriptors)
    }

    private fun buildCoreDocumentListPage(selected: AgentRegistryEntry): View {
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ui.dp(16), ui.dp(6), ui.dp(16), ui.dp(28))
        }.also { coreDocumentListHost = it }
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(agentPageBackground)
            addView(buildAgentSubpageHeader(
                title = "核心设定",
                backDescription = "返回 Agent 设置",
                onBack = ::returnToAgentSettings,
            ), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(64)))
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(ui.dp(22), ui.dp(2), ui.dp(22), ui.dp(12))
                addView(TextView(context).apply {
                    text = selected.registration.definition.displayName
                    textSize = 20f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(tokens.textPrimary)
                })
                addView(TextView(context).apply {
                    text = "每份文档仍由 Agent 原生读取；作用域和覆盖方式会分别标明。"
                    textSize = 12.5f
                    setLineSpacing(ui.dp(2).toFloat(), 1f)
                    setTextColor(tokens.textSecondary)
                    setPadding(0, ui.dp(4), 0, 0)
                })
            })
            addView(ScrollView(context).apply {
                isFillViewport = true
                overScrollMode = View.OVER_SCROLL_NEVER
                addView(content, ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ))
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        }
    }

    private fun loadCoreDocuments(selected: AgentRegistryEntry, adapter: AgentConfigAdapter) {
        val host = coreDocumentListHost ?: return
        host.removeAllViews()
        host.addView(settingsMessage("正在读取核心设定目录…"))
        val targetAgentId = selected.registration.definition.agentId
        val workspacePath = coreDocumentWorkspacePath
        val requestRevision = ++coreDocumentLoadRevision
        navigationJob?.cancel()
        navigationJob = lifecycleOwner.lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                adapter.listCoreDocuments(targetAgentId, workspacePath)
            }
            if (
                requestRevision != coreDocumentLoadRevision ||
                navigationScreen != AgentNavigationScreen.CoreDocumentList ||
                coreDocumentPageAgentId != targetAgentId
            ) return@launch
            when (result) {
                is AgentCoreDocumentListResult.Ready -> {
                    coreDocumentDescriptors = result.documents
                    renderCoreDocuments(result.documents)
                }
                is AgentCoreDocumentListResult.Unavailable -> renderCoreDocumentMessage(
                    result.discovery.warnings.firstOrNull() ?: "核心设定当前不可用"
                )
                is AgentCoreDocumentListResult.Failed -> renderCoreDocumentMessage(result.message)
            }
        }
    }

    private fun renderCoreDocuments(documents: List<AgentCoreDocumentDescriptor>) {
        val host = coreDocumentListHost ?: return
        host.removeAllViews()
        if (documents.isEmpty()) {
            host.addView(settingsMessage("当前 Agent 没有可管理的核心设定"))
            return
        }
        documents.groupBy(AgentCoreDocumentUiPolicy::sectionTitle).forEach { (title, items) ->
            host.addView(buildSettingsSection(
                title = title,
                description = AgentCoreDocumentUiPolicy.sectionDescription(items.first().scope),
                rows = items.map { document ->
                    SettingsRow(
                        title = document.displayName,
                        subtitle = AgentCoreDocumentUiPolicy.summary(document),
                        onClick = { showCoreDocumentEditor(document) },
                    )
                },
            ))
        }
    }

    private fun renderCoreDocumentMessage(message: String) {
        coreDocumentListHost?.apply {
            removeAllViews()
            addView(settingsMessage(message))
        }
    }

    private fun showCoreDocumentEditor(descriptor: AgentCoreDocumentDescriptor) {
        val adapter = coreDocumentPageAdapter
        val targetAgentId = coreDocumentPageAgentId
        if (adapter == null || targetAgentId == null) {
            returnToAgentSettings()
            return
        }
        navigationJob?.cancel()
        coreDocumentEditorSnapshot = null
        coreDocumentEditorInput = null
        navigationScreen = AgentNavigationScreen.CoreDocumentEditor
        navigationHost.removeAllViews()
        navigationHost.addView(
            buildCoreDocumentLoadingPage(descriptor),
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
        )
        val requestRevision = ++coreDocumentLoadRevision
        navigationJob = lifecycleOwner.lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                adapter.readCoreDocument(targetAgentId, descriptor.id, coreDocumentWorkspacePath)
            }
            if (
                requestRevision != coreDocumentLoadRevision ||
                navigationScreen != AgentNavigationScreen.CoreDocumentEditor ||
                coreDocumentPageAgentId != targetAgentId
            ) return@launch
            when (result) {
                is AgentCoreDocumentReadResult.Ready -> renderCoreDocumentEditor(adapter, result.snapshot)
                is AgentCoreDocumentReadResult.Missing -> renderCoreDocumentEditorError(descriptor, result.message)
                is AgentCoreDocumentReadResult.Unavailable -> renderCoreDocumentEditorError(
                    descriptor,
                    result.discovery.warnings.firstOrNull() ?: "核心设定当前不可用",
                )
                is AgentCoreDocumentReadResult.Failed -> renderCoreDocumentEditorError(descriptor, result.message)
            }
        }
    }

    private fun buildCoreDocumentLoadingPage(descriptor: AgentCoreDocumentDescriptor): View =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(agentPageBackground)
            addView(buildAgentSubpageHeader(
                title = descriptor.displayName,
                backDescription = "返回核心设定",
                onBack = ::showCurrentCoreDocumentList,
            ), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(64)))
            addView(settingsMessage("正在读取 ${descriptor.fileName}…"), LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f,
            ))
        }

    private fun renderCoreDocumentEditor(adapter: AgentConfigAdapter, snapshot: AgentCoreDocumentSnapshot) {
        coreDocumentEditorSnapshot = snapshot
        val status = TextView(context).apply {
            text = if (snapshot.descriptor.exists) "" else "保存后会创建此 Agent 原生文件"
            textSize = 12.5f
            setTextColor(tokens.textSecondary)
            visibility = if (text.isBlank()) View.GONE else View.VISIBLE
            setPadding(ui.dp(2), ui.dp(9), ui.dp(2), 0)
        }
        lateinit var saveAction: TextView
        val input = EditText(context).apply {
            setText(snapshot.content)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            gravity = Gravity.TOP or Gravity.START
            textSize = 14.5f
            setTextColor(tokens.textPrimary)
            setHintTextColor(tokens.textTertiary)
            hint = "输入 Markdown 内容"
            minLines = 14
            setPadding(ui.dp(16), ui.dp(15), ui.dp(16), ui.dp(15))
            background = ui.roundedBox(agentSettingsSurface, tokens.border, ui.dp(20).toFloat(), ui.dp(1))
            isEnabled = snapshot.descriptor.writable
        }.also { coreDocumentEditorInput = it }
        saveAction = TextView(context).apply {
            text = "保存"
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(tokens.primaryStrong)
            isEnabled = snapshot.descriptor.writable
            alpha = if (isEnabled) 1f else 0.38f
            isClickable = true
            isFocusable = true
            setOnClickListener { saveCoreDocument(adapter, this, status) }
        }
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ui.dp(16), ui.dp(8), ui.dp(16), ui.dp(28))
            addView(buildSettingsSection(
                title = "文档说明",
                description = snapshot.descriptor.displayLocation,
                rows = listOf(
                    SettingsRow("作用范围", AgentCoreDocumentUiPolicy.scopeLabel(snapshot.descriptor.scope)),
                    SettingsRow("生效方式", AgentCoreDocumentUiPolicy.semanticsLabel(snapshot.descriptor.semantics)),
                    SettingsRow("优先级", snapshot.descriptor.priorityDescription),
                ),
            ))
            snapshot.descriptor.warning?.let { warning ->
                addView(LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(ui.dp(16), ui.dp(14), ui.dp(16), ui.dp(14))
                    background = ui.roundedBox(tokens.dangerSoft, tokens.dangerBorder, ui.dp(20).toFloat(), ui.dp(1))
                    addView(TextView(context).apply {
                        text = "保存前请确认"
                        textSize = 14.5f
                        typeface = Typeface.DEFAULT_BOLD
                        setTextColor(tokens.danger)
                    })
                    addView(TextView(context).apply {
                        text = warning
                        textSize = 12.5f
                        setLineSpacing(ui.dp(2).toFloat(), 1f)
                        setTextColor(tokens.textSecondary)
                        setPadding(0, ui.dp(5), 0, 0)
                    })
                }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    setMargins(0, 0, 0, ui.dp(20))
                })
            }
            addView(TextView(context).apply {
                text = "Markdown 内容"
                textSize = 15f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(tokens.textPrimary)
                setPadding(ui.dp(2), 0, ui.dp(2), ui.dp(9))
            })
            addView(input, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(status)
        }
        navigationHost.removeAllViews()
        navigationHost.addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(agentPageBackground)
            addView(buildAgentSubpageHeader(
                title = snapshot.descriptor.displayName,
                backDescription = "返回核心设定",
                onBack = ::returnFromCoreDocumentEditor,
                trailingView = saveAction,
            ), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(64)))
            addView(ScrollView(context).apply {
                isFillViewport = true
                overScrollMode = View.OVER_SCROLL_NEVER
                addView(content, ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ))
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    private fun saveCoreDocument(adapter: AgentConfigAdapter, saveAction: TextView, status: TextView) {
        val snapshot = coreDocumentEditorSnapshot ?: return
        val content = coreDocumentEditorInput?.text?.toString() ?: return
        val targetAgentId = coreDocumentPageAgentId ?: return
        saveAction.text = "保存中"
        saveAction.isEnabled = false
        saveAction.alpha = 0.42f
        status.text = "正在安全写入 Agent 原生文件…"
        status.setTextColor(tokens.textSecondary)
        status.visibility = View.VISIBLE
        val requestRevision = ++coreDocumentLoadRevision
        navigationJob?.cancel()
        navigationJob = lifecycleOwner.lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                adapter.writeCoreDocument(
                    AgentCoreDocumentWriteRequest(
                        agentId = targetAgentId,
                        documentId = snapshot.descriptor.id,
                        workspacePath = coreDocumentWorkspacePath,
                        expectedRevision = snapshot.revision,
                        content = content,
                    )
                )
            }
            if (
                requestRevision != coreDocumentLoadRevision ||
                navigationScreen != AgentNavigationScreen.CoreDocumentEditor
            ) return@launch
            when (result) {
                is AgentCoreDocumentWriteResult.Applied -> {
                    coreDocumentEditorSnapshot = result.snapshot
                    coreDocumentDescriptors = coreDocumentDescriptors.map {
                        if (it.id == result.snapshot.descriptor.id) result.snapshot.descriptor else it
                    }
                    saveAction.text = "保存"
                    saveAction.isEnabled = true
                    saveAction.alpha = 1f
                    status.text = "已保存到 Agent 原生文件"
                    status.setTextColor(tokens.textSecondary)
                }
                is AgentCoreDocumentWriteResult.Conflict -> {
                    saveAction.text = "重读"
                    saveAction.isEnabled = true
                    saveAction.alpha = 1f
                    saveAction.setOnClickListener { showCoreDocumentEditor(snapshot.descriptor) }
                    status.text = result.message
                    status.setTextColor(tokens.danger)
                }
                else -> {
                    saveAction.text = "保存"
                    saveAction.isEnabled = snapshot.descriptor.writable
                    saveAction.alpha = if (saveAction.isEnabled) 1f else 0.38f
                    status.text = result.coreDocumentUserMessage()
                    status.setTextColor(tokens.danger)
                }
            }
        }
    }

    private fun AgentCoreDocumentWriteResult.coreDocumentUserMessage(): String = when (this) {
        is AgentCoreDocumentWriteResult.Applied -> "已保存"
        is AgentCoreDocumentWriteResult.Conflict -> message
        is AgentCoreDocumentWriteResult.Rejected -> problems.firstOrNull()?.message ?: "内容未通过校验"
        is AgentCoreDocumentWriteResult.Unavailable -> discovery.warnings.firstOrNull() ?: "核心设定当前不可用"
        is AgentCoreDocumentWriteResult.Failed -> if (restored) "$message，原文件已保留" else message
    }

    private fun renderCoreDocumentEditorError(descriptor: AgentCoreDocumentDescriptor, message: String) {
        navigationHost.removeAllViews()
        navigationHost.addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(agentPageBackground)
            addView(buildAgentSubpageHeader(
                title = descriptor.displayName,
                backDescription = "返回核心设定",
                onBack = ::showCurrentCoreDocumentList,
            ), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(64)))
            addView(settingsMessage(message), LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f,
            ))
        }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    private fun returnFromCoreDocumentEditor() {
        val snapshot = coreDocumentEditorSnapshot
        val current = coreDocumentEditorInput?.text?.toString()
        if (snapshot == null || current == null || current == snapshot.content) {
            showCurrentCoreDocumentList()
            return
        }
        showAgentDialogCard(
            title = "放弃未保存的修改？",
            message = "返回后，这次还没有写入 Agent 原生文件的内容会丢失。",
            actions = listOf(
                AgentDialogAction("继续编辑", UiActionRole.Secondary) { dialog, _ -> dialog.dismiss() },
                AgentDialogAction("放弃", UiActionRole.Danger) { dialog, _ ->
                    dialog.dismiss()
                    showCurrentCoreDocumentList()
                },
            ),
        )
    }

    private fun showCurrentCoreDocumentList() {
        val selected = settingsRegistrySnapshot?.entry(coreDocumentPageAgentId.orEmpty())
        val adapter = coreDocumentPageAdapter
        if (selected == null || adapter == null) {
            returnToAgentSettings()
            return
        }
        showCoreDocumentManager(selected, adapter, reload = false)
    }

    private fun showProviderManager(
        selected: AgentRegistryEntry,
        adapter: AgentConfigAdapter,
        snapshot: AgentLiveConfigSnapshot
    ) {
        providerPageAgentId = selected.registration.definition.agentId
        providerPageAdapter = adapter
        providerPageSnapshot = snapshot
        navigationScreen = AgentNavigationScreen.ProviderList
        navigationHost.removeAllViews()
        navigationHost.addView(
            buildProviderListPage(selected, adapter, snapshot),
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
        navigationHost.visibility = View.VISIBLE
    }

    private fun showMcpManager(
        selected: AgentRegistryEntry,
        adapter: AgentConfigAdapter,
        snapshot: AgentLiveConfigSnapshot,
    ) {
        val targetAgentId = selected.registration.definition.agentId
        mcpPageAgentId = targetAgentId
        mcpPageAdapter = adapter
        mcpPageSnapshot = snapshot
        mcpConnectionStates.keys.retainAll(snapshot.mcpServers.map(AgentMcpSummary::id).toSet())
        mcpConnectionMessages.keys.retainAll(snapshot.mcpServers.map(AgentMcpSummary::id).toSet())
        navigationScreen = AgentNavigationScreen.McpList
        navigationHost.removeAllViews()
        navigationHost.addView(
            buildMcpListPage(selected, adapter),
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        navigationHost.visibility = View.VISIBLE
        renderMcpSnapshot(snapshot)
    }

    private fun buildMcpListPage(
        selected: AgentRegistryEntry,
        adapter: AgentConfigAdapter,
    ): View {
        val targetAgentId = selected.registration.definition.agentId
        val listAdapter = AgentMcpListAdapter(
            context = context,
            tokens = tokens,
            onClick = { item -> showMcpActions(selected, adapter, item.server) },
            onConnectionCheck = { item -> checkMcpConnection(selected, adapter, item.server) },
        ).also { mcpPageListAdapter = it }
        val status = TextView(context).apply {
            textSize = 13.5f
            gravity = Gravity.CENTER
            setTextColor(tokens.textSecondary)
            setPadding(ui.dp(24), ui.dp(40), ui.dp(24), ui.dp(40))
        }.also { mcpPageStatusText = it }
        val mcpList = RecyclerView(context).apply {
            layoutManager = LinearLayoutManager(context)
            this.adapter = listAdapter
            itemAnimator = null
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            clipToPadding = false
            setPadding(ui.dp(14), ui.dp(2), ui.dp(14), ui.dp(18))
        }.also { mcpPageListView = it }
        val viewport = FrameLayout(context).apply {
            addView(mcpList, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ))
            addView(status, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER,
            ))
        }
        val canCreate = AgentMcpOperation.Create in adapter.capabilities().mcpOperations &&
            adapter.capabilities().mcpTransports.isNotEmpty()
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(agentPageBackground)
            addView(buildAgentSubpageHeader(
                title = "MCP",
                backDescription = "返回 Agent 设置",
                onBack = ::returnToAgentSettings,
                actionIcon = R.drawable.ic_add_light.takeIf { canCreate },
                actionDescription = "新建 MCP",
                onAction = if (canCreate) {
                    { mcpPageSnapshot?.let { current -> showMcpEditor(selected, adapter, current, null) } }
                } else null,
            ), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(64)))
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(ui.dp(22), ui.dp(7), ui.dp(22), ui.dp(12))
                addView(TextView(context).apply {
                    text = selected.registration.definition.displayName
                    textSize = 12.5f
                    setTextColor(tokens.textSecondary)
                    contentDescription = "$targetAgentId 的 MCP"
                })
                addView(TextView(context).apply {
                    text = if (AgentMcpOperation.CheckConnection in adapter.capabilities().mcpOperations) {
                        "保存只更新 Agent 原生配置；当前实例不承诺热加载。支持的项目可单独检查连接。"
                    } else {
                        "保存只更新 Agent 原生配置；连接和当前实例加载状态由 Agent 自身确认。"
                    }
                    textSize = 11.5f
                    setLineSpacing(ui.dp(2).toFloat(), 1f)
                    setTextColor(tokens.textTertiary)
                    setPadding(0, ui.dp(4), 0, 0)
                })
            })
            addView(viewport, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f,
            ))
        }
    }

    private fun renderMcpSnapshot(
        snapshot: AgentLiveConfigSnapshot,
        pendingServerId: String? = null,
        message: String? = null,
    ) {
        mcpPageSnapshot = snapshot
        val items = snapshot.mcpServers.map { server ->
            AgentMcpListItem(
                server = server,
                connectionState = mcpConnectionStates[server.id] ?: server.connectionState,
                connectionMessage = mcpConnectionMessages[server.id],
                pending = pendingServerId == server.id,
            )
        }
        mcpPageListAdapter?.submitList(items)
        mcpPageListView?.visibility = if (items.isEmpty()) View.GONE else View.VISIBLE
        mcpPageStatusText?.apply {
            text = message ?: "尚未配置 MCP"
            visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun showMcpActions(
        selected: AgentRegistryEntry,
        adapter: AgentConfigAdapter,
        server: AgentMcpSummary,
    ) {
        val actions = buildList {
            if (AgentMcpOperation.Edit in server.allowedOperations) {
                add(AgentChoiceAction("编辑") {
                    mcpPageSnapshot?.let { current -> showMcpEditor(selected, adapter, current, server) }
                })
            }
            if (server.enabled && AgentMcpOperation.Disable in server.allowedOperations) {
                add(AgentChoiceAction("停用") {
                    applyMcpChange(
                        selected,
                        adapter,
                        server.id,
                        AgentPersistentConfigChange.SetMcpEnabled(server.id, false),
                        "已停用 ${server.id}",
                    )
                })
            } else if (!server.enabled && AgentMcpOperation.Enable in server.allowedOperations) {
                add(AgentChoiceAction("启用") {
                    applyMcpChange(
                        selected,
                        adapter,
                        server.id,
                        AgentPersistentConfigChange.SetMcpEnabled(server.id, true),
                        "已启用 ${server.id}",
                    )
                })
            }
            if (server.enabled && AgentMcpOperation.CheckConnection in server.allowedOperations) {
                add(AgentChoiceAction("检查连接") { checkMcpConnection(selected, adapter, server) })
            }
            if (AgentMcpOperation.Remove in server.allowedOperations) {
                add(AgentChoiceAction(
                    label = "移除",
                    role = UiActionRole.Danger,
                    onClick = { showMcpRemoveConfirmation(selected, adapter, server) },
                ))
            }
        }
        if (actions.isEmpty()) {
            showAgentDialogCard(
                title = server.id,
                message = "这个 MCP 由 Agent 原生配置管理，当前没有可在 Kite 中执行的操作。",
                actions = listOf(
                    AgentDialogAction("知道了", UiActionRole.Secondary) { dialog, _ -> dialog.dismiss() },
                ),
            )
            return
        }
        val state = mcpConnectionStates[server.id] ?: server.connectionState
        showAgentChoiceCard(
            title = server.id,
            message = "${AgentMcpUiPolicy.transportLabel(server)} · ${AgentMcpUiPolicy.connectionLabel(server, state)}",
            actions = actions,
        )
    }

    private fun checkMcpConnection(
        selected: AgentRegistryEntry,
        adapter: AgentConfigAdapter,
        server: AgentMcpSummary,
    ) {
        val targetAgentId = selected.registration.definition.agentId
        val requestRevision = ++settingsLoadRevision
        mcpConnectionStates[server.id] = AgentMcpConnectionState.Checking
        mcpConnectionMessages.remove(server.id)
        mcpPageSnapshot?.let { renderMcpSnapshot(it, pendingServerId = server.id) }
        navigationJob?.cancel()
        navigationJob = lifecycleOwner.lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { adapter.checkMcpServer(targetAgentId, server.id) }
            if (requestRevision != settingsLoadRevision ||
                navigationScreen != AgentNavigationScreen.McpList ||
                mcpPageAgentId != targetAgentId
            ) return@launch
            when (result) {
                is AgentMcpConnectionCheckResult.Available -> {
                    mcpConnectionStates[server.id] = AgentMcpConnectionState.Available
                    mcpConnectionMessages[server.id] = result.message ?: "连接可用"
                }
                is AgentMcpConnectionCheckResult.Unavailable -> {
                    mcpConnectionStates[server.id] = AgentMcpConnectionState.Unavailable
                    mcpConnectionMessages[server.id] = result.message
                }
                is AgentMcpConnectionCheckResult.Unsupported -> {
                    mcpConnectionStates[server.id] = AgentMcpConnectionState.NotChecked
                    mcpConnectionMessages[server.id] = result.message
                }
            }
            mcpPageSnapshot?.let(::renderMcpSnapshot)
        }
    }

    private fun applyMcpChange(
        selected: AgentRegistryEntry,
        adapter: AgentConfigAdapter,
        serverId: String,
        change: AgentPersistentConfigChange,
        successMessage: String,
    ) {
        val snapshot = mcpPageSnapshot ?: return
        val targetAgentId = selected.registration.definition.agentId
        val requestRevision = ++settingsLoadRevision
        renderMcpSnapshot(snapshot, pendingServerId = serverId)
        mcpEditorSaveAction?.apply { isEnabled = false; alpha = 0.45f }
        navigationJob?.cancel()
        navigationJob = lifecycleOwner.lifecycleScope.launch {
            val outcome = withContext(Dispatchers.IO) {
                val result = adapter.apply(
                    AgentConfigApplyRequest(targetAgentId, snapshot.revision, listOf(change)),
                )
                val refreshed = when (result) {
                    is AgentConfigApplyResult.Applied -> AgentConfigReadResult.Ready(result.snapshot)
                    else -> adapter.backfill(targetAgentId)
                }
                result to refreshed
            }
            if (requestRevision != settingsLoadRevision || selectedSettingsAgentId != targetAgentId) return@launch
            persistentConfigAgentId = targetAgentId
            persistentConfigResult = outcome.second
            val refreshedSnapshot = (outcome.second as? AgentConfigReadResult.Ready)?.snapshot
                ?: (outcome.first as? AgentConfigApplyResult.Applied)?.snapshot
                ?: snapshot
            if (outcome.first is AgentConfigApplyResult.Applied) {
                mcpConnectionStates.remove(serverId)
                mcpConnectionMessages.remove(serverId)
                mcpEditorSaveAction = null
                mcpEditorStatusText = null
                showMcpManager(selected, adapter, refreshedSnapshot)
                Toast.makeText(context, successMessage, Toast.LENGTH_SHORT).show()
            } else {
                mcpPageSnapshot = refreshedSnapshot
                mcpEditorSaveAction?.apply { isEnabled = true; alpha = 1f }
                mcpEditorStatusText?.apply {
                    text = outcome.first.userMessage(successMessage)
                    setTextColor(android.graphics.Color.rgb(198, 40, 40))
                    visibility = View.VISIBLE
                }
                if (navigationScreen == AgentNavigationScreen.McpList) renderMcpSnapshot(refreshedSnapshot)
                Toast.makeText(context, outcome.first.userMessage(successMessage), Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showMcpRemoveConfirmation(
        selected: AgentRegistryEntry,
        adapter: AgentConfigAdapter,
        server: AgentMcpSummary,
    ) {
        showAgentDialogCard(
            title = "移除 ${server.id}？",
            message = "只会从当前 Agent 的原生配置中移除这个 MCP；不会删除它指向的程序、服务或用户文件。",
            actions = listOf(
                AgentDialogAction("取消", UiActionRole.Secondary) { dialog, _ -> dialog.dismiss() },
                AgentDialogAction("移除", UiActionRole.Danger) { dialog, _ ->
                    dialog.dismiss()
                    applyMcpChange(
                        selected,
                        adapter,
                        server.id,
                        AgentPersistentConfigChange.RemoveMcpServer(server.id),
                        "已移除 ${server.id}",
                    )
                },
            ),
        )
    }

    private fun showCurrentMcpList() {
        val targetAgentId = mcpPageAgentId ?: selectedSettingsAgentId
        val selected = settingsRegistrySnapshot?.entry(targetAgentId.orEmpty())
        val adapter = mcpPageAdapter
        val snapshot = mcpPageSnapshot
        if (selected == null || adapter == null || snapshot == null) {
            returnToAgentSettings()
            return
        }
        mcpEditorSaveAction = null
        mcpEditorStatusText = null
        showMcpManager(selected, adapter, snapshot)
    }

    private fun showMcpEditor(
        selected: AgentRegistryEntry,
        adapter: AgentConfigAdapter,
        snapshot: AgentLiveConfigSnapshot,
        existing: AgentMcpSummary?,
    ) {
        mcpPageAgentId = selected.registration.definition.agentId
        mcpPageAdapter = adapter
        mcpPageSnapshot = snapshot
        navigationScreen = AgentNavigationScreen.McpEditor
        navigationHost.removeAllViews()
        navigationHost.addView(
            buildMcpEditorPage(selected, adapter, existing),
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        navigationHost.visibility = View.VISIBLE
    }

    private fun buildMcpEditorPage(
        selected: AgentRegistryEntry,
        adapter: AgentConfigAdapter,
        existing: AgentMcpSummary?,
    ): View {
        val supportedTransports = adapter.capabilities().mcpTransports
        var transport = existing?.transport?.takeIf(supportedTransports::contains)
            ?: supportedTransports.firstOrNull()
            ?: AgentMcpTransport.Unknown
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ui.dp(16), ui.dp(10), ui.dp(16), ui.dp(30))
        }
        val status = TextView(context).apply {
            textSize = 12.5f
            setTextColor(android.graphics.Color.rgb(198, 40, 40))
            visibility = View.GONE
            setPadding(ui.dp(2), 0, ui.dp(2), ui.dp(10))
        }.also { mcpEditorStatusText = it }
        content.addView(status)

        content.addView(sectionTitle("连接方式", "OpenCode 会为远程地址自动尝试 HTTP 与 SSE。"))
        val transportRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val transportButtons = linkedMapOf<AgentMcpTransport, TextView>()
        supportedTransports.forEach { candidate ->
            val label = when (candidate) {
                AgentMcpTransport.Stdio -> "本地命令"
                AgentMcpTransport.RemoteHttpOrSse -> "远程地址"
                AgentMcpTransport.StreamableHttp -> "HTTP"
                AgentMcpTransport.Sse -> "SSE"
                AgentMcpTransport.Unknown -> "其他"
            }
            val button = TextView(context).apply {
                text = label
                textSize = 14f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setTextColor(tokens.textPrimary)
                isClickable = true
                isFocusable = true
            }
            transportButtons[candidate] = button
            transportRow.addView(button, LinearLayout.LayoutParams(0, ui.dp(48), 1f).apply {
                setMargins(if (transportRow.childCount == 0) 0 else ui.dp(4), 0, 0, 0)
            })
        }
        content.addView(transportRow, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { setMargins(0, 0, 0, ui.dp(18)) })

        val idInput = providerEditorField(
            content,
            label = "MCP ID",
            hintText = "例如 github",
            value = existing?.id.orEmpty(),
        ).apply { isEnabled = existing == null }
        val localFields = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val commandInput = providerEditorField(
            localFields,
            label = "命令",
            hintText = "例如 npx",
            value = existing?.command.orEmpty(),
        )
        val argumentsInput = mcpEditorMultilineField(
            localFields,
            label = "参数",
            hintText = "每行一个参数",
            value = existing?.arguments.orEmpty().joinToString("\n"),
        )
        val remoteFields = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val urlInput = providerEditorField(
            remoteFields,
            label = "请求地址",
            hintText = "https://example.com/mcp",
            value = existing?.url.orEmpty(),
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI,
        )
        content.addView(localFields)
        content.addView(remoteFields)

        val advancedContent = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }
        val localReferences = mcpEditorMultilineField(
            advancedContent,
            label = "环境变量引用",
            hintText = "名称=环境变量名，每行一项",
            value = AgentMcpEditorPolicy.referencesText(existing?.environmentReferences.orEmpty()),
        )
        val remoteReferences = mcpEditorMultilineField(
            advancedContent,
            label = "Header 引用",
            hintText = "Header名称=环境变量名，每行一项",
            value = AgentMcpEditorPolicy.referencesText(existing?.headerReferences.orEmpty()),
        )
        advancedContent.addView(TextView(context).apply {
            text = "这里只保存环境变量名称，不读取或显示 Header 与环境变量真值。未在此页面管理的 Agent 原生字段会保持不变。"
            textSize = 12f
            setTextColor(tokens.textSecondary)
            setPadding(ui.dp(2), 0, ui.dp(2), ui.dp(18))
        })
        val advancedChevron = ImageView(context).apply {
            setImageResource(R.drawable.ic_chevron_right_light)
            imageTintList = ColorStateList.valueOf(tokens.textSecondary)
            setPadding(ui.dp(12), ui.dp(12), ui.dp(12), ui.dp(12))
        }
        content.addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(ui.dp(4), ui.dp(4), ui.dp(4), ui.dp(4))
            addView(TextView(context).apply {
                text = "高级设置"
                textSize = 15f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(tokens.textPrimary)
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(advancedChevron, LinearLayout.LayoutParams(ui.dp(48), ui.dp(48)))
            isClickable = true
            isFocusable = true
            setOnClickListener {
                advancedContent.visibility = if (advancedContent.visibility == View.VISIBLE) View.GONE else View.VISIBLE
                advancedChevron.rotation = if (advancedContent.visibility == View.VISIBLE) 90f else 0f
            }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(56)))
        content.addView(advancedContent)

        fun renderTransport() {
            transportButtons.forEach { (candidate, button) ->
                val selected = candidate == transport
                button.background = ui.roundedBox(
                    if (selected) agentSettingsSurface else android.graphics.Color.TRANSPARENT,
                    if (selected) tokens.border else android.graphics.Color.TRANSPARENT,
                    ui.dp(18).toFloat(),
                    if (selected) ui.dp(1) else 0,
                )
                button.alpha = if (selected) 1f else 0.7f
            }
            val local = transport == AgentMcpTransport.Stdio
            localFields.visibility = if (local) View.VISIBLE else View.GONE
            remoteFields.visibility = if (local) View.GONE else View.VISIBLE
            localReferences.visibility = if (local) View.VISIBLE else View.GONE
            remoteReferences.visibility = if (local) View.GONE else View.VISIBLE
        }
        transportButtons.forEach { (candidate, button) ->
            button.setOnClickListener { transport = candidate; renderTransport() }
        }
        renderTransport()

        val saveAction = TextView(context).apply {
            text = "保存"
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(tokens.primaryStrong)
            contentDescription = if (existing == null) "保存新 MCP" else "保存 MCP"
            isClickable = true
            isFocusable = true
            setOnClickListener {
                val result = AgentMcpEditorPolicy.buildDraft(
                    id = idInput.text?.toString().orEmpty(),
                    transport = transport,
                    enabled = existing?.enabled ?: true,
                    command = commandInput.text?.toString().orEmpty(),
                    argumentsText = argumentsInput.text?.toString().orEmpty(),
                    url = urlInput.text?.toString().orEmpty(),
                    referencesText = if (transport == AgentMcpTransport.Stdio) {
                        localReferences.text?.toString().orEmpty()
                    } else {
                        remoteReferences.text?.toString().orEmpty()
                    },
                )
                when (result) {
                    is AgentMcpDraftBuildResult.Invalid -> {
                        status.text = result.message
                        status.setTextColor(android.graphics.Color.rgb(198, 40, 40))
                        status.visibility = View.VISIBLE
                    }
                    is AgentMcpDraftBuildResult.Ready -> {
                        status.text = "正在安全写入 Agent 原生配置…"
                        status.setTextColor(tokens.textSecondary)
                        status.visibility = View.VISIBLE
                        isEnabled = false
                        alpha = 0.45f
                        mcpEditorSaveAction = this
                        applyMcpChange(
                            selected,
                            adapter,
                            result.draft.id,
                            AgentPersistentConfigChange.ConfigureMcpServer(result.draft),
                            "MCP 配置已保存，尚未检查连接",
                        )
                    }
                }
            }
        }.also { mcpEditorSaveAction = it }

        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(agentPageBackground)
            addView(buildAgentSubpageHeader(
                title = if (existing == null) "新建 MCP" else "编辑 MCP",
                backDescription = "返回 MCP",
                onBack = ::showCurrentMcpList,
                trailingView = saveAction,
            ), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(64)))
            addView(ScrollView(context).apply {
                isFillViewport = true
                overScrollMode = View.OVER_SCROLL_NEVER
                addView(content, ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ))
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        }
    }

    private fun mcpEditorMultilineField(
        host: LinearLayout,
        label: String,
        hintText: String,
        value: String,
    ): EditText {
        val field = EditText(context).apply {
            hint = hintText
            setText(value)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            gravity = Gravity.TOP or Gravity.START
            textSize = 14.5f
            setTextColor(tokens.textPrimary)
            setHintTextColor(tokens.textTertiary)
            minLines = 3
            maxLines = 7
            setPadding(ui.dp(15), ui.dp(13), ui.dp(15), ui.dp(13))
            background = ui.roundedBox(agentSettingsSurface, tokens.border, ui.dp(18).toFloat(), ui.dp(1))
        }
        host.addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(context).apply {
                text = label
                textSize = 13f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(tokens.textPrimary)
                setPadding(ui.dp(2), 0, 0, ui.dp(7))
            })
            addView(field, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            setMargins(0, 0, 0, ui.dp(14))
        })
        return field
    }

    private fun showSkillManager(
        selected: AgentRegistryEntry,
        adapter: AgentConfigAdapter,
        snapshot: AgentLiveConfigSnapshot,
    ) {
        skillPageAgentId = selected.registration.definition.agentId
        skillPageAdapter = adapter
        skillPageSnapshot = snapshot
        navigationScreen = AgentNavigationScreen.SkillList
        navigationHost.removeAllViews()
        navigationHost.addView(
            buildSkillListPage(selected, adapter),
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        navigationHost.visibility = View.VISIBLE
        renderSkillSnapshot(snapshot)
    }

    private fun buildSkillListPage(
        selected: AgentRegistryEntry,
        adapter: AgentConfigAdapter,
    ): View {
        val targetAgentId = selected.registration.definition.agentId
        val listAdapter = AgentSkillListAdapter(
            context = context,
            tokens = tokens,
            onClick = { skill -> showSkillActions(selected, adapter, skill) },
        ).also { skillPageListAdapter = it }
        val status = TextView(context).apply {
            textSize = 13.5f
            gravity = Gravity.CENTER
            setTextColor(tokens.textSecondary)
            setPadding(ui.dp(24), ui.dp(40), ui.dp(24), ui.dp(40))
        }.also { skillPageStatusText = it }
        val skillList = RecyclerView(context).apply {
            layoutManager = LinearLayoutManager(context)
            this.adapter = listAdapter
            itemAnimator = null
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            clipToPadding = false
            setPadding(ui.dp(14), ui.dp(2), ui.dp(14), ui.dp(18))
        }.also { skillPageListView = it }
        val viewport = FrameLayout(context).apply {
            addView(skillList, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ))
            addView(status, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER,
            ))
        }
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(agentPageBackground)
            addView(buildAgentSubpageHeader(
                title = "Skill",
                backDescription = "返回 Agent 设置",
                onBack = ::returnToAgentSettings,
                actionIcon = R.drawable.ic_add_light.takeIf {
                    AgentSkillOperation.Import in adapter.capabilities().skillOperations
                },
                actionDescription = "导入 Skill",
                onAction = if (AgentSkillOperation.Import in adapter.capabilities().skillOperations) {
                    { showSkillImportPicker(selected, adapter) }
                } else null,
            ), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(64)))
            addView(TextView(context).apply {
                text = selected.registration.definition.displayName
                textSize = 12.5f
                setTextColor(tokens.textSecondary)
                setPadding(ui.dp(22), ui.dp(7), ui.dp(22), ui.dp(12))
                contentDescription = "$targetAgentId 的 Skill"
            })
            addView(viewport, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f,
            ))
        }
    }

    private fun renderSkillSnapshot(
        snapshot: AgentLiveConfigSnapshot,
        pendingSkillId: String? = null,
        message: String? = null,
    ) {
        skillPageSnapshot = snapshot
        skillPageListAdapter?.submit(snapshot.skills, pendingSkillId)
        skillPageListView?.visibility = if (snapshot.skills.isEmpty()) View.GONE else View.VISIBLE
        skillPageStatusText?.apply {
            text = message ?: "尚未安装 Skill"
            visibility = if (snapshot.skills.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun showSkillActions(
        selected: AgentRegistryEntry,
        adapter: AgentConfigAdapter,
        skill: AgentSkillSummary,
    ) {
        val actions = buildList {
            if (AgentSkillOperation.Enable in skill.allowedOperations ||
                skill.activation == AgentSkillActivation.Enabled
            ) {
                add(AgentChoiceAction(
                    label = "启用",
                    selected = skill.activation == AgentSkillActivation.Enabled,
                    onClick = {
                        applySkillChange(selected, adapter, skill,
                            AgentPersistentConfigChange.SetSkillActivation(
                                skill.id,
                                AgentSkillActivation.Enabled,
                            ), "已启用 ${skill.displayName}")
                    },
                ))
            }
            if (AgentSkillOperation.RequireApproval in skill.allowedOperations ||
                skill.activation == AgentSkillActivation.ApprovalRequired
            ) {
                add(AgentChoiceAction(
                    label = "每次确认",
                    selected = skill.activation == AgentSkillActivation.ApprovalRequired,
                    onClick = {
                        applySkillChange(selected, adapter, skill,
                            AgentPersistentConfigChange.SetSkillActivation(
                                skill.id,
                                AgentSkillActivation.ApprovalRequired,
                            ), "${skill.displayName} 已设为每次确认")
                    },
                ))
            }
            if (AgentSkillOperation.ManualOnly in skill.allowedOperations ||
                skill.activation == AgentSkillActivation.ManualOnly
            ) {
                add(AgentChoiceAction(
                    label = "仅手动",
                    selected = skill.activation == AgentSkillActivation.ManualOnly,
                    onClick = {
                        applySkillChange(selected, adapter, skill,
                            AgentPersistentConfigChange.SetSkillActivation(
                                skill.id,
                                AgentSkillActivation.ManualOnly,
                            ), "${skill.displayName} 已设为仅手动")
                    },
                ))
            }
            if (AgentSkillOperation.Disable in skill.allowedOperations ||
                skill.activation == AgentSkillActivation.Disabled
            ) {
                add(AgentChoiceAction(
                    label = "停用",
                    selected = skill.activation == AgentSkillActivation.Disabled,
                    onClick = {
                        applySkillChange(selected, adapter, skill,
                            AgentPersistentConfigChange.SetSkillActivation(
                                skill.id,
                                AgentSkillActivation.Disabled,
                            ), "已停用 ${skill.displayName}")
                    },
                ))
            }
            if (AgentSkillOperation.Remove in skill.allowedOperations) {
                add(AgentChoiceAction(
                    label = "移除",
                    role = UiActionRole.Danger,
                    onClick = { showSkillRemoveConfirmation(selected, adapter, skill) },
                ))
            }
        }
        if (actions.isEmpty()) {
            showAgentDialogCard(
                title = skill.displayName,
                message = "这个 Skill 由 Agent 原生位置管理，当前没有可在 Kite 中执行的操作。",
                actions = listOf(
                    AgentDialogAction("知道了", UiActionRole.Secondary) { dialog, _ -> dialog.dismiss() },
                ),
            )
            return
        }
        showAgentChoiceCard(
            title = skill.displayName,
            message = AgentSkillUiPolicy.summary(skill),
            actions = actions,
        )
    }

    private fun applySkillChange(
        selected: AgentRegistryEntry,
        adapter: AgentConfigAdapter,
        skill: AgentSkillSummary,
        change: AgentPersistentConfigChange,
        successMessage: String,
    ) {
        val snapshot = skillPageSnapshot ?: return
        val targetAgentId = selected.registration.definition.agentId
        if (targetAgentId != skillPageAgentId) return
        val requestRevision = ++settingsLoadRevision
        renderSkillSnapshot(snapshot, pendingSkillId = skill.id)
        navigationJob?.cancel()
        navigationJob = lifecycleOwner.lifecycleScope.launch {
            val outcome = withContext(Dispatchers.IO) {
                val result = adapter.apply(
                    AgentConfigApplyRequest(targetAgentId, snapshot.revision, listOf(change)),
                )
                val refreshed = when (result) {
                    is AgentConfigApplyResult.Applied -> AgentConfigReadResult.Ready(result.snapshot)
                    else -> adapter.backfill(targetAgentId)
                }
                result to refreshed
            }
            if (requestRevision != settingsLoadRevision || selectedSettingsAgentId != targetAgentId) return@launch
            consumeSkillApplyOutcome(targetAgentId, outcome.first, outcome.second, successMessage)
        }
    }

    private fun consumeSkillApplyOutcome(
        targetAgentId: String,
        applyResult: AgentConfigApplyResult,
        refreshed: AgentConfigReadResult,
        successMessage: String,
    ) {
        persistentConfigAgentId = targetAgentId
        persistentConfigResult = refreshed
        val refreshedSnapshot = (refreshed as? AgentConfigReadResult.Ready)?.snapshot
        val visibleSnapshot = refreshedSnapshot
            ?: (applyResult as? AgentConfigApplyResult.Applied)?.snapshot
            ?: skillPageSnapshot
        if (visibleSnapshot != null) renderSkillSnapshot(visibleSnapshot)
        if (applyResult is AgentConfigApplyResult.Applied) {
            Toast.makeText(context, successMessage, Toast.LENGTH_SHORT).show()
        } else {
            val message = applyResult.userMessage(successMessage)
            if (visibleSnapshot != null) {
                renderSkillSnapshot(visibleSnapshot, message = message)
                if (visibleSnapshot.skills.isNotEmpty()) {
                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                }
            }
            else skillPageStatusText?.apply { text = message; visibility = View.VISIBLE }
        }
    }

    private fun showSkillRemoveConfirmation(
        selected: AgentRegistryEntry,
        adapter: AgentConfigAdapter,
        skill: AgentSkillSummary,
    ) {
        showAgentDialogCard(
            title = "移除 ${skill.displayName}？",
            message = "Kite 会先把这个 Skill 移入 Agent 的备份目录；工作区中的原始导入文件不会被删除。",
            actions = listOf(
                AgentDialogAction("取消", UiActionRole.Secondary) { dialog, _ -> dialog.dismiss() },
                AgentDialogAction("移除", UiActionRole.Danger) { dialog, _ ->
                    dialog.dismiss()
                    applySkillChange(
                        selected,
                        adapter,
                        skill,
                        AgentPersistentConfigChange.RemoveSkill(skill.id),
                        "已移除 ${skill.displayName}",
                    )
                },
            ),
        )
    }

    private fun showSkillImportPicker(
        selected: AgentRegistryEntry,
        adapter: AgentConfigAdapter,
    ) {
        skillDirectoryPickerDialog?.dismiss()
        skillDirectoryPickerDialog = WorkspaceDirectoryPickerDialog(
            context = context,
            tokens = tokens,
            scope = lifecycleOwner.lifecycleScope,
            hostWorkspaceRoot = KFContainerManager.resolveWorkspaceDirectory(context),
            initialContainerPath = KiteStorageContract.CONTAINER_WORKSPACE_ROOT,
            pageTitle = "导入 Skill",
            selectionLabel = "Skill 文件夹",
            actionLabel = "导入",
            allowCreateDirectory = false,
            onSelected = { selectedPath ->
                skillDirectoryPickerDialog = null
                importSkill(selected, adapter, selectedPath)
            },
        ).also(WorkspaceDirectoryPickerDialog::show)
    }

    private fun importSkill(
        selected: AgentRegistryEntry,
        adapter: AgentConfigAdapter,
        selectedPath: String,
    ) {
        val snapshot = skillPageSnapshot ?: return
        val targetAgentId = selected.registration.definition.agentId
        val requestRevision = ++settingsLoadRevision
        skillPageStatusText?.apply {
            text = "正在检查并导入 Skill…"
            visibility = View.VISIBLE
        }
        skillPageListView?.visibility = View.GONE
        navigationJob?.cancel()
        navigationJob = lifecycleOwner.lifecycleScope.launch {
            val outcome = withContext(Dispatchers.IO) {
                runCatching {
                    val stage = AgentSkillImportStager(
                        KFContainerManager.resolveWorkspaceDirectory(context),
                    ).stage(selectedPath)
                    try {
                        val result = adapter.apply(
                            AgentConfigApplyRequest(
                                targetAgentId,
                                snapshot.revision,
                                listOf(AgentPersistentConfigChange.InstallSkill(
                                    stage.skillId,
                                    stage.sourceReference,
                                )),
                            ),
                        )
                        val refreshed = when (result) {
                            is AgentConfigApplyResult.Applied -> AgentConfigReadResult.Ready(result.snapshot)
                            else -> adapter.backfill(targetAgentId)
                        }
                        SkillImportOutcome(result, refreshed, null)
                    } finally {
                        stage.discard()
                    }
                }.getOrElse { error ->
                    SkillImportOutcome(null, null, error.message ?: "无法导入这个 Skill")
                }
            }
            if (requestRevision != settingsLoadRevision || selectedSettingsAgentId != targetAgentId) return@launch
            if (outcome.applyResult != null && outcome.refreshed != null) {
                consumeSkillApplyOutcome(
                    targetAgentId,
                    outcome.applyResult,
                    outcome.refreshed,
                    "Skill 已导入",
                )
            } else {
                val message = outcome.errorMessage ?: "无法导入这个 Skill"
                renderSkillSnapshot(snapshot, message = message)
                if (snapshot.skills.isNotEmpty()) Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showCurrentProviderList() {
        val targetAgentId = providerPageAgentId ?: selectedSettingsAgentId
        val selected = settingsRegistrySnapshot?.entry(targetAgentId.orEmpty())
        val adapter = providerPageAdapter
        val snapshot = providerPageSnapshot
        if (selected == null || adapter == null || snapshot == null) {
            returnToAgentSettings()
            return
        }
        showProviderManager(selected, adapter, snapshot)
    }

    private fun buildAgentSubpageHeader(
        title: String,
        backDescription: String,
        onBack: () -> Unit,
        actionIcon: Int? = null,
        actionDescription: String? = null,
        onAction: (() -> Unit)? = null,
        trailingView: View? = null
    ): View = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(ui.dp(14), ui.dp(10), ui.dp(14), ui.dp(6))
        addView(
            iconButton(context, R.drawable.ic_arrow_back_light, backDescription, onBack),
            LinearLayout.LayoutParams(ui.dp(48), ui.dp(48))
        )
        addView(TextView(context).apply {
            text = title
            textSize = 19f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setTextColor(tokens.textPrimary)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        when {
            trailingView != null -> addView(trailingView, LinearLayout.LayoutParams(ui.dp(56), ui.dp(48)))
            actionIcon != null && onAction != null -> addView(
                iconButton(context, actionIcon, actionDescription ?: title, onAction),
                LinearLayout.LayoutParams(ui.dp(48), ui.dp(48))
            )
            else -> addView(View(context), LinearLayout.LayoutParams(ui.dp(48), ui.dp(48)))
        }
    }

    private fun actionTextButton(label: String, onClick: () -> Unit): TextView = TextView(context).apply {
        text = label
        textSize = 15f
        typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER
        setTextColor(tokens.buttonText)
        background = ui.roundedBox(tokens.primaryStrong, tokens.primaryStrong, ui.dp(18).toFloat())
        isClickable = true
        isFocusable = true
        setOnClickListener { onClick() }
    }

    private fun actionOutlineButton(label: String, onClick: () -> Unit): TextView = TextView(context).apply {
        text = label
        textSize = 14.5f
        typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER
        setTextColor(tokens.textPrimary)
        background = ui.roundedBox(agentPageBackground, tokens.borderStrong, ui.dp(16).toFloat(), ui.dp(1))
        isClickable = true
        isFocusable = true
        setOnClickListener { onClick() }
    }

    private fun actionDangerButton(label: String, onClick: () -> Unit): TextView = TextView(context).apply {
        text = label
        textSize = 14.5f
        typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER
        setTextColor(tokens.danger)
        background = ui.roundedBox(tokens.dangerSoft, tokens.dangerBorder, ui.dp(16).toFloat(), ui.dp(1))
        isClickable = true
        isFocusable = true
        setOnClickListener { onClick() }
    }

    private fun sectionTitle(title: String, subtitle: String): View = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        addView(TextView(context).apply {
            text = title
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(tokens.textPrimary)
        })
        addView(TextView(context).apply {
            text = subtitle
            textSize = 12.5f
            setTextColor(tokens.textSecondary)
            setPadding(0, ui.dp(5), 0, 0)
        })
    }.also { view ->
        view.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(0, ui.dp(4), 0, ui.dp(12)) }
    }

    private fun providerPresetSelectionRow(valueText: TextView, onClick: () -> Unit): View =
        LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = ui.dp(68)
            setPadding(ui.dp(16), ui.dp(10), ui.dp(8), ui.dp(10))
            background = ui.roundedBox(agentSettingsSurface, android.graphics.Color.TRANSPARENT, ui.dp(20).toFloat())
            addView(ImageView(context).apply {
                setImageResource(R.drawable.ic_bridge)
                imageTintList = ColorStateList.valueOf(tokens.textPrimary)
                setPadding(ui.dp(9), ui.dp(9), ui.dp(9), ui.dp(9))
            }, LinearLayout.LayoutParams(ui.dp(42), ui.dp(42)))
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(TextView(context).apply {
                    text = "供应商预设"
                    textSize = 15f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(tokens.textPrimary)
                })
                addView(valueText.apply {
                    textSize = 12.5f
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                    setTextColor(tokens.textSecondary)
                    setPadding(0, ui.dp(3), 0, 0)
                })
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(ui.dp(6), 0, ui.dp(4), 0)
            })
            addView(ImageView(context).apply {
                setImageResource(R.drawable.ic_chevron_right_light)
                imageTintList = ColorStateList.valueOf(tokens.textSecondary)
                setPadding(ui.dp(11), ui.dp(11), ui.dp(11), ui.dp(11))
            }, LinearLayout.LayoutParams(ui.dp(44), ui.dp(44)))
            contentDescription = "选择供应商预设"
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
        }

    private fun showProviderPresetPicker(onSelected: (AgentProviderPreset?) -> Unit) {
        val grid = GridLayout(context).apply {
            columnCount = 2
            alignmentMode = GridLayout.ALIGN_BOUNDS
            useDefaultMargins = false
        }
        val search = EditText(context).apply {
            hint = "搜索供应商"
            setSingleLine(true)
            textSize = 15f
            setTextColor(tokens.textPrimary)
            setHintTextColor(tokens.textTertiary)
            setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_material_search, 0, 0, 0)
            compoundDrawableTintList = ColorStateList.valueOf(tokens.textSecondary)
            compoundDrawablePadding = ui.dp(9)
            setPadding(ui.dp(15), 0, ui.dp(15), 0)
            background = ui.roundedBox(agentSettingsSurface, android.graphics.Color.TRANSPARENT, ui.dp(18).toFloat())
        }
        fun render(query: String) {
            grid.removeAllViews()
            val normalized = query.trim().lowercase()
            val entries = buildList<AgentProviderPreset?> {
                add(null)
                addAll(AgentProviderPresetCatalog.presets)
            }.filter { preset ->
                normalized.isEmpty() || preset == null && "自定义".contains(normalized) ||
                    preset?.displayName?.lowercase()?.contains(normalized) == true ||
                    preset?.providerId?.lowercase()?.contains(normalized) == true
            }
            entries.forEachIndexed { index, preset ->
                val row = index / 2
                val column = index % 2
                val card = providerPresetCard(
                    title = preset?.displayName ?: "自定义",
                    icon = if (preset == null) R.drawable.ic_material_settings else R.drawable.ic_bridge
                ) {
                    onSelected(preset)
                    closeProviderEditorOverlay()
                }
                grid.addView(card, GridLayout.LayoutParams(
                    GridLayout.spec(row, 1f),
                    GridLayout.spec(column, 1f)
                ).apply {
                    width = 0
                    height = ui.dp(68)
                    setMargins(
                        if (column == 0) 0 else ui.dp(5),
                        ui.dp(5),
                        if (column == 0) ui.dp(5) else 0,
                        ui.dp(5)
                    )
                })
            }
            if (entries.isEmpty()) {
                grid.addView(settingsMessage("没有匹配的供应商预设"), GridLayout.LayoutParams(
                    GridLayout.spec(0),
                    GridLayout.spec(0, 2)
                ).apply {
                    width = 0
                    columnSpec = GridLayout.spec(0, 2, 1f)
                })
            }
        }
        search.addTextChangedListener(simpleTextWatcher { render(search.text?.toString().orEmpty()) })
        render("")
        val page = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(agentPageBackground)
            addView(buildAgentSubpageHeader(
                title = "选择供应商预设",
                backDescription = "返回新建供应商",
                onBack = ::closeProviderEditorOverlay
            ), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(64)))
            addView(search, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(52)).apply {
                setMargins(ui.dp(16), ui.dp(8), ui.dp(16), ui.dp(8))
            })
            addView(ScrollView(context).apply {
                overScrollMode = View.OVER_SCROLL_NEVER
                addView(LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(ui.dp(16), ui.dp(8), ui.dp(16), ui.dp(28))
                    addView(TextView(context).apply {
                        text = "预设只负责填入名称、请求地址和推荐模型，保存前仍可全部修改。"
                        textSize = 12.5f
                        setTextColor(tokens.textSecondary)
                        setPadding(ui.dp(2), 0, ui.dp(2), ui.dp(8))
                    })
                    addView(grid, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
                }, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        }
        pushProviderEditorOverlay(page, AgentNavigationScreen.ProviderPresetPicker)
    }

    private fun providerPresetCard(title: String, icon: Int, onClick: () -> Unit): View =
        LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(ui.dp(12), ui.dp(8), ui.dp(10), ui.dp(8))
            background = ui.roundedBox(agentSettingsSurface, android.graphics.Color.TRANSPARENT, ui.dp(18).toFloat())
            addView(ImageView(context).apply {
                setImageResource(icon)
                imageTintList = ColorStateList.valueOf(tokens.textPrimary)
                setPadding(ui.dp(6), ui.dp(6), ui.dp(6), ui.dp(6))
            }, LinearLayout.LayoutParams(ui.dp(34), ui.dp(34)))
            addView(TextView(context).apply {
                text = title
                textSize = 13.5f
                typeface = Typeface.DEFAULT_BOLD
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                setTextColor(tokens.textPrimary)
                setPadding(ui.dp(7), 0, 0, 0)
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            contentDescription = "使用 $title 预设"
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
        }

    private fun pushProviderEditorOverlay(view: View, screen: AgentNavigationScreen) {
        navigationScreen = screen
        navigationHost.addView(view, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))
    }

    private fun closeProviderEditorOverlay() {
        if (navigationHost.childCount > 1) {
            navigationHost.removeViewAt(navigationHost.childCount - 1)
        }
        navigationScreen = AgentNavigationScreen.ProviderEditor
    }

    private fun buildProviderModelListRow(
        model: AgentProviderModelSummary,
        onClick: () -> Unit
    ): View = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = ui.dp(72)
        setPadding(ui.dp(16), ui.dp(10), ui.dp(7), ui.dp(10))
        background = ui.roundedBox(agentSettingsSurface, android.graphics.Color.TRANSPARENT, ui.dp(19).toFloat())
        addView(ImageView(context).apply {
            setImageResource(R.drawable.ic_status)
            imageTintList = ColorStateList.valueOf(tokens.textSecondary)
            setPadding(ui.dp(8), ui.dp(8), ui.dp(8), ui.dp(8))
        }, LinearLayout.LayoutParams(ui.dp(40), ui.dp(40)))
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(context).apply {
                text = model.displayName.ifBlank { model.id }
                textSize = 15f
                typeface = Typeface.DEFAULT_BOLD
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                setTextColor(tokens.textPrimary)
            })
            addView(TextView(context).apply {
                text = model.id
                textSize = 12f
                typeface = Typeface.MONOSPACE
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                setTextColor(tokens.textSecondary)
                setPadding(0, ui.dp(3), 0, 0)
            })
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            setMargins(ui.dp(7), 0, ui.dp(4), 0)
        })
        addView(ImageView(context).apply {
            setImageResource(R.drawable.ic_chevron_right_light)
            imageTintList = ColorStateList.valueOf(tokens.textSecondary)
            setPadding(ui.dp(11), ui.dp(11), ui.dp(11), ui.dp(11))
        }, LinearLayout.LayoutParams(ui.dp(44), ui.dp(44)))
        contentDescription = "编辑模型 ${model.displayName.ifBlank { model.id }}"
        isClickable = true
        isFocusable = true
        setOnClickListener { onClick() }
    }

    private fun providerEditorField(
        host: LinearLayout,
        label: String,
        hintText: String,
        value: String,
        inputType: Int = InputType.TYPE_CLASS_TEXT
    ): EditText {
        val field = EditText(context).apply {
            hint = hintText
            setText(value)
            this.inputType = inputType
            textSize = 15f
            setTextColor(tokens.textPrimary)
            setHintTextColor(tokens.textTertiary)
            setSingleLine(true)
            setPadding(ui.dp(15), 0, ui.dp(15), 0)
            background = ui.roundedBox(agentSettingsSurface, tokens.border, ui.dp(18).toFloat(), ui.dp(1))
        }
        host.addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(context).apply {
                text = label
                textSize = 13f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(tokens.textPrimary)
                setPadding(ui.dp(2), 0, 0, ui.dp(7))
            })
            addView(field, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(52)))
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            setMargins(0, 0, 0, ui.dp(14))
        })
        return field
    }

    private fun providerCredentialField(
        host: LinearLayout,
        hintText: String,
        credentialPresent: Boolean
    ): ProviderCredentialFieldBinding {
        val field = EditText(context).apply {
            hint = AgentProviderCredentialInputPolicy.displayHint(
                credentialPresent = credentialPresent,
                removeRequested = false,
                emptyHint = hintText
            )
            inputType = AgentProviderCredentialInputPolicy.inputType
            textSize = 15f
            setTextColor(tokens.textPrimary)
            setHintTextColor(tokens.textTertiary)
            setSingleLine(false)
            setHorizontallyScrolling(false)
            minLines = 1
            gravity = Gravity.CENTER_VERTICAL or Gravity.START
            imeOptions = EditorInfo.IME_ACTION_DONE
            minHeight = ui.dp(52)
            setPadding(ui.dp(15), ui.dp(12), ui.dp(8), ui.dp(12))
            background = null
            importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
            isSaveEnabled = false
        }
        lateinit var credentialBinding: ProviderCredentialFieldBinding
        val pasteAction = iconButton(
            context,
            R.drawable.ic_paste_light,
            "从剪贴板粘贴 API Key"
        ) {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val value = AgentProviderCredentialInputPolicy.clipboardValue(clipboard.primaryClip
                ?.takeIf { it.itemCount > 0 }
                ?.getItemAt(0)
                ?.text
            )
            if (value == null) {
                Toast.makeText(context, "剪贴板里没有可粘贴的文本", Toast.LENGTH_SHORT).show()
            } else {
                field.setText(value)
                field.setSelection(field.text?.length ?: 0)
                Toast.makeText(context, "API Key 已粘贴", Toast.LENGTH_SHORT).show()
            }
        }.apply {
            imageTintList = ColorStateList.valueOf(tokens.textSecondary)
            setPadding(ui.dp(12), ui.dp(12), ui.dp(12), ui.dp(12))
        }
        val deleteAction = iconButton(
            context,
            R.drawable.ic_delete_light,
            "移除已保存的 API Key"
        ) {
            credentialBinding.markForRemoval()
            Toast.makeText(context, "保存后将移除 API Key", Toast.LENGTH_SHORT).show()
        }.apply {
            imageTintList = ColorStateList.valueOf(tokens.danger)
            setPadding(ui.dp(12), ui.dp(12), ui.dp(12), ui.dp(12))
            visibility = if (credentialPresent) View.VISIBLE else View.GONE
        }
        host.addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(context).apply {
                text = "API Key"
                textSize = 13f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(tokens.textPrimary)
                setPadding(ui.dp(2), 0, 0, ui.dp(7))
            })
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = ui.dp(52)
                background = ui.roundedBox(agentSettingsSurface, tokens.border, ui.dp(18).toFloat(), ui.dp(1))
                addView(field, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                addView(deleteAction, LinearLayout.LayoutParams(ui.dp(48), ui.dp(48)).apply {
                    setMargins(0, ui.dp(2), 0, ui.dp(2))
                })
                addView(pasteAction, LinearLayout.LayoutParams(ui.dp(48), ui.dp(48)).apply {
                    setMargins(0, ui.dp(2), ui.dp(4), ui.dp(2))
                })
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            setMargins(0, 0, 0, ui.dp(14))
        })
        credentialBinding = ProviderCredentialFieldBinding(
            field = field,
            pasteAction = pasteAction,
            deleteAction = deleteAction,
            credentialPresent = credentialPresent,
            emptyHint = hintText
        )
        field.addTextChangedListener(simpleTextWatcher {
            credentialBinding.onInputChanged(field.text)
        })
        return credentialBinding
    }

    private fun showProviderModelEditor(
        model: AgentProviderModelSummary?,
        onSave: (AgentProviderModelSummary) -> Unit,
        onDelete: (() -> Unit)?
    ) {
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ui.dp(16), ui.dp(16), ui.dp(16), ui.dp(28))
        }
        val status = TextView(context).apply {
            textSize = 12.5f
            setTextColor(tokens.danger)
            visibility = View.GONE
            setPadding(ui.dp(2), 0, ui.dp(2), ui.dp(10))
        }
        content.addView(status)
        val idField = providerEditorField(
            content,
            label = "模型 ID（发送给供应商）",
            hintText = "例如 mimo-v2-pro",
            value = model?.id.orEmpty()
        )
        val nameField = providerEditorField(
            content,
            label = "显示名称（仅在 Kite 中显示，可选）",
            hintText = "留空时使用模型 ID",
            value = model?.displayName.orEmpty()
        )
        content.addView(TextView(context).apply {
            text = "模型 ID 必须和供应商真实接口一致；显示名称只用于会话选择时更易阅读。"
            textSize = 12.5f
            setTextColor(tokens.textSecondary)
            setPadding(ui.dp(2), 0, ui.dp(2), ui.dp(18))
        })
        if (onDelete != null) {
            content.addView(actionDangerButton("删除模型") {
                onDelete()
                closeProviderEditorOverlay()
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(50)).apply {
                setMargins(0, ui.dp(10), 0, 0)
            })
        }
        val save = TextView(context).apply {
            text = "保存"
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(tokens.primaryStrong)
            isClickable = true
            isFocusable = true
            setOnClickListener {
                val id = idField.text?.toString()?.trim().orEmpty()
                if (id.isBlank()) {
                    status.text = "请输入真实模型 ID"
                    status.visibility = View.VISIBLE
                    return@setOnClickListener
                }
                val name = nameField.text?.toString()?.trim().orEmpty().ifBlank { id }
                onSave(AgentProviderModelSummary(id, name))
                closeProviderEditorOverlay()
            }
        }
        val page = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(agentPageBackground)
            addView(buildAgentSubpageHeader(
                title = if (model == null) "添加模型" else "编辑模型",
                backDescription = "返回供应商配置",
                onBack = ::closeProviderEditorOverlay,
                trailingView = save
            ), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(64)))
            addView(ScrollView(context).apply {
                isFillViewport = true
                overScrollMode = View.OVER_SCROLL_NEVER
                addView(content, ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ))
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        }
        pushProviderEditorOverlay(page, AgentNavigationScreen.ProviderModelEditor)
    }

    private fun simpleTextWatcher(onChanged: () -> Unit): TextWatcher = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = onChanged()
        override fun afterTextChanged(s: Editable?) = Unit
    }

    private fun providerDraftJson(
        id: String,
        displayName: String,
        baseUrl: String,
        models: List<AgentProviderModelSummary>
    ): String = JSONObject().apply {
        put("id", id)
        put("name", displayName)
        put("baseUrl", baseUrl)
        put("models", JSONArray().apply {
            models.forEach { model ->
                put(JSONObject().apply {
                    put("id", model.id)
                    put("name", model.displayName)
                })
            }
        })
    }.toString(2).replace("\\/", "/")

    private fun AgentCredentialPresence.providerCredentialLabel(): String = when (this) {
        AgentCredentialPresence.Present -> "API Key 已配置"
        AgentCredentialPresence.Missing -> "缺少 API Key"
        AgentCredentialPresence.Unknown -> "API Key 状态未知"
        AgentCredentialPresence.NotApplicable -> "无需 API Key"
    }

    private fun buildProviderListPage(
        selected: AgentRegistryEntry,
        adapter: AgentConfigAdapter,
        snapshot: AgentLiveConfigSnapshot
    ): View = LinearLayout(context).apply {
        providerListCardBindings.clear()
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(agentPageBackground)
        addView(buildAgentSubpageHeader(
            title = "供应商配置",
            backDescription = "返回 Agent 设置",
            onBack = ::returnToAgentSettings,
            actionIcon = R.drawable.ic_add_light,
            actionDescription = "添加供应商",
            onAction = { showProviderEditor(selected, adapter, snapshot, existing = null, preset = null) }
        ), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(64)))
        addView(ScrollView(context).apply {
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_NEVER
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(ui.dp(16), ui.dp(12), ui.dp(16), ui.dp(28))
                addView(TextView(context).apply {
                    text = selected.registration.definition.displayName
                    textSize = 21f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(tokens.textPrimary)
                })
                addView(TextView(context).apply {
                    text = "这里准备供应商和可用模型；默认配置只影响以后新建或重新连接的会话。"
                    textSize = 13f
                    setTextColor(tokens.textSecondary)
                    setPadding(0, ui.dp(5), 0, ui.dp(18))
                })
                if (snapshot.providers.isEmpty()) {
                    addView(LinearLayout(context).apply {
                        orientation = LinearLayout.VERTICAL
                        gravity = Gravity.CENTER
                        setPadding(ui.dp(22), ui.dp(40), ui.dp(22), ui.dp(40))
                        background = ui.roundedBox(
                            agentSettingsSurface,
                            android.graphics.Color.TRANSPARENT,
                            ui.dp(24).toFloat()
                        )
                        addView(TextView(context).apply {
                            text = "还没有供应商配置"
                            textSize = 17f
                            typeface = Typeface.DEFAULT_BOLD
                            setTextColor(tokens.textPrimary)
                        })
                        addView(TextView(context).apply {
                            text = "可以从预置开始，也可以完整自定义。"
                            textSize = 13f
                            gravity = Gravity.CENTER
                            setTextColor(tokens.textSecondary)
                            setPadding(0, ui.dp(7), 0, ui.dp(18))
                        })
                        addView(actionTextButton("添加供应商") {
                            showProviderEditor(selected, adapter, snapshot, existing = null, preset = null)
                        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(48)))
                    }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
                } else {
                    snapshot.providers.forEach { provider ->
                        addView(buildProviderListCard(
                            provider = provider,
                            selected = provider.id == snapshot.activeProviderId,
                            onSelect = { selectPersistentProvider(selected, adapter, provider) },
                            onEdit = { showProviderEditor(selected, adapter, providerPageSnapshot ?: snapshot, provider, preset = null) }
                        ), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                            setMargins(0, 0, 0, ui.dp(12))
                        })
                    }
                }
            }, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
    }

    private fun buildProviderListCard(
        provider: AgentProviderSummary,
        selected: Boolean,
        onSelect: () -> Unit,
        onEdit: () -> Unit
    ): View {
        lateinit var status: TextView
        lateinit var editButton: View
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = ui.dp(92)
            setPadding(ui.dp(18), ui.dp(15), ui.dp(9), ui.dp(15))
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(TextView(context).apply {
                    text = provider.displayName
                    textSize = 16.5f
                    typeface = Typeface.DEFAULT_BOLD
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                    setTextColor(tokens.textPrimary)
                })
                addView(TextView(context).apply {
                    text = provider.baseUrl
                    textSize = 12.5f
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    setTextColor(tokens.textSecondary)
                    setPadding(0, ui.dp(4), 0, 0)
                })
                status = TextView(context).apply {
                    textSize = 12f
                    setTextColor(tokens.textSecondary)
                    setPadding(0, ui.dp(4), 0, 0)
                }
                addView(status)
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            editButton = iconButton(
                context,
                R.drawable.ic_chevron_right_light,
                "编辑 ${provider.displayName}",
                onEdit
            ).apply {
                imageTintList = ColorStateList.valueOf(tokens.textSecondary)
            }
            addView(editButton, LinearLayout.LayoutParams(ui.dp(48), ui.dp(48)))
            isClickable = true
            isFocusable = true
            setOnClickListener { onSelect() }
        }
        providerListCardBindings[provider.id] = ProviderListCardBinding(container, status, editButton, provider)
        renderProviderListCards(providerPageSnapshot?.activeProviderId, pendingProviderId = null)
        return container
    }

    private fun renderProviderListCards(activeProviderId: String?, pendingProviderId: String?) {
        providerListCardBindings.forEach { (providerId, binding) ->
            val isSelected = providerId == (pendingProviderId ?: activeProviderId)
            binding.container.background = ui.roundedBox(
                if (isSelected) agentSettingsSurface else agentSurface,
                if (isSelected) android.graphics.Color.TRANSPARENT else tokens.border,
                ui.dp(22).toFloat(),
                if (isSelected) 0 else ui.dp(1)
            )
            binding.container.contentDescription = buildString {
                append(binding.provider.displayName)
                append(if (isSelected) "，已选择" else "，未选择")
                if (pendingProviderId == providerId) append("，正在切换")
            }
            binding.status.text = buildString {
                if (isSelected) append("当前默认 · ")
                append("${binding.provider.models.size} 个模型 · ")
                append(binding.provider.credentialPresence.providerCredentialLabel())
            }
            val enabled = pendingProviderId == null
            binding.container.isEnabled = enabled
            binding.editButton.isEnabled = enabled
            binding.container.alpha = if (enabled || isSelected) 1f else 0.55f
            binding.editButton.alpha = if (enabled) 1f else 0.45f
        }
    }

    private fun showProviderEditor(
        selected: AgentRegistryEntry,
        adapter: AgentConfigAdapter,
        snapshot: AgentLiveConfigSnapshot,
        existing: AgentProviderSummary?,
        preset: AgentProviderPreset?
    ) {
        providerPageAgentId = selected.registration.definition.agentId
        providerPageAdapter = adapter
        providerPageSnapshot = snapshot
        navigationScreen = AgentNavigationScreen.ProviderEditor
        navigationHost.removeAllViews()
        navigationHost.addView(
            buildProviderEditorPage(selected, adapter, snapshot, existing, preset),
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
        navigationHost.visibility = View.VISIBLE
    }

    private fun buildProviderEditorPage(
        selected: AgentRegistryEntry,
        adapter: AgentConfigAdapter,
        snapshot: AgentLiveConfigSnapshot,
        existing: AgentProviderSummary?,
        initialPreset: AgentProviderPreset?
    ): View {
        val initialModels = existing?.models ?: initialPreset?.models.orEmpty()
        val modelDrafts = mutableListOf<AgentProviderModelSummary>()
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ui.dp(16), ui.dp(10), ui.dp(16), ui.dp(30))
        }
        val basicFields = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        val status = TextView(context).apply {
            textSize = 12.5f
            setTextColor(android.graphics.Color.rgb(198, 40, 40))
            visibility = View.GONE
            setPadding(ui.dp(2), 0, ui.dp(2), ui.dp(10))
        }
        providerEditorStatusText = status
        content.addView(status)

        val nameInput = providerEditorField(
            basicFields,
            label = "供应商名称",
            hintText = "例如 智谱 GLM",
            value = existing?.displayName ?: initialPreset?.displayName.orEmpty()
        )
        val credentialInput = providerCredentialField(
            host = basicFields,
            hintText = "粘贴供应商 API Key",
            credentialPresent = existing?.credentialPresence == AgentCredentialPresence.Present
        )
        val keyInput = credentialInput.field
        val urlInput = providerEditorField(
            basicFields,
            label = "请求地址",
            hintText = "https://api.example.com/v1",
            value = existing?.baseUrl ?: initialPreset?.baseUrl.orEmpty(),
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        )
        val modelsHost = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val idInput = EditText(context).apply {
            setText(existing?.id ?: initialPreset?.providerId.orEmpty())
            isEnabled = existing == null
        }
        val jsonPreview = TextView(context).apply {
            textSize = 12f
            typeface = Typeface.MONOSPACE
            setTextColor(tokens.textSecondary)
            setTextIsSelectable(true)
            setPadding(ui.dp(14), ui.dp(13), ui.dp(14), ui.dp(13))
            background = ui.roundedBox(agentSettingsSurface, tokens.border, ui.dp(18).toFloat(), ui.dp(1))
        }
        lateinit var updatePreview: () -> Unit
        lateinit var renderModels: () -> Unit
        fun replaceModels(models: List<AgentProviderModelSummary>) {
            modelDrafts.clear()
            modelDrafts.addAll(models)
            renderModels()
            updatePreview()
        }
        updatePreview = {
            jsonPreview.text = providerDraftJson(
                id = idInput.text?.toString().orEmpty(),
                displayName = nameInput.text?.toString().orEmpty(),
                baseUrl = urlInput.text?.toString().orEmpty(),
                models = modelDrafts.filter { it.id.isNotBlank() }
            )
        }
        renderModels = {
            modelsHost.removeAllViews()
            if (modelDrafts.isEmpty()) {
                modelsHost.addView(TextView(context).apply {
                    text = "还没有可用模型"
                    textSize = 13f
                    gravity = Gravity.CENTER
                    setTextColor(tokens.textSecondary)
                    setPadding(ui.dp(12), ui.dp(20), ui.dp(12), ui.dp(20))
                    background = ui.roundedBox(
                        agentSettingsSurface,
                        android.graphics.Color.TRANSPARENT,
                        ui.dp(18).toFloat()
                    )
                }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    setMargins(0, 0, 0, ui.dp(10))
                })
            } else {
                modelDrafts.forEachIndexed { index, model ->
                    modelsHost.addView(buildProviderModelListRow(model) {
                        showProviderModelEditor(
                            model = model,
                            onSave = { updated ->
                                modelDrafts[index] = updated
                                renderModels()
                                updatePreview()
                            },
                            onDelete = {
                                modelDrafts.removeAt(index)
                                renderModels()
                                updatePreview()
                            }
                        )
                    }, LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply { setMargins(0, 0, 0, ui.dp(10)) })
                }
            }
        }
        listOf(nameInput, urlInput, idInput).forEach { field ->
            field.addTextChangedListener(simpleTextWatcher { updatePreview() })
        }

        if (existing == null) {
            content.addView(sectionTitle("从预置开始", "先选一个供应商模板，或者保留完全自定义。"))
            val presetValue = TextView(context).apply {
                text = initialPreset?.displayName ?: "自定义配置"
            }
            content.addView(providerPresetSelectionRow(presetValue) {
                showProviderPresetPicker { preset ->
                    if (preset == null) {
                        presetValue.text = "自定义配置"
                        idInput.setText("")
                        nameInput.setText("")
                        urlInput.setText("")
                        replaceModels(emptyList())
                    } else {
                        presetValue.text = preset.displayName
                        idInput.setText(preset.providerId)
                        nameInput.setText(preset.displayName)
                        urlInput.setText(preset.baseUrl)
                        replaceModels(preset.models)
                    }
                }
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, 0, 0, ui.dp(20))
            })
        }
        content.addView(basicFields)

        content.addView(sectionTitle("可用模型", "配置此供应商可以提供的模型；会话中再选择实际使用哪一个。"))
        content.addView(modelsHost)
        content.addView(actionOutlineButton("＋  添加模型") {
            showProviderModelEditor(
                model = null,
                onSave = { model ->
                    modelDrafts += model
                    renderModels()
                    updatePreview()
                },
                onDelete = null
            )
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(48)).apply {
            setMargins(0, 0, 0, ui.dp(22))
        })

        val advancedContent = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }
        val visibleId = providerEditorField(
            advancedContent,
            label = "供应商 ID",
            hintText = "保存时自动生成，也可以手动填写",
            value = idInput.text?.toString().orEmpty()
        )
        visibleId.isEnabled = existing == null
        idInput.addTextChangedListener(simpleTextWatcher {
            if (visibleId.text?.toString() != idInput.text?.toString()) {
                visibleId.setText(idInput.text?.toString().orEmpty())
            }
        })
        visibleId.addTextChangedListener(simpleTextWatcher {
            if (idInput.text?.toString() != visibleId.text?.toString()) {
                idInput.setText(visibleId.text?.toString().orEmpty())
            }
        })
        advancedContent.addView(TextView(context).apply {
            text = "配置 JSON（不含 API Key）"
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(tokens.textPrimary)
            setPadding(0, ui.dp(6), 0, ui.dp(7))
        })
        advancedContent.addView(jsonPreview)
        advancedContent.addView(TextView(context).apply {
            text = "这是当前草稿的安全预览。保存时仍由 Agent 适配器校验并翻译成原生格式。"
            textSize = 12f
            setTextColor(tokens.textSecondary)
            setPadding(0, ui.dp(7), 0, ui.dp(18))
        })
        content.addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(ui.dp(4), ui.dp(4), ui.dp(4), ui.dp(4))
            addView(TextView(context).apply {
                text = "高级设置"
                textSize = 15f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(tokens.textPrimary)
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            val chevron = ImageView(context).apply {
                setImageResource(R.drawable.ic_chevron_right_light)
                imageTintList = ColorStateList.valueOf(tokens.textSecondary)
                setPadding(ui.dp(12), ui.dp(12), ui.dp(12), ui.dp(12))
            }
            addView(chevron, LinearLayout.LayoutParams(ui.dp(48), ui.dp(48)))
            isClickable = true
            isFocusable = true
            setOnClickListener {
                advancedContent.visibility = if (advancedContent.visibility == View.VISIBLE) View.GONE else View.VISIBLE
                chevron.rotation = if (advancedContent.visibility == View.VISIBLE) 90f else 0f
            }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(56)))
        content.addView(advancedContent)

        if (existing != null) {
            content.addView(actionDangerButton("删除供应商") {
                confirmRemoveProvider(selected, adapter, snapshot, existing)
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(50)).apply {
                setMargins(0, ui.dp(16), 0, 0)
            })
        }

        replaceModels(initialModels)
        val saveAction = TextView(context).apply {
            text = "保存"
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(tokens.primaryStrong)
            contentDescription = if (existing == null) "保存新供应商" else "保存供应商"
            isClickable = true
            isFocusable = true
            setOnClickListener {
                val name = nameInput.text?.toString()?.trim().orEmpty()
                val rawId = idInput.text?.toString()?.trim().orEmpty()
                val id = rawId.ifBlank { AgentProviderEditorPolicy.providerIdFromName(name) }
                val url = urlInput.text?.toString()?.trim().orEmpty()
                val models = modelDrafts.toList()
                val error = AgentProviderEditorPolicy.validate(name, id, url, models)
                if (error != null) {
                    status.text = error
                    status.visibility = View.VISIBLE
                    return@setOnClickListener
                }
                status.text = "正在安全写入 Agent 原生配置…"
                status.setTextColor(tokens.textSecondary)
                status.visibility = View.VISIBLE
                isEnabled = false
                alpha = 0.45f
                providerEditorSaveAction = this
                val credential = credentialInput.credentialChange()
                keyInput.setText("")
                applyPersistentProviderChange(
                    selected = selected,
                    adapter = adapter,
                    snapshot = snapshot,
                    change = AgentPersistentConfigChange.ConfigureProvider(
                        provider = AgentProviderDraft(
                            id = id,
                            displayName = name,
                            baseUrl = url,
                            models = models
                        ),
                        credential = credential
                    ),
                    successMessage = "供应商资料已更新"
                )
            }
        }
        providerEditorSaveAction = saveAction
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(agentPageBackground)
            addView(buildAgentSubpageHeader(
                title = if (existing == null) "新建供应商" else "编辑供应商",
                backDescription = "返回供应商配置",
                onBack = ::showCurrentProviderList,
                trailingView = saveAction
            ), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(64)))
            addView(ScrollView(context).apply {
                isFillViewport = true
                overScrollMode = View.OVER_SCROLL_NEVER
                addView(content, ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ))
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        }
    }

    private fun confirmRemoveProvider(
        selected: AgentRegistryEntry,
        adapter: AgentConfigAdapter,
        snapshot: AgentLiveConfigSnapshot,
        provider: AgentProviderSummary
    ) {
        AlertDialog.Builder(context)
            .setTitle("删除 ${provider.displayName}？")
            .setMessage("供应商资料和它在 Agent 原生认证文件中的 API Key 都会移除；其他供应商不受影响。")
            .setNegativeButton("取消", null)
            .setPositiveButton("删除") { _, _ ->
                applyPersistentProviderChange(
                    selected,
                    adapter,
                    snapshot,
                    AgentPersistentConfigChange.RemoveProvider(provider.id, removeCredential = true),
                    "供应商资料已删除"
                )
            }
            .show()
    }

    private fun selectPersistentProvider(
        selected: AgentRegistryEntry,
        adapter: AgentConfigAdapter,
        provider: AgentProviderSummary
    ) {
        val snapshot = providerPageSnapshot ?: return
        if (snapshot.activeProviderId == provider.id) return
        val model = provider.models.firstOrNull()
        if (model == null) {
            Toast.makeText(context, "请先编辑供应商并添加至少一个模型", Toast.LENGTH_SHORT).show()
            return
        }
        val selection = AgentPersistentConfigChange.SelectProvider(provider.id, model.id)
        val targetAgentId = selected.registration.definition.agentId
        val requestRevision = ++settingsLoadRevision
        renderProviderListCards(snapshot.activeProviderId, pendingProviderId = provider.id)
        navigationJob?.cancel()
        navigationJob = lifecycleOwner.lifecycleScope.launch {
            val outcome = withContext(Dispatchers.IO) {
                val result = adapter.apply(
                    AgentConfigApplyRequest(targetAgentId, snapshot.revision, listOf(selection))
                )
                val refreshed = when (result) {
                    is AgentConfigApplyResult.Applied -> AgentConfigReadResult.Ready(result.snapshot)
                    else -> adapter.backfill(targetAgentId)
                }
                result to refreshed
            }
            if (requestRevision != settingsLoadRevision || selectedSettingsAgentId != targetAgentId) return@launch
            persistentConfigAgentId = targetAgentId
            persistentConfigResult = outcome.second
            val refreshedSnapshot = when (val refreshed = outcome.second) {
                is AgentConfigReadResult.Ready -> refreshed.snapshot
                else -> null
            }
            if (refreshedSnapshot != null) providerPageSnapshot = refreshedSnapshot
            when (val applyResult = outcome.first) {
                is AgentConfigApplyResult.Applied -> {
                    usePersistentSnapshotAsDraftDefault(
                        targetAgentId,
                        refreshedSnapshot ?: applyResult.snapshot
                    )
                    renderProviderListCards(
                        (refreshedSnapshot ?: applyResult.snapshot).activeProviderId,
                        pendingProviderId = null
                    )
                    val message = AgentPersistentDefaultPolicy.savedMessage(
                        provider.displayName,
                        targetAgentId == agentId
                    )
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                }
                else -> {
                    renderProviderListCards(
                        refreshedSnapshot?.activeProviderId ?: snapshot.activeProviderId,
                        pendingProviderId = null
                    )
                    Toast.makeText(
                        context,
                        applyResult.userMessage("默认供应商已更新"),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun applyPersistentProviderChange(
        selected: AgentRegistryEntry,
        adapter: AgentConfigAdapter,
        snapshot: AgentLiveConfigSnapshot,
        change: AgentPersistentConfigChange,
        successMessage: String
    ) {
        val targetAgentId = selected.registration.definition.agentId
        val requestRevision = ++settingsLoadRevision
        navigationJob = lifecycleOwner.lifecycleScope.launch {
            val outcome = withContext(Dispatchers.IO) {
                val result = adapter.apply(
                    AgentConfigApplyRequest(targetAgentId, snapshot.revision, listOf(change))
                )
                val refreshed = when (result) {
                    is AgentConfigApplyResult.Applied -> AgentConfigReadResult.Ready(result.snapshot)
                    else -> adapter.backfill(targetAgentId)
                }
                result to refreshed
            }
            if (requestRevision != settingsLoadRevision || selectedSettingsAgentId != targetAgentId) return@launch
            persistentConfigAgentId = targetAgentId
            persistentConfigResult = outcome.second
            val refreshedSnapshot = when (val refreshed = outcome.second) {
                is AgentConfigReadResult.Ready -> refreshed.snapshot
                else -> null
            }
            if (refreshedSnapshot != null) providerPageSnapshot = refreshedSnapshot
            when (val applyResult = outcome.first) {
                is AgentConfigApplyResult.Applied -> {
                    providerEditorSaveAction = null
                    providerEditorStatusText = null
                    val appliedSnapshot = refreshedSnapshot ?: applyResult.snapshot
                    usePersistentSnapshotAsDraftDefault(targetAgentId, appliedSnapshot)
                    showProviderManager(selected, adapter, appliedSnapshot)
                    Toast.makeText(
                        context,
                        AgentPersistentDefaultPolicy.configurationSavedMessage(
                            successMessage,
                            targetAgentId == agentId
                        ),
                        Toast.LENGTH_SHORT
                    ).show()
                }
                else -> {
                    Toast.makeText(
                        context,
                        outcome.first.userMessage(successMessage),
                        Toast.LENGTH_SHORT
                    ).show()
                    providerEditorSaveAction?.apply {
                        isEnabled = true
                        alpha = 1f
                    }
                    providerEditorStatusText?.apply {
                        text = outcome.first.userMessage(successMessage)
                        setTextColor(android.graphics.Color.rgb(198, 40, 40))
                        visibility = View.VISIBLE
                    }
                }
            }
        }
    }

    private fun AgentConfigApplyResult.userMessage(successMessage: String): String = when (this) {
        is AgentConfigApplyResult.Applied -> successMessage
        is AgentConfigApplyResult.Conflict -> message
        is AgentConfigApplyResult.Rejected -> problems.firstOrNull()?.message ?: "配置未通过校验"
        is AgentConfigApplyResult.Unavailable -> discovery.warnings.firstOrNull() ?: "原生配置当前不可用"
        is AgentConfigApplyResult.Failed -> if (restored) "$message，原配置已保留" else "$message，请检查原生配置"
    }

    private fun buildSettingsSection(
        title: String,
        description: String,
        rows: List<SettingsRow>
    ): View = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        addView(TextView(context).apply {
            text = title
            textSize = 16.5f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(tokens.textPrimary)
            setPadding(ui.dp(2), 0, ui.dp(2), ui.dp(4))
        })
        addView(TextView(context).apply {
            text = description
            textSize = 12f
            setTextColor(tokens.textSecondary)
            setPadding(ui.dp(2), 0, ui.dp(2), ui.dp(11))
        })
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = ui.roundedBox(
                agentSettingsSurface,
                android.graphics.Color.TRANSPARENT,
                ui.dp(20).toFloat()
            )
            rows.forEachIndexed { index, row ->
                addView(buildSettingsRow(row), LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ))
                if (index != rows.lastIndex) addView(View(context).apply {
                    setBackgroundColor(tokens.border)
                }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(1)).apply {
                    setMargins(ui.dp(16), 0, 0, 0)
                })
            }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }.also { view ->
        view.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(0, 0, 0, ui.dp(20)) }
    }

    private fun buildSettingsRow(row: SettingsRow): View = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = ui.dp(62)
        setPadding(ui.dp(16), ui.dp(9), ui.dp(8), ui.dp(9))
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(context).apply {
                text = row.title
                textSize = 14.5f
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                setTextColor(tokens.textPrimary)
            })
            addView(TextView(context).apply {
                text = row.subtitle
                textSize = 12f
                maxLines = 2
                ellipsize = TextUtils.TruncateAt.END
                setTextColor(tokens.textSecondary)
                setPadding(0, ui.dp(3), 0, 0)
            })
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        if (row.onClick != null) addView(ImageView(context).apply {
            setImageResource(R.drawable.ic_chevron_right_light)
            imageTintList = ColorStateList.valueOf(tokens.textSecondary)
            contentDescription = "打开${row.title}"
            setPadding(ui.dp(12), ui.dp(12), ui.dp(12), ui.dp(12))
        }, LinearLayout.LayoutParams(ui.dp(48), ui.dp(48)))
        if (row.onClick != null) {
            isClickable = true
            isFocusable = true
            setOnClickListener { row.onClick.invoke() }
        }
    }

    private fun settingsMessage(message: String): View = TextView(context).apply {
        text = message
        textSize = 14f
        gravity = Gravity.CENTER
        setTextColor(tokens.textSecondary)
        setPadding(ui.dp(16), ui.dp(48), ui.dp(16), ui.dp(48))
    }

    private fun AgentRegistryEntry.primaryStatusLabel(): String = when {
        installationStatus == AgentInstallationStatus.NotInstalled -> "未安装"
        configurationStatus == AgentConfigurationStatus.Required -> "需要配置"
        launchStatus == AgentLaunchStatus.Unsupported -> "暂不可连接"
        runtimeStatus == AgentRuntimeStatus.Running -> "运行中"
        else -> "可用"
    }

    private fun AgentRegistryEntry.installationStatusLabel(): String = when (installationStatus) {
        AgentInstallationStatus.NotApplicable -> "不由资源安装管理"
        AgentInstallationStatus.NotInstalled -> "未安装"
        AgentInstallationStatus.Installed -> "已安装"
    }

    private fun AgentRegistryEntry.configurationStatusLabel(): String = when (configurationStatus) {
        AgentConfigurationStatus.NotRequired -> "无需 Kite 配置"
        AgentConfigurationStatus.Unknown -> "尚未检查"
        AgentConfigurationStatus.Required -> "需要配置"
        AgentConfigurationStatus.Ready -> "已配置"
    }

    private fun sessionConfigurationOptions(): List<AgentConfigOption> {
        currentSnapshot?.let { snapshot ->
            return snapshot.configuration.filterNot {
                it.category == AgentConfigCategory.Mode || it.category == AgentConfigCategory.Permission
            }
        }
        val runtime = AgentRuntimeRegistry.session(instanceId)
            ?.takeIf { it.generation == generation && it.isDraft }
            ?: return emptyList()
        return draftSessionConfigurationOptions(runtime)
            .filterNot {
                it.category == AgentConfigCategory.Mode || it.category == AgentConfigCategory.Permission
            }
    }

    private fun draftSessionConfigurationOptions(
        runtime: AgentRuntimeSession
    ): List<AgentConfigOption> {
        val preferences = AgentRuntimeRegistry.draftPreferences(runtime.instanceId, runtime.generation)
        val cached = AgentRuntimeRegistry.draftCapabilityCatalog(runtime.instanceId, runtime.generation)
            ?.configuration
            .orEmpty()
            .map { option -> option.withDraftValue(preferences?.configuration?.get(option.id)) }
        val persistentModel = draftModelSnapshot?.let { snapshot ->
            AgentDraftModelPolicy.option(
                snapshot,
                AgentRuntimeRegistry.draftModelSelection(runtime.instanceId, runtime.generation)
            )
        }
        return if (persistentModel == null) {
            cached
        } else {
            listOf(persistentModel) + cached.filterNot { it.category == AgentConfigCategory.Model }
        }
    }

    private fun composerModeOptions(): List<AgentComposerModeOption> {
        val runtime = AgentRuntimeRegistry.session(instanceId) ?: return emptyList()
        val configuration = currentSnapshot?.configuration
            ?: runtime.takeIf { it.generation == generation && it.isDraft }
                ?.let(::draftSessionConfigurationOptions)
                .orEmpty()
        val configMode = configuration
            .filterIsInstance<AgentConfigOption.Select>()
            .firstOrNull { it.category == AgentConfigCategory.Mode }
        if (configMode != null) {
            return configMode.choices.map { choice ->
                AgentComposerModeOption(
                    id = choice.value,
                    name = choice.name,
                    description = choice.description,
                    selected = choice.value == configMode.currentValue,
                    configId = configMode.id
                )
            }
        }
        val catalog = if (runtime.isDraft) {
            AgentRuntimeRegistry.draftCapabilityCatalog(runtime.instanceId, runtime.generation)
        } else {
            runtime.snapshot?.let { snapshot ->
                AgentDraftCapabilityCatalog(
                    configuration = snapshot.configuration,
                    modes = snapshot.modes,
                    currentModeId = snapshot.currentModeId
                )
            }
        } ?: return emptyList()
        val currentModeId = if (runtime.isDraft) {
            AgentRuntimeRegistry.draftPreferences(runtime.instanceId, runtime.generation)?.modeId
                ?: catalog.currentModeId
        } else {
            currentSnapshot?.currentModeId ?: catalog.currentModeId
        }
        return catalog.modes.map { mode ->
            AgentComposerModeOption(
                id = mode.id,
                name = mode.name,
                description = mode.description,
                selected = mode.id == currentModeId,
                configId = null
            )
        }
    }

    private fun composerPermissionOption(): AgentConfigOption.Select? {
        val runtime = AgentRuntimeRegistry.session(instanceId) ?: return null
        val configuration = currentSnapshot?.configuration
            ?: runtime.takeIf { it.generation == generation && it.isDraft }
                ?.let(::draftSessionConfigurationOptions)
                .orEmpty()
        return AgentSurfaceNavigationPolicy.permissionOption(configuration)
    }

    private fun composerCommands(): List<AgentCommand> = currentSnapshot?.commands
        ?: AgentRuntimeRegistry.session(instanceId)
            ?.takeIf { it.generation == generation && it.isDraft }
            ?.let { runtime ->
                AgentRuntimeRegistry.draftCapabilityCatalog(runtime.instanceId, runtime.generation)?.commands
            }
            .orEmpty()

    private fun supportsImageAttachments(): Boolean {
        val prompt = AgentRuntimeRegistry.session(instanceId)?.capabilities?.prompt ?: return false
        return prompt.images || prompt.resourceLinks
    }

    private fun supportsFileAttachments(): Boolean {
        val prompt = AgentRuntimeRegistry.session(instanceId)?.capabilities?.prompt ?: return false
        return prompt.audio || prompt.resourceLinks || prompt.embeddedResources
    }

    private fun showComposerExtensionMenu(initialRoute: ComposerExtensionRoute = ComposerExtensionRoute.Main) {
        if (draftPreparationPending) {
            Toast.makeText(context, "正在准备新会话", Toast.LENGTH_SHORT).show()
            return
        }
        closeCommandPalette(animate = false)
        closeSessionConfigurationPanel(animate = false)
        val hasEntry = supportsImageAttachments() ||
            supportsFileAttachments() ||
            composerCommands().isNotEmpty() ||
            composerModeOptions().isNotEmpty()
        if (initialRoute == ComposerExtensionRoute.Main && !hasEntry) {
            Toast.makeText(context, "当前 Agent 没有可用扩展", Toast.LENGTH_SHORT).show()
            return
        }
        composerExtensionRoute = initialRoute
        rebuildComposerExtensionMenu(animateContent = false)
        composerExtensionOverlay.apply {
            visibility = View.VISIBLE
            alpha = 0f
            setOnClickListener { closeComposerExtensionMenu() }
            animate()
                .alpha(1f)
                .setDuration(150L)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }
    }

    private fun closeComposerExtensionMenu(animate: Boolean = true) {
        if (composerExtensionOverlay.visibility != View.VISIBLE) return
        if (!animate) {
            composerExtensionOverlay.animate().cancel()
            composerExtensionOverlay.visibility = View.GONE
            composerExtensionOverlay.removeAllViews()
            composerExtensionRoute = ComposerExtensionRoute.Main
            return
        }
        composerExtensionOverlay.animate()
            .alpha(0f)
            .setDuration(110L)
            .withEndAction {
                composerExtensionOverlay.visibility = View.GONE
                composerExtensionOverlay.removeAllViews()
                composerExtensionRoute = ComposerExtensionRoute.Main
            }
            .start()
    }

    private fun rebuildComposerExtensionMenu(animateContent: Boolean) {
        if (composerExtensionOverlay.visibility != View.VISIBLE && animateContent) return
        val modes = composerModeOptions()
        val permission = composerPermissionOption()
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ui.dp(10), ui.dp(9), ui.dp(10), ui.dp(11))
            when (composerExtensionRoute) {
                ComposerExtensionRoute.Main -> {
                    if (supportsImageAttachments()) {
                        addView(composerExtensionActionRow(
                            icon = R.drawable.ic_photo_light,
                            title = "相册"
                        ) {
                            closeComposerExtensionMenu(animate = false)
                            onPickImages()
                        })
                    }
                    if (supportsFileAttachments()) {
                        addView(composerExtensionActionRow(
                            icon = R.drawable.ic_attachment_light,
                            title = "文件"
                        ) {
                            closeComposerExtensionMenu(animate = false)
                            onPickFiles()
                        })
                    }
                    if (modes.isNotEmpty()) {
                        addView(composerExtensionActionRow(
                            icon = R.drawable.ic_mode_light,
                            title = "工作模式",
                            value = modes.firstOrNull { it.selected }?.name,
                            showChevron = true
                        ) {
                            composerExtensionRoute = ComposerExtensionRoute.Modes
                            rebuildComposerExtensionMenu(animateContent = true)
                        })
                    }
                    if (composerCommands().isNotEmpty()) {
                        addView(composerExtensionActionRow(
                            icon = R.drawable.ic_skill_light,
                            title = "Skill 与命令"
                        ) {
                            openSlashCommandPalette()
                        })
                    }
                }
                ComposerExtensionRoute.Modes -> {
                    addView(composerExtensionHeader("工作模式") {
                        composerExtensionRoute = ComposerExtensionRoute.Main
                        rebuildComposerExtensionMenu(animateContent = true)
                    })
                    modes.forEach { mode ->
                        addView(sessionChoiceRow(
                            title = mode.name,
                            description = mode.description,
                            selected = mode.selected,
                            contentDescription = "工作模式，${mode.name}",
                            onClick = {
                                closeComposerExtensionMenu(animate = false)
                                if (mode.configId != null) {
                                    updateConfiguration(mode.configId, AgentConfigValue.Select(mode.id))
                                } else {
                                    updateLegacyMode(mode.id)
                                }
                            }
                        ))
                    }
                }
                ComposerExtensionRoute.Permissions -> {
                    permission?.choices.orEmpty().forEach { choice ->
                        addView(sessionPermissionChoiceRow(
                            title = choice.name,
                            description = choice.description,
                            selected = choice.value == permission?.currentValue,
                            contentDescription = "会话权限，${choice.name}",
                            onClick = {
                                closeComposerExtensionMenu(animate = false)
                                permission?.let { option ->
                                    updateConfiguration(option.id, AgentConfigValue.Select(choice.value))
                                }
                            },
                        ))
                    }
                    if (permission == null) {
                        addView(settingsMessage("当前 Agent 尚未提供可选权限策略"))
                    }
                }
            }
        }
        val viewportHeight = root.height.takeIf { it > 0 } ?: context.resources.displayMetrics.heightPixels
        val maxHeight = AgentSurfaceNavigationPolicy.sessionPanelMaxHeight(
            viewportHeight = viewportHeight,
            composerHeight = composerArea.height,
            topBarHeight = topBar.height,
            preferredHeight = if (composerExtensionRoute == ComposerExtensionRoute.Main) ui.dp(240) else ui.dp(340),
            minimumHeight = ui.dp(140),
            outerSpacing = ui.dp(32)
        )
        val availableWidth = (root.width.takeIf { it > 0 } ?: context.resources.displayMetrics.widthPixels) - ui.dp(36)
        val panel = MaxHeightScrollView(context, maxHeight).apply {
            isFillViewport = false
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            isClickable = true
            elevation = ui.dp(12).toFloat()
            background = ui.roundedBox(
                agentSurface,
                android.graphics.Color.TRANSPARENT,
                ui.dp(24).toFloat()
            )
            setOnClickListener { }
            addView(content, ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ))
        }
        composerExtensionOverlay.removeAllViews()
        composerExtensionOverlay.addView(panel, FrameLayout.LayoutParams(
            minOf(
                availableWidth,
                ui.dp(
                    when (composerExtensionRoute) {
                        ComposerExtensionRoute.Main -> 218
                        ComposerExtensionRoute.Permissions -> 284
                        ComposerExtensionRoute.Modes -> 286
                    }
                )
            ),
            ViewGroup.LayoutParams.WRAP_CONTENT,
            (if (composerExtensionRoute == ComposerExtensionRoute.Permissions) Gravity.END else Gravity.START) or
                Gravity.BOTTOM
        ).apply {
            if (composerExtensionRoute == ComposerExtensionRoute.Permissions) {
                marginEnd = ui.dp(18)
            } else {
                marginStart = ui.dp(28)
            }
            bottomMargin = composerArea.height + ui.dp(14)
        })
        if (animateContent) {
            panel.alpha = 0f
            panel.translationY = ui.dp(8).toFloat()
            panel.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(170L)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }
    }

    private fun composerExtensionActionRow(
        icon: Int,
        title: String,
        value: String? = null,
        showChevron: Boolean = false,
        onClick: () -> Unit
    ): View = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = ui.dp(48)
        setPadding(ui.dp(6), ui.dp(2), ui.dp(5), ui.dp(2))
        background = ui.roundedBox(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT, ui.dp(15).toFloat())
        addView(ImageView(context).apply {
            setImageResource(icon)
            imageTintList = ColorStateList.valueOf(tokens.textPrimary)
            setPadding(ui.dp(9), ui.dp(9), ui.dp(9), ui.dp(9))
        }, LinearLayout.LayoutParams(ui.dp(40), ui.dp(40)))
        addView(TextView(context).apply {
            text = title
            textSize = 14.5f
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setTextColor(tokens.textPrimary)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginStart = ui.dp(5)
        })
        value?.takeIf(String::isNotBlank)?.let { currentValue ->
            addView(TextView(context).apply {
                text = currentValue
                textSize = 12f
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                gravity = Gravity.END
                setTextColor(tokens.textSecondary)
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.6f))
        }
        if (showChevron) {
            addView(ImageView(context).apply {
                setImageResource(R.drawable.ic_chevron_right_light)
                imageTintList = ColorStateList.valueOf(tokens.textSecondary)
                setPadding(ui.dp(10), ui.dp(10), ui.dp(10), ui.dp(10))
            }, LinearLayout.LayoutParams(ui.dp(40), ui.dp(40)))
        }
        isClickable = true
        isFocusable = true
        contentDescription = listOfNotNull(title, value).joinToString("，")
        setOnClickListener { onClick() }
    }.also { row ->
        row.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(ui.dp(1), ui.dp(1), ui.dp(1), ui.dp(1)) }
    }

    private fun composerExtensionHeader(title: String, onBack: (() -> Unit)?): View =
        LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = ui.dp(44)
            if (onBack != null) {
                addView(iconButton(context, R.drawable.ic_arrow_back_light, "返回", onBack),
                    LinearLayout.LayoutParams(ui.dp(40), ui.dp(40)))
            } else {
                addView(View(context), LinearLayout.LayoutParams(ui.dp(8), ui.dp(1)))
            }
            addView(TextView(context).apply {
                text = title
                textSize = 13.5f
                typeface = Typeface.DEFAULT_BOLD
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                setTextColor(tokens.textSecondary)
                setPadding(ui.dp(8), 0, ui.dp(8), 0)
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(iconButton(context, R.drawable.ic_close_light, "关闭") {
                closeComposerExtensionMenu()
            }, LinearLayout.LayoutParams(ui.dp(40), ui.dp(40)))
        }

    private fun openSlashCommandPalette() {
        closeComposerExtensionMenu(animate = false)
        input.setText("/")
        input.setSelection(input.text?.length ?: 0)
        input.requestFocus()
        context.getSystemService(InputMethodManager::class.java)
            ?.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
        syncCommandPalette("/")
    }

    private fun syncCommandPalette(text: String) {
        val query = AgentSurfaceNavigationPolicy.slashCommandQuery(text)
        if (query == null) {
            closeCommandPalette(animate = false)
            return
        }
        val commands = AgentSurfaceNavigationPolicy.filterCommands(
            composerCommands(),
            query
        )
        if (commands.isEmpty()) {
            closeCommandPalette(animate = false)
            return
        }
        showCommandPalette(commands)
    }

    private fun showCommandPalette(commands: List<AgentCommand>) {
        val wasVisible = commandPaletteOverlay.visibility == View.VISIBLE
        rebuildCommandPalette(commands)
        commandPaletteOverlay.apply {
            visibility = View.VISIBLE
            if (!wasVisible) {
                alpha = 0f
                animate()
                    .alpha(1f)
                    .setDuration(120L)
                    .setInterpolator(DecelerateInterpolator())
                    .start()
            }
        }
    }

    private fun closeCommandPalette(animate: Boolean = true) {
        if (commandPaletteOverlay.visibility != View.VISIBLE) return
        if (!animate) {
            commandPaletteOverlay.animate().cancel()
            commandPaletteOverlay.visibility = View.GONE
            commandPaletteOverlay.removeAllViews()
            return
        }
        commandPaletteOverlay.animate()
            .alpha(0f)
            .setDuration(90L)
            .withEndAction {
                commandPaletteOverlay.visibility = View.GONE
                commandPaletteOverlay.removeAllViews()
            }
            .start()
    }

    private fun rebuildCommandPalette(commands: List<AgentCommand>) {
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ui.dp(8), ui.dp(8), ui.dp(8), ui.dp(8))
            commands.forEach { command -> addView(commandPaletteRow(command)) }
        }
        val viewportHeight = root.height.takeIf { it > 0 } ?: context.resources.displayMetrics.heightPixels
        val maxHeight = AgentSurfaceNavigationPolicy.sessionPanelMaxHeight(
            viewportHeight = viewportHeight,
            composerHeight = composerArea.height,
            topBarHeight = topBar.height,
            preferredHeight = ui.dp(360),
            minimumHeight = ui.dp(160),
            outerSpacing = ui.dp(28)
        )
        val panel = MaxHeightScrollView(context, maxHeight).apply {
            isFillViewport = false
            isVerticalScrollBarEnabled = true
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            isClickable = true
            elevation = ui.dp(12).toFloat()
            background = ui.roundedBox(agentSurface, tokens.border, ui.dp(20).toFloat(), ui.dp(1))
            addView(content, ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ))
        }
        val availableWidth = (root.width.takeIf { it > 0 } ?: context.resources.displayMetrics.widthPixels) - ui.dp(36)
        commandPaletteOverlay.removeAllViews()
        commandPaletteOverlay.addView(panel, FrameLayout.LayoutParams(
            minOf(availableWidth, ui.dp(370)),
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM
        ).apply {
            bottomMargin = composerArea.height + ui.dp(18)
        })
    }

    private fun commandPaletteRow(command: AgentCommand): View = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = ui.dp(48)
        setPadding(ui.dp(13), ui.dp(7), ui.dp(13), ui.dp(7))
        addView(TextView(context).apply {
            text = "/${command.name}"
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setTextColor(tokens.textPrimary)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.42f))
        addView(TextView(context).apply {
            text = command.description
            textSize = 12f
            gravity = Gravity.END
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setTextColor(tokens.textSecondary)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.58f))
        isClickable = true
        isFocusable = true
        contentDescription = "/${command.name}，${command.description}"
        setOnClickListener {
            input.setText(buildString {
                append('/').append(command.name)
                if (!command.inputHint.isNullOrBlank()) append(' ')
            })
            input.setSelection(input.text?.length ?: 0)
            input.requestFocus()
            closeCommandPalette(animate = false)
        }
    }.also { row ->
        row.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(0, ui.dp(1), 0, ui.dp(1)) }
    }

    private fun showMoreMenu(anchor: View) {
        ui.showAnchoredMenu(
            context = root.context,
            anchor = anchor,
            widthDp = 238,
            items = listOf(
                UiMenuItem(
                    label = "归档当前会话",
                    enabled = !providerId.isNullOrBlank() && !sessionId.isNullOrBlank(),
                    onClick = ::archiveCurrentSession
                ),
                UiMenuItem(
                    label = "Agent 设置",
                    onClick = { showAgentSettings(returnToDrawer = false) }
                ),
                UiMenuItem(label = "新建会话", onClick = { createNewSession() }),
                UiMenuItem(label = "在其他工作区新建会话", onClick = ::showNewWorkspaceSession),
                UiMenuItem(
                    label = "分支当前会话",
                    enabled = AgentRuntimeRegistry.session(instanceId)?.capabilities?.sessions?.fork == true,
                    onClick = ::forkCurrentSession
                ),
                UiMenuItem(
                    label = "关闭 Agent 实例",
                    role = UiActionRole.Danger,
                    onClick = onCloseInstance
                )
            )
        )
    }

    private fun archiveCurrentSession() {
        val currentProviderId = providerId?.takeIf(String::isNotBlank) ?: return
        val currentSessionId = sessionId?.takeIf(String::isNotBlank) ?: return
        val changed = sessionMetadataStore.archive(currentProviderId, currentSessionId)
        if (changed) {
            Toast.makeText(context, "已归档，可在 Agent 设置中恢复或删除", Toast.LENGTH_SHORT).show()
        }
        showSessionDrawer()
    }

    private fun showSessionConfigurationPanel() {
        if (pendingSessionConfigId != null) return
        closeCommandPalette(animate = false)
        closeComposerExtensionMenu(animate = false)
        sessionConfigurationRoute = SessionConfigurationRoute.Overview
        sessionConfigurationModelGroupId = null
        rebuildSessionConfigurationPanel(animateContent = false)
        sessionConfigurationOverlay.apply {
            visibility = View.VISIBLE
            alpha = 0f
            setOnClickListener { closeSessionConfigurationPanel() }
            animate()
                .alpha(1f)
                .setDuration(150L)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }
    }

    private fun closeSessionConfigurationPanel(animate: Boolean = true) {
        if (sessionConfigurationOverlay.visibility != View.VISIBLE) return
        if (!animate) {
            sessionConfigurationOverlay.animate().cancel()
            sessionConfigurationOverlay.visibility = View.GONE
            sessionConfigurationOverlay.removeAllViews()
            return
        }
        sessionConfigurationOverlay.animate()
            .alpha(0f)
            .setDuration(110L)
            .withEndAction {
                sessionConfigurationOverlay.visibility = View.GONE
                sessionConfigurationOverlay.removeAllViews()
            }
            .start()
    }

    private fun rebuildSessionConfigurationPanel(animateContent: Boolean) {
        if (sessionConfigurationOverlay.visibility != View.VISIBLE && animateContent) return
        val options = sessionConfigurationOptions()
        val availableWidth = (root.width.takeIf { it > 0 } ?: context.resources.displayMetrics.widthPixels) - ui.dp(36)
        val panelWidth = minOf(
            availableWidth,
            ui.dp(if (sessionConfigurationRoute == SessionConfigurationRoute.Overview) 228 else 340)
        )
        val viewportHeight = root.height.takeIf { it > 0 } ?: context.resources.displayMetrics.heightPixels
        val maxHeight = AgentSurfaceNavigationPolicy.sessionPanelMaxHeight(
            viewportHeight = viewportHeight,
            composerHeight = composerArea.height,
            topBarHeight = topBar.height,
            preferredHeight = ui.dp(430),
            minimumHeight = ui.dp(220),
            outerSpacing = ui.dp(32)
        )
        val panel = MaxHeightScrollView(context, maxHeight).apply {
            isFillViewport = false
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            isClickable = true
            elevation = ui.dp(12).toFloat()
            background = ui.roundedBox(
                agentSurface,
                android.graphics.Color.TRANSPARENT,
                ui.dp(24).toFloat()
            )
            setOnClickListener { }
            addView(buildSessionConfigurationPanelContent(options), ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ))
        }
        sessionConfigurationOverlay.removeAllViews()
        sessionConfigurationOverlay.addView(panel, FrameLayout.LayoutParams(
            panelWidth,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.START or Gravity.BOTTOM
        ).apply {
            marginStart = ui.dp(28)
            bottomMargin = composerArea.height + ui.dp(14)
        })
        if (animateContent) {
            panel.alpha = 0f
            panel.translationY = ui.dp(8).toFloat()
            panel.scaleX = 0.98f
            panel.scaleY = 0.98f
            panel.animate()
                .alpha(1f)
                .translationY(0f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(170L)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }
    }

    private fun buildSessionConfigurationPanelContent(options: List<AgentConfigOption>): View =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ui.dp(10), ui.dp(8), ui.dp(10), ui.dp(12))
            when (sessionConfigurationRoute) {
                SessionConfigurationRoute.Overview -> buildSessionConfigurationOverview(this, options)
                SessionConfigurationRoute.ModelProviders -> buildSessionModelProviders(this, options)
                SessionConfigurationRoute.Models -> buildSessionModels(this, options)
            }
        }

    private fun buildSessionConfigurationOverview(
        host: LinearLayout,
        options: List<AgentConfigOption>
    ) {
        if (options.isEmpty()) {
            host.addView(settingsMessage("当前 Agent 尚未提供可选模型"))
            return
        }
        val thoughtLevel = options.firstOrNull { it.category == AgentConfigCategory.ThoughtLevel }
        val model = options.firstOrNull { it.category == AgentConfigCategory.Model }
        val remaining = options.filterNot { it === thoughtLevel || it === model }

        thoughtLevel?.let { option ->
            when (option) {
                is AgentConfigOption.Select -> {
                    host.addView(sessionSectionLabel(option.sessionSettingTitle()))
                    option.choices.forEach { choice ->
                        host.addView(sessionChoiceRow(
                            title = choice.name,
                            description = choice.description,
                            selected = choice.value == option.currentValue,
                            contentDescription = "${option.sessionSettingTitle()}，${choice.name}",
                            onClick = {
                                updateConfiguration(option.id, AgentConfigValue.Select(choice.value))
                            }
                        ))
                    }
                }
                is AgentConfigOption.Toggle -> host.addView(sessionChoiceRow(
                    title = option.sessionSettingTitle(),
                    description = option.description ?: if (option.currentValue) "当前已开启" else "当前已关闭",
                    selected = option.currentValue,
                    contentDescription = "${option.sessionSettingTitle()}，${option.currentValueLabel()}",
                    onClick = {
                        updateConfiguration(option.id, AgentConfigValue.Toggle(!option.currentValue))
                    }
                ))
            }
        }
        if (thoughtLevel != null && model != null) {
            host.addView(View(context).apply {
                setBackgroundColor(tokens.border)
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(1)).apply {
                setMargins(ui.dp(13), ui.dp(6), ui.dp(13), ui.dp(6))
            })
        }
        (model as? AgentConfigOption.Select)?.let { option ->
            host.addView(sessionNavigationRow(
                title = option.sessionSettingTitle(),
                value = option.currentValueLabel(),
                description = null,
                onClick = {
                    val groups = AgentSurfaceNavigationPolicy.modelChoiceGroups(option)
                    sessionConfigurationRoute = if (groups.isEmpty()) {
                        SessionConfigurationRoute.Models
                    } else {
                        SessionConfigurationRoute.ModelProviders
                    }
                    sessionConfigurationModelGroupId = groups.firstOrNull { group ->
                        group.choices.any { it.value == option.currentValue }
                    }?.id
                    rebuildSessionConfigurationPanel(animateContent = true)
                }
            ))
        }
        remaining.forEach { option ->
            when (option) {
                is AgentConfigOption.Select -> {
                    host.addView(sessionSectionLabel(option.sessionSettingTitle()))
                    option.choices.forEach { choice ->
                        host.addView(sessionChoiceRow(
                            title = choice.name,
                            description = choice.description,
                            selected = choice.value == option.currentValue,
                            contentDescription = "${option.sessionSettingTitle()}，${choice.name}",
                            onClick = {
                                updateConfiguration(option.id, AgentConfigValue.Select(choice.value))
                            }
                        ))
                    }
                }
                is AgentConfigOption.Toggle -> host.addView(sessionChoiceRow(
                    title = option.sessionSettingTitle(),
                    description = option.description ?: if (option.currentValue) "当前已开启" else "当前已关闭",
                    selected = option.currentValue,
                    contentDescription = "${option.sessionSettingTitle()}，${option.currentValueLabel()}",
                    onClick = {
                        updateConfiguration(option.id, AgentConfigValue.Toggle(!option.currentValue))
                    }
                ))
            }
        }
    }

    private fun buildSessionModelProviders(host: LinearLayout, options: List<AgentConfigOption>) {
        val model = options.filterIsInstance<AgentConfigOption.Select>()
            .firstOrNull { it.category == AgentConfigCategory.Model }
        val groups = model?.let(AgentSurfaceNavigationPolicy::modelChoiceGroups).orEmpty()
        host.addView(sessionPanelHeader("选择供应商") {
            sessionConfigurationRoute = SessionConfigurationRoute.Overview
            rebuildSessionConfigurationPanel(animateContent = true)
        })
        if (model == null || groups.isEmpty()) {
            host.addView(settingsMessage("当前 Agent 没有可分组的模型"))
            return
        }
        val grid = GridLayout(context).apply {
            columnCount = 2
            alignmentMode = GridLayout.ALIGN_BOUNDS
            useDefaultMargins = false
        }
        groups.forEachIndexed { index, group ->
            val current = group.choices.any { it.value == model.currentValue }
            val row = index / 2
            val column = index % 2
            grid.addView(
                sessionModelProviderCard(group.name, group.choices.size, current) {
                    sessionConfigurationModelGroupId = group.id
                    sessionConfigurationRoute = SessionConfigurationRoute.Models
                    rebuildSessionConfigurationPanel(animateContent = true)
                },
                GridLayout.LayoutParams(
                    GridLayout.spec(row, 1f),
                    GridLayout.spec(column, 1f)
                ).apply {
                    width = 0
                    height = ui.dp(66)
                    setMargins(
                        if (column == 0) ui.dp(2) else ui.dp(4),
                        ui.dp(3),
                        if (column == 0) ui.dp(4) else ui.dp(2),
                        ui.dp(3)
                    )
                }
            )
        }
        host.addView(grid, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))
    }

    private fun sessionModelProviderCard(
        name: String,
        modelCount: Int,
        selected: Boolean,
        onClick: () -> Unit
    ): View = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(ui.dp(10), ui.dp(7), ui.dp(7), ui.dp(7))
        background = ui.roundedBox(
            agentSettingsSurface,
            android.graphics.Color.TRANSPARENT,
            ui.dp(17).toFloat()
        )
        addView(ImageView(context).apply {
            setImageResource(R.drawable.ic_bridge)
            imageTintList = ColorStateList.valueOf(tokens.textPrimary)
            setPadding(ui.dp(6), ui.dp(6), ui.dp(6), ui.dp(6))
        }, LinearLayout.LayoutParams(ui.dp(34), ui.dp(34)))
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(context).apply {
                text = name
                textSize = 13f
                typeface = if (selected) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                setTextColor(tokens.textPrimary)
            })
            addView(TextView(context).apply {
                text = "$modelCount 个模型"
                textSize = 10.5f
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                setTextColor(tokens.textSecondary)
                setPadding(0, ui.dp(2), 0, 0)
            })
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginStart = ui.dp(5)
        })
        contentDescription = "供应商 $name，$modelCount 个模型"
        isClickable = pendingSessionConfigId == null
        isFocusable = true
        alpha = if (pendingSessionConfigId == null) 1f else 0.55f
        setOnClickListener { onClick() }
    }

    private fun buildSessionModels(host: LinearLayout, options: List<AgentConfigOption>) {
        val model = options.filterIsInstance<AgentConfigOption.Select>()
            .firstOrNull { it.category == AgentConfigCategory.Model }
        val groups = model?.let(AgentSurfaceNavigationPolicy::modelChoiceGroups).orEmpty()
        val group = groups.firstOrNull { it.id == sessionConfigurationModelGroupId }
        val choices = group?.choices ?: model?.choices.orEmpty()
        host.addView(sessionPanelHeader(group?.name ?: "选择模型") {
            sessionConfigurationRoute = if (groups.isEmpty()) {
                SessionConfigurationRoute.Overview
            } else {
                SessionConfigurationRoute.ModelProviders
            }
            rebuildSessionConfigurationPanel(animateContent = true)
        })
        if (model == null || choices.isEmpty()) {
            host.addView(settingsMessage("当前 Agent 没有可选模型"))
            return
        }
        choices.forEach { choice ->
            host.addView(sessionChoiceRow(
                title = choice.name,
                description = choice.description ?: choice.value.takeIf { it != choice.name },
                selected = choice.value == model.currentValue,
                contentDescription = "模型 ${choice.name}",
                onClick = {
                    sessionConfigurationRoute = SessionConfigurationRoute.Overview
                    updateConfiguration(model.id, AgentConfigValue.Select(choice.value))
                }
            ))
        }
    }

    private fun sessionPanelHeader(title: String, onBack: (() -> Unit)?): View = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = ui.dp(48)
        if (onBack != null) {
            addView(iconButton(context, R.drawable.ic_arrow_back_light, "返回", onBack),
                LinearLayout.LayoutParams(ui.dp(42), ui.dp(42)))
        } else {
            addView(View(context), LinearLayout.LayoutParams(ui.dp(8), ui.dp(1)))
        }
        addView(TextView(context).apply {
            text = title
            textSize = 15.5f
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setTextColor(tokens.textPrimary)
            setPadding(ui.dp(8), 0, ui.dp(8), 0)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(iconButton(context, R.drawable.ic_close_light, "关闭会话配置") {
            closeSessionConfigurationPanel()
        }, LinearLayout.LayoutParams(ui.dp(42), ui.dp(42)))
    }

    private fun sessionSectionLabel(label: String): View = TextView(context).apply {
        text = label
        textSize = 12.5f
        typeface = Typeface.DEFAULT_BOLD
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
        setTextColor(tokens.textSecondary)
        setPadding(ui.dp(13), ui.dp(12), ui.dp(13), ui.dp(5))
    }

    private fun sessionNavigationRow(
        title: String,
        value: String,
        description: String?,
        onClick: () -> Unit
    ): View = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = ui.dp(60)
        setPadding(ui.dp(14), ui.dp(7), ui.dp(5), ui.dp(7))
        background = ui.roundedBox(agentSettingsSurface, android.graphics.Color.TRANSPARENT, ui.dp(17).toFloat())
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
            description?.takeIf(String::isNotBlank)?.let { detail ->
                addView(TextView(context).apply {
                    text = detail
                    textSize = 11.5f
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                    setTextColor(tokens.textTertiary)
                    setPadding(0, ui.dp(2), 0, 0)
                })
            }
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(ImageView(context).apply {
            setImageResource(R.drawable.ic_chevron_right_light)
            imageTintList = ColorStateList.valueOf(tokens.textSecondary)
            setPadding(ui.dp(11), ui.dp(11), ui.dp(11), ui.dp(11))
        }, LinearLayout.LayoutParams(ui.dp(42), ui.dp(42)))
        contentDescription = "$title，$value"
        isClickable = pendingSessionConfigId == null
        isFocusable = true
        setOnClickListener { onClick() }
    }.also { row ->
        row.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(ui.dp(2), ui.dp(2), ui.dp(2), ui.dp(5)) }
    }

    private fun sessionChoiceRow(
        title: String,
        description: String?,
        selected: Boolean,
        contentDescription: String,
        showChevron: Boolean = false,
        onClick: () -> Unit
    ): View = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = ui.dp(52)
        setPadding(ui.dp(14), ui.dp(7), ui.dp(6), ui.dp(7))
        background = ui.roundedBox(
            android.graphics.Color.TRANSPARENT,
            android.graphics.Color.TRANSPARENT,
            ui.dp(16).toFloat()
        )
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
        when {
            showChevron -> addView(ImageView(context).apply {
                setImageResource(R.drawable.ic_chevron_right_light)
                imageTintList = ColorStateList.valueOf(tokens.textSecondary)
                setPadding(ui.dp(10), ui.dp(10), ui.dp(10), ui.dp(10))
            }, LinearLayout.LayoutParams(ui.dp(40), ui.dp(40)))
            selected -> addView(ImageView(context).apply {
                setImageResource(R.drawable.ic_check_light)
                imageTintList = ColorStateList.valueOf(tokens.textPrimary)
                setPadding(ui.dp(10), ui.dp(10), ui.dp(10), ui.dp(10))
            }, LinearLayout.LayoutParams(ui.dp(40), ui.dp(40)))
        }
        this.contentDescription = contentDescription
        isClickable = pendingSessionConfigId == null
        isFocusable = true
        alpha = if (pendingSessionConfigId == null) 1f else 0.55f
        setOnClickListener { onClick() }
    }.also { row ->
        row.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(ui.dp(2), ui.dp(1), ui.dp(2), ui.dp(1)) }
    }

    private fun sessionPermissionChoiceRow(
        title: String,
        description: String?,
        selected: Boolean,
        contentDescription: String,
        onClick: () -> Unit,
    ): View = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = ui.dp(62)
        setPadding(ui.dp(15), ui.dp(8), ui.dp(7), ui.dp(8))
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(context).apply {
                text = title
                textSize = 15f
                includeFontPadding = false
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                setTextColor(tokens.textPrimary)
            })
            description?.takeIf(String::isNotBlank)?.let { detail ->
                addView(TextView(context).apply {
                    text = detail
                    textSize = 12.5f
                    includeFontPadding = false
                    maxLines = 2
                    ellipsize = TextUtils.TruncateAt.END
                    setTextColor(tokens.textTertiary)
                    setPadding(0, ui.dp(5), 0, 0)
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
        isClickable = pendingSessionConfigId == null
        isFocusable = true
        alpha = if (pendingSessionConfigId == null) 1f else 0.55f
        setOnClickListener { onClick() }
    }.also { row ->
        row.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(ui.dp(2), ui.dp(1), ui.dp(2), ui.dp(1)) }
    }

    private fun AgentConfigOption.currentValueLabel(): String = when (this) {
        is AgentConfigOption.Select -> choices.firstOrNull { it.value == currentValue }?.name ?: currentValue
        is AgentConfigOption.Toggle -> if (currentValue) "开启" else "关闭"
    }

    private fun AgentConfigOption.withDraftValue(value: AgentConfigValue?): AgentConfigOption = when {
        this is AgentConfigOption.Select && value is AgentConfigValue.Select -> copy(currentValue = value.value)
        this is AgentConfigOption.Toggle && value is AgentConfigValue.Toggle -> copy(currentValue = value.value)
        else -> this
    }

    private fun AgentConfigOption.sessionSettingTitle(): String = when (category) {
        AgentConfigCategory.Model -> "模型"
        AgentConfigCategory.ThoughtLevel -> "推理强度"
        AgentConfigCategory.Mode -> "工作模式"
        AgentConfigCategory.Permission -> "权限"
        AgentConfigCategory.ModelConfiguration -> "模型设置"
        else -> name
    }

    private fun updateConfiguration(configId: String, value: AgentConfigValue) {
        if (pendingSessionConfigId != null || draftPreparationPending) return
        val runtime = AgentRuntimeRegistry.session(instanceId)
        if (runtime?.generation == generation && runtime.isDraft) {
            val result = if (configId == AgentDraftModelPolicy.CONFIG_ID && value is AgentConfigValue.Select) {
                val snapshot = draftModelSnapshot ?: return
                val selection = AgentDraftModelPolicy.selection(snapshot, value.value) ?: return
                AgentRuntimeRegistry.selectDraftModel(instanceId, generation, selection)
            } else {
                AgentRuntimeRegistry.selectDraftConfiguration(instanceId, generation, configId, value)
            }
            when (result) {
                is AgentOperationResult.Success -> {
                    sessionConfigurationRoute = SessionConfigurationRoute.Overview
                    renderSessionConfigurationControls()
                    if (sessionConfigurationOverlay.visibility == View.VISIBLE) {
                        rebuildSessionConfigurationPanel(animateContent = true)
                    }
                    Toast.makeText(context, "已为本次新会话预选", Toast.LENGTH_SHORT).show()
                }
                else -> showOperationResult(result, null)
            }
            return
        }
        pendingSessionConfigId = configId
        renderSessionConfigurationControls()
        if (sessionConfigurationOverlay.visibility == View.VISIBLE) {
            rebuildSessionConfigurationPanel(animateContent = false)
        }
        lifecycleOwner.lifecycleScope.launch {
            val result = AgentRuntimeRegistry.setConfiguration(instanceId, generation, configId, value)
            pendingSessionConfigId = null
            if (result is AgentOperationResult.Success && result.value != currentSnapshot?.configuration) {
                currentSnapshot = currentSnapshot?.copy(configuration = result.value)
            }
            renderSessionConfigurationControls()
            if (sessionConfigurationOverlay.visibility == View.VISIBLE) {
                rebuildSessionConfigurationPanel(animateContent = true)
            }
            showOperationResult(result, "当前会话配置已更新")
        }
    }

    private fun updateLegacyMode(modeId: String) {
        if (pendingSessionConfigId != null || draftPreparationPending) return
        val runtime = AgentRuntimeRegistry.session(instanceId)
        if (runtime?.generation == generation && runtime.isDraft) {
            when (val result = AgentRuntimeRegistry.selectDraftMode(instanceId, generation, modeId)) {
                is AgentOperationResult.Success -> {
                    renderSessionConfigurationControls()
                    Toast.makeText(context, "已为本次新会话预选工作模式", Toast.LENGTH_SHORT).show()
                }
                else -> showOperationResult(result, null)
            }
            return
        }
        pendingSessionConfigId = LEGACY_MODE_PENDING_ID
        lifecycleOwner.lifecycleScope.launch {
            val result = AgentRuntimeRegistry.setMode(instanceId, generation, modeId)
            pendingSessionConfigId = null
            showOperationResult(result, "工作模式已更新")
        }
    }

    private fun showNewWorkspaceSession() {
        if (draftPreparationPending) return
        val targetAgentId = agentId?.takeIf(String::isNotBlank) ?: return
        projectEditorDialog?.dismiss()
        projectEditorDialog = AgentProjectEditorDialog(
            context = context,
            tokens = tokens,
            onChooseDirectory = ::showWorkspaceDirectoryPicker,
        ) { name, cwd, handle ->
            when (val saved = projectStore.save(targetAgentId, name, cwd)) {
                is AgentProjectSaveResult.Failure -> handle.showError(saved.message)
                is AgentProjectSaveResult.Success -> {
                    handle.dismiss()
                    projectEditorDialog = null
                    expandedProjectCwds.add(
                        AgentSurfaceNavigationPolicy.normalizeCwd(saved.project.cwd)
                    )
                    closeNavigation()
                    createNewSession(saved.project.cwd)
                }
            }
        }.also(AgentProjectEditorDialog::show)
    }

    private fun showWorkspaceDirectoryPicker() {
        val editor = projectEditorDialog?.takeIf(AgentProjectEditorDialog::isShowing) ?: return
        workspaceDirectoryPickerDialog?.dismiss()
        workspaceDirectoryPickerDialog = WorkspaceDirectoryPickerDialog(
            context = context,
            tokens = tokens,
            scope = lifecycleOwner.lifecycleScope,
            hostWorkspaceRoot = KFContainerManager.resolveWorkspaceDirectory(context),
            initialContainerPath = editor.currentDirectory()
                ?: KiteStorageContract.CONTAINER_WORKSPACE_ROOT,
        ) { cwd ->
            workspaceDirectoryPickerDialog = null
            projectEditorDialog
                ?.takeIf(AgentProjectEditorDialog::isShowing)
                ?.updateDirectory(cwd)
        }.also(WorkspaceDirectoryPickerDialog::show)
    }

    private fun forkCurrentSession() {
        lifecycleOwner.lifecycleScope.launch {
            showOperationResult(
                AgentRuntimeRegistry.forkSession(instanceId, generation),
                "已创建会话分支"
            )
        }
    }

    private fun showOperationResult(result: AgentOperationResult<*>, successMessage: String?) {
        when (result) {
            is AgentOperationResult.Success -> successMessage?.let {
                Toast.makeText(root.context, it, Toast.LENGTH_SHORT).show()
            }
            is AgentOperationResult.Unsupported -> Toast.makeText(
                root.context,
                "当前 Agent 不支持：${result.operation}",
                Toast.LENGTH_SHORT
            ).show()
            is AgentOperationResult.Failure -> Toast.makeText(root.context, result.message, Toast.LENGTH_LONG).show()
        }
    }

    private fun phaseLabel(phase: AgentSessionPhase): String = when (phase) {
        AgentSessionPhase.Preparing -> "正在准备 Agent 会话"
        AgentSessionPhase.Ready -> "准备就绪"
        AgentSessionPhase.Prompting -> "正在生成回复"
        AgentSessionPhase.WaitingPermission -> "等待权限选择"
        AgentSessionPhase.Cancelling -> "正在停止生成"
        AgentSessionPhase.Cancelled -> "已停止生成"
        AgentSessionPhase.Failed -> "Agent 会话失败"
        AgentSessionPhase.Closed -> "Agent 会话已关闭"
    }

    private fun connectionStatusLabel(status: com.kite.app.run.CardRunAgentConnectionStatus): String = when (status) {
        com.kite.app.run.CardRunAgentConnectionStatus.Preparing -> "正在准备 Agent 会话"
        com.kite.app.run.CardRunAgentConnectionStatus.Ready -> "准备就绪"
        com.kite.app.run.CardRunAgentConnectionStatus.WaitingPermission -> "等待权限选择"
        com.kite.app.run.CardRunAgentConnectionStatus.Disconnected -> "连接已断开"
        com.kite.app.run.CardRunAgentConnectionStatus.Failed -> "Agent 会话失败"
        com.kite.app.run.CardRunAgentConnectionStatus.Stopped -> "Agent 会话已关闭"
    }

    private data class SettingsRow(
        val title: String,
        val subtitle: String,
        val onClick: (() -> Unit)? = null
    )

    private data class AgentSettingsLoad(
        val registry: AgentRegistrySnapshot,
        val agentId: String?,
        val configResult: AgentConfigReadResult?
    )

    private data class AgentSessionListKey(
        val instanceId: String,
        val generation: Long,
        val providerId: String?
    )

    private data class AgentDialogAction(
        val label: String,
        val role: UiActionRole,
        val enabled: Boolean = true,
        val onClick: (Dialog, TextView) -> Unit
    )

    private data class AgentChoiceAction(
        val label: String,
        val selected: Boolean = false,
        val role: UiActionRole = UiActionRole.Primary,
        val onClick: () -> Unit,
    )

    private data class SkillImportOutcome(
        val applyResult: AgentConfigApplyResult?,
        val refreshed: AgentConfigReadResult?,
        val errorMessage: String?,
    )

    private data class PendingAttachment(
        val name: String,
        val content: AgentContent
    )

    private companion object {
        const val SESSION_SEARCH_WATCHER_TAG = "agent-session-search-watcher"
        const val COPY_BUFFER_SIZE = 8 * 1024
        const val MAX_INLINE_IMAGE_BYTES = 12 * 1024 * 1024
        const val LEGACY_MODE_PENDING_ID = "__kite_legacy_mode__"
        const val SESSION_LOADING_FEEDBACK_DELAY_MS = 140L
    }
}

private data class AgentComposerModeOption(
    val id: String,
    val name: String,
    val description: String?,
    val selected: Boolean,
    val configId: String?
)

internal object AgentSkillUiPolicy {
    fun activationLabel(activation: AgentSkillActivation): String = when (activation) {
        AgentSkillActivation.Enabled -> "已启用"
        AgentSkillActivation.ApprovalRequired -> "每次确认"
        AgentSkillActivation.ManualOnly -> "仅手动"
        AgentSkillActivation.Disabled -> "已停用"
        AgentSkillActivation.Unknown -> "状态未知"
    }

    fun scopeLabel(scope: AgentConfigScope): String = when (scope) {
        AgentConfigScope.User -> "用户级"
        AgentConfigScope.Project -> "项目级"
        AgentConfigScope.Workspace -> "工作区"
        AgentConfigScope.External -> "外部位置"
        AgentConfigScope.Unknown -> "作用域未知"
    }

    fun summary(skill: AgentSkillSummary): String =
        "${activationLabel(skill.activation)} · ${scopeLabel(skill.scope)}"
}

internal object AgentCoreDocumentUiPolicy {
    fun sectionTitle(document: AgentCoreDocumentDescriptor): String = when (document.scope) {
        AgentConfigScope.User -> "全局设定"
        AgentConfigScope.Project, AgentConfigScope.Workspace -> "当前工作区"
        AgentConfigScope.External -> "外部设定"
        AgentConfigScope.Unknown -> "其他设定"
    }

    fun sectionDescription(scope: AgentConfigScope): String = when (scope) {
        AgentConfigScope.User -> "由当前 Agent 跨工作区读取。"
        AgentConfigScope.Project, AgentConfigScope.Workspace -> "只影响当前工作目录及其适用范围。"
        AgentConfigScope.External -> "由 Agent 的外部配置位置提供。"
        AgentConfigScope.Unknown -> "实际作用范围由 Agent 原生规则决定。"
    }

    fun scopeLabel(scope: AgentConfigScope): String = when (scope) {
        AgentConfigScope.User -> "全局 · 当前 Agent"
        AgentConfigScope.Project -> "项目 · 当前工作目录"
        AgentConfigScope.Workspace -> "Agent 工作区"
        AgentConfigScope.External -> "外部配置"
        AgentConfigScope.Unknown -> "由 Agent 决定"
    }

    fun semanticsLabel(semantics: AgentCoreDocumentSemantics): String = when (semantics) {
        AgentCoreDocumentSemantics.SupplementalInstructions -> "追加到 Agent 的长期说明"
        AgentCoreDocumentSemantics.FullSystemPromptReplacement -> "完整替换主 Agent 系统提示"
        AgentCoreDocumentSemantics.Persona -> "定义人格、语气与边界"
        AgentCoreDocumentSemantics.UserProfile -> "提供长期用户资料"
        AgentCoreDocumentSemantics.Identity -> "定义 Agent 名称与身份"
    }

    fun summary(document: AgentCoreDocumentDescriptor): String = buildString {
        append(document.fileName)
        append(" · ")
        append(if (document.exists) "已存在" else "尚未创建")
        if (!document.writable) append(" · 只读")
        append(" · ")
        append(semanticsLabel(document.semantics))
    }
}

private data class AgentMcpListItem(
    val server: AgentMcpSummary,
    val connectionState: AgentMcpConnectionState,
    val connectionMessage: String?,
    val pending: Boolean,
)

private class AgentMcpListAdapter(
    private val context: Context,
    private val tokens: ThemeTokens,
    private val onClick: (AgentMcpListItem) -> Unit,
    private val onConnectionCheck: (AgentMcpListItem) -> Unit,
) : ListAdapter<AgentMcpListItem, AgentMcpListAdapter.Holder>(Diff) {
    private val ui = UiKit(context, tokens)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val title = TextView(context)
        val summary = TextView(context)
        val detail = TextView(context)
        val checkAction = TextView(context)
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(ui.dp(15), 0, ui.dp(9), 0)
            background = ui.roundedBox(
                tokens.cardBackground,
                android.graphics.Color.TRANSPARENT,
                ui.dp(21).toFloat(),
            )
            addView(ImageView(context).apply {
                setImageResource(R.drawable.ic_mcp_light)
                imageTintList = ColorStateList.valueOf(tokens.textSecondary)
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                setPadding(ui.dp(9), ui.dp(9), ui.dp(9), ui.dp(9))
                background = ui.roundedBox(
                    tokens.inputBackground,
                    android.graphics.Color.TRANSPARENT,
                    ui.dp(20).toFloat(),
                )
            }, LinearLayout.LayoutParams(ui.dp(40), ui.dp(40)))
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_VERTICAL
                addView(LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    addView(title.apply {
                        textSize = 14.5f
                        typeface = Typeface.DEFAULT_BOLD
                        maxLines = 1
                        ellipsize = TextUtils.TruncateAt.END
                        setTextColor(tokens.textPrimary)
                    }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                    addView(summary.apply {
                        textSize = 11.5f
                        maxLines = 1
                        setTextColor(tokens.textSecondary)
                        setPadding(ui.dp(8), 0, 0, 0)
                    })
                })
                addView(detail.apply {
                    textSize = 12f
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                    setTextColor(tokens.textSecondary)
                    setPadding(0, ui.dp(3), 0, 0)
                })
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(ui.dp(13), 0, ui.dp(4), 0)
            })
            addView(checkAction.apply {
                text = "检查"
                textSize = 12.5f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setTextColor(tokens.primaryStrong)
                setPadding(ui.dp(8), ui.dp(7), ui.dp(8), ui.dp(7))
                isClickable = true
                isFocusable = true
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ui.dp(40)))
            addView(ImageView(context).apply {
                setImageResource(R.drawable.ic_chevron_right_light)
                imageTintList = ColorStateList.valueOf(tokens.textTertiary)
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                contentDescription = null
            }, LinearLayout.LayoutParams(ui.dp(40), ui.dp(40)))
            isClickable = true
            isFocusable = true
        }
        row.layoutParams = RecyclerView.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ui.dp(74),
        ).apply { setMargins(0, ui.dp(4), 0, ui.dp(4)) }
        return Holder(row, title, summary, detail, checkAction)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val item = getItem(position)
        val server = item.server
        holder.title.text = server.id
        holder.summary.text = AgentMcpUiPolicy.transportLabel(server)
        holder.detail.text = if (item.pending) {
            "正在更新…"
        } else {
            val status = AgentMcpUiPolicy.connectionLabel(server, item.connectionState)
            item.connectionMessage?.let { "$status · $it" } ?: status
        }
        holder.itemView.isEnabled = !item.pending
        holder.itemView.alpha = if (item.pending) 0.68f else 1f
        holder.itemView.contentDescription = "${server.id}，${holder.summary.text}，${holder.detail.text}"
        holder.itemView.setOnClickListener { if (!item.pending) onClick(item) }
        holder.checkAction.visibility = if (
            !item.pending && AgentMcpUiPolicy.supportsConnectionCheck(server)
        ) View.VISIBLE else View.GONE
        holder.checkAction.contentDescription = "检查 ${server.id} 的连接"
        holder.checkAction.setOnClickListener {
            if (!item.pending && AgentMcpUiPolicy.supportsConnectionCheck(server)) onConnectionCheck(item)
        }
    }

    class Holder(
        itemView: View,
        val title: TextView,
        val summary: TextView,
        val detail: TextView,
        val checkAction: TextView,
    ) : RecyclerView.ViewHolder(itemView)

    private object Diff : DiffUtil.ItemCallback<AgentMcpListItem>() {
        override fun areItemsTheSame(oldItem: AgentMcpListItem, newItem: AgentMcpListItem): Boolean =
            oldItem.server.id == newItem.server.id

        override fun areContentsTheSame(oldItem: AgentMcpListItem, newItem: AgentMcpListItem): Boolean =
            oldItem == newItem
    }
}

private class AgentSkillListAdapter(
    private val context: Context,
    private val tokens: ThemeTokens,
    private val onClick: (AgentSkillSummary) -> Unit,
) : ListAdapter<AgentSkillSummary, AgentSkillListAdapter.Holder>(Diff) {
    private val ui = UiKit(context, tokens)
    private var pendingSkillId: String? = null

    fun submit(skills: List<AgentSkillSummary>, pendingSkillId: String?) {
        this.pendingSkillId = pendingSkillId
        submitList(skills.sortedBy { it.displayName.lowercase() }) {
            notifyDataSetChanged()
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val title = TextView(context)
        val summary = TextView(context)
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(ui.dp(15), 0, ui.dp(9), 0)
            background = ui.roundedBox(
                tokens.cardBackground,
                android.graphics.Color.TRANSPARENT,
                ui.dp(21).toFloat(),
            )
            addView(ImageView(context).apply {
                setImageResource(R.drawable.ic_skill_light)
                imageTintList = ColorStateList.valueOf(tokens.textSecondary)
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                setPadding(ui.dp(10), ui.dp(10), ui.dp(10), ui.dp(10))
                background = ui.roundedBox(
                    tokens.inputBackground,
                    android.graphics.Color.TRANSPARENT,
                    ui.dp(20).toFloat(),
                )
            }, LinearLayout.LayoutParams(ui.dp(40), ui.dp(40)))
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_VERTICAL
                addView(title.apply {
                    textSize = 14.5f
                    typeface = Typeface.DEFAULT_BOLD
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                    setTextColor(tokens.textPrimary)
                })
                addView(summary.apply {
                    textSize = 12f
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                    setTextColor(tokens.textSecondary)
                    setPadding(0, ui.dp(3), 0, 0)
                })
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(ui.dp(13), 0, ui.dp(4), 0)
            })
            addView(ImageView(context).apply {
                setImageResource(R.drawable.ic_chevron_right_light)
                imageTintList = ColorStateList.valueOf(tokens.textTertiary)
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                contentDescription = null
            }, LinearLayout.LayoutParams(ui.dp(40), ui.dp(40)))
            isClickable = true
            isFocusable = true
        }
        row.layoutParams = RecyclerView.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ui.dp(70),
        ).apply { setMargins(0, ui.dp(4), 0, ui.dp(4)) }
        return Holder(row, title, summary)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val skill = getItem(position)
        val pending = pendingSkillId == skill.id
        val operationPending = pendingSkillId != null
        holder.title.text = skill.displayName
        holder.summary.text = if (pending) "正在更新…" else AgentSkillUiPolicy.summary(skill)
        holder.itemView.isEnabled = !operationPending
        holder.itemView.alpha = if (operationPending && !pending) 0.55f else 1f
        holder.itemView.contentDescription = "${skill.displayName}，${holder.summary.text}"
        holder.itemView.setOnClickListener { if (!operationPending) onClick(skill) }
    }

    class Holder(
        itemView: View,
        val title: TextView,
        val summary: TextView,
    ) : RecyclerView.ViewHolder(itemView)

    private object Diff : DiffUtil.ItemCallback<AgentSkillSummary>() {
        override fun areItemsTheSame(oldItem: AgentSkillSummary, newItem: AgentSkillSummary): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: AgentSkillSummary, newItem: AgentSkillSummary): Boolean =
            oldItem == newItem
    }
}

private class MaxHeightScrollView(
    context: Context,
    private val maximumHeight: Int
) : ScrollView(context) {
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val cappedHeightSpec = View.MeasureSpec.makeMeasureSpec(maximumHeight, View.MeasureSpec.AT_MOST)
        super.onMeasure(widthMeasureSpec, cappedHeightSpec)
    }
}

private enum class SessionConfigurationRoute {
    Overview,
    ModelProviders,
    Models
}

private enum class ComposerExtensionRoute {
    Main,
    Modes,
    Permissions,
}

private enum class AgentNavigationScreen {
    Main,
    Drawer,
    SessionSearch,
    Settings,
    DefaultPermission,
    ProviderList,
    ProviderEditor,
    ProviderPresetPicker,
    ProviderModelEditor,
    SkillList,
    McpList,
    McpEditor,
    CoreDocumentList,
    CoreDocumentEditor,
    ArchivedContent
}

internal data class AgentSessionProjectGroup(
    val cwd: String,
    val name: String,
    val sessions: List<AgentSessionSummary>
)

internal data class AgentSessionGrouping(
    val defaultCwd: String,
    val defaultSessions: List<AgentSessionSummary>,
    val projects: List<AgentSessionProjectGroup>
)

internal sealed interface AgentDrawerAction {
    data class NewDraft(val cwd: String) : AgentDrawerAction
    data object ChooseProject : AgentDrawerAction
}

internal sealed interface AgentDrawerRow {
    val key: String

    data class SectionHeader(
        val title: String,
        val actionDescription: String,
        val action: AgentDrawerAction,
        override val key: String
    ) : AgentDrawerRow

    data class ProjectHeader(
        val project: AgentSessionProjectGroup,
        val expanded: Boolean
    ) : AgentDrawerRow {
        override val key: String = "project:${project.cwd}"
    }

    data class Session(
        val summary: AgentSessionSummary,
        val inProject: Boolean
    ) : AgentDrawerRow {
        override val key: String = "session:${summary.id}"
    }

    data class Empty(
        val label: String,
        override val key: String
    ) : AgentDrawerRow
}

internal sealed interface AgentArchivedRow {
    val key: String

    data class GroupHeader(
        val cwd: String,
        val title: String,
        val subtitle: String?,
        val count: Int,
        val expanded: Boolean,
        val archivedProject: AgentProject? = null,
    ) : AgentArchivedRow {
        override val key: String = "group:$cwd"
    }

    data class Session(val summary: AgentSessionSummary) : AgentArchivedRow {
        override val key: String = "session:${summary.id}"
    }
}

internal object AgentSurfaceNavigationPolicy {
    const val MODEL_ENTRY_LABEL = "模型"
    const val PERMISSION_ENTRY_LABEL = "权限"
    val fixedComposerEntries: List<String> = listOf(MODEL_ENTRY_LABEL, PERMISSION_ENTRY_LABEL)

    fun normalizeCwd(cwd: String): String {
        val cleaned = cwd.trim().replace('\\', '/')
        if (cleaned == "/") return cleaned
        return cleaned.trimEnd('/')
    }

    fun sameCwd(left: String, right: String): Boolean = normalizeCwd(left) == normalizeCwd(right)

    fun groupSessions(
        sessions: List<AgentSessionSummary>,
        defaultCwd: String,
        registeredProjects: List<AgentProject> = emptyList(),
        archivedProjectCwds: Set<String> = emptySet(),
    ): AgentSessionGrouping {
        val normalizedDefault = normalizeCwd(defaultCwd).ifBlank { "/workspace" }
        val normalizedArchivedCwds = archivedProjectCwds.mapTo(linkedSetOf(), ::normalizeCwd)
        val defaultSessions = mutableListOf<AgentSessionSummary>()
        val projects = linkedMapOf<String, ProjectBucket>()
        registeredProjects
            .sortedBy(AgentProject::createdAtMillis)
            .forEach { project ->
                val normalized = normalizeCwd(project.cwd)
                if (normalized.isNotBlank() && normalized != normalizedDefault &&
                    normalized !in normalizedArchivedCwds
                ) {
                    projects.putIfAbsent(normalized, ProjectBucket(project.name))
                }
            }
        sessions.forEach { session ->
            val normalized = normalizeCwd(session.cwd).ifBlank { normalizedDefault }
            if (normalized in normalizedArchivedCwds) return@forEach
            if (normalized == normalizedDefault) {
                defaultSessions += session
            } else {
                projects.getOrPut(normalized) { ProjectBucket(projectName(normalized)) }.sessions += session
            }
        }
        return AgentSessionGrouping(
            defaultCwd = normalizedDefault,
            defaultSessions = defaultSessions,
            projects = projects.map { (cwd, bucket) ->
                AgentSessionProjectGroup(
                    cwd = cwd,
                    name = bucket.name,
                    sessions = bucket.sessions,
                )
            }
        )
    }

    fun drawerRows(
        grouping: AgentSessionGrouping,
        expandedProjectCwds: Set<String>
    ): List<AgentDrawerRow> = buildList {
        add(AgentDrawerRow.SectionHeader(
            title = "会话",
            actionDescription = "在默认目录新建会话",
            action = AgentDrawerAction.NewDraft(grouping.defaultCwd),
            key = "section:sessions"
        ))
        if (grouping.defaultSessions.isEmpty()) {
            add(AgentDrawerRow.Empty("还没有会话", "empty:sessions"))
        } else {
            grouping.defaultSessions.forEach { add(AgentDrawerRow.Session(it, inProject = false)) }
        }
        add(AgentDrawerRow.SectionHeader(
            title = "项目",
            actionDescription = "选择新的项目目录",
            action = AgentDrawerAction.ChooseProject,
            key = "section:projects"
        ))
        if (grouping.projects.isEmpty()) {
            add(AgentDrawerRow.Empty("还没有项目会话", "empty:projects"))
        } else {
            grouping.projects.forEach { project ->
                val expanded = normalizeCwd(project.cwd) in expandedProjectCwds
                add(AgentDrawerRow.ProjectHeader(project, expanded))
                if (expanded) {
                    project.sessions.forEach { add(AgentDrawerRow.Session(it, inProject = true)) }
                }
            }
        }
    }

    fun archivedRows(
        grouping: AgentSessionGrouping,
        expandedCwds: Set<String>,
        archivedProjects: List<AgentProject> = emptyList(),
    ): List<AgentArchivedRow> = buildList {
        val archivedProjectsByCwd = archivedProjects.associateBy { normalizeCwd(it.cwd) }
        val groups = buildList {
            if (grouping.defaultSessions.isNotEmpty()) {
                add(AgentSessionProjectGroup(grouping.defaultCwd, "会话", grouping.defaultSessions))
            }
            addAll(grouping.projects.filter { group ->
                group.sessions.isNotEmpty() || normalizeCwd(group.cwd) in archivedProjectsByCwd
            })
        }
        groups.forEach { group ->
            val normalizedCwd = normalizeCwd(group.cwd)
            val expanded = normalizedCwd in expandedCwds
            add(AgentArchivedRow.GroupHeader(
                cwd = group.cwd,
                title = group.name,
                subtitle = group.sessions.size.takeIf { it > 0 }?.let { "$it 个归档会话" },
                count = group.sessions.size,
                expanded = expanded,
                archivedProject = archivedProjectsByCwd[normalizedCwd],
            ))
            if (expanded) group.sessions.forEach { add(AgentArchivedRow.Session(it)) }
        }
    }

    private fun projectName(cwd: String): String = normalizeCwd(cwd)
        .substringAfterLast('/')
        .takeIf(String::isNotBlank)
        ?: cwd

    private data class ProjectBucket(
        val name: String,
        val sessions: MutableList<AgentSessionSummary> = mutableListOf(),
    )

    fun configurationSummary(options: List<AgentConfigOption>): String {
        val ordered = buildList {
            options.firstOrNull { it.category == AgentConfigCategory.Model }?.let(::add)
            options.firstOrNull { it.category == AgentConfigCategory.ThoughtLevel }?.let(::add)
            if (isEmpty()) {
                options.firstOrNull {
                    it.category != AgentConfigCategory.Mode && it.category != AgentConfigCategory.Permission
                }?.let(::add)
            }
        }.distinctBy(AgentConfigOption::id)
        return ordered.joinToString(" · ") { option ->
            when (option) {
                is AgentConfigOption.Select -> option.choices
                    .firstOrNull { it.value == option.currentValue }
                    ?.name
                    ?: option.currentValue
                is AgentConfigOption.Toggle -> if (option.currentValue) "开启" else "关闭"
            }
        }
    }

    fun composerModelLabel(options: List<AgentConfigOption>): String = configurationSummary(options)
        .replace(" · ", " ")
        .ifBlank { MODEL_ENTRY_LABEL }

    fun composerPermissionLabel(option: AgentConfigOption.Select?): String = when (option) {
        null -> PERMISSION_ENTRY_LABEL
        else -> option.choices
            .firstOrNull { it.value == option.currentValue }
            ?.name
            ?.takeIf(String::isNotBlank)
            ?: "自定义"
    }

    fun permissionOption(options: List<AgentConfigOption>): AgentConfigOption.Select? = options
        .filterIsInstance<AgentConfigOption.Select>()
        .firstOrNull { it.category == AgentConfigCategory.Permission && it.choices.isNotEmpty() }

    fun sessionPanelMaxHeight(
        viewportHeight: Int,
        composerHeight: Int,
        topBarHeight: Int,
        preferredHeight: Int,
        minimumHeight: Int,
        outerSpacing: Int
    ): Int {
        val available = viewportHeight - composerHeight - topBarHeight - outerSpacing
        return minOf(preferredHeight, available.coerceAtLeast(minimumHeight))
    }

    fun filterSessions(
        sessions: List<AgentSessionSummary>,
        query: String
    ): List<AgentSessionSummary> {
        val normalized = query.trim().lowercase()
        if (normalized.isEmpty()) return sessions
        return sessions.filter { session ->
            sequenceOf(session.title, session.id, session.cwd)
                .filterNotNull()
                .any { value -> value.lowercase().contains(normalized) }
        }
    }

    fun slashCommandQuery(text: String): String? {
        if (!text.startsWith('/')) return null
        val query = text.drop(1)
        if (query.any(Char::isWhitespace)) return null
        return query.lowercase()
    }

    fun filterCommands(commands: List<AgentCommand>, query: String): List<AgentCommand> {
        val normalized = query.trim().lowercase()
        if (normalized.isEmpty()) return commands
        return commands.filter { command ->
            command.name.lowercase().contains(normalized) ||
                command.description.lowercase().contains(normalized)
        }
    }

    fun modelChoiceGroups(option: AgentConfigOption.Select): List<AgentModelChoiceGroup> {
        if (option.category != AgentConfigCategory.Model) return emptyList()
        val grouped = linkedMapOf<String, MutableList<com.kite.app.agent.contract.AgentConfigChoice>>()
        var hasExplicitGroup = false
        option.choices.forEach { choice ->
            val groupId = choice.groupId?.takeIf(String::isNotBlank)
            val groupName = choice.groupName?.takeIf(String::isNotBlank)
            if (groupId != null || groupName != null) hasExplicitGroup = true
            val key = groupId ?: groupName ?: UNGROUPED_CONFIGURATION_KEY
            grouped.getOrPut(key, ::mutableListOf) += choice
        }
        if (!hasExplicitGroup) return emptyList()
        return grouped.map { (id, choices) ->
            AgentModelChoiceGroup(
                id = id,
                name = choices.firstOrNull()?.groupName?.takeIf(String::isNotBlank) ?: "其他供应商",
                choices = choices
            )
        }
    }

    private const val UNGROUPED_CONFIGURATION_KEY = "__kite_ungrouped__"
}

internal data class AgentModelChoiceGroup(
    val id: String,
    val name: String,
    val choices: List<com.kite.app.agent.contract.AgentConfigChoice>
)

private data class ProviderListCardBinding(
    val container: View,
    val status: TextView,
    val editButton: View,
    val provider: AgentProviderSummary
)

private class ProviderCredentialFieldBinding(
    val field: EditText,
    val pasteAction: ImageButton,
    private val deleteAction: ImageButton,
    private val credentialPresent: Boolean,
    private val emptyHint: String
) {
    private var removeRequested: Boolean = false

    fun markForRemoval() {
        removeRequested = true
        field.setText("")
        field.hint = AgentProviderCredentialInputPolicy.displayHint(
            credentialPresent = credentialPresent,
            removeRequested = true,
            emptyHint = emptyHint
        )
        deleteAction.visibility = View.GONE
    }

    fun onInputChanged(value: CharSequence?) {
        if (!value.isNullOrBlank()) {
            removeRequested = false
        }
        field.hint = AgentProviderCredentialInputPolicy.displayHint(
            credentialPresent = credentialPresent,
            removeRequested = removeRequested,
            emptyHint = emptyHint
        )
        deleteAction.visibility = if (credentialPresent && !removeRequested) View.VISIBLE else View.GONE
    }

    fun credentialChange(): AgentProviderCredentialChange = AgentProviderCredentialInputPolicy.credentialChange(
        removeRequested = removeRequested,
        value = field.text
    )

    fun setEnabledState(enabled: Boolean) {
        field.isEnabled = enabled
        field.alpha = if (enabled) 1f else 0.45f
        pasteAction.isEnabled = enabled
        pasteAction.alpha = if (enabled) 1f else 0.45f
        deleteAction.isEnabled = enabled
        deleteAction.alpha = if (enabled) 1f else 0.45f
    }
}

internal object AgentPersistentDefaultPolicy {
    fun savedMessage(providerName: String, currentAgent: Boolean): String = if (currentAgent) {
        "$providerName 已设为默认；正在进行的会话不会改变"
    } else {
        "$providerName 已设为默认；下次打开该 Agent 时使用"
    }

    fun configurationSavedMessage(successMessage: String, currentAgent: Boolean): String = if (currentAgent) {
        "$successMessage；正在进行的会话不会改变"
    } else {
        "$successMessage；下次打开该 Agent 时使用"
    }
}

internal object AgentDraftModelPolicy {
    const val CONFIG_ID = "kite.draft.model"

    fun defaultSelection(snapshot: AgentLiveConfigSnapshot): AgentDraftModelSelection? {
        val provider = snapshot.providers.firstOrNull { it.id == snapshot.activeProviderId }
            ?: return null
        val model = provider.models.firstOrNull { candidate ->
            snapshot.defaultModel == candidate.id ||
                snapshot.defaultModel == "${provider.id}/${candidate.id}"
        } ?: provider.models.firstOrNull() ?: return null
        return AgentDraftModelSelection(provider.id, model.id, usesAgentDefault = true)
    }

    fun contains(snapshot: AgentLiveConfigSnapshot, selection: AgentDraftModelSelection): Boolean =
        snapshot.providers.any { provider ->
            provider.id == selection.providerId && provider.models.any { it.id == selection.modelId }
        }

    fun option(
        snapshot: AgentLiveConfigSnapshot,
        selected: AgentDraftModelSelection?
    ): AgentConfigOption.Select? {
        val choices = snapshot.providers.flatMap { provider ->
            provider.models.map { model ->
                AgentConfigChoice(
                    value = choiceValue(provider.id, model.id),
                    name = model.displayName,
                    description = model.id.takeIf { it != model.displayName },
                    groupId = provider.id,
                    groupName = provider.displayName
                )
            }
        }
        if (choices.isEmpty()) return null
        val current = selected?.takeIf { contains(snapshot, it) }
            ?: defaultSelection(snapshot)
            ?: return null
        return AgentConfigOption.Select(
            id = CONFIG_ID,
            name = "模型",
            category = AgentConfigCategory.Model,
            currentValue = choiceValue(current.providerId, current.modelId),
            choices = choices
        )
    }

    fun selection(snapshot: AgentLiveConfigSnapshot, value: String): AgentDraftModelSelection? {
        val default = defaultSelection(snapshot)
        snapshot.providers.forEach { provider ->
            provider.models.forEach { model ->
                if (choiceValue(provider.id, model.id) == value) {
                    return AgentDraftModelSelection(
                        provider.id,
                        model.id,
                        usesAgentDefault = default?.providerId == provider.id && default.modelId == model.id
                    )
                }
            }
        }
        return null
    }

    private fun choiceValue(providerId: String, modelId: String): String =
        "${providerId.length}:$providerId$modelId"
}

internal object AgentProviderEditorPolicy {
    private val SAFE_PROVIDER_ID = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,127}")
    private val SAFE_MODEL_ID = Regex("[^\\s\\p{Cc}]{1,384}")

    fun providerIdFromName(displayName: String): String {
        val ascii = displayName.trim().lowercase()
            .replace(Regex("[^a-z0-9._-]+"), "-")
            .trim('-', '.', '_')
            .take(96)
        if (ascii.isNotBlank() && SAFE_PROVIDER_ID.matches(ascii)) return ascii
        val hash = displayName.trim().hashCode().toUInt().toString(16)
        return "custom-$hash"
    }

    fun validate(
        displayName: String,
        providerId: String,
        baseUrl: String,
        models: List<AgentProviderModelSummary>
    ): String? {
        if (displayName.isBlank()) return "请输入供应商名称"
        if (!SAFE_PROVIDER_ID.matches(providerId)) return "供应商 ID 只能使用字母、数字、点、下划线和短横线"
        val uri = Uri.parse(baseUrl.trim())
        if (uri.scheme !in setOf("http", "https") || uri.host.isNullOrBlank() || uri.userInfo != null) {
            return "请求地址必须是有效的 HTTP 或 HTTPS 地址"
        }
        if (models.isEmpty()) return "请至少添加一个可用模型"
        if (models.any { !SAFE_MODEL_ID.matches(it.id.trim()) }) return "模型 ID 不能为空，也不能包含空格"
        if (models.map { it.id.trim() }.distinct().size != models.size) return "模型 ID 不能重复"
        return null
    }
}

internal object AgentProviderCredentialInputPolicy {
    const val inputType: Int = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
    const val savedMask: String = "••••••••••••"

    fun displayHint(
        credentialPresent: Boolean,
        removeRequested: Boolean,
        emptyHint: String
    ): String = when {
        removeRequested -> "保存后移除 API Key"
        credentialPresent -> savedMask
        else -> emptyHint
    }

    fun credentialChange(
        removeRequested: Boolean,
        value: CharSequence?
    ): AgentProviderCredentialChange {
        if (removeRequested) return AgentProviderCredentialChange.Remove
        val replacement = value?.toString()?.trim().orEmpty()
        return if (replacement.isBlank()) {
            AgentProviderCredentialChange.Keep
        } else {
            AgentProviderCredentialChange.replace(replacement)
        }
    }

    fun clipboardValue(value: CharSequence?): String? = value
        ?.toString()
        ?.trim()
        ?.takeIf(String::isNotBlank)
}

internal object AgentSurfaceThemePolicy {
    fun project(source: ThemeTokens, isDark: Boolean): ThemeTokens = if (isDark) {
        source.copy(
            pageBackground = android.graphics.Color.BLACK,
            surface = android.graphics.Color.rgb(32, 32, 32),
            surfaceElevated = android.graphics.Color.rgb(38, 38, 38),
            cardBackground = android.graphics.Color.rgb(36, 36, 36),
            inputBackground = android.graphics.Color.rgb(32, 32, 32),
            border = android.graphics.Color.rgb(55, 55, 55),
            borderStrong = android.graphics.Color.rgb(74, 74, 74),
            textPrimary = android.graphics.Color.rgb(245, 245, 245),
            textSecondary = android.graphics.Color.rgb(178, 178, 178),
            textTertiary = android.graphics.Color.rgb(122, 122, 122)
        )
    } else {
        source.copy(
            pageBackground = android.graphics.Color.WHITE,
            surface = android.graphics.Color.WHITE,
            surfaceElevated = android.graphics.Color.WHITE,
            cardBackground = android.graphics.Color.rgb(247, 247, 247),
            inputBackground = android.graphics.Color.WHITE,
            border = android.graphics.Color.rgb(232, 232, 232),
            borderStrong = android.graphics.Color.rgb(209, 209, 209),
            textPrimary = android.graphics.Color.rgb(17, 17, 17),
            textSecondary = android.graphics.Color.rgb(102, 102, 102),
            textTertiary = android.graphics.Color.rgb(150, 150, 150)
        )
    }
}

internal object AgentArchivedSelectionPolicy {
    fun toggle(current: Set<String>, sessionId: String): Set<String> = current.toMutableSet().apply {
        if (!add(sessionId)) remove(sessionId)
    }

    fun selectAll(sessions: List<AgentSessionSummary>): Set<String> = sessions
        .mapTo(linkedSetOf(), AgentSessionSummary::id)

    fun canDelete(
        selectedIds: Set<String>,
        currentSessionId: String?,
        deleteSupported: Boolean
    ): Boolean = deleteSupported && selectedIds.isNotEmpty() && currentSessionId !in selectedIds
}

private class ArchivedSessionAdapter(
    private val context: Context,
    private val tokens: ThemeTokens,
    private val onClick: (AgentSessionSummary) -> Unit,
    private val onGroupToggle: (String) -> Unit,
    private val onProjectRestore: (AgentProject) -> Unit,
) : ListAdapter<AgentArchivedRow, RecyclerView.ViewHolder>(DIFF) {
    private val ui = UiKit(context, tokens)
    private var selectionMode = false
    private var selectedIds: Set<String> = emptySet()

    fun setSelectionState(selectionMode: Boolean, selectedIds: Set<String>) {
        val nextIds = selectedIds.toSet()
        if (this.selectionMode == selectionMode && this.selectedIds == nextIds) return
        this.selectionMode = selectionMode
        this.selectedIds = nextIds
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        is AgentArchivedRow.GroupHeader -> TYPE_GROUP
        is AgentArchivedRow.Session -> TYPE_SESSION
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
        }
    }

    inner class GroupHolder(private val container: LinearLayout) : RecyclerView.ViewHolder(container) {
        private val chevron = ImageView(context).apply {
            setImageResource(R.drawable.ic_chevron_right_light)
            imageTintList = ColorStateList.valueOf(tokens.textSecondary)
            setPadding(ui.dp(9), ui.dp(9), ui.dp(9), ui.dp(9))
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
            chevron.rotation = if (row.expanded) 90f else 0f
            chevron.visibility = if (row.count > 0) View.VISIBLE else View.INVISIBLE
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
                if (row.count > 0) onGroupToggle(row.cwd)
            }
        }
    }

    inner class Holder(private val container: LinearLayout) : RecyclerView.ViewHolder(container) {
        private val selector = TextView(context).apply {
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
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
            container.addView(selector, LinearLayout.LayoutParams(ui.dp(30), ui.dp(30)).apply {
                setMargins(ui.dp(7), 0, ui.dp(7), 0)
            })
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
            selector.text = if (selected) "✓" else ""
            selector.setTextColor(android.graphics.Color.WHITE)
            selector.background = ui.roundedBox(
                if (selected) tokens.primaryStrong else android.graphics.Color.TRANSPARENT,
                if (selected) android.graphics.Color.TRANSPARENT else tokens.borderStrong,
                ui.dp(15).toFloat(),
                ui.dp(1)
            )
            selector.contentDescription = if (selected) "已选择" else "未选择"
            title.text = session.title?.takeIf(String::isNotBlank) ?: "未命名会话"
            title.typeface = if (selected) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            subtitle.text = buildString {
                append(session.cwd.ifBlank { session.id })
                session.updatedAt?.takeIf(String::isNotBlank)?.let { append(" · ").append(it) }
            }
            container.background = ui.roundedBox(
                if (selected) tokens.primarySubtle else android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT,
                ui.dp(16).toFloat()
            )
            container.contentDescription = when {
                selectionMode && selected -> "取消选择，${title.text}"
                selectionMode -> "选择，${title.text}"
                else -> "管理归档会话，${title.text}"
            }
            container.setOnClickListener { onClick(session) }
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

private class AgentSessionDrawerAdapter(
    private val context: Context,
    private val tokens: ThemeTokens,
    private val onSessionClick: (AgentSessionSummary) -> Unit,
    private val onProjectToggle: (String) -> Unit,
    private val onProjectMenu: (View, AgentSessionProjectGroup) -> Unit,
    private val onAction: (AgentDrawerAction) -> Unit
) : ListAdapter<AgentDrawerRow, RecyclerView.ViewHolder>(DIFF) {
    private val ui = UiKit(context, tokens)
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

        init {
            container.orientation = LinearLayout.VERTICAL
            container.layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, ui.dp(2), 0, ui.dp(2)) }
            container.addView(title)
            container.addView(subtitle)
        }

        fun bind(row: AgentDrawerRow.Session, selected: Boolean) {
            val session = row.summary
            container.setPadding(
                ui.dp(if (row.inProject) 46 else 14),
                ui.dp(10),
                ui.dp(14),
                ui.dp(10)
            )
            title.text = session.title?.takeIf(String::isNotBlank) ?: "未命名会话"
            title.typeface = if (selected) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            subtitle.text = session.updatedAt?.takeIf(String::isNotBlank) ?: session.id
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

private class AgentSessionAdapter(
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
                session.updatedAt?.takeIf(String::isNotBlank)?.let { append(" · ").append(it) }
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

private class ConversationAdapter(
    private val context: Context,
    private val tokens: ThemeTokens,
    private val scope: CoroutineScope
) : ListAdapter<AgentConversationDisplayItem, ConversationAdapter.DisplayHolder>(DIFF) {
    private val ui = UiKit(context, tokens)
    private val mediaRepository = AgentConversationMediaRepository(context)
    private val projectionCache = linkedMapOf<String, Pair<AgentConversationItem, List<AgentConversationDisplayItem>>>()
    private val processExpansionOverrides = mutableMapOf<String, Boolean>()
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
        val activeProcessIds = projected
            .filterIsInstance<AgentConversationDisplayItem.Process>()
            .mapTo(linkedSetOf()) { it.id }
        processExpansionOverrides.keys.retainAll(activeProcessIds)
        submitList(projected, committed)
    }

    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        is AgentConversationDisplayItem.UserMessage -> TYPE_USER
        is AgentConversationDisplayItem.AssistantText -> TYPE_ASSISTANT
        is AgentConversationDisplayItem.Code -> TYPE_CODE
        is AgentConversationDisplayItem.Rule -> TYPE_RULE
        is AgentConversationDisplayItem.Table -> TYPE_TABLE
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
        private val text = messageTextView().apply {
            textSize = 15f
            setLineSpacing(0f, 1.3f)
            letterSpacing = CONVERSATION_LETTER_SPACING
            maxWidth = context.resources.displayMetrics.widthPixels - ui.dp(72)
            setPadding(ui.dp(15), ui.dp(10), ui.dp(15), ui.dp(10))
            background = ui.roundedBox(tokens.surfaceElevated, tokens.border, ui.dp(20).toFloat(), ui.dp(1))
        }.also { frame.addView(it) }

        override fun bind(item: AgentConversationDisplayItem) {
            item as AgentConversationDisplayItem.UserMessage
            text.text = item.text
            text.layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.END
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
            text.movementMethod = if (item.inline.any { it.style == AgentInlineTextSegment.Style.Link }) {
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
                    text.setPadding(ui.dp(2), 0, 0, 0)
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
        private val copy = TextView(context).apply {
            text = "复制"
            textSize = 12f
            setTextColor(tokens.primaryStrong)
            gravity = Gravity.CENTER
            setPadding(ui.dp(10), ui.dp(5), ui.dp(10), ui.dp(5))
        }.also(header::addView)
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
            copy.contentDescription = "复制代码"
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
        private val copy = TextView(context).apply {
            text = "复制"
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(tokens.primaryStrong)
            setPadding(ui.dp(10), ui.dp(5), ui.dp(10), ui.dp(5))
        }.also(header::addView)
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
            copy.contentDescription = "复制表格"
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
            layoutParams = rowParams(top = 9, bottom = 9)
        }
    ) {
        private val container = itemView as LinearLayout
        private val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isClickable = true
            isFocusable = true
            setPadding(0, ui.dp(5), 0, ui.dp(5))
        }.also(container::addView)
        private val title = TextView(context).apply {
            textSize = 13.5f
            typeface = Typeface.DEFAULT_BOLD
            includeFontPadding = false
            setTextColor(tokens.textPrimary)
        }.also { header.addView(it, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)) }
        private val chevron = ImageView(context).apply {
            setImageResource(R.drawable.ic_chevron_right_light)
            setColorFilter(tokens.textSecondary)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            contentDescription = null
        }.also { header.addView(it, LinearLayout.LayoutParams(ui.dp(20), ui.dp(20))) }
        private val entries = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, ui.dp(4), 0, 0)
        }.also(container::addView)
        private var ticker: Runnable? = null
        private var boundItem: AgentConversationDisplayItem.Process? = null

        override fun bind(item: AgentConversationDisplayItem) {
            item as AgentConversationDisplayItem.Process
            stopTicker()
            boundItem = item
            val expanded = processExpansionOverrides[item.id]
                ?: (item.state == AgentConversationTurnState.Running)
            updateTitle(item)
            chevron.rotation = if (expanded) 90f else 0f
            entries.visibility = if (expanded) View.VISIBLE else View.GONE
            if (expanded) rebuildEntries(item) else entries.removeAllViews()
            header.contentDescription = if (expanded) "收起处理过程" else "展开处理过程"
            header.setOnClickListener {
                processExpansionOverrides[item.id] = !expanded
                bindingAdapterPosition.takeIf { it != RecyclerView.NO_POSITION }?.let(::notifyItemChanged)
            }
            if (item.state == AgentConversationTurnState.Running && item.startedAtMillis != null) startTicker(item)
        }

        private fun updateTitle(item: AgentConversationDisplayItem.Process) {
            title.text = when (item.state) {
                AgentConversationTurnState.Running -> {
                    val elapsed = item.startedAtMillis
                        ?.let { (System.currentTimeMillis() - it).coerceAtLeast(0L) }
                        ?: 0L
                    "思考中 ${formatProcessDuration(elapsed)}"
                }
                AgentConversationTurnState.Completed -> item.durationMillis
                    ?.let { "思考了 ${formatProcessDuration(it)}" }
                    ?: "已处理"
                AgentConversationTurnState.Failed -> "处理失败"
                AgentConversationTurnState.Cancelled -> "已取消"
                AgentConversationTurnState.Historical -> "已处理"
            }
            title.setTextColor(
                if (item.state == AgentConversationTurnState.Failed) tokens.danger else tokens.textPrimary
            )
        }

        private fun startTicker(item: AgentConversationDisplayItem.Process) {
            val runnable = object : Runnable {
                override fun run() {
                    if (boundItem?.id != item.id || !itemView.isAttachedToWindow) return
                    updateTitle(item)
                    itemView.postDelayed(this, PROCESS_TICK_MS)
                }
            }
            ticker = runnable
            itemView.postDelayed(runnable, PROCESS_TICK_MS)
        }

        private fun stopTicker() {
            ticker?.let(itemView::removeCallbacks)
            ticker = null
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

        private fun thoughtRow(item: AgentConversationDisplayItem.Thought): View = TextView(context).apply {
            text = item.text
            textSize = 14.5f
            includeFontPadding = false
            setLineSpacing(0f, 1.28f)
            letterSpacing = CONVERSATION_LETTER_SPACING
            setTextColor(tokens.textPrimary)
            setPadding(0, ui.dp(5), 0, ui.dp(7))
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
            stopTicker()
            boundItem = null
            entries.removeAllViews()
        }
    }

    inner class ThoughtHolder : DisplayHolder(
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = rowParams(top = 5, bottom = 7)
            setPadding(ui.dp(13), ui.dp(10), ui.dp(13), ui.dp(11))
            background = ui.roundedBox(tokens.primarySubtle, tokens.border, ui.dp(14).toFloat(), ui.dp(1))
        }
    ) {
        private val container = itemView as LinearLayout
        private val label = sectionLabel().also(container::addView)
        private val text = messageTextView().apply {
            textSize = 14.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
            setTextColor(tokens.textSecondary)
        }.also(container::addView)

        override fun bind(item: AgentConversationDisplayItem) {
            item as AgentConversationDisplayItem.Thought
            val expanded = item.id in expandedThoughtIds
            label.text = if (expanded) "思考过程 · 点击收起" else "思考过程 · 点击展开"
            text.text = item.text
            text.maxLines = if (expanded) Int.MAX_VALUE else COLLAPSED_THOUGHT_LINES
            text.ellipsize = if (expanded) null else TextUtils.TruncateAt.END
            container.setOnClickListener {
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
            when (segment.style) {
                AgentInlineTextSegment.Style.Plain -> Unit
                AgentInlineTextSegment.Style.Strong -> result.setSpan(
                    StyleSpan(Typeface.BOLD),
                    start,
                    end,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                AgentInlineTextSegment.Style.Code -> {
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
                AgentInlineTextSegment.Style.Link -> segment.link?.let { url ->
                    result.setSpan(
                        URLSpan(url),
                        start,
                        end,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                    )
                }
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

    private fun formatProcessDuration(durationMillis: Long): String {
        val totalSeconds = (durationMillis / 1_000L).coerceAtLeast(0L)
        val minutes = totalSeconds / 60L
        val seconds = totalSeconds % 60L
        return if (minutes > 0L) "${minutes}m ${seconds}s" else "${seconds}s"
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
        const val PLAN_ROWS = 6
        const val COLLAPSED_THOUGHT_LINES = 4
        const val COLLAPSED_TOOL_LINES = 4
        const val PROCESS_TICK_MS = 1_000L
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
