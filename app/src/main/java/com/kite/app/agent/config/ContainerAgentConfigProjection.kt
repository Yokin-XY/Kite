package com.kite.app.agent.config

import com.kite.app.foundation.contracts.ContainerRecord
import com.kite.app.foundation.runtime.ProotEnvironmentWorkspace
import com.kite.app.foundation.runtime.ProotViewBinding
import com.kite.app.foundation.runtime.ProotViewRuntime
import com.kite.app.foundation.runtime.ProotViewStore
import java.io.File

/**
 * 把 Agent 看到的容器路径投影成当前运行环境真正使用的宿主文件。
 *
 * `/workspace` 是独立 bind mount，必须跟随环境工作区；其他路径才通过 PRoot View 投影。
 * 配置适配器不能直接写 Base rootfs，否则活跃 View 可能继续读取旧 Upper，造成“保存成功但未生效”。
 */
internal class ContainerAgentConfigProjection(
    private val containerProvider: () -> ContainerRecord?
) {
    data class FileProjection(
        val containerPath: String,
        val baseFile: File,
        val readFile: File,
        val writeFile: File,
        val viewId: String?
    )

    fun resolve(containerPath: String): FileProjection? {
        require(containerPath.startsWith('/')) { "Agent 配置必须使用容器绝对路径" }
        val container = containerProvider() ?: return null
        val rootfs = File(container.rootfsPath).canonicalFile
        require(rootfs.isDirectory) { "运行容器 rootfs 不存在" }
        val activeBinding = ProotViewRuntime.resolveActiveBinding(container)
        resolveWorkspacePath(container, activeBinding, containerPath)?.let { return it }

        val requested = File(rootfs, containerPath.removePrefix("/")).canonicalFile
        require(requested.toPath().startsWith(rootfs.toPath())) { "Agent 配置路径越界" }

        if (activeBinding == null) {
            val physical = if (requested.exists()) requested.canonicalFile else requested.absoluteFile
            return FileProjection(containerPath, physical, physical, physical, viewId = null)
        }
        val projection = ProotViewStore.forContainer(container).projectPath(requested)
        return FileProjection(
            containerPath = containerPath,
            baseFile = requested.absoluteFile,
            readFile = projection.visibleFile ?: projection.writableFile,
            writeFile = projection.writableFile,
            viewId = activeBinding.viewId
        )
    }

    private fun resolveWorkspacePath(
        container: ContainerRecord,
        activeBinding: ProotViewBinding?,
        containerPath: String,
    ): FileProjection? {
        if (containerPath != WORKSPACE_PATH && !containerPath.startsWith("$WORKSPACE_PATH/")) {
            return null
        }

        val plan = ProotEnvironmentWorkspace.plan(container, activeBinding)
        val relative = containerPath.removePrefix(WORKSPACE_PATH).removePrefix("/")
        val sharedControl = plan.sharedControlDirectory
        val (root, remainder) = if (
            sharedControl != null && (relative == CONTROL_DIRECTORY || relative.startsWith("$CONTROL_DIRECTORY/"))
        ) {
            sharedControl to relative.removePrefix(CONTROL_DIRECTORY).removePrefix("/")
        } else {
            plan.workspaceDirectory to relative
        }
        val physical = resolveWithin(root, remainder)
        return FileProjection(
            containerPath = containerPath,
            baseFile = physical,
            readFile = physical,
            writeFile = physical,
            viewId = activeBinding?.viewId,
        )
    }

    private fun resolveWithin(root: File, relativePath: String): File {
        val normalizedRoot = root.absoluteFile.normalize()
        val requested = if (relativePath.isBlank()) {
            normalizedRoot
        } else {
            File(normalizedRoot, relativePath).absoluteFile.normalize()
        }
        require(requested.toPath().startsWith(normalizedRoot.toPath())) { "Agent 配置路径越界" }
        return requested
    }

    private companion object {
        const val WORKSPACE_PATH = "/workspace"
        const val CONTROL_DIRECTORY = ".kf"
    }
}
