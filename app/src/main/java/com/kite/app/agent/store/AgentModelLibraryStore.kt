package com.kite.app.agent.store

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Agent 模型库的 Kite 展示偏好。
 *
 * Provider、模型 ID、地址和密钥由 [AgentProviderCatalogStore] 统一拥有；这里仅保存自定义分组、
 * 某个供应商是否进入会话模型选择器，以及不参与请求的模型显示名称。
 */
class AgentModelLibraryStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES,
        Context.MODE_PRIVATE
    )

    fun snapshot(agentId: String): AgentModelLibrarySnapshot = synchronized(LOCK) {
        if (agentId.isBlank()) return@synchronized AgentModelLibrarySnapshot()
        readAgents().optJSONObject(agentId)?.toSnapshot() ?: AgentModelLibrarySnapshot()
    }

    fun createGroup(agentId: String, name: String): AgentModelLibraryGroup? {
        val normalized = name.trim().take(MAX_GROUP_NAME)
        if (agentId.isBlank() || normalized.isBlank()) return null
        return update(agentId) { current ->
            if (current.groups.any { it.name.equals(normalized, ignoreCase = true) }) {
                current to null
            } else {
                val group = AgentModelLibraryGroup(
                    id = "group-${UUID.randomUUID()}",
                    name = normalized,
                    order = (current.groups.maxOfOrNull(AgentModelLibraryGroup::order) ?: -1) + 1
                )
                current.copy(groups = current.groups + group) to group
            }
        }
    }

    fun renameGroup(agentId: String, groupId: String, name: String): Boolean {
        val normalized = name.trim().take(MAX_GROUP_NAME)
        if (agentId.isBlank() || groupId.isBlank() || normalized.isBlank()) return false
        return update(agentId) { current ->
            if (current.groups.any { it.id != groupId && it.name.equals(normalized, ignoreCase = true) }) {
                current to false
            } else {
                var changed = false
                val groups = current.groups.map { group ->
                    if (group.id == groupId && group.name != normalized) {
                        changed = true
                        group.copy(name = normalized)
                    } else group
                }
                current.copy(groups = groups) to changed
            }
        }
    }

    /** 删除分组只会解除归类，不会删除供应商或模型。 */
    fun deleteGroup(agentId: String, groupId: String): Boolean {
        if (agentId.isBlank() || groupId.isBlank()) return false
        return update(agentId) { current ->
            val groups = current.groups.filterNot { it.id == groupId }
            if (groups.size == current.groups.size) {
                current to false
            } else {
                current.copy(
                    groups = groups,
                    providers = current.providers.mapValues { (_, preference) ->
                        if (preference.groupId == groupId) preference.copy(groupId = null) else preference
                    }
                ).normalized() to true
            }
        }
    }

    fun assignProviderGroup(agentId: String, providerId: String, groupId: String?): Boolean {
        if (agentId.isBlank() || providerId.isBlank()) return false
        return update(agentId) { current ->
            val normalizedGroupId = groupId?.takeIf { target -> current.groups.any { it.id == target } }
            val existing = current.providers[providerId]
            if (existing?.groupId == normalizedGroupId) {
                current to false
            } else {
                current.copy(
                    providers = current.providers + (
                        providerId to (existing ?: AgentModelLibraryProviderPreference()).copy(
                            groupId = normalizedGroupId
                        )
                    )
                ).normalized() to true
            }
        }
    }

    fun setProviderVisible(agentId: String, providerId: String, visible: Boolean): Boolean {
        if (agentId.isBlank() || providerId.isBlank()) return false
        return update(agentId) { current ->
            val existing = current.providers[providerId]
            if ((existing?.visibleInConversation ?: true) == visible) {
                current to false
            } else {
                current.copy(
                    providers = current.providers + (
                        providerId to (existing ?: AgentModelLibraryProviderPreference()).copy(
                            visibleInConversation = visible
                        )
                    )
                ).normalized() to true
            }
        }
    }

    /**
     * 原子替换一个供应商的模型显示名称。模型 ID 仍由统一 Provider 目录拥有并参与真实请求；
     * 与 ID 相同的名称不需要额外保存，因此旧数据和默认行为都可以自然回退到模型 ID。
     */
    fun replaceProviderModelDisplayNames(
        agentId: String,
        providerId: String,
        models: List<AgentModelDisplayName>
    ): Boolean {
        if (agentId.isBlank() || providerId.isBlank()) return false
        val displayNames = models.mapNotNull { model ->
            val modelId = model.modelId.trim()
            val displayName = model.displayName.trim().take(MAX_MODEL_DISPLAY_NAME)
            if (modelId.isBlank() || displayName.isBlank() || displayName == modelId) null
            else modelId to displayName
        }.toMap()
        return update(agentId) { current ->
            val existing = current.providers[providerId]
            if (existing?.modelDisplayNames.orEmpty() == displayNames) {
                current to false
            } else {
                current.copy(
                    providers = current.providers + (
                        providerId to (existing ?: AgentModelLibraryProviderPreference()).copy(
                            modelDisplayNames = displayNames
                        )
                    )
                ).normalized() to true
            }
        }
    }

    /**
     * 原子保存供应商当前目录中的模型显示选择。
     *
     * 供应商只使用二态指示：只要至少一个模型显示，供应商即为显示；
     * 没有模型显示时供应商同时隐藏。目录外的休眠模型偏好保持不变。
     */
    fun setProviderModelSelection(
        agentId: String,
        providerId: String,
        modelIds: Collection<String>,
        visibleModelIds: Collection<String>,
    ): Boolean {
        if (agentId.isBlank() || providerId.isBlank()) return false
        val normalizedModelIds = modelIds.map(String::trim).filter(String::isNotBlank).toSet()
        if (normalizedModelIds.isEmpty()) return false
        val normalizedVisibleIds = visibleModelIds
            .map(String::trim)
            .filter(String::isNotBlank)
            .toSet()
            .intersect(normalizedModelIds)
        return update(agentId) { current ->
            val existing = current.providers[providerId] ?: AgentModelLibraryProviderPreference()
            val hidden = existing.hiddenModelIds.toMutableSet().apply {
                removeAll(normalizedModelIds)
                addAll(normalizedModelIds - normalizedVisibleIds)
            }
            val updated = existing.copy(
                visibleInConversation = normalizedVisibleIds.isNotEmpty(),
                hiddenModelIds = hidden,
            )
            if (updated == existing) {
                current to false
            } else {
                current.copy(providers = current.providers + (providerId to updated)).normalized() to true
            }
        }
    }

    /**
     * 增量更新 Agent 公布的系统模型显示名称。
     *
     * 动态目录本次没有公布的模型不等于用户删除了别名；保留这些休眠项，
     * 同一真实模型值恢复时即可继续使用原名称。提交为空或与真实值相同时清除该项。
     */
    fun updatePublishedModelDisplayNames(
        agentId: String,
        providerId: String,
        models: List<AgentModelDisplayName>
    ): Boolean {
        if (agentId.isBlank() || providerId.isBlank()) return false
        val updates = models.mapNotNull { model ->
            val modelId = model.modelId.trim()
            if (modelId.isBlank()) null else modelId to model.displayName.trim().take(MAX_MODEL_DISPLAY_NAME)
        }.toMap()
        return update(agentId) { current ->
            val existing = current.providers[providerId]
            val displayNames = existing?.modelDisplayNames.orEmpty().toMutableMap()
            updates.forEach { (modelId, displayName) ->
                if (displayName.isBlank() || displayName == modelId) displayNames.remove(modelId)
                else displayNames[modelId] = displayName
            }
            if (existing?.modelDisplayNames.orEmpty() == displayNames) {
                current to false
            } else {
                current.copy(
                    providers = current.providers + (
                        providerId to (existing ?: AgentModelLibraryProviderPreference()).copy(
                            modelDisplayNames = displayNames
                        )
                    )
                ).normalized() to true
            }
        }
    }

    internal fun resetForTest() = synchronized(LOCK) {
        preferences.edit().clear().commit()
    }

    private fun <T> update(
        agentId: String,
        transform: (AgentModelLibrarySnapshot) -> Pair<AgentModelLibrarySnapshot, T>
    ): T = synchronized(LOCK) {
        val agents = readAgents()
        val current = agents.optJSONObject(agentId)?.toSnapshot() ?: AgentModelLibrarySnapshot()
        val (next, result) = transform(current)
        if (next != current) {
            if (next.isEmpty()) agents.remove(agentId) else agents.put(agentId, next.toJson())
            writeAgents(agents)
        }
        result
    }

    private fun readAgents(): JSONObject = runCatching {
        val payload = JSONObject(preferences.getString(KEY_PAYLOAD, null) ?: "{}")
        if (payload.optInt(KEY_VERSION, VERSION) != VERSION) JSONObject()
        else payload.optJSONObject(KEY_AGENTS) ?: JSONObject()
    }.getOrElse { JSONObject() }

    private fun writeAgents(agents: JSONObject) {
        preferences.edit().putString(
            KEY_PAYLOAD,
            JSONObject().put(KEY_VERSION, VERSION).put(KEY_AGENTS, agents).toString()
        ).apply()
    }

    private fun JSONObject.toSnapshot(): AgentModelLibrarySnapshot {
        val groups = buildList {
            val array = optJSONArray(KEY_GROUPS) ?: JSONArray()
            for (index in 0 until array.length()) {
                val json = array.optJSONObject(index) ?: continue
                val id = json.optString(KEY_ID).trim()
                val name = json.optString(KEY_NAME).trim()
                if (id.isNotBlank() && name.isNotBlank()) {
                    add(AgentModelLibraryGroup(id, name, json.optInt(KEY_ORDER, index)))
                }
            }
        }.distinctBy(AgentModelLibraryGroup::id).sortedBy(AgentModelLibraryGroup::order)
        val providers = buildMap {
            val json = optJSONObject(KEY_PROVIDERS) ?: JSONObject()
            json.keys().forEach { providerId ->
                val value = json.optJSONObject(providerId) ?: return@forEach
                put(
                    providerId,
                    AgentModelLibraryProviderPreference(
                        groupId = value.optString(KEY_GROUP_ID).trim().takeIf(String::isNotBlank),
                        visibleInConversation = value.optBoolean(KEY_VISIBLE, true),
                        modelDisplayNames = buildMap {
                            val names = value.optJSONObject(KEY_MODEL_DISPLAY_NAMES) ?: JSONObject()
                            names.keys().forEach { modelId ->
                                val displayName = names.optString(modelId).trim().take(MAX_MODEL_DISPLAY_NAME)
                                if (modelId.isNotBlank() && displayName.isNotBlank() && displayName != modelId) {
                                    put(modelId, displayName)
                                }
                            }
                        },
                        hiddenModelIds = buildSet {
                            val hidden = value.optJSONArray(KEY_HIDDEN_MODEL_IDS) ?: JSONArray()
                            for (index in 0 until hidden.length()) {
                                hidden.optString(index).trim().takeIf(String::isNotBlank)?.let(::add)
                            }
                        },
                    )
                )
            }
        }
        return AgentModelLibrarySnapshot(groups, providers).normalized()
    }

    private fun AgentModelLibrarySnapshot.toJson(): JSONObject = JSONObject().apply {
        put(KEY_GROUPS, JSONArray().apply {
            groups.sortedBy(AgentModelLibraryGroup::order).forEach { group ->
                put(JSONObject().put(KEY_ID, group.id).put(KEY_NAME, group.name).put(KEY_ORDER, group.order))
            }
        })
        put(KEY_PROVIDERS, JSONObject().apply {
            providers.forEach { (providerId, preference) ->
                put(providerId, JSONObject().apply {
                    preference.groupId?.let { put(KEY_GROUP_ID, it) }
                    put(KEY_VISIBLE, preference.visibleInConversation)
                    if (preference.modelDisplayNames.isNotEmpty()) {
                        put(KEY_MODEL_DISPLAY_NAMES, JSONObject().apply {
                            preference.modelDisplayNames.forEach { (modelId, displayName) ->
                                put(modelId, displayName)
                            }
                        })
                    }
                    if (preference.hiddenModelIds.isNotEmpty()) {
                        put(KEY_HIDDEN_MODEL_IDS, JSONArray().apply {
                            preference.hiddenModelIds.sorted().forEach(::put)
                        })
                    }
                })
            }
        })
    }

    private fun AgentModelLibrarySnapshot.normalized(): AgentModelLibrarySnapshot = copy(
        providers = providers.filterValues {
            it.groupId != null ||
                !it.visibleInConversation ||
                it.modelDisplayNames.isNotEmpty() ||
                it.hiddenModelIds.isNotEmpty()
        }
    )

    private fun AgentModelLibrarySnapshot.isEmpty(): Boolean = groups.isEmpty() && providers.isEmpty()

    companion object {
        const val ALL_GROUP_ID = "__kite_all__"
        const val FREE_GROUP_ID = "__kite_free__"
        const val OFFICIAL_GROUP_ID = "__kite_official__"
        private val LOCK = Any()
        private const val PREFERENCES = "kite_agent_model_library"
        private const val KEY_PAYLOAD = "payload"
        private const val KEY_VERSION = "version"
        private const val KEY_AGENTS = "agents"
        private const val KEY_GROUPS = "groups"
        private const val KEY_PROVIDERS = "providers"
        private const val KEY_ID = "id"
        private const val KEY_NAME = "name"
        private const val KEY_ORDER = "order"
        private const val KEY_GROUP_ID = "groupId"
        private const val KEY_VISIBLE = "visibleInConversation"
        private const val KEY_MODEL_DISPLAY_NAMES = "modelDisplayNames"
        private const val KEY_HIDDEN_MODEL_IDS = "hiddenModelIds"
        private const val MAX_GROUP_NAME = 48
        private const val MAX_MODEL_DISPLAY_NAME = 128
        private const val VERSION = 1
    }
}

data class AgentModelLibrarySnapshot(
    val groups: List<AgentModelLibraryGroup> = emptyList(),
    val providers: Map<String, AgentModelLibraryProviderPreference> = emptyMap()
) {
    fun isProviderVisible(providerId: String): Boolean =
        providers[providerId]?.visibleInConversation ?: true

    fun isModelVisible(providerId: String, modelId: String): Boolean =
        modelId !in providers[providerId]?.hiddenModelIds.orEmpty()

    fun providerGroupId(providerId: String): String? = providers[providerId]?.groupId

    fun modelDisplayName(providerId: String, modelId: String, fallback: String): String =
        providers[providerId]?.modelDisplayNames?.get(modelId)?.takeIf(String::isNotBlank)
            ?: fallback.takeIf(String::isNotBlank)
            ?: modelId
}

data class AgentModelLibraryGroup(
    val id: String,
    val name: String,
    val order: Int
)

data class AgentModelLibraryProviderPreference(
    val groupId: String? = null,
    val visibleInConversation: Boolean = true,
    val modelDisplayNames: Map<String, String> = emptyMap(),
    val hiddenModelIds: Set<String> = emptySet(),
)

data class AgentModelDisplayName(
    val modelId: String,
    val displayName: String
)
