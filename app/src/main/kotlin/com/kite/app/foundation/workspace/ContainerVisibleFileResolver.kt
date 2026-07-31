package com.kite.app.foundation.workspace

import android.content.Context
import com.kite.app.foundation.runtime.KFContainerManager
import java.io.File
import java.net.URI

/** 把 Agent 可见的真实路径解析回 Android 宿主文件，不提供旧路径别名。 */
object ContainerVisibleFileResolver {

    fun resolve(context: Context, rawValue: String?): File? = resolve(
        hostWorkspaceRoot = KFContainerManager.resolveWorkspaceDirectory(context.applicationContext),
        rawValue = rawValue,
    )

    fun resolve(hostWorkspaceRoot: File, rawValue: String?): File? {
        val rawPath = rawValue
            ?.trim()
            ?.takeIf(String::isNotBlank)
            ?.let { value ->
                if (value.startsWith("file://", ignoreCase = true)) {
                    runCatching { URI(value).path }.getOrNull()
                } else {
                    value
                }
            }
            ?.trim()
            .orEmpty()
        if (rawPath.isBlank()) return null

        KiteStorageContract.normalizeWorkspacePath(rawPath)?.let { workspacePath ->
            if (workspacePath == KiteStorageContract.CONTAINER_WORKSPACE_ROOT) return null
            return KiteStorageContract.resolveHostWorkspacePath(hostWorkspaceRoot, workspacePath)?.absoluteFile
        }

        val normalized = KiteStorageContract.normalizeAbsoluteUnixPath(rawPath) ?: return null
        if (normalized == "/sdcard" || normalized.startsWith("/sdcard/")) return File(normalized).absoluteFile
        if (normalized.startsWith("/storage/") && normalized != "/storage/emulated") {
            return File(normalized).absoluteFile
        }
        return null
    }
}
