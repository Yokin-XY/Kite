package com.kite.app.application.runtimemanagement

/**
 * View 层巡检快照——已退役，保留完整数据形状供设置页面编译。
 * 所有嵌套结果类型默认 null / 空值，渲染"未运行"。
 */

data class ProotViewInspectionSnapshot(
    val viewHead: String = "",
    val baseHead: String = "",
    val enabled: Boolean = false,
    val baseSealed: Boolean = false,
    val runtimeSupported: Boolean = false,
    val available: Boolean = false,
    val environmentId: String = "",
    val spaceId: String = "",
    val workspacePath: String = "",
    val currentViewId: String = "",
    val parentDepth: Int = 0,
    val environments: List<ProotEnvironmentSnapshot> = emptyList(),
    val environmentOperation: ProotEnvironmentOperation = ProotEnvironmentOperation.None,
    val environmentOperationTarget: String = "",
    val environmentOperationError: String = "",
    val lastIsolationVerification: ProotIsolationVerificationResult? = null,
    val lastAcceptance: ProotAcceptanceResult? = null,
    val upperAllocatedBytes: Long? = null,
    val upperLogicalBytes: Long = 0,
    val scopeRootPaths: List<String> = emptyList(),
    val lastVerification: ProotAcceptanceResult? = null,
)

data class ProotEnvironmentSnapshot(
    val environmentId: String = "",
    val spaceId: String = "",
    val viewId: String = "",
    val workspacePath: String = "",
    val enabled: Boolean = false,
    val active: Boolean = false,
)

data class ProotIsolationVerificationResult(
    val success: Boolean = false,
    val firstEnvironmentId: String = "",
    val secondEnvironmentId: String = "",
    val rootIsolated: Boolean = false,
    val workspaceIsolated: Boolean = false,
    val exchangeShared: Boolean = false,
    val baseUntouched: Boolean = false,
    val originalEnvironmentRestored: Boolean = false,
    val message: String = "",
)

data class ProotAcceptanceResult(
    val success: Boolean = false,
    val checks: List<ProotAcceptanceCheck> = emptyList(),
    val totalMs: Long = 0,
    val environmentId: String = "",
    val viewId: String = "",
    val fileSha256: String = "",
    val runCount: Int = 0,
    val message: String = "",
)

data class ProotAcceptanceCheck(
    val passed: Boolean = false,
    val title: String = "",
    val detail: String = "",
)

data class ProotEnvironmentIsolationResult(
    val success: Boolean = false,
    val atUnixMs: Long = 0,
    val firstEnvironmentId: String = "",
    val secondEnvironmentId: String = "",
    val rootIsolated: Boolean = false,
    val workspaceIsolated: Boolean = false,
    val exchangeShared: Boolean = false,
    val baseUntouched: Boolean = false,
    val originalEnvironmentRestored: Boolean = false,
    val message: String = "",
)

enum class ProotEnvironmentOperation { None, Creating, Switching, VerifyingAcceptance, VerifyingIsolation, Idle }
