package com.kftest.app.foundation.workspace

import org.json.JSONObject

enum class SpaceStatus(val label: String) {
    CREATED("已创建"),
    READY("已就绪"),
    ACTIVE("当前空间"),
    ERROR("异常")
}

enum class ManagedTerminalKind(val label: String) {
    SHELL("Shell 终端"),
    AGENT_CONSOLE("智能体终端")
}

enum class ManagedTerminalStatus(val label: String) {
    REGISTERED("已登记"),
    ATTACHED("已连接"),
    RUNNING("运行中"),
    FROZEN("已冷冻"),
    EXITED("已退出"),
    FAILED("异常退出"),
    STOPPED("已停止")
}

enum class AgentKind(val label: String, val defaultCommand: String) {
    CLAUDE_CODE("Claude Code", "claude"),
    CODEX("Codex", "codex"),
    OPENCLAW("OpenClaw", "openclaw"),
    CUSTOM("自定义", "")
}

enum class AgentRuntimeStatus(val label: String) {
    REGISTERED("已登记"),
    STARTING("启动中"),
    RUNNING("运行中"),
    STOPPED("已停止"),
    ERROR("异常")
}

enum class AgentLaunchMode(val label: String) {
    REUSE_CURRENT("复用当前终端"),
    NEW_MANAGED_SESSION("新建受管终端"),
    BACKGROUND_SERVICE("后台服务")
}

data class SpaceRecord(
    val id: String,
    val displayName: String,
    val containerId: String,
    val workspacePath: String,
    val createdAt: Long,
    val lastOpenedAt: Long? = null,
    val status: SpaceStatus = SpaceStatus.CREATED,
    val currentTerminalSessionId: String? = null,
    val primaryAgentId: String? = null,
    val note: String? = null
) {
    fun toJson(): JSONObject {
        return JSONObject()
            .put("id", id)
            .put("displayName", displayName)
            .put("containerId", containerId)
            .put("workspacePath", workspacePath)
            .put("createdAt", createdAt)
            .put("lastOpenedAt", lastOpenedAt)
            .put("status", status.name)
            .put("currentTerminalSessionId", currentTerminalSessionId)
            .put("primaryAgentId", primaryAgentId)
            .put("note", note)
    }

    companion object {
        fun fromJson(json: JSONObject): SpaceRecord {
            return SpaceRecord(
                id = json.getString("id"),
                displayName = json.optString("displayName", json.getString("id")),
                containerId = json.optString("containerId", "ubuntu-main"),
                workspacePath = json.optString("workspacePath", ""),
                createdAt = json.getLong("createdAt"),
                lastOpenedAt = json.optLong("lastOpenedAt").takeIf { !json.isNull("lastOpenedAt") },
                status = SpaceStatus.valueOf(json.optString("status", SpaceStatus.CREATED.name)),
                currentTerminalSessionId = json.optString("currentTerminalSessionId").takeIf {
                    !json.isNull("currentTerminalSessionId")
                },
                primaryAgentId = json.optString("primaryAgentId").takeIf {
                    !json.isNull("primaryAgentId")
                },
                note = json.optString("note").takeIf { !json.isNull("note") }
            )
        }
    }
}

data class ManagedTerminalRecord(
    val id: String,
    val spaceId: String,
    val title: String,
    val kind: ManagedTerminalKind,
    val createdAt: Long,
    val lastAttachedAt: Long? = null,
    val lastStartedAt: Long? = null,
    val lastExitedAt: Long? = null,
    val lastPid: Int? = null,
    val lastExitCode: Int? = null,
    val sourceAgentRuntimeId: String? = null,
    val startupCommand: String? = null,
    val sourceLabel: String? = null,
    val status: ManagedTerminalStatus = ManagedTerminalStatus.REGISTERED
) {
    fun toJson(): JSONObject {
        return JSONObject()
            .put("id", id)
            .put("spaceId", spaceId)
            .put("title", title)
            .put("kind", kind.name)
            .put("createdAt", createdAt)
            .put("lastAttachedAt", lastAttachedAt)
            .put("lastStartedAt", lastStartedAt)
            .put("lastExitedAt", lastExitedAt)
            .put("lastPid", lastPid)
            .put("lastExitCode", lastExitCode)
            .put("sourceAgentRuntimeId", sourceAgentRuntimeId)
            .put("startupCommand", startupCommand)
            .put("sourceLabel", sourceLabel)
            .put("status", status.name)
    }

    companion object {
        fun fromJson(json: JSONObject): ManagedTerminalRecord {
            return ManagedTerminalRecord(
                id = json.getString("id"),
                spaceId = json.getString("spaceId"),
                title = json.optString("title", "终端"),
                kind = ManagedTerminalKind.valueOf(
                    json.optString("kind", ManagedTerminalKind.SHELL.name)
                ),
                createdAt = json.getLong("createdAt"),
                lastAttachedAt = json.optLong("lastAttachedAt").takeIf {
                    !json.isNull("lastAttachedAt")
                },
                lastStartedAt = json.optLong("lastStartedAt").takeIf {
                    !json.isNull("lastStartedAt")
                },
                lastExitedAt = json.optLong("lastExitedAt").takeIf {
                    !json.isNull("lastExitedAt")
                },
                lastPid = json.optInt("lastPid").takeIf {
                    !json.isNull("lastPid")
                },
                lastExitCode = json.optInt("lastExitCode").takeIf {
                    !json.isNull("lastExitCode")
                },
                sourceAgentRuntimeId = json.optString("sourceAgentRuntimeId").takeIf {
                    !json.isNull("sourceAgentRuntimeId")
                },
                startupCommand = json.optString("startupCommand").takeIf {
                    !json.isNull("startupCommand")
                },
                sourceLabel = json.optString("sourceLabel").takeIf {
                    !json.isNull("sourceLabel")
                },
                status = ManagedTerminalStatus.valueOf(
                    json.optString("status", ManagedTerminalStatus.REGISTERED.name)
                )
            )
        }
    }
}

data class AgentRuntimeRecord(
    val id: String,
    val spaceId: String,
    val agentKind: AgentKind,
    val displayName: String,
    val workingDirectory: String,
    val launchCommand: String,
    val launchMode: AgentLaunchMode = AgentLaunchMode.NEW_MANAGED_SESSION,
    val createdAt: Long,
    val lastStartedAt: Long? = null,
    val status: AgentRuntimeStatus = AgentRuntimeStatus.REGISTERED,
    val pid: Int? = null,
    val isPrimary: Boolean = false,
    val lastError: String? = null
) {
    fun toJson(): JSONObject {
        return JSONObject()
            .put("id", id)
            .put("spaceId", spaceId)
            .put("agentKind", agentKind.name)
            .put("displayName", displayName)
            .put("workingDirectory", workingDirectory)
            .put("launchCommand", launchCommand)
            .put("launchMode", launchMode.name)
            .put("createdAt", createdAt)
            .put("lastStartedAt", lastStartedAt)
            .put("status", status.name)
            .put("pid", pid)
            .put("isPrimary", isPrimary)
            .put("lastError", lastError)
    }

    companion object {
        fun fromJson(json: JSONObject): AgentRuntimeRecord {
            return AgentRuntimeRecord(
                id = json.getString("id"),
                spaceId = json.getString("spaceId"),
                agentKind = AgentKind.valueOf(json.optString("agentKind", AgentKind.CUSTOM.name)),
                displayName = json.optString("displayName", json.getString("id")),
                workingDirectory = json.optString(
                    "workingDirectory",
                    WorkSurfaceRuntimeBridge.defaults.workspaceDir
                ),
                launchCommand = json.optString("launchCommand", ""),
                launchMode = AgentLaunchMode.valueOf(
                    json.optString("launchMode", AgentLaunchMode.NEW_MANAGED_SESSION.name)
                ),
                createdAt = json.getLong("createdAt"),
                lastStartedAt = json.optLong("lastStartedAt").takeIf {
                    !json.isNull("lastStartedAt")
                },
                status = AgentRuntimeStatus.valueOf(
                    json.optString("status", AgentRuntimeStatus.REGISTERED.name)
                ),
                pid = json.optInt("pid").takeIf { !json.isNull("pid") },
                isPrimary = json.optBoolean("isPrimary", false),
                lastError = json.optString("lastError").takeIf { !json.isNull("lastError") }
            )
        }
    }
}
