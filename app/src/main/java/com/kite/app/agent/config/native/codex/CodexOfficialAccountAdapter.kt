package com.kite.app.agent.config.native

import com.kite.app.agent.config.AtomicConfigFileStore
import com.kite.app.agent.config.AtomicConfigFileWriteResult
import com.kite.app.agent.config.ContainerAgentConfigProjection
import com.kite.app.agent.sdk.account.AgentAccountCapabilities
import com.kite.app.agent.sdk.account.AgentAccountCapability
import com.kite.app.agent.sdk.account.AgentAccountCredentialReadResult
import com.kite.app.agent.sdk.account.AgentAccountCredentialSnapshot
import com.kite.app.agent.sdk.account.AgentAccountCredentialWriteResult
import com.kite.app.agent.sdk.account.AgentAccountIdentity
import com.kite.app.agent.sdk.account.AgentAccountIdentityResult
import com.kite.app.agent.sdk.account.AgentOfficialAccountAdapter
import com.kite.app.foundation.contracts.ContainerRecord
import org.json.JSONObject

internal class CodexOfficialAccountAdapter(
    containerProvider: () -> ContainerRecord?,
    private val fileStore: AtomicConfigFileStore,
) : AgentOfficialAccountAdapter {
    private val projection = ContainerAgentConfigProjection(containerProvider)

    override val adapterId: String = CodexAgentConfigAdapter.ADAPTER_ID

    override fun accountCapabilities(): AgentAccountCapabilities = AgentAccountCapabilities(
        supported = setOf(
            AgentAccountCapability.SaveCurrent,
            AgentAccountCapability.Switch,
            AgentAccountCapability.Delete,
            AgentAccountCapability.StableId,
        ),
    )

    override suspend fun currentIdentity(agentId: String): AgentAccountIdentityResult = runCatching {
        val bytes = readAuthBytes()
            ?: return@runCatching AgentAccountIdentityResult.Unavailable("Codex 官方登录尚未生成原生凭据")
        val accountId = codexAccountId(bytes)
            ?: return@runCatching AgentAccountIdentityResult.Unavailable("Codex 没有提供稳定账号 ID")
        codexIdentity(accountId).let(AgentAccountIdentityResult::Ready)
    }.getOrElse { error ->
        AgentAccountIdentityResult.Failed(error.message ?: "无法读取 Codex 官方账号状态")
    }

    override suspend fun captureCurrent(agentId: String): AgentAccountCredentialReadResult = runCatching {
        val bytes = readAuthBytes()
            ?: return@runCatching AgentAccountCredentialReadResult.Missing()
        val accountId = codexAccountId(bytes)
            ?: return@runCatching AgentAccountCredentialReadResult.Unavailable("Codex 凭据缺少稳定账号 ID")
        AgentAccountCredentialReadResult.Ready(
            snapshot = AgentAccountCredentialSnapshot(bytes.copyOf()),
            identity = codexIdentity(accountId),
        )
    }.getOrElse { error ->
        AgentAccountCredentialReadResult.Failed(error.message ?: "无法读取 Codex 官方凭据")
    }

    override suspend fun restoreCurrent(
        agentId: String,
        snapshot: AgentAccountCredentialSnapshot,
    ): AgentAccountCredentialWriteResult {
        val target = projection.resolve(AUTH_PATH)?.writeFile
            ?: return AgentAccountCredentialWriteResult.Unavailable("Kite 运行容器尚未创建")
        val before = runCatching { fileStore.read(target) }.getOrElse { error ->
            return AgentAccountCredentialWriteResult.Failed(
                error.message ?: "无法读取 Codex 原生凭据",
                restored = false,
            )
        }
        return when (
            val result = fileStore.replace(
                target = target,
                expectedRevision = before.revision,
                nextBytes = snapshot.bytes.copyOf(),
                validate = ::validateAuthBytes,
            )
        ) {
            is AtomicConfigFileWriteResult.Applied -> AgentAccountCredentialWriteResult.Applied
            is AtomicConfigFileWriteResult.Conflict ->
                AgentAccountCredentialWriteResult.Failed("Codex 原生凭据在切换期间发生变化", restored = false)
            is AtomicConfigFileWriteResult.Rejected ->
                AgentAccountCredentialWriteResult.Failed(result.message, restored = false)
            is AtomicConfigFileWriteResult.Failed ->
                AgentAccountCredentialWriteResult.Failed(result.message, result.restored)
        }
    }

    private fun readAuthBytes(): ByteArray? = projection.resolve(AUTH_PATH)
        ?.readFile
        ?.let(fileStore::read)
        ?.bytes
        ?.takeIf { it.isNotEmpty() }

    private fun codexAccountId(bytes: ByteArray): String? = runCatching {
        val root = JSONObject(bytes.toString(Charsets.UTF_8))
        val tokens = root.optJSONObject("tokens")
        listOf(
            root.optString("account_id"),
            root.optString("accountId"),
            tokens?.optString("account_id").orEmpty(),
            tokens?.optString("accountId").orEmpty(),
            tokens?.optString("chatgpt_account_id").orEmpty(),
        ).firstOrNull { value ->
            value.isNotBlank() && value.length <= MAX_ACCOUNT_ID && value.none(Char::isISOControl)
        }
    }.getOrNull()

    private fun validateAuthBytes(bytes: ByteArray): String? = runCatching {
        require(bytes.isNotEmpty()) { "Codex 官方凭据不能为空" }
        val root = JSONObject(bytes.toString(Charsets.UTF_8))
        require(root.optJSONObject("tokens") != null) { "Codex 官方凭据格式无效" }
        require(codexAccountId(bytes) != null) { "Codex 官方凭据缺少稳定账号 ID" }
        null
    }.getOrElse { error -> error.message ?: "Codex 官方凭据格式无效" }

    private fun codexIdentity(accountId: String): AgentAccountIdentity = AgentAccountIdentity(
        accountId = accountId,
        displayName = "ChatGPT · ${compactAccountId(accountId)}",
    )

    private fun compactAccountId(accountId: String): String =
        if (accountId.length <= 12) accountId else "${accountId.take(6)}…${accountId.takeLast(4)}"

    private companion object {
        const val AUTH_PATH = "/root/.codex/auth.json"
        const val MAX_ACCOUNT_ID = 256
    }
}
