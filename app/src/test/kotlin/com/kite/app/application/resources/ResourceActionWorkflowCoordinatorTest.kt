package com.kite.app.application.resources

import com.kite.app.action.KiteResourceActionIntent
import com.kite.app.action.KiteResourceActionRequest
import com.kite.app.action.KiteResourceActionSource
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ResourceActionWorkflowCoordinatorTest {
    @Test
    fun `stable resource intents dispatch to one gateway path`() = runTest {
        val gateway = FakeGateway()
        val coordinator = ResourceActionWorkflowCoordinator(gateway)

        KiteResourceActionIntent.entries.forEach { intent ->
            coordinator.dispatch(KiteResourceActionRequest("resource", intent, KiteResourceActionSource.Card))
        }

        assertEquals(
            listOf("install", "reopen", "open", "stop", "uninstall", "cancel", "cancel_failed"),
            gateway.calls
        )
    }

    @Test
    fun `plan cancellation and home card creation remain explicit workflows`() = runTest {
        val gateway = FakeGateway()
        val coordinator = ResourceActionWorkflowCoordinator(gateway)

        coordinator.cancelPlan("target", listOf("base", "target"))
        coordinator.createHomeCard("target")

        assertEquals(listOf("cancel_plan:target:base,target", "home:target"), gateway.calls)
    }

    private class FakeGateway : ResourceActionGateway {
        val calls = mutableListOf<String>()
        private fun record(value: String) = listOf(ResourceActionEffect.Message(value)).also { calls += value }
        override suspend fun install(resourceId: String) = record("install")
        override suspend fun reopenInstall(resourceId: String) = record("reopen")
        override suspend fun open(resourceId: String) = record("open")
        override suspend fun stop(resourceId: String) = record("stop")
        override suspend fun uninstall(resourceId: String) = record("uninstall")
        override suspend fun cancelInstall(resourceId: String) = record("cancel")
        override suspend fun cancelFailedInstall(resourceId: String) = record("cancel_failed")
        override suspend fun cancelPlan(targetResourceId: String, planResourceIds: List<String>) =
            record("cancel_plan:$targetResourceId:${planResourceIds.joinToString(",")}")
        override suspend fun createHomeCard(resourceId: String) = record("home:$resourceId")
    }
}
