package com.kite.app.feature.startupreport

import android.content.Context
import com.kite.app.foundation.bootstrap.DiagnosticReportFileWriter
import com.kite.app.foundation.bootstrap.GeneratedDiagnosticReport
import java.io.Writer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal object StartupReportExporter {
    fun generate(context: Context, report: StartupReportBundle): GeneratedDiagnosticReport {
        val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date(report.capturedAt))
        return DiagnosticReportFileWriter.write(
            context = context,
            displayName = "kite-first-run-report-$timestamp.txt",
        ) { writer -> writeReport(writer, report) }
    }

    internal fun writeReport(writer: Writer, report: StartupReportBundle) {
        writer.apply {
            section("Kite 首次运行报告") {
                line("capturedAt=${report.capturedAt}")
                line("appVersion=${report.appVersion}")
                line("appVersionCode=${report.appVersionCode}")
                line("device=${report.deviceSummary}")
                line("environmentId=${report.environmentId}")
            }
            writer.section("启动阶段记录") {
                line(report.startupTrace)
            }
            writer.section("运行环境原始状态") {
                report.runtimeLines.forEach { value -> line(value) }
            }
            writer.section("内置工具包原始状态") {
                report.toolchainLines.forEach { value -> line(value) }
            }
            writer.section("逐项检查结果") {
                report.checks.forEach { check ->
                    line("id=${check.id}")
                    line("title=${check.title}")
                    line("source=${check.source}")
                    line("status=${check.status}")
                    line("reason=${check.reason}")
                    line("updatedAt=${check.updatedAt}")
                    line()
                }
            }
            report.logFiles.forEach { log ->
                section(log.label) {
                    line("path=${log.file.absolutePath}")
                    line("exists=${log.file.isFile}")
                    line("bytes=${if (log.file.isFile) log.file.length() else 0L}")
                    line()
                    if (log.file.isFile) {
                        log.file.reader(Charsets.UTF_8).use { reader -> reader.copyTo(writer) }
                        line()
                    }
                }
            }
        }
    }

    private inline fun Writer.section(title: String, block: Writer.() -> Unit) {
        appendLine("==================== $title ====================")
        block()
        appendLine()
    }

    private fun Writer.line(value: String = "") {
        append(value)
        append('\n')
    }
}
