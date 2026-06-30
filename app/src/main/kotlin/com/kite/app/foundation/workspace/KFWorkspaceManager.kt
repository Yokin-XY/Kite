package com.kite.app.foundation.workspace

import android.content.Context
import com.kite.app.foundation.logging.Logger
import com.kite.app.foundation.runtime.HostProcessInspector
import com.kite.app.foundation.runtime.ProcessExitSemantics
import com.kite.app.foundation.runtime.RuntimeBoundary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import java.io.File

/**
 * 工作面层对象，主责是维护“空间 / 终端会话 / 智能体运行时”的元数据。
 *
 * 它可以决定谁属于哪个空间、哪个会话是当前查看的终端；
 * 但不应该回到建房层去决定 rootfs、bind 或 PRoot 参数。
 */
object KFWorkspaceManager {

    private const val DEFAULT_SPACE_ID = "space-main"
    private const val DEFAULT_SPACE_NAME = "默认空间"
    private const val SPACES_FILE = "spaces.json"
    private const val TERMINALS_FILE = "terminal-sessions.json"
    private const val AGENTS_FILE = "agent-runtimes.json"

    private val _currentSpaceState = MutableStateFlow<SpaceRecord?>(null)
    val currentSpaceState: StateFlow<SpaceRecord?> = _currentSpaceState

    private fun runtimeRoot(context: Context): File = WorkSurfaceRuntimeBridge.getRuntimeRoot(context)

    @Synchronized
    fun ensureDefaultSpace(context: Context): SpaceRecord {
        WorkSurfaceRuntimeBridge.ensureBaseImageReady(context)
        val container = WorkSurfaceRuntimeBridge.ensureDefaultContainer(context)
        val runtimeRoot = runtimeRoot(context)
        val now = System.currentTimeMillis()

        val spaces = loadSpaces(runtimeRoot).toMutableList()
        val existing = spaces.firstOrNull { it.id == DEFAULT_SPACE_ID }
        val currentTerminalSessionId = existing?.currentTerminalSessionId

        val space = (existing ?: SpaceRecord(
            id = DEFAULT_SPACE_ID,
            displayName = DEFAULT_SPACE_NAME,
            containerId = container.id,
            workspacePath = container.workspacePath,
            createdAt = now,
            note = "当前产品默认以空间为顶层对象。"
        )).copy(
            containerId = container.id,
            workspacePath = container.workspacePath,
            lastOpenedAt = now,
            status = SpaceStatus.ACTIVE,
            currentTerminalSessionId = currentTerminalSessionId
        )

        val updatedSpaces = spaces
            .filterNot { it.id == space.id }
            .toMutableList()
            .apply { add(space) }

        saveSpaces(runtimeRoot, updatedSpaces)
        ensureBuiltinAgents(runtimeRoot, space)
        val (normalizedSpaces, _) = normalizeWorkspaceState(runtimeRoot)
        syncCurrentSpaceState(normalizedSpaces)
        val normalizedSpace = normalizedSpaces.firstOrNull { it.id == DEFAULT_SPACE_ID } ?: space
        _currentSpaceState.value = normalizedSpace
        return normalizedSpace
    }

    fun getCurrentSpace(context: Context): SpaceRecord? {
        val normalizedSpaces = normalizeWorkspaceState(runtimeRoot(context)).first
        syncCurrentSpaceState(normalizedSpaces)
        return _currentSpaceState.value ?: normalizedSpaces.firstOrNull { it.id == DEFAULT_SPACE_ID }?.also {
            _currentSpaceState.value = it
        }
    }

    fun listSpaces(context: Context): List<SpaceRecord> {
        return normalizeWorkspaceState(runtimeRoot(context)).first.also { normalizedSpaces ->
            syncCurrentSpaceState(normalizedSpaces)
        }
    }

    @Synchronized
    fun setCurrentTerminalSession(
        context: Context,
        spaceId: String,
        sessionId: String?
    ): SpaceRecord? {
        val runtimeRoot = runtimeRoot(context)
        val spaces = loadSpaces(runtimeRoot)
        val updatedSpaces = spaces.map { space ->
            if (space.id == spaceId) {
                space.copy(
                    currentTerminalSessionId = sessionId,
                    lastOpenedAt = System.currentTimeMillis(),
                    status = SpaceStatus.ACTIVE
                )
            } else {
                space
            }
        }

        saveSpaces(runtimeRoot, updatedSpaces)
        val (normalizedSpaces, _) = normalizeWorkspaceState(runtimeRoot)
        syncCurrentSpaceState(normalizedSpaces)
        val updatedSpace = normalizedSpaces.firstOrNull { it.id == spaceId }
        if (_currentSpaceState.value?.id == spaceId) {
            _currentSpaceState.value = updatedSpace
        }
        return updatedSpace
    }

    fun listTerminalSessions(context: Context, spaceId: String): List<ManagedTerminalRecord> {
        return normalizeWorkspaceState(runtimeRoot(context)).second
            .filter { it.spaceId == spaceId }
            .sortedWith(compareBy<ManagedTerminalRecord> { it.createdAt }.thenBy { it.title })
    }

    fun listAllTerminalSessions(context: Context): List<ManagedTerminalRecord> {
        return normalizeWorkspaceState(runtimeRoot(context)).second
            .sortedWith(compareBy<ManagedTerminalRecord> { it.spaceId }.thenBy { it.createdAt })
    }

    fun getAgentRuntime(context: Context, runtimeId: String): AgentRuntimeRecord? {
        return loadAgentRuntimes(runtimeRoot(context))
            .firstOrNull { it.id == runtimeId }
    }

    fun getTerminalSession(context: Context, sessionId: String): ManagedTerminalRecord? {
        return normalizeWorkspaceState(runtimeRoot(context)).second
            .firstOrNull { it.id == sessionId }
    }

    fun getCurrentTerminalSession(context: Context, spaceId: String): ManagedTerminalRecord? {
        val runtimeRoot = runtimeRoot(context)
        val (spaces, sessions) = normalizeWorkspaceState(runtimeRoot)
        val space = spaces
            .firstOrNull { it.id == spaceId }
        val spaceSessions = sessions
            .filter { it.spaceId == spaceId }
            .sortedWith(compareBy<ManagedTerminalRecord> { it.createdAt }.thenBy { it.title })
        val currentId = space?.currentTerminalSessionId
        return spaceSessions.firstOrNull { it.id == currentId } ?: spaceSessions.firstOrNull()
    }

    @Synchronized
    fun createShellSession(
        context: Context,
        spaceId: String,
        title: String = suggestNextShellTitle(context, spaceId),
        sourceLabel: String? = null
    ): ManagedTerminalRecord {
        return createManagedTerminalSession(
            context = context,
            spaceId = spaceId,
            title = title,
            kind = ManagedTerminalKind.SHELL,
            sourceLabel = sourceLabel
        )
    }

    fun createEmbeddedShellSession(
        context: Context,
        spaceId: String,
        title: String
    ): ManagedTerminalRecord {
        val now = System.currentTimeMillis()
        val safeTitle = title.trim().ifBlank { "终端" }
        return ManagedTerminalRecord(
            id = "embedded-$spaceId-$now",
            spaceId = spaceId,
            title = safeTitle,
            kind = ManagedTerminalKind.SHELL,
            createdAt = now,
            status = ManagedTerminalStatus.REGISTERED
        )
    }

    @Synchronized
    fun createAgentConsoleSession(
        context: Context,
        spaceId: String,
        agentDisplayName: String,
        sourceAgentRuntimeId: String? = null,
        startupCommand: String? = null
    ): ManagedTerminalRecord {
        val baseTitle = "${agentDisplayName.trim().ifBlank { "智能体" }} 会话"
        return createManagedTerminalSession(
            context = context,
            spaceId = spaceId,
            title = suggestUniqueTerminalTitle(context, spaceId, baseTitle),
            kind = ManagedTerminalKind.AGENT_CONSOLE,
            sourceAgentRuntimeId = sourceAgentRuntimeId,
            startupCommand = startupCommand
        )
    }

    @Synchronized
    fun updateTerminalSessionStatus(
        context: Context,
        sessionId: String,
        status: ManagedTerminalStatus,
        lastAttachedAt: Long? = null,
        lastStartedAt: Long? = null,
        lastExitedAt: Long? = null,
        lastPid: Int? = null,
        lastExitCode: Int? = null
    ): ManagedTerminalRecord? {
        val runtimeRoot = runtimeRoot(context)
        val updated = loadTerminalSessions(runtimeRoot).map { session ->
            if (session.id == sessionId) {
                session.copy(
                    status = status,
                    lastAttachedAt = lastAttachedAt ?: session.lastAttachedAt,
                    lastStartedAt = lastStartedAt ?: session.lastStartedAt,
                    lastExitedAt = lastExitedAt ?: session.lastExitedAt,
                    lastPid = lastPid ?: session.lastPid,
                    lastExitCode = lastExitCode ?: session.lastExitCode
                )
            } else {
                session
            }
        }
        saveTerminalSessions(runtimeRoot, updated)
        val (normalizedSpaces, normalizedSessions) = normalizeWorkspaceState(runtimeRoot)
        syncCurrentSpaceState(normalizedSpaces)
        return normalizedSessions.firstOrNull { it.id == sessionId }
    }

    @Synchronized
    fun freezeRecoverableTerminalSessions(context: Context): List<ManagedTerminalRecord> {
        val runtimeRoot = runtimeRoot(context)
        val sessions = loadTerminalSessions(runtimeRoot)
        val hostSnapshot = HostProcessInspector.readSnapshot(logTag = "WorkspaceManager")
        val now = System.currentTimeMillis()
        val updated = sessions.map { session ->
            session.reconciledAfterHostRestart(hostSnapshot, now)
        }

        if (updated != sessions) {
            val changedCount = sessions.zip(updated).count { it.first != it.second }
            val reapedCount = sessions.zip(updated).count { (before, after) ->
                before.status == ManagedTerminalStatus.FROZEN &&
                    before.lastPid?.takeIf { it > 0 } != null &&
                    (
                        after.status == ManagedTerminalStatus.FAILED ||
                            after.status == ManagedTerminalStatus.STOPPED
                        )
            }
            Logger.i(
                "WorkspaceManager",
                "宿主重建，终端会话已归一化: changed=$changedCount reapedFrozen=$reapedCount"
            )
            saveTerminalSessions(runtimeRoot, updated)
        } else {
            Logger.i("WorkspaceManager", "宿主重建，没有需要归一化的终端会话")
        }
        return normalizeWorkspaceState(runtimeRoot).second
    }

    @Synchronized
    fun deleteTerminalSession(context: Context, sessionId: String): Boolean {
        val runtimeRoot = runtimeRoot(context)
        val sessions = loadTerminalSessions(runtimeRoot)
        val target = sessions.firstOrNull { it.id == sessionId } ?: return false
        val updatedSessions = sessions.filterNot { it.id == sessionId }
        saveTerminalSessions(runtimeRoot, updatedSessions)

        val spaces = loadSpaces(runtimeRoot)
        val updatedSpaces = spaces.map { space ->
            if (space.id == target.spaceId && space.currentTerminalSessionId == sessionId) {
                val fallback = updatedSessions.firstOrNull { it.spaceId == space.id }?.id
                space.copy(currentTerminalSessionId = fallback)
            } else {
                space
            }
        }
        saveSpaces(runtimeRoot, updatedSpaces)
        val (normalizedSpaces, _) = normalizeWorkspaceState(runtimeRoot)
        syncCurrentSpaceState(normalizedSpaces)
        if (_currentSpaceState.value?.id == target.spaceId) {
            _currentSpaceState.value = normalizedSpaces.firstOrNull { it.id == target.spaceId }
        }
        return true
    }

    fun suggestNextShellTitle(context: Context, spaceId: String): String {
        val existingTitles = listTerminalSessions(context, spaceId).map { it.title }.toSet()
        var suffix = System.currentTimeMillis().toString(36).takeLast(4).uppercase()
        var candidate = "Shell $suffix"
        var attempts = 0
        while (candidate in existingTitles) {
            attempts += 1
            suffix = (System.currentTimeMillis() + attempts).toString(36).takeLast(4).uppercase()
            candidate = "Shell $suffix"
        }
        return candidate
    }

    @Synchronized
    fun updateTerminalSessionTitle(
        context: Context,
        sessionId: String,
        title: String
    ): ManagedTerminalRecord? {
        val safeTitle = sanitizeTerminalTitle(title) ?: return getTerminalSession(context, sessionId)
        val runtimeRoot = runtimeRoot(context)
        val sessions = loadTerminalSessions(runtimeRoot)
        var changed = false
        val updated = sessions.map { session ->
            if (session.id == sessionId && session.title != safeTitle) {
                changed = true
                session.copy(title = safeTitle)
            } else {
                session
            }
        }
        if (changed) {
            saveTerminalSessions(runtimeRoot, updated)
        }
        val (normalizedSpaces, normalizedSessions) = normalizeWorkspaceState(runtimeRoot)
        syncCurrentSpaceState(normalizedSpaces)
        return normalizedSessions.firstOrNull { it.id == sessionId }
    }

    private fun suggestUniqueTerminalTitle(
        context: Context,
        spaceId: String,
        baseTitle: String
    ): String {
        val existingTitles = listTerminalSessions(context, spaceId).map { it.title }.toSet()
        if (baseTitle !in existingTitles) {
            return baseTitle
        }

        var suffix = 2
        while ("$baseTitle $suffix" in existingTitles) {
            suffix += 1
        }
        return "$baseTitle $suffix"
    }

    private fun sanitizeTerminalTitle(title: String): String? {
        val cleaned = title
            .replace(Regex("\\p{Cntrl}+"), " ")
            .trim()
            .take(80)
            .trim()
        return cleaned.takeIf { it.isNotBlank() }
    }

    fun listAgentRuntimes(context: Context, spaceId: String): List<AgentRuntimeRecord> {
        return loadAgentRuntimes(runtimeRoot(context))
            .filter { it.spaceId == spaceId }
            .sortedWith(compareByDescending<AgentRuntimeRecord> { it.isPrimary }.thenBy { it.createdAt })
    }

    @Synchronized
    fun updateAgentRuntimeStatus(
        context: Context,
        runtimeId: String,
        status: AgentRuntimeStatus,
        pid: Int? = null,
        lastError: String? = null
    ): AgentRuntimeRecord? {
        val runtimeRoot = runtimeRoot(context)
        val updated = loadAgentRuntimes(runtimeRoot).map { runtime ->
            if (runtime.id == runtimeId) {
                runtime.copy(
                    status = status,
                    pid = pid ?: runtime.pid,
                    lastStartedAt = when (status) {
                        AgentRuntimeStatus.STARTING, AgentRuntimeStatus.RUNNING ->
                            System.currentTimeMillis()
                        else -> runtime.lastStartedAt
                    },
                    lastError = lastError
                )
            } else {
                runtime
            }
        }
        saveAgentRuntimes(runtimeRoot, updated)
        return updated.firstOrNull { it.id == runtimeId }
    }

    private fun normalizeWorkspaceState(
        runtimeRoot: File
    ): Pair<List<SpaceRecord>, List<ManagedTerminalRecord>> {
        val spaces = loadSpaces(runtimeRoot)
        val sessions = loadTerminalSessions(runtimeRoot)
        if (spaces.isEmpty() && sessions.isEmpty()) {
            return spaces to sessions
        }

        val normalizedSessions = sessions.map { it.normalizedRecord() }
        val normalizedSpaces = spaces.map { space ->
            val spaceSessions = normalizedSessions.filter { it.spaceId == space.id }
            val normalizedCurrentSessionId = resolvePreferredCurrentSessionId(
                currentSessionId = space.currentTerminalSessionId,
                sessions = spaceSessions
            )
            if (normalizedCurrentSessionId == space.currentTerminalSessionId) {
                space
            } else {
                space.copy(currentTerminalSessionId = normalizedCurrentSessionId)
            }
        }

        if (normalizedSessions != sessions) {
            saveTerminalSessions(runtimeRoot, normalizedSessions)
            Logger.i(
                "WorkspaceManager",
                "终端记录已归一化: changed=${sessions.zip(normalizedSessions).count { it.first != it.second }}"
            )
        }
        if (normalizedSpaces != spaces) {
            saveSpaces(runtimeRoot, normalizedSpaces)
            Logger.i("WorkspaceManager", "空间当前终端指针已归一化")
        }

        return normalizedSpaces to normalizedSessions
    }

    private fun ManagedTerminalRecord.normalizedRecord(): ManagedTerminalRecord {
        val normalizedPid = lastPid?.takeIf { it > 0 }
        val normalizedStatus = when (status) {
            ManagedTerminalStatus.ATTACHED,
            ManagedTerminalStatus.RUNNING -> {
                if (normalizedPid != null) {
                    status
                } else if (ProcessExitSemantics.isManagedStopExit(lastExitCode)) {
                    ManagedTerminalStatus.STOPPED
                } else if (lastAttachedAt != null || lastStartedAt != null) {
                    ManagedTerminalStatus.FROZEN
                } else {
                    ManagedTerminalStatus.REGISTERED
                }
            }

            else -> status
        }
        return if (normalizedPid == lastPid && normalizedStatus == status) {
            this
        } else {
            copy(
                lastPid = normalizedPid,
                status = normalizedStatus
            )
        }
    }

    private fun ManagedTerminalRecord.reconciledAfterHostRestart(
        hostSnapshot: com.kite.app.foundation.runtime.HostProcessSnapshot,
        now: Long
    ): ManagedTerminalRecord {
        val normalizedPid = lastPid?.takeIf { it > 0 }
        val hostPidAlive = normalizedPid?.let(hostSnapshot::appProcess) != null
        val fallbackAttachedAt = lastAttachedAt ?: lastStartedAt ?: createdAt
        val nextStatus = when (status) {
            ManagedTerminalStatus.ATTACHED,
            ManagedTerminalStatus.RUNNING -> when {
                hostPidAlive -> ManagedTerminalStatus.FROZEN
                normalizedPid != null -> archivedStatusAfterHostRestart()
                else -> ManagedTerminalStatus.REGISTERED
            }

            ManagedTerminalStatus.FROZEN -> when {
                hostPidAlive -> ManagedTerminalStatus.FROZEN
                normalizedPid != null -> archivedStatusAfterHostRestart()
                else -> ManagedTerminalStatus.REGISTERED
            }

            else -> status
        }
        val nextAttachedAt = when (nextStatus) {
            ManagedTerminalStatus.FROZEN -> fallbackAttachedAt
            else -> lastAttachedAt
        }
        val nextExitedAt = when {
            (
                nextStatus == ManagedTerminalStatus.FAILED ||
                    nextStatus == ManagedTerminalStatus.STOPPED
                ) && lastExitedAt == null -> now
            else -> lastExitedAt
        }

        return if (
            nextStatus == status &&
            normalizedPid == lastPid &&
            nextAttachedAt == lastAttachedAt &&
            nextExitedAt == lastExitedAt
        ) {
            this
        } else {
            copy(
                status = nextStatus,
                lastPid = normalizedPid,
                lastAttachedAt = nextAttachedAt,
                lastExitedAt = nextExitedAt
            )
        }
    }

    private fun ManagedTerminalRecord.archivedStatusAfterHostRestart(): ManagedTerminalStatus {
        return if (ProcessExitSemantics.isManagedStopExit(lastExitCode)) {
            ManagedTerminalStatus.STOPPED
        } else {
            ManagedTerminalStatus.FAILED
        }
    }

    private fun resolvePreferredCurrentSessionId(
        currentSessionId: String?,
        sessions: List<ManagedTerminalRecord>
    ): String? {
        val validCurrent = sessions.firstOrNull {
            it.id == currentSessionId && !it.isArchivedRecord()
        }
        if (validCurrent != null) {
            return validCurrent.id
        }

        return sessions
            .filterNot { it.isArchivedRecord() }
            .sortedWith(
                compareBy<ManagedTerminalRecord> { currentSessionPriority(it) }
                    .thenByDescending { it.lastAttachedAt ?: it.lastStartedAt ?: it.createdAt }
                    .thenBy { it.title }
            )
            .firstOrNull()
            ?.id
    }

    private fun currentSessionPriority(session: ManagedTerminalRecord): Int {
        return when {
            session.status.isLiveProcessStatus() -> 0
            session.status == ManagedTerminalStatus.FROZEN -> 1
            session.status == ManagedTerminalStatus.REGISTERED -> 2
            else -> 3
        }
    }

    private fun syncCurrentSpaceState(spaces: List<SpaceRecord>) {
        val currentState = _currentSpaceState.value ?: return
        val normalized = spaces.firstOrNull { it.id == currentState.id } ?: return
        if (normalized != currentState) {
            _currentSpaceState.value = normalized
        }
    }

    private fun createManagedTerminalSession(
        context: Context,
        spaceId: String,
        title: String,
        kind: ManagedTerminalKind,
        sourceAgentRuntimeId: String? = null,
        startupCommand: String? = null,
        sourceLabel: String? = null
    ): ManagedTerminalRecord {
        val runtimeRoot = runtimeRoot(context)
        val now = System.currentTimeMillis()
        val fallbackTitle = when (kind) {
            ManagedTerminalKind.SHELL -> suggestNextShellTitle(context, spaceId)
            ManagedTerminalKind.AGENT_CONSOLE -> suggestUniqueTerminalTitle(context, spaceId, "智能体会话")
        }
        val safeTitle = title.trim().ifBlank { fallbackTitle }
        val safeSourceLabel = sourceLabel
            ?.trim()
            ?.take(80)
            ?.takeIf { it.isNotBlank() }
        val prefix = when (kind) {
            ManagedTerminalKind.SHELL -> "shell"
            ManagedTerminalKind.AGENT_CONSOLE -> "agent-console"
        }

        val record = ManagedTerminalRecord(
            id = "$prefix-$spaceId-$now",
            spaceId = spaceId,
            title = safeTitle,
            kind = kind,
            createdAt = now,
            sourceAgentRuntimeId = sourceAgentRuntimeId,
            startupCommand = startupCommand,
            sourceLabel = safeSourceLabel,
            status = ManagedTerminalStatus.REGISTERED
        )

        val updated = loadTerminalSessions(runtimeRoot)
            .toMutableList()
            .apply { add(record) }
        saveTerminalSessions(runtimeRoot, updated)
        return record
    }

    private fun ensureBuiltinAgents(runtimeRoot: File, space: SpaceRecord) {
        val existing = loadAgentRuntimes(runtimeRoot)
        val existingIds = existing
            .filter { it.spaceId == space.id }
            .map { it.id }
            .toSet()

        val builtins = listOf(
            AgentRuntimeRecord(
                id = "agent-${space.id}-claude-code",
                spaceId = space.id,
                agentKind = AgentKind.CLAUDE_CODE,
                displayName = "Claude Code",
                workingDirectory = WorkSurfaceRuntimeBridge.defaults.workspaceDir,
                launchCommand = "claude",
                launchMode = AgentLaunchMode.NEW_MANAGED_SESSION,
                createdAt = space.createdAt,
                isPrimary = true
            ),
            AgentRuntimeRecord(
                id = "agent-${space.id}-codex",
                spaceId = space.id,
                agentKind = AgentKind.CODEX,
                displayName = "Codex",
                workingDirectory = WorkSurfaceRuntimeBridge.defaults.workspaceDir,
                launchCommand = "codex",
                launchMode = AgentLaunchMode.NEW_MANAGED_SESSION,
                createdAt = space.createdAt
            ),
            AgentRuntimeRecord(
                id = "agent-${space.id}-openclaw",
                spaceId = space.id,
                agentKind = AgentKind.OPENCLAW,
                displayName = "OpenClaw",
                workingDirectory = WorkSurfaceRuntimeBridge.defaults.workspaceDir,
                launchCommand = "openclaw",
                launchMode = AgentLaunchMode.NEW_MANAGED_SESSION,
                createdAt = space.createdAt
            )
        ).filterNot { it.id in existingIds }

        if (builtins.isNotEmpty()) {
            saveAgentRuntimes(runtimeRoot, existing + builtins)
        }
    }

    private fun loadSpaces(runtimeRoot: File): List<SpaceRecord> {
        val spacesFile = File(runtimeRoot, SPACES_FILE)
        return loadJsonArray(spacesFile) { SpaceRecord.fromJson(it) }
    }

    private fun saveSpaces(runtimeRoot: File, spaces: List<SpaceRecord>) {
        val spacesFile = File(runtimeRoot, SPACES_FILE)
        saveJsonArray(spacesFile, spaces.sortedBy { it.createdAt }.map { it.toJson() })
    }

    private fun loadTerminalSessions(runtimeRoot: File): List<ManagedTerminalRecord> {
        val terminalsFile = File(runtimeRoot, TERMINALS_FILE)
        return loadJsonArray(terminalsFile) { ManagedTerminalRecord.fromJson(it) }
    }

    private fun saveTerminalSessions(runtimeRoot: File, sessions: List<ManagedTerminalRecord>) {
        val terminalsFile = File(runtimeRoot, TERMINALS_FILE)
        saveJsonArray(terminalsFile, sessions.sortedBy { it.createdAt }.map { it.toJson() })
    }

    private fun loadAgentRuntimes(runtimeRoot: File): List<AgentRuntimeRecord> {
        val agentsFile = File(runtimeRoot, AGENTS_FILE)
        return loadJsonArray(agentsFile) { AgentRuntimeRecord.fromJson(it) }
    }

    private fun saveAgentRuntimes(runtimeRoot: File, runtimes: List<AgentRuntimeRecord>) {
        val agentsFile = File(runtimeRoot, AGENTS_FILE)
        saveJsonArray(agentsFile, runtimes.sortedBy { it.createdAt }.map { it.toJson() })
    }

    private fun <T> loadJsonArray(file: File, parser: (org.json.JSONObject) -> T): List<T> {
        if (!file.exists()) {
            return emptyList()
        }

        return runCatching {
            val raw = file.readText()
            if (raw.isBlank()) {
                emptyList()
            } else {
                val array = JSONArray(raw)
                buildList {
                    for (index in 0 until array.length()) {
                        add(parser(array.getJSONObject(index)))
                    }
                }
            }
        }.getOrElse { error ->
            Logger.e("WorkspaceManager", "读取注册文件失败: ${file.name}, ${error.message}")
            emptyList()
        }
    }

    private fun saveJsonArray(file: File, objects: List<org.json.JSONObject>) {
        val array = JSONArray()
        objects.forEach { array.put(it) }
        runCatching {
            file.parentFile?.mkdirs()
            file.writeText(array.toString(2))
        }.onFailure { error ->
            Logger.e("KFWorkspaceManager", "保存工作面状态失败: ${file.name}, ${error.message}")
        }
    }
}
