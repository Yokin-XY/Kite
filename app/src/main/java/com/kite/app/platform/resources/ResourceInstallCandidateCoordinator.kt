package com.kite.app.platform.resources

import com.kite.app.application.runs.RunExecutionEnvironment
import com.kite.app.application.runs.RunExecutionFilesystemBinding
import com.kite.app.resources.KiteResourceInstallRecipes
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.BasicFileAttributes
import java.util.concurrent.ConcurrentHashMap

/**
 * 把单个资源的安装根和公共命令目录投影到本次运行独占的候选目录。
 *
 * 资源脚本始终看到稳定的容器绝对路径；只有验证通过后，Android 才在同一文件系统内
 * 原子切换正式安装根，并按资源命令账本合并公共命令。不同资源的候选目录互不共享，
 * 是否能够并发仍由上层 writeScopes 决定。
 */
internal class ResourceInstallCandidateCoordinator {
    private enum class Phase { PREPARED, ROOT_COMMITTED }

    private data class CandidateRecord(
        val resourceId: String,
        val runInstanceId: String,
        val formalRoot: File,
        val formalBin: File,
        val pendingRoot: File,
        val candidateRoot: File,
        val candidateBin: File,
        val backupRoot: File,
        val commandBackupRoot: File,
        val preservePaths: List<String>,
        @Volatile var phase: Phase = Phase.PREPARED,
        @Volatile var commandNames: Set<String> = emptySet(),
    )

    private val records = ConcurrentHashMap<String, CandidateRecord>()

    fun begin(
        workspaceDirectory: File,
        resourceId: String,
        runInstanceId: String,
        guestInstallRoot: String,
        preservePaths: List<String>,
    ): Result<Unit> = runCatching {
        val safeResourceId = requireSafeId(resourceId, "resourceId")
        val safeRunId = requireSafeId(runInstanceId, "runInstanceId")
        val expectedGuestRoot = KiteResourceInstallRecipes.softwarePath(safeResourceId)
        require(guestInstallRoot.trim().removeSuffix("/") == expectedGuestRoot) {
            "resource_candidate_install_root_unsupported:$guestInstallRoot"
        }
        require(!records.containsKey(runInstanceId)) { "resource_candidate_run_already_prepared" }

        val workspace = workspaceDirectory.absoluteFile.normalize()
        val softwareRoot = File(workspace, ".kf/software").absoluteFile.normalize()
        val formalRoot = File(softwareRoot, safeResourceId).absoluteFile.normalize()
        val formalBin = File(workspace, ".kf/bin").absoluteFile.normalize()
        require(formalRoot.toPath().startsWith(softwareRoot.toPath())) {
            "resource_candidate_formal_root_outside_software"
        }
        require(!Files.isSymbolicLink(formalRoot.toPath()) && (!formalRoot.exists() || formalRoot.isDirectory)) {
            "resource_candidate_formal_root_unsafe"
        }
        require(!Files.isSymbolicLink(formalBin.toPath()) && (!formalBin.exists() || formalBin.isDirectory)) {
            "resource_candidate_formal_bin_unsafe"
        }
        val pendingRunRoot = File(softwareRoot, ".kite-pending/$safeRunId").absoluteFile.normalize()
        val pendingRoot = File(pendingRunRoot, safeResourceId).absoluteFile.normalize()
        require(pendingRoot.toPath().startsWith(File(softwareRoot, ".kite-pending").toPath())) {
            "resource_candidate_pending_root_outside_software"
        }
        deleteTree(pendingRoot.toPath())
        val candidateRoot = File(pendingRoot, "install")
        val candidateBin = File(pendingRoot, "bin")
        val backupRoot = File(pendingRoot, "previous-install")
        val commandBackupRoot = File(pendingRoot, "previous-commands")
        require(candidateRoot.mkdirs() || candidateRoot.isDirectory) {
            "resource_candidate_install_directory_unavailable"
        }
        require(candidateBin.mkdirs() || candidateBin.isDirectory) {
            "resource_candidate_bin_directory_unavailable"
        }
        require(formalBin.mkdirs() || formalBin.isDirectory) {
            "resource_candidate_formal_bin_unavailable"
        }
        if (formalRoot.exists()) copyTree(formalRoot.toPath(), candidateRoot.toPath())
        copyTree(formalBin.toPath(), candidateBin.toPath())

        val record = CandidateRecord(
            resourceId = safeResourceId,
            runInstanceId = runInstanceId,
            formalRoot = formalRoot,
            formalBin = formalBin,
            pendingRoot = pendingRoot,
            candidateRoot = candidateRoot,
            candidateBin = candidateBin,
            backupRoot = backupRoot,
            commandBackupRoot = commandBackupRoot,
            preservePaths = preservePaths.map(::requireSafeRelativePath).distinct(),
        )
        check(records.putIfAbsent(runInstanceId, record) == null) {
            "resource_candidate_run_already_prepared"
        }
    }

    fun environmentForRun(runInstanceId: String): RunExecutionEnvironment {
        val record = records[runInstanceId] ?: return RunExecutionEnvironment()
        return RunExecutionEnvironment(
            variables = mapOf(CANDIDATE_ENV to "1"),
            filesystemBindings = listOf(
                RunExecutionFilesystemBinding(
                    sourcePath = record.candidateRoot.absolutePath,
                    targetPath = KiteResourceInstallRecipes.softwarePath(record.resourceId),
                    role = "resource_candidate_install",
                ),
                RunExecutionFilesystemBinding(
                    sourcePath = record.candidateBin.absolutePath,
                    targetPath = KiteResourceInstallRecipes.WORKSPACE_BIN_ROOT,
                    role = "resource_candidate_bin",
                ),
            ),
        )
    }

    fun commit(resourceId: String, runInstanceId: String): Result<Unit> = runCatching {
        val record = requireRecord(resourceId, runInstanceId)
        require(record.phase == Phase.PREPARED) { "resource_candidate_not_prepared" }
        val oldLedger = readCommandLedger(File(record.formalRoot, COMMAND_LEDGER))
        val newLedger = readCommandLedger(File(record.candidateRoot, COMMAND_LEDGER))
        val commandNames = (oldLedger.keys + newLedger.keys).toSortedSet()
        record.commandNames = commandNames
        snapshotCommands(record, commandNames)

        var previousMoved = false
        var candidateMoved = false
        try {
            if (record.formalRoot.exists()) {
                atomicMove(record.formalRoot.toPath(), record.backupRoot.toPath())
                previousMoved = true
            }
            atomicMove(record.candidateRoot.toPath(), record.formalRoot.toPath())
            candidateMoved = true
            applyCommands(record, oldLedger, newLedger)
            record.phase = Phase.ROOT_COMMITTED
        } catch (error: Throwable) {
            restoreAfterFailedCommit(record, previousMoved, candidateMoved)
                .exceptionOrNull()
                ?.let(error::addSuppressed)
            throw error
        }
    }

    fun finalize(resourceId: String, runInstanceId: String): Result<Unit> = runCatching {
        val record = requireRecord(resourceId, runInstanceId)
        require(record.phase == Phase.ROOT_COMMITTED) { "resource_candidate_not_committed" }
        clearRetainedPaths(record)
        deleteOwnedPending(record)
        records.remove(runInstanceId, record)
    }

    fun rollback(resourceId: String, runInstanceId: String): Result<Unit> {
        val record = records[runInstanceId] ?: return Result.success(Unit)
        if (record.resourceId != KiteResourceInstallRecipes.safeId(resourceId)) {
            return Result.failure(IllegalStateException("resource_candidate_owner_mismatch"))
        }
        return runCatching {
            if (record.phase == Phase.ROOT_COMMITTED) {
                restoreCommitted(record).getOrThrow()
            }
            deleteOwnedPending(record)
            records.remove(runInstanceId, record)
        }
    }

    private fun requireRecord(resourceId: String, runInstanceId: String): CandidateRecord {
        val record = records[runInstanceId] ?: error("resource_candidate_missing")
        require(record.resourceId == KiteResourceInstallRecipes.safeId(resourceId)) {
            "resource_candidate_owner_mismatch"
        }
        return record
    }

    private fun snapshotCommands(record: CandidateRecord, commandNames: Set<String>) {
        deleteTree(record.commandBackupRoot.toPath())
        require(record.commandBackupRoot.mkdirs() || record.commandBackupRoot.isDirectory) {
            "resource_candidate_command_backup_unavailable"
        }
        commandNames.forEach { commandName ->
            val current = File(record.formalBin, commandName).toPath()
            if (Files.exists(current) || Files.isSymbolicLink(current)) {
                copyNode(current, File(record.commandBackupRoot, commandName).toPath())
            }
        }
    }

    private fun applyCommands(
        record: CandidateRecord,
        oldLedger: Map<String, String>,
        newLedger: Map<String, String>,
    ) {
        record.commandNames.forEach { commandName ->
            val formalCommand = File(record.formalBin, commandName).toPath()
            val candidateCommand = File(record.candidateBin, commandName).toPath()
            val newTarget = newLedger[commandName]
            if (newTarget != null) {
                require(Files.exists(candidateCommand) || Files.isSymbolicLink(candidateCommand)) {
                    "resource_candidate_command_missing:$commandName"
                }
                replaceNodeAtomically(candidateCommand, formalCommand)
            } else {
                val oldTarget = oldLedger[commandName]
                if (oldTarget != null && Files.isSymbolicLink(formalCommand)) {
                    val currentTarget = Files.readSymbolicLink(formalCommand).toString()
                    if (currentTarget == oldTarget) Files.deleteIfExists(formalCommand)
                }
            }
        }
    }

    private fun restoreAfterFailedCommit(
        record: CandidateRecord,
        previousMoved: Boolean,
        candidateMoved: Boolean,
    ): Result<Unit> = runCatching {
        if (candidateMoved && record.formalRoot.exists()) {
            atomicMove(record.formalRoot.toPath(), record.candidateRoot.toPath())
        }
        if (previousMoved && record.backupRoot.exists()) {
            atomicMove(record.backupRoot.toPath(), record.formalRoot.toPath())
        }
        restoreCommands(record)
    }

    private fun restoreCommitted(record: CandidateRecord): Result<Unit> = runCatching {
        if (record.formalRoot.exists()) {
            deleteTree(record.candidateRoot.toPath())
            atomicMove(record.formalRoot.toPath(), record.candidateRoot.toPath())
        }
        if (record.backupRoot.exists()) {
            atomicMove(record.backupRoot.toPath(), record.formalRoot.toPath())
        }
        restoreCommands(record)
        record.phase = Phase.PREPARED
    }

    private fun restoreCommands(record: CandidateRecord) {
        record.commandNames.forEach { commandName ->
            val formalCommand = File(record.formalBin, commandName).toPath()
            Files.deleteIfExists(formalCommand)
            val backup = File(record.commandBackupRoot, commandName).toPath()
            if (Files.exists(backup) || Files.isSymbolicLink(backup)) {
                copyNode(backup, formalCommand)
            }
        }
    }

    private fun clearRetainedPaths(record: CandidateRecord) {
        val retainedRoot = File(record.formalRoot.absolutePath + RETAINED_SUFFIX).absoluteFile.normalize()
        record.preservePaths.forEach { relative ->
            val target = File(retainedRoot, relative).absoluteFile.normalize()
            require(target.toPath().startsWith(retainedRoot.toPath())) {
                "resource_candidate_retained_path_outside_root"
            }
            deleteTree(target.toPath())
        }
        if (retainedRoot.isDirectory && retainedRoot.list().isNullOrEmpty()) {
            Files.deleteIfExists(retainedRoot.toPath())
        }
    }

    private fun deleteOwnedPending(record: CandidateRecord) {
        deleteTree(record.pendingRoot.toPath())
        val runRoot = record.pendingRoot.parentFile
        if (runRoot?.isDirectory == true && runRoot.list().isNullOrEmpty()) {
            Files.deleteIfExists(runRoot.toPath())
        }
    }

    private fun readCommandLedger(file: File): Map<String, String> {
        if (!file.isFile) return emptyMap()
        return buildMap {
            file.forEachLine { line ->
                val parts = line.split('\t', limit = 2)
                if (parts.size != 2) return@forEachLine
                val command = requireSafeCommandName(parts[0])
                val target = parts[1].trim()
                require(target.startsWith("/") && '\u0000' !in target) {
                    "resource_candidate_command_target_invalid:$command"
                }
                put(command, target)
            }
        }
    }

    private fun replaceNodeAtomically(source: Path, target: Path) {
        val temp = target.resolveSibling(".${target.fileName}.kite-candidate-${System.nanoTime()}")
        Files.deleteIfExists(temp)
        copyNode(source, temp)
        try {
            Files.move(
                temp,
                target,
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun atomicMove(source: Path, target: Path) {
        target.parent?.let(Files::createDirectories)
        Files.move(source, target, StandardCopyOption.ATOMIC_MOVE)
    }

    private fun copyTree(source: Path, target: Path) {
        if (!Files.exists(source) && !Files.isSymbolicLink(source)) return
        Files.walkFileTree(source, object : SimpleFileVisitor<Path>() {
            override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                val relative = source.relativize(dir)
                Files.createDirectories(target.resolve(relative))
                return FileVisitResult.CONTINUE
            }

            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                copyNode(file, target.resolve(source.relativize(file)))
                return FileVisitResult.CONTINUE
            }
        })
    }

    private fun copyNode(source: Path, target: Path) {
        target.parent?.let(Files::createDirectories)
        Files.deleteIfExists(target)
        if (Files.isSymbolicLink(source)) {
            Files.createSymbolicLink(target, Files.readSymbolicLink(source))
        } else {
            Files.copy(source, target, StandardCopyOption.COPY_ATTRIBUTES)
        }
    }

    private fun deleteTree(path: Path) {
        if (!Files.exists(path) && !Files.isSymbolicLink(path)) return
        Files.walkFileTree(path, object : SimpleFileVisitor<Path>() {
            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                Files.deleteIfExists(file)
                return FileVisitResult.CONTINUE
            }

            override fun postVisitDirectory(dir: Path, exc: java.io.IOException?): FileVisitResult {
                if (exc != null) throw exc
                Files.deleteIfExists(dir)
                return FileVisitResult.CONTINUE
            }
        })
    }

    private fun requireSafeId(value: String, field: String): String {
        val safe = KiteResourceInstallRecipes.safeId(value)
        require(safe.isNotBlank() && safe.length <= 160) { "$field is unsafe" }
        return safe
    }

    private fun requireSafeCommandName(value: String): String {
        val safe = value.trim()
        require(safe.isNotBlank() && safe.length <= 128 && safe.all {
            it.isLetterOrDigit() || it == '-' || it == '_' || it == '.'
        }) { "resource_candidate_command_name_unsafe" }
        require(!safe.startsWith('.')) { "resource_candidate_command_name_hidden" }
        return safe
    }

    private fun requireSafeRelativePath(value: String): String {
        val safe = value.trim().replace('\\', '/').trim('/')
        require(safe.isNotBlank() && safe.split('/').none { it.isBlank() || it == "." || it == ".." }) {
            "resource_candidate_preserve_path_unsafe"
        }
        return safe
    }

    companion object {
        const val CANDIDATE_ENV = "KITE_RESOURCE_CANDIDATE"
        private const val COMMAND_LEDGER = ".kite-managed-commands"
        private const val RETAINED_SUFFIX = ".kite-retained"
    }
}
