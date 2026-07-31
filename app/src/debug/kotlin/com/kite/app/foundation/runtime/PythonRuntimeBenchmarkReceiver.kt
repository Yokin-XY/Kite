package com.kite.app.foundation.runtime

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import com.kite.app.foundation.contracts.ContainerLaunchConfig
import com.kite.app.foundation.contracts.ContainerRecord
import com.kite.app.foundation.workspace.WorkSurfaceRuntimeBridge
import com.kite.app.resources.KiteResourceManifestLoader
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Locale
import java.util.TimeZone
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

/** Debug-only 固定 Python Host/PRoot 矩阵；不接受外部命令、路径、负载或并发参数。 */
class PythonRuntimeBenchmarkReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in setOf(ACTION_BENCHMARK, ACTION_COMPATIBILITY, ACTION_LAYERED)) return
        runCatching {
            context.startService(
                Intent(context, PythonRuntimeBenchmarkService::class.java).putExtra(
                    EXTRA_MODE,
                    when (intent.action) {
                        ACTION_COMPATIBILITY -> MODE_COMPATIBILITY
                        ACTION_LAYERED -> MODE_LAYERED
                        else -> MODE_BENCHMARK
                    },
                )
            )
        }.onFailure { error ->
            Log.e(
                LOG_TAG,
                "status=trigger_rejected reason=${safe(error.message ?: error.javaClass.simpleName)}",
                error,
            )
        }
    }

    internal companion object {
        const val ACTION_BENCHMARK = "com.kite.app.debug.PYTHON_RUNTIME_BENCHMARK"
        const val ACTION_COMPATIBILITY = "com.kite.app.debug.PYTHON_RUNTIME_COMPATIBILITY"
        const val ACTION_LAYERED = "com.kite.app.debug.PYTHON_RUNTIME_LAYERED"
        const val EXTRA_MODE = "mode"
        const val MODE_BENCHMARK = "benchmark"
        const val MODE_COMPATIBILITY = "compatibility"
        const val MODE_LAYERED = "layered"
        const val LOG_TAG = "[KFShell]PythonRuntimeBenchmark"

        fun safe(value: String): String = value.take(240).map { character ->
            if (character.isLetterOrDigit() || character in "-_.:=/%") character else '_'
        }.joinToString("")
    }
}

/** 长矩阵放在 Debug service，广播只负责触发，避免基准本身阻塞主线程。 */
class PythonRuntimeBenchmarkService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!running.compareAndSet(false, true)) return START_NOT_STICKY
        scope.launch {
            try {
                val mode = intent?.getStringExtra(PythonRuntimeBenchmarkReceiver.EXTRA_MODE)
                val reports = when (mode) {
                    PythonRuntimeBenchmarkReceiver.MODE_COMPATIBILITY ->
                        PythonRuntimeBenchmark.runCompatibility(applicationContext)
                    PythonRuntimeBenchmarkReceiver.MODE_LAYERED ->
                        PythonRuntimeBenchmark.runLayeredCompatibility(applicationContext)
                    else -> PythonRuntimeBenchmark.run(applicationContext)
                }
                reports.forEach { report ->
                    Log.i(PythonRuntimeBenchmarkReceiver.LOG_TAG, report)
                }
            } catch (error: Throwable) {
                Log.e(
                    PythonRuntimeBenchmarkReceiver.LOG_TAG,
                    "status=failed reason=${PythonRuntimeBenchmarkReceiver.safe(error.message ?: error.javaClass.simpleName)}",
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

private object PythonRuntimeBenchmark {
    private const val TOKEN = "KITE_PY_OK"
    private const val TIMEOUT_MS = 45_000L
    private const val ROUNDS = 3
    private val LEVELS = listOf(1, 4, 8, 16)
    private val WORKLOADS = listOf(
        Workload("startup", "print(\"$TOKEN\")"),
        Workload(
            "imports",
            "import hashlib,json,lzma,ssl,sqlite3,socket;" +
                "assert socket.getaddrinfo(\"localhost\",80);print(\"$TOKEN\")",
        ),
        Workload(
            "small_files",
            "import pathlib,shutil,tempfile;" +
                "p=pathlib.Path(tempfile.mkdtemp(prefix=\"kite-py-small-\"));" +
                "fs=[p/f\"f-{i}.txt\" for i in range(48)];" +
                "[f.write_text(f\"sample-{i}\") for i,f in enumerate(fs)];" +
                "assert sum(len(f.read_text()) for f in fs)>0;shutil.rmtree(p);print(\"$TOKEN\")",
        ),
        Workload(
            "cpu",
            "import hashlib;payload=b\"kite\"*1024;" +
                "digest=b\"\";" +
                "exec(\"for _ in range(20000):\\n digest=hashlib.sha256(payload+digest).digest()\");" +
                "assert digest;print(\"$TOKEN\")",
        ),
        Workload(
            "io",
            "import os,pathlib,tempfile;fd,path=tempfile.mkstemp(prefix=\"kite-py-io-\");" +
                "os.close(fd);pathlib.Path(path).write_bytes(b\"k\"*(8*1024*1024));" +
                "assert os.path.getsize(path)==8*1024*1024;" +
                "fd=os.open(path,os.O_RDONLY);assert len(os.read(fd,4096))==4096;" +
                "os.close(fd);os.unlink(path);print(\"$TOKEN\")",
        ),
    )

    fun run(context: Context): List<String> {
        val host = resolveHost(context)
        val reports = mutableListOf<String>()
        reports += "status=started python=${safe(host.pythonBinary.absolutePath)} " +
            "rounds=$ROUNDS levels=${LEVELS.joinToString(",")}"
        reports += functionalReports(context, host)
        WORKLOADS.forEach { workload ->
            listOf(Lane.HOST, Lane.PROOT).forEach { lane ->
                LEVELS.forEach { concurrency ->
                    reports += benchmark(context, host, workload, lane, concurrency)
                }
            }
        }
        reports += "status=complete"
        return reports
    }

    fun runCompatibility(context: Context): List<String> {
        val host = resolveHost(context)
        return buildList {
            add("status=compatibility_started python=${safe(host.pythonBinary.absolutePath)}")
            addAll(functionalReports(context, host))
            add("status=compatibility_complete")
        }
    }

    fun runLayeredCompatibility(context: Context): List<String> {
        val host = resolveHost(context)
        return buildList {
            add("status=layered_started python=${safe(host.pythonBinary.absolutePath)}")
            addAll(layeredReports(context, host))
            add("status=layered_complete")
        }
    }

    private fun functionalReports(context: Context, host: HostPythonLayout): List<String> = listOf(
        productionProviderReport(context, host),
        compatibilityReport(
            context,
            host,
            "stdlib_c_extensions",
            "import _hashlib,_sqlite3,_ssl,ctypes,hashlib,json,lzma,ssl,sqlite3,socket;" +
                "assert socket.getaddrinfo(\"localhost\",80);print(\"$TOKEN\")",
        ),
        compatibilityReport(
            context,
            host,
            "pip_entry",
            "import pip;assert pip.__version__;print(pip.__version__);print(\"$TOKEN\")",
        ),
        compatibilityReport(context, host, "pure_wheel_install", PURE_WHEEL_PROBE),
        compatibilityReport(
            context,
            host,
            "subprocess_python",
            "import subprocess,sys;" +
                "subprocess.run([sys.executable,\"--version\"],check=True);print(\"$TOKEN\")",
        ),
        compatibilityReport(context, host, "venv_create", VENV_CREATE_PROBE),
        compatibilityReport(context, host, "venv_child", VENV_CHILD_PROBE),
    )

    private fun layeredReports(context: Context, host: HostPythonLayout): List<String> = listOf(
        compatibilityReport(
            context,
            host,
            "subprocess_linux_identity",
            "import subprocess;value=subprocess.check_output([\"/bin/uname\",\"-o\"],text=True).strip();" +
                "assert value==\"GNU/Linux\",value;print(\"$TOKEN\")",
        ),
        compatibilityReport(
            context,
            host,
            "os_system_linux_view",
            "import os;assert os.system(\"/bin/sh -c 'grep -q Ubuntu /etc/os-release'\") == 0;" +
                "print(\"$TOKEN\")",
        ),
        compatibilityReport(
            context,
            host,
            "os_exec_python",
            "import os,sys;os.execve(sys.executable,[sys.executable,\"-c\",\"print('$TOKEN')\"],os.environ.copy())",
        ),
        compatibilityReport(context, host, "venv_with_pip", VENV_WITH_PIP_PROBE),
    )

    private fun productionProviderReport(context: Context, host: HostPythonLayout): String {
        val declaration = KiteResourceManifestLoader(context).parseManifestJson(
            PYTHON_PROVIDER_DECLARATION,
        ).agentProfiles.single()
        val guarantees = checkNotNull(
            RuntimeExecutionGuaranteeCodec.decode(declaration.runtimeGuarantees)
        ) { "python_provider_fixture_guarantees_invalid" }
        val decision = HostPythonRuntimeProvider.prepare(
            context = HostPythonProviderContext(context, host.container, host.workspaceDirectory),
            request = RuntimeExecutionRequest(
                payload = RuntimeExecutionPayload.Argv(
                    executable = "python3",
                    arguments = listOf("-c", "print(\"$TOKEN\")"),
                ),
                workingDirectory = "/workspace",
                environment = mapOf("KITE_PYTHON_PROVIDER_PROBE" to "1"),
                guarantees = guarantees,
            ),
        )
        val execution = when (decision) {
            is RuntimeProviderDecision.Ready -> execute(decision.plan)
            is RuntimeProviderDecision.Unsupported -> Execution(
                exitCode = -1,
                stdout = "",
                stderr = "unsupported_${decision.reason}",
            )
            is RuntimeProviderDecision.Blocked -> Execution(
                exitCode = -1,
                stdout = "",
                stderr = "blocked_${decision.reason}",
            )
        }
        return "status=compatibility capability=production_manifest_provider host=${execution.succeeded} " +
            "hostExit=${execution.exitCode} hostReason=${safe(execution.stderr)} " +
            "proot=not_run prootExit=not_run prootReason=provider_host_only"
    }

    private fun compatibilityReport(
        context: Context,
        host: HostPythonLayout,
        capability: String,
        code: String,
    ): String {
        val hostResult = execute(context, host, Lane.HOST, code)
        val prootResult = execute(context, host, Lane.PROOT, code)
        return "status=compatibility capability=$capability host=${hostResult.succeeded} " +
            "hostExit=${hostResult.exitCode} hostReason=${safe(hostResult.stderr)} " +
            "proot=${prootResult.succeeded} prootExit=${prootResult.exitCode} " +
            "prootReason=${safe(prootResult.stderr)}"
    }

    private fun benchmark(
        context: Context,
        host: HostPythonLayout,
        workload: Workload,
        lane: Lane,
        concurrency: Int,
    ): String {
        val samples = mutableListOf<Long>()
        val failures = mutableListOf<String>()
        repeat(ROUNDS) { round ->
            val started = SystemClock.elapsedRealtime()
            val results = parallel(concurrency) {
                execute(context, host, lane, workload.code)
            }
            val wallMs = SystemClock.elapsedRealtime() - started
            val failed = results.filterNot(Execution::succeeded)
            if (failed.isEmpty()) {
                samples += wallMs
            } else {
                failures += "round_${round}_${failed.first().exitCode}_${safe(failed.first().stderr)}"
            }
        }
        val p50 = samples.takeIf(List<Long>::isNotEmpty)?.let { percentile(it, 0.50) } ?: -1L
        val p95 = samples.takeIf(List<Long>::isNotEmpty)?.let { percentile(it, 0.95) } ?: -1L
        val throughput = if (p50 > 0L) concurrency * 1_000.0 / p50 else 0.0
        return "status=${if (failures.isEmpty()) "ok" else "degraded"} workload=${workload.name} " +
            "lane=${lane.label} concurrency=$concurrency rounds=$ROUNDS p50Ms=$p50 p95Ms=$p95 " +
            "throughput=${String.format(Locale.US, "%.2f", throughput)} failures=${failures.size} " +
            "failureReason=${safe(failures.firstOrNull().orEmpty())}"
    }

    private fun execute(
        context: Context,
        host: HostPythonLayout,
        lane: Lane,
        code: String,
    ): Execution {
        val processBuilder = when (lane) {
            Lane.HOST -> ProcessBuilder(host.command(code))
                .directory(host.workspaceDirectory)
                .apply {
                    environment().clear()
                    environment().putAll(host.environment)
                }
            Lane.PROOT -> WorkSurfaceRuntimeBridge.buildArgvExecConfig(
                context = context,
                argv = listOf("python3", "-c", code),
            ).let { config ->
                ProcessBuilder(config.command).apply { environment().putAll(config.env) }
            }
        }.redirectErrorStream(false)

        return execute(processBuilder)
    }

    private fun execute(config: ContainerLaunchConfig): Execution {
        val environmentMap = config.env.associateTo(linkedMapOf()) { entry ->
            entry.substringBefore('=') to entry.substringAfter('=', "")
        }
        return execute(
            ProcessBuilder(config.args.toList())
                .directory(File(config.workingDirectory))
                .apply {
                    environment().clear()
                    environment().putAll(environmentMap)
                }
                .redirectErrorStream(false)
        )
    }

    private fun execute(processBuilder: ProcessBuilder): Execution {
        val process = processBuilder.start()
        val stdout = ByteArrayOutputStream()
        val stderr = ByteArrayOutputStream()
        val outReader = thread(start = true, isDaemon = true) {
            process.inputStream.use { input -> input.copyTo(stdout, limit = 16_384L) }
        }
        val errReader = thread(start = true, isDaemon = true) {
            process.errorStream.use { input -> input.copyTo(stderr, limit = 16_384L) }
        }
        val finished = process.waitFor(TIMEOUT_MS, TimeUnit.MILLISECONDS)
        if (!finished) process.destroyForcibly()
        outReader.join(1_000L)
        errReader.join(1_000L)
        return Execution(
            exitCode = if (finished) process.exitValue() else -1,
            stdout = stdout.toString(Charsets.UTF_8.name()),
            stderr = stderr.toString(Charsets.UTF_8.name()),
        )
    }

    private fun resolveHost(context: Context): HostPythonLayout {
        val container = checkNotNull(KFContainerManager.getSavedContainer(context)) {
            "default_container_missing"
        }
        val basis = checkNotNull(
            WorkSurfaceRuntimeBridge.managedCommandVerificationBasis(context, listOf("python3"))
        ) { "python_basis_missing" }
        check(basis.runtimeIdentity.rootfsPath == container.rootfsPath) { "python_rootfs_identity_mismatch" }
        check(basis.runtimeIdentity.workspacePath == container.workspacePath) { "python_workspace_identity_mismatch" }
        val pythonBinary = checkNotNull(basis.commandFiles.singleOrNull()) {
            "python_command_missing"
        }.canonicalPath.let(::File)
        check(pythonBinary.isFile && pythonBinary.canExecute()) { "python_binary_unusable" }
        check(isArm64Elf(pythonBinary)) { "python_binary_abi_mismatch" }
        val pythonRoot = checkNotNull(pythonBinary.parentFile?.parentFile) { "python_root_missing" }
        val pythonLibraryDirectory = File(pythonRoot, "lib")
        check(pythonLibraryDirectory.isDirectory) { "python_libraries_missing" }
        val workspaceDirectory = File(container.workspacePath)
        val assets = when (val prepared = GlibcHostRuntimePreparer.prepare(
            context = context,
            container = container,
            workspaceDirectory = workspaceDirectory,
        )) {
            is GlibcHostRuntimePreparation.Ready -> prepared.assets
            is GlibcHostRuntimePreparation.Unsupported -> error("host_assets_${prepared.reason}")
        }
        val rootfsDirectory = File(container.rootfsPath)
        val glibcDirectories = listOf(
            File(rootfsDirectory, "usr/lib/aarch64-linux-gnu"),
            File(rootfsDirectory, "lib/aarch64-linux-gnu"),
        ).filter(File::isDirectory)
        check(glibcDirectories.isNotEmpty()) { "glibc_libraries_missing" }
        return HostPythonLayout(
            container = container,
            workspaceDirectory = workspaceDirectory,
            rootfsDirectory = rootfsDirectory,
            pythonRoot = pythonRoot,
            pythonBinary = pythonBinary,
            pythonLibraryDirectory = pythonLibraryDirectory,
            glibcLibraryDirectories = glibcDirectories,
            assets = assets,
        )
    }

    private fun isArm64Elf(file: File): Boolean = runCatching {
        val header = file.inputStream().use { input ->
            ByteArray(20).also { bytes -> check(input.read(bytes) == bytes.size) }
        }
        header[0] == 0x7f.toByte() && header[1] == 'E'.code.toByte() &&
            header[2] == 'L'.code.toByte() && header[3] == 'F'.code.toByte() &&
            header[4] == 2.toByte() && header[5] == 1.toByte() &&
            header[18] == 0xb7.toByte() && header[19] == 0.toByte()
    }.getOrDefault(false)

    private fun <T> parallel(count: Int, task: () -> T): List<T> {
        val executor = Executors.newFixedThreadPool(count)
        return try {
            executor.invokeAll((0 until count).map { Callable(task) })
                .map { future -> future.get(TIMEOUT_MS + 10_000L, TimeUnit.MILLISECONDS) }
        } finally {
            executor.shutdownNow()
        }
    }

    private fun percentile(values: List<Long>, ratio: Double): Long {
        val sorted = values.sorted()
        val index = ceil(sorted.size * ratio).toInt().coerceIn(1, sorted.size) - 1
        return sorted[index]
    }

    private fun safe(value: String): String = PythonRuntimeBenchmarkReceiver.safe(value)

    private data class Workload(val name: String, val code: String)

    private enum class Lane(val label: String) {
        HOST("host_python"),
        PROOT("proot"),
    }

    private data class Execution(
        val exitCode: Int,
        val stdout: String,
        val stderr: String,
    ) {
        val succeeded: Boolean get() = exitCode == 0 && stdout.contains(TOKEN)
    }

    private data class HostPythonLayout(
        val container: ContainerRecord,
        val workspaceDirectory: File,
        val rootfsDirectory: File,
        val pythonRoot: File,
        val pythonBinary: File,
        val pythonLibraryDirectory: File,
        val glibcLibraryDirectories: List<File>,
        val assets: GlibcHostRuntimeAssets,
    ) {
        val environment: Map<String, String>
            get() {
                val runtimeRoot = assets.launcher.parentFile
                val tmpDirectory = File(runtimeRoot, "tmp").also(File::mkdirs)
                val certificateFile = File(rootfsDirectory, "etc/ssl/certs/ca-certificates.crt")
                val libraryPath = buildList {
                    add(assets.patchedLibc.parentFile)
                    add(pythonLibraryDirectory)
                    addAll(glibcLibraryDirectories)
                }.distinctBy(File::getAbsolutePath).joinToString(":") { it.absolutePath }
                return linkedMapOf(
                    "HOME" to File(rootfsDirectory, "root").absolutePath,
                    "USER" to "root",
                    "LOGNAME" to "root",
                    "LANG" to "C.UTF-8",
                    "TZ" to TimeZone.getDefault().id,
                    "TMPDIR" to tmpDirectory.absolutePath,
                    "PWD" to workspaceDirectory.absolutePath,
                    "PATH" to "${checkNotNull(pythonBinary.parentFile).absolutePath}:/system/bin",
                    "PYTHONHOME" to pythonRoot.absolutePath,
                    "PYTHONUNBUFFERED" to "1",
                    "GLIBC_TUNABLES" to "glibc.pthread.rseq=0",
                    "SSL_CERT_FILE" to certificateFile.absolutePath,
                    "KITE_GLIBC_HOST_LOADER" to assets.patchedLoader.absolutePath,
                    "KITE_GLIBC_HOST_LIBRARY_PATH" to libraryPath,
                    "KITE_GLIBC_HOST_COMPAT_LIBRARY" to assets.compatLibrary.absolutePath,
                    "KITE_GLIBC_HOST_TARGET" to pythonBinary.absolutePath,
                    "KITE_GLIBC_HOST_RESOLV_CONF" to assets.resolvConf.absolutePath,
                )
            }

        fun command(code: String): List<String> = listOf(
            assets.launcher.absolutePath,
            "-c",
            code,
        )
    }

    private fun java.io.InputStream.copyTo(output: ByteArrayOutputStream, limit: Long) {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var remaining = limit
        while (remaining > 0L) {
            val count = read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
            if (count < 0) break
            if (count > 0) {
                output.write(buffer, 0, count)
                remaining -= count
            }
        }
    }

    private val PURE_WHEEL_PROBE = """
        import pathlib, shutil, sys, tempfile, zipfile
        from pip._internal.cli.main import main
        root = pathlib.Path(tempfile.mkdtemp(prefix="kite-py-wheel-"))
        try:
            wheel = root / "kite_probe-0.0.1-py3-none-any.whl"
            with zipfile.ZipFile(wheel, "w") as archive:
                archive.writestr("kite_probe/__init__.py", "VALUE = 42\n")
                archive.writestr(
                    "kite_probe-0.0.1.dist-info/METADATA",
                    "Metadata-Version: 2.1\nName: kite-probe\nVersion: 0.0.1\n",
                )
                archive.writestr(
                    "kite_probe-0.0.1.dist-info/WHEEL",
                    "Wheel-Version: 1.0\nGenerator: Kite\nRoot-Is-Purelib: true\nTag: py3-none-any\n",
                )
                archive.writestr("kite_probe-0.0.1.dist-info/RECORD", "")
            target = root / "target"
            assert main(["install", "--no-index", "--no-deps", "--target", str(target), str(wheel)]) == 0
            sys.path.insert(0, str(target))
            import kite_probe
            assert kite_probe.VALUE == 42
            print("$TOKEN")
        finally:
            shutil.rmtree(root, ignore_errors=True)
    """.trimIndent()

    private val VENV_CHILD_PROBE = """
        import pathlib, shutil, subprocess, tempfile, venv
        root = pathlib.Path(tempfile.mkdtemp(prefix="kite-py-venv-"))
        try:
            venv.EnvBuilder(with_pip=False).create(root)
            subprocess.run([str(root / "bin/python"), "--version"], check=True)
            print("$TOKEN")
        finally:
            shutil.rmtree(root, ignore_errors=True)
    """.trimIndent()

    private val VENV_CREATE_PROBE = """
        import pathlib, shutil, tempfile, venv
        root = pathlib.Path(tempfile.mkdtemp(prefix="kite-py-venv-create-"))
        try:
            venv.EnvBuilder(with_pip=False).create(root)
            assert (root / "pyvenv.cfg").is_file()
            print("$TOKEN")
        finally:
            shutil.rmtree(root, ignore_errors=True)
    """.trimIndent()

    private val VENV_WITH_PIP_PROBE = """
        import pathlib, shutil, sys, tempfile, venv
        root = pathlib.Path(tempfile.mkdtemp(prefix="kite-py-venv-pip-"))
        try:
            try:
                venv.EnvBuilder(with_pip=True).create(root)
                assert (root / "bin/pip").is_file()
                print("$TOKEN")
            except Exception as error:
                print(type(error).__name__ + ":" + str(error), file=sys.stderr)
                raise
        finally:
            shutil.rmtree(root, ignore_errors=True)
    """.trimIndent()

    private val PYTHON_PROVIDER_DECLARATION = """
        {
          "id": "kite.python-provider-probe",
          "base": {"name": "Python Provider Probe"},
          "agents": [{
            "id": "python-provider-probe",
            "name": "Python Provider Probe",
            "launch": {
              "mode": "managed",
              "providerId": "python-provider-probe",
              "protocol": "acp",
              "transport": "stdio",
              "argv": ["python3", "-c", "print('KITE_PY_OK')"],
              "runtimeGuarantees": ["no_child_process", "verified_native_imports"]
            }
          }]
        }
    """.trimIndent()
}
