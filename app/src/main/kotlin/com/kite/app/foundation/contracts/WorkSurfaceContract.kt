package com.kite.app.foundation.contracts

import android.content.Context
import java.io.File

/**
 * T4.2:runtime 对 workspace 工作面的依赖反转契约(目标定义)。
 *
 * 背景:runtime 多处调用 workspace.WorkSurfaceRuntimeBridge(实现类)的方法
 * (getSavedContainer 调 16 次等),构成 runtime→workspace 反向依赖。本接口把 runtime
 * 需要的方法抽象到 contracts(两子包都依赖的中立层),让 runtime 依赖接口而非具体类。
 *
 * 签名与 WorkSurfaceRuntimeBridge(object)逐方法对齐,使其可 implement 本接口。
 * 返回类型均用 contracts 内的纯数据(ContainerRecord/RuntimeBoundarySnapshot/
 * ContainerExecConfig),不引入 workspace 类型,保证接口纯净。
 *
 * 接入状态(本轮):接口定义就绪。完整接入(WorkSurfaceRuntimeBridge implement +
 * runtime 经 Host 走接口 + 改 34 调用点)作为后续 T4.2 收尾,因涉及逐方法精确 override
 * 与大量调用点迁移,需谨慎分步。
 */
interface WorkSurfaceContract {
    fun getSavedContainer(context: Context): ContainerRecord?
    fun getLogsDir(context: Context): File
    fun resolveRuntimeSnapshot(context: Context, container: ContainerRecord? = null): RuntimeBoundarySnapshot
    fun describeHostPath(context: Context, path: String?, container: ContainerRecord? = null): String
    fun actionRouteLabel(action: RuntimeActionKind): String
    fun hostPathAliases(container: ContainerRecord): Set<String>
    fun hostPathAliases(rootfsPath: String, workspacePath: String): Set<String>
    fun containerPathAliases(): Set<String>
    fun resolveActiveContainer(context: Context): ContainerRecord
    fun markContainerStopped(context: Context)
}
