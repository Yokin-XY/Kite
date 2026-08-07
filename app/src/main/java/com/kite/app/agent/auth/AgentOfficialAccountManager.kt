package com.kite.app.agent.auth

import com.kite.app.agent.registration.AgentOfficialAccountCommand
import com.kite.app.agent.registration.AgentOfficialAccountSpec
import com.kite.app.agent.registration.KiteAgentRegistry
import com.kite.app.agent.sdk.account.AgentAccountCapability
import com.kite.app.agent.sdk.account.AgentAccountCredentialReadResult
import com.kite.app.agent.sdk.account.AgentAccountCredentialWriteResult
import com.kite.app.agent.sdk.account.AgentAccountIdentityResult
import com.kite.app.agent.sdk.account.AgentOfficialAccountAdapter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
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
    Saving,
    Switching,
    Deleting,
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

internal sealed interface AgentOfficialAccountOperationResult {
    data class Saved(val account: AgentSavedOfficialAccount) : AgentOfficialAccountOperationResult
    data class Switched(val account: AgentSavedOfficialAccount) : AgentOfficialAccountOperationResult
    data object Deleted : AgentOfficialAccountOperationResult
    data class Unsupported(val message: String) : AgentOfficialAccountOperationResult
    data class Failed(val message: String, val restored: Boolean = false) : AgentOfficialAccountOperationResult
}

fun interface AgentOfficialAccountCommandRunner {
    suspend fun run(command: AgentOfficialAccountCommand): AgentOfficialAccountCommandResult
}

/**
 * 官方账号状态的进程级协调器。
 *
 * 账号凭据始终由 Agent 原生 CLI 保存；本类只运行资源声明的动作并投影状态。
 */
internal class AgentOfficialAccountManager(
    private val scope: CoroutineScope,
    private val registry: KiteAgentRegistry,
    private val commandRunner: AgentOfficialAccountCommandRunner,
    private val accountAdapterResolver: ((String) -> AgentOfficialAccountAdapter?)? = null,
    private val vault: AgentOfficialAccountVault? = null,
    private val storageDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val mutableStates = MutableStateFlow<Map<AgentOfficialAccountKey, AgentOfficialAccountState>>(emptyMap())
    private val mutableSavedAccounts = MutableStateFlow<Map<String, List<AgentSavedOfficialAccount>>>(emptyMap())
    private val mutableCurrentAccountIds = MutableStateFlow<Map<String, String?>>(emptyMap())
    private val activeJobs = mutableMapOf<AgentOfficialAccountKey, Job>()
    private val loadedAgentIds = mutableSetOf<String>()

    val states: StateFlow<Map<AgentOfficialAccountKey, AgentOfficialAccountState>> = mutableStates.asStateFlow()
    val savedAccounts: StateFlow<Map<String, List<AgentSavedOfficialAccount>>> =
        mutableSavedAccounts.asStateFlow()
    val currentAccountIds: StateFlow<Map<String, String?>> = mutableCurrentAccountIds.asStateFlow()

    fun accounts(agentId: String): List<AgentOfficialAccountSpec> =
        registry.snapshot().entry(agentId)?.registration?.officialAccounts.orEmpty()

    fun state(agentId: String, accountId: String): AgentOfficialAccountState =
        states.value[AgentOfficialAccountKey(agentId, accountId)] ?: AgentOfficialAccountState()

    fun savedAccounts(agentId: String): List<AgentSavedOfficialAccount> =
        savedAccounts.value[agentId].orEmpty()

    fun currentSavedAccountId(agentId: String): String? =
        mutableCurrentAccountIds.value[agentId]

    fun loadSavedAccounts(agentId: String) {
        if (vault == null) return
        synchronized(loadedAgentIds) {
            if (!loadedAgentIds.add(agentId)) return
        }
        scope.launch {
            val accounts = withContext(storageDispatcher) { vault.accounts(agentId) }
            val currentId = withContext(storageDispatcher) { vault.currentAccountId(agentId) }
            mutableSavedAccounts.update { current -> current + (agentId to accounts) }
            mutableCurrentAccountIds.update { current -> current + (agentId to currentId) }
        }
    }

    fun accountCapabilities(agentId: String) =
        accountAdapterResolver?.invoke(agentId)?.accountCapabilities()

    fun saveCurrent(
        agentId: String,
        accountId: String,
        onResult: (AgentOfficialAccountOperationResult) -> Unit = {},
    ) {
        val account = account(agentId, accountId) ?: return
        launch(account, agentId, AgentOfficialAccountStatus.Saving) {
            val result = saveCurrentAccount(agentId, account)
            onResult(result)
            when (result) {
                is AgentOfficialAccountOperationResult.Saved -> AgentOfficialAccountState(
                    AgentOfficialAccountStatus.LoggedIn,
                    "已保存当前账号",
                )
                is AgentOfficialAccountOperationResult.Unsupported -> AgentOfficialAccountState(
                    AgentOfficialAccountStatus.Failed,
                    result.message,
                )
                is AgentOfficialAccountOperationResult.Failed -> AgentOfficialAccountState(
                    AgentOfficialAccountStatus.Failed,
                    result.message,
                )
                else -> AgentOfficialAccountState(AgentOfficialAccountStatus.Failed, "账号操作未完成")
            }
        }
    }

    fun switchTo(
        agentId: String,
        loginAccountId: String,
        savedAccountId: String,
        onResult: (AgentOfficialAccountOperationResult) -> Unit = {},
    ) {
        val account = account(agentId, loginAccountId) ?: return
        launch(account, agentId, AgentOfficialAccountStatus.Switching) {
            val result = switchSavedAccount(agentId, savedAccountId)
            onResult(result)
            when (result) {
                is AgentOfficialAccountOperationResult.Switched -> AgentOfficialAccountState(
                    AgentOfficialAccountStatus.LoggedIn,
                    "已切换账号",
                )
                is AgentOfficialAccountOperationResult.Unsupported -> AgentOfficialAccountState(
                    AgentOfficialAccountStatus.Failed,
                    result.message,
                )
                is AgentOfficialAccountOperationResult.Failed -> AgentOfficialAccountState(
                    AgentOfficialAccountStatus.Failed,
                    result.message,
                )
                else -> AgentOfficialAccountState(AgentOfficialAccountStatus.Failed, "账号操作未完成")
            }
        }
    }

    fun deleteSaved(
        agentId: String,
        loginAccountId: String,
        savedAccountId: String,
        onResult: (AgentOfficialAccountOperationResult) -> Unit = {},
    ) {
        val account = account(agentId, loginAccountId) ?: return
        launch(account, agentId, AgentOfficialAccountStatus.Deleting) {
            val result = deleteSavedAccount(agentId, savedAccountId)
            onResult(result)
            when (result) {
                AgentOfficialAccountOperationResult.Deleted -> AgentOfficialAccountState(
                    AgentOfficialAccountStatus.LoggedIn,
                    "已删除账号档案",
                )
                is AgentOfficialAccountOperationResult.Unsupported -> AgentOfficialAccountState(
                    AgentOfficialAccountStatus.Failed,
                    result.message,
                )
                is AgentOfficialAccountOperationResult.Failed -> AgentOfficialAccountState(
                    AgentOfficialAccountStatus.Failed,
                    result.message,
                )
                else -> AgentOfficialAccountState(AgentOfficialAccountStatus.Failed, "账号操作未完成")
            }
        }
    }

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
                    message = "无法确认登录状态",
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
                    "登录未完成",
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
                    "退出未完成",
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
                            "官方账号操作失败",
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

    private suspend fun saveCurrentAccount(
        agentId: String,
        loginAccount: AgentOfficialAccountSpec,
    ): AgentOfficialAccountOperationResult {
        val vault = vault ?: return AgentOfficialAccountOperationResult.Unsupported("当前版本未启用账号安全存储")
        val adapter = accountAdapter(agentId)
            ?: return AgentOfficialAccountOperationResult.Unsupported("当前 Agent 未声明官方账号保存能力")
        val capabilities = adapter.accountCapabilities()
        if (!capabilities.supports(AgentAccountCapability.SaveCurrent) ||
            !capabilities.supports(AgentAccountCapability.StableId)
        ) {
            return AgentOfficialAccountOperationResult.Unsupported("当前 Agent 不支持保存官方账号")
        }
        val identity = when (val result = adapter.currentIdentity(agentId)) {
            is AgentAccountIdentityResult.Ready -> result.identity
            is AgentAccountIdentityResult.Unavailable ->
                return AgentOfficialAccountOperationResult.Failed(result.message)
            is AgentAccountIdentityResult.Failed ->
                return AgentOfficialAccountOperationResult.Failed(result.message)
        }
        val credential = when (val result = adapter.captureCurrent(agentId)) {
            is AgentAccountCredentialReadResult.Ready -> result.snapshot
            is AgentAccountCredentialReadResult.Missing ->
                return AgentOfficialAccountOperationResult.Failed(result.message)
            is AgentAccountCredentialReadResult.Unavailable ->
                return AgentOfficialAccountOperationResult.Failed(result.message)
            is AgentAccountCredentialReadResult.Failed ->
                return AgentOfficialAccountOperationResult.Failed(result.message)
        }
        val existing = savedAccounts(agentId).firstOrNull { it.accountId == identity.accountId }
            ?: withContext(storageDispatcher) { vault.account(agentId, identity.accountId) }
        val now = System.currentTimeMillis()
        val saved = AgentSavedOfficialAccount(
            agentId = agentId,
            accountId = identity.accountId,
            displayName = identity.displayName.ifBlank { loginAccount.displayName },
            createdAt = existing?.createdAt ?: now,
            lastUsedAt = now,
        )
        withContext(storageDispatcher) {
            vault.save(saved, credential)
            vault.markCurrent(agentId, identity.accountId)
        }
        updateSavedCache(agentId)
        return AgentOfficialAccountOperationResult.Saved(saved)
    }

    private suspend fun switchSavedAccount(
        agentId: String,
        targetAccountId: String,
    ): AgentOfficialAccountOperationResult {
        val vault = vault ?: return AgentOfficialAccountOperationResult.Unsupported("当前版本未启用账号安全存储")
        val adapter = accountAdapter(agentId)
            ?: return AgentOfficialAccountOperationResult.Unsupported("当前 Agent 未声明官方账号切换能力")
        val capabilities = adapter.accountCapabilities()
        if (!capabilities.supports(AgentAccountCapability.Switch) ||
            !capabilities.supports(AgentAccountCapability.StableId)
        ) {
            return AgentOfficialAccountOperationResult.Unsupported("当前 Agent 不支持切换官方账号")
        }
        val target = withContext(storageDispatcher) { vault.account(agentId, targetAccountId) }
            ?: return AgentOfficialAccountOperationResult.Failed("目标账号档案不存在")
        val targetCredential = withContext(storageDispatcher) {
            vault.credential(agentId, targetAccountId)
        } ?: return AgentOfficialAccountOperationResult.Failed("目标账号凭据不可用")
        val before = when (val result = adapter.captureCurrent(agentId)) {
            is AgentAccountCredentialReadResult.Ready -> result.snapshot
            is AgentAccountCredentialReadResult.Missing ->
                return AgentOfficialAccountOperationResult.Failed("切换前无法取得当前凭据，已停止切换")
            is AgentAccountCredentialReadResult.Unavailable ->
                return AgentOfficialAccountOperationResult.Failed(result.message)
            is AgentAccountCredentialReadResult.Failed ->
                return AgentOfficialAccountOperationResult.Failed(result.message)
        }
        var nativeChanged = false
        return try {
            when (val written = adapter.restoreCurrent(agentId, targetCredential)) {
                AgentAccountCredentialWriteResult.Applied -> nativeChanged = true
                is AgentAccountCredentialWriteResult.Unavailable ->
                    return AgentOfficialAccountOperationResult.Failed(written.message)
                is AgentAccountCredentialWriteResult.Failed ->
                    return AgentOfficialAccountOperationResult.Failed(written.message, written.restored)
            }
            val verified = when (val identity = adapter.currentIdentity(agentId)) {
                is AgentAccountIdentityResult.Ready -> identity.identity
                is AgentAccountIdentityResult.Unavailable -> error(identity.message)
                is AgentAccountIdentityResult.Failed -> error(identity.message)
            }
            if (verified.accountId != targetAccountId) {
                error("官方状态检查未确认目标账号")
            }
            withContext(storageDispatcher) { vault.markCurrent(agentId, targetAccountId) }
            updateSavedCache(agentId)
            AgentOfficialAccountOperationResult.Switched(target)
        } catch (error: CancellationException) {
            if (nativeChanged) {
                withContext(NonCancellable) {
                    runCatching { adapter.restoreCurrent(agentId, before) }
                }
            }
            throw error
        } catch (_: Throwable) {
            val restored = if (nativeChanged) {
                runCatching { adapter.restoreCurrent(agentId, before) }
                    .getOrNull() is AgentAccountCredentialWriteResult.Applied
            } else {
                true
            }
            AgentOfficialAccountOperationResult.Failed(
                message = if (restored) "账号切换验证失败" else "账号切换失败",
                restored = restored,
            )
        }
    }

    private suspend fun deleteSavedAccount(
        agentId: String,
        targetAccountId: String,
    ): AgentOfficialAccountOperationResult {
        val vault = vault ?: return AgentOfficialAccountOperationResult.Unsupported("当前版本未启用账号安全存储")
        val adapter = accountAdapter(agentId)
            ?: return AgentOfficialAccountOperationResult.Unsupported("当前 Agent 未声明官方账号删除能力")
        val current = withContext(storageDispatcher) { vault.currentAccountId(agentId) }
        if (current == targetAccountId) {
            return AgentOfficialAccountOperationResult.Failed("当前账号不能直接删除，请先切换到其他账号")
        }
        val capabilities = adapter.accountCapabilities()
        if (!capabilities.supports(AgentAccountCapability.Delete) ||
            !capabilities.supports(AgentAccountCapability.StableId)
        ) {
            return AgentOfficialAccountOperationResult.Unsupported("当前 Agent 未声明账号档案管理能力")
        }
        val existing = withContext(storageDispatcher) { vault.account(agentId, targetAccountId) }
            ?: return AgentOfficialAccountOperationResult.Failed("账号档案不存在")
        withContext(storageDispatcher) { vault.remove(existing.agentId, existing.accountId) }
        updateSavedCache(agentId)
        return AgentOfficialAccountOperationResult.Deleted
    }

    private fun updateSavedCache(agentId: String) {
        val vault = vault ?: return
        scope.launch {
            val accounts = withContext(storageDispatcher) { vault.accounts(agentId) }
            val currentId = withContext(storageDispatcher) { vault.currentAccountId(agentId) }
            mutableSavedAccounts.update { current -> current + (agentId to accounts) }
            mutableCurrentAccountIds.update { current -> current + (agentId to currentId) }
        }
    }

    private fun accountAdapter(agentId: String): AgentOfficialAccountAdapter? =
        accountAdapterResolver?.invoke(agentId)

    private fun account(agentId: String, accountId: String): AgentOfficialAccountSpec? =
        accounts(agentId).firstOrNull { it.id == accountId }

    private companion object {
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
