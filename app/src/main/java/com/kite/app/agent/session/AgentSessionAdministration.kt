package com.kite.app.agent.session

import com.kite.app.agent.contract.AgentOperationResult

/** Agent 原生会话的低频管理能力；归档不属于这里。 */
interface AgentSessionAdministrationAdapter {
    val adapterId: String

    suspend fun deleteSession(sessionId: String, cwd: String): AgentOperationResult<Unit>
}

class AgentSessionAdministrationAdapterRegistry(adapters: List<AgentSessionAdministrationAdapter>) {
    private val adaptersById: Map<String, AgentSessionAdministrationAdapter>

    init {
        val grouped = adapters.groupBy(AgentSessionAdministrationAdapter::adapterId)
        require(grouped.none { (id, values) -> !STABLE_ID.matches(id) || values.size != 1 }) {
            "Agent 会话管理适配器 ID 不能为空或重复"
        }
        adaptersById = grouped.mapValues { it.value.single() }
    }

    fun adapter(adapterId: String?): AgentSessionAdministrationAdapter? =
        adapterId?.let(adaptersById::get)

    private companion object {
        val STABLE_ID = Regex("[a-z0-9](?:[a-z0-9._-]*[a-z0-9])?")
    }
}

fun interface AgentSessionCommandExecutor {
    suspend fun execute(argv: List<String>, cwd: String): AgentOperationResult<Unit>
}
