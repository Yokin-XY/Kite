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
        val targets = modelTargets(parse(files.getValue(CONFIG_KEY)))
        val userTargets = targets.filterNot { it.providerId.startsWith(BUILTIN_PROVIDER_PREFIX) }
        val providers = userTargets.groupBy(ModelTarget::providerId).map { (providerId, entries) ->
            val first = entries.first()
            AgentProviderSummary(
                id = providerId,
                displayName = first.providerName ?: providerId,
                baseUrl = first.baseUrl,
                models = entries.distinctBy(ModelTarget::modelId).map { target ->
                    AgentProviderModelSummary(target.modelId, target.modelId)
                },
                credentialPresence = if (entries.any { !it.apiKey.isNullOrBlank() }) {
                    AgentCredentialPresence.Present
                } else {
                    AgentCredentialPresence.Missing
                },
            )
        }.sortedBy(AgentProviderSummary::id)
        val main = targets.firstOrNull { it.primary }
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
                    val target = modelTargets(root).firstOrNull { candidate ->
                        candidate.modelId == modelId && (providerId == null || candidate.providerId == providerId)
                    }
                    if (target != null) selectTarget(root, target.document)
                }
                is AgentPersistentConfigChange.SelectProvider -> {
                    val target = modelTargets(root).firstOrNull { candidate ->
                        candidate.providerId == change.providerId && candidate.modelId == change.modelId
                    } ?: error("ZCode 原生配置中没有该供应商模型")
                    selectTarget(root, target.document)
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
        val targets = modelTargets(parse(bytes))
            .filterNot { it.providerId.startsWith(BUILTIN_PROVIDER_PREFIX) }
        val providers = targets.groupBy(ModelTarget::providerId).map { (providerId, entries) ->
            val first = entries.first()
            ZCodeRuntimeModelProvider(
                providerId = providerId,
                label = first.providerName ?: providerId,
                kind = first.kind ?: inferProviderKind(first.baseUrl),
                apiFormat = inferApiFormat(first.kind, first.baseUrl),
                baseUrl = first.baseUrl,
                apiKey = first.apiKey,
                models = entries.distinctBy(ModelTarget::modelId).map { entry ->
                    AgentProviderModelSummary(entry.modelId, entry.modelId)
                },
            )
        }
        val main = targets.firstOrNull(ModelTarget::primary)
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
        val before = modelTargets(root)
        val owned = before.filter { it.providerId == provider.id }
        val existingKey = owned.firstNotNullOfOrNull(ModelTarget::apiKey)
        val apiKey = when (credential) {
            AgentProviderCredentialChange.Keep -> existingKey
            is AgentProviderCredentialChange.Replace -> credential.secret
            AgentProviderCredentialChange.Remove -> null
        }
        val kind = inferProviderKind(provider.baseUrl)
        val replacements = provider.models.map { model ->
            modelTarget(
                providerId = provider.id,
                providerName = provider.displayName,
                modelId = model.id,
                kind = kind,
                baseUrl = provider.baseUrl,
                apiKey = apiKey,
            )
        }
        val currentMain = before.firstOrNull(ModelTarget::primary)
        val selectedModelId = currentMain
            ?.takeIf { it.providerId == provider.id && provider.models.any { model -> model.id == it.modelId } }
            ?.modelId
            ?: provider.models.first().id
        val selected = requireNotNull(replacements.firstOrNull { it.string(MODEL_ID_KEY) == selectedModelId })
        val preserved = before.filterNot { it.providerId == provider.id }.map(ModelTarget::document)
        writeTargets(root, selected, preserved + replacements.filterNot { it === selected })
    }

    private fun removeProvider(root: JsonObject, providerId: String) {
        val remaining = modelTargets(root).filterNot { it.providerId == providerId }
        val main = remaining.firstOrNull(ModelTarget::primary) ?: remaining.firstOrNull()
        if (main == null) {
            root.remove(MODEL_SECTION_KEY)
        } else {
            writeTargets(root, main.document, remaining.filterNot { it === main }.map(ModelTarget::document))
        }
    }

    private fun selectTarget(root: JsonObject, selected: JsonObject) {
        val targets = modelTargets(root)
        val previousMain = targets.firstOrNull(ModelTarget::primary)?.document
        val selectedRef = targetRef(selected)
        val remaining = buildList {
            targets.filterNot { targetRef(it.document) == selectedRef }.forEach { add(it.document) }
            previousMain?.takeIf { targetRef(it) != selectedRef }?.let(::add)
        }.distinctBy(::targetRef)
        writeTargets(root, selected, remaining)
    }

    private fun clearMain(root: JsonObject) {
        val model = root.objectCopy(MODEL_SECTION_KEY)
        val previousMain = model.getObject(MAIN_KEY)?.clone()
        model.remove(MAIN_KEY)
        previousMain?.let { main ->
            val available = buildList {
                (model[AVAILABLE_KEY] as? JsonArray).orEmpty().forEach { value ->
                    (value as? JsonObject)?.let(::add)
                }
                add(main)
            }.distinctBy(::targetRef)
            val array = JsonArray()
            available.forEach { array.add(it.clone()) }
            putPreserving(model, AVAILABLE_KEY, array)
        }
        if (model.isEmpty()) root.remove(MODEL_SECTION_KEY) else putPreserving(root, MODEL_SECTION_KEY, model)
    }

    private fun writeTargets(root: JsonObject, main: JsonObject, available: List<JsonObject>) {
        val model = root.objectCopy(MODEL_SECTION_KEY)
        putPreserving(model, MAIN_KEY, main.clone())
        val array = JsonArray()
        available.distinctBy(::targetRef).forEach { array.add(it.clone()) }
        if (array.isEmpty()) model.remove(AVAILABLE_KEY) else putPreserving(model, AVAILABLE_KEY, array)
        putPreserving(root, MODEL_SECTION_KEY, model)
    }

    private fun modelTargets(root: JsonObject): List<ModelTarget> {
        val model = root.getObject(MODEL_SECTION_KEY) ?: return emptyList()
        return buildList {
            model.getObject(MAIN_KEY)?.toModelTarget(primary = true)?.let(::add)
            model.getObject(LITE_KEY)?.toModelTarget(primary = false)?.let(::add)
            (model[AVAILABLE_KEY] as? JsonArray).orEmpty().forEach { value ->
                (value as? JsonObject)?.toModelTarget(primary = false)?.let(::add)
            }
        }.distinctBy { "${it.providerId}/${it.modelId}" }
    }

    private fun JsonObject.toModelTarget(primary: Boolean): ModelTarget? {
        val providerId = string(PROVIDER_ID_KEY)?.takeIf(String::isNotBlank) ?: return null
        val modelId = string(MODEL_ID_KEY)?.takeIf(String::isNotBlank) ?: return null
        return ModelTarget(
            document = this,
            providerId = providerId,
            providerName = string(PROVIDER_NAME_KEY),
            modelId = modelId,
            kind = string(KIND_KEY),
            baseUrl = string(BASE_URL_KEY),
            apiKey = string(API_KEY_KEY),
            primary = primary,
        )
    }

    private fun modelTarget(
        providerId: String,
        providerName: String?,
        modelId: String,
        kind: String,
        baseUrl: String,
        apiKey: String?,
    ) = JsonObject().also { target ->
        putPreserving(target, PROVIDER_ID_KEY, JsonPrimitive.of(providerId.trim()))
        providerName?.trim()?.takeIf(String::isNotBlank)?.let {
            putPreserving(target, PROVIDER_NAME_KEY, JsonPrimitive.of(it))
        }
        putPreserving(target, MODEL_ID_KEY, JsonPrimitive.of(modelId.trim()))
        putPreserving(target, KIND_KEY, JsonPrimitive.of(kind))
        putPreserving(target, BASE_URL_KEY, JsonPrimitive.of(baseUrl.trim()))
        if (apiKey.isNullOrBlank()) target.remove(API_KEY_KEY)
        else putPreserving(target, API_KEY_KEY, JsonPrimitive.of(apiKey))
    }

    private fun targetRef(target: JsonObject): String =
        "${target.string(PROVIDER_ID_KEY).orEmpty()}/${target.string(MODEL_ID_KEY).orEmpty()}"

    private fun inferProviderKind(baseUrl: String?): String =
        if (baseUrl.orEmpty().contains("/anthropic", ignoreCase = true)) ANTHROPIC_KIND else OPENAI_COMPATIBLE_KIND

    private fun inferApiFormat(kind: String?, baseUrl: String?): String =
        if (kind == ANTHROPIC_KIND || baseUrl.orEmpty().contains("/anthropic", ignoreCase = true)) {
            ANTHROPIC_MESSAGES_FORMAT
        } else {
            OPENAI_CHAT_FORMAT
        }

    private data class ModelTarget(
        val document: JsonObject,
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
        private const val PROVIDER_ID_KEY = "provider"
        private const val PROVIDER_NAME_KEY = "providerName"
        private const val MODEL_ID_KEY = "model"
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
