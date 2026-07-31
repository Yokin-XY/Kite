package com.kite.app.agent.store

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AgentSessionMetadataStoreTest {
    private val context by lazy { ApplicationProvider.getApplicationContext<Context>() }
    private val store by lazy { AgentSessionMetadataStore(context) }

    @Before
    fun setUp() = store.resetForTest()

    @After
    fun tearDown() = store.resetForTest()

    @Test
    fun `归档与恢复只改变对应 Agent 会话的可见标记`() {
        assertTrue(store.archive("opencode", "session-a", 123L))
        assertFalse(store.archive("opencode", "session-a", 123L))
        assertEquals(setOf("session-a"), store.archivedSessionIds("opencode"))
        assertTrue(store.archivedSessionIds("hermes").isEmpty())

        assertTrue(store.restore("opencode", "session-a"))
        assertFalse(store.restore("opencode", "session-a"))
        assertTrue(store.archivedSessionIds("opencode").isEmpty())
    }

    @Test
    fun `旧版模型偏好被忽略且下次写入只保留归档字段`() {
        val preferences = context.getSharedPreferences("kite_agent_session_metadata", Context.MODE_PRIVATE)
        preferences.edit().putString(
            "payload",
            """{"version":1,"records":[{"providerId":"opencode","sessionId":"legacy-model","modelConfigId":"model","modelValue":"zhipu/glm-5.2"},{"providerId":"opencode","sessionId":"archived","archivedAt":456,"modelConfigId":"model","modelValue":"zhipu/glm-5.2"}]}"""
        ).commit()

        assertEquals(setOf("archived"), store.archivedSessionIds("opencode"))
        assertTrue(store.archive("opencode", "session-new", 789L))

        val rewritten = preferences.getString("payload", "").orEmpty()
        assertFalse(rewritten.contains("modelConfigId"))
        assertFalse(rewritten.contains("modelValue"))
        assertFalse(rewritten.contains("legacy-model"))
    }

    @Test
    fun `真实删除成功后可以清除整条 Kite 元数据`() {
        store.archive("opencode", "session-a", 456L)

        assertTrue(store.remove("opencode", "session-a"))
        assertFalse(store.remove("opencode", "session-a"))
        assertTrue(store.archivedSessionIds("opencode").isEmpty())
    }
}
