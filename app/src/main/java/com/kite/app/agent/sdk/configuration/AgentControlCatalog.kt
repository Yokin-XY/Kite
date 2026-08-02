package com.kite.app.agent.sdk.configuration

import com.kite.app.agent.contract.AgentConfigCategory
import com.kite.app.agent.contract.AgentConfigChoice
import com.kite.app.agent.contract.AgentConfigOption
import com.kite.app.agent.contract.AgentModelSource
import com.kite.app.agent.contract.AgentPermissionLevel
import com.kite.app.agent.contract.AgentReasoningSemantics

/** 显示层固定消费的模型、权限和推理强度目录。 */
data class AgentControlCatalog(
    val model: AgentModelControlCatalog? = null,
    val permission: AgentPermissionControlCatalog? = null,
    val reasoning: AgentReasoningControlCatalog? = null,
)

data class AgentModelControlCatalog(
    val configId: String,
    val current: AgentModelSelection,
    val choices: List<AgentModelChoice>,
)

data class AgentModelChoice(
    val selection: AgentModelSelection,
    val displayName: String,
    val description: String? = null,
    val sourceName: String? = null,
)

/** 页面只回传这个稳定选择；如何写入原生配置完全由 Adapter 决定。 */
data class AgentModelSelection(
    val configId: String,
    val sourceId: String,
    val modelId: String,
    val nativeValue: String,
    val source: AgentModelSource,
)

data class AgentPermissionControlCatalog(
    val configId: String,
    val currentProfileId: String,
    val choices: List<AgentPermissionChoice>,
)

data class AgentPermissionChoice(
    val profileId: String,
    val level: AgentPermissionLevel,
    val description: String? = null,
)

sealed interface AgentReasoningControlCatalog {
    val configId: String

    data class Select(
        override val configId: String,
        val currentValue: String,
        val choices: List<AgentReasoningChoice>,
    ) : AgentReasoningControlCatalog

    data class Toggle(
        override val configId: String,
        val enabled: Boolean,
    ) : AgentReasoningControlCatalog
}

data class AgentReasoningChoice(
    val nativeValue: String,
    val semantics: AgentReasoningSemantics,
)

/**
 * 把协议或 Adapter 已经明确标注的真实能力投影成固定组件目录。
 *
 * 这里不根据名称、分组文案或 Agent 产品名猜测来源和语义；缺少显式声明的选项不会进入固定组件。
 */
object AgentControlCatalogProjector {
    fun project(options: List<AgentConfigOption>): AgentControlCatalog = AgentControlCatalog(
        model = options.firstModelCatalog(),
        permission = options.firstPermissionCatalog(),
        reasoning = options.firstReasoningCatalog(),
    )

    private fun List<AgentConfigOption>.firstModelCatalog(): AgentModelControlCatalog? {
        val option = filterIsInstance<AgentConfigOption.Select>()
            .firstOrNull { it.category == AgentConfigCategory.Model }
            ?: return null
        val choices = option.choices.mapNotNull { it.toModelChoice(option.id) }
            .distinctBy { it.selection.nativeValue }
        val current = choices.firstOrNull { it.selection.nativeValue == option.currentValue }?.selection
            ?: return null
        return AgentModelControlCatalog(option.id, current, choices)
    }

    private fun AgentConfigChoice.toModelChoice(configId: String): AgentModelChoice? {
        val source = modelSource ?: return null
        val sourceId = groupId?.takeIf(String::isNotBlank)
            ?: value.substringBefore('/').takeIf { it != value && it.isNotBlank() }
            ?: when (source) {
                AgentModelSource.Free -> FREE_SOURCE_ID
                AgentModelSource.OfficialLogin -> OFFICIAL_SOURCE_ID
                AgentModelSource.UserConfigured -> return null
            }
        val prefix = "$sourceId/"
        val modelId = value.removePrefix(prefix).takeIf(String::isNotBlank) ?: return null
        return AgentModelChoice(
            selection = AgentModelSelection(configId, sourceId, modelId, value, source),
            displayName = name,
            description = description,
            sourceName = groupName,
        )
    }

    private fun List<AgentConfigOption>.firstPermissionCatalog(): AgentPermissionControlCatalog? {
        val option = filterIsInstance<AgentConfigOption.Select>()
            .firstOrNull { it.category == AgentConfigCategory.Permission }
            ?: return null
        val choices = option.choices.mapNotNull { choice ->
            choice.permission?.let { level ->
                AgentPermissionChoice(choice.value, level, choice.description)
            }
        }.distinctBy(AgentPermissionChoice::profileId)
        if (choices.none { it.profileId == option.currentValue }) return null
        return AgentPermissionControlCatalog(option.id, option.currentValue, choices.sortedBy { it.level.order })
    }

    private fun List<AgentConfigOption>.firstReasoningCatalog(): AgentReasoningControlCatalog? {
        val option = firstOrNull { it.category == AgentConfigCategory.ThoughtLevel } ?: return null
        return when (option) {
            is AgentConfigOption.Toggle -> AgentReasoningControlCatalog.Toggle(option.id, option.currentValue)
            is AgentConfigOption.Select -> {
                val choices = option.choices.mapNotNull { choice ->
                    choice.reasoning?.let { AgentReasoningChoice(choice.value, it) }
                }.distinctBy { it.semantics.id }.sortedBy { it.semantics.order }
                if (choices.size < 2 || choices.none { it.nativeValue == option.currentValue }) null
                else AgentReasoningControlCatalog.Select(option.id, option.currentValue, choices)
            }
        }
    }

    private const val FREE_SOURCE_ID = "__kite_free__"
    private const val OFFICIAL_SOURCE_ID = "__kite_official__"
}
