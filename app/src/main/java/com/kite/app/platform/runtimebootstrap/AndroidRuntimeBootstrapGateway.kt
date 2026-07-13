package com.kite.app.platform.runtimebootstrap

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import com.kite.app.application.runtimebootstrap.RuntimeBootstrapGateway
import com.kite.app.application.runtimebootstrap.RuntimeBootstrapSnapshot
import com.kite.app.application.runtimebootstrap.RuntimeBootstrapStage
import com.kite.app.application.runtimebootstrap.RuntimeDeploymentProgress
import com.kite.app.application.runtimebootstrap.RuntimePermissionKind
import com.kite.app.application.runtimebootstrap.RuntimePermissionSnapshot
import com.kite.app.application.runtimebootstrap.RuntimeRootfsPhase
import com.kite.app.application.runtimebootstrap.RuntimeRootfsSnapshot
import com.kite.app.foundation.bootstrap.BootstrapCoordinator
import com.kite.app.foundation.bootstrap.BootstrapSnapshot
import com.kite.app.foundation.bootstrap.BootstrapStage
import com.kite.app.foundation.runtime.AssetExtractor
import com.kite.app.foundation.runtime.RuntimeBootstrapProgress
import com.kite.app.foundation.runtime.RuntimeBootstrapProgressSnapshot
import com.kite.app.foundation.toolchain.ToolchainPackInstaller
import com.kite.app.foundation.workspace.WorkSurfaceRuntimeBridge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Android runtime readiness and permission adapter. Expensive readiness probes run off the UI thread. */
internal class AndroidRuntimeBootstrapGateway(context: Context) : RuntimeBootstrapGateway {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val probe = MutableStateFlow(RuntimeReadinessProbe())
    @Volatile
    private var ensureReadyInFlight = false

    override val snapshots: StateFlow<RuntimeBootstrapSnapshot> = combine(
        BootstrapCoordinator.snapshot,
        AssetExtractor.rootfsProgress,
        RuntimeBootstrapProgress.snapshot,
        probe
    ) { bootstrap, rootfs, deployment, readiness ->
        mapSnapshot(bootstrap, rootfs, deployment, readiness, permissionSnapshot())
    }.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = currentSnapshot()
    )

    init {
        refresh()
        scope.launch(Dispatchers.IO) {
            BootstrapCoordinator.snapshot
                .map { it.stage }
                .distinctUntilChanged()
                .filter(::shouldRefreshReadiness)
                .collect {
                    probe.value = probeReadiness()
                }
        }
    }

    override fun currentSnapshot(): RuntimeBootstrapSnapshot = mapSnapshot(
        bootstrap = BootstrapCoordinator.snapshot.value,
        rootfs = AssetExtractor.rootfsProgress.value,
        deployment = RuntimeBootstrapProgress.snapshot.value,
        readiness = probe.value,
        permissions = permissionSnapshot()
    )

    override fun refresh() {
        scope.launch(Dispatchers.IO) {
            probe.value = probeReadiness()
        }
    }

    override fun ensureReady() {
        if (ensureReadyInFlight) return
        ensureReadyInFlight = true
        scope.launch(Dispatchers.IO) {
            try {
                val readiness = probeReadiness()
                probe.value = readiness
                if (
                    permissionSnapshot().ready &&
                    !(readiness.baseImageReady && readiness.defaultContainerReady && readiness.bootstrapResourcesSettled)
                ) {
                    BootstrapCoordinator.ensureStarted(appContext)
                }
            } finally {
                ensureReadyInFlight = false
            }
        }
    }

    private fun probeReadiness(): RuntimeReadinessProbe {
        val baseReady = runCatching {
            WorkSurfaceRuntimeBridge.isBaseImageReady(appContext)
        }.getOrDefault(false)
        val containerReady = baseReady && runCatching {
            WorkSurfaceRuntimeBridge.isDefaultContainerReady(appContext)
        }.getOrDefault(false)
        val resourcesSettled = containerReady && runCatching {
            ToolchainPackInstaller.bootstrapResourcesSettled(appContext)
        }.getOrDefault(false)
        return RuntimeReadinessProbe(
            completed = true,
            baseImageReady = baseReady,
            defaultContainerReady = containerReady,
            bootstrapResourcesSettled = resourcesSettled,
            refreshedAt = System.currentTimeMillis()
        )
    }

    private fun permissionSnapshot(): RuntimePermissionSnapshot {
        val missing = buildSet {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                if (appContext.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                    add(RuntimePermissionKind.FileRead)
                }
                if (
                    Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q &&
                    appContext.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
                ) {
                    add(RuntimePermissionKind.FileWrite)
                }
            }
        }
        return RuntimePermissionSnapshot(
            missing = missing,
            needsAllFilesAccess = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                !Environment.isExternalStorageManager()
        )
    }

    companion object {
        internal data class RuntimeReadinessProbe(
            val completed: Boolean = false,
            val baseImageReady: Boolean = false,
            val defaultContainerReady: Boolean = false,
            val bootstrapResourcesSettled: Boolean = false,
            val refreshedAt: Long = 0L
        )

        internal fun shouldRefreshReadiness(stage: BootstrapStage): Boolean =
            stage == BootstrapStage.READY || stage == BootstrapStage.FAILED

        internal fun mapSnapshot(
            bootstrap: BootstrapSnapshot,
            rootfs: AssetExtractor.RootfsExtractionProgress,
            deployment: RuntimeBootstrapProgressSnapshot,
            readiness: RuntimeReadinessProbe,
            permissions: RuntimePermissionSnapshot
        ): RuntimeBootstrapSnapshot = RuntimeBootstrapSnapshot(
            permissions = permissions,
            bootstrapStage = bootstrap.stage.toContractStage(),
            bootstrapError = bootstrap.lastError,
            rootfs = RuntimeRootfsSnapshot(
                phase = rootfs.phase.toContractPhase(),
                percent = rootfs.percent,
                entriesExtracted = rootfs.entriesExtracted,
                bytesRead = rootfs.bytesRead,
                message = rootfs.message,
                errorMessage = rootfs.errorMessage,
                startedAt = rootfs.startedAt
            ),
            deployment = RuntimeDeploymentProgress(
                active = deployment.active,
                title = deployment.title,
                detail = deployment.detail,
                percent = deployment.percent
            ),
            readinessProbeCompleted = readiness.completed,
            baseImageReady = readiness.baseImageReady,
            defaultContainerReady = readiness.defaultContainerReady,
            bootstrapResourcesSettled = readiness.bootstrapResourcesSettled,
            refreshedAt = maxOf(readiness.refreshedAt, rootfs.updatedAt, deployment.updatedAt)
        )

        private fun BootstrapStage.toContractStage(): RuntimeBootstrapStage = when (this) {
            BootstrapStage.IDLE -> RuntimeBootstrapStage.Idle
            BootstrapStage.SERVICE_REQUESTED -> RuntimeBootstrapStage.ServiceRequested
            BootstrapStage.ROOTFS_EXTRACTING -> RuntimeBootstrapStage.RootfsExtracting
            BootstrapStage.BASE_BOOTSTRAP -> RuntimeBootstrapStage.BaseBootstrap
            BootstrapStage.SPACE_READY -> RuntimeBootstrapStage.SpaceReady
            BootstrapStage.READY -> RuntimeBootstrapStage.Ready
            BootstrapStage.FAILED -> RuntimeBootstrapStage.Failed
        }

        private fun AssetExtractor.RootfsExtractionPhase.toContractPhase(): RuntimeRootfsPhase = when (this) {
            AssetExtractor.RootfsExtractionPhase.IDLE -> RuntimeRootfsPhase.Idle
            AssetExtractor.RootfsExtractionPhase.PREPARING -> RuntimeRootfsPhase.Preparing
            AssetExtractor.RootfsExtractionPhase.EXTRACTING -> RuntimeRootfsPhase.Extracting
            AssetExtractor.RootfsExtractionPhase.VERIFYING -> RuntimeRootfsPhase.Verifying
            AssetExtractor.RootfsExtractionPhase.READY -> RuntimeRootfsPhase.Ready
            AssetExtractor.RootfsExtractionPhase.FAILED -> RuntimeRootfsPhase.Failed
        }
    }
}
