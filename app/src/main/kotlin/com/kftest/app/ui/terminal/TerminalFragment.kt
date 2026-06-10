package com.kftest.app.ui.terminal

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.database.Cursor
import android.graphics.Typeface
import android.net.Uri
import android.os.Handler
import android.os.Bundle
import android.os.Looper
import android.os.SystemClock
import android.provider.OpenableColumns
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.text.format.DateFormat
import android.view.ContextMenu
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Scroller
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.AppCompatImageButton
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.FileProvider
import androidx.core.graphics.ColorUtils
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.card.MaterialCardView
import com.google.android.material.button.MaterialButton
import com.kftest.app.R
import com.kftest.app.foundation.bootstrap.BootstrapCoordinator
import com.kftest.app.foundation.bootstrap.BootstrapSnapshot
import com.kftest.app.foundation.bootstrap.BootstrapStage
import com.kftest.app.foundation.bootstrap.KFApplication
import com.kftest.app.foundation.logging.Logger
import com.kftest.app.foundation.runtime.ContainerRecord
import com.kftest.app.foundation.runtime.ExternalExchangeManager
import com.kftest.app.foundation.runtime.RuntimeActionKind
import com.kftest.app.foundation.runtime.TerminalSessionItem
import com.kftest.app.foundation.runtime.TerminalSessionStore
import com.kftest.app.foundation.runtime.TerminalSessionsSnapshot
import com.kftest.app.foundation.service.WorkstationActionGateway
import com.kftest.app.foundation.terminal.TerminalSessionController
import com.kftest.app.foundation.terminal.TerminalRuntimeHost
import com.kftest.app.foundation.terminal.TerminalSessionUiCallbacks
import com.kftest.app.foundation.workspace.AgentKind
import com.kftest.app.foundation.workspace.AgentRuntimeRecord
import com.kftest.app.foundation.workspace.KFWorkspaceManager
import com.kftest.app.foundation.workspace.ManagedTerminalStatus
import com.kftest.app.foundation.workspace.SpaceRecord
import com.kftest.app.foundation.workspace.WorkSurfaceRuntimeBridge
import com.termux.terminal.TerminalColors
import com.termux.terminal.TerminalSession
import com.termux.terminal.TextStyle
import com.termux.view.TerminalView
import com.termux.view.TerminalViewClient
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Calendar
import java.util.concurrent.atomic.AtomicLong

class TerminalFragment : Fragment(), TerminalViewClient, TerminalSessionUiCallbacks,
    TerminalActionSheet.Listener, TerminalEntrySheet.Listener {

    companion object {
        private const val MIN_TERMINAL_REFRESH_INTERVAL_MS = 33L
        private const val TERMINAL_REFRESH_MIN_INTERVAL_MS = 5_000L
        private const val TERMINAL_COMPOSER_MAX_LINES = 8
        private const val TERMINAL_CONTEXT_COPY_SCREEN = 4001
        private const val TERMINAL_CONTEXT_COPY_ALL = 4002
    }

    private lateinit var listPage: View
    private lateinit var detailPage: View
    private lateinit var terminalListRefresh: SwipeRefreshLayout
    private lateinit var tvEmptySessions: TextView
    private lateinit var terminalListContainer: LinearLayout
    private lateinit var tvDetailTitle: TextView
    private lateinit var tvSessionNote: TextView
    private lateinit var cardBootstrapStatus: View
    private lateinit var progressBootstrap: ProgressBar
    private lateinit var tvBootstrapTitle: TextView
    private lateinit var tvBootstrapDetail: TextView
    private lateinit var btnThemeMode: MaterialButton
    private lateinit var terminalInputBar: LinearLayout
    private lateinit var terminalComposerInput: EditText
    private lateinit var terminalControlPanel: LinearLayout
    private lateinit var terminalControlPage: LinearLayout
    private lateinit var terminalPanelIndicator: LinearLayout
    private lateinit var terminalView: TerminalView
    private lateinit var terminalController: TerminalSessionController

    private var isCtrlPressed = false
    private var isAltPressed = false
    private var currentFontSizeDp = 35
    private var currentSpace: SpaceRecord? = null
    private var currentAgents: List<AgentRuntimeRecord> = emptyList()
    private var currentTerminalSnapshot = TerminalSessionsSnapshot()
    private var lastManagedSessionsRenderSignature: String = ""
    private var isDetailMode = false
    private var isTerminalPanelExpanded = false
    private var terminalPanelPageIndex = 0
    private var panelSwipeStartX = 0f
    private var sessionNoteJob: Job? = null
    private var terminalRefreshJob: Job? = null
    @Volatile
    private var pendingTerminalRefresh = false
    private val keyRepeatHandler = Handler(Looper.getMainLooper())
    private var keyRepeatRunnable: Runnable? = null
    private val terminalRefreshLock = Any()
    private val uiRefreshRequested = AtomicLong(0L)
    private val uiRefreshExecuted = AtomicLong(0L)
    private val uiRefreshCoalesced = AtomicLong(0L)
    private val uiRefreshRateLimited = AtomicLong(0L)
    private var composerLiveSyncBuffer = ""
    private var composerLiveSyncEnabled = false
    private var suppressComposerWatcher = false
    private var terminalSelectionFocusAllowed = false
    private val fileDeliveryPicker = registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            deliverFilesToChuan(uris)
        }
    }
    private var pendingCameraFile: File? = null
    private val cameraCaptureLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        val file = pendingCameraFile
        pendingCameraFile = null
        if (success && file != null) {
            Toast.makeText(requireContext(), "照片已保存：/chuan/in/camera/${file.name}", Toast.LENGTH_SHORT).show()
            setTerminalPanelExpanded(false)
        } else {
            file?.delete()
            Toast.makeText(requireContext(), "拍照已取消", Toast.LENGTH_SHORT).show()
        }
    }
    private var followTerminalOutput = true
    private var terminalTouchScrolling = false
    private var terminalTouchStartTopRow = 0
    private var baseTerminalPalette: IntArray? = null
    @Volatile
    private var uiRefreshScheduled = false
    @Volatile
    private var uiRefreshDirty = false
    @Volatile
    private var lastUiRefreshAtMs = 0L
    @Volatile
    private var lastTerminalRefreshRequestedAtMs = 0L
    private val terminalViewRefreshRunnable: Runnable = object : Runnable {
        override fun run() {
            if (view == null) {
                synchronized(terminalRefreshLock) {
                    uiRefreshScheduled = false
                    uiRefreshDirty = false
                }
                return
            }
            uiRefreshExecuted.incrementAndGet()
            lastUiRefreshAtMs = SystemClock.uptimeMillis()
            performTerminalViewRefresh()
            synchronized(terminalRefreshLock) {
                uiRefreshScheduled = false
            }
            scheduleTerminalViewRefreshIfNeeded()
        }
    }

    private val detailBackCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            showListPage()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        Logger.i("Terminal", "创建终端页面")
        return inflater.inflate(R.layout.fragment_terminal, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        KFApplication.markLaunchStage("Terminal", "TerminalFragment.onViewCreated 开始")
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            detailBackCallback
        )
        setupViews(view)
        KFApplication.markLaunchStage("Terminal", "终端页面视图初始化完成")
        observeContainerState()
        observeSpaceState()
        observeTerminalSessions()
        observeBootstrapState()
        requestTerminalRefresh("first-render")
        KFApplication.markLaunchStage("Terminal", "终端快照刷新已请求")
        KFApplication.markLaunchStage("Terminal", "终端页面以入口模式就绪")
    }

    override fun onResume() {
        super.onResume()
        terminalView.setTerminalCursorBlinkerState(true, true)
        if (isDetailMode) {
            terminalComposerInput.post {
                if (!terminalView.isSelectingText()) {
                    focusComposerInput(showKeyboard = false)
                }
            }
        }
        requestTerminalRefresh("fragment-resume")
    }

    override fun onPause() {
        terminalView.removeCallbacks(terminalViewRefreshRunnable)
        synchronized(terminalRefreshLock) {
            uiRefreshScheduled = false
            uiRefreshDirty = false
        }
        Logger.i("Terminal", buildUiRefreshDebugSummary())
        Logger.i("Terminal", terminalController.buildOutputBackpressureDebugSummary())
        terminalView.setTerminalCursorBlinkerState(false, false)
        super.onPause()
    }

    private fun setupViews(view: View) {
        listPage = view.findViewById(R.id.terminalListPage)
        detailPage = view.findViewById(R.id.terminalDetailPage)
        terminalListRefresh = view.findViewById(R.id.terminalListRefresh)
        tvEmptySessions = view.findViewById(R.id.tvEmptySessions)
        terminalListContainer = view.findViewById(R.id.terminalListContainer)
        tvDetailTitle = view.findViewById(R.id.tvDetailTitle)
        tvSessionNote = view.findViewById(R.id.tvSessionNote)
        cardBootstrapStatus = view.findViewById(R.id.cardBootstrapStatus)
        progressBootstrap = view.findViewById(R.id.progressBootstrap)
        tvBootstrapTitle = view.findViewById(R.id.tvBootstrapTitle)
        tvBootstrapDetail = view.findViewById(R.id.tvBootstrapDetail)
        btnThemeMode = view.findViewById(R.id.btnThemeMode)
        terminalInputBar = view.findViewById(R.id.terminalInputBar)
        terminalView = view.findViewById(R.id.terminalView)
        applyShellThemeToStaticViews(view)
        val appContext = requireContext().applicationContext
        terminalController = TerminalRuntimeHost.attachUi(appContext, this)
        currentFontSizeDp = TerminalUiPreferences.loadFontSizeDp(appContext)

        applyTerminalColorScheme()
        terminalView.setTerminalViewClient(this)
        terminalView.setTextSize(currentFontSizeDp)
        terminalView.setTypeface(Typeface.MONOSPACE)
        terminalView.setTerminalCursorBlinkerRate(650)
        terminalView.keepScreenOn = true
        terminalView.isFocusable = true
        terminalView.isFocusableInTouchMode = true
        terminalView.isLongClickable = true
        terminalView.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus && !terminalSelectionFocusAllowed && !terminalView.isSelectingText()) {
                terminalComposerInput.post {
                    if (isDetailMode && !terminalSelectionFocusAllowed && !terminalView.isSelectingText()) {
                        focusComposerInput(showKeyboard = false)
                    }
                }
            }
        }
        registerForContextMenu(terminalView)
        terminalView.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    terminalTouchScrolling = false
                    terminalTouchStartTopRow = terminalView.topRow
                }

                MotionEvent.ACTION_MOVE -> {
                    terminalTouchScrolling = true
                    followTerminalOutput = false
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (terminalTouchScrolling || terminalView.topRow != terminalTouchStartTopRow) {
                        followTerminalOutput = terminalView.topRow == 0
                    }
                }
            }
            false
        }

        terminalListRefresh.setColorSchemeColors(
            color(R.color.terminal_page_blue)
        )
        terminalListRefresh.setOnRefreshListener {
            requestTerminalRefresh("pull-to-refresh", force = true, userVisible = true)
        }

        view.findViewById<AppCompatImageButton>(R.id.btnListAdd).setOnClickListener {
            showEntrySheet()
        }
        view.findViewById<AppCompatImageButton>(R.id.btnBackToSessions).setOnClickListener {
            showListPage()
        }
        view.findViewById<AppCompatImageButton>(R.id.btnDetailMoreActions).setOnClickListener {
            showActionSheet()
        }
        view.findViewById<MaterialButton>(R.id.btnFontSmaller).setOnClickListener {
            applyFontSize(TerminalUiPreferences.stepFontSize(currentFontSizeDp, -1), true)
        }
        btnThemeMode.setOnClickListener { showThemeMenu(it) }
        view.findViewById<MaterialButton>(R.id.btnFontLarger).setOnClickListener {
            applyFontSize(TerminalUiPreferences.stepFontSize(currentFontSizeDp, 1), true)
        }

        setupTerminalComposer()
        setupWindowInsets(view)
        updateThemeModeLabel()

        tvDetailTitle.text = getString(R.string.terminal_title_short)
        showSessionNote("")
        showListPage()
    }

    private fun applyTerminalColorScheme() {
        val colors = TerminalColors.COLOR_SCHEME.mDefaultColors
        val basePalette = baseTerminalPalette ?: colors.clone().also { baseTerminalPalette = it }
        System.arraycopy(basePalette, 0, colors, 0, minOf(basePalette.size, colors.size))
        val isDark = TerminalUiPreferences.resolveTerminalDarkMode(requireContext())
        if (isDark) {
            applyDarkTerminalPalette(colors)
        } else {
            applyLightTerminalPalette(colors)
        }
        TerminalColors.COLOR_SCHEME.setCursorColorForBackground()
    }

    private fun applyDarkTerminalPalette(colors: IntArray) {
        colors[TextStyle.COLOR_INDEX_FOREGROUND] = 0xFFF2F6FB.toInt()
        colors[TextStyle.COLOR_INDEX_BACKGROUND] = 0xFF0B1118.toInt()
        applyAnsiOverride(colors, 0, 0xFF2C3440.toInt())
        applyAnsiOverride(colors, 7, 0xFFD6DEE8.toInt())
        applyAnsiOverride(colors, 8, 0xFF667381.toInt())
        applyAnsiOverride(colors, 15, 0xFFF7FBFF.toInt())
    }

    private fun applyLightTerminalPalette(colors: IntArray) {
        val background = 0xFFFDFDFD.toInt()
        val foreground = 0xFF1F2329.toInt()
        colors[TextStyle.COLOR_INDEX_FOREGROUND] = foreground
        colors[TextStyle.COLOR_INDEX_BACKGROUND] = background

        for (index in 0 until minOf(16, colors.size)) {
            colors[index] = adaptAnsiColorForLightMode(colors[index], background, foreground, index)
        }

        applyAnsiOverride(colors, 0, 0xFF252B33.toInt())
        applyAnsiOverride(colors, 7, 0xFF5F6873.toInt())
        applyAnsiOverride(colors, 8, 0xFF6D7784.toInt())
        applyAnsiOverride(colors, 15, 0xFF4D5661.toInt())
    }

    private fun applyAnsiOverride(colors: IntArray, index: Int, value: Int) {
        if (index in colors.indices) {
            colors[index] = value
        }
    }

    private fun adaptAnsiColorForLightMode(
        originalColor: Int,
        backgroundColor: Int,
        foregroundColor: Int,
        index: Int
    ): Int {
        val specialTargetLightness = when (index) {
            3, 11 -> 0.34f
            6, 14 -> 0.33f
            2, 10 -> 0.32f
            7, 15 -> 0.40f
            else -> 0.38f
        }
        var adjusted = originalColor
        if (ColorUtils.calculateLuminance(adjusted) > 0.66) {
            adjusted = reduceColorLightness(adjusted, specialTargetLightness)
        }
        if (ColorUtils.calculateContrast(adjusted, backgroundColor) < 4.5) {
            adjusted = blendTowardForeground(adjusted, foregroundColor, backgroundColor, 4.5)
        }
        return adjusted
    }

    private fun reduceColorLightness(color: Int, targetLightness: Float): Int {
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(color, hsl)
        hsl[2] = minOf(hsl[2], targetLightness)
        return ColorUtils.HSLToColor(hsl)
    }

    private fun blendTowardForeground(
        color: Int,
        foregroundColor: Int,
        backgroundColor: Int,
        minContrast: Double
    ): Int {
        var adjusted = color
        var blend = 0f
        while (ColorUtils.calculateContrast(adjusted, backgroundColor) < minContrast && blend < 1f) {
            blend += 0.12f
            adjusted = ColorUtils.blendARGB(color, foregroundColor, blend.coerceAtMost(1f))
        }
        return adjusted
    }

    private fun setupTerminalComposer() {
        terminalInputBar.removeAllViews()
        terminalInputBar.setPadding(dp(10), dp(8), dp(10), dp(10))

        terminalControlPanel = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(0, 0, 0, dp(8))
        }
        terminalControlPage = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setOnTouchListener { _, event ->
                handlePanelSwipe(event)
            }
        }
        terminalPanelIndicator = LinearLayout(requireContext()).apply {
            gravity = Gravity.CENTER
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(8), 0, 0)
        }
        terminalControlPanel.addView(terminalControlPage)
        terminalControlPanel.addView(terminalPanelIndicator)
        terminalInputBar.addView(terminalControlPanel)

        val composerRow = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.BOTTOM
        }

        val panelToggle = composerRoundButton("+").apply {
            setOnClickListener { toggleTerminalPanel() }
        }
        composerRow.addView(panelToggle, LinearLayout.LayoutParams(dp(44), dp(44)))

        val inputCard = MaterialCardView(requireContext()).apply {
            radius = dp(18).toFloat()
            cardElevation = 0f
            setCardBackgroundColor(color(R.color.terminal_page_input_bg))
            strokeColor = color(R.color.terminal_page_line)
            strokeWidth = dp(1)
            minimumHeight = dp(44)
            minimumWidth = 0
            setContentPadding(dp(12), dp(8), dp(10), dp(8))
        }
        terminalComposerInput = EditText(requireContext()).apply {
            background = null
            hint = "输入命令或和 Agent 对话..."
            setHintTextColor(color(R.color.terminal_page_subtext))
            setTextColor(color(R.color.terminal_page_text))
            textSize = 14f
            minLines = 1
            maxLines = TERMINAL_COMPOSER_MAX_LINES
            minHeight = dp(28)
            minWidth = 0
            maxWidth = Int.MAX_VALUE
            includeFontPadding = false
            setMaxHeight(composerInputMaxHeight())
            gravity = Gravity.START or Gravity.TOP
            inputType = InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            imeOptions = EditorInfo.IME_ACTION_NONE or EditorInfo.IME_FLAG_NO_EXTRACT_UI
            setSingleLine(false)
            setHorizontallyScrolling(false)
            setScroller(Scroller(context))
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            scrollBarStyle = View.SCROLLBARS_INSIDE_INSET
            setOnEditorActionListener { _, actionId, event ->
                val isSendAction = actionId == EditorInfo.IME_ACTION_SEND
                if (isSendAction) {
                    submitComposerInput()
                    true
                } else {
                    false
                }
            }
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit

                override fun afterTextChanged(s: Editable?) {
                    if (!suppressComposerWatcher) {
                        handleComposerRealtimeInput(s?.toString().orEmpty())
                    }
                    post { updateComposerScrollState() }
                }
            })
        }
        inputCard.addView(
            terminalComposerInput,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        composerRow.addView(
            inputCard,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(8)
            }
        )

        val sendButton = composerRoundButton("➤").apply {
            setOnClickListener { submitComposerInput() }
        }
        composerRow.addView(
            sendButton,
            LinearLayout.LayoutParams(dp(44), dp(44)).apply {
                marginStart = dp(8)
            }
        )

        terminalInputBar.addView(composerRow)
        renderTerminalPanelPage()
        terminalComposerInput.post { updateComposerScrollState() }
    }

    private fun renderTerminalPanelPage() {
        terminalControlPage.removeAllViews()
        terminalControlPage.addView(
            when (terminalPanelPageIndex) {
                1 -> buildPanelGrid(
                    listOf(
                        PanelButton("文件", "传入") { openFileDeliveryPicker() },
                        PanelButton("图片", "占位") {},
                        PanelButton("相机", "拍照") { openCameraDelivery() },
                        PanelButton("语音", "占位") {},
                        PanelButton("日志", "占位") { showActionSheet() },
                        PanelButton("收起", "面板") { setTerminalPanelExpanded(false) }
                    )
                )

                2 -> buildPanelGrid(
                    listOf(
                        PanelButton("A-", "字号") {
                            applyFontSize(TerminalUiPreferences.stepFontSize(currentFontSizeDp, -1), true)
                        },
                        PanelButton("主题", "切换") {
                            showThemeMenu(it)
                        },
                        PanelButton("A+", "字号") {
                            applyFontSize(TerminalUiPreferences.stepFontSize(currentFontSizeDp, 1), true)
                        },
                        PanelButton("更多", "占位") { showActionSheet() },
                        PanelButton("自定义", "占位") {},
                        PanelButton("收起", "面板") { setTerminalPanelExpanded(false) }
                    )
                )

                else -> buildTerminalControlPage()
            }
        )
        renderPanelIndicator()
    }

    private fun openFileDeliveryPicker() {
        fileDeliveryPicker.launch(arrayOf("*/*"))
    }

    private fun openCameraDelivery() {
        runCatching {
            val deliveryRoot = ExternalExchangeManager.ensureExchangeDir(requireContext().applicationContext)
            val cameraDir = File(deliveryRoot, "in/camera").apply { mkdirs() }
            val photoFile = File(cameraDir, "photo-${System.currentTimeMillis()}.jpg")
            val uri = FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().packageName}.fileprovider",
                photoFile
            )
            pendingCameraFile = photoFile
            cameraCaptureLauncher.launch(uri)
        }.onFailure { throwable ->
            pendingCameraFile = null
            Logger.e("TerminalFragment", "Camera delivery failed: ${throwable.message}")
            Toast.makeText(requireContext(), "无法启动相机：${throwable.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun deliverFilesToChuan(uris: List<Uri>) {
        val appContext = requireContext().applicationContext
        viewLifecycleOwner.lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val deliveryRoot = ExternalExchangeManager.ensureExchangeDir(appContext)
                    val inputDir = File(deliveryRoot, "in").apply { mkdirs() }
                    var copied = 0
                    uris.forEach { uri ->
                        val fileName = resolveDisplayName(appContext, uri).ifBlank {
                            "file-${System.currentTimeMillis()}-${copied + 1}"
                        }
                        val target = uniqueTargetFile(inputDir, sanitizeFileName(fileName))
                        appContext.contentResolver.openInputStream(uri)?.use { input ->
                            target.outputStream().use { output -> input.copyTo(output) }
                            copied += 1
                        }
                    }
                    copied
                }
            }
            result
                .onSuccess { copied ->
                    Toast.makeText(requireContext(), "已传入 $copied 个文件：/chuan/in", Toast.LENGTH_SHORT).show()
                    setTerminalPanelExpanded(false)
                }
                .onFailure { throwable ->
                    Logger.e("TerminalFragment", "File delivery failed: ${throwable.message}")
                    Toast.makeText(requireContext(), "文件传入失败：${throwable.message}", Toast.LENGTH_LONG).show()
                }
        }
    }

    private fun resolveDisplayName(context: Context, uri: Uri): String {
        var cursor: Cursor? = null
        return try {
            cursor = context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null
            )
            if (cursor != null && cursor.moveToFirst()) {
                cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)).orEmpty()
            } else {
                uri.lastPathSegment.orEmpty()
            }
        } catch (_: Throwable) {
            uri.lastPathSegment.orEmpty()
        } finally {
            cursor?.close()
        }
    }

    private fun sanitizeFileName(name: String): String {
        return name
            .replace(Regex("[\\\\/:*?\"<>|\\u0000-\\u001F]"), "_")
            .trim()
            .ifBlank { "file-${System.currentTimeMillis()}" }
    }

    private fun uniqueTargetFile(dir: File, fileName: String): File {
        var candidate = File(dir, fileName)
        if (!candidate.exists()) return candidate
        val dot = fileName.lastIndexOf('.')
        val base = if (dot > 0) fileName.substring(0, dot) else fileName
        val ext = if (dot > 0) fileName.substring(dot) else ""
        var index = 1
        while (candidate.exists()) {
            candidate = File(dir, "$base-$index$ext")
            index += 1
        }
        return candidate
    }

    private fun updateComposerScrollState() {
        if (!::terminalComposerInput.isInitialized) {
            return
        }
        val layout = terminalComposerInput.layout
        if (layout == null) {
            terminalComposerInput.isVerticalScrollBarEnabled = false
            terminalComposerInput.scrollTo(0, 0)
            return
        }
        val contentHeight = layout.getLineTop(terminalComposerInput.lineCount) +
            terminalComposerInput.compoundPaddingTop +
            terminalComposerInput.compoundPaddingBottom
        val maxHeight = composerInputMaxHeight()
        terminalComposerInput.setMaxHeight(maxHeight)
        val targetHeight = contentHeight
            .coerceAtLeast(dp(28))
            .coerceAtMost(maxHeight)
        val layoutParams = terminalComposerInput.layoutParams
        if (layoutParams.height != targetHeight) {
            layoutParams.height = targetHeight
            terminalComposerInput.layoutParams = layoutParams
        }

        val reachedMaxHeight = contentHeight > maxHeight
        terminalComposerInput.isVerticalScrollBarEnabled = reachedMaxHeight
        if (!reachedMaxHeight) {
            terminalComposerInput.scrollTo(0, 0)
            return
        }
        val availableHeight = terminalComposerInput.height -
            terminalComposerInput.compoundPaddingTop -
            terminalComposerInput.compoundPaddingBottom
        val scrollableContentHeight = layout.getLineTop(terminalComposerInput.lineCount)
        val scrollY = (scrollableContentHeight - availableHeight).coerceAtLeast(0)
        terminalComposerInput.scrollTo(0, scrollY)
    }

    private fun composerInputMaxHeight(): Int {
        val lineHeight = if (::terminalComposerInput.isInitialized && terminalComposerInput.lineHeight > 0) {
            terminalComposerInput.lineHeight
        } else {
            dp(20)
        }
        return lineHeight * TERMINAL_COMPOSER_MAX_LINES + dp(4)
    }

    private fun buildTerminalControlPage(): View {
        val root = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        root.addView(
            buildPanelGrid(
                listOf(
                    PanelButton("Ctrl+C", "中断") { sendTerminalInput("\u0003") },
                    PanelButton("Esc", "取消") { sendTerminalInput("\u001b") },
                    PanelButton("Tab", "补全") { sendTerminalInput("\t") },
                    PanelButton("OK", "回车") { submitComposerOrSendEnter() },
                    PanelButton("清屏", "Ctrl+L") { sendTerminalInput("\u000c") },
                    PanelButton("粘贴", "剪贴板") { pasteFromClipboard() }
                )
            ),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )

        val divider = View(requireContext()).apply {
            setBackgroundColor(color(R.color.terminal_page_line))
        }
        root.addView(
            divider,
            LinearLayout.LayoutParams(dp(1), dp(132)).apply {
                marginStart = dp(10)
                marginEnd = dp(10)
            }
        )

        root.addView(buildDpadCluster())
        return root
    }

    private fun buildDpadCluster(): View {
        val grid = GridLayout(requireContext()).apply {
            columnCount = 3
            rowCount = 3
        }

        fun addCell(view: View?, row: Int, col: Int) {
            val params = GridLayout.LayoutParams(
                GridLayout.spec(row),
                GridLayout.spec(col)
            ).apply {
                width = dp(54)
                height = dp(42)
                setMargins(dp(3), dp(3), dp(3), dp(3))
            }
            grid.addView(view ?: SpaceView(requireContext()), params)
        }

        addCell(null, 0, 0)
        addCell(controlTile("▲", "上").also {
            bindRepeatingKey(it, KeyEvent.KEYCODE_DPAD_UP, "\u001b[A")
        }, 0, 1)
        addCell(null, 0, 2)
        addCell(controlTile("◀", "左").also {
            bindRepeatingKey(it, KeyEvent.KEYCODE_DPAD_LEFT, "\u001b[D")
        }, 1, 0)
        addCell(controlTile("OK", "回车").also {
            it.setOnClickListener { submitComposerOrSendEnter() }
        }, 1, 1)
        addCell(controlTile("▶", "右").also {
            bindRepeatingKey(it, KeyEvent.KEYCODE_DPAD_RIGHT, "\u001b[C")
        }, 1, 2)
        addCell(null, 2, 0)
        addCell(controlTile("▼", "下").also {
            bindRepeatingKey(it, KeyEvent.KEYCODE_DPAD_DOWN, "\u001b[B")
        }, 2, 1)
        addCell(null, 2, 2)
        return grid
    }

    private fun buildPanelGrid(buttons: List<PanelButton>): View {
        val grid = GridLayout(requireContext()).apply {
            columnCount = 3
            rowCount = 2
        }
        buttons.forEachIndexed { index, button ->
            val tile = controlTile(button.title, button.subtitle).apply {
                setOnClickListener { button.action(this) }
            }
            val params = GridLayout.LayoutParams(
                GridLayout.spec(index / 3),
                GridLayout.spec(index % 3)
            ).apply {
                width = 0
                height = dp(58)
                columnSpec = GridLayout.spec(index % 3, 1f)
                setMargins(dp(4), dp(4), dp(4), dp(4))
            }
            grid.addView(tile, params)
        }
        return grid
    }

    private fun controlTile(title: String, subtitle: String): MaterialCardView {
        return MaterialCardView(requireContext()).apply {
            radius = dp(12).toFloat()
            cardElevation = dp(1).toFloat()
            setCardBackgroundColor(color(R.color.terminal_page_input_bg))
            strokeColor = color(R.color.terminal_page_line)
            strokeWidth = dp(1)
            isClickable = true
            isFocusable = true
            val label = TextView(requireContext()).apply {
                gravity = Gravity.CENTER
                text = if (subtitle.isBlank()) title else "$title\n$subtitle"
                setTextColor(color(R.color.terminal_page_text))
                textSize = if (subtitle.isBlank()) 17f else 12f
                typeface = Typeface.DEFAULT_BOLD
                includeFontPadding = false
            }
            addView(
                label,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
        }
    }

    private fun composerRoundButton(label: String): MaterialButton {
        return MaterialButton(requireContext()).apply {
            text = label
            textSize = 22f
            setTextColor(color(R.color.terminal_page_text))
            cornerRadius = dp(22)
            insetTop = 0
            insetBottom = 0
            minWidth = 0
            minimumWidth = 0
            setPadding(0, 0, 0, 0)
            backgroundTintList = android.content.res.ColorStateList.valueOf(
                color(R.color.terminal_page_input_bg)
            )
        }
    }

    private fun toggleTerminalPanel() {
        setTerminalPanelExpanded(!isTerminalPanelExpanded)
    }

    private fun setTerminalPanelExpanded(expanded: Boolean) {
        isTerminalPanelExpanded = expanded
        terminalControlPanel.visibility = if (expanded) View.VISIBLE else View.GONE
        if (expanded) {
            hideSoftKeyboard(terminalComposerInput)
        }
        renderTerminalPanelPage()
    }

    private fun handlePanelSwipe(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                panelSwipeStartX = event.x
                return true
            }

            MotionEvent.ACTION_UP -> {
                val delta = event.x - panelSwipeStartX
                if (kotlin.math.abs(delta) > dp(48)) {
                    if (delta < 0) {
                        terminalPanelPageIndex = (terminalPanelPageIndex + 1).coerceAtMost(2)
                    } else {
                        terminalPanelPageIndex = (terminalPanelPageIndex - 1).coerceAtLeast(0)
                    }
                    renderTerminalPanelPage()
                    return true
                }
            }
        }
        return false
    }

    private fun renderPanelIndicator() {
        terminalPanelIndicator.removeAllViews()
        terminalPanelIndicator.addView(panelPageArrow("‹") {
            terminalPanelPageIndex = (terminalPanelPageIndex - 1).coerceAtLeast(0)
            renderTerminalPanelPage()
        })
        repeat(3) { index ->
            val dot = TextView(requireContext()).apply {
                text = if (index == terminalPanelPageIndex) "●" else "●"
                textSize = if (index == terminalPanelPageIndex) 12f else 10f
                setTextColor(
                    color(
                        if (index == terminalPanelPageIndex) {
                            R.color.terminal_page_green
                        } else {
                            R.color.terminal_page_gray_chip
                        }
                    )
                )
                setPadding(dp(3), 0, dp(3), 0)
            }
            terminalPanelIndicator.addView(dot)
        }
        terminalPanelIndicator.addView(panelPageArrow("›") {
            terminalPanelPageIndex = (terminalPanelPageIndex + 1).coerceAtMost(2)
            renderTerminalPanelPage()
        })
    }

    private fun panelPageArrow(label: String, action: () -> Unit): TextView {
        return TextView(requireContext()).apply {
            text = label
            textSize = 18f
            gravity = Gravity.CENTER
            setTextColor(color(R.color.terminal_page_subtext))
            setPadding(dp(14), 0, dp(14), 0)
            setOnClickListener { action() }
        }
    }

    private fun bindRepeatingKey(tile: MaterialCardView, keyCode: Int, fallbackSequence: String) {
        tile.setOnClickListener {
            sendTerminalKeyEvent(keyCode, fallbackSequence)
        }
        tile.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    view.isPressed = true
                    sendTerminalKeyEvent(keyCode, fallbackSequence)
                    startRepeatingKey(keyCode, fallbackSequence)
                    true
                }

                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL -> {
                    view.isPressed = false
                    stopRepeatingKey()
                    true
                }

                else -> true
            }
        }
    }

    private fun startRepeatingKey(keyCode: Int, fallbackSequence: String) {
        stopRepeatingKey()
        val repeat = object : Runnable {
            override fun run() {
                sendTerminalKeyEvent(keyCode, fallbackSequence)
                keyRepeatHandler.postDelayed(this, 160L)
            }
        }
        keyRepeatRunnable = repeat
        keyRepeatHandler.postDelayed(repeat, 360L)
    }

    private fun stopRepeatingKey() {
        keyRepeatRunnable?.let(keyRepeatHandler::removeCallbacks)
        keyRepeatRunnable = null
    }

    private fun sendTerminalKeyEvent(keyCode: Int, fallbackSequence: String) {
        val down = KeyEvent(KeyEvent.ACTION_DOWN, keyCode)
        val up = KeyEvent(KeyEvent.ACTION_UP, keyCode)
        val handled = terminalView.dispatchKeyEvent(down)
        terminalView.dispatchKeyEvent(up)
        if (!handled) {
            sendTerminalInput(fallbackSequence)
        }
        focusComposerInput(showKeyboard = false)
        keepLatestTerminalOutputVisible(forceImmediate = true)
    }

    private fun submitComposerOrSendEnter() {
        if (terminalComposerInput.text?.isNotBlank() == true) {
            submitComposerInput()
        } else {
            sendTerminalInput("\r")
        }
    }

    private fun submitComposerInput() {
        val text = terminalComposerInput.text?.toString().orEmpty()
        if (text.isBlank()) {
            sendTerminalInput("\r")
            return
        }
        val normalizedText = normalizeComposerSubmitText(text)
        if (composerLiveSyncEnabled) {
            syncComposerLiveDiff(normalizedText)
            sendTerminalInput("\r")
        } else {
            sendTerminalPaste(normalizedText + "\r")
        }
        resetComposerAfterSubmit()
        updateComposerScrollState()
        focusComposerInput(showKeyboard = true)
    }

    private fun normalizeComposerSubmitText(text: String): String {
        return text
            .trimEnd('\r', '\n')
            .replace(Regex("\\r\\n|\\r|\\n"), "\\\\n")
    }

    private fun resetComposerAfterSubmit() {
        composerLiveSyncBuffer = ""
        composerLiveSyncEnabled = false
        suppressComposerWatcher = true
        try {
            terminalComposerInput.clearComposingText()
            terminalComposerInput.text?.clear()
        } finally {
            suppressComposerWatcher = false
        }
    }

    private fun resetComposerAfterRealtimeControl() {
        composerLiveSyncBuffer = ""
        composerLiveSyncEnabled = false
        suppressComposerWatcher = true
        try {
            terminalComposerInput.clearComposingText()
            terminalComposerInput.text?.clear()
        } finally {
            suppressComposerWatcher = false
        }
        terminalComposerInput.post { updateComposerScrollState() }
    }

    private fun handleComposerRealtimeInput(text: String) {
        if (terminalComposerInput.hasFocus() && shouldUseRealtimeComposerSync(text)) {
            composerLiveSyncEnabled = true
            syncComposerLiveDiff(text)
            return
        }
        clearComposerLiveEchoIfNeeded()
        composerLiveSyncEnabled = false
    }

    private fun syncComposerLiveDiff(text: String) {
        val previous = composerLiveSyncBuffer
        if (previous == text) {
            return
        }
        val commonPrefixLength = previous.commonPrefixWith(text).length
        val deleteCount = previous.length - commonPrefixLength
        val appendText = text.substring(commonPrefixLength)
        val delta = buildString {
            repeat(deleteCount) {
                append('\u007f')
            }
            append(appendText)
        }
        if (delta.isNotEmpty()) {
            terminalController.writeRawInput(delta)
            followTerminalOutput = true
            keepLatestTerminalOutputVisible(forceImmediate = true)
        }
        composerLiveSyncBuffer = text
    }

    private fun shouldUseRealtimeComposerSync(text: String): Boolean {
        return text.startsWith("/") && !text.contains('\n') && !text.contains('\r')
    }

    private fun clearComposerLiveEchoIfNeeded() {
        if (!composerLiveSyncEnabled || composerLiveSyncBuffer.isEmpty()) {
            composerLiveSyncBuffer = ""
            return
        }
        terminalController.writeRawInput("\u007f".repeat(composerLiveSyncBuffer.length))
        composerLiveSyncBuffer = ""
        followTerminalOutput = true
        keepLatestTerminalOutputVisible(forceImmediate = true)
    }

    private data class PanelButton(
        val title: String,
        val subtitle: String,
        val action: (View) -> Unit
    )

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    private fun color(resId: Int): Int {
        return KiteTerminalShellTheme.resolve(requireContext(), resId)
    }

    private fun applyShellThemeToStaticViews(root: View) {
        fun tintHeader(header: View?) {
            header?.setBackgroundColor(color(R.color.terminal_page_header))
            if (header is ViewGroup) {
                for (index in 0 until header.childCount) {
                    when (val child = header.getChildAt(index)) {
                        is TextView -> child.setTextColor(color(R.color.terminal_page_text))
                        is AppCompatImageButton -> child.setColorFilter(color(R.color.terminal_page_text))
                    }
                }
            }
        }

        root.setBackgroundColor(color(R.color.terminal_page_bg))
        listPage.setBackgroundColor(color(R.color.terminal_page_surface))
        detailPage.setBackgroundColor(color(R.color.terminal_page_bg))
        tintHeader((listPage as? ViewGroup)?.getChildAt(0))
        tintHeader((detailPage as? ViewGroup)?.getChildAt(0))
        root.findViewById<View>(R.id.terminalOutputContainer)
            ?.setBackgroundColor(color(R.color.terminal_page_surface))
        terminalInputBar.setBackgroundColor(color(R.color.terminal_page_header))
        tvEmptySessions.setTextColor(color(R.color.terminal_page_subtext))
        tvDetailTitle.setTextColor(color(R.color.terminal_page_text))
        tvSessionNote.setTextColor(color(R.color.terminal_page_subtext))
        tvBootstrapTitle.setTextColor(color(R.color.terminal_page_text))
        tvBootstrapDetail.setTextColor(color(R.color.terminal_page_subtext))
        (cardBootstrapStatus as? MaterialCardView)?.apply {
            setCardBackgroundColor(color(R.color.terminal_page_surface))
            strokeColor = color(R.color.terminal_page_line)
        }
    }

    private class SpaceView(context: Context) : View(context)

    private fun setupWindowInsets(root: View) {
        val listInitialBottomPadding = listPage.paddingBottom
        val detailInitialBottomPadding = detailPage.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val systemInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())
            listPage.setPadding(
                listPage.paddingLeft,
                listPage.paddingTop,
                listPage.paddingRight,
                listInitialBottomPadding + systemInsets.bottom
            )
            detailPage.setPadding(
                detailPage.paddingLeft,
                detailPage.paddingTop,
                detailPage.paddingRight,
                detailInitialBottomPadding + maxOf(systemInsets.bottom, imeInsets.bottom)
            )
            insets
        }
    }

    private fun requestTerminalRefresh(
        reason: String,
        force: Boolean = false,
        userVisible: Boolean = false
    ) {
        val appContext = requireContext().applicationContext
        if (terminalRefreshJob?.isActive == true) {
            pendingTerminalRefresh = true
            if (userVisible && ::terminalListRefresh.isInitialized) {
                terminalListRefresh.isRefreshing = true
            }
            Logger.i("TerminalFragment", "终端刷新已排队: reason=$reason")
            return
        }
        val now = SystemClock.uptimeMillis()
        if (!force && now - lastTerminalRefreshRequestedAtMs < TERMINAL_REFRESH_MIN_INTERVAL_MS) {
            Logger.i("TerminalFragment", "跳过终端刷新: reason=$reason, recent=true")
            return
        }
        if (userVisible && ::terminalListRefresh.isInitialized) {
            terminalListRefresh.isRefreshing = true
        }
        terminalRefreshJob = viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                var queuedReason = reason
                var runForce = force
                do {
                    pendingTerminalRefresh = false
                    val waitMs = if (runForce) {
                        (
                            lastTerminalRefreshRequestedAtMs +
                                TERMINAL_REFRESH_MIN_INTERVAL_MS -
                                SystemClock.uptimeMillis()
                            ).coerceAtLeast(0L)
                    } else {
                        0L
                    }
                    if (waitMs > 0L) {
                        delay(waitMs)
                    }
                    lastTerminalRefreshRequestedAtMs = SystemClock.uptimeMillis()
                    Logger.i("TerminalFragment", "执行终端刷新: reason=$queuedReason")
                    TerminalRuntimeHost.refreshRuntimeSnapshot(appContext)
                    TerminalSessionStore.refresh(appContext, force = runForce)
                    if (pendingTerminalRefresh) {
                        queuedReason = "queued-terminal-refresh"
                        runForce = true
                    }
                } while (pendingTerminalRefresh)
            } finally {
                withContext(Dispatchers.Main) {
                    if (::terminalListRefresh.isInitialized) {
                        terminalListRefresh.isRefreshing = false
                    }
                }
                terminalRefreshJob = null
            }
        }
    }

    private fun applyFontSize(newSizeDp: Int, announce: Boolean = false) {
        currentFontSizeDp = newSizeDp
        terminalView.setTextSize(currentFontSizeDp)
        TerminalUiPreferences.saveFontSizeDp(requireContext().applicationContext, currentFontSizeDp)
        keepLatestTerminalOutputVisible(forceImmediate = true)
        if (announce) {
            Toast.makeText(
                requireContext(),
                getString(R.string.terminal_font_changed, currentFontSizeDp),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun updateThemeModeLabel() {
        if (!::btnThemeMode.isInitialized) {
            return
        }
        val label = TerminalUiPreferences.loadThemeMode(requireContext().applicationContext).label
        btnThemeMode.text = "${getString(R.string.terminal_theme_button)} · $label"
    }

    private fun showThemeMenu(anchor: View) {
        PopupMenu(requireContext(), anchor, Gravity.END).apply {
            menu.add(Menu.NONE, TerminalThemeMode.SYSTEM.ordinal, Menu.NONE, getString(R.string.terminal_theme_system))
            menu.add(Menu.NONE, TerminalThemeMode.DARK.ordinal, Menu.NONE, getString(R.string.terminal_theme_dark))
            menu.add(Menu.NONE, TerminalThemeMode.LIGHT.ordinal, Menu.NONE, getString(R.string.terminal_theme_light))
            setOnMenuItemClickListener(::handleThemeMenuItem)
            show()
        }
    }

    private fun handleThemeMenuItem(item: MenuItem): Boolean {
        val mode = TerminalThemeMode.entries.firstOrNull { it.ordinal == item.itemId } ?: return false
        TerminalUiPreferences.saveThemeMode(requireContext().applicationContext, mode)
        updateThemeModeLabel()
        applyTerminalColorScheme()
        terminalView.mTermSession?.emulator?.mColors?.reset()
        refreshTerminalColors()
        keepLatestTerminalOutputVisible()
        return true
    }

    private fun sendTerminalInput(rawInput: String) {
        terminalController.writeRawInput(rawInput)
        if (composerLiveSyncEnabled && shouldResetComposerAfterControlInput(rawInput)) {
            resetComposerAfterRealtimeControl()
        }
        followTerminalOutput = true
        keepLatestTerminalOutputVisible()
    }

    private fun shouldResetComposerAfterControlInput(rawInput: String): Boolean {
        return when (rawInput) {
            "\r" -> true

            else -> false
        }
    }

    private fun sendTerminalPaste(rawInput: String) {
        terminalController.writePastedInput(rawInput)
        followTerminalOutput = true
        keepLatestTerminalOutputVisible(forceImmediate = true)
    }

    private fun keepLatestTerminalOutputVisible(forceImmediate: Boolean = false) {
        uiRefreshRequested.incrementAndGet()
        synchronized(terminalRefreshLock) {
            uiRefreshDirty = true
            if (uiRefreshScheduled) {
                uiRefreshCoalesced.incrementAndGet()
            }
        }
        scheduleTerminalViewRefreshIfNeeded(forceImmediate)
    }

    private fun scheduleTerminalViewRefreshIfNeeded(forceImmediate: Boolean = false) {
        if (view == null) {
            synchronized(terminalRefreshLock) {
                uiRefreshScheduled = false
            }
            return
        }
        var delayMs = 0L
        var shouldSchedule = false
        synchronized(terminalRefreshLock) {
            if (!uiRefreshDirty || uiRefreshScheduled) {
                return
            }
            val now = SystemClock.uptimeMillis()
            val elapsed = now - lastUiRefreshAtMs
            delayMs = if (forceImmediate || lastUiRefreshAtMs == 0L || elapsed >= MIN_TERMINAL_REFRESH_INTERVAL_MS) {
                0L
            } else {
                MIN_TERMINAL_REFRESH_INTERVAL_MS - elapsed
            }
            if (delayMs > 0L) {
                uiRefreshRateLimited.incrementAndGet()
            }
            uiRefreshDirty = false
            uiRefreshScheduled = true
            shouldSchedule = true
        }
        if (!shouldSchedule) {
            return
        }
        if (delayMs > 0L) {
            terminalView.postDelayed(terminalViewRefreshRunnable, delayMs)
        } else if (Looper.myLooper() == Looper.getMainLooper()) {
            terminalViewRefreshRunnable.run()
        } else {
            terminalView.post(terminalViewRefreshRunnable)
        }
    }

    private fun performTerminalViewRefresh() {
        if (isDetailMode && followTerminalOutput && !terminalView.isSelectingText() && terminalView.topRow != 0) {
            terminalView.topRow = 0
        }
        terminalView.onScreenUpdated()
        terminalView.invalidate()
    }

    private fun buildUiRefreshDebugSummary(): String {
        return "terminal-ui-refresh " +
            "uiRefreshRequested=${uiRefreshRequested.get()} " +
            "uiRefreshExecuted=${uiRefreshExecuted.get()} " +
            "uiRefreshCoalesced=${uiRefreshCoalesced.get()} " +
            "uiRefreshRateLimited=${uiRefreshRateLimited.get()} " +
            "currentMinRefreshIntervalMs=$MIN_TERMINAL_REFRESH_INTERVAL_MS " +
            "lastUiRefreshAtMs=$lastUiRefreshAtMs"
    }

    private fun showActionSheet() {
        if (childFragmentManager.findFragmentByTag("terminal-actions") != null) {
            return
        }
        TerminalActionSheet().show(childFragmentManager, "terminal-actions")
    }

    private fun showEntrySheet() {
        if (childFragmentManager.findFragmentByTag("terminal-entries") != null) {
            return
        }
        TerminalEntrySheet().show(childFragmentManager, "terminal-entries")
    }

    private fun runQuickCommand(command: String) {
        Logger.i("Terminal", "触发更多操作里的 Shell 动作: $command")
        terminalController.sendCommand(command)
        showDetailPage()
    }

    private fun runBuildAction(label: String, command: String) {
        Logger.i("Terminal", "触发构建快捷动作: $label, command=$command")
        val routeLabel = WorkSurfaceRuntimeBridge.actionRouteLabel(RuntimeActionKind.MOBILE_BUILD)
        // 任务入口层只表达“用户想触发哪个工作面动作”，具体会话与命令路由继续交给 TerminalSessionController。
        terminalController.runCommandInPrimaryShell(
            command = command,
            note = "${routeLabel}：已切回主终端，开始执行${label}。"
        )
        showDetailPage()
    }

    private fun runSmokeScript() {
        Logger.i("Terminal", "触发终端基础验收脚本")
        terminalController.sendCommand(
            """
            printf '\n[SMOKE] 容器基础验收开始\n'
            whoami
            uname -a
            pwd
            ls /workspace
            node --version
            claude auth status
            printf '[SMOKE] 容器基础验收结束\n'
            """.trimIndent()
        )
        showDetailPage()
    }

    private fun observeContainerState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                WorkSurfaceRuntimeBridge.containerState.collect { container ->
                    renderContainerState(container)
                }
            }
        }
    }

    private fun observeSpaceState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                KFWorkspaceManager.currentSpaceState.collect { space ->
                    currentSpace = space
                    currentAgents = if (space == null) {
                        emptyList()
                    } else {
                        KFWorkspaceManager.listAgentRuntimes(
                            requireContext().applicationContext,
                            space.id
                        )
                    }
                    renderManagedSessions()
                }
            }
        }
    }

    private fun observeTerminalSessions() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                TerminalSessionStore.snapshot.collect { snapshot ->
                    currentTerminalSnapshot = snapshot
                    terminalListRefresh.isRefreshing = false
                    renderManagedSessions()
                }
            }
        }
    }

    private fun observeBootstrapState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                BootstrapCoordinator.snapshot.collect { snapshot ->
                    renderBootstrapState(snapshot)
                }
            }
        }
    }

    private fun renderContainerState(container: ContainerRecord?) {
        val error = container?.lastError?.takeIf { it.isNotBlank() } ?: return
        showSessionNote("容器提示：$error")
    }

    private fun renderBootstrapState(snapshot: BootstrapSnapshot) {
        when (snapshot.stage) {
            BootstrapStage.IDLE,
            BootstrapStage.READY -> {
                cardBootstrapStatus.visibility = View.GONE
            }

            BootstrapStage.FAILED -> {
                cardBootstrapStatus.visibility = View.VISIBLE
                progressBootstrap.visibility = View.GONE
                tvBootstrapTitle.text = "初始化失败"
                tvBootstrapDetail.text = snapshot.lastError ?: "初始化过程中出现未知错误。"
            }

            else -> {
                cardBootstrapStatus.visibility = View.VISIBLE
                progressBootstrap.visibility = View.VISIBLE
                tvBootstrapTitle.text = when (snapshot.stage) {
                    BootstrapStage.SERVICE_REQUESTED -> "正在唤起运行环境"
                    BootstrapStage.ROOTFS_EXTRACTING -> "正在解压系统镜像"
                    BootstrapStage.BASE_BOOTSTRAP -> "正在初始化基础环境"
                    BootstrapStage.SPACE_READY -> "正在准备工作区"
                    BootstrapStage.TERMINAL_WARMING -> "正在预热主终端"
                    else -> getString(R.string.terminal_bootstrap_progress_title)
                }
                tvBootstrapDetail.text = when (snapshot.stage) {
                    BootstrapStage.ROOTFS_EXTRACTING ->
                        "首次安装会先解压系统镜像，请稍候。"
                    BootstrapStage.BASE_BOOTSTRAP ->
                        "正在初始化基础环境和工具链，完成后会自动继续。"
                    BootstrapStage.SPACE_READY ->
                        "正在准备标准工作区和构建辅助目录。"
                    BootstrapStage.TERMINAL_WARMING ->
                        "正在恢复主终端状态，请稍候。"
                    BootstrapStage.SERVICE_REQUESTED ->
                        "后台服务正在接管终端环境。"
                    else -> getString(R.string.terminal_bootstrap_progress_detail)
                }
            }
        }
    }

    private fun renderManagedSessions() {
        if (view == null) {
            return
        }

        val space = currentSpace
        if (space == null) {
            if (lastManagedSessionsRenderSignature == "space:none") {
                return
            }
            lastManagedSessionsRenderSignature = "space:none"
            terminalListContainer.removeAllViews()
            tvEmptySessions.visibility = View.VISIBLE
            tvDetailTitle.text = getString(R.string.terminal_title_short)
            return
        }

        val snapshot = currentTerminalSnapshot
        val activeSessionId = terminalController.getActiveSessionId()
            ?: snapshot.currentViewedSessionId
            ?: space.currentTerminalSessionId
        val activeSession = sequenceOf(snapshot.primaryEntry)
            .filterNotNull()
            .plus(snapshot.sessions.asSequence())
            .firstOrNull { it.id == activeSessionId }

        val renderSignature = buildManagedSessionsRenderSignature(space, snapshot, activeSessionId)
        if (!isDetailMode && renderSignature != lastManagedSessionsRenderSignature) {
            renderTerminalSessions(snapshot)
            lastManagedSessionsRenderSignature = renderSignature
        }
        renderDetailHeader(activeSession?.title)
    }

    private fun renderTerminalSessions(snapshot: TerminalSessionsSnapshot) {
        terminalListContainer.removeAllViews()

        val additionalSessions = snapshot.liveSessions
            .sortedWith(
                compareByDescending<TerminalSessionItem> { it.isInputReady || it.allowsQueuedInput }
                    .thenByDescending { it.lastAttachedAt ?: it.lastStartedAt ?: it.createdAt }
            )
        Logger.i(
            "TerminalFragment",
            "渲染终端列表: primary=${snapshot.primaryEntry?.title ?: getString(R.string.terminal_primary_title)}, extra=${additionalSessions.joinToString { "${it.title}:${it.status.name}" }}, all=${snapshot.sessions.joinToString { "${it.title}:${it.status.name}" }}"
        )
        tvEmptySessions.visibility = View.GONE

        if (additionalSessions.isEmpty()) {
            terminalListContainer.addView(createPrimarySessionEntry(snapshot.primaryEntry))
        } else {
            additionalSessions.forEach { session ->
                terminalListContainer.addView(createSessionListItem(session))
            }
        }
    }

    private fun createPrimarySessionEntry(primaryEntry: TerminalSessionItem?): View {
        val listPrimaryEntry = primaryEntry?.takeUnless { it.isTerminalListArchived() }
        val effectiveStatus = when (listPrimaryEntry?.status) {
            ManagedTerminalStatus.RUNNING,
            ManagedTerminalStatus.ATTACHED,
            ManagedTerminalStatus.REGISTERED,
            ManagedTerminalStatus.FROZEN,
            ManagedTerminalStatus.STOPPED,
            ManagedTerminalStatus.EXITED,
            ManagedTerminalStatus.FAILED -> ManagedTerminalStatus.REGISTERED
            null -> ManagedTerminalStatus.REGISTERED
        }
        val badge = resolveSessionBadge(
            status = effectiveStatus,
            isActive = listPrimaryEntry?.isCurrentViewed == true
        )
        val timestamp = resolveSessionTimestamp(listPrimaryEntry)

        return buildSessionListItem(
            title = listPrimaryEntry?.title ?: getString(R.string.terminal_primary_title),
            timeText = timestamp?.let(::formatSessionTime),
            badge = badge,
            statusLabelOverride = listPrimaryEntry?.statusLabel,
            onClick = {
                terminalController.openPrimaryShellEntry()
                showDetailPage()
            }
        )
    }

    private fun createSessionListItem(session: TerminalSessionItem): View {
        val badge = resolveSessionBadge(session.status, session.isCurrentViewed)
        val timestamp = resolveSessionTimestamp(session)
        return buildSessionListItem(
            title = session.title,
            timeText = timestamp?.let(::formatSessionTime),
            badge = badge,
            statusLabelOverride = session.statusLabel,
            onClick = {
                terminalController.switchToSession(session.id)
                showDetailPage()
            }
        )
    }

    private fun buildSessionListItem(
        title: String,
        timeText: String?,
        badge: Triple<Int, Int, String>,
        statusLabelOverride: String? = null,
        onClick: () -> Unit
    ): View {
        val itemView = layoutInflater.inflate(
            R.layout.item_terminal_session,
            terminalListContainer,
            false
        )
        val root = itemView.findViewById<View>(R.id.sessionRowRoot)
        val iconCard = itemView.findViewById<MaterialCardView>(R.id.cardSessionItemIcon)
        val iconView = itemView.findViewById<ImageView>(R.id.ivSessionItemIcon)
        val chevronView = itemView.findViewById<ImageView>(R.id.ivSessionItemChevron)
        val divider = itemView.findViewById<View>(R.id.sessionItemDivider)
        val statusCard = itemView.findViewById<MaterialCardView>(R.id.cardSessionItemStatus)
        val titleView = itemView.findViewById<TextView>(R.id.tvSessionItemTitle)
        val timeView = itemView.findViewById<TextView>(R.id.tvSessionItemTime)
        val statusView = itemView.findViewById<TextView>(R.id.tvSessionItemStatus)

        val (bgColorRes, textColorRes, labelText) = badge
        root.setBackgroundColor(color(R.color.terminal_page_surface))
        iconCard.setCardBackgroundColor(color(R.color.terminal_page_green))
        iconView.setColorFilter(color(R.color.terminal_page_surface))
        chevronView.setColorFilter(color(R.color.terminal_page_subtext))
        divider.setBackgroundColor(color(R.color.terminal_page_line))
        statusCard.setCardBackgroundColor(color(bgColorRes))
        statusView.setTextColor(color(textColorRes))
        titleView.setTextColor(color(R.color.terminal_page_text))
        timeView.setTextColor(color(R.color.terminal_page_subtext))

        titleView.text = title
        if (timeText.isNullOrBlank()) {
            timeView.visibility = View.INVISIBLE
            timeView.text = ""
        } else {
            timeView.visibility = View.VISIBLE
            timeView.text = timeText
        }
        statusView.text = statusLabelOverride?.takeIf { it.isNotBlank() } ?: labelText

        root.setOnClickListener { onClick() }
        return itemView
    }

    private fun resolveSessionBadge(
        status: ManagedTerminalStatus?,
        isActive: Boolean
    ): Triple<Int, Int, String> {
        return when (status) {
            ManagedTerminalStatus.FAILED ->
                Triple(R.color.error, R.color.terminal_page_surface, getString(R.string.terminal_status_failed))

            ManagedTerminalStatus.EXITED ->
                Triple(R.color.terminal_page_gray_chip, R.color.terminal_page_surface, getString(R.string.terminal_status_exited))

            ManagedTerminalStatus.STOPPED ->
                Triple(R.color.terminal_page_gray_chip, R.color.terminal_page_surface, getString(R.string.terminal_status_exited))

            ManagedTerminalStatus.FROZEN ->
                Triple(R.color.terminal_page_gray_chip, R.color.terminal_page_text, getString(R.string.terminal_status_idle))

            ManagedTerminalStatus.RUNNING ->
                if (isActive) {
                    Triple(
                        R.color.terminal_page_green,
                        R.color.terminal_page_surface,
                        getString(R.string.terminal_status_running)
                    )
                } else {
                    Triple(
                        R.color.terminal_page_blue,
                        R.color.terminal_page_surface,
                        getString(R.string.terminal_status_connected)
                    )
                }

            ManagedTerminalStatus.ATTACHED ->
                Triple(R.color.terminal_page_blue, R.color.terminal_page_surface, getString(R.string.terminal_status_connected))

            else ->
                Triple(R.color.terminal_page_gray_chip, R.color.terminal_page_surface, getString(R.string.terminal_status_idle))
        }
    }

    private fun resolveSessionTimestamp(session: TerminalSessionItem?): Long? {
        if (session == null) {
            return null
        }
        return session.lastExitedAt
            ?: session.lastStartedAt
            ?: session.lastAttachedAt
            ?: session.createdAt
    }

    private fun TerminalSessionItem.isTerminalListArchived(): Boolean {
        return status == ManagedTerminalStatus.STOPPED ||
            status == ManagedTerminalStatus.EXITED ||
            status == ManagedTerminalStatus.FAILED
    }

    private fun formatSessionTime(timestamp: Long): String {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply { timeInMillis = timestamp }

        val sameYear = now.get(Calendar.YEAR) == target.get(Calendar.YEAR)
        val sameDay = sameYear && now.get(Calendar.DAY_OF_YEAR) == target.get(Calendar.DAY_OF_YEAR)
        if (sameDay) {
            return DateFormat.format("HH:mm", timestamp).toString()
        }

        now.add(Calendar.DAY_OF_YEAR, -1)
        val yesterday = sameYear && now.get(Calendar.DAY_OF_YEAR) == target.get(Calendar.DAY_OF_YEAR)
        if (yesterday) {
            return getString(R.string.terminal_time_yesterday)
        }

        val diffDays = ((System.currentTimeMillis() - timestamp) / (24 * 60 * 60 * 1000)).toInt()
        if (diffDays in 2..6) {
            return DateFormat.format("EEEE", timestamp).toString()
        }

        return DateFormat.format("MM-dd", timestamp).toString()
    }

    private fun renderDetailHeader(activeSessionTitle: String?) {
        tvDetailTitle.text = activeSessionTitle ?: getString(R.string.terminal_title_short)
    }

    private fun launchAgentInDedicatedSession(agent: AgentRuntimeRecord) {
        Logger.i("Terminal", "从参考入口打开独立智能体会话: ${agent.displayName}")
        WorkstationActionGateway.launchAgentSession(requireContext().applicationContext, agent.id)
        showDetailPage()
    }

    fun openSessionFromExternal(sessionId: String) {
        terminalController.switchToSession(sessionId)
        showDetailPage()
    }

    private fun showListPage() {
        isDetailMode = false
        detailBackCallback.isEnabled = false
        hideSoftKeyboard(terminalComposerInput)
        hideSoftKeyboard(terminalView)
        listPage.visibility = View.VISIBLE
        detailPage.visibility = View.GONE
        (activity as? TerminalChromeHost)?.setTerminalDetailMode(false)
        stopRepeatingKey()
        lastManagedSessionsRenderSignature = ""
        renderManagedSessions()
        requestTerminalRefresh("show-list")
    }

    private fun showDetailPage() {
        isDetailMode = true
        detailBackCallback.isEnabled = true
        listPage.visibility = View.GONE
        detailPage.visibility = View.VISIBLE
        (activity as? TerminalChromeHost)?.setTerminalDetailMode(true)
        terminalView.post {
            focusComposerInput(showKeyboard = false)
            keepLatestTerminalOutputVisible(forceImmediate = true)
        }
    }

    private fun buildManagedSessionsRenderSignature(
        space: SpaceRecord,
        snapshot: TerminalSessionsSnapshot,
        activeSessionId: String?
    ): String {
        return buildString {
            append(space.id)
            append('|')
            append(activeSessionId ?: "none")
            append('|')
            snapshot.primaryEntry?.let { appendSessionRenderSignature(it) }
            append('|')
            snapshot.liveSessions.forEach { appendSessionRenderSignature(it) }
            append('|')
            snapshot.sessions.forEach { appendSessionRenderSignature(it) }
        }
    }

    private fun StringBuilder.appendSessionRenderSignature(session: TerminalSessionItem) {
        append(session.id)
        append(':')
        append(session.title)
        append(':')
        append(session.status.name)
        append(':')
        append(session.statusLabel)
        append(':')
        append(session.isCurrentViewed)
        append(':')
        append(session.isInputReady)
        append(':')
        append(session.allowsQueuedInput)
        append(':')
        append(session.lastAttachedAt ?: 0L)
        append(':')
        append(session.lastStartedAt ?: 0L)
        append(':')
        append(session.lastExitedAt ?: 0L)
        append(';')
    }

    private fun focusComposerInput(showKeyboard: Boolean) {
        terminalSelectionFocusAllowed = false
        terminalView.clearFocus()
        terminalComposerInput.requestFocus()
        if (showKeyboard) {
            showSoftKeyboard(terminalComposerInput)
        }
    }

    private fun showSoftKeyboard(target: View) {
        val imm = context?.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.showSoftInput(target, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun copyToClipboard(text: String) {
        val clipboard = context?.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(ClipData.newPlainText("terminal", text))
    }

    private fun pasteFromClipboard() {
        val clipboard = context?.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val text = clipboard?.primaryClip?.getItemAt(0)?.coerceToText(requireContext())?.toString()
        if (!text.isNullOrEmpty()) {
            val editable = terminalComposerInput.text
            val insertAt = terminalComposerInput.selectionStart.coerceIn(0, editable?.length ?: 0)
            editable?.insert(insertAt, text)
            focusComposerInput(showKeyboard = true)
        }
    }

    private fun showFullTranscriptSelectionDialog() {
        val transcript = terminalView.mEmulator?.screen?.transcriptText?.trim()
        if (transcript.isNullOrBlank()) {
            Toast.makeText(requireContext(), getString(R.string.terminal_copy_empty), Toast.LENGTH_SHORT).show()
            return
        }
        val preview = EditText(requireContext()).apply {
            setText(transcript)
            setTextColor(color(R.color.terminal_page_text))
            setHintTextColor(color(R.color.terminal_page_subtext))
            setTextIsSelectable(true)
            setSingleLine(false)
            minLines = 8
            maxLines = 18
            typeface = Typeface.MONOSPACE
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            gravity = Gravity.START or Gravity.TOP
            setHorizontallyScrolling(false)
            setShowSoftInputOnFocus(false)
            background = null
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.terminal_select_all_title))
            .setView(preview)
            .setPositiveButton(android.R.string.ok, null)
            .show()
        preview.post {
            preview.requestFocus()
            preview.selectAll()
        }
    }

    private fun copyCurrentScreenText() {
        val emulator = terminalView.mEmulator
        if (emulator == null) {
            Toast.makeText(requireContext(), getString(R.string.terminal_copy_empty), Toast.LENGTH_SHORT).show()
            return
        }
        val cols = emulator.mColumns
        val rows = emulator.mRows
        val topRow = terminalView.topRow
        val screenText = emulator.screen.getSelectedText(0, topRow, cols, topRow + rows)?.trim()
        if (screenText.isNullOrBlank()) {
            Toast.makeText(requireContext(), getString(R.string.terminal_copy_empty), Toast.LENGTH_SHORT).show()
            return
        }
        copyToClipboard(screenText)
        Toast.makeText(requireContext(), getString(R.string.terminal_copy_success), Toast.LENGTH_SHORT).show()
    }

    private fun copyFullTranscriptText() {
        val transcript = terminalView.mEmulator?.screen?.transcriptText?.trim()
        if (transcript.isNullOrBlank()) {
            Toast.makeText(requireContext(), getString(R.string.terminal_copy_empty), Toast.LENGTH_SHORT).show()
            return
        }
        copyToClipboard(transcript)
        Toast.makeText(requireContext(), getString(R.string.terminal_copy_success), Toast.LENGTH_SHORT).show()
    }

    private fun hideSoftKeyboard(target: View) {
        val imm = context?.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(target.windowToken, 0)
    }

    private fun shouldDisplaySessionNote(message: String): Boolean {
        val keywords = listOf("失败", "错误", "未找到", "没有", "提示", "退出")
        return keywords.any { message.contains(it) }
    }

    override fun onDestroyView() {
        (activity as? TerminalChromeHost)?.setTerminalDetailMode(false)
        // 这里只是 UI 脱离当前终端，不是关闭终端会话。
        TerminalRuntimeHost.detachUi(this)
        terminalView.removeCallbacks(terminalViewRefreshRunnable)
        synchronized(terminalRefreshLock) {
            uiRefreshScheduled = false
            uiRefreshDirty = false
        }
        terminalView.setTerminalCursorBlinkerState(false, false)
        super.onDestroyView()
    }

    override fun onScale(scale: Float): Float {
        applyFontSize(TerminalUiPreferences.scaleFontSize(currentFontSizeDp, scale))
        return 1f
    }

    override fun onSingleTapUp(e: MotionEvent) {
        if (terminalView.isSelectingText()) {
            return
        }
        terminalComposerInput.post {
            focusComposerInput(showKeyboard = true)
        }
    }

    override fun shouldBackButtonBeMappedToEscape(): Boolean = false

    override fun shouldEnforceCharBasedInput(): Boolean = false

    override fun shouldUseCtrlSpaceWorkaround(): Boolean = false

    override fun isTerminalViewSelected(): Boolean = false

    override fun copyModeChanged(copyMode: Boolean) {
        terminalSelectionFocusAllowed = copyMode
        if (copyMode) {
            showSessionNote("当前处于文本选择模式，复制后可直接继续输入命令。")
        } else {
            terminalComposerInput.post {
                if (isDetailMode) {
                    focusComposerInput(showKeyboard = false)
                }
            }
        }
    }

    override fun onKeyDown(
        keyCode: Int,
        e: KeyEvent,
        session: TerminalSession
    ): Boolean {
        if (!terminalController.isActiveSession(session)) {
            return false
        }

        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            isCtrlPressed = true
            showSessionNote("音量上键临时映射为 Ctrl，松开后恢复。")
            return true
        }

        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            isAltPressed = true
            showSessionNote("音量下键临时映射为 Alt，松开后恢复。")
            return true
        }

        return false
    }

    override fun onKeyUp(keyCode: Int, e: KeyEvent): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> {
                isCtrlPressed = false
                showSessionNote("Ctrl 映射已释放。")
                return true
            }

            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                isAltPressed = false
                showSessionNote("Alt 映射已释放。")
                return true
            }
        }
        return false
    }

    override fun onLongPress(event: MotionEvent): Boolean {
        followTerminalOutput = false
        hideSoftKeyboard(terminalComposerInput)
        terminalSelectionFocusAllowed = true
        terminalView.requestFocus()
        terminalView.startTextSelectionMode(event)
        return true
    }

    override fun onCreateContextMenu(
        menu: ContextMenu,
        v: View,
        menuInfo: ContextMenu.ContextMenuInfo?
    ) {
        super.onCreateContextMenu(menu, v, menuInfo)
        if (v !== terminalView) {
            return
        }
        menu.clear()
        menu.add(Menu.NONE, TERMINAL_CONTEXT_COPY_SCREEN, Menu.NONE, getString(R.string.terminal_copy_transcript))
        menu.add(Menu.NONE, TERMINAL_CONTEXT_COPY_ALL, Menu.NONE, getString(R.string.terminal_copy_all_output))
    }

    override fun onContextItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            TERMINAL_CONTEXT_COPY_SCREEN -> {
                copyCurrentScreenText()
                true
            }
            TERMINAL_CONTEXT_COPY_ALL -> {
                copyFullTranscriptText()
                true
            }
            else -> super.onContextItemSelected(item)
        }
    }

    override fun readControlKey(): Boolean = isCtrlPressed

    override fun readAltKey(): Boolean = isAltPressed

    override fun readShiftKey(): Boolean = false

    override fun readFnKey(): Boolean = false

    override fun onCodePoint(
        codePoint: Int,
        ctrlDown: Boolean,
        session: TerminalSession
    ): Boolean {
        if (!terminalController.isActiveSession(session)) {
            return false
        }
        if (!terminalComposerInput.hasFocus()) {
            return true
        }
        terminalController.writeRawInput(encodeTerminalCodePoint(codePoint, ctrlDown))
        keepLatestTerminalOutputVisible()
        return true
    }

    private fun encodeTerminalCodePoint(codePoint: Int, ctrlDown: Boolean): String {
        if (ctrlDown) {
            val upper = Character.toUpperCase(codePoint)
            if (upper in 64..95) {
                return ((upper and 0x1f).toChar()).toString()
            }
            if (codePoint == '?'.code) {
                return "\u007f"
            }
        }
        if (codePoint == '\n'.code || codePoint == '\r'.code) {
            return "\r"
        }
        val chars = Character.toChars(codePoint)
        return String(chars, 0, chars.size)
    }

    override fun onEmulatorSet() {
        renderManagedSessions()
        terminalView.mTermSession?.emulator?.mColors?.reset()
        keepLatestTerminalOutputVisible()
    }

    override fun showSessionNote(message: String) {
        if (view == null) {
            return
        }
        sessionNoteJob?.cancel()
        if (message.isBlank() || !shouldDisplaySessionNote(message)) {
            tvSessionNote.visibility = View.GONE
            tvSessionNote.text = ""
        } else {
            tvSessionNote.visibility = View.VISIBLE
            tvSessionNote.text = message
            sessionNoteJob = viewLifecycleOwner.lifecycleScope.launch {
                delay(3200)
                if (view != null) {
                    tvSessionNote.visibility = View.GONE
                    tvSessionNote.text = ""
                }
            }
        }
    }

    override fun attachSession(session: TerminalSession) {
        applyTerminalColorScheme()
        terminalView.attachSession(session)
        session.emulator?.mColors?.reset()
        followTerminalOutput = true
        keepLatestTerminalOutputVisible()
    }

    override fun onManagedSessionsChanged() {
        renderManagedSessions()
    }

    override fun refreshTerminalView() {
        keepLatestTerminalOutputVisible(forceImmediate = isDetailMode)
    }

    override fun copyTextToClipboard(text: String) {
        copyToClipboard(text)
    }

    override fun pasteTextFromClipboard() {
        showFullTranscriptSelectionDialog()
    }

    override fun performBellFeedback() {
        view?.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    override fun refreshTerminalColors() {
        terminalView.invalidate()
        keepLatestTerminalOutputVisible()
    }

    override fun updateCursorState(state: Boolean) {
        terminalView.setTerminalCursorBlinkerState(state, true)
    }

    override fun onTerminalActionSelected(action: TerminalActionSheet.Action) {
        when (action) {
            TerminalActionSheet.Action.BUILD_DOCTOR ->
                runBuildAction("构建医生", "kf-gradle doctor")

            TerminalActionSheet.Action.BUILD_FAST_COMPILE ->
                runBuildAction("热身编译", "kf-gradle compile")

            TerminalActionSheet.Action.BUILD_DEBUG_APK ->
                runBuildAction("调试包构建", "kf-gradle assemble")

            TerminalActionSheet.Action.CLEAR_SCREEN ->
                sendTerminalInput("\u000c")

            TerminalActionSheet.Action.KILL_CURRENT_SESSION ->
                terminalController.getActiveSessionId()?.let { id ->
                    terminalController.endSession(id)
                }

            TerminalActionSheet.Action.SEND_CTRL_C ->
                sendTerminalInput("\u0003")

            TerminalActionSheet.Action.COPY_TRANSCRIPT -> copyCurrentScreenText()
            TerminalActionSheet.Action.DELETE_CURRENT_SESSION -> {
                terminalController.removeActiveSession()
                showListPage()
            }
        }
    }

    override fun onTerminalEntrySelected(action: TerminalEntrySheet.Action) {
        when (action) {
            TerminalEntrySheet.Action.NEW_SESSION -> {
                // 这里仍是入口层，只把"新建会话"转发给宿主服务，不在 Fragment 里拼终端运行逻辑。
                WorkstationActionGateway.createShellSession(requireContext().applicationContext)
                showDetailPage()
            }

            TerminalEntrySheet.Action.OPEN_CLAUDE -> openAgentEntry(AgentKind.CLAUDE_CODE)
            TerminalEntrySheet.Action.OPEN_CODEX -> openAgentEntry(AgentKind.CODEX)
            TerminalEntrySheet.Action.OPEN_OPENCLAW -> openAgentEntry(AgentKind.OPENCLAW)
            TerminalEntrySheet.Action.SEND_ENTER -> sendTerminalInput("\r")
        }
    }

    private fun openAgentEntry(agentKind: AgentKind) {
        val agent = currentAgents.firstOrNull { it.agentKind == agentKind }
        if (agent == null) {
            showSessionNote("当前空间里还没有这个智能体入口。")
            return
        }
        if (!WorkSurfaceRuntimeBridge.isCommandAvailable(requireContext().applicationContext, agent.launchCommand)) {
            showSessionNote("${agent.displayName} 还没有安装到当前空间，先在主终端里按正常 Linux 方式安装。")
            return
        }
        launchAgentInDedicatedSession(agent)
    }

    override fun logError(tag: String, message: String) {
        Logger.e(tag, message)
    }

    override fun logWarn(tag: String, message: String) {
        Logger.i(tag, message)
    }

    override fun logInfo(tag: String, message: String) {
        Logger.i(tag, message)
    }

    override fun logDebug(tag: String, message: String) {
        Logger.d(tag, message)
    }

    override fun logVerbose(tag: String, message: String) {
        Logger.d(tag, message)
    }

    override fun logStackTraceWithMessage(tag: String, message: String, e: Exception) {
        Logger.e(tag, "$message: ${e.message}")
    }

    override fun logStackTrace(tag: String, e: Exception) {
        Logger.e(tag, e.stackTraceToString())
    }
}
