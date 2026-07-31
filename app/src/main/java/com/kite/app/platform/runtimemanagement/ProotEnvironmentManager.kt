package com.kite.app.platform.runtimemanagement

import com.kite.app.foundation.contracts.ContainerRecord
import com.kite.app.foundation.logging.Logger
import com.kite.app.foundation.runtime.ProotEnvironmentWorkspace
import com.kite.app.foundation.runtime.ProotViewBinding
import com.kite.app.foundation.runtime.ProotViewLeaseMode
import com.kite.app.foundation.runtime.ProotViewStore
import com.kite.app.platform.runtime.ProotViewProcessGuard
import java.util.UUID

internal data class ProotEnvironmentSwitchMetrics(
    val sourceEnvironmentId: String,
    val targetEnvironmentId: String,
    val preparationMs: Long,
    val quiesceMs: Long,
    val pointerSwitchMs: Long,
    val totalMs: Long,
    val changed: Boolean,
    val success: Boolean,
    val error: String,
)

/**
 * 单活跃 PRoot 环境的控制面编排。
 *
 * Store 持有环境根、头与活跃指针；本类只编排跨状态动作：创建时准备私有工作区，切换时先封住旧头的
 * 新启动并收口旧进程，再原子切换活跃指针。这里不认识设置页、资源卡片或 AI 产品名称。
 */
internal class ProotEnvironmentManager(
    private val containerProvider: () -> ContainerRecord,
    private val storeProvider: (ContainerRecord) -> ProotViewStore = { ProotViewStore.forContainer(it) },
    private val quiesceView: (String) -> Result<Unit> = ProotViewProcessGuard()::quiesce,
    private val ownerIdFactory: (String) -> String = { operation ->
        "environment-$operation-${UUID.randomUUID()}"
    },
    private val monotonicNanos: () -> Long = System::nanoTime,
    private val onSwitchMeasured: (ProotEnvironmentSwitchMetrics) -> Unit = { metrics ->
        Logger.i(
            "ProotEnvironmentManager",
            "environment-switch source=${metrics.sourceEnvironmentId} target=${metrics.targetEnvironmentId} " +
                "changed=${metrics.changed} success=${metrics.success} prepareMs=${metrics.preparationMs} " +
                "quiesceMs=${metrics.quiesceMs} pointerMs=${metrics.pointerSwitchMs} totalMs=${metrics.totalMs} " +
                "error=${metrics.error.ifBlank { "none" }}",
        )
    },
) {
    @Synchronized
    fun createEnvironment(environmentId: String): Result<ProotViewBinding> = runCatching {
        val container = containerProvider()
        val store = storeProvider(container)
        store.ensureInitialized()
        require(environmentId !in store.environmentCurrents()) {
            "PRoot View 环境已存在：$environmentId"
        }
        val ownerId = ownerIdFactory("create")
        val prepared = store.prepare("environment-create:$environmentId", environmentId = environmentId)
        runCatching {
            store.verify(prepared.viewId)
            store.acquireLease(prepared.viewId, ownerId, ProotViewLeaseMode.WRITER)
            val binding = store.binding(prepared.viewId)
            ProotEnvironmentWorkspace.plan(container, binding).ensureReady()
            store.commit(prepared.viewId, ownerId, environmentId = environmentId)
            store.releaseLease(prepared.viewId, ownerId)
            store.currentBinding(environmentId)
        }.onFailure {
            runCatching { store.releaseLease(prepared.viewId, ownerId) }
            runCatching { store.discard(prepared.viewId) }
        }.getOrThrow()
    }

    @Synchronized
    fun switchEnvironment(environmentId: String): Result<ProotViewBinding> {
        val totalStartedAt = monotonicNanos()
        var sourceEnvironmentId = ""
        var targetEnvironmentId = environmentId
        var preparationMs = 0L
        var quiesceMs = 0L
        var pointerSwitchMs = 0L
        var changed = false
        val result = runCatching {
            val preparationStartedAt = monotonicNanos()
            val container = containerProvider()
            val store = storeProvider(container)
            store.ensureInitialized()
            val current = store.activeBinding()
            val target = store.currentBinding(environmentId)
            sourceEnvironmentId = current.environmentId
            targetEnvironmentId = target.environmentId
            preparationMs = elapsedMs(preparationStartedAt)
            if (current.environmentId == target.environmentId) return@runCatching current
            changed = true

            val ownerId = ownerIdFactory("switch")
            // writer lease 先封住旧头的新启动；已有进程不依赖 lease，由 process guard 精确按 viewId 收口。
            store.acquireLease(current.viewId, ownerId, ProotViewLeaseMode.WRITER)
            val switched = runCatching {
                val quiesceStartedAt = monotonicNanos()
                try {
                    quiesceView(current.viewId).getOrThrow()
                } finally {
                    quiesceMs = elapsedMs(quiesceStartedAt)
                }
                val pointerStartedAt = monotonicNanos()
                try {
                    store.switchActiveEnvironment(environmentId)
                } finally {
                    pointerSwitchMs = elapsedMs(pointerStartedAt)
                }
            }.onFailure {
                runCatching { store.releaseLease(current.viewId, ownerId) }
            }.getOrThrow()
            // 活跃指针已切换就是成功真相；清理旧头 lease 失败不能倒置切换结果，恢复流程会清理旧进程 session。
            runCatching { store.releaseLease(current.viewId, ownerId) }
            switched
        }
        val error = result.exceptionOrNull()?.let { it.message ?: it.javaClass.simpleName }.orEmpty()
        onSwitchMeasured(
            ProotEnvironmentSwitchMetrics(
                sourceEnvironmentId = sourceEnvironmentId,
                targetEnvironmentId = targetEnvironmentId,
                preparationMs = preparationMs,
                quiesceMs = quiesceMs,
                pointerSwitchMs = pointerSwitchMs,
                totalMs = elapsedMs(totalStartedAt),
                changed = changed,
                success = result.isSuccess,
                error = error,
            )
        )
        return result
    }

    private fun elapsedMs(startedAtNanos: Long): Long =
        ((monotonicNanos() - startedAtNanos).coerceAtLeast(0L) / 1_000_000L)
}
