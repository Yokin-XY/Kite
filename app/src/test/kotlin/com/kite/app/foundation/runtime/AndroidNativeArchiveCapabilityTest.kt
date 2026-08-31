package com.kite.app.foundation.runtime

import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.concurrent.atomic.AtomicBoolean
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidNativeArchiveCapabilityTest {
    @Test
    fun `provider only accepts bounded zip inside authorized roots`() {
        val fixture = fixture()
        zip(File(fixture.cache, "source.zip"), "dir/file.txt" to "content".toByteArray())
        val ready = prepare(fixture) as RuntimeProviderDecision.Ready
        assertEquals("native_archive_zip_ready", ready.reason)

        assertBlocked(fixture, parameters = parameters(format = "7z"), "native_archive_format_unsupported")
        assertBlocked(
            fixture,
            parameters = parameters(destination = "/workspace/../escape"),
            "native_archive_destination_invalid",
        )
        assertBlocked(
            fixture,
            parameters = parameters(extra = mapOf("preserveMode" to "true")),
            "native_archive_parameter_unknown",
        )
    }

    @Test
    fun `provider accepts bounded tar formats without weakening path authorization`() {
        val fixture = fixture()
        File(fixture.cache, "source.zip").writeBytes(byteArrayOf(1, 2, 3))
        val digest = "a".repeat(64)
        val ready = prepare(
            fixture,
            parameters(
                format = "tar.gz",
                extra = mapOf(
                    AndroidNativeArchiveCapabilityProvider.PARAM_EXPECTED_SHA256 to digest,
                    AndroidNativeArchiveCapabilityProvider.PARAM_SPECIAL_ENTRY_POLICY to "reject",
                    AndroidNativeArchiveCapabilityProvider.PARAM_REUSE_KEY to "v1:tar.gz:$digest",
                ),
            ),
        ) as RuntimeProviderDecision.Ready

        assertEquals(AndroidNativeArchiveFormat.TAR_GZIP, ready.plan.format)
        assertEquals(digest, ready.plan.expectedSha256)
        assertEquals("native_archive_tar_gz_ready", ready.reason)
        val accepted = setOf("b".repeat(64), "c".repeat(64))
        val acceptedReady = prepare(
            fixture,
            parameters(
                format = "tar.gz",
                extra = mapOf(
                    AndroidNativeArchiveCapabilityProvider.PARAM_ACCEPTED_SHA256S to accepted.joinToString("\n"),
                ),
            ),
        ) as RuntimeProviderDecision.Ready
        assertEquals(accepted, acceptedReady.plan.acceptedSha256s)
        assertBlocked(
            fixture,
            parameters(format = "tar.gz", extra = mapOf("specialEntryPolicy" to "unknown")),
            "native_archive_special_entry_policy_invalid",
        )
        assertBlocked(
            fixture,
            parameters(
                format = "tar.gz",
                extra = mapOf(
                    AndroidNativeArchiveCapabilityProvider.PARAM_EXPECTED_SHA256 to digest,
                    AndroidNativeArchiveCapabilityProvider.PARAM_ACCEPTED_SHA256S to accepted.joinToString("\n"),
                ),
            ),
            "native_archive_digest_contract_conflict",
        )
    }

    @Test
    fun `regular zip extracts into staging then atomically publishes`() {
        val fixture = fixture()
        val archive = File(fixture.cache, "source.zip")
        zip(archive, "dir/file.txt" to "content".toByteArray(), "root.bin" to ByteArray(128) { 7 })
        val plan = readyPlan(fixture)
        val progress = mutableListOf<Long>()

        val result = AndroidNativeArchiveExecutor().execute(
            plan,
            progress = NativeArchiveProgressListener { _, bytes -> progress += bytes },
        )

        assertEquals(NativeArchiveExecutionResult.Success(2, 135L), result)
        assertEquals("content", File(plan.destination, "dir/file.txt").readText())
        assertEquals(128L, File(plan.destination, "root.bin").length())
        assertEquals(135L, progress.last())
        assertFalse(plan.stagingDirectory.exists())
    }

    @Test
    fun `zip slip duplicate and symlink entries fail closed and clean staging`() {
        val fixture = fixture()
        val archive = File(fixture.cache, "source.zip")
        zip(archive, "../escape.txt" to "escape".toByteArray())
        var plan = readyPlan(fixture)
        assertEquals(
            NativeArchiveExecutionResult.Failure("native_archive_path_invalid"),
            AndroidNativeArchiveExecutor().execute(plan),
        )
        assertFalse(plan.destination.exists())
        assertFalse(plan.stagingDirectory.exists())

        zipWithEntries(archive) { output ->
            repeat(2) {
                output.putArchiveEntry(ZipArchiveEntry("same.txt"))
                output.write(byteArrayOf(1))
                output.closeArchiveEntry()
            }
        }
        plan = readyPlan(fixture)
        assertEquals(
            NativeArchiveExecutionResult.Failure("native_archive_duplicate_entry"),
            AndroidNativeArchiveExecutor().execute(plan),
        )

        zipWithEntries(archive) { output ->
            val symlink = ZipArchiveEntry("link").apply { unixMode = 0xA1FF }
            output.putArchiveEntry(symlink)
            output.write("target".toByteArray())
            output.closeArchiveEntry()
        }
        plan = readyPlan(fixture)
        assertEquals(
            NativeArchiveExecutionResult.Failure("native_archive_special_entry"),
            AndroidNativeArchiveExecutor().execute(plan),
        )
        assertFalse(plan.destination.exists())
        assertFalse(plan.stagingDirectory.exists())
    }

    @Test
    fun `entry output and expansion limits stop zip bombs before publication`() {
        val fixture = fixture()
        val archive = File(fixture.cache, "source.zip")
        zip(archive, "large.bin" to ByteArray(256 * 1024))
        val base = readyPlan(fixture)
        val fileLimited = base.copy(maximumFileBytes = 64 * 1024L)
        assertEquals(
            NativeArchiveExecutionResult.Failure("native_archive_file_size_limit"),
            AndroidNativeArchiveExecutor().execute(fileLimited),
        )
        val ratioLimited = base.copy(maximumFileBytes = 512 * 1024L, maximumExpansionRatio = 1)
        assertEquals(
            NativeArchiveExecutionResult.Failure("native_archive_expansion_ratio_limit"),
            AndroidNativeArchiveExecutor().execute(ratioLimited),
        )
        assertFalse(base.destination.exists())
        assertFalse(base.stagingDirectory.exists())

        val noSpace = AndroidNativeArchiveExecutor(availableBytes = { 1_024L }).execute(base)
        assertEquals(NativeArchiveExecutionResult.Failure("native_archive_insufficient_space"), noSpace)
        assertFalse(base.destination.exists())
        assertFalse(base.stagingDirectory.exists())
    }

    @Test
    fun `cancellation and unsupported atomic publication leave destination absent`() {
        val fixture = fixture()
        val archive = File(fixture.cache, "source.zip")
        zip(archive, "large.bin" to ByteArray(512 * 1024) { 19 })
        val plan = readyPlan(fixture)
        val cancelled = AtomicBoolean(false)
        val cancellationResult = AndroidNativeArchiveExecutor().execute(
            plan,
            cancellation = NativeFileCancellation(cancelled::get),
            progress = NativeArchiveProgressListener { _, _ -> cancelled.set(true) },
        )
        assertTrue(cancellationResult is NativeArchiveExecutionResult.Cancelled)
        assertFalse(plan.destination.exists())
        assertFalse(plan.stagingDirectory.exists())

        val unsupported = AndroidNativeArchiveExecutor(object : NativeFilePlatform {
            override fun atomicMove(source: Path, destination: Path, replaceExisting: Boolean) {
                throw AtomicMoveNotSupportedException(source.toString(), destination.toString(), "unsupported")
            }

            override fun delete(path: Path) = Files.delete(path)
        }).execute(plan.copy(maximumExpansionRatio = 1_000))
        assertEquals(
            NativeArchiveExecutionResult.Failure("native_archive_atomic_move_unsupported"),
            unsupported,
        )
        assertFalse(plan.destination.exists())
        assertFalse(plan.stagingDirectory.exists())
    }

    private data class Fixture(val workspace: File, val cache: File)

    private fun fixture(): Fixture {
        val workspace = Files.createTempDirectory("kite-native-archive").toFile()
        val cache = File(workspace, ".kf/cache").apply { mkdirs() }
        return Fixture(workspace, cache)
    }

    private fun context(fixture: Fixture) = AndroidNativeFileCapabilityContext(
        listOf(
            NativeFileCapabilityRoot(
                "/workspace/.kf/cache",
                fixture.cache,
                setOf(NativeFilePermission.READ, NativeFilePermission.CREATE),
            ),
            NativeFileCapabilityRoot(
                "/workspace",
                fixture.workspace,
                setOf(NativeFilePermission.READ),
            ),
        )
    )

    private fun prepare(
        fixture: Fixture,
        parameters: Map<String, String> = parameters(),
    ) = AndroidNativeArchiveCapabilityProvider.prepare(
        context(fixture),
        RuntimeExecutionRequest(
            payload = RuntimeExecutionPayload.NativeCapability(AndroidNativeArchiveCapabilityProvider.CAPABILITY_ID, parameters),
            requirements = setOf(RuntimeExecutionRequirement.ANDROID_NATIVE),
        ),
    )

    private fun readyPlan(fixture: Fixture): AndroidNativeArchivePlan =
        (prepare(fixture) as RuntimeProviderDecision.Ready).plan

    private fun parameters(
        format: String = "zip",
        destination: String = "/workspace/.kf/cache/output",
        extra: Map<String, String> = emptyMap(),
    ) = mapOf(
        "source" to "/workspace/.kf/cache/source.zip",
        "destination" to destination,
        "format" to format,
        "maxArchiveBytes" to (2 * 1024 * 1024).toString(),
        "maxEntries" to "100",
        "maxTotalBytes" to (2 * 1024 * 1024).toString(),
        "maxFileBytes" to (1024 * 1024).toString(),
        "maxDepth" to "8",
        "maxExpansionRatio" to "100",
    ) + extra

    private fun assertBlocked(fixture: Fixture, parameters: Map<String, String>, reason: String) {
        val blocked = prepare(fixture, parameters) as RuntimeProviderDecision.Blocked
        assertEquals(reason, blocked.reason)
    }

    private fun zip(target: File, vararg entries: Pair<String, ByteArray>) = zipWithEntries(target) { output ->
        entries.forEach { (name, bytes) ->
            output.putArchiveEntry(ZipArchiveEntry(name))
            output.write(bytes)
            output.closeArchiveEntry()
        }
    }

    private fun zipWithEntries(target: File, block: (ZipArchiveOutputStream) -> Unit) {
        target.parentFile?.mkdirs()
        ZipArchiveOutputStream(target).use { output ->
            block(output)
            output.finish()
        }
    }
}
