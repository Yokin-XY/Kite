package com.kite.app.application.runtimemanagement

import kotlinx.coroutines.flow.StateFlow

/** 运行管理 Feature 的事实入口；实现负责组合现有 Store，Feature 不直接读取它们。 */
interface RuntimeManagementGateway {
    val snapshots: StateFlow<RuntimeManagementSnapshot>

    fun currentSnapshot(): RuntimeManagementSnapshot

    fun refresh(force: Boolean = false)
}

interface RuntimeManagementDependenciesOwner {
    val runtimeManagementGateway: RuntimeManagementGateway
}
