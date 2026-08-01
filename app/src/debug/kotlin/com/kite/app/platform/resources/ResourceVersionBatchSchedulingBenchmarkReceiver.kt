package com.kite.app.platform.resources

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import com.kite.app.application.resources.ResourceVersionBatchLane
import com.kite.app.application.resources.ResourceVersionBatchScheduler
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Debug-only 固定批量调度矩阵；ADB 只能触发，不能覆盖请求、延迟、轮数、槽位或阈值。 */
class ResourceVersionBatchSchedulingBenchmarkReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION) return
        runCatching {
            context.startService(Intent(context, ResourceVersionBatchSchedulingBenchmarkService::class.java))
        }.onFailure { error ->
            Log.e(LOG_TAG, "status=rejected requiresForeground=true reason=${safe(error.message)}")
        }
    }

    internal companion object {
        const val ACTION = "com.kite.app.debug.RESOURCE_VERSION_BATCH_SCHEDULING_BENCHMARK"
        const val LOG_TAG = "KiteVersionBatch"

        fun safe(value: String?): String = value.orEmpty().take(180).map { character ->
            if (character.isLetterOrDigit() || character in "-_.:=/%") character else '_'
        }.joinToString("")
    }
}

class ResourceVersionBatchSchedulingBenchmarkService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!running.compareAndSet(false, true)) return START_NOT_STICKY
        scope.launch {
            try {
                ResourceVersionBatchSchedulingBenchmark.run().forEach { report ->
                    Log.i(ResourceVersionBatchSchedulingBenchmarkReceiver.LOG_TAG, report)
                }
            } catch (error: Throwable) {
                Log.e(
                    ResourceVersionBatchSchedulingBenchmarkReceiver.LOG_TAG,
                    "status=failed reason=${ResourceVersionBatchSchedulingBenchmarkReceiver.safe(error.message)}",
                    error,
                )
            } finally {
                running.set(false)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private companion object {
        val running = AtomicBoolean(false)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}

internal typealias DebugResourceVersionBatchLane = ResourceVersionBatchLane

/** RF1720 的固定入口；RF1730 后只委托生产调度器，避免保留平行实现。 */
internal object ResourceVersionBatchSchedulingCandidate {
    const val STRUCTURED_NATIVE_REMOTE_LIMIT = ResourceVersionBatchScheduler.STRUCTURED_NATIVE_REMOTE_LIMIT
    const val PROOT_COMPATIBILITY_LIMIT = ResourceVersionBatchScheduler.PROOT_COMPATIBILITY_LIMIT

    suspend fun <T, R> executeOrdered(
        requests: List<T>,
        laneOf: (T) -> DebugResourceVersionBatchLane,
        execute: suspend (T) -> R,
    ): List<R> = ResourceVersionBatchScheduler.executeOrdered(
        requests = requests,
        laneOf = laneOf,
        execute = execute,
    )
}

private object ResourceVersionBatchSchedulingBenchmark {
    private const val SUITE = "rf1720_resource_version_batch_scheduling"
    private const val ROUNDS = 3
    private const val FIXED_PROBE_DELAY_MS = 180L
    private const val REQUIRED_REDUCTION_PERCENT = 40.0
    private const val MAXIMUM_CANDIDATE_P95_MS = 550L
    private const val CANCELLATION_PROBE_DELAY_MS = 5_000L

    private data class FixedRequest(
        val id: String,
        val lane: DebugResourceVersionBatchLane,
        val outcome: String,
    )

    private data class TimedExecution(
        val outcomes: List<String>,
        val durationMs: Long,
        val maxStructuredNativeRemote: Int,
        val maxProotCompatibility: Int,
        val firstProbeSawAllChecking: Boolean,
    )

    private data class RoundExecution(
        val baseline: TimedExecution,
        val candidate: TimedExecution,
    )

    suspend fun run(): List<String> {
        val requests = fixedRequests()
        check(requests.size == 5) { "fixed_request_count_invalid" }
        val rounds = List(ROUNDS) { runRound(requests) }
        val orderOk = rounds.all { round -> round.baseline.outcomes == round.candidate.outcomes }
        val differences = rounds.sumOf { round ->
            round.baseline.outcomes.zip(round.candidate.outcomes).count { (left, right) -> left != right }
        }
        val firstProbeAfterChecking = rounds.all { round -> round.candidate.firstProbeSawAllChecking }
        val structuredLimitOk = rounds.all { round ->
            round.candidate.maxStructuredNativeRemote in 1..ResourceVersionBatchSchedulingCandidate.STRUCTURED_NATIVE_REMOTE_LIMIT
        }
        val compatibilityLimitOk = rounds.all { round ->
            round.candidate.maxProotCompatibility == ResourceVersionBatchSchedulingCandidate.PROOT_COMPATIBILITY_LIMIT
        }
        val failureIsolated = rounds.all { round ->
            round.candidate.outcomes == requests.map(FixedRequest::outcome) &&
                round.candidate.outcomes.count { outcome -> outcome.startsWith("failed:") } == 1
        }
        val cancellationClean = cancellationLeavesNoWork(requests)
        val reductions = rounds.map { round ->
            reductionPercent(round.baseline.durationMs, round.candidate.durationMs)
        }
        val candidateDurations = rounds.map { round -> round.candidate.durationMs }
        val candidateP95Ms = percentile(candidateDurations, 0.95)
        val correctnessGate = differences == 0 && orderOk && firstProbeAfterChecking &&
            structuredLimitOk && compatibilityLimitOk && failureIsolated && cancellationClean
        val performanceGate = reductions.all { reduction -> reduction >= REQUIRED_REDUCTION_PERCENT } &&
            candidateP95Ms <= MAXIMUM_CANDIDATE_P95_MS

        return buildList {
            rounds.forEachIndexed { index, round ->
                add(
                    "status=metric suite=$SUITE round=${index + 1} " +
                        "sequentialMs=${round.baseline.durationMs} candidateMs=${round.candidate.durationMs} " +
                        "reductionPercent=${formatPercent(reductions[index])} " +
                        "maxStructuredNativeRemote=${round.candidate.maxStructuredNativeRemote} " +
                        "maxProotCompatibility=${round.candidate.maxProotCompatibility}",
                )
            }
            add(
                "status=contract suite=$SUITE requests=${requests.size} rounds=$ROUNDS differences=$differences " +
                    "orderOk=$orderOk firstProbeAfterChecking=$firstProbeAfterChecking " +
                    "structuredLimitOk=$structuredLimitOk compatibilityLimitOk=$compatibilityLimitOk " +
                    "failureIsolated=$failureIsolated cancellationClean=$cancellationClean " +
                    "correctnessGate=$correctnessGate adbOverrides=false",
            )
            add(
                "status=complete suite=$SUITE sequentialP50Ms=${percentile(rounds.map { it.baseline.durationMs }, 0.50)} " +
                    "candidateP50Ms=${percentile(candidateDurations, 0.50)} candidateP95Ms=$candidateP95Ms " +
                    "minimumReductionPercent=${formatPercent(reductions.minOrNull() ?: 0.0)} " +
                    "performanceGate=$performanceGate correctnessGate=$correctnessGate providerSource=production_scheduler",
            )
        }
    }

    private suspend fun runRound(requests: List<FixedRequest>): RoundExecution = RoundExecution(
        baseline = executeSequential(requests),
        candidate = executeCandidate(requests),
    )

    private suspend fun executeSequential(requests: List<FixedRequest>): TimedExecution {
        val started = SystemClock.elapsedRealtimeNanos()
        val outcomes = requests.map { request ->
            delay(FIXED_PROBE_DELAY_MS)
            request.outcome
        }
        return TimedExecution(
            outcomes = outcomes,
            durationMs = elapsedMs(started),
            maxStructuredNativeRemote = 1,
            maxProotCompatibility = 1,
            firstProbeSawAllChecking = true,
        )
    }

    private suspend fun executeCandidate(requests: List<FixedRequest>): TimedExecution {
        val checking = requests.map(FixedRequest::id).toSet()
        val probe = ActiveProbe()
        val firstProbeSawAllChecking = AtomicBoolean(true)
        val started = SystemClock.elapsedRealtimeNanos()
        val outcomes = ResourceVersionBatchSchedulingCandidate.executeOrdered(
            requests = requests,
            laneOf = FixedRequest::lane,
        ) { request ->
            if (checking.size != requests.size) firstProbeSawAllChecking.set(false)
            probe.enter(request.lane)
            try {
                delay(FIXED_PROBE_DELAY_MS)
                request.outcome
            } finally {
                probe.exit(request.lane)
            }
        }
        return TimedExecution(
            outcomes = outcomes,
            durationMs = elapsedMs(started),
            maxStructuredNativeRemote = probe.maxStructuredNativeRemote,
            maxProotCompatibility = probe.maxProotCompatibility,
            firstProbeSawAllChecking = firstProbeSawAllChecking.get(),
        )
    }

    private suspend fun cancellationLeavesNoWork(requests: List<FixedRequest>): Boolean {
        val probe = ActiveProbe()
        val started = CompletableDeferred<Unit>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val job = scope.launch {
            ResourceVersionBatchSchedulingCandidate.executeOrdered(
                requests = requests,
                laneOf = FixedRequest::lane,
            ) { request ->
                probe.enter(request.lane)
                started.complete(Unit)
                try {
                    delay(CANCELLATION_PROBE_DELAY_MS)
                    request.outcome
                } finally {
                    probe.exit(request.lane)
                }
            }
        }
        started.await()
        delay(30L)
        job.cancelAndJoin()
        scope.cancel()
        return probe.started > 0 && probe.active == 0
    }

    private class ActiveProbe {
        private val lock = Any()
        private var activeStructuredNativeRemote = 0
        private var activeProotCompatibility = 0
        var maxStructuredNativeRemote: Int = 0
            private set
        var maxProotCompatibility: Int = 0
            private set
        var started: Int = 0
            private set

        val active: Int
            get() = synchronized(lock) { activeStructuredNativeRemote + activeProotCompatibility }

        fun enter(lane: DebugResourceVersionBatchLane) = synchronized(lock) {
            started += 1
            when (lane) {
                DebugResourceVersionBatchLane.STRUCTURED_NATIVE_REMOTE -> {
                    activeStructuredNativeRemote += 1
                    maxStructuredNativeRemote = maxOf(maxStructuredNativeRemote, activeStructuredNativeRemote)
                }
                DebugResourceVersionBatchLane.PROOT_COMPATIBILITY -> {
                    activeProotCompatibility += 1
                    maxProotCompatibility = maxOf(maxProotCompatibility, activeProotCompatibility)
                }
            }
        }

        fun exit(lane: DebugResourceVersionBatchLane) = synchronized(lock) {
            when (lane) {
                DebugResourceVersionBatchLane.STRUCTURED_NATIVE_REMOTE -> activeStructuredNativeRemote -= 1
                DebugResourceVersionBatchLane.PROOT_COMPATIBILITY -> activeProotCompatibility -= 1
            }
        }
    }

    private fun fixedRequests(): List<FixedRequest> = listOf(
        FixedRequest("native-current", DebugResourceVersionBatchLane.STRUCTURED_NATIVE_REMOTE, "current:1.0.0"),
        FixedRequest("compat-available", DebugResourceVersionBatchLane.PROOT_COMPATIBILITY, "available:1.0.0:1.1.0"),
        FixedRequest("native-failed", DebugResourceVersionBatchLane.STRUCTURED_NATIVE_REMOTE, "failed:network"),
        FixedRequest("compat-unsupported", DebugResourceVersionBatchLane.PROOT_COMPATIBILITY, "unsupported:probe"),
        FixedRequest("native-ahead", DebugResourceVersionBatchLane.STRUCTURED_NATIVE_REMOTE, "current:2.0.0:ahead"),
    )

    private fun elapsedMs(startedNs: Long): Long =
        (SystemClock.elapsedRealtimeNanos() - startedNs) / 1_000_000L

    private fun reductionPercent(baseline: Long, candidate: Long): Double =
        if (baseline <= 0L) 0.0 else (baseline - candidate).toDouble() * 100.0 / baseline.toDouble()

    private fun percentile(values: List<Long>, ratio: Double): Long {
        val sorted = values.sorted()
        val index = kotlin.math.ceil(sorted.size * ratio).toInt().coerceIn(1, sorted.size) - 1
        return sorted[index]
    }

    private fun formatPercent(value: Double): String = "%.1f".format(Locale.US, value)
}
