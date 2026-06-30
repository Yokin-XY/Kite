package com.kite.app.foundation.runtime

data class RuntimeLifecycleDefaultStrategy(
    val name: String = "standard",
    val mode: RuntimeLifecycleStrategyActivationMode =
        RuntimeLifecycleStrategyActivationMode.LEASE_RECLAIM_ONLY,
    val allowLeaseReclaim: Boolean = true,
    val allowTemporaryReclaim: Boolean = true,
    val allowEphemeralReclaim: Boolean = true,
    val allowUserLocked: Boolean = false,
    val allowCore: Boolean = false,
    val allowProotCore: Boolean = false,
    val allowUnmanaged: Boolean = false,
    val allowQuarantine: Boolean = false,
    val allowCoreRecovery: Boolean = false,
    val allowProotCoreRecovery: Boolean = false,
    val foregroundDowngradeSupported: Boolean = true
) {
    fun toEnvText(lastGateReason: String = "none"): String {
        return buildString {
            appendLine("runtime_lifecycle_default_strategy_name=${name.toDefaultStrategyEnvValue()}")
            appendLine("runtime_lifecycle_default_strategy_mode=${mode.name.lowercase()}")
            appendLine("runtime_lifecycle_default_strategy_allow_lease_reclaim=$allowLeaseReclaim")
            appendLine("runtime_lifecycle_default_strategy_allow_temporary_reclaim=$allowTemporaryReclaim")
            appendLine("runtime_lifecycle_default_strategy_allow_ephemeral_reclaim=$allowEphemeralReclaim")
            appendLine("runtime_lifecycle_default_strategy_allow_user_locked=$allowUserLocked")
            appendLine("runtime_lifecycle_default_strategy_allow_core=$allowCore")
            appendLine("runtime_lifecycle_default_strategy_allow_proot_core=$allowProotCore")
            appendLine("runtime_lifecycle_default_strategy_allow_unmanaged=$allowUnmanaged")
            appendLine("runtime_lifecycle_default_strategy_allow_quarantine=$allowQuarantine")
            appendLine("runtime_lifecycle_default_strategy_allow_core_recovery=$allowCoreRecovery")
            appendLine("runtime_lifecycle_default_strategy_allow_proot_core_recovery=$allowProotCoreRecovery")
            appendLine("runtime_lifecycle_default_strategy_foreground_downgrade_supported=$foregroundDowngradeSupported")
            appendLine("runtime_lifecycle_default_strategy_last_gate_reason=${lastGateReason.toDefaultStrategyEnvValue()}")
        }
    }

    fun blocksTier(tier: RuntimeLifecycleAuthorityTier): Boolean {
        return when (tier) {
            RuntimeLifecycleAuthorityTier.SYSTEM_CORE -> !allowCore
            RuntimeLifecycleAuthorityTier.PROOT_CORE -> !allowProotCore
            RuntimeLifecycleAuthorityTier.USER_LOCKED -> !allowUserLocked
            RuntimeLifecycleAuthorityTier.FOREGROUND -> !foregroundDowngradeSupported
            RuntimeLifecycleAuthorityTier.UNMANAGED -> !allowUnmanaged
            RuntimeLifecycleAuthorityTier.QUARANTINE -> !allowQuarantine
            RuntimeLifecycleAuthorityTier.PROOT_ELASTIC -> true
            RuntimeLifecycleAuthorityTier.LEASE -> false
        }
    }

    fun allowsWorkload(item: RuntimeLifecycleReclaimItem): Boolean {
        val leaseAllowed = item.retention == RuntimeWorkloadRetention.LEASE && allowLeaseReclaim
        val temporaryAllowed = item.workloadClass == RuntimeWorkloadClass.BUILD ||
            item.workloadClass == RuntimeWorkloadClass.PROBE
        val ephemeralAllowed = item.workloadClass == RuntimeWorkloadClass.EPHEMERAL
        return leaseAllowed ||
            (temporaryAllowed && allowTemporaryReclaim) ||
            (ephemeralAllowed && allowEphemeralReclaim)
    }

    companion object {
        fun standard(): RuntimeLifecycleDefaultStrategy = RuntimeLifecycleDefaultStrategy()
    }
}

private fun String?.toDefaultStrategyEnvValue(): String {
    if (this.isNullOrBlank()) return "none"
    return trim()
        .replace(Regex("[\\r\\n\\t ]+"), "_")
        .replace(Regex("[^A-Za-z0-9._:@/+,-]"), "_")
        .take(240)
}
