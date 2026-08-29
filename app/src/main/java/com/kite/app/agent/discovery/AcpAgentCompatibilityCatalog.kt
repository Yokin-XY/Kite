package com.kite.app.agent.discovery

import android.content.Context
import com.kite.app.agent.registration.AgentRegistrySnapshot
import org.json.JSONArray
import org.json.JSONObject

internal enum class AcpAgentIntegrationState {
    Integrated,
    Declared,
    Candidate,
}

internal data class AcpAgentCompatibilityEntry(
    val candidate: AcpAgentCatalogEntry,
    val localAgentId: String?,
    val state: AcpAgentIntegrationState,
)

internal data class AcpAgentCompatibilitySnapshot(
    val catalog: AcpAgentCatalogSnapshot,
    val entries: List<AcpAgentCompatibilityEntry>,
) {
    val integratedCount: Int
        get() = entries.count { it.state == AcpAgentIntegrationState.Integrated }
}

/**
 * 只声明 ACP Registry ID 到 Kite 稳定 Agent ID 的别名。
 *
 * 安装状态、provider、argv、配置 Adapter 和会话能力继续从 [AgentRegistrySnapshot] 读取，
 * 这里不能复制第二份运行事实。
 */
internal class AcpAgentCompatibilityCatalog(context: Context) {
    private val aliases by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        context.applicationContext.assets.open(ASSET_PATH).bufferedReader().use { reader ->
            AcpAgentCompatibilityParser.parse(reader.readText())
        }
    }

    fun resolve(
        catalog: AcpAgentCatalogSnapshot,
        registry: AgentRegistrySnapshot,
    ): AcpAgentCompatibilitySnapshot {
        val localAgentIds = registry.entries
            .map { it.registration.definition.agentId }
            .toSet()
        return AcpAgentCompatibilitySnapshot(
            catalog = catalog,
            entries = catalog.entries.map { candidate ->
                val localAgentId = aliases[candidate.id]
                AcpAgentCompatibilityEntry(
                    candidate = candidate,
                    localAgentId = localAgentId,
                    state = when {
                        localAgentId == null -> AcpAgentIntegrationState.Candidate
                        localAgentId in localAgentIds -> AcpAgentIntegrationState.Integrated
                        else -> AcpAgentIntegrationState.Declared
                    },
                )
            },
        )
    }

    private companion object {
        const val ASSET_PATH = "agent-catalog/acp-compatibility.json"
    }
}

internal object AcpAgentCompatibilityParser {
    fun parse(payload: String): Map<String, String> {
        val root = JSONObject(payload)
        require(root.optInt("version") == 1) { "ACP 兼容目录版本无效" }
        val aliases = root.optJSONArray("aliases") ?: JSONArray()
        val pairs = buildList {
            for (index in 0 until minOf(aliases.length(), MAX_ALIASES)) {
                val item = aliases.optJSONObject(index) ?: continue
                val registryId = item.optString("registryId").trim().takeIf(STABLE_ID::matches)
                    ?: continue
                val agentId = item.optString("agentId").trim().takeIf(STABLE_ID::matches)
                    ?: continue
                add(registryId to agentId)
            }
        }
        require(pairs.map { it.first }.distinct().size == pairs.size) { "ACP Registry ID 重复" }
        return pairs.toMap()
    }

    private val STABLE_ID = Regex("[a-z0-9](?:[a-z0-9._-]*[a-z0-9])?")
    private const val MAX_ALIASES = 512
}
