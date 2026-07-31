package com.kite.app.foundation.runtime

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.kite.app.foundation.service.BackgroundRuntimeProcessIdentityMatch
import com.kite.app.foundation.service.BackgroundRuntimeProcessIdentityPolicy
import com.kite.app.foundation.service.BackgroundRuntimeProcessObservation
import com.kite.app.foundation.service.BackgroundRuntimeRecoveryAction
import com.kite.app.foundation.service.BackgroundRuntimeStopAction
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Debug-only 固定 RF950 矩阵；不接收 profile、并发、命令或路径参数。 */
class ManagedProotProductionGateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION) return
        context.startService(Intent(context, ManagedProotProductionGateService::class.java))
    }

    internal companion object {
        const val ACTION = "com.kite.app.debug.MANAGED_PROOT_PRODUCTION_GATE"
        const val LOG_TAG = "[KFShell]ManagedProotGate"
    }
}

class ManagedProotProductionGateService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!running.compareAndSet(false, true)) return START_NOT_STICKY
        scope.launch {
            try {
                // 等待应用启动期 RuntimeHealth 首次接管完成，避免它覆盖固定矩阵的临时档位。
                delay(5_000L)
                val reports = ManagedProotProductionGateMatrix.run(applicationContext)
                Log.i(
                    ManagedProotProductionGateReceiver.LOG_TAG,
                    "status=ok suite=rf950_production_gate cases=${reports.size}",
                )
            } catch (error: Throwable) {
                Log.e(
                    ManagedProotProductionGateReceiver.LOG_TAG,
                    "status=failed reason=${safe(error.message ?: error.javaClass.simpleName)}",
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
        fun safe(value: String): String = value.take(160).map { character ->
            if (character.isLetterOrDigit() || character in "-_.:") character else '_'
        }.joinToString("")
    }
}

private object ManagedProotProductionGateMatrix {
    private data class Held(val ownerId: String, val generation: Long)

    fun run(context: Context): List<String> {
        val baseline = RuntimeHealthStore.snapshot.value
        val held = mutableListOf<Held>()
        check(WarmProotExecutionCoordinator.snapshot().activeCount == 0) {
            "rf950_requires_idle_actual_controller"
        }
        return try {
            buildList {
                add(report(identityCounterexamples()))
                listOf(
                    RuntimeLifecyclePolicyProfileGroup.LOW_POWER to 1,
                    RuntimeLifecyclePolicyProfileGroup.DEFAULT_BALANCED to 2,
                    RuntimeLifecyclePolicyProfileGroup.HIGH_PERFORMANCE to 4,
                ).forEach { (profile, expectedMax) ->
                    applyPolicy(baseline, profile, RuntimePressureLevel.NORMAL)
                    repeat(expectedMax) { index ->
                        held += acquire(context, "profile-${profile.name.lowercase()}-$index", 1L)
                    }
                    val full = WarmProotExecutionCoordinator.tuningSnapshot().unifiedActualCapacity
                    check(full.effectiveGlobalMax == expectedMax)
                    check(full.totalActiveCount == expectedMax)
                    check(full.state == UnifiedProotCapacityState.FULL)
                    val overflow = requireRejected(
                        result = acquireResult(
                            context,
                            "profile-${profile.name.lowercase()}-overflow",
                            1L,
                            waitTimeoutMs = 80L,
                        ),
                        suffix = "profile-${profile.name.lowercase()}-overflow",
                        generation = 1L,
                        case = "profile_${profile.name.lowercase()}",
                    )
                    releaseAll(held)
                    add(report(
                        "status=ok case=profile_${profile.name.lowercase()} " +
                            "max=$expectedMax active=${full.totalActiveCount} " +
                            "overflow=${overflow.reason}"
                    ))
                }

                applyPolicy(
                    baseline,
                    RuntimeLifecyclePolicyProfileGroup.DEFAULT_BALANCED,
                    RuntimePressureLevel.NORMAL,
                )
                held += acquire(context, "mixed-long", 1L)
                val executor = Executors.newSingleThreadExecutor()
                try {
                    val shortJobId = "rf950-short-mixed-short"
                    val short = executor.submit(Callable {
                        WarmProotExecutionCoordinator.executeBlocking(
                            context = context,
                            admissionRequest = shortRequest("mixed-short", 2_000L),
                            jobRequest = WarmProotJobRequest(
                                jobId = shortJobId,
                                argv = listOf("/bin/sleep", "2"),
                                timeoutMs = 5_000L,
                                maxOutputBytesPerStream = 1_024,
                            ),
                        )
                    })
                    val mixed = waitForCapacity(
                        timeoutMs = 5_000L,
                        predicate = { snapshot ->
                            snapshot.shortActiveCount == 1 && snapshot.longActiveCount == 1
                        },
                    )
                    val overflow = requireRejected(
                        acquireResult(context, "mixed-overflow", 1L, 80L),
                        "mixed-overflow",
                        1L,
                        "short_long_competition",
                    )
                    check(short.get(6, TimeUnit.SECONDS).succeeded)
                    add(report(
                        "status=ok case=short_long_competition short=${mixed.shortActiveCount} " +
                            "long=${mixed.longActiveCount} total=${mixed.totalActiveCount} " +
                            "overflow=${overflow.reason}"
                    ))
                } finally {
                    executor.shutdownNow()
                    releaseAll(held)
                }

                applyPolicy(
                    baseline,
                    RuntimeLifecyclePolicyProfileGroup.HIGH_PERFORMANCE,
                    RuntimePressureLevel.NORMAL,
                )
                held += acquire(context, "pressure-one", 1L)
                held += acquire(context, "pressure-two", 1L)
                applyPolicy(
                    baseline,
                    RuntimeLifecyclePolicyProfileGroup.HIGH_PERFORMANCE,
                    RuntimePressureLevel.HIGH,
                )
                val pressured = WarmProotExecutionCoordinator.tuningSnapshot().unifiedActualCapacity
                check(pressured.state == UnifiedProotCapacityState.OVERCOMMITTED)
                check(pressured.effectiveGlobalMax == 1)
                check(pressured.totalActiveCount == 2)
                val rejected = requireRejected(
                    acquireResult(context, "pressure-new", 1L, 80L),
                    "pressure-new",
                    1L,
                    "pressure_contraction",
                )
                add(report(
                    "status=ok case=pressure_contraction effective=${pressured.effectiveGlobalMax} " +
                        "active=${pressured.totalActiveCount} state=${pressured.state.name} " +
                        "new=${rejected.reason}"
                ))
            }
        } finally {
            releaseAll(held)
            WarmProotExecutionCoordinator.updateFrom(baseline)
        }
    }

    private fun applyPolicy(
        baseline: RuntimeHealthSnapshot,
        profile: RuntimeLifecyclePolicyProfileGroup,
        pressure: RuntimePressureLevel,
    ) {
        val lanes = RuntimeWorkloadPolicy.defaultLanes().map { lane ->
            if (lane.lane == RuntimeLaneKind.SERVICE || lane.lane == RuntimeLaneKind.INTERACTIVE) {
                lane.copy(maxConcurrency = 4, backgroundMaxConcurrency = 4, serial = false)
            } else {
                lane
            }
        }
        WarmProotExecutionCoordinator.updateFrom(
            baseline.copy(
                pressure = baseline.pressure.copy(level = pressure),
                lifecyclePolicyProfileSurface = baseline.lifecyclePolicyProfileSurface.copy(
                    activeProfileGroup = profile,
                    activeLanes = lanes,
                ),
                backgroundDecay = baseline.backgroundDecay.copy(
                    lifecycleState = RuntimeAppVisibilityState.FOREGROUND,
                ),
            )
        )
    }

    private fun identityCounterexamples(): String {
        val persisted = HostProcessIdentityObservation(
            bootId = "11111111-1111-4111-8111-111111111111",
            hostPid = 950,
            processStartTicks = 100L,
        )
        val pidReused = BackgroundRuntimeProcessIdentityPolicy.decide(
            persistedPid = persisted.hostPid,
            persistedIdentity = persisted,
            observation = BackgroundRuntimeProcessObservation.identityReady(
                persisted.copy(processStartTicks = 101L)
            ),
        )
        val bootChanged = BackgroundRuntimeProcessIdentityPolicy.decide(
            persistedPid = persisted.hostPid,
            persistedIdentity = persisted,
            observation = BackgroundRuntimeProcessObservation.identityReady(
                persisted.copy(bootId = "22222222-2222-4222-8222-222222222222")
            ),
        )
        check(pidReused.processMatch == BackgroundRuntimeProcessIdentityMatch.PID_REUSED)
        check(bootChanged.processMatch == BackgroundRuntimeProcessIdentityMatch.BOOT_CHANGED)
        listOf(pidReused, bootChanged).forEach { decision ->
            check(decision.recoveryAction == BackgroundRuntimeRecoveryAction.REVIEW_WITHOUT_ATTACH)
            check(decision.stopAction == BackgroundRuntimeStopAction.CONFIRM_ORIGINAL_EXITED)
            check(decision.processStartsRequested == 0)
        }
        return "status=ok case=identity_counterexamples pid_reused=review boot_changed=review starts=0"
    }

    private fun acquire(context: Context, suffix: String, generation: Long): Held {
        val result = acquireResult(context, suffix, generation, waitTimeoutMs = 500L)
        check(result is ManagedProotOwnerAdmissionResult.Granted) {
            "rf950_managed_acquire_failed_$suffix"
        }
        return Held(ownerId(suffix), generation)
    }

    private fun acquireResult(
        context: Context,
        suffix: String,
        generation: Long,
        waitTimeoutMs: Long,
    ) = WarmProotExecutionCoordinator.acquireManagedOwnerBlocking(
        context = context,
        request = managedRequest(suffix, generation, waitTimeoutMs),
        generation = generation,
    )

    private fun releaseAll(held: MutableList<Held>) {
        held.toList().asReversed().forEach { item ->
            WarmProotExecutionCoordinator.releaseManagedOwner(item.ownerId, item.generation)
        }
        held.clear()
    }

    private fun waitForCapacity(
        timeoutMs: Long,
        predicate: (ProotUnifiedActualHealthSnapshot) -> Boolean,
    ): ProotUnifiedActualHealthSnapshot {
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs)
        while (System.nanoTime() < deadline) {
            val current = WarmProotExecutionCoordinator.tuningSnapshot().unifiedActualCapacity
            if (predicate(current)) return current
            Thread.sleep(10L)
        }
        val current = WarmProotExecutionCoordinator.tuningSnapshot().unifiedActualCapacity
        error(
            "rf950_capacity_observation_timeout_" +
                "short_${current.shortActiveCount}_long_${current.longActiveCount}_" +
                "queued_${current.totalQueuedCount}_max_${current.effectiveGlobalMax}"
        )
    }

    private fun managedRequest(
        suffix: String,
        generation: Long,
        waitTimeoutMs: Long,
    ) = ProotJobAdmissionRequest(
        jobId = "rf950-managed-$suffix-$generation",
        ownerId = ownerId(suffix),
        lane = RuntimeLaneKind.SERVICE,
        cancellationMode = ProotJobCancellationMode.MANAGED_OWNER,
        resultMode = ProotJobResultMode.DETACHED_BINDING,
        waitTimeoutMs = waitTimeoutMs,
    )

    private fun shortRequest(suffix: String, waitTimeoutMs: Long) = ProotJobAdmissionRequest(
        jobId = "rf950-short-$suffix",
        ownerId = "debug:rf950:short:$suffix",
        lane = RuntimeLaneKind.INTERACTIVE,
        cancellationMode = ProotJobCancellationMode.TIMEOUT_AND_OWNER,
        resultMode = ProotJobResultMode.CAPTURED_STDIO,
        waitTimeoutMs = waitTimeoutMs,
    )

    private fun ownerId(suffix: String) = "debug:rf950:managed:$suffix"

    private fun requireRejected(
        result: ManagedProotOwnerAdmissionResult,
        suffix: String,
        generation: Long,
        case: String,
    ): ManagedProotOwnerAdmissionResult.Rejected {
        if (result is ManagedProotOwnerAdmissionResult.Rejected) return result
        WarmProotExecutionCoordinator.releaseManagedOwner(ownerId(suffix), generation)
        val current = WarmProotExecutionCoordinator.tuningSnapshot().unifiedActualCapacity
        val granted = result as ManagedProotOwnerAdmissionResult.Granted
        error(
            "rf950_overflow_admitted_${case}_existing_${granted.existing}_" +
                "active_${current.totalActiveCount}_max_${current.effectiveGlobalMax}"
        )
    }

    private fun report(value: String): String = value.also { line ->
        Log.i(ManagedProotProductionGateReceiver.LOG_TAG, line)
    }
}
