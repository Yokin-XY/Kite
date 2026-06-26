package com.kite.app.run

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.os.IBinder
import android.os.Parcel
import android.os.ParcelFileDescriptor
import android.view.View
import com.kftest.app.foundation.workspace.WorkSurfaceRuntimeBridge
import com.termux.x11.LorieView
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

object KiteX11SurfaceServer {
    private var serviceBinder: IBinder? = null
    private var serviceConnection: ServiceConnection? = null
    private var activeDisplay: String? = null
    private var activeRootfsPath: String? = null

    @Synchronized
    fun ensureStarted(context: Context, binding: KiteX11SurfaceBinding): Result<Unit> = runCatching {
        val container = WorkSurfaceRuntimeBridge.resolveActiveContainer(context.applicationContext)
        val rootfs = File(container.rootfsPath)
        val tmpDir = File(rootfs, "tmp").also { it.mkdirs() }
        val socketDir = File(tmpDir, ".X11-unix").also { it.mkdirs() }
        val xkbRoot = File(rootfs, "usr/share/X11/xkb")
        require(rootfs.isDirectory) { "rootfs 不存在：${rootfs.absolutePath}" }
        require(xkbRoot.isDirectory) { "XKB 配置不存在：${xkbRoot.absolutePath}" }

        if (serviceBinder != null && activeDisplay == binding.display && activeRootfsPath == rootfs.absolutePath) {
            return@runCatching
        }
        require(serviceBinder == null) {
            "Kite X11 已在 ${activeDisplay.orEmpty()} 运行"
        }

        File(socketDir, "X${binding.display.removePrefix(":")}").delete()
        val serviceIntent = KiteX11ServerService.intent(
            context = context.applicationContext,
            display = binding.display,
            rootfsPath = rootfs.absolutePath,
            xkbRootPath = xkbRoot.absolutePath,
            homePath = File(rootfs, "root").absolutePath
        )
        serviceBinder = bindServer(context.applicationContext, serviceIntent)
        activeDisplay = binding.display
        activeRootfsPath = rootfs.absolutePath
    }

    fun surfaceView(context: Context, binding: KiteX11SurfaceBinding): View {
        ensureStarted(context, binding).getOrThrow()
        return LorieView(context).apply {
            attachRenderer(this)
        }
    }

    @Synchronized
    private fun attachRenderer(view: LorieView) {
        if (!LorieView.connected()) {
            val fd: ParcelFileDescriptor = requestXConnection()
            LorieView.connect(fd.detachFd())
        }
        view.requestFocus()
        view.triggerCallback()
    }

    private fun bindServer(context: Context, intent: android.content.Intent): IBinder {
        context.startService(intent)
        val latch = CountDownLatch(1)
        var connected: IBinder? = null
        var disconnected = false
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                connected = service
                latch.countDown()
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                disconnected = true
                serviceBinder = null
                serviceConnection = null
            }
        }
        check(context.bindService(intent, connection, Context.BIND_AUTO_CREATE)) {
            "native X11 service 绑定失败"
        }
        check(latch.await(5, TimeUnit.SECONDS) && !disconnected) {
            runCatching { context.unbindService(connection) }
            "native X11 service 启动超时"
        }
        serviceConnection = connection
        return connected ?: error("native X11 service 未返回 Binder")
    }

    private fun requestXConnection(): ParcelFileDescriptor {
        val binder = serviceBinder ?: error("native X server 未启动")
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            check(binder.transact(KiteX11ServerService.TRANSACTION_GET_X_CONNECTION, data, reply, 0)) {
                "native X server 连接请求失败"
            }
            val hasFd = reply.readInt()
            check(hasFd == 1) { "native X server 未返回连接 fd" }
            return ParcelFileDescriptor.CREATOR.createFromParcel(reply)
        } finally {
            data.recycle()
            reply.recycle()
        }
    }
}
