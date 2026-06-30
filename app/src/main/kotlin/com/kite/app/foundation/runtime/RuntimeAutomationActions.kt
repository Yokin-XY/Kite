package com.kite.app.foundation.runtime

import android.content.Context
import android.content.pm.ApplicationInfo
import com.kite.app.foundation.logging.Logger
import com.kite.app.foundation.service.SupervisordServiceHealthStore
import com.kite.app.foundation.workspace.WorkSurfaceRuntimeBridge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import org.json.JSONObject

object RuntimeAutomationActions {
    private const val LOG_TAG = "RuntimeAutomation"
    private const val LOG_FILE = "adb-automation.log"
    private const val COMMAND_TIMEOUT_SECONDS = 120L
    private const val CALIBRATION_TIMEOUT_SECONDS = 3_600L
    private const val OUTPUT_LIMIT = 28_000
    private const val PREVIEW_LIMIT = 200
    private const val P0_CALIBRATION_SCRIPT_ASSET = "kf-scripts/kf-proot-device-calibration-p0.py"
    private const val P0_CALIBRATION_SCRIPT_NAME = "kf-proot-device-calibration-p0.py"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val logLock = Any()

    fun isEnabled(context: Context, action: String): Boolean {
        return isAutomationEnabled(context, action)
    }

    fun runEnvDoctor(context: Context) {
        if (!isAutomationEnabled(context, "env-doctor")) return
        val appContext = context.applicationContext
        scope.launch {
            val result = executeContainerCommand(
                context = appContext,
                label = "env-doctor",
                payload = envDoctorPayload()
            )
            appendAutomationLog(
                appContext,
                buildString {
                    append(result.toLogBlock())
                    appendLine(hostNetworkContractBlock(appContext, reason = "env-doctor"))
                }
            )
        }
    }

    fun runCompatSmoke(context: Context) {
        if (!isAutomationEnabled(context, "compat-smoke")) return
        val appContext = context.applicationContext
        scope.launch {
            val result = executeContainerCommand(
                context = appContext,
                label = "compat-smoke",
                payload = compatSmokePayload()
            )
            appendAutomationLog(
                appContext,
                buildString {
                    append(result.toLogBlock())
                    appendLine(hostNetworkContractBlock(appContext, reason = "compat-smoke"))
                }
            )
            Logger.i(LOG_TAG, "ADB compat_smoke complete: ${result.summaryLine()}")
        }
    }

    fun runOneShotCommand(context: Context, command: String) {
        if (!isAutomationEnabled(context, "run-command")) return
        val trimmed = command.trim()
        if (trimmed.isBlank()) {
            Logger.i(LOG_TAG, "Ignore empty adb run_command request")
            return
        }
        val appContext = context.applicationContext
        Logger.i(
            LOG_TAG,
            "ADB run_command requested: length=${command.length} preview=${command.escapedPreview()}"
        )
        scope.launch {
            val result = executeContainerCommand(
                context = appContext,
                label = "run-command",
                payload = command
            )
            appendAutomationLog(appContext, result.toLogBlock())
        }
    }

    fun runProotCalibration(context: Context, target: String) {
        if (!isAutomationEnabled(context, "run-proot-calibration")) return
        val appContext = context.applicationContext
        val normalized = target.trim().lowercase()
        if (normalized != "p0") {
            Logger.i(LOG_TAG, "Ignore deprecated PRoot calibration target: $target")
            scope.launch {
                appendAutomationLog(
                    appContext,
                    "== proot-calibration-$normalized rejected ==\n" +
                        "只保留 P0：单 PRoot 寻峰校准；P1/P2 已停用，避免误触旧测试路径。\n"
                )
            }
            return
        }
        val payload = prootCalibrationP0Payload()
        Logger.i(LOG_TAG, "PRoot calibration requested: target=p0")
        scope.launch {
            appendAutomationLog(appContext, "== proot-calibration-p0 requested ==\n")
            installPackagedCalibrationScriptIfNeeded(
                context = appContext,
                assetPath = P0_CALIBRATION_SCRIPT_ASSET,
                scriptName = P0_CALIBRATION_SCRIPT_NAME
            )
            val result = executeContainerCommand(
                context = appContext,
                label = "proot-calibration-p0",
                payload = payload,
                timeoutSeconds = CALIBRATION_TIMEOUT_SECONDS
            )
            if (result.exitCode != 0) {
                appendProotCalibrationRunError(appContext, result)
            }
            appendAutomationLog(appContext, result.toLogBlock())
            RuntimeHealthStore.refresh(appContext, reason = "status-ui-proot-calibration-p0")
        }
    }

    private fun appendProotCalibrationRunError(context: Context, result: AutomationCommandResult) {
        val workspacePath = WorkSurfaceRuntimeBridge.getSavedContainer(context)?.workspacePath
            ?.takeIf { it.isNotBlank() }
            ?: return
        val planLog = File(File(workspacePath, ".kf").also { it.mkdirs() }, "proot-device-calibration-plan.jsonl")
        val payload = JSONObject()
            .put("schema", "proot_device_calibration_p0_runner_v0")
            .put("phase", "RUN_ERROR")
            .put("atMs", System.currentTimeMillis())
            .put("exitCode", result.exitCode)
            .put("timedOut", result.timedOut)
            .put("durationMs", result.durationMs)
            .put("source", "android_control_plane")
            .put("meaning", "calibration_process_stopped_after_last_checkpoint_overlay")
        planLog.appendText(payload.toString() + "\n")
    }

    private fun installPackagedCalibrationScriptIfNeeded(
        context: Context,
        assetPath: String,
        scriptName: String
    ) {
        val workspacePath = WorkSurfaceRuntimeBridge.getSavedContainer(context)?.workspacePath
        if (workspacePath.isNullOrBlank()) {
            Logger.i(LOG_TAG, "Skip calibration script install: workspace not ready")
            return
        }
        val bytes = runCatching {
            context.assets.open(assetPath).use { input -> input.readBytes() }
        }.getOrElse { error ->
            Logger.e(LOG_TAG, "Packaged calibration script missing: $assetPath, ${error.message}")
            return
        }
        val helperDir = File(workspacePath, ".kf").also { it.mkdirs() }
        val destination = File(helperDir, scriptName)
        val shouldWrite = !destination.exists() ||
            runCatching { !destination.readBytes().contentEquals(bytes) }.getOrDefault(true)
        if (shouldWrite) {
            destination.writeBytes(bytes)
            Logger.i(LOG_TAG, "Installed calibration script: ${destination.absolutePath}")
        }
        destination.setExecutable(true, false)
    }

    fun dumpDiagnostics(context: Context) {
        if (!isAutomationEnabled(context, "dump-diagnostics")) return
        val appContext = context.applicationContext
        scope.launch {
            runCatching {
                RuntimeHealthStore.attachContext(appContext)
                RuntimeFrameCoordinator.refreshProcessSnapshot(
                    context = appContext,
                    reason = "adb-dump-diagnostics"
                )
                RuntimeHealthStore.refresh(
                    context = appContext,
                    reason = "adb-dump-diagnostics"
                )
                SupervisordServiceHealthStore.refresh(
                    context = appContext,
                    reason = "adb-dump-diagnostics"
                )
                val snapshot = RuntimeDiagnostics.from()
                appendAutomationLog(
                    appContext,
                    buildString {
                        appendLine("== dump-diagnostics ==")
                        appendLine(snapshot.toLogLine(maxRoots = 20, maxServices = 20))
                        appendLine(snapshot.toStatusText(maxRoots = 20, maxServices = 20))
                    }
                )
            }.onFailure { error ->
                appendAutomationLog(
                    appContext,
                    "== dump-diagnostics failed ==\n${error.stackTraceToString().take(OUTPUT_LIMIT)}"
                )
                Logger.e(LOG_TAG, "ADB dump_diagnostics failed: ${error.message}")
            }
        }
    }

    fun rotateProotTelemetry(context: Context) {
        if (!isAutomationEnabled(context, "rotate-proot-telemetry")) return
        val appContext = context.applicationContext
        scope.launch {
            runCatching {
                RuntimeHealthStore.attachContext(appContext)
                val result = ProotTelemetryStore.rotateHistoryContaminatedJsonl(
                    context = appContext,
                    reason = "adb-manual-repair"
                )
                RuntimeHealthStore.refresh(appContext, reason = "adb-rotate-proot-telemetry")
                appendAutomationLog(appContext, result.toLogBlock())
                Logger.i(LOG_TAG, "ADB rotate_proot_telemetry complete: ${result.summary()}")
            }.onFailure { error ->
                appendAutomationLog(
                    appContext,
                    "== rotate-proot-telemetry failed ==\n${error.stackTraceToString().take(OUTPUT_LIMIT)}"
                )
                Logger.e(LOG_TAG, "ADB rotate_proot_telemetry failed: ${error.message}")
            }
        }
    }

    fun refreshProotTelemetryHeartbeat(context: Context) {
        if (!isAutomationEnabled(context, "refresh-proot-telemetry-heartbeat")) return
        val appContext = context.applicationContext
        scope.launch {
            runCatching {
                RuntimeHealthStore.attachContext(appContext)
                val result = ProotTelemetryStore.appendTelemetryHeartbeat(
                    context = appContext,
                    reason = "adb-proot-telemetry-heartbeat"
                )
                RuntimeHealthStore.refresh(
                    context = appContext,
                    reason = "adb-refresh-proot-telemetry-heartbeat"
                )
                appendAutomationLog(appContext, result.toLogBlock())
                Logger.i(LOG_TAG, "ADB refresh_proot_telemetry_heartbeat complete: ${result.summary()}")
            }.onFailure { error ->
                appendAutomationLog(
                    appContext,
                    "== refresh-proot-telemetry-heartbeat failed ==\n${error.stackTraceToString().take(OUTPUT_LIMIT)}"
                )
                Logger.e(LOG_TAG, "ADB refresh_proot_telemetry_heartbeat failed: ${error.message}")
            }
        }
    }

    fun resetProotDeviceCalibration(context: Context) {
        if (!isAutomationEnabled(context, "reset-proot-device-calibration")) return
        val appContext = context.applicationContext
        scope.launch {
            runCatching {
                RuntimeHealthStore.attachContext(appContext)
                val workspacePath = WorkSurfaceRuntimeBridge.getSavedContainer(appContext)?.workspacePath
                val result = RuntimeProotDeviceCalibrationOverlayStore.reset(
                    workspacePath = workspacePath,
                    reason = "adb-reset-proot-device-calibration"
                )
                RuntimeHealthStore.refresh(
                    context = appContext,
                    reason = "adb-reset-proot-device-calibration"
                )
                appendAutomationLog(appContext, result.toLogBlock())
                Logger.i(LOG_TAG, "ADB reset_proot_device_calibration complete: ${result.summary()}")
            }.onFailure { error ->
                appendAutomationLog(
                    appContext,
                    "== reset-proot-device-calibration failed ==\n${error.stackTraceToString().take(OUTPUT_LIMIT)}"
                )
                Logger.e(LOG_TAG, "ADB reset_proot_device_calibration failed: ${error.message}")
            }
        }
    }

    fun prepareProotLiveTraceeProbe(context: Context, targetLiveTracees: Int) {
        if (!isAutomationEnabled(context, "prepare-proot-live-tracee-probe")) return
        val appContext = context.applicationContext
        scope.launch {
            runCatching {
                RuntimeHealthStore.attachContext(appContext)
                val result = ProotTelemetryStore.prepareLiveTraceeProbeBaseline(
                    context = appContext,
                    targetLiveTracees = targetLiveTracees,
                    reason = "adb-proot-live-tracee-probe"
                )
                RuntimeHealthStore.refresh(
                    context = appContext,
                    reason = "adb-prepare-proot-live-tracee-probe"
                )
                appendAutomationLog(appContext, result.toLogBlock())
                Logger.i(LOG_TAG, "ADB prepare_proot_live_tracee_probe complete: ${result.summary()}")
            }.onFailure { error ->
                appendAutomationLog(
                    appContext,
                    "== prepare-proot-live-tracee-probe failed ==\n${error.stackTraceToString().take(OUTPUT_LIMIT)}"
                )
                Logger.e(LOG_TAG, "ADB prepare_proot_live_tracee_probe failed: ${error.message}")
            }
        }
    }

    fun injectProotLiveTraceeProbe(context: Context, targetLiveTracees: Int) {
        if (!isAutomationEnabled(context, "inject-proot-live-tracee-probe")) return
        val appContext = context.applicationContext
        scope.launch {
            runCatching {
                RuntimeHealthStore.attachContext(appContext)
                val result = ProotTelemetryStore.injectLiveTraceeProbeSample(
                    context = appContext,
                    targetLiveTracees = targetLiveTracees,
                    reason = "adb-proot-live-tracee-probe"
                )
                RuntimeHealthStore.refresh(
                    context = appContext,
                    reason = "adb-inject-proot-live-tracee-probe"
                )
                appendAutomationLog(appContext, result.toLogBlock())
                Logger.i(LOG_TAG, "ADB inject_proot_live_tracee_probe complete: ${result.summary()}")
            }.onFailure { error ->
                appendAutomationLog(
                    appContext,
                    "== inject-proot-live-tracee-probe failed ==\n${error.stackTraceToString().take(OUTPUT_LIMIT)}"
                )
                Logger.e(LOG_TAG, "ADB inject_proot_live_tracee_probe failed: ${error.message}")
            }
        }
    }

    fun prepareProotInstanceProbe(
        context: Context,
        instanceCount: Int,
        traceesPerInstance: Int,
        durationSeconds: Int
    ) {
        if (!isAutomationEnabled(context, "prepare-proot-instance-probe")) return
        val appContext = context.applicationContext
        scope.launch {
            runCatching {
                appendAutomationLog(
                    appContext,
                    "== prepare-proot-instance-probe rejected ==\n" +
                        "instanceCount=${instanceCount.coerceAtLeast(0)} " +
                        "traceesPerInstance=${traceesPerInstance.coerceAtLeast(0)} " +
                        "durationSeconds=${durationSeconds.coerceAtLeast(1)}\n" +
                        "reason=deprecated_p2_path_disabled_use_p0_single_proot_peak_calibration\n"
                )
                Logger.i(
                    LOG_TAG,
                    "Ignore deprecated prepare_proot_instance_probe: instances=$instanceCount tracees=$traceesPerInstance"
                )
            }.onFailure { error ->
                appendAutomationLog(
                    appContext,
                    "== prepare-proot-instance-probe failed ==\n${error.stackTraceToString().take(OUTPUT_LIMIT)}"
                )
                Logger.e(LOG_TAG, "ADB prepare_proot_instance_probe failed: ${error.message}")
            }
        }
    }

    fun startProotInstanceProbe(
        context: Context,
        instanceCount: Int,
        traceesPerInstance: Int,
        durationSeconds: Int
    ) {
        if (!isAutomationEnabled(context, "start-proot-instance-probe")) return
        val appContext = context.applicationContext
        scope.launch {
            runCatching {
                val safeInstanceCount = instanceCount.coerceIn(0, 8)
                val safeTraceesPerInstance = traceesPerInstance.coerceIn(0, 128)
                val safeDurationSeconds = durationSeconds.coerceIn(5, 900)
                appendAutomationLog(
                    appContext,
                    "== start-proot-instance-probe rejected ==\n" +
                        "instanceCount=$safeInstanceCount traceesPerInstance=$safeTraceesPerInstance " +
                        "durationSeconds=$safeDurationSeconds\n" +
                        "reason=deprecated_p2_path_disabled_use_p0_single_proot_peak_calibration\n"
                )
                Logger.i(
                    LOG_TAG,
                    "Ignore deprecated start_proot_instance_probe: instances=$safeInstanceCount tracees=$safeTraceesPerInstance"
                )
            }.onFailure { error ->
                appendAutomationLog(
                    appContext,
                    "== start-proot-instance-probe failed ==\n${error.stackTraceToString().take(OUTPUT_LIMIT)}"
                )
                Logger.e(LOG_TAG, "ADB start_proot_instance_probe failed: ${error.message}")
            }
        }
    }

    fun cleanupProotInstanceProbe(context: Context) {
        if (!isAutomationEnabled(context, "cleanup-proot-instance-probe")) return
        val appContext = context.applicationContext
        scope.launch {
            RuntimeHealthStore.refresh(
                context = appContext,
                reason = "adb-cleanup-proot-instance-probe"
            )
            appendAutomationLog(
                appContext,
                "== cleanup-proot-instance-probe rejected ==\n" +
                    "reason=deprecated_p2_path_removed_no_owned_probe_processes\n"
            )
            Logger.i(LOG_TAG, "Ignore deprecated cleanup_proot_instance_probe")
        }
    }

    internal fun logPasteRequest(context: Context, payload: String, sessionId: String?) {
        if (!isAutomationEnabled(context, "paste-multiline")) return
        val appContext = context.applicationContext
        Logger.i(
            LOG_TAG,
            "ADB paste_multiline requested: session=${sessionId ?: "active"} length=${payload.length} " +
                "hasLf=${payload.contains('\n')} hasCr=${payload.contains('\r')} preview=${payload.escapedPreview()}"
        )
        scope.launch {
            appendAutomationLog(
                appContext,
                "== paste-multiline ==\nsession=${sessionId ?: "active"} length=${payload.length} " +
                    "hasLf=${payload.contains('\n')} hasCr=${payload.contains('\r')} preview=${payload.escapedPreview()}\n"
            )
        }
    }

    private fun isAutomationEnabled(context: Context, action: String): Boolean {
        val debuggable = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        if (debuggable) {
            return true
        }
        Logger.i(LOG_TAG, "ADB automation disabled for non-debug build: $action")
        return false
    }

    private fun executeContainerCommand(
        context: Context,
        label: String,
        payload: String,
        timeoutSeconds: Long = COMMAND_TIMEOUT_SECONDS
    ): AutomationCommandResult {
        return runCatching {
            val config = WorkSurfaceRuntimeBridge.buildShellExecConfig(
                context = context,
                payload = payload,
                loginShell = true
            )
            val output = StringBuilder()
            val startedAt = System.currentTimeMillis()
            val process = ProcessBuilder(config.command)
                .redirectErrorStream(true)
                .apply { environment().putAll(config.env) }
                .start()
            val reader = thread(start = true, isDaemon = true, name = "AdbAutomationReader") {
                runCatching {
                    process.inputStream.bufferedReader().useLines { lines ->
                        lines.forEach { line ->
                            if (output.length < OUTPUT_LIMIT) {
                                output.append(line).append('\n')
                            }
                        }
                    }
                }
            }
            val finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
            reader.join(1200L)
            if (!finished) {
                process.destroyForcibly()
            }
            val exitCode = if (finished) process.exitValue() else -1
            val durationMs = System.currentTimeMillis() - startedAt
            AutomationCommandResult(
                label = label,
                exitCode = exitCode,
                timedOut = !finished,
                durationMs = durationMs,
                output = output.toString()
            )
        }.getOrElse { error ->
            AutomationCommandResult(
                label = label,
                exitCode = -1,
                timedOut = false,
                durationMs = 0L,
                output = error.stackTraceToString().take(OUTPUT_LIMIT)
            )
        }
    }

    private fun envDoctorPayload(): String {
        return """
            printf 'UV_LINK_MODE=%s\n' "${'$'}{UV_LINK_MODE:-}"
            printf 'PATH=%s\n' "${'$'}PATH"
            printf 'HOST_NETWORK_CONTRACT=%s\n' "shared_host_stack"
            printf 'HOST_LOOPBACK=%s\n' "shared_with_android"
            printf 'HOST_PORT_POLICY=%s\n' "prefer_127_0_0_1_and_ports_ge_1024"
            printf 'HOST_CONTROL_BOUNDARY=%s\n' "android_control_stays_in_apk"
            for tool in uv python3 node hermes kf-host kf-adb-check; do
              if command -v "${'$'}tool" >/dev/null 2>&1; then
                printf '%s.path=%s\n' "${'$'}tool" "${'$'}(command -v "${'$'}tool")"
                if [ "${'$'}tool" = "kf-host" ]; then
                  "${'$'}tool" endpoints 2>&1 | sed "s/^/kf-host.endpoint=/"
                elif [ "${'$'}tool" = "kf-adb-check" ]; then
                  echo "kf-adb-check.ready=true"
                else
                  "${'$'}tool" --version 2>&1 | sed "s/^/${'$'}tool.version=/"
                fi
              else
                printf '%s.path=missing\n' "${'$'}tool"
              fi
            done
            case ":${'$'}PATH:" in
              *":${'$'}HOME/.local/bin:"*) echo 'path.home_local_bin=true' ;;
              *) echo 'path.home_local_bin=false' ;;
            esac
            case ":${'$'}PATH:" in
              *":/workspace/.kf/bin:"*) echo 'path.workspace_kf_bin=true' ;;
              *) echo 'path.workspace_kf_bin=false' ;;
            esac
            case ":${'$'}PATH:" in
              *":/workspace/.kf/toolchains/"*"/bin:"*) echo 'path.workspace_kf_toolchain_bin=true' ;;
              *) echo 'path.workspace_kf_toolchain_bin=false' ;;
            esac
        """.trimIndent()
    }

    private fun prootCalibrationP0Payload(): String {
        return """
            set -u
            cd /workspace/.kf
            if [ ! -f ./kf-proot-device-calibration-p0.py ]; then
              echo "缺少 PRoot 设备校准脚本：/workspace/.kf/kf-proot-device-calibration-p0.py"
              exit 2
            fi
            python3 ./kf-proot-device-calibration-p0.py \
              --mode seek \
              --start-target 1 \
              --min-target 1 \
              --max-target 128 \
              --settle 4 \
              --units-per-worker 4 \
              --unit-iterations 20000 \
              --step-timeout 120 \
              --post-peak-confirm-rounds 10
            code=${'$'}?
            if [ "${'$'}code" -ne 0 ]; then
              now=${'$'}(date +%s)000
              printf '{"schema":"proot_device_calibration_p0_runner_v0","phase":"RUN_ERROR","atMs":%s,"exitCode":%s,"source":"android_payload_wrapper"}\n' "${'$'}now" "${'$'}code" >> /workspace/.kf/proot-device-calibration-plan.jsonl
            fi
            exit "${'$'}code"
        """.trimIndent()
    }

    private fun hostNetworkContractBlock(
        context: Context,
        reason: String
    ): String {
        return runCatching {
            RuntimeHealthStore.attachContext(context)
            RuntimeFrameCoordinator.refreshProcessSnapshot(
                context = context,
                reason = "automation-$reason"
            )
            val snapshot = RuntimeDiagnostics.from()
            buildString {
                appendLine("== host-network-contract ==")
                appendLine(snapshot.toStatusText(maxRoots = 8, maxServices = 8))
            }
        }.getOrElse { error ->
            "== host-network-contract failed ==\n${error.stackTraceToString().take(OUTPUT_LIMIT)}"
        }
    }

    private fun compatSmokePayload(): String {
        val script = """
            #!/usr/bin/env bash
            set +e

            TMPROOT=/tmp/kfshell-compat-smoke
            rm -rf "@D@TMPROOT"
            mkdir -p "@D@TMPROOT"

            PASS=0
            WARN=0
            FAIL=0
            KNOWN=0

            emit() {
              local level="@D@1"
              local area="@D@2"
              shift 2
              printf '%s\t%s\t%s\n' "@D@level" "@D@area" "@D@*"
              case "@D@level" in
                PASS) PASS=@D@((PASS + 1)) ;;
                WARN) WARN=@D@((WARN + 1)) ;;
                FAIL) FAIL=@D@((FAIL + 1)) ;;
                KNOWN_LIMITATION) KNOWN=@D@((KNOWN + 1)) ;;
              esac
            }

            has() {
              command -v "@D@1" >/dev/null 2>&1
            }

            quick() {
              if command -v timeout >/dev/null 2>&1; then
                timeout 6s "@D@@"
              else
                "@D@@"
              fi
            }

            version_for() {
              local cmd="@D@1"
              case "@D@cmd" in
                bash|sh) quick "@D@cmd" --version 2>&1 | sed -n '1p' ;;
                python3) python3 --version 2>&1 | head -n 1 ;;
                pip3) pip3 --version 2>&1 | head -n 1 ;;
                node|npm|npx|uv|git|curl|wget|rg|fd|jq|supervisorctl|supervisord) quick "@D@cmd" --version 2>&1 | sed -n '1p' ;;
                tar|gzip|xz|zstd|unzip|zip) quick "@D@cmd" --version 2>&1 | sed -n '1p' ;;
                ps) quick ps --version 2>&1 | sed -n '1p' ;;
                *) command -v "@D@cmd" ;;
              esac
            }

            check_cmd() {
              local level="@D@1"
              local cmd="@D@2"
              local why="@D@3"
              if has "@D@cmd"; then
                emit PASS "cmd:@D@cmd" "@D@(command -v "@D@cmd") | @D@(version_for "@D@cmd")"
              else
                emit "@D@level" "cmd:@D@cmd" "missing; @D@why"
              fi
            }

            path_contains() {
              case ":@D@PATH:" in
                *":@D@1:"*) return 0 ;;
                *) return 1 ;;
              esac
            }

            writable_dir() {
              local dir="@D@1"
              mkdir -p "@D@dir" 2>/dev/null
              local f="@D@dir/.kfshell-write-test"
              if (echo ok > "@D@f") 2>/dev/null && rm -f "@D@f" 2>/dev/null; then
                emit PASS "path:writable:@D@dir" "writable"
              else
                emit FAIL "path:writable:@D@dir" "not writable"
              fi
            }

            echo "KFSHELL_COMPAT_SMOKE_BEGIN"
            echo "timestamp=@D@(date -u '+%Y-%m-%dT%H:%M:%SZ' 2>/dev/null)"
            echo "kernel=@D@(uname -a 2>/dev/null)"
            echo "user=@D@(id 2>/dev/null)"
            echo "PATH=@D@PATH"
            echo "HOME=@D@HOME USER=@D@USER SHELL=@D@SHELL TMPDIR=@D@TMPDIR UV_LINK_MODE=@D@UV_LINK_MODE"

            check_cmd FAIL bash "login shell is required"
            check_cmd FAIL sh "fallback shell is required"
            for cmd in ls cp mv rm mkdir cat head tail sed awk grep find xargs chmod chown ln readlink stat env printenv date uname id whoami; do
              check_cmd FAIL "@D@cmd" "core utility is required"
            done
            for cmd in tar gzip curl git ssh python3 pip3; do
              check_cmd FAIL "@D@cmd" "common installer dependency is required"
            done
            for cmd in ps kill pkill pgrep which command; do
              check_cmd WARN "@D@cmd" "process/shell diagnostic utility is strongly recommended"
            done
            for cmd in xz zstd unzip zip wget uv node npm npx rg fd jq supervisorctl supervisord adb; do
              check_cmd WARN "@D@cmd" "suggested preinstall or install on demand for developer tooling or service diagnostics"
            done

            if [ "@D@UV_LINK_MODE" = "copy" ]; then
              emit PASS env:UV_LINK_MODE "copy"
            else
              emit FAIL env:UV_LINK_MODE "expected copy, got '@D@UV_LINK_MODE'"
            fi
            path_contains /root/.local/bin && emit PASS env:PATH.root_local "/root/.local/bin present" || emit WARN env:PATH.root_local "/root/.local/bin missing"
            path_contains /workspace/.kf/bin && emit PASS env:PATH.workspace_kf "/workspace/.kf/bin present" || emit WARN env:PATH.workspace_kf "/workspace/.kf/bin missing"
            case ":@D@PATH:" in
              *":/workspace/.kf/toolchains/"*"/bin:"*) emit PASS env:PATH.workspace_kf_toolchain "workspace toolchain bin present" ;;
              *) emit WARN env:PATH.workspace_kf_toolchain "workspace toolchain bin missing" ;;
            esac
            [ "@D@HOME" = "/root" ] && emit PASS env:HOME "/root" || emit WARN env:HOME "unexpected HOME=@D@HOME"
            [ "@D@USER" = "root" ] && emit PASS env:USER "root" || emit WARN env:USER "unexpected USER=@D@USER"
            [ -z "@D@TMPDIR" ] || [ "@D@TMPDIR" = "/tmp" ] && emit PASS env:TMPDIR "@D@TMPDIR" || emit WARN env:TMPDIR "unexpected TMPDIR=@D@TMPDIR"

            writable_dir /tmp
            writable_dir /root/.local/bin
            writable_dir /workspace/.kf/bin
            writable_dir /root/.cache/pip
            writable_dir /root/.cache/uv

            echo hello > "@D@TMPROOT/file" && grep -q hello "@D@TMPROOT/file" && emit PASS fs:write "create/read file" || emit FAIL fs:write "create/read failed"
            mv "@D@TMPROOT/file" "@D@TMPROOT/renamed" && [ -f "@D@TMPROOT/renamed" ] && emit PASS fs:rename "rename works" || emit FAIL fs:rename "rename failed"
            printf '#!/bin/sh\necho exec-ok\n' > "@D@TMPROOT/run.sh" && chmod +x "@D@TMPROOT/run.sh" && "@D@TMPROOT/run.sh" >/dev/null 2>&1 && emit PASS fs:chmod_exec "chmod +x works" || emit FAIL fs:chmod_exec "chmod +x failed"
            ln -s renamed "@D@TMPROOT/symlink" 2>/dev/null && [ "@D@(cat "@D@TMPROOT/symlink" 2>/dev/null)" = "hello" ] && emit PASS fs:symlink "symlink works" || emit FAIL fs:symlink "symlink failed"
            if ln "@D@TMPROOT/renamed" "@D@TMPROOT/hardlink" 2>/dev/null; then
              emit PASS fs:hardlink "hardlink works"
            else
              if [ "@D@UV_LINK_MODE" = "copy" ]; then
                emit KNOWN_LIMITATION fs:hardlink "hardlink not permitted under Android/proot; UV_LINK_MODE=copy mitigates uv installers"
              else
                emit FAIL fs:hardlink "hardlink failed and UV_LINK_MODE is not copy"
              fi
            fi
            echo atomic > "@D@TMPROOT/.atomic.tmp" && mv "@D@TMPROOT/.atomic.tmp" "@D@TMPROOT/atomic" && grep -q atomic "@D@TMPROOT/atomic" && emit PASS fs:atomic_write "temp file + rename works" || emit FAIL fs:atomic_write "atomic write failed"
            dd if=/dev/zero of="@D@TMPROOT/large.bin" bs=1024 count=512 >/dev/null 2>&1 && [ -s "@D@TMPROOT/large.bin" ] && emit PASS fs:large_write "512KiB write works" || emit FAIL fs:large_write "large write failed"
            if has flock; then
              ( flock -n 9 ) 9>"@D@TMPROOT/lock" >/dev/null 2>&1 && emit PASS fs:flock "flock works" || emit WARN fs:flock "flock command exists but lock failed"
            else
              emit WARN fs:flock "flock missing"
            fi

            bash -lc 'printf login-shell-ok' >/dev/null 2>&1 && emit PASS shell:login "bash -lc works" || emit FAIL shell:login "bash -lc failed"
            sh -c 'printf sh-ok' >/dev/null 2>&1 && emit PASS shell:sh "sh -c works" || emit FAIL shell:sh "sh -c failed"

            if python3 -m pip --version >/dev/null 2>&1; then
              emit PASS python:pip "python3 -m pip available"
            else
              emit FAIL python:pip "python3 -m pip unavailable"
            fi
            if quick python3 -m venv --without-pip "@D@TMPROOT/venv-nopip" >/dev/null 2>&1; then
              emit PASS python:venv_nopip "python3 -m venv --without-pip works"
            else
              emit FAIL python:venv_nopip "python3 -m venv --without-pip failed"
            fi
            emit KNOWN_LIMITATION python:venv_with_pip "CPython venv ensurepip/pip bootstrap is intentionally skipped in smoke because it can hang under Android/proot; use uv venv / uv pip in KFShell"
            if has uv; then
              quick uv --version >/dev/null 2>&1 && emit PASS uv:version "uv available with link_mode=@D@UV_LINK_MODE" || emit WARN uv:version "uv exists but version failed"
              rm -rf "@D@TMPROOT/uv-venv"
              quick uv venv "@D@TMPROOT/uv-venv" >/dev/null 2>&1 && emit PASS uv:venv "uv venv works as the recommended Python environment path" || emit WARN uv:venv "uv venv failed; inspect uv/python environment"
              quick uv pip install --help >/dev/null 2>&1 && emit PASS uv:pip_help "uv pip install help works" || emit WARN uv:pip_help "uv pip install help failed"
            else
              emit WARN uv:missing "uv not installed; acceptable if installed on demand"
            fi
            if has npm; then
              quick npm --version >/dev/null 2>&1 && emit PASS node:npm "npm available" || emit WARN node:npm "npm version failed"
            else
              emit WARN node:npm "npm missing; install Node when JS tooling is needed"
            fi
            if has npx; then
              quick npx --version >/dev/null 2>&1 && emit PASS node:npx "npx available" || emit WARN node:npx "npx version failed"
            else
              emit WARN node:npx "npx missing; install Node when JS tooling is needed"
            fi

            python3 - <<'PY' >/tmp/kfshell-compat-smoke/dns.out 2>&1
            import socket
            print(socket.gethostbyname("github.com"))
            PY
            [ @D@? -eq 0 ] && emit PASS net:dns "@D@(cat /tmp/kfshell-compat-smoke/dns.out)" || emit FAIL net:dns "github.com DNS failed: @D@(cat /tmp/kfshell-compat-smoke/dns.out 2>/dev/null)"

            if has curl; then
              curl -fsSL --max-time 8 https://raw.githubusercontent.com/github/gitignore/main/Python.gitignore -o "@D@TMPROOT/raw" >/dev/null 2>&1 && emit PASS net:https_raw "GitHub raw HTTPS works" || emit FAIL net:https_raw "curl GitHub raw failed; check DNS/CA/network"
              curl -I -fsSL --max-time 8 https://github.com/ >/dev/null 2>&1 && emit PASS net:https_head "GitHub HTTPS HEAD works" || emit FAIL net:https_head "curl HTTPS HEAD failed; check CA/network"
            fi
            if has git; then
              if has timeout; then
                timeout 12s git ls-remote --heads https://github.com/git/git master >/dev/null 2>&1
              else
                git ls-remote --heads https://github.com/git/git master >/dev/null 2>&1
              fi
              [ @D@? -eq 0 ] && emit PASS net:git_ls_remote "git ls-remote GitHub works" || emit FAIL net:git_ls_remote "git ls-remote failed"
            fi
            if has apt-get; then
              quick apt-get update --print-uris >/tmp/kfshell-compat-smoke/apt-print.log 2>&1 && emit PASS apt:print_uris "apt sources parse" || emit WARN apt:print_uris "apt update --print-uris failed: @D@(tail -n 2 /tmp/kfshell-compat-smoke/apt-print.log | tr '\n' ' ')"
            else
              emit WARN apt:missing "apt-get missing"
            fi
            if has supervisorctl; then
              quick supervisorctl status >/tmp/kfshell-compat-smoke/supervisorctl.log 2>&1 && emit PASS supervisor:status "supervisorctl status works" || emit WARN supervisor:status "supervisorctl unavailable from default shell: @D@(tail -n 2 /tmp/kfshell-compat-smoke/supervisorctl.log | tr '\n' ' ')"
            else
              emit WARN supervisor:status "supervisorctl missing"
            fi

            if command -v systemctl >/dev/null 2>&1; then
              emit KNOWN_LIMITATION linux:systemd "systemctl binary exists, but systemd is unavailable by design under Android/proot"
            else
              emit KNOWN_LIMITATION linux:systemd "systemd unavailable by design under Android/proot"
            fi

            printf 'SUMMARY PASS=%s WARN=%s FAIL=%s KNOWN_LIMITATION=%s\n' "@D@PASS" "@D@WARN" "@D@FAIL" "@D@KNOWN"
            echo "KFSHELL_COMPAT_SMOKE_END"
            rm -rf "@D@TMPROOT"
            if [ "@D@FAIL" -gt 0 ]; then
              exit 2
            fi
            exit 0
        """.trimIndent().replace("@D@", "$")

        return listOf(
            "cat > /tmp/kfshell-compat-smoke.sh <<'KFSHELL_COMPAT_SCRIPT'",
            script,
            "KFSHELL_COMPAT_SCRIPT",
            "bash /tmp/kfshell-compat-smoke.sh"
        ).joinToString("\n")
    }

    private fun appendAutomationLog(context: Context, text: String) {
        runCatching {
            val logDir = WorkSurfaceRuntimeBridge.getLogsDir(context).also { it.mkdirs() }
            val file = File(logDir, LOG_FILE)
            synchronized(logLock) {
                file.appendText("[${System.currentTimeMillis()}]\n$text\n")
            }
            Logger.i(LOG_TAG, "ADB automation log updated: ${file.absolutePath}")
        }.onFailure { error ->
            Logger.e(LOG_TAG, "Failed to write adb automation log: ${error.message}")
        }
    }

    private fun AutomationCommandResult.toLogBlock(): String {
        return buildString {
            appendLine("== $label ==")
            appendLine("exitCode=$exitCode timedOut=$timedOut durationMs=$durationMs")
            appendLine(output.take(OUTPUT_LIMIT))
        }
    }

    private fun AutomationCommandResult.summaryLine(): String {
        return output
            .lineSequence()
            .firstOrNull { it.startsWith("SUMMARY ") }
            ?: "exitCode=$exitCode timedOut=$timedOut"
    }

    private data class AutomationCommandResult(
        val label: String,
        val exitCode: Int,
        val timedOut: Boolean,
        val durationMs: Long,
        val output: String
    )

    private fun String.escapedPreview(maxLength: Int = PREVIEW_LIMIT): String {
        return take(maxLength)
            .replace("\\", "\\\\")
            .replace("\r", "\\r")
            .replace("\n", "\\n")
            .replace("\t", "\\t")
    }
}
