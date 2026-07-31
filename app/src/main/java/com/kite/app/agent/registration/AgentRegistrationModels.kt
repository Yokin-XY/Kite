package com.kite.app.agent.registration

/** 面向用户的稳定 Agent 身份；显示名称允许改名或重复。 */
data class AgentDefinition(
    val agentId: String,
    val displayName: String,
    val description: String = "",
    val iconText: String = ""
)

/**
 * Agent 的启动或连接资料。
 *
 * 卡片只保存 [AgentDefinition.agentId]；provider、argv 和连接引用只存在于登记层。
 */
sealed interface AgentLaunchSpec {
    val providerId: String
    val protocol: String
    val transport: String

    data class Managed(
        override val providerId: String,
        override val protocol: String,
        override val transport: String,
        val argv: List<String>
    ) : AgentLaunchSpec

    data class Attach(
        override val providerId: String,
        override val protocol: String,
        override val transport: String,
        /** 指向受控连接配置；这里不保存端口、PID 或密钥。 */
        val connectionReference: String
    ) : AgentLaunchSpec
}

sealed interface AgentRegistrationSource {
    data class Resource(val resourceId: String) : AgentRegistrationSource
    data object Custom : AgentRegistrationSource
}

/** Agent 能力登记；不包含安装、配置内容、运行进程或会话事实。 */
data class AgentRegistration(
    val definition: AgentDefinition,
    val source: AgentRegistrationSource,
    val launch: AgentLaunchSpec,
    /** true 只表示打开前需要配置，不保存配置本身。 */
    val configurationRequired: Boolean = false,
    /** 指向进程内配置适配器；不包含配置内容、路径或密钥。 */
    val configAdapterId: String? = null,
    /** 指向进程内会话管理适配器；不包含会话或运行状态。 */
    val sessionAdapterId: String? = null
)

enum class AgentInstallationStatus {
    NotApplicable,
    NotInstalled,
    Installed
}

enum class AgentConfigurationStatus {
    NotRequired,
    Unknown,
    Required,
    Ready
}

enum class AgentRuntimeStatus {
    Stopped,
    Running
}

enum class AgentLaunchStatus {
    Ready,
    Unsupported
}

data class AgentRegistryEntry(
    val registration: AgentRegistration,
    val installationStatus: AgentInstallationStatus,
    val configurationStatus: AgentConfigurationStatus,
    val runtimeStatus: AgentRuntimeStatus,
    val launchStatus: AgentLaunchStatus
) {
    val registered: Boolean get() = true

    val canOpen: Boolean get() =
        installationStatus != AgentInstallationStatus.NotInstalled &&
            configurationStatus != AgentConfigurationStatus.Required &&
            launchStatus == AgentLaunchStatus.Ready
}

data class AgentRegistrationConflict(
    val agentId: String,
    val sources: List<String>,
    val message: String = "Agent ID 重复：$agentId"
)

data class AgentRegistrySnapshot(
    val entries: List<AgentRegistryEntry>,
    val conflicts: List<AgentRegistrationConflict>
) {
    fun entry(agentId: String): AgentRegistryEntry? =
        entries.singleOrNull { it.registration.definition.agentId == agentId }
}

object AgentRegistrationPolicy {
    private val stableId = Regex("[a-z0-9](?:[a-z0-9._-]*[a-z0-9])?")

    fun problem(registration: AgentRegistration): String? {
        val definition = registration.definition
        if (!stableId.matches(definition.agentId)) return "agentId 格式无效：${definition.agentId}"
        if (definition.displayName.isBlank()) return "Agent 显示名称不能为空"
        if (!stableId.matches(registration.launch.providerId)) {
            return "providerId 格式无效：${registration.launch.providerId}"
        }
        if (registration.launch.protocol.isBlank()) return "Agent protocol 不能为空"
        if (registration.launch.transport.isBlank()) return "Agent transport 不能为空"
        registration.configAdapterId?.let { adapterId ->
            if (!stableId.matches(adapterId)) return "configAdapterId 格式无效：$adapterId"
        }
        registration.sessionAdapterId?.let { adapterId ->
            if (!stableId.matches(adapterId)) return "sessionAdapterId 格式无效：$adapterId"
        }
        return when (val launch = registration.launch) {
            is AgentLaunchSpec.Managed -> if (launch.argv.isEmpty() || launch.argv.any(String::isBlank)) {
                "Managed Agent 必须声明完整 argv"
            } else {
                null
            }
            is AgentLaunchSpec.Attach -> if (launch.connectionReference.isBlank()) {
                "Attach Agent 必须引用连接配置"
            } else {
                null
            }
        }
    }
}

object AgentRegistryAssembler {
    fun assemble(
        registrations: List<AgentRegistration>,
        installedResourceIds: Set<String>,
        configurationByAgentId: Map<String, AgentConfigurationStatus> = emptyMap(),
        runningProviderIds: Set<String> = emptySet(),
        launchStatus: (AgentLaunchSpec) -> AgentLaunchStatus = { launch ->
            if (launch is AgentLaunchSpec.Managed) AgentLaunchStatus.Ready else AgentLaunchStatus.Unsupported
        }
    ): AgentRegistrySnapshot {
        val valid = registrations.filter { AgentRegistrationPolicy.problem(it) == null }
        val grouped = valid.groupBy { it.definition.agentId }
        val conflicts = grouped
            .filterValues { it.size > 1 }
            .map { (agentId, duplicates) ->
                AgentRegistrationConflict(
                    agentId = agentId,
                    sources = duplicates.map(::sourceLabel).distinct()
                )
            }
            .sortedBy(AgentRegistrationConflict::agentId)
        val entries = grouped
            .filterValues { it.size == 1 }
            .values
            .map { it.single() }
            .map { registration ->
                val agentId = registration.definition.agentId
                AgentRegistryEntry(
                    registration = registration,
                    installationStatus = when (val source = registration.source) {
                        AgentRegistrationSource.Custom -> AgentInstallationStatus.NotApplicable
                        is AgentRegistrationSource.Resource -> if (source.resourceId in installedResourceIds) {
                            AgentInstallationStatus.Installed
                        } else {
                            AgentInstallationStatus.NotInstalled
                        }
                    },
                    configurationStatus = configurationByAgentId[agentId]
                        ?: if (registration.configurationRequired) {
                            AgentConfigurationStatus.Required
                        } else {
                            AgentConfigurationStatus.NotRequired
                        },
                    runtimeStatus = if (registration.launch.providerId in runningProviderIds) {
                        AgentRuntimeStatus.Running
                    } else {
                        AgentRuntimeStatus.Stopped
                    },
                    launchStatus = launchStatus(registration.launch)
                )
            }
            .sortedWith(
                compareBy<AgentRegistryEntry> { it.registration.definition.displayName.lowercase() }
                    .thenBy { it.registration.definition.agentId }
            )
        return AgentRegistrySnapshot(entries = entries, conflicts = conflicts)
    }

    private fun sourceLabel(registration: AgentRegistration): String = when (val source = registration.source) {
        AgentRegistrationSource.Custom -> "custom"
        is AgentRegistrationSource.Resource -> source.resourceId
    }
}

sealed interface AgentRegistrationWriteResult {
    data class Accepted(val registration: AgentRegistration) : AgentRegistrationWriteResult
    data class Rejected(val message: String) : AgentRegistrationWriteResult
}
