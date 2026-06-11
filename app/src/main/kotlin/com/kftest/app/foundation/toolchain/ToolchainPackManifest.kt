package com.kftest.app.foundation.toolchain

import org.json.JSONObject

data class ToolchainPackManifest(
    val packId: String,
    val displayName: String,
    val version: Int,
    val description: String
) {
    companion object {
        fun fromJson(json: JSONObject): ToolchainPackManifest {
            return ToolchainPackManifest(
                packId = json.optString("packId", "ai-dev-pack"),
                displayName = json.optString("displayName", "KF tool environment"),
                version = json.optInt("version", 1),
                description = json.optString("description", "")
            )
        }
    }
}
