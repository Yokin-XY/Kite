package com.kite.app.application.runtimemanagement

import kotlinx.coroutines.flow.StateFlow

/**
 * View 层巡检网关——已退役。
 * 接口形状保留供设置页面编译；所有操作均为空实现。
 */
interface ProotViewInspectionGateway {
    val snapshots: StateFlow<ProotViewInspectionSnapshot>
    fun currentSnapshot(): ProotViewInspectionSnapshot = ProotViewInspectionSnapshot()
    fun refresh(): ProotViewInspectionSnapshot = ProotViewInspectionSnapshot()
    fun runAcceptance() { /* retired */ }
    fun runVerification() { /* retired */ }
    fun createEnvironment() { /* retired */ }
    fun switchEnvironment(@Suppress("UNUSED_PARAMETER") environmentId: String) { /* retired */ }
    fun runEnvironmentIsolationVerification() { /* retired */ }
}

/** View 层依赖注入标识。 */
interface ProotViewInspectionDependenciesOwner {
    val prootViewInspectionGateway: ProotViewInspectionGateway
}
