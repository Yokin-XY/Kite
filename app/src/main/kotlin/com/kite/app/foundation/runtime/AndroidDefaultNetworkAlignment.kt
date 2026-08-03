package com.kite.app.foundation.runtime

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import com.kite.app.foundation.logging.Logger
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * 让长驻容器跟随 Android 给 Kite 分配的默认网络。
 *
 * 这里只监听普通 Android 默认网络生命周期，不读取 VPN 应用、节点、规则或包名单。
 * VPN 是否接管 Kite 仍完全由 Android 和用户在 VPN 软件中的按应用设置决定。
 */
internal object AndroidDefaultNetworkAlignment {
    private val registrationLock = Any()
    private val refreshDirty = AtomicBoolean(false)
    private val refreshRunning = AtomicBoolean(false)
    private val latestReason = AtomicReference("default-network")
    private val latestNetwork = AtomicReference<Network?>(null)
    private val refreshExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "KiteDefaultNetworkAlignment").apply { isDaemon = true }
    }

    @Volatile
    private var registered = false

    @Volatile
    private var appContext: Context? = null

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            requestRefresh("available", network)
        }

        override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
            requestRefresh("link-properties", network)
        }

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            requestRefresh("capabilities", network)
        }

        override fun onLost(network: Network) {
            latestNetwork.get()
                ?.takeIf { current -> current == network }
                ?.let { current -> latestNetwork.compareAndSet(current, null) }
            requestRefresh("lost")
        }
    }

    fun ensureStarted(context: Context) {
        if (registered) return
        synchronized(registrationLock) {
            if (registered) return
            val applicationContext = context.applicationContext
            val connectivityManager =
                applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            if (connectivityManager == null) {
                Logger.e("ContainerNetwork", "Android 默认网络服务不可用，无法保持容器 DNS 对齐")
                return
            }

            appContext = applicationContext
            AndroidRuntimeHttpProxy.ensureStarted(applicationContext)
            runCatching {
                connectivityManager.registerDefaultNetworkCallback(callback)
            }.onSuccess {
                registered = true
                Logger.i("ContainerNetwork", "容器已接入 Android 默认网络生命周期")
                connectivityManager.activeNetwork?.let { network ->
                    requestRefresh("registered", network)
                }
            }.onFailure { error ->
                appContext = null
                Logger.e("ContainerNetwork", "注册 Android 默认网络回调失败: ${error.message}")
            }
        }
    }

    private fun requestRefresh(reason: String, preferredNetwork: Network? = null) {
        if (preferredNetwork != null) latestNetwork.set(preferredNetwork)
        latestReason.set(reason)
        refreshDirty.set(true)
        startRefreshWorkerIfNeeded()
    }

    private fun startRefreshWorkerIfNeeded() {
        if (!refreshRunning.compareAndSet(false, true)) return
        refreshExecutor.execute {
            try {
                while (refreshDirty.getAndSet(false)) {
                    val context = appContext ?: continue
                    val reason = latestReason.get()
                    val network = latestNetwork.get()
                    runCatching {
                        AndroidRuntimeHttpProxy.updateDefaultNetwork(network, reason)
                        KFContainerManager.refreshAndroidDefaultNetworkResolver(context, reason, network)
                    }.onFailure { error ->
                        Logger.e("ContainerNetwork", "刷新容器系统 DNS 失败: ${error.message}")
                    }
                }
            } finally {
                refreshRunning.set(false)
                if (refreshDirty.get()) {
                    startRefreshWorkerIfNeeded()
                }
            }
        }
    }
}
