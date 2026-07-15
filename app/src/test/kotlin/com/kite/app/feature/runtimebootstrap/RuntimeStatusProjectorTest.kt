package com.kite.app.feature.runtimebootstrap

import com.kite.app.application.runtimebootstrap.RuntimeBootstrapSnapshot
import com.kite.app.application.runtimebootstrap.RuntimeBootstrapStage
import com.kite.app.application.runtimebootstrap.RuntimeDeploymentProgress
import com.kite.app.application.runtimebootstrap.RuntimePermissionKind
import com.kite.app.application.runtimebootstrap.RuntimePermissionSnapshot
import com.kite.app.application.runtimebootstrap.RuntimeRootfsPhase
import com.kite.app.application.runtimebootstrap.RuntimeRootfsSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeStatusProjectorTest {
    @Test
    fun allReadyProjectsHiddenStatusWithManagementCounts() {
        val state = RuntimeStatusProjector.project(
            snapshot = RuntimeBootstrapSnapshot(
                readinessProbeCompleted = true,
                baseImageReady = true,
                defaultContainerReady = true,
                bootstrapResourcesSettled = true
            ),
            counts = RuntimeStatusCounts(2, 3, 9)
        )

        assertFalse(state.visible)
        assertFalse(state.blocksUbuntuActions)
        assertEquals("就绪", state.statusLabel)
        assertEquals(9, state.counts.runningProcesses)
        assertEquals(RuntimeStatusAction.OpenProcessManagement, state.primaryAction)
    }

    @Test
    fun missingRuntimePermissionProjectsExplicitPermissionAction() {
        val state = RuntimeStatusProjector.project(
            RuntimeBootstrapSnapshot(
                permissions = RuntimePermissionSnapshot(setOf(RuntimePermissionKind.FileRead)),
                readinessProbeCompleted = true
            )
        )

        assertTrue(state.shouldShowGate)
        assertTrue(state.requiresPermission)
        assertEquals(RuntimeStatusAction.RequestRuntimePermissions, state.primaryAction)
        assertEquals("待授权", state.statusLabel)
    }

    @Test
    fun allFilesRequirementDoesNotMasqueradeAsRuntimePermissionDialog() {
        val state = RuntimeStatusProjector.project(
            RuntimeBootstrapSnapshot(
                permissions = RuntimePermissionSnapshot(needsAllFilesAccess = true),
                readinessProbeCompleted = true
            )
        )

        assertEquals(RuntimeStatusAction.OpenAllFilesSettings, state.primaryAction)
        assertEquals("打开文件访问设置", state.primaryActionLabel)
    }

    @Test
    fun rootfsProgressWinsOverBootstrapStageAndCarriesGeneration() {
        val state = RuntimeStatusProjector.project(
            RuntimeBootstrapSnapshot(
                bootstrapStage = RuntimeBootstrapStage.RootfsExtracting,
                rootfs = RuntimeRootfsSnapshot(
                    phase = RuntimeRootfsPhase.Extracting,
                    percent = 40,
                    entriesExtracted = 12,
                    startedAt = 88L
                )
            )
        )

        assertEquals("正在解压 Ubuntu 系统镜像", state.title)
        assertEquals(23, state.progressPercent)
        assertEquals(88L, state.autoOpenGeneration)
        assertTrue(state.autoOpenPanel)
    }

    @Test
    fun deploymentProgressWinsAfterRootfsHasNoActivePresentation() {
        val state = RuntimeStatusProjector.project(
            RuntimeBootstrapSnapshot(
                bootstrapStage = RuntimeBootstrapStage.BaseBootstrap,
                rootfs = RuntimeRootfsSnapshot(phase = RuntimeRootfsPhase.Ready),
                deployment = RuntimeDeploymentProgress(
                    active = true,
                    title = "正在安装工具",
                    detail = "第 1 项",
                    percent = 80
                )
            )
        )

        assertEquals("正在安装工具", state.title)
        assertEquals(80, state.progressPercent)
        assertEquals("总进度 80%", state.progressText)
    }

    @Test
    fun failureProjectsRetryWithoutPretendingRuntimeStopped() {
        val state = RuntimeStatusProjector.project(
            RuntimeBootstrapSnapshot(
                bootstrapStage = RuntimeBootstrapStage.Failed,
                bootstrapError = "bootstrap failed"
            )
        )

        assertTrue(state.isProblem)
        assertFalse(state.blocksUbuntuActions)
        assertEquals(RuntimeStatusAction.RetryDeployment, state.primaryAction)
        assertEquals("bootstrap failed", state.detail)
    }

    @Test
    fun firstRunOnboardingIsAnExplicitProjectionOverride() {
        val state = RuntimeStatusProjector.project(
            snapshot = RuntimeBootstrapSnapshot(
                readinessProbeCompleted = true,
                baseImageReady = true,
                defaultContainerReady = true,
                bootstrapResourcesSettled = true
            ),
            onboarding = RuntimePermissionOnboardingUiInput(
                active = true,
                missingPermissions = setOf(RuntimePermissionKind.Notifications)
            )
        )

        assertTrue(state.firstRunPermissionOnboarding)
        assertEquals("开始授权", state.primaryActionLabel)
        assertTrue(state.detail.contains("系统通知"))
    }

    @Test
    fun notificationChannelReviewHasExplicitSystemSettingsSemantics() {
        val state = RuntimeStatusProjector.project(
            snapshot = RuntimeBootstrapSnapshot(),
            onboarding = RuntimePermissionOnboardingUiInput(
                active = true,
                needsNotificationChannelSetup = true
            )
        )

        assertEquals("设置卡片通知", state.primaryActionLabel)
        assertTrue(state.detail.contains("首页卡片进度"))
        assertTrue(state.detail.contains("横幅"))
    }
}
