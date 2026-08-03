package com.kite.app.agent.acp

import com.agentclientprotocol.model.SessionNotification
import com.agentclientprotocol.model.SessionUpdate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * ACP Agent 可以在 `session/load` 返回前重放历史。部分 SDK 版本在
 * ClientSession 尚未建立时会丢弃这些通知，因此在 stdio 协议边界短暂旁路捕获，待请求
 * 成功后补交；正常会话更新仍由官方 SDK 负责。
 */
internal class AcpInlineSessionUpdateBuffer {
    private val json = Json { ignoreUnknownKeys = true }
    private val relays = linkedMapOf<String, AcpInlineSessionUpdateRelay>()

    @Synchronized
    fun begin(
        sessionId: String,
        deliver: (SessionUpdate) -> Unit,
    ): AcpInlineSessionUpdateRelay = AcpInlineSessionUpdateRelay(deliver).also { relay ->
        relays[sessionId] = relay
    }

    @Synchronized
    fun end(sessionId: String, relay: AcpInlineSessionUpdateRelay) {
        if (relays[sessionId] === relay) relays.remove(sessionId)
    }

    fun inspect(line: String) {
        synchronized(this) {
            if (relays.isEmpty()) return
        }
        val notification = runCatching {
            val message = json.parseToJsonElement(line).jsonObject
            if (message["method"]?.jsonPrimitive?.contentOrNull != SESSION_UPDATE_METHOD) return
            val params = message["params"] ?: return
            json.decodeFromJsonElement(SessionNotification.serializer(), params)
        }.getOrNull() ?: return
        synchronized(this) {
            relays[notification.sessionId.value]
        }?.capture(notification.update)
    }

    private companion object {
        const val SESSION_UPDATE_METHOD = "session/update"
    }
}

internal class AcpInlineSessionUpdateRelay(
    private val deliver: (SessionUpdate) -> Unit,
) {
    private val captured = mutableListOf<CapturedUpdate>()
    private var capturing = true
    private var deduplicating = true

    @Synchronized
    fun capture(update: SessionUpdate) {
        if (capturing) captured += CapturedUpdate(update)
    }

    fun fromSdk(update: SessionUpdate) {
        val shouldDeliver = synchronized(this) {
            val capturedUpdate = if (deduplicating) {
                captured.firstOrNull { !it.deliveredBySdk && it.update == update }
            } else {
                null
            }
            capturedUpdate?.deliveredBySdk = true
            val forward = capturedUpdate?.deliveredManually != true
            if (captured.isNotEmpty() && captured.all(CapturedUpdate::deliveredBySdk)) {
                captured.clear()
                deduplicating = false
            }
            forward
        }
        if (shouldDeliver) deliver(update)
    }

    fun complete() {
        val missed = synchronized(this) {
            if (!capturing) return
            capturing = false
            captured.filterNot(CapturedUpdate::deliveredBySdk).onEach {
                it.deliveredManually = true
            }.map(CapturedUpdate::update).also {
                if (captured.isEmpty()) deduplicating = false
            }
        }
        missed.forEach(deliver)
    }

    @Synchronized
    fun abort() {
        capturing = false
        deduplicating = false
        captured.clear()
    }

    @Synchronized
    fun releaseDeduplication() {
        deduplicating = false
        captured.clear()
    }

    private data class CapturedUpdate(
        val update: SessionUpdate,
        var deliveredBySdk: Boolean = false,
        var deliveredManually: Boolean = false,
    )
}
