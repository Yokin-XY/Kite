package com.kite.app.agent.sdk.configuration

import com.kite.app.agent.contract.AgentConfigOption
import com.kite.app.agent.contract.AgentConfigValue
import com.kite.app.agent.contract.AgentOperationResult
import com.kite.app.agent.runtime.AgentRuntimeRegistry

data class AgentSessionControlState(
    val options: List<AgentConfigOption>,
    val catalog: AgentControlCatalog,
)

/** 当前会话三个固定组件的唯一写入口。 */
interface AgentSessionControlApi {
    fun project(options: List<AgentConfigOption>): AgentSessionControlState
    suspend fun apply(
        instanceId: String,
        generation: Long,
        configId: String,
        value: AgentConfigValue,
    ): AgentOperationResult<AgentSessionControlState>
}

class RuntimeBackedAgentSessionControlApi : AgentSessionControlApi {
    override fun project(options: List<AgentConfigOption>): AgentSessionControlState =
        AgentSessionControlState(options, AgentControlCatalogProjector.project(options))

    override suspend fun apply(
        instanceId: String,
        generation: Long,
        configId: String,
        value: AgentConfigValue,
    ): AgentOperationResult<AgentSessionControlState> = when (
        val result = AgentRuntimeRegistry.setConfiguration(instanceId, generation, configId, value)
    ) {
        is AgentOperationResult.Success -> AgentOperationResult.Success(project(result.value))
        is AgentOperationResult.Failure -> result
        is AgentOperationResult.Unsupported -> result
    }
}
