package com.kite.app.platform.runs

import android.content.Context
import com.kite.app.agent.config.AgentConfigCommandExecutionResult
import com.kite.app.agent.config.AgentConfigCommandExecutor
import com.kite.app.agent.process.AgentProcessFactory
import com.kite.app.agent.process.AgentProcessLaunch
import com.kite.app.agent.process.JavaAgentProcessFactory
import com.kite.app.foundation.workspace.WorkSurfaceRuntimeBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** 用户主动检查配置时才运行的低频 PRoot 命令，不参与页面绘制或后台轮询。 */
internal class AndroidAgentConfigCommandExecutor(
    context: Context,
    private val processFactory: AgentProcessFactory = JavaAgentProcessFactory()
) : AgentConfigCommandExecutor {
    private val appContext = context.applicationContext

    override suspend fun execute(
        argv: List<String>,
        cwd: String
    ): AgentConfigCommandExecutionResult = withContext(Dispatchers.IO) {
        if (argv.isEmpty()) return@withContext AgentConfigCommandExecutionResult.Failed("Agent 检查命令为空")
        val config = runCatching {
            WorkSurfaceRuntimeBridge.buildArgvExecConfig(appContext, cwd, argv)
        }.getOrElse { error ->
            return@withContext AgentConfigCommandExecutionResult.Failed(
                "Agent 检查命令准备失败：${error.message ?: "未知错误"}"
            )
        }
        val process = runCatching {
            processFactory.start(AgentProcessLaunch(config.command, config.env))
        }.getOrElse { error ->
            return@withContext AgentConfigCommandExecutionResult.Failed(
                "Agent 检查命令启动失败：${error.message ?: "未知错误"}"
            )
        }
        try {
            coroutineScope {
                val stdout = async(Dispatchers.IO) { process.stdoutLines.toList().boundedOutput() }
                val stderr = async(Dispatchers.IO) { process.stderrLines.toList().boundedOutput() }
                val exitCode = withTimeoutOrNull(COMMAND_TIMEOUT_MS) { process.awaitExit() }
                if (exitCode == null) {
                    process.stop()
                    stdout.cancel()
                    stderr.cancel()
                    AgentConfigCommandExecutionResult.Failed("Agent MCP 连接检查超时")
                } else {
                    AgentConfigCommandExecutionResult.Completed.of(exitCode, stdout.await(), stderr.await())
                }
            }
        } finally {
            process.close()
        }
    }

    private fun List<String>.boundedOutput(): List<String> = take(MAX_OUTPUT_LINES).map { it.take(MAX_LINE_LENGTH) }

    private companion object {
        const val COMMAND_TIMEOUT_MS = 30_000L
        const val MAX_OUTPUT_LINES = 256
        const val MAX_LINE_LENGTH = 2_048
    }
}
