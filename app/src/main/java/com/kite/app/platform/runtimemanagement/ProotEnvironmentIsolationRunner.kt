package com.kite.app.platform.runtimemanagement

import android.content.Context
import com.kite.app.application.runtimemanagement.ProotEnvironmentIsolationResult
import com.kite.app.foundation.contracts.ContainerRecord
import com.kite.app.foundation.runtime.KFContainerManager
import com.kite.app.foundation.runtime.ProotEnvironmentWorkspace
import com.kite.app.foundation.runtime.ProotViewBinding
import com.kite.app.foundation.runtime.ProotViewStore
import com.kite.app.foundation.runtime.RuntimeBoundary
import com.kite.app.foundation.workspace.WorkSurfaceRuntimeBridge
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * 双环境离线验收夹具。
 *
 * 它只调用正式的环境 manager 与普通 PRoot 启动链；不显式指定 viewId/environmentId，也不直接改 active 指针。
 * 夹具在两个环境的同一路径写不同标记，验证 rootfs 与 workspace 隔离、/exchange 共享、Base 不污染，最后恢复
 * 触发前的活跃环境。实验文件只落在 `.kite-environment-lab` 专用目录。
 */
internal class ProotEnvironmentIsolationRunner(
    context: Context,
    private val containerProvider: () -> ContainerRecord? = {
        WorkSurfaceRuntimeBridge.getSavedContainer(context.applicationContext)
    },
    private val managerProvider: (() -> ProotEnvironmentManager)? = null,
) {
    private val appContext = context.applicationContext

    fun run(): ProotEnvironmentIsolationResult {
        val startedAt = System.currentTimeMillis()
        val container = containerProvider() ?: return failure("容器未就绪", startedAt)
        val store = runCatching { ProotViewStore.forContainer(container).also { it.ensureInitialized() } }
            .getOrElse { return failure(it.message ?: "View store 不可用", startedAt) }
        val manager = managerProvider?.invoke() ?: ProotEnvironmentManager(
            containerProvider = { container },
            storeProvider = { store },
        )
        val originalEnvironmentId = runCatching { store.activeEnvironmentId() }
            .getOrElse { return failure(it.message ?: "活跃环境不可用", startedAt) }
        val secondEnvironmentId = runCatching { ensureSecondEnvironment(store, manager) }
            .getOrElse { return failure(it.message ?: "无法准备第二环境", startedAt) }
        val markerId = UUID.randomUUID().toString()
        val firstMarker = "default:$markerId"
        val secondMarker = "$secondEnvironmentId:$markerId"
        val sharedMarker = "exchange:$markerId"

        var result = runCatching {
            stageScript(container)

            val firstBinding = manager.switchEnvironment(ProotViewStore.DEFAULT_ENVIRONMENT_ID).getOrThrow()
            execute(firstBinding, listOf("write", firstMarker, sharedMarker))

            val secondBinding = manager.switchEnvironment(secondEnvironmentId).getOrThrow()
            val secondBefore = execute(secondBinding, listOf("read"))
            execute(secondBinding, listOf("write", secondMarker, "-"))
            val secondAfter = execute(secondBinding, listOf("read"))

            val firstAgain = manager.switchEnvironment(ProotViewStore.DEFAULT_ENVIRONMENT_ID).getOrThrow()
            val firstAfter = execute(firstAgain, listOf("read"))

            val baseUntouched = !File(container.rootfsPath, "root/.kite-environment-lab").exists()
            val evidence = evaluateEnvironmentIsolation(
                firstMarker = firstMarker,
                secondMarker = secondMarker,
                sharedMarker = sharedMarker,
                secondBefore = secondBefore,
                secondAfter = secondAfter,
                firstAfter = firstAfter,
                firstHostWorkspaceMatches = hostWorkspaceEvidence(container, firstBinding, firstMarker),
                secondHostWorkspaceMatches = hostWorkspaceEvidence(container, secondBinding, secondMarker),
                baseUntouched = baseUntouched,
            )
            ProotEnvironmentIsolationResult(
                success = evidence.success,
                firstEnvironmentId = ProotViewStore.DEFAULT_ENVIRONMENT_ID,
                secondEnvironmentId = secondEnvironmentId,
                rootIsolated = evidence.rootIsolated,
                workspaceIsolated = evidence.workspaceIsolated,
                exchangeShared = evidence.exchangeShared,
                baseUntouched = evidence.baseUntouched,
                message = buildMessage(
                    evidence.rootIsolated,
                    evidence.workspaceIsolated,
                    evidence.exchangeShared,
                    evidence.baseUntouched,
                ),
                atUnixMs = System.currentTimeMillis(),
            )
        }.getOrElse { error ->
            failure(error.message ?: error.javaClass.simpleName, startedAt).copy(
                firstEnvironmentId = ProotViewStore.DEFAULT_ENVIRONMENT_ID,
                secondEnvironmentId = secondEnvironmentId,
            )
        }

        val restored = manager.switchEnvironment(originalEnvironmentId).isSuccess
        if (!restored) {
            result = result.copy(
                success = false,
                originalEnvironmentRestored = false,
                message = listOf(result.message, "未能恢复原活跃环境：$originalEnvironmentId")
                    .filter { it.isNotBlank() }
                    .joinToString("；"),
            )
        } else {
            result = result.copy(originalEnvironmentRestored = true)
        }
        return result
    }

    private fun ensureSecondEnvironment(
        store: ProotViewStore,
        manager: ProotEnvironmentManager,
    ): String {
        store.environmentCurrents().keys
            .filter { it != ProotViewStore.DEFAULT_ENVIRONMENT_ID }
            .sorted()
            .firstOrNull()
            ?.let { return it }
        var ordinal = 2
        val existing = store.environmentCurrents().keys
        while ("profile_$ordinal" in existing) ordinal += 1
        return manager.createEnvironment("profile_$ordinal").getOrThrow().environmentId
    }

    private fun stageScript(container: ContainerRecord) {
        val target = File(
            container.workspacePath,
            ".kf/system/state/kite-environment-lab/kite_environment_lab.sh",
        )
        require(target.parentFile?.mkdirs() == true || target.parentFile?.isDirectory == true) {
            "无法创建环境夹具目录"
        }
        appContext.assets.open(ASSET_PATH).use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        }
    }

    private fun execute(
        expectedBinding: ProotViewBinding,
        arguments: List<String>,
    ): EnvironmentIsolationObservation {
        val config = KFContainerManager.buildContainerArgvExecConfig(
            context = appContext,
            workingDirectory = RuntimeBoundary.CONTAINER_ROOT_HOME,
            argv = environmentLabArgv(arguments),
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
        }, "KiteEnvironmentLabReader").apply { isDaemon = true; start() }
        val completed = process.waitFor(TIMEOUT_MS, TimeUnit.MILLISECONDS)
        if (!completed) {
            process.destroyForcibly()
            reader.join(500L)
            error("环境夹具执行超时")
        }
        reader.join(1_000L)
        require(process.exitValue() == 0) {
            "环境夹具退出码 ${process.exitValue()}：${output.take(240)}"
        }
        val report = parseReport(output.toString()) ?: error("环境夹具报告不可解析：${output.take(240)}")
        val observation = observation(report)
        require(observation.environmentId == expectedBinding.environmentId) {
            "夹具环境身份漂移：${observation.environmentId} != ${expectedBinding.environmentId}"
        }
        require(observation.viewId == expectedBinding.viewId) {
            "夹具 View 身份漂移：${observation.viewId} != ${expectedBinding.viewId}"
        }
        return observation
    }

    private fun hostWorkspaceEvidence(
        container: ContainerRecord,
        binding: ProotViewBinding,
        expected: String,
    ): Boolean {
        val workspace = ProotEnvironmentWorkspace.plan(container, binding).workspaceDirectory
        return File(workspace, ".kite-environment-lab/private.txt")
            .takeIf { it.isFile }
            ?.readText() == expected
    }

    private fun parseReport(raw: String): JSONObject? = raw.lineSequence()
        .filter { it.trimStart().startsWith("{") }
        .mapNotNull { runCatching { JSONObject(it) }.getOrNull() }
        .lastOrNull()

    private fun observation(report: JSONObject): EnvironmentIsolationObservation {
        require(report.optString("schema") == REPORT_SCHEMA) { "环境夹具报告 schema 不符" }
        return EnvironmentIsolationObservation(
            environmentId = report.optString("environmentId"),
            viewId = report.optString("viewId"),
            rootValue = report.nullableString("rootValue"),
            workspaceValue = report.nullableString("workspaceValue"),
            exchangeValue = report.nullableString("exchangeValue"),
        )
    }

    private fun JSONObject.nullableString(name: String): String? =
        if (isNull(name)) null else optString(name)

    private fun buildMessage(
        rootIsolated: Boolean,
        workspaceIsolated: Boolean,
        exchangeShared: Boolean,
        baseUntouched: Boolean,
    ): String = listOf(
        "rootfs 隔离=${if (rootIsolated) "通过" else "失败"}",
        "workspace 隔离=${if (workspaceIsolated) "通过" else "失败"}",
        "/exchange 共享=${if (exchangeShared) "通过" else "失败"}",
        "Base 不污染=${if (baseUntouched) "通过" else "失败"}",
    ).joinToString("；")

    private fun failure(message: String, atUnixMs: Long) = ProotEnvironmentIsolationResult(
        success = false,
        message = message,
        atUnixMs = atUnixMs,
    )

    private companion object {
        const val ASSET_PATH = "engineering/kite-environment-lab/kite_environment_lab.sh"
        const val REPORT_SCHEMA = "kite_environment_lab_report_v1"
        const val TIMEOUT_MS = 60_000L
    }
}

internal fun environmentLabArgv(arguments: List<String>): List<String> =
    listOf("/bin/sh", "/workspace/.kf/system/state/kite-environment-lab/kite_environment_lab.sh") + arguments

internal data class EnvironmentIsolationObservation(
    val environmentId: String,
    val viewId: String,
    val rootValue: String?,
    val workspaceValue: String?,
    val exchangeValue: String?,
)

internal data class EnvironmentIsolationEvidence(
    val rootIsolated: Boolean,
    val workspaceIsolated: Boolean,
    val exchangeShared: Boolean,
    val baseUntouched: Boolean,
) {
    val success: Boolean
        get() = rootIsolated && workspaceIsolated && exchangeShared && baseUntouched
}

internal fun evaluateEnvironmentIsolation(
    firstMarker: String,
    secondMarker: String,
    sharedMarker: String,
    secondBefore: EnvironmentIsolationObservation,
    secondAfter: EnvironmentIsolationObservation,
    firstAfter: EnvironmentIsolationObservation,
    firstHostWorkspaceMatches: Boolean,
    secondHostWorkspaceMatches: Boolean,
    baseUntouched: Boolean,
): EnvironmentIsolationEvidence = EnvironmentIsolationEvidence(
    rootIsolated = secondBefore.rootValue != firstMarker &&
        secondAfter.rootValue == secondMarker &&
        firstAfter.rootValue == firstMarker &&
        firstAfter.rootValue != secondMarker,
    workspaceIsolated = secondBefore.workspaceValue != firstMarker &&
        secondAfter.workspaceValue == secondMarker &&
        firstAfter.workspaceValue == firstMarker &&
        firstAfter.workspaceValue != secondMarker &&
        firstHostWorkspaceMatches && secondHostWorkspaceMatches,
    exchangeShared = secondBefore.exchangeValue == sharedMarker &&
        secondAfter.exchangeValue == sharedMarker &&
        firstAfter.exchangeValue == sharedMarker,
    baseUntouched = baseUntouched,
)
