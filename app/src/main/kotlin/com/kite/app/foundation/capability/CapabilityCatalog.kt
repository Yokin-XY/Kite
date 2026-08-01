package com.kite.app.foundation.capability

import com.kite.app.foundation.runtime.AndroidNativeArchiveCapabilityProvider
import com.kite.app.foundation.runtime.AndroidNativeDownloadCapabilityProvider
import com.kite.app.foundation.runtime.AndroidNativeFileCapabilityProvider
import com.kite.app.recipe.KiteRecipe

data class CapabilityCatalogEntry(
    val domain: CapabilityDomain,
    val examples: List<String>,
    val description: String
)

enum class CapabilityInvocationKind {
    NATIVE_RECIPE,
    ANDROID_ACTION,
    LIFECYCLE_SERVICE,
    QUERY_ONLY,
}

enum class CapabilityResultOwner {
    CARD_RUN_STORE,
    EXTERNAL_ANDROID_INSTALLER,
    RUNTIME_BOOTSTRAP_GATEWAY,
    DEFAULT_NETWORK_ALIGNMENT,
}

enum class CapabilityCompletionKind {
    RESULT,
    EXTERNAL_HANDOFF,
    CONTINUOUS_ALIGNMENT,
    SNAPSHOT,
}

enum class CapabilityPermissionGate {
    NONE,
    ANDROID_INTERNET_NORMAL,
    FILE_ROOT_POLICY,
    USER_CONFIRMATION,
    RUNTIME_PERMISSION_STATE,
}

enum class CapabilityFallbackBoundary {
    NEVER_AUTOMATIC,
    NOT_APPLICABLE,
}

data class RoutableCapabilityEntry(
    val id: String,
    val invocation: CapabilityInvocationKind,
    val resultOwner: CapabilityResultOwner,
    val completion: CapabilityCompletionKind,
    val permissionGates: Set<CapabilityPermissionGate>,
    val fallbackBoundary: CapabilityFallbackBoundary,
    val legacyAction: String? = null,
    val automaticResourceRouting: Boolean = false,
    val notes: String,
)

object CapabilityCatalog {
    val entries: List<CapabilityCatalogEntry> = listOf(
        CapabilityCatalogEntry(
            domain = CapabilityDomain.ANDROID,
            examples = listOf("filePicker", "shareFile", "uiProjection"),
            description = "Android host UI, system intents, and native projection capabilities."
        ),
        CapabilityCatalogEntry(
            domain = CapabilityDomain.WORKSPACE,
            examples = listOf("exchangeRead", "exchangeWrite", "projectFiles", "logs", "artifacts"),
            description = "Workspace, exchange, logs, and app-side file artifact access."
        ),
        CapabilityCatalogEntry(
            domain = CapabilityDomain.PROOT,
            examples = listOf("buildLaunchConfig", "shellSession", "oneShotExec"),
            description = "PRoot launch and container command execution primitives."
        ),
        CapabilityCatalogEntry(
            domain = CapabilityDomain.UBUNTU,
            examples = listOf("apt", "npm", "pip", "git", "python", "node", "bash"),
            description = "Ubuntu userspace tools and package ecosystem actions."
        ),
        CapabilityCatalogEntry(
            domain = CapabilityDomain.TERMINAL,
            examples = listOf("sessionCreate", "sessionInput", "sessionInterrupt", "transcriptMirror", "terminalOutput"),
            description = "Interactive terminal session lifecycle, input, and PTY output capabilities."
        ),
        CapabilityCatalogEntry(
            domain = CapabilityDomain.SERVICE,
            examples = listOf("backgroundStart", "backgroundStop", "supervisordStatus", "agentRuntime", "webui"),
            description = "Background runtime, supervisord, agent runtime, and local service capabilities."
        ),
        CapabilityCatalogEntry(
            domain = CapabilityDomain.OUTPUT,
            examples = listOf("streamOutput", "logTail", "reportExport", "uiRefresh"),
            description = "Streaming output, logs, reports, and UI refresh surfaces."
        ),
        CapabilityCatalogEntry(
            domain = CapabilityDomain.RUNTIME,
            examples = listOf("processSnapshot", "healthRefresh", "reconcile", "taskStatus"),
            description = "Runtime health, process sampling, reconciliation, and task status projection."
        ),
        CapabilityCatalogEntry(
            domain = CapabilityDomain.UNKNOWN,
            examples = listOf("fallback"),
            description = "Fallback bucket for legacy or uncategorized capabilities."
        )
    )

    /**
     * 只登记当前有真实生产入口的能力。它描述路由与所有权，不复制执行器、权限状态或业务结果。
     */
    val routableEntries: List<RoutableCapabilityEntry> = listOf(
        RoutableCapabilityEntry(
            id = AndroidNativeDownloadCapabilityProvider.CAPABILITY_ID,
            invocation = CapabilityInvocationKind.NATIVE_RECIPE,
            resultOwner = CapabilityResultOwner.CARD_RUN_STORE,
            completion = CapabilityCompletionKind.RESULT,
            permissionGates = setOf(CapabilityPermissionGate.ANDROID_INTERNET_NORMAL),
            fallbackBoundary = CapabilityFallbackBoundary.NEVER_AUTOMATIC,
            automaticResourceRouting = true,
            notes = "静态有界 HTTPS 下载；Android 网络栈决定 VPN、DNS 和证书。",
        ),
        RoutableCapabilityEntry(
            id = AndroidNativeFileCapabilityProvider.CAPABILITY_COPY_FILE,
            invocation = CapabilityInvocationKind.NATIVE_RECIPE,
            resultOwner = CapabilityResultOwner.CARD_RUN_STORE,
            completion = CapabilityCompletionKind.RESULT,
            permissionGates = setOf(CapabilityPermissionGate.FILE_ROOT_POLICY),
            fallbackBoundary = CapabilityFallbackBoundary.NEVER_AUTOMATIC,
            notes = "受控根内的单文件原子复制。",
        ),
        RoutableCapabilityEntry(
            id = AndroidNativeFileCapabilityProvider.CAPABILITY_MOVE_FILE,
            invocation = CapabilityInvocationKind.NATIVE_RECIPE,
            resultOwner = CapabilityResultOwner.CARD_RUN_STORE,
            completion = CapabilityCompletionKind.RESULT,
            permissionGates = setOf(CapabilityPermissionGate.FILE_ROOT_POLICY),
            fallbackBoundary = CapabilityFallbackBoundary.NEVER_AUTOMATIC,
            notes = "只有 REMOVE 根可作为移动源，不对非原子实现降级。",
        ),
        RoutableCapabilityEntry(
            id = AndroidNativeFileCapabilityProvider.CAPABILITY_DELETE_FILE,
            invocation = CapabilityInvocationKind.NATIVE_RECIPE,
            resultOwner = CapabilityResultOwner.CARD_RUN_STORE,
            completion = CapabilityCompletionKind.RESULT,
            permissionGates = setOf(CapabilityPermissionGate.FILE_ROOT_POLICY),
            fallbackBoundary = CapabilityFallbackBoundary.NEVER_AUTOMATIC,
            notes = "仅删除显式 REMOVE 根内的普通单文件，不递归。",
        ),
        RoutableCapabilityEntry(
            id = AndroidNativeArchiveCapabilityProvider.CAPABILITY_ID,
            invocation = CapabilityInvocationKind.NATIVE_RECIPE,
            resultOwner = CapabilityResultOwner.CARD_RUN_STORE,
            completion = CapabilityCompletionKind.RESULT,
            permissionGates = setOf(CapabilityPermissionGate.FILE_ROOT_POLICY),
            fallbackBoundary = CapabilityFallbackBoundary.NEVER_AUTOMATIC,
            automaticResourceRouting = false,
            notes = "普通 ZIP 安全解包；真机性能 no-go，不进入自动资源快速车道。",
        ),
        RoutableCapabilityEntry(
            id = CAPABILITY_OPEN_APK_INSTALLER,
            invocation = CapabilityInvocationKind.ANDROID_ACTION,
            resultOwner = CapabilityResultOwner.EXTERNAL_ANDROID_INSTALLER,
            completion = CapabilityCompletionKind.EXTERNAL_HANDOFF,
            permissionGates = setOf(CapabilityPermissionGate.FILE_ROOT_POLICY, CapabilityPermissionGate.USER_CONFIRMATION),
            fallbackBoundary = CapabilityFallbackBoundary.NEVER_AUTOMATIC,
            legacyAction = KiteRecipe.ANDROID_ACTION_INSTALL_APK,
            notes = "只证明已把受控 APK 交给系统安装器，不代表用户已安装成功。",
        ),
        RoutableCapabilityEntry(
            id = CAPABILITY_DEFAULT_NETWORK_ALIGNMENT,
            invocation = CapabilityInvocationKind.LIFECYCLE_SERVICE,
            resultOwner = CapabilityResultOwner.DEFAULT_NETWORK_ALIGNMENT,
            completion = CapabilityCompletionKind.CONTINUOUS_ALIGNMENT,
            permissionGates = setOf(CapabilityPermissionGate.ANDROID_INTERNET_NORMAL),
            fallbackBoundary = CapabilityFallbackBoundary.NOT_APPLICABLE,
            notes = "监听 Android 默认网络并校准容器 DNS，不读取 VPN 产品配置。",
        ),
        RoutableCapabilityEntry(
            id = CAPABILITY_RUNTIME_PERMISSION_SNAPSHOT,
            invocation = CapabilityInvocationKind.QUERY_ONLY,
            resultOwner = CapabilityResultOwner.RUNTIME_BOOTSTRAP_GATEWAY,
            completion = CapabilityCompletionKind.SNAPSHOT,
            permissionGates = setOf(CapabilityPermissionGate.RUNTIME_PERMISSION_STATE),
            fallbackBoundary = CapabilityFallbackBoundary.NOT_APPLICABLE,
            notes = "只读取文件访问权限缺口；权限请求仍由 Activity/引导流程发起。",
        ),
    )

    fun entryFor(domain: CapabilityDomain): CapabilityCatalogEntry? {
        return entries.firstOrNull { it.domain == domain }
    }

    fun routableEntryFor(id: String): RoutableCapabilityEntry? =
        routableEntries.firstOrNull { it.id == id }

    fun routableEntryForLegacyAction(action: String): RoutableCapabilityEntry? =
        routableEntries.firstOrNull { it.legacyAction == action }

    const val CAPABILITY_OPEN_APK_INSTALLER = "android.apk.open_installer"
    const val CAPABILITY_DEFAULT_NETWORK_ALIGNMENT = "android.network.default_alignment"
    const val CAPABILITY_RUNTIME_PERMISSION_SNAPSHOT = "android.permission.runtime_snapshot"
}
