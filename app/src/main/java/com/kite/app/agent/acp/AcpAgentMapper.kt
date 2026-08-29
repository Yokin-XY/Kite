package com.kite.app.agent.acp

import com.agentclientprotocol.annotations.UnstableApi
import com.agentclientprotocol.model.AgentCapabilities as AcpAgentCapabilities
import com.agentclientprotocol.model.Annotations
import com.agentclientprotocol.model.AuthMethod
import com.agentclientprotocol.model.AvailableCommandInput
import com.agentclientprotocol.model.ContentBlock
import com.agentclientprotocol.model.EmbeddedResourceResource
import com.agentclientprotocol.model.ModelInfo
import com.agentclientprotocol.model.PermissionOption
import com.agentclientprotocol.model.PermissionOptionId
import com.agentclientprotocol.model.PermissionOptionKind
import com.agentclientprotocol.model.RequestPermissionOutcome
import com.agentclientprotocol.model.RequestPermissionRequest
import com.agentclientprotocol.model.SessionConfigOption
import com.agentclientprotocol.model.SessionConfigSelectOptions
import com.agentclientprotocol.model.SessionInfo
import com.agentclientprotocol.model.SessionUpdate
import com.agentclientprotocol.model.StopReason
import com.agentclientprotocol.model.ToolCallContent
import com.agentclientprotocol.model.ToolCallLocation
import com.agentclientprotocol.model.ToolCallStatus
import com.agentclientprotocol.model.ToolKind
import com.agentclientprotocol.protocol.JsonRpcException
import com.kite.app.agent.contract.AgentCapabilities
import com.kite.app.agent.contract.AgentAuthenticationCapabilities
import com.kite.app.agent.contract.AgentAuthenticationMethod
import com.kite.app.agent.contract.AgentAuthenticationVariable
import com.kite.app.agent.contract.AgentCommand
import com.kite.app.agent.contract.AgentConfigCategory
import com.kite.app.agent.contract.AgentConfigChoice
import com.kite.app.agent.contract.AgentConfigOption
import com.kite.app.agent.contract.AgentContent
import com.kite.app.agent.contract.AgentContentAnnotations
import com.kite.app.agent.contract.AgentCost
import com.kite.app.agent.contract.AgentFailureCode
import com.kite.app.agent.contract.AgentFailures
import com.kite.app.agent.contract.AgentMcpCapabilities
import com.kite.app.agent.contract.AgentMessageRole
import com.kite.app.agent.contract.AgentPermissionKind
import com.kite.app.agent.contract.AgentPermissionOption
import com.kite.app.agent.contract.AgentPermissionOutcome
import com.kite.app.agent.contract.AgentPermissionRequest
import com.kite.app.agent.contract.AgentPlanEntry
import com.kite.app.agent.contract.AgentPromptCapabilities
import com.kite.app.agent.contract.AgentProtocolExtension
import com.kite.app.agent.contract.AgentOperationResult
import com.kite.app.agent.contract.AgentSessionCapabilities
import com.kite.app.agent.contract.AgentSessionEvent
import com.kite.app.agent.contract.AgentSessionSummary
import com.kite.app.agent.contract.AgentStopReason
import com.kite.app.agent.contract.AgentToolCall
import com.kite.app.agent.contract.AgentToolCallPatch
import com.kite.app.agent.contract.AgentToolContent
import com.kite.app.agent.contract.AgentToolKind
import com.kite.app.agent.contract.AgentToolLocation
import com.kite.app.agent.contract.AgentToolStatus
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * ACP 类型只能在本适配边界内出现。所有不稳定 API 的 opt-in 也被限制在这里。
 */
@OptIn(UnstableApi::class)
object AcpAgentMapper {
    fun capabilities(
        source: AcpAgentCapabilities,
        authenticationMethods: List<AuthMethod> = emptyList()
    ): AgentCapabilities = AgentCapabilities(
        prompt = AgentPromptCapabilities(
            images = source.promptCapabilities.image,
            audio = source.promptCapabilities.audio,
            embeddedResources = source.promptCapabilities.embeddedContext
        ),
        sessions = AgentSessionCapabilities(
            load = source.loadSession,
            list = source.sessionCapabilities.list != null,
            resume = source.sessionCapabilities.resume != null,
            fork = source.sessionCapabilities.fork != null,
            close = source.sessionCapabilities.close != null,
            additionalDirectories = source.sessionCapabilities.additionalDirectories != null
        ),
        mcp = AgentMcpCapabilities(
            http = source.mcpCapabilities.http,
            sse = source.mcpCapabilities.sse
        ),
        authentication = AgentAuthenticationCapabilities(
            methods = authenticationMethods.map(::authenticationMethod),
            logout = source.auth.logout != null,
            extension = source.auth._meta.acpExtension()
        ),
        extension = source._meta.acpExtension()
    )

    fun authenticationMethod(source: AuthMethod): AgentAuthenticationMethod = when (source) {
        is AuthMethod.AgentAuth -> AgentAuthenticationMethod.AgentManaged(
            id = source.id.value,
            name = source.name,
            description = source.description,
            extension = source._meta.acpExtension()
        )
        is AuthMethod.TerminalAuth -> AgentAuthenticationMethod.Terminal(
            id = source.id.value,
            name = source.name,
            description = source.description,
            arguments = source.args.orEmpty(),
            environment = source.env.orEmpty(),
            extension = source._meta.acpExtension()
        )
        is AuthMethod.EnvVarAuth -> AgentAuthenticationMethod.EnvironmentVariables(
            id = source.id.value,
            name = source.name,
            description = source.description,
            variables = source.vars.map { variable ->
                AgentAuthenticationVariable(
                    name = variable.name,
                    label = variable.label,
                    secret = variable.secret,
                    optional = variable.optional,
                    extension = variable._meta.acpExtension()
                )
            },
            helpUrl = source.link,
            extension = source._meta.acpExtension()
        )
        is AuthMethod.UnknownAuthMethod -> AgentAuthenticationMethod.Extension(
            id = source.id.value,
            name = source.name,
            description = source.description,
            type = source.type,
            payload = source.rawJson.toString(),
            extension = source._meta.acpExtension()
        )
    }

    fun failure(label: String, error: Throwable): AgentOperationResult.Failure {
        val rpcError = error as? JsonRpcException
        val protocolPayload = rpcError?.let { source ->
            buildJsonObject {
                put("code", source.code)
                put("message", source.message)
                source.data?.let { put("data", it) }
            }.toString()
        }
        return AgentFailures.protocol(
            message = "$label 失败: ${error.message}",
            cause = error,
            code = when {
                rpcError?.isAuthenticationRequired() == true -> AgentFailureCode.AuthenticationRequired
                rpcError != null -> AgentFailureCode("acp_json_rpc_${rpcError.code}")
                else -> AgentFailureCode.ProtocolFailure
            },
            extension = protocolPayload?.let {
                AgentProtocolExtension(
                    protocol = ACP_PROTOCOL,
                    type = "json_rpc_error",
                    payload = it
                )
            }
        )
    }

    fun content(source: ContentBlock): AgentContent = when (source) {
        is ContentBlock.Text -> AgentContent.Text(
            text = source.text,
            annotations = annotations(source.annotations),
            extension = source._meta.acpExtension()
        )
        is ContentBlock.Image -> AgentContent.Image(
            data = source.data,
            mimeType = source.mimeType,
            uri = source.uri,
            annotations = annotations(source.annotations),
            extension = source._meta.acpExtension()
        )
        is ContentBlock.Audio -> AgentContent.Audio(
            data = source.data,
            mimeType = source.mimeType,
            annotations = annotations(source.annotations),
            extension = source._meta.acpExtension()
        )
        is ContentBlock.ResourceLink -> AgentContent.ResourceLink(
            name = source.name,
            uri = source.uri,
            description = source.description,
            mimeType = source.mimeType,
            size = source.size,
            title = source.title,
            annotations = annotations(source.annotations),
            extension = source._meta.acpExtension()
        )
        is ContentBlock.Resource -> when (val resource = source.resource) {
            is EmbeddedResourceResource.TextResourceContents -> AgentContent.EmbeddedText(
                text = resource.text,
                uri = resource.uri,
                mimeType = resource.mimeType,
                annotations = annotations(source.annotations),
                extension = resource._meta.acpExtension() ?: source._meta.acpExtension()
            )
            is EmbeddedResourceResource.BlobResourceContents -> AgentContent.EmbeddedBlob(
                data = resource.blob,
                uri = resource.uri,
                mimeType = resource.mimeType,
                annotations = annotations(source.annotations),
                extension = resource._meta.acpExtension() ?: source._meta.acpExtension()
            )
        }
    }

    fun configOptions(source: List<SessionConfigOption>): List<AgentConfigOption> = source.map(::configOption)

    /**
     * ACP 把模型选择作为独立于 Session Config Options 的正式通道。Kite SDK 仍以统一配置项
     * 投影给 Runtime，但保留专用 ID，写回时必须走 session/set_model，不能退化成普通配置键。
     */
    fun modelOption(currentModelId: String, source: List<ModelInfo>): AgentConfigOption.Select =
        AgentConfigOption.Select(
            id = ACP_SESSION_MODEL_CONFIG_ID,
            name = "模型",
            description = "由 Agent 的 ACP 模型通道提供",
            category = AgentConfigCategory.Model,
            currentValue = currentModelId,
            choices = source.map { model ->
                AgentConfigChoice(
                    value = model.modelId.value,
                    name = model.name,
                    description = model.description,
                    extension = model._meta.acpExtension()
                )
            }
        )

    fun configOption(source: SessionConfigOption): AgentConfigOption = when (source) {
        is SessionConfigOption.Select -> AgentConfigOption.Select(
            id = source.id.value,
            name = source.name,
            description = source.description,
            category = source.category?.value?.let(::AgentConfigCategory),
            currentValue = source.currentValue.value,
            choices = selectChoices(source.options),
            extension = source._meta.acpExtension()
        )
        is SessionConfigOption.BooleanOption -> AgentConfigOption.Toggle(
            id = source.id.value,
            name = source.name,
            description = source.description,
            category = source.category?.value?.let(::AgentConfigCategory),
            currentValue = source.currentValue,
            extension = source._meta.acpExtension()
        )
    }

    fun sessionEvent(source: SessionUpdate): AgentSessionEvent = when (source) {
        is SessionUpdate.UserMessageChunk -> AgentSessionEvent.MessageChunk(
            role = AgentMessageRole.User,
            content = content(source.content),
            messageId = source.messageId?.value,
            extension = source._meta.acpExtension()
        )
        is SessionUpdate.AgentMessageChunk -> AgentSessionEvent.MessageChunk(
            role = AgentMessageRole.Assistant,
            content = content(source.content),
            messageId = source.messageId?.value,
            extension = source._meta.acpExtension()
        )
        is SessionUpdate.AgentThoughtChunk -> AgentSessionEvent.MessageChunk(
            role = AgentMessageRole.Thought,
            content = content(source.content),
            messageId = source.messageId?.value,
            extension = source._meta.acpExtension()
        )
        is SessionUpdate.ToolCall -> AgentSessionEvent.ToolCallStarted(
            call = AgentToolCall(
                id = source.toolCallId.value,
                title = source.title,
                kind = source.kind?.toKite(),
                status = source.status?.toKite(),
                content = source.content.map(::toolContent),
                locations = source.locations.map(::toolLocation),
                rawInput = source.rawInput?.toString(),
                rawOutput = source.rawOutput?.toString()
            ),
            extension = source._meta.acpExtension()
        )
        is SessionUpdate.ToolCallUpdate -> AgentSessionEvent.ToolCallUpdated(
            update = toolCallPatch(source),
            extension = source._meta.acpExtension()
        )
        is SessionUpdate.PlanUpdate -> AgentSessionEvent.PlanUpdated(
            entries = source.entries.map { entry ->
                AgentPlanEntry(
                    content = entry.content,
                    priority = entry.priority.protocolValue(),
                    status = entry.status.protocolValue(),
                    extension = entry._meta.acpExtension()
                )
            },
            extension = source._meta.acpExtension()
        )
        is SessionUpdate.AvailableCommandsUpdate -> AgentSessionEvent.CommandsUpdated(
            commands = source.availableCommands.map { command ->
                AgentCommand(
                    name = command.name,
                    description = command.description,
                    inputHint = (command.input as? AvailableCommandInput.Unstructured)?.hint,
                    extension = command._meta.acpExtension()
                )
            }
        )
        is SessionUpdate.CurrentModeUpdate -> AgentSessionEvent.CurrentModeChanged(source.currentModeId.value)
        is SessionUpdate.ConfigOptionUpdate -> AgentSessionEvent.ConfigurationUpdated(
            options = configOptions(source.configOptions),
            extension = source._meta.acpExtension()
        )
        is SessionUpdate.SessionInfoUpdate -> AgentSessionEvent.SessionInfoChanged(
            title = source.title,
            updatedAt = source.updatedAt,
            extension = source._meta.acpExtension()
        )
        is SessionUpdate.UsageUpdate -> AgentSessionEvent.UsageChanged(
            used = source.used,
            size = source.size,
            cost = source.cost?.let { AgentCost(it.amount, it.currency, it._meta.acpExtension()) },
            extension = source._meta.acpExtension()
        )
        is SessionUpdate.UnknownSessionUpdate -> AgentSessionEvent.Extension(
            type = source.sessionUpdateType,
            payload = source.rawJson.toString(),
            metadata = source._meta.acpExtension()
        )
        is SessionUpdate.PlanUpdateV2 -> AgentSessionEvent.Extension(
            type = "plan_update",
            payload = source.plan.toString(),
            metadata = source._meta.acpExtension()
        )
        is SessionUpdate.PlanRemoved -> AgentSessionEvent.Extension(
            type = "plan_removed",
            payload = source.id,
            metadata = source._meta.acpExtension()
        )
    }

    fun permissionRequest(source: RequestPermissionRequest): AgentPermissionRequest = AgentPermissionRequest(
        sessionId = source.sessionId.value,
        toolCall = toolCallPatch(source.toolCall),
        options = source.options.map(::permissionOption),
        extension = source._meta.acpExtension()
    )

    fun permissionOutcome(source: RequestPermissionOutcome): AgentPermissionOutcome = when (source) {
        is RequestPermissionOutcome.Selected -> AgentPermissionOutcome.Selected(source.optionId.value)
        RequestPermissionOutcome.Cancelled -> AgentPermissionOutcome.Cancelled
    }

    fun permissionOutcome(source: AgentPermissionOutcome): RequestPermissionOutcome = when (source) {
        is AgentPermissionOutcome.Selected -> RequestPermissionOutcome.Selected(PermissionOptionId(source.optionId))
        AgentPermissionOutcome.Cancelled -> RequestPermissionOutcome.Cancelled
    }

    fun sessionSummary(source: SessionInfo): AgentSessionSummary = AgentSessionSummary(
        id = source.sessionId.value,
        cwd = source.cwd,
        title = source.title,
        updatedAt = source.updatedAt,
        additionalDirectories = source.additionalDirectories.orEmpty(),
        extension = source._meta.acpExtension()
    )

    fun stopReason(source: StopReason): AgentStopReason = AgentStopReason(source.protocolValue())

    private fun selectChoices(source: SessionConfigSelectOptions): List<AgentConfigChoice> = when (source) {
        is SessionConfigSelectOptions.Flat -> source.options.map { option ->
            AgentConfigChoice(
                value = option.value.value,
                name = option.name,
                description = option.description,
                extension = option._meta.acpExtension()
            )
        }
        is SessionConfigSelectOptions.Grouped -> source.groups.flatMap { group ->
            group.options.map { option ->
                AgentConfigChoice(
                    value = option.value.value,
                    name = option.name,
                    description = option.description,
                    groupId = group.group.value,
                    groupName = group.name,
                    extension = option._meta.acpExtension() ?: group._meta.acpExtension()
                )
            }
        }
    }

    private fun annotations(source: Annotations?): AgentContentAnnotations? = source?.let {
        AgentContentAnnotations(
            audience = it.audience.orEmpty().map { role -> role.protocolValue() },
            priority = it.priority,
            lastModified = it.lastModified,
            extension = it._meta.acpExtension()
        )
    }

    private fun toolCallPatch(source: SessionUpdate.ToolCallUpdate): AgentToolCallPatch = AgentToolCallPatch(
        id = source.toolCallId.value,
        title = source.title,
        kind = source.kind?.toKite(),
        status = source.status?.toKite(),
        content = source.content?.map(::toolContent),
        locations = source.locations?.map(::toolLocation),
        rawInput = source.rawInput?.toString(),
        rawOutput = source.rawOutput?.toString()
    )

    private fun toolContent(source: ToolCallContent): AgentToolContent = when (source) {
        is ToolCallContent.Content -> AgentToolContent.Content(content(source.content))
        is ToolCallContent.Diff -> AgentToolContent.Diff(
            path = source.path,
            newText = source.newText,
            oldText = source.oldText,
            extension = source._meta.acpExtension()
        )
        is ToolCallContent.Terminal -> AgentToolContent.Terminal(
            terminalId = source.terminalId,
            extension = source._meta.acpExtension()
        )
    }

    private fun toolLocation(source: ToolCallLocation): AgentToolLocation = AgentToolLocation(
        path = source.path,
        line = source.line?.toLong(),
        extension = source._meta.acpExtension()
    )

    private fun permissionOption(source: PermissionOption): AgentPermissionOption = AgentPermissionOption(
        id = source.optionId.value,
        name = source.name,
        kind = when (source.kind) {
            PermissionOptionKind.ALLOW_ONCE -> AgentPermissionKind.AllowOnce
            PermissionOptionKind.ALLOW_ALWAYS -> AgentPermissionKind.AllowAlways
            PermissionOptionKind.REJECT_ONCE -> AgentPermissionKind.RejectOnce
            PermissionOptionKind.REJECT_ALWAYS -> AgentPermissionKind.RejectAlways
        },
        extension = source._meta.acpExtension()
    )

    private fun ToolKind.toKite(): AgentToolKind = AgentToolKind(protocolValue())
    private fun ToolCallStatus.toKite(): AgentToolStatus = AgentToolStatus(protocolValue())

    private fun Enum<*>.protocolValue(): String = name.lowercase()

    private fun JsonRpcException.isAuthenticationRequired(): Boolean {
        val normalizedMessage = message.trim().lowercase().replace(' ', '_')
        return normalizedMessage == "authentication_required" ||
            data?.toString()?.contains("auth_required", ignoreCase = true) == true
    }

    private fun JsonElement?.acpExtension(): AgentProtocolExtension? = this?.let {
        AgentProtocolExtension(protocol = ACP_PROTOCOL, payload = it.toString())
    }

    private const val ACP_PROTOCOL = "acp"
}
