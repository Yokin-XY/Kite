package com.kite.app.foundation.runtime

import java.io.File
import org.json.JSONArray
import org.json.JSONObject

enum class RuntimeResourceEpisodeState {
    NONE,
    OBSERVING,
    NEAR_LIMIT_EPISODE,
    OVER_LIMIT_EPISODE,
    RECOVERED,
    RESTART_CANDIDATE_DRY_RUN,
    QUARANTINE_CANDIDATE_DRY_RUN,
    SUPPRESSED_UNLIMITED,
    SUPPRESSED_AMBIGUOUS,
    SUPPRESSED_CORE_PROTECTED,
    SUPPRESSED_UNMANAGED
}

data class RuntimeResourceEventLedgerEntry(
    val rootKey: String,
    val unitId: String,
    val effectiveTier: RuntimeLifecycleAuthorityTier,
    val matchSource: RuntimeProcessUnitMatchSource = RuntimeProcessUnitMatchSource.NONE,
    val matchedPid: Int? = null,
    val matchedPgid: Int? = null,
    val matchedSid: Int? = null,
    val memoryState: RuntimeProcessResourceMemoryState =
        RuntimeProcessResourceMemoryState.DRY_RUN_ONLY,
    val lastMemoryKb: Long = 0L,
    val limitKb: Long? = null,
    val firstSeenAt: Long = 0L,
    val lastSeenAt: Long = 0L,
    val nearLimitCount: Int = 0,
    val overLimitCount: Int = 0,
    val consecutiveOverLimitCount: Int = 0,
    val recoveredCount: Int = 0,
    val lastRecoveryAt: Long? = null,
    val lastWarningAt: Long? = null,
    val restartCandidateCount: Int = 0,
    val quarantineCandidateCount: Int = 0,
    val cooldownUntilAt: Long? = null,
    val episodeState: RuntimeResourceEpisodeState = RuntimeResourceEpisodeState.NONE,
    val suppressionReason: String = "none"
)

data class RuntimeResourceEventLedgerSnapshot(
    val mode: String = "runtime_resource_event_ledger_v0",
    val enabled: Boolean = true,
    val enforcementEnabled: Boolean = false,
    val persistenceMode: String = "in_memory_volatile",
    val path: String = "none",
    val loadStatus: String = "volatile_default",
    val loadError: String? = null,
    val generatedAt: Long = 0L,
    val maxEntries: Int = RuntimeResourceEventLedger.DEFAULT_MAX_ENTRIES,
    val resourceEpisodeCount: Int = 0,
    val nearLimitEpisodeCount: Int = 0,
    val overLimitEpisodeCount: Int = 0,
    val restartCandidateDryRunCount: Int = 0,
    val quarantineCandidateDryRunCount: Int = 0,
    val suppressedUnlimitedCount: Int = 0,
    val suppressedAmbiguousCount: Int = 0,
    val suppressedCoreProtectedCount: Int = 0,
    val suppressedUnmanagedCount: Int = 0,
    val recoveredEpisodeCount: Int = 0,
    val entries: List<RuntimeResourceEventLedgerEntry> = emptyList(),
    val boundary: String =
        "diagnostic_state_only_no_restart_reclaim_kill_quarantine_or_proot_capacity_execution"
) {
    fun toEnvText(maxItems: Int = 8): String {
        return buildString {
            appendLine("runtime_resource_event_ledger_mode=${mode.toResourceLedgerEnvValue()}")
            appendLine("runtime_resource_event_ledger_enabled=$enabled")
            appendLine("runtime_resource_event_ledger_enforcement_enabled=$enforcementEnabled")
            appendLine("runtime_resource_event_ledger_persistence_mode=${persistenceMode.toResourceLedgerEnvValue()}")
            appendLine("runtime_resource_event_ledger_path=${path.toResourceLedgerEnvValue()}")
            appendLine("runtime_resource_event_ledger_load_status=${loadStatus.toResourceLedgerEnvValue()}")
            appendLine("runtime_resource_event_ledger_load_error=${loadError.toResourceLedgerEnvValue()}")
            appendLine("runtime_resource_event_ledger_generated_at=$generatedAt")
            appendLine("runtime_resource_event_ledger_max_entries=$maxEntries")
            appendLine("runtime_resource_event_ledger_episode_count=$resourceEpisodeCount")
            appendLine("runtime_resource_event_ledger_near_limit_episode_count=$nearLimitEpisodeCount")
            appendLine("runtime_resource_event_ledger_over_limit_episode_count=$overLimitEpisodeCount")
            appendLine("runtime_resource_event_ledger_restart_candidate_dry_run_count=$restartCandidateDryRunCount")
            appendLine("runtime_resource_event_ledger_quarantine_candidate_dry_run_count=$quarantineCandidateDryRunCount")
            appendLine("runtime_resource_event_ledger_suppressed_unlimited_count=$suppressedUnlimitedCount")
            appendLine("runtime_resource_event_ledger_suppressed_ambiguous_count=$suppressedAmbiguousCount")
            appendLine("runtime_resource_event_ledger_suppressed_core_protected_count=$suppressedCoreProtectedCount")
            appendLine("runtime_resource_event_ledger_suppressed_unmanaged_count=$suppressedUnmanagedCount")
            appendLine("runtime_resource_event_ledger_recovered_episode_count=$recoveredEpisodeCount")
            entries.take(maxItems).forEachIndexed { index, entry ->
                val prefix = "runtime_resource_event_ledger_unit_${index + 1}"
                appendLine("${prefix}_id=${entry.unitId.toResourceLedgerEnvValue()}")
                appendLine("${prefix}_effective_tier=${entry.effectiveTier.name}")
                appendLine("${prefix}_match_source=${entry.matchSource.name}")
                appendLine("${prefix}_matched_pid=${entry.matchedPid ?: 0}")
                appendLine("${prefix}_matched_pgid=${entry.matchedPgid ?: 0}")
                appendLine("${prefix}_matched_sid=${entry.matchedSid ?: 0}")
                appendLine("${prefix}_memory_state=${entry.memoryState.name}")
                appendLine("${prefix}_episode_state=${entry.episodeState.name}")
                appendLine("${prefix}_last_memory_kb=${entry.lastMemoryKb}")
                appendLine("${prefix}_limit_kb=${entry.limitKb ?: 0L}")
                appendLine("${prefix}_first_seen_at=${entry.firstSeenAt}")
                appendLine("${prefix}_last_seen_at=${entry.lastSeenAt}")
                appendLine("${prefix}_near_limit_count=${entry.nearLimitCount}")
                appendLine("${prefix}_over_limit_count=${entry.overLimitCount}")
                appendLine("${prefix}_consecutive_over_limit_count=${entry.consecutiveOverLimitCount}")
                appendLine("${prefix}_recovered_count=${entry.recoveredCount}")
                appendLine("${prefix}_last_recovery_at=${entry.lastRecoveryAt ?: 0L}")
                appendLine("${prefix}_last_warning_at=${entry.lastWarningAt ?: 0L}")
                appendLine("${prefix}_restart_candidate_count=${entry.restartCandidateCount}")
                appendLine("${prefix}_quarantine_candidate_count=${entry.quarantineCandidateCount}")
                appendLine("${prefix}_cooldown_until_at=${entry.cooldownUntilAt ?: 0L}")
                appendLine("${prefix}_suppression_reason=${entry.suppressionReason.toResourceLedgerEnvValue()}")
            }
            appendLine("runtime_resource_event_ledger_boundary=${boundary.toResourceLedgerEnvValue()}")
        }
    }
}

object RuntimeResourceEventLedger {
    const val DEFAULT_MAX_ENTRIES = 64
    const val DEFAULT_RECOVERY_COOLDOWN_MS = 60_000L

    fun record(
        previous: RuntimeResourceEventLedgerSnapshot = RuntimeResourceEventLedgerSnapshot(),
        resourceWatch: RuntimeProcessResourceWatchSnapshot,
        roots: List<RuntimeRootSnapshot>,
        now: Long = System.currentTimeMillis(),
        maxEntries: Int = DEFAULT_MAX_ENTRIES
    ): RuntimeResourceEventLedgerSnapshot {
        val rootByKey = roots.associateBy { it.ownershipKey }
        val previousByUnit = previous.entries.associateBy { it.unitId }
        val currentEntries = resourceWatch.entries.map { watchEntry ->
            nextEntry(
                previous = previousByUnit[watchEntry.unitId],
                watchEntry = watchEntry,
                root = rootByKey[watchEntry.rootKey],
                now = now
            )
        }
        val currentUnitIds = currentEntries.map { it.unitId }.toSet()
        val retainedEntries = previous.entries
            .asSequence()
            .filter { it.unitId !in currentUnitIds }
            .sortedByDescending { it.lastSeenAt }
            .take((maxEntries - currentEntries.size).coerceAtLeast(0))
            .map {
                it.copy(
                    suppressionReason =
                        "unit_not_present_in_current_resource_watch_preserved_for_recent_diagnostics"
                )
            }
            .toList()
        return snapshot(
            entries = (currentEntries + retainedEntries)
                .sortedWith(
                    compareByDescending<RuntimeResourceEventLedgerEntry> { it.lastSeenAt }
                        .thenBy { it.unitId }
                )
                .take(maxEntries.coerceAtLeast(1)),
            generatedAt = now,
            maxEntries = maxEntries,
            persistenceMode = previous.persistenceMode,
            path = previous.path,
            loadStatus = previous.loadStatus,
            loadError = previous.loadError
        )
    }

    internal fun snapshot(
        entries: List<RuntimeResourceEventLedgerEntry>,
        generatedAt: Long,
        maxEntries: Int,
        persistenceMode: String,
        path: String,
        loadStatus: String,
        loadError: String? = null
    ): RuntimeResourceEventLedgerSnapshot {
        val boundedEntries = entries.take(maxEntries.coerceAtLeast(1))
        return RuntimeResourceEventLedgerSnapshot(
            persistenceMode = persistenceMode,
            path = path,
            loadStatus = loadStatus,
            loadError = loadError,
            generatedAt = generatedAt,
            maxEntries = maxEntries,
            resourceEpisodeCount = boundedEntries.count {
                it.episodeState != RuntimeResourceEpisodeState.NONE
            },
            nearLimitEpisodeCount = boundedEntries.count {
                it.episodeState == RuntimeResourceEpisodeState.NEAR_LIMIT_EPISODE
            },
            overLimitEpisodeCount = boundedEntries.count {
                it.episodeState == RuntimeResourceEpisodeState.OVER_LIMIT_EPISODE ||
                    it.episodeState == RuntimeResourceEpisodeState.RESTART_CANDIDATE_DRY_RUN ||
                    it.episodeState == RuntimeResourceEpisodeState.QUARANTINE_CANDIDATE_DRY_RUN
            },
            restartCandidateDryRunCount = boundedEntries.count {
                it.episodeState == RuntimeResourceEpisodeState.RESTART_CANDIDATE_DRY_RUN
            },
            quarantineCandidateDryRunCount = boundedEntries.count {
                it.episodeState == RuntimeResourceEpisodeState.QUARANTINE_CANDIDATE_DRY_RUN
            },
            suppressedUnlimitedCount = boundedEntries.count {
                it.episodeState == RuntimeResourceEpisodeState.SUPPRESSED_UNLIMITED
            },
            suppressedAmbiguousCount = boundedEntries.count {
                it.episodeState == RuntimeResourceEpisodeState.SUPPRESSED_AMBIGUOUS
            },
            suppressedCoreProtectedCount = boundedEntries.count {
                it.episodeState == RuntimeResourceEpisodeState.SUPPRESSED_CORE_PROTECTED
            },
            suppressedUnmanagedCount = boundedEntries.count {
                it.episodeState == RuntimeResourceEpisodeState.SUPPRESSED_UNMANAGED
            },
            recoveredEpisodeCount = boundedEntries.count {
                it.episodeState == RuntimeResourceEpisodeState.RECOVERED
            },
            entries = boundedEntries
        )
    }

    private fun nextEntry(
        previous: RuntimeResourceEventLedgerEntry?,
        watchEntry: RuntimeProcessResourceWatchEntry,
        root: RuntimeRootSnapshot?,
        now: Long
    ): RuntimeResourceEventLedgerEntry {
        val base = previous
            ?.copy(
                rootKey = watchEntry.rootKey,
                effectiveTier = watchEntry.effectiveTier,
                matchSource = root?.processUnitMatchSource ?: previous.matchSource,
                matchedPid = watchEntry.matchedPid,
                matchedPgid = watchEntry.matchedPgid,
                matchedSid = watchEntry.matchedSid,
                memoryState = watchEntry.memoryState,
                lastMemoryKb = watchEntry.memoryTreeKb.coerceAtLeast(watchEntry.memoryCurrentKb),
                limitKb = watchEntry.expectedMemoryLimitKb,
                lastSeenAt = now
            )
            ?: RuntimeResourceEventLedgerEntry(
                rootKey = watchEntry.rootKey,
                unitId = watchEntry.unitId,
                effectiveTier = watchEntry.effectiveTier,
                matchSource = root?.processUnitMatchSource ?: RuntimeProcessUnitMatchSource.NONE,
                matchedPid = watchEntry.matchedPid,
                matchedPgid = watchEntry.matchedPgid,
                matchedSid = watchEntry.matchedSid,
                memoryState = watchEntry.memoryState,
                lastMemoryKb = watchEntry.memoryTreeKb.coerceAtLeast(watchEntry.memoryCurrentKb),
                limitKb = watchEntry.expectedMemoryLimitKb,
                firstSeenAt = now,
                lastSeenAt = now
            )

        val observationState = root?.processUnitObservedState
            ?: root?.stopReconciliationState
            ?: root?.takeIf { !it.isRunning }?.let {
                RuntimeProcessStopReconciliation.evaluate(it).observedState
            }

        if (observationState == RuntimeProcessUnitObservationState.STOPPED_EXPECTED) {
            return base.copy(
                episodeState = RuntimeResourceEpisodeState.NONE,
                consecutiveOverLimitCount = 0,
                cooldownUntilAt = null,
                suppressionReason = "expected_stop_closed_resource_episode"
            )
        }

        if (observationState == RuntimeProcessUnitObservationState.WAIT_CONFIRM_RESTART) {
            return base.copy(
                episodeState = previous?.episodeState ?: RuntimeResourceEpisodeState.OBSERVING,
                consecutiveOverLimitCount = previous?.consecutiveOverLimitCount ?: 0,
                suppressionReason =
                    "wait_confirm_preserves_resource_diagnostic_no_auto_restart_execution"
            )
        }

        if (
            observationState == RuntimeProcessUnitObservationState.STOPPED_CRASH_SUSPECTED ||
            observationState == RuntimeProcessUnitObservationState.AUTO_RESTART_ALLOWED
        ) {
            return base.copy(
                episodeState = previous?.episodeState ?: RuntimeResourceEpisodeState.OBSERVING,
                consecutiveOverLimitCount = previous?.consecutiveOverLimitCount ?: 0,
                suppressionReason =
                    "stopped_${observationState.name.lowercase()}_preserve_resource_episode_dry_run_only"
            )
        }

        if (
            watchEntry.effectiveTier == RuntimeLifecycleAuthorityTier.SYSTEM_CORE ||
            watchEntry.effectiveTier == RuntimeLifecycleAuthorityTier.PROOT_CORE ||
            observationState == RuntimeProcessUnitObservationState.CORE_RECOVERY_REQUIRED
        ) {
            return base.copy(
                episodeState = RuntimeResourceEpisodeState.SUPPRESSED_CORE_PROTECTED,
                consecutiveOverLimitCount = 0,
                cooldownUntilAt = null,
                suppressionReason =
                    "core_or_proot_core_resource_episode_is_diagnostic_only"
            )
        }

        if (watchEntry.effectiveTier == RuntimeLifecycleAuthorityTier.UNMANAGED) {
            return base.copy(
                episodeState = RuntimeResourceEpisodeState.SUPPRESSED_UNMANAGED,
                consecutiveOverLimitCount = 0,
                cooldownUntilAt = null,
                suppressionReason = "unmanaged_process_observe_only_no_auto_registration_or_restart"
            )
        }

        return when (watchEntry.memoryState) {
            RuntimeProcessResourceMemoryState.WITHIN_LIMIT -> withinLimit(
                base = base,
                previous = previous,
                now = now
            )

            RuntimeProcessResourceMemoryState.NEAR_LIMIT -> base.copy(
                episodeState = RuntimeResourceEpisodeState.NEAR_LIMIT_EPISODE,
                nearLimitCount = base.nearLimitCount + 1,
                consecutiveOverLimitCount = 0,
                lastWarningAt = now,
                cooldownUntilAt = null,
                suppressionReason = "near_limit_warning_episode_dry_run_only"
            )

            RuntimeProcessResourceMemoryState.OVER_LIMIT -> overLimit(
                base = base,
                root = root,
                watchEntry = watchEntry
            )

            RuntimeProcessResourceMemoryState.OVER_LIMIT_BUT_UNLIMITED -> base.copy(
                episodeState = RuntimeResourceEpisodeState.SUPPRESSED_UNLIMITED,
                consecutiveOverLimitCount = 0,
                cooldownUntilAt = null,
                suppressionReason =
                    "unlimited_memory_over_limit_observed_no_enforcement_count"
            )

            RuntimeProcessResourceMemoryState.AMBIGUOUS_MATCH_NO_ENFORCEMENT -> base.copy(
                episodeState = RuntimeResourceEpisodeState.SUPPRESSED_AMBIGUOUS,
                consecutiveOverLimitCount = 0,
                cooldownUntilAt = null,
                suppressionReason =
                    "ambiguous_match_blocks_resource_episode_enforcement_counts"
            )

            RuntimeProcessResourceMemoryState.CORE_PROTECTED_NO_ENFORCEMENT -> base.copy(
                episodeState = RuntimeResourceEpisodeState.SUPPRESSED_CORE_PROTECTED,
                consecutiveOverLimitCount = 0,
                cooldownUntilAt = null,
                suppressionReason =
                    "core_protected_resource_episode_diagnostic_only"
            )

            RuntimeProcessResourceMemoryState.NO_LIMIT,
            RuntimeProcessResourceMemoryState.NO_MEMORY_DATA,
            RuntimeProcessResourceMemoryState.DRY_RUN_ONLY -> base.copy(
                episodeState = RuntimeResourceEpisodeState.OBSERVING,
                consecutiveOverLimitCount = 0,
                cooldownUntilAt = null,
                suppressionReason = watchEntry.resourceSuppressionReason
            )
        }
    }

    private fun withinLimit(
        base: RuntimeResourceEventLedgerEntry,
        previous: RuntimeResourceEventLedgerEntry?,
        now: Long
    ): RuntimeResourceEventLedgerEntry {
        val wasActiveEpisode = previous?.episodeState in setOf(
            RuntimeResourceEpisodeState.NEAR_LIMIT_EPISODE,
            RuntimeResourceEpisodeState.OVER_LIMIT_EPISODE,
            RuntimeResourceEpisodeState.RESTART_CANDIDATE_DRY_RUN,
            RuntimeResourceEpisodeState.QUARANTINE_CANDIDATE_DRY_RUN
        )
        val inRecoveryCooldown = previous?.episodeState == RuntimeResourceEpisodeState.RECOVERED &&
            (previous.cooldownUntilAt ?: 0L) > now
        return when {
            wasActiveEpisode -> base.copy(
                episodeState = RuntimeResourceEpisodeState.RECOVERED,
                consecutiveOverLimitCount = 0,
                recoveredCount = base.recoveredCount + 1,
                lastRecoveryAt = now,
                cooldownUntilAt = now + DEFAULT_RECOVERY_COOLDOWN_MS,
                suppressionReason = "within_limit_recovered_consecutive_count_reset"
            )

            inRecoveryCooldown -> base.copy(
                episodeState = RuntimeResourceEpisodeState.RECOVERED,
                consecutiveOverLimitCount = 0,
                suppressionReason = "within_limit_recovery_cooldown_active"
            )

            else -> base.copy(
                episodeState = RuntimeResourceEpisodeState.OBSERVING,
                consecutiveOverLimitCount = 0,
                cooldownUntilAt = null,
                suppressionReason = "within_expected_memory_limit"
            )
        }
    }

    private fun overLimit(
        base: RuntimeResourceEventLedgerEntry,
        root: RuntimeRootSnapshot?,
        watchEntry: RuntimeProcessResourceWatchEntry
    ): RuntimeResourceEventLedgerEntry {
        val nextConsecutive = base.consecutiveOverLimitCount + 1
        val quarantineAfterFailures = root
            ?.processUnitQuarantineAfterFailures
            ?.coerceAtLeast(1)
            ?: 3
        val nextState = when {
            watchEntry.effectiveTier == RuntimeLifecycleAuthorityTier.USER_LOCKED &&
                nextConsecutive >= quarantineAfterFailures ->
                RuntimeResourceEpisodeState.QUARANTINE_CANDIDATE_DRY_RUN

            watchEntry.recommendedResourceAction ==
                RuntimeProcessResourceRecommendedAction.QUARANTINE_CANDIDATE_DRY_RUN ->
                RuntimeResourceEpisodeState.QUARANTINE_CANDIDATE_DRY_RUN

            watchEntry.recommendedResourceAction ==
                RuntimeProcessResourceRecommendedAction.RESTART_CANDIDATE_DRY_RUN ->
                RuntimeResourceEpisodeState.RESTART_CANDIDATE_DRY_RUN

            else -> RuntimeResourceEpisodeState.OVER_LIMIT_EPISODE
        }
        return base.copy(
            episodeState = nextState,
            overLimitCount = base.overLimitCount + 1,
            consecutiveOverLimitCount = nextConsecutive,
            restartCandidateCount = if (nextState == RuntimeResourceEpisodeState.RESTART_CANDIDATE_DRY_RUN) {
                base.restartCandidateCount + 1
            } else {
                base.restartCandidateCount
            },
            quarantineCandidateCount =
                if (nextState == RuntimeResourceEpisodeState.QUARANTINE_CANDIDATE_DRY_RUN) {
                    base.quarantineCandidateCount + 1
                } else {
                    base.quarantineCandidateCount
                },
            cooldownUntilAt = null,
            suppressionReason =
                "${watchEntry.resourceSuppressionReason},ledger_dry_run_only_no_execution"
        )
    }
}

object RuntimeResourceEventLedgerStore {
    private const val RESOURCE_LEDGER_PERSIST_MIN_INTERVAL_MS = 60_000L

    private var volatileSnapshot = RuntimeResourceEventLedgerSnapshot()
    private val lastPersistedSignatureByPath = mutableMapOf<String, String>()
    private val lastPersistedAtByPath = mutableMapOf<String, Long>()

    @Synchronized
    fun resetVolatileForTests() {
        volatileSnapshot = RuntimeResourceEventLedgerSnapshot()
        lastPersistedSignatureByPath.clear()
        lastPersistedAtByPath.clear()
    }

    @Synchronized
    fun load(file: File?): RuntimeResourceEventLedgerSnapshot {
        if (file == null) {
            return volatileSnapshot.copy(
                persistenceMode = "in_memory_volatile",
                path = "none",
                loadStatus = "volatile_default"
            )
        }
        if (!file.exists()) {
            return RuntimeResourceEventLedgerSnapshot(
                persistenceMode = "persistent_file",
                path = file.absolutePath,
                loadStatus = "missing_default"
            )
        }
        return runCatching {
            val text = file.readText()
            if (text.isBlank()) {
                RuntimeResourceEventLedgerSnapshot(
                    persistenceMode = "persistent_file",
                    path = file.absolutePath,
                    loadStatus = "empty_default"
                )
            } else {
                fromJson(JSONObject(text), file)
            }
        }.getOrElse { error ->
            RuntimeResourceEventLedgerSnapshot(
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
        resourceWatch: RuntimeProcessResourceWatchSnapshot,
        roots: List<RuntimeRootSnapshot>,
        now: Long = System.currentTimeMillis(),
        maxEntries: Int = RuntimeResourceEventLedger.DEFAULT_MAX_ENTRIES
    ): RuntimeResourceEventLedgerSnapshot {
        val previous = load(file)
        val recorded = RuntimeResourceEventLedger.record(
            previous = previous,
            resourceWatch = resourceWatch,
            roots = roots,
            now = now,
            maxEntries = maxEntries
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
        snapshot: RuntimeResourceEventLedgerSnapshot
    ): RuntimeResourceEventLedgerSnapshot {
        return runCatching {
            file.parentFile?.mkdirs()
            val signature = buildPersistenceSignature(snapshot)
            val now = System.currentTimeMillis()
            val path = file.absolutePath
            if (
                file.exists() &&
                signature == lastPersistedSignatureByPath[path] &&
                now - (lastPersistedAtByPath[path] ?: 0L) < RESOURCE_LEDGER_PERSIST_MIN_INTERVAL_MS
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
    ): RuntimeResourceEventLedgerSnapshot {
        val entriesJson = json.optJSONArray("entries") ?: JSONArray()
        val entries = (0 until entriesJson.length()).mapNotNull { index ->
            entriesJson.optJSONObject(index)?.let(::entryFromJson)
        }
        return RuntimeResourceEventLedger.snapshot(
            entries = entries,
            generatedAt = json.optLong("generatedAt", 0L),
            maxEntries = json.optInt("maxEntries", RuntimeResourceEventLedger.DEFAULT_MAX_ENTRIES),
            persistenceMode = "persistent_file",
            path = file.absolutePath,
            loadStatus = "loaded",
            loadError = null
        )
    }

    private fun entryFromJson(json: JSONObject): RuntimeResourceEventLedgerEntry {
        return RuntimeResourceEventLedgerEntry(
            rootKey = json.optString("rootKey", "none"),
            unitId = json.optString("unitId", "none"),
            effectiveTier = enumValueOrDefault(
                json.optString("effectiveTier"),
                RuntimeLifecycleAuthorityTier.UNMANAGED
            ),
            matchSource = enumValueOrDefault(
                json.optString("matchSource"),
                RuntimeProcessUnitMatchSource.NONE
            ),
            matchedPid = json.optNullableInt("matchedPid"),
            matchedPgid = json.optNullableInt("matchedPgid"),
            matchedSid = json.optNullableInt("matchedSid"),
            memoryState = enumValueOrDefault(
                json.optString("memoryState"),
                RuntimeProcessResourceMemoryState.DRY_RUN_ONLY
            ),
            lastMemoryKb = json.optLong("lastMemoryKb", 0L),
            limitKb = json.optNullableLong("limitKb"),
            firstSeenAt = json.optLong("firstSeenAt", 0L),
            lastSeenAt = json.optLong("lastSeenAt", 0L),
            nearLimitCount = json.optInt("nearLimitCount", 0),
            overLimitCount = json.optInt("overLimitCount", 0),
            consecutiveOverLimitCount = json.optInt("consecutiveOverLimitCount", 0),
            recoveredCount = json.optInt("recoveredCount", 0),
            lastRecoveryAt = json.optNullableLong("lastRecoveryAt"),
            lastWarningAt = json.optNullableLong("lastWarningAt"),
            restartCandidateCount = json.optInt("restartCandidateCount", 0),
            quarantineCandidateCount = json.optInt("quarantineCandidateCount", 0),
            cooldownUntilAt = json.optNullableLong("cooldownUntilAt"),
            episodeState = enumValueOrDefault(
                json.optString("episodeState"),
                RuntimeResourceEpisodeState.NONE
            ),
            suppressionReason = json.optString("suppressionReason", "none")
        )
    }

    private fun toJson(snapshot: RuntimeResourceEventLedgerSnapshot): JSONObject {
        val entries = JSONArray()
        snapshot.entries.forEach { entries.put(entryToJson(it)) }
        return JSONObject()
            .put("version", 1)
            .put("mode", snapshot.mode)
            .put("generatedAt", snapshot.generatedAt)
            .put("maxEntries", snapshot.maxEntries)
            .put("boundary", snapshot.boundary)
            .put("entries", entries)
    }

    private fun entryToJson(entry: RuntimeResourceEventLedgerEntry): JSONObject {
        return JSONObject()
            .put("rootKey", entry.rootKey)
            .put("unitId", entry.unitId)
            .put("effectiveTier", entry.effectiveTier.name)
            .put("matchSource", entry.matchSource.name)
            .putNullable("matchedPid", entry.matchedPid)
            .putNullable("matchedPgid", entry.matchedPgid)
            .putNullable("matchedSid", entry.matchedSid)
            .put("memoryState", entry.memoryState.name)
            .put("lastMemoryKb", entry.lastMemoryKb)
            .putNullable("limitKb", entry.limitKb)
            .put("firstSeenAt", entry.firstSeenAt)
            .put("lastSeenAt", entry.lastSeenAt)
            .put("nearLimitCount", entry.nearLimitCount)
            .put("overLimitCount", entry.overLimitCount)
            .put("consecutiveOverLimitCount", entry.consecutiveOverLimitCount)
            .put("recoveredCount", entry.recoveredCount)
            .putNullable("lastRecoveryAt", entry.lastRecoveryAt)
            .putNullable("lastWarningAt", entry.lastWarningAt)
            .put("restartCandidateCount", entry.restartCandidateCount)
            .put("quarantineCandidateCount", entry.quarantineCandidateCount)
            .putNullable("cooldownUntilAt", entry.cooldownUntilAt)
            .put("episodeState", entry.episodeState.name)
            .put("suppressionReason", entry.suppressionReason)
    }

    private fun buildPersistenceSignature(snapshot: RuntimeResourceEventLedgerSnapshot): String {
        return buildString {
            append(snapshot.mode)
            append('|')
            append(snapshot.maxEntries)
            append('|')
            snapshot.entries.forEach { entry ->
                append(entry.rootKey)
                append(':')
                append(entry.unitId)
                append(':')
                append(entry.effectiveTier.name)
                append(':')
                append(entry.matchSource.name)
                append(':')
                append(entry.matchedPid ?: 0)
                append(':')
                append(entry.matchedPgid ?: 0)
                append(':')
                append(entry.matchedSid ?: 0)
                append(':')
                append(entry.memoryState.name)
                append(':')
                append(entry.limitKb ?: 0L)
                append(':')
                append(entry.nearLimitCount)
                append(':')
                append(entry.overLimitCount)
                append(':')
                append(entry.consecutiveOverLimitCount)
                append(':')
                append(entry.recoveredCount)
                append(':')
                append(entry.restartCandidateCount)
                append(':')
                append(entry.quarantineCandidateCount)
                append(':')
                append(entry.cooldownUntilAt ?: 0L)
                append(':')
                append(entry.episodeState.name)
                append(':')
                append(entry.suppressionReason)
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

private fun JSONObject.putNullable(name: String, value: Int?): JSONObject {
    return put(name, value ?: JSONObject.NULL)
}

private fun JSONObject.putNullable(name: String, value: Long?): JSONObject {
    return put(name, value ?: JSONObject.NULL)
}

private fun JSONObject.optNullableInt(name: String): Int? {
    return if (!has(name) || isNull(name)) null else optInt(name)
}

private fun JSONObject.optNullableLong(name: String): Long? {
    return if (!has(name) || isNull(name)) null else optLong(name)
}

private fun String?.toResourceLedgerEnvValue(): String {
    if (this.isNullOrBlank()) return "none"
    return trim()
        .replace(Regex("[\\r\\n\\t ]+"), "_")
        .replace(Regex("[^A-Za-z0-9._:@/+,-]"), "_")
        .take(240)
}
