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
        assertTrue(source.contains("private const val HOTSPOT_ROUNDS = 9"))
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
        assertTrue(source.contains("private val BASE_VARIANTS"))
        assertTrue(source.contains("ACTIVE_TELEMETRY_LOG_ONLY"))
        assertTrue(source.contains("ACTIVE_TELEMETRY_LOG_SHARDED"))
        assertTrue(source.contains("ACTIVE_NO_TELEMETRY_NO_PROCFS"))
        assertTrue(source.contains("ACTIVE_NO_TELEMETRY_NO_MOUNTINFO"))
        assertTrue(source.contains("ACTIVE_NO_TELEMETRY_MINIMAL"))
        assertTrue(source.contains("ACTIVE_NO_TELEMETRY_EXTERNAL_LOADER"))
        assertTrue(source.contains("PROOT_NO_KF_PROCFS"))
        assertTrue(source.contains("PROOT_NO_MOUNTINFO"))
        assertTrue(source.contains("rf1430_proot_active_hotspot"))
        assertTrue(source.contains("status=rejected suite="))
        assertTrue(source.contains("requiresForeground=true"))
        assertFalse(source.contains("getStringExtra"))
        assertFalse(source.contains("getIntExtra"))
        assertFalse(source.contains("activeRuntimeId"))
        assertTrue(manifest.contains("com.kite.app.debug.PROOT_ACTIVE_RUNTIME_BENCHMARK"))
        assertTrue(manifest.contains("com.kite.app.debug.PROOT_ACTIVE_RUNTIME_HOTSPOT"))
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
