package com.kftest.app.foundation.runtime

enum class RuntimeLifecycleUserNoticeSeverity {
    INFO,
    WARNING,
    ACTION_REQUIRED,
    CRITICAL_DRY_RUN
}

enum class RuntimeLifecycleUserNoticeSource {
    ACTION_INBOX,
    RESOURCE_WATCH,
    RESOURCE_EVENT_LEDGER,
    DIAGNOSTIC_REVIEW,
    OBSERVATION_VALIDATION
}

enum class RuntimeLifecycleUserNoticeStatus {
    OPEN,
    RESOLVED,
    ACKNOWLEDGED,
    SUPPRESSED,
    EXPIRED
}

data class RuntimeLifecycleUserNoticeItem(
    val noticeId: String,
    val unitId: String,
    val title: String,
    val message: String,
    val severity: RuntimeLifecycleUserNoticeSeverity,
    val source: RuntimeLifecycleUserNoticeSource,
    val relatedFinalAction: RuntimeLifecycleFinalAction? = null,
    val requiresUserConfirmation: Boolean = false,
    val isExecutableNow: Boolean = false,
    val recommendedUserResponse: String,
    val technicalReason: String,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val status: RuntimeLifecycleUserNoticeStatus = RuntimeLifecycleUserNoticeStatus.OPEN
)

data class RuntimeLifecycleUserNoticeSnapshot(
    val mode: String = "runtime_lifecycle_warning_notice_v0",
    val enabled: Boolean = true,
    val enforcementEnabled: Boolean = false,
    val noticeCount: Int = 0,
    val infoNoticeCount: Int = 0,
    val warningNoticeCount: Int = 0,
    val actionRequiredCount: Int = 0,
    val criticalDryRunCount: Int = 0,
    val requiresUserConfirmationCount: Int = 0,
    val blockedByObservationGapCount: Int = 0,
    val executableNowCount: Int = 0,
    val items: List<RuntimeLifecycleUserNoticeItem> = emptyList(),
    val invariantChecks: List<String> = RuntimeLifecycleUserNotice.DEFAULT_INVARIANT_CHECKS,
    val boundary: String =
        "warning_notice_ui_ready_only_no_restart_reclaim_quarantine_kill_or_proot_rebuild"
) {
    fun toEnvText(maxItems: Int = 8): String {
        return buildString {
            appendLine("runtime_lifecycle_warning_notice_mode=${mode.toUserNoticeEnvValue()}")
            appendLine("runtime_lifecycle_warning_notice_enabled=$enabled")
            appendLine("runtime_lifecycle_warning_notice_enforcement_enabled=$enforcementEnabled")
            appendLine("runtime_lifecycle_warning_notice_count=$noticeCount")
            appendLine("runtime_lifecycle_warning_notice_info_count=$infoNoticeCount")
            appendLine("runtime_lifecycle_warning_notice_warning_count=$warningNoticeCount")
            appendLine("runtime_lifecycle_warning_notice_action_required_count=$actionRequiredCount")
            appendLine("runtime_lifecycle_warning_notice_critical_dry_run_count=$criticalDryRunCount")
            appendLine("runtime_lifecycle_warning_notice_requires_user_confirmation_count=$requiresUserConfirmationCount")
            appendLine("runtime_lifecycle_warning_notice_blocked_by_observation_gap_count=$blockedByObservationGapCount")
            appendLine("runtime_lifecycle_warning_notice_executable_now_count=$executableNowCount")
            items.take(maxItems).forEachIndexed { index, item ->
                val prefix = "runtime_lifecycle_warning_notice_${index + 1}"
                appendLine("${prefix}_notice_id=${item.noticeId.toUserNoticeEnvValue()}")
                appendLine("${prefix}_unit_id=${item.unitId.toUserNoticeEnvValue()}")
                appendLine("${prefix}_severity=${item.severity.name}")
                appendLine("${prefix}_source=${item.source.name}")
                appendLine("${prefix}_status=${item.status.name}")
                appendLine("${prefix}_title=${item.title.toUserNoticeEnvValue()}")
                appendLine("${prefix}_related_final_action=${item.relatedFinalAction?.name.toUserNoticeEnvValue()}")
                appendLine("${prefix}_requires_user_confirmation=${item.requiresUserConfirmation}")
                appendLine("${prefix}_is_executable_now=${item.isExecutableNow}")
                appendLine("${prefix}_recommended_user_response=${item.recommendedUserResponse.toUserNoticeEnvValue()}")
                appendLine("${prefix}_technical_reason=${item.technicalReason.toUserNoticeEnvValue()}")
                appendLine("${prefix}_created_at=${item.createdAt}")
                appendLine("${prefix}_updated_at=${item.updatedAt}")
            }
            appendLine("runtime_lifecycle_warning_notice_invariant_checks=${invariantChecks.joinToString(";").toUserNoticeEnvValue()}")
            appendLine("runtime_lifecycle_warning_notice_boundary=${boundary.toUserNoticeEnvValue()}")
        }
    }
}

object RuntimeLifecycleUserNotice {
    val DEFAULT_INVARIANT_CHECKS = listOf(
        "notice_layer_not_executor",
        "host_process_terminator_not_called_by_notice",
        "proot_capacity_executor_not_called_by_notice",
        "proot_capacity_actuator_not_called_by_notice",
        "no_restore_restart_quarantine_reclaim_buttons",
        "unmanaged_ubuntu_processes_remain_observe_only"
    )

    fun evaluate(
        actionInbox: RuntimeLifecycleActionInboxSnapshot =
            RuntimeLifecycleActionInboxSnapshot(),
        actionPlanner: RuntimeLifecycleActionPlannerSnapshot =
            RuntimeLifecycleActionPlannerSnapshot(),
        resourceWatch: RuntimeProcessResourceWatchSnapshot =
            RuntimeProcessResourceWatchSnapshot(),
        resourceEventLedger: RuntimeResourceEventLedgerSnapshot =
            RuntimeResourceEventLedgerSnapshot(),
        diagnosticReview: RuntimeLifecycleDiagnosticReviewSnapshot =
            RuntimeLifecycleDiagnosticReviewSnapshot(),
        @Suppress("UNUSED_PARAMETER")
        observationValidation: RuntimeProcessObservationValidationReport =
            RuntimeProcessObservationValidationReport(),
        now: Long = System.currentTimeMillis()
    ): RuntimeLifecycleUserNoticeSnapshot {
        val notices = linkedMapOf<String, RuntimeLifecycleUserNoticeItem>()
        actionInbox.items
            .mapNotNull { noticeFromInbox(it) }
            .forEach { notices.putIfAbsent(it.noticeId, it) }
        actionPlanner.entries
            .mapNotNull { noticeFromPlanner(it, now) }
            .forEach { notices.putIfAbsent(it.noticeId, it) }
        resourceWatch.entries
            .mapNotNull { noticeFromResourceWatch(it, now) }
            .forEach { notices.putIfAbsent(it.noticeId, it) }
        resourceEventLedger.entries
            .mapNotNull { noticeFromResourceLedger(it, now) }
            .forEach { notices.putIfAbsent(it.noticeId, it) }
        diagnosticReview.entries
            .mapNotNull { noticeFromDiagnosticReview(it, now) }
            .forEach { notices.putIfAbsent(it.noticeId, it) }
        val sorted = notices.values
            .filterNot { it.status == RuntimeLifecycleUserNoticeStatus.SUPPRESSED }
            .sortedWith(
                compareByDescending<RuntimeLifecycleUserNoticeItem> { it.severity.rank() }
                    .thenByDescending { it.updatedAt }
                    .thenBy { it.noticeId }
            )
        return RuntimeLifecycleUserNoticeSnapshot(
            noticeCount = sorted.size,
            infoNoticeCount = sorted.count { it.severity == RuntimeLifecycleUserNoticeSeverity.INFO },
            warningNoticeCount = sorted.count {
                it.severity == RuntimeLifecycleUserNoticeSeverity.WARNING
            },
            actionRequiredCount = sorted.count {
                it.severity == RuntimeLifecycleUserNoticeSeverity.ACTION_REQUIRED
            },
            criticalDryRunCount = sorted.count {
                it.severity == RuntimeLifecycleUserNoticeSeverity.CRITICAL_DRY_RUN
            },
            requiresUserConfirmationCount = sorted.count { it.requiresUserConfirmation },
            blockedByObservationGapCount = sorted.count {
                it.source == RuntimeLifecycleUserNoticeSource.OBSERVATION_VALIDATION &&
                    it.severity == RuntimeLifecycleUserNoticeSeverity.CRITICAL_DRY_RUN
            },
            executableNowCount = sorted.count { it.isExecutableNow },
            items = sorted
        )
    }

    private fun noticeFromInbox(
        item: RuntimeLifecycleActionInboxItem
    ): RuntimeLifecycleUserNoticeItem? {
        val severity = severityFor(item.finalAction) ?: return null
        val content = contentFor(item.finalAction)
        return RuntimeLifecycleUserNoticeItem(
            noticeId = "notice-${item.actionId}",
            unitId = item.unitId,
            title = content.title,
            message = content.message,
            severity = severity,
            source = RuntimeLifecycleUserNoticeSource.ACTION_INBOX,
            relatedFinalAction = item.finalAction,
            requiresUserConfirmation = item.requiresUserConfirmation,
            isExecutableNow = false,
            recommendedUserResponse = content.recommendedUserResponse,
            technicalReason = item.primaryReason,
            createdAt = item.createdAt,
            updatedAt = item.updatedAt,
            status = item.status.toUserNoticeStatus()
        )
    }

    private fun noticeFromPlanner(
        entry: RuntimeLifecycleActionPlanEntry,
        now: Long
    ): RuntimeLifecycleUserNoticeItem? {
        val severity = severityFor(entry.finalAction) ?: return null
        val content = contentFor(entry.finalAction)
        return RuntimeLifecycleUserNoticeItem(
            noticeId = noticeId("planner", entry.unitId, entry.finalAction.name),
            unitId = entry.unitId,
            title = content.title,
            message = content.message,
            severity = severity,
            source = RuntimeLifecycleUserNoticeSource.ACTION_INBOX,
            relatedFinalAction = entry.finalAction,
            requiresUserConfirmation = entry.requiresUserConfirmation,
            isExecutableNow = false,
            recommendedUserResponse = content.recommendedUserResponse,
            technicalReason = entry.primaryReason,
            createdAt = now,
            updatedAt = now
        )
    }

    private fun noticeFromResourceWatch(
        entry: RuntimeProcessResourceWatchEntry,
        now: Long
    ): RuntimeLifecycleUserNoticeItem? {
        val action = when (entry.recommendedResourceAction) {
            RuntimeProcessResourceRecommendedAction.WARN_ONLY ->
                RuntimeLifecycleFinalAction.WARN_RESOURCE_NEAR_LIMIT
            RuntimeProcessResourceRecommendedAction.RESTART_CANDIDATE_DRY_RUN ->
                RuntimeLifecycleFinalAction.RESTART_CANDIDATE_DRY_RUN
            RuntimeProcessResourceRecommendedAction.QUARANTINE_CANDIDATE_DRY_RUN ->
                RuntimeLifecycleFinalAction.QUARANTINE_CANDIDATE_DRY_RUN
            RuntimeProcessResourceRecommendedAction.RECLAIM_CANDIDATE_DRY_RUN ->
                RuntimeLifecycleFinalAction.RECLAIM_CANDIDATE_DRY_RUN
            RuntimeProcessResourceRecommendedAction.OBSERVE,
            RuntimeProcessResourceRecommendedAction.NO_ACTION_UNLIMITED,
            RuntimeProcessResourceRecommendedAction.NO_ACTION_CORE_PROTECTED,
            RuntimeProcessResourceRecommendedAction.NO_ACTION_AMBIGUOUS -> return null
        }
        val content = contentFor(action)
        return RuntimeLifecycleUserNoticeItem(
            noticeId = noticeId("resource-watch", entry.unitId, action.name),
            unitId = entry.unitId,
            title = content.title,
            message = content.message,
            severity = severityFor(action) ?: return null,
            source = RuntimeLifecycleUserNoticeSource.RESOURCE_WATCH,
            relatedFinalAction = action,
            isExecutableNow = false,
            recommendedUserResponse = content.recommendedUserResponse,
            technicalReason = entry.resourceSuppressionReason,
            createdAt = now,
            updatedAt = now
        )
    }

    private fun noticeFromResourceLedger(
        entry: RuntimeResourceEventLedgerEntry,
        now: Long
    ): RuntimeLifecycleUserNoticeItem? {
        val action = when (entry.episodeState) {
            RuntimeResourceEpisodeState.NEAR_LIMIT_EPISODE,
            RuntimeResourceEpisodeState.OVER_LIMIT_EPISODE ->
                RuntimeLifecycleFinalAction.WARN_RESOURCE_NEAR_LIMIT
            RuntimeResourceEpisodeState.RESTART_CANDIDATE_DRY_RUN ->
                RuntimeLifecycleFinalAction.RESTART_CANDIDATE_DRY_RUN
            RuntimeResourceEpisodeState.QUARANTINE_CANDIDATE_DRY_RUN ->
                RuntimeLifecycleFinalAction.QUARANTINE_CANDIDATE_DRY_RUN
            RuntimeResourceEpisodeState.NONE,
            RuntimeResourceEpisodeState.OBSERVING,
            RuntimeResourceEpisodeState.RECOVERED,
            RuntimeResourceEpisodeState.SUPPRESSED_UNLIMITED,
            RuntimeResourceEpisodeState.SUPPRESSED_AMBIGUOUS,
            RuntimeResourceEpisodeState.SUPPRESSED_CORE_PROTECTED,
            RuntimeResourceEpisodeState.SUPPRESSED_UNMANAGED -> return null
        }
        val content = contentFor(action)
        return RuntimeLifecycleUserNoticeItem(
            noticeId = noticeId("resource-ledger", entry.unitId, action.name),
            unitId = entry.unitId,
            title = content.title,
            message = content.message,
            severity = severityFor(action) ?: return null,
            source = RuntimeLifecycleUserNoticeSource.RESOURCE_EVENT_LEDGER,
            relatedFinalAction = action,
            isExecutableNow = false,
            recommendedUserResponse = content.recommendedUserResponse,
            technicalReason = entry.suppressionReason,
            createdAt = entry.firstSeenAt.takeIf { it > 0L } ?: now,
            updatedAt = entry.lastSeenAt.takeIf { it > 0L } ?: now
        )
    }

    private fun noticeFromDiagnosticReview(
        entry: RuntimeLifecycleDiagnosticReviewEntry,
        now: Long
    ): RuntimeLifecycleUserNoticeItem? {
        if (entry.blockingIssues.isEmpty() &&
            entry.warnings.isEmpty() &&
            entry.reviewStatus != RuntimeLifecycleDiagnosticReviewStatus.FAILED_INVARIANT
        ) {
            return null
        }
        val severity = if (
            entry.blockingIssues.isNotEmpty() ||
            entry.reviewStatus == RuntimeLifecycleDiagnosticReviewStatus.FAILED_INVARIANT
        ) {
            RuntimeLifecycleUserNoticeSeverity.CRITICAL_DRY_RUN
        } else {
            RuntimeLifecycleUserNoticeSeverity.WARNING
        }
        return RuntimeLifecycleUserNoticeItem(
            noticeId = noticeId("diagnostic", entry.unitId, entry.scenarioName),
            unitId = entry.unitId,
            title = if (severity == RuntimeLifecycleUserNoticeSeverity.CRITICAL_DRY_RUN) {
                "生命周期诊断发现阻塞项"
            } else {
                "生命周期诊断存在提示"
            },
            message = if (severity == RuntimeLifecycleUserNoticeSeverity.CRITICAL_DRY_RUN) {
                "诊断发现潜在不变量或保护边界问题，当前只展示，不执行任何动作。"
            } else {
                "诊断发现可关注的 warning，当前保持 dry-run/observe-only。"
            },
            severity = severity,
            source = RuntimeLifecycleUserNoticeSource.DIAGNOSTIC_REVIEW,
            relatedFinalAction = entry.finalAction,
            isExecutableNow = false,
            recommendedUserResponse = "查看技术原因和 manifest/runtime 配置，确认后再决定后续策略。",
            technicalReason = (entry.blockingIssues + entry.warnings)
                .distinct()
                .joinToString(";")
                .ifBlank { entry.reviewStatus.name },
            createdAt = now,
            updatedAt = now
        )
    }

    private fun severityFor(
        action: RuntimeLifecycleFinalAction
    ): RuntimeLifecycleUserNoticeSeverity? {
        return when (action) {
            RuntimeLifecycleFinalAction.WAIT_FOR_USER_CONFIRMATION ->
                RuntimeLifecycleUserNoticeSeverity.ACTION_REQUIRED
            RuntimeLifecycleFinalAction.WARN_RESOURCE_NEAR_LIMIT ->
                RuntimeLifecycleUserNoticeSeverity.WARNING
            RuntimeLifecycleFinalAction.RESTART_CANDIDATE_DRY_RUN ->
                RuntimeLifecycleUserNoticeSeverity.ACTION_REQUIRED
            RuntimeLifecycleFinalAction.QUARANTINE_CANDIDATE_DRY_RUN,
            RuntimeLifecycleFinalAction.CORE_RECOVERY_DRY_RUN ->
                RuntimeLifecycleUserNoticeSeverity.CRITICAL_DRY_RUN
            RuntimeLifecycleFinalAction.RECLAIM_CANDIDATE_DRY_RUN ->
                RuntimeLifecycleUserNoticeSeverity.WARNING
            RuntimeLifecycleFinalAction.OBSERVE,
            RuntimeLifecycleFinalAction.KEEP_RUNNING,
            RuntimeLifecycleFinalAction.EXPECTED_STOP_CONFIRMED,
            RuntimeLifecycleFinalAction.NO_ACTION_UNMANAGED,
            RuntimeLifecycleFinalAction.NO_ACTION_UNLIMITED,
            RuntimeLifecycleFinalAction.NO_ACTION_AMBIGUOUS,
            RuntimeLifecycleFinalAction.NO_ACTION_CORE_PROTECTED -> null
        }
    }

    private fun contentFor(action: RuntimeLifecycleFinalAction): NoticeContent {
        return when (action) {
            RuntimeLifecycleFinalAction.WAIT_FOR_USER_CONFIRMATION -> NoticeContent(
                title = "用户锁定进程等待确认",
                message = "某个用户锁定进程已停止，需要确认是否恢复。",
                recommendedUserResponse = "检查该进程是否应恢复；当前不会自动恢复或重启。"
            )
            RuntimeLifecycleFinalAction.WARN_RESOURCE_NEAR_LIMIT -> NoticeContent(
                title = "进程接近声明内存上限",
                message = "某个进程接近声明内存上限。",
                recommendedUserResponse = "检查进程行为、配置或提高 expectedMemoryLimit。"
            )
            RuntimeLifecycleFinalAction.RESTART_CANDIDATE_DRY_RUN -> NoticeContent(
                title = "进程达到未来重启候选条件",
                message = "该进程已达到未来重启候选条件，但当前仅诊断。",
                recommendedUserResponse = "查看原因并等待显式确认；当前不会执行 restart。"
            )
            RuntimeLifecycleFinalAction.QUARANTINE_CANDIDATE_DRY_RUN -> NoticeContent(
                title = "进程达到未来隔离候选条件",
                message = "该进程达到未来隔离候选条件，但当前没有执行隔离。",
                recommendedUserResponse = "检查连续超限或异常原因；当前不会执行 quarantine。"
            )
            RuntimeLifecycleFinalAction.RECLAIM_CANDIDATE_DRY_RUN -> NoticeContent(
                title = "临时进程可能成为回收候选",
                message = "该临时或 lease 进程在内存压力下可能成为回收候选。",
                recommendedUserResponse = "检查 lease 任务是否仍需要运行；当前不扩大真实 reclaim。"
            )
            RuntimeLifecycleFinalAction.CORE_RECOVERY_DRY_RUN -> NoticeContent(
                title = "核心组件出现恢复候选",
                message = "核心组件出现恢复候选，但当前只是诊断。",
                recommendedUserResponse = "检查核心组件状态；当前不会执行 PRoot #1 rebuild 或 core recovery。"
            )
            else -> NoticeContent(
                title = "生命周期提示",
                message = "当前仅展示生命周期诊断。",
                recommendedUserResponse = "保持观察。"
            )
        }
    }

    private fun noticeId(vararg parts: String): String {
        val raw = parts.joinToString("|")
        return "notice-${Integer.toHexString(raw.hashCode())}"
    }

    private fun RuntimeLifecycleUserNoticeSeverity.rank(): Int {
        return when (this) {
            RuntimeLifecycleUserNoticeSeverity.CRITICAL_DRY_RUN -> 4
            RuntimeLifecycleUserNoticeSeverity.ACTION_REQUIRED -> 3
            RuntimeLifecycleUserNoticeSeverity.WARNING -> 2
            RuntimeLifecycleUserNoticeSeverity.INFO -> 1
        }
    }

    private fun RuntimeLifecycleActionInboxStatus.toUserNoticeStatus(): RuntimeLifecycleUserNoticeStatus {
        return when (this) {
            RuntimeLifecycleActionInboxStatus.OPEN,
            RuntimeLifecycleActionInboxStatus.BLOCKED -> RuntimeLifecycleUserNoticeStatus.OPEN
            RuntimeLifecycleActionInboxStatus.ACKNOWLEDGED -> RuntimeLifecycleUserNoticeStatus.ACKNOWLEDGED
            RuntimeLifecycleActionInboxStatus.DISMISSED -> RuntimeLifecycleUserNoticeStatus.SUPPRESSED
            RuntimeLifecycleActionInboxStatus.EXPIRED -> RuntimeLifecycleUserNoticeStatus.EXPIRED
            RuntimeLifecycleActionInboxStatus.RESOLVED_BY_STATE_CHANGE ->
                RuntimeLifecycleUserNoticeStatus.RESOLVED
        }
    }

    private data class NoticeContent(
        val title: String,
        val message: String,
        val recommendedUserResponse: String
    )
}

private fun String?.toUserNoticeEnvValue(): String {
    if (this.isNullOrBlank()) return "none"
    return trim()
        .replace(Regex("[\\r\\n\\t ]+"), "_")
        .replace(Regex("[^A-Za-z0-9._:@/+,-]"), "_")
        .take(260)
}
