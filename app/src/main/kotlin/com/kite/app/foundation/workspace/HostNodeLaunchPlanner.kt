package com.kite.app.foundation.workspace

import android.content.Context
import com.kite.app.foundation.contracts.ContainerLaunchConfig
import com.kite.app.foundation.contracts.ContainerRecord
import com.kite.app.foundation.runtime.HostNodeChildProcessContract
import com.kite.app.foundation.runtime.HostNodeRuntimeProvider
import com.kite.app.foundation.runtime.HostNodeTerminalLaunchResult
import com.kite.app.foundation.runtime.RuntimeExecutionRequest
import java.io.File
import java.util.UUID

internal sealed interface HostNodeLaunchPlan {
    data class Ready(val config: ContainerLaunchConfig) : HostNodeLaunchPlan
    data class Fallback(val reason: String) : HostNodeLaunchPlan
}

/**
 * 统一选择 Host Node 或 PRoot，并给 Host Node 附加完整 Linux 子进程合同。
 * 调用方继续拥有终端或后台进程生命周期，本计划器不创建进程、不写运行状态。
 */
internal object HostNodeLaunchPlanner {
    fun plan(
        context: Context,
        container: ContainerRecord,
        workspaceDirectory: File,
        request: RuntimeExecutionRequest,
    ): HostNodeLaunchPlan {
        val baseConfig = when (val result = HostNodeRuntimeProvider.prepare(
            context = context,
            container = container,
            workspaceDirectory = workspaceDirectory,
            request = request,
        )) {
            is HostNodeTerminalLaunchResult.Ready -> result.config
            is HostNodeTerminalLaunchResult.Fallback -> return HostNodeLaunchPlan.Fallback(result.reason)
        }

        return runCatching {
            val marker = "__kite_host_node_child__${UUID.randomUUID()}"
            val childExecConfig = WorkSurfaceRuntimeBridge.buildArgvExecConfig(
                context = context,
                workingDirectory = request.workingDirectory?.trim().orEmpty().ifBlank { "/workspace" },
                argv = listOf(marker),
            )
            HostNodeLaunchPlan.Ready(
                HostNodeChildProcessContract.from(childExecConfig, marker).attachTo(baseConfig)
            )
        }.getOrElse { error ->
            HostNodeLaunchPlan.Fallback(
                "child_process_contract_unavailable:${error.javaClass.simpleName}"
            )
        }
    }
}
