package com.kite.app.platform.browser

import com.kite.app.browser.automation.BrowserAutomationEvent
import com.kite.app.browser.automation.BrowserAutomationEventKind
import com.kite.app.recipe.KiteRecipe
import com.kite.app.run.CardRunState
import com.kite.app.run.CardRunStatus
import com.kite.app.run.CardRunStore
import com.kite.app.run.CardRunSurface

/** 把自动浏览器事件投影回既有 CardRunStore，不持有第二份自动化状态。 */
internal class AndroidBrowserAutomationRunUpdater(
    private val recipeResolver: (String) -> KiteRecipe?
) {
    fun update(event: BrowserAutomationEvent): CardRunState? {
        val session = event.session
        val recipeId = session.recipeId?.takeIf { it.isNotBlank() } ?: return null
        val instanceId = session.instanceId?.takeIf { it.isNotBlank() } ?: return null
        val recipe = recipeResolver(recipeId) ?: CardRunStore.registeredRecipe(recipeId) ?: return null
        val existing = CardRunStore.get(instanceId)
        val fatal = event.kind == BrowserAutomationEventKind.Failed
        val actionFailed = event.actionResult?.succeeded == false
        return CardRunStore.update(
            recipe = recipe,
            status = status(event.kind, existing?.status),
            instanceId = instanceId,
            parentInstanceId = existing?.parentInstanceId,
            ownerKind = existing?.ownerKind,
            stepId = existing?.stepId,
            surface = if (fatal) CardRunSurface.Report else existing?.surface ?: CardRunSurface.Web,
            currentStepIndex = existing?.currentStepIndex,
            runId = existing?.runId,
            terminalSessionId = existing?.terminalSessionId,
            pid = existing?.pid,
            rootPid = existing?.rootPid,
            processGroupId = existing?.processGroupId,
            systemSessionId = existing?.systemSessionId,
            lastMeaningfulOutput = summary(event),
            lastError = if (fatal || actionFailed) event.message.take(500) else null,
            shellReportText = report(event)
        )
    }

    private fun status(kind: BrowserAutomationEventKind, current: CardRunStatus?): CardRunStatus =
        when (kind) {
            BrowserAutomationEventKind.SessionOpening -> when (current) {
                CardRunStatus.Starting,
                CardRunStatus.Running,
                CardRunStatus.WaitingTerminal,
                CardRunStatus.AlreadyRunning,
                CardRunStatus.Opened -> current
                else -> CardRunStatus.Starting
            }
            BrowserAutomationEventKind.SnapshotReady,
            BrowserAutomationEventKind.ActionFinished -> when (current) {
                CardRunStatus.Running,
                CardRunStatus.WaitingTerminal,
                CardRunStatus.AlreadyRunning -> current
                else -> CardRunStatus.Opened
            }
            BrowserAutomationEventKind.Failed -> when (current) {
                CardRunStatus.Running,
                CardRunStatus.WaitingTerminal,
                CardRunStatus.AlreadyRunning -> current
                else -> CardRunStatus.Failed
            }
        }

    private fun summary(event: BrowserAutomationEvent): String = when (event.kind) {
        BrowserAutomationEventKind.SessionOpening -> "自动浏览器正在打开页面"
        BrowserAutomationEventKind.SnapshotReady -> {
            val snapshot = event.snapshot
            val title = snapshot?.title?.takeIf { it.isNotBlank() } ?: snapshot?.url ?: event.session.url
            "自动浏览器已采集页面快照：$title"
        }
        BrowserAutomationEventKind.ActionFinished -> event.actionResult?.let { result ->
            if (result.succeeded) {
                "自动浏览器动作完成：${result.type.wireName}"
            } else {
                "自动浏览器动作失败：${result.errorCode ?: "unknown"}"
            }
        } ?: "自动浏览器动作没有返回结果"
        BrowserAutomationEventKind.Failed -> event.message.take(500)
    }

    private fun report(event: BrowserAutomationEvent): String {
        val session = event.session
        val snapshot = event.snapshot
        return buildString {
            appendLine("自动浏览器")
            appendLine("Session: ${session.sessionId}")
            appendLine("状态: ${session.status}")
            appendLine("URL: ${snapshot?.url ?: session.url}")
            if (!snapshot?.title.isNullOrBlank()) appendLine("标题: ${snapshot?.title}")
            if (!snapshot?.readyState.isNullOrBlank()) appendLine("DOM: ${snapshot?.readyState}")
            appendLine("消息: ${event.message}")
            if (!event.errorCode.isNullOrBlank()) appendLine("错误码: ${event.errorCode}")
            event.actionResult?.let { result ->
                appendLine()
                appendLine("动作结果")
                appendLine("Action: ${result.actionId}")
                appendLine("类型: ${result.type.wireName}")
                appendLine("结果: ${result.status}")
                appendLine("耗时: ${result.durationMs}ms")
                appendLine("匹配数量: ${result.matchedCount}")
                appendLine("消息: ${result.message}")
                if (!result.errorCode.isNullOrBlank()) appendLine("错误码: ${result.errorCode}")
                if (!result.errorDetail.isNullOrBlank()) appendLine("错误详情: ${result.errorDetail}")
            }
            if (snapshot != null) {
                appendLine()
                appendLine("页面摘要")
                appendLine(snapshot.text.ifBlank { "(页面没有可见文本)" }.take(1200))
                appendLine("元素摘要: ${snapshot.elementCount} 个 DOM 节点，采样 ${snapshot.elements.size} 个可交互元素")
            }
        }.take(4000)
    }
}
