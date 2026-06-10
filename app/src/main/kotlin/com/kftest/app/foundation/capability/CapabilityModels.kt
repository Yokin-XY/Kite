package com.kftest.app.foundation.capability

import java.util.concurrent.atomic.AtomicLong

enum class CapabilityDomain {
    ANDROID,
    WORKSPACE,
    PROOT,
    UBUNTU,
    TERMINAL,
    SERVICE,
    OUTPUT,
    RUNTIME,
    UNKNOWN
}

enum class CapabilityCallerType {
    UI,
    SUB_APP,
    SERVICE,
    AUTOMATION,
    LEGACY
}

enum class CapabilityOutputLevel {
    NONE,
    LOW,
    MEDIUM,
    HIGH,
    STREAM
}

enum class CapabilityMode {
    AUDIT_ONLY
}

enum class CapabilityLane {
    MAIN_UI,
    TERMINAL,
    CONTAINER_ONESHOT,
    LONG_JOB,
    FILE_IO,
    RUNTIME_REFRESH,
    LOG_OUTPUT,
    UNKNOWN
}

data class CapabilityRequest(
    val requestId: String = CapabilityRequestIds.next(),
    val callerName: String,
    val callerType: CapabilityCallerType,
    val actionName: String,
    val capabilityDomains: Set<CapabilityDomain> = emptySet(),
    val requiresContainer: Boolean = false,
    val longRunning: Boolean = false,
    val expectedOutputLevel: CapabilityOutputLevel = CapabilityOutputLevel.NONE,
    val concurrencyKey: String? = null,
    val sourcePath: String? = null,
    val sourceModule: String? = null,
    val legacyDirectCall: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

data class CapabilityDecision(
    val requestId: String,
    val allowed: Boolean,
    val mode: CapabilityMode,
    val normalizedDomains: Set<CapabilityDomain>,
    val warnings: List<String>,
    val suggestedLane: CapabilityLane,
    val reason: String
)

data class CapabilityAuditRecord(
    val request: CapabilityRequest,
    val decision: CapabilityDecision,
    val recordedAt: Long = System.currentTimeMillis()
) {
    fun toTextLine(): String {
        return buildString {
            append("capability requestId=${request.requestId} ")
            append("caller=${request.callerName}/${request.callerType.name} ")
            append("action=${request.actionName} ")
            append("domains=${decision.normalizedDomains.joinToString("|") { it.name }} ")
            append("lane=${decision.suggestedLane.name} ")
            append("allowed=${decision.allowed} ")
            append("mode=${decision.mode.name} ")
            append("legacyDirectCall=${request.legacyDirectCall}")
            if (decision.warnings.isNotEmpty()) {
                append(" warnings=${decision.warnings.joinToString("|")}")
            }
        }
    }
}

private object CapabilityRequestIds {
    private val nextId = AtomicLong(1L)

    fun next(): String = "cap-${System.currentTimeMillis()}-${nextId.getAndIncrement()}"
}
