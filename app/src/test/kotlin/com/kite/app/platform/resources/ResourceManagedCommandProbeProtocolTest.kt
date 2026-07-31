package com.kite.app.platform.resources

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ResourceManagedCommandProbeProtocolTest {
    @Test
    fun `一次探针覆盖全部已登记资源并要求每个受管命令存在`() {
        val command = ResourceManagedCommandProbeProtocol.command(
            listOf(
                ResourceManagedCommandRequirement("kite.nodejs", listOf("node", "npm", "npx")),
                ResourceManagedCommandRequirement("kite.openclaw", listOf("openclaw"))
            )
        )

        assertTrue(command.contains("PATH=/workspace/.kf/bin"))
        assertTrue(command.contains("command -v 'node'"))
        assertTrue(command.contains("command -v 'npm'"))
        assertTrue(command.contains("command -v 'npx'"))
        assertTrue(command.contains("command -v 'openclaw'"))
        assertEquals(1, "KITE_RESOURCE_COMMAND_PROBE_BEGIN".toRegex().findAll(command).count())
        assertEquals(1, "KITE_RESOURCE_COMMAND_PROBE_END".toRegex().findAll(command).count())
    }

    @Test
    fun `完整探针输出只返回缺少命令的资源`() {
        val result = ResourceManagedCommandProbeProtocol.parse(
            """
            KITE_RESOURCE_COMMAND_PROBE_BEGIN
            KITE_RESOURCE_COMMAND_MISSING	kite.nodejs
            KITE_RESOURCE_COMMAND_MISSING	kite.openclaw
            KITE_RESOURCE_COMMAND_PROBE_END
            """.trimIndent()
        )

        assertEquals(setOf("kite.nodejs", "kite.openclaw"), result.getOrThrow())
    }

    @Test
    fun `输出不完整时不能撤销任何安装事实`() {
        val result = ResourceManagedCommandProbeProtocol.parse(
            "KITE_RESOURCE_COMMAND_MISSING\tkite.nodejs"
        )

        assertTrue(result.isFailure)
    }
}
