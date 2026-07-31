package com.kite.app.foundation.runtime

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import com.kite.app.application.runs.RunCommandResult
import com.kite.app.application.runs.RunLifecycleSink
import com.kite.app.application.runs.RunStartRequest
import com.kite.app.recipe.KiteRecipe
import com.kite.app.recipe.KiteRecipeStep
import com.kite.app.resources.KiteResourceInstallRecipes
import com.kite.app.resources.KiteResourceInstallSpec
import com.kite.app.run.CardRunState
import com.kite.app.run.CardRunStatus
import com.kite.app.run.CardRunStore
import com.kite.app.shell.KiteAppGraph
import java.io.File
import java.security.MessageDigest
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import org.json.JSONObject

/** Debug-only 固定 HTTPS 真机探针；不接受外部 URL、路径或摘要参数。 */
class NativeDownloadCapabilityProbeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_BENCHMARK) {
            runCatching {
                context.startService(Intent(context, NativeDownloadCapabilityBenchmarkService::class.java))
            }.onFailure { error ->
                Log.e(
                    BENCHMARK_LOG_TAG,
                    "status=trigger_rejected reason=${safe(error.message ?: error.javaClass.simpleName)}",
                    error,
                )
            }
            return
        }
        if (intent.action != ACTION_PROBE) return
        val pending = goAsync()
        thread(name = "KiteNativeDownloadProbe", isDaemon = true) {
            try {
                runProbe(context.applicationContext).forEach { line -> Log.i(LOG_TAG, line) }
            } catch (error: Throwable) {
                Log.i(
                    LOG_TAG,
                    "status=failed reason=${safe(error.javaClass.simpleName)} " +
                        "message=${safe(error.message.orEmpty())}",
                )
            } finally {
                pending.finish()
            }
        }
    }

    private fun runProbe(context: Context): List<String> {
        val root = File(context.cacheDir, "native-download-probe").apply { mkdirs() }
        val destination = File(root, "rfc20.txt")
        val cancelledDestination = File(root, "cancelled-rfc20.txt")
        check(!destination.exists() || destination.delete()) { "probe_destination_cleanup_failed" }
        check(!cancelledDestination.exists() || cancelledDestination.delete()) {
            "probe_cancel_destination_cleanup_failed"
        }
        val providerContext = AndroidNativeCapabilityContext(
            listOf(NativeCapabilityDestinationRoot(CONTAINER_ROOT, root)),
        )
        val executor = AndroidNativeDownloadExecutor()
        return try {
            val verifiedPlan = providerPlan(providerContext, "$CONTAINER_ROOT/${destination.name}", EXPECTED_SHA256)
            val verified = executor.execute(verifiedPlan)
            check(verified is NativeDownloadExecutionResult.Success) { "verified_download_failed:$verified" }
            check(verified.bytesWritten == EXPECTED_BYTES) { "verified_download_size_mismatch" }
            check(destination.sha256() == EXPECTED_SHA256) { "verified_download_digest_mismatch" }

            val mismatchPlan = providerPlan(
                providerContext,
                "$CONTAINER_ROOT/${destination.name}",
                "0".repeat(64),
            )
            val mismatch = executor.execute(mismatchPlan)
            val mismatchFailure = mismatch as? NativeDownloadExecutionResult.Failure
                ?: error("mismatch_did_not_fail_closed:$mismatch")
            check(
                mismatchFailure == NativeDownloadExecutionResult.Failure("native_download_sha256_mismatch", 1)
            ) { "mismatch_did_not_fail_closed:$mismatch" }
            check(destination.sha256() == EXPECTED_SHA256) { "mismatch_replaced_verified_target" }

            val cancellation = NativeDownloadCancellationSignal()
            val cancelledPlan = providerPlan(
                providerContext,
                "$CONTAINER_ROOT/${cancelledDestination.name}",
                EXPECTED_SHA256,
            )
            val cancelled = executor.execute(
                cancelledPlan,
                cancellation,
                NativeDownloadProgressListener { _, _ -> cancellation.cancel() },
            )
            check(cancelled is NativeDownloadExecutionResult.Cancelled) {
                "cancel_did_not_stop:$cancelled"
            }
            check(!cancelledDestination.exists() && !cancelledPlan.temporaryFile.exists()) {
                "cancel_cleanup_failed"
            }
            listOf(
                "status=started capability=${AndroidNativeDownloadCapabilityProvider.CAPABILITY_ID}",
                "status=verified bytes=${verified.bytesWritten} sha256=${verified.actualSha256} " +
                    "attempts=${verified.attempts} atomic=${verified.atomicMove}",
                "status=mismatch_preserved reason=${mismatchFailure.reason}",
                "status=cancelled cleanup=true attempts=${cancelled.attempts}",
            ) + runRecipeProbe(context) + "status=complete"
        } finally {
            destination.delete()
            cancelledDestination.delete()
            root.listFiles()?.filter { it.name.contains(".kite-download-") }?.forEach(File::delete)
            root.delete()
        }
    }

    /** 使用正式 RunOrchestrator、原生步骤和资源事务 shell，证明两条车道属于同一个 CardRun。 */
    private fun runRecipeProbe(context: Context): List<String> {
        val workspace = KFContainerManager.resolveWorkspaceDirectory(context)
        val cacheRelative = ".kf/cache/resources/$RECIPE_RESOURCE_ID/native-downloads/rfc20.payload"
        val installRelative = ".kf/software/$RECIPE_RESOURCE_ID"
        val cacheFile = File(workspace, cacheRelative)
        val installRoot = File(workspace, installRelative)
        cacheFile.parentFile?.deleteRecursively()
        installRoot.deleteRecursively()
        installRoot.mkdirs()
        File(installRoot, "previous.txt").writeText("previous-generation")

        val containerCache = "/workspace/$cacheRelative"
        val nativeStep = KiteRecipeStep(
            id = "download-rfc20",
            type = KiteRecipe.STEP_NATIVE_CAPABILITY,
            action = AndroidNativeDownloadCapabilityProvider.CAPABILITY_ID,
            params = JSONObject()
                .put(AndroidNativeDownloadCapabilityProvider.PARAM_URL, SOURCE_URL)
                .put(AndroidNativeDownloadCapabilityProvider.PARAM_DESTINATION, containerCache)
                .put(AndroidNativeDownloadCapabilityProvider.PARAM_EXPECTED_SHA256, EXPECTED_SHA256)
                .put(AndroidNativeDownloadCapabilityProvider.PARAM_MAX_BYTES, "65536")
                .put(AndroidNativeDownloadCapabilityProvider.PARAM_MAX_ATTEMPTS, "2")
                .put(AndroidNativeDownloadCapabilityProvider.PARAM_RETRY_DELAY_MS, "100")
                .put(AndroidNativeDownloadCapabilityProvider.PARAM_REPLACE_EXISTING, "true"),
            surfaceMode = KiteRecipe.SURFACE_MODE_PANEL,
            workdir = "/workspace",
            timeoutMs = 60_000L,
        )
        val transaction = KiteResourceInstallRecipes.manifestInstallCommand(
            resourceId = RECIPE_RESOURCE_ID,
            displayName = "Native recipe probe",
            rawCommand = """
                native_cache='$containerCache'
                native_destination="${'$'}install_root/rfc20.txt"
                test -s "${'$'}native_cache"
                mv -f "${'$'}native_cache" "${'$'}native_destination"
            """.trimIndent(),
            managedCommands = emptyList(),
            cleanInstallRoot = true,
            verificationCommand =
                "test \"${'$'}(sha256sum \"${'$'}install_root/rfc20.txt\" | cut -d ' ' -f 1)\" = '$EXPECTED_SHA256'",
            recordOwnership = false,
            protectExistingInstall = true,
        )
        val recipe = KiteResourceInstallRecipes.toRecipe(
            KiteResourceInstallSpec(
                id = RECIPE_RESOURCE_ID,
                name = "Native recipe probe",
                description = "Debug-only fixed resource transaction probe",
                category = "debug",
                steps = listOf(
                    nativeStep,
                    KiteRecipeStep(
                        id = "commit-rfc20",
                        type = KiteRecipe.STEP_SHELL,
                        cmd = transaction,
                        surfaceMode = KiteRecipe.SURFACE_MODE_PANEL,
                        workdir = "/workspace",
                        timeoutMs = 60_000L,
                    ),
                ),
            )
        )
        val instanceId = "native-resource-probe-${System.currentTimeMillis()}"
        val lanes = Collections.synchronizedList(mutableListOf<String>())
        val finalState = AtomicReference<CardRunState>()
        val completed = CountDownLatch(1)
        val graph = KiteAppGraph.from(context)
        val sink = RunLifecycleSink { event ->
            if (event.state.instanceId != instanceId) return@RunLifecycleSink
            event.state.runtimeLane?.takeIf(String::isNotBlank)?.let(lanes::add)
            if (event.state.status in TERMINAL_STATUSES) {
                finalState.set(event.state)
                completed.countDown()
            }
        }
        graph.runLifecycleEventHub.register(sink)
        val startedAt = SystemClock.elapsedRealtime()
        try {
            val start = graph.runOrchestrator.start(
                RunStartRequest(
                    recipe = recipe,
                    instanceId = instanceId,
                    ownerKind = CardRunState.OWNER_KIND_RESOURCE,
                    stepId = RECIPE_RESOURCE_ID,
                )
            )
            check(start is RunCommandResult.Accepted) { "recipe_start_rejected:$start" }
            check(completed.await(120, TimeUnit.SECONDS)) { "recipe_probe_timeout" }
            val state = checkNotNull(finalState.get()) { "recipe_final_state_missing" }
            check(state.status == CardRunStatus.Completed) {
                "recipe_failed:${state.lastError ?: state.lastMeaningfulOutput}"
            }
            check("android_native" in lanes) { "recipe_native_lane_missing:$lanes" }
            check("proot_shell" in lanes) { "recipe_proot_lane_missing:$lanes" }
            check(state.terminalSessionId == null) { "recipe_created_fake_terminal" }
            val installed = File(installRoot, "rfc20.txt")
            check(installed.isFile && installed.length() == EXPECTED_BYTES) { "recipe_payload_missing" }
            check(installed.sha256() == EXPECTED_SHA256) { "recipe_payload_digest_mismatch" }
            check(!File(installRoot, "previous.txt").exists()) { "recipe_old_generation_not_replaced" }
            return listOf(
                "status=recipe_complete elapsed_ms=${SystemClock.elapsedRealtime() - startedAt} " +
                    "lanes=${lanes.distinct().joinToString(",")} transaction=true",
            )
        } finally {
            graph.runLifecycleEventHub.unregister(sink)
            CardRunStore.get(instanceId)?.takeIf { it.status !in TERMINAL_STATUSES }?.let {
                graph.runOrchestrator.stop(instanceId)
                val stopDeadline = SystemClock.elapsedRealtime() + 5_000L
                while (
                    CardRunStore.get(instanceId)?.status?.let { it !in TERMINAL_STATUSES } == true &&
                    SystemClock.elapsedRealtime() < stopDeadline
                ) {
                    Thread.sleep(25L)
                }
            }
            CardRunStore.removeRun(instanceId)
            cacheFile.parentFile?.deleteRecursively()
            installRoot.deleteRecursively()
            File(workspace, "$installRelative.kite-backup").deleteRecursively()
            File(workspace, "$installRelative.kite-update-lock").deleteRecursively()
        }
    }

    private fun providerPlan(
        context: AndroidNativeCapabilityContext,
        destination: String,
        expectedSha256: String,
    ): AndroidNativeDownloadPlan {
        val decision = AndroidNativeDownloadCapabilityProvider.prepare(
            context,
            RuntimeExecutionRequest(
                payload = RuntimeExecutionPayload.NativeCapability(
                    capabilityId = AndroidNativeDownloadCapabilityProvider.CAPABILITY_ID,
                    parameters = mapOf(
                        AndroidNativeDownloadCapabilityProvider.PARAM_URL to SOURCE_URL,
                        AndroidNativeDownloadCapabilityProvider.PARAM_DESTINATION to destination,
                        AndroidNativeDownloadCapabilityProvider.PARAM_EXPECTED_SHA256 to expectedSha256,
                        AndroidNativeDownloadCapabilityProvider.PARAM_MAX_BYTES to "65536",
                        AndroidNativeDownloadCapabilityProvider.PARAM_MAX_ATTEMPTS to "2",
                        AndroidNativeDownloadCapabilityProvider.PARAM_RETRY_DELAY_MS to "100",
                        AndroidNativeDownloadCapabilityProvider.PARAM_REPLACE_EXISTING to "true",
                    ),
                ),
                requirements = setOf(RuntimeExecutionRequirement.ANDROID_NATIVE),
            ),
        )
        return (decision as? RuntimeProviderDecision.Ready)?.plan
            ?: error("native_provider_not_ready:${decision.reason}")
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

    private fun safe(value: String): String = value.take(160).map { character ->
        if (character.isLetterOrDigit() || character in "._-:=") character else '_'
    }.joinToString("")

    internal companion object {
        const val ACTION_PROBE = "com.kite.app.debug.NATIVE_DOWNLOAD_CAPABILITY_PROBE"
        const val ACTION_BENCHMARK = "com.kite.app.debug.NATIVE_DOWNLOAD_CAPABILITY_BENCHMARK"
        const val LOG_TAG = "[KFShell]NativeDownload"
        const val BENCHMARK_LOG_TAG = "[KFShell]NativeDownloadBenchmark"
        const val CONTAINER_ROOT = "/probe"
        const val RECIPE_RESOURCE_ID = "kite.debug.native-recipe-probe"
        const val SOURCE_URL = "https://www.rfc-editor.org/rfc/rfc20.txt"
        const val EXPECTED_BYTES = 18_504L
        const val EXPECTED_SHA256 = "714d11bfcbc001f98cd8a92291a19e3f670c2236ad02771092e0eea826acd13a"
        val TERMINAL_STATUSES = setOf(
            CardRunStatus.Completed,
            CardRunStatus.Failed,
            CardRunStatus.Stopped,
            CardRunStatus.BridgeUnavailable,
        )
    }
}
