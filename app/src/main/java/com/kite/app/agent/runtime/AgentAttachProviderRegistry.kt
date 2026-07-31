package com.kite.app.agent.runtime

import com.kite.app.agent.contract.KiteAgentProvider
import java.util.concurrent.ConcurrentHashMap

/**
 * Attach Agent 的进程内连接提供者目录。
 *
 * connectionReference 只负责定位受控适配器，不包含端口、PID 或密钥。外部适配器拥有服务进程和连接
 * 参数；Kite Agent Runtime 只持有这里返回的协议 provider，并在实例停止时断开连接。
 */
object AgentAttachProviderRegistry {
    private val providers = ConcurrentHashMap<String, KiteAgentProvider>()

    fun register(connectionReference: String, provider: KiteAgentProvider): Boolean {
        val reference = connectionReference.normalizedReference() ?: return false
        return providers.putIfAbsent(reference, provider) == null
    }

    fun unregister(connectionReference: String, provider: KiteAgentProvider): Boolean {
        val reference = connectionReference.normalizedReference() ?: return false
        return providers.remove(reference, provider)
    }

    fun provider(connectionReference: String): KiteAgentProvider? =
        connectionReference.normalizedReference()?.let(providers::get)

    fun contains(connectionReference: String): Boolean = provider(connectionReference) != null

    internal fun resetForTest() {
        providers.clear()
    }

    private fun String?.normalizedReference(): String? =
        this?.trim()?.takeIf(String::isNotBlank)
}
