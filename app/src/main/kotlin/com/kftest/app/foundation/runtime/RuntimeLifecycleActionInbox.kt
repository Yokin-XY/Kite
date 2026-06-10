package com.kftest.app.foundation.runtime

import java.io.File
import org.json.JSONArray
import org.json.JSONObject

enum class RuntimeLifecycleActionSeverity {
    INFO,
    WARNING,
    ACTION_REQUIRED,
    CRITICAL_DRY_RUN
}

enum class RuntimeLifecycleActionInboxStatus {
    OPEN,
    ACKNOWLEDGED,
    DISMISSED,
    EXPIRED,
    RESOLVED_BY_STATE_CHANGE,
    BLOCKED
}

data class RuntimeLifecycleActionInboxItem(
    val actionId: String,
    val unitId: String,
    val effectiveTier: RuntimeLifecycleAuthorityTier,
    val finalAction: RuntimeLifecycleFinalAction,
    val finalActionMode: RuntimeLifecycleFinalActionMode,
    val executorBoundary: RuntimeLifecycleExecutorBoundary,
    val primaryReason: String,
    val suppressionReasons: List<String> = emptyList(),
    val blockedActions: List<String> = emptyList(),
    val allowedFutureActions: List<String> = emptyList(),
    val requiresUserConfirmation: Boolean = false,
    val isExecutableNow: Boolean = false,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val expiresAt: Long? = null,
    val dedupeKey: String,
    val severity: RuntimeLifecycleActionSeverity,
    val status: RuntimeLifecycleActionInboxStatus = RuntimeLifecycleActionInboxStatus.OPEN,
    val seenCount: Int = 1
)

data class RuntimeLifecycleActionInboxSnapshot(
    val mode: String = "runtime_lifecycle_action_inbox_v0",
    val enabled: Boolean = true,
    val enforcementEnabled: Boolean = false,
    val persistenceMode: String = "in_memory_volatile",
    val path: String = "none",
    val loadStatus: String = "volatile_default",
    val loadError: String? = null,
    val generatedAt: Long = 0L,
    val maxItems: Int = RuntimeLifecycleActionInbox.DEFAULT_MAX_ITEMS,
    val openActionCount: Int = 0,
    val warningActionCount: Int = 0,
    val actionRequiredCount: Int = 0,
    val criticalDryRunCount: Int = 0,
    val requiresUserConfirmationCount: Int = 0,
    val resolvedActionCount: Int = 0,
    val blockedActionCount: Int = 0,
    val items: List<RuntimeLifecycleActionInboxItem> = emptyList(),
    val boundary: String =
        "diagnostic_pending_state_only_no_confirmation_execution_or_runtime_actions"
) {
    fun toEnvText(maxItemsToPrint: Int = 8): String {
        return buildString {
            appendLine("runtime_lifecycle_action_inbox_mode=${mode.toActionInboxEnvValue()}")
            appendLine("runtime_lifecycle_action_inbox_enabled=$enabled")
            appendLine("runtime_lifecycle_action_inbox_enforcement_enabled=$enforcementEnabled")
            appendLine("runtime_lifecycle_action_inbox_persistence=${persistenceMode.toActionInboxEnvValue()}")
            appendLine("runtime_lifecycle_action_inbox_path=${path.toActionInboxEnvValue()}")
            appendLine("runtime_lifecycle_action_inbox_load_status=${loadStatus.toActionInboxEnvValue()}")
            appendLine("runtime_lifecycle_action_inbox_load_error=${loadError.toActionInboxEnvValue()}")
            appendLine("runtime_lifecycle_action_inbox_generated_at=$generatedAt")
            appendLine("runtime_lifecycle_action_inbox_max_items=$maxItems")
            appendLine("runtime_lifecycle_action_inbox_open_action_count=$openActionCount")
            appendLine("runtime_lifecycle_action_inbox_warning_action_count=$warningActionCount")
            appendLine("runtime_lifecycle_action_inbox_action_required_count=$actionRequiredCount")
            appendLine("runtime_lifecycle_action_inbox_critical_dry_run_count=$criticalDryRunCount")
            appendLine("runtime_lifecycle_action_inbox_requires_user_confirmation_count=$requiresUserConfirmationCount")
            appendLine("runtime_lifecycle_action_inbox_resolved_action_count=$resolvedActionCount")
            appendLine("runtime_lifecycle_action_inbox_blocked_action_count=$blockedActionCount")
            items.take(maxItemsToPrint).forEachIndexed { index, item ->
                val prefix = "runtime_lifecycle_action_inbox_item_${index + 1}"
                appendLine("${prefix}_action_id=${item.actionId.toActionInboxEnvValue()}")
                appendLine("${prefix}_unit_id=${item.unitId.toActionInboxEnvValue()}")
                appendLine("${prefix}_effective_tier=${item.effectiveTier.name}")
                appendLine("${prefix}_final_action=${item.finalAction.name}")
                appendLine("${prefix}_final_action_mode=${item.finalActionMode.name}")
                appendLine("${prefix}_executor_boundary=${item.executorBoundary.name}")
                appendLine("${prefix}_severity=${item.severity.name}")
                appendLine("${prefix}_status=${item.status.name}")
                appendLine("${prefix}_primary_reason=${item.primaryReason.toActionInboxEnvValue()}")
                appendLine("${prefix}_requires_user_confirmation=${item.requiresUserConfirmation}")
                appendLine("${prefix}_is_executable_now=${item.isExecutableNow}")
                appendLine("${prefix}_created_at=${item.createdAt}")
                appendLine("${prefix}_updated_at=${item.updatedAt}")
                appendLine("${prefix}_expires_at=${item.expiresAt ?: 0L}")
                appendLine("${prefix}_seen_count=${item.seenCount}")
            }
            appendLine("runtime_lifecycle_action_inbox_boundary=${boundary.toActionInboxEnvValue()}")
        }
    }
}

object RuntimeLifecycleActionInbox {
    const val DEFAULT_MAX_ITEMS = 64
    const val WARNING_TTL_MS = 10 * 60 * 1000L

    fun record(
        previous: RuntimeLifecycleActionInboxSnapshot = RuntimeLifecycleActionInboxSnapshot(),
        planner: RuntimeLifecycleActionPlannerSnapshot,
        now: Long = System.currentTimeMillis(),
        maxItems: Int = DEFAULT_MAX_ITEMS
    ): RuntimeLifecycleActionInboxSnapshot {
        val previousByDedupe = previous.items.associateBy { it.dedupeKey }
        val activeItems = planner.entries
            .mapNotNull { entry -> pendingItem(entry, previousByDedupe[dedupeKey(entry)], now) }
        val activeKeys = activeItems.map { it.dedupeKey }.toSet()
        val resolvedItems = previous.items
            .filter { it.dedupeKey !in activeKeys }
            .map { item -> resolveInactiveItem(item, now) }
        return snapshot(
            items = (activeItems + resolvedItems)
                .sortedWith(
                    compareByDescending<RuntimeLifecycleActionInboxItem> {
                        it.status == RuntimeLifecycleActionInboxStatus.OPEN ||
                            it.status == RuntimeLifecycleActionInboxStatus.BLOCKED
                    }.thenByDescending { it.updatedAt }
                        .thenBy { it.actionId }
                )
                .take(maxItems.coerceAtLeast(1)),
            generatedAt = now,
            maxItems = maxItems,
            persistenceMode = previous.persistenceMode,
            path = previous.path,
            loadStatus = previous.loadStatus,
            loadError = previous.loadError
        )
    }

    internal fun snapshot(
        items: List<RuntimeLifecycleActionInboxItem>,
        generatedAt: Long,
        maxItems: Int,
        persistenceMode: String,
        path: String,
        loadStatus: String,
        loadError: String? = null
    ): RuntimeLifecycleActionInboxSnapshot {
        val boundedItems = items.take(maxItems.coerceAtLeast(1))
        return RuntimeLifecycleActionInboxSnapshot(
            persistenceMode = persistenceMode,
            path = path,
            loadStatus = loadStatus,
            loadError = loadError,
            generatedAt = generatedAt,
            maxItems = maxItems,
            openActionCount = boundedItems.count {
                it.status == RuntimeLifecycleActionInboxStatus.OPEN ||
                    it.status == RuntimeLifecycleActionInboxStatus.BLOCKED
            },
            warningActionCount = boundedItems.count {
                it.status.isOpenLike() &&
                    it.severity == RuntimeLifecycleActionSeverity.WARNING
            },
            actionRequiredCount = boundedItems.count {
                it.status.isOpenLike() &&
                    it.severity == RuntimeLifecycleActionSeverity.ACTION_REQUIRED
            },
            criticalDryRunCount = boundedItems.count {
                it.status.isOpenLike() &&
                    it.severity == RuntimeLifecycleActionSeverity.CRITICAL_DRY_RUN
            },
            requiresUserConfirmationCount = boundedItems.count {
                it.status.isOpenLike() && it.requiresUserConfirmation
            },
            resolvedActionCount = boundedItems.count {
                it.status == RuntimeLifecycleActionInboxStatus.RESOLVED_BY_STATE_CHANGE ||
                    it.status == RuntimeLifecycleActionInboxStatus.EXPIRED
            },
            blockedActionCount = boundedItems.count {
                it.status == RuntimeLifecycleActionInboxStatus.BLOCKED ||
                    it.blockedActions.isNotEmpty()
            },
            items = boundedItems
        )
    }

    private fun pendingItem(
        entry: RuntimeLifecycleActionPlanEntry,
        previous: RuntimeLifecycleActionInboxItem?,
        now: Long
    ): RuntimeLifecycleActionInboxItem? {
        val severity = severityFor(entry.finalAction) ?: return null
        val dedupeKey = dedupeKey(entry)
        val status = when {
            previous?.status == RuntimeLifecycleActionInboxStatus.ACKNOWLEDGED -> previous.status
            previous?.status == RuntimeLifecycleActionInboxStatus.DISMISSED -> previous.status
            entry.finalActionMode == RuntimeLifecycleFinalActionMode.BLOCKED &&
                entry.finalAction != RuntimeLifecycleFinalAction.WAIT_FOR_USER_CONFIRMATION ->
                RuntimeLifecycleActionInboxStatus.BLOCKED
            else -> RuntimeLifecycleActionInboxStatus.OPEN
        }
        return RuntimeLifecycleActionInboxItem(
            actionId = previous?.actionId ?: actionIdFor(dedupeKey),
            unitId = entry.unitId,
            effectiveTier = entry.effectiveTier,
            finalAction = entry.finalAction,
            finalActionMode = entry.finalActionMode,
            executorBoundary = entry.executorBoundary,
            primaryReason = entry.primaryReason,
            suppressionReasons = entry.suppressionReasons,
            blockedActions = entry.blockedActions,
            allowedFutureActions = entry.allowedFutureActions,
            requiresUserConfirmation = entry.requiresUserConfirmation,
            isExecutableNow = false,
            createdAt = previous?.createdAt ?: now,
            updatedAt = now,
            expiresAt = expiresAtFor(entry.finalAction, now),
            dedupeKey = dedupeKey,
            severity = severity,
            status = status,
            seenCount = (previous?.seenCount ?: 0).coerceAtLeast(0) + 1
        )
    }

    private fun resolveInactiveItem(
        item: RuntimeLifecycleActionInboxItem,
        now: Long
    ): RuntimeLifecycleActionInboxItem {
        if (item.status == RuntimeLifecycleActionInboxStatus.ACKNOWLEDGED ||
            item.status == RuntimeLifecycleActionInboxStatus.DISMISSED ||
            item.status == RuntimeLifecycleActionInboxStatus.RESOLVED_BY_STATE_CHANGE ||
            item.status == RuntimeLifecycleActionInboxStatus.EXPIRED
        ) {
            return item
        }
        val expired = item.expiresAt?.let { it <= now } == true &&
            item.severity == RuntimeLifecycleActionSeverity.WARNING
        return item.copy(
            status = if (expired) {
                RuntimeLifecycleActionInboxStatus.EXPIRED
            } else {
                RuntimeLifecycleActionInboxStatus.RESOLVED_BY_STATE_CHANGE
            },
            updatedAt = now,
            isExecutableNow = false
        )
    }

    private fun severityFor(
        action: RuntimeLifecycleFinalAction
    ): RuntimeLifecycleActionSeverity? {
        return when (action) {
            RuntimeLifecycleFinalAction.WAIT_FOR_USER_CONFIRMATION ->
                RuntimeLifecycleActionSeverity.ACTION_REQUIRED
            RuntimeLifecycleFinalAction.WARN_RESOURCE_NEAR_LIMIT ->
                RuntimeLifecycleActionSeverity.WARNING
            RuntimeLifecycleFinalAction.RESTART_CANDIDATE_DRY_RUN ->
                RuntimeLifecycleActionSeverity.ACTION_REQUIRED
            RuntimeLifecycleFinalAction.QUARANTINE_CANDIDATE_DRY_RUN,
            RuntimeLifecycleFinalAction.CORE_RECOVERY_DRY_RUN ->
                RuntimeLifecycleActionSeverity.CRITICAL_DRY_RUN
            RuntimeLifecycleFinalAction.RECLAIM_CANDIDATE_DRY_RUN ->
                RuntimeLifecycleActionSeverity.WARNING
            RuntimeLifecycleFinalAction.OBSERVE,
            RuntimeLifecycleFinalAction.KEEP_RUNNING,
            RuntimeLifecycleFinalAction.EXPECTED_STOP_CONFIRMED,
            RuntimeLifecycleFinalAction.NO_ACTION_UNMANAGED,
            RuntimeLifecycleFinalAction.NO_ACTION_UNLIMITED,
            RuntimeLifecycleFinalAction.NO_ACTION_AMBIGUOUS,
            RuntimeLifecycleFinalAction.NO_ACTION_CORE_PROTECTED -> null
        }
    }

    private fun expiresAtFor(
        action: RuntimeLifecycleFinalAction,
        now: Long
    ): Long? {
        return when (action) {
            RuntimeLifecycleFinalAction.WARN_RESOURCE_NEAR_LIMIT,
            RuntimeLifecycleFinalAction.RECLAIM_CANDIDATE_DRY_RUN -> now + WARNING_TTL_MS
            else -> null
        }
    }

    private fun dedupeKey(entry: RuntimeLifecycleActionPlanEntry): String {
        return listOf(
            entry.unitId,
            entry.finalAction.name,
            entry.primaryReason
        ).joinToString("|")
    }

    private fun actionIdFor(dedupeKey: String): String {
        return "lifecycle-action-${Integer.toHexString(dedupeKey.hashCode())}"
    }

    private fun RuntimeLifecycleActionInboxStatus.isOpenLike(): Boolean {
        return this == RuntimeLifecycleActionInboxStatus.OPEN ||
            this == RuntimeLifecycleActionInboxStatus.BLOCKED
    }
}

object RuntimeLifecycleActionInboxStore {
    private const val ACTION_INBOX_PERSIST_MIN_INTERVAL_MS = 60_000L

    private var volatileSnapshot = RuntimeLifecycleActionInboxSnapshot()
    private val lastPersistedSignatureByPath = mutableMapOf<String, String>()
    private val lastPersistedAtByPath = mutableMapOf<String, Long>()

    @Synchronized
    fun resetVolatileForTests() {
        volatileSnapshot = RuntimeLifecycleActionInboxSnapshot()
        lastPersistedSignatureByPath.clear()
        lastPersistedAtByPath.clear()
    }

    @Synchronized
    fun load(file: File?): RuntimeLifecycleActionInboxSnapshot {
        if (file == null) {
            return volatileSnapshot.copy(
                persistenceMode = "in_memory_volatile",
                path = "none",
                loadStatus = "volatile_default"
            )
        }
        if (!file.exists()) {
            return RuntimeLifecycleActionInboxSnapshot(
                persistenceMode = "persistent_file",
                path = file.absolutePath,
                loadStatus = "missing_default"
            )
        }
        return runCatching {
            val text = file.readText()
            if (text.isBlank()) {
                RuntimeLifecycleActionInboxSnapshot(
                    persistenceMode = "persistent_file",
                    path = file.absolutePath,
                    loadStatus = "empty_default"
                )
            } else {
                fromJson(JSONObject(text), file)
            }
        }.getOrElse { error ->
            RuntimeLifecycleActionInboxSnapshot(
                persistenceMode = "persistent_file",
                path = file.absolutePath,
                loadStatus = "error_default",
                loadError = error.message ?: error::class.java.simpleName
            )
        }
    }

    @Synchronized
    fun record(
        file: File?,
        planner: RuntimeLifecycleActionPlannerSnapshot,
        now: Long = System.currentTimeMillis(),
        maxItems: Int = RuntimeLifecycleActionInbox.DEFAULT_MAX_ITEMS
    ): RuntimeLifecycleActionInboxSnapshot {
        val previous = load(file)
        val recorded = RuntimeLifecycleActionInbox.record(
            previous = previous,
            planner = planner,
            now = now,
            maxItems = maxItems
        ).copy(
            persistenceMode = if (file == null) "in_memory_volatile" else "persistent_file",
            path = file?.absolutePath ?: "none",
            loadStatus = when {
                previous.loadStatus == "error_default" -> "recorded_after_error_default"
                previous.loadStatus == "missing_default" -> "recorded_after_missing_default"
                previous.loadStatus == "empty_default" -> "recorded_after_empty_default"
                else -> "recorded"
            },
            loadError = previous.loadError
        )
        if (file == null) {
            volatileSnapshot = recorded
            return recorded
        }
        return persist(file, recorded)
    }

    private fun persist(
        file: File,
        snapshot: RuntimeLifecycleActionInboxSnapshot
    ): RuntimeLifecycleActionInboxSnapshot {
        return runCatching {
            file.parentFile?.mkdirs()
            val signature = buildPersistenceSignature(snapshot)
            val now = System.currentTimeMillis()
            val path = file.absolutePath
            if (
                file.exists() &&
                signature == lastPersistedSignatureByPath[path] &&
                now - (lastPersistedAtByPath[path] ?: 0L) < ACTION_INBOX_PERSIST_MIN_INTERVAL_MS
            ) {
                return@runCatching snapshot.copy(loadStatus = "recorded_unchanged_skipped_persist")
            }
            file.writeText(toJson(snapshot).toString(2))
            lastPersistedSignatureByPath[path] = signature
            lastPersistedAtByPath[path] = now
            snapshot.copy(loadStatus = "recorded")
        }.getOrElse { error ->
            snapshot.copy(
                loadStatus = "persist_error",
                loadError = error.message ?: error::class.java.simpleName
            )
        }
    }

    private fun fromJson(
        json: JSONObject,
        file: File
    ): RuntimeLifecycleActionInboxSnapshot {
        val itemsJson = json.optJSONArray("items") ?: JSONArray()
        val items = (0 until itemsJson.length()).mapNotNull { index ->
            itemsJson.optJSONObject(index)?.let(::itemFromJson)
        }
        return RuntimeLifecycleActionInbox.snapshot(
            items = items,
            generatedAt = json.optLong("generatedAt", 0L),
            maxItems = json.optInt("maxItems", RuntimeLifecycleActionInbox.DEFAULT_MAX_ITEMS),
            persistenceMode = "persistent_file",
            path = file.absolutePath,
            loadStatus = "loaded"
        )
    }

    private fun itemFromJson(json: JSONObject): RuntimeLifecycleActionInboxItem {
        return RuntimeLifecycleActionInboxItem(
            actionId = json.optString("actionId", "unknown-action"),
            unitId = json.optString("unitId", "unknown-unit"),
            effectiveTier = enumValueOrDefault(
                json.optString("effectiveTier"),
                RuntimeLifecycleAuthorityTier.UNMANAGED
            ),
            finalAction = enumValueOrDefault(
                json.optString("finalAction"),
                RuntimeLifecycleFinalAction.OBSERVE
            ),
            finalActionMode = enumValueOrDefault(
                json.optString("finalActionMode"),
                RuntimeLifecycleFinalActionMode.OBSERVE_ONLY
            ),
            executorBoundary = enumValueOrDefault(
                json.optString("executorBoundary"),
                RuntimeLifecycleExecutorBoundary.NONE_OBSERVE_ONLY
            ),
            primaryReason = json.optString("primaryReason", "none"),
            suppressionReasons = json.optStringList("suppressionReasons"),
            blockedActions = json.optStringList("blockedActions"),
            allowedFutureActions = json.optStringList("allowedFutureActions"),
            requiresUserConfirmation = json.optBoolean("requiresUserConfirmation", false),
            isExecutableNow = false,
            createdAt = json.optLong("createdAt", 0L),
            updatedAt = json.optLong("updatedAt", 0L),
            expiresAt = json.optNullableLong("expiresAt"),
            dedupeKey = json.optString("dedupeKey", "unknown"),
            severity = enumValueOrDefault(
                json.optString("severity"),
                RuntimeLifecycleActionSeverity.INFO
            ),
            status = enumValueOrDefault(
                json.optString("status"),
                RuntimeLifecycleActionInboxStatus.OPEN
            ),
            seenCount = json.optInt("seenCount", 1).coerceAtLeast(1)
        )
    }

    private fun toJson(snapshot: RuntimeLifecycleActionInboxSnapshot): JSONObject {
        val items = JSONArray()
        snapshot.items.forEach { items.put(itemToJson(it)) }
        return JSONObject()
            .put("version", 1)
            .put("mode", snapshot.mode)
            .put("generatedAt", snapshot.generatedAt)
            .put("maxItems", snapshot.maxItems)
            .put("boundary", snapshot.boundary)
            .put("items", items)
    }

    private fun itemToJson(item: RuntimeLifecycleActionInboxItem): JSONObject {
        return JSONObject()
            .put("actionId", item.actionId)
            .put("unitId", item.unitId)
            .put("effectiveTier", item.effectiveTier.name)
            .put("finalAction", item.finalAction.name)
            .put("finalActionMode", item.finalActionMode.name)
            .put("executorBoundary", item.executorBoundary.name)
            .put("primaryReason", item.primaryReason)
            .put("suppressionReasons", JSONArray(item.suppressionReasons))
            .put("blockedActions", JSONArray(item.blockedActions))
            .put("allowedFutureActions", JSONArray(item.allowedFutureActions))
            .put("requiresUserConfirmation", item.requiresUserConfirmation)
            .put("isExecutableNow", false)
            .put("createdAt", item.createdAt)
            .put("updatedAt", item.updatedAt)
            .putNullable("expiresAt", item.expiresAt)
            .put("dedupeKey", item.dedupeKey)
            .put("severity", item.severity.name)
            .put("status", item.status.name)
            .put("seenCount", item.seenCount)
    }

    private fun buildPersistenceSignature(snapshot: RuntimeLifecycleActionInboxSnapshot): String {
        return buildString {
            append(snapshot.mode)
            append('|')
            append(snapshot.maxItems)
            append('|')
            snapshot.items.forEach { item ->
                append(item.actionId)
                append(':')
                append(item.unitId)
                append(':')
                append(item.effectiveTier.name)
                append(':')
                append(item.finalAction.name)
                append(':')
                append(item.finalActionMode.name)
                append(':')
                append(item.executorBoundary.name)
                append(':')
                append(item.primaryReason)
                append(':')
                append(item.suppressionReasons.joinToString(","))
                append(':')
                append(item.blockedActions.joinToString(","))
                append(':')
                append(item.allowedFutureActions.joinToString(","))
                append(':')
                append(item.requiresUserConfirmation)
                append(':')
                append(item.expiresAt ?: 0L)
                append(':')
                append(item.dedupeKey)
                append(':')
                append(item.severity.name)
                append(':')
                append(item.status.name)
                append(';')
            }
        }
    }

    private inline fun <reified T : Enum<T>> enumValueOrDefault(value: String?, default: T): T {
        return value
            ?.takeIf { it.isNotBlank() }
            ?.let { runCatching { enumValueOf<T>(it) }.getOrNull() }
            ?: default
    }
}

private fun JSONObject.putNullable(name: String, value: Long?): JSONObject {
    return put(name, value ?: JSONObject.NULL)
}

private fun JSONObject.optNullableLong(name: String): Long? {
    return if (!has(name) || isNull(name)) null else optLong(name)
}

private fun JSONObject.optStringList(name: String): List<String> {
    val array = optJSONArray(name) ?: return emptyList()
    return (0 until array.length()).mapNotNull { index ->
        array.optString(index).takeIf { it.isNotBlank() }
    }
}

private fun String?.toActionInboxEnvValue(): String {
    if (this.isNullOrBlank()) return "none"
    return trim()
        .replace(Regex("[\\r\\n\\t ]+"), "_")
        .replace(Regex("[^A-Za-z0-9._:@/+,-]"), "_")
        .take(240)
}
