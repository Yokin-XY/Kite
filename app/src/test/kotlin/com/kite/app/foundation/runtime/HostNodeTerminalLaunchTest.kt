package com.kite.app.foundation.runtime

import com.kite.app.foundation.contracts.ContainerRecord
import com.kite.app.foundation.contracts.NetworkMode
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class HostNodeTerminalLaunchTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `builds direct PTY config with physical runtime paths`() {
        val root = temporaryFolder.newFolder()
        val rootfs = File(root, "rootfs").apply { mkdirs() }
        val workspace = File(root, "workspace").apply { mkdirs() }
        val workdir = File(workspace, "project").apply { mkdirs() }
        val launcher = File(workspace, ".kf/system/node-runtime/host/kite-node-host").apply {
            parentFile.mkdirs()
            writeText("launcher")
        }
        val preload = File(launcher.parentFile, "kite-node-host-runtime.cjs").apply { writeText("preload") }
        val patchedLibc = File(launcher.parentFile, "glibc/libc.so.6").apply {
            parentFile.mkdirs()
            writeText("libc")
        }
        val patchedLoader = File(launcher.parentFile, "glibc/ld-linux-aarch64.so.1").apply {
            writeText("loader")
        }
        val compat = File(launcher.parentFile, "glibc/libkite-node-glibc-compat.so").apply {
            writeText("compat")
        }
        val resolv = File(launcher.parentFile, "resolv.conf").apply { writeText("nameserver 1::1\n") }
        val tracer = File(launcher.parentFile, "kite-syscall-tracer").apply {
            writeText("#!/system/bin/sh\n")
            setExecutable(true)
        }
        val node = File(workspace, ".kf/software/kite.nodejs/node-v26.4.0/bin/node")
        val nodeLib = File(node.parentFile.parentFile, "lib").apply { mkdirs() }
        val layout = HostNodeRuntimeLayout(
            rootfsDirectory = rootfs,
            workspaceDirectory = workspace,
            workspaceControlDirectory = File(workspace, ".kf"),
            loader = patchedLoader,
            nodeBinary = node,
            nodeLibraryDirectory = nodeLib,
            glibcLibraryDirectories = listOf(File(rootfs, "usr/lib/aarch64-linux-gnu")),
            assets = HostNodeRuntimeAssets(launcher, preload, patchedLoader, patchedLibc, compat, tracer, resolv),
        )
        val entry = File(workspace, ".kf/software/example/cli.mjs")

        val config = HostNodeTerminalLaunchFactory.buildConfig(
            container = container(rootfs, workspace),
            layout = layout,
            invocation = HostNodeInvocation(entry, listOf("chat", "--plain")),
            workingDirectory = workdir,
        )
        val environment = config.env.associate { value ->
            value.substringBefore('=') to value.substringAfter('=')
        }

        assertEquals(launcher.absolutePath, config.executablePath)
        assertEquals(workdir.absolutePath, config.workingDirectory)
        assertArrayEquals(
            arrayOf(launcher.absolutePath, entry.absolutePath, "chat", "--plain"),
            config.args,
        )
        assertEquals(patchedLoader.absolutePath, environment["KITE_NODE_HOST_LOADER"])
        assertEquals(node.absolutePath, environment["KITE_NODE_HOST_BINARY"])
        assertEquals(File(workspace, ".kf").absolutePath, environment["KITE_NODE_HOST_CONTROL"])
        assertEquals("glibc.pthread.rseq=0", environment["GLIBC_TUNABLES"])
        assertEquals("--require=${preload.absolutePath}", environment["NODE_OPTIONS"])
        assertEquals(compat.absolutePath, environment["KITE_NODE_HOST_COMPAT_LIBRARY"])
        assertFalse(environment.containsKey("NODE_COMPILE_CACHE"))
        assertFalse(config.args.any { it.contains("proot", ignoreCase = true) })
    }

    @Test
    fun `provider adds caller environment without allowing runtime contract replacement`() {
        val root = temporaryFolder.newFolder()
        val rootfs = File(root, "rootfs").apply { mkdirs() }
        val workspace = File(root, "workspace").apply { mkdirs() }
        val workdir = File(workspace, "project").apply { mkdirs() }
        val launcher = File(workspace, ".kf/system/node-runtime/host/kite-node-host").apply {
            parentFile.mkdirs()
            writeText("launcher")
        }
        val preload = File(launcher.parentFile, "kite-node-host-runtime.cjs").apply { writeText("preload") }
        val patchedLibc = File(launcher.parentFile, "glibc/libc.so.6").apply {
            parentFile.mkdirs()
            writeText("libc")
        }
        val patchedLoader = File(launcher.parentFile, "glibc/ld-linux-aarch64.so.1").apply { writeText("loader") }
        val compat = File(launcher.parentFile, "glibc/libkite-node-glibc-compat.so").apply { writeText("compat") }
        val resolv = File(launcher.parentFile, "resolv.conf").apply { writeText("nameserver 1::1\n") }
        val tracer = File(launcher.parentFile, "kite-syscall-tracer").apply {
            writeText("#!/system/bin/sh\n")
            setExecutable(true)
        }
        val node = File(workspace, ".kf/software/kite.nodejs/node-v26.4.0/bin/node")
        val layout = HostNodeRuntimeLayout(
            rootfsDirectory = rootfs,
            workspaceDirectory = workspace,
            workspaceControlDirectory = File(workspace, ".kf"),
            loader = patchedLoader,
            nodeBinary = node,
            nodeLibraryDirectory = File(node.parentFile.parentFile, "lib"),
            glibcLibraryDirectories = listOf(File(rootfs, "usr/lib/aarch64-linux-gnu")),
            assets = HostNodeRuntimeAssets(launcher, preload, patchedLoader, patchedLibc, compat, tracer, resolv),
        )

        val config = HostNodeRuntimeProvider.buildConfig(
            container = container(rootfs, workspace),
            layout = layout,
            invocation = HostNodeInvocation(null, listOf("--version")),
            workingDirectory = workdir,
            additionalEnvironment = mapOf(
                "OPENCLAW_DISABLE_BONJOUR" to "1",
                "NODE_OPTIONS" to "--unsafe-replacement",
                "INVALID-NAME" to "ignored",
            ),
        )
        val environment = config.env.associate { value ->
            value.substringBefore('=') to value.substringAfter('=')
        }

        assertEquals("1", environment["OPENCLAW_DISABLE_BONJOUR"])
        assertEquals("--require=${preload.absolutePath}", environment["NODE_OPTIONS"])
        assertFalse(environment.containsKey("INVALID-NAME"))
    }

    @Test
    fun `non-host network mode is rejected before asset preparation`() {
        val root = temporaryFolder.newFolder()
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()

        assertEquals(
            RuntimeProviderDecision.Unsupported(
                RuntimeProviderKind.MANAGED_RUNTIME,
                "network_mode_requires_proot",
            ),
            HostNodeRuntimeProvider.prepare(
                context = HostNodeProviderContext(
                    context,
                    container(File(root, "rootfs"), File(root, "workspace"), NetworkMode.NONE),
                    File(root, "workspace"),
                ),
                request = RuntimeExecutionRequest(
                    payload = RuntimeExecutionPayload.CommandLine("node --version"),
                    workingDirectory = "/workspace",
                ),
            ),
        )
    }

    @Test
    fun `syscall tracer wraps launch chain with locator environment`() {
        val root = temporaryFolder.newFolder()
        val rootfs = File(root, "rootfs").apply { mkdirs() }
        val workspace = File(root, "workspace").apply { mkdirs() }
        val workdir = File(workspace, "project").apply { mkdirs() }
        val launcher = File(workspace, ".kf/system/node-runtime/host/kite-node-host").apply {
            parentFile.mkdirs()
            writeText("launcher")
            setExecutable(true)
        }
        val preload = File(launcher.parentFile, "kite-node-host-runtime.cjs").apply { writeText("preload") }
        val patchedLibc = File(launcher.parentFile, "glibc/libc.so.6").apply {
            parentFile.mkdirs()
            writeText("libc")
        }
        val patchedLoader = File(launcher.parentFile, "glibc/ld-linux-aarch64.so.1").apply { writeText("loader") }
        val compat = File(launcher.parentFile, "glibc/libkite-node-glibc-compat.so").apply { writeText("compat") }
        val resolv = File(launcher.parentFile, "resolv.conf").apply { writeText("nameserver 1::1\n") }
        val tracer = File(launcher.parentFile, "kite-syscall-tracer").apply {
            writeText("#!/system/bin/sh\n")
            setExecutable(true)
        }
        val node = File(workspace, ".kf/software/kite.nodejs/node-v26.4.0/bin/node")
        val nodeLib = File(node.parentFile.parentFile, "lib").apply { mkdirs() }
        val layout = HostNodeRuntimeLayout(
            rootfsDirectory = rootfs,
            workspaceDirectory = workspace,
            workspaceControlDirectory = File(workspace, ".kf"),
            loader = patchedLoader,
            nodeBinary = node,
            nodeLibraryDirectory = nodeLib,
            glibcLibraryDirectories = listOf(File(rootfs, "usr/lib/aarch64-linux-gnu")),
            assets = HostNodeRuntimeAssets(launcher, preload, patchedLoader, patchedLibc, compat, tracer, resolv),
        )
        val entry = File(workspace, ".kf/software/example/cli.mjs")

        val config = HostNodeRuntimeProvider.buildConfig(
            container = container(rootfs, workspace),
            layout = layout,
            invocation = HostNodeInvocation(entry, listOf("gateway", "run")),
            workingDirectory = workdir,
            syscallTracer = true,
        )
        val environment = config.env.associate { value ->
            value.substringBefore('=') to value.substringAfter('=', "")
        }

        // 监护进程包住整条链：argv = [tracer, launcher, node args...]。
        assertEquals(tracer.absolutePath, config.executablePath)
        assertArrayEquals(
            arrayOf(tracer.absolutePath, launcher.absolutePath, entry.absolutePath, "gateway", "run"),
            config.args,
        )
        // tracer 的定位信息（loader/_r_debug 偏移 + libc 扫 svc）随链注入。
        assertEquals(patchedLoader.absolutePath, environment["KITE_GLIBC_HOST_LOADER"])
        assertEquals(layout.libraryPath, environment["KITE_GLIBC_HOST_LIBRARY_PATH"])
    }

    @Test
    fun `syscall tracer asset participates in runtime identity and deploy`() {
        // 监护进程资产随车道资产一起部署：源码合同（资产名/落盘名/身份摘要）不可漂移。
        val preparer = sequenceOf(
            java.io.File("src/main/kotlin/com/kite/app/foundation/runtime/HostNodeRuntimePreparer.kt"),
            java.io.File("../src/main/kotlin/com/kite/app/foundation/runtime/HostNodeRuntimePreparer.kt"),
            java.io.File("app/src/main/kotlin/com/kite/app/foundation/runtime/HostNodeRuntimePreparer.kt"),
        ).firstOrNull(java.io.File::isFile)?.let { it.readText() }
            ?: error("missing preparer source")
        assertTrue(preparer.contains("kite-syscall-tracer-arm64"))
        assertTrue(preparer.contains("kite-syscall-tracer"))
        assertTrue(preparer.contains("tracerSha256"))
        val asset = sequenceOf(
            java.io.File("../assets/node-runtime/kite-syscall-tracer-arm64"),
            java.io.File("assets/node-runtime/kite-syscall-tracer-arm64"),
        ).firstOrNull(java.io.File::isFile)
        assertTrue("syscall tracer asset must ship with the lane assets", asset?.isFile == true)
    }

    @Test
    fun `explicit native and full linux requirements reject host before asset preparation`() {
        val root = temporaryFolder.newFolder()
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val container = container(File(root, "missing-rootfs"), File(root, "missing-workspace"))

        assertEquals(
            RuntimeProviderDecision.Unsupported(
                RuntimeProviderKind.MANAGED_RUNTIME,
                "android_native_required",
            ),
            HostNodeRuntimeProvider.prepare(
                context = HostNodeProviderContext(
                    context,
                    container,
                    File(root, "missing-workspace"),
                ),
                request = RuntimeExecutionRequest(
                    payload = RuntimeExecutionPayload.NativeCapability("network.download_sha256"),
                    requirements = setOf(RuntimeExecutionRequirement.ANDROID_NATIVE),
                ),
            ),
        )
        assertEquals(
            RuntimeProviderDecision.Unsupported(
                RuntimeProviderKind.MANAGED_RUNTIME,
                "full_linux_required",
            ),
            HostNodeRuntimeProvider.prepare(
                context = HostNodeProviderContext(
                    context,
                    container,
                    File(root, "missing-workspace"),
                ),
                request = RuntimeExecutionRequest(
                    payload = RuntimeExecutionPayload.Argv("node", listOf("--version")),
                    requirements = setOf(RuntimeExecutionRequirement.FULL_LINUX),
                ),
            ),
        )
    }

    @Test
    fun `filesystem view is not a rejection reason and falls through to asset checks`() {
        val root = temporaryFolder.newFolder()
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val container = container(File(root, "missing-rootfs"), File(root, "missing-workspace"))

        val decision = HostNodeRuntimeProvider.prepare(
            context = HostNodeProviderContext(
                context,
                container,
                File(root, "missing-workspace"),
            ),
            request = RuntimeExecutionRequest(
                payload = RuntimeExecutionPayload.Argv("node", listOf("--version")),
                requirements = setOf(RuntimeExecutionRequirement.FILESYSTEM_VIEW),
            ),
        ) as RuntimeProviderDecision.Unsupported

        // FILESYSTEM_VIEW 不再是拒绝理由；路径翻译由预载层提供，缺件才是回退原因。
        assertNotEquals("full_linux_required", decision.reason)
        assertEquals("workspace_missing", decision.reason)
    }

    private fun container(
        rootfs: File,
        workspace: File,
        networkMode: NetworkMode = NetworkMode.HOST,
    ): ContainerRecord = ContainerRecord(
        id = "ubuntu-main",
        displayName = "Ubuntu",
        imageName = "ubuntu-base-24.04-arm64",
        rootfsPath = rootfs.absolutePath,
        workspacePath = workspace.absolutePath,
        createdAt = 10L,
        networkMode = networkMode,
    )
}
