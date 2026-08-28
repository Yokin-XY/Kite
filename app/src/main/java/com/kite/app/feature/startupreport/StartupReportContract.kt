package com.kite.app.feature.startupreport

import java.io.File

internal enum class StartupReportCheckStatus {
    Passed,
    Failed,
    InProgress,
    NotRecorded,
}

internal enum class StartupReportCheckKind {
    AppStartup,
    Permission,
    Rootfs,
    Bootstrap,
    Toolchain,
    Resource,
}

internal data class StartupReportCheck(
    val id: String,
    val title: String,
    val source: String,
    val status: StartupReportCheckStatus,
    val reason: String = "",
    val updatedAt: Long = 0L,
    val kind: StartupReportCheckKind,
)

internal data class StartupReportLogFile(
    val label: String,
    val file: File,
)

internal data class StartupReportBundle(
    val capturedAt: Long,
    val appVersion: String,
    val appVersionCode: Long,
    val deviceSummary: String,
    val startupTrace: String,
    val runtimeLines: List<String>,
    val toolchainLines: List<String>,
    val environmentId: String,
    val checks: List<StartupReportCheck>,
    val logFiles: List<StartupReportLogFile>,
) {
    val issues: List<StartupReportCheck>
        get() = StartupReportProjector.issues(checks)
}

internal object StartupReportProjector {
    fun issues(checks: List<StartupReportCheck>): List<StartupReportCheck> {
        val failed = checks.filter { it.status == StartupReportCheckStatus.Failed }
        val exactResourceFailure = failed.any { it.kind == StartupReportCheckKind.Resource }
        val exactRootfsFailure = failed.any { it.kind == StartupReportCheckKind.Rootfs }
        return failed.filterNot { check ->
            (exactResourceFailure && check.kind == StartupReportCheckKind.Toolchain) ||
                (exactRootfsFailure && check.kind == StartupReportCheckKind.Bootstrap)
        }
    }
}
