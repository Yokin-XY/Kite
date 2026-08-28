package com.kite.app.resources

import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.BasicFileAttributes

internal enum class ResourceInstallRecoveryDisposition {
    NO_ACTION,
    ACTIVE,
    RESTORED,
    COMMITTED,
    FAILED,
}

internal data class ResourceInstallTransactionRecoveryResult(
    val resourceId: String,
    val disposition: ResourceInstallRecoveryDisposition,
    val operation: String = KiteResourceInstallRecipes.OP_UPDATE,
    val targetVersion: String = "",
    val message: String,
)

/**
 * 恢复单个资源目录留下的更新事务。
 *
 * 事务事实只由安装根旁边的 backup、lock 和 state 三个受控路径表达；这里不扫描 rootfs，
 * 也不切换整套 PRoot View。只有锁的 PID 与进程启动代次都已失效时才会接管恢复。
 */
internal class ResourceInstallTransactionRecovery(
    private val processStartTime: (Int) -> String? = ::readProcessStartTime,
) {
    fun recover(workspaceDirectory: File, resourceId: String): ResourceInstallTransactionRecoveryResult {
        val safeResourceId = KiteResourceInstallRecipes.safeId(resourceId)
        val softwareRoot = File(workspaceDirectory, ".kf/software").absoluteFile.normalize()
        val installRoot = File(softwareRoot, safeResourceId)
        val backupRoot = File(softwareRoot, "$safeResourceId.kite-backup")
        val updateLock = File(softwareRoot, "$safeResourceId.kite-update-lock")
        val transactionState = File(softwareRoot, "$safeResourceId.kite-transaction")

        return runCatching {
            checkDirectChild(softwareRoot, installRoot)
            checkDirectChild(softwareRoot, backupRoot)
            checkDirectChild(softwareRoot, updateLock)
            checkDirectChild(softwareRoot, transactionState)

            val hasBackup = pathExists(backupRoot)
            val hasState = pathExists(transactionState)
            val hasLock = pathExists(updateLock)
            if (!hasBackup && !hasState && !hasLock) {
                return@runCatching result(
                    safeResourceId,
                    ResourceInstallRecoveryDisposition.NO_ACTION,
                    message = "没有待恢复的资源事务",
                )
            }
            if (hasLock && lockOwnerIsAlive(updateLock)) {
                return@runCatching result(
                    safeResourceId,
                    ResourceInstallRecoveryDisposition.ACTIVE,
                    message = "资源安装进程仍在运行，未接管事务",
                )
            }

            val state = readState(transactionState)
            val operation = state[STATE_OPERATION]
                ?.takeIf { value -> value in RECOVERABLE_OPERATIONS }
                ?: KiteResourceInstallRecipes.OP_UPDATE
            val targetVersion = state[STATE_TARGET_VERSION]
                ?.takeIf(VALID_STATE_VALUE::matches)
                .orEmpty()
            val committed = state[STATE_PHASE] == PHASE_COMMITTED && pathExists(installRoot)

            if (committed) {
                if (hasBackup) deleteOwnedPath(backupRoot)
                cleanupTransactionArtifacts(softwareRoot, safeResourceId, updateLock, transactionState)
                return@runCatching result(
                    safeResourceId,
                    ResourceInstallRecoveryDisposition.COMMITTED,
                    operation,
                    targetVersion,
                    "上次资源更新已提交，已清理中断残留",
                )
            }

            if (hasBackup) {
                removeOwnedCommandLinks(workspaceDirectory, File(installRoot, COMMAND_LEDGER))
                if (pathExists(installRoot)) deleteOwnedPath(installRoot)
                move(backupRoot, installRoot)
                restoreOwnedCommandLinks(workspaceDirectory, File(installRoot, COMMAND_LEDGER))
                cleanupTransactionArtifacts(softwareRoot, safeResourceId, updateLock, transactionState)
                return@runCatching result(
                    safeResourceId,
                    ResourceInstallRecoveryDisposition.RESTORED,
                    operation,
                    targetVersion,
                    "上次资源更新中断，已恢复更新前版本",
                )
            }

            val previousRootStillUsable = pathExists(installRoot)
            cleanupTransactionArtifacts(softwareRoot, safeResourceId, updateLock, transactionState)
            result(
                safeResourceId,
                if (previousRootStillUsable) {
                    ResourceInstallRecoveryDisposition.RESTORED
                } else {
                    ResourceInstallRecoveryDisposition.FAILED
                },
                operation,
                targetVersion,
                if (previousRootStillUsable) {
                    "上次资源更新在替换目录前中断，原版本保持可用"
                } else {
                    "上次资源更新中断，且没有可恢复的资源目录"
                },
            )
        }.getOrElse { error ->
            result(
                safeResourceId,
                ResourceInstallRecoveryDisposition.FAILED,
                message = "资源事务自动恢复失败：${error.message ?: error.javaClass.simpleName}",
            )
        }
    }

    private fun lockOwnerIsAlive(updateLock: File): Boolean {
        val owner = File(updateLock, LOCK_OWNER_FILE)
        val lines = runCatching { owner.readLines() }.getOrDefault(emptyList())
        val pid = lines.getOrNull(0)?.trim()?.toIntOrNull() ?: return false
        val expectedStart = lines.getOrNull(1)?.trim().orEmpty()
        if (expectedStart.isBlank()) return false
        return processStartTime(pid) == expectedStart
    }

    private fun readState(transactionState: File): Map<String, String> {
        if (!pathExists(transactionState) || !transactionState.isFile) return emptyMap()
        return transactionState.readLines()
            .mapNotNull { line ->
                val separator = line.indexOf('=')
                if (separator <= 0) return@mapNotNull null
                line.substring(0, separator) to line.substring(separator + 1)
            }
            .toMap()
    }

    private fun removeOwnedCommandLinks(workspaceDirectory: File, ledger: File) {
        ledgerEntries(ledger).forEach { (commandName, targetPath) ->
            val link = File(workspaceDirectory, ".kf/bin/$commandName").toPath()
            if (!Files.isSymbolicLink(link)) return@forEach
            if (Files.readSymbolicLink(link).toString() == targetPath) {
                Files.deleteIfExists(link)
            }
        }
    }

    private fun restoreOwnedCommandLinks(workspaceDirectory: File, ledger: File) {
        val binRoot = File(workspaceDirectory, ".kf/bin").also { it.mkdirs() }
        ledgerEntries(ledger).forEach { (commandName, targetPath) ->
            val link = File(binRoot, commandName).toPath()
            if (pathExists(link.toFile())) return@forEach
            Files.createSymbolicLink(link, Path.of(targetPath))
        }
    }

    private fun ledgerEntries(ledger: File): List<Pair<String, String>> {
        if (!ledger.isFile) return emptyList()
        return ledger.readLines().mapNotNull { line ->
            val columns = line.split('\t', limit = 2)
            val commandName = columns.getOrNull(0).orEmpty()
            val targetPath = columns.getOrNull(1).orEmpty()
            if (!VALID_COMMAND_NAME.matches(commandName) || targetPath.isBlank()) null
            else commandName to targetPath
        }
    }

    private fun cleanupTransactionArtifacts(
        softwareRoot: File,
        resourceId: String,
        updateLock: File,
        transactionState: File,
    ) {
        if (pathExists(updateLock)) deleteOwnedPath(updateLock)
        Files.deleteIfExists(transactionState.toPath())
        softwareRoot.listFiles().orEmpty()
            .filter { file ->
                file.name.startsWith("$resourceId.kite-transaction.tmp.") ||
                    file.name.startsWith("$resourceId.kite-old-commands.") ||
                    file.name.startsWith("$resourceId.kite-failed-commands.")
            }
            .forEach(::deleteOwnedPath)
    }

    private fun checkDirectChild(parent: File, child: File) {
        check(child.parentFile?.absoluteFile?.normalize() == parent) {
            "资源事务路径越界：${child.absolutePath}"
        }
    }

    private fun pathExists(path: File): Boolean =
        Files.exists(path.toPath(), LinkOption.NOFOLLOW_LINKS)

    private fun move(source: File, destination: File) {
        try {
            Files.move(source.toPath(), destination.toPath(), StandardCopyOption.ATOMIC_MOVE)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(source.toPath(), destination.toPath())
        }
    }

    private fun deleteOwnedPath(path: File) {
        if (!pathExists(path)) return
        Files.walkFileTree(path.toPath(), object : SimpleFileVisitor<Path>() {
            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                Files.delete(file)
                return FileVisitResult.CONTINUE
            }

            override fun postVisitDirectory(dir: Path, exc: java.io.IOException?): FileVisitResult {
                if (exc != null) throw exc
                Files.delete(dir)
                return FileVisitResult.CONTINUE
            }
        })
    }

    private fun result(
        resourceId: String,
        disposition: ResourceInstallRecoveryDisposition,
        operation: String = KiteResourceInstallRecipes.OP_UPDATE,
        targetVersion: String = "",
        message: String,
    ) = ResourceInstallTransactionRecoveryResult(
        resourceId = resourceId,
        disposition = disposition,
        operation = operation,
        targetVersion = targetVersion,
        message = message,
    )

    companion object {
        private const val COMMAND_LEDGER = ".kite-managed-commands"
        private const val LOCK_OWNER_FILE = "owner"
        private const val STATE_PHASE = "phase"
        private const val STATE_OPERATION = "operation"
        private const val STATE_TARGET_VERSION = "target_version"
        private const val PHASE_COMMITTED = "committed"
        private val VALID_COMMAND_NAME = Regex("[A-Za-z0-9._-]{1,80}")
        private val VALID_STATE_VALUE = Regex("[A-Za-z0-9._:+-]{0,128}")
        private val RECOVERABLE_OPERATIONS = setOf(
            KiteResourceInstallRecipes.OP_UPDATE,
            KiteResourceInstallRecipes.OP_REINSTALL,
        )

        private fun readProcessStartTime(pid: Int): String? {
            val stat = runCatching { File("/proc/$pid/stat").readText() }.getOrNull() ?: return null
            val commandEnd = stat.lastIndexOf(") ")
            if (commandEnd < 0) return null
            return stat.substring(commandEnd + 2)
                .trim()
                .split(Regex("\\s+"))
                .getOrNull(19)
        }
    }
}
