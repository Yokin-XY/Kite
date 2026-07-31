package com.kite.app.foundation.runtime

import java.io.File
import java.nio.file.Files

internal data class HostNodeRuntimeAssets(
    val launcher: File,
    val preloadScript: File,
    val patchedLoader: File,
    val patchedLibc: File,
    val compatLibrary: File,
    val resolvConf: File,
)

internal data class HostNodeRuntimeLayout(
    val rootfsDirectory: File,
    val workspaceDirectory: File,
    val workspaceControlDirectory: File,
    val loader: File,
    val nodeBinary: File,
    val nodeLibraryDirectory: File,
    val glibcLibraryDirectories: List<File>,
    val assets: HostNodeRuntimeAssets,
) {
    val libraryPath: String
        get() = buildList {
            add(assets.patchedLibc.parentFile)
            add(nodeLibraryDirectory)
            addAll(glibcLibraryDirectories)
        }.distinctBy(File::getAbsolutePath).joinToString(":") { it.absolutePath }

    fun mapContainerPath(path: String?): File? {
        val normalized = path?.trim().orEmpty().ifBlank { RuntimeBoundary.CONTAINER_WORKSPACE_PATH }
        val (root, candidate) = when {
            normalized == "${RuntimeBoundary.CONTAINER_WORKSPACE_PATH}/.kf" ->
                workspaceControlDirectory to workspaceControlDirectory
            normalized.startsWith("${RuntimeBoundary.CONTAINER_WORKSPACE_PATH}/.kf/") ->
                workspaceControlDirectory to File(
                    workspaceControlDirectory,
                    normalized.removePrefix("${RuntimeBoundary.CONTAINER_WORKSPACE_PATH}/.kf/"),
                )
            normalized == RuntimeBoundary.CONTAINER_WORKSPACE_PATH -> workspaceDirectory to workspaceDirectory
            normalized.startsWith("${RuntimeBoundary.CONTAINER_WORKSPACE_PATH}/") ->
                workspaceDirectory to File(
                    workspaceDirectory,
                    normalized.removePrefix("${RuntimeBoundary.CONTAINER_WORKSPACE_PATH}/"),
                )
            normalized == "/" -> rootfsDirectory to rootfsDirectory
            normalized.startsWith("/") -> rootfsDirectory to File(rootfsDirectory, normalized.removePrefix("/"))
            else -> null
        } ?: return null
        val normalizedRoot = root.absoluteFile.normalize().toPath()
        val normalizedCandidate = candidate.absoluteFile.normalize()
        return normalizedCandidate.takeIf { it.toPath().startsWith(normalizedRoot) }
    }
}

internal sealed interface HostNodeRuntimeResolution {
    data class Ready(val layout: HostNodeRuntimeLayout) : HostNodeRuntimeResolution
    data class Fallback(val reason: String) : HostNodeRuntimeResolution
}

/** 只读取已经准备好的正式 Ubuntu/Node 资产，不创建进程，也不修改资源状态。 */
internal object HostNodeRuntimeResolver {
    private val glibcLibraryRelativeCandidates = listOf(
        "usr/lib/aarch64-linux-gnu",
        "lib/aarch64-linux-gnu",
    )
    private val nodeParentRelativeCandidates = listOf(
        "software/kite.nodejs",
        "components/kite.nodejs",
        "toolchains",
    )

    fun resolve(
        rootfsDirectory: File,
        workspaceDirectory: File,
        assets: HostNodeRuntimeAssets,
        workspaceControlDirectory: File = File(workspaceDirectory, ".kf"),
    ): HostNodeRuntimeResolution {
        if (!rootfsDirectory.isDirectory) return HostNodeRuntimeResolution.Fallback("rootfs_missing")
        if (!workspaceDirectory.isDirectory) return HostNodeRuntimeResolution.Fallback("workspace_missing")
        if (!workspaceControlDirectory.isDirectory) {
            return HostNodeRuntimeResolution.Fallback("workspace_control_missing")
        }

        val glibcLibraries = glibcLibraryRelativeCandidates
            .map { File(rootfsDirectory, it) }
            .filter(File::isDirectory)
            .distinctBy(File::getAbsolutePath)
        if (glibcLibraries.isEmpty()) return HostNodeRuntimeResolution.Fallback("glibc_libraries_missing")

        val nodeBinary = findNodeBinary(workspaceControlDirectory)
            ?: return HostNodeRuntimeResolution.Fallback("node_binary_missing")
        if (!isArm64Elf(nodeBinary)) return HostNodeRuntimeResolution.Fallback("node_binary_abi_mismatch")
        val nodeLibraryDirectory = nodeBinary.parentFile?.parentFile?.resolve("lib")
            ?.takeIf(File::isDirectory)
            ?: return HostNodeRuntimeResolution.Fallback("node_libraries_missing")
        if (!File(nodeLibraryDirectory, "libatomic.so.1").isFile) {
            return HostNodeRuntimeResolution.Fallback("node_libatomic_missing")
        }

        if (!usableExecutable(assets.launcher)) return HostNodeRuntimeResolution.Fallback("host_launcher_missing")
        if (!isArm64Elf(assets.launcher)) return HostNodeRuntimeResolution.Fallback("host_launcher_abi_mismatch")
        if (!assets.preloadScript.isFile) return HostNodeRuntimeResolution.Fallback("host_preload_missing")
        if (!usableExecutable(assets.patchedLoader)) {
            return HostNodeRuntimeResolution.Fallback("patched_loader_missing")
        }
        if (!isArm64Elf(assets.patchedLoader)) {
            return HostNodeRuntimeResolution.Fallback("patched_loader_abi_mismatch")
        }
        if (!assets.patchedLibc.isFile) return HostNodeRuntimeResolution.Fallback("patched_libc_missing")
        if (!isArm64Elf(assets.patchedLibc)) return HostNodeRuntimeResolution.Fallback("patched_libc_abi_mismatch")
        if (!assets.compatLibrary.isFile) return HostNodeRuntimeResolution.Fallback("compat_library_missing")
        if (!isArm64Elf(assets.compatLibrary)) {
            return HostNodeRuntimeResolution.Fallback("compat_library_abi_mismatch")
        }
        if (!assets.resolvConf.isFile || assets.resolvConf.length() <= 0L) {
            return HostNodeRuntimeResolution.Fallback("resolver_config_missing")
        }

        return HostNodeRuntimeResolution.Ready(
            HostNodeRuntimeLayout(
                rootfsDirectory = rootfsDirectory.absoluteFile.normalize(),
                workspaceDirectory = workspaceDirectory.absoluteFile.normalize(),
                workspaceControlDirectory = workspaceControlDirectory.absoluteFile.normalize(),
                loader = assets.patchedLoader.absoluteFile.normalize(),
                nodeBinary = nodeBinary.absoluteFile.normalize(),
                nodeLibraryDirectory = nodeLibraryDirectory.absoluteFile.normalize(),
                glibcLibraryDirectories = glibcLibraries.map { it.absoluteFile.normalize() },
                assets = assets,
            )
        )
    }

    private fun findNodeBinary(workspaceControlDirectory: File): File? = nodeParentRelativeCandidates
        .asSequence()
        .map { File(workspaceControlDirectory, it) }
        .filter(File::isDirectory)
        .flatMap { parent ->
            parent.listFiles().orEmpty()
                .asSequence()
                .filter { it.isDirectory && it.name.startsWith("node-v") }
                .sortedByDescending(File::getName)
        }
        .map { File(it, "bin/node") }
        .firstOrNull(::usableExecutable)

    private fun usableExecutable(file: File): Boolean = file.isFile && file.canExecute()

    private fun isArm64Elf(file: File): Boolean = runCatching {
        val header = file.inputStream().use { input -> ByteArray(20).also { bytes ->
            if (input.read(bytes) != bytes.size) return@runCatching false
        } }
        header[0] == 0x7f.toByte() && header[1] == 'E'.code.toByte() &&
            header[2] == 'L'.code.toByte() && header[3] == 'F'.code.toByte() &&
            header[4] == 2.toByte() && header[5] == 1.toByte() &&
            header[18] == 0xb7.toByte() && header[19] == 0.toByte()
    }.getOrDefault(false)
}

internal data class HostNodeInvocation(
    val entryFile: File?,
    val arguments: List<String>,
) {
    fun nodeArguments(): List<String> = buildList {
        entryFile?.let { add(it.absolutePath) }
        addAll(arguments)
    }
}

/** 普通入口使用结构化 argv；只有兼容旧卡片时才允许受限的单命令文本。 */
internal sealed interface HostNodeExecutionRequest {
    data class CommandLine(val command: String) : HostNodeExecutionRequest
    data class Argv(
        val executable: String,
        val arguments: List<String> = emptyList(),
    ) : HostNodeExecutionRequest
}

internal sealed interface HostNodeCommandResolution {
    data class Ready(val invocation: HostNodeInvocation) : HostNodeCommandResolution
    data class Fallback(val reason: String) : HostNodeCommandResolution
}

/**
 * 只接受单一、无需 shell 展开的 Node 命令。管道、重定向、变量展开和复合脚本明确留在 PRoot。
 */
internal object HostNodeCommandResolver {
    private val safeCommandName = Regex("[A-Za-z0-9._+-]+")
    private val nodeShebangs = listOf(
        Regex("^#!\\s*/usr/bin/env\\s+(?:-S\\s+)?node(?:\\s|$)"),
        Regex("^#!\\s*/usr/bin/node(?:\\s|$)"),
        Regex("^#!\\s*/bin/node(?:\\s|$)"),
    )

    fun resolve(
        command: String,
        layout: HostNodeRuntimeLayout,
        linkTargetReader: (File) -> String? = ::readLinkTarget,
    ): HostNodeCommandResolution {
        val tokens = StrictCommandLine.parse(command)
            ?: return HostNodeCommandResolution.Fallback("shell_syntax_required")
        if (tokens.isEmpty()) return HostNodeCommandResolution.Fallback("command_missing")
        return resolve(tokens.first(), tokens.drop(1), layout, linkTargetReader)
    }

    fun resolve(
        executable: String,
        arguments: List<String>,
        layout: HostNodeRuntimeLayout,
        linkTargetReader: (File) -> String? = ::readLinkTarget,
    ): HostNodeCommandResolution {
        val normalizedExecutable = executable.trim()
        if (normalizedExecutable.isBlank()) return HostNodeCommandResolution.Fallback("command_missing")
        if (normalizedExecutable.contains('=')) {
            return HostNodeCommandResolution.Fallback("environment_assignment")
        }
        if (normalizedExecutable == "node") {
            return HostNodeCommandResolution.Ready(
                HostNodeInvocation(entryFile = null, arguments = arguments)
            )
        }

        val executableFile = resolveCommandFile(normalizedExecutable, layout)
            ?: return HostNodeCommandResolution.Fallback("managed_command_missing")
        val entryFile = followLinks(executableFile, layout, linkTargetReader)
            ?: return HostNodeCommandResolution.Fallback("managed_command_link_invalid")
        if (!hasNodeShebang(entryFile)) return HostNodeCommandResolution.Fallback("managed_command_not_node")
        return HostNodeCommandResolution.Ready(
            HostNodeInvocation(entryFile = entryFile, arguments = arguments)
        )
    }

    private fun resolveCommandFile(token: String, layout: HostNodeRuntimeLayout): File? = when {
        safeCommandName.matches(token) -> File(layout.workspaceControlDirectory, "bin/$token")
        token.startsWith("/") -> layout.mapContainerPath(token)
        else -> null
    }?.takeIf { Files.isRegularFile(it.toPath()) || Files.isSymbolicLink(it.toPath()) }

    private fun followLinks(
        initial: File,
        layout: HostNodeRuntimeLayout,
        linkTargetReader: (File) -> String?,
    ): File? {
        var current = initial.absoluteFile.normalize()
        repeat(12) {
            if (!withinRuntimeRoots(current, layout)) return null
            val target = linkTargetReader(current)
            if (target == null) {
                return current.takeIf(File::isFile)
            }
            current = if (target.startsWith('/')) {
                layout.mapContainerPath(target) ?: return null
            } else {
                File(current.parentFile, target).absoluteFile.normalize()
            }
        }
        return null
    }

    private fun readLinkTarget(file: File): String? = runCatching {
        file.toPath().takeIf(Files::isSymbolicLink)?.let(Files::readSymbolicLink)?.toString()
    }.getOrNull()

    private fun withinRuntimeRoots(file: File, layout: HostNodeRuntimeLayout): Boolean {
        val path = file.toPath().normalize()
        return path.startsWith(layout.workspaceDirectory.toPath().normalize()) ||
            path.startsWith(layout.workspaceControlDirectory.toPath().normalize()) ||
            path.startsWith(layout.rootfsDirectory.toPath().normalize())
    }

    private fun hasNodeShebang(file: File): Boolean {
        val firstLine = runCatching { file.bufferedReader().use { it.readLine().orEmpty() } }.getOrDefault("")
        return nodeShebangs.any { it.containsMatchIn(firstLine) }
    }
}

private object StrictCommandLine {
    private val shellOperators = setOf(';', '|', '&', '<', '>', '$', '`', '\n', '\r', '\u0000')

    fun parse(raw: String): List<String>? {
        if (raw.isBlank()) return emptyList()
        val tokens = mutableListOf<String>()
        val current = StringBuilder()
        var quote: Char? = null
        var escaping = false
        var tokenStarted = false

        raw.forEach { char ->
            if (escaping) {
                current.append(char)
                tokenStarted = true
                escaping = false
                return@forEach
            }
            if (char == '\\' && quote != '\'') {
                escaping = true
                tokenStarted = true
                return@forEach
            }
            if (quote != null) {
                if (char == quote) {
                    quote = null
                } else {
                    current.append(char)
                }
                tokenStarted = true
                return@forEach
            }
            if (char == '\'' || char == '"') {
                quote = char
                tokenStarted = true
                return@forEach
            }
            if (char in shellOperators) return null
            if (char.isWhitespace()) {
                if (tokenStarted) {
                    tokens += current.toString()
                    current.setLength(0)
                    tokenStarted = false
                }
            } else {
                current.append(char)
                tokenStarted = true
            }
        }
        if (escaping || quote != null) return null
        if (tokenStarted) tokens += current.toString()
        return tokens
    }
}
