package com.kite.app.agent.process

import android.os.Process as AndroidProcess
import android.system.Os
import android.system.OsConstants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.BufferedWriter
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import java.util.UUID

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
    val environment: Map<String, String> = emptyMap(),
    val workingDirectory: String? = null,
)

fun interface AgentProcessFactory {
    fun start(launch: AgentProcessLaunch): AgentProcessChannel
}

/** Java/Android ProcessBuilder 实现；命令的 PRoot 组装仍由上层既有 runtime bridge 负责。 */
class JavaAgentProcessFactory : AgentProcessFactory {
    override fun start(launch: AgentProcessLaunch): AgentProcessChannel {
        val ownerId = UUID.randomUUID().toString()
        val process = buildProcessBuilder(launch)
            .apply { environment()[PROCESS_OWNER_ENV] = ownerId }
            .redirectInput(ProcessBuilder.Redirect.PIPE)
            .redirectOutput(ProcessBuilder.Redirect.PIPE)
            .redirectError(ProcessBuilder.Redirect.PIPE)
            .start()
        return JavaAgentProcessChannel(process, processOwnerId = ownerId)
    }

    internal fun buildProcessBuilder(launch: AgentProcessLaunch): ProcessBuilder {
        require(launch.command.isNotEmpty()) { "agent_process_command_empty" }
        return ProcessBuilder(launch.command).apply {
            launch.workingDirectory
                ?.trim()
                ?.takeIf(String::isNotBlank)
                ?.let(::File)
                ?.also { directory -> require(directory.isDirectory) { "agent_workdir_invalid:$directory" } }
                ?.let(::directory)
            environment().putAll(launch.environment)
        }
    }
}

internal class JavaAgentProcessChannel(
    private val process: Process,
    private val processOwnerId: String? = null,
    private val processTreeStopper: ProcOwnedProcessTreeStopper = ProcOwnedProcessTreeStopper(),
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

    override suspend fun awaitExit(): Int = runInterruptible(Dispatchers.IO) {
        process.waitFor()
    }

    override suspend fun stop(gracePeriodMs: Long): Int? = withContext(Dispatchers.IO) {
        runCatching { writer.close() }
        if (!process.isAlive) return@withContext runCatching { process.exitValue() }.getOrNull()

        val grace = gracePeriodMs.coerceAtLeast(0L)
        processOwnerId?.let { ownerId ->
            processTreeStopper.stopOwner(ownerId, grace, grace)
        } ?: pid?.toInt()?.takeIf { it > 0 }?.let { rootPid ->
            processTreeStopper.stop(rootPid, grace, grace)
        }
        if (process.isAlive) process.destroy()
        val stopped = process.waitFor(grace, TimeUnit.MILLISECONDS)
        if (!stopped) {
            process.destroyForcibly()
            process.waitFor(grace, TimeUnit.MILLISECONDS)
        }
        runCatching { process.exitValue() }.getOrNull()
    }

    override fun close() {
        runCatching { writer.close() }
        runCatching { process.inputStream.close() }
        runCatching { process.errorStream.close() }
        if (process.isAlive) {
            processOwnerId?.let(processTreeStopper::forceStopOwner)
                ?: pid?.toInt()?.takeIf { it > 0 }?.let(processTreeStopper::forceStop)
            process.destroyForcibly()
        }
    }

    private fun java.io.InputStream.lineFlow(): Flow<String> = flow {
        bufferedReader(StandardCharsets.UTF_8).use { reader ->
            while (true) {
                val line = runInterruptible(Dispatchers.IO) { reader.readLine() } ?: break
                emit(line)
            }
        }
    }.catch { error ->
        // 进程退出或 transport 主动关闭管道时，Android 的管道流会以 IOException 结束。
        // 对逐行协议而言这与 EOF 等价；真正的会话失败由协议层或进程退出观察者报告。
        if (error !is IOException) throw error
    }.flowOn(Dispatchers.IO)
}

/**
 * 只收敛由一个受管进程创建的后代树，避免销毁启动外壳后 PRoot/CLI 被重新挂到应用进程继续存活。
 */
internal class ProcOwnedProcessTreeStopper(
    private val procRoot: File = File("/proc"),
    private val currentPid: () -> Int = AndroidProcess::myPid,
    private val sendSignal: (Int, Int) -> Unit = Os::kill,
    private val sleep: (Long) -> Unit = Thread::sleep,
) {
    fun stopOwner(ownerId: String, gracefulWaitMs: Long, killWaitMs: Long): Boolean {
        if (ownerId.isBlank()) return true
        var live = matchingOwnerPids(ownerId)
        if (live.isEmpty()) return true
        live.forEach { pid -> runCatching { sendSignal(pid, OsConstants.SIGTERM) } }
        live = awaitOwnerGone(ownerId, gracefulWaitMs)
        if (live.isNotEmpty()) {
            live.forEach { pid -> runCatching { sendSignal(pid, OsConstants.SIGKILL) } }
            live = awaitOwnerGone(ownerId, killWaitMs)
        }
        return live.isEmpty()
    }

    fun forceStopOwner(ownerId: String) {
        if (ownerId.isBlank()) return
        matchingOwnerPids(ownerId).forEach { pid -> runCatching { sendSignal(pid, OsConstants.SIGKILL) } }
    }

    fun stop(rootPid: Int, gracefulWaitMs: Long, killWaitMs: Long): Boolean {
        val targets = ownedTree(rootPid)
        if (targets.isEmpty()) return true
        targets.forEach { pid -> runCatching { sendSignal(pid, OsConstants.SIGTERM) } }
        var live = awaitGone(targets, gracefulWaitMs)
        if (live.isNotEmpty()) {
            live.forEach { pid -> runCatching { sendSignal(pid, OsConstants.SIGKILL) } }
            live = awaitGone(live, killWaitMs)
        }
        return live.isEmpty()
    }

    fun forceStop(rootPid: Int) {
        ownedTree(rootPid).forEach { pid -> runCatching { sendSignal(pid, OsConstants.SIGKILL) } }
    }

    private fun awaitGone(targets: Collection<Int>, timeoutMs: Long): Set<Int> {
        val deadline = System.nanoTime() + timeoutMs.coerceAtLeast(0L) * 1_000_000L
        var live = targets.filterTo(linkedSetOf(), ::isAlive)
        while (live.isNotEmpty() && System.nanoTime() < deadline) {
            sleep(POLL_MS)
            live = live.filterTo(linkedSetOf(), ::isAlive)
        }
        return live
    }

    private fun awaitOwnerGone(ownerId: String, timeoutMs: Long): Set<Int> {
        val deadline = System.nanoTime() + timeoutMs.coerceAtLeast(0L) * 1_000_000L
        var live = matchingOwnerPids(ownerId)
        while (live.isNotEmpty() && System.nanoTime() < deadline) {
            sleep(POLL_MS)
            live = matchingOwnerPids(ownerId)
        }
        return live
    }

    private fun matchingOwnerPids(ownerId: String): Set<Int> {
        val expected = "$PROCESS_OWNER_ENV=$ownerId"
        val self = currentPid()
        return procRoot.listFiles().orEmpty().asSequence()
            .mapNotNull { directory -> directory.name.toIntOrNull()?.let { it to directory } }
            .filter { (pid, _) -> pid != self }
            .filter { (_, directory) -> environmentContains(File(directory, "environ"), expected) }
            .mapTo(linkedSetOf()) { (pid, _) -> pid }
    }

    private fun ownedTree(rootPid: Int): List<Int> {
        if (rootPid <= 0 || rootPid == currentPid()) return emptyList()
        val childrenByParent = procRoot.listFiles().orEmpty().asSequence()
            .mapNotNull { directory ->
                val pid = directory.name.toIntOrNull() ?: return@mapNotNull null
                val parent = readParentPid(File(directory, "status")) ?: return@mapNotNull null
                parent to pid
            }
            .groupBy({ it.first }, { it.second })
        val ordered = mutableListOf<Int>()
        val visited = mutableSetOf<Int>()
        fun visit(pid: Int) {
            if (!visited.add(pid)) return
            childrenByParent[pid].orEmpty().forEach(::visit)
            ordered += pid
        }
        visit(rootPid)
        return ordered
    }

    private fun readParentPid(status: File): Int? = runCatching {
        status.useLines { lines ->
            lines.firstOrNull { it.startsWith("PPid:") }
                ?.substringAfter(':')
                ?.trim()
                ?.toIntOrNull()
        }
    }.getOrNull()

    private fun environmentContains(environment: File, expected: String): Boolean = runCatching {
        expected in environment.readBytes().toString(Charsets.UTF_8).split('\u0000')
    }.getOrDefault(false)

    private fun isAlive(pid: Int): Boolean = File(procRoot, pid.toString()).exists()

    private companion object {
        const val POLL_MS = 25L
    }
}

internal const val PROCESS_OWNER_ENV = "KITE_AGENT_PROCESS_OWNER"
