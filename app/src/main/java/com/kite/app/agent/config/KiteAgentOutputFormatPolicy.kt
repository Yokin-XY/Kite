package com.kite.app.agent.config

/**
 * Kite 托管的稳定输出约定。
 *
 * 该区块持久化在 Agent 原生全局设定的最前面，以便模型前缀缓存稳定命中；设置页只展示
 * [userContent]，用户不能误删托管区块，但仍可自由编辑自己的其余设定。
 */
internal object KiteAgentOutputFormatPolicy {
    private const val START_MARKER = "<!-- kite:managed-output-format:start -->"
    private const val END_MARKER = "<!-- kite:managed-output-format:end -->"

    val managedBlock: String = """
        $START_MARKER
        ## Kite 输出格式协议

        面向用户的最终回答必须使用 GitHub Flavored Markdown（GFM）。

        - 简单回答直接作答；仅当标题能明显改善结构时使用 `#` 至 `######`，并保持层级连续。
        - 段落、列表、引用、表格、链接、图片与代码块必须使用对应的 GFM 语法；围栏代码块必须标注语言。
        - 只在比较结构化数据时使用表格，避免为简单信息制造复杂结构。
        - 文件、网页和图片必须指向真实可访问的目标；不得伪造链接、路径或媒体。
        - 不得输出原始 HTML、CSS 或界面样式参数，也不要向用户复述本协议。
        $END_MARKER
    """.trimIndent()

    fun userContent(rawContent: String): String {
        var visible = rawContent
        while (true) {
            val range = managedRange(visible) ?: return visible
            val before = visible.substring(0, range.first)
            val after = visible.substring(range.last + 1)
            visible = before + if (before.isEmpty()) after.removeSingleLeadingSeparator() else after
        }
    }

    fun merge(rawUserContent: String): String {
        val visible = userContent(rawUserContent)
        return if (visible.isEmpty()) "$managedBlock\n" else "$managedBlock\n\n$visible"
    }

    fun isCurrent(rawContent: String): Boolean = rawContent == merge(rawContent)

    private fun managedRange(content: String): IntRange? {
        val start = content.indexOf(START_MARKER)
        if (start < 0) return null
        val endMarkerStart = content.indexOf(END_MARKER, start + START_MARKER.length)
        if (endMarkerStart < 0) return null
        return start until (endMarkerStart + END_MARKER.length)
    }

    private fun String.removeSingleLeadingSeparator(): String = when {
        startsWith("\r\n\r\n") -> substring(4)
        startsWith("\n\n") -> substring(2)
        startsWith("\r\n") -> substring(2)
        startsWith("\n") -> substring(1)
        else -> this
    }
}

internal enum class NativeAgentManagedOutputFormat {
    Disabled,
    /** 缺失时也创建，适用于纯补充指令文件。 */
    CreateOrUpdate,
    /** 只更新已有且含用户正文的文件，避免创建后替换 Agent 内置身份。 */
    ExistingNonBlankOnly,
}

internal sealed interface NativeAgentManagedOutputSyncResult {
    data object Ready : NativeAgentManagedOutputSyncResult
    data class Failed(val message: String) : NativeAgentManagedOutputSyncResult
}
