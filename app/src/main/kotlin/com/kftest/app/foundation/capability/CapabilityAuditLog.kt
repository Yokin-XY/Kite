package com.kftest.app.foundation.capability

import java.util.ArrayDeque

object CapabilityAuditLog {
    private const val MAX_RECORDS = 300
    private val lock = Any()
    private val records = ArrayDeque<CapabilityAuditRecord>(MAX_RECORDS)

    fun record(
        request: CapabilityRequest,
        decision: CapabilityDecision
    ) {
        synchronized(lock) {
            while (records.size >= MAX_RECORDS) {
                records.removeFirst()
            }
            records.addLast(CapabilityAuditRecord(request, decision))
        }
    }

    fun recent(limit: Int = MAX_RECORDS): List<CapabilityAuditRecord> {
        return synchronized(lock) {
            records.toList().takeLast(limit.coerceAtLeast(0))
        }
    }

    fun toText(limit: Int = MAX_RECORDS): String {
        return recent(limit).joinToString(separator = "\n") { it.toTextLine() }
    }

    fun clear() {
        synchronized(lock) {
            records.clear()
        }
    }
}
