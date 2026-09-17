package com.kite.app.agent.zcode

import com.kite.app.agent.contract.AgentContent
import com.kite.app.agent.contract.AgentModelSource
import com.kite.app.agent.config.AgentProviderModelSummary
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
    val providerName: String = providerId,
    val modelSource: AgentModelSource = AgentModelSource.UserConfigured,
    val variant: String? = null,
) {
    val selectionId: String = "$providerId/$modelId" + variant?.let { "#$it" }.orEmpty()

    fun toProtocol(): JSONObject = JSONObject()
        .put("providerId", providerId)
        .put("modelId", modelId)
        .apply { variant?.let { put("variant", it) } }
}

internal fun JSONObject.toZCodeModel(): ZCodeModel? {
    val ref = optJSONObject("ref") ?: this
    val providerId = ref.nullableString("providerId") ?: ref.nullableString("provider") ?: return null
    val modelId = ref.nullableString("modelId") ?: ref.nullableString("id") ?: return null
    val providerSource = nullableString("providerSource")
    return ZCodeModel(
        providerId = providerId,
        modelId = modelId,
        displayName = nullableString("label") ?: nullableString("displayName") ?: nullableString("name") ?: modelId,
        providerName = nullableString("providerLabel") ?: providerId,
        modelSource = zcodeModelSource(providerId, providerSource),
        variant = ref.nullableString("variant"),
    )
}

/** ZCode 登录流程保留的 Provider ID；会话快照和原生配置必须使用同一来源判断。 */
internal fun zcodeModelSource(providerId: String, providerSource: String? = null): AgentModelSource =
    if (
        providerSource == "builtin" ||
        providerId.startsWith("builtin:") ||
        providerId == "zai" ||
        providerId == "bigmodel"
    ) {
        AgentModelSource.OfficialLogin
    } else {
        AgentModelSource.UserConfigured
    }

/** ZCode 官方 runtimeModel/workspace Provider 协议所需的内存态，不向 UI 暴露凭据。 */
internal class ZCodeRuntimeModelProvider(
    val providerId: String,
    val label: String,
    val kind: String,
    val apiFormat: String,
    val baseUrl: String?,
    private val apiKey: String?,
    val models: List<AgentProviderModelSummary>,
    val modelSource: AgentModelSource = AgentModelSource.UserConfigured,
) {
    fun toProtocol(): JSONObject = JSONObject()
        .put("providerId", providerId)
        .put("kind", kind)
        .put("apiFormat", apiFormat)
        .put("label", label)
        .put("source", "user")
        .put(
            "models",
            JSONArray().also { array ->
                models.forEach { model ->
                    array.put(
                        JSONObject()
                            .put("modelId", model.id)
                            .put("label", model.displayName.ifBlank { model.id }),
                    )
                }
            },
        )
        .apply {
            baseUrl?.takeIf(String::isNotBlank)?.let { put("baseURL", it) }
            apiKey?.takeIf(String::isNotBlank)?.let { key ->
                put("apiKey", JSONObject().put("source", "inline").put("value", key))
            }
        }
}

internal class ZCodeRuntimeModelCatalog(
    val revision: String,
    val generatedAt: Long,
    val providers: List<ZCodeRuntimeModelProvider>,
    private val selectedProviderId: String?,
    private val selectedModelId: String?,
    val advertisedModels: List<ZCodeModel> = emptyList(),
) {
    fun models(): List<ZCodeModel> = buildList {
        addAll(advertisedModels)
        providers.forEach { provider ->
            provider.models.forEach { model ->
                add(
                    ZCodeModel(
                        providerId = provider.providerId,
                        modelId = model.id,
                        displayName = model.displayName.ifBlank { model.id },
                        providerName = provider.label,
                        modelSource = provider.modelSource,
                    )
                )
            }
        }
    }.distinctBy(ZCodeModel::selectionId)

    fun selectedModel(): ZCodeModel? {
        val providerId = selectedProviderId ?: return null
        val modelId = selectedModelId ?: return null
        return models().firstOrNull { it.providerId == providerId && it.modelId == modelId }
    }

    fun selectedRuntimeModel(): JSONObject? {
        val providerId = selectedProviderId ?: return null
        val modelId = selectedModelId ?: return null
        return runtimeModel(providerId, modelId)
    }

    fun runtimeModel(providerId: String, modelId: String): JSONObject? {
        val provider = providers.firstOrNull { it.providerId == providerId } ?: return null
        if (provider.models.none { it.id == modelId }) return null
        return JSONObject()
            .put("revision", revision)
            .put("generatedAt", generatedAt)
            .put("model", JSONObject().put("providerId", providerId).put("modelId", modelId))
            .put("provider", provider.toProtocol())
    }
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
