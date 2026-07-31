package com.kite.app.foundation.runtime

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProotCompatibilityEntryContractTest {
    @Test
    fun `formal user facing entries consume the standard proot plan`() {
        val bridge = source("java/com/kite/app/bridge/KiteBridgeClient.kt")
        val terminal = source("kotlin/com/kite/app/foundation/terminal/TerminalSessionController.kt")
        val agent = source("java/com/kite/app/platform/runs/AndroidAgentRecipeRuntime.kt")
        val agentConfig = source("java/com/kite/app/platform/runs/AndroidAgentConfigCommandExecutor.kt")
        val background = source("kotlin/com/kite/app/foundation/service/BackgroundRuntimeHost.kt")
        val supervisor = source("kotlin/com/kite/app/foundation/service/SupervisordServiceHealthStore.kt")

        assertTrue(bridge.contains("ProotCompatibilityRuntimeProvider.requirePlan("))
        assertFalse(bridge.contains("WorkSurfaceRuntimeBridge.buildShellExecConfig("))
        assertTrue(terminal.contains("ProotCompatibilityRuntimeProvider.requirePlan("))
        assertTrue(terminal.contains("WorkSurfaceRuntimeBridge.buildProotTerminalLaunchConfig("))
        assertTrue(agent.contains("ManagedRuntimeLaunchPlanner.plan("))
        assertTrue(agent.contains("WorkSurfaceRuntimeBridge.buildProotExecConfig("))
        assertFalse(agent.contains("WorkSurfaceRuntimeBridge.buildArgvExecConfig("))
        assertTrue(agentConfig.contains("WorkSurfaceRuntimeBridge.buildRequiredProotExecConfig("))
        assertTrue(background.contains("ManagedRuntimeLaunchPlan.Proot"))
        assertTrue(background.contains("WorkSurfaceRuntimeBridge.buildRequiredProotExecConfig("))
        assertTrue(supervisor.contains("BoundedProotTaskExecutor.executeBlocking("))
        assertTrue(supervisor.contains("WorkspaceBuildSupport.ensureSupervisordHealthSnapshotHelper("))
        assertFalse(supervisor.contains("WorkSurfaceRuntimeBridge.buildRequiredProotExecConfig("))
        assertFalse(supervisor.contains("ProcessBuilder("))
    }

    @Test
    fun `provider and planner do not create process or specialize products`() {
        val provider = source("kotlin/com/kite/app/foundation/runtime/ProotCompatibilityRuntime.kt")
        val planner = source("kotlin/com/kite/app/foundation/workspace/ManagedRuntimeLaunchPlanner.kt")
        val combined = provider + planner

        assertFalse(combined.contains("ProcessBuilder("))
        assertFalse(combined.contains("openclaw", ignoreCase = true))
        assertFalse(combined.contains("opencode", ignoreCase = true))
        assertFalse(combined.contains("resourceId"))
        assertTrue(planner.contains("ProotCompatibilityRuntimeProvider.prepare("))
    }

    private fun source(relative: String): String = listOf(
        File("src/main/$relative"),
        File("app/src/main/$relative"),
    ).first(File::exists).readText()
}
