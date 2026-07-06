package com.kite.app.browser.automation

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class BrowserAutomationSessionStore(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @Synchronized
    fun startSession(
        recipeId: String?,
        recipeName: String?,
        instanceId: String?,
        source: String?,
        url: String,
        now: Long = System.currentTimeMillis()
    ): BrowserAutomationSession {
        val redactedUrl = BrowserAutomationRedactor.redactUrl(url)
        val sessions = loadSessions().toMutableList()
        val reusableIndex = instanceId
            ?.takeIf { it.isNotBlank() }
            ?.let { id ->
                sessions.indexOfFirst { session ->
                    session.instanceId == id &&
                        session.recipeId.orEmpty() == recipeId.orEmpty() &&
                        session.status != BrowserAutomationSessionStatus.Closed
                }
            }
            ?: -1
        val session = if (reusableIndex >= 0) {
            sessions[reusableIndex].copy(
                recipeName = recipeName?.takeIf { it.isNotBlank() } ?: sessions[reusableIndex].recipeName,
                source = source?.takeIf { it.isNotBlank() } ?: sessions[reusableIndex].source,
                url = redactedUrl,
                status = BrowserAutomationSessionStatus.Opening,
                updatedAt = now,
                lastError = null
            )
        } else {
            BrowserAutomationSession(
                sessionId = UUID.randomUUID().toString().replace("-", ""),
                recipeId = recipeId?.takeIf { it.isNotBlank() },
                recipeName = recipeName?.takeIf { it.isNotBlank() },
                instanceId = instanceId?.takeIf { it.isNotBlank() },
                source = source?.takeIf { it.isNotBlank() },
                url = redactedUrl,
                status = BrowserAutomationSessionStatus.Opening,
                createdAt = now,
                updatedAt = now
            )
        }
        if (reusableIndex >= 0) {
            sessions[reusableIndex] = session
        } else {
            sessions.add(session)
        }
        saveSessions(sessions)
        return session
    }

    @Synchronized
    fun markReady(
        sessionId: String,
        snapshot: BrowserAutomationSnapshot,
        now: Long = System.currentTimeMillis()
    ): BrowserAutomationSession? {
        saveSnapshot(snapshot)
        return updateSession(sessionId) {
            it.copy(
                url = snapshot.url,
                status = BrowserAutomationSessionStatus.Ready,
                updatedAt = now,
                lastSnapshotId = snapshot.snapshotId,
                lastError = null
            )
        }
    }

    @Synchronized
    fun markActionRunning(
        sessionId: String,
        action: BrowserAutomationAction,
        now: Long = System.currentTimeMillis()
    ): BrowserAutomationSession? =
        updateSession(sessionId) {
            it.copy(
                status = if (action.type == BrowserAutomationActionType.WaitFor) {
                    BrowserAutomationSessionStatus.Waiting
                } else {
                    BrowserAutomationSessionStatus.RunningAction
                },
                updatedAt = now,
                lastActionId = action.actionId,
                lastError = null
            )
        }

    @Synchronized
    fun markActionResult(
        result: BrowserAutomationActionResult,
        now: Long = System.currentTimeMillis()
    ): BrowserAutomationSession? {
        saveResult(result)
        return updateSession(result.sessionId) { session ->
            session.copy(
                url = result.url.takeIf { it.isNotBlank() } ?: session.url,
                status = if (result.succeeded) {
                    BrowserAutomationSessionStatus.Ready
                } else {
                    BrowserAutomationSessionStatus.Failed
                },
                updatedAt = now,
                lastActionId = result.actionId,
                lastSnapshotId = result.snapshotId ?: session.lastSnapshotId,
                lastError = if (result.succeeded) null else result.errorDetail ?: result.errorCode ?: result.message
            )
        }
    }

    @Synchronized
    fun saveConsoleEntry(entry: BrowserAutomationConsoleEntry) {
        val entries = loadConsoleEntries()
            .filterNot { it.entryId == entry.entryId }
            .plus(entry)
            .sortedByDescending { it.capturedAt }
            .take(MAX_STORED_CONSOLE_ENTRIES)
        val payload = JSONArray()
        entries.forEach { payload.put(it.toJson()) }
        prefs.edit().putString(KEY_CONSOLE_ENTRIES, payload.toString()).apply()
    }

    @Synchronized
    fun saveNetworkEntry(entry: BrowserAutomationNetworkEntry) {
        val entries = loadNetworkEntries()
            .filterNot { it.entryId == entry.entryId }
            .plus(entry)
            .sortedByDescending { it.capturedAt }
            .take(MAX_STORED_NETWORK_ENTRIES)
        val payload = JSONArray()
        entries.forEach { payload.put(it.toJson()) }
        prefs.edit().putString(KEY_NETWORK_ENTRIES, payload.toString()).apply()
    }

    @Synchronized
    fun markFailed(
        sessionId: String,
        reason: String,
        now: Long = System.currentTimeMillis()
    ): BrowserAutomationSession? =
        updateSession(sessionId) {
            it.copy(
                status = BrowserAutomationSessionStatus.Failed,
                updatedAt = now,
                lastError = BrowserAutomationRedactor.safeText(reason, MAX_ERROR_CHARS)
            )
        }

    @Synchronized
    fun close(sessionId: String, now: Long = System.currentTimeMillis()): BrowserAutomationSession? =
        updateSession(sessionId) {
            it.copy(status = BrowserAutomationSessionStatus.Closed, updatedAt = now)
        }

    @Synchronized
    fun get(sessionId: String): BrowserAutomationSession? =
        loadSessions().firstOrNull { it.sessionId == sessionId }

    @Synchronized
    fun latestForInstance(instanceId: String): BrowserAutomationSession? =
        loadSessions()
            .filter { it.instanceId == instanceId }
            .maxByOrNull { it.updatedAt }

    @Synchronized
    fun latestOpenSession(): BrowserAutomationSession? =
        loadSessions()
            .filter { it.status != BrowserAutomationSessionStatus.Closed }
            .maxByOrNull { it.updatedAt }

    @Synchronized
    fun recentSessions(
        limit: Int = 20,
        includeClosed: Boolean = false,
        instanceId: String? = null
    ): List<BrowserAutomationSession> =
        loadSessions()
            .asSequence()
            .filter { includeClosed || it.status != BrowserAutomationSessionStatus.Closed }
            .filter { instanceId.isNullOrBlank() || it.instanceId == instanceId }
            .sortedByDescending { it.updatedAt }
            .take(limit.coerceIn(0, MAX_STORED_SESSIONS))
            .toList()

    @Synchronized
    fun latestSnapshot(sessionId: String): BrowserAutomationSnapshot? =
        loadSnapshots()
            .filter { it.sessionId == sessionId }
            .maxByOrNull { it.capturedAt }

    @Synchronized
    fun latestResult(sessionId: String): BrowserAutomationActionResult? =
        loadResults()
            .filter { it.sessionId == sessionId }
            .maxByOrNull { it.completedAt }

    @Synchronized
    fun recentResults(sessionId: String, limit: Int = 20): List<BrowserAutomationActionResult> =
        loadResults()
            .filter { it.sessionId == sessionId }
            .sortedByDescending { it.completedAt }
            .take(limit.coerceIn(0, 100))

    @Synchronized
    fun latestResultForAction(actionId: String, sessionId: String? = null): BrowserAutomationActionResult? =
        loadResults()
            .filter { it.actionId == actionId }
            .filter { sessionId.isNullOrBlank() || it.sessionId == sessionId }
            .maxByOrNull { it.completedAt }

    @Synchronized
    fun saveRunResult(result: BrowserAutomationRunResult) {
        val runs = loadRuns()
            .filterNot { it.runId == result.runId }
            .plus(result)
            .sortedByDescending { it.completedAt }
            .take(MAX_STORED_RUNS)
        val payload = JSONArray()
        runs.forEach { payload.put(it.toJson()) }
        prefs.edit().putString(KEY_RUNS, payload.toString()).apply()
    }

    @Synchronized
    fun getRun(runId: String): BrowserAutomationRunResult? =
        loadResults().let { results ->
            loadRuns()
                .firstOrNull { it.runId == runId }
                ?.let { reconcileRunResult(it, results) }
        }

    @Synchronized
    fun recentRuns(sessionId: String? = null, limit: Int = 20): List<BrowserAutomationRunResult> =
        loadResults().let { results ->
            loadRuns().map { reconcileRunResult(it, results) }
        }
            .filter { sessionId.isNullOrBlank() || it.sessionId == sessionId }
            .sortedByDescending { it.completedAt }
            .take(limit.coerceIn(0, 100))

    @Synchronized
    fun recentConsoleEntries(sessionId: String, limit: Int = 20): List<BrowserAutomationConsoleEntry> =
        loadConsoleEntries()
            .filter { it.sessionId == sessionId }
            .sortedByDescending { it.capturedAt }
            .take(limit.coerceIn(0, 100))

    @Synchronized
    fun recentNetworkEntries(sessionId: String, limit: Int = 50): List<BrowserAutomationNetworkEntry> =
        loadNetworkEntries()
            .filter { it.sessionId == sessionId }
            .sortedByDescending { it.capturedAt }
            .take(limit.coerceIn(0, 200))

    @Synchronized
    fun allSessions(): List<BrowserAutomationSession> = loadSessions()

    @Synchronized
    fun clearForTest() {
        prefs.edit().clear().commit()
    }

    private fun updateSession(
        sessionId: String,
        transform: (BrowserAutomationSession) -> BrowserAutomationSession
    ): BrowserAutomationSession? {
        val sessions = loadSessions().toMutableList()
        val index = sessions.indexOfFirst { it.sessionId == sessionId }
        if (index < 0) return null
        val next = transform(sessions[index])
        sessions[index] = next
        saveSessions(sessions)
        return next
    }

    private fun saveSessions(sessions: List<BrowserAutomationSession>) {
        val payload = JSONArray()
        sessions
            .sortedByDescending { it.updatedAt }
            .take(MAX_STORED_SESSIONS)
            .forEach { payload.put(it.toJson()) }
        prefs.edit().putString(KEY_SESSIONS, payload.toString()).apply()
    }

    private fun saveSnapshot(snapshot: BrowserAutomationSnapshot) {
        val snapshots = loadSnapshots()
            .filterNot { it.snapshotId == snapshot.snapshotId }
            .plus(snapshot)
            .sortedByDescending { it.capturedAt }
            .take(MAX_STORED_SNAPSHOTS)
        val payload = JSONArray()
        snapshots.forEach { payload.put(it.toJson()) }
        prefs.edit().putString(KEY_SNAPSHOTS, payload.toString()).apply()
    }

    private fun saveResult(result: BrowserAutomationActionResult) {
        val results = loadResults()
            .filterNot { it.actionId == result.actionId }
            .plus(result)
            .sortedByDescending { it.completedAt }
            .take(MAX_STORED_RESULTS)
        val payload = JSONArray()
        results.forEach { payload.put(it.toJson()) }
        prefs.edit().putString(KEY_RESULTS, payload.toString()).apply()
    }

    private fun reconcileRunResult(
        runResult: BrowserAutomationRunResult,
        actionResults: List<BrowserAutomationActionResult>
    ): BrowserAutomationRunResult =
        BrowserAutomationRunReconciler.reconcileRunResult(runResult) { result ->
            actionResults
                .filter { it.actionId == result.actionId }
                .filter { result.sessionId.isBlank() || it.sessionId == result.sessionId }
                .maxByOrNull { it.completedAt }
        }

    private fun loadSessions(): List<BrowserAutomationSession> {
        val raw = prefs.getString(KEY_SESSIONS, null).orEmpty()
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    array.optJSONObject(index)?.toSessionOrNull()?.let { add(it) }
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun loadSnapshots(): List<BrowserAutomationSnapshot> {
        val raw = prefs.getString(KEY_SNAPSHOTS, null).orEmpty()
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    array.optJSONObject(index)?.toSnapshotOrNull()?.let { add(it) }
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun loadResults(): List<BrowserAutomationActionResult> {
        val raw = prefs.getString(KEY_RESULTS, null).orEmpty()
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    array.optJSONObject(index)?.toActionResultOrNull()?.let { add(it) }
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun loadRuns(): List<BrowserAutomationRunResult> {
        val raw = prefs.getString(KEY_RUNS, null).orEmpty()
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    array.optJSONObject(index)?.toRunResultOrNull()?.let { add(it) }
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun loadConsoleEntries(): List<BrowserAutomationConsoleEntry> {
        val raw = prefs.getString(KEY_CONSOLE_ENTRIES, null).orEmpty()
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    array.optJSONObject(index)?.toConsoleEntryOrNull()?.let { add(it) }
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun loadNetworkEntries(): List<BrowserAutomationNetworkEntry> {
        val raw = prefs.getString(KEY_NETWORK_ENTRIES, null).orEmpty()
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    array.optJSONObject(index)?.toNetworkEntryOrNull()?.let { add(it) }
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun BrowserAutomationSession.toJson(): JSONObject =
        JSONObject()
            .put("sessionId", sessionId)
            .put("recipeId", recipeId.orEmpty())
            .put("recipeName", recipeName.orEmpty())
            .put("instanceId", instanceId.orEmpty())
            .put("source", source.orEmpty())
            .put("url", BrowserAutomationRedactor.redactUrl(url))
            .put("mode", mode)
            .put("status", status.name)
            .put("createdAt", createdAt)
            .put("updatedAt", updatedAt)
            .put("lastActionId", lastActionId.orEmpty())
            .put("lastSnapshotId", lastSnapshotId.orEmpty())
            .put("lastError", lastError.orEmpty())

    private fun BrowserAutomationSnapshot.toJson(): JSONObject =
        JSONObject()
            .put("snapshotId", snapshotId)
            .put("sessionId", sessionId)
            .put("url", BrowserAutomationRedactor.redactUrl(url))
            .put("title", title.orEmpty().take(MAX_TITLE_CHARS))
            .put("readyState", readyState.orEmpty().take(MAX_READY_STATE_CHARS))
            .put("text", BrowserAutomationRedactor.safeText(text, MAX_TEXT_CHARS))
            .put("elementCount", elementCount)
            .put("elements", JSONArray().apply {
                elements.take(MAX_STORED_ELEMENTS).forEach { put(it.toJson()) }
            })
            .put("accessibility", JSONArray().apply {
                accessibility.take(MAX_STORED_ACCESSIBILITY_NODES).forEach { put(it.toJson()) }
            })
            .put("capturedAt", capturedAt)

    private fun BrowserAutomationElementSummary.toJson(): JSONObject =
        JSONObject()
            .put("index", index)
            .put("tag", tag.take(MAX_TAG_CHARS))
            .put("type", type.orEmpty().take(MAX_ATTR_CHARS))
            .put("role", role.orEmpty().take(MAX_ATTR_CHARS))
            .put("text", BrowserAutomationRedactor.safeText(text, MAX_ELEMENT_TEXT_CHARS))
            .put("placeholder", BrowserAutomationRedactor.safeText(placeholder, MAX_ELEMENT_TEXT_CHARS))
            .put("ariaLabel", BrowserAutomationRedactor.safeText(ariaLabel, MAX_ELEMENT_TEXT_CHARS))
            .put("visible", visible)
            .put("enabled", enabled)
            .put("x", x)
            .put("y", y)
            .put("width", width)
            .put("height", height)
            .put("framePath", framePath.orEmpty().take(MAX_FRAME_CHARS))
            .put("frameUrl", BrowserAutomationRedactor.redactUrl(frameUrl.orEmpty()))
            .put("frameName", BrowserAutomationRedactor.safeText(frameName, MAX_FRAME_CHARS))
            .put("shadowPath", shadowPath.orEmpty().take(MAX_SHADOW_CHARS))
            .put("shadowHost", BrowserAutomationRedactor.safeText(shadowHost, MAX_SHADOW_CHARS))

    private fun BrowserAutomationAccessibilityNode.toJson(): JSONObject =
        JSONObject()
            .put("index", index)
            .put("role", role.take(MAX_ATTR_CHARS))
            .put("name", BrowserAutomationRedactor.safeText(name, MAX_ACCESSIBILITY_NAME_CHARS))
            .put("tag", tag.take(MAX_TAG_CHARS))
            .put("type", type.orEmpty().take(MAX_ATTR_CHARS))
            .put("level", level ?: JSONObject.NULL)
            .put("visible", visible)
            .put("enabled", enabled)
            .put("checked", checked.orEmpty().take(MAX_STATE_CHARS))
            .put("selected", selected ?: JSONObject.NULL)
            .put("expanded", expanded ?: JSONObject.NULL)
            .put("x", x)
            .put("y", y)
            .put("width", width)
            .put("height", height)
            .put("framePath", framePath.orEmpty().take(MAX_FRAME_CHARS))
            .put("frameUrl", BrowserAutomationRedactor.redactUrl(frameUrl.orEmpty()))
            .put("frameName", BrowserAutomationRedactor.safeText(frameName, MAX_FRAME_CHARS))
            .put("frameAccessible", frameAccessible ?: JSONObject.NULL)
            .put("shadowPath", shadowPath.orEmpty().take(MAX_SHADOW_CHARS))
            .put("shadowHost", BrowserAutomationRedactor.safeText(shadowHost, MAX_SHADOW_CHARS))

    private fun BrowserAutomationConsoleEntry.toJson(): JSONObject =
        JSONObject()
            .put("entryId", entryId)
            .put("sessionId", sessionId)
            .put("level", level.take(MAX_CONSOLE_LEVEL_CHARS))
            .put("message", BrowserAutomationRedactor.safeText(message, MAX_CONSOLE_MESSAGE_CHARS))
            .put("sourceId", BrowserAutomationRedactor.redactUrl(sourceId.orEmpty()))
            .put("lineNumber", lineNumber.coerceAtLeast(0))
            .put("capturedAt", capturedAt)

    private fun BrowserAutomationNetworkEntry.toJson(): JSONObject =
        JSONObject()
            .put("entryId", entryId)
            .put("sessionId", sessionId)
            .put("kind", kind.take(MAX_NETWORK_KIND_CHARS))
            .put("method", method.take(MAX_NETWORK_METHOD_CHARS))
            .put("url", BrowserAutomationRedactor.redactUrl(url))
            .put("isForMainFrame", isForMainFrame)
            .put("statusCode", statusCode ?: JSONObject.NULL)
            .put("reasonPhrase", BrowserAutomationRedactor.safeText(reasonPhrase, MAX_NETWORK_REASON_CHARS))
            .put("capturedAt", capturedAt)

    private fun JSONObject.toSessionOrNull(): BrowserAutomationSession? {
        val sessionId = optString("sessionId").takeIf { it.isNotBlank() } ?: return null
        val updatedAt = optLong("updatedAt").takeIf { it > 0L } ?: System.currentTimeMillis()
        return BrowserAutomationSession(
            sessionId = sessionId,
            recipeId = optString("recipeId").takeIf { it.isNotBlank() },
            recipeName = optString("recipeName").takeIf { it.isNotBlank() },
            instanceId = optString("instanceId").takeIf { it.isNotBlank() },
            source = optString("source").takeIf { it.isNotBlank() },
            url = BrowserAutomationRedactor.redactUrl(optString("url")),
            mode = optString("mode").ifBlank { BrowserAutomationSession.MODE_WEBVIEW },
            status = enumValueOrDefault(optString("status"), BrowserAutomationSessionStatus.Failed),
            createdAt = optLong("createdAt").takeIf { it > 0L } ?: updatedAt,
            updatedAt = updatedAt,
            lastActionId = optString("lastActionId").takeIf { it.isNotBlank() },
            lastSnapshotId = optString("lastSnapshotId").takeIf { it.isNotBlank() },
            lastError = optString("lastError").takeIf { it.isNotBlank() }
        )
    }

    private fun JSONObject.toSnapshotOrNull(): BrowserAutomationSnapshot? {
        val snapshotId = optString("snapshotId").takeIf { it.isNotBlank() } ?: return null
        val sessionId = optString("sessionId").takeIf { it.isNotBlank() } ?: return null
        val elementsJson = optJSONArray("elements") ?: JSONArray()
        val elements = buildList {
            for (index in 0 until elementsJson.length()) {
                elementsJson.optJSONObject(index)?.toElementOrNull()?.let { add(it) }
            }
        }
        val accessibilityJson = optJSONArray("accessibility") ?: JSONArray()
        val accessibility = buildList {
            for (index in 0 until accessibilityJson.length()) {
                accessibilityJson.optJSONObject(index)?.toAccessibilityNodeOrNull()?.let { add(it) }
            }
        }
        return BrowserAutomationSnapshot(
            snapshotId = snapshotId,
            sessionId = sessionId,
            url = BrowserAutomationRedactor.redactUrl(optString("url")),
            title = optString("title").takeIf { it.isNotBlank() },
            readyState = optString("readyState").takeIf { it.isNotBlank() },
            text = BrowserAutomationRedactor.safeText(optString("text"), MAX_TEXT_CHARS),
            elementCount = optInt("elementCount", elements.size),
            elements = elements,
            accessibility = accessibility,
            capturedAt = optLong("capturedAt").takeIf { it > 0L } ?: System.currentTimeMillis()
        )
    }

    private fun JSONObject.toActionResultOrNull(): BrowserAutomationActionResult? {
        val actionId = optString("actionId").takeIf { it.isNotBlank() } ?: return null
        val sessionId = optString("sessionId").takeIf { it.isNotBlank() } ?: return null
        val type = BrowserAutomationActionType.fromWireName(optString("type")) ?: return null
        return BrowserAutomationActionResult(
            actionId = actionId,
            sessionId = sessionId,
            type = type,
            status = enumValueOrDefault(optString("status"), BrowserAutomationResultStatus.Failed),
            durationMs = optLong("durationMs", 0L).coerceAtLeast(0L),
            url = BrowserAutomationRedactor.redactUrl(optString("url")),
            title = optString("title").takeIf { it.isNotBlank() },
            message = BrowserAutomationRedactor.safeText(optString("message"), MAX_ERROR_CHARS),
            matchedCount = optInt("matchedCount", 0).coerceAtLeast(0),
            snapshotId = optString("snapshotId").takeIf { it.isNotBlank() },
            artifactPath = optString("artifactPath").takeIf { it.isNotBlank() },
            errorCode = optString("errorCode").takeIf { it.isNotBlank() },
            errorDetail = optString("errorDetail").takeIf { it.isNotBlank() },
            completedAt = optLong("completedAt").takeIf { it > 0L } ?: System.currentTimeMillis()
        )
    }

    private fun JSONObject.toRunResultOrNull(): BrowserAutomationRunResult? {
        val runId = optString("runId").takeIf { it.isNotBlank() } ?: return null
        val resultsJson = optJSONArray("results") ?: JSONArray()
        val results = buildList {
            for (index in 0 until resultsJson.length()) {
                resultsJson.optJSONObject(index)?.toActionResultOrNull()?.let { add(it) }
            }
        }
        return BrowserAutomationRunResult(
            runId = runId,
            sessionId = optString("sessionId").takeIf { it.isNotBlank() }
                ?: results.firstOrNull()?.sessionId,
            status = enumValueOrDefault(optString("status"), BrowserAutomationResultStatus.Failed),
            durationMs = optLong("durationMs", 0L).coerceAtLeast(0L),
            requestedCount = optInt("requestedCount", results.size).coerceAtLeast(0),
            completedCount = optInt("completedCount", results.size).coerceAtLeast(0),
            stoppedOnFailure = optBoolean("stoppedOnFailure", false),
            results = results,
            completedAt = optLong("completedAt").takeIf { it > 0L }
                ?: results.maxOfOrNull { it.completedAt }
                ?: System.currentTimeMillis()
        )
    }

    private fun JSONObject.toConsoleEntryOrNull(): BrowserAutomationConsoleEntry? {
        val entryId = optString("entryId").takeIf { it.isNotBlank() } ?: return null
        val sessionId = optString("sessionId").takeIf { it.isNotBlank() } ?: return null
        return BrowserAutomationConsoleEntry(
            entryId = entryId,
            sessionId = sessionId,
            level = optString("level").ifBlank { "LOG" }.take(MAX_CONSOLE_LEVEL_CHARS),
            message = BrowserAutomationRedactor.safeText(optString("message"), MAX_CONSOLE_MESSAGE_CHARS),
            sourceId = optString("sourceId").takeIf { it.isNotBlank() },
            lineNumber = optInt("lineNumber", 0).coerceAtLeast(0),
            capturedAt = optLong("capturedAt").takeIf { it > 0L } ?: System.currentTimeMillis()
        )
    }

    private fun JSONObject.toNetworkEntryOrNull(): BrowserAutomationNetworkEntry? {
        val entryId = optString("entryId").takeIf { it.isNotBlank() } ?: return null
        val sessionId = optString("sessionId").takeIf { it.isNotBlank() } ?: return null
        return BrowserAutomationNetworkEntry(
            entryId = entryId,
            sessionId = sessionId,
            kind = optString("kind").ifBlank { "request" }.take(MAX_NETWORK_KIND_CHARS),
            method = optString("method").ifBlank { "GET" }.take(MAX_NETWORK_METHOD_CHARS),
            url = BrowserAutomationRedactor.redactUrl(optString("url")),
            isForMainFrame = optBoolean("isForMainFrame", false),
            statusCode = optInt("statusCode").takeIf { has("statusCode") && it > 0 },
            reasonPhrase = optString("reasonPhrase").takeIf { it.isNotBlank() },
            capturedAt = optLong("capturedAt").takeIf { it > 0L } ?: System.currentTimeMillis()
        )
    }

    private fun JSONObject.toElementOrNull(): BrowserAutomationElementSummary? {
        val tag = optString("tag").takeIf { it.isNotBlank() } ?: return null
        return BrowserAutomationElementSummary(
            index = optInt("index", 0),
            tag = tag,
            type = optString("type").takeIf { it.isNotBlank() },
            role = optString("role").takeIf { it.isNotBlank() },
            text = optString("text").takeIf { it.isNotBlank() },
            placeholder = optString("placeholder").takeIf { it.isNotBlank() },
            ariaLabel = optString("ariaLabel").takeIf { it.isNotBlank() },
            visible = optBoolean("visible", false),
            enabled = optBoolean("enabled", true),
            x = optDouble("x", 0.0),
            y = optDouble("y", 0.0),
            width = optDouble("width", 0.0),
            height = optDouble("height", 0.0),
            framePath = optString("framePath").takeIf { it.isNotBlank() },
            frameUrl = BrowserAutomationRedactor.redactUrl(optString("frameUrl")).takeIf { it.isNotBlank() },
            frameName = BrowserAutomationRedactor.safeText(optString("frameName"), MAX_FRAME_CHARS)
                .takeIf { it.isNotBlank() },
            shadowPath = optString("shadowPath").takeIf { it.isNotBlank() },
            shadowHost = BrowserAutomationRedactor.safeText(optString("shadowHost"), MAX_SHADOW_CHARS)
                .takeIf { it.isNotBlank() }
        )
    }

    private fun JSONObject.toAccessibilityNodeOrNull(): BrowserAutomationAccessibilityNode? {
        val role = optString("role").takeIf { it.isNotBlank() } ?: return null
        return BrowserAutomationAccessibilityNode(
            index = optInt("index", 0),
            role = role.take(MAX_ATTR_CHARS),
            name = BrowserAutomationRedactor.safeText(optString("name"), MAX_ACCESSIBILITY_NAME_CHARS),
            tag = optString("tag").takeIf { it.isNotBlank() }?.take(MAX_TAG_CHARS) ?: "node",
            type = optString("type").takeIf { it.isNotBlank() },
            level = optInt("level").takeIf { has("level") && it > 0 },
            visible = optBoolean("visible", false),
            enabled = optBoolean("enabled", true),
            checked = optString("checked").takeIf { it.isNotBlank() },
            selected = optBoolean("selected").takeIf { has("selected") && !isNull("selected") },
            expanded = optBoolean("expanded").takeIf { has("expanded") && !isNull("expanded") },
            x = optDouble("x", 0.0),
            y = optDouble("y", 0.0),
            width = optDouble("width", 0.0),
            height = optDouble("height", 0.0),
            framePath = optString("framePath").takeIf { it.isNotBlank() },
            frameUrl = BrowserAutomationRedactor.redactUrl(optString("frameUrl")).takeIf { it.isNotBlank() },
            frameName = BrowserAutomationRedactor.safeText(optString("frameName"), MAX_FRAME_CHARS)
                .takeIf { it.isNotBlank() },
            frameAccessible = optBoolean("frameAccessible").takeIf { has("frameAccessible") && !isNull("frameAccessible") },
            shadowPath = optString("shadowPath").takeIf { it.isNotBlank() },
            shadowHost = BrowserAutomationRedactor.safeText(optString("shadowHost"), MAX_SHADOW_CHARS)
                .takeIf { it.isNotBlank() }
        )
    }

    private inline fun <reified T : Enum<T>> enumValueOrDefault(name: String, default: T): T =
        runCatching { enumValueOf<T>(name) }.getOrDefault(default)

    companion object {
        private const val PREFS_NAME = "kite_browser_automation"
        private const val KEY_SESSIONS = "sessions_v1"
        private const val KEY_SNAPSHOTS = "snapshots_v1"
        private const val KEY_RESULTS = "results_v1"
        private const val KEY_RUNS = "runs_v1"
        private const val KEY_CONSOLE_ENTRIES = "console_v1"
        private const val KEY_NETWORK_ENTRIES = "network_v1"
        private const val MAX_STORED_SESSIONS = 24
        private const val MAX_STORED_SNAPSHOTS = 48
        private const val MAX_STORED_RESULTS = 80
        private const val MAX_STORED_RUNS = 60
        private const val MAX_STORED_CONSOLE_ENTRIES = 120
        private const val MAX_STORED_NETWORK_ENTRIES = 240
        private const val MAX_STORED_ELEMENTS = 80
        private const val MAX_STORED_ACCESSIBILITY_NODES = 120
        private const val MAX_TEXT_CHARS = 4000
        private const val MAX_ERROR_CHARS = 500
        private const val MAX_TITLE_CHARS = 160
        private const val MAX_READY_STATE_CHARS = 32
        private const val MAX_TAG_CHARS = 32
        private const val MAX_ATTR_CHARS = 80
        private const val MAX_ELEMENT_TEXT_CHARS = 160
        private const val MAX_ACCESSIBILITY_NAME_CHARS = 200
        private const val MAX_STATE_CHARS = 32
        private const val MAX_CONSOLE_LEVEL_CHARS = 32
        private const val MAX_CONSOLE_MESSAGE_CHARS = 1000
        private const val MAX_NETWORK_KIND_CHARS = 32
        private const val MAX_NETWORK_METHOD_CHARS = 16
        private const val MAX_NETWORK_REASON_CHARS = 120
        private const val MAX_FRAME_CHARS = 160
        private const val MAX_SHADOW_CHARS = 160
    }
}
