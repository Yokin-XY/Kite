package com.kite.app.agent.config

import com.kite.app.agent.contract.AgentConfigCategory
import com.kite.app.agent.contract.AgentConfigChoice
import com.kite.app.agent.contract.AgentConfigOption
import com.kite.app.agent.contract.AgentReasoningLevel
import com.kite.app.agent.contract.AgentReasoningMode
import com.kite.app.agent.contract.AgentReasoningSemantics

/** 一个 Agent 原生推理选项到 Kite 稳定语义的显式映射。 */
data class AgentReasoningNativeMapping(
    val value: String,
    val semantics: AgentReasoningSemantics,
    /** 只在同一原生 value 会因 Provider profile 改变语义时使用。 */
    val nativeLabel: String? = null,
)

/**
 * 适配器声明它能够识别的推理语义；实际可见子集仍完全取自当前会话公布的选项。
 *
 * 未映射的值不会进入 Kite 的统一推理入口。`ultra`/`ultracode` 故意不在这里，
 * 因为它们可能同时改变子 Agent 编排或工作流，不是纯推理强度。
 */
class AgentReasoningControl(
    mappings: List<AgentReasoningNativeMapping>,
) {
    private val mappings = mappings.toList()

    init {
        require(this.mappings.isNotEmpty()) { "推理能力映射不能为空" }
        require(this.mappings.all { it.value.isNotBlank() }) { "原生推理值不能为空" }
        require(this.mappings.map { it.key() }.distinct().size == this.mappings.size) {
            "同一原生推理值与标签不能重复映射"
        }
    }

    fun normalize(options: List<AgentConfigOption>): List<AgentConfigOption> =
        options.mapNotNull(::normalize)

    fun normalize(option: AgentConfigOption): AgentConfigOption? {
        if (option.category != AgentConfigCategory.ThoughtLevel) return option
        return when (option) {
            is AgentConfigOption.Toggle -> option.copy(
                name = "推理强度",
                description = option.description
                    ?: "当前模型只提供推理开关，具体强度由模型决定",
            )
            is AgentConfigOption.Select -> normalizeSelect(option)
        }
    }

    private fun normalizeSelect(option: AgentConfigOption.Select): AgentConfigOption.Select? {
        val projected = option.choices.mapNotNull { choice ->
            val semantics = resolve(choice) ?: return@mapNotNull null
            choice.copy(
                name = semantics.displayName,
                description = semantics.description,
                reasoning = semantics,
            )
        }
        val deduplicated = projected
            .groupBy { requireNotNull(it.reasoning).id }
            .values
            .map { sameSemantic ->
                sameSemantic.firstOrNull { it.value == option.currentValue } ?: sameSemantic.first()
            }
            .sortedBy { requireNotNull(it.reasoning).order }
        if (deduplicated.size < 2 || deduplicated.none { it.value == option.currentValue }) return null
        return option.copy(
            name = "推理强度",
            description = option.description ?: "只显示当前工具、供应商和模型真实支持的选项",
            choices = deduplicated,
        )
    }

    private fun resolve(choice: AgentConfigChoice): AgentReasoningSemantics? {
        val value = choice.value.normalizedKey()
        val label = choice.name.normalizedKey()
        return mappings.firstOrNull { mapping ->
            mapping.value.normalizedKey() == value &&
                mapping.nativeLabel?.normalizedKey() == label
        }?.semantics ?: mappings.firstOrNull { mapping ->
            mapping.nativeLabel == null && mapping.value.normalizedKey() == value
        }?.semantics
    }

    private fun AgentReasoningNativeMapping.key(): Pair<String, String?> =
        value.normalizedKey() to nativeLabel?.normalizedKey()

    private fun String.normalizedKey(): String = trim().lowercase()
}

/** 五个正式 Agent 的映射词表；它们不会替 Agent 补造当前模型未公布的选项。 */
object AgentReasoningControls {
    val OpenCode = AgentReasoningControl(levelMappings())

    val OpenClaw = AgentReasoningControl(
        levelMappings() + listOf(
            AgentReasoningNativeMapping("adaptive", AgentReasoningMode.Adaptive),
            AgentReasoningNativeMapping("on", AgentReasoningMode.Enabled),
            AgentReasoningNativeMapping("enabled", AgentReasoningMode.Enabled),
            // OpenClaw 的二值 Provider profile 保留 low 作为 ID，并把显示标签声明为 on。
            AgentReasoningNativeMapping("low", AgentReasoningMode.Enabled, nativeLabel = "on"),
        )
    )

    val Hermes = AgentReasoningControl(levelMappings())

    val Codex = AgentReasoningControl(levelMappings())

    val ClaudeCode = AgentReasoningControl(
        levelMappings(
            levels = setOf(
                AgentReasoningLevel.Off,
                AgentReasoningLevel.Low,
                AgentReasoningLevel.Medium,
                AgentReasoningLevel.High,
                AgentReasoningLevel.ExtraHigh,
                AgentReasoningLevel.Maximum,
            )
        ) + listOf(
            AgentReasoningNativeMapping("default", AgentReasoningMode.Inherit),
            AgentReasoningNativeMapping("auto", AgentReasoningMode.Inherit),
        )
    )

    private fun levelMappings(
        levels: Set<AgentReasoningLevel> = AgentReasoningLevel.entries.toSet(),
    ): List<AgentReasoningNativeMapping> = buildList {
        levels.forEach { level -> add(AgentReasoningNativeMapping(level.id, level)) }
        if (AgentReasoningLevel.Off in levels) {
            add(AgentReasoningNativeMapping("none", AgentReasoningLevel.Off))
        }
        if (AgentReasoningLevel.ExtraHigh in levels) {
            add(AgentReasoningNativeMapping("x-high", AgentReasoningLevel.ExtraHigh))
            add(AgentReasoningNativeMapping("x_high", AgentReasoningLevel.ExtraHigh))
            add(AgentReasoningNativeMapping("extra-high", AgentReasoningLevel.ExtraHigh))
        }
    }
}

/** 先执行 Agent 自己的结构补充，再应用显式推理能力词表。 */
fun AgentConfigAdapter.normalizePublishedSessionConfiguration(
    options: List<AgentConfigOption>,
): List<AgentConfigOption> {
    val normalized = normalizeSessionConfiguration(options)
    return reasoningControl()?.normalize(normalized)
        ?: normalized.filterNot { it.category == AgentConfigCategory.ThoughtLevel }
}
