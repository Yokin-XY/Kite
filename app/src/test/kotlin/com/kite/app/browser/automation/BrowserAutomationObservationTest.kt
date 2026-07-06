package com.kite.app.browser.automation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BrowserAutomationObservationTest {
    @Test
    fun observationReturnsAgentFriendlyTargetsAndRedactsSensitiveText() {
        val session = BrowserAutomationSession(
            sessionId = "session",
            recipeId = "recipe",
            recipeName = "Recipe",
            instanceId = "instance",
            source = "card_run_surface",
            url = "http://127.0.0.1:8791/page?token=session-secret",
            status = BrowserAutomationSessionStatus.Ready,
            createdAt = 1_000L,
            updatedAt = 1_500L,
            lastActionId = "action",
            lastSnapshotId = "snapshot"
        )
        val snapshot = BrowserAutomationSnapshot(
            snapshotId = "snapshot",
            sessionId = session.sessionId,
            url = "http://127.0.0.1:8791/page?token=snapshot-secret",
            title = "Demo",
            readyState = "complete",
            text = "Visible page token=snapshot-secret",
            elementCount = 6,
            elements = emptyList(),
            accessibility = listOf(
                BrowserAutomationAccessibilityNode(
                    index = 0,
                    role = "textbox",
                    name = "Name",
                    tag = "input",
                    type = "text",
                    level = null,
                    visible = true,
                    enabled = true,
                    checked = null,
                    selected = null,
                    expanded = null,
                    x = 1.0,
                    y = 2.0,
                    width = 100.0,
                    height = 24.0
                ),
                BrowserAutomationAccessibilityNode(
                    index = 1,
                    role = "combobox",
                    name = "Tone",
                    tag = "select",
                    type = null,
                    level = null,
                    visible = true,
                    enabled = true,
                    checked = null,
                    selected = null,
                    expanded = null,
                    x = 1.0,
                    y = 40.0,
                    width = 140.0,
                    height = 36.0
                ),
                BrowserAutomationAccessibilityNode(
                    index = 2,
                    role = "checkbox",
                    name = "Subscribe updates",
                    tag = "input",
                    type = "checkbox",
                    level = null,
                    visible = true,
                    enabled = true,
                    checked = "false",
                    selected = null,
                    expanded = null,
                    x = 1.0,
                    y = 86.0,
                    width = 18.0,
                    height = 18.0
                ),
                BrowserAutomationAccessibilityNode(
                    index = 3,
                    role = "button",
                    name = "Apply greeting",
                    tag = "button",
                    type = null,
                    level = null,
                    visible = true,
                    enabled = true,
                    checked = null,
                    selected = null,
                    expanded = null,
                    x = 1.0,
                    y = 118.0,
                    width = 140.0,
                    height = 36.0
                ),
                BrowserAutomationAccessibilityNode(
                    index = 4,
                    role = "button",
                    name = "Apply frame greeting",
                    tag = "button",
                    type = null,
                    level = null,
                    visible = true,
                    enabled = true,
                    checked = null,
                    selected = null,
                    expanded = null,
                    x = 10.0,
                    y = 220.0,
                    width = 160.0,
                    height = 40.0,
                    framePath = "top/frame[0]",
                    frameUrl = "http://127.0.0.1:8791/browser-automation/test-frame?token=frame-secret",
                    frameName = "Same origin automation frame",
                    frameAccessible = true
                ),
                BrowserAutomationAccessibilityNode(
                    index = 5,
                    role = "iframe",
                    name = "Sandboxed blocked frame",
                    tag = "iframe",
                    type = null,
                    level = null,
                    visible = true,
                    enabled = true,
                    checked = null,
                    selected = null,
                    expanded = null,
                    x = 0.0,
                    y = 280.0,
                    width = 320.0,
                    height = 160.0,
                    framePath = "top/frame[1]",
                    frameUrl = "",
                    frameName = "Sandboxed blocked frame",
                    frameAccessible = false
                ),
                BrowserAutomationAccessibilityNode(
                    index = 6,
                    role = "button",
                    name = "Apply shadow greeting",
                    tag = "button",
                    type = null,
                    level = null,
                    visible = true,
                    enabled = true,
                    checked = null,
                    selected = null,
                    expanded = null,
                    x = 12.0,
                    y = 460.0,
                    width = 170.0,
                    height = 40.0,
                    shadowPath = "/shadow[4]",
                    shadowHost = "open-shadow-widget #open-shadow-widget"
                ),
                BrowserAutomationAccessibilityNode(
                    index = 7,
                    role = "button",
                    name = "hidden-token-should-not-leak",
                    tag = "button",
                    type = null,
                    level = null,
                    visible = false,
                    enabled = true,
                    checked = null,
                    selected = null,
                    expanded = null,
                    x = 0.0,
                    y = 0.0,
                    width = 0.0,
                    height = 0.0
                )
            ),
            capturedAt = 1_600L
        )
        val recentAction = BrowserAutomationActionResult(
            actionId = "action",
            sessionId = session.sessionId,
            type = BrowserAutomationActionType.Click,
            status = BrowserAutomationResultStatus.Failed,
            durationMs = 12L,
            url = snapshot.url,
            title = "Demo",
            message = "authorization: Bearer action-secret",
            snapshotId = "snapshot-action",
            artifactPath = "/data/user/0/com.kite.app/files/browser-automation/screenshots/observe-shot.png",
            errorCode = "target_not_found",
            errorDetail = "password=plain-secret",
            completedAt = 1_700L
        )
        val recentRun = BrowserAutomationRunResult(
            runId = "run",
            sessionId = session.sessionId,
            status = BrowserAutomationResultStatus.Failed,
            durationMs = 20L,
            requestedCount = 1,
            completedCount = 1,
            stoppedOnFailure = true,
            results = listOf(recentAction),
            completedAt = 1_800L
        )

        val observation = BrowserAutomationObservation.toJson(
            session = session,
            snapshot = snapshot,
            recentAction = recentAction,
            recentRun = recentRun
        )
        val raw = observation.toString()
        val interactive = observation.getJSONArray("interactive")
        val textbox = interactive.getJSONObject(0)
        val combobox = interactive.getJSONObject(1)
        val checkbox = interactive.getJSONObject(2)
        val button = interactive.getJSONObject(3)
        val frameButton = interactive.getJSONObject(4)
        val blockedFrame = interactive.getJSONObject(5)
        val shadowButton = interactive.getJSONObject(6)

        assertEquals(7, interactive.length())
        assertEquals("textbox", textbox.getJSONObject("suggestedTarget").getString("value"))
        assertEquals("Name", textbox.getJSONObject("suggestedTarget").getString("name"))
        assertEquals(0, textbox.getJSONObject("suggestedTarget").getInt("index"))
        assertEquals("type", textbox.getJSONArray("suggestedActions").getString(0))
        assertEquals("clear", textbox.getJSONArray("suggestedActions").getString(1))
        assertEquals("press", textbox.getJSONArray("suggestedActions").getString(2))
        assertEquals("combobox", combobox.getJSONObject("suggestedTarget").getString("value"))
        assertEquals("Tone", combobox.getJSONObject("suggestedTarget").getString("name"))
        assertEquals("select", combobox.getJSONArray("suggestedActions").getString(0))
        assertEquals("checkbox", checkbox.getJSONObject("suggestedTarget").getString("value"))
        assertEquals("Subscribe updates", checkbox.getJSONObject("suggestedTarget").getString("name"))
        assertEquals("check", checkbox.getJSONArray("suggestedActions").getString(0))
        assertEquals("button", button.getJSONObject("suggestedTarget").getString("value"))
        assertEquals("Apply greeting", button.getJSONObject("suggestedTarget").getString("name"))
        assertEquals(0, button.getJSONObject("suggestedTarget").getInt("index"))
        assertEquals("click", button.getJSONArray("suggestedActions").getString(0))
        assertEquals("hover", button.getJSONArray("suggestedActions").getString(1))
        assertEquals("doubleClick", button.getJSONArray("suggestedActions").getString(2))
        assertEquals("button", frameButton.getJSONObject("suggestedTarget").getString("value"))
        assertEquals("Apply frame greeting", frameButton.getJSONObject("suggestedTarget").getString("name"))
        assertEquals("top/frame[0]", frameButton.getJSONObject("frame").getString("path"))
        assertTrue(frameButton.getJSONObject("frame").getString("url").contains("token=present"))
        assertFalse(frameButton.getJSONObject("frame").getString("url").contains("frame-secret"))
        assertEquals("Same origin automation frame", frameButton.getJSONObject("frame").getString("name"))
        assertTrue(frameButton.getJSONObject("frame").getBoolean("accessible"))
        assertEquals("iframe", blockedFrame.getJSONObject("suggestedTarget").getString("value"))
        assertEquals("Sandboxed blocked frame", blockedFrame.getJSONObject("suggestedTarget").getString("name"))
        assertEquals("find", blockedFrame.getJSONArray("suggestedActions").getString(0))
        assertFalse(blockedFrame.getJSONObject("frame").getBoolean("accessible"))
        assertEquals("button", shadowButton.getJSONObject("suggestedTarget").getString("value"))
        assertEquals("Apply shadow greeting", shadowButton.getJSONObject("suggestedTarget").getString("name"))
        assertEquals("/shadow[4]", shadowButton.getJSONObject("shadow").getString("path"))
        assertEquals("open-shadow-widget #open-shadow-widget", shadowButton.getJSONObject("shadow").getString("host"))
        assertEquals("local", observation.getJSONObject("page").getString("scope"))
        assertTrue(observation.getJSONObject("page").getBoolean("trustedForEvaluate"))
        assertTrue(observation.getJSONObject("page").getString("text").contains("token=present"))
        assertTrue(observation.getJSONObject("recentAction").getString("message").contains("authorization: present"))
        assertEquals("snapshot-action", observation.getJSONObject("recentAction").getString("snapshotId"))
        assertEquals(
            "/data/user/0/com.kite.app/files/browser-automation/screenshots/observe-shot.png",
            observation.getJSONObject("recentAction").getString("artifactPath")
        )
        assertTrue(
            observation.getJSONObject("recentAction").getString("artifactUrl")
                .startsWith("/browser-automation/artifact?path=")
        )
        assertEquals(1, observation.getJSONObject("recentRun").getInt("artifactCount"))
        assertTrue(
            observation.getJSONObject("recentRun").getString("latestArtifactUrl")
                .contains("observe-shot.png")
        )
        val capabilities = observation.getJSONObject("capabilities")
        assertJsonArrayContains(capabilities.getJSONArray("actions"), "click")
        assertJsonArrayContains(capabilities.getJSONArray("actions"), "clear")
        assertJsonArrayContains(capabilities.getJSONArray("actions"), "doubleClick")
        assertJsonArrayContains(capabilities.getJSONArray("actions"), "hover")
        assertJsonArrayContains(capabilities.getJSONArray("actions"), "navigate")
        assertJsonArrayContains(capabilities.getJSONArray("actions"), "screenshot")
        assertJsonArrayContains(capabilities.getJSONArray("targets"), "role+name")
        assertJsonArrayContains(capabilities.getJSONArray("targets"), "url")
        assertJsonArrayContains(capabilities.getJSONArray("targets"), "state")
        assertJsonArrayContains(capabilities.getJSONArray("endpoints"), "/browser-automation/run")
        assertJsonArrayContains(capabilities.getJSONArray("endpoints"), "/browser-automation/open-run")
        assertJsonArrayContains(capabilities.getJSONArray("endpoints"), "/browser-automation/artifact")
        assertJsonArrayContains(capabilities.getJSONArray("endpoints"), "/browser-automation/sessions")
        assertEquals("oauth_and_sso_stay_external", capabilities.getString("authBoundary"))
        assertEquals("local_trusted_only", capabilities.getString("evaluate"))
        assertEquals("BrowserAutomationCapabilities", capabilities.getString("source"))
        assertTrue(observation.getJSONObject("recentRun").getString("errorDetail").contains("password=present"))
        assertFalse(raw.contains("session-secret"))
        assertFalse(raw.contains("snapshot-secret"))
        assertFalse(raw.contains("action-secret"))
        assertFalse(raw.contains("plain-secret"))
        assertFalse(raw.contains("frame-secret"))
        assertFalse(raw.contains("closed-shadow-secret-should-not-leak"))
        assertFalse(raw.contains("hidden-token-should-not-leak"))
    }

    @Test
    fun observationOnlySuggestsFindForDisabledInteractiveNodes() {
        val session = BrowserAutomationSession(
            sessionId = "session-disabled",
            recipeId = null,
            recipeName = null,
            instanceId = "instance-disabled",
            source = "card_run_surface",
            url = "http://127.0.0.1:8791/browser-automation/test-page",
            status = BrowserAutomationSessionStatus.Ready,
            createdAt = 1_000L,
            updatedAt = 1_500L,
            lastActionId = null,
            lastSnapshotId = "snapshot-disabled"
        )
        val snapshot = BrowserAutomationSnapshot(
            snapshotId = "snapshot-disabled",
            sessionId = session.sessionId,
            url = session.url,
            title = "Demo",
            readyState = "complete",
            text = "Disabled controls",
            elementCount = 2,
            elements = emptyList(),
            accessibility = listOf(
                BrowserAutomationAccessibilityNode(
                    index = 0,
                    role = "button",
                    name = "Disabled action",
                    tag = "button",
                    type = null,
                    level = null,
                    visible = true,
                    enabled = false,
                    checked = null,
                    selected = null,
                    expanded = null,
                    x = 1.0,
                    y = 2.0,
                    width = 120.0,
                    height = 36.0
                ),
                BrowserAutomationAccessibilityNode(
                    index = 1,
                    role = "textbox",
                    name = "Disabled Name",
                    tag = "input",
                    type = "text",
                    level = null,
                    visible = true,
                    enabled = false,
                    checked = null,
                    selected = null,
                    expanded = null,
                    x = 1.0,
                    y = 48.0,
                    width = 160.0,
                    height = 36.0
                )
            ),
            capturedAt = 1_600L
        )

        val observation = BrowserAutomationObservation.toJson(
            session = session,
            snapshot = snapshot,
            recentAction = null,
            recentRun = null
        )
        val interactive = observation.getJSONArray("interactive")
        val disabledButton = interactive.getJSONObject(0)
        val disabledTextbox = interactive.getJSONObject(1)

        assertEquals(false, disabledButton.getBoolean("enabled"))
        assertEquals("button", disabledButton.getJSONObject("suggestedTarget").getString("value"))
        assertEquals("Disabled action", disabledButton.getJSONObject("suggestedTarget").getString("name"))
        assertEquals(1, disabledButton.getJSONArray("suggestedActions").length())
        assertEquals("find", disabledButton.getJSONArray("suggestedActions").getString(0))
        assertEquals(false, disabledTextbox.getBoolean("enabled"))
        assertEquals("textbox", disabledTextbox.getJSONObject("suggestedTarget").getString("value"))
        assertEquals(1, disabledTextbox.getJSONArray("suggestedActions").length())
        assertEquals("find", disabledTextbox.getJSONArray("suggestedActions").getString(0))
    }

    @Test
    fun observationSuggestedTargetsIncludeDuplicateIndexes() {
        val session = BrowserAutomationSession(
            sessionId = "session",
            recipeId = null,
            recipeName = null,
            instanceId = null,
            source = "card_run_surface",
            url = "http://127.0.0.1:8791/page",
            status = BrowserAutomationSessionStatus.Ready,
            createdAt = 1_000L,
            updatedAt = 1_500L
        )
        val snapshot = BrowserAutomationSnapshot(
            snapshotId = "snapshot",
            sessionId = session.sessionId,
            url = session.url,
            title = "Duplicate",
            readyState = "complete",
            text = "Duplicate action",
            elementCount = 3,
            elements = emptyList(),
            accessibility = listOf(
                BrowserAutomationAccessibilityNode(
                    index = 0,
                    role = "button",
                    name = "Duplicate action",
                    tag = "button",
                    type = null,
                    level = null,
                    visible = true,
                    enabled = true,
                    checked = null,
                    selected = null,
                    expanded = null,
                    x = 1.0,
                    y = 1.0,
                    width = 100.0,
                    height = 40.0
                ),
                BrowserAutomationAccessibilityNode(
                    index = 1,
                    role = "button",
                    name = "Duplicate action",
                    tag = "button",
                    type = null,
                    level = null,
                    visible = true,
                    enabled = true,
                    checked = null,
                    selected = null,
                    expanded = null,
                    x = 1.0,
                    y = 50.0,
                    width = 100.0,
                    height = 40.0
                ),
                BrowserAutomationAccessibilityNode(
                    index = 2,
                    role = "button",
                    name = "Unique action",
                    tag = "button",
                    type = null,
                    level = null,
                    visible = true,
                    enabled = true,
                    checked = null,
                    selected = null,
                    expanded = null,
                    x = 1.0,
                    y = 100.0,
                    width = 100.0,
                    height = 40.0
                )
            ),
            capturedAt = 1_600L
        )

        val observation = BrowserAutomationObservation.toJson(
            session = session,
            snapshot = snapshot,
            recentAction = null,
            recentRun = null
        )
        val first = observation.getJSONArray("interactive").getJSONObject(0).getJSONObject("suggestedTarget")
        val second = observation.getJSONArray("interactive").getJSONObject(1).getJSONObject("suggestedTarget")
        val third = observation.getJSONArray("interactive").getJSONObject(2).getJSONObject("suggestedTarget")

        assertEquals("button", first.getString("value"))
        assertEquals("Duplicate action", first.getString("name"))
        assertEquals(0, first.getInt("index"))
        assertEquals("Duplicate action", second.getString("name"))
        assertEquals(1, second.getInt("index"))
        assertEquals("Unique action", third.getString("name"))
        assertEquals(0, third.getInt("index"))
    }

    @Test
    fun observationMarksRemoteAndUnknownPagesAsNotTrustedForEvaluate() {
        val remoteSession = BrowserAutomationSession(
            sessionId = "remote",
            recipeId = null,
            recipeName = null,
            instanceId = null,
            source = "card_run_surface",
            url = "https://example.com/path?token=remote-secret",
            status = BrowserAutomationSessionStatus.Ready,
            createdAt = 1_000L,
            updatedAt = 1_100L
        )
        val remote = BrowserAutomationObservation.toJson(
            session = remoteSession,
            snapshot = null,
            recentAction = null,
            recentRun = null
        ).getJSONObject("page")
        val unknownSession = remoteSession.copy(sessionId = "unknown", url = "about:blank")
        val unknown = BrowserAutomationObservation.toJson(
            session = unknownSession,
            snapshot = null,
            recentAction = null,
            recentRun = null
        ).getJSONObject("page")

        assertEquals("remote", remote.getString("scope"))
        assertFalse(remote.getBoolean("trustedForEvaluate"))
        assertTrue(remote.getString("url").contains("token=present"))
        assertFalse(remote.toString().contains("remote-secret"))
        assertEquals("unknown", unknown.getString("scope"))
        assertFalse(unknown.getBoolean("trustedForEvaluate"))
    }

    @Test
    fun capabilitiesEndpointJsonKeepsStringAndListFormsInSync() {
        val endpointJson = BrowserAutomationCapabilities.toEndpointJson()
        val observationJson = BrowserAutomationCapabilities.toObservationJson()

        assertTrue(endpointJson.getString("actions").contains("doubleClick"))
        assertTrue(endpointJson.getString("actions").contains("clear"))
        assertTrue(endpointJson.getString("actions").contains("navigate"))
        assertTrue(endpointJson.getString("targets").contains("role+name"))
        assertTrue(endpointJson.getString("endpoints").contains("/browser-automation/open-run"))
        assertTrue(endpointJson.getString("endpoints").contains("/browser-automation/sessions"))
        assertJsonArrayContains(endpointJson.getJSONArray("actionList"), "doubleClick")
        assertJsonArrayContains(endpointJson.getJSONArray("actionList"), "clear")
        assertJsonArrayContains(endpointJson.getJSONArray("actionList"), "navigate")
        assertJsonArrayContains(endpointJson.getJSONArray("targetList"), "state")
        assertJsonArrayContains(endpointJson.getJSONArray("endpointList"), "/browser-automation/artifact")
        assertJsonArrayContains(endpointJson.getJSONArray("endpointList"), "/browser-automation/sessions")
        assertJsonArrayContains(observationJson.getJSONArray("actions"), "doubleClick")
        assertJsonArrayContains(observationJson.getJSONArray("actions"), "clear")
        assertJsonArrayContains(observationJson.getJSONArray("actions"), "navigate")
        assertEquals(endpointJson.getJSONArray("actionList").length(), observationJson.getJSONArray("actions").length())
        assertEquals(endpointJson.getString("authBoundary"), observationJson.getString("authBoundary"))
    }

    private fun assertJsonArrayContains(array: org.json.JSONArray, expected: String) {
        val values = buildList {
            for (index in 0 until array.length()) add(array.getString(index))
        }
        assertTrue("$expected missing from $values", expected in values)
    }
}
