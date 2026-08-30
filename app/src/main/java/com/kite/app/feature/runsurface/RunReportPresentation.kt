package com.kite.app.feature.runsurface

import com.kite.app.recipe.KiteRecipe
import com.kite.app.resources.KiteResourceInstallOutput
import com.kite.app.run.CardRunState
import com.kite.app.run.CardRunStatus

internal data class RunReportInsight(
    val marker: String,
    val title: String,
    val detail: String,
    val tone: RunReportInsightTone
)

internal enum class RunReportInsightTone {
    Warning,
    Danger
}

internal object RunReportPresenter {
    fun project(recipe: KiteRecipe, state: CardRunState): RunSurfaceContent.Report {
        val hint = commandHint(state)
        val output = buildString {
            append(outputText(state))
            hint?.let { append("\n\n提示：").append(it) }
        }.trim()
        return RunSurfaceContent.Report(
            outputText = output.ifBlank { "暂无输出。" },
            currentCommand = currentCommand(recipe, state),
            fullCommand = fullCommand(recipe, state),
            commandHint = hint,
            insight = failureInsight(recipe, state),
            failed = state.failureSummary() != null
        )
    }

    fun isLive(status: CardRunStatus): Boolean =
        status == CardRunStatus.Starting ||
            status == CardRunStatus.Running ||
            status == CardRunStatus.WaitingTerminal ||
            status == CardRunStatus.AlreadyRunning ||
            status == CardRunStatus.Opened ||
            status == CardRunStatus.Stopping

    fun elapsedLabel(state: RunSurfaceUiState, now: Long = System.currentTimeMillis()): String {
        val endAt = if (isLive(state.status)) now else state.updatedAt
        val seconds = ((endAt - state.createdAt).coerceAtLeast(0L) / 1000L)
            .coerceAtMost(99L * 60L + 59L)
        return String.format("%02d:%02d", seconds / 60L, seconds % 60L)
    }

    fun footerLabel(state: RunSurfaceUiState, now: Long = System.currentTimeMillis()): String {
        val elapsed = elapsedLabel(state, now)
        return when {
            (state.content as? RunSurfaceContent.Report)?.failed == true -> "执行失败 · $elapsed"
            isLive(state.status) -> "正在执行 · $elapsed"
            state.status == CardRunStatus.Completed -> "已完成 · $elapsed"
            state.status == CardRunStatus.Stopped -> "已停止 · $elapsed"
            else -> state.statusLabel
        }
    }

    private fun currentCommand(recipe: KiteRecipe, state: CardRunState): String {
        val stepCommand = recipe.steps.getOrNull(state.currentStepIndex)?.cmd.orEmpty().trim()
        if (stepCommand.isNotBlank()) {
            return stepCommand.lineSequence().firstOrNull().orEmpty().ifBlank { stepCommand }
        }
        return state.shellReportText.orEmpty()
            .lineSequence()
            .firstOrNull { it.startsWith("命令：") }
            ?.removePrefix("命令：")
            ?.trim()
            .orEmpty()
    }

    private fun fullCommand(recipe: KiteRecipe, state: CardRunState): String =
        recipe.steps.getOrNull(state.currentStepIndex)?.cmd.orEmpty().trim()
            .ifBlank { currentCommand(recipe, state) }

    private fun outputText(state: CardRunState): String {
        val report = state.shellReportText.orEmpty().trim()
        val output = extractShellOutput(report)
        return when {
            output.isNotBlank() -> output
            !state.lastError.isNullOrBlank() -> state.lastError
            !state.lastMeaningfulOutput.isNullOrBlank() -> state.lastMeaningfulOutput
            else -> "暂无输出。一次性命令请使用“等待结束”，例如 python3 -V。"
        }.orEmpty().let(KiteResourceInstallOutput::userVisibleReport)
    }

    private fun extractShellOutput(report: String): String {
        listOf("原始输出：", "有效输出：", "错误输出：", "输出：").forEach { marker ->
            val index = report.indexOf(marker)
            if (index >= 0) return report.substring(index + marker.length).trim()
        }
        return report.lineSequence()
            .filterNot { line ->
                line.startsWith("命令：") ||
                    line.startsWith("结果：") ||
                    line.startsWith("退出码：") ||
                    line.startsWith("匹配：")
            }
            .joinToString("\n")
            .trim()
    }

    private fun commandHint(state: CardRunState): String? {
        if (state.status != CardRunStatus.Failed && state.status != CardRunStatus.BridgeUnavailable) return null
        val text = listOfNotNull(state.lastError, state.lastMeaningfulOutput, state.shellReportText).joinToString("\n")
        val missingCommand = Regex("""(?:^|\n).*?:\s*([A-Za-z0-9_.+-]+): command not found""")
            .find(text)
            ?.groupValues
            ?.getOrNull(1)
        val timeoutSignal = Regex(
            """(?i)(?:\btimed\s+out\b|\btimedOut\s*=\s*true\b|命令超时|(?:^|[\s:=])timeout(?:$|[\s.,;]))"""
        ).containsMatchIn(text)
        return when {
            text.contains("python: command not found", ignoreCase = true) ->
                "当前环境没有 python 这个别名。先试 python3 -V；如果以后想直接用 python，可以再补一个别名。"
            text.contains("python3: command not found", ignoreCase = true) ->
                "当前环境没有 Python 3，需要先安装 Python。"
            missingCommand != null ->
                "没有找到命令：$missingCommand。一般是还没安装、命令名写错，或者当前环境的 PATH 没包含它。"
            text.contains("Permission denied", ignoreCase = true) ->
                "权限不足，或者这个文件还没有执行权限。"
            text.contains("No such file or directory", ignoreCase = true) ->
                "路径或文件不存在，先检查命令里的目录和文件名。"
            timeoutSignal ->
                "命令超时，可能还在等待输入、网络、服务启动，或者命令本身卡住了。"
            else -> null
        }
    }

    private fun failureInsight(recipe: KiteRecipe, state: CardRunState): RunReportInsight? {
        if (state.failureSummary() == null) return null
        val text = listOfNotNull(state.lastError, state.lastMeaningfulOutput, state.shellReportText)
            .joinToString("\n")
        if (text.isBlank()) return null
        return when {
            text.contains("terminated with signal 9", ignoreCase = true) ||
                text.contains("signal 9", ignoreCase = true) ||
                Regex("""\bKilled\b""", RegexOption.IGNORE_CASE).containsMatchIn(text) ->
                RunReportInsight(
                    marker = "杀",
                    title = "不像网络错误，更像进程被系统强制结束",
                    detail = "日志里出现 signal 9。通常是内存或资源压力、PRoot 进程被 Android 杀掉，或安装阶段启动了过重的子进程。可以先关闭其他实例后重试。",
                    tone = RunReportInsightTone.Warning
                )
            isNetworkFailureText(text) ->
                RunReportInsight(
                    marker = "网",
                    title = "可能是网络或上游源不可达",
                    detail = networkFailureDetailFor(recipe),
                    tone = RunReportInsightTone.Warning
                )
            text.contains("No space left on device", ignoreCase = true) ->
                RunReportInsight(
                    marker = "存",
                    title = "存储空间不足",
                    detail = "安装目录或缓存目录空间不够。清理资源缓存、旧安装目录或释放手机存储后再重试。",
                    tone = RunReportInsightTone.Warning
                )
            else -> null
        }
    }

    private fun isNetworkFailureText(text: String): Boolean = listOf(
        "ENOTFOUND",
        "ECONNRESET",
        "ECONNREFUSED",
        "ETIMEDOUT",
        "network timeout",
        "Connection timed out",
        "Temporary failure in name resolution",
        "Could not resolve host",
        "Failed to connect",
        "SSL certificate problem",
        "npm ERR!",
        "pip._vendor",
        "ReadTimeout",
        "HTTPError 403",
        "HTTPError 404"
    ).any { text.contains(it, ignoreCase = true) }

    private fun networkFailureDetailFor(recipe: KiteRecipe): String = when {
        recipe.id.contains("kite.hermes.core") ->
            "Hermes 需要访问官方安装脚本、GitHub、PyPI 和 files.pythonhosted.org。请确认当前网络或代理能访问这些域名。"
        recipe.id.contains("kite.hermes.webui") ->
            "Hermes WebUI 主要需要访问 registry.npmjs.org；如果安装浏览器工具，还可能访问 GitHub 或 CDN。"
        listOf("kite.git", "kite.curl", "kite.python").any(recipe.id::contains) ->
            "这个资源通过 Ubuntu apt 安装，需要容器能访问当前 apt 软件源。源慢或 DNS 不通时会失败。"
        else -> "请检查代理、DNS、证书和上游下载地址。"
    }
}
