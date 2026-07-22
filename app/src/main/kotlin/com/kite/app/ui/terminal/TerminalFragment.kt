package com.kite.app.ui.terminal

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Bundle
import android.os.Looper
import android.os.SystemClock
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
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Scroller
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.widget.AppCompatImageButton
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.card.MaterialCardView
import com.google.android.material.button.MaterialButton
import com.kite.app.R
import com.kite.app.foundation.bootstrap.BootstrapCoordinator
import com.kite.app.foundation.bootstrap.BootstrapSnapshot
import com.kite.app.foundation.bootstrap.BootstrapStage
import com.kite.app.foundation.bootstrap.KFApplication
import com.kite.app.foundation.logging.Logger
import com.kite.app.foundation.contracts.ContainerRecord
import com.kite.app.foundation.runtime.TerminalSessionItem
import com.kite.app.foundation.runtime.TerminalSessionStore
import com.kite.app.foundation.runtime.TerminalSessionsSnapshot
import com.kite.app.foundation.service.WorkstationActionGateway
import com.kite.app.foundation.terminal.TerminalSessionController
import com.kite.app.foundation.terminal.TerminalRuntimeHost
import com.kite.app.foundation.terminal.TerminalSessionUiCallbacks
import com.kite.app.foundation.workspace.KFWorkspaceManager
import com.kite.app.foundation.contracts.ManagedTerminalStatus
import com.kite.app.foundation.contracts.SpaceRecord
import com.kite.app.foundation.workspace.WorkSurfaceRuntimeBridge
import com.kite.app.application.surface.SurfaceChromeMode
import com.kite.app.application.surface.SurfaceEffect
import com.kite.app.feature.terminal.TerminalSurfaceResultContract
import com.kite.app.theme.ThemeEnvironment
import com.kite.app.ui.UiKit
import com.kite.app.ui.theme.kiteThemeEnvironment
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
import java.util.Calendar
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.roundToInt

class TerminalFragment : Fragment(), TerminalViewClient, TerminalSessionUiCallbacks {

    companion object {
        private const val ARG_DETAIL_ONLY = "detail_only"
        private const val ARG_INITIAL_SESSION_ID = "initial_session_id"
        private const val MIN_TERMINAL_REFRESH_INTERVAL_MS = 33L
        private const val TERMINAL_REFRESH_MIN_INTERVAL_MS = 5_000L
        private const val TERMINAL_COMPOSER_MAX_LINES = 8
        private const val TERMINAL_CONTEXT_COPY_SCREEN = 4001
        private const val TERMINAL_CONTEXT_COPY_ALL = 4002

        fun detailOnly(sessionId: String): TerminalFragment =
            TerminalFragment().apply {
                arguments = Bundle().apply {
                    putBoolean(ARG_DETAIL_ONLY, true)
                    putString(ARG_INITIAL_SESSION_ID, sessionId)
                }
            }
    }

    private var listPage: View? = null
    private lateinit var detailPage: View
    private lateinit var terminalDetailHeader: View
    private var terminalListRefresh: SwipeRefreshLayout? = null
    private var tvEmptySessions: TextView? = null
    private var terminalListContainer: LinearLayout? = null
    private lateinit var tvDetailTitle: TextView
    private lateinit var tvDetailSubtitle: TextView
    private lateinit var tvSessionNote: TextView
    private lateinit var terminalOutputContainer: View
    private lateinit var cardBootstrapStatus: View
    private lateinit var progressBootstrap: ProgressBar
    private lateinit var tvBootstrapTitle: TextView
    private lateinit var tvBootstrapDetail: TextView
    private lateinit var terminalInputBar: LinearLayout
    private lateinit var terminalComposerInput: EditText
    private lateinit var terminalControlPanel: LinearLayout
    private lateinit var terminalControlPager: HorizontalScrollView
    private lateinit var terminalControlPage: LinearLayout
    private lateinit var terminalControlIndicator: LinearLayout
    private lateinit var terminalPanelToggle: MaterialButton
    private lateinit var terminalSendButton: MaterialButton
    private lateinit var terminalView: TerminalView
    private lateinit var terminalController: TerminalSessionController
    private lateinit var appThemeEnvironment: ThemeEnvironment
    private lateinit var appUi: UiKit

    private var isCtrlPressed = false
    private var isAltPressed = false
    private var currentFontSizeDp = 35
    private var currentTerminalThemeMode = TerminalThemeMode.SYSTEM
    private var currentTerminalDarkMode = false
    private var currentSpace: SpaceRecord? = null
    private var currentTerminalSnapshot = TerminalSessionsSnapshot()
    private var lastManagedSessionsRenderSignature: String = ""
    private var isDetailMode = false
    private var isTerminalPanelExpanded = false
    private val terminalPanelActionHost = object : TerminalPanelActionHost {
        override fun sendInput(input: String) {
            sendTerminalInput(input)
        }

        override fun applyComposerEffect(effect: TerminalComposerEffect) {
            applyTerminalComposerEffect(effect)
        }

        override fun adjustFont(step: Int) {
            applyFontSize(TerminalUiPreferences.stepFontSize(currentFontSizeDp, step), true)
        }

        override fun pasteClipboard() {
            pasteFromClipboard()
        }

        override fun showThemeMenu(anchor: View) {
            this@TerminalFragment.showThemeMenu(anchor)
        }

        override fun themeLabel(): String =
            requireContext().terminalThemeLabel(TerminalUiPreferences.loadThemeMode(requireContext()))
    }
    private var terminalPanelPageIndex = 0
    private val terminalPanelActionBindings = LinkedHashMap<String, PanelActionBinding>()
    private var sessionNoteJob: Job? = null
    private var terminalRefreshJob: Job? = null
    @Volatile
    private var pendingTerminalRefresh = false
    @Volatile
    private var pendingTerminalRefreshForce = false
    @Volatile
    private var pendingTerminalRefreshUserVisible = false
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
    private var followTerminalOutput = true
    private var terminalTouchScrolling = false
    private var terminalTouchStartTopRow = 0
    private var detailOnlyInitialSessionOpened = false
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
            if (isDetailOnlyMode()) {
                sendSurfaceEffect(SurfaceEffect.RequestBack)
                return
            }
            showListPage()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        Logger.i("Terminal", "创建终端页面")
        val layout = if (isDetailOnlyMode()) {
            R.layout.fragment_terminal_detail
        } else {
            R.layout.fragment_terminal
        }
        return inflater.inflate(layout, container, false)
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
        if (!isDetailOnlyMode()) {
            requestTerminalRefresh("first-render")
            KFApplication.markLaunchStage("Terminal", "终端快照刷新已请求")
        }
        KFApplication.markLaunchStage("Terminal", "终端页面以入口模式就绪")
    }

    override fun onResume() {
        super.onResume()
        syncTerminalUiPreferences()
        terminalView.setTerminalCursorBlinkerState(true, true)
        if (isDetailMode) {
            terminalComposerInput.post {
                if (!terminalView.isSelectingText()) {
                    focusComposerInput(showKeyboard = false)
                }
            }
        }
        if (!isDetailOnlyMode()) {
            requestTerminalRefresh("fragment-resume")
        }
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
        appThemeEnvironment = requireContext().kiteThemeEnvironment()
        appUi = UiKit(requireContext(), appThemeEnvironment)
        listPage = view.findViewById(R.id.terminalListPage)
        detailPage = view.findViewById(R.id.terminalDetailPage)
        terminalDetailHeader = (detailPage as ViewGroup).getChildAt(0)
        terminalListRefresh = view.findViewById(R.id.terminalListRefresh)
        tvEmptySessions = view.findViewById(R.id.tvEmptySessions)
        terminalListContainer = view.findViewById(R.id.terminalListContainer)
        tvDetailTitle = view.findViewById(R.id.tvDetailTitle)
        tvDetailSubtitle = view.findViewById(R.id.tvDetailSubtitle)
        tvSessionNote = view.findViewById(R.id.tvSessionNote)
        terminalOutputContainer = view.findViewById(R.id.terminalOutputContainer)
        cardBootstrapStatus = view.findViewById(R.id.cardBootstrapStatus)
        progressBootstrap = view.findViewById(R.id.progressBootstrap)
        tvBootstrapTitle = view.findViewById(R.id.tvBootstrapTitle)
        tvBootstrapDetail = view.findViewById(R.id.tvBootstrapDetail)
        terminalInputBar = view.findViewById(R.id.terminalInputBar)
        terminalView = view.findViewById(R.id.terminalView)
        terminalView.setBackgroundColor(color(R.color.terminal_page_surface))
        applyShellThemeToStaticViews(view)
        val appContext = requireContext().applicationContext
        terminalController = TerminalRuntimeHost.attachUi(
            appContext,
            this,
            preferredSessionId = initialSessionId().takeIf { isDetailOnlyMode() && it.isNotBlank() },
            notifyManagedSessionsChanged = !isDetailOnlyMode()
        )
        currentFontSizeDp = TerminalUiPreferences.loadFontSizeDp(appContext)
        currentTerminalThemeMode = TerminalUiPreferences.loadThemeMode(appContext)

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
                    setTerminalOutputFollow(false)
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    terminalView.postDelayed({
                        if (terminalTouchScrolling || terminalView.topRow != terminalTouchStartTopRow) {
                            setTerminalOutputFollow(terminalView.topRow == 0)
                        }
                    }, 80L)
                }
            }
            false
        }

        terminalListRefresh?.setColorSchemeColors(
            appThemeEnvironment.tokens.primaryStrong
        )
        terminalListRefresh?.setOnRefreshListener {
            requestTerminalRefresh("pull-to-refresh", force = true, userVisible = true)
        }

        view.findViewById<AppCompatImageButton>(R.id.btnListAdd)?.setOnClickListener {
            createNewTerminalSession()
        }
        view.findViewById<AppCompatImageButton>(R.id.btnBackToSessions)?.apply {
            if (isDetailOnlyMode()) {
                visibility = View.INVISIBLE
                setOnClickListener(null)
            } else {
                setOnClickListener { sendSurfaceEffect(SurfaceEffect.RequestBack) }
            }
        }
        setupTerminalComposer()
        setupWindowInsets(view)

        renderDetailHeader(null)
        showSessionNote("")
        showInitialPage()
    }

    private fun isDetailOnlyMode(): Boolean =
        arguments?.getBoolean(ARG_DETAIL_ONLY, false) == true

    private fun initialSessionId(): String =
        arguments?.getString(ARG_INITIAL_SESSION_ID).orEmpty()

    private fun showInitialPage() {
        if (isDetailOnlyMode()) {
            openRequestedSessionDetail()
        } else {
            showListPage()
        }
    }

    private fun openRequestedSessionDetail() {
        val sessionId = initialSessionId()
        if (sessionId.isNotBlank() && !detailOnlyInitialSessionOpened) {
            detailOnlyInitialSessionOpened = true
            terminalController.openEmbeddedSession(sessionId)
        }
        showDetailPage()
    }

    private fun applyTerminalColorScheme() {
        val colors = TerminalColors.COLOR_SCHEME.mDefaultColors
        val isDark = TerminalUiPreferences.resolveTerminalDarkMode(requireContext())
        currentTerminalDarkMode = isDark
        TerminalColorPalette.applyTo(colors, isDark)
        applyTerminalCanvasBackground()
    }

    private fun setupTerminalComposer() {
        terminalInputBar.removeAllViews()
        terminalInputBar.setPadding(dp(10), dp(8), dp(10), dp(10))
        applyTerminalComposerBackground()

        terminalControlPanel = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, 0, 0, dp(6))
        }
        terminalControlPager = object : HorizontalScrollView(requireContext()) {
            override fun onTouchEvent(event: MotionEvent): Boolean {
                val handled = super.onTouchEvent(event)
                if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
                    snapTerminalPanelPage()
                }
                return handled
            }
        }.apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            isFillViewport = true
        }
        terminalControlPage = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        terminalControlPager.addView(
            terminalControlPage,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        terminalControlPanel.addView(
            terminalControlPager,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        terminalControlIndicator = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        }
        terminalControlPanel.addView(
            terminalControlIndicator,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(16),
            ).apply {
                topMargin = dp(2)
            },
        )
        terminalInputBar.addView(
            terminalControlPanel,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        val composerRow = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.BOTTOM
        }

        terminalPanelToggle = composerIconButton(
            iconRes = R.drawable.ic_terminal_prompt_light,
            contentDescriptionRes = R.string.terminal_show_shortcuts,
        ).apply {
            setOnClickListener { toggleTerminalPanel() }
        }
        composerRow.addView(terminalPanelToggle, LinearLayout.LayoutParams(dp(48), dp(48)))

        val inputCard = MaterialCardView(requireContext()).apply {
            radius = dp(18).toFloat()
            cardElevation = 0f
            setCardBackgroundColor(terminalColor(R.color.terminal_page_surface))
            strokeColor = terminalColor(R.color.terminal_page_line)
            strokeWidth = dp(1)
            minimumHeight = dp(48)
            minimumWidth = 0
            setContentPadding(dp(14), dp(8), dp(12), dp(8))
        }
        terminalComposerInput = EditText(requireContext()).apply {
            background = null
            hint = getString(R.string.terminal_composer_hint)
            contentDescription = getString(R.string.terminal_composer_hint)
            setHintTextColor(terminalColor(R.color.terminal_page_subtext))
            setTextColor(terminalColor(R.color.terminal_page_text))
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
            setOnEditorActionListener { _, actionId, _ ->
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
                marginStart = dp(7)
            }
        )

        terminalSendButton = composerIconButton(
            iconRes = R.drawable.ic_send_light,
            contentDescriptionRes = R.string.terminal_send_input,
        ).apply {
            setOnClickListener { submitComposerInput() }
        }
        composerRow.addView(
            terminalSendButton,
            LinearLayout.LayoutParams(dp(48), dp(48)).apply {
                marginStart = dp(7)
            }
        )

        terminalInputBar.addView(
            composerRow,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
        if (::terminalControlPage.isInitialized) {
            renderTerminalPanelPage()
        }
        refreshComposerActionButtons()
        terminalComposerInput.post { updateComposerScrollState() }
    }

    private fun renderTerminalPanelPage() {
        terminalControlPage.removeAllViews()
        terminalPanelActionBindings.clear()
        TerminalPanelActionRegistry.snapshot().forEach { page ->
            terminalControlPage.addView(buildTerminalPanelPageContainer(buildTerminalPanelPage(page)))
        }
        terminalControlPager.post {
            updateTerminalPanelPageWidths()
            terminalControlPager.scrollTo(terminalPanelPageIndex * terminalControlPager.width, 0)
        }
        renderTerminalPanelIndicator()
    }

    private fun setTerminalPanelPage(pageIndex: Int) {
        val nextPage = pageIndex.coerceIn(0, (terminalControlPage.childCount - 1).coerceAtLeast(0))
        if (terminalPanelPageIndex == nextPage) {
            return
        }
        terminalPanelPageIndex = nextPage
        terminalControlPager.post {
            terminalControlPager.smoothScrollTo(terminalPanelPageIndex * terminalControlPager.width, 0)
        }
        renderTerminalPanelIndicator()
    }

    private fun buildTerminalPanelPageContainer(content: View): View {
        return FrameLayout(requireContext()).apply {
            addView(
                content,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER
                )
            )
        }
    }

    private fun updateTerminalPanelPageWidths() {
        val pageWidth = terminalControlPager.width
        if (pageWidth <= 0) {
            return
        }
        for (index in 0 until terminalControlPage.childCount) {
            val child = terminalControlPage.getChildAt(index)
            val params = child.layoutParams
            if (params.width != pageWidth) {
                params.width = pageWidth
                child.layoutParams = params
            }
        }
    }

    private fun snapTerminalPanelPage() {
        if (!::terminalControlPager.isInitialized || terminalControlPager.width <= 0) {
            return
        }
        terminalPanelPageIndex = (terminalControlPager.scrollX.toFloat() / terminalControlPager.width)
            .roundToInt()
            .coerceIn(0, (terminalControlPage.childCount - 1).coerceAtLeast(0))
        terminalControlPager.smoothScrollTo(terminalPanelPageIndex * terminalControlPager.width, 0)
        renderTerminalPanelIndicator()
    }

    private fun renderTerminalPanelIndicator() {
        if (!::terminalControlIndicator.isInitialized) {
            return
        }
        val pageCount = TerminalPanelActionRegistry.snapshot().size.coerceAtLeast(1)
        terminalPanelPageIndex = terminalPanelPageIndex.coerceIn(0, pageCount - 1)
        terminalControlIndicator.removeAllViews()
        repeat(pageCount) { index ->
            val selected = index == terminalPanelPageIndex
            terminalControlIndicator.addView(
                View(requireContext()).apply {
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        cornerRadius = dp(3).toFloat()
                        setColor(
                            terminalColor(
                                if (selected) R.color.terminal_page_blue else R.color.terminal_page_gray_chip,
                            ),
                        )
                    }
                },
                LinearLayout.LayoutParams(dp(if (selected) 18 else 6), dp(6)).apply {
                    marginStart = dp(3)
                    marginEnd = dp(3)
                },
            )
        }
        terminalControlIndicator.contentDescription = getString(
            R.string.terminal_shortcut_page_status,
            terminalPanelPageIndex + 1,
            pageCount,
        )
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

    private fun buildTerminalPanelPage(page: TerminalPanelPage): View {
        return LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(2), 0, dp(2), 0)
            addView(
                buildPanelGrid(
                    page.actions.map { action ->
                        PanelButton(
                            id = action.id,
                            title = getString(action.titleRes),
                            subtitle = action.resolvedSubtitle(terminalPanelActionHost, ::getString),
                            iconRes = action.iconRes
                        ) { anchor -> action.execute(terminalPanelActionHost, anchor) }
                    }
                ),
                LinearLayout.LayoutParams(dp(172), ViewGroup.LayoutParams.WRAP_CONTENT)
            )
            if (page.showDpad) {
                addView(
                    buildDpadCluster(),
                    LinearLayout.LayoutParams(dp(150), ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                        marginStart = dp(8)
                    }
                )
            }
        }
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
                width = dp(48)
                height = dp(48)
                setMargins(dp(1), dp(1), dp(1), dp(1))
            }
            grid.addView(view ?: SpaceView(requireContext()), params)
        }

        addCell(null, 0, 0)
        addCell(controlTile("↑", "", contentDescription = getString(R.string.terminal_direction_up)).also {
            bindRepeatingKey(it, KeyEvent.KEYCODE_DPAD_UP, "\u001b[A")
        }, 0, 1)
        addCell(null, 0, 2)
        addCell(controlTile("←", "", contentDescription = getString(R.string.terminal_direction_left)).also {
            bindRepeatingKey(it, KeyEvent.KEYCODE_DPAD_LEFT, "\u001b[D")
        }, 1, 0)
        addCell(controlTile("↵", getString(R.string.terminal_enter)).also {
            it.setOnClickListener {
                sendTerminalInput("\r")
                applyTerminalComposerEffect(TerminalComposerEffect.RESET_AFTER_ACTION)
            }
        }, 1, 1)
        addCell(controlTile("→", "", contentDescription = getString(R.string.terminal_direction_right)).also {
            bindRepeatingKey(it, KeyEvent.KEYCODE_DPAD_RIGHT, "\u001b[C")
        }, 1, 2)
        addCell(null, 2, 0)
        addCell(controlTile("↓", "", contentDescription = getString(R.string.terminal_direction_down)).also {
            bindRepeatingKey(it, KeyEvent.KEYCODE_DPAD_DOWN, "\u001b[B")
        }, 2, 1)
        addCell(null, 2, 2)
        return grid
    }

    private fun buildPanelGrid(buttons: List<PanelButton>): View {
        val columns = 2
        val grid = GridLayout(requireContext()).apply {
            columnCount = columns
            rowCount = ((buttons.size + columns - 1) / columns).coerceAtLeast(1)
        }
        buttons.forEachIndexed { index, button ->
            var subtitleView: TextView? = null
            val tile = controlTile(
                button.title,
                button.subtitle,
                button.iconRes,
                onSubtitleView = { subtitleView = it },
            ).apply {
                setOnClickListener { button.action(this) }
            }
            terminalPanelActionBindings[button.id] = PanelActionBinding(
                tile = tile,
                title = button.title,
                subtitleView = subtitleView,
            )
            val params = GridLayout.LayoutParams(
                GridLayout.spec(index / columns),
                GridLayout.spec(index % columns)
            ).apply {
                width = dp(80)
                height = dp(48)
                setMargins(dp(3), dp(3), dp(3), dp(3))
            }
            grid.addView(tile, params)
        }
        return grid
    }

    private fun controlTile(
        title: String,
        subtitle: String,
        iconRes: Int? = null,
        compact: Boolean = false,
        contentDescription: String = listOf(title, subtitle).filter(String::isNotBlank).joinToString(", "),
        onSubtitleView: (TextView) -> Unit = {},
    ): MaterialCardView {
        return MaterialCardView(requireContext()).apply {
            radius = dp(13).toFloat()
            cardElevation = 0f
            setCardBackgroundColor(terminalColor(R.color.terminal_page_surface))
            strokeColor = terminalColor(R.color.terminal_page_line)
            strokeWidth = dp(1)
            minimumWidth = dp(48)
            setMinimumWidth(dp(48))
            isClickable = true
            isFocusable = true
            this.contentDescription = contentDescription
            addView(buildControlTileContent(title, subtitle, iconRes, compact, onSubtitleView))
        }
    }

    private fun buildControlTileContent(
        title: String,
        subtitle: String,
        iconRes: Int?,
        compact: Boolean,
        onSubtitleView: (TextView) -> Unit,
    ): View {
        if (subtitle.isBlank()) {
            return TextView(requireContext()).apply {
                gravity = Gravity.CENTER
                text = title
                setTextColor(terminalColor(R.color.terminal_page_text))
                textSize = if (compact) 13f else 20f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                includeFontPadding = false
                minWidth = dp(44)
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
        }

        val titleView = TextView(requireContext()).apply {
            text = title
            setTextColor(terminalColor(R.color.terminal_page_text))
            textSize = 11.2f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            includeFontPadding = false
            maxLines = 1
            gravity = Gravity.CENTER
        }
        val subtitleView = TextView(requireContext()).apply {
            text = subtitle
            setTextColor(terminalColor(R.color.terminal_page_subtext))
            textSize = 10.2f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            includeFontPadding = false
            maxLines = 1
            gravity = Gravity.CENTER
        }
        onSubtitleView(subtitleView)
        val textColumn = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            addView(
                titleView,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
            addView(
                subtitleView,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = dp(2)
                }
            )
        }

        return LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(8), 0, dp(8), 0)
            iconRes?.let { resId ->
                addView(
                    ImageView(requireContext()).apply {
                        setImageResource(resId)
                        setColorFilter(terminalColor(R.color.terminal_page_text))
                    },
                    LinearLayout.LayoutParams(dp(16), dp(16)).apply {
                        marginEnd = dp(6)
                    }
                )
            }
            addView(
                textColumn,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
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

    private class SpaceView(context: Context) : View(context)

    private fun buildTerminalHandle(): View {
        return LinearLayout(requireContext()).apply {
            gravity = Gravity.CENTER
            addView(View(requireContext()).apply {
                background = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                    cornerRadius = dp(2).toFloat()
                    setColor(terminalColor(R.color.terminal_page_gray_chip))
                }
            }, LinearLayout.LayoutParams(dp(64), dp(4)))
        }
    }

    private fun composerIconButton(iconRes: Int, contentDescriptionRes: Int): MaterialButton {
        return MaterialButton(requireContext()).apply {
            text = ""
            icon = ContextCompat.getDrawable(requireContext(), iconRes)
            iconSize = dp(22)
            iconPadding = 0
            iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
            gravity = Gravity.CENTER
            contentDescription = getString(contentDescriptionRes)
            cornerRadius = dp(24)
            insetTop = 0
            insetBottom = 0
            minWidth = 0
            minimumWidth = 0
            setPadding(0, 0, 0, 0)
            backgroundTintList = ColorStateList.valueOf(
                terminalColor(R.color.terminal_page_input_bg)
            )
        }
    }

    private fun applyTerminalComposerBackground() {
        terminalInputBar.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadii = floatArrayOf(
                dp(22).toFloat(), dp(22).toFloat(),
                dp(22).toFloat(), dp(22).toFloat(),
                0f, 0f,
                0f, 0f
            )
            setColor(terminalColor(R.color.terminal_page_header))
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
        refreshComposerActionButtons()
        renderTerminalPanelPage()
    }

    private fun refreshComposerActionButtons() {
        if (::terminalPanelToggle.isInitialized) {
            terminalPanelToggle.setIconResource(
                if (isTerminalPanelExpanded) R.drawable.ic_close_light else R.drawable.ic_terminal_prompt_light,
            )
            terminalPanelToggle.contentDescription = getString(
                if (isTerminalPanelExpanded) R.string.terminal_hide_shortcuts
                else R.string.terminal_show_shortcuts,
            )
            terminalPanelToggle.backgroundTintList = ColorStateList.valueOf(
                terminalColor(
                    if (isTerminalPanelExpanded) R.color.terminal_page_blue
                    else R.color.terminal_page_input_bg,
                ),
            )
            terminalPanelToggle.iconTint = ColorStateList.valueOf(
                if (isTerminalPanelExpanded) Color.WHITE else terminalColor(R.color.terminal_page_text),
            )
        }
        if (::terminalSendButton.isInitialized) {
            terminalSendButton.backgroundTintList = ColorStateList.valueOf(
                terminalColor(R.color.terminal_page_blue),
            )
            terminalSendButton.iconTint = ColorStateList.valueOf(Color.WHITE)
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
            setTerminalOutputFollow(true)
            keepLatestTerminalOutputVisible(forceImmediate = true)
        }
        composerLiveSyncBuffer = text
    }

    private fun shouldUseRealtimeComposerSync(text: String): Boolean {
        return !text.contains('\n') && !text.contains('\r')
    }

    private fun clearComposerLiveEchoIfNeeded() {
        if (!composerLiveSyncEnabled || composerLiveSyncBuffer.isEmpty()) {
            composerLiveSyncBuffer = ""
            return
        }
        terminalController.writeRawInput("\u007f".repeat(composerLiveSyncBuffer.length))
        composerLiveSyncBuffer = ""
        setTerminalOutputFollow(true)
        keepLatestTerminalOutputVisible(forceImmediate = true)
    }

    private data class PanelButton(
        val id: String,
        val title: String,
        val subtitle: String,
        val iconRes: Int? = null,
        val action: (View) -> Unit
    )

    private data class PanelActionBinding(
        val tile: MaterialCardView,
        val title: String,
        val subtitleView: TextView?,
    )

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    private fun color(resId: Int): Int {
        return KiteTerminalShellTheme.resolve(requireContext(), resId)
    }

    private fun terminalColor(resId: Int): Int {
        val isDark = TerminalUiPreferences.resolveTerminalDarkMode(requireContext())
        return if (isDark) {
            when (resId) {
                R.color.terminal_page_bg -> Color.rgb(5, 8, 13)
                R.color.terminal_page_header -> Color.rgb(14, 20, 29)
                R.color.terminal_page_surface -> Color.rgb(11, 17, 24)
                R.color.terminal_page_text -> Color.rgb(242, 246, 251)
                R.color.terminal_page_subtext -> Color.rgb(148, 163, 184)
                R.color.terminal_page_line -> Color.rgb(36, 49, 64)
                R.color.terminal_page_green -> Color.rgb(71, 209, 140)
                R.color.terminal_page_blue -> Color.rgb(87, 166, 255)
                R.color.terminal_page_gray_chip -> Color.rgb(51, 65, 85)
                R.color.terminal_page_input_bg -> Color.rgb(17, 24, 39)
                else -> color(resId)
            }
        } else {
            when (resId) {
                R.color.terminal_page_bg -> Color.rgb(242, 243, 245)
                R.color.terminal_page_header -> Color.rgb(232, 235, 238)
                R.color.terminal_page_surface -> Color.rgb(255, 255, 255)
                R.color.terminal_page_text -> Color.rgb(31, 35, 41)
                R.color.terminal_page_subtext -> Color.rgb(111, 119, 131)
                R.color.terminal_page_line -> Color.rgb(225, 229, 234)
                R.color.terminal_page_gray_chip -> Color.rgb(201, 206, 214)
                R.color.terminal_page_input_bg -> Color.rgb(244, 246, 248)
                R.color.terminal_page_blue -> Color.rgb(59, 130, 246)
                else -> color(resId)
            }
        }
    }

    private fun applyTerminalCanvasBackground() {
        if (!::terminalView.isInitialized) return
        val colors = TerminalColors.COLOR_SCHEME.mDefaultColors
        val background = colors.getOrElse(TextStyle.COLOR_INDEX_BACKGROUND) {
            terminalColor(R.color.terminal_page_surface)
        }
        terminalView.setBackgroundColor(background)
        if (::terminalOutputContainer.isInitialized) {
            terminalOutputContainer.setBackgroundColor(background)
        }
    }

    private fun applyShellThemeToStaticViews(root: View) {
        val appTokens = appThemeEnvironment.tokens
        fun tintListHeader(header: View?) {
            header?.setBackgroundColor(appTokens.pageBackground)
            if (header is ViewGroup) {
                for (index in 0 until header.childCount) {
                    when (val child = header.getChildAt(index)) {
                        is TextView -> {
                            child.setTextColor(appTokens.textPrimary)
                            child.textSize = appThemeEnvironment.foundations.typography.pageTitle
                            child.typeface = Typeface.DEFAULT_BOLD
                        }
                        is AppCompatImageButton -> child.setColorFilter(appTokens.textPrimary)
                    }
                }
            }
        }

        root.setBackgroundColor(appTokens.pageBackground)
        listPage?.setBackgroundColor(appTokens.pageBackground)
        detailPage.setBackgroundColor(color(R.color.terminal_page_bg))
        tintListHeader((listPage as? ViewGroup)?.getChildAt(0))
        tvEmptySessions?.apply {
            setTextColor(appTokens.textSecondary)
            textSize = appThemeEnvironment.foundations.typography.supporting
            background = appUi.containerBackground(
                appTokens.cardBackground,
                appTokens.border,
                appThemeEnvironment.components.card,
            )
            setPadding(appUi.dp(22), appUi.dp(30), appUi.dp(22), appUi.dp(30))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                setMargins(
                    appUi.dp(appThemeEnvironment.foundations.spacing.pageHorizontal),
                    appUi.dp(appThemeEnvironment.foundations.spacing.sectionGap),
                    appUi.dp(appThemeEnvironment.foundations.spacing.pageHorizontal),
                    0,
                )
            }
        }
        terminalListContainer?.setPadding(
            appUi.dp(appThemeEnvironment.foundations.spacing.pageHorizontal),
            appUi.dp(appThemeEnvironment.foundations.spacing.sectionGap),
            appUi.dp(appThemeEnvironment.foundations.spacing.pageHorizontal),
            appUi.dp(96),
        )
        tvDetailTitle.setTextColor(color(R.color.terminal_page_text))
        tvDetailSubtitle.setTextColor(color(R.color.terminal_page_subtext))
        tvSessionNote.setTextColor(color(R.color.terminal_page_subtext))
        tvBootstrapTitle.setTextColor(color(R.color.terminal_page_text))
        tvBootstrapDetail.setTextColor(color(R.color.terminal_page_subtext))
        (cardBootstrapStatus as? MaterialCardView)?.apply {
            setCardBackgroundColor(color(R.color.terminal_page_surface))
            strokeColor = color(R.color.terminal_page_line)
        }
        applyTerminalDetailTheme()
    }

    private fun applyTerminalDetailTheme() {
        if (!::detailPage.isInitialized) {
            return
        }
        fun tintTerminalHeader(header: View?) {
            header?.setBackgroundColor(terminalColor(R.color.terminal_page_header))
            if (header is ViewGroup) {
                for (index in 0 until header.childCount) {
                    when (val child = header.getChildAt(index)) {
                        is TextView -> child.setTextColor(terminalColor(R.color.terminal_page_text))
                        is AppCompatImageButton -> child.setColorFilter(terminalColor(R.color.terminal_page_text))
                    }
                }
            }
        }

        detailPage.setBackgroundColor(terminalColor(R.color.terminal_page_bg))
        tintTerminalHeader(terminalDetailHeader)
        tvDetailTitle.setTextColor(terminalColor(R.color.terminal_page_text))
        tvDetailSubtitle.setTextColor(terminalColor(R.color.terminal_page_subtext))
        tvSessionNote.setTextColor(terminalColor(R.color.terminal_page_subtext))
        applyTerminalCanvasBackground()
        refreshTerminalComposerTheme()
    }

    private fun refreshTerminalComposerTheme() {
        if (!::terminalInputBar.isInitialized) {
            return
        }
        applyTerminalComposerBackground()
        if (::terminalComposerInput.isInitialized) {
            terminalComposerInput.setTextColor(terminalColor(R.color.terminal_page_text))
            terminalComposerInput.setHintTextColor(terminalColor(R.color.terminal_page_subtext))
        }
        applyThemeToTerminalControlTree(terminalInputBar)
        renderTerminalPanelIndicator()
        refreshComposerActionButtons()
    }

    private fun refreshTerminalPanelActionSubtitles() {
        TerminalPanelActionRegistry.snapshot()
            .flatMap(TerminalPanelPage::actions)
            .forEach { action ->
                val binding = terminalPanelActionBindings[action.id] ?: return@forEach
                val subtitle = action.resolvedSubtitle(terminalPanelActionHost, ::getString)
                binding.subtitleView?.text = subtitle
                binding.tile.contentDescription = listOf(binding.title, subtitle)
                    .filter(String::isNotBlank)
                    .joinToString(", ")
            }
    }

    private fun applyThemeToTerminalControlTree(view: View) {
        when (view) {
            is MaterialButton -> {
                view.setTextColor(terminalColor(R.color.terminal_page_text))
                view.iconTint = ColorStateList.valueOf(terminalColor(R.color.terminal_page_text))
                view.backgroundTintList = ColorStateList.valueOf(
                    terminalColor(R.color.terminal_page_input_bg)
                )
            }

            is MaterialCardView -> {
                view.setCardBackgroundColor(terminalColor(R.color.terminal_page_surface))
                view.strokeColor = terminalColor(R.color.terminal_page_line)
            }

            is TextView -> {
                view.setTextColor(terminalColor(R.color.terminal_page_text))
            }

            is ImageView -> {
                view.setColorFilter(terminalColor(R.color.terminal_page_text))
            }
        }
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                applyThemeToTerminalControlTree(view.getChildAt(index))
            }
        }
    }

    private fun setupWindowInsets(root: View) {
        val list = listPage
        val listInitialBottomPadding = list?.paddingBottom ?: 0
        val detailInitialBottomPadding = detailPage.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val systemInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())
            list?.setPadding(
                list.paddingLeft,
                list.paddingTop,
                list.paddingRight,
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
        if (isDetailOnlyMode()) {
            return
        }
        val appContext = requireContext().applicationContext
        if (terminalRefreshJob?.isActive == true) {
            pendingTerminalRefresh = true
            pendingTerminalRefreshForce = pendingTerminalRefreshForce || force
            pendingTerminalRefreshUserVisible = pendingTerminalRefreshUserVisible || userVisible
            if (userVisible) {
                terminalListRefresh?.isRefreshing = true
            }
            Logger.i("TerminalFragment", "终端刷新已排队: reason=$reason")
            return
        }
        val now = SystemClock.uptimeMillis()
        if (!force && now - lastTerminalRefreshRequestedAtMs < TERMINAL_REFRESH_MIN_INTERVAL_MS) {
            Logger.i("TerminalFragment", "跳过终端刷新: reason=$reason, recent=true")
            return
        }
        if (userVisible) {
            terminalListRefresh?.isRefreshing = true
        }
        terminalRefreshJob = viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                var queuedReason = reason
                var runForce = force
                var runUserVisible = userVisible
                do {
                    pendingTerminalRefresh = false
                    pendingTerminalRefreshForce = false
                    pendingTerminalRefreshUserVisible = false
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
                    val runFullRefresh = runForce || runUserVisible
                    Logger.i(
                        "TerminalFragment",
                        "执行终端刷新: reason=$queuedReason, full=$runFullRefresh"
                    )
                    if (runFullRefresh) {
                        TerminalRuntimeHost.refreshRuntimeSnapshot(appContext)
                    }
                    TerminalSessionStore.refresh(appContext, force = runForce)
                    if (pendingTerminalRefresh) {
                        queuedReason = "queued-terminal-refresh"
                        runForce = pendingTerminalRefreshForce
                        runUserVisible = pendingTerminalRefreshUserVisible
                    }
                } while (pendingTerminalRefresh)
            } finally {
                withContext(Dispatchers.Main) {
                    terminalListRefresh?.isRefreshing = false
                }
                terminalRefreshJob = null
            }
        }
    }

    private fun applyFontSize(newSizeDp: Int, announce: Boolean = false) {
        currentFontSizeDp = newSizeDp
        terminalView.setTextSize(currentFontSizeDp)
        TerminalUiPreferences.saveFontSizeDp(requireContext().applicationContext, currentFontSizeDp)
        relayoutTerminalAfterFontSizeChange()
        if (announce) {
            Toast.makeText(
                requireContext(),
                getString(R.string.terminal_font_changed, currentFontSizeDp),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun relayoutTerminalAfterFontSizeChange() {
        terminalView.scaleX = 1f
        terminalView.scaleY = 1f
        terminalOutputContainer.requestLayout()
        terminalView.requestLayout()
        forceTerminalViewResize()
        terminalView.post { forceTerminalViewResize() }
        terminalView.postDelayed({ forceTerminalViewResize() }, 80L)
    }

    private fun forceTerminalViewResize() {
        if (view == null) {
            return
        }
        terminalView.updateSize()
        keepLatestTerminalOutputVisible(forceImmediate = true)
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
        applyTerminalThemeMode(mode)
        return true
    }

    private fun showThemeDialog() {
        val modes = listOf(TerminalThemeMode.SYSTEM, TerminalThemeMode.DARK, TerminalThemeMode.LIGHT)
        val context = requireContext()
        appUi.showChoiceDialog(
            context = context,
            title = getString(R.string.terminal_theme_button),
            options = modes.map(context::terminalThemeLabel),
            selectedIndex = modes.indexOf(currentTerminalThemeMode),
            dismissLabel = getString(R.string.common_close),
        ) { selectedIndex -> applyTerminalThemeMode(modes[selectedIndex]) }
    }

    private fun applyTerminalThemeMode(mode: TerminalThemeMode) {
        TerminalUiPreferences.saveThemeMode(requireContext().applicationContext, mode)
        currentTerminalThemeMode = mode
        applyTerminalColorScheme()
        applyTerminalDetailTheme()
        refreshTerminalPanelActionSubtitles()
        terminalView.mTermSession?.emulator?.mColors?.reset()
        refreshTerminalColors()
        keepLatestTerminalOutputVisible()
    }

    private fun syncTerminalUiPreferences() {
        val context = requireContext().applicationContext
        val storedFontSize = TerminalUiPreferences.loadFontSizeDp(context)
        if (::terminalView.isInitialized && storedFontSize != currentFontSizeDp) {
            currentFontSizeDp = storedFontSize
            terminalView.setTextSize(currentFontSizeDp)
            relayoutTerminalAfterFontSizeChange()
        }
        val storedThemeMode = TerminalUiPreferences.loadThemeMode(context)
        val effectiveDarkMode = TerminalUiPreferences.resolveTerminalDarkMode(context)
        if (::terminalView.isInitialized &&
            (storedThemeMode != currentTerminalThemeMode || effectiveDarkMode != currentTerminalDarkMode)
        ) {
            currentTerminalThemeMode = storedThemeMode
            applyTerminalColorScheme()
            applyTerminalDetailTheme()
            refreshTerminalPanelActionSubtitles()
            terminalView.mTermSession?.emulator?.mColors?.reset()
            refreshTerminalColors()
            keepLatestTerminalOutputVisible()
        }
    }

    private fun sendTerminalInput(rawInput: String) {
        terminalController.writeRawInput(rawInput)
        setTerminalOutputFollow(true)
        keepLatestTerminalOutputVisible()
    }

    private fun applyTerminalComposerEffect(effect: TerminalComposerEffect) {
        if (composerLiveSyncEnabled && effect == TerminalComposerEffect.RESET_AFTER_ACTION) {
            resetComposerAfterRealtimeControl()
        }
    }

    private fun sendTerminalPaste(rawInput: String) {
        terminalController.writePastedInput(rawInput)
        setTerminalOutputFollow(true)
        keepLatestTerminalOutputVisible(forceImmediate = true)
    }

    private fun setTerminalOutputFollow(enabled: Boolean) {
        followTerminalOutput = enabled
        if (::terminalView.isInitialized) {
            terminalView.setScrollToBottomOnScreenUpdate(enabled)
        }
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
        var delayMs: Long
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
        terminalView.setScrollToBottomOnScreenUpdate(followTerminalOutput && !terminalView.isSelectingText())
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

    private fun createNewTerminalSession() {
        WorkstationActionGateway.createShellSession(requireContext().applicationContext)
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
                    terminalListRefresh?.isRefreshing = false
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
                    else -> getString(R.string.terminal_bootstrap_progress_title)
                }
                tvBootstrapDetail.text = when (snapshot.stage) {
                    BootstrapStage.ROOTFS_EXTRACTING ->
                        "首次安装会先解压系统镜像，请稍候。"
                    BootstrapStage.BASE_BOOTSTRAP ->
                        "正在初始化基础环境和工具链，完成后会自动继续。"
                    BootstrapStage.SPACE_READY ->
                        "正在准备标准工作区和构建辅助目录。"
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
            terminalListContainer?.removeAllViews()
            tvEmptySessions?.visibility = View.VISIBLE
            renderDetailHeader(null)
            return
        }

        val snapshot = currentTerminalSnapshot
        val activeSessionId = terminalController.getActiveSessionId()
            ?: snapshot.currentViewedSessionId
            ?: space.currentTerminalSessionId
        val activeSession = snapshot.sessions.asSequence()
            .firstOrNull { it.id == activeSessionId }

        if (isDetailOnlyMode()) {
            renderDetailHeader(activeSession?.title)
            return
        }

        val renderSignature = buildManagedSessionsRenderSignature(space, snapshot, activeSessionId)
        if (!isDetailMode && renderSignature != lastManagedSessionsRenderSignature) {
            renderTerminalSessions(snapshot)
            lastManagedSessionsRenderSignature = renderSignature
        }
        renderDetailHeader(activeSession?.title)
    }

    private fun renderTerminalSessions(snapshot: TerminalSessionsSnapshot) {
        val container = terminalListContainer ?: return
        val emptyView = tvEmptySessions ?: return
        container.removeAllViews()

        val visibleSessions = snapshot.liveSessions
            .sortedWith(
                compareByDescending<TerminalSessionItem> { it.isInputReady || it.allowsQueuedInput }
                    .thenByDescending { it.lastAttachedAt ?: it.lastStartedAt ?: it.createdAt }
            )
        Logger.i(
            "TerminalFragment",
            "渲染终端列表: visibleCount=${visibleSessions.size}, allCount=${snapshot.sessions.size}"
        )
        if (visibleSessions.isEmpty()) {
            emptyView.visibility = View.VISIBLE
            return
        }

        emptyView.visibility = View.GONE
        visibleSessions.forEach { session ->
            container.addView(createSessionListItem(session))
        }
    }

    private fun createSessionListItem(session: TerminalSessionItem): View {
        val badge = resolveSessionBadge(session.status, session.isCurrentViewed)
        val timestamp = resolveSessionTimestamp(session)
        return buildSessionListItem(
            title = session.title,
            sourceText = session.sourceLabel.orEmpty(),
            timeText = timestamp?.let(::formatSessionTime),
            badge = badge,
            onClick = {
                terminalController.switchToSession(session.id)
                showDetailPage()
            }
        )
    }

    private fun buildSessionListItem(
        title: String,
        sourceText: String,
        timeText: String?,
        badge: Triple<Int, Int, String>,
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
        val sourceView = itemView.findViewById<TextView>(R.id.tvSessionItemSource)
        val timeView = itemView.findViewById<TextView>(R.id.tvSessionItemTime)
        val statusView = itemView.findViewById<TextView>(R.id.tvSessionItemStatus)

        val (bgColorRes, textColorRes, labelText) = badge
        val appTokens = appThemeEnvironment.tokens
        root.background = appUi.containerBackground(
            appTokens.cardBackground,
            appTokens.border,
            appThemeEnvironment.components.interactiveCard,
        )
        root.elevation = appUi.dp(appThemeEnvironment.components.interactiveCard.elevation).toFloat()
        root.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { setMargins(0, 0, 0, appUi.dp(appThemeEnvironment.foundations.spacing.itemGap)) }
        iconCard.radius = appUi.dp(appThemeEnvironment.components.iconTile.radius).toFloat()
        iconCard.setCardBackgroundColor(appTokens.primarySubtle)
        iconView.setColorFilter(appTokens.primaryStrong)
        chevronView.setColorFilter(appTokens.textTertiary)
        divider.visibility = View.GONE
        statusCard.setCardBackgroundColor(color(bgColorRes))
        statusView.setTextColor(color(textColorRes))
        titleView.setTextColor(appTokens.textPrimary)
        titleView.textSize = appThemeEnvironment.foundations.typography.cardTitle
        sourceView.setTextColor(appTokens.textSecondary)
        sourceView.textSize = appThemeEnvironment.foundations.typography.supporting
        timeView.setTextColor(appTokens.textSecondary)

        val titleParts = splitTerminalTitle(title)
        titleView.text = titleParts.main
        val cleanedSource = sourceText.trim()
        if (cleanedSource.isBlank()) {
            sourceView.visibility = View.INVISIBLE
            sourceView.text = ""
        } else {
            sourceView.visibility = View.VISIBLE
            sourceView.text = cleanedSource
        }
        if (timeText.isNullOrBlank()) {
            timeView.visibility = View.INVISIBLE
            timeView.text = ""
        } else {
            timeView.visibility = View.VISIBLE
            timeView.text = timeText
        }
        statusView.text = labelText

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
                        getString(R.string.terminal_status_running)
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

    private data class TerminalTitleParts(
        val main: String,
        val subtitle: String
    )

    private fun splitTerminalTitle(title: String?): TerminalTitleParts {
        val raw = title?.trim().orEmpty()
        if (raw.isBlank()) {
            return TerminalTitleParts(getString(R.string.terminal_title_short), "")
        }
        val sessionNumber = Regex("""#\d+""").find(raw)?.value
        val withoutNumber = sessionNumber?.let { raw.replace(it, "").trim() } ?: raw
        val colonIndex = withoutNumber.indexOf(':')
        val main = if (colonIndex > 0) {
            withoutNumber.substring(0, colonIndex).trim()
        } else {
            withoutNumber.trim()
        }
        val path = if (colonIndex > 0) {
            withoutNumber.substring(colonIndex + 1).trim()
        } else {
            ""
        }
        val subtitle = listOf(path.takeIf { it.isNotBlank() }, sessionNumber)
            .filterNotNull()
            .joinToString(" · ")
        return TerminalTitleParts(main.ifBlank { raw }, subtitle)
    }

    private fun renderDetailHeader(activeSessionTitle: String?) {
        val parts = splitTerminalTitle(activeSessionTitle)
        tvDetailTitle.text = parts.main
        if (parts.subtitle.isBlank()) {
            tvDetailSubtitle.visibility = View.GONE
            tvDetailSubtitle.text = ""
        } else {
            tvDetailSubtitle.visibility = View.VISIBLE
            tvDetailSubtitle.text = parts.subtitle
        }
    }

    private fun showListPage() {
        if (isDetailOnlyMode()) {
            openRequestedSessionDetail()
            return
        }
        isDetailMode = false
        detailBackCallback.isEnabled = false
        hideSoftKeyboard(terminalComposerInput)
        hideSoftKeyboard(terminalView)
        listPage?.visibility = View.VISIBLE
        detailPage.visibility = View.GONE
        sendSurfaceEffect(SurfaceEffect.SetChromeMode(SurfaceChromeMode.Standard))
        stopRepeatingKey()
        lastManagedSessionsRenderSignature = ""
        renderManagedSessions()
        requestTerminalRefresh("show-list")
    }

    private fun showDetailPage() {
        isDetailMode = true
        detailBackCallback.isEnabled = !isDetailOnlyMode()
        listPage?.visibility = View.GONE
        detailPage.visibility = View.VISIBLE
        terminalDetailHeader.visibility = if (isDetailOnlyMode()) View.GONE else View.VISIBLE
        sendSurfaceEffect(SurfaceEffect.SetChromeMode(SurfaceChromeMode.Immersive))
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
        sendSurfaceEffect(SurfaceEffect.SetChromeMode(SurfaceChromeMode.Standard))
        // 这里只是 UI 脱离当前终端，不是关闭终端会话。
        TerminalRuntimeHost.detachUi(this)
        terminalView.removeCallbacks(terminalViewRefreshRunnable)
        synchronized(terminalRefreshLock) {
            uiRefreshScheduled = false
            uiRefreshDirty = false
        }
        stopRepeatingKey()
        terminalView.setTerminalCursorBlinkerState(false, false)
        super.onDestroyView()
    }

    private fun sendSurfaceEffect(effect: SurfaceEffect) {
        TerminalSurfaceResultContract.send(this, effect)
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
        setTerminalOutputFollow(false)
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
        applyTerminalCanvasBackground()
        setTerminalOutputFollow(true)
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
        pasteFromClipboard()
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
