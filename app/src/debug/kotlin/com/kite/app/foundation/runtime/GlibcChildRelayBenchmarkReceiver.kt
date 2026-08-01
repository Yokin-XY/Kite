package com.kite.app.foundation.runtime

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import com.kite.app.foundation.contracts.ContainerExecConfig
import com.kite.app.foundation.contracts.ContainerLaunchConfig
import com.kite.app.foundation.contracts.ContainerRecord
import com.kite.app.foundation.workspace.WorkSurfaceRuntimeBridge
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlin.math.ceil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Debug-only 固定 child relay 矩阵；原生资产由 ADB 部署，不进入 APK/Git。 */
class GlibcChildRelayBenchmarkReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION) return
        context.startService(Intent(context, GlibcChildRelayBenchmarkService::class.java))
    }

    internal companion object {
        const val ACTION = "com.kite.app.debug.GLIBC_CHILD_RELAY_BENCHMARK"
        const val LOG_TAG = "[KFShell]ChildRelay"

        fun safe(value: String): String = value.take(260).map { character ->
            if (character.isLetterOrDigit() || character in "-_.:,=/") character else '_'
        }.joinToString("")
    }
}

class GlibcChildRelayBenchmarkService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!running.compareAndSet(false, true)) return START_NOT_STICKY
        scope.launch {
            try {
                GlibcChildRelayBenchmark.run(applicationContext).forEach { report ->
                    Log.i(GlibcChildRelayBenchmarkReceiver.LOG_TAG, report)
                }
            } catch (error: Throwable) {
                Log.e(
                    GlibcChildRelayBenchmarkReceiver.LOG_TAG,
                    "status=failed reason=${GlibcChildRelayBenchmarkReceiver.safe(error.message ?: error.javaClass.simpleName)}",
                    error,
                )
            } finally {
                running.set(false)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private companion object {
        val running = AtomicBoolean(false)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}

private object GlibcChildRelayBenchmark {
    private const val TIMEOUT_MS = 20_000L
    private const val INPUT = "STDIN_PAYLOAD\n"

    private data class Layout(
        val container: ContainerRecord,
        val rootfs: File,
        val workspace: File,
        val control: File,
        val assets: GlibcHostRuntimeAssets,
        val relay: File,
        val sourceProbe: File,
        val probe: File,
        val work: File,
        val containerProbe: String,
        val containerWork: String,
        val prefixFile: File,
        val environmentFile: File,
        val relayLog: File,
        val libraryPath: String,
    )

    private data class ProbeCase(
        val name: String,
        val hostArguments: List<String>,
        val prootArguments: List<String> = hostArguments,
        val stdin: String = "",
        val compareOutput: Boolean = true,
    )

    private data class Execution(
        val exitCode: Int,
        val stdout: String,
        val stderr: String,
        val elapsedMs: Long,
        val timedOut: Boolean,
    )

    private data class Batch(
        val wallMs: Long,
        val executions: List<Execution>,
    )

    fun run(context: Context): List<String> {
        val layout = prepareLayout(context)
        val script = File(layout.work, "probe-script.sh").apply {
            writeText("#!/bin/sh\n/usr/bin/printf 'SCRIPT_OK:%s\\n' \"${'$'}1\"\n")
            check(setExecutable(true, false) || canExecute()) { "relay_script_not_executable" }
        }
        val hostActionOutput = File(layout.work, "host-file-action.txt")
        val prootActionOutput = File(layout.work, "proot-file-action.txt")
        val noExec = File(layout.work, "no-exec.sh").apply {
            writeText("#!/bin/sh\nexit 0\n")
            setExecutable(false, false)
        }
        val badShebang = File(layout.work, "bad-shebang.sh").apply {
            writeText("#!/kf-no-such-interpreter\nexit 0\n")
            check(setExecutable(true, false) || canExecute()) { "relay_bad_shebang_not_executable" }
        }
        val rootfsPrintf = File(layout.rootfs, "usr/bin/printf")
        check(rootfsPrintf.isFile) { "relay_rootfs_printf_missing" }
        val cases = listOf(
            ProbeCase("execve", listOf("execve")),
            ProbeCase("execv", listOf("execv")),
            ProbeCase("execvp", listOf("execvp")),
            ProbeCase("execvpe", listOf("execvpe")),
            ProbeCase("execl", listOf("execl")),
            ProbeCase("execlp", listOf("execlp")),
            ProbeCase("execle", listOf("execle")),
            ProbeCase("spawn", listOf("spawn")),
            ProbeCase("spawnp", listOf("spawnp")),
            ProbeCase("envcwd", listOf("envcwd")),
            ProbeCase("stdio", listOf("stdio")),
            ProbeCase("stdin", listOf("stdin"), stdin = INPUT),
            ProbeCase("exit37", listOf("exit37")),
            ProbeCase("signal", listOf("signal")),
            ProbeCase(
                "script",
                hostArguments = listOf("script", script.absolutePath),
                prootArguments = listOf("script", containerPath(layout, script)),
            ),
            ProbeCase(
                "file_actions",
                hostArguments = listOf("file_actions", hostActionOutput.absolutePath),
                prootArguments = listOf("file_actions", containerPath(layout, prootActionOutput)),
            ),
            ProbeCase("fork_exec", listOf("fork_exec")),
            ProbeCase("system", listOf("system")),
            ProbeCase("popen", listOf("popen")),
            ProbeCase(
                "fexecve",
                hostArguments = listOf("fexecve", rootfsPrintf.absolutePath),
                prootArguments = listOf("fexecve", "/usr/bin/printf"),
            ),
            ProbeCase("missing_exec", listOf("missing_exec")),
            ProbeCase("missing_spawn", listOf("missing_spawn")),
            ProbeCase(
                "eacces",
                hostArguments = listOf("path_exec", noExec.absolutePath),
                prootArguments = listOf("path_exec", containerPath(layout, noExec)),
            ),
            ProbeCase(
                "bad_shebang",
                hostArguments = listOf("path_exec", badShebang.absolutePath),
                prootArguments = listOf("path_exec", containerPath(layout, badShebang)),
            ),
        )

        return try {
            val reports = mutableListOf<String>()
            cases.forEach { probe ->
                layout.relayLog.delete()
                hostActionOutput.delete()
                if (probe.name == "file_actions") prootActionOutput.delete()
                val host = execute(hostConfig(layout, probe.hostArguments), probe.stdin)
                val hits = layout.relayLog.takeIf(File::isFile)?.readLines().orEmpty()
                val proot = execute(prootConfig(context, layout, probe.prootArguments), probe.stdin)
                val outputMatches = !probe.compareOutput || host.stdout == proot.stdout
                val stderrMatches = host.stderr == proot.stderr
                val exitMatches = host.exitCode == proot.exitCode && host.timedOut == proot.timedOut
                val fileMatches = if (probe.name == "file_actions") {
                    hostActionOutput.takeIf(File::isFile)?.readText() ==
                        prootActionOutput.takeIf(File::isFile)?.readText()
                } else {
                    true
                }
                reports += "status=ok case=${probe.name} hits=${hits.joinToString(",").ifBlank { "none" }} " +
                    "hostExit=${host.exitCode} prootExit=${proot.exitCode} exitMatches=$exitMatches " +
                    "stdoutMatches=$outputMatches stderrMatches=$stderrMatches fileMatches=$fileMatches " +
                    "hostMs=${host.elapsedMs} prootMs=${proot.elapsedMs} " +
                    "hostOut=${GlibcChildRelayBenchmarkReceiver.safe(host.stdout)} " +
                    "hostErr=${GlibcChildRelayBenchmarkReceiver.safe(host.stderr)}"
            }

            listOf("missing_exec", "missing_spawn").forEach { name ->
                val direct = execute(hostConfig(layout, listOf(name), withRelay = false))
                reports += "status=ok case=${name}_direct_host exit=${direct.exitCode} " +
                    "out=${GlibcChildRelayBenchmarkReceiver.safe(direct.stdout)} " +
                    "err=${GlibcChildRelayBenchmarkReceiver.safe(direct.stderr)}"
            }
            listOf(1, 4, 8).forEach { concurrency ->
                val hostBatches = List(3) {
                    batch(concurrency) { execute(hostConfig(layout, listOf("execve"))) }
                }
                val prootBatches = List(3) {
                    batch(concurrency) { execute(prootConfig(context, layout, listOf("execve"))) }
                }
                val hostExecutions = hostBatches.flatMap(Batch::executions)
                val prootExecutions = prootBatches.flatMap(Batch::executions)
                val hostFailures = hostExecutions.count { it.exitCode != 0 || it.stdout != "EXECVE_OK\n" || it.timedOut }
                val prootFailures = prootExecutions.count { it.exitCode != 0 || it.stdout != "EXECVE_OK\n" || it.timedOut }
                reports += "status=ok case=concurrency concurrency=$concurrency rounds=3 " +
                    "hostWallMedianMs=${percentile(hostBatches.map(Batch::wallMs), 0.50)} " +
                    "hostP95Ms=${percentile(hostExecutions.map(Execution::elapsedMs), 0.95)} " +
                    "prootWallMedianMs=${percentile(prootBatches.map(Batch::wallMs), 0.50)} " +
                    "prootP95Ms=${percentile(prootExecutions.map(Execution::elapsedMs), 0.95)} " +
                    "hostFailures=$hostFailures prootFailures=$prootFailures"
            }
            reports += "status=ok suite=rf1320_child_relay cases=${cases.size + 5}"
            reports
        } finally {
            layout.work.parentFile?.deleteRecursively()
        }
    }

    private fun prepareLayout(context: Context): Layout {
        val container = WorkSurfaceRuntimeBridge.ensureDefaultContainer(context)
        val rootfs = File(container.rootfsPath).absoluteFile.normalize()
        val workspace = File(container.workspacePath).absoluteFile.normalize()
        val control = File(workspace, ".kf").absoluteFile.normalize()
        val deployed = File(context.filesDir, "runtime/debug/glibc-child-relay-rf1320").absoluteFile.normalize()
        val relay = File(deployed, "libkite-glibc-child-relay.so")
        val sourceProbe = File(deployed, "kite-glibc-child-probe")
        check(relay.isFile && sourceProbe.isFile) { "relay_debug_assets_missing" }
        check(isArm64Elf(relay) && isArm64Elf(sourceProbe)) { "relay_debug_assets_abi_mismatch" }

        val assets = when (val prepared = GlibcHostRuntimePreparer.prepare(
            context = context,
            container = container,
            workspaceDirectory = workspace,
            workspaceControlDirectory = control,
        )) {
            is GlibcHostRuntimePreparation.Ready -> prepared.assets
            is GlibcHostRuntimePreparation.Unsupported -> error("glibc_assets_${prepared.reason}")
        }
        val root = File(control, "system/bench/glibc-child-relay-rf1320").absoluteFile.normalize()
        val allowed = File(control, "system/bench").absoluteFile.normalize()
        check(root.toPath().startsWith(allowed.toPath())) { "relay_benchmark_root_invalid" }
        root.deleteRecursively()
        val work = File(root, "work")
        check(work.mkdirs()) { "relay_benchmark_root_create_failed" }
        val probe = File(root, "kite-glibc-child-probe")
        sourceProbe.copyTo(probe, overwrite = true)
        check(probe.setExecutable(true, false) || probe.canExecute()) { "relay_probe_not_executable" }
        val libraryDirectories = listOf(
            assets.patchedLibc.parentFile,
            File(rootfs, "usr/lib/aarch64-linux-gnu"),
            File(rootfs, "lib/aarch64-linux-gnu"),
            File(rootfs, "usr/lib"),
            File(rootfs, "lib"),
        ).filter(File::isDirectory).distinctBy(File::getAbsolutePath)
        check(libraryDirectories.isNotEmpty()) { "relay_library_path_missing" }
        val partial = Layout(
            container = container,
            rootfs = rootfs,
            workspace = workspace,
            control = control,
            assets = assets,
            relay = relay,
            sourceProbe = sourceProbe,
            probe = probe,
            work = work,
            containerProbe = "",
            containerWork = "",
            prefixFile = File(root, "proot-prefix.nul"),
            environmentFile = File(root, "proot-environment.nul"),
            relayLog = File(work, "relay.log"),
            libraryPath = libraryDirectories.joinToString(":") { it.absolutePath },
        )
        val containerProbe = containerPath(partial, probe)
        val containerWork = containerPath(partial, work)
        val base = WorkSurfaceRuntimeBridge.buildArgvExecConfig(
            context = context,
            workingDirectory = containerWork,
            argv = listOf("__kite_child_relay_marker__"),
        )
        writeNulList(partial.prefixFile, base.command.dropLast(1))
        writeNulList(partial.environmentFile, base.env.map { (key, value) -> "$key=$value" })
        return partial.copy(containerProbe = containerProbe, containerWork = containerWork)
    }

    private fun hostConfig(
        layout: Layout,
        arguments: List<String>,
        withRelay: Boolean = true,
    ): ContainerLaunchConfig {
        val runtimeRoot = layout.assets.launcher.parentFile
        val tmp = File(runtimeRoot, "tmp").also(File::mkdirs)
        val preload = if (withRelay) {
            "${layout.assets.compatLibrary.absolutePath}:${layout.relay.absolutePath}"
        } else {
            layout.assets.compatLibrary.absolutePath
        }
        val environment = linkedMapOf(
            "HOME" to File(layout.rootfs, "root").absolutePath,
            "USER" to "root",
            "LOGNAME" to "root",
            "LANG" to "C.UTF-8",
            "LC_ALL" to "C",
            "TMPDIR" to tmp.absolutePath,
            "TMP" to tmp.absolutePath,
            "TEMP" to tmp.absolutePath,
            "PWD" to layout.work.absolutePath,
            "PATH" to "/usr/bin:/bin",
            "KF_CHILD_ENV" to "RF1320_ENV",
            "GLIBC_TUNABLES" to "glibc.pthread.rseq=0",
            "KITE_GLIBC_HOST_LANE" to "direct_glibc_v1",
            "KITE_GLIBC_HOST_LOADER" to layout.assets.patchedLoader.absolutePath,
            "KITE_GLIBC_HOST_LIBRARY_PATH" to layout.libraryPath,
            "KITE_GLIBC_HOST_COMPAT_LIBRARY" to preload,
            "KITE_GLIBC_HOST_TARGET" to layout.probe.absolutePath,
            "KITE_GLIBC_HOST_RESOLV_CONF" to layout.assets.resolvConf.absolutePath,
        )
        if (withRelay) {
            environment.putAll(linkedMapOf(
                "KITE_GLIBC_CHILD_RELAY_PREFIX_FILE" to layout.prefixFile.absolutePath,
                "KITE_GLIBC_CHILD_RELAY_ENV_FILE" to layout.environmentFile.absolutePath,
                "KITE_GLIBC_CHILD_RELAY_LOG" to layout.relayLog.absolutePath,
                "KITE_GLIBC_CHILD_RELAY_HOST_ROOTFS" to layout.rootfs.absolutePath,
                "KITE_GLIBC_CHILD_RELAY_HOST_WORKSPACE" to layout.workspace.absolutePath,
                "KITE_GLIBC_CHILD_RELAY_HOST_CONTROL" to layout.control.absolutePath,
            ))
        }
        return ContainerLaunchConfig(
            container = layout.container,
            executablePath = layout.assets.launcher.absolutePath,
            workingDirectory = layout.work.absolutePath,
            args = (listOf(layout.assets.launcher.absolutePath) + arguments).toTypedArray(),
            env = environment.map { (key, value) -> "$key=$value" }.toTypedArray(),
        )
    }

    private fun prootConfig(
        context: Context,
        layout: Layout,
        arguments: List<String>,
    ): ContainerExecConfig {
        val config = WorkSurfaceRuntimeBridge.buildArgvExecConfig(
            context = context,
            workingDirectory = layout.containerWork,
            argv = listOf(layout.containerProbe) + arguments,
        )
        return config.copy(env = config.env + mapOf(
            "PATH" to "/usr/bin:/bin",
            "KF_CHILD_ENV" to "RF1320_ENV",
        ))
    }

    private fun execute(config: ContainerLaunchConfig, input: String = ""): Execution {
        val environment = config.env.associate { entry ->
            entry.substringBefore('=') to entry.substringAfter('=', "")
        }
        return execute(config.args.toList(), File(config.workingDirectory), environment, input)
    }

    private fun execute(config: ContainerExecConfig, input: String = ""): Execution =
        execute(config.command, null, config.env, input)

    private fun execute(
        command: List<String>,
        directory: File?,
        environment: Map<String, String>,
        input: String,
    ): Execution {
        val startedAt = SystemClock.elapsedRealtime()
        val process = ProcessBuilder(command)
            .redirectErrorStream(false)
            .apply {
                if (directory != null) this.directory(directory)
                environment().putAll(environment)
            }
            .start()
        if (input.isNotEmpty()) {
            process.outputStream.use { it.write(input.toByteArray()) }
        } else {
            process.outputStream.close()
        }
        val stdout = ByteArrayOutputStream()
        val stderr = ByteArrayOutputStream()
        val stdoutReader = thread(start = true, isDaemon = true) {
            process.inputStream.use { it.copyTo(stdout) }
        }
        val stderrReader = thread(start = true, isDaemon = true) {
            process.errorStream.use { it.copyTo(stderr) }
        }
        val finished = process.waitFor(TIMEOUT_MS, TimeUnit.MILLISECONDS)
        if (!finished) {
            process.destroyForcibly()
            process.waitFor(1_000L, TimeUnit.MILLISECONDS)
        }
        stdoutReader.join(1_000L)
        stderrReader.join(1_000L)
        return Execution(
            exitCode = if (finished) process.exitValue() else -1,
            stdout = stdout.toString(Charsets.UTF_8.name()),
            stderr = stderr.toString(Charsets.UTF_8.name()),
            elapsedMs = SystemClock.elapsedRealtime() - startedAt,
            timedOut = !finished,
        )
    }

    private fun writeNulList(file: File, values: List<String>) {
        file.parentFile?.mkdirs()
        file.outputStream().use { output ->
            values.forEach { value ->
                output.write(value.toByteArray(Charsets.UTF_8))
                output.write(0)
            }
        }
    }

    private fun batch(concurrency: Int, action: () -> Execution): Batch {
        val executor = Executors.newFixedThreadPool(concurrency)
        val startedAt = SystemClock.elapsedRealtime()
        return try {
            val executions = executor.invokeAll(List(concurrency) { Callable { action() } })
                .map { it.get(TIMEOUT_MS + 5_000L, TimeUnit.MILLISECONDS) }
            Batch(SystemClock.elapsedRealtime() - startedAt, executions)
        } finally {
            executor.shutdownNow()
        }
    }

    private fun percentile(values: List<Long>, ratio: Double): Long {
        require(values.isNotEmpty())
        val sorted = values.sorted()
        val index = ceil(sorted.size * ratio).toInt().coerceIn(1, sorted.size) - 1
        return sorted[index]
    }

    private fun containerPath(layout: Layout, file: File): String {
        val normalized = file.absoluteFile.normalize()
        check(normalized.toPath().startsWith(layout.workspace.toPath())) { "relay_path_outside_workspace" }
        val relative = layout.workspace.toPath().relativize(normalized.toPath()).toString().replace('\\', '/')
        return if (relative.isBlank()) "/workspace" else "/workspace/$relative"
    }

    private fun isArm64Elf(file: File): Boolean = runCatching {
        file.inputStream().use { input ->
            val header = ByteArray(20)
            input.read(header) == header.size &&
                header[0] == 0x7f.toByte() && header[1] == 'E'.code.toByte() &&
                header[2] == 'L'.code.toByte() && header[3] == 'F'.code.toByte() &&
                header[18].toInt() and 0xff == 183 && header[19].toInt() and 0xff == 0
        }
    }.getOrDefault(false)
}
