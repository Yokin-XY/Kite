package com.kite.app.run

import android.content.Context
import android.content.SharedPreferences
import com.kite.app.recipe.KiteRecipe
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

object CardRunStore {
    private val _runs = MutableStateFlow<List<CardRunState>>(emptyList())
    private val registeredRecipes = linkedMapOf<String, KiteRecipe>()
    private val runsByInstance = linkedMapOf<String, CardRunState>()
    private val historiesByRecipe = linkedMapOf<String, MutableList<CardRunHistoryEntry>>()
    private val persistExecutor = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "KiteCardRunStorePersist").apply { isDaemon = true }
    }
    private val persistScheduleLock = Any()
    private var prefs: SharedPreferences? = null
    private var initialized = false
    private var persistScheduled = false
    private var historyPersistScheduled = false
    val runs: StateFlow<List<CardRunState>> = _runs

    @Synchronized
    fun initialize(context: Context) {
        if (initialized) return
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val loaded = loadPersistedRuns()
        val normalized = loaded.map { it.normalizedAfterProcessRestore() }
        runsByInstance.clear()
        normalized.forEach { runsByInstance[it.instanceId] = it }
        historiesByRecipe.clear()
        loadPersistedHistory().forEach { entry ->
            historiesByRecipe.getOrPut(entry.recipeId) { mutableListOf() }.add(entry)
        }
        trimAllHistory()
        _runs.value = sortedRuns()
        initialized = true
        if (normalized != loaded) {
            persistRuns()
        }
    }

    @Synchronized
    fun registerRecipe(recipe: KiteRecipe) {
        registeredRecipes[recipe.id] = recipe
    }

    @Synchronized
    fun registeredRecipe(recipeId: String): KiteRecipe? =
        registeredRecipes[recipeId]

    @Synchronized
    fun start(
        recipe: KiteRecipe,
        instanceId: String = recipe.id,
        parentInstanceId: String? = null,
        ownerKind: String = CardRunState.OWNER_KIND_CARD,
        stepId: String? = null
    ): CardRunState {
        registerRecipe(recipe)
        val now = System.currentTimeMillis()
        val run = CardRunState(
            instanceId = instanceId,
            recipeId = recipe.id,
            recipeName = recipe.name,
            parentInstanceId = parentInstanceId,
            ownerKind = ownerKind,
            stepId = stepId,
            status = CardRunStatus.Starting,
            stepCount = recipe.steps.size,
            createdAt = now,
            updatedAt = now
        )
        upsert(run)
        recordHistoryStart(recipe, run, now)
        return run
    }

    @Synchronized
    fun update(
        recipe: KiteRecipe,
        status: CardRunStatus,
        instanceId: String? = null,
        parentInstanceId: String? = null,
        ownerKind: String? = null,
        stepId: String? = null,
        surface: CardRunSurface? = null,
        currentStepIndex: Int? = null,
        runId: String? = null,
        terminalSessionId: String? = null,
        pid: String? = null,
        rootPid: String? = null,
        processGroupId: String? = null,
        systemSessionId: String? = null,
        lastMeaningfulOutput: String? = null,
        lastError: String? = null,
        shellReportText: String? = null,
        nextActionUrl: String? = null,
        clearRunBinding: Boolean = false,
        clearTerminalSession: Boolean = false,
        clearNextActionUrl: Boolean = false
    ): CardRunState {
        val now = System.currentTimeMillis()
        val existing = instanceId
            ?.takeIf { it.isNotBlank() }
            ?.let { get(it) ?: start(recipe, it) }
            ?: currentForRecipe(recipe.id)
            ?: start(recipe)
        val beginsNewHistory = existing.status.endsHistoryEntry() &&
            (status.startsHistoryEntry() || status.isImmediateHistoryEnd())
        val resolvedSurface = surface ?: when {
            !nextActionUrl.isNullOrBlank() -> CardRunSurface.Web
            !terminalSessionId.isNullOrBlank() -> CardRunSurface.Terminal
            !lastError.isNullOrBlank() || !lastMeaningfulOutput.isNullOrBlank() || !shellReportText.isNullOrBlank() -> CardRunSurface.Report
            else -> existing.surface
        }
        val next = existing.copy(
            recipeName = recipe.name,
            parentInstanceId = parentInstanceId ?: existing.parentInstanceId,
            ownerKind = ownerKind ?: existing.ownerKind,
            stepId = stepId ?: existing.stepId,
            status = status,
            surface = resolvedSurface,
            currentStepIndex = currentStepIndex ?: existing.currentStepIndex,
            stepCount = recipe.steps.size,
            runId = if (clearRunBinding) null else runId ?: existing.runId,
            terminalSessionId = if (clearRunBinding || clearTerminalSession) null else terminalSessionId ?: existing.terminalSessionId,
            pid = if (clearRunBinding) null else pid ?: existing.pid,
            rootPid = if (clearRunBinding) null else rootPid ?: existing.rootPid,
            processGroupId = if (clearRunBinding) null else processGroupId ?: existing.processGroupId,
            systemSessionId = if (clearRunBinding) null else systemSessionId ?: existing.systemSessionId,
            lastMeaningfulOutput = lastMeaningfulOutput ?: existing.lastMeaningfulOutput,
            lastError = lastError,
            shellReportText = shellReportText ?: existing.shellReportText,
            nextActionUrl = if (clearRunBinding || clearNextActionUrl) null else nextActionUrl ?: existing.nextActionUrl,
            createdAt = if (beginsNewHistory) now else existing.createdAt,
            updatedAt = now
        )
        upsert(next)
        if (beginsNewHistory) {
            recordHistoryStart(recipe, next, now)
        } else {
            recordHistoryUpdate(recipe, next, now)
        }
        return next
    }

    @Synchronized
    fun selectSurface(instanceId: String, surface: CardRunSurface): CardRunState? {
        val existing = get(instanceId) ?: return null
        val next = existing.copy(surface = surface, updatedAt = System.currentTimeMillis())
        upsert(next)
        return next
    }

    @Synchronized
    fun currentForRecipe(recipeId: String): CardRunState? =
        _runs.value
            .filter { it.recipeId == recipeId }
            .maxByOrNull { it.updatedAt }

    @Synchronized
    fun get(instanceId: String): CardRunState? =
        runsByInstance[instanceId]

    @Synchronized
    fun snapshot(): List<CardRunState> = _runs.value

    @Synchronized
    fun childrenOf(parentInstanceId: String): List<CardRunState> =
        _runs.value
            .filter { it.parentInstanceId == parentInstanceId }
            .sortedByDescending { it.updatedAt }

    @Synchronized
    fun historyForRecipe(recipeId: String, limit: Int = MAX_HISTORY_PER_RECIPE): List<CardRunHistoryEntry> =
        historiesByRecipe[recipeId]
            ?.sortedByDescending { it.startedAt }
            ?.take(limit.coerceAtLeast(0))
            .orEmpty()

    @Synchronized
    fun removeRecipes(recipeIds: Collection<String>) {
        val ids = recipeIds
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toSet()
        if (ids.isEmpty()) return
        val beforeSize = runsByInstance.size
        runsByInstance.entries.removeAll { (_, run) -> run.recipeId in ids || run.instanceId in ids }
        val historyChanged = ids.any { historiesByRecipe.remove(it) != null }
        if (runsByInstance.size == beforeSize && !historyChanged) return
        _runs.value = sortedRuns()
        schedulePersistRuns()
        if (historyChanged) schedulePersistHistory()
    }

    @Synchronized
    private fun upsert(run: CardRunState) {
        runsByInstance[run.instanceId] = run
        _runs.value = sortedRuns()
        schedulePersistRuns()
    }

    private fun sortedRuns(): List<CardRunState> =
        runsByInstance.values.sortedByDescending { it.updatedAt }

    private fun schedulePersistRuns() {
        if (prefs == null) return
        synchronized(persistScheduleLock) {
            if (persistScheduled) return
            persistScheduled = true
        }
        persistExecutor.schedule(
            {
                synchronized(persistScheduleLock) {
                    persistScheduled = false
                }
                persistRuns()
            },
            PERSIST_DEBOUNCE_MS,
            TimeUnit.MILLISECONDS
        )
    }

    private fun persistRuns() {
        val store = prefs ?: return
        val payload = JSONArray()
        _runs.value
            .sortedByDescending { it.updatedAt }
            .filterNot { it.isTemporaryResourceRun() }
            .take(MAX_STORED_RUNS)
            .forEach { payload.put(it.toJson()) }
        store.edit().putString(KEY_RUNS, payload.toString()).apply()
    }

    private fun schedulePersistHistory() {
        if (prefs == null) return
        synchronized(persistScheduleLock) {
            if (historyPersistScheduled) return
            historyPersistScheduled = true
        }
        persistExecutor.schedule(
            {
                synchronized(persistScheduleLock) {
                    historyPersistScheduled = false
                }
                persistHistory()
            },
            PERSIST_DEBOUNCE_MS,
            TimeUnit.MILLISECONDS
        )
    }

    private fun persistHistory() {
        val store = prefs ?: return
        val entries = synchronized(this) {
            historiesByRecipe.values.flatMap { it.toList() }
        }
        val payload = JSONArray()
        entries
            .sortedByDescending { it.updatedAt }
            .take(MAX_STORED_HISTORY_ENTRIES)
            .forEach { payload.put(it.toJson()) }
        store.edit().putString(KEY_HISTORY, payload.toString()).apply()
    }

    private fun loadPersistedRuns(): List<CardRunState> {
        val raw = prefs?.getString(KEY_RUNS, null).orEmpty()
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    array.optJSONObject(index)?.toCardRunStateOrNull()?.let { add(it) }
                }
            }.sortedByDescending { it.updatedAt }
                .filterNot { it.isTemporaryResourceRun() }
        }.getOrDefault(emptyList())
    }

    private fun loadPersistedHistory(): List<CardRunHistoryEntry> {
        val raw = prefs?.getString(KEY_HISTORY, null).orEmpty()
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    array.optJSONObject(index)?.toCardRunHistoryEntryOrNull()?.let { add(it.normalizedHistoryAfterProcessRestore()) }
                }
            }.sortedByDescending { it.updatedAt }
        }.getOrDefault(emptyList())
    }

    private fun CardRunState.normalizedAfterProcessRestore(): CardRunState {
        if (!status.shouldResetAfterProcessRestore()) return this
        return copy(
            status = CardRunStatus.Stopped,
            surface = when {
                !shellReportText.isNullOrBlank() || !lastMeaningfulOutput.isNullOrBlank() -> CardRunSurface.Report
                else -> CardRunSurface.Summary
            },
            runId = null,
            terminalSessionId = null,
            pid = null,
            rootPid = null,
            processGroupId = null,
            systemSessionId = null,
            lastMeaningfulOutput = lastMeaningfulOutput ?: "Kite 重新启动，已重置上次未完成状态",
            lastError = null,
            nextActionUrl = null
        )
    }

    private fun CardRunStatus.shouldResetAfterProcessRestore(): Boolean =
        this == CardRunStatus.Starting ||
            this == CardRunStatus.Running ||
            this == CardRunStatus.WaitingTerminal ||
            this == CardRunStatus.AlreadyRunning ||
            this == CardRunStatus.Opened ||
            this == CardRunStatus.Stopping

    private fun CardRunState.isTemporaryResourceRun(): Boolean =
        recipeId.startsWith("resource-install-wizard-") ||
            (recipeId.startsWith("resource-") && (recipeId.endsWith("-install") || recipeId.endsWith("-uninstall")))

    private fun recordHistoryStart(recipe: KiteRecipe, state: CardRunState, now: Long = state.createdAt) {
        if (state.isTemporaryResourceRun()) return
        val entry = CardRunHistoryEntry(
            historyId = "${state.instanceId}@$now",
            recipeId = recipe.id,
            recipeName = recipe.name,
            instanceId = state.instanceId,
            ownerKind = state.ownerKind,
            status = state.status,
            currentStepIndex = state.currentStepIndex,
            stepCount = state.stepCount.takeIf { it > 0 } ?: recipe.steps.size,
            startedAt = now,
            endedAt = if (state.status.endsHistoryEntry()) now else null,
            updatedAt = now,
            summary = state.lastMeaningfulOutput.orEmpty().take(MAX_HISTORY_SUMMARY_CHARS),
            error = state.lastError.orEmpty().take(MAX_HISTORY_SUMMARY_CHARS),
            shellReportText = state.shellReportText.orEmpty().takeLast(MAX_HISTORY_REPORT_CHARS),
            steps = recipe.toHistorySteps(state)
        )
        val entries = historiesByRecipe.getOrPut(recipe.id) { mutableListOf() }
        entries.removeAll { it.historyId == entry.historyId }
        entries.add(0, entry)
        trimHistory(recipe.id)
        schedulePersistHistory()
    }

    private fun recordHistoryUpdate(recipe: KiteRecipe, state: CardRunState, now: Long = state.updatedAt) {
        if (state.isTemporaryResourceRun()) return
        val entries = historiesByRecipe.getOrPut(recipe.id) { mutableListOf() }
        val current = entries.firstOrNull { it.instanceId == state.instanceId && !it.isClosed() }
            ?: entries.firstOrNull()
            ?: run {
                recordHistoryStart(recipe, state, now)
                return
            }
        if (current.isClosed() && (state.status.startsHistoryEntry() || state.status.isImmediateHistoryEnd())) {
            recordHistoryStart(recipe, state, now)
            return
        }
        val nextStepCount = state.stepCount.takeIf { it > 0 } ?: recipe.steps.size
        val reportTargetNeedsSnapshot = state.shellReportText.orEmpty().isNotBlank() &&
            recipe.shellReportTargetStepIndex(state.currentStepIndex)?.let { targetIndex ->
                current.steps.firstOrNull { it.index == targetIndex }?.reportText.isNullOrBlank()
            } == true
        if (!state.status.endsHistoryEntry() &&
            current.status == state.status &&
            current.currentStepIndex == state.currentStepIndex &&
            current.stepCount == nextStepCount &&
            state.nextActionUrl.isNullOrBlank() &&
            state.terminalSessionId.isNullOrBlank() &&
            state.lastError.isNullOrBlank() &&
            !reportTargetNeedsSnapshot
        ) {
            return
        }
        val nextSteps = recipe.toHistorySteps(
            state = state,
            previousSteps = current.steps,
            fallbackReportText = current.shellReportText
        )
        val next = current.copy(
            recipeName = recipe.name,
            ownerKind = state.ownerKind,
            status = state.status,
            currentStepIndex = state.currentStepIndex,
            stepCount = nextStepCount,
            endedAt = if (state.status.endsHistoryEntry()) now else current.endedAt,
            updatedAt = now,
            summary = state.lastMeaningfulOutput.orEmpty().take(MAX_HISTORY_SUMMARY_CHARS),
            error = state.lastError.orEmpty().take(MAX_HISTORY_SUMMARY_CHARS),
            shellReportText = state.shellReportText.orEmpty()
                .ifBlank { current.shellReportText }
                .takeLast(MAX_HISTORY_REPORT_CHARS),
            steps = nextSteps
        )
        if (!current.meaningfullyDiffersFrom(next)) return
        val index = entries.indexOfFirst { it.historyId == current.historyId }
        if (index >= 0) entries[index] = next else entries.add(0, next)
        trimHistory(recipe.id)
        schedulePersistHistory()
    }

    private fun KiteRecipe.toHistorySteps(
        state: CardRunState,
        previousSteps: List<CardRunHistoryStep> = emptyList(),
        fallbackReportText: String = ""
    ): List<CardRunHistoryStep> {
        val previousReportsByIndex = previousSteps.associateBy { it.index }
        val shellReport = state.shellReportText.orEmpty().takeLast(MAX_HISTORY_REPORT_CHARS)
        val reportTargetIndex = shellReport
            .takeIf { it.isNotBlank() }
            ?.let { shellReportTargetStepIndex(state.currentStepIndex) }
        val rows = steps.mapIndexed { index, step ->
            val previousReport = previousReportsByIndex[index]?.reportText.orEmpty()
            val reportText = if (index == reportTargetIndex) {
                shellReport.ifBlank { previousReport }
            } else {
                previousReport
            }.takeLast(MAX_HISTORY_REPORT_CHARS)
            step.toHistoryStep(
                index = index,
                resolvedUrl = if (index == state.currentStepIndex) state.nextActionUrl else null,
                defaultUrl = defaultUrl,
                reportText = reportText
            )
        }.toMutableList()
        val nextUrl = state.nextActionUrl?.takeIf { it.isNotBlank() }
        if (nextUrl != null && rows.none { it.type == KiteRecipe.STEP_OPEN_WEB && it.detail == nextUrl }) {
            rows.add(
                CardRunHistoryStep(
                    index = rows.size,
                    type = KiteRecipe.STEP_OPEN_WEB,
                    label = "网页",
                    detail = nextUrl.take(MAX_HISTORY_DETAIL_CHARS)
                )
            )
        }
        return rows.attachLegacyReportIfNeeded(fallbackReportText, state.currentStepIndex)
    }

    private fun KiteRecipe.shellReportTargetStepIndex(currentStepIndex: Int): Int? {
        if (steps.isEmpty()) return null
        val cappedIndex = when {
            currentStepIndex < 0 -> 0
            currentStepIndex > steps.lastIndex -> steps.lastIndex
            else -> currentStepIndex
        }
        return (cappedIndex downTo 0).firstOrNull { steps[it].type == KiteRecipe.STEP_SHELL }
    }

    private fun com.kite.app.recipe.KiteRecipeStep.toHistoryStep(
        index: Int,
        resolvedUrl: String?,
        defaultUrl: String,
        reportText: String
    ): CardRunHistoryStep {
        val detail = when (type) {
            KiteRecipe.STEP_SHELL -> cmd ?: text
            KiteRecipe.STEP_TERMINAL -> text ?: cmd
            KiteRecipe.STEP_OPEN_WEB -> resolvedUrl ?: url ?: defaultUrl
            KiteRecipe.STEP_ANDROID_ACTION -> action
            else -> cmd ?: text ?: url ?: action
        }.orEmpty().lineSequence()
            .map { it.trim() }
            .firstOrNull { it.isNotBlank() }
            .orEmpty()
            .replace(Regex("\\s+"), " ")
            .take(MAX_HISTORY_DETAIL_CHARS)
        return CardRunHistoryStep(
            index = index,
            type = type,
            label = when (type) {
                KiteRecipe.STEP_SHELL -> "SH"
                KiteRecipe.STEP_TERMINAL -> "终端"
                KiteRecipe.STEP_OPEN_WEB -> "网页"
                KiteRecipe.STEP_ANDROID_ACTION -> "本机"
                else -> type.ifBlank { "步骤" }
            },
            detail = detail,
            reportText = reportText.takeLast(MAX_HISTORY_REPORT_CHARS)
        )
    }

    private fun List<CardRunHistoryStep>.attachLegacyReportIfNeeded(
        reportText: String,
        currentStepIndex: Int
    ): List<CardRunHistoryStep> {
        val report = reportText.takeLast(MAX_HISTORY_REPORT_CHARS).takeIf { it.isNotBlank() } ?: return this
        if (any { it.reportText.isNotBlank() }) return this
        val cappedPosition = when {
            isEmpty() -> return this
            currentStepIndex < 0 -> 0
            currentStepIndex > lastIndex -> lastIndex
            else -> currentStepIndex
        }
        val targetPosition = (cappedPosition downTo 0).firstOrNull { getOrNull(it)?.type == KiteRecipe.STEP_SHELL }
            ?: indexOfLast { it.type == KiteRecipe.STEP_SHELL }
        if (targetPosition < 0) return this
        return mapIndexed { position, step ->
            if (position == targetPosition) step.copy(reportText = report) else step
        }
    }

    private fun CardRunHistoryEntry.meaningfullyDiffersFrom(next: CardRunHistoryEntry): Boolean =
        status != next.status ||
            currentStepIndex != next.currentStepIndex ||
            stepCount != next.stepCount ||
            endedAt != next.endedAt ||
            error != next.error ||
            shellReportText != next.shellReportText && (next.status.endsHistoryEntry() || currentStepIndex != next.currentStepIndex) ||
            steps != next.steps ||
            ((next.status.endsHistoryEntry() || next.error.isNotBlank()) && summary != next.summary)

    private fun trimAllHistory() {
        historiesByRecipe.keys.toList().forEach { trimHistory(it) }
    }

    private fun trimHistory(recipeId: String) {
        val entries = historiesByRecipe[recipeId] ?: return
        val trimmed = entries.sortedByDescending { it.startedAt }.take(MAX_HISTORY_PER_RECIPE)
        entries.clear()
        entries.addAll(trimmed)
    }

    private fun CardRunStatus.startsHistoryEntry(): Boolean =
        this == CardRunStatus.Starting ||
            this == CardRunStatus.Running ||
            this == CardRunStatus.WaitingTerminal ||
            this == CardRunStatus.AlreadyRunning ||
            this == CardRunStatus.Opened

    private fun CardRunStatus.endsHistoryEntry(): Boolean =
        this == CardRunStatus.Completed ||
            this == CardRunStatus.Failed ||
            this == CardRunStatus.Stopped ||
            this == CardRunStatus.BridgeUnavailable ||
            this == CardRunStatus.Unknown

    private fun CardRunStatus.isImmediateHistoryEnd(): Boolean =
        this == CardRunStatus.Failed ||
            this == CardRunStatus.BridgeUnavailable ||
            this == CardRunStatus.Stopped

    private fun CardRunHistoryEntry.normalizedHistoryAfterProcessRestore(): CardRunHistoryEntry =
        if (!status.shouldResetAfterProcessRestore() || endedAt != null) {
            this
        } else {
            copy(
                status = CardRunStatus.Stopped,
                endedAt = updatedAt,
                summary = summary.ifBlank { "Kite 重新启动，已重置上次未完成状态" }
            )
        }

    private fun CardRunState.toJson(): JSONObject =
        JSONObject()
            .put("instanceId", instanceId)
            .put("recipeId", recipeId)
            .put("recipeName", recipeName)
            .put("parentInstanceId", parentInstanceId.orEmpty())
            .put("ownerKind", ownerKind)
            .put("stepId", stepId.orEmpty())
            .put("status", status.name)
            .put("surface", surface.name)
            .put("currentStepIndex", currentStepIndex)
            .put("stepCount", stepCount)
            .put("runId", runId.orEmpty())
            .put("terminalSessionId", terminalSessionId.orEmpty())
            .put("pid", pid.orEmpty())
            .put("rootPid", rootPid.orEmpty())
            .put("processGroupId", processGroupId.orEmpty())
            .put("systemSessionId", systemSessionId.orEmpty())
            .put("lastMeaningfulOutput", lastMeaningfulOutput.orEmpty().take(MAX_STORED_TEXT_CHARS))
            .put("lastError", lastError.orEmpty().take(MAX_STORED_TEXT_CHARS))
            .put("shellReportText", shellReportText.orEmpty().takeLast(MAX_STORED_TEXT_CHARS))
            .put("nextActionUrl", nextActionUrl.orEmpty())
            .put("createdAt", createdAt)
            .put("updatedAt", updatedAt)

    private fun CardRunHistoryEntry.toJson(): JSONObject =
        JSONObject()
            .put("historyId", historyId)
            .put("recipeId", recipeId)
            .put("recipeName", recipeName)
            .put("instanceId", instanceId)
            .put("ownerKind", ownerKind)
            .put("status", status.name)
            .put("currentStepIndex", currentStepIndex)
            .put("stepCount", stepCount)
            .put("startedAt", startedAt)
            .put("endedAt", endedAt ?: 0L)
            .put("updatedAt", updatedAt)
            .put("summary", summary.take(MAX_HISTORY_SUMMARY_CHARS))
            .put("error", error.take(MAX_HISTORY_SUMMARY_CHARS))
            .put("shellReportText", shellReportText.takeLast(MAX_HISTORY_REPORT_CHARS))
            .put("steps", JSONArray().apply { steps.forEach { put(it.toJson()) } })

    private fun CardRunHistoryStep.toJson(): JSONObject =
        JSONObject()
            .put("index", index)
            .put("type", type)
            .put("label", label)
            .put("detail", detail.take(MAX_HISTORY_DETAIL_CHARS))
            .put("reportText", reportText.takeLast(MAX_HISTORY_REPORT_CHARS))

    private fun JSONObject.toCardRunStateOrNull(): CardRunState? {
        val instanceId = optString("instanceId").takeIf { it.isNotBlank() } ?: return null
        val recipeId = optString("recipeId").takeIf { it.isNotBlank() } ?: return null
        val status = enumValueOrDefault(optString("status"), CardRunStatus.Unknown)
        val surface = enumValueOrDefault(optString("surface"), CardRunSurface.Summary)
        val updatedAt = optLong("updatedAt").takeIf { it > 0L } ?: System.currentTimeMillis()
        val createdAt = optLong("createdAt").takeIf { it > 0L } ?: updatedAt
        return CardRunState(
            instanceId = instanceId,
            recipeId = recipeId,
            recipeName = optString("recipeName"),
            parentInstanceId = optString("parentInstanceId").takeIf { it.isNotBlank() },
            ownerKind = optString("ownerKind").ifBlank { CardRunState.OWNER_KIND_CARD },
            stepId = optString("stepId").takeIf { it.isNotBlank() },
            status = status,
            surface = surface,
            currentStepIndex = optInt("currentStepIndex", -1),
            stepCount = optInt("stepCount", 0),
            runId = optString("runId").takeIf { it.isNotBlank() },
            terminalSessionId = optString("terminalSessionId").takeIf { it.isNotBlank() },
            pid = optString("pid").takeIf { it.isNotBlank() },
            rootPid = optString("rootPid").takeIf { it.isNotBlank() },
            processGroupId = optString("processGroupId").takeIf { it.isNotBlank() },
            systemSessionId = optString("systemSessionId").takeIf { it.isNotBlank() },
            lastMeaningfulOutput = optString("lastMeaningfulOutput").takeIf { it.isNotBlank() },
            lastError = optString("lastError").takeIf { it.isNotBlank() },
            shellReportText = optString("shellReportText").takeIf { it.isNotBlank() },
            nextActionUrl = optString("nextActionUrl").takeIf { it.isNotBlank() },
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

    private fun JSONObject.toCardRunHistoryEntryOrNull(): CardRunHistoryEntry? {
        val recipeId = optString("recipeId").takeIf { it.isNotBlank() } ?: return null
        val startedAt = optLong("startedAt").takeIf { it > 0L } ?: return null
        val instanceId = optString("instanceId").ifBlank { recipeId }
        val historyId = optString("historyId").ifBlank { "$instanceId@$startedAt" }
        val status = enumValueOrDefault(optString("status"), CardRunStatus.Unknown)
        val stepsJson = optJSONArray("steps") ?: JSONArray()
        val legacyReportText = optString("shellReportText").takeLast(MAX_HISTORY_REPORT_CHARS)
        val steps = buildList {
            for (index in 0 until stepsJson.length()) {
                stepsJson.optJSONObject(index)?.toCardRunHistoryStepOrNull()?.let { add(it) }
            }
        }.attachLegacyReportIfNeeded(legacyReportText, optInt("currentStepIndex", -1))
        return CardRunHistoryEntry(
            historyId = historyId,
            recipeId = recipeId,
            recipeName = optString("recipeName"),
            instanceId = instanceId,
            ownerKind = optString("ownerKind").ifBlank { CardRunState.OWNER_KIND_CARD },
            status = status,
            currentStepIndex = optInt("currentStepIndex", -1),
            stepCount = optInt("stepCount", steps.size),
            startedAt = startedAt,
            endedAt = optLong("endedAt").takeIf { it > 0L },
            updatedAt = optLong("updatedAt").takeIf { it > 0L } ?: startedAt,
            summary = optString("summary").take(MAX_HISTORY_SUMMARY_CHARS),
            error = optString("error").take(MAX_HISTORY_SUMMARY_CHARS),
            shellReportText = legacyReportText,
            steps = steps
        )
    }

    private fun JSONObject.toCardRunHistoryStepOrNull(): CardRunHistoryStep? =
        CardRunHistoryStep(
            index = optInt("index", 0),
            type = optString("type"),
            label = optString("label").ifBlank { optString("type").ifBlank { "步骤" } },
            detail = optString("detail").take(MAX_HISTORY_DETAIL_CHARS),
            reportText = optString("reportText").takeLast(MAX_HISTORY_REPORT_CHARS)
        )

    private inline fun <reified T : Enum<T>> enumValueOrDefault(name: String, default: T): T =
        runCatching { enumValueOf<T>(name) }.getOrDefault(default)

    private const val PREFS_NAME = "kite_card_run_store"
    private const val KEY_RUNS = "runs_v1"
    private const val KEY_HISTORY = "history_v1"
    private const val MAX_STORED_RUNS = 80
    private const val MAX_STORED_TEXT_CHARS = 4000
    private const val MAX_HISTORY_PER_RECIPE = 5
    private const val MAX_STORED_HISTORY_ENTRIES = 200
    private const val MAX_HISTORY_DETAIL_CHARS = 260
    private const val MAX_HISTORY_SUMMARY_CHARS = 500
    private const val MAX_HISTORY_REPORT_CHARS = 4000
    private const val PERSIST_DEBOUNCE_MS = 300L

}
