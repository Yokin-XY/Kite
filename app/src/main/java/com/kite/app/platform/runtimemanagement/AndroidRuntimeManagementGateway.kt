package com.kite.app.platform.runtimemanagement

import android.content.Context
import com.kite.app.application.runtimemanagement.RuntimeManagedOwnerKind
import com.kite.app.application.runtimemanagement.RuntimeManagedProcess
import com.kite.app.application.runtimemanagement.RuntimeManagedTerminal
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
internal class AndroidRuntimeManagementGateway(context: Context) : RuntimeManagementGateway {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val snapshots: StateFlow<RuntimeManagementSnapshot> = combine(
        CardRunStore.runs,
        TerminalSessionStore.snapshot,
        TaskManagerStore.snapshot,
        RuntimeHealthStore.snapshot
    ) { runs, terminals, tasks, health ->
        mapSnapshot(runs, terminals, tasks, health)
    }.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = currentSnapshot()
    )

    override fun currentSnapshot(): RuntimeManagementSnapshot = mapSnapshot(
        runs = CardRunStore.runs.value,
        terminals = TerminalSessionStore.snapshot.value,
        tasks = TaskManagerStore.snapshot.value,
        health = RuntimeHealthStore.snapshot.value
    )

    override fun refresh(force: Boolean) {
        TerminalSessionStore.refresh(appContext, force = force)
        TaskManagerStore.refresh(appContext, force = force)
    }

    companion object {
        internal fun mapSnapshot(
            runs: List<CardRunState>,
            terminals: TerminalSessionsSnapshot,
            tasks: TaskManagerSnapshot,
            health: RuntimeHealthSnapshot
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
                observedProcessCount = observedProcessCount,
                refreshedAt = maxOf(tasks.refreshedAt, terminals.refreshedAt, health.reconciledAt)
            )
        }

        internal fun TaskManagerProcessItem.toRuntimeManagedProcess(): RuntimeManagedProcess {
            val identity = identityText()
            return RuntimeManagedProcess(
                id = id,
                pid = pid,
                parentPid = parentPid,
                title = processName(identity),
                stateLabel = stateLabel,
                commandLine = commandLine,
                purpose = processPurpose(identity),
                ownerKind = ownerKind(identity),
                ownerId = runtimeOwnerId,
                unitId = runtimeUnitId,
                ownerRootPid = runtimeRootPid,
                linkedTerminalSessionId = linkedTerminalSessionId,
                linkedRuntimeId = linkedRuntimeId,
                isOwnerRoot = id.startsWith("root-"),
                canEndDirectly = TaskManagerAction.END_PROCESS in availableActions
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

        private fun TaskManagerProcessItem.processName(identity: String): String = when {
            "supervisord" in identity -> "容器守护进程"
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

        private fun String.compact(maxLength: Int): String {
            val value = trim().replace(Regex("\\s+"), " ")
            return if (value.length <= maxLength) value else value.take(maxLength - 1) + "…"
        }
    }
}
