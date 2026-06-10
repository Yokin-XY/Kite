package com.kftest.app.foundation.bootstrap

import android.app.Application
import android.app.Application.ActivityLifecycleCallbacks
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import com.kftest.app.foundation.logging.Logger
import com.kftest.app.foundation.runtime.HostSelfAdbBridgeWorker
import com.kftest.app.foundation.runtime.RuntimeLifecycleSignalStore
import com.kftest.app.foundation.runtime.RuntimePressureResponder
import com.kftest.app.ui.terminal.TerminalUiPreferences

class KFApplication : Application() {

    companion object {
        const val CHANNEL_SHELL = "kfshell_service"
        const val CHANNEL_API = "flask_api_service"

        @Volatile
        private var launchStartedAtElapsed: Long = 0L

        fun markLaunchStage(tag: String, stage: String) {
            val startedAt = launchStartedAtElapsed
            if (startedAt <= 0L) {
                Logger.i(tag, "冷启动时序: $stage")
                return
            }
            val delta = SystemClock.elapsedRealtime() - startedAt
            Logger.i(tag, "冷启动时序 +${delta}ms: $stage")
        }
    }

    override fun onCreate() {
        super.onCreate()
        launchStartedAtElapsed = SystemClock.elapsedRealtime()
        TerminalUiPreferences.applySavedTheme(this)
        Logger.init(this)
        Logger.i("App", "KFShell 应用启动")
        markLaunchStage("App", "Application.onCreate")
        registerRuntimeLifecycleSignals()
        HostSelfAdbBridgeWorker.start(this)
        markLaunchStage("App", "host-self adb bridge worker 就绪")
        createNotificationChannels()
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
