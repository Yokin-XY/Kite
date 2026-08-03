package com.kite.app.agent.config

import com.kite.app.agent.contract.AgentConfigCategory
import com.kite.app.agent.contract.AgentConfigOption
import com.kite.app.agent.contract.AgentModelSource
import com.kite.app.agent.sdk.configuration.AgentConfigurationIntent
import com.kite.app.agent.sdk.configuration.AgentConfigurationTarget
import com.kite.app.agent.sdk.configuration.AgentModelSelection
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdapterBackedAgentConfigurationApiTest {
    @Test
    fun `provider presets require both a matching adapter and provider capability`() {
        val unsupported = RecordingAdapter()
        val supported = RecordingAdapter(
            adapterId = "opencode",
            supported = setOf(AgentPersistentConfigCapability.Provider),
        )

        assertTrue(
            AdapterBackedAgentConfigurationApi(AgentConfigAdapterRegistry(listOf(unsupported)))
                .providerPresets(AgentConfigurationTarget("agent", unsupported.adapterId))
                .isEmpty(),
        )
        assertTrue(
            AdapterBackedAgentConfigurationApi(AgentConfigAdapterRegistry(listOf(supported)))
                .providerPresets(AgentConfigurationTarget("agent", supported.adapterId))
                .size >= 14,
        )
    }

    @Test
    fun `model and permission intents are translated below the sdk boundary`() = runTest {
        val adapter = RecordingAdapter()
        val api = AdapterBackedAgentConfigurationApi(AgentConfigAdapterRegistry(listOf(adapter)))
        val target = AgentConfigurationTarget("agent", adapter.adapterId)

        val mutation = api.apply(
            target,
            expectedRevision = "1",
            intents = listOf(
                AgentConfigurationIntent.SelectModel(
                    AgentModelSelection(
                        configId = "model",
                        sourceId = "zhipu",
                        modelId = "glm-5.2",
                        nativeValue = "zhipu/glm-5.2",
                        source = AgentModelSource.UserConfigured,
                    )
                ),
                AgentConfigurationIntent.SetPermission("ask"),
            ),
        )

        assertTrue(mutation.result is AgentConfigApplyResult.Applied)
        assertEquals(
            listOf(
                AgentPersistentConfigChange.SelectProvider("zhipu", "glm-5.2"),
                AgentPersistentConfigChange.SetPermissionProfile("ask"),
            ),
            adapter.lastRequest?.changes,
        )
    }

    @Test
    fun `non custom model uses adapter mapping without ui branching`() = runTest {
        val adapter = RecordingAdapter()
        val api = AdapterBackedAgentConfigurationApi(AgentConfigAdapterRegistry(listOf(adapter)))

        api.apply(
            AgentConfigurationTarget("agent", adapter.adapterId),
            expectedRevision = "1",
            intents = listOf(
                AgentConfigurationIntent.SelectModel(
                    AgentModelSelection(
                        configId = "model",
                        sourceId = "opencode",
                        modelId = "free-small",
                        nativeValue = "opencode/free-small",
                        source = AgentModelSource.Free,
                    )
                )
            ),
        )

        assertEquals(
            AgentPersistentConfigChange.SetDefaultModel("opencode/free-small"),
            adapter.lastRequest?.changes?.single(),
        )
    }

    @Test
    fun `failed mutation rereads current native fact inside the sdk`() = runTest {
        val adapter = RecordingAdapter(failApply = true)
        val api = AdapterBackedAgentConfigurationApi(AgentConfigAdapterRegistry(listOf(adapter)))

        val mutation = api.apply(
            AgentConfigurationTarget("agent", adapter.adapterId),
            expectedRevision = "1",
            intents = listOf(AgentConfigurationIntent.SetPermission("ask")),
        )

        assertTrue(mutation.result is AgentConfigApplyResult.Failed)
        assertTrue(mutation.current is AgentConfigReadResult.Ready)
        assertEquals(1, adapter.backfillCount)
    }

    private class RecordingAdapter(
        private val failApply: Boolean = false,
        override val adapterId: String = "recording",
        private val supported: Set<AgentPersistentConfigCapability> = setOf(
            AgentPersistentConfigCapability.DefaultModel,
            AgentPersistentConfigCapability.PermissionProfiles,
        ),
    ) : AgentConfigAdapter {
        var lastRequest: AgentConfigApplyRequest? = null
        var backfillCount = 0

        override fun capabilities(): AgentConfigCapabilities = AgentConfigCapabilities(supported)

        override fun defaultModelChange(
            option: AgentConfigOption.Select,
        ): AgentPersistentConfigChange.SetDefaultModel =
            AgentPersistentConfigChange.SetDefaultModel(option.currentValue)

        override suspend fun discover(agentId: String): AgentConfigDiscovery = AgentConfigDiscovery(
            agentId,
            adapterId,
            AgentConfigDiscoveryState.Ready,
            "/config",
            writable = true,
        )

        override suspend fun readLive(agentId: String): AgentConfigReadResult = AgentConfigReadResult.Ready(snapshot())

        override fun validate(request: AgentConfigApplyRequest): List<AgentConfigValidationProblem> = emptyList()

        override suspend fun apply(request: AgentConfigApplyRequest): AgentConfigApplyResult {
            lastRequest = request
            return if (failApply) AgentConfigApplyResult.Failed("failed", restored = true)
            else AgentConfigApplyResult.Applied(snapshot(), null)
        }

        override suspend fun backfill(agentId: String): AgentConfigReadResult {
            backfillCount++
            return AgentConfigReadResult.Ready(snapshot().copy(revision = "2"))
        }

        private fun snapshot() = AgentLiveConfigSnapshot(
            agentId = "agent",
            adapterId = adapterId,
            revision = "1",
            displayLocation = "/config",
        )
    }
}
