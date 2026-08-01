package com.kite.app.platform.resources

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResourceVersionBatchSchedulingContractTest {
    private val benchmark = File(
        "src/debug/kotlin/com/kite/app/platform/resources/ResourceVersionBatchSchedulingBenchmarkReceiver.kt"
    ).readText()
    private val gateway = File(
        "src/main/java/com/kite/app/platform/resources/AndroidResourceActionGateway.kt"
    ).readText()
    private val manifest = File("src/debug/AndroidManifest.xml").readText()

    @Test
    fun fixedMatrixCannotBeChangedByAdbAndProductionStillMarksAllTargetsFirst() {
        assertTrue(benchmark.contains("RESOURCE_VERSION_BATCH_SCHEDULING_BENCHMARK"))
        assertTrue(benchmark.contains("rf1720_resource_version_batch_scheduling"))
        assertTrue(benchmark.contains("private const val ROUNDS = 3"))
        assertTrue(benchmark.contains("private const val FIXED_PROBE_DELAY_MS = 180L"))
        assertTrue(benchmark.contains("REQUIRED_REDUCTION_PERCENT = 40.0"))
        assertTrue(benchmark.contains("MAXIMUM_CANDIDATE_P95_MS = 550L"))
        assertTrue(benchmark.contains("STRUCTURED_NATIVE_REMOTE_LIMIT = 3"))
        assertTrue(benchmark.contains("PROOT_COMPATIBILITY_LIMIT = 1"))
        assertTrue(benchmark.contains("FixedRequest(\"native-current\""))
        assertTrue(benchmark.contains("FixedRequest(\"compat-available\""))
        assertTrue(benchmark.contains("FixedRequest(\"native-failed\""))
        assertFalse(benchmark.contains("getStringExtra"))
        assertFalse(benchmark.contains("getIntExtra"))
        assertFalse(benchmark.contains("getLongExtra"))
        assertTrue(manifest.contains("com.kite.app.debug.RESOURCE_VERSION_BATCH_SCHEDULING_BENCHMARK"))
        assertTrue(manifest.contains("ResourceVersionBatchSchedulingBenchmarkService"))

        val checking = gateway.indexOf(
            "targets.forEach { target -> installStore.markUpdateChecking(target.id, environmentId) }"
        )
        val firstProbe = gateway.indexOf("targets.map { target ->", startIndex = checking)
        assertTrue(checking >= 0)
        assertTrue(firstProbe > checking)
    }
}
