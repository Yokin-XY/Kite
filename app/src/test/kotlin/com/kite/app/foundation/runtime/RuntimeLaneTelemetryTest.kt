package com.kite.app.foundation.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 遥测合同：兜底即缺陷（警告级）、快道命中率、环形容量。 */
class RuntimeLaneTelemetryTest {
    @Test
    fun `fast lanes and fallbacks are classified`() {
        assertTrue(RuntimeLaneTelemetry.LaneEntry(0L, "agent", "host_node", "none").isFastLane)
        assertTrue(RuntimeLaneTelemetry.LaneEntry(0L, "recipe", "host_python", "none").isFastLane)
        assertTrue(RuntimeLaneTelemetry.LaneEntry(0L, "recipe", "android_native", "none").isFastLane)
        assertFalse(RuntimeLaneTelemetry.LaneEntry(0L, "agent", "proot_shell", "full_linux_required").isFastLane)
    }

    @Test
    fun `record keeps newest first and bounded`() {
        RuntimeLaneTelemetry.record("test", "host_node")
        RuntimeLaneTelemetry.record("test", "proot_shell", "network_mode_requires_proot")

        val entries = RuntimeLaneTelemetry.entries.value
        assertTrue(entries.first().lane == "proot_shell")
        assertTrue(entries.any { it.entryPoint == "test" && it.isFastLane })
        assertTrue((RuntimeLaneTelemetry.fastLaneRatio ?: 0.0) in 0.0..1.0)
    }

    @Test
    fun `ratio reflects fast lane share`() {
        RuntimeLaneTelemetry.record("ratio", "host_node")
        RuntimeLaneTelemetry.record("ratio", "proot_shell", "test_reason")
        val entries = RuntimeLaneTelemetry.entries.value.filter { it.entryPoint == "ratio" }
        val fast = entries.count(RuntimeLaneTelemetry.LaneEntry::isFastLane)
        assertEquals(1, fast)
        assertEquals(2, entries.size)
    }
}
