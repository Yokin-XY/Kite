package com.kite.app.platform.runtimemanagement

import android.content.Context
import com.kite.app.application.runtimemanagement.ProotEnvironmentIsolationResult
import com.kite.app.application.runtimemanagement.ProotViewAcceptanceCheck
import com.kite.app.application.runtimemanagement.ProotViewAcceptanceResult
import com.kite.app.application.runtimemanagement.ProotViewVerificationResult
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * 固定的 PRoot View 底层通用验收协议。
 *
 * 它只组合已经走真实普通启动链的单 View 夹具与双环境夹具，不直接读写 View catalog、活跃指针或实验文件。
 * 资源更新、多用户等产品能力不是本执行器的依赖；后续只需追加稳定检查项即可扩展验收覆盖。
 */
internal class ProotViewAcceptanceRunner(
    private val reportFile: File,
    private val runViewVerification: () -> ProotViewVerificationResult,
    private val runEnvironmentIsolation: () -> ProotEnvironmentIsolationResult,
    private val now: () -> Long,
    private val monotonicNanos: () -> Long,
) {
    constructor(context: Context) : this(
        reportFile = File(
            context.applicationContext.filesDir,
            "engineering/proot-view-acceptance/latest.json",
        ),
        runViewVerification = ProotViewLabRunner(context.applicationContext)::run,
        runEnvironmentIsolation = ProotEnvironmentIsolationRunner(context.applicationContext)::run,
        now = System::currentTimeMillis,
        monotonicNanos = System::nanoTime,
    )

    fun run(): ProotViewAcceptanceExecution {
        val startedAt = monotonicNanos()
        val viewStartedAt = monotonicNanos()
        val viewResult = runCatching(runViewVerification).getOrElse { error ->
            ProotViewVerificationResult(
                success = false,
                message = error.message ?: error.javaClass.simpleName,
                atUnixMs = now(),
            )
        }
        val viewMs = elapsedMs(viewStartedAt)

        val isolationStartedAt = monotonicNanos()
        val isolationResult = runCatching(runEnvironmentIsolation).getOrElse { error ->
            ProotEnvironmentIsolationResult(
                success = false,
                message = error.message ?: error.javaClass.simpleName,
                atUnixMs = now(),
            )
        }
        val isolationMs = elapsedMs(isolationStartedAt)
        val environmentPair = listOf(
            isolationResult.firstEnvironmentId,
            isolationResult.secondEnvironmentId,
        ).filter(String::isNotBlank).joinToString(" ↔ ").ifBlank { "环境身份不可用" }

        val result = ProotViewAcceptanceResult(
            checks = listOf(
                ProotViewAcceptanceCheck(
                    id = "ordinary_view",
                    title = "普通启动、CRUD 与 Upper 归属",
                    passed = viewResult.success,
                    detail = buildString {
                        append("环境=").append(viewResult.environmentId.ifBlank { "-" })
                        append("，view=").append(viewResult.viewId.ifBlank { "-" })
                        append("，runCount=").append(viewResult.runCount)
                        append("，耗时=").append(viewMs).append("ms")
                        if (viewResult.message.isNotBlank()) append("；").append(viewResult.message)
                    },
                ),
                ProotViewAcceptanceCheck(
                    id = "environment_rootfs_isolation",
                    title = "环境 rootfs 隔离",
                    passed = isolationResult.rootIsolated,
                    detail = environmentPair,
                ),
                ProotViewAcceptanceCheck(
                    id = "environment_workspace_isolation",
                    title = "环境工作区隔离",
                    passed = isolationResult.workspaceIsolated,
                    detail = environmentPair,
                ),
                ProotViewAcceptanceCheck(
                    id = "explicit_exchange_sharing",
                    title = "显式 exchange 共享",
                    passed = isolationResult.exchangeShared,
                    detail = environmentPair,
                ),
                ProotViewAcceptanceCheck(
                    id = "base_immutable",
                    title = "不可变 Base 未污染",
                    passed = isolationResult.baseUntouched,
                    detail = "双环境执行后检查原始 Base",
                ),
                ProotViewAcceptanceCheck(
                    id = "original_environment_restored",
                    title = "原活跃环境恢复",
                    passed = isolationResult.originalEnvironmentRestored,
                    detail = "隔离验收耗时=${isolationMs}ms",
                ),
            ),
            environmentId = viewResult.environmentId,
            viewId = viewResult.viewId,
            totalMs = elapsedMs(startedAt),
            atUnixMs = now(),
        )
        write(result)
        return ProotViewAcceptanceExecution(result, viewResult, isolationResult)
    }

    fun latest(): ProotViewAcceptanceResult? = runCatching {
        if (!reportFile.isFile) return null
        decode(JSONObject(reportFile.readText(Charsets.UTF_8)))
    }.getOrNull()

    private fun write(result: ProotViewAcceptanceResult) {
        require(reportFile.parentFile?.mkdirs() == true || reportFile.parentFile?.isDirectory == true) {
            "无法创建 View 验收报告目录"
        }
        val temp = File(reportFile.parentFile, ".${reportFile.name}.tmp-${System.nanoTime()}")
        FileOutputStream(temp).use { stream ->
            stream.write((encode(result).toString(2) + "\n").toByteArray(Charsets.UTF_8))
            stream.fd.sync()
        }
        try {
            Files.move(
                temp.toPath(),
                reportFile.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temp.toPath(), reportFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun encode(result: ProotViewAcceptanceResult): JSONObject = JSONObject()
        .put("schema", REPORT_SCHEMA)
        .put("environmentId", result.environmentId)
        .put("viewId", result.viewId)
        .put("totalMs", result.totalMs)
        .put("atUnixMs", result.atUnixMs)
        .put("checks", JSONArray().also { array ->
            result.checks.forEach { check ->
                array.put(JSONObject()
                    .put("id", check.id)
                    .put("title", check.title)
                    .put("passed", check.passed)
                    .put("detail", check.detail))
            }
        })

    private fun decode(json: JSONObject): ProotViewAcceptanceResult {
        require(json.getString("schema") == REPORT_SCHEMA) { "View 验收报告 schema 不支持" }
        val array = json.getJSONArray("checks")
        val checks = buildList {
            for (index in 0 until array.length()) {
                val check = array.getJSONObject(index)
                add(ProotViewAcceptanceCheck(
                    id = check.getString("id"),
                    title = check.getString("title"),
                    passed = check.getBoolean("passed"),
                    detail = check.optString("detail"),
                ))
            }
        }
        return ProotViewAcceptanceResult(
            checks = checks,
            environmentId = json.optString("environmentId"),
            viewId = json.optString("viewId"),
            totalMs = json.optLong("totalMs"),
            atUnixMs = json.optLong("atUnixMs"),
        )
    }

    private fun elapsedMs(startedAtNanos: Long): Long =
        ((monotonicNanos() - startedAtNanos).coerceAtLeast(0L) / 1_000_000L)

    private companion object {
        const val REPORT_SCHEMA = "kite_proot_view_acceptance_v1"
    }
}

internal data class ProotViewAcceptanceExecution(
    val result: ProotViewAcceptanceResult,
    val viewVerification: ProotViewVerificationResult,
    val environmentIsolation: ProotEnvironmentIsolationResult,
)
