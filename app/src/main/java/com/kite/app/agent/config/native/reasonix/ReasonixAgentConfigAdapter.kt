package com.kite.app.agent.config.native

import android.content.Context
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
import com.kite.app.agent.config.AgentReasoningControl
import com.kite.app.agent.config.AgentReasoningNativeMapping
import com.kite.app.agent.config.AgentSkillActivation
import com.kite.app.agent.config.AgentSkillOperation
import com.kite.app.agent.config.AgentWorkModeCatalog
import com.kite.app.agent.config.AtomicConfigFileStore
import com.kite.app.agent.config.standardReasoningLevelMappings
import com.kite.app.agent.contract.AgentConfigCategory
import com.kite.app.agent.contract.AgentConfigOption
import com.kite.app.agent.contract.AgentMode
import com.kite.app.agent.contract.AgentPermissionLevel
import com.kite.app.agent.contract.AgentReasoningMode
import com.kite.app.foundation.contracts.ContainerRecord
import com.kite.app.foundation.workspace.WorkSurfaceRuntimeBridge
import org.tomlj.Toml
import org.tomlj.TomlTable

/** Reasonix 直接管理 config.toml Provider，凭据仍放在它自己的 .env 中。 */
internal class ReasonixAgentConfigAdapter internal constructor(
    context: Context,
    containerProvider: () -> ContainerRecord?,
    fileStore: AtomicConfigFileStore = AtomicConfigFileStore(),
) : NativeAgentConfigAdapter(
    context = context,
    adapterId = ADAPTER_ID,
    paths = linkedMapOf(CONFIG_KEY to CONFIG_PATH, ENV_KEY to ENV_PATH),
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
        roots = listOf(SKILL_ROOT, AGENTS_SKILL_ROOT, AGENT_SKILL_ROOT, CLAUDE_SKILL_ROOT),
    )

    override fun displayName(): String = "Reasonix"

    override fun nativeCapabilities(): AgentConfigCapabilities = AgentConfigCapabilities(
        supported = setOf(
            AgentPersistentConfigCapability.DefaultModel,
            AgentPersistentConfigCapability.Provider,
            AgentPersistentConfigCapability.ProviderProfiles,
            AgentPersistentConfigCapability.CredentialStatus,
            AgentPersistentConfigCapability.Skill,
        ),
        credentialOwnership = AgentCredentialOwnership.AgentOwned,
        skillOperations = setOf(AgentSkillOperation.Import, AgentSkillOperation.Remove),
    )

    override fun bundledWorkModeCatalog(agentId: String): AgentWorkModeCatalog = AgentWorkModeCatalog(
        modes = REASONIX_COLLABORATION_MODES.values.toList(),
        defaultModeId = MODE_NORMAL,
    )

    override fun normalizeSessionModes(modes: List<AgentMode>): List<AgentMode> = modes.map { mode ->
        REASONIX_COLLABORATION_MODES[mode.id] ?: mode
    }

    override fun normalizeSessionConfiguration(options: List<AgentConfigOption>): List<AgentConfigOption> =
        super.normalizeSessionConfiguration(options).mapNotNull { option ->
            if (option !is AgentConfigOption.Select || option.id != TOOL_APPROVAL_CONFIG_ID) return@mapNotNull option
            val choices = option.choices.mapNotNull { choice ->
                val level = REASONIX_PERMISSION_LEVELS[choice.value] ?: return@mapNotNull null
                choice.copy(name = level.displayName, description = level.description, permission = level)
            }
            if (choices.size < 2 || choices.none { it.value == option.currentValue }) null
            else option.copy(
                name = "权限",
                description = "Reasonix 当前会话真实提供的工具审批策略",
                category = AgentConfigCategory.Permission,
                choices = choices,
            )
        }

    override fun reasoningControl(): AgentReasoningControl = REASONIX_REASONING_CONTROL

    override fun decode(files: Map<String, ByteArray>): NativeState {
        val parsed = Toml.parse(files.getValue(CONFIG_KEY).toString(Charsets.UTF_8))
        check(!parsed.hasErrors()) { "Reasonix config.toml 格式无效" }
        val environment = parseDotenv(files.getValue(ENV_KEY).toString(Charsets.UTF_8))
        val providersArray = parsed.getArray(PROVIDERS_KEY)
        val providers = buildList {
            if (providersArray != null) for (index in 0 until providersArray.size()) {
                val provider = runCatching { providersArray.getTable(index) }.getOrNull() ?: continue
                val id = provider.getString(NAME_KEY)?.takeIf(String::isNotBlank) ?: continue
                val envName = provider.getString(API_KEY_ENV)
                add(
                    AgentProviderSummary(
                        id = id,
                        displayName = id,
                        baseUrl = provider.getString(BASE_URL_KEY),
                        models = reasonixModels(provider).map(::AgentProviderModelSummary),
                        credentialPresence = if (!envName.isNullOrBlank() && !environment[envName].isNullOrBlank()) {
                            AgentCredentialPresence.Present
                        } else {
                            AgentCredentialPresence.Missing
                        },
                    ),
                )
            }
        }.sortedBy(AgentProviderSummary::id)
        val defaultModel = parsed.getString(DEFAULT_MODEL_KEY)
        return NativeState(
            defaultModel = defaultModel,
            providers = providers,
            credentialPresence = overallCredential(providers),
            activeProviderId = defaultModel?.substringBefore('/')?.takeIf { active -> providers.any { it.id == active } },
            skills = skillDirectory.summaries(
                activation = { AgentSkillActivation.Enabled },
                activationOperations = emptySet(),
            ),
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
        val configText = files.getValue(CONFIG_KEY).toString(Charsets.UTF_8)
        val editor = NativeTomlTextEditor(configText)
        val environment = parseDotenv(files.getValue(ENV_KEY).toString(Charsets.UTF_8)).toMutableMap()
        changes.forEach { change ->
            when (change) {
                is AgentPersistentConfigChange.SetDefaultModel -> editor.setRootString(DEFAULT_MODEL_KEY, change.modelId)
                is AgentPersistentConfigChange.SelectProvider ->
                    editor.setRootString(DEFAULT_MODEL_KEY, providerModelRef(change.providerId, change.modelId))
                is AgentPersistentConfigChange.ConfigureProvider -> {
                    val parsed = Toml.parse(editor.text)
                    val existingEnv = findReasonixProvider(parsed.getArray(PROVIDERS_KEY), change.provider.id)
                        ?.getString(API_KEY_ENV)
                    val envName = existingEnv ?: reasonixCredentialEnvironment(change.provider.id)
                    editor.setNamedArrayTable(
                        PROVIDERS_KEY,
                        change.provider.id,
                        mapOf(
                            KIND_KEY to NativeTomlTextEditor.tomlString(OPENAI_KIND),
                            BASE_URL_KEY to NativeTomlTextEditor.tomlString(change.provider.baseUrl.trim()),
                            MODELS_KEY to NativeTomlTextEditor.tomlStringArray(change.provider.models.map { it.id.trim() }),
                            API_KEY_ENV to NativeTomlTextEditor.tomlString(envName),
                        ),
                    )
                    when (val credential = change.credential) {
                        AgentProviderCredentialChange.Keep -> Unit
                        is AgentProviderCredentialChange.Replace -> environment[envName] = credential.secret
                        AgentProviderCredentialChange.Remove -> environment.remove(envName)
                    }
                    if (Toml.parse(editor.text).getString(DEFAULT_MODEL_KEY).isNullOrBlank()) {
                        editor.setRootString(
                            DEFAULT_MODEL_KEY,
                            providerModelRef(change.provider.id, change.provider.models.first().id),
                        )
                    }
                }
                is AgentPersistentConfigChange.RemoveProvider -> {
                    val parsed = Toml.parse(editor.text)
                    val envName = findReasonixProvider(parsed.getArray(PROVIDERS_KEY), change.providerId)
                        ?.getString(API_KEY_ENV)
                    editor.removeNamedArrayTable(PROVIDERS_KEY, change.providerId)
                    if (change.removeCredential && envName != null) environment.remove(envName)
                    if (Toml.parse(editor.text).getString(DEFAULT_MODEL_KEY)?.startsWith("${change.providerId}/") == true) {
                        editor.setRootString(DEFAULT_MODEL_KEY, null)
                    }
                }
                else -> Unit
            }
        }
        return mapOf(
            CONFIG_KEY to editor.text.toByteArray(),
            ENV_KEY to renderDotenv(environment).toByteArray(),
        )
    }

    override fun validateBytes(key: String, bytes: ByteArray): String? = when (key) {
        CONFIG_KEY -> if (Toml.parse(bytes.toString(Charsets.UTF_8)).hasErrors()) "Reasonix 原生 config.toml 格式无效" else null
        ENV_KEY -> runCatching { parseDotenv(bytes.toString(Charsets.UTF_8)); null }
            .getOrElse { "Reasonix 原生 .env 格式无效" }
        else -> "未知的 Reasonix 配置文件"
    }

    private fun reasonixModels(provider: TomlTable): List<String> {
        val array = provider.getArray(MODELS_KEY)
        if (array != null) return (0 until array.size()).mapNotNull { index ->
            runCatching { array.getString(index) }.getOrNull()
        }
        return listOfNotNull(provider.getString(MODEL_KEY))
    }

    private fun findReasonixProvider(array: org.tomlj.TomlArray?, id: String): TomlTable? {
        if (array == null) return null
        for (index in 0 until array.size()) {
            val table = runCatching { array.getTable(index) }.getOrNull() ?: continue
            if (table.getString(NAME_KEY) == id) return table
        }
        return null
    }

    private fun parseDotenv(text: String): Map<String, String> = buildMap {
        text.lineSequence().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.isBlank() || trimmed.startsWith('#')) return@forEach
            val separator = trimmed.indexOf('=')
            require(separator > 0) { "invalid dotenv line" }
            val name = trimmed.substring(0, separator).trim()
            require(SAFE_ENV_NAME.matches(name)) { "invalid dotenv key" }
            put(name, trimmed.substring(separator + 1))
        }
    }

    private fun renderDotenv(values: Map<String, String>): String =
        values.toSortedMap().entries.joinToString("\n", postfix = if (values.isEmpty()) "" else "\n") { (key, value) ->
            "$key=$value"
        }

    private fun reasonixCredentialEnvironment(providerId: String): String =
        "KITE_REASONIX_${providerId.uppercase().replace(Regex("[^A-Z0-9]"), "_")}_API_KEY"

    private fun providerModelRef(providerId: String, modelId: String): String = "$providerId/$modelId"

    companion object {
        const val ADAPTER_ID = "reasonix"
        private const val CONFIG_KEY = "config"
        private const val ENV_KEY = "env"
        private const val CONFIG_PATH = "/root/.reasonix/config.toml"
        private const val ENV_PATH = "/root/.reasonix/.env"
        private const val SKILL_ROOT = "/root/.reasonix/skills"
        private const val AGENTS_SKILL_ROOT = "/root/.agents/skills"
        private const val AGENT_SKILL_ROOT = "/root/.agent/skills"
        private const val CLAUDE_SKILL_ROOT = "/root/.claude/skills"
        private const val DEFAULT_MODEL_KEY = "default_model"
        private const val PROVIDERS_KEY = "providers"
        private const val NAME_KEY = "name"
        private const val KIND_KEY = "kind"
        private const val OPENAI_KIND = "openai"
        private const val BASE_URL_KEY = "base_url"
        private const val MODELS_KEY = "models"
        private const val MODEL_KEY = "model"
        private const val API_KEY_ENV = "api_key_env"
        private const val TOOL_APPROVAL_CONFIG_ID = "tool_approval"
        private const val MODE_NORMAL = "normal"
        private val SAFE_IMPORT_REFERENCE = Regex("kite-import:import-[A-Za-z0-9-]{8,80}")
        private val SAFE_ENV_NAME = Regex("[A-Za-z_][A-Za-z0-9_]{0,127}")
        private val REASONIX_PERMISSION_LEVELS = mapOf(
            "ask" to AgentPermissionLevel.Approval,
            "auto" to AgentPermissionLevel.Lenient,
            "yolo" to AgentPermissionLevel.Full,
        )
        private val REASONIX_REASONING_CONTROL = AgentReasoningControl(
            standardReasoningLevelMappings() + AgentReasoningNativeMapping("auto", AgentReasoningMode.Adaptive),
        )
        private val REASONIX_COLLABORATION_MODES = linkedMapOf(
            MODE_NORMAL to AgentMode(MODE_NORMAL, "常规", "正常协作并按当前权限执行任务"),
            "plan" to AgentMode("plan", "计划", "先分析和规划，再由用户确认后继续"),
            "goal" to AgentMode("goal", "目标", "围绕明确目标持续推进并保持任务状态"),
        )
    }
}
