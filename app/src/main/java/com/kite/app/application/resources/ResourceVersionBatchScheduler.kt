package com.kite.app.application.resources

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

internal enum class ResourceVersionBatchLane {
    STRUCTURED_NATIVE_REMOTE,
    PROOT_COMPATIBILITY,
}

internal data class ResourceVersionBatchSummary(
    val total: Int,
    val structuredNativeRemote: Int,
    val prootCompatibility: Int,
    val maxStructuredNativeRemote: Int,
    val maxProotCompatibility: Int,
)

/** 只消费调用方预先给出的结构化车道，不读取资源身份、命令文本或页面状态。 */
internal object ResourceVersionBatchScheduler {
    const val STRUCTURED_NATIVE_REMOTE_LIMIT = 3
    const val PROOT_COMPATIBILITY_LIMIT = 1

    suspend fun <T, R> executeOrdered(
        requests: List<T>,
        laneOf: (T) -> ResourceVersionBatchLane,
        observer: (ResourceVersionBatchSummary) -> Unit = {},
        execute: suspend (T) -> R,
    ): List<R> {
        // 在任何任务开始前完成全部分类；分类失败时不能留下部分已执行请求。
        val classified = requests.map { request -> ClassifiedRequest(request, laneOf(request)) }
        val structuredNativeRemote = Semaphore(STRUCTURED_NATIVE_REMOTE_LIMIT)
        val prootCompatibility = Semaphore(PROOT_COMPATIBILITY_LIMIT)
        val activity = BatchActivity()
        val results = coroutineScope {
            classified.map { classifiedRequest ->
                async {
                    when (classifiedRequest.lane) {
                        ResourceVersionBatchLane.STRUCTURED_NATIVE_REMOTE ->
                            structuredNativeRemote.withPermit {
                                activity.execute(classifiedRequest.lane) { execute(classifiedRequest.request) }
                            }
                        ResourceVersionBatchLane.PROOT_COMPATIBILITY ->
                            prootCompatibility.withPermit {
                                activity.execute(classifiedRequest.lane) { execute(classifiedRequest.request) }
                            }
                    }
                }
            }.awaitAll()
        }
        val summary = activity.summary(classified.map(ClassifiedRequest<T>::lane))
        runCatching { observer(summary) }
        return results
    }

    private data class ClassifiedRequest<T>(
        val request: T,
        val lane: ResourceVersionBatchLane,
    )

    private class BatchActivity {
        private val lock = Any()
        private var activeStructuredNativeRemote = 0
        private var activeProotCompatibility = 0
        private var maxStructuredNativeRemote = 0
        private var maxProotCompatibility = 0

        suspend fun <T> execute(lane: ResourceVersionBatchLane, block: suspend () -> T): T {
            enter(lane)
            return try {
                block()
            } finally {
                exit(lane)
            }
        }

        fun summary(lanes: List<ResourceVersionBatchLane>): ResourceVersionBatchSummary = synchronized(lock) {
            ResourceVersionBatchSummary(
                total = lanes.size,
                structuredNativeRemote = lanes.count { it == ResourceVersionBatchLane.STRUCTURED_NATIVE_REMOTE },
                prootCompatibility = lanes.count { it == ResourceVersionBatchLane.PROOT_COMPATIBILITY },
                maxStructuredNativeRemote = maxStructuredNativeRemote,
                maxProotCompatibility = maxProotCompatibility,
            )
        }

        private fun enter(lane: ResourceVersionBatchLane) = synchronized(lock) {
            when (lane) {
                ResourceVersionBatchLane.STRUCTURED_NATIVE_REMOTE -> {
                    activeStructuredNativeRemote += 1
                    maxStructuredNativeRemote = maxOf(maxStructuredNativeRemote, activeStructuredNativeRemote)
                }
                ResourceVersionBatchLane.PROOT_COMPATIBILITY -> {
                    activeProotCompatibility += 1
                    maxProotCompatibility = maxOf(maxProotCompatibility, activeProotCompatibility)
                }
            }
        }

        private fun exit(lane: ResourceVersionBatchLane) = synchronized(lock) {
            when (lane) {
                ResourceVersionBatchLane.STRUCTURED_NATIVE_REMOTE -> activeStructuredNativeRemote -= 1
                ResourceVersionBatchLane.PROOT_COMPATIBILITY -> activeProotCompatibility -= 1
            }
        }
    }
}
