package com.kftest.app.foundation.runtime

import com.kftest.app.foundation.service.BackgroundRuntimeRecord
import com.kftest.app.foundation.service.RuntimeRetentionClass

enum class RuntimeStartSource(
    val label: String,
    val manualBypass: Boolean = false
) {
    MANUAL_START("manual_start", manualBypass = true),
    MANUAL_RESTART("manual_restart", manualBypass = true),
    RESIDENT_POLICY("resident_policy"),
    AUTO_RESTART("auto_restart"),
    CORE_ENSURE("core_ensure"),
    UNKNOWN("unknown");

    companion object {
        fun fromRecoverySource(recoverySource: String?): RuntimeStartSource {
            return when (recoverySource?.trim()?.lowercase()) {
                null, "" -> MANUAL_START
                "manual_restart" -> MANUAL_RESTART
                "resident_policy" -> RESIDENT_POLICY
                "auto_restart" -> AUTO_RESTART
                "core_ensure" -> CORE_ENSURE
                else -> UNKNOWN
            }
        }
    }
}

enum class RuntimeAdmissionAction {
    ALLOW,
    DEFER
}

data class RuntimeAdmissionDecision(
    val action: RuntimeAdmissionAction,
    val startSource: RuntimeStartSource,
    val pressureLevel: RuntimePressureLevel,
    val profile: RuntimeReclaimerProfile,
    val summary: String
) {
    val allowed: Boolean
        get() = action == RuntimeAdmissionAction.ALLOW
}

object RuntimeAdmissionGuard {

    fun evaluate(
        record: BackgroundRuntimeRecord,
        pressure: RuntimePressureSnapshot,
        policy: RuntimeReclaimerPolicy,
        startSource: RuntimeStartSource
    ): RuntimeAdmissionDecision {
        val profile = policy.activeProfile
        val pressureLevel = pressure.level
        if (startSource.manualBypass) {
            return allow(startSource, pressureLevel, profile, "manual_bypass")
        }
        if (record.retentionClass == RuntimeRetentionClass.CRITICAL_CORE) {
            return allow(startSource, pressureLevel, profile, "critical_core")
        }
        if (record.retentionClass.resident || record.retentionClass == RuntimeRetentionClass.INTERACTIVE) {
            return allow(startSource, pressureLevel, profile, "protected_retention=${record.retentionClass.name}")
        }
        if (pressureLevel == RuntimePressureLevel.UNKNOWN || pressureLevel == RuntimePressureLevel.NORMAL) {
            return allow(startSource, pressureLevel, profile, "pressure=$pressureLevel")
        }
        if (profile == RuntimeReclaimerProfile.OBSERVE_ONLY) {
            return allow(startSource, pressureLevel, profile, "observe_only_profile")
        }

        val shouldDefer = when (record.retentionClass) {
            RuntimeRetentionClass.EPHEMERAL -> when (profile) {
                RuntimeReclaimerProfile.CONSERVATIVE -> pressureLevel == RuntimePressureLevel.CRITICAL
                RuntimeReclaimerProfile.BALANCED,
                RuntimeReclaimerProfile.AGGRESSIVE -> pressureLevel.ordinal >= RuntimePressureLevel.HIGH.ordinal
                RuntimeReclaimerProfile.OBSERVE_ONLY -> false
            }

            RuntimeRetentionClass.BATCH -> when (profile) {
                RuntimeReclaimerProfile.CONSERVATIVE,
                RuntimeReclaimerProfile.BALANCED -> pressureLevel == RuntimePressureLevel.CRITICAL
                RuntimeReclaimerProfile.AGGRESSIVE -> pressureLevel.ordinal >= RuntimePressureLevel.HIGH.ordinal
                RuntimeReclaimerProfile.OBSERVE_ONLY -> false
            }

            else -> false
        }

        return if (shouldDefer) {
            RuntimeAdmissionDecision(
                action = RuntimeAdmissionAction.DEFER,
                startSource = startSource,
                pressureLevel = pressureLevel,
                profile = profile,
                summary = buildString {
                    append("deferred_by_pressure")
                    append(" source=").append(startSource.name)
                    append(" profile=").append(profile.name)
                    append(" pressure=").append(pressureLevel.name)
                    append(" retention=").append(record.retentionClass.name)
                    append(" totalRssKb=").append(pressure.totalRssKb)
                    append(" reclaimableRssKb=").append(pressure.reclaimableRssKb)
                }
            )
        } else {
            allow(
                startSource,
                pressureLevel,
                profile,
                "pressure=${pressureLevel.name} retention=${record.retentionClass.name}"
            )
        }
    }

    private fun allow(
        startSource: RuntimeStartSource,
        pressureLevel: RuntimePressureLevel,
        profile: RuntimeReclaimerProfile,
        reason: String
    ): RuntimeAdmissionDecision {
        return RuntimeAdmissionDecision(
            action = RuntimeAdmissionAction.ALLOW,
            startSource = startSource,
            pressureLevel = pressureLevel,
            profile = profile,
            summary = "allowed source=${startSource.name} profile=${profile.name} $reason"
        )
    }
}
