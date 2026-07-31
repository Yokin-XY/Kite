package com.kite.app.foundation.workspace

import android.content.Context
import com.kite.app.foundation.contracts.ContainerLaunchConfig
import com.kite.app.foundation.contracts.ContainerRecord
import com.kite.app.foundation.runtime.HostNodeChildProcessContract
import com.kite.app.foundation.runtime.HostNodeProviderContext
import com.kite.app.foundation.runtime.HostNodeRuntimeProvider
import com.kite.app.foundation.runtime.HostPythonCommandResolver
import com.kite.app.foundation.runtime.HostPythonProviderContext
import com.kite.app.foundation.runtime.HostPythonRuntimeProvider
import com.kite.app.foundation.runtime.RuntimeExecutionRequest
import com.kite.app.foundation.runtime.RuntimeProviderDecision
import com.kite.app.foundation.runtime.allowsProviderFallback
import java.io.File
import java.util.UUID

internal enum class ManagedRuntimeLane(val value: String) {
    HOST_NODE("host_node"),
    HOST_PYTHON("host_python"),
}

internal sealed interface ManagedRuntimeLaunchPlan {
    data class Ready(
        val config: ContainerLaunchConfig,
        val lane: ManagedRuntimeLane,
        val reason: String,
    ) : ManagedRuntimeLaunchPlan

    data class Fallback(val reason: String) : ManagedRuntimeLaunchPlan
    data class Blocked(val reason: String) : ManagedRuntimeLaunchPlan
}

/**
 * 入口无关的通用依赖计划器。Provider 只生成配置；本计划器也不创建进程、不写 Store。
 * Python 只接受结构化受管解释器，其他命令保持既有 Node/shebang 判断。
 */
internal object ManagedRuntimeLaunchPlanner {
    fun plan(
        context: Context,
        container: ContainerRecord,
        workspaceDirectory: File,
        request: RuntimeExecutionRequest,
    ): ManagedRuntimeLaunchPlan = if (HostPythonCommandResolver.isCandidate(request)) {
        preparePython(context, container, workspaceDirectory, request)
    } else {
        prepareNode(context, container, workspaceDirectory, request)
    }

    private fun preparePython(
        context: Context,
        container: ContainerRecord,
        workspaceDirectory: File,
        request: RuntimeExecutionRequest,
    ): ManagedRuntimeLaunchPlan = when (val decision = HostPythonRuntimeProvider.prepare(
        context = HostPythonProviderContext(context, container, workspaceDirectory),
        request = request,
    )) {
        is RuntimeProviderDecision.Ready -> ManagedRuntimeLaunchPlan.Ready(
            config = decision.plan,
            lane = ManagedRuntimeLane.HOST_PYTHON,
            reason = decision.reason,
        )
        is RuntimeProviderDecision.Unsupported -> fallbackOrBlocked(request, decision.reason)
        is RuntimeProviderDecision.Blocked -> ManagedRuntimeLaunchPlan.Blocked(decision.reason)
    }

    private fun prepareNode(
        context: Context,
        container: ContainerRecord,
        workspaceDirectory: File,
        request: RuntimeExecutionRequest,
    ): ManagedRuntimeLaunchPlan {
        val baseConfig = when (val decision = HostNodeRuntimeProvider.prepare(
            context = HostNodeProviderContext(context, container, workspaceDirectory),
            request = request,
        )) {
            is RuntimeProviderDecision.Ready -> decision
            is RuntimeProviderDecision.Unsupported -> return fallbackOrBlocked(request, decision.reason)
            is RuntimeProviderDecision.Blocked -> return ManagedRuntimeLaunchPlan.Blocked(decision.reason)
        }
        return runCatching {
            val marker = "__kite_host_node_child__${UUID.randomUUID()}"
            val childExecConfig = WorkSurfaceRuntimeBridge.buildArgvExecConfig(
                context = context,
                workingDirectory = request.workingDirectory?.trim().orEmpty().ifBlank { "/workspace" },
                argv = listOf(marker),
            )
            ManagedRuntimeLaunchPlan.Ready(
                config = HostNodeChildProcessContract.from(childExecConfig, marker).attachTo(baseConfig.plan),
                lane = ManagedRuntimeLane.HOST_NODE,
                reason = baseConfig.reason,
            )
        }.getOrElse { error ->
            fallbackOrBlocked(request, "child_process_contract_unavailable:${error.javaClass.simpleName}")
        }
    }

    private fun fallbackOrBlocked(
        request: RuntimeExecutionRequest,
        reason: String,
    ): ManagedRuntimeLaunchPlan = if (request.fallbackPolicy.allowsProviderFallback()) {
        ManagedRuntimeLaunchPlan.Fallback(reason)
    } else {
        ManagedRuntimeLaunchPlan.Blocked("fallback_disabled:$reason")
    }
}
