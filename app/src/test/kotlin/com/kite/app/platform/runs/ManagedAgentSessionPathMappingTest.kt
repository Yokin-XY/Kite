package com.kite.app.platform.runs

import org.junit.Assert.assertEquals
import org.junit.Test

class ManagedAgentSessionPathMappingTest {
    @Test
    fun hostRuntimeMapsWorkspaceAtAcpBoundaryAndRestoresKitePath() {
        val mapping = ManagedAgentSessionPathMapping("/data/user/0/com.kite.app/files/runtime/shared/ubuntu-main")

        assertEquals(
            "/data/user/0/com.kite.app/files/runtime/shared/ubuntu-main",
            mapping.toAgent("/workspace"),
        )
        assertEquals(
            "/data/user/0/com.kite.app/files/runtime/shared/ubuntu-main/project/src",
            mapping.toAgent("/workspace/project/src"),
        )
        assertEquals(
            "/workspace/project/src",
            mapping.fromAgent("/data/user/0/com.kite.app/files/runtime/shared/ubuntu-main/project/src"),
        )
        assertEquals("/sdcard/Download", mapping.toAgent("/sdcard/Download"))
    }

    @Test
    fun prootRuntimeKeepsContainerPathsUnchanged() {
        val mapping = ManagedAgentSessionPathMapping()

        assertEquals("/workspace/project", mapping.toAgent("/workspace/project"))
        assertEquals("/workspace/project", mapping.fromAgent("/workspace/project"))
    }
}
