package com.kite.app.platform.runtimemanagement

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProotEnvironmentIsolationRunnerTest {
    @Test
    fun `工程隔离夹具只依赖不可变 Base 的 shell`() {
        assertEquals(
            listOf(
                "/bin/sh",
                "/workspace/.kf/system/state/kite-environment-lab/kite_environment_lab.sh",
                "read",
            ),
            environmentLabArgv(listOf("read")),
        )
        val fixture = projectFile(
            "src/main/assets/engineering/kite-environment-lab/kite_environment_lab.sh",
            "app/src/main/assets/engineering/kite-environment-lab/kite_environment_lab.sh",
        ).readText()
        assertTrue(fixture.startsWith("#!/bin/sh"))
        assertFalse(fixture.contains("python3"))
    }

    @Test
    fun `独立 rootfs 工作区与共享 exchange 证据全部满足才通过`() {
        val evidence = evaluateEnvironmentIsolation(
            firstMarker = "first",
            secondMarker = "second",
            sharedMarker = "shared",
            secondBefore = observation(root = null, workspace = null, exchange = "shared"),
            secondAfter = observation(root = "second", workspace = "second", exchange = "shared"),
            firstAfter = observation(root = "first", workspace = "first", exchange = "shared"),
            firstHostWorkspaceMatches = true,
            secondHostWorkspaceMatches = true,
            baseUntouched = true,
        )

        assertTrue(evidence.rootIsolated)
        assertTrue(evidence.workspaceIsolated)
        assertTrue(evidence.exchangeShared)
        assertTrue(evidence.baseUntouched)
        assertTrue(evidence.success)
    }

    @Test
    fun `第二环境读到第一环境私有标记时隔离失败`() {
        val evidence = evaluateEnvironmentIsolation(
            firstMarker = "first",
            secondMarker = "second",
            sharedMarker = "shared",
            secondBefore = observation(root = "first", workspace = "first", exchange = "shared"),
            secondAfter = observation(root = "second", workspace = "second", exchange = "shared"),
            firstAfter = observation(root = "first", workspace = "first", exchange = "shared"),
            firstHostWorkspaceMatches = true,
            secondHostWorkspaceMatches = true,
            baseUntouched = true,
        )

        assertFalse(evidence.rootIsolated)
        assertFalse(evidence.workspaceIsolated)
        assertFalse(evidence.success)
    }

    @Test
    fun `共享标记丢失或Base被写入时必须失败`() {
        val evidence = evaluateEnvironmentIsolation(
            firstMarker = "first",
            secondMarker = "second",
            sharedMarker = "shared",
            secondBefore = observation(root = null, workspace = null, exchange = null),
            secondAfter = observation(root = "second", workspace = "second", exchange = null),
            firstAfter = observation(root = "first", workspace = "first", exchange = "shared"),
            firstHostWorkspaceMatches = true,
            secondHostWorkspaceMatches = true,
            baseUntouched = false,
        )

        assertFalse(evidence.exchangeShared)
        assertFalse(evidence.baseUntouched)
        assertFalse(evidence.success)
    }

    private fun observation(
        root: String?,
        workspace: String?,
        exchange: String?,
    ) = EnvironmentIsolationObservation(
        environmentId = "environment",
        viewId = "view",
        rootValue = root,
        workspaceValue = workspace,
        exchangeValue = exchange,
    )

    private fun projectFile(vararg candidates: String): File =
        candidates.map(::File).first { it.isFile }
}
