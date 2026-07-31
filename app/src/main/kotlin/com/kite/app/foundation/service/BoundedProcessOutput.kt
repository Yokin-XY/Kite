package com.kite.app.foundation.service

internal data class BoundedProcessOutputSnapshot(
    val text: String,
    val truncated: Boolean,
)

/** 持续排空子进程输出，但只保留有界前缀，避免 one-shot 日志把 Android 进程内存撑满。 */
internal class BoundedProcessOutput(private val maxChars: Int) {
    private val buffer = StringBuilder(minOf(maxChars, 8 * 1024))
    private var truncated = false

    init {
        require(maxChars > 0) { "bounded_process_output_limit_invalid" }
    }

    @Synchronized
    fun append(chars: CharArray, count: Int) {
        require(count in 0..chars.size) { "bounded_process_output_count_invalid" }
        val remaining = maxChars - buffer.length
        if (remaining > 0) buffer.append(chars, 0, minOf(count, remaining))
        if (count > remaining) truncated = true
    }

    @Synchronized
    fun snapshot(): BoundedProcessOutputSnapshot = BoundedProcessOutputSnapshot(
        text = buffer.toString(),
        truncated = truncated,
    )
}
