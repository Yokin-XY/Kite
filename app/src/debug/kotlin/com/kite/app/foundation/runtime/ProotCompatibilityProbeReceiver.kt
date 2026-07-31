package com.kite.app.foundation.runtime

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import com.kite.app.application.runs.RunCommandResult
import com.kite.app.application.runs.RunLifecycleSink
import com.kite.app.application.runs.RunStartRequest
import com.kite.app.foundation.workspace.WorkSurfaceRuntimeBridge
import com.kite.app.recipe.KiteExecution
import com.kite.app.recipe.KiteRecipe
import com.kite.app.recipe.KiteRecipeStep
import com.kite.app.run.CardRunState
import com.kite.app.run.CardRunStatus
import com.kite.app.run.CardRunStore
import com.kite.app.shell.KiteAppGraph
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

/** Debug-only 固定 PRoot 等价探针；不接收外部命令、路径或 View 标识。 */
class ProotCompatibilityProbeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return
        val pending = goAsync()
        thread(name = "KiteProotCompatibilityProbe", isDaemon = true) {
            try {
                Log.i(LOG_TAG, ProotCompatibilityProbe.run(context.applicationContext))
            } catch (error: Throwable) {
                Log.e(LOG_TAG, "status=failed reason=${safe(error.message ?: error.javaClass.simpleName)}", error)
            } finally {
                pending.finish()
            }
        }
    }

    internal companion object {
        const val ACTION = "com.kite.app.debug.PROOT_COMPATIBILITY_PROBE"
        const val LOG_TAG = "[KFShell]ProotCompatibilityProbe"

        fun safe(value: String): String = value.take(240).map { character ->
            if (character.isLetterOrDigit() || character in "-_.:=,") character else '_'
        }.joinToString("")
    }
}

private object ProotCompatibilityProbe {
    private const val TIMEOUT_SECONDS = 30L

    private data class ProbeExecution(
        val durationMs: Long,
        val output: String,
    )

    fun run(context: Context): String {
        val startedAt = SystemClock.elapsedRealtime()
        val shell = executeShell(
            context = context,
            label = "shell",
            payload = "printf '__rf410_shell__\\n'",
            expected = "__rf410_shell__",
        )
        val complex = executeShell(
            context = context,
            label = "complex_compiler",
            payload = """
                set -eu
                rf410_dir=/tmp/kite-rf410-compat-${'$'}${'$'}
                trap 'rm -rf "${'$'}rf410_dir"' EXIT
                mkdir -p "${'$'}rf410_dir"
                elf_magic=${'$'}(od -An -tx1 -N4 /bin/bash | tr -d ' \n')
                test "${'$'}elf_magic" = 7f454c46
                compiler=absent
                if command -v cc >/dev/null 2>&1; then
                  printf 'int main(void){return 0;}\n' > "${'$'}rf410_dir/probe.c"
                  cc "${'$'}rf410_dir/probe.c" -o "${'$'}rf410_dir/probe"
                  "${'$'}rf410_dir/probe"
                  compiler=present
                fi
                for value in one two three; do printf '%s ' "${'$'}value" >/dev/null; done
                printf '__rf410_complex__:%s:compiler=%s\n' "${'$'}(uname -s)" "${'$'}compiler"
            """.trimIndent(),
            expected = "__rf410_complex__:Linux:compiler=",
        )
        val argv = executeArgv(context)
        val viewPlan = ProotCompatibilityRuntimeProvider.requirePlan(
            request = RuntimeExecutionRequest(
                payload = RuntimeExecutionPayload.Argv("/bin/bash", listOf("--login")),
                environment = mapOf(
                    ProotViewBinding.ENV_VIEW_ID to "rf410-logical-view",
                    ProotViewBinding.ENV_ENVIRONMENT_ID to "rf410-logical-environment",
                ),
                requirements = setOf(
                    RuntimeExecutionRequirement.FULL_LINUX,
                    RuntimeExecutionRequirement.INTERACTIVE_PTY,
                    RuntimeExecutionRequirement.FILESYSTEM_VIEW,
                ),
            ),
            selectionReason = "rf410_logical_view_probe",
        )
        check(viewPlan.requestedProotViewId == "rf410-logical-view")
        check(viewPlan.requestedProotEnvironmentId == "rf410-logical-environment")
        val orchestrated = executeOrchestratedShell(context)
        val compiler = if (complex.output.contains("compiler=present")) "present" else "absent"
        return "status=ok shellMs=${shell.durationMs} complexCompilerMs=${complex.durationMs} " +
            "compiler=$compiler argvMs=${argv.durationMs} " +
            "orchestratedMs=${orchestrated.durationMs} orchestratedLane=${orchestrated.lane} " +
            "orchestratedReason=${orchestrated.reason} viewPlan=true viewExecuted=false " +
            "totalMs=${SystemClock.elapsedRealtime() - startedAt}"
    }

    private data class OrchestratedExecution(
        val durationMs: Long,
        val lane: String,
        val reason: String,
    )

    /** 经过正式编排链验证 Provider；固定短命令，不暴露任意命令执行入口。 */
    private fun executeOrchestratedShell(context: Context): OrchestratedExecution {
        val recipe = KiteRecipe(
            id = "kite.debug.proot-compatibility-probe",
            name = "PRoot compatibility probe",
            description = "Debug-only fixed PRoot provider probe",
            type = KiteRecipe.TYPE_TEMPLATE,
            defaultUrl = "",
            shortcut = false,
            execution = KiteExecution.steps(
                listOf(
                    KiteRecipeStep(
                        id = "fixed-shell",
                        type = KiteRecipe.STEP_SHELL,
                        cmd = "printf '__rf410_orchestrated__\\n'",
                        surfaceMode = KiteRecipe.SURFACE_MODE_PANEL,
                        workdir = "/workspace",
                        timeoutMs = 30_000L,
                    )
                )
            ),
        )
        val instanceId = "proot-compatibility-probe-${System.currentTimeMillis()}"
        val lanes = Collections.synchronizedList(mutableListOf<String>())
        val reasons = Collections.synchronizedList(mutableListOf<String>())
        val finalState = AtomicReference<CardRunState>()
        val completed = CountDownLatch(1)
        val graph = KiteAppGraph.from(context)
        val sink = RunLifecycleSink { event ->
            if (event.state.instanceId != instanceId) return@RunLifecycleSink
            event.state.runtimeLane?.takeIf(String::isNotBlank)?.let(lanes::add)
            event.state.runtimeFallbackReason?.takeIf(String::isNotBlank)?.let(reasons::add)
            if (event.state.status in TERMINAL_STATUSES) {
                finalState.set(event.state)
                completed.countDown()
            }
        }
        graph.runLifecycleEventHub.register(sink)
        val startedAt = SystemClock.elapsedRealtime()
        try {
            val started = graph.runOrchestrator.start(
                RunStartRequest(
                    recipe = recipe,
                    instanceId = instanceId,
                    ownerKind = CardRunState.OWNER_KIND_RESOURCE,
                    stepId = recipe.id,
                )
            )
            check(started is RunCommandResult.Accepted) { "orchestrated_start_rejected:$started" }
            check(completed.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) { "orchestrated_timeout" }
            val state = checkNotNull(finalState.get()) { "orchestrated_state_missing" }
            check(state.status == CardRunStatus.Completed) {
                "orchestrated_failed:${state.lastError ?: state.lastMeaningfulOutput}"
            }
            val lane = checkNotNull((lanes + listOfNotNull(state.runtimeLane)).lastOrNull()) {
                "orchestrated_lane_missing"
            }
            check(lane == "proot_shell") { "orchestrated_lane_mismatch:$lanes" }
            val reason = checkNotNull((reasons + listOfNotNull(state.runtimeFallbackReason)).lastOrNull()) {
                "orchestrated_reason_missing"
            }
            check(state.terminalSessionId == null) { "orchestrated_created_terminal" }
            return OrchestratedExecution(
                durationMs = SystemClock.elapsedRealtime() - startedAt,
                lane = lane,
                reason = reason,
            )
        } finally {
            graph.runLifecycleEventHub.unregister(sink)
            CardRunStore.get(instanceId)?.takeIf { it.status !in TERMINAL_STATUSES }?.let {
                graph.runOrchestrator.stop(instanceId)
            }
            CardRunStore.removeRun(instanceId)
        }
    }

    private fun executeShell(
        context: Context,
        label: String,
        payload: String,
        expected: String,
    ): ProbeExecution {
        val config = WorkSurfaceRuntimeBridge.buildRequiredProotExecConfig(
            context = context,
            request = RuntimeExecutionRequest(
                payload = RuntimeExecutionPayload.CommandLine(payload),
                workingDirectory = "/workspace",
                requirements = setOf(RuntimeExecutionRequirement.FULL_LINUX),
            ),
            selectionReason = "rf410_${label}_requires_proot",
        )
        return execute(config.command, config.env, expected)
    }

    private fun executeArgv(context: Context): ProbeExecution {
        val plan = ProotCompatibilityRuntimeProvider.requirePlan(
            request = RuntimeExecutionRequest(
                payload = RuntimeExecutionPayload.Argv("/usr/bin/printf", listOf("__rf410_argv__")),
                workingDirectory = "/workspace",
                requirements = setOf(RuntimeExecutionRequirement.FULL_LINUX),
            ),
            selectionReason = "rf410_argv_requires_proot",
        )
        val config = WorkSurfaceRuntimeBridge.buildProotExecConfig(context, plan)
        return execute(config.command, config.env, "__rf410_argv__")
    }

    private fun execute(
        command: List<String>,
        environment: Map<String, String>,
        expected: String,
    ): ProbeExecution {
        val startedAt = SystemClock.elapsedRealtime()
        val output = StringBuilder()
        val process = ProcessBuilder(command)
            .redirectErrorStream(true)
            .apply { environment().putAll(environment) }
            .start()
        val reader = thread(name = "KiteProotCompatibilityReader", isDaemon = true) {
            process.inputStream.bufferedReader().useLines { lines ->
                lines.forEach { line -> if (output.length < 8_192) output.appendLine(line) }
            }
        }
        val completed = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        if (!completed) process.destroyForcibly()
        reader.join(1_500L)
        check(completed) { "probe_timeout" }
        check(process.exitValue() == 0) { "probe_exit_${process.exitValue()}:${output.toString().takeLast(400)}" }
        check(output.contains(expected)) { "probe_output_missing:$expected:${output.toString().takeLast(400)}" }
        return ProbeExecution(
            durationMs = SystemClock.elapsedRealtime() - startedAt,
            output = output.toString(),
        )
    }

    private val TERMINAL_STATUSES = setOf(
        CardRunStatus.Completed,
        CardRunStatus.Failed,
        CardRunStatus.Stopped,
        CardRunStatus.BridgeUnavailable,
    )
}
