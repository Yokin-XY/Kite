package com.kite.app.agent.config

import com.kite.app.agent.contract.AgentCapabilities
import com.kite.app.agent.contract.AgentClientEndpoint
import com.kite.app.agent.contract.AgentClientInfo
import com.kite.app.agent.contract.AgentConfigCategory
import com.kite.app.agent.contract.AgentConfigChoice
import com.kite.app.agent.contract.AgentConfigOption
import com.kite.app.agent.contract.AgentConfigValue
import com.kite.app.agent.contract.AgentConnectionRequest
import com.kite.app.agent.contract.AgentExistingSessionRequest
import com.kite.app.agent.contract.AgentNewSessionRequest
import com.kite.app.agent.contract.AgentOperationResult
import com.kite.app.agent.contract.AgentPermissionKind
import com.kite.app.agent.contract.AgentPermissionOption
import com.kite.app.agent.contract.AgentPermissionOutcome
import com.kite.app.agent.contract.AgentPermissionRequest
import com.kite.app.agent.contract.AgentPromptRequest
import com.kite.app.agent.contract.AgentProviderInfo
import com.kite.app.agent.contract.AgentSessionListRequest
import com.kite.app.agent.contract.AgentSessionPage
import com.kite.app.agent.contract.AgentSessionSnapshot
import com.kite.app.agent.contract.AgentToolCallPatch
import com.kite.app.agent.contract.AgentTurnResult
import com.kite.app.agent.contract.KiteAgentConnection
import com.kite.app.agent.contract.KiteAgentProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentSessionConfigurationOverlayProviderTest {
    @Test
    fun sessionPermissionOptionCanSeedAnEmptyDraftBeforeAgentSessionExists() {
        val mediated = mediatedSessionPermissionControl(
            AgentPermissionLevel.Restricted,
            AgentPermissionLevel.Approval,
            AgentPermissionLevel.Full,
        ).option()

        val merged = mergeAgentSessionConfigurationOverlay(emptyList(), listOf(mediated))

        assertEquals(listOf(SESSION_PERMISSION_CONFIG_ID), merged.options.map { it.id })
        assertEquals(false, merged.protocolPermissionPublished)
    }

    @Test
    fun protocolModelWinsByCategoryWhileSessionPermissionStillFillsItsGap() {
        val mediated = mediatedSessionPermissionControl(
            AgentPermissionLevel.Approval,
            AgentPermissionLevel.Full,
        ).option()
        val protocolModel = modelOption("acp.session.model", "protocol/model")

        val merged = mergeAgentSessionConfigurationOverlay(
            listOf(protocolModel),
            listOf(modelOption(NATIVE_MODEL_CONFIG_ID, "native/model"), mediated),
        )

        assertEquals(
            listOf("acp.session.model", SESSION_PERMISSION_CONFIG_ID),
            merged.options.map { it.id },
        )
        assertEquals(setOf(AgentConfigCategory.Model), merged.protocolCategoriesPublished)
    }

    @Test
    fun sessionPermissionSelectionStaysInsideOneSessionAndNeverWritesPersistentAdapter() = runTest {
        val adapter = FakeAdapter()
        val delegate = FakeProvider(emptyList())
        val connection = connect(delegate, adapter)
        val first = (connection.newSession(AgentNewSessionRequest("/workspace")) as AgentOperationResult.Success).value
        val second = (connection.newSession(AgentNewSessionRequest("/workspace")) as AgentOperationResult.Success).value

        val selected = connection.setConfiguration(
            first.id,
            SESSION_PERMISSION_CONFIG_ID,
            AgentConfigValue.Select(FULL_ID),
        ) as AgentOperationResult.Success

        assertEquals(0, adapter.persistentApplyCount)
        assertEquals(0, delegate.connection.setConfigurationCount)
        assertEquals(FULL_ID, selected.value.permissionCurrentValue())
        assertEquals(APPROVAL_ID, second.configuration.permissionCurrentValue())
    }

    @Test
    fun protocolPermissionOptionWinsAndMediatedControlStaysIdle() = runTest {
        val protocolPermission = permissionOption("acp.permission", "ask")
        val adapter = FakeAdapter()
        val delegate = FakeProvider(listOf(protocolPermission))
        val connection = connect(delegate, adapter)

        val opened = connection.newSession(AgentNewSessionRequest("/workspace")) as AgentOperationResult.Success
        assertEquals(listOf("acp.permission"), opened.value.configuration.map { it.id })

        connection.setConfiguration(
            opened.value.id,
            "acp.permission",
            AgentConfigValue.Select("allow"),
        ) as AgentOperationResult.Success

        assertEquals(1, delegate.connection.setConfigurationCount)
    }

    @Test
    fun fullSessionPermissionSelectsAllowOnceButNeverAllowAlways() = runTest {
        val adapter = FakeAdapter()
        val delegate = FakeProvider(emptyList())
        val endpoint = RecordingEndpoint()
        val connection = connect(delegate, adapter, endpoint)
        val opened = (connection.newSession(AgentNewSessionRequest("/workspace")) as AgentOperationResult.Success).value
        connection.setConfiguration(opened.id, SESSION_PERMISSION_CONFIG_ID, AgentConfigValue.Select(FULL_ID))

        val outcome = delegate.requestPermission(permissionRequest(opened.id))

        assertEquals(AgentPermissionOutcome.Selected("allow-once"), outcome)
        assertEquals(0, endpoint.permissionRequests)
    }

    @Test
    fun restrictedSessionPermissionSelectsRejectOnceButNeverRejectAlways() = runTest {
        val adapter = FakeAdapter()
        val delegate = FakeProvider(emptyList())
        val endpoint = RecordingEndpoint()
        val connection = connect(delegate, adapter, endpoint)
        val opened = (connection.newSession(AgentNewSessionRequest("/workspace")) as AgentOperationResult.Success).value
        connection.setConfiguration(opened.id, SESSION_PERMISSION_CONFIG_ID, AgentConfigValue.Select(RESTRICTED_ID))

        val outcome = delegate.requestPermission(permissionRequest(opened.id))

        assertEquals(AgentPermissionOutcome.Selected("reject-once"), outcome)
        assertEquals(0, endpoint.permissionRequests)
    }

    @Test
    fun approvalDelegatesToUiAndMissingOneShotOptionAlsoFallsBackToUi() = runTest {
        val adapter = FakeAdapter()
        val delegate = FakeProvider(emptyList())
        val endpoint = RecordingEndpoint()
        val connection = connect(delegate, adapter, endpoint)
        val opened = (connection.newSession(AgentNewSessionRequest("/workspace")) as AgentOperationResult.Success).value

        assertEquals(AgentPermissionOutcome.Cancelled, delegate.requestPermission(permissionRequest(opened.id)))
        connection.setConfiguration(opened.id, SESSION_PERMISSION_CONFIG_ID, AgentConfigValue.Select(FULL_ID))
        val permanentOnly = permissionRequest(opened.id).copy(
            options = listOf(AgentPermissionOption("allow-always", "始终允许", AgentPermissionKind.AllowAlways))
        )
        assertEquals(AgentPermissionOutcome.Cancelled, delegate.requestPermission(permanentOnly))
        assertEquals(2, endpoint.permissionRequests)
    }

    @Test
    fun nativePermissionCatalogKeepsOfficialIdsAndDoesNotForgeSmartApproval() {
        val profiles = listOf(
            AgentPermissionProfileSummary(
                id = "manual",
                displayName = "手动审批",
                effect = AgentSessionConfigurationEffect.NewSession,
                level = AgentPermissionLevel.Approval,
            ),
            AgentPermissionProfileSummary(
                id = "smart",
                displayName = "智能审批",
                effect = AgentSessionConfigurationEffect.NewSession,
                level = AgentPermissionLevel.Smart,
            ),
            AgentPermissionProfileSummary(
                id = "off",
                displayName = "关闭审批",
                effect = AgentSessionConfigurationEffect.NewSession,
                level = AgentPermissionLevel.Full,
            ),
        )
        val control = mediatedSessionPermissionControl(
            profiles = profiles,
            handlingByProfileId = mapOf(
                "manual" to AgentSessionPermissionHandling.AskUser,
                "smart" to AgentSessionPermissionHandling.PreserveAgentDecision,
                "off" to AgentSessionPermissionHandling.AllowRequest,
            ),
            initialProfileId = "smart",
        )

        assertEquals(listOf("manual", "smart", "off"), control.profiles.map { it.id })
        assertEquals(listOf("审批", "智能", "完全"), control.option().choices.map { it.name })
        assertEquals("smart", control.option().currentValue)
        assertNull(control.resolve("smart", permissionRequest("session-1")))
    }

    private suspend fun connect(
        delegate: FakeProvider,
        adapter: FakeAdapter,
        endpoint: RecordingEndpoint = RecordingEndpoint(),
    ): KiteAgentConnection {
        val provider = AgentSessionConfigurationOverlayProvider(delegate, "fake-agent", adapter)
        val result = provider.connect(
            AgentConnectionRequest(AgentClientInfo("test", "1")),
            AgentClientEndpoint(eventSink = { _, _ -> }, permissionHandler = endpoint::requestPermission),
        )
        assertTrue(result is AgentOperationResult.Success)
        return (result as AgentOperationResult.Success).value
    }

    private class RecordingEndpoint {
        var permissionRequests: Int = 0

        suspend fun requestPermission(request: AgentPermissionRequest): AgentPermissionOutcome {
            permissionRequests += 1
            return AgentPermissionOutcome.Cancelled
        }
    }

    private class FakeAdapter : AgentConfigAdapter {
        override val adapterId: String = "fake"
        var persistentApplyCount: Int = 0

        override fun capabilities() = AgentConfigCapabilities(
            supported = setOf(AgentPersistentConfigCapability.PermissionProfiles),
        )

        override fun sessionPermissionControl(): AgentSessionPermissionControl =
            mediatedSessionPermissionControl(
                AgentPermissionLevel.Restricted,
                AgentPermissionLevel.Approval,
                AgentPermissionLevel.Full,
            )

        override suspend fun readSessionConfiguration(agentId: String): List<AgentConfigOption> =
            listOf(sessionPermissionControl().option())

        override suspend fun discover(agentId: String) = AgentConfigDiscovery(
            agentId = agentId,
            adapterId = adapterId,
            state = AgentConfigDiscoveryState.Ready,
        )

        override suspend fun readLive(agentId: String): AgentConfigReadResult = AgentConfigReadResult.Failed("unused")

        override fun validate(request: AgentConfigApplyRequest): List<AgentConfigValidationProblem> = emptyList()

        override suspend fun apply(request: AgentConfigApplyRequest): AgentConfigApplyResult {
            persistentApplyCount += 1
            return AgentConfigApplyResult.Failed("unused", restored = false)
        }
    }

    private class FakeProvider(configuration: List<AgentConfigOption>) : KiteAgentProvider {
        override val id: String = "fake"
        val connection = FakeConnection(configuration)
        private lateinit var endpoint: AgentClientEndpoint

        override suspend fun connect(
            request: AgentConnectionRequest,
            client: AgentClientEndpoint,
        ): AgentOperationResult<KiteAgentConnection> {
            endpoint = client
            return AgentOperationResult.Success(connection)
        }

        suspend fun requestPermission(request: AgentPermissionRequest): AgentPermissionOutcome =
            endpoint.permissionHandler.request(request)
    }

    private class FakeConnection(
        private var configuration: List<AgentConfigOption>,
    ) : KiteAgentConnection {
        override val provider = AgentProviderInfo("fake", "Fake")
        override val capabilities = AgentCapabilities()
        var setConfigurationCount: Int = 0
        private var sessionSequence = 0

        override suspend fun newSession(request: AgentNewSessionRequest): AgentOperationResult<AgentSessionSnapshot> =
            AgentOperationResult.Success(
                AgentSessionSnapshot("session-${++sessionSequence}", configuration = configuration)
            )

        override suspend fun setConfiguration(
            sessionId: String,
            configId: String,
            value: AgentConfigValue,
        ): AgentOperationResult<List<AgentConfigOption>> {
            setConfigurationCount += 1
            configuration = configuration.map { option ->
                if (option is AgentConfigOption.Select && option.id == configId && value is AgentConfigValue.Select) {
                    option.copy(currentValue = value.value)
                } else {
                    option
                }
            }
            return AgentOperationResult.Success(configuration)
        }

        override suspend fun loadSession(request: AgentExistingSessionRequest) = unsupported<AgentSessionSnapshot>()
        override suspend fun listSessions(request: AgentSessionListRequest) = unsupported<AgentSessionPage>()
        override suspend fun resumeSession(request: AgentExistingSessionRequest) = unsupported<AgentSessionSnapshot>()
        override suspend fun forkSession(request: AgentExistingSessionRequest) = unsupported<AgentSessionSnapshot>()
        override suspend fun closeSession(sessionId: String) = unsupported<Unit>()
        override suspend fun deleteSession(sessionId: String) = unsupported<Unit>()
        override suspend fun prompt(request: AgentPromptRequest) = unsupported<AgentTurnResult>()
        override suspend fun cancel(sessionId: String) = unsupported<Unit>()
        override suspend fun disconnect() = Unit

        private fun <T> unsupported(): AgentOperationResult<T> = AgentOperationResult.Unsupported("unused")
    }

    private companion object {
        const val RESTRICTED_ID = "kite.permission.restricted"
        const val APPROVAL_ID = "kite.permission.approval"
        const val FULL_ID = "kite.permission.full"

        fun List<AgentConfigOption>.permissionCurrentValue(): String =
            (single { it.id == SESSION_PERMISSION_CONFIG_ID } as AgentConfigOption.Select).currentValue

        fun modelOption(id: String, current: String) = AgentConfigOption.Select(
            id = id,
            name = "模型",
            category = AgentConfigCategory.Model,
            currentValue = current,
            choices = listOf(AgentConfigChoice(current, current)),
        )

        fun permissionOption(id: String, current: String) = AgentConfigOption.Select(
            id = id,
            name = "权限",
            category = AgentConfigCategory.Permission,
            currentValue = current,
            choices = listOf(
                AgentConfigChoice("ask", "询问"),
                AgentConfigChoice("allow", "允许"),
            ),
        )

        fun permissionRequest(sessionId: String) = AgentPermissionRequest(
            sessionId = sessionId,
            toolCall = AgentToolCallPatch("tool-1", "写入文件"),
            options = listOf(
                AgentPermissionOption("allow-always", "始终允许", AgentPermissionKind.AllowAlways),
                AgentPermissionOption("allow-once", "允许一次", AgentPermissionKind.AllowOnce),
                AgentPermissionOption("reject-always", "始终拒绝", AgentPermissionKind.RejectAlways),
                AgentPermissionOption("reject-once", "拒绝一次", AgentPermissionKind.RejectOnce),
            ),
        )
    }
}
