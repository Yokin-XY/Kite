package com.kite.app.agent.auth

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.kite.app.agent.registration.AgentDefinition
import com.kite.app.agent.registration.AgentLaunchSpec
import com.kite.app.agent.registration.AgentOfficialAccountCommand
import com.kite.app.agent.registration.AgentOfficialAccountSpec
import com.kite.app.agent.registration.AgentRegistration
import com.kite.app.agent.registration.AgentRegistrationSource
import com.kite.app.agent.registration.KiteAgentRegistry
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class AgentOfficialAccountManagerTest {
    @Test
    fun `取消登录不会伪装成失败并回到未登录`() = runTest {
        val started = CompletableDeferred<Unit>()
        val cancelled = CompletableDeferred<Unit>()
        val manager = AgentOfficialAccountManager(
            scope = this,
            registry = registry(),
            commandRunner = {
                started.complete(Unit)
                try {
                    awaitCancellation()
                } finally {
                    cancelled.complete(Unit)
                }
            },
        )

        manager.login(AGENT_ID, ACCOUNT_ID)
        runCurrent()
        started.await()
        assertEquals(AgentOfficialAccountStatus.SigningIn, manager.state(AGENT_ID, ACCOUNT_ID).status)

        manager.cancelLogin(AGENT_ID, ACCOUNT_ID)
        assertEquals(
            AgentOfficialAccountStatus.CancellingLogin,
            manager.state(AGENT_ID, ACCOUNT_ID).status,
        )
        advanceUntilIdle()
        cancelled.await()

        assertEquals(AgentOfficialAccountStatus.LoggedOut, manager.state(AGENT_ID, ACCOUNT_ID).status)
    }

    @Test
    fun `没有状态命令的官方账号仍可登录且刷新不猜测凭据`() = runTest {
        var executions = 0
        val manager = AgentOfficialAccountManager(
            scope = this,
            registry = registry(),
            commandRunner = {
                executions += 1
                AgentOfficialAccountCommandResult(0, "Logged in")
            },
        )

        manager.refresh(AGENT_ID, ACCOUNT_ID)
        advanceUntilIdle()
        assertEquals(0, executions)
        assertEquals(AgentOfficialAccountStatus.Unverified, manager.state(AGENT_ID, ACCOUNT_ID).status)

        manager.login(AGENT_ID, ACCOUNT_ID)
        advanceUntilIdle()
        assertEquals(1, executions)
        assertEquals(AgentOfficialAccountStatus.LoggedIn, manager.state(AGENT_ID, ACCOUNT_ID).status)
    }

    private fun registry(): KiteAgentRegistry {
        val context = ApplicationProvider.getApplicationContext<Context>()
        return KiteAgentRegistry(
            context = context,
            resourceRegistrationSource = {
                listOf(
                    AgentRegistration(
                        definition = AgentDefinition(AGENT_ID, "Kimi Code"),
                        source = AgentRegistrationSource.Custom,
                        launch = AgentLaunchSpec.Managed(
                            providerId = AGENT_ID,
                            protocol = "acp",
                            transport = "stdio",
                            argv = listOf("kimi", "acp"),
                        ),
                        officialAccounts = listOf(
                            AgentOfficialAccountSpec(
                                id = ACCOUNT_ID,
                                displayName = "Kimi 官方",
                                login = AgentOfficialAccountCommand(listOf("kimi", "login")),
                            ),
                        ),
                    ),
                )
            },
        )
    }

    private companion object {
        const val AGENT_ID = "kimi"
        const val ACCOUNT_ID = "moonshot"
    }
}
