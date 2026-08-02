package com.kite.app.agent.sdk.skill

import com.kite.app.agent.contract.AgentContent
import java.net.URLDecoder
import java.net.URLEncoder

/** 会话草稿里选中的稳定 Skill 引用；不包含本机路径和正文。 */
data class AgentSelectedSkill(
    val id: String,
    val displayName: String,
)

/** Kite 统一输入草稿。Skill 选择和用户正文分别保存，直到点击发送才组合。 */
data class AgentPromptDraft(
    val content: List<AgentContent>,
    val skills: List<AgentSelectedSkill> = emptyList(),
) {
    val visibleContent: List<AgentContent>
        get() = skills.distinctBy(AgentSelectedSkill::id).map { skill ->
            AgentContent.SkillReference(skill.id, skill.displayName)
        } + content
}

/**
 * 把统一 Skill 选择转换为 Agent 可理解的中性文本。
 *
 * 这段话只提示关联关系，不把“查看 Skill 是什么”误判成强制执行 Skill。
 */
object AgentSkillPromptComposer {
    fun compose(draft: AgentPromptDraft): List<AgentContent> {
        val skills = draft.skills.distinctBy(AgentSelectedSkill::id)
        if (skills.isEmpty()) return draft.content
        val instruction = AgentContent.Text(
            "${marker(skills)}\n${instruction(skills)}"
        )
        return listOf(instruction) + draft.content
    }

    /** 把 Agent 原生历史中的临时提示恢复成 Kite 胶囊，避免隐藏文本在重载后泄漏到聊天记录。 */
    fun restoreVisibleText(text: String): List<AgentContent>? {
        val markerEnd = text.indexOf('\n')
        if (markerEnd <= 0) return null
        val markerLine = text.substring(0, markerEnd)
        if (!markerLine.startsWith(MARKER_PREFIX) || !markerLine.endsWith(MARKER_SUFFIX)) return null
        val skills = markerLine
            .removePrefix(MARKER_PREFIX)
            .removeSuffix(MARKER_SUFFIX)
            .split(';')
            .mapNotNull { entry ->
                val parts = entry.split(',', limit = 2)
                if (parts.size != 2) null else runCatching {
                    AgentSelectedSkill(decode(parts[0]), decode(parts[1]))
                }.getOrNull()
            }
            .filter { it.id.isNotBlank() && it.displayName.isNotBlank() }
            .distinctBy(AgentSelectedSkill::id)
        if (skills.isEmpty()) return null
        val afterMarker = text.substring(markerEnd + 1)
        val expectedInstruction = instruction(skills)
        if (!afterMarker.startsWith(expectedInstruction)) return null
        val remainder = afterMarker.removePrefix(expectedInstruction).trimStart('\n')
        return buildList {
            addAll(skills.map { AgentContent.SkillReference(it.id, it.displayName) })
            if (remainder.isNotBlank()) add(AgentContent.Text(remainder))
        }
    }

    private fun instruction(skills: List<AgentSelectedSkill>): String {
        val names = skills.joinToString("、") { skill -> "〈${skill.displayName}〉" }
        return "本轮关联的 Skill：$names。请结合用户正文判断是使用、说明还是仅作参考；不要仅因关联而强制执行。"
    }

    private fun marker(skills: List<AgentSelectedSkill>): String = MARKER_PREFIX + skills.joinToString(";") { skill ->
        "${encode(skill.id)},${encode(skill.displayName)}"
    } + MARKER_SUFFIX

    private fun encode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name())
    private fun decode(value: String): String = URLDecoder.decode(value, Charsets.UTF_8.name())

    private const val MARKER_PREFIX = "[[kite-skill-context-v1:"
    private const val MARKER_SUFFIX = "]]"
}
