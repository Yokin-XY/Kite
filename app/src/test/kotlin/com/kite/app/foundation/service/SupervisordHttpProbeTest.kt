package com.kite.app.foundation.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 锁定 XML-RPC 响应解析与 supervisorctl 兼容行的合同。 */
class SupervisordHttpProbeTest {
    @Test
    fun `parses process structs into supervisorctl compatible lines`() {
        val payload = """
            <?xml version="1.0"?>
            <methodResponse><params><param><value><array><data>
            <value><struct>
              <member><name>name</name><value><string>gateway</string></value></member>
              <member><name>state</name><value><int>10</int></value></member>
              <member><name>description</name><value><string>pid 4321, uptime 0:01:23</string></value></member>
            </struct></value>
            <value><struct>
              <member><name>name</name><value><string>mcp-server</string></value></member>
              <member><name>statename</name><value><string>FATAL</string></value></member>
              <member><name>state</name><value><int>200</int></value></member>
              <member><name>description</name><value><string>exited too quickly (process log may have details)</string></value></member>
            </struct></value>
            </data></array></value></param></params></methodResponse>
        """.trimIndent()

        val lines = SupervisordHttpProbeTestAccess.parseProcessStructs(payload)

        assertEquals(
            listOf(
                "gateway RUNNING pid 4321, uptime 0:01:23",
                "mcp-server FATAL exited too quickly (process log may have details)",
            ),
            lines,
        )
    }

    @Test
    fun `numeric state codes map to supervisorctl state names`() {
        val payload = """
            <methodResponse><params><param><value><array><data>
            <value><struct>
              <member><name>name</name><value><string>worker</string></value></member>
              <member><name>state</name><value><int>40</int></value></member>
              <member><name>description</name><value><string>Nov 01 10:00 PM exited normally</string></value></member>
            </struct></value>
            </data></array></value></param></params></methodResponse>
        """.trimIndent()

        val lines = SupervisordHttpProbeTestAccess.parseProcessStructs(payload)

        assertEquals(1, lines.size)
        assertTrue(lines[0].startsWith("worker EXITED "))
    }

    @Test
    fun `struct without name is skipped and malformed payload yields empty list`() {
        assertEquals(
            emptyList<String>(),
            SupervisordHttpProbeTestAccess.parseProcessStructs("<data><value>42</value></data>"),
        )
    }
}

/** 单测只读解析函数，不起网络。 */
internal object SupervisordHttpProbeTestAccess {
    fun parseProcessStructs(payload: String): List<String> =
        SupervisordHttpProbeAccess.parseProcessStructsForTest(payload)
}
