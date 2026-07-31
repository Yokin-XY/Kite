package com.kite.app.agent.store

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Kite 对 Agent 原生会话附加的轻量用户元数据。
 *
 * 这里只保存 Kite 自有的归档标记，不复制消息、标题、工作目录、运行状态或 Agent 当前配置。
 * Agent 会话内容和模型状态仍由 Agent 自己拥有。
 */
class AgentSessionMetadataStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES,
        Context.MODE_PRIVATE
    )

    fun archivedSessionIds(providerId: String): Set<String> = synchronized(LOCK) {
        readRecords()
            .asSequence()
            .filter { it.providerId == providerId && it.archivedAtMillis != null }
            .map(Record::sessionId)
            .toSet()
    }

    fun archive(providerId: String, sessionId: String, nowMillis: Long = System.currentTimeMillis()): Boolean =
        update(providerId, sessionId) { current ->
            val next = current.copy(archivedAtMillis = nowMillis.coerceAtLeast(1L))
            next to (next != current)
        }

    fun restore(providerId: String, sessionId: String): Boolean = update(providerId, sessionId) { current ->
        val next = current.copy(archivedAtMillis = null)
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
                    ?: continue
                add(Record(providerId, sessionId, archivedAtMillis))
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
                    })
                }
            })
        preferences.edit().putString(KEY_PAYLOAD, payload.toString()).apply()
    }

    private data class Record(
        val providerId: String,
        val sessionId: String,
        val archivedAtMillis: Long? = null
    ) {
        fun isEmpty(): Boolean = archivedAtMillis == null
    }

    private companion object {
        val LOCK = Any()
        const val PREFERENCES = "kite_agent_session_metadata"
        const val KEY_PAYLOAD = "payload"
        const val KEY_VERSION = "version"
        const val KEY_RECORDS = "records"
        const val KEY_PROVIDER_ID = "providerId"
        const val KEY_SESSION_ID = "sessionId"
        const val KEY_ARCHIVED_AT = "archivedAt"
        const val VERSION = 2
    }
}
