package com.kite.app.resources

enum class KiteResourceManagementMode(val wireValue: String) {
    SYSTEM_COMPONENT("system_component"),
    MANAGED_EXTENSION("managed_extension");

    companion object {
        fun parse(value: String): KiteResourceManagementMode = when (value.trim()) {
            SYSTEM_COMPONENT.wireValue -> SYSTEM_COMPONENT
            MANAGED_EXTENSION.wireValue, "" -> MANAGED_EXTENSION
            else -> SYSTEM_COMPONENT
        }
    }
}

enum class KiteResourceAvailability(val wireValue: String) {
    STABLE("stable"),
    DEBUG_ONLY("debug_only");

    companion object {
        fun parse(value: String): KiteResourceAvailability = when (value.trim()) {
            "", STABLE.wireValue -> STABLE
            DEBUG_ONLY.wireValue -> DEBUG_ONLY
            else -> error("Unsupported resource availability: $value")
        }
    }
}

data class KiteResourceVersionProbeSpec(
    val command: String,
    val pattern: String = "",
    val group: Int = 1,
    val structuredMetadata: KiteResourceMetadataVersionProbeSpec? = null,
)

/** 版本查询的内部结构化合同；显式命令探针不从 shell 文本推导此事实。 */
data class KiteResourceMetadataVersionProbeSpec(
    val containerPath: String,
    val maximumBytes: Long,
    val jsonField: String,
)

data class KiteResourceManagementSpec(
    val mode: KiteResourceManagementMode,
    val managedCommands: List<String> = emptyList(),
    val versionProbe: KiteResourceVersionProbeSpec? = null,
    val latestVersionProbe: KiteResourceVersionProbeSpec? = null,
    val preservePaths: List<String> = emptyList()
) {
    val userLifecycleEnabled: Boolean
        get() = mode == KiteResourceManagementMode.MANAGED_EXTENSION
}

data class KiteResourceSourceSpec(
    val type: String,
    val packageName: String = "",
    val companionPackages: List<String> = emptyList(),
    val repository: String = "",
    val url: String = "",
    val asset: String = "",
    val assetPattern: String = "",
    val channel: String = "stable",
    val tag: String = "",
    val releaseTagTemplate: String = "",
    val archiveType: String = "",
    val binaryPath: String = "",
    val architectures: Map<String, String> = emptyMap(),
    val latestUrl: String = "",
    val latestFormat: String = "json",
    val latestJsonField: String = "",
    val latestRegex: String = "",
    val latestStripPrefix: String = "",
    val registries: List<String> = emptyList(),
    val latestVersionWindow: List<KiteResourceSourceVersion> = emptyList(),
    val installArguments: List<String> = emptyList(),
    val versionArguments: List<String> = emptyList(),
    val environment: Map<String, String> = emptyMap(),
    val profile: String = "",
    val interpreter: String = "",
    val entry: String = "",
    val maxBytes: Long = 0L
)
