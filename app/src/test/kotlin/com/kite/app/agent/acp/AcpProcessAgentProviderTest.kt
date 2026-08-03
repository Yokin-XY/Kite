@file:OptIn(com.agentclientprotocol.annotations.UnstableApi::class)

package com.kite.app.agent.acp

import com.agentclientprotocol.agent.Agent
import com.agentclientprotocol.agent.AgentInfo
import com.agentclientprotocol.agent.AgentSession
import com.agentclientprotocol.agent.AgentSupport
import com.agentclientprotocol.client.ClientInfo
import com.agentclientprotocol.common.ClientSessionOperations
import com.agentclientprotocol.common.Event
import com.agentclientprotocol.common.SessionCreationParameters
import com.agentclientprotocol.model.AgentCapabilities
import com.agentclientprotocol.model.AgentAuthCapabilities
import com.agentclientprotocol.model.AuthMethod
import com.agentclientprotocol.model.AuthMethodId
import com.agentclientprotocol.model.AuthenticateResponse
import com.agentclientprotocol.model.ContentBlock
import com.agentclientprotocol.model.Implementation
import com.agentclientprotocol.model.LATEST_PROTOCOL_VERSION
import com.agentclientprotocol.model.LogoutCapabilities
import com.agentclientprotocol.model.LogoutResponse
import com.agentclientprotocol.model.ModelId
import com.agentclientprotocol.model.ModelInfo
import com.agentclientprotocol.model.PromptResponse
import com.agentclientprotocol.model.SessionConfigId
import com.agentclientprotocol.model.SessionConfigOption as AcpSessionConfigOption
import com.agentclientprotocol.model.SessionConfigOptionCategory
import com.agentclientprotocol.model.SessionConfigOptionValue
import com.agentclientprotocol.model.SessionConfigSelectOption
import com.agentclientprotocol.model.SessionConfigSelectOptions
import com.agentclientprotocol.model.SessionConfigValueId
import com.agentclientprotocol.model.SessionId
import com.agentclientprotocol.model.SessionUpdate
import com.agentclientprotocol.model.SetSessionConfigOptionResponse
import com.agentclientprotocol.model.StopReason
import com.agentclientprotocol.protocol.Protocol
import com.agentclientprotocol.transport.StdioTransport
import com.kite.app.agent.contract.AgentClientEndpoint
import com.kite.app.agent.contract.AgentAuthenticationMethod
import com.kite.app.agent.contract.AgentClientCapabilities
import com.kite.app.agent.contract.AgentClientInfo
import com.kite.app.agent.contract.AgentConnectionRequest
import com.kite.app.agent.contract.AgentContent
import com.kite.app.agent.contract.AgentConfigCategory
import com.kite.app.agent.contract.AgentConfigOption
import com.kite.app.agent.contract.AgentConfigValue
import com.kite.app.agent.contract.AgentExistingSessionRequest
import com.kite.app.agent.contract.AgentMessageRole
import com.kite.app.agent.contract.AgentNewSessionRequest
import com.kite.app.agent.contract.AgentOperationResult
import com.kite.app.agent.contract.AgentPermissionOutcome
import com.kite.app.agent.contract.AgentPromptRequest
import com.kite.app.agent.contract.AgentSessionEvent
import com.kite.app.agent.contract.AgentSessionPhase
import com.kite.app.agent.contract.AgentSessionRenameRequest
import com.kite.app.agent.contract.AgentStopReason
import com.kite.app.agent.process.AgentProcessChannel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.yield
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonElement
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.coroutines.coroutineContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AcpProcessAgentProviderTest {
    @Test
    fun androidPrivateImageUriIsNotPublishedToAcpAgent() {
        val privateImage = AgentContent.Image(
            data = "aGVsbG8=",
            mimeType = "image/png",
            uri = "content://com.meizu.fileprovider/document/msf%3A1000006734",
        ).toAcp() as ContentBlock.Image
        val agentVisibleImage = AgentContent.Image(
            data = "aGVsbG8=",
            mimeType = "image/png",
            uri = "file:///workspace/reference.png",
        ).toAcp() as ContentBlock.Image

        assertNull(privateImage.uri)
        assertEquals("file:///workspace/reference.png", agentVisibleImage.uri)
    }

    @Test
    fun loadSessionKeepsHistoryUpdatesEmittedBeforeLoadResponse() = runBlocking {
        val fixture = AcpAgentFixture()
        val events = CopyOnWriteArrayList<AgentSessionEvent>()
        val provider = AcpProcessAgentProvider(
            descriptor = AcpProcessProviderDescriptor("fixture", "Fixture ACP"),
            launcher = AcpProcessChannelLauncher { fixture.start() },
            initializeTimeoutMs = 2_000L,
        )
        val connected = provider.connect(
            AgentConnectionRequest(AgentClientInfo("kite", "test", "Kite Test"), AgentClientCapabilities()),
            AgentClientEndpoint(
                eventSink = { _, event -> events += event },
                permissionHandler = { AgentPermissionOutcome.Cancelled },
            ),
        ) as AgentOperationResult.Success

        val loaded = connected.value.loadSession(
            AgentExistingSessionRequest("historical-session", "/workspace"),
        )

        assertTrue(loaded is AgentOperationResult.Success)
        withTimeout(2_000L) {
            while (events.count { it is AgentSessionEvent.MessageChunk } < 2) yield()
        }
        assertEquals(
            listOf(AgentMessageRole.User to "历史问题", AgentMessageRole.Assistant to "历史回答"),
            events.mapNotNull { event ->
                val chunk = event as? AgentSessionEvent.MessageChunk ?: return@mapNotNull null
                val text = chunk.content as? AgentContent.Text ?: return@mapNotNull null
                chunk.role to text.text
            },
        )
        connected.value.disconnect()
    }

    @Test
    fun officialSdkCompletesInitializeSessionPromptCancelAndStopOverLineChannel() = runBlocking {
        val fixture = AcpAgentFixture()
        val events = mutableListOf<Pair<String, AgentSessionEvent>>()
        val deletedSessions = mutableListOf<String>()
        val renamedSessions = mutableListOf<AgentSessionRenameRequest>()
        val provider = AcpProcessAgentProvider(
            descriptor = AcpProcessProviderDescriptor("fixture", "Fixture ACP"),
            launcher = AcpProcessChannelLauncher { fixture.start() },
            initializeTimeoutMs = 2_000L,
            sessionDelete = { sessionId ->
                deletedSessions += sessionId
                AgentOperationResult.Success(Unit)
            },
            sessionRename = { request ->
                renamedSessions += request
                AgentOperationResult.Success(Unit)
            },
            sessionPathMapper = AcpSessionPathMapper(
                toAgent = { path -> path.replace("/workspace", "/data/runtime/workspace") },
                fromAgent = { path -> path.replace("/data/runtime/workspace", "/workspace") },
            ),
        )

        val connected = withTimeout(5_000L) {
            provider.connect(
                AgentConnectionRequest(
                    AgentClientInfo("kite", "test", "Kite Test"),
                    AgentClientCapabilities(authentication = true)
                ),
                AgentClientEndpoint(
                    eventSink = { sessionId, event -> events += sessionId to event },
                    permissionHandler = { AgentPermissionOutcome.Cancelled }
                )
            )
        }
        assertTrue(connected is AgentOperationResult.Success)
        val connection = (connected as AgentOperationResult.Success).value
        assertEquals("fixture-agent", connection.provider.name)
        assertTrue(connection.capabilities.sessions.delete)
        assertTrue(connection.capabilities.sessions.rename)
        assertTrue(connection.capabilities.authentication.methods.single() is AgentAuthenticationMethod.AgentManaged)
        assertTrue(connection.capabilities.authentication.logout)
        assertTrue(connection.authenticate("login") is AgentOperationResult.Success)
        assertTrue(fixture.authenticated)

        val created = withTimeout(5_000L) {
            connection.newSession(AgentNewSessionRequest("/workspace", listOf("/workspace/shared")))
        }
        assertTrue(created is AgentOperationResult.Success)
        assertEquals("/data/runtime/workspace", fixture.createdSessionParameters?.cwd)
        assertEquals(
            listOf("/data/runtime/workspace/shared"),
            fixture.createdSessionParameters?.additionalDirectories,
        )
        val sessionId = (created as AgentOperationResult.Success).value.id
        val model = created.value.configuration.single { it.category == AgentConfigCategory.Model }
            as AgentConfigOption.Select
        assertEquals("fixture-balanced", model.currentValue)
        assertEquals(listOf("fixture-balanced", "fixture-fast"), model.choices.map { it.value })
        val switched = connection.setConfiguration(
            sessionId,
            ACP_SESSION_MODEL_CONFIG_ID,
            AgentConfigValue.Select("fixture-fast")
        )
        assertTrue(switched is AgentOperationResult.Success)
        assertEquals("fixture-fast", fixture.selectedModel)
        assertEquals(
            "fixture-fast",
            ((switched as AgentOperationResult.Success).value.single {
                it.category == AgentConfigCategory.Model
            } as AgentConfigOption.Select).currentValue
        )
        val mode = created.value.configuration.single { it.category == AgentConfigCategory.Mode }
            as AgentConfigOption.Select
        assertEquals("build", mode.currentValue)
        assertEquals(listOf("build", "plan"), mode.choices.map { it.value })
        assertTrue(connection.setMode(sessionId, "plan") is AgentOperationResult.Success)
        assertEquals("plan", fixture.selectedMode)

        val prompted = withTimeout(5_000L) {
            connection.prompt(
                AgentPromptRequest(sessionId, listOf(AgentContent.Text("hello")))
            )
        }
        assertTrue(prompted is AgentOperationResult.Success)
        assertEquals(AgentStopReason.EndTurn, (prompted as AgentOperationResult.Success).value.stopReason)
        assertTrue(events.any { (_, event) ->
            event is AgentSessionEvent.MessageChunk &&
                (event.content as? AgentContent.Text)?.text == "echo:hello"
        })
        assertEquals(
            listOf(AgentSessionPhase.Ready, AgentSessionPhase.Prompting, AgentSessionPhase.Ready),
            events.mapNotNull { (_, event) -> (event as? AgentSessionEvent.LifecycleChanged)?.phase }
        )

        val waitingPrompt = async {
            connection.prompt(AgentPromptRequest(sessionId, listOf(AgentContent.Text("wait"))))
        }
        withTimeout(2_000L) { fixture.promptStarted.await() }
        val steered = withTimeout(2_000L) {
            connection.steer(AgentPromptRequest(sessionId, listOf(AgentContent.Text("现在改为检查测试"))))
        }
        assertTrue(steered.toString(), steered is AgentOperationResult.Success)
        assertTrue(events.any { (_, event) ->
            event is AgentSessionEvent.MessageChunk &&
                (event.content as? AgentContent.Text)?.text == "echo:现在改为检查测试"
        })
        assertEquals(
            AgentSessionPhase.Ready,
            events.mapNotNull { (_, event) -> (event as? AgentSessionEvent.LifecycleChanged)?.phase }.last(),
        )
        assertTrue(connection.cancel(sessionId) is AgentOperationResult.Success)
        withTimeout(2_000L) {
            while (!fixture.cancelled) yield()
        }
        waitingPrompt.cancelAndJoin()
        val renameRequest = AgentSessionRenameRequest(sessionId, "新标题")
        assertTrue(connection.renameSession(renameRequest) is AgentOperationResult.Success)
        assertEquals(listOf(renameRequest), renamedSessions)
        assertTrue(connection.deleteSession(sessionId) is AgentOperationResult.Success)
        assertEquals(listOf(sessionId), deletedSessions)
        assertTrue(connection.logout() is AgentOperationResult.Success)
        assertTrue(fixture.loggedOut)
        connection.disconnect()
        assertFalse(fixture.channel.isAlive)
    }

    private class AcpAgentFixture {
        lateinit var channel: InMemoryAgentProcessChannel
        @Volatile var cancelled: Boolean = false
        @Volatile var authenticated: Boolean = false
        @Volatile var loggedOut: Boolean = false
        @Volatile var selectedModel: String = "fixture-balanced"
        @Volatile var selectedMode: String = "build"
        @Volatile var createdSessionParameters: SessionCreationParameters? = null
        val promptStarted = CompletableDeferred<Unit>()
        private val releasePrompt = CompletableDeferred<Unit>()

        fun start(): AgentProcessChannel {
            val clientToAgent = Channel<String>(Channel.UNLIMITED)
            val agentToClient = Channel<String>(Channel.UNLIMITED)
            val agentScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val transport = StdioTransport(
                parentScope = agentScope,
                ioDispatcher = Dispatchers.Default,
                input = clientToAgent.receiveAsFlow(),
                output = { line -> agentToClient.send(line) },
                name = "fixture-agent-stdio"
            )
            val protocol = Protocol(agentScope, transport)
            Agent(
                protocol,
                FixtureAgentSupport(
                    onCancel = {
                        cancelled = true
                        releasePrompt.complete(Unit)
                    },
                    onPromptStarted = { promptStarted.complete(Unit) },
                    awaitPromptRelease = { releasePrompt.await() },
                    onAuthenticate = { authenticated = it == "login" },
                    onLogout = { loggedOut = true },
                    onModelSelected = { selectedModel = it },
                    onModeSelected = { selectedMode = it },
                    onSessionCreated = { createdSessionParameters = it },
                )
            )
            protocol.start()
            channel = InMemoryAgentProcessChannel(
                stdoutLines = agentToClient.receiveAsFlow(),
                write = { clientToAgent.send(it) },
                closeAction = {
                    protocol.close()
                    clientToAgent.close()
                    agentToClient.close()
                }
            )
            return channel
        }
    }

    private class FixtureAgentSupport(
        private val onCancel: () -> Unit,
        private val onPromptStarted: () -> Unit,
        private val awaitPromptRelease: suspend () -> Unit,
        private val onAuthenticate: (String) -> Unit,
        private val onLogout: () -> Unit,
        private val onModelSelected: (String) -> Unit,
        private val onModeSelected: (String) -> Unit,
        private val onSessionCreated: (SessionCreationParameters) -> Unit,
    ) : AgentSupport {
        override suspend fun initialize(clientInfo: ClientInfo): AgentInfo = AgentInfo(
            protocolVersion = LATEST_PROTOCOL_VERSION,
            capabilities = AgentCapabilities(
                loadSession = true,
                auth = AgentAuthCapabilities(logout = LogoutCapabilities())
            ),
            authMethods = listOf(
                AuthMethod.AgentAuth(
                    id = AuthMethodId("login"),
                    name = "登录",
                    description = "由测试 Agent 完成认证"
                )
            ),
            implementation = Implementation("fixture-agent", "1.0.0", "Fixture Agent")
        )

        override suspend fun authenticate(methodId: AuthMethodId, _meta: JsonElement?): AuthenticateResponse {
            onAuthenticate(methodId.value)
            return AuthenticateResponse()
        }

        override suspend fun logout(_meta: JsonElement?): LogoutResponse {
            onLogout()
            return LogoutResponse()
        }

        override suspend fun createSession(sessionParameters: SessionCreationParameters): AgentSession {
            onSessionCreated(sessionParameters)
            return FixtureSession(
                SessionId("session-fixture"),
                onCancel,
                onPromptStarted,
                awaitPromptRelease,
                onModelSelected,
                onModeSelected,
                replayHistory = false,
            )
        }

        override suspend fun loadSession(
            sessionId: SessionId,
            sessionParameters: SessionCreationParameters,
        ): AgentSession = FixtureSession(
            sessionId,
            onCancel,
            onPromptStarted,
            awaitPromptRelease,
            onModelSelected,
            onModeSelected,
            replayHistory = true,
        )
    }

    private class FixtureSession(
        override val sessionId: SessionId,
        private val onCancel: () -> Unit,
        private val onPromptStarted: () -> Unit,
        private val awaitPromptRelease: suspend () -> Unit,
        private val onModelSelected: (String) -> Unit,
        private val onModeSelected: (String) -> Unit,
        private val replayHistory: Boolean,
    ) : AgentSession {
        override val availableModels: List<ModelInfo> = listOf(
            ModelInfo(ModelId("fixture-balanced"), "Fixture Balanced"),
            ModelInfo(ModelId("fixture-fast"), "Fixture Fast")
        )
        override val defaultModel: ModelId = ModelId("fixture-balanced")

        private var selectedMode = "build"
        override val configOptions: List<AcpSessionConfigOption>
            get() = listOf(modeOption(selectedMode))

        override suspend fun postInitialize() {
            if (!replayHistory) return
            val contextElement = coroutineContext.fold<kotlin.coroutines.CoroutineContext.Element?>(null) { found, element ->
                found ?: element.takeIf {
                    it.javaClass.name == "com.agentclientprotocol.agent.SessionWrapperContextElement"
                }
            } ?: error("missing ACP session context")
            val wrapper = contextElement.javaClass.getMethod("getSessionWrapper").invoke(contextElement)
            val operations = wrapper.javaClass.getMethod("getClientOperations").invoke(wrapper)
                as ClientSessionOperations
            operations.notify(SessionUpdate.UserMessageChunk(ContentBlock.Text("历史问题")))
            operations.notify(SessionUpdate.AgentMessageChunk(ContentBlock.Text("历史回答")))
        }

        override suspend fun setModel(modelId: ModelId, _meta: JsonElement?) =
            com.agentclientprotocol.model.SetSessionModelResponse().also {
                onModelSelected(modelId.value)
            }

        override suspend fun setConfigOption(
            configId: SessionConfigId,
            value: SessionConfigOptionValue,
            _meta: JsonElement?,
        ): SetSessionConfigOptionResponse {
            check(configId.value == "mode")
            val next = (value as SessionConfigOptionValue.StringValue).value
            check(next == "build" || next == "plan")
            selectedMode = next
            onModeSelected(next)
            return SetSessionConfigOptionResponse(configOptions)
        }

        override suspend fun prompt(content: List<ContentBlock>, _meta: JsonElement?): Flow<Event> = flow {
            val text = content.filterIsInstance<ContentBlock.Text>().joinToString(" ") { it.text }
            emit(Event.SessionUpdateEvent(SessionUpdate.AgentMessageChunk(ContentBlock.Text("echo:$text"))))
            if (text == "wait") {
                onPromptStarted()
                awaitPromptRelease()
            }
            emit(Event.PromptResponseEvent(PromptResponse(StopReason.END_TURN)))
        }

        override suspend fun cancel() = onCancel()

        private fun modeOption(current: String): AcpSessionConfigOption.Select = AcpSessionConfigOption.Select(
            id = SessionConfigId("mode"),
            name = "Session Mode",
            category = SessionConfigOptionCategory("mode"),
            currentValue = SessionConfigValueId(current),
            options = SessionConfigSelectOptions.Flat(
                listOf(
                    SessionConfigSelectOption(SessionConfigValueId("build"), "Build"),
                    SessionConfigSelectOption(SessionConfigValueId("plan"), "Plan"),
                )
            ),
        )
    }

    private class InMemoryAgentProcessChannel(
        override val stdoutLines: Flow<String>,
        private val write: suspend (String) -> Unit,
        private val closeAction: () -> Unit
    ) : AgentProcessChannel {
        private val exit = CompletableDeferred<Int>()
        override val stderrLines: Flow<String> = emptyFlow()
        override val pid: Long? = 7L
        override val isAlive: Boolean get() = !exit.isCompleted

        override suspend fun writeLine(line: String) = write(line)
        override suspend fun awaitExit(): Int = exit.await()
        override suspend fun stop(gracePeriodMs: Long): Int {
            closeAction()
            exit.complete(0)
            return 0
        }
        override fun close() {
            closeAction()
            exit.complete(0)
        }
    }
}
