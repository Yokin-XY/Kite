package com.kite.app.feature.runsurface

import com.kite.app.agent.contract.AgentConfigCategory
import com.kite.app.agent.contract.AgentConfigOption

/**
 * 合并持久配置与 Agent 运行时能力目录。
 *
 * 运行时目录拥有它明确解析出的控制类别；模型例外地以 Kite 的持久供应商目录为事实源，
 * 避免 Agent 只回报当前模型时把同一供应商的其他可选模型从界面中压缩掉。
 */
internal object AgentDraftSessionConfigurationPolicy {
    fun merge(
        storedControls: List<AgentConfigOption>,
        runtimeOptions: List<AgentConfigOption>,
        runtimeResolvedCategories: Set<AgentConfigCategory>,
        persistentModel: AgentConfigOption.Select?,
    ): List<AgentConfigOption> {
        val runtimeCategories = runtimeOptions
            .mapNotNullTo(hashSetOf(), AgentConfigOption::category)
            .apply { addAll(runtimeResolvedCategories) }
        val controls = storedControls.filterNot { it.category in runtimeCategories } + runtimeOptions
        return if (persistentModel == null) {
            controls
        } else {
            listOf(persistentModel) + controls.filterNot { it.category == AgentConfigCategory.Model }
        }
    }
}
