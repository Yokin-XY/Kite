package com.kite.app.foundation.runtime

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import com.kite.app.application.runs.RunCommandResult
import com.kite.app.application.runs.RunLifecycleSink
import com.kite.app.application.runs.RunStartRequest
import com.kite.app.foundation.workspace.WorkSurfaceRuntimeBridge
import com.kite.app.recipe.KiteExecution
import com.kite.app.recipe.KiteRecipe
import com.kite.app.recipe.KiteRecipeStep
import com.kite.app.run.CardRunState
import com.kite.app.run.CardRunStatus
import com.kite.app.run.CardRunStore
import com.kite.app.shell.KiteAppGraph
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Collections
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream
import org.json.JSONObject

/** Debug-only 固定 ZIP 安全与性能门；不接受外部压缩包、路径、上限或轮数。 */
class NativeArchiveCapabilityBenchmarkService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!running.compareAndSet(false, true)) return START_NOT_STICKY
        scope.launch {
            try {
                NativeArchiveCapabilityBenchmark.run(applicationContext).forEach { report ->
                    Log.i(NativeDownloadCapabilityProbeReceiver.ARCHIVE_BENCHMARK_LOG_TAG, report)
                }
            } catch (error: Throwable) {
                Log.e(
                    NativeDownloadCapabilityProbeReceiver.ARCHIVE_BENCHMARK_LOG_TAG,
                    "status=failed reason=${safe(error.message ?: error.javaClass.simpleName)}",
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

        fun safe(value: String): String = value.take(160).map { character ->
            if (character.isLetterOrDigit() || character in "-_.:=/%") character else '_'
        }.joinToString("")
    }
}

private object NativeArchiveCapabilityBenchmark {
    private const val RESOURCE_ID = "kite.debug.native-archive-benchmark"
    private const val CONTAINER_ROOT = "/workspace/.kf/cache/resources/$RESOURCE_ID"
    private const val ENTRY_COUNT = 128
    private const val ENTRY_BYTES = 128 * 1024
    private const val TOTAL_BYTES = ENTRY_COUNT.toLong() * ENTRY_BYTES
    private const val ROUNDS = 3
    private const val TIMEOUT_MS = 120_000L

    fun run(context: Context): List<String> {
        val workspace = KFContainerManager.resolveWorkspaceDirectory(context)
        val root = File(workspace, ".kf/cache/resources/$RESOURCE_ID")
        root.deleteRecursively()
        root.mkdirs()
        val archive = File(root, "fixture.zip")
        writeFixtureArchive(archive)
        val providerContext = AndroidNativeFileCapabilityContext(
            listOf(NativeFileCapabilityRoot(CONTAINER_ROOT, root, NativeFilePermission.entries.toSet()))
        )
        return try {
            check(RustArchiveBridge.isAvailable) { "rust_archive_library_unavailable" }
            val kotlinTimes = mutableListOf<Long>()
            val rustTimes = mutableListOf<Long>()
            val prootTimes = mutableListOf<Long>()
            repeat(ROUNDS) { round ->
                when (round % 3) {
                    0 -> {
                        kotlinTimes += runNative(providerContext, root, round)
                        rustTimes += runRust(providerContext, root, round)
                        prootTimes += runProot(context, root, round)
                    }
                    1 -> {
                        prootTimes += runProot(context, root, round)
                        kotlinTimes += runNative(providerContext, root, round)
                        rustTimes += runRust(providerContext, root, round)
                    }
                    else -> {
                        rustTimes += runRust(providerContext, root, round)
                        prootTimes += runProot(context, root, round)
                        kotlinTimes += runNative(providerContext, root, round)
                    }
                }
            }
            val kotlinMalicious = runMalicious(providerContext, root, rust = false)
            val rustMalicious = runMalicious(providerContext, root, rust = true)
            val kotlinCancelled = runCancelled(providerContext, root, rust = false)
            val rustCancelled = runCancelled(providerContext, root, rust = true)
            listOf(
                "status=comparison bytes=$TOTAL_BYTES entries=$ENTRY_COUNT rounds=$ROUNDS " +
                    "kotlin_ms=${kotlinTimes.joinToString(",")} rust_ms=${rustTimes.joinToString(",")} " +
                    "proot_ms=${prootTimes.joinToString(",")} kotlin_p50_ms=${p50(kotlinTimes)} " +
                    "rust_p50_ms=${p50(rustTimes)} proot_p50_ms=${p50(prootTimes)} " +
                    "rust_vs_kotlin_pct=${formatDelta(rustTimes, kotlinTimes)} " +
                    "rust_vs_proot_pct=${formatDelta(rustTimes, prootTimes)}",
                "status=malicious kotlin=$kotlinMalicious rust=$rustMalicious destination=false escape=false",
                "status=cancelled kotlin=$kotlinCancelled rust=$rustCancelled cleanup=true",
                "status=complete cleanup=true",
            )
        } finally {
            root.deleteRecursively()
        }
    }

    private fun runNative(context: AndroidNativeFileCapabilityContext, root: File, round: Int): Long {
        val destination = File(root, "native-$round")
        val plan = plan(context, destination.name)
        val started = SystemClock.elapsedRealtime()
        val result = AndroidNativeArchiveExecutor().execute(plan)
        val elapsed = SystemClock.elapsedRealtime() - started
        check(result == NativeArchiveExecutionResult.Success(ENTRY_COUNT, TOTAL_BYTES)) {
            "native_extract_failed:$result"
        }
        verifyTree(destination)
        destination.deleteRecursively()
        return elapsed
    }

    private fun runRust(context: AndroidNativeFileCapabilityContext, root: File, round: Int): Long {
        val destination = File(root, "rust-$round")
        val plan = plan(context, destination.name)
        val started = SystemClock.elapsedRealtime()
        val result = RustArchiveBridge.execute(plan)
        val elapsed = SystemClock.elapsedRealtime() - started
        check(result == NativeArchiveExecutionResult.Success(ENTRY_COUNT, TOTAL_BYTES)) {
            "rust_extract_failed:$result"
        }
        verifyTree(destination)
        destination.deleteRecursively()
        return elapsed
    }

    private fun runProot(context: Context, root: File, round: Int): Long {
        val destination = File(root, "proot-$round")
        val containerDestination = "$CONTAINER_ROOT/${destination.name}"
        val command =
            "set -e; stage='${containerDestination}.part'; rm -rf \"${'$'}stage\" '$containerDestination'; " +
                "mkdir -p \"${'$'}stage\"; unzip -q '$CONTAINER_ROOT/fixture.zip' -d \"${'$'}stage\"; " +
                "mv \"${'$'}stage\" '$containerDestination'"
        val execution = executeProot(context, listOf("/bin/bash", "-lc", command))
        check(execution.exitCode == 0) { "proot_extract_failed:${execution.exitCode}:${execution.stderr}" }
        verifyTree(destination)
        destination.deleteRecursively()
        return execution.elapsedMs
    }

    private fun runMalicious(
        context: AndroidNativeFileCapabilityContext,
        root: File,
        rust: Boolean,
    ): String {
        val malicious = File(root, "malicious.zip")
        ZipArchiveOutputStream(malicious).use { output ->
            output.putArchiveEntry(ZipArchiveEntry("../escape.txt"))
            output.write("escape".toByteArray())
            output.closeArchiveEntry()
            output.finish()
        }
        val destinationName = if (rust) "rust-malicious-out" else "kotlin-malicious-out"
        val archivePlan = plan(context, destinationName, "malicious.zip")
        val result = if (rust) {
            RustArchiveBridge.execute(archivePlan)
        } else {
            AndroidNativeArchiveExecutor().execute(archivePlan)
        }
        check(result == NativeArchiveExecutionResult.Failure("native_archive_path_invalid")) {
            "malicious_archive_not_blocked:$result"
        }
        check(!File(root, destinationName).exists() && !File(root, "escape.txt").exists()) {
            "malicious_archive_escaped"
        }
        malicious.delete()
        return "native_archive_path_invalid"
    }

    private fun runCancelled(
        context: AndroidNativeFileCapabilityContext,
        root: File,
        rust: Boolean,
    ): String {
        val destination = File(root, if (rust) "rust-cancelled-out" else "kotlin-cancelled-out")
        val archivePlan = plan(context, destination.name)
        val result = if (rust) {
            RustArchiveBridge.execute(archivePlan, cancelAfterBytes = 1L)
        } else {
            val cancellation = NativeFileCancellationSignal()
            AndroidNativeArchiveExecutor().execute(
                archivePlan,
                cancellation,
                NativeArchiveProgressListener { _, bytes -> if (bytes > 0L) cancellation.cancel() },
            )
        }
        check(result is NativeArchiveExecutionResult.Cancelled) { "archive_cancel_failed:$result" }
        check(!destination.exists() && root.listFiles().orEmpty().none { it.name.contains("cancelled-out.kite-extract") }) {
            "archive_cancel_cleanup_failed"
        }
        return "native_archive_cancelled"
    }

    private fun runRecipeProbe(context: Context, root: File): List<String> {
        val destination = File(root, "recipe-out")
        destination.deleteRecursively()
        val step = KiteRecipeStep(
            id = "extract",
            type = KiteRecipe.STEP_NATIVE_CAPABILITY,
            action = AndroidNativeArchiveCapabilityProvider.CAPABILITY_ID,
            params = JSONObject(parameters("recipe-out")),
            surfaceMode = KiteRecipe.SURFACE_MODE_PANEL,
            workdir = "/workspace",
            timeoutMs = TIMEOUT_MS,
        )
        val recipe = KiteRecipe(
            id = RESOURCE_ID,
            name = "Native archive recipe probe",
            description = "Debug-only fixed archive probe",
            type = KiteRecipe.TYPE_TEMPLATE,
            defaultUrl = "",
            shortcut = false,
            execution = KiteExecution.steps(listOf(step)),
        )
        val instanceId = "native-archive-probe-${System.currentTimeMillis()}"
        val lanes = Collections.synchronizedList(mutableListOf<String>())
        val finalState = AtomicReference<CardRunState>()
        val completed = CountDownLatch(1)
        val graph = KiteAppGraph.from(context)
        val sink = RunLifecycleSink { event ->
            if (event.state.instanceId != instanceId) return@RunLifecycleSink
            event.state.runtimeLane?.takeIf(String::isNotBlank)?.let(lanes::add)
            if (event.state.status in NativeDownloadCapabilityProbeReceiver.TERMINAL_STATUSES) {
                finalState.set(event.state)
                completed.countDown()
            }
        }
        graph.runLifecycleEventHub.register(sink)
        val started = SystemClock.elapsedRealtime()
        try {
            val result = graph.runOrchestrator.start(
                RunStartRequest(
                    recipe = recipe,
                    instanceId = instanceId,
                    ownerKind = CardRunState.OWNER_KIND_RESOURCE,
                    stepId = RESOURCE_ID,
                )
            )
            check(result is RunCommandResult.Accepted) { "recipe_start_rejected:$result" }
            check(completed.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)) { "recipe_timeout" }
            val state = checkNotNull(finalState.get()) { "recipe_state_missing" }
            check(state.status == CardRunStatus.Completed) {
                "recipe_failed:${state.lastError ?: state.lastMeaningfulOutput}"
            }
            check(lanes.distinct() == listOf("android_native")) { "recipe_lane_mismatch:$lanes" }
            check(state.runId == null && state.terminalSessionId == null) { "recipe_created_process_surface" }
            verifyTree(destination)
            return listOf(
                "status=recipe_complete elapsed_ms=${SystemClock.elapsedRealtime() - started} " +
                    "entries=$ENTRY_COUNT bytes=$TOTAL_BYTES lane=android_native terminal=false",
            )
        } finally {
            graph.runLifecycleEventHub.unregister(sink)
            CardRunStore.get(instanceId)?.takeIf { it.status !in NativeDownloadCapabilityProbeReceiver.TERMINAL_STATUSES }
                ?.let { graph.runOrchestrator.stop(instanceId) }
            CardRunStore.removeRun(instanceId)
            destination.deleteRecursively()
        }
    }

    private fun plan(
        context: AndroidNativeFileCapabilityContext,
        destinationName: String,
        sourceName: String = "fixture.zip",
    ): AndroidNativeArchivePlan {
        val decision = AndroidNativeArchiveCapabilityProvider.prepare(
            context,
            RuntimeExecutionRequest(
                payload = RuntimeExecutionPayload.NativeCapability(
                    AndroidNativeArchiveCapabilityProvider.CAPABILITY_ID,
                    parameters(destinationName, sourceName),
                ),
                requirements = setOf(RuntimeExecutionRequirement.ANDROID_NATIVE),
            ),
        )
        return (decision as? RuntimeProviderDecision.Ready)?.plan
            ?: error("archive_provider_not_ready:${decision.reason}")
    }

    private fun parameters(destinationName: String, sourceName: String = "fixture.zip") = mapOf(
        AndroidNativeArchiveCapabilityProvider.PARAM_SOURCE to "$CONTAINER_ROOT/$sourceName",
        AndroidNativeArchiveCapabilityProvider.PARAM_DESTINATION to "$CONTAINER_ROOT/$destinationName",
        AndroidNativeArchiveCapabilityProvider.PARAM_FORMAT to "zip",
        AndroidNativeArchiveCapabilityProvider.PARAM_MAX_ARCHIVE_BYTES to (32L * 1024L * 1024L).toString(),
        AndroidNativeArchiveCapabilityProvider.PARAM_MAX_ENTRIES to "256",
        AndroidNativeArchiveCapabilityProvider.PARAM_MAX_TOTAL_BYTES to (32L * 1024L * 1024L).toString(),
        AndroidNativeArchiveCapabilityProvider.PARAM_MAX_FILE_BYTES to (256L * 1024L).toString(),
        AndroidNativeArchiveCapabilityProvider.PARAM_MAX_DEPTH to "8",
        AndroidNativeArchiveCapabilityProvider.PARAM_MAX_EXPANSION_RATIO to "20",
    )

    private fun writeFixtureArchive(target: File) {
        ZipArchiveOutputStream(target).use { output ->
            output.setLevel(1)
            repeat(ENTRY_COUNT) { index ->
                output.putArchiveEntry(ZipArchiveEntry("group-${index % 8}/entry-$index.bin"))
                output.write(fixtureBytes(index))
                output.closeArchiveEntry()
            }
            output.finish()
        }
    }

    private fun fixtureBytes(seed: Int): ByteArray {
        var state = seed + 1
        return ByteArray(ENTRY_BYTES) {
            state = state * 1_664_525 + 1_013_904_223
            (state ushr 24).toByte()
        }
    }

    private fun verifyTree(root: File) {
        val files = root.walkTopDown().filter(File::isFile).toList()
        check(files.size == ENTRY_COUNT) { "archive_file_count_${files.size}" }
        check(files.sumOf(File::length) == TOTAL_BYTES) { "archive_total_bytes_mismatch" }
        check(File(root, "group-0/entry-0.bin").readBytes().contentEquals(fixtureBytes(0))) {
            "archive_sample_content_mismatch"
        }
    }

    private fun executeProot(context: Context, argv: List<String>): ProotExecution {
        val started = SystemClock.elapsedRealtime()
        val config = WorkSurfaceRuntimeBridge.buildArgvExecConfig(context = context, argv = argv)
        val process = ProcessBuilder(config.command)
            .redirectErrorStream(false)
            .apply { environment().putAll(config.env) }
            .start()
        val stdout = ByteArrayOutputStream()
        val stderr = ByteArrayOutputStream()
        val outReader = thread(start = true, isDaemon = true) { process.inputStream.use { it.copyTo(stdout) } }
        val errReader = thread(start = true, isDaemon = true) { process.errorStream.use { it.copyTo(stderr) } }
        val finished = process.waitFor(TIMEOUT_MS, TimeUnit.MILLISECONDS)
        if (!finished) process.destroyForcibly()
        outReader.join(1_000L)
        errReader.join(1_000L)
        return ProotExecution(
            SystemClock.elapsedRealtime() - started,
            if (finished) process.exitValue() else -1,
            stderr.toString(Charsets.UTF_8.name()),
        )
    }

    private fun p50(values: List<Long>): Long = values.sorted()[values.size / 2]

    private fun formatDelta(nativeTimes: List<Long>, prootTimes: List<Long>): String {
        val native = p50(nativeTimes).toDouble()
        val proot = p50(prootTimes).toDouble()
        return String.format(Locale.US, "%.1f", (proot - native) / proot * 100.0)
    }

    private data class ProotExecution(val elapsedMs: Long, val exitCode: Int, val stderr: String)
}
