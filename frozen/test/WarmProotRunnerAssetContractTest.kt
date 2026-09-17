package com.kite.app.foundation.runtime

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WarmProotRunnerAssetContractTest {
    @Test
    fun `tracked runner asset is a 64 bit little endian AArch64 ELF`() {
        val bytes = File(repositoryRoot(), "assets/system/kf-runner-arm64").readBytes()

        assertTrue("runner asset is unexpectedly small", bytes.size > 64 * 1024)
        assertEquals(listOf(0x7f, 'E'.code, 'L'.code, 'F'.code), bytes.take(4).map(Byte::toInt))
        assertEquals("ELFCLASS64", 2, bytes[4].toInt() and 0xff)
        assertEquals("ELFDATA2LSB", 1, bytes[5].toInt() and 0xff)
        val machine = (bytes[18].toInt() and 0xff) or ((bytes[19].toInt() and 0xff) shl 8)
        assertEquals("EM_AARCH64", 183, machine)
    }

    @Test
    fun `runner source and workspace marker declare protocol generation`() {
        val root = repositoryRoot()
        val source = File(root, "native/kf-runner/kf-runner.c").readText()
        val workspaceSupport = File(
            root,
            "app/src/main/kotlin/com/kite/app/foundation/workspace/WorkspaceBuildSupport.kt"
        ).readText()

        assertTrue(source.contains("#define KF_RUNNER_VERSION \"0.2.0\""))
        assertTrue(source.contains("strcmp(argv[1], \"--server\")"))
        assertTrue(workspaceSupport.contains("layout=v11_kf_runner_protocol_v1"))
    }

    private fun repositoryRoot(): File {
        val workingDirectory = File(System.getProperty("user.dir").orEmpty())
        return sequenceOf(workingDirectory, workingDirectory.parentFile)
            .filterNotNull()
            .firstOrNull { File(it, "native/kf-runner/kf-runner.c").isFile }
            ?: error("找不到 Kite 仓库根目录，当前目录：${workingDirectory.absolutePath}")
    }
}
