package com.kite.app.foundation.runtime

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProotActiveRuntimeBenchmarkContractTest {
    @Test
    fun `debug matrix is fixed and cannot switch production runtime`() {
        val root = repositoryRoot()
        val source = File(
            root,
            "app/src/debug/kotlin/com/kite/app/foundation/runtime/ProotActiveRuntimeBenchmarkReceiver.kt",
        ).readText()
        val manifest = File(root, "app/src/debug/AndroidManifest.xml").readText()

        assertTrue(source.contains("private const val ROUNDS = 3"))
        assertTrue(source.contains("private val CONCURRENCY_LEVELS = listOf(1, 4, 8)"))
        assertTrue(source.contains("ACTIVE_TELEMETRY"))
        assertTrue(source.contains("ACTIVE_NO_TELEMETRY"))
        assertTrue(source.contains("STOCK_NO_TELEMETRY"))
        assertTrue(source.contains("WorkSurfaceRuntimeBridge.buildArgvExecConfig"))
        assertTrue(source.contains("command = base.command.toMutableList().also { it[0] = assets.stock.absolutePath }"))
        assertTrue(source.contains("KF_PROOT_TELEMETRY_PATH"))
        assertTrue(source.contains("hostTelemetryFile.absolutePath"))
        assertTrue(source.contains("status=telemetry_sink"))
        assertTrue(source.contains("PROOT_LOADER_32"))
        assertTrue(source.contains("proot/proot-arm64"))
        assertTrue(source.contains("wallSamplesMs="))
        assertFalse(source.contains("getStringExtra"))
        assertFalse(source.contains("getIntExtra"))
        assertFalse(source.contains("activeRuntimeId"))
        assertTrue(manifest.contains("com.kite.app.debug.PROOT_ACTIVE_RUNTIME_BENCHMARK"))
        assertTrue(
            Regex(
                "android:name=\"com\\.kite\\.app\\.foundation\\.runtime\\.ProotActiveRuntimeBenchmarkService\"\\s+" +
                    "android:exported=\"false\"",
            ).containsMatchIn(manifest)
        )
    }

    private fun repositoryRoot(): File {
        val workingDirectory = File(System.getProperty("user.dir").orEmpty())
        return sequenceOf(workingDirectory, workingDirectory.parentFile)
            .filterNotNull()
            .firstOrNull { File(it, "app/src/debug/AndroidManifest.xml").isFile }
            ?: error("找不到 Kite 仓库根目录，当前目录：${workingDirectory.absolutePath}")
    }
}
