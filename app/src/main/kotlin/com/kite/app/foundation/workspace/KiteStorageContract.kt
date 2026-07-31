package com.kite.app.foundation.workspace

import java.io.File

/**
 * Kite 双文件体系的纯路径契约。
 *
 * `/workspace` 是 Agent 项目的唯一根；安卓共享存储只作为额外可操作文件树，
 * 不会因为已经挂入 Ubuntu 就自动成为项目工作区。
 */
object KiteStorageContract {

    const val CONTAINER_WORKSPACE_ROOT = "/workspace"
    const val WORKSPACE_CONTROL_DIR_NAME = ".kf"
    private val RESERVED_ROOT_NAMES = setOf(
        WORKSPACE_CONTROL_DIR_NAME,
        ".gradle-user",
        ".android-user",
        ".android-data",
        "npm-global"
    )

    fun normalizeWorkspacePath(rawPath: String?): String? {
        val normalized = normalizeAbsoluteUnixPath(rawPath) ?: return null
        return normalized.takeIf {
            it == CONTAINER_WORKSPACE_ROOT || it.startsWith("$CONTAINER_WORKSPACE_ROOT/")
        }
    }

    fun isSelectableProjectPath(rawPath: String?): Boolean {
        val normalized = normalizeWorkspacePath(rawPath) ?: return false
        if (normalized == CONTAINER_WORKSPACE_ROOT) return false
        val firstSegment = workspaceRelativePath(normalized)?.substringBefore('/').orEmpty()
        return firstSegment !in RESERVED_ROOT_NAMES
    }

    fun isReservedWorkspaceRootName(name: String): Boolean = name in RESERVED_ROOT_NAMES

    fun workspaceRelativePath(rawPath: String?): String? {
        val normalized = normalizeWorkspacePath(rawPath) ?: return null
        return normalized.removePrefix(CONTAINER_WORKSPACE_ROOT).trimStart('/')
    }

    fun resolveHostWorkspacePath(hostWorkspaceRoot: File, containerPath: String?): File? {
        val relative = workspaceRelativePath(containerPath) ?: return null
        val normalizedRoot = hostWorkspaceRoot.absoluteFile.toPath().normalize()
        val target = normalizedRoot.resolve(relative).normalize()
        if (target != normalizedRoot && !target.startsWith(normalizedRoot)) return null
        return target.toFile()
    }

    internal fun normalizeAbsoluteUnixPath(rawPath: String?): String? {
        val trimmed = rawPath?.trim().orEmpty()
        if (trimmed.isEmpty() || !trimmed.startsWith('/') || '\u0000' in trimmed) return null

        val segments = ArrayDeque<String>()
        trimmed.split('/').forEach { segment ->
            when (segment) {
                "", "." -> Unit
                ".." -> if (segments.isEmpty()) return null else segments.removeLast()
                else -> segments.addLast(segment)
            }
        }
        return "/${segments.joinToString("/")}".removeSuffixUnlessRoot()
    }

    private fun String.removeSuffixUnlessRoot(): String = if (this == "/") this else removeSuffix("/")
}

data class AndroidSharedStorageVolume(
    val path: String,
    val displayName: String
) {
    val hostPath: String = path
    val containerPath: String = path
}

/**
 * 把 Android 已确认可直接访问的卷根整理为 PRoot 同路径挂载计划。
 */
object AndroidSharedStorageVolumePlan {

    fun fromRoots(roots: List<Pair<String, String>>): List<AndroidSharedStorageVolume> {
        val normalized = roots.mapNotNull { (rawPath, displayName) ->
            normalizeStorageRoot(rawPath)?.let { path ->
                AndroidSharedStorageVolume(path, displayName.trim().ifBlank { path.substringAfterLast('/') })
            }
        }.distinctBy { it.path }

        return normalized.filter { candidate ->
            normalized.none { other ->
                other !== candidate && candidate.path.startsWith("${other.path}/")
            }
        }.sortedBy { it.path }
    }

    fun normalizeStorageRoot(rawPath: String?): String? {
        val normalized = KiteStorageContract.normalizeAbsoluteUnixPath(rawPath) ?: return null
        if (normalized == "/sdcard") return normalized
        if (!normalized.startsWith("/storage/")) return null
        if (normalized == "/storage/emulated" || normalized == "/storage/self") return null
        if (normalized.contains("/Android/data/") || normalized.endsWith("/Android/data")) return null
        if (normalized.contains("/Android/obb/") || normalized.endsWith("/Android/obb")) return null
        return normalized
    }
}
