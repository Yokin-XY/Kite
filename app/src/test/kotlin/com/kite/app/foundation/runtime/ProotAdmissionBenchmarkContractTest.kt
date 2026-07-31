package com.kite.app.foundation.runtime

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProotAdmissionBenchmarkContractTest {
    @Test
    fun `debug benchmark leaves broadcast immediately and runs in an unexported service`() {
        val root = repositoryRoot()
        val source = File(
            root,
            "app/src/debug/kotlin/com/kite/app/foundation/runtime/ProotAdmissionBenchmarkReceiver.kt",
        ).readText()
        val manifest = File(root, "app/src/debug/AndroidManifest.xml").readText()

        assertTrue(source.contains("context.startService(Intent(context, ProotAdmissionBenchmarkService::class.java))"))
        assertFalse(source.contains("goAsync()"))
        assertTrue(source.contains("stopSelf()"))
        assertTrue(manifest.contains("com.kite.app.debug.PROOT_ADMISSION_BENCHMARK"))
        assertTrue(
            Regex(
                "android:name=\"com\\.kite\\.app\\.foundation\\.runtime\\.ProotAdmissionBenchmarkService\"\\s+" +
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
