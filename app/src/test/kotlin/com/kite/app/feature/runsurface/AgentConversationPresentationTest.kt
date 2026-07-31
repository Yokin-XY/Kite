package com.kite.app.feature.runsurface

import com.kite.app.agent.contract.AgentContent
import com.kite.app.agent.contract.AgentMessageRole
import com.kite.app.agent.contract.AgentPlanEntry
import com.kite.app.agent.contract.AgentToolCall
import com.kite.app.agent.contract.AgentToolLocation
import com.kite.app.agent.contract.AgentToolStatus
import com.kite.app.agent.store.AgentConversationItem
import com.kite.app.agent.store.AgentConversationTurn
import com.kite.app.agent.store.AgentConversationTurnState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentConversationPresentationTest {
    @Test
    fun `行内强调和代码去掉标记并保留样式语义`() {
        val segments = AgentConversationPresentation.parseInlineMarkdown(
            "这是 **重点**，运行 `gradle test`。"
        )

        assertEquals(
            listOf(
                AgentInlineTextSegment("这是 ", AgentInlineTextSegment.Style.Plain),
                AgentInlineTextSegment("重点", AgentInlineTextSegment.Style.Strong),
                AgentInlineTextSegment("，运行 ", AgentInlineTextSegment.Style.Plain),
                AgentInlineTextSegment("gradle test", AgentInlineTextSegment.Style.Code),
                AgentInlineTextSegment("。", AgentInlineTextSegment.Style.Plain)
            ),
            segments
        )
    }

    @Test
    fun `安全链接保留目标而危险协议降级成正文`() {
        val segments = AgentConversationPresentation.parseInlineMarkdown(
            "查看 [报告](https://example.com/report) 和 [危险链接](javascript:alert(1))"
        )

        val link = segments.first { it.style == AgentInlineTextSegment.Style.Link }
        assertEquals("报告", link.text)
        assertEquals("https://example.com/report", link.link)
        assertTrue(segments.joinToString("") { it.text }.contains("[危险链接](javascript:alert(1))"))
        assertFalse(segments.any { it.link?.startsWith("javascript:") == true })
    }

    @Test
    fun `助手 Markdown 拆成标题正文列表和可复制代码块`() {
        val items = AgentConversationPresentation.project(
            listOf(
                AgentConversationItem.Message(
                    id = "answer",
                    role = AgentMessageRole.Assistant,
                    content = listOf(
                        AgentContent.Text(
                            """
                                ## 结果

                                已完成核心修改。

                                - 通过测试

                                ```kotlin
                                val ready = true
                                ```
                            """.trimIndent()
                        )
                    )
                )
            )
        )

        assertEquals(4, items.size)
        val heading = items[0] as AgentConversationDisplayItem.AssistantText
        val paragraph = items[1] as AgentConversationDisplayItem.AssistantText
        val bullet = items[2] as AgentConversationDisplayItem.AssistantText
        val code = items[3] as AgentConversationDisplayItem.Code
        assertEquals(AgentTextBlockStyle.Heading2, heading.style)
        assertTrue(heading.startsAnswer)
        assertEquals("已完成核心修改。", paragraph.text)
        assertEquals("• 通过测试", bullet.text)
        assertEquals("kotlin", code.language)
        assertEquals("val ready = true", code.code)
        assertFalse(code.startsAnswer)
    }

    @Test
    fun `编号分隔线和简单表格保留独立展示语义`() {
        val items = AgentConversationPresentation.project(
            listOf(
                AgentConversationItem.Message(
                    id = "structured",
                    role = AgentMessageRole.Assistant,
                    content = listOf(
                        AgentContent.Text(
                            """
                                1. 检查入口
                                2) 运行测试

                                ---

                                | 项目 | 状态 |
                                | --- | :---: |
                                | SDK | 完成 |
                            """.trimIndent()
                        )
                    )
                )
            )
        )

        val first = items[0] as AgentConversationDisplayItem.AssistantText
        val second = items[1] as AgentConversationDisplayItem.AssistantText
        assertEquals(AgentTextBlockStyle.Ordered, first.style)
        assertEquals("1. 检查入口", first.text)
        assertEquals("2) 运行测试", second.text)
        assertTrue(items[2] is AgentConversationDisplayItem.Rule)
        val table = items[3] as AgentConversationDisplayItem.Table
        assertEquals(listOf("项目", "状态"), table.headers)
        assertEquals(listOf("SDK", "完成"), table.rows.single())
        assertTrue(table.copyText.contains("| SDK | 完成 |"))
    }

    @Test
    fun `推理工具与计划保持不同显示语义`() {
        val source = listOf(
                AgentConversationItem.Message(
                    id = "thought",
                    role = AgentMessageRole.Thought,
                    content = listOf(AgentContent.Text("先检查真实入口")),
                    turnOrdinal = 1L,
                ),
                AgentConversationItem.Tool(
                    id = "tool-1",
                    call = AgentToolCall(
                        id = "tool-1",
                        title = "读取文件",
                        status = AgentToolStatus("completed"),
                        locations = listOf(AgentToolLocation("/workspace/app.kt", 12)),
                        rawOutput = "读取成功"
                    ),
                    turnOrdinal = 1L,
                ),
                AgentConversationItem.Plan(
                    id = "plan-1",
                    entries = listOf(AgentPlanEntry("运行测试", "high", "in_progress")),
                    turnOrdinal = 1L,
                )
            )
        val items = AgentConversationPresentation.composeTurns(
            source,
            listOf(AgentConversationTurn(1L, AgentConversationTurnState.Completed, 1_000L, 4_000L)),
        )

        val process = items.single() as AgentConversationDisplayItem.Process
        assertEquals(3_000L, process.durationMillis)
        assertTrue(process.entries[0] is AgentConversationDisplayItem.Thought)
        val tool = process.entries[1] as AgentConversationDisplayItem.Tool
        assertEquals("已完成", tool.status)
        assertTrue(tool.detail.orEmpty().contains("/workspace/app.kt:12"))
        val plan = process.entries[2] as AgentConversationDisplayItem.Plan
        assertEquals("运行测试", plan.entries.single().content)
    }

    @Test
    fun `完成回合只折叠过程而最终回答保持在外部`() {
        val source = listOf(
            AgentConversationItem.Message(
                id = "user",
                role = AgentMessageRole.User,
                content = listOf(AgentContent.Text("检查")),
                turnOrdinal = 3L,
            ),
            AgentConversationItem.Message(
                id = "thought",
                role = AgentMessageRole.Thought,
                content = listOf(AgentContent.Text("正在读取")),
                turnOrdinal = 3L,
            ),
            AgentConversationItem.Message(
                id = "answer",
                role = AgentMessageRole.Assistant,
                content = listOf(AgentContent.Text("已完成")),
                turnOrdinal = 3L,
            ),
        )

        val items = AgentConversationPresentation.composeTurns(
            source,
            listOf(AgentConversationTurn(3L, AgentConversationTurnState.Completed, 2_000L, 5_000L)),
        )

        assertTrue(items[0] is AgentConversationDisplayItem.UserMessage)
        val process = items[1] as AgentConversationDisplayItem.Process
        assertEquals(AgentConversationTurnState.Completed, process.state)
        assertTrue(process.entries.single() is AgentConversationDisplayItem.Thought)
        val answer = items[2] as AgentConversationDisplayItem.AssistantText
        assertEquals("已完成", answer.text)
    }

    @Test
    fun `同一回合的中间说明不会切出第二个计时根节点`() {
        val source = listOf(
            AgentConversationItem.Message(
                id = "user",
                role = AgentMessageRole.User,
                content = listOf(AgentContent.Text("检查并写入文件")),
                turnOrdinal = 4L,
            ),
            AgentConversationItem.Message(
                id = "thought-before",
                role = AgentMessageRole.Thought,
                content = listOf(AgentContent.Text("先查看目录")),
                turnOrdinal = 4L,
            ),
            AgentConversationItem.Message(
                id = "progress",
                role = AgentMessageRole.Assistant,
                content = listOf(AgentContent.Text("当前目录为空，准备写入示例文件")),
                turnOrdinal = 4L,
            ),
            AgentConversationItem.Tool(
                id = "write-tool",
                call = AgentToolCall(id = "write-tool", title = "写入 hello.txt"),
                turnOrdinal = 4L,
            ),
            AgentConversationItem.Message(
                id = "thought-after",
                role = AgentMessageRole.Thought,
                content = listOf(AgentContent.Text("检查写入结果")),
                turnOrdinal = 4L,
            ),
            AgentConversationItem.Message(
                id = "answer",
                role = AgentMessageRole.Assistant,
                content = listOf(AgentContent.Text("完成，文件已经写入。")),
                turnOrdinal = 4L,
            ),
        )

        val items = AgentConversationPresentation.composeTurns(
            source,
            listOf(AgentConversationTurn(4L, AgentConversationTurnState.Completed, 1_000L, 13_000L)),
        )

        assertEquals(1, items.count { it is AgentConversationDisplayItem.Process })
        assertTrue(items[0] is AgentConversationDisplayItem.UserMessage)
        val process = items[1] as AgentConversationDisplayItem.Process
        assertEquals(12_000L, process.durationMillis)
        assertEquals(4, process.entries.size)
        assertEquals(
            "当前目录为空，准备写入示例文件",
            (process.entries[1] as AgentConversationDisplayItem.Thought).text,
        )
        val answer = items[2] as AgentConversationDisplayItem.AssistantText
        assertEquals("完成，文件已经写入。", answer.text)
    }

    @Test
    fun `未闭合代码围栏仍按代码块显示`() {
        val items = AgentConversationPresentation.project(
            listOf(
                AgentConversationItem.Message(
                    id = "answer",
                    role = AgentMessageRole.Assistant,
                    content = listOf(AgentContent.Text("```sh\necho ready"))
                )
            )
        )

        val code = items.single() as AgentConversationDisplayItem.Code
        assertEquals("sh", code.language)
        assertEquals("echo ready", code.code)
        assertTrue(code.startsAnswer)
    }

    @Test
    fun `图片和嵌入文件保持媒体语义而不是压成文本`() {
        val items = AgentConversationPresentation.project(
            listOf(
                AgentConversationItem.Message(
                    id = "media",
                    role = AgentMessageRole.Assistant,
                    content = listOf(
                        AgentContent.Image("aGVsbG8=", "image/png"),
                        AgentContent.EmbeddedBlob("AQID", "file:///workspace/result.pdf", "application/pdf"),
                        AgentContent.EmbeddedText("结果", "file:///workspace/result.txt", "text/plain"),
                        AgentContent.ResourceLink(
                            name = "report.pdf",
                            uri = "https://example.com/report.pdf",
                            mimeType = "application/pdf",
                            size = 2048
                        )
                    )
                )
            )
        )

        assertTrue(items[0] is AgentConversationDisplayItem.Image)
        val blob = items[1] as AgentConversationDisplayItem.Attachment
        assertEquals("result.pdf", blob.title)
        assertTrue(blob.source is AgentFileSource.InlineBase64)
        val text = items[2] as AgentConversationDisplayItem.Attachment
        assertTrue(text.source is AgentFileSource.InlineText)
        val link = items[3] as AgentConversationDisplayItem.Attachment
        assertEquals("application/pdf · 2.0 KB", link.detail)
        assertTrue(link.source is AgentFileSource.Link)
    }

    @Test
    fun `媒体边界先按编码长度拒绝超限数据并限制可委托协议`() {
        assertEquals(6L, AgentMediaPolicy.estimatedDecodedBytes("aGVsbG8="))
        assertTrue(AgentMediaPolicy.canDelegateUri("https://example.com/a.png"))
        assertTrue(AgentMediaPolicy.canDelegateUri("content://com.kite.app/a"))
        assertFalse(AgentMediaPolicy.canDelegateUri("file:///workspace/secret"))
        assertFalse(AgentMediaPolicy.canDelegateUri("javascript:alert(1)"))
    }
}
