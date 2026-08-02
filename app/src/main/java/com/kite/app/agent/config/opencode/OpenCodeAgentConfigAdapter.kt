package com.kite.app.agent.config.opencode

import android.content.Context
import blue.endless.jankson.Jankson
import blue.endless.jankson.JsonArray
import blue.endless.jankson.JsonElement
import blue.endless.jankson.JsonGrammar
import blue.endless.jankson.JsonNull
import blue.endless.jankson.JsonObject
import blue.endless.jankson.JsonPrimitive
import com.kite.app.agent.config.AgentConfigAdapter
import com.kite.app.agent.config.AgentConfigApplyRequest
import com.kite.app.agent.config.AgentConfigApplyResult
import com.kite.app.agent.config.AgentConfigCapabilities
import com.kite.app.agent.config.AgentConfigCommandExecutionResult
import com.kite.app.agent.config.AgentConfigCommandExecutor
import com.kite.app.agent.config.AgentConfigDiscovery
import com.kite.app.agent.config.AgentConfigDiscoveryState
import com.kite.app.agent.config.AgentConfigReadResult
import com.kite.app.agent.config.AgentConfigScope
import com.kite.app.agent.config.AgentCoreDocumentListResult
import com.kite.app.agent.config.AgentCoreDocumentReadResult
import com.kite.app.agent.config.AgentCoreDocumentSemantics
import com.kite.app.agent.config.AgentCoreDocumentWriteRequest
import com.kite.app.agent.config.AgentCoreDocumentWriteResult
import com.kite.app.agent.config.AgentConfigValidationProblem
import com.kite.app.agent.config.AgentConfigValue
import com.kite.app.agent.config.AgentCredentialOwnership
import com.kite.app.agent.config.AgentCredentialPresence
import com.kite.app.agent.config.AgentLiveConfigSnapshot
import com.kite.app.agent.config.AgentMcpOperation
import com.kite.app.agent.config.AgentMcpConnectionCheckResult
import com.kite.app.agent.config.AgentMcpDraft
import com.kite.app.agent.config.AgentMcpEnvironmentReference
import com.kite.app.agent.config.AgentMcpSummary
import com.kite.app.agent.config.AgentMcpTransport
import com.kite.app.agent.config.AgentPersistentConfigCapability
import com.kite.app.agent.config.AgentPersistentConfigChange
import com.kite.app.agent.config.AgentPermissionProfileSummary
import com.kite.app.agent.contract.AgentPermissionLevel
import com.kite.app.agent.config.AgentSessionPermissionControl
import com.kite.app.agent.config.AgentSessionPermissionHandling
import com.kite.app.agent.config.AgentProviderCredentialChange
import com.kite.app.agent.config.AgentProviderDraft
import com.kite.app.agent.config.AgentProviderModelSummary
import com.kite.app.agent.config.AgentProviderSummary
import com.kite.app.agent.config.AgentReasoningControl
import com.kite.app.agent.config.AgentSkillActivation
import com.kite.app.agent.config.AgentSkillOperation
import com.kite.app.agent.config.AgentSkillSummary
import com.kite.app.agent.config.AgentSessionConfigurationEffect
import com.kite.app.agent.config.AtomicConfigFileStore
import com.kite.app.agent.config.AtomicConfigFileUpdate
import com.kite.app.agent.config.AtomicConfigFileWriteResult
import com.kite.app.agent.config.AtomicConfigFilesWriteResult
import com.kite.app.agent.config.ConfigFileRevision
import com.kite.app.agent.config.ContainerAgentConfigProjection
import com.kite.app.agent.config.NativeAgentCoreDocumentSpec
import com.kite.app.agent.config.NativeAgentCoreDocumentStore
import com.kite.app.agent.config.NATIVE_MODEL_CONFIG_ID
import com.kite.app.agent.config.mediatedSessionPermissionControl
import com.kite.app.agent.contract.AgentConfigCategory
import com.kite.app.agent.contract.AgentConfigChoice
import com.kite.app.agent.contract.AgentConfigOption
import com.kite.app.agent.contract.AgentModelSource
import com.kite.app.foundation.contracts.ContainerRecord
import com.kite.app.foundation.runtime.ProotViewRuntime
import com.kite.app.foundation.runtime.ProotViewStore
import com.kite.app.foundation.workspace.WorkSurfaceRuntimeBridge
import java.io.File
import java.net.URI
import java.security.MessageDigest

/**
 * OpenCode 原生全局配置适配器。
 *
 * 配置的事实源始终是 PRoot 内的 OpenCode 文件；Kite 只做安全读取、定点修改和回填，不保存镜像副本。
 * auth.json 只解析供应商是否存在凭据；密钥值不会进入安全投影、日志或页面状态。
 */
internal class OpenCodeAgentConfigAdapter(
    context: Context,
    private val containerProvider: () -> ContainerRecord? = {
        WorkSurfaceRuntimeBridge.getSavedContainer(context.applicationContext)
    },
    private val fileStore: AtomicConfigFileStore = AtomicConfigFileStore(),
    private val commandExecutor: AgentConfigCommandExecutor? = null
) : AgentConfigAdapter {
    override val adapterId: String = ADAPTER_ID

    private val parser = Jankson.builder().build()
    private val coreDocumentStore = NativeAgentCoreDocumentStore(
        ContainerAgentConfigProjection(containerProvider)::resolve,
        fileStore,
    )
    private val modelCatalogReader = OpenCodeModelCatalogReader(commandExecutor)

    override fun capabilities(): AgentConfigCapabilities = AgentConfigCapabilities(
        supported = setOf(
            AgentPersistentConfigCapability.DefaultModel,
            AgentPersistentConfigCapability.Provider,
            AgentPersistentConfigCapability.ProviderProfiles,
            AgentPersistentConfigCapability.PermissionProfiles,
            AgentPersistentConfigCapability.Mcp,
            AgentPersistentConfigCapability.Skill,
            AgentPersistentConfigCapability.CoreDocuments,
            AgentPersistentConfigCapability.CredentialStatus
        ),
        credentialOwnership = AgentCredentialOwnership.AgentOwned,
        mcpOperations = setOf(
            AgentMcpOperation.Create,
            AgentMcpOperation.Edit,
            AgentMcpOperation.Enable,
            AgentMcpOperation.Disable,
            AgentMcpOperation.Remove
        ) + setOfNotNull(AgentMcpOperation.CheckConnection.takeIf { commandExecutor != null }),
        mcpTransports = setOf(
            AgentMcpTransport.Stdio,
            AgentMcpTransport.RemoteHttpOrSse
        ),
        skillOperations = setOf(AgentSkillOperation.Import)
    )

    override fun sessionPermissionControl(): AgentSessionPermissionControl =
        mediatedSessionPermissionControl(
            profiles = OPEN_CODE_PERMISSION_PROFILES,
            handlingByProfileId = mapOf(
                DENY_ACTION to AgentSessionPermissionHandling.RejectRequest,
                ASK_ACTION to AgentSessionPermissionHandling.AskUser,
                ALLOW_ACTION to AgentSessionPermissionHandling.AllowRequest,
            ),
            initialProfileId = ASK_ACTION,
        )

    override fun reasoningControl(): AgentReasoningControl = openCodeReasoningControl

    override fun defaultModelChange(
        option: AgentConfigOption.Select
    ): AgentPersistentConfigChange.SetDefaultModel? {
        if (option.id !in setOf(MODEL_KEY, NATIVE_MODEL_CONFIG_ID) ||
            option.category != AgentConfigCategory.Model
        ) return null
        val selected = option.currentValue.takeIf { current ->
            option.choices.any { it.value == current } && OPEN_CODE_MODEL_ID.matches(current)
        } ?: return null
        return AgentPersistentConfigChange.SetDefaultModel(selected)
    }

    override fun normalizeSessionConfiguration(
        options: List<AgentConfigOption>
    ): List<AgentConfigOption> = options.map { option ->
        if (option !is AgentConfigOption.Select ||
            option.id != MODEL_KEY ||
            option.category != AgentConfigCategory.Model
        ) {
            return@map option
        }
        option.copy(choices = option.choices.map(::normalizeOpenCodeModelChoice))
    }

    private fun normalizeOpenCodeModelChoice(choice: AgentConfigChoice): AgentConfigChoice {
        val grouped = if (!choice.groupId.isNullOrBlank() || !choice.groupName.isNullOrBlank()) {
            choice
        } else {
            val providerId = choice.value.substringBefore('/').takeIf(String::isNotBlank) ?: return choice
            val modelId = choice.value.substringAfter('/', missingDelimiterValue = "")
            if (modelId.isBlank() || providerId == choice.value) return choice
            val displayParts = choice.name.split('/', limit = 2)
            val providerName = displayParts.firstOrNull()?.takeIf(String::isNotBlank) ?: providerId
            val modelName = displayParts.getOrNull(1)?.takeIf(String::isNotBlank) ?: modelId
            choice.copy(
                name = modelName,
                groupId = providerId,
                groupName = providerName
            )
        }
        return if (grouped.value in modelCatalogReader.cachedNativeValues()) {
            grouped.copy(modelSource = AgentModelSource.Free)
        } else {
            grouped
        }
    }

    override suspend fun discover(agentId: String): AgentConfigDiscovery {
        val paths = resolvePaths()
            ?: return AgentConfigDiscovery(
                agentId = agentId,
                adapterId = adapterId,
                state = AgentConfigDiscoveryState.NoRuntime,
                warnings = listOf("Kite 运行容器尚未创建")
            )
        return runCatching {
            val target = paths.writeTarget()
            AgentConfigDiscovery(
                agentId = agentId,
                adapterId = adapterId,
                state = AgentConfigDiscoveryState.Ready,
                displayLocation = target.containerPath,
                writable = target.writeFile.let { file ->
                    if (file.exists()) file.isFile && file.canWrite()
                    else file.parentFile?.let { parent ->
                        (parent.exists() && parent.isDirectory && parent.canWrite()) ||
                            (!parent.exists() && paths.rootfs.canWrite())
                    } == true
                }
            )
        }.getOrElse {
            AgentConfigDiscovery(
                agentId = agentId,
                adapterId = adapterId,
                state = AgentConfigDiscoveryState.Error,
                warnings = listOf("OpenCode 配置位置不安全或不可访问")
            )
        }
    }

    override suspend fun listCoreDocuments(
        agentId: String,
        workspacePath: String?,
    ): AgentCoreDocumentListResult {
        val discovery = discover(agentId)
        if (discovery.state != AgentConfigDiscoveryState.Ready) {
            return AgentCoreDocumentListResult.Unavailable(discovery)
        }
        return runCatching {
            AgentCoreDocumentListResult.Ready(coreDocumentStore.descriptors(coreDocuments(workspacePath)))
        }.getOrElse { AgentCoreDocumentListResult.Failed("无法读取 OpenCode 核心设定") }
    }

    override suspend fun readCoreDocument(
        agentId: String,
        documentId: String,
        workspacePath: String?,
    ): AgentCoreDocumentReadResult {
        val discovery = discover(agentId)
        if (discovery.state != AgentConfigDiscoveryState.Ready) {
            return AgentCoreDocumentReadResult.Unavailable(discovery)
        }
        return runCatching {
            coreDocumentStore.read(coreDocuments(workspacePath), documentId)
                ?.let(AgentCoreDocumentReadResult::Ready)
                ?: AgentCoreDocumentReadResult.Missing()
        }.getOrElse { AgentCoreDocumentReadResult.Failed("无法读取 OpenCode 核心设定") }
    }

    override suspend fun writeCoreDocument(
        request: AgentCoreDocumentWriteRequest,
    ): AgentCoreDocumentWriteResult {
        val discovery = discover(request.agentId)
        if (discovery.state != AgentConfigDiscoveryState.Ready) {
            return AgentCoreDocumentWriteResult.Unavailable(discovery)
        }
        return runCatching { coreDocumentStore.write(coreDocuments(request.workspacePath), request) }
            .getOrElse { AgentCoreDocumentWriteResult.Failed("无法写入 OpenCode 核心设定", restored = true) }
    }

    private fun coreDocuments(workspacePath: String?): List<NativeAgentCoreDocumentSpec> = buildList {
        add(NativeAgentCoreDocumentSpec(
            id = "opencode-global-agents",
            displayName = "OpenCode 全局说明",
            fileName = "AGENTS.md",
            containerPath = GLOBAL_AGENTS_PATH,
            scope = AgentConfigScope.User,
            semantics = AgentCoreDocumentSemantics.SupplementalInstructions,
            priorityDescription = "所有 OpenCode 工作区优先读取的用户级说明",
        ))
        NativeAgentCoreDocumentStore.projectPath(workspacePath, "AGENTS.md")?.let { path ->
            add(NativeAgentCoreDocumentSpec(
                id = "opencode-project-agents",
                displayName = "当前项目说明",
                fileName = "AGENTS.md",
                containerPath = path,
                scope = AgentConfigScope.Project,
                semantics = AgentCoreDocumentSemantics.SupplementalInstructions,
                priorityDescription = "当前工作目录优先匹配的项目说明",
            ))
        }
    }

    override suspend fun readLive(agentId: String): AgentConfigReadResult {
        val discovery = discover(agentId)
        if (discovery.state != AgentConfigDiscoveryState.Ready) {
            return AgentConfigReadResult.Unavailable(discovery)
        }
        return runCatching {
            AgentConfigReadResult.Ready(enrichPublicModelCatalog(readState(agentId).snapshot))
        }.getOrElse {
            AgentConfigReadResult.Failed("无法读取 OpenCode 原生配置")
        }
    }

    private suspend fun enrichPublicModelCatalog(
        snapshot: AgentLiveConfigSnapshot,
    ): AgentLiveConfigSnapshot = when (val catalog = modelCatalogReader.read()) {
        OpenCodeModelCatalogReadResult.Unsupported -> snapshot
        is OpenCodeModelCatalogReadResult.Failed -> snapshot.copy(
            warnings = (snapshot.warnings + catalog.message).distinct(),
        )
        is OpenCodeModelCatalogReadResult.Ready -> snapshot.withPublicModelCatalog(
            models = catalog.models,
            warning = catalog.warning,
        )
    }

    private fun AgentLiveConfigSnapshot.withPublicModelCatalog(
        models: List<OpenCodeCatalogModel>,
        warning: String?,
    ): AgentLiveConfigSnapshot {
        if (models.isEmpty()) {
            return warning?.let { copy(warnings = (warnings + it).distinct()) } ?: this
        }
        val publicProvider = AgentProviderSummary(
            id = OPEN_CODE_PUBLIC_PROVIDER_ID,
            displayName = OPEN_CODE_PUBLIC_PROVIDER_NAME,
            models = models.map { model ->
                AgentProviderModelSummary(model.modelId, model.displayName)
            },
            credentialPresence = AgentCredentialPresence.Missing,
            source = AgentModelSource.Free,
        )
        val existingIndex = providers.indexOfFirst { it.id == OPEN_CODE_PUBLIC_PROVIDER_ID }
        val nextProviders = if (existingIndex >= 0) {
            providers.toMutableList().also { it[existingIndex] = publicProvider }
        } else {
            providers + publicProvider
        }
        val defaultProviderId = defaultModel
            ?.substringBefore('/')
            ?.takeIf { providerId -> nextProviders.any { it.id == providerId } }
        return copy(
            activeProviderId = defaultProviderId,
            providerIds = nextProviders.map(AgentProviderSummary::id),
            providers = nextProviders,
            warnings = (warnings + listOfNotNull(warning)).distinct(),
        )
    }

    override fun validate(request: AgentConfigApplyRequest): List<AgentConfigValidationProblem> = buildList {
        if (!SAFE_ID.matches(request.agentId)) add(problem("agentId", "Agent ID 格式无效"))
        if (request.expectedRevision.isBlank()) add(problem("expectedRevision", "缺少配置 revision"))
        if (request.changes.isEmpty()) add(problem("changes", "没有待应用的配置变更"))
        request.changes.forEachIndexed { index, change ->
            when (change) {
                is AgentPersistentConfigChange.SetDefaultModel -> {
                    val model = change.modelId
                    if (model != null && !OPEN_CODE_MODEL_ID.matches(model)) {
                        add(problem("changes[$index].modelId", "OpenCode 默认模型必须使用 provider/model 格式"))
                    }
                }
                is AgentPersistentConfigChange.SelectProvider -> {
                    validateId("changes[$index].providerId", change.providerId, this)
                    if (!SAFE_MODEL_COMPONENT.matches(change.modelId)) {
                        add(problem("changes[$index].modelId", "模型 ID 格式无效"))
                    }
                }
                is AgentPersistentConfigChange.PutProvider -> {
                    validateId("changes[$index].providerId", change.providerId, this)
                    validateValue("changes[$index].configuration", change.configuration, this)
                }
                is AgentPersistentConfigChange.ConfigureProvider ->
                    validateProvider("changes[$index]", change.provider, change.credential, this)
                is AgentPersistentConfigChange.RemoveProvider ->
                    validateId("changes[$index].providerId", change.providerId, this)
                is AgentPersistentConfigChange.SetPermissionProfile -> {
                    if (change.profileId !in OPEN_CODE_PERMISSION_ACTIONS) {
                        add(problem("changes[$index].profileId", "OpenCode 不支持这个官方权限动作"))
                    }
                }
                is AgentPersistentConfigChange.PutMcpServer -> {
                    validateId("changes[$index].serverId", change.serverId, this)
                    validateValue("changes[$index].configuration", change.configuration, this)
                }
                is AgentPersistentConfigChange.ConfigureMcpServer ->
                    validateMcpDraft("changes[$index].server", change.server, this)
                is AgentPersistentConfigChange.SetMcpEnabled ->
                    validateId("changes[$index].serverId", change.serverId, this)
                is AgentPersistentConfigChange.RemoveMcpServer ->
                    validateId("changes[$index].serverId", change.serverId, this)
                is AgentPersistentConfigChange.InstallSkill -> {
                    validateId("changes[$index].skillId", change.skillId, this)
                    if (!SAFE_SOURCE_REFERENCE.matches(change.sourceReference)) {
                        add(problem("changes[$index].sourceReference", "Skill 来源引用格式无效"))
                    }
                }
                is AgentPersistentConfigChange.RemoveSkill ->
                    validateId("changes[$index].skillId", change.skillId, this)
                is AgentPersistentConfigChange.SetSkillActivation -> {
                    validateId("changes[$index].skillId", change.skillId, this)
                    if (change.activation !in OPEN_CODE_SKILL_ACTIVATIONS) {
                        add(problem("changes[$index].activation", "OpenCode 不支持这个 Skill 状态"))
                    }
                }
            }
        }
    }

    override suspend fun apply(request: AgentConfigApplyRequest): AgentConfigApplyResult =
        when (val result = applyNative(request)) {
            is AgentConfigApplyResult.Applied -> result.copy(
                snapshot = enrichPublicModelCatalog(result.snapshot),
            )
            else -> result
        }

    private suspend fun applyNative(request: AgentConfigApplyRequest): AgentConfigApplyResult {
        val problems = validate(request)
        if (problems.isNotEmpty()) return AgentConfigApplyResult.Rejected(problems)
        val discovery = discover(request.agentId)
        if (discovery.state != AgentConfigDiscoveryState.Ready) {
            return AgentConfigApplyResult.Unavailable(discovery)
        }

        val before = runCatching { readState(request.agentId) }.getOrElse {
            return AgentConfigApplyResult.Failed("无法读取当前 OpenCode 配置", restored = true)
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
        request.changes.filterIsInstance<AgentPersistentConfigChange.SetMcpEnabled>().firstOrNull()?.let { change ->
            if (before.snapshot.mcpServers.none { it.id == change.serverId }) {
                return AgentConfigApplyResult.Rejected(
                    listOf(problem("serverId", "MCP 已不存在，请重新读取配置"))
                )
            }
        }
        request.changes.filterIsInstance<AgentPersistentConfigChange.SetSkillActivation>().firstOrNull()?.let { change ->
            if (before.snapshot.skills.none { it.id == change.skillId }) {
                return AgentConfigApplyResult.Rejected(
                    listOf(problem("skillId", "Skill 已不存在，请重新读取配置"))
                )
            }
        }
        if (request.changes.any { it is AgentPersistentConfigChange.ConfigureProvider ||
                it is AgentPersistentConfigChange.RemoveProvider && it.removeCredential
            }) {
            return applyProviderTransaction(before, request)
        }
        if (request.changes.any { it is AgentPersistentConfigChange.InstallSkill || it is AgentPersistentConfigChange.RemoveSkill }) {
            return applySkillChanges(before, request)
        }

        val targetDocument = before.targetDocument.clone()
        if (request.changes.any { it is AgentPersistentConfigChange.SetPermissionProfile } &&
            !targetDocument.containsKey(PERMISSION_KEY)
        ) {
            before.effectivePermission?.let { permission ->
                putPreservingComment(targetDocument, PERMISSION_KEY, permission.clone())
            }
        }
        applyDocumentChanges(targetDocument, request.changes)
        val bytes = (targetDocument.toJson(JSONC_GRAMMAR, 0).trimEnd() + "\n").toByteArray(Charsets.UTF_8)
        val currentRevision = currentCompositeRevision()
        if (currentRevision != request.expectedRevision) {
            return AgentConfigApplyResult.Conflict(currentRevision)
        }
        val writeResult = fileStore.replace(
            target = before.target.writeFile,
            expectedRevision = before.targetRevision,
            nextBytes = bytes,
            validate = ::validateDocument
        )
        return mapWriteResult(request.agentId, before.paths, writeResult)
    }

    override suspend fun checkMcpServer(
        agentId: String,
        serverId: String
    ): AgentMcpConnectionCheckResult {
        if (!SAFE_ID.matches(agentId) || !SAFE_ID.matches(serverId)) {
            return AgentMcpConnectionCheckResult.Unavailable("MCP 标识无效")
        }
        val snapshot = when (val result = readLive(agentId)) {
            is AgentConfigReadResult.Ready -> result.snapshot
            is AgentConfigReadResult.Failed -> return AgentMcpConnectionCheckResult.Unavailable(result.message)
            is AgentConfigReadResult.Unavailable -> return AgentMcpConnectionCheckResult.Unavailable(
                result.discovery.warnings.firstOrNull() ?: "当前无法读取 Agent 配置"
            )
        }
        val server = snapshot.mcpServers.firstOrNull { it.id == serverId }
            ?: return AgentMcpConnectionCheckResult.Unavailable("MCP 已不存在，请重新读取配置")
        if (!server.enabled) return AgentMcpConnectionCheckResult.Unavailable("MCP 已停用")
        val executor = commandExecutor ?: return AgentMcpConnectionCheckResult.Unsupported()
        return when (val execution = executor.execute(listOf("opencode", "mcp", "list"), DEFAULT_MCP_CHECK_CWD)) {
            is AgentConfigCommandExecutionResult.Failed ->
                AgentMcpConnectionCheckResult.Unavailable(execution.message)
            is AgentConfigCommandExecutionResult.Completed -> parseMcpStatus(serverId, execution)
        }
    }

    private fun applyProviderTransaction(
        before: LiveState,
        request: AgentConfigApplyRequest
    ): AgentConfigApplyResult {
        if (request.changes.size != 1) {
            return AgentConfigApplyResult.Rejected(
                listOf(problem("changes", "供应商资料与凭据一次只能修改一个，不能和其他配置混合"))
            )
        }
        val change = request.changes.single()
        val credentialChange = when (change) {
            is AgentPersistentConfigChange.ConfigureProvider -> change.credential
            is AgentPersistentConfigChange.RemoveProvider -> AgentProviderCredentialChange.Remove
            else -> return AgentConfigApplyResult.Rejected(listOf(problem("changes", "供应商变更类型无效")))
        }
        val targetDocument = before.targetDocument.clone()
        applyDocumentChanges(targetDocument, listOf(change))
        val configBytes = serializeConfig(targetDocument)
        if (credentialChange == AgentProviderCredentialChange.Keep) {
            return applyProviderConfigOnly(before, request, configBytes)
        }

        val authSnapshot = runCatching { fileStore.read(before.paths.authFile.readFile) }.getOrElse {
            return AgentConfigApplyResult.Failed("无法读取 Agent 原生认证配置", restored = true)
        }
        val authWriteRevision = runCatching { fileStore.read(before.paths.authFile.writeFile).revision }.getOrElse {
            return AgentConfigApplyResult.Failed("无法读取 Agent 原生认证写入位置", restored = true)
        }
        val authDocument = runCatching { parseAuthDocument(authSnapshot.bytes) }.getOrElse {
            return AgentConfigApplyResult.Rejected(
                listOf(problem("auth.json", "Agent 原生认证配置格式无效，未执行覆盖"))
            )
        }
        val providerId = when (change) {
            is AgentPersistentConfigChange.ConfigureProvider -> change.provider.id
            is AgentPersistentConfigChange.RemoveProvider -> change.providerId
            else -> error("已由调用方限制供应商变更")
        }
        if (credentialChange == AgentProviderCredentialChange.Remove && !authDocument.containsKey(providerId)) {
            return applyProviderConfigOnly(before, request, configBytes)
        }
        when (credentialChange) {
            AgentProviderCredentialChange.Keep -> Unit
            is AgentProviderCredentialChange.Replace -> {
                val entry = authDocument.getObject(providerId)?.clone() ?: JsonObject()
                putPreservingComment(entry, AUTH_TYPE_KEY, JsonPrimitive.of(AUTH_TYPE_API))
                putPreservingComment(entry, AUTH_KEY_KEY, JsonPrimitive.of(credentialChange.secret))
                putPreservingComment(authDocument, providerId, entry)
            }
            AgentProviderCredentialChange.Remove -> authDocument.remove(providerId)
        }
        val authBytes = serializeAuth(authDocument)
        val currentRevision = currentCompositeRevision()
        if (currentRevision != request.expectedRevision) return AgentConfigApplyResult.Conflict(currentRevision)
        return mapMultiWriteResult(
            agentId = request.agentId,
            paths = before.paths,
            result = fileStore.replaceAll(
                listOf(
                    AtomicConfigFileUpdate(
                        target = before.target.writeFile,
                        expectedRevision = before.targetRevision,
                        nextBytes = configBytes,
                        validate = ::validateDocument
                    ),
                    AtomicConfigFileUpdate(
                        target = before.paths.authFile.writeFile,
                        expectedRevision = authWriteRevision,
                        nextBytes = authBytes,
                        validate = ::validateAuthDocument
                    )
                )
            )
        )
    }

    private fun applyProviderConfigOnly(
        before: LiveState,
        request: AgentConfigApplyRequest,
        configBytes: ByteArray
    ): AgentConfigApplyResult {
        val currentRevision = currentCompositeRevision()
        if (currentRevision != request.expectedRevision) return AgentConfigApplyResult.Conflict(currentRevision)
        return mapWriteResult(
            request.agentId,
            before.paths,
            fileStore.replace(before.target.writeFile, before.targetRevision, configBytes, ::validateDocument)
        )
    }

    private fun applySkillChanges(
        before: LiveState,
        request: AgentConfigApplyRequest
    ): AgentConfigApplyResult {
        if (request.changes.any { it !is AgentPersistentConfigChange.InstallSkill && it !is AgentPersistentConfigChange.RemoveSkill }) {
            return AgentConfigApplyResult.Rejected(
                listOf(problem("changes", "Skill 文件变更不能和配置文件变更放在同一事务中"))
            )
        }
        val changes = request.changes
        if (changes.size != 1) {
            return AgentConfigApplyResult.Rejected(
                listOf(problem("changes", "一次只能安装或移除一个 Skill"))
            )
        }
        val change = changes.single()
        val result = when (change) {
            is AgentPersistentConfigChange.InstallSkill -> installSkill(before.paths, change)
            is AgentPersistentConfigChange.RemoveSkill -> removeSkill(before.paths, change)
            else -> error("已由调用方限制 Skill 变更")
        }
        if (result != null) return result
        return when (val read = runCatching { readState(request.agentId).snapshot }.getOrNull()) {
            null -> AgentConfigApplyResult.Failed("Skill 已变更，但无法重新读取配置", restored = false)
            else -> AgentConfigApplyResult.Applied(read, backupReference = null)
        }
    }

    private fun installSkill(
        paths: OpenCodePaths,
        change: AgentPersistentConfigChange.InstallSkill
    ): AgentConfigApplyResult? {
        val source = resolveSkillSource(paths, change.sourceReference)
            ?: return AgentConfigApplyResult.Rejected(
                listOf(problem("sourceReference", "Skill 来源不存在或不在 Kite 受控导入区"))
            )
        val sourceFile = if (source.isDirectory) File(source, SKILL_FILE) else source
        if (!sourceFile.isFile || sourceFile.length() > MAX_SKILL_BYTES) {
            return AgentConfigApplyResult.Rejected(
                listOf(problem("sourceReference", "Skill 缺少 SKILL.md 或超过大小限制"))
            )
        }
        val targetDir = File(paths.configDirectory, "skills/${change.skillId}")
        if (targetDir.exists()) {
            return AgentConfigApplyResult.Conflict("skill:${skillRevision(targetDir)}", "Skill 已存在，请先重新读取")
        }
        return runCatching {
            val parent = requireNotNull(targetDir.parentFile)
            require(parent.mkdirs() || parent.isDirectory)
            val stage = File(parent, ".${change.skillId}.kite-stage-${System.nanoTime()}")
            try {
                val sourceFiles = source.walkTopDown().toList()
                require(sourceFiles.none { java.nio.file.Files.isSymbolicLink(it.toPath()) })
                require(sourceFiles.count { it.isFile } <= MAX_SKILL_FILES)
                require(sourceFiles.filter { it.isFile }.sumOf { it.length() } <= MAX_SKILL_TREE_BYTES)
                if (source.isDirectory) {
                    require(source.copyRecursively(stage, overwrite = false))
                } else {
                    require(stage.mkdirs())
                    source.copyTo(File(stage, SKILL_FILE), overwrite = true)
                }
                val stagedSkill = File(stage, SKILL_FILE)
                require(stagedSkill.isFile && stagedSkill.length() <= MAX_SKILL_BYTES)
                require(skillMetadata(stagedSkill).first.isNotBlank())
                java.nio.file.Files.move(
                    stage.toPath(),
                    targetDir.toPath(),
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE
                )
            } finally {
                if (stage.exists()) stage.deleteRecursively()
            }
            null
        }.getOrElse {
            AgentConfigApplyResult.Failed("Skill 安装失败", restored = !targetDir.exists())
        }
    }

    private fun removeSkill(
        paths: OpenCodePaths,
        change: AgentPersistentConfigChange.RemoveSkill
    ): AgentConfigApplyResult? {
        val target = File(paths.configDirectory, "skills/${change.skillId}")
        if (!target.isDirectory) {
            return AgentConfigApplyResult.Conflict("skill:missing", "Skill 已不存在，请重新读取")
        }
        return runCatching {
            val backupDir = File(paths.configDirectory, ".kite-skill-backups")
            require(backupDir.mkdirs() || backupDir.isDirectory)
            val backup = File(backupDir, "${change.skillId}-${System.currentTimeMillis()}")
            java.nio.file.Files.move(
                target.toPath(),
                backup.toPath(),
                java.nio.file.StandardCopyOption.ATOMIC_MOVE
            )
            trimSkillBackups(backupDir, change.skillId)
            null
        }.getOrElse {
            AgentConfigApplyResult.Failed("Skill 移除失败", restored = target.isDirectory)
        }
    }

    private fun resolveSkillSource(paths: OpenCodePaths, reference: String): File? {
        val relative = reference.removePrefix("kite-import:")
        val importRoot = File(paths.rootfs, "workspace/.kf/imports/skills").canonicalFile
        val candidate = File(importRoot, relative).canonicalFile
        return candidate.takeIf { it.isWithin(importRoot) && it.exists() }
    }

    private fun trimSkillBackups(directory: File, skillId: String) {
        directory.listFiles()
            .orEmpty()
            .filter { it.name.startsWith("$skillId-") }
            .sortedByDescending(File::lastModified)
            .drop(MAX_SKILL_BACKUPS)
            .forEach(File::deleteRecursively)
    }

    private fun mapWriteResult(
        agentId: String,
        paths: OpenCodePaths,
        result: AtomicConfigFileWriteResult
    ): AgentConfigApplyResult = when (result) {
        is AtomicConfigFileWriteResult.Applied -> {
            val next = runCatching { readState(agentId).snapshot }.getOrNull()
                ?: return AgentConfigApplyResult.Failed("配置已写入，但无法重新读取", restored = false)
            AgentConfigApplyResult.Applied(
                snapshot = next,
                backupReference = result.backupReference?.let { paths.toContainerPath(File(it)) }
            )
        }
        is AtomicConfigFileWriteResult.Conflict ->
            AgentConfigApplyResult.Conflict(
                runCatching { readState(agentId).snapshot.revision }.getOrDefault(result.actualRevision.value)
            )
        is AtomicConfigFileWriteResult.Rejected ->
            AgentConfigApplyResult.Rejected(listOf(problem("document", result.message)))
        is AtomicConfigFileWriteResult.Failed ->
            AgentConfigApplyResult.Failed(result.message, result.restored)
    }

    private fun mapMultiWriteResult(
        agentId: String,
        paths: OpenCodePaths,
        result: AtomicConfigFilesWriteResult
    ): AgentConfigApplyResult = when (result) {
        is AtomicConfigFilesWriteResult.Applied -> {
            val next = runCatching { readState(agentId).snapshot }.getOrNull()
                ?: return AgentConfigApplyResult.Failed("配置已写入，但无法重新读取", restored = false)
            AgentConfigApplyResult.Applied(
                snapshot = next,
                backupReference = result.backupReferences.firstOrNull()?.let { paths.toContainerPath(File(it)) }
            )
        }
        is AtomicConfigFilesWriteResult.Conflict ->
            AgentConfigApplyResult.Conflict(runCatching { readState(agentId).snapshot.revision }
                .getOrDefault(result.actualRevision.value))
        is AtomicConfigFilesWriteResult.Rejected ->
            AgentConfigApplyResult.Rejected(listOf(problem("document", result.message)))
        is AtomicConfigFilesWriteResult.Failed ->
            AgentConfigApplyResult.Failed(result.message, result.restored)
    }

    private fun applyDocumentChanges(
        document: JsonObject,
        changes: List<AgentPersistentConfigChange>
    ) {
        if (!document.containsKey(SCHEMA_KEY)) {
            document.put(SCHEMA_KEY, JsonPrimitive.of(SCHEMA_URL))
        }
        changes.forEach { change ->
            when (change) {
                is AgentPersistentConfigChange.SetDefaultModel -> {
                    if (change.modelId == null) document.remove(MODEL_KEY)
                    else putPreservingComment(document, MODEL_KEY, JsonPrimitive.of(change.modelId))
                }
                is AgentPersistentConfigChange.SelectProvider -> putPreservingComment(
                    document,
                    MODEL_KEY,
                    JsonPrimitive.of("${change.providerId}/${change.modelId}")
                )
                is AgentPersistentConfigChange.PutProvider ->
                    putNested(document, PROVIDER_KEY, change.providerId, change.configuration)
                is AgentPersistentConfigChange.ConfigureProvider -> putProviderProfile(document, change.provider)
                is AgentPersistentConfigChange.RemoveProvider -> removeNested(document, PROVIDER_KEY, change.providerId)
                is AgentPersistentConfigChange.SetPermissionProfile ->
                    setPermissionProfile(document, change.profileId)
                is AgentPersistentConfigChange.PutMcpServer ->
                    putNested(document, MCP_KEY, change.serverId, change.configuration)
                is AgentPersistentConfigChange.ConfigureMcpServer ->
                    configureMcpServer(document, change.server)
                is AgentPersistentConfigChange.SetMcpEnabled ->
                    setMcpEnabled(document, change.serverId, change.enabled)
                is AgentPersistentConfigChange.RemoveMcpServer -> removeNested(document, MCP_KEY, change.serverId)
                is AgentPersistentConfigChange.SetSkillActivation ->
                    setSkillActivation(document, change.skillId, change.activation)
                is AgentPersistentConfigChange.InstallSkill,
                is AgentPersistentConfigChange.RemoveSkill -> Unit
            }
        }
    }

    private fun setMcpEnabled(document: JsonObject, serverId: String, enabled: Boolean) {
        val section = document.getObject(MCP_KEY)?.clone() ?: JsonObject()
        val server = section.getObject(serverId)?.clone() ?: JsonObject()
        putPreservingComment(server, ENABLED_KEY, JsonPrimitive.of(enabled))
        putPreservingComment(section, serverId, server)
        putPreservingComment(document, MCP_KEY, section)
    }

    private fun configureMcpServer(document: JsonObject, draft: AgentMcpDraft) {
        val section = document.getObject(MCP_KEY)?.clone() ?: JsonObject()
        val server = section.getObject(draft.id)?.clone() ?: JsonObject()
        when (draft.transport) {
            AgentMcpTransport.Stdio -> {
                putPreservingComment(server, TYPE_KEY, JsonPrimitive.of(LOCAL_TYPE))
                val command = JsonArray().also { array ->
                    array.add(JsonPrimitive.of(requireNotNull(draft.command).trim()))
                    draft.arguments.forEach { array.add(JsonPrimitive.of(it)) }
                }
                putPreservingComment(server, COMMAND_KEY, command)
                server.remove(URL_KEY)
                server.remove(HEADERS_KEY)
                server.remove(OAUTH_KEY)
                mergeEnvironmentReferences(server, ENVIRONMENT_KEY, draft.environmentReferences)
            }
            AgentMcpTransport.RemoteHttpOrSse -> {
                putPreservingComment(server, TYPE_KEY, JsonPrimitive.of(REMOTE_TYPE))
                putPreservingComment(server, URL_KEY, JsonPrimitive.of(requireNotNull(draft.url).trim()))
                server.remove(COMMAND_KEY)
                server.remove(ENVIRONMENT_KEY)
                mergeEnvironmentReferences(server, HEADERS_KEY, draft.headerReferences)
            }
            AgentMcpTransport.StreamableHttp,
            AgentMcpTransport.Sse,
            AgentMcpTransport.Unknown -> error("已由 validate 限制 OpenCode MCP 传输类型")
        }
        putPreservingComment(server, ENABLED_KEY, JsonPrimitive.of(draft.enabled))
        putPreservingComment(section, draft.id, server)
        putPreservingComment(document, MCP_KEY, section)
    }

    private fun mergeEnvironmentReferences(
        server: JsonObject,
        field: String,
        references: List<AgentMcpEnvironmentReference>
    ) {
        val values = server.getObject(field)?.clone() ?: JsonObject()
        val desiredNames = references.map(AgentMcpEnvironmentReference::name).toSet()
        values.entries.toList().forEach { (name, value) ->
            if (environmentReference(value) != null && name !in desiredNames) values.remove(name)
        }
        references.forEach { reference ->
            putPreservingComment(values, reference.name, JsonPrimitive.of("{env:${reference.environmentVariable}}"))
        }
        if (values.isEmpty()) server.remove(field) else putPreservingComment(server, field, values)
    }

    private fun setSkillActivation(
        document: JsonObject,
        skillId: String,
        activation: AgentSkillActivation
    ) {
        val rawPermission = document[PERMISSION_KEY]
        val permission = when (rawPermission) {
            is JsonObject -> rawPermission.clone()
            is JsonPrimitive -> JsonObject().also {
                putPreservingComment(it, ALL_PATTERN, rawPermission.clone())
            }
            else -> JsonObject()
        }
        val rawSkillPermission = permission[SKILL_PERMISSION_KEY]
        val skillPermission = when (rawSkillPermission) {
            is JsonObject -> rawSkillPermission.clone()
            is JsonPrimitive -> JsonObject().also {
                putPreservingComment(it, ALL_PATTERN, rawSkillPermission.clone())
            }
            else -> JsonObject()
        }
        val action = when (activation) {
            AgentSkillActivation.Enabled -> ALLOW_ACTION
            AgentSkillActivation.ApprovalRequired -> ASK_ACTION
            AgentSkillActivation.Disabled -> DENY_ACTION
            AgentSkillActivation.ManualOnly,
            AgentSkillActivation.Unknown -> error("已由 validate 限制 OpenCode Skill 状态")
        }
        putPreservingComment(skillPermission, skillId, JsonPrimitive.of(action))
        putPreservingComment(permission, SKILL_PERMISSION_KEY, skillPermission)
        putPreservingComment(document, PERMISSION_KEY, permission)
    }

    private fun setPermissionProfile(document: JsonObject, action: String) {
        require(action in OPEN_CODE_PERMISSION_ACTIONS)
        val existing = document[PERMISSION_KEY]
        if (existing is JsonObject) {
            val permission = existing.clone()
            putPreservingComment(permission, ALL_PATTERN, JsonPrimitive.of(action))
            putPreservingComment(document, PERMISSION_KEY, permission)
        } else {
            putPreservingComment(document, PERMISSION_KEY, JsonPrimitive.of(action))
        }
    }

    private fun putProviderProfile(document: JsonObject, provider: AgentProviderDraft) {
        val section = document.getObject(PROVIDER_KEY)?.clone() ?: JsonObject()
        val existing = section.getObject(provider.id)?.clone() ?: JsonObject()
        if (provider.displayName.isNullOrBlank()) existing.remove(NAME_KEY)
        else putPreservingComment(existing, NAME_KEY, JsonPrimitive.of(provider.displayName.trim()))
        if (!existing.containsKey(NPM_KEY)) {
            putPreservingComment(existing, NPM_KEY, JsonPrimitive.of(OPENAI_COMPATIBLE_PACKAGE))
        }
        val options = existing.getObject(OPTIONS_KEY)?.clone() ?: JsonObject()
        putPreservingComment(options, BASE_URL_KEY, JsonPrimitive.of(provider.baseUrl.trim()))
        putPreservingComment(existing, OPTIONS_KEY, options)
        val models = JsonObject()
        provider.models.forEach { model ->
            val modelObject = JsonObject()
            if (model.displayName.isNotBlank() && model.displayName != model.id) {
                modelObject.put(NAME_KEY, JsonPrimitive.of(model.displayName.trim()))
            }
            models.put(model.id.trim(), modelObject)
        }
        putPreservingComment(existing, MODELS_KEY, models)
        putPreservingComment(section, provider.id, existing)
        putPreservingComment(document, PROVIDER_KEY, section)
    }

    private fun putNested(
        document: JsonObject,
        sectionKey: String,
        itemId: String,
        value: AgentConfigValue.ObjectValue
    ) {
        val section = document.getObject(sectionKey)?.clone() ?: JsonObject()
        val existing = section.getObject(itemId)?.clone() ?: JsonObject()
        deepMerge(existing, value.toJsonObject())
        putPreservingComment(section, itemId, existing)
        putPreservingComment(document, sectionKey, section)
    }

    private fun removeNested(document: JsonObject, sectionKey: String, itemId: String) {
        val section = document.getObject(sectionKey)?.clone() ?: return
        section.remove(itemId)
        if (section.isEmpty()) document.remove(sectionKey)
        else putPreservingComment(document, sectionKey, section)
    }

    private fun readState(agentId: String): LiveState {
        var paths = requireNotNull(resolvePaths()) { "Kite 运行容器尚未创建" }
        val migratedLegacyViewConfiguration = migrateLegacyViewConfiguration(paths)
        if (migratedLegacyViewConfiguration) {
            paths = requireNotNull(resolvePaths()) { "Kite 运行容器尚未创建" }
        }
        val target = paths.writeTarget()
        val documents = paths.configCandidates.mapNotNull { candidate ->
            candidate.readFile.takeIf(File::isFile)?.let { file ->
                candidate to parseDocument(file.readText())
            }
        }
        val effective = JsonObject()
        documents.forEach { (_, document) -> deepMerge(effective, document) }
        val targetDocument = documents.firstOrNull { it.first.containerPath == target.containerPath }
            ?.second
            ?.clone()
            ?: JsonObject()
        val targetRevision = fileStore.read(target.writeFile).revision
        val authProjection = readAuthProjection(paths)
        val providers = providerSummaries(effective.getObject(PROVIDER_KEY), authProjection)
        val defaultModel = effective.string(MODEL_KEY)
        val activePermissionProfileId = permissionAction(effective[PERMISSION_KEY])
        val snapshot = AgentLiveConfigSnapshot(
            agentId = agentId,
            adapterId = adapterId,
            revision = compositeRevision(paths),
            displayLocation = target.containerPath,
            activeProviderId = defaultModel
                ?.substringBefore('/')
                ?.takeIf { active -> providers.any { it.id == active } },
            defaultModel = defaultModel,
            providerIds = providers.map(AgentProviderSummary::id),
            providers = providers,
            activePermissionProfileId = activePermissionProfileId,
            permissionProfiles = OPEN_CODE_PERMISSION_PROFILES,
            mcpServers = mcpSummaries(effective.getObject(MCP_KEY)),
            skills = discoverSkills(paths, effective),
            credentialPresence = authProjection.overallPresence,
            warnings = buildList {
                authProjection.warning?.let(::add)
                if (migratedLegacyViewConfiguration) {
                    add("旧版 Kite 配置已迁移到当前 Agent 运行视图")
                }
            },
            runtimeReloadRequired = migratedLegacyViewConfiguration
        )
        return LiveState(
            paths,
            target,
            targetRevision,
            targetDocument,
            effective[PERMISSION_KEY]?.clone(),
            snapshot
        )
    }

    private fun permissionAction(value: JsonElement?): String? {
        val raw = when (value) {
            is JsonPrimitive -> value.getValue() as? String
            is JsonObject -> value.string(ALL_PATTERN)
            else -> null
        }
        return raw?.takeIf { it in OPEN_CODE_PERMISSION_ACTIONS }
    }

    /**
     * 早期 ConfigAdapter 曾绕过 PRoot View 把文件直接写入 Base。若当前 View 已有较旧的同名
     * 文件，这份用户配置会被永久遮蔽。这里只迁移能证明是旧 Kite 写入的新 Base 文件，
     * 并把配置与对应原生凭据原子 copy-up 到当前 Upper；普通原生配置不会被主动提升。
     */
    private fun migrateLegacyViewConfiguration(paths: OpenCodePaths): Boolean {
        if (paths.viewId == null) return false
        val target = paths.writeTarget()
        if (!target.baseFile.isFile || target.writeFile.exists()) return false
        val visible = target.readFile.takeIf(File::isFile) ?: return false
        if (visible.canonicalFile == target.baseFile.canonicalFile) return false
        if (fileStore.read(visible).revision == fileStore.read(target.baseFile).revision) return false
        val hasKiteBackup = File(paths.configDirectory, ".kite-backups")
            .listFiles()
            .orEmpty()
            .any { it.isFile && it.name.startsWith("${target.fileName}-") }
        val baseIsNewerThanVisible = target.baseFile.lastModified() > visible.lastModified()
        if (!hasKiteBackup && !baseIsNewerThanVisible) return false

        val legacyConfig = fileStore.read(target.baseFile)
        require(validateDocument(legacyConfig.bytes) == null) { "旧版 Kite 配置格式无效" }
        val updates = mutableListOf(
            AtomicConfigFileUpdate(
                target = target.writeFile,
                expectedRevision = fileStore.read(target.writeFile).revision,
                nextBytes = legacyConfig.bytes,
                validate = ::validateDocument
            )
        )
        val legacyAuth = paths.authFile.baseFile.takeIf(File::isFile)?.let(fileStore::read)
        if (legacyAuth != null) {
            val legacyDocument = parseDocument(legacyConfig.bytes.toString(Charsets.UTF_8))
            val providerIds = legacyDocument.getObject(PROVIDER_KEY)?.keys.orEmpty()
            val legacyAuthDocument = parseAuthDocument(legacyAuth.bytes)
            val visibleAuthDocument = paths.authFile.readFile.takeIf(File::isFile)
                ?.let(fileStore::read)
                ?.bytes
                ?.let(::parseAuthDocument)
                ?: JsonObject()
            providerIds.forEach { providerId ->
                legacyAuthDocument[providerId]?.let { entry ->
                    putPreservingComment(visibleAuthDocument, providerId, entry.clone())
                }
            }
            if (providerIds.any(legacyAuthDocument::containsKey)) {
                updates += AtomicConfigFileUpdate(
                    target = paths.authFile.writeFile,
                    expectedRevision = fileStore.read(paths.authFile.writeFile).revision,
                    nextBytes = serializeAuth(visibleAuthDocument),
                    validate = ::validateAuthDocument
                )
            }
        }
        return when (val result = fileStore.replaceAll(updates)) {
            is AtomicConfigFilesWriteResult.Applied -> true
            is AtomicConfigFilesWriteResult.Conflict -> error("旧版 Kite 配置迁移发生并发冲突")
            is AtomicConfigFilesWriteResult.Rejected -> error(result.message)
            is AtomicConfigFilesWriteResult.Failed -> error(result.message)
        }
    }

    private fun compositeRevision(paths: OpenCodePaths): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update((paths.viewId ?: "base").toByteArray())
        paths.configCandidates.forEach { candidate ->
            digest.update(candidate.containerPath.toByteArray())
            digest.update(fileStore.read(candidate.readFile).revision.value.toByteArray())
        }
        digest.update(AUTH_CONTAINER_PATH.toByteArray())
        digest.update(fileStore.read(paths.authFile.readFile).revision.value.toByteArray())
        discoverSkillFiles(paths).forEach { file ->
            digest.update(paths.toContainerPath(file).toByteArray())
            digest.update(skillRevision(file).toByteArray())
        }
        return "sha256:" + digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun currentCompositeRevision(): String =
        compositeRevision(requireNotNull(resolvePaths()) { "Kite 运行容器尚未创建" })

    private fun providerSummaries(
        section: JsonObject?,
        auth: AuthProjection
    ): List<AgentProviderSummary> = section?.entries
        ?.mapNotNull { (id, value) ->
            val provider = value as? JsonObject ?: return@mapNotNull null
            val options = provider.getObject(OPTIONS_KEY)
            val models = provider.getObject(MODELS_KEY)?.entries.orEmpty().map { (modelId, modelValue) ->
                val displayName = (modelValue as? JsonObject)?.string(NAME_KEY)?.takeIf(String::isNotBlank) ?: modelId
                AgentProviderModelSummary(modelId, displayName)
            }.sortedBy(AgentProviderModelSummary::id)
            AgentProviderSummary(
                id = id,
                displayName = provider.string(NAME_KEY)?.takeIf(String::isNotBlank) ?: id,
                baseUrl = options?.string(BASE_URL_KEY),
                models = models,
                credentialPresence = when {
                    auth.warning != null -> AgentCredentialPresence.Unknown
                    id in auth.providerIds -> AgentCredentialPresence.Present
                    else -> AgentCredentialPresence.Missing
                }
            )
        }
        ?.sortedBy(AgentProviderSummary::id)
        .orEmpty()

    private fun readAuthProjection(paths: OpenCodePaths): AuthProjection {
        if (!paths.authFile.readFile.isFile) return AuthProjection(emptySet(), AgentCredentialPresence.Missing)
        return runCatching {
            val document = parseAuthDocument(fileStore.read(paths.authFile.readFile).bytes)
            val providerIds = document.entries.mapNotNull { (id, value) ->
                val entry = value as? JsonObject ?: return@mapNotNull null
                id.takeIf { entry[AUTH_KEY_KEY] is JsonPrimitive }
            }.toSet()
            AuthProjection(
                providerIds = providerIds,
                overallPresence = if (providerIds.isEmpty()) AgentCredentialPresence.Missing else AgentCredentialPresence.Present
            )
        }.getOrElse {
            AuthProjection(emptySet(), AgentCredentialPresence.Unknown, "Agent 原生认证配置无法安全解析")
        }
    }

    private fun mcpSummaries(section: JsonObject?): List<AgentMcpSummary> = section?.entries
        ?.map { (id, value) ->
            val config = value as? JsonObject
            val enabled = (config?.get(ENABLED_KEY) as? JsonPrimitive)?.asBoolean(true) ?: true
            val kind = config?.string(TYPE_KEY) ?: "unknown"
            val command = (config?.get(COMMAND_KEY) as? JsonArray)
                ?.mapNotNull { (it as? JsonPrimitive)?.stringValue() }
                .orEmpty()
            AgentMcpSummary(
                id = id,
                kind = kind,
                enabled = enabled,
                transport = when (kind) {
                    LOCAL_TYPE -> AgentMcpTransport.Stdio
                    REMOTE_TYPE -> AgentMcpTransport.RemoteHttpOrSse
                    else -> AgentMcpTransport.Unknown
                },
                command = command.firstOrNull(),
                arguments = command.drop(1),
                url = config?.string(URL_KEY),
                environmentReferences = environmentReferences(config?.getObject(ENVIRONMENT_KEY)),
                headerReferences = environmentReferences(config?.getObject(HEADERS_KEY)),
                scope = AgentConfigScope.User,
                allowedOperations = buildSet {
                    if (kind == LOCAL_TYPE || kind == REMOTE_TYPE) add(AgentMcpOperation.Edit)
                    add(if (enabled) AgentMcpOperation.Disable else AgentMcpOperation.Enable)
                    add(AgentMcpOperation.Remove)
                    if (enabled && commandExecutor != null) add(AgentMcpOperation.CheckConnection)
                }
            )
        }
        ?.sortedBy(AgentMcpSummary::id)
        .orEmpty()

    private fun environmentReferences(section: JsonObject?): List<AgentMcpEnvironmentReference> =
        section?.entries.orEmpty().mapNotNull { (name, value) ->
            environmentReference(value)?.let { variable -> AgentMcpEnvironmentReference(name, variable) }
        }.sortedBy(AgentMcpEnvironmentReference::name)

    private fun environmentReference(value: JsonElement?): String? {
        val raw = (value as? JsonPrimitive)?.stringValue() ?: return null
        if (!raw.startsWith(ENVIRONMENT_REFERENCE_PREFIX) || !raw.endsWith(ENVIRONMENT_REFERENCE_SUFFIX)) {
            return null
        }
        val variable = raw.substring(
            ENVIRONMENT_REFERENCE_PREFIX.length,
            raw.length - ENVIRONMENT_REFERENCE_SUFFIX.length
        )
        return variable.takeIf(ENVIRONMENT_NAME::matches)
    }

    private fun discoverSkills(paths: OpenCodePaths, effective: JsonObject): List<AgentSkillSummary> =
        discoverSkillFiles(paths)
        .mapNotNull { file ->
            runCatching {
                val (name, displayName) = skillMetadata(file)
                val location = paths.toContainerPath(file)
                val removable = location == "$CONFIG_CONTAINER_PATH/skills/$name/$SKILL_FILE"
                AgentSkillSummary(
                    id = name,
                    displayName = displayName,
                    location = location,
                    scope = AgentConfigScope.User,
                    activation = resolveSkillActivation(effective, name),
                    allowedOperations = buildSet {
                        add(AgentSkillOperation.Enable)
                        add(AgentSkillOperation.RequireApproval)
                        add(AgentSkillOperation.Disable)
                        if (removable) add(AgentSkillOperation.Remove)
                    }
                )
            }.getOrNull()
        }
        .distinctBy(AgentSkillSummary::id)
        .sortedBy(AgentSkillSummary::id)

    private fun resolveSkillActivation(document: JsonObject, skillId: String): AgentSkillActivation {
        val rawPermission = document[PERMISSION_KEY]
        var action = when (rawPermission) {
            is JsonPrimitive -> rawPermission.stringValue()
            is JsonObject -> rawPermission.string(ALL_PATTERN)
            else -> null
        }
        val skillPermission = (rawPermission as? JsonObject)?.get(SKILL_PERMISSION_KEY)
        when (skillPermission) {
            is JsonPrimitive -> action = skillPermission.stringValue()
            is JsonObject -> skillPermission.entries.forEach { (pattern, value) ->
                val candidate = (value as? JsonPrimitive)?.stringValue()
                if (candidate != null && wildcardMatches(pattern, skillId)) action = candidate
            }
        }
        return when (action) {
            ASK_ACTION -> AgentSkillActivation.ApprovalRequired
            DENY_ACTION -> AgentSkillActivation.Disabled
            ALLOW_ACTION, null -> AgentSkillActivation.Enabled
            else -> AgentSkillActivation.Unknown
        }
    }

    private fun JsonPrimitive.stringValue(): String? = getValue() as? String

    private fun wildcardMatches(pattern: String, value: String): Boolean {
        val expression = buildString {
            append('^')
            pattern.forEach { character ->
                when (character) {
                    '*' -> append(".*")
                    '?' -> append('.')
                    else -> append(Regex.escape(character.toString()))
                }
            }
            append('$')
        }
        return Regex(expression).matches(value)
    }

    private fun discoverSkillFiles(paths: OpenCodePaths): List<File> = listOf("skill", "skills")
        .flatMap { directory ->
            val root = File(paths.configDirectory, directory)
            if (!root.isDirectory) emptyList()
            else root.walkTopDown()
                .onEnter { !java.nio.file.Files.isSymbolicLink(it.toPath()) }
                .maxDepth(MAX_SKILL_DEPTH)
                .filter { it.isFile && it.name == SKILL_FILE && it.length() <= MAX_SKILL_BYTES }
                .toList()
        }
        .sortedBy { it.absolutePath }

    private fun skillMetadata(file: File): Pair<String, String> {
        val header = file.useLines { lines -> lines.take(MAX_SKILL_HEADER_LINES).toList() }
        val name = header.firstNotNullOfOrNull { line -> FRONTMATTER_NAME.matchEntire(line.trim())?.groupValues?.get(1) }
            ?.trim('"', '\'', ' ')
            ?.takeIf(SAFE_ID::matches)
            ?: file.parentFile?.name?.takeIf(SAFE_ID::matches)
            ?: error("Skill name 无效")
        val display = header.firstNotNullOfOrNull { line -> FRONTMATTER_TITLE.matchEntire(line.trim())?.groupValues?.get(1) }
            ?.trim('"', '\'', ' ')
            ?.takeIf(String::isNotBlank)
            ?: name
        return name to display
    }

    private fun skillRevision(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        if (file.isDirectory) {
            file.walkTopDown().filter { it.isFile }.sortedBy { it.absolutePath }.forEach { child ->
                digest.update(child.relativeTo(file).path.toByteArray())
                digest.update(child.readBytes())
            }
        } else {
            digest.update(file.readBytes())
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun validateDocument(bytes: ByteArray): String? = runCatching {
        val document = parseDocument(bytes.toString(Charsets.UTF_8))
        val model = document[MODEL_KEY]
        require(model == null || model is JsonPrimitive && model.getValue() is String) { "model 必须是字符串" }
        require(document[PROVIDER_KEY] == null || document[PROVIDER_KEY] is JsonObject) { "provider 必须是对象" }
        require(document[MCP_KEY] == null || document[MCP_KEY] is JsonObject) { "mcp 必须是对象" }
        null
    }.getOrElse { "OpenCode 配置不是有效的 JSONC 或字段结构无效" }

    private fun validateAuthDocument(bytes: ByteArray): String? = runCatching {
        parseAuthDocument(bytes)
        null
    }.getOrElse { "Agent 原生认证配置不是有效 JSON" }

    private fun serializeConfig(document: JsonObject): ByteArray =
        (document.toJson(JSONC_GRAMMAR, 0).trimEnd() + "\n").toByteArray(Charsets.UTF_8)

    private fun serializeAuth(document: JsonObject): ByteArray =
        (document.toJson(AUTH_GRAMMAR, 0).trimEnd() + "\n").toByteArray(Charsets.UTF_8)

    private fun parseAuthDocument(bytes: ByteArray): JsonObject =
        if (bytes.isEmpty() || bytes.toString(Charsets.UTF_8).isBlank()) JsonObject()
        else parser.load(bytes.toString(Charsets.UTF_8))

    private fun parseDocument(text: String): JsonObject = if (text.isBlank()) JsonObject() else parser.load(text)

    private fun resolvePaths(): OpenCodePaths? {
        val container = containerProvider() ?: return null
        val rootfs = File(container.rootfsPath).canonicalFile
        require(rootfs.isDirectory) { "运行容器 rootfs 不存在" }
        val configDirectory = File(rootfs, "root/.config/opencode").canonicalFile
        val dataDirectory = File(rootfs, "root/.local/share/opencode").canonicalFile
        require(configDirectory.isWithin(rootfs) && dataDirectory.isWithin(rootfs)) { "OpenCode 配置路径越界" }
        val activeBinding = ProotViewRuntime.resolveActiveBinding(container)
        val viewStore = activeBinding?.let { ProotViewStore.forContainer(container) }
        fun project(file: File, displayPath: String): ProjectedFile {
            if (viewStore == null) {
                val physical = if (file.exists()) file.canonicalFile else file.absoluteFile
                return ProjectedFile(physical, physical, physical, displayPath)
            }
            val projection = viewStore.projectPath(file)
            return ProjectedFile(
                baseFile = file.absoluteFile,
                readFile = projection.visibleFile ?: projection.writableFile,
                writeFile = projection.writableFile,
                containerPath = displayPath
            )
        }
        val candidates = CONFIG_FILE_NAMES.map { name ->
            val display = "$CONFIG_CONTAINER_PATH/$name"
            val requested = File(configDirectory, name)
            require(requested.isWithin(rootfs)) { "OpenCode 配置文件路径越界" }
            val projected = project(requested, display)
            ConfigCandidate(projected.baseFile, projected.readFile, projected.writeFile, display, name)
        }
        val auth = project(File(dataDirectory, "auth.json"), AUTH_CONTAINER_PATH)
        return OpenCodePaths(
            rootfs = rootfs,
            configDirectory = configDirectory,
            authFile = auth,
            configCandidates = candidates,
            viewId = activeBinding?.viewId,
            projectionLayerRoots = activeBinding?.let { binding ->
                val activeViewStore = requireNotNull(viewStore)
                listOf(binding.upperRootPath) + binding.parentViewIds.mapNotNull { parentId ->
                    runCatching { activeViewStore.binding(parentId).upperRootPath }.getOrNull()
                } + binding.baseRootPath
            }.orEmpty()
        )
    }

    private fun OpenCodePaths.writeTarget(): ConfigCandidate {
        val priority = WRITE_TARGET_PRIORITY
        return priority.firstNotNullOfOrNull { name ->
            configCandidates.first { it.fileName == name }.takeIf { it.readFile.exists() }
        } ?: configCandidates.first { it.fileName == WRITE_TARGET_PRIORITY.first() }
    }

    private fun validateId(
        field: String,
        value: String,
        output: MutableList<AgentConfigValidationProblem>
    ) {
        if (!SAFE_ID.matches(value)) output.add(problem(field, "ID 格式无效"))
    }

    private fun validateProvider(
        path: String,
        provider: AgentProviderDraft,
        credential: AgentProviderCredentialChange,
        output: MutableList<AgentConfigValidationProblem>
    ) {
        validateId("$path.provider.id", provider.id, output)
        provider.displayName?.let { name ->
            if (name.length > MAX_PROVIDER_NAME_LENGTH || name.any(Char::isISOControl)) {
                output.add(problem("$path.provider.displayName", "供应商名称格式无效"))
            }
        }
        val uri = runCatching { URI(provider.baseUrl.trim()) }.getOrNull()
        if (uri == null || uri.scheme !in setOf("http", "https") || uri.host.isNullOrBlank() || uri.userInfo != null) {
            output.add(problem("$path.provider.baseUrl", "供应商 URL 必须是有效的 HTTP 或 HTTPS 地址"))
        }
        if (provider.models.isEmpty()) output.add(problem("$path.provider.models", "至少保留一个可用模型"))
        if (provider.models.size > MAX_PROVIDER_MODELS) output.add(problem("$path.provider.models", "模型数量过多"))
        val duplicateModels = provider.models.groupBy { it.id.trim() }.filterValues { it.size > 1 }.keys
        if (duplicateModels.isNotEmpty()) output.add(problem("$path.provider.models", "模型 ID 不能重复"))
        provider.models.forEachIndexed { index, model ->
            if (!SAFE_MODEL_ID.matches(model.id.trim())) {
                output.add(problem("$path.provider.models[$index].id", "模型 ID 格式无效"))
            }
            if (model.displayName.length > MAX_PROVIDER_NAME_LENGTH || model.displayName.any(Char::isISOControl)) {
                output.add(problem("$path.provider.models[$index].displayName", "模型名称格式无效"))
            }
        }
        if (credential is AgentProviderCredentialChange.Replace &&
            (credential.secret.isBlank() || credential.secret.length > MAX_CREDENTIAL_LENGTH || credential.secret.any(Char::isISOControl))
        ) {
            output.add(problem("$path.credential", "API Key 格式无效"))
        }
    }

    private fun validateMcpDraft(
        path: String,
        draft: AgentMcpDraft,
        output: MutableList<AgentConfigValidationProblem>
    ) {
        validateId("$path.id", draft.id, output)
        when (draft.transport) {
            AgentMcpTransport.Stdio -> {
                val command = draft.command?.trim().orEmpty()
                if (command.isBlank() || command.length > MAX_MCP_TEXT_LENGTH || command.any(Char::isISOControl)) {
                    output.add(problem("$path.command", "本地 MCP 必须提供有效命令"))
                }
                if (draft.arguments.size > MAX_MCP_ARGUMENTS || draft.arguments.any {
                        it.length > MAX_MCP_TEXT_LENGTH || it.any(Char::isISOControl)
                    }) {
                    output.add(problem("$path.arguments", "MCP 参数数量或格式无效"))
                }
                if (draft.url != null) output.add(problem("$path.url", "本地 MCP 不能同时设置远程地址"))
                if (draft.headerReferences.isNotEmpty()) {
                    output.add(problem("$path.headerReferences", "本地 MCP 不支持 Header 引用"))
                }
            }
            AgentMcpTransport.RemoteHttpOrSse -> {
                val uri = draft.url?.trim()?.let { runCatching { URI(it) }.getOrNull() }
                if (uri == null || uri.scheme !in setOf("http", "https") || uri.host.isNullOrBlank() || uri.userInfo != null) {
                    output.add(problem("$path.url", "远程 MCP 必须提供有效的 HTTP 或 HTTPS 地址"))
                }
                if (draft.command != null || draft.arguments.isNotEmpty()) {
                    output.add(problem("$path.command", "远程 MCP 不能同时设置本地命令"))
                }
                if (draft.environmentReferences.isNotEmpty()) {
                    output.add(problem("$path.environmentReferences", "远程 MCP 不支持进程环境引用"))
                }
            }
            AgentMcpTransport.StreamableHttp,
            AgentMcpTransport.Sse,
            AgentMcpTransport.Unknown ->
                output.add(problem("$path.transport", "当前 OpenCode 版本不支持单独指定这个传输类型"))
        }
        validateMcpReferences("$path.environmentReferences", draft.environmentReferences, output)
        validateMcpReferences("$path.headerReferences", draft.headerReferences, output)
    }

    private fun validateMcpReferences(
        path: String,
        references: List<AgentMcpEnvironmentReference>,
        output: MutableList<AgentConfigValidationProblem>
    ) {
        if (references.size > MAX_MCP_REFERENCES) output.add(problem(path, "引用数量过多"))
        if (references.groupBy(AgentMcpEnvironmentReference::name).any { it.value.size > 1 }) {
            output.add(problem(path, "引用名称不能重复"))
        }
        references.forEachIndexed { index, reference ->
            if (!SAFE_CONFIG_KEY.matches(reference.name)) {
                output.add(problem("$path[$index].name", "引用名称格式无效"))
            }
            if (!ENVIRONMENT_NAME.matches(reference.environmentVariable)) {
                output.add(problem("$path[$index].environmentVariable", "环境变量名格式无效"))
            }
        }
    }

    private fun parseMcpStatus(
        serverId: String,
        execution: AgentConfigCommandExecutionResult.Completed
    ): AgentMcpConnectionCheckResult {
        if (execution.exitCode != 0) return AgentMcpConnectionCheckResult.Unavailable("OpenCode MCP 检查失败")
        val statusPattern = Regex(
            "(?:^|\\s)[✓○⚠✗]\\s+${Regex.escape(serverId)}\\s+" +
                "(connected|disabled|needs authentication|needs client registration|failed|not initialized)(?:\\s|$)",
            RegexOption.IGNORE_CASE
        )
        val status = execution.stdout.asSequence()
            .map { ANSI_ESCAPE.replace(it, "") }
            .mapNotNull { line -> statusPattern.find(line)?.groupValues?.get(1)?.lowercase() }
            .firstOrNull()
            ?: return AgentMcpConnectionCheckResult.Unavailable("OpenCode 未返回这个 MCP 的连接状态")
        return when (status) {
            "connected" -> AgentMcpConnectionCheckResult.Available("OpenCode 已连接")
            "disabled" -> AgentMcpConnectionCheckResult.Unavailable("MCP 已停用")
            "needs authentication" -> AgentMcpConnectionCheckResult.Unavailable("MCP 需要认证")
            "needs client registration" -> AgentMcpConnectionCheckResult.Unavailable("MCP 需要客户端登记")
            "not initialized" -> AgentMcpConnectionCheckResult.Unavailable("MCP 尚未完成初始化")
            else -> AgentMcpConnectionCheckResult.Unavailable("OpenCode 无法连接这个 MCP")
        }
    }

    private fun validateValue(
        path: String,
        value: AgentConfigValue,
        output: MutableList<AgentConfigValidationProblem>,
        depth: Int = 0,
        parentKey: String? = null
    ) {
        if (depth > MAX_VALUE_DEPTH) {
            output.add(problem(path, "配置嵌套过深"))
            return
        }
        if (parentKey != null && SENSITIVE_KEY.containsMatchIn(parentKey) &&
            value !is AgentConfigValue.EnvironmentReference && value !is AgentConfigValue.NullValue
        ) {
            output.add(problem(path, "敏感字段只能使用环境变量引用，不能保存明文"))
        }
        when (value) {
            is AgentConfigValue.Text -> if (value.value.length > MAX_TEXT_LENGTH) {
                output.add(problem(path, "文本配置过长"))
            }
            is AgentConfigValue.Number -> if (!value.value.isFinite()) output.add(problem(path, "数字必须是有限值"))
            is AgentConfigValue.EnvironmentReference -> if (!ENVIRONMENT_NAME.matches(value.variableName)) {
                output.add(problem(path, "环境变量名格式无效"))
            }
            is AgentConfigValue.Values -> {
                if (value.values.size > MAX_COLLECTION_SIZE) output.add(problem(path, "配置列表过长"))
                value.values.forEachIndexed { index, child ->
                    validateValue("$path[$index]", child, output, depth + 1)
                }
            }
            is AgentConfigValue.ObjectValue -> {
                if (value.values.size > MAX_COLLECTION_SIZE) output.add(problem(path, "配置对象字段过多"))
                value.values.forEach { (key, child) ->
                    if (!SAFE_CONFIG_KEY.matches(key)) output.add(problem("$path.$key", "配置键格式无效"))
                    validateValue("$path.$key", child, output, depth + 1, key)
                }
            }
            is AgentConfigValue.Flag,
            AgentConfigValue.NullValue -> Unit
        }
    }

    private fun AgentConfigValue.toJson(): JsonElement = when (this) {
        is AgentConfigValue.Text -> JsonPrimitive.of(value)
        is AgentConfigValue.Flag -> JsonPrimitive.of(value)
        is AgentConfigValue.Number -> JsonPrimitive(value)
        is AgentConfigValue.Values -> JsonArray().also { array -> values.forEach { array.add(it.toJson()) } }
        is AgentConfigValue.ObjectValue -> toJsonObject()
        is AgentConfigValue.EnvironmentReference -> JsonPrimitive.of("{env:$variableName}")
        AgentConfigValue.NullValue -> JsonNull.INSTANCE
    }

    private fun AgentConfigValue.ObjectValue.toJsonObject(): JsonObject = JsonObject().also { output ->
        values.forEach { (key, value) -> output.put(key, value.toJson()) }
    }

    private fun deepMerge(target: JsonObject, patch: JsonObject): JsonObject {
        patch.forEach { (key, value) ->
            val current = target[key]
            if (current is JsonObject && value is JsonObject) {
                deepMerge(current, value)
            } else {
                putPreservingComment(target, key, value.clone())
            }
        }
        return target
    }

    private fun putPreservingComment(target: JsonObject, key: String, value: JsonElement) {
        target.put(key, value, target.getComment(key))
    }

    private fun JsonObject.string(key: String): String? =
        (get(key) as? JsonPrimitive)?.getValue()?.let { it as? String }

    private fun File.isWithin(root: File): Boolean {
        val rootPath = root.canonicalFile.toPath()
        return canonicalFile.toPath().startsWith(rootPath)
    }

    private fun problem(field: String, message: String) = AgentConfigValidationProblem(field, message)

    private data class ConfigCandidate(
        val baseFile: File,
        val readFile: File,
        val writeFile: File,
        val containerPath: String,
        val fileName: String
    )

    private data class ProjectedFile(
        val baseFile: File,
        val readFile: File,
        val writeFile: File,
        val containerPath: String
    )

    private data class OpenCodePaths(
        val rootfs: File,
        val configDirectory: File,
        val authFile: ProjectedFile,
        val configCandidates: List<ConfigCandidate>,
        val viewId: String?,
        val projectionLayerRoots: List<String>
    ) {
        fun toContainerPath(file: File): String {
            val canonical = file.canonicalFile
            if (canonical.isWithin(rootfs)) {
                return "/" + canonical.relativeTo(rootfs).invariantSeparatorsPath
            }
            val baseRoot = projectionLayerRoots.lastOrNull()?.let(::File)?.canonicalFile
                ?: return canonical.absolutePath
            val rootfsRelative = rootfs.relativeTo(baseRoot).invariantSeparatorsPath
            projectionLayerRoots.dropLast(1).forEach { layerRootPath ->
                val layerRoot = File(layerRootPath).canonicalFile
                if (canonical.isWithin(layerRoot)) {
                    val relative = canonical.relativeTo(layerRoot).invariantSeparatorsPath
                    if (relative == rootfsRelative) return "/"
                    if (relative.startsWith("$rootfsRelative/")) {
                        return "/" + relative.removePrefix("$rootfsRelative/")
                    }
                }
            }
            return canonical.absolutePath
        }

        private fun File.isWithin(root: File): Boolean =
            canonicalFile.toPath().startsWith(root.canonicalFile.toPath())
    }

    private data class LiveState(
        val paths: OpenCodePaths,
        val target: ConfigCandidate,
        val targetRevision: ConfigFileRevision,
        val targetDocument: JsonObject,
        val effectivePermission: JsonElement?,
        val snapshot: AgentLiveConfigSnapshot
    )

    private data class AuthProjection(
        val providerIds: Set<String>,
        val overallPresence: AgentCredentialPresence,
        val warning: String? = null
    )

    companion object {
        const val ADAPTER_ID = "opencode"
        private const val CONFIG_CONTAINER_PATH = "/root/.config/opencode"
        private const val GLOBAL_AGENTS_PATH = "$CONFIG_CONTAINER_PATH/AGENTS.md"
        private const val AUTH_CONTAINER_PATH = "/root/.local/share/opencode/auth.json"
        private const val SCHEMA_KEY = "$" + "schema"
        private const val SCHEMA_URL = "https://opencode.ai/config.json"
        private const val MODEL_KEY = "model"
        private const val OPEN_CODE_PUBLIC_PROVIDER_ID = "opencode"
        private const val OPEN_CODE_PUBLIC_PROVIDER_NAME = "OpenCode"
        private const val PROVIDER_KEY = "provider"
        private const val MCP_KEY = "mcp"
        private const val ENABLED_KEY = "enabled"
        private const val TYPE_KEY = "type"
        private const val LOCAL_TYPE = "local"
        private const val REMOTE_TYPE = "remote"
        private const val COMMAND_KEY = "command"
        private const val URL_KEY = "url"
        private const val ENVIRONMENT_KEY = "environment"
        private const val HEADERS_KEY = "headers"
        private const val OAUTH_KEY = "oauth"
        private const val PERMISSION_KEY = "permission"
        private const val SKILL_PERMISSION_KEY = "skill"
        private const val ALL_PATTERN = "*"
        private const val ALLOW_ACTION = "allow"
        private const val ASK_ACTION = "ask"
        private const val DENY_ACTION = "deny"
        private const val NAME_KEY = "name"
        private const val NPM_KEY = "npm"
        private const val OPTIONS_KEY = "options"
        private const val BASE_URL_KEY = "baseURL"
        private const val MODELS_KEY = "models"
        private const val AUTH_TYPE_KEY = "type"
        private const val AUTH_KEY_KEY = "key"
        private const val AUTH_TYPE_API = "api"
        private const val DEFAULT_MCP_CHECK_CWD = "/workspace"
        private const val MAX_MCP_ARGUMENTS = 64
        private const val MAX_MCP_REFERENCES = 64
        private const val MAX_MCP_TEXT_LENGTH = 2_048
        private const val OPENAI_COMPATIBLE_PACKAGE = "@ai-sdk/openai-compatible"
        private const val SKILL_FILE = "SKILL.md"
        private const val MAX_TEXT_LENGTH = 16 * 1024
        private const val MAX_COLLECTION_SIZE = 256
        private const val MAX_VALUE_DEPTH = 12
        private const val MAX_PROVIDER_NAME_LENGTH = 256
        private const val MAX_PROVIDER_MODELS = 256
        private const val MAX_CREDENTIAL_LENGTH = 16 * 1024
        private const val MAX_SKILL_BYTES = 1024L * 1024L
        private const val MAX_SKILL_TREE_BYTES = 8L * 1024L * 1024L
        private const val MAX_SKILL_FILES = 128
        private const val MAX_SKILL_BACKUPS = 5
        private const val MAX_SKILL_DEPTH = 8
        private const val MAX_SKILL_HEADER_LINES = 80
        private val CONFIG_FILE_NAMES = listOf("config.json", "opencode.json", "opencode.jsonc")
        private val WRITE_TARGET_PRIORITY = listOf("opencode.jsonc", "opencode.json", "config.json")
        private val SAFE_ID = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,127}")
        private val SAFE_MODEL_COMPONENT = Regex("[^\\s\\p{Cc}]{1,256}")
        private val OPEN_CODE_MODEL_ID = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,127}/[^\\s\\p{Cc}]{1,383}")
        private val SAFE_MODEL_ID = Regex("[^\\s\\p{Cc}]{1,384}")
        private val SAFE_CONFIG_KEY = Regex("[A-Za-z0-9_.$-]{1,128}")
        private val SAFE_SOURCE_REFERENCE = Regex("kite-import:[A-Za-z0-9][A-Za-z0-9._/-]{0,255}")
        private val OPEN_CODE_SKILL_ACTIVATIONS = setOf(
            AgentSkillActivation.Enabled,
            AgentSkillActivation.ApprovalRequired,
            AgentSkillActivation.Disabled
        )
        private val OPEN_CODE_PERMISSION_ACTIONS = setOf(ALLOW_ACTION, ASK_ACTION, DENY_ACTION)
        private val OPEN_CODE_PERMISSION_PROFILES = listOf(
            AgentPermissionProfileSummary(
                id = DENY_ACTION,
                displayName = "拒绝（deny）",
                description = "阻止未被更具体规则放行的操作",
                effect = AgentSessionConfigurationEffect.NextTurn,
                level = AgentPermissionLevel.Restricted,
            ),
            AgentPermissionProfileSummary(
                id = ASK_ACTION,
                displayName = "询问（ask）",
                description = "操作前请求审批；具体工具规则仍优先生效",
                effect = AgentSessionConfigurationEffect.NextTurn,
                level = AgentPermissionLevel.Approval,
            ),
            AgentPermissionProfileSummary(
                id = ALLOW_ACTION,
                displayName = "允许（allow）",
                description = "无需审批直接运行；具体工具的 ask / deny 规则仍优先生效",
                effect = AgentSessionConfigurationEffect.NextTurn,
                level = AgentPermissionLevel.Full,
            )
        )
        private val ENVIRONMENT_NAME = Regex("[A-Z_][A-Z0-9_]{0,127}")
        private const val ENVIRONMENT_REFERENCE_PREFIX = "{env:"
        private const val ENVIRONMENT_REFERENCE_SUFFIX = "}"
        private val ANSI_ESCAPE = Regex("\\u001B\\[[0-?]*[ -/]*[@-~]")
        private val SENSITIVE_KEY = Regex("(?i)(api.?key|token|secret|password|authorization)")
        private val FRONTMATTER_NAME = Regex("name\\s*:\\s*(.+)", RegexOption.IGNORE_CASE)
        private val FRONTMATTER_TITLE = Regex("title\\s*:\\s*(.+)", RegexOption.IGNORE_CASE)
        private val JSONC_GRAMMAR = JsonGrammar.builder()
            .withComments(true)
            .printWhitespace(true)
            .printCommas(true)
            .printTrailingCommas(false)
            .bareSpecialNumerics(false)
            .bareRootObject(false)
            .printUnquotedKeys(false)
            .build()
        private val AUTH_GRAMMAR = JsonGrammar.builder()
            .withComments(false)
            .printWhitespace(true)
            .printCommas(true)
            .printTrailingCommas(false)
            .bareSpecialNumerics(false)
            .bareRootObject(false)
            .printUnquotedKeys(false)
            .build()
    }
}
