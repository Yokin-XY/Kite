package com.kite.app.foundation.runtime

import android.content.Context
import android.net.LocalServerSocket
import android.net.LocalSocket
import com.kite.app.foundation.contracts.ContainerRecord
import com.kite.app.foundation.workspace.ManagedRuntimeLaunchPlanner
import com.kite.app.foundation.workspace.ManagedRuntimeLaunchPlan
import com.kite.app.foundation.workspace.WorkSurfaceRuntimeBridge
import com.kite.app.foundation.logging.Logger
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStream
import java.util.Base64
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 终端命令宿主车道桥（快速通道整改 P2）：
 * 嵌入式终端里手敲的 node/python 经 PATH shim 转发到本桥，统一走
 * ManagedRuntimeLaunchPlanner 裁决（与 Agent 主进程同一合同）后宿主直跑；
 * 裁决拒绝或桥不可用时 shim 回退终端内原生命令（可用性优先）。
 *
 * 协议（abstract unix socket "kite-host-exec"，ndjson 帧）：
 * - 请求（一行）：{"argv":[...],"cwd":"/workspace","env":{...}}
 * - 响应：{"s":"started","pid":N} / {"s":"denied","reason":...}
 *   {"s":"out","d":base64} / {"s":"err","d":base64} / {"s":"exit","code":N}
 * - 输入/信号（shim→桥）：{"s":"in","d":base64} / {"s":"sig","sig":"INT"}
 */
object HostExecBridge {
    private const val TAG = "HostExecBridge"
    private const val SOCKET_NAME = "kite-host-exec"
    private const val MAX_REQUEST_BYTES = 64 * 1024

    /** 终端环境里值得透传给宿主车道的变量（其余由车道 buildConfig 自设）。 */
    private val ENV_PASSTHROUGH = setOf(
        "TERM", "COLORTERM", "NO_COLOR", "CLICOLOR_FORCE", "FORCE_COLOR", "LANG", "TZ",
    )

    private val started = AtomicBoolean(false)
    private val executor = Executors.newCachedThreadPool { runnable ->
        Thread(runnable, "kite-host-exec").apply { isDaemon = true }
    }

    @Synchronized
    fun ensureStarted(context: Context) {
        if (!started.compareAndSet(false, true)) return
        val appContext = context.applicationContext
        executor.execute {
            runCatching { serve(appContext) }
                .onFailure { error ->
                    Logger.e(TAG, "host exec bridge stopped: ${error.javaClass.simpleName}: ${error.message}")
                    started.set(false)
                }
        }
    }

    private fun serve(appContext: Context) {
        LocalServerSocket(SOCKET_NAME).use { server ->
            Logger.i(TAG, "host exec bridge listening on @$SOCKET_NAME")
            while (!Thread.currentThread().isInterrupted) {
                val client = server.accept()
                executor.execute { handleClient(appContext, client) }
            }
        }
    }

    private fun handleClient(appContext: Context, client: LocalSocket) {
        client.use { socket ->
            val input = BufferedReader(InputStreamReader(socket.inputStream, Charsets.UTF_8))
            val output = socket.outputStream
            val requestLine = runCatching { input.readLine() }.getOrNull()
            if (requestLine == null || requestLine.length > MAX_REQUEST_BYTES) return
            val request = runCatching { JSONObject(requestLine) }.getOrNull() ?: return
            val argv = request.optJSONArray("argv")?.let { array ->
                List(array.length()) { index -> array.optString(index) }
            }.orEmpty()
            if (argv.isEmpty()) return
            val workingDirectory = request.optString("cwd").ifBlank { "/workspace" }
            val passthroughEnv = buildMap {
                val env = request.optJSONObject("env")
                if (env != null) {
                    for (key in env.keys()) {
                        if (key in ENV_PASSTHROUGH) put(key, env.optString(key))
                    }
                }
            }
            launchAndBridge(appContext, argv, workingDirectory, passthroughEnv, output, input)
        }
    }

    private fun launchAndBridge(
        appContext: Context,
        argv: List<String>,
        workingDirectory: String,
        environment: Map<String, String>,
        output: OutputStream,
        input: BufferedReader,
    ) {
        val container: ContainerRecord = WorkSurfaceRuntimeBridge.ensureDefaultContainer(appContext)
        val workspaceDirectory = File(
            WorkSurfaceRuntimeBridge.resolveActiveWorkspaceEnvironment(container).workspacePath
        )
        val plan = ManagedRuntimeLaunchPlanner.plan(
            context = appContext,
            container = container,
            workspaceDirectory = workspaceDirectory,
            request = RuntimeExecutionRequest(
                payload = RuntimeExecutionPayload.Argv(argv.first(), argv.drop(1)),
                workingDirectory = workingDirectory,
                environment = environment,
                guarantees = emptySet(),
                guaranteeEvidence = emptyMap(),
                hardLinkMode = RuntimeHardLinkMode.EMULATED,
                requirements = emptySet(),
            ),
        )
        when (plan) {
            is ManagedRuntimeLaunchPlan.Blocked -> {
                writeFrame(output, JSONObject().put("s", "denied").put("reason", plan.reason))
                return
            }
            is ManagedRuntimeLaunchPlan.Proot -> {
                RuntimeLaneTelemetry.record(
                    entryPoint = "terminal-cmd",
                    lane = "proot_shell",
                    fallbackReason = plan.reason,
                    detail = argv.firstOrNull(),
                )
                writeFrame(output, JSONObject().put("s", "denied").put("reason", plan.reason))
                return
            }
            is ManagedRuntimeLaunchPlan.Ready -> Unit
        }
        val config = (plan as ManagedRuntimeLaunchPlan.Ready).config
        RuntimeLaneTelemetry.record(
            entryPoint = "terminal-cmd",
            lane = plan.lane.value,
            detail = argv.firstOrNull(),
        )
        val process = ProcessBuilder(config.args.toList())
            .apply {
                environment().clear()
                config.env.forEach { entry ->
                    environment()[entry.substringBefore('=')] = entry.substringAfter('=', "")
                }
            }
            .start()
        writeFrame(output, JSONObject().put("s", "started"))

        val pumpOut = executor.submit {
            process.inputStream.use { stream ->
                val buffer = ByteArray(16 * 1024)
                while (true) {
                    val read = stream.read(buffer)
                    if (read < 0) break
                    writeFrame(
                        output,
                        JSONObject()
                            .put("s", "out")
                            .put("d", Base64.getEncoder().encodeToString(buffer.copyOf(read))),
                    )
                }
            }
        }
        val pumpErr = executor.submit {
            process.errorStream.use { stream ->
                val buffer = ByteArray(16 * 1024)
                while (true) {
                    val read = stream.read(buffer)
                    if (read < 0) break
                    writeFrame(
                        output,
                        JSONObject()
                            .put("s", "err")
                            .put("d", Base64.getEncoder().encodeToString(buffer.copyOf(read))),
                    )
                }
            }
        }
        val stdinWriter = process.outputStream
        try {
            while (true) {
                val line = input.readLine() ?: break
                val frame = runCatching { JSONObject(line) }.getOrNull() ?: continue
                when (frame.optString("s")) {
                    "in" -> {
                        val bytes = runCatching {
                            Base64.getDecoder().decode(frame.optString("d"))
                        }.getOrNull() ?: continue
                        stdinWriter.write(bytes)
                        stdinWriter.flush()
                    }
                    "sig" -> {
                        // Java Process 无 SIGINT；INT/TERM 统一 SIGTERM 优雅退出（node 收 TERM 会清退）。
                        when (frame.optString("sig")) {
                            "INT", "TERM" -> runCatching { process.destroy() }
                            "KILL" -> runCatching { process.destroyForcibly() }
                        }
                    }
                }
            }
        } catch (_: Exception) {
            // shim 断开：终止宿主进程，避免孤儿。
        } finally {
            // shim 断开（EOF）：关进程 stdin 让读端收尾，再等待退出；超时强杀防孤儿。
            runCatching { stdinWriter.close() }
        }
        val exit = runCatching {
            if (!process.waitFor(30, java.util.concurrent.TimeUnit.SECONDS)) {
                process.destroyForcibly()
                process.waitFor()
            } else {
                process.exitValue()
            }
        }.getOrDefault(-1)
        // pump 收尾后再写 exit 帧，保证输出先于退出码到达。
        runCatching { pumpOut.get(); pumpErr.get() }
        writeFrame(output, JSONObject().put("s", "exit").put("code", exit))
    }

    private fun writeFrame(output: OutputStream, payload: JSONObject) {
        runCatching {
            output.write((payload.toString() + "\n").toByteArray(Charsets.UTF_8))
            output.flush()
        }
    }
}
