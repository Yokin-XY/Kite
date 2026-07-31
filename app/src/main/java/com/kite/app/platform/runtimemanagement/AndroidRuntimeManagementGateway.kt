package com.kite.app.platform.runtimemanagement

import android.content.Context
import com.kite.app.application.runtimemanagement.RuntimeManagedOwnerKind
import com.kite.app.application.runtimemanagement.RuntimeManagedCardIcon
import com.kite.app.application.runtimemanagement.RuntimeManagedProcess
import com.kite.app.application.runtimemanagement.RuntimeManagedTerminal
import com.kite.app.application.runtimemanagement.RuntimeManagementDispatchResult
import com.kite.app.application.runtimemanagement.RuntimeManagementGateway
import com.kite.app.application.runtimemanagement.RuntimeManagementSnapshot
import com.kite.app.foundation.runtime.RuntimeHealthSnapshot
import com.kite.app.foundation.runtime.RuntimeHealthStore
import com.kite.app.foundation.runtime.TaskManagerAction
import com.kite.app.foundation.runtime.TaskManagerProcessItem
import com.kite.app.foundation.runtime.TaskManagerSnapshot
import com.kite.app.foundation.runtime.TaskManagerStore
import com.kite.app.foundation.runtime.TerminalSessionItem
import com.kite.app.foundation.runtime.TerminalSessionStore
import com.kite.app.foundation.runtime.TerminalSessionsSnapshot
import com.kite.app.run.CardRunState
import com.kite.app.run.CardRunStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** 把 Android 运行时的四个事实流映射成 Feature 可消费的一份快照。 */
internal class AndroidRuntimeManagementGateway(
    context: Context,
    private val environmentIdProvider: () -> String = { CardRunState.DEFAULT_ENVIRONMENT_ID }
) : RuntimeManagementGateway {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val snapshots: StateFlow<RuntimeManagementSnapshot> = combine(
        CardRunStore.runs,
        TerminalSessionStore.snapshot,
        TaskManagerStore.snapshot,
        RuntimeHealthStore.snapshot
    ) { runs, terminals, tasks, health ->
        val activeRuns = runs.filter { it.environmentId == environmentIdProvider() }
        mapSnapshot(activeRuns, terminals, tasks, health, cardIcons(activeRuns))
    }.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = currentSnapshot()
    )

    init {
        refresh(force = false)
    }

    override fun currentSnapshot(): RuntimeManagementSnapshot = CardRunStore.runs.value
        .filter { it.environmentId == environmentIdProvider() }
        .let { activeRuns ->
            mapSnapshot(
                runs = activeRuns,
                terminals = TerminalSessionStore.snapshot.value,
                tasks = TaskManagerStore.snapshot.value,
                health = RuntimeHealthStore.snapshot.value,
                cardIconsByRecipeId = cardIcons(activeRuns),
            )
        }

    private fun cardIcons(runs: List<CardRunState>): Map<String, RuntimeManagedCardIcon> = runs
        .map(CardRunState::recipeId)
        .filter(String::isNotBlank)
        .distinct()
        .mapNotNull { recipeId ->
            CardRunStore.registeredRecipe(recipeId)?.icon?.let { icon ->
                recipeId to RuntimeManagedCardIcon(icon.type, icon.name, icon.source)
            }
        }
        .toMap()

    override fun refresh(force: Boolean) {
        TerminalSessionStore.refresh(appContext, force = force)
        TaskManagerStore.refresh(appContext, force = force)
    }

    override suspend fun endTerminal(sessionId: String): RuntimeManagementDispatchResult {
        val id = sessionId.trim().takeIf(String::isNotBlank)
            ?: return RuntimeManagementDispatchResult.rejected("terminal_id_missing")
        TerminalSessionStore.end(appContext, id)
        return RuntimeManagementDispatchResult.accepted("terminal_end_requested")
    }

    override suspend fun endProcess(processId: String, pid: Int): RuntimeManagementDispatchResult {
        if (pid <= 1) return RuntimeManagementDispatchResult.rejected("invalid_pid")
        val item = TaskManagerStore.getProcess(processId)
            ?: processId.takeIf(String::isBlank)?.let {
                TaskManagerStore.snapshot.value.processes.firstOrNull { candidate -> candidate.pid == pid }
            }
            ?: return RuntimeManagementDispatchResult.rejected("process_not_found")
        if (item.processRef != null && item.processRef.hasStrongIdentity.not()) {
            return RuntimeManagementDispatchResult.rejected("process_identity_unavailable")
        }
        TaskManagerStore.endProcess(appContext, item, pid)
        return RuntimeManagementDispatchResult.accepted("process_end_requested")
    }

    override suspend fun endWorkloadScope(workloadScopeId: String): RuntimeManagementDispatchResult {
        val accepted = TaskManagerStore.endWorkloadScope(appContext, workloadScopeId)
        return if (accepted) {
            RuntimeManagementDispatchResult.accepted("workload_scope_end_requested")
        } else {
            RuntimeManagementDispatchResult.rejected("workload_scope_identity_unavailable")
        }
    }

    override suspend fun stopBackgroundRuntime(runtimeId: String): RuntimeManagementDispatchResult {
        val id = runtimeId.trim().takeIf(String::isNotBlank)
            ?: return RuntimeManagementDispatchResult.rejected("runtime_id_missing")
        TaskManagerStore.stopRuntime(appContext, id)
        return RuntimeManagementDispatchResult.accepted("runtime_stop_requested")
    }

    override suspend fun restartBackgroundRuntime(runtimeId: String): RuntimeManagementDispatchResult {
        val id = runtimeId.trim().takeIf(String::isNotBlank)
            ?: return RuntimeManagementDispatchResult.rejected("runtime_id_missing")
        TaskManagerStore.restartRuntime(appContext, id)
        return RuntimeManagementDispatchResult.accepted("runtime_restart_requested")
    }

    companion object {
        internal fun mapSnapshot(
            runs: List<CardRunState>,
            terminals: TerminalSessionsSnapshot,
            tasks: TaskManagerSnapshot,
            health: RuntimeHealthSnapshot,
            cardIconsByRecipeId: Map<String, RuntimeManagedCardIcon> = emptyMap(),
        ): RuntimeManagementSnapshot {
            val liveTerminalIds = terminals.liveSessions.mapTo(mutableSetOf(), TerminalSessionItem::id)
            val observedProcessCount = listOf(
                tasks.processes.size,
                health.prootTelemetry.processLiveTable.liveTraceeCount,
                health.processResourceSnapshot.processCount,
                health.processSnapshotMergedProcessCount,
                health.roots.sumOf { it.processCount }
            ).maxOrNull() ?: 0
            return RuntimeManagementSnapshot(
                runs = runs,
                terminals = terminals.sessions.map { it.toRuntimeManagedTerminal(it.id in liveTerminalIds) },
                processes = tasks.processes.map { it.toRuntimeManagedProcess() },
                cardIconsByRecipeId = cardIconsByRecipeId,
                observedProcessCount = observedProcessCount,
                refreshedAt = maxOf(tasks.refreshedAt, terminals.refreshedAt, health.reconciledAt)
            )
        }

        internal fun TaskManagerProcessItem.toRuntimeManagedProcess(): RuntimeManagedProcess {
            val identity = identityText()
            val isOwnerRoot = id.startsWith("root-") ||
                (runtimeRootPid == pid && (!runtimeOwnerId.isNullOrBlank() || !runtimeUnitId.isNullOrBlank()))
            return RuntimeManagedProcess(
                id = id,
                pid = pid,
                parentPid = parentPid,
                title = processName(identity, isOwnerRoot),
                stateLabel = stateLabel,
                commandLine = commandLine,
                purpose = processPurpose(identity),
                ownerKind = ownerKind(identity),
                ownerId = runtimeOwnerId,
                unitId = runtimeUnitId,
                workloadScopeId = workloadScopeId,
                ownerRootPid = runtimeRootPid,
                linkedTerminalSessionId = linkedTerminalSessionId,
                linkedRuntimeId = linkedRuntimeId,
                isOwnerRoot = isOwnerRoot,
                isRuntimeScaffold = isWorkloadLauncher || isRuntimeScaffold(identity),
                canEndDirectly = TaskManagerAction.END_PROCESS in availableActions,
                lifecycleId = lifecycleId,
                processGroupId = processGroupId,
                kernelState = kernelState.name,
                identityVerified = processRef?.hasStrongIdentity == true,
            )
        }

        private fun TerminalSessionItem.toRuntimeManagedTerminal(isLive: Boolean): RuntimeManagedTerminal =
            RuntimeManagedTerminal(
                id = id,
                title = title,
                statusLabel = statusLabel,
                processCount = processCount,
                rootPid = rootPid,
                observedPid = observedPid,
                isLive = isLive
            )

        private fun TaskManagerProcessItem.ownerKind(identity: String): RuntimeManagedOwnerKind = when {
            !linkedRuntimeId.isNullOrBlank() || runtimeOwnerKindLabel == "后台运行项" ->
                RuntimeManagedOwnerKind.BackgroundRuntime
            runtimeOwnerId?.startsWith("resource:") == true -> RuntimeManagedOwnerKind.Resource
            runtimeOwnerId?.startsWith("terminal:") == true ||
                !linkedTerminalSessionId.isNullOrBlank() ||
                runtimeOwnerKindLabel == "终端" -> RuntimeManagedOwnerKind.Terminal
            runtimeOwnerId?.startsWith("card:") == true || runtimeUnitId?.startsWith("card:") == true ->
                RuntimeManagedOwnerKind.Card
            isSystemProcess(identity) -> RuntimeManagedOwnerKind.System
            else -> RuntimeManagedOwnerKind.Unattributed
        }

        private fun TaskManagerProcessItem.processName(identity: String, isOwnerRoot: Boolean): String = when {
            "supervisord" in identity -> "容器守护进程"
            isOwnerRoot && ("容量工作器" in identity || "proot-capacity" in identity) -> "PRoot 容量工作器"
            "/runtime/bin/proot" in identity || "link2symlink" in identity -> "PRoot 容器入口"
            "/workspace/.kf/system/bin/kf-runner" in identity -> "Kite 命令启动器"
            "locale-check" in identity -> "语言环境检查"
            "mkdir -p /run/" in identity -> "运行目录准备"
            else -> title.ifBlank { command.ifBlank { commandLine.substringBefore(' ') } }
                .compact(36)
                .ifBlank { "进程" }
        }

        private fun TaskManagerProcessItem.processPurpose(identity: String): String = when {
            "supervisord" in identity -> "维护 Ubuntu 容器里的后台服务"
            "容量工作器" in identity || "proot-capacity" in identity -> "为卡片和终端保留可用的 PRoot 容量"
            "/runtime/bin/proot" in identity || "link2symlink" in identity -> "启动并隔离 Ubuntu 文件系统"
            "/workspace/.kf/system/bin/kf-runner" in identity -> "执行卡片命令前的统一入口"
            "locale-check" in identity -> "检查 Ubuntu 语言环境"
            "mkdir -p /run/" in identity -> "准备 Ubuntu 运行目录"
            else -> "卡片或用户启动的普通进程"
        }

        private fun TaskManagerProcessItem.identityText(): String = listOf(
            title,
            sourceLabel,
            runtimeOwnerKindLabel.orEmpty(),
            runtimeOwnerId.orEmpty(),
            runtimeUnitId.orEmpty(),
            command,
            commandLine
        ).joinToString(" ").lowercase()

        private fun TaskManagerProcessItem.isSystemProcess(identity: String): Boolean =
            sourceLabel.startsWith("后台") ||
                "容器骨架" in identity ||
                "容量工作器" in identity ||
                "supervisord" in identity ||
                "/runtime/bin/proot" in identity ||
                "link2symlink" in identity ||
                "/workspace/.kf/system/" in identity ||
                "locale-check" in identity ||
                "mkdir -p /run/" in identity

        private fun TaskManagerProcessItem.isRuntimeScaffold(identity: String): Boolean =
            "容器骨架" in identity ||
                "容量工作器" in identity ||
                "proot-capacity" in identity ||
                "supervisord" in identity ||
                "/runtime/bin/proot" in identity ||
                "link2symlink" in identity ||
                "/workspace/.kf/system/bin/kf-runner" in identity ||
                "locale-check" in identity ||
                "mkdir -p /run/" in identity

        private fun String.compact(maxLength: Int): String {
            val value = trim().replace(Regex("\\s+"), " ")
            return if (value.length <= maxLength) value else value.take(maxLength - 1) + "…"
        }
    }
}
