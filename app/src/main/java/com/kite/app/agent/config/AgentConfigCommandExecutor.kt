package com.kite.app.agent.config

/** 低频 Agent 配置检查命令；原始输出只允许留在适配器内部解析。 */
fun interface AgentConfigCommandExecutor {
    suspend fun execute(argv: List<String>, cwd: String): AgentConfigCommandExecutionResult
}

sealed interface AgentConfigCommandExecutionResult {
    class Completed internal constructor(
        internal val exitCode: Int,
        internal val stdout: List<String>,
        internal val stderr: List<String>
    ) : AgentConfigCommandExecutionResult {
        override fun toString(): String =
            "Completed(exitCode=$exitCode, stdout=[REDACTED], stderr=[REDACTED])"

        companion object {
            fun of(exitCode: Int, stdout: List<String>, stderr: List<String> = emptyList()): Completed =
                Completed(exitCode, stdout.toList(), stderr.toList())
        }
    }

    data class Failed(val message: String) : AgentConfigCommandExecutionResult
}
