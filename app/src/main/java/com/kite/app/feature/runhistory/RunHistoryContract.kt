package com.kite.app.feature.runhistory

import com.kite.app.application.runs.RunHistoryGateway
import com.kite.app.run.CardRunHistoryEntry
import com.kite.app.run.CardRunHistoryStep
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal enum class RunHistoryPage {
    List,
    Detail,
    Report
}

internal data class RunHistoryUiState(
    val recipeId: String,
    val entries: List<CardRunHistoryEntry> = emptyList(),
    val page: RunHistoryPage = RunHistoryPage.List,
    val selectedHistoryId: String? = null,
    val selectedStepIndex: Int? = null
) {
    val selectedEntry: CardRunHistoryEntry?
        get() = entries.firstOrNull { it.historyId == selectedHistoryId }

    val selectedStep: CardRunHistoryStep?
        get() = selectedEntry?.steps?.firstOrNull { it.index == selectedStepIndex }
}

internal class RunHistoryController(
    private val recipeId: String,
    private val initialHistoryId: String?,
    private val gateway: RunHistoryGateway
) {
    private val mutableState = MutableStateFlow(RunHistoryUiState(recipeId = recipeId))
    private var initialSelectionApplied = false
    val state: StateFlow<RunHistoryUiState> = mutableState.asStateFlow()

    fun refresh() {
        val entries = gateway.historyForRecipe(recipeId)
        val current = mutableState.value
        val selectedId = current.selectedHistoryId ?: initialHistoryId.takeUnless { initialSelectionApplied }
        initialSelectionApplied = true
        val selected = entries.firstOrNull { it.historyId == selectedId }
        mutableState.value = current.copy(
            entries = entries,
            page = when {
                selected == null -> RunHistoryPage.List
                current.page == RunHistoryPage.Report &&
                    selected.steps.any { it.index == current.selectedStepIndex } -> RunHistoryPage.Report
                else -> RunHistoryPage.Detail
            },
            selectedHistoryId = selected?.historyId,
            selectedStepIndex = current.selectedStepIndex
                ?.takeIf { index -> selected?.steps?.any { it.index == index } == true }
        )
    }

    fun openEntry(historyId: String) {
        val entry = mutableState.value.entries.firstOrNull { it.historyId == historyId } ?: return
        mutableState.value = mutableState.value.copy(
            page = RunHistoryPage.Detail,
            selectedHistoryId = entry.historyId,
            selectedStepIndex = null
        )
    }

    fun openReport(stepIndex: Int) {
        val state = mutableState.value
        val step = state.selectedEntry?.steps?.firstOrNull { it.index == stepIndex } ?: return
        if (step.reportText.isBlank()) return
        mutableState.value = state.copy(
            page = RunHistoryPage.Report,
            selectedStepIndex = step.index
        )
    }

    /** true 表示 Feature 已消费返回；false 表示应交给 Shell。 */
    fun back(): Boolean {
        val current = mutableState.value
        mutableState.value = when (current.page) {
            RunHistoryPage.Report -> current.copy(page = RunHistoryPage.Detail, selectedStepIndex = null)
            RunHistoryPage.Detail -> current.copy(
                page = RunHistoryPage.List,
                selectedHistoryId = null,
                selectedStepIndex = null
            )
            RunHistoryPage.List -> return false
        }
        return true
    }
}
