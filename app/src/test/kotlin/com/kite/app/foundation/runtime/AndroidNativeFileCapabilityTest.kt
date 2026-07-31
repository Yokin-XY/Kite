package com.kite.app.foundation.runtime

import java.io.File
import java.io.IOException
import java.nio.file.AccessDeniedException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidNativeFileCapabilityTest {
    @Test
    fun `provider uses explicit root permissions and rejects traversal or symlink`() {
        val fixture = fixture()
        File(fixture.workspace, "source.txt").writeText("source")
        val ready = prepare(
            fixture,
            AndroidNativeFileCapabilityProvider.CAPABILITY_COPY_FILE,
            mapOf(
                "source" to "/workspace/source.txt",
                "destination" to "/workspace/copied.txt",
                "maxBytes" to "1024",
            ),
        ) as RuntimeProviderDecision.Ready
        assertEquals("native_file_copy_ready", ready.reason)

        assertBlocked(
            fixture,
            AndroidNativeFileCapabilityProvider.CAPABILITY_DELETE_FILE,
            mapOf("target" to "/workspace/source.txt"),
            "native_file_delete_not_authorized",
        )
        assertBlocked(
            fixture,
            AndroidNativeFileCapabilityProvider.CAPABILITY_COPY_FILE,
            mapOf("source" to "/workspace/../escape", "destination" to "/workspace/out", "maxBytes" to "1"),
            "native_file_source_invalid",
        )
        val outside = Files.createTempDirectory("kite-native-file-outside")
        val link = File(fixture.cache, "outside-link").toPath()
        runCatching { Files.createSymbolicLink(link, outside) }.onSuccess {
            assertBlocked(
                fixture,
                AndroidNativeFileCapabilityProvider.CAPABILITY_DELETE_FILE,
                mapOf("target" to "/workspace/.kf/cache/outside-link/file"),
                "native_file_delete_not_authorized",
            )
        }
    }

    @Test
    fun `copy streams through a temporary file and atomically replaces destination`() {
        val fixture = fixture()
        val source = File(fixture.workspace, "source.bin").apply { writeBytes(ByteArray(256 * 1024) { 31 }) }
        val destination = File(fixture.workspace, "destination.bin").apply { writeText("old") }
        val plan = readyPlan(
            fixture,
            AndroidNativeFileCapabilityProvider.CAPABILITY_COPY_FILE,
            mapOf(
                "source" to "/workspace/${source.name}",
                "destination" to "/workspace/${destination.name}",
                "maxBytes" to source.length().toString(),
                "replaceExisting" to "true",
            ),
        ) as AndroidNativeFilePlan.CopyFile
        val progress = mutableListOf<Long>()

        val result = AndroidNativeFileExecutor().execute(
            plan,
            progress = NativeFileProgressListener { copied, _ -> progress += copied },
        )

        assertEquals(NativeFileExecutionResult.Success(plan.capabilityId, source.length()), result)
        assertEquals(source.readBytes().toList(), destination.readBytes().toList())
        assertEquals(source.length(), progress.last())
        assertFalse(plan.temporaryFile.exists())
    }

    @Test
    fun `copy cancellation and size failure preserve destination and clean temporary file`() {
        val fixture = fixture()
        val source = File(fixture.workspace, "source.bin").apply { writeBytes(ByteArray(256 * 1024) { 17 }) }
        val destination = File(fixture.workspace, "destination.bin").apply { writeText("old") }
        val cancelledPlan = readyPlan(
            fixture,
            AndroidNativeFileCapabilityProvider.CAPABILITY_COPY_FILE,
            mapOf(
                "source" to "/workspace/${source.name}",
                "destination" to "/workspace/${destination.name}",
                "maxBytes" to source.length().toString(),
                "replaceExisting" to "true",
            ),
        ) as AndroidNativeFilePlan.CopyFile
        val cancelled = AtomicBoolean(false)
        val cancelResult = AndroidNativeFileExecutor().execute(
            cancelledPlan,
            cancellation = NativeFileCancellation(cancelled::get),
            progress = NativeFileProgressListener { _, _ -> cancelled.set(true) },
        )
        assertTrue(cancelResult is NativeFileExecutionResult.Cancelled)
        assertEquals("old", destination.readText())
        assertFalse(cancelledPlan.temporaryFile.exists())

        val limitedPlan = cancelledPlan.copy(
            temporaryFile = File(destination.parentFile, ".limited.part"),
            maximumBytes = source.length() - 1,
        )
        assertEquals(
            NativeFileExecutionResult.Failure("native_file_size_limit"),
            AndroidNativeFileExecutor().execute(limitedPlan),
        )
        assertEquals("old", destination.readText())
        assertFalse(limitedPlan.temporaryFile.exists())
    }

    @Test
    fun `atomic move and controlled delete only operate inside removable root`() {
        val fixture = fixture()
        val source = File(fixture.cache, "source.txt").apply { writeText("move") }
        val move = readyPlan(
            fixture,
            AndroidNativeFileCapabilityProvider.CAPABILITY_MOVE_FILE,
            mapOf(
                "source" to "/workspace/.kf/cache/source.txt",
                "destination" to "/workspace/.kf/cache/moved.txt",
            ),
        ) as AndroidNativeFilePlan.MoveFile
        val executor = AndroidNativeFileExecutor()
        assertEquals(NativeFileExecutionResult.Success(move.capabilityId, 4L), executor.execute(move))
        assertFalse(source.exists())
        assertEquals("move", File(fixture.cache, "moved.txt").readText())

        val delete = readyPlan(
            fixture,
            AndroidNativeFileCapabilityProvider.CAPABILITY_DELETE_FILE,
            mapOf("target" to "/workspace/.kf/cache/moved.txt"),
        ) as AndroidNativeFilePlan.DeleteFile
        assertEquals(NativeFileExecutionResult.Success(delete.capabilityId, 4L), executor.execute(delete))
        assertFalse(delete.target.exists())
    }

    @Test
    fun `permission and atomic move failures do not report success or remove source`() {
        val fixture = fixture()
        val source = File(fixture.cache, "source.txt").apply { writeText("safe") }
        val destination = File(fixture.cache, "destination.txt")
        val move = readyPlan(
            fixture,
            AndroidNativeFileCapabilityProvider.CAPABILITY_MOVE_FILE,
            mapOf(
                "source" to "/workspace/.kf/cache/source.txt",
                "destination" to "/workspace/.kf/cache/destination.txt",
            ),
        ) as AndroidNativeFilePlan.MoveFile
        val denied = AndroidNativeFileExecutor(FailingPlatform(AccessDeniedException(source.path))).execute(move)
        assertEquals(NativeFileExecutionResult.Failure("native_file_permission_denied"), denied)
        assertTrue(source.exists())
        assertFalse(destination.exists())

        val unsupported = AndroidNativeFileExecutor(
            FailingPlatform(AtomicMoveNotSupportedException(source.path, destination.path, "unsupported"))
        ).execute(move)
        assertEquals(NativeFileExecutionResult.Failure("native_file_atomic_move_unsupported"), unsupported)
        assertTrue(source.exists())
        assertFalse(destination.exists())
    }

    private data class Fixture(val workspace: File, val cache: File)

    private fun fixture(): Fixture {
        val workspace = Files.createTempDirectory("kite-native-file").toFile()
        val cache = File(workspace, ".kf/cache").apply { mkdirs() }
        return Fixture(workspace, cache)
    }

    private fun context(fixture: Fixture) = AndroidNativeFileCapabilityContext(
        listOf(
            NativeFileCapabilityRoot(
                "/workspace/.kf/cache",
                fixture.cache,
                NativeFilePermission.entries.toSet(),
            ),
            NativeFileCapabilityRoot(
                "/workspace",
                fixture.workspace,
                setOf(NativeFilePermission.READ, NativeFilePermission.CREATE, NativeFilePermission.REPLACE),
            ),
        )
    )

    private fun prepare(
        fixture: Fixture,
        capabilityId: String,
        parameters: Map<String, String>,
    ): RuntimeProviderDecision<AndroidNativeFilePlan> = AndroidNativeFileCapabilityProvider.prepare(
        context(fixture),
        RuntimeExecutionRequest(
            payload = RuntimeExecutionPayload.NativeCapability(capabilityId, parameters),
            requirements = setOf(RuntimeExecutionRequirement.ANDROID_NATIVE),
        ),
    )

    private fun readyPlan(
        fixture: Fixture,
        capabilityId: String,
        parameters: Map<String, String>,
    ): AndroidNativeFilePlan = (prepare(fixture, capabilityId, parameters) as RuntimeProviderDecision.Ready).plan

    private fun assertBlocked(
        fixture: Fixture,
        capabilityId: String,
        parameters: Map<String, String>,
        reason: String,
    ) {
        val decision = prepare(fixture, capabilityId, parameters) as RuntimeProviderDecision.Blocked
        assertEquals(reason, decision.reason)
    }

    private class FailingPlatform(private val error: IOException) : NativeFilePlatform {
        override fun atomicMove(source: Path, destination: Path, replaceExisting: Boolean): Unit = throw error
        override fun delete(path: Path) = Files.delete(path)
    }
}
