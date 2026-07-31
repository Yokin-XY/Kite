package com.kite.app.foundation.runtime

import com.kite.app.foundation.contracts.ContainerExecConfig
import com.kite.app.foundation.contracts.ContainerLaunchConfig
import com.kite.app.foundation.contracts.ContainerRecord
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Base64

@RunWith(RobolectricTestRunner::class)
class HostNodeChildProcessContractTest {
    @Test
    fun `removes only the exact marker and attaches encoded contract`() {
        val container = container()
        val contract = HostNodeChildProcessContract.from(
            config = ContainerExecConfig(
                container = container,
                workingDirectory = "/workspace",
                command = listOf("/data/proot", "-r", "/data/rootfs", "-w", "/workspace", "marker"),
                env = mapOf("HOME" to "/root", "PATH" to "/usr/bin"),
            ),
            marker = "marker",
        )
        val attached = contract.attachTo(
            ContainerLaunchConfig(
                container = container,
                executablePath = "/data/kite-node-host",
                workingDirectory = "/data/workspace",
                args = arrayOf("/data/kite-node-host", "app.mjs"),
                env = arrayOf("HOME=/data/rootfs/root"),
            )
        )
        val env = attached.env.associate { it.substringBefore('=') to it.substringAfter('=') }
        val argv = JSONArray(decode(env.getValue(HostNodeChildProcessContract.ENV_PROOT_ARGV)))
        val prootEnvironment = JSONObject(decode(env.getValue(HostNodeChildProcessContract.ENV_PROOT_ENV)))

        assertEquals(5, argv.length())
        assertEquals("/workspace", argv.getString(4))
        assertFalse((0 until argv.length()).any { argv.getString(it) == "marker" })
        assertEquals("/root", prootEnvironment.getString("HOME"))
        assertEquals("/data/rootfs/root", env["HOME"])
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects a prefix whose final marker does not match`() {
        HostNodeChildProcessContract.from(
            ContainerExecConfig(container(), "/workspace", listOf("proot", "other"), emptyMap()),
            marker = "marker",
        )
    }

    @Test
    fun `contract contains no application identity`() {
        val source = java.io.File("scripts/kite-node-host-runtime.cjs").takeIf { it.isFile }
            ?: java.io.File("../scripts/kite-node-host-runtime.cjs")
        val text = source.readText()

        assertTrue(text.contains("childProcess.spawn"))
        assertFalse(text.contains("OpenClaw", ignoreCase = true))
        assertFalse(text.contains("kite.openclaw", ignoreCase = true))
    }

    private fun decode(value: String): String = String(Base64.getDecoder().decode(value))

    private fun container(): ContainerRecord = ContainerRecord(
        id = "ubuntu-main",
        displayName = "Ubuntu",
        imageName = "ubuntu-base-24.04-arm64",
        rootfsPath = "/data/rootfs",
        workspacePath = "/data/workspace",
        createdAt = 10L,
    )
}
