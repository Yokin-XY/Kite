package com.kite.app.foundation.workspace

import com.kite.app.foundation.contracts.SpaceRecord
import com.kite.app.foundation.contracts.AgentKind
import com.kite.app.foundation.contracts.AgentRuntimeRecord
import com.kite.app.foundation.contracts.AgentRuntimeStatus
import com.kite.app.foundation.contracts.ManagedTerminalKind
import com.kite.app.foundation.contracts.ManagedTerminalRecord
import com.kite.app.foundation.contracts.ManagedTerminalStatus
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class EnvironmentSpaceIdentityTest {

    @Test
    fun `legacy space record belongs only to default environment`() {
        val record = SpaceRecord.fromJson(
            JSONObject()
                .put("id", "space-main")
                .put("displayName", "默认空间")
                .put("containerId", "ubuntu-main")
                .put("workspacePath", "/workspace")
                .put("createdAt", 1L)
        )

        assertEquals("default", record.environmentId)
        assertEquals("default", SpaceRecord.fromJson(record.toJson()).environmentId)
    }

    @Test
    fun `default preserves legacy space identity`() {
        val descriptor = EnvironmentSpaceIdentity.resolve("default")

        assertEquals("space-main", descriptor.spaceId)
        assertEquals("默认空间", descriptor.displayName)
    }

    @Test
    fun `non default environment receives stable independent space identity`() {
        val first = EnvironmentSpaceIdentity.resolve("profile_2")
        val second = EnvironmentSpaceIdentity.resolve("profile_2")

        assertEquals(first, second)
        assertEquals("space-environment-profile_2", first.spaceId)
        assertEquals("profile_2", first.environmentId)
    }

    @Test
    fun `unsafe environment identity is rejected instead of sharing default space`() {
        try {
            EnvironmentSpaceIdentity.resolve("../default")
            fail("应拒绝不安全的 environmentId")
        } catch (_: IllegalArgumentException) {
            // expected
        }
    }

    @Test
    fun `confirmed environment stop only closes matching space activity`() {
        val oldTerminal = terminal("old", "space-main", ManagedTerminalStatus.RUNNING, 101)
        val targetTerminal = terminal("target", "space-environment-profile_2", ManagedTerminalStatus.RUNNING, 202)
        val registered = terminal("draft", "space-main", ManagedTerminalStatus.REGISTERED, null)
        val oldAgent = agent("agent-old", "space-main", AgentRuntimeStatus.RUNNING, 303)
        val targetAgent = agent(
            "agent-target",
            "space-environment-profile_2",
            AgentRuntimeStatus.RUNNING,
            404,
        )

        val result = EnvironmentSpaceStateTransitions.confirmStopped(
            spaceId = "space-main",
            terminals = listOf(oldTerminal, targetTerminal, registered),
            agents = listOf(oldAgent, targetAgent),
            stoppedAt = 999L,
        )

        assertEquals(ManagedTerminalStatus.STOPPED, result.terminals[0].status)
        assertEquals(null, result.terminals[0].lastPid)
        assertEquals(999L, result.terminals[0].lastExitedAt)
        assertEquals(targetTerminal, result.terminals[1])
        assertEquals(registered, result.terminals[2])
        assertEquals(AgentRuntimeStatus.STOPPED, result.agents[0].status)
        assertEquals(null, result.agents[0].pid)
        assertEquals(targetAgent, result.agents[1])
    }

    private fun terminal(
        id: String,
        spaceId: String,
        status: ManagedTerminalStatus,
        pid: Int?,
    ) = ManagedTerminalRecord(
        id = id,
        spaceId = spaceId,
        title = id,
        kind = ManagedTerminalKind.SHELL,
        createdAt = 1L,
        lastPid = pid,
        status = status,
    )

    private fun agent(
        id: String,
        spaceId: String,
        status: AgentRuntimeStatus,
        pid: Int?,
    ) = AgentRuntimeRecord(
        id = id,
        spaceId = spaceId,
        agentKind = AgentKind.CUSTOM,
        displayName = id,
        workingDirectory = "/workspace",
        launchCommand = id,
        createdAt = 1L,
        status = status,
        pid = pid,
    )
}
