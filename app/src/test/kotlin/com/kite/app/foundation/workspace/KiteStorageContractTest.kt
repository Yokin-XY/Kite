package com.kite.app.foundation.workspace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class KiteStorageContractTest {

    @Test
    fun `project paths stay below workspace and normalize dot segments`() {
        assertEquals(
            "/workspace/client/app",
            KiteStorageContract.normalizeWorkspacePath(" /workspace/client/./draft/../app ")
        )
        assertTrue(KiteStorageContract.isSelectableProjectPath("/workspace/client/app"))
        assertFalse(KiteStorageContract.isSelectableProjectPath("/workspace"))
        assertFalse(KiteStorageContract.isSelectableProjectPath("/storage/emulated/0/Documents"))
        assertNull(KiteStorageContract.normalizeWorkspacePath("/workspace/../../storage/emulated/0"))
        assertNull(KiteStorageContract.normalizeWorkspacePath("workspace/client"))
    }

    @Test
    fun `workspace control directory cannot become a user project`() {
        assertFalse(KiteStorageContract.isSelectableProjectPath("/workspace/.kf"))
        assertFalse(KiteStorageContract.isSelectableProjectPath("/workspace/.kf/toolchains"))
        assertTrue(KiteStorageContract.isSelectableProjectPath("/workspace/.config"))
    }

    @Test
    fun `container workspace path resolves to the same host tree`() {
        val root = File("build/test-workspace").absoluteFile
        assertEquals(
            File(root, "Kite/app").toPath().normalize().toFile(),
            KiteStorageContract.resolveHostWorkspacePath(root, "/workspace/Kite/app")
        )
        assertNull(KiteStorageContract.resolveHostWorkspacePath(root, "/storage/emulated/0/Kite"))
    }

    @Test
    fun `android volumes keep the real path on both sides`() {
        val volumes = AndroidSharedStorageVolumePlan.fromRoots(
            listOf(
                "/storage/emulated/0" to "手机存储",
                "/storage/emulated/0/Android/data/com.kite.app/files" to "错误的应用目录",
                "/storage/1234-5678" to "存储卡",
                "/storage/1234-5678" to "重复存储卡",
                "/data/user/0/com.kite.app" to "应用私有目录"
            )
        )

        assertEquals(listOf("/storage/1234-5678", "/storage/emulated/0"), volumes.map { it.path })
        assertTrue(volumes.all { it.hostPath == it.containerPath })
    }
}
