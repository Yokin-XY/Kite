package com.kite.app.foundation.devicebridge

import org.json.JSONArray
import org.json.JSONObject

/**
 * Kite Device Bridge 的传输无关合同。
 *
 * Android/Shizuku、Root 和 Ubuntu CLI 都只能依赖这些稳定语义；具体 Binder、Socket、
 * 文件或进程实现不得泄漏到 Agent/UI。
 */
object DeviceBridgeContract {
    const val PROTOCOL_VERSION = 1

    const val EXIT_OK = 0
    const val EXIT_INVALID_REQUEST = 64
    const val EXIT_BACKEND_UNAVAILABLE = 69
    const val EXIT_PERMISSION_DENIED = 77
    const val EXIT_TIMEOUT = 124
    const val EXIT_TRANSPORT_ERROR = 125
    const val EXIT_UNSUPPORTED = 126
    const val EXIT_CANCELLED = 130
}

enum class DeviceBridgeBackend {
    None,
    Shizuku,
    Root
}

enum class DeviceBridgeIdentity(val rank: Int) {
    Unknown(0),
    App(1),
    Shell(2),
    Root(3);

    fun satisfies(requirement: DeviceBridgeIdentityRequirement): Boolean = when (requirement) {
        DeviceBridgeIdentityRequirement.AppOrHigher -> rank >= App.rank
        DeviceBridgeIdentityRequirement.ShellOrRoot -> rank >= Shell.rank
        DeviceBridgeIdentityRequirement.RootOnly -> this == Root
    }
}

enum class DeviceBridgeIdentityRequirement {
    AppOrHigher,
    ShellOrRoot,
    RootOnly
}

enum class DeviceBridgeLifecycleStatus {
    Unavailable,
    InstalledButStopped,
    PermissionRequired,
    Connecting,
    Ready,
    Revoked,
    Failed
}

enum class DeviceBridgeCapabilityRisk {
    ReadOnly,
    Mutating,
    Sensitive,
    Destructive
}

enum class DeviceBridgeCapabilitySupport {
    Available,
    ProbeRequired,
    Unsupported,
    Blocked
}

enum class DeviceBridgeCapabilityFamily {
    Shell,
    File,
    Package,
    Input,
    Screen,
    System
}

data class DeviceBridgeCapabilityDefinition(
    val id: String,
    val family: DeviceBridgeCapabilityFamily,
    val minimumAndroidApi: Int = 23,
    val identity: DeviceBridgeIdentityRequirement = DeviceBridgeIdentityRequirement.ShellOrRoot,
    val risk: DeviceBridgeCapabilityRisk,
    val supportsStreaming: Boolean = false,
    val supportsStdin: Boolean = false
)

data class DeviceBridgeRuntime(
    val backend: DeviceBridgeBackend,
    val identity: DeviceBridgeIdentity,
    val lifecycle: DeviceBridgeLifecycleStatus,
    val androidApi: Int
)

data class DeviceBridgeCapabilitySnapshot(
    val definition: DeviceBridgeCapabilityDefinition,
    val support: DeviceBridgeCapabilitySupport,
    val reason: String
)

object DeviceBridgeCatalog {
    val definitions: List<DeviceBridgeCapabilityDefinition> = listOf(
        capability("shell.exec", DeviceBridgeCapabilityFamily.Shell, DeviceBridgeCapabilityRisk.Sensitive, streaming = true, stdin = true),
        capability("file.read", DeviceBridgeCapabilityFamily.File, DeviceBridgeCapabilityRisk.ReadOnly, streaming = true),
        capability("file.write", DeviceBridgeCapabilityFamily.File, DeviceBridgeCapabilityRisk.Mutating, streaming = true, stdin = true),
        capability("package.list", DeviceBridgeCapabilityFamily.Package, DeviceBridgeCapabilityRisk.ReadOnly),
        capability("package.inspect", DeviceBridgeCapabilityFamily.Package, DeviceBridgeCapabilityRisk.ReadOnly),
        capability("package.install", DeviceBridgeCapabilityFamily.Package, DeviceBridgeCapabilityRisk.Sensitive, streaming = true, stdin = true),
        capability("package.uninstall", DeviceBridgeCapabilityFamily.Package, DeviceBridgeCapabilityRisk.Destructive),
        capability("package.launch", DeviceBridgeCapabilityFamily.Package, DeviceBridgeCapabilityRisk.Mutating),
        capability("package.force_stop", DeviceBridgeCapabilityFamily.Package, DeviceBridgeCapabilityRisk.Sensitive),
        capability("package.clear_data", DeviceBridgeCapabilityFamily.Package, DeviceBridgeCapabilityRisk.Destructive),
        capability("package.permissions.read", DeviceBridgeCapabilityFamily.Package, DeviceBridgeCapabilityRisk.ReadOnly),
        capability("package.permissions.write", DeviceBridgeCapabilityFamily.Package, DeviceBridgeCapabilityRisk.Sensitive),
        capability("package.appops.read", DeviceBridgeCapabilityFamily.Package, DeviceBridgeCapabilityRisk.ReadOnly),
        capability("package.appops.write", DeviceBridgeCapabilityFamily.Package, DeviceBridgeCapabilityRisk.Sensitive),
        capability("input.tap", DeviceBridgeCapabilityFamily.Input, DeviceBridgeCapabilityRisk.Mutating),
        capability("input.swipe", DeviceBridgeCapabilityFamily.Input, DeviceBridgeCapabilityRisk.Mutating),
        capability("input.text", DeviceBridgeCapabilityFamily.Input, DeviceBridgeCapabilityRisk.Mutating, stdin = true),
        capability("input.key", DeviceBridgeCapabilityFamily.Input, DeviceBridgeCapabilityRisk.Mutating),
        capability("screen.capture", DeviceBridgeCapabilityFamily.Screen, DeviceBridgeCapabilityRisk.ReadOnly, streaming = true),
        capability("screen.record", DeviceBridgeCapabilityFamily.Screen, DeviceBridgeCapabilityRisk.Sensitive, streaming = true),
        capability("system.processes", DeviceBridgeCapabilityFamily.System, DeviceBridgeCapabilityRisk.ReadOnly),
        capability("system.logcat", DeviceBridgeCapabilityFamily.System, DeviceBridgeCapabilityRisk.ReadOnly, streaming = true),
        capability("system.dumpsys", DeviceBridgeCapabilityFamily.System, DeviceBridgeCapabilityRisk.ReadOnly, streaming = true),
        capability("system.settings.read", DeviceBridgeCapabilityFamily.System, DeviceBridgeCapabilityRisk.ReadOnly),
        capability("system.settings.write", DeviceBridgeCapabilityFamily.System, DeviceBridgeCapabilityRisk.Sensitive),
        capability("system.device_info", DeviceBridgeCapabilityFamily.System, DeviceBridgeCapabilityRisk.ReadOnly)
    )

    private val byId = definitions.associateBy { it.id }

    /** 当前 Host-self/UserService 通道已实现并可由实时后端探测转为 available 的能力。 */
    val implementedCapabilityIds: Set<String> = linkedSetOf(
        "shell.exec",
        "file.read",
        "file.write",
        "package.list",
        "package.inspect",
        "package.install",
        "package.uninstall",
        "package.launch",
        "package.force_stop",
        "package.clear_data",
        "package.permissions.read",
        "package.permissions.write",
        "package.appops.read",
        "package.appops.write",
        "input.tap",
        "input.swipe",
        "input.text",
        "input.key",
        "screen.capture",
        "screen.record",
        "system.processes",
        "system.logcat",
        "system.dumpsys",
        "system.settings.read",
        "system.settings.write",
        "system.device_info"
    )

    init {
        require(definitions.size == byId.size) { "Device Bridge capability ids must be unique" }
        require(implementedCapabilityIds.all(byId::containsKey)) {
            "Implemented Device Bridge capabilities must exist in the shared catalog"
        }
    }

    fun definition(id: String): DeviceBridgeCapabilityDefinition? = byId[id]

    /**
     * 投影给 Ubuntu CLI 的稳定目录。运行时是否可用由 Android 后端探测另行声明，
     * 这里不把候选能力提前标成 available。
     */
    fun toJson(): String {
        val capabilities = JSONArray()
        definitions.forEach { definition ->
            capabilities.put(
                JSONObject()
                    .put("id", definition.id)
                    .put("family", definition.family.name.lowercase())
                    .put("minimumAndroidApi", definition.minimumAndroidApi)
                    .put("identity", definition.identity.protocolValue())
                    .put("risk", definition.risk.protocolValue())
                    .put("supportsStreaming", definition.supportsStreaming)
                    .put("supportsStdin", definition.supportsStdin)
                    .put("defaultSupport", "probe_required")
            )
        }
        return JSONObject()
            .put("schemaVersion", 1)
            .put("protocolVersion", DeviceBridgeContract.PROTOCOL_VERSION)
            .put("implementedCapabilityIds", JSONArray(implementedCapabilityIds.toList()))
            .put("capabilities", capabilities)
            .toString(2) + "\n"
    }

    /**
     * 这里只判断合同层的硬门槛。OEM/Android 私有 API 的实际可用性仍必须由后端探测；
     * 因此通过硬门槛后返回 [DeviceBridgeCapabilitySupport.ProbeRequired]，不能提前误报可用。
     */
    fun resolve(runtime: DeviceBridgeRuntime): List<DeviceBridgeCapabilitySnapshot> = definitions.map { definition ->
        when {
            runtime.lifecycle != DeviceBridgeLifecycleStatus.Ready -> DeviceBridgeCapabilitySnapshot(
                definition,
                DeviceBridgeCapabilitySupport.Blocked,
                "backend_not_ready:${runtime.lifecycle.name}"
            )

            runtime.androidApi < definition.minimumAndroidApi -> DeviceBridgeCapabilitySnapshot(
                definition,
                DeviceBridgeCapabilitySupport.Unsupported,
                "android_api_too_low:${runtime.androidApi}<${definition.minimumAndroidApi}"
            )

            !runtime.identity.satisfies(definition.identity) -> DeviceBridgeCapabilitySnapshot(
                definition,
                DeviceBridgeCapabilitySupport.Unsupported,
                "identity_insufficient:${runtime.identity.name}"
            )

            else -> DeviceBridgeCapabilitySnapshot(
                definition,
                DeviceBridgeCapabilitySupport.ProbeRequired,
                "backend_probe_required"
            )
        }
    }

    private fun capability(
        id: String,
        family: DeviceBridgeCapabilityFamily,
        risk: DeviceBridgeCapabilityRisk,
        streaming: Boolean = false,
        stdin: Boolean = false
    ): DeviceBridgeCapabilityDefinition = DeviceBridgeCapabilityDefinition(
        id = id,
        family = family,
        risk = risk,
        supportsStreaming = streaming,
        supportsStdin = stdin
    )

    private fun DeviceBridgeIdentityRequirement.protocolValue(): String = when (this) {
        DeviceBridgeIdentityRequirement.AppOrHigher -> "app_or_higher"
        DeviceBridgeIdentityRequirement.ShellOrRoot -> "shell_or_root"
        DeviceBridgeIdentityRequirement.RootOnly -> "root_only"
    }

    private fun DeviceBridgeCapabilityRisk.protocolValue(): String = when (this) {
        DeviceBridgeCapabilityRisk.ReadOnly -> "read_only"
        DeviceBridgeCapabilityRisk.Mutating -> "mutating"
        DeviceBridgeCapabilityRisk.Sensitive -> "sensitive"
        DeviceBridgeCapabilityRisk.Destructive -> "destructive"
    }
}
