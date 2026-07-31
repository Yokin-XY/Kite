package com.kite.app.agent.config

import com.kite.app.agent.registration.AgentRegistration

/** 按登记中的稳定 adapterId 选择实现，不按 Agent 显示名或产品名分支。 */
class AgentConfigAdapterRegistry(adapters: List<AgentConfigAdapter>) {
    private val adaptersById: Map<String, AgentConfigAdapter>

    init {
        val grouped = adapters.groupBy { it.adapterId }
        require(grouped.none { (id, values) -> !STABLE_ID.matches(id) || values.size != 1 }) {
            "Agent 配置适配器 ID 不能为空或重复"
        }
        adaptersById = grouped.mapValues { it.value.single() }
    }

    fun adapter(adapterId: String?): AgentConfigAdapter? = adapterId?.let(adaptersById::get)

    fun adapterFor(registration: AgentRegistration): AgentConfigAdapter? =
        adapter(registration.configAdapterId)

    fun adapterIds(): Set<String> = adaptersById.keys

    private companion object {
        val STABLE_ID = Regex("[a-z0-9](?:[a-z0-9._-]*[a-z0-9])?")
    }
}
