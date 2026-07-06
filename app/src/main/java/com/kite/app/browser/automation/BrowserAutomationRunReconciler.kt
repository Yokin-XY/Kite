package com.kite.app.browser.automation

object BrowserAutomationRunReconciler {
    fun isRequestTimeout(result: BrowserAutomationActionResult): Boolean =
        result.status == BrowserAutomationResultStatus.TimedOut &&
            result.errorCode == REQUEST_TIMEOUT

    fun reconcileActionResult(
        result: BrowserAutomationActionResult,
        storedResult: BrowserAutomationActionResult?
    ): BrowserAutomationActionResult {
        if (!isRequestTimeout(result) || storedResult == null) return result
        if (storedResult.actionId != result.actionId) return result
        if (result.sessionId.isNotBlank() && storedResult.sessionId != result.sessionId) return result
        if (storedResult.completedAt < result.completedAt) return result
        if (isRequestTimeout(storedResult)) return result
        return storedResult
    }

    fun reconcileRunResult(
        runResult: BrowserAutomationRunResult,
        storedResultForAction: (BrowserAutomationActionResult) -> BrowserAutomationActionResult?
    ): BrowserAutomationRunResult {
        var changed = false
        val reconciledResults = runResult.results.map { result ->
            val reconciled = reconcileActionResult(result, storedResultForAction(result))
            if (reconciled != result) changed = true
            reconciled
        }
        if (!changed) return runResult

        val failed = reconciledResults.firstOrNull { !it.succeeded }
        val status = when {
            failed == null && reconciledResults.size == runResult.requestedCount -> BrowserAutomationResultStatus.Succeeded
            failed == null -> runResult.status
            failed.status == BrowserAutomationResultStatus.Rejected -> BrowserAutomationResultStatus.Rejected
            failed.status == BrowserAutomationResultStatus.TimedOut -> BrowserAutomationResultStatus.TimedOut
            else -> BrowserAutomationResultStatus.Failed
        }
        val latestCompletedAt = reconciledResults.maxOfOrNull { it.completedAt } ?: runResult.completedAt
        val completedAt = maxOf(runResult.completedAt, latestCompletedAt)
        val durationMs = runResult.durationMs + (completedAt - runResult.completedAt).coerceAtLeast(0L)

        return runResult.copy(
            status = status,
            durationMs = durationMs,
            results = reconciledResults,
            completedAt = completedAt
        )
    }

    private const val REQUEST_TIMEOUT = "request_timeout"
}
