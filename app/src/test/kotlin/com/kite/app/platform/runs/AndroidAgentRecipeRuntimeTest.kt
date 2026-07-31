package com.kite.app.platform.runs

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.kite.app.agent.contract.AgentCapabilities
import com.kite.app.agent.contract.AgentClientEndpoint
import com.kite.app.agent.contract.AgentConfigOption
import com.kite.app.agent.contract.AgentConfigValue
import com.kite.app.agent.contract.AgentConnectionRequest
import com.kite.app.agent.contract.AgentExistingSessionRequest
import com.kite.app.agent.contract.AgentFailureCode
import com.kite.app.agent.contract.AgentNewSessionRequest
import com.kite.app.agent.contract.AgentOperationResult
import com.kite.app.agent.contract.AgentPromptRequest
import com.kite.app.agent.contract.AgentProviderInfo
import com.kite.app.agent.contract.AgentSessionListRequest
import com.kite.app.agent.contract.AgentSessionPage
import com.kite.app.agent.contract.AgentSessionCapabilities
import com.kite.app.agent.contract.AgentSessionSnapshot
import com.kite.app.agent.contract.AgentTurnResult
import com.kite.app.agent.contract.KiteAgentConnection
import com.kite.app.agent.contract.KiteAgentProvider
import com.kite.app.agent.process.AgentProcessFactory
import com.kite.app.agent.registration.AgentDefinition
import com.kite.app.agent.registration.AgentLaunchSpec
import com.kite.app.agent.registration.AgentRegistration
import com.kite.app.agent.registration.AgentRegistrationSource
import com.kite.app.agent.registration.KiteAgentRegistry
import com.kite.app.agent.runtime.AgentAttachProviderRegistry
import com.kite.app.agent.runtime.AgentRuntimeRegistry
import com.kite.app.agent.store.AgentConversationStore
import com.kite.app.application.runs.RecipeExecutionEvent
import com.kite.app.application.runs.RecipeStepExecutionRequest
import com.kite.app.foundation.contracts.ContainerExecConfig
import com.kite.app.foundation.contracts.ContainerLaunchConfig
import com.kite.app.foundation.contracts.ContainerRecord
import com.kite.app.foundation.workspace.ManagedRuntimeLane
import com.kite.app.foundation.workspace.ManagedRuntimeLaunchPlan
import com.kite.app.recipe.KiteExecution
import com.kite.app.recipe.KiteRecipe
import com.kite.app.recipe.KiteRecipeStep
import com.kite.app.run.CardRunState
import com.kite.app.run.CardRunAgentBinding
import com.kite.app.run.CardRunAgentConnectionStatus
import com.kite.app.run.CardRunStatus
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
class AndroidAgentRecipeRuntimeTest {
    private val context by lazy { ApplicationProvider.getApplicationContext<Context>() }

    @Before
    fun setUp() {
        AgentAttachProviderRegistry.resetForTest()
        AgentConversationStore.resetForTest()
    }

    @After
    fun tearDown() = runBlocking {
        AgentRuntimeRegistry.resetForTest()
        AgentAttachProviderRegistry.resetForTest()
        AgentConversationStore.resetForTest()
    }

    @Test
    fun stableAgentIdFailsClearlyWhenRegistrationIsMissing() {
        val runtime = runtime(emptyList())
        val events = mutableListOf<RecipeExecutionEvent>()

        runtime.start(request("missing-agent"), emptyMap(), events::add)

        val failure = events.single() as RecipeExecutionEvent.Failed
        assertTrue(failure.message.contains("未找到 Agent 登记"))
    }

    @Test
    fun registeredAgentThatNeedsConfigurationDoesNotLaunchProcess() {
        val registration = managedRegistration(configurationRequired = true)
        val runtime = runtime(listOf(registration))
        val events = mutableListOf<RecipeExecutionEvent>()

        runtime.start(request("opencode"), emptyMap(), events::add)

        val failure = events.single() as RecipeExecutionEvent.Failed
        assertTrue(failure.message.contains("尚未完成配置"))
    }

    @Test
    fun resolvedAgentCannotReplaceIdentityAlreadyFixedByRunInstance() {
        val registration = attachRegistration()
        val runtime = runtime(listOf(registration))
        val events = mutableListOf<RecipeExecutionEvent>()
        val fixedRequest = request("remote-agent").copy(
            previousState = request("remote-agent").previousState.copy(agentId = "opencode")
        )

        runtime.start(fixedRequest, emptyMap(), events::add)

        val failure = events.single() as RecipeExecutionEvent.Failed
        assertTrue(failure.message.contains("已固定绑定 Agent：opencode"))
        assertEquals("opencode", failure.mutation?.agentId)
    }

    @Test
    fun attachRegistrationWithoutConnectionAdapterFailsClearly() {
        val registration = attachRegistration()
        val runtime = runtime(listOf(registration))
        val events = mutableListOf<RecipeExecutionEvent>()

        runtime.start(request("remote-agent"), emptyMap(), events::add)

        val failure = events.single() as RecipeExecutionEvent.Failed
        assertTrue(failure.message.contains("Attach"))
        assertTrue(failure.mutation?.clearRunBinding == true)
        assertTrue(failure.mutation?.agentId == "remote-agent")
    }

    @Test
    fun attachUsesRegisteredConnectionWithoutManagedPreparationOrProcessOwnership() {
        val provider = FakeAttachProvider()
        val reference = "connections/remote-provider"
        assertTrue(AgentAttachProviderRegistry.register(reference, provider))
        val processStarted = AtomicBoolean(false)
        val managedPrepared = AtomicBoolean(false)
        val registry = KiteAgentRegistry(
            context = context,
            resourceRegistrationSource = { listOf(attachRegistration()) }
        )
        val runtime = AndroidAgentRecipeRuntime(
            context = context,
            processFactory = AgentProcessFactory {
                processStarted.set(true)
                error("Attach 不应创建进程")
            },
            agentRegistry = registry,
            managedPreparation = { managedPrepared.set(true) }
        )
        val events = CopyOnWriteArrayList<RecipeExecutionEvent>()
        val settled = CountDownLatch(1)

        val attachRequest = request("remote-agent").copy(
            runtimeRootOwnerId = "card:run-agent@1",
            runtimeOwnerId = "card:run-agent@1/step/0-agent",
            runtimeUnitId = "agent"
        )
        runtime.start(attachRequest, emptyMap()) { event ->
            events += event
            if (event is RecipeExecutionEvent.AwaitingUser || event is RecipeExecutionEvent.Failed) {
                settled.countDown()
            }
        }

        assertTrue("Attach 连接没有在时限内完成", settled.await(5, TimeUnit.SECONDS))
        val ready = events.filterIsInstance<RecipeExecutionEvent.AwaitingUser>().single()
        assertFalse(processStarted.get())
        assertFalse(managedPrepared.get())
        assertTrue(provider.connected.get())
        assertEquals("remote-agent", ready.mutation.agentId)
        assertEquals("remote-provider", ready.mutation.agentBinding?.providerId)
        assertEquals(null, ready.mutation.agentBinding?.sessionId)
        assertEquals("可以开始新会话", ready.mutation.agentBinding?.statusMessage)
        assertEquals(0, provider.connection.newSessionCalls)
        assertEquals(null, ready.mutation.runtimeRootOwnerId)
        assertEquals(null, ready.mutation.runtimeOwnerId)
        assertEquals(emptyList<String>(), ready.mutation.ownedRuntimeOwnerIds)
        assertNotNull(AgentRuntimeRegistry.session("run-agent"))
        val stopped = CountDownLatch(1)
        runtime.stop("run-agent", 1L) { confirmed ->
            assertTrue(confirmed)
            stopped.countDown()
        }
        assertTrue("Attach 连接没有在时限内断开", stopped.await(5, TimeUnit.SECONDS))
        assertTrue(provider.connection.disconnected.get())
    }

    @Test
    fun attachRestartOpensDraftWithoutResumingPersistedSession() {
        val provider = FakeAttachProvider(resumeExisting = true)
        val reference = "connections/remote-provider"
        assertTrue(AgentAttachProviderRegistry.register(reference, provider))
        val runtime = runtime(listOf(attachRegistration()))
        val events = CopyOnWriteArrayList<RecipeExecutionEvent>()
        val settled = CountDownLatch(1)
        val restartRequest = request("remote-agent").copy(
            previousState = request("remote-agent").previousState.copy(
                agentId = "remote-agent",
                agentBinding = CardRunAgentBinding(
                    providerId = "remote-provider",
                    sessionId = "existing-session",
                    status = CardRunAgentConnectionStatus.Ready
                )
            )
        )

        runtime.start(restartRequest, emptyMap()) { event ->
            events += event
            if (event is RecipeExecutionEvent.AwaitingUser || event is RecipeExecutionEvent.Failed) {
                settled.countDown()
            }
        }

        assertTrue("空白草稿没有在时限内就绪", settled.await(5, TimeUnit.SECONDS))
        val preparing = events.filterIsInstance<RecipeExecutionEvent.Progress>().first()
        val ready = events.filterIsInstance<RecipeExecutionEvent.AwaitingUser>().single()
        assertEquals(null, preparing.mutation.agentBinding?.sessionId)
        assertEquals(null, ready.mutation.agentBinding?.sessionId)
        assertEquals("可以开始新会话", ready.mutation.agentBinding?.statusMessage)
        assertEquals(0, provider.connection.newSessionCalls)
        assertEquals(0, provider.connection.resumeSessionCalls)
    }

    @Test
    fun persistedSessionRestoreFailureCannotBlockOpeningDraft() {
        val provider = FakeAttachProvider(resumeExisting = true, resumeFails = true)
        val reference = "connections/remote-provider"
        assertTrue(AgentAttachProviderRegistry.register(reference, provider))
        val runtime = runtime(listOf(attachRegistration()))
        val events = CopyOnWriteArrayList<RecipeExecutionEvent>()
        val settled = CountDownLatch(1)
        val restartRequest = request("remote-agent").copy(
            previousState = request("remote-agent").previousState.copy(
                agentId = "remote-agent",
                agentBinding = CardRunAgentBinding(
                    providerId = "remote-provider",
                    sessionId = "existing-session",
                    status = CardRunAgentConnectionStatus.Ready
                )
            )
        )

        runtime.start(restartRequest, emptyMap()) { event ->
            events += event
            if (event is RecipeExecutionEvent.AwaitingUser || event is RecipeExecutionEvent.Failed) {
                settled.countDown()
            }
        }

        assertTrue("空白草稿没有在时限内就绪", settled.await(5, TimeUnit.SECONDS))
        val ready = events.filterIsInstance<RecipeExecutionEvent.AwaitingUser>().single()
        assertEquals(null, ready.mutation.agentBinding?.sessionId)
        assertEquals(0, provider.connection.resumeSessionCalls)
        assertEquals(0, provider.connection.newSessionCalls)
    }

    @Test
    fun authenticationIsDeferredUntilTheDraftFirstSend() {
        val provider = AuthenticationRequiredProvider()
        val reference = "connections/remote-provider"
        assertTrue(AgentAttachProviderRegistry.register(reference, provider))
        val runtime = runtime(listOf(attachRegistration()))
        val events = CopyOnWriteArrayList<RecipeExecutionEvent>()
        val settled = CountDownLatch(1)

        runtime.start(request("remote-agent"), emptyMap()) { event ->
            events += event
            if (event is RecipeExecutionEvent.AwaitingUser || event is RecipeExecutionEvent.Failed) settled.countDown()
        }

        assertTrue("Agent 草稿没有在时限内就绪", settled.await(5, TimeUnit.SECONDS))
        val ready = events.filterIsInstance<RecipeExecutionEvent.AwaitingUser>().single()
        assertEquals(null, ready.mutation.agentBinding?.sessionId)
        val prompt = runBlocking {
            AgentRuntimeRegistry.prompt(
                "run-agent",
                1L,
                listOf(com.kite.app.agent.contract.AgentContent.Text("开始"))
            )
        }
        assertTrue(prompt is AgentOperationResult.Failure)
        assertEquals(
            AgentFailureCode.AuthenticationRequired,
            (prompt as AgentOperationResult.Failure).code
        )
    }

    @Test
    fun hostReadyLaunchNeverBuildsProotFallback() {
        val prootBuilds = AtomicInteger(0)
        val selected = ManagedAgentProcessLaunchSelector.select(
            runtimePlan = ManagedRuntimeLaunchPlan.Ready(
                config = ContainerLaunchConfig(
                    container = container(),
                    executablePath = "/host/node",
                    workingDirectory = "/workspace",
                    args = arrayOf("/host/node", "openclaw", "acp"),
                    env = arrayOf("PATH=/host/bin", "OPENCLAW_GATEWAY_TOKEN=private")
                ),
                lane = ManagedRuntimeLane.HOST_NODE,
                reason = "host_node_ready",
            ),
            additionalEnvironment = mapOf("OPENCLAW_GATEWAY_TOKEN" to "private"),
        ) {
            prootBuilds.incrementAndGet()
            prootConfig()
        }

        assertEquals(0, prootBuilds.get())
        assertEquals("host_node", selected.runtimeLane)
        assertEquals("none", selected.fallbackReason)
        assertEquals(listOf("/host/node", "openclaw", "acp"), selected.process.command)
        assertEquals("private", selected.process.environment["OPENCLAW_GATEWAY_TOKEN"])
    }

    @Test
    fun pythonHostReadyProjectsPythonLaneWithoutBuildingProot() {
        val prootBuilds = AtomicInteger(0)
        val selected = ManagedAgentProcessLaunchSelector.select(
            runtimePlan = ManagedRuntimeLaunchPlan.Ready(
                config = ContainerLaunchConfig(
                    container = container(),
                    executablePath = "/host/glibc",
                    workingDirectory = "/workspace",
                    args = arrayOf("/host/glibc", "-c", "print('ok')"),
                    env = arrayOf("KITE_GLIBC_HOST_TARGET=/host/python3"),
                ),
                lane = ManagedRuntimeLane.HOST_PYTHON,
                reason = "host_python_ready",
            ),
            additionalEnvironment = emptyMap(),
        ) {
            prootBuilds.incrementAndGet()
            prootConfig()
        }

        assertEquals(0, prootBuilds.get())
        assertEquals("host_python", selected.runtimeLane)
        assertEquals("none", selected.fallbackReason)
    }

    @Test
    fun fallbackBuildsExactlyOneProotLaunchAndKeepsReason() {
        val prootBuilds = AtomicInteger(0)
        val selected = ManagedAgentProcessLaunchSelector.select(
            runtimePlan = ManagedRuntimeLaunchPlan.Fallback("host_node_not_ready"),
            additionalEnvironment = mapOf("OPENCLAW_GATEWAY_TOKEN" to "private"),
        ) {
            prootBuilds.incrementAndGet()
            prootConfig()
        }

        assertEquals(1, prootBuilds.get())
        assertEquals("proot_shell", selected.runtimeLane)
        assertEquals("host_node_not_ready", selected.fallbackReason)
        assertEquals(listOf("openclaw", "acp"), selected.process.command)
        assertEquals("private", selected.process.environment["OPENCLAW_GATEWAY_TOKEN"])
        assertEquals("/usr/bin", selected.process.environment["PATH"])
    }

    @Test
    fun blockedProviderNeverBuildsProotFallback() {
        val prootBuilds = AtomicInteger(0)

        val failure = assertThrows(IllegalStateException::class.java) {
            ManagedAgentProcessLaunchSelector.select(
                runtimePlan = ManagedRuntimeLaunchPlan.Blocked("runtime_identity_invalid"),
                additionalEnvironment = emptyMap(),
            ) {
                prootBuilds.incrementAndGet()
                prootConfig()
            }
        }

        assertEquals(0, prootBuilds.get())
        assertTrue(failure.message.orEmpty().contains("runtime_identity_invalid"))
    }

    private fun container(): ContainerRecord = ContainerRecord(
        id = "ubuntu-main",
        displayName = "Ubuntu",
        imageName = "ubuntu-base-24.04-arm64",
        rootfsPath = "/rootfs",
        workspacePath = "/workspace",
        createdAt = 1L,
    )

    private fun prootConfig(): ContainerExecConfig = ContainerExecConfig(
        container = container(),
        workingDirectory = "/workspace",
        command = listOf("openclaw", "acp"),
        env = mapOf("PATH" to "/usr/bin"),
    )

    private fun runtime(registrations: List<AgentRegistration>): AndroidAgentRecipeRuntime {
        val registry = KiteAgentRegistry(
            context = context,
            resourceRegistrationSource = { registrations }
        )
        return AndroidAgentRecipeRuntime(
            context = context,
            processFactory = AgentProcessFactory { error("preflight should stop before launch") },
            agentRegistry = registry
        )
    }

    private fun managedRegistration(configurationRequired: Boolean): AgentRegistration =
        AgentRegistration(
            definition = AgentDefinition("opencode", "OpenCode"),
            source = AgentRegistrationSource.Custom,
            launch = AgentLaunchSpec.Managed(
                providerId = "opencode-provider",
                protocol = "acp",
                transport = "stdio",
                argv = listOf("opencode", "acp")
            ),
            configurationRequired = configurationRequired
        )

    private fun attachRegistration(): AgentRegistration = AgentRegistration(
        definition = AgentDefinition("remote-agent", "Remote Agent"),
        source = AgentRegistrationSource.Custom,
        launch = AgentLaunchSpec.Attach(
            providerId = "remote-provider",
            protocol = "acp",
            transport = "socket",
            connectionReference = "connections/remote-provider"
        )
    )

    private fun request(agentId: String): RecipeStepExecutionRequest {
        val step = KiteRecipeStep(
            id = "agent",
            type = KiteRecipe.STEP_AGENT,
            agentId = agentId,
            workdir = "/workspace"
        )
        val recipe = KiteRecipe(
            id = "agent-card",
            name = "Agent",
            description = "",
            type = KiteRecipe.TYPE_AGENT,
            defaultUrl = "",
            shortcut = false,
            execution = KiteExecution.steps(listOf(step))
        )
        return RecipeStepExecutionRequest(
            recipe = recipe,
            instanceId = "run-agent",
            generation = 1L,
            stepIndex = 0,
            step = step,
            previousState = CardRunState(
                instanceId = "run-agent",
                recipeId = recipe.id,
                status = CardRunStatus.Starting
            )
        )
    }

    private class FakeAttachProvider(
        private val resumeExisting: Boolean = false,
        private val resumeFails: Boolean = false
    ) : KiteAgentProvider {
        override val id: String = "remote-provider"
        val connected = AtomicBoolean(false)
        lateinit var connection: FakeAttachConnection

        override suspend fun connect(
            request: AgentConnectionRequest,
            client: AgentClientEndpoint
        ): AgentOperationResult<KiteAgentConnection> {
            connected.set(true)
            connection = FakeAttachConnection(resumeExisting, resumeFails)
            return AgentOperationResult.Success(connection)
        }
    }

    private open class FakeAttachConnection(
        private val resumeExisting: Boolean = false,
        private val resumeFails: Boolean = false
    ) : KiteAgentConnection {
        override val provider = AgentProviderInfo("remote-provider", "Remote Agent")
        override val capabilities = AgentCapabilities(
            sessions = AgentSessionCapabilities(resume = resumeExisting)
        )
        val disconnected = AtomicBoolean(false)
        var newSessionCalls = 0
        var resumeSessionCalls = 0

        override suspend fun newSession(
            request: AgentNewSessionRequest
        ): AgentOperationResult<AgentSessionSnapshot> {
            newSessionCalls++
            return AgentOperationResult.Success(AgentSessionSnapshot("remote-session"))
        }

        override suspend fun cancel(sessionId: String): AgentOperationResult<Unit> =
            AgentOperationResult.Success(Unit)

        override suspend fun disconnect() {
            disconnected.set(true)
        }

        override suspend fun loadSession(request: AgentExistingSessionRequest) =
            unsupported<AgentSessionSnapshot>()

        override suspend fun listSessions(request: AgentSessionListRequest) =
            unsupported<AgentSessionPage>()

        override suspend fun resumeSession(request: AgentExistingSessionRequest): AgentOperationResult<AgentSessionSnapshot> {
            resumeSessionCalls++
            return if (resumeFails) {
                AgentOperationResult.Failure("恢复失败")
            } else if (resumeExisting) {
                AgentOperationResult.Success(AgentSessionSnapshot(request.sessionId))
            } else {
                unsupported()
            }
        }

        override suspend fun forkSession(request: AgentExistingSessionRequest) =
            unsupported<AgentSessionSnapshot>()

        override suspend fun closeSession(sessionId: String) = unsupported<Unit>()

        override suspend fun deleteSession(sessionId: String) = unsupported<Unit>()

        override suspend fun prompt(request: AgentPromptRequest) = unsupported<AgentTurnResult>()

        override suspend fun setConfiguration(
            sessionId: String,
            configId: String,
            value: AgentConfigValue
        ) = unsupported<List<AgentConfigOption>>()

        private fun <T> unsupported(): AgentOperationResult<T> =
            AgentOperationResult.Unsupported("test")
    }

    private class AuthenticationRequiredProvider : KiteAgentProvider {
        override val id: String = "remote-provider"

        override suspend fun connect(
            request: AgentConnectionRequest,
            client: AgentClientEndpoint
        ): AgentOperationResult<KiteAgentConnection> =
            AgentOperationResult.Success(AuthenticationRequiredConnection())
    }

    private class AuthenticationRequiredConnection : FakeAttachConnection() {
        override suspend fun newSession(
            request: AgentNewSessionRequest
        ): AgentOperationResult<AgentSessionSnapshot> = AgentOperationResult.Failure(
            message = "Authentication required",
            code = AgentFailureCode.AuthenticationRequired
        )
    }
}
