package com.kite.app.feature.runtimemanagement

import com.kite.app.application.runtimemanagement.RuntimeManagedOwnerKind
import com.kite.app.application.runtimemanagement.RuntimeManagedProcess
import com.kite.app.application.runtimemanagement.RuntimeManagedTerminal
import com.kite.app.application.runtimemanagement.RuntimeManagementSnapshot
import com.kite.app.run.CardRunState
import com.kite.app.run.CardRunStatus
import com.kite.app.run.CardRunSurface
import com.kite.app.run.KiteCardRunUiProjector

/** 把三个事实源合成一份页面状态；不读取 Store，不执行动作，也不保存页面展开状态。 */
internal object RuntimeManagementProjector {
    fun project(
        snapshot: RuntimeManagementSnapshot,
        mutations: Map<String, RuntimeManagementMutation> = emptyMap()
    ): RuntimeManagementUiState {
        val roots = snapshot.runs
            .filter { it.parentInstanceId.isNullOrBlank() && it.belongsOnManagementPage() }
            .sortedByDescending(CardRunState::updatedAt)
        val assignments = assignProcesses(roots, snapshot.processes)
        val assignedProcessIds = assignments.values.flatten().mapTo(mutableSetOf(), RuntimeManagedProcess::id)
        val terminalsById = snapshot.terminals.associateBy(RuntimeManagedTerminal::id)
        val childrenByParent = snapshot.runs
            .filter { !it.parentInstanceId.isNullOrBlank() }
            .groupBy { it.parentInstanceId.orEmpty() }
        val assignedTerminalIds = snapshot.runs
            .mapNotNull(CardRunState::terminalSessionId)
            .filter(String::isNotBlank)
            .toSet()

        val runs = roots.map { run ->
            val processes = assignments[run.instanceId].orEmpty()
            val main = processes.firstOrNull { it.isMainFor(run) }
                ?: processes.firstOrNull { it.isOwnerRoot }
            val processStates = processes.map { process ->
                process.toUiState(
                    run = run,
                    isMain = process.id == main?.id,
                    mutation = mutations[process.mutationKey()]
                )
            }
            val terminal = run.terminalSessionId?.let(terminalsById::get)
            RuntimeManagementRunUiState(
                instanceId = run.instanceId,
                recipeId = run.recipeId,
                title = run.recipeName.ifBlank { run.recipeId.ifBlank { "Kite 卡片" } },
                sourceLabel = run.ownerKind.sourceLabel(),
                status = run.status,
                statusLabel = run.status.label,
                statusTone = KiteCardRunUiProjector.project(run.status).tone,
                createdAt = run.createdAt,
                surfaces = surfacesFor(run, childrenByParent[run.instanceId].orEmpty()),
                terminalTitle = terminal?.title,
                processCount = maxOf(terminal?.processCount ?: 0, processes.size, if (run.boundPids().isEmpty()) 0 else 1),
                mainProcess = processStates.firstOrNull(RuntimeManagementProcessUiState::isMain),
                childProcesses = processStates.filterNot(RuntimeManagementProcessUiState::isMain),
                stopAction = run.stopAction(mutations[run.mutationKey()])
            )
        }

        val otherProcesses = snapshot.processes.filterNot { it.id in assignedProcessIds }
        val sections = listOf(
            RuntimeManagedOwnerKind.BackgroundRuntime to "后台服务",
            RuntimeManagedOwnerKind.Resource to "资源任务",
            RuntimeManagedOwnerKind.Terminal to "终端进程",
            RuntimeManagedOwnerKind.System to "系统",
            RuntimeManagedOwnerKind.Unattributed to "其他"
        ).mapNotNull { (kind, label) ->
            val items = otherProcesses
                .filter { process -> process.ownerKind == kind }
                .sortedBy(RuntimeManagedProcess::pid)
                .map { process ->
                    process.toUiState(
                        run = null,
                        isMain = false,
                        mutation = mutations[process.mutationKey()]
                    )
                }
            if (items.isEmpty()) null else RuntimeManagementProcessSectionUiState(
                key = kind.name,
                title = label,
                processes = items
            )
        }

        return RuntimeManagementUiState(
            summary = RuntimeManagementSummaryUiState(
                runningCards = roots.count { it.countsAsRunningCard() },
                runningTerminals = snapshot.terminals.count(RuntimeManagedTerminal::isLive),
                runningProcesses = maxOf(snapshot.processes.size, snapshot.observedProcessCount).coerceAtLeast(0)
            ),
            runs = runs,
            standaloneTerminals = snapshot.terminals
                .filter { terminal -> terminal.isLive && terminal.id !in assignedTerminalIds }
                .sortedBy(RuntimeManagedTerminal::title)
                .map { terminal -> terminal.toStandaloneUiState(mutations[terminal.mutationKey()]) },
            otherProcessSections = sections,
            refreshedAt = snapshot.refreshedAt
        )
    }

    private fun RuntimeManagedTerminal.toStandaloneUiState(
        mutation: RuntimeManagementMutation?
    ): RuntimeManagementTerminalUiState {
        val pending = mutation?.phase == RuntimeManagementMutationPhase.Requested ||
            mutation?.phase == RuntimeManagementMutationPhase.AwaitingConfirmation
        return RuntimeManagementTerminalUiState(
            key = id,
            title = title.ifBlank { "终端" },
            subtitle = buildList {
                add(
                    when (mutation?.phase) {
                        RuntimeManagementMutationPhase.Requested -> "请求中"
                        RuntimeManagementMutationPhase.AwaitingConfirmation -> "待确认"
                        RuntimeManagementMutationPhase.Failed -> "失败"
                        null -> statusLabel
                    }
                )
                if (processCount > 0) add("进程 $processCount")
                (observedPid ?: rootPid)?.takeIf { it > 0 }?.let { add("PID $it") }
            }.filter(String::isNotBlank).joinToString(" · "),
            processCount = processCount,
            endAction = RuntimeManagementActionUiState(
                label = if (pending) "结束中" else "结束终端",
                target = RuntimeManagementActionTarget.EndTerminal(id),
                mutationKey = mutationKey(),
                enabled = !pending,
                danger = true,
                mutationPhase = mutation?.phase
            )
        )
    }

    private fun assignProcesses(
        runs: List<CardRunState>,
        processes: List<RuntimeManagedProcess>
    ): Map<String, List<RuntimeManagedProcess>> {
        val assignments = linkedMapOf<String, MutableList<RuntimeManagedProcess>>()
        processes.forEach { process ->
            val owner = runs
                .map { run -> run to process.matchScore(run) }
                .filter { it.second > 0 }
                .maxWithOrNull(compareBy<Pair<CardRunState, Int>> { it.second }.thenBy { it.first.updatedAt })
                ?.first
                ?: return@forEach
            assignments.getOrPut(owner.instanceId) { mutableListOf() }.add(process)
        }
        return assignments.mapValues { (_, items) ->
            items.sortedWith(compareByDescending<RuntimeManagedProcess> { it.isOwnerRoot }.thenBy { it.parentPid }.thenBy { it.pid })
        }
    }

    private fun RuntimeManagedProcess.matchScore(run: CardRunState): Int {
        if (ownerId == run.expectedOwnerId()) return 50
        if (unitId == "card:${run.instanceId}") return 40
        if (!run.terminalSessionId.isNullOrBlank() && linkedTerminalSessionId == run.terminalSessionId) return 30
        val pids = run.boundPids()
        if (pid in pids || parentPid in pids || ownerRootPid in pids) return 20
        return 0
    }

    private fun RuntimeManagedProcess.isMainFor(run: CardRunState): Boolean {
        val pids = run.boundPids()
        return isOwnerRoot || pid in pids || ownerRootPid in pids
    }

    private fun RuntimeManagedProcess.toUiState(
        run: CardRunState?,
        isMain: Boolean,
        mutation: RuntimeManagementMutation?
    ): RuntimeManagementProcessUiState {
        val ownerLabel = ownerKind.ownerLabel()
        val state = mutation?.phase?.let { phase ->
            when (phase) {
                RuntimeManagementMutationPhase.Requested -> "请求中"
                RuntimeManagementMutationPhase.AwaitingConfirmation -> "待确认"
                RuntimeManagementMutationPhase.Failed -> "失败"
            }
        } ?: stateLabel
        return RuntimeManagementProcessUiState(
            key = id,
            pid = pid,
            parentPid = parentPid,
            title = buildList {
                if (pid > 0) add("PID $pid")
                title.takeIf(String::isNotBlank)?.let(::add)
            }.joinToString(" · ").ifBlank { "进程" },
            subtitle = listOf(ownerLabel, state).filter(String::isNotBlank).joinToString(" · "),
            ownerLabel = ownerLabel,
            purpose = purpose.ifBlank { "卡片或用户启动的普通进程" },
            isMain = isMain,
            stopAction = stopAction(run, isMain, mutation)
        )
    }

    private fun RuntimeManagedProcess.stopAction(
        run: CardRunState?,
        isMain: Boolean,
        mutation: RuntimeManagementMutation?
    ): RuntimeManagementActionUiState? {
        val pending = mutation?.phase == RuntimeManagementMutationPhase.Requested ||
            mutation?.phase == RuntimeManagementMutationPhase.AwaitingConfirmation
        val target = when {
            isMain && run != null -> RuntimeManagementActionTarget.StopRun(run.instanceId)
            isOwnerRoot && !linkedTerminalSessionId.isNullOrBlank() ->
                RuntimeManagementActionTarget.EndTerminal(linkedTerminalSessionId)
            isOwnerRoot && !linkedRuntimeId.isNullOrBlank() ->
                RuntimeManagementActionTarget.StopBackgroundRuntime(linkedRuntimeId)
            canEndDirectly -> RuntimeManagementActionTarget.EndProcess(id, pid)
            else -> return null
        }
        return RuntimeManagementActionUiState(
            label = when {
                pending -> "结束中"
                target is RuntimeManagementActionTarget.StopRun -> "停止任务"
                target is RuntimeManagementActionTarget.EndTerminal -> "结束终端"
                target is RuntimeManagementActionTarget.StopBackgroundRuntime -> "停止任务"
                else -> "结束进程"
            },
            target = target,
            mutationKey = mutationKey(),
            enabled = !pending,
            danger = true,
            mutationPhase = mutation?.phase
        )
    }

    private fun CardRunState.stopAction(mutation: RuntimeManagementMutation?): RuntimeManagementActionUiState? {
        val pending = status == CardRunStatus.Stopping ||
            mutation?.phase == RuntimeManagementMutationPhase.Requested ||
            mutation?.phase == RuntimeManagementMutationPhase.AwaitingConfirmation
        if (!pending && !status.canRequestStop()) return null
        return RuntimeManagementActionUiState(
            label = if (pending) "停止中" else "停止",
            target = RuntimeManagementActionTarget.StopRun(instanceId),
            mutationKey = mutationKey(),
            enabled = !pending,
            danger = true,
            mutationPhase = mutation?.phase
        )
    }

    private fun surfacesFor(root: CardRunState, children: List<CardRunState>): List<RuntimeManagementSurfaceUiState> {
        val states = listOf(root) + children.sortedBy(CardRunState::createdAt)
        return buildList {
            states.forEach { state ->
                if (state.hasReportSurface()) add(state.surfaceItem(CardRunSurface.Report, "SH 报告", "执行输出"))
                if (!state.terminalSessionId.isNullOrBlank()) add(state.surfaceItem(CardRunSurface.Terminal, "终端", "终端窗口"))
                if (!state.nextActionUrl.isNullOrBlank() || state.surface == CardRunSurface.Web) {
                    add(state.surfaceItem(CardRunSurface.Web, "网页", state.nextActionUrl.orEmpty().ifBlank { "等待网址" }))
                }
                if (!state.x11Display.isNullOrBlank()) {
                    add(state.surfaceItem(CardRunSurface.X11, "X11", "DISPLAY=${state.x11Display}"))
                }
            }
            if (isEmpty()) {
                add(root.surfaceItem(root.surface, root.surface.label, root.status.label))
            }
        }.distinctBy(RuntimeManagementSurfaceUiState::key)
    }

    private fun CardRunState.surfaceItem(
        targetSurface: CardRunSurface,
        title: String,
        caption: String
    ): RuntimeManagementSurfaceUiState = RuntimeManagementSurfaceUiState(
        key = "$instanceId:${targetSurface.name}",
        instanceId = instanceId,
        surface = targetSurface,
        title = title,
        caption = caption,
        openAction = RuntimeManagementActionUiState(
            label = "打开",
            target = RuntimeManagementActionTarget.OpenSurface(recipeId, instanceId, targetSurface),
            mutationKey = "surface:$instanceId:${targetSurface.name}"
        )
    )

    private fun CardRunState.hasReportSurface(): Boolean =
        !shellReportText.isNullOrBlank() ||
            !lastMeaningfulOutput.isNullOrBlank() ||
            !lastError.isNullOrBlank() ||
            surface == CardRunSurface.Report ||
            surface == CardRunSurface.Summary

    private fun CardRunState.expectedOwnerId(): String = when (ownerKind) {
        CardRunState.OWNER_KIND_RESOURCE -> "resource:${resourceId().orEmpty().ifBlank { instanceId }}"
        CardRunState.OWNER_KIND_TERMINAL -> "terminal:${terminalSessionId ?: runId ?: instanceId}"
        else -> "card:$instanceId"
    }

    private fun CardRunState.resourceId(): String? = when {
        recipeId.startsWith("resource-") -> recipeId
            .removePrefix("resource-")
            .removeSuffix("-install")
            .removeSuffix("-uninstall")
            .takeIf(String::isNotBlank)
        else -> stepId?.takeIf(String::isNotBlank)
    }

    private fun CardRunState.boundPids(): Set<Int> = listOf(rootPid, pid)
        .mapNotNull { it?.trim()?.toIntOrNull()?.takeIf { value -> value > 0 } }
        .toSet()

    private fun CardRunState.belongsOnManagementPage(): Boolean =
        countsAsRunningCard() || (hasRunBinding() && status in setOf(CardRunStatus.Failed, CardRunStatus.BridgeUnavailable))

    private fun CardRunState.countsAsRunningCard(): Boolean = status in setOf(
        CardRunStatus.Starting,
        CardRunStatus.Running,
        CardRunStatus.WaitingTerminal,
        CardRunStatus.AlreadyRunning,
        CardRunStatus.Opened,
        CardRunStatus.Stopping
    )

    private fun CardRunStatus.canRequestStop(): Boolean = this in setOf(
        CardRunStatus.Starting,
        CardRunStatus.Running,
        CardRunStatus.WaitingTerminal,
        CardRunStatus.AlreadyRunning,
        CardRunStatus.Opened,
        CardRunStatus.Failed,
        CardRunStatus.BridgeUnavailable
    )

    private fun String.sourceLabel(): String = when (this) {
        CardRunState.OWNER_KIND_RESOURCE -> "资源"
        CardRunState.OWNER_KIND_INSTALL_WIZARD -> "安装"
        CardRunState.OWNER_KIND_TERMINAL -> "终端"
        CardRunState.OWNER_KIND_WEB -> "网页"
        else -> "首页"
    }

    private fun RuntimeManagedOwnerKind.ownerLabel(): String = when (this) {
        RuntimeManagedOwnerKind.Card -> "卡片"
        RuntimeManagedOwnerKind.Resource -> "资源"
        RuntimeManagedOwnerKind.Terminal -> "卡片终端"
        RuntimeManagedOwnerKind.BackgroundRuntime,
        RuntimeManagedOwnerKind.System -> "系统"
        RuntimeManagedOwnerKind.Unattributed -> "未关联卡片"
    }

    private fun CardRunState.mutationKey(): String = "run:$instanceId"

    private fun RuntimeManagedProcess.mutationKey(): String = "process:$id"

    private fun RuntimeManagedTerminal.mutationKey(): String = "terminal:$id"
}
