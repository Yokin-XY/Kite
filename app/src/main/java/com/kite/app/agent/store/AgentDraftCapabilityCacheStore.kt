package com.kite.app.agent.store

import android.content.Context
import com.kite.app.agent.contract.AgentCommand
import com.kite.app.agent.contract.AgentConfigCategory
import com.kite.app.agent.contract.AgentConfigChoice
import com.kite.app.agent.contract.AgentConfigOption
import com.kite.app.agent.contract.AgentMode
import com.kite.app.agent.runtime.AgentDraftCapabilityCatalog
import org.json.JSONArray
import org.json.JSONObject

/**
 * 最近一次由 Agent 公布的草稿可选项缓存。
 *
 * 这不是会话或用户配置事实源：不保存 sessionId、消息、密钥或草稿选择，只让下次空白页在不创建
 * Agent 会话的前提下先展示已知的模式、即时配置和命令。真实会话一旦公布新目录就覆盖缓存。
 */
class AgentDraftCapabilityCacheStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun catalog(agentId: String): AgentDraftCapabilityCatalog? = synchronized(LOCK) {
        if (agentId.isBlank()) return@synchronized null
        val json = readRoot().optJSONObject(agentId) ?: return@synchronized null
        runCatching { json.toCatalog() }.getOrNull()
    }

    fun put(agentId: String, catalog: AgentDraftCapabilityCatalog) = synchronized(LOCK) {
        if (agentId.isBlank()) return@synchronized
        val root = readRoot()
        if (catalog.isEmpty()) {
            root.remove(agentId)
        } else {
            root.put(agentId, catalog.toJson())
            trimOldestCatalogs(root)
        }
        val payload = JSONObject()
            .put(KEY_VERSION, VERSION)
            .put(KEY_CATALOGS, root)
        preferences.edit().putString(KEY_PAYLOAD, payload.toString()).apply()
    }

    internal fun resetForTest() = synchronized(LOCK) {
        preferences.edit().clear().commit()
    }

    private fun readRoot(): JSONObject = runCatching {
        val payload = JSONObject(preferences.getString(KEY_PAYLOAD, null) ?: "{}")
        if (payload.optInt(KEY_VERSION, VERSION) != VERSION) {
            JSONObject()
        } else {
            payload.optJSONObject(KEY_CATALOGS) ?: JSONObject()
        }
    }.getOrElse { JSONObject() }

    private fun trimOldestCatalogs(root: JSONObject) {
        while (root.length() > MAX_CATALOGS) {
            val oldest = root.keys().asSequence().firstOrNull() ?: return
            root.remove(oldest)
        }
    }

    private fun AgentDraftCapabilityCatalog.toJson(): JSONObject = JSONObject().apply {
        put(KEY_VERSION, VERSION)
        put(KEY_CONFIGURATION, JSONArray().apply {
            configuration.take(MAX_CONFIGURATION).forEach { option -> put(option.toJson()) }
        })
        put(KEY_MODES, JSONArray().apply {
            modes.take(MAX_MODES).forEach { mode ->
                put(JSONObject().apply {
                    put(KEY_ID, mode.id.safe(MAX_ID))
                    put(KEY_NAME, mode.name.safe(MAX_TEXT))
                    mode.description?.safe(MAX_DESCRIPTION)?.let { put(KEY_DESCRIPTION, it) }
                })
            }
        })
        currentModeId?.safe(MAX_ID)?.let { put(KEY_CURRENT_MODE, it) }
        put(KEY_COMMANDS, JSONArray().apply {
            commands.take(MAX_COMMANDS).forEach { command ->
                put(JSONObject().apply {
                    put(KEY_NAME, command.name.safe(MAX_ID))
                    put(KEY_DESCRIPTION, command.description.safe(MAX_DESCRIPTION))
                    command.inputHint?.safe(MAX_DESCRIPTION)?.let { put(KEY_INPUT_HINT, it) }
                })
            }
        })
    }

    private fun AgentConfigOption.toJson(): JSONObject = JSONObject().apply {
        put(KEY_ID, id.safe(MAX_ID))
        put(KEY_NAME, name.safe(MAX_TEXT))
        description?.safe(MAX_DESCRIPTION)?.let { put(KEY_DESCRIPTION, it) }
        category?.value?.safe(MAX_ID)?.let { put(KEY_CATEGORY, it) }
        when (this@toJson) {
            is AgentConfigOption.Select -> {
                put(KEY_TYPE, TYPE_SELECT)
                put(KEY_CURRENT_VALUE, currentValue.safe(MAX_TEXT))
                put(KEY_CHOICES, JSONArray().apply {
                    choices.take(MAX_CHOICES).forEach { choice ->
                        put(JSONObject().apply {
                            put(KEY_VALUE, choice.value.safe(MAX_TEXT))
                            put(KEY_NAME, choice.name.safe(MAX_TEXT))
                            choice.description?.safe(MAX_DESCRIPTION)?.let { put(KEY_DESCRIPTION, it) }
                            choice.groupId?.safe(MAX_ID)?.let { put(KEY_GROUP_ID, it) }
                            choice.groupName?.safe(MAX_TEXT)?.let { put(KEY_GROUP_NAME, it) }
                        })
                    }
                })
            }
            is AgentConfigOption.Toggle -> {
                put(KEY_TYPE, TYPE_TOGGLE)
                put(KEY_CURRENT_VALUE, currentValue)
            }
        }
    }

    private fun JSONObject.toCatalog(): AgentDraftCapabilityCatalog = AgentDraftCapabilityCatalog(
        configuration = optJSONArray(KEY_CONFIGURATION).mapObjects { option -> option.toConfigOption() },
        modes = optJSONArray(KEY_MODES).mapObjects { mode ->
            AgentMode(
                id = mode.optString(KEY_ID).trim(),
                name = mode.optString(KEY_NAME).trim(),
                description = mode.optString(KEY_DESCRIPTION).trim().takeIf(String::isNotBlank)
            )
        }.filter { it.id.isNotBlank() && it.name.isNotBlank() },
        currentModeId = optString(KEY_CURRENT_MODE).trim().takeIf(String::isNotBlank),
        commands = optJSONArray(KEY_COMMANDS).mapObjects { command ->
            AgentCommand(
                name = command.optString(KEY_NAME).trim(),
                description = command.optString(KEY_DESCRIPTION).trim(),
                inputHint = command.optString(KEY_INPUT_HINT).trim().takeIf(String::isNotBlank)
            )
        }.filter { it.name.isNotBlank() }
    )

    private fun JSONObject.toConfigOption(): AgentConfigOption? {
        val id = optString(KEY_ID).trim()
        val name = optString(KEY_NAME).trim()
        if (id.isBlank() || name.isBlank()) return null
        val description = optString(KEY_DESCRIPTION).trim().takeIf(String::isNotBlank)
        val category = optString(KEY_CATEGORY).trim().takeIf(String::isNotBlank)?.let(::AgentConfigCategory)
        return when (optString(KEY_TYPE)) {
            TYPE_SELECT -> AgentConfigOption.Select(
                id = id,
                name = name,
                description = description,
                category = category,
                currentValue = optString(KEY_CURRENT_VALUE),
                choices = optJSONArray(KEY_CHOICES).mapObjects { choice ->
                    AgentConfigChoice(
                        value = choice.optString(KEY_VALUE),
                        name = choice.optString(KEY_NAME),
                        description = choice.optString(KEY_DESCRIPTION).trim().takeIf(String::isNotBlank),
                        groupId = choice.optString(KEY_GROUP_ID).trim().takeIf(String::isNotBlank),
                        groupName = choice.optString(KEY_GROUP_NAME).trim().takeIf(String::isNotBlank)
                    )
                }.filter { it.value.isNotBlank() && it.name.isNotBlank() }
            ).takeIf { it.choices.isNotEmpty() }
            TYPE_TOGGLE -> AgentConfigOption.Toggle(
                id = id,
                name = name,
                description = description,
                category = category,
                currentValue = optBoolean(KEY_CURRENT_VALUE)
            )
            else -> null
        }
    }

    private fun <T : Any> JSONArray?.mapObjects(transform: (JSONObject) -> T?): List<T> {
        if (this == null) return emptyList()
        return buildList {
            for (index in 0 until length()) {
                optJSONObject(index)?.let(transform)?.let(::add)
            }
        }
    }

    private fun AgentDraftCapabilityCatalog.isEmpty(): Boolean =
        configuration.isEmpty() && modes.isEmpty() && commands.isEmpty()

    private fun String.safe(maxLength: Int): String = trim().take(maxLength)

    private companion object {
        val LOCK = Any()
        const val PREFERENCES = "kite_agent_draft_capability_cache"
        const val KEY_PAYLOAD = "payload"
        const val KEY_CATALOGS = "catalogs"
        const val KEY_VERSION = "version"
        const val KEY_CONFIGURATION = "configuration"
        const val KEY_MODES = "modes"
        const val KEY_COMMANDS = "commands"
        const val KEY_CURRENT_MODE = "currentModeId"
        const val KEY_TYPE = "type"
        const val KEY_ID = "id"
        const val KEY_NAME = "name"
        const val KEY_DESCRIPTION = "description"
        const val KEY_CATEGORY = "category"
        const val KEY_CURRENT_VALUE = "currentValue"
        const val KEY_CHOICES = "choices"
        const val KEY_VALUE = "value"
        const val KEY_GROUP_ID = "groupId"
        const val KEY_GROUP_NAME = "groupName"
        const val KEY_INPUT_HINT = "inputHint"
        const val TYPE_SELECT = "select"
        const val TYPE_TOGGLE = "toggle"
        const val VERSION = 1
        const val MAX_CONFIGURATION = 32
        const val MAX_CATALOGS = 32
        const val MAX_CHOICES = 256
        const val MAX_MODES = 64
        const val MAX_COMMANDS = 256
        const val MAX_ID = 160
        const val MAX_TEXT = 240
        const val MAX_DESCRIPTION = 640
    }
}
