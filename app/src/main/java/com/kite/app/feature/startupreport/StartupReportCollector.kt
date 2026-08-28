package com.kite.app.feature.startupreport

import android.content.Context
import android.os.Build
import com.kite.app.application.runtimebootstrap.RuntimeBootstrapDependenciesOwner
import com.kite.app.application.runtimebootstrap.RuntimeBootstrapStage
import com.kite.app.application.runtimebootstrap.RuntimePermissionKind
import com.kite.app.application.runtimebootstrap.RuntimeRootfsPhase
import com.kite.app.foundation.bootstrap.StartupTraceStore
import com.kite.app.foundation.logging.Logger
import com.kite.app.foundation.toolchain.ToolchainInstallPhase
import com.kite.app.foundation.toolchain.ToolchainPackInstaller
import com.kite.app.resources.KiteResourceRegistry
import com.kite.app.shell.KiteAppGraph
import java.io.File

internal object StartupReportCollector {
    fun collect(context: Context): StartupReportBundle {
        val appContext = context.applicationContext
        val runtimeOwner = appContext as? RuntimeBootstrapDependenciesOwner
            ?: error("Application 必须提供 RuntimeBootstrapGateway")
        val runtime = runtimeOwner.runtimeBootstrapGateway.currentSnapshot()
        ToolchainPackInstaller.refreshState(appContext)
        val toolchain = ToolchainPackInstaller.state.value
        val descriptors = ToolchainPackInstaller.bootstrapResourceDescriptors()
        val graph = KiteAppGraph.from(appContext)
        val installStore = graph.resourceInstallStore
        val environmentId = installStore.currentEnvironmentId()
        val entries = installStore.registrySnapshot(
            descriptors.map { it.resourceId },
            environmentId,
        )
        val checks = buildList {
            StartupTraceStore.readFailure(appContext)?.let { failure ->
                add(StartupReportCheck(
                    id = "app-startup",
                    title = "应用启动未完成",
                    source = "应用启动 · ${failure.stage}",
                    status = StartupReportCheckStatus.Failed,
                    reason = failure.exceptionMessage.ifBlank { failure.exceptionClass.ifBlank { failure.status } },
                    updatedAt = failure.occurredAtMs,
                    kind = StartupReportCheckKind.AppStartup,
                ))
            }
            if (!runtime.permissions.ready) {
                val missing = buildList {
                    runtime.permissions.missing.forEach { permission ->
                        add(when (permission) {
                            RuntimePermissionKind.FileRead -> "读取文件"
                            RuntimePermissionKind.FileWrite -> "写入文件"
                            RuntimePermissionKind.Notifications -> "通知"
                        })
                    }
                    if (runtime.permissions.needsAllFilesAccess) add("所有文件访问")
                }
                add(StartupReportCheck(
                    id = "runtime-permissions",
                    title = "运行环境权限不完整",
                    source = "系统权限",
                    status = StartupReportCheckStatus.Failed,
                    reason = "缺少：${missing.joinToString("、")}",
                    kind = StartupReportCheckKind.Permission,
                ))
            }
            val recordedBootstrapFailure = StartupTraceStore.readSetupFailure(appContext, "runtime.bootstrap")
            val recordedRootfsFailure = recordedBootstrapFailure
                ?.takeIf { it.reason.startsWith("ROOTFS_EXTRACTING:") }
            if (runtime.rootfs.phase == RuntimeRootfsPhase.Failed || recordedRootfsFailure != null) {
                add(StartupReportCheck(
                    id = "rootfs",
                    title = "Ubuntu 空间解压失败",
                    source = "rootfs 解压与校验",
                    status = StartupReportCheckStatus.Failed,
                    reason = runtime.rootfs.errorMessage.orEmpty().ifBlank {
                        recordedRootfsFailure?.reason.orEmpty().ifBlank {
                            runtime.rootfs.message.ifBlank { "未记录失败原因" }
                        }
                    },
                    updatedAt = recordedRootfsFailure?.occurredAtMs ?: runtime.refreshedAt,
                    kind = StartupReportCheckKind.Rootfs,
                ))
            }
            if (runtime.bootstrapStage == RuntimeBootstrapStage.Failed ||
                (recordedBootstrapFailure != null && recordedRootfsFailure == null)
            ) {
                add(StartupReportCheck(
                    id = "bootstrap",
                    title = "首次空间准备失败",
                    source = "启动协调器",
                    status = StartupReportCheckStatus.Failed,
                    reason = runtime.bootstrapError.orEmpty().ifBlank {
                        recordedBootstrapFailure?.reason.orEmpty().ifBlank { "未记录失败原因" }
                    },
                    updatedAt = recordedBootstrapFailure?.occurredAtMs ?: runtime.refreshedAt,
                    kind = StartupReportCheckKind.Bootstrap,
                ))
            }
            add(StartupReportCheck(
                id = "toolchain",
                title = "内置工具包准备失败",
                source = "内置工具包 · ${toolchain.packId}",
                status = toolchain.phase.toCheckStatus(),
                reason = toolchain.summary,
                updatedAt = toolchain.updatedAt ?: 0L,
                kind = StartupReportCheckKind.Toolchain,
            ))
            descriptors.forEach { descriptor ->
                val entry = entries[descriptor.resourceId]
                val recordedFailure = StartupTraceStore.readSetupFailure(appContext, descriptor.resourceId)
                val failedNow = entry?.status == KiteResourceRegistry.STATUS_FAILED
                add(StartupReportCheck(
                    id = "resource:${descriptor.resourceId}",
                    title = if (!failedNow && recordedFailure != null) {
                        "${descriptor.label} 首次安装曾失败"
                    } else {
                        "${descriptor.label} 安装失败"
                    },
                    source = "内置组件 · ${descriptor.resourceId}",
                    status = if (recordedFailure != null) {
                        StartupReportCheckStatus.Failed
                    } else when (entry?.status) {
                        KiteResourceRegistry.STATUS_INSTALLED -> StartupReportCheckStatus.Passed
                        KiteResourceRegistry.STATUS_FAILED -> StartupReportCheckStatus.Failed
                        KiteResourceRegistry.STATUS_INSTALLING,
                        KiteResourceRegistry.STATUS_PREPARING -> StartupReportCheckStatus.InProgress
                        else -> StartupReportCheckStatus.NotRecorded
                    },
                    reason = (if (failedNow) entry?.summary.orEmpty() else recordedFailure?.reason.orEmpty())
                        .ifBlank {
                        if (entry?.status == KiteResourceRegistry.STATUS_FAILED) "安装失败，但没有记录原因" else ""
                    },
                    updatedAt = recordedFailure?.occurredAtMs ?: entry?.updatedAt ?: 0L,
                    kind = StartupReportCheckKind.Resource,
                    retryResourceId = descriptor.resourceId.takeIf { failedNow }.orEmpty(),
                ))
            }
        }
        val packageInfo = appContext.packageManager.getPackageInfo(appContext.packageName, 0)
        @Suppress("DEPRECATION")
        val versionCode = packageInfo.versionCode.toLong()
        val generalLog = File(Logger.getLogFilePath())
        return StartupReportBundle(
            capturedAt = System.currentTimeMillis(),
            appVersion = packageInfo.versionName.orEmpty(),
            appVersionCode = versionCode,
            deviceSummary = buildString {
                append(Build.MANUFACTURER.ifBlank { "unknown" })
                append(' ')
                append(Build.MODEL.ifBlank { "unknown" })
                append(" / Android API ")
                append(Build.VERSION.SDK_INT)
                append(" / ")
                append(Build.SUPPORTED_ABIS.joinToString(",").ifBlank { "unknown ABI" })
                append(" / fingerprint=")
                append(Build.FINGERPRINT)
            },
            startupTrace = StartupTraceStore.traceReportText(appContext),
            runtimeLines = listOf(
                "bootstrapStage=${runtime.bootstrapStage}",
                "bootstrapError=${runtime.bootstrapError.orEmpty()}",
                "rootfs.phase=${runtime.rootfs.phase}",
                "rootfs.percent=${runtime.rootfs.percent}",
                "rootfs.entriesExtracted=${runtime.rootfs.entriesExtracted}",
                "rootfs.bytesRead=${runtime.rootfs.bytesRead}",
                "rootfs.message=${runtime.rootfs.message}",
                "rootfs.error=${runtime.rootfs.errorMessage.orEmpty()}",
                "rootfs.startedAt=${runtime.rootfs.startedAt}",
                "deployment.active=${runtime.deployment.active}",
                "deployment.title=${runtime.deployment.title}",
                "deployment.detail=${runtime.deployment.detail}",
                "deployment.percent=${runtime.deployment.percent}",
                "readinessProbeCompleted=${runtime.readinessProbeCompleted}",
                "baseImageReady=${runtime.baseImageReady}",
                "defaultContainerReady=${runtime.defaultContainerReady}",
                "bootstrapResourcesSettled=${runtime.bootstrapResourcesSettled}",
                "refreshedAt=${runtime.refreshedAt}",
            ),
            toolchainLines = listOf(
                "phase=${toolchain.phase}",
                "action=${toolchain.action}",
                "packId=${toolchain.packId}",
                "packVersion=${toolchain.packVersion}",
                "startedAt=${toolchain.startedAt}",
                "updatedAt=${toolchain.updatedAt}",
                "exitCode=${toolchain.exitCode}",
                "timedOut=${toolchain.timedOut}",
                "summary=${toolchain.summary}",
                "logPath=${toolchain.logPath}",
                "outputPreview=${toolchain.outputPreview}",
            ),
            environmentId = environmentId,
            checks = checks,
            logFiles = listOf(
                StartupReportLogFile("Kite 运行日志（上一份）", File(generalLog.parentFile, "kftest.old.log")),
                StartupReportLogFile("Kite 运行日志", generalLog),
                StartupReportLogFile("内置工具包安装日志", ToolchainPackInstaller.logFile(appContext)),
            ),
        )
    }

    private fun ToolchainInstallPhase.toCheckStatus(): StartupReportCheckStatus = when (this) {
        ToolchainInstallPhase.SUCCEEDED -> StartupReportCheckStatus.Passed
        ToolchainInstallPhase.FAILED -> StartupReportCheckStatus.Failed
        ToolchainInstallPhase.RUNNING -> StartupReportCheckStatus.InProgress
        ToolchainInstallPhase.IDLE -> StartupReportCheckStatus.NotRecorded
    }
}
