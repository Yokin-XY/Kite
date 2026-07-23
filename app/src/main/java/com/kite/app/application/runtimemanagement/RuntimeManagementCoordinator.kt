package com.kite.app.application.runtimemanagement

import com.kite.app.run.CardRunStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface RuntimeManagementCommand {
    val mutationKey: String

    data class StopRun(
        val instanceId: String,
        override val mutationKey: String = "run:$instanceId"
    ) : RuntimeManagementCommand

    data class EndTerminal(
        val sessionId: String,
        override val mutationKey: String = "terminal:$sessionId"
    ) : RuntimeManagementCommand

    data class EndProcess(
        val processId: String,
        val pid: Int,
        override val mutationKey: String = "process:$processId"
    ) : RuntimeManagementCommand

    data class EndWorkloadScope(
        val workloadScopeId: String,
        override val mutationKey: String,
    ) : RuntimeManagementCommand

    data class StopBackgroundRuntime(
        val runtimeId: String,
        override val mutationKey: String = "runtime:$runtimeId"
    ) : RuntimeManagementCommand

    data class RestartBackgroundRuntime(
        val runtimeId: String,
        override val mutationKey: String = "runtime:$runtimeId"
    ) : RuntimeManagementCommand
}

enum class RuntimeManagementCommandPhase {
    Requested,
    AwaitingConfirmation,
    Failed
}

data class RuntimeManagementCommandState(
    val command: RuntimeManagementCommand,
    val phase: RuntimeManagementCommandPhase,
    val requestedAt: Long,
    val deadlineAt: Long,
    val message: String = "",
    val stopTarget: RuntimeStopConfirmationTarget? = null
)

data class RuntimeStopConfirmationTarget(
    val rootInstanceId: String,
    val rootGeneration: Long,
    val instanceGenerations: Map<String, Long>,
    val processIds: Set<String> = emptySet(),
    val terminalSessionIds: Set<String> = emptySet(),
)

sealed interface RuntimeManagementSubmitResult {
    data class Accepted(val mutationKey: String) : RuntimeManagementSubmitResult
    data class Ignored(val reason: String) : RuntimeManagementSubmitResult
}

/** 运行管理动作的确认事务。只提交给既有状态拥有者，并等待统一快照证明结果。 */
class RuntimeManagementCoordinator internal constructor(
    private val gateway: RuntimeManagementGateway,
    private val stopRun: (String) -> RuntimeManagementDispatchResult,
    private val clock: () -> Long = System::currentTimeMillis,
    private val confirmationTimeoutMs: Long = DEFAULT_CONFIRMATION_TIMEOUT_MS
) {
    private val lock = Any()
    private val mutableCommands = MutableStateFlow<Map<String, RuntimeManagementCommandState>>(emptyMap())
    val commands: StateFlow<Map<String, RuntimeManagementCommandState>> = mutableCommands.asStateFlow()

    fun refresh(force: Boolean = false) {
        gateway.refresh(force)
    }

    suspend fun submit(command: RuntimeManagementCommand): RuntimeManagementSubmitResult {
        val now = clock()
        val stopTarget = if (command is RuntimeManagementCommand.StopRun) {
            gateway.currentSnapshot().captureStopTarget(command.instanceId)
        } else {
            null
        }
        synchronized(lock) {
            val current = mutableCommands.value[command.mutationKey]
            if (current?.phase == RuntimeManagementCommandPhase.Requested ||
                current?.phase == RuntimeManagementCommandPhase.AwaitingConfirmation
            ) {
                return RuntimeManagementSubmitResult.Ignored("already_pending")
            }
            put(
                RuntimeManagementCommandState(
                    command = command,
                    phase = RuntimeManagementCommandPhase.Requested,
                    requestedAt = now,
                    deadlineAt = now + confirmationTimeoutMs.coerceAtLeast(1L),
                    stopTarget = stopTarget
                )
            )
        }

        val dispatch = runCatching { dispatch(command) }
            .getOrElse { error -> RuntimeManagementDispatchResult.rejected(error.message ?: error.javaClass.simpleName) }
        if (!dispatch.accepted) {
            fail(command.mutationKey, dispatch.message.ifBlank { "action_rejected" })
            return RuntimeManagementSubmitResult.Ignored(dispatch.message.ifBlank { "action_rejected" })
        }

        synchronized(lock) {
            val current = mutableCommands.value[command.mutationKey]
            if (current != null && current.command == command) {
                put(
                    current.copy(
                        phase = RuntimeManagementCommandPhase.AwaitingConfirmation,
                        message = dispatch.message
                    )
                )
            }
        }
        gateway.refresh(force = true)
        reconcile(gateway.currentSnapshot())
        return RuntimeManagementSubmitResult.Accepted(command.mutationKey)
    }

    fun reconcile(snapshot: RuntimeManagementSnapshot, now: Long = clock()) {
        synchronized(lock) {
            if (mutableCommands.value.isEmpty()) return
            var next = mutableCommands.value
            mutableCommands.value.values.forEach { commandState ->
                if (commandState.phase == RuntimeManagementCommandPhase.Failed) return@forEach
                when {
                    snapshot.confirms(commandState) ->
                        next = next - commandState.command.mutationKey
                    now >= commandState.deadlineAt ->
                        next = next + (
                            commandState.command.mutationKey to commandState.copy(
                                phase = RuntimeManagementCommandPhase.Failed,
                                message = "未在规定时间内确认操作结果"
                            )
                        )
                }
            }
            if (next != mutableCommands.value) mutableCommands.value = next
        }
    }

    fun dismissFailure(mutationKey: String) {
        synchronized(lock) {
            val current = mutableCommands.value[mutationKey] ?: return
            if (current.phase == RuntimeManagementCommandPhase.Failed) {
                mutableCommands.value = mutableCommands.value - mutationKey
            }
        }
    }

    private suspend fun dispatch(command: RuntimeManagementCommand): RuntimeManagementDispatchResult = when (command) {
        is RuntimeManagementCommand.StopRun -> stopRun(command.instanceId)
        is RuntimeManagementCommand.EndTerminal -> gateway.endTerminal(command.sessionId)
        is RuntimeManagementCommand.EndProcess -> gateway.endProcess(command.processId, command.pid)
        is RuntimeManagementCommand.EndWorkloadScope -> gateway.endWorkloadScope(command.workloadScopeId)
        is RuntimeManagementCommand.StopBackgroundRuntime -> gateway.stopBackgroundRuntime(command.runtimeId)
        is RuntimeManagementCommand.RestartBackgroundRuntime -> gateway.restartBackgroundRuntime(command.runtimeId)
    }

    private fun RuntimeManagementSnapshot.confirms(
        state: RuntimeManagementCommandState
    ): Boolean = when (val command = state.command) {
        is RuntimeManagementCommand.StopRun -> confirmsStop(state.stopTarget)
        is RuntimeManagementCommand.EndTerminal -> terminals.none { it.id == command.sessionId && it.isLive }
        is RuntimeManagementCommand.EndProcess -> processes.none {
            it.id == command.processId ||
                (command.processId.isBlank() && command.pid > 0 && it.pid == command.pid)
        }
        is RuntimeManagementCommand.EndWorkloadScope ->
            processes.none { it.workloadScopeId == command.workloadScopeId }
        is RuntimeManagementCommand.StopBackgroundRuntime -> processes.none {
            it.linkedRuntimeId == command.runtimeId
        }
        is RuntimeManagementCommand.RestartBackgroundRuntime ->
            refreshedAt >= state.requestedAt && processes.any { it.linkedRuntimeId == command.runtimeId }
    }

    private fun RuntimeManagementSnapshot.captureStopTarget(instanceId: String): RuntimeStopConfirmationTarget? {
        val root = topology.node(instanceId) ?: return null
        val subtree = topology.subtree(instanceId)
        return RuntimeStopConfirmationTarget(
            rootInstanceId = instanceId,
            rootGeneration = root.identity.generation,
            instanceGenerations = subtree.associate { it.identity.instanceId to it.identity.generation },
            processIds = subtree.flatMap { it.processIds }.toSet(),
            terminalSessionIds = subtree.flatMap { it.terminalSessionIds }.toSet(),
        )
    }

    private fun RuntimeManagementSnapshot.confirmsStop(target: RuntimeStopConfirmationTarget?): Boolean {
        target ?: return false
        val anyCurrentRoot = topology.node(target.rootInstanceId)
        if (anyCurrentRoot != null && anyCurrentRoot.identity.generation != target.rootGeneration) {
            return true
        }
        val currentRoot = anyCurrentRoot
            ?.takeIf { it.identity.generation == target.rootGeneration }
        if (currentRoot != null) {
            if (currentRoot.run.status != CardRunStatus.Stopped) return false
        }
        val staleDescendantRemains = target.instanceGenerations.any { (instanceId, generation) ->
            instanceId != target.rootInstanceId &&
                topology.node(instanceId)?.identity?.generation == generation
        }
        if (staleDescendantRemains) return false
        if (processes.any { it.id in target.processIds }) return false
        if (terminals.any { it.id in target.terminalSessionIds && it.isLive }) return false
        return currentRoot == null || currentRoot.run.status == CardRunStatus.Stopped
    }

    private fun fail(mutationKey: String, message: String) {
        synchronized(lock) {
            val current = mutableCommands.value[mutationKey] ?: return
            put(current.copy(phase = RuntimeManagementCommandPhase.Failed, message = message))
        }
    }

    private fun put(state: RuntimeManagementCommandState) {
        mutableCommands.value = mutableCommands.value + (state.command.mutationKey to state)
    }

    companion object {
        const val DEFAULT_CONFIRMATION_TIMEOUT_MS = 15_000L
    }
}
