package com.kite.app.foundation.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContainerDnsPolicyTest {
    @Test
    fun `keeps only usable Android DNS servers in system order`() {
        val result = ContainerDnsPolicy.normalize(
            listOf(" 172.19.0.2 ", "0.0.0.0", "127.0.0.1", "172.19.0.2", "2408:8888::8")
        )

        assertEquals(listOf("172.19.0.2", "2408:8888::8"), result)
    }

    @Test
    fun `does not invent public DNS when Android exposes none`() {
        val result = ContainerDnsPolicy.normalize(emptyList())
        val resolvConf = ContainerDnsPolicy.renderResolvConf(result)

        assertTrue(result.isEmpty())
        assertFalse(resolvConf.contains("nameserver"))
        assertFalse(resolvConf.contains("1.1.1.1"))
        assertFalse(resolvConf.contains("8.8.8.8"))
        assertFalse(resolvConf.contains("223.5.5.5"))
    }

    @Test
    fun `renders Android provided DNS without provider or VPN rules`() {
        val resolvConf = ContainerDnsPolicy.renderResolvConf(
            listOf("172.19.0.2", "218.104.111.122")
        )

        assertEquals(
            "nameserver 172.19.0.2\nnameserver 218.104.111.122\n",
            resolvConf
        )
    }
}
