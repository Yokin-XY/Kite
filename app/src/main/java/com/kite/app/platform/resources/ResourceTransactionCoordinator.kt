package com.kite.app.platform.resources

/**
 * 资源事务协调器——已退役。
 * official_command 模式下安装即原子（官方安装器自行保证），不再需要六步事务。
 * 保留空壳供既有调用点编译。
 */
internal class ResourceTransactionCoordinator {
    fun beginTransaction(@Suppress("UNUSED_PARAMETER") resourceId: String): Boolean = true
    fun commitTransaction(@Suppress("UNUSED_PARAMETER") resourceId: String): Boolean = true
    fun abortTransaction(@Suppress("UNUSED_PARAMETER") resourceId: String): Boolean = true
}
