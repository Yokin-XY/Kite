package com.kite.app.agent.auth

import com.kite.app.agent.registration.AgentOfficialAccountCommand
import com.kite.app.agent.registration.AgentOfficialAccountSpec
import com.kite.app.agent.registration.KiteAgentRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AgentOfficialAccountKey(
    val agentId: String,
    val accountId: String,
)

enum class AgentOfficialAccountStatus {
    Unknown,
    Unverified,
    Checking,
    LoggedOut,
    LoggedIn,
    SigningIn,
    CancellingLogin,
    SigningOut,
    Failed,
}

data class AgentOfficialAccountState(
    val status: AgentOfficialAccountStatus = AgentOfficialAccountStatus.Unknown,
    val message: String? = null,
)

data class AgentOfficialAccountCommandResult(
    val exitCode: Int,
    val output: String,
)

fun interface AgentOfficialAccountCommandRunner {
    suspend fun run(command: AgentOfficialAccountCommand): AgentOfficialAccountCommandResult
}

/**
 * 官方账号状态的进程级协调器。
 *
 * 账号凭据始终由 Agent 原生 CLI 保存；本类只运行资源声明的动作并投影状态。
 */
class AgentOfficialAccountManager(
    private val scope: CoroutineScope,
    private val registry: KiteAgentRegistry,
    private val commandRunner: AgentOfficialAccountCommandRunner,
) {
    private val mutableStates = MutableStateFlow<Map<AgentOfficialAccountKey, AgentOfficialAccountState>>(emptyMap())
    private val activeJobs = mutableMapOf<AgentOfficialAccountKey, Job>()

    val states: StateFlow<Map<AgentOfficialAccountKey, AgentOfficialAccountState>> = mutableStates.asStateFlow()

    fun accounts(agentId: String): List<AgentOfficialAccountSpec> =
        registry.snapshot().entry(agentId)?.registration?.officialAccounts.orEmpty()

    fun state(agentId: String, accountId: String): AgentOfficialAccountState =
        states.value[AgentOfficialAccountKey(agentId, accountId)] ?: AgentOfficialAccountState()

    fun refresh(agentId: String, accountId: String) {
        val account = account(agentId, accountId) ?: return
        val statusCommand = account.status ?: run {
            update(
                AgentOfficialAccountKey(agentId, account.id),
                AgentOfficialAccountState(AgentOfficialAccountStatus.Unverified),
            )
            return
        }
        launch(account, agentId, AgentOfficialAccountStatus.Checking) {
            val result = commandRunner.run(statusCommand)
            when (AgentOfficialAccountResultPolicy.resolveStatus(statusCommand, result)) {
                true -> AgentOfficialAccountState(AgentOfficialAccountStatus.LoggedIn)
                false -> AgentOfficialAccountState(AgentOfficialAccountStatus.LoggedOut)
                null -> AgentOfficialAccountState(
                    status = AgentOfficialAccountStatus.Failed,
                    message = result.output.takeLast(MAX_MESSAGE_LENGTH).ifBlank { "无法确认登录状态" },
                )
            }
        }
    }

    fun login(agentId: String, accountId: String) {
        val account = account(agentId, accountId) ?: return
        launch(account, agentId, AgentOfficialAccountStatus.SigningIn) {
            val result = commandRunner.run(account.login)
            if (AgentOfficialAccountResultPolicy.succeeded(account.login, result)) {
                AgentOfficialAccountState(AgentOfficialAccountStatus.LoggedIn)
            } else {
                AgentOfficialAccountState(
                    AgentOfficialAccountStatus.Failed,
                    result.output.takeLast(MAX_MESSAGE_LENGTH).ifBlank { "登录未完成" },
                )
            }
        }
    }

    fun logout(agentId: String, accountId: String) {
        val account = account(agentId, accountId) ?: return
        val logout = account.logout ?: return
        launch(account, agentId, AgentOfficialAccountStatus.SigningOut) {
            val result = commandRunner.run(logout)
            if (AgentOfficialAccountResultPolicy.succeeded(logout, result)) {
                AgentOfficialAccountState(AgentOfficialAccountStatus.LoggedOut)
            } else {
                AgentOfficialAccountState(
                    AgentOfficialAccountStatus.Failed,
                    result.output.takeLast(MAX_MESSAGE_LENGTH).ifBlank { "退出未完成" },
                )
            }
        }
    }

    /** 取消仍在等待浏览器确认的登录，并等待底层登录进程确实退出。 */
    fun cancelLogin(agentId: String, accountId: String) {
        val key = AgentOfficialAccountKey(agentId, accountId)
        val job = synchronized(activeJobs) {
            if (state(agentId, accountId).status != AgentOfficialAccountStatus.SigningIn) return
            activeJobs[key]?.takeIf(Job::isActive)
        } ?: return
        update(key, AgentOfficialAccountState(AgentOfficialAccountStatus.CancellingLogin))
        scope.launch {
            job.cancelAndJoin()
            synchronized(activeJobs) {
                if (activeJobs[key] === job) activeJobs.remove(key)
            }
            update(key, AgentOfficialAccountState(AgentOfficialAccountStatus.LoggedOut))
        }
    }

    private fun launch(
        account: AgentOfficialAccountSpec,
        agentId: String,
        pending: AgentOfficialAccountStatus,
        block: suspend () -> AgentOfficialAccountState,
    ) {
        val key = AgentOfficialAccountKey(agentId, account.id)
        synchronized(activeJobs) {
            if (activeJobs[key]?.isActive == true) return
            update(key, AgentOfficialAccountState(pending))
            val job = scope.launch(start = CoroutineStart.LAZY) {
                try {
                    update(key, block())
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Throwable) {
                    update(
                        key,
                        AgentOfficialAccountState(
                            AgentOfficialAccountStatus.Failed,
                            error.message?.take(MAX_MESSAGE_LENGTH) ?: "官方账号操作失败",
                        ),
                    )
                } finally {
                    val currentJob = currentCoroutineContext()[Job]
                    synchronized(activeJobs) {
                        if (activeJobs[key] === currentJob) activeJobs.remove(key)
                    }
                }
            }
            activeJobs[key] = job
            job.start()
        }
    }

    private fun update(key: AgentOfficialAccountKey, state: AgentOfficialAccountState) {
        mutableStates.update { current -> current + (key to state) }
    }

    private fun account(agentId: String, accountId: String): AgentOfficialAccountSpec? =
        accounts(agentId).firstOrNull { it.id == accountId }

    private companion object {
        const val MAX_MESSAGE_LENGTH = 240
    }
}

/** 统一解释不同 Agent CLI 的账号动作结果，先匹配退出态以避免“未登录”误判为“已登录”。 */
internal object AgentOfficialAccountResultPolicy {
    fun resolveStatus(
        command: AgentOfficialAccountCommand,
        result: AgentOfficialAccountCommandResult,
    ): Boolean? {
        val output = result.output.normalizedForMatch()
        if (command.loggedOutPatterns.any { output.contains(it.normalizedForMatch()) }) return false
        if (command.loggedInPatterns.any { output.contains(it.normalizedForMatch()) }) return true
        if (command.loggedInPatterns.isEmpty() && command.loggedOutPatterns.isEmpty()) {
            return result.exitCode == 0
        }
        return null
    }

    fun succeeded(
        command: AgentOfficialAccountCommand,
        result: AgentOfficialAccountCommandResult,
    ): Boolean {
        if (result.exitCode != 0) return false
        if (command.successPatterns.isEmpty()) return true
        val normalized = result.output.normalizedForMatch()
        return command.successPatterns.any { normalized.contains(it.normalizedForMatch()) }
    }

    private fun String.normalizedForMatch(): String =
        ANSI_ESCAPE_REGEX.replace(this, "").lowercase().filterNot(Char::isWhitespace)

    private val ANSI_ESCAPE_REGEX = Regex("\\u001B\\[[;\\d]*[ -/]*[@-~]")
}
