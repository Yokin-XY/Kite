package com.kite.app.feature.startupreport

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.StringWriter

class StartupReportProjectorTest {
    @Test
    fun `检查明细只显示异常并优先显示具体组件原因`() {
        val checks = listOf(
            check("rootfs", StartupReportCheckStatus.Passed, StartupReportCheckKind.Rootfs),
            check("toolchain", StartupReportCheckStatus.Failed, StartupReportCheckKind.Toolchain),
            check("git", StartupReportCheckStatus.Failed, StartupReportCheckKind.Resource, "missing git"),
            check("node", StartupReportCheckStatus.Passed, StartupReportCheckKind.Resource),
            check("curl", StartupReportCheckStatus.InProgress, StartupReportCheckKind.Resource),
        )

        val issues = StartupReportProjector.issues(checks)

        assertEquals(listOf("git"), issues.map { it.id })
        assertEquals("missing git", issues.single().reason)
    }

    @Test
    fun `没有具体组件失败时保留工具包总体失败`() {
        val issues = StartupReportProjector.issues(listOf(
            check("toolchain", StartupReportCheckStatus.Failed, StartupReportCheckKind.Toolchain),
        ))

        assertEquals(listOf("toolchain"), issues.map { it.id })
    }

    @Test
    fun `只有当前仍失败的具体组件提供定向重试`() {
        val currentFailure = check(
            "git",
            StartupReportCheckStatus.Failed,
            StartupReportCheckKind.Resource,
            retryResourceId = "kite.git",
        )
        val historicalFailure = check(
            "curl",
            StartupReportCheckStatus.Failed,
            StartupReportCheckKind.Resource,
        )
        val aggregateFailure = check(
            "toolchain",
            StartupReportCheckStatus.Failed,
            StartupReportCheckKind.Toolchain,
            retryResourceId = "kite.toolchain",
        )

        assertEquals("kite.git", StartupReportProjector.retryTarget(currentFailure))
        assertEquals(null, StartupReportProjector.retryTarget(historicalFailure))
        assertEquals(null, StartupReportProjector.retryTarget(aggregateFailure))
    }

    @Test
    fun `生成内容保留完整日志而不是报告层截断`() {
        val log = File.createTempFile("startup-report", ".log")
        val marker = "log-tail-marker"
        log.writeText("a".repeat(20_000) + marker)
        val report = StartupReportBundle(
            capturedAt = 1L,
            appVersion = "test",
            appVersionCode = 1L,
            deviceSummary = "device",
            startupTrace = "trace",
            runtimeLines = listOf("runtime=true"),
            toolchainLines = listOf("toolchain=true"),
            environmentId = "default",
            checks = emptyList(),
            logFiles = listOf(StartupReportLogFile("log", log)),
        )
        val writer = StringWriter()

        try {
            StartupReportExporter.writeReport(writer, report)
        } finally {
            log.delete()
        }

        assertTrue(writer.toString().contains(marker))
        assertFalse(writer.toString().contains("truncated"))
    }

    private fun check(
        id: String,
        status: StartupReportCheckStatus,
        kind: StartupReportCheckKind,
        reason: String = "",
        retryResourceId: String = "",
    ) = StartupReportCheck(
        id = id,
        title = id,
        source = id,
        status = status,
        reason = reason,
        kind = kind,
        retryResourceId = retryResourceId,
    )
}
