package com.kite.app.resources

/**
 * 资源卡在编写时选择安装方式的统一顺序。
 *
 * 这里不在运行时把一种安装方式切成另一种：包管理器、发布包、脚本和源码可能包含不同内容，
 * 运行时静默切换会破坏版本、校验和卸载合同。运行时只允许在同一种方式内切换下载源。
 */
enum class KiteResourceInstallMethodTier(val priority: Int) {
    BUNDLED_OFFLINE(0),
    ECOSYSTEM_PACKAGE(10),
    OFFICIAL_ARTIFACT(20),
    OFFICIAL_INSTALLER(30),
    PINNED_SOURCE(40),
}

data class KiteResourceInstallMethodRule(
    val sourceType: String,
    val tier: KiteResourceInstallMethodTier,
    val requiresPinnedIdentity: Boolean,
    val runtimeMethodFallbackAllowed: Boolean = false,
)

object KiteResourceInstallMethodPolicy {
    private val rules = listOf(
        rule("bundled", KiteResourceInstallMethodTier.BUNDLED_OFFLINE),
        rule("npm", KiteResourceInstallMethodTier.ECOSYSTEM_PACKAGE),
        rule("pypi", KiteResourceInstallMethodTier.ECOSYSTEM_PACKAGE),
        rule("apt", KiteResourceInstallMethodTier.ECOSYSTEM_PACKAGE),
        rule("github_release", KiteResourceInstallMethodTier.OFFICIAL_ARTIFACT, pinned = true),
        rule("official_release_archive", KiteResourceInstallMethodTier.OFFICIAL_ARTIFACT, pinned = true),
        rule("acp_registry_binary", KiteResourceInstallMethodTier.OFFICIAL_ARTIFACT, pinned = true),
        rule("android_apk", KiteResourceInstallMethodTier.OFFICIAL_ARTIFACT, pinned = true),
        rule("official_script", KiteResourceInstallMethodTier.OFFICIAL_INSTALLER),
        rule("git", KiteResourceInstallMethodTier.PINNED_SOURCE, pinned = true),
    ).associateBy(KiteResourceInstallMethodRule::sourceType)

    fun ruleFor(sourceType: String): KiteResourceInstallMethodRule? = rules[sourceType.trim()]

    fun prefer(leftSourceType: String, rightSourceType: String): String? {
        val left = ruleFor(leftSourceType) ?: return ruleFor(rightSourceType)?.sourceType
        val right = ruleFor(rightSourceType) ?: return left.sourceType
        return if (left.tier.priority <= right.tier.priority) left.sourceType else right.sourceType
    }

    private fun rule(
        sourceType: String,
        tier: KiteResourceInstallMethodTier,
        pinned: Boolean = false,
    ) = KiteResourceInstallMethodRule(
        sourceType = sourceType,
        tier = tier,
        requiresPinnedIdentity = pinned,
    )
}
