package com.kite.app.foundation.runtime

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.SystemClock
import android.system.Os
import android.util.Log
import com.kite.app.foundation.contracts.ContainerExecConfig
import com.kite.app.foundation.workspace.WorkSurfaceRuntimeBridge
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.thread
import kotlin.math.ceil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Debug-only 固定 PRoot 二进制 A/B；不接受外部命令、路径、并发、轮数或 runtime 选择。 */
class ProotActiveRuntimeBenchmarkReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION) return
        startDebugService(
            context = context,
            service = Intent(context, ProotActiveRuntimeBenchmarkService::class.java),
            suite = "rf1420_proot_active_runtime",
        )
    }

    internal companion object {
        const val ACTION = "com.kite.app.debug.PROOT_ACTIVE_RUNTIME_BENCHMARK"
        const val LOG_TAG = "[KFShell]ProotActiveRuntime"

        fun safe(value: String): String = value.take(220).map { character ->
            if (character.isLetterOrDigit() || character in "-_.:=/%") character else '_'
        }.joinToString("")

        fun startDebugService(context: Context, service: Intent, suite: String) {
            runCatching { context.startService(service) }
                .onFailure { error ->
                    Log.e(
                        LOG_TAG,
                        "status=rejected suite=$suite requiresForeground=true " +
                            "reason=${safe(error.message ?: error.javaClass.simpleName)}",
                    )
                }
        }
    }
}

class ProotActiveRuntimeBenchmarkService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!running.compareAndSet(false, true)) return START_NOT_STICKY
        scope.launch {
            try {
                ProotActiveRuntimeBenchmark.run(applicationContext).forEach { report ->
                    Log.i(ProotActiveRuntimeBenchmarkReceiver.LOG_TAG, report)
                }
            } catch (error: Throwable) {
                Log.e(
                    ProotActiveRuntimeBenchmarkReceiver.LOG_TAG,
                    "status=failed reason=${ProotActiveRuntimeBenchmarkReceiver.safe(error.message ?: error.javaClass.simpleName)}",
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

/** Debug-only RF1430 热点拆分；固定比较默认扩展和 active registry，不接受外部参数。 */
class ProotActiveRuntimeHotspotReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION) return
        ProotActiveRuntimeBenchmarkReceiver.startDebugService(
            context = context,
            service = Intent(context, ProotActiveRuntimeHotspotService::class.java),
            suite = "rf1430_proot_active_hotspot",
        )
    }

    internal companion object {
        const val ACTION = "com.kite.app.debug.PROOT_ACTIVE_RUNTIME_HOTSPOT"
    }
}

class ProotActiveRuntimeHotspotService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!running.compareAndSet(false, true)) return START_NOT_STICKY
        scope.launch {
            try {
                ProotActiveRuntimeBenchmark.runHotspot(applicationContext).forEach { report ->
                    Log.i(ProotActiveRuntimeBenchmarkReceiver.LOG_TAG, report)
                }
            } catch (error: Throwable) {
                Log.e(
                    ProotActiveRuntimeBenchmarkReceiver.LOG_TAG,
                    "status=failed suite=rf1430_proot_active_hotspot " +
                        "reason=${ProotActiveRuntimeBenchmarkReceiver.safe(error.message ?: error.javaClass.simpleName)}",
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

/** Debug-only RF1432 正式补丁逐层消融；候选二进制必须预先部署到固定应用私有路径。 */
class ProotPatchAblationBenchmarkReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION) return
        ProotActiveRuntimeBenchmarkReceiver.startDebugService(
            context = context,
            service = Intent(context, ProotPatchAblationBenchmarkService::class.java),
            suite = "rf1432_proot_patch_ablation",
        )
    }

    internal companion object {
        const val ACTION = "com.kite.app.debug.PROOT_PATCH_ABLATION_BENCHMARK"
    }
}

class ProotPatchAblationBenchmarkService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!running.compareAndSet(false, true)) return START_NOT_STICKY
        scope.launch {
            try {
                ProotActiveRuntimeBenchmark.runPatchAblation(applicationContext).forEach { report ->
                    Log.i(ProotActiveRuntimeBenchmarkReceiver.LOG_TAG, report)
                }
            } catch (error: Throwable) {
                Log.e(
                    ProotActiveRuntimeBenchmarkReceiver.LOG_TAG,
                    "status=failed suite=rf1432_proot_patch_ablation " +
                        "reason=${ProotActiveRuntimeBenchmarkReceiver.safe(error.message ?: error.javaClass.simpleName)}",
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

private object ProotActiveRuntimeBenchmark {
    private const val TOKEN = "KITE_PROOT_RF1420_OK"
    private const val STOCK_ASSET = "proot/proot-arm64"
    private const val ACTIVE_SHA256 = "9A599F91A089EF05AB774AC5272745A813285C791F62CFA72824BBDBABBF88F0"
    private const val STOCK_SHA256 = "125DFF2415AE1DCB8B1AE97C51357DE73EF11F28268B86CD50A0F13AA1C3EA91"
    private const val ROUNDS = 3
    private const val HOTSPOT_ROUNDS = 9
    private const val PROCESS_TIMEOUT_MS = 30_000L
    private const val FUTURE_TIMEOUT_MS = 40_000L
    private const val OUTPUT_LIMIT = 64L * 1024L
    private val CONCURRENCY_LEVELS = listOf(1, 4, 8)
    private val HOTSPOT_CONCURRENCY_LEVELS = listOf(4, 8)
    private val TELEMETRY_KEYS = setOf(
        "KF_PROOT_TELEMETRY_MODE",
        "KF_PROOT_TELEMETRY_PATH",
        "KF_PROOT_ACTIVE_REGISTRY_ROOT",
    )
    private val sampleSequence = AtomicLong(0L)

    private enum class Variant(val label: String) {
        ACTIVE_TELEMETRY("active_telemetry"),
        ACTIVE_TELEMETRY_LOG_ONLY("active_telemetry_log_only"),
        ACTIVE_TELEMETRY_LOG_SHARDED("active_telemetry_log_sharded"),
        ACTIVE_NO_TELEMETRY("active_no_telemetry"),
        ACTIVE_NO_TELEMETRY_NO_PROCFS("active_no_telemetry_no_procfs"),
        ACTIVE_NO_TELEMETRY_NO_MOUNTINFO("active_no_telemetry_no_mountinfo"),
        ACTIVE_NO_TELEMETRY_MINIMAL("active_no_telemetry_minimal"),
        ACTIVE_NO_TELEMETRY_EXTERNAL_LOADER("active_no_telemetry_external_loader"),
        ABLATION_BASE("patch_00_base"),
        ABLATION_LIFECYCLE("patch_01_lifecycle"),
        ABLATION_PROCFS("patch_02_procfs"),
        ABLATION_TRANSACTION("patch_03_transaction"),
        ABLATION_PROTECTION("patch_04_protection"),
        ABLATION_VIEW("patch_05_view"),
        ABLATION_UNBUNDLED("patch_06_unbundled"),
        ABLATION_NDK28("patch_07_ndk28"),
        STOCK_NO_TELEMETRY("stock_no_telemetry"),
    }

    private val BASE_VARIANTS = listOf(
        Variant.ACTIVE_TELEMETRY,
        Variant.ACTIVE_NO_TELEMETRY,
        Variant.STOCK_NO_TELEMETRY,
    )

    private val SMALL_WRITE_HOTSPOT_VARIANTS = listOf(
        Variant.ACTIVE_NO_TELEMETRY,
        Variant.ACTIVE_NO_TELEMETRY_NO_PROCFS,
        Variant.ACTIVE_NO_TELEMETRY_NO_MOUNTINFO,
        Variant.ACTIVE_NO_TELEMETRY_MINIMAL,
        Variant.ACTIVE_NO_TELEMETRY_EXTERNAL_LOADER,
        Variant.STOCK_NO_TELEMETRY,
    )

    private val CHILD_FANOUT_HOTSPOT_VARIANTS = listOf(
        Variant.ACTIVE_TELEMETRY,
        Variant.ACTIVE_TELEMETRY_LOG_ONLY,
        Variant.ACTIVE_TELEMETRY_LOG_SHARDED,
        Variant.ACTIVE_NO_TELEMETRY,
        Variant.STOCK_NO_TELEMETRY,
    )

    private data class AblationRuntime(
        val variant: Variant,
        val fileName: String,
        val sha256: String,
    )

    private val ABLATION_RUNTIMES = listOf(
        AblationRuntime(Variant.ABLATION_BASE, "proot-rf1432-00-base", "F8BD91DE272733B30ECC222D1BD38E924242A3DBCD28F3B51A04E1F42022E251"),
        AblationRuntime(Variant.ABLATION_LIFECYCLE, "proot-rf1432-01-lifecycle", "9435B333DA2AFBE1031D7CE926A9AD5EA733F8E5650DC20A6E089C241568E6CF"),
        AblationRuntime(Variant.ABLATION_PROCFS, "proot-rf1432-02-procfs", "DFEB842ADB5C2FB41991110AE67A79299CA874F8E22A338F171371C617717C88"),
        AblationRuntime(Variant.ABLATION_TRANSACTION, "proot-rf1432-03-transaction", "E52501DA61EFA14972E0FEEF38EC6576EFC2D1C7D1F895498A7E1F9F3F2E6D5A"),
        AblationRuntime(Variant.ABLATION_PROTECTION, "proot-rf1432-04-protection", "DC57AE34026C39D71B162A84142E840075E0B7C3673417520B6B2992A9328B28"),
        AblationRuntime(Variant.ABLATION_VIEW, "proot-rf1432-05-view", "7B1B4C5C4A370D03B46907A47FBCBDF12E0386ED5173ADE088ACE37401004247"),
        AblationRuntime(Variant.ABLATION_UNBUNDLED, "proot-rf1432-06-unbundled", "205C06FA726ADF4535C6A237A910A4CBCF8B6055EAA63367392F483C3EE6AA1A"),
        AblationRuntime(Variant.ABLATION_NDK28, "proot-rf1432-07-ndk28", "57778BB2D8BBF65E387B5755266EBCC95C6EFF53E8C65F1AADFC9C6549B1769B"),
    )

    private val PATCH_ABLATION_VARIANTS = ABLATION_RUNTIMES.map(AblationRuntime::variant) +
        listOf(Variant.ACTIVE_NO_TELEMETRY, Variant.STOCK_NO_TELEMETRY)

    private enum class Workload(val label: String) {
        STARTUP("startup"),
        SHELL("shell"),
        METADATA("metadata"),
        SMALL_WRITE("small_write"),
        CHILD_FANOUT("child_fanout"),
    }

    private data class BenchmarkWorkspace(
        val hostRoot: File,
        val hostMetadataRoot: File,
        val hostWriteRoot: File,
        val hostTelemetryFile: File,
        val hostRegistryRoot: File,
        val containerRoot: String,
    ) {
        val containerMetadataRoot: String get() = "$containerRoot/metadata"
        val containerWriteRoot: String get() = "$containerRoot/write"
    }

    private data class RuntimeAssets(
        val active: File,
        val stock: File,
        val loader: File,
        val loader32: File,
        val ablation: Map<Variant, File> = emptyMap(),
    )

    private data class PreparedConfig(
        val config: ContainerExecConfig,
        val workload: Workload,
        val cleanupDirectory: File? = null,
    )

    private data class Execution(
        val durationMs: Long,
        val exitCode: Int,
        val stdout: String,
        val stderr: String,
        val residual: Boolean,
        val reason: String,
    )

    private data class Batch(
        val wallMs: Long,
        val executions: List<Execution>,
    )

    private data class CaseKey(
        val workload: Workload,
        val variant: Variant,
        val concurrency: Int,
    )

    fun run(context: Context): List<String> {
        val workspace = prepareWorkspace(context, "proot-overhead-rf1420")
        return try {
            val identityConfig = WorkSurfaceRuntimeBridge.buildArgvExecConfig(
                context = context,
                argv = listOf("/bin/true"),
            )
            val assets = prepareRuntimeAssets(context, identityConfig)
            warmup(context, workspace, assets)

            val batches = linkedMapOf<CaseKey, MutableList<Batch>>()
            Workload.entries.forEachIndexed { workloadIndex, workload ->
                CONCURRENCY_LEVELS.forEachIndexed { levelIndex, concurrency ->
                    repeat(ROUNDS) { round ->
                        val offset = (workloadIndex + levelIndex + round) % BASE_VARIANTS.size
                        val variants = BASE_VARIANTS.drop(offset) + BASE_VARIANTS.take(offset)
                        variants.forEach { variant ->
                            val configs = prepareConfigs(
                                context = context,
                                workspace = workspace,
                                assets = assets,
                                workload = workload,
                                variant = variant,
                                count = concurrency,
                            )
                            val batch = runBatch(configs)
                            batches.getOrPut(CaseKey(workload, variant, concurrency)) { mutableListOf() } += batch
                            Thread.sleep(35L)
                        }
                    }
                }
            }

            buildList {
                add(
                    "status=started suite=rf1420_proot_active_runtime rounds=$ROUNDS " +
                        "levels=${CONCURRENCY_LEVELS.joinToString(",")} activeSha256=$ACTIVE_SHA256 stockSha256=$STOCK_SHA256"
                )
                Workload.entries.forEach { workload ->
                    CONCURRENCY_LEVELS.forEach { concurrency ->
                        BASE_VARIANTS.forEach { variant ->
                            val key = CaseKey(workload, variant, concurrency)
                            val measured = checkNotNull(batches[key])
                            val executions = measured.flatMap(Batch::executions)
                            val failures = executions.count { !succeeded(workload, it) }
                            val residual = executions.count(Execution::residual)
                            check(failures == 0) {
                                "rf1420_${workload.label}_${variant.label}_${concurrency}_${executions.firstOrNull { !succeeded(workload, it) }?.reason}"
                            }
                            check(residual == 0) {
                                "rf1420_residual_${workload.label}_${variant.label}_${concurrency}_$residual"
                            }
                            add(
                                "status=ok case=${workload.label} variant=${variant.label} " +
                                    "concurrency=$concurrency rounds=$ROUNDS " +
                                    "wallMedianMs=${median(measured.map(Batch::wallMs))} " +
                                    "wallSamplesMs=${measured.joinToString(",") { it.wallMs.toString() }} " +
                                    "p50Ms=${percentile(executions.map(Execution::durationMs), 0.50)} " +
                                    "p95Ms=${percentile(executions.map(Execution::durationMs), 0.95)} " +
                                    "failures=$failures residual=$residual"
                            )
                        }
                    }
                }
                add(
                    "status=telemetry_sink bytes=${workspace.hostTelemetryFile.length()} " +
                        "rotations=${workspace.hostTelemetryFile.parentFile?.listFiles()?.count { it.name.startsWith(workspace.hostTelemetryFile.name + ".") } ?: 0}"
                )
                add("status=complete suite=rf1420_proot_active_runtime cases=${Workload.entries.size * CONCURRENCY_LEVELS.size * BASE_VARIANTS.size}")
            }
        } finally {
            workspace.hostRoot.deleteRecursively()
        }
    }

    fun runHotspot(context: Context): List<String> {
        val workspace = prepareWorkspace(context, "proot-hotspot-rf1430")
        return try {
            val identityConfig = WorkSurfaceRuntimeBridge.buildArgvExecConfig(
                context = context,
                argv = listOf("/bin/true"),
            )
            val assets = prepareRuntimeAssets(context, identityConfig)
            val cases = linkedMapOf(
                Workload.SMALL_WRITE to SMALL_WRITE_HOTSPOT_VARIANTS,
                Workload.CHILD_FANOUT to CHILD_FANOUT_HOTSPOT_VARIANTS,
            )
            warmup(context, workspace, assets, cases.values.flatten().distinct())

            val batches = linkedMapOf<CaseKey, MutableList<Batch>>()
            cases.entries.forEachIndexed { workloadIndex, (workload, declaredVariants) ->
                HOTSPOT_CONCURRENCY_LEVELS.forEachIndexed { levelIndex, concurrency ->
                    repeat(HOTSPOT_ROUNDS) { round ->
                        val offset = (workloadIndex + levelIndex + round) % declaredVariants.size
                        val variants = declaredVariants.drop(offset) + declaredVariants.take(offset)
                        variants.forEach { variant ->
                            val configs = prepareConfigs(
                                context = context,
                                workspace = workspace,
                                assets = assets,
                                workload = workload,
                                variant = variant,
                                count = concurrency,
                            )
                            val batch = runBatch(configs)
                            batches.getOrPut(CaseKey(workload, variant, concurrency)) { mutableListOf() } += batch
                            Thread.sleep(35L)
                        }
                    }
                }
            }

            buildList {
                add(
                    "status=started suite=rf1430_proot_active_hotspot rounds=$HOTSPOT_ROUNDS " +
                        "levels=${HOTSPOT_CONCURRENCY_LEVELS.joinToString(",")} " +
                        "activeSha256=$ACTIVE_SHA256 stockSha256=$STOCK_SHA256"
                )
                cases.forEach { (workload, variants) ->
                    HOTSPOT_CONCURRENCY_LEVELS.forEach { concurrency ->
                        variants.forEach { variant ->
                            val key = CaseKey(workload, variant, concurrency)
                            val measured = checkNotNull(batches[key])
                            val executions = measured.flatMap(Batch::executions)
                            val failures = executions.count { !succeeded(workload, it) }
                            val residual = executions.count(Execution::residual)
                            check(failures == 0) {
                                "rf1430_${workload.label}_${variant.label}_${concurrency}_" +
                                    executions.firstOrNull { !succeeded(workload, it) }?.reason
                            }
                            check(residual == 0) {
                                "rf1430_residual_${workload.label}_${variant.label}_${concurrency}_$residual"
                            }
                            add(
                                "status=ok suite=rf1430_proot_active_hotspot case=${workload.label} " +
                                    "variant=${variant.label} concurrency=$concurrency rounds=$HOTSPOT_ROUNDS " +
                                    "wallMedianMs=${median(measured.map(Batch::wallMs))} " +
                                    "wallSamplesMs=${measured.joinToString(",") { it.wallMs.toString() }} " +
                                    "p50Ms=${percentile(executions.map(Execution::durationMs), 0.50)} " +
                                    "p95Ms=${percentile(executions.map(Execution::durationMs), 0.95)} " +
                                    "failures=$failures residual=$residual"
                            )
                        }
                    }
                }
                add(
                    "status=telemetry_sink suite=rf1430_proot_active_hotspot " +
                        "bytes=${workspace.hostTelemetryFile.length()} " +
                        "rotations=${workspace.hostTelemetryFile.parentFile?.listFiles()?.count { it.name.startsWith(workspace.hostTelemetryFile.name + ".") } ?: 0}"
                )
                add(
                    "status=complete suite=rf1430_proot_active_hotspot " +
                        "cases=${cases.values.sumOf { it.size } * HOTSPOT_CONCURRENCY_LEVELS.size}"
                )
            }
        } finally {
            workspace.hostRoot.deleteRecursively()
        }
    }

    fun runPatchAblation(context: Context): List<String> {
        val workspace = prepareWorkspace(context, "proot-patch-ablation-rf1432")
        return try {
            val identityConfig = WorkSurfaceRuntimeBridge.buildArgvExecConfig(
                context = context,
                argv = listOf("/bin/true"),
            )
            val assets = prepareRuntimeAssets(context, identityConfig, requireAblation = true)
            warmup(context, workspace, assets, PATCH_ABLATION_VARIANTS)
            val batches = linkedMapOf<CaseKey, MutableList<Batch>>()

            HOTSPOT_CONCURRENCY_LEVELS.forEachIndexed { levelIndex, concurrency ->
                repeat(HOTSPOT_ROUNDS) { round ->
                    val offset = (levelIndex + round) % PATCH_ABLATION_VARIANTS.size
                    val variants = PATCH_ABLATION_VARIANTS.drop(offset) + PATCH_ABLATION_VARIANTS.take(offset)
                    variants.forEach { variant ->
                        val batch = runBatch(
                            prepareConfigs(
                                context = context,
                                workspace = workspace,
                                assets = assets,
                                workload = Workload.SMALL_WRITE,
                                variant = variant,
                                count = concurrency,
                            ),
                        )
                        batches.getOrPut(CaseKey(Workload.SMALL_WRITE, variant, concurrency)) { mutableListOf() } += batch
                        Thread.sleep(35L)
                    }
                }
            }

            buildList {
                add(
                    "status=started suite=rf1432_proot_patch_ablation rounds=$HOTSPOT_ROUNDS " +
                        "levels=${HOTSPOT_CONCURRENCY_LEVELS.joinToString(",")} " +
                        "activeSha256=$ACTIVE_SHA256 " +
                        "stockSha256=$STOCK_SHA256"
                )
                ABLATION_RUNTIMES.forEach { runtime ->
                    add(
                        "status=identity suite=rf1432_proot_patch_ablation " +
                            "variant=${runtime.variant.label} sha256=${runtime.sha256}"
                    )
                }
                HOTSPOT_CONCURRENCY_LEVELS.forEach { concurrency ->
                    PATCH_ABLATION_VARIANTS.forEach { variant ->
                        val measured = checkNotNull(
                            batches[CaseKey(Workload.SMALL_WRITE, variant, concurrency)]
                        )
                        val executions = measured.flatMap(Batch::executions)
                        val failures = executions.count { !succeeded(Workload.SMALL_WRITE, it) }
                        val residual = executions.count(Execution::residual)
                        check(failures == 0 && residual == 0) {
                            "rf1432_small_write_${variant.label}_${concurrency}_failed"
                        }
                        add(
                            "status=ok suite=rf1432_proot_patch_ablation case=small_write " +
                                "variant=${variant.label} concurrency=$concurrency rounds=$HOTSPOT_ROUNDS " +
                                "wallMedianMs=${median(measured.map(Batch::wallMs))} " +
                                "wallSamplesMs=${measured.joinToString(",") { it.wallMs.toString() }} " +
                                "p50Ms=${percentile(executions.map(Execution::durationMs), 0.50)} " +
                                "p95Ms=${percentile(executions.map(Execution::durationMs), 0.95)} " +
                                "failures=$failures residual=$residual"
                        )
                    }
                }
                add(
                    "status=complete suite=rf1432_proot_patch_ablation " +
                        "cases=${PATCH_ABLATION_VARIANTS.size * HOTSPOT_CONCURRENCY_LEVELS.size}"
                )
            }
        } finally {
            workspace.hostRoot.deleteRecursively()
        }
    }

    private fun prepareWorkspace(context: Context, suiteDirectory: String): BenchmarkWorkspace {
        val container = WorkSurfaceRuntimeBridge.ensureDefaultContainer(context)
        val hostRoot = File(container.workspacePath, ".kf/system/bench/$suiteDirectory")
            .absoluteFile.normalize()
        val allowed = File(container.workspacePath, ".kf/system/bench").absoluteFile.normalize()
        check(hostRoot.toPath().startsWith(allowed.toPath())) { "rf1420_workspace_invalid" }
        hostRoot.deleteRecursively()
        val metadata = File(hostRoot, "metadata")
        val writes = File(hostRoot, "write")
        val registry = File(hostRoot, "active-registry")
        check(metadata.mkdirs() && writes.mkdirs() && registry.mkdirs()) {
            "rf1420_workspace_create_failed"
        }
        val telemetry = File(hostRoot, "telemetry.jsonl")
        check(telemetry.createNewFile()) { "rf1420_telemetry_create_failed" }
        repeat(512) { index ->
            File(metadata, "file-${index.toString().padStart(4, '0')}.txt")
                .writeText("kite-rf1420-$index\n")
        }
        return BenchmarkWorkspace(
            hostRoot = hostRoot,
            hostMetadataRoot = metadata,
            hostWriteRoot = writes,
            hostTelemetryFile = telemetry,
            hostRegistryRoot = registry,
            containerRoot = "/workspace/.kf/system/bench/$suiteDirectory",
        )
    }

    private fun prepareRuntimeAssets(
        context: Context,
        identityConfig: ContainerExecConfig,
        requireAblation: Boolean = false,
    ): RuntimeAssets {
        val active = File(checkNotNull(identityConfig.command.firstOrNull())).canonicalFile
        check(active.isFile && active.canExecute()) { "rf1420_active_missing" }
        check(sha256(active) == ACTIVE_SHA256) { "rf1420_active_identity_mismatch" }
        val runtimeRoot = checkNotNull(active.parentFile?.parentFile) { "rf1420_runtime_root_missing" }
        val loader = File(runtimeRoot, "libexec/proot/loader").canonicalFile
        val loader32 = File(runtimeRoot, "libexec/proot/loader32").canonicalFile
        check(loader.isFile && loader.canExecute()) { "rf1420_loader_missing" }
        check(loader32.isFile && loader32.canExecute()) { "rf1420_loader32_missing" }

        val debugRoot = File(context.filesDir, "runtime/debug/proot-overhead-rf1420")
        check(debugRoot.mkdirs() || debugRoot.isDirectory) { "rf1420_debug_root_create_failed" }
        val stock = File(debugRoot, "proot-stock-arm64")
        val packaged = context.assets.open(STOCK_ASSET).use { input -> input.readBytes() }
        check(sha256(packaged) == STOCK_SHA256) { "rf1420_stock_asset_identity_mismatch" }
        if (!stock.isFile || sha256(stock) != STOCK_SHA256) {
            val pending = File(debugRoot, ".proot-stock-${System.nanoTime()}.pending")
            try {
                FileOutputStream(pending).use { output ->
                    output.write(packaged)
                    output.fd.sync()
                }
                if (stock.exists()) check(stock.delete()) { "rf1420_stock_replace_failed" }
                check(pending.renameTo(stock)) { "rf1420_stock_publish_failed" }
            } finally {
                if (pending.exists()) pending.delete()
            }
        }
        Os.chmod(stock.absolutePath, 0b111101101)
        check(stock.canExecute() && sha256(stock) == STOCK_SHA256) { "rf1420_stock_unusable" }
        val ablation = if (requireAblation) {
            ABLATION_RUNTIMES.associate { runtime ->
                val file = File(debugRoot, runtime.fileName)
                check(file.isFile && sha256(file) == runtime.sha256) {
                    "rf1432_ablation_identity_mismatch_${runtime.variant.label}"
                }
                Os.chmod(file.absolutePath, 0b111101101)
                check(file.canExecute()) { "rf1432_ablation_unusable_${runtime.variant.label}" }
                runtime.variant to file
            }
        } else {
            emptyMap()
        }
        return RuntimeAssets(active, stock, loader, loader32, ablation)
    }

    private fun warmup(
        context: Context,
        workspace: BenchmarkWorkspace,
        assets: RuntimeAssets,
        variants: List<Variant> = BASE_VARIANTS,
    ) {
        variants.forEach { variant ->
            repeat(2) {
                val config = prepareConfigs(
                    context = context,
                    workspace = workspace,
                    assets = assets,
                    workload = Workload.SHELL,
                    variant = variant,
                    count = 1,
                ).single()
                val execution = execute(config)
                check(succeeded(Workload.SHELL, execution)) {
                    "rf1420_warmup_${variant.label}_${execution.reason}"
                }
            }
        }
    }

    private fun prepareConfigs(
        context: Context,
        workspace: BenchmarkWorkspace,
        assets: RuntimeAssets,
        workload: Workload,
        variant: Variant,
        count: Int,
    ): List<PreparedConfig> = List(count) { processIndex ->
        val invocation = invocation(workspace, workload)
        val base = WorkSurfaceRuntimeBridge.buildArgvExecConfig(
            context = context,
            argv = invocation.first,
        )
        PreparedConfig(
            config = applyVariant(
                base = base,
                assets = assets,
                workspace = workspace,
                variant = variant,
                telemetryShard = if (variant == Variant.ACTIVE_TELEMETRY_LOG_SHARDED) {
                    File(
                        workspace.hostRoot,
                        "telemetry-shards/${sampleSequence.incrementAndGet()}-$processIndex.jsonl",
                    ).also { shard ->
                        check(shard.parentFile?.mkdirs() == true || shard.parentFile?.isDirectory == true) {
                            "rf1430_telemetry_shard_root_failed"
                        }
                    }
                } else {
                    null
                },
            ),
            workload = workload,
            cleanupDirectory = invocation.second,
        )
    }

    private fun invocation(
        workspace: BenchmarkWorkspace,
        workload: Workload,
    ): Pair<List<String>, File?> = when (workload) {
        Workload.STARTUP -> listOf("/bin/true") to null
        Workload.SHELL -> listOf(
            "/bin/sh",
            "-c",
            "[ -d /proc ] && [ -d /workspace ] && printf '$TOKEN'",
        ) to null
        Workload.METADATA -> listOf(
            "/usr/bin/find",
            workspace.containerMetadataRoot,
            "-type",
            "f",
            "-printf",
            "x\\n",
        ) to null
        Workload.SMALL_WRITE -> {
            val id = sampleSequence.incrementAndGet()
            val hostDirectory = File(workspace.hostWriteRoot, "sample-$id")
            check(hostDirectory.mkdirs()) { "rf1420_write_directory_create_failed" }
            val containerDirectory = "${workspace.containerWriteRoot}/sample-$id"
            listOf(
                "/bin/sh",
                "-c",
                "set -eu; d=\"\$1\"; i=0; while [ \"\$i\" -lt 128 ]; do " +
                    "printf '%s' \"\$i\" > \"\$d/f-\$i\"; i=\$((i+1)); done; " +
                    "set -- \"\$d\"/*; [ \"\$#\" -eq 128 ]; printf '$TOKEN'",
                "kite-rf1420-write",
                containerDirectory,
            ) to hostDirectory
        }
        Workload.CHILD_FANOUT -> listOf(
            "/bin/sh",
            "-c",
            "set -eu; i=0; while [ \"\$i\" -lt 16 ]; do /bin/true; i=\$((i+1)); done; printf '$TOKEN'",
        ) to null
    }

    private fun applyVariant(
        base: ContainerExecConfig,
        assets: RuntimeAssets,
        workspace: BenchmarkWorkspace,
        variant: Variant,
        telemetryShard: File? = null,
    ): ContainerExecConfig {
        check(base.command.firstOrNull() == assets.active.absolutePath) { "rf1420_base_runtime_changed" }
        return when (variant) {
            Variant.ACTIVE_TELEMETRY -> base.copy(
                env = base.env + mapOf(
                    "KF_PROOT_TELEMETRY_PATH" to workspace.hostTelemetryFile.absolutePath,
                    "KF_PROOT_ACTIVE_REGISTRY_ROOT" to workspace.hostRegistryRoot.absolutePath,
                ),
            )
            Variant.ACTIVE_TELEMETRY_LOG_ONLY -> base.copy(
                env = (base.env - TELEMETRY_KEYS) + mapOf(
                    "KF_PROOT_TELEMETRY_PATH" to workspace.hostTelemetryFile.absolutePath,
                ),
            )
            Variant.ACTIVE_TELEMETRY_LOG_SHARDED -> base.copy(
                env = (base.env - TELEMETRY_KEYS) + mapOf(
                    "KF_PROOT_TELEMETRY_PATH" to checkNotNull(telemetryShard).absolutePath,
                ),
            )
            Variant.ACTIVE_NO_TELEMETRY -> base.copy(env = base.env - TELEMETRY_KEYS)
            Variant.ACTIVE_NO_TELEMETRY_NO_PROCFS -> base.copy(
                env = (base.env - TELEMETRY_KEYS) + mapOf("PROOT_NO_KF_PROCFS" to "1"),
            )
            Variant.ACTIVE_NO_TELEMETRY_NO_MOUNTINFO -> base.copy(
                env = (base.env - TELEMETRY_KEYS) + mapOf("PROOT_NO_MOUNTINFO" to "1"),
            )
            Variant.ACTIVE_NO_TELEMETRY_MINIMAL -> base.copy(
                env = (base.env - TELEMETRY_KEYS) + mapOf(
                    "PROOT_NO_KF_PROCFS" to "1",
                    "PROOT_NO_MOUNTINFO" to "1",
                ),
            )
            Variant.ACTIVE_NO_TELEMETRY_EXTERNAL_LOADER -> base.copy(
                env = (base.env - TELEMETRY_KEYS) + mapOf(
                    "PROOT_LOADER" to assets.loader.absolutePath,
                    "PROOT_LOADER_32" to assets.loader32.absolutePath,
                ),
            )
            Variant.ABLATION_BASE,
            Variant.ABLATION_LIFECYCLE,
            Variant.ABLATION_PROCFS,
            Variant.ABLATION_TRANSACTION,
            Variant.ABLATION_PROTECTION,
            Variant.ABLATION_VIEW,
            Variant.ABLATION_NDK28,
            -> base.copy(
                command = base.command.toMutableList().also {
                    it[0] = checkNotNull(assets.ablation[variant]).absolutePath
                },
                env = base.env - TELEMETRY_KEYS,
            )
            Variant.ABLATION_UNBUNDLED -> base.copy(
                command = base.command.toMutableList().also {
                    it[0] = checkNotNull(assets.ablation[variant]).absolutePath
                },
                env = (base.env - TELEMETRY_KEYS) + mapOf(
                    "PROOT_LOADER" to assets.loader.absolutePath,
                    "PROOT_LOADER_32" to assets.loader32.absolutePath,
                ),
            )
            Variant.STOCK_NO_TELEMETRY -> base.copy(
                command = base.command.toMutableList().also { it[0] = assets.stock.absolutePath },
                env = (base.env - TELEMETRY_KEYS) + mapOf(
                    "PROOT_LOADER" to assets.loader.absolutePath,
                    "PROOT_LOADER_32" to assets.loader32.absolutePath,
                ),
            )
        }
    }

    private fun runBatch(configs: List<PreparedConfig>): Batch {
        val startSignal = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(configs.size)
        val batchStarted = SystemClock.elapsedRealtime()
        return try {
            val futures = configs.map { config ->
                executor.submit(Callable {
                    startSignal.await()
                    execute(config)
                })
            }
            startSignal.countDown()
            val executions = futures.map { future ->
                future.get(FUTURE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            }
            Batch(
                wallMs = SystemClock.elapsedRealtime() - batchStarted,
                executions = executions,
            )
        } finally {
            executor.shutdownNow()
        }
    }

    private fun execute(prepared: PreparedConfig): Execution {
        val startedAt = SystemClock.elapsedRealtime()
        var process: Process? = null
        var stdoutReader: Thread? = null
        var stderrReader: Thread? = null
        val stdout = ByteArrayOutputStream()
        val stderr = ByteArrayOutputStream()
        return try {
            val started = ProcessBuilder(prepared.config.command)
                .redirectErrorStream(false)
                .apply { environment().putAll(prepared.config.env) }
                .start()
            process = started
            stdoutReader = thread(start = true, isDaemon = true, name = "ProotActiveOut") {
                runCatching { started.inputStream.use { it.copyTo(stdout, OUTPUT_LIMIT) } }
            }
            stderrReader = thread(start = true, isDaemon = true, name = "ProotActiveErr") {
                runCatching { started.errorStream.use { it.copyTo(stderr, OUTPUT_LIMIT) } }
            }
            val finished = started.waitFor(PROCESS_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            if (!finished) {
                started.destroyForcibly()
                started.waitFor(1_000L, TimeUnit.MILLISECONDS)
            }
            stdoutReader.join(1_000L)
            stderrReader.join(1_000L)
            val residual = started.isAlive
            val exitCode = if (finished && !residual) started.exitValue() else -1
            Execution(
                durationMs = SystemClock.elapsedRealtime() - startedAt,
                exitCode = exitCode,
                stdout = stdout.toString(Charsets.UTF_8.name()),
                stderr = stderr.toString(Charsets.UTF_8.name()),
                residual = residual,
                reason = when {
                    !finished -> "timeout"
                    residual -> "residual"
                    exitCode != 0 -> "exit_${exitCode}_${safe(stderr.toString(Charsets.UTF_8.name()))}"
                    else -> "none"
                },
            )
        } catch (error: Throwable) {
            process?.destroyForcibly()
            process?.waitFor(1_000L, TimeUnit.MILLISECONDS)
            Execution(
                durationMs = SystemClock.elapsedRealtime() - startedAt,
                exitCode = -1,
                stdout = stdout.toString(Charsets.UTF_8.name()),
                stderr = stderr.toString(Charsets.UTF_8.name()),
                residual = process?.isAlive == true,
                reason = safe(error.message ?: error.javaClass.simpleName),
            )
        } finally {
            prepared.cleanupDirectory?.deleteRecursively()
        }
    }

    private fun succeeded(workload: Workload, execution: Execution): Boolean {
        if (execution.exitCode != 0 || execution.residual) return false
        return when (workload) {
            Workload.STARTUP -> execution.stdout.isEmpty()
            Workload.METADATA -> execution.stdout.lineSequence().count { it == "x" } == 512
            Workload.SHELL,
            Workload.SMALL_WRITE,
            Workload.CHILD_FANOUT,
            -> execution.stdout == TOKEN
        }
    }

    private fun median(values: List<Long>): Long = percentile(values, 0.50)

    private fun percentile(values: List<Long>, ratio: Double): Long {
        require(values.isNotEmpty())
        val sorted = values.sorted()
        val index = ceil(sorted.size * ratio).toInt().coerceIn(1, sorted.size) - 1
        return sorted[index]
    }

    private fun sha256(file: File): String = file.inputStream().buffered().use { input ->
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            if (count > 0) digest.update(buffer, 0, count)
        }
        digest.digest().joinToString("") { byte -> "%02X".format(byte) }
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { byte -> "%02X".format(byte) }

    private fun safe(value: String): String = ProotActiveRuntimeBenchmarkReceiver.safe(value)

    private fun java.io.InputStream.copyTo(output: ByteArrayOutputStream, limit: Long) {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var remaining = limit
        while (remaining > 0L) {
            val count = read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
            if (count < 0) break
            if (count > 0) {
                output.write(buffer, 0, count)
                remaining -= count
            }
        }
    }
}
