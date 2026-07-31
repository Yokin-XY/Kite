package com.kite.app.foundation.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class BackgroundRuntimeStructuredLaunchContractTest {
    @Test
    fun `structured launch fields survive json round trip and old records remain readable`() {
        val record = record().copy(
            startExecutable = "arbitrary-node-cli",
            startArguments = listOf("gateway", "value with spaces"),
            environment = mapOf("DISABLE_DISCOVERY" to "1"),
            environmentFiles = mapOf("RUNTIME_TOKEN" to "/workspace/.kf/secrets/runtime-token"),
            lastLaunchLane = "host_node",
            lastLaunchReason = "structured_node_ready",
        )

        val restored = BackgroundRuntimeRecord.fromJson(record.toJson())

        assertEquals("arbitrary-node-cli", restored.startExecutable)
        assertEquals(listOf("gateway", "value with spaces"), restored.startArguments)
        assertEquals(mapOf("DISABLE_DISCOVERY" to "1"), restored.environment)
        assertEquals(
            mapOf("RUNTIME_TOKEN" to "/workspace/.kf/secrets/runtime-token"),
            restored.environmentFiles,
        )
        assertEquals("host_node", restored.lastLaunchLane)
        assertEquals("structured_node_ready", restored.lastLaunchReason)

        val legacy = record().toJson().apply {
            remove("startExecutable")
            remove("startArguments")
            remove("environment")
            remove("environmentFiles")
            remove("lastLaunchLane")
            remove("lastLaunchReason")
        }
        val restoredLegacy = BackgroundRuntimeRecord.fromJson(legacy)
        assertNull(restoredLegacy.startExecutable)
        assertTrue(restoredLegacy.startArguments.isEmpty())
        assertTrue(restoredLegacy.environment.isEmpty())
        assertTrue(restoredLegacy.environmentFiles.isEmpty())
        assertNull(restoredLegacy.lastLaunchLane)
        assertNull(restoredLegacy.lastLaunchReason)
    }

    @Test
    fun `background host routes structured requests through shared planner and keeps one proot fallback`() {
        val source = sourceFile("BackgroundRuntimeHost.kt").readText()
        val body = source.substringAfter("private fun buildRuntimeProcessLaunchConfig(")
            .substringBefore("private fun ", missingDelimiterValue = source)

        assertTrue(body.contains("HostNodeLaunchPlanner.plan("))
        assertTrue(body.contains("HostNodeExecutionRequest.Argv(executable, record.startArguments)"))
        assertTrue(body.contains("WorkSurfaceRuntimeBridge.buildShellExecConfig("))
        assertTrue(body.contains("hostFallbackReason ?: \"host_node_unavailable\""))
        assertFalse(body.contains("ProcessBuilder("))
        val startBody = source.substringAfter("private fun startProcessRuntime(")
            .substringBefore("private fun buildRuntimeProcessLaunchConfig(")
        assertEquals(1, startBody.windowed("ProcessBuilder(".length).count { it == "ProcessBuilder(" })
    }

    @Test
    fun `background registry owns persisted launch route`() {
        val registry = sourceFile("BackgroundRuntimeRegistry.kt").readText()
        val update = registry.substringAfter("fun updateLaunchRoute(")
            .substringBefore("fun updateRestartState(")

        assertTrue(update.contains("record.copy(lastLaunchLane = lane, lastLaunchReason = reason)"))
    }

    @Test
    fun `product gateway leaves builtin registry and is declared by resource manifest`() {
        val registry = sourceFile("BackgroundRuntimeRegistry.kt").readText()
        assertFalse(registry.contains("openClawId"))
        assertFalse(registry.contains("startExecutable = \"openclaw\""))

        val manifest = listOf(
            File("assets/resources/kite.openclaw/manifest.json"),
            File("../assets/resources/kite.openclaw/manifest.json"),
        ).first(File::exists).readText()
        assertTrue(manifest.contains("\"runtimeDependencies\""))
        assertTrue(manifest.contains("\"OPENCLAW_GATEWAY_TOKEN\""))
        assertTrue(manifest.contains("\"healthHttpPath\": \"/readyz\""))

        val provider = sourceFile("../runtime/HostNodeTerminalLaunch.kt").readText()
        assertFalse(provider.contains("openclaw", ignoreCase = true))
    }

    private fun record() = BackgroundRuntimeRecord(
        id = "runtime-1",
        spaceId = "space-main",
        kind = BackgroundRuntimeKind.CUSTOM,
        mode = BackgroundRuntimeMode.PROCESS,
        title = "Runtime",
        workingDirectory = "/workspace",
        startCommand = "exec arbitrary-node-cli gateway",
        logPath = "/tmp/runtime.log",
        createdAt = 1L,
    )

    private fun sourceFile(name: String): File = listOf(
        File("src/main/kotlin/com/kite/app/foundation/service/$name"),
        File("app/src/main/kotlin/com/kite/app/foundation/service/$name"),
    ).first(File::exists)
}
