package com.kite.app.bridge

import com.kite.app.recipe.KiteRecipe
import com.kite.app.recipe.KiteRunReport
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * T2 安全网:锁死 Kite Bridge 协议的消费端契约。
 *
 * KiteRunReport.fromJsonOrNull 是协议 JSON 进入 Kite 的唯一解析入口(KiteRecipe.kt:706),
 * 它的 status / ok / nextAction 语义就是协议文档(KITE_BRIDGE_PROTOCOL_V0_1.md §8/§9)的代码化身。
 * 这套测试把这 5 种协议响应(accepted/running/finished/failed/bridge_unavailable)+ nextAction
 * 跳转规则 + matchResult 成功判断 + 边界/错误路径全部钉死。
 *
 * 用 Robolectric 跑,因为 KiteRunReport.fromJson 内部用的是 Android 的 org.json.JSONObject
 * (纯 JUnit 下会 "not mocked")。
 *
 * 后续 P1/P2 重构(拆包名、抽 contracts、拆 Activity)都会触碰协议层,
 * 这套测试保证协议语义不被改坏。
 */
@RunWith(RobolectricTestRunner::class)
class KiteRunReportContractTest {

    // ------------------------------------------------------------------
    // 5 种协议状态解析
    // ------------------------------------------------------------------

    @Test
    fun `accepted 响应解析为 accepted 状态`() {
        val report = reportJson(status = "accepted", ok = true).parse()

        assertEquals(KiteRunReport.STATUS_ACCEPTED, report.status)
        assertTrue(report.ok)
    }

    @Test
    fun `running 响应解析为 running 状态`() {
        val report = reportJson(status = "running", ok = true).parse()

        assertEquals(KiteRunReport.STATUS_RUNNING, report.status)
    }

    @Test
    fun `already_running 响应解析为 already_running 状态`() {
        val report = reportJson(status = "already_running", ok = true).parse()

        assertEquals(KiteRunReport.STATUS_ALREADY_RUNNING, report.status)
    }

    @Test
    fun `finished 且 ok 响应解析为 finished 状态`() {
        val report = reportJson(status = "finished", ok = true).parse()

        assertEquals(KiteRunReport.STATUS_FINISHED, report.status)
        assertTrue(report.ok)
    }

    @Test
    fun `failed 响应解析为 failed 状态`() {
        val report = reportJson(status = "failed", ok = false).parse()

        assertEquals(KiteRunReport.STATUS_FAILED, report.status)
        assertFalse(report.ok)
    }

    @Test
    fun `stopped 响应解析为 stopped 状态`() {
        val report = reportJson(status = "stopped", ok = true).parse()

        assertEquals(KiteRunReport.STATUS_STOPPED, report.status)
    }

    @Test
    fun `缺失 status 字段时默认为 failed 而非崩溃`() {
        // 默认 STATUS_FAILED 是安全策略:无法识别状态时按失败处理,不假装成功
        val report = JSONObject("""{"requestId":"q","runId":"r","recipeId":"r1","ok":false}""").parse()

        assertEquals(KiteRunReport.STATUS_FAILED, report.status)
    }

    @Test
    fun `缺失 ok 字段时默认为 false`() {
        val report = JSONObject("""{"requestId":"q","runId":"r","recipeId":"r1","status":"finished"}""").parse()

        // ok 缺省 false:不能因为没说 ok 就当成功
        assertEquals(KiteRunReport.STATUS_FINISHED, report.status)
        assertFalse(report.ok)
    }

    // ------------------------------------------------------------------
    // finished + nextAction 跳转(协议核心:成功完成后才打开网页)
    // ------------------------------------------------------------------

    @Test
    fun `finished 且 ok 且 nextAction 为 open_web 时返回跳转 url`() {
        val report = reportJson(
            status = "finished",
            ok = true,
            nextAction = nextActionJson(type = KiteRecipe.STEP_OPEN_WEB, url = "http://127.0.0.1:8648")
        ).parse()

        assertEquals("http://127.0.0.1:8648", report.openWebUrlIfFinished())
    }

    @Test
    fun `finished 但 ok 为 false 时不返回跳转 url`() {
        // 失败完成不应自动跳转网页
        val report = reportJson(
            status = "finished",
            ok = false,
            nextAction = nextActionJson(type = KiteRecipe.STEP_OPEN_WEB, url = "http://127.0.0.1:8648")
        ).parse()

        assertNull(report.openWebUrlIfFinished())
    }

    @Test
    fun `running 状态即使带 open_web nextAction 也不通过 finished 入口跳转`() {
        val report = reportJson(
            status = "running",
            ok = true,
            nextAction = nextActionJson(type = KiteRecipe.STEP_OPEN_WEB, url = "http://127.0.0.1:8648")
        ).parse()

        assertNull(report.openWebUrlIfFinished())
    }

    @Test
    fun `openWebUrlIfPresent 不要求 finished 状态,只要有 open_web 即返回`() {
        val report = reportJson(
            status = "running",
            ok = true,
            nextAction = nextActionJson(type = KiteRecipe.STEP_OPEN_WEB, url = "http://127.0.0.1:9999")
        ).parse()

        assertEquals("http://127.0.0.1:9999", report.openWebUrlIfPresent())
    }

    @Test
    fun `openWebUrlIfPresent 对空 url 返回 null`() {
        val report = reportJson(
            status = "running",
            ok = true,
            nextAction = nextActionJson(type = KiteRecipe.STEP_OPEN_WEB, url = "")
        ).parse()

        assertNull(report.openWebUrlIfPresent())
    }

    @Test
    fun `无 nextAction 时两个 openWeb 入口都返回 null`() {
        val report = reportJson(status = "finished", ok = true).parse()

        assertNull(report.openWebUrlIfFinished())
        assertNull(report.openWebUrlIfPresent())
    }

    // ------------------------------------------------------------------
    // matchResult 成功判断(协议的"成功判断"机制)
    // ------------------------------------------------------------------

    @Test
    fun `hasMismatch 在任一 enabled step 未匹配时返回 true`() {
        val report = reportJson(
            status = "finished",
            ok = true,
            steps = arrayOf(
                stepReportJson(stepId = "s1", matched = true, enabled = true),
                stepReportJson(stepId = "s2", matched = false, enabled = true)
            )
        ).parse()

        assertTrue("s2 启用匹配但未匹配 → 整体 mismatch", report.hasMismatch())
    }

    @Test
    fun `hasMismatch 在所有 enabled step 都匹配时返回 false`() {
        val report = reportJson(
            status = "finished",
            ok = true,
            steps = arrayOf(
                stepReportJson(stepId = "s1", matched = true, enabled = true),
                stepReportJson(stepId = "s2", matched = false, enabled = false)
            )
        ).parse()

        assertFalse("s2 未匹配但未 enabled → 不算 mismatch", report.hasMismatch())
    }

    @Test
    fun `hasMismatch 无 step 时返回 false`() {
        val report = reportJson(status = "finished", ok = true).parse()

        assertFalse(report.hasMismatch())
    }

    // ------------------------------------------------------------------
    // lastMeaningfulOutput 提取(报告摘要来源)
    // ------------------------------------------------------------------

    @Test
    fun `lastMeaningfulOutput 逆向取首个非空 output`() {
        val report = reportJson(
            status = "finished",
            ok = true,
            steps = arrayOf(
                stepReportJson(stepId = "s1", lastMeaningfulOutput = "first"),
                stepReportJson(stepId = "s2", lastMeaningfulOutput = "second")
            )
        ).parse()

        assertEquals("second", report.lastMeaningfulOutput())
    }

    @Test
    fun `lastMeaningfulOutput 回退到 stderr 再到 stdout`() {
        val report = reportJson(
            status = "failed",
            ok = false,
            steps = arrayOf(
                stepReportJson(stepId = "s1", stdoutTail = "out", stderrTail = "err")
            )
        ).parse()

        assertEquals("err", report.lastMeaningfulOutput())
    }

    @Test
    fun `lastMeaningfulOutput 全空时返回 null`() {
        val report = reportJson(status = "finished", ok = true).parse()

        assertNull(report.lastMeaningfulOutput())
    }

    // ------------------------------------------------------------------
    // 错误路径:非法 JSON
    // ------------------------------------------------------------------

    @Test
    fun `fromJsonOrNull 对非 JSON 文本返回 null 而非抛异常`() {
        assertNull(KiteRunReport.fromJsonOrNull("not a json"))
    }

    @Test
    fun `fromJsonOrNull 对空字符串返回 null`() {
        assertNull(KiteRunReport.fromJsonOrNull(""))
    }

    @Test
    fun `fromJsonOrNull 对合法 JSON 数组返回 null`() {
        // 协议要求顶层是对象;数组不是合法 report
        assertNull(KiteRunReport.fromJsonOrNull("[1,2,3]"))
    }

    // ------------------------------------------------------------------
    // runId 回退契约
    // ------------------------------------------------------------------

    @Test
    fun `runId 缺失时回退到 requestId`() {
        val report = JSONObject(
            """{"requestId":"req-123","recipeId":"r1","status":"accepted","ok":true}"""
        ).parse()

        assertEquals("req-123", report.runId)
    }

    @Test
    fun `pid 等进程字段被正确解析`() {
        val report = KiteRunReport.fromJsonOrNull(
            """{"protocolVersion":1,"requestId":"req-test","runId":"run-test","""
                + """"recipeId":"recipe-test","status":"running","ok":true,"""
                + """"pid":"100","rootPid":"90","processGroupId":"90","systemSessionId":"sess-1"}"""
        )!!

        assertEquals("100", report.pid)
        assertEquals("90", report.rootPid)
        assertEquals("sess-1", report.systemSessionId)
    }

    // ------------------------------------------------------------------
    // 测试夹具
    // ------------------------------------------------------------------

    /** 构造一个合法的 KiteRunReport JSON 并解析。 */
    private fun reportJson(
        status: String,
        ok: Boolean,
        nextAction: JSONObject? = null,
        steps: Array<JSONObject> = emptyArray()
    ): JSONObject = JSONObject().apply {
        put("protocolVersion", 1)
        put("requestId", "req-test")
        put("runId", "run-test")
        put("recipeId", "recipe-test")
        put("status", status)
        put("ok", ok)
        put("steps", JSONArray().apply { steps.forEach { put(it) } })
        nextAction?.let { put("nextAction", it) }
    }

    private fun JSONObject.parse(): KiteRunReport =
        KiteRunReport.fromJsonOrNull(this.toString()) ?: error("测试夹具 JSON 解析失败")

    private fun nextActionJson(type: String, url: String): JSONObject =
        JSONObject().apply {
            put("type", type)
            if (url.isNotBlank()) put("url", url)
        }

    private fun stepReportJson(
        stepId: String = "step",
        lastMeaningfulOutput: String = "",
        stdoutTail: String = "",
        stderrTail: String = "",
        matched: Boolean = false,
        enabled: Boolean = false
    ): JSONObject = JSONObject().apply {
        put("stepId", stepId)
        put("type", "shell")
        put("status", "finished")
        put("lastMeaningfulOutput", lastMeaningfulOutput)
        put("stdoutTail", stdoutTail)
        put("stderrTail", stderrTail)
        put("matchResult", JSONObject().apply {
            put("enabled", enabled)
            put("matched", matched)
        })
    }
}
