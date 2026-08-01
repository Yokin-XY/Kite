package com.kite.app.feature.runsurface

import com.kite.app.agent.contract.AgentSessionSummary
import com.kite.app.theme.ThemeTokens

internal object AgentSurfaceThemePolicy {
    fun project(source: ThemeTokens, isDark: Boolean): ThemeTokens = if (isDark) {
        source.copy(
            pageBackground = android.graphics.Color.BLACK,
            surface = android.graphics.Color.rgb(32, 32, 32),
            surfaceElevated = android.graphics.Color.rgb(38, 38, 38),
            cardBackground = android.graphics.Color.rgb(36, 36, 36),
            inputBackground = android.graphics.Color.rgb(32, 32, 32),
            border = android.graphics.Color.rgb(55, 55, 55),
            borderStrong = android.graphics.Color.rgb(74, 74, 74),
            textPrimary = android.graphics.Color.rgb(245, 245, 245),
            textSecondary = android.graphics.Color.rgb(178, 178, 178),
            textTertiary = android.graphics.Color.rgb(122, 122, 122)
        )
    } else {
        source.copy(
            pageBackground = android.graphics.Color.WHITE,
            surface = android.graphics.Color.WHITE,
            surfaceElevated = android.graphics.Color.WHITE,
            cardBackground = android.graphics.Color.rgb(247, 247, 247),
            inputBackground = android.graphics.Color.WHITE,
            border = android.graphics.Color.rgb(232, 232, 232),
            borderStrong = android.graphics.Color.rgb(209, 209, 209),
            textPrimary = android.graphics.Color.rgb(17, 17, 17),
            textSecondary = android.graphics.Color.rgb(102, 102, 102),
            textTertiary = android.graphics.Color.rgb(150, 150, 150)
        )
    }
}

internal object AgentArchivedSelectionPolicy {
    fun toggle(current: Set<String>, sessionId: String): Set<String> = current.toMutableSet().apply {
        if (!add(sessionId)) remove(sessionId)
    }

    fun selectAll(sessions: List<AgentSessionSummary>): Set<String> = sessions
        .mapTo(linkedSetOf(), AgentSessionSummary::id)

    fun canDelete(
        selectedIds: Set<String>,
        currentSessionId: String?,
        deleteSupported: Boolean
    ): Boolean = deleteSupported && selectedIds.isNotEmpty() && currentSessionId !in selectedIds
}
