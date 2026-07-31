package com.kite.app.foundation.workspace

import com.kite.app.foundation.contracts.AgentRuntimeRecord
import com.kite.app.foundation.contracts.AgentRuntimeStatus
import com.kite.app.foundation.contracts.ManagedTerminalRecord
import com.kite.app.foundation.contracts.ManagedTerminalStatus
import com.kite.app.foundation.contracts.isLiveProcessStatus
import com.kite.app.foundation.runtime.ProotViewStore

/**
 * environmentId 与工作面 Space 之间的唯一命名规则。
 *
 * default 保留历史 `space-main`，以继承旧终端与 AI 会话；其他环境使用可逆推的稳定身份。
 * 这里只解析身份，不创建目录、不读 View catalog。
 */
internal object EnvironmentSpaceIdentity {
    const val LEGACY_DEFAULT_SPACE_ID = "space-main"

    data class Descriptor(
        val environmentId: String,
        val spaceId: String,
        val displayName: String,
    )

    fun resolve(environmentId: String): Descriptor {
        val normalized = environmentId.trim()
        require(
            normalized.isNotBlank() &&
                normalized.length <= 64 &&
                normalized.all { it.isLetterOrDigit() || it == '-' || it == '_' }
        ) { "environmentId 含不安全字符" }

        return if (normalized == ProotViewStore.DEFAULT_ENVIRONMENT_ID) {
            Descriptor(
                environmentId = normalized,
                spaceId = LEGACY_DEFAULT_SPACE_ID,
                displayName = "默认空间",
            )
        } else {
            Descriptor(
                environmentId = normalized,
                spaceId = "space-environment-$normalized",
                displayName = "环境 $normalized",
            )
        }
    }
}

internal data class EnvironmentSpaceStoppedState(
    val terminals: List<ManagedTerminalRecord>,
    val agents: List<AgentRuntimeRecord>,
)

/** 旧环境进程已由 View 守卫确认退出后，工作面记录的统一收口规则。 */
internal object EnvironmentSpaceStateTransitions {
    fun confirmStopped(
        spaceId: String,
        terminals: List<ManagedTerminalRecord>,
        agents: List<AgentRuntimeRecord>,
        stoppedAt: Long,
    ): EnvironmentSpaceStoppedState {
        return EnvironmentSpaceStoppedState(
            terminals = terminals.map { record ->
                if (
                    record.spaceId == spaceId &&
                    (record.status.isLiveProcessStatus() || record.status == ManagedTerminalStatus.FROZEN)
                ) {
                    record.copy(
                        status = ManagedTerminalStatus.STOPPED,
                        lastExitedAt = stoppedAt,
                        lastPid = null,
                    )
                } else {
                    record
                }
            },
            agents = agents.map { record ->
                if (
                    record.spaceId == spaceId &&
                    (record.status == AgentRuntimeStatus.STARTING || record.status == AgentRuntimeStatus.RUNNING)
                ) {
                    record.copy(
                        status = AgentRuntimeStatus.STOPPED,
                        pid = null,
                    )
                } else {
                    record
                }
            },
        )
    }
}
