package com.kite.app.foundation.toolchain

import android.content.Context

data class ResourceInstallRecoverySummary(
    val examined: Int = 0,
    val restored: Int = 0,
    val committed: Int = 0,
    val active: Int = 0,
    val failed: Int = 0,
)

/**
 * foundation 层(toolchain)对"资源安装登记"的依赖反转契约。
 *
 * 背景:ToolchainPackInstaller(底层工具链安装器)原本直接 import
 * com.kite.app.resources.KiteResourceInstallStore / KiteResourceRegistry(上层业务层),
 * 调它们的 registrySnapshot/markInstalling/markInstalled/markFailed 来查/写工具链资源的安装状态。
 * 这构成反向依赖。
 *
 * 解法:foundation 只定义本接口,业务层(KiteTaskContractInitializer ContentProvider)
 * 在启动时注入实现,ToolchainPackInstaller 通过 Host 读取,不再 hardcode 上层类。
 *
 * 状态字符串值与 KiteResourceRegistry 对齐(STATUS_*),由本接口 companion 暴露常量,
 * 使 foundation 不依赖业务层常量。
 */
interface ToolchainResourcePort {
    /** 在启动工具链前恢复资源目录留下的轻量更新事务。 */
    fun recoverInterruptedInstalls(context: Context): ResourceInstallRecoverySummary =
        ResourceInstallRecoverySummary()

    fun currentEnvironmentId(context: Context): String

    /** 查询指定资源的登记状态(STATUS_* 之一),不存在返回空串。 */
    fun statusOf(context: Context, resourceId: String, environmentId: String): String

    /** 查询当前环境中指定资源的登记版本。 */
    fun versionOf(context: Context, resourceId: String, environmentId: String): String

    /** 标记资源为安装中。 */
    fun markInstalling(context: Context, resourceId: String, runId: String?, environmentId: String)

    /** 标记资源为已安装。 */
    fun markInstalled(
        context: Context,
        resourceId: String,
        version: String?,
        runId: String?,
        summary: String?,
        environmentId: String
    )

    /** 标记资源为安装失败。 */
    fun markFailed(context: Context, resourceId: String, runId: String?, reason: String?, environmentId: String)

    companion object {
        const val STATUS_INSTALLED = "installed"
        const val STATUS_FAILED = "failed"
        const val STATUS_INSTALLING = "installing"
    }
}

/**
 * ToolchainResourcePort 的全局注入点。
 * 由业务层 ContentProvider 在应用启动时 install,供 ToolchainPackInstaller 读取。
 */
object ToolchainResourcePortHost {
    @Volatile
    private var port: ToolchainResourcePort? = null

    fun install(port: ToolchainResourcePort) {
        this.port = port
    }

    fun get(): ToolchainResourcePort =
        port ?: error("ToolchainResourcePort 尚未注入;应在 KiteTaskContractInitializer 中 install。")
}
