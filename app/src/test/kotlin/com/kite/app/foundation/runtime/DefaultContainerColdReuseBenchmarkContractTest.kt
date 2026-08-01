package com.kite.app.foundation.runtime

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultContainerColdReuseBenchmarkContractTest {
    @Test
    fun debugMatrixIsFixedAndCannotBeRewrittenByAdb() {
        val root = repositoryRoot()
        val source = File(
            root,
            "app/src/debug/kotlin/com/kite/app/foundation/runtime/DefaultContainerColdReuseBenchmarkReceiver.kt",
        ).readText()
        val manifest = File(root, "app/src/debug/AndroidManifest.xml").readText()

        assertTrue(source.contains("private const val EXPECTED_COLD_ROUNDS = 3"))
        assertTrue(source.contains("private const val MIN_REDUCTION_PERCENT = 50.0"))
        assertTrue(source.contains("private const val MIN_REDUCTION_MS = 700L"))
        assertTrue(source.contains("private const val MAX_CANDIDATE_P95_MS = 500L"))
        assertTrue(source.contains("KFContainerManager.defaultContainerColdReuseDecision(context)"))
        assertTrue(source.contains("KFContainerManager.ensureDefaultContainerFullPreparationForBenchmark(context)"))
        assertTrue(source.contains("noSideEffects="))
        assertTrue(source.contains("sameIdentity="))
        assertTrue(source.contains("adbOverrides=false"))
        assertFalse(source.contains("getStringExtra"))
        assertFalse(source.contains("getIntExtra"))
        assertFalse(source.contains("getLongExtra"))
        assertTrue(manifest.contains("com.kite.app.debug.DEFAULT_CONTAINER_COLD_REUSE_BENCHMARK"))
    }

    @Test
    fun productionMatrixUsesOnlyFixedColdProcessPaths() {
        val root = repositoryRoot()
        val source = File(
            root,
            "app/src/debug/kotlin/com/kite/app/foundation/runtime/DefaultContainerColdReuseProductionBenchmarkReceiver.kt",
        ).readText()
        val manager = File(
            root,
            "app/src/main/kotlin/com/kite/app/foundation/runtime/KFContainerManager.kt",
        ).readText()
        val manifest = File(root, "app/src/debug/AndroidManifest.xml").readText()

        assertTrue(source.contains("private const val EXPECTED_PAIRED_COLD_ROUNDS = 3"))
        assertTrue(source.contains("private const val MIN_REDUCTION_PERCENT = 50.0"))
        assertTrue(source.contains("private const val MIN_REDUCTION_MS = 700L"))
        assertTrue(source.contains("KFContainerManager.ensureDefaultContainer(context)"))
        assertTrue(source.contains("KFContainerManager.ensureDefaultContainerFullPreparationForBenchmark(context)"))
        assertTrue(source.contains("sameIdentity="))
        assertTrue(source.contains("baseUnchanged="))
        assertTrue(source.contains("adbOverrides=false"))
        assertFalse(source.contains("getStringExtra"))
        assertFalse(source.contains("getIntExtra"))
        assertFalse(source.contains("getLongExtra"))
        assertTrue(manager.contains("buildNetworkPlan(cold-reuse:"))
        assertTrue(manager.contains("mutableRepair=receipt_verified"))
        assertTrue(manager.contains("DefaultContainerColdReuseReceipt.write("))
        assertTrue(manager.contains("Os.chmod(target.absolutePath"))
        assertFalse(manager.contains("ProcessBuilder(\"/system/bin/chmod\""))
        assertFalse(manager.contains("ensureWorkspaceBuildSupport(\${container.id})"))
        assertTrue(manifest.contains("com.kite.app.debug.DEFAULT_CONTAINER_COLD_REUSE_PRODUCTION"))
        assertTrue(manifest.contains("com.kite.app.debug.DEFAULT_CONTAINER_COLD_REUSE_FULL"))
        assertTrue(manifest.contains("com.kite.app.debug.DEFAULT_CONTAINER_COLD_REUSE_COUNTEREXAMPLE"))
        assertTrue(source.contains("rf1930-fixed-invalid-time-zone"))
        assertTrue(source.contains("fallbackTriggered="))
        assertTrue(source.contains("repaired="))
    }

    private fun repositoryRoot(): File {
        val workingDirectory = File(System.getProperty("user.dir").orEmpty())
        return sequenceOf(workingDirectory, workingDirectory.parentFile)
            .filterNotNull()
            .firstOrNull { File(it, "app/src/debug/AndroidManifest.xml").isFile }
            ?: error("找不到 Kite 仓库根目录，当前目录：${workingDirectory.absolutePath}")
    }
}
