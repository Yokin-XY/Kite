package com.kite.app.agent.config.native

import android.content.Context
import blue.endless.jankson.JsonArray
import blue.endless.jankson.JsonObject
import blue.endless.jankson.JsonPrimitive
import com.kite.app.agent.config.AgentConfigCapabilities
import com.kite.app.agent.config.AgentCredentialOwnership
import com.kite.app.agent.config.AgentCredentialPresence
import com.kite.app.agent.config.AgentPersistentConfigCapability
import com.kite.app.agent.config.AgentPersistentConfigChange
import com.kite.app.agent.config.AgentProviderCredentialChange
import com.kite.app.agent.config.AgentProviderDraft
import com.kite.app.agent.config.AgentProviderModelSummary
import com.kite.app.agent.config.AgentProviderSummary
import com.kite.app.agent.config.AgentSessionConfigurationEffect
import com.kite.app.agent.config.AtomicConfigFileStore
import com.kite.app.agent.contract.AgentConfigOption
import com.kite.app.agent.contract.AgentModelSource
import com.kite.app.agent.zcode.ZCodeModel
import com.kite.app.agent.zcode.ZCodeRuntimeModelCatalog
import com.kite.app.agent.zcode.ZCodeRuntimeModelProvider
import com.kite.app.foundation.contracts.ContainerRecord
import com.kite.app.foundation.workspace.WorkSurfaceRuntimeBridge
import java.security.MessageDigest

/**
 * ZCode 独立 CLI 的原生 model 配置适配器。
 *
 * Kite 负责持久化用户选择，ZCode app-server 仍通过官方 runtimeModel/workspace Provider
 * 协议消费同一份配置；页面和会话层不识别 ZCode 的私有字段。
 */
internal class ZCodeAgentConfigAdapter internal constructor(
    context: Context,
    containerProvider: () -> ContainerRecord?,
    private val configFileStore: AtomicConfigFileStore = AtomicConfigFileStore(),
) : JanksonNativeAgentConfigAdapter(
    context = context,
    adapterId = ADAPTER_ID,
    paths = linkedMapOf(
        CONFIG_KEY to CONFIG_PATH,
        NATIVE_CONFIG_KEY to NATIVE_CONFIG_PATH,
    ),
    primaryKey = CONFIG_KEY,
    containerProvider = containerProvider,
    fileStore = configFileStore,
) {
    constructor(context: Context) : this(
        context.applicationContext,
        { WorkSurfaceRuntimeBridge.getSavedContainer(context.applicationContext) },
    )

    override fun displayName(): String = "ZCode"

    override fun nativeCapabilities(): AgentConfigCapabilities = AgentConfigCapabilities(
        supported = setOf(
            AgentPersistentConfigCapability.DefaultModel,
            AgentPersistentConfigCapability.Provider,
            AgentPersistentConfigCapability.ProviderProfiles,
            AgentPersistentConfigCapability.CredentialStatus,
        ),
        credentialOwnership = AgentCredentialOwnership.AgentOwned,
    )

    override fun providerConfigurationEffect(): AgentSessionConfigurationEffect =
        AgentSessionConfigurationEffect.Reconnect

    override fun defaultModelChange(
        option: AgentConfigOption.Select,
    ): AgentPersistentConfigChange.SetDefaultModel? {
        val change = super.defaultModelChange(option) ?: return null
        val selected = option.choices.firstOrNull { it.value == option.currentValue } ?: return null
        if (selected.modelSource != AgentModelSource.OfficialLogin) return change
        return change.copy(clearProviderOverride = true)
    }

    override fun decode(files: Map<String, ByteArray>): NativeState {
        val root = parse(files.getValue(CONFIG_KEY))
        val configured = configuredProviders(root)
        val providers = configured.map { provider ->
            AgentProviderSummary(
                id = provider.id,
                displayName = provider.name,
                baseUrl = provider.baseUrl,
                models = provider.models,
                credentialPresence = if (!provider.apiKey.isNullOrBlank()) {
                    AgentCredentialPresence.Present
                } else {
                    AgentCredentialPresence.Missing
                },
            )
        }.sortedBy(AgentProviderSummary::id)
        val main = selectedModelRef(root)
        val activeProvider = main?.providerId?.takeIf { id -> providers.any { it.id == id } }
        return NativeState(
            defaultModel = main?.let { providerModelRef(it.providerId, it.modelId) },
            providers = providers,
            credentialPresence = overallCredential(providers),
            activeProviderId = activeProvider,
            runtimeReloadRequired = true,
        )
    }

    override fun mutate(
        files: Map<String, ByteArray>,
        changes: List<AgentPersistentConfigChange>,
    ): Map<String, ByteArray> {
        val root = parse(files.getValue(CONFIG_KEY)).clone()
        migrateLegacyConfig(root)
        changes.forEach { change ->
            when (change) {
                is AgentPersistentConfigChange.SetDefaultModel -> {
                    if (change.clearProviderOverride) {
                        clearMain(root)
                        return@forEach
                    }
                    val ref = change.modelId ?: return@forEach clearMain(root)
                    val providerId = ref.substringBefore('/').takeIf { it != ref }
                    val modelId = ref.substringAfter('/', ref)
                    val target = configuredProviders(root).firstNotNullOfOrNull { provider ->
                        provider.models.firstOrNull { model ->
                            model.id == modelId && (providerId == null || provider.id == providerId)
                        }?.let { ModelRef(provider.id, it.id) }
                    }
                    if (target != null) setMain(root, target)
                }
                is AgentPersistentConfigChange.SelectProvider -> {
                    val provider = configuredProviders(root).firstOrNull { it.id == change.providerId }
                    if (provider?.models?.none { it.id == change.modelId } != false) {
                        error("ZCode 原生配置中没有该供应商模型")
                    }
                    setMain(root, ModelRef(change.providerId, change.modelId))
                }
                is AgentPersistentConfigChange.ConfigureProvider ->
                    configureProvider(root, change.provider, change.credential)
                is AgentPersistentConfigChange.RemoveProvider -> removeProvider(root, change.providerId)
                else -> Unit
            }
        }
        return mapOf(CONFIG_KEY to serialize(root))
    }

    /** 只在 Agent 运行通道中读取；返回对象不会把 API Key 写入日志或界面投影。 */
    fun runtimeModelCatalog(): ZCodeRuntimeModelCatalog? {
        val target = projection.resolve(CONFIG_PATH) ?: return null
        val nativeTarget = projection.resolve(NATIVE_CONFIG_PATH) ?: return null
        val bytes = configFileStore.read(target.readFile).bytes
        val nativeBytes = configFileStore.read(nativeTarget.readFile).bytes
        val root = parse(bytes)
        val configured = configuredProviders(root)
        val providers = configured.map { provider ->
            ZCodeRuntimeModelProvider(
                providerId = provider.id,
                label = provider.name,
                kind = provider.kind,
                apiFormat = inferApiFormat(provider.kind, provider.baseUrl),
                baseUrl = provider.baseUrl,
                apiKey = provider.apiKey,
                models = provider.models,
            )
        }
        val main = selectedModelRef(root)
        val officialModels = officialModels(parse(nativeBytes)).ifEmpty {
            ZCODE_3_10_1_OFFICIAL_MODELS
        }
        if (providers.isEmpty() && officialModels.isEmpty()) return null
        val digest = MessageDigest.getInstance("SHA-256").also { sha ->
            sha.update(bytes)
            sha.update(nativeBytes)
            if (nativeBytes.isEmpty()) sha.update(ZCODE_OFFICIAL_FALLBACK_REVISION.toByteArray())
        }
        return ZCodeRuntimeModelCatalog(
            revision = "sha256:" + digest.digest()
                .joinToString("") { byte -> "%02x".format(byte) },
            generatedAt = System.currentTimeMillis(),
            providers = providers,
            selectedProviderId = main?.providerId,
            selectedModelId = main?.modelId,
            advertisedModels = officialModels,
        )
    }

    /**
     * ZCode 的 session 快照只保证返回当前模型，不保证返回完整 available。官方模型目录实际
     * 保存在 v2/config.json 的 provider 表中，因此这里读取稳定 ID 和显示信息，不读取凭据。
     */
    private fun officialModels(root: JsonObject): List<ZCodeModel> =
        root.getObject(NATIVE_PROVIDER_KEY)?.entries.orEmpty().flatMap { (providerId, value) ->
            if (providerId !in OFFICIAL_LOGIN_PROVIDER_IDS) return@flatMap emptyList()
            val provider = value as? JsonObject ?: return@flatMap emptyList()
            val providerName = provider.string(NATIVE_PROVIDER_NAME_KEY)?.takeIf(String::isNotBlank)
                ?: providerId
            provider.getObject(NATIVE_MODELS_KEY)?.entries.orEmpty().map { (modelId, modelValue) ->
                val model = modelValue as? JsonObject
                ZCodeModel(
                    providerId = providerId,
                    modelId = modelId,
                    displayName = model?.string(NATIVE_MODEL_LABEL_KEY)?.takeIf(String::isNotBlank)
                        ?: modelId,
                    providerName = providerName,
                    modelSource = AgentModelSource.OfficialLogin,
                )
            }
        }.distinctBy(ZCodeModel::selectionId)

    private fun configureProvider(
        root: JsonObject,
        provider: AgentProviderDraft,
        credential: AgentProviderCredentialChange,
    ) {
        val providers = root.objectCopy(PROVIDERS_SECTION_KEY)
        val existing = providers.getObject(provider.id)?.clone() ?: JsonObject()
        val options = existing.objectCopy(OPTIONS_KEY)
        val existingKey = options.string(API_KEY_KEY)
        val apiKey = when (credential) {
            AgentProviderCredentialChange.Keep -> existingKey
            is AgentProviderCredentialChange.Replace -> credential.secret
            AgentProviderCredentialChange.Remove -> null
        }
        val kind = inferProviderKind(provider.baseUrl)
        putPreserving(existing, KIND_KEY, JsonPrimitive.of(kind))
        provider.displayName?.trim()?.takeIf(String::isNotBlank)?.let { name ->
            putPreserving(existing, NAME_KEY, JsonPrimitive.of(name))
        }
        putPreserving(options, BASE_URL_KEY, JsonPrimitive.of(provider.baseUrl.trim()))
        if (apiKey.isNullOrBlank()) options.remove(API_KEY_KEY)
        else putPreserving(options, API_KEY_KEY, JsonPrimitive.of(apiKey))
        putPreserving(existing, OPTIONS_KEY, options)

        val previousModels = existing.getObject(MODELS_KEY)
        val models = JsonObject()
        provider.models.forEach { model ->
            val nativeModel = previousModels?.getObject(model.id)?.clone() ?: JsonObject()
            model.displayName.takeIf { it.isNotBlank() && it != model.id }?.let { displayName ->
                putPreserving(nativeModel, NAME_KEY, JsonPrimitive.of(displayName))
            }
            putPreserving(models, model.id.trim(), nativeModel)
        }
        putPreserving(existing, MODELS_KEY, models)
        putPreserving(providers, provider.id, existing)
        putPreserving(root, PROVIDERS_SECTION_KEY, providers)

        val currentMain = selectedModelRef(root)
        val selectedModelId = currentMain
            ?.takeIf { it.providerId == provider.id && provider.models.any { model -> model.id == it.modelId } }
            ?.modelId
            ?: provider.models.first().id
        if (currentMain == null || currentMain.providerId == provider.id) {
            setMain(root, ModelRef(provider.id, selectedModelId))
        }
    }

    private fun removeProvider(root: JsonObject, providerId: String) {
        val selected = selectedModelRef(root)
        val providers = root.objectCopy(PROVIDERS_SECTION_KEY)
        providers.remove(providerId)
        if (providers.isEmpty()) root.remove(PROVIDERS_SECTION_KEY)
        else putPreserving(root, PROVIDERS_SECTION_KEY, providers)
        if (selected?.providerId == providerId) {
            val next = configuredProviders(root).firstNotNullOfOrNull { provider ->
                provider.models.firstOrNull()?.let { model -> ModelRef(provider.id, model.id) }
            }
            if (next == null) clearMain(root) else setMain(root, next)
        }
    }

    private fun setMain(root: JsonObject, selected: ModelRef) {
        val current = root[MODEL_SECTION_KEY]
        val model = (current as? JsonObject)?.clone() ?: JsonObject()
        putPreserving(model, MAIN_KEY, JsonPrimitive.of(providerModelRef(selected.providerId, selected.modelId)))
        model.remove(AVAILABLE_KEY)
        if (model[LITE_KEY] !is JsonPrimitive) model.remove(LITE_KEY)
        putPreserving(root, MODEL_SECTION_KEY, model)
    }

    private fun clearMain(root: JsonObject) {
        val model = root[MODEL_SECTION_KEY] as? JsonObject
        if (model == null) {
            root.remove(MODEL_SECTION_KEY)
            return
        }
        model.remove(MAIN_KEY)
        model.remove(AVAILABLE_KEY)
        if (model[LITE_KEY] !is JsonPrimitive) model.remove(LITE_KEY)
        if (model.isEmpty()) root.remove(MODEL_SECTION_KEY) else putPreserving(root, MODEL_SECTION_KEY, model)
    }

    private fun configuredProviders(root: JsonObject): List<ConfiguredProvider> {
        val native = root.getObject(PROVIDERS_SECTION_KEY)?.entries.orEmpty().mapNotNull { (id, value) ->
            if (id.startsWith(BUILTIN_PROVIDER_PREFIX)) return@mapNotNull null
            val document = value as? JsonObject ?: return@mapNotNull null
            val options = document.getObject(OPTIONS_KEY) ?: JsonObject()
            val baseUrl = options.string(BASE_URL_KEY) ?: document.string(BASE_URL_KEY)
            val models = document.getObject(MODELS_KEY)?.entries.orEmpty().mapNotNull { (modelKey, modelValue) ->
                val model = modelValue as? JsonObject ?: JsonObject()
                val modelId = model.string(ID_KEY)?.takeIf(String::isNotBlank) ?: modelKey
                AgentProviderModelSummary(
                    id = modelId,
                    displayName = model.string(NAME_KEY)?.takeIf(String::isNotBlank) ?: modelId,
                )
            }.distinctBy(AgentProviderModelSummary::id)
            if (models.isEmpty()) return@mapNotNull null
            ConfiguredProvider(
                id = id,
                name = document.string(NAME_KEY)?.takeIf(String::isNotBlank) ?: id,
                kind = document.string(KIND_KEY) ?: inferProviderKind(baseUrl),
                baseUrl = baseUrl,
                apiKey = options.string(API_KEY_KEY),
                models = models,
            )
        }
        if (native.isNotEmpty()) return native.sortedBy(ConfiguredProvider::id)
        return legacyTargets(root).groupBy(LegacyTarget::providerId).map { (providerId, targets) ->
            val first = targets.first()
            ConfiguredProvider(
                id = providerId,
                name = first.providerName ?: providerId,
                kind = first.kind ?: inferProviderKind(first.baseUrl),
                baseUrl = first.baseUrl,
                apiKey = first.apiKey,
                models = targets.distinctBy(LegacyTarget::modelId).map { target ->
                    AgentProviderModelSummary(target.modelId, target.modelId)
                },
            )
        }.sortedBy(ConfiguredProvider::id)
    }

    private fun selectedModelRef(root: JsonObject): ModelRef? {
        val model = root[MODEL_SECTION_KEY]
        val raw = when (model) {
            is JsonPrimitive -> model.getValue() as? String
            is JsonObject -> model.string(MAIN_KEY)
            else -> null
        }
        parseModelRef(raw)?.let { return it }
        return (model as? JsonObject)?.getObject(MAIN_KEY)?.let { legacy ->
            val providerId = legacy.string(LEGACY_PROVIDER_ID_KEY) ?: return@let null
            val modelId = legacy.string(LEGACY_MODEL_ID_KEY) ?: return@let null
            ModelRef(providerId, modelId)
        }
    }

    private fun parseModelRef(value: String?): ModelRef? {
        val raw = value?.trim().orEmpty()
        val separator = raw.indexOf('/')
        if (separator <= 0 || separator == raw.lastIndex) return null
        return ModelRef(raw.substring(0, separator), raw.substring(separator + 1))
    }

    private fun migrateLegacyConfig(root: JsonObject) {
        val legacy = legacyTargets(root)
        if (root.getObject(PROVIDERS_SECTION_KEY).isNullOrEmpty() && legacy.isNotEmpty()) {
            val providers = JsonObject()
            legacy.groupBy(LegacyTarget::providerId).forEach { (providerId, targets) ->
                val first = targets.first()
                val provider = JsonObject()
                putPreserving(provider, KIND_KEY, JsonPrimitive.of(first.kind ?: inferProviderKind(first.baseUrl)))
                first.providerName?.let { putPreserving(provider, NAME_KEY, JsonPrimitive.of(it)) }
                val options = JsonObject()
                first.baseUrl?.let { putPreserving(options, BASE_URL_KEY, JsonPrimitive.of(it)) }
                first.apiKey?.let { putPreserving(options, API_KEY_KEY, JsonPrimitive.of(it)) }
                putPreserving(provider, OPTIONS_KEY, options)
                val models = JsonObject()
                targets.distinctBy(LegacyTarget::modelId).forEach { target ->
                    putPreserving(models, target.modelId, JsonObject())
                }
                putPreserving(provider, MODELS_KEY, models)
                putPreserving(providers, providerId, provider)
            }
            putPreserving(root, PROVIDERS_SECTION_KEY, providers)
        }
        selectedModelRef(root)?.let { setMain(root, it) }
    }

    private fun legacyTargets(root: JsonObject): List<LegacyTarget> {
        val model = root.getObject(MODEL_SECTION_KEY) ?: return emptyList()
        return buildList {
            model.getObject(MAIN_KEY)?.toLegacyTarget(primary = true)?.let(::add)
            model.getObject(LITE_KEY)?.toLegacyTarget(primary = false)?.let(::add)
            (model[AVAILABLE_KEY] as? JsonArray).orEmpty().forEach { value ->
                (value as? JsonObject)?.toLegacyTarget(primary = false)?.let(::add)
            }
        }.distinctBy { "${it.providerId}/${it.modelId}" }
    }

    private fun JsonObject.toLegacyTarget(primary: Boolean): LegacyTarget? {
        val providerId = string(LEGACY_PROVIDER_ID_KEY)?.takeIf(String::isNotBlank) ?: return null
        val modelId = string(LEGACY_MODEL_ID_KEY)?.takeIf(String::isNotBlank) ?: return null
        return LegacyTarget(
            providerId = providerId,
            providerName = string(LEGACY_PROVIDER_NAME_KEY),
            modelId = modelId,
            kind = string(KIND_KEY),
            baseUrl = string(BASE_URL_KEY),
            apiKey = string(API_KEY_KEY),
            primary = primary,
        )
    }

    private fun inferProviderKind(baseUrl: String?): String =
        if (baseUrl.orEmpty().contains("/anthropic", ignoreCase = true)) ANTHROPIC_KIND else OPENAI_COMPATIBLE_KIND

    private fun inferApiFormat(kind: String?, baseUrl: String?): String =
        if (kind == ANTHROPIC_KIND || baseUrl.orEmpty().contains("/anthropic", ignoreCase = true)) {
            ANTHROPIC_MESSAGES_FORMAT
        } else {
            OPENAI_CHAT_FORMAT
        }

    private data class ConfiguredProvider(
        val id: String,
        val name: String,
        val kind: String,
        val baseUrl: String?,
        val apiKey: String?,
        val models: List<AgentProviderModelSummary>,
    )

    private data class ModelRef(val providerId: String, val modelId: String)

    private data class LegacyTarget(
        val providerId: String,
        val providerName: String?,
        val modelId: String,
        val kind: String?,
        val baseUrl: String?,
        val apiKey: String?,
        val primary: Boolean,
    )

    companion object {
        const val ADAPTER_ID = "zcode"
        private const val CONFIG_KEY = "config"
        private const val NATIVE_CONFIG_KEY = "native-v2-config"
        private const val CONFIG_PATH =
            "/workspace/.kf/software/kite.zcode/user-home/.zcode/cli/config.json"
        private const val NATIVE_CONFIG_PATH =
            "/workspace/.kf/software/kite.zcode/user-home/.zcode/v2/config.json"
        private const val MODEL_SECTION_KEY = "model"
        private const val MAIN_KEY = "main"
        private const val LITE_KEY = "lite"
        private const val AVAILABLE_KEY = "available"
        private const val PROVIDERS_SECTION_KEY = "provider"
        private const val OPTIONS_KEY = "options"
        private const val MODELS_KEY = "models"
        private const val NAME_KEY = "name"
        private const val ID_KEY = "id"
        private const val LEGACY_PROVIDER_ID_KEY = "provider"
        private const val LEGACY_PROVIDER_NAME_KEY = "providerName"
        private const val LEGACY_MODEL_ID_KEY = "model"
        private const val KIND_KEY = "kind"
        private const val BASE_URL_KEY = "baseURL"
        private const val API_KEY_KEY = "apiKey"
        private const val BUILTIN_PROVIDER_PREFIX = "builtin:"
        private const val ANTHROPIC_KIND = "anthropic"
        private const val OPENAI_COMPATIBLE_KIND = "openai-compatible"
        private const val ANTHROPIC_MESSAGES_FORMAT = "anthropic-messages"
        private const val OPENAI_CHAT_FORMAT = "openai-chat-completions"
        private const val NATIVE_PROVIDER_KEY = "provider"
        private const val NATIVE_PROVIDER_NAME_KEY = "name"
        private const val NATIVE_MODELS_KEY = "models"
        private const val NATIVE_MODEL_LABEL_KEY = "label"
        private const val ZCODE_OFFICIAL_FALLBACK_REVISION = "zcode-3.10.1-official-login-models-v1"

        private val OFFICIAL_LOGIN_PROVIDER_IDS = setOf(
            "builtin:bigmodel-coding-plan",
            "builtin:bigmodel-start-plan",
            "builtin:zai-coding-plan",
            "builtin:zai-start-plan",
        )

        /** 手机尚未登录时 v2/config.json 可能不存在；该清单只对应资源卡固定的 ZCode 3.10.1。 */
        private val ZCODE_3_10_1_OFFICIAL_MODELS = listOf(
            officialModel("builtin:bigmodel-coding-plan", "BigModel - Coding Plan", "GLM-5.3"),
            officialModel("builtin:bigmodel-coding-plan", "BigModel - Coding Plan", "GLM-5.3-Flash"),
            officialModel("builtin:bigmodel-coding-plan", "BigModel - Coding Plan", "GLM-5-Turbo"),
            officialModel("builtin:bigmodel-start-plan", "BigModel - Start Plan", "GLM-5.3-Flash"),
            officialModel("builtin:zai-coding-plan", "Z.AI - Coding Plan", "GLM-5.3"),
            officialModel("builtin:zai-coding-plan", "Z.AI - Coding Plan", "GLM-5.3-Flash"),
            officialModel("builtin:zai-coding-plan", "Z.AI - Coding Plan", "GLM-5-Turbo"),
            officialModel("builtin:zai-start-plan", "Z.AI - Start Plan", "GLM-5.2"),
            officialModel("builtin:zai-start-plan", "Z.AI - Start Plan", "GLM-5-Turbo"),
        )

        private fun officialModel(providerId: String, providerName: String, modelId: String) = ZCodeModel(
            providerId = providerId,
            modelId = modelId,
            displayName = modelId,
            providerName = providerName,
            modelSource = AgentModelSource.OfficialLogin,
        )
    }
}
