package com.kite.app.platform.runtimemanagement

import android.content.Context
import com.kite.app.application.runtimemanagement.ProotViewVerificationResult
import com.kite.app.foundation.runtime.KFContainerManager
import com.kite.app.foundation.runtime.ProotViewBinding
import com.kite.app.foundation.runtime.ProotViewStore
import com.kite.app.foundation.runtime.RuntimeBoundary
import com.kite.app.foundation.workspace.WorkSurfaceRuntimeBridge
import org.json.JSONObject
import java.io.File

/**
 * 普通 Ubuntu View 离线验证执行器（T014e）。
 *
 * 从工程页触发，走与首页卡片/终端相同的普通 PRoot 启动链（buildContainerArgvExecConfig），
 * 不调用 ResourceTransactionCoordinator。脚本随 APK 提供，不联网、不安装第三方依赖。
 * 执行后校验：原始 Base 的 /root/.kite-view-lab 仍不存在、当前活跃 View Upper 有分配、报告环境和
 * viewId 等于状态拥有者 active。不得通过修改报告文本伪造成功。
 */
internal class ProotViewLabRunner(context: Context) {
    private val appContext = context.applicationContext

    fun run(): ProotViewVerificationResult {
        val container = WorkSurfaceRuntimeBridge.getSavedContainer(appContext)
            ?: return failure("容器未就绪")
        // 1. stage 脚本到共享宿主目录（.kf/system/state），不进环境变化目录。
        val scriptHost = stageScript(container.workspacePath)
        // 容器内脚本路径：workspace bind 到 /workspace。
        val containerScript = "/workspace/.kf/system/state/kite-view-lab/kite_view_lab.sh"
        // 2. 走普通 PRoot 启动链执行（argv 形式，绑定当前活跃 View）。
        val config = KFContainerManager.buildContainerArgvExecConfig(
            context = appContext,
            workingDirectory = RuntimeBoundary.CONTAINER_ROOT_HOME,
            argv = listOf("/bin/sh", containerScript),
        )
        val process = ProcessBuilder(config.command)
            .redirectErrorStream(true)
            .apply { environment().putAll(config.env) }
            .start()
        val output = StringBuilder()
        val reader = Thread({
            runCatching {
                process.inputStream.bufferedReader().useLines { lines ->
                    lines.forEach { output.append(it).append('\n') }
                }
            }
        }, "KiteViewLabReader").apply { isDaemon = true; start() }
        val completed = process.waitFor(TIMEOUT_MS, java.util.concurrent.TimeUnit.MILLISECONDS)
        if (!completed) {
            process.destroyForcibly()
            reader.join(500L)
            return failure("验证脚本执行超时")
        }
        reader.join(1_000L)
        val exitCode = process.exitValue()
        // 3. 解析 stdout 末尾的 JSON 报告。
        val report = parseReport(output.toString())
            ?: return failure("无法解析报告，exitCode=$exitCode, output=${output.take(200)}")
        // 4. 校验：报告身份与 current 一致；Base 不被污染；Upper 有分配。
        val violations = verify(container, report)
        val success = report.optBoolean("success", false) && exitCode == 0 && violations.isEmpty()
        return ProotViewVerificationResult(
            success = success,
            runCount = report.optLong("runCount", 0L),
            viewId = report.optString("viewId"),
            environmentId = report.optString("environmentId"),
            fileSha256 = report.optString("fileSha256"),
            message = if (violations.isEmpty()) report.optString("message") else violations.joinToString("; "),
            atUnixMs = report.optLong("atUnixMs", System.currentTimeMillis()),
        )
    }

    private fun stageScript(workspacePath: String): File {
        val dir = File(workspacePath, ".kf/system/state/kite-view-lab").apply { mkdirs() }
        val target = File(dir, "kite_view_lab.sh")
        appContext.assets.open(ASSET_PATH).use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        }
        return target
    }

    internal fun parseReport(raw: String): JSONObject? {
        // 脚本把 JSON 打印到 stdout 末尾；取最后一个 { ... } 行。
        val lines = raw.lineSequence().filter { it.trimStart().startsWith("{") }.toList()
        for (line in lines.asReversed()) {
            runCatching { return JSONObject(line) }.getOrNull()
        }
        return runCatching { JSONObject(raw.substringAfterLast("\n{", raw).trim()) }.getOrNull()
    }

    private fun verify(
        container: com.kite.app.foundation.contracts.ContainerRecord,
        report: JSONObject,
    ): List<String> {
        val violations = mutableListOf<String>()
        // 严格校验报告字段契约。
        violations += validateReportFields(report)
        val runCount = report.optLong("runCount", 0L)
        val store = runCatching { ProotViewStore.forContainer(container) }.getOrNull()
            ?: return violations + listOf("View store 不可用")
        val current = runCatching { store.activeBinding() }.getOrNull()
            ?: return violations + listOf("active binding 不可用")
        if (report.optString("viewId") != current.viewId) {
            violations += "报告 viewId 与 current 不一致：${report.optString("viewId")} != ${current.viewId}"
        }
        if (report.optString("environmentId") != current.environmentId) {
            violations += "报告 environmentId 与 active 不一致：${report.optString("environmentId")} != ${current.environmentId}"
        }
        // 原始 Base 的 /root/.kite-view-lab 必须仍不存在（变化落在 View Upper，不污染 Base）。
        val baseLabDir = File(container.rootfsPath, "root/.kite-view-lab")
        if (baseLabDir.exists()) {
            violations += "原始 Base 的 /root/.kite-view-lab 不应存在（Base 被污染）"
        }
        // 证明本次实验变化属于当前 View Upper：用 baseRootPath.relativize(baseLabDir) 计算实验目录
        // 在 Base 内的相对路径，再拼到 upperRootPath。不写死 ubuntu-main/containers 层级。
        val upperLabDir = resolveUpperLabDir(current, container)
        if (upperLabDir == null) {
            violations += "实验目录不在 Base/rootfs scope 内，无法定位 Upper 证据"
        } else if (!upperLabDir.isDirectory) {
            violations += "当前 View Upper 未记录实验目录：${upperLabDir.absolutePath}"
        } else {
            val stateFile = File(upperLabDir, "state.json")
            val detFile = File(upperLabDir, "deterministic.bin")
            if (!stateFile.isFile) violations += "当前 View Upper 缺少 state.json"
            if (!detFile.isFile) violations += "当前 View Upper 缺少 deterministic.bin"
            // 确认 upper 记录的 runCount 与报告一致（证明是本次写入）。
            runCatching {
                val upperState = JSONObject(stateFile.readText())
                if (upperState.optLong("runCount", 0L) != runCount) {
                    violations += "Upper runCount 与报告不一致"
                }
            }.onFailure { violations += "Upper state.json 不可读：${it.message}" }
        }
        return violations
    }

    private fun failure(message: String) = ProotViewVerificationResult(
        success = false, message = message, atUnixMs = System.currentTimeMillis(),
    )

    companion object {
        private const val ASSET_PATH = "engineering/kite-view-lab/kite_view_lab.sh"
        private const val TIMEOUT_MS = 60_000L
        private const val REPORT_SCHEMA = "kite_view_lab_report_v1"

        /** 严格校验报告字段契约；返回违规列表（空表示通过）。纯函数，可独立测试。 */
        internal fun validateReportFields(report: JSONObject): List<String> {
            val violations = mutableListOf<String>()
            if (report.optString("schema") != REPORT_SCHEMA) {
                violations += "报告 schema 不符：${report.optString("schema")}"
            }
            if (!report.optBoolean("crudOk")) {
                violations += "crudOk 为 false（CRUD 验证失败）"
            }
            if (!report.optBoolean("labDirExists")) {
                violations += "labDirExists 为 false（实验目录未创建）"
            }
            if (report.optLong("runCount", 0L) < 1L) {
                violations += "runCount 异常：${report.optLong("runCount", 0L)}"
            }
            val fileSha = report.optString("fileSha256")
            if (fileSha.length != 64 || !fileSha.all { it in "0123456789abcdef" }) {
                violations += "fileSha256 非合法 hex64：$fileSha"
            }
            if (report.optString("environmentId").isBlank()) {
                violations += "报告 environmentId 为空"
            }
            return violations
        }

        /**
         * 计算 /root/.kite-view-lab 在当前 View Upper 的宿主路径。
         *
         * baseRoot = runtime；rootfs = runtime/containers/<id>/rootfs；
         * 实验目录 = runtime/containers/<id>/rootfs/root/.kite-view-lab。
         * relativize 得到 containers/<id>/rootfs/root/.kite-view-lab，拼到 upperRootPath。
         * 越过 baseRoot 或不属于 rootfs scope 时返回 null（越界立即失败）。不写死层级名。
         */
        internal fun resolveUpperLabDir(
            current: ProotViewBinding,
            container: com.kite.app.foundation.contracts.ContainerRecord,
        ): File? {
            val baseRoot = current.baseRootPath.let(::File).absoluteFile.toPath().normalize()
            val rootfsDir = File(container.rootfsPath).absoluteFile.toPath().normalize()
            val labDir = File(rootfsDir.toFile(), "root/.kite-view-lab").toPath().normalize()
            // 实验目录必须在 Base 内。
            if (!labDir.startsWith(baseRoot)) return null
            // 必须属于 rootfs scope（rootfs 或其子目录），不能越界到其他 scope。
            if (!labDir.startsWith(rootfsDir)) return null
            val relative = baseRoot.relativize(labDir)
            return File(current.upperRootPath).absoluteFile.toPath().normalize()
                .resolve(relative).toFile()
        }
    }
}
