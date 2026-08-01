package com.kite.app.platform.resources

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ManagedPackageVersionBenchmarkContractTest {
    private val source = File(
        "src/debug/kotlin/com/kite/app/platform/resources/ManagedPackageVersionBenchmarkReceiver.kt"
    ).readText()
    private val manifest = File("src/debug/AndroidManifest.xml").readText()

    @Test
    fun benchmarkIsFixedDebugOnlyAndCannotAcceptAdbSamples() {
        assertTrue(source.contains("MANAGED_PACKAGE_VERSION_BENCHMARK"))
        assertTrue(source.contains("rf1620_managed_package_version"))
        assertTrue(source.contains("private const val ROUNDS = 9"))
        assertTrue(source.contains("REQUIRED_REDUCTION_PERCENT = 70.0"))
        assertTrue(source.contains("REQUIRED_BATCH_REDUCTION_PERCENT = 60.0"))
        assertTrue(source.contains("MAXIMUM_NATIVE_P95_US = 30_000L"))
        assertTrue(source.contains("structured(\"plain\""))
        assertTrue(source.contains("structured(\"scoped\""))
        assertTrue(source.contains("structured(\"symlink\""))
        assertTrue(source.contains("id = \"path_escape\""))
        assertTrue(source.contains("RuntimeProviderDecision.Ready"))
        assertTrue(source.contains("RuntimeProviderDecision.Unsupported"))
        assertTrue(source.contains("RuntimeProviderDecision.Blocked"))
        assertTrue(source.contains("LinkOption.NOFOLLOW_LINKS"))
        assertTrue(source.contains("fixturesCleanedOnExit=\$fixturesCleanedOnExit"))
        assertFalse(source.contains("getStringExtra"))
        assertFalse(source.contains("getIntExtra"))
        assertFalse(source.contains("getLongExtra"))
        assertTrue(manifest.contains("com.kite.app.debug.MANAGED_PACKAGE_VERSION_BENCHMARK"))
        assertTrue(manifest.contains("ManagedPackageVersionBenchmarkService"))
    }
}
