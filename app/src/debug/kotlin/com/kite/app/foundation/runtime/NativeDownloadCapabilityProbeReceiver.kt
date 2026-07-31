package com.kite.app.foundation.runtime

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import java.io.File
import java.security.MessageDigest
import kotlin.concurrent.thread

/** Debug-only 固定 HTTPS 真机探针；不接受外部 URL、路径或摘要参数。 */
class NativeDownloadCapabilityProbeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_PROBE) return
        val pending = goAsync()
        thread(name = "KiteNativeDownloadProbe", isDaemon = true) {
            try {
                runProbe(context.applicationContext).forEach { line -> Log.i(LOG_TAG, line) }
            } catch (error: Throwable) {
                Log.i(LOG_TAG, "status=failed reason=${safe(error.javaClass.simpleName)}")
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
                "status=complete",
            )
        } finally {
            destination.delete()
            cancelledDestination.delete()
            root.listFiles()?.filter { it.name.contains(".kite-download-") }?.forEach(File::delete)
            root.delete()
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

    private companion object {
        const val ACTION_PROBE = "com.kite.app.debug.NATIVE_DOWNLOAD_CAPABILITY_PROBE"
        const val LOG_TAG = "[KFShell]NativeDownload"
        const val CONTAINER_ROOT = "/probe"
        const val SOURCE_URL = "https://www.rfc-editor.org/rfc/rfc20.txt"
        const val EXPECTED_BYTES = 18_504L
        const val EXPECTED_SHA256 = "714d11bfcbc001f98cd8a92291a19e3f670c2236ad02771092e0eea826acd13a"
    }
}
