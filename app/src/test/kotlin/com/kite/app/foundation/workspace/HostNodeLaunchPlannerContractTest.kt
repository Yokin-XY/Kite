package com.kite.app.foundation.workspace

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class HostNodeLaunchPlannerContractTest {
    @Test
    fun `planner owns provider selection and child process contract without process lifecycle`() {
        val source = sourceFile().readText()

        assertTrue(source.contains("HostPythonRuntimeProvider.prepare("))
        assertTrue(source.contains("HostNodeRuntimeProvider.prepare("))
        assertTrue(source.contains("RuntimeProviderDecision.Unsupported"))
        assertTrue(source.contains("ManagedRuntimeLaunchPlan.Blocked"))
        assertTrue(source.contains("HostNodeChildProcessContract.from(childExecConfig, marker).attachTo(baseConfig.plan)"))
        assertTrue(source.contains("WorkSurfaceRuntimeBridge.buildArgvExecConfig("))
        assertFalse(source.contains("ProcessBuilder("))
        assertFalse(source.contains("CardRunStore"))
        assertFalse(source.contains("BackgroundRuntimeRegistry"))
    }

    private fun sourceFile(): File = listOf(
        File("src/main/kotlin/com/kite/app/foundation/workspace/ManagedRuntimeLaunchPlanner.kt"),
        File("app/src/main/kotlin/com/kite/app/foundation/workspace/ManagedRuntimeLaunchPlanner.kt"),
    ).first(File::exists)
}
