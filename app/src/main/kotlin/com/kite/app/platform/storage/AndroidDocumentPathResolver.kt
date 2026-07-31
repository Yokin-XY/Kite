package com.kite.app.platform.storage

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import com.kite.app.foundation.runtime.AndroidSharedStorageManager
import com.kite.app.foundation.runtime.KFContainerManager
import com.kite.app.foundation.workspace.KiteStorageContract
import java.io.File

/**
 * 在系统文件选择器返回的 URI 确实对应已挂入 Agent 的真实文件时，恢复其同一条 POSIX 路径。
 * 无法可靠恢复时返回 null，由调用方复制到工作区托管目录，绝不猜测媒体库路径。
 */
object AndroidDocumentPathResolver {

    /** 把系统目录选择器返回的 Kite Ubuntu 目录恢复成 Agent 使用的同一条 `/workspace` 路径。 */
    fun resolveWorkspaceDirectory(context: Context, uri: Uri): String? {
        val appContext = context.applicationContext
        if (uri.authority != KiteWorkspaceDocumentsProvider.authority(appContext)) return null
        val documentId = workspaceDocumentId(appContext, uri) ?: return null
        if (documentId == KiteWorkspaceDocumentsProvider.ROOT_DOCUMENT_ID) return null
        val containerPath = workspaceContainerPath(documentId) ?: return null
        if (!KiteStorageContract.isSelectableProjectPath(containerPath)) return null
        val hostDirectory = KiteStorageContract.resolveHostWorkspacePath(
            KFContainerManager.resolveWorkspaceDirectory(appContext),
            containerPath,
        )
        return containerPath.takeIf { hostDirectory?.isDirectory == true }
    }

    fun resolveAgentVisiblePath(context: Context, uri: Uri): String? {
        val appContext = context.applicationContext
        resolveWorkspaceDocument(appContext, uri)?.let { return it }
        if (!AndroidSharedStorageManager.hasBroadFileAccess(appContext)) return null

        val candidate = when {
            uri.scheme.equals("file", ignoreCase = true) -> uri.path?.let(::File)
            DocumentsContract.isDocumentUri(appContext, uri) &&
                uri.authority == EXTERNAL_STORAGE_AUTHORITY -> resolveExternalStorageDocument(uri)
            else -> null
        }?.absoluteFile ?: return null

        val snapshot = AndroidSharedStorageManager.snapshot(appContext)
        val candidatePath = candidate.toPath().normalize()
        val insideMountedVolume = snapshot.volumes.any { volume ->
            val root = File(volume.hostPath).absoluteFile.toPath().normalize()
            candidatePath == root || candidatePath.startsWith(root)
        }
        return candidate.absolutePath.takeIf { insideMountedVolume && candidate.isFile }
    }

    private fun resolveWorkspaceDocument(context: Context, uri: Uri): String? {
        if (uri.authority != KiteWorkspaceDocumentsProvider.authority(context)) return null
        val documentId = workspaceDocumentId(context, uri) ?: return null
        if (documentId == KiteWorkspaceDocumentsProvider.ROOT_DOCUMENT_ID) return null
        val containerPath = workspaceContainerPath(documentId) ?: return null
        val hostFile = KiteStorageContract.resolveHostWorkspacePath(
            KFContainerManager.resolveWorkspaceDirectory(context),
            containerPath,
        )
        return containerPath.takeIf { hostFile?.isFile == true }
    }

    private fun workspaceDocumentId(context: Context, uri: Uri): String? = when {
        DocumentsContract.isTreeUri(uri) ->
            runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull()
        DocumentsContract.isDocumentUri(context, uri) ->
            runCatching { DocumentsContract.getDocumentId(uri) }.getOrNull()
        else -> null
    }

    private fun workspaceContainerPath(documentId: String): String? {
        val relative = documentId.removePrefix(WORKSPACE_DOCUMENT_PREFIX)
        if (relative == documentId || relative.isBlank()) return null
        return KiteStorageContract.normalizeWorkspacePath(
            "${KiteStorageContract.CONTAINER_WORKSPACE_ROOT}/$relative"
        )
    }

    @Suppress("DEPRECATION")
    private fun resolveExternalStorageDocument(uri: Uri): File? {
        val documentId = runCatching { DocumentsContract.getDocumentId(uri) }.getOrNull() ?: return null
        val volumeId = documentId.substringBefore(':')
        val relative = documentId.substringAfter(':', "").trimStart('/')
        val root = if (volumeId.equals("primary", ignoreCase = true)) {
            Environment.getExternalStorageDirectory()
        } else {
            File("/storage", volumeId)
        }
        val target = root.toPath().resolve(relative).normalize()
        val rootPath = root.absoluteFile.toPath().normalize()
        if (target != rootPath && !target.startsWith(rootPath)) return null
        return target.toFile()
    }

    private const val EXTERNAL_STORAGE_AUTHORITY = "com.android.externalstorage.documents"
    private const val WORKSPACE_DOCUMENT_PREFIX = "workspace:"
}
