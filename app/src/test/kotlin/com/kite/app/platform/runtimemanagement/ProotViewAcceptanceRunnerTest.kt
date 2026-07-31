package com.kite.app.platform.runtimemanagement

import com.kite.app.application.runtimemanagement.ProotEnvironmentIsolationResult
import com.kite.app.application.runtimemanagement.ProotViewVerificationResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.atomic.AtomicLong

@RunWith(RobolectricTestRunner::class)
class ProotViewAcceptanceRunnerTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `组合既有真实夹具并持久保存逐项验收结果`() {
        val report = temporaryFolder.newFile("latest.json")
        report.delete()
        val clock = AtomicLong(0L)
        val runner = ProotViewAcceptanceRunner(
            reportFile = report,
            runViewVerification = {
                ProotViewVerificationResult(
                    success = true,
                    runCount = 3,
                    viewId = "view-default",
                    environmentId = "default",
                    fileSha256 = "a".repeat(64),
                    message = "通过",
                )
            },
            runEnvironmentIsolation = {
                ProotEnvironmentIsolationResult(
                    success = true,
                    firstEnvironmentId = "default",
                    secondEnvironmentId = "profile_2",
                    rootIsolated = true,
                    workspaceIsolated = true,
                    exchangeShared = true,
                    baseUntouched = true,
                    originalEnvironmentRestored = true,
                    message = "通过",
                )
            },
            now = { 1234L },
            monotonicNanos = { clock.addAndGet(1_000_000L) },
        )

        val execution = runner.run()

        assertTrue(execution.result.success)
        assertEquals(6, execution.result.checks.size)
        assertEquals(
            listOf(
                "ordinary_view",
                "environment_rootfs_isolation",
                "environment_workspace_isolation",
                "explicit_exchange_sharing",
                "base_immutable",
                "original_environment_restored",
            ),
            execution.result.checks.map { it.id },
        )
        assertEquals("default", execution.result.environmentId)
        assertEquals("view-default", execution.result.viewId)
        assertEquals(5L, execution.result.totalMs)
        assertEquals(execution.result, runner.latest())
    }

    @Test
    fun `单项失败不会被总结果吞掉`() {
        val report = temporaryFolder.newFile("failed.json")
        report.delete()
        val runner = ProotViewAcceptanceRunner(
            reportFile = report,
            runViewVerification = {
                ProotViewVerificationResult(success = false, message = "CRUD 失败")
            },
            runEnvironmentIsolation = {
                ProotEnvironmentIsolationResult(
                    success = false,
                    rootIsolated = true,
                    workspaceIsolated = false,
                    exchangeShared = true,
                    baseUntouched = true,
                    originalEnvironmentRestored = true,
                )
            },
            now = { 1L },
            monotonicNanos = System::nanoTime,
        )

        val result = runner.run().result

        assertFalse(result.success)
        assertEquals(
            listOf("ordinary_view", "environment_workspace_isolation"),
            result.checks.filterNot { it.passed }.map { it.id },
        )
        assertTrue(result.checks.first().detail.contains("CRUD 失败"))
    }

    @Test
    fun `损坏的旧报告不会阻止工程页启动`() {
        val report = temporaryFolder.newFile("broken.json").apply { writeText("not-json") }
        val runner = ProotViewAcceptanceRunner(
            reportFile = report,
            runViewVerification = { error("不应执行") },
            runEnvironmentIsolation = { error("不应执行") },
            now = { 1L },
            monotonicNanos = System::nanoTime,
        )

        assertNull(runner.latest())
    }
}
