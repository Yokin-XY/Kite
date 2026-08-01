package com.kite.app.platform.resources

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ManagedCommandProofBenchmarkContractTest {
    private val source = File(
        "src/debug/kotlin/com/kite/app/platform/resources/ManagedCommandProofBenchmarkReceiver.kt"
    ).readText()
    private val manifest = File("src/debug/AndroidManifest.xml").readText()

    @Test
    fun benchmarkIsFixedAndDebugOnly() {
        assertTrue(source.contains("MANAGED_COMMAND_PROOF_BENCHMARK"))
        assertTrue(source.contains("rf1520_managed_command_proof"))
        assertTrue(source.contains("private const val PREFIX = \"kite-rf1520\""))
        assertTrue(source.contains("\"\$PREFIX-present\""))
        assertTrue(source.contains("\"\$PREFIX-missing\""))
        assertTrue(source.contains("\"\$PREFIX-broken\""))
        assertTrue(source.contains("\"\$PREFIX-nonexec\""))
        assertTrue(source.contains("managedCommandVerificationBasis"))
        assertTrue(source.contains("command -v"))
        assertTrue(source.contains("LinkOption.NOFOLLOW_LINKS"))
        assertTrue(source.contains("Files.deleteIfExists"))
        assertTrue(source.contains("fixturesCleanedOnExit=\$fixturesCleanedOnExit"))
        assertFalse(source.contains("getStringExtra"))
        assertFalse(source.contains("getIntExtra"))
        assertFalse(source.contains("resourceId"))
        assertTrue(manifest.contains("com.kite.app.debug.MANAGED_COMMAND_PROOF_BENCHMARK"))
        assertTrue(manifest.contains("ManagedCommandProofBenchmarkService"))
    }
}
