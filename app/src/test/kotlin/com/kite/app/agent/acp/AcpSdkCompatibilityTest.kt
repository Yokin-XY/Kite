package com.kite.app.agent.acp

import com.agentclientprotocol.annotations.UnstableApi
import com.agentclientprotocol.model.ClientCapabilities
import com.agentclientprotocol.model.ContentBlock
import com.agentclientprotocol.model.Implementation
import com.agentclientprotocol.model.InitializeRequest
import com.agentclientprotocol.model.LATEST_PROTOCOL_VERSION
import com.agentclientprotocol.model.NewSessionRequest
import com.agentclientprotocol.model.SessionUpdate
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T001 编译探针：证明当前 Android/Kotlin 工具链能真实消费官方 ACP 模型和序列化器。
 * 这不是 Kite 的公共 Agent 合同，后续业务代码不得直接把这些类型暴露给显示层。
 */
@OptIn(UnstableApi::class)
class AcpSdkCompatibilityTest {
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = false
    }

    @Test
    fun stableProtocolModelsCompileAndRoundTrip() {
        val initialize = InitializeRequest(
            protocolVersion = LATEST_PROTOCOL_VERSION,
            clientCapabilities = ClientCapabilities(),
            clientInfo = Implementation(
                name = "kite",
                version = "0.2.3",
                title = "Kite"
            )
        )
        val initializeJson = json.encodeToString(InitializeRequest.serializer(), initialize)
        val restoredInitialize = json.decodeFromString(InitializeRequest.serializer(), initializeJson)

        val newSession = NewSessionRequest(
            cwd = "/workspace",
            mcpServers = emptyList()
        )
        val sessionJson = json.encodeToString(NewSessionRequest.serializer(), newSession)

        val update: SessionUpdate = SessionUpdate.AgentMessageChunk(
            content = ContentBlock.Text("准备就绪")
        )
        val updateJson = json.encodeToString(SessionUpdate.serializer(), update)
        val restoredUpdate = json.decodeFromString(SessionUpdate.serializer(), updateJson)

        assertEquals(1, LATEST_PROTOCOL_VERSION)
        assertEquals(LATEST_PROTOCOL_VERSION, restoredInitialize.protocolVersion)
        assertTrue(sessionJson.contains("/workspace"))
        assertTrue(restoredUpdate is SessionUpdate.AgentMessageChunk)
        assertEquals(
            "准备就绪",
            ((restoredUpdate as SessionUpdate.AgentMessageChunk).content as ContentBlock.Text).text
        )
    }
}
