package com.kite.app.foundation.runtime

/**
 * 从 PRoot 的强生命周期和 Linux 作业边界生成稳定工作负载身份。
 *
 * PRoot 会话根代表启动入口；首个脱离根 PGID/SID 的作业是实际工作负载边界。
 * 后代始终继承最先出现的边界，不能因后续改名、exec 或再次创建进程组而换组。
 */
internal object ProotWorkloadScopeProjector {
    fun project(records: Collection<ProotTraceeRecord>): Map<String, String> =
        projectFacts(records.map { record -> record.toWorkloadFact() })

    /** 破坏性动作从目标会话的活动注册表重新投影，避免被无关旧会话拖成全局 partial。 */
    fun projectRegistry(
        entries: Collection<ProotActiveTraceeEntry>,
        historicalRecords: Collection<ProotTraceeRecord> = emptyList(),
    ): Map<String, String> {
        val facts = linkedMapOf<String, WorkloadProcessFact>()
        historicalRecords.map { record -> record.toWorkloadFact() }.forEach { fact ->
            facts[fact.lifecycleId] = fact
        }
        entries.map { entry -> entry.toWorkloadFact() }.forEach { fact ->
            facts[fact.lifecycleId] = fact
        }
        return projectFacts(facts.values)
    }

    private fun projectFacts(records: Collection<WorkloadProcessFact>): Map<String, String> {
        val projected = mutableMapOf<String, String>()
        records.groupBy(WorkloadProcessFact::sessionKey).values.forEach { sessionRecords ->
            val byLifecycle = sessionRecords.associateBy(WorkloadProcessFact::lifecycleSeq)
            sessionRecords.forEach { record ->
                val path = ancestry(record, byLifecycle)
                val root = path.last()
                val rootToLeaf = path.asReversed()
                val boundary = rootToLeaf.drop(1).firstOrNull { candidate ->
                    candidate.crossesJobBoundary(root)
                }
                val scopeRoot = boundary
                    ?.let { resolveJobLeader(it, sessionRecords) }
                    ?: root
                projected[record.lifecycleId] = workloadScopeId(scopeRoot.lifecycleId)
            }
        }
        return projected
    }

    /** 当会话已经产生真实作业时，根作用域只承担启动/调度，不再冒充一个应用组。 */
    fun launcherLifecycleIds(
        records: Collection<ProotTraceeRecord>,
        scopeIds: Map<String, String> = project(records),
    ): Set<String> = buildSet {
        records.groupBy { record ->
            record.telemetrySessionId
                .takeIf(String::isNotBlank)
                ?: "legacy:${record.prootStartMs}:${record.prootPid}"
        }.values.forEach { sessionRecords ->
            val lifecycles = sessionRecords.mapTo(mutableSetOf(), ProotTraceeRecord::lifecycleSeq)
            val roots = sessionRecords.filter { record ->
                record.parentLifecycleSeq == null || record.parentLifecycleSeq !in lifecycles
            }
            roots.forEach rootLoop@{ root ->
                val rootScope = scopeIds[root.lifecycleId] ?: return@rootLoop
                if (sessionRecords.any { scopeIds[it.lifecycleId] != rootScope }) {
                    sessionRecords
                        .filter { scopeIds[it.lifecycleId] == rootScope }
                        .mapTo(this, ProotTraceeRecord::lifecycleId)
                }
            }
        }
    }

    private fun ancestry(
        leaf: WorkloadProcessFact,
        byLifecycle: Map<Long, WorkloadProcessFact>,
    ): List<WorkloadProcessFact> {
        val path = mutableListOf<WorkloadProcessFact>()
        val seen = mutableSetOf<Long>()
        var current: WorkloadProcessFact? = leaf
        repeat(MAX_ANCESTRY_DEPTH) {
            val node = current ?: return path
            if (!seen.add(node.lifecycleSeq)) return path
            path += node
            current = node.parentLifecycleSeq?.let(byLifecycle::get)
        }
        return path
    }

    private fun WorkloadProcessFact.crossesJobBoundary(root: WorkloadProcessFact): Boolean {
        val pgidChanged = processGroupId.validKernelId() != null &&
            processGroupId.validKernelId() != root.processGroupId.validKernelId()
        val sidChanged = sessionId.validKernelId() != null &&
            sessionId.validKernelId() != root.sessionId.validKernelId()
        return pgidChanged || sidChanged
    }

    /** 同一 pipeline 的兄弟进程共享 PGID；用对应组长的强生命周期统一它们的身份。 */
    private fun resolveJobLeader(
        boundary: WorkloadProcessFact,
        sessionRecords: List<WorkloadProcessFact>,
    ): WorkloadProcessFact {
        val leaderPid = boundary.processGroupId.validKernelId()
            ?: boundary.sessionId.validKernelId()
            ?: return boundary
        return sessionRecords
            .asSequence()
            .filter { candidate ->
                candidate.traceePid == leaderPid &&
                    candidate.lifecycleSeq <= boundary.lifecycleSeq
            }
            .maxByOrNull(WorkloadProcessFact::lifecycleSeq)
            ?: boundary
    }

    private fun Int?.validKernelId(): Int? = this?.takeIf { it > 1 }

    private fun workloadScopeId(rootLifecycleId: String): String = "workload:$rootLifecycleId"

    private fun ProotTraceeRecord.toWorkloadFact(): WorkloadProcessFact = WorkloadProcessFact(
        lifecycleId = lifecycleId,
        sessionKey = telemetrySessionId
            .takeIf(String::isNotBlank)
            ?: "legacy:$prootStartMs:$prootPid",
        lifecycleSeq = lifecycleSeq,
        parentLifecycleSeq = parentLifecycleSeq,
        traceePid = traceePid,
        processGroupId = processGroupId,
        sessionId = sessionId,
    )

    private fun ProotActiveTraceeEntry.toWorkloadFact(): WorkloadProcessFact = WorkloadProcessFact(
        lifecycleId = lifecycleId,
        sessionKey = telemetrySessionId,
        lifecycleSeq = lifecycleSeq,
        parentLifecycleSeq = parentLifecycleSeq,
        traceePid = traceePid,
        processGroupId = processGroupId,
        sessionId = sessionId,
    )

    private data class WorkloadProcessFact(
        val lifecycleId: String,
        val sessionKey: String,
        val lifecycleSeq: Long,
        val parentLifecycleSeq: Long?,
        val traceePid: Int,
        val processGroupId: Int?,
        val sessionId: Int?,
    )

    private const val MAX_ANCESTRY_DEPTH = 128
}
