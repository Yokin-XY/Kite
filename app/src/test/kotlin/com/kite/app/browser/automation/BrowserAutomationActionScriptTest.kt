package com.kite.app.browser.automation

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BrowserAutomationActionScriptTest {
    @Test
    fun actionFromJsonSupportsNestedTarget() {
        val action = BrowserAutomationAction.fromJson(
            JSONObject()
                .put("type", "click")
                .put("sessionId", "session")
                .put(
                    "target",
                    JSONObject()
                        .put("kind", "text")
                        .put("value", "Apply")
                        .put("name", "Apply greeting")
                        .put("index", 1)
                )
                .put("timeoutMs", 2_000L)
        )

        checkNotNull(action)
        assertEquals(BrowserAutomationActionType.Click, action.type)
        assertEquals("session", action.sessionId)
        assertEquals(BrowserAutomationTargetKind.Text, action.target.kind)
        assertEquals("Apply", action.target.value)
        assertEquals("Apply greeting", action.target.name)
        assertEquals(1, action.target.index)
        assertEquals("Apply greeting", action.target.toJson().getString("name"))
    }

    @Test
    fun actionFromJsonSupportsRoleNameTarget() {
        val nested = BrowserAutomationAction.fromJson(
            JSONObject()
                .put("type", "click")
                .put(
                    "target",
                    JSONObject()
                        .put("kind", "role")
                        .put("value", "button")
                        .put("name", "Apply greeting")
                )
        )
        val flat = BrowserAutomationAction.fromJson(
            JSONObject()
                .put("type", "waitFor")
                .put("targetKind", "role")
                .put("target", "status")
                .put("targetName", "Hello Kite")
        )

        checkNotNull(nested)
        checkNotNull(flat)
        assertEquals(BrowserAutomationTargetKind.Role, nested.target.kind)
        assertEquals("button", nested.target.value)
        assertEquals("Apply greeting", nested.target.name)
        assertEquals(BrowserAutomationActionType.WaitFor, flat.type)
        assertEquals("status", flat.target.value)
        assertEquals("Hello Kite", flat.target.name)
    }

    @Test
    fun actionFromJsonSupportsUrlTarget() {
        val action = BrowserAutomationAction.fromJson(
            JSONObject()
                .put("type", "waitFor")
                .put(
                    "target",
                    JSONObject()
                        .put("kind", "url")
                        .put("value", "#ready")
                        .put("match", "contains")
                )
        )

        checkNotNull(action)
        assertEquals(BrowserAutomationActionType.WaitFor, action.type)
        assertEquals(BrowserAutomationTargetKind.Url, action.target.kind)
        assertEquals("#ready", action.target.value)
        assertEquals("contains", action.target.match)
        assertEquals("url=#ready", action.target.summary())
    }

    @Test
    fun actionFromJsonSupportsStateTarget() {
        val action = BrowserAutomationAction.fromJson(
            JSONObject()
                .put("type", "waitFor")
                .put("value", "350")
                .put(
                    "target",
                    JSONObject()
                        .put("kind", "state")
                        .put("value", "idle")
                )
        )

        checkNotNull(action)
        assertEquals(BrowserAutomationActionType.WaitFor, action.type)
        assertEquals(BrowserAutomationTargetKind.State, action.target.kind)
        assertEquals("idle", action.target.value)
        assertEquals("350", action.value)
        assertEquals("state=idle", action.target.summary())
    }

    @Test
    fun actionFromJsonSupportsExtendedActions() {
        val scroll = BrowserAutomationAction.fromJson(
            JSONObject()
                .put("type", "scroll")
                .put("value", "bottom")
        )
        val evaluate = BrowserAutomationAction.fromJson(
            JSONObject()
                .put("type", "evaluate")
                .put("value", "document.title")
        )
        val screenshot = BrowserAutomationAction.fromJson(
            JSONObject()
                .put("type", "screenshot")
        )
        val clear = BrowserAutomationAction.fromJson(
            JSONObject()
                .put("type", "clear")
                .put("target", JSONObject().put("kind", "role").put("value", "textbox").put("name", "Name"))
        )
        val press = BrowserAutomationAction.fromJson(
            JSONObject()
                .put("type", "press")
                .put("value", "Enter")
        )
        val select = BrowserAutomationAction.fromJson(
            JSONObject()
                .put("type", "select")
                .put("target", JSONObject().put("kind", "role").put("value", "combobox").put("name", "Tone"))
                .put("value", "formal")
        )
        val check = BrowserAutomationAction.fromJson(
            JSONObject()
                .put("type", "check")
                .put("target", JSONObject().put("kind", "role").put("value", "checkbox").put("name", "Subscribe updates"))
                .put("value", "true")
        )
        val hover = BrowserAutomationAction.fromJson(
            JSONObject()
                .put("type", "hover")
                .put("target", JSONObject().put("kind", "role").put("value", "button").put("name", "Hover reveal menu"))
        )
        val doubleClick = BrowserAutomationAction.fromJson(
            JSONObject()
                .put("type", "doubleClick")
                .put("target", JSONObject().put("kind", "role").put("value", "button").put("name", "Double click open"))
        )
        val navigate = BrowserAutomationAction.fromJson(
            JSONObject()
                .put("type", "navigate")
                .put("value", "back")
        )

        checkNotNull(scroll)
        checkNotNull(evaluate)
        checkNotNull(screenshot)
        checkNotNull(clear)
        checkNotNull(press)
        checkNotNull(select)
        checkNotNull(check)
        checkNotNull(hover)
        checkNotNull(doubleClick)
        checkNotNull(navigate)
        assertEquals(BrowserAutomationActionType.Scroll, scroll.type)
        assertEquals(BrowserAutomationActionType.Evaluate, evaluate.type)
        assertEquals(BrowserAutomationActionType.Screenshot, screenshot.type)
        assertEquals(BrowserAutomationActionType.Clear, clear.type)
        assertEquals(BrowserAutomationActionType.Press, press.type)
        assertEquals(BrowserAutomationActionType.Select, select.type)
        assertEquals(BrowserAutomationActionType.Check, check.type)
        assertEquals(BrowserAutomationActionType.Hover, hover.type)
        assertEquals(BrowserAutomationActionType.DoubleClick, doubleClick.type)
        assertEquals(BrowserAutomationActionType.Navigate, navigate.type)
        assertEquals("Enter", press.value)
        assertEquals("formal", select.value)
        assertEquals("true", check.value)
        assertEquals("Double click open", doubleClick.target.name)
        assertEquals("Name", clear.target.name)
        assertEquals("back", navigate.value)
        assertEquals(BrowserAutomationTargetKind.None, scroll.target.kind)
    }

    @Test
    fun runRequestFromJsonInheritsSessionAndStopFlag() {
        val request = BrowserAutomationRunRequest.fromJson(
            JSONObject()
                .put("runId", "run-fixed")
                .put("sessionId", "session")
                .put("stopOnFailure", true)
                .put(
                    "actions",
                    JSONArray()
                        .put(
                            JSONObject()
                                .put("type", "type")
                                .put("target", JSONObject().put("kind", "role").put("value", "textbox").put("name", "Name"))
                                .put("value", "A9 Run")
                        )
                        .put(
                            JSONObject()
                                .put("type", "click")
                                .put("target", JSONObject().put("kind", "role").put("value", "button").put("name", "Apply greeting"))
                        )
                )
        )

        checkNotNull(request)
        assertEquals("run-fixed", request.runId)
        assertEquals("session", request.sessionId)
        assertTrue(request.stopOnFailure)
        assertEquals(2, request.actions.size)
        assertEquals("session", request.actions.first().sessionId)
        assertEquals(BrowserAutomationActionType.TypeText, request.actions.first().type)
        assertEquals("Name", request.actions.first().target.name)
        assertEquals(BrowserAutomationActionType.Click, request.actions[1].type)
    }

    @Test
    fun openRunRequestFromJsonParsesUrlAndCanAttachSession() {
        val request = BrowserAutomationOpenRunRequest.fromJson(
            JSONObject()
                .put("url", "http://127.0.0.1:8791/browser-automation/test-page")
                .put("source", "test-open-run")
                .put("openTimeoutMs", 2_500L)
                .put("stopOnFailure", true)
                .put(
                    "actions",
                    JSONArray()
                        .put(
                            JSONObject()
                                .put("type", "type")
                                .put("target", JSONObject().put("kind", "role").put("value", "textbox").put("name", "Name"))
                                .put("value", "A10")
                        )
                        .put(
                            JSONObject()
                                .put("type", "waitFor")
                                .put("target", JSONObject().put("kind", "role").put("value", "status").put("name", "Hello A10"))
                        )
                )
        )
        checkNotNull(request)
        val session = BrowserAutomationSession(
            sessionId = "session-a10",
            recipeId = "recipe",
            recipeName = "Recipe",
            instanceId = "instance-a10",
            source = "card_run_surface",
            url = request.url,
            status = BrowserAutomationSessionStatus.Ready,
            createdAt = 1_000L,
            updatedAt = 1_200L
        )

        val attached = request.runRequest.withSession(session)

        assertEquals("http://127.0.0.1:8791/browser-automation/test-page", request.url)
        assertEquals("test-open-run", request.source)
        assertEquals(2_500L, request.openTimeoutMs)
        assertEquals("session-a10", attached.sessionId)
        assertEquals("session-a10", attached.actions.first().sessionId)
        assertEquals("instance-a10", attached.actions.first().instanceId)
        assertEquals("Hello A10", attached.actions[1].target.name)
    }

    @Test
    fun runResultSummarizesFailureAndStopState() {
        val request = BrowserAutomationRunRequest.fromJson(
            JSONObject()
                .put("runId", "run-failure")
                .put("sessionId", "session")
                .put("stopOnFailure", true)
                .put(
                    "actions",
                    JSONArray()
                        .put(JSONObject().put("type", "click").put("target", JSONObject().put("kind", "css").put("value", "#missing")))
                        .put(JSONObject().put("type", "snapshot"))
                )
        )
        checkNotNull(request)
        val failed = BrowserAutomationActionResult(
            actionId = request.actions.first().actionId,
            sessionId = "session",
            type = BrowserAutomationActionType.Click,
            status = BrowserAutomationResultStatus.Failed,
            durationMs = 20L,
            url = "http://127.0.0.1:8791/page?token=secret",
            title = "Demo",
            message = "target not found",
            errorCode = "target_not_found",
            errorDetail = "css=#missing",
            completedAt = 100L
        )

        val result = BrowserAutomationRunResult.fromResults(
            request = request,
            startedAt = System.currentTimeMillis(),
            results = listOf(failed)
        )
        val json = result.toJson()

        assertEquals(BrowserAutomationResultStatus.Failed, result.status)
        assertEquals(2, result.requestedCount)
        assertEquals(1, result.completedCount)
        assertTrue(result.stoppedOnFailure)
        assertEquals("target_not_found", json.getString("errorCode"))
        assertFalse(json.toString().contains("secret"))
        assertTrue(json.toString().contains("token=present"))
    }

    @Test
    fun parseResultDecodesQuotedActionResult() {
        val action = BrowserAutomationAction(
            actionId = "action",
            sessionId = "session",
            instanceId = null,
            type = BrowserAutomationActionType.TypeText,
            target = BrowserAutomationTarget(BrowserAutomationTargetKind.Css, "#name"),
            value = "secret typed value",
            timeoutMs = 1_000L
        )
        val raw = JSONObject.quote(
            JSONObject()
                .put("ok", true)
                .put("status", "Succeeded")
                .put("url", "http://127.0.0.1:8791/page?token=secret")
                .put("title", "Demo")
                .put("message", "typed 18 chars")
                .put("matchedCount", 1)
                .put("durationMs", 22L)
                .toString()
        )

        val result = BrowserAutomationActionScript.parseResult(
            sessionId = "session",
            action = action,
            rawResult = raw,
            startedAt = 10L,
            completedAt = 40L
        )

        assertEquals(BrowserAutomationResultStatus.Succeeded, result.status)
        assertEquals(BrowserAutomationActionType.TypeText, result.type)
        assertEquals(1, result.matchedCount)
        assertEquals("typed 18 chars", result.message)
        assertFalse(result.url.contains("secret"))
        assertTrue(result.url.contains("token=present"))
    }

    @Test
    fun scriptDoesNotEmbedRawValueOutsideJsonPayload() {
        val action = BrowserAutomationAction(
            actionId = "action",
            sessionId = "session",
            instanceId = null,
            type = BrowserAutomationActionType.TypeText,
            target = BrowserAutomationTarget(BrowserAutomationTargetKind.Css, "#name"),
            value = "Kite",
            timeoutMs = 1_000L
        )

        val script = BrowserAutomationActionScript.scriptFor(action)

        assertTrue(script.contains("JSON.parse"))
        assertTrue(script.contains("\\\"type\\\":\\\"type\\\""))
        assertTrue(script.contains("\\\"value\\\":\\\"Kite\\\""))
    }

    @Test
    fun scriptSupportsScrollAndEvaluateBranches() {
        val scrollAction = BrowserAutomationAction(
            actionId = "scroll",
            sessionId = "session",
            instanceId = null,
            type = BrowserAutomationActionType.Scroll,
            target = BrowserAutomationTarget(BrowserAutomationTargetKind.Css, "#deep-target"),
            value = null,
            timeoutMs = 1_000L
        )
        val evaluateAction = BrowserAutomationAction(
            actionId = "evaluate",
            sessionId = "session",
            instanceId = null,
            type = BrowserAutomationActionType.Evaluate,
            target = BrowserAutomationTarget(BrowserAutomationTargetKind.None),
            value = "document.body.dataset.automationResult",
            timeoutMs = 1_000L
        )

        val scrollScript = BrowserAutomationActionScript.scriptFor(scrollAction)
        val evaluateScript = BrowserAutomationActionScript.scriptFor(evaluateAction)

        assertTrue(scrollScript.contains("action.type === 'scroll'"))
        assertTrue(scrollScript.contains("scrollIntoView"))
        assertTrue(evaluateScript.contains("action.type === 'evaluate'"))
        assertTrue(evaluateScript.contains("(0, eval)(source)"))
    }

    @Test
    fun scriptSupportsPressAction() {
        val action = BrowserAutomationAction(
            actionId = "press",
            sessionId = "session",
            instanceId = null,
            type = BrowserAutomationActionType.Press,
            target = BrowserAutomationTarget(
                kind = BrowserAutomationTargetKind.Role,
                value = "textbox",
                name = "Name"
            ),
            value = "Enter",
            timeoutMs = 1_000L
        )
        val urlAction = action.copy(
            actionId = "press-url",
            target = BrowserAutomationTarget(BrowserAutomationTargetKind.Url, "#ready")
        )
        val stateAction = action.copy(
            actionId = "press-state",
            target = BrowserAutomationTarget(BrowserAutomationTargetKind.State, "domReady")
        )

        val script = BrowserAutomationActionScript.scriptFor(action)
        val urlScript = BrowserAutomationActionScript.scriptFor(urlAction)
        val stateScript = BrowserAutomationActionScript.scriptFor(stateAction)

        assertTrue(script.contains("\\\"type\\\":\\\"press\\\""))
        assertTrue(script.contains("action.type === 'press'"))
        assertTrue(script.contains("var normalizePressKey = function"))
        assertTrue(script.contains("new KeyboardEvent"))
        assertTrue(script.contains("pressed ' + keyInfo.label"))
        assertTrue(script.contains("target_not_visible"))
        assertTrue(urlScript.contains("url target only supports find and waitFor"))
        assertTrue(stateScript.contains("state target only supports find and waitFor"))
        assertTrue(urlScript.contains("target_not_actionable"))
        assertTrue(stateScript.contains("target_not_actionable"))
    }

    @Test
    fun scriptSupportsClearAction() {
        val action = BrowserAutomationAction(
            actionId = "clear",
            sessionId = "session",
            instanceId = null,
            type = BrowserAutomationActionType.Clear,
            target = BrowserAutomationTarget(
                kind = BrowserAutomationTargetKind.Role,
                value = "textbox",
                name = "Name"
            ),
            value = null,
            timeoutMs = 1_000L
        )
        val urlAction = action.copy(
            actionId = "clear-url",
            target = BrowserAutomationTarget(BrowserAutomationTargetKind.Url, "#ready")
        )
        val stateAction = action.copy(
            actionId = "clear-state",
            target = BrowserAutomationTarget(BrowserAutomationTargetKind.State, "domReady")
        )

        val script = BrowserAutomationActionScript.scriptFor(action)
        val urlScript = BrowserAutomationActionScript.scriptFor(urlAction)
        val stateScript = BrowserAutomationActionScript.scriptFor(stateAction)

        assertTrue(script.contains("\\\"type\\\":\\\"clear\\\""))
        assertTrue(script.contains("action.type === 'clear'"))
        assertTrue(script.contains("var clearTargetInfo = function"))
        assertTrue(script.contains("kind: 'contenteditable'"))
        assertTrue(script.contains("kind: 'textarea'"))
        assertTrue(script.contains("kind: 'input'"))
        assertTrue(script.contains("element.textContent = ''"))
        assertTrue(script.contains("element.value = ''"))
        assertTrue(script.contains("target_not_editable"))
        assertTrue(script.contains("cleared ' + clearInfo.kind"))
        assertTrue(script.contains("element.dispatchEvent(new Event('input'"))
        assertTrue(script.contains("element.dispatchEvent(new Event('change'"))
        assertTrue(urlScript.contains("url target only supports find and waitFor"))
        assertTrue(stateScript.contains("state target only supports find and waitFor"))
        assertTrue(urlScript.contains("target_not_actionable"))
        assertTrue(stateScript.contains("target_not_actionable"))
    }

    @Test
    fun scriptRejectsDisabledAndReadonlyActionability() {
        val clickAction = BrowserAutomationAction(
            actionId = "click-disabled",
            sessionId = "session",
            instanceId = null,
            type = BrowserAutomationActionType.Click,
            target = BrowserAutomationTarget(
                kind = BrowserAutomationTargetKind.Role,
                value = "button",
                name = "Disabled action"
            ),
            value = null,
            timeoutMs = 1_000L
        )
        val clearAction = clickAction.copy(
            actionId = "clear-readonly",
            type = BrowserAutomationActionType.Clear,
            target = BrowserAutomationTarget(
                kind = BrowserAutomationTargetKind.Role,
                value = "textbox",
                name = "Readonly Name"
            )
        )
        val pressAction = clickAction.copy(
            actionId = "press-disabled",
            type = BrowserAutomationActionType.Press,
            value = "Enter"
        )

        val clickScript = BrowserAutomationActionScript.scriptFor(clickAction)
        val clearScript = BrowserAutomationActionScript.scriptFor(clearAction)
        val pressScript = BrowserAutomationActionScript.scriptFor(pressAction)

        assertTrue(clickScript.contains("var disabledForAutomation = function"))
        assertTrue(clickScript.contains("var readonlyForAutomation = function"))
        assertTrue(clickScript.contains("var requiresEnabled = function"))
        assertTrue(clickScript.contains("hasAriaState(el, 'aria-disabled')"))
        assertTrue(clickScript.contains("hasAriaState(el, 'aria-readonly')"))
        assertTrue(clickScript.contains("el.matches(':disabled')"))
        assertTrue(clickScript.contains("requiresEnabled(action.type) && disabledForAutomation(element)"))
        assertTrue(clickScript.contains("errorCode: 'target_disabled'"))
        assertTrue(clickScript.contains("enabled: !disabledForAutomation(el)"))
        assertTrue(clearScript.contains("(action.type === 'type' || action.type === 'clear') && readonlyForAutomation(element)"))
        assertTrue(clearScript.contains("errorCode: 'target_readonly'"))
        assertTrue(pressScript.contains("disabledForAutomation(pressTarget)"))
        assertTrue(pressScript.contains("errorCode: 'target_disabled'"))
    }

    @Test
    fun scriptClickDispatchesPointerMousePreludeBeforeActivation() {
        val action = BrowserAutomationAction(
            actionId = "click",
            sessionId = "session",
            instanceId = null,
            type = BrowserAutomationActionType.Click,
            target = BrowserAutomationTarget(
                kind = BrowserAutomationTargetKind.Role,
                value = "button",
                name = "Pointer gated click"
            ),
            value = null,
            timeoutMs = 1_000L
        )

        val script = BrowserAutomationActionScript.scriptFor(action)

        assertTrue(script.contains("\\\"type\\\":\\\"click\\\""))
        assertTrue(script.contains("var dispatchClickPrelude = function"))
        assertTrue(script.contains("new view.PointerEvent(type, init)"))
        assertTrue(script.contains("new view.MouseEvent(type, init)"))
        assertTrue(script.contains("dispatchPointerMouse(el, 'pointerdown'"))
        assertTrue(script.contains("dispatchPointerMouse(el, 'mousedown'"))
        assertTrue(script.contains("dispatchPointerMouse(el, 'pointerup'"))
        assertTrue(script.contains("dispatchPointerMouse(el, 'mouseup'"))
        assertTrue(script.indexOf("dispatchClickPrelude(element)") < script.indexOf("element.click()"))
        assertTrue(script.contains("clicked ' + target.kind + ' with pointer prelude"))
    }

    @Test
    fun scriptHoverDispatchesPointerMouseOverAndMovePrelude() {
        val action = BrowserAutomationAction(
            actionId = "hover",
            sessionId = "session",
            instanceId = null,
            type = BrowserAutomationActionType.Hover,
            target = BrowserAutomationTarget(
                kind = BrowserAutomationTargetKind.Role,
                value = "button",
                name = "Hover reveal menu"
            ),
            value = null,
            timeoutMs = 1_000L
        )
        val urlAction = action.copy(
            actionId = "hover-url",
            target = BrowserAutomationTarget(BrowserAutomationTargetKind.Url, "#ready")
        )
        val stateAction = action.copy(
            actionId = "hover-state",
            target = BrowserAutomationTarget(BrowserAutomationTargetKind.State, "domReady")
        )

        val script = BrowserAutomationActionScript.scriptFor(action)
        val urlScript = BrowserAutomationActionScript.scriptFor(urlAction)
        val stateScript = BrowserAutomationActionScript.scriptFor(stateAction)

        assertTrue(script.contains("\\\"type\\\":\\\"hover\\\""))
        assertTrue(script.contains("var dispatchHoverPrelude = function"))
        assertTrue(script.contains("dispatchPointerMouse(el, 'pointerover'"))
        assertTrue(script.contains("dispatchPointerMouse(el, 'pointerenter'"))
        assertTrue(script.contains("dispatchPointerMouse(el, 'mouseover'"))
        assertTrue(script.contains("dispatchPointerMouse(el, 'mouseenter'"))
        assertTrue(script.contains("dispatchPointerMouse(el, 'pointermove'"))
        assertTrue(script.contains("dispatchPointerMouse(el, 'mousemove'"))
        assertTrue(script.contains("action.type === 'hover'"))
        assertFalse(script.contains("element.click();\n                  return finish('Succeeded', {\n                    message: 'hovered"))
        assertTrue(script.contains("hovered ' + target.kind + ' with pointer prelude"))
        assertTrue(urlScript.contains("url target only supports find and waitFor"))
        assertTrue(stateScript.contains("state target only supports find and waitFor"))
        assertTrue(urlScript.contains("target_not_actionable"))
        assertTrue(stateScript.contains("target_not_actionable"))
    }

    @Test
    fun scriptDoubleClickDispatchesTwoClickPreludesAndDblClick() {
        val action = BrowserAutomationAction(
            actionId = "double-click",
            sessionId = "session",
            instanceId = null,
            type = BrowserAutomationActionType.DoubleClick,
            target = BrowserAutomationTarget(
                kind = BrowserAutomationTargetKind.Role,
                value = "button",
                name = "Double click open"
            ),
            value = null,
            timeoutMs = 1_000L
        )
        val urlAction = action.copy(
            actionId = "double-click-url",
            target = BrowserAutomationTarget(BrowserAutomationTargetKind.Url, "#ready")
        )
        val stateAction = action.copy(
            actionId = "double-click-state",
            target = BrowserAutomationTarget(BrowserAutomationTargetKind.State, "domReady")
        )

        val script = BrowserAutomationActionScript.scriptFor(action)
        val urlScript = BrowserAutomationActionScript.scriptFor(urlAction)
        val stateScript = BrowserAutomationActionScript.scriptFor(stateAction)

        assertTrue(script.contains("\\\"type\\\":\\\"doubleClick\\\""))
        assertTrue(script.contains("var dispatchDoubleClick = function"))
        assertTrue(script.contains("detail: detail || 0"))
        assertTrue(script.contains("dispatchDoubleClick(element)"))
        assertTrue(script.contains("dispatchPointerMouse(el, 'dblclick', point, false, false, 2)"))
        assertTrue(script.contains("action.type === 'doubleClick'"))
        assertTrue(script.contains("double clicked ' + target.kind + ' with pointer prelude"))
        assertEquals(2, Regex("dispatchClickPrelude\\(el\\);").findAll(script).count())
        assertEquals(2, Regex("el\\.click\\(\\);").findAll(script.substringAfter("var dispatchDoubleClick")).count())
        assertTrue(urlScript.contains("url target only supports find and waitFor"))
        assertTrue(stateScript.contains("state target only supports find and waitFor"))
        assertTrue(urlScript.contains("target_not_actionable"))
        assertTrue(stateScript.contains("target_not_actionable"))
    }

    @Test
    fun scriptQueriesSameOriginIframeDocuments() {
        val action = BrowserAutomationAction(
            actionId = "iframe-click",
            sessionId = "session",
            instanceId = null,
            type = BrowserAutomationActionType.Click,
            target = BrowserAutomationTarget(
                kind = BrowserAutomationTargetKind.Role,
                value = "button",
                name = "Apply frame greeting"
            ),
            value = null,
            timeoutMs = 1_000L
        )

        val script = BrowserAutomationActionScript.scriptFor(action)

        assertTrue(script.contains("var collectDocumentContexts = function"))
        assertTrue(script.contains("childFrame.contentDocument"))
        assertTrue(script.contains("childFrame.contentWindow && childFrame.contentWindow.document"))
        assertTrue(script.contains("ctx.root.querySelectorAll"))
        assertTrue(script.contains("var doc = (el && el.ownerDocument) || document"))
        assertTrue(script.contains("var view = (el.ownerDocument && el.ownerDocument.defaultView) || window"))
        assertTrue(script.contains("framePath: frame.framePath || ''"))
    }

    @Test
    fun scriptQueriesOpenShadowRoots() {
        val action = BrowserAutomationAction(
            actionId = "shadow-click",
            sessionId = "session",
            instanceId = null,
            type = BrowserAutomationActionType.Click,
            target = BrowserAutomationTarget(
                kind = BrowserAutomationTargetKind.Role,
                value = "button",
                name = "Apply shadow greeting"
            ),
            value = null,
            timeoutMs = 1_000L
        )

        val script = BrowserAutomationActionScript.scriptFor(action)

        assertTrue(script.contains("var visitOpenShadows = function"))
        assertTrue(script.contains("if (!host.shadowRoot) return"))
        assertTrue(script.contains("root: host.shadowRoot"))
        assertTrue(script.contains("ctx.root.querySelectorAll"))
        assertTrue(script.contains("var root = (el && el.getRootNode && el.getRootNode()) || doc"))
        assertTrue(script.contains("shadowPath: frame.shadowPath || ''"))
        assertTrue(script.contains("shadowHost: frame.shadowHost || ''"))
    }

    @Test
    fun scriptNavigateSupportsHistoryControlsAndRejectsTargets() {
        val action = BrowserAutomationAction(
            actionId = "navigate",
            sessionId = "session",
            instanceId = null,
            type = BrowserAutomationActionType.Navigate,
            target = BrowserAutomationTarget(BrowserAutomationTargetKind.None),
            value = "back",
            timeoutMs = 1_000L
        )
        val targetedAction = action.copy(
            actionId = "navigate-target",
            target = BrowserAutomationTarget(BrowserAutomationTargetKind.Role, "button", name = "Apply greeting")
        )
        val unsupportedAction = action.copy(
            actionId = "navigate-unsupported",
            value = "https://example.com/"
        )

        val script = BrowserAutomationActionScript.scriptFor(action)
        val targetedScript = BrowserAutomationActionScript.scriptFor(targetedAction)
        val unsupportedScript = BrowserAutomationActionScript.scriptFor(unsupportedAction)

        assertTrue(script.contains("\\\"type\\\":\\\"navigate\\\""))
        assertTrue(script.contains("action.type === 'navigate'"))
        assertTrue(script.contains("location.reload()"))
        assertTrue(script.contains("history.back()"))
        assertTrue(script.contains("history.forward()"))
        assertTrue(script.contains("navigation back requested"))
        assertTrue(script.contains("navigation forward requested"))
        assertTrue(script.contains("navigation reload requested"))
        assertFalse(script.contains("location.href ="))
        assertTrue(targetedScript.contains("navigate only supports target kind none"))
        assertTrue(targetedScript.contains("target_not_actionable"))
        assertTrue(unsupportedScript.contains("unsupported_navigation_value"))
        assertTrue(unsupportedScript.contains("navigate supports back, forward and reload"))
    }

    @Test
    fun scriptSupportsSelectAction() {
        val action = BrowserAutomationAction(
            actionId = "select",
            sessionId = "session",
            instanceId = null,
            type = BrowserAutomationActionType.Select,
            target = BrowserAutomationTarget(
                kind = BrowserAutomationTargetKind.Role,
                value = "combobox",
                name = "Tone"
            ),
            value = "formal",
            timeoutMs = 1_000L
        )
        val textTargetAction = action.copy(
            actionId = "select-text",
            value = "Formal"
        )
        val indexAction = action.copy(
            actionId = "select-index",
            value = "index:2"
        )
        val urlAction = action.copy(
            actionId = "select-url",
            target = BrowserAutomationTarget(BrowserAutomationTargetKind.Url, "#ready")
        )
        val stateAction = action.copy(
            actionId = "select-state",
            target = BrowserAutomationTarget(BrowserAutomationTargetKind.State, "domReady")
        )

        val script = BrowserAutomationActionScript.scriptFor(action)
        val textScript = BrowserAutomationActionScript.scriptFor(textTargetAction)
        val indexScript = BrowserAutomationActionScript.scriptFor(indexAction)
        val urlScript = BrowserAutomationActionScript.scriptFor(urlAction)
        val stateScript = BrowserAutomationActionScript.scriptFor(stateAction)

        assertTrue(script.contains("\\\"type\\\":\\\"select\\\""))
        assertTrue(script.contains("action.type === 'select'"))
        assertTrue(script.contains("var matchSelectOption = function"))
        assertTrue(script.contains("target_not_selectable"))
        assertTrue(script.contains("selected ' + optionLabelOf"))
        assertTrue(script.contains("element.dispatchEvent(new Event('change'"))
        assertTrue(textScript.contains("\\\"value\\\":\\\"Formal\\\""))
        assertTrue(indexScript.contains("\\\"value\\\":\\\"index:2\\\""))
        assertTrue(urlScript.contains("url target only supports find and waitFor"))
        assertTrue(stateScript.contains("state target only supports find and waitFor"))
        assertTrue(urlScript.contains("target_not_actionable"))
        assertTrue(stateScript.contains("target_not_actionable"))
    }

    @Test
    fun scriptSupportsCheckAction() {
        val action = BrowserAutomationAction(
            actionId = "check",
            sessionId = "session",
            instanceId = null,
            type = BrowserAutomationActionType.Check,
            target = BrowserAutomationTarget(
                kind = BrowserAutomationTargetKind.Role,
                value = "checkbox",
                name = "Subscribe updates"
            ),
            value = "true",
            timeoutMs = 1_000L
        )
        val toggleAction = action.copy(actionId = "check-toggle", value = "toggle")
        val urlAction = action.copy(
            actionId = "check-url",
            target = BrowserAutomationTarget(BrowserAutomationTargetKind.Url, "#ready")
        )
        val stateAction = action.copy(
            actionId = "check-state",
            target = BrowserAutomationTarget(BrowserAutomationTargetKind.State, "domReady")
        )

        val script = BrowserAutomationActionScript.scriptFor(action)
        val toggleScript = BrowserAutomationActionScript.scriptFor(toggleAction)
        val urlScript = BrowserAutomationActionScript.scriptFor(urlAction)
        val stateScript = BrowserAutomationActionScript.scriptFor(stateAction)

        assertTrue(script.contains("\\\"type\\\":\\\"check\\\""))
        assertTrue(script.contains("action.type === 'check'"))
        assertTrue(script.contains("var normalizeCheckValue = function"))
        assertTrue(script.contains("var checkTargetInfo = function"))
        assertTrue(script.contains("target_not_checkable"))
        assertTrue(script.contains("radio cannot be unchecked directly"))
        assertTrue(script.contains("checked ' + finalChecked"))
        assertTrue(script.contains("element.dispatchEvent(new Event('change'"))
        assertTrue(toggleScript.contains("\\\"value\\\":\\\"toggle\\\""))
        assertTrue(urlScript.contains("url target only supports find and waitFor"))
        assertTrue(stateScript.contains("state target only supports find and waitFor"))
        assertTrue(urlScript.contains("target_not_actionable"))
        assertTrue(stateScript.contains("target_not_actionable"))
    }

    @Test
    fun scriptDoesNotUsePasswordInputValueForLabels() {
        val action = BrowserAutomationAction(
            actionId = "find",
            sessionId = "session",
            instanceId = null,
            type = BrowserAutomationActionType.Find,
            target = BrowserAutomationTarget(BrowserAutomationTargetKind.Text, "Secret"),
            value = null,
            timeoutMs = 1_000L
        )

        val script = BrowserAutomationActionScript.scriptFor(action)

        assertTrue(script.contains("type === 'password'"))
    }

    @Test
    fun scriptSupportsRoleNameSelectorAndPasswordGuard() {
        val action = BrowserAutomationAction(
            actionId = "click",
            sessionId = "session",
            instanceId = null,
            type = BrowserAutomationActionType.Click,
            target = BrowserAutomationTarget(
                kind = BrowserAutomationTargetKind.Role,
                value = "button",
                name = "Apply greeting"
            ),
            value = null,
            timeoutMs = 1_000L
        )

        val script = BrowserAutomationActionScript.scriptFor(action)

        assertTrue(script.contains("\\\"name\\\":\\\"Apply greeting\\\""))
        assertTrue(script.contains("var targetName = String(target.name || '')"))
        assertTrue(script.contains("var roleOf = function"))
        assertTrue(script.contains("var accessibleNameOf = function"))
        assertFalse(script.contains("pieces.push(el.value || '')"))
        assertTrue(script.contains("matchesText(name, targetName, match)"))
    }

    @Test
    fun scriptSupportsUrlTargetForFindAndWaitOnly() {
        val waitAction = BrowserAutomationAction(
            actionId = "wait-url",
            sessionId = "session",
            instanceId = null,
            type = BrowserAutomationActionType.WaitFor,
            target = BrowserAutomationTarget(
                kind = BrowserAutomationTargetKind.Url,
                value = "#ready",
                match = BrowserAutomationTarget.MATCH_CONTAINS
            ),
            value = null,
            timeoutMs = 1_000L
        )
        val clickAction = waitAction.copy(
            actionId = "click-url",
            type = BrowserAutomationActionType.Click
        )

        val waitScript = BrowserAutomationActionScript.scriptFor(waitAction)
        val clickScript = BrowserAutomationActionScript.scriptFor(clickAction)

        assertTrue(waitScript.contains("\\\"kind\\\":\\\"url\\\""))
        assertTrue(waitScript.contains("targetKind === 'url'"))
        assertTrue(waitScript.contains("location.href"))
        assertTrue(waitScript.contains("message: 'matched url'"))
        assertTrue(waitScript.contains("url target only supports find and waitFor"))
        assertTrue(clickScript.contains("\\\"type\\\":\\\"click\\\""))
        assertTrue(clickScript.contains("target_not_actionable"))
    }

    @Test
    fun scriptSupportsStateTargetForPageReadiness() {
        val domReadyAction = BrowserAutomationAction(
            actionId = "wait-state",
            sessionId = "session",
            instanceId = null,
            type = BrowserAutomationActionType.WaitFor,
            target = BrowserAutomationTarget(
                kind = BrowserAutomationTargetKind.State,
                value = "domReady"
            ),
            value = null,
            timeoutMs = 1_000L
        )
        val idleAction = domReadyAction.copy(
            actionId = "wait-idle",
            target = BrowserAutomationTarget(
                kind = BrowserAutomationTargetKind.State,
                value = "idle"
            ),
            value = "350"
        )
        val clickAction = domReadyAction.copy(
            actionId = "click-state",
            type = BrowserAutomationActionType.Click
        )

        val domReadyScript = BrowserAutomationActionScript.scriptFor(domReadyAction)
        val idleScript = BrowserAutomationActionScript.scriptFor(idleAction)
        val clickScript = BrowserAutomationActionScript.scriptFor(clickAction)

        assertTrue(domReadyScript.contains("\\\"kind\\\":\\\"state\\\""))
        assertTrue(domReadyScript.contains("targetKind === 'state'"))
        assertTrue(domReadyScript.contains("document.readyState"))
        assertTrue(domReadyScript.contains("state domReady readyState="))
        assertTrue(idleScript.contains("MutationObserver"))
        assertTrue(idleScript.contains("state idle"))
        assertTrue(idleScript.contains("\\\"value\\\":\\\"350\\\""))
        assertTrue(clickScript.contains("state target only supports find and waitFor"))
        assertTrue(clickScript.contains("target_not_actionable"))
    }
}
