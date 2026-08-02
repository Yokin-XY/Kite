package com.kite.app.agent.config.native

import android.content.Context
import blue.endless.jankson.Jankson
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
import com.kite.app.agent.config.AgentMcpSummary
import com.kite.app.agent.config.AgentPersistentConfigCapability
import com.kite.app.agent.config.AgentPersistentConfigChange
import com.kite.app.agent.config.AgentPermissionProfileSummary
import com.kite.app.agent.config.AgentProviderCredentialChange
import com.kite.app.agent.config.AgentProviderDraft
import com.kite.app.agent.config.AgentProviderSummary
import com.kite.app.agent.config.AgentSkillSummary
import com.kite.app.agent.config.AtomicConfigFileStore
import com.kite.app.agent.config.AtomicConfigFileUpdate
import com.kite.app.agent.config.AtomicConfigFilesWriteResult
import com.kite.app.agent.config.ConfigFileRevision
import com.kite.app.agent.config.ContainerAgentConfigProjection
import com.kite.app.agent.config.NativeAgentCoreDocumentSpec
import com.kite.app.agent.config.NativeAgentCoreDocumentStore
import com.kite.app.agent.contract.AgentConfigCategory
import com.kite.app.agent.contract.AgentConfigChoice
import com.kite.app.agent.contract.AgentConfigOption
import com.kite.app.foundation.contracts.ContainerRecord
import com.kite.app.foundation.workspace.WorkSurfaceRuntimeBridge
import java.io.File
import java.net.URI
import java.security.MessageDigest
import java.util.LinkedHashMap

internal fun projectCoreDocument(
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
