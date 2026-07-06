package com.kite.app.browser.automation

import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.Locale
import java.util.UUID

enum class BrowserAutomationSessionStatus {
    Opening,
    Ready,
    RunningAction,
    Waiting,
    Failed,
    Closed
}

enum class BrowserAutomationEventKind {
    SessionOpening,
    SnapshotReady,
    ActionFinished,
    Failed
}

enum class BrowserAutomationActionType(val wireName: String) {
    Snapshot("snapshot"),
    Find("find"),
    Click("click"),
    DoubleClick("doubleClick"),
    Hover("hover"),
    Navigate("navigate"),
    TypeText("type"),
    Clear("clear"),
    Press("press"),
    Select("select"),
    Check("check"),
    WaitFor("waitFor"),
    Scroll("scroll"),
    Evaluate("evaluate"),
    Screenshot("screenshot");

    companion object {
        fun fromWireName(value: String?): BrowserAutomationActionType? =
            values().firstOrNull { it.wireName.equals(value.orEmpty(), ignoreCase = true) }
    }
}

enum class BrowserAutomationTargetKind(val wireName: String) {
    Css("css"),
    Text("text"),
    Role("role"),
    Url("url"),
    State("state"),
    None("none");

    companion object {
        fun fromWireName(value: String?): BrowserAutomationTargetKind =
            values().firstOrNull { it.wireName.equals(value.orEmpty(), ignoreCase = true) } ?: None
    }
}

enum class BrowserAutomationResultStatus {
    Succeeded,
    Failed,
    TimedOut,
    Rejected
}

data class BrowserAutomationSession(
    val sessionId: String,
    val recipeId: String?,
    val recipeName: String?,
    val instanceId: String?,
    val source: String?,
    val url: String,
    val mode: String = MODE_WEBVIEW,
    val status: BrowserAutomationSessionStatus,
    val createdAt: Long,
    val updatedAt: Long,
    val lastActionId: String? = null,
    val lastSnapshotId: String? = null,
    val lastError: String? = null
) {
    companion object {
        const val MODE_WEBVIEW = "webview"
    }
}

data class BrowserAutomationTarget(
    val kind: BrowserAutomationTargetKind,
    val value: String = "",
    val match: String = MATCH_CONTAINS,
    val index: Int = 0,
    val name: String? = null
) {
    fun summary(): String =
        when (kind) {
            BrowserAutomationTargetKind.None -> "none"
            else -> buildString {
                append(kind.wireName)
                append("=")
                append(BrowserAutomationRedactor.safeText(value, 80))
                name?.takeIf { it.isNotBlank() }?.let {
                    append(" name=")
                    append(BrowserAutomationRedactor.safeText(it, 80))
                }
            }
        }

    fun toJson(): JSONObject =
        JSONObject()
            .put("kind", kind.wireName)
            .put("value", value.take(MAX_TARGET_VALUE_CHARS))
            .put("match", match.take(MAX_MATCH_CHARS))
            .put("index", index.coerceAtLeast(0))
            .put("name", name.orEmpty().take(MAX_TARGET_VALUE_CHARS))

    companion object {
        const val MATCH_CONTAINS = "contains"
        const val MATCH_EXACT = "exact"
        private const val MAX_TARGET_VALUE_CHARS = 500
        private const val MAX_MATCH_CHARS = 16

        fun fromJson(json: JSONObject?): BrowserAutomationTarget {
            if (json == null) return BrowserAutomationTarget(BrowserAutomationTargetKind.None)
            return BrowserAutomationTarget(
                kind = BrowserAutomationTargetKind.fromWireName(json.optString("kind")),
                value = json.optString("value").take(MAX_TARGET_VALUE_CHARS),
                match = json.optString("match").ifBlank { MATCH_CONTAINS }.take(MAX_MATCH_CHARS),
                index = json.optInt("index", 0).coerceAtLeast(0),
                name = json.optString("name")
                    .take(MAX_TARGET_VALUE_CHARS)
                    .takeIf { it.isNotBlank() }
            )
        }
    }
}

data class BrowserAutomationAction(
    val actionId: String,
    val sessionId: String?,
    val instanceId: String?,
    val type: BrowserAutomationActionType,
    val target: BrowserAutomationTarget,
    val value: String?,
    val timeoutMs: Long,
    val trusted: Boolean = false
) {
    fun toJson(): JSONObject =
        JSONObject()
            .put("actionId", actionId)
            .put("sessionId", sessionId.orEmpty())
            .put("instanceId", instanceId.orEmpty())
            .put("type", type.wireName)
            .put("target", target.toJson())
            .put("value", value.orEmpty().take(MAX_ACTION_VALUE_CHARS))
            .put("timeoutMs", timeoutMs.coerceIn(MIN_TIMEOUT_MS, MAX_TIMEOUT_MS))
            .put("trusted", trusted)

    fun displaySummary(): String =
        "${type.wireName} ${target.summary()}".take(160)

    companion object {
        private const val MIN_TIMEOUT_MS = 250L
        private const val DEFAULT_TIMEOUT_MS = 8_000L
        private const val MAX_TIMEOUT_MS = 15_000L
        private const val MAX_ACTION_VALUE_CHARS = 4000

        fun fromJson(json: JSONObject): BrowserAutomationAction? {
            val type = BrowserAutomationActionType.fromWireName(json.optString("type")) ?: return null
            val targetJson = json.optJSONObject("target") ?: JSONObject().apply {
                put("kind", json.optString("targetKind"))
                put("value", json.optString("target"))
                put("name", json.optString("name").ifBlank { json.optString("targetName") })
                put("match", json.optString("match"))
                put("index", json.optInt("index", 0))
            }
            return BrowserAutomationAction(
                actionId = json.optString("actionId").takeIf { it.isNotBlank() }
                    ?: "act_${UUID.randomUUID().toString().replace("-", "")}",
                sessionId = json.optString("sessionId").takeIf { it.isNotBlank() },
                instanceId = json.optString("instanceId")
                    .takeIf { it.isNotBlank() }
                    ?: json.optString("cardInstanceId").takeIf { it.isNotBlank() },
                type = type,
                target = BrowserAutomationTarget.fromJson(targetJson),
                value = json.optString("value").takeIf { it.isNotEmpty() }?.take(MAX_ACTION_VALUE_CHARS),
                timeoutMs = json.optLong("timeoutMs", DEFAULT_TIMEOUT_MS).coerceIn(MIN_TIMEOUT_MS, MAX_TIMEOUT_MS),
                trusted = json.optBoolean("trusted", false)
            )
        }
    }
}

data class BrowserAutomationActionResult(
    val actionId: String,
    val sessionId: String,
    val type: BrowserAutomationActionType,
    val status: BrowserAutomationResultStatus,
    val durationMs: Long,
    val url: String,
    val title: String?,
    val message: String,
    val matchedCount: Int = 0,
    val snapshotId: String? = null,
    val artifactPath: String? = null,
    val errorCode: String? = null,
    val errorDetail: String? = null,
    val completedAt: Long = System.currentTimeMillis()
) {
    val succeeded: Boolean get() = status == BrowserAutomationResultStatus.Succeeded

    fun toJson(): JSONObject =
        JSONObject()
            .put("actionId", actionId)
            .put("sessionId", sessionId)
            .put("type", type.wireName)
            .put("status", status.name)
            .put("durationMs", durationMs.coerceAtLeast(0L))
            .put("url", BrowserAutomationRedactor.redactUrl(url))
            .put("title", title.orEmpty().take(160))
            .put("message", BrowserAutomationRedactor.safeText(message, 500))
            .put("matchedCount", matchedCount.coerceAtLeast(0))
            .put("snapshotId", snapshotId.orEmpty())
            .put("artifactPath", artifactPath.orEmpty().take(500))
            .put("artifactUrl", artifactUrl().orEmpty())
            .put("errorCode", errorCode.orEmpty().take(80))
            .put("errorDetail", BrowserAutomationRedactor.safeText(errorDetail, 500))
            .put("completedAt", completedAt)

    private fun artifactUrl(): String? {
        val path = artifactPath?.takeIf { it.isNotBlank() } ?: return null
        return "/browser-automation/artifact?path=${URLEncoder.encode(path.take(500), Charsets.UTF_8.name())}"
    }
}

data class BrowserAutomationRunRequest(
    val runId: String,
    val sessionId: String?,
    val instanceId: String?,
    val stopOnFailure: Boolean,
    val actions: List<BrowserAutomationAction>
) {
    fun withSession(session: BrowserAutomationSession): BrowserAutomationRunRequest =
        copy(
            sessionId = session.sessionId,
            instanceId = session.instanceId ?: instanceId,
            actions = actions.map { action ->
                if (!action.sessionId.isNullOrBlank() || !action.instanceId.isNullOrBlank()) {
                    action
                } else {
                    action.copy(
                        sessionId = session.sessionId,
                        instanceId = session.instanceId
                    )
                }
            }
        )

    companion object {
        private const val MAX_RUN_ACTIONS = 20

        fun fromJson(json: JSONObject): BrowserAutomationRunRequest? {
            val actionsJson = json.optJSONArray("actions") ?: return null
            if (actionsJson.length() <= 0 || actionsJson.length() > MAX_RUN_ACTIONS) return null
            val runId = json.optString("runId").takeIf { it.isNotBlank() }
                ?: "run_${UUID.randomUUID().toString().replace("-", "")}"
            val sessionId = json.optString("sessionId").takeIf { it.isNotBlank() }
            val instanceId = json.optString("instanceId").takeIf { it.isNotBlank() }
                ?: json.optString("cardInstanceId").takeIf { it.isNotBlank() }
            val actions = buildList {
                for (index in 0 until actionsJson.length()) {
                    val actionJson = actionsJson.optJSONObject(index) ?: return null
                    val merged = JSONObject(actionJson.toString())
                    if (!merged.has("sessionId") && !sessionId.isNullOrBlank()) {
                        merged.put("sessionId", sessionId)
                    }
                    if (!merged.has("instanceId") && !instanceId.isNullOrBlank()) {
                        merged.put("instanceId", instanceId)
                    }
                    val action = BrowserAutomationAction.fromJson(merged) ?: return null
                    add(action)
                }
            }
            return BrowserAutomationRunRequest(
                runId = runId,
                sessionId = sessionId,
                instanceId = instanceId,
                stopOnFailure = json.optBoolean("stopOnFailure", true),
                actions = actions
            )
        }
    }
}

data class BrowserAutomationOpenRunRequest(
    val url: String,
    val source: String,
    val recipeId: String?,
    val instanceId: String?,
    val openTimeoutMs: Long,
    val runRequest: BrowserAutomationRunRequest
) {
    companion object {
        private const val DEFAULT_OPEN_TIMEOUT_MS = 15_000L
        private const val MIN_OPEN_TIMEOUT_MS = 1_000L
        private const val MAX_OPEN_TIMEOUT_MS = 30_000L

        fun fromJson(json: JSONObject): BrowserAutomationOpenRunRequest? {
            val url = json.optString("url").trim().takeIf { it.isNotBlank() } ?: return null
            val runRequest = BrowserAutomationRunRequest.fromJson(json) ?: return null
            return BrowserAutomationOpenRunRequest(
                url = url,
                source = json.optString("source").ifBlank { "browser_automation_open_run" },
                recipeId = json.optString("recipeId").takeIf { it.isNotBlank() },
                instanceId = json.optString("instanceId").takeIf { it.isNotBlank() }
                    ?: json.optString("cardInstanceId").takeIf { it.isNotBlank() },
                openTimeoutMs = json.optLong("openTimeoutMs", DEFAULT_OPEN_TIMEOUT_MS)
                    .coerceIn(MIN_OPEN_TIMEOUT_MS, MAX_OPEN_TIMEOUT_MS),
                runRequest = runRequest
            )
        }
    }
}

data class BrowserAutomationRunResult(
    val runId: String,
    val sessionId: String?,
    val status: BrowserAutomationResultStatus,
    val durationMs: Long,
    val requestedCount: Int,
    val completedCount: Int,
    val stoppedOnFailure: Boolean,
    val results: List<BrowserAutomationActionResult>,
    val completedAt: Long = System.currentTimeMillis()
) {
    val succeeded: Boolean get() = status == BrowserAutomationResultStatus.Succeeded

    fun toJson(): JSONObject =
        JSONObject()
            .put("runId", runId)
            .put("sessionId", sessionId.orEmpty())
            .put("status", status.name)
            .put("durationMs", durationMs.coerceAtLeast(0L))
            .put("requestedCount", requestedCount.coerceAtLeast(0))
            .put("completedCount", completedCount.coerceAtLeast(0))
            .put("stoppedOnFailure", stoppedOnFailure)
            .put("errorCode", results.firstOrNull { !it.succeeded }?.errorCode.orEmpty())
            .put("errorDetail", BrowserAutomationRedactor.safeText(results.firstOrNull { !it.succeeded }?.errorDetail, 500))
            .put("completedAt", completedAt)
            .put("results", JSONArray().apply {
                results.forEach { put(it.toJson()) }
            })

    companion object {
        fun fromResults(
            request: BrowserAutomationRunRequest,
            startedAt: Long,
            results: List<BrowserAutomationActionResult>
        ): BrowserAutomationRunResult {
            val failed = results.firstOrNull { !it.succeeded }
            val completedAt = System.currentTimeMillis()
            val status = when {
                failed == null && results.size == request.actions.size -> BrowserAutomationResultStatus.Succeeded
                failed?.status == BrowserAutomationResultStatus.Rejected -> BrowserAutomationResultStatus.Rejected
                failed?.status == BrowserAutomationResultStatus.TimedOut -> BrowserAutomationResultStatus.TimedOut
                else -> BrowserAutomationResultStatus.Failed
            }
            return BrowserAutomationRunResult(
                runId = request.runId,
                sessionId = results.firstOrNull()?.sessionId
                    ?: request.sessionId
                    ?: request.instanceId,
                status = status,
                durationMs = completedAt - startedAt,
                requestedCount = request.actions.size,
                completedCount = results.size,
                stoppedOnFailure = request.stopOnFailure && failed != null && results.size < request.actions.size,
                results = results,
                completedAt = completedAt
            )
        }
    }
}

data class BrowserAutomationConsoleEntry(
    val entryId: String,
    val sessionId: String,
    val level: String,
    val message: String,
    val sourceId: String?,
    val lineNumber: Int,
    val capturedAt: Long
)

data class BrowserAutomationNetworkEntry(
    val entryId: String,
    val sessionId: String,
    val kind: String,
    val method: String,
    val url: String,
    val isForMainFrame: Boolean,
    val statusCode: Int? = null,
    val reasonPhrase: String? = null,
    val capturedAt: Long
)

data class BrowserAutomationSnapshot(
    val snapshotId: String,
    val sessionId: String,
    val url: String,
    val title: String?,
    val readyState: String?,
    val text: String,
    val elementCount: Int,
    val elements: List<BrowserAutomationElementSummary>,
    val accessibility: List<BrowserAutomationAccessibilityNode> = emptyList(),
    val capturedAt: Long
)

data class BrowserAutomationAccessibilityNode(
    val index: Int,
    val role: String,
    val name: String,
    val tag: String,
    val type: String?,
    val level: Int?,
    val visible: Boolean,
    val enabled: Boolean,
    val checked: String?,
    val selected: Boolean?,
    val expanded: Boolean?,
    val x: Double,
    val y: Double,
    val width: Double,
    val height: Double,
    val framePath: String? = null,
    val frameUrl: String? = null,
    val frameName: String? = null,
    val frameAccessible: Boolean? = null,
    val shadowPath: String? = null,
    val shadowHost: String? = null
)

data class BrowserAutomationElementSummary(
    val index: Int,
    val tag: String,
    val type: String?,
    val role: String?,
    val text: String?,
    val placeholder: String?,
    val ariaLabel: String?,
    val visible: Boolean,
    val enabled: Boolean,
    val x: Double,
    val y: Double,
    val width: Double,
    val height: Double,
    val framePath: String? = null,
    val frameUrl: String? = null,
    val frameName: String? = null,
    val shadowPath: String? = null,
    val shadowHost: String? = null
)

data class BrowserAutomationEvent(
    val kind: BrowserAutomationEventKind,
    val session: BrowserAutomationSession,
    val snapshot: BrowserAutomationSnapshot? = null,
    val actionResult: BrowserAutomationActionResult? = null,
    val message: String,
    val errorCode: String? = null
)

object BrowserAutomationRedactor {
    private val sensitiveQueryKeys = setOf(
        "access_token",
        "auth",
        "authorization",
        "client_secret",
        "code",
        "code_challenge",
        "id_token",
        "key",
        "password",
        "refresh_token",
        "secret",
        "session",
        "state",
        "token"
    )
    private val sensitiveAssignmentPattern = Regex(
        "\\b(access_token|auth|authorization|client_secret|code|id_token|key|password|refresh_token|secret|session|state|token)\\b\\s*([=:])\\s*(?:Bearer\\s+)?[^\\s&;,]+",
        RegexOption.IGNORE_CASE
    )
    private val bearerPattern = Regex("\\bBearer\\s+[^\\s&;,]+", RegexOption.IGNORE_CASE)

    fun redactUrl(url: String): String {
        val trimmed = url.trim()
        if (trimmed.isBlank()) return ""
        val parsed = runCatching { java.net.URI(trimmed) }.getOrNull() ?: return trimmed.take(MAX_URL_CHARS)
        val query = parsed.rawQuery
        val userInfo = parsed.rawUserInfo
        if (query.isNullOrBlank() && userInfo.isNullOrBlank()) return trimmed.take(MAX_URL_CHARS)
        val redactedQuery = query
            ?.split("&")
            ?.joinToString("&") { pair ->
                val rawKey = pair.substringBefore("=")
                val key = decode(rawKey).lowercase(Locale.US)
                if (key in sensitiveQueryKeys || sensitiveQueryKeys.any { key.contains(it) }) {
                    "$rawKey=present"
                } else {
                    pair
                }
            }
        return buildString {
            append(parsed.scheme ?: "http")
            append("://")
            if (!userInfo.isNullOrBlank()) append("user:present@")
            append(parsed.host.orEmpty())
            if (parsed.port > 0) append(":").append(parsed.port)
            append(parsed.rawPath.orEmpty().ifBlank { "/" })
            if (!redactedQuery.isNullOrBlank()) append("?").append(redactedQuery)
            if (!parsed.rawFragment.isNullOrBlank()) append("#present")
        }.take(MAX_URL_CHARS)
    }

    fun safeText(value: String?, limit: Int): String =
        value.orEmpty()
            .replace(Regex("\\s+"), " ")
            .trim()
            .let { text ->
                sensitiveAssignmentPattern.replace(text) { match ->
                    val key = match.groups[1]?.value ?: "secret"
                    val separator = match.groups[2]?.value ?: "="
                    if (separator == ":") "$key: present" else "$key=present"
                }
            }
            .let { text -> bearerPattern.replace(text, "Bearer present") }
            .take(limit)

    private fun decode(value: String): String =
        runCatching { java.net.URLDecoder.decode(value, Charsets.UTF_8.name()) }.getOrDefault(value)

    private const val MAX_URL_CHARS = 500
}
