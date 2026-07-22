package com.kite.app.feature.runtimemanagement

import android.content.Context
import com.kite.app.R
import com.kite.app.application.runtimemanagement.RuntimeManagedOwnerKind
import com.kite.app.run.CardRunState
import com.kite.app.run.CardRunStatus
import com.kite.app.run.CardRunSurface

/** 运行管理的显示文案解析器。事实快照继续只携带状态，当前语言只在展示投影时注入。 */
internal class RuntimeManagementText private constructor(
    private val values: Values,
) {
    data class Values(
        val cardFallback: String,
        val sections: Map<RuntimeManagedOwnerKind, String>,
        val owners: Map<RuntimeManagedOwnerKind, String>,
        val sources: Map<String, String>,
        val statuses: Map<CardRunStatus, String>,
        val surfaces: Map<CardRunSurface, String>,
        val processStates: Map<String, String>,
        val processTitles: Map<String, String>,
        val processPurposes: Map<String, String>,
        val requested: String,
        val awaiting: String,
        val failed: String,
        val terminalFallback: String,
        val terminalProcessCount: (Int) -> String,
        val processFallback: String,
        val processGenericPurpose: String,
        val runtimeFoundation: String,
        val ending: String,
        val endTerminal: String,
        val stopTask: String,
        val endProcess: String,
        val endProcessGroup: String,
        val stopping: String,
        val stop: String,
        val open: String,
        val reportCaption: String,
        val terminalCaption: String,
        val waitingUrl: String,
    )

    val cardFallback: String get() = values.cardFallback
    val requested: String get() = values.requested
    val awaiting: String get() = values.awaiting
    val failed: String get() = values.failed
    val terminalFallback: String get() = values.terminalFallback
    val processFallback: String get() = values.processFallback
    val processGenericPurpose: String get() = values.processGenericPurpose
    val runtimeFoundation: String get() = values.runtimeFoundation
    val ending: String get() = values.ending
    val endTerminal: String get() = values.endTerminal
    val stopTask: String get() = values.stopTask
    val endProcess: String get() = values.endProcess
    val endProcessGroup: String get() = values.endProcessGroup
    val stopping: String get() = values.stopping
    val stop: String get() = values.stop
    val open: String get() = values.open
    val reportCaption: String get() = values.reportCaption
    val terminalCaption: String get() = values.terminalCaption
    val waitingUrl: String get() = values.waitingUrl

    fun section(kind: RuntimeManagedOwnerKind): String = values.sections.getValue(kind)
    fun owner(kind: RuntimeManagedOwnerKind): String = values.owners.getValue(kind)
    fun source(ownerKind: String): String = values.sources[ownerKind] ?: values.sources.getValue(CardRunState.OWNER_KIND_CARD)
    fun status(status: CardRunStatus): String = values.statuses.getValue(status)
    fun surface(surface: CardRunSurface): String = values.surfaces.getValue(surface)
    fun processState(raw: String): String = values.processStates[raw] ?: raw
    fun processTitle(raw: String): String = values.processTitles[raw] ?: raw
    fun processPurpose(raw: String): String = values.processPurposes[raw] ?: raw
    fun terminalProcessCount(count: Int): String = values.terminalProcessCount(count)

    companion object {
        fun from(context: Context): RuntimeManagementText = RuntimeManagementText(
            values(
                get = context::getString,
                terminalProcessCount = { context.getString(R.string.runtime_management_terminal_process_count, it) },
            )
        )

        fun zhCn(): RuntimeManagementText = RuntimeManagementText(
            values(
                get = { id -> ZH_CN.getValue(id) },
                terminalProcessCount = { "进程 $it" },
            )
        )

        private fun values(
            get: (Int) -> String,
            terminalProcessCount: (Int) -> String,
        ): Values = Values(
            cardFallback = get(R.string.runtime_management_card_fallback),
            sections = mapOf(
                RuntimeManagedOwnerKind.BackgroundRuntime to get(R.string.runtime_management_section_background),
                RuntimeManagedOwnerKind.Resource to get(R.string.runtime_management_section_resource),
                RuntimeManagedOwnerKind.Terminal to get(R.string.runtime_management_section_terminal),
                RuntimeManagedOwnerKind.System to get(R.string.runtime_management_section_system),
                RuntimeManagedOwnerKind.Unattributed to get(R.string.runtime_management_section_other),
                RuntimeManagedOwnerKind.Card to get(R.string.runtime_management_owner_card),
            ),
            owners = mapOf(
                RuntimeManagedOwnerKind.Card to get(R.string.runtime_management_owner_card),
                RuntimeManagedOwnerKind.Resource to get(R.string.runtime_management_owner_resource),
                RuntimeManagedOwnerKind.Terminal to get(R.string.runtime_management_owner_terminal),
                RuntimeManagedOwnerKind.BackgroundRuntime to get(R.string.runtime_management_owner_system),
                RuntimeManagedOwnerKind.System to get(R.string.runtime_management_owner_system),
                RuntimeManagedOwnerKind.Unattributed to get(R.string.runtime_management_owner_unattributed),
            ),
            sources = mapOf(
                CardRunState.OWNER_KIND_RESOURCE to get(R.string.runtime_management_owner_resource),
                CardRunState.OWNER_KIND_INSTALL_WIZARD to get(R.string.runtime_management_source_install),
                CardRunState.OWNER_KIND_TERMINAL to get(R.string.runtime_management_source_terminal),
                CardRunState.OWNER_KIND_WEB to get(R.string.runtime_management_source_web),
                CardRunState.OWNER_KIND_CARD to get(R.string.runtime_management_source_home),
            ),
            statuses = mapOf(
                CardRunStatus.Unknown to get(R.string.runtime_management_status_unknown),
                CardRunStatus.Stopped to get(R.string.runtime_management_status_stopped),
                CardRunStatus.Starting to get(R.string.runtime_management_status_starting),
                CardRunStatus.Running to get(R.string.runtime_management_status_running),
                CardRunStatus.WaitingTerminal to get(R.string.runtime_management_status_waiting_terminal),
                CardRunStatus.AlreadyRunning to get(R.string.runtime_management_status_already_running),
                CardRunStatus.Opened to get(R.string.runtime_management_status_opened),
                CardRunStatus.Completed to get(R.string.runtime_management_status_completed),
                CardRunStatus.Failed to get(R.string.runtime_management_status_failed),
                CardRunStatus.Stopping to get(R.string.runtime_management_status_stopping),
                CardRunStatus.CleanupPending to get(R.string.runtime_management_status_cleanup_pending),
                CardRunStatus.BridgeUnavailable to get(R.string.runtime_management_status_bridge_unavailable),
            ),
            surfaces = mapOf(
                CardRunSurface.Summary to get(R.string.runtime_metric_cards),
                CardRunSurface.Report to get(R.string.runtime_management_surface_report),
                CardRunSurface.Terminal to get(R.string.runtime_management_surface_terminal),
                CardRunSurface.Web to get(R.string.runtime_management_surface_web),
                CardRunSurface.X11 to "X11",
                CardRunSurface.InstallWizard to get(R.string.runtime_management_source_install),
            ),
            processStates = mapOf(
                "运行中" to get(R.string.runtime_management_state_running),
                "running" to get(R.string.runtime_management_state_running),
                "已暂停" to get(R.string.runtime_management_state_paused),
                "paused" to get(R.string.runtime_management_state_paused),
                "僵尸" to get(R.string.runtime_management_state_zombie),
                "zombie" to get(R.string.runtime_management_state_zombie),
                "未知" to get(R.string.runtime_management_state_unknown),
                "unknown" to get(R.string.runtime_management_state_unknown),
                "已退出" to get(R.string.runtime_management_state_exited),
                "exited" to get(R.string.runtime_management_state_exited),
                "已结束" to get(R.string.runtime_management_state_ended),
                "stopped" to get(R.string.runtime_management_state_ended),
            ),
            processTitles = mapOf(
                "容器守护进程" to get(R.string.runtime_management_process_supervisor),
                "PRoot 容量工作器" to get(R.string.runtime_management_process_capacity_worker),
                "PRoot 容器入口" to get(R.string.runtime_management_process_proot),
                "Kite 命令启动器" to get(R.string.runtime_management_process_runner),
                "语言环境检查" to get(R.string.runtime_management_process_locale_check),
                "运行目录准备" to get(R.string.runtime_management_process_runtime_dir),
                "进程" to get(R.string.runtime_management_process_fallback),
            ),
            processPurposes = mapOf(
                "维护 Ubuntu 容器里的后台服务" to get(R.string.runtime_management_purpose_supervisor),
                "为卡片和终端保留可用的 PRoot 容量" to get(R.string.runtime_management_purpose_capacity_worker),
                "启动并隔离 Ubuntu 文件系统" to get(R.string.runtime_management_purpose_proot),
                "执行卡片命令前的统一入口" to get(R.string.runtime_management_purpose_runner),
                "检查 Ubuntu 语言环境" to get(R.string.runtime_management_purpose_locale_check),
                "准备 Ubuntu 运行目录" to get(R.string.runtime_management_purpose_runtime_dir),
                "卡片或用户启动的普通进程" to get(R.string.runtime_management_process_generic_purpose),
            ),
            requested = get(R.string.runtime_management_state_requested),
            awaiting = get(R.string.runtime_management_state_awaiting),
            failed = get(R.string.runtime_management_state_failed),
            terminalFallback = get(R.string.runtime_management_terminal_fallback),
            terminalProcessCount = terminalProcessCount,
            processFallback = get(R.string.runtime_management_process_fallback),
            processGenericPurpose = get(R.string.runtime_management_process_generic_purpose),
            runtimeFoundation = get(R.string.runtime_management_group_runtime_foundation),
            ending = get(R.string.runtime_management_action_ending),
            endTerminal = get(R.string.runtime_management_action_end_terminal),
            stopTask = get(R.string.runtime_management_action_stop_task),
            endProcess = get(R.string.runtime_management_action_end_process),
            endProcessGroup = get(R.string.runtime_management_action_end_process_group),
            stopping = get(R.string.runtime_management_action_stopping),
            stop = get(R.string.runtime_management_action_stop),
            open = get(R.string.runtime_management_action_open),
            reportCaption = get(R.string.runtime_management_surface_report_caption),
            terminalCaption = get(R.string.runtime_management_surface_terminal_caption),
            waitingUrl = get(R.string.runtime_management_surface_waiting_url),
        )

        private val ZH_CN: Map<Int, String> = mapOf(
            R.string.runtime_management_card_fallback to "Kite 卡片",
            R.string.runtime_management_section_background to "后台服务",
            R.string.runtime_management_section_resource to "资源任务",
            R.string.runtime_management_section_terminal to "终端进程",
            R.string.runtime_management_section_system to "系统",
            R.string.runtime_management_section_other to "其他",
            R.string.runtime_management_owner_card to "卡片",
            R.string.runtime_management_owner_resource to "资源",
            R.string.runtime_management_owner_terminal to "卡片终端",
            R.string.runtime_management_owner_system to "系统",
            R.string.runtime_management_owner_unattributed to "未关联卡片",
            R.string.runtime_management_source_install to "安装",
            R.string.runtime_management_source_terminal to "终端",
            R.string.runtime_management_source_web to "网页",
            R.string.runtime_management_source_home to "首页",
            R.string.runtime_management_state_requested to "请求中",
            R.string.runtime_management_state_awaiting to "待确认",
            R.string.runtime_management_state_failed to "失败",
            R.string.runtime_management_state_running to "运行中",
            R.string.runtime_management_state_paused to "已暂停",
            R.string.runtime_management_state_zombie to "僵尸",
            R.string.runtime_management_state_unknown to "未知",
            R.string.runtime_management_state_exited to "已退出",
            R.string.runtime_management_state_ended to "已结束",
            R.string.runtime_management_terminal_fallback to "终端",
            R.string.runtime_management_process_fallback to "进程",
            R.string.runtime_management_process_generic_purpose to "卡片或用户启动的普通进程",
            R.string.runtime_management_group_runtime_foundation to "运行基础",
            R.string.runtime_management_process_supervisor to "容器守护进程",
            R.string.runtime_management_process_capacity_worker to "PRoot 容量工作器",
            R.string.runtime_management_process_proot to "PRoot 容器入口",
            R.string.runtime_management_process_runner to "Kite 命令启动器",
            R.string.runtime_management_process_locale_check to "语言环境检查",
            R.string.runtime_management_process_runtime_dir to "运行目录准备",
            R.string.runtime_management_purpose_supervisor to "维护 Ubuntu 容器里的后台服务",
            R.string.runtime_management_purpose_capacity_worker to "为卡片和终端保留可用的 PRoot 容量",
            R.string.runtime_management_purpose_proot to "启动并隔离 Ubuntu 文件系统",
            R.string.runtime_management_purpose_runner to "执行卡片命令前的统一入口",
            R.string.runtime_management_purpose_locale_check to "检查 Ubuntu 语言环境",
            R.string.runtime_management_purpose_runtime_dir to "准备 Ubuntu 运行目录",
            R.string.runtime_management_action_ending to "结束中",
            R.string.runtime_management_action_end_terminal to "结束终端",
            R.string.runtime_management_action_stop_task to "停止任务",
            R.string.runtime_management_action_end_process to "结束进程",
            R.string.runtime_management_action_end_process_group to "结束进程组",
            R.string.runtime_management_action_stopping to "停止中",
            R.string.runtime_management_action_stop to "停止",
            R.string.runtime_management_action_open to "打开",
            R.string.runtime_management_surface_report to "SH 报告",
            R.string.runtime_management_surface_terminal to "终端",
            R.string.runtime_management_surface_web to "网页",
            R.string.runtime_management_surface_report_caption to "执行输出",
            R.string.runtime_management_surface_terminal_caption to "终端窗口",
            R.string.runtime_management_surface_waiting_url to "等待网址",
            R.string.runtime_metric_cards to "卡片",
            R.string.runtime_management_status_unknown to "未启动",
            R.string.runtime_management_status_stopped to "已停止",
            R.string.runtime_management_status_starting to "启动中",
            R.string.runtime_management_status_running to "运行中",
            R.string.runtime_management_status_waiting_terminal to "等待终端",
            R.string.runtime_management_status_already_running to "已运行",
            R.string.runtime_management_status_opened to "已打开",
            R.string.runtime_management_status_completed to "已完成",
            R.string.runtime_management_status_failed to "启动失败",
            R.string.runtime_management_status_stopping to "停止中",
            R.string.runtime_management_status_cleanup_pending to "停止待确认",
            R.string.runtime_management_status_bridge_unavailable to "桥接不可用",
        )
    }
}
