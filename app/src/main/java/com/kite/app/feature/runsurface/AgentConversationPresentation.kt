package com.kite.app.feature.runsurface

import com.kite.app.agent.contract.AgentContent
import com.kite.app.agent.contract.AgentMessageRole
import com.kite.app.agent.contract.AgentPlanEntry
import com.kite.app.agent.contract.AgentToolContent
import com.kite.app.agent.store.AgentConversationItem
import com.kite.app.agent.store.AgentConversationTurn
import com.kite.app.agent.store.AgentConversationTurnState

internal enum class AgentTextBlockStyle {
    Paragraph,
    Heading1,
    Heading2,
    Heading3,
    Heading4,
    Heading5,
    Heading6,
    Quote,
    Bullet,
    Ordered
}

internal data class AgentInlineTextSegment(
    val text: String,
    val styles: Set<Style>,
    val link: String? = null,
) {
    constructor(text: String, style: Style, link: String? = null) : this(text, setOf(style), link)

    val style: Style
        get() = when {
            Style.Link in styles -> Style.Link
            Style.Code in styles -> Style.Code
            Style.Strong in styles -> Style.Strong
            Style.Emphasis in styles -> Style.Emphasis
            Style.Strike in styles -> Style.Strike
            else -> Style.Plain
        }

    internal enum class Style {
        Plain,
        Strong,
        Emphasis,
        Strike,
        Code,
        Link
    }
}

internal sealed interface AgentConversationDisplayItem {
    val id: String

    data class UserMessage(
        override val id: String,
        val text: String,
        val skills: List<String> = emptyList(),
    ) : AgentConversationDisplayItem

    data class AssistantText(
        override val id: String,
        val text: String,
        val style: AgentTextBlockStyle,
        val inline: List<AgentInlineTextSegment>,
        val startsAnswer: Boolean,
        val listDepth: Int = 0,
        val taskChecked: Boolean? = null,
    ) : AgentConversationDisplayItem

    data class Code(
        override val id: String,
        val language: String?,
        val code: String,
        val startsAnswer: Boolean
    ) : AgentConversationDisplayItem

    data class Rule(
        override val id: String,
    ) : AgentConversationDisplayItem

    data class Table(
        override val id: String,
        val headers: List<String>,
        val rows: List<List<String>>,
        val copyText: String,
        val startsAnswer: Boolean,
    ) : AgentConversationDisplayItem

    data class AnswerCopy(
        override val id: String,
        val copyText: String,
    ) : AgentConversationDisplayItem

    data class Process(
        override val id: String,
        val turnOrdinal: Long,
        val state: AgentConversationTurnState,
        val startedAtMillis: Long?,
        val durationMillis: Long?,
        val entries: List<AgentConversationDisplayItem>,
    ) : AgentConversationDisplayItem

    data class Thought(
        override val id: String,
        val text: String
    ) : AgentConversationDisplayItem

    data class Tool(
        override val id: String,
        val title: String,
        val kind: String?,
        val status: String,
        val detail: String?
    ) : AgentConversationDisplayItem

    data class Plan(
        override val id: String,
        val entries: List<AgentPlanEntry>
    ) : AgentConversationDisplayItem

    data class Image(
        override val id: String,
        val title: String,
        val mimeType: String,
        val source: AgentImageSource,
        val userAuthored: Boolean = false,
    ) : AgentConversationDisplayItem

    data class Attachment(
        override val id: String,
        val title: String,
        val detail: String,
        val mimeType: String?,
        val source: AgentFileSource,
        val userAuthored: Boolean = false,
    ) : AgentConversationDisplayItem
}

/**
 * 把协议无关会话事实投影成轻量显示块。解析只在 snapshot 变化时执行，RecyclerView 绑定不解析 Markdown。
 */
internal object AgentConversationPresentation {
    fun project(items: List<AgentConversationItem>): List<AgentConversationDisplayItem> = buildList {
        items.forEach { item ->
            when (item) {
                is AgentConversationItem.Message -> addMessage(item)
                is AgentConversationItem.Tool -> addTool(item)
                is AgentConversationItem.Plan -> add(
                    AgentConversationDisplayItem.Plan(item.id, item.entries)
                )
            }
        }
    }

    fun composeTurns(
        items: List<AgentConversationItem>,
        turns: List<AgentConversationTurn>,
        blocksForItem: (AgentConversationItem) -> List<AgentConversationDisplayItem> = { project(listOf(it)) },
    ): List<AgentConversationDisplayItem> {
        if (items.isEmpty()) return emptyList()
        val turnsByOrdinal = turns.associateBy(AgentConversationTurn::ordinal)
        val grouped = linkedMapOf<Long, MutableList<AgentConversationItem>>()
        items.forEach { item -> grouped.getOrPut(item.turnOrdinal) { mutableListOf() } += item }
        return buildList {
            grouped.forEach { (turnOrdinal, turnItems) ->
                val projected = turnItems.map { item -> item to blocksForItem(item) }
                val lastProcessSource = projected.indexOfLast { (_, blocks) -> blocks.any(::isProcessBlock) }
                val processEntries = mutableListOf<AgentConversationDisplayItem>()
                val visibleEntries = mutableListOf<AgentConversationDisplayItem>()

                projected.forEachIndexed { sourceIndex, (source, blocks) ->
                    blocks.forEach { block ->
                        when {
                            isProcessBlock(block) -> processEntries += block
                            source is AgentConversationItem.Message &&
                                source.role == AgentMessageRole.Assistant &&
                                sourceIndex < lastProcessSource &&
                                block is AgentConversationDisplayItem.AssistantText -> {
                                processEntries += AgentConversationDisplayItem.Thought(block.id, block.text)
                            }
                            else -> visibleEntries += block
                        }
                    }
                    if (
                        source is AgentConversationItem.Message &&
                        source.role == AgentMessageRole.Assistant &&
                        sourceIndex > lastProcessSource
                    ) {
                        source.content.toAnswerCopyText().takeIf(String::isNotBlank)?.let { copyText ->
                            visibleEntries += AgentConversationDisplayItem.AnswerCopy(
                                id = "${source.id}:answer-copy",
                                copyText = copyText,
                            )
                        }
                    }
                }

                val process = processEntries.takeIf { it.isNotEmpty() }?.let { entries ->
                    val turn = turnsByOrdinal[turnOrdinal]
                    AgentConversationDisplayItem.Process(
                        id = "${turnItems.first().id}:turn:$turnOrdinal:process",
                        turnOrdinal = turnOrdinal,
                        state = turn?.state ?: AgentConversationTurnState.Historical,
                        startedAtMillis = turn?.startedAtMillis,
                        durationMillis = turn?.durationMillis,
                        entries = entries.toList(),
                    )
                }
                var processAdded = false
                visibleEntries.forEach { block ->
                    if (!processAdded && !block.isUserAuthored()) {
                        process?.let(::add)
                        processAdded = true
                    }
                    add(block)
                }
                if (!processAdded) process?.let(::add)
            }
        }
    }

    private fun isProcessBlock(block: AgentConversationDisplayItem): Boolean =
        block is AgentConversationDisplayItem.Thought ||
            block is AgentConversationDisplayItem.Tool ||
            block is AgentConversationDisplayItem.Plan

    private fun AgentConversationDisplayItem.isUserAuthored(): Boolean = when (this) {
        is AgentConversationDisplayItem.UserMessage -> true
        is AgentConversationDisplayItem.Image -> userAuthored
        is AgentConversationDisplayItem.Attachment -> userAuthored
        else -> false
    }

    fun parseInlineMarkdown(value: String): List<AgentInlineTextSegment> =
        AgentMarkdownParser.parseInline(value)

    private fun MutableList<AgentConversationDisplayItem>.addMessage(
        message: AgentConversationItem.Message
    ) {
        if (message.role == AgentMessageRole.User) {
            val text = message.content.filterIsInstance<AgentContent.Text>()
                .joinToString("\n", transform = AgentContent.Text::text)
            val skills = message.content.filterIsInstance<AgentContent.SkillReference>()
                .map(AgentContent.SkillReference::displayName)
            if (text.isNotBlank() || skills.isNotEmpty()) {
                add(
                    AgentConversationDisplayItem.UserMessage(
                        id = message.id,
                        text = text,
                        skills = skills,
                    )
                )
            }
            message.content.forEachIndexed { contentIndex, content ->
                if (content !is AgentContent.Text && content !is AgentContent.SkillReference) {
                    add(content.toMedia("${message.id}:attachment:$contentIndex", userAuthored = true))
                }
            }
            return
        }
        if (message.role == AgentMessageRole.Thought) {
            val text = message.content.joinToString("\n") { it.fallbackText() }.trim()
            if (text.isNotEmpty()) add(AgentConversationDisplayItem.Thought(message.id, text))
            return
        }

        var startsAnswer = true
        message.content.forEachIndexed { contentIndex, content ->
            when (content) {
                is AgentContent.Text -> {
                    parseMarkdown(message.id, contentIndex, content.text).forEach { block ->
                        add(block.withAnswerStart(startsAnswer))
                        startsAnswer = false
                    }
                }
                else -> {
                    add(content.toMedia("${message.id}:attachment:$contentIndex"))
                    startsAnswer = false
                }
            }
        }
    }

    private fun parseMarkdown(
        messageId: String,
        contentIndex: Int,
        markdown: String
    ): List<AgentConversationDisplayItem> = AgentMarkdownParser.parse(messageId, contentIndex, markdown)

    private fun AgentConversationDisplayItem.withAnswerStart(
        startsAnswer: Boolean
    ): AgentConversationDisplayItem = when (this) {
        is AgentConversationDisplayItem.AssistantText -> copy(startsAnswer = startsAnswer)
        is AgentConversationDisplayItem.Code -> copy(startsAnswer = startsAnswer)
        is AgentConversationDisplayItem.Table -> copy(startsAnswer = startsAnswer)
        is AgentConversationDisplayItem.Process -> this
        else -> this
    }

    private fun List<AgentContent>.toAnswerCopyText(): String = mapNotNull { content ->
        when (content) {
            is AgentContent.Text -> content.text.trim().takeIf(String::isNotBlank)
            is AgentContent.EmbeddedText -> content.text.trim().takeIf(String::isNotBlank)
            is AgentContent.ResourceLink -> markdownLink(content.title ?: content.name, content.uri)
            is AgentContent.Image -> content.uri?.let { uri -> "![图片]($uri)" }
            is AgentContent.EmbeddedBlob -> markdownLink(
                AgentMediaPolicy.safeDisplayName(content.uri, "文件", content.mimeType),
                content.uri,
            )
            is AgentContent.SkillReference -> content.displayName.takeIf(String::isNotBlank)
            is AgentContent.Audio -> null
        }
    }.joinToString("\n\n")

    private fun markdownLink(label: String, target: String): String =
        "[${label.replace("[", "\\[").replace("]", "\\]")}]($target)"

    private fun AgentConversationItem.Tool.toDisplay(): AgentConversationDisplayItem.Tool {
        val location = call.locations.firstOrNull()?.let { item ->
            item.path + (item.line?.let { ":$it" } ?: "")
        }
        val content = call.content.firstNotNullOfOrNull { item ->
            when (item) {
                is AgentToolContent.Content -> item.content.fallbackText().takeIf(String::isNotBlank)
                is AgentToolContent.Diff -> "修改 ${item.path}"
                is AgentToolContent.Terminal -> "终端 ${item.terminalId}"
            }
        }
        val raw = call.rawOutput?.trim()?.takeIf(String::isNotBlank)?.take(MAX_TOOL_DETAIL)
        return AgentConversationDisplayItem.Tool(
            id = id,
            title = call.title.ifBlank { call.kind?.value.orEmpty().ifBlank { "工具调用" } },
            kind = call.kind?.value,
            status = statusLabel(call.status?.value),
            detail = listOfNotNull(location, content ?: raw).distinct().joinToString("\n").takeIf(String::isNotBlank)
        )
    }

    private fun MutableList<AgentConversationDisplayItem>.addTool(
        tool: AgentConversationItem.Tool,
    ) {
        add(tool.toDisplay())
        tool.call.content.forEachIndexed { index, content ->
            val media = (content as? AgentToolContent.Content)
                ?.content
                ?.toToolMedia("${tool.id}:output:$index")
            if (media != null) add(media)
        }
    }

    private fun AgentContent.toToolMedia(id: String): AgentConversationDisplayItem? = when (this) {
        is AgentContent.Image -> toMedia(id)
        is AgentContent.ResourceLink -> takeIf { mimeType?.startsWith("image/") == true }?.toMedia(id)
        is AgentContent.EmbeddedBlob -> takeIf { mimeType?.startsWith("image/") == true }?.toMedia(id)
        else -> null
    }

    private fun AgentContent.toMedia(
        id: String,
        userAuthored: Boolean = false,
    ): AgentConversationDisplayItem = when (this) {
        is AgentContent.Image -> AgentConversationDisplayItem.Image(
            id = id,
            title = "图片",
            mimeType = mimeType,
            source = data.takeIf(String::isNotBlank)
                ?.let(AgentImageSource::InlineBase64)
                ?: uri?.takeIf(String::isNotBlank)?.let(AgentImageSource::Link)
                ?: AgentImageSource.InlineBase64(data),
            userAuthored = userAuthored,
        )
        is AgentContent.Audio -> AgentConversationDisplayItem.Attachment(
            id = id,
            title = AgentMediaPolicy.safeDisplayName(null, "音频", mimeType),
            detail = mimeType,
            mimeType = mimeType,
            source = AgentFileSource.InlineBase64(data),
            userAuthored = userAuthored,
        )
        is AgentContent.ResourceLink -> if (mimeType?.startsWith("image/") == true) {
            AgentConversationDisplayItem.Image(
                id = id,
                title = title ?: name,
                mimeType = mimeType,
                source = AgentImageSource.Link(uri),
                userAuthored = userAuthored,
            )
        } else {
            AgentConversationDisplayItem.Attachment(
                id = id,
                title = title ?: name,
                detail = listOfNotNull(mimeType, size?.let(::formatBytes), description)
                    .joinToString(" · ")
                    .ifBlank { uri },
                mimeType = mimeType,
                source = AgentFileSource.Link(uri),
                userAuthored = userAuthored,
            )
        }
        is AgentContent.EmbeddedBlob -> if (mimeType?.startsWith("image/") == true) {
            AgentConversationDisplayItem.Image(
                id = id,
                title = AgentMediaPolicy.safeDisplayName(uri, "图片", mimeType),
                mimeType = mimeType,
                source = AgentImageSource.InlineBase64(data),
                userAuthored = userAuthored,
            )
        } else {
            AgentConversationDisplayItem.Attachment(
                id = id,
                title = AgentMediaPolicy.safeDisplayName(uri, "文件", mimeType),
                detail = mimeType ?: "嵌入文件",
                mimeType = mimeType,
                source = AgentFileSource.InlineBase64(data),
                userAuthored = userAuthored,
            )
        }
        is AgentContent.EmbeddedText -> AgentConversationDisplayItem.Attachment(
            id = id,
            title = AgentMediaPolicy.safeDisplayName(uri, "文本", mimeType),
            detail = listOfNotNull(mimeType, "${text.toByteArray().size} B").joinToString(" · "),
            mimeType = mimeType ?: "text/plain",
            source = AgentFileSource.InlineText(text),
            userAuthored = userAuthored,
        )
        is AgentContent.Text -> AgentConversationDisplayItem.Attachment(
            id = id,
            title = "文本",
            detail = "text/plain",
            mimeType = "text/plain",
            source = AgentFileSource.InlineText(text),
            userAuthored = userAuthored,
        )
        is AgentContent.SkillReference -> AgentConversationDisplayItem.Attachment(
            id = id,
            title = displayName,
            detail = "Skill",
            mimeType = "text/plain",
            source = AgentFileSource.InlineText(displayName),
            userAuthored = userAuthored,
        )
    }

    private fun AgentContent.fallbackText(): String = when (this) {
        is AgentContent.Text -> text
        is AgentContent.SkillReference -> "Skill · $displayName"
        is AgentContent.Image -> "图片 · $mimeType"
        is AgentContent.Audio -> "音频 · $mimeType"
        is AgentContent.ResourceLink -> title ?: name
        is AgentContent.EmbeddedText -> text
        is AgentContent.EmbeddedBlob -> "文件 · ${mimeType ?: uri}"
    }

    private fun statusLabel(value: String?): String = when (value?.lowercase()) {
        "pending" -> "等待中"
        "in_progress", "running" -> "进行中"
        "completed", "complete", "success", "succeeded" -> "已完成"
        "failed", "error" -> "失败"
        else -> value?.takeIf(String::isNotBlank) ?: "已调用"
    }

    private fun formatBytes(bytes: Long): String = when {
        bytes >= 1024L * 1024L -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
        bytes >= 1024L -> "%.1f KB".format(bytes / 1024.0)
        else -> "$bytes B"
    }

    private const val MAX_TOOL_DETAIL = 600
}
