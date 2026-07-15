package com.kite.app.foundation.runtime

enum class RuntimePressureConsumerState {
    NO_SOURCE,
    QUIET,
    OBSERVING,
    BUSY,
    BURST,
    DEGRADED
}

enum class RuntimePressureConsumerRecommendation {
    WAIT_FOR_TELEMETRY,
    OBSERVE_ONLY,
    REVIEW_BURST,
    REVIEW_TELEMETRY_HEALTH,
    PREPARE_ADMISSION_INPUT
}

data class RuntimePressureConsumerSnapshot(
    val mode: String = "proot_telemetry_consumer_v0",
    val enforcementMode: String = "observe_only",
    val enforcementEnabled: Boolean = false,
    val generatedAtMs: Long = 0L,
    val state: RuntimePressureConsumerState = RuntimePressureConsumerState.NO_SOURCE,
    val recommendation: RuntimePressureConsumerRecommendation = RuntimePressureConsumerRecommendation.WAIT_FOR_TELEMETRY,
    val telemetryHealthy: Boolean = false,
    val telemetryStatus: String = "not_started",
    val prootSignalLevel: ProotPressureSignalLevel = ProotPressureSignalLevel.QUIET,
    val prootPressureScore: Int = 0,
    val eventsInWindow: Int = 0,
    val forkExecEventsInWindow: Int = 0,
    val liveTraceeCount: Int = 0,
    val knownTraceeCount: Int = 0,
    val runningRootCount: Int = 0,
    val unattributedRootCount: Int = 0,
    val rssPressureLevel: RuntimePressureLevel = RuntimePressureLevel.UNKNOWN,
    val resourceMetricSource: String = "root_snapshot",
    val processResourceCount: Int = 0,
    val processResourceRssKb: Long = 0L,
    val processResourceVmSizeKb: Long = 0L,
    val processResourceCpuTimeTicks: Long = 0L,
    val processResourceIoReadBytes: Long = 0L,
    val processResourceIoWriteBytes: Long = 0L,
    val rootCpuTimeTicks: Long = 0L,
    val rootIoReadBytes: Long = 0L,
    val rootIoWriteBytes: Long = 0L,
    val resourceTrendStatus: String = "collecting_rate_baseline",
    val resourceTrendWindowMs: Long = 0L,
    val rootCpuDeltaTicks: Long = 0L,
    val rootCpuTicksPerSecond: Long = 0L,
    val rootIoReadDeltaBytes: Long = 0L,
    val rootIoWriteDeltaBytes: Long = 0L,
    val rootIoBytesPerSecond: Long = 0L,
    val resourceDominantAxis: String = "baseline_or_unknown",
    val resourceAxisCoverage: String = "proot_tracee+rss_memory",
    val reason: String = "waiting_for_proot_telemetry",
    val observations: List<String> = emptyList()
) {
    fun summary(): String {
        return "mode=$mode state=$state recommendation=$recommendation " +
            "enforcement=$enforcementEnabled proot=$prootSignalLevel/$prootPressureScore " +
            "live=$liveTraceeCount known=$knownTraceeCount rss=$rssPressureLevel " +
            "resourceSource=$resourceMetricSource cpuRate=$rootCpuTicksPerSecond " +
            "ioRate=$rootIoBytesPerSecond dominant=$resourceDominantAxis"
    }

    fun toEnvText(): String {
        val builder = StringBuilder()
        builder.appendLine("pressure_consumer_mode=$mode")
        builder.appendLine("pressure_consumer_enforcement_mode=$enforcementMode")
        builder.appendLine("pressure_consumer_enforcement_enabled=$enforcementEnabled")
        builder.appendLine("pressure_consumer_generated_at=$generatedAtMs")
        builder.appendLine("pressure_consumer_state=${state.name}")
        builder.appendLine("pressure_consumer_recommendation=${recommendation.name}")
        builder.appendLine("pressure_consumer_telemetry_healthy=$telemetryHealthy")
        builder.appendLine("pressure_consumer_telemetry_status=${telemetryStatus.toRuntimeEnvValue()}")
        builder.appendLine("pressure_consumer_proot_signal=${prootSignalLevel.name}")
        builder.appendLine("pressure_consumer_proot_score=$prootPressureScore")
        builder.appendLine("pressure_consumer_events_window=$eventsInWindow")
        builder.appendLine("pressure_consumer_fork_exec_window=$forkExecEventsInWindow")
        builder.appendLine("pressure_consumer_live_tracees=$liveTraceeCount")
        builder.appendLine("pressure_consumer_known_tracees=$knownTraceeCount")
        builder.appendLine("pressure_consumer_running_roots=$runningRootCount")
        builder.appendLine("pressure_consumer_unattributed_roots=$unattributedRootCount")
        builder.appendLine("pressure_consumer_rss_pressure=${rssPressureLevel.name}")
        builder.appendLine("pressure_consumer_resource_metric_source=${resourceMetricSource.toRuntimeEnvValue()}")
        builder.appendLine("pressure_consumer_process_resource_count=$processResourceCount")
        builder.appendLine("pressure_consumer_process_resource_rss_kb=$processResourceRssKb")
        builder.appendLine("pressure_consumer_process_resource_vm_size_kb=$processResourceVmSizeKb")
        builder.appendLine("pressure_consumer_process_resource_cpu_time_ticks=$processResourceCpuTimeTicks")
        builder.appendLine("pressure_consumer_process_resource_io_read_bytes=$processResourceIoReadBytes")
        builder.appendLine("pressure_consumer_process_resource_io_write_bytes=$processResourceIoWriteBytes")
        builder.appendLine("pressure_consumer_root_cpu_time_ticks=$rootCpuTimeTicks")
        builder.appendLine("pressure_consumer_root_io_read_bytes=$rootIoReadBytes")
        builder.appendLine("pressure_consumer_root_io_write_bytes=$rootIoWriteBytes")
        builder.appendLine("pressure_consumer_resource_trend_status=${resourceTrendStatus.toRuntimeEnvValue()}")
        builder.appendLine("pressure_consumer_resource_trend_window_ms=$resourceTrendWindowMs")
        builder.appendLine("pressure_consumer_root_cpu_delta_ticks=$rootCpuDeltaTicks")
        builder.appendLine("pressure_consumer_root_cpu_ticks_per_second=$rootCpuTicksPerSecond")
        builder.appendLine("pressure_consumer_root_io_read_delta_bytes=$rootIoReadDeltaBytes")
        builder.appendLine("pressure_consumer_root_io_write_delta_bytes=$rootIoWriteDeltaBytes")
        builder.appendLine("pressure_consumer_root_io_bytes_per_second=$rootIoBytesPerSecond")
        builder.appendLine("pressure_consumer_resource_dominant_axis=${resourceDominantAxis.toRuntimeEnvValue()}")
        builder.appendLine("pressure_consumer_resource_axis_coverage=${resourceAxisCoverage.toRuntimeEnvValue()}")
        builder.appendLine("pressure_consumer_reason=${reason.toRuntimeEnvValue()}")
        observations.take(5).forEachIndexed { index, observation ->
            builder.appendLine("pressure_consumer_observation_${index + 1}=${observation.toRuntimeEnvValue()}")
        }
        builder.appendLine("pressure_consumer_boundary=observe_only_no_reclaim_no_admission_no_lane_control")
        return builder.toString()
    }
}

object RuntimePressureConsumer {
    private const val CPU_BUSY_TICKS_PER_SECOND = 80L
    private const val IO_BUSY_BYTES_PER_SECOND = 1024L * 1024L
    private const val LIVE_TRACEE_RISK_FLOOR = 16

    @Volatile
    private var previousResourceSample: RuntimeResourceRateSample? = null

    fun evaluate(
        prootTelemetry: ProotTelemetrySnapshot,
        roots: List<RuntimeRootSnapshot>,
        pressure: RuntimePressureSnapshot,
        processResources: ContainerProcessResourceSnapshot = ContainerProcessResourceSnapshot(),
        now: Long = System.currentTimeMillis()
    ): RuntimePressureConsumerSnapshot {
        val telemetryHealthy = prootTelemetry.collectionStatus == "loaded" &&
            prootTelemetry.fileExists &&
            prootTelemetry.counters.parseErrors == 0L
        val runningRootCount = roots.count { it.isRunning }
        val unattributedRootCount = roots.count {
            it.isRunning && it.ownerKind == RuntimeRootOwnerKind.UNATTRIBUTED
        }
        val runningRoots = roots.filter { it.isRunning }
        val rootSnapshotCpuTimeTicks = runningRoots.sumOf { it.cpuTimeTicks }
        val rootSnapshotIoReadBytes = runningRoots.sumOf { it.ioReadBytes }
        val rootSnapshotIoWriteBytes = runningRoots.sumOf { it.ioWriteBytes }
        val useProcessResourceSnapshot = processResources.hasAnyCounter
        val resourceMetricSource = if (useProcessResourceSnapshot) {
            processResources.source
        } else {
            "root_snapshot"
        }
        val rootCpuTimeTicks = if (useProcessResourceSnapshot) {
            processResources.cpuTimeTicks
        } else {
            rootSnapshotCpuTimeTicks
        }
        val rootIoReadBytes = if (useProcessResourceSnapshot) {
            processResources.ioReadBytes
        } else {
            rootSnapshotIoReadBytes
        }
        val rootIoWriteBytes = if (useProcessResourceSnapshot) {
            processResources.ioWriteBytes
        } else {
            rootSnapshotIoWriteBytes
        }
        val resourceRate = calculateResourceRate(
            now = now,
            rootCpuTimeTicks = rootCpuTimeTicks,
            rootIoReadBytes = rootIoReadBytes,
            rootIoWriteBytes = rootIoWriteBytes
        )
        val dominantAxis = resolveDominantAxis(
            prootTelemetry = prootTelemetry,
            pressure = pressure,
            resourceRate = resourceRate
        )
        val observations = buildObservations(
            prootTelemetry = prootTelemetry,
            pressure = pressure,
            runningRootCount = runningRootCount,
            unattributedRootCount = unattributedRootCount,
            resourceMetricSource = resourceMetricSource,
            processResources = processResources,
            rootCpuTimeTicks = rootCpuTimeTicks,
            rootIoReadBytes = rootIoReadBytes,
            rootIoWriteBytes = rootIoWriteBytes,
            resourceRate = resourceRate,
            dominantAxis = dominantAxis
        )

        val stateAndRecommendation = resolveStateAndRecommendation(
            prootTelemetry = prootTelemetry,
            telemetryHealthy = telemetryHealthy
        )

        return RuntimePressureConsumerSnapshot(
            generatedAtMs = now,
            state = stateAndRecommendation.first,
            recommendation = stateAndRecommendation.second,
            telemetryHealthy = telemetryHealthy,
            telemetryStatus = prootTelemetry.collectionStatus,
            prootSignalLevel = prootTelemetry.pressureWindow.signalLevel,
            prootPressureScore = prootTelemetry.pressureWindow.pressureScore,
            eventsInWindow = prootTelemetry.pressureWindow.eventsInWindow,
            forkExecEventsInWindow = prootTelemetry.pressureWindow.forkExecEventsInWindow,
            liveTraceeCount = prootTelemetry.liveTraceeCount,
            knownTraceeCount = prootTelemetry.knownTraceeCount,
            runningRootCount = runningRootCount,
            unattributedRootCount = unattributedRootCount,
            rssPressureLevel = pressure.level,
            resourceMetricSource = resourceMetricSource,
            processResourceCount = processResources.processCount,
            processResourceRssKb = processResources.rssKb,
            processResourceVmSizeKb = processResources.vmSizeKb,
            processResourceCpuTimeTicks = processResources.cpuTimeTicks,
            processResourceIoReadBytes = processResources.ioReadBytes,
            processResourceIoWriteBytes = processResources.ioWriteBytes,
            rootCpuTimeTicks = rootCpuTimeTicks,
            rootIoReadBytes = rootIoReadBytes,
            rootIoWriteBytes = rootIoWriteBytes,
            resourceTrendStatus = resourceRate.status,
            resourceTrendWindowMs = resourceRate.windowMs,
            rootCpuDeltaTicks = resourceRate.cpuDeltaTicks,
            rootCpuTicksPerSecond = resourceRate.cpuTicksPerSecond,
            rootIoReadDeltaBytes = resourceRate.ioReadDeltaBytes,
            rootIoWriteDeltaBytes = resourceRate.ioWriteDeltaBytes,
            rootIoBytesPerSecond = resourceRate.ioBytesPerSecond,
            resourceDominantAxis = dominantAxis,
            resourceAxisCoverage = buildResourceAxisCoverage(
                resourceMetricSource = resourceMetricSource,
                rootCpuTimeTicks = rootCpuTimeTicks,
                rootIoReadBytes = rootIoReadBytes,
                rootIoWriteBytes = rootIoWriteBytes
            ),
            reason = buildReason(prootTelemetry, telemetryHealthy),
            observations = observations
        )
    }

    private fun resolveStateAndRecommendation(
        prootTelemetry: ProotTelemetrySnapshot,
        telemetryHealthy: Boolean
    ): Pair<RuntimePressureConsumerState, RuntimePressureConsumerRecommendation> {
        if (!telemetryHealthy) {
            return if (prootTelemetry.collectionStatus == "loaded" && prootTelemetry.fileExists) {
                RuntimePressureConsumerState.DEGRADED to RuntimePressureConsumerRecommendation.REVIEW_TELEMETRY_HEALTH
            } else {
                RuntimePressureConsumerState.NO_SOURCE to RuntimePressureConsumerRecommendation.WAIT_FOR_TELEMETRY
            }
        }
        if (prootTelemetry.counters.totalEvents <= 0L) {
            return RuntimePressureConsumerState.NO_SOURCE to RuntimePressureConsumerRecommendation.WAIT_FOR_TELEMETRY
        }
        return when (prootTelemetry.pressureWindow.signalLevel) {
            ProotPressureSignalLevel.QUIET ->
                RuntimePressureConsumerState.QUIET to RuntimePressureConsumerRecommendation.OBSERVE_ONLY
            ProotPressureSignalLevel.NORMAL ->
                RuntimePressureConsumerState.OBSERVING to RuntimePressureConsumerRecommendation.OBSERVE_ONLY
            ProotPressureSignalLevel.BUSY ->
                RuntimePressureConsumerState.BUSY to RuntimePressureConsumerRecommendation.PREPARE_ADMISSION_INPUT
            ProotPressureSignalLevel.BURST ->
                RuntimePressureConsumerState.BURST to RuntimePressureConsumerRecommendation.REVIEW_BURST
        }
    }

    private fun buildReason(
        prootTelemetry: ProotTelemetrySnapshot,
        telemetryHealthy: Boolean
    ): String {
        if (!telemetryHealthy) {
            return "telemetry_unhealthy_or_missing"
        }
        if (prootTelemetry.counters.totalEvents <= 0L) {
            return "telemetry_loaded_waiting_for_first_event"
        }
        return when (prootTelemetry.pressureWindow.signalLevel) {
            ProotPressureSignalLevel.QUIET -> "proot_event_window_quiet"
            ProotPressureSignalLevel.NORMAL -> "proot_event_window_observing"
            ProotPressureSignalLevel.BUSY -> "proot_event_window_busy_prepare_policy_input"
            ProotPressureSignalLevel.BURST -> "proot_event_window_burst_review_lane_and_admission_inputs"
        }
    }

    private fun buildObservations(
        prootTelemetry: ProotTelemetrySnapshot,
        pressure: RuntimePressureSnapshot,
        runningRootCount: Int,
        unattributedRootCount: Int,
        resourceMetricSource: String,
        processResources: ContainerProcessResourceSnapshot,
        rootCpuTimeTicks: Long,
        rootIoReadBytes: Long,
        rootIoWriteBytes: Long,
        resourceRate: RuntimeResourceRate,
        dominantAxis: String
    ): List<String> {
        val observations = mutableListOf<String>()
        observations += "events_total=${prootTelemetry.counters.totalEvents}"
        observations += "events_window=${prootTelemetry.pressureWindow.eventsInWindow}"
        observations += "fork_exec_window=${prootTelemetry.pressureWindow.forkExecEventsInWindow}"
        observations += "tracees_live=${prootTelemetry.liveTraceeCount}"
        observations += "tracees_known=${prootTelemetry.knownTraceeCount}"
        observations += "rss_pressure=${pressure.level.name}"
        observations += "resource_metric_source=$resourceMetricSource"
        observations += "process_resource_count=${processResources.processCount}"
        observations += "process_resource_rss_kb=${processResources.rssKb}"
        observations += "root_cpu_time_ticks=$rootCpuTimeTicks"
        observations += "root_io_read_bytes=$rootIoReadBytes"
        observations += "root_io_write_bytes=$rootIoWriteBytes"
        observations += "root_cpu_ticks_per_second=${resourceRate.cpuTicksPerSecond}"
        observations += "root_io_bytes_per_second=${resourceRate.ioBytesPerSecond}"
        observations += "resource_dominant_axis=$dominantAxis"
        observations += "running_roots=$runningRootCount"
        observations += "unattributed_roots=$unattributedRootCount"
        return observations
    }

    @Synchronized
    private fun calculateResourceRate(
        now: Long,
        rootCpuTimeTicks: Long,
        rootIoReadBytes: Long,
        rootIoWriteBytes: Long
    ): RuntimeResourceRate {
        val current = RuntimeResourceRateSample(
            atMs = now,
            cpuTimeTicks = rootCpuTimeTicks,
            ioReadBytes = rootIoReadBytes,
            ioWriteBytes = rootIoWriteBytes
        )
        val previous = previousResourceSample
        previousResourceSample = current
        if (previous == null) {
            return RuntimeResourceRate(status = "collecting_rate_baseline")
        }
        val windowMs = now - previous.atMs
        val countersReset = windowMs <= 0L ||
            rootCpuTimeTicks < previous.cpuTimeTicks ||
            rootIoReadBytes < previous.ioReadBytes ||
            rootIoWriteBytes < previous.ioWriteBytes
        if (countersReset) {
            return RuntimeResourceRate(status = "counter_reset_collecting_new_baseline")
        }
        val cpuDelta = rootCpuTimeTicks - previous.cpuTimeTicks
        val ioReadDelta = rootIoReadBytes - previous.ioReadBytes
        val ioWriteDelta = rootIoWriteBytes - previous.ioWriteBytes
        val ioDelta = ioReadDelta + ioWriteDelta
        return RuntimeResourceRate(
            status = "rate_window_valid",
            windowMs = windowMs,
            cpuDeltaTicks = cpuDelta,
            cpuTicksPerSecond = perSecond(cpuDelta, windowMs),
            ioReadDeltaBytes = ioReadDelta,
            ioWriteDeltaBytes = ioWriteDelta,
            ioBytesPerSecond = perSecond(ioDelta, windowMs)
        )
    }

    private fun resolveDominantAxis(
        prootTelemetry: ProotTelemetrySnapshot,
        pressure: RuntimePressureSnapshot,
        resourceRate: RuntimeResourceRate
    ): String {
        if (pressure.level == RuntimePressureLevel.HIGH ||
            pressure.level == RuntimePressureLevel.CRITICAL
        ) {
            return "memory_pressure"
        }
        if (resourceRate.cpuTicksPerSecond >= CPU_BUSY_TICKS_PER_SECOND) {
            return "cpu_rate_pressure"
        }
        if (resourceRate.ioBytesPerSecond >= IO_BUSY_BYTES_PER_SECOND) {
            return "io_rate_pressure"
        }
        if (prootTelemetry.liveTraceeCount >= LIVE_TRACEE_RISK_FLOOR) {
            return "live_tracee_accumulation"
        }
        if (prootTelemetry.pressureWindow.forkExecEventsInWindow > 0) {
            return "fork_exec_churn"
        }
        return "baseline_or_unknown"
    }

    private fun perSecond(delta: Long, windowMs: Long): Long {
        if (delta <= 0L || windowMs <= 0L) return 0L
        return (delta * 1000L) / windowMs
    }

    private fun buildResourceAxisCoverage(
        resourceMetricSource: String,
        rootCpuTimeTicks: Long,
        rootIoReadBytes: Long,
        rootIoWriteBytes: Long
    ): String {
        val axes = mutableListOf("proot_tracee", "rss_memory", resourceMetricSource)
        if (rootCpuTimeTicks > 0L) axes += "cumulative_cpu_ticks"
        if (rootIoReadBytes > 0L || rootIoWriteBytes > 0L) axes += "cumulative_io_bytes"
        return axes
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString("+")
    }
}

private data class RuntimeResourceRateSample(
    val atMs: Long,
    val cpuTimeTicks: Long,
    val ioReadBytes: Long,
    val ioWriteBytes: Long
)

private data class RuntimeResourceRate(
    val status: String,
    val windowMs: Long = 0L,
    val cpuDeltaTicks: Long = 0L,
    val cpuTicksPerSecond: Long = 0L,
    val ioReadDeltaBytes: Long = 0L,
    val ioWriteDeltaBytes: Long = 0L,
    val ioBytesPerSecond: Long = 0L
)

private fun String?.toRuntimeEnvValue(): String {
    if (this.isNullOrBlank()) return "none"
    return trim()
        .replace(Regex("[\\r\\n\\t ]+"), "_")
        .replace(Regex("[^A-Za-z0-9._:@/-]"), "_")
        .take(160)
}
