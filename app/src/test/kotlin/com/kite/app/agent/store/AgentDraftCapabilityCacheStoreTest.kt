package com.kite.app.agent.store

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.kite.app.agent.contract.AgentCommand
import com.kite.app.agent.contract.AgentConfigCategory
import com.kite.app.agent.contract.AgentConfigChoice
import com.kite.app.agent.contract.AgentConfigOption
import com.kite.app.agent.contract.AgentMode
import com.kite.app.agent.runtime.AgentDraftCapabilityCatalog
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AgentDraftCapabilityCacheStoreTest {
    private val context by lazy { ApplicationProvider.getApplicationContext<Context>() }
    private val store by lazy { AgentDraftCapabilityCacheStore(context) }

    @Before
    fun setUp() {
        store.resetForTest()
    }

    @After
    fun tearDown() {
        store.resetForTest()
    }

    @Test
    fun `缓存不回放与当前模型绑定的推理强度且不包含会话或密钥`() {
        store.put(
            "opencode",
            AgentDraftCapabilityCatalog(
                configuration = listOf(
                    AgentConfigOption.Select(
                        id = "language",
                        name = "语言",
                        category = AgentConfigCategory("language"),
                        currentValue = "zh",
                        choices = listOf(AgentConfigChoice("zh", "中文"))
                    ),
                    AgentConfigOption.Select(
                        id = "thought",
                        name = "推理强度",
                        category = AgentConfigCategory.ThoughtLevel,
                        currentValue = "medium",
                        choices = listOf(
                            AgentConfigChoice("medium", "中"),
                            AgentConfigChoice("high", "高")
                        )
                    )
                ),
                modes = listOf(AgentMode("build", "构建"), AgentMode("plan", "计划")),
                currentModeId = "build",
                commands = listOf(AgentCommand("review", "审查当前改动", "可选路径"))
            )
        )

        val restored = store.catalog("opencode")!!

        assertEquals(listOf("language"), restored.configuration.map { it.id })
        assertEquals(listOf("build", "plan"), restored.modes.map { it.id })
        assertEquals("build", restored.currentModeId)
        assertEquals(listOf("review"), restored.commands.map { it.name })
        val raw = context.getSharedPreferences(
            "kite_agent_draft_capability_cache",
            Context.MODE_PRIVATE
        ).getString("payload", "").orEmpty()
        assertFalse(raw.contains("sessionId"))
        assertFalse(raw.contains("apiKey", ignoreCase = true))
    }

    @Test
    fun `真实空目录会清除旧缓存`() {
        store.put(
            "opencode",
            AgentDraftCapabilityCatalog(modes = listOf(AgentMode("build", "构建")))
        )

        store.put("opencode", AgentDraftCapabilityCatalog())

        assertEquals(null, store.catalog("opencode"))
    }

    @Test
    fun `合同升级后不会回放缺少来源和新权限档位的旧缓存`() {
        context.getSharedPreferences(
            "kite_agent_draft_capability_cache",
            Context.MODE_PRIVATE,
        ).edit().putString(
            "payload",
            """{"version":2,"catalogs":{"codex":{"configuration":[]}}}""",
        ).commit()

        assertEquals(null, store.catalog("codex"))
    }
}
