package com.kite.app.agent.sdk.skill

import com.kite.app.agent.config.AgentSkillSummary
import com.kite.app.agent.sdk.configuration.AgentConfigurationTarget
import java.util.concurrent.ConcurrentHashMap

/** 进程内 Skill 目录缓存；不持久化正文、路径探测结果或用户草稿。 */
data class AgentSkillCatalogSnapshot(
    val skills: List<AgentSkillSummary>,
    val loadedAtMillis: Long,
)

object AgentSkillCatalogCache {
    private val snapshots = ConcurrentHashMap<AgentConfigurationTarget, AgentSkillCatalogSnapshot>()

    fun snapshot(target: AgentConfigurationTarget): AgentSkillCatalogSnapshot? = snapshots[target]

    fun update(
        target: AgentConfigurationTarget,
        skills: List<AgentSkillSummary>,
    ): AgentSkillCatalogSnapshot = AgentSkillCatalogSnapshot(
        skills = skills.distinctBy(AgentSkillSummary::id).sortedBy(AgentSkillSummary::displayName),
        loadedAtMillis = System.currentTimeMillis(),
    ).also { snapshots[target] = it }

    fun invalidate(target: AgentConfigurationTarget) {
        snapshots.remove(target)
    }
}
