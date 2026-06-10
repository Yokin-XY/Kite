package com.kftest.app.foundation.capability

object CapabilityGate {
    fun evaluate(request: CapabilityRequest): CapabilityDecision {
        val normalizedDomains = request.capabilityDomains
            .takeIf { it.isNotEmpty() }
            ?: setOf(CapabilityDomain.UNKNOWN)
        val warnings = buildList {
            if (request.legacyDirectCall) add("legacy_direct_call")
            if (CapabilityDomain.UNKNOWN in normalizedDomains) add("unknown_capability")
        }
        val decision = CapabilityDecision(
            requestId = request.requestId,
            allowed = true,
            mode = CapabilityMode.AUDIT_ONLY,
            normalizedDomains = normalizedDomains,
            warnings = warnings,
            suggestedLane = suggestLane(request, normalizedDomains),
            reason = "audit_only_allow"
        )
        CapabilityAuditLog.record(request, decision)
        return decision
    }

    private fun suggestLane(
        request: CapabilityRequest,
        normalizedDomains: Set<CapabilityDomain>
    ): CapabilityLane {
        return when {
            request.longRunning -> CapabilityLane.LONG_JOB
            CapabilityDomain.RUNTIME in normalizedDomains -> CapabilityLane.RUNTIME_REFRESH
            request.expectedOutputLevel == CapabilityOutputLevel.STREAM -> CapabilityLane.LOG_OUTPUT
            request.expectedOutputLevel == CapabilityOutputLevel.HIGH -> CapabilityLane.LOG_OUTPUT
            CapabilityDomain.OUTPUT in normalizedDomains -> CapabilityLane.LOG_OUTPUT
            CapabilityDomain.TERMINAL in normalizedDomains -> CapabilityLane.TERMINAL
            CapabilityDomain.WORKSPACE in normalizedDomains -> CapabilityLane.FILE_IO
            request.requiresContainer -> CapabilityLane.CONTAINER_ONESHOT
            request.callerType == CapabilityCallerType.UI -> CapabilityLane.MAIN_UI
            else -> CapabilityLane.UNKNOWN
        }
    }
}
