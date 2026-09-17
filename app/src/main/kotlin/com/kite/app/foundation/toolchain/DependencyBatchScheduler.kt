package com.kite.app.foundation.toolchain

import com.kite.app.foundation.concurrency.WriteScopeLeaseRegistry
import java.util.concurrent.ExecutorCompletionService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

internal data class DependencyBatchTask<K : Any, T : Any>(
    val key: K,
    val dependencies: Set<K> = emptySet(),
    val writeScopes: Set<String> = emptySet(),
    val execute: () -> T,
)

internal sealed interface DependencyBatchTaskOutcome<K : Any, T : Any> {
    val key: K

    data class Executed<K : Any, T : Any>(
        override val key: K,
        val value: T?,
        val successful: Boolean,
        val failureReason: String? = null,
    ) : DependencyBatchTaskOutcome<K, T>

    data class DependencyBlocked<K : Any, T : Any>(
        override val key: K,
        val failedDependencies: List<K>,
    ) : DependencyBatchTaskOutcome<K, T>
}

internal data class DependencyBatchReport<K : Any, T : Any>(
    val outcomes: List<DependencyBatchTaskOutcome<K, T>>,
    val maximumActiveTasks: Int,
)

internal sealed interface DependencyBatchDecision<K : Any, T : Any> {
    data class Completed<K : Any, T : Any>(
        val report: DependencyBatchReport<K, T>,
    ) : DependencyBatchDecision<K, T>

    data class Blocked<K : Any, T : Any>(
        val reason: String,
    ) : DependencyBatchDecision<K, T>
}

/**
 * 只按调用方提交的任务键和显式依赖释放工作，不理解资源、包、命令或页面身份。
 * 图合同不完整时在创建线程和执行首个任务前失败关闭。
 */
internal object DependencyBatchScheduler {
    fun <K : Any, T : Any> executeOrdered(
        tasks: List<DependencyBatchTask<K, T>>,
        maximumConcurrency: Int,
        isSuccessful: (T) -> Boolean,
    ): DependencyBatchDecision<K, T> {
        validate(tasks, maximumConcurrency)?.let { reason ->
            return DependencyBatchDecision.Blocked(reason)
        }
        if (tasks.isEmpty()) {
            return DependencyBatchDecision.Completed(
                DependencyBatchReport(emptyList(), maximumActiveTasks = 0),
            )
        }

        val indexByKey = tasks.mapIndexed { index, task -> task.key to index }.toMap()
        val outcomes = arrayOfNulls<DependencyBatchTaskOutcome<K, T>>(tasks.size)
        val pending = tasks.indices.toMutableSet()
        val running = linkedSetOf<Int>()
        val writeScopeLeases = WriteScopeLeaseRegistry()
        val executor = Executors.newFixedThreadPool(maximumConcurrency) { runnable ->
            Thread(runnable, "KiteDependencyBatch").apply { isDaemon = true }
        }
        val completions = ExecutorCompletionService<IndexedOutcome<K, T>>(executor)
        var maximumActiveTasks = 0

        return try {
            while (pending.isNotEmpty() || running.isNotEmpty()) {
                var progressed = false
                val pendingSnapshot = pending.toList()
                pendingSnapshot.forEach { index ->
                    if (running.size >= maximumConcurrency) return@forEach
                    val task = tasks[index]
                    val dependencyOutcomes = task.dependencies.mapNotNull { dependency ->
                        val dependencyIndex = checkNotNull(indexByKey[dependency])
                        dependency to outcomes[dependencyIndex]
                    }
                    val failedDependencies = dependencyOutcomes
                        .filter { (_, outcome) -> outcome != null && !outcome.isSuccessful() }
                        .map { (dependency, _) -> dependency }
                    if (failedDependencies.isNotEmpty()) {
                        outcomes[index] = DependencyBatchTaskOutcome.DependencyBlocked(
                            key = task.key,
                            failedDependencies = failedDependencies,
                        )
                        pending.remove(index)
                        progressed = true
                        return@forEach
                    }
                    if (dependencyOutcomes.any { (_, outcome) -> outcome == null }) return@forEach

                    val scopeOwnerId = scopeOwnerId(index)
                    if (writeScopeLeases.tryAcquire(scopeOwnerId, task.writeScopes) != null) return@forEach

                    completions.submit {
                        IndexedOutcome(index, execute(task, isSuccessful))
                    }
                    pending.remove(index)
                    running.add(index)
                    maximumActiveTasks = maxOf(maximumActiveTasks, running.size)
                    progressed = true
                }

                if (running.isNotEmpty()) {
                    val completed = completions.take().get()
                    writeScopeLeases.release(scopeOwnerId(completed.index))
                    outcomes[completed.index] = completed.outcome
                    running.remove(completed.index)
                    progressed = true
                }
                check(progressed) { "dependency_batch_scheduler_stalled" }
            }
            DependencyBatchDecision.Completed(
                DependencyBatchReport(
                    outcomes = outcomes.map(::checkNotNull),
                    maximumActiveTasks = maximumActiveTasks,
                ),
            )
        } catch (interrupted: InterruptedException) {
            Thread.currentThread().interrupt()
            DependencyBatchDecision.Blocked("dependency_batch_interrupted")
        } finally {
            running.forEach { index -> writeScopeLeases.release(scopeOwnerId(index)) }
            executor.shutdownNow()
            runCatching { executor.awaitTermination(5L, TimeUnit.SECONDS) }
        }
    }

    private fun <K : Any, T : Any> execute(
        task: DependencyBatchTask<K, T>,
        isSuccessful: (T) -> Boolean,
    ): DependencyBatchTaskOutcome.Executed<K, T> = runCatching {
        task.execute()
    }.fold(
        onSuccess = { value ->
            runCatching { isSuccessful(value) }.fold(
                onSuccess = { successful ->
                    DependencyBatchTaskOutcome.Executed(
                        key = task.key,
                        value = value,
                        successful = successful,
                        failureReason = if (successful) null else "task_reported_failure",
                    )
                },
                onFailure = { error -> failed(task.key, error) },
            )
        },
        onFailure = { error -> failed(task.key, error) },
    )

    private fun <K : Any, T : Any> failed(
        key: K,
        error: Throwable,
    ): DependencyBatchTaskOutcome.Executed<K, T> = DependencyBatchTaskOutcome.Executed(
        key = key,
        value = null,
        successful = false,
        failureReason = error.message ?: error.javaClass.simpleName,
    )

    private fun DependencyBatchTaskOutcome<*, *>.isSuccessful(): Boolean =
        this is DependencyBatchTaskOutcome.Executed<*, *> && successful

    private fun <K : Any, T : Any> validate(
        tasks: List<DependencyBatchTask<K, T>>,
        maximumConcurrency: Int,
    ): String? {
        if (maximumConcurrency <= 0) return "dependency_batch_concurrency_invalid"
        val keys = tasks.map(DependencyBatchTask<K, T>::key)
        if (keys.distinct().size != keys.size) return "dependency_batch_duplicate_key"
        val keySet = keys.toSet()
        if (tasks.any { task -> task.dependencies.any { dependency -> dependency !in keySet } }) {
            return "dependency_batch_missing_dependency"
        }
        if (tasks.any { task -> task.key in task.dependencies }) return "dependency_batch_cycle"

        val remainingDependencies = tasks.associate { task ->
            task.key to task.dependencies.toMutableSet()
        }.toMutableMap()
        val ready = ArrayDeque(tasks.filter { it.dependencies.isEmpty() }.map { it.key })
        var visited = 0
        while (ready.isNotEmpty()) {
            val completed = ready.removeFirst()
            visited += 1
            tasks.forEach { task ->
                val remaining = checkNotNull(remainingDependencies[task.key])
                if (remaining.remove(completed) && remaining.isEmpty()) ready.addLast(task.key)
            }
        }
        return if (visited == tasks.size) null else "dependency_batch_cycle"
    }

    private data class IndexedOutcome<K : Any, T : Any>(
        val index: Int,
        val outcome: DependencyBatchTaskOutcome<K, T>,
    )

    private fun scopeOwnerId(index: Int): String = "dependency-task-$index"
}
