package com.kite.app.agent.registration

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.kite.app.resources.KiteResourceInstallStore
import com.kite.app.resources.KiteResourceManifestLoader
import com.kite.app.run.CardRunStore
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class KiteAgentRegistryTest {
    private val context by lazy { ApplicationProvider.getApplicationContext<Context>() }
    private val installStore by lazy { KiteResourceInstallStore(context) }
    private val customStore by lazy { KiteCustomAgentRegistrationStore(context) }
    private val registry by lazy {
        KiteAgentRegistry(
            context = context,
            manifestLoader = KiteResourceManifestLoader(context),
            installStore = installStore,
            customStore = customStore,
            resourceRegistrationSource = { listOf(openCodeRegistration()) },
            runningProviderIds = { emptySet() }
        )
    }

    @Before
    fun setUp() {
        context.getSharedPreferences(CUSTOM_PREFERENCES, Context.MODE_PRIVATE).edit().clear().commit()
        installStore.clear(OPEN_CODE_RESOURCE)
        CardRunStore.resetForTest()
    }

    @After
    fun tearDown() {
        customStore.remove(CUSTOM_AGENT_ID)
        installStore.clear(OPEN_CODE_RESOURCE)
        CardRunStore.resetForTest()
    }

    @Test
    fun resourceDefinitionSurvivesUninstallAndBecomesAvailableAfterReinstall() {
        val beforeInstall = requireNotNull(registry.snapshot().entry(OPEN_CODE_AGENT_ID))
        assertEquals(AgentInstallationStatus.NotInstalled, beforeInstall.installationStatus)

        installStore.markInstalled(OPEN_CODE_RESOURCE, "1.18.5", null, "test")
        val installed = requireNotNull(registry.snapshot().entry(OPEN_CODE_AGENT_ID))
        assertEquals(AgentInstallationStatus.Installed, installed.installationStatus)
        assertTrue(installed.canOpen)

        installStore.clear(OPEN_CODE_RESOURCE)
        val afterUninstall = requireNotNull(registry.snapshot().entry(OPEN_CODE_AGENT_ID))
        assertEquals(AgentInstallationStatus.NotInstalled, afterUninstall.installationStatus)
        assertEquals("OpenCode", afterUninstall.registration.definition.displayName)

        installStore.markInstalled(OPEN_CODE_RESOURCE, "1.18.6", null, "reinstall")
        assertEquals(
            AgentInstallationStatus.Installed,
            registry.snapshot().entry(OPEN_CODE_AGENT_ID)?.installationStatus
        )
    }

    @Test
    fun customAgentIsRegisteredWithoutPretendingToBeAnInstalledResource() {
        val custom = AgentRegistration(
            definition = AgentDefinition(CUSTOM_AGENT_ID, "OpenCode"),
            source = AgentRegistrationSource.Custom,
            launch = AgentLaunchSpec.Attach(
                providerId = "custom-provider",
                protocol = "acp",
                transport = "socket",
                connectionReference = "connections/custom-provider"
            )
        )

        assertTrue(registry.registerCustom(custom) is AgentRegistrationWriteResult.Accepted)
        val entry = registry.snapshot().entry(CUSTOM_AGENT_ID)
        assertNotNull(entry)
        assertEquals(AgentInstallationStatus.NotApplicable, entry?.installationStatus)
        assertEquals(AgentLaunchStatus.Unsupported, entry?.launchStatus)
        assertEquals(false, entry?.canOpen)
        assertEquals("OpenCode", entry?.registration?.definition?.displayName)
        assertTrue(registry.snapshot().entries.any { it.registration.definition.agentId == OPEN_CODE_AGENT_ID })
    }

    @Test
    fun attachAgentBecomesOpenableOnlyWhenItsConnectionAdapterIsAvailable() {
        val attachReference = "connections/custom-provider"
        val attachRegistration = AgentRegistration(
            definition = AgentDefinition(CUSTOM_AGENT_ID, "Remote Agent"),
            source = AgentRegistrationSource.Custom,
            launch = AgentLaunchSpec.Attach(
                providerId = "custom-provider",
                protocol = "acp",
                transport = "socket",
                connectionReference = attachReference
            )
        )
        val unavailable = KiteAgentRegistry(
            context = context,
            installStore = installStore,
            customStore = customStore,
            resourceRegistrationSource = { listOf(attachRegistration) },
            attachAvailable = { false },
            runningProviderIds = { emptySet() }
        ).snapshot().entry(CUSTOM_AGENT_ID)
        val available = KiteAgentRegistry(
            context = context,
            installStore = installStore,
            customStore = customStore,
            resourceRegistrationSource = { listOf(attachRegistration) },
            attachAvailable = { it == attachReference },
            runningProviderIds = { emptySet() }
        ).snapshot().entry(CUSTOM_AGENT_ID)

        assertEquals(AgentLaunchStatus.Unsupported, unavailable?.launchStatus)
        assertEquals(false, unavailable?.canOpen)
        assertEquals(AgentLaunchStatus.Ready, available?.launchStatus)
        assertEquals(true, available?.canOpen)
    }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun resourceInstallSignalNamesOnlyAgentsDeclaredByAffectedResource() = runTest {
        val observed = async(start = CoroutineStart.UNDISPATCHED) {
            registry.signals.first { OPEN_CODE_AGENT_ID in it.affectedAgentIds }
        }
        runCurrent()

        installStore.markInstalled(OPEN_CODE_RESOURCE, "1.18.5", null, "test")

        val signal = observed.await()
        assertEquals(OPEN_CODE_RESOURCE, signal.resourceId)
        assertEquals(listOf(OPEN_CODE_AGENT_ID), signal.affectedAgentIds)
    }

    private companion object {
        const val OPEN_CODE_RESOURCE = "kite.opencode"
        const val OPEN_CODE_AGENT_ID = "opencode"
        const val CUSTOM_AGENT_ID = "custom-agent"
        const val CUSTOM_PREFERENCES = "kite_custom_agent_registrations"

        fun openCodeRegistration() = AgentRegistration(
            definition = AgentDefinition(OPEN_CODE_AGENT_ID, "OpenCode"),
            source = AgentRegistrationSource.Resource(OPEN_CODE_RESOURCE),
            launch = AgentLaunchSpec.Managed(
                providerId = "opencode",
                protocol = "acp",
                transport = "stdio",
                argv = listOf("opencode", "acp")
            )
        )
    }
}
