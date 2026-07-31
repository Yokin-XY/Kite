package com.kite.app.agent.store

import android.content.Context
import com.kite.app.foundation.workspace.KiteStorageContract
import org.json.JSONArray
import org.json.JSONObject

/**
 * Kite 自己拥有的 Agent 项目资料。
 *
 * 项目名称属于 Kite 的显示信息，工作目录仍是 Agent 会话使用的真实 `/workspace` 路径。
 * 这里不保存消息、会话标题、运行状态或模型配置。
 */
class AgentProjectStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES,
        Context.MODE_PRIVATE,
    )

    fun projects(agentId: String): List<AgentProject> = synchronized(LOCK) {
        val normalizedAgentId = agentId.trim()
        if (normalizedAgentId.isBlank()) return@synchronized emptyList()
        readProjects().filter { it.agentId == normalizedAgentId && !it.archived }
    }

    fun archivedProjects(agentId: String): List<AgentProject> = synchronized(LOCK) {
        val normalizedAgentId = agentId.trim()
        if (normalizedAgentId.isBlank()) return@synchronized emptyList()
        readProjects()
            .filter { it.agentId == normalizedAgentId && it.archived }
            .sortedByDescending(AgentProject::archivedAtMillis)
    }

    fun save(agentId: String, name: String, cwd: String): AgentProjectSaveResult = synchronized(LOCK) {
        val normalizedAgentId = agentId.trim()
        val normalizedName = name.trim()
        val normalizedCwd = KiteStorageContract.normalizeWorkspacePath(cwd)
        when {
            normalizedAgentId.isBlank() -> return@synchronized AgentProjectSaveResult.Failure("Agent 标识为空")
            normalizedName.isBlank() -> return@synchronized AgentProjectSaveResult.Failure("请输入项目名称")
            normalizedCwd == null || !KiteStorageContract.isSelectableProjectPath(normalizedCwd) ->
                return@synchronized AgentProjectSaveResult.Failure("请选择 Kite Ubuntu 中的项目目录")
        }

        val projects = readProjects().toMutableList()
        val sameDirectoryIndex = projects.indexOfFirst {
            it.agentId == normalizedAgentId && it.cwd == normalizedCwd
        }
        val duplicateName = projects.any { project ->
            project.agentId == normalizedAgentId &&
                project.cwd != normalizedCwd &&
                project.name.equals(normalizedName, ignoreCase = true)
        }
        if (duplicateName) {
            return@synchronized AgentProjectSaveResult.Failure("已经有同名项目")
        }

        val existing = projects.getOrNull(sameDirectoryIndex)
        val saved = AgentProject(
            agentId = normalizedAgentId,
            name = normalizedName,
            cwd = normalizedCwd,
            createdAtMillis = existing?.createdAtMillis ?: System.currentTimeMillis().coerceAtLeast(1L),
            archivedAtMillis = 0L,
        )
        if (sameDirectoryIndex >= 0) {
            projects[sameDirectoryIndex] = saved
        } else {
            projects += saved
        }
        writeProjects(projects)
        AgentProjectSaveResult.Success(saved)
    }

    fun archive(
        agentId: String,
        name: String,
        cwd: String,
        nowMillis: Long = System.currentTimeMillis(),
    ): Boolean = synchronized(LOCK) {
        val normalizedAgentId = agentId.trim()
        val normalizedName = name.trim()
        val normalizedCwd = KiteStorageContract.normalizeWorkspacePath(cwd)
        if (normalizedAgentId.isBlank() || normalizedName.isBlank() || normalizedCwd == null ||
            !KiteStorageContract.isSelectableProjectPath(normalizedCwd)
        ) {
            return@synchronized false
        }
        val projects = readProjects().toMutableList()
        val existingIndex = projects.indexOfFirst {
            it.agentId == normalizedAgentId && it.cwd == normalizedCwd
        }
        val existing = projects.getOrNull(existingIndex)
        if (existing?.archived == true) return@synchronized false
        val archived = AgentProject(
            agentId = normalizedAgentId,
            name = existing?.name ?: normalizedName,
            cwd = normalizedCwd,
            createdAtMillis = existing?.createdAtMillis ?: nowMillis.coerceAtLeast(1L),
            archivedAtMillis = nowMillis.coerceAtLeast(1L),
        )
        if (existingIndex >= 0) projects[existingIndex] = archived else projects += archived
        writeProjects(projects)
        true
    }

    fun restore(agentId: String, cwd: String): Boolean = synchronized(LOCK) {
        val normalizedAgentId = agentId.trim()
        val normalizedCwd = KiteStorageContract.normalizeWorkspacePath(cwd)
        if (normalizedAgentId.isBlank() || normalizedCwd == null) return@synchronized false
        val projects = readProjects().toMutableList()
        val index = projects.indexOfFirst {
            it.agentId == normalizedAgentId && it.cwd == normalizedCwd && it.archived
        }
        if (index < 0) return@synchronized false
        projects[index] = projects[index].copy(archivedAtMillis = 0L)
        writeProjects(projects)
        true
    }

    internal fun resetForTest() {
        synchronized(LOCK) { preferences.edit().clear().commit() }
    }

    private fun readProjects(): List<AgentProject> {
        val array = runCatching {
            JSONObject(preferences.getString(KEY_PAYLOAD, null) ?: "{}")
                .optJSONArray(KEY_PROJECTS)
        }.getOrNull() ?: return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val json = array.optJSONObject(index) ?: continue
                val agentId = json.optString(KEY_AGENT_ID).trim()
                val name = json.optString(KEY_NAME).trim()
                val cwd = KiteStorageContract.normalizeWorkspacePath(json.optString(KEY_CWD))
                if (agentId.isBlank() || name.isBlank() || cwd == null ||
                    !KiteStorageContract.isSelectableProjectPath(cwd)
                ) {
                    continue
                }
                add(AgentProject(
                    agentId = agentId,
                    name = name,
                    cwd = cwd,
                    createdAtMillis = json.optLong(KEY_CREATED_AT, 0L).coerceAtLeast(1L),
                    archivedAtMillis = json.optLong(KEY_ARCHIVED_AT, 0L).coerceAtLeast(0L),
                ))
            }
        }
    }

    private fun writeProjects(projects: List<AgentProject>) {
        val payload = JSONObject()
            .put(KEY_VERSION, VERSION)
            .put(KEY_PROJECTS, JSONArray().apply {
                projects.forEach { project ->
                    put(JSONObject()
                        .put(KEY_AGENT_ID, project.agentId)
                        .put(KEY_NAME, project.name)
                        .put(KEY_CWD, project.cwd)
                        .put(KEY_CREATED_AT, project.createdAtMillis)
                        .put(KEY_ARCHIVED_AT, project.archivedAtMillis))
                }
            })
        preferences.edit().putString(KEY_PAYLOAD, payload.toString()).apply()
    }

    private companion object {
        val LOCK = Any()
        const val PREFERENCES = "kite_agent_projects"
        const val KEY_PAYLOAD = "payload"
        const val KEY_VERSION = "version"
        const val KEY_PROJECTS = "projects"
        const val KEY_AGENT_ID = "agentId"
        const val KEY_NAME = "name"
        const val KEY_CWD = "cwd"
        const val KEY_CREATED_AT = "createdAt"
        const val KEY_ARCHIVED_AT = "archivedAt"
        const val VERSION = 2
    }
}

data class AgentProject(
    val agentId: String,
    val name: String,
    val cwd: String,
    val createdAtMillis: Long,
    val archivedAtMillis: Long = 0L,
) {
    val archived: Boolean get() = archivedAtMillis > 0L
}

sealed interface AgentProjectSaveResult {
    data class Success(val project: AgentProject) : AgentProjectSaveResult
    data class Failure(val message: String) : AgentProjectSaveResult
}
