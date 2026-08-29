package com.kite.app.agent.config.native

import android.content.Context
import blue.endless.jankson.JsonArray
import blue.endless.jankson.JsonObject
import blue.endless.jankson.JsonPrimitive
import com.kite.app.agent.config.AgentCredentialPresence
import com.kite.app.agent.config.AgentMcpSummary
import com.kite.app.agent.config.AgentPersistentConfigCapability
import com.kite.app.agent.config.AgentPersistentConfigChange
import com.kite.app.agent.config.AgentProviderCredentialChange
import com.kite.app.agent.config.AgentProviderDraft
import com.kite.app.agent.config.AgentProviderModelSummary
import com.kite.app.agent.config.AgentProviderSummary
import com.kite.app.agent.config.AgentSkillSummary
import com.kite.app.agent.contract.AgentConfigCategory
import com.kite.app.agent.contract.AgentConfigOption
import com.kite.app.agent.contract.AgentMode
import com.kite.app.agent.contract.AgentPermissionLevel
import com.kite.app.foundation.contracts.ContainerRecord
import com.kite.app.foundation.workspace.WorkSurfaceRuntimeBridge

/** Qwen Code 保留原生 MCP/Skill，并按 modelProviders 协议写入自定义 OpenAI Provider。 */
internal class QwenCodeAgentConfigAdapter(
    context: Context,
    containerProvider: () -> ContainerRecord?,
) : StandardJsonMcpProtocolAgentConfigAdapter(
    context = context,
    adapterId = ADAPTER_ID,
    agentDisplayName = "Qwen Code",
    configPath = SETTINGS_PATH,
    containerProvider = containerProvider,
    skillRoots = listOf(SKILL_ROOT, AGENTS_SKILL_ROOT),
    schema = StandardJsonMcpSchema(enablement = StandardJsonMcpEnablement.ExcludedList),
) {
    constructor(context: Context) : this(
        context.applicationContext,
        { WorkSurfaceRuntimeBridge.getSavedContainer(context.applicationContext) },
    )

    override fun normalizeSessionModes(modes: List<AgentMode>): List<AgentMode> =
        modes.filterNot { it.id in QWEN_PERMISSION_LEVELS }

    override fun normalizeSessionConfiguration(options: List<AgentConfigOption>): List<AgentConfigOption> =
        super.normalizeSessionConfiguration(options).mapNotNull { option ->
            if (
                option !is AgentConfigOption.Select ||
                option.id != MODE_CONFIG_ID ||
                option.category != AgentConfigCategory.Mode
            ) return@mapNotNull option
            val choices = option.choices.mapNotNull { choice ->
                val level = QWEN_PERMISSION_LEVELS[choice.value] ?: return@mapNotNull null
                choice.copy(
                    name = level.displayName,
                    description = level.description,
                    permission = level,
                )
            }
            if (choices.size < 2 || choices.none { it.value == option.currentValue }) {
                null
            } else {
                option.copy(
                    name = "权限",
                    description = "Qwen Code 当前会话真实提供的工具审批模式",
                    category = AgentConfigCategory.Permission,
                    choices = choices,
                )
            }
        }

    override fun additionalCapabilities(): Set<AgentPersistentConfigCapability> = setOf(
        AgentPersistentConfigCapability.DefaultModel,
        AgentPersistentConfigCapability.Provider,
        AgentPersistentConfigCapability.ProviderProfiles,
        AgentPersistentConfigCapability.CredentialStatus,
    )

    override fun decodeAdditionalState(
        root: JsonObject,
        mcpServers: List<AgentMcpSummary>,
        skills: List<AgentSkillSummary>,
    ): NativeState {
        val environment = root.getObject(ENV_KEY)
        val providerModels = root.getObject(MODEL_PROVIDERS_KEY)?.entries.orEmpty().flatMap { (protocol, value) ->
            (value as? JsonArray).orEmpty().mapNotNull { item ->
                val model = item as? JsonObject ?: return@mapNotNull null
                if (model.string(ID_KEY).isNullOrBlank()) return@mapNotNull null
                QwenProviderModel(protocol, model)
            }
        }
        val providers = providerModels.groupBy { entry ->
            entry.model.string(KITE_PROVIDER_ID_KEY)?.takeIf(String::isNotBlank) ?: entry.protocol
        }.map { (id, entries) ->
            val summaries = entries.mapNotNull { entry ->
                val modelId = entry.model.string(ID_KEY)?.takeIf(String::isNotBlank) ?: return@mapNotNull null
                AgentProviderModelSummary(
                    modelId,
                    entry.model.string(NAME_KEY)?.takeIf(String::isNotBlank) ?: modelId,
                )
            }
            val first = entries.first().model
            val envKeys = entries.mapNotNull { it.model.string(ENV_KEY_FIELD) }.distinct()
            val credential = if (envKeys.any { envKey -> !environment?.string(envKey).isNullOrBlank() }) {
                AgentCredentialPresence.Present
            } else {
                AgentCredentialPresence.Missing
            }
            AgentProviderSummary(
                id = id,
                displayName = id,
                baseUrl = first.string(BASE_URL_KEY),
                models = summaries,
                credentialPresence = credential,
            )
        }.sortedBy(AgentProviderSummary::id)
        val activeProtocol = root.getObject(SECURITY_KEY)?.getObject(AUTH_KEY)?.string(SELECTED_TYPE_KEY)
        val activeModel = root.getObject(MODEL_KEY)?.string(NAME_KEY)
        val activeProvider = providerModels.firstOrNull { entry ->
            entry.protocol == activeProtocol && entry.model.string(ID_KEY) == activeModel
        }?.model?.string(KITE_PROVIDER_ID_KEY)?.takeIf(String::isNotBlank)
            ?: activeProtocol?.takeIf { selected -> providers.any { it.id == selected } }
        return NativeState(
            defaultModel = root.getObject(MODEL_KEY)?.string(NAME_KEY),
            providers = providers,
            credentialPresence = overallCredential(providers),
            activeProviderId = activeProvider,
            mcpServers = mcpServers,
            skills = skills,
        )
    }

    override fun mutateAdditionalState(root: JsonObject, change: AgentPersistentConfigChange) {
        when (change) {
            is AgentPersistentConfigChange.SetDefaultModel -> {
                val model = root.objectCopy(MODEL_KEY)
                if (change.modelId == null) model.remove(NAME_KEY)
                else putPreserving(model, NAME_KEY, JsonPrimitive.of(change.modelId))
                putPreserving(root, MODEL_KEY, model)
            }
            is AgentPersistentConfigChange.SelectProvider -> selectProvider(root, change.providerId, change.modelId)
            is AgentPersistentConfigChange.ConfigureProvider -> configureProvider(root, change.provider, change.credential)
            is AgentPersistentConfigChange.RemoveProvider -> removeProvider(
                root,
                change.providerId,
                change.removeCredential,
            )
            else -> Unit
        }
    }

    private fun configureProvider(
        root: JsonObject,
        provider: AgentProviderDraft,
        credential: AgentProviderCredentialChange,
    ) {
        val providers = root.objectCopy(MODEL_PROVIDERS_KEY)
        val protocolModels = (providers[OPENAI_PROTOCOL] as? JsonArray).orEmpty()
        val legacyModels = (providers[provider.id] as? JsonArray).orEmpty()
        val ownedModels = (protocolModels + legacyModels).mapNotNull { value ->
            val model = value as? JsonObject ?: return@mapNotNull null
            val owner = model.string(KITE_PROVIDER_ID_KEY)
            if (owner == provider.id || value in legacyModels) model else null
        }
        val existingById = ownedModels.mapNotNull { model ->
            model.string(ID_KEY)?.let { it to model }
        }.toMap()
        val existingEnv = existingById.values.firstNotNullOfOrNull { it.string(ENV_KEY_FIELD) }
        val envName = existingEnv ?: qwenCredentialEnvironment(provider.id)
        val next = JsonArray()
        protocolModels.filterNot { value ->
            (value as? JsonObject)?.string(KITE_PROVIDER_ID_KEY) == provider.id
        }.forEach(next::add)
        provider.models.forEach { summary ->
            val model = existingById[summary.id]?.clone() ?: JsonObject()
            putPreserving(model, ID_KEY, JsonPrimitive.of(summary.id.trim()))
            putPreserving(model, NAME_KEY, JsonPrimitive.of(summary.displayName.ifBlank { summary.id }.trim()))
            putPreserving(model, BASE_URL_KEY, JsonPrimitive.of(provider.baseUrl.trim()))
            putPreserving(model, ENV_KEY_FIELD, JsonPrimitive.of(envName))
            putPreserving(model, KITE_PROVIDER_ID_KEY, JsonPrimitive.of(provider.id))
            next.add(model)
        }
        if (provider.id != OPENAI_PROTOCOL) providers.remove(provider.id)
        putPreserving(providers, OPENAI_PROTOCOL, next)
        putPreserving(root, MODEL_PROVIDERS_KEY, providers)
        val protocols = root.objectCopy(PROVIDER_PROTOCOL_KEY)
        protocols.remove(provider.id)
        if (protocols.isEmpty()) root.remove(PROVIDER_PROTOCOL_KEY)
        else putPreserving(root, PROVIDER_PROTOCOL_KEY, protocols)
        val environment = root.objectCopy(ENV_KEY)
        when (credential) {
            AgentProviderCredentialChange.Keep -> Unit
            is AgentProviderCredentialChange.Replace -> putPreserving(environment, envName, JsonPrimitive.of(credential.secret))
            AgentProviderCredentialChange.Remove -> environment.remove(envName)
        }
        putPreserving(root, ENV_KEY, environment)
        val current = root.getObject(MODEL_KEY)?.string(NAME_KEY)
        if (current.isNullOrBlank()) selectProvider(root, provider.id, provider.models.first().id)
    }

    private fun selectProvider(root: JsonObject, providerId: String, modelId: String) {
        val model = root.objectCopy(MODEL_KEY)
        putPreserving(model, NAME_KEY, JsonPrimitive.of(modelId))
        putPreserving(root, MODEL_KEY, model)
        val security = root.objectCopy(SECURITY_KEY)
        val auth = security.objectCopy(AUTH_KEY)
        val protocol = root.getObject(MODEL_PROVIDERS_KEY)?.entries?.firstOrNull { (_, value) ->
            (value as? JsonArray).orEmpty().any { item ->
                val candidate = item as? JsonObject ?: return@any false
                candidate.string(KITE_PROVIDER_ID_KEY) == providerId && candidate.string(ID_KEY) == modelId
            }
        }?.key ?: OPENAI_PROTOCOL
        putPreserving(auth, SELECTED_TYPE_KEY, JsonPrimitive.of(protocol))
        putPreserving(security, AUTH_KEY, auth)
        putPreserving(root, SECURITY_KEY, security)
    }

    private fun removeProvider(root: JsonObject, providerId: String, removeCredential: Boolean) {
        val providers = root.objectCopy(MODEL_PROVIDERS_KEY)
        val removed = mutableListOf<JsonObject>()
        providers.entries.toList().forEach { (protocol, value) ->
            val models = value as? JsonArray ?: return@forEach
            val kept = JsonArray()
            models.forEach { item ->
                val model = item as? JsonObject
                val belongsToProvider = model?.string(KITE_PROVIDER_ID_KEY) == providerId ||
                    (protocol == providerId && model?.string(KITE_PROVIDER_ID_KEY).isNullOrBlank())
                if (belongsToProvider && model != null) removed += model else kept.add(item)
            }
            if (kept.isEmpty()) providers.remove(protocol) else putPreserving(providers, protocol, kept)
        }
        providers.remove(providerId)
        putPreserving(root, MODEL_PROVIDERS_KEY, providers)
        val protocols = root.objectCopy(PROVIDER_PROTOCOL_KEY)
        protocols.remove(providerId)
        putPreserving(root, PROVIDER_PROTOCOL_KEY, protocols)
        if (removeCredential) {
            val environment = root.objectCopy(ENV_KEY)
            removed.mapNotNull { it.string(ENV_KEY_FIELD) }
                .distinct().forEach(environment::remove)
            putPreserving(root, ENV_KEY, environment)
        }
        val currentModel = root.getObject(MODEL_KEY)?.string(NAME_KEY)
        val removedCurrentModel = removed.any { it.string(ID_KEY) == currentModel }
        val security = root.objectCopy(SECURITY_KEY)
        val auth = security.objectCopy(AUTH_KEY)
        if (removedCurrentModel) auth.remove(SELECTED_TYPE_KEY)
        putPreserving(security, AUTH_KEY, auth)
        putPreserving(root, SECURITY_KEY, security)
        if (removedCurrentModel) {
            val model = root.objectCopy(MODEL_KEY)
            model.remove(NAME_KEY)
            putPreserving(root, MODEL_KEY, model)
        }
    }

    private fun qwenCredentialEnvironment(providerId: String): String =
        "KITE_QWEN_${providerId.uppercase().replace(Regex("[^A-Z0-9]"), "_")}_API_KEY"

    companion object {
        const val ADAPTER_ID = "qwen-code"
        private const val SETTINGS_PATH = "/root/.qwen/settings.json"
        private const val SKILL_ROOT = "/root/.qwen/skills"
        private const val AGENTS_SKILL_ROOT = "/root/.agents/skills"
        private const val MODE_CONFIG_ID = "mode"
        private const val MODEL_PROVIDERS_KEY = "modelProviders"
        private const val PROVIDER_PROTOCOL_KEY = "providerProtocol"
        private const val MODEL_KEY = "model"
        private const val SECURITY_KEY = "security"
        private const val AUTH_KEY = "auth"
        private const val SELECTED_TYPE_KEY = "selectedType"
        private const val ENV_KEY = "env"
        private const val ENV_KEY_FIELD = "envKey"
        private const val ID_KEY = "id"
        private const val NAME_KEY = "name"
        private const val BASE_URL_KEY = "baseUrl"
        private const val KITE_PROVIDER_ID_KEY = "x-kite-provider-id"
        private const val OPENAI_PROTOCOL = "openai"
        private val QWEN_PERMISSION_LEVELS = mapOf(
            "plan" to AgentPermissionLevel.ReadOnly,
            "default" to AgentPermissionLevel.Approval,
            "auto-edit" to AgentPermissionLevel.Lenient,
            "auto" to AgentPermissionLevel.Smart,
            "yolo" to AgentPermissionLevel.Full,
        )
    }

    private data class QwenProviderModel(
        val protocol: String,
        val model: JsonObject,
    )
}
