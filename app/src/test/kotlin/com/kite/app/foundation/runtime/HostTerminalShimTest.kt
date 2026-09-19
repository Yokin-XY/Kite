package com.kite.app.foundation.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/** 终端 PATH shim 合同：命令覆盖、幂等安装、可执行、回退语义内嵌。 */
class HostTerminalShimTest {
    @Test
    fun `shim installs idempotently with executable scripts`() {
        val control = Files.createTempDirectory("kite-host-shim-test").toFile()
        val bin = HostTerminalShim.ensureInstalled(control)
        val node = File(bin, "node")
        val python = File(bin, "python3.12")
        assertTrue(node.isFile && node.canExecute())
        assertTrue(python.isFile && python.canExecute())
        assertEquals(
            HostTerminalShim.SHIM_SCRIPT,
            node.readText(),
        )

        // 幂等：再次安装内容不变（mtime 层面不强制，内容层面必须一致）。
        HostTerminalShim.ensureInstalled(control)
        assertEquals(HostTerminalShim.SHIM_SCRIPT, node.readText())
    }

    @Test
    fun `shim script carries forward contract and passthrough allowlist`() {
        val script = HostTerminalShim.SHIM_SCRIPT
        // 转发与回退双路径都必须在脚本里。
        assertTrue(script.contains("kite-host-exec"))
        assertTrue(script.contains("run_native"))
        assertTrue(script.contains("KITE_HOST_SHIM"))
        // 透传白名单与桥侧一致。
        val bridgeAllowlist = setOf(
            "TERM", "COLORTERM", "NO_COLOR", "CLICOLOR_FORCE", "FORCE_COLOR", "LANG", "TZ",
        )
        bridgeAllowlist.forEach { key ->
            assertTrue("passthrough $key", script.contains("\"$key\""))
        }
    }

    @Suppress("unused")
    private fun noop() = Unit
}
