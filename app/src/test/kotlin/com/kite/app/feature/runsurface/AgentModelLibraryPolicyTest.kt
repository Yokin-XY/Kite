package com.kite.app.feature.runsurface

import com.kite.app.agent.config.AgentLiveConfigSnapshot
import com.kite.app.agent.config.AgentProviderModelSummary
import com.kite.app.agent.config.AgentProviderSummary
import com.kite.app.agent.contract.AgentConfigCategory
import com.kite.app.agent.contract.AgentConfigChoice
import com.kite.app.agent.contract.AgentConfigOption
import com.kite.app.agent.registration.AgentOfficialAccountCommand
import com.kite.app.agent.registration.AgentOfficialAccountSpec
import com.kite.app.agent.store.AgentModelLibraryStore
import com.kite.app.agent.store.AgentModelLibraryProviderPreference
import com.kite.app.agent.store.AgentModelLibrarySnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentModelLibraryPolicyTest {
    @Test
    fun `隐藏供应商不会从会话选择中移除当前模型供应商`() {
        val option = modelOption(current = "zhipu/glm-5.2")
        val library = AgentModelLibrarySnapshot(
            providers = mapOf(
                "zhipu" to AgentModelLibraryProviderPreference(visibleInConversation = false),
                "mimo" to AgentModelLibraryProviderPreference(visibleInConversation = false)
            )
        )

        val filtered = AgentModelLibraryPolicy.filterConversationModelOption(option, library, "zhipu")

        assertEquals(
            listOf("zhipu/glm-5.2", "zhipu/glm-5.0", "free/free-small"),
            filtered.choices.map { it.value }
        )
    }

    @Test
    fun `Agent发现但未进入持久供应商的分组投影为免费只读来源`() {
        val snapshot = AgentLiveConfigSnapshot(
            agentId = "opencode",
            adapterId = "test",
            revision = "1",
            displayLocation = "/config",
            activeProviderId = "zhipu",
            providers = listOf(
                AgentProviderSummary(
                    id = "zhipu",
                    displayName = "智谱 GLM",
                    models = listOf(AgentProviderModelSummary("glm-5.2"))
                )
            )
        )

        val providers = AgentModelLibraryPolicy.projectProviders(snapshot, modelOption(), AgentModelLibrarySnapshot())

        assertEquals(listOf("zhipu", "mimo", "builtin"), providers.map { it.id })
        assertEquals(AgentModelProviderSource.Configured, providers.first().source)
        assertTrue(providers.drop(1).all { it.source == AgentModelProviderSource.DiscoveredFree })
        assertTrue(providers.last().editableProvider == null)
    }

    @Test
    fun `供应商显示偏好只在供应商层过滤并保留其全部模型`() {
        val option = modelOption()
        val library = AgentModelLibrarySnapshot(
            providers = mapOf("mimo" to AgentModelLibraryProviderPreference(visibleInConversation = false))
        )

        val filtered = AgentModelLibraryPolicy.filterConversationModelOption(option, library)

        assertFalse(filtered.choices.any { it.groupId == "mimo" })
        assertEquals(2, filtered.choices.count { it.groupId == "zhipu" })
    }

    @Test
    fun `自定义模型名称只替换展示文案而不改变请求值`() {
        val library = AgentModelLibrarySnapshot(
            providers = mapOf(
                "zhipu" to AgentModelLibraryProviderPreference(
                    modelDisplayNames = mapOf("glm-5.2" to "日常模型")
                )
            )
        )

        val filtered = AgentModelLibraryPolicy.filterConversationModelOption(modelOption(), library)
        val choice = filtered.choices.single { it.value == "zhipu/glm-5.2" }

        assertEquals("日常模型", choice.name)
        assertEquals("glm-5.2", choice.description)
        assertEquals("zhipu/glm-5.2", choice.value)
    }

    @Test
    fun `原生配置只返回短模型名时仍映射到当前供应商模型`() {
        val snapshot = AgentLiveConfigSnapshot(
            agentId = "opencode",
            adapterId = "test",
            revision = "1",
            displayLocation = "/config",
            activeProviderId = "zhipu",
            defaultModel = "glm-5.2",
            providers = listOf(
                AgentProviderSummary(
                    id = "zhipu",
                    displayName = "智谱 GLM",
                    models = listOf(AgentProviderModelSummary("glm-5.2"))
                )
            )
        )

        val provider = AgentModelLibraryPolicy.projectProviders(
            snapshot = snapshot,
            modelOption = null,
            library = AgentModelLibrarySnapshot()
        ).single()

        assertEquals("zhipu/glm-5.2", provider.selectedModelValue)
    }

    @Test
    fun `后台默认模型不会被当前会话中另一供应商模型覆盖`() {
        val snapshot = AgentLiveConfigSnapshot(
            agentId = "opencode",
            adapterId = "test",
            revision = "1",
            displayLocation = "/config",
            activeProviderId = "zhipu",
            defaultModel = "glm-5.2",
            providers = listOf(
                AgentProviderSummary(
                    id = "zhipu",
                    displayName = "智谱 GLM",
                    models = listOf(AgentProviderModelSummary("glm-5.2"))
                )
            )
        )

        val provider = AgentModelLibraryPolicy.projectProviders(
            snapshot = snapshot,
            modelOption = modelOption(current = "free/free-small"),
            library = AgentModelLibrarySnapshot()
        ).first { it.id == "zhipu" }

        assertEquals("zhipu/glm-5.2", provider.selectedModelValue)
    }

    @Test
    fun `当前会话模型不会让模型库出现第二个默认圆点`() {
        val snapshot = AgentLiveConfigSnapshot(
            agentId = "opencode",
            adapterId = "test",
            revision = "1",
            displayLocation = "/config",
            activeProviderId = "zhipu",
            defaultModel = "glm-5.2",
            providers = listOf(
                AgentProviderSummary(
                    id = "zhipu",
                    displayName = "智谱 GLM",
                    models = listOf(AgentProviderModelSummary("glm-5.2"))
                )
            )
        )

        val providers = AgentModelLibraryPolicy.projectProviders(
            snapshot = snapshot,
            modelOption = modelOption(current = "free/free-small"),
            library = AgentModelLibrarySnapshot()
        )

        assertEquals(
            listOf("zhipu/glm-5.2"),
            providers.mapNotNull(AgentModelProviderProjection::selectedModelValue)
        )
        assertEquals("zhipu/glm-5.2", providers.first { it.id == "zhipu" }.selectedModelValue)
        assertEquals(null, providers.first { it.id == "builtin" }.selectedModelValue)
    }

    @Test
    fun `内置免费模型可以成为唯一后台默认`() {
        val snapshot = AgentLiveConfigSnapshot(
            agentId = "opencode",
            adapterId = "test",
            revision = "2",
            displayLocation = "/config",
            activeProviderId = null,
            defaultModel = "free/free-small",
            providers = listOf(
                AgentProviderSummary(
                    id = "zhipu",
                    displayName = "智谱 GLM",
                    models = listOf(AgentProviderModelSummary("glm-5.2"))
                )
            )
        )

        val providers = AgentModelLibraryPolicy.projectProviders(
            snapshot = snapshot,
            modelOption = modelOption(current = "zhipu/glm-5.2"),
            library = AgentModelLibrarySnapshot()
        )

        assertEquals(
            listOf("free/free-small"),
            providers.mapNotNull(AgentModelProviderProjection::selectedModelValue)
        )
        assertEquals(null, providers.first { it.id == "zhipu" }.selectedModelValue)
        val builtin = providers.first { it.id == "builtin" }
        assertEquals("free/free-small", builtin.selectedModelValue)
        assertTrue(builtin.visibleInConversation)
    }

    @Test
    fun `Agent当前免费模型不会覆盖后台会话可见性偏好`() {
        val snapshot = AgentLiveConfigSnapshot(
            agentId = "opencode",
            adapterId = "test",
            revision = "1",
            displayLocation = "/config",
            activeProviderId = "zhipu",
            defaultModel = "glm-5.2",
            providers = listOf(
                AgentProviderSummary(
                    id = "zhipu",
                    displayName = "智谱 GLM",
                    models = listOf(AgentProviderModelSummary("glm-5.2"))
                )
            )
        )
        val library = AgentModelLibrarySnapshot(
            providers = mapOf(
                "builtin" to AgentModelLibraryProviderPreference(visibleInConversation = false)
            )
        )

        val builtin = AgentModelLibraryPolicy.projectProviders(
            snapshot = snapshot,
            modelOption = modelOption(current = "free/free-small"),
            library = library
        ).first { it.id == "builtin" }

        assertFalse(builtin.visibleInConversation)
    }

    @Test
    fun `官方账号投影为只读官方供应商并接管声明的模型组`() {
        val account = AgentOfficialAccountSpec(
            id = "chatgpt",
            displayName = "ChatGPT 官方",
            modelGroupIds = listOf("openai"),
            status = AgentOfficialAccountCommand(listOf("codex", "login", "status")),
            login = AgentOfficialAccountCommand(listOf("codex", "login")),
            logout = AgentOfficialAccountCommand(listOf("codex", "logout")),
        )
        val option = AgentConfigOption.Select(
            id = "model",
            name = "模型",
            category = AgentConfigCategory.Model,
            currentValue = "openai/gpt-5.6",
            choices = listOf(
                AgentConfigChoice(
                    "openai/gpt-5.6",
                    "GPT-5.6",
                    groupId = "openai",
                    groupName = "OpenAI",
                ),
                AgentConfigChoice(
                    "free/small",
                    "Free Small",
                    groupId = "free",
                    groupName = "免费模型",
                ),
            ),
        )
        val snapshot = AgentLiveConfigSnapshot(
            agentId = "codex",
            adapterId = "codex",
            revision = "1",
            displayLocation = "/config",
            defaultModel = "openai/gpt-5.6",
            providers = emptyList(),
        )

        val providers = AgentModelLibraryPolicy.projectProviders(
            snapshot = snapshot,
            modelOption = option,
            library = AgentModelLibrarySnapshot(
                providers = mapOf(
                    "__kite_official__:chatgpt" to AgentModelLibraryProviderPreference(
                        modelDisplayNames = mapOf("openai/gpt-5.6" to "日常")
                    )
                )
            ),
            officialAccounts = listOf(account),
        )

        val official = providers.single { it.source == AgentModelProviderSource.Official }
        assertEquals("__kite_official__:chatgpt", official.id)
        assertEquals("ChatGPT 官方", official.name)
        assertEquals(listOf("openai/gpt-5.6"), official.models.map { it.value })
        assertEquals(listOf("日常"), official.models.map { it.name })
        assertEquals(listOf("openai/gpt-5.6"), official.models.map { it.description })
        assertEquals(AgentModelLibraryStore.OFFICIAL_GROUP_ID, official.libraryGroupId)
        assertEquals(account, official.officialAccount)
        assertEquals(null, official.editableProvider)
        assertFalse(providers.any { it.source == AgentModelProviderSource.DiscoveredFree && it.id == "openai" })
    }

    @Test
    fun `官方来源可见性使用Kite来源ID且当前模型仍保留`() {
        val account = AgentOfficialAccountSpec(
            id = "chatgpt",
            displayName = "ChatGPT 官方",
            modelGroupIds = listOf("openai"),
            login = AgentOfficialAccountCommand(listOf("codex", "login")),
        )
        val option = AgentConfigOption.Select(
            id = "model",
            name = "模型",
            category = AgentConfigCategory.Model,
            currentValue = "free/small",
            choices = listOf(
                AgentConfigChoice("openai/gpt-5.6", "GPT-5.6", groupId = "openai"),
                AgentConfigChoice("free/small", "Free Small", groupId = "free"),
            ),
        )
        val library = AgentModelLibrarySnapshot(
            providers = mapOf(
                "__kite_official__:chatgpt" to AgentModelLibraryProviderPreference(
                    visibleInConversation = false
                )
            )
        )

        val hidden = AgentModelLibraryPolicy.filterConversationModelOption(
            option,
            library,
            officialAccounts = listOf(account),
        )
        val current = AgentModelLibraryPolicy.filterConversationModelOption(
            option.copy(currentValue = "openai/gpt-5.6"),
            library,
            officialAccounts = listOf(account),
        )

        assertEquals(listOf("free/small"), hidden.choices.map { it.value })
        assertTrue(current.choices.any { it.value == "openai/gpt-5.6" })
    }

    @Test
    fun `免费来源显示名称只替换文案并保留真实选择值`() {
        val library = AgentModelLibrarySnapshot(
            providers = mapOf(
                "builtin" to AgentModelLibraryProviderPreference(
                    modelDisplayNames = mapOf("free/free-small" to "轻量")
                )
            )
        )

        val provider = AgentModelLibraryPolicy.projectProviders(
            snapshot = AgentLiveConfigSnapshot("agent", "test", "1", "/config"),
            modelOption = modelOption(current = "zhipu/glm-5.2"),
            library = library,
        ).single { it.id == "builtin" }

        assertEquals("轻量", provider.models.single().name)
        assertEquals("free/free-small", provider.models.single().value)
        assertEquals("free/free-small", provider.models.single().description)
    }

    private fun modelOption(current: String = "zhipu/glm-5.2") = AgentConfigOption.Select(
        id = "model",
        name = "模型",
        category = AgentConfigCategory.Model,
        currentValue = current,
        choices = listOf(
            AgentConfigChoice("zhipu/glm-5.2", "GLM-5.2", groupId = "zhipu", groupName = "智谱 GLM"),
            AgentConfigChoice("zhipu/glm-5.0", "GLM-5.0", groupId = "zhipu", groupName = "智谱 GLM"),
            AgentConfigChoice("mimo/mimo-v2", "MiMo V2", groupId = "mimo", groupName = "小米 MiMo"),
            AgentConfigChoice("free/free-small", "Free Small", groupId = "builtin", groupName = "免费模型")
        )
    )
}
