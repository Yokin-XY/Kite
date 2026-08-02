package com.kite.app.agent.config.opencode

import com.kite.app.agent.config.AgentConfigCommandExecutionResult
import com.kite.app.agent.config.AgentConfigCommandExecutor
import java.util.UUID
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

/**
 * 读取 OpenCode 在完全无凭据环境中真实公布的模型目录。
 *
 * 隔离 HOME/XDG 目录和 OPENCODE_API_KEY 后仍被 `opencode models opencode` 公布的模型，
 * 才能被 Kite 认定为“无需登录直接可用”。不依赖模型名称或 `-free` 后缀。
 */
internal class OpenCodeModelCatalogReader(
    private val commandExecutor: AgentConfigCommandExecutor?,
    private val clockMillis: () -> Long = System::currentTimeMillis,
    private val isolatedHome: String = "/tmp/kite-opencode-public-${UUID.randomUUID().toString().replace("-", "")}",
) {
    private val readMutex = Mutex()

    @Volatile
    private var cache: CacheEntry? = null

    suspend fun read(): OpenCodeModelCatalogReadResult {
        val executor = commandExecutor ?: return OpenCodeModelCatalogReadResult.Unsupported
        cache?.takeIf { clockMillis() - it.readAtMillis < CACHE_TTL_MILLIS }?.let { cached ->
            return OpenCodeModelCatalogReadResult.Ready(cached.models)
        }
        return readMutex.withLock<OpenCodeModelCatalogReadResult> {
            cache?.takeIf { clockMillis() - it.readAtMillis < CACHE_TTL_MILLIS }?.let { cached ->
                return@withLock OpenCodeModelCatalogReadResult.Ready(cached.models)
            }
            val execution = runCatching { executor.execute(command(), DEFAULT_CWD) }
                .getOrElse { return@withLock fallback(READ_FAILED_MESSAGE) }
            when (execution) {
                is AgentConfigCommandExecutionResult.Failed -> fallback(execution.message)
                is AgentConfigCommandExecutionResult.Completed -> {
                    if (execution.exitCode != 0) {
                        fallback(READ_FAILED_MESSAGE)
                    } else {
                        when (val parsed = parseOpenCodeVerboseModelCatalog(execution.stdout)) {
                            is OpenCodeModelCatalogParseResult.Ready -> {
                                cache = CacheEntry(parsed.models, clockMillis())
                                OpenCodeModelCatalogReadResult.Ready(parsed.models)
                            }
                            OpenCodeModelCatalogParseResult.Malformed -> fallback(READ_FAILED_MESSAGE)
                        }
                    }
                }
            }
        }
    }

    fun cachedNativeValues(): Set<String> = cache?.models
        ?.mapTo(linkedSetOf(), OpenCodeCatalogModel::nativeValue)
        .orEmpty()

    private fun command(): List<String> = listOf(
        "env",
        "-u",
        OPEN_CODE_API_KEY,
        "HOME=$isolatedHome",
        "XDG_DATA_HOME=$isolatedHome/data",
        "XDG_CONFIG_HOME=$isolatedHome/config",
        "XDG_CACHE_HOME=$isolatedHome/cache",
        "opencode",
        "--pure",
        "models",
        OPEN_CODE_PROVIDER_ID,
        "--verbose",
    )

    private fun fallback(message: String): OpenCodeModelCatalogReadResult = cache?.let { cached ->
        OpenCodeModelCatalogReadResult.Ready(
            models = cached.models,
            warning = "OpenCode 免费模型目录暂未刷新",
        )
    } ?: OpenCodeModelCatalogReadResult.Failed(
        message.takeIf(String::isNotBlank) ?: READ_FAILED_MESSAGE,
    )

    private data class CacheEntry(
        val models: List<OpenCodeCatalogModel>,
        val readAtMillis: Long,
    )

    private companion object {
        const val DEFAULT_CWD = "/workspace"
        const val OPEN_CODE_API_KEY = "OPENCODE_API_KEY"
        const val CACHE_TTL_MILLIS = 5 * 60 * 1_000L
        const val READ_FAILED_MESSAGE = "无法读取 OpenCode 免费模型目录"
    }
}

internal data class OpenCodeCatalogModel(
    val nativeValue: String,
    val modelId: String,
    val displayName: String,
)

internal sealed interface OpenCodeModelCatalogReadResult {
    data class Ready(
        val models: List<OpenCodeCatalogModel>,
        val warning: String? = null,
    ) : OpenCodeModelCatalogReadResult

    data object Unsupported : OpenCodeModelCatalogReadResult

    data class Failed(val message: String) : OpenCodeModelCatalogReadResult
}

internal sealed interface OpenCodeModelCatalogParseResult {
    data class Ready(val models: List<OpenCodeCatalogModel>) : OpenCodeModelCatalogParseResult
    data object Malformed : OpenCodeModelCatalogParseResult
}

internal fun parseOpenCodeVerboseModelCatalog(
    lines: List<String>,
): OpenCodeModelCatalogParseResult {
    val models = linkedMapOf<String, OpenCodeCatalogModel>()
    var index = 0
    while (index < lines.size) {
        val nativeValue = lines[index].withoutAnsi().trim()
        if (!OPEN_CODE_NATIVE_MODEL.matches(nativeValue)) {
            index += 1
            continue
        }
        index += 1
        val document = StringBuilder()
        var modelJson: JSONObject? = null
        while (index < lines.size && modelJson == null) {
            val line = lines[index].withoutAnsi()
            if (OPEN_CODE_NATIVE_MODEL.matches(line.trim()) && document.isNotEmpty()) {
                return OpenCodeModelCatalogParseResult.Malformed
            }
            if (document.isNotEmpty()) document.append('\n')
            document.append(line)
            modelJson = runCatching { JSONObject(document.toString()) }.getOrNull()
            index += 1
        }
        val json = modelJson ?: return OpenCodeModelCatalogParseResult.Malformed
        val modelId = nativeValue.removePrefix("$OPEN_CODE_PROVIDER_ID/")
        if (json.optString("providerID") != OPEN_CODE_PROVIDER_ID || json.optString("id") != modelId) {
            return OpenCodeModelCatalogParseResult.Malformed
        }
        val displayName = json.optString("name")
            .replace(ISO_CONTROL, " ")
            .trim()
            .take(MAX_DISPLAY_NAME)
            .takeIf(String::isNotBlank)
            ?: modelId
        models.putIfAbsent(
            nativeValue,
            OpenCodeCatalogModel(nativeValue, modelId, displayName),
        )
    }
    return OpenCodeModelCatalogParseResult.Ready(models.values.toList())
}

private fun String.withoutAnsi(): String = ANSI_ESCAPE.replace(this, "")

private const val OPEN_CODE_PROVIDER_ID = "opencode"
private const val MAX_DISPLAY_NAME = 240
private val OPEN_CODE_NATIVE_MODEL = Regex("opencode/[^\\s\\p{Cc}]{1,383}")
private val ANSI_ESCAPE = Regex("\\u001B\\[[0-?]*[ -/]*[@-~]")
private val ISO_CONTROL = Regex("[\\p{Cc}\\p{Cf}]+")
