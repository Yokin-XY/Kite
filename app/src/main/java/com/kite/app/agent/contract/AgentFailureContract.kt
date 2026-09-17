package com.kite.app.agent.contract

import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

enum class AgentFailurePhase {
    Installation,
    Launch,
    Initialization,
    Protocol,
    Authentication,
    Configuration,
    Session,
    Network,
    Runtime,
}

enum class AgentFailureRecovery {
    Retry,
    Authenticate,
    Reconfigure,
    RepairRuntime,
    None,
}

/** Agent 无关的失败语义；原始协议错误仍保留在 Failure.extension。 */
data class AgentFailureDetails(
    val phase: AgentFailurePhase,
    val retryable: Boolean,
    val recovery: AgentFailureRecovery,
)

/**
 * 各协议 Adapter 统一创建失败结果的入口。
 *
 * 这里根据异常类型和操作阶段投影通用语义，不按 Agent 名称或错误文案做特判。
 */
object AgentFailures {
    fun launch(message: String, cause: Throwable): AgentOperationResult.Failure =
        AgentOperationResult.Failure(
            message = message,
            cause = cause,
            code = AgentFailureCode.LaunchFailed,
            details = AgentFailureDetails(
                phase = AgentFailurePhase.Launch,
                retryable = false,
                recovery = AgentFailureRecovery.RepairRuntime,
            ),
        )

    fun initialize(
        message: String,
        cause: Throwable,
        protocolCode: AgentFailureCode = AgentFailureCode.InitializationFailed,
        extension: AgentProtocolExtension? = null,
    ): AgentOperationResult.Failure {
        val network = cause.hasNetworkCause()
        return AgentOperationResult.Failure(
            message = message,
            cause = cause,
            code = protocolCode,
            extension = extension,
            details = AgentFailureDetails(
                phase = if (network) AgentFailurePhase.Network else AgentFailurePhase.Initialization,
                retryable = true,
                recovery = AgentFailureRecovery.Retry,
            ),
        )
    }

    fun protocol(
        message: String,
        cause: Throwable,
        code: AgentFailureCode = AgentFailureCode.ProtocolFailure,
        extension: AgentProtocolExtension? = null,
    ): AgentOperationResult.Failure {
        val authentication = code == AgentFailureCode.AuthenticationRequired
        val network = !authentication && cause.hasNetworkCause()
        return AgentOperationResult.Failure(
            message = message,
            cause = cause,
            code = code,
            extension = extension,
            details = AgentFailureDetails(
                phase = when {
                    authentication -> AgentFailurePhase.Authentication
                    network -> AgentFailurePhase.Network
                    else -> AgentFailurePhase.Protocol
                },
                retryable = !authentication,
                recovery = if (authentication) {
                    AgentFailureRecovery.Authenticate
                } else {
                    AgentFailureRecovery.Retry
                },
            ),
        )
    }

    private fun Throwable.hasNetworkCause(): Boolean = generateSequence(this) { it.cause }
        .take(MAX_CAUSE_DEPTH)
        .any { error ->
            error is UnknownHostException ||
                error is ConnectException ||
                error is SocketTimeoutException
        }

    private const val MAX_CAUSE_DEPTH = 8
}
