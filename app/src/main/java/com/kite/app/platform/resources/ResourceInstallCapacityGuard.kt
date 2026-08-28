package com.kite.app.platform.resources

import com.kite.app.resources.KiteResourceInstallRecipes
import java.io.File

internal data class ResourceInstallCapacitySnapshot(
    val resourceId: String,
    val availableBytes: Long,
    val requiredBytes: Long,
    val existingInstallBytes: Long,
    val declaredWorkingBytes: Long,
)

/** 在复制资源级候选目录前，用当前可知上限拒绝明显无法完成的资源安装。 */
internal class ResourceInstallCapacityGuard(
    private val availableBytes: (File) -> Long = File::getUsableSpace,
    private val directoryBytes: (File) -> Long = ::directoryLogicalBytes,
    private val minimumWorkingBytes: Long = DEFAULT_MINIMUM_WORKING_BYTES,
    private val safetyReserveBytes: Long = DEFAULT_SAFETY_RESERVE_BYTES,
) {
    fun requireCapacity(
        workspaceDirectory: File,
        resourceId: String,
        declaredWorkingBytes: Long,
    ): ResourceInstallCapacitySnapshot {
        val safeResourceId = KiteResourceInstallRecipes.safeId(resourceId)
        val workspace = workspaceDirectory.absoluteFile.normalize()
        val installRoot = File(workspace, ".kf/software/$safeResourceId").absoluteFile.normalize()
        check(installRoot.toPath().startsWith(workspace.toPath())) {
            "资源空间检查路径越界：${installRoot.absolutePath}"
        }
        val existingBytes = directoryBytes(installRoot).coerceAtLeast(0L)
        val declaredBytes = declaredWorkingBytes.coerceAtLeast(0L)
        val workingBytes = maxOf(minimumWorkingBytes, existingBytes, declaredBytes)
        val requiredBytes = saturatingAdd(workingBytes, safetyReserveBytes.coerceAtLeast(0L))
        val available = availableBytes(workspace).coerceAtLeast(0L)
        check(available >= requiredBytes) {
            "资源安装空间不足：resource=$safeResourceId requiredBytes=$requiredBytes " +
                "availableBytes=$available existingBytes=$existingBytes declaredBytes=$declaredBytes"
        }
        return ResourceInstallCapacitySnapshot(
            resourceId = safeResourceId,
            availableBytes = available,
            requiredBytes = requiredBytes,
            existingInstallBytes = existingBytes,
            declaredWorkingBytes = declaredBytes,
        )
    }

    companion object {
        private const val DEFAULT_MINIMUM_WORKING_BYTES = 64L * 1024L * 1024L
        private const val DEFAULT_SAFETY_RESERVE_BYTES = 32L * 1024L * 1024L

        private fun directoryLogicalBytes(root: File): Long {
            if (!root.exists()) return 0L
            return root.walkTopDown()
                .filter(File::isFile)
                .fold(0L) { total, file -> saturatingAdd(total, file.length().coerceAtLeast(0L)) }
        }

        private fun saturatingAdd(left: Long, right: Long): Long =
            if (right > Long.MAX_VALUE - left) Long.MAX_VALUE else left + right
    }
}
