package com.kite.app.agent.config.native

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
import com.kite.app.agent.config.AgentPersistentConfigCapability
import com.kite.app.agent.config.AgentPersistentConfigChange
import com.kite.app.agent.config.AgentSkillActivation
import com.kite.app.agent.config.AgentSkillDocumentReadResult
import com.kite.app.agent.config.AgentSkillDocumentWriteRequest
import com.kite.app.agent.config.AgentSkillDocumentWriteResult
import com.kite.app.agent.config.AgentSkillOperation
import com.kite.app.agent.config.ContainerAgentConfigProjection
import com.kite.app.agent.contract.AgentConfigCategory
import com.kite.app.agent.contract.AgentConfigChoice
import com.kite.app.agent.contract.AgentConfigOption
import com.kite.app.foundation.contracts.ContainerRecord
import java.security.MessageDigest

/**
 * 只补充原生会话协议语义和用户级 Skill 目录、不接管 Provider 配置的轻量 Adapter。
 *
 * Provider、模型和会话状态仍由 Agent 自己通过 ACP 公布；Skill 仍以 Agent 的原生目录
 * 为事实源。这层只负责把已核验的原生值投影成 Kite 统一语义，不在页面复制状态。
 */
internal abstract class ProtocolOnlyAgentConfigAdapter(
    final override val adapterId: String,
    containerProvider: () -> ContainerRecord?,
    private val skillRoots: List<String>,
    mutableSkillRoots: Set<String> = setOf(skillRoots.first()),
) : AgentConfigAdapter {
    private val projection = ContainerAgentConfigProjection(containerProvider)
    private val skillDirectory = NativeAgentSkillDirectory(
        project = projection::resolve,
        roots = skillRoots,
        mutableRoots = mutableSkillRoots,
    )

    override fun capabilities(): AgentConfigCapabilities = AgentConfigCapabilities(
        supported = setOf(AgentPersistentConfigCapability.Skill),
        credentialOwnership = AgentCredentialOwnership.Unsupported,
        skillOperations = setOf(AgentSkillOperation.Import, AgentSkillOperation.Remove),
    )

    override suspend fun discover(agentId: String): AgentConfigDiscovery {
        val probe = projection.resolve("${skillRoots.first()}/.kite-directory-probe")
            ?: return AgentConfigDiscovery(
                agentId = agentId,
                adapterId = adapterId,
                state = AgentConfigDiscoveryState.NoRuntime,
                warnings = listOf("Kite 运行容器尚未创建"),
            )
        val parent = probe.writeFile.parentFile
        return AgentConfigDiscovery(
            agentId = agentId,
            adapterId = adapterId,
            state = AgentConfigDiscoveryState.Ready,
            displayLocation = skillRoots.first(),
            writable = parent?.let { it.isDirectory && it.canWrite() || !it.exists() } == true,
            warnings = listOf("持久 Provider 与认证由 Agent 原生 CLI 管理；Skill 从原生用户目录读取"),
        )
    }

    override suspend fun readLive(agentId: String): AgentConfigReadResult {
        val discovery = discover(agentId)
        if (discovery.state != AgentConfigDiscoveryState.Ready) {
            return AgentConfigReadResult.Unavailable(discovery)
        }
        return runCatching { AgentConfigReadResult.Ready(snapshot(agentId)) }
            .getOrElse { AgentConfigReadResult.Failed("无法读取 Agent 原生 Skill 目录") }
    }

    override fun validate(request: AgentConfigApplyRequest): List<AgentConfigValidationProblem> = buildList {
        if (request.expectedRevision.isBlank()) {
            add(AgentConfigValidationProblem("expectedRevision", "缺少配置 revision"))
        }
        if (request.changes.size != 1) {
            add(AgentConfigValidationProblem("changes", "一次只能安装或移除一个 Skill"))
        }
        request.changes.forEachIndexed { index, change ->
            if (
                change !is AgentPersistentConfigChange.InstallSkill &&
                change !is AgentPersistentConfigChange.RemoveSkill
            ) {
                add(
                    AgentConfigValidationProblem(
                        field = "changes[$index]",
                        message = "当前 Agent 只允许 Kite 管理原生 Skill 目录",
                    ),
                )
            }
        }
    }

    override suspend fun apply(request: AgentConfigApplyRequest): AgentConfigApplyResult {
        val problems = validate(request)
        if (problems.isNotEmpty()) return AgentConfigApplyResult.Rejected(problems)
        val discovery = discover(request.agentId)
        if (discovery.state != AgentConfigDiscoveryState.Ready) {
            return AgentConfigApplyResult.Unavailable(discovery)
        }
        val before = runCatching { snapshot(request.agentId) }.getOrElse {
            return AgentConfigApplyResult.Failed("无法读取当前 Agent Skill 目录", restored = true)
        }
        if (before.revision != request.expectedRevision) {
            return AgentConfigApplyResult.Conflict(before.revision)
        }
        skillDirectory.applyFileChange(request.changes.single())?.let { return it }
        val after = runCatching { snapshot(request.agentId) }.getOrElse {
            return AgentConfigApplyResult.Failed("Skill 已变更，但无法重新读取", restored = false)
        }
        return AgentConfigApplyResult.Applied(after, backupReference = null)
    }

    override suspend fun readSkillDocument(
        agentId: String,
        skillId: String,
    ): AgentSkillDocumentReadResult {
        val discovery = discover(agentId)
        if (discovery.state != AgentConfigDiscoveryState.Ready) {
            return AgentSkillDocumentReadResult.Unavailable(discovery)
        }
        return skillDirectory.readDocument(skillId)
    }

    override suspend fun writeSkillDocument(
        request: AgentSkillDocumentWriteRequest,
    ): AgentSkillDocumentWriteResult {
        val discovery = discover(request.agentId)
        if (discovery.state != AgentConfigDiscoveryState.Ready) {
            return AgentSkillDocumentWriteResult.Unavailable(discovery)
        }
        return skillDirectory.writeDocument(request)
    }

    override fun normalizeSessionConfiguration(options: List<AgentConfigOption>): List<AgentConfigOption> =
        options.map { option ->
            if (option !is AgentConfigOption.Select || option.category != AgentConfigCategory.Model) {
                return@map option
            }
            option.copy(choices = option.choices.map(::groupProviderModelChoice))
        }

    private fun snapshot(agentId: String): AgentLiveConfigSnapshot = AgentLiveConfigSnapshot(
        agentId = agentId,
        adapterId = adapterId,
        revision = revision(),
        displayLocation = skillRoots.first(),
        skills = skillDirectory.summaries(
            activation = { AgentSkillActivation.Enabled },
            activationOperations = emptySet(),
        ),
        credentialPresence = AgentCredentialPresence.NotApplicable,
        warnings = listOf("持久 Provider 与认证由 Agent 原生 CLI 管理"),
    )

    private fun revision(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        skillRoots.forEach { digest.update(it.toByteArray()) }
        skillDirectory.revisionInputs().sortedBy { it.first }.forEach { (key, value) ->
            digest.update(key.toByteArray())
            digest.update(value.toByteArray())
        }
        return "sha256:" + digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun groupProviderModelChoice(choice: AgentConfigChoice): AgentConfigChoice {
        if (!choice.groupId.isNullOrBlank() || !choice.groupName.isNullOrBlank()) return choice
        val separator = choice.value.indexOf('/')
        if (separator <= 0 || separator == choice.value.lastIndex) return choice
        val providerId = choice.value.substring(0, separator)
        val modelId = choice.value.substring(separator + 1)
        return choice.copy(
            name = choice.name.substringAfter('/', modelId),
            groupId = providerId,
            groupName = choice.name.substringBefore('/').takeIf(String::isNotBlank) ?: providerId,
        )
    }
}
