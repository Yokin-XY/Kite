package com.kite.app.ui.main

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.kite.app.R
import com.kite.app.foundation.bootstrap.BootstrapCoordinator
import com.kite.app.foundation.bootstrap.KFApplication
import com.kite.app.foundation.logging.Logger
import com.kite.app.foundation.runtime.ContainerProcessStore
import com.kite.app.foundation.runtime.RuntimeAutomationActions
import com.kite.app.foundation.runtime.TaskManagerStore
import com.kite.app.foundation.service.WorkstationActionGateway
import com.kite.app.foundation.toolchain.ToolchainPackInstaller
import com.kite.app.foundation.workspace.KFWorkspaceManager
import com.kite.app.foundation.workspace.WorkSurfaceRuntimeBridge
import com.kite.app.ui.bridge.BridgeFragment
import com.kite.app.ui.files.FilesFragment
import com.kite.app.ui.logs.LogActivity
import com.kite.app.ui.status.StatusFragment
import com.kite.app.ui.tasks.TaskManagerFragment
import com.kite.app.ui.terminal.TerminalChromeHost
import com.kite.app.ui.terminal.TerminalFragment
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity(), TerminalChromeHost {

    private enum class AutomationBootstrapPolicy {
        NORMAL,
        SKIP_BOOTSTRAP_WARMUP
    }

    private enum class MainTab(
        val navItemId: Int,
        val fragmentTag: String,
        val titleResId: Int
    ) {
        TERMINAL(R.id.nav_terminal, TAG_TERMINAL, R.string.tab_terminal),
        TASKS(R.id.nav_files, TAG_TASKS, R.string.tab_files),
        STATUS(R.id.nav_status, TAG_STATUS, R.string.tab_status),
        BRIDGE(R.id.nav_bridge, TAG_BRIDGE, R.string.tab_bridge);

        companion object {
            fun fromNavItemId(navItemId: Int): MainTab? {
                return values().firstOrNull { it.navItemId == navItemId }
            }

            fun fromSavedName(name: String?): MainTab {
                return values().firstOrNull { it.name == name } ?: TERMINAL
            }
        }
    }

    companion object {
        private const val KEY_SELECTED_TAB = "selected_tab"
        private const val MAIN_STATE_PREFS = "main_navigation_state"
        private const val PREF_LAST_SELECTED_TAB = "last_selected_tab"
        private const val PREF_AI_ENV_PROMPT_SHOWN = "ai_env_prompt_shown"
        private const val PREF_PROOT_PROBE_SAFETY_LOCK = "proot_probe_safety_lock"
        private const val PREF_LAST_PROBE_PREPARE_NONCE = "last_probe_prepare_nonce"
        private const val PREF_LAST_PROBE_INJECT_NONCE = "last_probe_inject_nonce"
        private const val TAG_TERMINAL = "terminal"
        private const val TAG_TASKS = "tasks"
        private const val TAG_FILES = "files"
        private const val TAG_STATUS = "status"
        private const val TAG_BRIDGE = "bridge"

        private const val EXTRA_AUTOMATION_BACKGROUND_ACTION = "background_action"
        private const val EXTRA_AUTOMATION_RUNTIME_ID = "background_runtime_id"
        private const val EXTRA_AUTOMATION_TERMINAL_ACTION = "terminal_action"
        private const val EXTRA_AUTOMATION_AGENT_RUNTIME_ID = "agent_runtime_id"
        private const val EXTRA_AUTOMATION_TERMINAL_SESSION_ID = "terminal_session_id"
        private const val EXTRA_AUTOMATION_TERMINAL_COMMAND = "terminal_command"
        private const val EXTRA_AUTOMATION_COMMAND = "command"
        private const val EXTRA_AUTOMATION_PAYLOAD = "payload"
        private const val EXTRA_AUTOMATION_ENV_ACTION = "env_action"
        private const val EXTRA_AUTOMATION_RUNTIME_ACTION = "runtime_action"
        private const val EXTRA_AUTOMATION_PROBE_TARGET_LIVE_TRACEES = "probe_target_live_tracees"
        private const val EXTRA_AUTOMATION_PROBE_INSTANCE_COUNT = "probe_instance_count"
        private const val EXTRA_AUTOMATION_PROBE_TRACEES_PER_INSTANCE = "probe_tracees_per_instance"
        private const val EXTRA_AUTOMATION_PROBE_DURATION_SECONDS = "probe_duration_seconds"
        private const val EXTRA_AUTOMATION_PROBE_PREPARE_NONCE = "probe_prepare_nonce"
        private const val EXTRA_AUTOMATION_PROBE_INJECT_NONCE = "probe_inject_nonce"
        private const val EXTRA_AUTOMATION_PROCESS_ACTION = "process_action"
        private const val EXTRA_AUTOMATION_PROCESS_PID = "process_pid"
        private const val EXTRA_AUTOMATION_TOOLCHAIN_ACTION = "toolchain_action"
        private const val ACTION_START = "start"
        private const val ACTION_STOP = "stop"
        private const val ACTION_RESTART = "restart"
        private const val ACTION_KILL = "kill"
        private const val ACTION_REFRESH = "refresh"
        private const val ACTION_NEW_SESSION = "new_session"
        private const val ACTION_OPEN_AGENT = "open_agent"
        private const val ACTION_SEND_COMMAND = "send_command"
        private const val ACTION_RUN_COMMAND = "run_command"
        private const val ACTION_PASTE_MULTILINE = "paste_multiline"
        private const val ACTION_DOCTOR = "doctor"
        private const val ACTION_COMPAT_SMOKE = "compat_smoke"
        private const val ACTION_DUMP_DIAGNOSTICS = "dump_diagnostics"
        private const val ACTION_ROTATE_PROOT_TELEMETRY = "rotate_proot_telemetry"
        private const val ACTION_REFRESH_PROOT_TELEMETRY_HEARTBEAT = "refresh_proot_telemetry_heartbeat"
        private const val ACTION_RESET_PROOT_DEVICE_CALIBRATION = "reset_proot_device_calibration"
        private const val ACTION_RUN_PROOT_DEVICE_CALIBRATION = "run_proot_device_calibration"
        private const val ACTION_PREPARE_PROOT_LIVE_TRACEE_PROBE = "prepare_proot_live_tracee_probe"
        private const val ACTION_INJECT_PROOT_LIVE_TRACEE_PROBE = "inject_proot_live_tracee_probe"
        private const val ACTION_PREPARE_PROOT_INSTANCE_PROBE = "prepare_proot_instance_probe"
        private const val ACTION_START_PROOT_INSTANCE_PROBE = "start_proot_instance_probe"
        private const val ACTION_CLEANUP_PROOT_INSTANCE_PROBE = "cleanup_proot_instance_probe"
        private const val ACTION_CLEAR_PROOT_PROBE_SAFETY_LOCK = "clear_proot_probe_safety_lock"
        private const val ACTION_PREPARE_AI_ENV = "prepare_ai_env"
        private const val DEFAULT_BOOTSTRAP_AFTER_FIRST_FRAME_DELAY_MS = 1500L
    }

    private lateinit var bottomNav: BottomNavigationView
    private lateinit var tvContainerStatus: TextView
    private lateinit var tvImageStatus: TextView
    private lateinit var tvNetworkStatus: TextView
    private lateinit var toolbar: MaterialToolbar
    private lateinit var statusBar: View

    private lateinit var terminalFragment: TerminalFragment
    private lateinit var taskManagerFragment: TaskManagerFragment
    private lateinit var filesFragment: FilesFragment
    private lateinit var statusFragment: StatusFragment
    private lateinit var bridgeFragment: BridgeFragment
    private lateinit var activeFragment: Fragment

    private var currentTab: MainTab = MainTab.TERMINAL
    private var isTerminalDetailMode = false
    private var suppressNavigationCallback = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        KFApplication.markLaunchStage("MainActivity", "onCreate start")
        setContentView(R.layout.activity_main)
        KFApplication.markLaunchStage("MainActivity", "setContentView complete")

        setupViews()
        setupToolbar()
        setupBottomNavigation()
        initFragments()
        requestPermissions()
        val bootstrapPolicy = handleAutomationIntent(intent)
        val prootProbeSafetyLockActive = isProotProbeSafetyLockActive()
        observeContainerState()
        observeSpaceState()

        currentTab = restoreSelectedTab(savedInstanceState)
        renderTab(currentTab, force = true)
        KFApplication.markLaunchStage("MainActivity", "initial tab rendered: ${currentTab.name}")
        scheduleDefaultBootstrapAfterFirstFrame(
            bootstrapPolicy = bootstrapPolicy,
            prootProbeSafetyLockActive = prootProbeSafetyLockActive
        )
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(KEY_SELECTED_TAB, currentTab.name)
        super.onSaveInstanceState(outState)
    }

    override fun onPostResume() {
        super.onPostResume()
        renderTab(currentTab, force = true)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleAutomationIntent(intent)
    }

    private fun scheduleDefaultBootstrapAfterFirstFrame(
        bootstrapPolicy: AutomationBootstrapPolicy,
        prootProbeSafetyLockActive: Boolean
    ) {
        when {
            bootstrapPolicy != AutomationBootstrapPolicy.NORMAL -> {
                Logger.i("MainActivity", "Skip default bootstrap warmup for automation intent")
            }
            prootProbeSafetyLockActive -> {
                Logger.i("MainActivity", "Skip default bootstrap warmup because PRoot probe safety lock is active")
            }
            else -> {
                window.decorView.postDelayed(
                    {
                        Logger.i("MainActivity", "Start default bootstrap warmup after first UI frame")
                        BootstrapCoordinator.ensureStarted(applicationContext)
                    },
                    DEFAULT_BOOTSTRAP_AFTER_FIRST_FRAME_DELAY_MS
                )
            }
        }
    }

    private fun setupViews() {
        tvContainerStatus = findViewById(R.id.tvContainerStatus)
        tvImageStatus = findViewById(R.id.tvApiStatus)
        tvNetworkStatus = findViewById(R.id.tvMemory)
        toolbar = findViewById(R.id.toolbar)
        statusBar = findViewById(R.id.statusBar)
    }

    private fun setupToolbar() {
        toolbar.subtitle = "手机里的 Linux 容器"
        toolbar.setTitleTextColor(ContextCompat.getColor(this, R.color.text_primary))
        toolbar.setSubtitleTextColor(ContextCompat.getColor(this, R.color.text_secondary))
        toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_files -> {
                    Logger.i("MainActivity", "Open files page")
                    showFilesPage()
                    true
                }

                R.id.action_logs -> {
                    Logger.i("MainActivity", "Open logs page")
                    startActivity(Intent(this, LogActivity::class.java))
                    true
                }

                else -> false
            }
        }
    }

    private fun setupBottomNavigation() {
        bottomNav = findViewById(R.id.bottomNav)
        bottomNav.setOnItemSelectedListener { item ->
            if (suppressNavigationCallback) {
                true
            } else {
                MainTab.fromNavItemId(item.itemId)?.let { tab ->
                    renderTab(tab)
                    true
                } ?: false
            }
        }
    }

    private fun initFragments() {
        terminalFragment = supportFragmentManager.findFragmentByTag(TAG_TERMINAL) as? TerminalFragment
            ?: TerminalFragment()
        taskManagerFragment = supportFragmentManager.findFragmentByTag(TAG_TASKS) as? TaskManagerFragment
            ?: TaskManagerFragment()
        filesFragment = supportFragmentManager.findFragmentByTag(TAG_FILES) as? FilesFragment
            ?: FilesFragment()
        statusFragment = supportFragmentManager.findFragmentByTag(TAG_STATUS) as? StatusFragment
            ?: StatusFragment()
        bridgeFragment = supportFragmentManager.findFragmentByTag(TAG_BRIDGE) as? BridgeFragment
            ?: BridgeFragment()
    }

    private fun renderTab(tab: MainTab, force: Boolean = false) {
        val target = fragmentForTab(tab)
        val alreadyShowing = ::activeFragment.isInitialized &&
            activeFragment == target &&
            currentTab == tab

        currentTab = tab
        persistSelectedTab(tab)
        if (bottomNav.selectedItemId != tab.navItemId) {
            suppressNavigationCallback = true
            bottomNav.selectedItemId = tab.navItemId
            suppressNavigationCallback = false
        }
        if (tab != MainTab.TERMINAL) {
            isTerminalDetailMode = false
        }
        toolbar.title = getString(tab.titleResId)

        if (!force && alreadyShowing) {
            updateChromeForTab(tab)
            return
        }

        val transaction = supportFragmentManager.beginTransaction()
        allMainFragments().forEach { fragment ->
            if (fragment.isAdded) {
                transaction.hide(fragment)
                transaction.setMaxLifecycle(fragment, Lifecycle.State.STARTED)
            }
        }
        if (!target.isAdded) {
            transaction.add(R.id.fragmentContainer, target, tab.fragmentTag)
        }
        transaction
            .show(target)
            .setMaxLifecycle(target, Lifecycle.State.RESUMED)
            .commit()
        activeFragment = target
        updateChromeForTab(tab)
    }

    private fun showFilesPage() {
        val transaction = supportFragmentManager.beginTransaction()
        allMainFragments().forEach { fragment ->
            if (fragment.isAdded) {
                transaction.hide(fragment)
                transaction.setMaxLifecycle(fragment, Lifecycle.State.STARTED)
            }
        }
        if (!filesFragment.isAdded) {
            transaction.add(R.id.fragmentContainer, filesFragment, TAG_FILES)
        }
        transaction
            .show(filesFragment)
            .setMaxLifecycle(filesFragment, Lifecycle.State.RESUMED)
            .commit()
        activeFragment = filesFragment
        isTerminalDetailMode = false
        toolbar.title = getString(R.string.files_exchange)
        updateChromeForStandaloneFragment()
    }

    private fun allMainFragments(): List<Fragment> {
        return listOf(
            terminalFragment,
            taskManagerFragment,
            filesFragment,
            statusFragment,
            bridgeFragment
        )
    }

    private fun fragmentForTab(tab: MainTab): Fragment {
        return when (tab) {
            MainTab.TERMINAL -> terminalFragment
            MainTab.TASKS -> taskManagerFragment
            MainTab.STATUS -> statusFragment
            MainTab.BRIDGE -> bridgeFragment
        }
    }

    private fun restoreSelectedTab(savedInstanceState: Bundle?): MainTab {
        val savedTab = savedInstanceState?.getString(KEY_SELECTED_TAB)
        if (!savedTab.isNullOrBlank()) {
            return MainTab.fromSavedName(savedTab)
        }
        return MainTab.TERMINAL
    }

    private fun persistSelectedTab(tab: MainTab) {
        getSharedPreferences(MAIN_STATE_PREFS, MODE_PRIVATE)
            .edit()
            .putString(PREF_LAST_SELECTED_TAB, tab.name)
            .apply()
    }

    private fun updateChromeForTab(tab: MainTab) {
        val isTerminal = tab == MainTab.TERMINAL
        val isTaskManager = tab == MainTab.TASKS
        val hideOuterChrome = isTerminal && isTerminalDetailMode
        val hideTopChrome = isTerminal || isTaskManager

        statusBar.visibility = if (hideTopChrome) View.GONE else View.VISIBLE
        toolbar.visibility = if (hideTopChrome) View.GONE else View.VISIBLE
        bottomNav.visibility = if (hideOuterChrome) View.GONE else View.VISIBLE
        if (isTerminal || isTaskManager) {
            val terminalNavColors = ContextCompat.getColorStateList(this, R.color.bottom_nav_terminal_color)
            bottomNav.setBackgroundColor(ContextCompat.getColor(this, R.color.terminal_page_surface))
            bottomNav.itemIconTintList = terminalNavColors
            bottomNav.itemTextColor = terminalNavColors
        } else {
            val defaultNavColors = ContextCompat.getColorStateList(this, R.color.bottom_nav_color)
            bottomNav.setBackgroundColor(ContextCompat.getColor(this, R.color.surface))
            bottomNav.itemIconTintList = defaultNavColors
            bottomNav.itemTextColor = defaultNavColors
        }
    }

    private fun updateChromeForStandaloneFragment() {
        statusBar.visibility = View.VISIBLE
        toolbar.visibility = View.VISIBLE
        bottomNav.visibility = View.VISIBLE
        val defaultNavColors = ContextCompat.getColorStateList(this, R.color.bottom_nav_color)
        bottomNav.setBackgroundColor(ContextCompat.getColor(this, R.color.surface))
        bottomNav.itemIconTintList = defaultNavColors
        bottomNav.itemTextColor = defaultNavColors
    }

    private fun handleAutomationIntent(intent: Intent?): AutomationBootstrapPolicy {
        var bootstrapPolicy = AutomationBootstrapPolicy.NORMAL
        val backgroundAction = intent?.getStringExtra(EXTRA_AUTOMATION_BACKGROUND_ACTION)?.trim().orEmpty()
        val runtimeId = intent?.getStringExtra(EXTRA_AUTOMATION_RUNTIME_ID)?.trim().orEmpty()
        if (backgroundAction.isNotBlank() && runtimeId.isNotBlank()) {
            bootstrapPolicy = AutomationBootstrapPolicy.SKIP_BOOTSTRAP_WARMUP
            Logger.i("MainActivity", "Background automation: action=$backgroundAction, runtimeId=$runtimeId")
            when (backgroundAction.lowercase()) {
                ACTION_START -> WorkstationActionGateway.startBackgroundRuntime(applicationContext, runtimeId)
                ACTION_STOP -> WorkstationActionGateway.stopBackgroundRuntime(applicationContext, runtimeId)
                ACTION_RESTART -> WorkstationActionGateway.restartBackgroundRuntime(applicationContext, runtimeId)
                else -> Logger.i("MainActivity", "Ignore unknown background action: $backgroundAction")
            }
            intent?.removeExtra(EXTRA_AUTOMATION_BACKGROUND_ACTION)
            intent?.removeExtra(EXTRA_AUTOMATION_RUNTIME_ID)
        }

        val processAction = intent?.getStringExtra(EXTRA_AUTOMATION_PROCESS_ACTION)?.trim().orEmpty()
        val processPid = when {
            intent == null || !intent.hasExtra(EXTRA_AUTOMATION_PROCESS_PID) -> null
            else -> {
                val rawInt = intent.getIntExtra(EXTRA_AUTOMATION_PROCESS_PID, Int.MIN_VALUE)
                if (rawInt != Int.MIN_VALUE) {
                    rawInt
                } else {
                    intent.getStringExtra(EXTRA_AUTOMATION_PROCESS_PID)?.trim()?.toIntOrNull()
                }
            }
        }
        if (intent != null && processAction.equals(ACTION_REFRESH, ignoreCase = true)) {
            bootstrapPolicy = AutomationBootstrapPolicy.SKIP_BOOTSTRAP_WARMUP
            TaskManagerStore.refresh(applicationContext)
            intent.removeExtra(EXTRA_AUTOMATION_PROCESS_ACTION)
            intent.removeExtra(EXTRA_AUTOMATION_PROCESS_PID)
        } else if (intent != null && processAction.isNotBlank() && processPid != null && processPid > 0) {
            bootstrapPolicy = AutomationBootstrapPolicy.SKIP_BOOTSTRAP_WARMUP
            when (processAction.lowercase()) {
                ACTION_STOP -> TaskManagerStore.endProcess(applicationContext, processPid)
                ACTION_KILL -> ContainerProcessStore.terminate(applicationContext, processPid, force = true)
                else -> Logger.i("MainActivity", "Ignore unknown process action: $processAction")
            }
            intent.removeExtra(EXTRA_AUTOMATION_PROCESS_ACTION)
            intent.removeExtra(EXTRA_AUTOMATION_PROCESS_PID)
        }

        val envAction = intent?.getStringExtra(EXTRA_AUTOMATION_ENV_ACTION)?.trim().orEmpty()
        if (envAction.isNotBlank()) {
            bootstrapPolicy = AutomationBootstrapPolicy.SKIP_BOOTSTRAP_WARMUP
            when (envAction.lowercase()) {
                ACTION_DOCTOR -> RuntimeAutomationActions.runEnvDoctor(applicationContext)
                ACTION_COMPAT_SMOKE -> RuntimeAutomationActions.runCompatSmoke(applicationContext)
                else -> Logger.i("MainActivity", "Ignore unknown env action: $envAction")
            }
            intent?.removeExtra(EXTRA_AUTOMATION_ENV_ACTION)
        }

        val runtimeAction = intent?.getStringExtra(EXTRA_AUTOMATION_RUNTIME_ACTION)?.trim().orEmpty()
        if (runtimeAction.isNotBlank()) {
            bootstrapPolicy = AutomationBootstrapPolicy.SKIP_BOOTSTRAP_WARMUP
            val probeTargetLiveTracees = readProbeTargetLiveTracees(intent)
            when (runtimeAction.lowercase()) {
                ACTION_DUMP_DIAGNOSTICS -> RuntimeAutomationActions.dumpDiagnostics(applicationContext)
                ACTION_ROTATE_PROOT_TELEMETRY -> RuntimeAutomationActions.rotateProotTelemetry(applicationContext)
                ACTION_REFRESH_PROOT_TELEMETRY_HEARTBEAT -> {
                    RuntimeAutomationActions.refreshProotTelemetryHeartbeat(applicationContext)
                }
                ACTION_RESET_PROOT_DEVICE_CALIBRATION -> {
                    RuntimeAutomationActions.resetProotDeviceCalibration(applicationContext)
                }
                ACTION_RUN_PROOT_DEVICE_CALIBRATION -> {
                    RuntimeAutomationActions.runProotCalibration(applicationContext, "p0")
                }
                ACTION_PREPARE_PROOT_LIVE_TRACEE_PROBE -> handlePrepareProotLiveTraceeProbe(
                    intent = intent,
                    targetLiveTracees = probeTargetLiveTracees
                )
                ACTION_INJECT_PROOT_LIVE_TRACEE_PROBE -> handleInjectProotLiveTraceeProbe(
                    intent = intent,
                    targetLiveTracees = probeTargetLiveTracees
                )
                ACTION_PREPARE_PROOT_INSTANCE_PROBE -> handlePrepareProotInstanceProbe(intent)
                ACTION_START_PROOT_INSTANCE_PROBE -> handleStartProotInstanceProbe(intent)
                ACTION_CLEANUP_PROOT_INSTANCE_PROBE -> {
                    RuntimeAutomationActions.cleanupProotInstanceProbe(applicationContext)
                }
                ACTION_CLEAR_PROOT_PROBE_SAFETY_LOCK -> {
                    setProotProbeSafetyLock(false)
                    Logger.i("MainActivity", "PRoot probe safety lock cleared by automation intent")
                }
                else -> Logger.i("MainActivity", "Ignore unknown runtime action: $runtimeAction")
            }
            intent?.removeExtra(EXTRA_AUTOMATION_RUNTIME_ACTION)
            intent?.removeExtra(EXTRA_AUTOMATION_PROBE_TARGET_LIVE_TRACEES)
            intent?.removeExtra(EXTRA_AUTOMATION_PROBE_INSTANCE_COUNT)
            intent?.removeExtra(EXTRA_AUTOMATION_PROBE_TRACEES_PER_INSTANCE)
            intent?.removeExtra(EXTRA_AUTOMATION_PROBE_DURATION_SECONDS)
            intent?.removeExtra(EXTRA_AUTOMATION_PROBE_PREPARE_NONCE)
            intent?.removeExtra(EXTRA_AUTOMATION_PROBE_INJECT_NONCE)
        }

        val toolchainAction = intent?.getStringExtra(EXTRA_AUTOMATION_TOOLCHAIN_ACTION)?.trim().orEmpty()
        if (toolchainAction.isNotBlank()) {
            bootstrapPolicy = AutomationBootstrapPolicy.SKIP_BOOTSTRAP_WARMUP
            when (toolchainAction.lowercase()) {
                ACTION_PREPARE_AI_ENV -> ToolchainPackInstaller.prepareAiEnv(applicationContext)
                ACTION_DOCTOR -> ToolchainPackInstaller.doctor(applicationContext)
                else -> Logger.i("MainActivity", "Ignore unknown toolchain action: $toolchainAction")
            }
            intent?.removeExtra(EXTRA_AUTOMATION_TOOLCHAIN_ACTION)
        }

        val terminalAction = intent?.getStringExtra(EXTRA_AUTOMATION_TERMINAL_ACTION)?.trim().orEmpty()
        val agentRuntimeId = intent?.getStringExtra(EXTRA_AUTOMATION_AGENT_RUNTIME_ID)?.trim().orEmpty()
        val terminalSessionId = intent?.getStringExtra(EXTRA_AUTOMATION_TERMINAL_SESSION_ID)?.trim().orEmpty()
        val terminalCommand = intent?.getStringExtra(EXTRA_AUTOMATION_TERMINAL_COMMAND)
            ?: intent?.getStringExtra(EXTRA_AUTOMATION_COMMAND)
            ?: ""
        val terminalPayload = intent?.getStringExtra(EXTRA_AUTOMATION_PAYLOAD).orEmpty()
        if (terminalAction.isBlank()) {
            return bootstrapPolicy
        }

        bootstrapPolicy = AutomationBootstrapPolicy.SKIP_BOOTSTRAP_WARMUP
        Logger.i(
            "MainActivity",
            "Terminal automation: action=$terminalAction, agentRuntimeId=$agentRuntimeId, sessionId=$terminalSessionId"
        )
        when (terminalAction.lowercase()) {
            ACTION_NEW_SESSION -> WorkstationActionGateway.createShellSession(applicationContext)
            ACTION_STOP -> WorkstationActionGateway.endTerminalSession(
                applicationContext,
                terminalSessionId.ifBlank { null }
            )
            ACTION_SEND_COMMAND -> {
                if (terminalCommand.isNotBlank()) {
                    WorkstationActionGateway.sendTerminalCommand(
                        context = applicationContext,
                        command = terminalCommand,
                        sessionId = terminalSessionId.ifBlank { null }
                    )
                }
            }
            ACTION_RUN_COMMAND -> {
                if (terminalCommand.isNotBlank()) {
                    RuntimeAutomationActions.runOneShotCommand(
                        context = applicationContext,
                        command = terminalCommand
                    )
                }
            }
            ACTION_PASTE_MULTILINE -> {
                val payload = terminalPayload.ifBlank { terminalCommand }
                if (payload.isNotEmpty() &&
                    RuntimeAutomationActions.isEnabled(applicationContext, ACTION_PASTE_MULTILINE)
                ) {
                    RuntimeAutomationActions.logPasteRequest(
                        context = applicationContext,
                        payload = payload,
                        sessionId = terminalSessionId.ifBlank { null }
                    )
                    WorkstationActionGateway.pasteTerminalInput(
                        context = applicationContext,
                        payload = payload
                    )
                }
            }
            ACTION_OPEN_AGENT -> {
                if (agentRuntimeId.isNotBlank()) {
                    WorkstationActionGateway.launchAgentSession(applicationContext, agentRuntimeId)
                }
            }
            else -> Logger.i("MainActivity", "Ignore unknown terminal action: $terminalAction")
        }

        intent?.removeExtra(EXTRA_AUTOMATION_TERMINAL_ACTION)
        intent?.removeExtra(EXTRA_AUTOMATION_AGENT_RUNTIME_ID)
        intent?.removeExtra(EXTRA_AUTOMATION_TERMINAL_SESSION_ID)
        intent?.removeExtra(EXTRA_AUTOMATION_TERMINAL_COMMAND)
        intent?.removeExtra(EXTRA_AUTOMATION_COMMAND)
        intent?.removeExtra(EXTRA_AUTOMATION_PAYLOAD)
        return bootstrapPolicy
    }

    private fun readProbeTargetLiveTracees(intent: Intent?): Int {
        if (intent == null || !intent.hasExtra(EXTRA_AUTOMATION_PROBE_TARGET_LIVE_TRACEES)) return 4
        val rawInt = intent.getIntExtra(EXTRA_AUTOMATION_PROBE_TARGET_LIVE_TRACEES, Int.MIN_VALUE)
        if (rawInt != Int.MIN_VALUE) return rawInt.coerceAtLeast(0)
        return intent.getStringExtra(EXTRA_AUTOMATION_PROBE_TARGET_LIVE_TRACEES)
            ?.trim()
            ?.toIntOrNull()
            ?.coerceAtLeast(0)
            ?: 4
    }

    private fun readAutomationIntExtra(intent: Intent?, key: String, defaultValue: Int): Int {
        if (intent == null || !intent.hasExtra(key)) return defaultValue
        val rawInt = intent.getIntExtra(key, Int.MIN_VALUE)
        if (rawInt != Int.MIN_VALUE) return rawInt
        return intent.getStringExtra(key)?.trim()?.toIntOrNull() ?: defaultValue
    }

    private fun handlePrepareProotLiveTraceeProbe(intent: Intent?, targetLiveTracees: Int) {
        setProotProbeSafetyLock(true)
        val nonce = intent?.getStringExtra(EXTRA_AUTOMATION_PROBE_PREPARE_NONCE)?.trim().orEmpty()
        if (nonce.isBlank()) {
            Logger.i("MainActivity", "Blocked prepare_proot_live_tracee_probe without nonce; safety lock enabled")
            return
        }
        val prefs = getSharedPreferences(MAIN_STATE_PREFS, MODE_PRIVATE)
        val lastNonce = prefs.getString(PREF_LAST_PROBE_PREPARE_NONCE, "")
        if (lastNonce == nonce) {
            Logger.i("MainActivity", "Ignore duplicate prepare_proot_live_tracee_probe nonce=$nonce")
            return
        }
        prefs.edit().putString(PREF_LAST_PROBE_PREPARE_NONCE, nonce).apply()
        RuntimeAutomationActions.prepareProotLiveTraceeProbe(
            context = applicationContext,
            targetLiveTracees = targetLiveTracees
        )
    }

    private fun handleInjectProotLiveTraceeProbe(intent: Intent?, targetLiveTracees: Int) {
        val nonce = intent?.getStringExtra(EXTRA_AUTOMATION_PROBE_INJECT_NONCE)?.trim().orEmpty()
        if (nonce.isBlank()) {
            Logger.i("MainActivity", "Blocked inject_proot_live_tracee_probe without nonce")
            return
        }
        if (!isProotProbeSafetyLockActive()) {
            Logger.i("MainActivity", "Blocked inject_proot_live_tracee_probe without safety lock")
            return
        }
        val prefs = getSharedPreferences(MAIN_STATE_PREFS, MODE_PRIVATE)
        val lastNonce = prefs.getString(PREF_LAST_PROBE_INJECT_NONCE, "")
        if (lastNonce == nonce) {
            Logger.i("MainActivity", "Ignore duplicate inject_proot_live_tracee_probe nonce=$nonce")
            return
        }
        prefs.edit().putString(PREF_LAST_PROBE_INJECT_NONCE, nonce).apply()
        RuntimeAutomationActions.injectProotLiveTraceeProbe(
            context = applicationContext,
            targetLiveTracees = targetLiveTracees
        )
    }

    private fun handlePrepareProotInstanceProbe(intent: Intent?) {
        val nonce = intent?.getStringExtra(EXTRA_AUTOMATION_PROBE_PREPARE_NONCE)?.trim().orEmpty()
        if (nonce.isBlank()) {
            Logger.i("MainActivity", "Blocked prepare_proot_instance_probe without nonce")
            return
        }
        val prefs = getSharedPreferences(MAIN_STATE_PREFS, MODE_PRIVATE)
        val lastNonce = prefs.getString(PREF_LAST_PROBE_PREPARE_NONCE, "")
        if (lastNonce == nonce) {
            Logger.i("MainActivity", "Ignore duplicate prepare_proot_instance_probe nonce=$nonce")
            return
        }
        prefs.edit().putString(PREF_LAST_PROBE_PREPARE_NONCE, nonce).apply()
        RuntimeAutomationActions.prepareProotInstanceProbe(
            context = applicationContext,
            instanceCount = readAutomationIntExtra(intent, EXTRA_AUTOMATION_PROBE_INSTANCE_COUNT, 1),
            traceesPerInstance = readAutomationIntExtra(intent, EXTRA_AUTOMATION_PROBE_TRACEES_PER_INSTANCE, 17),
            durationSeconds = readAutomationIntExtra(intent, EXTRA_AUTOMATION_PROBE_DURATION_SECONDS, 180)
        )
    }

    private fun handleStartProotInstanceProbe(intent: Intent?) {
        val nonce = intent?.getStringExtra(EXTRA_AUTOMATION_PROBE_INJECT_NONCE)?.trim().orEmpty()
        if (nonce.isBlank()) {
            Logger.i("MainActivity", "Blocked start_proot_instance_probe without nonce")
            return
        }
        val prefs = getSharedPreferences(MAIN_STATE_PREFS, MODE_PRIVATE)
        val lastNonce = prefs.getString(PREF_LAST_PROBE_INJECT_NONCE, "")
        if (lastNonce == nonce) {
            Logger.i("MainActivity", "Ignore duplicate start_proot_instance_probe nonce=$nonce")
            return
        }
        prefs.edit().putString(PREF_LAST_PROBE_INJECT_NONCE, nonce).apply()
        RuntimeAutomationActions.startProotInstanceProbe(
            context = applicationContext,
            instanceCount = readAutomationIntExtra(intent, EXTRA_AUTOMATION_PROBE_INSTANCE_COUNT, 1),
            traceesPerInstance = readAutomationIntExtra(intent, EXTRA_AUTOMATION_PROBE_TRACEES_PER_INSTANCE, 17),
            durationSeconds = readAutomationIntExtra(intent, EXTRA_AUTOMATION_PROBE_DURATION_SECONDS, 180)
        )
    }

    private fun isProotProbeSafetyLockActive(): Boolean {
        return getSharedPreferences(MAIN_STATE_PREFS, MODE_PRIVATE)
            .getBoolean(PREF_PROOT_PROBE_SAFETY_LOCK, false)
    }

    private fun setProotProbeSafetyLock(active: Boolean) {
        getSharedPreferences(MAIN_STATE_PREFS, MODE_PRIVATE)
            .edit()
            .putBoolean(PREF_PROOT_PROBE_SAFETY_LOCK, active)
            .apply()
    }

    private fun observeContainerState() {
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                WorkSurfaceRuntimeBridge.containerState.collect { container ->
                    val imageReady = WorkSurfaceRuntimeBridge.isBaseImageReady(this@MainActivity)
                    tvContainerStatus.text = "容器\n${container?.status?.label ?: "未初始化"}"
                    tvImageStatus.text = "镜像\n${if (imageReady) "已就绪" else "未解压"}"
                    tvNetworkStatus.text = "网络\n${container?.networkMode?.label ?: "--"}"
                    maybeShowAiEnvironmentPrompt(imageReady && container != null)
                }
            }
        }
    }

    private fun maybeShowAiEnvironmentPrompt(runtimeReady: Boolean) {
        if (!runtimeReady || isFinishing) return
        val prefs = getSharedPreferences(MAIN_STATE_PREFS, MODE_PRIVATE)
        if (prefs.getBoolean(PREF_AI_ENV_PROMPT_SHOWN, false)) return
        prefs.edit().putBoolean(PREF_AI_ENV_PROMPT_SHOWN, true).apply()
        MaterialAlertDialogBuilder(this)
            .setTitle("推荐检查 KF 工具环境")
            .setMessage(
                "检查并修复后，Node、npm、pnpm、uv、Python venv/pip 和常用命令会更稳定。\n\n" +
                    "不处理也可以正常使用终端，稍后可在“环境”页面再次执行。"
            )
            .setNegativeButton("稍后再说", null)
            .setPositiveButton("检查并修复") { _, _ ->
                ToolchainPackInstaller.prepareAiEnv(applicationContext)
                renderTab(MainTab.BRIDGE)
            }
            .show()
    }

    private fun observeSpaceState() {
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                KFWorkspaceManager.currentSpaceState.collect { space ->
                    toolbar.subtitle = space?.let { "当前空间：${it.displayName}" } ?: "手机里的 Linux 容器"
                }
            }
        }
    }

    override fun setTerminalDetailMode(enabled: Boolean) {
        isTerminalDetailMode = enabled
        if (::activeFragment.isInitialized && activeFragment == terminalFragment) {
            updateChromeForTab(MainTab.TERMINAL)
        }
    }

    override fun openTerminalSession(sessionId: String) {
        Logger.i("MainActivity", "Open linked terminal session from task page: $sessionId")
        renderTab(MainTab.TERMINAL, force = true)
        resolveTerminalFragment().openSessionFromExternal(sessionId)
    }

    private fun requestPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.INTERNET,
            Manifest.permission.FOREGROUND_SERVICE,
            Manifest.permission.WAKE_LOCK,
            Manifest.permission.CAMERA
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions += Manifest.permission.READ_MEDIA_IMAGES
            permissions += Manifest.permission.READ_MEDIA_VIDEO
            permissions += Manifest.permission.READ_MEDIA_AUDIO
        } else {
            permissions += Manifest.permission.READ_EXTERNAL_STORAGE
        }

        val missingPermissions = permissions
            .filter { permission ->
                ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED
            }
            .toTypedArray()
        if (missingPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missingPermissions, 0)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            runCatching {
                startActivity(
                    Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                        data = Uri.parse("package:$packageName")
                    }
                )
            }.onFailure { throwable ->
                Logger.i("MainActivity", "Unable to open all-files permission settings: ${throwable.message}")
            }
        }
    }

    private fun resolveTerminalFragment(): TerminalFragment {
        return supportFragmentManager.findFragmentByTag(TAG_TERMINAL) as? TerminalFragment
            ?: terminalFragment
    }
}
