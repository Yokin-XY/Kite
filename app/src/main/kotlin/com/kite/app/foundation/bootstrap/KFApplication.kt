package com.kite.app.foundation.bootstrap

import android.app.Application
import android.app.Application.ActivityLifecycleCallbacks
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import com.kite.app.R
import com.kite.app.application.resources.ResourceFeatureDependenciesOwner
import com.kite.app.application.resources.ResourceFeatureGateway
import com.kite.app.agent.registration.AgentRegistryDependenciesOwner
import com.kite.app.agent.registration.KiteAgentRegistry
import com.kite.app.application.recipes.RecipeFeatureDependenciesOwner
import com.kite.app.application.recipes.RecipeFeatureGateway
import com.kite.app.application.runtimemanagement.RuntimeManagementDependenciesOwner
import com.kite.app.application.runtimemanagement.RuntimeManagementCoordinator
import com.kite.app.application.runtimemanagement.RuntimeManagementGateway
import com.kite.app.application.runtimebootstrap.RuntimeBootstrapDependenciesOwner
import com.kite.app.application.runtimemanagement.ProotViewInspectionDependenciesOwner
import com.kite.app.application.runtimebootstrap.RuntimeBootstrapGateway
import com.kite.app.foundation.logging.Logger
import com.kite.app.foundation.devicebridge.DeviceBridgeBackendStateOwner
import com.kite.app.foundation.runtime.AndroidShellBridgeWorker
import com.kite.app.foundation.runtime.AndroidDefaultNetworkAlignment
import com.kite.app.foundation.runtime.HostSelfAdbBridgeWorker
import com.kite.app.foundation.runtime.ProotTelemetryStore
import com.kite.app.foundation.runtime.RuntimeHealthStore
import com.kite.app.foundation.runtime.RuntimeLifecycleSignalStore
import com.kite.app.foundation.runtime.RuntimeOverviewStore
import com.kite.app.foundation.runtime.RuntimePressureResponder
import com.kite.app.foundation.runtime.TaskManagerStore
import com.kite.app.run.CardRunStore
import com.kite.app.ui.terminal.TerminalUiPreferences
import com.kite.app.shell.KiteAppGraph
import com.kite.app.feature.web.WebWorkbenchDependenciesOwner
import com.kite.app.application.settings.SettingsFeatureDependenciesOwner
import com.kite.app.application.settings.SettingsGateway
import com.kite.app.application.theme.ThemeEnvironmentDependenciesOwner
import com.kite.app.application.theme.ThemeEnvironmentGateway
import com.kite.app.platform.theme.AndroidThemeEnvironmentGateway
import com.kite.app.application.runs.RunHistoryDependenciesOwner
import com.kite.app.application.runs.RunHistoryGateway
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class KFApplication : Application(), ResourceFeatureDependenciesOwner, RecipeFeatureDependenciesOwner,
    RuntimeManagementDependenciesOwner, WebWorkbenchDependenciesOwner, SettingsFeatureDependenciesOwner,
    RuntimeBootstrapDependenciesOwner, RunHistoryDependenciesOwner, ThemeEnvironmentDependenciesOwner,
    AgentRegistryDependenciesOwner, ProotViewInspectionDependenciesOwner {

    internal val applicationJob = SupervisorJob()
    private val applicationScope = CoroutineScope(applicationJob + Dispatchers.Default)

    override val resourceFeatureGateway: ResourceFeatureGateway
        get() = KiteAppGraph.from(this).resourceFeatureGateway

    override val recipeFeatureGateway: RecipeFeatureGateway
        get() = KiteAppGraph.from(this).recipeFeatureGateway

    override val agentRegistry: KiteAgentRegistry
        get() = KiteAppGraph.from(this).agentRegistry

    override val runtimeManagementGateway: RuntimeManagementGateway
        get() = KiteAppGraph.from(this).runtimeManagementGateway

    override val runtimeManagementCoordinator: RuntimeManagementCoordinator
        get() = KiteAppGraph.from(this).runtimeManagementCoordinator

    override val runtimeBootstrapGateway: RuntimeBootstrapGateway
        get() = KiteAppGraph.from(this).runtimeBootstrapGateway

    override val prootViewInspectionGateway: com.kite.app.application.runtimemanagement.ProotViewInspectionGateway
        get() = KiteAppGraph.from(this).prootViewInspectionGateway

    override val webWorkbenchDiagnostics
        get() = KiteAppGraph.from(this).diagnostics

    override val webWorkbenchAutomationSessions
        get() = KiteAppGraph.from(this).browserAutomationSessions

    override val settingsFeatureGateway: SettingsGateway
        get() = KiteAppGraph.from(this).settingsGateway

    override val themeEnvironmentGateway: ThemeEnvironmentGateway by lazy(LazyThreadSafetyMode.NONE) {
        AndroidThemeEnvironmentGateway(
            context = this,
            settingsGateway = settingsFeatureGateway,
        )
    }

    override val runHistoryGateway: RunHistoryGateway
        get() = KiteAppGraph.from(this).runHistoryGateway

    override fun launchWebWorkbenchHandoff(
        request: com.kite.app.browser.BrowserHandoffRequest,
        decision: com.kite.app.browser.BrowserHandoffDecision
    ): Boolean = KiteAppGraph.from(this).webWorkbenchHandoffCoordinator
        .launch(request, decision)
        .accepted

    companion object {
        const val CHANNEL_BACKGROUND_RUNTIME = "kite_background_runtime_v2"
        const val CHANNEL_RUNS = "kite_home_run_progress_v2"
        private const val LEGACY_CHANNEL_SHELL = "kfshell_service"
        private const val LEGACY_CHANNEL_API = "flask_api_service"
        private const val LEGACY_CHANNEL_RUNS = "kite_run_progress"

        @Volatile
        private var launchStartedAtElapsed: Long = 0L

        fun markLaunchStage(tag: String, stage: String) {
            val startedAt = launchStartedAtElapsed
            if (startedAt <= 0L) {
                runCatching { Logger.i(tag, "冷启动时序: $stage") }
                return
            }
            val delta = SystemClock.elapsedRealtime() - startedAt
            runCatching { Logger.i(tag, "冷启动时序 +${delta}ms: $stage") }
        }
    }

    override fun onCreate() {
        super.onCreate()
        launchStartedAtElapsed = SystemClock.elapsedRealtime()
        StartupTraceStore.prepareProcess(this)
        StartupTraceStore.runApplicationStage(this, "application.saved_theme") {
            TerminalUiPreferences.applySavedTheme(this)
        }
        StartupTraceStore.runApplicationStage(this, "application.logger") {
            Logger.init(this)
            Logger.i("App", "KFShell 应用启动")
        }
        markLaunchStage("App", "Application.onCreate")
        StartupTraceStore.runApplicationStage(this, "application.lifecycle_signals") {
            registerRuntimeLifecycleSignals()
        }
        StartupTraceStore.runApplicationStage(this, "application.network_alignment") {
            AndroidDefaultNetworkAlignment.ensureStarted(this)
        }
        StartupTraceStore.runApplicationStage(this, "application.shizuku_state") {
            DeviceBridgeBackendStateOwner.start(this)
        }
        StartupTraceStore.runApplicationStage(this, "application.android_shell_bridge") {
            AndroidShellBridgeWorker.start(this)
        }
        StartupTraceStore.runApplicationStage(this, "application.host_self_bridge") {
            HostSelfAdbBridgeWorker.start(this)
        }
        markLaunchStage("App", "Android shell / host-self bridge workers 就绪")
        StartupTraceStore.runApplicationStage(this, "application.notification_channels") {
            createNotificationChannels()
        }
        StartupTraceStore.runApplicationStage(this, "application.run_notifications") {
            RuntimeOverviewStore.start(this, applicationScope)
            ProotTelemetryStore.start(this, applicationScope)
            RuntimeHealthStore.start(this, applicationScope)
            TaskManagerStore.start(this, applicationScope)
            CardRunStore.initialize(this)
            applicationScope.launch(start = CoroutineStart.UNDISPATCHED) {
                TaskManagerStore.confirmedStoppedOwnerEvents.collect(CardRunStore::confirmRuntimeOwnersStopped)
            }
            KiteAppGraph.from(this).also { graph ->
                graph.preloadAgentConversationCatalogs()
                graph.runNotificationCoordinator.start()
            }
        }
        markLaunchStage("App", "通知通道就绪")
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        RuntimePressureResponder.onTrimMemory(this, level)
    }

    override fun onLowMemory() {
        super.onLowMemory()
        RuntimePressureResponder.onLowMemory(this)
    }

    override fun onTerminate() {
        val taskManagerJob = TaskManagerStore.release(this)
        val runtimeHealthJob = RuntimeHealthStore.release(this)
        val prootTelemetryJob = ProotTelemetryStore.release(this)
        val runtimeOverviewJob = RuntimeOverviewStore.release(this)
        applicationJob.cancel()
        val processJob = KiteAppGraph.release(this)
        runBlocking {
            taskManagerJob?.join()
            runtimeHealthJob?.join()
            prootTelemetryJob?.join()
            runtimeOverviewJob?.join()
            applicationJob.join()
            processJob?.join()
        }
        super.onTerminate()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val backgroundRuntimeChannel = NotificationChannel(
                CHANNEL_BACKGROUND_RUNTIME,
                getString(R.string.notification_channel_background_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_background_description)
                setSound(null, null)
                enableVibration(false)
                enableLights(false)
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_SECRET
            }

            val runChannel = NotificationChannel(
                CHANNEL_RUNS,
                getString(R.string.notification_channel_runs_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = getString(R.string.notification_channel_runs_description)
            }

            val manager = getSystemService(NotificationManager::class.java)
            manager.deleteNotificationChannel(LEGACY_CHANNEL_SHELL)
            manager.deleteNotificationChannel(LEGACY_CHANNEL_API)
            manager.deleteNotificationChannel(LEGACY_CHANNEL_RUNS)
            manager.createNotificationChannel(backgroundRuntimeChannel)
            manager.createNotificationChannel(runChannel)
        }
    }

    private fun registerRuntimeLifecycleSignals() {
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit

            override fun onActivityStarted(activity: Activity) {
                RuntimeLifecycleSignalStore.onActivityStarted(
                    activity.localClassName,
                    activity.runtimeLifecycleId()
                )
            }

            override fun onActivityResumed(activity: Activity) {
                RuntimeLifecycleSignalStore.onActivityResumed(
                    activity.localClassName,
                    activity.runtimeLifecycleId()
                )
            }

            override fun onActivityPaused(activity: Activity) {
                RuntimeLifecycleSignalStore.onActivityPaused(
                    activity.localClassName,
                    activity.runtimeLifecycleId()
                )
            }

            override fun onActivityStopped(activity: Activity) {
                RuntimeLifecycleSignalStore.onActivityStopped(
                    activity.localClassName,
                    activity.runtimeLifecycleId()
                )
            }

            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

            override fun onActivityDestroyed(activity: Activity) = Unit
        })
    }

    private fun Activity.runtimeLifecycleId(): String {
        return "${localClassName}@${System.identityHashCode(this).toString(16)}"
    }
}
