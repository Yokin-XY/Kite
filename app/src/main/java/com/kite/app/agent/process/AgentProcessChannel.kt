package com.kite.app.agent.process

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.BufferedWriter
import java.io.Closeable
import java.io.IOException
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

/**
 * 一个不经过 PTY 的长期双向进程通道。
 *
 * ACP 等逐行协议必须保持 stdout 纯净，因此 stderr 单独暴露，调用方不得把两者合并。
 */
interface AgentProcessChannel : Closeable {
    val stdoutLines: Flow<String>
    val stderrLines: Flow<String>
    val pid: Long?
    val isAlive: Boolean

    suspend fun writeLine(line: String)
    suspend fun awaitExit(): Int
    suspend fun stop(gracePeriodMs: Long = DEFAULT_STOP_GRACE_MS): Int?

    companion object {
        const val DEFAULT_STOP_GRACE_MS = 2_000L
    }
}

data class AgentProcessLaunch(
    val command: List<String>,
    val environment: Map<String, String> = emptyMap()
)

fun interface AgentProcessFactory {
    fun start(launch: AgentProcessLaunch): AgentProcessChannel
}

/** Java/Android ProcessBuilder 实现；命令的 PRoot 组装仍由上层既有 runtime bridge 负责。 */
class JavaAgentProcessFactory : AgentProcessFactory {
    override fun start(launch: AgentProcessLaunch): AgentProcessChannel {
        require(launch.command.isNotEmpty()) { "agent_process_command_empty" }
        val process = ProcessBuilder(launch.command)
            .redirectInput(ProcessBuilder.Redirect.PIPE)
            .redirectOutput(ProcessBuilder.Redirect.PIPE)
            .redirectError(ProcessBuilder.Redirect.PIPE)
            .apply { environment().putAll(launch.environment) }
            .start()
        return JavaAgentProcessChannel(process)
    }
}

internal class JavaAgentProcessChannel(
    private val process: Process
) : AgentProcessChannel {
    private val writer = BufferedWriter(OutputStreamWriter(process.outputStream, StandardCharsets.UTF_8))
    private val writeMutex = Mutex()

    override val stdoutLines: Flow<String> = process.inputStream.lineFlow()
    override val stderrLines: Flow<String> = process.errorStream.lineFlow()
    override val pid: Long? = runCatching {
        (process.javaClass.getMethod("pid").invoke(process) as? Number)?.toLong()
    }.getOrNull()?.takeIf { it > 0L }
    override val isAlive: Boolean get() = process.isAlive

    override suspend fun writeLine(line: String) {
        require(!line.contains('\n') && !line.contains('\r')) { "agent_process_line_contains_newline" }
        writeMutex.withLock {
            withContext(Dispatchers.IO) {
                check(process.isAlive) { "agent_process_closed" }
                writer.write(line)
                writer.newLine()
                writer.flush()
            }
        }
    }

    override suspend fun awaitExit(): Int = withContext(Dispatchers.IO) {
        process.waitFor()
    }

    override suspend fun stop(gracePeriodMs: Long): Int? = withContext(Dispatchers.IO) {
        runCatching { writer.close() }
        if (!process.isAlive) return@withContext runCatching { process.exitValue() }.getOrNull()

        process.destroy()
        val stopped = process.waitFor(gracePeriodMs.coerceAtLeast(0L), TimeUnit.MILLISECONDS)
        if (!stopped) {
            process.destroyForcibly()
            process.waitFor(gracePeriodMs.coerceAtLeast(0L), TimeUnit.MILLISECONDS)
        }
        runCatching { process.exitValue() }.getOrNull()
    }

    override fun close() {
        runCatching { writer.close() }
        runCatching { process.inputStream.close() }
        runCatching { process.errorStream.close() }
        if (process.isAlive) process.destroy()
    }

    private fun java.io.InputStream.lineFlow(): Flow<String> = flow {
        bufferedReader(StandardCharsets.UTF_8).use { reader ->
            while (true) {
                val line = reader.readLine() ?: break
                emit(line)
            }
        }
    }.catch { error ->
        // 进程退出或 transport 主动关闭管道时，Android 的管道流会以 IOException 结束。
        // 对逐行协议而言这与 EOF 等价；真正的会话失败由协议层或进程退出观察者报告。
        if (error !is IOException) throw error
    }.flowOn(Dispatchers.IO)
}
