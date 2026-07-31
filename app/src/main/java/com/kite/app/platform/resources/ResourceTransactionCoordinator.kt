package com.kite.app.platform.resources

import android.content.Context
import com.kite.app.application.fileprotection.FileProtectionBackendId
import com.kite.app.application.fileprotection.FileProtectionPhase
import com.kite.app.foundation.runtime.AssetExtractor
import com.kite.app.foundation.runtime.ProotFileProtectionRuntime
import com.kite.app.foundation.runtime.ProotViewLeaseMode
import com.kite.app.foundation.runtime.ProotViewRuntime
import com.kite.app.foundation.runtime.ProotViewState
import com.kite.app.foundation.runtime.ProotViewStore
import com.kite.app.foundation.workspace.WorkSurfaceRuntimeBridge
import com.kite.app.platform.fileprotection.AndroidFileProtectionRestoreGuard
import com.kite.app.platform.fileprotection.FileProtectionCoordinator
import com.kite.app.platform.fileprotection.FileProtectionRecord
import com.kite.app.platform.fileprotection.FileProtectionRestoreGuard
import com.kite.app.platform.fileprotection.LegacyFileProtectionRecordDecoder
import com.kite.app.platform.runtime.ProotViewProcessGuard
import com.kite.app.resources.KiteResourceInstallRecipes
import com.kite.app.resources.KiteResourceManifest
import com.kite.app.resources.KiteResourceManifestLoader
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

internal data class ResourceTransactionRestoreResult(
    val resourceId: String,
    val transactionId: String,
    val restoredVersion: String,
    val environmentId: String = ProotViewStore.DEFAULT_ENVIRONMENT_ID
)

internal data class ResourceInterruptedRecoveryResult(
    val resourceId: String,
    val restored: Boolean,
    val committed: Boolean = false,
    val targetVersion: String = "",
    val environmentId: String = ProotViewStore.DEFAULT_ENVIRONMENT_ID,
    val message: String
)

internal data class ResourceTransactionTarget(
    val resourceId: String,
    val rootHostPath: File
)

private enum class ResourceViewTransactionPhase {
    ACTIVE,
    VIEW_COMMITTED,
    COMPLETED,
    ROLLED_BACK,
    REPAIRED,
    FAILED
}

private data class ResourceViewTransactionRecord(
    val transactionId: String,
    val resourceId: String,
    val runInstanceId: String,
    val viewId: String,
    val parentViewId: String,
    val ownerId: String,
    val previousVersion: String,
    val targetVersion: String,
    val phase: ResourceViewTransactionPhase,
    val startedAt: Long,
    val updatedAt: Long,
    val lastError: String = "",
    // 事务归属的控制面环境。beginUpdate 写入，commit/rollback 只改本环境头，
    // 避免环境 A 的更新影响环境 B。旧记录缺失时视为 default，向后兼容。
    val environmentId: String = ProotViewStore.DEFAULT_ENVIRONMENT_ID
) {
    fun toJson(): JSONObject = JSONObject()
        .put("schema", RECORD_SCHEMA)
        .put("transactionId", transactionId)
        .put("resourceId", resourceId)
        .put("runInstanceId", runInstanceId)
        .put("viewId", viewId)
        .put("parentViewId", parentViewId)
        .put("ownerId", ownerId)
        .put("previousVersion", previousVersion)
        .put("targetVersion", targetVersion)
        .put("phase", phase.name)
        .put("startedAt", startedAt)
        .put("updatedAt", updatedAt)
        .put("lastError", lastError)
        .put("environmentId", environmentId)

    companion object {
        const val RECORD_SCHEMA = "kite_resource_view_transaction_v1"

        fun fromJson(json: JSONObject): ResourceViewTransactionRecord {
            require(json.getString("schema") == RECORD_SCHEMA) { "资源 View 事务 schema 不支持" }
            return ResourceViewTransactionRecord(
                transactionId = json.getString("transactionId"),
                resourceId = json.getString("resourceId"),
                runInstanceId = json.getString("runInstanceId"),
                viewId = json.getString("viewId"),
                parentViewId = json.getString("parentViewId"),
                ownerId = json.getString("ownerId"),
                previousVersion = json.optString("previousVersion"),
                targetVersion = json.optString("targetVersion"),
                phase = ResourceViewTransactionPhase.valueOf(json.getString("phase")),
                startedAt = json.getLong("startedAt"),
                updatedAt = json.getLong("updatedAt"),
                lastError = json.optString("lastError"),
                environmentId = json.optString("environmentId")
                    .takeIf { it.isNotBlank() && it != "null" }
                    ?: ProotViewStore.DEFAULT_ENVIRONMENT_ID
            )
        }
    }
}

/**
 * 资源更新对通用 PRoot View 生命周期的适配器。
 *
 * 这里保存资源、版本和运行实例的业务事务记录；View catalog/current、文件投影、
 * 父层和崩溃一致性仍由 [ProotViewStore] 与 native PRoot 持有。
 */
internal class ResourceTransactionCoordinator(
    private val storeRoot: File,
    controlFile: File,
    private val targetResolver: (String) -> ResourceTransactionTarget?,
    private val viewStoreProvider: () -> ProotViewStore?,
    private val viewProcessGuard: ProotViewProcessGuard = ProotViewProcessGuard(),
    now: () -> Long = System::currentTimeMillis,
    maxBackupBytes: Long = DEFAULT_MAX_BACKUP_BYTES,
    nativePath: (File) -> String = File::getAbsolutePath,
    restoreGuard: FileProtectionRestoreGuard = FileProtectionRestoreGuard { Result.success(Unit) }
) {
    private val now = now
    private val legacyRecordFile = File(storeRoot, LEGACY_VIEW_RECORD_FILE_NAME)
    private val activeRecordRoot = File(storeRoot, ACTIVE_RECORD_DIRECTORY_NAME)
    private val checkpointRecordRoot = File(storeRoot, CHECKPOINT_RECORD_DIRECTORY_NAME)
    private val legacyDelegate = FileProtectionCoordinator(
        storeRoot = storeRoot,
        controlFile = controlFile,
        legacyRecordDecoders = listOf(RESOURCE_LEGACY_RECORD_DECODER),
        restoreGuard = restoreGuard,
        now = now,
        maxJournalBytes = maxBackupBytes,
        nativePath = nativePath
    )

    @Synchronized
    fun beginUpdate(
        resourceId: String,
        runInstanceId: String,
        previousVersion: String,
        targetVersion: String,
        environmentId: String? = null
    ): Result<Unit> = runCatching {
        val target = targetResolver(resourceId) ?: error("resource_transaction_target_unavailable")
        val cleanResourceId = cleanResourceId(target.resourceId)
        require(runInstanceId.isNotBlank()) { "missing_resource_run_instance" }
        val store = requireViewStore()
        store.ensureInitialized()
        val resolvedEnvironmentId = environmentId ?: store.activeEnvironmentId()
        readActiveRecord(resolvedEnvironmentId)?.let {
            error("resource_view_transaction_busy:${it.resourceId}:environment=${it.environmentId}")
        }
        // 资源更新绑定明确的环境身份：从该环境的当前头派生子 View，不读写其他环境的头。
        val environmentHead = store.environmentCurrents()[resolvedEnvironmentId]
            ?: error("proot_view_environment_unavailable:$resolvedEnvironmentId")
        val current = store.recover()?.views?.firstOrNull { it.viewId == environmentHead }
            ?: error("proot_view_current_unavailable")
        val targetPath = target.rootHostPath.toPath().toAbsolutePath().normalize()
        val currentBinding = store.binding(current.viewId)
        require(currentBinding.scopeRootPaths.any { scope ->
            val scopePath = File(scope).toPath().toAbsolutePath().normalize()
            targetPath == scopePath || targetPath.startsWith(scopePath)
        }) { "resource_transaction_target_outside_view_scope" }
        store.enable()
        val ownerId = "resource-update-$cleanResourceId-${safeRunId(runInstanceId)}"
        viewProcessGuard.quiesce(current.viewId).getOrThrow()
        // 子 View 直接读取父代的块数据。父代从这一刻起必须保持不可变：writer lease
        // 一方面拒绝新进程绑定本环境 current，另一方面把这条约束写进可恢复的 catalog。
        store.acquireLease(current.viewId, ownerId, ProotViewLeaseMode.WRITER)
        val prepared = runCatching {
            store.prepare("resource-update:$cleanResourceId", environmentId = resolvedEnvironmentId)
        }.onFailure {
            store.releaseLease(current.viewId, ownerId)
        }.getOrThrow()
        runCatching {
            store.verify(prepared.viewId)
            store.acquireLease(prepared.viewId, ownerId, ProotViewLeaseMode.WRITER)
        }.onFailure {
            runCatching { store.releaseLease(prepared.viewId, ownerId) }
            runCatching { store.discard(prepared.viewId) }
            runCatching { store.releaseLease(current.viewId, ownerId) }
        }.getOrThrow()
        val timestamp = now()
        val record = ResourceViewTransactionRecord(
            transactionId = "resource-view-$timestamp-${safeRunId(runInstanceId)}",
            resourceId = cleanResourceId,
            runInstanceId = runInstanceId,
            viewId = prepared.viewId,
            parentViewId = current.viewId,
            ownerId = ownerId,
            previousVersion = previousVersion,
            targetVersion = targetVersion,
            phase = ResourceViewTransactionPhase.ACTIVE,
            startedAt = timestamp,
            updatedAt = timestamp,
            environmentId = resolvedEnvironmentId
        )
        runCatching { writeActiveRecord(record) }.onFailure {
            store.releaseLease(prepared.viewId, ownerId)
            store.discard(prepared.viewId)
            store.releaseLease(current.viewId, ownerId)
        }.getOrThrow()
    }

    @Synchronized
    fun environmentForRun(runInstanceId: String): Map<String, String> {
        val record = readActiveRecords().firstOrNull {
            it.runInstanceId == runInstanceId && it.phase == ResourceViewTransactionPhase.ACTIVE
        } ?: return emptyMap()
        return requireViewStore().binding(record.viewId).environment()
    }

    @Synchronized
    fun commitUpdate(resourceId: String, runInstanceId: String): Result<Unit> {
        val record = runCatching { requireActiveRecord(resourceId, runInstanceId) }
            .getOrElse { return Result.failure(it) }
        val store = requireViewStore()
        val committed = runCatching {
            // 提交校验和本环境 current 原子切换之前，先确保新代不再有写入者。
            viewProcessGuard.quiesce(record.viewId).getOrThrow()
            store.commit(record.viewId, record.ownerId, environmentId = record.environmentId)
        }
        if (committed.isFailure) return Result.failure(requireNotNull(committed.exceptionOrNull()))

        // current 已经切换后，提交事实不能因为清理父代 lease 失败而倒置；finalize 和
        // 启动恢复都会再次执行幂等释放。
        runCatching { store.releaseLease(record.parentViewId, record.ownerId) }

        // current 指针切换成功就是文件视图的提交真相。业务记录落盘失败不能反过来
        // 宣布更新失败；finalize 或下次启动会按 current 补齐登记。
        runCatching {
            writeActiveRecord(record.copy(
                phase = ResourceViewTransactionPhase.VIEW_COMMITTED,
                updatedAt = now()
            ))
        }
        return Result.success(Unit)
    }

    @Synchronized
    fun finalizeUpdate(resourceId: String, runInstanceId: String): Result<Unit> = runCatching {
        val record = requireRecord(resourceId, runInstanceId)
        val store = requireViewStore()
        val snapshot = store.recover()
        // 用本环境头判断提交真相，而非全局 current；A 的 finalize 不应误读 B 的头。
        val environmentHead = snapshot?.environmentCurrents?.get(record.environmentId)
        val committed = record.phase == ResourceViewTransactionPhase.VIEW_COMMITTED ||
            (record.phase == ResourceViewTransactionPhase.ACTIVE && environmentHead == record.viewId)
        require(committed) {
            "resource_view_transaction_not_committed"
        }
        store.releaseLease(record.viewId, record.ownerId)
        store.releaseLease(record.parentViewId, record.ownerId)
        deleteActiveRecord(record.environmentId)
        deleteRetiredCheckpointRecords()
    }

    @Synchronized
    fun rollbackUpdate(
        resourceId: String,
        runInstanceId: String
    ): Result<ResourceTransactionRestoreResult> = runCatching {
        val record = requireRecord(resourceId, runInstanceId)
        require(record.phase == ResourceViewTransactionPhase.ACTIVE) {
            "resource_view_transaction_cannot_rollback:${record.phase}"
        }
        rollbackActiveRecord(record)
    }

    fun deactivateInterruptedTransaction() = legacyDelegate.deactivateInterruptedOperation()

    @Synchronized
    fun recoverInterruptedTransactions(): List<ResourceInterruptedRecoveryResult> {
        val results = mutableListOf<ResourceInterruptedRecoveryResult>()
        deleteRetiredCheckpointRecords()
        readActiveRecords().forEach { record ->
            runCatching {
                val store = requireViewStore()
                val snapshot = store.recover()
                when (record.phase) {
                    ResourceViewTransactionPhase.ACTIVE -> {
                        if (snapshot?.environmentCurrents?.get(record.environmentId) == record.viewId) {
                            store.releaseLease(record.viewId, record.ownerId)
                            store.releaseLease(record.parentViewId, record.ownerId)
                            deleteActiveRecord(record.environmentId)
                            results += ResourceInterruptedRecoveryResult(
                                resourceId = record.resourceId,
                                restored = false,
                                committed = true,
                                targetVersion = record.targetVersion,
                                environmentId = record.environmentId,
                                message = "上次更新已完成 View 切换，已补齐资源登记"
                            )
                        } else {
                            rollbackActiveRecord(record)
                            results += ResourceInterruptedRecoveryResult(
                                resourceId = record.resourceId,
                                restored = true,
                                environmentId = record.environmentId,
                                message = "上次更新中断，当前 View 未切换"
                            )
                        }
                    }
                    ResourceViewTransactionPhase.VIEW_COMMITTED -> {
                        store.releaseLease(record.viewId, record.ownerId)
                        store.releaseLease(record.parentViewId, record.ownerId)
                        deleteActiveRecord(record.environmentId)
                        results += ResourceInterruptedRecoveryResult(
                            resourceId = record.resourceId,
                            restored = false,
                            committed = true,
                            targetVersion = record.targetVersion,
                            environmentId = record.environmentId,
                            message = "上次更新已提交，已补齐资源登记"
                        )
                    }
                    else -> Unit
                }
            }.onFailure { error ->
                results += ResourceInterruptedRecoveryResult(
                    resourceId = record.resourceId,
                    restored = false,
                    environmentId = record.environmentId,
                    message = error.message ?: error.javaClass.simpleName
                )
            }
        }
        results += legacyDelegate.settleTransientOperations().map { legacy ->
            ResourceInterruptedRecoveryResult(
                resourceId = legacy.ownerId,
                restored = legacy.restored,
                message = if (legacy.restored) {
                    "上次更新中断，已自动恢复更新前版本"
                } else {
                    legacy.message
                }
            )
        }
        return results
    }

    private fun rollbackActiveRecord(record: ResourceViewTransactionRecord): ResourceTransactionRestoreResult {
        val store = requireViewStore()
        val snapshot = store.recover()
        require(snapshot?.environmentCurrents?.get(record.environmentId) != record.viewId) {
            "resource_view_transaction_already_committed"
        }
        if (snapshot?.views?.any { it.viewId == record.viewId } == true) {
            viewProcessGuard.quiesce(record.viewId).getOrThrow()
            store.releaseLease(record.viewId, record.ownerId)
            store.discard(record.viewId)
        }
        store.releaseLease(record.parentViewId, record.ownerId)
        deleteActiveRecord(record.environmentId)
        return ResourceTransactionRestoreResult(
            resourceId = record.resourceId,
            transactionId = record.transactionId,
            restoredVersion = record.previousVersion,
            environmentId = record.environmentId
        )
    }

    private fun requireActiveRecord(resourceId: String, runInstanceId: String): ResourceViewTransactionRecord {
        val record = requireRecord(resourceId, runInstanceId)
        require(record.phase == ResourceViewTransactionPhase.ACTIVE) { "resource_view_transaction_not_active" }
        return record
    }

    private fun requireRecord(resourceId: String, runInstanceId: String): ResourceViewTransactionRecord {
        val record = readActiveRecords().firstOrNull { it.runInstanceId == runInstanceId }
            ?: error("resource_view_transaction_missing")
        require(record.resourceId == cleanResourceId(resourceId)) { "resource_view_transaction_owner_mismatch" }
        require(record.runInstanceId == runInstanceId) { "resource_view_transaction_run_mismatch" }
        return record
    }

    private fun requireViewStore(): ProotViewStore =
        viewStoreProvider() ?: error("proot_view_runtime_unavailable")

    private fun readActiveRecord(environmentId: String): ResourceViewTransactionRecord? {
        migrateLegacyRecordIfNeeded()
        return readRecordFile(recordFile(activeRecordRoot, environmentId))
            ?.takeIf {
                it.environmentId == environmentId &&
                    (it.phase == ResourceViewTransactionPhase.ACTIVE ||
                        it.phase == ResourceViewTransactionPhase.VIEW_COMMITTED)
            }
    }

    private fun readActiveRecords(): List<ResourceViewTransactionRecord> {
        migrateLegacyRecordIfNeeded()
        return activeRecordRoot.listFiles { file -> file.isFile && file.extension == "json" }
            .orEmpty()
            .sortedBy(File::getName)
            .mapNotNull(::readRecordFile)
            .filter {
                it.phase == ResourceViewTransactionPhase.ACTIVE ||
                    it.phase == ResourceViewTransactionPhase.VIEW_COMMITTED
            }
    }

    private fun writeActiveRecord(record: ResourceViewTransactionRecord) {
        require(record.phase == ResourceViewTransactionPhase.ACTIVE ||
            record.phase == ResourceViewTransactionPhase.VIEW_COMMITTED
        ) { "resource_view_active_record_phase_invalid:${record.phase}" }
        writeRecordFile(recordFile(activeRecordRoot, record.environmentId), record)
    }

    private fun deleteActiveRecord(environmentId: String) {
        Files.deleteIfExists(recordFile(activeRecordRoot, environmentId).toPath())
    }

    private fun deleteRetiredCheckpointRecords() {
        checkpointRecordRoot.listFiles().orEmpty().forEach { file ->
            if (file.isFile) Files.deleteIfExists(file.toPath())
        }
    }

    private fun readRecordFile(file: File): ResourceViewTransactionRecord? {
        if (!file.isFile) return null
        return ResourceViewTransactionRecord.fromJson(JSONObject(file.readText()))
    }

    private fun writeRecordFile(recordFile: File, record: ResourceViewTransactionRecord) {
        recordFile.parentFile?.mkdirs()
        val temp = File(recordFile.parentFile, ".${recordFile.name}.tmp-${System.nanoTime()}")
        FileOutputStream(temp).use { stream ->
            stream.write((record.toJson().toString(2) + "\n").toByteArray(StandardCharsets.UTF_8))
            stream.fd.sync()
        }
        try {
            Files.move(
                temp.toPath(),
                recordFile.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temp.toPath(), recordFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun recordFile(root: File, environmentId: String): File {
        require(environmentId.isNotBlank() && environmentId.all {
            it.isLetterOrDigit() || it == '-' || it == '_'
        }) { "resource_view_environment_id_unsafe:$environmentId" }
        return File(root, "$environmentId.json")
    }

    private fun migrateLegacyRecordIfNeeded() {
        if (!legacyRecordFile.isFile) return
        val record = readRecordFile(legacyRecordFile)
        if (record == null) {
            Files.deleteIfExists(legacyRecordFile.toPath())
            return
        }
        val target = when (record.phase) {
            ResourceViewTransactionPhase.ACTIVE,
            ResourceViewTransactionPhase.VIEW_COMMITTED -> recordFile(activeRecordRoot, record.environmentId)
            ResourceViewTransactionPhase.COMPLETED -> null
            ResourceViewTransactionPhase.ROLLED_BACK,
            ResourceViewTransactionPhase.REPAIRED,
            ResourceViewTransactionPhase.FAILED -> null
        }
        if (target != null && !target.isFile) {
            writeRecordFile(target, record)
        }
        Files.deleteIfExists(legacyRecordFile.toPath())
    }

    private fun cleanResourceId(resourceId: String): String =
        KiteResourceInstallRecipes.safeId(resourceId).takeIf(String::isNotBlank)
            ?: error("unsafe_resource_id")

    private fun safeRunId(runInstanceId: String): String =
        KiteResourceInstallRecipes.safeId(runInstanceId).takeLast(48).ifBlank { "run" }

    companion object {
        private const val DEFAULT_MAX_BACKUP_BYTES = 512L * 1024L * 1024L
        private const val METADATA_PREVIOUS_VERSION = "previousVersion"
        private const val METADATA_TARGET_VERSION = "targetVersion"
        private const val LEGACY_RECORD_SCHEMA = "kf_resource_txn_record_v1"
        private const val LEGACY_VIEW_RECORD_FILE_NAME = "resource-view-transaction.json"
        private const val ACTIVE_RECORD_DIRECTORY_NAME = "active"
        private const val CHECKPOINT_RECORD_DIRECTORY_NAME = "checkpoints"

        private val RESOURCE_LEGACY_RECORD_DECODER = LegacyFileProtectionRecordDecoder { properties ->
            if (properties.getProperty("schema") != LEGACY_RECORD_SCHEMA) return@LegacyFileProtectionRecordDecoder null
            runCatching {
                FileProtectionRecord(
                    operationId = properties.getProperty("transactionId"),
                    ownerId = properties.getProperty("resourceId"),
                    operationKind = "resource_update",
                    rootHostPath = properties.getProperty("rootHostPath"),
                    journalHostPath = properties.getProperty("entriesHostPath"),
                    backendId = FileProtectionBackendId.WholeObjectPreimage,
                    phase = FileProtectionPhase.valueOf(properties.getProperty("phase")),
                    startedAt = properties.getProperty("startedAt").toLong(),
                    committedAt = properties.getProperty("committedAt").toLong(),
                    lastError = properties.getProperty("lastError").orEmpty(),
                    metadata = mapOf(
                        METADATA_PREVIOUS_VERSION to properties.getProperty("previousVersion").orEmpty(),
                        METADATA_TARGET_VERSION to properties.getProperty("targetVersion").orEmpty()
                    )
                )
            }.getOrNull()
        }

        fun create(context: Context, manifestLoader: KiteResourceManifestLoader): ResourceTransactionCoordinator {
            val appContext = context.applicationContext
            val layout = WorkSurfaceRuntimeBridge.getRuntimeLayout(appContext)
            return ResourceTransactionCoordinator(
                storeRoot = File(appContext.filesDir, "resource-transactions"),
                controlFile = ProotFileProtectionRuntime.controlFile(layout),
                restoreGuard = AndroidFileProtectionRestoreGuard(),
                viewStoreProvider = viewStore@ {
                    val preparedLayout = AssetExtractor.prepareRuntime(appContext)
                    val container = WorkSurfaceRuntimeBridge.getSavedContainer(appContext)
                        ?: return@viewStore null
                    val descriptor = readRuntimeDescriptor(preparedLayout)
                    if (!ProotViewRuntime.run {
                            descriptor.hasCapability(ProotViewStore.RUNTIME_CAPABILITY) &&
                                descriptor.hasCapability(ProotViewStore.BLOCK_RUNTIME_CAPABILITY)
                        }
                    ) {
                        null
                    } else {
                        ProotViewStore.forContainer(container)
                    }
                },
                targetResolver = { resourceId ->
                    manifestLoader.requestManifest(resourceId)?.let { manifest ->
                        resolveAndroidTarget(appContext, manifest)
                    }
                }
            )
        }

        private fun readRuntimeDescriptor(layout: AssetExtractor.RuntimeLayout): JSONObject = runCatching {
            JSONObject(layout.prootRuntimeDescriptorFile.readText())
        }.getOrDefault(JSONObject())

        private fun resolveAndroidTarget(context: Context, manifest: KiteResourceManifest): ResourceTransactionTarget? {
            val guestRoot = manifest.installRoot.trim().replace(Regex("/+"), "/").removeSuffix("/")
            if (guestRoot != "/workspace" && !guestRoot.startsWith("/workspace/")) return null
            val container = WorkSurfaceRuntimeBridge.getSavedContainer(context) ?: return null
            val workspace = File(container.workspacePath).toPath().toAbsolutePath().normalize()
            val relative = guestRoot.removePrefix("/workspace").removePrefix("/")
            if (relative.split('/').any { it == "." || it == ".." }) return null
            val target = if (relative.isBlank()) workspace else workspace.resolve(relative).normalize()
            if (target != workspace && !target.startsWith(workspace)) return null
            val managedSoftware = workspace.resolve(".kf/software").normalize()
            if (target != managedSoftware && !target.startsWith(managedSoftware)) return null
            return ResourceTransactionTarget(manifest.id, target.toFile())
        }
    }
}
