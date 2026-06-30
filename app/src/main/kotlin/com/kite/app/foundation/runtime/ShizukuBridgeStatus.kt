package com.kite.app.foundation.runtime

import android.content.Context
import android.content.pm.PackageManager
import rikka.shizuku.Shizuku

/**
 * APK-side status probe for the host-self ADB bridge.
 *
 * This does not request permission or execute commands. It only exposes whether
 * the Android-side receiver lane can currently back the container-visible
 * kf-host-self target.
 */
object ShizukuBridgeStatus {
    const val STATUS_LISTED = "listed"
    const val STATUS_READY = "shizuku_ready"
    const val STATUS_PERMISSION_REQUIRED = "permission_required"
    const val STATUS_NOT_RUNNING = "shizuku_not_running"
    const val STATUS_UNAVAILABLE = "shizuku_unavailable"

    data class Snapshot(
        val status: String,
        val available: Boolean,
        val permission: String,
        val source: String,
        val uid: String,
        val version: String,
        val error: String? = null
    )

    fun snapshot(context: Context): Snapshot {
        val packageName = context.applicationContext.packageName
        return snapshot(source = "shizuku:$packageName")
    }

    fun snapshotForExecution(): Snapshot {
        return snapshot(source = "shizuku")
    }

    private fun snapshot(source: String): Snapshot {
        return runCatching {
            val alive = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
            if (!alive) {
                return@runCatching Snapshot(
                    status = STATUS_NOT_RUNNING,
                    available = false,
                    permission = "unknown",
                    source = source,
                    uid = "",
                    version = ""
                )
            }

            val granted = runCatching {
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            }.getOrDefault(false)
            val version = runCatching { Shizuku.getVersion().toString() }.getOrDefault("")
            val uid = runCatching { Shizuku.getUid().toString() }.getOrDefault("")

            Snapshot(
                status = if (granted) STATUS_READY else STATUS_PERMISSION_REQUIRED,
                available = true,
                permission = if (granted) "granted" else "required",
                source = source,
                uid = uid,
                version = version
            )
        }.getOrElse { error ->
            Snapshot(
                status = STATUS_UNAVAILABLE,
                available = false,
                permission = "unknown",
                source = source,
                uid = "",
                version = "",
                error = error.javaClass.simpleName
            )
        }
    }
}
