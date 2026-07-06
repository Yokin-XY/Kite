package com.kite.app.browser.automation

import org.json.JSONArray
import org.json.JSONObject

object BrowserAutomationObservation {
    fun toJson(
        session: BrowserAutomationSession,
        snapshot: BrowserAutomationSnapshot?,
        recentAction: BrowserAutomationActionResult?,
        recentRun: BrowserAutomationRunResult?,
        interactiveLimit: Int = DEFAULT_INTERACTIVE_LIMIT,
        textLimit: Int = DEFAULT_TEXT_LIMIT
    ): JSONObject {
        val nodeLimit = interactiveLimit.coerceIn(0, MAX_INTERACTIVE_LIMIT)
        val previewLimit = textLimit.coerceIn(0, MAX_TEXT_LIMIT)
        val interactiveNodes = snapshot
            ?.accessibility
            .orEmpty()
            .asSequence()
            .filter { it.visible && it.name.isNotBlank() && it.role.isNotBlank() }
            .filter { it.role.lowercase() in OBSERVABLE_ROLES }
            .sortedBy { it.index }
            .toList()
        val targetIndexes = duplicateTargetIndexes(interactiveNodes)
        return JSONObject()
            .put("ok", true)
            .put("session", session.toObservationJson())
            .put("page", pageJson(session, snapshot, previewLimit))
            .put("interactive", JSONArray().apply {
                interactiveNodes
                    .asSequence()
                    .take(nodeLimit)
                    .forEach { node ->
                        put(node.toObservationJson(targetIndexes[node.index] ?: 0))
                    }
            })
            .put("recentAction", recentAction?.toObservationJson() ?: JSONObject.NULL)
            .put("recentRun", recentRun?.toObservationJson() ?: JSONObject.NULL)
            .put("capabilities", BrowserAutomationCapabilities.toObservationJson())
            .put(
                "limits",
                JSONObject()
                    .put("textLimit", previewLimit)
                    .put("interactiveLimit", nodeLimit)
                    .put("source", "BrowserAutomationSessionStore")
            )
            .put("authBoundary", BrowserAutomationCapabilities.AUTH_BOUNDARY)
    }

    private fun BrowserAutomationSession.toObservationJson(): JSONObject =
        JSONObject()
            .put("sessionId", sessionId)
            .put("instanceId", instanceId.orEmpty())
            .put("source", source.orEmpty())
            .put("url", BrowserAutomationRedactor.redactUrl(url))
            .put("mode", mode)
            .put("status", status.name)
            .put("updatedAt", updatedAt)
            .put("lastActionId", lastActionId.orEmpty())
            .put("lastSnapshotId", lastSnapshotId.orEmpty())
            .put("lastError", BrowserAutomationRedactor.safeText(lastError, MAX_ERROR_CHARS))

    private fun pageJson(
        session: BrowserAutomationSession,
        snapshot: BrowserAutomationSnapshot?,
        textLimit: Int
    ): JSONObject {
        val url = snapshot?.url ?: session.url
        return JSONObject()
            .put("snapshotReady", snapshot != null)
            .put("snapshotId", snapshot?.snapshotId.orEmpty())
            .put("url", BrowserAutomationRedactor.redactUrl(url))
            .put("scope", BrowserAutomationPageTrust.scope(url))
            .put("trustedForEvaluate", BrowserAutomationPageTrust.evaluateAllowed(url))
            .put("title", BrowserAutomationRedactor.safeText(snapshot?.title, MAX_TITLE_CHARS))
            .put("readyState", snapshot?.readyState.orEmpty())
            .put("text", BrowserAutomationRedactor.safeText(snapshot?.text, textLimit))
            .put("elementCount", snapshot?.elementCount ?: 0)
            .put("accessibilityCount", snapshot?.accessibility?.size ?: 0)
            .put("capturedAt", snapshot?.capturedAt ?: 0L)
    }

    private fun BrowserAutomationAccessibilityNode.toObservationJson(targetIndex: Int): JSONObject =
        JSONObject()
            .put("index", index)
            .put("role", role)
            .put("name", BrowserAutomationRedactor.safeText(name, MAX_NAME_CHARS))
            .put("tag", tag)
            .put("type", type.orEmpty())
            .put("enabled", enabled)
            .put("state", stateJson())
            .put("rect", rectJson())
            .put("frame", frameJson())
            .put("shadow", shadowJson())
            .put("suggestedTarget", suggestedTargetJson(targetIndex))
            .put("suggestedActions", JSONArray().apply {
                suggestedActions().forEach { put(it) }
            })

    private fun BrowserAutomationAccessibilityNode.stateJson(): JSONObject =
        JSONObject()
            .put("checked", checked.orEmpty())
            .put("selected", selected ?: JSONObject.NULL)
            .put("expanded", expanded ?: JSONObject.NULL)

    private fun BrowserAutomationAccessibilityNode.rectJson(): JSONObject =
        JSONObject()
            .put("x", x)
            .put("y", y)
            .put("width", width)
            .put("height", height)

    private fun BrowserAutomationAccessibilityNode.frameJson(): Any =
        if (framePath.isNullOrBlank()) {
            JSONObject.NULL
        } else {
            JSONObject()
                .put("path", framePath.orEmpty())
                .put("url", BrowserAutomationRedactor.redactUrl(frameUrl.orEmpty()))
                .put("name", BrowserAutomationRedactor.safeText(frameName, MAX_NAME_CHARS))
                .put("accessible", frameAccessible ?: JSONObject.NULL)
        }

    private fun BrowserAutomationAccessibilityNode.shadowJson(): Any =
        if (shadowPath.isNullOrBlank()) {
            JSONObject.NULL
        } else {
            JSONObject()
                .put("path", shadowPath.orEmpty())
                .put("host", BrowserAutomationRedactor.safeText(shadowHost, MAX_NAME_CHARS))
        }

    private fun BrowserAutomationAccessibilityNode.suggestedTargetJson(targetIndex: Int): JSONObject =
        JSONObject()
            .put("kind", BrowserAutomationTargetKind.Role.wireName)
            .put("value", role)
            .put("name", BrowserAutomationRedactor.safeText(name, MAX_NAME_CHARS))
            .put("index", targetIndex.coerceAtLeast(0))

    private fun duplicateTargetIndexes(nodes: List<BrowserAutomationAccessibilityNode>): Map<Int, Int> {
        val counts = mutableMapOf<String, Int>()
        return nodes.associate { node ->
            val key = "${node.role.lowercase()}\u0000${node.name.lowercase()}"
            val targetIndex = counts.getOrDefault(key, 0)
            counts[key] = targetIndex + 1
            node.index to targetIndex
        }
    }

    private fun BrowserAutomationAccessibilityNode.suggestedActions(): List<String> {
        val roleKey = role.lowercase()
        return when {
            !enabled -> listOf("find")
            roleKey in TEXT_INPUT_ROLES -> listOf("type", "clear", "press", "find", "waitFor")
            roleKey == "combobox" -> listOf("select", "click", "find", "waitFor")
            roleKey in CHECKABLE_ROLES -> listOf("check", "click", "find", "waitFor")
            roleKey in CLICKABLE_ROLES -> listOf("click", "hover", "doubleClick", "find", "waitFor")
            roleKey == "region" -> listOf("scroll", "find")
            roleKey == "iframe" -> listOf("find")
            roleKey in WAITABLE_ROLES -> listOf("waitFor", "find")
            else -> listOf("find")
        }
    }

    private fun BrowserAutomationActionResult.toObservationJson(): JSONObject {
        val publicJson = toJson()
        return JSONObject()
            .put("actionId", actionId)
            .put("sessionId", sessionId)
            .put("type", type.wireName)
            .put("status", status.name)
            .put("message", BrowserAutomationRedactor.safeText(message, MAX_ERROR_CHARS))
            .put("snapshotId", snapshotId.orEmpty())
            .put("artifactPath", publicJson.optString("artifactPath"))
            .put("artifactUrl", publicJson.optString("artifactUrl"))
            .put("errorCode", errorCode.orEmpty())
            .put("errorDetail", BrowserAutomationRedactor.safeText(errorDetail, MAX_ERROR_CHARS))
            .put("completedAt", completedAt)
    }

    private fun BrowserAutomationRunResult.toObservationJson(): JSONObject {
        val artifacts = results.filter { !it.artifactPath.isNullOrBlank() }
        val latestArtifact = artifacts.lastOrNull()?.toJson()
        return JSONObject()
            .put("runId", runId)
            .put("sessionId", sessionId.orEmpty())
            .put("status", status.name)
            .put("requestedCount", requestedCount.coerceAtLeast(0))
            .put("completedCount", completedCount.coerceAtLeast(0))
            .put("stoppedOnFailure", stoppedOnFailure)
            .put("artifactCount", artifacts.size)
            .put("latestArtifactPath", latestArtifact?.optString("artifactPath").orEmpty())
            .put("latestArtifactUrl", latestArtifact?.optString("artifactUrl").orEmpty())
            .put("errorCode", results.firstOrNull { !it.succeeded }?.errorCode.orEmpty())
            .put("errorDetail", BrowserAutomationRedactor.safeText(results.firstOrNull { !it.succeeded }?.errorDetail, MAX_ERROR_CHARS))
            .put("completedAt", completedAt)
    }

    private val TEXT_INPUT_ROLES = setOf("textbox", "searchbox")
    private val CHECKABLE_ROLES = setOf("checkbox", "radio", "switch")
    private val CLICKABLE_ROLES = setOf(
        "button",
        "link",
        "checkbox",
        "radio",
        "switch",
        "combobox",
        "tab",
        "menuitem",
        "slider",
        "spinbutton"
    )
    private val WAITABLE_ROLES = setOf("status", "heading")
    private val OBSERVABLE_ROLES = TEXT_INPUT_ROLES + CLICKABLE_ROLES + WAITABLE_ROLES + setOf("region", "iframe")

    private const val DEFAULT_INTERACTIVE_LIMIT = 30
    private const val MAX_INTERACTIVE_LIMIT = 80
    private const val DEFAULT_TEXT_LIMIT = 1200
    private const val MAX_TEXT_LIMIT = 4000
    private const val MAX_NAME_CHARS = 200
    private const val MAX_TITLE_CHARS = 160
    private const val MAX_ERROR_CHARS = 500
}
