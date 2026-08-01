package com.kite.app.feature.runsurface

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
    fun toggleSession(
        current: Set<AgentArchivedSelectionKey>,
        sessionId: String,
    ): Set<AgentArchivedSelectionKey> = current.toMutableSet().apply {
        val key = AgentArchivedSelectionKey.Session(sessionId)
        if (!add(key)) remove(key)
    }

    fun toggleProject(
        current: Set<AgentArchivedSelectionKey>,
        childSessionIds: Collection<String>,
    ): Set<AgentArchivedSelectionKey> = current.toMutableSet().apply {
        val childKeys = childSessionIds.mapTo(linkedSetOf(), AgentArchivedSelectionKey::Session)
        if (childKeys.isNotEmpty() && containsAll(childKeys)) {
            removeAll(childKeys.toSet())
        } else {
            addAll(childKeys)
        }
    }

    fun selectAll(sessionIds: Collection<String>): Set<AgentArchivedSelectionKey> = buildSet {
        sessionIds.mapTo(this, AgentArchivedSelectionKey::Session)
    }

    fun projectSelectionState(
        selected: Set<AgentArchivedSelectionKey>,
        childSessionIds: Collection<String>,
    ): AgentArchivedProjectSelectionState {
        val childKeys = childSessionIds.mapTo(linkedSetOf(), AgentArchivedSelectionKey::Session)
        val selectedCount = childKeys.count(selected::contains)
        return when {
            selectedCount == 0 -> AgentArchivedProjectSelectionState.Unchecked
            selectedCount == childKeys.size -> AgentArchivedProjectSelectionState.Checked
            else -> AgentArchivedProjectSelectionState.Partial
        }
    }

    fun selectedSessionIds(selected: Set<AgentArchivedSelectionKey>): Set<String> = selected
        .filterIsInstance<AgentArchivedSelectionKey.Session>()
        .mapTo(linkedSetOf(), AgentArchivedSelectionKey.Session::sessionId)

    fun canDelete(
        selectedIds: Set<String>,
        currentSessionId: String?,
        deleteSupported: Boolean
    ): Boolean = deleteSupported && selectedIds.isNotEmpty() && currentSessionId !in selectedIds
}

internal object AgentSelectionVisualPolicy {
    const val INDICATOR_SIZE_DP = 18
    const val TOUCH_TARGET_DP = 44
    const val CHECKED_DOT_SIZE_DP = 10
    const val PARTIAL_DOT_SIZE_DP = 6
    const val ACTION_HEIGHT_DP = 48
    const val ACTION_RADIUS_DP = 16

    fun palette(isDark: Boolean): AgentSelectionPalette = if (isDark) {
        AgentSelectionPalette(
            selectedIndicator = android.graphics.Color.rgb(236, 236, 236),
            indicatorSurface = android.graphics.Color.rgb(47, 47, 47),
            unselectedIndicatorStroke = android.graphics.Color.rgb(94, 94, 94),
            selectedRow = android.graphics.Color.rgb(47, 47, 47),
            primaryAction = android.graphics.Color.rgb(236, 236, 236),
            primaryActionText = android.graphics.Color.rgb(20, 20, 20),
            dangerAction = android.graphics.Color.rgb(72, 38, 40),
            dangerActionText = android.graphics.Color.rgb(255, 128, 135),
            disabledAction = android.graphics.Color.rgb(42, 42, 42),
            disabledActionText = android.graphics.Color.rgb(112, 112, 112),
        )
    } else {
        AgentSelectionPalette(
            selectedIndicator = android.graphics.Color.rgb(32, 33, 35),
            indicatorSurface = android.graphics.Color.WHITE,
            unselectedIndicatorStroke = android.graphics.Color.rgb(199, 199, 199),
            selectedRow = android.graphics.Color.rgb(247, 247, 248),
            primaryAction = android.graphics.Color.rgb(32, 33, 35),
            primaryActionText = android.graphics.Color.WHITE,
            dangerAction = android.graphics.Color.rgb(252, 235, 235),
            dangerActionText = android.graphics.Color.rgb(207, 30, 39),
            disabledAction = android.graphics.Color.rgb(244, 244, 244),
            disabledActionText = android.graphics.Color.rgb(166, 166, 166),
        )
    }
}

internal data class AgentSelectionPalette(
    val selectedIndicator: Int,
    val indicatorSurface: Int,
    val unselectedIndicatorStroke: Int,
    val selectedRow: Int,
    val primaryAction: Int,
    val primaryActionText: Int,
    val dangerAction: Int,
    val dangerActionText: Int,
    val disabledAction: Int,
    val disabledActionText: Int,
)

internal sealed interface AgentArchivedSelectionKey {
    data class Session(val sessionId: String) : AgentArchivedSelectionKey
}

internal enum class AgentArchivedProjectSelectionState {
    Unchecked,
    Partial,
    Checked,
}
