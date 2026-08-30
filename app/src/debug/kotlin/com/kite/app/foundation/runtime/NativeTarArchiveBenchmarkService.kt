package com.kite.app.foundation.runtime

import android.app.Service
import android.app.Notification
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.SystemClock
import android.system.Os
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.kite.app.R
import com.kite.app.foundation.bootstrap.KFApplication
import com.kite.app.foundation.service.KiteTaskContractHost
import com.kite.app.foundation.workspace.WorkSurfaceRuntimeBridge
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.archivers.tar.TarConstants
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream

/** Debug-only entry for the fixed real Kite tar.gz matrix. */
class NativeTarArchiveBenchmarkReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION) return
        ContextCompat.startForegroundService(
            context,
            Intent(context, NativeTarArchiveBenchmarkService::class.java),
        )
    }

    companion object {
        const val ACTION = "com.kite.app.debug.NATIVE_TAR_ARCHIVE_BENCHMARK"
        const val LOG_TAG = "[KFShell]NativeTarBenchmark"
    }
}

class NativeTarArchiveBenchmarkService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, createNotification())
        if (!running.compareAndSet(false, true)) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        scope.launch {
            try {
                NativeTarArchiveBenchmark.run(applicationContext).forEach { report ->
                    Log.i(NativeTarArchiveBenchmarkReceiver.LOG_TAG, report)
                }
            } catch (error: Throwable) {
                Log.e(
                    NativeTarArchiveBenchmarkReceiver.LOG_TAG,
                    "status=failed reason=${safe(error.message ?: error.javaClass.simpleName)}",
                    error,
                )
            } finally {
                running.set(false)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun createNotification(): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            KiteTaskContractHost.get().buildMainActivityIntent(this),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, KFApplication.CHANNEL_BACKGROUND_RUNTIME)
            .setSmallIcon(R.drawable.ic_status)
            .setContentTitle("Kite Debug 归档矩阵")
            .setContentText("正在比较 Kotlin、Rust、C 与 PRoot")
            .setContentIntent(openApp)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setSilent(true)
            .setOngoing(true)
            .build()
    }

    private companion object {
        const val NOTIFICATION_ID = 44_021
        val running = AtomicBoolean(false)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        fun safe(value: String): String = value.take(240).map { character ->
            if (character.isLetterOrDigit() || character in "-_.:=/%") character else '_'
        }.joinToString("")
    }
}

private object NativeTarArchiveBenchmark {
    private const val RESOURCE_ID = "kite.debug.native-tar-benchmark"
    private const val CONTAINER_ROOT = "/workspace/.kf/cache/resources/$RESOURCE_ID"
    private const val TIMEOUT_MS = 180_000L
    private const val MAXIMUM_TOTAL_BYTES = 2L * 1024L * 1024L * 1024L
    private const val MAXIMUM_FILE_BYTES = 768L * 1024L * 1024L
    private val fixtures = listOf(
        Fixture(
            id = "rootfs",
            assetPath = "rootfs/ubuntu-base-24.04-arm64.tgz",
            expectedEntries = 12_305,
            maximumEntries = 20_000,
            expectedSha256 = "592dbc9d5d119a49bde1a8777ee125f57293df27e3a14d6b01d494339349faa8",
            compression = TarCompression.GZIP,
            engines = Engine.entries,
        ),
        Fixture(
            id = "cpython",
            assetPath = "toolchain/ai-dev-pack/packages/" +
                "cpython-3.14.6+20260623-aarch64-unknown-linux-gnu-install_only_stripped.tgz",
            expectedEntries = 4_557,
            maximumEntries = 8_000,
            expectedSha256 = "f177d40ca931df03f660fc006f86ad8cd2ac6e7d6b5d54edbc625103464fc4aa",
            compression = TarCompression.GZIP,
            engines = Engine.entries,
        ),
        Fixture(
            id = "pnpm",
            assetPath = "toolchain/ai-dev-pack/packages/pnpm-11.9.0.tgz",
            expectedEntries = 450,
            maximumEntries = 1_000,
            expectedSha256 = "2b567aa66026238078ac2e0a33bec3febd60e962987aac697456f3180819b287",
            compression = TarCompression.GZIP,
            engines = Engine.entries,
        ),
        Fixture(
            id = "node",
            assetPath = "toolchain/ai-dev-pack/packages/node-v26.4.0-linux-arm64.tar.xz",
            expectedEntries = 5_719,
            maximumEntries = 8_000,
            expectedSha256 = "f6d8eedc52170667d45730ac2f413c4aa1e7cd2165c9cac5746ef3cb0f4ec45a",
            compression = TarCompression.XZ,
            engines = listOf(Engine.RUST, Engine.PROOT),
        ),
    )

    fun run(context: Context): List<String> {
        check(RustArchiveBridge.isAvailable) { "rust_archive_library_unavailable" }
        val workspace = KFContainerManager.resolveWorkspaceDirectory(context)
        val root = File(workspace, ".kf/cache/resources/$RESOURCE_ID")
        deleteTree(root)
        check(root.mkdirs()) { "benchmark_root_create_failed" }
        return try {
            buildList {
                fixtures.forEachIndexed { index, fixture ->
                    val source = File(root, "${fixture.id}.${fixture.compression.assetExtension}")
                    copyAsset(context, fixture.assetPath, source)
                    val rotation = index % fixture.engines.size
                    val engines = fixture.engines.drop(rotation) + fixture.engines.take(rotation)
                    val outcomes = linkedMapOf<Engine, EngineOutcome>()
                    engines.forEach { engine ->
                        outcomes[engine] = runEngine(context, root, fixture, source, engine)
                    }
                    val successful = outcomes.values.filter { it.failure == null }
                    val baseline = outcomes[Engine.KOTLIN]?.fingerprint
                        ?: outcomes[Engine.PROOT]?.fingerprint
                    checkNotNull(baseline) { "${fixture.id}_baseline_missing" }
                    successful.forEach { outcome ->
                        check(outcome.fingerprint == baseline) {
                            "${fixture.id}_${outcome.engine.id}_tree_mismatch:" +
                                describeTreeDifference(checkNotNull(outcome.fingerprint), baseline)
                        }
                    }
                    add(renderFixture(fixture, source.length(), outcomes))
                }
                add(runAdmissionGates(context, root))
                add("status=complete cleanup=true production_route=false")
            }
        } finally {
            deleteTree(root)
        }
    }

    private fun runEngine(
        context: Context,
        root: File,
        fixture: Fixture,
        source: File,
        engine: Engine,
    ): EngineOutcome {
        val destination = File(root, "${fixture.id}-${engine.id}")
        val staging = File(root, ".${destination.name}.kite-tar-part")
        deleteTree(destination)
        deleteTree(staging)
        val started = SystemClock.elapsedRealtime()
        val failure = runCatching {
            when (engine) {
                Engine.KOTLIN -> {
                    val result = AssetExtractor.extractTarGzipFile(source, destination)
                    check(result.entriesExtracted == fixture.expectedEntries) {
                        "entry_count_${result.entriesExtracted}"
                    }
                }
                Engine.RUST -> {
                    val result = RustArchiveBridge.executeTar(
                        RustTarArchiveRequest(
                            source = source,
                            destination = destination,
                            stagingDirectory = staging,
                            maximumArchiveBytes = source.length(),
                            maximumEntries = fixture.maximumEntries,
                            maximumTotalBytes = MAXIMUM_TOTAL_BYTES,
                            maximumFileBytes = MAXIMUM_FILE_BYTES,
                            maximumDepth = 64,
                            maximumExpansionRatio = 24,
                            expectedArchiveBytes = source.length(),
                            expectedSha256 = fixture.expectedSha256,
                            compression = fixture.compression.rust,
                            specialEntryPolicy = RustTarSpecialEntryPolicy.MATERIALIZE_EMPTY_FILE,
                        )
                    )
                    check(
                        result is NativeArchiveExecutionResult.Success &&
                            result.entriesExtracted == fixture.expectedEntries
                    ) { "rust_extract_failed:$result" }
                }
                Engine.LIBARCHIVE_C -> {
                    val execution = executeLibarchive(context, fixture, source, destination, staging)
                    check(
                        execution.exitCode == 0 &&
                            execution.stdout.lineSequence().any {
                                it == "success|${fixture.expectedEntries}|${execution.declaredBytes}"
                            }
                    ) {
                        "libarchive_extract_failed:${execution.exitCode}:" +
                            "${execution.stdout}:${execution.stderr}"
                    }
                }
                Engine.PROOT -> {
                    val execution = executeProot(context, fixture, destination)
                    check(execution.exitCode == 0) {
                        "proot_extract_failed:${execution.exitCode}:${execution.stderr}"
                    }
                }
            }
        }.exceptionOrNull()?.message
        val elapsed = SystemClock.elapsedRealtime() - started
        val fingerprint = if (failure == null) fingerprint(destination) else null
        val outcome = EngineOutcome(engine, elapsed, fingerprint, failure)
        deleteTree(destination)
        deleteTree(staging)
        return outcome
    }

    private fun executeLibarchive(
        context: Context,
        fixture: Fixture,
        source: File,
        destination: File,
        staging: File,
    ): NativeProcessExecution = executeLibarchiveRequest(
        context = context,
        source = source,
        destination = destination,
        staging = staging,
        maximumEntries = fixture.maximumEntries,
        cancelAfterBytes = 0L,
    )

    private fun executeLibarchiveRequest(
        context: Context,
        source: File,
        destination: File,
        staging: File,
        maximumEntries: Int,
        cancelAfterBytes: Long,
    ): NativeProcessExecution {
        val executable = File(
            context.applicationInfo.nativeLibraryDir,
            "libkite_libarchive_benchmark.so",
        )
        check(executable.isFile && executable.canExecute()) { "libarchive_benchmark_unavailable" }
        val process = ProcessBuilder(
            executable.absolutePath,
            source.absolutePath,
            destination.absolutePath,
            staging.absolutePath,
            source.length().toString(),
            maximumEntries.toString(),
            MAXIMUM_TOTAL_BYTES.toString(),
            MAXIMUM_FILE_BYTES.toString(),
            "64",
            "24",
            RustTarSpecialEntryPolicy.MATERIALIZE_EMPTY_FILE.wireValue,
            cancelAfterBytes.toString(),
        ).start()
        val stdout = ByteArrayOutputStream()
        val stderr = ByteArrayOutputStream()
        val outReader = thread(start = true, isDaemon = true) { process.inputStream.use { it.copyTo(stdout) } }
        val errReader = thread(start = true, isDaemon = true) { process.errorStream.use { it.copyTo(stderr) } }
        val finished = process.waitFor(TIMEOUT_MS, TimeUnit.MILLISECONDS)
        if (!finished) process.destroyForcibly()
        outReader.join(1_000L)
        errReader.join(1_000L)
        val stdoutText = stdout.toString(Charsets.UTF_8.name()).trim()
        val declaredBytes = stdoutText
            .lineSequence()
            .firstOrNull { it.startsWith("success|") }
            ?.substringAfterLast('|')
            ?.toLongOrNull()
            ?: -1L
        val execution = NativeProcessExecution(
            exitCode = if (finished) process.exitValue() else -1,
            stdout = stdoutText,
            stderr = stderr.toString(Charsets.UTF_8.name()).trim(),
            declaredBytes = declaredBytes,
        )
        if (execution.exitCode != 0) {
            deleteTree(destination)
            deleteTree(staging)
        }
        return execution
    }

    private fun runAdmissionGates(context: Context, root: File): String {
        val traversalArchive = File(root, "gate-traversal.tgz")
        writeTraversalArchive(traversalArchive)
        val traversalEscape = File(root, "escape-marker")
        val rustTraversalDestination = File(root, "gate-rust-traversal")
        val rustTraversalStaging = File(root, ".gate-rust-traversal-part")
        val rustTraversal = RustArchiveBridge.executeTar(
            gateRequest(traversalArchive, rustTraversalDestination, rustTraversalStaging)
        )
        check(rustTraversal is NativeArchiveExecutionResult.Failure)
        check(!traversalEscape.exists() && !rustTraversalDestination.exists() && !rustTraversalStaging.exists())

        val cTraversalDestination = File(root, "gate-c-traversal")
        val cTraversalStaging = File(root, ".gate-c-traversal-part")
        val cTraversal = executeLibarchiveRequest(
            context,
            traversalArchive,
            cTraversalDestination,
            cTraversalStaging,
            maximumEntries = 16,
            cancelAfterBytes = 0L,
        )
        check(cTraversal.exitCode != 0)
        check(!traversalEscape.exists() && !cTraversalDestination.exists() && !cTraversalStaging.exists())

        val symlinkArchive = File(root, "gate-symlink.tgz")
        writeSymlinkEscapeArchive(symlinkArchive)
        val outside = File(root, "outside").apply { check(mkdirs()) }
        val rustSymlinkDestination = File(root, "gate-rust-symlink")
        val rustSymlinkStaging = File(root, ".gate-rust-symlink-part")
        val rustSymlink = RustArchiveBridge.executeTar(
            gateRequest(symlinkArchive, rustSymlinkDestination, rustSymlinkStaging)
        )
        check(rustSymlink is NativeArchiveExecutionResult.Failure)
        check(!File(outside, "payload").exists())
        check(!rustSymlinkDestination.exists() && !rustSymlinkStaging.exists())

        val cSymlinkDestination = File(root, "gate-c-symlink")
        val cSymlinkStaging = File(root, ".gate-c-symlink-part")
        val cSymlink = executeLibarchiveRequest(
            context,
            symlinkArchive,
            cSymlinkDestination,
            cSymlinkStaging,
            maximumEntries = 16,
            cancelAfterBytes = 0L,
        )
        check(cSymlink.exitCode != 0)
        check(!File(outside, "payload").exists())
        check(!cSymlinkDestination.exists() && !cSymlinkStaging.exists())

        val cancellationSource = File(root, "pnpm.tgz")
        val rustCancelDestination = File(root, "gate-rust-cancel")
        val rustCancelStaging = File(root, ".gate-rust-cancel-part")
        val rustCancelled = RustArchiveBridge.executeTar(
            gateRequest(cancellationSource, rustCancelDestination, rustCancelStaging),
            cancelAfterBytes = 1L,
        )
        check(rustCancelled is NativeArchiveExecutionResult.Cancelled)
        check(!rustCancelDestination.exists() && !rustCancelStaging.exists())

        val cCancelDestination = File(root, "gate-c-cancel")
        val cCancelStaging = File(root, ".gate-c-cancel-part")
        val cCancelled = executeLibarchiveRequest(
            context,
            cancellationSource,
            cCancelDestination,
            cCancelStaging,
            maximumEntries = 1_000,
            cancelAfterBytes = 1L,
        )
        check(cCancelled.exitCode == 3)
        check(!cCancelDestination.exists() && !cCancelStaging.exists())

        val digestDestination = File(root, "gate-rust-digest")
        val digestStaging = File(root, ".gate-rust-digest-part")
        val digestMismatch = RustArchiveBridge.executeTar(
            gateRequest(cancellationSource, digestDestination, digestStaging).copy(
                expectedSha256 = "0".repeat(64),
            )
        )
        check(
            digestMismatch == NativeArchiveExecutionResult.Failure("native_archive_digest_mismatch")
        )
        check(!digestDestination.exists() && !digestStaging.exists())
        return "status=gates traversal=true symlink_escape=true cancellation=true " +
            "digest_rejection=true cleanup=true"
    }

    private fun gateRequest(
        source: File,
        destination: File,
        staging: File,
    ): RustTarArchiveRequest = RustTarArchiveRequest(
        source = source,
        destination = destination,
        stagingDirectory = staging,
        maximumArchiveBytes = source.length(),
        maximumEntries = 1_000,
        maximumTotalBytes = 32L * 1024L * 1024L,
        maximumFileBytes = 16L * 1024L * 1024L,
        maximumDepth = 16,
        maximumExpansionRatio = 64,
        expectedArchiveBytes = source.length(),
        expectedSha256 = sha256(source.toPath()),
        compression = RustTarCompression.GZIP,
        specialEntryPolicy = RustTarSpecialEntryPolicy.REJECT,
    )

    private fun writeTraversalArchive(target: File) {
        writeTar(target) { tar ->
            val payload = "escape".toByteArray()
            val entry = TarArchiveEntry("../escape-marker").apply { size = payload.size.toLong() }
            tar.putArchiveEntry(entry)
            tar.write(payload)
            tar.closeArchiveEntry()
        }
    }

    private fun writeSymlinkEscapeArchive(target: File) {
        writeTar(target) { tar ->
            val link = TarArchiveEntry("pivot", TarConstants.LF_SYMLINK).apply {
                linkName = "../outside"
            }
            tar.putArchiveEntry(link)
            tar.closeArchiveEntry()
            val payload = "escape".toByteArray()
            val entry = TarArchiveEntry("pivot/payload").apply { size = payload.size.toLong() }
            tar.putArchiveEntry(entry)
            tar.write(payload)
            tar.closeArchiveEntry()
        }
    }

    private fun writeTar(target: File, writeEntries: (TarArchiveOutputStream) -> Unit) {
        GzipCompressorOutputStream(target.outputStream().buffered()).use { gzip ->
            TarArchiveOutputStream(gzip).use { tar ->
                writeEntries(tar)
                tar.finish()
            }
        }
    }

    private fun executeProot(context: Context, fixture: Fixture, destination: File): ProotExecution {
        val containerDestination = "$CONTAINER_ROOT/${destination.name}"
        val containerSource = "$CONTAINER_ROOT/${fixture.id}.${fixture.compression.assetExtension}"
        val command =
            "set -e; stage='${containerDestination}.part'; " +
                "rm -rf \"${'$'}stage\" '$containerDestination'; mkdir -p \"${'$'}stage\"; " +
                "tar --no-same-owner ${fixture.compression.prootFlag} '$containerSource' " +
                "-C \"${'$'}stage\"; " +
                "mv \"${'$'}stage\" '$containerDestination'"
        val started = SystemClock.elapsedRealtime()
        val config = WorkSurfaceRuntimeBridge.buildArgvExecConfig(
            context = context,
            argv = listOf("/bin/bash", "-lc", command),
        )
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
            elapsedMs = SystemClock.elapsedRealtime() - started,
            exitCode = if (finished) process.exitValue() else -1,
            stderr = stderr.toString(Charsets.UTF_8.name()),
        )
    }

    private fun fingerprint(root: File): TreeFingerprint {
        check(root.isDirectory) { "fingerprint_root_missing" }
        val records = mutableListOf<String>()
        var directories = 0
        var regularFiles = 0
        var symbolicLinks = 0
        var otherEntries = 0
        var regularBytes = 0L
        Files.walkFileTree(root.toPath(), object : SimpleFileVisitor<Path>() {
            override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                if (dir != root.toPath()) {
                    directories += 1
                    records += "d|${relative(root, dir)}|${mode(dir)}"
                }
                return FileVisitResult.CONTINUE
            }

            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                val relative = relative(root, file)
                when {
                    attrs.isSymbolicLink -> {
                        symbolicLinks += 1
                        records += "l|$relative|${Files.readSymbolicLink(file)}"
                    }
                    attrs.isRegularFile -> {
                        regularFiles += 1
                        regularBytes += attrs.size()
                        records += "f|$relative|${mode(file)}|${attrs.size()}|${sha256(file)}"
                    }
                    else -> {
                        otherEntries += 1
                        records += "o|$relative|${mode(file)}"
                    }
                }
                return FileVisitResult.CONTINUE
            }
        })
        records.sort()
        return TreeFingerprint(
            digest = sha256(records.joinToString("\n").toByteArray(Charsets.UTF_8)),
            directories = directories,
            regularFiles = regularFiles,
            symbolicLinks = symbolicLinks,
            otherEntries = otherEntries,
            regularBytes = regularBytes,
            records = records,
        )
    }

    private fun describeTreeDifference(
        actual: TreeFingerprint,
        expected: TreeFingerprint,
    ): String {
        val actualRecords = actual.records.toHashSet()
        val expectedRecords = expected.records.toHashSet()
        val actualOnly = actual.records.firstOrNull { it !in expectedRecords } ?: "none"
        val expectedOnly = expected.records.firstOrNull { it !in actualRecords } ?: "none"
        return "actual=${actual.digest.take(16)}" +
            ",expected=${expected.digest.take(16)}" +
            ",counts=${actual.directories}/${actual.regularFiles}/${actual.symbolicLinks}" +
            ":${expected.directories}/${expected.regularFiles}/${expected.symbolicLinks}" +
            ",bytes=${actual.regularBytes}:${expected.regularBytes}" +
            ",actual_only=$actualOnly,expected_only=$expectedOnly"
    }

    private fun renderFixture(
        fixture: Fixture,
        sourceBytes: Long,
        outcomes: Map<Engine, EngineOutcome>,
    ): String = buildString {
        append("status=fixture id=${fixture.id} source_bytes=$sourceBytes entries=${fixture.expectedEntries}")
        Engine.entries.forEach { engine ->
            val outcome = outcomes[engine] ?: return@forEach
            append(" ${engine.id}_ms=${outcome.elapsedMs}")
            if (outcome.failure == null) {
                val fingerprint = checkNotNull(outcome.fingerprint)
                append(" ${engine.id}_status=ok")
                append(" ${engine.id}_output_bytes=${fingerprint.regularBytes}")
                append(" ${engine.id}_digest=${fingerprint.digest.take(16)}")
            } else {
                append(" ${engine.id}_status=failed")
                append(" ${engine.id}_reason=${safe(outcome.failure)}")
            }
        }
    }

    private fun copyAsset(context: Context, assetPath: String, target: File) {
        target.parentFile?.mkdirs()
        context.assets.open(assetPath).use { input ->
            target.outputStream().buffered().use(input::copyTo)
        }
    }

    private fun deleteTree(root: File) {
        if (!Files.exists(root.toPath(), LinkOption.NOFOLLOW_LINKS)) return
        Files.walkFileTree(root.toPath(), object : SimpleFileVisitor<Path>() {
            override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                dir.toFile().setWritable(true, true)
                return FileVisitResult.CONTINUE
            }

            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                Files.deleteIfExists(file)
                return FileVisitResult.CONTINUE
            }

            override fun postVisitDirectory(dir: Path, error: java.io.IOException?): FileVisitResult {
                if (error != null) throw error
                Files.deleteIfExists(dir)
                return FileVisitResult.CONTINUE
            }
        })
    }

    private fun relative(root: File, path: Path): String =
        root.toPath().relativize(path).toString().replace(File.separatorChar, '/')

    private fun mode(path: Path): Int = Os.lstat(path.toString()).st_mode and 0x1FF

    private fun sha256(path: Path): String = Files.newInputStream(path).use { input ->
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            if (count > 0) digest.update(buffer, 0, count)
        }
        digest.digest().toHex()
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .toHex()

    private fun ByteArray.toHex(): String = joinToString("") { byte -> "%02x".format(byte) }

    private fun safe(value: String): String = value.take(240).map { character ->
        if (character.isLetterOrDigit() || character in "-_.:=/%") character else '_'
    }.joinToString("")

    private data class Fixture(
        val id: String,
        val assetPath: String,
        val expectedEntries: Int,
        val maximumEntries: Int,
        val expectedSha256: String,
        val compression: TarCompression,
        val engines: List<Engine>,
    )

    private enum class TarCompression(
        val assetExtension: String,
        val prootFlag: String,
        val rust: RustTarCompression,
    ) {
        GZIP("tgz", "-xzf", RustTarCompression.GZIP),
        XZ("tar.xz", "-xJf", RustTarCompression.XZ),
    }

    private enum class Engine(val id: String) {
        KOTLIN("kotlin"),
        RUST("rust"),
        LIBARCHIVE_C("libarchive_c"),
        PROOT("proot"),
    }

    private data class EngineOutcome(
        val engine: Engine,
        val elapsedMs: Long,
        val fingerprint: TreeFingerprint?,
        val failure: String?,
    )

    private data class TreeFingerprint(
        val digest: String,
        val directories: Int,
        val regularFiles: Int,
        val symbolicLinks: Int,
        val otherEntries: Int,
        val regularBytes: Long,
        val records: List<String>,
    )

    private data class ProotExecution(
        val elapsedMs: Long,
        val exitCode: Int,
        val stderr: String,
    )

    private data class NativeProcessExecution(
        val exitCode: Int,
        val stdout: String,
        val stderr: String,
        val declaredBytes: Long,
    )
}
