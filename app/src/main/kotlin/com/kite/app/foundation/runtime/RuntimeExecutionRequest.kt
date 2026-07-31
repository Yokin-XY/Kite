package com.kite.app.foundation.runtime

/** Runtime Planner 可以识别的请求形状；普通 argv、兼容命令文本和原生能力不会互相冒充。 */
internal sealed interface RuntimeExecutionPayload {
    data class Argv(
        val executable: String,
        val arguments: List<String> = emptyList(),
    ) : RuntimeExecutionPayload {
        init {
            require(executable.isNotBlank()) { "runtime_executable_missing" }
            require('\u0000' !in executable) { "runtime_executable_contains_nul" }
            require(arguments.none { '\u0000' in it }) { "runtime_argument_contains_nul" }
        }
    }

    /** 只服务旧入口的受限单命令解析；复杂 shell 仍由 PRoot Provider 完整处理。 */
    data class CommandLine(val command: String) : RuntimeExecutionPayload {
        init {
            require(command.isNotBlank()) { "runtime_command_missing" }
            require('\u0000' !in command) { "runtime_command_contains_nul" }
        }
    }

    data class NativeCapability(
        val capabilityId: String,
        val parameters: Map<String, String> = emptyMap(),
    ) : RuntimeExecutionPayload {
        init {
            require(CAPABILITY_ID.matches(capabilityId)) { "runtime_capability_id_invalid" }
            require(parameters.keys.all(ENVIRONMENT_NAME::matches)) {
                "runtime_capability_parameter_name_invalid"
            }
            require(parameters.values.none { '\u0000' in it }) {
                "runtime_capability_parameter_contains_nul"
            }
        }
    }

    companion object {
        private val CAPABILITY_ID = Regex("[a-z][a-z0-9_.-]*")
        private val ENVIRONMENT_NAME = Regex("[A-Za-z_][A-Za-z0-9_]*")
    }
}

internal enum class RuntimeExecutionRequirement {
    FULL_LINUX,
    ANDROID_NATIVE,
    INTERACTIVE_PTY,
    FILESYSTEM_VIEW,
    CHILD_PROCESS,
    UNVERIFIED_NATIVE_EXTENSION,
}

internal enum class RuntimeFallbackPolicy {
    /** 只允许在任何业务执行开始前换到后续 Provider。 */
    BEFORE_START_ONLY,

    /** 当前请求不允许自动换车道。 */
    DISABLED,
}

/**
 * 入口无关的执行请求。运行实例身份仍由 Orchestrator/Registry 持有，本对象只表达 Provider 选择所需事实。
 */
internal data class RuntimeExecutionRequest(
    val payload: RuntimeExecutionPayload,
    val workingDirectory: String? = null,
    val environment: Map<String, String> = emptyMap(),
    val requirements: Set<RuntimeExecutionRequirement> = emptySet(),
    val fallbackPolicy: RuntimeFallbackPolicy = RuntimeFallbackPolicy.BEFORE_START_ONLY,
) {
    init {
        require(workingDirectory?.contains('\u0000') != true) { "runtime_working_directory_contains_nul" }
        require(environment.keys.all(ENVIRONMENT_NAME::matches)) { "runtime_environment_name_invalid" }
        require(environment.values.none { '\u0000' in it }) { "runtime_environment_value_contains_nul" }
    }

    companion object {
        private val ENVIRONMENT_NAME = Regex("[A-Za-z_][A-Za-z0-9_]*")
    }
}
