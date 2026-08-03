package com.kite.app.foundation.service

import android.app.ActivityManager
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.kite.app.R
import com.kite.app.foundation.bootstrap.KFApplication
import com.kite.app.foundation.logging.Logger
import com.kite.app.foundation.runtime.AndroidShellBridgeWorker
import com.kite.app.foundation.runtime.HostSelfAdbBridgeWorker
import com.kite.app.foundation.runtime.RuntimeOverviewStore
import com.kite.app.foundation.runtime.RuntimeFrameCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 任务入口层宿主，负责把通知、Intent 和自动化请求转发到对应的工作面宿主。
 *
 * 这里不直接拼容器参数；只保证入口被串到正确的 terminal/runtime host。
 */
class KFShellService : Service() {

    companion object {
        private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private const val ACTION_START_BACKGROUND_RUNTIME =
            "com.kite.app.foundation.service.action.START_BACKGROUND_RUNTIME"
        private const val ACTION_STOP_BACKGROUND_RUNTIME =
            "com.kite.app.foundation.service.action.STOP_BACKGROUND_RUNTIME"
        private const val ACTION_RESTART_BACKGROUND_RUNTIME =
            "com.kite.app.foundation.service.action.RESTART_BACKGROUND_RUNTIME"
        private const val ACTION_CREATE_SHELL_SESSION =
            "com.kite.app.foundation.service.action.CREATE_SHELL_SESSION"
        private const val ACTION_LAUNCH_AGENT_SESSION =
            "com.kite.app.foundation.service.action.LAUNCH_AGENT_SESSION"
        private const val ACTION_SEND_TERMINAL_COMMAND =
            "com.kite.app.foundation.service.action.SEND_TERMINAL_COMMAND"
        private const val ACTION_PASTE_TERMINAL_INPUT =
            "com.kite.app.foundation.service.action.PASTE_TERMINAL_INPUT"
        private const val ACTION_END_TERMINAL_SESSION =
            "com.kite.app.foundation.service.action.END_TERMINAL_SESSION"
        private const val ACTION_REFRESH_RUNTIME_OVERVIEW =
            "com.kite.app.foundation.service.action.REFRESH_RUNTIME_OVERVIEW"
        private const val ACTION_ENSURE_EXECUTION_HOST_RESIDENT =
            "com.kite.app.foundation.service.action.ENSURE_EXECUTION_HOST_RESIDENT"
        private const val EXTRA_RUNTIME_ID = "runtime_id"
        private const val EXTRA_AGENT_RUNTIME_ID = "agent_runtime_id"
        private const val EXTRA_TERMINAL_SESSION_ID = "terminal_session_id"
        private const val EXTRA_TERMINAL_COMMAND = "terminal_command"
        private const val DEFAULT_START_SUPPRESSION_MS = 12_000L

        @Volatile
        private var suppressDefaultStartUntil = 0L

        fun start(context: Context) {
            val appContext = context.applicationContext
            val intent = Intent(appContext, KFShellService::class.java)
            ContextCompat.startForegroundService(appContext, intent)
        }

        fun startBackgroundRuntime(context: Context, runtimeId: String) {
            enqueueRuntimeAction(context, ACTION_START_BACKGROUND_RUNTIME, runtimeId)
        }

        fun stopBackgroundRuntime(context: Context, runtimeId: String) {
            enqueueRuntimeAction(context, ACTION_STOP_BACKGROUND_RUNTIME, runtimeId)
        }

        fun restartBackgroundRuntime(context: Context, runtimeId: String) {
            enqueueRuntimeAction(context, ACTION_RESTART_BACKGROUND_RUNTIME, runtimeId)
        }

        fun createShellSession(context: Context) {
            val appContext = context.applicationContext
            val intent = Intent(appContext, KFShellService::class.java).apply {
                action = ACTION_CREATE_SHELL_SESSION
            }
            ContextCompat.startForegroundService(appContext, intent)
        }

        fun launchAgentSession(context: Context, runtimeId: String) {
            val appContext = context.applicationContext
            val intent = Intent(appContext, KFShellService::class.java).apply {
                action = ACTION_LAUNCH_AGENT_SESSION
                putExtra(EXTRA_AGENT_RUNTIME_ID, runtimeId)
            }
            ContextCompat.startForegroundService(appContext, intent)
        }

        fun sendTerminalCommand(
            context: Context,
            command: String,
            sessionId: String? = null
        ) {
            val appContext = context.applicationContext
            val intent = Intent(appContext, KFShellService::class.java).apply {
                action = ACTION_SEND_TERMINAL_COMMAND
                putExtra(EXTRA_TERMINAL_COMMAND, command)
                sessionId?.takeIf { it.isNotBlank() }?.let {
                    putExtra(EXTRA_TERMINAL_SESSION_ID, it)
                }
            }
            ContextCompat.startForegroundService(appContext, intent)
        }

        fun pasteTerminalInput(
            context: Context,
            payload: String
        ) {
            val appContext = context.applicationContext
            val intent = Intent(appContext, KFShellService::class.java).apply {
                action = ACTION_PASTE_TERMINAL_INPUT
                putExtra(EXTRA_TERMINAL_COMMAND, payload)
            }
            ContextCompat.startForegroundService(appContext, intent)
        }

        fun endTerminalSession(context: Context, sessionId: String? = null) {
            val appContext = context.applicationContext
            val intent = Intent(appContext, KFShellService::class.java).apply {
                action = ACTION_END_TERMINAL_SESSION
                sessionId?.takeIf { it.isNotBlank() }?.let {
                    putExtra(EXTRA_TERMINAL_SESSION_ID, it)
                }
            }
            ContextCompat.startForegroundService(appContext, intent)
        }

        fun refreshRuntimeOverview(context: Context) {
            val appContext = context.applicationContext
            serviceScope.launch {
                RuntimeOverviewStore.refresh(appContext)
            }
        }

        /**
         * 让直接执行命令及其 Android 网络代理拥有前台进程宿主。
         * 这里只取得现有服务的驻留保障，不触发默认 runtime 暖启动。
         */
        fun ensureExecutionHostResident(context: Context) {
            val appContext = context.applicationContext
            val intent = Intent(appContext, KFShellService::class.java).apply {
                action = ACTION_ENSURE_EXECUTION_HOST_RESIDENT
            }
            ContextCompat.startForegroundService(appContext, intent)
        }

        private fun enqueueRuntimeAction(context: Context, action: String, runtimeId: String) {
            val appContext = context.applicationContext
            if (action == ACTION_START_BACKGROUND_RUNTIME ||
                action == ACTION_STOP_BACKGROUND_RUNTIME ||
                action == ACTION_RESTART_BACKGROUND_RUNTIME
            ) {
                suppressDefaultStartUntil =
                    System.currentTimeMillis() + DEFAULT_START_SUPPRESSION_MS
            }
            val intent = Intent(appContext, KFShellService::class.java).apply {
                this.action = action
                putExtra(EXTRA_RUNTIME_ID, runtimeId)
            }
            ContextCompat.startForegroundService(appContext, intent)
        }

        private fun shouldSuppressDefaultStart(): Boolean {
            return System.currentTimeMillis() < suppressDefaultStartUntil
        }
    }

    private var wakeLock: PowerManager.WakeLock? = null

    @Volatile
    private var hostsReady = false

    @Volatile
    private var hostsReadyJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        acquireWakeLock()
        AndroidShellBridgeWorker.start(applicationContext)
        HostSelfAdbBridgeWorker.start(applicationContext)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = createNotification()
        startForeground(1, notification)
        val initJob = ensureHostsReadyAsync()
        handleRuntimeAction(intent, initJob)
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        forwardTaskRemoval(rootIntent)
        closeCardRunTasksIfMainTaskRemoved(rootIntent)
        Logger.i("KFShellService", "recent task removed; requesting foreground runtime recovery")
        start(applicationContext)
    }

    private fun closeCardRunTasksIfMainTaskRemoved(rootIntent: Intent?) {
        val removedComponent = rootIntent?.component?.className.orEmpty()
        if (removedComponent != KiteTaskContractHost.get().mainActivityClassName) {
            return
        }
        val closedCount = finishCardRunDocumentTasks()
        Logger.i("KFShellService", "main task removed; closed card run document tasks=$closedCount")
    }

    private fun forwardTaskRemoval(rootIntent: Intent?) {
        runCatching {
            KiteTaskContractHost.get().onTaskRemoved(applicationContext, rootIntent)
        }.onFailure { error ->
            Logger.e("KFShellService", "task removal forwarding failed: ${error.message}")
        }
    }

    private fun finishCardRunDocumentTasks(): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return 0
        val manager = getSystemService(ACTIVITY_SERVICE) as? ActivityManager ?: return 0
        val cardRunClassName = KiteTaskContractHost.get().cardRunActivityClassName
        var closedCount = 0
        manager.appTasks.forEach { task ->
            val baseIntent = task.taskInfo.baseIntent
            val isCardRunTask = baseIntent.component?.className == cardRunClassName
            if (isCardRunTask) {
                forwardTaskRemoval(baseIntent)
                task.finishAndRemoveTask()
                closedCount += 1
            }
        }
        return closedCount
    }

    private fun createNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            KiteTaskContractHost.get().buildMainActivityIntent(this),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, KFApplication.CHANNEL_BACKGROUND_RUNTIME)
            .setContentTitle(getString(R.string.service_notification_title))
            .setContentText(getString(R.string.service_notification_text))
            .setSmallIcon(R.drawable.ic_status)
            .setContentIntent(pendingIntent)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setSilent(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_DEFERRED)
            .setOngoing(true)
            .build()
    }

    private fun acquireWakeLock() {
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "KFShell::ContainerWakeLock"
        ).apply {
            acquire(10 * 60 * 60 * 1000L)
        }
    }

    @Synchronized
    private fun ensureHostsReadyAsync(): Job {
        if (hostsReady) {
            return serviceScope.launch { }
        }
        val existing = hostsReadyJob
        if (existing != null && existing.isActive) {
            return existing
        }

        val appContext = applicationContext
        return serviceScope.launch {
            runCatching {
                Logger.i("KFShellService", "后台骨架初始化开始")
                BackgroundRuntimeHost.ensureInitialized(appContext)
                Logger.i("KFShellService", "后台宿主初始化完成")
                hostsReady = true
                Logger.i("KFShellService", "后台宿主基础初始化完成")
            }.onFailure { error ->
                hostsReady = false
                Logger.e("KFShellService", "后台骨架初始化失败: ${error.message}")
            }.also {
                synchronized(this@KFShellService) {
                    hostsReadyJob = null
                }
            }
        }.also { job ->
            hostsReadyJob = job
        }
    }

    private suspend fun ensureOperationalState(appContext: Context) {
        RuntimeFrameCoordinator.ensureOperationalFrame(appContext)
        BackgroundRuntimeHost.ensureResidentRuntimes(appContext, "service-start")
        Logger.i("KFShellService", "后台骨架初始化完成")
    }

    private fun handleRuntimeAction(intent: Intent?, initJob: Job) {
        val appContext = applicationContext
        when (intent?.action) {
            ACTION_CREATE_SHELL_SESSION -> {
                serviceScope.launch {
                    initJob.join()
                    ensureOperationalState(appContext)
                    // Service 是入口层，只做 runtime ready 和动作转发。
                    com.kite.app.foundation.terminal.TerminalRuntimeHost.createShellSession(appContext)
                }
            }

            ACTION_LAUNCH_AGENT_SESSION -> {
                val agentRuntimeId = intent.getStringExtra(EXTRA_AGENT_RUNTIME_ID)?.trim().orEmpty()
                if (agentRuntimeId.isNotBlank()) {
                    serviceScope.launch {
                        initJob.join()
                        ensureOperationalState(appContext)
                        com.kite.app.foundation.terminal.TerminalRuntimeHost.launchAgentSession(appContext, agentRuntimeId)
                    }
                }
            }

            ACTION_SEND_TERMINAL_COMMAND -> {
                val command = intent.getStringExtra(EXTRA_TERMINAL_COMMAND)?.trim().orEmpty()
                val sessionId = intent.getStringExtra(EXTRA_TERMINAL_SESSION_ID)?.trim()
                if (command.isNotBlank()) {
                    serviceScope.launch {
                        initJob.join()
                        ensureOperationalState(appContext)
                        // 终端命令仍由工作面宿主接手，避免在 service 里继续下沉到底层容器配置。
                        com.kite.app.foundation.terminal.TerminalRuntimeHost.sendCommand(
                            appContext = appContext,
                            command = command,
                            sessionId = sessionId
                        )
                    }
                }
            }

            ACTION_END_TERMINAL_SESSION -> {
                val sessionId = intent.getStringExtra(EXTRA_TERMINAL_SESSION_ID)?.trim()
                serviceScope.launch {
                    initJob.join()
                    ensureOperationalState(appContext)
                    com.kite.app.foundation.terminal.TerminalRuntimeHost.endSession(
                        appContext,
                        sessionId
                    )
                }
            }

            ACTION_PASTE_TERMINAL_INPUT -> {
                val payload = intent.getStringExtra(EXTRA_TERMINAL_COMMAND).orEmpty()
                if (payload.isNotEmpty()) {
                    serviceScope.launch {
                        initJob.join()
                        ensureOperationalState(appContext)
                        com.kite.app.foundation.terminal.TerminalRuntimeHost.pasteMultiline(
                            appContext,
                            payload
                        )
                    }
                }
            }

            ACTION_REFRESH_RUNTIME_OVERVIEW -> {
                serviceScope.launch {
                    initJob.join()
                    RuntimeOverviewStore.refresh(appContext)
                }
            }

            ACTION_ENSURE_EXECUTION_HOST_RESIDENT -> {
                // onStartCommand 已经把进程提升为前台服务宿主；执行核心会继续自行准备容器。
                Logger.i("KFShellService", "直接执行宿主已取得前台驻留保障")
            }

            ACTION_START_BACKGROUND_RUNTIME -> {
                val runtimeId = intent.getStringExtra(EXTRA_RUNTIME_ID)?.trim().orEmpty()
                if (runtimeId.isNotBlank()) {
                    serviceScope.launch {
                        initJob.join()
                        // 后台运行项入口只转发给 BackgroundRuntimeHost，不在这里拼 one-shot/runtime 启动细节。
                        BackgroundRuntimeHost.startRuntime(appContext, runtimeId)
                    }
                }
            }

            ACTION_STOP_BACKGROUND_RUNTIME -> {
                val runtimeId = intent.getStringExtra(EXTRA_RUNTIME_ID)?.trim().orEmpty()
                if (runtimeId.isNotBlank()) {
                    serviceScope.launch {
                        initJob.join()
                        BackgroundRuntimeHost.stopRuntime(appContext, runtimeId)
                    }
                }
            }

            ACTION_RESTART_BACKGROUND_RUNTIME -> {
                val runtimeId = intent.getStringExtra(EXTRA_RUNTIME_ID)?.trim().orEmpty()
                if (runtimeId.isNotBlank()) {
                    serviceScope.launch {
                        initJob.join()
                        BackgroundRuntimeHost.restartRuntime(appContext, runtimeId)
                    }
                }
            }

            null -> {
                serviceScope.launch {
                    initJob.join()
                    if (shouldSuppressDefaultStart()) {
                        Logger.i("KFShellService", "默认宿主暖启动已抑制，本轮跳过 core runtime 自动拉起")
                        return@launch
                    }
                    ensureOperationalState(appContext)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
    }
}
