package com.kite.app.foundation.runtime

import java.io.File

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

/** 调用方在业务进程创建前能够证明的封闭属性；缺省表示未知，不能把未声明需求当成安全证明。 */
internal enum class RuntimeExecutionGuarantee(val wireValue: String) {
    NO_CHILD_PROCESS("no_child_process"),
    VERIFIED_NATIVE_IMPORTS("verified_native_imports"),
}

internal object RuntimeExecutionGuaranteeCodec {
    private val byWireValue = RuntimeExecutionGuarantee.entries.associateBy { it.wireValue }

    fun normalize(values: Collection<String>): Set<String>? {
        val normalized = values.map { it.trim().lowercase() }.filter(String::isNotBlank).toSet()
        return normalized.takeIf { candidates -> candidates.all(byWireValue::containsKey) }
    }

    fun decode(values: Collection<String>): Set<RuntimeExecutionGuarantee>? = normalize(values)
        ?.mapTo(linkedSetOf()) { value -> checkNotNull(byWireValue[value]) }
}

internal object RuntimeExecutionGuaranteeEvidenceCodec {
    private val pythonAbi = Regex("cpython-[0-9]+-aarch64-linux-gnu")

    fun normalize(values: Map<String, String>): Map<String, String>? {
        if (values.keys.any { it != PYTHON_ABI }) return null
        val normalized = values.mapValues { (_, value) -> value.trim().lowercase() }
        return normalized.takeIf { evidence ->
            evidence[PYTHON_ABI]?.let(pythonAbi::matches) != false
        }
    }

    const val PYTHON_ABI = "pythonAbi"
}

internal enum class RuntimeFallbackPolicy {
    /** 只允许在任何业务执行开始前换到后续 Provider。 */
    BEFORE_START_ONLY,

    /** 当前请求不允许自动换车道。 */
    DISABLED,
}

/** 由受信任调用方声明的单次运行文件绑定；物理 PRoot 参数仍只由运行时统一构造。 */
internal data class RuntimeFilesystemBinding(
    val sourcePath: String,
    val targetPath: String,
    val role: String,
) {
    init {
        require(File(sourcePath).isAbsolute && '\u0000' !in sourcePath) {
            "runtime_filesystem_binding_source_invalid"
        }
        require(targetPath.startsWith("/") && '\u0000' !in targetPath) {
            "runtime_filesystem_binding_target_invalid"
        }
        require(targetPath.split('/').none { it == "." || it == ".." }) {
            "runtime_filesystem_binding_target_unsafe"
        }
        require(role.isNotBlank() && role.all { it.isLetterOrDigit() || it == '-' || it == '_' }) {
            "runtime_filesystem_binding_role_invalid"
        }
    }
}

/**
 * 入口无关的执行请求。运行实例身份仍由 Orchestrator/Registry 持有，本对象只表达 Provider 选择所需事实。
 */
internal data class RuntimeExecutionRequest(
    val payload: RuntimeExecutionPayload,
    val workingDirectory: String? = null,
    val environment: Map<String, String> = emptyMap(),
    val requirements: Set<RuntimeExecutionRequirement> = emptySet(),
    val guarantees: Set<RuntimeExecutionGuarantee> = emptySet(),
    val guaranteeEvidence: Map<String, String> = emptyMap(),
    val filesystemBindings: List<RuntimeFilesystemBinding> = emptyList(),
    val fallbackPolicy: RuntimeFallbackPolicy = RuntimeFallbackPolicy.BEFORE_START_ONLY,
) {
    init {
        require(workingDirectory?.contains('\u0000') != true) { "runtime_working_directory_contains_nul" }
        require(environment.keys.all(ENVIRONMENT_NAME::matches)) { "runtime_environment_name_invalid" }
        require(environment.values.none { '\u0000' in it }) { "runtime_environment_value_contains_nul" }
        require(guaranteeEvidence.keys.all(ENVIRONMENT_NAME::matches)) {
            "runtime_guarantee_evidence_name_invalid"
        }
        require(guaranteeEvidence.values.none { '\u0000' in it }) {
            "runtime_guarantee_evidence_value_contains_nul"
        }
    }

    companion object {
        private val ENVIRONMENT_NAME = Regex("[A-Za-z_][A-Za-z0-9_]*")
    }
}
