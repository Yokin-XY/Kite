package com.kite.app.foundation.service

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

/**
 * 电池优化豁免引导。
 *
 * Kite 的运行时（proot 容器与 Agent 进程）在熄屏后仍需继续工作；部分厂商的
 * 省电策略（如 OnePlus 的熄屏冻结）只对“不受电池优化影响”的应用豁免。
 * 这里在主界面可见时做一次系统级检查与引导，用户拒绝或已豁免后不再打扰。
 */
object BatteryOptimizationGuard {

    private const val PREFS_NAME = "kite_battery_guard"
    private const val KEY_PROMPTED = "exemption_prompted"

    fun isExempted(context: Context): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            ?: return true
        val packageName = context.packageName
        return powerManager.isIgnoringBatteryOptimizations(packageName)
    }

    /**
     * 首次进入主界面且尚未豁免时，弹出系统电池优化豁免对话框。
     * 只负责发起系统对话框与记录“已问过”，不跟踪结果；用户随时可在系统设置里改。
     */
    fun maybeRequestExemptionOnce(activity: Activity) {
        if (isExempted(activity)) {
            return
        }
        val preferences = activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (preferences.getBoolean(KEY_PROMPTED, false)) {
            return
        }
        preferences.edit().putBoolean(KEY_PROMPTED, true).apply()

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return
        }
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${activity.packageName}")
        }
        runCatching { activity.startActivity(intent) }
    }
}
