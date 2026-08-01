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
    private val scheduler = File(
        "src/main/java/com/kite/app/application/resources/ResourceVersionBatchScheduler.kt"
    ).readText()
    private val coordinator = File(
        "src/main/java/com/kite/app/application/resources/ResourceVersionCoordinator.kt"
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
        assertTrue(scheduler.contains("STRUCTURED_NATIVE_REMOTE_LIMIT = 3"))
        assertTrue(scheduler.contains("PROOT_COMPATIBILITY_LIMIT = 1"))
        assertTrue(benchmark.contains("FixedRequest(\"native-current\""))
        assertTrue(benchmark.contains("FixedRequest(\"compat-available\""))
        assertTrue(benchmark.contains("FixedRequest(\"native-failed\""))
        assertFalse(benchmark.contains("getStringExtra"))
        assertFalse(benchmark.contains("getIntExtra"))
        assertFalse(benchmark.contains("getLongExtra"))
        assertTrue(manifest.contains("com.kite.app.debug.RESOURCE_VERSION_BATCH_SCHEDULING_BENCHMARK"))
        assertTrue(manifest.contains("ResourceVersionBatchSchedulingBenchmarkService"))
        assertTrue(benchmark.contains("ResourceVersionBatchScheduler.executeOrdered"))
        assertTrue(benchmark.contains("providerSource=production_scheduler"))
        assertFalse(scheduler.contains("resourceId"))
        assertFalse(scheduler.contains("packageName"))
        assertFalse(scheduler.contains("probe.command"))
        assertFalse(scheduler.contains("KiteResourceManifest"))
        val preparation = coordinator.substringAfter("suspend fun prepareBatchCheck")
            .substringBefore("suspend fun check(prepared")
        assertTrue(preparation.contains("installedProbe.structuredMetadata"))
        assertTrue(preparation.contains("latestProbe !is KiteResourceRemoteVersionProbe"))
        assertFalse(preparation.contains("probe.command"))

        val checking = gateway.indexOf(
            "targets.forEach { target -> installStore.markUpdateChecking(target.id, environmentId) }"
        )
        val firstProbe = gateway.indexOf("versionCoordinator.prepareBatchCheck", startIndex = checking)
        val scheduling = gateway.indexOf("ResourceVersionBatchScheduler.executeOrdered", startIndex = firstProbe)
        assertTrue(checking >= 0)
        assertTrue(firstProbe > checking)
        assertTrue(scheduling > firstProbe)
    }
}
