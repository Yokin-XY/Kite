package com.kite.app.application.runtimemanagement

import kotlinx.coroutines.flow.StateFlow

/**
 * PRoot View 工程检查网关。
 *
 * 包装 foundation/runtime 的 ProotViewStore 只读查询为状态流，供设置工程页投影。
 * 工程页只发出刷新、创建或切换意图并 collect [snapshots]，不直接扫描文件树或复制持久事实。
 */
interface ProotViewInspectionGateway {
    val snapshots: StateFlow<ProotViewInspectionSnapshot>

    fun currentSnapshot(): ProotViewInspectionSnapshot

    fun refresh()

    /** 复用真实普通启动与双环境夹具，运行固定的底层通用验收协议。 */
    fun runAcceptance()

    /** 触发普通 Ubuntu View 离线验证（T014e）；结果回填到 snapshot.lastVerification。 */
    fun runVerification()

    /** 创建一个自动编号的工程测试环境；环境根和工作区均由统一控制面分配。 */
    fun createEnvironment()

    /** 切换唯一活跃环境；旧环境进程收口和指针提交由 platform 编排层负责。 */
    fun switchEnvironment(environmentId: String)

    /** 运行双环境文件隔离与显式共享夹具；完成后恢复触发前的活跃环境。 */
    fun runEnvironmentIsolationVerification()
}

interface ProotViewInspectionDependenciesOwner {
    val prootViewInspectionGateway: ProotViewInspectionGateway
}
