package com.kite.app.foundation.workspace

import java.io.File

data class WorkspaceDirectoryEntry(
    val name: String,
    val containerPath: String,
    val lastModified: Long,
    val itemCount: Int,
)

/** Kite 自有项目选择页使用的受限 `/workspace` 目录浏览器。 */
object WorkspaceDirectoryBrowser {

    fun listDirectories(
        hostWorkspaceRoot: File,
        currentContainerPath: String,
    ): List<WorkspaceDirectoryEntry> {
        val current = resolveExistingDirectory(hostWorkspaceRoot, currentContainerPath)
        return current.listFiles().orEmpty()
            .asSequence()
            .filter(File::isDirectory)
            .filterNot { it.name.startsWith('.') || isSymbolicAlias(it) }
            .mapNotNull { child ->
                val containerPath = containerPathFor(hostWorkspaceRoot, child) ?: return@mapNotNull null
                if (!KiteStorageContract.isSelectableProjectPath(containerPath)) return@mapNotNull null
                WorkspaceDirectoryEntry(
                    name = child.name,
                    containerPath = containerPath,
                    lastModified = child.lastModified(),
                    itemCount = child.list()
                        ?.count { name -> !name.startsWith('.') }
                        ?: 0,
                )
            }
            .sortedBy { it.name.lowercase() }
            .toList()
    }

    fun createDirectory(
        hostWorkspaceRoot: File,
        parentContainerPath: String,
        displayName: String,
    ): WorkspaceDirectoryEntry {
        val parent = resolveExistingDirectory(hostWorkspaceRoot, parentContainerPath)
        val name = validateDisplayName(displayName)
        if (parent.canonicalFile == hostWorkspaceRoot.canonicalFile &&
            KiteStorageContract.isReservedWorkspaceRootName(name)
        ) {
            throw IllegalArgumentException("该名称由 Kite 保留")
        }
        val target = File(parent, name)
        val containerPath = containerPathFor(hostWorkspaceRoot, target)
            ?: throw IllegalArgumentException("目录不能越出 /workspace")
        if (!KiteStorageContract.isSelectableProjectPath(containerPath)) {
            throw IllegalArgumentException("该目录不能作为项目")
        }
        if (target.exists()) throw IllegalArgumentException("同名目录已经存在")
        if (!target.mkdir()) throw IllegalStateException("无法创建目录")
        return WorkspaceDirectoryEntry(
            name = target.name,
            containerPath = containerPath,
            lastModified = target.lastModified(),
            itemCount = 0,
        )
    }

    fun parentPath(containerPath: String): String? {
        val normalized = KiteStorageContract.normalizeWorkspacePath(containerPath) ?: return null
        if (normalized == KiteStorageContract.CONTAINER_WORKSPACE_ROOT) return null
        return normalized.substringBeforeLast('/').ifBlank {
            KiteStorageContract.CONTAINER_WORKSPACE_ROOT
        }
    }

    fun containerPathFor(hostWorkspaceRoot: File, hostDirectory: File): String? {
        val root = hostWorkspaceRoot.canonicalFile.toPath()
        val directory = hostDirectory.canonicalFile.toPath()
        if (directory != root && !directory.startsWith(root)) return null
        val relative = root.relativize(directory).joinToString("/")
        return if (relative.isBlank()) {
            KiteStorageContract.CONTAINER_WORKSPACE_ROOT
        } else {
            "${KiteStorageContract.CONTAINER_WORKSPACE_ROOT}/$relative"
        }
    }

    private fun resolveExistingDirectory(hostWorkspaceRoot: File, containerPath: String): File {
        val directory = KiteStorageContract.resolveHostWorkspacePath(hostWorkspaceRoot, containerPath)
            ?: throw IllegalArgumentException("目录不属于 /workspace")
        val canonicalRoot = hostWorkspaceRoot.canonicalFile.toPath()
        val canonicalDirectory = directory.canonicalFile.toPath()
        if (canonicalDirectory != canonicalRoot && !canonicalDirectory.startsWith(canonicalRoot)) {
            throw IllegalArgumentException("目录不能越出 /workspace")
        }
        if (!directory.isDirectory) throw IllegalArgumentException("目录不存在")
        return directory
    }

    private fun validateDisplayName(displayName: String): String {
        val name = displayName.trim()
        if (name.isBlank() || name.startsWith('.') || name == ".." ||
            '/' in name || '\\' in name || '\u0000' in name
        ) {
            throw IllegalArgumentException("请输入有效的文件夹名称")
        }
        return name
    }

    private fun isSymbolicAlias(file: File): Boolean = runCatching {
        file.absoluteFile.toPath().normalize() != file.canonicalFile.toPath()
    }.getOrDefault(true)
}
