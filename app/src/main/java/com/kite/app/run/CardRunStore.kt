package com.kite.app.run

import com.kite.app.recipe.KiteRecipe
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID

object CardRunStore {
    private val _runs = MutableStateFlow<List<CardRunState>>(emptyList())
    private val registeredRecipes = linkedMapOf<String, KiteRecipe>()
    val runs: StateFlow<List<CardRunState>> = _runs

    @Synchronized
    fun registerRecipe(recipe: KiteRecipe) {
        registeredRecipes[recipe.id] = recipe
    }

    @Synchronized
    fun registeredRecipe(recipeId: String): KiteRecipe? =
        registeredRecipes[recipeId]

    @Synchronized
    fun start(recipe: KiteRecipe, instanceId: String = newInstanceId(recipe.id)): CardRunState {
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
        nextActionUrl: String? = null
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
            runId = runId ?: existing.runId,
            terminalSessionId = terminalSessionId ?: existing.terminalSessionId,
            pid = pid ?: existing.pid,
            lastMeaningfulOutput = lastMeaningfulOutput ?: existing.lastMeaningfulOutput,
            lastError = lastError,
            shellReportText = shellReportText ?: existing.shellReportText,
            nextActionUrl = nextActionUrl ?: existing.nextActionUrl,
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
    }

    private fun newInstanceId(recipeId: String): String =
        "run_${recipeId}_${UUID.randomUUID().toString().replace("-", "")}"
}
