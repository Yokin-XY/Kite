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

    @Test
    fun `不同会话分别保存最后模型和权限且删除时一起清理`() {
        val first = AgentSessionDraftPreferences(
            modelProviderId = "zhipu",
            modelId = "glm-5.2",
            permissionConfigId = "kite.session_permission",
            permissionValue = "full",
        )
        val second = AgentSessionDraftPreferences(
            modelProviderId = "opencode",
            modelId = "mimo-v2.5-free",
            permissionConfigId = "kite.session_permission",
            permissionValue = "approval",
        )

        assertTrue(store.saveDraftPreferences("opencode", "session-a", first))
        assertTrue(store.saveDraftPreferences("opencode", "session-b", second))

        val restored = AgentSessionMetadataStore(context)
        assertEquals(first, restored.draftPreferences("opencode", "session-a"))
        assertEquals(second, restored.draftPreferences("opencode", "session-b"))
        assertTrue(restored.remove("opencode", "session-a"))
        assertEquals(null, restored.draftPreferences("opencode", "session-a"))
        assertEquals(second, restored.draftPreferences("opencode", "session-b"))
    }

    @Test
    fun `回合用时作为Kite会话元数据持久化且不复制消息`() {
        val timings = listOf(
            AgentPersistedTurnTiming(1L, "a".repeat(64), 3_500L),
            AgentPersistedTurnTiming(2L, "b".repeat(64), 12_000L),
        )

        assertTrue(store.saveTurnTimings("opencode", "session-a", timings))

        val restored = AgentSessionMetadataStore(context)
        assertEquals(timings, restored.turnTimings("opencode", "session-a"))
        assertTrue(restored.turnTimings("hermes", "session-a").isEmpty())
    }

    @Test
    fun `完整源目录只把确实缺失的归档会话标记为已删除`() {
        store.archive("opencode", "session-present", 100L)
        store.archive("opencode", "session-missing", 100L)

        assertTrue(store.reconcileSourceDirectory("opencode", setOf("session-present"), 200L))

        val records = store.archivedSessions("opencode").associateBy { it.sessionId }
        assertEquals(AgentArchivedSessionSourceState.Available, records.getValue("session-present").sourceState)
        assertEquals(AgentArchivedSessionSourceState.Deleted, records.getValue("session-missing").sourceState)
        assertEquals(200L, records.getValue("session-missing").sourceCheckedAtMillis)
    }

    @Test
    fun `旧归档记录读取为尚未确认并可迁移到新版源状态`() {
        val preferences = context.getSharedPreferences("kite_agent_session_metadata", Context.MODE_PRIVATE)
        preferences.edit().putString(
            "payload",
            """{"version":2,"records":[{"providerId":"opencode","sessionId":"legacy","archivedAt":456}]}"""
        ).commit()

        assertEquals(
            AgentArchivedSessionSourceState.Unknown,
            store.archivedSessions("opencode").single().sourceState,
        )
        assertTrue(store.reconcileSourceDirectory("opencode", emptySet(), 789L))
        assertEquals(
            AgentArchivedSessionSourceState.Deleted,
            store.archivedSessions("opencode").single().sourceState,
        )
    }
}
