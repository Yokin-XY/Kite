package com.kite.app.application.onboarding

import com.kite.app.application.runtimebootstrap.RuntimePermissionKind

internal enum class FirstRunOnboardingPhase {
    NotStarted,
    AwaitingRuntimePermissionResult,
    AwaitingAllFilesReturn,
    AwaitingNotificationSettingsReturn,
    Completed
}

internal data class FirstRunOnboardingFacts(
    val missingRuntimePermissions: Set<RuntimePermissionKind> = emptySet(),
    val needsAllFilesAccess: Boolean = false,
    val needsNotificationChannelSetup: Boolean = false
)

internal data class FirstRunOnboardingState(
    val phase: FirstRunOnboardingPhase,
    val active: Boolean,
    val missingRuntimePermissions: Set<RuntimePermissionKind>,
    val needsAllFilesAccess: Boolean,
    val needsNotificationChannelSetup: Boolean
)

internal sealed interface FirstRunOnboardingEffect {
    data class RequestRuntimePermissions(
        val permissions: Set<RuntimePermissionKind>
    ) : FirstRunOnboardingEffect

    data object OpenAllFilesSettings : FirstRunOnboardingEffect
    data object OpenRunNotificationSettings : FirstRunOnboardingEffect
}

internal data class FirstRunOnboardingTransition(
    val state: FirstRunOnboardingState,
    val effect: FirstRunOnboardingEffect? = null
)

internal interface FirstRunOnboardingStore {
    fun readPhase(): FirstRunOnboardingPhase
    fun writePhase(phase: FirstRunOnboardingPhase)
}

/**
 * 首次权限引导的进程级状态机。运行环境是否就绪仍由 RuntimeBootstrapGateway 负责。
 * 外部动作发出前先持久化等待阶段，因此 Activity 或进程重建不会重复弹出系统页面。
 */
internal class FirstRunOnboardingCoordinator(
    private val store: FirstRunOnboardingStore
) {
    private var runtimePermissionRequestInFlight = false
    private var allFilesSettingsInFlight = false
    private var hostPausedForAllFilesSettings = false
    private var notificationSettingsInFlight = false
    private var hostPausedForNotificationSettings = false

    fun startOrRecover(facts: FirstRunOnboardingFacts): FirstRunOnboardingTransition =
        when (store.readPhase()) {
            FirstRunOnboardingPhase.NotStarted -> startNextStep(facts)
            FirstRunOnboardingPhase.AwaitingRuntimePermissionResult -> {
                if (runtimePermissionRequestInFlight) transition(facts) else afterRuntimePermissionAttempt(facts)
            }
            FirstRunOnboardingPhase.AwaitingAllFilesReturn -> {
                if (allFilesSettingsInFlight) transition(facts) else afterAllFilesAttempt(facts)
            }
            FirstRunOnboardingPhase.AwaitingNotificationSettingsReturn -> {
                if (notificationSettingsInFlight) transition(facts) else complete(facts)
            }
            FirstRunOnboardingPhase.Completed -> transition(facts)
        }

    fun onHostPaused() {
        if (allFilesSettingsInFlight) hostPausedForAllFilesSettings = true
        if (notificationSettingsInFlight) hostPausedForNotificationSettings = true
    }

    fun onHostResumed(facts: FirstRunOnboardingFacts): FirstRunOnboardingTransition {
        if (
            store.readPhase() == FirstRunOnboardingPhase.AwaitingAllFilesReturn &&
            allFilesSettingsInFlight &&
            hostPausedForAllFilesSettings
        ) {
            allFilesSettingsInFlight = false
            hostPausedForAllFilesSettings = false
            return afterAllFilesAttempt(facts)
        }
        if (
            store.readPhase() == FirstRunOnboardingPhase.AwaitingNotificationSettingsReturn &&
            notificationSettingsInFlight &&
            hostPausedForNotificationSettings
        ) {
            notificationSettingsInFlight = false
            hostPausedForNotificationSettings = false
            return complete(facts)
        }
        return startOrRecover(facts)
    }

    fun onRuntimePermissionResult(facts: FirstRunOnboardingFacts): FirstRunOnboardingTransition {
        runtimePermissionRequestInFlight = false
        return if (store.readPhase() == FirstRunOnboardingPhase.AwaitingRuntimePermissionResult) {
            afterRuntimePermissionAttempt(facts)
        } else {
            startOrRecover(facts)
        }
    }

    fun continueNow(facts: FirstRunOnboardingFacts): FirstRunOnboardingTransition =
        when (store.readPhase()) {
            FirstRunOnboardingPhase.NotStarted -> startNextStep(facts)
            FirstRunOnboardingPhase.AwaitingRuntimePermissionResult -> when {
                facts.missingRuntimePermissions.isEmpty() -> afterRuntimePermissionAttempt(facts)
                runtimePermissionRequestInFlight -> transition(facts)
                else -> requestRuntimePermissions(facts)
            }
            FirstRunOnboardingPhase.AwaitingAllFilesReturn -> when {
                !facts.needsAllFilesAccess -> afterAllFilesAttempt(facts)
                allFilesSettingsInFlight -> transition(facts)
                else -> openAllFilesSettings(facts)
            }
            FirstRunOnboardingPhase.AwaitingNotificationSettingsReturn -> when {
                !facts.needsNotificationChannelSetup -> complete(facts)
                notificationSettingsInFlight -> transition(facts)
                else -> openNotificationSettings(facts)
            }
            FirstRunOnboardingPhase.Completed -> transition(facts)
        }

    private fun startNextStep(facts: FirstRunOnboardingFacts): FirstRunOnboardingTransition = when {
        facts.missingRuntimePermissions.isNotEmpty() -> requestRuntimePermissions(facts)
        facts.needsAllFilesAccess -> openAllFilesSettings(facts)
        facts.needsNotificationChannelSetup -> openNotificationSettings(facts)
        else -> complete(facts)
    }

    private fun afterRuntimePermissionAttempt(
        facts: FirstRunOnboardingFacts
    ): FirstRunOnboardingTransition = when {
        facts.needsAllFilesAccess -> openAllFilesSettings(facts)
        facts.needsNotificationChannelSetup -> openNotificationSettings(facts)
        else -> complete(facts)
    }

    private fun afterAllFilesAttempt(facts: FirstRunOnboardingFacts): FirstRunOnboardingTransition =
        if (facts.needsNotificationChannelSetup) openNotificationSettings(facts) else complete(facts)

    private fun requestRuntimePermissions(
        facts: FirstRunOnboardingFacts
    ): FirstRunOnboardingTransition {
        store.writePhase(FirstRunOnboardingPhase.AwaitingRuntimePermissionResult)
        runtimePermissionRequestInFlight = true
        return transition(
            facts,
            FirstRunOnboardingEffect.RequestRuntimePermissions(facts.missingRuntimePermissions)
        )
    }

    private fun openAllFilesSettings(facts: FirstRunOnboardingFacts): FirstRunOnboardingTransition {
        store.writePhase(FirstRunOnboardingPhase.AwaitingAllFilesReturn)
        allFilesSettingsInFlight = true
        hostPausedForAllFilesSettings = false
        return transition(facts, FirstRunOnboardingEffect.OpenAllFilesSettings)
    }

    private fun openNotificationSettings(facts: FirstRunOnboardingFacts): FirstRunOnboardingTransition {
        store.writePhase(FirstRunOnboardingPhase.AwaitingNotificationSettingsReturn)
        notificationSettingsInFlight = true
        hostPausedForNotificationSettings = false
        return transition(facts, FirstRunOnboardingEffect.OpenRunNotificationSettings)
    }

    private fun complete(facts: FirstRunOnboardingFacts): FirstRunOnboardingTransition {
        store.writePhase(FirstRunOnboardingPhase.Completed)
        runtimePermissionRequestInFlight = false
        allFilesSettingsInFlight = false
        hostPausedForAllFilesSettings = false
        notificationSettingsInFlight = false
        hostPausedForNotificationSettings = false
        return transition(facts)
    }

    private fun transition(
        facts: FirstRunOnboardingFacts,
        effect: FirstRunOnboardingEffect? = null
    ): FirstRunOnboardingTransition {
        val phase = store.readPhase()
        return FirstRunOnboardingTransition(
            state = FirstRunOnboardingState(
                phase = phase,
                active = phase != FirstRunOnboardingPhase.Completed,
                missingRuntimePermissions = facts.missingRuntimePermissions,
                needsAllFilesAccess = facts.needsAllFilesAccess,
                needsNotificationChannelSetup = facts.needsNotificationChannelSetup
            ),
            effect = effect
        )
    }
}
