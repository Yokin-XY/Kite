package com.kite.app.application.resources

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * 一次资源计划准备所绑定的持久化向导代次。
 *
 * environmentId 是安装计划的互斥范围；instanceId + generation 用于阻止旧向导的
 * 迟到结果写回已经取消或重新创建的计划。
 */
internal data class ResourceInstallPreparationToken(
    val environmentId: String,
    val targetResourceId: String,
    val instanceId: String,
    val generation: Long,
)

/**
 * 进程级资源计划准备任务表。这里只拥有协程任务，不拥有安装计划或资源状态事实。
 */
internal class ResourceInstallPreparationFlights(
    private val scope: CoroutineScope,
) {
    private data class Flight(
        val token: ResourceInstallPreparationToken,
        val job: Job,
    )

    private val lock = Any()
    private val flightsByEnvironment = mutableMapOf<String, Flight>()

    fun launch(
        token: ResourceInstallPreparationToken,
        block: suspend (ResourceInstallPreparationToken) -> Unit,
    ): Boolean {
        lateinit var job: Job
        synchronized(lock) {
            val current = flightsByEnvironment[token.environmentId]
            if (current?.job?.isActive == true) return false
            job = scope.launch(start = CoroutineStart.LAZY) {
                try {
                    block(token)
                } finally {
                    synchronized(lock) {
                        if (flightsByEnvironment[token.environmentId]?.token == token) {
                            flightsByEnvironment.remove(token.environmentId)
                        }
                    }
                }
            }
            flightsByEnvironment[token.environmentId] = Flight(token, job)
        }
        job.start()
        return true
    }

    fun cancel(
        environmentId: String,
        targetResourceId: String? = null,
        generation: Long? = null,
    ): Boolean {
        val flight = synchronized(lock) {
            val current = flightsByEnvironment[environmentId] ?: return false
            if (targetResourceId != null && current.token.targetResourceId != targetResourceId) return false
            if (generation != null && current.token.generation != generation) return false
            flightsByEnvironment.remove(environmentId)
            current
        }
        flight.job.cancel()
        return true
    }

    fun isCurrent(token: ResourceInstallPreparationToken): Boolean = synchronized(lock) {
        flightsByEnvironment[token.environmentId]?.let { flight ->
            flight.token == token && flight.job.isActive
        } == true
    }

    /**
     * 把“仍是当前任务”的校验与最终事实提交放在同一临界区。
     * block 必须保持为短小的同步状态写入，不能在这里做文件、进程或网络工作。
     */
    fun commitIfCurrent(
        token: ResourceInstallPreparationToken,
        block: () -> Unit,
    ): Boolean = synchronized(lock) {
        val current = flightsByEnvironment[token.environmentId]
        if (current?.token != token || !current.job.isActive) return false
        block()
        true
    }
}
