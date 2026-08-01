package com.kite.app.foundation.runtime

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OrdinaryLaunchPreparationBenchmarkContractTest {
    @Test
    fun fixedColdProcessBaselineCannotBeRewrittenOrExecutedByAdb() {
        val root = repositoryRoot()
        val source = File(
            root,
            "app/src/debug/kotlin/com/kite/app/foundation/runtime/OrdinaryLaunchPreparationBenchmarkReceiver.kt",
        ).readText()
        val manifest = File(root, "app/src/debug/AndroidManifest.xml").readText()
        val manager = File(
            root,
            "app/src/main/kotlin/com/kite/app/foundation/runtime/KFContainerManager.kt",
        ).readText()

        assertTrue(source.contains("private const val EXPECTED_PAIRED_COLD_ROUNDS = 3"))
        assertTrue(source.contains("private const val MIN_REDUCTION_PERCENT = 30.0"))
        assertTrue(source.contains("private const val MIN_REDUCTION_MS = 300L"))
        assertTrue(source.contains("private const val MAX_CANDIDATE_P95_MS = 500L"))
        assertTrue(source.contains("private val FIXED_ARGV = listOf(\"/bin/true\")"))
        assertTrue(source.contains("KFContainerManager.defaultContainerColdReuseDecision(context)"))
        assertTrue(source.contains("KFContainerManager.buildContainerArgvExecConfigFullPreparationForBenchmark("))
        assertTrue(source.contains("KFContainerManager.buildContainerArgvExecConfigColdReuseCandidateForBenchmark("))
        assertTrue(source.contains("businessProcessStarted=false"))
        assertTrue(source.contains("configDigest="))
        assertTrue(source.contains("adbOverrides=false"))
        assertFalse(source.contains("getStringExtra"))
        assertFalse(source.contains("getIntExtra"))
        assertFalse(source.contains("getLongExtra"))
        assertFalse(source.contains("ProcessBuilder"))
        assertFalse(source.contains(".start()"))
        assertTrue(manifest.contains("com.kite.app.debug.ORDINARY_LAUNCH_PREPARATION_BASELINE"))
        assertTrue(manifest.contains("com.kite.app.debug.ORDINARY_LAUNCH_PREPARATION_CANDIDATE"))
        assertTrue(manifest.contains("com.kite.app.debug.ORDINARY_LAUNCH_PREPARATION_COUNTEREXAMPLE"))
        assertTrue(source.contains("rf2020-fixed-view"))
        assertTrue(source.contains("rf2020-fixed-environment"))
        assertTrue(source.contains("viewFallback="))
        assertTrue(source.contains("environmentFallback="))

        val productionOrdinaryPreparation = manager
            .substringAfter("private fun prepareOrdinaryBuildContext(")
            .substringBefore("private fun resolveOrdinaryLaunchPreparationCandidate(")
        assertTrue(productionOrdinaryPreparation.contains("prepareBuildContextUncached("))
        assertFalse(productionOrdinaryPreparation.contains("prepareBuildContextFromReadyCandidate("))
    }

    private fun repositoryRoot(): File {
        val workingDirectory = File(System.getProperty("user.dir").orEmpty())
        return sequenceOf(workingDirectory, workingDirectory.parentFile)
            .filterNotNull()
            .firstOrNull { File(it, "app/src/debug/AndroidManifest.xml").isFile }
            ?: error("找不到 Kite 仓库根目录，当前目录：${workingDirectory.absolutePath}")
    }
}
