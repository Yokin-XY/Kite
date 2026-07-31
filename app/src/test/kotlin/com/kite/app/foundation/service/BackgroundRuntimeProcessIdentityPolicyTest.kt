package com.kite.app.foundation.service

import com.kite.app.foundation.runtime.HostProcessIdentityObservation
import org.junit.Assert.assertEquals
import org.junit.Test

class BackgroundRuntimeProcessIdentityPolicyTest {
    @Test
    fun `only exact boot pid and generation can attach or receive a signal`() {
        val decision = decide(persisted = identity(), observed = identity())

        assertEquals(BackgroundRuntimeProcessIdentityMatch.EXACT_GENERATION, decision.processMatch)
        assertEquals(BackgroundRuntimeRecoveryAction.ATTACH_EXACT_PROCESS, decision.recoveryAction)
        assertEquals(BackgroundRuntimeStopAction.SIGNAL_EXACT_PROCESS, decision.stopAction)
        assertEquals(0, decision.processStartsRequested)
    }

    @Test
    fun `same pid with a new generation cannot attach or receive a signal`() {
        val decision = decide(
            persisted = identity(),
            observed = identity(startTicks = 101L),
        )

        assertEquals(BackgroundRuntimeProcessIdentityMatch.PID_REUSED, decision.processMatch)
        assertEquals(BackgroundRuntimeRecoveryAction.REVIEW_WITHOUT_ATTACH, decision.recoveryAction)
        assertEquals(BackgroundRuntimeStopAction.CONFIRM_ORIGINAL_EXITED, decision.stopAction)
    }

    @Test
    fun `a different boot invalidates recovery without signalling the current process`() {
        val decision = decide(
            persisted = identity(),
            observed = identity(bootId = OTHER_BOOT_ID),
        )

        assertEquals(BackgroundRuntimeProcessIdentityMatch.BOOT_CHANGED, decision.processMatch)
        assertEquals(BackgroundRuntimeRecoveryAction.REVIEW_WITHOUT_ATTACH, decision.recoveryAction)
        assertEquals(BackgroundRuntimeStopAction.CONFIRM_ORIGINAL_EXITED, decision.stopAction)
    }

    @Test
    fun `a missing process confirms no running instance without requesting replacement`() {
        val decision = BackgroundRuntimeProcessIdentityPolicy.decide(
            persistedPid = PID,
            persistedIdentity = identity(),
            observation = BackgroundRuntimeProcessObservation.processNotFound(PID),
        )

        assertEquals(BackgroundRuntimeProcessIdentityMatch.PROCESS_NOT_FOUND, decision.processMatch)
        assertEquals(BackgroundRuntimeRecoveryAction.CONFIRM_NOT_RUNNING, decision.recoveryAction)
        assertEquals(BackgroundRuntimeStopAction.CONFIRM_ORIGINAL_EXITED, decision.stopAction)
        assertEquals(0, decision.processStartsRequested)
    }

    @Test
    fun `legacy pid without persisted identity remains review only`() {
        val decision = BackgroundRuntimeProcessIdentityPolicy.decide(
            persistedPid = PID,
            persistedIdentity = null,
            observation = BackgroundRuntimeProcessObservation.identityReady(identity()),
        )

        assertEquals(
            BackgroundRuntimeProcessIdentityMatch.PERSISTED_IDENTITY_UNAVAILABLE,
            decision.processMatch,
        )
        assertEquals(BackgroundRuntimeRecoveryAction.REVIEW_WITHOUT_ATTACH, decision.recoveryAction)
        assertEquals(BackgroundRuntimeStopAction.REVIEW_WITHOUT_SIGNAL, decision.stopAction)
    }

    @Test
    fun `ps fallback without observed generation cannot attach or signal`() {
        val decision = BackgroundRuntimeProcessIdentityPolicy.decide(
            persistedPid = PID,
            persistedIdentity = identity(),
            observation = BackgroundRuntimeProcessObservation.identityUnavailable(PID),
        )

        assertEquals(
            BackgroundRuntimeProcessIdentityMatch.OBSERVED_IDENTITY_UNAVAILABLE,
            decision.processMatch,
        )
        assertEquals(BackgroundRuntimeRecoveryAction.REVIEW_WITHOUT_ATTACH, decision.recoveryAction)
        assertEquals(BackgroundRuntimeStopAction.REVIEW_WITHOUT_SIGNAL, decision.stopAction)
    }

    @Test
    fun `record without a pid is already not running and never starts a process`() {
        val decision = BackgroundRuntimeProcessIdentityPolicy.decide(
            persistedPid = null,
            persistedIdentity = null,
            observation = null,
        )

        assertEquals(BackgroundRuntimeProcessIdentityMatch.NO_PERSISTED_PID, decision.processMatch)
        assertEquals(BackgroundRuntimeRecoveryAction.CONFIRM_NOT_RUNNING, decision.recoveryAction)
        assertEquals(BackgroundRuntimeStopAction.CONFIRM_ORIGINAL_EXITED, decision.stopAction)
        assertEquals(0, decision.processStartsRequested)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `observation for another pid is rejected before a decision is made`() {
        BackgroundRuntimeProcessIdentityPolicy.decide(
            persistedPid = PID,
            persistedIdentity = identity(),
            observation = BackgroundRuntimeProcessObservation.identityReady(identity(pid = PID + 1)),
        )
    }

    private fun decide(
        persisted: HostProcessIdentityObservation,
        observed: HostProcessIdentityObservation,
    ) = BackgroundRuntimeProcessIdentityPolicy.decide(
        persistedPid = persisted.hostPid,
        persistedIdentity = persisted,
        observation = BackgroundRuntimeProcessObservation.identityReady(observed),
    )

    private fun identity(
        bootId: String = BOOT_ID,
        pid: Int = PID,
        startTicks: Long = 100L,
    ) = HostProcessIdentityObservation(
        bootId = bootId,
        hostPid = pid,
        processStartTicks = startTicks,
    )

    private companion object {
        const val PID = 321
        const val BOOT_ID = "123e4567-e89b-12d3-a456-426614174000"
        const val OTHER_BOOT_ID = "123e4567-e89b-12d3-a456-426614174001"
    }
}
