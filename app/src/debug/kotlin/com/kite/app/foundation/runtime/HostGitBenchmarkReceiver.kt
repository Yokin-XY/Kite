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
import java.nio.file.Files
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

/** Debug-only 固定 Git 兼容/性能矩阵；不接收命令、仓库、文件数或并发参数。 */
class HostGitBenchmarkReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION) return
        context.startService(Intent(context, HostGitBenchmarkService::class.java))
    }

    internal companion object {
        const val ACTION = "com.kite.app.debug.HOST_GIT_BENCHMARK"
        const val LOG_TAG = "[KFShell]HostGitBenchmark"

        fun safe(value: String): String = value.take(200).map { character ->
            if (character.isLetterOrDigit() || character in "-_.:,%") character else '_'
        }.joinToString("")
    }
}

class HostGitBenchmarkService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!running.compareAndSet(false, true)) return START_NOT_STICKY
        scope.launch {
            try {
                HostGitBenchmark.run(applicationContext).forEach { report ->
                    Log.i(HostGitBenchmarkReceiver.LOG_TAG, report)
                }
            } catch (error: Throwable) {
                Log.e(
                    HostGitBenchmarkReceiver.LOG_TAG,
                    "status=failed reason=${HostGitBenchmarkReceiver.safe(error.message ?: error.javaClass.simpleName)}",
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

class HostGitRelayBenchmarkReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION) return
        context.startService(Intent(context, HostGitRelayBenchmarkService::class.java))
    }

    internal companion object {
        const val ACTION = "com.kite.app.debug.HOST_GIT_RELAY_BENCHMARK"
        const val LOG_TAG = "[KFShell]HostGitRelay"
    }
}

class HostGitRelayBenchmarkService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!running.compareAndSet(false, true)) return START_NOT_STICKY
        scope.launch {
            try {
                HostGitBenchmark.run(applicationContext, withRelay = true).forEach { report ->
                    Log.i(HostGitRelayBenchmarkReceiver.LOG_TAG, report)
                }
            } catch (error: Throwable) {
                Log.e(
                    HostGitRelayBenchmarkReceiver.LOG_TAG,
                    "status=failed reason=${HostGitBenchmarkReceiver.safe(error.message ?: error.javaClass.simpleName)}",
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

private object HostGitBenchmark {
    private const val FILE_COUNT = 1_000
    private const val ROUNDS = 7
    private const val BATCH_ROUNDS = 3
    private const val TIMEOUT_MS = 30_000L
    private val CONCURRENCY_LEVELS = listOf(1, 4, 8)

    private data class Layout(
        val container: ContainerRecord,
        val rootfs: File,
        val workspace: File,
        val control: File,
        val gitBinary: File,
        val assets: GlibcHostRuntimeAssets,
        val libraryPath: String,
        val relayLibrary: File?,
        var relayContract: RelayContract? = null,
    )

    private data class RelayContract(
        val prefixFile: File,
        val environmentFile: File,
        val logFile: File,
    )

    private data class Execution(
        val exitCode: Int,
        val stdout: String,
        val stderr: String,
        val elapsedMs: Long,
        val timedOut: Boolean,
    ) {
        val succeeded: Boolean get() = !timedOut && exitCode == 0
    }

    private data class Batch(
        val wallMs: Long,
        val times: List<Long>,
        val failures: Int,
    )

    fun run(context: Context, withRelay: Boolean = false): List<String> {
        val container = WorkSurfaceRuntimeBridge.ensureDefaultContainer(context)
        val layout = resolveLayout(context, container, withRelay)
        val benchmarkRoot = File(layout.control, "system/bench/host-git-rf1220").absoluteFile.normalize()
        val allowedRoot = File(layout.control, "system/bench").absoluteFile.normalize()
        check(benchmarkRoot.toPath().startsWith(allowedRoot.toPath())) { "git_benchmark_root_invalid" }
        benchmarkRoot.deleteRecursively()
        check(benchmarkRoot.mkdirs()) { "git_benchmark_root_create_failed" }

        return try {
            val hostRepository = File(benchmarkRoot, "host-repository").also(File::mkdirs)
            val prootRepository = File(benchmarkRoot, "proot-repository").also(File::mkdirs)
            val hostHome = File(benchmarkRoot, "host-home").also(File::mkdirs)
            val prootHome = File(benchmarkRoot, "proot-home").also(File::mkdirs)
            seedFiles(hostRepository)
            seedFiles(prootRepository)

            if (withRelay) {
                layout.relayContract = prepareRelayContract(context, layout, hostRepository, benchmarkRoot)
            }

            val hostEnvironment = gitEnvironment(hostHome.absolutePath)
            val prootEnvironment = gitEnvironment(containerPath(layout, prootHome))
            val compatibility = compatibility(
                context,
                layout,
                hostRepository,
                prootRepository,
                hostEnvironment,
                prootEnvironment,
            )
            val performance = performance(
                context,
                layout,
                hostRepository,
                prootRepository,
                hostEnvironment,
                prootEnvironment,
            )
            val subprocess = subprocessCounterexamples(
                context,
                layout,
                hostRepository,
                prootRepository,
                hostEnvironment,
                prootEnvironment,
            )
            buildList {
                add(compatibility)
                addAll(performance)
                addAll(subprocess)
                layout.relayContract?.logFile?.takeIf(File::isFile)?.readLines()?.let { hits ->
                    add(
                        "status=ok case=relay_hits total=${hits.size} " +
                            "entries=${hits.groupingBy { it }.eachCount().entries.joinToString(",") { "${it.key}:${it.value}" }}"
                    )
                }
                add(
                    "status=ok suite=${if (withRelay) "rf1330_host_git_relay" else "rf1220_host_git"} " +
                        "cases=${1 + performance.size + subprocess.size + if (withRelay) 1 else 0}"
                )
            }
        } finally {
            benchmarkRoot.deleteRecursively()
        }
    }

    private fun resolveLayout(context: Context, container: ContainerRecord, withRelay: Boolean): Layout {
        val workspace = File(container.workspacePath).absoluteFile.normalize()
        val control = File(workspace, ".kf").absoluteFile.normalize()
        val rootfs = File(container.rootfsPath).absoluteFile.normalize()
        val managed = File(control, "bin/git")
        val resolved = followManagedLinks(managed, rootfs, workspace, control)
        val expected = File(rootfs, "usr/bin/git").absoluteFile.normalize()
        check(resolved == expected) { "managed_git_identity_mismatch" }
        check(expected.isFile && expected.canExecute()) { "managed_git_binary_missing" }
        check(isArm64Elf(expected)) { "managed_git_abi_mismatch" }
        val assets = when (val prepared = GlibcHostRuntimePreparer.prepare(
            context = context,
            container = container,
            workspaceDirectory = workspace,
            workspaceControlDirectory = control,
        )) {
            is GlibcHostRuntimePreparation.Ready -> prepared.assets
            is GlibcHostRuntimePreparation.Unsupported -> error("glibc_assets_${prepared.reason}")
        }
        val libraryDirectories = listOf(
            assets.patchedLibc.parentFile,
            File(rootfs, "usr/lib/aarch64-linux-gnu"),
            File(rootfs, "lib/aarch64-linux-gnu"),
            File(rootfs, "usr/lib"),
            File(rootfs, "lib"),
        ).filter(File::isDirectory).distinctBy(File::getAbsolutePath)
        check(libraryDirectories.isNotEmpty()) { "git_library_path_missing" }
        val relay = if (withRelay) {
            File(context.filesDir, "runtime/debug/glibc-child-relay-rf1320/libkite-glibc-child-relay.so")
                .absoluteFile.normalize()
                .also { file ->
                    check(file.isFile && isArm64Elf(file)) { "git_relay_asset_missing" }
                }
        } else {
            null
        }
        return Layout(
            container = container,
            rootfs = rootfs,
            workspace = workspace,
            control = control,
            gitBinary = expected,
            assets = assets,
            libraryPath = libraryDirectories.joinToString(":") { it.absolutePath },
            relayLibrary = relay,
        )
    }

    private fun prepareRelayContract(
        context: Context,
        layout: Layout,
        hostRepository: File,
        benchmarkRoot: File,
    ): RelayContract {
        val marker = "__kite_git_relay_marker__"
        val base = WorkSurfaceRuntimeBridge.buildArgvExecConfig(
            context = context,
            workingDirectory = containerPath(layout, hostRepository),
            argv = listOf(marker),
        )
        check(base.command.lastOrNull() == marker) { "git_relay_prefix_marker_mismatch" }
        val prefixFile = File(benchmarkRoot, "relay-prefix.nul")
        val environmentFile = File(benchmarkRoot, "relay-environment.nul")
        val logFile = File(benchmarkRoot, "relay.log")
        writeNulList(prefixFile, base.command.dropLast(1))
        writeNulList(environmentFile, base.env.map { (key, value) -> "$key=$value" })
        logFile.delete()
        return RelayContract(prefixFile, environmentFile, logFile)
    }

    private fun compatibility(
        context: Context,
        layout: Layout,
        hostRepository: File,
        prootRepository: File,
        hostEnvironment: Map<String, String>,
        prootEnvironment: Map<String, String>,
    ): String {
        val commands = listOf(
            listOf("init", "--initial-branch=main"),
            listOf("add", "."),
            listOf("commit", "--no-gpg-sign", "-m", "seed"),
            listOf("rev-parse", "HEAD"),
            listOf("status", "--porcelain=v1", "--untracked-files=all"),
            listOf("log", "-1", "--format=%H"),
        )
        val host = mutableListOf<Execution>()
        val proot = mutableListOf<Execution>()
        commands.forEach { arguments ->
            host += executeHost(layout, hostRepository, arguments, hostEnvironment)
            proot += executeProot(context, layout, prootRepository, arguments, prootEnvironment)
        }
        check(host.all(Execution::succeeded)) {
            "host_git_compat_failed_${host.indexOfFirst { !it.succeeded }}_${host.firstOrNull { !it.succeeded }?.stderr}"
        }
        check(proot.all(Execution::succeeded)) { "proot_git_compat_failed" }
        val hostHead = host[3].stdout.trim()
        val prootHead = proot[3].stdout.trim()
        check(hostHead.matches(Regex("[0-9a-f]{40,64}")) && hostHead == prootHead) {
            "git_commit_identity_mismatch"
        }
        check(host[4].stdout == proot[4].stdout && host[4].stdout.isBlank()) {
            "git_clean_status_mismatch"
        }
        check(host[5].stdout.trim() == hostHead && proot[5].stdout.trim() == prootHead) {
            "git_log_identity_mismatch"
        }

        File(hostRepository, "dir-000/file-0000.txt").appendText("changed\n")
        File(prootRepository, "dir-000/file-0000.txt").appendText("changed\n")
        val diffArgs = listOf("diff", "--no-ext-diff", "--no-color", "--", "dir-000/file-0000.txt")
        val hostDiff = executeHost(layout, hostRepository, diffArgs, hostEnvironment)
        val prootDiff = executeProot(context, layout, prootRepository, diffArgs, prootEnvironment)
        check(hostDiff.succeeded && prootDiff.succeeded && hostDiff.stdout == prootDiff.stdout) {
            "git_diff_mismatch"
        }
        return "status=ok case=compatibility files=$FILE_COUNT commands=${commands.size + 1} " +
            "head=$hostHead diffBytes=${hostDiff.stdout.toByteArray().size}"
    }

    private fun performance(
        context: Context,
        layout: Layout,
        hostRepository: File,
        prootRepository: File,
        hostEnvironment: Map<String, String>,
        prootEnvironment: Map<String, String>,
    ): List<String> {
        val statusArgs = listOf("status", "--porcelain=v1", "--untracked-files=all")
        repeat(2) {
            check(executeHost(layout, hostRepository, statusArgs, hostEnvironment).succeeded)
            check(executeProot(context, layout, prootRepository, statusArgs, prootEnvironment).succeeded)
        }
        val hostSequential = mutableListOf<Long>()
        val prootSequential = mutableListOf<Long>()
        repeat(ROUNDS) { round ->
            if (round % 2 == 0) {
                hostSequential += executeHost(layout, hostRepository, statusArgs, hostEnvironment).requireOk().elapsedMs
                prootSequential += executeProot(context, layout, prootRepository, statusArgs, prootEnvironment).requireOk().elapsedMs
            } else {
                prootSequential += executeProot(context, layout, prootRepository, statusArgs, prootEnvironment).requireOk().elapsedMs
                hostSequential += executeHost(layout, hostRepository, statusArgs, hostEnvironment).requireOk().elapsedMs
            }
        }
        val reports = mutableListOf(
            "status=ok case=status_sequential files=$FILE_COUNT rounds=$ROUNDS " +
                "hostP50Ms=${percentile(hostSequential, 0.50)} hostP95Ms=${percentile(hostSequential, 0.95)} " +
                "prootP50Ms=${percentile(prootSequential, 0.50)} prootP95Ms=${percentile(prootSequential, 0.95)}",
        )
        CONCURRENCY_LEVELS.forEach { concurrency ->
            val hostBatches = List(BATCH_ROUNDS) {
                batch(concurrency) {
                    executeHost(layout, hostRepository, statusArgs, hostEnvironment)
                }
            }
            val prootBatches = List(BATCH_ROUNDS) {
                batch(concurrency) {
                    executeProot(context, layout, prootRepository, statusArgs, prootEnvironment)
                }
            }
            check(hostBatches.sumOf(Batch::failures) == 0) { "host_git_batch_failure_$concurrency" }
            check(prootBatches.sumOf(Batch::failures) == 0) { "proot_git_batch_failure_$concurrency" }
            reports += "status=ok case=status_concurrent concurrency=$concurrency rounds=$BATCH_ROUNDS " +
                "hostWallMedianMs=${percentile(hostBatches.map(Batch::wallMs), 0.50)} " +
                "hostP95Ms=${percentile(hostBatches.flatMap(Batch::times), 0.95)} " +
                "prootWallMedianMs=${percentile(prootBatches.map(Batch::wallMs), 0.50)} " +
                "prootP95Ms=${percentile(prootBatches.flatMap(Batch::times), 0.95)} failures=0"
        }
        return reports
    }

    private fun subprocessCounterexamples(
        context: Context,
        layout: Layout,
        hostRepository: File,
        prootRepository: File,
        hostEnvironment: Map<String, String>,
        prootEnvironment: Map<String, String>,
    ): List<String> {
        val alias = listOf("-c", "alias.kf=!printf KF_ALIAS_OK", "kf")
        val hostAlias = executeHost(layout, hostRepository, alias, hostEnvironment)
        val prootAlias = executeProot(context, layout, prootRepository, alias, prootEnvironment)
        check(prootAlias.succeeded && prootAlias.stdout.contains("KF_ALIAS_OK")) {
            "proot_git_alias_control_failed"
        }

        val hostHook = installHook(hostRepository)
        val prootHook = installHook(prootRepository)
        File(hostRepository, "hook-change.txt").writeText("host\n")
        File(prootRepository, "hook-change.txt").writeText("proot\n")
        val hostAdd = executeHost(layout, hostRepository, listOf("add", "hook-change.txt"), hostEnvironment)
        val prootAdd = executeProot(context, layout, prootRepository, listOf("add", "hook-change.txt"), prootEnvironment)
        check(hostAdd.succeeded && prootAdd.succeeded) { "git_hook_add_failed" }
        val hostCommit = executeHost(
            layout,
            hostRepository,
            listOf("commit", "--no-gpg-sign", "-m", "hook"),
            hostEnvironment,
        )
        val prootCommit = executeProot(
            context,
            layout,
            prootRepository,
            listOf("commit", "--no-gpg-sign", "-m", "hook"),
            prootEnvironment,
        )

        val hostExternalDiffMarker = File(hostRepository, ".git/kf-probes/external-diff.marker")
        val prootExternalDiffMarker = File(prootRepository, ".git/kf-probes/external-diff.marker")
        val hostExternalDiff = installProbeScript(
            repository = hostRepository,
            name = "external-diff",
            body = "/usr/bin/printf 'OK' > '${childVisiblePath(layout, hostExternalDiffMarker)}'\n",
        )
        val prootExternalDiff = installProbeScript(
            repository = prootRepository,
            name = "external-diff",
            body = "/usr/bin/printf 'OK' > '${containerPath(layout, prootExternalDiffMarker)}'\n",
        )
        val hostExternalDiffRun = executeHost(
            layout,
            hostRepository,
            listOf("diff", "--", "dir-000/file-0000.txt"),
            hostEnvironment + ("GIT_EXTERNAL_DIFF" to hostExternalDiff.absolutePath),
        )
        val prootExternalDiffRun = executeProot(
            context,
            layout,
            prootRepository,
            listOf("diff", "--", "dir-000/file-0000.txt"),
            prootEnvironment + ("GIT_EXTERNAL_DIFF" to containerPath(layout, prootExternalDiff)),
        )
        check(prootExternalDiffRun.succeeded && markerIsOk(prootExternalDiffMarker)) {
            "proot_git_external_diff_control_failed_${prootExternalDiffRun.stderr}"
        }

        val hostFilterMarker = File(hostRepository, ".git/kf-probes/filter.marker")
        val prootFilterMarker = File(prootRepository, ".git/kf-probes/filter.marker")
        val hostFilter = installProbeScript(
            repository = hostRepository,
            name = "clean-filter",
            body = "/usr/bin/printf 'OK' > '${childVisiblePath(layout, hostFilterMarker)}'\n" +
                "/usr/bin/tr '[:lower:]' '[:upper:]'\n",
        )
        val prootFilter = installProbeScript(
            repository = prootRepository,
            name = "clean-filter",
            body = "/usr/bin/printf 'OK' > '${containerPath(layout, prootFilterMarker)}'\n" +
                "/usr/bin/tr '[:lower:]' '[:upper:]'\n",
        )
        File(hostRepository, ".gitattributes").writeText("*.kf filter=kfprobe\n")
        File(prootRepository, ".gitattributes").writeText("*.kf filter=kfprobe\n")
        File(hostRepository, "filter-input.kf").writeText("filter-input\n")
        File(prootRepository, "filter-input.kf").writeText("filter-input\n")
        val hostFilterRun = executeHost(
            layout,
            hostRepository,
            listOf(
                "-c",
                "filter.kfprobe.clean=${hostFilter.absolutePath}",
                "add",
                ".gitattributes",
                "filter-input.kf",
            ),
            hostEnvironment,
        )
        val prootFilterRun = executeProot(
            context,
            layout,
            prootRepository,
            listOf(
                "-c",
                "filter.kfprobe.clean=${containerPath(layout, prootFilter)}",
                "add",
                ".gitattributes",
                "filter-input.kf",
            ),
            prootEnvironment,
        )
        val hostFilterIndex = executeHost(
            layout,
            hostRepository,
            listOf("show", ":filter-input.kf"),
            hostEnvironment,
        )
        val prootFilterIndex = executeProot(
            context,
            layout,
            prootRepository,
            listOf("show", ":filter-input.kf"),
            prootEnvironment,
        )
        check(
            prootFilterRun.succeeded && markerIsOk(prootFilterMarker) &&
                prootFilterIndex.succeeded && prootFilterIndex.stdout == "FILTER-INPUT\n"
        ) {
            "proot_git_filter_control_failed_${prootFilterRun.stderr}"
        }

        val hostRemoteMarker = File(hostRepository, ".git/kf-probes/remote.marker")
        val prootRemoteMarker = File(prootRepository, ".git/kf-probes/remote.marker")
        val hostRemoteHelper = installRemoteHelper(
            repository = hostRepository,
            markerPath = childVisiblePath(layout, hostRemoteMarker),
        )
        val prootRemoteHelper = installRemoteHelper(
            repository = prootRepository,
            markerPath = containerPath(layout, prootRemoteMarker),
        )
        val hostRemoteDirectory = requireNotNull(hostRemoteHelper.parentFile)
        val prootRemoteDirectory = requireNotNull(prootRemoteHelper.parentFile)
        val hostRemotePath = hostRemoteDirectory.absolutePath + ":" +
            buildList {
                add(File(layout.rootfs, "usr/lib/git-core").absolutePath)
                if (layout.relayLibrary != null) {
                    add(File(layout.rootfs, "usr/bin").absolutePath)
                    add(File(layout.rootfs, "bin").absolutePath)
                }
            }.joinToString(":")
        val prootRemotePath = containerPath(layout, prootRemoteDirectory) +
            ":/usr/lib/git-core:/usr/bin:/bin"
        val hostRemoteRun = executeHost(
            layout,
            hostRepository,
            listOf("ls-remote", "kfprobe::unused"),
            hostEnvironment + ("PATH" to hostRemotePath),
        )
        val prootRemoteRun = executeProot(
            context,
            layout,
            prootRepository,
            listOf("ls-remote", "kfprobe::unused"),
            prootEnvironment + ("PATH" to prootRemotePath),
        )
        check(prootRemoteRun.succeeded && markerIsOk(prootRemoteMarker)) {
            "proot_git_remote_helper_control_failed_${prootRemoteRun.stderr}"
        }

        val hostSubmodule = executeHost(
            layout,
            hostRepository,
            listOf("submodule", "status"),
            hostEnvironment,
        )
        val prootSubmodule = executeProot(
            context,
            layout,
            prootRepository,
            listOf("submodule", "status"),
            prootEnvironment,
        )
        check(prootSubmodule.succeeded) { "proot_git_submodule_control_failed_${prootSubmodule.stderr}" }
        return listOf(
            "status=ok case=shell_alias hostExit=${hostAlias.exitCode} " +
                "hostOutput=${hostAlias.stdout.contains("KF_ALIAS_OK")} prootExit=${prootAlias.exitCode} prootOutput=true " +
                "hostReason=${HostGitBenchmarkReceiver.safe(hostAlias.stderr)}",
            "status=ok case=pre_commit_hook hostExit=${hostCommit.exitCode} hostMarker=${hostHook.isFile} " +
                "hostMarkerOk=${markerIsOk(hostHook)} prootExit=${prootCommit.exitCode} " +
                "prootMarkerOk=${markerIsOk(prootHook)} " +
                "hostReason=${HostGitBenchmarkReceiver.safe(hostCommit.stderr)}",
            "status=ok case=external_diff hostExit=${hostExternalDiffRun.exitCode} " +
                "hostMarkerOk=${markerIsOk(hostExternalDiffMarker)} prootExit=${prootExternalDiffRun.exitCode} " +
                "prootMarkerOk=true hostReason=${HostGitBenchmarkReceiver.safe(hostExternalDiffRun.stderr)}",
            "status=ok case=clean_filter hostExit=${hostFilterRun.exitCode} " +
                "hostMarkerOk=${markerIsOk(hostFilterMarker)} hostIndexMatches=${hostFilterIndex.succeeded && hostFilterIndex.stdout == prootFilterIndex.stdout} " +
                "prootExit=${prootFilterRun.exitCode} prootMarkerOk=true " +
                "hostReason=${HostGitBenchmarkReceiver.safe(hostFilterRun.stderr)}",
            "status=ok case=remote_helper hostExit=${hostRemoteRun.exitCode} " +
                "hostMarkerOk=${markerIsOk(hostRemoteMarker)} prootExit=${prootRemoteRun.exitCode} prootMarkerOk=true " +
                "hostReason=${HostGitBenchmarkReceiver.safe(hostRemoteRun.stderr)}",
            "status=ok case=submodule hostExit=${hostSubmodule.exitCode} prootExit=${prootSubmodule.exitCode} " +
                "hostReason=${HostGitBenchmarkReceiver.safe(hostSubmodule.stderr)}",
        )
    }

    private fun installProbeScript(repository: File, name: String, body: String): File {
        val script = File(repository, ".git/kf-probes/$name")
        script.parentFile?.mkdirs()
        script.writeText("#!/bin/sh\n$body")
        check(script.setExecutable(true, false) || script.canExecute()) { "git_probe_not_executable_$name" }
        return script
    }

    private fun installRemoteHelper(repository: File, markerPath: String): File {
        val helper = File(repository, ".git/kf-probes/bin/git-remote-kfprobe")
        helper.parentFile?.mkdirs()
        helper.writeText(
            """#!/bin/sh
/usr/bin/printf 'OK' > '$markerPath'
while IFS= read -r line; do
  case "${'$'}line" in
    capabilities) /usr/bin/printf '\n' ;;
    list*) /usr/bin/printf '\n' ;;
    '') break ;;
    *) /usr/bin/printf '\n' ;;
  esac
done
""",
        )
        check(helper.setExecutable(true, false) || helper.canExecute()) { "git_remote_helper_not_executable" }
        return helper
    }

    private fun installHook(repository: File): File {
        val marker = File(repository, ".git/kf-hook-marker")
        val hook = File(repository, ".git/hooks/pre-commit")
        hook.parentFile?.mkdirs()
        hook.writeText("#!/bin/sh\n/usr/bin/printf 'OK' > .git/kf-hook-marker\n")
        check(hook.setExecutable(true, false) || hook.canExecute()) { "git_hook_not_executable" }
        marker.delete()
        return marker
    }

    private fun markerIsOk(marker: File): Boolean =
        marker.isFile && runCatching { marker.readText() == "OK" }.getOrDefault(false)

    private fun seedFiles(repository: File) {
        repeat(FILE_COUNT) { index ->
            val directory = File(repository, "dir-${(index / 100).toString().padStart(3, '0')}")
            directory.mkdirs()
            File(directory, "file-${index.toString().padStart(4, '0')}.txt")
                .writeText("seed-$index\n")
        }
    }

    private fun executeHost(
        layout: Layout,
        repository: File,
        arguments: List<String>,
        environment: Map<String, String>,
    ): Execution = execute(hostConfig(layout, repository, arguments, environment))

    private fun executeProot(
        context: Context,
        layout: Layout,
        repository: File,
        arguments: List<String>,
        environment: Map<String, String>,
    ): Execution {
        val base = WorkSurfaceRuntimeBridge.buildArgvExecConfig(
            context = context,
            workingDirectory = containerPath(layout, repository),
            argv = listOf("git") + arguments,
        )
        return execute(base.copy(env = base.env + environment))
    }

    private fun hostConfig(
        layout: Layout,
        repository: File,
        arguments: List<String>,
        additionalEnvironment: Map<String, String>,
    ): ContainerLaunchConfig {
        val runtimeRoot = layout.assets.launcher.parentFile
        val tmp = File(runtimeRoot, "tmp").also(File::mkdirs)
        val environment = linkedMapOf(
            "HOME" to File(layout.rootfs, "root").absolutePath,
            "USER" to "root",
            "LOGNAME" to "root",
            "LANG" to "C.UTF-8",
            "LC_ALL" to "C",
            "TMPDIR" to tmp.absolutePath,
            "TMP" to tmp.absolutePath,
            "TEMP" to tmp.absolutePath,
            "PWD" to repository.absolutePath,
            "PATH" to buildList {
                add(File(layout.rootfs, "usr/lib/git-core").absolutePath)
                if (layout.relayLibrary != null) {
                    add(File(layout.rootfs, "usr/bin").absolutePath)
                    add(File(layout.rootfs, "bin").absolutePath)
                }
            }.joinToString(":"),
            "GLIBC_TUNABLES" to "glibc.pthread.rseq=0",
            "GIT_EXEC_PATH" to File(layout.rootfs, "usr/lib/git-core").absolutePath,
            "KITE_GLIBC_HOST_LANE" to "direct_glibc_v1",
            "KITE_GLIBC_HOST_LOADER" to layout.assets.patchedLoader.absolutePath,
            "KITE_GLIBC_HOST_LIBRARY_PATH" to layout.libraryPath,
            "KITE_GLIBC_HOST_COMPAT_LIBRARY" to layout.assets.compatLibrary.absolutePath,
            "KITE_GLIBC_HOST_TARGET" to layout.gitBinary.absolutePath,
            "KITE_GLIBC_HOST_RESOLV_CONF" to layout.assets.resolvConf.absolutePath,
        )
        environment.putAll(additionalEnvironment)
        val relay = layout.relayLibrary
        val relayContract = layout.relayContract
        if (relay != null && relayContract != null) {
            environment["KITE_GLIBC_HOST_COMPAT_LIBRARY"] =
                "${layout.assets.compatLibrary.absolutePath}:${relay.absolutePath}"
            environment.putAll(linkedMapOf(
                "KITE_GLIBC_CHILD_RELAY_PREFIX_FILE" to relayContract.prefixFile.absolutePath,
                "KITE_GLIBC_CHILD_RELAY_ENV_FILE" to relayContract.environmentFile.absolutePath,
                "KITE_GLIBC_CHILD_RELAY_LOG" to relayContract.logFile.absolutePath,
                "KITE_GLIBC_CHILD_RELAY_HOST_ROOTFS" to layout.rootfs.absolutePath,
                "KITE_GLIBC_CHILD_RELAY_HOST_WORKSPACE" to layout.workspace.absolutePath,
                "KITE_GLIBC_CHILD_RELAY_HOST_CONTROL" to layout.control.absolutePath,
            ))
        }
        return ContainerLaunchConfig(
            container = layout.container,
            executablePath = layout.assets.launcher.absolutePath,
            workingDirectory = repository.absolutePath,
            args = (listOf(layout.assets.launcher.absolutePath) + arguments).toTypedArray(),
            env = environment.map { (key, value) -> "$key=$value" }.toTypedArray(),
        )
    }

    private fun gitEnvironment(home: String): Map<String, String> = linkedMapOf(
        "HOME" to home,
        "GIT_CONFIG_NOSYSTEM" to "1",
        "GIT_CONFIG_GLOBAL" to "/dev/null",
        "GIT_TERMINAL_PROMPT" to "0",
        "GIT_PAGER" to "cat",
        "PAGER" to "cat",
        "GIT_AUTHOR_NAME" to "Kite RF1220",
        "GIT_AUTHOR_EMAIL" to "rf1220@kite.local",
        "GIT_COMMITTER_NAME" to "Kite RF1220",
        "GIT_COMMITTER_EMAIL" to "rf1220@kite.local",
        "GIT_AUTHOR_DATE" to "2001-02-03T04:05:06Z",
        "GIT_COMMITTER_DATE" to "2001-02-03T04:05:06Z",
    )

    private fun execute(config: ContainerLaunchConfig): Execution {
        val environment = config.env.associate { entry ->
            entry.substringBefore('=') to entry.substringAfter('=', "")
        }
        return execute(config.args.toList(), File(config.workingDirectory), environment)
    }

    private fun execute(config: ContainerExecConfig): Execution =
        execute(config.command, null, config.env)

    private fun execute(
        command: List<String>,
        directory: File?,
        environment: Map<String, String>,
    ): Execution {
        val startedAt = SystemClock.elapsedRealtime()
        val process = ProcessBuilder(command)
            .redirectErrorStream(false)
            .apply {
                if (directory != null) this.directory(directory)
                environment().putAll(environment)
            }
            .start()
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

    private fun batch(concurrency: Int, action: () -> Execution): Batch {
        val executor = Executors.newFixedThreadPool(concurrency)
        val started = SystemClock.elapsedRealtime()
        return try {
            val executions = executor.invokeAll(List(concurrency) { Callable { action() } })
                .map { it.get(TIMEOUT_MS + 5_000L, TimeUnit.MILLISECONDS) }
            Batch(
                wallMs = SystemClock.elapsedRealtime() - started,
                times = executions.map(Execution::elapsedMs),
                failures = executions.count { !it.succeeded },
            )
        } finally {
            executor.shutdownNow()
        }
    }

    private fun Execution.requireOk(): Execution = also {
        check(succeeded) { "git_execution_failed_${exitCode}_${stderr}" }
    }

    private fun followManagedLinks(
        initial: File,
        rootfs: File,
        workspace: File,
        control: File,
    ): File {
        var current = initial.absoluteFile.normalize()
        repeat(8) {
            if (!Files.isSymbolicLink(current.toPath())) return current
            val raw = Files.readSymbolicLink(current.toPath()).toString()
            current = if (raw.startsWith('/')) {
                when {
                    raw == "/workspace/.kf" -> control
                    raw.startsWith("/workspace/.kf/") -> File(control, raw.removePrefix("/workspace/.kf/"))
                    raw == "/workspace" -> workspace
                    raw.startsWith("/workspace/") -> File(workspace, raw.removePrefix("/workspace/"))
                    raw == "/" -> rootfs
                    else -> File(rootfs, raw.removePrefix("/"))
                }
            } else {
                File(current.parentFile, raw)
            }.absoluteFile.normalize()
            check(
                current.toPath().startsWith(rootfs.toPath()) ||
                    current.toPath().startsWith(workspace.toPath())
            ) { "managed_git_link_escape" }
        }
        error("managed_git_link_depth")
    }

    private fun containerPath(layout: Layout, file: File): String {
        val normalized = file.absoluteFile.normalize()
        check(normalized.toPath().startsWith(layout.workspace.toPath())) { "git_path_outside_workspace" }
        val relative = layout.workspace.toPath().relativize(normalized.toPath()).toString().replace('\\', '/')
        return if (relative.isBlank()) "/workspace" else "/workspace/$relative"
    }

    private fun childVisiblePath(layout: Layout, file: File): String =
        if (layout.relayLibrary != null) containerPath(layout, file) else file.absolutePath

    private fun isArm64Elf(file: File): Boolean = runCatching {
        file.inputStream().use { input ->
            val header = ByteArray(20)
            input.read(header) == header.size &&
                header[0] == 0x7f.toByte() && header[1] == 'E'.code.toByte() &&
                header[2] == 'L'.code.toByte() && header[3] == 'F'.code.toByte() &&
                header[18].toInt() and 0xff == 183 && header[19].toInt() and 0xff == 0
        }
    }.getOrDefault(false)

    private fun percentile(values: List<Long>, ratio: Double): Long {
        require(values.isNotEmpty())
        val sorted = values.sorted()
        val index = ceil(sorted.size * ratio).toInt().coerceIn(1, sorted.size) - 1
        return sorted[index]
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
}
