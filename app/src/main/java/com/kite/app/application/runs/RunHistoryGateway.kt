package com.kite.app.application.runs

import com.kite.app.run.CardRunHistoryEntry
import kotlinx.coroutines.flow.Flow

/** 运行历史只读边界；Feature 不直接访问具体运行 Store。 */
interface RunHistoryGateway {
    val changes: Flow<Unit>

    fun historyForRecipe(recipeId: String): List<CardRunHistoryEntry>
}

interface RunHistoryDependenciesOwner {
    val runHistoryGateway: RunHistoryGateway
}
