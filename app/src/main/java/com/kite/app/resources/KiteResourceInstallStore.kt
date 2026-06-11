package com.kite.app.resources

import android.content.Context
import android.content.SharedPreferences

class KiteResourceInstallStore(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("kite_resource_installs", Context.MODE_PRIVATE)

    fun status(resourceId: String): String? =
        prefs.getString(key(resourceId, "status"), null)

    fun isInstalled(resourceId: String): Boolean =
        status(resourceId) == STATUS_INSTALLED

    fun isFailed(resourceId: String): Boolean =
        status(resourceId) == STATUS_FAILED

    fun isInstalling(resourceId: String): Boolean =
        status(resourceId) == STATUS_INSTALLING

    fun isUninstalling(resourceId: String): Boolean =
        status(resourceId) == STATUS_UNINSTALLING

    fun isBusy(resourceId: String): Boolean =
        isInstalling(resourceId) || isUninstalling(resourceId)

    fun failedOperation(resourceId: String): String =
        prefs.getString(key(resourceId, "operation"), "").orEmpty()

    fun markInstalling(resourceId: String, runId: String? = null) {
        prefs.edit()
            .putString(key(resourceId, "status"), STATUS_INSTALLING)
            .putString(key(resourceId, "operation"), OP_INSTALL)
            .putString(key(resourceId, "runId"), runId.orEmpty())
            .putString(key(resourceId, "summary"), "安装中")
            .putLong(key(resourceId, "updatedAt"), System.currentTimeMillis())
            .apply()
    }

    fun markUninstalling(resourceId: String, runId: String? = null) {
        prefs.edit()
            .putString(key(resourceId, "status"), STATUS_UNINSTALLING)
            .putString(key(resourceId, "operation"), OP_UNINSTALL)
            .putString(key(resourceId, "runId"), runId.orEmpty())
            .putString(key(resourceId, "summary"), "清理中")
            .putLong(key(resourceId, "updatedAt"), System.currentTimeMillis())
            .apply()
    }

    fun markInstalled(resourceId: String, version: String, runId: String?, summary: String?) {
        prefs.edit()
            .putString(key(resourceId, "status"), STATUS_INSTALLED)
            .putString(key(resourceId, "operation"), OP_INSTALL)
            .putString(key(resourceId, "version"), version)
            .putString(key(resourceId, "runId"), runId.orEmpty())
            .putString(key(resourceId, "summary"), summary.orEmpty())
            .putLong(key(resourceId, "updatedAt"), System.currentTimeMillis())
            .apply()
    }

    fun markFailed(resourceId: String, operation: String, runId: String?, reason: String?) {
        prefs.edit()
            .putString(key(resourceId, "status"), STATUS_FAILED)
            .putString(key(resourceId, "operation"), operation)
            .putString(key(resourceId, "runId"), runId.orEmpty())
            .putString(key(resourceId, "summary"), reason.orEmpty())
            .putLong(key(resourceId, "updatedAt"), System.currentTimeMillis())
            .apply()
    }

    fun clear(resourceId: String) {
        prefs.edit()
            .remove(key(resourceId, "status"))
            .remove(key(resourceId, "operation"))
            .remove(key(resourceId, "version"))
            .remove(key(resourceId, "runId"))
            .remove(key(resourceId, "summary"))
            .remove(key(resourceId, "updatedAt"))
            .apply()
    }

    private fun key(resourceId: String, field: String): String =
        "${KiteResourceInstallRecipes.safeId(resourceId)}.$field"

    companion object {
        const val STATUS_INSTALLED = "installed"
        const val STATUS_FAILED = "failed"
        const val STATUS_INSTALLING = "installing"
        const val STATUS_UNINSTALLING = "uninstalling"
        const val OP_INSTALL = "install"
        const val OP_UNINSTALL = "uninstall"
    }
}
