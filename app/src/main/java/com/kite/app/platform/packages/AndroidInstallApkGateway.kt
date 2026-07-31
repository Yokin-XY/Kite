package com.kite.app.platform.packages

import android.content.Context
import com.kite.app.application.packages.InstallApkGateway
import com.kite.app.application.packages.InstallApkResult
import com.kite.app.foundation.workspace.ContainerVisibleFileResolver

/** APK 入站路径解析器；不启动安装器，也不持有 Activity。 */
internal class AndroidInstallApkGateway(context: Context) : InstallApkGateway {
    private val appContext = context.applicationContext

    override fun resolve(path: String): InstallApkResult {
        val file = ContainerVisibleFileResolver.resolve(appContext, path)
            ?: return InstallApkResult(false, path, error = "unsupported_path")
        if (!file.name.endsWith(".apk", ignoreCase = true)) {
            return InstallApkResult(false, path, file.absolutePath, "not_apk")
        }
        if (!file.isFile) {
            return InstallApkResult(false, path, file.absolutePath, "apk_not_found")
        }
        return InstallApkResult(true, path, file.absolutePath)
    }

}
