package com.kftest.app.foundation.terminal

import com.kftest.app.foundation.workspace.ManagedTerminalKind
import com.kftest.app.foundation.workspace.ManagedTerminalRecord
import com.kftest.app.foundation.workspace.ManagedTerminalStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

/**
 * 进程级终端注册表。
 *
 * 作用：
 * 1. 让真实终端会话在 UI 之外也有一份正式登记。
 * 2. 为后面的任务管理器和后台托管层提供统一入口。
 * 3. 先从“终端会话”开始，后面再扩成更完整的运行时注册体系。
 */
data class TerminalRuntimeEntry(
    val sessionId: String,
    val spaceId: String,
    val title: String,
    val kind: ManagedTerminalKind,
    val status: ManagedTerminalStatus,
    val createdAt: Long,
    val lastAttachedAt: Long? = null,
    val lastStartedAt: Long? = null,
    val lastExitedAt: Long? = null,
    val lastPid: Int? = null,
    val lastExitCode: Int? = null,
    val sourceAgentRuntimeId: String? = null,
    val startupCommand: String? = null,
    val sourceLabel: String? = null,
    val transcriptPath: String,
    val isActive: Boolean = false,
    val hasAttachedSession: Boolean = false,
    val pendingInputCount: Int = 0
)

object TerminalRuntimeRegistry {

    private val _entries = MutableStateFlow<List<TerminalRuntimeEntry>>(emptyList())
    val entries: StateFlow<List<TerminalRuntimeEntry>> = _entries

    @Synchronized
    fun replaceAll(
        records: List<ManagedTerminalRecord>,
        transcriptDir: File,
        currentViewedSessionId: String?
    ) {
        _entries.value = records.map { record ->
            TerminalRuntimeEntry(
                sessionId = record.id,
                spaceId = record.spaceId,
                title = record.title,
                kind = record.kind,
                status = record.status,
                createdAt = record.createdAt,
                lastAttachedAt = record.lastAttachedAt,
                lastStartedAt = record.lastStartedAt,
                lastExitedAt = record.lastExitedAt,
                lastPid = record.lastPid,
                lastExitCode = record.lastExitCode,
                sourceAgentRuntimeId = record.sourceAgentRuntimeId,
                startupCommand = record.startupCommand,
                sourceLabel = record.sourceLabel,
                transcriptPath = File(transcriptDir, "${record.id}.txt").absolutePath,
                isActive = currentViewedSessionId != null && record.id == currentViewedSessionId
            )
        }.sortedWith(
            compareByDescending<TerminalRuntimeEntry> { it.isActive }.thenBy { it.createdAt }
        )
    }

    @Synchronized
    fun upsert(
        record: ManagedTerminalRecord,
        transcriptFile: File,
        isActive: Boolean,
        hasAttachedSession: Boolean = false,
        pendingInputCount: Int = 0
    ) {
        val current = _entries.value.associateBy { it.sessionId }.toMutableMap()
        current[record.id] = TerminalRuntimeEntry(
            sessionId = record.id,
            spaceId = record.spaceId,
            title = record.title,
            kind = record.kind,
            status = record.status,
            createdAt = record.createdAt,
            lastAttachedAt = record.lastAttachedAt,
            lastStartedAt = record.lastStartedAt,
            lastExitedAt = record.lastExitedAt,
            lastPid = record.lastPid,
            lastExitCode = record.lastExitCode,
            sourceAgentRuntimeId = record.sourceAgentRuntimeId,
            startupCommand = record.startupCommand,
            sourceLabel = record.sourceLabel,
            transcriptPath = transcriptFile.absolutePath,
            isActive = isActive,
            hasAttachedSession = hasAttachedSession,
            pendingInputCount = pendingInputCount
        )
        _entries.value = current.values
            .sortedWith(compareByDescending<TerminalRuntimeEntry> { it.isActive }.thenBy { it.createdAt })
    }

    @Synchronized
    fun markActive(sessionId: String?) {
        _entries.value = _entries.value.map { entry ->
            entry.copy(isActive = sessionId != null && entry.sessionId == sessionId)
        }
    }

    @Synchronized
    fun remove(sessionId: String) {
        _entries.value = _entries.value.filterNot { it.sessionId == sessionId }
    }

    fun snapshot(): List<TerminalRuntimeEntry> = _entries.value
}
