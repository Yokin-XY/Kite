package com.kite.app.agent.config.native

import android.content.Context
import blue.endless.jankson.JsonArray
import blue.endless.jankson.JsonObject
import blue.endless.jankson.JsonPrimitive
import com.kite.app.agent.config.AgentConfigApplyRequest
import com.kite.app.agent.config.AgentConfigApplyResult
import com.kite.app.agent.config.AgentConfigCapabilities
import com.kite.app.agent.config.AgentConfigReadResult
import com.kite.app.agent.config.AgentConfigValidationProblem
import com.kite.app.agent.config.AgentCredentialOwnership
import com.kite.app.agent.config.AgentCredentialPresence
import com.kite.app.agent.config.AgentLiveConfigSnapshot
import com.kite.app.agent.config.AgentPersistentConfigCapability
import com.kite.app.agent.config.AgentPersistentConfigChange
import com.kite.app.agent.config.AgentProviderCredentialChange
import com.kite.app.agent.config.AgentProviderDraft
import com.kite.app.agent.config.AgentProviderModelSummary
import com.kite.app.agent.config.AgentProviderSummary
import com.kite.app.agent.config.AgentSkillActivation
import com.kite.app.agent.config.AgentSkillOperation
import com.kite.app.agent.config.AtomicConfigFileStore
import com.kite.app.foundation.contracts.ContainerRecord
import com.kite.app.foundation.workspace.WorkSurfaceRuntimeBridge

/** Pi 的 Provider 以 models.json 为事实源；模型选择由 Kite 在每个 RPC 会话内应用。 */
internal class PiCodingAgentConfigAdapter internal constructor(
    context: Context,
    containerProvider: () -> ContainerRecord?,
    fileStore: AtomicConfigFileStore = AtomicConfigFileStore(),
) : JanksonNativeAgentConfigAdapter(
    context = context,
    adapterId = ADAPTER_ID,
    paths = linkedMapOf(CONFIG_KEY to CONFIG_PATH),
    primaryKey = CONFIG_KEY,
    containerProvider = containerProvider,
    fileStore = fileStore,
) {
    constructor(context: Context) : this(
        context.applicationContext,
        { WorkSurfaceRuntimeBridge.getSavedContainer(context.applicationContext) },
    )

    private val skillDirectory = NativeAgentSkillDirectory(
        project = projection::resolve,
        roots = listOf(SKILL_ROOT, AGENTS_SKILL_ROOT),
    )

    override fun displayName(): String = "Pi"

    override fun nativeCapabilities(): AgentConfigCapabilities = AgentConfigCapabilities(
        supported = setOf(
            AgentPersistentConfigCapability.Provider,
            AgentPersistentConfigCapability.ProviderProfiles,
            AgentPersistentConfigCapability.CredentialStatus,
            AgentPersistentConfigCapability.Skill,
        ),
        credentialOwnership = AgentCredentialOwnership.AgentOwned,
        skillOperations = setOf(AgentSkillOperation.Import, AgentSkillOperation.Remove),
    )

    override fun decode(files: Map<String, ByteArray>): NativeState {
        val root = parse(files.getValue(CONFIG_KEY))
        val providers = root.getObject(PROVIDERS_KEY)?.entries.orEmpty().mapNotNull { (id, value) ->
            val provider = value as? JsonObject ?: return@mapNotNull null
            AgentProviderSummary(
                id = id,
                displayName = provider.string(NAME_KEY)?.takeIf(String::isNotBlank) ?: id,
                baseUrl = provider.string(BASE_URL_KEY),
                models = (provider[MODELS_KEY] as? JsonArray).orEmpty().mapNotNull { item ->
                    val model = item as? JsonObject ?: return@mapNotNull null
                    val modelId = model.string(ID_KEY)?.takeIf(String::isNotBlank) ?: return@mapNotNull null
                    AgentProviderModelSummary(
                        modelId,
                        model.string(NAME_KEY)?.takeIf(String::isNotBlank) ?: modelId,
                    )
                },
                credentialPresence = if (provider.string(API_KEY).isNullOrBlank()) {
                    AgentCredentialPresence.Missing
                } else {
                    AgentCredentialPresence.Present
                },
            )
        }.sortedBy(AgentProviderSummary::id)
        return NativeState(
            defaultModel = null,
            providers = providers,
            credentialPresence = overallCredential(providers),
            skills = skillDirectory.summaries(
                activation = { AgentSkillActivation.Enabled },
                activationOperations = emptySet(),
            ),
            warnings = listOf("模型选择由 Pi RPC 当前会话保存，Provider 以 models.json 为事实源"),
        )
    }

    override fun validateNativeChange(
        index: Int,
        change: AgentPersistentConfigChange,
        output: MutableList<AgentConfigValidationProblem>,
    ) {
        when (change) {
            is AgentPersistentConfigChange.InstallSkill -> {
                if (!isSafeNativeId(change.skillId)) output += problem("changes[$index].skillId", "Skill ID 格式无效")
                if (!SAFE_IMPORT_REFERENCE.matches(change.sourceReference)) {
                    output += problem("changes[$index].sourceReference", "Skill 来源引用格式无效")
                }
            }
            is AgentPersistentConfigChange.RemoveSkill -> if (!isSafeNativeId(change.skillId)) {
                output += problem("changes[$index].skillId", "Skill ID 格式无效")
            }
            else -> super.validateNativeChange(index, change, output)
        }
    }

    override suspend fun applyExternalChanges(
        request: AgentConfigApplyRequest,
        before: AgentLiveConfigSnapshot,
    ): AgentConfigApplyResult? {
        val changes = request.changes.filter {
            it is AgentPersistentConfigChange.InstallSkill || it is AgentPersistentConfigChange.RemoveSkill
        }
        if (changes.isEmpty()) return null
        if (request.changes.size != 1 || changes.size != 1) {
            return AgentConfigApplyResult.Rejected(
                listOf(problem("changes", "Skill 变更一次只能执行一项，不能和 Provider 配置混合")),
            )
        }
        skillDirectory.applyFileChange(changes.single())?.let { return it }
        return when (val refreshed = readLive(request.agentId)) {
            is AgentConfigReadResult.Ready -> AgentConfigApplyResult.Applied(refreshed.snapshot, null)
            is AgentConfigReadResult.Failed -> AgentConfigApplyResult.Failed(refreshed.message, false)
            is AgentConfigReadResult.Unavailable -> AgentConfigApplyResult.Unavailable(refreshed.discovery)
        }
    }

    override fun nativeRevisionInputs(): List<Pair<String, String>> = skillDirectory.revisionInputs()

    override suspend fun readSkillDocument(agentId: String, skillId: String) = skillDirectory.readDocument(skillId)

    override suspend fun writeSkillDocument(request: com.kite.app.agent.config.AgentSkillDocumentWriteRequest) =
        skillDirectory.writeDocument(request)

    override fun mutate(
        files: Map<String, ByteArray>,
        changes: List<AgentPersistentConfigChange>,
    ): Map<String, ByteArray> {
        val root = parse(files.getValue(CONFIG_KEY)).clone()
        changes.forEach { change ->
            when (change) {
                is AgentPersistentConfigChange.ConfigureProvider -> configure(root, change.provider, change.credential)
                is AgentPersistentConfigChange.RemoveProvider -> {
                    val providers = root.objectCopy(PROVIDERS_KEY)
                    providers.remove(change.providerId)
                    putPreserving(root, PROVIDERS_KEY, providers)
                }
                // Pi 没有原生跨会话默认模型字段；Kite 会在会话 RPC 中应用选择。
                is AgentPersistentConfigChange.SelectProvider -> Unit
                else -> Unit
            }
        }
        return mapOf(CONFIG_KEY to serialize(root))
    }

    private fun configure(
        root: JsonObject,
        provider: AgentProviderDraft,
        credential: AgentProviderCredentialChange,
    ) {
        val providers = root.objectCopy(PROVIDERS_KEY)
        val entry = providers.objectCopy(provider.id)
        provider.displayName?.takeIf(String::isNotBlank)?.let {
            putPreserving(entry, NAME_KEY, JsonPrimitive.of(it.trim()))
        }
        putPreserving(entry, BASE_URL_KEY, JsonPrimitive.of(provider.baseUrl.trim()))
        putPreserving(entry, API_KIND_KEY, JsonPrimitive.of(OPENAI_COMPLETIONS))
        when (credential) {
            AgentProviderCredentialChange.Keep -> Unit
            is AgentProviderCredentialChange.Replace -> putPreserving(entry, API_KEY, JsonPrimitive.of(credential.secret))
            AgentProviderCredentialChange.Remove -> entry.remove(API_KEY)
        }
        val existing = (entry[MODELS_KEY] as? JsonArray).orEmpty().mapNotNull { value ->
            val model = value as? JsonObject ?: return@mapNotNull null
            model.string(ID_KEY)?.let { it to model }
        }.toMap()
        val models = JsonArray()
        provider.models.forEach { summary ->
            val model = existing[summary.id]?.clone() ?: JsonObject()
            putPreserving(model, ID_KEY, JsonPrimitive.of(summary.id.trim()))
            putPreserving(model, NAME_KEY, JsonPrimitive.of(summary.displayName.ifBlank { summary.id }.trim()))
            if (!model.containsKey(REASONING_KEY)) putPreserving(model, REASONING_KEY, JsonPrimitive.of(false))
            if (!model.containsKey(CONTEXT_WINDOW_KEY)) putPreserving(model, CONTEXT_WINDOW_KEY, JsonPrimitive.of(DEFAULT_CONTEXT))
            if (!model.containsKey(MAX_TOKENS_KEY)) putPreserving(model, MAX_TOKENS_KEY, JsonPrimitive.of(DEFAULT_MAX_TOKENS))
            if (!model.containsKey(INPUT_KEY)) {
                putPreserving(model, INPUT_KEY, JsonArray().also { it.add(JsonPrimitive.of("text")) })
            }
            models.add(model)
        }
        putPreserving(entry, MODELS_KEY, models)
        putPreserving(providers, provider.id, entry)
        putPreserving(root, PROVIDERS_KEY, providers)
    }

    companion object {
        const val ADAPTER_ID = "pi-coding-agent"
        private const val CONFIG_KEY = "models"
        private const val CONFIG_PATH = "/root/.pi/agent/models.json"
        private const val SKILL_ROOT = "/root/.pi/agent/skills"
        private const val AGENTS_SKILL_ROOT = "/root/.agents/skills"
        private const val PROVIDERS_KEY = "providers"
        private const val NAME_KEY = "name"
        private const val BASE_URL_KEY = "baseUrl"
        private const val API_KEY = "apiKey"
        private const val API_KIND_KEY = "api"
        private const val OPENAI_COMPLETIONS = "openai-completions"
        private const val MODELS_KEY = "models"
        private const val ID_KEY = "id"
        private const val REASONING_KEY = "reasoning"
        private const val INPUT_KEY = "input"
        private const val CONTEXT_WINDOW_KEY = "contextWindow"
        private const val MAX_TOKENS_KEY = "maxTokens"
        private const val DEFAULT_CONTEXT = 128_000L
        private const val DEFAULT_MAX_TOKENS = 16_384L
        private val SAFE_IMPORT_REFERENCE = Regex("kite-import:import-[A-Za-z0-9-]{8,80}")
    }
}
