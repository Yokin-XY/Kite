package com.kite.app.foundation.devicebridge

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import com.kite.app.foundation.logging.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuProvider

/**
 * Shizuku 生命周期与授权事实的应用级唯一所有者。
 *
 * UI、Ubuntu 环境投影和执行器只能消费这里的状态，不能各自轮询 Shizuku。
 */
object ShizukuBridgeStateOwner {
    private const val LOG_TAG = "ShizukuBridgeState"
    private const val PERMISSION_REQUEST_CODE = 7_031

    enum class AuthorizationRequestResult {
        Requested,
        AlreadyReady,
        AlreadyRequesting,
        ServiceNotRunning,
        NotStarted,
        Failed
    }

    private val mutableState = MutableStateFlow(ShizukuBridgeState())
    val state: StateFlow<ShizukuBridgeState> = mutableState.asStateFlow()

    @Volatile
    private var appContext: Context? = null

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        refresh("binder_received")
    }
    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        val installed = appContext?.let(::isManagerInstalled) ?: false
        dispatch(ShizukuBridgeSignal.BinderDied(installed))
    }
    private val permissionResultListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        if (requestCode != PERMISSION_REQUEST_CODE) return@OnRequestPermissionResultListener
        dispatch(ShizukuBridgeSignal.PermissionResult(grantResult == PackageManager.PERMISSION_GRANTED))
        if (grantResult == PackageManager.PERMISSION_GRANTED) {
            refresh("permission_granted_refresh")
        }
    }
    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.data?.schemeSpecificPart == ShizukuProvider.MANAGER_APPLICATION_ID) {
                refresh("manager_package_changed")
            }
        }
    }

    @Synchronized
    fun start(context: Context) {
        if (appContext != null) return
        val applicationContext = context.applicationContext
        appContext = applicationContext

        Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
        Shizuku.addBinderDeadListener(binderDeadListener)
        Shizuku.addRequestPermissionResultListener(permissionResultListener)
        registerPackageReceiver(applicationContext)
        refresh("owner_started")
    }

    fun isStarted(): Boolean = appContext != null

    fun current(): ShizukuBridgeState = mutableState.value

    fun refresh(source: String = "manual_refresh"): ShizukuBridgeState {
        val context = appContext ?: return mutableState.value
        val installed = isManagerInstalled(context)
        val signal = runCatching {
            val binderAlive = Shizuku.pingBinder()
            if (!binderAlive) {
                ShizukuBridgeSignal.SnapshotObserved(
                    managerInstalled = installed,
                    binderAlive = false,
                    permissionGranted = null,
                    uid = null,
                    serverVersion = null,
                    source = source
                )
            } else {
                ShizukuBridgeSignal.SnapshotObserved(
                    managerInstalled = installed,
                    binderAlive = true,
                    permissionGranted = Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED,
                    uid = runCatching { Shizuku.getUid() }.getOrNull(),
                    serverVersion = runCatching { Shizuku.getVersion() }.getOrNull(),
                    source = source
                )
            }
        }.getOrElse { error ->
            ShizukuBridgeSignal.ProbeFailed(
                managerInstalled = installed,
                source = source,
                error = "${error.javaClass.simpleName}:${error.message.orEmpty()}"
            )
        }
        dispatch(signal)
        return mutableState.value
    }

    @Synchronized
    fun requestAuthorization(): AuthorizationRequestResult {
        if (appContext == null) return AuthorizationRequestResult.NotStarted
        if (mutableState.value.requestInFlight) {
            return AuthorizationRequestResult.AlreadyRequesting
        }
        val current = refresh("authorization_preflight")
        if (current.lifecycle == DeviceBridgeLifecycleStatus.Ready) {
            return AuthorizationRequestResult.AlreadyReady
        }
        if (!current.binderAlive) {
            return AuthorizationRequestResult.ServiceNotRunning
        }

        dispatch(ShizukuBridgeSignal.AuthorizationRequested)
        return runCatching {
            Shizuku.requestPermission(PERMISSION_REQUEST_CODE)
            AuthorizationRequestResult.Requested
        }.getOrElse { error ->
            dispatch(
                ShizukuBridgeSignal.ProbeFailed(
                    managerInstalled = current.managerInstalled,
                    source = "authorization_request_failed",
                    error = "${error.javaClass.simpleName}:${error.message.orEmpty()}"
                )
            )
            AuthorizationRequestResult.Failed
        }
    }

    private fun dispatch(signal: ShizukuBridgeSignal) {
        synchronized(this) {
            mutableState.value = ShizukuBridgeStateReducer.reduce(mutableState.value, signal)
            Logger.i(
                LOG_TAG,
                "signal=${mutableState.value.lastSignal} lifecycle=${mutableState.value.lifecycle} " +
                    "permission=${mutableState.value.permission} uid=${mutableState.value.uid ?: "-"}"
            )
        }
    }

    private fun registerPackageReceiver(context: Context) {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        if (Build.VERSION.SDK_INT >= 33) {
            context.registerReceiver(packageReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            context.registerReceiver(packageReceiver, filter)
        }
    }

    @Suppress("DEPRECATION")
    private fun isManagerInstalled(context: Context): Boolean = runCatching {
        context.packageManager.getApplicationInfo(ShizukuProvider.MANAGER_APPLICATION_ID, 0)
        true
    }.getOrDefault(false)
}
