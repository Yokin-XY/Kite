package com.kite.app.agent.config.native

import android.content.Context
import blue.endless.jankson.Jankson
import blue.endless.jankson.JsonArray
import blue.endless.jankson.JsonElement
import blue.endless.jankson.JsonGrammar
import blue.endless.jankson.JsonObject
import blue.endless.jankson.JsonPrimitive
import com.kite.app.agent.config.AgentConfigAdapter
import com.kite.app.agent.config.AgentConfigApplyRequest
import com.kite.app.agent.config.AgentConfigApplyResult
import com.kite.app.agent.config.AgentConfigCapabilities
import com.kite.app.agent.config.AgentConfigDiscovery
import com.kite.app.agent.config.AgentConfigDiscoveryState
import com.kite.app.agent.config.AgentConfigReadResult
import com.kite.app.agent.config.AgentConfigValidationProblem
import com.kite.app.agent.config.AgentCredentialOwnership
import com.kite.app.agent.config.AgentCredentialPresence
import com.kite.app.agent.config.AgentLiveConfigSnapshot
import com.kite.app.agent.config.AgentConfigScope
import com.kite.app.agent.config.AgentCoreDocumentListResult
import com.kite.app.agent.config.AgentCoreDocumentReadResult
import com.kite.app.agent.config.AgentCoreDocumentSemantics
import com.kite.app.agent.config.AgentCoreDocumentWriteRequest
import com.kite.app.agent.config.AgentCoreDocumentWriteResult
import com.kite.app.agent.config.AgentMcpDraft
import com.kite.app.agent.config.AgentMcpEnvironmentReference
import com.kite.app.agent.config.AgentMcpOperation
import com.kite.app.agent.config.AgentMcpSummary
import com.kite.app.agent.config.AgentMcpTransport
import com.kite.app.agent.config.AgentPersistentConfigCapability
import com.kite.app.agent.config.AgentPersistentConfigChange
import com.kite.app.agent.config.AgentPermissionProfileSummary
import com.kite.app.agent.config.AgentPermissionLevel
import com.kite.app.agent.config.AgentSessionPermissionControl
import com.kite.app.agent.config.AgentSessionPermissionHandling
import com.kite.app.agent.config.AgentProviderCredentialChange
import com.kite.app.agent.config.AgentProviderDraft
import com.kite.app.agent.config.AgentProviderModelSummary
import com.kite.app.agent.config.AgentProviderSummary
import com.kite.app.agent.config.AgentReasoningControl
import com.kite.app.agent.config.AgentReasoningControls
import com.kite.app.agent.config.AgentSkillSummary
import com.kite.app.agent.config.AgentSkillActivation
import com.kite.app.agent.config.AgentSkillOperation
import com.kite.app.agent.config.AgentSessionConfigurationEffect
import com.kite.app.agent.config.AtomicConfigFileStore
import com.kite.app.agent.config.AtomicConfigFileUpdate
import com.kite.app.agent.config.AtomicConfigFilesWriteResult
import com.kite.app.agent.config.ConfigFileRevision
import com.kite.app.agent.config.ContainerAgentConfigProjection
import com.kite.app.agent.config.NativeAgentCoreDocumentSpec
import com.kite.app.agent.config.NativeAgentCoreDocumentStore
import com.kite.app.agent.config.mediatedSessionPermissionControl
import com.kite.app.agent.contract.AgentConfigCategory
import com.kite.app.agent.contract.AgentConfigChoice
import com.kite.app.agent.contract.AgentConfigOption
import com.kite.app.foundation.contracts.ContainerRecord
import com.kite.app.foundation.workspace.WorkSurfaceRuntimeBridge
import org.tomlj.Toml
import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor
import java.io.File
import java.net.URI
import java.security.MessageDigest
import java.util.LinkedHashMap

private fun projectCoreDocument(
    workspacePath: String?,
    id: String,
    displayName: String,
    fileName: String,
    priorityDescription: String,
    warning: String? = null,
): NativeAgentCoreDocumentSpec? = NativeAgentCoreDocumentStore.projectPath(workspacePath, fileName)?.let { path ->
    NativeAgentCoreDocumentSpec(
        id = id,
        displayName = displayName,
        fileName = fileName,
        containerPath = path,
        scope = AgentConfigScope.Project,
        semantics = AgentCoreDocumentSemantics.SupplementalInstructions,
        priorityDescription = priorityDescription,
        warning = warning,
    )
}

/** 五种 Agent 共享文件事务与安全投影，但各自保留真实原生 schema。 */
internal abstract class NativeAgentConfigAdapter(
    context: Context,
    final override val adapterId: String,
    private val paths: LinkedHashMap<String, String>,
    private val primaryKey: String,
    protected val containerProvider: () -> ContainerRecord? = {
        WorkSurfaceRuntimeBridge.getSavedContainer(context.applicationContext)
    },
    protected val fileStore: AtomicConfigFileStore = AtomicConfigFileStore()
) : AgentConfigAdapter {
    protected data class NativeState(
        val defaultModel: String?,
        val providers: List<AgentProviderSummary>,
        val credentialPresence: AgentCredentialPresence,
        val warnings: List<String> = emptyList(),
        val activeProviderId: String? = null,
        val activePermissionProfileId: String? = null,
        val permissionProfiles: List<AgentPermissionProfileSummary> = emptyList(),
        val mcpServers: List<AgentMcpSummary> = emptyList(),
        val skills: List<AgentSkillSummary> = emptyList(),
        val runtimeReloadRequired: Boolean = false
    )

    private data class LiveState(
        val projections: Map<String, ContainerAgentConfigProjection.FileProjection>,
        val visibleBytes: Map<String, ByteArray>,
        val writeRevisions: Map<String, ConfigFileRevision>,
        val snapshot: AgentLiveConfigSnapshot
    )

    protected val projection = ContainerAgentConfigProjection(containerProvider)
    private val coreDocumentStore by lazy(LazyThreadSafetyMode.NONE) {
        NativeAgentCoreDocumentStore(projection::resolve, fileStore)
    }

    override fun capabilities(): AgentConfigCapabilities = nativeCapabilities()

    protected open fun nativeCapabilities(): AgentConfigCapabilities = AgentConfigCapabilities(
        supported = setOf(
            AgentPersistentConfigCapability.DefaultModel,
            AgentPersistentConfigCapability.Provider,
            AgentPersistentConfigCapability.ProviderProfiles,
            AgentPersistentConfigCapability.CredentialStatus
        ),
        credentialOwnership = AgentCredentialOwnership.AgentOwned
    )

    override fun normalizeSessionConfiguration(options: List<AgentConfigOption>): List<AgentConfigOption> =
        options.map { option ->
            if (option !is AgentConfigOption.Select || option.category != AgentConfigCategory.Model) return@map option
            option.copy(choices = option.choices.map(::groupProviderModelChoice))
        }

    private fun groupProviderModelChoice(choice: AgentConfigChoice): AgentConfigChoice {
        if (!choice.groupId.isNullOrBlank() || !choice.groupName.isNullOrBlank()) return choice
        val separator = choice.value.indexOf('/')
        if (separator <= 0 || separator == choice.value.lastIndex) return choice
        val provider = choice.value.substring(0, separator)
        val model = choice.value.substring(separator + 1)
        return choice.copy(
            name = choice.name.substringAfter('/', model),
            groupId = provider,
            groupName = choice.name.substringBefore('/').takeIf(String::isNotBlank) ?: provider
        )
    }

    override fun defaultModelChange(option: AgentConfigOption.Select): AgentPersistentConfigChange.SetDefaultModel? {
        if (option.category != AgentConfigCategory.Model || option.currentValue.isBlank()) return null
        if (option.choices.none { it.value == option.currentValue }) return null
        return AgentPersistentConfigChange.SetDefaultModel(option.currentValue)
    }

    override suspend fun discover(agentId: String): AgentConfigDiscovery {
        val target = projection.resolve(requireNotNull(paths[primaryKey]))
            ?: return AgentConfigDiscovery(
                agentId = agentId,
                adapterId = adapterId,
                state = AgentConfigDiscoveryState.NoRuntime,
                warnings = listOf("Kite 运行容器尚未创建")
            )
        return AgentConfigDiscovery(
            agentId = agentId,
            adapterId = adapterId,
            state = AgentConfigDiscoveryState.Ready,
            displayLocation = target.containerPath,
            writable = target.writeFile.let { file ->
                if (file.exists()) file.isFile && file.canWrite()
                else file.parentFile?.let { it.isDirectory && it.canWrite() || !it.exists() } == true
            }
        )
    }

    override suspend fun readLive(agentId: String): AgentConfigReadResult {
        val discovery = discover(agentId)
        if (discovery.state != AgentConfigDiscoveryState.Ready) return AgentConfigReadResult.Unavailable(discovery)
        return runCatching { AgentConfigReadResult.Ready(readState(agentId).snapshot) }
            .getOrElse { AgentConfigReadResult.Failed("无法读取 ${displayName()} 原生配置") }
    }

    protected open fun nativeCoreDocuments(workspacePath: String?): List<NativeAgentCoreDocumentSpec> = emptyList()

    override suspend fun listCoreDocuments(
        agentId: String,
        workspacePath: String?
    ): AgentCoreDocumentListResult {
        val discovery = discover(agentId)
        if (discovery.state != AgentConfigDiscoveryState.Ready) {
            return AgentCoreDocumentListResult.Unavailable(discovery)
        }
        return runCatching {
            AgentCoreDocumentListResult.Ready(coreDocumentStore.descriptors(nativeCoreDocuments(workspacePath)))
        }.getOrElse { AgentCoreDocumentListResult.Failed("无法读取 ${displayName()} 核心设定") }
    }

    override suspend fun readCoreDocument(
        agentId: String,
        documentId: String,
        workspacePath: String?
    ): AgentCoreDocumentReadResult {
        val discovery = discover(agentId)
        if (discovery.state != AgentConfigDiscoveryState.Ready) {
            return AgentCoreDocumentReadResult.Unavailable(discovery)
        }
        return runCatching {
            coreDocumentStore.read(nativeCoreDocuments(workspacePath), documentId)
                ?.let(AgentCoreDocumentReadResult::Ready)
                ?: AgentCoreDocumentReadResult.Missing()
        }.getOrElse { AgentCoreDocumentReadResult.Failed("无法读取 ${displayName()} 核心设定") }
    }

    override suspend fun writeCoreDocument(
        request: AgentCoreDocumentWriteRequest
    ): AgentCoreDocumentWriteResult {
        val discovery = discover(request.agentId)
        if (discovery.state != AgentConfigDiscoveryState.Ready) {
            return AgentCoreDocumentWriteResult.Unavailable(discovery)
        }
        return runCatching {
            coreDocumentStore.write(nativeCoreDocuments(request.workspacePath), request)
        }.getOrElse { AgentCoreDocumentWriteResult.Failed("无法写入 ${displayName()} 核心设定", restored = true) }
    }

    override fun validate(request: AgentConfigApplyRequest): List<AgentConfigValidationProblem> = buildList {
        if (!SAFE_ID.matches(request.agentId)) add(problem("agentId", "Agent ID 格式无效"))
        if (request.expectedRevision.isBlank()) add(problem("expectedRevision", "缺少配置 revision"))
        if (request.changes.isEmpty()) add(problem("changes", "没有待应用的配置变更"))
        request.changes.forEachIndexed { index, change ->
            when (change) {
                is AgentPersistentConfigChange.SetDefaultModel -> change.modelId?.let { model ->
                    if (!SAFE_MODEL_ID.matches(model)) add(problem("changes[$index].modelId", "模型 ID 格式无效"))
                }
                is AgentPersistentConfigChange.SelectProvider -> {
                    if (!SAFE_ID.matches(change.providerId)) {
                        add(problem("changes[$index].providerId", "供应商 ID 格式无效"))
                    }
                    if (!SAFE_MODEL_ID.matches(change.modelId)) {
                        add(problem("changes[$index].modelId", "模型 ID 格式无效"))
                    }
                }
                is AgentPersistentConfigChange.ConfigureProvider ->
                    validateProvider("changes[$index]", change.provider, change.credential, this)
                is AgentPersistentConfigChange.RemoveProvider -> {
                    if (!SAFE_ID.matches(change.providerId)) add(problem("changes[$index].providerId", "供应商 ID 格式无效"))
                }
                else -> validateNativeChange(index, change, this)
            }
        }
        addAll(validateNativeRequest(request))
    }

    protected open fun validateNativeChange(
        index: Int,
        change: AgentPersistentConfigChange,
        output: MutableList<AgentConfigValidationProblem>
    ) {
        output += problem("changes[$index]", "${displayName()} 当前不支持这项配置")
    }

    protected open fun validateNativeRequest(request: AgentConfigApplyRequest): List<AgentConfigValidationProblem> = emptyList()

    /** 文件夹型 Skill 等不属于主配置文件事务的原生变更由具体适配器处理。 */
    protected open suspend fun applyExternalChanges(
        request: AgentConfigApplyRequest,
        before: AgentLiveConfigSnapshot
    ): AgentConfigApplyResult? = null

    /** 把 Skill 目录等额外事实纳入乐观并发 revision。 */
    protected open fun nativeRevisionInputs(): List<Pair<String, String>> = emptyList()

    override suspend fun apply(request: AgentConfigApplyRequest): AgentConfigApplyResult {
        val problems = validate(request)
        if (problems.isNotEmpty()) return AgentConfigApplyResult.Rejected(problems)
        val discovery = discover(request.agentId)
        if (discovery.state != AgentConfigDiscoveryState.Ready) return AgentConfigApplyResult.Unavailable(discovery)
        val before = runCatching { readState(request.agentId) }.getOrElse {
            return AgentConfigApplyResult.Failed("无法读取当前 ${displayName()} 配置", restored = true)
        }
        if (before.snapshot.revision != request.expectedRevision) {
            return AgentConfigApplyResult.Conflict(before.snapshot.revision)
        }
        request.changes.filterIsInstance<AgentPersistentConfigChange.SelectProvider>().firstOrNull()?.let { selection ->
            val provider = before.snapshot.providers.firstOrNull { it.id == selection.providerId }
                ?: return AgentConfigApplyResult.Rejected(
                    listOf(problem("providerId", "供应商不存在，请重新读取配置"))
                )
            if (provider.models.none { it.id == selection.modelId }) {
                return AgentConfigApplyResult.Rejected(
                    listOf(problem("modelId", "该供应商没有这个模型，请重新读取配置"))
                )
            }
        }
        applyExternalChanges(request, before.snapshot)?.let { return it }
        val next = runCatching { mutate(before.visibleBytes, request.changes) }.getOrElse {
            return AgentConfigApplyResult.Rejected(listOf(problem("document", it.message ?: "原生配置变更无效")))
        }
        if (next.keys.any { it !in paths }) {
            return AgentConfigApplyResult.Rejected(listOf(problem("document", "适配器返回了未登记的配置文件")))
        }
        val changed = next.filter { (key, bytes) -> !bytes.contentEquals(before.visibleBytes[key] ?: ByteArray(0)) }
        if (changed.isEmpty()) return AgentConfigApplyResult.Applied(before.snapshot, backupReference = null)
        if (currentRevision() != request.expectedRevision) return AgentConfigApplyResult.Conflict(currentRevision())

        val updates = changed.map { (key, bytes) ->
            val target = requireNotNull(before.projections[key])
            AtomicConfigFileUpdate(
                target = target.writeFile,
                expectedRevision = requireNotNull(before.writeRevisions[key]),
                nextBytes = bytes,
                validate = { validateBytes(key, it) }
            )
        }
        return when (val result = fileStore.replaceAll(updates)) {
            is AtomicConfigFilesWriteResult.Applied -> {
                val refreshed = runCatching { readState(request.agentId).snapshot }.getOrElse {
                    return AgentConfigApplyResult.Failed("配置已写入，但无法重新读取", restored = false)
                }
                AgentConfigApplyResult.Applied(refreshed, result.backupReferences.firstOrNull())
            }
            is AtomicConfigFilesWriteResult.Conflict -> AgentConfigApplyResult.Conflict(currentRevision())
            is AtomicConfigFilesWriteResult.Rejected ->
                AgentConfigApplyResult.Rejected(listOf(problem("document", result.message)))
            is AtomicConfigFilesWriteResult.Failed -> AgentConfigApplyResult.Failed(result.message, result.restored)
        }
    }

    protected abstract fun displayName(): String

    protected abstract fun decode(files: Map<String, ByteArray>): NativeState

    protected abstract fun mutate(
        files: Map<String, ByteArray>,
        changes: List<AgentPersistentConfigChange>
    ): Map<String, ByteArray>

    protected abstract fun validateBytes(key: String, bytes: ByteArray): String?

    private fun readState(agentId: String): LiveState {
        val projections = paths.mapValues { (_, path) -> requireNotNull(projection.resolve(path)) }
        val visibleBytes = projections.mapValues { (_, target) -> fileStore.read(target.readFile).bytes }
        val writeRevisions = projections.mapValues { (_, target) -> fileStore.read(target.writeFile).revision }
        val native = decode(visibleBytes)
        val primary = requireNotNull(projections[primaryKey])
        return LiveState(
            projections = projections,
            visibleBytes = visibleBytes,
            writeRevisions = writeRevisions,
            snapshot = AgentLiveConfigSnapshot(
                agentId = agentId,
                adapterId = adapterId,
                revision = revision(projections),
                displayLocation = primary.containerPath,
                activeProviderId = native.activeProviderId,
                defaultModel = native.defaultModel,
                providerIds = native.providers.map(AgentProviderSummary::id),
                providers = native.providers,
                activePermissionProfileId = native.activePermissionProfileId,
                permissionProfiles = native.permissionProfiles,
                mcpServers = native.mcpServers,
                skills = native.skills,
                credentialPresence = native.credentialPresence,
                warnings = native.warnings,
                runtimeReloadRequired = native.runtimeReloadRequired
            )
        )
    }

    private fun currentRevision(): String {
        val projections = paths.mapValues { (_, path) -> requireNotNull(projection.resolve(path)) }
        return revision(projections)
    }

    private fun revision(projections: Map<String, ContainerAgentConfigProjection.FileProjection>): String {
        val digest = MessageDigest.getInstance("SHA-256")
        projections.toSortedMap().forEach { (key, target) ->
            digest.update(key.toByteArray())
            digest.update(target.containerPath.toByteArray())
            digest.update((target.viewId ?: "base").toByteArray())
            digest.update(fileStore.read(target.readFile).revision.value.toByteArray())
            digest.update(fileStore.read(target.writeFile).revision.value.toByteArray())
        }
        nativeRevisionInputs().sortedBy { it.first }.forEach { (key, value) ->
            digest.update(key.toByteArray())
            digest.update(value.toByteArray())
        }
        return "sha256:" + digest.digest().joinToString("") { "%02x".format(it) }
    }

    protected fun isSafeNativeId(value: String): Boolean = SAFE_ID.matches(value)

    private fun validateProvider(
        path: String,
        provider: AgentProviderDraft,
        credential: AgentProviderCredentialChange,
        output: MutableList<AgentConfigValidationProblem>
    ) {
        if (!SAFE_ID.matches(provider.id)) output += problem("$path.provider.id", "供应商 ID 格式无效")
        val name = provider.displayName.orEmpty()
        if (name.length > MAX_NAME || name.any(Char::isISOControl)) {
            output += problem("$path.provider.displayName", "供应商名称格式无效")
        }
        val uri = runCatching { URI(provider.baseUrl.trim()) }.getOrNull()
        if (uri == null || uri.scheme !in setOf("http", "https") || uri.host.isNullOrBlank() || uri.userInfo != null) {
            output += problem("$path.provider.baseUrl", "供应商 URL 必须是有效的 HTTP 或 HTTPS 地址")
        }
        if (provider.models.isEmpty()) output += problem("$path.provider.models", "至少保留一个可用模型")
        if (provider.models.size > MAX_MODELS) output += problem("$path.provider.models", "模型数量过多")
        if (provider.models.map { it.id.trim() }.distinct().size != provider.models.size) {
            output += problem("$path.provider.models", "模型 ID 不能重复")
        }
        provider.models.forEachIndexed { index, model ->
            if (!SAFE_MODEL_ID.matches(model.id.trim())) output += problem("$path.provider.models[$index].id", "模型 ID 格式无效")
            if (model.displayName.length > MAX_NAME || model.displayName.any(Char::isISOControl)) {
                output += problem("$path.provider.models[$index].displayName", "模型名称格式无效")
            }
        }
        if (credential is AgentProviderCredentialChange.Replace &&
            (credential.secret.isBlank() || credential.secret.length > MAX_SECRET || credential.secret.any(Char::isISOControl))
        ) {
            output += problem("$path.credential", "API Key 格式无效")
        }
    }

    protected fun overallCredential(providers: List<AgentProviderSummary>): AgentCredentialPresence = when {
        providers.any { it.credentialPresence == AgentCredentialPresence.Present } -> AgentCredentialPresence.Present
        providers.any { it.credentialPresence == AgentCredentialPresence.Unknown } -> AgentCredentialPresence.Unknown
        providers.isEmpty() -> AgentCredentialPresence.Missing
        else -> AgentCredentialPresence.Missing
    }

    protected fun problem(field: String, message: String) = AgentConfigValidationProblem(field, message)

    private companion object {
        val SAFE_ID = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,127}")
        val SAFE_MODEL_ID = Regex("[^\\s\\p{Cc}]{1,384}")
        const val MAX_NAME = 256
        const val MAX_MODELS = 256
        const val MAX_SECRET = 16 * 1024
    }
}

/** JSON/JSONC/JSON5 原生配置的公共语法工具；具体字段仍由各 Agent 实现。 */
internal abstract class JanksonNativeAgentConfigAdapter(
    context: Context,
    adapterId: String,
    paths: LinkedHashMap<String, String>,
    primaryKey: String,
    containerProvider: () -> ContainerRecord?,
    fileStore: AtomicConfigFileStore = AtomicConfigFileStore()
) : NativeAgentConfigAdapter(context, adapterId, paths, primaryKey, containerProvider, fileStore) {
    protected val parser: Jankson = Jankson.builder().build()

    protected fun parse(bytes: ByteArray): JsonObject =
        if (bytes.isEmpty() || bytes.toString(Charsets.UTF_8).isBlank()) JsonObject()
        else parser.load(bytes.toString(Charsets.UTF_8))

    protected fun serialize(document: JsonObject): ByteArray =
        (document.toJson(JSON5_GRAMMAR, 0).trimEnd() + "\n").toByteArray(Charsets.UTF_8)

    protected fun JsonObject.string(key: String): String? =
        (get(key) as? JsonPrimitive)?.getValue()?.let { it as? String }

    protected fun JsonObject.objectCopy(key: String): JsonObject = getObject(key)?.clone() ?: JsonObject()

    protected fun putPreserving(target: JsonObject, key: String, value: JsonElement) {
        target.put(key, value, target.getComment(key))
    }

    protected fun setNested(root: JsonObject, keys: List<String>, value: JsonElement) {
        require(keys.isNotEmpty())
        var current = root
        keys.dropLast(1).forEach { key ->
            val child = current.objectCopy(key)
            putPreserving(current, key, child)
            current = child
        }
        putPreserving(current, keys.last(), value)
    }

    protected fun removeNested(root: JsonObject, keys: List<String>) {
        if (keys.isEmpty()) return
        var current = root
        keys.dropLast(1).forEach { key -> current = current.getObject(key) ?: return }
        current.remove(keys.last())
    }

    protected fun providerModelRef(providerId: String, modelId: String): String = "$providerId/$modelId"

    override fun validateBytes(key: String, bytes: ByteArray): String? = runCatching { parse(bytes); null }
        .getOrElse { "Agent 原生 JSON/JSONC 配置格式无效" }

    private companion object {
        val JSON5_GRAMMAR: JsonGrammar = JsonGrammar.builder()
            .withComments(true)
            .printWhitespace(true)
            .printCommas(true)
            .printTrailingCommas(false)
            .bareSpecialNumerics(false)
            .bareRootObject(false)
            .printUnquotedKeys(false)
            .build()
    }
}

/** Kimi Code 把 MCP 与 Skill 放在独立用户目录；供应商模型 schema 需要额外上下文字段，本阶段不伪造。 */
internal class KimiCodeAgentConfigAdapter(
    context: Context,
    containerProvider: () -> ContainerRecord? = {
        WorkSurfaceRuntimeBridge.getSavedContainer(context.applicationContext)
    },
    fileStore: AtomicConfigFileStore = AtomicConfigFileStore(),
) : JanksonNativeAgentConfigAdapter(
    context,
    ADAPTER_ID,
    linkedMapOf(MCP_KEY to MCP_PATH),
    MCP_KEY,
    containerProvider,
    fileStore,
) {
    private val skillDirectory = NativeAgentSkillDirectory(
        projection::resolve,
        listOf(SKILL_ROOT, AGENTS_SKILL_ROOT),
    )

    override fun displayName(): String = "Kimi Code"

    override fun nativeCoreDocuments(workspacePath: String?): List<NativeAgentCoreDocumentSpec> = buildList {
        add(NativeAgentCoreDocumentSpec(
            id = "kimi-global-agents",
            displayName = "Kimi 全局说明",
            fileName = "AGENTS.md",
            containerPath = GLOBAL_AGENTS_PATH,
            scope = AgentConfigScope.User,
            semantics = AgentCoreDocumentSemantics.SupplementalInstructions,
            priorityDescription = "Kimi Code 跨工作区加载的专属说明",
        ))
        add(NativeAgentCoreDocumentSpec(
            id = "kimi-system",
            displayName = "主 Agent 系统提示",
            fileName = "SYSTEM.md",
            containerPath = SYSTEM_PATH,
            scope = AgentConfigScope.User,
            semantics = AgentCoreDocumentSemantics.FullSystemPromptReplacement,
            priorityDescription = "非空时替换内置主 Agent 系统提示；显式 Agent 覆盖仍优先",
            warning = "这不是附加说明。非空内容会完整替换 Kimi Code 内置主 Agent 的系统提示；" +
                "如仍需默认能力或 Plugin 指令，请按 Kimi 模板规则显式保留。",
        ))
        projectCoreDocument(
            workspacePath,
            id = "kimi-project-agents",
            displayName = "当前项目说明",
            fileName = "AGENTS.md",
            priorityDescription = "当前工作目录的项目说明，比全局说明更贴近本项目",
        )?.let(::add)
    }

    override fun nativeCapabilities(): AgentConfigCapabilities = AgentConfigCapabilities(
        supported = setOf(
            AgentPersistentConfigCapability.Mcp,
            AgentPersistentConfigCapability.Skill,
            AgentPersistentConfigCapability.CoreDocuments,
        ),
        credentialOwnership = AgentCredentialOwnership.AgentOwned,
        mcpOperations = setOf(
            AgentMcpOperation.Create,
            AgentMcpOperation.Edit,
            AgentMcpOperation.Enable,
            AgentMcpOperation.Disable,
            AgentMcpOperation.Remove,
        ),
        mcpTransports = setOf(
            AgentMcpTransport.Stdio,
            AgentMcpTransport.StreamableHttp,
            AgentMcpTransport.Sse,
        ),
        skillOperations = setOf(
            AgentSkillOperation.Import,
            AgentSkillOperation.Enable,
            AgentSkillOperation.ManualOnly,
            AgentSkillOperation.Remove,
        ),
    )

    override fun decode(files: Map<String, ByteArray>): NativeState {
        val root = parse(files.getValue(MCP_KEY))
        return NativeState(
            defaultModel = null,
            providers = emptyList(),
            credentialPresence = AgentCredentialPresence.NotApplicable,
            mcpServers = kimiMcpServers(root.getObject(MCP_SERVERS_KEY)),
            skills = skillDirectory.summaries(
                activation = ::kimiSkillActivation,
                activationOperations = setOf(AgentSkillOperation.Enable, AgentSkillOperation.ManualOnly),
            ),
        )
    }

    override fun validateNativeChange(
        index: Int,
        change: AgentPersistentConfigChange,
        output: MutableList<AgentConfigValidationProblem>,
    ) {
        when (change) {
            is AgentPersistentConfigChange.ConfigureMcpServer -> validateKimiMcpDraft(index, change.server, output)
            is AgentPersistentConfigChange.SetMcpEnabled -> validateKimiId(index, "serverId", change.serverId, output)
            is AgentPersistentConfigChange.RemoveMcpServer -> validateKimiId(index, "serverId", change.serverId, output)
            is AgentPersistentConfigChange.InstallSkill -> {
                validateKimiId(index, "skillId", change.skillId, output)
                if (!SAFE_IMPORT_REFERENCE.matches(change.sourceReference)) {
                    output += problem("changes[$index].sourceReference", "Skill 来源引用格式无效")
                }
            }
            is AgentPersistentConfigChange.SetSkillActivation -> {
                validateKimiId(index, "skillId", change.skillId, output)
                if (change.activation !in KIMI_SKILL_ACTIVATIONS) {
                    output += problem("changes[$index].activation", "Kimi Code 只支持启用或仅手动调用")
                }
            }
            is AgentPersistentConfigChange.RemoveSkill -> validateKimiId(index, "skillId", change.skillId, output)
            else -> super.validateNativeChange(index, change, output)
        }
    }

    override suspend fun applyExternalChanges(
        request: AgentConfigApplyRequest,
        before: AgentLiveConfigSnapshot,
    ): AgentConfigApplyResult? {
        val skillChanges = request.changes.filter {
            it is AgentPersistentConfigChange.InstallSkill ||
                it is AgentPersistentConfigChange.RemoveSkill ||
                it is AgentPersistentConfigChange.SetSkillActivation
        }
        if (skillChanges.isEmpty()) return null
        if (request.changes.size != 1 || skillChanges.size != 1) {
            return AgentConfigApplyResult.Rejected(
                listOf(problem("changes", "Skill 变更一次只能执行一项，不能和 MCP 配置混合")),
            )
        }
        when (val change = skillChanges.single()) {
            is AgentPersistentConfigChange.SetSkillActivation -> skillDirectory.applyTextChange(
                skillId = change.skillId,
                transform = { setKimiSkillManualOnly(it, change.activation == AgentSkillActivation.ManualOnly) },
                validate = { text -> if (frontmatterRange(text) == null) "SKILL.md 缺少有效 frontmatter" else null },
            )?.let { return it }
            else -> skillDirectory.applyFileChange(change)?.let { return it }
        }
        return refreshedApplyResult(request.agentId)
    }

    override fun nativeRevisionInputs(): List<Pair<String, String>> = skillDirectory.revisionInputs()

    override fun mutate(
        files: Map<String, ByteArray>,
        changes: List<AgentPersistentConfigChange>,
    ): Map<String, ByteArray> {
        val root = parse(files.getValue(MCP_KEY)).clone()
        changes.forEach { change ->
            when (change) {
                is AgentPersistentConfigChange.ConfigureMcpServer -> configureKimiMcp(root, change.server)
                is AgentPersistentConfigChange.SetMcpEnabled -> {
                    val servers = root.objectCopy(MCP_SERVERS_KEY)
                    val server = servers.objectCopy(change.serverId)
                    putPreserving(server, ENABLED_KEY, JsonPrimitive.of(change.enabled))
                    putPreserving(servers, change.serverId, server)
                    putPreserving(root, MCP_SERVERS_KEY, servers)
                }
                is AgentPersistentConfigChange.RemoveMcpServer -> {
                    val servers = root.objectCopy(MCP_SERVERS_KEY)
                    servers.remove(change.serverId)
                    putPreserving(root, MCP_SERVERS_KEY, servers)
                }
                else -> Unit
            }
        }
        return mapOf(MCP_KEY to serialize(root))
    }

    private fun kimiMcpServers(section: JsonObject?): List<AgentMcpSummary> = section?.entries
        .orEmpty()
        .mapNotNull { (id, value) ->
            val server = value as? JsonObject ?: return@mapNotNull null
            val command = server.string(COMMAND_KEY)
            val url = server.string(URL_KEY)
            val transport = when {
                !command.isNullOrBlank() -> AgentMcpTransport.Stdio
                !url.isNullOrBlank() && server.string(TRANSPORT_KEY) == SSE_TYPE -> AgentMcpTransport.Sse
                !url.isNullOrBlank() -> AgentMcpTransport.StreamableHttp
                else -> AgentMcpTransport.Unknown
            }
            val enabled = (server[ENABLED_KEY] as? JsonPrimitive)?.getValue() as? Boolean ?: true
            AgentMcpSummary(
                id = id,
                kind = when (transport) {
                    AgentMcpTransport.Stdio -> STDIO_TYPE
                    AgentMcpTransport.Sse -> SSE_TYPE
                    AgentMcpTransport.StreamableHttp -> HTTP_TYPE
                    else -> "unknown"
                },
                enabled = enabled,
                transport = transport,
                command = command,
                arguments = (server[ARGS_KEY] as? JsonArray)
                    ?.mapNotNull { (it as? JsonPrimitive)?.getValue() as? String }
                    .orEmpty(),
                workingDirectory = server.string(CWD_KEY),
                url = url,
                headerReferences = listOfNotNull(
                    server.string(BEARER_ENV_KEY)?.takeIf(SAFE_ENV_NAME::matches)?.let {
                        AgentMcpEnvironmentReference(AUTHORIZATION_HEADER, it)
                    },
                ),
                scope = AgentConfigScope.User,
                allowedOperations = buildSet {
                    if (transport != AgentMcpTransport.Unknown) add(AgentMcpOperation.Edit)
                    add(if (enabled) AgentMcpOperation.Disable else AgentMcpOperation.Enable)
                    add(AgentMcpOperation.Remove)
                },
            )
        }
        .sortedBy(AgentMcpSummary::id)

    private fun configureKimiMcp(root: JsonObject, draft: AgentMcpDraft) {
        val servers = root.objectCopy(MCP_SERVERS_KEY)
        val server = servers.objectCopy(draft.id)
        when (draft.transport) {
            AgentMcpTransport.Stdio -> {
                putPreserving(server, COMMAND_KEY, JsonPrimitive.of(requireNotNull(draft.command).trim()))
                val args = JsonArray()
                draft.arguments.forEach { args.add(JsonPrimitive.of(it)) }
                putPreserving(server, ARGS_KEY, args)
                draft.workingDirectory?.trim()?.takeIf(String::isNotBlank)?.let {
                    putPreserving(server, CWD_KEY, JsonPrimitive.of(it))
                } ?: server.remove(CWD_KEY)
                listOf(URL_KEY, TRANSPORT_KEY, HEADERS_KEY, BEARER_ENV_KEY).forEach(server::remove)
            }
            AgentMcpTransport.StreamableHttp,
            AgentMcpTransport.Sse -> {
                putPreserving(server, URL_KEY, JsonPrimitive.of(requireNotNull(draft.url).trim()))
                if (draft.transport == AgentMcpTransport.Sse) {
                    putPreserving(server, TRANSPORT_KEY, JsonPrimitive.of(SSE_TYPE))
                } else {
                    server.remove(TRANSPORT_KEY)
                }
                listOf(COMMAND_KEY, ARGS_KEY, CWD_KEY, ENV_KEY).forEach(server::remove)
                val bearer = draft.headerReferences.singleOrNull()?.environmentVariable
                if (bearer == null) server.remove(BEARER_ENV_KEY)
                else putPreserving(server, BEARER_ENV_KEY, JsonPrimitive.of(bearer))
            }
            else -> error("已由 Kimi MCP 校验限制传输类型")
        }
        putPreserving(server, ENABLED_KEY, JsonPrimitive.of(draft.enabled))
        putPreserving(servers, draft.id, server)
        putPreserving(root, MCP_SERVERS_KEY, servers)
    }

    private fun validateKimiMcpDraft(
        index: Int,
        draft: AgentMcpDraft,
        output: MutableList<AgentConfigValidationProblem>,
    ) {
        validateKimiId(index, "server.id", draft.id, output)
        when (draft.transport) {
            AgentMcpTransport.Stdio -> {
                if (draft.command.isNullOrBlank() || draft.command.any(Char::isISOControl)) {
                    output += problem("changes[$index].server.command", "本地 MCP 必须提供有效命令")
                }
                if (draft.environmentReferences.isNotEmpty() || draft.headerReferences.isNotEmpty()) {
                    output += problem("changes[$index].server.references", "Kimi Code 的 stdio MCP 不支持安全变量引用编辑")
                }
            }
            AgentMcpTransport.StreamableHttp,
            AgentMcpTransport.Sse -> {
                val uri = runCatching { URI(draft.url.orEmpty()) }.getOrNull()
                if (uri == null || uri.scheme !in setOf("http", "https") || uri.host.isNullOrBlank() || uri.userInfo != null) {
                    output += problem("changes[$index].server.url", "远程 MCP 必须提供有效 HTTP 或 HTTPS 地址")
                }
                if (draft.environmentReferences.isNotEmpty()) {
                    output += problem("changes[$index].server.environmentReferences", "远程 MCP 不使用进程环境变量映射")
                }
                if (draft.headerReferences.size > 1 || draft.headerReferences.any { it.name != AUTHORIZATION_HEADER }) {
                    output += problem("changes[$index].server.headerReferences", "Kimi Code 只支持 Authorization bearer 环境变量引用")
                }
            }
            else -> output += problem("changes[$index].server.transport", "Kimi Code 不支持这个 MCP 传输类型")
        }
        if (draft.arguments.size > MAX_MCP_ITEMS) {
            output += problem("changes[$index].server.arguments", "MCP 参数数量过多")
        }
        draft.headerReferences.forEach { reference ->
            if (!SAFE_ENV_NAME.matches(reference.environmentVariable)) {
                output += problem("changes[$index].server.headerReferences", "MCP 环境变量名称无效")
            }
        }
    }

    private fun validateKimiId(
        index: Int,
        field: String,
        value: String,
        output: MutableList<AgentConfigValidationProblem>,
    ) {
        if (!isSafeNativeId(value)) output += problem("changes[$index].$field", "ID 格式无效")
    }

    private fun kimiSkillActivation(entry: NativeAgentSkillDirectory.Entry): AgentSkillActivation {
        val text = runCatching { File(entry.directory, SKILL_FILE).readText() }.getOrNull()
            ?: return AgentSkillActivation.Unknown
        val range = frontmatterRange(text) ?: return AgentSkillActivation.Unknown
        val lines = text.replace("\r\n", "\n").replace('\r', '\n').split('\n')
        return if ((range.first + 1 until range.last).any { index ->
                val line = lines[index]
                SKILL_MANUAL_FIELD.matchEntire(line.trim())?.groupValues?.get(2)?.equals("true", ignoreCase = true) == true
            }
        ) AgentSkillActivation.ManualOnly else AgentSkillActivation.Enabled
    }

    private fun setKimiSkillManualOnly(text: String, manualOnly: Boolean): String {
        val normalized = text.replace("\r\n", "\n").replace('\r', '\n')
        val range = frontmatterRange(normalized) ?: error("SKILL.md 缺少有效 frontmatter")
        val lines = normalized.split('\n').toMutableList()
        val existing = range.first + 1 until range.last
        val index = existing.firstOrNull { SKILL_MANUAL_FIELD.matches(lines[it].trim()) }
        val rendered = "disableModelInvocation: $manualOnly"
        if (index == null) lines.add(range.last, rendered) else lines[index] = rendered
        return lines.joinToString("\n").trimEnd() + "\n"
    }

    private fun frontmatterRange(text: String): IntRange? {
        val lines = text.replace("\r\n", "\n").replace('\r', '\n').split('\n')
        if (lines.firstOrNull()?.trim() != "---") return null
        val end = (1 until minOf(lines.size, MAX_FRONTMATTER_LINES)).firstOrNull { lines[it].trim() == "---" }
            ?: return null
        return 0..end
    }

    private suspend fun refreshedApplyResult(agentId: String): AgentConfigApplyResult = when (val refreshed = readLive(agentId)) {
        is AgentConfigReadResult.Ready -> AgentConfigApplyResult.Applied(refreshed.snapshot, backupReference = null)
        is AgentConfigReadResult.Failed -> AgentConfigApplyResult.Failed(refreshed.message, restored = false)
        is AgentConfigReadResult.Unavailable -> AgentConfigApplyResult.Unavailable(refreshed.discovery)
    }

    companion object {
        const val ADAPTER_ID = "kimi-code"
        private const val MCP_KEY = "mcp"
        private const val MCP_PATH = "/root/.kimi-code/mcp.json"
        private const val MCP_SERVERS_KEY = "mcpServers"
        private const val SKILL_ROOT = "/root/.kimi-code/skills"
        private const val AGENTS_SKILL_ROOT = "/root/.agents/skills"
        private const val GLOBAL_AGENTS_PATH = "/root/.kimi-code/AGENTS.md"
        private const val SYSTEM_PATH = "/root/.kimi-code/SYSTEM.md"
        private const val SKILL_FILE = "SKILL.md"
        private const val COMMAND_KEY = "command"
        private const val ARGS_KEY = "args"
        private const val CWD_KEY = "cwd"
        private const val ENV_KEY = "env"
        private const val URL_KEY = "url"
        private const val TRANSPORT_KEY = "transport"
        private const val HEADERS_KEY = "headers"
        private const val BEARER_ENV_KEY = "bearerTokenEnvVar"
        private const val ENABLED_KEY = "enabled"
        private const val STDIO_TYPE = "stdio"
        private const val HTTP_TYPE = "http"
        private const val SSE_TYPE = "sse"
        private const val AUTHORIZATION_HEADER = "Authorization"
        private const val MAX_MCP_ITEMS = 64
        private const val MAX_FRONTMATTER_LINES = 160
        private val SAFE_IMPORT_REFERENCE = Regex("kite-import:import-[A-Za-z0-9-]{8,80}")
        private val SAFE_ENV_NAME = Regex("[A-Za-z_][A-Za-z0-9_]{0,127}")
        private val SKILL_MANUAL_FIELD = Regex(
            "(disableModelInvocation|disable-model-invocation|disable_model_invocation)\\s*:\\s*(true|false)",
            RegexOption.IGNORE_CASE,
        )
        private val KIMI_SKILL_ACTIVATIONS = setOf(AgentSkillActivation.Enabled, AgentSkillActivation.ManualOnly)
    }
}

internal class MiMoCodeAgentConfigAdapter(
    context: Context,
    containerProvider: () -> ContainerRecord? = {
        WorkSurfaceRuntimeBridge.getSavedContainer(context.applicationContext)
    },
    fileStore: AtomicConfigFileStore = AtomicConfigFileStore()
) : JanksonNativeAgentConfigAdapter(
    context,
    ADAPTER_ID,
    linkedMapOf(CONFIG_KEY to CONFIG_PATH),
    CONFIG_KEY,
    containerProvider,
    fileStore
) {
    private val skillDirectory = NativeAgentSkillDirectory(
        projection::resolve,
        listOf(SKILL_ROOT, AGENTS_SKILL_ROOT, CLAUDE_SKILL_ROOT, CODEX_SKILL_ROOT, OPENCODE_SKILL_ROOT),
    )

    override fun displayName(): String = "MiMo Code"

    override fun nativeCoreDocuments(workspacePath: String?): List<NativeAgentCoreDocumentSpec> = buildList {
        add(NativeAgentCoreDocumentSpec(
            id = "mimo-global-agents",
            displayName = "MiMo 全局说明",
            fileName = "AGENTS.md",
            containerPath = GLOBAL_AGENTS_PATH,
            scope = AgentConfigScope.User,
            semantics = AgentCoreDocumentSemantics.SupplementalInstructions,
            priorityDescription = "MiMo Code 跨工作区读取的全局说明",
        ))
        projectCoreDocument(
            workspacePath,
            id = "mimo-project-agents",
            displayName = "当前项目说明",
            fileName = "AGENTS.md",
            priorityDescription = "当前工作目录优先匹配的项目说明",
        )?.let(::add)
    }

    override fun nativeCapabilities(): AgentConfigCapabilities = AgentConfigCapabilities(
        supported = setOf(
            AgentPersistentConfigCapability.DefaultModel,
            AgentPersistentConfigCapability.Provider,
            AgentPersistentConfigCapability.ProviderProfiles,
            AgentPersistentConfigCapability.CredentialStatus,
            AgentPersistentConfigCapability.Mcp,
            AgentPersistentConfigCapability.Skill,
            AgentPersistentConfigCapability.CoreDocuments,
        ),
        credentialOwnership = AgentCredentialOwnership.AgentOwned,
        mcpOperations = setOf(
            AgentMcpOperation.Create,
            AgentMcpOperation.Edit,
            AgentMcpOperation.Enable,
            AgentMcpOperation.Disable,
            AgentMcpOperation.Remove,
        ),
        mcpTransports = setOf(AgentMcpTransport.Stdio, AgentMcpTransport.RemoteHttpOrSse),
        skillOperations = setOf(AgentSkillOperation.Import, AgentSkillOperation.Remove),
    )

    override fun decode(files: Map<String, ByteArray>): NativeState {
        val root = parse(files.getValue(CONFIG_KEY))
        val providers = root.getObject("provider")?.entries.orEmpty().mapNotNull { (id, value) ->
            val provider = value as? JsonObject ?: return@mapNotNull null
            val options = provider.getObject("options")
            val credential = if (!options?.string("apiKey").isNullOrBlank()) AgentCredentialPresence.Present
            else AgentCredentialPresence.Missing
            AgentProviderSummary(
                id = id,
                displayName = provider.string("name")?.takeIf(String::isNotBlank) ?: id,
                baseUrl = options?.string("baseURL"),
                models = provider.getObject("models")?.entries.orEmpty().map { (modelId, modelValue) ->
                    AgentProviderModelSummary(
                        modelId,
                        (modelValue as? JsonObject)?.string("name")?.takeIf(String::isNotBlank) ?: modelId
                    )
                }.sortedBy(AgentProviderModelSummary::id),
                credentialPresence = credential
            )
        }.sortedBy(AgentProviderSummary::id)
        val defaultModel = root.string("model")
        return NativeState(
            defaultModel,
            providers,
            overallCredential(providers),
            activeProviderId = defaultModel
                ?.substringBefore('/')
                ?.takeIf { active -> providers.any { it.id == active } },
            mcpServers = mimoMcpServers(root.getObject(MCP_KEY)),
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
            is AgentPersistentConfigChange.ConfigureMcpServer -> validateMiMoMcpDraft(index, change.server, output)
            is AgentPersistentConfigChange.SetMcpEnabled -> validateMiMoId(index, "serverId", change.serverId, output)
            is AgentPersistentConfigChange.RemoveMcpServer -> validateMiMoId(index, "serverId", change.serverId, output)
            is AgentPersistentConfigChange.InstallSkill -> {
                validateMiMoId(index, "skillId", change.skillId, output)
                if (!SAFE_IMPORT_REFERENCE.matches(change.sourceReference)) {
                    output += problem("changes[$index].sourceReference", "Skill 来源引用格式无效")
                }
            }
            is AgentPersistentConfigChange.RemoveSkill -> validateMiMoId(index, "skillId", change.skillId, output)
            else -> super.validateNativeChange(index, change, output)
        }
    }

    override suspend fun applyExternalChanges(
        request: AgentConfigApplyRequest,
        before: AgentLiveConfigSnapshot,
    ): AgentConfigApplyResult? {
        val fileChanges = request.changes.filter {
            it is AgentPersistentConfigChange.InstallSkill || it is AgentPersistentConfigChange.RemoveSkill
        }
        if (fileChanges.isEmpty()) return null
        if (request.changes.size != 1 || fileChanges.size != 1) {
            return AgentConfigApplyResult.Rejected(
                listOf(problem("changes", "Skill 文件变更一次只能执行一项，不能和其他配置混合")),
            )
        }
        skillDirectory.applyFileChange(fileChanges.single())?.let { return it }
        return refreshedApplyResult(request.agentId)
    }

    override fun nativeRevisionInputs(): List<Pair<String, String>> = skillDirectory.revisionInputs()

    override fun mutate(files: Map<String, ByteArray>, changes: List<AgentPersistentConfigChange>): Map<String, ByteArray> {
        val root = parse(files.getValue(CONFIG_KEY)).clone()
        if (!root.containsKey("$" + "schema")) putPreserving(root, "$" + "schema", JsonPrimitive.of(SCHEMA_URL))
        changes.forEach { change ->
            when (change) {
                is AgentPersistentConfigChange.SetDefaultModel -> if (change.modelId == null) root.remove("model")
                    else putPreserving(root, "model", JsonPrimitive.of(change.modelId))
                is AgentPersistentConfigChange.SelectProvider ->
                    putPreserving(root, "model", JsonPrimitive.of(providerModelRef(change.providerId, change.modelId)))
                is AgentPersistentConfigChange.ConfigureProvider -> configure(root, change.provider, change.credential)
                is AgentPersistentConfigChange.RemoveProvider -> {
                    val section = root.objectCopy("provider")
                    section.remove(change.providerId)
                    putPreserving(root, "provider", section)
                    if (root.string("model")?.startsWith("${change.providerId}/") == true) root.remove("model")
                }
                is AgentPersistentConfigChange.ConfigureMcpServer -> configureMiMoMcp(root, change.server)
                is AgentPersistentConfigChange.SetMcpEnabled -> {
                    val section = root.objectCopy(MCP_KEY)
                    val server = section.objectCopy(change.serverId)
                    putPreserving(server, ENABLED_KEY, JsonPrimitive.of(change.enabled))
                    putPreserving(section, change.serverId, server)
                    putPreserving(root, MCP_KEY, section)
                }
                is AgentPersistentConfigChange.RemoveMcpServer -> {
                    val section = root.objectCopy(MCP_KEY)
                    section.remove(change.serverId)
                    putPreserving(root, MCP_KEY, section)
                }
                else -> Unit
            }
        }
        return mapOf(CONFIG_KEY to serialize(root))
    }

    private fun mimoMcpServers(section: JsonObject?): List<AgentMcpSummary> = section?.entries
        .orEmpty()
        .mapNotNull { (id, value) ->
            val server = value as? JsonObject ?: return@mapNotNull null
            val type = server.string(TYPE_KEY) ?: "unknown"
            val command = (server[COMMAND_KEY] as? JsonArray)
                ?.mapNotNull { (it as? JsonPrimitive)?.getValue() as? String }
                .orEmpty()
            val enabled = (server[ENABLED_KEY] as? JsonPrimitive)?.getValue() as? Boolean ?: true
            val transport = when (type) {
                LOCAL_TYPE -> AgentMcpTransport.Stdio
                REMOTE_TYPE -> AgentMcpTransport.RemoteHttpOrSse
                else -> AgentMcpTransport.Unknown
            }
            AgentMcpSummary(
                id = id,
                kind = type,
                enabled = enabled,
                transport = transport,
                command = command.firstOrNull(),
                arguments = command.drop(1),
                url = server.string(URL_KEY),
                environmentReferences = mimoReferences(server.getObject(ENVIRONMENT_KEY)),
                headerReferences = mimoReferences(server.getObject(HEADERS_KEY)),
                scope = AgentConfigScope.User,
                allowedOperations = buildSet {
                    if (transport != AgentMcpTransport.Unknown) add(AgentMcpOperation.Edit)
                    add(if (enabled) AgentMcpOperation.Disable else AgentMcpOperation.Enable)
                    add(AgentMcpOperation.Remove)
                },
            )
        }
        .sortedBy(AgentMcpSummary::id)

    private fun mimoReferences(values: JsonObject?): List<AgentMcpEnvironmentReference> = values?.entries
        .orEmpty()
        .mapNotNull { (name, value) ->
            val raw = (value as? JsonPrimitive)?.getValue() as? String ?: return@mapNotNull null
            val variable = MIMO_ENV_REFERENCE.matchEntire(raw)?.groupValues?.get(1) ?: return@mapNotNull null
            AgentMcpEnvironmentReference(name, variable)
        }
        .sortedBy(AgentMcpEnvironmentReference::name)

    private fun configureMiMoMcp(root: JsonObject, draft: AgentMcpDraft) {
        val section = root.objectCopy(MCP_KEY)
        val server = section.objectCopy(draft.id)
        when (draft.transport) {
            AgentMcpTransport.Stdio -> {
                putPreserving(server, TYPE_KEY, JsonPrimitive.of(LOCAL_TYPE))
                val command = JsonArray()
                command.add(JsonPrimitive.of(requireNotNull(draft.command).trim()))
                draft.arguments.forEach { command.add(JsonPrimitive.of(it)) }
                putPreserving(server, COMMAND_KEY, command)
                server.remove(URL_KEY)
                server.remove(HEADERS_KEY)
                mergeMiMoReferences(server, ENVIRONMENT_KEY, draft.environmentReferences)
            }
            AgentMcpTransport.RemoteHttpOrSse -> {
                putPreserving(server, TYPE_KEY, JsonPrimitive.of(REMOTE_TYPE))
                putPreserving(server, URL_KEY, JsonPrimitive.of(requireNotNull(draft.url).trim()))
                server.remove(COMMAND_KEY)
                server.remove(ENVIRONMENT_KEY)
                mergeMiMoReferences(server, HEADERS_KEY, draft.headerReferences)
            }
            else -> error("已由 MiMo MCP 校验限制传输类型")
        }
        putPreserving(server, ENABLED_KEY, JsonPrimitive.of(draft.enabled))
        putPreserving(section, draft.id, server)
        putPreserving(root, MCP_KEY, section)
    }

    private fun mergeMiMoReferences(
        server: JsonObject,
        field: String,
        references: List<AgentMcpEnvironmentReference>,
    ) {
        val values = server.objectCopy(field)
        val desired = references.map(AgentMcpEnvironmentReference::name).toSet()
        values.entries.toList().forEach { (name, value) ->
            val raw = (value as? JsonPrimitive)?.getValue() as? String
            if (raw != null && MIMO_ENV_REFERENCE.matches(raw) && name !in desired) values.remove(name)
        }
        references.forEach { reference ->
            putPreserving(values, reference.name, JsonPrimitive.of("{env:${reference.environmentVariable}}"))
        }
        if (values.isEmpty()) server.remove(field) else putPreserving(server, field, values)
    }

    private fun validateMiMoMcpDraft(
        index: Int,
        draft: AgentMcpDraft,
        output: MutableList<AgentConfigValidationProblem>,
    ) {
        validateMiMoId(index, "server.id", draft.id, output)
        when (draft.transport) {
            AgentMcpTransport.Stdio -> if (draft.command.isNullOrBlank() || draft.command.any(Char::isISOControl)) {
                output += problem("changes[$index].server.command", "本地 MCP 必须提供有效命令")
            }
            AgentMcpTransport.RemoteHttpOrSse -> {
                val uri = runCatching { URI(draft.url.orEmpty()) }.getOrNull()
                if (uri == null || uri.scheme !in setOf("http", "https") || uri.host.isNullOrBlank() || uri.userInfo != null) {
                    output += problem("changes[$index].server.url", "远程 MCP 必须提供有效 HTTP 或 HTTPS 地址")
                }
            }
            else -> output += problem("changes[$index].server.transport", "MiMo Code 不支持这个 MCP 传输类型")
        }
        if (draft.arguments.size > MAX_MCP_ITEMS || draft.environmentReferences.size > MAX_MCP_ITEMS || draft.headerReferences.size > MAX_MCP_ITEMS) {
            output += problem("changes[$index].server", "MCP 参数、环境变量或 Header 数量过多")
        }
        (draft.environmentReferences + draft.headerReferences).forEach { reference ->
            if (reference.name.isBlank() || reference.name.any(Char::isISOControl) || !SAFE_ENV_NAME.matches(reference.environmentVariable)) {
                output += problem("changes[$index].server.references", "MCP 环境变量引用格式无效")
            }
        }
    }

    private fun validateMiMoId(
        index: Int,
        field: String,
        value: String,
        output: MutableList<AgentConfigValidationProblem>,
    ) {
        if (!isSafeNativeId(value)) output += problem("changes[$index].$field", "ID 格式无效")
    }

    private suspend fun refreshedApplyResult(agentId: String): AgentConfigApplyResult = when (val refreshed = readLive(agentId)) {
        is AgentConfigReadResult.Ready -> AgentConfigApplyResult.Applied(refreshed.snapshot, backupReference = null)
        is AgentConfigReadResult.Failed -> AgentConfigApplyResult.Failed(refreshed.message, restored = false)
        is AgentConfigReadResult.Unavailable -> AgentConfigApplyResult.Unavailable(refreshed.discovery)
    }

    private fun configure(root: JsonObject, provider: AgentProviderDraft, credential: AgentProviderCredentialChange) {
        val providers = root.objectCopy("provider")
        val entry = providers.objectCopy(provider.id)
        provider.displayName?.takeIf(String::isNotBlank)?.let { putPreserving(entry, "name", JsonPrimitive.of(it.trim())) }
        if (!entry.containsKey("npm")) putPreserving(entry, "npm", JsonPrimitive.of("@ai-sdk/openai-compatible"))
        val options = entry.objectCopy("options")
        putPreserving(options, "baseURL", JsonPrimitive.of(provider.baseUrl.trim()))
        when (credential) {
            AgentProviderCredentialChange.Keep -> Unit
            is AgentProviderCredentialChange.Replace -> putPreserving(options, "apiKey", JsonPrimitive.of(credential.secret))
            AgentProviderCredentialChange.Remove -> options.remove("apiKey")
        }
        putPreserving(entry, "options", options)
        val models = JsonObject()
        provider.models.forEach { model ->
            val modelEntry = JsonObject()
            if (model.displayName.isNotBlank() && model.displayName != model.id) {
                modelEntry.put("name", JsonPrimitive.of(model.displayName.trim()))
            }
            models.put(model.id.trim(), modelEntry)
        }
        putPreserving(entry, "models", models)
        putPreserving(providers, provider.id, entry)
        putPreserving(root, "provider", providers)
        if (root.string("model").isNullOrBlank()) {
            putPreserving(root, "model", JsonPrimitive.of(providerModelRef(provider.id, provider.models.first().id.trim())))
        }
    }

    companion object {
        const val ADAPTER_ID = "mimo-code"
        private const val CONFIG_KEY = "config"
        private const val CONFIG_PATH = "/root/.config/mimocode/mimocode.jsonc"
        private const val SCHEMA_URL = "https://mimo.xiaomi.com/mimocode/config.json"
        private const val MCP_KEY = "mcp"
        private const val TYPE_KEY = "type"
        private const val COMMAND_KEY = "command"
        private const val ENVIRONMENT_KEY = "environment"
        private const val URL_KEY = "url"
        private const val HEADERS_KEY = "headers"
        private const val ENABLED_KEY = "enabled"
        private const val LOCAL_TYPE = "local"
        private const val REMOTE_TYPE = "remote"
        private const val SKILL_ROOT = "/root/.config/mimocode/skills"
        private const val AGENTS_SKILL_ROOT = "/root/.agents/skills"
        private const val GLOBAL_AGENTS_PATH = "/root/.config/mimocode/AGENTS.md"
        private const val CLAUDE_SKILL_ROOT = "/root/.claude/skills"
        private const val CODEX_SKILL_ROOT = "/root/.codex/skills"
        private const val OPENCODE_SKILL_ROOT = "/root/.opencode/skills"
        private const val MAX_MCP_ITEMS = 64
        private val SAFE_IMPORT_REFERENCE = Regex("kite-import:import-[A-Za-z0-9-]{8,80}")
        private val SAFE_ENV_NAME = Regex("[A-Za-z_][A-Za-z0-9_]{0,127}")
        private val MIMO_ENV_REFERENCE = Regex("\\{env:([A-Za-z_][A-Za-z0-9_]*)\\}")
    }
}

internal class OpenClawAgentConfigAdapter(
    context: Context,
    containerProvider: () -> ContainerRecord? = {
        WorkSurfaceRuntimeBridge.getSavedContainer(context.applicationContext)
    },
    fileStore: AtomicConfigFileStore = AtomicConfigFileStore()
) : JanksonNativeAgentConfigAdapter(
    context,
    ADAPTER_ID,
    linkedMapOf(CONFIG_KEY to CONFIG_PATH),
    CONFIG_KEY,
    containerProvider,
    fileStore
) {
    private val skillDirectory = NativeAgentSkillDirectory(
        project = projection::resolve,
        roots = listOf(SKILL_ROOT, AGENTS_SKILL_ROOT),
        configurationId = ::openClawSkillKey,
    )

    override fun displayName(): String = "OpenClaw"

    override fun reasoningControl(): AgentReasoningControl = AgentReasoningControls.OpenClaw

    override fun nativeCoreDocuments(workspacePath: String?): List<NativeAgentCoreDocumentSpec> {
        val workspace = openClawWorkspacePath()
        fun document(
            id: String,
            name: String,
            fileName: String,
            semantics: AgentCoreDocumentSemantics,
            priority: String,
        ) = NativeAgentCoreDocumentSpec(
            id = id,
            displayName = name,
            fileName = fileName,
            containerPath = "$workspace/$fileName",
            scope = AgentConfigScope.Workspace,
            semantics = semantics,
            priorityDescription = priority,
        )
        return listOf(
            document(
                "openclaw-agents",
                "工作方式",
                "AGENTS.md",
                AgentCoreDocumentSemantics.SupplementalInstructions,
                "每次会话注入的工作区操作说明",
            ),
            document(
                "openclaw-soul",
                "人格与边界",
                "SOUL.md",
                AgentCoreDocumentSemantics.Persona,
                "定义 Agent 的语气、立场和行为边界",
            ),
            document(
                "openclaw-user",
                "用户资料",
                "USER.md",
                AgentCoreDocumentSemantics.UserProfile,
                "作为工作区用户资料随会话注入",
            ),
            document(
                "openclaw-identity",
                "Agent 身份",
                "IDENTITY.md",
                AgentCoreDocumentSemantics.Identity,
                "定义 Agent 的名称、形象和身份表达",
            ),
        )
    }

    private fun openClawWorkspacePath(): String {
        val configFile = projection.resolve(CONFIG_PATH)
            ?.readFile
            ?.takeIf(File::isFile)
        val configured = configFile
            ?.let { file -> parse(file.readBytes()) }
            ?.getObject("agents")
            ?.getObject("defaults")
            ?.string("workspace")
            ?.trim()
            ?.takeIf(String::isNotBlank)
        val expanded = when {
            configured == null -> DEFAULT_WORKSPACE_PATH
            configured == "~" -> "/root"
            configured.startsWith("~/") -> "/root/${configured.removePrefix("~/")}"
            configured.startsWith("\$HOME/") -> "/root/${configured.removePrefix("\$HOME/")}"
            configured.startsWith("\${HOME}/") -> "/root/${configured.removePrefix("\${HOME}/")}"
            else -> configured
        }
        return requireNotNull(NativeAgentCoreDocumentStore.normalizeContainerPath(expanded)) {
            "OpenClaw workspace 不是安全的容器绝对路径"
        }
    }

    override fun nativeCapabilities(): AgentConfigCapabilities = AgentConfigCapabilities(
        supported = setOf(
            AgentPersistentConfigCapability.DefaultModel,
            AgentPersistentConfigCapability.Provider,
            AgentPersistentConfigCapability.ProviderProfiles,
            AgentPersistentConfigCapability.CredentialStatus,
            AgentPersistentConfigCapability.Mcp,
            AgentPersistentConfigCapability.Skill,
            AgentPersistentConfigCapability.CoreDocuments,
        ),
        credentialOwnership = AgentCredentialOwnership.AgentOwned,
        mcpOperations = setOf(
            AgentMcpOperation.Create,
            AgentMcpOperation.Edit,
            AgentMcpOperation.Enable,
            AgentMcpOperation.Disable,
            AgentMcpOperation.Remove,
        ),
        mcpTransports = setOf(
            AgentMcpTransport.Stdio,
            AgentMcpTransport.StreamableHttp,
            AgentMcpTransport.Sse,
        ),
        skillOperations = setOf(
            AgentSkillOperation.Import,
            AgentSkillOperation.Enable,
            AgentSkillOperation.Disable,
            AgentSkillOperation.Remove,
        ),
    )

    override fun decode(files: Map<String, ByteArray>): NativeState {
        val root = parse(files.getValue(CONFIG_KEY))
        val providers = root.getObject("models")?.getObject("providers")?.entries.orEmpty().mapNotNull { (id, value) ->
            val provider = value as? JsonObject ?: return@mapNotNull null
            val apiKey = provider["apiKey"]
            AgentProviderSummary(
                id = id,
                displayName = id,
                baseUrl = provider.string("baseUrl"),
                models = (provider["models"] as? JsonArray).orEmptyObjects().mapNotNull { model ->
                    model.string("id")?.takeIf(String::isNotBlank)?.let { modelId ->
                        AgentProviderModelSummary(modelId, model.string("name")?.takeIf(String::isNotBlank) ?: modelId)
                    }
                },
                credentialPresence = if (apiKey != null) AgentCredentialPresence.Present else AgentCredentialPresence.Missing
            )
        }.sortedBy(AgentProviderSummary::id)
        val modelValue = root.getObject("agents")?.getObject("defaults")?.get("model")
        val defaultModel = when (modelValue) {
            is JsonPrimitive -> modelValue.getValue() as? String
            is JsonObject -> modelValue.string("primary")
            else -> null
        }
        return NativeState(
            defaultModel,
            providers,
            overallCredential(providers),
            activeProviderId = defaultModel
                ?.substringBefore('/')
                ?.takeIf { active -> providers.any { it.id == active } },
            mcpServers = openClawMcpServers(root.getObject(MCP_KEY)?.getObject(MCP_SERVERS_KEY)),
            skills = skillDirectory.summaries(
                activation = { entry -> openClawSkillActivation(root, entry.configurationId) },
                activationOperations = setOf(AgentSkillOperation.Enable, AgentSkillOperation.Disable),
            ),
        )
    }

    override fun validateNativeChange(
        index: Int,
        change: AgentPersistentConfigChange,
        output: MutableList<AgentConfigValidationProblem>,
    ) {
        when (change) {
            is AgentPersistentConfigChange.ConfigureMcpServer -> validateOpenClawMcpDraft(index, change.server, output)
            is AgentPersistentConfigChange.SetMcpEnabled -> validateOpenClawId(index, "serverId", change.serverId, output)
            is AgentPersistentConfigChange.RemoveMcpServer -> validateOpenClawId(index, "serverId", change.serverId, output)
            is AgentPersistentConfigChange.InstallSkill -> {
                validateOpenClawId(index, "skillId", change.skillId, output)
                if (!SAFE_IMPORT_REFERENCE.matches(change.sourceReference)) {
                    output += problem("changes[$index].sourceReference", "Skill 来源引用格式无效")
                }
            }
            is AgentPersistentConfigChange.SetSkillActivation -> {
                validateOpenClawId(index, "skillId", change.skillId, output)
                if (change.activation !in OPENCLAW_SKILL_ACTIVATIONS) {
                    output += problem("changes[$index].activation", "OpenClaw 只支持启用或停用 Skill")
                }
            }
            is AgentPersistentConfigChange.RemoveSkill -> validateOpenClawId(index, "skillId", change.skillId, output)
            else -> super.validateNativeChange(index, change, output)
        }
    }

    override suspend fun applyExternalChanges(
        request: AgentConfigApplyRequest,
        before: AgentLiveConfigSnapshot,
    ): AgentConfigApplyResult? {
        val fileChanges = request.changes.filter {
            it is AgentPersistentConfigChange.InstallSkill || it is AgentPersistentConfigChange.RemoveSkill
        }
        if (fileChanges.isEmpty()) return null
        if (request.changes.size != 1 || fileChanges.size != 1) {
            return AgentConfigApplyResult.Rejected(
                listOf(problem("changes", "Skill 文件变更一次只能执行一项，不能和其他配置混合")),
            )
        }
        skillDirectory.applyFileChange(fileChanges.single())?.let { return it }
        return refreshedApplyResult(request.agentId)
    }

    override fun nativeRevisionInputs(): List<Pair<String, String>> = skillDirectory.revisionInputs()

    override fun mutate(files: Map<String, ByteArray>, changes: List<AgentPersistentConfigChange>): Map<String, ByteArray> {
        val root = parse(files.getValue(CONFIG_KEY)).clone()
        changes.forEach { change ->
            when (change) {
                is AgentPersistentConfigChange.SetDefaultModel -> setDefault(root, change.modelId)
                is AgentPersistentConfigChange.SelectProvider ->
                    setDefault(root, providerModelRef(change.providerId, change.modelId))
                is AgentPersistentConfigChange.ConfigureProvider -> configure(root, change.provider, change.credential)
                is AgentPersistentConfigChange.RemoveProvider -> removeProvider(root, change.providerId)
                is AgentPersistentConfigChange.ConfigureMcpServer -> configureOpenClawMcp(root, change.server)
                is AgentPersistentConfigChange.SetMcpEnabled -> {
                    val mcp = root.objectCopy(MCP_KEY)
                    val servers = mcp.objectCopy(MCP_SERVERS_KEY)
                    val server = servers.objectCopy(change.serverId)
                    putPreserving(server, ENABLED_KEY, JsonPrimitive.of(change.enabled))
                    putPreserving(servers, change.serverId, server)
                    putPreserving(mcp, MCP_SERVERS_KEY, servers)
                    putPreserving(root, MCP_KEY, mcp)
                }
                is AgentPersistentConfigChange.RemoveMcpServer -> {
                    val mcp = root.objectCopy(MCP_KEY)
                    val servers = mcp.objectCopy(MCP_SERVERS_KEY)
                    servers.remove(change.serverId)
                    putPreserving(mcp, MCP_SERVERS_KEY, servers)
                    putPreserving(root, MCP_KEY, mcp)
                }
                is AgentPersistentConfigChange.SetSkillActivation -> setOpenClawSkillActivation(
                    root,
                    change.skillId,
                    change.activation,
                )
                else -> Unit
            }
        }
        return mapOf(CONFIG_KEY to serialize(root))
    }

    private fun openClawMcpServers(section: JsonObject?): List<AgentMcpSummary> = section?.entries
        .orEmpty()
        .mapNotNull { (id, value) ->
            val server = value as? JsonObject ?: return@mapNotNull null
            val command = server.string(COMMAND_KEY)
            val url = server.string(URL_KEY)
            val nativeTransport = server.string(TRANSPORT_KEY)
            val transport = when {
                !command.isNullOrBlank() -> AgentMcpTransport.Stdio
                url.isNullOrBlank() -> AgentMcpTransport.Unknown
                nativeTransport == SSE_TYPE -> AgentMcpTransport.Sse
                nativeTransport == STREAMABLE_HTTP_TYPE || nativeTransport.isNullOrBlank() ->
                    AgentMcpTransport.StreamableHttp
                else -> AgentMcpTransport.Unknown
            }
            val enabled = (server[ENABLED_KEY] as? JsonPrimitive)?.getValue() as? Boolean ?: true
            AgentMcpSummary(
                id = id,
                kind = nativeTransport ?: if (command != null) STDIO_TYPE else "remote",
                enabled = enabled,
                transport = transport,
                command = command,
                arguments = (server[ARGS_KEY] as? JsonArray)
                    ?.mapNotNull { (it as? JsonPrimitive)?.getValue() as? String }
                    .orEmpty(),
                url = url,
                environmentReferences = openClawReferences(server.getObject(ENV_KEY)),
                headerReferences = openClawReferences(server.getObject(HEADERS_KEY)),
                scope = AgentConfigScope.User,
                allowedOperations = buildSet {
                    if (transport != AgentMcpTransport.Unknown) add(AgentMcpOperation.Edit)
                    add(if (enabled) AgentMcpOperation.Disable else AgentMcpOperation.Enable)
                    add(AgentMcpOperation.Remove)
                },
            )
        }
        .sortedBy(AgentMcpSummary::id)

    private fun openClawReferences(values: JsonObject?): List<AgentMcpEnvironmentReference> = values?.entries
        .orEmpty()
        .mapNotNull { (name, value) ->
            val raw = (value as? JsonPrimitive)?.getValue() as? String ?: return@mapNotNull null
            val variable = openClawReferenceVariable(raw) ?: return@mapNotNull null
            AgentMcpEnvironmentReference(name, variable)
        }
        .sortedBy(AgentMcpEnvironmentReference::name)

    private fun openClawReferenceVariable(value: String): String? =
        OPENCLAW_ENV_REFERENCE.matchEntire(value.trim())?.groupValues?.get(1)
            ?: OPENCLAW_BEARER_REFERENCE.matchEntire(value.trim())?.groupValues?.get(1)

    private fun configureOpenClawMcp(root: JsonObject, draft: AgentMcpDraft) {
        val mcp = root.objectCopy(MCP_KEY)
        val servers = mcp.objectCopy(MCP_SERVERS_KEY)
        val server = servers.objectCopy(draft.id)
        when (draft.transport) {
            AgentMcpTransport.Stdio -> {
                putPreserving(server, TRANSPORT_KEY, JsonPrimitive.of(STDIO_TYPE))
                putPreserving(server, COMMAND_KEY, JsonPrimitive.of(requireNotNull(draft.command).trim()))
                val arguments = JsonArray()
                draft.arguments.forEach { arguments.add(JsonPrimitive.of(it)) }
                putPreserving(server, ARGS_KEY, arguments)
                server.remove(URL_KEY)
                server.remove(HEADERS_KEY)
                mergeOpenClawReferences(server, ENV_KEY, draft.environmentReferences, authorizationBearer = false)
            }
            AgentMcpTransport.StreamableHttp, AgentMcpTransport.Sse -> {
                putPreserving(
                    server,
                    TRANSPORT_KEY,
                    JsonPrimitive.of(
                        if (draft.transport == AgentMcpTransport.Sse) SSE_TYPE else STREAMABLE_HTTP_TYPE,
                    ),
                )
                putPreserving(server, URL_KEY, JsonPrimitive.of(requireNotNull(draft.url).trim()))
                server.remove(COMMAND_KEY)
                server.remove(ARGS_KEY)
                server.remove(ENV_KEY)
                server.remove(CWD_KEY)
                mergeOpenClawReferences(server, HEADERS_KEY, draft.headerReferences, authorizationBearer = true)
            }
            else -> error("已由 OpenClaw MCP 校验限制传输类型")
        }
        putPreserving(server, ENABLED_KEY, JsonPrimitive.of(draft.enabled))
        putPreserving(servers, draft.id, server)
        putPreserving(mcp, MCP_SERVERS_KEY, servers)
        putPreserving(root, MCP_KEY, mcp)
    }

    private fun mergeOpenClawReferences(
        server: JsonObject,
        field: String,
        references: List<AgentMcpEnvironmentReference>,
        authorizationBearer: Boolean,
    ) {
        val values = server.objectCopy(field)
        val desired = references.associateBy(AgentMcpEnvironmentReference::name)
        values.entries.toList().forEach { (name, value) ->
            val raw = (value as? JsonPrimitive)?.getValue() as? String
            val currentVariable = raw?.let(::openClawReferenceVariable)
            if (currentVariable != null && name !in desired) values.remove(name)
        }
        references.forEach { reference ->
            val current = (values[reference.name] as? JsonPrimitive)?.getValue() as? String
            if (current?.let(::openClawReferenceVariable) != reference.environmentVariable) {
                val next = if (authorizationBearer && reference.name.equals(AUTHORIZATION_HEADER, ignoreCase = true)) {
                    "Bearer \${${reference.environmentVariable}}"
                } else {
                    "\${${reference.environmentVariable}}"
                }
                putPreserving(values, reference.name, JsonPrimitive.of(next))
            }
        }
        if (values.isEmpty()) server.remove(field) else putPreserving(server, field, values)
    }

    private fun validateOpenClawMcpDraft(
        index: Int,
        draft: AgentMcpDraft,
        output: MutableList<AgentConfigValidationProblem>,
    ) {
        validateOpenClawId(index, "server.id", draft.id, output)
        when (draft.transport) {
            AgentMcpTransport.Stdio -> if (draft.command.isNullOrBlank() || draft.command.any(Char::isISOControl)) {
                output += problem("changes[$index].server.command", "本地 MCP 必须提供有效命令")
            }
            AgentMcpTransport.StreamableHttp, AgentMcpTransport.Sse -> {
                val uri = runCatching { URI(draft.url.orEmpty()) }.getOrNull()
                if (uri == null || uri.scheme !in setOf("http", "https") || uri.host.isNullOrBlank() || uri.userInfo != null) {
                    output += problem("changes[$index].server.url", "远程 MCP 必须提供有效 HTTP 或 HTTPS 地址")
                }
            }
            else -> output += problem("changes[$index].server.transport", "OpenClaw 不支持这个 MCP 传输类型")
        }
        if (draft.arguments.size > MAX_MCP_ITEMS || draft.environmentReferences.size > MAX_MCP_ITEMS || draft.headerReferences.size > MAX_MCP_ITEMS) {
            output += problem("changes[$index].server", "MCP 参数、环境变量或 Header 数量过多")
        }
        (draft.environmentReferences + draft.headerReferences).forEach { reference ->
            if (reference.name.isBlank() || reference.name.any(Char::isISOControl) || !SAFE_ENV_NAME.matches(reference.environmentVariable)) {
                output += problem("changes[$index].server.references", "MCP 环境变量引用格式无效")
            }
        }
    }

    private fun validateOpenClawId(
        index: Int,
        field: String,
        value: String,
        output: MutableList<AgentConfigValidationProblem>,
    ) {
        if (!isSafeNativeId(value) || value == RESERVED_MCP_ID) {
            output += problem("changes[$index].$field", "ID 格式无效")
        }
    }

    private fun openClawSkillActivation(root: JsonObject, configurationId: String): AgentSkillActivation {
        val entry = root.getObject(SKILLS_KEY)?.getObject(SKILL_ENTRIES_KEY)?.getObject(configurationId)
        val enabled = (entry?.get(ENABLED_KEY) as? JsonPrimitive)?.getValue() as? Boolean ?: true
        return if (enabled) AgentSkillActivation.Enabled else AgentSkillActivation.Disabled
    }

    private fun setOpenClawSkillActivation(
        root: JsonObject,
        skillId: String,
        activation: AgentSkillActivation,
    ) {
        val entry = skillDirectory.discover().firstOrNull { it.id == skillId }
            ?: error("Skill 已不存在，请重新读取")
        val skills = root.objectCopy(SKILLS_KEY)
        val entries = skills.objectCopy(SKILL_ENTRIES_KEY)
        val config = entries.objectCopy(entry.configurationId)
        putPreserving(config, ENABLED_KEY, JsonPrimitive.of(activation == AgentSkillActivation.Enabled))
        putPreserving(entries, entry.configurationId, config)
        putPreserving(skills, SKILL_ENTRIES_KEY, entries)
        putPreserving(root, SKILLS_KEY, skills)
    }

    private fun openClawSkillKey(file: File, fallback: String): String = runCatching {
        val header = file.useLines { lines -> lines.take(MAX_SKILL_HEADER_LINES).joinToString("\n") }
        (OPENCLAW_JSON_SKILL_KEY.find(header) ?: OPENCLAW_YAML_SKILL_KEY.find(header))
            ?.groupValues
            ?.get(1)
            ?.takeIf(::isSafeNativeId)
            ?: fallback
    }.getOrDefault(fallback)

    private suspend fun refreshedApplyResult(agentId: String): AgentConfigApplyResult = when (val refreshed = readLive(agentId)) {
        is AgentConfigReadResult.Ready -> AgentConfigApplyResult.Applied(refreshed.snapshot, backupReference = null)
        is AgentConfigReadResult.Failed -> AgentConfigApplyResult.Failed(refreshed.message, restored = false)
        is AgentConfigReadResult.Unavailable -> AgentConfigApplyResult.Unavailable(refreshed.discovery)
    }

    private fun configure(root: JsonObject, provider: AgentProviderDraft, credential: AgentProviderCredentialChange) {
        val modelsRoot = root.objectCopy("models")
        if (!modelsRoot.containsKey("mode")) putPreserving(modelsRoot, "mode", JsonPrimitive.of("merge"))
        val providers = modelsRoot.objectCopy("providers")
        val entry = providers.objectCopy(provider.id)
        putPreserving(entry, "baseUrl", JsonPrimitive.of(provider.baseUrl.trim()))
        if (!entry.containsKey("api")) putPreserving(entry, "api", JsonPrimitive.of("openai-completions"))
        when (credential) {
            AgentProviderCredentialChange.Keep -> Unit
            is AgentProviderCredentialChange.Replace -> putPreserving(entry, "apiKey", JsonPrimitive.of(credential.secret))
            AgentProviderCredentialChange.Remove -> entry.remove("apiKey")
        }
        val models = JsonArray()
        provider.models.forEach { model ->
            models.add(JsonObject().also { value ->
                value.put("id", JsonPrimitive.of(model.id.trim()))
                if (model.displayName.isNotBlank() && model.displayName != model.id) {
                    value.put("name", JsonPrimitive.of(model.displayName.trim()))
                }
            })
        }
        putPreserving(entry, "models", models)
        putPreserving(providers, provider.id, entry)
        putPreserving(modelsRoot, "providers", providers)
        putPreserving(root, "models", modelsRoot)

        val catalog = defaults(root).objectCopy("models")
        catalog.keys.filter { it.startsWith("${provider.id}/") }.toList().forEach(catalog::remove)
        provider.models.forEach { model ->
            val catalogEntry = JsonObject()
            if (model.displayName.isNotBlank() && model.displayName != model.id) {
                catalogEntry.put("alias", JsonPrimitive.of(model.displayName.trim()))
            }
            catalog.put(providerModelRef(provider.id, model.id.trim()), catalogEntry)
        }
        val defaults = defaults(root)
        putPreserving(defaults, "models", catalog)
        setDefaults(root, defaults)
        if (currentDefault(root).isNullOrBlank()) setDefault(root, providerModelRef(provider.id, provider.models.first().id.trim()))
    }

    private fun removeProvider(root: JsonObject, providerId: String) {
        val modelsRoot = root.objectCopy("models")
        val providers = modelsRoot.objectCopy("providers")
        providers.remove(providerId)
        putPreserving(modelsRoot, "providers", providers)
        putPreserving(root, "models", modelsRoot)
        val defaults = defaults(root)
        val catalog = defaults.objectCopy("models")
        catalog.keys.filter { it.startsWith("$providerId/") }.toList().forEach(catalog::remove)
        putPreserving(defaults, "models", catalog)
        setDefaults(root, defaults)
        if (currentDefault(root)?.startsWith("$providerId/") == true) setDefault(root, null)
    }

    private fun currentDefault(root: JsonObject): String? {
        val value = defaults(root)["model"]
        return when (value) {
            is JsonPrimitive -> value.getValue() as? String
            is JsonObject -> value.string("primary")
            else -> null
        }
    }

    private fun setDefault(root: JsonObject, modelId: String?) {
        val defaults = defaults(root)
        if (modelId == null) {
            defaults.remove("model")
        } else {
            val model = (defaults["model"] as? JsonObject)?.clone() ?: JsonObject()
            putPreserving(model, "primary", JsonPrimitive.of(modelId))
            putPreserving(defaults, "model", model)
        }
        setDefaults(root, defaults)
    }

    private fun defaults(root: JsonObject): JsonObject = root.getObject("agents")?.getObject("defaults")?.clone() ?: JsonObject()

    private fun setDefaults(root: JsonObject, defaults: JsonObject) {
        val agents = root.objectCopy("agents")
        putPreserving(agents, "defaults", defaults)
        putPreserving(root, "agents", agents)
    }

    private fun JsonArray?.orEmptyObjects(): List<JsonObject> = this?.mapNotNull { it as? JsonObject }.orEmpty()

    companion object {
        const val ADAPTER_ID = "openclaw"
        private const val CONFIG_KEY = "config"
        private const val CONFIG_PATH = "/root/.openclaw/openclaw.json"
        private const val DEFAULT_WORKSPACE_PATH = "/root/.openclaw/workspace"
        private const val MCP_KEY = "mcp"
        private const val MCP_SERVERS_KEY = "servers"
        private const val COMMAND_KEY = "command"
        private const val ARGS_KEY = "args"
        private const val ENV_KEY = "env"
        private const val CWD_KEY = "cwd"
        private const val URL_KEY = "url"
        private const val TRANSPORT_KEY = "transport"
        private const val HEADERS_KEY = "headers"
        private const val ENABLED_KEY = "enabled"
        private const val STDIO_TYPE = "stdio"
        private const val STREAMABLE_HTTP_TYPE = "streamable-http"
        private const val SSE_TYPE = "sse"
        private const val SKILLS_KEY = "skills"
        private const val SKILL_ENTRIES_KEY = "entries"
        private const val SKILL_ROOT = "/root/.openclaw/skills"
        private const val AGENTS_SKILL_ROOT = "/root/.agents/skills"
        private const val AUTHORIZATION_HEADER = "Authorization"
        private const val RESERVED_MCP_ID = "__proto__"
        private const val MAX_MCP_ITEMS = 64
        private const val MAX_SKILL_HEADER_LINES = 160
        private val SAFE_IMPORT_REFERENCE = Regex("kite-import:import-[A-Za-z0-9-]{8,80}")
        private val SAFE_ENV_NAME = Regex("[A-Za-z_][A-Za-z0-9_]{0,127}")
        private val OPENCLAW_ENV_REFERENCE = Regex("\\$\\{([A-Za-z_][A-Za-z0-9_]*)\\}")
        private val OPENCLAW_BEARER_REFERENCE = Regex(
            "Bearer\\s+\\$\\{([A-Za-z_][A-Za-z0-9_]*)\\}",
            RegexOption.IGNORE_CASE,
        )
        private val OPENCLAW_JSON_SKILL_KEY = Regex(
            "[\\\"']skillKey[\\\"']\\s*:\\s*[\\\"']([A-Za-z0-9][A-Za-z0-9._-]{0,127})[\\\"']",
        )
        private val OPENCLAW_YAML_SKILL_KEY = Regex(
            "(?m)^\\s*skillKey\\s*:\\s*[\\\"']?([A-Za-z0-9][A-Za-z0-9._-]{0,127})[\\\"']?\\s*$",
        )
        private val OPENCLAW_SKILL_ACTIVATIONS = setOf(AgentSkillActivation.Enabled, AgentSkillActivation.Disabled)
    }
}

internal class ClaudeCodeAgentConfigAdapter(
    context: Context,
    containerProvider: () -> ContainerRecord? = {
        WorkSurfaceRuntimeBridge.getSavedContainer(context.applicationContext)
    },
    fileStore: AtomicConfigFileStore = AtomicConfigFileStore()
) : JanksonNativeAgentConfigAdapter(
    context,
    ADAPTER_ID,
    linkedMapOf(CONFIG_KEY to CONFIG_PATH, STATE_KEY to STATE_PATH),
    CONFIG_KEY,
    containerProvider,
    fileStore
) {
    private val skillDirectory = NativeAgentSkillDirectory(projection::resolve, listOf(SKILL_ROOT))

    override fun displayName(): String = "Claude Code"

    override fun reasoningControl(): AgentReasoningControl = AgentReasoningControls.ClaudeCode

    override fun nativeCoreDocuments(workspacePath: String?): List<NativeAgentCoreDocumentSpec> = buildList {
        add(NativeAgentCoreDocumentSpec(
            id = "claude-global",
            displayName = "Claude 全局说明",
            fileName = "CLAUDE.md",
            containerPath = GLOBAL_CLAUDE_PATH,
            scope = AgentConfigScope.User,
            semantics = AgentCoreDocumentSemantics.SupplementalInstructions,
            priorityDescription = "所有 Claude Code 项目都会加载的用户级说明",
        ))
        projectCoreDocument(
            workspacePath,
            id = "claude-project",
            displayName = "当前项目说明",
            fileName = "CLAUDE.md",
            priorityDescription = "项目共享说明，在用户级说明之后加入上下文",
        )?.let(::add)
        projectCoreDocument(
            workspacePath,
            id = "claude-local",
            displayName = "当前项目个人说明",
            fileName = "CLAUDE.local.md",
            priorityDescription = "当前项目的个人补充，在项目共享说明之后加入上下文",
        )?.let(::add)
    }

    override fun nativeCapabilities(): AgentConfigCapabilities = AgentConfigCapabilities(
        supported = setOf(
            AgentPersistentConfigCapability.DefaultModel,
            AgentPersistentConfigCapability.Provider,
            AgentPersistentConfigCapability.ProviderProfiles,
            AgentPersistentConfigCapability.CredentialStatus,
            AgentPersistentConfigCapability.Mcp,
            AgentPersistentConfigCapability.Skill,
            AgentPersistentConfigCapability.CoreDocuments,
        ),
        credentialOwnership = AgentCredentialOwnership.AgentOwned,
        mcpOperations = setOf(AgentMcpOperation.Create, AgentMcpOperation.Edit, AgentMcpOperation.Remove),
        mcpTransports = setOf(AgentMcpTransport.Stdio, AgentMcpTransport.StreamableHttp, AgentMcpTransport.Sse),
        skillOperations = setOf(
            AgentSkillOperation.Import,
            AgentSkillOperation.Enable,
            AgentSkillOperation.ManualOnly,
            AgentSkillOperation.Disable,
            AgentSkillOperation.Remove,
        ),
    )

    override fun decode(files: Map<String, ByteArray>): NativeState {
        val root = parse(files.getValue(CONFIG_KEY))
        val env = root.getObject("env")
        val baseUrl = env?.string("ANTHROPIC_BASE_URL")
        val model = root.string("model")
        val modelIds = (root["availableModels"] as? JsonArray)
            ?.mapNotNull { (it as? JsonPrimitive)?.getValue() as? String }
            ?.filter(String::isNotBlank)
            ?.distinct()
            .orEmpty()
            .ifEmpty { listOfNotNull(model) }
        val hasCredential = !env?.string("ANTHROPIC_API_KEY").isNullOrBlank() ||
            !env?.string("ANTHROPIC_AUTH_TOKEN").isNullOrBlank()
        val provider = if (baseUrl.isNullOrBlank() && modelIds.isEmpty() && !hasCredential) emptyList() else listOf(
            AgentProviderSummary(
                id = providerId(baseUrl),
                displayName = providerName(baseUrl),
                baseUrl = baseUrl ?: "https://api.anthropic.com",
                models = modelIds.map { AgentProviderModelSummary(it, it) },
                credentialPresence = if (hasCredential) AgentCredentialPresence.Present else AgentCredentialPresence.Missing
            )
        )
        val state = parse(files.getValue(STATE_KEY))
        return NativeState(
            model,
            provider,
            overallCredential(provider),
            activeProviderId = provider.firstOrNull()?.id?.takeIf { !model.isNullOrBlank() },
            mcpServers = claudeMcpServers(state),
            skills = skillDirectory.summaries(
                activation = { entry -> claudeSkillActivation(root, entry.id) },
                activationOperations = setOf(
                    AgentSkillOperation.Enable,
                    AgentSkillOperation.ManualOnly,
                    AgentSkillOperation.Disable,
                ),
            ),
        )
    }

    override fun validateNativeChange(
        index: Int,
        change: AgentPersistentConfigChange,
        output: MutableList<AgentConfigValidationProblem>,
    ) {
        when (change) {
            is AgentPersistentConfigChange.ConfigureMcpServer -> validateClaudeMcpDraft(index, change.server, output)
            is AgentPersistentConfigChange.RemoveMcpServer -> validateClaudeId(index, "serverId", change.serverId, output)
            is AgentPersistentConfigChange.InstallSkill -> {
                validateClaudeId(index, "skillId", change.skillId, output)
                if (!SAFE_IMPORT_REFERENCE.matches(change.sourceReference)) {
                    output += problem("changes[$index].sourceReference", "Skill 来源引用格式无效")
                }
            }
            is AgentPersistentConfigChange.SetSkillActivation -> {
                validateClaudeId(index, "skillId", change.skillId, output)
                if (change.activation !in CLAUDE_SKILL_ACTIVATIONS) {
                    output += problem("changes[$index].activation", "Claude Code 不支持这个 Skill 状态")
                }
            }
            is AgentPersistentConfigChange.RemoveSkill -> validateClaudeId(index, "skillId", change.skillId, output)
            else -> super.validateNativeChange(index, change, output)
        }
    }

    override suspend fun applyExternalChanges(
        request: AgentConfigApplyRequest,
        before: AgentLiveConfigSnapshot,
    ): AgentConfigApplyResult? {
        val fileChanges = request.changes.filter {
            it is AgentPersistentConfigChange.InstallSkill || it is AgentPersistentConfigChange.RemoveSkill
        }
        if (fileChanges.isEmpty()) return null
        if (request.changes.size != 1 || fileChanges.size != 1) {
            return AgentConfigApplyResult.Rejected(
                listOf(problem("changes", "Skill 文件变更一次只能执行一项，不能和其他配置混合")),
            )
        }
        skillDirectory.applyFileChange(fileChanges.single())?.let { return it }
        return when (val refreshed = readLive(request.agentId)) {
            is AgentConfigReadResult.Ready -> AgentConfigApplyResult.Applied(refreshed.snapshot, backupReference = null)
            is AgentConfigReadResult.Failed -> AgentConfigApplyResult.Failed(refreshed.message, restored = false)
            is AgentConfigReadResult.Unavailable -> AgentConfigApplyResult.Unavailable(refreshed.discovery)
        }
    }

    override fun nativeRevisionInputs(): List<Pair<String, String>> = skillDirectory.revisionInputs()

    override fun mutate(files: Map<String, ByteArray>, changes: List<AgentPersistentConfigChange>): Map<String, ByteArray> {
        val root = parse(files.getValue(CONFIG_KEY)).clone()
        val state = parse(files.getValue(STATE_KEY)).clone()
        changes.forEach { change ->
            when (change) {
                is AgentPersistentConfigChange.SetDefaultModel -> if (change.modelId == null) root.remove("model")
                    else putPreserving(root, "model", JsonPrimitive.of(change.modelId))
                is AgentPersistentConfigChange.SelectProvider ->
                    putPreserving(root, "model", JsonPrimitive.of(change.modelId))
                is AgentPersistentConfigChange.ConfigureProvider -> configure(root, change.provider, change.credential)
                is AgentPersistentConfigChange.RemoveProvider -> removeProvider(root)
                is AgentPersistentConfigChange.ConfigureMcpServer -> configureClaudeMcp(state, change.server)
                is AgentPersistentConfigChange.RemoveMcpServer -> {
                    val servers = state.objectCopy(MCP_KEY)
                    servers.remove(change.serverId)
                    putPreserving(state, MCP_KEY, servers)
                }
                is AgentPersistentConfigChange.SetSkillActivation -> {
                    val overrides = root.objectCopy(SKILL_OVERRIDES_KEY)
                    putPreserving(
                        overrides,
                        change.skillId,
                        JsonPrimitive.of(
                            when (change.activation) {
                                AgentSkillActivation.Enabled -> "on"
                                AgentSkillActivation.ManualOnly -> "user-invocable-only"
                                AgentSkillActivation.Disabled -> "off"
                                else -> error("已由 Claude Code Skill 校验限制状态")
                            },
                        ),
                    )
                    putPreserving(root, SKILL_OVERRIDES_KEY, overrides)
                }
                else -> Unit
            }
        }
        return mapOf(CONFIG_KEY to serialize(root), STATE_KEY to serialize(state))
    }

    private fun claudeMcpServers(state: JsonObject): List<AgentMcpSummary> = state.getObject(MCP_KEY)?.entries
        .orEmpty()
        .mapNotNull { (id, value) ->
            val server = value as? JsonObject ?: return@mapNotNull null
            val type = server.string("type")
            val command = server.string("command")
            val url = server.string("url")
            val transport = when (type) {
                "stdio" -> AgentMcpTransport.Stdio
                "http" -> AgentMcpTransport.StreamableHttp
                "sse" -> AgentMcpTransport.Sse
                else -> when {
                    !command.isNullOrBlank() -> AgentMcpTransport.Stdio
                    !url.isNullOrBlank() -> AgentMcpTransport.StreamableHttp
                    else -> AgentMcpTransport.Unknown
                }
            }
            AgentMcpSummary(
                id = id,
                kind = type ?: "unknown",
                enabled = true,
                transport = transport,
                command = command,
                arguments = (server["args"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.getValue() as? String }.orEmpty(),
                url = url,
                environmentReferences = claudeReferences(server.getObject("env")),
                headerReferences = claudeReferences(server.getObject("headers")),
                scope = AgentConfigScope.User,
                allowedOperations = buildSet {
                    if (transport != AgentMcpTransport.Unknown) add(AgentMcpOperation.Edit)
                    add(AgentMcpOperation.Remove)
                },
            )
        }
        .sortedBy(AgentMcpSummary::id)

    private fun claudeReferences(values: JsonObject?): List<AgentMcpEnvironmentReference> = values?.entries
        .orEmpty()
        .mapNotNull { (name, value) ->
            val raw = (value as? JsonPrimitive)?.getValue() as? String ?: return@mapNotNull null
            val variable = CLAUDE_ENV_REFERENCE.matchEntire(raw)?.groupValues?.get(1) ?: return@mapNotNull null
            AgentMcpEnvironmentReference(name, variable)
        }
        .sortedBy(AgentMcpEnvironmentReference::name)

    private fun claudeSkillActivation(settings: JsonObject, skillId: String): AgentSkillActivation =
        when (settings.getObject(SKILL_OVERRIDES_KEY)?.string(skillId)) {
            "off" -> AgentSkillActivation.Disabled
            "user-invocable-only" -> AgentSkillActivation.ManualOnly
            "on", "name-only", null -> AgentSkillActivation.Enabled
            else -> AgentSkillActivation.Unknown
        }

    private fun configureClaudeMcp(state: JsonObject, draft: AgentMcpDraft) {
        val servers = state.objectCopy(MCP_KEY)
        val server = servers.objectCopy(draft.id)
        listOf("type", "command", "args", "env", "url", "headers").forEach(server::remove)
        when (draft.transport) {
            AgentMcpTransport.Stdio -> {
                putPreserving(server, "type", JsonPrimitive.of("stdio"))
                putPreserving(server, "command", JsonPrimitive.of(requireNotNull(draft.command).trim()))
                val arguments = JsonArray()
                draft.arguments.forEach { arguments.add(JsonPrimitive.of(it)) }
                putPreserving(server, "args", arguments)
                if (draft.environmentReferences.isNotEmpty()) {
                    val env = JsonObject()
                    draft.environmentReferences.forEach { reference ->
                        env.put(reference.name, JsonPrimitive.of("${'$'}{${reference.environmentVariable}}"))
                    }
                    putPreserving(server, "env", env)
                }
            }
            AgentMcpTransport.StreamableHttp,
            AgentMcpTransport.Sse -> {
                putPreserving(
                    server,
                    "type",
                    JsonPrimitive.of(if (draft.transport == AgentMcpTransport.Sse) "sse" else "http"),
                )
                putPreserving(server, "url", JsonPrimitive.of(requireNotNull(draft.url).trim()))
                if (draft.headerReferences.isNotEmpty()) {
                    val headers = JsonObject()
                    draft.headerReferences.forEach { reference ->
                        headers.put(reference.name, JsonPrimitive.of("${'$'}{${reference.environmentVariable}}"))
                    }
                    putPreserving(server, "headers", headers)
                }
            }
            else -> error("已由 Claude Code MCP 校验限制传输类型")
        }
        putPreserving(servers, draft.id, server)
        putPreserving(state, MCP_KEY, servers)
    }

    private fun validateClaudeMcpDraft(
        index: Int,
        draft: AgentMcpDraft,
        output: MutableList<AgentConfigValidationProblem>,
    ) {
        validateClaudeId(index, "server.id", draft.id, output)
        when (draft.transport) {
            AgentMcpTransport.Stdio -> if (draft.command.isNullOrBlank() || draft.command.any(Char::isISOControl)) {
                output += problem("changes[$index].server.command", "本地 MCP 必须提供有效命令")
            }
            AgentMcpTransport.StreamableHttp,
            AgentMcpTransport.Sse -> {
                val uri = runCatching { URI(draft.url.orEmpty()) }.getOrNull()
                if (uri == null || uri.scheme !in setOf("http", "https") || uri.host.isNullOrBlank() || uri.userInfo != null) {
                    output += problem("changes[$index].server.url", "远程 MCP 必须提供有效 HTTP 或 HTTPS 地址")
                }
            }
            else -> output += problem("changes[$index].server.transport", "Claude Code 不支持这个 MCP 传输类型")
        }
        if (draft.arguments.size > MAX_MCP_ITEMS || draft.environmentReferences.size > MAX_MCP_ITEMS || draft.headerReferences.size > MAX_MCP_ITEMS) {
            output += problem("changes[$index].server", "MCP 参数、环境变量或 Header 数量过多")
        }
        (draft.environmentReferences + draft.headerReferences).forEach { reference ->
            if (reference.name.isBlank() || reference.name.any(Char::isISOControl) || !SAFE_ENV_NAME.matches(reference.environmentVariable)) {
                output += problem("changes[$index].server.references", "MCP 环境变量引用格式无效")
            }
        }
    }

    private fun validateClaudeId(
        index: Int,
        field: String,
        value: String,
        output: MutableList<AgentConfigValidationProblem>,
    ) {
        if (!isSafeNativeId(value)) output += problem("changes[$index].$field", "ID 格式无效")
    }

    private fun configure(root: JsonObject, provider: AgentProviderDraft, credential: AgentProviderCredentialChange) {
        val env = root.objectCopy("env")
        putPreserving(env, "ANTHROPIC_BASE_URL", JsonPrimitive.of(provider.baseUrl.trim()))
        putPreserving(env, "CLAUDE_CODE_ENABLE_GATEWAY_MODEL_DISCOVERY", JsonPrimitive.of("1"))
        val first = provider.models.first()
        putPreserving(env, "ANTHROPIC_CUSTOM_MODEL_OPTION", JsonPrimitive.of(first.id.trim()))
        if (first.displayName.isNotBlank()) {
            putPreserving(env, "ANTHROPIC_CUSTOM_MODEL_OPTION_NAME", JsonPrimitive.of(first.displayName.trim()))
        }
        when (credential) {
            AgentProviderCredentialChange.Keep -> Unit
            is AgentProviderCredentialChange.Replace -> {
                putPreserving(env, "ANTHROPIC_API_KEY", JsonPrimitive.of(credential.secret))
                env.remove("ANTHROPIC_AUTH_TOKEN")
            }
            AgentProviderCredentialChange.Remove -> {
                env.remove("ANTHROPIC_API_KEY")
                env.remove("ANTHROPIC_AUTH_TOKEN")
            }
        }
        putPreserving(root, "env", env)
        val models = JsonArray()
        provider.models.forEach { models.add(JsonPrimitive.of(it.id.trim())) }
        putPreserving(root, "availableModels", models)
        if (root.string("model").isNullOrBlank()) putPreserving(root, "model", JsonPrimitive.of(first.id.trim()))
    }

    private fun removeProvider(root: JsonObject) {
        val env = root.objectCopy("env")
        listOf(
            "ANTHROPIC_BASE_URL",
            "ANTHROPIC_API_KEY",
            "ANTHROPIC_AUTH_TOKEN",
            "CLAUDE_CODE_ENABLE_GATEWAY_MODEL_DISCOVERY",
            "ANTHROPIC_CUSTOM_MODEL_OPTION",
            "ANTHROPIC_CUSTOM_MODEL_OPTION_NAME",
            "ANTHROPIC_CUSTOM_MODEL_OPTION_DESCRIPTION"
        ).forEach(env::remove)
        putPreserving(root, "env", env)
        root.remove("availableModels")
        root.remove("model")
    }

    private fun providerId(baseUrl: String?): String = runCatching {
        URI(baseUrl ?: "https://api.anthropic.com").host
            ?.lowercase()
            ?.replace(Regex("[^a-z0-9.-]"), "-")
            ?.take(64)
    }.getOrNull().orEmpty().ifBlank { "active" }

    private fun providerName(baseUrl: String?): String = runCatching {
        URI(baseUrl ?: "https://api.anthropic.com").host
    }.getOrNull().orEmpty().ifBlank { "Claude 当前供应商" }

    companion object {
        const val ADAPTER_ID = "claude-code"
        private const val CONFIG_KEY = "settings"
        private const val CONFIG_PATH = "/root/.claude/settings.json"
        private const val STATE_KEY = "state"
        private const val STATE_PATH = "/root/.claude.json"
        private const val SKILL_ROOT = "/root/.claude/skills"
        private const val GLOBAL_CLAUDE_PATH = "/root/.claude/CLAUDE.md"
        private const val MCP_KEY = "mcpServers"
        private const val SKILL_OVERRIDES_KEY = "skillOverrides"
        private const val MAX_MCP_ITEMS = 64
        private val SAFE_IMPORT_REFERENCE = Regex("kite-import:import-[A-Za-z0-9-]{8,80}")
        private val SAFE_ENV_NAME = Regex("[A-Za-z_][A-Za-z0-9_]{0,127}")
        private val CLAUDE_ENV_REFERENCE = Regex("\\$\\{([A-Za-z_][A-Za-z0-9_]*)\\}")
        private val CLAUDE_SKILL_ACTIVATIONS = setOf(
            AgentSkillActivation.Enabled,
            AgentSkillActivation.ManualOnly,
            AgentSkillActivation.Disabled,
        )
    }
}

internal class CodexAgentConfigAdapter(
    context: Context,
    containerProvider: () -> ContainerRecord? = {
        WorkSurfaceRuntimeBridge.getSavedContainer(context.applicationContext)
    },
    fileStore: AtomicConfigFileStore = AtomicConfigFileStore()
) : NativeAgentConfigAdapter(
    context,
    ADAPTER_ID,
    linkedMapOf(
        CONFIG_KEY to CONFIG_PATH,
        RELAY_UPSTREAM_KEY to RELAY_UPSTREAM_PATH,
        RELAY_API_KEY_KEY to RELAY_API_KEY_PATH,
    ),
    CONFIG_KEY,
    containerProvider,
    fileStore
) {
    private val skillDirectory = NativeAgentSkillDirectory(projection::resolve, listOf(SKILL_ROOT))

    override fun displayName(): String = "Codex"

    override fun reasoningControl(): AgentReasoningControl = AgentReasoningControls.Codex

    override fun normalizeSessionConfiguration(options: List<AgentConfigOption>): List<AgentConfigOption> =
        super.normalizeSessionConfiguration(options).mapNotNull { option ->
            if (
                option is AgentConfigOption.Select &&
                option.category == AgentConfigCategory.Model &&
                option.choices.none { it.value == option.currentValue }
            ) {
                return@mapNotNull option.copy(
                    currentValue = stripUnverifiedCodexEffortSuffix(option.currentValue),
                )
            }
            if (
                option !is AgentConfigOption.Select ||
                option.id != NATIVE_MODE_OPTION_ID ||
                option.category != AgentConfigCategory.Mode
            ) return@mapNotNull option
            val mappedChoices = option.choices.mapNotNull { choice ->
                val level = CODEX_PERMISSION_LEVELS[choice.value] ?: return@mapNotNull null
                choice.copy(
                    name = level.displayName,
                    description = level.description,
                )
            }
            if (mappedChoices.size < 2 || mappedChoices.none { it.value == option.currentValue }) {
                null
            } else {
                option.copy(
                    name = "权限",
                    description = "Codex 当前会话真实提供的沙箱与审批模式",
                    category = AgentConfigCategory.Permission,
                    choices = mappedChoices,
                )
            }
        }

    override fun nativeCoreDocuments(workspacePath: String?): List<NativeAgentCoreDocumentSpec> = buildList {
        add(NativeAgentCoreDocumentSpec(
            id = "codex-global-agents",
            displayName = "Codex 全局说明",
            fileName = "AGENTS.md",
            containerPath = GLOBAL_AGENTS_PATH,
            scope = AgentConfigScope.User,
            semantics = AgentCoreDocumentSemantics.SupplementalInstructions,
            priorityDescription = "所有 Codex 工作区都会读取的用户级说明",
        ))
        add(NativeAgentCoreDocumentSpec(
            id = "codex-global-override",
            displayName = "Codex 全局覆盖说明",
            fileName = "AGENTS.override.md",
            containerPath = GLOBAL_OVERRIDE_PATH,
            scope = AgentConfigScope.User,
            semantics = AgentCoreDocumentSemantics.SupplementalInstructions,
            priorityDescription = "非空时替代同级全局 AGENTS.md",
        ))
        projectCoreDocument(
            workspacePath,
            id = "codex-project-agents",
            displayName = "当前项目说明",
            fileName = "AGENTS.md",
            priorityDescription = "当前目录说明；越接近工作目录，实际优先级越高",
        )?.let(::add)
        projectCoreDocument(
            workspacePath,
            id = "codex-project-override",
            displayName = "当前项目覆盖说明",
            fileName = "AGENTS.override.md",
            priorityDescription = "非空时替代当前目录同级 AGENTS.md",
        )?.let(::add)
    }

    override fun nativeCapabilities(): AgentConfigCapabilities = AgentConfigCapabilities(
        supported = setOf(
            AgentPersistentConfigCapability.DefaultModel,
            AgentPersistentConfigCapability.Provider,
            AgentPersistentConfigCapability.ProviderProfiles,
            AgentPersistentConfigCapability.CredentialStatus,
            AgentPersistentConfigCapability.Mcp,
            AgentPersistentConfigCapability.Skill,
            AgentPersistentConfigCapability.CoreDocuments,
        ),
        credentialOwnership = AgentCredentialOwnership.AgentOwned,
        mcpOperations = setOf(
            AgentMcpOperation.Create,
            AgentMcpOperation.Edit,
            AgentMcpOperation.Enable,
            AgentMcpOperation.Disable,
            AgentMcpOperation.Remove,
        ),
        mcpTransports = setOf(AgentMcpTransport.Stdio, AgentMcpTransport.StreamableHttp),
        skillOperations = setOf(
            AgentSkillOperation.Import,
            AgentSkillOperation.Enable,
            AgentSkillOperation.Disable,
            AgentSkillOperation.Remove,
        ),
    )

    override fun decode(files: Map<String, ByteArray>): NativeState {
        val text = files.getValue(CONFIG_KEY).toString(Charsets.UTF_8)
        val parsed = Toml.parse(text)
        require(!parsed.hasErrors()) { "Codex TOML 无法解析" }
        val active = parsed.getString("model_provider")
        val model = parsed.getString("model")
        val relayUpstream = files.getValue(RELAY_UPSTREAM_KEY).toString(Charsets.UTF_8).trim()
        val relayApiKey = files.getValue(RELAY_API_KEY_KEY).toString(Charsets.UTF_8).trim()
        val provider = active?.let { id ->
            val table = parsed.getTable("model_providers.${tomlPathKey(id)}")
            val token = table?.getString("experimental_bearer_token")
            val configuredBaseUrl = table?.getString("base_url")
            val usesKiteRelay = configuredBaseUrl?.trimEnd('/') == RELAY_BASE_URL
            AgentProviderSummary(
                id = id,
                displayName = table?.getString("name")?.takeIf(String::isNotBlank) ?: id,
                baseUrl = relayUpstream.takeIf { usesKiteRelay && it.isNotBlank() } ?: configuredBaseUrl,
                models = listOfNotNull(model?.let { AgentProviderModelSummary(it, it) }),
                credentialPresence = if (
                    !token.isNullOrBlank() || usesKiteRelay && relayApiKey.isNotBlank()
                ) AgentCredentialPresence.Present else AgentCredentialPresence.Missing
            )
        }?.let(::listOf).orEmpty()
        return NativeState(
            model,
            provider,
            overallCredential(provider),
            activeProviderId = active,
            mcpServers = codexMcpServers(parsed),
            skills = skillDirectory.summaries(
                activation = { entry -> codexSkillActivation(parsed, "${entry.containerLocation}/SKILL.md") },
                activationOperations = setOf(AgentSkillOperation.Enable, AgentSkillOperation.Disable),
            ),
        )
    }

    override fun validateNativeChange(
        index: Int,
        change: AgentPersistentConfigChange,
        output: MutableList<AgentConfigValidationProblem>,
    ) {
        when (change) {
            is AgentPersistentConfigChange.ConfigureMcpServer -> validateCodexMcpDraft(index, change.server, output)
            is AgentPersistentConfigChange.SetMcpEnabled -> validateNativeId(index, "serverId", change.serverId, output)
            is AgentPersistentConfigChange.RemoveMcpServer -> validateNativeId(index, "serverId", change.serverId, output)
            is AgentPersistentConfigChange.InstallSkill -> {
                validateNativeId(index, "skillId", change.skillId, output)
                if (!SAFE_IMPORT_REFERENCE.matches(change.sourceReference)) {
                    output += problem("changes[$index].sourceReference", "Skill 来源引用格式无效")
                }
            }
            is AgentPersistentConfigChange.SetSkillActivation -> {
                validateNativeId(index, "skillId", change.skillId, output)
                if (change.activation !in setOf(AgentSkillActivation.Enabled, AgentSkillActivation.Disabled)) {
                    output += problem("changes[$index].activation", "Codex 只支持启用或停用 Skill")
                }
            }
            is AgentPersistentConfigChange.RemoveSkill -> validateNativeId(index, "skillId", change.skillId, output)
            else -> super.validateNativeChange(index, change, output)
        }
    }

    override suspend fun applyExternalChanges(
        request: AgentConfigApplyRequest,
        before: AgentLiveConfigSnapshot,
    ): AgentConfigApplyResult? {
        val fileChanges = request.changes.filter {
            it is AgentPersistentConfigChange.InstallSkill || it is AgentPersistentConfigChange.RemoveSkill
        }
        if (fileChanges.isEmpty()) return null
        if (request.changes.size != 1 || fileChanges.size != 1) {
            return AgentConfigApplyResult.Rejected(
                listOf(problem("changes", "Skill 文件变更一次只能执行一项，不能和其他配置混合")),
            )
        }
        skillDirectory.applyFileChange(fileChanges.single())?.let { return it }
        return when (val refreshed = readLive(request.agentId)) {
            is AgentConfigReadResult.Ready -> AgentConfigApplyResult.Applied(refreshed.snapshot, backupReference = null)
            is AgentConfigReadResult.Failed -> AgentConfigApplyResult.Failed(refreshed.message, restored = false)
            is AgentConfigReadResult.Unavailable -> AgentConfigApplyResult.Unavailable(refreshed.discovery)
        }
    }

    override fun nativeRevisionInputs(): List<Pair<String, String>> = skillDirectory.revisionInputs()

    override fun mutate(files: Map<String, ByteArray>, changes: List<AgentPersistentConfigChange>): Map<String, ByteArray> {
        var editor = TomlTextEditor(files.getValue(CONFIG_KEY).toString(Charsets.UTF_8))
        var relayUpstream = files.getValue(RELAY_UPSTREAM_KEY).toString(Charsets.UTF_8)
        var relayApiKey = files.getValue(RELAY_API_KEY_KEY).toString(Charsets.UTF_8)
        changes.forEach { change ->
            when (change) {
                is AgentPersistentConfigChange.SetDefaultModel -> editor = editor.setTopString("model", change.modelId)
                is AgentPersistentConfigChange.SelectProvider -> editor = editor
                    .setTopString("model_provider", change.providerId)
                    .setTopString("model", change.modelId)
                is AgentPersistentConfigChange.ConfigureProvider -> {
                    val provider = change.provider
                    val before = Toml.parse(editor.text)
                    val matchingProviderIds = buildSet {
                        add(provider.id)
                        before.getTable("model_providers")?.let { providers ->
                            providers.keySet().forEach { providerId ->
                                val existingUrl = providers.getTable(providerId)?.getString("base_url")
                                if (sameProviderEndpoint(existingUrl, provider.baseUrl)) add(providerId)
                            }
                        }
                    }
                    val legacyToken = matchingProviderIds.asSequence()
                        .mapNotNull { providerId ->
                            before.getTable("model_providers.${tomlPathKey(providerId)}")
                                ?.getString("experimental_bearer_token")
                                ?.takeIf(String::isNotBlank)
                        }
                        .firstOrNull()
                    editor = editor
                        .setTopString("model_provider", provider.id)
                        .setTopString("model", provider.models.first().id.trim())
                        .setSectionString(provider.id, "name", provider.displayName?.trim()?.takeIf(String::isNotBlank) ?: provider.id)
                        .setSectionString(provider.id, "base_url", RELAY_BASE_URL)
                        .setSectionString(provider.id, "wire_api", "responses")
                        .setSectionBoolean(provider.id, "requires_openai_auth", false)
                        .setSectionString(provider.id, "experimental_bearer_token", null)
                    matchingProviderIds.forEach { providerId ->
                        editor = editor.setSectionString(providerId, "experimental_bearer_token", null)
                    }
                    val keepsExistingRelayCredential = sameProviderEndpoint(relayUpstream, provider.baseUrl)
                    relayUpstream = provider.baseUrl.trim()
                    relayApiKey = when (val credential = change.credential) {
                        AgentProviderCredentialChange.Keep -> when {
                            keepsExistingRelayCredential && relayApiKey.isNotBlank() -> relayApiKey
                            !legacyToken.isNullOrBlank() -> legacyToken
                            else -> ""
                        }
                        is AgentProviderCredentialChange.Replace -> credential.secret
                        AgentProviderCredentialChange.Remove -> ""
                    }
                }
                is AgentPersistentConfigChange.RemoveProvider -> {
                    val parsed = Toml.parse(editor.text)
                    val wasActive = parsed.getString("model_provider") == change.providerId
                    editor = editor.removeProviderSection(change.providerId)
                    if (wasActive) {
                        editor = editor.setTopString("model_provider", null).setTopString("model", null)
                        relayUpstream = ""
                        relayApiKey = ""
                    }
                }
                is AgentPersistentConfigChange.ConfigureMcpServer -> editor = editor.setMcpServer(change.server)
                is AgentPersistentConfigChange.SetMcpEnabled ->
                    editor = editor.setMcpBoolean(change.serverId, "enabled", change.enabled)
                is AgentPersistentConfigChange.RemoveMcpServer -> editor = editor.removeMcpServer(change.serverId)
                is AgentPersistentConfigChange.SetSkillActivation -> editor = editor.setSkillEnabled(
                    "$SKILL_ROOT/${change.skillId}/SKILL.md",
                    change.activation == AgentSkillActivation.Enabled,
                )
                else -> Unit
            }
        }
        return mapOf(
            CONFIG_KEY to editor.text.toByteArray(Charsets.UTF_8),
            RELAY_UPSTREAM_KEY to relayUpstream.toByteArray(Charsets.UTF_8),
            RELAY_API_KEY_KEY to relayApiKey.toByteArray(Charsets.UTF_8),
        )
    }

    override fun validateBytes(key: String, bytes: ByteArray): String? {
        val text = bytes.toString(Charsets.UTF_8)
        return when (key) {
            CONFIG_KEY -> if (Toml.parse(text).hasErrors()) "Codex 原生 config.toml 格式无效" else null
            RELAY_UPSTREAM_KEY -> {
                if (text.isBlank()) return null
                val uri = runCatching { URI(text.trim()) }.getOrNull()
                if (
                    bytes.size > MAX_RELAY_UPSTREAM_BYTES ||
                    uri == null ||
                    uri.scheme !in setOf("http", "https") ||
                    uri.host.isNullOrBlank() ||
                    uri.userInfo != null
                ) "Codex 协议桥上游 URL 无效" else null
            }
            RELAY_API_KEY_KEY -> if (bytes.size > MAX_RELAY_API_KEY_BYTES || text.any { it == '\u0000' }) {
                "Codex 协议桥凭据格式无效"
            } else null
            else -> "Codex 配置文件类型无效"
        }
    }

    private fun codexMcpServers(parsed: org.tomlj.TomlParseResult): List<AgentMcpSummary> {
        val servers = parsed.getTable("mcp_servers") ?: return emptyList()
        return servers.keySet().mapNotNull { id ->
            val table = servers.getTable(id) ?: return@mapNotNull null
            val command = table.getString("command")
            val url = table.getString("url")
            val transport = when {
                !command.isNullOrBlank() -> AgentMcpTransport.Stdio
                !url.isNullOrBlank() -> AgentMcpTransport.StreamableHttp
                else -> AgentMcpTransport.Unknown
            }
            val enabled = table.getBoolean("enabled") ?: true
            AgentMcpSummary(
                id = id,
                kind = when (transport) {
                    AgentMcpTransport.Stdio -> "stdio"
                    AgentMcpTransport.StreamableHttp -> "http"
                    else -> "unknown"
                },
                enabled = enabled,
                transport = transport,
                command = command,
                arguments = table.getArray("args")?.toList()?.mapNotNull { it as? String }.orEmpty(),
                workingDirectory = table.getString("cwd"),
                url = url,
                environmentReferences = table.getArray("env_vars")?.toList()?.mapNotNull { value ->
                    (value as? String)?.let { AgentMcpEnvironmentReference(it, it) }
                }.orEmpty(),
                headerReferences = table.getTable("env_http_headers")?.let { headers ->
                    headers.keySet().mapNotNull { name ->
                        headers.getString(name)?.let { variable -> AgentMcpEnvironmentReference(name, variable) }
                    }
                }.orEmpty(),
                scope = AgentConfigScope.User,
                allowedOperations = buildSet {
                    if (transport != AgentMcpTransport.Unknown) add(AgentMcpOperation.Edit)
                    add(if (enabled) AgentMcpOperation.Disable else AgentMcpOperation.Enable)
                    add(AgentMcpOperation.Remove)
                },
            )
        }.sortedBy(AgentMcpSummary::id)
    }

    private fun codexSkillActivation(parsed: org.tomlj.TomlParseResult, path: String): AgentSkillActivation {
        val overrides = parsed.getArray("skills.config") ?: return AgentSkillActivation.Enabled
        repeat(overrides.size()) { index ->
            val entry = overrides.getTable(index) ?: return@repeat
            if (entry.getString("path") == path && entry.getBoolean("enabled") == false) {
                return AgentSkillActivation.Disabled
            }
        }
        return AgentSkillActivation.Enabled
    }

    private fun validateCodexMcpDraft(
        index: Int,
        draft: AgentMcpDraft,
        output: MutableList<AgentConfigValidationProblem>,
    ) {
        validateNativeId(index, "server.id", draft.id, output)
        when (draft.transport) {
            AgentMcpTransport.Stdio -> {
                if (draft.command.isNullOrBlank() || draft.command.any(Char::isISOControl)) {
                    output += problem("changes[$index].server.command", "本地 MCP 必须提供有效命令")
                }
                if (draft.arguments.size > MAX_MCP_ITEMS || draft.arguments.any { it.length > MAX_MCP_TEXT || it.any(Char::isISOControl) }) {
                    output += problem("changes[$index].server.arguments", "MCP 参数数量或格式无效")
                }
                if (draft.environmentReferences.any { it.name != it.environmentVariable }) {
                    output += problem("changes[$index].server.environmentReferences", "Codex stdio MCP 只能转发同名环境变量")
                }
            }
            AgentMcpTransport.StreamableHttp -> {
                val uri = runCatching { URI(draft.url.orEmpty()) }.getOrNull()
                if (uri == null || uri.scheme !in setOf("http", "https") || uri.host.isNullOrBlank() || uri.userInfo != null) {
                    output += problem("changes[$index].server.url", "远程 MCP 必须提供有效 HTTP 或 HTTPS 地址")
                }
            }
            else -> output += problem("changes[$index].server.transport", "Codex 不支持这个 MCP 传输类型")
        }
        if (draft.environmentReferences.size > MAX_MCP_ITEMS || draft.headerReferences.size > MAX_MCP_ITEMS) {
            output += problem("changes[$index].server", "MCP 环境变量或 Header 数量过多")
        }
        (draft.environmentReferences + draft.headerReferences).forEach { reference ->
            if (!SAFE_ENV_NAME.matches(reference.environmentVariable) || reference.name.isBlank() || reference.name.any(Char::isISOControl)) {
                output += problem("changes[$index].server.references", "MCP 环境变量引用格式无效")
            }
        }
    }

    private fun validateNativeId(
        index: Int,
        field: String,
        value: String,
        output: MutableList<AgentConfigValidationProblem>,
    ) {
        if (!isSafeNativeId(value)) output += problem("changes[$index].$field", "ID 格式无效")
    }

    companion object {
        const val ADAPTER_ID = "codex"
        private const val CONFIG_KEY = "config"
        private const val CONFIG_PATH = "/root/.codex/config.toml"
        private const val RELAY_UPSTREAM_KEY = "relay-upstream"
        private const val RELAY_UPSTREAM_PATH = "/workspace/.kf/secrets/kite.codex-relay-upstream"
        private const val RELAY_API_KEY_KEY = "relay-api-key"
        private const val RELAY_API_KEY_PATH = "/workspace/.kf/secrets/kite.codex-relay-api-key"
        private const val RELAY_BASE_URL = "http://127.0.0.1:4453/v1"
        private const val NATIVE_MODE_OPTION_ID = "mode"
        private const val SKILL_ROOT = "/root/.agents/skills"
        private const val GLOBAL_AGENTS_PATH = "/root/.codex/AGENTS.md"
        private const val GLOBAL_OVERRIDE_PATH = "/root/.codex/AGENTS.override.md"
        private const val MAX_MCP_ITEMS = 64
        private const val MAX_MCP_TEXT = 2_048
        private const val MAX_RELAY_UPSTREAM_BYTES = 4 * 1024
        private const val MAX_RELAY_API_KEY_BYTES = 64 * 1024
        private val CODEX_PERMISSION_LEVELS = mapOf(
            "read-only" to AgentPermissionLevel.ReadOnly,
            "agent" to AgentPermissionLevel.Approval,
            "agent-full-access" to AgentPermissionLevel.Full,
        )
        private val SAFE_IMPORT_REFERENCE = Regex("kite-import:import-[A-Za-z0-9-]{8,80}")
        private val SAFE_ENV_NAME = Regex("[A-Za-z_][A-Za-z0-9_]{0,127}")
        private val CODEX_COMPOSITE_MODEL = Regex("^(.+)\\[([^]\\r\\n]+)]$")
        private val CODEX_FALLBACK_EFFORTS = setOf(
            "none", "minimal", "low", "medium", "high", "xhigh", "max", "ultra",
        )
        private fun stripUnverifiedCodexEffortSuffix(value: String): String {
            val match = CODEX_COMPOSITE_MODEL.matchEntire(value) ?: return value
            return match.groupValues[1]
                .takeIf { match.groupValues[2].lowercase() in CODEX_FALLBACK_EFFORTS }
                ?: value
        }
        private fun sameProviderEndpoint(left: String?, right: String?): Boolean =
            !left.isNullOrBlank() && !right.isNullOrBlank() &&
                left.trim().trimEnd('/') == right.trim().trimEnd('/')
        private fun tomlPathKey(value: String): String =
            if (Regex("[A-Za-z0-9_-]+").matches(value)) value
            else "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
    }
}

private data class TomlTextEditor(val text: String) {
    fun setTopString(key: String, value: String?): TomlTextEditor =
        copy(text = setField(text, key, value?.let(::tomlQuote), sectionHeader = null))

    fun setSectionString(providerId: String, key: String, value: String?): TomlTextEditor =
        copy(text = setField(text, key, value?.let(::tomlQuote), providerHeader(providerId)))

    fun setSectionBoolean(providerId: String, key: String, value: Boolean?): TomlTextEditor =
        copy(text = setField(text, key, value?.toString(), providerHeader(providerId)))

    fun removeProviderSection(providerId: String): TomlTextEditor {
        val lines = normalizedLines(text)
        val range = sectionRange(lines, providerHeader(providerId)) ?: return this
        return copy(text = normalizeToml((lines.subList(0, range.first) + lines.subList(range.last + 1, lines.size)).joinToString("\n")))
    }

    fun setMcpServer(draft: AgentMcpDraft): TomlTextEditor {
        var editor = this
        MCP_MANAGED_KEYS.forEach { key -> editor = editor.setMcpField(draft.id, key, null) }
        editor = editor.setMcpField(draft.id, "enabled", draft.enabled.toString())
        editor = when (draft.transport) {
            AgentMcpTransport.Stdio -> editor
                .setMcpField(draft.id, "command", tomlQuote(requireNotNull(draft.command).trim()))
                .setMcpField(draft.id, "args", tomlStringArray(draft.arguments))
                .setMcpField(draft.id, "cwd", draft.workingDirectory?.trim()?.takeIf(String::isNotBlank)?.let(::tomlQuote))
                .setMcpField(
                    draft.id,
                    "env_vars",
                    tomlStringArray(draft.environmentReferences.map(AgentMcpEnvironmentReference::environmentVariable)),
                )
            AgentMcpTransport.StreamableHttp -> editor
                .setMcpField(draft.id, "url", tomlQuote(requireNotNull(draft.url).trim()))
                .setMcpField(
                    draft.id,
                    "env_http_headers",
                    tomlStringMap(draft.headerReferences.associate { it.name to it.environmentVariable }),
                )
            else -> error("已由 Codex MCP 校验限制传输类型")
        }
        return editor
    }

    fun setMcpBoolean(serverId: String, key: String, value: Boolean?): TomlTextEditor =
        setMcpField(serverId, key, value?.toString())

    fun removeMcpServer(serverId: String): TomlTextEditor {
        val lines = normalizedLines(text)
        val range = sectionFamilyRange(lines, mcpHeader(serverId)) ?: return this
        return copy(
            text = normalizeToml(
                (lines.subList(0, range.first) + lines.subList(range.last + 1, lines.size)).joinToString("\n"),
            ),
        )
    }

    fun setSkillEnabled(path: String, enabled: Boolean): TomlTextEditor {
        val lines = normalizedLines(text).toMutableList()
        val headers = lines.indices.filter { lines[it].trim() == SKILL_HEADER }
        headers.forEachIndexed { headerIndex, start ->
            val endExclusive = headers.getOrNull(headerIndex + 1)
                ?: (start + 1 until lines.size).firstOrNull { lines[it].trimStart().startsWith('[') }
                ?: lines.size
            val pathLine = (start + 1 until endExclusive).firstOrNull { SKILL_PATH_FIELD.containsMatchIn(lines[it]) }
                ?: return@forEachIndexed
            val existingPath = TOML_STRING_VALUE.find(lines[pathLine])?.groupValues?.get(1)?.let(::tomlUnescape)
            if (existingPath != path) return@forEachIndexed
            val enabledLine = (start + 1 until endExclusive).firstOrNull { SKILL_ENABLED_FIELD.containsMatchIn(lines[it]) }
            if (enabledLine == null) lines.add(endExclusive, "enabled = $enabled") else lines[enabledLine] = "enabled = $enabled"
            return copy(text = normalizeToml(lines.joinToString("\n")))
        }
        if (lines.any(String::isNotBlank)) lines += ""
        lines += SKILL_HEADER
        lines += "path = ${tomlQuote(path)}"
        lines += "enabled = $enabled"
        return copy(text = normalizeToml(lines.joinToString("\n")))
    }

    private fun setMcpField(serverId: String, key: String, rendered: String?): TomlTextEditor =
        copy(text = setField(text, key, rendered, mcpHeader(serverId)))

    private fun setField(source: String, key: String, rendered: String?, sectionHeader: String?): String {
        val lines = normalizedLines(source).toMutableList()
        val range = if (sectionHeader == null) topRange(lines) else sectionRange(lines, sectionHeader)
        if (range == null && sectionHeader != null) {
            if (rendered == null) return normalizeToml(lines.joinToString("\n"))
            if (lines.any(String::isNotBlank)) lines += ""
            lines += sectionHeader
            lines += "$key = $rendered"
            return normalizeToml(lines.joinToString("\n"))
        }
        val actual = requireNotNull(range)
        val regex = Regex("^\\s*${Regex.escape(key)}\\s*=")
        val index = (actual.first..actual.last).firstOrNull { regex.containsMatchIn(lines[it]) }
        if (index != null) {
            if (rendered == null) lines.removeAt(index) else lines[index] = "$key = $rendered"
        } else if (rendered != null) {
            val insertAt = actual.last + 1
            lines.add(insertAt.coerceAtMost(lines.size), "$key = $rendered")
        }
        return normalizeToml(lines.joinToString("\n"))
    }

    private fun topRange(lines: List<String>): IntRange {
        val end = lines.indexOfFirst { it.trimStart().startsWith('[') }.let { if (it < 0) lines.lastIndex else it - 1 }
        return 0..end.coerceAtLeast(0)
    }

    private fun sectionRange(lines: List<String>, header: String): IntRange? {
        val start = lines.indexOfFirst { it.trim() == header }
        if (start < 0) return null
        val next = (start + 1 until lines.size).firstOrNull { lines[it].trimStart().startsWith('[') } ?: lines.size
        return start until next
    }

    private fun sectionFamilyRange(lines: List<String>, header: String): IntRange? {
        val start = lines.indexOfFirst { it.trim() == header }
        if (start < 0) return null
        val nestedPrefix = header.removeSuffix("]") + "."
        val next = (start + 1 until lines.size).firstOrNull { index ->
            val candidate = lines[index].trim()
            candidate.startsWith('[') && candidate != header && !candidate.startsWith(nestedPrefix)
        } ?: lines.size
        return start until next
    }

    private companion object {
        fun normalizedLines(value: String): List<String> = value.replace("\r\n", "\n").replace('\r', '\n').split('\n')
        fun normalizeToml(value: String): String = value.trimEnd() + "\n"
        fun tomlQuote(value: String): String = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\""
        fun tomlKey(value: String): String = if (Regex("[A-Za-z0-9_-]+").matches(value)) value else tomlQuote(value)
        fun providerHeader(id: String): String = "[model_providers.${tomlKey(id)}]"
        fun mcpHeader(id: String): String = "[mcp_servers.${tomlKey(id)}]"
        fun tomlStringArray(values: List<String>): String? = values.takeIf { it.isNotEmpty() }
            ?.joinToString(prefix = "[", postfix = "]") { tomlQuote(it) }
        fun tomlStringMap(values: Map<String, String>): String? = values.takeIf { it.isNotEmpty() }
            ?.entries
            ?.sortedBy { it.key }
            ?.joinToString(prefix = "{ ", postfix = " }") { (key, value) -> "${tomlKey(key)} = ${tomlQuote(value)}" }
        fun tomlUnescape(value: String): String = value.replace("\\n", "\n").replace("\\\"", "\"").replace("\\\\", "\\")

        const val SKILL_HEADER = "[[skills.config]]"
        val SKILL_PATH_FIELD = Regex("^\\s*path\\s*=")
        val SKILL_ENABLED_FIELD = Regex("^\\s*enabled\\s*=")
        val TOML_STRING_VALUE = Regex("=\\s*\"((?:\\\\.|[^\"])*)\"")
        val MCP_MANAGED_KEYS = listOf(
            "command",
            "args",
            "cwd",
            "env_vars",
            "url",
            "bearer_token_env_var",
            "env_http_headers",
            "enabled",
        )
    }
}

internal class HermesAgentConfigAdapter(
    context: Context,
    containerProvider: () -> ContainerRecord? = {
        WorkSurfaceRuntimeBridge.getSavedContainer(context.applicationContext)
    },
    fileStore: AtomicConfigFileStore = AtomicConfigFileStore()
) : NativeAgentConfigAdapter(
    context,
    ADAPTER_ID,
    linkedMapOf(CONFIG_KEY to CONFIG_PATH),
    CONFIG_KEY,
    containerProvider,
    fileStore
) {
    private val yaml = Yaml(
        SafeConstructor(LoaderOptions().apply {
            maxAliasesForCollections = 50
            nestingDepthLimit = 32
            codePointLimit = 8 * 1024 * 1024
        }),
        org.yaml.snakeyaml.representer.Representer(DumperOptions()),
        DumperOptions().apply {
            defaultFlowStyle = DumperOptions.FlowStyle.BLOCK
            isPrettyFlow = true
            indent = 2
            indicatorIndent = 0
            width = 120
        }
    )
    private val skillDirectory = NativeAgentSkillDirectory(
        project = projection::resolve,
        roots = listOf(SKILL_ROOT),
    )

    override fun displayName(): String = "Hermes"

    override fun reasoningControl(): AgentReasoningControl = AgentReasoningControls.Hermes

    override fun sessionPermissionControl(): AgentSessionPermissionControl =
        mediatedSessionPermissionControl(
            profiles = HERMES_PERMISSION_PROFILES,
            handlingByProfileId = mapOf(
                "manual" to AgentSessionPermissionHandling.AskUser,
                "smart" to AgentSessionPermissionHandling.PreserveAgentDecision,
                "off" to AgentSessionPermissionHandling.AllowRequest,
            ),
            initialProfileId = DEFAULT_APPROVAL_MODE,
        )

    override fun nativeCoreDocuments(workspacePath: String?): List<NativeAgentCoreDocumentSpec> = buildList {
        add(NativeAgentCoreDocumentSpec(
            id = "hermes-soul",
            displayName = "人格与身份",
            fileName = "SOUL.md",
            containerPath = "$HERMES_HOME_PATH/SOUL.md",
            scope = AgentConfigScope.User,
            semantics = AgentCoreDocumentSemantics.Persona,
            priorityDescription = "作为 Hermes 系统提示的第一层身份；非空时替代内置身份",
        ))
        add(NativeAgentCoreDocumentSpec(
            id = "hermes-user",
            displayName = "用户资料",
            fileName = "USER.md",
            containerPath = "$HERMES_HOME_PATH/memories/USER.md",
            scope = AgentConfigScope.User,
            semantics = AgentCoreDocumentSemantics.UserProfile,
            priorityDescription = "作为跨会话用户资料进入 Hermes 的可变上下文",
        ))
        projectCoreDocument(
            workspacePath,
            id = "hermes-project-agents",
            displayName = "当前项目说明",
            fileName = "AGENTS.md",
            priorityDescription = "当前工作目录的项目规则；与人格文件分开加载",
        )?.let(::add)
    }

    override fun nativeCapabilities(): AgentConfigCapabilities = AgentConfigCapabilities(
        supported = setOf(
            AgentPersistentConfigCapability.DefaultModel,
            AgentPersistentConfigCapability.Provider,
            AgentPersistentConfigCapability.ProviderProfiles,
            AgentPersistentConfigCapability.PermissionProfiles,
            AgentPersistentConfigCapability.CredentialStatus,
            AgentPersistentConfigCapability.Mcp,
            AgentPersistentConfigCapability.Skill,
            AgentPersistentConfigCapability.CoreDocuments,
        ),
        credentialOwnership = AgentCredentialOwnership.AgentOwned,
        mcpOperations = setOf(
            AgentMcpOperation.Create,
            AgentMcpOperation.Edit,
            AgentMcpOperation.Enable,
            AgentMcpOperation.Disable,
            AgentMcpOperation.Remove,
        ),
        mcpTransports = setOf(
            AgentMcpTransport.Stdio,
            AgentMcpTransport.StreamableHttp,
            AgentMcpTransport.Sse,
        ),
        skillOperations = setOf(
            AgentSkillOperation.Import,
            AgentSkillOperation.Enable,
            AgentSkillOperation.Disable,
            AgentSkillOperation.Remove,
        ),
    )

    override fun decode(files: Map<String, ByteArray>): NativeState {
        val root = yamlMap(files.getValue(CONFIG_KEY))
        val model = root.map("model")
        val providerId = model.string("provider")
        val defaultModel = model.string("default") ?: model.string("model")
        val customProviders = root.list("custom_providers").mapNotNull { value ->
            val provider = value as? Map<*, *> ?: return@mapNotNull null
            val id = provider.string("name")?.takeIf(String::isNotBlank) ?: return@mapNotNull null
            val models = when (val source = provider["models"]) {
                is Map<*, *> -> source.keys.mapNotNull { it as? String }.map { AgentProviderModelSummary(it, it) }
                is List<*> -> source.mapNotNull { it as? String }.map { AgentProviderModelSummary(it, it) }
                else -> listOfNotNull(provider.string("model")?.let { AgentProviderModelSummary(it, it) })
            }
            AgentProviderSummary(
                id = id,
                displayName = id,
                baseUrl = provider.string("base_url"),
                models = models,
                credentialPresence = if (!provider.string("api_key").isNullOrBlank() || !provider.string("key_env").isNullOrBlank()) {
                    AgentCredentialPresence.Present
                } else AgentCredentialPresence.Missing
            )
        }.sortedBy(AgentProviderSummary::id)
        val providers = if (customProviders.isNotEmpty()) customProviders else providerId?.let { active ->
            listOf(
                AgentProviderSummary(
                    id = active,
                    displayName = active,
                    baseUrl = model.string("base_url"),
                    models = listOfNotNull(defaultModel?.let { AgentProviderModelSummary(it, it) }),
                    credentialPresence = if (!model.string("api_key").isNullOrBlank()) AgentCredentialPresence.Present
                    else AgentCredentialPresence.Missing
                )
            )
        }.orEmpty()
        val approvalMode = root.map(APPROVALS_KEY).string(MODE_KEY)
        val activePermissionProfileId = when {
            approvalMode == null -> DEFAULT_APPROVAL_MODE
            approvalMode in HERMES_PERMISSION_PROFILE_IDS -> approvalMode
            else -> null
        }
        val warnings = buildList {
            if (root["providers"] is Map<*, *>) {
                add("Hermes 原生 providers 字典由 Hermes 自身管理；Kite 只编辑 custom_providers")
            }
            if (approvalMode != null && activePermissionProfileId == null) {
                add("Hermes approvals.mode 不是官方支持值，Kite 不会替换或猜测")
            }
        }
        val disabledSkills = hermesDisabledSkills(root)
        return NativeState(
            defaultModel,
            providers,
            overallCredential(providers),
            warnings,
            activeProviderId = providerId?.takeIf { active -> providers.any { it.id == active } },
            activePermissionProfileId = activePermissionProfileId,
            permissionProfiles = HERMES_PERMISSION_PROFILES,
            mcpServers = hermesMcpServers(root.map(MCP_SERVERS_KEY)),
            skills = skillDirectory.summaries(
                activation = { entry ->
                    if (entry.id in disabledSkills) AgentSkillActivation.Disabled else AgentSkillActivation.Enabled
                },
                activationOperations = setOf(AgentSkillOperation.Enable, AgentSkillOperation.Disable),
            ),
        )
    }

    override fun validateNativeChange(
        index: Int,
        change: AgentPersistentConfigChange,
        output: MutableList<AgentConfigValidationProblem>,
    ) {
        when (change) {
            is AgentPersistentConfigChange.SetPermissionProfile -> {
                if (change.profileId !in HERMES_PERMISSION_PROFILE_IDS) {
                    output += problem("changes[$index].profileId", "Hermes 不支持这个官方权限档位")
                }
            }
            is AgentPersistentConfigChange.ConfigureMcpServer -> validateHermesMcpDraft(index, change.server, output)
            is AgentPersistentConfigChange.SetMcpEnabled -> validateHermesId(index, "serverId", change.serverId, output)
            is AgentPersistentConfigChange.RemoveMcpServer -> validateHermesId(index, "serverId", change.serverId, output)
            is AgentPersistentConfigChange.InstallSkill -> {
                validateHermesId(index, "skillId", change.skillId, output)
                if (!SAFE_IMPORT_REFERENCE.matches(change.sourceReference)) {
                    output += problem("changes[$index].sourceReference", "Skill 来源引用格式无效")
                }
            }
            is AgentPersistentConfigChange.SetSkillActivation -> {
                validateHermesId(index, "skillId", change.skillId, output)
                if (change.activation !in HERMES_SKILL_ACTIVATIONS) {
                    output += problem("changes[$index].activation", "Hermes 只支持启用或停用 Skill")
                }
            }
            is AgentPersistentConfigChange.RemoveSkill -> validateHermesId(index, "skillId", change.skillId, output)
            else -> super.validateNativeChange(index, change, output)
        }
    }

    override suspend fun applyExternalChanges(
        request: AgentConfigApplyRequest,
        before: AgentLiveConfigSnapshot,
    ): AgentConfigApplyResult? {
        val fileChanges = request.changes.filter {
            it is AgentPersistentConfigChange.InstallSkill || it is AgentPersistentConfigChange.RemoveSkill
        }
        if (fileChanges.isEmpty()) return null
        if (request.changes.size != 1 || fileChanges.size != 1) {
            return AgentConfigApplyResult.Rejected(
                listOf(problem("changes", "Skill 文件变更一次只能执行一项，不能和其他配置混合")),
            )
        }
        skillDirectory.applyFileChange(fileChanges.single())?.let { return it }
        return refreshedApplyResult(request.agentId)
    }

    override fun nativeRevisionInputs(): List<Pair<String, String>> = skillDirectory.revisionInputs()

    override fun mutate(files: Map<String, ByteArray>, changes: List<AgentPersistentConfigChange>): Map<String, ByteArray> {
        val original = files.getValue(CONFIG_KEY).toString(Charsets.UTF_8)
        val root = yamlMap(files.getValue(CONFIG_KEY)).toMutableMap()
        val model = root.map("model").toMutableMap()
        val providers = root.list("custom_providers").mapNotNull { value ->
            (value as? Map<*, *>)?.entries?.associateTo(linkedMapOf()) { it.key.toString() to it.value }
        }.toMutableList()
        var mcpChanged = false
        var skillsChanged = false
        var approvalsChanged = false
        changes.forEach { change ->
            when (change) {
                is AgentPersistentConfigChange.SetDefaultModel -> {
                    if (change.modelId == null) model.remove("default") else model["default"] = change.modelId
                }
                is AgentPersistentConfigChange.SelectProvider -> {
                    model["provider"] = change.providerId
                    model["default"] = change.modelId
                }
                is AgentPersistentConfigChange.ConfigureProvider -> {
                    val draft = change.provider
                    val index = providers.indexOfFirst { it["name"] == draft.id }
                    val entry = LinkedHashMap<String, Any?>(providers.getOrNull(index).orEmpty()).apply {
                        this["name"] = draft.id
                        this["base_url"] = draft.baseUrl.trim()
                        this["models"] = linkedMapOf<String, Any?>().also { models ->
                            draft.models.forEach { models[it.id.trim()] = linkedMapOf<String, Any?>() }
                        }
                        when (val credential = change.credential) {
                            AgentProviderCredentialChange.Keep -> Unit
                            is AgentProviderCredentialChange.Replace -> {
                                this["api_key"] = credential.secret
                                remove("key_env")
                            }
                            AgentProviderCredentialChange.Remove -> {
                                remove("api_key")
                                remove("key_env")
                            }
                        }
                    }
                    if (index >= 0) providers[index] = entry else providers.add(entry)
                    if (model["default"] == null) {
                        model["provider"] = draft.id
                        model["default"] = draft.models.first().id.trim()
                    }
                }
                is AgentPersistentConfigChange.RemoveProvider -> {
                    providers.removeAll { it["name"] == change.providerId }
                    if (model["provider"] == change.providerId) {
                        model.remove("provider")
                        model.remove("default")
                    }
                }
                is AgentPersistentConfigChange.SetPermissionProfile -> {
                    val approvals = root.mutableMap(APPROVALS_KEY)
                    approvals[MODE_KEY] = change.profileId
                    root[APPROVALS_KEY] = approvals
                    approvalsChanged = true
                }
                is AgentPersistentConfigChange.ConfigureMcpServer -> {
                    configureHermesMcp(root, change.server)
                    mcpChanged = true
                }
                is AgentPersistentConfigChange.SetMcpEnabled -> {
                    val servers = root.mutableMap(MCP_SERVERS_KEY)
                    val server = servers.mutableMap(change.serverId)
                    server[ENABLED_KEY] = change.enabled
                    servers[change.serverId] = server
                    root[MCP_SERVERS_KEY] = servers
                    mcpChanged = true
                }
                is AgentPersistentConfigChange.RemoveMcpServer -> {
                    val servers = root.mutableMap(MCP_SERVERS_KEY)
                    servers.remove(change.serverId)
                    root[MCP_SERVERS_KEY] = servers
                    mcpChanged = true
                }
                is AgentPersistentConfigChange.SetSkillActivation -> {
                    setHermesSkillActivation(root, change.skillId, change.activation)
                    skillsChanged = true
                }
                else -> Unit
            }
        }
        val sections = linkedMapOf<String, Any?>("model" to model, "custom_providers" to providers)
        if (mcpChanged) sections[MCP_SERVERS_KEY] = root[MCP_SERVERS_KEY]
        if (skillsChanged) sections[SKILLS_KEY] = root[SKILLS_KEY]
        if (approvalsChanged) sections[APPROVALS_KEY] = root[APPROVALS_KEY]
        val next = replaceYamlSections(
            original,
            sections,
        )
        return mapOf(CONFIG_KEY to next.toByteArray(Charsets.UTF_8))
    }

    private fun hermesMcpServers(section: Map<String, Any?>): List<AgentMcpSummary> = section.mapNotNull { (id, value) ->
        val server = value.asStringMap() ?: return@mapNotNull null
        val command = server.string(COMMAND_KEY)
        val url = server.string(URL_KEY)
        val nativeTransport = server.string(TRANSPORT_KEY)
        val transport = when {
            !command.isNullOrBlank() -> AgentMcpTransport.Stdio
            url.isNullOrBlank() -> AgentMcpTransport.Unknown
            nativeTransport == SSE_TYPE -> AgentMcpTransport.Sse
            nativeTransport.isNullOrBlank() -> AgentMcpTransport.StreamableHttp
            else -> AgentMcpTransport.Unknown
        }
        val enabled = server.boolean(ENABLED_KEY) ?: true
        AgentMcpSummary(
            id = id,
            kind = nativeTransport ?: if (command != null) STDIO_TYPE else "remote",
            enabled = enabled,
            transport = transport,
            command = command,
            arguments = server.stringList(ARGS_KEY),
            url = url,
            environmentReferences = hermesReferences(server.map(ENV_KEY)),
            headerReferences = hermesReferences(server.map(HEADERS_KEY)),
            scope = AgentConfigScope.User,
            allowedOperations = buildSet {
                if (transport != AgentMcpTransport.Unknown) add(AgentMcpOperation.Edit)
                add(if (enabled) AgentMcpOperation.Disable else AgentMcpOperation.Enable)
                add(AgentMcpOperation.Remove)
            },
        )
    }.sortedBy(AgentMcpSummary::id)

    private fun hermesReferences(values: Map<String, Any?>): List<AgentMcpEnvironmentReference> = values.mapNotNull { (name, value) ->
        val variable = (value as? String)?.let(::hermesReferenceVariable) ?: return@mapNotNull null
        AgentMcpEnvironmentReference(name, variable)
    }.sortedBy(AgentMcpEnvironmentReference::name)

    private fun hermesReferenceVariable(value: String): String? =
        HERMES_ENV_REFERENCE.matchEntire(value.trim())?.groupValues?.get(1)
            ?: HERMES_BEARER_REFERENCE.matchEntire(value.trim())?.groupValues?.get(1)

    private fun configureHermesMcp(root: MutableMap<String, Any?>, draft: AgentMcpDraft) {
        val servers = root.mutableMap(MCP_SERVERS_KEY)
        val server = servers.mutableMap(draft.id)
        when (draft.transport) {
            AgentMcpTransport.Stdio -> {
                server[COMMAND_KEY] = requireNotNull(draft.command).trim()
                server[ARGS_KEY] = draft.arguments.toList()
                server.remove(URL_KEY)
                server.remove(TRANSPORT_KEY)
                server.remove(HEADERS_KEY)
                mergeHermesReferences(server, ENV_KEY, draft.environmentReferences, authorizationBearer = false)
            }
            AgentMcpTransport.StreamableHttp, AgentMcpTransport.Sse -> {
                server[URL_KEY] = requireNotNull(draft.url).trim()
                if (draft.transport == AgentMcpTransport.Sse) server[TRANSPORT_KEY] = SSE_TYPE
                else server.remove(TRANSPORT_KEY)
                server.remove(COMMAND_KEY)
                server.remove(ARGS_KEY)
                server.remove(ENV_KEY)
                mergeHermesReferences(server, HEADERS_KEY, draft.headerReferences, authorizationBearer = true)
            }
            else -> error("已由 Hermes MCP 校验限制传输类型")
        }
        server[ENABLED_KEY] = draft.enabled
        servers[draft.id] = server
        root[MCP_SERVERS_KEY] = servers
    }

    private fun mergeHermesReferences(
        server: MutableMap<String, Any?>,
        field: String,
        references: List<AgentMcpEnvironmentReference>,
        authorizationBearer: Boolean,
    ) {
        val values = server.mutableMap(field)
        val desired = references.associateBy(AgentMcpEnvironmentReference::name)
        values.entries.toList().forEach { (name, value) ->
            if ((value as? String)?.let(::hermesReferenceVariable) != null && name !in desired) values.remove(name)
        }
        references.forEach { reference ->
            val current = (values[reference.name] as? String)?.let(::hermesReferenceVariable)
            if (current != reference.environmentVariable) {
                values[reference.name] = if (
                    authorizationBearer && reference.name.equals(AUTHORIZATION_HEADER, ignoreCase = true)
                ) {
                    "Bearer \${${reference.environmentVariable}}"
                } else {
                    "\${${reference.environmentVariable}}"
                }
            }
        }
        if (values.isEmpty()) server.remove(field) else server[field] = values
    }

    private fun hermesDisabledSkills(root: Map<String, Any?>): Set<String> = when (val disabled = root.map(SKILLS_KEY)[DISABLED_KEY]) {
        is String -> setOf(disabled.trim()).filterTo(linkedSetOf(), String::isNotBlank)
        is Iterable<*> -> disabled.mapNotNullTo(linkedSetOf()) { it?.toString()?.trim()?.takeIf(String::isNotBlank) }
        else -> emptySet()
    }

    private fun setHermesSkillActivation(
        root: MutableMap<String, Any?>,
        skillId: String,
        activation: AgentSkillActivation,
    ) {
        if (skillDirectory.discover().none { it.id == skillId }) error("Skill 已不存在，请重新读取")
        val skills = root.mutableMap(SKILLS_KEY)
        val disabled = hermesDisabledSkills(root).toMutableSet()
        if (activation == AgentSkillActivation.Disabled) disabled += skillId else disabled -= skillId
        skills[DISABLED_KEY] = disabled.sorted()
        root[SKILLS_KEY] = skills
    }

    private fun validateHermesMcpDraft(
        index: Int,
        draft: AgentMcpDraft,
        output: MutableList<AgentConfigValidationProblem>,
    ) {
        validateHermesId(index, "server.id", draft.id, output)
        when (draft.transport) {
            AgentMcpTransport.Stdio -> {
                if (draft.command.isNullOrBlank() || draft.command.any(Char::isISOControl)) {
                    output += problem("changes[$index].server.command", "本地 MCP 必须提供有效命令")
                }
                if (draft.arguments.any { value -> value.any(Char::isISOControl) }) {
                    output += problem("changes[$index].server.arguments", "MCP 参数不能包含控制字符")
                }
            }
            AgentMcpTransport.StreamableHttp, AgentMcpTransport.Sse -> {
                val uri = runCatching { URI(draft.url.orEmpty()) }.getOrNull()
                if (uri == null || uri.scheme !in setOf("http", "https") || uri.host.isNullOrBlank() || uri.userInfo != null) {
                    output += problem("changes[$index].server.url", "远程 MCP 必须提供有效 HTTP 或 HTTPS 地址")
                }
            }
            else -> output += problem("changes[$index].server.transport", "Hermes 不支持这个 MCP 传输类型")
        }
        if (draft.arguments.size > MAX_MCP_ITEMS || draft.environmentReferences.size > MAX_MCP_ITEMS || draft.headerReferences.size > MAX_MCP_ITEMS) {
            output += problem("changes[$index].server", "MCP 参数、环境变量或 Header 数量过多")
        }
        (draft.environmentReferences + draft.headerReferences).forEach { reference ->
            if (reference.name.isBlank() || reference.name.any(Char::isISOControl) || !SAFE_ENV_NAME.matches(reference.environmentVariable)) {
                output += problem("changes[$index].server.references", "MCP 环境变量引用格式无效")
            }
        }
    }

    private fun validateHermesId(
        index: Int,
        field: String,
        value: String,
        output: MutableList<AgentConfigValidationProblem>,
    ) {
        if (!isSafeNativeId(value)) output += problem("changes[$index].$field", "ID 格式无效")
    }

    private suspend fun refreshedApplyResult(agentId: String): AgentConfigApplyResult = when (val refreshed = readLive(agentId)) {
        is AgentConfigReadResult.Ready -> AgentConfigApplyResult.Applied(refreshed.snapshot, backupReference = null)
        is AgentConfigReadResult.Failed -> AgentConfigApplyResult.Failed(refreshed.message, restored = false)
        is AgentConfigReadResult.Unavailable -> AgentConfigApplyResult.Unavailable(refreshed.discovery)
    }

    override fun validateBytes(key: String, bytes: ByteArray): String? = runCatching { yamlMap(bytes); null }
        .getOrElse { "Hermes 原生 config.yaml 格式无效" }

    private fun yamlMap(bytes: ByteArray): Map<String, Any?> {
        val text = bytes.toString(Charsets.UTF_8)
        if (text.isBlank()) return emptyMap()
        val loaded = yaml.load<Any?>(text) ?: return emptyMap()
        require(loaded is Map<*, *>) { "Hermes config.yaml 顶层必须是对象" }
        return loaded.entries.associateTo(linkedMapOf()) { it.key.toString() to it.value }
    }

    private fun replaceYamlSections(source: String, sections: LinkedHashMap<String, Any?>): String {
        var result = source.replace("\r\n", "\n").replace('\r', '\n')
        sections.forEach { (key, value) ->
            val dumped = yaml.dump(linkedMapOf(key to value)).trimEnd() + "\n"
            val range = yamlSectionRange(result, key)
            result = if (range == null) {
                result.trimEnd().let { existing -> if (existing.isBlank()) dumped else "$existing\n\n$dumped" }
            } else {
                result.substring(0, range.first) + dumped + result.substring(range.last + 1)
            }
        }
        return result.trimEnd() + "\n"
    }

    private fun yamlSectionRange(source: String, key: String): IntRange? {
        val linePattern = Regex("(?m)^${Regex.escape(key)}:(?:[ \\t].*)?(?:\\n|$)")
        val startMatch = linePattern.find(source) ?: return null
        val next = Regex("(?m)^[A-Za-z0-9_.-]+:(?:[ \\t].*)?(?:\\n|$)")
            .find(source, startMatch.range.last + 1)
        val endExclusive = next?.range?.first ?: source.length
        return startMatch.range.first until endExclusive
    }

    private fun Map<*, *>.string(key: String): String? = this[key] as? String
    private fun Map<*, *>.boolean(key: String): Boolean? = this[key] as? Boolean
    private fun Map<*, *>.stringList(key: String): List<String> = (this[key] as? Iterable<*>)
        ?.mapNotNull { it as? String }
        .orEmpty()
    private fun Any?.asStringMap(): Map<String, Any?>? = (this as? Map<*, *>)
        ?.entries
        ?.associateTo(linkedMapOf()) { it.key.toString() to it.value }
    private fun Map<String, Any?>.map(key: String): Map<String, Any?> =
        (this[key] as? Map<*, *>)?.entries?.associateTo(linkedMapOf()) { it.key.toString() to it.value }.orEmpty()
    private fun Map<String, Any?>.list(key: String): List<Any?> = this[key] as? List<Any?> ?: emptyList()
    private fun MutableMap<String, Any?>.mutableMap(key: String): LinkedHashMap<String, Any?> =
        (this[key] as? Map<*, *>)?.entries?.associateTo(linkedMapOf()) { it.key.toString() to it.value }
            ?: linkedMapOf()

    companion object {
        const val ADAPTER_ID = "hermes"
        private const val CONFIG_KEY = "config"
        private const val HERMES_HOME_PATH = "/workspace/.kf/software/kite.hermes.core/home"
        private const val CONFIG_PATH = "$HERMES_HOME_PATH/config.yaml"
        private const val MCP_SERVERS_KEY = "mcp_servers"
        private const val COMMAND_KEY = "command"
        private const val ARGS_KEY = "args"
        private const val ENV_KEY = "env"
        private const val URL_KEY = "url"
        private const val TRANSPORT_KEY = "transport"
        private const val HEADERS_KEY = "headers"
        private const val ENABLED_KEY = "enabled"
        private const val STDIO_TYPE = "stdio"
        private const val SSE_TYPE = "sse"
        private const val SKILLS_KEY = "skills"
        private const val DISABLED_KEY = "disabled"
        private const val APPROVALS_KEY = "approvals"
        private const val MODE_KEY = "mode"
        private const val DEFAULT_APPROVAL_MODE = "smart"
        private const val SKILL_ROOT = "$HERMES_HOME_PATH/skills"
        private const val AUTHORIZATION_HEADER = "Authorization"
        private val HERMES_PERMISSION_PROFILES = listOf(
            AgentPermissionProfileSummary(
                id = "manual",
                displayName = "手动（manual）",
                description = "危险命令始终请求用户确认",
                effect = AgentSessionConfigurationEffect.Immediate,
                level = AgentPermissionLevel.Approval,
            ),
            AgentPermissionProfileSummary(
                id = "smart",
                displayName = "智能（smart）",
                description = "低风险命令自动通过，高风险命令自动拒绝，不确定时请求确认",
                effect = AgentSessionConfigurationEffect.Immediate,
                level = AgentPermissionLevel.Smart,
            ),
            AgentPermissionProfileSummary(
                id = "off",
                displayName = "关闭审批（off）",
                description = "关闭危险命令审批；Hermes 的不可恢复操作硬阻止仍然生效",
                effect = AgentSessionConfigurationEffect.Immediate,
                level = AgentPermissionLevel.Full,
            ),
        )
        private val HERMES_PERMISSION_PROFILE_IDS = HERMES_PERMISSION_PROFILES.mapTo(linkedSetOf()) { it.id }
        private const val MAX_MCP_ITEMS = 64
        private val SAFE_IMPORT_REFERENCE = Regex("kite-import:import-[A-Za-z0-9-]{8,80}")
        private val SAFE_ENV_NAME = Regex("[A-Za-z_][A-Za-z0-9_]{0,127}")
        private val HERMES_ENV_REFERENCE = Regex("\\$\\{(?:env:)?([A-Za-z_][A-Za-z0-9_]*)\\}")
        private val HERMES_BEARER_REFERENCE = Regex(
            "Bearer\\s+\\$\\{(?:env:)?([A-Za-z_][A-Za-z0-9_]*)\\}",
            RegexOption.IGNORE_CASE,
        )
        private val HERMES_SKILL_ACTIVATIONS = setOf(AgentSkillActivation.Enabled, AgentSkillActivation.Disabled)
    }
}
