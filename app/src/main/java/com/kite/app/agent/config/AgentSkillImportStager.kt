package com.kite.app.agent.config

import com.kite.app.foundation.workspace.KiteStorageContract
import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.StandardCopyOption
import java.util.UUID
import java.util.zip.ZipInputStream

/**
 * 把用户在 `/workspace` 中选择的 Skill 文件夹复制到一次性受控导入区。
 *
 * 这里不安装 Skill，也不修改原始文件；Agent 适配器仍负责最终格式校验和原生写入。
 */
internal class AgentSkillImportStager(
    private val hostWorkspaceRoot: File,
) {
    fun stage(selectedContainerPath: String): AgentSkillImportStage {
        require(KiteStorageContract.isSelectableProjectPath(selectedContainerPath)) {
            "请选择 /workspace 下的 Skill 文件夹"
        }
        val workspaceRoot = hostWorkspaceRoot.canonicalFile
        val source = KiteStorageContract.resolveHostWorkspacePath(workspaceRoot, selectedContainerPath)
            ?.canonicalFile
            ?: throw IllegalArgumentException("Skill 文件夹不属于 /workspace")
        require(source.isDirectory && source.toPath().startsWith(workspaceRoot.toPath())) {
            "Skill 文件夹不存在或已经越出工作区"
        }

        return stageSource(source)
    }

    /** 从文档选择器或市场下载的 ZIP 中暂存单个 Skill。 */
    fun stageArchive(input: InputStream): AgentSkillImportStage {
        val workspaceRoot = hostWorkspaceRoot.canonicalFile
        val extractionRoot = File(workspaceRoot, ARCHIVE_ROOT).canonicalFile
        require(extractionRoot.toPath().startsWith(workspaceRoot.toPath())) { "Skill 解压目录越界" }
        require(extractionRoot.mkdirs() || extractionRoot.isDirectory) { "无法准备 Skill 解压目录" }
        val transaction = File(extractionRoot, ".archive-${UUID.randomUUID()}")
        require(transaction.mkdir()) { "无法创建 Skill 解压事务" }
        return try {
            extractArchive(input, transaction)
            val skillDirectories = Files.walk(transaction.toPath()).use { stream ->
                stream.iterator().asSequence()
                    .filter { path ->
                        Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS) &&
                            Files.isRegularFile(path.resolve(SKILL_FILE), LinkOption.NOFOLLOW_LINKS)
                    }
                    .map(java.nio.file.Path::toFile)
                    .toList()
            }
            require(skillDirectories.size == 1) {
                if (skillDirectories.isEmpty()) "Skill ZIP 中缺少 SKILL.md" else "一个 ZIP 只能导入一个 Skill"
            }
            stageSource(skillDirectories.single())
        } finally {
            transaction.deleteRecursively()
        }
    }

    private fun stageSource(source: File): AgentSkillImportStage {
        val workspaceRoot = hostWorkspaceRoot.canonicalFile
        val canonicalSource = source.canonicalFile
        require(canonicalSource.isDirectory && canonicalSource.toPath().startsWith(workspaceRoot.toPath())) {
            "Skill 文件夹不存在或已经越出工作区"
        }
        val inspected = inspect(canonicalSource)
        val importRoot = File(workspaceRoot, IMPORT_ROOT).canonicalFile
        require(importRoot.toPath().startsWith(workspaceRoot.toPath())) { "Skill 导入目录越界" }
        require(importRoot.mkdirs() || importRoot.isDirectory) { "无法准备 Skill 导入目录" }

        val referenceId = "import-${UUID.randomUUID()}"
        val temporary = File(importRoot, ".$referenceId-stage")
        val destination = File(importRoot, referenceId)
        require(!temporary.exists() && !destination.exists()) { "无法准备 Skill 导入事务" }
        try {
            copyTree(canonicalSource, temporary, inspected.paths)
            Files.move(
                temporary.toPath(),
                destination.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
            )
        } finally {
            if (temporary.exists()) temporary.deleteRecursively()
        }
        return AgentSkillImportStage(
            skillId = inspected.skillId,
            sourceReference = "kite-import:$referenceId",
            stagedDirectory = destination,
            importRoot = importRoot,
        )
    }

    private fun extractArchive(input: InputStream, destination: File) {
        var fileCount = 0
        var totalBytes = 0L
        ZipInputStream(input.buffered()).use { archive ->
            while (true) {
                val entry = archive.nextEntry ?: break
                val rawName = entry.name.replace('\\', '/')
                require(rawName.isNotBlank() && !rawName.startsWith('/') && '\u0000' !in rawName) {
                    "Skill ZIP 包含无效路径"
                }
                val relative = java.nio.file.Paths.get(rawName).normalize()
                require(!relative.isAbsolute && relative.none { it.toString() == ".." }) { "Skill ZIP 路径越界" }
                require(relative.nameCount <= MAX_DEPTH + 2) { "Skill ZIP 层级过深" }
                val target = destination.toPath().resolve(relative).normalize()
                require(target.startsWith(destination.toPath())) { "Skill ZIP 路径越界" }
                if (entry.isDirectory) {
                    Files.createDirectories(target)
                } else {
                    fileCount += 1
                    require(fileCount <= MAX_FILES) { "Skill ZIP 文件数量超过限制" }
                    require(!Files.exists(target)) { "Skill ZIP 包含重复路径" }
                    Files.createDirectories(target.parent)
                    Files.newOutputStream(target).use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            val count = archive.read(buffer)
                            if (count < 0) break
                            totalBytes += count
                            require(totalBytes <= MAX_TREE_BYTES) { "Skill ZIP 解压后超过大小限制" }
                            output.write(buffer, 0, count)
                        }
                    }
                }
                archive.closeEntry()
            }
        }
    }

    private fun inspect(source: File): InspectedSkill {
        val sourcePath = source.toPath()
        val paths = Files.walk(sourcePath).use { stream -> stream.iterator().asSequence().toList() }
        require(paths.none { Files.isSymbolicLink(it) }) { "Skill 文件夹不能包含符号链接" }
        require(paths.all { path -> path == sourcePath || path.startsWith(sourcePath) }) {
            "Skill 文件夹内容越界"
        }
        require(paths.maxOfOrNull { sourcePath.relativize(it).nameCount }?.let { it <= MAX_DEPTH } != false) {
            "Skill 文件夹层级过深"
        }
        val regularFiles = paths.filter { Files.isRegularFile(it, LinkOption.NOFOLLOW_LINKS) }
        require(regularFiles.size <= MAX_FILES) { "Skill 文件数量超过限制" }
        require(regularFiles.sumOf { Files.size(it) } <= MAX_TREE_BYTES) { "Skill 文件夹超过大小限制" }

        val skillFile = sourcePath.resolve(SKILL_FILE)
        require(Files.isRegularFile(skillFile, LinkOption.NOFOLLOW_LINKS)) { "所选文件夹缺少 SKILL.md" }
        require(Files.size(skillFile) <= MAX_SKILL_BYTES) { "SKILL.md 超过大小限制" }
        val skillId = parseSkillId(Files.readAllLines(skillFile, Charsets.UTF_8))
        return InspectedSkill(skillId, paths)
    }

    private fun parseSkillId(lines: List<String>): String {
        require(lines.firstOrNull()?.trim() == "---") { "SKILL.md 缺少 YAML 头部" }
        val closing = lines.take(MAX_HEADER_LINES).drop(1).indexOfFirst { it.trim() == "---" }
        require(closing >= 0) { "SKILL.md 的 YAML 头部未结束" }
        val value = lines.subList(1, closing + 1)
            .firstOrNull { line -> line.substringBefore(':').trim() == "name" }
            ?.substringAfter(':')
            ?.trim()
            ?.removeSurrounding("\"")
            ?.removeSurrounding("'")
            .orEmpty()
        require(SAFE_SKILL_ID.matches(value)) { "SKILL.md 的 name 格式无效" }
        return value
    }

    private fun copyTree(source: File, destination: File, paths: List<java.nio.file.Path>) {
        require(destination.mkdir()) { "无法创建 Skill 暂存目录" }
        val sourcePath = source.toPath()
        paths.filter { it != sourcePath }
            .sortedBy { sourcePath.relativize(it).nameCount }
            .forEach { path ->
                val relative = sourcePath.relativize(path)
                val target = destination.toPath().resolve(relative).normalize()
                require(target.startsWith(destination.toPath())) { "Skill 暂存路径越界" }
                when {
                    Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS) -> Files.createDirectory(target)
                    Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) -> Files.copy(
                        path,
                        target,
                        StandardCopyOption.COPY_ATTRIBUTES,
                    )
                    else -> throw IllegalArgumentException("Skill 包含不支持的文件类型")
                }
            }
    }

    private data class InspectedSkill(
        val skillId: String,
        val paths: List<java.nio.file.Path>,
    )

    private companion object {
        const val IMPORT_ROOT = ".kf/imports/skills"
        const val ARCHIVE_ROOT = ".kf/imports/skill-archives"
        const val SKILL_FILE = "SKILL.md"
        const val MAX_SKILL_BYTES = 1024L * 1024L
        const val MAX_TREE_BYTES = 8L * 1024L * 1024L
        const val MAX_FILES = 128
        const val MAX_DEPTH = 8
        const val MAX_HEADER_LINES = 80
        val SAFE_SKILL_ID = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,127}")
    }
}

internal class AgentSkillImportStage internal constructor(
    val skillId: String,
    val sourceReference: String,
    private val stagedDirectory: File,
    private val importRoot: File,
) {
    fun discard() {
        val root = importRoot.canonicalFile.toPath()
        val target = stagedDirectory.canonicalFile.toPath()
        if (target.startsWith(root) && target != root) stagedDirectory.deleteRecursively()
    }
}
