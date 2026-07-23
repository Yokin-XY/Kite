package com.kite.app.feature.runtimemanagement

import com.kite.app.application.runtimemanagement.RuntimeManagedOwnerKind
import com.kite.app.application.runtimemanagement.RuntimeManagedProcess
import com.kite.app.application.runtimemanagement.RuntimeManagedTerminal
import com.kite.app.application.runtimemanagement.RuntimeManagementSnapshot
import com.kite.app.run.CardRunState
import com.kite.app.run.CardRunStatus
import com.kite.app.run.CardRunSurface
import com.kite.app.run.KiteCardRunUiProjector

/** 把事实快照投影成一级作用域和可复用的二级应用组；不读取 Store，也不保存页面状态。 */
internal object RuntimeManagementProjector {
    fun project(
        snapshot: RuntimeManagementSnapshot,
        mutations: Map<String, RuntimeManagementMutation> = emptyMap(),
        text: RuntimeManagementText = RuntimeManagementText.zhCn(),
    ): RuntimeManagementUiState {
        val topology = snapshot.topology
        val processesById = snapshot.processes.associateBy(RuntimeManagedProcess::id)
        val candidateRoots = topology.rootInstanceIds
            .mapNotNull(topology.nodesByInstanceId::get)
            .map { it.run }
            .sortedByDescending(CardRunState::updatedAt)
        val candidateAssignments = candidateRoots.associate { root ->
            root.instanceId to topology.subtree(root.instanceId)
                .flatMap { it.processIds }
                .distinct()
                .mapNotNull(processesById::get)
        }
        val terminalsById = snapshot.terminals.associateBy(RuntimeManagedTerminal::id)
        val roots = candidateRoots.filter { root ->
            val hasLiveTerminal = topology.subtree(root.instanceId)
                .flatMap { it.terminalSessionIds }
                .mapNotNull(terminalsById::get)
                .any(RuntimeManagedTerminal::isLive)
            root.belongsOnManagementPage(
                processes = candidateAssignments[root.instanceId].orEmpty(),
                hasLiveTerminal = hasLiveTerminal,
            )
        }
        val assignments = roots.associate { root ->
            root.instanceId to candidateAssignments[root.instanceId].orEmpty()
        }
        val assignedProcessIds = assignments.values.flatten().mapTo(mutableSetOf(), RuntimeManagedProcess::id)
        val assignedTerminalIds = roots
            .flatMap { root -> topology.subtree(root.instanceId) }
            .flatMap { it.terminalSessionIds }
            .toSet()

        val runs = roots.map { run ->
            val processes = assignments[run.instanceId].orEmpty()
            val title = run.recipeName.ifBlank { run.recipeId.ifBlank { text.cardFallback } }
            val groups = groupsFor(
                processes = processes,
                run = run,
                cardLabel = title,
                keyPrefix = "card:${run.instanceId}",
                mutations = mutations,
                text = text,
            )
            val terminal = run.terminalSessionId?.let(terminalsById::get)
            val icon = snapshot.cardIconsByRecipeId[run.recipeId]
            RuntimeManagementRunUiState(
                instanceId = run.instanceId,
                recipeId = run.recipeId,
                title = title,
                sourceLabel = text.source(run.ownerKind),
                status = run.status,
                statusLabel = text.status(run.status),
                statusTone = KiteCardRunUiProjector.project(run.status).tone,
                createdAt = run.createdAt,
                icon = RuntimeManagementCardIconUiState(
                    type = icon?.type.orEmpty().ifBlank { "builtin" },
                    name = icon?.name.orEmpty().ifBlank { "default" },
                    source = icon?.source.orEmpty(),
                ),
                surfaces = surfacesFor(run, topology.descendants(run.instanceId).map { it.run }, text),
                terminalTitle = terminal?.title,
                processCount = maxOf(
                    terminal?.processCount ?: 0,
                    processes.size,
                ),
                processGroups = groups,
                stopAction = run.stopAction(mutations[run.mutationKey()], text),
            )
        }

        val unassignedProcesses = snapshot.processes.filterNot { it.id in assignedProcessIds }
        val unassignedGroups = groupsFor(
            processes = unassignedProcesses,
            run = null,
            cardLabel = null,
            keyPrefix = "unassigned",
            mutations = mutations,
            text = text,
        ).toMutableList()
        val representedTerminalIds = snapshot.terminals.mapNotNull { terminal ->
            val terminalPids = listOfNotNull(terminal.rootPid, terminal.observedPid).filter { it > 0 }.toSet()
            terminal.id.takeIf { id ->
                unassignedProcesses.any { process ->
                    process.linkedTerminalSessionId == id || process.pid in terminalPids || process.ownerRootPid in terminalPids
                }
            }
        }.toSet()
        snapshot.terminals
            .filter { terminal -> terminal.isLive && terminal.id !in assignedTerminalIds && terminal.id !in representedTerminalIds }
            .sortedBy(RuntimeManagedTerminal::title)
            .forEach { terminal -> unassignedGroups += terminal.toProcessGroup(mutations[terminal.mutationKey()], text) }

        val allGroups = mergeForAll(
            runs.flatMap(RuntimeManagementRunUiState::processGroups) + unassignedGroups,
            mutations,
            text,
        )
        return RuntimeManagementUiState(
            summary = RuntimeManagementSummaryUiState(
                runningCards = roots.count { it.countsAsRunningCard() },
                runningTerminals = snapshot.terminals.count(RuntimeManagedTerminal::isLive),
                runningProcesses = maxOf(snapshot.processes.size, snapshot.observedProcessCount).coerceAtLeast(0),
            ),
            runs = runs,
            allProcessGroups = allGroups,
            unassignedProcessGroups = unassignedGroups,
            refreshedAt = snapshot.refreshedAt,
        )
    }

    private fun groupsFor(
        processes: List<RuntimeManagedProcess>,
        run: CardRunState?,
        cardLabel: String?,
        keyPrefix: String,
        mutations: Map<String, RuntimeManagementMutation>,
        text: RuntimeManagementText,
    ): List<RuntimeManagementProcessGroupUiState> {
        if (processes.isEmpty()) return emptyList()
        val byPid = processes.filter { it.pid > 0 }.associateBy(RuntimeManagedProcess::pid)
        val regular = processes.filterNot(RuntimeManagedProcess::isRuntimeScaffold)
        val roots = regular.groupBy { process ->
            process.workloadScopeId?.takeIf(String::isNotBlank)
                ?: "fallback:${regularRoot(process, byPid).id}"
        }
            .values
            .map { members ->
                val memberIds = members.mapTo(mutableSetOf(), RuntimeManagedProcess::id)
                val root = members.firstOrNull { process ->
                    process.parentPid <= 1 || byPid[process.parentPid]?.let { it.id !in memberIds } != false
                } ?: members.minBy(RuntimeManagedProcess::pid)
                val ordered = treeOrder(members, root)
                val workloadScopeId = members.mapNotNull(RuntimeManagedProcess::workloadScopeId)
                    .distinct()
                    .singleOrNull()
                val groupKey = workloadScopeId
                    ?.let { "$keyPrefix:workload:$it" }
                    ?: "$keyPrefix:process-tree:${root.id}"
                RuntimeManagementProcessGroupUiState(
                    key = groupKey,
                    title = text.processTitle(root.title).ifBlank { text.processFallback },
                    processCount = members.size,
                    processes = ordered.map { (process, depth) ->
                        process.toUiState(
                            run = run,
                            cardLabel = cardLabel,
                            depth = depth,
                            mutation = mutations[process.mutationKey()],
                            text = text,
                        )
                    },
                    workloadScopeId = workloadScopeId,
                    cardLabels = listOfNotNull(cardLabel),
                ).withWorkloadScopeAction(mutations, text)
            }

        val infrastructure = processes.filter(RuntimeManagedProcess::isRuntimeScaffold)
        val infrastructureGroup = infrastructure.takeIf { it.isNotEmpty() }?.let { members ->
            RuntimeManagementProcessGroupUiState(
                key = "$keyPrefix:foundation",
                title = text.runtimeFoundation,
                processCount = members.size,
                processes = forestOrder(members).map { (process, depth) ->
                    process.toUiState(
                        run = run,
                        cardLabel = cardLabel,
                        depth = depth,
                        mutation = mutations[process.mutationKey()],
                        text = text,
                    )
                },
                cardLabels = listOfNotNull(cardLabel),
                isInfrastructure = true,
            )
        }
        return roots.sortedBy { it.title.lowercase() } + listOfNotNull(infrastructureGroup)
    }

    private fun regularRoot(
        process: RuntimeManagedProcess,
        byPid: Map<Int, RuntimeManagedProcess>,
    ): RuntimeManagedProcess {
        var current = process
        val seen = mutableSetOf<String>()
        while (seen.add(current.id)) {
            val parent = byPid[current.parentPid] ?: break
            if (parent.isRuntimeScaffold) break
            current = parent
        }
        return current
    }

    private fun treeOrder(
        processes: List<RuntimeManagedProcess>,
        preferredRoot: RuntimeManagedProcess,
    ): List<Pair<RuntimeManagedProcess, Int>> {
        val ids = processes.mapTo(mutableSetOf(), RuntimeManagedProcess::id)
        val children = processes.groupBy(RuntimeManagedProcess::parentPid)
        val ordered = mutableListOf<Pair<RuntimeManagedProcess, Int>>()
        val seen = mutableSetOf<String>()
        fun visit(process: RuntimeManagedProcess, depth: Int) {
            if (!seen.add(process.id)) return
            ordered += process to depth
            children[process.pid].orEmpty().sortedBy(RuntimeManagedProcess::pid).forEach { visit(it, depth + 1) }
        }
        visit(preferredRoot, 0)
        processes
            .filter { it.id !in seen && (it.parentPid <= 1 || byProcessId(processes, it.parentPid)?.id !in ids) }
            .sortedBy(RuntimeManagedProcess::pid)
            .forEach { visit(it, 0) }
        processes.filter { it.id !in seen }.sortedBy(RuntimeManagedProcess::pid).forEach { visit(it, 0) }
        return ordered
    }

    private fun forestOrder(processes: List<RuntimeManagedProcess>): List<Pair<RuntimeManagedProcess, Int>> {
        if (processes.isEmpty()) return emptyList()
        val byPid = processes.filter { it.pid > 0 }.associateBy(RuntimeManagedProcess::pid)
        val roots = processes.filter { it.parentPid <= 1 || byPid[it.parentPid] == null }.sortedBy(RuntimeManagedProcess::pid)
        val ordered = mutableListOf<Pair<RuntimeManagedProcess, Int>>()
        val seen = mutableSetOf<String>()
        val children = processes.groupBy(RuntimeManagedProcess::parentPid)
        fun visit(process: RuntimeManagedProcess, depth: Int) {
            if (!seen.add(process.id)) return
            ordered += process to depth
            children[process.pid].orEmpty().sortedBy(RuntimeManagedProcess::pid).forEach { visit(it, depth + 1) }
        }
        roots.forEach { visit(it, 0) }
        processes.filter { it.id !in seen }.sortedBy(RuntimeManagedProcess::pid).forEach { visit(it, 0) }
        return ordered
    }

    private fun byProcessId(processes: List<RuntimeManagedProcess>, pid: Int): RuntimeManagedProcess? =
        processes.firstOrNull { it.pid == pid }

    private fun mergeForAll(
        groups: List<RuntimeManagementProcessGroupUiState>,
        mutations: Map<String, RuntimeManagementMutation>,
        text: RuntimeManagementText,
    ): List<RuntimeManagementProcessGroupUiState> =
        groups.groupBy { group -> when {
            group.isInfrastructure -> "foundation"
            !group.workloadScopeId.isNullOrBlank() -> "workload:${group.workloadScopeId}"
            else -> "projection:${group.key}"
        } }
            .values
            .map { matches ->
                val first = matches.first()
                first.copy(
                    key = when {
                        first.isInfrastructure -> "all:foundation"
                        !first.workloadScopeId.isNullOrBlank() -> "all:workload:${first.workloadScopeId}"
                        else -> "all:projection:${first.key}"
                    },
                    processCount = matches.sumOf(RuntimeManagementProcessGroupUiState::processCount),
                    processes = matches.flatMap(RuntimeManagementProcessGroupUiState::processes)
                        .sortedWith(compareBy<RuntimeManagementProcessUiState> { it.cardLabel.orEmpty() }
                            .thenBy(RuntimeManagementProcessUiState::depth)
                            .thenBy(RuntimeManagementProcessUiState::pid)),
                    cardLabels = matches.flatMap(RuntimeManagementProcessGroupUiState::cardLabels).distinct().sorted(),
                ).withWorkloadScopeAction(mutations, text)
            }
            .sortedWith(compareBy<RuntimeManagementProcessGroupUiState> { it.isInfrastructure }.thenBy { it.title.lowercase() })

    private fun RuntimeManagedTerminal.toProcessGroup(
        mutation: RuntimeManagementMutation?,
        text: RuntimeManagementText,
    ): RuntimeManagementProcessGroupUiState {
        val pending = mutation?.phase == RuntimeManagementMutationPhase.Requested ||
            mutation?.phase == RuntimeManagementMutationPhase.AwaitingConfirmation
        val pid = observedPid ?: rootPid ?: 0
        val state = when (mutation?.phase) {
            RuntimeManagementMutationPhase.Requested -> text.requested
            RuntimeManagementMutationPhase.AwaitingConfirmation -> text.awaiting
            RuntimeManagementMutationPhase.Failed -> text.failed
            null -> text.processState(statusLabel)
        }
        val action = RuntimeManagementActionUiState(
            label = if (pending) text.ending else text.endTerminal,
            target = RuntimeManagementActionTarget.EndTerminal(id),
            mutationKey = mutationKey(),
            enabled = !pending,
            danger = true,
            mutationPhase = mutation?.phase,
        )
        val item = RuntimeManagementProcessUiState(
            key = "terminal:$id",
            pid = pid,
            parentPid = 0,
            title = title.ifBlank { text.terminalFallback },
            subtitle = listOf(text.owner(RuntimeManagedOwnerKind.Terminal), state).joinToString(" · "),
            stateLabel = state,
            ownerLabel = text.owner(RuntimeManagedOwnerKind.Terminal),
            purpose = text.terminalProcessCount(processCount),
            commandLine = "",
            stopAction = action,
        )
        return RuntimeManagementProcessGroupUiState(
            key = "unassigned:terminal:$id",
            title = item.title,
            processCount = processCount.coerceAtLeast(1),
            processes = listOf(item),
        )
    }

    private fun RuntimeManagedProcess.toUiState(
        run: CardRunState?,
        cardLabel: String?,
        depth: Int,
        mutation: RuntimeManagementMutation?,
        text: RuntimeManagementText,
    ): RuntimeManagementProcessUiState {
        val ownerLabel = text.owner(ownerKind)
        val state = when (mutation?.phase) {
            RuntimeManagementMutationPhase.Requested -> text.requested
            RuntimeManagementMutationPhase.AwaitingConfirmation -> text.awaiting
            RuntimeManagementMutationPhase.Failed -> text.failed
            null -> text.processState(stateLabel)
        }
        return RuntimeManagementProcessUiState(
            key = id,
            pid = pid,
            parentPid = parentPid,
            title = text.processTitle(title).ifBlank { text.processFallback },
            subtitle = buildList {
                add(state)
                if (!cardLabel.isNullOrBlank()) add(cardLabel)
            }.joinToString(" · "),
            stateLabel = state,
            ownerLabel = ownerLabel,
            purpose = text.processPurpose(purpose.ifBlank { text.processGenericPurpose }),
            commandLine = commandLine,
            cardInstanceId = run?.instanceId,
            cardLabel = cardLabel,
            depth = depth,
            isInfrastructure = isRuntimeScaffold,
            canEndAsWorkload = canEndDirectly && identityVerified && !lifecycleId.isNullOrBlank(),
            processGroupId = processGroupId,
            lifecycleId = lifecycleId,
            kernelState = kernelState,
            identityVerified = identityVerified,
            stopAction = stopAction(run, mutation, text),
        )
    }

    private fun RuntimeManagementProcessGroupUiState.withWorkloadScopeAction(
        mutations: Map<String, RuntimeManagementMutation>,
        text: RuntimeManagementText,
    ): RuntimeManagementProcessGroupUiState {
        val scopeId = workloadScopeId?.takeIf(String::isNotBlank)
        if (isInfrastructure || scopeId == null || processes.isEmpty() || processes.any { !it.canEndAsWorkload }) {
            return copy(stopAction = null)
        }
        val mutationKey = "workload:$scopeId"
        val mutation = mutations[mutationKey]
        val pending = mutation?.phase == RuntimeManagementMutationPhase.Requested ||
            mutation?.phase == RuntimeManagementMutationPhase.AwaitingConfirmation
        return copy(
            stopAction = RuntimeManagementActionUiState(
                label = if (pending) text.ending else text.endProcessGroup,
                target = RuntimeManagementActionTarget.EndWorkloadScope(scopeId),
                mutationKey = mutationKey,
                enabled = !pending,
                danger = true,
                mutationPhase = mutation?.phase,
            ),
        )
    }

    private fun RuntimeManagedProcess.stopAction(
        run: CardRunState?,
        mutation: RuntimeManagementMutation?,
        text: RuntimeManagementText,
    ): RuntimeManagementActionUiState? {
        val pending = mutation?.phase == RuntimeManagementMutationPhase.Requested ||
            mutation?.phase == RuntimeManagementMutationPhase.AwaitingConfirmation
        val target = when {
            run != null && isMainFor(run) -> RuntimeManagementActionTarget.StopRun(run.instanceId)
            isOwnerRoot && !linkedTerminalSessionId.isNullOrBlank() -> RuntimeManagementActionTarget.EndTerminal(linkedTerminalSessionId)
            isOwnerRoot && !linkedRuntimeId.isNullOrBlank() -> RuntimeManagementActionTarget.StopBackgroundRuntime(linkedRuntimeId)
            canEndDirectly -> RuntimeManagementActionTarget.EndProcess(id, pid)
            else -> return null
        }
        return RuntimeManagementActionUiState(
            label = when {
                pending -> text.ending
                target is RuntimeManagementActionTarget.StopRun -> text.stopTask
                target is RuntimeManagementActionTarget.EndTerminal -> text.endTerminal
                target is RuntimeManagementActionTarget.StopBackgroundRuntime -> text.stopTask
                else -> text.endProcess
            },
            target = target,
            mutationKey = mutationKey(),
            enabled = !pending,
            danger = true,
            mutationPhase = mutation?.phase,
        )
    }

    private fun CardRunState.stopAction(
        mutation: RuntimeManagementMutation?,
        text: RuntimeManagementText,
    ): RuntimeManagementActionUiState? {
        val pending = status == CardRunStatus.Stopping ||
            mutation?.phase == RuntimeManagementMutationPhase.Requested ||
            mutation?.phase == RuntimeManagementMutationPhase.AwaitingConfirmation
        if (!pending && !status.canRequestStop()) return null
        return RuntimeManagementActionUiState(
            label = if (pending) text.stopping else text.stop,
            target = RuntimeManagementActionTarget.StopRun(instanceId),
            mutationKey = mutationKey(),
            enabled = !pending,
            danger = true,
            mutationPhase = mutation?.phase,
        )
    }

    private fun surfacesFor(
        root: CardRunState,
        children: List<CardRunState>,
        text: RuntimeManagementText,
    ): List<RuntimeManagementSurfaceUiState> {
        val states = listOf(root) + children.sortedBy(CardRunState::createdAt)
        return buildList {
            states.forEach { state ->
                if (state.hasReportSurface()) add(state.surfaceItem(CardRunSurface.Report, text.surface(CardRunSurface.Report), text.reportCaption, text.open))
                if (!state.terminalSessionId.isNullOrBlank()) add(state.surfaceItem(CardRunSurface.Terminal, text.surface(CardRunSurface.Terminal), text.terminalCaption, text.open))
                if (!state.nextActionUrl.isNullOrBlank() || state.surface == CardRunSurface.Web) {
                    add(state.surfaceItem(CardRunSurface.Web, text.surface(CardRunSurface.Web), state.nextActionUrl.orEmpty().ifBlank { text.waitingUrl }, text.open))
                }
                if (!state.x11Display.isNullOrBlank()) add(state.surfaceItem(CardRunSurface.X11, "X11", "DISPLAY=${state.x11Display}", text.open))
            }
            if (isEmpty()) add(root.surfaceItem(root.surface, text.surface(root.surface), text.status(root.status), text.open))
        }.distinctBy(RuntimeManagementSurfaceUiState::key)
    }

    private fun CardRunState.surfaceItem(
        targetSurface: CardRunSurface,
        title: String,
        caption: String,
        actionLabel: String,
    ): RuntimeManagementSurfaceUiState = RuntimeManagementSurfaceUiState(
        key = "$instanceId:${targetSurface.name}",
        instanceId = instanceId,
        surface = targetSurface,
        title = title,
        caption = caption,
        openAction = RuntimeManagementActionUiState(
            label = actionLabel,
            target = RuntimeManagementActionTarget.OpenSurface(recipeId, instanceId, targetSurface),
            mutationKey = "surface:$instanceId:${targetSurface.name}",
        ),
    )

    private fun RuntimeManagedProcess.isMainFor(run: CardRunState): Boolean {
        val pids = run.boundPids()
        return isOwnerRoot || pid in pids
    }

    private fun CardRunState.hasReportSurface(): Boolean =
        !shellReportText.isNullOrBlank() || !lastMeaningfulOutput.isNullOrBlank() || !lastError.isNullOrBlank() ||
            surface == CardRunSurface.Report || surface == CardRunSurface.Summary

    private fun CardRunState.boundPids(): Set<Int> = listOf(rootPid, pid)
        .mapNotNull { it?.trim()?.toIntOrNull()?.takeIf { value -> value > 0 } }
        .toSet()

    private fun CardRunState.belongsOnManagementPage(
        processes: List<RuntimeManagedProcess>,
        hasLiveTerminal: Boolean,
    ): Boolean = countsAsRunningCard() || processes.isNotEmpty() || hasLiveTerminal

    private fun CardRunState.countsAsRunningCard(): Boolean = status in setOf(
        CardRunStatus.Starting,
        CardRunStatus.Running,
        CardRunStatus.WaitingTerminal,
        CardRunStatus.AlreadyRunning,
        CardRunStatus.Opened,
        CardRunStatus.Stopping,
    )

    private fun CardRunStatus.canRequestStop(): Boolean = this in setOf(
        CardRunStatus.Starting,
        CardRunStatus.Running,
        CardRunStatus.WaitingTerminal,
        CardRunStatus.AlreadyRunning,
        CardRunStatus.Opened,
        CardRunStatus.Failed,
        CardRunStatus.CleanupPending,
        CardRunStatus.BridgeUnavailable,
    )

    private fun CardRunState.mutationKey(): String = "run:$instanceId"
    private fun RuntimeManagedProcess.mutationKey(): String = "process:$id"
    private fun RuntimeManagedTerminal.mutationKey(): String = "terminal:$id"
}
