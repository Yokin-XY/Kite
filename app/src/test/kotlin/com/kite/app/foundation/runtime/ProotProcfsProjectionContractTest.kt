package com.kite.app.foundation.runtime

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProotProcfsProjectionContractTest {
    @Test
    fun `symlink leaves are never materialized as regular projection files`() {
        val root = repositoryRoot()
        val patch = File(root, "assets/proot/patches/kf-proot-procfs-projection-v1.patch").readText()
        val store = File(
            root,
            "app/src/main/kotlin/com/kite/app/foundation/runtime/RuntimeHealthStore.kt",
        ).readText()

        assertTrue(patch.contains("strcmp(leaf, \"statm\") == 0;"))
        assertFalse(patch.contains("strcmp(leaf, \"cwd\") == 0"))
        assertFalse(patch.contains("strcmp(leaf, \"exe\") == 0"))
        assertFalse(store.contains("files[\"\$pid/cwd\"]"))
        assertFalse(store.contains("files[\"\$pid/exe\"]"))
    }

    private fun repositoryRoot(): File {
        val workingDirectory = File(System.getProperty("user.dir").orEmpty())
        return sequenceOf(workingDirectory, workingDirectory.parentFile)
            .filterNotNull()
            .firstOrNull { File(it, "assets/proot/proot-runtime.json").isFile }
            ?: error("找不到 Kite 仓库根目录，当前目录：${workingDirectory.absolutePath}")
    }
}
