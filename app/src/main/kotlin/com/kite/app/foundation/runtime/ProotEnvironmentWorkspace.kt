package com.kite.app.foundation.runtime

import com.kite.app.foundation.contracts.ContainerRecord
import java.io.File

/**
 * 环境工作区的唯一解析规则。
 *
 * Ubuntu rootfs 由 PRoot View 提供环境变化层；普通 `/workspace` 项目文件不在 rootfs 内，
 * 因此需要独立的宿主目录。`.kf` 是现有 Android/PRoot 控制命名空间，继续映射旧工作区，
 * 其中 `.kf/bin` 与 `.kf/software` 仍由 View scope 隔离，`.kf/system` 由 Android 持有并共享。
 */
internal data class ProotEnvironmentWorkspacePlan(
    val environmentId: String,
    val workspaceDirectory: File,
    val sharedControlDirectory: File?,
) {
    val usesLegacyWorkspace: Boolean
        get() = sharedControlDirectory == null

    fun ensureReady() {
        require(workspaceDirectory.mkdirs() || workspaceDirectory.isDirectory) {
            "无法创建环境工作区：${workspaceDirectory.absolutePath}"
        }
        sharedControlDirectory?.let { directory ->
            require(directory.isDirectory) {
                "共享 .kf 控制目录不存在：${directory.absolutePath}"
            }
        }
    }

    fun workspaceBindMounts(): List<ProotBindMount> = buildList {
        add(ProotBindMount(
            sourcePath = workspaceDirectory.absolutePath,
            targetPath = RuntimeBoundary.CONTAINER_WORKSPACE_PATH,
            role = "environment_workspace",
        ))
        sharedControlDirectory?.let { directory ->
            // PRoot 按参数顺序处理 bind；父工作区先挂，随后用更具体的 .kf 覆盖。
            add(ProotBindMount(
                sourcePath = directory.absolutePath,
                targetPath = "${RuntimeBoundary.CONTAINER_WORKSPACE_PATH}/.kf",
                role = "shared_workspace_control",
            ))
        }
    }
}

internal object ProotEnvironmentWorkspace {
    private const val ENVIRONMENT_DIRECTORY = ".environments"

    fun plan(
        container: ContainerRecord,
        binding: ProotViewBinding?,
    ): ProotEnvironmentWorkspacePlan {
        val environmentId = binding?.environmentId ?: ProotViewStore.DEFAULT_ENVIRONMENT_ID
        requireSafePathId(container.id, "containerId")
        requireSafePathId(environmentId, "environmentId")
        val legacyWorkspace = File(container.workspacePath).absoluteFile.normalize()
        if (environmentId == ProotViewStore.DEFAULT_ENVIRONMENT_ID) {
            return ProotEnvironmentWorkspacePlan(
                environmentId = environmentId,
                workspaceDirectory = legacyWorkspace,
                sharedControlDirectory = null,
            )
        }

        val sharedRoot = requireNotNull(legacyWorkspace.parentFile) {
            "容器工作区缺少父目录：${legacyWorkspace.absolutePath}"
        }
        val environmentRoot = File(sharedRoot, ENVIRONMENT_DIRECTORY).absoluteFile.normalize()
        val workspace = File(environmentRoot, "${container.id}/$environmentId/workspace")
            .absoluteFile
            .normalize()
        require(workspace.toPath().startsWith(environmentRoot.toPath())) {
            "环境工作区越界：${workspace.absolutePath}"
        }
        return ProotEnvironmentWorkspacePlan(
            environmentId = environmentId,
            workspaceDirectory = workspace,
            sharedControlDirectory = File(legacyWorkspace, ".kf").absoluteFile.normalize(),
        )
    }

    private fun requireSafePathId(value: String, field: String) {
        require(value.isNotBlank() && value.length <= 64 &&
            value.all { it.isLetterOrDigit() || it == '-' || it == '_' }
        ) { "$field 含不安全字符" }
    }
}
