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
        parentProjectCwd: String? = null,
    ): Set<AgentArchivedSelectionKey> = current.toMutableSet().apply {
        val key = AgentArchivedSelectionKey.Session(sessionId)
        if (!add(key)) {
            remove(key)
            parentProjectCwd?.let { remove(AgentArchivedSelectionKey.Project(it)) }
        }
    }

    fun toggleProject(
        current: Set<AgentArchivedSelectionKey>,
        projectCwd: String,
        childSessionIds: Collection<String>,
    ): Set<AgentArchivedSelectionKey> = current.toMutableSet().apply {
        val projectKey = AgentArchivedSelectionKey.Project(projectCwd)
        val childKeys = childSessionIds.map(AgentArchivedSelectionKey::Session)
        if (projectKey in this) {
            remove(projectKey)
            removeAll(childKeys.toSet())
        } else {
            add(projectKey)
            addAll(childKeys)
        }
    }

    fun selectAll(
        sessionIds: Collection<String>,
        projectCwds: Collection<String>,
    ): Set<AgentArchivedSelectionKey> = buildSet {
        sessionIds.mapTo(this, AgentArchivedSelectionKey::Session)
        projectCwds.mapTo(this, AgentArchivedSelectionKey::Project)
    }

    fun selectedSessionIds(selected: Set<AgentArchivedSelectionKey>): Set<String> = selected
        .filterIsInstance<AgentArchivedSelectionKey.Session>()
        .mapTo(linkedSetOf(), AgentArchivedSelectionKey.Session::sessionId)

    fun selectedProjectCwds(selected: Set<AgentArchivedSelectionKey>): Set<String> = selected
        .filterIsInstance<AgentArchivedSelectionKey.Project>()
        .mapTo(linkedSetOf(), AgentArchivedSelectionKey.Project::cwd)

    fun canDelete(
        selectedIds: Set<String>,
        currentSessionId: String?,
        deleteSupported: Boolean
    ): Boolean = deleteSupported && selectedIds.isNotEmpty() && currentSessionId !in selectedIds
}

internal sealed interface AgentArchivedSelectionKey {
    data class Session(val sessionId: String) : AgentArchivedSelectionKey
    data class Project(val cwd: String) : AgentArchivedSelectionKey
}
