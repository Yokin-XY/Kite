package com.kite.app.agent.sdk.configuration

import com.kite.app.agent.contract.AgentConfigOption
import com.kite.app.agent.contract.AgentConfigValue
import com.kite.app.agent.contract.AgentOperationResult
import com.kite.app.agent.runtime.AgentDraftModelSelection
import com.kite.app.agent.runtime.AgentRuntimeRegistry

data class AgentSessionControlState(
    val options: List<AgentConfigOption>,
    val catalog: AgentControlCatalog,
)

/** 模型、权限和推理强度等本轮输入草稿的统一选择入口。选择时不修改 Agent。 */
interface AgentSessionControlApi {
    fun project(options: List<AgentConfigOption>): AgentSessionControlState
    fun selectModel(
        instanceId: String,
        generation: Long,
        selection: AgentDraftModelSelection,
    ): AgentOperationResult<Unit>
    fun selectConfiguration(
        instanceId: String,
        generation: Long,
        configId: String,
        value: AgentConfigValue,
    ): AgentOperationResult<Unit>
    fun selectMode(
        instanceId: String,
        generation: Long,
        modeId: String,
    ): AgentOperationResult<Unit>
}

class RuntimeBackedAgentSessionControlApi : AgentSessionControlApi {
    override fun project(options: List<AgentConfigOption>): AgentSessionControlState =
        AgentSessionControlState(options, AgentControlCatalogProjector.project(options))

    override fun selectModel(
        instanceId: String,
        generation: Long,
        selection: AgentDraftModelSelection,
    ): AgentOperationResult<Unit> = AgentRuntimeRegistry.selectDraftModel(
        instanceId,
        generation,
        selection,
    ).withoutValue()

    override fun selectConfiguration(
        instanceId: String,
        generation: Long,
        configId: String,
        value: AgentConfigValue,
    ): AgentOperationResult<Unit> = AgentRuntimeRegistry.selectDraftConfiguration(
        instanceId,
        generation,
        configId,
        value,
    ).withoutValue()

    override fun selectMode(
        instanceId: String,
        generation: Long,
        modeId: String,
    ): AgentOperationResult<Unit> = AgentRuntimeRegistry.selectDraftMode(
        instanceId,
        generation,
        modeId,
    ).withoutValue()

    private fun <T> AgentOperationResult<T>.withoutValue(): AgentOperationResult<Unit> = when (this) {
        is AgentOperationResult.Success -> AgentOperationResult.Success(Unit)
        is AgentOperationResult.Failure -> this
        is AgentOperationResult.Unsupported -> this
    }
}
