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
import com.kite.app.agent.sdk.account.AgentAccountCapabilities
import com.kite.app.agent.sdk.account.AgentAccountCapability
import com.kite.app.agent.sdk.account.AgentAccountCredentialReadResult
import com.kite.app.agent.sdk.account.AgentAccountCredentialSnapshot
import com.kite.app.agent.sdk.account.AgentAccountCredentialWriteResult
import com.kite.app.agent.sdk.account.AgentAccountIdentity
import com.kite.app.agent.sdk.account.AgentAccountIdentityResult
import com.kite.app.agent.sdk.account.AgentOfficialAccountAdapter
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    @Test
    fun `保存并切换账号在验证失败时回滚原生凭据`() = runTest {
        val dispatcher = kotlinx.coroutines.test.StandardTestDispatcher(testScheduler)
        val vault = MemoryOfficialAccountVault()
        val adapter = FakeOfficialAccountAdapter()
        val manager = AgentOfficialAccountManager(
            scope = this,
            registry = registry(),
            commandRunner = { AgentOfficialAccountCommandResult(0, "Logged in") },
            accountAdapterResolver = { adapter },
            vault = vault,
            storageDispatcher = dispatcher,
        )
        val results = mutableListOf<AgentOfficialAccountOperationResult>()

        manager.saveCurrent(AGENT_ID, ACCOUNT_ID) { results += it }
        advanceUntilIdle()
        assertTrue(results.last() is AgentOfficialAccountOperationResult.Saved)
        assertEquals("first", vault.currentAccountId(AGENT_ID))

        adapter.useAccount("second")
        manager.saveCurrent(AGENT_ID, ACCOUNT_ID) { results += it }
        advanceUntilIdle()
        assertTrue(results.last() is AgentOfficialAccountOperationResult.Saved)
        assertEquals(2, vault.accounts(AGENT_ID).size)
        assertEquals("second", vault.currentAccountId(AGENT_ID))

        adapter.returnWrongIdentityOnce = true
        manager.switchTo(AGENT_ID, ACCOUNT_ID, "first") { results += it }
        advanceUntilIdle()

        val failure = results.last() as AgentOfficialAccountOperationResult.Failed
        assertTrue(failure.restored)
        assertEquals("second", adapter.currentAccountId())
        assertEquals("second", vault.currentAccountId(AGENT_ID))
    }

    @Test
    fun `不能删除当前账号切换后可以删除旧档案`() = runTest {
        val dispatcher = kotlinx.coroutines.test.StandardTestDispatcher(testScheduler)
        val vault = MemoryOfficialAccountVault()
        val adapter = FakeOfficialAccountAdapter()
        val manager = AgentOfficialAccountManager(
            scope = this,
            registry = registry(),
            commandRunner = { AgentOfficialAccountCommandResult(0, "Logged in") },
            accountAdapterResolver = { adapter },
            vault = vault,
            storageDispatcher = dispatcher,
        )
        adapter.useAccount("first")
        manager.saveCurrent(AGENT_ID, ACCOUNT_ID)
        advanceUntilIdle()
        adapter.useAccount("second")
        manager.saveCurrent(AGENT_ID, ACCOUNT_ID)
        advanceUntilIdle()

        val results = mutableListOf<AgentOfficialAccountOperationResult>()
        manager.deleteSaved(AGENT_ID, ACCOUNT_ID, "second") { results += it }
        advanceUntilIdle()
        assertTrue(results.last() is AgentOfficialAccountOperationResult.Failed)

        manager.switchTo(AGENT_ID, ACCOUNT_ID, "first") { results += it }
        advanceUntilIdle()
        manager.deleteSaved(AGENT_ID, ACCOUNT_ID, "second") { results += it }
        advanceUntilIdle()
        assertEquals(AgentOfficialAccountOperationResult.Deleted, results.last())
        assertEquals(listOf("first"), vault.accounts(AGENT_ID).map { it.accountId })
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

private class MemoryOfficialAccountVault : AgentOfficialAccountVault {
    private val records = linkedMapOf<Pair<String, String>, Pair<AgentSavedOfficialAccount, ByteArray>>()
    private val currentIds = mutableMapOf<String, String>()

    override fun accounts(agentId: String): List<AgentSavedOfficialAccount> = records.values
        .map { it.first }
        .filter { it.agentId == agentId }
        .sortedByDescending(AgentSavedOfficialAccount::lastUsedAt)

    override fun account(agentId: String, accountId: String): AgentSavedOfficialAccount? =
        records[agentId to accountId]?.first

    override fun currentAccountId(agentId: String): String? = currentIds[agentId]

    override fun save(account: AgentSavedOfficialAccount, credential: AgentAccountCredentialSnapshot) {
        records[account.agentId to account.accountId] = account to credential.bytes.copyOf()
    }

    override fun credential(agentId: String, accountId: String): AgentAccountCredentialSnapshot? =
        records[agentId to accountId]?.second?.let { AgentAccountCredentialSnapshot(it.copyOf()) }

    override fun markCurrent(agentId: String, accountId: String) {
        currentIds[agentId] = accountId
    }

    override fun remove(agentId: String, accountId: String) {
        records.remove(agentId to accountId)
    }
}

private class FakeOfficialAccountAdapter : AgentOfficialAccountAdapter {
    override val adapterId: String = "fake"
    private var bytes = "native:first".toByteArray()
    var returnWrongIdentityOnce: Boolean = false

    override fun accountCapabilities(): AgentAccountCapabilities = AgentAccountCapabilities(
        setOf(
            AgentAccountCapability.SaveCurrent,
            AgentAccountCapability.Switch,
            AgentAccountCapability.Delete,
            AgentAccountCapability.StableId,
        )
    )

    override suspend fun currentIdentity(agentId: String): AgentAccountIdentityResult {
        val actual = bytes.toString(Charsets.UTF_8).removePrefix("native:")
        if (returnWrongIdentityOnce) {
            returnWrongIdentityOnce = false
            return AgentAccountIdentityResult.Ready(AgentAccountIdentity("unexpected", "Unexpected"))
        }
        return AgentAccountIdentityResult.Ready(AgentAccountIdentity(actual, "Official $actual"))
    }

    override suspend fun captureCurrent(agentId: String): AgentAccountCredentialReadResult =
        AgentAccountCredentialReadResult.Ready(AgentAccountCredentialSnapshot(bytes.copyOf()))

    override suspend fun restoreCurrent(
        agentId: String,
        snapshot: AgentAccountCredentialSnapshot,
    ): AgentAccountCredentialWriteResult {
        bytes = snapshot.bytes.copyOf()
        return AgentAccountCredentialWriteResult.Applied
    }

    fun useAccount(accountId: String) {
        bytes = "native:$accountId".toByteArray()
    }

    fun currentAccountId(): String = bytes.toString(Charsets.UTF_8).removePrefix("native:")
}
