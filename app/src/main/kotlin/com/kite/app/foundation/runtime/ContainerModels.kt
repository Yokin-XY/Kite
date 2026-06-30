package com.kite.app.foundation.runtime

import org.json.JSONObject

enum class ContainerStatus(val label: String) {
    CREATED("已创建"),
    STARTING("启动中"),
    RUNNING("运行中"),
    STOPPED("已停止"),
    ERROR("异常")
}

enum class NetworkMode(val label: String, val prootFlag: String?) {
    HOST("宿主网络", null),
    NONE("无网络", "--net=none")
}

data class ContainerRecord(
    val id: String,
    val displayName: String,
    val imageName: String,
    val baseProfile: String? = null,
    val rootfsPath: String,
    val workspacePath: String,
    val createdAt: Long,
    val lastStartedAt: Long? = null,
    val status: ContainerStatus = ContainerStatus.CREATED,
    val networkMode: NetworkMode = NetworkMode.HOST,
    // Legacy compatibility field. This may contain the last terminal proot pid, not a
    // unique container root. Runtime truth must come from RuntimeHealthStore.
    val pid: Int? = null,
    val lastError: String? = null
) {
    fun toJson(): JSONObject {
        return JSONObject()
            .put("id", id)
            .put("displayName", displayName)
            .put("imageName", imageName)
            .put("rootfsPath", rootfsPath)
            .put("workspacePath", workspacePath)
            .put("createdAt", createdAt)
            .put("lastStartedAt", lastStartedAt)
            .put("status", status.name)
            .put("networkMode", networkMode.name)
            .put("pid", pid)
            .put("lastError", lastError)
            .put("baseProfile", baseProfile)
    }

    companion object {
        fun fromJson(json: JSONObject): ContainerRecord {
            return ContainerRecord(
                id = json.getString("id"),
                displayName = json.optString("displayName", json.getString("id")),
                imageName = json.optString("imageName", "ubuntu-base-24.04-arm64"),
                rootfsPath = json.getString("rootfsPath"),
                workspacePath = json.getString("workspacePath"),
                createdAt = json.getLong("createdAt"),
                lastStartedAt = json.optLong("lastStartedAt").takeIf { !json.isNull("lastStartedAt") },
                status = ContainerStatus.valueOf(json.optString("status", ContainerStatus.CREATED.name)),
                networkMode = NetworkMode.valueOf(json.optString("networkMode", NetworkMode.HOST.name)),
                pid = json.optInt("pid").takeIf { !json.isNull("pid") },
                lastError = json.optString("lastError").takeIf { !json.isNull("lastError") },
                baseProfile = json.optString("baseProfile", BaseImageProfile.fromImageName(json.optString("imageName", "ubuntu-base-24.04-arm64")).codename)
            )
        }
    }
}

data class ContainerLaunchConfig(
    val container: ContainerRecord,
    val executablePath: String,
    val workingDirectory: String,
    val args: Array<String>,
    val env: Array<String>
)

data class ContainerExecConfig(
    val container: ContainerRecord,
    val workingDirectory: String,
    val command: List<String>,
    val env: Map<String, String>
)
