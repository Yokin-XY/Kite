package com.kite.app.agent.pi

import com.kite.app.agent.contract.AgentContent
import com.kite.app.agent.contract.AgentReasoningLevel
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

internal data class PiModel(
    val id: String,
    val name: String,
    val provider: String,
    val reasoning: Boolean,
    val input: Set<String>,
) {
    val selectionId: String = "$provider/$id"
}

internal data class PiState(
    val sessionId: String,
    val sessionFile: String?,
    val sessionName: String?,
    val model: PiModel?,
    val thinkingLevel: String?,
)

internal data class PiPromptPayload(
    val message: String,
    val images: JSONArray,
)

/** Pi Coding Agent 的 LF 分帧 JSONL RPC 客户端。 */
internal class PiRpc(
    private val process: AgentProcessChannel,
    private val scope: CoroutineScope,
    private val diagnosticSink: (String) -> Unit,
) {
    private val sequence = AtomicLong(0L)
    private val pending = ConcurrentHashMap<String, CompletableDeferred<JSONObject>>()
    private val closed = AtomicBoolean(false)
    var eventHandler: suspend (JSONObject) -> Unit = {}

    fun start() {
        scope.launch(Dispatchers.IO + CoroutineName("pi-rpc-stdout")) {
            process.stdoutLines.collect(::handleLine)
        }
        scope.launch(Dispatchers.IO + CoroutineName("pi-rpc-stderr")) {
            process.stderrLines.collect(diagnosticSink)
        }
    }

    suspend fun request(command: String, parameters: JSONObject = JSONObject()): JSONObject {
        check(!closed.get()) { "Pi RPC 连接已关闭" }
        val id = sequence.incrementAndGet().toString()
        val response = CompletableDeferred<JSONObject>()
        pending[id] = response
        try {
            val payload = JSONObject(parameters.toString()).put("id", id).put("type", command)
            process.writeLine(payload.toString())
            return response.await()
        } finally {
            pending.remove(id, response)
        }
    }

    fun close(cause: Throwable? = null) {
        if (!closed.compareAndSet(false, true)) return
        val error = cause ?: CancellationException("Pi RPC 连接已关闭")
        pending.values.forEach { it.completeExceptionally(error) }
        pending.clear()
    }

    private suspend fun handleLine(line: String) {
        val message = runCatching { JSONObject(line) }.getOrElse {
            diagnosticSink("Pi RPC 输出了无效 JSON")
            return
        }
        if (message.optString("type") == "response") {
            val id = message.opt("id")?.toString()?.takeIf(String::isNotBlank) ?: return
            val deferred = pending.remove(id) ?: return
            if (message.optBoolean("success", false)) {
                deferred.complete(message)
            } else {
                deferred.completeExceptionally(
                    IllegalStateException(message.optString("error").ifBlank { "Pi RPC 请求失败" })
                )
            }
            return
        }
        eventHandler(message)
    }
}

internal fun JSONObject.toPiModel(): PiModel? {
    val id = optString("id").trim().takeIf(String::isNotBlank) ?: return null
    val provider = optString("provider").trim().takeIf(String::isNotBlank) ?: return null
    return PiModel(
        id = id,
        name = optString("name", id).trim().ifBlank { id },
        provider = provider,
        reasoning = optBoolean("reasoning", false),
        input = optJSONArray("input").strings().map(String::lowercase).toSet(),
    )
}

internal fun JSONObject.toPiState(): PiState? {
    val data = optJSONObject("data") ?: return null
    val sessionId = data.optString("sessionId").trim().takeIf(String::isNotBlank) ?: return null
    return PiState(
        sessionId = sessionId,
        sessionFile = data.nullableString("sessionFile"),
        sessionName = data.nullableString("sessionName"),
        model = data.optJSONObject("model")?.toPiModel(),
        thinkingLevel = data.nullableString("thinkingLevel"),
    )
}

internal fun piReasoningSemantics(value: String): AgentReasoningSemantics? = when (value.lowercase()) {
    "off" -> AgentReasoningLevel.Off
    "minimal" -> AgentReasoningLevel.Minimal
    "low" -> AgentReasoningLevel.Low
    "medium" -> AgentReasoningLevel.Medium
    "high" -> AgentReasoningLevel.High
    "xhigh" -> AgentReasoningLevel.ExtraHigh
    "max" -> AgentReasoningLevel.Maximum
    else -> null
}

internal fun List<AgentContent>.toPiPrompt(): PiPromptPayload? {
    val text = mutableListOf<String>()
    val images = JSONArray()
    forEach { content ->
        when (content) {
            is AgentContent.Text -> text += content.text
            is AgentContent.Image -> images.put(
                JSONObject()
                    .put("type", "image")
                    .put("data", content.data.substringAfter("base64,", content.data))
                    .put("mimeType", content.mimeType),
            )
            is AgentContent.ResourceLink -> {
                val path = content.localFilePath() ?: return null
                text += "[用户附加文件，可按需通过文件工具读取：$path]"
            }
            is AgentContent.EmbeddedText -> text += buildString {
                append("[用户附加文本：${content.uri}]\n")
                append(content.text)
            }
            else -> return null
        }
    }
    val message = text.filter(String::isNotBlank).joinToString("\n\n")
    if (message.isBlank() && images.length() == 0) return null
    return PiPromptPayload(message, images)
}

private fun AgentContent.ResourceLink.localFilePath(): String? = runCatching {
    val parsed = URI(uri)
    if (!parsed.scheme.equals("file", ignoreCase = true)) return@runCatching null
    parsed.path?.takeIf { it.startsWith('/') }
}.getOrNull()

internal fun JSONArray?.objects(): List<JSONObject> = buildList {
    val source = this@objects ?: return@buildList
    for (index in 0 until source.length()) source.optJSONObject(index)?.let(::add)
}

internal fun JSONArray?.strings(): List<String> = buildList {
    val source = this@strings ?: return@buildList
    for (index in 0 until source.length()) source.optString(index).takeIf(String::isNotBlank)?.let(::add)
}

internal fun JSONObject.nullableString(key: String): String? =
    optString(key).trim().takeIf { has(key) && !isNull(key) && it.isNotBlank() }
