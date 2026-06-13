package com.kite.app.run

import android.content.Context
import android.content.SharedPreferences
import com.kite.app.recipe.KiteRecipe
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject

object CardRunStore {
    private val _runs = MutableStateFlow<List<CardRunState>>(emptyList())
    private val registeredRecipes = linkedMapOf<String, KiteRecipe>()
    private var prefs: SharedPreferences? = null
    private var initialized = false
    val runs: StateFlow<List<CardRunState>> = _runs

    @Synchronized
    fun initialize(context: Context) {
        if (initialized) return
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _runs.value = loadPersistedRuns()
        initialized = true
    }

    @Synchronized
    fun registerRecipe(recipe: KiteRecipe) {
        registeredRecipes[recipe.id] = recipe
    }

    @Synchronized
    fun registeredRecipe(recipeId: String): KiteRecipe? =
        registeredRecipes[recipeId]

    @Synchronized
    fun start(recipe: KiteRecipe, instanceId: String = recipe.id): CardRunState {
        registerRecipe(recipe)
        val now = System.currentTimeMillis()
        val run = CardRunState(
            instanceId = instanceId,
            recipeId = recipe.id,
            recipeName = recipe.name,
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
        surface: CardRunSurface? = null,
        currentStepIndex: Int? = null,
        runId: String? = null,
        terminalSessionId: String? = null,
        pid: String? = null,
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
            status = status,
            surface = resolvedSurface,
            currentStepIndex = currentStepIndex ?: existing.currentStepIndex,
            stepCount = recipe.steps.size,
            runId = if (clearRunBinding) null else runId ?: existing.runId,
            terminalSessionId = if (clearRunBinding || clearTerminalSession) null else terminalSessionId ?: existing.terminalSessionId,
            pid = if (clearRunBinding) null else pid ?: existing.pid,
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
        _runs.value.firstOrNull { it.instanceId == instanceId }

    @Synchronized
    fun snapshot(): List<CardRunState> = _runs.value

    @Synchronized
    private fun upsert(run: CardRunState) {
        val current = _runs.value.associateBy { it.instanceId }.toMutableMap()
        current[run.instanceId] = run
        _runs.value = current.values.sortedByDescending { it.updatedAt }
        persistRuns()
    }

    private fun persistRuns() {
        val store = prefs ?: return
        val payload = JSONArray()
        _runs.value
            .sortedByDescending { it.updatedAt }
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
        }.getOrDefault(emptyList())
    }

    private fun CardRunState.toJson(): JSONObject =
        JSONObject()
            .put("instanceId", instanceId)
            .put("recipeId", recipeId)
            .put("recipeName", recipeName)
            .put("status", status.name)
            .put("surface", surface.name)
            .put("currentStepIndex", currentStepIndex)
            .put("stepCount", stepCount)
            .put("runId", runId.orEmpty())
            .put("terminalSessionId", terminalSessionId.orEmpty())
            .put("pid", pid.orEmpty())
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
            status = status,
            surface = surface,
            currentStepIndex = optInt("currentStepIndex", -1),
            stepCount = optInt("stepCount", 0),
            runId = optString("runId").takeIf { it.isNotBlank() },
            terminalSessionId = optString("terminalSessionId").takeIf { it.isNotBlank() },
            pid = optString("pid").takeIf { it.isNotBlank() },
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

}
