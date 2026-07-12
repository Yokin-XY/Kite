package com.kite.app.application.runtimebootstrap

enum class RuntimePermissionKind {
    FileRead,
    FileWrite,
    Notifications
}

data class RuntimePermissionSnapshot(
    val missing: Set<RuntimePermissionKind> = emptySet(),
    val needsAllFilesAccess: Boolean = false
) {
    val ready: Boolean
        get() = missing.isEmpty() && !needsAllFilesAccess
}

enum class RuntimeBootstrapStage {
    Idle,
    ServiceRequested,
    RootfsExtracting,
    BaseBootstrap,
    SpaceReady,
    Ready,
    Failed
}

enum class RuntimeRootfsPhase {
    Idle,
    Preparing,
    Extracting,
    Verifying,
    Ready,
    Failed
}

data class RuntimeRootfsSnapshot(
    val phase: RuntimeRootfsPhase = RuntimeRootfsPhase.Idle,
    val percent: Int? = null,
    val entriesExtracted: Int = 0,
    val bytesRead: Long = 0L,
    val message: String = "",
    val errorMessage: String? = null,
    val startedAt: Long = 0L
)

data class RuntimeDeploymentProgress(
    val active: Boolean = false,
    val title: String = "",
    val detail: String = "",
    val percent: Int? = null
)

/** runtime-status Feature 消费的原始事实，不包含 View、Dialog 或页面展开状态。 */
data class RuntimeBootstrapSnapshot(
    val permissions: RuntimePermissionSnapshot = RuntimePermissionSnapshot(),
    val bootstrapStage: RuntimeBootstrapStage = RuntimeBootstrapStage.Idle,
    val bootstrapError: String? = null,
    val rootfs: RuntimeRootfsSnapshot = RuntimeRootfsSnapshot(),
    val deployment: RuntimeDeploymentProgress = RuntimeDeploymentProgress(),
    val readinessProbeCompleted: Boolean = false,
    val baseImageReady: Boolean = false,
    val defaultContainerReady: Boolean = false,
    val bootstrapResourcesSettled: Boolean = false,
    val refreshedAt: Long = 0L
)
