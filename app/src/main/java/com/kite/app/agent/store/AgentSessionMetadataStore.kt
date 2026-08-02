package com.kite.app.agent.store

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Kite 对 Agent 原生会话附加的轻量用户元数据。
 *
 * 这里只保存 Kite 自有的归档标记，以及模型、权限这两个输入草稿偏好；不复制消息、标题、
 * 工作目录、运行状态或 Agent 当前配置。偏好只在发送时由 SDK 映射，不提前修改 Agent。
 */
class AgentSessionMetadataStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES,
        Context.MODE_PRIVATE
    )

    fun archivedSessionIds(providerId: String): Set<String> = synchronized(LOCK) {
        archivedSessions(providerId).mapTo(linkedSetOf(), AgentArchivedSessionMetadata::sessionId)
    }

    fun archivedSessions(providerId: String): List<AgentArchivedSessionMetadata> = synchronized(LOCK) {
        readRecords()
            .filter { it.providerId == providerId && it.archivedAtMillis != null }
            .map { record ->
                AgentArchivedSessionMetadata(
                    sessionId = record.sessionId,
                    archivedAtMillis = checkNotNull(record.archivedAtMillis),
                    sourceState = record.sourceState,
                    sourceCheckedAtMillis = record.sourceCheckedAtMillis,
                )
            }
    }

    fun archive(providerId: String, sessionId: String, nowMillis: Long = System.currentTimeMillis()): Boolean =
        update(providerId, sessionId) { current ->
            val checkedAt = nowMillis.coerceAtLeast(1L)
            val next = current.copy(
                archivedAtMillis = checkedAt,
                sourceState = AgentArchivedSessionSourceState.Available,
                sourceCheckedAtMillis = checkedAt,
            )
            next to (next != current)
        }

    fun reconcileSourceDirectory(
        providerId: String,
        sourceSessionIds: Set<String>,
        checkedAtMillis: Long = System.currentTimeMillis(),
    ): Boolean = synchronized(LOCK) {
        val normalizedProviderId = providerId.trim()
        if (normalizedProviderId.isBlank()) return@synchronized false
        val checkedAt = checkedAtMillis.coerceAtLeast(1L)
        val records = readRecords().toMutableList()
        var changed = false
        records.indices.forEach { index ->
            val current = records[index]
            if (current.providerId != normalizedProviderId || current.archivedAtMillis == null) return@forEach
            val nextState = if (current.sessionId in sourceSessionIds) {
                AgentArchivedSessionSourceState.Available
            } else {
                AgentArchivedSessionSourceState.Deleted
            }
            val next = current.copy(sourceState = nextState, sourceCheckedAtMillis = checkedAt)
            if (next != current) {
                records[index] = next
                changed = true
            }
        }
        if (changed) writeRecords(records)
        changed
    }

    fun restore(providerId: String, sessionId: String): Boolean = update(providerId, sessionId) { current ->
        val next = current.copy(archivedAtMillis = null)
        next to (next != current)
    }

    fun draftPreferences(providerId: String, sessionId: String): AgentSessionDraftPreferences? =
        synchronized(LOCK) {
            readRecords()
                .firstOrNull { it.providerId == providerId && it.sessionId == sessionId }
                ?.toDraftPreferences()
                ?.takeUnless(AgentSessionDraftPreferences::isEmpty)
        }

    fun saveDraftPreferences(
        providerId: String,
        sessionId: String,
        preferences: AgentSessionDraftPreferences,
    ): Boolean = update(providerId, sessionId) { current ->
        val next = current.copy(
            modelProviderId = preferences.modelProviderId?.normalizedOrNull(),
            modelId = preferences.modelId?.normalizedOrNull(),
            modelUsesAgentDefault = preferences.modelUsesAgentDefault,
            permissionConfigId = preferences.permissionConfigId?.normalizedOrNull(),
            permissionValue = preferences.permissionValue?.normalizedOrNull(),
        ).normalizedDraftPreferences()
        next to (next != current)
    }

    fun remove(providerId: String, sessionId: String): Boolean = synchronized(LOCK) {
        val records = readRecords().toMutableList()
        val removed = records.removeAll { it.providerId == providerId && it.sessionId == sessionId }
        if (removed) writeRecords(records)
        removed
    }

    internal fun resetForTest() {
        synchronized(LOCK) { preferences.edit().clear().apply() }
    }

    private fun update(
        providerId: String,
        sessionId: String,
        transform: (Record) -> Pair<Record, Boolean>
    ): Boolean {
        if (providerId.isBlank() || sessionId.isBlank()) return false
        return synchronized(LOCK) {
            val records = readRecords().toMutableList()
            val index = records.indexOfFirst { it.providerId == providerId && it.sessionId == sessionId }
            val current = records.getOrNull(index) ?: Record(providerId, sessionId)
            val (next, changed) = transform(current)
            if (!changed) return@synchronized false
            if (next.isEmpty()) {
                if (index >= 0) records.removeAt(index)
            } else if (index >= 0) {
                records[index] = next
            } else {
                records += next
            }
            writeRecords(records)
            true
        }
    }

    private fun readRecords(): List<Record> {
        val array = runCatching {
            JSONObject(preferences.getString(KEY_PAYLOAD, null) ?: "{}")
                .optJSONArray(KEY_RECORDS)
        }.getOrNull() ?: return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val json = array.optJSONObject(index) ?: continue
                val providerId = json.optString(KEY_PROVIDER_ID).trim()
                val sessionId = json.optString(KEY_SESSION_ID).trim()
                if (providerId.isBlank() || sessionId.isBlank()) continue
                val archivedAtMillis = json.optLong(KEY_ARCHIVED_AT, 0L).takeIf { it > 0L }
                val sourceState = when (json.optString(KEY_SOURCE_STATE)) {
                    SOURCE_AVAILABLE -> AgentArchivedSessionSourceState.Available
                    SOURCE_DELETED -> AgentArchivedSessionSourceState.Deleted
                    else -> AgentArchivedSessionSourceState.Unknown
                }
                val sourceCheckedAtMillis = json.optLong(KEY_SOURCE_CHECKED_AT, 0L).coerceAtLeast(0L)
                val record = Record(
                    providerId = providerId,
                    sessionId = sessionId,
                    archivedAtMillis = archivedAtMillis,
                    sourceState = sourceState,
                    sourceCheckedAtMillis = sourceCheckedAtMillis,
                    modelProviderId = json.optString(KEY_DRAFT_MODEL_PROVIDER_ID).normalizedOrNull(),
                    modelId = json.optString(KEY_DRAFT_MODEL_ID).normalizedOrNull(),
                    modelUsesAgentDefault = json.optBoolean(KEY_DRAFT_MODEL_USES_AGENT_DEFAULT),
                    permissionConfigId = json.optString(KEY_DRAFT_PERMISSION_CONFIG_ID).normalizedOrNull(),
                    permissionValue = json.optString(KEY_DRAFT_PERMISSION_VALUE).normalizedOrNull(),
                ).normalizedDraftPreferences()
                if (!record.isEmpty()) add(record)
            }
        }
    }

    private fun writeRecords(records: List<Record>) {
        val payload = JSONObject()
            .put(KEY_VERSION, VERSION)
            .put(KEY_RECORDS, JSONArray().apply {
                records.forEach { record ->
                    put(JSONObject().apply {
                        put(KEY_PROVIDER_ID, record.providerId)
                        put(KEY_SESSION_ID, record.sessionId)
                        record.archivedAtMillis?.let { put(KEY_ARCHIVED_AT, it) }
                        if (record.sourceState != AgentArchivedSessionSourceState.Unknown) {
                            put(KEY_SOURCE_STATE, when (record.sourceState) {
                                AgentArchivedSessionSourceState.Available -> SOURCE_AVAILABLE
                                AgentArchivedSessionSourceState.Deleted -> SOURCE_DELETED
                                AgentArchivedSessionSourceState.Unknown -> error("未知状态不应写入")
                            })
                        }
                        record.sourceCheckedAtMillis.takeIf { it > 0L }?.let {
                            put(KEY_SOURCE_CHECKED_AT, it)
                        }
                        record.modelProviderId?.let { put(KEY_DRAFT_MODEL_PROVIDER_ID, it) }
                        record.modelId?.let { put(KEY_DRAFT_MODEL_ID, it) }
                        if (record.modelProviderId != null && record.modelId != null) {
                            put(KEY_DRAFT_MODEL_USES_AGENT_DEFAULT, record.modelUsesAgentDefault)
                        }
                        record.permissionConfigId?.let { put(KEY_DRAFT_PERMISSION_CONFIG_ID, it) }
                        record.permissionValue?.let { put(KEY_DRAFT_PERMISSION_VALUE, it) }
                    })
                }
            })
        preferences.edit().putString(KEY_PAYLOAD, payload.toString()).apply()
    }

    private data class Record(
        val providerId: String,
        val sessionId: String,
        val archivedAtMillis: Long? = null,
        val sourceState: AgentArchivedSessionSourceState = AgentArchivedSessionSourceState.Unknown,
        val sourceCheckedAtMillis: Long = 0L,
        val modelProviderId: String? = null,
        val modelId: String? = null,
        val modelUsesAgentDefault: Boolean = false,
        val permissionConfigId: String? = null,
        val permissionValue: String? = null,
    ) {
        fun normalizedDraftPreferences(): Record {
            val hasModel = modelProviderId != null && modelId != null
            val hasPermission = permissionConfigId != null && permissionValue != null
            return copy(
                modelProviderId = modelProviderId.takeIf { hasModel },
                modelId = modelId.takeIf { hasModel },
                modelUsesAgentDefault = modelUsesAgentDefault && hasModel,
                permissionConfigId = permissionConfigId.takeIf { hasPermission },
                permissionValue = permissionValue.takeIf { hasPermission },
            )
        }

        fun toDraftPreferences() = AgentSessionDraftPreferences(
            modelProviderId = modelProviderId,
            modelId = modelId,
            modelUsesAgentDefault = modelUsesAgentDefault,
            permissionConfigId = permissionConfigId,
            permissionValue = permissionValue,
        )

        fun isEmpty(): Boolean = archivedAtMillis == null &&
            modelProviderId == null && permissionConfigId == null
    }

    private fun String.normalizedOrNull(): String? = trim().take(MAX_DRAFT_VALUE).takeIf(String::isNotBlank)

    private companion object {
        val LOCK = Any()
        const val PREFERENCES = "kite_agent_session_metadata"
        const val KEY_PAYLOAD = "payload"
        const val KEY_VERSION = "version"
        const val KEY_RECORDS = "records"
        const val KEY_PROVIDER_ID = "providerId"
        const val KEY_SESSION_ID = "sessionId"
        const val KEY_ARCHIVED_AT = "archivedAt"
        const val KEY_SOURCE_STATE = "sourceState"
        const val KEY_SOURCE_CHECKED_AT = "sourceCheckedAt"
        const val KEY_DRAFT_MODEL_PROVIDER_ID = "draftModelProviderId"
        const val KEY_DRAFT_MODEL_ID = "draftModelId"
        const val KEY_DRAFT_MODEL_USES_AGENT_DEFAULT = "draftModelUsesAgentDefault"
        const val KEY_DRAFT_PERMISSION_CONFIG_ID = "draftPermissionConfigId"
        const val KEY_DRAFT_PERMISSION_VALUE = "draftPermissionValue"
        const val SOURCE_AVAILABLE = "available"
        const val SOURCE_DELETED = "deleted"
        const val VERSION = 4
        const val MAX_DRAFT_VALUE = 512
    }
}

data class AgentSessionDraftPreferences(
    val modelProviderId: String? = null,
    val modelId: String? = null,
    val modelUsesAgentDefault: Boolean = false,
    val permissionConfigId: String? = null,
    val permissionValue: String? = null,
) {
    fun isEmpty(): Boolean = (modelProviderId == null || modelId == null) &&
        (permissionConfigId == null || permissionValue == null)
}

data class AgentArchivedSessionMetadata(
    val sessionId: String,
    val archivedAtMillis: Long,
    val sourceState: AgentArchivedSessionSourceState,
    val sourceCheckedAtMillis: Long,
)

enum class AgentArchivedSessionSourceState {
    Unknown,
    Available,
    Deleted,
}
