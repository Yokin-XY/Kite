package com.kite.app.foundation.devicebridge

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.ParcelFileDescriptor
import com.kite.app.foundation.workspace.KFWorkspaceManager
import com.kite.app.foundation.workspace.WorkSurfaceRuntimeBridge
import com.kite.app.foundation.workspace.WorkspaceBuildSupport
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

/** 只存在于 Debug 包，用于真机验证 UserService 的流、退出码和身份。 */
class DeviceBridgeSmokeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action !in setOf(ACTION, ACTION_CANCEL, ACTION_PROOT_CLI)) return
        if (action == ACTION_PROOT_CLI) {
            runProotCliSmoke(context)
            return
        }
        val cancelSmoke = action == ACTION_CANCEL
        val command = if (cancelSmoke) {
            CANCEL_COMMAND
        } else {
            intent.getStringExtra(EXTRA_COMMAND)
                ?.takeIf { it.isNotBlank() && it.length <= MAX_COMMAND_LENGTH && '\u0000' !in it }
                ?: DEFAULT_COMMAND
        }
        val pending = goAsync()
        thread(start = true, isDaemon = true, name = "kite-device-smoke") {
            runCatching {
                ShizukuBridgeStateOwner.start(context)
                val state = ShizukuBridgeStateOwner.refresh("debug_smoke")
                check(state.lifecycle == DeviceBridgeLifecycleStatus.Ready) {
                    "backend_not_ready:${state.lifecycle}"
                }

                val process = ShizukuUserServiceProcessClient.startShell(context, command)
                process.outputStream.close()
                val stdout = AtomicReference("")
                val stderr = AtomicReference("")
                val stdoutThread = readAsync(process.inputStream, stdout)
                val stderrThread = readAsync(process.errorStream, stderr)

                if (cancelSmoke) {
                    Thread.sleep(CANCEL_AFTER_MS)
                    process.destroy()
                    check(process.waitForTimeout(CANCEL_WAIT_MS)) { "cancel_timeout" }
                }

                val deadline = System.nanoTime() + TIMEOUT_MS * 1_000_000L
                while (!process.waitForTimeout(POLL_INTERVAL_MS)) {
                    if (System.nanoTime() >= deadline) {
                        process.destroy()
                        check(process.waitForTimeout(CANCEL_WAIT_MS)) { "cancel_timeout" }
                        break
                    }
                }
                stdoutThread.join(STREAM_JOIN_MS)
                stderrThread.join(STREAM_JOIN_MS)
                "status=ok exit=${process.exitValue()} stdout=${safe(stdout.get())} stderr=${safe(stderr.get())}"
            }.onSuccess { result ->
                pending.resultCode = Activity.RESULT_OK
                pending.resultData = result
            }.onFailure { error ->
                pending.resultCode = Activity.RESULT_CANCELED
                pending.resultData = "status=failed error=${safe(error.javaClass.simpleName + ":" + error.message.orEmpty())}"
            }
            pending.finish()
        }
    }

    private fun runProotCliSmoke(context: Context) {
        val pending = goAsync()
        thread(start = true, isDaemon = true, name = "kite-device-proot-smoke") {
            runCatching {
                val space = KFWorkspaceManager.getCurrentSpace(context)
                    ?: KFWorkspaceManager.listSpaces(context).firstOrNull()
                    ?: KFWorkspaceManager.ensureActiveSpace(context)
                WorkspaceBuildSupport.installSystemComponents(
                    context = context,
                    workspaceDir = File(space.workspacePath),
                    sealedChecker = { true }
                )
                val config = WorkSurfaceRuntimeBridge.buildArgvExecConfig(
                    context = context,
                    workingDirectory = "/workspace",
                    argv = listOf(
                        "/bin/sh",
                        "-lc",
                        buildString {
                            append("set -eu; ")
                            append("cap=/tmp/kite-device-capabilities-smoke.json; ")
                            append("${WorkspaceBuildSupport.CONTAINER_KITE_DEVICE_PATH} capabilities --json > \"\$cap\"; ")
                            append("grep -q '\"protocolVersion\":1' \"\$cap\"; ")
                            append("${WorkspaceBuildSupport.CONTAINER_KITE_DEVICE_PATH} system info; ")
                            append("${WorkspaceBuildSupport.CONTAINER_KITE_DEVICE_PATH} package inspect com.kite.app >/dev/null; ")
                            append("${WorkspaceBuildSupport.CONTAINER_KITE_DEVICE_PATH} screen size; ")
                            append("rm -f \"\$cap\"; printf 'capability_pack=ok\\n'")
                        }
                    )
                )
                val output = AtomicReference("")
                val process = ProcessBuilder(config.command)
                    .redirectErrorStream(true)
                    .apply { environment().putAll(config.env) }
                    .start()
                val reader = thread(start = true, isDaemon = true, name = "kite-device-proot-smoke-reader") {
                    output.set(process.inputStream.bufferedReader().use { it.readText() })
                }
                val completed = process.waitFor(PROOT_CLI_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                if (!completed) process.destroyForcibly()
                reader.join(STREAM_JOIN_MS)
                check(completed) { "proot_cli_timeout" }
                check(process.exitValue() == 0) {
                    "proot_cli_failed:exit=${process.exitValue()}:${output.get()}"
                }
                "status=ok stdout=${safe(output.get())}"
            }.onSuccess { result ->
                pending.resultCode = Activity.RESULT_OK
                pending.resultData = result
            }.onFailure { error ->
                pending.resultCode = Activity.RESULT_CANCELED
                pending.resultData =
                    "status=failed error=${safe(error.javaClass.simpleName + ":" + error.message.orEmpty())}"
            }
            pending.finish()
        }
    }

    private fun readAsync(descriptor: ParcelFileDescriptor, target: AtomicReference<String>): Thread = thread(
        start = true,
        isDaemon = true,
        name = "kite-device-smoke-stream"
    ) {
        target.set(
            runCatching {
                ParcelFileDescriptor.AutoCloseInputStream(descriptor).bufferedReader().use { it.readText() }
            }.getOrDefault("")
        )
    }

    private fun safe(value: String): String = value
        .replace(Regex("[\\r\\n\\t]+"), " ")
        .take(MAX_RESULT_LENGTH)

    companion object {
        const val ACTION = "com.kite.app.debug.DEVICE_BRIDGE_SMOKE"
        const val ACTION_CANCEL = "com.kite.app.debug.DEVICE_BRIDGE_CANCEL_SMOKE"
        const val ACTION_PROOT_CLI = "com.kite.app.debug.DEVICE_BRIDGE_PROOT_CLI_SMOKE"
        const val EXTRA_COMMAND = "command"
        private const val DEFAULT_COMMAND = "printf 'bridge-ok uid='; id -u; printf ' package='; pm path com.kite.app"
        private const val CANCEL_COMMAND = "sleep 30; printf 'should-not-print'"
        private const val MAX_COMMAND_LENGTH = 1_024
        private const val MAX_RESULT_LENGTH = 4_096
        private const val POLL_INTERVAL_MS = 100L
        private const val TIMEOUT_MS = 15_000L
        private const val CANCEL_AFTER_MS = 250L
        private const val CANCEL_WAIT_MS = 3_000L
        private const val STREAM_JOIN_MS = 1_000L
        private const val PROOT_CLI_TIMEOUT_SECONDS = 20L
    }
}
