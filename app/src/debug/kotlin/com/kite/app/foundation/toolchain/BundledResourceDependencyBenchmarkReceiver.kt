package com.kite.app.foundation.toolchain

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import com.kite.app.foundation.storage.AtomicDirectoryPublisher
import com.kite.app.foundation.storage.ImmutableArtifactIntegrity
import com.kite.app.foundation.workspace.WorkSurfaceRuntimeBridge
import java.io.File
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlin.math.ceil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONObject

/** Debug-only 真实内置包矩阵；ADB 只能触发，不能覆盖任务、依赖、路径、轮数、槽位或阈值。 */
class BundledResourceDependencyBenchmarkReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action?.takeIf { it == ACTION || it == PRODUCTION_PROOF_ACTION } ?: return
        runCatching {
            context.startService(
                Intent(context, BundledResourceDependencyBenchmarkService::class.java).setAction(action)
            )
        }.onFailure { error ->
            Log.e(LOG_TAG, "status=rejected requiresForeground=true reason=${safe(error.message)}")
        }
    }

    internal companion object {
        const val ACTION = "com.kite.app.debug.BUNDLED_RESOURCE_DEPENDENCY_BENCHMARK"
        const val PRODUCTION_PROOF_ACTION = "com.kite.app.debug.BUNDLED_RESOURCE_PRODUCTION_PROOF"
        const val LOG_TAG = "KiteBundledDependency"

        fun safe(value: String?): String = value.orEmpty().take(220).map { character ->
            if (character.isLetterOrDigit() || character in "-_.:=/%") character else '_'
        }.joinToString("")
    }
}

class BundledResourceDependencyBenchmarkService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!running.compareAndSet(false, true)) return START_NOT_STICKY
        val action = intent?.action
        scope.launch {
            try {
                val reports = when (action) {
                    BundledResourceDependencyBenchmarkReceiver.ACTION ->
                        BundledResourceDependencyBenchmark.run(applicationContext)
                    BundledResourceDependencyBenchmarkReceiver.PRODUCTION_PROOF_ACTION ->
                        BundledResourceProductionProof.run(applicationContext)
                    else -> listOf("status=rejected reason=unknown_action")
                }
                reports.forEach { report ->
                    Log.i(BundledResourceDependencyBenchmarkReceiver.LOG_TAG, report)
                }
            } catch (error: Throwable) {
                Log.e(
                    BundledResourceDependencyBenchmarkReceiver.LOG_TAG,
                    "status=failed reason=${BundledResourceDependencyBenchmarkReceiver.safe(error.message)}",
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

private object BundledResourceProductionProof {
    fun run(context: Context): List<String> {
        val contracts = ToolchainPackInstaller.bootstrapResourceSchedulingContracts()
        val beforeSettled = ToolchainPackInstaller.bootstrapResourcesSettled(context)
        val startedAt = SystemClock.elapsedRealtimeNanos()
        val state = ToolchainPackInstaller.prepareAiEnvForBootstrap(context)
        val durationMs = (SystemClock.elapsedRealtimeNanos() - startedAt) / 1_000_000L
        val afterSettled = ToolchainPackInstaller.bootstrapResourcesSettled(context)
        val correctnessGate = contracts.size == 6 &&
            contracts.sumOf { it.dependencies.size } == 1 &&
            state.phase == ToolchainInstallPhase.SUCCEEDED &&
            state.exitCode == 0 &&
            state.summary == "SUMMARY resources=6 failed=0" &&
            afterSettled
        return listOf(
            "status=production_proof suite=rf1840_bundled_resource_production " +
                "resources=${contracts.size} dependencies=${contracts.sumOf { it.dependencies.size }} " +
                "beforeSettled=$beforeSettled afterSettled=$afterSettled phase=${state.phase.name.lowercase()} " +
                "exitCode=${state.exitCode} timedOut=${state.timedOut} durationMs=$durationMs " +
                "summary=${BundledResourceDependencyBenchmarkReceiver.safe(state.summary)} " +
                "correctnessGate=$correctnessGate adbOverrides=false",
        )
    }
}

private object BundledResourceDependencyBenchmark {
    private const val SUITE = "rf1820_bundled_resource_dependency"
    private const val ASSET_ROOT = "toolchain/ai-dev-pack"
    private const val CONTAINER_ROOT = "/workspace/.kf/cache/rf1820-bundled-dependency"
    private const val ROUNDS = 3
    private const val MAXIMUM_CONCURRENCY = 2
    private const val REQUIRED_REDUCTION_PERCENT = 20.0
    private const val REQUIRED_REDUCTION_MS = 5_000L
    private const val MAXIMUM_CANDIDATE_P95_MS = 36_000L
    private const val TASK_TIMEOUT_SECONDS = 900L
    private const val OUTPUT_LIMIT = 48_000

    private data class FixedTask(
        val key: String,
        val mode: String,
        val dependencies: Set<String> = emptySet(),
    )

    private data class TaskResult(
        val key: String,
        val exitCode: Int,
        val timedOut: Boolean,
        val summary: String,
    ) {
        val successful: Boolean = exitCode == 0 && !timedOut && summary.endsWith("FAIL=0")

        fun signature(): String = "$key:$exitCode:$timedOut:$summary"
    }

    private data class BatchResult(
        val results: List<TaskResult>,
        val durationMs: Long,
        val maximumActiveTasks: Int,
    )

    private data class RoundResult(
        val sequential: BatchResult,
        val candidate: BatchResult,
    )

    fun run(context: Context): List<String> {
        val container = WorkSurfaceRuntimeBridge.ensureDefaultContainer(context)
        val hostRoot = File(container.workspacePath, ".kf/cache/rf1820-bundled-dependency")
        hostRoot.deleteRecursively()
        val reports = try {
            val inputHost = File(hostRoot, "input/ai-dev-pack")
            stageFixedPack(context, inputHost)
            val tasks = fixedTasks()
            check(tasks.size == 6) { "fixed_task_count_invalid" }
            check(tasks.sumOf { it.dependencies.size } == 1) { "fixed_dependency_count_invalid" }
            val rounds = List(ROUNDS) { roundIndex ->
                runRound(context, hostRoot, tasks, roundIndex + 1)
            }
            buildReports(rounds, tasks)
        } finally {
            hostRoot.deleteRecursively()
        }
        val fixturesCleanedOnExit = !hostRoot.exists()
        check(fixturesCleanedOnExit) { "fixed_fixture_cleanup_failed" }
        return reports.map { report ->
            if (report.startsWith("status=complete")) "$report fixturesCleanedOnExit=true" else report
        }
    }

    private fun runRound(
        context: Context,
        hostRoot: File,
        tasks: List<FixedTask>,
        round: Int,
    ): RoundResult {
        val sequential = runSequential(context, hostRoot, tasks, "round-$round-sequential")
        val candidate = runCandidate(context, hostRoot, tasks, "round-$round-candidate")
        return RoundResult(sequential, candidate)
    }

    private fun runSequential(
        context: Context,
        hostRoot: File,
        tasks: List<FixedTask>,
        runName: String,
    ): BatchResult {
        val runHost = File(hostRoot, runName).apply { deleteRecursively(); mkdirs() }
        val startedAt = SystemClock.elapsedRealtimeNanos()
        val results = tasks.map { task -> executeTask(context, runHost, task) }
        val durationMs = elapsedMs(startedAt)
        runHost.deleteRecursively()
        check(!runHost.exists()) { "sequential_round_cleanup_failed" }
        return BatchResult(results, durationMs, maximumActiveTasks = 1)
    }

    private fun runCandidate(
        context: Context,
        hostRoot: File,
        tasks: List<FixedTask>,
        runName: String,
    ): BatchResult {
        val runHost = File(hostRoot, runName).apply { deleteRecursively(); mkdirs() }
        val startedAt = SystemClock.elapsedRealtimeNanos()
        val decision = DependencyBatchScheduler.executeOrdered(
            tasks = tasks.map { task ->
                DependencyBatchTask(task.key, task.dependencies) {
                    executeTask(context, runHost, task)
                }
            },
            maximumConcurrency = MAXIMUM_CONCURRENCY,
            isSuccessful = TaskResult::successful,
        )
        val durationMs = elapsedMs(startedAt)
        val report = (decision as? DependencyBatchDecision.Completed)
            ?.report ?: error("candidate_scheduler_blocked")
        val results = report.outcomes.map { outcome ->
            when (outcome) {
                is DependencyBatchTaskOutcome.Executed -> outcome.value
                    ?: error("candidate_task_failed:${outcome.key}:${outcome.failureReason}")
                is DependencyBatchTaskOutcome.DependencyBlocked ->
                    error("candidate_dependency_blocked:${outcome.key}")
            }
        }
        runHost.deleteRecursively()
        check(!runHost.exists()) { "candidate_round_cleanup_failed" }
        return BatchResult(results, durationMs, report.maximumActiveTasks)
    }

    private fun executeTask(
        context: Context,
        runHost: File,
        task: FixedTask,
    ): TaskResult {
        val safeKey = task.key.replace(Regex("[^a-z0-9-]"), "-")
        val runContainer = "$CONTAINER_ROOT/${runHost.name}"
        val toolchainContainer = "$runContainer/software/$safeKey"
        val binContainer = "$runContainer/bin"
        val packContainer = "$CONTAINER_ROOT/input/ai-dev-pack"
        val scriptContainer = "$packContainer/install.sh"
        val payload = """
            export KF_TOOLCHAIN_PACK_DIR=$packContainer
            export KF_TOOLCHAIN_DIR=$toolchainContainer
            export KF_TOOLCHAIN_BIN_DIR=$binContainer
            export UV_LINK_MODE=copy
            chmod +x "$scriptContainer" 2>/dev/null || true
            bash "$scriptContainer" "${task.mode}"
        """.trimIndent()
        val config = WorkSurfaceRuntimeBridge.buildShellExecConfig(
            context = context,
            workingDirectory = "/workspace",
            payload = payload,
            loginShell = true,
        )
        val output = StringBuilder()
        val process = ProcessBuilder(config.command)
            .redirectErrorStream(true)
            .apply { environment().putAll(config.env) }
            .start()
        activeProcesses.add(process)
        val reader = thread(start = true, isDaemon = true, name = "RF1820-${task.key}") {
            runCatching {
                process.inputStream.bufferedReader().useLines { lines ->
                    lines.forEach { line ->
                        synchronized(output) {
                            if (output.length < OUTPUT_LIMIT) output.append(line).append('\n')
                        }
                    }
                }
            }
        }
        val finished = process.waitFor(TASK_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        if (!finished) {
            process.destroyForcibly()
            runCatching { process.waitFor(5L, TimeUnit.SECONDS) }
        }
        activeProcesses.remove(process)
        reader.join(1_500L)
        val text = synchronized(output) { output.toString() }
        val summary = text.lineSequence().lastOrNull { line -> line.startsWith("SUMMARY ") }
            ?: text.lineSequence().lastOrNull { line -> line.startsWith("FAIL") }
            ?: "summary_missing"
        return TaskResult(
            key = task.key,
            exitCode = if (finished) process.exitValue() else -1,
            timedOut = !finished,
            summary = summary,
        )
    }

    private fun stageFixedPack(context: Context, inputHost: File) {
        val expectedManifest = context.assets.open("$ASSET_ROOT/manifest.json")
            .bufferedReader()
            .use { it.readText() }
        val integrity = context.assets.open("$ASSET_ROOT/${ImmutableArtifactIntegrity.INTEGRITY_FILE}")
            .bufferedReader()
            .use { reader -> ImmutableArtifactIntegrity.parse(reader.readText()) }
        AtomicDirectoryPublisher.publish(
            destination = inputHost,
            isComplete = integrity::isPublished,
        ) { pending ->
            pending.mkdirs()
            copyAssetTree(context, ASSET_ROOT, pending)
            pending.walkTopDown()
                .filter { file -> file.isFile && file.extension.equals("sh", ignoreCase = true) }
                .forEach { file ->
                    val original = file.readText(Charsets.UTF_8)
                    val normalized = original.replace("\r\n", "\n").replace("\r", "\n")
                    if (normalized != original) file.writeText(normalized, Charsets.UTF_8)
                }
            integrity.validateStageAndSeal(pending)
        }
        val expected = JSONObject(expectedManifest)
        val actual = JSONObject(File(inputHost, "manifest.json").readText())
        check(
            actual.optString("packId") == expected.optString("packId") &&
                actual.optInt("version") == expected.optInt("version"),
        ) {
            "fixed_pack_identity_invalid"
        }
    }

    private fun copyAssetTree(context: Context, assetPath: String, destination: File) {
        val children = context.assets.list(assetPath).orEmpty()
        if (children.isEmpty()) {
            destination.parentFile?.mkdirs()
            context.assets.open(assetPath).use { input ->
                destination.outputStream().use { output -> input.copyTo(output) }
            }
            return
        }
        destination.mkdirs()
        children.forEach { child ->
            copyAssetTree(context, "$assetPath/$child", File(destination, child))
        }
    }

    private fun buildReports(rounds: List<RoundResult>, tasks: List<FixedTask>): List<String> {
        val differences = rounds.sumOf { round ->
            round.sequential.results.zip(round.candidate.results).count { (left, right) ->
                left.signature() != right.signature()
            }
        }
        val reductionsMs = rounds.map { round -> round.sequential.durationMs - round.candidate.durationMs }
        val reductionsPercent = rounds.map { round ->
            reductionPercent(round.sequential.durationMs, round.candidate.durationMs)
        }
        val candidateDurations = rounds.map { it.candidate.durationMs }
        val candidateP95Ms = percentile(candidateDurations, 0.95)
        val orderOk = rounds.all { round ->
            round.sequential.results.map(TaskResult::key) == tasks.map(FixedTask::key) &&
                round.candidate.results.map(TaskResult::key) == tasks.map(FixedTask::key)
        }
        val allSuccessful = rounds.all { round ->
            round.sequential.results.all(TaskResult::successful) &&
                round.candidate.results.all(TaskResult::successful)
        }
        val concurrencyOk = rounds.all { round ->
            round.candidate.maximumActiveTasks in 1..MAXIMUM_CONCURRENCY
        }
        val correctnessGate = differences == 0 && orderOk && allSuccessful && concurrencyOk &&
            activeProcesses.none(Process::isAlive)
        val performanceGate = rounds.indices.all { index ->
            reductionsPercent[index] >= REQUIRED_REDUCTION_PERCENT &&
                reductionsMs[index] >= REQUIRED_REDUCTION_MS
        } && candidateP95Ms <= MAXIMUM_CANDIDATE_P95_MS

        return buildList {
            rounds.forEachIndexed { index, round ->
                add(
                    "status=metric suite=$SUITE round=${index + 1} " +
                        "sequentialMs=${round.sequential.durationMs} candidateMs=${round.candidate.durationMs} " +
                        "reductionMs=${reductionsMs[index]} " +
                        "reductionPercent=${formatPercent(reductionsPercent[index])} " +
                        "maximumActiveTasks=${round.candidate.maximumActiveTasks}",
                )
            }
            add(
                "status=contract suite=$SUITE tasks=${tasks.size} dependencies=${tasks.sumOf { it.dependencies.size }} " +
                    "rounds=$ROUNDS differences=$differences orderOk=$orderOk allSuccessful=$allSuccessful " +
                    "concurrencyOk=$concurrencyOk processClean=${activeProcesses.none(Process::isAlive)} " +
                    "correctnessGate=$correctnessGate adbOverrides=false",
            )
            add(
                "status=complete suite=$SUITE sequentialP50Ms=${percentile(rounds.map { it.sequential.durationMs }, 0.50)} " +
                    "candidateP50Ms=${percentile(candidateDurations, 0.50)} candidateP95Ms=$candidateP95Ms " +
                    "minimumReductionMs=${reductionsMs.minOrNull() ?: 0L} " +
                    "minimumReductionPercent=${formatPercent(reductionsPercent.minOrNull() ?: 0.0)} " +
                    "performanceGate=$performanceGate correctnessGate=$correctnessGate providerSource=production_scheduler",
            )
        }
    }

    private fun fixedTasks(): List<FixedTask> =
        ToolchainPackInstaller.bootstrapResourceSchedulingContracts().map { contract ->
            FixedTask(contract.resourceId, contract.mode, contract.dependencies)
        }

    private fun elapsedMs(startedNs: Long): Long =
        (SystemClock.elapsedRealtimeNanos() - startedNs) / 1_000_000L

    private fun reductionPercent(baseline: Long, candidate: Long): Double =
        if (baseline <= 0L) 0.0 else (baseline - candidate).toDouble() * 100.0 / baseline.toDouble()

    private fun percentile(values: List<Long>, ratio: Double): Long {
        val sorted = values.sorted()
        val index = ceil(sorted.size * ratio).toInt().coerceIn(1, sorted.size) - 1
        return sorted[index]
    }

    private fun formatPercent(value: Double): String = "%.1f".format(Locale.US, value)

    private val activeProcesses = ConcurrentHashMap.newKeySet<Process>()
}
