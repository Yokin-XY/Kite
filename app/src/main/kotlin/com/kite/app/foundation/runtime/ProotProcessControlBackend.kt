package com.kite.app.foundation.runtime

import android.content.Context
import com.kite.app.foundation.jni.KFJni

/**
 * 控制层使用的进程目标。父 PID 只用于按“子进程优先”排序，不参与身份判断。
 */
data class ProotProcessControlTarget(
    val ref: ProotProcessRef,
    val parentHostPid: Int? = null,
    val processGroupId: Int? = null,
)

enum class ProotControlSignal(val number: Int) {
    TERM(15),
    KILL(9),
}

internal fun interface ProotProcessIdentityVerifier {
    fun verify(ref: ProotProcessRef): ProotProcessVerification
}

internal fun interface ProotProcessSignalSender {
    fun send(hostPid: Int, signal: Int): Boolean
}

data class ProotSignalAttempt(
    val target: ProotProcessControlTarget,
    val verification: ProotProcessVerification,
    val signal: ProotControlSignal,
    val sent: Boolean,
    val reason: String,
)

data class ProotSignalBatchResult(
    val attempts: List<ProotSignalAttempt>,
) {
    val sentHostPids: List<Int>
        get() = attempts.filter(ProotSignalAttempt::sent).map { it.target.ref.hostPid }

    val refusedHostPids: List<Int>
        get() = attempts.filterNot(ProotSignalAttempt::sent).map { it.target.ref.hostPid }
}

/**
 * PRoot 的低成本定向控制后端。
 *
 * 它不枚举 `/proc`，也不按名称搜索进程；每次发信号前只核验目标 PID 的
 * `starttime`，确认仍是同一生命周期后才调用系统 kill。进程树按子节点优先处理，
 * 避免父进程先退出后留下仍在工作的子进程。
 */
internal class ProotProcessControlBackend(
    private val verifier: ProotProcessIdentityVerifier,
    private val signalSender: ProotProcessSignalSender,
) {
    fun verify(targets: Collection<ProotProcessControlTarget>): List<ProotProcessVerification> =
        targets
            .distinctBy { it.ref.lifecycleId }
            .map { verifier.verify(it.ref) }

    fun signal(
        targets: Collection<ProotProcessControlTarget>,
        signal: ProotControlSignal,
    ): ProotSignalBatchResult {
        val ordered = childrenFirst(targets)
        return ProotSignalBatchResult(
            attempts = ordered.map { target ->
                val verification = verifier.verify(target.ref)
                val maySignal = verification.status == ProotProcessVerificationStatus.MATCHED_ACTIVE
                val sent = maySignal && signalSender.send(target.ref.hostPid, signal.number)
                ProotSignalAttempt(
                    target = target,
                    verification = verification,
                    signal = signal,
                    sent = sent,
                    reason = when {
                        !maySignal -> "identity_${verification.status.name.lowercase()}"
                        sent -> "signal_sent"
                        else -> "signal_rejected"
                    },
                )
            },
        )
    }

    private fun childrenFirst(
        targets: Collection<ProotProcessControlTarget>,
    ): List<ProotProcessControlTarget> {
        val unique = targets.distinctBy { it.ref.lifecycleId }
        val byPid = unique.associateBy { it.ref.hostPid }
        fun depth(target: ProotProcessControlTarget): Int {
            var current = target
            val seen = mutableSetOf(target.ref.hostPid)
            var depth = 0
            repeat(32) {
                val parentPid = current.parentHostPid?.takeIf { it > 1 } ?: return depth
                if (!seen.add(parentPid)) return depth
                current = byPid[parentPid] ?: return depth
                depth += 1
            }
            return depth
        }
        return unique.sortedWith(
            compareByDescending<ProotProcessControlTarget>(::depth)
                .thenByDescending { it.ref.hostPid },
        )
    }
}

enum class ProotDirectProcessTerminationOutcome {
    CONFIRMED,
    ALREADY_TERMINAL,
    IDENTITY_UNAVAILABLE,
    SIGNAL_FAILED,
    TIMEOUT,
}

data class ProotDirectProcessTerminationResult(
    val target: ProotProcessControlTarget,
    val outcome: ProotDirectProcessTerminationOutcome,
    val sentTerminate: Boolean = false,
    val sentKill: Boolean = false,
    val finalVerification: ProotProcessVerification,
    val reason: String,
) {
    val settled: Boolean
        get() = outcome == ProotDirectProcessTerminationOutcome.CONFIRMED ||
            outcome == ProotDirectProcessTerminationOutcome.ALREADY_TERMINAL
}

/** 单个进程行的确定性停止入口；总等待不超过约 400ms，且只在后台线程调用。 */
object ProotDirectProcessTerminator {
    private const val TERM_GRACE_MS = 240L
    private const val KILL_GRACE_MS = 160L

    fun terminate(
        context: Context,
        target: ProotProcessControlTarget,
    ): ProotDirectProcessTerminationResult {
        val appContext = context.applicationContext
        val verifier = ProotProcessVerifier()
        val backend = ProotProcessControlBackend(
            verifier = ProotProcessIdentityVerifier(verifier::verify),
            signalSender = ProotProcessSignalSender { pid, signal ->
                runCatching { KFJni.sendSignal(pid, signal) }.getOrDefault(false)
            },
        )
        val observations = mutableListOf<ProotProcessVerification>()

        fun verify(): ProotProcessVerification = backend.verify(listOf(target)).single().also(observations::add)
        fun finish(
            outcome: ProotDirectProcessTerminationOutcome,
            final: ProotProcessVerification,
            sentTerminate: Boolean,
            sentKill: Boolean,
            reason: String,
        ): ProotDirectProcessTerminationResult {
            ProotTelemetryStore.applyProcessVerifications(appContext, observations)
            return ProotDirectProcessTerminationResult(
                target = target,
                outcome = outcome,
                sentTerminate = sentTerminate,
                sentKill = sentKill,
                finalVerification = final,
                reason = reason,
            )
        }

        val initial = verify()
        if (initial.isTerminalForControl()) {
            return finish(
                ProotDirectProcessTerminationOutcome.ALREADY_TERMINAL,
                initial,
                sentTerminate = false,
                sentKill = false,
                reason = "target_already_terminal",
            )
        }
        if (initial.status != ProotProcessVerificationStatus.MATCHED_ACTIVE) {
            return finish(
                ProotDirectProcessTerminationOutcome.IDENTITY_UNAVAILABLE,
                initial,
                sentTerminate = false,
                sentKill = false,
                reason = "target_identity_${initial.status.name.lowercase()}",
            )
        }

        val term = backend.signal(listOf(target), ProotControlSignal.TERM)
        observations += term.attempts.map(ProotSignalAttempt::verification)
        val sentTerminate = term.sentHostPids.isNotEmpty()
        if (!sentTerminate) {
            val final = term.attempts.single().verification
            val outcome = if (final.isTerminalForControl()) {
                ProotDirectProcessTerminationOutcome.ALREADY_TERMINAL
            } else {
                ProotDirectProcessTerminationOutcome.SIGNAL_FAILED
            }
            return finish(outcome, final, false, false, term.attempts.single().reason)
        }

        Thread.sleep(TERM_GRACE_MS)
        val afterTerm = verify()
        if (afterTerm.isTerminalForControl()) {
            return finish(
                ProotDirectProcessTerminationOutcome.CONFIRMED,
                afterTerm,
                sentTerminate = true,
                sentKill = false,
                reason = "terminated_after_term",
            )
        }
        if (afterTerm.status != ProotProcessVerificationStatus.MATCHED_ACTIVE) {
            return finish(
                ProotDirectProcessTerminationOutcome.IDENTITY_UNAVAILABLE,
                afterTerm,
                sentTerminate = true,
                sentKill = false,
                reason = "post_term_identity_${afterTerm.status.name.lowercase()}",
            )
        }

        val kill = backend.signal(listOf(target), ProotControlSignal.KILL)
        observations += kill.attempts.map(ProotSignalAttempt::verification)
        val sentKill = kill.sentHostPids.isNotEmpty()
        Thread.sleep(KILL_GRACE_MS)
        val final = verify()
        return finish(
            outcome = when {
                final.isTerminalForControl() -> ProotDirectProcessTerminationOutcome.CONFIRMED
                !sentKill -> ProotDirectProcessTerminationOutcome.SIGNAL_FAILED
                else -> ProotDirectProcessTerminationOutcome.TIMEOUT
            },
            final = final,
            sentTerminate = true,
            sentKill = sentKill,
            reason = when {
                final.isTerminalForControl() -> "terminated_after_kill"
                !sentKill -> "kill_signal_failed"
                else -> "target_still_active_after_kill"
            },
        )
    }
}

private fun ProotProcessVerification.isTerminalForControl(): Boolean =
    terminal || status == ProotProcessVerificationStatus.MATCHED_ZOMBIE

data class ProotDirectProcessTreeTerminationResult(
    val targetLifecycleIds: List<String>,
    val remainingLifecycleIds: List<String>,
    val sentTerminatePids: List<Int>,
    val sentKillPids: List<Int>,
    val reason: String,
) {
    val settled: Boolean
        get() = remainingLifecycleIds.isEmpty()
}

/** 应用分组/父进程树停止：同批 TERM，同批复核，再只对仍存活目标发 KILL。 */
object ProotDirectProcessTreeTerminator {
    private const val TERM_GRACE_MS = 260L
    private const val KILL_GRACE_MS = 170L

    fun terminate(
        context: Context,
        targets: Collection<ProotProcessControlTarget>,
    ): ProotDirectProcessTreeTerminationResult {
        val appContext = context.applicationContext
        val unique = targets.distinctBy { it.ref.lifecycleId }
        if (unique.isEmpty()) {
            return ProotDirectProcessTreeTerminationResult(
                targetLifecycleIds = emptyList(),
                remainingLifecycleIds = emptyList(),
                sentTerminatePids = emptyList(),
                sentKillPids = emptyList(),
                reason = "no_targets",
            )
        }
        if (unique.any { !it.ref.hasStrongIdentity }) {
            return ProotDirectProcessTreeTerminationResult(
                targetLifecycleIds = unique.map { it.ref.lifecycleId },
                remainingLifecycleIds = unique.map { it.ref.lifecycleId },
                sentTerminatePids = emptyList(),
                sentKillPids = emptyList(),
                reason = "tree_identity_incomplete",
            )
        }

        val verifier = ProotProcessVerifier()
        val backend = ProotProcessControlBackend(
            verifier = ProotProcessIdentityVerifier(verifier::verify),
            signalSender = ProotProcessSignalSender { pid, signal ->
                runCatching { KFJni.sendSignal(pid, signal) }.getOrDefault(false)
            },
        )
        val term = backend.signal(unique, ProotControlSignal.TERM)
        ProotTelemetryStore.applyProcessVerifications(
            appContext,
            term.attempts.map(ProotSignalAttempt::verification),
        )
        Thread.sleep(TERM_GRACE_MS)

        val afterTerm = backend.verify(unique)
        ProotTelemetryStore.applyProcessVerifications(appContext, afterTerm)
        val afterTermById = afterTerm.associateBy { it.ref.lifecycleId }
        val remainingAfterTerm = unique.filter { target ->
            afterTermById[target.ref.lifecycleId]?.status == ProotProcessVerificationStatus.MATCHED_ACTIVE
        }
        if (remainingAfterTerm.isEmpty()) {
            return ProotDirectProcessTreeTerminationResult(
                targetLifecycleIds = unique.map { it.ref.lifecycleId },
                remainingLifecycleIds = emptyList(),
                sentTerminatePids = term.sentHostPids,
                sentKillPids = emptyList(),
                reason = "tree_terminated_after_term",
            )
        }

        val kill = backend.signal(remainingAfterTerm, ProotControlSignal.KILL)
        ProotTelemetryStore.applyProcessVerifications(
            appContext,
            kill.attempts.map(ProotSignalAttempt::verification),
        )
        Thread.sleep(KILL_GRACE_MS)
        val final = backend.verify(remainingAfterTerm)
        ProotTelemetryStore.applyProcessVerifications(appContext, final)
        val remaining = final
            .filter { it.status == ProotProcessVerificationStatus.MATCHED_ACTIVE }
            .map { it.ref.lifecycleId }
        return ProotDirectProcessTreeTerminationResult(
            targetLifecycleIds = unique.map { it.ref.lifecycleId },
            remainingLifecycleIds = remaining,
            sentTerminatePids = term.sentHostPids,
            sentKillPids = kill.sentHostPids,
            reason = if (remaining.isEmpty()) "tree_terminated_after_kill" else "tree_still_active_after_kill",
        )
    }
}
