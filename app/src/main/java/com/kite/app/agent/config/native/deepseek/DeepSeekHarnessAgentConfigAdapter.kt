package com.kite.app.agent.config.native

import android.content.Context
import com.kite.app.agent.config.AgentConfigCapabilities
import com.kite.app.agent.config.AgentCredentialOwnership
import com.kite.app.agent.config.AgentCredentialPresence
import com.kite.app.agent.config.AgentPersistentConfigCapability
import com.kite.app.agent.config.AgentPersistentConfigChange
import com.kite.app.agent.config.AgentProviderCredentialChange
import com.kite.app.agent.config.AgentProviderModelSummary
import com.kite.app.agent.config.AgentProviderSummary
import com.kite.app.agent.config.AtomicConfigFileStore
import com.kite.app.foundation.contracts.ContainerRecord
import com.kite.app.foundation.workspace.WorkSurfaceRuntimeBridge
import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor
import java.net.URI
import java.util.LinkedHashMap

/** DeepSeek Harness 官方 settings.yaml / .credentials.yaml 供应商配置适配器。 */
internal class DeepSeekHarnessAgentConfigAdapter internal constructor(
    context: Context,
    containerProvider: () -> ContainerRecord?,
    fileStore: AtomicConfigFileStore = AtomicConfigFileStore(),
) : NativeAgentConfigAdapter(
    context = context,
    adapterId = ADAPTER_ID,
    paths = linkedMapOf(
        SETTINGS_KEY to SETTINGS_PATH,
        CREDENTIALS_KEY to CREDENTIALS_PATH,
    ),
    primaryKey = SETTINGS_KEY,
    containerProvider = containerProvider,
    fileStore = fileStore,
) {
    constructor(context: Context) : this(
        context.applicationContext,
        { WorkSurfaceRuntimeBridge.getSavedContainer(context.applicationContext) },
    )

    private val yaml = Yaml(
        SafeConstructor(LoaderOptions().apply {
            maxAliasesForCollections = 50
            nestingDepthLimit = 32
            codePointLimit = MAX_DOCUMENT_BYTES
        }),
        org.yaml.snakeyaml.representer.Representer(DumperOptions()),
        DumperOptions().apply {
            defaultFlowStyle = DumperOptions.FlowStyle.BLOCK
            isPrettyFlow = true
            indent = 2
            indicatorIndent = 0
            width = 120
        },
    )

    override fun displayName(): String = "DeepSeek Harness"

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
        val settings = yamlMap(files.getValue(SETTINGS_KEY))
        val credentials = yamlMap(files.getValue(CREDENTIALS_KEY))
        val providerEntries = settings.map(LLM_PI_AI_SECTION).map(PROVIDERS_KEY)
        val providers = providerEntries.mapNotNull { (providerId, value) ->
            val provider = value.asStringMap() ?: return@mapNotNull null
            val models = provider.list(MODELS_KEY).mapNotNull { item ->
                val model = item.asStringMap() ?: return@mapNotNull null
                val id = model.string(ID_KEY)?.takeIf(String::isNotBlank) ?: return@mapNotNull null
                AgentProviderModelSummary(
                    id = id,
                    displayName = model.string(NAME_KEY)?.takeIf(String::isNotBlank) ?: id,
                )
            }
            if (models.isEmpty()) return@mapNotNull null
            val credentialRef = provider.string(API_KEY_ENV_KEY)
            AgentProviderSummary(
                id = providerId,
                displayName = provider.string(DISPLAY_NAME_KEY)?.takeIf(String::isNotBlank) ?: providerId,
                baseUrl = provider.string(BASE_URL_KEY),
                models = models,
                credentialPresence = if (!credentialRef.isNullOrBlank() && !credentials.string(credentialRef).isNullOrBlank()) {
                    AgentCredentialPresence.Present
                } else {
                    AgentCredentialPresence.Missing
                },
            )
        }.sortedBy(AgentProviderSummary::id)
        val selection = settings.map(DEFAULT_MODEL_SECTION)
        val activeProvider = selection.string(PROVIDER_KEY)
            ?.takeIf { id -> providers.any { it.id == id } }
        return NativeState(
            defaultModel = selection.string(MODEL_KEY),
            providers = providers,
            credentialPresence = overallCredential(providers),
            activeProviderId = activeProvider,
        )
    }

    override fun mutate(
        files: Map<String, ByteArray>,
        changes: List<AgentPersistentConfigChange>,
    ): Map<String, ByteArray> {
        val originalSettings = files.getValue(SETTINGS_KEY).toString(Charsets.UTF_8)
        val settings = yamlMap(files.getValue(SETTINGS_KEY)).deepMutableMap()
        val credentials = yamlMap(files.getValue(CREDENTIALS_KEY)).deepMutableMap()

        changes.forEach { change ->
            when (change) {
                is AgentPersistentConfigChange.SetDefaultModel -> {
                    if (change.modelId == null) {
                        settings.remove(DEFAULT_MODEL_SECTION)
                    } else {
                        val selection = settings.mutableMap(DEFAULT_MODEL_SECTION)
                        selection[MODEL_KEY] = change.modelId
                        settings[DEFAULT_MODEL_SECTION] = selection
                    }
                }
                is AgentPersistentConfigChange.SelectProvider -> {
                    settings[DEFAULT_MODEL_SECTION] = linkedMapOf(
                        PROVIDER_KEY to change.providerId,
                        MODEL_KEY to change.modelId,
                    )
                }
                is AgentPersistentConfigChange.ConfigureProvider -> {
                    val draft = change.provider
                    val llm = settings.mutableMap(LLM_PI_AI_SECTION)
                    val providers = llm.mutableMap(PROVIDERS_KEY)
                    val current = providers.mutableMap(draft.id)
                    val credentialRef = current.string(API_KEY_ENV_KEY) ?: credentialEnvironment(draft.id)
                    val existingModels = current.list(MODELS_KEY).mapNotNull { value ->
                        value.asStringMap()?.let { model -> model.string(ID_KEY)?.let { it to model.deepMutableMap() } }
                    }.toMap()
                    val nextModels = draft.models.map { summary ->
                        (existingModels[summary.id] ?: linkedMapOf()).apply {
                            this[ID_KEY] = summary.id.trim()
                            this[NAME_KEY] = summary.displayName.takeIf(String::isNotBlank) ?: summary.id
                        }
                    }
                    current[API_KEY_ENV_KEY] = credentialRef
                    current[DISPLAY_NAME_KEY] = draft.displayName?.takeIf(String::isNotBlank) ?: draft.id
                    current[API_KEY] = OPENAI_COMPLETIONS_API
                    current[BASE_URL_KEY] = draft.baseUrl.trim()
                    current[MODELS_KEY] = nextModels
                    if (isZhipuEndpoint(draft.baseUrl)) {
                        val compatibility = current.mutableMap(COMPAT_KEY)
                        compatibility[SUPPORTS_DEVELOPER_ROLE_KEY] = false
                        compatibility[MAX_TOKENS_FIELD_KEY] = MAX_TOKENS_FIELD_VALUE
                        current[COMPAT_KEY] = compatibility
                    }
                    providers[draft.id] = current
                    llm[PROVIDERS_KEY] = providers
                    settings[LLM_PI_AI_SECTION] = llm
                    when (val credential = change.credential) {
                        AgentProviderCredentialChange.Keep -> Unit
                        is AgentProviderCredentialChange.Replace -> credentials[credentialRef] = credential.secret
                        AgentProviderCredentialChange.Remove -> credentials.remove(credentialRef)
                    }
                    settings[DEFAULT_MODEL_SECTION] = linkedMapOf(
                        PROVIDER_KEY to draft.id,
                        MODEL_KEY to draft.models.first().id.trim(),
                    )
                }
                is AgentPersistentConfigChange.RemoveProvider -> {
                    val llm = settings.mutableMap(LLM_PI_AI_SECTION)
                    val providers = llm.mutableMap(PROVIDERS_KEY)
                    val removed = providers.remove(change.providerId).asStringMap()
                    if (change.removeCredential) {
                        removed?.string(API_KEY_ENV_KEY)?.let(credentials::remove)
                    }
                    llm[PROVIDERS_KEY] = providers
                    settings[LLM_PI_AI_SECTION] = llm
                    if (settings.map(DEFAULT_MODEL_SECTION).string(PROVIDER_KEY) == change.providerId) {
                        settings.remove(DEFAULT_MODEL_SECTION)
                    }
                }
                else -> Unit
            }
        }

        val nextSettings = replaceYamlSections(
            originalSettings,
            linkedMapOf(
                LLM_PI_AI_SECTION to settings[LLM_PI_AI_SECTION],
                DEFAULT_MODEL_SECTION to settings[DEFAULT_MODEL_SECTION],
            ),
        )
        return mapOf(
            SETTINGS_KEY to nextSettings.toByteArray(Charsets.UTF_8),
            CREDENTIALS_KEY to dumpMap(credentials),
        )
    }

    override fun validateBytes(key: String, bytes: ByteArray): String? = runCatching {
        require(bytes.size <= MAX_DOCUMENT_BYTES)
        val root = yamlMap(bytes)
        if (key == CREDENTIALS_KEY) {
            require(root.all { (name, value) -> SAFE_ENV_NAME.matches(name) && value is String && value.isNotBlank() })
        }
        null
    }.getOrElse { "DeepSeek Harness 原生 YAML 配置格式无效" }

    private fun yamlMap(bytes: ByteArray): Map<String, Any?> {
        val text = bytes.toString(Charsets.UTF_8)
        if (text.isBlank()) return emptyMap()
        val loaded = yaml.load<Any?>(text) ?: return emptyMap()
        require(loaded is Map<*, *>) { "YAML 顶层必须是对象" }
        return loaded.entries.associateTo(linkedMapOf()) { it.key.toString() to it.value }
    }

    private fun dumpMap(value: Map<String, Any?>): ByteArray = if (value.isEmpty()) {
        ByteArray(0)
    } else {
        yaml.dump(value).trimEnd().plus("\n").toByteArray(Charsets.UTF_8)
    }

    private fun replaceYamlSections(
        source: String,
        sections: LinkedHashMap<String, Any?>,
    ): String {
        var result = source.replace("\r\n", "\n").replace('\r', '\n')
        sections.forEach { (key, value) ->
            val range = yamlSectionRange(result, key)
            if (value == null) {
                if (range != null) result = result.removeRange(range)
            } else {
                val dumped = yaml.dump(linkedMapOf(key to value)).trimEnd() + "\n"
                result = if (range == null) {
                    result.trimEnd().let { existing -> if (existing.isBlank()) dumped else "$existing\n\n$dumped" }
                } else {
                    result.substring(0, range.first) + dumped + result.substring(range.last + 1)
                }
            }
        }
        return result.trimEnd().let { if (it.isBlank()) "" else "$it\n" }
    }

    private fun yamlSectionRange(source: String, key: String): IntRange? {
        val linePattern = Regex("(?m)^${Regex.escape(key)}:(?:[ \\t].*)?(?:\\n|$)")
        val startMatch = linePattern.find(source) ?: return null
        val next = Regex("(?m)^[A-Za-z0-9_.-]+:(?:[ \\t].*)?(?:\\n|$)")
            .find(source, startMatch.range.last + 1)
        val endExclusive = next?.range?.first ?: source.length
        return startMatch.range.first until endExclusive
    }

    private fun credentialEnvironment(providerId: String): String =
        "KITE_DSH_${providerId.uppercase().replace(Regex("[^A-Z0-9]"), "_")}_API_KEY"

    private fun isZhipuEndpoint(baseUrl: String): Boolean = runCatching {
        URI(baseUrl.trim()).host?.equals("open.bigmodel.cn", ignoreCase = true) == true
    }.getOrDefault(false)

    private fun Any?.asStringMap(): Map<String, Any?>? = (this as? Map<*, *>)
        ?.entries
        ?.associateTo(linkedMapOf()) { it.key.toString() to it.value }

    private fun Map<String, Any?>.map(key: String): Map<String, Any?> = this[key].asStringMap().orEmpty()

    private fun Map<String, Any?>.string(key: String): String? = this[key] as? String

    private fun Map<String, Any?>.list(key: String): List<Any?> = this[key] as? List<Any?> ?: emptyList()

    private fun Map<String, Any?>.deepMutableMap(): LinkedHashMap<String, Any?> = entries.associateTo(linkedMapOf()) {
        it.key to deepMutable(it.value)
    }

    private fun deepMutable(value: Any?): Any? = when (value) {
        is Map<*, *> -> value.entries.associateTo(linkedMapOf<String, Any?>()) {
            it.key.toString() to deepMutable(it.value)
        }
        is List<*> -> value.map(::deepMutable).toMutableList()
        else -> value
    }

    private fun MutableMap<String, Any?>.mutableMap(key: String): LinkedHashMap<String, Any?> =
        (this[key].asStringMap()?.deepMutableMap() ?: linkedMapOf()).also { this[key] = it }

    companion object {
        const val ADAPTER_ID = "deepseek-harness"
        private const val SETTINGS_KEY = "settings"
        private const val CREDENTIALS_KEY = "credentials"
        private const val HARNESS_HOME = "/workspace/.kf/software/kite.deepseek.harness/user-home/.dsh"
        private const val SETTINGS_PATH = "$HARNESS_HOME/settings.yaml"
        private const val CREDENTIALS_PATH = "$HARNESS_HOME/.credentials.yaml"
        private const val LLM_PI_AI_SECTION = "llm-pi-ai"
        private const val DEFAULT_MODEL_SECTION = "agent-default-model"
        private const val PROVIDERS_KEY = "providers"
        private const val PROVIDER_KEY = "provider"
        private const val MODEL_KEY = "model"
        private const val MODELS_KEY = "models"
        private const val ID_KEY = "id"
        private const val NAME_KEY = "name"
        private const val DISPLAY_NAME_KEY = "displayName"
        private const val API_KEY_ENV_KEY = "apiKeyEnv"
        private const val API_KEY = "api"
        private const val BASE_URL_KEY = "baseURL"
        private const val COMPAT_KEY = "compat"
        private const val SUPPORTS_DEVELOPER_ROLE_KEY = "supportsDeveloperRole"
        private const val MAX_TOKENS_FIELD_KEY = "maxTokensField"
        private const val MAX_TOKENS_FIELD_VALUE = "max_tokens"
        private const val OPENAI_COMPLETIONS_API = "openai-completions"
        private const val MAX_DOCUMENT_BYTES = 8 * 1024 * 1024
        private val SAFE_ENV_NAME = Regex("[A-Za-z_][A-Za-z0-9_]{0,127}")
    }
}
