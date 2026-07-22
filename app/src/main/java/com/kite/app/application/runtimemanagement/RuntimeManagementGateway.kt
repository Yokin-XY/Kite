package com.kite.app.application.runtimemanagement

import kotlinx.coroutines.flow.StateFlow

/** 运行管理 Feature 的事实入口；实现负责组合现有 Store，Feature 不直接读取它们。 */
interface RuntimeManagementGateway {
    val snapshots: StateFlow<RuntimeManagementSnapshot>

    fun currentSnapshot(): RuntimeManagementSnapshot

    fun refresh(force: Boolean = false)

    suspend fun endTerminal(sessionId: String): RuntimeManagementDispatchResult

    suspend fun endProcess(processId: String, pid: Int): RuntimeManagementDispatchResult

    suspend fun endProcessTree(processIds: List<String>): RuntimeManagementDispatchResult =
        RuntimeManagementDispatchResult.rejected("process_tree_not_supported")

    suspend fun stopBackgroundRuntime(runtimeId: String): RuntimeManagementDispatchResult

    suspend fun restartBackgroundRuntime(runtimeId: String): RuntimeManagementDispatchResult
}

interface RuntimeManagementDependenciesOwner {
    val runtimeManagementGateway: RuntimeManagementGateway

    val runtimeManagementCoordinator: RuntimeManagementCoordinator
}

data class RuntimeManagementDispatchResult(
    val accepted: Boolean,
    val message: String = ""
) {
    companion object {
        fun accepted(message: String = "accepted") = RuntimeManagementDispatchResult(true, message)

        fun rejected(message: String) = RuntimeManagementDispatchResult(false, message)
    }
}
