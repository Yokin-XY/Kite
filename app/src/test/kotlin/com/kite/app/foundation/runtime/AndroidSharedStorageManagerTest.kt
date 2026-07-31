package com.kite.app.foundation.runtime

import com.kite.app.foundation.workspace.AndroidSharedStorageVolume
import com.kite.app.foundation.contracts.RuntimePathRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidSharedStorageManagerTest {

    @Test
    fun `granted storage volumes become same path proot binds`() {
        val mounts = AndroidSharedStorageManager.bindMounts(
            AndroidSharedStorageSnapshot(
                accessGranted = true,
                volumes = listOf(
                    AndroidSharedStorageVolume("/storage/emulated/0", "手机存储"),
                    AndroidSharedStorageVolume("/storage/1234-5678", "存储卡")
                )
            )
        )

        assertEquals(2, mounts.size)
        assertTrue(mounts.all { it.sourcePath == it.targetPath })
        assertEquals(listOf("/storage/emulated/0", "/storage/1234-5678"), mounts.map { it.targetPath })
        assertEquals(listOf("-b", "/storage/emulated/0"), mounts.first().toArgv())
    }

    @Test
    fun `missing broad access produces no proot bind`() {
        assertTrue(
            AndroidSharedStorageManager.bindMounts(
                AndroidSharedStorageSnapshot(false, emptyList(), "未授权")
            ).isEmpty()
        )
    }

    @Test
    fun `runtime boundary distinguishes Android files from Ubuntu projects`() {
        assertEquals(
            RuntimePathRole.ANDROID_SHARED_STORAGE,
            RuntimeBoundary.classifyContainerPath("/storage/emulated/0/Download/report.pdf")
        )
        assertEquals(
            RuntimePathRole.WORKSPACE,
            RuntimeBoundary.classifyContainerPath("/workspace/Kite")
        )
        assertEquals(
            RuntimePathRole.CONTAINER_ROOTFS,
            RuntimeBoundary.classifyContainerPath("/exchange/legacy.txt")
        )
    }
}
