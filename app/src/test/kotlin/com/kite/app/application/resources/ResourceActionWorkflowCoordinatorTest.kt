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
            listOf(
                "install", "reopen", "reopen_operation", "open", "stop", "uninstall",
                "check_update", "update", "reinstall", "repair", "cancel", "cancel_failed"
            ),
            gateway.calls
        )
    }

    @Test
    fun `plan cancellation and home card creation remain explicit workflows`() = runTest {
        val gateway = FakeGateway()
        val coordinator = ResourceActionWorkflowCoordinator(gateway)

        coordinator.cancelPlan("target", listOf("base", "target"))
        coordinator.cancelInstallWizard(
            targetResourceId = "target",
            planResourceIds = listOf("base", "target"),
            environmentId = "profile",
            instanceId = "wizard",
            expectedGeneration = 17L,
        )
        coordinator.createHomeCard("target")
        coordinator.installDirect("target")
        coordinator.checkUpdates(listOf("base", "target"))
        coordinator.recoverFailedInstall("target", "wizard")

        assertEquals(
            listOf(
                "cancel_plan:target:base,target",
                "cancel_wizard:target:profile:wizard:17:base,target",
                "home:target",
                "direct:target",
                "check_updates:base,target",
                "recover:target:wizard",
            ),
            gateway.calls
        )
    }

    private class FakeGateway : ResourceActionGateway {
        val calls = mutableListOf<String>()
        private fun record(value: String) = listOf(ResourceActionEffect.Message(value)).also { calls += value }
        override suspend fun install(resourceId: String) = record("install")
        override suspend fun reopenInstall(resourceId: String) = record("reopen")
        override suspend fun reopenOperation(resourceId: String) = record("reopen_operation")
        override suspend fun open(resourceId: String) = record("open")
        override suspend fun stop(resourceId: String) = record("stop")
        override suspend fun uninstall(resourceId: String) = record("uninstall")
        override suspend fun checkUpdate(resourceId: String) = record("check_update")
        override suspend fun checkUpdates(resourceIds: List<String>) =
            record("check_updates:${resourceIds.joinToString(",")}")
        override suspend fun update(resourceId: String) = record("update")
        override suspend fun reinstall(resourceId: String) = record("reinstall")
        override suspend fun repair(resourceId: String) = record("repair")
        override suspend fun cancelInstall(resourceId: String) = record("cancel")
        override suspend fun cancelFailedInstall(resourceId: String) = record("cancel_failed")
        override suspend fun recoverFailedInstall(resourceId: String, parentInstanceId: String?) =
            record("recover:$resourceId:$parentInstanceId")
        override suspend fun cancelPlan(targetResourceId: String, planResourceIds: List<String>) =
            record("cancel_plan:$targetResourceId:${planResourceIds.joinToString(",")}")
        override suspend fun cancelInstallWizard(
            targetResourceId: String,
            planResourceIds: List<String>,
            environmentId: String,
            instanceId: String,
            expectedGeneration: Long,
        ): Boolean {
            calls += "cancel_wizard:$targetResourceId:$environmentId:$instanceId:$expectedGeneration:${planResourceIds.joinToString(",")}"
            return true
        }
        override suspend fun createHomeCard(resourceId: String) = record("home:$resourceId")
        override suspend fun installDirect(resourceId: String) = record("direct:$resourceId")
    }
}
