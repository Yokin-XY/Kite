package com.kite.app.agent.config.native

import android.content.Context
import blue.endless.jankson.Jankson
import blue.endless.jankson.JsonArray
import blue.endless.jankson.JsonObject
import blue.endless.jankson.JsonPrimitive
import com.kite.app.agent.config.AgentConfigCapabilities
import com.kite.app.agent.config.AgentCredentialOwnership
import com.kite.app.agent.config.AgentCredentialPresence
import com.kite.app.agent.config.AgentPersistentConfigCapability
import com.kite.app.agent.config.AgentPersistentConfigChange
import com.kite.app.agent.config.AgentProviderCredentialChange
import com.kite.app.agent.config.AgentProviderModelSummary
import com.kite.app.agent.config.AgentProviderSummary
import com.kite.app.agent.config.AgentSessionConfigurationEffect
import com.kite.app.agent.config.AtomicConfigFileStore
import com.kite.app.foundation.contracts.ContainerRecord
import com.kite.app.foundation.workspace.WorkSurfaceRuntimeBridge
import java.net.URI

/**
 * GitHub Copilot CLI 的 BYOK 原生入口是 COPILOT_PROVIDER_* 环境变量。
 *
 * Kite 目录保留多供应商；本适配器只把当前选中的一项物化为启动器读取的环境文件，
 * 不在 UI 或 ACP 层伪造 Copilot 自己不存在的配置文件协议。
 */
internal class CopilotAgentConfigAdapter internal constructor(
    context: Context,
    containerProvider: () -> ContainerRecord?,
    fileStore: AtomicConfigFileStore = AtomicConfigFileStore(),
) : NativeAgentConfigAdapter(
    context = context,
    adapterId = ADAPTER_ID,
    paths = linkedMapOf(
        METADATA_KEY to METADATA_PATH,
        BASE_URL_KEY to BASE_URL_PATH,
        API_KEY_KEY to API_KEY_PATH,
        PROVIDER_TYPE_KEY to PROVIDER_TYPE_PATH,
        MODEL_KEY to MODEL_PATH,
        CREDENTIAL_OWNER_KEY to CREDENTIAL_OWNER_PATH,
    ),
    primaryKey = METADATA_KEY,
    containerProvider = containerProvider,
    fileStore = fileStore,
) {
    constructor(context: Context) : this(
        context.applicationContext,
        { WorkSurfaceRuntimeBridge.getSavedContainer(context.applicationContext) },
    )

    private val parser = Jankson.builder().build()

    override fun displayName(): String = "GitHub Copilot CLI"

    override fun providerConfigurationEffect(): AgentSessionConfigurationEffect =
        AgentSessionConfigurationEffect.Reconnect

    override fun nativeCapabilities(): AgentConfigCapabilities = AgentConfigCapabilities(
        supported = setOf(
            AgentPersistentConfigCapability.DefaultModel,
            AgentPersistentConfigCapability.Provider,
            AgentPersistentConfigCapability.ProviderProfiles,
            AgentPersistentConfigCapability.CredentialStatus,
        ),
        credentialOwnership = AgentCredentialOwnership.AgentOwned,
    )

    override fun decode(files: Map<String, ByteArray>): NativeState {
        val baseUrl = text(files.getValue(BASE_URL_KEY))
        val selectedModel = text(files.getValue(MODEL_KEY))
        val metadata = metadata(files.getValue(METADATA_KEY))
        val providerId = metadata?.string(ID_FIELD)
            ?.takeIf(String::isNotBlank)
            ?: baseUrl?.let { LEGACY_PROVIDER_ID }
        val models = metadata?.array(MODELS_FIELD)
            ?.mapNotNull(::modelSummary)
            .orEmpty()
            .ifEmpty {
                selectedModel?.let { listOf(AgentProviderModelSummary(it, it)) }.orEmpty()
            }
        val credentialOwner = text(files.getValue(CREDENTIAL_OWNER_KEY))
        val credentialPresent = providerId != null && credentialOwner == providerId &&
            !text(files.getValue(API_KEY_KEY)).isNullOrBlank()
        val providers = if (providerId == null || baseUrl == null || models.isEmpty()) {
            emptyList()
        } else {
            listOf(
                AgentProviderSummary(
                    id = providerId,
                    displayName = metadata?.string(DISPLAY_NAME_FIELD)?.takeIf(String::isNotBlank) ?: providerId,
                    baseUrl = baseUrl,
                    models = models,
                    credentialPresence = if (credentialPresent) {
                        AgentCredentialPresence.Present
                    } else {
                        AgentCredentialPresence.Missing
                    },
                ),
            )
        }
        return NativeState(
            defaultModel = selectedModel,
            providers = providers,
            credentialPresence = overallCredential(providers),
            activeProviderId = providers.singleOrNull()?.id,
        )
    }

    override fun mutate(
        files: Map<String, ByteArray>,
        changes: List<AgentPersistentConfigChange>,
    ): Map<String, ByteArray> {
        val next = files.toMutableMap()
        changes.forEach { change ->
            when (change) {
                is AgentPersistentConfigChange.SetDefaultModel ->
                    next[MODEL_KEY] = line(change.modelId)
                is AgentPersistentConfigChange.SelectProvider ->
                    next[MODEL_KEY] = line(change.modelId)
                is AgentPersistentConfigChange.ConfigureProvider -> {
                    val provider = change.provider
                    next[METADATA_KEY] = serializeMetadata(provider.id, provider.displayName, provider.models)
                    next[BASE_URL_KEY] = line(provider.baseUrl.trim())
                    next[PROVIDER_TYPE_KEY] = line(OPENAI_PROVIDER_TYPE)
                    next[MODEL_KEY] = line(provider.models.first().id.trim())
                    val currentOwner = text(next.getValue(CREDENTIAL_OWNER_KEY))
                    when (val credential = change.credential) {
                        AgentProviderCredentialChange.Keep -> if (currentOwner != provider.id) {
                            next[API_KEY_KEY] = ByteArray(0)
                            next[CREDENTIAL_OWNER_KEY] = ByteArray(0)
                        }
                        is AgentProviderCredentialChange.Replace -> {
                            next[API_KEY_KEY] = line(credential.secret)
                            next[CREDENTIAL_OWNER_KEY] = line(provider.id)
                        }
                        AgentProviderCredentialChange.Remove -> {
                            next[API_KEY_KEY] = ByteArray(0)
                            next[CREDENTIAL_OWNER_KEY] = ByteArray(0)
                        }
                    }
                }
                is AgentPersistentConfigChange.RemoveProvider -> {
                    val currentId = metadata(next.getValue(METADATA_KEY))?.string(ID_FIELD)
                        ?: text(next.getValue(BASE_URL_KEY))?.let { LEGACY_PROVIDER_ID }
                    if (currentId == change.providerId) {
                        next[METADATA_KEY] = ByteArray(0)
                        next[BASE_URL_KEY] = ByteArray(0)
                        next[PROVIDER_TYPE_KEY] = ByteArray(0)
                        next[MODEL_KEY] = ByteArray(0)
                        if (change.removeCredential) {
                            next[API_KEY_KEY] = ByteArray(0)
                            next[CREDENTIAL_OWNER_KEY] = ByteArray(0)
                        }
                    }
                }
                else -> Unit
            }
        }
        return next
    }

    override fun validateBytes(key: String, bytes: ByteArray): String? = runCatching {
        when (key) {
            METADATA_KEY -> if (bytes.isNotEmpty() && bytes.toString(Charsets.UTF_8).isNotBlank()) {
                val root = requireNotNull(metadata(bytes))
                require(isSafeNativeId(requireNotNull(root.string(ID_FIELD))))
                require(root.array(MODELS_FIELD).orEmpty().mapNotNull(::modelSummary).isNotEmpty())
            }
            BASE_URL_KEY -> text(bytes)?.let { value ->
                val uri = URI(value)
                require(uri.scheme in setOf("http", "https") && !uri.host.isNullOrBlank() && uri.userInfo == null)
            }
            PROVIDER_TYPE_KEY -> require(text(bytes) == null || text(bytes) == OPENAI_PROVIDER_TYPE)
            MODEL_KEY -> require(text(bytes)?.none(Char::isISOControl) != false)
            CREDENTIAL_OWNER_KEY -> text(bytes)?.let { require(isSafeNativeId(it)) }
            API_KEY_KEY -> require(bytes.size <= MAX_SECRET_BYTES)
        }
        null
    }.getOrElse { "GitHub Copilot CLI 供应商环境配置格式无效" }

    private fun metadata(bytes: ByteArray): JsonObject? {
        val value = bytes.toString(Charsets.UTF_8).trim()
        if (value.isBlank()) return null
        return parser.load(value)
    }

    private fun serializeMetadata(
        id: String,
        displayName: String?,
        models: List<AgentProviderModelSummary>,
    ): ByteArray {
        val root = JsonObject()
        root[ID_FIELD] = JsonPrimitive.of(id)
        root[DISPLAY_NAME_FIELD] = JsonPrimitive.of(displayName?.takeIf(String::isNotBlank) ?: id)
        root[MODELS_FIELD] = JsonArray().apply {
            models.forEach { model ->
                add(JsonObject().apply {
                    this[ID_FIELD] = JsonPrimitive.of(model.id.trim())
                    this[DISPLAY_NAME_FIELD] = JsonPrimitive.of(
                        model.displayName.takeIf(String::isNotBlank) ?: model.id,
                    )
                })
            }
        }
        return (root.toJson() + "\n").toByteArray(Charsets.UTF_8)
    }

    private fun modelSummary(value: Any?): AgentProviderModelSummary? {
        val model = value as? JsonObject ?: return null
        val id = model.string(ID_FIELD)?.takeIf(String::isNotBlank) ?: return null
        return AgentProviderModelSummary(
            id = id,
            displayName = model.string(DISPLAY_NAME_FIELD)?.takeIf(String::isNotBlank) ?: id,
        )
    }

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.getValue() as? String

    private fun JsonObject.array(key: String): JsonArray? = this[key] as? JsonArray

    private fun text(bytes: ByteArray): String? = bytes.toString(Charsets.UTF_8).trim().takeIf(String::isNotBlank)

    private fun line(value: String?): ByteArray = value
        ?.takeIf(String::isNotBlank)
        ?.let { "$it\n".toByteArray(Charsets.UTF_8) }
        ?: ByteArray(0)

    companion object {
        const val ADAPTER_ID = "github-copilot"
        private const val METADATA_KEY = "metadata"
        private const val BASE_URL_KEY = "base-url"
        private const val API_KEY_KEY = "api-key"
        private const val PROVIDER_TYPE_KEY = "provider-type"
        private const val MODEL_KEY = "model"
        private const val CREDENTIAL_OWNER_KEY = "credential-owner"
        private const val SOFTWARE_HOME = "/workspace/.kf/software/kite.github.copilot/user-home"
        private const val SECRET_HOME = "/workspace/.kf/secrets"
        private const val METADATA_PATH = "$SOFTWARE_HOME/provider.json"
        private const val BASE_URL_PATH = "$SECRET_HOME/kite.copilot-provider-base-url"
        private const val API_KEY_PATH = "$SECRET_HOME/kite.copilot-provider-api-key"
        private const val PROVIDER_TYPE_PATH = "$SECRET_HOME/kite.copilot-provider-type"
        private const val MODEL_PATH = "$SECRET_HOME/kite.copilot-model"
        private const val CREDENTIAL_OWNER_PATH = "$SECRET_HOME/kite.copilot-provider-key-owner"
        private const val ID_FIELD = "id"
        private const val DISPLAY_NAME_FIELD = "displayName"
        private const val MODELS_FIELD = "models"
        private const val OPENAI_PROVIDER_TYPE = "openai"
        private const val LEGACY_PROVIDER_ID = "custom"
        private const val MAX_SECRET_BYTES = 16 * 1024
    }
}
