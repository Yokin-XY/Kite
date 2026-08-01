package com.kite.app.foundation.runtime

import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.attribute.BasicFileAttributes
import org.json.JSONObject
import org.json.JSONTokener

/** Android 原生只读 JSON 能力的显式授权根。 */
internal data class StructuredJsonStringRoot(
    val containerPath: String,
    val directory: File,
)

internal data class StructuredJsonStringContext(
    val roots: List<StructuredJsonStringRoot>,
)

/** 只表达受控文件、字节上限和顶层字符串字段，不携带 shell 或业务标识。 */
internal data class StructuredJsonStringRequest(
    val containerPath: String,
    val maximumBytes: Long,
    val jsonField: String,
)

internal data class StructuredJsonStringPlan(
    val value: String,
)

/**
 * 在创建业务进程或产生原生副作用前，读取授权根内的小型普通 JSON 文件。
 *
 * 文件事实不完整时返回 Unsupported，由调用方整条回到兼容路径；合同或授权非法时
 * 返回 Blocked，禁止以另一条执行路径掩盖输入错误。
 */
internal object AndroidNativeStructuredJsonStringProvider {
    fun prepare(
        context: StructuredJsonStringContext,
        request: StructuredJsonStringRequest,
    ): RuntimeProviderDecision<StructuredJsonStringPlan> {
        if (context.roots.isEmpty()) return blocked("structured_json_roots_missing")
        if (request.maximumBytes !in 1L..MAXIMUM_JSON_BYTES) {
            return blocked("structured_json_maximum_bytes_invalid")
        }
        if (!JSON_FIELD.matches(request.jsonField)) return blocked("structured_json_field_invalid")
        val resolved = when (val resolution = resolve(context, request.containerPath)) {
            is JsonPathResolution.Ready -> resolution.file
            is JsonPathResolution.Unsupported -> return unsupported(resolution.reason)
            is JsonPathResolution.Blocked -> return blocked(resolution.reason)
        }
        val attributes = attributes(resolved) ?: return unsupported("structured_json_file_missing")
        if (!attributes.isRegularFile) return unsupported("structured_json_file_not_regular")
        if (attributes.size() > request.maximumBytes) return unsupported("structured_json_file_too_large")
        val bytes = runCatching { readBounded(resolved, request.maximumBytes) }.getOrNull()
            ?: return unsupported("structured_json_file_read_failed")
        val raw = decodeUtf8(bytes) ?: return unsupported("structured_json_utf8_invalid")
        val json = parseObject(raw) ?: return unsupported("structured_json_invalid")
        val value = runCatching { json.opt(request.jsonField) as? String }.getOrNull()
            ?: return unsupported("structured_json_string_field_missing")
        if (value.length > MAXIMUM_VALUE_CHARS) return unsupported("structured_json_value_too_large")
        return RuntimeProviderDecision.Ready(
            provider = RuntimeProviderKind.ANDROID_NATIVE,
            plan = StructuredJsonStringPlan(value),
            reason = "structured_json_string_ready",
        )
    }

    private fun resolve(
        context: StructuredJsonStringContext,
        rawPath: String,
    ): JsonPathResolution {
        if (!rawPath.startsWith('/') || rawPath.endsWith('/') || '\\' in rawPath || rawPath.length > 512) {
            return JsonPathResolution.Blocked("structured_json_path_invalid")
        }
        val root = context.roots
            .filter { candidate ->
                candidate.containerPath.startsWith('/') &&
                    candidate.containerPath != "/" &&
                    !candidate.containerPath.endsWith('/') &&
                    rawPath.startsWith("${candidate.containerPath}/")
            }
            .maxByOrNull { it.containerPath.length }
            ?: return JsonPathResolution.Blocked("structured_json_root_not_authorized")
        val segments = rawPath.removePrefix("${root.containerPath}/").split('/')
        if (segments.isEmpty() || segments.any { it.isBlank() || it == "." || it == ".." }) {
            return JsonPathResolution.Blocked("structured_json_path_segment_invalid")
        }
        val rootPath = root.directory.toPath().toAbsolutePath().normalize()
        if (!Files.isDirectory(rootPath, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(rootPath)) {
            return JsonPathResolution.Blocked("structured_json_root_invalid")
        }
        var cursor = rootPath
        segments.forEachIndexed { index, segment ->
            cursor = cursor.resolve(segment).normalize()
            if (!cursor.startsWith(rootPath)) return JsonPathResolution.Blocked("structured_json_path_escape")
            val attributes = attributes(cursor.toFile())
                ?: return JsonPathResolution.Unsupported("structured_json_path_missing")
            if (attributes.isSymbolicLink) {
                return JsonPathResolution.Unsupported("structured_json_symbolic_link")
            }
            if (index < segments.lastIndex && !attributes.isDirectory) {
                return JsonPathResolution.Unsupported("structured_json_parent_not_directory")
            }
        }
        return JsonPathResolution.Ready(cursor.toFile())
    }

    private fun attributes(file: File): BasicFileAttributes? = runCatching {
        Files.readAttributes(
            file.toPath(),
            BasicFileAttributes::class.java,
            LinkOption.NOFOLLOW_LINKS,
        )
    }.getOrNull()

    private fun readBounded(file: File, maximumBytes: Long): ByteArray {
        val limit = maximumBytes.toInt()
        return file.inputStream().use { input ->
            val output = ByteArrayOutputStream(limit.coerceAtMost(8 * 1024))
            val buffer = ByteArray(8 * 1024)
            var total = 0
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                total += count
                if (total > limit) error("structured_json_file_too_large")
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        }
    }

    private fun decodeUtf8(bytes: ByteArray): String? = runCatching {
        StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()
    }.getOrNull()

    private fun parseObject(raw: String): JSONObject? = runCatching {
        val tokener = JSONTokener(raw)
        val value = tokener.nextValue() as? JSONObject ?: return@runCatching null
        if (tokener.nextClean() != 0.toChar()) return@runCatching null
        value
    }.getOrNull()

    private fun unsupported(reason: String) =
        RuntimeProviderDecision.Unsupported(RuntimeProviderKind.ANDROID_NATIVE, reason)

    private fun blocked(reason: String) =
        RuntimeProviderDecision.Blocked(RuntimeProviderKind.ANDROID_NATIVE, reason)

    private sealed interface JsonPathResolution {
        data class Ready(val file: File) : JsonPathResolution
        data class Unsupported(val reason: String) : JsonPathResolution
        data class Blocked(val reason: String) : JsonPathResolution
    }

    private val JSON_FIELD = Regex("[A-Za-z][A-Za-z0-9_-]{0,63}")
    private const val MAXIMUM_JSON_BYTES = 1024L * 1024L
    private const val MAXIMUM_VALUE_CHARS = 256
}
