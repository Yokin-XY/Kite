package com.kite.app.foundation.runtime

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KiteTestProfileScriptContractTest {
    private val testRunner = File("../scripts/run-kite-tests.ps1").readText()
    private val gradleRunner = File("../scripts/invoke-kite-gradle.ps1").readText()
    private val gradleWorker = File("../scripts/invoke-kite-gradle-worker.ps1").readText()

    @Test
    fun threeProfilesKeepFullAndRequireExplicitStageScope() {
        assertTrue(testRunner.contains("ValidateSet('Quick', 'Stage', 'Full')"))
        assertTrue(testRunner.contains("Stage 必须通过 -Tests"))
        assertTrue(testRunner.contains("Stage 测试模式没有匹配源码类"))
        assertTrue(testRunner.contains("':app:testDebugUnitTest'"))
        assertTrue(testRunner.contains("'*ContractTest'"))
        assertTrue(testRunner.contains("'*ProtocolTest'"))
        assertTrue(testRunner.contains("'*RoutingTest'"))
        assertTrue(testRunner.contains("'*PolicyTest'"))
        assertTrue(testRunner.contains("'*SchemaTest'"))
        assertTrue(testRunner.contains("'*GuardTest'"))
        assertTrue(testRunner.contains("Quick 测试类超过全量 25%"))
        assertTrue(testRunner.contains("quickSourceClasses"))
        assertTrue(testRunner.contains("totalSourceClasses"))
        assertFalse(testRunner.contains("excludeTestsMatching"))
    }

    @Test
    fun localGradleRunnerSerializesWithoutStoppingOtherDaemons() {
        assertTrue(gradleRunner.contains("invoke-kite-gradle-worker.ps1"))
        assertTrue(gradleRunner.contains("Start-Process"))
        assertTrue(gradleRunner.contains("-Wait"))
        assertTrue(gradleRunner.contains("实际 Gradle 结束前也不会提前放锁"))
        assertTrue(gradleWorker.contains("Local\\KiteGradleBuildV1"))
        assertTrue(gradleWorker.contains("AbandonedMutexException"))
        assertTrue(gradleWorker.contains("--no-daemon"))
        assertTrue(gradleWorker.contains("--console=plain"))
        assertTrue(gradleWorker.contains("KITE_GRADLE_LOCK status=acquired"))
        assertTrue(gradleWorker.contains("KITE_GRADLE_LOCK status=released"))
        assertFalse(gradleRunner.contains("gradlew --stop"))
        assertFalse(gradleRunner.contains("Remove-Item"))
        assertFalse(gradleWorker.contains("gradlew --stop"))
        assertFalse(gradleWorker.contains("Remove-Item"))
    }
}
