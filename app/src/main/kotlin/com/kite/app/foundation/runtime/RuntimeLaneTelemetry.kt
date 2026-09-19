package com.kite.app.foundation.runtime

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 运行通道遥测：宿主快速通道是默认车道，PRoot/兼容兜底是缺陷态（快速通道整改方案 C）。
 * 采集各入口的 lane 决策快照，供诊断面板消费；同时落 logcat（tag KiteRuntimeLane），
 * 开发期一条 `adb logcat -s KiteRuntimeLane` 即可看到全部兜底事件与原因。
 *
 * 只做进程内环形缓冲（最近 [CAPACITY] 条），不持久化、不进任何渲染关键路径。
 */
object RuntimeLaneTelemetry {
    private const val TAG = "KiteRuntimeLane"
    private const val CAPACITY = 100

    /** 已知的非兜底车道前缀：host_node / host_python / android_native。 */
    private val FAST_LANES = setOf("host_node", "host_python", "android_native")

    data class LaneEntry(
        val timestampMs: Long,
        val entryPoint: String,
        val lane: String,
        val fallbackReason: String,
        val detail: String? = null,
    ) {
        val isFastLane: Boolean get() = lane in FAST_LANES
    }

    private val entriesImpl = MutableStateFlow<List<LaneEntry>>(emptyList())

    /** 最近决策（新→旧）。 */
    val entries: StateFlow<List<LaneEntry>> = entriesImpl.asStateFlow()

    fun record(
        entryPoint: String,
        lane: String,
        fallbackReason: String = "none",
        detail: String? = null,
    ) {
        val entry = LaneEntry(
            timestampMs = System.currentTimeMillis(),
            entryPoint = entryPoint,
            lane = lane,
            fallbackReason = fallbackReason,
            detail = detail,
        )
        entriesImpl.value = (listOf(entry) + entriesImpl.value).take(CAPACITY)
        logEntry(entry)
    }

    private fun logEntry(entry: LaneEntry) {
        // 兜底即缺陷：警告级日志便于过滤定位（按用户原则，走兜底要解决而不是容忍）。
        // 纯 JVM 单测环境没有 android.util.Log，静默跳过。
        if (entry.isFastLane) {
            runCatching {
                Log.i(TAG, "fast " + entry.entryPoint + " -> " + entry.lane + entry.detail?.let { " [$it]" }.orEmpty())
            }
        } else {
            runCatching {
                Log.w(TAG, "fallback " + entry.entryPoint + " -> " + entry.lane + " reason=" + entry.fallbackReason + entry.detail?.let { " [$it]" }.orEmpty())
            }
        }
    }

    /** 快车道命中率（0..1）；无样本时 null。 */
    val fastLaneRatio: Double?
        get() = entriesImpl.value.takeIf(List<LaneEntry>::isNotEmpty)?.let { list ->
            list.count(LaneEntry::isFastLane).toDouble() / list.size
        }
}
