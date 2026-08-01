package com.kite.app.feature.runsurface

import android.net.Uri
import android.text.InputType
import com.kite.app.agent.config.AgentProviderCredentialChange
import com.kite.app.agent.config.AgentProviderModelSummary

internal object AgentProviderEditorPolicy {
    private val SAFE_PROVIDER_ID = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,127}")
    private val SAFE_MODEL_ID = Regex("[^\\s\\p{Cc}]{1,384}")

    fun providerIdFromName(displayName: String): String {
        val ascii = displayName.trim().lowercase()
            .replace(Regex("[^a-z0-9._-]+"), "-")
            .trim('-', '.', '_')
            .take(96)
        if (ascii.isNotBlank() && SAFE_PROVIDER_ID.matches(ascii)) return ascii
        val hash = displayName.trim().hashCode().toUInt().toString(16)
        return "custom-$hash"
    }

    fun validate(
        displayName: String,
        providerId: String,
        baseUrl: String,
        models: List<AgentProviderModelSummary>
    ): String? {
        if (displayName.isBlank()) return "请输入供应商名称"
        if (!SAFE_PROVIDER_ID.matches(providerId)) return "供应商 ID 只能使用字母、数字、点、下划线和短横线"
        val uri = Uri.parse(baseUrl.trim())
        if (uri.scheme !in setOf("http", "https") || uri.host.isNullOrBlank() || uri.userInfo != null) {
            return "请求地址必须是有效的 HTTP 或 HTTPS 地址"
        }
        if (models.isEmpty()) return "请至少添加一个可用模型"
        models.forEach { model -> validateModel(model.displayName, model.id)?.let { return it } }
        if (models.map { it.id.trim() }.distinct().size != models.size) return "模型 ID 不能重复"
        return null
    }

    fun validateModel(displayName: String, modelId: String): String? {
        validateDisplayName(displayName)?.let { return it }
        if (!SAFE_MODEL_ID.matches(modelId.trim())) return "模型 ID 不能为空，也不能包含空格"
        return null
    }

    fun validateDisplayName(displayName: String): String? {
        val normalizedName = displayName.trim()
        if (normalizedName.isBlank()) return "请输入显示名称"
        if (normalizedName.length > MAX_MODEL_DISPLAY_NAME || normalizedName.any(Char::isISOControl)) {
            return "显示名称不能超过 $MAX_MODEL_DISPLAY_NAME 个字符"
        }
        return null
    }

    private const val MAX_MODEL_DISPLAY_NAME = 128
}

internal object AgentProviderCredentialInputPolicy {
    const val inputType: Int = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
    const val savedMask: String = "••••••••••••"

    fun displayHint(
        credentialPresent: Boolean,
        removeRequested: Boolean,
        emptyHint: String
    ): String = when {
        removeRequested -> "保存后移除 API Key"
        credentialPresent -> savedMask
        else -> emptyHint
    }

    fun credentialChange(
        removeRequested: Boolean,
        value: CharSequence?
    ): AgentProviderCredentialChange {
        if (removeRequested) return AgentProviderCredentialChange.Remove
        val replacement = value?.toString()?.trim().orEmpty()
        return if (replacement.isBlank()) {
            AgentProviderCredentialChange.Keep
        } else {
            AgentProviderCredentialChange.replace(replacement)
        }
    }

    fun clipboardValue(value: CharSequence?): String? = value
        ?.toString()
        ?.trim()
        ?.takeIf(String::isNotBlank)
}
