package com.kite.app.agent.zcode

import com.kite.app.agent.contract.AgentContent
import com.kite.app.agent.contract.AgentReasoningLevel
import com.kite.app.agent.contract.AgentReasoningMode
import com.kite.app.agent.contract.AgentReasoningSemantics
import com.kite.app.agent.process.AgentProcessChannel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/** ZCode app-server 的逐行 JSON-RPC 传输。 */
internal class ZCodeAppServerRpc(
    private val process: AgentProcessChannel,
    private val scope: CoroutineScope,
    private val diagnosticSink: (String) -> Unit,
) {
    private val sequence = AtomicLong(0L)
    private val pending = ConcurrentHashMap<Long, CompletableDeferred<JSONObject>>()
    private val methods = ConcurrentHashMap<Long, String>()
    private val closed = AtomicBoolean(false)

    var notificationHandler: suspend (String, JSONObject) -> Unit = { _, _ -> }
    var serverRequestHandler: suspend (String, JSONObject) -> JSONObject = { method, _ ->
        throw UnsupportedOperationException("ZCode app-server 请求暂不支持: $method")
    }

    fun start() {
        scope.launch(Dispatchers.IO + CoroutineName("zcode-app-server-stdout")) {
            process.stdoutLines.collect(::handleLine)
        }
        scope.launch(Dispatchers.IO + CoroutineName("zcode-app-server-stderr")) {
            process.stderrLines.collect(diagnosticSink)
        }
    }

    suspend fun request(method: String, params: JSONObject = JSONObject()): JSONObject {
        check(!closed.get()) { "ZCode app-server 连接已关闭" }
        val id = sequence.incrementAndGet()
        val response = CompletableDeferred<JSONObject>()
        pending[id] = response
        methods[id] = method
        try {
            process.writeLine(
                JSONObject().put("id", id).put("method", method).put("params", params).toString()
            )
            return response.await()
        } finally {
            pending.remove(id, response)
            methods.remove(id)
        }
    }

    fun close(cause: Throwable? = null) {
        if (!closed.compareAndSet(false, true)) return
        val error = cause ?: CancellationException("ZCode app-server 连接已关闭")
        pending.values.forEach { it.completeExceptionally(error) }
        pending.clear()
        methods.clear()
    }

    private fun handleLine(line: String) {
        val message = runCatching { JSONObject(line) }.getOrElse {
            diagnosticSink("ZCode app-server 输出了无效 JSON")
            return
        }
        val rawId = message.opt("id")?.takeUnless { it == JSONObject.NULL }
        val responseId = when (rawId) {
            is Number -> rawId.toLong()
            is String -> rawId.toLongOrNull()
            else -> null
        }
        if (responseId != null && (message.has("result") || message.has("error"))) {
            val deferred = pending.remove(responseId) ?: return
            val error = message.optJSONObject("error")
            if (error == null) {
                deferred.complete(message.optJSONObject("result") ?: JSONObject())
            } else {
                val method = methods[responseId].orEmpty()
                val code = error.optInt("code")
                val detail = error.optString("message").ifBlank { "ZCode app-server 请求失败" }
                diagnosticSink("ZCode app-server 请求失败: method=$method, code=$code, message=$detail")
                deferred.completeExceptionally(ZCodeProtocolException(code, detail))
            }
            return
        }
        val method = message.optString("method").takeIf(String::isNotBlank) ?: return
        val params = message.optJSONObject("params") ?: JSONObject()
        if (rawId != null) {
            scope.launch {
                runCatching { serverRequestHandler(method, params) }
                    .onSuccess { result ->
                        process.writeLine(JSONObject().put("id", rawId).put("result", result).toString())
                    }
                    .onFailure { error ->
                        process.writeLine(
                            JSONObject().put("id", rawId).put(
                                "error",
                                JSONObject().put("code", -32601).put(
                                    "message",
                                    error.message ?: "ZCode 客户端请求暂不支持",
                                ),
                            ).toString()
                        )
                    }
            }
        } else {
            scope.launch { notificationHandler(method, params) }
        }
    }
}

internal class ZCodeProtocolException(
    val code: Int,
    message: String,
) : IllegalStateException(message)

internal data class ZCodeModel(
    val providerId: String,
    val modelId: String,
    val displayName: String,
    val variant: String? = null,
) {
    val selectionId: String = "$providerId/$modelId" + variant?.let { "#$it" }.orEmpty()

    fun toProtocol(): JSONObject = JSONObject()
        .put("providerId", providerId)
        .put("modelId", modelId)
        .apply { variant?.let { put("variant", it) } }
}

internal fun JSONObject.toZCodeModel(): ZCodeModel? {
    val providerId = nullableString("providerId") ?: nullableString("provider") ?: return null
    val modelId = nullableString("modelId") ?: nullableString("id") ?: return null
    return ZCodeModel(
        providerId = providerId,
        modelId = modelId,
        displayName = nullableString("displayName") ?: nullableString("name") ?: modelId,
        variant = nullableString("variant"),
    )
}

internal fun zcodeReasoningSemantics(value: String): AgentReasoningSemantics? = when (value.lowercase()) {
    "none", "off" -> AgentReasoningLevel.Off
    "minimal" -> AgentReasoningLevel.Minimal
    "low" -> AgentReasoningLevel.Low
    "medium" -> AgentReasoningLevel.Medium
    "high" -> AgentReasoningLevel.High
    "xhigh", "x-high", "x_high", "extra-high" -> AgentReasoningLevel.ExtraHigh
    "max", "ultra" -> AgentReasoningLevel.Maximum
    "auto", "adaptive" -> AgentReasoningMode.Adaptive
    "enabled", "on" -> AgentReasoningMode.Enabled
    else -> null
}

internal fun List<AgentContent>.toZCodePrompt(): String? {
    val blocks = mapNotNull { content ->
        when (content) {
            is AgentContent.Text -> content.text
            is AgentContent.ResourceLink -> content.localFilePath()?.let { path ->
                "[用户附加文件，可按需通过文件工具读取：$path]"
            }
            is AgentContent.EmbeddedText -> "[用户附加文本：${content.uri}]\n${content.text}"
            else -> return null
        }
    }
    return blocks.filter(String::isNotBlank).joinToString("\n\n").takeIf(String::isNotBlank)
}

private fun AgentContent.ResourceLink.localFilePath(): String? = runCatching {
    val parsed = URI(uri)
    if (!parsed.scheme.equals("file", ignoreCase = true)) return@runCatching null
    parsed.path?.takeIf { it.startsWith('/') }
}.getOrNull()

internal fun JSONArray?.zcodeObjects(): List<JSONObject> = buildList {
    val source = this@zcodeObjects ?: return@buildList
    repeat(source.length()) { index -> source.optJSONObject(index)?.let(::add) }
}

internal fun JSONArray?.zcodeStrings(): List<String> = buildList {
    val source = this@zcodeStrings ?: return@buildList
    repeat(source.length()) { index ->
        when (val item = source.opt(index)) {
            is String -> item.takeIf(String::isNotBlank)?.let(::add)
            is JSONObject -> (item.nullableString("id") ?: item.nullableString("value"))?.let(::add)
        }
    }
}

internal fun JSONObject.nullableString(key: String): String? =
    opt(key)?.takeUnless { it == JSONObject.NULL }?.toString()?.trim()?.takeIf(String::isNotBlank)
