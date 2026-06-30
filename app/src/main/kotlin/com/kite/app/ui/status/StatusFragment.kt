package com.kite.app.ui.status

import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.StatFs
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.kite.app.R
import com.kite.app.foundation.contracts.ContainerRecord
import com.kite.app.foundation.runtime.ProotTelemetryRepairAction
import com.kite.app.foundation.runtime.ProotTelemetryRepairReadiness
import com.kite.app.foundation.runtime.ProotTelemetryStore
import com.kite.app.foundation.contracts.RuntimeActionKind
import com.kite.app.foundation.runtime.RuntimeAutomationActions
import com.kite.app.foundation.runtime.RuntimeDiagnostics
import com.kite.app.foundation.runtime.RuntimeHealthSnapshot
import com.kite.app.foundation.runtime.RuntimeHealthStore
import com.kite.app.foundation.workspace.WorkSurfaceRuntimeBridge
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

class StatusFragment : Fragment() {

    private companion object {
        private const val HEALTH_REFRESH_MIN_INTERVAL_MS = 10_000L
    }

    private lateinit var tvMemoryUsage: TextView
    private lateinit var tvCpuUsage: TextView
    private lateinit var tvDiskUsage: TextView
    private lateinit var tvNetworkStats: TextView
    private lateinit var cardProotManagementQuick: View
    private lateinit var tvProotManagementQuickStatus: TextView
    private lateinit var btnRunProotCalibrationQuick: MaterialButton
    private lateinit var cardProotCalibration: View
    private lateinit var tvProotCalibrationStatus: TextView
    private lateinit var btnRunProotCalibrationP0: MaterialButton
    private lateinit var cardTelemetryRepair: View
    private lateinit var tvTelemetryRepairStatus: TextView
    private lateinit var btnTelemetryRepair: MaterialButton
    private lateinit var progressMemory: ProgressBar
    private lateinit var progressCpu: ProgressBar

    private var lastProcessTicks: Long? = null
    private var lastSystemTicks: Long? = null
    private var lastPid: Int? = null
    private var prootCalibrationUiRunStartedAtMs: Long? = null
    private var lastProotCalibrationCompletionKey: String? = null
    private var lastHealthRefreshAtMs: Long = 0L
    private var statusUpdateJob: Job? = null

    private val handler = Handler(Looper.getMainLooper())
    private val updateRunnable = object : Runnable {
        override fun run() {
            updateStats()
            handler.postDelayed(this, 3000)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_status, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupViews(view)
    }

    override fun onResume() {
        super.onResume()
        handler.removeCallbacks(updateRunnable)
        handler.post(updateRunnable)
    }

    override fun onPause() {
        handler.removeCallbacks(updateRunnable)
        super.onPause()
    }

    private fun setupViews(view: View) {
        tvMemoryUsage = view.findViewById(R.id.tvMemoryUsage)
        tvCpuUsage = view.findViewById(R.id.tvCpuUsage)
        tvDiskUsage = view.findViewById(R.id.tvDiskUsage)
        tvNetworkStats = view.findViewById(R.id.tvNetworkStats)
        cardProotManagementQuick = view.findViewById(R.id.cardProotManagementQuick)
        tvProotManagementQuickStatus = view.findViewById(R.id.tvProotManagementQuickStatus)
        btnRunProotCalibrationQuick = view.findViewById(R.id.btnRunProotCalibrationQuick)
        cardProotCalibration = view.findViewById(R.id.cardProotCalibration)
        tvProotCalibrationStatus = view.findViewById(R.id.tvProotCalibrationStatus)
        btnRunProotCalibrationP0 = view.findViewById(R.id.btnRunProotCalibrationP0)
        cardTelemetryRepair = view.findViewById(R.id.cardTelemetryRepair)
        tvTelemetryRepairStatus = view.findViewById(R.id.tvTelemetryRepairStatus)
        btnTelemetryRepair = view.findViewById(R.id.btnTelemetryRepair)
        progressMemory = view.findViewById(R.id.progressMemory)
        progressCpu = view.findViewById(R.id.progressCpu)
        cardProotCalibration.visibility = if (isDebuggable()) View.VISIBLE else View.GONE
        btnRunProotCalibrationP0.setOnClickListener { startProotCalibration("p0") }
        btnRunProotCalibrationQuick.setOnClickListener { startProotCalibration("p0") }
        cardTelemetryRepair.visibility = if (isDebuggable()) View.VISIBLE else View.GONE
        btnTelemetryRepair.setOnClickListener { confirmTelemetryRepair() }
    }

    private fun updateStats() {
        val appContext = context?.applicationContext ?: return
        val now = System.currentTimeMillis()
        if (now - lastHealthRefreshAtMs >= HEALTH_REFRESH_MIN_INTERVAL_MS) {
            if (statusUpdateJob?.isActive == true) {
                renderStatusSnapshot(appContext, RuntimeHealthStore.snapshot.value)
                return
            }
            renderStatusSnapshot(appContext, RuntimeHealthStore.snapshot.value)
            statusUpdateJob = viewLifecycleOwner.lifecycleScope.launch {
                val health = withContext(Dispatchers.IO) {
                    RuntimeHealthStore.refresh(appContext, reason = "status-ui-periodic-refresh")
                    RuntimeHealthStore.snapshot.value
                }
                lastHealthRefreshAtMs = now
                renderStatusSnapshot(appContext, health)
            }
            return
        }
        renderStatusSnapshot(appContext, RuntimeHealthStore.snapshot.value)
    }

    private fun renderStatusSnapshot(context: android.content.Context, health: RuntimeHealthSnapshot) {
        val container = WorkSurfaceRuntimeBridge.getSavedContainer(context)
        val metricsPid = health.primaryMetricsPid

        updateMemory(metricsPid, health)
        updateCpu(metricsPid, health)
        updateDisk(context)
        updateNetwork(container, health)
        updateProotCalibrationCard(health)
        updateTelemetryRepairCard(health)
    }

    private fun updateMemory(pid: Int?, health: RuntimeHealthSnapshot) {
        if (pid == null || pid <= 0) {
            tvMemoryUsage.text = buildNoMetricsLabel(health)
            progressMemory.progress = 0
            return
        }

        val totalMem = readMemInfo()["MemTotal"] ?: 0L
        val rssKb = readProcessStatusValue(pid, "VmRSS")
        val percent = if (totalMem > 0) ((rssKb * 100) / totalMem).toInt() else 0

        tvMemoryUsage.text = "运行根 RSS: ${formatKB(rssKb)} / 系统总内存: ${formatKB(totalMem)}"
        progressMemory.progress = percent.coerceIn(0, 100)
    }

    private fun updateCpu(pid: Int?, health: RuntimeHealthSnapshot) {
        if (pid == null || pid <= 0) {
            lastPid = null
            lastProcessTicks = null
            lastSystemTicks = null
            tvCpuUsage.text = buildNoMetricsLabel(health)
            progressCpu.progress = 0
            return
        }

        val processTicks = readProcessTicks(pid)
        val systemTicks = readSystemTicks()

        if (processTicks == null || systemTicks == null) {
            tvCpuUsage.text = "无法获取 CPU 信息"
            progressCpu.progress = 0
            return
        }

        val cpuPercent = if (lastPid == pid && lastProcessTicks != null && lastSystemTicks != null) {
            val deltaProcess = processTicks - (lastProcessTicks ?: processTicks)
            val deltaSystem = systemTicks - (lastSystemTicks ?: systemTicks)
            if (deltaSystem > 0) {
                ((deltaProcess * 100 * Runtime.getRuntime().availableProcessors()) / deltaSystem)
                    .toInt()
                    .coerceIn(0, 100)
            } else {
                0
            }
        } else {
            0
        }

        lastPid = pid
        lastProcessTicks = processTicks
        lastSystemTicks = systemTicks

        tvCpuUsage.text = "运行根 CPU 占用: $cpuPercent%"
        progressCpu.progress = cpuPercent
    }

    private fun updateDisk(context: android.content.Context) {
        val statFs = StatFs(context.filesDir.absolutePath)
        val totalBytes = statFs.totalBytes
        val availableBytes = statFs.availableBytes
        val usedBytes = totalBytes - availableBytes

        tvDiskUsage.text = "应用私有存储: ${formatBytes(usedBytes)} / ${formatBytes(totalBytes)}"
    }

    private fun updateNetwork(container: ContainerRecord?, health: RuntimeHealthSnapshot) {
        val context = context ?: return
        val snapshot = WorkSurfaceRuntimeBridge.resolveRuntimeSnapshot(context, container)
        val networkMode = health.networkModeLabel ?: container?.networkMode?.label ?: "未初始化"
        val workspaceLabel = WorkSurfaceRuntimeBridge.describeHostPath(
            context = context,
            path = container?.workspacePath,
            container = container
        )
        val exchangeLabel = WorkSurfaceRuntimeBridge.describeHostPath(
            context = context,
            path = snapshot.exchangeDir.absolutePath,
            container = container
        )
        val browseRoute = WorkSurfaceRuntimeBridge.actionRouteLabel(RuntimeActionKind.WORKSPACE_BROWSE)
        val buildRoute = WorkSurfaceRuntimeBridge.actionRouteLabel(RuntimeActionKind.MOBILE_BUILD)
        val diagnostics = RuntimeDiagnostics.from(health)
        tvNetworkStats.text =
            "运行实例:\n${formatRuntimeHealth(health)}\n网络模式: $networkMode\n工作区: $workspaceLabel\n投递区: $exchangeLabel\n文件页: $browseRoute / 构建: $buildRoute\n---\n${diagnostics.toStatusText()}"
    }

    private fun updateTelemetryRepairCard(health: RuntimeHealthSnapshot) {
        if (!isDebuggable()) {
            cardTelemetryRepair.visibility = View.GONE
            return
        }
        cardTelemetryRepair.visibility = View.VISIBLE
        val telemetryHealth = health.prootTelemetryHealth
        val repairPlan = health.prootTelemetryRepairPlan
        val ready = repairPlan.action == ProotTelemetryRepairAction.ROTATE_HISTORY_CONTAMINATED_JSONL &&
            repairPlan.readiness == ProotTelemetryRepairReadiness.MANUAL_READY
        btnTelemetryRepair.isEnabled = ready
        tvTelemetryRepairStatus.text = buildString {
            appendLine("health=${telemetryHealth.state}, blocker=${telemetryHealth.blocker}")
            appendLine("repair=${repairPlan.action}, readiness=${repairPlan.readiness}")
            appendLine("skipped=${repairPlan.currentSkippedBytes}, parseErrors=${repairPlan.currentParseErrors}")
            append("source=${repairPlan.sourcePath.ifBlank { "none" }}")
        }
    }

    private fun updateProotCalibrationCard(health: RuntimeHealthSnapshot) {
        if (!isDebuggable()) {
            cardProotCalibration.visibility = View.GONE
            return
        }
        cardProotCalibration.visibility = View.VISIBLE
        val calibration = health.prootDeviceCalibration
        val pool = health.prootPoolPlan
        val mainline = health.prootManagementMainline
        val calibrationStatus = readProotCalibrationStatus()
        val calibrationRunning = calibrationStatus.state == "RUNNING"
        btnRunProotCalibrationQuick.isEnabled = !calibrationRunning
        btnRunProotCalibrationQuick.text = if (calibrationRunning) {
            "校准运行中..."
        } else {
            "开始 PRoot 校准"
        }
        tvProotManagementQuickStatus.text = buildString {
            appendLine("目标：标准任务吞吐曲线；取实测峰值，乘以一个溢出倍率决定何时启用第二 PRoot。")
            appendLine(
                "当前校准水位：${pool.adaptiveStrategyActiveBand}；" +
                    "单 PRoot 吞吐峰值=${pool.adaptiveStrategyPeakTracees}，" +
                    "单 PRoot 尽量塞到=${pool.adaptiveStrategyQueueUntilTracees}，" +
                    "超过后启用第二 PRoot=${pool.adaptiveStrategySecondProotTriggerTracees}"
            )
            appendLine(
                "容量：起点=${calibration.overlayDefaultStartCap}，" +
                    "舒适=${calibration.overlayHealthyStableTraceeCap}，" +
                    "预算观察=${calibration.overlayBudgetKneeTracees}(不触发扩容)，" +
                    "模型护栏=${calibration.overlayModelGuardKneeTracees}，" +
                    "最大=${calibration.overlayTraceeMaxCap}"
            )
            appendLine("当前：${mainline.state}/${mainline.recommendation}，risk=${pool.resourceEquationRiskPercent}%，瓶颈=${pool.resourceEquationBottleneckAxis}")
            appendLine("校准：${calibrationStatus.stateLabel}，${calibrationStatus.detail}")
            appendLine("进度：${calibrationStatus.progress}")
            append("结果：${calibrationStatus.summary}")
        }
        tvProotCalibrationStatus.text = buildString {
            appendLine("校准状态：${calibration.state} / ${calibration.recommendation}")
            appendLine("overlay=${calibration.overlayValid}, status=${calibration.overlayStatus}")
            appendLine(
                "tracee: start=${calibration.overlayDefaultStartCap}, " +
                    "max=${calibration.overlayTraceeMaxCap}, " +
                    "soft=${calibration.conservativeTraceeSoftCap}->${pool.deviceCalibrationTraceeSoftCap}, " +
                    "hard=${calibration.conservativeTraceeHardCap}->${pool.deviceCalibrationTraceeHardCap}"
            )
            appendLine(
                "校准水位: peak=${pool.adaptiveStrategyPeakTracees}, " +
                    "singleProotLimit=${pool.adaptiveStrategyQueueUntilTracees}, " +
                    "secondProotFrom=${pool.adaptiveStrategySecondProotTriggerTracees}, " +
                    "multiplier=1.${pool.adaptiveStrategySingleProotOverflowPercent}, " +
                    "base=${pool.adaptiveStrategyOverflowPercentBase}"
            )
            appendLine("旧档位=${calibration.profileLimitPolicy}，仅作兼容显示；实际按峰值倍率触发第二 PRoot")
            appendLine("memory worker rss=${pool.deviceCalibrationMemoryWorkerRssKb}KB")
            appendLine(
                "dynamic posture: concurrency=${pool.adaptiveConcurrencyPosture}, " +
                    "queue=${pool.adaptiveQueuePosture}, limiter=${pool.adaptiveResourceLimiter}"
            )
            appendLine(
                "equation: risk=${pool.resourceEquationRiskPercent}%, " +
                    "headroom=${pool.resourceEquationHeadroomPercent}%, " +
                    "bottleneck=${pool.resourceEquationBottleneckAxis}, decision=${pool.resourceEquationDecision}"
            )
            appendLine("mainline=${mainline.state}/${mainline.recommendation}, blocker=${calibration.blocker}")
            appendLine("UI run: ${calibrationStatus.detail}")
            appendLine("UI result: ${calibrationStatus.summary}")
            append("logs: /workspace/.kf/proot-device-calibration-plan.jsonl / proot-device-calibration-p0-summary.json / proot-device-calibration.json")
        }
        maybeShowProotCalibrationCompletion(calibrationStatus)
    }

    private fun startProotCalibration(target: String) {
        if (target.equals("p0", ignoreCase = true)) {
            prootCalibrationUiRunStartedAtMs = System.currentTimeMillis()
        }
        RuntimeAutomationActions.runProotCalibration(requireContext(), target)
        Toast.makeText(
            requireContext(),
            "已启动 PRoot ${target.uppercase()} 校准，日志写入 /workspace/.kf 和 adb-automation.log",
            Toast.LENGTH_SHORT
        ).show()
        updateStats()
    }

    private data class ProotCalibrationUiStatus(
        val state: String,
        val stateLabel: String,
        val detail: String,
        val progress: String,
        val summary: String,
        val completionKey: String,
        val completedAtMs: Long
    )

    private fun readProotCalibrationStatus(): ProotCalibrationUiStatus {
        val appContext = context?.applicationContext
            ?: return ProotCalibrationUiStatus("UNKNOWN", "未知", "no_context", "无", "无", "", 0L)
        val workspaceDir = WorkSurfaceRuntimeBridge.getSavedContainer(appContext)
            ?.workspacePath
            ?.takeIf { it.isNotBlank() }
            ?.let(::File)
            ?: return ProotCalibrationUiStatus("WAITING", "等待", "工作区未就绪", "无", "无", "", 0L)
        val kfDir = File(workspaceDir, ".kf")
        val planFile = File(kfDir, "proot-device-calibration-plan.jsonl")
        val summaryFile = File(kfDir, "proot-device-calibration-p0-summary.json")
        val overlayFile = File(kfDir, "proot-device-calibration.json")
        val lastPlan = planFile.lastJsonLineOrNull()
        val summary = summaryFile.jsonObjectOrNull()
        val overlay = overlayFile.jsonObjectOrNull()
        val now = System.currentTimeMillis()
        val lastPlanRunId = lastPlan?.optString("runId").orEmpty()
        val summaryRunId = summary?.optString("runId").orEmpty()
        val lastPhase = lastPlan?.optString("phase").orEmpty()
        val lastPlanAt = lastPlan?.let { plan ->
            plan.optLong("atMs", 0L).takeIf { it > 0L }
                ?: plan.optLong("generatedAtMs", 0L).takeIf { it > 0L }
        } ?: 0L
        val summaryCompletedAt = summaryFile
            .takeIf { it.exists() }
            ?.lastModified()
            ?: 0L
        val startedAt = findRunStartMs(planFile, lastPlanRunId)
            ?: prootCalibrationUiRunStartedAtMs
        val elapsedText = startedAt?.let { formatDurationMs(now - it) } ?: "--"
        val latestSummaryMatchesPlan = summary != null &&
            (lastPlanRunId.isBlank() || summaryRunId.isBlank() || summaryRunId == lastPlanRunId)
        val summaryComplete = summary?.optBoolean("calibrationComplete", false) == true ||
            summary?.optBoolean("complete", false) == true
        val overlayWritten = lastPhase == "OVERLAY_WRITTEN"
        val runError = lastPhase == "RUN_ERROR"
        val planAfterSummary = lastPlanAt > summaryCompletedAt && !overlayWritten && !runError
        val activeCandidate = lastPlan != null &&
            !overlayWritten &&
            !runError &&
            (summary == null || planAfterSummary || !summaryComplete || !latestSummaryMatchesPlan)
        val workerDurationMs = ((lastPlan?.optDouble("workerDurationSeconds", 15.0) ?: 15.0) * 1000L)
            .toLong()
            .coerceAtLeast(15_000L)
        val phaseStaleMs = when (lastPhase) {
            "WORKERS_STARTED" -> workerDurationMs + 90_000L
            "WORKER_START_PROGRESS" -> 90_000L
            "PLAN_DECLARED", "BASELINE_AFTER_PREPARE", "WORKERS_CLEANED" -> 120_000L
            "RUN_START", "CHECKPOINT" -> 180_000L
            else -> 120_000L
        }
        val staleActive = activeCandidate && lastPlanAt > 0L && now - lastPlanAt > phaseStaleMs
        val running = activeCandidate &&
            !staleActive &&
            (startedAt == null || now - startedAt < 4 * 60 * 60_000L)
        val state = when {
            running -> "RUNNING"
            runError -> "FAILED"
            staleActive -> "INTERRUPTED"
            summaryComplete && latestSummaryMatchesPlan -> "COMPLETE"
            overlay?.optBoolean("valid", false) == true -> "COMPLETE"
            summary != null -> "HAS_SUMMARY"
            lastPlan != null -> "PLAN_ONLY"
            else -> "NOT_RUN"
        }
        val target = lastPlan?.optInt("targetLiveTracees", 0)?.takeIf { it > 0 }
            ?: lastPlan?.optInt("nextTargetLiveTracees", 0)?.takeIf { it > 0 }
        val step = lastPlan?.optString("stepId").orEmpty().ifBlank { "未开始" }
        val result = lastPlan?.optString("result").orEmpty().ifBlank { "等待采样" }
        val detail = when (state) {
            "RUNNING" -> "已运行 $elapsedText，当前第 ${target ?: "?"} 轮"
            "INTERRUPTED" -> "运行已停止/卡住，最后更新 ${formatDurationMs(now - lastPlanAt)} 前，可重新开始"
            "COMPLETE", "HAS_SUMMARY" -> "完成于 ${formatDurationMs(now - summaryCompletedAt)} 前"
            "FAILED" -> "运行失败，${formatDurationMs(now - lastPlanAt)} 前"
            "PLAN_ONLY" -> "已有计划，${formatDurationMs(now - lastPlanAt)} 前"
            else -> "尚未运行"
        }
        val progress = when (state) {
            "RUNNING" -> "阶段=${lastPhase.ifBlank { "启动中" }}，步骤=$step，结果=$result"
            "INTERRUPTED" -> "停止在阶段=${lastPhase.ifBlank { "unknown" }}，步骤=$step；已保留最后安全 overlay/日志"
            "COMPLETE" -> "已完成，summary 与 overlay 可用于策略"
            "FAILED" -> "失败阶段=${lastPhase.ifBlank { "unknown" }}，请看 adb-automation.log"
            "HAS_SUMMARY" -> "有 summary，但未确认完整；请复核日志"
            "PLAN_ONLY" -> "只有计划日志，还没有 summary"
            else -> "等待点击开始"
        }
        val summaryText = when {
            summary != null -> buildProotCalibrationSummaryText(summary)
            overlay != null -> buildProotOverlaySummaryText(overlay)
            else -> "未校准，正在使用安全默认值"
        }
        val completionKey = when {
            summaryRunId.isNotBlank() -> "$summaryRunId:$summaryCompletedAt"
            overlay != null -> "overlay:${overlayFile.lastModified()}"
            else -> ""
        }
        return ProotCalibrationUiStatus(
            state = state,
            stateLabel = when (state) {
                "RUNNING" -> "运行中"
                "INTERRUPTED" -> "已中断"
                "COMPLETE" -> "已完成"
                "FAILED" -> "失败"
                "HAS_SUMMARY" -> "有结果待复核"
                "PLAN_ONLY" -> "已有计划"
                "WAITING" -> "等待工作区"
                else -> "未运行"
            },
            detail = detail,
            progress = progress,
            summary = summaryText,
            completionKey = completionKey,
            completedAtMs = summaryCompletedAt
        )
    }

    private fun buildProotCalibrationSummaryText(summary: JSONObject): String {
        val overlay = summary.optJSONObject("recommendedOverlay") ?: JSONObject()
        val max = summary.optInt("measuredMaxTracees", overlay.optInt("traceeMaxCap", 0))
        val comfort = summary.optInt("healthyStableTraceeCap", overlay.optInt("healthyStableTraceeCap", 0))
        val knee = summary.optInt("budgetKneeTracees", overlay.optInt("budgetKneeTracees", 0))
        val modelGuard = summary.optInt("modelGuardKneeTracees", overlay.optInt("modelGuardKneeTracees", 0))
        val firstFail = summary.optInt("firstFailTracees", 0)
        val lastPass = summary.optInt("lastPassTracees", overlay.optInt("safeTestedMaxTracees", max))
        val strategy = overlay.optJSONObject("queueStrategy") ?: JSONObject()
        val peak = strategy.optInt(
            "throughputPeakTracees",
            overlay.optInt("singleProotPeakTracees", summary.optInt("singleProotPeakTracees", max))
        )
        val queueUntil = strategy.optInt(
            "queueUntilTracees",
            overlay.optInt("queueUntilTracees", summary.optInt("queueUntilTracees", peak))
        )
        val secondTrigger = strategy.optInt(
            "secondProotTriggerTracees",
            overlay.optInt("secondProotTriggerTracees", summary.optInt("secondProotTriggerTracees", queueUntil + 1))
        )
        val failText = if (firstFail > 0) firstFail.toString() else "未遇到"
        val limitKind = summary.optString("measuredLimitKind")
        val maxLabel = when (limitKind) {
            "tested_lower_bound_reached_configured_max" -> "已测≥"
            "throughput_peak_after_decline_confirmed" -> "吞吐峰值="
            else -> "最大="
        }
        val throughput = summary.optDouble("throughputBestUnitsPerSecond", 0.0)
            .takeIf { it > 0.0 }
            ?.let { "，吞吐=${String.format("%.2f", it)}任务/秒" }
            .orEmpty()
        val avg = summary.optDouble("throughputBestAvgMsPerUnit", 0.0)
            .takeIf { it > 0.0 }
            ?.let { "，均时=${String.format("%.1f", it)}ms/任务" }
            .orEmpty()
        val overflowPercent = strategy.optInt(
            "singleProotOverflowPercent",
            overlay.optInt("singleProotOverflowPercent", summary.optInt("singleProotOverflowPercent", 0))
        )
        val overflowText = if (overflowPercent > 0) {
            "，倍率=${String.format("%.2f", 1.0 + overflowPercent / 100.0)}"
        } else {
            ""
        }
        return "${maxLabel}$max，当前校准水位：单 PRoot 吞吐峰值=$peak，单 PRoot 尽量塞到=$queueUntil，超过后启用第二 PRoot=$secondTrigger$overflowText；硬上限看 measuredMax/hardCap，舒适=$comfort，预算观察=$knee(不触发扩容)，模型护栏=$modelGuard，最后通过=$lastPass，首次失败=$failText$throughput$avg"
    }

    private fun buildProotOverlaySummaryText(overlay: JSONObject): String {
        val strategy = overlay.optJSONObject("queueStrategy") ?: JSONObject()
        val peak = strategy.optInt("throughputPeakTracees", overlay.optInt("singleProotPeakTracees", overlay.optInt("traceeMaxCap", 0)))
        val queueUntil = strategy.optInt("queueUntilTracees", overlay.optInt("queueUntilTracees", overlay.optInt("traceeSoftCap", peak)))
        val secondTrigger = strategy.optInt("secondProotTriggerTracees", overlay.optInt("secondProotTriggerTracees", queueUntil + 1))
        val hard = overlay.optInt("traceeHardCap", overlay.optInt("runtimeHardCapTracees", overlay.optInt("traceeMaxCap", 0)))
        val overflowPercent = strategy.optInt("singleProotOverflowPercent", overlay.optInt("singleProotOverflowPercent", 0))
        val overflowText = if (overflowPercent > 0) {
            "，倍率=${String.format("%.2f", 1.0 + overflowPercent / 100.0)}"
        } else {
            ""
        }
        return "已应用：当前校准水位：单 PRoot 吞吐峰值=$peak，单 PRoot 尽量塞到=$queueUntil，超过后启用第二 PRoot=$secondTrigger$overflowText；硬上限=$hard，舒适=${overlay.optInt("healthyStableTraceeCap", overlay.optInt("traceeSoftCap", 0))}，预算观察=${overlay.optInt("budgetKneeTracees", 0)}(不触发扩容)，模型护栏=${overlay.optInt("modelGuardKneeTracees", 0)}"
    }

    private fun maybeShowProotCalibrationCompletion(status: ProotCalibrationUiStatus) {
        if (status.state != "COMPLETE" || status.completionKey.isBlank()) return
        if (lastProotCalibrationCompletionKey == status.completionKey) return
        val startedAt = prootCalibrationUiRunStartedAtMs
        val recentCompletion = status.completedAtMs > 0L &&
            System.currentTimeMillis() - status.completedAtMs < 10 * 60_000L
        if (startedAt == null && !recentCompletion) {
            lastProotCalibrationCompletionKey = status.completionKey
            return
        }
        lastProotCalibrationCompletionKey = status.completionKey
        if (!isResumed) return
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("PRoot 校准已完成")
            .setMessage("${status.summary}\n\n结果已写入 /workspace/.kf/proot-device-calibration.json")
            .setPositiveButton("知道了", null)
            .show()
    }

    private fun File.jsonObjectOrNull(): JSONObject? {
        return runCatching {
            if (!exists()) return null
            JSONObject(readText())
        }.getOrNull()
    }

    private fun File.lastJsonLineOrNull(): JSONObject? {
        return runCatching {
            if (!exists()) return null
            readLines()
                .asReversed()
                .firstNotNullOfOrNull { line ->
                    val trimmed = line.trim()
                    if (trimmed.isBlank()) null else runCatching { JSONObject(trimmed) }.getOrNull()
                }
        }.getOrNull()
    }

    private fun findRunStartMs(planFile: File, runId: String): Long? {
        if (runId.isBlank()) return null
        return runCatching {
            if (!planFile.exists()) return null
            planFile.useLines { lines ->
                lines.firstNotNullOfOrNull { line ->
                    val json = runCatching { JSONObject(line) }.getOrNull()
                    if (json?.optString("runId") == runId) {
                        json.optLong("atMs", 0L).takeIf { it > 0L }
                            ?: json.optLong("generatedAtMs", 0L).takeIf { it > 0L }
                    } else {
                        null
                    }
                }
            }
        }.getOrNull()
    }

    private fun formatDurationMs(durationMs: Long): String {
        val safeMs = durationMs.coerceAtLeast(0L)
        val totalSeconds = safeMs / 1000L
        val minutes = totalSeconds / 60L
        val seconds = totalSeconds % 60L
        return if (minutes > 0L) {
            "${minutes}m${seconds}s"
        } else {
            "${seconds}s"
        }
    }

    private fun confirmTelemetryRepair() {
        val current = RuntimeHealthStore.snapshot.value
        val plan = current.prootTelemetryRepairPlan
        if (
            plan.action != ProotTelemetryRepairAction.ROTATE_HISTORY_CONTAMINATED_JSONL ||
            plan.readiness != ProotTelemetryRepairReadiness.MANUAL_READY
        ) {
            updateTelemetryRepairCard(current)
            return
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("执行 telemetry 手动修复？")
            .setMessage(
                "将归档当前 PRoot telemetry JSONL 副本，然后清空原文件，保留当前 PRoot 进程继续写入。" +
                    "\n\nsource=${plan.sourcePath}\nskipped=${plan.currentSkippedBytes}"
            )
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton("执行修复") { _, _ -> executeTelemetryRepair() }
            .show()
    }

    private fun executeTelemetryRepair() {
        val appContext = requireContext().applicationContext
        btnTelemetryRepair.isEnabled = false
        tvTelemetryRepairStatus.text = "manual repair running..."
        viewLifecycleOwner.lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                RuntimeHealthStore.attachContext(appContext)
                val repair = ProotTelemetryStore.rotateHistoryContaminatedJsonl(
                    context = appContext,
                    reason = "status-ui-manual-repair"
                )
                RuntimeHealthStore.refresh(appContext, reason = "status-ui-proot-telemetry-repair")
                repair
            }
            tvTelemetryRepairStatus.text = result.toLogBlock()
            updateStats()
        }
    }

    private fun isDebuggable(): Boolean {
        val appContext = context?.applicationContext ?: return false
        return appContext.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
    }

    private fun buildNoMetricsLabel(health: RuntimeHealthSnapshot): String {
        return when {
            health.roots.isEmpty() -> "未发现运行实例"
            health.runningRootCount == 0 && health.staleRootCount > 0 -> "未发现可采样运行根，旧 PID 已忽略"
            else -> "运行实例暂不可采样"
        }
    }

    private fun formatRuntimeHealth(health: RuntimeHealthSnapshot): String {
        return RuntimeDiagnostics.statusText(health)
    }

    private fun readMemInfo(): Map<String, Long> {
        val result = mutableMapOf<String, Long>()
        BufferedReader(FileReader("/proc/meminfo")).use { reader ->
            reader.readLines().forEach { line ->
                val parts = line.split(":\\s+".toRegex())
                if (parts.size == 2) {
                    result[parts[0].trim()] = parts[1]
                        .trim()
                        .removeSuffix(" kB")
                        .toLongOrNull() ?: 0L
                }
            }
        }
        return result
    }

    private fun readProcessStatusValue(pid: Int, key: String): Long {
        return runCatching {
            BufferedReader(FileReader("/proc/$pid/status")).use { reader ->
                reader.readLines()
                    .firstOrNull { it.startsWith("$key:") }
                    ?.substringAfter(":")
                    ?.trim()
                    ?.removeSuffix(" kB")
                    ?.toLongOrNull() ?: 0L
            }
        }.getOrDefault(0L)
    }

    private fun readProcessTicks(pid: Int): Long? {
        return runCatching {
            val statLine = BufferedReader(FileReader("/proc/$pid/stat")).use { it.readLine() }
            val parts = statLine.split("\\s+".toRegex())
            val utime = parts.getOrNull(13)?.toLongOrNull() ?: 0L
            val stime = parts.getOrNull(14)?.toLongOrNull() ?: 0L
            utime + stime
        }.getOrNull()
    }

    private fun readSystemTicks(): Long? {
        return runCatching {
            val line = BufferedReader(FileReader("/proc/stat")).use { it.readLine() }
            line.split("\\s+".toRegex())
                .drop(1)
                .take(8)
                .sumOf { it.toLongOrNull() ?: 0L }
        }.getOrNull()
    }

    private fun formatKB(kb: Long): String {
        return when {
            kb < 1024 -> "${kb}KB"
            kb < 1024 * 1024 -> "${kb / 1024}MB"
            else -> "${kb / (1024 * 1024)}GB"
        }
    }

    private fun formatBytes(bytes: Long): String {
        return when {
            bytes < 1024 -> "${bytes}B"
            bytes < 1024 * 1024 -> "${bytes / 1024}KB"
            bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)}MB"
            else -> "${bytes / (1024 * 1024 * 1024)}GB"
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        handler.removeCallbacks(updateRunnable)
    }
}
