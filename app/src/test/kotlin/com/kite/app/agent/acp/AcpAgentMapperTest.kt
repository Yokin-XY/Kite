package com.kite.app.agent.acp

import com.agentclientprotocol.annotations.UnstableApi
import com.agentclientprotocol.model.AgentCapabilities as AcpAgentCapabilities
import com.agentclientprotocol.model.ContentBlock
import com.agentclientprotocol.model.EmbeddedResourceResource
import com.agentclientprotocol.model.McpCapabilities
import com.agentclientprotocol.model.ModelId
import com.agentclientprotocol.model.ModelInfo
import com.agentclientprotocol.model.PermissionOption
import com.agentclientprotocol.model.PermissionOptionId
import com.agentclientprotocol.model.PermissionOptionKind
import com.agentclientprotocol.model.PlanEntry
import com.agentclientprotocol.model.PlanEntryPriority
import com.agentclientprotocol.model.PlanEntryStatus
import com.agentclientprotocol.model.PromptCapabilities
import com.agentclientprotocol.model.RequestPermissionOutcome
import com.agentclientprotocol.model.RequestPermissionRequest
import com.agentclientprotocol.model.SessionAdditionalDirectoriesCapabilities
import com.agentclientprotocol.model.SessionCapabilities
import com.agentclientprotocol.model.SessionCloseCapabilities
import com.agentclientprotocol.model.SessionConfigGroupId
import com.agentclientprotocol.model.SessionConfigOption
import com.agentclientprotocol.model.SessionConfigOptionCategory
import com.agentclientprotocol.model.SessionConfigSelectGroup
import com.agentclientprotocol.model.SessionConfigSelectOption
import com.agentclientprotocol.model.SessionConfigSelectOptions
import com.agentclientprotocol.model.SessionConfigValueId
import com.agentclientprotocol.model.SessionForkCapabilities
import com.agentclientprotocol.model.SessionId
import com.agentclientprotocol.model.SessionInfo
import com.agentclientprotocol.model.SessionListCapabilities
import com.agentclientprotocol.model.SessionResumeCapabilities
import com.agentclientprotocol.model.SessionUpdate
import com.agentclientprotocol.model.StopReason
import com.agentclientprotocol.model.ToolCallContent
import com.agentclientprotocol.model.ToolCallId
import com.agentclientprotocol.model.ToolCallLocation
import com.agentclientprotocol.model.ToolCallStatus
import com.agentclientprotocol.model.ToolKind
import com.agentclientprotocol.protocol.JsonRpcException
import com.kite.app.agent.contract.AgentConfigCategory
import com.kite.app.agent.contract.AgentConfigOption
import com.kite.app.agent.contract.AgentContent
import com.kite.app.agent.contract.AgentMessageRole
import com.kite.app.agent.contract.AgentFailureCode
import com.kite.app.agent.contract.AgentPermissionKind
import com.kite.app.agent.contract.AgentPermissionOutcome
import com.kite.app.agent.contract.AgentSessionEvent
import com.kite.app.agent.contract.AgentStopReason
import com.kite.app.agent.contract.AgentToolContent
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(UnstableApi::class)
class AcpAgentMapperTest {
    @Test
    fun mapsNegotiatedCapabilitiesWithoutInventingUnsupportedOperations() {
        val source = AcpAgentCapabilities(
            loadSession = true,
            promptCapabilities = PromptCapabilities(
                audio = true,
                image = true,
                embeddedContext = true
            ),
            mcpCapabilities = McpCapabilities(http = true, sse = false),
            sessionCapabilities = SessionCapabilities(
                fork = SessionForkCapabilities(),
                list = SessionListCapabilities(),
                resume = SessionResumeCapabilities(),
                close = SessionCloseCapabilities(),
                additionalDirectories = SessionAdditionalDirectoriesCapabilities()
            ),
            _meta = buildJsonObject { put("provider", "opencode") }
        )

        val mapped = AcpAgentMapper.capabilities(source)

        assertTrue(mapped.prompt.text)
        assertTrue(mapped.prompt.resourceLinks)
        assertTrue(mapped.prompt.images)
        assertTrue(mapped.prompt.audio)
        assertTrue(mapped.prompt.embeddedResources)
        assertTrue(mapped.sessions.load)
        assertTrue(mapped.sessions.list)
        assertTrue(mapped.sessions.resume)
        assertTrue(mapped.sessions.fork)
        assertTrue(mapped.sessions.close)
        assertTrue(mapped.sessions.additionalDirectories)
        assertFalse(mapped.sessions.delete)
        assertTrue(mapped.mcp.stdio)
        assertTrue(mapped.mcp.http)
        assertFalse(mapped.mcp.sse)
        assertTrue(mapped.extension?.payload?.contains("opencode") == true)
    }

    @Test
    fun mapsContentAndConfigurationWhilePreservingUnknownCategoriesAndGroups() {
        val embedded = AcpAgentMapper.content(
            ContentBlock.Resource(
                resource = EmbeddedResourceResource.TextResourceContents(
                    text = "项目规则",
                    uri = "file:///workspace/AGENTS.md",
                    mimeType = "text/markdown"
                )
            )
        )
        val image = AcpAgentMapper.content(
            ContentBlock.Image(
                data = "aGVsbG8=",
                mimeType = "image/png",
                uri = "file:///storage/emulated/0/Download/reference.png"
            )
        )
        val groupedModel = SessionConfigOption.select(
            id = "model",
            name = "模型",
            currentValue = "gpt-5.6",
            category = SessionConfigOptionCategory.MODEL,
            options = SessionConfigSelectOptions.Grouped(
                listOf(
                    SessionConfigSelectGroup(
                        group = SessionConfigGroupId("openai"),
                        name = "OpenAI",
                        options = listOf(
                            SessionConfigSelectOption(
                                value = SessionConfigValueId("gpt-5.6"),
                                name = "GPT-5.6"
                            )
                        )
                    )
                )
            )
        )
        val vendorOption = SessionConfigOption.select(
            id = "speed",
            name = "速度",
            currentValue = "balanced",
            category = SessionConfigOptionCategory("_vendor_speed"),
            options = SessionConfigSelectOptions.Flat(
                listOf(
                    SessionConfigSelectOption(
                        value = SessionConfigValueId("balanced"),
                        name = "均衡"
                    )
                )
            )
        )
        val thoughtToggle = SessionConfigOption.boolean(
            id = "show_thought",
            name = "显示思考",
            currentValue = true,
            category = SessionConfigOptionCategory.THOUGHT_LEVEL
        )

        val mapped = AcpAgentMapper.configOptions(listOf(groupedModel, vendorOption, thoughtToggle))

        assertTrue(embedded is AgentContent.EmbeddedText)
        assertEquals("项目规则", (embedded as AgentContent.EmbeddedText).text)
        assertTrue(image is AgentContent.Image)
        assertEquals("image/png", (image as AgentContent.Image).mimeType)
        val model = mapped[0] as AgentConfigOption.Select
        assertEquals(AgentConfigCategory.Model, model.category)
        assertEquals("openai", model.choices.single().groupId)
        assertEquals("OpenAI", model.choices.single().groupName)
        assertEquals(AgentConfigCategory("_vendor_speed"), mapped[1].category)
        assertTrue(mapped[2] is AgentConfigOption.Toggle)
        assertTrue((mapped[2] as AgentConfigOption.Toggle).currentValue)
    }

    @Test
    fun mapsDedicatedAcpModelsIntoUnifiedKiteModelOption() {
        val mapped = AcpAgentMapper.modelOption(
            currentModelId = "hermes-large",
            source = listOf(
                ModelInfo(ModelId("hermes-large"), "Hermes Large", "适合复杂任务"),
                ModelInfo(ModelId("hermes-fast"), "Hermes Fast", "适合快速响应")
            )
        )

        assertEquals(ACP_SESSION_MODEL_CONFIG_ID, mapped.id)
        assertEquals(AgentConfigCategory.Model, mapped.category)
        assertEquals("hermes-large", mapped.currentValue)
        assertEquals(listOf("hermes-large", "hermes-fast"), mapped.choices.map { it.value })
        assertEquals("Hermes Fast", mapped.choices.last().name)
    }

    @Test
    fun mapsStreamingMessagesToolsPlansAndUnknownUpdates() {
        val message = AcpAgentMapper.sessionEvent(
            SessionUpdate.AgentMessageChunk(ContentBlock.Text("准备就绪"))
        )
        val tool = AcpAgentMapper.sessionEvent(
            SessionUpdate.ToolCall(
                toolCallId = ToolCallId("tool-1"),
                title = "修改配置",
                kind = ToolKind.EDIT,
                status = ToolCallStatus.IN_PROGRESS,
                content = listOf(
                    ToolCallContent.Diff(
                        path = "/workspace/config.json",
                        newText = "{\"enabled\":true}",
                        oldText = "{\"enabled\":false}"
                    )
                ),
                locations = listOf(ToolCallLocation("/workspace/config.json", 1u)),
                rawInput = buildJsonObject { put("path", "/workspace/config.json") }
            )
        )
        val plan = AcpAgentMapper.sessionEvent(
            SessionUpdate.PlanUpdate(
                entries = listOf(
                    PlanEntry("检查配置", PlanEntryPriority.HIGH, PlanEntryStatus.COMPLETED)
                )
            )
        )
        val unknown = AcpAgentMapper.sessionEvent(
            SessionUpdate.UnknownSessionUpdate(
                sessionUpdateType = "_vendor_progress",
                rawJson = buildJsonObject { put("percent", 42) }
            )
        )

        assertTrue(message is AgentSessionEvent.MessageChunk)
        assertEquals(AgentMessageRole.Assistant, (message as AgentSessionEvent.MessageChunk).role)
        assertEquals("准备就绪", (message.content as AgentContent.Text).text)
        assertTrue(tool is AgentSessionEvent.ToolCallStarted)
        tool as AgentSessionEvent.ToolCallStarted
        assertEquals("edit", tool.call.kind?.value)
        assertEquals("in_progress", tool.call.status?.value)
        assertTrue(tool.call.content.single() is AgentToolContent.Diff)
        assertEquals(1L, tool.call.locations.single().line)
        assertTrue(plan is AgentSessionEvent.PlanUpdated)
        assertEquals("completed", (plan as AgentSessionEvent.PlanUpdated).entries.single().status)
        assertTrue(unknown is AgentSessionEvent.Extension)
        assertEquals("_vendor_progress", (unknown as AgentSessionEvent.Extension).type)
        assertTrue(unknown.payload.contains("42"))
    }

    @Test
    fun mapsPermissionsSessionsAndStopReasonsInBothDirections() {
        val request = RequestPermissionRequest(
            sessionId = SessionId("session-1"),
            toolCall = SessionUpdate.ToolCallUpdate(
                toolCallId = ToolCallId("tool-1"),
                title = "运行测试",
                kind = ToolKind.EXECUTE,
                status = ToolCallStatus.PENDING
            ),
            options = listOf(
                PermissionOption(
                    optionId = PermissionOptionId("allow-once"),
                    name = "允许一次",
                    kind = PermissionOptionKind.ALLOW_ONCE
                ),
                PermissionOption(
                    optionId = PermissionOptionId("reject-always"),
                    name = "始终拒绝",
                    kind = PermissionOptionKind.REJECT_ALWAYS
                )
            )
        )
        val mappedRequest = AcpAgentMapper.permissionRequest(request)
        val selected = AgentPermissionOutcome.Selected("allow-once")
        val acpSelected = AcpAgentMapper.permissionOutcome(selected)
        val session = AcpAgentMapper.sessionSummary(
            SessionInfo(
                sessionId = SessionId("session-1"),
                cwd = "/workspace",
                title = "修复构建",
                updatedAt = "2026-07-28T13:00:00Z",
                additionalDirectories = listOf("/storage/emulated/0")
            )
        )

        assertEquals(AgentPermissionKind.AllowOnce, mappedRequest.options[0].kind)
        assertEquals(AgentPermissionKind.RejectAlways, mappedRequest.options[1].kind)
        assertTrue(acpSelected is RequestPermissionOutcome.Selected)
        assertEquals(selected, AcpAgentMapper.permissionOutcome(acpSelected))
        assertEquals(AgentPermissionOutcome.Cancelled, AcpAgentMapper.permissionOutcome(RequestPermissionOutcome.Cancelled))
        assertEquals("session-1", session.id)
        assertEquals(listOf("/storage/emulated/0"), session.additionalDirectories)
        assertEquals(AgentStopReason.EndTurn, AcpAgentMapper.stopReason(StopReason.END_TURN))
        assertEquals(AgentStopReason.Cancelled, AcpAgentMapper.stopReason(StopReason.CANCELLED))
        assertNull(session.extension)
    }

    @Test
    fun mapsAuthenticationRequiredAndUnknownRpcErrorsWithoutDroppingProtocolDetails() {
        val legacyAuthenticationError = AcpAgentMapper.failure(
            "创建会话",
            JsonRpcException(-32000, "Authentication required")
        )
        val typedAuthenticationError = AcpAgentMapper.failure(
            "恢复会话",
            JsonRpcException(
                -32001,
                "Session unavailable",
                buildJsonObject { put("kind", "auth_required") }
            )
        )
        val unknownError = AcpAgentMapper.failure(
            "更新配置",
            JsonRpcException(-32042, "Vendor failure", buildJsonObject { put("retry", true) })
        )

        assertEquals(AgentFailureCode.AuthenticationRequired, legacyAuthenticationError.code)
        assertEquals(AgentFailureCode.AuthenticationRequired, typedAuthenticationError.code)
        assertEquals(AgentFailureCode("acp_json_rpc_-32042"), unknownError.code)
        assertEquals("json_rpc_error", unknownError.extension?.type)
        assertTrue(unknownError.extension?.payload?.contains("Vendor failure") == true)
        assertTrue(unknownError.extension?.payload?.contains("retry") == true)
    }
}
