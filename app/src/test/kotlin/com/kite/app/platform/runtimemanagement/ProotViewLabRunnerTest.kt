package com.kite.app.platform.runtimemanagement

import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * T014e 离线验证夹具报告解析契约测试。
 *
 * 端到端执行（普通 PRoot 启动链 + Base 不污染 + Upper 分配）需真机，不在单测范围；
 * 这里只验证 stdout 报告解析的健壮性与契约字段。
 */
@RunWith(RobolectricTestRunner::class)
class ProotViewLabRunnerTest {
    private val runner = ProotViewLabRunner(ApplicationProvider.getApplicationContext())

    @Test
    fun parsesTrailingJsonObjectFromScriptOutput() {
        // 脚本用 print(json.dumps(report)) 输出单行 JSON 到 stdout 末尾。
        val raw = "KF view lab starting\n" +
            "{\"schema\":\"kite_view_lab_report_v1\",\"success\":true,\"runCount\":3}\n"
        val report = runner.parseReport(raw)
        assertTrue(report != null)
        assertEquals("kite_view_lab_report_v1", report?.optString("schema"))
        assertEquals(true, report?.optBoolean("success"))
        assertEquals(3L, report?.optLong("runCount"))
    }

    @Test
    fun parsesSingleLineJsonObject() {
        val raw = """{"schema":"kite_view_lab_report_v1","success":true,"runCount":1,"fileSha256":"abc"}"""
        val report = runner.parseReport(raw)
        assertEquals("abc", report?.optString("fileSha256"))
    }

    @Test
    fun returnsNullForNonJsonOutput() {
        val raw = "some stderr noise without json"
        val report = runner.parseReport(raw)
        assertNull(report)
    }

    @Test
    fun reportSchemaContractIsStable() {
        // 验证脚本输出的报告字段契约（与 ProotViewVerificationResult 对齐）。
        val sample = """{"schema":"kite_view_lab_report_v1","success":true,"runCount":2,"fileSha256":"deadbeef","crudOk":true,"environmentId":"default","viewId":"view-1","pythonAvailable":true,"nodeAvailable":false,"workingDirectory":"/root","labDirExists":true,"exitCode":0,"atUnixMs":123}"""
        val report = runner.parseReport(sample)!!
        assertEquals("kite_view_lab_report_v1", report.optString("schema"))
        assertTrue(report.optBoolean("success"))
        assertEquals("default", report.optString("environmentId"))
        assertFalse(report.optBoolean("nodeAvailable"))
    }

    @Test
    fun validateReportFieldsAcceptsLegalReport() {
        val report = JSONObject(
            """{"schema":"kite_view_lab_report_v1","crudOk":true,"labDirExists":true,"runCount":3,""" +
                """"fileSha256":"${"a".repeat(64)}","environmentId":"default"}"""
        )
        assertEquals(emptyList<String>(), ProotViewLabRunner.validateReportFields(report))
        val nonDefault = JSONObject(
            """{"schema":"kite_view_lab_report_v1","crudOk":true,"labDirExists":true,"runCount":3,""" +
                """"fileSha256":"${"b".repeat(64)}","environmentId":"profile_2"}"""
        )
        assertEquals(emptyList<String>(), ProotViewLabRunner.validateReportFields(nonDefault))
    }

    @Test
    fun validateReportFieldsDetectsEachViolation() {
        // schema 错误。
        assertEquals(
            listOf("报告 schema 不符：wrong"),
            ProotViewLabRunner.validateReportFields(JSONObject("""{"schema":"wrong","crudOk":true,"labDirExists":true,"runCount":1,"fileSha256":"${"0".repeat(64)}","environmentId":"default"}"""))
        )
        // crudOk false。
        val crudViolations = ProotViewLabRunner.validateReportFields(
            JSONObject("""{"schema":"kite_view_lab_report_v1","crudOk":false,"labDirExists":true,"runCount":1,"fileSha256":"${"0".repeat(64)}","environmentId":"default"}""")
        )
        assertTrue(crudViolations.any { it.contains("crudOk") })
        // labDirExists false。
        val labViolations = ProotViewLabRunner.validateReportFields(
            JSONObject("""{"schema":"kite_view_lab_report_v1","crudOk":true,"labDirExists":false,"runCount":1,"fileSha256":"${"0".repeat(64)}","environmentId":"default"}""")
        )
        assertTrue(labViolations.any { it.contains("labDirExists") })
        // runCount 0。
        val runViolations = ProotViewLabRunner.validateReportFields(
            JSONObject("""{"schema":"kite_view_lab_report_v1","crudOk":true,"labDirExists":true,"runCount":0,"fileSha256":"${"0".repeat(64)}","environmentId":"default"}""")
        )
        assertTrue(runViolations.any { it.contains("runCount") })
        // fileSha256 非法（长度不足）。
        val shaViolations = ProotViewLabRunner.validateReportFields(
            JSONObject("""{"schema":"kite_view_lab_report_v1","crudOk":true,"labDirExists":true,"runCount":1,"fileSha256":"deadbeef","environmentId":"default"}""")
        )
        assertTrue(shaViolations.any { it.contains("fileSha256") })
        // 环境身份为空。
        val envViolations = ProotViewLabRunner.validateReportFields(
            JSONObject("""{"schema":"kite_view_lab_report_v1","crudOk":true,"labDirExists":true,"runCount":1,"fileSha256":"${"0".repeat(64)}","environmentId":""}""")
        )
        assertTrue(envViolations.any { it.contains("environmentId") })
    }
}
