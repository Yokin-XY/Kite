package com.kite.app.platform.storage

import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.graphics.Point
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.DocumentsProvider
import android.webkit.MimeTypeMap
import com.kite.app.R
import com.kite.app.foundation.runtime.KFContainerManager
import com.kite.app.foundation.workspace.KiteStorageContract
import java.io.File
import java.io.FileNotFoundException
import java.util.ArrayDeque
import java.util.Locale
import java.util.PriorityQueue

/**
 * 把 Ubuntu `/workspace` 的同一份物理文件显示为安卓系统文件选择器中的顶级存储位置。
 *
 * 这里没有导入、导出或同步：安卓和 Ubuntu 始终操作同一棵宿主文件树。
 */
class KiteWorkspaceDocumentsProvider : DocumentsProvider() {

    override fun onCreate(): Boolean {
        workspaceRoot().mkdirs()
        return true
    }

    override fun queryRoots(projection: Array<out String>?): Cursor {
        val root = workspaceRoot().apply { mkdirs() }
        return MatrixCursor(resolveRootProjection(projection)).apply {
            newRow()
                .add(DocumentsContract.Root.COLUMN_ROOT_ID, ROOT_ID)
                .add(DocumentsContract.Root.COLUMN_DOCUMENT_ID, ROOT_DOCUMENT_ID)
                .add(DocumentsContract.Root.COLUMN_TITLE, ROOT_TITLE)
                .add(DocumentsContract.Root.COLUMN_SUMMARY, ROOT_SUMMARY)
                .add(DocumentsContract.Root.COLUMN_ICON, R.mipmap.ic_launcher)
                .add(DocumentsContract.Root.COLUMN_MIME_TYPES, "*/*")
                .add(DocumentsContract.Root.COLUMN_AVAILABLE_BYTES, root.usableSpace)
                .add(
                    DocumentsContract.Root.COLUMN_FLAGS,
                    DocumentsContract.Root.FLAG_LOCAL_ONLY or
                        DocumentsContract.Root.FLAG_SUPPORTS_CREATE or
                        DocumentsContract.Root.FLAG_SUPPORTS_RECENTS or
                        DocumentsContract.Root.FLAG_SUPPORTS_SEARCH or
                        DocumentsContract.Root.FLAG_SUPPORTS_IS_CHILD
                )
        }
    }

    override fun queryDocument(documentId: String, projection: Array<out String>?): Cursor {
        return MatrixCursor(resolveDocumentProjection(projection)).apply {
            includeDocument(this, fileForDocumentId(documentId))
        }
    }

    override fun queryChildDocuments(
        parentDocumentId: String,
        projection: Array<out String>?,
        sortOrder: String?
    ): Cursor {
        val parent = requireDirectory(parentDocumentId)
        return MatrixCursor(resolveDocumentProjection(projection)).apply {
            parent.listFiles().orEmpty()
                .asSequence()
                .filter(::isExposedFile)
                .sortedWith(compareBy<File>({ !it.isDirectory }, { it.name.lowercase(Locale.ROOT) }))
                .forEach { includeDocument(this, it) }
        }
    }

    override fun queryRecentDocuments(rootId: String, projection: Array<out String>?): Cursor {
        if (rootId != ROOT_ID) throw FileNotFoundException("未知工作区根：$rootId")
        val candidates = PriorityQueue<File>(RECENT_LIMIT, compareBy(File::lastModified))
        walkWorkspace(maxVisited = RECENT_SCAN_LIMIT) { file ->
            if (!file.isFile) return@walkWorkspace
            if (candidates.size < RECENT_LIMIT) {
                candidates += file
            } else if (file.lastModified() > (candidates.peek()?.lastModified() ?: Long.MIN_VALUE)) {
                candidates.poll()
                candidates += file
            }
        }
        return MatrixCursor(resolveDocumentProjection(projection)).apply {
            candidates.sortedByDescending(File::lastModified).forEach { includeDocument(this, it) }
        }
    }

    override fun querySearchDocuments(
        rootId: String,
        query: String,
        projection: Array<out String>?
    ): Cursor {
        if (rootId != ROOT_ID) throw FileNotFoundException("未知工作区根：$rootId")
        val needle = query.trim().lowercase(Locale.ROOT)
        val matches = mutableListOf<File>()
        if (needle.isNotEmpty()) {
            walkWorkspace(maxVisited = SEARCH_SCAN_LIMIT) { file ->
                if (matches.size < SEARCH_RESULT_LIMIT && file.name.lowercase(Locale.ROOT).contains(needle)) {
                    matches += file
                }
            }
        }
        return MatrixCursor(resolveDocumentProjection(projection)).apply {
            matches.forEach { includeDocument(this, it) }
        }
    }

    override fun openDocument(
        documentId: String,
        mode: String,
        signal: CancellationSignal?
    ): ParcelFileDescriptor {
        val file = requireExistingFile(documentId)
        if (file.isDirectory) throw FileNotFoundException("目录不能作为普通文件打开：$documentId")
        val accessMode = ParcelFileDescriptor.parseMode(mode)
        return if ('w' in mode || 'a' in mode || '+' in mode) {
            ParcelFileDescriptor.open(file, accessMode, Handler(Looper.getMainLooper())) {
                notifyDocumentChanged(documentId)
            }
        } else {
            ParcelFileDescriptor.open(file, accessMode)
        }
    }

    override fun openDocumentThumbnail(
        documentId: String,
        sizeHint: Point,
        signal: CancellationSignal?
    ): android.content.res.AssetFileDescriptor {
        val file = requireExistingFile(documentId)
        if (!mimeTypeFor(file).startsWith("image/")) {
            throw FileNotFoundException("文件没有可用缩略图：$documentId")
        }
        return android.content.res.AssetFileDescriptor(
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY),
            0,
            android.content.res.AssetFileDescriptor.UNKNOWN_LENGTH
        )
    }

    override fun createDocument(parentDocumentId: String, mimeType: String, displayName: String): String {
        val parent = requireDirectory(parentDocumentId)
        val name = validateDisplayName(displayName)
        val target = safeChild(parent, name)
        if (target.exists()) throw FileNotFoundException("同名文件已经存在：$name")
        val created = if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
            target.mkdir()
        } else {
            target.createNewFile()
        }
        if (!created) throw FileNotFoundException("无法创建：$name")
        val documentId = documentIdForFile(target)
        notifyChildrenChanged(parentDocumentId)
        notifyDocumentChanged(documentId)
        return documentId
    }

    override fun renameDocument(documentId: String, displayName: String): String {
        if (documentId == ROOT_DOCUMENT_ID) throw FileNotFoundException("不能重命名工作区根")
        val source = requireExistingFile(documentId)
        val target = safeChild(source.parentFile ?: workspaceRoot(), validateDisplayName(displayName))
        if (target.exists()) throw FileNotFoundException("同名文件已经存在：${target.name}")
        if (!source.renameTo(target)) throw FileNotFoundException("无法重命名：${source.name}")
        val renamedId = documentIdForFile(target)
        notifyChildrenChanged(documentIdForFile(target.parentFile ?: workspaceRoot()))
        notifyDocumentChanged(renamedId)
        return renamedId
    }

    override fun deleteDocument(documentId: String) {
        if (documentId == ROOT_DOCUMENT_ID) throw FileNotFoundException("不能删除工作区根")
        val target = requireExistingFile(documentId)
        val parentId = documentIdForFile(target.parentFile ?: workspaceRoot())
        if (!target.deleteRecursively()) throw FileNotFoundException("无法删除：${target.name}")
        notifyChildrenChanged(parentId)
        notifyDocumentChanged(documentId)
    }

    override fun moveDocument(
        sourceDocumentId: String,
        sourceParentDocumentId: String,
        targetParentDocumentId: String
    ): String {
        if (sourceDocumentId == ROOT_DOCUMENT_ID) throw FileNotFoundException("不能移动工作区根")
        val source = requireExistingFile(sourceDocumentId)
        val sourceParent = requireDirectory(sourceParentDocumentId)
        val targetParent = requireDirectory(targetParentDocumentId)
        if (source.parentFile?.canonicalFile != sourceParent.canonicalFile) {
            throw FileNotFoundException("来源父目录不匹配")
        }
        val target = safeChild(targetParent, source.name)
        if (target.exists()) throw FileNotFoundException("目标目录已有同名文件：${source.name}")
        if (!source.renameTo(target)) throw FileNotFoundException("无法移动：${source.name}")
        val targetId = documentIdForFile(target)
        notifyChildrenChanged(sourceParentDocumentId)
        notifyChildrenChanged(targetParentDocumentId)
        notifyDocumentChanged(targetId)
        return targetId
    }

    override fun isChildDocument(parentDocumentId: String, documentId: String): Boolean {
        return runCatching {
            val parent = fileForDocumentId(parentDocumentId).canonicalFile.toPath()
            val child = fileForDocumentId(documentId).canonicalFile.toPath()
            child != parent && child.startsWith(parent)
        }.getOrDefault(false)
    }

    override fun getDocumentType(documentId: String): String = mimeTypeFor(requireExistingFile(documentId))

    private fun includeDocument(cursor: MatrixCursor, file: File) {
        if (!isExposedFile(file)) return
        val mimeType = mimeTypeFor(file)
        val flags = if (file.isDirectory) {
            DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE or
                DocumentsContract.Document.FLAG_SUPPORTS_DELETE or
                DocumentsContract.Document.FLAG_SUPPORTS_RENAME or
                DocumentsContract.Document.FLAG_SUPPORTS_MOVE
        } else {
            DocumentsContract.Document.FLAG_SUPPORTS_WRITE or
                DocumentsContract.Document.FLAG_SUPPORTS_DELETE or
                DocumentsContract.Document.FLAG_SUPPORTS_RENAME or
                DocumentsContract.Document.FLAG_SUPPORTS_MOVE or
                if (mimeType.startsWith("image/")) DocumentsContract.Document.FLAG_SUPPORTS_THUMBNAIL else 0
        }
        cursor.newRow()
            .add(DocumentsContract.Document.COLUMN_DOCUMENT_ID, documentIdForFile(file))
            .add(DocumentsContract.Document.COLUMN_DISPLAY_NAME, if (file == workspaceRoot()) ROOT_TITLE else file.name)
            .add(DocumentsContract.Document.COLUMN_MIME_TYPE, mimeType)
            .add(DocumentsContract.Document.COLUMN_FLAGS, flags)
            .add(DocumentsContract.Document.COLUMN_SIZE, if (file.isFile) file.length() else null)
            .add(DocumentsContract.Document.COLUMN_LAST_MODIFIED, file.lastModified())
    }

    private fun walkWorkspace(maxVisited: Int, consume: (File) -> Unit) {
        val pending = ArrayDeque<File>()
        pending += workspaceRoot()
        var visited = 0
        while (pending.isNotEmpty() && visited < maxVisited) {
            val current = pending.removeFirst()
            current.listFiles().orEmpty().asSequence().filter(::isExposedFile).forEach { child ->
                if (visited >= maxVisited) return
                visited++
                consume(child)
                if (child.isDirectory) pending += child
            }
        }
    }

    private fun workspaceRoot(): File {
        val appContext = context?.applicationContext
            ?: throw IllegalStateException("DocumentsProvider 尚未附加 Context")
        return KFContainerManager.resolveWorkspaceDirectory(appContext).absoluteFile
    }

    private fun requireDirectory(documentId: String): File {
        val file = requireExistingFile(documentId)
        if (!file.isDirectory) throw FileNotFoundException("不是目录：$documentId")
        return file
    }

    private fun requireExistingFile(documentId: String): File {
        val file = fileForDocumentId(documentId)
        if (!file.exists()) throw FileNotFoundException("文件不存在：$documentId")
        return file
    }

    private fun fileForDocumentId(documentId: String): File {
        val root = workspaceRoot().apply { mkdirs() }
        if (documentId == ROOT_DOCUMENT_ID) return root
        if (!documentId.startsWith(DOCUMENT_ID_PREFIX)) throw FileNotFoundException("未知文档：$documentId")
        val relative = documentId.removePrefix(DOCUMENT_ID_PREFIX)
        if (relative.isBlank()) throw FileNotFoundException("文档路径为空")
        if (KiteStorageContract.isReservedWorkspaceRootName(relative.substringBefore('/'))) {
            throw FileNotFoundException("该目录不对外显示")
        }
        val target = root.toPath().resolve(relative).normalize().toFile()
        ensureInsideRoot(target)
        return target
    }

    private fun documentIdForFile(file: File): String {
        val rootPath = workspaceRoot().absoluteFile.toPath().normalize()
        val filePath = file.absoluteFile.toPath().normalize()
        if (filePath == rootPath) return ROOT_DOCUMENT_ID
        if (!filePath.startsWith(rootPath)) throw FileNotFoundException("文件不属于工作区")
        ensureInsideRoot(file)
        val relative = rootPath.relativize(filePath).joinToString("/")
        if (KiteStorageContract.isReservedWorkspaceRootName(relative.substringBefore('/'))) {
            throw FileNotFoundException("该目录不对外显示")
        }
        return "$DOCUMENT_ID_PREFIX$relative"
    }

    private fun ensureInsideRoot(file: File) {
        val rootPath = workspaceRoot().canonicalFile.toPath()
        val filePath = file.canonicalFile.toPath()
        if (filePath != rootPath && !filePath.startsWith(rootPath)) {
            throw FileNotFoundException("文档路径越出工作区")
        }
    }

    private fun safeChild(parent: File, displayName: String): File {
        val target = File(parent, displayName)
        ensureInsideRoot(target)
        return target
    }

    private fun isExposedFile(file: File): Boolean {
        if (file.parentFile?.absoluteFile == workspaceRoot().absoluteFile &&
            KiteStorageContract.isReservedWorkspaceRootName(file.name)
        ) {
            return false
        }
        return runCatching {
            ensureInsideRoot(file)
            true
        }.getOrDefault(false)
    }

    private fun validateDisplayName(displayName: String): String {
        val name = displayName.trim()
        if (name.isBlank() || name == "." || name == ".." || '/' in name || '\\' in name || '\u0000' in name) {
            throw FileNotFoundException("无效文件名")
        }
        if (KiteStorageContract.isReservedWorkspaceRootName(name)) {
            throw FileNotFoundException("该名称由 Kite 保留")
        }
        return name
    }

    private fun mimeTypeFor(file: File): String {
        if (file.isDirectory) return DocumentsContract.Document.MIME_TYPE_DIR
        val extension = file.extension.lowercase(Locale.ROOT)
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "application/octet-stream"
    }

    private fun notifyChildrenChanged(parentDocumentId: String) {
        val ctx = context ?: return
        ctx.contentResolver.notifyChange(
            DocumentsContract.buildChildDocumentsUri(authority(ctx), parentDocumentId),
            null
        )
    }

    private fun notifyDocumentChanged(documentId: String) {
        val ctx = context ?: return
        ctx.contentResolver.notifyChange(
            DocumentsContract.buildDocumentUri(authority(ctx), documentId),
            null
        )
    }

    private fun resolveRootProjection(projection: Array<out String>?): Array<String> {
        return projection?.map(String::toString)?.toTypedArray() ?: DEFAULT_ROOT_PROJECTION
    }

    private fun resolveDocumentProjection(projection: Array<out String>?): Array<String> {
        return projection?.map(String::toString)?.toTypedArray() ?: DEFAULT_DOCUMENT_PROJECTION
    }

    companion object {
        const val AUTHORITY_SUFFIX = ".workspace.documents"
        const val ROOT_ID = "kite-ubuntu"
        const val ROOT_DOCUMENT_ID = "workspace"
        const val ROOT_TITLE = "Kite Ubuntu"
        const val ROOT_SUMMARY = "Ubuntu 工作区"
        private const val DOCUMENT_ID_PREFIX = "workspace:"
        private const val RECENT_LIMIT = 48
        private const val RECENT_SCAN_LIMIT = 4_000
        private const val SEARCH_RESULT_LIMIT = 100
        private const val SEARCH_SCAN_LIMIT = 8_000
        private val DEFAULT_ROOT_PROJECTION = arrayOf(
            DocumentsContract.Root.COLUMN_ROOT_ID,
            DocumentsContract.Root.COLUMN_DOCUMENT_ID,
            DocumentsContract.Root.COLUMN_TITLE,
            DocumentsContract.Root.COLUMN_SUMMARY,
            DocumentsContract.Root.COLUMN_ICON,
            DocumentsContract.Root.COLUMN_FLAGS,
            DocumentsContract.Root.COLUMN_MIME_TYPES,
            DocumentsContract.Root.COLUMN_AVAILABLE_BYTES
        )
        private val DEFAULT_DOCUMENT_PROJECTION = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_FLAGS,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED
        )

        fun authority(context: Context): String = "${context.packageName}$AUTHORITY_SUFFIX"

        fun rootDocumentUri(context: Context) = DocumentsContract.buildDocumentUri(
            authority(context),
            ROOT_DOCUMENT_ID,
        )

        /** 系统目录选择器用 Root URI 才会直接定位此存储根；普通 Document URI 会被部分厂商忽略。 */
        fun pickerRootUri(context: Context) = DocumentsContract.buildRootUri(
            authority(context),
            ROOT_ID,
        )
    }
}
