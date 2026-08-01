package com.kite.app.agent.registration

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class KiteCustomAgentRegistrationStoreTest {
    private val context by lazy { ApplicationProvider.getApplicationContext<Context>() }

    @Before
    @After
    fun clearStore() {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun customManagedAndAttachRegistrationsPersistWithoutResourceState() {
        val store = KiteCustomAgentRegistrationStore(context)
        val managed = registration(
            agentId = "my-agent",
            displayName = "我的 Agent",
            launch = AgentLaunchSpec.Managed(
                "my-provider",
                "acp",
                "stdio",
                listOf("my-agent", "acp"),
                setOf("no_child_process", "verified_native_imports"),
                mapOf("pythonAbi" to "cpython-314-aarch64-linux-gnu"),
            ),
            configAdapterId = "custom-config",
            sessionAdapterId = "custom-sessions"
        )
        val attach = registration(
            agentId = "remote-agent",
            displayName = "我的 Agent",
            launch = AgentLaunchSpec.Attach("remote-provider", "acp", "socket", "connections/remote")
        )

        assertTrue(store.register(managed) is AgentRegistrationWriteResult.Accepted)
        assertTrue(store.register(attach) is AgentRegistrationWriteResult.Accepted)

        val restored = KiteCustomAgentRegistrationStore(context).snapshot()
        assertEquals(listOf("my-agent", "remote-agent"), restored.map { it.definition.agentId })
        assertTrue(restored.last().launch is AgentLaunchSpec.Attach)
        assertEquals("custom-config", restored.first().configAdapterId)
        assertEquals("custom-sessions", restored.first().sessionAdapterId)
        assertEquals(
            setOf("no_child_process", "verified_native_imports"),
            (restored.first().launch as AgentLaunchSpec.Managed).runtimeGuarantees,
        )
        assertEquals(
            mapOf("pythonAbi" to "cpython-314-aarch64-linux-gnu"),
            (restored.first().launch as AgentLaunchSpec.Managed).runtimeGuaranteeEvidence,
        )
    }

    @Test
    fun resourceAgentIdAndExistingCustomIdAreRejectedWithoutUsingDisplayName() {
        val store = KiteCustomAgentRegistrationStore(context)
        val first = registration(
            agentId = "my-agent",
            displayName = "原名称",
            launch = AgentLaunchSpec.Managed("my-provider", "acp", "stdio", listOf("my-agent"))
        )
        val renamedDuplicate = first.copy(definition = first.definition.copy(displayName = "新名称"))
        val resourceConflict = registration(
            agentId = "opencode",
            displayName = "随便什么显示名",
            launch = AgentLaunchSpec.Managed("other-provider", "acp", "stdio", listOf("other"))
        )

        assertTrue(store.register(first) is AgentRegistrationWriteResult.Accepted)
        assertTrue(store.register(renamedDuplicate) is AgentRegistrationWriteResult.Rejected)
        assertTrue(
            store.register(resourceConflict, reservedAgentIds = setOf("opencode"))
                is AgentRegistrationWriteResult.Rejected
        )
    }

    @Test
    fun unknownRuntimeGuaranteeIsRejectedInsteadOfDowngradedToEmpty() {
        val store = KiteCustomAgentRegistrationStore(context)
        val registration = registration(
            agentId = "unsafe-agent",
            displayName = "Unsafe",
            launch = AgentLaunchSpec.Managed(
                "unsafe-provider",
                "acp",
                "stdio",
                listOf("python3", "agent.py"),
                setOf("trust_me"),
            ),
        )

        assertTrue(store.register(registration) is AgentRegistrationWriteResult.Rejected)
        assertTrue(store.snapshot().isEmpty())
    }

    private fun registration(
        agentId: String,
        displayName: String,
        launch: AgentLaunchSpec,
        configAdapterId: String? = null,
        sessionAdapterId: String? = null
    ) = AgentRegistration(
        definition = AgentDefinition(agentId, displayName),
        source = AgentRegistrationSource.Custom,
        launch = launch,
        configAdapterId = configAdapterId,
        sessionAdapterId = sessionAdapterId
    )

    private companion object {
        const val PREFERENCES = "kite_custom_agent_registrations"
    }
}
