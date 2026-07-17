package com.kite.app.foundation.runtime

/**
 * 容器 DNS 只接受 Android 当前默认网络提供的服务器。
 *
 * 这里不识别 VPN，也不提供公共 DNS 兜底。系统没有给 Kite 可用 DNS 时，
 * 容器应明确处于无 DNS 状态，不能擅自绕过用户的按应用网络、私有 DNS 或 VPN 规则。
 */
internal object ContainerDnsPolicy {
    private const val NO_DNS_COMMENT =
        "# Android default network currently exposes no usable DNS server.\n"

    fun normalize(systemDnsServers: List<String>): List<String> {
        return systemDnsServers
            .map(String::trim)
            .filter(String::isNotBlank)
            .filterNot(::isClearlyInvalid)
            .distinct()
    }

    fun renderResolvConf(dnsServers: List<String>): String {
        val normalized = normalize(dnsServers)
        return if (normalized.isEmpty()) {
            NO_DNS_COMMENT
        } else {
            normalized.joinToString(separator = "\n", postfix = "\n") { "nameserver $it" }
        }
    }

    private fun isClearlyInvalid(ip: String): Boolean {
        return ip == "0.0.0.0" ||
            ip.startsWith("127.") ||
            ip.startsWith("169.254.")
    }
}
