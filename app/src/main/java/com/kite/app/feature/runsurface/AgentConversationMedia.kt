package com.kite.app.feature.runsurface

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.util.LruCache
import androidx.core.content.FileProvider
import com.kite.app.foundation.workspace.ContainerVisibleFileResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.URI
import java.net.URL
import java.security.MessageDigest

internal sealed interface AgentImageSource {
    data class InlineBase64(val data: String) : AgentImageSource
    data class Link(val uri: String) : AgentImageSource
}

internal sealed interface AgentFileSource {
    data class Link(val uri: String) : AgentFileSource
    data class InlineBase64(val data: String) : AgentFileSource
    data class InlineText(val text: String) : AgentFileSource
}

internal object AgentMediaPolicy {
    const val MAX_INLINE_BYTES = 12 * 1024 * 1024
    const val MAX_TEXT_BYTES = 2 * 1024 * 1024
    const val MAX_THUMBNAIL_EDGE = 1080

    fun readInlineText(
        declaredSizeBytes: Long?,
        openInput: () -> java.io.InputStream?
    ): String {
        val knownSizeBytes = declaredSizeBytes?.takeIf { it >= 0L }
        require(knownSizeBytes == null || knownSizeBytes <= MAX_TEXT_BYTES) {
            "文本文件超过 2 MB"
        }

        val bytes = openInput()?.use { input ->
            val limit = MAX_TEXT_BYTES + 1
            val output = ByteArrayOutputStream(minOf(limit, DEFAULT_BUFFER_SIZE))
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var remaining = limit
            while (remaining > 0) {
                val read = input.read(buffer, 0, minOf(buffer.size, remaining))
                if (read < 0) break
                if (read == 0) {
                    val next = input.read()
                    if (next < 0) break
                    output.write(next)
                    remaining--
                    continue
                }
                output.write(buffer, 0, read)
                remaining -= read
            }
            output.toByteArray()
        } ?: error("无法读取文本文件")

        require(bytes.size <= MAX_TEXT_BYTES) { "文本文件超过 2 MB" }
        return String(bytes, Charsets.UTF_8)
    }

    fun estimatedDecodedBytes(value: String): Long {
        val payload = value.substringAfter("base64,", value)
        var useful = 0L
        payload.forEach { char -> if (!char.isWhitespace()) useful++ }
        return useful * 3L / 4L
    }

    fun safeDisplayName(value: String?, fallback: String, mimeType: String?): String {
        val base = value
            ?.substringBefore('#')
            ?.substringBefore('?')
            ?.substringAfterLast('/')
            ?.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]+"), "_")
            ?.takeLast(120)
            ?.takeIf(String::isNotBlank)
        if (base != null) return base
        val extension = when (mimeType?.lowercase()) {
            "image/jpeg" -> "jpg"
            "image/png" -> "png"
            "image/webp" -> "webp"
            "image/gif" -> "gif"
            "audio/mpeg" -> "mp3"
            "audio/wav", "audio/x-wav" -> "wav"
            "application/pdf" -> "pdf"
            "application/json" -> "json"
            "text/plain" -> "txt"
            else -> null
        }
        return if (extension == null) fallback else "$fallback.$extension"
    }

    fun canDelegateUri(uri: String): Boolean = when (runCatching { URI(uri).scheme?.lowercase() }.getOrNull()) {
        "content", "http", "https" -> true
        else -> false
    }
}

internal class AgentConversationMediaRepository(
    context: Context
) {
    private val appContext = context.applicationContext
    private val bitmaps = object : LruCache<String, Bitmap>(BITMAP_CACHE_BYTES) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount
    }

    suspend fun loadThumbnail(
        cacheKey: String,
        source: AgentImageSource,
        mimeType: String
    ): Bitmap = withContext(Dispatchers.IO) {
        require(mimeType.startsWith("image/")) { "不支持的图片类型" }
        val contentKey = when (source) {
            is AgentImageSource.InlineBase64 ->
                "$cacheKey:inline:${source.data.length}:${source.data.take(16)}:${source.data.takeLast(16)}"
            is AgentImageSource.Link -> "$cacheKey:link:${source.uri}"
        }
        bitmaps.get(contentKey)?.let { return@withContext it }
        val bytes = when (source) {
            is AgentImageSource.InlineBase64 -> {
                require(AgentMediaPolicy.estimatedDecodedBytes(source.data) <= AgentMediaPolicy.MAX_INLINE_BYTES) {
                    "图片超过 12 MB"
                }
                val payload = source.data.substringAfter("base64,", source.data)
                runCatching { Base64.decode(payload, Base64.DEFAULT) }
                    .getOrElse { error("图片数据无效") }
            }
            is AgentImageSource.Link -> readLinkedImage(source.uri)
        }
        require(bytes.size <= AgentMediaPolicy.MAX_INLINE_BYTES) { "图片超过 12 MB" }

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "无法识别图片" }
        var sample = 1
        while (bounds.outWidth / sample > AgentMediaPolicy.MAX_THUMBNAIL_EDGE * 2 ||
            bounds.outHeight / sample > AgentMediaPolicy.MAX_THUMBNAIL_EDGE * 2
        ) {
            sample *= 2
        }
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            ?: error("无法解码图片")
        bitmaps.put(contentKey, bitmap)
        bitmap
    }

    private fun readLinkedImage(rawUri: String): ByteArray {
        val scheme = runCatching { URI(rawUri).scheme?.lowercase() }.getOrNull()
        return when (scheme) {
            "http", "https" -> URL(rawUri).openConnection().apply {
                connectTimeout = IMAGE_CONNECT_TIMEOUT_MS
                readTimeout = IMAGE_READ_TIMEOUT_MS
                useCaches = true
            }.getInputStream().use(::readLimited)
            "content" -> appContext.contentResolver.openInputStream(Uri.parse(rawUri))
                ?.use(::readLimited)
                ?: error("无法读取图片")
            "file", null -> {
                val file = ContainerVisibleFileResolver.resolve(appContext, rawUri)
                    ?: error("图片不在 Kite 可访问的工作区或共享存储")
                require(file.isFile) { "图片不存在" }
                require(file.length() <= AgentMediaPolicy.MAX_INLINE_BYTES) { "图片超过 12 MB" }
                file.readBytes()
            }
            else -> error("不支持的图片地址")
        }
    }

    private fun readLimited(input: java.io.InputStream): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            require(total <= AgentMediaPolicy.MAX_INLINE_BYTES) { "图片超过 12 MB" }
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    suspend fun resolveOpenUri(
        cacheKey: String,
        displayName: String,
        mimeType: String?,
        source: AgentFileSource
    ): Uri = withContext(Dispatchers.IO) {
        when (source) {
            is AgentFileSource.Link -> {
                require(AgentMediaPolicy.canDelegateUri(source.uri)) {
                    "此文件仍位于 Agent 工作区，请先让 Agent 导出到共享目录"
                }
                Uri.parse(source.uri)
            }
            is AgentFileSource.InlineBase64 -> {
                require(AgentMediaPolicy.estimatedDecodedBytes(source.data) <= AgentMediaPolicy.MAX_INLINE_BYTES) {
                    "文件超过 12 MB"
                }
                val payload = source.data.substringAfter("base64,", source.data)
                val bytes = runCatching { Base64.decode(payload, Base64.DEFAULT) }
                    .getOrElse { error("文件数据无效") }
                require(bytes.size <= AgentMediaPolicy.MAX_INLINE_BYTES) { "文件超过 12 MB" }
                materialize(cacheKey, displayName, mimeType, bytes)
            }
            is AgentFileSource.InlineText -> {
                val bytes = source.text.toByteArray(Charsets.UTF_8)
                require(bytes.size <= AgentMediaPolicy.MAX_TEXT_BYTES) { "文本文件超过 2 MB" }
                materialize(cacheKey, displayName, mimeType, bytes)
            }
        }
    }

    private fun materialize(
        cacheKey: String,
        displayName: String,
        mimeType: String?,
        bytes: ByteArray
    ): Uri {
        val directory = File(requireNotNull(appContext.getExternalFilesDir(null)), "agent-media").apply { mkdirs() }
        val name = AgentMediaPolicy.safeDisplayName(displayName, "agent-file", mimeType)
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(cacheKey.toByteArray())
            .take(8)
            .joinToString("") { "%02x".format(it) }
        val target = File(directory, "$digest-$name")
        val temporary = File(directory, ".${target.name}.tmp")
        temporary.outputStream().use { it.write(bytes) }
        if (target.exists() && !target.delete()) error("无法更新临时文件")
        if (!temporary.renameTo(target)) {
            temporary.delete()
            error("无法保存临时文件")
        }
        target.setLastModified(System.currentTimeMillis())
        pruneFileCache(directory, keep = target)
        return FileProvider.getUriForFile(appContext, "${appContext.packageName}.fileprovider", target)
    }

    private fun pruneFileCache(directory: File, keep: File) {
        val files = directory.listFiles()
            ?.filter { it.isFile && !it.name.startsWith('.') }
            ?.sortedByDescending(File::lastModified)
            .orEmpty()
        var retained = 0L
        files.forEach { file ->
            retained += file.length()
            if (retained > FILE_CACHE_BYTES && file != keep) file.delete()
        }
    }

    private companion object {
        const val BITMAP_CACHE_BYTES = 16 * 1024 * 1024
        const val FILE_CACHE_BYTES = 64L * 1024L * 1024L
        const val IMAGE_CONNECT_TIMEOUT_MS = 8_000
        const val IMAGE_READ_TIMEOUT_MS = 15_000
    }
}
