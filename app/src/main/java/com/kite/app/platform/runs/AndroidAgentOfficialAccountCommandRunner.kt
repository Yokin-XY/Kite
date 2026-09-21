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
import java.io.File

/** 复用 Agent 的 Host Node / PRoot 启动规划运行官方账号动作。 */
internal class AndroidAgentOfficialAccountCommandRunner(
    context: Context,
    private val processFactory: AgentProcessFactory = JavaAgentProcessFactory(),
    private val launchPlanner: ManagedAgentProcessLaunchPlanner =
        AndroidManagedAgentProcessLaunchPlanner(context.applicationContext),
    private val openExternal: (String) -> Boolean,
) : AgentOfficialAccountCommandRunner {
    private val appContext = context.applicationContext

    override suspend fun run(command: AgentOfficialAccountCommand): AgentOfficialAccountCommandResult {
        // 干净 HOME：auth status 也会读 settings.json 内的第三方凭据 env 段，
        // 换空 HOME 才能反映真实的官方登录身份（设备实验：空 HOME → loggedIn:false）。
        val cleanHomeEnvironment = if (command.cleanHome) {
            val home = File(appContext.cacheDir, "official-account-home")
            home.mkdirs()
            mapOf("HOME" to home.absolutePath)
        } else {
            emptyMap()
        }
        val plannedLaunch = launchPlanner.plan(
            argv = command.argv,
            workingDirectory = DEFAULT_WORKDIR,
            environment = cleanHomeEnvironment,
            runtimeGuarantees = emptySet(),
            runtimeGuaranteeEvidence = emptyMap(),
            hardLinkMode = command.hardLinkMode,
            requirements = emptySet(),
        ).let { planned ->
            // 官方账号动作必须在无第三方凭据的环境下运行：第三方 token/base_url 会
            // 伪装成登录态（claude 把 ANTHROPIC_AUTH_TOKEN 当作已登录官方）。
            if (command.credentialEnvDenylist.isEmpty()) {
                planned
            } else {
                planned.copy(
                    process = planned.process.copy(
                        environmentDenylist = command.credentialEnvDenylist.toSet(),
                    )
                )
            }
        }
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
