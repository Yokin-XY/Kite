package com.kite.app.platform.runs

import com.kite.app.application.runs.RecipeExecutionEvent
import com.kite.app.application.runs.RecipeStepExecutionRequest
import com.kite.app.foundation.runtime.AndroidNativeCapabilityContext
import com.kite.app.foundation.runtime.AndroidNativeDownloadCapabilityProvider
import com.kite.app.foundation.runtime.AndroidNativeDownloadExecutor
import com.kite.app.foundation.runtime.AndroidNativeFileCapabilityContext
import com.kite.app.foundation.runtime.AndroidNativeFileCapabilityProvider
import com.kite.app.foundation.runtime.AndroidNativeFilePlan
import com.kite.app.foundation.runtime.NativeCapabilityDestinationRoot
import com.kite.app.foundation.runtime.NativeDownloadConnection
import com.kite.app.foundation.runtime.NativeDownloadConnectionFactory
import com.kite.app.foundation.runtime.NativeFileCancellation
import com.kite.app.foundation.runtime.NativeFileCapabilityRoot
import com.kite.app.foundation.runtime.NativeFileExecutionResult
import com.kite.app.foundation.runtime.NativeFilePermission
import com.kite.app.foundation.runtime.NativeFileProgressListener
import com.kite.app.recipe.KiteExecution
import com.kite.app.recipe.KiteRecipe
import com.kite.app.recipe.KiteRecipeStep
import com.kite.app.run.CardRunState
import com.kite.app.run.CardRunStatus
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.URL
import java.nio.file.Files
import java.security.MessageDigest
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class AndroidNativeCapabilityRecipeRuntimeTest {
    @Test
    fun `native step publishes lane and result into the same run without terminal binding`() {
        val root = Files.createTempDirectory("kite-native-recipe").toFile()
        val bytes = "recipe-native-download".toByteArray()
        val runtime = runtime(root, NativeDownloadConnectionFactory { _, _, _ -> Response(bytes) })
        val events = Collections.synchronizedList(mutableListOf<RecipeExecutionEvent>())
        val completed = CountDownLatch(1)

        runtime.execute(request(root, bytes.sha256())) { event ->
            events += event
            if (event is RecipeExecutionEvent.Completed || event is RecipeExecutionEvent.Failed) {
                completed.countDown()
            }
        }

        assertTrue(completed.await(5, TimeUnit.SECONDS))
        val result = events.filterIsInstance<RecipeExecutionEvent.Completed>().single()
        assertEquals("android_native", result.mutation.runtimeLane)
        assertEquals("native_download_sha256_ready", result.mutation.runtimeFallbackReason)
        assertEquals(null, result.mutation.runId)
        assertEquals(null, result.mutation.terminalSessionId)
        assertTrue(result.mutation.shellReportText.orEmpty().contains(bytes.sha256()))
        assertEquals(bytes.toList(), File(root, "payload.bin").readBytes().toList())
        assertFalse(runtime.owns("native-recipe-instance", 100L))
    }

    @Test
    fun `stop closes a blocking native download and waits for temporary cleanup`() {
        val root = Files.createTempDirectory("kite-native-recipe-stop").toFile()
        val connection = BlockingConnection()
        val runtime = runtime(root, NativeDownloadConnectionFactory { _, _, _ -> connection })
        val failed = CountDownLatch(1)
        val stopCompleted = CountDownLatch(1)
        val stopped = AtomicReference<Boolean>()
        runtime.execute(request(root, "")) { event ->
            if (event is RecipeExecutionEvent.Failed) failed.countDown()
        }
        assertTrue(connection.readStarted.await(5, TimeUnit.SECONDS))

        runtime.stop("native-recipe-instance", 100L) { confirmed ->
            stopped.set(confirmed)
            stopCompleted.countDown()
        }

        assertTrue(failed.await(5, TimeUnit.SECONDS))
        assertTrue(stopCompleted.await(5, TimeUnit.SECONDS))
        assertEquals(true, stopped.get())
        assertFalse(File(root, "payload.bin").exists())
        assertFalse(root.listFiles().orEmpty().any { it.name.contains(".kite-download-") })
    }

    @Test
    fun `native file step writes the same run without terminal or process binding`() {
        val root = Files.createTempDirectory("kite-native-file-recipe").toFile()
        File(root, "source.txt").writeText("native-file")
        val runtime = runtime(root, NativeDownloadConnectionFactory { _, _, _ -> error("download_not_expected") })
        val events = Collections.synchronizedList(mutableListOf<RecipeExecutionEvent>())
        val completed = CountDownLatch(1)

        runtime.execute(fileRequest()) { event ->
            events += event
            if (event is RecipeExecutionEvent.Completed || event is RecipeExecutionEvent.Failed) {
                completed.countDown()
            }
        }

        assertTrue(completed.await(5, TimeUnit.SECONDS))
        val result = events.filterIsInstance<RecipeExecutionEvent.Completed>().single()
        assertEquals("android_native", result.mutation.runtimeLane)
        assertEquals("native_file_copy_ready", result.mutation.runtimeFallbackReason)
        assertEquals(null, result.mutation.runId)
        assertEquals(null, result.mutation.terminalSessionId)
        assertEquals("native-file", File(root, "copied.txt").readText())
        assertFalse(runtime.owns("native-file-instance", 200L))
    }

    @Test
    fun `stop cancels a blocking native file operation and waits for ownership release`() {
        val root = Files.createTempDirectory("kite-native-file-stop").toFile()
        File(root, "source.txt").writeText("native-file")
        val started = CountDownLatch(1)
        val failed = CountDownLatch(1)
        val stopped = CountDownLatch(1)
        val confirmed = AtomicReference<Boolean>()
        val runtime = runtime(
            root,
            NativeDownloadConnectionFactory { _, _, _ -> error("download_not_expected") },
            fileGateway = NativeFileExecutionGateway { _, cancellation, _ ->
                started.countDown()
                while (!cancellation.isCancelled()) Thread.sleep(10L)
                NativeFileExecutionResult.Cancelled(0L)
            },
        )
        runtime.execute(fileRequest()) { event ->
            if (event is RecipeExecutionEvent.Failed) failed.countDown()
        }
        assertTrue(started.await(5, TimeUnit.SECONDS))

        runtime.stop("native-file-instance", 200L) { result ->
            confirmed.set(result)
            stopped.countDown()
        }

        assertTrue(failed.await(5, TimeUnit.SECONDS))
        assertTrue(stopped.await(5, TimeUnit.SECONDS))
        assertEquals(true, confirmed.get())
        assertFalse(File(root, "copied.txt").exists())
    }

    private fun runtime(
        root: File,
        factory: NativeDownloadConnectionFactory,
        fileGateway: NativeFileExecutionGateway? = null,
    ) = AndroidNativeCapabilityRecipeRuntime(
        context = RuntimeEnvironment.getApplication(),
        downloadExecutor = AndroidNativeDownloadExecutor(factory),
        capabilityContextProvider = {
            AndroidNativeCapabilityContext(
                listOf(NativeCapabilityDestinationRoot("/workspace", root))
            )
        },
        fileCapabilityContextProvider = {
            AndroidNativeFileCapabilityContext(
                listOf(
                    NativeFileCapabilityRoot(
                        "/workspace",
                        root,
                        NativeFilePermission.entries.toSet(),
                    )
                )
            )
        },
        fileExecutionGateway = fileGateway ?: NativeFileExecutionGateway { plan, cancellation, progress ->
            com.kite.app.foundation.runtime.AndroidNativeFileExecutor().execute(plan, cancellation, progress)
        },
    )

    private fun fileRequest(): RecipeStepExecutionRequest {
        val params = JSONObject()
            .put(AndroidNativeFileCapabilityProvider.PARAM_SOURCE, "/workspace/source.txt")
            .put(AndroidNativeFileCapabilityProvider.PARAM_DESTINATION, "/workspace/copied.txt")
            .put(AndroidNativeFileCapabilityProvider.PARAM_MAX_BYTES, "1048576")
        val step = KiteRecipeStep(
            id = "native-file-copy",
            type = KiteRecipe.STEP_NATIVE_CAPABILITY,
            action = AndroidNativeFileCapabilityProvider.CAPABILITY_COPY_FILE,
            params = params,
        )
        val recipe = KiteRecipe(
            id = "native-file-recipe",
            name = "Native File Recipe",
            description = "",
            type = KiteRecipe.TYPE_TEMPLATE,
            defaultUrl = "",
            shortcut = false,
            execution = KiteExecution.steps(listOf(step)),
        )
        val state = CardRunState(
            instanceId = "native-file-instance",
            recipeId = recipe.id,
            recipeName = recipe.name,
            status = CardRunStatus.Running,
            currentStepIndex = 0,
            createdAt = 200L,
            updatedAt = 200L,
        )
        return RecipeStepExecutionRequest(
            recipe = recipe,
            instanceId = state.instanceId,
            generation = state.createdAt,
            stepIndex = 0,
            step = step,
            previousState = state,
        )
    }

    private fun request(root: File, expectedSha256: String): RecipeStepExecutionRequest {
        val params = JSONObject()
            .put(AndroidNativeDownloadCapabilityProvider.PARAM_URL, "https://example.test/payload")
            .put(AndroidNativeDownloadCapabilityProvider.PARAM_DESTINATION, "/workspace/payload.bin")
            .put(AndroidNativeDownloadCapabilityProvider.PARAM_EXPECTED_SHA256, expectedSha256)
            .put(AndroidNativeDownloadCapabilityProvider.PARAM_MAX_BYTES, "1048576")
            .put(AndroidNativeDownloadCapabilityProvider.PARAM_REPLACE_EXISTING, "true")
        val step = KiteRecipeStep(
            id = "native-download",
            type = KiteRecipe.STEP_NATIVE_CAPABILITY,
            action = AndroidNativeDownloadCapabilityProvider.CAPABILITY_ID,
            params = params,
        )
        val recipe = KiteRecipe(
            id = "native-recipe",
            name = "Native Recipe",
            description = root.absolutePath,
            type = KiteRecipe.TYPE_TEMPLATE,
            defaultUrl = "",
            shortcut = false,
            execution = KiteExecution.steps(listOf(step)),
        )
        val state = CardRunState(
            instanceId = "native-recipe-instance",
            recipeId = recipe.id,
            recipeName = recipe.name,
            status = CardRunStatus.Running,
            currentStepIndex = 0,
            createdAt = 100L,
            updatedAt = 100L,
        )
        return RecipeStepExecutionRequest(
            recipe = recipe,
            instanceId = state.instanceId,
            generation = state.createdAt,
            stepIndex = 0,
            step = step,
            previousState = state,
        )
    }

    private class Response(private val bytes: ByteArray) : NativeDownloadConnection {
        override val responseCode: Int = 200
        override val contentLength: Long = bytes.size.toLong()
        override fun header(name: String): String? = null
        override fun inputStream(): InputStream = ByteArrayInputStream(bytes)
        override fun close() = Unit
    }

    private class BlockingConnection : NativeDownloadConnection {
        val readStarted = CountDownLatch(1)
        private val lock = Object()
        @Volatile private var closed = false
        override val responseCode: Int = 200
        override val contentLength: Long = -1L
        override fun header(name: String): String? = null
        override fun inputStream(): InputStream = object : InputStream() {
            private var emitted = false

            override fun read(): Int = error("single_byte_read_not_supported")

            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                if (!emitted) {
                    emitted = true
                    val count = minOf(length, 1024)
                    repeat(count) { index -> buffer[offset + index] = 7 }
                    readStarted.countDown()
                    return count
                }
                synchronized(lock) {
                    while (!closed) lock.wait(100L)
                }
                throw IOException("connection closed")
            }

            override fun close() {
                this@BlockingConnection.close()
            }
        }

        override fun close() {
            synchronized(lock) {
                closed = true
                lock.notifyAll()
            }
        }
    }

    private fun ByteArray.sha256(): String = MessageDigest.getInstance("SHA-256")
        .digest(this)
        .joinToString("") { byte -> "%02x".format(byte) }
}
