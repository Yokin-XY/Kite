package com.kftest.app.ui.tasks

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.AppCompatImageButton
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.kftest.app.R
import com.kftest.app.foundation.runtime.TaskManagerAction
import com.kftest.app.foundation.runtime.TaskManagerProcessItem
import com.kftest.app.foundation.runtime.TaskManagerStore
import com.kftest.app.ui.terminal.TerminalChromeHost
import java.util.Locale
import kotlinx.coroutines.launch

class TaskManagerFragment : Fragment() {

    private lateinit var btnRefresh: AppCompatImageButton
    private lateinit var taskListRefresh: SwipeRefreshLayout
    private lateinit var tvEmpty: TextView
    private lateinit var runtimeListContainer: LinearLayout
    private var lastRenderedFingerprint: String = ""

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_task_manager, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        bindViews(view)
        observeSnapshot()
    }

    override fun onResume() {
        super.onResume()
        refreshSnapshot()
    }

    private fun bindViews(view: View) {
        btnRefresh = view.findViewById(R.id.btnRefreshTasks)
        taskListRefresh = view.findViewById(R.id.taskListRefresh)
        tvEmpty = view.findViewById(R.id.tvTaskEmpty)
        runtimeListContainer = view.findViewById(R.id.runtimeListContainer)

        taskListRefresh.setColorSchemeColors(
            ContextCompat.getColor(requireContext(), R.color.terminal_page_blue)
        )
        taskListRefresh.setOnRefreshListener {
            refreshSnapshot(force = true, userVisible = true)
        }
        btnRefresh.setOnClickListener {
            refreshSnapshot(force = true, userVisible = true)
            showHint("刷新已排队")
        }
    }

    private fun observeSnapshot() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                TaskManagerStore.snapshot.collect { snapshot ->
                    renderProcessList(snapshot.processes)
                    stopRefreshSpinner()
                }
            }
        }
    }

    private fun renderProcessList(items: List<TaskManagerProcessItem>) {
        val fingerprint = items.joinToString(separator = "\u001f") { item ->
            listOf(
                item.id,
                item.pid.toString(),
                item.parentPid.toString(),
                item.title,
                item.subtitle,
                item.sourceLabel,
                item.stateLabel,
                item.rawState,
                item.commandLine,
                item.linkedRuntimeId.orEmpty(),
                item.linkedTerminalSessionId.orEmpty(),
                item.runtimeOwnerKindLabel.orEmpty(),
                item.runtimeRealityLabel.orEmpty()
            ).joinToString(separator = "\u001e")
        }
        if (fingerprint == lastRenderedFingerprint) {
            return
        }
        lastRenderedFingerprint = fingerprint

        runtimeListContainer.removeAllViews()
        tvEmpty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        runtimeListContainer.visibility = if (items.isEmpty()) View.GONE else View.VISIBLE

        if (items.isEmpty()) {
            return
        }

        val inflater = LayoutInflater.from(requireContext())
        items.forEach { item ->
            val row = inflater.inflate(
                R.layout.item_task_runtime,
                runtimeListContainer,
                false
            )
            bindRuntimeItem(row, item)
            runtimeListContainer.addView(row)
        }
    }

    private fun bindRuntimeItem(view: View, item: TaskManagerProcessItem) {
        val cardTile = view.findViewById<MaterialCardView>(R.id.cardRuntimeIcon)
        val tvTile = view.findViewById<TextView>(R.id.tvRuntimeIcon)
        val tvTitle = view.findViewById<TextView>(R.id.tvRuntimeTitle)
        val tvSubtitle = view.findViewById<TextView>(R.id.tvRuntimeSubtitle)
        val cardStatus = view.findViewById<MaterialCardView>(R.id.cardRuntimeStatus)
        val tvStatus = view.findViewById<TextView>(R.id.tvRuntimeStatus)

        tvTile.text = buildIconText(item)
        tvTitle.text = item.title
        tvSubtitle.text = item.subtitle
        tvStatus.text = item.stateLabel

        cardTile.setCardBackgroundColor(resolveTileBackground(item))
        tvTile.setTextColor(ContextCompat.getColor(requireContext(), R.color.terminal_page_surface))
        cardStatus.setCardBackgroundColor(resolveStatusBackground(item))
        tvStatus.setTextColor(resolveStatusText(item))

        view.setOnClickListener {
            showProcessActions(item)
        }
    }

    private fun showProcessActions(item: TaskManagerProcessItem) {
        val actions = buildActionEntries(item)
        val labels = actions.map { it.label }.toTypedArray()

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(item.title)
            .setMessage(buildDetailText(item))
            .setItems(labels) { _, which ->
                when (actions[which].action) {
                    TaskManagerAction.OPEN_TERMINAL -> {
                        openLinkedTerminal(item)
                    }

                    TaskManagerAction.END_PROCESS -> {
                        TaskManagerStore.endProcess(requireContext(), item.pid)
                        showHint("正在结束 ${item.title}")
                        scheduleRefresh()
                    }

                    TaskManagerAction.STOP_RUNTIME -> {
                        val runtimeId = item.linkedRuntimeId ?: return@setItems
                        TaskManagerStore.stopRuntime(requireContext(), runtimeId)
                        showHint("正在停止 ${item.title}")
                        scheduleRefresh()
                    }

                    TaskManagerAction.RESTART_RUNTIME -> {
                        val runtimeId = item.linkedRuntimeId ?: return@setItems
                        TaskManagerStore.restartRuntime(requireContext(), runtimeId)
                        showHint("正在重启 ${item.title}")
                        scheduleRefresh()
                    }

                    TaskManagerAction.VIEW_LOG -> showRuntimeLog(item)
                    TaskManagerAction.REFRESH -> refreshSnapshot(force = true, userVisible = true)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showRuntimeLog(item: TaskManagerProcessItem) {
        val runtimeId = item.linkedRuntimeId
        if (runtimeId.isNullOrBlank()) {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(item.title)
                .setMessage(getString(R.string.task_log_unavailable))
                .setPositiveButton(android.R.string.ok, null)
                .show()
            return
        }

        val content = TaskManagerStore.readRuntimeLog(requireContext(), runtimeId).ifBlank {
            getString(R.string.task_log_empty)
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.task_log_title_format, item.title))
            .setMessage(content)
            .setPositiveButton(R.string.task_log_refresh) { dialog, _ ->
                dialog.dismiss()
                refreshSnapshot(force = true, userVisible = true)
            }
            .setNegativeButton(android.R.string.ok, null)
            .show()
    }

    private fun buildDetailText(item: TaskManagerProcessItem): String {
        val lines = mutableListOf<String>()
        lines += getString(R.string.task_detail_status, item.stateLabel)
        lines += getString(R.string.task_detail_source, item.sourceLabel)
        lines += "PID: ${if (item.pid > 0) item.pid else "会话级"}"
        lines += "PPID: ${if (item.parentPid > 0) item.parentPid else "--"}"
        lines += getString(R.string.task_detail_command, item.command)
        lines += getString(R.string.task_detail_cmdline, item.commandLine)
        if (!item.linkedTerminalTitle.isNullOrBlank()) {
            lines += getString(R.string.task_detail_terminal, item.linkedTerminalTitle)
        }
        if (!item.linkedRuntimeTitle.isNullOrBlank()) {
            lines += getString(R.string.task_detail_runtime, item.linkedRuntimeTitle)
        }
        if (!item.runtimeOwnerKindLabel.isNullOrBlank()) {
            lines += "运行归属: ${item.runtimeOwnerKindLabel} / ${item.runtimeRealityLabel ?: "--"}"
        }
        if (item.runtimeRootPid != null) {
            lines += "Runtime Root PID: ${item.runtimeRootPid}"
        }
        if (!item.runtimeStaleReason.isNullOrBlank()) {
            lines += "Stale: ${item.runtimeStaleReason}"
        }
        return lines.joinToString("\n")
    }

    private fun buildActionEntries(item: TaskManagerProcessItem): List<ActionEntry> {
        val preferredOrder = listOf(
            TaskManagerAction.OPEN_TERMINAL,
            TaskManagerAction.STOP_RUNTIME,
            TaskManagerAction.RESTART_RUNTIME,
            TaskManagerAction.VIEW_LOG,
            TaskManagerAction.END_PROCESS,
            TaskManagerAction.REFRESH
        )

        return preferredOrder
            .filter { item.availableActions.contains(it) }
            .map { action ->
                ActionEntry(
                    action = action,
                    label = when (action) {
                        TaskManagerAction.OPEN_TERMINAL -> getString(R.string.task_action_open_terminal)
                        TaskManagerAction.END_PROCESS -> getString(R.string.task_action_end_process)
                        TaskManagerAction.STOP_RUNTIME -> getString(R.string.task_action_stop)
                        TaskManagerAction.RESTART_RUNTIME -> getString(R.string.task_action_restart)
                        TaskManagerAction.VIEW_LOG -> getString(R.string.task_action_logs)
                        TaskManagerAction.REFRESH -> getString(R.string.task_refresh)
                    }
                )
            }
    }

    private fun buildIconText(item: TaskManagerProcessItem): String {
        val raw = item.title.trim()
        val tokens = raw.split(" ", "-", "_").filter { it.isNotBlank() }
        if (tokens.size >= 2) {
            return (tokens[0].first().toString() + tokens[1].first().toString()).uppercase(Locale.ROOT)
        }

        val compact = raw.filterNot { it.isWhitespace() }
        return when {
            compact.length >= 2 -> compact.substring(0, 2).uppercase(Locale.ROOT)
            compact.isNotEmpty() -> compact.uppercase(Locale.ROOT)
            else -> "KF"
        }
    }

    private fun resolveTileBackground(item: TaskManagerProcessItem): Int {
        val colorRes = when {
            item.linkedTerminalSessionId != null -> R.color.task_page_tile_blue
            item.linkedRuntimeId != null -> R.color.task_page_tile_green
            item.rawState.startsWith("Z", ignoreCase = true) -> R.color.task_page_tile_red
            else -> R.color.task_page_tile_blue
        }
        return ContextCompat.getColor(requireContext(), colorRes)
    }

    private fun resolveStatusBackground(item: TaskManagerProcessItem): Int {
        val colorRes = when {
            item.rawState.startsWith("R", ignoreCase = true) ||
                item.rawState.startsWith("S", ignoreCase = true) ||
                item.rawState.startsWith("D", ignoreCase = true) ||
                item.rawState.startsWith("I", ignoreCase = true) -> R.color.task_page_chip_green_bg

            item.rawState.startsWith("T", ignoreCase = true) -> R.color.task_page_chip_gray_bg
            else -> R.color.task_page_chip_red_bg
        }
        return ContextCompat.getColor(requireContext(), colorRes)
    }

    private fun resolveStatusText(item: TaskManagerProcessItem): Int {
        val colorRes = when {
            item.rawState.startsWith("R", ignoreCase = true) ||
                item.rawState.startsWith("S", ignoreCase = true) ||
                item.rawState.startsWith("D", ignoreCase = true) ||
                item.rawState.startsWith("I", ignoreCase = true) -> R.color.task_page_chip_green_text

            item.rawState.startsWith("T", ignoreCase = true) -> R.color.task_page_chip_gray_text
            else -> R.color.task_page_chip_red_text
        }
        return ContextCompat.getColor(requireContext(), colorRes)
    }

    private fun refreshSnapshot(force: Boolean = false, userVisible: Boolean = false) {
        if (userVisible && ::taskListRefresh.isInitialized) {
            taskListRefresh.isRefreshing = true
        }
        TaskManagerStore.refresh(requireContext(), force = force)
    }

    private fun stopRefreshSpinner() {
        if (::taskListRefresh.isInitialized) {
            taskListRefresh.isRefreshing = false
        }
    }

    private fun openLinkedTerminal(item: TaskManagerProcessItem) {
        val sessionId = item.linkedTerminalSessionId ?: return
        val host = activity as? TerminalChromeHost
        if (host == null) {
            showHint("当前无法直接打开关联终端")
            return
        }
        host.openTerminalSession(sessionId)
        showHint("已切到 ${item.linkedTerminalTitle ?: item.title}")
    }

    private fun scheduleRefresh() {
        view?.postDelayed({ refreshSnapshot() }, 550)
    }

    private fun showHint(message: String) {
        view?.let { root ->
            Snackbar.make(root, message, Snackbar.LENGTH_SHORT).show()
        }
    }

    private data class ActionEntry(
        val action: TaskManagerAction,
        val label: String
    )
}
