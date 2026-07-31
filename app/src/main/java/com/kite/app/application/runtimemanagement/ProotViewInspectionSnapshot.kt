package com.kite.app.application.runtimemanagement

/**
 * PRoot View 工程检查快照。
 *
 * 这是 foundation/runtime 状态拥有者的只读投影，供工程页显示；不复制持久事实，不扫描文件树。
 * 字段缺失或不可用时取安全默认值，由投影层解释。
 */
data class ProotViewInspectionSnapshot(
    val available: Boolean = false,
    val enabled: Boolean = false,
    val runtimeSupported: Boolean = false,
    val containerReady: Boolean = false,
    val currentViewId: String = "",
    val environmentId: String = "",
    val spaceId: String = "",
    val workspacePath: String = "",
    val parentDepth: Int = 0,
    val upperAllocatedBytes: Long? = null,
    val upperLogicalBytes: Long = 0L,
    val scopeRootPaths: List<String> = emptyList(),
    val baseSealed: Boolean = false,
    val environments: List<ProotEnvironmentInspection> = emptyList(),
    val environmentOperation: ProotEnvironmentOperation = ProotEnvironmentOperation.Idle,
    val environmentOperationTarget: String = "",
    val environmentOperationError: String = "",
    val lastAcceptance: ProotViewAcceptanceResult? = null,
    val lastVerification: ProotViewVerificationResult? = null,
    val lastIsolationVerification: ProotEnvironmentIsolationResult? = null,
)

/** 单个环境头的只读投影；环境身份和 View 身份仍以 ProotViewStore 为准。 */
data class ProotEnvironmentInspection(
    val environmentId: String,
    val viewId: String,
    val active: Boolean,
    val parentDepth: Int,
    val workspacePath: String,
)

enum class ProotEnvironmentOperation {
    Idle,
    Creating,
    Switching,
    VerifyingAcceptance,
    VerifyingIsolation,
}

data class ProotViewAcceptanceResult(
    val checks: List<ProotViewAcceptanceCheck> = emptyList(),
    val environmentId: String = "",
    val viewId: String = "",
    val totalMs: Long = 0L,
    val atUnixMs: Long = 0L,
) {
    val success: Boolean
        get() = checks.isNotEmpty() && checks.all { it.passed }
}

data class ProotViewAcceptanceCheck(
    val id: String,
    val title: String,
    val passed: Boolean,
    val detail: String = "",
)

data class ProotEnvironmentIsolationResult(
    val success: Boolean,
    val firstEnvironmentId: String = "",
    val secondEnvironmentId: String = "",
    val rootIsolated: Boolean = false,
    val workspaceIsolated: Boolean = false,
    val exchangeShared: Boolean = false,
    val baseUntouched: Boolean = false,
    val originalEnvironmentRestored: Boolean = false,
    val message: String = "",
    val atUnixMs: Long = 0L,
)

data class ProotViewVerificationResult(
    val success: Boolean,
    val runCount: Long = 0L,
    val viewId: String = "",
    val environmentId: String = "",
    val fileSha256: String = "",
    val message: String = "",
    val atUnixMs: Long = 0L,
)
