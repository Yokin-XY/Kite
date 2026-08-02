package com.kite.app.agent.config

import java.nio.charset.CodingErrorAction
import java.security.MessageDigest

internal data class NativeAgentCoreDocumentSpec(
    val id: String,
    val displayName: String,
    val fileName: String,
    val containerPath: String,
    val scope: AgentConfigScope,
    val semantics: AgentCoreDocumentSemantics,
    val priorityDescription: String,
    val warning: String? = null,
    val managedOutputFormat: NativeAgentManagedOutputFormat = NativeAgentManagedOutputFormat.Disabled,
)

/**
 * 核心设定文档的受控读写器。
 *
 * 文档正文只在显式读取时出现；PRoot 活跃 View 继续通过 Upper 写入，避免改到不可见的 Base rootfs。
 */
internal class NativeAgentCoreDocumentStore(
    private val resolve: (String) -> ContainerAgentConfigProjection.FileProjection?,
    private val fileStore: AtomicConfigFileStore
) {
    fun descriptors(specs: List<NativeAgentCoreDocumentSpec>): List<AgentCoreDocumentDescriptor> =
        specs.map { spec -> state(spec).descriptor }

    fun read(
        specs: List<NativeAgentCoreDocumentSpec>,
        documentId: String
    ): AgentCoreDocumentSnapshot? = specs.firstOrNull { it.id == documentId }?.let(::state)?.snapshot

    fun write(
        specs: List<NativeAgentCoreDocumentSpec>,
        request: AgentCoreDocumentWriteRequest
    ): AgentCoreDocumentWriteResult {
        val spec = specs.firstOrNull { it.id == request.documentId }
            ?: return AgentCoreDocumentWriteResult.Rejected(
                listOf(AgentConfigValidationProblem("documentId", "核心设定文档不存在"))
            )
        val problems = validate(request)
        if (problems.isNotEmpty()) return AgentCoreDocumentWriteResult.Rejected(problems)
        val before = runCatching { state(spec) }.getOrElse {
            return AgentCoreDocumentWriteResult.Failed("无法读取当前核心设定", restored = true)
        }
        if (!before.descriptor.writable) {
            return AgentCoreDocumentWriteResult.Rejected(
                listOf(AgentConfigValidationProblem("documentId", "当前核心设定文件不可写"))
            )
        }
        if (before.revision != request.expectedRevision) {
            return AgentCoreDocumentWriteResult.Conflict(before.revision)
        }
        val nextContent = spec.projectForAgent(request.content)
        val nextBytes = nextContent.toByteArray(Charsets.UTF_8)
        return when (val result = fileStore.replace(
            target = before.projection.writeFile,
            expectedRevision = before.writeRevision,
            nextBytes = nextBytes,
            validate = ::validateBytes
        )) {
            is AtomicConfigFileWriteResult.Applied -> {
                val after = runCatching { state(spec) }.getOrElse {
                    return AgentCoreDocumentWriteResult.Failed("核心设定已写入，但无法重新读取", restored = false)
                }
                AgentCoreDocumentWriteResult.Applied(after.snapshot, result.backupReference)
            }
            is AtomicConfigFileWriteResult.Conflict -> {
                val current = runCatching { state(spec).revision }.getOrDefault(result.actualRevision.value)
                AgentCoreDocumentWriteResult.Conflict(current)
            }
            is AtomicConfigFileWriteResult.Rejected -> AgentCoreDocumentWriteResult.Rejected(
                listOf(AgentConfigValidationProblem("content", result.message))
            )
            is AtomicConfigFileWriteResult.Failed ->
                AgentCoreDocumentWriteResult.Failed(result.message, result.restored)
        }
    }

    fun ensureManagedOutputFormat(
        specs: List<NativeAgentCoreDocumentSpec>,
    ): NativeAgentManagedOutputSyncResult {
        specs.filter { it.managedOutputFormat != NativeAgentManagedOutputFormat.Disabled }
            .forEach { spec ->
                val before = runCatching { state(spec) }.getOrElse {
                    return NativeAgentManagedOutputSyncResult.Failed("无法读取 ${spec.displayName}")
                }
                val userContent = spec.projectForEditor(before.rawContent)
                val shouldCreate = when (spec.managedOutputFormat) {
                    NativeAgentManagedOutputFormat.Disabled -> false
                    NativeAgentManagedOutputFormat.CreateOrUpdate -> true
                    NativeAgentManagedOutputFormat.ExistingNonBlankOnly ->
                        before.descriptor.exists && userContent.isNotBlank()
                }
                if (!shouldCreate || KiteAgentOutputFormatPolicy.isCurrent(before.rawContent)) {
                    return@forEach
                }
                if (!before.descriptor.writable) {
                    return NativeAgentManagedOutputSyncResult.Failed("${spec.displayName} 当前不可写")
                }
                val nextBytes = spec.projectForAgent(userContent).toByteArray(Charsets.UTF_8)
                when (val result = fileStore.replace(
                    target = before.projection.writeFile,
                    expectedRevision = before.writeRevision,
                    nextBytes = nextBytes,
                    validate = ::validateBytes,
                )) {
                    is AtomicConfigFileWriteResult.Applied -> Unit
                    is AtomicConfigFileWriteResult.Conflict ->
                        return NativeAgentManagedOutputSyncResult.Failed("${spec.displayName} 已被其他进程修改")
                    is AtomicConfigFileWriteResult.Rejected ->
                        return NativeAgentManagedOutputSyncResult.Failed(result.message)
                    is AtomicConfigFileWriteResult.Failed ->
                        return NativeAgentManagedOutputSyncResult.Failed(result.message)
                }
            }
        return NativeAgentManagedOutputSyncResult.Ready
    }

    private fun state(spec: NativeAgentCoreDocumentSpec): CoreDocumentState {
        val projection = requireNotNull(resolve(spec.containerPath)) { "Kite 运行容器尚未创建" }
        val visible = fileStore.read(projection.readFile)
        val writable = fileStore.read(projection.writeFile)
        val revision = revision(
            spec.containerPath,
            projection.viewId,
            visible.revision,
            writable.revision
        )
        val rawContent = decode(visible.bytes)
        val content = spec.projectForEditor(rawContent)
        val descriptor = AgentCoreDocumentDescriptor(
            id = spec.id,
            displayName = spec.displayName,
            fileName = spec.fileName,
            displayLocation = spec.containerPath,
            scope = spec.scope,
            semantics = spec.semantics,
            exists = visible.revision.exists,
            writable = canWrite(projection),
            priorityDescription = spec.priorityDescription,
            warning = spec.warning
        )
        return CoreDocumentState(
            projection = projection,
            writeRevision = writable.revision,
            descriptor = descriptor,
            revision = revision,
            snapshot = AgentCoreDocumentSnapshot(descriptor, revision, content),
            rawContent = rawContent,
        )
    }

    private fun NativeAgentCoreDocumentSpec.projectForEditor(rawContent: String): String =
        if (managedOutputFormat == NativeAgentManagedOutputFormat.Disabled) rawContent
        else KiteAgentOutputFormatPolicy.userContent(rawContent)

    private fun NativeAgentCoreDocumentSpec.projectForAgent(userContent: String): String =
        if (managedOutputFormat == NativeAgentManagedOutputFormat.Disabled) userContent
        else KiteAgentOutputFormatPolicy.merge(userContent)

    private fun canWrite(projection: ContainerAgentConfigProjection.FileProjection): Boolean {
        val target = projection.writeFile
        if (target.exists()) return target.isFile && target.canWrite()
        val parent = target.parentFile ?: return false
        var candidate = parent
        while (!candidate.exists()) candidate = candidate.parentFile ?: return false
        return candidate.isDirectory && candidate.canWrite()
    }

    private fun validate(request: AgentCoreDocumentWriteRequest): List<AgentConfigValidationProblem> = buildList {
        if (!SAFE_AGENT_ID.matches(request.agentId)) add(AgentConfigValidationProblem("agentId", "Agent ID 格式无效"))
        if (!SAFE_DOCUMENT_ID.matches(request.documentId)) {
            add(AgentConfigValidationProblem("documentId", "核心设定文档 ID 格式无效"))
        }
        if (request.expectedRevision.isBlank()) {
            add(AgentConfigValidationProblem("expectedRevision", "缺少核心设定 revision"))
        }
        if (request.content.toByteArray(Charsets.UTF_8).size > MAX_DOCUMENT_BYTES) {
            add(AgentConfigValidationProblem("content", "核心设定内容超过安全写入上限"))
        }
        if ('\u0000' in request.content) add(AgentConfigValidationProblem("content", "核心设定不能包含 NUL 字符"))
    }

    private fun validateBytes(bytes: ByteArray): String? = when {
        bytes.size > MAX_DOCUMENT_BYTES -> "核心设定内容超过安全写入上限"
        bytes.any { it == 0.toByte() } -> "核心设定不能包含 NUL 字符"
        runCatching { decode(bytes) }.isFailure -> "核心设定必须是有效 UTF-8 文本"
        else -> null
    }

    private fun decode(bytes: ByteArray): String = Charsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
        .decode(java.nio.ByteBuffer.wrap(bytes))
        .toString()

    private fun revision(
        containerPath: String,
        viewId: String?,
        visible: ConfigFileRevision,
        writable: ConfigFileRevision
    ): String {
        val digest = MessageDigest.getInstance("SHA-256")
        listOf(containerPath, viewId ?: "base", visible.value, writable.value).forEach {
            digest.update(it.toByteArray(Charsets.UTF_8))
            digest.update(0)
        }
        return "sha256:" + digest.digest().joinToString("") { "%02x".format(it) }
    }

    private data class CoreDocumentState(
        val projection: ContainerAgentConfigProjection.FileProjection,
        val writeRevision: ConfigFileRevision,
        val descriptor: AgentCoreDocumentDescriptor,
        val revision: String,
        val snapshot: AgentCoreDocumentSnapshot,
        val rawContent: String,
    )

    companion object {
        private const val MAX_DOCUMENT_BYTES = 512 * 1024
        private val SAFE_AGENT_ID = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,127}")
        private val SAFE_DOCUMENT_ID = Regex("[a-z0-9][a-z0-9._-]{0,127}")

        fun projectPath(workspacePath: String?, fileName: String): String? {
            if (workspacePath.isNullOrBlank() || !SAFE_FILE_NAME.matches(fileName)) return null
            val root = normalizeContainerPath(workspacePath) ?: return null
            return if (root == "/") "/$fileName" else "$root/$fileName"
        }

        fun normalizeContainerPath(value: String): String? {
            if (!value.startsWith('/')) return null
            val segments = value.split('/').filter(String::isNotEmpty)
            if (segments.any { it == "." || it == ".." || it.any(Char::isISOControl) }) return null
            return "/" + segments.joinToString("/")
        }

        private val SAFE_FILE_NAME = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,127}")
    }
}
