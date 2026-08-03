package com.kite.app.foundation.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RuntimeHttpProxyRequestTest {
    @Test
    fun `connect authority keeps host and port`() {
        val request = RuntimeHttpProxyRequest.parse(
            "CONNECT api.openai.com:443 HTTP/1.1\r\nHost: api.openai.com:443\r\n\r\n"
                .toByteArray(Charsets.ISO_8859_1),
        )

        assertEquals("api.openai.com", request?.host)
        assertEquals(443, request?.port)
        assertEquals(true, request?.isConnect)
        assertNull(request?.forwardedHead)
    }

    @Test
    fun `absolute http request is rewritten for origin server`() {
        val request = RuntimeHttpProxyRequest.parse(
            "GET http://example.com:8080/a/b?q=1 HTTP/1.1\r\nHost: example.com:8080\r\nProxy-Connection: keep-alive\r\n\r\n"
                .toByteArray(Charsets.ISO_8859_1),
        )

        assertEquals("example.com", request?.host)
        assertEquals(8080, request?.port)
        assertEquals(
            "GET /a/b?q=1 HTTP/1.1\r\nHost: example.com:8080\r\n\r\n",
            request?.forwardedHead?.toString(Charsets.ISO_8859_1),
        )
    }

    @Test
    fun `origin form is not accepted as proxy target`() {
        assertNull(
            RuntimeHttpProxyRequest.parse(
                "GET /local HTTP/1.1\r\nHost: localhost\r\n\r\n".toByteArray(),
            ),
        )
    }
}
