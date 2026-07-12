package com.kite.app.foundation.bootstrap

import android.app.Application
import android.app.Application.ActivityLifecycleCallbacks
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import com.kite.app.application.resources.ResourceFeatureDependenciesOwner
import com.kite.app.application.resources.ResourceFeatureGateway
import com.kite.app.application.recipes.RecipeFeatureDependenciesOwner
import com.kite.app.application.recipes.RecipeFeatureGateway
import com.kite.app.application.runtimemanagement.RuntimeManagementDependenciesOwner
import com.kite.app.application.runtimemanagement.RuntimeManagementCoordinator
import com.kite.app.application.runtimemanagement.RuntimeManagementGateway
import com.kite.app.foundation.logging.Logger
import com.kite.app.foundation.runtime.AndroidShellBridgeWorker
import com.kite.app.foundation.runtime.HostSelfAdbBridgeWorker
import com.kite.app.foundation.runtime.RuntimeLifecycleSignalStore
import com.kite.app.foundation.runtime.RuntimePressureResponder
import com.kite.app.ui.terminal.TerminalUiPreferences
import com.kite.app.shell.KiteAppGraph

class KFApplication : Application(), ResourceFeatureDependenciesOwner, RecipeFeatureDependenciesOwner,
    RuntimeManagementDependenciesOwner {

    override val resourceFeatureGateway: ResourceFeatureGateway
        get() = KiteAppGraph.from(this).resourceFeatureGateway

    override val recipeFeatureGateway: RecipeFeatureGateway
        get() = KiteAppGraph.from(this).recipeFeatureGateway

    override val runtimeManagementGateway: RuntimeManagementGateway
        get() = KiteAppGraph.from(this).runtimeManagementGateway

    override val runtimeManagementCoordinator: RuntimeManagementCoordinator
        get() = KiteAppGraph.from(this).runtimeManagementCoordinator

    companion object {
        const val CHANNEL_SHELL = "kfshell_service"
        const val CHANNEL_API = "flask_api_service"

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

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val shellChannel = NotificationChannel(
                CHANNEL_SHELL,
                "KFShell 服务",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Linux 容器运行环境"
            }

            val apiChannel = NotificationChannel(
                CHANNEL_API,
                "设备控制 API",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "设备控制 HTTP API 服务"
            }

            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(shellChannel)
            manager.createNotificationChannel(apiChannel)
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
