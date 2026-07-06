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
class BrowserAutomationSnapshotParserTest {
    @Test
    fun parseEvaluateJavascriptResultDecodesQuotedSnapshotJson() {
        val payload = JSONObject()
            .put("ok", true)
            .put("url", "http://127.0.0.1:8791/page?token=secret")
            .put("title", "Demo")
            .put("readyState", "complete")
            .put("text", "Hello\n\nWorld")
            .put("elementCount", 7)
            .put(
                "elements",
                JSONArray().put(
                    JSONObject()
                        .put("index", 0)
                        .put("tag", "button")
                        .put("role", "button")
                        .put("text", "登录")
                        .put("framePath", "top/frame[0]")
                        .put("frameUrl", "http://127.0.0.1:8791/frame?token=frame-secret")
                        .put("frameName", "Same origin automation frame")
                        .put("shadowPath", "/shadow[3]")
                        .put("shadowHost", "open-shadow-widget #open-shadow-widget")
                        .put("visible", true)
                        .put("enabled", true)
                        .put(
                            "rect",
                            JSONObject()
                                .put("x", 10.5)
                                .put("y", 20.0)
                                .put("width", 88.0)
                                .put("height", 36.0)
                        )
                )
            )
            .put(
                "accessibility",
                JSONArray()
                    .put(
                        JSONObject()
                            .put("index", 0)
                            .put("role", "heading")
                            .put("name", "Demo heading")
                            .put("tag", "h1")
                            .put("level", 1)
                            .put("visible", true)
                            .put("enabled", true)
                            .put(
                                "rect",
                                JSONObject()
                                    .put("x", 12.0)
                                    .put("y", 8.0)
                                    .put("width", 180.0)
                                    .put("height", 32.0)
                            )
                    )
                    .put(
                        JSONObject()
                            .put("index", 1)
                            .put("role", "textbox")
                            .put("name", "Name")
                            .put("tag", "input")
                            .put("type", "text")
                            .put("framePath", "top/frame[0]")
                            .put("frameUrl", "http://127.0.0.1:8791/frame?token=frame-secret")
                            .put("frameName", "Same origin automation frame")
                            .put("frameAccessible", true)
                            .put("shadowPath", "/shadow[3]")
                            .put("shadowHost", "open-shadow-widget #open-shadow-widget")
                            .put("visible", true)
                            .put("enabled", true)
                            .put(
                                "rect",
                                JSONObject()
                                    .put("x", 10.0)
                                    .put("y", 48.0)
                                    .put("width", 120.0)
                                    .put("height", 40.0)
                            )
                    )
            )
        val raw = JSONObject.quote(payload.toString())

        val snapshot = BrowserAutomationSnapshotParser.parseEvaluateJavascriptResult(
            sessionId = "session",
            rawResult = raw,
            fallbackUrl = "http://127.0.0.1:8791/fallback",
            fallbackTitle = "Fallback",
            now = 2_000L
        )

        assertEquals("snap_session_2000", snapshot.snapshotId)
        assertEquals("Demo", snapshot.title)
        assertEquals("complete", snapshot.readyState)
        assertEquals("Hello World", snapshot.text)
        assertEquals(7, snapshot.elementCount)
        assertEquals(1, snapshot.elements.size)
        assertEquals("button", snapshot.elements.single().tag)
        assertEquals("登录", snapshot.elements.single().text)
        assertEquals("top/frame[0]", snapshot.elements.single().framePath)
        assertTrue(snapshot.elements.single().frameUrl.orEmpty().contains("token=present"))
        assertFalse(snapshot.elements.single().frameUrl.orEmpty().contains("frame-secret"))
        assertEquals("Same origin automation frame", snapshot.elements.single().frameName)
        assertEquals("/shadow[3]", snapshot.elements.single().shadowPath)
        assertEquals("open-shadow-widget #open-shadow-widget", snapshot.elements.single().shadowHost)
        assertTrue(snapshot.elements.single().visible)
        assertEquals(2, snapshot.accessibility.size)
        assertEquals("heading", snapshot.accessibility.first().role)
        assertEquals("Demo heading", snapshot.accessibility.first().name)
        assertEquals(1, snapshot.accessibility.first().level)
        assertEquals("textbox", snapshot.accessibility[1].role)
        assertEquals("Name", snapshot.accessibility[1].name)
        assertEquals("top/frame[0]", snapshot.accessibility[1].framePath)
        assertTrue(snapshot.accessibility[1].frameUrl.orEmpty().contains("token=present"))
        assertEquals("Same origin automation frame", snapshot.accessibility[1].frameName)
        assertEquals(true, snapshot.accessibility[1].frameAccessible)
        assertEquals("/shadow[3]", snapshot.accessibility[1].shadowPath)
        assertEquals("open-shadow-widget #open-shadow-widget", snapshot.accessibility[1].shadowHost)
        assertFalse(snapshot.url.contains("secret"))
        assertTrue(snapshot.url.contains("token=present"))
    }

    @Test(expected = IllegalStateException::class)
    fun parseEvaluateJavascriptResultRejectsFailurePayload() {
        val payload = JSONObject()
            .put("ok", false)
            .put("error", "script_failed")

        BrowserAutomationSnapshotParser.parseEvaluateJavascriptResult(
            sessionId = "session",
            rawResult = JSONObject.quote(payload.toString()),
            fallbackUrl = "http://127.0.0.1:8791",
            fallbackTitle = null,
            now = 2_000L
        )
    }
}
