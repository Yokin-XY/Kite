package com.kite.app.foundation.service

import com.kite.app.foundation.runtime.HostProcessIdentityObservation

internal enum class BackgroundRuntimeProcessObservationState {
    PROCESS_NOT_FOUND,
    IDENTITY_UNAVAILABLE,
    IDENTITY_READY,
}

/** 对持久 PID 的单次宿主观察；不把命令 token、端口或健康检查伪装成进程身份。 */
internal data class BackgroundRuntimeProcessObservation(
    val state: BackgroundRuntimeProcessObservationState,
    val observedPid: Int,
    val identity: HostProcessIdentityObservation? = null,
) {
    init {
        require(observedPid > 0) { "background_runtime_observed_pid_invalid" }
        require((state == BackgroundRuntimeProcessObservationState.IDENTITY_READY) == (identity != null)) {
            "background_runtime_observation_identity_state_invalid"
        }
        require(identity == null || identity.hostPid == observedPid) {
            "background_runtime_observation_pid_mismatch"
        }
    }

    companion object {
        fun processNotFound(pid: Int) = BackgroundRuntimeProcessObservation(
            state = BackgroundRuntimeProcessObservationState.PROCESS_NOT_FOUND,
            observedPid = pid,
        )

        fun identityUnavailable(pid: Int) = BackgroundRuntimeProcessObservation(
            state = BackgroundRuntimeProcessObservationState.IDENTITY_UNAVAILABLE,
            observedPid = pid,
        )

        fun identityReady(identity: HostProcessIdentityObservation) =
            BackgroundRuntimeProcessObservation(
                state = BackgroundRuntimeProcessObservationState.IDENTITY_READY,
                observedPid = identity.hostPid,
                identity = identity,
            )
    }
}

internal enum class BackgroundRuntimeProcessIdentityMatch {
    NO_PERSISTED_PID,
    PROCESS_NOT_FOUND,
    PERSISTED_IDENTITY_UNAVAILABLE,
    OBSERVED_IDENTITY_UNAVAILABLE,
    EXACT_GENERATION,
    BOOT_CHANGED,
    PID_REUSED,
}

internal enum class BackgroundRuntimeRecoveryAction {
    ATTACH_EXACT_PROCESS,
    CONFIRM_NOT_RUNNING,
    REVIEW_WITHOUT_ATTACH,
}

internal enum class BackgroundRuntimeStopAction {
    SIGNAL_EXACT_PROCESS,
    CONFIRM_ORIGINAL_EXITED,
    REVIEW_WITHOUT_SIGNAL,
}

internal data class BackgroundRuntimeProcessIdentityDecision(
    val processMatch: BackgroundRuntimeProcessIdentityMatch,
    val recoveryAction: BackgroundRuntimeRecoveryAction,
    val stopAction: BackgroundRuntimeStopAction,
    val processStartsRequested: Int = 0,
) {
    init {
        require(processStartsRequested == 0) {
            "background_runtime_identity_decision_must_not_start_process"
        }
    }
}

/**
 * 后台 PROCESS 的纯身份决策。调用方负责读取 `/proc`，本类不读取状态、不 attach、
 * 不创建或停止进程。恢复与停止共用同一比较结果，避免两条链各自解释 PID。
 */
internal object BackgroundRuntimeProcessIdentityPolicy {
    fun decide(
        persistedPid: Int?,
        persistedIdentity: HostProcessIdentityObservation?,
        observation: BackgroundRuntimeProcessObservation?,
    ): BackgroundRuntimeProcessIdentityDecision {
        val expectedPid = persistedPid?.takeIf { it > 0 }
        if (expectedPid == null) {
            require(persistedIdentity == null && observation == null) {
                "background_runtime_identity_without_persisted_pid"
            }
            return decision(BackgroundRuntimeProcessIdentityMatch.NO_PERSISTED_PID)
        }
        require(persistedIdentity == null || persistedIdentity.hostPid == expectedPid) {
            "background_runtime_persisted_identity_pid_mismatch"
        }
        require(observation != null && observation.observedPid == expectedPid) {
            "background_runtime_observation_expected_pid_mismatch"
        }

        val processMatch = when {
            observation.state == BackgroundRuntimeProcessObservationState.PROCESS_NOT_FOUND ->
                BackgroundRuntimeProcessIdentityMatch.PROCESS_NOT_FOUND
            persistedIdentity == null ->
                BackgroundRuntimeProcessIdentityMatch.PERSISTED_IDENTITY_UNAVAILABLE
            observation.state == BackgroundRuntimeProcessObservationState.IDENTITY_UNAVAILABLE ->
                BackgroundRuntimeProcessIdentityMatch.OBSERVED_IDENTITY_UNAVAILABLE
            observation.identity?.bootId != persistedIdentity.bootId ->
                BackgroundRuntimeProcessIdentityMatch.BOOT_CHANGED
            observation.identity.processStartTicks != persistedIdentity.processStartTicks ->
                BackgroundRuntimeProcessIdentityMatch.PID_REUSED
            else -> BackgroundRuntimeProcessIdentityMatch.EXACT_GENERATION
        }
        return decision(processMatch)
    }

    private fun decision(
        processMatch: BackgroundRuntimeProcessIdentityMatch,
    ): BackgroundRuntimeProcessIdentityDecision {
        val recoveryAction = when (processMatch) {
            BackgroundRuntimeProcessIdentityMatch.EXACT_GENERATION ->
                BackgroundRuntimeRecoveryAction.ATTACH_EXACT_PROCESS
            BackgroundRuntimeProcessIdentityMatch.NO_PERSISTED_PID,
            BackgroundRuntimeProcessIdentityMatch.PROCESS_NOT_FOUND ->
                BackgroundRuntimeRecoveryAction.CONFIRM_NOT_RUNNING
            BackgroundRuntimeProcessIdentityMatch.PERSISTED_IDENTITY_UNAVAILABLE,
            BackgroundRuntimeProcessIdentityMatch.OBSERVED_IDENTITY_UNAVAILABLE,
            BackgroundRuntimeProcessIdentityMatch.BOOT_CHANGED,
            BackgroundRuntimeProcessIdentityMatch.PID_REUSED ->
                BackgroundRuntimeRecoveryAction.REVIEW_WITHOUT_ATTACH
        }
        val stopAction = when (processMatch) {
            BackgroundRuntimeProcessIdentityMatch.EXACT_GENERATION ->
                BackgroundRuntimeStopAction.SIGNAL_EXACT_PROCESS
            BackgroundRuntimeProcessIdentityMatch.NO_PERSISTED_PID,
            BackgroundRuntimeProcessIdentityMatch.PROCESS_NOT_FOUND,
            BackgroundRuntimeProcessIdentityMatch.BOOT_CHANGED,
            BackgroundRuntimeProcessIdentityMatch.PID_REUSED ->
                BackgroundRuntimeStopAction.CONFIRM_ORIGINAL_EXITED
            BackgroundRuntimeProcessIdentityMatch.PERSISTED_IDENTITY_UNAVAILABLE,
            BackgroundRuntimeProcessIdentityMatch.OBSERVED_IDENTITY_UNAVAILABLE ->
                BackgroundRuntimeStopAction.REVIEW_WITHOUT_SIGNAL
        }
        return BackgroundRuntimeProcessIdentityDecision(
            processMatch = processMatch,
            recoveryAction = recoveryAction,
            stopAction = stopAction,
        )
    }
}

/** 健康命令只能证明服务响应，不能让失效的 owner PID 重新进入记录。 */
internal fun selectRefreshedBackgroundRuntimePid(
    localHandleAlive: Boolean,
    localHandlePid: Int?,
    exactExternalProcessAlive: Boolean,
    exactExternalPid: Int?,
    withinStartingGrace: Boolean,
    persistedPid: Int?,
): Int? = when {
    localHandleAlive -> localHandlePid ?: exactExternalPid ?: persistedPid
    exactExternalProcessAlive -> exactExternalPid
    withinStartingGrace -> localHandlePid ?: persistedPid
    else -> null
}

internal fun selectRefreshedBackgroundRuntimeStatus(
    currentStatus: BackgroundRuntimeStatus,
    localHandleAlive: Boolean,
    externalServiceAlive: Boolean,
    withinStartingGrace: Boolean,
    expectedStopPending: Boolean,
    identityReview: Boolean,
    originalProcessGone: Boolean,
): BackgroundRuntimeStatus = when {
    localHandleAlive -> BackgroundRuntimeStatus.RUNNING
    expectedStopPending && originalProcessGone -> BackgroundRuntimeStatus.STOPPED
    expectedStopPending && identityReview -> currentStatus
    externalServiceAlive -> BackgroundRuntimeStatus.RUNNING
    withinStartingGrace -> BackgroundRuntimeStatus.STARTING
    identityReview -> currentStatus
    currentStatus.isActiveStatus() -> BackgroundRuntimeStatus.STOPPED
    else -> currentStatus
}
