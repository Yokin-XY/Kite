package com.kftest.app.bridge.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.kftest.app.R
import com.kftest.app.foundation.bootstrap.KFApplication
import com.kftest.app.foundation.logging.Logger
import com.kite.app.MainActivity

/**
 * 预留的设备桥接服务。
 *
 * 目前项目重点是容器基座，因此这里先保持“可启动但不伪装成已实现”的状态，
 * 避免继续依赖 `/data/local/tmp` 和宿主机上的临时 Python 环境。
 */
class FlaskApiService : Service() {

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Logger.i("FlaskApiService", "设备控制 API 仍处于规划阶段")
        startForeground(2, createNotification())
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, KFApplication.CHANNEL_API)
            .setContentTitle(getString(R.string.service_notification_title))
            .setContentText(getString(R.string.flask_notification_text))
            .setSmallIcon(R.drawable.ic_bridge)
            .setContentIntent(pendingIntent)
            .setOngoing(false)
            .build()
    }
}
