package com.kite.app.platform.runs

import android.content.Context
import com.kite.app.agent.auth.AgentOfficialAccountCommandResult
import com.kite.app.agent.auth.AgentOfficialAccountCommandRunner
import com.kite.app.agent.process.AgentProcessFactory
import com.kite.app.agent.process.JavaAgentProcessFactory
import com.kite.app.agent.registration.AgentOfficialAccountCommand
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withContext

/** 复用 Agent 的 Host Node / PRoot 启动规划运行官方账号动作。 */
internal class AndroidAgentOfficialAccountCommandRunner(
    context: Context,
    private val processFactory: AgentProcessFactory = JavaAgentProcessFactory(),
    private val launchPlanner: ManagedAgentProcessLaunchPlanner =
        AndroidManagedAgentProcessLaunchPlanner(context.applicationContext),
    private val openExternal: (String) -> Boolean,
) : AgentOfficialAccountCommandRunner {
    override suspend fun run(command: AgentOfficialAccountCommand): AgentOfficialAccountCommandResult {
        val plannedLaunch = launchPlanner.plan(
            argv = command.argv,
            workingDirectory = DEFAULT_WORKDIR,
            environment = emptyMap(),
            runtimeGuarantees = emptySet(),
            runtimeGuaranteeEvidence = emptyMap(),
        )
        val process = processFactory.start(plannedLaunch.process)
        val output = StringBuilder()
        val outputLock = Mutex()
        val openedUrls = linkedSetOf<String>()
        return try {
            withTimeout(command.timeoutMs) {
                coroutineScope {
                    // 阻塞中的 waitFor/readLine 未必会仅凭协程取消立即返回。让一个同级任务在
                    // 取消传播的第一时间停止唯一进程，随后所有等待者才能可靠收口。
                    val processStopper = launch(start = CoroutineStart.UNDISPATCHED) {
                        try {
                            awaitCancellation()
                        } finally {
                            withContext(NonCancellable) { process.stop(CANCEL_STOP_GRACE_MS) }
                        }
                    }
                    suspend fun consume(line: String) {
                        outputLock.withLock {
                            if (output.isNotEmpty()) output.append('\n')
                            output.append(line)
                        }
                        URL_REGEX.findAll(line).forEach { match ->
                            val url = match.value.trimEnd('.', ',', ')', ']', '}')
                            if (openedUrls.add(url)) openExternal(url)
                        }
                    }
                    try {
                        val stdout = launch { process.stdoutLines.collect(::consume) }
                        val stderr = launch { process.stderrLines.collect(::consume) }
                        val exit = async { process.awaitExit() }.await()
                        stdout.join()
                        stderr.join()
                        AgentOfficialAccountCommandResult(exit, output.toString())
                    } finally {
                        withContext(NonCancellable) { processStopper.cancelAndJoin() }
                    }
                }
            }
        } finally {
            process.close()
        }
    }

    private companion object {
        const val DEFAULT_WORKDIR = "/workspace"
        const val CANCEL_STOP_GRACE_MS = 500L
        val URL_REGEX = Regex("https?://[^\\s<>\\\"']+")
    }
}
