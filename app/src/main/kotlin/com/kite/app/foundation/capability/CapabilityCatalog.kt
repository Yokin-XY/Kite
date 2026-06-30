package com.kite.app.foundation.capability

data class CapabilityCatalogEntry(
    val domain: CapabilityDomain,
    val examples: List<String>,
    val description: String
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

    fun entryFor(domain: CapabilityDomain): CapabilityCatalogEntry? {
        return entries.firstOrNull { it.domain == domain }
    }
}
