package com.kite.app.browser

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class BrowserAuthSessionKind {
    AppRedirect,
    CliLoopback,
    ExternalOnly
}

enum class BrowserAuthSessionStatus {
    Pending,
    Returned,
    Delivered,
    Failed,
    Expired
}

enum class BrowserAuthCallbackChannelStatus {
    Unprepared,
    Direct,
    RelayReady,
    Forwarded,
    RelayUnavailable
}

data class BrowserAuthSession(
    val sessionId: String,
    val kind: BrowserAuthSessionKind,
    val recipeId: String?,
    val recipeName: String?,
    val instanceId: String?,
    val source: String?,
    val originalUrl: String,
    val requestKey: String,
    val redirectUri: String?,
    val state: String?,
    val stateKey: String?,
    val createdAt: Long,
    val expiresAt: Long,
    val status: BrowserAuthSessionStatus,
    val returnedUrl: String? = null,
    val failureReason: String? = null,
    val runtimeNotifiedAt: Long? = null,
    val callbackChannelStatus: BrowserAuthCallbackChannelStatus = BrowserAuthCallbackChannelStatus.Unprepared,
    val callbackChannelUpdatedAt: Long? = null,
    val callbackChannelFailureReason: String? = null
)

class BrowserAuthSessionStore(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @Synchronized
    fun createPending(
        request: BrowserHandoffRequest,
        decision: BrowserHandoffDecision,
        now: Long = System.currentTimeMillis()
    ): BrowserAuthSession {
        val stateValue = decision.stateOrNull()
            ?: BrowserHandoffPolicy.queryParameters(request.url)["state"]?.firstOrNull()?.takeIf { it.isNotBlank() }
            ?: UUID.randomUUID().toString().replace("-", "")
        val session = BrowserAuthSession(
            sessionId = UUID.randomUUID().toString().replace("-", ""),
            kind = decision.toSessionKind(),
            recipeId = request.recipeId?.takeIf { it.isNotBlank() },
            recipeName = request.recipeName?.takeIf { it.isNotBlank() },
            instanceId = request.instanceId?.takeIf { it.isNotBlank() },
            source = request.source?.takeIf { it.isNotBlank() },
            originalUrl = BrowserHandoffPolicy.redactedUrlForDiagnostics(request.url),
            requestKey = BrowserHandoffPolicy.requestKey(request.url),
            redirectUri = decision.redirectUriOrNull(),
            state = STATE_PRESENT_MARKER,
            stateKey = BrowserHandoffPolicy.stateKey(stateValue),
            createdAt = now,
            expiresAt = now + SESSION_TTL_MS,
            status = BrowserAuthSessionStatus.Pending
        )
        save(session)
        return session
    }

    @Synchronized
    fun findPending(
        instanceId: String?,
        originalUrl: String,
        now: Long = System.currentTimeMillis()
    ): BrowserAuthSession? {
        val requestKey = BrowserHandoffPolicy.requestKey(originalUrl)
        return loadAll()
            .map { it.expireIfNeeded(now) }
            .also { saveAll(it) }
            .firstOrNull { session ->
                session.status == BrowserAuthSessionStatus.Pending &&
                    session.requestKey == requestKey &&
                    session.instanceId.orEmpty() == instanceId.orEmpty()
            }
    }

    @Synchronized
    fun markReturned(
        redirect: BrowserAuthRedirect,
        now: Long = System.currentTimeMillis()
    ): BrowserAuthSession? {
        val sessions = loadAll().map { it.expireIfNeeded(now) }.toMutableList()
        val redirectStateKey = redirect.state
            ?.takeIf { it.isNotBlank() }
            ?.let(BrowserHandoffPolicy::stateKey)
        val index = sessions.indexOfFirst { session ->
            session.status == BrowserAuthSessionStatus.Pending &&
                session.kind == BrowserAuthSessionKind.AppRedirect &&
                session.redirectUri?.let(BrowserHandoffPolicy::isKiteAppRedirectUri) == true &&
                !session.stateKey.isNullOrBlank() &&
                session.stateKey == redirectStateKey &&
                session.expiresAt >= now
        }
        if (index < 0) {
            saveAll(sessions)
            return null
        }
        val next = sessions[index].copy(
            status = if (redirect.error.isNullOrBlank()) {
                BrowserAuthSessionStatus.Returned
            } else {
                BrowserAuthSessionStatus.Failed
            },
            returnedUrl = redirect.redactedUrl,
            failureReason = redirect.error
        )
        sessions[index] = next
        saveAll(sessions)
        return next
    }

    @Synchronized
    fun markDelivered(sessionId: String) {
        update(sessionId) { it.copy(status = BrowserAuthSessionStatus.Delivered) }
    }

    @Synchronized
    fun markLoopbackCallbackChannel(
        sessionId: String,
        status: BrowserAuthCallbackChannelStatus,
        reason: String? = null,
        now: Long = System.currentTimeMillis()
    ) {
        update(sessionId) { session ->
            if (session.kind != BrowserAuthSessionKind.CliLoopback ||
                session.status !in setOf(BrowserAuthSessionStatus.Pending, BrowserAuthSessionStatus.Delivered) ||
                (session.callbackChannelStatus == BrowserAuthCallbackChannelStatus.Forwarded &&
                    status != BrowserAuthCallbackChannelStatus.Forwarded)
            ) {
                session
            } else {
                session.copy(
                    callbackChannelStatus = status,
                    callbackChannelUpdatedAt = now,
                    callbackChannelFailureReason = reason?.takeIf { it.isNotBlank() }
                )
            }
        }
    }

    @Synchronized
    fun markLoopbackCallbackForwarded(sessionId: String, now: Long = System.currentTimeMillis()) {
        update(sessionId) { session ->
            if (session.kind != BrowserAuthSessionKind.CliLoopback ||
                session.status != BrowserAuthSessionStatus.Pending
            ) {
                session
            } else {
                session.copy(
                    status = BrowserAuthSessionStatus.Delivered,
                    callbackChannelStatus = BrowserAuthCallbackChannelStatus.Forwarded,
                    callbackChannelUpdatedAt = now,
                    callbackChannelFailureReason = null
                )
            }
        }
    }

    @Synchronized
    fun markFailed(sessionId: String, reason: String) {
        update(sessionId) { it.copy(status = BrowserAuthSessionStatus.Failed, failureReason = reason) }
    }

    @Synchronized
    fun expirePending(now: Long = System.currentTimeMillis()): List<BrowserAuthSession> {
        val sessions = loadAll().toMutableList()
        val expired = mutableListOf<BrowserAuthSession>()
        sessions.indices.forEach { index ->
            val session = sessions[index]
            if (session.status == BrowserAuthSessionStatus.Pending && session.expiresAt < now) {
                val next = session.copy(
                    status = BrowserAuthSessionStatus.Expired,
                    failureReason = "expired"
                )
                sessions[index] = next
                expired.add(next)
            }
        }
        if (expired.isNotEmpty()) {
            saveAll(sessions)
        }
        return expired
    }

    @Synchronized
    fun expiredNeedingRuntimeSync(): List<BrowserAuthSession> =
        loadAll().filter {
            it.status == BrowserAuthSessionStatus.Expired && it.runtimeNotifiedAt == null
        }

    @Synchronized
    fun forwardedLoopbackNeedingRuntimeSync(): List<BrowserAuthSession> =
        loadAll().filter {
            it.kind == BrowserAuthSessionKind.CliLoopback &&
                it.status == BrowserAuthSessionStatus.Delivered &&
                it.callbackChannelStatus == BrowserAuthCallbackChannelStatus.Forwarded &&
                it.runtimeNotifiedAt == null
        }

    @Synchronized
    fun markRuntimeNotified(sessionId: String, now: Long = System.currentTimeMillis()) {
        update(sessionId) { it.copy(runtimeNotifiedAt = now) }
    }

    private fun update(sessionId: String, transform: (BrowserAuthSession) -> BrowserAuthSession) {
        val sessions = loadAll().toMutableList()
        val index = sessions.indexOfFirst { it.sessionId == sessionId }
        if (index < 0) return
        sessions[index] = transform(sessions[index])
        saveAll(sessions)
    }

    private fun save(session: BrowserAuthSession) {
        val sessions = loadAll()
            .filterNot { it.sessionId == session.sessionId }
            .filterNot { it.status == BrowserAuthSessionStatus.Expired && it.expiresAt < System.currentTimeMillis() - SESSION_TTL_MS }
            .plus(session)
            .sortedByDescending { it.createdAt }
            .take(MAX_STORED_SESSIONS)
        saveAll(sessions)
    }

    private fun loadAll(): List<BrowserAuthSession> {
        val raw = prefs.getString(KEY_SESSIONS, null).orEmpty()
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    array.optJSONObject(index)?.toBrowserAuthSessionOrNull()?.let { add(it) }
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun saveAll(sessions: List<BrowserAuthSession>) {
        val array = JSONArray()
        sessions
            .sortedByDescending { it.createdAt }
            .take(MAX_STORED_SESSIONS)
            .forEach { array.put(it.toJson()) }
        prefs.edit().putString(KEY_SESSIONS, array.toString()).apply()
    }

    private fun BrowserAuthSession.expireIfNeeded(now: Long): BrowserAuthSession =
        if (status == BrowserAuthSessionStatus.Pending && expiresAt < now) {
            copy(status = BrowserAuthSessionStatus.Expired, failureReason = "expired")
        } else {
            this
        }

    private fun BrowserHandoffDecision.toSessionKind(): BrowserAuthSessionKind =
        when (this) {
            is BrowserHandoffDecision.StartCliCallbackHandoff -> BrowserAuthSessionKind.CliLoopback
            is BrowserHandoffDecision.StartAuthHandoff -> BrowserAuthSessionKind.AppRedirect
            else -> BrowserAuthSessionKind.ExternalOnly
        }

    private fun BrowserHandoffDecision.redirectUriOrNull(): String? =
        when (this) {
            is BrowserHandoffDecision.StartAuthHandoff -> redirectUri
            is BrowserHandoffDecision.StartCliCallbackHandoff -> redirectUri
            else -> null
        }

    private fun BrowserHandoffDecision.stateOrNull(): String? =
        when (this) {
            is BrowserHandoffDecision.StartAuthHandoff -> state
            is BrowserHandoffDecision.StartCliCallbackHandoff -> state
            else -> null
        }

    private fun BrowserAuthSession.toJson(): JSONObject =
        JSONObject()
            .put("sessionId", sessionId)
            .put("kind", kind.name)
            .put("recipeId", recipeId.orEmpty())
            .put("recipeName", recipeName.orEmpty())
            .put("instanceId", instanceId.orEmpty())
            .put("source", source.orEmpty())
            .put("originalUrl", originalUrl)
            .put("requestKey", requestKey)
            .put("redirectUri", redirectUri.orEmpty())
            .put("state", state.orEmpty())
            .put("stateKey", stateKey.orEmpty())
            .put("createdAt", createdAt)
            .put("expiresAt", expiresAt)
            .put("status", status.name)
            .put("returnedUrl", returnedUrl.orEmpty())
            .put("failureReason", failureReason.orEmpty())
            .put("runtimeNotifiedAt", runtimeNotifiedAt ?: 0L)
            .put("callbackChannelStatus", callbackChannelStatus.name)
            .put("callbackChannelUpdatedAt", callbackChannelUpdatedAt ?: 0L)
            .put("callbackChannelFailureReason", callbackChannelFailureReason.orEmpty())

    private fun JSONObject.toBrowserAuthSessionOrNull(): BrowserAuthSession? {
        val sessionId = optString("sessionId").takeIf { it.isNotBlank() } ?: return null
        val persistedOriginalUrl = optString("originalUrl").takeIf { it.isNotBlank() } ?: return null
        val requestKey = optString("requestKey").takeIf { it.isNotBlank() }
            ?: BrowserHandoffPolicy.requestKey(persistedOriginalUrl)
        val persistedState = optString("state").takeIf { it.isNotBlank() }
        val stateKey = optString("stateKey").takeIf { it.isNotBlank() }
            ?: persistedState
                ?.takeUnless { it == STATE_PRESENT_MARKER }
                ?.let(BrowserHandoffPolicy::stateKey)
        return BrowserAuthSession(
            sessionId = sessionId,
            kind = enumValueOrDefault(optString("kind"), BrowserAuthSessionKind.ExternalOnly),
            recipeId = optString("recipeId").takeIf { it.isNotBlank() },
            recipeName = optString("recipeName").takeIf { it.isNotBlank() },
            instanceId = optString("instanceId").takeIf { it.isNotBlank() },
            source = optString("source").takeIf { it.isNotBlank() },
            originalUrl = BrowserHandoffPolicy.redactedUrlForDiagnostics(persistedOriginalUrl),
            requestKey = requestKey,
            redirectUri = optString("redirectUri").takeIf { it.isNotBlank() },
            state = persistedState?.let { STATE_PRESENT_MARKER },
            stateKey = stateKey,
            createdAt = optLong("createdAt").takeIf { it > 0L } ?: System.currentTimeMillis(),
            expiresAt = optLong("expiresAt").takeIf { it > 0L } ?: 0L,
            status = enumValueOrDefault(optString("status"), BrowserAuthSessionStatus.Failed),
            returnedUrl = optString("returnedUrl")
                .takeIf { it.isNotBlank() }
                ?.let { BrowserAuthRedirectParser.parse(it)?.redactedUrl ?: it },
            failureReason = optString("failureReason").takeIf { it.isNotBlank() },
            runtimeNotifiedAt = optLong("runtimeNotifiedAt").takeIf { it > 0L },
            callbackChannelStatus = enumValueOrDefault(
                optString("callbackChannelStatus"),
                BrowserAuthCallbackChannelStatus.Unprepared
            ),
            callbackChannelUpdatedAt = optLong("callbackChannelUpdatedAt").takeIf { it > 0L },
            callbackChannelFailureReason = optString("callbackChannelFailureReason").takeIf { it.isNotBlank() }
        )
    }

    private inline fun <reified T : Enum<T>> enumValueOrDefault(name: String, default: T): T =
        runCatching { enumValueOf<T>(name) }.getOrDefault(default)

    companion object {
        private const val PREFS_NAME = "kite_browser_auth_sessions"
        private const val KEY_SESSIONS = "sessions_v1"
        private const val MAX_STORED_SESSIONS = 24
        private const val SESSION_TTL_MS = 10 * 60 * 1000L
        private const val STATE_PRESENT_MARKER = "present"
    }
}
