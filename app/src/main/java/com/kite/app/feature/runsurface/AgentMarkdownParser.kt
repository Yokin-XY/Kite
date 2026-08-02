package com.kite.app.feature.runsurface

import java.net.URI
import org.commonmark.Extension
import org.commonmark.ext.autolink.AutolinkExtension
import org.commonmark.ext.gfm.strikethrough.Strikethrough
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension
import org.commonmark.ext.gfm.tables.TableBlock
import org.commonmark.ext.gfm.tables.TableCell
import org.commonmark.ext.gfm.tables.TableRow
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.ext.task.list.items.TaskListItemMarker
import org.commonmark.ext.task.list.items.TaskListItemsExtension
import org.commonmark.node.BlockQuote
import org.commonmark.node.BulletList
import org.commonmark.node.Code
import org.commonmark.node.Emphasis
import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.HardLineBreak
import org.commonmark.node.Heading
import org.commonmark.node.HtmlBlock
import org.commonmark.node.HtmlInline
import org.commonmark.node.Image
import org.commonmark.node.IndentedCodeBlock
import org.commonmark.node.Link
import org.commonmark.node.ListBlock
import org.commonmark.node.ListItem
import org.commonmark.node.Node
import org.commonmark.node.OrderedList
import org.commonmark.node.Paragraph
import org.commonmark.node.SoftLineBreak
import org.commonmark.node.StrongEmphasis
import org.commonmark.node.Text
import org.commonmark.node.ThematicBreak
import org.commonmark.parser.Parser

/** GFM AST 到 Kite 统一会话显示模型的唯一转换入口。 */
internal object AgentMarkdownParser {
    private val extensions: List<Extension> = listOf(
        AutolinkExtension.create(),
        StrikethroughExtension.create(),
        TablesExtension.create(),
        TaskListItemsExtension.create(),
    )
    private val parser: Parser = Parser.builder().extensions(extensions).build()

    fun parse(
        messageId: String,
        contentIndex: Int,
        markdown: String,
    ): List<AgentConversationDisplayItem> {
        if (markdown.isBlank()) return emptyList()
        return Projection(messageId, contentIndex).apply {
            renderChildren(parser.parse(markdown), BlockContext())
        }.items
    }

    fun parseInline(value: String): List<AgentInlineTextSegment> {
        if (value.isEmpty()) return emptyList()
        val parts = mutableListOf<InlinePart>()
        val document = parser.parse(value)
        var block = document.firstChild
        var needsParagraphBreak = false
        while (block != null) {
            if (needsParagraphBreak) appendText(parts, "\n\n", InlineState())
            collectInlineChildren(block, InlineState(), parts)
            needsParagraphBreak = true
            block = block.next
        }
        return parts.flatMap { part ->
            when (part) {
                is InlinePart.Segment -> listOf(part.value)
                is InlinePart.Image -> listOf(
                    AgentInlineTextSegment(
                        text = part.alt.ifBlank { part.destination },
                        style = AgentInlineTextSegment.Style.Plain,
                    )
                )
            }
        }.ifEmpty { listOf(AgentInlineTextSegment(value, AgentInlineTextSegment.Style.Plain)) }
    }

    private class Projection(
        private val messageId: String,
        private val contentIndex: Int,
    ) {
        val items = mutableListOf<AgentConversationDisplayItem>()
        private var ordinal = 0

        fun renderChildren(parent: Node, context: BlockContext) {
            var child = parent.firstChild
            while (child != null) {
                renderBlock(child, context)
                child = child.next
            }
        }

        private fun renderBlock(node: Node, context: BlockContext) {
            when (node) {
                is Paragraph -> renderInlineBlock(
                    node = node,
                    style = if (context.quote) AgentTextBlockStyle.Quote else AgentTextBlockStyle.Paragraph,
                    listDepth = context.listDepth,
                )
                is Heading -> renderInlineBlock(node, node.level.toHeadingStyle())
                is FencedCodeBlock -> items += AgentConversationDisplayItem.Code(
                    id = nextId("code"),
                    language = node.info?.trim()?.substringBefore(' ')?.takeIf(String::isNotBlank),
                    code = node.literal.trimEnd('\r', '\n'),
                    startsAnswer = false,
                )
                is IndentedCodeBlock -> items += AgentConversationDisplayItem.Code(
                    id = nextId("code"),
                    language = null,
                    code = node.literal.trimEnd('\r', '\n'),
                    startsAnswer = false,
                )
                is ThematicBreak -> items += AgentConversationDisplayItem.Rule(nextId("rule"))
                is BlockQuote -> renderChildren(node, context.copy(quote = true))
                is BulletList -> renderList(node, context, AgentTextBlockStyle.Bullet)
                is OrderedList -> renderList(node, context, AgentTextBlockStyle.Ordered)
                is TableBlock -> renderTable(node)
                is HtmlBlock -> items += AgentConversationDisplayItem.Code(
                    id = nextId("raw-html"),
                    language = "html",
                    code = node.literal.trimEnd('\r', '\n'),
                    startsAnswer = false,
                )
                else -> renderChildren(node, context)
            }
        }

        private fun renderList(list: ListBlock, context: BlockContext, style: AgentTextBlockStyle) {
            var itemNode = list.firstChild
            var itemIndex = 0
            while (itemNode != null) {
                if (itemNode is ListItem) {
                    val taskChecked = itemNode.findTaskMarker()?.isChecked
                    var child = itemNode.firstChild
                    var firstParagraph = true
                    while (child != null) {
                        when (child) {
                            is Paragraph -> {
                                val marker = if (firstParagraph) {
                                    listMarker(list, itemIndex, taskChecked)
                                } else {
                                    null
                                }
                                renderInlineBlock(
                                    node = child,
                                    style = if (firstParagraph) style else AgentTextBlockStyle.Paragraph,
                                    prefix = marker,
                                    listDepth = context.listDepth,
                                    taskChecked = taskChecked.takeIf { firstParagraph },
                                )
                                firstParagraph = false
                            }
                            is BulletList -> renderList(
                                child,
                                context.copy(listDepth = context.listDepth + 1),
                                AgentTextBlockStyle.Bullet,
                            )
                            is OrderedList -> renderList(
                                child,
                                context.copy(listDepth = context.listDepth + 1),
                                AgentTextBlockStyle.Ordered,
                            )
                            else -> renderBlock(child, context.copy(listDepth = context.listDepth + 1))
                        }
                        child = child.next
                    }
                    itemIndex += 1
                }
                itemNode = itemNode.next
            }
        }

        private fun renderInlineBlock(
            node: Node,
            style: AgentTextBlockStyle,
            prefix: String? = null,
            listDepth: Int = 0,
            taskChecked: Boolean? = null,
        ) {
            val parts = mutableListOf<InlinePart>()
            collectInlineChildren(node, InlineState(), parts)
            val pending = mutableListOf<AgentInlineTextSegment>()
            prefix?.let { pending += AgentInlineTextSegment(it, AgentInlineTextSegment.Style.Plain) }

            fun flushText() {
                if (pending.isEmpty()) return
                val text = pending.joinToString(separator = "", transform = AgentInlineTextSegment::text)
                if (text.isNotBlank()) {
                    items += AgentConversationDisplayItem.AssistantText(
                        id = nextId("text"),
                        text = text,
                        style = style,
                        inline = pending.toList(),
                        startsAnswer = false,
                        listDepth = listDepth,
                        taskChecked = taskChecked,
                    )
                }
                pending.clear()
            }

            parts.forEach { part ->
                when (part) {
                    is InlinePart.Segment -> pending += part.value
                    is InlinePart.Image -> {
                        flushText()
                        if (isSafeTarget(part.destination)) {
                            items += AgentConversationDisplayItem.Image(
                                id = nextId("image"),
                                title = part.alt.ifBlank { displayName(part.destination, "图片") },
                                mimeType = imageMimeType(part.destination),
                                source = AgentImageSource.Link(part.destination),
                            )
                        } else {
                            pending += AgentInlineTextSegment(
                                "![${part.alt}](${part.destination})",
                                AgentInlineTextSegment.Style.Plain,
                            )
                        }
                    }
                }
            }
            flushText()
        }

        private fun renderTable(table: TableBlock) {
            val rows = mutableListOf<List<String>>()
            var node: Node? = table.firstChild
            while (node != null) {
                if (node is TableRow) {
                    val cells = mutableListOf<String>()
                    var cell = node.firstChild
                    while (cell != null) {
                        if (cell is TableCell) cells += plainText(cell).trim()
                        cell = cell.next
                    }
                    if (cells.isNotEmpty()) rows += cells.take(MAX_TABLE_COLUMNS)
                }
                if (node.firstChild != null) {
                    node = node.firstChild
                    continue
                }
                while (node != null && node !== table && node.next == null) node = node.parent
                node = if (node == null || node === table) null else node.next
            }
            if (rows.isEmpty()) return
            val headers = rows.first()
            val body = rows.drop(1).take(MAX_TABLE_ROWS)
            val copyText = buildString {
                appendTableRow(headers)
                appendTableRow(List(headers.size) { "---" })
                body.forEach { row -> appendTableRow(row) }
            }.trimEnd()
            items += AgentConversationDisplayItem.Table(
                id = nextId("table"),
                headers = headers,
                rows = body.map { row -> List(headers.size) { index -> row.getOrNull(index).orEmpty() } },
                copyText = copyText,
                startsAnswer = false,
            )
        }

        private fun StringBuilder.appendTableRow(values: List<String>) {
            append("| ")
            append(values.joinToString(" | ") { it.replace("|", "\\|") })
            append(" |\n")
        }

        private fun nextId(kind: String): String = "$messageId:$contentIndex:$kind:${ordinal++}"
    }

    private data class BlockContext(
        val quote: Boolean = false,
        val listDepth: Int = 0,
    )

    private data class InlineState(
        val styles: Set<AgentInlineTextSegment.Style> = emptySet(),
        val link: String? = null,
    )

    private sealed interface InlinePart {
        data class Segment(val value: AgentInlineTextSegment) : InlinePart
        data class Image(val alt: String, val destination: String) : InlinePart
    }

    private fun collectInlineChildren(node: Node, state: InlineState, output: MutableList<InlinePart>) {
        var child = node.firstChild
        while (child != null) {
            collectInline(child, state, output)
            child = child.next
        }
    }

    private fun collectInline(node: Node, state: InlineState, output: MutableList<InlinePart>) {
        when (node) {
            is Text -> appendText(output, node.literal, state)
            is Code -> appendText(
                output,
                node.literal,
                state.copy(styles = state.styles + AgentInlineTextSegment.Style.Code),
            )
            is SoftLineBreak -> appendText(output, "\n", state)
            is HardLineBreak -> appendText(output, "\n", state)
            is StrongEmphasis -> collectInlineChildren(
                node,
                state.copy(styles = state.styles + AgentInlineTextSegment.Style.Strong),
                output,
            )
            is Emphasis -> collectInlineChildren(
                node,
                state.copy(styles = state.styles + AgentInlineTextSegment.Style.Emphasis),
                output,
            )
            is Strikethrough -> collectInlineChildren(
                node,
                state.copy(styles = state.styles + AgentInlineTextSegment.Style.Strike),
                output,
            )
            is Link -> {
                if (isSafeTarget(node.destination)) {
                    collectInlineChildren(
                        node,
                        state.copy(
                            styles = state.styles + AgentInlineTextSegment.Style.Link,
                            link = node.destination,
                        ),
                        output,
                    )
                } else {
                    appendText(output, "[${plainText(node)}](${node.destination})", state)
                }
            }
            is Image -> output += InlinePart.Image(plainText(node), node.destination)
            is HtmlInline -> appendText(output, node.literal, state)
            is TaskListItemMarker -> Unit
            else -> collectInlineChildren(node, state, output)
        }
    }

    private fun appendText(output: MutableList<InlinePart>, text: String, state: InlineState) {
        if (text.isEmpty()) return
        val styles = state.styles.ifEmpty { setOf(AgentInlineTextSegment.Style.Plain) }
        val segment = AgentInlineTextSegment(text, styles, state.link)
        val previous = output.lastOrNull() as? InlinePart.Segment
        if (previous?.value?.styles == segment.styles && previous.value.link == segment.link) {
            output[output.lastIndex] = InlinePart.Segment(previous.value.copy(text = previous.value.text + text))
        } else {
            output += InlinePart.Segment(segment)
        }
    }

    private fun Node.findTaskMarker(): TaskListItemMarker? {
        var node = firstChild
        while (node != null) {
            if (node is TaskListItemMarker) return node
            node.findTaskMarker()?.let { return it }
            node = node.next
        }
        return null
    }

    private fun plainText(node: Node): String = buildString {
        fun appendNode(current: Node) {
            when (current) {
                is Text -> append(current.literal)
                is Code -> append(current.literal)
                is SoftLineBreak, is HardLineBreak -> append('\n')
                is Image -> append(plainText(current))
                is HtmlInline -> append(current.literal)
                is TaskListItemMarker -> Unit
                else -> {
                    var child = current.firstChild
                    while (child != null) {
                        appendNode(child)
                        child = child.next
                    }
                }
            }
        }
        var child = node.firstChild
        while (child != null) {
            appendNode(child)
            child = child.next
        }
    }

    private fun listMarker(list: ListBlock, itemIndex: Int, taskChecked: Boolean?): String = when {
        taskChecked == true -> "☑ "
        taskChecked == false -> "☐ "
        list is OrderedList -> {
            val number = (list.markerStartNumber ?: 1) + itemIndex
            "$number${list.markerDelimiter ?: "."} "
        }
        else -> "• "
    }

    private fun Int.toHeadingStyle(): AgentTextBlockStyle = when (this) {
        1 -> AgentTextBlockStyle.Heading1
        2 -> AgentTextBlockStyle.Heading2
        3 -> AgentTextBlockStyle.Heading3
        4 -> AgentTextBlockStyle.Heading4
        5 -> AgentTextBlockStyle.Heading5
        else -> AgentTextBlockStyle.Heading6
    }

    private fun isSafeTarget(value: String): Boolean {
        val target = value.trim()
        if (target.isEmpty() || target.any(Char::isISOControl)) return false
        val scheme = runCatching { URI(target).scheme?.lowercase() }.getOrNull()
        return when (scheme) {
            null -> !target.startsWith("//")
            "http", "https", "mailto", "content", "file" -> true
            else -> false
        }
    }

    private fun displayName(value: String, fallback: String): String = value
        .substringBefore('#')
        .substringBefore('?')
        .substringAfterLast('/')
        .takeIf(String::isNotBlank)
        ?: fallback

    private fun imageMimeType(value: String): String = when (
        value.substringBefore('#').substringBefore('?').substringAfterLast('.').lowercase()
    ) {
        "jpg", "jpeg" -> "image/jpeg"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        "svg" -> "image/svg+xml"
        else -> "image/png"
    }

    private const val MAX_TABLE_COLUMNS = 8
    private const val MAX_TABLE_ROWS = 40
}
