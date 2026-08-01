package com.kite.app.platform.runs

import android.content.Context
import com.kite.app.agent.acp.AcpProcessAgentProvider
import com.kite.app.agent.acp.AcpProcessChannelLauncher
import com.kite.app.agent.acp.AcpProcessProviderDescriptor
import com.kite.app.agent.config.AgentConfigAdapterRegistry
import com.kite.app.agent.config.ContainerAgentConfigProjection
import com.kite.app.agent.config.AgentPersistentConfigChange
import com.kite.app.agent.config.AgentSessionConfigurationOverlayProvider
import com.kite.app.agent.config.defaultAgentConfigAdapters
import com.kite.app.agent.config.mergeAgentSessionConfigurationOverlay
import com.kite.app.agent.config.normalizePublishedSessionConfiguration
import com.kite.app.agent.contract.AgentConfigCategory
import com.kite.app.agent.contract.AgentFailureCode
import com.kite.app.agent.contract.AgentOperationResult
import com.kite.app.agent.contract.AgentSessionPhase
import com.kite.app.agent.contract.AgentSessionRenameRequest
import com.kite.app.agent.contract.KiteAgentProvider
import com.kite.app.agent.registration.AgentConfigurationStatus
import com.kite.app.agent.registration.AgentInstallationStatus
import com.kite.app.agent.registration.AgentLaunchSpec
import com.kite.app.agent.registration.KiteAgentRegistry
import com.kite.app.agent.process.AgentProcessFactory
import com.kite.app.agent.process.AgentProcessLaunch
import com.kite.app.agent.process.JavaAgentProcessFactory
import com.kite.app.agent.runtime.AgentAttachProviderRegistry
import com.kite.app.agent.runtime.AgentDraftCapabilityCatalog
import com.kite.app.agent.runtime.AgentDraftModelSelection
import com.kite.app.agent.runtime.AgentRuntimeRegistry
import com.kite.app.agent.runtime.AgentRuntimeStartRequest
import com.kite.app.foundation.runtime.AndroidSharedStorageManager
import com.kite.app.foundation.runtime.RuntimeExecutionGuaranteeCodec
import com.kite.app.foundation.runtime.RuntimeExecutionGuaranteeEvidenceCodec
import com.kite.app.foundation.runtime.RuntimeExecutionPayload
import com.kite.app.foundation.runtime.RuntimeExecutionRequest
import com.kite.app.foundation.runtime.RuntimeExecutionRequirement
import com.kite.app.foundation.runtime.RuntimeExposureScope
import com.kite.app.foundation.contracts.ContainerExecConfig
import com.kite.app.agent.runtime.AgentRuntimeStatusSink
import com.kite.app.agent.session.AgentSessionAdministrationAdapter
import com.kite.app.agent.session.AgentSessionAdministrationAdapterRegistry
import com.kite.app.agent.session.AgentSessionCommand
import com.kite.app.agent.session.AgentSessionCommandExecutor
import com.kite.app.agent.session.opencode.OpenCodeAgentSessionAdministrationAdapter
import com.kite.app.agent.store.AgentDraftCapabilityCacheStore
import com.kite.app.application.runs.RecipeExecutionEvent
import com.kite.app.application.runs.RecipeStepExecutionRequest
import com.kite.app.application.runs.RunStateMutation
import com.kite.app.foundation.workspace.WorkSurfaceRuntimeBridge
import com.kite.app.foundation.workspace.KFWorkspaceManager
import com.kite.app.foundation.workspace.ContainerVisibleFileResolver
import com.kite.app.foundation.workspace.ManagedRuntimeLaunchPlan
import com.kite.app.foundation.workspace.ManagedRuntimeLaunchPlanner
import com.kite.app.foundation.terminal.TerminalRuntimeHost
import com.kite.app.resources.KiteResourceAgentProfile
import com.kite.app.resources.KiteResourceAgentRuntimeDependency
import com.kite.app.resources.KiteResourceManifest
import com.kite.app.resources.KiteResourceManifestLoader
import com.kite.app.run.CardRunAgentBinding
import com.kite.app.run.CardRunAgentConnectionStatus
import com.kite.app.run.CardRunStatus
import com.kite.app.run.CardRunSurface
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import com.kite.app.foundation.service.BackgroundRuntimeHealthStatus
import com.kite.app.foundation.service.BackgroundRuntimeHost
import com.kite.app.foundation.service.BackgroundRuntimeKind
import com.kite.app.foundation.service.BackgroundRuntimeMode
import com.kite.app.foundation.service.BackgroundRuntimeRecord
import com.kite.app.foundation.service.BackgroundRuntimeRegistry
import com.kite.app.foundation.service.BackgroundRuntimeRestartPolicy
import com.kite.app.foundation.service.RuntimeRetentionClass

internal interface AgentRecipeRuntime {
    fun owns(instanceId: String, generation: Long): Boolean

    fun start(
        request: RecipeStepExecutionRequest,
        environment: Map<String, String>,
        callback: (RecipeExecutionEvent) -> Unit
    )

    fun stop(instanceId: String, generation: Long, callback: (Boolean) -> Unit)
}

internal data class ManagedAgentProcessLaunch(
    val process: AgentProcessLaunch,
    val runtimeLane: String,
    val fallbackReason: String,
)

internal fun interface ManagedAgentProcessLaunchPlanner {
    fun plan(
        argv: List<String>,
        workingDirectory: String,
        environment: Map<String, String>,
        runtimeGuarantees: Set<String>,
        runtimeGuaranteeEvidence: Map<String, String>,
    ): ManagedAgentProcessLaunch
}

internal fun interface ManagedAgentRuntimeDependencyPreparer {
    suspend fun prepare(dependencies: List<KiteResourceAgentRuntimeDependency>)
}

/** 资源声明依赖，后台运行时仍由统一 Registry/Host 登记、启动和健康确认。 */
internal class AndroidManagedAgentRuntimeDependencyPreparer(context: Context) :
    ManagedAgentRuntimeDependencyPreparer {
    private val appContext = context.applicationContext
    private val activationProjection = ContainerAgentConfigProjection {
        WorkSurfaceRuntimeBridge.getSavedContainer(appContext)
    }

    override suspend fun prepare(dependencies: List<KiteResourceAgentRuntimeDependency>) {
        if (dependencies.isEmpty()) return
        val space = KFWorkspaceManager.getCurrentSpace(appContext)
            ?: KFWorkspaceManager.ensureActiveSpace(appContext)
        dependencies.forEach { dependency ->
            val runtimeId = "background-${space.id}-${dependency.id}"
            if (!isActivated(dependency)) {
                BackgroundRuntimeHost.stopRuntime(appContext, runtimeId)
                return@forEach
            }
            val definition = BackgroundRuntimeRecord(
                id = runtimeId,
                spaceId = space.id,
                kind = BackgroundRuntimeKind.CUSTOM,
                mode = BackgroundRuntimeMode.PROCESS,
                title = dependency.title,
                workingDirectory = dependency.workdir,
                startCommand = dependency.argv.joinToString(" ", transform = ::shellQuote),
                startExecutable = dependency.argv.first(),
                startArguments = dependency.argv.drop(1),
                environment = dependency.environment,
                runtimeGuarantees = dependency.runtimeGuarantees,
                runtimeGuaranteeEvidence = dependency.runtimeGuaranteeEvidence,
                environmentFiles = dependency.environmentFiles,
                bindAddress = dependency.bindAddress.takeIf(String::isNotBlank),
                bindPort = dependency.bindPort,
                exposureScope = if (dependency.bindAddress == "127.0.0.1") {
                    RuntimeExposureScope.LOOPBACK_ONLY
                } else {
                    RuntimeExposureScope.HOST_LOCAL_ONLY
                },
                healthHttpPath = dependency.healthHttpPath.takeIf(String::isNotBlank),
                healthCheckStartupDelayMs = dependency.healthCheckStartupDelayMs,
                logPath = BackgroundRuntimeRegistry.buildLogFile(appContext, runtimeId).absolutePath,
                createdAt = System.currentTimeMillis(),
                healthStatus = BackgroundRuntimeHealthStatus.INACTIVE,
                restartPolicy = dependency.restartPolicy.toRestartPolicy(),
                retentionClass = dependency.retentionClass.toRetentionClass(),
            )
            BackgroundRuntimeHost.ensureRuntimeReady(
                context = appContext,
                definition = definition,
                timeoutMs = dependency.startupTimeoutMs,
            ).getOrThrow()
        }
    }

    private fun isActivated(dependency: KiteResourceAgentRuntimeDependency): Boolean {
        val activationFile = dependency.activationFile.takeIf(String::isNotBlank) ?: return true
        return runCatching {
            activationProjection.resolve(activationFile)
                ?.readFile
                ?.takeIf(File::isFile)
                ?.readText()
                ?.trim()
                ?.isNotBlank() == true
        }.getOrDefault(false)
    }

    private fun String.toRestartPolicy(): BackgroundRuntimeRestartPolicy = when (lowercase()) {
        "never" -> BackgroundRuntimeRestartPolicy.NEVER
        "always_core" -> BackgroundRuntimeRestartPolicy.ALWAYS_CORE
        else -> BackgroundRuntimeRestartPolicy.ON_FAILURE
    }

    private fun String.toRetentionClass(): RuntimeRetentionClass = when (lowercase()) {
        "critical_core" -> RuntimeRetentionClass.CRITICAL_CORE
        "interactive" -> RuntimeRetentionClass.INTERACTIVE
        "batch" -> RuntimeRetentionClass.BATCH
        "ephemeral" -> RuntimeRetentionClass.EPHEMERAL
        else -> RuntimeRetentionClass.RESIDENT
    }

    private fun shellQuote(value: String): String = "'${value.replace("'", "'\\''")}'"
}

/** Agent 只声明结构化 argv；实际 Host Node / PRoot 选择仍由统一 Node Provider 决定。 */
internal class AndroidManagedAgentProcessLaunchPlanner(context: Context) : ManagedAgentProcessLaunchPlanner {
    private val appContext = context.applicationContext

    override fun plan(
        argv: List<String>,
        workingDirectory: String,
        environment: Map<String, String>,
        runtimeGuarantees: Set<String>,
        runtimeGuaranteeEvidence: Map<String, String>,
    ): ManagedAgentProcessLaunch {
        require(argv.isNotEmpty()) { "agent_process_command_empty" }
        val guarantees = RuntimeExecutionGuaranteeCodec.decode(runtimeGuarantees)
            ?: error("agent_runtime_guarantees_invalid")
        val guaranteeEvidence = RuntimeExecutionGuaranteeEvidenceCodec.normalize(runtimeGuaranteeEvidence)
            ?: error("agent_runtime_guarantee_evidence_invalid")
        val container = WorkSurfaceRuntimeBridge.ensureDefaultContainer(appContext)
        val activeEnvironment = WorkSurfaceRuntimeBridge.resolveActiveWorkspaceEnvironment(container)
        val runtimePlan = ManagedRuntimeLaunchPlanner.plan(
            context = appContext,
            container = container,
            workspaceDirectory = File(activeEnvironment.workspacePath),
            request = RuntimeExecutionRequest(
                payload = RuntimeExecutionPayload.Argv(argv.first(), argv.drop(1)),
                workingDirectory = workingDirectory,
                environment = environment,
                guarantees = guarantees,
                guaranteeEvidence = guaranteeEvidence,
            ),
        )
        return ManagedAgentProcessLaunchSelector.select(runtimePlan) { plan ->
            WorkSurfaceRuntimeBridge.buildProotExecConfig(appContext, plan)
        }
    }

}

/** Host 已就绪时绝不构建第二条 PRoot 进程；Proot 计划也只物化一份配置。 */
internal object ManagedAgentProcessLaunchSelector {
    fun select(
        runtimePlan: ManagedRuntimeLaunchPlan,
        prootConfig: (com.kite.app.foundation.runtime.ProotCompatibilityPlan) -> ContainerExecConfig,
    ): ManagedAgentProcessLaunch = when (runtimePlan) {
        is ManagedRuntimeLaunchPlan.Ready -> ManagedAgentProcessLaunch(
            process = AgentProcessLaunch(
                command = runtimePlan.config.args.toList(),
                environment = runtimePlan.config.env.associateTo(linkedMapOf()) { entry ->
                    entry.substringBefore('=') to entry.substringAfter('=', "")
                },
                workingDirectory = runtimePlan.config.workingDirectory,
            ),
            runtimeLane = runtimePlan.lane.value,
            fallbackReason = "none",
        )
        is ManagedRuntimeLaunchPlan.Proot -> prootConfig(runtimePlan.plan).let { config ->
            ManagedAgentProcessLaunch(
                process = AgentProcessLaunch(
                    command = config.command,
                    environment = config.env,
                ),
                runtimeLane = "proot_shell",
                fallbackReason = runtimePlan.reason,
            )
        }
        is ManagedRuntimeLaunchPlan.Blocked -> error("runtime_provider_blocked:${runtimePlan.reason}")
    }
}

/** 将资源注册的通用 Agent profile 接到统一 Host/PRoot 计划和进程级 AgentRuntimeRegistry。 */
internal class AndroidAgentRecipeRuntime(
    context: Context,
    private val processFactory: AgentProcessFactory = JavaAgentProcessFactory(),
    private val agentRegistry: KiteAgentRegistry = KiteAgentRegistry(context.applicationContext),
    private val agentConfigAdapters: AgentConfigAdapterRegistry = AgentConfigAdapterRegistry(
        defaultAgentConfigAdapters(
            context.applicationContext,
            commandExecutor = AndroidAgentConfigCommandExecutor(
                context.applicationContext,
                processFactory
            )
        )
    ),
    sessionAdministrationAdapters: AgentSessionAdministrationAdapterRegistry? = null,
    managedPreparation: (() -> Unit)? = null,
    managedProcessLaunchPlanner: ManagedAgentProcessLaunchPlanner? = null,
    managedRuntimeDependencyPreparer: ManagedAgentRuntimeDependencyPreparer? = null,
) : AgentRecipeRuntime {
    private data class ResolvedLaunch(
        val agentId: String?,
        val providerId: String,
        val displayName: String,
        val title: String,
        val version: String?,
        val launchMode: String,
        val protocol: String,
        val transport: String,
        val argv: List<String>,
        val runtimeGuarantees: Set<String>,
        val runtimeGuaranteeEvidence: Map<String, String>,
        val environmentFiles: Map<String, String>,
        val runtimeDependencies: List<KiteResourceAgentRuntimeDependency>,
        val connectionReference: String?,
        val configAdapterId: String?,
        val sessionAdapterId: String?
    )

    private val appContext = context.applicationContext
    private val sessionAdministrationAdapters = sessionAdministrationAdapters
        ?: AgentSessionAdministrationAdapterRegistry(
            listOf(
                OpenCodeAgentSessionAdministrationAdapter(
                    AgentSessionCommandExecutor(::executeSessionCommand)
                )
            )
        )
    private val manifestLoader = KiteResourceManifestLoader(appContext)
    private val draftCapabilityCache = AgentDraftCapabilityCacheStore(appContext)
    private val managedProcessLaunchPlanner = managedProcessLaunchPlanner
        ?: AndroidManagedAgentProcessLaunchPlanner(appContext)
    private val managedRuntimeDependencyPreparer = managedRuntimeDependencyPreparer
        ?: AndroidManagedAgentRuntimeDependencyPreparer(appContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val prepareManagedRuntime = managedPreparation ?: {
        WorkSurfaceRuntimeBridge.ensureBaseImageReady(appContext)
        KFWorkspaceManager.ensureDefaultSpace(appContext)
        WorkSurfaceRuntimeBridge.ensureDefaultContainer(appContext)
        TerminalRuntimeHost.refreshRuntimeSnapshot(appContext)
    }

    override fun owns(instanceId: String, generation: Long): Boolean =
        AgentRuntimeRegistry.session(instanceId)?.generation == generation

    override fun start(
        request: RecipeStepExecutionRequest,
        environment: Map<String, String>,
        callback: (RecipeExecutionEvent) -> Unit
    ) {
        val resolved = resolveLaunch(request, callback) ?: return
        val providerId = resolved.providerId
        val fixedAgentId = request.previousState.agentId?.trim()?.takeIf(String::isNotBlank)
        if (fixedAgentId != null && resolved.agentId != fixedAgentId) {
            callback(
                request.failedAgent(
                    "运行实例已固定绑定 Agent：$fixedAgentId",
                    providerId,
                    agentId = fixedAgentId
                )
            )
            return
        }
        val cwd = request.step.workdir?.trim().orEmpty().ifBlank { DEFAULT_WORKDIR }
        if (resolved.launchMode == LAUNCH_MODE_ATTACH) {
            val provider = resolved.connectionReference
                ?.let(AgentAttachProviderRegistry::provider)
            if (provider == null) {
                callback(
                    request.failedAgent(
                        "Agent 的 Attach 连接尚未注册：${resolved.connectionReference.orEmpty()}",
                        providerId,
                        agentId = resolved.agentId
                    )
                )
                return
            }
            scope.launch {
                startConnection(
                    request = request,
                    resolved = resolved,
                    cwd = cwd,
                    provider = provider,
                    managedOwnership = false,
                    runtimeLane = null,
                    runtimeFallbackReason = null,
                    callback = callback
                )
            }
            return
        }
        if (resolved.launchMode != LAUNCH_MODE_MANAGED) {
            callback(
                request.failedAgent(
                    "暂不支持 Agent 启动方式：${resolved.launchMode}",
                    providerId,
                    agentId = resolved.agentId
                )
            )
            return
        }
        if (resolved.protocol != PROTOCOL_ACP || resolved.transport != TRANSPORT_STDIO) {
            callback(
                request.failedAgent(
                    "暂不支持 Agent provider：${resolved.protocol}/${resolved.transport}",
                    providerId,
                    agentId = resolved.agentId
                )
            )
            return
        }
        scope.launch {
            runCatching(prepareManagedRuntime).getOrElse { error ->
                callback(
                    request.failedAgent(
                        "Ubuntu 环境未就绪：${error.message ?: error.javaClass.simpleName}",
                        providerId,
                        agentId = resolved.agentId
                    )
                )
                return@launch
            }
            val processLaunch = runCatching {
                managedRuntimeDependencyPreparer.prepare(resolved.runtimeDependencies)
                val resolvedEnvironment = environment + resolveEnvironmentFiles(
                    resolved.environmentFiles
                )
                managedProcessLaunchPlanner.plan(
                    argv = resolved.argv,
                    workingDirectory = cwd,
                    environment = resolvedEnvironment,
                    runtimeGuarantees = resolved.runtimeGuarantees,
                    runtimeGuaranteeEvidence = resolved.runtimeGuaranteeEvidence,
                )
            }.getOrElse { error ->
                callback(
                    request.failedAgent(
                        "Agent 启动准备失败：${error.message}",
                        providerId,
                        agentId = resolved.agentId
                    )
                )
                return@launch
            }
            val provider = AcpProcessAgentProvider(
                descriptor = AcpProcessProviderDescriptor(
                    id = providerId,
                    name = resolved.displayName,
                    title = resolved.title,
                    version = resolved.version
                ),
                launcher = AcpProcessChannelLauncher {
                    processFactory.start(processLaunch.process)
                },
                sessionDelete = sessionAdministrationAdapters
                    .adapter(resolved.sessionAdapterId)
                    ?.let { adapter ->
                        { targetSessionId: String -> adapter.deleteSession(targetSessionId, cwd) }
                    },
                sessionRename = sessionAdministrationAdapters
                    .adapter(resolved.sessionAdapterId)
                    ?.takeIf(AgentSessionAdministrationAdapter::supportsRename)
                    ?.let { adapter ->
                        { renameRequest: AgentSessionRenameRequest -> adapter.renameSession(renameRequest, cwd) }
                    },
            )
            startConnection(
                request = request,
                resolved = resolved,
                cwd = cwd,
                provider = provider,
                managedOwnership = true,
                runtimeLane = processLaunch.runtimeLane,
                runtimeFallbackReason = processLaunch.fallbackReason,
                callback = callback
            )
        }
    }

    private suspend fun startConnection(
        request: RecipeStepExecutionRequest,
        resolved: ResolvedLaunch,
        cwd: String,
        provider: KiteAgentProvider,
        managedOwnership: Boolean,
        runtimeLane: String?,
        runtimeFallbackReason: String?,
        callback: (RecipeExecutionEvent) -> Unit
    ) {
        val providerId = resolved.providerId
        val preparingMessage = if (managedOwnership) {
            "正在启动 ${resolved.displayName}"
        } else {
            "正在连接 ${resolved.displayName}"
        }
        callback(
            RecipeExecutionEvent.Progress(
                request.instanceId,
                request.generation,
                request.stepIndex,
                request.agentMutation(
                    agentId = resolved.agentId,
                    providerId = providerId,
                    sessionId = null,
                    connectionStatus = CardRunAgentConnectionStatus.Preparing,
                    message = preparingMessage,
                    managedOwnership = managedOwnership,
                    runtimeLane = runtimeLane,
                    runtimeFallbackReason = runtimeFallbackReason,
                )
            )
        )
        val statusSink = AgentRuntimeStatusSink { sessionId, phase, message ->
            callback(
                RecipeExecutionEvent.Progress(
                    request.instanceId,
                    request.generation,
                    request.stepIndex,
                    request.agentMutation(
                        agentId = resolved.agentId,
                        providerId = providerId,
                        sessionId = sessionId,
                        connectionStatus = phase.toConnectionStatus(),
                        message = message ?: phase.defaultMessage(),
                        managedOwnership = managedOwnership,
                        runtimeLane = runtimeLane,
                        runtimeFallbackReason = runtimeFallbackReason,
                    )
                )
            )
        }
        val configAdapter = agentConfigAdapters.adapter(resolved.configAdapterId)
        val nativeSessionConfiguration = configAdapter
            ?.readSessionConfiguration(resolved.agentId ?: providerId)
            .orEmpty()
        val runtimeProvider = if (configAdapter == null) {
            provider
        } else {
            AgentSessionConfigurationOverlayProvider(
                delegate = provider,
                agentId = resolved.agentId ?: providerId,
                adapter = configAdapter,
                initialConfiguration = nativeSessionConfiguration,
            )
        }
        val draftCatalogKey = resolved.agentId ?: providerId
        val cachedDraftCatalog = draftCapabilityCache.catalog(draftCatalogKey)
            ?: AgentDraftCapabilityCatalog()
        val initialDraftCatalog = cachedDraftCatalog.copy(
            configuration = mergeAgentSessionConfigurationOverlay(
                options = cachedDraftCatalog.configuration,
                native = nativeSessionConfiguration,
            ).options.let { options ->
                configAdapter?.normalizePublishedSessionConfiguration(options)
                    ?: options.filterNot { it.category == AgentConfigCategory.ThoughtLevel }
            },
        )
        when (val result = AgentRuntimeRegistry.start(
            request = AgentRuntimeStartRequest(
                instanceId = request.instanceId,
                generation = request.generation,
                providerId = providerId,
                cwd = cwd,
                additionalDirectories = AndroidSharedStorageManager.containerRoots(appContext),
                preferredSessionId = null,
                normalizeConfiguration = { options ->
                    configAdapter?.normalizePublishedSessionConfiguration(options)
                        ?: options.filterNot { it.category == AgentConfigCategory.ThoughtLevel }
                },
                resolveDraftModelSelection = { target: AgentDraftModelSelection, options ->
                    configAdapter?.sessionModelSelection(
                        AgentPersistentConfigChange.SelectProvider(target.providerId, target.modelId),
                        options
                    )
                },
                initialDraftCatalog = initialDraftCatalog,
                onDraftCatalogChanged = { catalog -> draftCapabilityCache.put(draftCatalogKey, catalog) }
            ),
            provider = runtimeProvider,
            statusSink = statusSink
        )) {
                is AgentOperationResult.Success -> callback(
                    RecipeExecutionEvent.AwaitingUser(
                        request.instanceId,
                        request.generation,
                        request.stepIndex,
                        request.agentMutation(
                            agentId = resolved.agentId,
                            providerId = providerId,
                            sessionId = result.value.sessionId,
                            connectionStatus = CardRunAgentConnectionStatus.Ready,
                            message = if (result.value.isDraft) "可以开始新会话" else "准备就绪",
                            managedOwnership = managedOwnership,
                            runtimeLane = runtimeLane,
                            runtimeFallbackReason = runtimeFallbackReason,
                        )
                    )
                )
                is AgentOperationResult.Failure -> {
                    val authenticationRequired = result.code == AgentFailureCode.AuthenticationRequired
                    callback(
                        request.failedAgent(
                            message = if (authenticationRequired) {
                                "请先登录 ${resolved.displayName}"
                            } else {
                                result.message
                            },
                            providerId = providerId,
                            agentId = resolved.agentId,
                            causeMessage = result.cause?.message.takeUnless { authenticationRequired }
                        )
                    )
                }
                is AgentOperationResult.Unsupported -> callback(
                    request.failedAgent(
                        "Agent 不支持必要操作：${result.operation}",
                        providerId,
                        agentId = resolved.agentId
                    )
                )
        }
    }

    override fun stop(instanceId: String, generation: Long, callback: (Boolean) -> Unit) {
        scope.launch {
            callback(AgentRuntimeRegistry.stop(instanceId, generation))
        }
    }

    private fun resolveProfile(providerId: String): ResolvedLaunch? {
        val matches = manifestLoader.manifests().values.flatMap { manifest ->
            manifest.agentProfiles
                .filter { it.providerId == providerId }
                .map { profile -> profile.toResolvedLaunch(manifest) }
        }
        return matches.singleOrNull()
    }

    private fun resolveLaunch(
        request: RecipeStepExecutionRequest,
        callback: (RecipeExecutionEvent) -> Unit
    ): ResolvedLaunch? {
        val agentId = request.step.agentId?.trim().orEmpty()
        if (agentId.isBlank()) {
            val legacyProviderId = request.step.providerId?.trim().orEmpty()
            if (legacyProviderId.isBlank()) {
                callback(request.failedAgent("agent_missing_id"))
                return null
            }
            return resolveProfile(legacyProviderId).also { resolved ->
                if (resolved == null) {
                    callback(
                        request.failedAgent(
                            "未找到旧卡 Agent provider 注册：$legacyProviderId",
                            legacyProviderId
                        )
                    )
                }
            }
        }
        val snapshot = agentRegistry.snapshot()
        val entry = snapshot.entry(agentId)
        if (entry == null) {
            val conflict = snapshot.conflicts.firstOrNull { it.agentId == agentId }
            callback(
                request.failedAgent(
                    conflict?.message ?: "未找到 Agent 登记：$agentId"
                )
            )
            return null
        }
        val providerId = entry.registration.launch.providerId
        if (entry.installationStatus == AgentInstallationStatus.NotInstalled) {
            callback(request.failedAgent("Agent 尚未安装：${entry.registration.definition.displayName}", providerId))
            return null
        }
        if (entry.configurationStatus == AgentConfigurationStatus.Required) {
            callback(request.failedAgent("Agent 尚未完成配置：${entry.registration.definition.displayName}", providerId))
            return null
        }
        return when (val launch = entry.registration.launch) {
            is AgentLaunchSpec.Managed -> {
                val profile = resourceProfile(entry.registration.source, agentId)
                ResolvedLaunch(
                    agentId = entry.registration.definition.agentId,
                    providerId = launch.providerId,
                    displayName = entry.registration.definition.displayName,
                    title = entry.registration.definition.displayName,
                    version = null,
                    launchMode = LAUNCH_MODE_MANAGED,
                    protocol = launch.protocol,
                    transport = launch.transport,
                    argv = launch.argv,
                    runtimeGuarantees = launch.runtimeGuarantees,
                    runtimeGuaranteeEvidence = launch.runtimeGuaranteeEvidence,
                    environmentFiles = profile?.environmentFiles.orEmpty(),
                    runtimeDependencies = profile?.runtimeDependencies.orEmpty(),
                    connectionReference = null,
                    configAdapterId = entry.registration.configAdapterId,
                    sessionAdapterId = entry.registration.sessionAdapterId
                )
            }
            is AgentLaunchSpec.Attach -> ResolvedLaunch(
                agentId = entry.registration.definition.agentId,
                providerId = launch.providerId,
                displayName = entry.registration.definition.displayName,
                title = entry.registration.definition.displayName,
                version = null,
                launchMode = LAUNCH_MODE_ATTACH,
                protocol = launch.protocol,
                transport = launch.transport,
                argv = emptyList(),
                runtimeGuarantees = emptySet(),
                runtimeGuaranteeEvidence = emptyMap(),
                environmentFiles = emptyMap(),
                runtimeDependencies = emptyList(),
                connectionReference = launch.connectionReference,
                configAdapterId = entry.registration.configAdapterId,
                sessionAdapterId = entry.registration.sessionAdapterId
            )
        }
    }

    private fun KiteResourceAgentProfile.toResolvedLaunch(
        manifest: KiteResourceManifest
    ): ResolvedLaunch = ResolvedLaunch(
            agentId = agentId,
            providerId = providerId,
            displayName = manifest.name.ifBlank { providerId },
            title = title.ifBlank { manifest.name.ifBlank { providerId } },
            version = manifest.version.takeIf { it.isNotBlank() && it != "official" },
            launchMode = launchMode,
            protocol = protocol,
            transport = transport,
            argv = argv,
            runtimeGuarantees = runtimeGuarantees,
            runtimeGuaranteeEvidence = runtimeGuaranteeEvidence,
            environmentFiles = environmentFiles,
            runtimeDependencies = runtimeDependencies,
            connectionReference = connectionReference.takeIf(String::isNotBlank),
            configAdapterId = configAdapterId.takeIf(String::isNotBlank),
            sessionAdapterId = sessionAdapterId.takeIf(String::isNotBlank)
        )

    private fun resourceProfile(
        source: com.kite.app.agent.registration.AgentRegistrationSource,
        agentId: String,
    ): KiteResourceAgentProfile? {
        val resourceId = (source as? com.kite.app.agent.registration.AgentRegistrationSource.Resource)
            ?.resourceId ?: return null
        return manifestLoader.manifests()[resourceId]
            ?.agentProfiles
            ?.singleOrNull { it.agentId == agentId }
    }

    private fun resolveEnvironmentFiles(files: Map<String, String>): Map<String, String> = buildMap {
        files.forEach { (name, containerPath) ->
            require(ENVIRONMENT_NAME.matches(name)) { "agent_environment_name_invalid:$name" }
            val file = ContainerVisibleFileResolver.resolve(appContext, containerPath)
                ?.takeIf(File::isFile)
                ?: error("agent_environment_file_missing:$containerPath")
            val value = file.readText().trim()
            require(value.isNotBlank()) { "agent_environment_file_empty:$containerPath" }
            put(name, value)
        }
    }

    private fun RecipeStepExecutionRequest.agentMutation(
        agentId: String?,
        providerId: String,
        sessionId: String?,
        connectionStatus: CardRunAgentConnectionStatus,
        message: String,
        managedOwnership: Boolean,
        runtimeLane: String? = null,
        runtimeFallbackReason: String? = null,
    ): RunStateMutation = RunStateMutation(
        status = if (connectionStatus == CardRunAgentConnectionStatus.Failed) {
            CardRunStatus.Failed
        } else {
            CardRunStatus.Running
        },
        surface = CardRunSurface.Agent,
        currentStepIndex = stepIndex,
        runtimeRootOwnerId = runtimeRootOwnerId.takeIf { managedOwnership },
        runtimeOwnerId = runtimeOwnerId.takeIf { managedOwnership },
        runtimeUnitId = runtimeUnitId.takeIf { managedOwnership },
        ownedRuntimeOwnerIds = if (managedOwnership) {
            previousState.ownedRuntimeOwnerIds
                .plus(runtimeOwnerId.orEmpty())
                .filter(String::isNotBlank)
                .distinct()
        } else {
            emptyList()
        },
        runtimeLane = runtimeLane,
        runtimeFallbackReason = runtimeFallbackReason,
        lastMeaningfulOutput = message,
        lastError = message.takeIf { connectionStatus == CardRunAgentConnectionStatus.Failed },
        agentId = agentId,
        agentBinding = CardRunAgentBinding(
            providerId = providerId,
            sessionId = sessionId,
            status = connectionStatus,
            statusMessage = message
        ),
        clearNextActionUrl = true
    )

    private fun RecipeStepExecutionRequest.failedAgent(
        message: String,
        providerId: String = step.providerId.orEmpty(),
        causeMessage: String? = null,
        agentId: String? = step.agentId
    ): RecipeExecutionEvent.Failed {
        val detail = causeMessage?.takeIf { it.isNotBlank() && !message.contains(it) }
            ?.let { "$message：$it" }
            ?: message
        return RecipeExecutionEvent.Failed(
            instanceId = instanceId,
            generation = generation,
            stepIndex = stepIndex,
            message = detail,
            mutation = RunStateMutation(
                status = CardRunStatus.Failed,
                surface = CardRunSurface.Agent,
                currentStepIndex = stepIndex,
                lastError = detail,
                agentId = agentId,
                agentBinding = providerId.takeIf(String::isNotBlank)?.let {
                    CardRunAgentBinding(
                        providerId = it,
                        sessionId = previousState.agentBinding
                            ?.takeIf { binding -> binding.providerId == providerId }
                            ?.sessionId,
                        status = CardRunAgentConnectionStatus.Failed,
                        statusMessage = detail
                    )
                },
                clearRunBinding = true
            )
        )
    }

    private fun AgentSessionPhase.toConnectionStatus(): CardRunAgentConnectionStatus = when (this) {
        AgentSessionPhase.Preparing -> CardRunAgentConnectionStatus.Preparing
        AgentSessionPhase.WaitingPermission -> CardRunAgentConnectionStatus.WaitingPermission
        AgentSessionPhase.Failed -> CardRunAgentConnectionStatus.Failed
        AgentSessionPhase.Closed -> CardRunAgentConnectionStatus.Stopped
        AgentSessionPhase.Ready,
        AgentSessionPhase.Prompting,
        AgentSessionPhase.Cancelling,
        AgentSessionPhase.Cancelled -> CardRunAgentConnectionStatus.Ready
    }

    private fun AgentSessionPhase.defaultMessage(): String = when (this) {
        AgentSessionPhase.Preparing -> "正在准备 Agent 会话"
        AgentSessionPhase.Ready -> "准备就绪"
        AgentSessionPhase.Prompting -> "正在生成回复"
        AgentSessionPhase.WaitingPermission -> "等待权限选择"
        AgentSessionPhase.Cancelling -> "正在停止生成"
        AgentSessionPhase.Cancelled -> "已停止生成"
        AgentSessionPhase.Failed -> "Agent 会话失败"
        AgentSessionPhase.Closed -> "Agent 会话已关闭"
    }

    private suspend fun executeSessionCommand(
        request: AgentSessionCommand,
    ): AgentOperationResult<Unit> = withContext(Dispatchers.IO) {
        val config = runCatching {
            require(request.argv.isNotEmpty()) { "agent_session_command_empty" }
            WorkSurfaceRuntimeBridge.buildRequiredProotExecConfig(
                context = appContext,
                request = RuntimeExecutionRequest(
                    payload = RuntimeExecutionPayload.Argv(request.argv.first(), request.argv.drop(1)),
                    workingDirectory = request.cwd,
                    requirements = setOf(RuntimeExecutionRequirement.FULL_LINUX),
                ),
                selectionReason = "agent_session_command_requires_proot",
            )
        }.getOrElse { error ->
            return@withContext AgentOperationResult.Failure("会话管理命令准备失败：${error.message}", error)
        }
        val process = runCatching {
            processFactory.start(AgentProcessLaunch(config.command, config.env))
        }.getOrElse { error ->
            return@withContext AgentOperationResult.Failure("会话管理命令启动失败：${error.message}", error)
        }
        try {
            coroutineScope {
                val stdout = async(Dispatchers.IO) { runCatching { process.stdoutLines.toList() }.getOrDefault(emptyList()) }
                val stderr = async(Dispatchers.IO) { runCatching { process.stderrLines.toList() }.getOrDefault(emptyList()) }
                request.stdinLine?.let { process.writeLine(it) }
                val exitCode = withTimeoutOrNull(SESSION_COMMAND_TIMEOUT_MS) { process.awaitExit() }
                if (exitCode == null) {
                    process.stop()
                    stdout.cancel()
                    stderr.cancel()
                    AgentOperationResult.Failure("${request.operationLabel}超时")
                } else {
                    val detail = stderr.await().joinToString(" ").trim().take(240)
                    stdout.await()
                    if (exitCode == 0) {
                        AgentOperationResult.Success(Unit)
                    } else {
                        AgentOperationResult.Failure(
                            detail.ifBlank { "${request.operationLabel}失败，exitCode=$exitCode" }
                        )
                    }
                }
            }
        } finally {
            process.close()
        }
    }

    private companion object {
        const val PROTOCOL_ACP = "acp"
        const val TRANSPORT_STDIO = "stdio"
        const val LAUNCH_MODE_MANAGED = "managed"
        const val LAUNCH_MODE_ATTACH = "attach"
        const val DEFAULT_WORKDIR = "/workspace"
        const val SESSION_COMMAND_TIMEOUT_MS = 20_000L
        val ENVIRONMENT_NAME = Regex("[A-Za-z_][A-Za-z0-9_]*")
    }
}
