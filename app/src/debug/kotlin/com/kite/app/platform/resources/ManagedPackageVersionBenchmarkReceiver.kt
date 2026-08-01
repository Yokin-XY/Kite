package com.kite.app.platform.resources

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.SystemClock
import android.system.Os
import android.util.Log
import com.kite.app.application.resources.ResourceVersionParser
import com.kite.app.foundation.runtime.AndroidNativeStructuredJsonStringProvider as ManagedPackageMetadataVersionCandidate
import com.kite.app.foundation.runtime.RuntimeProviderDecision
import com.kite.app.foundation.runtime.StructuredJsonStringContext as ManagedPackageMetadataContext
import com.kite.app.foundation.runtime.StructuredJsonStringPlan as ManagedPackageMetadataPlan
import com.kite.app.foundation.runtime.StructuredJsonStringRequest as ManagedPackageMetadataRequest
import com.kite.app.foundation.runtime.StructuredJsonStringRoot as ManagedPackageMetadataRoot
import com.kite.app.foundation.workspace.WorkSurfaceRuntimeBridge
import com.kite.app.resources.KiteResourceVersionProbeSpec
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Debug-only 固定包元数据版本矩阵；ADB 只能触发，不能覆盖样例、路径、轮数或阈值。 */
class ManagedPackageVersionBenchmarkReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION) return
        runCatching {
            context.startService(Intent(context, ManagedPackageVersionBenchmarkService::class.java))
        }.onFailure { error ->
            Log.e(LOG_TAG, "status=rejected requiresForeground=true reason=${safe(error.message)}")
        }
    }

    internal companion object {
        const val ACTION = "com.kite.app.debug.MANAGED_PACKAGE_VERSION_BENCHMARK"
        const val LOG_TAG = "[KFShell]ManagedPackageVersion"

        fun safe(value: String?): String = value.orEmpty().take(180).map { character ->
            if (character.isLetterOrDigit() || character in "-_.:=/%") character else '_'
        }.joinToString("")
    }
}

class ManagedPackageVersionBenchmarkService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!running.compareAndSet(false, true)) return START_NOT_STICKY
        scope.launch {
            try {
                ManagedPackageVersionBenchmark.run(applicationContext).forEach { report ->
                    Log.i(ManagedPackageVersionBenchmarkReceiver.LOG_TAG, report)
                }
            } catch (error: Throwable) {
                Log.e(
                    ManagedPackageVersionBenchmarkReceiver.LOG_TAG,
                    "status=failed reason=${ManagedPackageVersionBenchmarkReceiver.safe(error.message)}",
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

private object ManagedPackageVersionBenchmark {
    private const val SUITE = "rf1620_managed_package_version"
    private const val CONTAINER_ROOT = "/workspace/.kf/cache/rf1620-managed-package-version"
    private const val ROUNDS = 9
    private const val METADATA_MAX_BYTES = 1024L
    private const val PROCESS_TIMEOUT_MS = 10_000L
    private const val REQUIRED_REDUCTION_PERCENT = 70.0
    private const val REQUIRED_BATCH_REDUCTION_PERCENT = 60.0
    private const val MAXIMUM_NATIVE_P95_US = 30_000L
    private val VERSION_PROBE = KiteResourceVersionProbeSpec(command = "", group = 0)

    private data class Fixture(
        val id: String,
        val containerPath: String,
        val command: String,
        val request: ManagedPackageMetadataRequest?,
        val expectedRoute: String,
    )

    private data class ShellExecution(
        val durationUs: Long,
        val exitCode: Int,
        val stdout: String,
        val stderr: String,
    )

    private data class VersionOutcome(
        val status: String,
        val value: String = "",
    )

    private data class CandidateExecution(
        val route: String,
        val outcome: VersionOutcome,
        val durationUs: Long,
        val fallbackProcesses: Int,
    )

    fun run(context: Context): List<String> {
        val container = WorkSurfaceRuntimeBridge.ensureDefaultContainer(context)
        val workspace = File(container.workspacePath).absoluteFile.normalize()
        val allowedRoot = File(workspace, ".kf/cache").absoluteFile.normalize()
        val suiteRoot = File(workspace, ".kf/cache/rf1620-managed-package-version").absoluteFile.normalize()
        check(suiteRoot.toPath().startsWith(allowedRoot.toPath())) { "suite_root_invalid" }
        check(!Files.exists(suiteRoot.toPath(), LinkOption.NOFOLLOW_LINKS)) { "fixture_collision" }
        check(suiteRoot.mkdirs()) { "fixture_root_create_failed" }

        val reports = try {
            val fixtures = prepareFixtures(suiteRoot)
            val providerContext = ManagedPackageMetadataContext(
                roots = listOf(ManagedPackageMetadataRoot("/workspace", workspace)),
            )
            val correctness = fixtures.map { fixture ->
                val baseline = if (fixture.expectedRoute == "blocked") null else shellProbe(context, fixture.command)
                val candidate = candidateProbe(context, providerContext, fixture)
                val baselineOutcome = baseline?.let(::outcome) ?: VersionOutcome("blocked")
                val differs = if (fixture.expectedRoute == "blocked") {
                    candidate.route != "blocked" ||
                        candidate.outcome.status != "blocked" ||
                        candidate.fallbackProcesses != 0
                } else {
                    baselineOutcome != candidate.outcome || candidate.route != fixture.expectedRoute
                }
                "status=case suite=$SUITE id=${fixture.id} route=${candidate.route} " +
                    "expectedRoute=${fixture.expectedRoute} baseline=${format(baselineOutcome)} " +
                    "candidate=${format(candidate.outcome)} fallbackProcesses=${candidate.fallbackProcesses} " +
                    "durationUs=${candidate.durationUs} differs=$differs"
            }
            val differenceCount = correctness.count { "differs=true" in it }

            val positive = fixtures.filter { it.expectedRoute == "native" }
            check(positive.size >= 5) { "positive_fixture_count_invalid" }
            shellProbe(context, positive.first().command)
            candidateProbe(context, providerContext, positive.first())
            val shellDurations = List(ROUNDS) { shellProbe(context, positive.first().command).durationUs }
            val nativeDurations = List(ROUNDS) {
                candidateProbe(context, providerContext, positive.first()).also { execution ->
                    check(execution.route == "native" && execution.outcome.status == "version") {
                        "native_positive_failed"
                    }
                }.durationUs
            }
            val shellBatchDurations = List(ROUNDS) {
                val started = SystemClock.elapsedRealtimeNanos()
                positive.forEach { fixture ->
                    check(outcome(shellProbe(context, fixture.command)).status == "version") {
                        "shell_batch_failed_${fixture.id}"
                    }
                }
                (SystemClock.elapsedRealtimeNanos() - started) / 1_000L
            }
            val nativeBatchDurations = List(ROUNDS) {
                val started = SystemClock.elapsedRealtimeNanos()
                positive.forEach { fixture ->
                    val execution = candidateProbe(context, providerContext, fixture)
                    check(execution.route == "native" && execution.outcome.status == "version") {
                        "native_batch_failed_${fixture.id}"
                    }
                }
                (SystemClock.elapsedRealtimeNanos() - started) / 1_000L
            }
            val shellP50Us = percentile(shellDurations, 0.50)
            val shellP95Us = percentile(shellDurations, 0.95)
            val nativeP50Us = percentile(nativeDurations, 0.50)
            val nativeP95Us = percentile(nativeDurations, 0.95)
            val batchShellP50Us = percentile(shellBatchDurations, 0.50)
            val batchNativeP50Us = percentile(nativeBatchDurations, 0.50)
            val reductionPercent = reductionPercent(shellP50Us, nativeP50Us)
            val batchReductionPercent = reductionPercent(batchShellP50Us, batchNativeP50Us)
            val correctnessGate = differenceCount == 0
            val performanceGate = reductionPercent >= REQUIRED_REDUCTION_PERCENT &&
                nativeP95Us <= MAXIMUM_NATIVE_P95_US &&
                batchReductionPercent >= REQUIRED_BATCH_REDUCTION_PERCENT
            correctness + listOf(
                "status=contract suite=$SUITE cases=${fixtures.size} differences=$differenceCount " +
                    "correctnessGate=$correctnessGate adbOverrides=false",
                "status=metric suite=$SUITE rounds=$ROUNDS shellP50Us=$shellP50Us shellP95Us=$shellP95Us " +
                    "nativeP50Us=$nativeP50Us nativeP95Us=$nativeP95Us " +
                    "reductionPercent=${"%.1f".format(java.util.Locale.US, reductionPercent)} " +
                    "batchShellP50Us=$batchShellP50Us batchNativeP50Us=$batchNativeP50Us " +
                    "batchReductionPercent=${"%.1f".format(java.util.Locale.US, batchReductionPercent)} " +
                    "performanceGate=$performanceGate",
            )
        } finally {
            suiteRoot.deleteRecursively()
        }
        val fixturesCleanedOnExit = !Files.exists(suiteRoot.toPath(), LinkOption.NOFOLLOW_LINKS)
        return reports + (
            "status=complete suite=$SUITE providerSource=production fixturesCleanedOnExit=$fixturesCleanedOnExit"
            )
    }

    private fun prepareFixtures(root: File): List<Fixture> {
        fun metadata(relative: String, raw: String): String {
            val target = File(root, relative)
            check(target.parentFile?.mkdirs() != false || target.parentFile?.isDirectory == true) {
                "fixture_parent_failed"
            }
            target.writeText(raw)
            return "$CONTAINER_ROOT/$relative"
        }

        val plain = metadata("plain/package.json", "{\"version\":\"1.2.3\"}")
        val scoped = metadata("scoped/@scope/tool/package.json", "{\"version\":\"2.0.0\"}")
        val prerelease = metadata("prerelease/package.json", "{\"version\":\"3.1.0-beta.2\"}")
        val batchFour = metadata("batch-four/package.json", "{\"version\":\"4.0.0\"}")
        val batchFive = metadata("batch-five/package.json", "{\"version\":\"5.0.0+rf1620\"}")
        val malformed = metadata("malformed/package.json", "{\"version\":")
        val missingField = metadata("missing-field/package.json", "{\"name\":\"fixture\"}")
        val nonString = metadata("non-string/package.json", "{\"version\":1620}")
        val oversized = metadata(
            "oversized/package.json",
            "{\"version\":\"6.0.0\",\"padding\":\"${"x".repeat(METADATA_MAX_BYTES.toInt() + 256)}\"}",
        )
        val symlink = File(root, "symlink/package.json")
        check(symlink.parentFile?.mkdirs() == true) { "symlink_parent_failed" }
        Os.symlink(plain, symlink.absolutePath)
        val symlinkPath = "$CONTAINER_ROOT/symlink/package.json"
        val missing = "$CONTAINER_ROOT/missing/package.json"

        fun structured(id: String, path: String, expectedRoute: String = "native") = Fixture(
            id = id,
            containerPath = path,
            command = nodeVersionCommand(path),
            request = ManagedPackageMetadataRequest(path, METADATA_MAX_BYTES, "version"),
            expectedRoute = expectedRoute,
        )

        return listOf(
            structured("plain", plain),
            structured("scoped", scoped),
            structured("prerelease", prerelease),
            structured("batch_four", batchFour),
            structured("batch_five", batchFive),
            structured("missing", missing, "fallback"),
            structured("symlink", symlinkPath, "fallback"),
            structured("oversized", oversized, "fallback"),
            structured("malformed", malformed, "fallback"),
            structured("missing_field", missingField, "fallback"),
            structured("non_string", nonString, "fallback"),
            Fixture(
                id = "custom_command",
                containerPath = "",
                command = "node -p \"'7.7.7-custom'\"",
                request = null,
                expectedRoute = "fallback",
            ),
            Fixture(
                id = "path_escape",
                containerPath = "$CONTAINER_ROOT/../escape/package.json",
                command = ":",
                request = ManagedPackageMetadataRequest(
                    "$CONTAINER_ROOT/../escape/package.json",
                    METADATA_MAX_BYTES,
                    "version",
                ),
                expectedRoute = "blocked",
            ),
        )
    }

    private fun candidateProbe(
        context: Context,
        providerContext: ManagedPackageMetadataContext,
        fixture: Fixture,
    ): CandidateExecution {
        val started = SystemClock.elapsedRealtimeNanos()
        val request = fixture.request
        if (request == null) {
            val shell = shellProbe(context, fixture.command)
            return CandidateExecution(
                route = "fallback",
                outcome = outcome(shell),
                durationUs = (SystemClock.elapsedRealtimeNanos() - started) / 1_000L,
                fallbackProcesses = 1,
            )
        }
        return when (val decision = ManagedPackageMetadataVersionCandidate.prepare(providerContext, request)) {
            is RuntimeProviderDecision.Ready -> CandidateExecution(
                route = "native",
                outcome = outcome(decision.plan.value),
                durationUs = (SystemClock.elapsedRealtimeNanos() - started) / 1_000L,
                fallbackProcesses = 0,
            )
            is RuntimeProviderDecision.Unsupported -> {
                val shell = shellProbe(context, fixture.command)
                CandidateExecution(
                    route = "fallback",
                    outcome = outcome(shell),
                    durationUs = (SystemClock.elapsedRealtimeNanos() - started) / 1_000L,
                    fallbackProcesses = 1,
                )
            }
            is RuntimeProviderDecision.Blocked -> CandidateExecution(
                route = "blocked",
                outcome = VersionOutcome("blocked", decision.reason),
                durationUs = (SystemClock.elapsedRealtimeNanos() - started) / 1_000L,
                fallbackProcesses = 0,
            )
        }
    }

    private fun shellProbe(context: Context, command: String): ShellExecution {
        val config = WorkSurfaceRuntimeBridge.buildArgvExecConfig(
            context = context,
            argv = listOf("/bin/bash", "-lc", command),
        )
        val started = SystemClock.elapsedRealtimeNanos()
        val process = ProcessBuilder(config.command)
            .redirectErrorStream(false)
            .apply { environment().putAll(config.env) }
            .start()
        val stdout = ByteArrayOutputStream()
        val stderr = ByteArrayOutputStream()
        val stdoutThread = Thread { process.inputStream.use { it.copyTo(stdout) } }.apply { start() }
        val stderrThread = Thread { process.errorStream.use { it.copyTo(stderr) } }.apply { start() }
        val completed = process.waitFor(PROCESS_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        if (!completed) process.destroyForcibly()
        stdoutThread.join(PROCESS_TIMEOUT_MS)
        stderrThread.join(PROCESS_TIMEOUT_MS)
        check(completed) { "version_probe_timeout" }
        return ShellExecution(
            durationUs = (SystemClock.elapsedRealtimeNanos() - started) / 1_000L,
            exitCode = process.exitValue(),
            stdout = stdout.toString(Charsets.UTF_8.name()).trim(),
            stderr = stderr.toString(Charsets.UTF_8.name()).trim(),
        )
    }

    private fun outcome(shell: ShellExecution): VersionOutcome = if (shell.exitCode != 0) {
        VersionOutcome("failed", ManagedPackageVersionBenchmarkReceiver.safe(shell.stderr))
    } else {
        outcome(shell.stdout)
    }

    private fun outcome(raw: String): VersionOutcome =
        ResourceVersionParser.installed(raw, VERSION_PROBE)
            ?.let { version -> VersionOutcome("version", version) }
            ?: VersionOutcome("failed", "unrecognized")

    private fun nodeVersionCommand(containerPath: String): String =
        "node -p \"require('$containerPath').version\""

    private fun format(outcome: VersionOutcome): String =
        if (outcome.value.isBlank()) outcome.status else "${outcome.status}:${outcome.value}"

    private fun reductionPercent(baseline: Long, candidate: Long): Double =
        if (baseline <= 0L) 0.0 else (baseline - candidate).toDouble() * 100.0 / baseline.toDouble()

    private fun percentile(values: List<Long>, ratio: Double): Long {
        val sorted = values.sorted()
        val index = kotlin.math.ceil(sorted.size * ratio).toInt().coerceIn(1, sorted.size) - 1
        return sorted[index]
    }
}
