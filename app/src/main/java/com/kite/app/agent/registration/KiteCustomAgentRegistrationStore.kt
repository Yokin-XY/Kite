package com.kite.app.agent.registration

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject

data class CustomAgentRegistrationSignal(
    val revision: Long = 0L,
    val reason: String = "initial",
    val agentId: String? = null
)

/**
 * 用户自定义 Agent 的低频登记事实源。
 *
 * 这里只保存身份和安全的启动/连接引用；密钥、连接状态、会话和资源安装状态都不属于本存储。
 */
class KiteCustomAgentRegistrationStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private val _signals = MutableStateFlow(CustomAgentRegistrationSignal())

    val signals: StateFlow<CustomAgentRegistrationSignal> = _signals

    fun snapshot(): List<AgentRegistration> =
        runCatching {
            val root = JSONObject(preferences.getString(KEY_REGISTRATIONS, "{}").orEmpty())
            val registrations = root.optJSONArray("registrations") ?: JSONArray()
            buildList {
                for (index in 0 until registrations.length()) {
                    parse(registrations.optJSONObject(index))?.let(::add)
                }
            }
        }.getOrDefault(emptyList())

    @Synchronized
    fun register(
        registration: AgentRegistration,
        reservedAgentIds: Set<String> = emptySet()
    ): AgentRegistrationWriteResult {
        validationProblem(registration, reservedAgentIds)?.let {
            return AgentRegistrationWriteResult.Rejected(it)
        }
        if (snapshot().any { it.definition.agentId == registration.definition.agentId }) {
            return AgentRegistrationWriteResult.Rejected("Agent ID 已登记：${registration.definition.agentId}")
        }
        persist(snapshot() + registration, reason = "register", agentId = registration.definition.agentId)
        return AgentRegistrationWriteResult.Accepted(registration)
    }

    @Synchronized
    fun update(
        registration: AgentRegistration,
        reservedAgentIds: Set<String> = emptySet()
    ): AgentRegistrationWriteResult {
        validationProblem(registration, reservedAgentIds)?.let {
            return AgentRegistrationWriteResult.Rejected(it)
        }
        val current = snapshot()
        if (current.none { it.definition.agentId == registration.definition.agentId }) {
            return AgentRegistrationWriteResult.Rejected("Agent 尚未登记：${registration.definition.agentId}")
        }
        persist(
            current.map { existing ->
                if (existing.definition.agentId == registration.definition.agentId) registration else existing
            },
            reason = "update",
            agentId = registration.definition.agentId
        )
        return AgentRegistrationWriteResult.Accepted(registration)
    }

    @Synchronized
    fun remove(agentId: String): Boolean {
        val current = snapshot()
        val next = current.filterNot { it.definition.agentId == agentId }
        if (next.size == current.size) return false
        persist(next, reason = "remove", agentId = agentId)
        return true
    }

    private fun validationProblem(
        registration: AgentRegistration,
        reservedAgentIds: Set<String>
    ): String? {
        if (registration.source != AgentRegistrationSource.Custom) {
            return "自定义 Agent 只能使用 custom 来源"
        }
        AgentRegistrationPolicy.problem(registration)?.let { return it }
        if (registration.definition.agentId in reservedAgentIds) {
            return "Agent ID 已由资源声明：${registration.definition.agentId}"
        }
        return null
    }

    private fun persist(registrations: List<AgentRegistration>, reason: String, agentId: String) {
        val root = JSONObject()
            .put("schemaVersion", SCHEMA_VERSION)
            .put("registrations", JSONArray().apply { registrations.forEach { put(serialize(it)) } })
        preferences.edit().putString(KEY_REGISTRATIONS, root.toString()).apply()
        _signals.value = CustomAgentRegistrationSignal(
            revision = _signals.value.revision + 1L,
            reason = reason,
            agentId = agentId
        )
    }

    private fun serialize(registration: AgentRegistration): JSONObject {
        val definition = registration.definition
        val launch = registration.launch
        return JSONObject()
            .put("agentId", definition.agentId)
            .put("displayName", definition.displayName)
            .put("description", definition.description)
            .put("iconText", definition.iconText)
            .put("configurationRequired", registration.configurationRequired)
            .put("configAdapterId", registration.configAdapterId ?: JSONObject.NULL)
            .put("sessionAdapterId", registration.sessionAdapterId ?: JSONObject.NULL)
            .put(
                "launch",
                JSONObject()
                    .put("mode", if (launch is AgentLaunchSpec.Managed) "managed" else "attach")
                    .put("providerId", launch.providerId)
                    .put("protocol", launch.protocol)
                    .put("transport", launch.transport)
                    .apply {
                        when (launch) {
                            is AgentLaunchSpec.Managed -> put(
                                "argv",
                                JSONArray().apply { launch.argv.forEach(::put) }
                            )
                            is AgentLaunchSpec.Attach -> put(
                                "connectionReference",
                                launch.connectionReference
                            )
                        }
                    }
            )
    }

    private fun parse(json: JSONObject?): AgentRegistration? {
        if (json == null) return null
        val launchJson = json.optJSONObject("launch") ?: return null
        val providerId = launchJson.optString("providerId").trim()
        val protocol = launchJson.optString("protocol").trim().lowercase()
        val transport = launchJson.optString("transport").trim().lowercase()
        val launch = when (launchJson.optString("mode").trim().lowercase()) {
            "managed" -> AgentLaunchSpec.Managed(
                providerId = providerId,
                protocol = protocol,
                transport = transport,
                argv = launchJson.optJSONArray("argv").toStringList()
            )
            "attach" -> AgentLaunchSpec.Attach(
                providerId = providerId,
                protocol = protocol,
                transport = transport,
                connectionReference = launchJson.optString("connectionReference").trim()
            )
            else -> return null
        }
        return AgentRegistration(
            definition = AgentDefinition(
                agentId = json.optString("agentId").trim(),
                displayName = json.optString("displayName").trim(),
                description = json.optString("description").trim(),
                iconText = json.optString("iconText").trim()
            ),
            source = AgentRegistrationSource.Custom,
            launch = launch,
            configurationRequired = json.optBoolean("configurationRequired", false),
            configAdapterId = json.takeUnless { it.isNull("configAdapterId") }
                ?.optString("configAdapterId")
                ?.trim()
                ?.ifBlank { null },
            sessionAdapterId = json.takeUnless { it.isNull("sessionAdapterId") }
                ?.optString("sessionAdapterId")
                ?.trim()
                ?.ifBlank { null }
        ).takeIf { AgentRegistrationPolicy.problem(it) == null }
    }

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        return buildList {
            for (index in 0 until length()) {
                optString(index).trim().takeIf(String::isNotBlank)?.let(::add)
            }
        }
    }

    private companion object {
        const val PREFERENCES = "kite_custom_agent_registrations"
        const val KEY_REGISTRATIONS = "registry"
        const val SCHEMA_VERSION = 2
    }
}
