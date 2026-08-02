package com.kite.app.agent.config.native

import com.kite.app.agent.config.AgentConfigApplyResult
import com.kite.app.agent.config.AgentConfigScope
import com.kite.app.agent.config.AgentConfigValidationProblem
import com.kite.app.agent.config.AgentPersistentConfigChange
import com.kite.app.agent.config.AgentSkillActivation
import com.kite.app.agent.config.AgentSkillDocumentReadResult
import com.kite.app.agent.config.AgentSkillDocumentSnapshot
import com.kite.app.agent.config.AgentSkillDocumentWriteRequest
import com.kite.app.agent.config.AgentSkillDocumentWriteResult
import com.kite.app.agent.config.AgentSkillOperation
import com.kite.app.agent.config.AgentSkillSummary
import com.kite.app.agent.config.AtomicConfigFileStore
import com.kite.app.agent.config.AtomicConfigFileWriteResult
import com.kite.app.agent.config.ContainerAgentConfigProjection
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

/**
 * 文件夹型 Agent Skill 的受控读写工具。
 *
 * 它只接收容器绝对路径，并始终通过当前 PRoot View 的投影位置读写；不会把 Skill
 * 内容复制到 Kite 数据库，也不会跟随符号链接越出原生 Skill 根目录。
 */
internal class NativeAgentSkillDirectory(
    private val project: (String) -> ContainerAgentConfigProjection.FileProjection?,
    private val roots: List<String>,
    private val mutableRoots: Set<String> = setOf(roots.first()),
    private val fileStore: AtomicConfigFileStore = AtomicConfigFileStore(),
    private val configurationId: (File, String) -> String = { _, id -> id },
) {
    data class Entry(
        val id: String,
        val configurationId: String,
        val displayName: String,
        val containerLocation: String,
        val directory: File,
        val removable: Boolean,
    )

    fun discover(): List<Entry> {
        val seen = linkedMapOf<String, Entry>()
        roots.forEach { root ->
            visibleRoots(root).forEach { visibleRoot ->
                visibleRoot.walkTopDown()
                    .maxDepth(MAX_DISCOVERY_DEPTH)
                    .onEnter { directory ->
                        !Files.isSymbolicLink(directory.toPath()) &&
                            (directory == visibleRoot || !directory.name.startsWith('.'))
                    }
                    .filter { directory -> directory != visibleRoot && directory.isDirectory }
                    .sortedBy { directory -> directory.relativeTo(visibleRoot).invariantSeparatorsPath }
                    .forEach { directory ->
                        val skillFile = File(directory, SKILL_FILE)
                        if (!isSafeSkillFile(directory, skillFile)) return@forEach
                        val metadata = skillMetadata(skillFile) ?: return@forEach
                        val relative = directory.relativeTo(visibleRoot).invariantSeparatorsPath
                        val nativeId = configurationId(skillFile, metadata.first).takeIf(SAFE_ID::matches)
                            ?: metadata.first
                        seen.putIfAbsent(
                            metadata.first,
                            Entry(
                                id = metadata.first,
                                configurationId = nativeId,
                                displayName = metadata.second,
                                containerLocation = "$root/$relative",
                                directory = directory,
                                removable = canRemove(root, relative, directory),
                            ),
                        )
                    }
            }
        }
        return seen.values.sortedBy(Entry::id)
    }

    fun summaries(
        activation: (Entry) -> AgentSkillActivation,
        activationOperations: Set<AgentSkillOperation>,
    ): List<AgentSkillSummary> = discover().map { entry ->
        AgentSkillSummary(
            id = entry.id,
            displayName = entry.displayName,
            location = entry.containerLocation,
            scope = AgentConfigScope.User,
            activation = activation(entry),
            allowedOperations = buildSet {
                addAll(activationOperations)
                if (entry.removable) add(AgentSkillOperation.Remove)
            },
        )
    }

    fun revisionInputs(): List<Pair<String, String>> = discover().map { entry ->
        "skill:${entry.containerLocation}" to treeRevision(entry.directory)
    }

    fun readDocument(skillId: String): AgentSkillDocumentReadResult {
        if (!SAFE_ID.matches(skillId)) return AgentSkillDocumentReadResult.Missing()
        val entry = discover().firstOrNull { it.id == skillId }
            ?: return AgentSkillDocumentReadResult.Missing()
        return runCatching {
            AgentSkillDocumentReadResult.Ready(snapshot(entry))
        }.getOrElse {
            AgentSkillDocumentReadResult.Failed("无法读取 Skill 主文件")
        }
    }

    fun writeDocument(request: AgentSkillDocumentWriteRequest): AgentSkillDocumentWriteResult {
        if (!SAFE_ID.matches(request.skillId)) return documentRejected("skillId", "Skill ID 格式无效")
        val nextBytes = request.content.toByteArray(Charsets.UTF_8)
        validateDocumentBytes(nextBytes)?.let { return documentRejected("content", it) }
        val entry = discover().firstOrNull { it.id == request.skillId }
            ?: return AgentSkillDocumentWriteResult.Conflict("skill:missing", "Skill 已不存在，请重新读取")
        if (!entry.removable) return documentRejected("skillId", "当前 Skill 来自共享或只读层，不能编辑")
        val target = File(entry.directory, SKILL_FILE)
        val before = runCatching { fileStore.read(target) }.getOrElse {
            return AgentSkillDocumentWriteResult.Failed("无法读取当前 Skill", restored = true)
        }
        if (before.revision.value != request.expectedRevision) {
            return AgentSkillDocumentWriteResult.Conflict(before.revision.value)
        }
        return when (val result = fileStore.replace(
            target = target,
            expectedRevision = before.revision,
            nextBytes = nextBytes,
            validate = ::validateDocumentBytes,
        )) {
            is AtomicConfigFileWriteResult.Applied -> when (val reread = readDocument(request.skillId)) {
                is AgentSkillDocumentReadResult.Ready ->
                    AgentSkillDocumentWriteResult.Applied(reread.snapshot, result.backupReference)
                else -> AgentSkillDocumentWriteResult.Failed("Skill 已写入，但无法重新读取", restored = false)
            }
            is AtomicConfigFileWriteResult.Conflict ->
                AgentSkillDocumentWriteResult.Conflict(result.actualRevision.value)
            is AtomicConfigFileWriteResult.Rejected -> documentRejected("content", result.message)
            is AtomicConfigFileWriteResult.Failed ->
                AgentSkillDocumentWriteResult.Failed(result.message, result.restored)
        }
    }

    fun applyFileChange(change: AgentPersistentConfigChange): AgentConfigApplyResult? = when (change) {
        is AgentPersistentConfigChange.InstallSkill -> install(change)
        is AgentPersistentConfigChange.RemoveSkill -> remove(change)
        else -> null
    }

    /**
     * 定点改写当前可写 Skill 的主文件，供原生把启用语义保存在 frontmatter 的 Agent 使用。
     * 只允许修改当前运行视图真正拥有的目录；共享或只读层只展示，不会被间接覆盖。
     */
    fun applyTextChange(
        skillId: String,
        transform: (String) -> String,
        validate: (String) -> String? = { null },
    ): AgentConfigApplyResult? {
        val entry = discover().firstOrNull { it.id == skillId }
            ?: return AgentConfigApplyResult.Conflict("skill:missing", "Skill 已不存在，请重新读取")
        if (!entry.removable) return rejected("skillId", "当前 Skill 来自共享或只读层，不能在这里修改")
        val target = File(entry.directory, SKILL_FILE)
        val before = runCatching { fileStore.read(target) }.getOrElse {
            return AgentConfigApplyResult.Failed("无法读取 Skill", restored = true)
        }
        val nextText = runCatching { transform(before.bytes.toString(Charsets.UTF_8)) }.getOrElse {
            return rejected("skillId", it.message ?: "Skill 内容无法修改")
        }
        validate(nextText)?.let { return rejected("skillId", it) }
        return when (val result = fileStore.replace(
            target = target,
            expectedRevision = before.revision,
            nextBytes = nextText.toByteArray(Charsets.UTF_8),
            validate = { bytes -> validate(bytes.toString(Charsets.UTF_8)) },
        )) {
            is AtomicConfigFileWriteResult.Applied -> null
            is AtomicConfigFileWriteResult.Conflict ->
                AgentConfigApplyResult.Conflict(result.actualRevision.value, "Skill 已被外部修改，请重新读取")
            is AtomicConfigFileWriteResult.Rejected -> rejected("skillId", result.message)
            is AtomicConfigFileWriteResult.Failed -> AgentConfigApplyResult.Failed(result.message, result.restored)
        }
    }

    private fun install(change: AgentPersistentConfigChange.InstallSkill): AgentConfigApplyResult? {
        val relative = change.sourceReference.removePrefix(IMPORT_PREFIX)
        if (!SAFE_REFERENCE.matches(relative) || change.sourceReference == relative) {
            return rejected("sourceReference", "Skill 来源引用格式无效")
        }
        if (!SAFE_ID.matches(change.skillId)) return rejected("skillId", "Skill ID 格式无效")
        if (discover().any { it.id == change.skillId }) {
            return AgentConfigApplyResult.Conflict("skill:exists", "Skill 已存在，请先重新读取")
        }
        val sourceProjection = project("$IMPORT_ROOT/$relative/$SKILL_FILE")
            ?: return rejected("sourceReference", "Kite 运行容器尚未创建")
        val sourceFile = sourceProjection.readFile
        val sourceDirectory = requireNotNull(sourceFile.parentFile)
        if (!isSafeSkillFile(sourceDirectory, sourceFile)) {
            return rejected("sourceReference", "Skill 来源不存在或内容不安全")
        }
        val metadata = skillMetadata(sourceFile)
        if (metadata?.first != change.skillId) {
            return rejected("skillId", "Skill ID 与 SKILL.md 的 name 不一致")
        }
        val targetProjection = project("${roots.first()}/${change.skillId}/$SKILL_FILE")
            ?: return rejected("skillId", "Kite 运行容器尚未创建")
        val target = requireNotNull(targetProjection.writeFile.parentFile)
        if (target.exists()) return AgentConfigApplyResult.Conflict("skill:exists", "Skill 已存在，请先重新读取")
        return runCatching {
            val parent = requireNotNull(target.parentFile)
            require(parent.mkdirs() || parent.isDirectory)
            val stage = File(parent, ".${change.skillId}.kite-stage-${System.nanoTime()}")
            try {
                copyTree(sourceDirectory, stage)
                Files.move(stage.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
            } finally {
                if (stage.exists()) stage.deleteRecursively()
            }
            null
        }.getOrElse {
            AgentConfigApplyResult.Failed("Skill 导入失败", restored = !target.exists())
        }
    }

    private fun remove(change: AgentPersistentConfigChange.RemoveSkill): AgentConfigApplyResult? {
        val entry = discover().firstOrNull { it.id == change.skillId }
            ?: return AgentConfigApplyResult.Conflict("skill:missing", "Skill 已不存在，请重新读取")
        if (!entry.removable) {
            return rejected("skillId", "当前 Skill 来自只读层，不能在这个运行视图中移除")
        }
        return runCatching {
            val backupRoot = File(requireNotNull(entry.directory.parentFile), BACKUP_DIRECTORY)
            require(backupRoot.mkdirs() || backupRoot.isDirectory)
            val backup = File(backupRoot, "${entry.id}-${System.currentTimeMillis()}")
            Files.move(entry.directory.toPath(), backup.toPath(), StandardCopyOption.ATOMIC_MOVE)
            backupRoot.listFiles()
                .orEmpty()
                .filter { it.name.startsWith("${entry.id}-") }
                .sortedByDescending(File::lastModified)
                .drop(MAX_BACKUPS)
                .forEach(File::deleteRecursively)
            null
        }.getOrElse {
            AgentConfigApplyResult.Failed("Skill 移除失败", restored = entry.directory.exists())
        }
    }

    private fun visibleRoots(root: String): List<File> {
        val probe = project("$root/$PROBE_FILE") ?: return emptyList()
        return listOfNotNull(probe.writeFile.parentFile, probe.readFile.parentFile, probe.baseFile.parentFile)
            .distinctBy { runCatching { it.canonicalPath }.getOrDefault(it.absolutePath) }
            .filter(File::isDirectory)
    }

    private fun canRemove(root: String, relativePath: String, directory: File): Boolean {
        if (root !in mutableRoots) return false
        val projection = project("$root/$relativePath/$SKILL_FILE") ?: return false
        return runCatching { requireNotNull(projection.writeFile.parentFile).canonicalFile == directory.canonicalFile }
            .getOrDefault(false)
    }

    private fun isSafeSkillFile(directory: File, skillFile: File): Boolean = runCatching {
        directory.isDirectory &&
            skillFile.isFile &&
            skillFile.length() <= MAX_SKILL_BYTES &&
            !Files.isSymbolicLink(directory.toPath()) &&
            !Files.isSymbolicLink(skillFile.toPath()) &&
            skillFile.canonicalFile.toPath().startsWith(directory.canonicalFile.toPath())
    }.getOrDefault(false)

    private fun skillMetadata(file: File): Pair<String, String>? = runCatching {
        val header = file.useLines { it.take(MAX_HEADER_LINES).toList() }
        val name = header.firstNotNullOfOrNull { line ->
            FRONTMATTER_NAME.matchEntire(line.trim())?.groupValues?.get(1)
        }?.trim('"', '\'', ' ')?.takeIf(SAFE_ID::matches)
            ?: file.parentFile?.name?.takeIf(SAFE_ID::matches)
        val title = header.firstNotNullOfOrNull { line ->
            FRONTMATTER_TITLE.matchEntire(line.trim())?.groupValues?.get(1)
        }?.trim('"', '\'', ' ')?.takeIf(String::isNotBlank) ?: name
        requireNotNull(name) to requireNotNull(title)
    }.getOrNull()

    private fun snapshot(entry: Entry): AgentSkillDocumentSnapshot {
        val file = File(entry.directory, SKILL_FILE)
        require(isSafeSkillFile(entry.directory, file))
        val stored = fileStore.read(file)
        return AgentSkillDocumentSnapshot(
            skillId = entry.id,
            displayName = entry.displayName,
            location = "${entry.containerLocation}/$SKILL_FILE",
            revision = stored.revision.value,
            content = stored.bytes.toString(Charsets.UTF_8),
            writable = entry.removable,
        )
    }

    private fun validateDocumentBytes(bytes: ByteArray): String? = when {
        bytes.size > MAX_SKILL_BYTES -> "SKILL.md 超过大小限制"
        bytes.any { it == 0.toByte() } -> "SKILL.md 不能包含空字节"
        else -> null
    }

    private fun copyTree(source: File, destination: File) {
        val files = source.walkTopDown().toList()
        require(files.none { Files.isSymbolicLink(it.toPath()) })
        require(files.count(File::isFile) <= MAX_FILES)
        require(files.filter(File::isFile).sumOf(File::length) <= MAX_TREE_BYTES)
        require(files.maxOfOrNull { it.relativeTo(source).toPath().nameCount }?.let { it <= MAX_DEPTH } != false)
        require(destination.mkdir())
        files.filter { it != source }.forEach { child ->
            val relative = child.relativeTo(source)
            val target = File(destination, relative.path)
            require(target.canonicalFile.toPath().startsWith(destination.canonicalFile.toPath()))
            if (child.isDirectory) require(target.mkdir()) else child.copyTo(target, overwrite = false)
        }
        require(isSafeSkillFile(destination, File(destination, SKILL_FILE)))
    }

    private fun treeRevision(directory: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        directory.walkTopDown().filter(File::isFile).sortedBy(File::getAbsolutePath).forEach { file ->
            digest.update(file.relativeTo(directory).invariantSeparatorsPath.toByteArray())
            digest.update(file.readBytes())
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun rejected(field: String, message: String) = AgentConfigApplyResult.Rejected(
        listOf(AgentConfigValidationProblem(field, message)),
    )

    private fun documentRejected(field: String, message: String) = AgentSkillDocumentWriteResult.Rejected(
        listOf(AgentConfigValidationProblem(field, message)),
    )

    private companion object {
        const val SKILL_FILE = "SKILL.md"
        const val IMPORT_PREFIX = "kite-import:"
        const val IMPORT_ROOT = "/workspace/.kf/imports/skills"
        const val PROBE_FILE = ".kite-directory-probe"
        const val BACKUP_DIRECTORY = ".kite-skill-backups"
        const val MAX_BACKUPS = 5
        const val MAX_HEADER_LINES = 80
        const val MAX_DISCOVERY_DEPTH = 7
        const val MAX_FILES = 128
        const val MAX_DEPTH = 8
        const val MAX_SKILL_BYTES = 1024L * 1024L
        const val MAX_TREE_BYTES = 8L * 1024L * 1024L
        val SAFE_ID = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,127}")
        val SAFE_REFERENCE = Regex("import-[A-Za-z0-9-]{8,80}")
        val FRONTMATTER_NAME = Regex("name\\s*:\\s*(.+)")
        val FRONTMATTER_TITLE = Regex("(?:title|display_name)\\s*:\\s*(.+)")
    }
}
