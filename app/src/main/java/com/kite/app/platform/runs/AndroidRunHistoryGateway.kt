package com.kite.app.platform.runs

import com.kite.app.application.runs.RunHistoryGateway
import com.kite.app.run.CardRunHistoryEntry
import com.kite.app.run.CardRunStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map

internal class AndroidRunHistoryGateway : RunHistoryGateway {
    override val changes: Flow<Unit> = CardRunStore.runs.drop(1).map { Unit }

    override fun historyForRecipe(recipeId: String): List<CardRunHistoryEntry> =
        CardRunStore.historyForRecipe(recipeId.trim())
}
