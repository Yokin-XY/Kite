package com.kite.app.run

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.os.Parcel
import android.os.ParcelFileDescriptor
import android.os.Parcelable
import android.system.Os
import com.termux.x11.CmdEntryPoint
import java.io.File

class KiteX11ServerService : Service() {
    private val binder = X11Binder()
    private var entryPoint: CmdEntryPoint? = null
    private var activeDisplay: String? = null
    private var activeRootfsPath: String? = null

    override fun onBind(intent: Intent?): IBinder {
        intent?.let { startFromIntent(it) }
        return binder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let { startFromIntent(it) }
        return START_STICKY
    }

    @Synchronized
    private fun startFromIntent(intent: Intent) {
        val display = intent.getStringExtra(EXTRA_DISPLAY).orEmpty()
        val rootfsPath = intent.getStringExtra(EXTRA_ROOTFS).orEmpty()
        val xkbRootPath = intent.getStringExtra(EXTRA_XKB_ROOT).orEmpty()
        val homePath = intent.getStringExtra(EXTRA_HOME).orEmpty()
        require(display.isNotBlank()) { "DISPLAY 为空" }
        require(rootfsPath.isNotBlank()) { "rootfs 为空" }

        if (entryPoint != null && activeDisplay == display && activeRootfsPath == rootfsPath) return
        require(entryPoint == null) { "Kite X11 server 已在 ${activeDisplay.orEmpty()} 运行" }

        val rootfs = File(rootfsPath)
        val tmpDir = File(rootfs, "tmp").also { it.mkdirs() }
        val socketDir = File(tmpDir, ".X11-unix").also { it.mkdirs() }
        File(socketDir, "X${display.removePrefix(":")}").delete()

        Os.setenv("TMPDIR", tmpDir.absolutePath, true)
        if (xkbRootPath.isNotBlank()) Os.setenv("XKB_CONFIG_ROOT", xkbRootPath, true)
        if (homePath.isNotBlank()) Os.setenv("HOME", homePath, true)

        check(CmdEntryPoint.start(arrayOf(display, "-ac", "-nolisten", "tcp"))) {
            "native X server 启动失败：$display"
        }
        entryPoint = CmdEntryPoint()
        activeDisplay = display
        activeRootfsPath = rootfsPath
    }

    private inner class X11Binder : Binder() {
        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            if (code != TRANSACTION_GET_X_CONNECTION || reply == null) {
                return super.onTransact(code, data, reply, flags)
            }
            val fd: ParcelFileDescriptor? = synchronized(this@KiteX11ServerService) {
                entryPoint?.getXConnection()
            }
            if (fd == null) {
                reply.writeInt(0)
            } else {
                reply.writeInt(1)
                fd.writeToParcel(reply, Parcelable.PARCELABLE_WRITE_RETURN_VALUE)
            }
            return true
        }
    }

    companion object {
        const val TRANSACTION_GET_X_CONNECTION: Int = IBinder.FIRST_CALL_TRANSACTION
        private const val EXTRA_DISPLAY = "display"
        private const val EXTRA_ROOTFS = "rootfs"
        private const val EXTRA_XKB_ROOT = "xkb_root"
        private const val EXTRA_HOME = "home"

        fun intent(
            context: Context,
            display: String,
            rootfsPath: String,
            xkbRootPath: String,
            homePath: String
        ): Intent = Intent(context, KiteX11ServerService::class.java).apply {
            putExtra(EXTRA_DISPLAY, display)
            putExtra(EXTRA_ROOTFS, rootfsPath)
            putExtra(EXTRA_XKB_ROOT, xkbRootPath)
            putExtra(EXTRA_HOME, homePath)
        }
    }
}
