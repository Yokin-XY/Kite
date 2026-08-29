package com.kite.app.feature.runsurface

import com.kite.app.agent.contract.AgentConfigCategory
import com.kite.app.agent.contract.AgentConfigChoice
import com.kite.app.agent.contract.AgentConfigOption
import com.kite.app.agent.contract.AgentSessionSummary
import com.kite.app.agent.store.AgentArchivedSessionMetadata
import com.kite.app.agent.store.AgentArchivedSessionSourceState
import com.kite.app.agent.store.AgentProject

/** 页面分组、筛选和会话配置导航的纯计算策略。 */
internal object AgentSurfaceNavigationPolicy {
    const val DELETED_ARCHIVE_GROUP_CWD = "kite://deleted-archived-sessions"
    const val UNCONFIRMED_ARCHIVE_GROUP_CWD = "kite://unconfirmed-archived-sessions"

    fun sessionListFailureMessage(rawMessage: String): String {
        val normalized = rawMessage.trim().lowercase()
        return when {
            normalized.contains("authentication required") ||
                normalized.contains("unauthorized") ||
                normalized.contains("not authenticated") ||
                normalized.contains("login required") ->
                "需要先登录当前 Agent，请在右下角设置中完成登录"
            else -> "暂时无法读取会话，请稍后重试"
        }
    }

    const val MODEL_ENTRY_LABEL = "模型"
    const val PERMISSION_ENTRY_LABEL = "权限"
    val fixedComposerEntries: List<String> = listOf(MODEL_ENTRY_LABEL, PERMISSION_ENTRY_LABEL)

    fun normalizeCwd(cwd: String): String {
        val cleaned = cwd.trim().replace('\\', '/')
        if (cleaned == "/") return cleaned
        return cleaned.trimEnd('/')
    }

    fun sameCwd(left: String, right: String): Boolean = normalizeCwd(left) == normalizeCwd(right)

    /**
     * 进程内 Runtime 是当前会话身份的事实源；CardRun 持久化身份只用于进程重建后的恢复提示。
     * 这样旧连接迟到的状态更新无法把已打开页面重新指向旧会话。
     */
    fun resolveSessionBinding(
        persistedSessionId: String?,
        runtime: AgentSurfaceRuntimeSessionIdentity?,
    ): AgentSurfaceSessionBinding = if (runtime == null) {
        AgentSurfaceSessionBinding(
            nativeSessionId = persistedSessionId,
            conversationSessionId = persistedSessionId,
        )
    } else {
        AgentSurfaceSessionBinding(
            nativeSessionId = runtime.sessionId.takeUnless { runtime.isDraft },
            conversationSessionId = runtime.conversationSessionId,
        )
    }

    fun groupSessions(
        sessions: List<AgentSessionSummary>,
        defaultCwd: String,
        registeredProjects: List<AgentProject> = emptyList(),
        archivedProjectCwds: Set<String> = emptySet(),
    ): AgentSessionGrouping {
        val normalizedDefault = normalizeCwd(defaultCwd).ifBlank { "/workspace" }
        val normalizedArchivedCwds = archivedProjectCwds.mapTo(linkedSetOf(), ::normalizeCwd)
        val defaultSessions = mutableListOf<AgentSessionSummary>()
        val projects = linkedMapOf<String, ProjectBucket>()
        registeredProjects
            .sortedBy(AgentProject::createdAtMillis)
            .forEach { project ->
                val normalized = normalizeCwd(project.cwd)
                if (normalized.isNotBlank() && normalized != normalizedDefault &&
                    normalized !in normalizedArchivedCwds
                ) {
                    projects.putIfAbsent(normalized, ProjectBucket(project.name))
                }
            }
        sessions.forEach { session ->
            val normalized = normalizeCwd(session.cwd).ifBlank { normalizedDefault }
            if (normalized in normalizedArchivedCwds) return@forEach
            if (normalized == normalizedDefault) {
                defaultSessions += session
            } else {
                projects.getOrPut(normalized) { ProjectBucket(projectName(normalized)) }.sessions += session
            }
        }
        return AgentSessionGrouping(
            defaultCwd = normalizedDefault,
            defaultSessions = defaultSessions,
            projects = projects.map { (cwd, bucket) ->
                AgentSessionProjectGroup(cwd, bucket.name, bucket.sessions)
            },
        )
    }

    fun drawerRows(
        grouping: AgentSessionGrouping,
        expandedProjectCwds: Set<String>,
    ): List<AgentDrawerRow> = buildList {
        add(AgentDrawerRow.SectionHeader(
            title = "会话",
            actionDescription = "在默认目录新建会话",
            action = AgentDrawerAction.NewDraft(grouping.defaultCwd),
            key = "section:sessions",
        ))
        if (grouping.defaultSessions.isEmpty()) {
            add(AgentDrawerRow.Empty("还没有会话", "empty:sessions"))
        } else {
            grouping.defaultSessions.forEach { add(AgentDrawerRow.Session(it, inProject = false)) }
        }
        add(AgentDrawerRow.SectionHeader(
            title = "项目",
            actionDescription = "选择新的项目目录",
            action = AgentDrawerAction.ChooseProject,
            key = "section:projects",
        ))
        if (grouping.projects.isEmpty()) {
            add(AgentDrawerRow.Empty("还没有项目会话", "empty:projects"))
        } else {
            grouping.projects.forEach { project ->
                val expanded = normalizeCwd(project.cwd) in expandedProjectCwds
                add(AgentDrawerRow.ProjectHeader(project, expanded))
                if (expanded) {
                    project.sessions.forEach { add(AgentDrawerRow.Session(it, inProject = true)) }
                }
            }
        }
    }

    fun archivedRows(
        grouping: AgentSessionGrouping,
        expandedCwds: Set<String>,
        archivedProjects: List<AgentProject> = emptyList(),
    ): List<AgentArchivedRow> = buildList {
        val archivedProjectsByCwd = archivedProjects.associateBy { normalizeCwd(it.cwd) }
        val groups = buildList {
            if (grouping.defaultSessions.isNotEmpty()) {
                add(AgentSessionProjectGroup(grouping.defaultCwd, "会话", grouping.defaultSessions))
            }
            addAll(grouping.projects.filter { group ->
                group.sessions.isNotEmpty() || normalizeCwd(group.cwd) in archivedProjectsByCwd
            })
        }
        groups.forEach { group ->
            val normalizedCwd = normalizeCwd(group.cwd)
            val projectSessionIds = if (normalizedCwd == normalizeCwd(grouping.defaultCwd)) {
                emptySet()
            } else {
                group.sessions.mapTo(linkedSetOf(), AgentSessionSummary::id)
            }
            val expanded = normalizedCwd in expandedCwds
            add(AgentArchivedRow.GroupHeader(
                cwd = group.cwd,
                title = group.name,
                subtitle = group.sessions.size.takeIf { it > 0 }?.let { "$it 个归档会话" },
                count = group.sessions.size,
                expanded = expanded,
                archivedProject = archivedProjectsByCwd[normalizedCwd],
                selectableSessionIds = projectSessionIds,
            ))
            if (expanded) group.sessions.forEach { add(AgentArchivedRow.Session(it)) }
        }
    }

    fun archivedSessionProjection(
        sessions: List<AgentSessionSummary>,
        archivedSessionIds: Set<String>,
    ): AgentArchivedSessionProjection {
        val sessionsById = sessions.associateBy(AgentSessionSummary::id)
        return AgentArchivedSessionProjection(
            sessions = archivedSessionIds.mapNotNull(sessionsById::get),
            unavailableSessionIds = archivedSessionIds.filterNot(sessionsById::containsKey),
        )
    }

    fun unavailableArchivedRows(
        sessions: List<AgentArchivedSessionMetadata>,
        expandedCwds: Set<String>,
    ): List<AgentArchivedRow> = buildList {
        val deleted = sessions.filter { it.sourceState == AgentArchivedSessionSourceState.Deleted }
        val unconfirmed = sessions.filterNot { it.sourceState == AgentArchivedSessionSourceState.Deleted }
        fun addGroup(
            cwd: String,
            title: String,
            items: List<AgentArchivedSessionMetadata>,
        ) {
            if (items.isEmpty()) return
            val expanded = cwd in expandedCwds
            add(AgentArchivedRow.GroupHeader(
                cwd = cwd,
                title = title,
                subtitle = "${items.size} 个归档会话",
                count = items.size,
                expanded = expanded,
            ))
            if (expanded) items.forEach { add(AgentArchivedRow.UnavailableSession(it)) }
        }
        addGroup(DELETED_ARCHIVE_GROUP_CWD, "源会话已删除", deleted)
        addGroup(UNCONFIRMED_ARCHIVE_GROUP_CWD, "尚未确认", unconfirmed)
    }

    fun canDeleteUnavailableSessionNatively(
        targetProviderId: String,
        runtimeProviderId: String?,
        deleteSupported: Boolean,
        currentSessionId: String?,
        targetSessionId: String,
    ): Boolean =
        runtimeProviderId == targetProviderId &&
            deleteSupported &&
            currentSessionId != targetSessionId

    fun sessionMenuActions(
        renameSupported: Boolean,
        deleteSupported: Boolean,
        currentSessionId: String?,
        targetSessionId: String,
    ): List<AgentDrawerSessionMenuAction> = buildList {
        if (renameSupported) add(AgentDrawerSessionMenuAction.Rename)
        add(AgentDrawerSessionMenuAction.Archive)
        if (deleteSupported) {
            add(AgentDrawerSessionMenuAction.Delete(enabled = currentSessionId != targetSessionId))
        }
    }

    private fun projectName(cwd: String): String = normalizeCwd(cwd)
        .substringAfterLast('/')
        .takeIf(String::isNotBlank)
        ?: cwd

    private data class ProjectBucket(
        val name: String,
        val sessions: MutableList<AgentSessionSummary> = mutableListOf(),
    )

    /**
     * 推理强度只有在适配层完成统一语义映射且存在真实可切换项时才进入页面。
     * Toggle 自身已经明确表达二值能力；Select 则必须保留当前值并至少有两个已映射选项。
     */
    fun reasoningOption(options: List<AgentConfigOption>): AgentConfigOption? = options
        .firstOrNull { option ->
            if (option.category != AgentConfigCategory.ThoughtLevel) return@firstOrNull false
            when (option) {
                is AgentConfigOption.Toggle -> true
                is AgentConfigOption.Select -> option.choices.size >= 2 &&
                    option.choices.all { it.reasoning != null } &&
                    option.choices.any { it.value == option.currentValue }
            }
        }

    fun configurationSummary(options: List<AgentConfigOption>): String {
        val ordered = buildList {
            options.firstOrNull { it.category == AgentConfigCategory.Model }?.let(::add)
            reasoningOption(options)?.let(::add)
            if (isEmpty()) {
                options.firstOrNull {
                    it.category != AgentConfigCategory.Mode &&
                        it.category != AgentConfigCategory.Permission &&
                        it.category != AgentConfigCategory.ThoughtLevel
                }?.let(::add)
            }
        }.distinctBy(AgentConfigOption::id)
        return ordered.joinToString(" · ") { option ->
            when (option) {
                is AgentConfigOption.Select -> option.choices
                    .firstOrNull { it.value == option.currentValue }
                    ?.name
                    ?: option.currentValue
                is AgentConfigOption.Toggle -> if (option.currentValue) "开启" else "关闭"
            }
        }
    }

    fun composerModelLabel(options: List<AgentConfigOption>): String = options
        .filterIsInstance<AgentConfigOption.Select>()
        .firstOrNull { it.category == AgentConfigCategory.Model }
        ?.let { option ->
            option.choices.firstOrNull { it.value == option.currentValue }?.name ?: option.currentValue
        }
        ?.takeIf(String::isNotBlank)
        ?: MODEL_ENTRY_LABEL

    fun composerModelTextStyle(label: String): ComposerModelTextStyle {
        val characterCount = label.codePointCount(0, label.length)
        val textSize = when {
            characterCount <= 11 -> 13f
            characterCount <= 18 -> 12f
            else -> 11f
        }
        return ComposerModelTextStyle(textSizeSp = textSize, maximumWidthDp = 118)
    }

    fun composerPermissionLabel(option: AgentConfigOption.Select?): String = when (option) {
        null -> PERMISSION_ENTRY_LABEL
        else -> option.choices
            .firstOrNull { it.value == option.currentValue }
            ?.name
            ?.takeIf(String::isNotBlank)
            ?: "自定义"
    }

    fun permissionOption(options: List<AgentConfigOption>): AgentConfigOption.Select? = options
        .filterIsInstance<AgentConfigOption.Select>()
        .firstOrNull { it.category == AgentConfigCategory.Permission && it.choices.isNotEmpty() }

    fun sessionPanelMaxHeight(
        viewportHeight: Int,
        composerHeight: Int,
        topBarHeight: Int,
        preferredHeight: Int,
        minimumHeight: Int,
        outerSpacing: Int,
    ): Int {
        val available = viewportHeight - composerHeight - topBarHeight - outerSpacing
        return minOf(preferredHeight, available.coerceAtLeast(minimumHeight))
    }

    fun filterSessions(
        sessions: List<AgentSessionSummary>,
        query: String,
    ): List<AgentSessionSummary> {
        val normalized = query.trim().lowercase()
        if (normalized.isEmpty()) return sessions
        return sessions.filter { session ->
            sequenceOf(session.title, session.id, session.cwd)
                .filterNotNull()
                .any { value -> value.lowercase().contains(normalized) }
        }
    }

    fun modelChoiceGroups(option: AgentConfigOption.Select): List<AgentModelChoiceGroup> {
        if (option.category != AgentConfigCategory.Model) return emptyList()
        val grouped = linkedMapOf<String, MutableList<AgentConfigChoice>>()
        var hasExplicitGroup = false
        option.choices.forEach { choice ->
            val groupId = choice.groupId?.takeIf(String::isNotBlank)
            val groupName = choice.groupName?.takeIf(String::isNotBlank)
            if (groupId != null || groupName != null) hasExplicitGroup = true
            val key = groupId ?: groupName ?: UNGROUPED_CONFIGURATION_KEY
            grouped.getOrPut(key, ::mutableListOf) += choice
        }
        if (!hasExplicitGroup) return emptyList()
        return grouped.map { (id, choices) ->
            AgentModelChoiceGroup(
                id = id,
                name = choices.firstOrNull()?.groupName?.takeIf(String::isNotBlank) ?: "其他供应商",
                choices = choices,
            )
        }
    }

    fun resolveModelChoiceGroup(
        groups: List<AgentModelChoiceGroup>,
        currentModelValue: String?,
        requestedGroupId: String?,
    ): AgentModelChoiceGroup? = groups.firstOrNull { group ->
        group.id == requestedGroupId
    } ?: groups.firstOrNull { group ->
        group.choices.any { choice -> choice.value == currentModelValue }
    } ?: groups.firstOrNull()

    private const val UNGROUPED_CONFIGURATION_KEY = "__kite_ungrouped__"
}

internal data class AgentSurfaceRuntimeSessionIdentity(
    val sessionId: String?,
    val isDraft: Boolean,
    val conversationSessionId: String?,
)

internal data class AgentSurfaceSessionBinding(
    val nativeSessionId: String?,
    val conversationSessionId: String?,
)

internal data class AgentArchivedSessionProjection(
    val sessions: List<AgentSessionSummary>,
    val unavailableSessionIds: List<String>,
)

internal data class AgentModelChoiceGroup(
    val id: String,
    val name: String,
    val choices: List<AgentConfigChoice>,
)
