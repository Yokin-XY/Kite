package com.kite.app.application.onboarding

import com.kite.app.application.runtimebootstrap.RuntimePermissionKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FirstRunOnboardingCoordinatorTest {
    @Test
    fun `fresh onboarding requests runtime permissions once`() {
        val store = FakeStore()
        val coordinator = FirstRunOnboardingCoordinator(store)
        val facts = facts(missing = setOf(RuntimePermissionKind.Notifications))

        val first = coordinator.startOrRecover(facts)
        val second = coordinator.startOrRecover(facts)

        assertEquals(FirstRunOnboardingPhase.AwaitingRuntimePermissionResult, store.phase)
        assertEquals(
            FirstRunOnboardingEffect.RequestRuntimePermissions(facts.missingRuntimePermissions),
            first.effect
        )
        assertNull(second.effect)
        assertTrue(second.state.active)
    }

    @Test
    fun `permission denial finishes one-time attempt without hiding missing fact`() {
        val store = FakeStore()
        val coordinator = FirstRunOnboardingCoordinator(store)
        val denied = facts(missing = setOf(RuntimePermissionKind.FileRead))
        coordinator.startOrRecover(denied)

        val result = coordinator.onRuntimePermissionResult(denied)

        assertEquals(FirstRunOnboardingPhase.Completed, store.phase)
        assertFalse(result.state.active)
        assertEquals(denied.missingRuntimePermissions, result.state.missingRuntimePermissions)
        assertNull(result.effect)
    }

    @Test
    fun `all-files settings completes only after host leaves and returns`() {
        val store = FakeStore()
        val coordinator = FirstRunOnboardingCoordinator(store)
        val needsAccess = facts(needsAllFiles = true)

        val launch = coordinator.startOrRecover(needsAccess)
        val prematureResume = coordinator.onHostResumed(needsAccess)
        coordinator.onHostPaused()
        val returned = coordinator.onHostResumed(facts())

        assertEquals(FirstRunOnboardingEffect.OpenAllFilesSettings, launch.effect)
        assertTrue(prematureResume.state.active)
        assertEquals(FirstRunOnboardingPhase.Completed, store.phase)
        assertFalse(returned.state.active)
    }

    @Test
    fun `process replacement after runtime prompt advances without repeating prompt`() {
        val store = FakeStore()
        val firstProcess = FirstRunOnboardingCoordinator(store)
        firstProcess.startOrRecover(
            facts(
                missing = setOf(RuntimePermissionKind.Notifications),
                needsAllFiles = true
            )
        )

        val recovered = FirstRunOnboardingCoordinator(store).startOrRecover(
            facts(
                missing = setOf(RuntimePermissionKind.Notifications),
                needsAllFiles = true
            )
        )

        assertEquals(FirstRunOnboardingEffect.OpenAllFilesSettings, recovered.effect)
        assertEquals(FirstRunOnboardingPhase.AwaitingAllFilesReturn, store.phase)
    }

    @Test
    fun `process replacement after settings launch completes without reopening settings`() {
        val store = FakeStore()
        FirstRunOnboardingCoordinator(store).startOrRecover(facts(needsAllFiles = true))

        val recovered = FirstRunOnboardingCoordinator(store).startOrRecover(facts(needsAllFiles = true))

        assertEquals(FirstRunOnboardingPhase.Completed, store.phase)
        assertFalse(recovered.state.active)
        assertNull(recovered.effect)
        assertTrue(recovered.state.needsAllFilesAccess)
    }

    @Test
    fun `ready first install completes without system effects`() {
        val store = FakeStore()

        val result = FirstRunOnboardingCoordinator(store).startOrRecover(facts())

        assertEquals(FirstRunOnboardingPhase.Completed, store.phase)
        assertFalse(result.state.active)
        assertNull(result.effect)
    }

    private fun facts(
        missing: Set<RuntimePermissionKind> = emptySet(),
        needsAllFiles: Boolean = false
    ) = FirstRunOnboardingFacts(
        missingRuntimePermissions = missing,
        needsAllFilesAccess = needsAllFiles
    )

    private class FakeStore(
        initial: FirstRunOnboardingPhase = FirstRunOnboardingPhase.NotStarted
    ) : FirstRunOnboardingStore {
        var phase: FirstRunOnboardingPhase = initial
            private set

        override fun readPhase(): FirstRunOnboardingPhase = phase

        override fun writePhase(phase: FirstRunOnboardingPhase) {
            this.phase = phase
        }
    }
}
