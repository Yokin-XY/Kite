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
import android.text.method.PasswordTransformationMethod
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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.kite.app.R
import com.kite.app.agent.contract.AgentContent
import com.kite.app.agent.contract.AgentConfigCategory
import com.kite.app.agent.contract.AgentConfigChoice
import com.kite.app.agent.contract.AgentConfigOption
import com.kite.app.agent.contract.AgentConfigValue
import com.kite.app.agent.contract.AgentModelSource
import com.kite.app.agent.contract.AgentOperationResult
import com.kite.app.agent.contract.AgentPermissionOutcome
import com.kite.app.agent.contract.AgentSessionPhase
import com.kite.app.agent.contract.AgentSessionRenameRequest
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
import com.kite.app.agent.config.AgentSkillDocumentReadResult
import com.kite.app.agent.config.AgentSkillDocumentSnapshot
import com.kite.app.agent.config.AgentSkillDocumentWriteRequest
import com.kite.app.agent.config.AgentSkillDocumentWriteResult
import com.kite.app.agent.config.AgentSkillImportStager
import com.kite.app.agent.config.AgentSkillOperation
import com.kite.app.agent.config.AgentSkillSummary
import com.kite.app.agent.registration.AgentConfigurationStatus
import com.kite.app.agent.registration.AgentInstallationStatus
import com.kite.app.agent.registration.AgentLaunchStatus
import com.kite.app.agent.registration.AgentOfficialAccountSpec
import com.kite.app.agent.registration.AgentRegistryEntry
import com.kite.app.agent.registration.AgentRegistrySnapshot
import com.kite.app.agent.registration.AgentRuntimeStatus
import com.kite.app.agent.registration.KiteAgentRegistry
import com.kite.app.agent.auth.AgentOfficialAccountManager
import com.kite.app.agent.auth.AgentOfficialAccountStatus
import com.kite.app.agent.runtime.AgentDraftCapabilityCatalog
import com.kite.app.agent.runtime.AgentDraftModelSelection
import com.kite.app.agent.sdk.configuration.AgentConfigurationApi
import com.kite.app.agent.sdk.configuration.AgentConfigurationIntent
import com.kite.app.agent.sdk.configuration.AgentConfigurationTarget
import com.kite.app.agent.sdk.configuration.AgentProviderCatalogApi
import com.kite.app.agent.sdk.configuration.toConfigurationProjection
import com.kite.app.agent.sdk.configuration.AgentSessionControlApi
import com.kite.app.agent.sdk.configuration.RuntimeBackedAgentSessionControlApi
import com.kite.app.agent.sdk.configuration.configurationTarget
import com.kite.app.agent.sdk.skill.AgentPromptDraft
import com.kite.app.agent.sdk.skill.AgentSelectedSkill
import com.kite.app.agent.sdk.skill.AgentSkillCatalogCache
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
import com.kite.app.agent.store.AgentArchivedSessionMetadata
import com.kite.app.agent.store.AgentArchivedSessionSourceState
import com.kite.app.agent.store.AgentModelDisplayName
import com.kite.app.agent.store.AgentModelLibrarySnapshot
import com.kite.app.agent.store.AgentModelLibraryStore
import com.kite.app.agent.store.AgentProviderCatalogSnapshot
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
    private val onPickCodexAuthJson: () -> Unit,
    private val agentRegistry: KiteAgentRegistry,
    private val officialAccountManager: AgentOfficialAccountManager,
    private val agentConfigurationApi: AgentConfigurationApi,
    private val agentProviderCatalogApi: AgentProviderCatalogApi,
    private val agentSessionControlApi: AgentSessionControlApi = RuntimeBackedAgentSessionControlApi(),
    private val onManageCodexAccounts: () -> Unit = {},
) : RunSurfaceBinding {
    private val isDark = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
        Configuration.UI_MODE_NIGHT_YES
    private val tokens = AgentSurfaceThemePolicy.project(tokens, isDark)
    private val ui = UiKit(context, this.tokens)
    private val selectionPalette = AgentSelectionVisualPolicy.palette(isDark)
    private val skillChipPalette = AgentSkillChipVisualPolicy.palette(isDark)
    private val sessionMetadataStore = AgentSessionMetadataStore(context)
    private val draftCapabilityCacheStore = AgentDraftCapabilityCacheStore(context)
    private val modelLibraryStore = AgentModelLibraryStore(context)
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
    private val skillHost = LinearLayout(context)
    private val skillScrollHost = HorizontalScrollView(context)
    private val attachmentHost = LinearLayout(context)
    private val composer = LinearLayout(context)
    private val sessionConfigurationOverlay = FrameLayout(context).apply {
        visibility = View.GONE
        isClickable = true
    }
    private val composerExtensionOverlay = FrameLayout(context).apply {
        visibility = View.GONE
        isClickable = true
    }
    private val input = EditText(context)
    private val actionButton = ImageButton(context)
    private val fixedSessionControls = AgentFixedSessionControlStrip(
        context = context,
        tokens = this.tokens,
        onModelClick = ::showSessionConfigurationPanel,
        onPermissionClick = { showComposerExtensionMenu(ComposerExtensionRoute.Permissions) },
    )
    private val adapter = ConversationAdapter(context, tokens, lifecycleOwner.lifecycleScope)
    private val navigationHost = FrameLayout(context)
    private val drawerList = RecyclerView(context)
    private val drawerStatusText = TextView(context)
    private val drawerAdapter = AgentSessionDrawerAdapter(
        context = context,
        tokens = this.tokens,
        onSessionClick = ::loadDrawerSession,
        onSessionMenu = ::showSessionMenu,
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
    private var officialAccountObservation: Job? = null
    private var officialAccountObservedAgentId: String? = null
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
    private var archivedSelectionBackHandler: (() -> Boolean)? = null
    private var settingsReturnsToDrawer = false
    private var selectedSettingsAgentId: String? = null
    private var persistentConfigAgentId: String? = null
    private var persistentConfigResult: AgentConfigReadResult? = null
    private var defaultPermissionPendingProfileId: String? = null
    private var settingsRegistrySnapshot: AgentRegistrySnapshot? = null
    private var settingsLoadRevision: Long = 0L
    private var providerPageAgentId: String? = null
    private var providerPageTarget: AgentConfigurationTarget? = null
    private var providerPageSnapshot: AgentLiveConfigSnapshot? = null
    private var providerCatalogLoadRevision: Long = 0L
    private var providerCatalogRefreshJob: Job? = null
    private var skillPageAgentId: String? = null
    private var skillPageTarget: AgentConfigurationTarget? = null
    private var skillPageSnapshot: AgentLiveConfigSnapshot? = null
    private var skillPageListAdapter: AgentSkillListAdapter? = null
    private var skillPageListView: RecyclerView? = null
    private var skillPageStatusText: TextView? = null
    private var skillPageRefreshView: SwipeRefreshLayout? = null
    private var skillDocumentEditorSnapshot: AgentSkillDocumentSnapshot? = null
    private var skillDocumentEditorInput: EditText? = null
    private var skillDocumentLoadRevision: Long = 0L
    private var mcpPageAgentId: String? = null
    private var mcpPageTarget: AgentConfigurationTarget? = null
    private var mcpPageSnapshot: AgentLiveConfigSnapshot? = null
    private var mcpPageListAdapter: AgentMcpListAdapter? = null
    private var mcpPageListView: RecyclerView? = null
    private var mcpPageStatusText: TextView? = null
    private val mcpConnectionStates = linkedMapOf<String, AgentMcpConnectionState>()
    private val mcpConnectionMessages = linkedMapOf<String, String>()
    private var mcpEditorStatusText: TextView? = null
    private var mcpEditorSaveAction: TextView? = null
    private var coreDocumentPageAgentId: String? = null
    private var coreDocumentPageTarget: AgentConfigurationTarget? = null
    private var coreDocumentWorkspacePath: String? = null
    private var coreDocumentDescriptors: List<AgentCoreDocumentDescriptor> = emptyList()
    private var coreDocumentListHost: LinearLayout? = null
    private var coreDocumentLoadRevision: Long = 0L
    private var coreDocumentEditorSnapshot: AgentCoreDocumentSnapshot? = null
    private var coreDocumentEditorInput: EditText? = null
    private var draftModelAgentId: String? = null
    private var draftModelSnapshot: AgentLiveConfigSnapshot? = null
    private var draftProviderCatalogTarget: AgentConfigurationTarget? = null
    private var draftProviderCatalogSnapshot: AgentProviderCatalogSnapshot? = null
    private var draftModelOfficialAccounts: List<AgentOfficialAccountSpec> = emptyList()
    private var draftModelLoadRevision: Long = 0L
    private var draftModelLoadJob: Job? = null
    private var providerEditorStatusText: TextView? = null
    private var providerEditorSaveAction: TextView? = null
    private var providerLibraryGroupId: String = AgentModelLibraryStore.ALL_GROUP_ID
    private var providerLibraryMode: AgentProviderLibraryMode = AgentProviderLibraryMode.Browse
    private val expandedProviderIds = linkedSetOf<String>()
    private val selectedProviderIds = linkedSetOf<String>()
    private val selectedSkillIds = linkedSetOf<String>()
    private val sessionConfigurationPanel by lazy(LazyThreadSafetyMode.NONE) {
        AgentSessionConfigurationPanel(
            context = context,
            tokens = tokens,
            overlay = sessionConfigurationOverlay,
            optionsProvider = ::sessionConfigurationOptions,
            pendingProvider = { false },
            viewportProvider = {
                AgentSessionConfigurationViewport(
                    availableWidth = root.width.takeIf { it > 0 }
                        ?: context.resources.displayMetrics.widthPixels,
                    viewportHeight = root.height.takeIf { it > 0 }
                        ?: context.resources.displayMetrics.heightPixels,
                    composerHeight = composerArea.height,
                    topBarHeight = topBar.height,
                )
            },
            onUpdateConfiguration = ::updateConfiguration,
        )
    }
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
    private var composerPresentation: ComposerPresentation? = null
    private val pendingAttachments = mutableListOf<PendingAttachment>()
    private val selectedSkills = mutableListOf<AgentSelectedSkill>()
    private var restoredComposerDraftIdentity: ComposerDraftIdentity? = null
    private var restoringComposerDraft = false
    private var suspendComposerDraftPersistence = false
    private var composerSkillLoadJob: Job? = null
    private var composerSkillLoadRevision: Long = 0L
    private var composerSkillLoading: Boolean = false
    private var composerSkillError: String? = null

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
        ).apply { setMargins(ui.dp(14), ui.dp(8), ui.dp(14), ui.dp(12)) })
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
        addView(sessionConfigurationOverlay, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))
    }

    override fun render(state: RunSurfaceUiState) {
        val content = state.content as? RunSurfaceContent.Agent ?: return
        instanceId = state.target.instanceId
        generation = state.createdAt
        if (agentId != content.agentId) {
            draftModelOfficialAccounts = emptyList()
            draftModelSnapshot = null
            draftProviderCatalogSnapshot = null
            draftModelAgentId = null
            selectedSkills.clear()
            renderSelectedSkills()
        }
        agentId = content.agentId
        agentDisplayName = state.title.ifBlank { content.agentId ?: "Agent" }
        agentTitleText.setTextIfChanged(agentDisplayName)
        providerId = content.providerId
        sessionId = content.sessionId
        restoreComposerDraft(
            ComposerDraftIdentity(instanceId, generation, content.sessionId),
        )
        statusText.setTextIfChanged(content.statusMessage
            ?: content.connectionStatus?.let(::connectionStatusLabel)
            ?: state.statusLabel)
        loadDraftModelCatalog()
        if (prepareInitialEntryDraftIfNeeded()) return
        subscribe(content.providerId, observableSessionId(content.sessionId))
        updateComposer()
    }

    override fun setSurfaceToolbarVisible(visible: Boolean): Boolean {
        toolbarVisible = visible
        topBar.visibility = if (visible) View.VISIBLE else View.GONE
        return true
    }

    override fun toggleSurfaceToolbar(): Boolean = setSurfaceToolbarVisible(!toolbarVisible)

    override fun dispose() {
        closeComposerExtensionMenu(animate = false)
        closeSessionConfigurationPanel(animate = false)
        observation?.cancel()
        observation = null
        officialAccountObservation?.cancel()
        officialAccountObservation = null
        officialAccountObservedAgentId = null
        navigationJob?.cancel()
        navigationJob = null
        providerCatalogRefreshJob?.cancel()
        providerCatalogRefreshJob = null
        draftModelLoadJob?.cancel()
        draftModelLoadJob = null
        composerSkillLoadJob?.cancel()
        composerSkillLoadJob = null
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
        AgentNavigationScreen.SkillDocumentEditor -> {
            returnFromSkillDocumentEditor()
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
            if (archivedSelectionBackHandler?.invoke() != true) returnToAgentSettings()
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

    internal fun sessionControlIdentityForTesting(): Pair<Int, Int> =
        fixedSessionControls.identities()

    internal fun sessionControlChildCountsForTesting(): Pair<Int, Int> =
        fixedSessionControls.childCounts()

    internal fun refreshSessionControlsForTesting() = renderSessionConfigurationControls()

    internal fun composerInputFlagsForTesting(): Pair<Int, Int> = input.inputType to input.imeOptions

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

    private fun renderSelectedSkills() {
        skillHost.removeAllViews()
        skillScrollHost.visibility = if (selectedSkills.isEmpty()) View.GONE else View.VISIBLE
        selectedSkills.forEach { skill ->
            skillHost.addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = ui.roundedBox(
                    skillChipPalette.fill,
                    skillChipPalette.border,
                    ui.dp(AgentSkillChipVisualPolicy.HEIGHT_DP / 2).toFloat(),
                    ui.dp(1),
                )
                addView(TextView(context).apply {
                    text = skill.displayName
                    textSize = 12.5f
                    typeface = Typeface.DEFAULT_BOLD
                    includeFontPadding = false
                    gravity = Gravity.CENTER
                    maxLines = 1
                    maxWidth = ui.dp(
                        AgentSkillChipVisualPolicy.MAX_WIDTH_DP -
                            AgentSkillChipVisualPolicy.REMOVE_ACTION_SIZE_DP
                    )
                    ellipsize = TextUtils.TruncateAt.END
                    setTextColor(skillChipPalette.text)
                    setPadding(ui.dp(10), 0, ui.dp(4), 0)
                }, LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ui.dp(AgentSkillChipVisualPolicy.HEIGHT_DP),
                ))
                addView(iconButton(context, R.drawable.ic_close_light, "移除 ${skill.displayName}") {
                    selectedSkills.removeAll { it.id == skill.id }
                    renderSelectedSkills()
                    updateComposer()
                    if (composerExtensionRoute == ComposerExtensionRoute.Skills) {
                        rebuildComposerExtensionMenu(animateContent = false)
                    }
                }.apply {
                    imageTintList = ColorStateList.valueOf(skillChipPalette.text)
                    setPadding(ui.dp(8), ui.dp(8), ui.dp(8), ui.dp(8))
                }, LinearLayout.LayoutParams(
                    ui.dp(AgentSkillChipVisualPolicy.REMOVE_ACTION_SIZE_DP),
                    ui.dp(AgentSkillChipVisualPolicy.REMOVE_ACTION_SIZE_DP),
                ))
            }, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ui.dp(AgentSkillChipVisualPolicy.HEIGHT_DP),
            ).apply {
                marginEnd = ui.dp(6)
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
        addView(skillScrollHost.apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            visibility = View.GONE
            addView(skillHost.apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }, ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ))
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            setMargins(ui.dp(8), 0, ui.dp(8), ui.dp(6))
        })
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
        background = ColorDrawable(android.graphics.Color.TRANSPARENT)
        addView(fixedSessionControls.view, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ui.dp(34),
        ).apply {
            setMargins(ui.dp(4), 0, ui.dp(4), ui.dp(6))
        })
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = ui.dp(52)
            addView(iconButtonWithAnchor(context, R.drawable.ic_add_light, "扩展与工作模式") {
                showComposerExtensionMenu()
            }.apply {
                background = ui.roundedBox(
                    agentInputBackground,
                    android.graphics.Color.TRANSPARENT,
                    ui.dp(25).toFloat(),
                )
                elevation = ui.dp(2).toFloat()
            }, LinearLayout.LayoutParams(ui.dp(50), ui.dp(50)).apply {
                marginEnd = ui.dp(8)
            })
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = ui.dp(52)
                setPadding(ui.dp(14), ui.dp(3), ui.dp(2), ui.dp(3))
                background = ui.roundedBox(
                    agentInputBackground,
                    android.graphics.Color.TRANSPARENT,
                    ui.dp(26).toFloat(),
                )
                elevation = ui.dp(2).toFloat()
                addView(input.apply {
                    hint = "给 Agent 发消息"
                    textSize = 16f
                    includeFontPadding = false
                    minHeight = ui.dp(44)
                    maxLines = 6
                    inputType = InputType.TYPE_CLASS_TEXT or
                        InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                        InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                    imeOptions = EditorInfo.IME_FLAG_NO_ENTER_ACTION
                    setTextColor(tokens.textPrimary)
                    setHintTextColor(tokens.textTertiary)
                    setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    setPadding(0, ui.dp(6), ui.dp(7), ui.dp(6))
                    addTextChangedListener(object : TextWatcher {
                        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                            updateComposer()
                        }
                        override fun afterTextChanged(s: Editable?) = Unit
                    })
                }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                addView(actionButton.apply {
                    contentDescription = "发送"
                    scaleType = ImageView.ScaleType.CENTER_INSIDE
                    setPadding(ui.dp(14), ui.dp(14), ui.dp(14), ui.dp(14))
                    visibility = View.VISIBLE
                    setOnClickListener { submitOrCancel() }
                }, LinearLayout.LayoutParams(ui.dp(48), ui.dp(48)))
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
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
        historyStatusText.visibility = View.GONE
        renderPermission(null)
        renderSessionConfigurationControls()
        if (key == null) {
            adapter.submitList(emptyList())
            return
        }
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
        val options = sessionConfigurationOptions()
        val permission = composerPermissionOption()
        val catalog = agentSessionControlApi.project(options + listOfNotNull(permission)).catalog
        val runtimeReady = AgentRuntimeRegistry.session(instanceId)?.generation == generation
        fixedSessionControls.render(catalog, pending = !runtimeReady || draftPreparationPending)
        if (sessionConfigurationOverlay.visibility == View.VISIBLE) {
            rebuildSessionConfigurationPanel(animateContent = false)
        }
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
        val text = input.text?.toString()?.trim().orEmpty()
        val hasDraft = text.isNotBlank() || pendingAttachments.isNotEmpty() || selectedSkills.isNotEmpty()
        if (phase == AgentSessionPhase.Prompting && !hasDraft) {
            lifecycleOwner.lifecycleScope.launch {
                showOperationResult(AgentRuntimeRegistry.cancel(instanceId, generation), "已请求停止生成")
            }
            return
        }
        if (!hasDraft || phase !in setOf(AgentSessionPhase.Ready, AgentSessionPhase.Prompting)) return
        val attachments = pendingAttachments.toList()
        val skills = selectedSkills.toList()
        val content = buildList {
            if (text.isNotBlank()) add(AgentContent.Text(text))
            addAll(attachments.map(PendingAttachment::content))
        }
        suspendComposerDraftPersistence = true
        input.setText("")
        pendingAttachments.clear()
        selectedSkills.clear()
        renderAttachments()
        renderSelectedSkills()
        updateComposer()
        suspendComposerDraftPersistence = false
        lifecycleOwner.lifecycleScope.launch {
            val draft = AgentPromptDraft(content = content, skills = skills)
            val result = if (phase == AgentSessionPhase.Prompting) {
                AgentRuntimeRegistry.steer(instanceId, generation, draft)
            } else {
                AgentRuntimeRegistry.prompt(instanceId, generation, draft)
            }
            if (result !is AgentOperationResult.Success) {
                restoreComposerAfterFailure(text, attachments, skills)
            }
            showOperationResult(result, null)
        }
    }

    private fun updateComposer() {
        persistComposerDraft()
        val phase = composerPhase()
        val hasDraft = !input.text.isNullOrBlank() || pendingAttachments.isNotEmpty() || selectedSkills.isNotEmpty()
        val showStop = (phase == AgentSessionPhase.Prompting && !hasDraft) ||
            phase == AgentSessionPhase.Cancelling
        val canSubmit = hasDraft && phase in setOf(AgentSessionPhase.Ready, AgentSessionPhase.Prompting)
        val nextPresentation = ComposerPresentation(phase, showStop, canSubmit)
        if (composerPresentation == nextPresentation) return
        composerPresentation = nextPresentation
        input.isEnabled = phase == AgentSessionPhase.Ready || phase == AgentSessionPhase.Prompting
        actionButton.visibility = View.VISIBLE
        actionButton.isEnabled = canSubmit || (phase == AgentSessionPhase.Prompting && !hasDraft)
        actionButton.setImageResource(when {
            showStop -> R.drawable.ic_terminal_interrupt
            else -> R.drawable.ic_arrow_up_light
        })
        actionButton.imageTintList = ColorStateList.valueOf(agentPageBackground)
        actionButton.background = InsetDrawable(
            ui.roundedBox(
                tokens.textPrimary,
                android.graphics.Color.TRANSPARENT,
                ui.dp(20).toFloat(),
            ),
            ui.dp(4),
        )
        actionButton.contentDescription = when {
            showStop -> "停止生成"
            phase == AgentSessionPhase.Prompting -> "插入当前回复"
            else -> "发送"
        }
    }

    private fun createNewSession(cwd: String? = null) {
        if (instanceId.isBlank() || generation <= 0L) return
        if (draftPreparationPending) return
        val previousSessionId = sessionId
        val previousText = input.text?.toString().orEmpty()
        val previousAttachments = pendingAttachments.toList()
        val previousSkills = selectedSkills.toList()
        draftPreparationPending = true
        enterDraftUi()
        statusText.text = "正在准备新会话…"
        updateComposer()
        lifecycleOwner.lifecycleScope.launch {
            when (val result = AgentRuntimeRegistry.prepareNewSession(instanceId, generation, cwd)) {
                is AgentOperationResult.Success -> {
                    draftPreparationPending = false
                    statusText.text = "可以开始新会话"
                    subscribe(providerId, result.value.sessionId)
                    loadDraftModelCatalog(force = true)
                    input.requestFocus()
                    updateComposer()
                }
                is AgentOperationResult.Unsupported -> {
                    draftPreparationPending = false
                    restoreSessionUi(previousSessionId, previousText, previousAttachments, previousSkills)
                    Toast.makeText(root.context, "当前 Agent 不支持新建会话", Toast.LENGTH_SHORT).show()
                }
                is AgentOperationResult.Failure -> {
                    draftPreparationPending = false
                    restoreSessionUi(previousSessionId, previousText, previousAttachments, previousSkills)
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
                    subscribe(providerId, result.value.sessionId)
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
        restoredComposerDraftIdentity = ComposerDraftIdentity(instanceId, generation, null)
        subscribe(providerId, observableSessionId(null))
        input.setText("")
        pendingAttachments.clear()
        selectedSkills.clear()
        renderAttachments()
        renderSelectedSkills()
        statusText.text = "可以开始新会话"
        updateComposer()
    }

    private fun persistComposerDraft() {
        if (restoringComposerDraft || suspendComposerDraftPersistence) return
        if (instanceId.isBlank() || generation <= 0L) return
        val text = input.text?.toString().orEmpty()
        val content = buildList {
            if (text.isNotEmpty()) add(AgentContent.Text(text))
            addAll(pendingAttachments.map(PendingAttachment::content))
        }
        AgentRuntimeRegistry.updateComposerDraft(
            instanceId = instanceId,
            generation = generation,
            sessionId = sessionId,
            draft = AgentPromptDraft(content = content, skills = selectedSkills.toList()),
        )
    }

    private fun restoreComposerDraft(identity: ComposerDraftIdentity) {
        if (identity == restoredComposerDraftIdentity) return
        restoredComposerDraftIdentity = identity
        val draft = AgentRuntimeRegistry.composerDraft(
            identity.instanceId,
            identity.generation,
            identity.sessionId,
        )
        restoringComposerDraft = true
        try {
            val text = draft?.content
                ?.filterIsInstance<AgentContent.Text>()
                ?.joinToString("\n\n", transform = AgentContent.Text::text)
                .orEmpty()
            input.setText(text)
            input.setSelection(input.text?.length ?: 0)
            pendingAttachments.clear()
            pendingAttachments += draft?.content.orEmpty()
                .filterNot { it is AgentContent.Text || it is AgentContent.SkillReference }
                .map { content -> PendingAttachment(content.attachmentDisplayName(), content) }
            selectedSkills.clear()
            selectedSkills += draft?.skills.orEmpty()
            renderAttachments()
            renderSelectedSkills()
        } finally {
            restoringComposerDraft = false
        }
    }

    private fun AgentContent.attachmentDisplayName(): String = when (this) {
        is AgentContent.Image -> uri?.let(Uri::parse)?.lastPathSegment ?: "图片"
        is AgentContent.Audio -> "音频"
        is AgentContent.ResourceLink -> name
        is AgentContent.EmbeddedText -> Uri.parse(uri).lastPathSegment ?: "文本文件"
        is AgentContent.EmbeddedBlob -> Uri.parse(uri).lastPathSegment ?: "文件"
        is AgentContent.Text -> "文本"
        is AgentContent.SkillReference -> displayName
    }

    private fun observableSessionId(visibleSessionId: String?): String? {
        if (!visibleSessionId.isNullOrBlank()) return visibleSessionId
        return AgentRuntimeRegistry.conversationProjectionSessionId(instanceId, generation)
    }

    private fun loadDraftModelCatalog(force: Boolean = false) {
        val targetAgentId = agentId?.takeIf(String::isNotBlank) ?: return
        if (
            !force &&
            draftModelAgentId == targetAgentId &&
            (draftModelSnapshot != null || draftModelLoadJob?.isActive == true)
        ) {
            draftModelSnapshot?.let { applyDraftModelDefault(targetAgentId, it) }
            renderSessionConfigurationControls()
            return
        }
        val requestRevision = ++draftModelLoadRevision
        draftModelAgentId = targetAgentId
        draftModelLoadJob?.cancel()
        draftModelLoadJob = lifecycleOwner.lifecycleScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                val entry = agentRegistry.snapshot().entry(targetAgentId)
                val target = entry?.configurationTarget()
                val catalog = target?.let(agentProviderCatalogApi::snapshot)
                Triple(
                    entry?.registration?.officialAccounts.orEmpty(),
                    target?.let { it to catalog },
                    if (target == null || catalog == null) null else catalog.toConfigurationProjection(target),
                )
            }
            if (
                requestRevision != draftModelLoadRevision ||
                agentId != targetAgentId
            ) return@launch
            draftModelOfficialAccounts = loaded.first
            draftProviderCatalogTarget = loaded.second?.first
            draftProviderCatalogSnapshot = loaded.second?.second
            val snapshot = loaded.third
            draftModelSnapshot = snapshot
            if (snapshot != null) {
                applyDraftModelDefault(targetAgentId, snapshot)
            }
            renderSessionConfigurationControls()
        }
    }

    private fun applyDraftModelDefault(targetAgentId: String, snapshot: AgentLiveConfigSnapshot) {
        val current = AgentRuntimeRegistry.draftModelSelection(instanceId, generation)
        val available = AgentDraftModelPolicy.option(
            snapshot,
            current,
            modelLibraryStore.snapshot(targetAgentId),
            draftModelOfficialAccounts,
        )
        val next = current?.takeIf { AgentDraftModelPolicy.contains(available, it) }
            ?: AgentDraftModelPolicy.defaultSelection(snapshot)
        if (next != null && next != current) {
            AgentRuntimeRegistry.selectDraftModel(instanceId, generation, next)
        }
    }

    private fun usePersistentSnapshotAsDraftDefault(targetAgentId: String, snapshot: AgentLiveConfigSnapshot) {
        if (targetAgentId != agentId) return
        val runtime = AgentRuntimeRegistry.session(instanceId)
            ?.takeIf { it.generation == generation }
            ?: return
        draftModelAgentId = targetAgentId
        draftModelSnapshot = snapshot
        val target = settingsRegistrySnapshot?.entry(targetAgentId)?.configurationTarget()
        if (target != null) {
            draftProviderCatalogTarget = target
            draftProviderCatalogSnapshot = agentProviderCatalogApi.snapshot(target)
        }
        AgentDraftModelPolicy.defaultSelection(snapshot)?.let {
            AgentRuntimeRegistry.selectDraftModel(runtime.instanceId, runtime.generation, it)
        }
        renderSessionConfigurationControls()
    }

    private fun restoreSessionUi(
        previousSessionId: String?,
        previousText: String,
        previousAttachments: List<PendingAttachment>,
        previousSkills: List<AgentSelectedSkill>,
    ) {
        sessionId = previousSessionId
        subscribe(providerId, previousSessionId)
        input.setText(previousText)
        input.setSelection(input.text?.length ?: 0)
        pendingAttachments.clear()
        pendingAttachments += previousAttachments
        selectedSkills.clear()
        selectedSkills += previousSkills
        renderAttachments()
        renderSelectedSkills()
        updateComposer()
    }

    private fun restoreComposerAfterFailure(
        text: String,
        attachments: List<PendingAttachment>,
        skills: List<AgentSelectedSkill>,
    ) {
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
        if (skills.isNotEmpty()) {
            selectedSkills.addAll(0, skills.filterNot { skill -> selectedSkills.any { it.id == skill.id } })
            renderSelectedSkills()
        }
        updateComposer()
    }

    private fun showSessionDrawer() {
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
                visibility = View.INVISIBLE
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
            drawerList.visibility = View.INVISIBLE
            sessionSearchList.visibility = View.GONE
            drawerStatusText.visibility = View.GONE
            sessionSearchStatusText.visibility = View.GONE
        }
        val runtime = AgentRuntimeRegistry.session(instanceId)
        if (runtime?.capabilities?.sessions?.list != true) {
            drawerSessionsKey = requestKey
            renderDrawerSessions()
            drawerStatusText.text = "当前 Agent 由自身管理历史会话"
            drawerStatusText.visibility = View.VISIBLE
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
                        providerId?.let { currentProviderId ->
                            sessionMetadataStore.reconcileSourceDirectory(
                                providerId = currentProviderId,
                                sourceSessionIds = result.value.sessions.mapTo(linkedSetOf(), AgentSessionSummary::id),
                            )
                        }
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
                        val message = AgentSurfaceNavigationPolicy.sessionListFailureMessage(result.message)
                        drawerStatusText.text = message
                        drawerStatusText.visibility = View.VISIBLE
                        sessionSearchStatusText.text = message
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
        val rows = AgentSurfaceNavigationPolicy.drawerRows(groups, expandedProjectCwds)
        drawerAdapter.selectedSessionId = sessionId
        drawerAdapter.submitList(rows)
        drawerList.visibility = if (rows.isEmpty()) View.INVISIBLE else View.VISIBLE
        drawerStatusText.visibility = if (rows.isEmpty()) View.VISIBLE else View.GONE
        if (rows.isEmpty()) drawerStatusText.text = "当前 Agent 还没有历史会话"
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

    private fun showSessionMenu(anchor: View, session: AgentSessionSummary) {
        val runtime = AgentRuntimeRegistry.session(instanceId)
        ui.showAnchoredMenu(
            context = context,
            anchor = anchor,
            widthDp = 184,
            items = AgentSurfaceNavigationPolicy.sessionMenuActions(
                renameSupported = runtime?.capabilities?.sessions?.rename == true,
                deleteSupported = runtime?.capabilities?.sessions?.delete == true,
                currentSessionId = runtime?.sessionId,
                targetSessionId = session.id,
            ).map { action ->
                when (action) {
                    AgentDrawerSessionMenuAction.Rename -> UiMenuItem(
                        label = "重命名",
                        iconRes = R.drawable.ic_edit_light,
                        onClick = { showSessionRenameDialog(session) },
                    )
                    AgentDrawerSessionMenuAction.Archive -> UiMenuItem(
                    label = "归档",
                    iconRes = R.drawable.ic_archive_light,
                    onClick = { archiveDrawerSession(session) },
                    )
                    is AgentDrawerSessionMenuAction.Delete -> UiMenuItem(
                        label = "删除",
                        iconRes = R.drawable.ic_delete_light,
                        enabled = action.enabled,
                        role = UiActionRole.Danger,
                        onClick = { showDrawerSessionDeleteConfirmation(session) },
                    )
                }
            },
        )
    }

    private fun archiveDrawerSession(session: AgentSessionSummary) {
        val currentProviderId = providerId?.takeIf(String::isNotBlank) ?: return
        if (sessionMetadataStore.archive(currentProviderId, session.id)) {
            renderDrawerSessions()
            renderSessionSearchResults()
            Toast.makeText(context, "已归档", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showDrawerSessionDeleteConfirmation(session: AgentSessionSummary) {
        val selected = agentId
            ?.let { agentRegistry.snapshot().entry(it) }
            ?: return
        showArchivedDeleteConfirmation(selected, listOf(session)) {
            drawerSessions = drawerSessions.filterNot { it.id == session.id }
            renderDrawerSessions()
            renderSessionSearchResults()
        }
    }

    private fun showSessionRenameDialog(session: AgentSessionSummary) {
        val originalTitle = session.title?.takeIf(String::isNotBlank).orEmpty()
        val inputField = EditText(context).apply {
            hint = "会话名称"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            maxLines = 1
            setText(originalTitle)
            setSelection(text?.length ?: 0)
            setTextColor(tokens.textPrimary)
            setHintTextColor(tokens.textTertiary)
            setPadding(ui.dp(16), 0, ui.dp(16), 0)
            background = ui.roundedBox(
                agentInputBackground,
                tokens.borderStrong,
                ui.dp(15).toFloat(),
                ui.dp(1),
            )
        }
        val dialog = Dialog(context)
        val cancel = TextView(context).apply {
            text = "取消"
            textSize = 14f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            gravity = Gravity.CENTER
            setTextColor(tokens.textSecondary)
            background = ui.roundedBox(agentSettingsSurface, android.graphics.Color.TRANSPARENT, ui.dp(16).toFloat())
            setOnClickListener { dialog.dismiss() }
        }
        val confirm = TextView(context).apply {
            text = "重命名"
            textSize = 14f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            gravity = Gravity.CENTER
            setTextColor(selectionPalette.primaryActionText)
            background = ui.roundedBox(
                selectionPalette.primaryAction,
                android.graphics.Color.TRANSPARENT,
                ui.dp(16).toFloat(),
            )
        }
        confirm.setOnClickListener {
            val title = inputField.text?.toString()?.trim().orEmpty()
            if (title.isBlank() || title.any(Char::isISOControl)) {
                inputField.error = "请输入有效的会话名称"
                return@setOnClickListener
            }
            confirm.isEnabled = false
            confirm.alpha = 0.48f
            confirm.text = "保存中…"
            lifecycleOwner.lifecycleScope.launch {
                when (val result = AgentRuntimeRegistry.renameSession(
                    instanceId,
                    generation,
                    AgentSessionRenameRequest(session.id, title),
                )) {
                    is AgentOperationResult.Success -> {
                        drawerSessions = drawerSessions.map {
                            if (it.id == session.id) it.copy(title = title) else it
                        }
                        dialog.dismiss()
                        renderDrawerSessions()
                        renderSessionSearchResults()
                        Toast.makeText(context, "已重命名", Toast.LENGTH_SHORT).show()
                    }
                    is AgentOperationResult.Unsupported -> {
                        confirm.isEnabled = true
                        confirm.alpha = 1f
                        confirm.text = "重命名"
                        inputField.error = "当前 Agent 不支持重命名"
                    }
                    is AgentOperationResult.Failure -> {
                        confirm.isEnabled = true
                        confirm.alpha = 1f
                        confirm.text = "重命名"
                        inputField.error = result.message
                    }
                }
            }
        }
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ui.dp(22), ui.dp(22), ui.dp(22), ui.dp(18))
            background = ui.roundedBox(agentSurface, android.graphics.Color.TRANSPARENT, ui.dp(26).toFloat())
            elevation = ui.dp(10).toFloat()
            addView(TextView(context).apply {
                text = "重命名会话"
                textSize = 19f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(tokens.textPrimary)
            })
            addView(inputField, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ui.dp(52),
            ).apply { setMargins(0, ui.dp(16), 0, 0) })
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(cancel, LinearLayout.LayoutParams(0, ui.dp(48), 1f))
                addView(confirm, LinearLayout.LayoutParams(0, ui.dp(48), 1f).apply {
                    setMargins(ui.dp(8), 0, 0, 0)
                })
            }, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { setMargins(0, ui.dp(18), 0, 0) })
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
        inputField.requestFocus()
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
    }

    private fun showProjectMenu(anchor: View, project: AgentSessionProjectGroup) {
        ui.showAnchoredMenu(
            context = context,
            anchor = anchor,
            widthDp = 172,
            items = listOf(
                UiMenuItem(
                    label = "归档项目",
                    iconRes = R.drawable.ic_archive_light,
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
        selectedSkillIds.clear()
        showSettingsPage(reload = settingsRegistrySnapshot == null)
    }

    private fun returnFromProviderManager() {
        if (providerLibraryMode != AgentProviderLibraryMode.Browse) {
            providerLibraryMode = AgentProviderLibraryMode.Browse
            selectedProviderIds.clear()
            showCurrentProviderList()
            return
        }
        selectedProviderIds.clear()
        returnToAgentSettings()
    }

    private fun showSettingsPage(reload: Boolean) {
        archivedSelectionBackHandler = null
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
                val target = selected?.configurationTarget()
                val configResult = target?.let { agentConfigurationApi.read(it) }
                AgentSettingsLoad(snapshot, target?.agentId, configResult)
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
                    text = selected.primaryStatusLabel()
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
        var unavailableArchivedSessions = sessionMetadataStore
            .archivedSessions(targetProviderId)
            .sortedBy(AgentArchivedSessionMetadata::sessionId)
        var archivedProjects = projectStore.archivedProjects(targetAgentId)
        var sessionStatusMessage: String? = "正在读取已归档会话…"
        val selectedKeys = linkedSetOf<AgentArchivedSelectionKey>()
        val expandedArchivedCwds = linkedSetOf<String>()
        var archiveExpansionSeeded = false
        lateinit var refreshArchivedContent: () -> Unit
        lateinit var renderArchiveState: () -> Unit
        lateinit var renderEditState: () -> Unit
        lateinit var renderArchivedRows: () -> Unit
        fun selectedSessionIds(): Set<String> = AgentArchivedSelectionPolicy.selectedSessionIds(selectedKeys)
        fun applySelection(next: Set<AgentArchivedSelectionKey>) {
            selectedKeys.clear()
            selectedKeys.addAll(next)
            renderEditState()
        }
        fun toggleSession(sessionId: String) {
            applySelection(AgentArchivedSelectionPolicy.toggleSession(
                current = selectedKeys,
                sessionId = sessionId,
            ))
        }
        fun toggleProject(row: AgentArchivedRow.GroupHeader) {
            applySelection(AgentArchivedSelectionPolicy.toggleProject(
                current = selectedKeys,
                childSessionIds = row.selectableSessionIds,
            ))
        }
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
                    toggleSession(archivedSession.id)
                } else {
                    showArchivedSessionActions(selected, archivedSession, refreshArchivedContent)
                }
            },
            onLongClick = { archivedSession ->
                editMode = true
                toggleSession(archivedSession.id)
            },
            onUnavailableClick = { unavailableSession ->
                if (editMode) {
                    toggleSession(unavailableSession.sessionId)
                } else {
                    showUnavailableArchivedSessionActions(selected, unavailableSession) {
                        unavailableArchivedSessions = sessionMetadataStore
                            .archivedSessions(targetProviderId)
                            .filterNot { metadata -> metadata.sessionId in archivedSessions.map(AgentSessionSummary::id) }
                            .sortedBy(AgentArchivedSessionMetadata::sessionId)
                        renderArchivedRows()
                        renderArchiveState()
                    }
                }
            },
            onUnavailableLongClick = { unavailableSession ->
                editMode = true
                toggleSession(unavailableSession.sessionId)
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
            },
            onProjectSelect = ::toggleProject,
            onProjectLongClick = { row ->
                editMode = true
                toggleProject(row)
            },
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
            if (!archiveExpansionSeeded &&
                (archivedSessions.isNotEmpty() || unavailableArchivedSessions.isNotEmpty() || archivedProjects.isNotEmpty())
            ) {
                when {
                    groups.defaultSessions.isNotEmpty() -> expandedArchivedCwds.add(groups.defaultCwd)
                    groups.projects.isNotEmpty() -> expandedArchivedCwds.add(groups.projects.first().cwd)
                    unavailableArchivedSessions.any {
                        it.sourceState == AgentArchivedSessionSourceState.Deleted
                    } -> expandedArchivedCwds.add(AgentSurfaceNavigationPolicy.DELETED_ARCHIVE_GROUP_CWD)
                    unavailableArchivedSessions.isNotEmpty() ->
                        expandedArchivedCwds.add(AgentSurfaceNavigationPolicy.UNCONFIRMED_ARCHIVE_GROUP_CWD)
                }
                archiveExpansionSeeded = true
            }
            archivedAdapter.submitList(buildList {
                addAll(AgentSurfaceNavigationPolicy.archivedRows(groups, expandedArchivedCwds, archivedProjects))
                addAll(AgentSurfaceNavigationPolicy.unavailableArchivedRows(
                    unavailableArchivedSessions,
                    expandedArchivedCwds,
                ))
            })
        }
        val selectAllIndicator = ImageView(context).apply {
            scaleType = ImageView.ScaleType.CENTER
        }
        val selectAll = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(selectAllIndicator, LinearLayout.LayoutParams(
                ui.dp(AgentSelectionVisualPolicy.TOUCH_TARGET_DP),
                ui.dp(AgentSelectionVisualPolicy.TOUCH_TARGET_DP),
            ).apply { setMargins(0, 0, ui.dp(4), 0) })
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
        fun showBatchRestoreConfirmation() {
            val selectedIds = selectedSessionIds()
            val sessions = archivedSessions.filter { it.id in selectedIds }
            val restorableCount = sessions.size
            if (restorableCount == 0) return
            val skippedCount = selectedIds.count { id ->
                unavailableArchivedSessions.any { it.sessionId == id }
            }
            showAgentDialogCard(
                title = "恢复所选内容？",
                message = buildString {
                    append("$restorableCount 项将回到列表。")
                    if (skippedCount > 0) append(" $skippedCount 条不可恢复记录会保留。")
                },
                actions = listOf(
                    AgentDialogAction("取消", UiActionRole.Secondary) { dialog, _ -> dialog.dismiss() },
                    AgentDialogAction(
                        label = "恢复",
                        role = UiActionRole.Primary,
                        filledPrimary = true,
                    ) { dialog, _ ->
                        sessions.forEach { sessionMetadataStore.restore(targetProviderId, it.id) }
                        dialog.dismiss()
                        editMode = false
                        selectedKeys.clear()
                        refreshArchivedContent()
                        Toast.makeText(context, "已恢复 $restorableCount 项", Toast.LENGTH_SHORT).show()
                    },
                ),
            )
        }
        fun showBatchDeleteConfirmation() {
            val selectedIds = selectedSessionIds()
            val sessions = archivedSessions.filter { it.id in selectedIds }
            val unavailable = unavailableArchivedSessions.filter { it.sessionId in selectedIds }
            val count = sessions.size + unavailable.size
            if (count == 0) return
            showAgentDialogCard(
                title = "删除所选内容？",
                message = "会话将从 Agent 永久删除；失效记录只从 Kite 移除。",
                actions = listOf(
                    AgentDialogAction("取消", UiActionRole.Secondary) { dialog, _ -> dialog.dismiss() },
                    AgentDialogAction("删除", UiActionRole.Danger) { dialog, button ->
                        button.isEnabled = false
                        button.alpha = 0.48f
                        button.text = "删除中…"
                        lifecycleOwner.lifecycleScope.launch {
                            val runtime = AgentRuntimeRegistry.session(instanceId)
                            var deleted = 0
                            val failures = mutableListOf<String>()
                            suspend fun deleteFromAgent(sessionId: String): Boolean = when (
                                val result = AgentRuntimeRegistry.deleteSession(instanceId, generation, sessionId)
                            ) {
                                is AgentOperationResult.Success -> true
                                is AgentOperationResult.Unsupported -> {
                                    failures += "当前 Agent 未提供永久删除"
                                    false
                                }
                                is AgentOperationResult.Failure -> {
                                    failures += result.message
                                    false
                                }
                            }
                            sessions.forEach { session ->
                                if (deleteFromAgent(session.id)) {
                                    sessionMetadataStore.remove(targetProviderId, session.id)
                                    deleted++
                                }
                            }
                            unavailable.forEach { metadata ->
                                val canDeleteNatively =
                                    metadata.sourceState != AgentArchivedSessionSourceState.Deleted &&
                                        AgentSurfaceNavigationPolicy.canDeleteUnavailableSessionNatively(
                                            targetProviderId = targetProviderId,
                                            runtimeProviderId = runtime?.providerId,
                                            deleteSupported = runtime?.capabilities?.sessions?.delete == true,
                                            currentSessionId = runtime?.sessionId,
                                            targetSessionId = metadata.sessionId,
                                        )
                                if (!canDeleteNatively || deleteFromAgent(metadata.sessionId)) {
                                    sessionMetadataStore.remove(targetProviderId, metadata.sessionId)
                                    deleted++
                                }
                            }
                            dialog.dismiss()
                            if (failures.isEmpty()) {
                                editMode = false
                                selectedKeys.clear()
                            }
                            refreshArchivedContent()
                            val message = if (failures.isEmpty()) {
                                "已删除 $deleted 项"
                            } else {
                                "已删除 $deleted 项；${failures.distinct().joinToString("；")}"
                            }
                            Toast.makeText(
                                context,
                                message,
                                if (failures.isEmpty()) Toast.LENGTH_SHORT else Toast.LENGTH_LONG,
                            ).show()
                        }
                    },
                ),
            )
        }
        val restoreSelected = archiveBatchActionButton("恢复") {
            showBatchRestoreConfirmation()
        }
        val deleteSelected = archiveBatchActionButton("删除") {
            showBatchDeleteConfirmation()
        }
        val batchActions = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            visibility = View.GONE
            setPadding(ui.dp(18), ui.dp(8), ui.dp(18), ui.dp(14))
            addView(restoreSelected, LinearLayout.LayoutParams(
                0,
                ui.dp(AgentSelectionVisualPolicy.ACTION_HEIGHT_DP),
                1f,
            ))
            addView(deleteSelected, LinearLayout.LayoutParams(
                0,
                ui.dp(AgentSelectionVisualPolicy.ACTION_HEIGHT_DP),
                1f,
            ).apply {
                setMargins(ui.dp(10), 0, 0, 0)
            })
        }
        renderEditState = {
            val selectedIds = selectedSessionIds()
            archivedAdapter.setSelectionState(editMode, selectedIds)
            selectionRow.visibility = if (editMode) View.VISIBLE else View.GONE
            batchActions.visibility = if (editMode) View.VISIBLE else View.GONE
            val allKeys = AgentArchivedSelectionPolicy.selectAll(
                sessionIds = archivedSessions.map(AgentSessionSummary::id) +
                    unavailableArchivedSessions.map(AgentArchivedSessionMetadata::sessionId),
            )
            val allSelected = allKeys.isNotEmpty() && selectedKeys.containsAll(allKeys)
            renderArchiveSelectionIndicator(selectAllIndicator, allSelected)
            selectAll.contentDescription = if (allSelected) "取消全选归档内容" else "全选归档内容"
            selectedCount.text = "已选择 ${selectedKeys.size} 项"
            val hasSelection = selectedKeys.isNotEmpty()
            val hasRestorableContent = archivedSessions.any { it.id in selectedIds }
            restoreSelected.isEnabled = hasRestorableContent
            renderArchiveBatchAction(restoreSelected, hasRestorableContent, danger = false)
            val runtime = AgentRuntimeRegistry.session(instanceId)
            val selectedReadableIds = archivedSessions
                .filter { it.id in selectedIds }
                .mapTo(linkedSetOf(), AgentSessionSummary::id)
            val canDeleteReadableSessions = selectedReadableIds.isEmpty() ||
                AgentArchivedSelectionPolicy.canDelete(
                    selectedIds = selectedReadableIds,
                    currentSessionId = runtime?.sessionId,
                    deleteSupported = runtime?.providerId == targetProviderId &&
                        runtime.capabilities.sessions.delete,
                )
            val canDelete = hasSelection && canDeleteReadableSessions
            deleteSelected.isEnabled = canDelete
            renderArchiveBatchAction(deleteSelected, canDelete, danger = true)
            deleteSelected.contentDescription = when {
                !hasSelection -> "删除归档会话，请先选择"
                !canDeleteReadableSessions && runtime?.sessionId in selectedReadableIds ->
                    "当前窗口使用中的会话不能永久删除"
                !canDeleteReadableSessions -> "当前 Agent 不支持永久删除所选会话"
                else -> "删除选中的 ${selectedKeys.size} 项"
            }
        }
        renderArchiveState = {
            val hasItems = archivedSessions.isNotEmpty() ||
                unavailableArchivedSessions.isNotEmpty() ||
                archivedProjects.isNotEmpty()
            archivedList.visibility = if (hasItems) View.VISIBLE else View.GONE
            if (!hasItems) {
                editMode = false
                selectedKeys.clear()
            }
            status.text = sessionStatusMessage ?: if (hasItems) "" else "暂无已归档内容"
            status.visibility = if (status.text.isNullOrBlank()) View.GONE else View.VISIBLE
            renderEditState()
        }
        selectAll.setOnClickListener {
            val allKeys = AgentArchivedSelectionPolicy.selectAll(
                sessionIds = archivedSessions.map(AgentSessionSummary::id) +
                    unavailableArchivedSessions.map(AgentArchivedSessionMetadata::sessionId),
            )
            val checked = allKeys.isNotEmpty() && !selectedKeys.containsAll(allKeys)
            selectedKeys.clear()
            if (checked) selectedKeys.addAll(allKeys)
            renderEditState()
        }
        archivedSelectionBackHandler = {
            if (!editMode) {
                false
            } else {
                editMode = false
                selectedKeys.clear()
                renderEditState()
                true
            }
        }
        val page = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(agentPageBackground)
            addView(buildAgentSubpageHeader(
                title = "归档内容管理",
                backDescription = "返回 Agent 设置",
                onBack = {
                    if (archivedSelectionBackHandler?.invoke() != true) returnToAgentSettings()
                },
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

        refreshArchivedContent = refresh@{
            navigationJob?.cancel()
            archivedProjects = projectStore.archivedProjects(targetAgentId)
            val archivedIds = sessionMetadataStore.archivedSessionIds(targetProviderId)
            archivedSessions = archivedSessions.filter { it.id in archivedIds }
            val availableIds = archivedSessions.mapTo(linkedSetOf(), AgentSessionSummary::id)
            unavailableArchivedSessions = sessionMetadataStore
                .archivedSessions(targetProviderId)
                .filterNot { it.sessionId in availableIds }
                .sortedBy(AgentArchivedSessionMetadata::sessionId)
            val runtime = AgentRuntimeRegistry.session(instanceId)
            if (runtime == null || runtime.providerId != targetProviderId) {
                sessionStatusMessage = "打开当前 Agent 后可确认源会话状态"
                renderArchivedRows()
                renderArchiveState()
                return@refresh
            }
            if (!runtime.capabilities.sessions.list) {
                sessionStatusMessage = "当前 Agent 未提供会话列表；项目仍可管理"
                renderArchivedRows()
                renderArchiveState()
                return@refresh
            }
            sessionStatusMessage = "正在读取已归档会话…"
            renderArchivedRows()
            renderArchiveState()
            navigationJob = lifecycleOwner.lifecycleScope.launch {
                when (val result = AgentRuntimeRegistry.listSessions(instanceId, generation)) {
                    is AgentOperationResult.Success -> {
                        if (navigationScreen != AgentNavigationScreen.ArchivedContent) return@launch
                        sessionMetadataStore.reconcileSourceDirectory(
                            providerId = targetProviderId,
                            sourceSessionIds = result.value.sessions.mapTo(linkedSetOf(), AgentSessionSummary::id),
                        )
                        val archivedIds = sessionMetadataStore.archivedSessionIds(targetProviderId)
                        val projection = AgentSurfaceNavigationPolicy.archivedSessionProjection(
                            result.value.sessions,
                            archivedIds,
                        )
                        archivedSessions = projection.sessions
                        unavailableArchivedSessions = sessionMetadataStore
                            .archivedSessions(targetProviderId)
                            .filter { it.sessionId in projection.unavailableSessionIds }
                            .sortedBy(AgentArchivedSessionMetadata::sessionId)
                        val validKeys = AgentArchivedSelectionPolicy.selectAll(
                            sessionIds = archivedSessions.map(AgentSessionSummary::id) +
                                unavailableArchivedSessions.map(AgentArchivedSessionMetadata::sessionId),
                        )
                        selectedKeys.retainAll(validKeys)
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

    private fun renderArchiveSelectionIndicator(indicator: ImageView, selected: Boolean) {
        renderArchivedSelectionIndicator(
            indicator = indicator,
            ui = ui,
            palette = selectionPalette,
            state = if (selected) AgentArchivedProjectSelectionState.Checked
            else AgentArchivedProjectSelectionState.Unchecked,
        )
    }

    private fun archiveBatchActionButton(label: String, onClick: () -> Unit): TextView =
        TextView(context).apply {
            text = label
            textSize = 14.5f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
        }

    private fun renderArchiveBatchAction(button: TextView, enabled: Boolean, danger: Boolean) {
        val backgroundColor = when {
            !enabled -> selectionPalette.disabledAction
            danger -> selectionPalette.dangerAction
            else -> selectionPalette.primaryAction
        }
        val textColor = when {
            !enabled -> selectionPalette.disabledActionText
            danger -> selectionPalette.dangerActionText
            else -> selectionPalette.primaryActionText
        }
        button.setTextColor(textColor)
        button.background = ui.roundedBox(
            backgroundColor,
            android.graphics.Color.TRANSPARENT,
            ui.dp(AgentSelectionVisualPolicy.ACTION_RADIUS_DP).toFloat(),
        )
        button.alpha = 1f
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
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                gravity = Gravity.CENTER
                isEnabled = action.enabled
                alpha = if (action.enabled) 1f else 0.36f
                setTextColor(
                    if (action.filledPrimary) agentPageBackground else when (action.role) {
                        UiActionRole.Primary -> tokens.primaryStrong
                        UiActionRole.Secondary -> tokens.textSecondary
                        UiActionRole.Danger -> tokens.danger
                    }
                )
                background = ui.roundedBox(
                    if (action.filledPrimary) tokens.textPrimary else when (action.role) {
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
                        text = if (action.selected) "●" else ""
                        textSize = 11f
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
        val target = selected.configurationTarget()
        val capabilities = agentConfigurationApi.capabilities(target)
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
            is AgentConfigReadResult.Ready -> persistentSnapshotRows(selected, target, result.snapshot)
        }
    }

    private fun persistentSnapshotRows(
        selected: AgentRegistryEntry,
        target: AgentConfigurationTarget,
        snapshot: AgentLiveConfigSnapshot
    ): List<SettingsRow> = buildList {
        val capabilities = agentConfigurationApi.capabilities(target) ?: return@buildList
        val providerSnapshot = agentProviderCatalogApi.snapshot(target).toConfigurationProjection(target)
        val supportsProviders = capabilities.supports(AgentPersistentConfigCapability.Provider)
        val officialAccounts = selected.registration.officialAccounts
        if (supportsProviders || officialAccounts.isNotEmpty()) {
            val providerSummary = providerSnapshot.providers.takeIf { it.isNotEmpty() }
                ?.joinToString("、") { provider ->
                    "${provider.displayName}（${provider.models.size} 个模型）"
                }
                ?: providerSnapshot.providerIds.takeIf { it.isNotEmpty() }?.joinToString("、")
            val accountSummary = officialAccounts.takeIf { it.isNotEmpty() }
                ?.joinToString("、") { account ->
                    val status = officialAccountManager.state(
                        selected.registration.definition.agentId,
                        account.id,
                    ).status.officialAccountLabel()
                    "${account.displayName}（$status）"
                }
            add(SettingsRow(
                title = "供应商配置",
                subtitle = listOfNotNull(providerSummary, accountSummary).joinToString("、")
                    .ifBlank { "尚未配置供应商" },
                onClick = if (
                    capabilities.supports(AgentPersistentConfigCapability.ProviderProfiles) ||
                    officialAccounts.isNotEmpty()
                ) {
                    { openProviderManager(selected, target) }
                } else null
            ))
        }
        if (capabilities.supports(AgentPersistentConfigCapability.PermissionProfiles)) {
            val active = snapshot.permissionProfiles.firstOrNull {
                it.id == snapshot.activePermissionProfileId
            }
            add(SettingsRow(
                title = "默认权限",
                subtitle = active?.let { profile ->
                    "${profile.level?.displayName ?: profile.displayName}·以后新建或重连的会话"
                } ?: "使用 Agent 默认或自定义规则",
                onClick = snapshot.permissionProfiles.takeIf { it.isNotEmpty() }?.let {
                    { showDefaultPermissionManager(selected, target, snapshot) }
                },
            ))
        }
        if (capabilities.supports(AgentPersistentConfigCapability.Mcp)) {
            add(SettingsRow(
                "MCP",
                snapshot.mcpServers.takeIf { it.isNotEmpty() }
                    ?.joinToString("、") { "${it.id}（${if (it.enabled) "启用" else "停用"}）" }
                    ?: "尚未配置 MCP",
                onClick = { showMcpManager(selected, target, snapshot) }
            ))
        }
        if (capabilities.supports(AgentPersistentConfigCapability.Skill)) {
            add(SettingsRow(
                "Skill",
                snapshot.skills.takeIf { it.isNotEmpty() }
                    ?.joinToString("、") { it.displayName }
                    ?: "尚未安装 Skill",
                onClick = { showSkillManager(selected, target, snapshot) }
            ))
        }
        if (capabilities.supports(AgentPersistentConfigCapability.CoreDocuments)) {
            add(SettingsRow(
                "核心设定",
                "管理当前 Agent 的全局说明、人格或固定工作区规则",
                onClick = { showCoreDocumentManager(selected, target) }
            ))
        }
        snapshot.warnings.firstOrNull()?.let { add(SettingsRow("注意", it)) }
    }

    private fun showDefaultPermissionManager(
        selected: AgentRegistryEntry,
        target: AgentConfigurationTarget,
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
                                    applyDefaultPermissionProfile(selected, target, snapshot, profile)
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
        target: AgentConfigurationTarget,
        snapshot: AgentLiveConfigSnapshot,
        profile: AgentPermissionProfileSummary,
    ) {
        if (defaultPermissionPendingProfileId != null || profile.id == snapshot.activePermissionProfileId) return
        val targetAgentId = selected.registration.definition.agentId
        val requestRevision = ++settingsLoadRevision
        defaultPermissionPendingProfileId = profile.id
        showDefaultPermissionManager(
            selected,
            target,
            snapshot.copy(activePermissionProfileId = profile.id),
        )
        navigationJob?.cancel()
        navigationJob = lifecycleOwner.lifecycleScope.launch {
            val outcome = withContext(Dispatchers.IO) {
                agentConfigurationApi.apply(
                    target = target,
                    expectedRevision = snapshot.revision,
                    intents = listOf(AgentConfigurationIntent.SetPermission(profile.id)),
                )
            }
            if (requestRevision != settingsLoadRevision || navigationScreen != AgentNavigationScreen.DefaultPermission) {
                return@launch
            }
            defaultPermissionPendingProfileId = null
            persistentConfigAgentId = targetAgentId
            persistentConfigResult = outcome.current
            val refreshedSnapshot = (outcome.current as? AgentConfigReadResult.Ready)?.snapshot
            when (val result = outcome.result) {
                is AgentConfigApplyResult.Applied -> {
                    val applied = refreshedSnapshot ?: result.snapshot
                    showDefaultPermissionManager(selected, target, applied)
                    Toast.makeText(
                        context,
                        "默认权限已保存；当前会话保持不变",
                        Toast.LENGTH_SHORT,
                    ).show()
                }
                else -> {
                    showDefaultPermissionManager(selected, target, refreshedSnapshot ?: snapshot)
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
        target: AgentConfigurationTarget,
        reload: Boolean = true,
    ) {
        coreDocumentPageAgentId = selected.registration.definition.agentId
        coreDocumentPageTarget = target
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
        if (reload) loadCoreDocuments(selected, target) else renderCoreDocuments(coreDocumentDescriptors)
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

    private fun loadCoreDocuments(selected: AgentRegistryEntry, target: AgentConfigurationTarget) {
        val host = coreDocumentListHost ?: return
        host.removeAllViews()
        host.addView(settingsMessage("正在读取核心设定目录…"))
        val targetAgentId = selected.registration.definition.agentId
        val workspacePath = coreDocumentWorkspacePath
        val requestRevision = ++coreDocumentLoadRevision
        navigationJob?.cancel()
        navigationJob = lifecycleOwner.lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                agentConfigurationApi.listCoreDocuments(target, workspacePath)
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
        val target = coreDocumentPageTarget
        val targetAgentId = coreDocumentPageAgentId
        if (target == null || targetAgentId == null) {
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
                agentConfigurationApi.readCoreDocument(target, descriptor.id, coreDocumentWorkspacePath)
            }
            if (
                requestRevision != coreDocumentLoadRevision ||
                navigationScreen != AgentNavigationScreen.CoreDocumentEditor ||
                coreDocumentPageAgentId != targetAgentId
            ) return@launch
            when (result) {
                is AgentCoreDocumentReadResult.Ready -> renderCoreDocumentEditor(target, result.snapshot)
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

    private fun renderCoreDocumentEditor(target: AgentConfigurationTarget, snapshot: AgentCoreDocumentSnapshot) {
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
            setOnClickListener { saveCoreDocument(target, this, status) }
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

    private fun saveCoreDocument(target: AgentConfigurationTarget, saveAction: TextView, status: TextView) {
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
                agentConfigurationApi.writeCoreDocument(
                    target,
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
        val target = coreDocumentPageTarget
        if (selected == null || target == null) {
            returnToAgentSettings()
            return
        }
        showCoreDocumentManager(selected, target, reload = false)
    }

    private fun openProviderManager(
        selected: AgentRegistryEntry,
        target: AgentConfigurationTarget,
    ) {
        val targetAgentId = selected.registration.definition.agentId
        val catalog = agentProviderCatalogApi.snapshot(target)
        val projection = catalog.toConfigurationProjection(target)
        if (targetAgentId == agentId) {
            draftProviderCatalogSnapshot = catalog
            draftModelSnapshot = projection
        }
        showProviderManager(selected, target, projection)
        renderSessionConfigurationControls()
    }

    private fun refreshProviderCatalog(
        selected: AgentRegistryEntry,
        target: AgentConfigurationTarget,
        refresh: SwipeRefreshLayout,
    ) {
        if (providerCatalogRefreshJob?.isActive == true) return
        val targetAgentId = selected.registration.definition.agentId
        val requestRevision = ++providerCatalogLoadRevision
        refresh.isRefreshing = true
        providerCatalogRefreshJob = lifecycleOwner.lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                agentProviderCatalogApi.refreshFreeProviderCatalog(target)
            }
            refresh.isRefreshing = false
            if (
                requestRevision != providerCatalogLoadRevision ||
                navigationScreen != AgentNavigationScreen.ProviderList ||
                providerPageAgentId != targetAgentId
            ) return@launch
            val projection = result.snapshot.toConfigurationProjection(target)
            providerPageSnapshot = projection
            if (targetAgentId == agentId) {
                draftProviderCatalogSnapshot = result.snapshot
                draftModelSnapshot = projection
                applyDraftModelDefault(targetAgentId, projection)
            }
            showProviderManager(selected, target, projection)
            renderSessionConfigurationControls()
            val message = result.warnings.firstOrNull()
                ?: if (result.refreshed) "免费模型目录已更新" else "本地模型目录已是最新状态"
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun showProviderManager(
        selected: AgentRegistryEntry,
        target: AgentConfigurationTarget,
        snapshot: AgentLiveConfigSnapshot
    ) {
        val targetAgentId = selected.registration.definition.agentId
        if (providerPageAgentId != null && providerPageAgentId != targetAgentId) {
            providerLibraryGroupId = AgentModelLibraryStore.ALL_GROUP_ID
            providerLibraryMode = AgentProviderLibraryMode.Browse
            expandedProviderIds.clear()
            selectedProviderIds.clear()
        }
        providerPageAgentId = targetAgentId
        providerPageTarget = target
        providerPageSnapshot = snapshot
        observeOfficialAccounts(targetAgentId, selected, target)
        navigationScreen = AgentNavigationScreen.ProviderList
        navigationHost.removeAllViews()
        navigationHost.addView(
            buildProviderListPage(selected, target, snapshot),
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
        navigationHost.visibility = View.VISIBLE
    }

    private fun observeOfficialAccounts(
        targetAgentId: String,
        selected: AgentRegistryEntry,
        target: AgentConfigurationTarget,
    ) {
        if (officialAccountObservedAgentId != targetAgentId) {
            officialAccountObservation?.cancel()
            officialAccountObservedAgentId = targetAgentId
            var previous = officialAccountManager.states.value.filterKeys { it.agentId == targetAgentId }
            officialAccountObservation = lifecycleOwner.lifecycleScope.launch {
                lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    officialAccountManager.states.collect { allStates ->
                        val relevant = allStates.filterKeys { it.agentId == targetAgentId }
                        if (relevant == previous) return@collect
                        previous = relevant
                        if (
                            navigationScreen == AgentNavigationScreen.ProviderList &&
                            providerPageAgentId == targetAgentId
                        ) {
                            showProviderManager(
                                selected,
                                target,
                                providerPageSnapshot ?: return@collect,
                            )
                        }
                    }
                }
            }
        }
        officialAccountManager.accounts(targetAgentId).forEach { account ->
            if (officialAccountManager.state(targetAgentId, account.id).status == AgentOfficialAccountStatus.Unknown) {
                officialAccountManager.refresh(targetAgentId, account.id)
            }
        }
    }

    private fun showMcpManager(
        selected: AgentRegistryEntry,
        target: AgentConfigurationTarget,
        snapshot: AgentLiveConfigSnapshot,
    ) {
        val targetAgentId = selected.registration.definition.agentId
        mcpPageAgentId = targetAgentId
        mcpPageTarget = target
        mcpPageSnapshot = snapshot
        mcpConnectionStates.keys.retainAll(snapshot.mcpServers.map(AgentMcpSummary::id).toSet())
        mcpConnectionMessages.keys.retainAll(snapshot.mcpServers.map(AgentMcpSummary::id).toSet())
        navigationScreen = AgentNavigationScreen.McpList
        navigationHost.removeAllViews()
        navigationHost.addView(
            buildMcpListPage(selected, target),
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        navigationHost.visibility = View.VISIBLE
        renderMcpSnapshot(snapshot)
        refreshMcpSnapshot(selected, target)
    }

    /**
     * MCP 文件可能由 Agent 自己或外部工具改动。进入管理页时先显示已有快照，再在 IO
     * 线程重新读取原生事实；不得把目录读取放进 RecyclerView 绑定或页面绘制路径。
     */
    private fun refreshMcpSnapshot(
        selected: AgentRegistryEntry,
        target: AgentConfigurationTarget,
    ) {
        val targetAgentId = selected.registration.definition.agentId
        val requestRevision = ++settingsLoadRevision
        navigationJob?.cancel()
        navigationJob = lifecycleOwner.lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { agentConfigurationApi.read(target) }
            if (
                requestRevision != settingsLoadRevision ||
                navigationScreen != AgentNavigationScreen.McpList ||
                mcpPageAgentId != targetAgentId
            ) return@launch
            persistentConfigAgentId = targetAgentId
            persistentConfigResult = result
            when (result) {
                is AgentConfigReadResult.Ready -> {
                    val ids = result.snapshot.mcpServers.map(AgentMcpSummary::id).toSet()
                    mcpConnectionStates.keys.retainAll(ids)
                    mcpConnectionMessages.keys.retainAll(ids)
                    renderMcpSnapshot(result.snapshot)
                }
                is AgentConfigReadResult.Failed -> if (mcpPageSnapshot?.mcpServers.isNullOrEmpty()) {
                    mcpPageStatusText?.apply { text = result.message; visibility = View.VISIBLE }
                }
                is AgentConfigReadResult.Unavailable -> if (mcpPageSnapshot?.mcpServers.isNullOrEmpty()) {
                    mcpPageStatusText?.apply {
                        text = result.discovery.warnings.firstOrNull() ?: "当前 Agent 的 MCP 配置不可用"
                        visibility = View.VISIBLE
                    }
                }
            }
        }
    }

    private fun buildMcpListPage(
        selected: AgentRegistryEntry,
        target: AgentConfigurationTarget,
    ): View {
        val targetAgentId = selected.registration.definition.agentId
        val listAdapter = AgentMcpListAdapter(
            context = context,
            tokens = tokens,
            onClick = { item -> showMcpActions(selected, target, item.server) },
            onConnectionCheck = { item -> checkMcpConnection(selected, target, item.server) },
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
        val capabilities = agentConfigurationApi.capabilities(target)
        val canCreate = AgentMcpOperation.Create in capabilities?.mcpOperations.orEmpty() &&
            capabilities?.mcpTransports.orEmpty().isNotEmpty()
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
                    { mcpPageSnapshot?.let { current -> showMcpEditor(selected, target, current, null) } }
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
                    text = if (AgentMcpOperation.CheckConnection in capabilities?.mcpOperations.orEmpty()) {
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
        target: AgentConfigurationTarget,
        server: AgentMcpSummary,
    ) {
        val actions = buildList {
            if (AgentMcpOperation.Edit in server.allowedOperations) {
                add(AgentChoiceAction("编辑") {
                    mcpPageSnapshot?.let { current -> showMcpEditor(selected, target, current, server) }
                })
            }
            if (server.enabled && AgentMcpOperation.Disable in server.allowedOperations) {
                add(AgentChoiceAction("停用") {
                    applyMcpChange(
                        selected,
                        target,
                        server.id,
                        AgentConfigurationIntent.SetMcpEnabled(server.id, false),
                        "已停用 ${server.id}",
                    )
                })
            } else if (!server.enabled && AgentMcpOperation.Enable in server.allowedOperations) {
                add(AgentChoiceAction("启用") {
                    applyMcpChange(
                        selected,
                        target,
                        server.id,
                        AgentConfigurationIntent.SetMcpEnabled(server.id, true),
                        "已启用 ${server.id}",
                    )
                })
            }
            if (server.enabled && AgentMcpOperation.CheckConnection in server.allowedOperations) {
                add(AgentChoiceAction("检查连接") { checkMcpConnection(selected, target, server) })
            }
            if (AgentMcpOperation.Remove in server.allowedOperations) {
                add(AgentChoiceAction(
                    label = "移除",
                    role = UiActionRole.Danger,
                    onClick = { showMcpRemoveConfirmation(selected, target, server) },
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
        target: AgentConfigurationTarget,
        server: AgentMcpSummary,
    ) {
        val targetAgentId = selected.registration.definition.agentId
        val requestRevision = ++settingsLoadRevision
        mcpConnectionStates[server.id] = AgentMcpConnectionState.Checking
        mcpConnectionMessages.remove(server.id)
        mcpPageSnapshot?.let { renderMcpSnapshot(it, pendingServerId = server.id) }
        navigationJob?.cancel()
        navigationJob = lifecycleOwner.lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { agentConfigurationApi.checkMcp(target, server.id) }
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
        target: AgentConfigurationTarget,
        serverId: String,
        intent: AgentConfigurationIntent,
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
                agentConfigurationApi.apply(target, snapshot.revision, listOf(intent))
            }
            if (requestRevision != settingsLoadRevision || selectedSettingsAgentId != targetAgentId) return@launch
            persistentConfigAgentId = targetAgentId
            persistentConfigResult = outcome.current
            val refreshedSnapshot = (outcome.current as? AgentConfigReadResult.Ready)?.snapshot
                ?: (outcome.result as? AgentConfigApplyResult.Applied)?.snapshot
                ?: snapshot
            if (outcome.result is AgentConfigApplyResult.Applied) {
                mcpConnectionStates.remove(serverId)
                mcpConnectionMessages.remove(serverId)
                mcpEditorSaveAction = null
                mcpEditorStatusText = null
                showMcpManager(selected, target, refreshedSnapshot)
                Toast.makeText(context, successMessage, Toast.LENGTH_SHORT).show()
            } else {
                mcpPageSnapshot = refreshedSnapshot
                mcpEditorSaveAction?.apply { isEnabled = true; alpha = 1f }
                mcpEditorStatusText?.apply {
                    text = outcome.result.userMessage(successMessage)
                    setTextColor(android.graphics.Color.rgb(198, 40, 40))
                    visibility = View.VISIBLE
                }
                if (navigationScreen == AgentNavigationScreen.McpList) renderMcpSnapshot(refreshedSnapshot)
                Toast.makeText(context, outcome.result.userMessage(successMessage), Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showMcpRemoveConfirmation(
        selected: AgentRegistryEntry,
        target: AgentConfigurationTarget,
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
                        target,
                        server.id,
                        AgentConfigurationIntent.RemoveMcp(server.id),
                        "已移除 ${server.id}",
                    )
                },
            ),
        )
    }

    private fun showCurrentMcpList() {
        val targetAgentId = mcpPageAgentId ?: selectedSettingsAgentId
        val selected = settingsRegistrySnapshot?.entry(targetAgentId.orEmpty())
        val target = mcpPageTarget
        val snapshot = mcpPageSnapshot
        if (selected == null || target == null || snapshot == null) {
            returnToAgentSettings()
            return
        }
        mcpEditorSaveAction = null
        mcpEditorStatusText = null
        showMcpManager(selected, target, snapshot)
    }

    private fun showMcpEditor(
        selected: AgentRegistryEntry,
        target: AgentConfigurationTarget,
        snapshot: AgentLiveConfigSnapshot,
        existing: AgentMcpSummary?,
    ) {
        mcpPageAgentId = selected.registration.definition.agentId
        mcpPageTarget = target
        mcpPageSnapshot = snapshot
        navigationScreen = AgentNavigationScreen.McpEditor
        navigationHost.removeAllViews()
        navigationHost.addView(
            buildMcpEditorPage(selected, target, existing),
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        navigationHost.visibility = View.VISIBLE
    }

    private fun buildMcpEditorPage(
        selected: AgentRegistryEntry,
        target: AgentConfigurationTarget,
        existing: AgentMcpSummary?,
    ): View {
        val supportedTransports = agentConfigurationApi.capabilities(target)?.mcpTransports.orEmpty()
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

        content.addView(sectionTitle("连接方式", "远程地址会由当前 Agent 的兼容层翻译为它实际支持的传输方式。"))
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
            setTextColor(tokens.textPrimary)
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
                            target,
                            result.draft.id,
                            AgentConfigurationIntent.ConfigureMcp(result.draft),
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
        target: AgentConfigurationTarget,
        snapshot: AgentLiveConfigSnapshot,
    ) {
        selectedSkillIds.clear()
        skillPageAgentId = selected.registration.definition.agentId
        skillPageTarget = target
        skillPageSnapshot = snapshot
        renderSkillManagerPage(selected, target, snapshot)
        refreshSkillSnapshot(selected, target)
    }

    private fun renderSkillManagerPage(
        selected: AgentRegistryEntry,
        target: AgentConfigurationTarget,
        snapshot: AgentLiveConfigSnapshot,
    ) {
        navigationScreen = AgentNavigationScreen.SkillList
        navigationHost.removeAllViews()
        navigationHost.addView(
            buildSkillListPage(selected, target),
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        navigationHost.visibility = View.VISIBLE
        renderSkillSnapshot(snapshot)
    }

    /** Skill 与 MCP 使用同一刷新边界：进入页面时回填一次，列表只消费完成后的快照。 */
    private fun refreshSkillSnapshot(
        selected: AgentRegistryEntry,
        target: AgentConfigurationTarget,
    ) {
        val targetAgentId = selected.registration.definition.agentId
        val requestRevision = ++settingsLoadRevision
        val refreshView = skillPageRefreshView
        refreshView?.isRefreshing = true
        navigationJob?.cancel()
        navigationJob = lifecycleOwner.lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { agentConfigurationApi.read(target) }
            refreshView?.isRefreshing = false
            if (
                requestRevision != settingsLoadRevision ||
                navigationScreen != AgentNavigationScreen.SkillList ||
                skillPageAgentId != targetAgentId
            ) return@launch
            persistentConfigAgentId = targetAgentId
            persistentConfigResult = result
            when (result) {
                is AgentConfigReadResult.Ready -> renderSkillSnapshot(result.snapshot)
                is AgentConfigReadResult.Failed -> if (skillPageSnapshot?.skills.isNullOrEmpty()) {
                    skillPageStatusText?.apply { text = result.message; visibility = View.VISIBLE }
                }
                is AgentConfigReadResult.Unavailable -> if (skillPageSnapshot?.skills.isNullOrEmpty()) {
                    skillPageStatusText?.apply {
                        text = result.discovery.warnings.firstOrNull() ?: "当前 Agent 的 Skill 配置不可用"
                        visibility = View.VISIBLE
                    }
                }
            }
        }
    }

    private fun buildSkillListPage(
        selected: AgentRegistryEntry,
        target: AgentConfigurationTarget,
    ): View {
        val targetAgentId = selected.registration.definition.agentId
        val listAdapter = AgentSkillListAdapter(
            context = context,
            tokens = tokens,
            onClick = { skill ->
                if (selectedSkillIds.isEmpty()) {
                    showSkillDocumentEditor(skill)
                } else {
                    toggleSkillBatchSelection(selected, target, skill)
                }
            },
            onLongClick = { skill -> toggleSkillBatchSelection(selected, target, skill) },
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
        val refresh = SwipeRefreshLayout(context).apply {
            setColorSchemeColors(tokens.textPrimary)
            setProgressBackgroundColorSchemeColor(agentSettingsSurface)
            addView(viewport, ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ))
            setOnChildScrollUpCallback { _, _ -> skillList.canScrollVertically(-1) }
            setOnRefreshListener { refreshSkillSnapshot(selected, target) }
        }.also { skillPageRefreshView = it }
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(agentPageBackground)
            addView(buildAgentSubpageHeader(
                title = "Skill",
                backDescription = "返回 Agent 设置",
                onBack = ::returnToAgentSettings,
                actionIcon = R.drawable.ic_add_light.takeIf {
                    AgentSkillOperation.Import in agentConfigurationApi.capabilities(target)?.skillOperations.orEmpty()
                },
                actionDescription = "导入 Skill",
                onAction = if (AgentSkillOperation.Import in agentConfigurationApi.capabilities(target)?.skillOperations.orEmpty()) {
                    { showSkillImportPicker(selected, target) }
                } else null,
            ), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(64)))
            addView(TextView(context).apply {
                text = selected.registration.definition.displayName
                textSize = 12.5f
                setTextColor(tokens.textSecondary)
                setPadding(ui.dp(22), ui.dp(7), ui.dp(22), ui.dp(12))
                contentDescription = "$targetAgentId 的 Skill"
            })
            addView(refresh, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f,
            ))
            if (selectedSkillIds.isNotEmpty()) {
                addView(buildSkillBatchBar(selected, target), LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ui.dp(70),
                ))
            }
        }
    }

    private fun renderSkillSnapshot(
        snapshot: AgentLiveConfigSnapshot,
        pendingSkillId: String? = null,
        message: String? = null,
    ) {
        skillPageSnapshot = snapshot
        skillPageTarget?.let { target -> AgentSkillCatalogCache.update(target, snapshot.skills) }
        skillPageListAdapter?.submit(snapshot.skills, pendingSkillId, selectedSkillIds)
        skillPageListView?.visibility = if (snapshot.skills.isEmpty()) View.GONE else View.VISIBLE
        skillPageStatusText?.apply {
            text = message ?: "尚未安装 Skill"
            visibility = if (snapshot.skills.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun toggleSkillBatchSelection(
        selected: AgentRegistryEntry,
        target: AgentConfigurationTarget,
        skill: AgentSkillSummary,
    ) {
        if (AgentSkillOperation.Remove !in skill.allowedOperations) {
            Toast.makeText(context, "${skill.displayName} 来自只读位置，不能删除", Toast.LENGTH_SHORT).show()
            return
        }
        if (!selectedSkillIds.add(skill.id)) selectedSkillIds.remove(skill.id)
        val snapshot = skillPageSnapshot ?: return
        renderSkillManagerPage(selected, target, snapshot)
    }

    private fun buildSkillBatchBar(
        selected: AgentRegistryEntry,
        target: AgentConfigurationTarget,
    ): View = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(ui.dp(16), ui.dp(8), ui.dp(16), ui.dp(10))
        setBackgroundColor(agentSurface)
        addView(TextView(context).apply {
            text = "已选 ${selectedSkillIds.size} 项"
            textSize = 12.5f
            setTextColor(tokens.textSecondary)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(providerBatchAction("取消", UiActionRole.Secondary) {
            selectedSkillIds.clear()
            skillPageSnapshot?.let { renderSkillManagerPage(selected, target, it) }
        })
        addView(providerBatchAction("删除", UiActionRole.Danger) {
            confirmRemoveSelectedSkills(selected, target)
        })
    }

    private fun showSkillDocumentEditor(skill: AgentSkillSummary) {
        val target = skillPageTarget ?: return
        val targetAgentId = skillPageAgentId ?: return
        navigationJob?.cancel()
        skillDocumentEditorSnapshot = null
        skillDocumentEditorInput = null
        navigationScreen = AgentNavigationScreen.SkillDocumentEditor
        navigationHost.removeAllViews()
        navigationHost.addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(agentPageBackground)
            addView(buildAgentSubpageHeader(
                title = skill.displayName,
                backDescription = "返回 Skill",
                onBack = ::returnFromSkillDocumentEditor,
            ), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(64)))
            addView(settingsMessage("正在读取 SKILL.md…"), LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f,
            ))
        }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        val requestRevision = ++skillDocumentLoadRevision
        navigationJob = lifecycleOwner.lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                agentConfigurationApi.readSkillDocument(target, skill.id)
            }
            if (requestRevision != skillDocumentLoadRevision ||
                navigationScreen != AgentNavigationScreen.SkillDocumentEditor ||
                skillPageAgentId != targetAgentId
            ) return@launch
            when (result) {
                is AgentSkillDocumentReadResult.Ready -> renderSkillDocumentEditor(target, result.snapshot)
                is AgentSkillDocumentReadResult.Missing -> renderSkillDocumentError(skill, result.message)
                is AgentSkillDocumentReadResult.Unavailable -> renderSkillDocumentError(
                    skill,
                    result.discovery.warnings.firstOrNull() ?: "Skill 当前不可用",
                )
                is AgentSkillDocumentReadResult.Failed -> renderSkillDocumentError(skill, result.message)
            }
        }
    }

    private fun renderSkillDocumentEditor(
        target: AgentConfigurationTarget,
        snapshot: AgentSkillDocumentSnapshot,
    ) {
        skillDocumentEditorSnapshot = snapshot
        val status = TextView(context).apply {
            textSize = 12.5f
            setTextColor(tokens.textSecondary)
            visibility = View.GONE
            setPadding(ui.dp(2), ui.dp(9), ui.dp(2), 0)
        }
        lateinit var saveAction: TextView
        val editor = EditText(context).apply {
            setText(snapshot.content)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            gravity = Gravity.TOP or Gravity.START
            textSize = 14.5f
            setTextColor(tokens.textPrimary)
            setHintTextColor(tokens.textTertiary)
            hint = "SKILL.md"
            minLines = 16
            setPadding(ui.dp(16), ui.dp(15), ui.dp(16), ui.dp(15))
            background = ui.roundedBox(agentSettingsSurface, tokens.border, ui.dp(20).toFloat(), ui.dp(1))
            isEnabled = snapshot.writable
        }.also { skillDocumentEditorInput = it }
        saveAction = TextView(context).apply {
            text = "保存"
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(tokens.primaryStrong)
            isEnabled = snapshot.writable
            alpha = if (isEnabled) 1f else 0.38f
            isClickable = true
            isFocusable = true
            setOnClickListener { saveSkillDocument(target, this, status) }
        }
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ui.dp(16), ui.dp(8), ui.dp(16), ui.dp(28))
            addView(buildSettingsSection(
                title = "Skill 主文件",
                description = snapshot.location,
                rows = listOf(SettingsRow(
                    "编辑权限",
                    if (snapshot.writable) "可编辑" else "当前来源只读",
                )),
            ))
            addView(editor, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ))
            addView(status)
        }
        navigationHost.removeAllViews()
        navigationHost.addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(agentPageBackground)
            addView(buildAgentSubpageHeader(
                title = snapshot.displayName,
                backDescription = "返回 Skill",
                onBack = ::returnFromSkillDocumentEditor,
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

    private fun saveSkillDocument(
        target: AgentConfigurationTarget,
        saveAction: TextView,
        status: TextView,
    ) {
        val snapshot = skillDocumentEditorSnapshot ?: return
        val content = skillDocumentEditorInput?.text?.toString() ?: return
        val targetAgentId = skillPageAgentId ?: return
        saveAction.text = "保存中"
        saveAction.isEnabled = false
        saveAction.alpha = 0.42f
        status.text = "正在写入 Agent 原生 SKILL.md…"
        status.setTextColor(tokens.textSecondary)
        status.visibility = View.VISIBLE
        val requestRevision = ++skillDocumentLoadRevision
        navigationJob?.cancel()
        navigationJob = lifecycleOwner.lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                agentConfigurationApi.writeSkillDocument(
                    target,
                    AgentSkillDocumentWriteRequest(
                        agentId = targetAgentId,
                        skillId = snapshot.skillId,
                        expectedRevision = snapshot.revision,
                        content = content,
                    ),
                )
            }
            if (requestRevision != skillDocumentLoadRevision ||
                navigationScreen != AgentNavigationScreen.SkillDocumentEditor
            ) return@launch
            when (result) {
                is AgentSkillDocumentWriteResult.Applied -> {
                    skillDocumentEditorSnapshot = result.snapshot
                    saveAction.text = "保存"
                    saveAction.isEnabled = result.snapshot.writable
                    saveAction.alpha = if (saveAction.isEnabled) 1f else 0.38f
                    status.text = "已保存到 Agent 原生 SKILL.md"
                    status.setTextColor(tokens.textSecondary)
                }
                is AgentSkillDocumentWriteResult.Conflict -> {
                    saveAction.text = "重读"
                    saveAction.isEnabled = true
                    saveAction.alpha = 1f
                    saveAction.setOnClickListener {
                        skillPageSnapshot?.skills?.firstOrNull { it.id == snapshot.skillId }
                            ?.let(::showSkillDocumentEditor)
                    }
                    status.text = result.message
                    status.setTextColor(tokens.danger)
                }
                else -> {
                    saveAction.text = "保存"
                    saveAction.isEnabled = snapshot.writable
                    saveAction.alpha = if (saveAction.isEnabled) 1f else 0.38f
                    status.text = result.skillDocumentUserMessage()
                    status.setTextColor(tokens.danger)
                }
            }
        }
    }

    private fun AgentSkillDocumentWriteResult.skillDocumentUserMessage(): String = when (this) {
        is AgentSkillDocumentWriteResult.Applied -> "已保存"
        is AgentSkillDocumentWriteResult.Conflict -> message
        is AgentSkillDocumentWriteResult.Rejected -> problems.firstOrNull()?.message ?: "内容未通过校验"
        is AgentSkillDocumentWriteResult.Unavailable -> discovery.warnings.firstOrNull() ?: "Skill 当前不可用"
        is AgentSkillDocumentWriteResult.Failed -> if (restored) "$message，原文件已保留" else message
    }

    private fun renderSkillDocumentError(skill: AgentSkillSummary, message: String) {
        navigationHost.removeAllViews()
        navigationHost.addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(agentPageBackground)
            addView(buildAgentSubpageHeader(
                title = skill.displayName,
                backDescription = "返回 Skill",
                onBack = ::returnFromSkillDocumentEditor,
            ), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(64)))
            addView(settingsMessage(message), LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f,
            ))
        }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    private fun returnFromSkillDocumentEditor() {
        val targetAgentId = skillPageAgentId ?: run {
            returnToAgentSettings()
            return
        }
        val selected = settingsRegistrySnapshot?.entry(targetAgentId)
            ?: agentRegistry.snapshot().entry(targetAgentId)
        val target = skillPageTarget
        val snapshot = skillPageSnapshot
        if (selected == null || target == null || snapshot == null) {
            returnToAgentSettings()
            return
        }
        navigationJob?.cancel()
        skillDocumentLoadRevision++
        skillDocumentEditorSnapshot = null
        skillDocumentEditorInput = null
        navigationScreen = AgentNavigationScreen.SkillList
        navigationHost.removeAllViews()
        navigationHost.addView(buildSkillListPage(selected, target), FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        ))
        renderSkillSnapshot(snapshot)
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

    private fun confirmRemoveSelectedSkills(
        selected: AgentRegistryEntry,
        target: AgentConfigurationTarget,
    ) {
        val snapshot = skillPageSnapshot ?: return
        val removable = snapshot.skills.filter { skill ->
            skill.id in selectedSkillIds && AgentSkillOperation.Remove in skill.allowedOperations
        }
        if (removable.isEmpty()) return
        showAgentDialogCard(
            title = "删除 ${removable.size} 个 Skill？",
            message = "这些 Skill 会从当前 Agent 的可写目录中移除；只读或共享 Skill 不会受到影响。",
            actions = listOf(
                AgentDialogAction("取消", UiActionRole.Secondary) { dialog, _ -> dialog.dismiss() },
                AgentDialogAction("删除", UiActionRole.Danger) { dialog, _ ->
                    dialog.dismiss()
                    removeSelectedSkills(selected, target, snapshot, removable)
                },
            ),
        )
    }

    private fun removeSelectedSkills(
        selected: AgentRegistryEntry,
        target: AgentConfigurationTarget,
        snapshot: AgentLiveConfigSnapshot,
        skills: List<AgentSkillSummary>,
    ) {
        val targetAgentId = selected.registration.definition.agentId
        val requestRevision = ++settingsLoadRevision
        navigationJob?.cancel()
        navigationJob = lifecycleOwner.lifecycleScope.launch {
            val outcome = withContext(Dispatchers.IO) {
                var current = snapshot
                var removed = 0
                var failure: String? = null
                for (skill in skills) {
                    val mutation = agentConfigurationApi.apply(
                        target,
                        current.revision,
                        listOf(AgentConfigurationIntent.RemoveSkill(skill.id)),
                    )
                    val next = (mutation.current as? AgentConfigReadResult.Ready)?.snapshot
                        ?: (mutation.result as? AgentConfigApplyResult.Applied)?.snapshot
                    if (mutation.result is AgentConfigApplyResult.Applied && next != null) {
                        current = next
                        removed++
                    } else {
                        if (next != null) current = next
                        failure = mutation.result.userMessage("无法删除 ${skill.displayName}")
                        break
                    }
                }
                SkillBatchRemoveOutcome(current, removed, failure)
            }
            if (
                requestRevision != settingsLoadRevision ||
                navigationScreen != AgentNavigationScreen.SkillList ||
                skillPageAgentId != targetAgentId
            ) return@launch
            persistentConfigAgentId = targetAgentId
            persistentConfigResult = AgentConfigReadResult.Ready(outcome.snapshot)
            selectedSkillIds.clear()
            skillPageSnapshot = outcome.snapshot
            renderSkillManagerPage(selected, target, outcome.snapshot)
            val message = outcome.failure ?: "已删除 ${outcome.removedCount} 个 Skill"
            Toast.makeText(
                context,
                message,
                if (outcome.failure == null) Toast.LENGTH_SHORT else Toast.LENGTH_LONG,
            ).show()
        }
    }

    private fun showSkillImportPicker(
        selected: AgentRegistryEntry,
        target: AgentConfigurationTarget,
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
                importSkill(selected, target, selectedPath)
            },
        ).also(WorkspaceDirectoryPickerDialog::show)
    }

    private fun importSkill(
        selected: AgentRegistryEntry,
        target: AgentConfigurationTarget,
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
                        val mutation = agentConfigurationApi.apply(
                            target,
                            snapshot.revision,
                            listOf(AgentConfigurationIntent.InstallSkill(
                                    stage.skillId,
                                    stage.sourceReference,
                                )),
                            )
                        SkillImportOutcome(mutation.result, mutation.current, null)
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
        val target = providerPageTarget
        val snapshot = providerPageSnapshot
        if (selected == null || target == null || snapshot == null) {
            returnToAgentSettings()
            return
        }
        showProviderManager(selected, target, snapshot)
    }

    private fun buildAgentSubpageHeader(
        title: String,
        backDescription: String,
        onBack: () -> Unit,
        actionIcon: Int? = null,
        actionDescription: String? = null,
        onAction: (() -> Unit)? = null,
        trailingView: View? = null,
        trailingWidthDp: Int = 56,
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
            trailingView != null -> addView(
                trailingView,
                LinearLayout.LayoutParams(ui.dp(trailingWidthDp), ui.dp(48)),
            )
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

    private fun showProviderPresetPicker(
        target: AgentConfigurationTarget,
        onSelected: (AgentProviderPreset?) -> Unit,
    ) {
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
                addAll(agentConfigurationApi.providerPresets(target))
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
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(context).apply {
                text = model.displayName
                textSize = 15f
                typeface = Typeface.DEFAULT_BOLD
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                setTextColor(tokens.textPrimary)
            })
            if (model.displayName != model.id) {
                addView(TextView(context).apply {
                    text = model.id
                    textSize = 12.5f
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                    setTextColor(tokens.textSecondary)
                    setPadding(0, ui.dp(3), 0, 0)
                })
            }
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            setMargins(ui.dp(2), 0, ui.dp(4), 0)
        })
        addView(ImageView(context).apply {
            setImageResource(R.drawable.ic_chevron_right_light)
            imageTintList = ColorStateList.valueOf(tokens.textSecondary)
            setPadding(ui.dp(11), ui.dp(11), ui.dp(11), ui.dp(11))
        }, LinearLayout.LayoutParams(ui.dp(44), ui.dp(44)))
        contentDescription = "编辑模型 ${model.displayName}"
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
            textSize = 15f
            setTextColor(tokens.textPrimary)
            setHintTextColor(tokens.textTertiary)
            setSingleLine(true)
            setHorizontallyScrolling(true)
            inputType = AgentProviderCredentialInputPolicy.inputType
            transformationMethod = PasswordTransformationMethod.getInstance()
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
        val displayNameField = providerEditorField(
            content,
            label = "显示名称",
            hintText = "例如自定义模型",
            value = model?.displayName.orEmpty()
        )
        content.addView(TextView(context).apply {
            text = "只在 Kite 界面中显示，可以随时修改。"
            textSize = 12.5f
            setTextColor(tokens.textSecondary)
            setPadding(ui.dp(2), 0, ui.dp(2), ui.dp(14))
        })
        val idField = providerEditorField(
            content,
            label = "模型 ID",
            hintText = "例如 mimo-v2-pro",
            value = model?.id.orEmpty()
        )
        content.addView(TextView(context).apply {
            text = "发送给供应商或 Agent 的实际标识，请按官方值填写。"
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
            setTextColor(tokens.textPrimary)
            isClickable = true
            isFocusable = true
            setOnClickListener {
                val displayName = displayNameField.text?.toString()?.trim().orEmpty()
                val id = idField.text?.toString()?.trim().orEmpty()
                val error = AgentProviderEditorPolicy.validateModel(displayName, id)
                if (error != null) {
                    status.text = error
                    status.visibility = View.VISIBLE
                    return@setOnClickListener
                }
                onSave(AgentProviderModelSummary(id, displayName))
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
        target: AgentConfigurationTarget,
        snapshot: AgentLiveConfigSnapshot
    ): View = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(agentPageBackground)
        val targetAgentId = selected.registration.definition.agentId
        val library = modelLibraryStore.snapshot(targetAgentId)
        val providers = AgentModelLibraryPolicy.projectProviders(
            snapshot = snapshot,
            modelOption = providerManagerModelOption(targetAgentId),
            library = library,
            officialAccounts = selected.registration.officialAccounts,
        )
        val freeProviders = providers.filter { it.source == AgentModelSource.Free }
        val officialProviders = providers.filter { it.source == AgentModelSource.OfficialLogin }
        val canEditProviders = agentConfigurationApi.capabilities(target)?.supports(
            AgentPersistentConfigCapability.ProviderProfiles
        ) == true
        if (providerLibraryGroupId == AgentModelLibraryStore.FREE_GROUP_ID && freeProviders.isEmpty()) {
            providerLibraryGroupId = AgentModelLibraryStore.ALL_GROUP_ID
        }
        if (providerLibraryGroupId !in library.groups.map { it.id } + setOf(
                AgentModelLibraryStore.ALL_GROUP_ID,
                AgentModelLibraryStore.OFFICIAL_GROUP_ID,
                AgentModelLibraryStore.FREE_GROUP_ID
            )) {
            providerLibraryGroupId = AgentModelLibraryStore.ALL_GROUP_ID
        }
        val customProviders = providers.filter {
            it.source == AgentModelSource.UserConfigured && it.editableProvider != null
        }
        selectedProviderIds.retainAll(customProviders.mapTo(linkedSetOf()) { it.id })
        val visibleProviders = when (providerLibraryMode) {
            AgentProviderLibraryMode.Visibility -> providers
            AgentProviderLibraryMode.Delete -> customProviders
            AgentProviderLibraryMode.Browse -> when (providerLibraryGroupId) {
                AgentModelLibraryStore.ALL_GROUP_ID -> providers
                AgentModelLibraryStore.OFFICIAL_GROUP_ID -> officialProviders
                AgentModelLibraryStore.FREE_GROUP_ID -> freeProviders
                else -> providers.filter { it.libraryGroupId == providerLibraryGroupId }
            }
        }
        val headerAction = when (providerLibraryMode) {
            AgentProviderLibraryMode.Browse -> modelLibraryHeaderAction("显示管理") {
                providerLibraryMode = AgentProviderLibraryMode.Visibility
                selectedProviderIds.clear()
                showProviderManager(selected, target, providerPageSnapshot ?: snapshot)
            }
            AgentProviderLibraryMode.Visibility -> modelLibraryHeaderAction("完成") {
                providerLibraryMode = AgentProviderLibraryMode.Browse
                showProviderManager(selected, target, providerPageSnapshot ?: snapshot)
            }
            AgentProviderLibraryMode.Delete -> modelLibraryHeaderAction("取消") {
                providerLibraryMode = AgentProviderLibraryMode.Browse
                selectedProviderIds.clear()
                showProviderManager(selected, target, providerPageSnapshot ?: snapshot)
            }
        }
        addView(buildAgentSubpageHeader(
            title = when (providerLibraryMode) {
                AgentProviderLibraryMode.Browse -> "模型库"
                AgentProviderLibraryMode.Visibility -> "显示管理"
                AgentProviderLibraryMode.Delete -> "删除供应商"
            },
            backDescription = "返回 Agent 设置",
            onBack = ::returnFromProviderManager,
            trailingView = headerAction,
            trailingWidthDp = 78,
        ), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(64)))
        if (providerLibraryMode == AgentProviderLibraryMode.Delete) {
            addView(
                buildProviderDeleteSelectionRow(selected, target, snapshot, customProviders),
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
            )
        }
        val providerScroll = ScrollView(context).apply {
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
                    text = when (providerLibraryMode) {
                        AgentProviderLibraryMode.Browse ->
                            "按供应商折叠模型；右上角管理会话显示，长按自定义供应商可以删除。"
                        AgentProviderLibraryMode.Visibility ->
                            "勾选会出现在会话模型选择器；展开供应商可以精细到单个模型。"
                        AgentProviderLibraryMode.Delete ->
                            "只删除用户自定义供应商及其模型；免费和官方来源不会进入删除选择。"
                    }
                    textSize = 13f
                    setTextColor(tokens.textSecondary)
                    setPadding(0, ui.dp(5), 0, ui.dp(13))
                })
                if (providerLibraryMode == AgentProviderLibraryMode.Browse && targetAgentId == "codex") {
                    addView(buildCodexAuthImportCard(), LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ).apply { bottomMargin = ui.dp(12) })
                }
                if (providerLibraryMode == AgentProviderLibraryMode.Browse) {
                    addView(buildProviderGroupStrip(
                        selected,
                        target,
                        snapshot,
                        library,
                        hasOfficialProviders = officialProviders.isNotEmpty(),
                        hasFreeProviders = freeProviders.isNotEmpty(),
                    ))
                }
                if (visibleProviders.isEmpty()) {
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
                            text = when {
                                providers.isEmpty() -> "还没有模型"
                                providerLibraryMode == AgentProviderLibraryMode.Delete -> "没有可删除的自定义供应商"
                                else -> "这个分组还是空的"
                            }
                            textSize = 17f
                            typeface = Typeface.DEFAULT_BOLD
                            setTextColor(tokens.textPrimary)
                        })
                        addView(TextView(context).apply {
                            text = if (providers.isEmpty()) "可以从预置开始，也可以完整自定义。" else "可在供应商编辑页选择分组。"
                            textSize = 13f
                            gravity = Gravity.CENTER
                            setTextColor(tokens.textSecondary)
                            setPadding(0, ui.dp(7), 0, ui.dp(18))
                        })
                    }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
                } else {
                    visibleProviders.forEach { provider ->
                        addView(buildProviderLibraryCard(
                            selected = selected,
                            target = target,
                            snapshot = snapshot,
                            projection = provider,
                            library = library,
                        ), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                            setMargins(0, 0, 0, ui.dp(12))
                        })
                    }
                }
            }, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        addView(SwipeRefreshLayout(context).apply {
            setColorSchemeColors(tokens.textPrimary)
            setProgressBackgroundColorSchemeColor(agentSettingsSurface)
            addView(providerScroll, ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ))
            setOnRefreshListener { refreshProviderCatalog(selected, target, this) }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        when {
            providerLibraryMode == AgentProviderLibraryMode.Delete -> addView(
                buildProviderDeleteBar(selected, target, snapshot, customProviders),
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
            )
            providerLibraryMode == AgentProviderLibraryMode.Browse && canEditProviders -> addView(
                buildModelLibraryAddBar {
                    showProviderEditor(selected, target, snapshot, existing = null, preset = null)
                },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
            )
        }
    }

    private fun providerManagerModelOption(targetAgentId: String): AgentConfigOption.Select? {
        val live = currentSnapshot
            ?.takeIf { targetAgentId == agentId }
            ?.configuration
            ?.filterIsInstance<AgentConfigOption.Select>()
            ?.firstOrNull { it.category == AgentConfigCategory.Model }
        return live ?: draftCapabilityCacheStore.catalog(targetAgentId)
            ?.configuration
            ?.filterIsInstance<AgentConfigOption.Select>()
            ?.firstOrNull { it.category == AgentConfigCategory.Model }
    }

    private fun buildProviderGroupStrip(
        selected: AgentRegistryEntry,
        target: AgentConfigurationTarget,
        snapshot: AgentLiveConfigSnapshot,
        library: com.kite.app.agent.store.AgentModelLibrarySnapshot,
        hasOfficialProviders: Boolean,
        hasFreeProviders: Boolean
    ): View = HorizontalScrollView(context).apply {
        isHorizontalScrollBarEnabled = false
        overScrollMode = View.OVER_SCROLL_NEVER
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            fun addGroup(id: String, name: String, removable: Boolean = false) {
                addView(TextView(context).apply {
                    text = name
                    textSize = 13.5f
                    typeface = if (providerLibraryGroupId == id) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                    gravity = Gravity.CENTER
                    setTextColor(tokens.textPrimary)
                    setPadding(ui.dp(16), 0, ui.dp(16), 0)
                    background = ui.roundedBox(
                        if (providerLibraryGroupId == id) agentSettingsSurface else agentSurface,
                        if (providerLibraryGroupId == id) android.graphics.Color.TRANSPARENT else tokens.border,
                        ui.dp(18).toFloat(),
                        if (providerLibraryGroupId == id) 0 else ui.dp(1)
                    )
                    isClickable = true
                    isFocusable = true
                    setOnClickListener {
                        providerLibraryGroupId = id
                        selectedProviderIds.clear()
                        showProviderManager(selected, target, providerPageSnapshot ?: snapshot)
                    }
                    if (removable) {
                        setOnLongClickListener {
                            confirmDeleteModelGroup(selected, target, snapshot, id, name)
                            true
                        }
                    }
                }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ui.dp(38)).apply {
                    marginEnd = ui.dp(8)
                })
            }
            addGroup(AgentModelLibraryStore.ALL_GROUP_ID, "全部")
            if (hasOfficialProviders) addGroup(AgentModelLibraryStore.OFFICIAL_GROUP_ID, "官方")
            if (hasFreeProviders) addGroup(AgentModelLibraryStore.FREE_GROUP_ID, "免费")
            library.groups.forEach { group -> addGroup(group.id, group.name, removable = true) }
            addView(iconButton(context, R.drawable.ic_add_light, "新建分组") {
                showCreateModelGroupDialog(selected, target, snapshot)
            }.apply {
                background = ui.roundedBox(agentSurface, tokens.border, ui.dp(18).toFloat(), ui.dp(1))
                imageTintList = ColorStateList.valueOf(tokens.textSecondary)
            }, LinearLayout.LayoutParams(ui.dp(38), ui.dp(38)))
        }, ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }.also { strip ->
        strip.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(0, 0, 0, ui.dp(16)) }
    }

    private fun buildProviderLibraryCard(
        selected: AgentRegistryEntry,
        target: AgentConfigurationTarget,
        snapshot: AgentLiveConfigSnapshot,
        projection: AgentModelProviderProjection,
        library: com.kite.app.agent.store.AgentModelLibrarySnapshot,
    ): View {
        val expanded = projection.id in expandedProviderIds
        val selectedForBatch = providerLibraryMode == AgentProviderLibraryMode.Delete &&
            projection.id in selectedProviderIds
        val isDefault = projection.selectedModelValue != null
        val visibilityState = providerVisibilityState(projection, library)
        val officialAccount = projection.officialAccount
        val officialState = officialAccount?.let { account ->
            officialAccountManager.state(selected.registration.definition.agentId, account.id)
        }
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = ui.roundedBox(
                when {
                    selectedForBatch -> selectionPalette.selectedRow
                    providerLibraryMode == AgentProviderLibraryMode.Browse && isDefault -> agentSettingsSurface
                    else -> agentSurface
                },
                if (selectedForBatch || (providerLibraryMode == AgentProviderLibraryMode.Browse && isDefault)) {
                    android.graphics.Color.TRANSPARENT
                } else tokens.border,
                ui.dp(22).toFloat(),
                if (selectedForBatch || (providerLibraryMode == AgentProviderLibraryMode.Browse && isDefault)) 0
                else ui.dp(1)
            )
            val header = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = ui.dp(76)
                setPadding(ui.dp(13), ui.dp(10), ui.dp(7), ui.dp(10))
                if (providerLibraryMode != AgentProviderLibraryMode.Browse) {
                    val selectionState = when (providerLibraryMode) {
                        AgentProviderLibraryMode.Visibility -> visibilityState
                        AgentProviderLibraryMode.Delete -> if (selectedForBatch) {
                            AgentArchivedProjectSelectionState.Checked
                        } else AgentArchivedProjectSelectionState.Unchecked
                        AgentProviderLibraryMode.Browse -> AgentArchivedProjectSelectionState.Unchecked
                    }
                    addView(modelLibrarySelectionIndicator(
                        state = selectionState,
                        description = when (providerLibraryMode) {
                            AgentProviderLibraryMode.Visibility ->
                                if (visibilityState == AgentArchivedProjectSelectionState.Checked) {
                                    "隐藏 ${projection.name}"
                                } else {
                                    "显示 ${projection.name}"
                                }
                            AgentProviderLibraryMode.Delete ->
                                if (selectedForBatch) "取消选择 ${projection.name}" else "选择删除 ${projection.name}"
                            AgentProviderLibraryMode.Browse -> projection.name
                        },
                        onClick = {
                            when (providerLibraryMode) {
                                AgentProviderLibraryMode.Visibility -> {
                                    val modelIds = projection.models.map(AgentConfigChoice::value)
                                    if (modelIds.isEmpty()) {
                                        modelLibraryStore.setProviderVisible(
                                            selected.registration.definition.agentId,
                                            projection.id,
                                            !projection.visibleInConversation,
                                        )
                                    } else {
                                        modelLibraryStore.setProviderModelSelection(
                                            selected.registration.definition.agentId,
                                            projection.id,
                                            modelIds,
                                            if (visibilityState == AgentArchivedProjectSelectionState.Checked) {
                                                emptySet()
                                            } else {
                                                modelIds
                                            },
                                        )
                                    }
                                    showProviderManager(selected, target, providerPageSnapshot ?: snapshot)
                                }
                                AgentProviderLibraryMode.Delete ->
                                    toggleProviderDeleteSelection(selected, target, snapshot, projection.id)
                                AgentProviderLibraryMode.Browse -> Unit
                            }
                        },
                    ), LinearLayout.LayoutParams(
                        ui.dp(AgentSelectionVisualPolicy.TOUCH_TARGET_DP),
                        ui.dp(AgentSelectionVisualPolicy.TOUCH_TARGET_DP),
                    ))
                }
                addView(ImageView(context).apply {
                    setImageResource(R.drawable.ic_bridge)
                    imageTintList = ColorStateList.valueOf(tokens.textSecondary)
                    setPadding(ui.dp(8), ui.dp(8), ui.dp(8), ui.dp(8))
                }, LinearLayout.LayoutParams(ui.dp(40), ui.dp(40)))
                addView(LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(TextView(context).apply {
                        text = projection.name
                        textSize = 16f
                        typeface = Typeface.DEFAULT_BOLD
                        maxLines = 1
                        ellipsize = TextUtils.TruncateAt.END
                        setTextColor(tokens.textPrimary)
                    })
                    addView(TextView(context).apply {
                        text = buildString {
                            if (projection.source == AgentModelSource.Free) append("免费 · ")
                            if (projection.source == AgentModelSource.OfficialLogin) append("官方登录 · ")
                            if (projection.source == AgentModelSource.UserConfigured) append("用户自定义 · ")
                            if (isDefault) append("当前默认 · ")
                            if (officialState != null) {
                                append(officialState.status.officialAccountLabel())
                                if (projection.models.isNotEmpty()) append(" · ")
                            }
                            if (projection.models.isNotEmpty() || officialState == null) {
                                append("${projection.models.size} 个模型")
                            }
                            if (!projection.visibleInConversation && !isDefault) append(" · 未加入会话选择")
                        }
                        textSize = 11.5f
                        maxLines = 1
                        ellipsize = TextUtils.TruncateAt.END
                        setTextColor(tokens.textSecondary)
                        setPadding(0, ui.dp(3), 0, 0)
                    })
                }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = ui.dp(4)
                })
                if (projection.models.isNotEmpty() && providerLibraryMode == AgentProviderLibraryMode.Browse) {
                    addView(iconButton(context, R.drawable.ic_more_vert_light, "编辑 ${projection.name}") {
                        if (projection.editableProvider != null) {
                            showProviderEditor(
                                selected,
                                target,
                                providerPageSnapshot ?: snapshot,
                                projection.editableProvider,
                                preset = null
                            )
                        } else {
                            showSystemModelDisplayNameEditor(
                                selected,
                                target,
                                providerPageSnapshot ?: snapshot,
                                projection,
                            )
                        }
                    }.apply { imageTintList = ColorStateList.valueOf(tokens.textSecondary) }, LinearLayout.LayoutParams(
                        ui.dp(42),
                        ui.dp(42)
                    ))
                }
                if (
                    officialAccount != null &&
                    officialState != null &&
                    providerLibraryMode == AgentProviderLibraryMode.Browse
                ) {
                    addView(buildOfficialAccountAction(
                        agentId = selected.registration.definition.agentId,
                        account = officialAccount,
                        status = officialState.status,
                    ), LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ui.dp(36)).apply {
                        marginStart = ui.dp(6)
                        marginEnd = ui.dp(2)
                    })
                }
                if (
                    projection.models.isNotEmpty() &&
                    providerLibraryMode != AgentProviderLibraryMode.Delete
                ) {
                    addView(ImageView(context).apply {
                        setImageResource(R.drawable.ic_chevron_right_light)
                        imageTintList = ColorStateList.valueOf(tokens.textSecondary)
                        rotation = if (expanded) 90f else 0f
                        setPadding(ui.dp(11), ui.dp(11), ui.dp(11), ui.dp(11))
                    }, LinearLayout.LayoutParams(ui.dp(42), ui.dp(42)))
                }
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    when (providerLibraryMode) {
                        AgentProviderLibraryMode.Delete ->
                            toggleProviderDeleteSelection(selected, target, snapshot, projection.id)
                        AgentProviderLibraryMode.Browse,
                        AgentProviderLibraryMode.Visibility -> {
                            if (!expandedProviderIds.add(projection.id)) expandedProviderIds.remove(projection.id)
                            showProviderManager(selected, target, providerPageSnapshot ?: snapshot)
                        }
                    }
                }
                setOnLongClickListener {
                    if (
                        providerLibraryMode == AgentProviderLibraryMode.Browse &&
                        projection.source == AgentModelSource.UserConfigured &&
                        projection.editableProvider != null
                    ) {
                        providerLibraryMode = AgentProviderLibraryMode.Delete
                        selectedProviderIds.clear()
                        selectedProviderIds += projection.id
                        showProviderManager(selected, target, providerPageSnapshot ?: snapshot)
                        true
                    } else {
                        false
                    }
                }
            }
            addView(header)
            if (
                expanded &&
                projection.models.isNotEmpty() &&
                providerLibraryMode != AgentProviderLibraryMode.Delete
            ) {
                addView(View(context).apply { setBackgroundColor(tokens.border) }, LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ui.dp(1)
                ).apply { setMargins(ui.dp(16), 0, ui.dp(16), 0) })
                projection.models.forEach { model ->
                    val row = when (providerLibraryMode) {
                        AgentProviderLibraryMode.Browse -> sessionChoiceRow(
                            title = model.name,
                            description = model.description ?: model.value.takeIf { it != model.name },
                            selected = model.value == projection.selectedModelValue,
                            contentDescription = "设为默认模型 ${model.name}",
                            useSelectionDot = true,
                            onClick = {
                                selectPersistentModel(selected, target, snapshot, projection, model)
                            }
                        )
                        AgentProviderLibraryMode.Visibility -> modelVisibilityRow(
                            provider = projection,
                            model = model,
                            visible = projection.visibleInConversation &&
                                library.isModelVisible(projection.id, model.value),
                            onClick = {
                                val modelIds = projection.models.map(AgentConfigChoice::value)
                                val visibleModelIds = if (projection.visibleInConversation) {
                                    modelIds.filterTo(mutableSetOf()) {
                                        library.isModelVisible(projection.id, it)
                                    }
                                } else {
                                    mutableSetOf()
                                }
                                if (!visibleModelIds.add(model.value)) {
                                    visibleModelIds.remove(model.value)
                                }
                                modelLibraryStore.setProviderModelSelection(
                                    selected.registration.definition.agentId,
                                    projection.id,
                                    modelIds,
                                    visibleModelIds,
                                )
                                showProviderManager(selected, target, providerPageSnapshot ?: snapshot)
                            },
                        )
                        AgentProviderLibraryMode.Delete -> error("删除模式不展示模型行")
                    }
                    addView(row, LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply { setMargins(ui.dp(8), 0, ui.dp(8), ui.dp(4)) })
                }
            }
        }
        return card
    }

    private fun showUnavailableArchivedSessionActions(
        selected: AgentRegistryEntry,
        archivedSession: AgentArchivedSessionMetadata,
        onChanged: () -> Unit,
    ) {
        val providerId = selected.registration.launch.providerId
        val runtime = AgentRuntimeRegistry.session(instanceId)
        val sourceDeleted = archivedSession.sourceState == AgentArchivedSessionSourceState.Deleted
        val canDeleteNatively = !sourceDeleted && AgentSurfaceNavigationPolicy.canDeleteUnavailableSessionNatively(
            targetProviderId = providerId,
            runtimeProviderId = runtime?.providerId,
            deleteSupported = runtime?.capabilities?.sessions?.delete == true,
            currentSessionId = runtime?.sessionId,
            targetSessionId = archivedSession.sessionId,
        )
        showAgentDialogCard(
            title = if (sourceDeleted) "源会话已删除" else "尚未确认",
            message = if (sourceDeleted) {
                "Agent 中已找不到这条会话。"
            } else {
                "打开当前 Agent 并刷新后再试。"
            },
            actions = listOf(
                AgentDialogAction(
                    label = "恢复",
                    role = UiActionRole.Primary,
                    filledPrimary = true,
                ) { dialog, _ ->
                    dialog.dismiss()
                    Toast.makeText(
                        context,
                        if (sourceDeleted) "源会话已删除，无法恢复" else "尚未确认源会话状态",
                        Toast.LENGTH_SHORT,
                    ).show()
                },
                AgentDialogAction("删除", UiActionRole.Danger) { dialog, _ ->
                    dialog.dismiss()
                    showUnavailableArchivedDeleteConfirmation(
                        selected = selected,
                        sessionId = archivedSession.sessionId,
                        canDeleteNatively = canDeleteNatively,
                        onChanged = onChanged,
                    )
                },
            ),
        )
    }

    private fun showUnavailableArchivedDeleteConfirmation(
        selected: AgentRegistryEntry,
        sessionId: String,
        canDeleteNatively: Boolean,
        onChanged: () -> Unit,
    ) {
        val providerId = selected.registration.launch.providerId
        showAgentDialogCard(
            title = if (canDeleteNatively) "删除会话？" else "删除记录？",
            message = if (canDeleteNatively) {
                "这会从 ${selected.registration.definition.displayName} 永久删除，无法恢复。"
            } else {
                "这只会删除 Kite 保存的归档记录。"
            },
            actions = listOf(
                AgentDialogAction("取消", UiActionRole.Secondary) { dialog, _ -> dialog.dismiss() },
                AgentDialogAction(
                    label = "删除",
                    role = UiActionRole.Danger,
                ) { dialog, button ->
                    button.isEnabled = false
                    button.alpha = 0.48f
                    button.text = "删除中…"
                    if (!canDeleteNatively) {
                        sessionMetadataStore.remove(providerId, sessionId)
                        dialog.dismiss()
                        onChanged()
                        Toast.makeText(context, "已删除 Kite 归档记录", Toast.LENGTH_SHORT).show()
                    } else {
                        lifecycleOwner.lifecycleScope.launch {
                            when (val result = AgentRuntimeRegistry.deleteSession(instanceId, generation, sessionId)) {
                                is AgentOperationResult.Success -> {
                                    sessionMetadataStore.remove(providerId, sessionId)
                                    dialog.dismiss()
                                    onChanged()
                                    Toast.makeText(context, "已永久删除 Agent 会话", Toast.LENGTH_SHORT).show()
                                }
                                is AgentOperationResult.Unsupported -> {
                                    dialog.dismiss()
                                    Toast.makeText(context, "当前 Agent 未提供永久删除", Toast.LENGTH_LONG).show()
                                }
                                is AgentOperationResult.Failure -> {
                                    dialog.dismiss()
                                    Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    }
                },
            ),
        )
    }

    private fun showSystemModelDisplayNameEditor(
        selected: AgentRegistryEntry,
        target: AgentConfigurationTarget,
        snapshot: AgentLiveConfigSnapshot,
        projection: AgentModelProviderProjection,
    ) {
        providerPageAgentId = selected.registration.definition.agentId
        providerPageTarget = target
        providerPageSnapshot = snapshot
        navigationScreen = AgentNavigationScreen.ProviderEditor
        navigationHost.removeAllViews()
        navigationHost.addView(
            buildSystemModelDisplayNameEditorPage(selected, projection),
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
        navigationHost.visibility = View.VISIBLE
    }

    private fun buildSystemModelDisplayNameEditorPage(
        selected: AgentRegistryEntry,
        projection: AgentModelProviderProjection,
    ): View {
        val fields = linkedMapOf<String, EditText>()
        val status = TextView(context).apply {
            textSize = 12.5f
            setTextColor(tokens.danger)
            visibility = View.GONE
            setPadding(ui.dp(2), 0, ui.dp(2), ui.dp(10))
        }
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ui.dp(16), ui.dp(10), ui.dp(16), ui.dp(30))
            addView(status)
            addView(sectionTitle(
                projection.name,
                when (projection.source) {
                    AgentModelSource.OfficialLogin ->
                        "官方模型目录与登录状态由系统管理；这里只修改 Kite 显示名称。"
                    AgentModelSource.Free ->
                        "免费模型目录由 Agent 提供；这里只修改 Kite 显示名称。"
                    AgentModelSource.UserConfigured ->
                        "显示名称只影响 Kite 界面。"
                }
            ))
            projection.models.forEach { model ->
                addView(LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(ui.dp(14), ui.dp(14), ui.dp(14), ui.dp(14))
                    background = ui.roundedBox(
                        agentSettingsSurface,
                        android.graphics.Color.TRANSPARENT,
                        ui.dp(20).toFloat()
                    )
                    val field = providerEditorField(
                        this,
                        label = "显示名称",
                        hintText = model.name,
                        value = model.name,
                    )
                    fields[model.value] = field
                    addView(TextView(context).apply {
                        text = "模型 ID"
                        textSize = 13f
                        typeface = Typeface.DEFAULT_BOLD
                        setTextColor(tokens.textPrimary)
                        setPadding(ui.dp(2), 0, 0, ui.dp(7))
                    })
                    addView(TextView(context).apply {
                        text = model.value
                        textSize = 13.5f
                        typeface = Typeface.MONOSPACE
                        setTextColor(tokens.textSecondary)
                        setTextIsSelectable(true)
                        setPadding(ui.dp(15), ui.dp(14), ui.dp(15), ui.dp(14))
                        background = ui.roundedBox(agentSurface, tokens.border, ui.dp(18).toFloat(), ui.dp(1))
                        contentDescription = "只读模型 ID，${model.value}"
                    }, LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ))
                    addView(TextView(context).apply {
                        text = "真正传给 Agent 的稳定标识，只读且不会随显示名称改变。"
                        textSize = 12.5f
                        setTextColor(tokens.textSecondary)
                        setPadding(ui.dp(2), ui.dp(7), ui.dp(2), 0)
                    })
                }, LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply { setMargins(0, 0, 0, ui.dp(12)) })
            }
        }
        val save = TextView(context).apply {
            text = "保存"
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(tokens.textPrimary)
            contentDescription = "保存模型显示名称"
            isClickable = true
            isFocusable = true
            setOnClickListener {
                val names = projection.models.map { model ->
                    val displayName = fields.getValue(model.value).text?.toString()?.trim().orEmpty()
                    val error = AgentProviderEditorPolicy.validateDisplayName(displayName)
                    if (error != null) {
                        status.text = "${model.value}：$error"
                        status.visibility = View.VISIBLE
                        return@setOnClickListener
                    }
                    AgentModelDisplayName(model.value, displayName)
                }
                modelLibraryStore.updatePublishedModelDisplayNames(
                    selected.registration.definition.agentId,
                    projection.id,
                    names,
                )
                showCurrentProviderList()
                Toast.makeText(context, "显示名称已保存", Toast.LENGTH_SHORT).show()
            }
        }
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(agentPageBackground)
            addView(buildAgentSubpageHeader(
                title = "编辑显示名称",
                backDescription = "返回模型库",
                onBack = ::showCurrentProviderList,
                trailingView = save,
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

    private fun buildOfficialAccountAction(
        agentId: String,
        account: com.kite.app.agent.registration.AgentOfficialAccountSpec,
        status: AgentOfficialAccountStatus,
    ): TextView = TextView(context).apply {
        text = when (status) {
            AgentOfficialAccountStatus.Checking -> "检查中"
            AgentOfficialAccountStatus.SigningIn -> "取消"
            AgentOfficialAccountStatus.CancellingLogin -> "取消中"
            AgentOfficialAccountStatus.SigningOut -> "退出中"
            AgentOfficialAccountStatus.LoggedIn -> if (account.logout != null) "退出" else "已登录"
            AgentOfficialAccountStatus.Unknown,
            AgentOfficialAccountStatus.Unverified,
            AgentOfficialAccountStatus.LoggedOut,
            AgentOfficialAccountStatus.Failed -> "登录"
        }
        textSize = 14f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        gravity = Gravity.CENTER
        setTextColor(tokens.textPrimary)
        setPadding(ui.dp(13), 0, ui.dp(13), 0)
        background = ui.roundedBox(
            agentSurface,
            tokens.border,
            ui.dp(18).toFloat(),
            ui.dp(1),
        )
        val pending = status in setOf(
            AgentOfficialAccountStatus.Checking,
            AgentOfficialAccountStatus.CancellingLogin,
            AgentOfficialAccountStatus.SigningOut,
        )
        isEnabled = !pending && (status != AgentOfficialAccountStatus.LoggedIn || account.logout != null)
        alpha = if (isEnabled) 1f else 0.55f
        isClickable = isEnabled
        isFocusable = isEnabled
        setOnClickListener {
            when (status) {
                AgentOfficialAccountStatus.SigningIn -> officialAccountManager.cancelLogin(agentId, account.id)
                AgentOfficialAccountStatus.LoggedIn -> officialAccountManager.logout(agentId, account.id)
                else -> officialAccountManager.login(agentId, account.id)
            }
        }
    }

    private fun buildCodexAuthImportCard(): View = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(ui.dp(16), ui.dp(12), ui.dp(10), ui.dp(12))
        background = ui.roundedBox(
            agentSettingsSurface,
            tokens.border,
            ui.dp(18).toFloat(),
            ui.dp(1),
        )
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(context).apply {
                text = context.getString(R.string.agent_codex_auth_import_title)
                textSize = 15f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(tokens.textPrimary)
            })
            addView(TextView(context).apply {
                text = context.getString(R.string.agent_codex_auth_import_summary)
                textSize = 12.5f
                setTextColor(tokens.textSecondary)
                setPadding(0, ui.dp(4), ui.dp(10), 0)
            })
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(
                codexAuthCardAction(
                    context.getString(R.string.agent_codex_auth_import_action),
                    onPickCodexAuthJson,
                ),
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(40)),
            )
            addView(
                codexAuthCardAction(
                    context.getString(R.string.agent_codex_auth_manage_action),
                    onManageCodexAccounts,
                ),
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(40)).apply {
                    topMargin = ui.dp(8)
                },
            )
        }, LinearLayout.LayoutParams(ui.dp(126), ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    private fun codexAuthCardAction(label: String, onClick: () -> Unit): TextView =
        TextView(context).apply {
            text = label
            textSize = 13.5f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(tokens.textPrimary)
            setPadding(ui.dp(10), 0, ui.dp(10), 0)
            background = ui.roundedBox(agentSurface, tokens.border, ui.dp(16).toFloat(), ui.dp(1))
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
        }

    fun refreshCodexOfficialAccountStatus() {
        officialAccountManager.refresh("codex", "chatgpt")
    }

    private fun AgentOfficialAccountStatus.officialAccountLabel(): String = when (this) {
        AgentOfficialAccountStatus.Unknown -> "待检查"
        AgentOfficialAccountStatus.Unverified -> "未检测登录状态"
        AgentOfficialAccountStatus.Checking -> "正在检查"
        AgentOfficialAccountStatus.LoggedOut -> "未登录"
        AgentOfficialAccountStatus.LoggedIn -> "已登录"
        AgentOfficialAccountStatus.SigningIn -> "等待浏览器确认"
        AgentOfficialAccountStatus.CancellingLogin -> "正在结束登录"
        AgentOfficialAccountStatus.SigningOut -> "正在退出"
        AgentOfficialAccountStatus.Failed -> "状态未知"
    }

    private fun modelLibraryHeaderAction(label: String, onClick: () -> Unit): TextView =
        TextView(context).apply {
            text = label
            textSize = 13.5f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(tokens.textPrimary)
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
        }

    private fun modelLibrarySelectionIndicator(
        state: AgentArchivedProjectSelectionState,
        description: String,
        onClick: () -> Unit,
    ): ImageView = ImageView(context).apply {
        renderArchivedSelectionIndicator(this, ui, selectionPalette, state)
        contentDescription = description
        isClickable = true
        isFocusable = true
        setOnClickListener { onClick() }
    }

    private fun providerVisibilityState(
        provider: AgentModelProviderProjection,
        library: AgentModelLibrarySnapshot,
    ): AgentArchivedProjectSelectionState {
        if (!provider.visibleInConversation) return AgentArchivedProjectSelectionState.Unchecked
        if (provider.models.isEmpty()) return AgentArchivedProjectSelectionState.Checked
        return if (provider.models.any { library.isModelVisible(provider.id, it.value) }) {
            AgentArchivedProjectSelectionState.Checked
        } else {
            AgentArchivedProjectSelectionState.Unchecked
        }
    }

    private fun modelVisibilityRow(
        provider: AgentModelProviderProjection,
        model: AgentConfigChoice,
        visible: Boolean,
        onClick: () -> Unit,
    ): View = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = ui.dp(56)
        setPadding(ui.dp(4), ui.dp(4), ui.dp(10), ui.dp(4))
        addView(modelLibrarySelectionIndicator(
            state = if (visible) AgentArchivedProjectSelectionState.Checked
            else AgentArchivedProjectSelectionState.Unchecked,
            description = if (visible) "隐藏 ${model.name}" else "显示 ${model.name}",
            onClick = onClick,
        ), LinearLayout.LayoutParams(
            ui.dp(AgentSelectionVisualPolicy.TOUCH_TARGET_DP),
            ui.dp(AgentSelectionVisualPolicy.TOUCH_TARGET_DP),
        ))
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(context).apply {
                text = model.name
                textSize = 14f
                typeface = Typeface.DEFAULT_BOLD
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                setTextColor(tokens.textPrimary)
            })
            addView(TextView(context).apply {
                text = listOfNotNull(
                    model.description ?: model.value.takeIf { it != model.name },
                    "当前默认".takeIf { model.value == provider.selectedModelValue },
                ).joinToString(" · ")
                textSize = 11.5f
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                setTextColor(tokens.textSecondary)
                visibility = if (text.isNullOrBlank()) View.GONE else View.VISIBLE
                setPadding(0, ui.dp(2), 0, 0)
            })
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        isClickable = true
        isFocusable = true
        contentDescription = if (visible) "隐藏模型 ${model.name}" else "显示模型 ${model.name}"
        setOnClickListener { onClick() }
    }

    private fun toggleProviderDeleteSelection(
        selected: AgentRegistryEntry,
        target: AgentConfigurationTarget,
        snapshot: AgentLiveConfigSnapshot,
        providerId: String
    ) {
        if (!selectedProviderIds.add(providerId)) selectedProviderIds.remove(providerId)
        showProviderManager(selected, target, providerPageSnapshot ?: snapshot)
    }

    private fun buildProviderDeleteSelectionRow(
        selected: AgentRegistryEntry,
        target: AgentConfigurationTarget,
        snapshot: AgentLiveConfigSnapshot,
        providers: List<AgentModelProviderProjection>
    ): View = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(ui.dp(18), 0, ui.dp(18), ui.dp(6))
        val allSelected = providers.isNotEmpty() && selectedProviderIds.containsAll(providers.map { it.id })
        addView(modelLibrarySelectionIndicator(
            state = if (allSelected) AgentArchivedProjectSelectionState.Checked
            else AgentArchivedProjectSelectionState.Unchecked,
            description = if (allSelected) "取消全选自定义供应商" else "全选自定义供应商",
            onClick = {
                selectedProviderIds.clear()
                if (!allSelected) selectedProviderIds.addAll(providers.map { it.id })
                showProviderManager(selected, target, providerPageSnapshot ?: snapshot)
            },
        ), LinearLayout.LayoutParams(
            ui.dp(AgentSelectionVisualPolicy.TOUCH_TARGET_DP),
            ui.dp(AgentSelectionVisualPolicy.TOUCH_TARGET_DP),
        ))
        addView(TextView(context).apply {
            text = "全选自定义供应商"
            textSize = 14f
            setTextColor(tokens.textPrimary)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(TextView(context).apply {
            text = "已选择 ${selectedProviderIds.size} 项"
            textSize = 12.5f
            gravity = Gravity.END
            setTextColor(tokens.textSecondary)
        })
    }

    private fun buildProviderDeleteBar(
        selected: AgentRegistryEntry,
        target: AgentConfigurationTarget,
        snapshot: AgentLiveConfigSnapshot,
        providers: List<AgentModelProviderProjection>,
    ): View = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(ui.dp(18), ui.dp(8), ui.dp(18), ui.dp(14))
        setBackgroundColor(agentSurface)
        val removable = providers.filter { it.id in selectedProviderIds }
        addView(archiveBatchActionButton("删除所选供应商") {
            if (removable.isNotEmpty()) confirmRemoveSelectedProviders(selected, target, snapshot, removable)
        }.apply {
            isEnabled = removable.isNotEmpty()
            renderArchiveBatchAction(this, isEnabled, danger = true)
        }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ui.dp(AgentSelectionVisualPolicy.ACTION_HEIGHT_DP),
        ))
    }

    private fun buildModelLibraryAddBar(onClick: () -> Unit): View = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(ui.dp(18), ui.dp(8), ui.dp(18), ui.dp(14))
        setBackgroundColor(agentSurface)
        addView(archiveBatchActionButton("添加模型", onClick).apply {
            renderArchiveBatchAction(this, enabled = true, danger = false)
        }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ui.dp(AgentSelectionVisualPolicy.ACTION_HEIGHT_DP),
        ))
    }

    private fun providerBatchAction(label: String, role: UiActionRole, onClick: () -> Unit): TextView =
        TextView(context).apply {
            text = label
            textSize = 12.5f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(when (role) {
                UiActionRole.Primary -> tokens.textPrimary
                UiActionRole.Secondary -> tokens.textSecondary
                UiActionRole.Danger -> tokens.danger
            })
            setPadding(ui.dp(13), 0, ui.dp(13), 0)
            background = ui.roundedBox(
                when (role) {
                    UiActionRole.Primary -> agentSettingsSurface
                    UiActionRole.Secondary -> android.graphics.Color.TRANSPARENT
                    UiActionRole.Danger -> tokens.dangerSoft
                },
                android.graphics.Color.TRANSPARENT,
                ui.dp(17).toFloat()
            )
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
        }.also { action ->
            action.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ui.dp(36)).apply {
                marginStart = ui.dp(5)
            }
        }

    private fun showCreateModelGroupDialog(
        selected: AgentRegistryEntry,
        target: AgentConfigurationTarget,
        snapshot: AgentLiveConfigSnapshot
    ) {
        val dialog = Dialog(context)
        val input = EditText(context).apply {
            hint = "分组名称"
            textSize = 15f
            maxLines = 1
            setSingleLine(true)
            setTextColor(tokens.textPrimary)
            setHintTextColor(tokens.textTertiary)
            setPadding(ui.dp(16), 0, ui.dp(16), 0)
            background = ui.roundedBox(agentSettingsSurface, tokens.border, ui.dp(18).toFloat(), ui.dp(1))
        }
        val status = TextView(context).apply {
            textSize = 12f
            setTextColor(tokens.danger)
            visibility = View.GONE
        }
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ui.dp(22), ui.dp(22), ui.dp(22), ui.dp(18))
            background = ui.roundedBox(agentSurface, android.graphics.Color.TRANSPARENT, ui.dp(26).toFloat())
            elevation = ui.dp(10).toFloat()
            addView(TextView(context).apply {
                text = "新建分组"
                textSize = 19f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(tokens.textPrimary)
            })
            addView(TextView(context).apply {
                text = "分组只整理模型库，不会改变 Agent 原生配置。"
                textSize = 13f
                setTextColor(tokens.textSecondary)
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, ui.dp(7), 0, ui.dp(14))
            })
            addView(input, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(52)))
            addView(status, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                setMargins(ui.dp(2), ui.dp(7), ui.dp(2), 0)
            })
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(providerBatchAction("取消", UiActionRole.Secondary) { dialog.dismiss() }, LinearLayout.LayoutParams(
                    0,
                    ui.dp(46),
                    1f
                ))
                addView(providerBatchAction("新建", UiActionRole.Primary) {
                    val group = modelLibraryStore.createGroup(
                        selected.registration.definition.agentId,
                        input.text?.toString().orEmpty()
                    )
                    if (group == null) {
                        status.text = "请输入一个未使用的分组名称"
                        status.visibility = View.VISIBLE
                    } else {
                        providerLibraryGroupId = group.id
                        dialog.dismiss()
                        showProviderManager(selected, target, providerPageSnapshot ?: snapshot)
                    }
                }, LinearLayout.LayoutParams(0, ui.dp(46), 1f).apply { marginStart = ui.dp(8) })
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, ui.dp(16), 0, 0)
            })
        }
        dialog.setContentView(content)
        dialog.show()
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
            setGravity(Gravity.CENTER)
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setDimAmount(if (isDark) 0.62f else 0.32f)
            setLayout((context.resources.displayMetrics.widthPixels * 0.88f).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        input.requestFocus()
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
    }

    private fun confirmDeleteModelGroup(
        selected: AgentRegistryEntry,
        target: AgentConfigurationTarget,
        snapshot: AgentLiveConfigSnapshot,
        groupId: String,
        groupName: String
    ) {
        showAgentDialogCard(
            title = "删除分组“$groupName”？",
            message = "供应商和模型不会被删除，只会回到“全部”。",
            actions = listOf(
                AgentDialogAction("取消", UiActionRole.Secondary) { dialog, _ -> dialog.dismiss() },
                AgentDialogAction("删除", UiActionRole.Danger) { dialog, _ ->
                    modelLibraryStore.deleteGroup(selected.registration.definition.agentId, groupId)
                    providerLibraryGroupId = AgentModelLibraryStore.ALL_GROUP_ID
                    dialog.dismiss()
                    showProviderManager(selected, target, providerPageSnapshot ?: snapshot)
                }
            )
        )
    }

    private fun confirmRemoveSelectedProviders(
        selected: AgentRegistryEntry,
        target: AgentConfigurationTarget,
        snapshot: AgentLiveConfigSnapshot,
        providers: List<AgentModelProviderProjection>
    ) {
        showAgentDialogCard(
            title = "删除 ${providers.size} 个供应商？",
            message = "会从 Kite 目录移除这些用户供应商和安全凭据；免费与官方来源不会删除。",
            actions = listOf(
                AgentDialogAction("取消", UiActionRole.Secondary) { dialog, _ -> dialog.dismiss() },
                AgentDialogAction("删除", UiActionRole.Danger) { dialog, button ->
                    button.isEnabled = false
                    button.alpha = 0.45f
                    button.text = "删除中…"
                    dialog.dismiss()
                    removeCatalogProviders(
                        selected,
                        target,
                        providers.map(AgentModelProviderProjection::id),
                    )
                }
            )
        )
    }

    private fun showProviderEditor(
        selected: AgentRegistryEntry,
        target: AgentConfigurationTarget,
        snapshot: AgentLiveConfigSnapshot,
        existing: AgentProviderSummary?,
        preset: AgentProviderPreset?
    ) {
        providerPageAgentId = selected.registration.definition.agentId
        providerPageTarget = target
        providerPageSnapshot = snapshot
        navigationScreen = AgentNavigationScreen.ProviderEditor
        navigationHost.removeAllViews()
        navigationHost.addView(
            buildProviderEditorPage(selected, target, snapshot, existing, preset),
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
        navigationHost.visibility = View.VISIBLE
    }

    private fun buildProviderEditorPage(
        selected: AgentRegistryEntry,
        target: AgentConfigurationTarget,
        snapshot: AgentLiveConfigSnapshot,
        existing: AgentProviderSummary?,
        initialPreset: AgentProviderPreset?
    ): View {
        val targetAgentId = selected.registration.definition.agentId
        val modelLibrary = modelLibraryStore.snapshot(targetAgentId)
        var selectedGroupId = existing?.id?.let(modelLibrary::providerGroupId)
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
                showProviderPresetPicker(target) { preset ->
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

        val groupValue = TextView(context).apply {
            text = modelLibrary.groups.firstOrNull { it.id == selectedGroupId }?.name ?: "未分组"
        }
        content.addView(sectionTitle("分组", "只整理 Kite 模型库；不会改写 Agent 原生模型配置。"))
        content.addView(providerGroupSelectionRow(groupValue) {
            showAgentChoiceCard(
                title = "选择分组",
                message = "删除分组时，供应商会自动回到“全部”。",
                actions = listOf(
                    AgentChoiceAction("未分组", selected = selectedGroupId == null) {
                        selectedGroupId = null
                        groupValue.text = "未分组"
                    }
                ) + modelLibrary.groups.map { group ->
                    AgentChoiceAction(group.name, selected = group.id == selectedGroupId) {
                        selectedGroupId = group.id
                        groupValue.text = group.name
                    }
                }
            )
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            setMargins(0, 0, 0, ui.dp(20))
        })

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
                confirmRemoveProvider(selected, target, snapshot, existing)
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
            setTextColor(tokens.textPrimary)
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
                status.text = "正在保存到 Kite…"
                status.setTextColor(tokens.textSecondary)
                status.visibility = View.VISIBLE
                isEnabled = false
                alpha = 0.45f
                providerEditorSaveAction = this
                val credential = credentialInput.credentialChange()
                keyInput.setText("")
                saveCatalogProvider(
                    selected = selected,
                    target = target,
                    provider = AgentProviderDraft(
                        id = id,
                        displayName = name,
                        baseUrl = url,
                        models = models,
                    ),
                    credential = credential,
                    successMessage = "供应商资料已更新",
                    onApplied = { appliedProviderId ->
                        modelLibraryStore.assignProviderGroup(targetAgentId, appliedProviderId, selectedGroupId)
                        modelLibraryStore.replaceProviderModelDisplayNames(
                            targetAgentId,
                            appliedProviderId,
                            models.map { model -> AgentModelDisplayName(model.id, model.displayName) }
                        )
                    }
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
        target: AgentConfigurationTarget,
        snapshot: AgentLiveConfigSnapshot,
        provider: AgentProviderSummary
    ) {
        showAgentDialogCard(
            title = "删除 ${provider.displayName}？",
            message = "会从 Kite 的供应商目录和安全凭据中移除；其他供应商不受影响。",
            actions = listOf(
                AgentDialogAction("取消", UiActionRole.Secondary) { dialog, _ -> dialog.dismiss() },
                AgentDialogAction("删除", UiActionRole.Danger) { dialog, _ ->
                    dialog.dismiss()
                    removeCatalogProvider(selected, target, provider.id)
                }
            )
        )
    }

    private fun selectPersistentModel(
        selected: AgentRegistryEntry,
        target: AgentConfigurationTarget,
        snapshot: AgentLiveConfigSnapshot,
        provider: AgentModelProviderProjection,
        model: AgentConfigChoice
    ) {
        val snapshotProvider = snapshot.providers.firstOrNull { it.id == provider.id }
        val snapshotContainsModel = snapshotProvider?.models?.any { candidate ->
            model.value == "${provider.id}/${candidate.id}" || model.value == candidate.id
        } == true
        if (!snapshotContainsModel) {
            Toast.makeText(context, "Kite 目录中已没有这个模型", Toast.LENGTH_SHORT).show()
            return
        }
        val modelId = model.value.removePrefix("${provider.id}/")
        val targetAgentId = selected.registration.definition.agentId
        val requestRevision = ++settingsLoadRevision
        navigationJob?.cancel()
        navigationJob = lifecycleOwner.lifecycleScope.launch {
            val selectedInCatalog = withContext(Dispatchers.IO) {
                agentProviderCatalogApi.selectModel(target, provider.id, modelId)
            }
            if (requestRevision != settingsLoadRevision || selectedSettingsAgentId != targetAgentId) return@launch
            if (selectedInCatalog) {
                val projection = agentProviderCatalogApi.snapshot(target).toConfigurationProjection(target)
                providerPageSnapshot = projection
                usePersistentSnapshotAsDraftDefault(targetAgentId, projection)
                showProviderManager(selected, target, projection)
                Toast.makeText(context, "${model.name} 已设为默认", Toast.LENGTH_SHORT).show()
            } else {
                showProviderManager(selected, target, snapshot)
                Toast.makeText(context, "Kite 目录中已没有这个模型", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun saveCatalogProvider(
        selected: AgentRegistryEntry,
        target: AgentConfigurationTarget,
        provider: AgentProviderDraft,
        credential: AgentProviderCredentialChange,
        successMessage: String,
        onApplied: ((String) -> Unit)? = null
    ) {
        val targetAgentId = selected.registration.definition.agentId
        val requestRevision = ++settingsLoadRevision
        navigationJob = lifecycleOwner.lifecycleScope.launch {
            val saved = withContext(Dispatchers.IO) {
                agentProviderCatalogApi.saveUserProvider(target, provider, credential)
            }
            if (requestRevision != settingsLoadRevision || selectedSettingsAgentId != targetAgentId) return@launch
            if (saved != null) {
                providerEditorSaveAction = null
                providerEditorStatusText = null
                onApplied?.invoke(saved.id)
                val projection = agentProviderCatalogApi.snapshot(target).toConfigurationProjection(target)
                providerPageSnapshot = projection
                showProviderManager(selected, target, projection)
                loadDraftModelCatalog(force = true)
                Toast.makeText(context, successMessage, Toast.LENGTH_SHORT).show()
            } else {
                providerEditorSaveAction?.apply {
                    isEnabled = true
                    alpha = 1f
                }
                providerEditorStatusText?.apply {
                    text = "供应商 ID 已被免费或官方来源占用"
                    setTextColor(android.graphics.Color.rgb(198, 40, 40))
                    visibility = View.VISIBLE
                }
            }
        }
    }

    private fun removeCatalogProvider(
        selected: AgentRegistryEntry,
        target: AgentConfigurationTarget,
        providerId: String,
    ) {
        val targetAgentId = selected.registration.definition.agentId
        val requestRevision = ++settingsLoadRevision
        navigationJob?.cancel()
        navigationJob = lifecycleOwner.lifecycleScope.launch {
            val removed = withContext(Dispatchers.IO) {
                agentProviderCatalogApi.removeUserProvider(target, providerId)
            }
            if (requestRevision != settingsLoadRevision || selectedSettingsAgentId != targetAgentId) return@launch
            val projection = agentProviderCatalogApi.snapshot(target).toConfigurationProjection(target)
            providerPageSnapshot = projection
            showProviderManager(selected, target, projection)
            loadDraftModelCatalog(force = true)
            Toast.makeText(
                context,
                if (removed) "供应商资料已删除" else "这个供应商不能从用户目录删除",
                Toast.LENGTH_SHORT,
            ).show()
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
        val runtime = AgentRuntimeRegistry.session(instanceId)
            ?.takeIf { it.generation == generation }
        return filterModelLibraryChoices(draftSessionConfigurationOptions(runtime))
            .filterNot {
                it.category == AgentConfigCategory.Mode || it.category == AgentConfigCategory.Permission
            }
    }

    private fun providerGroupSelectionRow(valueText: TextView, onClick: () -> Unit): View =
        LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = ui.dp(58)
            setPadding(ui.dp(16), ui.dp(8), ui.dp(7), ui.dp(8))
            background = ui.roundedBox(agentSettingsSurface, android.graphics.Color.TRANSPARENT, ui.dp(19).toFloat())
            addView(valueText.apply {
                textSize = 15f
                typeface = Typeface.DEFAULT_BOLD
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                setTextColor(tokens.textPrimary)
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(ImageView(context).apply {
                setImageResource(R.drawable.ic_chevron_right_light)
                imageTintList = ColorStateList.valueOf(tokens.textSecondary)
                setPadding(ui.dp(11), ui.dp(11), ui.dp(11), ui.dp(11))
            }, LinearLayout.LayoutParams(ui.dp(42), ui.dp(42)))
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
        }

    private fun filterModelLibraryChoices(options: List<AgentConfigOption>): List<AgentConfigOption> {
        val targetAgentId = agentId?.takeIf(String::isNotBlank) ?: return options
        val library = modelLibraryStore.snapshot(targetAgentId)
        val activeProviderId = draftModelSnapshot
            ?.takeIf { it.agentId == targetAgentId }
            ?.activeProviderId
        return options.map { option ->
            if (option is AgentConfigOption.Select && option.category == AgentConfigCategory.Model) {
                AgentModelLibraryPolicy.filterConversationModelOption(
                    option,
                    library,
                    activeProviderId,
                    modelLibraryOfficialAccounts(targetAgentId),
                )
            } else option
        }
    }

    private fun removeCatalogProviders(
        selected: AgentRegistryEntry,
        target: AgentConfigurationTarget,
        providerIds: List<String>,
    ) {
        if (providerIds.isEmpty()) return
        val targetAgentId = selected.registration.definition.agentId
        val requestRevision = ++settingsLoadRevision
        navigationJob?.cancel()
        navigationJob = lifecycleOwner.lifecycleScope.launch {
            val removed = withContext(Dispatchers.IO) {
                providerIds.count { providerId ->
                    agentProviderCatalogApi.removeUserProvider(target, providerId)
                }
            }
            if (requestRevision != settingsLoadRevision || selectedSettingsAgentId != targetAgentId) return@launch
            val projection = agentProviderCatalogApi.snapshot(target).toConfigurationProjection(target)
            providerPageSnapshot = projection
            providerLibraryMode = AgentProviderLibraryMode.Browse
            selectedProviderIds.clear()
            expandedProviderIds.retainAll(projection.providers.map { it.id }.toSet())
            showProviderManager(selected, target, projection)
            loadDraftModelCatalog(force = true)
            Toast.makeText(context, "已删除 $removed 个供应商", Toast.LENGTH_SHORT).show()
        }
    }

    private fun draftSessionConfigurationOptions(
        runtime: AgentRuntimeSession?
    ): List<AgentConfigOption> {
        val preferences = runtime?.let {
            AgentRuntimeRegistry.draftPreferences(it.instanceId, it.generation)
        }
        val runtimeCatalog = runtime?.let {
            AgentRuntimeRegistry.draftCapabilityCatalog(it.instanceId, it.generation)
        }
        val runtimeOptions = runtimeCatalog
            ?.configuration
            .orEmpty()
            .map { option -> option.withDraftValue(preferences?.configuration?.get(option.id)) }
        val runtimeCategories = runtimeOptions.mapNotNullTo(hashSetOf(), AgentConfigOption::category).apply {
            addAll(runtimeCatalog?.resolvedConfigurationCategories.orEmpty())
        }
        val storedControls = draftProviderCatalogSnapshot
            ?.controls
            .orEmpty()
            .filterNot { it.category in runtimeCategories }
            .map { option -> option.withDraftValue(preferences?.configuration?.get(option.id)) }
        val cached = storedControls + runtimeOptions
        val persistentModel = draftModelSnapshot?.let { snapshot ->
            AgentDraftModelPolicy.option(
                snapshot,
                runtime?.let {
                    AgentRuntimeRegistry.draftModelSelection(it.instanceId, it.generation)
                },
                agentId?.let(modelLibraryStore::snapshot) ?: com.kite.app.agent.store.AgentModelLibrarySnapshot(),
                modelLibraryOfficialAccounts(agentId),
            )
        }
        return if (persistentModel == null) {
            cached
        } else {
            listOf(persistentModel) + cached.filterNot { it.category == AgentConfigCategory.Model }
        }
    }

    private fun modelLibraryOfficialAccounts(targetAgentId: String?): List<AgentOfficialAccountSpec> {
        if (targetAgentId.isNullOrBlank()) return emptyList()
        if (targetAgentId == agentId && draftModelOfficialAccounts.isNotEmpty()) {
            return draftModelOfficialAccounts
        }
        return settingsRegistrySnapshot?.entry(targetAgentId)?.registration?.officialAccounts.orEmpty()
    }

    private fun composerModeOptions(): List<AgentComposerModeOption> {
        val runtime = AgentRuntimeRegistry.session(instanceId)?.takeIf { it.generation == generation }
        val runtimeCatalog = runtime?.let {
            AgentRuntimeRegistry.draftCapabilityCatalog(it.instanceId, it.generation)
        }
        val modes = runtimeCatalog?.modes?.takeIf { it.isNotEmpty() }
            ?: draftProviderCatalogSnapshot?.workModes.orEmpty()
        if (modes.isNotEmpty()) {
            val currentModeId = runtime?.let {
                AgentRuntimeRegistry.draftPreferences(it.instanceId, it.generation)?.modeId
            } ?: runtimeCatalog?.currentModeId
                ?: draftProviderCatalogSnapshot?.selectedWorkModeId
            return modes.map { mode ->
                AgentComposerModeOption(
                    id = mode.id,
                    name = mode.name,
                    description = mode.description,
                    selected = mode.id == currentModeId,
                    configId = null,
                )
            }
        }
        val configuration = runtime?.let(::draftSessionConfigurationOptions).orEmpty()
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
        return emptyList()
    }

    private fun composerPermissionOption(): AgentConfigOption.Select? {
        val runtime = AgentRuntimeRegistry.session(instanceId)?.takeIf { it.generation == generation }
        val configuration = draftSessionConfigurationOptions(runtime)
        return AgentSurfaceNavigationPolicy.permissionOption(configuration)
    }

    private fun composerSkillTarget(): AgentConfigurationTarget? {
        val currentAgentId = agentId?.takeIf(String::isNotBlank) ?: return null
        return agentRegistry.snapshot().entry(currentAgentId)?.configurationTarget()
    }

    private fun composerSupportsSkills(): Boolean = composerSkillTarget()?.let { target ->
        agentConfigurationApi.capabilities(target)?.supports(AgentPersistentConfigCapability.Skill)
    } == true

    private fun loadComposerSkills(force: Boolean) {
        val target = composerSkillTarget() ?: return
        if (!force && AgentSkillCatalogCache.snapshot(target) != null) {
            composerSkillError = null
            if (composerExtensionRoute == ComposerExtensionRoute.Skills) {
                rebuildComposerExtensionMenu(animateContent = false)
            }
            return
        }
        if (composerSkillLoadJob?.isActive == true && !force) return
        val expectedAgentId = target.agentId
        val requestRevision = ++composerSkillLoadRevision
        composerSkillLoadJob?.cancel()
        composerSkillLoading = true
        composerSkillError = null
        if (composerExtensionRoute == ComposerExtensionRoute.Skills) {
            rebuildComposerExtensionMenu(animateContent = false)
        }
        composerSkillLoadJob = lifecycleOwner.lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { agentConfigurationApi.read(target) }
            if (requestRevision != composerSkillLoadRevision || agentId != expectedAgentId) return@launch
            composerSkillLoading = false
            when (result) {
                is AgentConfigReadResult.Ready -> AgentSkillCatalogCache.update(target, result.snapshot.skills)
                is AgentConfigReadResult.Failed -> composerSkillError = result.message
                is AgentConfigReadResult.Unavailable -> {
                    composerSkillError = result.discovery.warnings.firstOrNull() ?: "当前 Agent 的 Skill 不可用"
                }
            }
            if (composerExtensionRoute == ComposerExtensionRoute.Skills &&
                composerExtensionOverlay.visibility == View.VISIBLE
            ) {
                rebuildComposerExtensionMenu(animateContent = false)
            }
        }
    }

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
        closeSessionConfigurationPanel(animate = false)
        val hasEntry = supportsImageAttachments() ||
            supportsFileAttachments() ||
            composerSupportsSkills() ||
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
                    if (composerSupportsSkills()) {
                        addView(composerExtensionActionRow(
                            icon = R.drawable.ic_skill_light,
                            title = "Skill",
                            value = selectedSkills.size.takeIf { it > 0 }?.let { "已选 $it" },
                            showChevron = true,
                        ) {
                            composerExtensionRoute = ComposerExtensionRoute.Skills
                            rebuildComposerExtensionMenu(animateContent = true)
                            loadComposerSkills(force = false)
                        })
                    }
                }
                ComposerExtensionRoute.Modes -> {
                    addView(composerExtensionHeader(
                        title = "工作模式",
                        onBack = {
                            composerExtensionRoute = ComposerExtensionRoute.Main
                            rebuildComposerExtensionMenu(animateContent = true)
                        },
                    ))
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
                ComposerExtensionRoute.Skills -> {
                    addView(composerExtensionHeader(
                        title = "Skill",
                        onBack = null,
                        onRefresh = { loadComposerSkills(force = true) },
                        showClose = false,
                    ))
                    val target = composerSkillTarget()
                    val cached = target?.let(AgentSkillCatalogCache::snapshot)
                    when {
                        cached == null && composerSkillLoading -> addView(settingsMessage("正在读取 Skill…"))
                        cached == null && composerSkillError != null -> addView(settingsMessage(composerSkillError.orEmpty()))
                        cached == null -> addView(settingsMessage("尚未读取 Skill"))
                        cached.skills.isEmpty() -> addView(settingsMessage("尚未找到 Skill"))
                        else -> cached.skills.forEach { skill ->
                            val selected = selectedSkills.any { it.id == skill.id }
                            val available = skill.activation != AgentSkillActivation.Disabled
                            addView(sessionChoiceRow(
                                title = skill.displayName,
                                description = if (available) AgentSkillUiPolicy.summary(skill) else "已停用，请先在设置中启用",
                                selected = selected,
                                contentDescription = "Skill，${skill.displayName}",
                                onClick = {
                                    if (selected) {
                                        selectedSkills.removeAll { it.id == skill.id }
                                    } else {
                                        selectedSkills += AgentSelectedSkill(skill.id, skill.displayName)
                                    }
                                    renderSelectedSkills()
                                    updateComposer()
                                    rebuildComposerExtensionMenu(animateContent = false)
                                },
                            ).apply {
                                isEnabled = available
                                alpha = if (available) 1f else 0.45f
                            })
                        }
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
                        ComposerExtensionRoute.Skills -> 300
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

    private fun composerExtensionHeader(
        title: String,
        onBack: (() -> Unit)?,
        onRefresh: (() -> Unit)? = null,
        showClose: Boolean = true,
    ): View =
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
            if (onRefresh != null) {
                addView(iconButton(context, R.drawable.ic_refresh_light, "刷新 Skill", onRefresh).apply {
                    setPadding(ui.dp(10), ui.dp(10), ui.dp(10), ui.dp(10))
                }, LinearLayout.LayoutParams(ui.dp(44), ui.dp(44)))
            }
            if (showClose) {
                addView(iconButton(context, R.drawable.ic_close_light, "关闭") {
                    closeComposerExtensionMenu()
                }, LinearLayout.LayoutParams(ui.dp(40), ui.dp(40)))
            }
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
        closeComposerExtensionMenu(animate = false)
        sessionConfigurationPanel.show()
    }

    private fun closeSessionConfigurationPanel(animate: Boolean = true) {
        sessionConfigurationPanel.close(animate)
    }

    private fun rebuildSessionConfigurationPanel(animateContent: Boolean) {
        sessionConfigurationPanel.refresh(animateContent)
    }










    private fun sessionChoiceRow(
        title: String,
        description: String?,
        selected: Boolean,
        contentDescription: String,
        showChevron: Boolean = false,
        useSelectionDot: Boolean = false,
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
            selected && useSelectionDot -> addView(View(context).apply {
                background = ui.roundedBox(
                    tokens.textPrimary,
                    android.graphics.Color.TRANSPARENT,
                    ui.dp(5).toFloat()
                )
            }, LinearLayout.LayoutParams(ui.dp(10), ui.dp(10)).apply {
                marginStart = ui.dp(15)
                marginEnd = ui.dp(15)
            })
            selected -> addView(ImageView(context).apply {
                setImageResource(R.drawable.ic_check_light)
                imageTintList = ColorStateList.valueOf(tokens.textPrimary)
                setPadding(ui.dp(10), ui.dp(10), ui.dp(10), ui.dp(10))
            }, LinearLayout.LayoutParams(ui.dp(40), ui.dp(40)))
        }
        this.contentDescription = contentDescription
        isClickable = true
        isFocusable = true
        alpha = 1f
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
        isClickable = true
        isFocusable = true
        alpha = 1f
        setOnClickListener { onClick() }
    }.also { row ->
        row.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(ui.dp(2), ui.dp(1), ui.dp(2), ui.dp(1)) }
    }

    private fun AgentConfigOption.withDraftValue(value: AgentConfigValue?): AgentConfigOption = when {
        this is AgentConfigOption.Select && value is AgentConfigValue.Select -> copy(currentValue = value.value)
        this is AgentConfigOption.Toggle && value is AgentConfigValue.Toggle -> copy(currentValue = value.value)
        else -> this
    }

    private fun updateConfiguration(configId: String, value: AgentConfigValue) {
        if (draftPreparationPending) return
        val runtime = AgentRuntimeRegistry.session(instanceId)
            ?.takeIf { it.generation == generation }
            ?: return
        val result = if (configId == AgentDraftModelPolicy.CONFIG_ID && value is AgentConfigValue.Select) {
            val snapshot = draftModelSnapshot ?: return
            val selection = AgentDraftModelPolicy.selection(snapshot, value.value) ?: return
            agentSessionControlApi.selectModel(instanceId, generation, selection)
        } else {
            agentSessionControlApi.selectConfiguration(instanceId, generation, configId, value)
        }
        when (result) {
            is AgentOperationResult.Success -> {
                sessionConfigurationPanel.showOverview(animateContent = true)
                renderSessionConfigurationControls()
                Toast.makeText(context, "已为本轮消息预选", Toast.LENGTH_SHORT).show()
            }
            else -> showOperationResult(result, null)
        }
    }

    private fun updateLegacyMode(modeId: String) {
        if (draftPreparationPending) return
        val runtime = AgentRuntimeRegistry.session(instanceId)
            ?.takeIf { it.generation == generation }
        val result = if (runtime != null) {
            agentSessionControlApi.selectMode(instanceId, generation, modeId)
        } else {
            val target = draftProviderCatalogTarget ?: return
            if (agentProviderCatalogApi.selectWorkMode(target, modeId)) {
                draftProviderCatalogSnapshot = agentProviderCatalogApi.snapshot(target)
                AgentOperationResult.Success(Unit)
            } else {
                AgentOperationResult.Failure("当前 Agent 未提供该工作模式")
            }
        }
        when (result) {
            is AgentOperationResult.Success -> {
                renderSessionConfigurationControls()
                Toast.makeText(context, "已为本轮消息预选工作模式", Toast.LENGTH_SHORT).show()
            }
            else -> showOperationResult(result, null)
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
        val filledPrimary: Boolean = false,
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

    private data class SkillBatchRemoveOutcome(
        val snapshot: AgentLiveConfigSnapshot,
        val removedCount: Int,
        val failure: String?,
    )

    private data class PendingAttachment(
        val name: String,
        val content: AgentContent
    )

    private data class ComposerDraftIdentity(
        val instanceId: String,
        val generation: Long,
        val sessionId: String?,
    )

    private companion object {
        const val SESSION_SEARCH_WATCHER_TAG = "agent-session-search-watcher"
        const val COPY_BUFFER_SIZE = 8 * 1024
        const val MAX_INLINE_IMAGE_BYTES = 12 * 1024 * 1024
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
