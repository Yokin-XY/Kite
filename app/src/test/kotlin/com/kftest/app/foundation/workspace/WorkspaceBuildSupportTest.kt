package com.kftest.app.foundation.workspace

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class WorkspaceBuildSupportTest {
    @Test
    fun ensure_replacesContainerSymlinkFdWrapper() {
        val workspace = Files.createTempDirectory("kite-workspace-support-").toFile()
        try {
            val helperBin = WorkspaceBuildSupport.helperBinDir(workspace).also { it.mkdirs() }
            val fd = helperBin.resolve("fd")
            val symlinkCreated = runCatching {
                Files.createSymbolicLink(
                    fd.toPath(),
                    Paths.get("/workspace/.kf/software/kite.tool.env/bin/fd")
                )
            }.isSuccess
            assumeTrue("symlink creation is not available on this host", symlinkCreated)

            WorkspaceBuildSupport.ensure(workspace)

            assertFalse(Files.isSymbolicLink(fd.toPath()))
            assertTrue(fd.readText().contains("exec fdfind"))
        } finally {
            workspace.deleteRecursively()
        }
    }
}
