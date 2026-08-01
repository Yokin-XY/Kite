package com.kite.app.feature.runsurface

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class AgentSessionRelativeTimeFormatterTest {
    private val now = Instant.parse("2026-08-01T12:00:00Z")

    @Test
    fun `一分钟内显示刚刚`() {
        assertEquals("刚刚", format("2026-08-01T11:59:01Z"))
        assertEquals("刚刚", format("2026-08-01T12:05:00Z"))
    }

    @Test
    fun `一小时内按分钟显示`() {
        assertEquals("1 分钟前", format("2026-08-01T11:59:00Z"))
        assertEquals("59 分钟前", format("2026-08-01T11:00:01Z"))
    }

    @Test
    fun `一天内按小时和分钟显示`() {
        assertEquals("1 小时前", format("2026-08-01T11:00:00Z"))
        assertEquals("3 小时 25 分钟前", format("2026-08-01T08:35:00Z"))
        assertEquals("23 小时 59 分钟前", format("2026-07-31T12:01:00Z"))
    }

    @Test
    fun `一天起按天显示`() {
        assertEquals("1 天前", format("2026-07-31T12:00:00Z"))
        assertEquals("8 天前", format("2026-07-24T11:59:59Z"))
    }

    @Test
    fun `支持偏移时区且非法时间不泄露原始值`() {
        assertEquals("1 小时前", format("2026-08-01T19:00:00+08:00"))
        assertEquals("时间未知", format("not-a-time"))
        assertEquals("时间未知", format(null))
    }

    private fun format(value: String?): String = AgentSessionRelativeTimeFormatter.format(value, now)
}
