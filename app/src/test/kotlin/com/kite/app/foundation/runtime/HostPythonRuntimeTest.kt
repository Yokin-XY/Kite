package com.kite.app.foundation.runtime

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.kite.app.foundation.contracts.ContainerRecord
import com.kite.app.foundation.contracts.ContainerStatus
import com.kite.app.foundation.contracts.NetworkMode
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HostPythonRuntimeTest {
    @Test
    fun `candidate only accepts structured managed python commands`() {
        assertTrue(HostPythonCommandResolver.isCandidate(request("python3", listOf("--version"))))
        assertTrue(HostPythonCommandResolver.isCandidate(request("/workspace/.kf/bin/python3")))
        assertFalse(HostPythonCommandResolver.isCandidate(request("/usr/bin/python3")))
        assertFalse(HostPythonCommandResolver.isCandidate(request("node")))
        assertFalse(
            HostPythonCommandResolver.isCandidate(
                RuntimeExecutionRequest(RuntimeExecutionPayload.CommandLine("python3 --version"))
            )
        )
    }

    @Test
    fun `resolver binds managed interpreter identity and maps container script`() {
        val fixture = fixture()
        val script = File(fixture.workspace, "jobs/task.py").apply {
            checkNotNull(parentFile).mkdirs()
            writeText("print('ok')")
        }

        val resolution = HostPythonCommandResolver.resolve(
            executable = "python3",
            arguments = listOf("/workspace/jobs/task.py", "value"),
            rootfsDirectory = fixture.rootfs,
            workspaceDirectory = fixture.workspace,
            assets = fixture.assets,
            linkTargetReader = { file ->
                if (file == File(fixture.control, "bin/python3").absoluteFile.normalize()) {
                    "/workspace/.kf/software/kite.python/python-3.14.6/bin/python3.14"
                } else {
                    null
                }
            },
        ) as HostPythonCommandResolution.Ready

        assertEquals(fixture.pythonBinary.canonicalFile, resolution.layout.pythonBinary.canonicalFile)
        assertEquals(listOf(script.absolutePath, "value"), resolution.invocation.arguments)
        assertTrue(resolution.layout.libraryPath.contains("python-3.14.6${File.separator}lib"))
    }

    @Test
    fun `resolver maps absolute file arguments after interpreter and module options`() {
        val fixture = fixture()
        val script = File(fixture.workspace, "jobs/task.py").apply {
            checkNotNull(parentFile).mkdirs()
            writeText("print('ok')")
        }
        val input = File(fixture.workspace, "input.txt").apply { writeText("value") }
        val linkReader: (File) -> String? = { file ->
            if (file == File(fixture.control, "bin/python3").absoluteFile.normalize()) {
                "/workspace/.kf/software/kite.python/python-3.14.6/bin/python3.14"
            } else {
                null
            }
        }

        val scriptResolution = HostPythonCommandResolver.resolve(
            executable = "python3",
            arguments = listOf("-u", "/workspace/jobs/task.py", "/workspace/input.txt"),
            rootfsDirectory = fixture.rootfs,
            workspaceDirectory = fixture.workspace,
            assets = fixture.assets,
            linkTargetReader = linkReader,
        ) as HostPythonCommandResolution.Ready
        val moduleResolution = HostPythonCommandResolver.resolve(
            executable = "python3",
            arguments = listOf("-m", "json.tool", "/workspace/input.txt"),
            rootfsDirectory = fixture.rootfs,
            workspaceDirectory = fixture.workspace,
            assets = fixture.assets,
            linkTargetReader = linkReader,
        ) as HostPythonCommandResolution.Ready

        assertEquals(listOf("-u", script.absolutePath, input.absolutePath), scriptResolution.invocation.arguments)
        assertEquals(listOf("-m", "json.tool", input.absolutePath), moduleResolution.invocation.arguments)
    }

    @Test
    fun `resolver blocks broken managed identity instead of hiding it with fallback`() {
        val fixture = fixture()

        val resolution = HostPythonCommandResolver.resolve(
            executable = "python3",
            arguments = emptyList(),
            rootfsDirectory = fixture.rootfs,
            workspaceDirectory = fixture.workspace,
            assets = fixture.assets,
            linkTargetReader = { "/outside/python3" },
        )

        assertEquals(
            "python_command_link_invalid",
            (resolution as HostPythonCommandResolution.Blocked).reason,
        )
    }

    @Test
    fun `provider rejects unproven child and package lifecycle before asset preparation`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val fixture = fixture()
        val providerContext = HostPythonProviderContext(context, fixture.container, fixture.workspace)

        val child = HostPythonRuntimeProvider.prepare(
            providerContext,
            request("python3", requirements = setOf(RuntimeExecutionRequirement.CHILD_PROCESS)),
        ) as RuntimeProviderDecision.Unsupported
        val pip = HostPythonRuntimeProvider.prepare(
            providerContext,
            request("python3", listOf("-m", "pip", "list")),
        ) as RuntimeProviderDecision.Unsupported
        val venv = HostPythonRuntimeProvider.prepare(
            providerContext,
            request("python3", listOf("-m", "venv", "/tmp/test")),
        ) as RuntimeProviderDecision.Unsupported

        assertEquals("python_child_process_required", child.reason)
        assertEquals("python_package_lifecycle_requires_proot", pip.reason)
        assertEquals("python_venv_requires_proot", venv.reason)
    }

    @Test
    fun `host config uses generic launcher contract and preserves additional env`() {
        val fixture = fixture()
        val layout = fixture.layout()

        val config = HostPythonRuntimeProvider.buildConfig(
            container = fixture.container,
            layout = layout,
            invocation = HostPythonInvocation(listOf("-c", "print('ok')")),
            workingDirectory = fixture.workspace,
            additionalEnvironment = mapOf("KITE_TEST" to "value", "PYTHONHOME" to "/invalid"),
        )
        val environment = config.env.associate { entry ->
            entry.substringBefore('=') to entry.substringAfter('=', "")
        }

        assertEquals(layout.assets.launcher.absolutePath, config.executablePath)
        assertEquals(listOf(layout.assets.launcher.absolutePath, "-c", "print('ok')"), config.args.toList())
        assertEquals("value", environment["KITE_TEST"])
        assertEquals(layout.pythonRoot.absolutePath, environment["PYTHONHOME"])
        assertEquals(layout.pythonBinary.absolutePath, environment["KITE_GLIBC_HOST_TARGET"])
        assertFalse(environment.keys.any { it.startsWith("KITE_NODE_HOST_") })
    }

    private fun request(
        executable: String,
        arguments: List<String> = emptyList(),
        requirements: Set<RuntimeExecutionRequirement> = emptySet(),
    ) = RuntimeExecutionRequest(
        payload = RuntimeExecutionPayload.Argv(executable, arguments),
        requirements = requirements,
    )

    private fun fixture(): Fixture {
        val root = Files.createTempDirectory("kite-host-python-test").toFile()
        val rootfs = File(root, "rootfs").apply { mkdirs() }
        val workspace = File(root, "workspace").apply { mkdirs() }
        val control = File(workspace, ".kf").apply { mkdirs() }
        File(control, "bin").mkdirs()
        val pythonRoot = File(control, "software/kite.python/python-3.14.6")
        val pythonBinary = arm64Elf(File(pythonRoot, "bin/python3.14"))
        File(pythonRoot, "lib/python3.14/os.py").apply {
            checkNotNull(parentFile).mkdirs()
            writeText("# stdlib marker")
        }
        File(pythonRoot, "lib/libpython3.14.so.1.0").writeText("shared")
        val glibc = File(rootfs, "usr/lib/aarch64-linux-gnu").apply { mkdirs() }
        val runtime = File(control, "system/glibc-runtime/host").apply { mkdirs() }
        val assets = GlibcHostRuntimeAssets(
            launcher = executable(File(runtime, "kite-glibc-host")),
            patchedLoader = executable(File(runtime, "glibc/ld-linux-aarch64.so.1")),
            patchedLibc = File(runtime, "glibc/libc.so.6").apply { writeText("libc") },
            compatLibrary = File(runtime, "glibc/libkite-glibc-compat.so").apply { writeText("compat") },
            resolvConf = File(runtime, "resolv.conf").apply { writeText("nameserver 1.1.1.1\n") },
        )
        val container = ContainerRecord(
            id = "test",
            displayName = "Test",
            imageName = "ubuntu",
            rootfsPath = rootfs.absolutePath,
            workspacePath = workspace.absolutePath,
            createdAt = 1L,
            status = ContainerStatus.CREATED,
            networkMode = NetworkMode.HOST,
        )
        return Fixture(rootfs, workspace, control, pythonRoot, pythonBinary, glibc, assets, container)
    }

    private fun arm64Elf(file: File): File {
        val bytes = ByteArray(20)
        bytes[0] = 0x7f
        bytes[1] = 'E'.code.toByte()
        bytes[2] = 'L'.code.toByte()
        bytes[3] = 'F'.code.toByte()
        bytes[4] = 2
        bytes[5] = 1
        bytes[18] = 0xb7.toByte()
        checkNotNull(file.parentFile).mkdirs()
        file.writeBytes(bytes)
        file.setExecutable(true, false)
        return file
    }

    private fun executable(file: File): File = file.apply {
        checkNotNull(parentFile).mkdirs()
        writeText("fixture")
        setExecutable(true, false)
    }

    private data class Fixture(
        val rootfs: File,
        val workspace: File,
        val control: File,
        val pythonRoot: File,
        val pythonBinary: File,
        val glibc: File,
        val assets: GlibcHostRuntimeAssets,
        val container: ContainerRecord,
    ) {
        fun layout() = HostPythonRuntimeLayout(
            rootfsDirectory = rootfs,
            workspaceDirectory = workspace,
            workspaceControlDirectory = control,
            pythonBinary = pythonBinary,
            pythonRoot = pythonRoot,
            pythonLibraryDirectory = File(pythonRoot, "lib"),
            glibcLibraryDirectories = listOf(glibc),
            assets = assets,
        )
    }
}
