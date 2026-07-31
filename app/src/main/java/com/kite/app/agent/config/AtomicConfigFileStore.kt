package com.kite.app.agent.config

import java.io.File
import java.io.FileOutputStream
import java.nio.channels.FileChannel
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.util.UUID

internal data class ConfigFileRevision(val value: String, val exists: Boolean)

internal data class ConfigFileSnapshot(
    val revision: ConfigFileRevision,
    val bytes: ByteArray
)

internal sealed interface AtomicConfigFileWriteResult {
    data class Applied(
        val revision: ConfigFileRevision,
        val backupReference: String?
    ) : AtomicConfigFileWriteResult
    data class Conflict(val actualRevision: ConfigFileRevision) : AtomicConfigFileWriteResult
    data class Rejected(val message: String) : AtomicConfigFileWriteResult
    data class Failed(val message: String, val restored: Boolean) : AtomicConfigFileWriteResult
}

internal data class AtomicConfigFileUpdate(
    val target: File,
    val expectedRevision: ConfigFileRevision,
    val nextBytes: ByteArray,
    val validate: (ByteArray) -> String?
)

internal sealed interface AtomicConfigFilesWriteResult {
    data class Applied(val backupReferences: List<String>) : AtomicConfigFilesWriteResult
    data class Conflict(val target: File, val actualRevision: ConfigFileRevision) : AtomicConfigFilesWriteResult
    data class Rejected(val message: String) : AtomicConfigFilesWriteResult
    data class Failed(val message: String, val restored: Boolean) : AtomicConfigFilesWriteResult
}

/**
 * 面向 Agent 原生配置的乐观并发文件事务。
 *
 * 它在同目录暂存、fsync、再次核对 revision 后原子替换；替换后的解析失败会从备份恢复。
 * 外部进程不参与同一把锁，因此调用方仍应把 Conflict 明确交给用户，不能悄悄重试覆盖。
 */
internal class AtomicConfigFileStore(
    private val now: () -> Long = System::currentTimeMillis,
    private val maxBytes: Int = DEFAULT_MAX_BYTES,
    private val maxBackups: Int = DEFAULT_MAX_BACKUPS
) {
    @Synchronized
    fun read(target: File): ConfigFileSnapshot {
        if (!target.exists()) return ConfigFileSnapshot(MISSING_REVISION, ByteArray(0))
        require(target.isFile) { "配置目标不是普通文件" }
        require(target.length() <= maxBytes) { "配置文件超过安全读取上限" }
        val bytes = target.readBytes()
        return ConfigFileSnapshot(revisionOf(bytes), bytes)
    }

    @Synchronized
    fun replace(
        target: File,
        expectedRevision: ConfigFileRevision,
        nextBytes: ByteArray,
        validate: (ByteArray) -> String?
    ): AtomicConfigFileWriteResult {
        if (nextBytes.size > maxBytes) return AtomicConfigFileWriteResult.Rejected("配置内容超过安全写入上限")
        validate(nextBytes)?.let { return AtomicConfigFileWriteResult.Rejected(it) }

        val initial = runCatching { read(target) }.getOrElse {
            return AtomicConfigFileWriteResult.Failed("无法读取当前配置", restored = true)
        }
        if (initial.revision != expectedRevision) {
            return AtomicConfigFileWriteResult.Conflict(initial.revision)
        }

        val parent = target.parentFile
            ?: return AtomicConfigFileWriteResult.Failed("配置目标缺少父目录", restored = true)
        if (!parent.exists() && !parent.mkdirs()) {
            return AtomicConfigFileWriteResult.Failed("无法创建配置目录", restored = true)
        }
        if (!parent.isDirectory) {
            return AtomicConfigFileWriteResult.Failed("配置父路径不是目录", restored = true)
        }

        val stage = File(parent, ".${target.name}.kite-stage-${UUID.randomUUID()}")
        var backup: File? = null
        try {
            writeSynced(stage, nextBytes)
            applyTargetPermissions(stage, target.takeIf(File::exists))
            backup = if (initial.revision.exists) createBackup(target, initial) else null

            val beforeReplace = read(target)
            if (beforeReplace.revision != expectedRevision) {
                backup?.delete()
                return AtomicConfigFileWriteResult.Conflict(beforeReplace.revision)
            }

            moveAtomic(stage, target)
            syncDirectoryBestEffort(parent)

            val applied = read(target)
            validate(applied.bytes)?.let {
                val restored = restore(target, initial, backup)
                return AtomicConfigFileWriteResult.Failed("写入后的配置校验失败", restored)
            }
            trimBackups(target)
            return AtomicConfigFileWriteResult.Applied(
                revision = applied.revision,
                backupReference = backup?.absolutePath
            )
        } catch (_: AtomicMoveNotSupportedException) {
            val restored = restore(target, initial, backup)
            return AtomicConfigFileWriteResult.Failed("当前文件系统不支持原子替换", restored)
        } catch (_: Exception) {
            val restored = restore(target, initial, backup)
            return AtomicConfigFileWriteResult.Failed("配置写入失败", restored)
        } finally {
            if (stage.exists()) stage.delete()
        }
    }

    /**
     * 对同一 Agent 的多份原生文件执行一个乐观并发事务。
     *
     * 所有文件会先完成校验、暂存和 revision 复核，再逐个原子替换；任一替换失败都会恢复全部目标。
     */
    @Synchronized
    fun replaceAll(updates: List<AtomicConfigFileUpdate>): AtomicConfigFilesWriteResult {
        if (updates.isEmpty()) return AtomicConfigFilesWriteResult.Rejected("没有待写入的配置文件")
        val canonicalTargets = runCatching { updates.map { it.target.canonicalFile } }.getOrElse {
            return AtomicConfigFilesWriteResult.Failed("配置目标无法解析", restored = true)
        }
        if (canonicalTargets.distinct().size != canonicalTargets.size) {
            return AtomicConfigFilesWriteResult.Rejected("同一配置目标不能重复写入")
        }
        updates.forEach { update ->
            if (update.nextBytes.size > maxBytes) {
                return AtomicConfigFilesWriteResult.Rejected("配置内容超过安全写入上限")
            }
            update.validate(update.nextBytes)?.let { return AtomicConfigFilesWriteResult.Rejected(it) }
        }
        val initial = linkedMapOf<File, ConfigFileSnapshot>()
        updates.forEach { update ->
            val snapshot = runCatching { read(update.target) }.getOrElse {
                return AtomicConfigFilesWriteResult.Failed("无法读取当前配置", restored = true)
            }
            if (snapshot.revision != update.expectedRevision) {
                return AtomicConfigFilesWriteResult.Conflict(update.target, snapshot.revision)
            }
            initial[update.target] = snapshot
        }

        val stages = linkedMapOf<File, File>()
        val backups = linkedMapOf<File, File?>()
        val appliedTargets = mutableListOf<File>()
        try {
            updates.forEach { update ->
                val parent = update.target.parentFile ?: error("配置目标缺少父目录")
                require(parent.mkdirs() || parent.isDirectory) { "无法创建配置目录" }
                val stage = File(parent, ".${update.target.name}.kite-stage-${UUID.randomUUID()}")
                writeSynced(stage, update.nextBytes)
                applyTargetPermissions(stage, update.target.takeIf(File::exists))
                stages[update.target] = stage
            }
            updates.forEach { update ->
                val current = read(update.target)
                if (current.revision != update.expectedRevision) {
                    return AtomicConfigFilesWriteResult.Conflict(update.target, current.revision)
                }
            }
            updates.forEach { update ->
                val snapshot = requireNotNull(initial[update.target])
                backups[update.target] = if (snapshot.revision.exists) createBackup(update.target, snapshot) else null
            }
            updates.forEach { update ->
                moveAtomic(requireNotNull(stages[update.target]), update.target)
                syncDirectoryBestEffort(requireNotNull(update.target.parentFile))
                appliedTargets += update.target
            }
            updates.forEach { update ->
                val written = read(update.target)
                update.validate(written.bytes)?.let { error("写入后的配置校验失败") }
            }
            updates.forEach { trimBackups(it.target) }
            return AtomicConfigFilesWriteResult.Applied(
                backups.values.filterNotNull().map(File::getAbsolutePath)
            )
        } catch (_: AtomicMoveNotSupportedException) {
            val restored = restoreAll(appliedTargets, initial, backups)
            return AtomicConfigFilesWriteResult.Failed("当前文件系统不支持原子替换", restored)
        } catch (_: Exception) {
            val restored = restoreAll(
                targets = appliedTargets,
                initial = initial,
                backups = backups
            )
            return AtomicConfigFilesWriteResult.Failed("配置写入失败", restored)
        } finally {
            stages.values.forEach { if (it.exists()) it.delete() }
        }
    }

    private fun restoreAll(
        targets: List<File>,
        initial: Map<File, ConfigFileSnapshot>,
        backups: Map<File, File?>
    ): Boolean = targets.all { target ->
        val snapshot = initial[target] ?: return@all false
        restore(target, snapshot, backups[target])
    }

    private fun createBackup(target: File, snapshot: ConfigFileSnapshot): File {
        val directory = File(target.parentFile, BACKUP_DIRECTORY)
        require(directory.mkdirs() || directory.isDirectory) { "无法创建配置备份目录" }
        val revisionToken = snapshot.revision.value.substringAfter(':').take(12)
        val backup = File(directory, "${safeName(target.name)}-${now()}-$revisionToken.bak")
        writeSynced(backup, snapshot.bytes)
        applyPrivatePermissions(backup)
        syncDirectoryBestEffort(directory)
        return backup
    }

    private fun restore(target: File, original: ConfigFileSnapshot, backup: File?): Boolean = runCatching {
        val parent = requireNotNull(target.parentFile)
        if (!original.revision.exists) {
            if (target.exists() && !target.delete()) error("无法移除失败的新配置")
            syncDirectoryBestEffort(parent)
        } else {
            val restoreStage = File(parent, ".${target.name}.kite-restore-${UUID.randomUUID()}")
            try {
                val bytes = backup?.takeIf(File::isFile)?.readBytes() ?: original.bytes
                writeSynced(restoreStage, bytes)
                applyTargetPermissions(restoreStage, target.takeIf(File::exists))
                moveAtomic(restoreStage, target)
                syncDirectoryBestEffort(parent)
            } finally {
                if (restoreStage.exists()) restoreStage.delete()
            }
        }
        read(target).revision == original.revision
    }.getOrDefault(false)

    private fun trimBackups(target: File) {
        val directory = File(target.parentFile, BACKUP_DIRECTORY)
        val prefix = "${safeName(target.name)}-"
        directory.listFiles()
            .orEmpty()
            .filter { it.isFile && it.name.startsWith(prefix) && it.name.endsWith(".bak") }
            .sortedByDescending(File::lastModified)
            .drop(maxBackups.coerceAtLeast(1))
            .forEach(File::delete)
    }

    private fun writeSynced(target: File, bytes: ByteArray) {
        FileOutputStream(target).use { stream ->
            stream.write(bytes)
            stream.fd.sync()
        }
    }

    private fun applyTargetPermissions(stage: File, existing: File?) {
        if (existing != null) {
            val copied = runCatching {
                val permissions = Files.getPosixFilePermissions(existing.toPath())
                Files.setPosixFilePermissions(stage.toPath(), permissions)
            }.isSuccess
            if (copied) return
        }
        applyPrivatePermissions(stage)
    }

    private fun applyPrivatePermissions(file: File) {
        runCatching {
            Files.setPosixFilePermissions(
                file.toPath(),
                setOf(
                    java.nio.file.attribute.PosixFilePermission.OWNER_READ,
                    java.nio.file.attribute.PosixFilePermission.OWNER_WRITE
                )
            )
        }
    }

    private fun moveAtomic(source: File, target: File) {
        Files.move(
            source.toPath(),
            target.toPath(),
            StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING
        )
    }

    private fun syncDirectoryBestEffort(directory: File) {
        runCatching {
            FileChannel.open(directory.toPath(), StandardOpenOption.READ).use { it.force(true) }
        }
    }

    private fun revisionOf(bytes: ByteArray): ConfigFileRevision {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return ConfigFileRevision(
            value = "sha256:" + digest.joinToString("") { "%02x".format(it) },
            exists = true
        )
    }

    private fun safeName(value: String): String = value.replace(Regex("[^A-Za-z0-9._-]"), "_")

    companion object {
        val MISSING_REVISION = ConfigFileRevision("missing", exists = false)
        private const val BACKUP_DIRECTORY = ".kite-backups"
        private const val DEFAULT_MAX_BYTES = 8 * 1024 * 1024
        private const val DEFAULT_MAX_BACKUPS = 5
    }
}
