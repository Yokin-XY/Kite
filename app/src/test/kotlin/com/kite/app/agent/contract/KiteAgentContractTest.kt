package com.kite.app.agent.contract

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KiteAgentContractTest {
    @Test
    fun fakeProviderKeepsLifecycleAndStreamingEventsOrdered() = runTest {
        val events = mutableListOf<AgentSessionEvent>()
        val provider = FakeProvider()
        val result = provider.connect(
            request = AgentConnectionRequest(AgentClientInfo("kite", "test")),
            client = AgentClientEndpoint(
                eventSink = AgentSessionEventSink { _, event -> events += event },
                permissionHandler = AgentPermissionHandler { AgentPermissionOutcome.Cancelled }
            )
        )
        val connection = (result as AgentOperationResult.Success).value
        val session = connection.newSession(AgentNewSessionRequest("/workspace"))
        val prompt = connection.prompt(
            AgentPromptRequest("session-1", listOf(AgentContent.Text("你好")))
        )

        assertTrue(session is AgentOperationResult.Success)
        assertTrue(prompt is AgentOperationResult.Success)
        assertEquals(
            listOf(
                AgentSessionPhase.Preparing,
                AgentSessionPhase.Ready,
                AgentSessionPhase.Prompting,
                AgentSessionPhase.Ready
            ),
            events.filterIsInstance<AgentSessionEvent.LifecycleChanged>().map { it.phase }
        )
        assertEquals(
            listOf("收", "到"),
            events.filterIsInstance<AgentSessionEvent.MessageChunk>()
                .map { (it.content as AgentContent.Text).text }
        )
    }

    @Test
    fun fakeProviderReportsCancellationAndFailureWithoutPretendingSuccess() = runTest {
        val events = mutableListOf<AgentSessionEvent>()
        val connection = (FakeProvider().connect(
            request = AgentConnectionRequest(AgentClientInfo("kite", "test")),
            client = AgentClientEndpoint(
                eventSink = AgentSessionEventSink { _, event -> events += event },
                permissionHandler = AgentPermissionHandler { AgentPermissionOutcome.Cancelled }
            )
        ) as AgentOperationResult.Success).value
        connection.newSession(AgentNewSessionRequest("/workspace"))

        val cancelled = connection.cancel("session-1")
        val failed = connection.prompt(
            AgentPromptRequest("session-1", listOf(AgentContent.Text("fail")))
        )

        assertTrue(cancelled is AgentOperationResult.Success)
        assertTrue(failed is AgentOperationResult.Failure)
        assertEquals("模拟失败", (failed as AgentOperationResult.Failure).message)
        assertEquals(
            listOf(
                AgentSessionPhase.Preparing,
                AgentSessionPhase.Ready,
                AgentSessionPhase.Cancelling,
                AgentSessionPhase.Cancelled,
                AgentSessionPhase.Prompting,
                AgentSessionPhase.Failed
            ),
            events.filterIsInstance<AgentSessionEvent.LifecycleChanged>().map { it.phase }
        )
    }

    private class FakeProvider : KiteAgentProvider {
        override val id: String = "fake"

        override suspend fun connect(
            request: AgentConnectionRequest,
            client: AgentClientEndpoint
        ): AgentOperationResult<KiteAgentConnection> = AgentOperationResult.Success(FakeConnection(client))
    }

    private class FakeConnection(
        private val client: AgentClientEndpoint
    ) : KiteAgentConnection {
        override val provider = AgentProviderInfo("fake", "Fake Agent", "1")
        override val capabilities = AgentCapabilities()

        override suspend fun newSession(
            request: AgentNewSessionRequest
        ): AgentOperationResult<AgentSessionSnapshot> {
            emit(AgentSessionEvent.LifecycleChanged(AgentSessionPhase.Preparing))
            emit(AgentSessionEvent.LifecycleChanged(AgentSessionPhase.Ready))
            return AgentOperationResult.Success(AgentSessionSnapshot("session-1"))
        }

        override suspend fun prompt(request: AgentPromptRequest): AgentOperationResult<AgentTurnResult> {
            emit(AgentSessionEvent.LifecycleChanged(AgentSessionPhase.Prompting))
            val text = (request.content.firstOrNull() as? AgentContent.Text)?.text
            if (text == "fail") {
                emit(AgentSessionEvent.LifecycleChanged(AgentSessionPhase.Failed, "模拟失败"))
                return AgentOperationResult.Failure("模拟失败")
            }
            emit(
                AgentSessionEvent.MessageChunk(
                    AgentMessageRole.Assistant,
                    AgentContent.Text("收")
                )
            )
            emit(
                AgentSessionEvent.MessageChunk(
                    AgentMessageRole.Assistant,
                    AgentContent.Text("到")
                )
            )
            emit(AgentSessionEvent.LifecycleChanged(AgentSessionPhase.Ready))
            return AgentOperationResult.Success(AgentTurnResult(AgentStopReason.EndTurn))
        }

        override suspend fun cancel(sessionId: String): AgentOperationResult<Unit> {
            emit(AgentSessionEvent.LifecycleChanged(AgentSessionPhase.Cancelling))
            emit(AgentSessionEvent.LifecycleChanged(AgentSessionPhase.Cancelled))
            return AgentOperationResult.Success(Unit)
        }

        override suspend fun loadSession(
            request: AgentExistingSessionRequest
        ): AgentOperationResult<AgentSessionSnapshot> = AgentOperationResult.Unsupported("loadSession")

        override suspend fun listSessions(
            request: AgentSessionListRequest
        ): AgentOperationResult<AgentSessionPage> = AgentOperationResult.Unsupported("listSessions")

        override suspend fun resumeSession(
            request: AgentExistingSessionRequest
        ): AgentOperationResult<AgentSessionSnapshot> = AgentOperationResult.Unsupported("resumeSession")

        override suspend fun forkSession(
            request: AgentExistingSessionRequest
        ): AgentOperationResult<AgentSessionSnapshot> = AgentOperationResult.Unsupported("forkSession")

        override suspend fun closeSession(sessionId: String): AgentOperationResult<Unit> =
            AgentOperationResult.Unsupported("closeSession")

        override suspend fun deleteSession(sessionId: String): AgentOperationResult<Unit> =
            AgentOperationResult.Unsupported("deleteSession")

        override suspend fun setConfiguration(
            sessionId: String,
            configId: String,
            value: AgentConfigValue
        ): AgentOperationResult<List<AgentConfigOption>> = AgentOperationResult.Unsupported("setConfiguration")

        override suspend fun disconnect() = Unit

        private fun emit(event: AgentSessionEvent) {
            client.eventSink.onEvent("session-1", event)
        }
    }
}
