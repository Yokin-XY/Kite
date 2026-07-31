package com.kite.app.foundation.runtime

import java.util.concurrent.atomic.AtomicLong

/**
 * 普通 PRoot 启动中可复用的静态准备身份。
 *
 * 动态网络内容、显式 View 绑定、运行状态和命令结果不属于这个身份，也不得由本缓存持有。
 */
internal data class RuntimeLaunchPreparationIdentity(
    val runtimeRootPath: String,
    val runtimeDescriptorStamp: Long,
    val containerId: String,
    val containerCreatedAtMs: Long,
    val rootfsPath: String,
    val workspacePath: String,
    val networkMode: String,
)

/**
 * 普通 PRoot PATH 中受管命令的轻量宿主文件身份。
 *
 * 它只描述已经存在的命令文件；缺失命令不会生成伪身份，也不会被当成可缓存的负向结论。
 */
internal data class ManagedCommandHostFileStamp(
    val command: String,
    val hostPath: String,
    val canonicalPath: String,
    val linkChain: List<String>,
    val lastModifiedMs: Long,
    val length: Long,
)

internal data class ManagedCommandVerificationBasis(
    val runtimeIdentity: RuntimeLaunchPreparationIdentity,
    val commandFiles: List<ManagedCommandHostFileStamp>,
)

/**
 * 只有原生默认环境可复用启动准备；显式 View/环境必须逐次解析真实绑定。
 */
internal object RuntimeLaunchPreparationPolicy {
    fun isCacheEligible(
        requestedProotViewId: String?,
        requestedProotEnvironmentId: String?,
    ): Boolean = requestedProotViewId.isNullOrBlank() && requestedProotEnvironmentId.isNullOrBlank()
}

internal data class RuntimeLaunchPreparationCacheSnapshot(
    val generation: Long = 0L,
    val hitCount: Long = 0L,
    val rebuildCount: Long = 0L,
    val invalidationCount: Long = 0L,
    val hasEntry: Boolean = false,
    val lastBuildReason: String = "none",
    val lastInvalidationReason: String = "none",
)

/**
 * 每进程启动准备缓存。命中读取无锁；同一身份的首次构建在锁内 single-flight。
 *
 * 调用方负责提供完整身份，并在本进程内发生无法由身份表达的配置变化时显式失效。
 */
internal class RuntimeLaunchPreparationCache<T : Any> {
    private data class Entry<T : Any>(
        val identity: RuntimeLaunchPreparationIdentity,
        val value: T,
        val generation: Long,
    )

    private val lock = Any()
    private val hitCount = AtomicLong(0L)

    @Volatile
    private var entry: Entry<T>? = null

    @Volatile
    private var generation: Long = 0L

    @Volatile
    private var rebuildCount: Long = 0L

    @Volatile
    private var invalidationCount: Long = 0L

    @Volatile
    private var lastBuildReason: String = "none"

    @Volatile
    private var lastInvalidationReason: String = "none"

    fun getOrBuild(
        identity: RuntimeLaunchPreparationIdentity,
        buildReason: String,
        builder: () -> T,
    ): T {
        entry?.takeIf { it.identity == identity }?.let { cached ->
            hitCount.incrementAndGet()
            return cached.value
        }

        return synchronized(lock) {
            entry?.takeIf { it.identity == identity }?.let { cached ->
                hitCount.incrementAndGet()
                return@synchronized cached.value
            }

            val value = builder()
            generation += 1L
            rebuildCount += 1L
            lastBuildReason = buildReason.ifBlank { "unspecified" }
            entry = Entry(identity, value, generation)
            value
        }
    }

    fun invalidate(reason: String) {
        synchronized(lock) {
            entry = null
            generation += 1L
            invalidationCount += 1L
            lastInvalidationReason = reason.ifBlank { "unspecified" }
        }
    }

    fun snapshot(): RuntimeLaunchPreparationCacheSnapshot = synchronized(lock) {
        RuntimeLaunchPreparationCacheSnapshot(
            generation = generation,
            hitCount = hitCount.get(),
            rebuildCount = rebuildCount,
            invalidationCount = invalidationCount,
            hasEntry = entry != null,
            lastBuildReason = lastBuildReason,
            lastInvalidationReason = lastInvalidationReason,
        )
    }
}
