package com.kite.app.agent.session.opencode

import com.kite.app.agent.contract.AgentOperationResult
import com.kite.app.agent.session.AgentSessionAdministrationAdapter
import com.kite.app.agent.session.AgentSessionCommandExecutor

/** OpenCode 的产品差异只在该适配器内，公共 Runtime 不拼接产品命令。 */
class OpenCodeAgentSessionAdministrationAdapter(
    private val executor: AgentSessionCommandExecutor
) : AgentSessionAdministrationAdapter {
    override val adapterId: String = ADAPTER_ID

    override suspend fun deleteSession(sessionId: String, cwd: String): AgentOperationResult<Unit> {
        val normalized = sessionId.trim()
        if (normalized.isBlank() || normalized.any(Char::isISOControl)) {
            return AgentOperationResult.Failure("OpenCode 会话 ID 无效")
        }
        return executor.execute(
            listOf("opencode", "session", "delete", normalized),
            cwd
        )
    }

    companion object {
        const val ADAPTER_ID = "opencode"
    }
}
