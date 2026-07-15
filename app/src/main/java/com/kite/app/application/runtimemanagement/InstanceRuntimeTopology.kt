package com.kite.app.application.runtimemanagement

import com.kite.app.run.CardRunState

data class RuntimeInstanceIdentity(
    val instanceId: String,
    val generation: Long
)

data class RuntimeInstanceNode(
    val identity: RuntimeInstanceIdentity,
    val run: CardRunState,
    val parentInstanceId: String? = null,
    val childInstanceIds: List<String> = emptyList(),
    val ownerIds: List<String> = emptyList(),
    val terminalSessionIds: List<String> = emptyList(),
    val processIds: List<String> = emptyList()
)

data class InstanceRuntimeTopology(
    val nodesByInstanceId: Map<String, RuntimeInstanceNode> = emptyMap(),
    val rootInstanceIds: List<String> = emptyList(),
    val unassignedProcessIds: List<String> = emptyList(),
    val ambiguousProcessIds: List<String> = emptyList()
) {
    fun node(instanceId: String): RuntimeInstanceNode? = nodesByInstanceId[instanceId]

    fun subtree(instanceId: String): List<RuntimeInstanceNode> {
        val ordered = mutableListOf<RuntimeInstanceNode>()
        val pending = ArrayDeque<String>().apply { add(instanceId) }
        val seen = mutableSetOf<String>()
        while (pending.isNotEmpty()) {
            val currentId = pending.removeFirst()
            if (!seen.add(currentId)) continue
            val current = nodesByInstanceId[currentId] ?: continue
            ordered += current
            current.childInstanceIds.forEach(pending::addLast)
        }
        return ordered
    }

    fun descendants(instanceId: String): List<RuntimeInstanceNode> =
        subtree(instanceId).drop(1)

    fun ownerIdsForSubtree(instanceId: String): List<String> = subtree(instanceId)
        .flatMap(RuntimeInstanceNode::ownerIds)
        .distinct()
}

/** 从事实字段建立拓扑；没有猜测分数，歧义关系保持未归属。 */
object InstanceRuntimeTopologyBuilder {
    fun build(
        runs: List<CardRunState>,
        terminals: List<RuntimeManagedTerminal>,
        processes: List<RuntimeManagedProcess>
    ): InstanceRuntimeTopology {
        val runsById = runs.associateBy(CardRunState::instanceId)
        val childrenByParent = runs
            .mapNotNull { run -> run.parentInstanceId?.takeIf(runsById::containsKey)?.let { it to run.instanceId } }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, ids) -> ids.distinct().sorted() }
        val ownersByRun = runs.associate { run ->
            run.instanceId to (
                run.ownedRuntimeOwnerIds +
                    listOfNotNull(run.runtimeRootOwnerId, run.runtimeOwnerId)
                )
                .map(String::trim)
                .filter(String::isNotBlank)
                .distinct()
        }
        val terminalIds = terminals.mapTo(mutableSetOf(), RuntimeManagedTerminal::id)
        val terminalsByRun = runs.associate { run ->
            run.instanceId to listOfNotNull(run.terminalSessionId?.takeIf { it in terminalIds }).distinct()
        }
        val ownerToRuns = reverseUniqueIndex(ownersByRun)
        val terminalToRuns = reverseUniqueIndex(terminalsByRun)
        val pidToRuns = reverseUniqueIntIndex(
            runs.associate { run -> run.instanceId to run.boundPids() }
        )

        val assignments = linkedMapOf<String, String>()
        val ambiguous = linkedSetOf<String>()
        processes.forEach { process ->
            val candidates = linkedSetOf<String>()
            process.ownerId?.takeIf(String::isNotBlank)?.let { candidates += ownerToRuns[it].orEmpty() }
            process.linkedTerminalSessionId
                ?.takeIf(String::isNotBlank)
                ?.let { candidates += terminalToRuns[it].orEmpty() }
            if (process.pid > 1) candidates += pidToRuns[process.pid].orEmpty()
            if (process.ownerRootPid != null && process.ownerRootPid > 1) {
                candidates += pidToRuns[process.ownerRootPid].orEmpty()
            }
            when (candidates.size) {
                1 -> assignments[process.id] = candidates.single()
                in 2..Int.MAX_VALUE -> ambiguous += process.id
            }
        }

        val uniqueProcessByPid = processes
            .groupBy(RuntimeManagedProcess::pid)
            .filterValues { it.size == 1 }
            .mapValues { (_, values) -> values.single() }
        var changed: Boolean
        do {
            changed = false
            processes.forEach { process ->
                if (process.id in assignments || process.id in ambiguous || process.parentPid <= 1) return@forEach
                val parent = uniqueProcessByPid[process.parentPid] ?: return@forEach
                val inherited = assignments[parent.id] ?: return@forEach
                assignments[process.id] = inherited
                changed = true
            }
        } while (changed)

        val processIdsByRun = assignments.entries
            .groupBy({ it.value }, { it.key })
            .mapValues { (_, ids) -> ids.distinct() }
        val nodes = runs.associate { run ->
            run.instanceId to RuntimeInstanceNode(
                identity = RuntimeInstanceIdentity(run.instanceId, run.createdAt),
                run = run,
                parentInstanceId = run.parentInstanceId?.takeIf(runsById::containsKey),
                childInstanceIds = childrenByParent[run.instanceId].orEmpty(),
                ownerIds = ownersByRun[run.instanceId].orEmpty(),
                terminalSessionIds = terminalsByRun[run.instanceId].orEmpty(),
                processIds = processIdsByRun[run.instanceId].orEmpty()
            )
        }
        return InstanceRuntimeTopology(
            nodesByInstanceId = nodes,
            rootInstanceIds = nodes.values
                .filter { it.parentInstanceId == null }
                .sortedByDescending { it.run.updatedAt }
                .map { it.identity.instanceId },
            unassignedProcessIds = processes
                .map(RuntimeManagedProcess::id)
                .filterNot { it in assignments || it in ambiguous },
            ambiguousProcessIds = ambiguous.toList()
        )
    }

    private fun reverseUniqueIndex(valuesByRun: Map<String, List<String>>): Map<String, Set<String>> =
        buildMap {
            valuesByRun.forEach { (runId, values) ->
                values.forEach { value -> put(value, get(value).orEmpty() + runId) }
            }
        }

    private fun reverseUniqueIntIndex(valuesByRun: Map<String, Set<Int>>): Map<Int, Set<String>> =
        buildMap {
            valuesByRun.forEach { (runId, values) ->
                values.forEach { value -> put(value, get(value).orEmpty() + runId) }
            }
        }

    private fun CardRunState.boundPids(): Set<Int> = listOf(pid, rootPid)
        .mapNotNull { it?.trim()?.toIntOrNull()?.takeIf { value -> value > 1 } }
        .toSet()
}
