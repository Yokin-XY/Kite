package com.kite.app.application.resources

import com.kite.app.resources.KiteResourcePlanSnapshot
import com.kite.app.resources.KiteResourceInstallStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

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
        instanceId: String? = null,
        generation: Long? = null,
    ): Boolean {
        val flight = synchronized(lock) {
            val current = flightsByEnvironment[environmentId] ?: return false
            if (targetResourceId != null && current.token.targetResourceId != targetResourceId) return false
            if (instanceId != null && current.token.instanceId != instanceId) return false
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

/** 同一环境的计划创建、恢复与取消共用一条生命周期通道。 */
internal class ResourcePlanLifecycleGate {
    private val locks = ConcurrentHashMap<String, Mutex>()

    suspend fun <T> withEnvironment(environmentId: String, block: suspend () -> T): T {
        val key = environmentId.trim().ifBlank { "default" }
        return locks.computeIfAbsent(key) { Mutex() }.withLock { block() }
    }
}

/** 取消只能消费调用方开始时观察到的同一份计划代次。 */
internal object ResourcePlanCancellationPolicy {
    fun owns(
        current: KiteResourcePlanSnapshot,
        expectedTargetResourceId: String,
        expectedGeneration: Long,
        requestedTargetResourceId: String,
    ): Boolean =
        current.targetResourceId == expectedTargetResourceId &&
            current.generation == expectedGeneration &&
            (current.targetResourceId.isBlank() || current.targetResourceId == requestedTargetResourceId)

    /**
     * 返回键只消费还没有开始执行的计划：准备阶段，或全部步骤仍为 pending 的已就绪计划。
     * 一旦任何步骤进入 running/done/failed/blocked，就只能隐藏页面或走显式取消。
     */
    fun canCancelBeforeFirstStart(
        current: KiteResourcePlanSnapshot,
        requestedTargetResourceId: String,
    ): Boolean {
        if (current.targetResourceId != requestedTargetResourceId) return false
        if (current.isPreparing) return true
        if (!current.isActive || current.resourceIds.isEmpty()) return false
        return current.resourceIds.all { resourceId ->
            current.stepStatus(resourceId) == KiteResourceInstallStore.PLAN_STEP_PENDING
        }
    }
}
