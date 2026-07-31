package com.kite.app.foundation.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class HostNodeRuntimeTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `resolves generic glibc and node layout without resource identity`() {
        val fixture = fixture()

        val result = HostNodeRuntimeResolver.resolve(fixture.rootfs, fixture.workspace, fixture.assets)

        assertTrue(result is HostNodeRuntimeResolution.Ready)
        val layout = (result as HostNodeRuntimeResolution.Ready).layout
        assertEquals(fixture.node.absolutePath, layout.nodeBinary.absolutePath)
        assertTrue(layout.libraryPath.startsWith(fixture.assets.patchedLibc.parentFile.absolutePath))
    }

    @Test
    fun `missing patched loader fails closed before a host process exists`() {
        val fixture = fixture()
        fixture.assets.patchedLoader.delete()

        assertEquals(
            HostNodeRuntimeResolution.Fallback("patched_loader_missing"),
            HostNodeRuntimeResolver.resolve(fixture.rootfs, fixture.workspace, fixture.assets),
        )
    }

    @Test
    fun `wrong node ABI fails closed before a host process exists`() {
        val fixture = fixture()
        fixture.node.writeText("not an arm64 ELF")

        assertEquals(
            HostNodeRuntimeResolution.Fallback("node_binary_abi_mismatch"),
            HostNodeRuntimeResolver.resolve(fixture.rootfs, fixture.workspace, fixture.assets),
        )
    }

    @Test
    fun `managed node shebang resolves through absolute and relative links`() {
        val fixture = fixture()
        val installBin = File(fixture.workspace, ".kf/software/example/npm-global/bin").apply { mkdirs() }
        val entry = File(fixture.workspace, ".kf/software/example/npm-global/lib/node_modules/example/cli.mjs")
        entry.parentFile.mkdirs()
        entry.writeText("#!/usr/bin/env node\nconsole.log('ok')\n")
        val installedCommand = File(installBin, "example").apply { writeText("link fixture") }
        val exposedCommand = File(fixture.workspace, ".kf/bin/example").apply { writeText("link fixture") }
        val links = mapOf(
            installedCommand.absoluteFile.normalize() to "../lib/node_modules/example/cli.mjs",
            exposedCommand.absoluteFile.normalize() to "/workspace/.kf/software/example/npm-global/bin/example",
        )
        val layout = (HostNodeRuntimeResolver.resolve(
            fixture.rootfs,
            fixture.workspace,
            fixture.assets,
        ) as HostNodeRuntimeResolution.Ready).layout

        val result = HostNodeCommandResolver.resolve("example chat --plain", layout) { links[it] }

        assertEquals(
            HostNodeCommandResolution.Ready(HostNodeInvocation(entry, listOf("chat", "--plain"))),
            result,
        )
    }

    @Test
    fun `node binary invocation is generic`() {
        val fixture = fixture()
        val layout = (HostNodeRuntimeResolver.resolve(
            fixture.rootfs,
            fixture.workspace,
            fixture.assets,
        ) as HostNodeRuntimeResolution.Ready).layout

        assertEquals(
            HostNodeCommandResolution.Ready(HostNodeInvocation(null, listOf("--version"))),
            HostNodeCommandResolver.resolve("node --version", layout),
        )
    }

    @Test
    fun `structured argv resolves an arbitrary managed node shebang without reparsing shell text`() {
        val fixture = fixture()
        val entry = File(fixture.workspace, ".kf/bin/arbitrary-tool").apply {
            writeText("#!/usr/bin/env node\nconsole.log('ok')\n")
            setExecutable(true)
        }
        val layout = (HostNodeRuntimeResolver.resolve(
            fixture.rootfs,
            fixture.workspace,
            fixture.assets,
        ) as HostNodeRuntimeResolution.Ready).layout

        assertEquals(
            HostNodeCommandResolution.Ready(
                HostNodeInvocation(entry, listOf("value with spaces", "--plain"))
            ),
            HostNodeCommandResolver.resolve(
                executable = "arbitrary-tool",
                arguments = listOf("value with spaces", "--plain"),
                layout = layout,
            ),
        )
    }

    @Test
    fun `structured argv preserves non-node fallback reason`() {
        val fixture = fixture()
        File(fixture.workspace, ".kf/bin/arbitrary-tool").apply {
            writeText("#!/usr/bin/env sh\necho ok\n")
            setExecutable(true)
        }
        val layout = (HostNodeRuntimeResolver.resolve(
            fixture.rootfs,
            fixture.workspace,
            fixture.assets,
        ) as HostNodeRuntimeResolution.Ready).layout

        assertEquals(
            HostNodeCommandResolution.Fallback("managed_command_not_node"),
            HostNodeCommandResolver.resolve(
                executable = "arbitrary-tool",
                arguments = listOf("value with spaces"),
                layout = layout,
            ),
        )
    }

    @Test
    fun `absolute container paths map to physical runtime roots`() {
        val fixture = fixture()
        val layout = (HostNodeRuntimeResolver.resolve(
            fixture.rootfs,
            fixture.workspace,
            fixture.assets,
        ) as HostNodeRuntimeResolution.Ready).layout

        assertEquals(
            File(fixture.workspace, ".kf/bin/example").absoluteFile.normalize(),
            layout.mapContainerPath("/workspace/.kf/bin/example"),
        )
        assertEquals(
            File(fixture.rootfs, "usr/bin/env").absoluteFile.normalize(),
            layout.mapContainerPath("/usr/bin/env"),
        )
        assertEquals(null, layout.mapContainerPath("/workspace/../../outside"))
        assertEquals(null, layout.mapContainerPath("/../../outside"))
    }

    @Test
    fun `shared control root stays distinct from environment workspace`() {
        val fixture = fixture()
        val environmentWorkspace = File(fixture.workspace.parentFile, "profile/workspace").apply { mkdirs() }
        val layout = (HostNodeRuntimeResolver.resolve(
            rootfsDirectory = fixture.rootfs,
            workspaceDirectory = environmentWorkspace,
            workspaceControlDirectory = File(fixture.workspace, ".kf"),
            assets = fixture.assets,
        ) as HostNodeRuntimeResolution.Ready).layout

        assertEquals(
            File(fixture.workspace, ".kf/bin/example").absoluteFile.normalize(),
            layout.mapContainerPath("/workspace/.kf/bin/example"),
        )
        assertEquals(
            File(environmentWorkspace, "project").absoluteFile.normalize(),
            layout.mapContainerPath("/workspace/project"),
        )
    }

    @Test
    fun `compound shell syntax and non-node scripts stay in proot`() {
        val fixture = fixture()
        val command = File(fixture.workspace, ".kf/bin/tool")
        command.writeText("#!/usr/bin/env sh\necho ok\n")
        command.setExecutable(true)
        val layout = (HostNodeRuntimeResolver.resolve(
            fixture.rootfs,
            fixture.workspace,
            fixture.assets,
        ) as HostNodeRuntimeResolution.Ready).layout

        assertEquals(
            HostNodeCommandResolution.Fallback("shell_syntax_required"),
            HostNodeCommandResolver.resolve("node app.mjs | tee out", layout),
        )
        assertEquals(
            HostNodeCommandResolution.Fallback("managed_command_not_node"),
            HostNodeCommandResolver.resolve("tool", layout),
        )
    }

    private fun fixture(): Fixture {
        val root = temporaryFolder.newFolder()
        val rootfs = File(root, "rootfs").apply { mkdirs() }
        val workspace = File(root, "workspace").apply { mkdirs() }
        val loader = executable(File(rootfs, "usr/lib/aarch64-linux-gnu/ld-linux-aarch64.so.1"))
        File(rootfs, "lib/aarch64-linux-gnu").mkdirs()
        val node = executable(File(workspace, ".kf/software/kite.nodejs/node-v26.4.0/bin/node"))
        val nodeLib = File(node.parentFile.parentFile, "lib").apply { mkdirs() }
        File(nodeLib, "libatomic.so.1").writeText("atomic")
        File(workspace, ".kf/bin").mkdirs()
        val assetRoot = File(workspace, ".kf/system/node-runtime/host").apply { mkdirs() }
        val launcher = executable(File(assetRoot, "kite-node-host"))
        val preload = File(assetRoot, "kite-node-host-runtime.cjs").apply { writeText("// fixture") }
        val patchedLoader = executable(File(assetRoot, "glibc/ld-linux-aarch64.so.1"))
        val libc = elf(File(assetRoot, "glibc/libc.so.6"))
        val compat = elf(File(assetRoot, "glibc/libkite-node-glibc-compat.so"))
        val resolv = File(assetRoot, "resolv.conf").apply { writeText("nameserver 127.0.0.1\n") }
        return Fixture(
            rootfs,
            workspace,
            loader,
            node,
            HostNodeRuntimeAssets(launcher, preload, patchedLoader, libc, compat, resolv),
        )
    }

    private fun executable(file: File): File = file.apply {
        parentFile.mkdirs()
        writeBytes(arm64ElfHeader())
        setExecutable(true)
    }

    private fun elf(file: File): File = file.apply {
        parentFile.mkdirs()
        writeBytes(arm64ElfHeader())
    }

    private fun arm64ElfHeader(): ByteArray = ByteArray(20).apply {
        this[0] = 0x7f
        this[1] = 'E'.code.toByte()
        this[2] = 'L'.code.toByte()
        this[3] = 'F'.code.toByte()
        this[4] = 2
        this[5] = 1
        this[18] = 0xb7.toByte()
    }

    private data class Fixture(
        val rootfs: File,
        val workspace: File,
        val loader: File,
        val node: File,
        val assets: HostNodeRuntimeAssets,
    )
}
