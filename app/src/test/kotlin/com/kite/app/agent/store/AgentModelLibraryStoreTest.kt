package com.kite.app.agent.store

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AgentModelLibraryStoreTest {
    private val context by lazy { ApplicationProvider.getApplicationContext<Context>() }
    private val store by lazy { AgentModelLibraryStore(context) }

    @Before
    fun setUp() {
        store.resetForTest()
    }

    @After
    fun tearDown() {
        store.resetForTest()
    }

    @Test
    fun `未保存偏好时供应商默认进入会话选择`() {
        assertTrue(store.snapshot("opencode").isProviderVisible("zhipu"))
    }

    @Test
    fun `分组删除只解除供应商归类且不改变可见性`() {
        val group = store.createGroup("opencode", "常用")!!
        assertTrue(store.assignProviderGroup("opencode", "zhipu", group.id))
        assertTrue(store.setProviderVisible("opencode", "zhipu", false))

        assertTrue(store.deleteGroup("opencode", group.id))

        val snapshot = store.snapshot("opencode")
        assertTrue(snapshot.groups.isEmpty())
        assertNull(snapshot.providerGroupId("zhipu"))
        assertFalse(snapshot.isProviderVisible("zhipu"))
    }

    @Test
    fun `同名分组不会重复创建且供应商偏好按Agent隔离`() {
        val group = store.createGroup("opencode", "免费")!!
        assertNull(store.createGroup("opencode", " 免费 "))
        assertTrue(store.assignProviderGroup("opencode", "builtin", group.id))

        assertEquals(group.id, store.snapshot("opencode").providerGroupId("builtin"))
        assertNull(store.snapshot("hermes").providerGroupId("builtin"))
    }

    @Test
    fun `模型显示名称按Agent供应商和模型ID隔离且不改变ID回退`() {
        assertTrue(
            store.replaceProviderModelDisplayNames(
                "opencode",
                "zhipu",
                listOf(
                    AgentModelDisplayName("glm-5.2", "日常模型"),
                    AgentModelDisplayName("glm-5.0", "glm-5.0")
                )
            )
        )

        val snapshot = AgentModelLibraryStore(context).snapshot("opencode")
        assertEquals("日常模型", snapshot.modelDisplayName("zhipu", "glm-5.2", "GLM-5.2"))
        assertEquals("GLM-5.0", snapshot.modelDisplayName("zhipu", "glm-5.0", "GLM-5.0"))
        assertEquals("GLM-5.2", snapshot.modelDisplayName("mimo", "glm-5.2", "GLM-5.2"))
        assertEquals("GLM-5.2", store.snapshot("hermes").modelDisplayName("zhipu", "glm-5.2", "GLM-5.2"))
    }

    @Test
    fun `系统来源显示名称按Kite来源和真实模型值隔离`() {
        assertTrue(
            store.replaceProviderModelDisplayNames(
                "codex",
                "__kite_official__:chatgpt",
                listOf(AgentModelDisplayName("openai/gpt-5.6", "日常"))
            )
        )
        assertTrue(
            store.replaceProviderModelDisplayNames(
                "codex",
                "builtin",
                listOf(AgentModelDisplayName("free/small", "轻量"))
            )
        )

        val snapshot = AgentModelLibraryStore(context).snapshot("codex")
        assertEquals("日常", snapshot.modelDisplayName(
            "__kite_official__:chatgpt",
            "openai/gpt-5.6",
            "GPT-5.6"
        ))
        assertEquals("轻量", snapshot.modelDisplayName("builtin", "free/small", "Free Small"))
        assertEquals("GPT-5.6", snapshot.modelDisplayName("builtin", "openai/gpt-5.6", "GPT-5.6"))
        assertEquals(
            setOf("openai/gpt-5.6"),
            snapshot.providers["__kite_official__:chatgpt"]?.modelDisplayNames?.keys
        )
    }
}
