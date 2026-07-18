package com.kite.app.application.settings

/**
 * Kite 支持的应用语言。空 languageTag 表示不覆盖系统语言。
 *
 * 这里保存稳定语义，不保存展示文字；语言名称由当前 Context 的资源解析。
 */
enum class AppLanguagePreference(val languageTag: String?) {
    System(null),
    SimplifiedChinese("zh-CN"),
    English("en");

    companion object {
        fun fromLanguageTags(languageTags: String?): AppLanguagePreference {
            val primaryTag = languageTags
                ?.split(',')
                ?.firstOrNull()
                ?.trim()
                ?.lowercase()
                .orEmpty()
            if (primaryTag.isBlank()) return System
            return when {
                primaryTag == "zh" || primaryTag.startsWith("zh-") -> SimplifiedChinese
                primaryTag == "en" || primaryTag.startsWith("en-") -> English
                else -> System
            }
        }
    }
}
