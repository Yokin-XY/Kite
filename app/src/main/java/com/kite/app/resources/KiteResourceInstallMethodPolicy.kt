package com.kite.app.resources

/**
 * 仅供资源卡编写和目录检查使用的安装方式选择顺序。
 *
 * 这里不在运行时把一种安装方式切成另一种：包管理器、发布包、脚本和源码可能包含不同内容，
 * 运行时静默切换会破坏版本、校验和卸载合同。运行时只允许在同一种方式内切换下载源。
 */
enum class KiteResourceCardInstallMethodTier(val priority: Int) {
    BUNDLED_OFFLINE(0),
    ECOSYSTEM_PACKAGE(10),
    OFFICIAL_ARTIFACT(20),
    OFFICIAL_INSTALLER(30),
    PINNED_SOURCE(40),
}

data class KiteResourceCardInstallMethodRule(
    val sourceType: String,
    val tier: KiteResourceCardInstallMethodTier,
    val requiresPinnedIdentity: Boolean,
    val runtimeMethodFallbackAllowed: Boolean = false,
)

object KiteResourceCardAuthoringPolicy {
    private val rules = listOf(
        rule("bundled", KiteResourceCardInstallMethodTier.BUNDLED_OFFLINE),
        rule("npm", KiteResourceCardInstallMethodTier.ECOSYSTEM_PACKAGE),
        rule("pypi", KiteResourceCardInstallMethodTier.ECOSYSTEM_PACKAGE),
        rule("apt", KiteResourceCardInstallMethodTier.ECOSYSTEM_PACKAGE),
        rule("github_release", KiteResourceCardInstallMethodTier.OFFICIAL_ARTIFACT, pinned = true),
        rule("official_release_archive", KiteResourceCardInstallMethodTier.OFFICIAL_ARTIFACT, pinned = true),
        rule("acp_registry_binary", KiteResourceCardInstallMethodTier.OFFICIAL_ARTIFACT, pinned = true),
        rule("android_apk", KiteResourceCardInstallMethodTier.OFFICIAL_ARTIFACT, pinned = true),
        rule("official_script", KiteResourceCardInstallMethodTier.OFFICIAL_INSTALLER),
        rule("git", KiteResourceCardInstallMethodTier.PINNED_SOURCE, pinned = true),
    ).associateBy(KiteResourceCardInstallMethodRule::sourceType)

    fun ruleFor(sourceType: String): KiteResourceCardInstallMethodRule? = rules[sourceType.trim()]

    fun prefer(leftSourceType: String, rightSourceType: String): String? {
        val left = ruleFor(leftSourceType) ?: return ruleFor(rightSourceType)?.sourceType
        val right = ruleFor(rightSourceType) ?: return left.sourceType
        return if (left.tier.priority <= right.tier.priority) left.sourceType else right.sourceType
    }

    private fun rule(
        sourceType: String,
        tier: KiteResourceCardInstallMethodTier,
        pinned: Boolean = false,
    ) = KiteResourceCardInstallMethodRule(
        sourceType = sourceType,
        tier = tier,
        requiresPinnedIdentity = pinned,
    )
}
