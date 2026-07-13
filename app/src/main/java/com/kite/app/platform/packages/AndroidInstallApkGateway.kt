package com.kite.app.platform.packages

import android.content.Context
import android.net.Uri
import com.kite.app.application.packages.InstallApkGateway
import com.kite.app.application.packages.InstallApkResult
import com.kite.app.foundation.runtime.ExternalExchangeManager
import java.io.File

/** APK 入站路径解析器；不启动安装器，也不持有 Activity。 */
internal class AndroidInstallApkGateway(context: Context) : InstallApkGateway {
    private val appContext = context.applicationContext

    override fun resolve(path: String): InstallApkResult {
        val file = resolveFile(path)
            ?: return InstallApkResult(false, path, error = "unsupported_path")
        if (!file.name.endsWith(".apk", ignoreCase = true)) {
            return InstallApkResult(false, path, file.absolutePath, "not_apk")
        }
        if (!file.isFile) {
            return InstallApkResult(false, path, file.absolutePath, "apk_not_found")
        }
        return InstallApkResult(true, path, file.absolutePath)
    }

    private fun resolveFile(path: String): File? {
        val rawPath = if (path.startsWith("file://", ignoreCase = true)) {
            Uri.parse(path).path.orEmpty()
        } else {
            path
        }.trim()
        if (rawPath.isBlank()) return null
        return when {
            rawPath == ExternalExchangeManager.CONTAINER_MOUNT_PATH -> null
            rawPath.startsWith("${ExternalExchangeManager.CONTAINER_MOUNT_PATH}/") -> {
                val relative = rawPath.removePrefix("${ExternalExchangeManager.CONTAINER_MOUNT_PATH}/")
                File(ExternalExchangeManager.ensureExchangeDir(appContext), relative)
            }
            rawPath.startsWith("/sdcard/") || rawPath.startsWith("/storage/") -> File(rawPath)
            else -> null
        }?.absoluteFile
    }
}
