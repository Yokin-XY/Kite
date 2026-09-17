package com.kite.app.platform.resources

import com.kite.app.application.runs.RunExecutionEnvironment
import com.kite.app.application.runs.RunExecutionFilesystemBinding
import com.kite.app.resources.KiteResourceInstallRecipes
import com.kite.app.resources.ResourceInstallRecoveryDisposition
import com.kite.app.resources.ResourceInstallTransactionRecoveryResult
import java.io.File
import java.io.FileOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.BasicFileAttributes
import java.util.Properties
import java.util.concurrent.ConcurrentHashMap

/**
 * 把单个资源的安装根和公共命令目录投影到本次运行独占的候选目录。
 *
 * 资源脚本始终看到稳定的容器绝对路径；只有验证通过后，Android 才在同一文件系统内
 * 原子切换正式安装根，并按资源命令账本合并公共命令。不同资源的候选目录互不共享，
 * 是否能够并发仍由上层 writeScopes 决定。
 */
internal class ResourceInstallCandidateCoordinator(
    private val phaseCheckpoint: (String) -> Unit = {},
) {
    private enum class Phase {
        PREPARING,
        PREPARED,
        INSTALLING,
        VERIFIED,
        COMMITTING,
        COMMITTED,
        ROLLING_BACK,
        ROLLED_BACK,
    }

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
        val operation: String,
        val targetVersion: String,
        val previousVersion: String,
        @Volatile var phase: Phase = Phase.PREPARING,
        @Volatile var commandNames: Set<String> = emptySet(),
    ) {
        val stateFile: File
            get() = File(pendingRoot, STATE_FILE_NAME)
    }

    private val records = ConcurrentHashMap<String, CandidateRecord>()

    fun begin(
        workspaceDirectory: File,
        resourceId: String,
        runInstanceId: String,
        guestInstallRoot: String,
        preservePaths: List<String>,
        operation: String = KiteResourceInstallRecipes.OP_INSTALL,
        targetVersion: String = "",
        previousVersion: String = "",
    ): Result<Unit> {
        var record: CandidateRecord? = null
        return runCatching {
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
        record = CandidateRecord(
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
            operation = requireOperation(operation),
            targetVersion = requireSafeStateValue(targetVersion, "targetVersion"),
            previousVersion = requireSafeStateValue(previousVersion, "previousVersion"),
        )
        val activeRecord = checkNotNull(record)
        check(records.putIfAbsent(runInstanceId, activeRecord) == null) {
            "resource_candidate_run_already_prepared"
        }
        writeRecord(activeRecord)
        if (formalRoot.exists()) copyTree(formalRoot.toPath(), candidateRoot.toPath())
        copyTree(formalBin.toPath(), candidateBin.toPath())
        updatePhase(activeRecord, Phase.PREPARED)
        }.onFailure {
            record?.let { failed ->
                records.remove(runInstanceId, failed)
                runCatching { deleteOwnedPending(failed) }
            }
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

    fun markInstalling(resourceId: String, runInstanceId: String): Result<Unit> = runCatching {
        val record = requireRecord(resourceId, runInstanceId)
        require(record.phase == Phase.PREPARED || record.phase == Phase.INSTALLING) {
            "resource_candidate_install_phase_invalid:${record.phase}"
        }
        updatePhase(record, Phase.INSTALLING)
    }

    fun markVerified(resourceId: String, runInstanceId: String): Result<Unit> = runCatching {
        val record = requireRecord(resourceId, runInstanceId)
        require(record.phase == Phase.INSTALLING || record.phase == Phase.VERIFIED) {
            "resource_candidate_verify_phase_invalid:${record.phase}"
        }
        updatePhase(record, Phase.VERIFIED)
    }

    fun commit(resourceId: String, runInstanceId: String): Result<Unit> = runCatching {
        val record = requireRecord(resourceId, runInstanceId)
        require(record.phase == Phase.VERIFIED) { "resource_candidate_not_verified" }
        val oldLedger = readCommandLedger(File(record.formalRoot, COMMAND_LEDGER))
        val newLedger = readCommandLedger(File(record.candidateRoot, COMMAND_LEDGER))
        val commandNames = (oldLedger.keys + newLedger.keys).toSortedSet()
        record.commandNames = commandNames
        rewriteCandidateInternalAbsoluteSymlinks(record)
        snapshotCommands(record, commandNames)
        updatePhase(record, Phase.COMMITTING)

        var previousMoved = false
        var candidateMoved = false
        try {
            if (record.formalRoot.exists()) {
                atomicMove(record.formalRoot.toPath(), record.backupRoot.toPath())
                previousMoved = true
            }
            atomicMove(record.candidateRoot.toPath(), record.formalRoot.toPath())
            candidateMoved = true
            phaseCheckpoint(CHECKPOINT_ROOT_ACTIVATED)
            applyCommands(record, oldLedger, newLedger)
            updatePhase(record, Phase.COMMITTED)
        } catch (error: Exception) {
            restoreAfterFailedCommit(record, previousMoved, candidateMoved)
                .exceptionOrNull()
                ?.let(error::addSuppressed)
            if (error.suppressed.isEmpty()) updatePhase(record, Phase.VERIFIED)
            throw error
        }
    }

    fun finalize(resourceId: String, runInstanceId: String): Result<Unit> = runCatching {
        val record = requireRecord(resourceId, runInstanceId)
        require(record.phase == Phase.COMMITTED) { "resource_candidate_not_committed" }
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
            updatePhase(record, Phase.ROLLING_BACK)
            if (record.backupRoot.exists() || !record.candidateRoot.exists()) {
                restoreCommitted(record).getOrThrow()
            }
            updatePhase(record, Phase.ROLLED_BACK)
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
        val pendingRoot = runRoot?.parentFile
        if (pendingRoot?.isDirectory == true && pendingRoot.list().isNullOrEmpty()) {
            Files.deleteIfExists(pendingRoot.toPath())
        }
    }

    fun recoverInterrupted(workspaceDirectory: File): List<ResourceInstallTransactionRecoveryResult> {
        val workspace = workspaceDirectory.absoluteFile.normalize()
        val pendingRoot = File(workspace, ".kf/software/.kite-pending").absoluteFile.normalize()
        if (!pendingRoot.isDirectory) return emptyList()
        return pendingRoot.listFiles().orEmpty()
            .filter(File::isDirectory)
            .sortedBy(File::getName)
            .flatMap { runRoot ->
                runRoot.listFiles().orEmpty()
                    .filter(File::isDirectory)
                    .sortedBy(File::getName)
            }
            .filter { candidateRoot -> File(candidateRoot, STATE_FILE_NAME).isFile }
            .map { candidateRoot -> recoverCandidate(workspace, candidateRoot) }
    }

    private fun recoverCandidate(
        workspace: File,
        pendingRoot: File,
    ): ResourceInstallTransactionRecoveryResult {
        val fallbackResourceId = KiteResourceInstallRecipes.safeId(pendingRoot.name)
        return runCatching {
            val record = readRecord(workspace, pendingRoot)
            if (records.containsKey(record.runInstanceId)) {
                return@runCatching recoveryResult(
                    record,
                    ResourceInstallRecoveryDisposition.ACTIVE,
                    "资源安装仍在当前进程中运行，未接管候选事务",
                )
            }
            when (record.phase) {
                Phase.COMMITTED -> {
                    require(record.formalRoot.isDirectory) {
                        "resource_candidate_committed_root_missing"
                    }
                    clearRetainedPaths(record)
                    deleteOwnedPending(record)
                    recoveryResult(
                        record,
                        ResourceInstallRecoveryDisposition.COMMITTED,
                        "上次资源安装已提交，已补齐登记并清理候选残留",
                    )
                }
                Phase.COMMITTING,
                Phase.ROLLING_BACK -> {
                    restoreInterrupted(record)
                    val restored = record.formalRoot.isDirectory
                    deleteOwnedPending(record)
                    recoveryResult(
                        record,
                        if (restored) {
                            ResourceInstallRecoveryDisposition.RESTORED
                        } else {
                            ResourceInstallRecoveryDisposition.FAILED
                        },
                        if (restored) {
                            "上次资源安装在提交边界中断，已恢复更新前版本"
                        } else {
                            "上次资源安装在提交边界中断，候选已撤销但没有旧版本"
                        },
                    )
                }
                Phase.PREPARING,
                Phase.PREPARED,
                Phase.INSTALLING,
                Phase.VERIFIED,
                Phase.ROLLED_BACK -> {
                    val previousRootStillUsable = record.formalRoot.isDirectory
                    deleteOwnedPending(record)
                    recoveryResult(
                        record,
                        if (previousRootStillUsable) {
                            ResourceInstallRecoveryDisposition.RESTORED
                        } else {
                            ResourceInstallRecoveryDisposition.FAILED
                        },
                        if (previousRootStillUsable) {
                            "上次资源安装在正式切换前中断，原版本保持可用"
                        } else {
                            "上次资源安装在正式切换前中断，已清理未完成候选"
                        },
                    )
                }
            }
        }.getOrElse { error ->
            ResourceInstallTransactionRecoveryResult(
                resourceId = fallbackResourceId,
                disposition = ResourceInstallRecoveryDisposition.FAILED,
                message = "资源候选事务自动恢复失败：${error.message ?: error.javaClass.simpleName}",
            )
        }
    }

    private fun restoreInterrupted(record: CandidateRecord) {
        val candidateWasActivated = !record.candidateRoot.exists()
        if (record.backupRoot.exists()) {
            if (record.formalRoot.exists()) deleteTree(record.formalRoot.toPath())
            atomicMove(record.backupRoot.toPath(), record.formalRoot.toPath())
        } else if (candidateWasActivated && record.formalRoot.exists()) {
            deleteTree(record.formalRoot.toPath())
        }
        restoreCommands(record)
    }

    private fun recoveryResult(
        record: CandidateRecord,
        disposition: ResourceInstallRecoveryDisposition,
        message: String,
    ) = ResourceInstallTransactionRecoveryResult(
        resourceId = record.resourceId,
        disposition = disposition,
        operation = record.operation,
        targetVersion = record.targetVersion,
        message = message,
    )

    private fun updatePhase(record: CandidateRecord, phase: Phase) {
        record.phase = phase
        writeRecord(record)
    }

    private fun writeRecord(record: CandidateRecord) {
        val properties = Properties().apply {
            setProperty(STATE_SCHEMA_KEY, STATE_SCHEMA)
            setProperty(STATE_PHASE_KEY, record.phase.name)
            setProperty(STATE_RESOURCE_ID_KEY, record.resourceId)
            setProperty(STATE_RUN_ID_KEY, record.runInstanceId)
            setProperty(STATE_OPERATION_KEY, record.operation)
            setProperty(STATE_TARGET_VERSION_KEY, record.targetVersion)
            setProperty(STATE_PREVIOUS_VERSION_KEY, record.previousVersion)
            setProperty(STATE_PRESERVE_PATHS_KEY, record.preservePaths.joinToString("\t"))
            setProperty(STATE_COMMAND_NAMES_KEY, record.commandNames.joinToString("\t"))
        }
        val stateFile = record.stateFile
        stateFile.parentFile?.mkdirs()
        val temp = File(stateFile.parentFile, ".${stateFile.name}.tmp-${System.nanoTime()}")
        FileOutputStream(temp).use { output ->
            properties.store(output, null)
            output.fd.sync()
        }
        try {
            Files.move(
                temp.toPath(),
                stateFile.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temp.toPath(), stateFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun readRecord(workspace: File, pendingRoot: File): CandidateRecord {
        val properties = Properties().apply {
            File(pendingRoot, STATE_FILE_NAME).inputStream().use { input -> load(input) }
        }
        require(properties.getProperty(STATE_SCHEMA_KEY) == STATE_SCHEMA) {
            "resource_candidate_state_schema_unsupported"
        }
        val resourceId = requireSafeId(properties.getProperty(STATE_RESOURCE_ID_KEY).orEmpty(), "resourceId")
        val runInstanceId = properties.getProperty(STATE_RUN_ID_KEY).orEmpty()
        val safeRunId = requireSafeId(runInstanceId, "runInstanceId")
        val softwareRoot = File(workspace, ".kf/software").absoluteFile.normalize()
        val expectedPending = File(softwareRoot, ".kite-pending/$safeRunId/$resourceId")
            .absoluteFile
            .normalize()
        require(pendingRoot.absoluteFile.normalize() == expectedPending) {
            "resource_candidate_state_owner_mismatch"
        }
        val preservePaths = properties.getProperty(STATE_PRESERVE_PATHS_KEY).orEmpty()
            .split('\t')
            .filter(String::isNotBlank)
            .map(::requireSafeRelativePath)
        val commandNames = properties.getProperty(STATE_COMMAND_NAMES_KEY).orEmpty()
            .split('\t')
            .filter(String::isNotBlank)
            .map(::requireSafeCommandName)
            .toSortedSet()
        return CandidateRecord(
            resourceId = resourceId,
            runInstanceId = runInstanceId,
            formalRoot = File(softwareRoot, resourceId),
            formalBin = File(workspace, ".kf/bin"),
            pendingRoot = expectedPending,
            candidateRoot = File(expectedPending, "install"),
            candidateBin = File(expectedPending, "bin"),
            backupRoot = File(expectedPending, "previous-install"),
            commandBackupRoot = File(expectedPending, "previous-commands"),
            preservePaths = preservePaths,
            operation = requireOperation(properties.getProperty(STATE_OPERATION_KEY).orEmpty()),
            targetVersion = requireSafeStateValue(
                properties.getProperty(STATE_TARGET_VERSION_KEY).orEmpty(),
                "targetVersion",
            ),
            previousVersion = requireSafeStateValue(
                properties.getProperty(STATE_PREVIOUS_VERSION_KEY).orEmpty(),
                "previousVersion",
            ),
            phase = Phase.valueOf(properties.getProperty(STATE_PHASE_KEY).orEmpty()),
            commandNames = commandNames,
        )
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

    /**
     * 某些安装器会先解析 PRoot 绑定，再把候选目录的宿主绝对路径写进软链接。
     * 候选根原子切换到正式根后，这类链接会立即断开。提交前只重写仍位于本候选根内的
     * 绝对目标，并改成相对链接；外部绝对链接和普通相对链接保持原样。
     */
    private fun rewriteCandidateInternalAbsoluteSymlinks(record: CandidateRecord) {
        val candidateRoot = record.candidateRoot.toPath().toAbsolutePath().normalize()
        Files.walkFileTree(candidateRoot, object : SimpleFileVisitor<Path>() {
            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                if (!Files.isSymbolicLink(file)) return FileVisitResult.CONTINUE
                val originalTarget = Files.readSymbolicLink(file)
                val relativeTarget = candidateRelativeSymlinkTarget(
                    candidateRoot = candidateRoot,
                    link = file,
                    target = originalTarget,
                ) ?: return FileVisitResult.CONTINUE
                val replacement = file.resolveSibling(".${file.fileName}.kite-relocated-${System.nanoTime()}")
                Files.deleteIfExists(replacement)
                Files.createSymbolicLink(replacement, relativeTarget)
                try {
                    Files.move(
                        replacement,
                        file,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING,
                    )
                } catch (_: AtomicMoveNotSupportedException) {
                    Files.move(replacement, file, StandardCopyOption.REPLACE_EXISTING)
                } finally {
                    Files.deleteIfExists(replacement)
                }
                return FileVisitResult.CONTINUE
            }
        })
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

    private fun requireOperation(value: String): String {
        require(value in TRANSACTION_OPERATIONS) { "resource_candidate_operation_unsupported:$value" }
        return value
    }

    private fun requireSafeStateValue(value: String, field: String): String {
        val safe = value.trim()
        require(STATE_VALUE.matches(safe)) { "resource_candidate_${field}_unsafe" }
        return safe
    }

    companion object {
        const val CANDIDATE_ENV = "KITE_RESOURCE_CANDIDATE"
        internal const val CHECKPOINT_ROOT_ACTIVATED = "root_activated"
        private const val COMMAND_LEDGER = ".kite-managed-commands"
        private const val RETAINED_SUFFIX = ".kite-retained"
        private const val STATE_FILE_NAME = "transaction.properties"
        private const val STATE_SCHEMA_KEY = "schema"
        private const val STATE_PHASE_KEY = "phase"
        private const val STATE_RESOURCE_ID_KEY = "resourceId"
        private const val STATE_RUN_ID_KEY = "runInstanceId"
        private const val STATE_OPERATION_KEY = "operation"
        private const val STATE_TARGET_VERSION_KEY = "targetVersion"
        private const val STATE_PREVIOUS_VERSION_KEY = "previousVersion"
        private const val STATE_PRESERVE_PATHS_KEY = "preservePaths"
        private const val STATE_COMMAND_NAMES_KEY = "commandNames"
        private const val STATE_SCHEMA = "kite_resource_candidate_transaction_v1"
        private val STATE_VALUE = Regex("[A-Za-z0-9._:+-]{0,128}")
        private val TRANSACTION_OPERATIONS = setOf(
            KiteResourceInstallRecipes.OP_INSTALL,
            KiteResourceInstallRecipes.OP_UPDATE,
            KiteResourceInstallRecipes.OP_REINSTALL,
            KiteResourceInstallRecipes.OP_REPAIR,
        )
    }
}

internal fun candidateRelativeSymlinkTarget(candidateRoot: Path, link: Path, target: Path): Path? {
    if (!target.isAbsolute) return null
    val normalizedRoot = candidateRoot.toAbsolutePath().normalize()
    val normalizedTarget = target.normalize()
    if (!normalizedTarget.startsWith(normalizedRoot)) return null
    return link.toAbsolutePath().normalize().parent.relativize(normalizedTarget)
}
