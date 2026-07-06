package com.kite.app.browser.automation

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BrowserAutomationSessionStoreTest {
    private val context by lazy {
        ApplicationProvider.getApplicationContext<Context>()
    }

    @Before
    fun clearPrefs() {
        context.getSharedPreferences("kite_browser_automation", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun startSessionRedactsSensitiveUrlBeforePersisting() {
        val store = BrowserAutomationSessionStore(context)
        val secret = "secret-token"

        val session = store.startSession(
            recipeId = "recipe",
            recipeName = "Recipe",
            instanceId = "instance",
            source = "card_run_surface",
            url = "http://127.0.0.1:8791/callback?token=$secret&name=demo",
            now = 1_000L
        )

        assertEquals(BrowserAutomationSessionStatus.Opening, session.status)
        assertFalse(session.url.contains(secret))
        assertTrue(session.url.contains("token=present"))
        val persisted = context.getSharedPreferences("kite_browser_automation", Context.MODE_PRIVATE)
            .getString("sessions_v1", "")
            .orEmpty()
        assertFalse(persisted.contains(secret))
        assertTrue(persisted.contains("token=present"))
    }

    @Test
    fun startSessionReusesLiveInstanceSession() {
        val store = BrowserAutomationSessionStore(context)
        val first = store.startSession(
            recipeId = "recipe",
            recipeName = "Recipe",
            instanceId = "instance",
            source = "card_run_surface",
            url = "http://127.0.0.1:8791/one",
            now = 1_000L
        )

        val second = store.startSession(
            recipeId = "recipe",
            recipeName = "Recipe",
            instanceId = "instance",
            source = "card_run_surface",
            url = "http://127.0.0.1:8791/two",
            now = 2_000L
        )

        assertEquals(first.sessionId, second.sessionId)
        assertEquals("http://127.0.0.1:8791/two", second.url)
        assertEquals(1, store.allSessions().size)
    }

    @Test
    fun recentSessionsFiltersSortsLimitsAndKeepsUrlsRedacted() {
        val store = BrowserAutomationSessionStore(context)
        val first = store.startSession(
            recipeId = "recipe",
            recipeName = "First",
            instanceId = "one",
            source = "card_run_surface",
            url = "http://127.0.0.1:8791/one?token=secret-token",
            now = 1_000L
        )
        val second = store.startSession(
            recipeId = "recipe",
            recipeName = "Second",
            instanceId = "two",
            source = "card_run_surface",
            url = "http://127.0.0.1:8791/two?password=plain-text",
            now = 2_000L
        )
        val third = store.startSession(
            recipeId = "recipe",
            recipeName = "Third",
            instanceId = "three",
            source = "card_run_surface",
            url = "http://127.0.0.1:8791/three",
            now = 3_000L
        )
        store.close(first.sessionId, now = 3_500L)

        val openSessions = store.recentSessions()
        val limited = store.recentSessions(limit = 1)
        val withClosed = store.recentSessions(includeClosed = true)
        val secondOnly = store.recentSessions(instanceId = "two")
        val persisted = context.getSharedPreferences("kite_browser_automation", Context.MODE_PRIVATE)
            .getString("sessions_v1", "")
            .orEmpty()

        assertEquals(listOf(third.sessionId, second.sessionId), openSessions.map { it.sessionId })
        assertEquals(listOf(third.sessionId), limited.map { it.sessionId })
        assertEquals(
            listOf(first.sessionId, third.sessionId, second.sessionId),
            withClosed.map { it.sessionId }
        )
        assertEquals(BrowserAutomationSessionStatus.Closed, withClosed.first().status)
        assertEquals(listOf(second.sessionId), secondOnly.map { it.sessionId })
        assertFalse(persisted.contains("secret-token"))
        assertFalse(persisted.contains("plain-text"))
        assertTrue(persisted.contains("token=present"))
        assertTrue(persisted.contains("password=present"))
    }

    @Test
    fun markReadyStoresSnapshotAndClearsError() {
        val store = BrowserAutomationSessionStore(context)
        val session = store.startSession(
            recipeId = "recipe",
            recipeName = "Recipe",
            instanceId = "instance",
            source = "card_run_surface",
            url = "http://127.0.0.1:8791",
            now = 1_000L
        )
        store.markFailed(session.sessionId, "temporary", now = 1_100L)
        val snapshot = BrowserAutomationSnapshot(
            snapshotId = "snapshot",
            sessionId = session.sessionId,
            url = "http://127.0.0.1:8791/home",
            title = "Home",
            readyState = "complete",
            text = "Hello",
            elementCount = 3,
            elements = emptyList(),
            accessibility = listOf(
                BrowserAutomationAccessibilityNode(
                    index = 0,
                    role = "button",
                    name = "Apply greeting",
                    tag = "button",
                    type = "button",
                    level = null,
                    visible = true,
                    enabled = true,
                    checked = null,
                    selected = null,
                    expanded = null,
                    x = 10.0,
                    y = 20.0,
                    width = 120.0,
                    height = 40.0
                )
            ),
            capturedAt = 1_200L
        )

        val ready = store.markReady(session.sessionId, snapshot, now = 1_300L)

        assertNotNull(ready)
        assertEquals(BrowserAutomationSessionStatus.Ready, ready?.status)
        assertEquals("snapshot", ready?.lastSnapshotId)
        assertNull(ready?.lastError)
        assertEquals(snapshot, store.latestSnapshot(session.sessionId))
        assertEquals("button", store.latestSnapshot(session.sessionId)?.accessibility?.single()?.role)
        assertEquals("Apply greeting", store.latestSnapshot(session.sessionId)?.accessibility?.single()?.name)
    }

    @Test
    fun markActionResultPersistsLatestResultAndLastActionId() {
        val store = BrowserAutomationSessionStore(context)
        val session = store.startSession(
            recipeId = "recipe",
            recipeName = "Recipe",
            instanceId = "instance",
            source = "card_run_surface",
            url = "http://127.0.0.1:8791",
            now = 1_000L
        )
        val action = BrowserAutomationAction(
            actionId = "action",
            sessionId = session.sessionId,
            instanceId = null,
            type = BrowserAutomationActionType.Click,
            target = BrowserAutomationTarget(BrowserAutomationTargetKind.Text, "Apply"),
            value = null,
            timeoutMs = 1_000L
        )
        store.markActionRunning(session.sessionId, action, now = 1_100L)
        val result = BrowserAutomationActionResult(
            actionId = action.actionId,
            sessionId = session.sessionId,
            type = BrowserAutomationActionType.Click,
            status = BrowserAutomationResultStatus.Succeeded,
            durationMs = 12L,
            url = "http://127.0.0.1:8791/done",
            title = "Done",
            message = "clicked",
            matchedCount = 1,
            completedAt = 1_200L
        )

        val ready = store.markActionResult(result, now = 1_300L)

        assertNotNull(ready)
        assertEquals(BrowserAutomationSessionStatus.Ready, ready?.status)
        assertEquals("action", ready?.lastActionId)
        assertEquals(result, store.latestResult(session.sessionId))
    }

    @Test
    fun recentResultsReturnsActionHistoryNewestFirst() {
        val store = BrowserAutomationSessionStore(context)
        val session = store.startSession(
            recipeId = "recipe",
            recipeName = "Recipe",
            instanceId = "instance",
            source = "card_run_surface",
            url = "http://127.0.0.1:8791",
            now = 1_000L
        )
        val first = BrowserAutomationActionResult(
            actionId = "first",
            sessionId = session.sessionId,
            type = BrowserAutomationActionType.Click,
            status = BrowserAutomationResultStatus.Succeeded,
            durationMs = 10L,
            url = "http://127.0.0.1:8791/first",
            title = "First",
            message = "clicked",
            completedAt = 1_100L
        )
        val second = first.copy(
            actionId = "second",
            type = BrowserAutomationActionType.Scroll,
            url = "http://127.0.0.1:8791/second",
            title = "Second",
            message = "scrolled",
            completedAt = 1_200L
        )

        store.markActionResult(first, now = 1_110L)
        store.markActionResult(second, now = 1_210L)

        val results = store.recentResults(session.sessionId)

        assertEquals(listOf("second", "first"), results.map { it.actionId })
    }

    @Test
    fun markActionResultPersistsArtifactPathAndSnapshotId() {
        val store = BrowserAutomationSessionStore(context)
        val session = store.startSession(
            recipeId = "recipe",
            recipeName = "Recipe",
            instanceId = "instance",
            source = "card_run_surface",
            url = "http://127.0.0.1:8791",
            now = 1_000L
        )
        val result = BrowserAutomationActionResult(
            actionId = "shot",
            sessionId = session.sessionId,
            type = BrowserAutomationActionType.Screenshot,
            status = BrowserAutomationResultStatus.Succeeded,
            durationMs = 40L,
            url = "http://127.0.0.1:8791/test",
            title = "Test",
            message = "screenshot captured",
            snapshotId = "snapshot-after-action",
            artifactPath = "/data/user/0/com.kite.app/files/browser-automation/screenshots/shot.png",
            completedAt = 1_200L
        )

        val ready = store.markActionResult(result, now = 1_300L)

        assertEquals("snapshot-after-action", ready?.lastSnapshotId)
        assertEquals(result.artifactPath, store.latestResult(session.sessionId)?.artifactPath)
        val json = store.latestResult(session.sessionId)?.toJson()
        checkNotNull(json)
        assertEquals(result.artifactPath, json.getString("artifactPath"))
        assertTrue(json.getString("artifactUrl").startsWith("/browser-automation/artifact?path="))
        assertTrue(json.getString("artifactUrl").contains("%2Fbrowser-automation%2Fscreenshots%2Fshot.png"))
    }

    @Test
    fun consoleEntriesAreQueryablePerSession() {
        val store = BrowserAutomationSessionStore(context)
        val first = store.startSession(
            recipeId = "recipe",
            recipeName = "Recipe",
            instanceId = "one",
            source = "card_run_surface",
            url = "http://127.0.0.1:8791/one",
            now = 1_000L
        )
        val second = store.startSession(
            recipeId = "recipe",
            recipeName = "Recipe",
            instanceId = "two",
            source = "card_run_surface",
            url = "http://127.0.0.1:8791/two",
            now = 1_100L
        )

        store.saveConsoleEntry(
            BrowserAutomationConsoleEntry(
                entryId = "first-log",
                sessionId = first.sessionId,
                level = "LOG",
                message = "automation:greeting:Kite",
                sourceId = "http://127.0.0.1:8791/browser-automation/test-page",
                lineNumber = 42,
                capturedAt = 1_200L
            )
        )
        store.saveConsoleEntry(
            BrowserAutomationConsoleEntry(
                entryId = "second-log",
                sessionId = second.sessionId,
                level = "LOG",
                message = "other",
                sourceId = null,
                lineNumber = 1,
                capturedAt = 1_300L
            )
        )

        val entries = store.recentConsoleEntries(first.sessionId)

        assertEquals(1, entries.size)
        assertEquals("automation:greeting:Kite", entries.single().message)
        assertEquals(42, entries.single().lineNumber)
    }

    @Test
    fun networkEntriesAreQueryablePerSessionAndRedacted() {
        val store = BrowserAutomationSessionStore(context)
        val first = store.startSession(
            recipeId = "recipe",
            recipeName = "Recipe",
            instanceId = "one",
            source = "card_run_surface",
            url = "http://127.0.0.1:8791/one",
            now = 1_000L
        )
        val second = store.startSession(
            recipeId = "recipe",
            recipeName = "Recipe",
            instanceId = "two",
            source = "card_run_surface",
            url = "http://127.0.0.1:8791/two",
            now = 1_100L
        )

        store.saveNetworkEntry(
            BrowserAutomationNetworkEntry(
                entryId = "first-network",
                sessionId = first.sessionId,
                kind = "request",
                method = "GET",
                url = "http://127.0.0.1:8791/status?token=secret&automationNetwork=A6",
                isForMainFrame = false,
                capturedAt = 1_200L
            )
        )
        store.saveNetworkEntry(
            BrowserAutomationNetworkEntry(
                entryId = "second-network",
                sessionId = second.sessionId,
                kind = "request",
                method = "POST",
                url = "http://127.0.0.1:8791/other",
                isForMainFrame = true,
                capturedAt = 1_300L
            )
        )

        val entries = store.recentNetworkEntries(first.sessionId)

        assertEquals(1, entries.size)
        assertEquals("GET", entries.single().method)
        assertFalse(entries.single().url.contains("secret"))
        assertTrue(entries.single().url.contains("token=present"))
        assertTrue(entries.single().url.contains("automationNetwork=A6"))
    }

    @Test
    fun runResultsAreQueryablePerSessionAndRedacted() {
        val store = BrowserAutomationSessionStore(context)
        val first = store.startSession(
            recipeId = "recipe",
            recipeName = "Recipe",
            instanceId = "one",
            source = "card_run_surface",
            url = "http://127.0.0.1:8791/one",
            now = 1_000L
        )
        val second = store.startSession(
            recipeId = "recipe",
            recipeName = "Recipe",
            instanceId = "two",
            source = "card_run_surface",
            url = "http://127.0.0.1:8791/two",
            now = 1_100L
        )
        val firstAction = BrowserAutomationActionResult(
            actionId = "first-action",
            sessionId = first.sessionId,
            type = BrowserAutomationActionType.Click,
            status = BrowserAutomationResultStatus.Succeeded,
            durationMs = 12L,
            url = "http://127.0.0.1:8791/done?token=secret-token",
            title = "Done",
            message = "authorization: Bearer abc.secret",
            matchedCount = 1,
            completedAt = 1_200L
        )
        val secondAction = BrowserAutomationActionResult(
            actionId = "second-action",
            sessionId = second.sessionId,
            type = BrowserAutomationActionType.Click,
            status = BrowserAutomationResultStatus.Failed,
            durationMs = 13L,
            url = "http://127.0.0.1:8791/fail?password=plain-text",
            title = "Fail",
            message = "target not found",
            errorCode = "target_not_found",
            errorDetail = "password=plain-text",
            completedAt = 1_300L
        )

        val firstRun = BrowserAutomationRunResult(
            runId = "first-run",
            sessionId = first.sessionId,
            status = BrowserAutomationResultStatus.Succeeded,
            durationMs = 22L,
            requestedCount = 1,
            completedCount = 1,
            stoppedOnFailure = false,
            results = listOf(firstAction),
            completedAt = 1_250L
        )
        val secondRun = BrowserAutomationRunResult(
            runId = "second-run",
            sessionId = second.sessionId,
            status = BrowserAutomationResultStatus.Failed,
            durationMs = 24L,
            requestedCount = 1,
            completedCount = 1,
            stoppedOnFailure = true,
            results = listOf(secondAction),
            completedAt = 1_350L
        )

        store.saveRunResult(firstRun)
        store.saveRunResult(secondRun)

        val firstSessionRuns = store.recentRuns(first.sessionId)
        val persisted = context.getSharedPreferences("kite_browser_automation", Context.MODE_PRIVATE)
            .getString("runs_v1", "")
            .orEmpty()

        assertEquals("first-run", store.getRun("first-run")?.runId)
        assertNull(store.getRun("missing-run"))
        assertEquals(listOf("first-run"), firstSessionRuns.map { it.runId })
        assertEquals(listOf("second-run", "first-run"), store.recentRuns().map { it.runId })
        assertFalse(persisted.contains("secret-token"))
        assertFalse(persisted.contains("abc.secret"))
        assertFalse(persisted.contains("plain-text"))
        assertTrue(persisted.contains("token=present"))
        assertTrue(persisted.contains("authorization: present"))
        assertTrue(persisted.contains("password=present"))
    }

    @Test
    fun runResultReconcilesLateActionSuccessForSameActionId() {
        val store = BrowserAutomationSessionStore(context)
        val session = store.startSession(
            recipeId = "recipe",
            recipeName = "Recipe",
            instanceId = "instance",
            source = "card_run_surface",
            url = "http://127.0.0.1:8791/browser-automation/test-page",
            now = 1_000L
        )
        val timeoutResult = BrowserAutomationActionResult(
            actionId = "late-action",
            sessionId = session.sessionId,
            type = BrowserAutomationActionType.WaitFor,
            status = BrowserAutomationResultStatus.TimedOut,
            durationMs = 1_000L,
            url = "http://127.0.0.1:8791/browser-automation/test-page",
            title = "Kite Automation Test",
            message = "request timed out",
            errorCode = "request_timeout",
            errorDetail = "No result before request timeout",
            completedAt = 2_000L
        )
        store.saveRunResult(
            BrowserAutomationRunResult(
                runId = "late-run",
                sessionId = session.sessionId,
                status = BrowserAutomationResultStatus.TimedOut,
                durationMs = 1_000L,
                requestedCount = 1,
                completedCount = 1,
                stoppedOnFailure = false,
                results = listOf(timeoutResult),
                completedAt = 2_000L
            )
        )
        val successResult = timeoutResult.copy(
            status = BrowserAutomationResultStatus.Succeeded,
            durationMs = 1_100L,
            message = "state domReady readyState=complete",
            errorCode = null,
            errorDetail = null,
            completedAt = 2_250L
        )

        store.markActionResult(successResult, now = 2_300L)

        val reconciledById = checkNotNull(store.getRun("late-run"))
        val reconciledRecent = store.recentRuns(session.sessionId, limit = 1).single()
        val observe = BrowserAutomationObservation.toJson(
            session = checkNotNull(store.get(session.sessionId)),
            snapshot = null,
            recentAction = store.latestResult(session.sessionId),
            recentRun = reconciledRecent
        )

        assertEquals(BrowserAutomationResultStatus.Succeeded, reconciledById.status)
        assertEquals(BrowserAutomationResultStatus.Succeeded, reconciledById.results.single().status)
        assertEquals("state domReady readyState=complete", reconciledById.results.single().message)
        assertEquals(2_250L, reconciledById.completedAt)
        assertEquals(BrowserAutomationResultStatus.Succeeded, reconciledRecent.status)
        assertEquals("Succeeded", observe.getJSONObject("recentRun").getString("status"))
        assertEquals("", observe.getJSONObject("recentRun").getString("errorCode"))
    }

    @Test
    fun runResultKeepsRealRequestTimeoutWithoutLateActionResult() {
        val store = BrowserAutomationSessionStore(context)
        val session = store.startSession(
            recipeId = "recipe",
            recipeName = "Recipe",
            instanceId = "instance",
            source = "card_run_surface",
            url = "http://127.0.0.1:8791/browser-automation/test-page",
            now = 1_000L
        )
        val timeoutResult = BrowserAutomationActionResult(
            actionId = "real-timeout-action",
            sessionId = session.sessionId,
            type = BrowserAutomationActionType.WaitFor,
            status = BrowserAutomationResultStatus.TimedOut,
            durationMs = 1_000L,
            url = "http://127.0.0.1:8791/browser-automation/test-page",
            title = "Kite Automation Test",
            message = "request timed out",
            errorCode = "request_timeout",
            errorDetail = "No result before request timeout",
            completedAt = 2_000L
        )

        store.saveRunResult(
            BrowserAutomationRunResult(
                runId = "real-timeout-run",
                sessionId = session.sessionId,
                status = BrowserAutomationResultStatus.TimedOut,
                durationMs = 1_000L,
                requestedCount = 1,
                completedCount = 1,
                stoppedOnFailure = false,
                results = listOf(timeoutResult),
                completedAt = 2_000L
            )
        )

        val persisted = checkNotNull(store.getRun("real-timeout-run"))

        assertEquals(BrowserAutomationResultStatus.TimedOut, persisted.status)
        assertEquals(BrowserAutomationResultStatus.TimedOut, persisted.results.single().status)
        assertEquals("request_timeout", persisted.results.single().errorCode)
        assertEquals("request_timeout", persisted.toJson().getString("errorCode"))
    }
}
