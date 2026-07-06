package com.kite.app.browser.automation

import org.json.JSONArray
import org.json.JSONObject

object BrowserAutomationCapabilities {
    val actions = listOf(
        "snapshot",
        "find",
        "click",
        "doubleClick",
        "hover",
        "navigate",
        "type",
        "clear",
        "press",
        "select",
        "check",
        "waitFor",
        "scroll",
        "evaluate",
        "screenshot"
    )
    val targets = listOf("css", "text", "role", "role+name", "url", "state")
    val runs = listOf("sequential", "stopOnFailure")
    val endpoints = listOf(
        "/browser-automation/action",
        "/browser-automation/run",
        "/browser-automation/open-run",
        "/browser-automation/observe",
        "/browser-automation/artifact",
        "/browser-automation/runs",
        "/browser-automation/actions",
        "/browser-automation/sessions",
        "/browser-automation/session",
        "/browser-automation/console",
        "/browser-automation/network",
        "/browser-automation/test-page"
    )

    const val AUTH_BOUNDARY = "oauth_and_sso_stay_external"
    const val EVALUATE_BOUNDARY = "local_trusted_only"

    fun toEndpointJson(): JSONObject =
        toJson()
            .put("ok", true)
            .put("actionList", actions.toJsonArray())
            .put("targetList", targets.toJsonArray())
            .put("runList", runs.toJsonArray())
            .put("endpointList", endpoints.toJsonArray())
            .put("actions", actions.joinToString(","))
            .put("targets", targets.joinToString(","))
            .put("runs", runs.joinToString(","))
            .put("endpoints", endpoints.joinToString(","))

    fun toObservationJson(): JSONObject =
        toJson()
            .put("source", "BrowserAutomationCapabilities")

    private fun toJson(): JSONObject =
        JSONObject()
            .put("actions", actions.toJsonArray())
            .put("targets", targets.toJsonArray())
            .put("runs", runs.toJsonArray())
            .put("endpoints", endpoints.toJsonArray())
            .put("authBoundary", AUTH_BOUNDARY)
            .put("evaluate", EVALUATE_BOUNDARY)

    private fun List<String>.toJsonArray(): JSONArray =
        JSONArray().apply {
            forEach { put(it) }
        }
}
