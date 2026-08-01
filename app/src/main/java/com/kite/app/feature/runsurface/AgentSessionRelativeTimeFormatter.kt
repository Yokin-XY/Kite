package com.kite.app.feature.runsurface

import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime

internal object AgentSessionRelativeTimeFormatter {
    fun format(value: String?, now: Instant = Instant.now()): String {
        val updatedAt = parse(value) ?: return "时间未知"
        val elapsedMinutes = Duration.between(updatedAt, now)
            .seconds
            .coerceAtLeast(0L) / 60L
        return when {
            elapsedMinutes < 1L -> "刚刚"
            elapsedMinutes < 60L -> "$elapsedMinutes 分钟前"
            elapsedMinutes < MINUTES_PER_DAY -> {
                val hours = elapsedMinutes / MINUTES_PER_HOUR
                val minutes = elapsedMinutes % MINUTES_PER_HOUR
                if (minutes == 0L) "$hours 小时前" else "$hours 小时 $minutes 分钟前"
            }
            else -> "${elapsedMinutes / MINUTES_PER_DAY} 天前"
        }
    }

    private fun parse(value: String?): Instant? {
        val normalized = value?.trim()?.takeIf(String::isNotBlank) ?: return null
        return runCatching { OffsetDateTime.parse(normalized).toInstant() }
            .recoverCatching { Instant.parse(normalized) }
            .getOrNull()
    }

    private const val MINUTES_PER_HOUR = 60L
    private const val MINUTES_PER_DAY = 24L * MINUTES_PER_HOUR
}
