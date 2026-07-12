package com.kite.app.feature.runtimebootstrap

import com.kite.app.application.runtimebootstrap.RuntimeBootstrapGateway
import com.kite.app.application.runtimebootstrap.RuntimeBootstrapSnapshot
import com.kite.app.application.runtimemanagement.RuntimeManagementDispatchResult
import com.kite.app.application.runtimemanagement.RuntimeManagementGateway
import com.kite.app.application.runtimemanagement.RuntimeManagementSnapshot
import com.kite.app.application.runtimemanagement.RuntimeManagedProcess
import com.kite.app.application.runtimemanagement.RuntimeManagedTerminal
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RuntimeStatusFeatureControllerTest {
    @Test
    fun combinesBootstrapAndManagementFactsWithoutReadingStores() {
        val dispatcher = StandardTestDispatcher()
        val scope = TestScope(dispatcher)
        val bootstrap = FakeBootstrapGateway(
            RuntimeBootstrapSnapshot(
                readinessProbeCompleted = true,
                baseImageReady = true,
                defaultContainerReady = true,
                bootstrapResourcesSettled = true
            )
        )
        val management = FakeManagementGateway(
            RuntimeManagementSnapshot(
                terminals = listOf(RuntimeManagedTerminal("terminal-1", "T", "运行中", isLive = true)),
                processes = listOf(RuntimeManagedProcess("p1", 10, title = "p", stateLabel = "运行中")),
                observedProcessCount = 4
            )
        )
        val controller = RuntimeStatusFeatureController(bootstrap, management, scope)

        scope.advanceUntilIdle()

        assertEquals(1, controller.state.value.counts.runningTerminals)
        assertEquals(4, controller.state.value.counts.runningProcesses)
        assertEquals("就绪", controller.state.value.statusLabel)
    }

    @Test
    fun retryDelegatesToBootstrapGatewayInsteadOfWritingUiSuccess() {
        val dispatcher = StandardTestDispatcher()
        val scope = TestScope(dispatcher)
        val bootstrap = FakeBootstrapGateway(
            RuntimeBootstrapSnapshot(readinessProbeCompleted = true)
        )
        val controller = RuntimeStatusFeatureController(
            bootstrap,
            FakeManagementGateway(RuntimeManagementSnapshot()),
            scope
        )
        scope.advanceUntilIdle()

        val effect = controller.submitPrimaryAction()

        assertNull(effect)
        assertEquals(1, bootstrap.ensureReadyCount)
    }

    @Test
    fun onboardingActionRemainsAnExplicitShellEffect() {
        val dispatcher = StandardTestDispatcher()
        val scope = TestScope(dispatcher)
        val controller = RuntimeStatusFeatureController(
            FakeBootstrapGateway(RuntimeBootstrapSnapshot()),
            FakeManagementGateway(RuntimeManagementSnapshot()),
            scope
        )
        controller.updateOnboarding(RuntimePermissionOnboardingUiInput(active = true))
        scope.advanceUntilIdle()

        assertEquals(
            RuntimeStatusFeatureEffect.ContinueFirstRunPermissionOnboarding,
            controller.submitPrimaryAction()
        )
    }

    @Test
    fun refreshCalibratesBootstrapAndManagementGatewaysTogether() {
        val dispatcher = StandardTestDispatcher()
        val scope = TestScope(dispatcher)
        val bootstrap = FakeBootstrapGateway(RuntimeBootstrapSnapshot())
        val management = FakeManagementGateway(RuntimeManagementSnapshot())
        val controller = RuntimeStatusFeatureController(bootstrap, management, scope)

        controller.refresh()

        assertEquals(1, bootstrap.refreshCount)
        assertEquals(1, management.refreshCount)
        assertEquals(true, management.lastRefreshForce)
    }

    private class FakeBootstrapGateway(initial: RuntimeBootstrapSnapshot) : RuntimeBootstrapGateway {
        override val snapshots = MutableStateFlow(initial)
        var ensureReadyCount = 0
        var refreshCount = 0

        override fun currentSnapshot(): RuntimeBootstrapSnapshot = snapshots.value
        override fun refresh() {
            refreshCount += 1
        }
        override fun ensureReady() {
            ensureReadyCount += 1
        }
    }

    private class FakeManagementGateway(initial: RuntimeManagementSnapshot) : RuntimeManagementGateway {
        override val snapshots = MutableStateFlow(initial)
        var refreshCount = 0
        var lastRefreshForce = false

        override fun currentSnapshot(): RuntimeManagementSnapshot = snapshots.value
        override fun refresh(force: Boolean) {
            refreshCount += 1
            lastRefreshForce = force
        }
        override suspend fun endTerminal(sessionId: String): RuntimeManagementDispatchResult =
            RuntimeManagementDispatchResult.accepted()
        override suspend fun endProcess(processId: String, pid: Int): RuntimeManagementDispatchResult =
            RuntimeManagementDispatchResult.accepted()
        override suspend fun stopBackgroundRuntime(runtimeId: String): RuntimeManagementDispatchResult =
            RuntimeManagementDispatchResult.accepted()
        override suspend fun restartBackgroundRuntime(runtimeId: String): RuntimeManagementDispatchResult =
            RuntimeManagementDispatchResult.accepted()
    }
}
