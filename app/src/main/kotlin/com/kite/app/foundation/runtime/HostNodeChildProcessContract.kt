package com.kite.app.foundation.runtime

import com.kite.app.foundation.contracts.ContainerExecConfig
import com.kite.app.foundation.contracts.ContainerLaunchConfig
import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.util.Base64

internal data class HostNodeChildProcessContract(
    val prootArgv: List<String>,
    val prootEnvironment: Map<String, String>,
) {
    fun attachTo(config: ContainerLaunchConfig): ContainerLaunchConfig {
        val environment = config.env.associateTo(linkedMapOf()) { entry ->
            entry.substringBefore('=') to entry.substringAfter('=', "")
        }
        environment[ENV_PROOT_ARGV] = encodeJson(JSONArray(prootArgv).toString())
        environment[ENV_PROOT_ENV] = encodeJson(JSONObject(prootEnvironment).toString())
        return config.copy(env = environment.map { (key, value) -> "$key=$value" }.toTypedArray())
    }

    companion object {
        const val ENV_PROOT_ARGV = "KITE_NODE_HOST_PROOT_ARGV_B64"
        const val ENV_PROOT_ENV = "KITE_NODE_HOST_PROOT_ENV_B64"

        fun from(config: ContainerExecConfig, marker: String): HostNodeChildProcessContract {
            require(marker.isNotBlank()) { "host_node_child_marker_missing" }
            require(config.command.lastOrNull() == marker) { "host_node_child_marker_mismatch" }
            val prefix = config.command.dropLast(1)
            require(prefix.isNotEmpty()) { "host_node_child_proot_prefix_missing" }
            return HostNodeChildProcessContract(
                prootArgv = prefix,
                prootEnvironment = config.env,
            )
        }

        private fun encodeJson(value: String): String = Base64.getEncoder().withoutPadding()
            .encodeToString(value.toByteArray(StandardCharsets.UTF_8))
    }
}
