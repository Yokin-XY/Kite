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
    Quote,
    Bullet,
    Ordered
}

internal data class AgentInlineTextSegment(
    val text: String,
    val style: Style,
    val link: String? = null,
) {
    internal enum class Style {
        Plain,
        Strong,
        Code,
        Link
    }
}

internal sealed interface AgentConversationDisplayItem {
    val id: String

    data class UserMessage(
        override val id: String,
        val text: String
    ) : AgentConversationDisplayItem

    data class AssistantText(
        override val id: String,
        val text: String,
        val style: AgentTextBlockStyle,
        val inline: List<AgentInlineTextSegment>,
        val startsAnswer: Boolean
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
        val source: AgentImageSource
    ) : AgentConversationDisplayItem

    data class Attachment(
        override val id: String,
        val title: String,
        val detail: String,
        val mimeType: String?,
        val source: AgentFileSource
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
                is AgentConversationItem.Tool -> add(item.toDisplay())
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
                    if (!processAdded && block !is AgentConversationDisplayItem.UserMessage) {
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

    fun parseInlineMarkdown(value: String): List<AgentInlineTextSegment> {
        if (value.isEmpty()) return emptyList()
        val result = mutableListOf<AgentInlineTextSegment>()
        var cursor = 0
        INLINE_MARKDOWN.findAll(value).forEach { match ->
            if (match.range.first > cursor) {
                result += AgentInlineTextSegment(
                    value.substring(cursor, match.range.first),
                    AgentInlineTextSegment.Style.Plain
                )
            }
            val strong = match.groups[1]?.value
            val code = match.groups[2]?.value
            val linkText = match.groups[3]?.value
            val linkTarget = match.groups[4]?.value
            result += when {
                strong != null -> AgentInlineTextSegment(strong, AgentInlineTextSegment.Style.Strong)
                code != null -> AgentInlineTextSegment(code, AgentInlineTextSegment.Style.Code)
                linkText != null && isSafeLink(linkTarget) -> AgentInlineTextSegment(
                    linkText,
                    AgentInlineTextSegment.Style.Link,
                    linkTarget,
                )
                else -> AgentInlineTextSegment(match.value, AgentInlineTextSegment.Style.Plain)
            }
            cursor = match.range.last + 1
        }
        if (cursor < value.length) {
            result += AgentInlineTextSegment(
                value.substring(cursor),
                AgentInlineTextSegment.Style.Plain
            )
        }
        return result.ifEmpty {
            listOf(AgentInlineTextSegment(value, AgentInlineTextSegment.Style.Plain))
        }
    }

    private fun MutableList<AgentConversationDisplayItem>.addMessage(
        message: AgentConversationItem.Message
    ) {
        if (message.role == AgentMessageRole.User) {
            add(
                AgentConversationDisplayItem.UserMessage(
                    id = message.id,
                    text = message.content.joinToString("\n") { it.fallbackText() }
                )
            )
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
    ): List<AgentConversationDisplayItem> {
        if (markdown.isBlank()) return emptyList()
        val result = mutableListOf<AgentConversationDisplayItem>()
        val paragraph = mutableListOf<String>()
        val code = mutableListOf<String>()
        var codeLanguage: String? = null
        var inCode = false
        var ordinal = 0

        fun nextId(kind: String): String = "$messageId:$contentIndex:$kind:${ordinal++}"
        fun flushParagraph() {
            val value = paragraph.joinToString("\n").trimEnd()
            paragraph.clear()
            if (value.isNotBlank()) {
                result += AgentConversationDisplayItem.AssistantText(
                    id = nextId("text"),
                    text = value,
                    style = AgentTextBlockStyle.Paragraph,
                    inline = parseInlineMarkdown(value),
                    startsAnswer = false
                )
            }
        }
        fun flushCode() {
            result += AgentConversationDisplayItem.Code(
                id = nextId("code"),
                language = codeLanguage,
                code = code.joinToString("\n").trimEnd(),
                startsAnswer = false
            )
            code.clear()
            codeLanguage = null
        }

        val lines = markdown.replace("\r\n", "\n").replace('\r', '\n').split('\n')
        var lineIndex = 0
        while (lineIndex < lines.size) {
            val line = lines[lineIndex]
            val fence = FENCE.matchEntire(line)
            if (fence != null) {
                if (inCode) {
                    flushCode()
                    inCode = false
                } else {
                    flushParagraph()
                    codeLanguage = fence.groupValues[1].trim().takeIf(String::isNotBlank)
                    inCode = true
                }
                lineIndex += 1
                continue
            }
            if (inCode) {
                code += line
                lineIndex += 1
                continue
            }
            if (line.isBlank()) {
                flushParagraph()
                lineIndex += 1
                continue
            }
            val table = parseTable(lines, lineIndex)
            if (table != null) {
                flushParagraph()
                result += AgentConversationDisplayItem.Table(
                    id = nextId("table"),
                    headers = table.headers,
                    rows = table.rows,
                    copyText = table.copyText,
                    startsAnswer = false,
                )
                lineIndex = table.nextLineIndex
                continue
            }

            val heading = HEADING.matchEntire(line)
            val quote = QUOTE.matchEntire(line)
            val bullet = BULLET.matchEntire(line)
            val rule = HORIZONTAL_RULE.matchEntire(line)
            when {
                heading != null -> {
                    flushParagraph()
                    val level = heading.groupValues[1].length
                    result += AgentConversationDisplayItem.AssistantText(
                        id = nextId("heading"),
                        text = heading.groupValues[2].trim(),
                        style = when (level) {
                            1 -> AgentTextBlockStyle.Heading1
                            2 -> AgentTextBlockStyle.Heading2
                            else -> AgentTextBlockStyle.Heading3
                        },
                        inline = parseInlineMarkdown(heading.groupValues[2].trim()),
                        startsAnswer = false
                    )
                }
                quote != null -> {
                    flushParagraph()
                    result += AgentConversationDisplayItem.AssistantText(
                        id = nextId("quote"),
                        text = quote.groupValues[1].trim(),
                        style = AgentTextBlockStyle.Quote,
                        inline = parseInlineMarkdown(quote.groupValues[1].trim()),
                        startsAnswer = false
                    )
                }
                bullet != null -> {
                    flushParagraph()
                    val marker = bullet.groupValues[1]
                    val body = bullet.groupValues[2].trim()
                    val text = if (marker.firstOrNull()?.isDigit() == true) "$marker $body" else "• $body"
                    result += AgentConversationDisplayItem.AssistantText(
                        id = nextId("bullet"),
                        text = text,
                        style = if (marker.firstOrNull()?.isDigit() == true) {
                            AgentTextBlockStyle.Ordered
                        } else {
                            AgentTextBlockStyle.Bullet
                        },
                        inline = parseInlineMarkdown(text),
                        startsAnswer = false
                    )
                }
                rule != null -> {
                    flushParagraph()
                    result += AgentConversationDisplayItem.Rule(nextId("rule"))
                }
                else -> paragraph += line
            }
            lineIndex += 1
        }
        if (inCode) flushCode() else flushParagraph()
        return result
    }

    private fun AgentConversationDisplayItem.withAnswerStart(
        startsAnswer: Boolean
    ): AgentConversationDisplayItem = when (this) {
        is AgentConversationDisplayItem.AssistantText -> copy(startsAnswer = startsAnswer)
        is AgentConversationDisplayItem.Code -> copy(startsAnswer = startsAnswer)
        is AgentConversationDisplayItem.Table -> copy(startsAnswer = startsAnswer)
        is AgentConversationDisplayItem.Process -> this
        else -> this
    }

    private fun parseTable(lines: List<String>, start: Int): MarkdownTable? {
        if (start + 1 >= lines.size || '|' !in lines[start]) return null
        val headers = parseTableRow(lines[start])
        val divider = parseTableRow(lines[start + 1])
        if (headers.size < 2 || divider.size != headers.size || divider.any { !TABLE_DIVIDER.matches(it) }) {
            return null
        }
        val rows = mutableListOf<List<String>>()
        val source = mutableListOf(lines[start], lines[start + 1])
        var cursor = start + 2
        while (cursor < lines.size && rows.size < MAX_TABLE_ROWS) {
            val candidate = lines[cursor]
            if (candidate.isBlank() || '|' !in candidate) break
            val cells = parseTableRow(candidate)
            if (cells.isEmpty()) break
            rows += List(headers.size) { index -> cells.getOrNull(index).orEmpty() }
            source += candidate
            cursor += 1
        }
        return MarkdownTable(
            headers = headers.take(MAX_TABLE_COLUMNS),
            rows = rows.map { it.take(MAX_TABLE_COLUMNS) },
            copyText = source.joinToString("\n"),
            nextLineIndex = cursor,
        )
    }

    private fun parseTableRow(line: String): List<String> = line
        .trim()
        .removePrefix("|")
        .removeSuffix("|")
        .split('|')
        .map(String::trim)

    private fun isSafeLink(value: String?): Boolean = value
        ?.trim()
        ?.lowercase()
        ?.let { it.startsWith("https://") || it.startsWith("http://") }
        ?: false

    private data class MarkdownTable(
        val headers: List<String>,
        val rows: List<List<String>>,
        val copyText: String,
        val nextLineIndex: Int,
    )

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

    private fun AgentContent.toMedia(id: String): AgentConversationDisplayItem = when (this) {
        is AgentContent.Image -> AgentConversationDisplayItem.Image(
            id = id,
            title = "图片",
            mimeType = mimeType,
            source = AgentImageSource.InlineBase64(data)
        )
        is AgentContent.Audio -> AgentConversationDisplayItem.Attachment(
            id = id,
            title = AgentMediaPolicy.safeDisplayName(null, "音频", mimeType),
            detail = mimeType,
            mimeType = mimeType,
            source = AgentFileSource.InlineBase64(data)
        )
        is AgentContent.ResourceLink -> AgentConversationDisplayItem.Attachment(
            id = id,
            title = title ?: name,
            detail = listOfNotNull(mimeType, size?.let(::formatBytes), description)
                .joinToString(" · ")
                .ifBlank { uri },
            mimeType = mimeType,
            source = AgentFileSource.Link(uri)
        )
        is AgentContent.EmbeddedBlob -> if (mimeType?.startsWith("image/") == true) {
            AgentConversationDisplayItem.Image(
                id = id,
                title = AgentMediaPolicy.safeDisplayName(uri, "图片", mimeType),
                mimeType = mimeType,
                source = AgentImageSource.InlineBase64(data)
            )
        } else {
            AgentConversationDisplayItem.Attachment(
                id = id,
                title = AgentMediaPolicy.safeDisplayName(uri, "文件", mimeType),
                detail = mimeType ?: "嵌入文件",
                mimeType = mimeType,
                source = AgentFileSource.InlineBase64(data)
            )
        }
        is AgentContent.EmbeddedText -> AgentConversationDisplayItem.Attachment(
            id = id,
            title = AgentMediaPolicy.safeDisplayName(uri, "文本", mimeType),
            detail = listOfNotNull(mimeType, "${text.toByteArray().size} B").joinToString(" · "),
            mimeType = mimeType ?: "text/plain",
            source = AgentFileSource.InlineText(text)
        )
        is AgentContent.Text -> AgentConversationDisplayItem.Attachment(
            id = id,
            title = "文本",
            detail = "text/plain",
            mimeType = "text/plain",
            source = AgentFileSource.InlineText(text)
        )
    }

    private fun AgentContent.fallbackText(): String = when (this) {
        is AgentContent.Text -> text
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

    private val FENCE = Regex("^\\s*```(.*)$")
    private val HEADING = Regex("^\\s*(#{1,6})\\s+(.+)$")
    private val QUOTE = Regex("^\\s*>\\s?(.*)$")
    private val BULLET = Regex("^\\s*((?:[-*+])|(?:\\d+[.)]))\\s+(.+)$")
    private val HORIZONTAL_RULE = Regex("^\\s*(?:-{3,}|_{3,}|\\*{3,})\\s*$")
    private val TABLE_DIVIDER = Regex("^:?-{3,}:?$")
    private val INLINE_MARKDOWN = Regex("\\*\\*(.+?)\\*\\*|`([^`\\n]+)`|\\[([^]\\n]+)]\\(([^)\\s]+)\\)")
    private const val MAX_TOOL_DETAIL = 600
    private const val MAX_TABLE_COLUMNS = 8
    private const val MAX_TABLE_ROWS = 40
}
