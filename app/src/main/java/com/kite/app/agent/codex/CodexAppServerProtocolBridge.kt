package com.kite.app.agent.codex

import com.kite.app.agent.contract.AgentContent
import com.kite.app.agent.contract.AgentPermissionKind
import com.kite.app.agent.contract.AgentPermissionOption
import com.kite.app.agent.contract.AgentReasoningLevel
import com.kite.app.agent.contract.AgentReasoningSemantics
import com.kite.app.agent.contract.AgentToolCall
import com.kite.app.agent.contract.AgentToolCallPatch
import com.kite.app.agent.contract.AgentToolKind
import com.kite.app.agent.contract.AgentToolStatus
import com.kite.app.agent.contract.AgentTurnUsage
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
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

internal data class CodexEffort(
    val value: String,
    val description: String?,
    val semantics: AgentReasoningSemantics,
)

internal data class CodexModel(
    val id: String,
    val displayName: String,
    val description: String?,
    val defaultEffort: String?,
    val efforts: List<CodexEffort>,
    val isDefault: Boolean,
)

internal data class CodexCollaborationMode(
    val id: String,
    val name: String,
    val model: String?,
    val reasoningEffort: String?,
)

internal data class CodexNativePermissionSettings(
    val approvalPolicy: Any,
    val approvalsReviewer: String,
    val sandboxPolicy: JSONObject,
)

/** Codex App Server 的 JSON-RPC 传输，只向同包 Adapter 暴露结构化请求。 */
internal class CodexAppServerRpc(
    private val process: AgentProcessChannel,
    private val scope: CoroutineScope,
    private val diagnosticSink: (String) -> Unit,
) {
    private val sequence = AtomicLong(0L)
    private val pending = ConcurrentHashMap<Long, CompletableDeferred<JSONObject>>()
    private val closed = AtomicBoolean(false)
    var notificationHandler: suspend (String, JSONObject) -> Unit = { _, _ -> }
    var serverRequestHandler: suspend (String, JSONObject) -> JSONObject = { method, _ ->
        throw UnsupportedOperationException("Codex App Server 请求暂不支持: $method")
    }

    fun start() {
        scope.launch(Dispatchers.IO + CoroutineName("codex-app-server-stdout")) {
            process.stdoutLines.collect { line -> handleLine(line) }
        }
        scope.launch(Dispatchers.IO + CoroutineName("codex-app-server-stderr")) {
            process.stderrLines.collect(diagnosticSink)
        }
    }

    suspend fun request(method: String, params: JSONObject): JSONObject {
        check(!closed.get()) { "Codex App Server 连接已关闭" }
        val id = sequence.incrementAndGet()
        val response = CompletableDeferred<JSONObject>()
        pending[id] = response
        try {
            process.writeLine(
                JSONObject().put("method", method).put("id", id).put("params", params).toString()
            )
            return response.await()
        } finally {
            pending.remove(id, response)
        }
    }

    suspend fun notify(method: String, params: JSONObject) {
        check(!closed.get()) { "Codex App Server 连接已关闭" }
        process.writeLine(JSONObject().put("method", method).put("params", params).toString())
    }

    fun close(cause: Throwable? = null) {
        if (!closed.compareAndSet(false, true)) return
        val error = cause ?: CancellationException("Codex App Server 连接已关闭")
        pending.values.forEach { it.completeExceptionally(error) }
        pending.clear()
    }

    private fun handleLine(line: String) {
        val message = runCatching { JSONObject(line) }.getOrElse {
            diagnosticSink("Codex App Server 输出了无效 JSON")
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
            if (error != null) {
                deferred.completeExceptionally(
                    IllegalStateException(error.optString("message").ifBlank { "Codex App Server 请求失败" })
                )
            } else {
                deferred.complete(message.optJSONObject("result") ?: JSONObject())
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
                                JSONObject().put("code", -32601).put("message", error.message),
                            ).toString()
                        )
                    }
            }
        } else {
            scope.launch { notificationHandler(method, params) }
        }
    }
}

internal fun codexReasoningSemantics(value: String): AgentReasoningSemantics? = when (value.lowercase()) {
    "none", "off" -> AgentReasoningLevel.Off
    "minimal" -> AgentReasoningLevel.Minimal
    "low" -> AgentReasoningLevel.Low
    "medium" -> AgentReasoningLevel.Medium
    "high" -> AgentReasoningLevel.High
    "xhigh", "x-high", "x_high", "extra-high" -> AgentReasoningLevel.ExtraHigh
    "max", "ultra" -> AgentReasoningLevel.Maximum
    else -> null
}

internal fun List<AgentContent>.toCodexInput(): JSONArray? {
    if (any { it !is AgentContent.Text && it !is AgentContent.Image }) return null
    return JSONArray().apply {
        this@toCodexInput.forEach { content ->
            when (content) {
                is AgentContent.Text -> put(JSONObject().put("type", "text").put("text", content.text))
                is AgentContent.Image -> put(
                    JSONObject()
                        .put("type", "image")
                        .put("url", content.codexDataUrl()),
                )
                else -> error("已由 Codex 输入类型校验限制分支")
            }
        }
    }
}

private fun AgentContent.Image.codexDataUrl(): String =
    data.takeIf { it.startsWith("data:", ignoreCase = true) }
        ?: "data:${mimeType.ifBlank { "application/octet-stream" }};base64,$data"

internal fun JSONObject.toAgentUserContent(): AgentContent? = when (optString("type")) {
    "text" -> AgentContent.Text(optString("text"))
    "image" -> nullableString("url")?.toAgentImageContent()
    "localImage" -> nullableString("path")?.let { path ->
        AgentContent.Image(data = "", mimeType = path.inferredImageMimeType(), uri = path)
    }
    else -> null
}

internal fun JSONObject.reasoningText(): String? = buildList {
    addAll(optJSONArray("summary").strings())
    addAll(optJSONArray("content").strings())
}.filter(String::isNotBlank).joinToString("\n\n").takeIf(String::isNotBlank)

private fun String.toAgentImageContent(): AgentContent.Image {
    val match = DATA_URL_PATTERN.matchEntire(this)
    return if (match != null) {
        AgentContent.Image(
            data = match.groupValues[2],
            mimeType = match.groupValues[1].ifBlank { "application/octet-stream" },
        )
    } else {
        AgentContent.Image(data = "", mimeType = inferredImageMimeType(), uri = this)
    }
}

private fun String.inferredImageMimeType(): String = when (substringBefore('?').substringAfterLast('.').lowercase()) {
    "png" -> "image/png"
    "jpg", "jpeg" -> "image/jpeg"
    "gif" -> "image/gif"
    "webp" -> "image/webp"
    else -> "application/octet-stream"
}

internal fun Any.jsonCopy(): Any = when (this) {
    is JSONObject -> JSONObject(toString())
    is JSONArray -> JSONArray(toString())
    else -> this
}

internal fun JSONObject.toToolCall(): AgentToolCall? {
    val type = optString("type")
    val id = optString("id").takeIf(String::isNotBlank) ?: return null
    val title = when (type) {
        "commandExecution" -> optString("command").ifBlank { "执行命令" }
        "fileChange" -> "修改文件"
        "mcpToolCall" -> listOf(optString("server"), optString("tool")).filter(String::isNotBlank).joinToString("/")
        "dynamicToolCall" -> listOf(optString("namespace"), optString("tool"))
            .filter(String::isNotBlank).joinToString("/").ifBlank { "调用工具" }
        "collabAgentToolCall" -> optString("tool").ifBlank { "协作 Agent" }
        "subAgentActivity" -> optString("agentPath").ifBlank { "协作 Agent" }
        "webSearch" -> optString("query").ifBlank { "搜索网页" }
        "imageView" -> "查看图片"
        "imageGeneration" -> "生成图片"
        "sleep" -> "等待"
        "enteredReviewMode" -> "进入审查"
        "exitedReviewMode" -> "结束审查"
        "contextCompaction" -> "压缩上下文"
        else -> return null
    }
    val rawInput = when (type) {
        "commandExecution" -> nullableString("command")
        "fileChange" -> optJSONArray("changes")?.toString()
        "mcpToolCall", "dynamicToolCall" -> opt("arguments")?.takeUnless { it == JSONObject.NULL }?.toString()
        "collabAgentToolCall" -> nullableString("prompt")
        "subAgentActivity" -> nullableString("kind")
        "webSearch" -> nullableString("query")
        "imageView" -> nullableString("path")
        "imageGeneration" -> nullableString("revisedPrompt")
        "sleep" -> opt("durationMs")?.takeUnless { it == JSONObject.NULL }?.toString()
        "enteredReviewMode", "exitedReviewMode" -> nullableString("review")
        else -> null
    }
    val rawOutput = when (type) {
        "commandExecution" -> nullableString("aggregatedOutput")
        "mcpToolCall" -> opt("result")?.takeUnless { it == JSONObject.NULL }?.toString()
            ?: opt("error")?.takeUnless { it == JSONObject.NULL }?.toString()
        "dynamicToolCall" -> optJSONArray("contentItems")?.toString()
        "imageGeneration" -> nullableString("result")
        else -> null
    }
    val staticItem = type in setOf(
        "subAgentActivity",
        "imageView",
        "sleep",
        "enteredReviewMode",
        "exitedReviewMode",
        "contextCompaction",
    )
    return AgentToolCall(
        id = id,
        title = title,
        kind = AgentToolKind(type),
        status = AgentToolStatus(optString("status", if (staticItem) "completed" else "inProgress")),
        rawInput = rawInput,
        rawOutput = rawOutput,
    )
}

internal fun JSONObject.toToolPatch(): AgentToolCallPatch? {
    val call = toToolCall() ?: return null
    return AgentToolCallPatch(
        id = call.id,
        title = call.title,
        kind = call.kind,
        status = call.status,
        rawInput = call.rawInput,
        rawOutput = call.rawOutput,
    )
}

internal fun JSONObject.toTurnUsage(): AgentTurnUsage = AgentTurnUsage(
    inputTokens = optLong("inputTokens"),
    outputTokens = optLong("outputTokens"),
    totalTokens = optLong("totalTokens"),
    thoughtTokens = optLong("reasoningOutputTokens"),
    cachedReadTokens = optLong("cachedInputTokens"),
    cachedWriteTokens = optLong("cacheWriteInputTokens"),
)

internal fun approvalOption(decision: String): AgentPermissionOption = when (decision) {
    "accept" -> AgentPermissionOption(decision, "允许一次", AgentPermissionKind.AllowOnce)
    "acceptForSession" -> AgentPermissionOption(decision, "本次会话允许", AgentPermissionKind.AllowAlways)
    "decline" -> AgentPermissionOption(decision, "拒绝一次", AgentPermissionKind.RejectOnce)
    else -> AgentPermissionOption(decision, "拒绝并停止", AgentPermissionKind.RejectAlways)
}

private val DATA_URL_PATTERN = Regex("^data:([^;,]+)?;base64,(.*)$", RegexOption.IGNORE_CASE)

internal fun JSONArray?.objects(): List<JSONObject> = buildList {
    val source = this@objects ?: return@buildList
    repeat(source.length()) { index -> source.optJSONObject(index)?.let(::add) }
}

internal fun JSONArray?.strings(): List<String> = buildList {
    val source = this@strings ?: return@buildList
    repeat(source.length()) { index -> source.optString(index).takeIf(String::isNotBlank)?.let(::add) }
}

internal fun JSONObject.nullableString(key: String): String? =
    opt(key)?.takeUnless { it == JSONObject.NULL }?.toString()?.trim()?.takeIf(String::isNotBlank)
