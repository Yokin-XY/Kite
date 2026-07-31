package com.kite.app.platform.runtimemanagement

import com.kite.app.foundation.contracts.ContainerRecord
import com.kite.app.foundation.runtime.ProotViewBinding
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

/**
 * T014h 路径映射测试：证明 ProotViewLabRunner.resolveUpperLabDir 的 Upper 证据路径计算正确。
 *
 * 映射关系：
 * - baseRoot = runtime
 * - rootfs = runtime/containers/ubuntu-main/rootfs
 * - /root/.kite-view-lab（rootfs 内）对应 upper/containers/ubuntu-main/rootfs/root/.kite-view-lab
 * 不写死 ubuntu-main 或 containers 层级；越过 baseRoot 的路径必须拒绝。
 */
class ProotViewLabPathMappingTest {

    @Test
    fun mapsRootfsLabDirToCorrectUpperSubpath() {
        val tmp = createTempDir(prefix = "kite-pathmap-")
        try {
            val runtime = File(tmp, "runtime").apply { mkdirs() }
            val rootfs = File(runtime, "containers/ubuntu-main/rootfs").apply { mkdirs() }
            val upper = File(runtime, "proot-views/ubuntu-main/views/view-1/upper").apply { mkdirs() }
            val container = container(rootfs)
            val binding = binding(baseRoot = runtime.absolutePath, upper = upper.absolutePath)

            val resolved = ProotViewLabRunner.resolveUpperLabDir(binding, container)!!

            // 期望：upper/containers/ubuntu-main/rootfs/root/.kite-view-lab
            val expected = File(upper, "containers/ubuntu-main/rootfs/root/.kite-view-lab")
            assertEquals(expected.absoluteFile, resolved.absoluteFile)
        } finally {
            tmp.deleteRecursively()
        }
    }

    @Test
    fun mapsWorksWithDifferentContainerIdWithoutHardcoding() {
        val tmp = createTempDir(prefix = "kite-pathmap-alt-")
        try {
            // 故意用非 ubuntu-main 的容器 id，证明不写死层级名。
            val runtime = File(tmp, "rt").apply { mkdirs() }
            val rootfs = File(runtime, "containers/dev-box/rootfs").apply { mkdirs() }
            val upper = File(runtime, "proot-views/dev-box/views/view-9/upper").apply { mkdirs() }
            val container = ContainerRecord(
                id = "dev-box", displayName = "Dev", imageName = "ubuntu-base",
                rootfsPath = rootfs.absolutePath, workspacePath = "${runtime}/shared/dev-box",
                createdAt = 1L,
            )
            val binding = binding(baseRoot = runtime.absolutePath, upper = upper.absolutePath)

            val resolved = ProotViewLabRunner.resolveUpperLabDir(binding, container)!!

            val expected = File(upper, "containers/dev-box/rootfs/root/.kite-view-lab")
            assertEquals(expected.absoluteFile, resolved.absoluteFile)
        } finally {
            tmp.deleteRecursively()
        }
    }

    @Test
    fun rejectsLabDirOutsideBaseRoot() {
        val tmp = createTempDir(prefix = "kite-pathmap-outside-")
        try {
            val runtime = File(tmp, "runtime").apply { mkdirs() }
            // rootfs 不在 runtime 下（越界 baseRoot）。
            val outsideRootfs = File(tmp, "elsewhere/rootfs").apply { mkdirs() }
            val upper = File(runtime, "proot-views/ubuntu-main/views/view-1/upper").apply { mkdirs() }
            val container = container(outsideRootfs)
            val binding = binding(baseRoot = runtime.absolutePath, upper = upper.absolutePath)

            assertNull("越过 baseRoot 必须拒绝", ProotViewLabRunner.resolveUpperLabDir(binding, container))
        } finally {
            tmp.deleteRecursively()
        }
    }

    @Test
    fun rejectsLabDirOutsideRootfsScope() {
        val tmp = createTempDir(prefix = "kite-pathmap-scopes-")
        try {
            val runtime = File(tmp, "runtime").apply { mkdirs() }
            val upper = File(runtime, "proot-views/ubuntu-main/views/view-1/upper").apply { mkdirs() }
            // rootfs 不在 baseRoot 内（越过 baseRoot 即不属于 rootfs scope）。
            val outsideRootfs = File(tmp, "outside/rootfs").apply { mkdirs() }
            val binding = binding(baseRoot = runtime.absolutePath, upper = upper.absolutePath)

            assertNull("越过 baseRoot 的 rootfs 必须拒绝",
                ProotViewLabRunner.resolveUpperLabDir(binding, container(outsideRootfs)))
        } finally {
            tmp.deleteRecursively()
        }
    }

    private fun container(rootfs: File) = ContainerRecord(
        id = "ubuntu-main", displayName = "Ubuntu", imageName = "ubuntu-base",
        rootfsPath = rootfs.absolutePath,
        workspacePath = "${rootfs.parentFile?.parentFile?.parentFile}/shared/default",
        createdAt = 1L,
    )

    private fun binding(baseRoot: String, upper: String) = ProotViewBinding(
        viewId = "view-1",
        baseRootPath = baseRoot,
        upperRootPath = upper,
        whiteoutRootPath = "$upper/../whiteout",
        controlFilePath = "$upper/../control.conf",
        writable = true,
    )
}
