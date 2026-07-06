package com.kite.app.browser.automation

import org.json.JSONArray
import org.json.JSONObject

object BrowserAutomationSnapshotParser {
    fun parseEvaluateJavascriptResult(
        sessionId: String,
        rawResult: String?,
        fallbackUrl: String,
        fallbackTitle: String?,
        now: Long = System.currentTimeMillis()
    ): BrowserAutomationSnapshot {
        val decoded = decodeEvaluateJavascriptResult(rawResult)
        val json = JSONObject(decoded)
        if (!json.optBoolean("ok", false)) {
            throw IllegalStateException(
                json.optString("error").takeIf { it.isNotBlank() } ?: "snapshot_failed"
            )
        }
        val elementsJson = json.optJSONArray("elements") ?: JSONArray()
        val elements = buildList {
            for (index in 0 until elementsJson.length()) {
                elementsJson.optJSONObject(index)?.toElementSummaryOrNull()?.let { add(it) }
            }
        }
        val accessibilityJson = json.optJSONArray("accessibility") ?: JSONArray()
        val accessibility = buildList {
            for (index in 0 until accessibilityJson.length()) {
                accessibilityJson.optJSONObject(index)?.toAccessibilityNodeOrNull()?.let { add(it) }
            }
        }
        return BrowserAutomationSnapshot(
            snapshotId = "snap_${sessionId.take(8)}_$now",
            sessionId = sessionId,
            url = BrowserAutomationRedactor.redactUrl(
                json.optString("url").takeIf { it.isNotBlank() } ?: fallbackUrl
            ),
            title = json.optString("title").takeIf { it.isNotBlank() } ?: fallbackTitle,
            readyState = json.optString("readyState").takeIf { it.isNotBlank() },
            text = BrowserAutomationRedactor.safeText(json.optString("text"), MAX_TEXT_CHARS),
            elementCount = json.optInt("elementCount", elements.size),
            elements = elements.take(MAX_ELEMENTS),
            accessibility = accessibility.take(MAX_ACCESSIBILITY_NODES),
            capturedAt = now
        )
    }

    private fun decodeEvaluateJavascriptResult(rawResult: String?): String {
        val raw = rawResult?.trim().orEmpty()
        if (raw.isBlank() || raw == "null") {
            throw IllegalStateException("empty_snapshot_result")
        }
        return if (raw.startsWith("\"")) {
            JSONArray("[$raw]").optString(0)
        } else {
            raw
        }.takeIf { it.isNotBlank() } ?: throw IllegalStateException("empty_snapshot_json")
    }

    private fun JSONObject.toElementSummaryOrNull(): BrowserAutomationElementSummary? {
        val tag = optString("tag").takeIf { it.isNotBlank() } ?: return null
        val rect = optJSONObject("rect") ?: JSONObject()
        return BrowserAutomationElementSummary(
            index = optInt("index", 0),
            tag = tag.take(MAX_TAG_CHARS),
            type = optString("type").takeIf { it.isNotBlank() }?.take(MAX_ATTR_CHARS),
            role = optString("role").takeIf { it.isNotBlank() }?.take(MAX_ATTR_CHARS),
            text = BrowserAutomationRedactor.safeText(optString("text"), MAX_ELEMENT_TEXT_CHARS)
                .takeIf { it.isNotBlank() },
            placeholder = BrowserAutomationRedactor.safeText(optString("placeholder"), MAX_ELEMENT_TEXT_CHARS)
                .takeIf { it.isNotBlank() },
            ariaLabel = BrowserAutomationRedactor.safeText(optString("ariaLabel"), MAX_ELEMENT_TEXT_CHARS)
                .takeIf { it.isNotBlank() },
            visible = optBoolean("visible", false),
            enabled = optBoolean("enabled", true),
            x = rect.optDouble("x", 0.0),
            y = rect.optDouble("y", 0.0),
            width = rect.optDouble("width", 0.0),
            height = rect.optDouble("height", 0.0),
            framePath = optString("framePath").takeIf { it.isNotBlank() }?.take(MAX_FRAME_CHARS),
            frameUrl = BrowserAutomationRedactor.redactUrl(optString("frameUrl")).takeIf { it.isNotBlank() },
            frameName = BrowserAutomationRedactor.safeText(optString("frameName"), MAX_FRAME_CHARS)
                .takeIf { it.isNotBlank() },
            shadowPath = optString("shadowPath").takeIf { it.isNotBlank() }?.take(MAX_SHADOW_CHARS),
            shadowHost = BrowserAutomationRedactor.safeText(optString("shadowHost"), MAX_SHADOW_CHARS)
                .takeIf { it.isNotBlank() }
        )
    }

    private fun JSONObject.toAccessibilityNodeOrNull(): BrowserAutomationAccessibilityNode? {
        val role = optString("role").takeIf { it.isNotBlank() } ?: return null
        val name = BrowserAutomationRedactor.safeText(optString("name"), MAX_ACCESSIBILITY_NAME_CHARS)
        if (name.isBlank() && role == "generic") return null
        val rect = optJSONObject("rect") ?: JSONObject()
        return BrowserAutomationAccessibilityNode(
            index = optInt("index", 0),
            role = role.take(MAX_ROLE_CHARS),
            name = name,
            tag = optString("tag").takeIf { it.isNotBlank() }?.take(MAX_TAG_CHARS) ?: "node",
            type = optString("type").takeIf { it.isNotBlank() }?.take(MAX_ATTR_CHARS),
            level = optInt("level").takeIf { has("level") && it > 0 },
            visible = optBoolean("visible", false),
            enabled = optBoolean("enabled", true),
            checked = optString("checked").takeIf { it.isNotBlank() }?.take(MAX_STATE_CHARS),
            selected = optBoolean("selected").takeIf { has("selected") && !isNull("selected") },
            expanded = optBoolean("expanded").takeIf { has("expanded") && !isNull("expanded") },
            x = rect.optDouble("x", 0.0),
            y = rect.optDouble("y", 0.0),
            width = rect.optDouble("width", 0.0),
            height = rect.optDouble("height", 0.0),
            framePath = optString("framePath").takeIf { it.isNotBlank() }?.take(MAX_FRAME_CHARS),
            frameUrl = BrowserAutomationRedactor.redactUrl(optString("frameUrl")).takeIf { it.isNotBlank() },
            frameName = BrowserAutomationRedactor.safeText(optString("frameName"), MAX_FRAME_CHARS)
                .takeIf { it.isNotBlank() },
            frameAccessible = optBoolean("frameAccessible").takeIf { has("frameAccessible") && !isNull("frameAccessible") },
            shadowPath = optString("shadowPath").takeIf { it.isNotBlank() }?.take(MAX_SHADOW_CHARS),
            shadowHost = BrowserAutomationRedactor.safeText(optString("shadowHost"), MAX_SHADOW_CHARS)
                .takeIf { it.isNotBlank() }
        )
    }

    private const val MAX_TEXT_CHARS = 4000
    private const val MAX_ELEMENTS = 80
    private const val MAX_ACCESSIBILITY_NODES = 120
    private const val MAX_TAG_CHARS = 32
    private const val MAX_ATTR_CHARS = 80
    private const val MAX_ELEMENT_TEXT_CHARS = 160
    private const val MAX_ACCESSIBILITY_NAME_CHARS = 200
    private const val MAX_ROLE_CHARS = 80
    private const val MAX_STATE_CHARS = 32
    private const val MAX_FRAME_CHARS = 160
    private const val MAX_SHADOW_CHARS = 160
}
