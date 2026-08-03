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
import com.kite.app.agent.contract.AgentConfigCategory
import com.kite.app.agent.contract.AgentConfigChoice
import com.kite.app.agent.contract.AgentConfigOption

/**
 * 只补充原生会话协议语义、不接管 Agent 持久配置的轻量 Adapter。
 *
 * Provider、模型和会话状态仍由 Agent 自己通过 ACP 公布；这层只负责把已核验的原生值
 * 投影成 Kite 统一语义，避免为了接一个协议选择器而伪造第二份配置事实。
 */
internal abstract class ProtocolOnlyAgentConfigAdapter(
    final override val adapterId: String,
) : AgentConfigAdapter {
    override fun capabilities(): AgentConfigCapabilities = AgentConfigCapabilities(
        supported = emptySet(),
        credentialOwnership = AgentCredentialOwnership.Unsupported,
    )

    override suspend fun discover(agentId: String): AgentConfigDiscovery = unavailable(agentId)

    override suspend fun readLive(agentId: String): AgentConfigReadResult =
        AgentConfigReadResult.Unavailable(unavailable(agentId))

    override fun validate(request: AgentConfigApplyRequest): List<AgentConfigValidationProblem> =
        request.changes.mapIndexed { index, _ ->
            AgentConfigValidationProblem(
                field = "changes[$index]",
                message = "当前 Agent 的持久配置由原生 CLI 管理",
            )
        }

    override suspend fun apply(request: AgentConfigApplyRequest): AgentConfigApplyResult =
        AgentConfigApplyResult.Unavailable(unavailable(request.agentId))

    override fun normalizeSessionConfiguration(options: List<AgentConfigOption>): List<AgentConfigOption> =
        options.map { option ->
            if (option !is AgentConfigOption.Select || option.category != AgentConfigCategory.Model) {
                return@map option
            }
            option.copy(choices = option.choices.map(::groupProviderModelChoice))
        }

    private fun unavailable(agentId: String) = AgentConfigDiscovery(
        agentId = agentId,
        adapterId = adapterId,
        state = AgentConfigDiscoveryState.Unsupported,
        writable = false,
        warnings = listOf("持久 Provider 与认证由 Agent 原生 CLI 管理；当前会话能力从 ACP 读取"),
    )

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
