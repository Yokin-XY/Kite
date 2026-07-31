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
import java.io.FileOutputStream
import java.security.MessageDigest
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
import org.json.JSONObject

/** Debug-only 固定原生文件对照；不接受外部路径、大小、动作或轮数。 */
class NativeFileCapabilityBenchmarkService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!running.compareAndSet(false, true)) return START_NOT_STICKY
        scope.launch {
            try {
                NativeFileCapabilityBenchmark.run(applicationContext).forEach { report ->
                    Log.i(NativeDownloadCapabilityProbeReceiver.FILE_BENCHMARK_LOG_TAG, report)
                }
            } catch (error: Throwable) {
                Log.e(
                    NativeDownloadCapabilityProbeReceiver.FILE_BENCHMARK_LOG_TAG,
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

private object NativeFileCapabilityBenchmark {
    private const val RESOURCE_ID = "kite.debug.native-file-benchmark"
    private const val CONTAINER_ROOT = "/workspace/.kf/cache/resources/$RESOURCE_ID"
    private const val BYTES = 16L * 1024L * 1024L
    private const val ROUNDS = 3
    private const val TIMEOUT_MS = 60_000L

    fun run(context: Context): List<String> {
        val workspace = KFContainerManager.resolveWorkspaceDirectory(context)
        val root = File(workspace, ".kf/cache/resources/$RESOURCE_ID")
        root.deleteRecursively()
        root.mkdirs()
        val source = File(root, "source.bin")
        writeFixture(source, BYTES)
        val digest = source.sha256()
        val providerContext = AndroidNativeFileCapabilityContext(
            listOf(
                NativeFileCapabilityRoot(CONTAINER_ROOT, root, NativeFilePermission.entries.toSet()),
            )
        )
        return try {
            val nativeTimes = mutableListOf<Long>()
            val prootTimes = mutableListOf<Long>()
            repeat(ROUNDS) { round ->
                if (round % 2 == 0) {
                    nativeTimes += runNativeCopy(providerContext, root, round, digest)
                    prootTimes += runProotCopy(context, workspace, root, round, digest)
                } else {
                    prootTimes += runProotCopy(context, workspace, root, round, digest)
                    nativeTimes += runNativeCopy(providerContext, root, round, digest)
                }
            }
            val unauthorized = AndroidNativeFileCapabilityProvider.prepare(
                AndroidNativeFileCapabilityContext(
                    listOf(
                        NativeFileCapabilityRoot(
                            "/workspace",
                            workspace,
                            setOf(NativeFilePermission.READ, NativeFilePermission.CREATE, NativeFilePermission.REPLACE),
                        )
                    )
                ),
                request(
                    AndroidNativeFileCapabilityProvider.CAPABILITY_DELETE_FILE,
                    mapOf(AndroidNativeFileCapabilityProvider.PARAM_TARGET to "/workspace/${root.name}/source.bin"),
                ),
            )
            check(
                unauthorized is RuntimeProviderDecision.Blocked &&
                    unauthorized.reason == "native_file_delete_not_authorized"
            ) { "workspace_delete_was_authorized:$unauthorized" }

            listOf(
                "status=comparison bytes=$BYTES rounds=$ROUNDS native_ms=${nativeTimes.joinToString(",")} " +
                    "proot_ms=${prootTimes.joinToString(",")} native_p50_ms=${p50(nativeTimes)} " +
                    "proot_p50_ms=${p50(prootTimes)} delta_pct=${formatDelta(nativeTimes, prootTimes)}",
                "status=permission_gate workspace_delete=blocked reason=native_file_delete_not_authorized",
            ) + runRecipeProbe(context, root, digest) + "status=complete cleanup=true"
        } finally {
            root.deleteRecursively()
        }
    }

    private fun runNativeCopy(
        providerContext: AndroidNativeFileCapabilityContext,
        root: File,
        round: Int,
        digest: String,
    ): Long {
        val destination = File(root, "native-$round.bin")
        val decision = AndroidNativeFileCapabilityProvider.prepare(
            providerContext,
            request(
                AndroidNativeFileCapabilityProvider.CAPABILITY_COPY_FILE,
                mapOf(
                    AndroidNativeFileCapabilityProvider.PARAM_SOURCE to "$CONTAINER_ROOT/source.bin",
                    AndroidNativeFileCapabilityProvider.PARAM_DESTINATION to "$CONTAINER_ROOT/${destination.name}",
                    AndroidNativeFileCapabilityProvider.PARAM_MAX_BYTES to BYTES.toString(),
                    AndroidNativeFileCapabilityProvider.PARAM_REPLACE_EXISTING to "true",
                ),
            ),
        )
        val plan = (decision as? RuntimeProviderDecision.Ready)?.plan
            ?: error("native_copy_not_ready:${decision.reason}")
        val started = SystemClock.elapsedRealtime()
        val result = AndroidNativeFileExecutor().execute(plan)
        val elapsed = SystemClock.elapsedRealtime() - started
        check(result == NativeFileExecutionResult.Success(plan.capabilityId, BYTES)) {
            "native_copy_failed:$result"
        }
        check(destination.length() == BYTES && destination.sha256() == digest) {
            "native_copy_content_mismatch"
        }
        destination.delete()
        return elapsed
    }

    private fun runProotCopy(
        context: Context,
        workspace: File,
        root: File,
        round: Int,
        digest: String,
    ): Long {
        val destination = File(root, "proot-$round.bin")
        val containerDestination = "$CONTAINER_ROOT/${destination.name}"
        val command =
            "set -e; cp '$CONTAINER_ROOT/source.bin' '$containerDestination'; " +
                "sync -f '$containerDestination'"
        val execution = executeProot(context, listOf("/bin/bash", "-lc", command))
        check(execution.exitCode == 0) { "proot_copy_failed:${execution.exitCode}:${execution.stderr}" }
        check(destination.length() == BYTES && destination.sha256() == digest) {
            "proot_copy_content_mismatch"
        }
        destination.delete()
        check(workspace.isDirectory)
        return execution.elapsedMs
    }

    private fun runRecipeProbe(context: Context, root: File, digest: String): List<String> {
        val copied = File(root, "recipe-copied.bin")
        val moved = File(root, "recipe-moved.bin")
        copied.delete()
        moved.delete()
        val recipe = KiteRecipe(
            id = RESOURCE_ID,
            name = "Native file recipe probe",
            description = "Debug-only fixed file capability probe",
            type = KiteRecipe.TYPE_TEMPLATE,
            defaultUrl = "",
            shortcut = false,
            execution = KiteExecution.steps(
                listOf(
                    fileStep(
                        "copy",
                        AndroidNativeFileCapabilityProvider.CAPABILITY_COPY_FILE,
                        mapOf(
                            AndroidNativeFileCapabilityProvider.PARAM_SOURCE to "$CONTAINER_ROOT/source.bin",
                            AndroidNativeFileCapabilityProvider.PARAM_DESTINATION to "$CONTAINER_ROOT/${copied.name}",
                            AndroidNativeFileCapabilityProvider.PARAM_MAX_BYTES to BYTES.toString(),
                            AndroidNativeFileCapabilityProvider.PARAM_REPLACE_EXISTING to "true",
                        ),
                    ),
                    fileStep(
                        "move",
                        AndroidNativeFileCapabilityProvider.CAPABILITY_MOVE_FILE,
                        mapOf(
                            AndroidNativeFileCapabilityProvider.PARAM_SOURCE to "$CONTAINER_ROOT/${copied.name}",
                            AndroidNativeFileCapabilityProvider.PARAM_DESTINATION to "$CONTAINER_ROOT/${moved.name}",
                        ),
                    ),
                    fileStep(
                        "delete",
                        AndroidNativeFileCapabilityProvider.CAPABILITY_DELETE_FILE,
                        mapOf(AndroidNativeFileCapabilityProvider.PARAM_TARGET to "$CONTAINER_ROOT/${moved.name}"),
                    ),
                )
            ),
        )
        val instanceId = "native-file-probe-${System.currentTimeMillis()}"
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
            check(completed.await(120, TimeUnit.SECONDS)) { "recipe_timeout" }
            val state = checkNotNull(finalState.get()) { "recipe_state_missing" }
            check(state.status == CardRunStatus.Completed) {
                "recipe_failed:${state.lastError ?: state.lastMeaningfulOutput}"
            }
            check(lanes.distinct() == listOf("android_native")) { "recipe_lane_mismatch:$lanes" }
            check(state.runId == null && state.terminalSessionId == null) { "recipe_created_process_surface" }
            check(!copied.exists() && !moved.exists()) { "recipe_delete_not_applied" }
            check(File(root, "source.bin").sha256() == digest) { "recipe_source_changed" }
            return listOf(
                "status=recipe_complete elapsed_ms=${SystemClock.elapsedRealtime() - started} " +
                    "steps=copy,move,delete lane=android_native terminal=false",
            )
        } finally {
            graph.runLifecycleEventHub.unregister(sink)
            CardRunStore.get(instanceId)?.takeIf { it.status !in NativeDownloadCapabilityProbeReceiver.TERMINAL_STATUSES }
                ?.let { graph.runOrchestrator.stop(instanceId) }
            CardRunStore.removeRun(instanceId)
            copied.delete()
            moved.delete()
        }
    }

    private fun fileStep(id: String, action: String, parameters: Map<String, String>) = KiteRecipeStep(
        id = id,
        type = KiteRecipe.STEP_NATIVE_CAPABILITY,
        action = action,
        params = JSONObject(parameters),
        surfaceMode = KiteRecipe.SURFACE_MODE_PANEL,
        workdir = "/workspace",
        timeoutMs = 60_000L,
    )

    private fun request(capabilityId: String, parameters: Map<String, String>) = RuntimeExecutionRequest(
        payload = RuntimeExecutionPayload.NativeCapability(capabilityId, parameters),
        requirements = setOf(RuntimeExecutionRequirement.ANDROID_NATIVE),
    )

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

    private fun writeFixture(target: File, bytes: Long) {
        target.parentFile?.mkdirs()
        val buffer = ByteArray(64 * 1024) { index -> (index % 251).toByte() }
        var remaining = bytes
        FileOutputStream(target).use { output ->
            while (remaining > 0L) {
                val count = minOf(buffer.size.toLong(), remaining).toInt()
                output.write(buffer, 0, count)
                remaining -= count
            }
            output.fd.sync()
        }
    }

    private fun File.sha256(): String = inputStream().use { input ->
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            if (count > 0) digest.update(buffer, 0, count)
        }
        digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }

    private fun p50(values: List<Long>): Long = values.sorted()[values.size / 2]

    private fun formatDelta(nativeTimes: List<Long>, prootTimes: List<Long>): String {
        val native = p50(nativeTimes).toDouble()
        val proot = p50(prootTimes).toDouble()
        return String.format(Locale.US, "%.1f", (proot - native) / proot * 100.0)
    }

    private data class ProotExecution(val elapsedMs: Long, val exitCode: Int, val stderr: String)
}
