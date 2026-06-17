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
    private val persistExecutor = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "KiteCardRunStorePersist").apply { isDaemon = true }
    }
    private val persistScheduleLock = Any()
    private var prefs: SharedPreferences? = null
    private var initialized = false
    private var persistScheduled = false
    val runs: StateFlow<List<CardRunState>> = _runs

    @Synchronized
    fun initialize(context: Context) {
        if (initialized) return
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val loaded = loadPersistedRuns()
        val normalized = loaded.map { it.normalizedAfterProcessRestore() }
        runsByInstance.clear()
        normalized.forEach { runsByInstance[it.instanceId] = it }
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
        val existing = instanceId
            ?.takeIf { it.isNotBlank() }
            ?.let { get(it) ?: start(recipe, it) }
            ?: currentForRecipe(recipe.id)
            ?: start(recipe)
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
            updatedAt = System.currentTimeMillis()
        )
        upsert(next)
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
    fun removeRecipes(recipeIds: Collection<String>) {
        val ids = recipeIds
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toSet()
        if (ids.isEmpty()) return
        val beforeSize = runsByInstance.size
        runsByInstance.entries.removeAll { (_, run) -> run.recipeId in ids || run.instanceId in ids }
        if (runsByInstance.size == beforeSize) return
        _runs.value = sortedRuns()
        schedulePersistRuns()
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

    private inline fun <reified T : Enum<T>> enumValueOrDefault(name: String, default: T): T =
        runCatching { enumValueOf<T>(name) }.getOrDefault(default)

    private const val PREFS_NAME = "kite_card_run_store"
    private const val KEY_RUNS = "runs_v1"
    private const val MAX_STORED_RUNS = 80
    private const val MAX_STORED_TEXT_CHARS = 4000
    private const val PERSIST_DEBOUNCE_MS = 300L

}
