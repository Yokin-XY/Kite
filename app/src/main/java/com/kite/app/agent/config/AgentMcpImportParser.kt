package com.kite.app.agent.config

import org.json.JSONArray
import org.json.JSONObject

internal data class AgentMcpImportCandidate(
    val title: String,
    val description: String,
    val version: String?,
    val server: AgentMcpSummary,
)

/**
 * 把可移植 MCP 描述和常见客户端 JSON 配置转换为 Kite 的通用 MCP 草稿形状。
 *
 * 导入只读取结构化命令、参数、地址和变量名。外部文件中的环境变量或 Header 真值不会
 * 进入 Kite 配置，用户仍需在目标环境中提供对应变量。
 */
internal object AgentMcpImportParser {
    fun parse(payload: String): List<AgentMcpImportCandidate> {
        require(payload.toByteArray(Charsets.UTF_8).size <= MAX_PAYLOAD_BYTES) { "MCP 配置文件超过大小限制" }
        val root = JSONObject(payload)
        if (root.has("name") && (root.has("remotes") || root.has("packages"))) {
            return listOfNotNull(parseServerJson(root))
        }
        val servers = sequenceOf("mcpServers", "servers", "mcp")
            .mapNotNull(root::optJSONObject)
            .firstOrNull()
            ?: throw IllegalArgumentException("没有找到 server.json 或 mcpServers 配置")
        return servers.keys().asSequence()
            .take(MAX_SERVERS)
            .mapNotNull { id -> parseClientServer(id, servers.optJSONObject(id)) }
            .toList()
            .also { require(it.isNotEmpty()) { "MCP 配置中没有可导入的服务" } }
    }

    fun parseServerJson(server: JSONObject): AgentMcpImportCandidate? {
        val externalName = server.optString("name").trim().takeIf(String::isNotBlank) ?: return null
        val title = server.optString("title").trim().ifBlank { externalName.substringAfterLast('/') }
        val description = server.optString("description").trim().take(MAX_DESCRIPTION)
        val version = server.optString("version").trim().ifBlank { null }
        val id = safeId(title.ifBlank { externalName })

        val remotes = server.optJSONArray("remotes")
        for (index in 0 until minOf(remotes?.length() ?: 0, MAX_OPTIONS)) {
            val remote = remotes?.optJSONObject(index) ?: continue
            val url = remote.optString("url").trim().takeIf(::safeHttpUrl) ?: continue
            val transport = when (remote.optString("type").trim().lowercase()) {
                "sse" -> AgentMcpTransport.Sse
                "streamable-http", "http" -> AgentMcpTransport.StreamableHttp
                else -> AgentMcpTransport.RemoteHttpOrSse
            }
            return AgentMcpImportCandidate(
                title = title,
                description = description,
                version = version,
                server = AgentMcpSummary(
                    id = id,
                    kind = "registry",
                    enabled = true,
                    transport = transport,
                    url = url,
                    headerReferences = parseHeaderReferences(remote.optJSONArray("headers")),
                ),
            )
        }

        val packages = server.optJSONArray("packages")
        for (index in 0 until minOf(packages?.length() ?: 0, MAX_OPTIONS)) {
            val packageJson = packages?.optJSONObject(index) ?: continue
            val transport = packageJson.optJSONObject("transport")?.optString("type")?.lowercase()
            if (transport != "stdio") continue
            val identifier = packageJson.optString("identifier").trim().takeIf(String::isNotBlank) ?: continue
            val packageVersion = packageJson.optString("version").trim().ifBlank { version }
            val registryType = packageJson.optString("registryType").trim().lowercase()
            val runtimeHint = packageJson.optString("runtimeHint").trim()
            val commandAndPrefix = when (registryType) {
                "npm" -> (runtimeHint.ifBlank { "npx" }) to listOf("-y", versionedNpm(identifier, packageVersion))
                "pypi" -> (runtimeHint.ifBlank { "uvx" }) to listOf(versionedPython(identifier, packageVersion))
                else -> continue
            }
            return AgentMcpImportCandidate(
                title = title,
                description = description,
                version = version,
                server = AgentMcpSummary(
                    id = id,
                    kind = "registry",
                    enabled = true,
                    transport = AgentMcpTransport.Stdio,
                    command = commandAndPrefix.first,
                    arguments = commandAndPrefix.second + parseFixedArguments(packageJson.optJSONArray("packageArguments")),
                    environmentReferences = parseEnvironmentReferences(packageJson.optJSONArray("environmentVariables")),
                ),
            )
        }
        return null
    }

    private fun parseClientServer(id: String, json: JSONObject?): AgentMcpImportCandidate? {
        json ?: return null
        val command = json.optString("command").trim()
        val url = json.optString("url").trim()
        val normalizedId = safeId(id)
        val server = when {
            command.isNotBlank() -> AgentMcpSummary(
                id = normalizedId,
                kind = "import",
                enabled = json.optBoolean("enabled", !json.optBoolean("disabled", false)),
                transport = AgentMcpTransport.Stdio,
                command = command,
                arguments = stringArray(json.optJSONArray("args")),
                workingDirectory = json.optString("cwd").trim().ifBlank { null },
                environmentReferences = objectReferences(json.optJSONObject("env")),
            )
            safeHttpUrl(url) -> AgentMcpSummary(
                id = normalizedId,
                kind = "import",
                enabled = json.optBoolean("enabled", !json.optBoolean("disabled", false)),
                transport = when (json.optString("type").trim().lowercase()) {
                    "sse" -> AgentMcpTransport.Sse
                    "http", "streamable-http" -> AgentMcpTransport.StreamableHttp
                    else -> AgentMcpTransport.RemoteHttpOrSse
                },
                url = url,
                headerReferences = objectReferences(json.optJSONObject("headers")),
            )
            else -> return null
        }
        return AgentMcpImportCandidate(
            title = id,
            description = "从本地 MCP 配置导入",
            version = null,
            server = server,
        )
    }

    private fun parseEnvironmentReferences(array: JSONArray?): List<AgentMcpEnvironmentReference> = buildList {
        for (index in 0 until minOf(array?.length() ?: 0, MAX_REFERENCES)) {
            val name = array?.optJSONObject(index)?.optString("name")?.trim().orEmpty()
            if (ENVIRONMENT_NAME.matches(name)) add(AgentMcpEnvironmentReference(name, name))
        }
    }

    private fun parseHeaderReferences(array: JSONArray?): List<AgentMcpEnvironmentReference> = buildList {
        for (index in 0 until minOf(array?.length() ?: 0, MAX_REFERENCES)) {
            val item = array?.optJSONObject(index) ?: continue
            val name = item.optString("name").trim().takeIf(SAFE_REFERENCE_NAME::matches) ?: continue
            val variable = PLACEHOLDER.find(item.optString("value"))?.groupValues?.getOrNull(1)
                ?.uppercase()?.replace(NON_ENVIRONMENT_CHARACTER, "_")
                ?.takeIf(ENVIRONMENT_NAME::matches)
                ?: environmentName(name)
            add(AgentMcpEnvironmentReference(name, variable))
        }
    }

    private fun objectReferences(json: JSONObject?): List<AgentMcpEnvironmentReference> = buildList {
        json ?: return@buildList
        json.keys().asSequence().take(MAX_REFERENCES).forEach { name ->
            if (!SAFE_REFERENCE_NAME.matches(name)) return@forEach
            val rawValue = json.optString(name)
            val variable = ENV_REFERENCE.find(rawValue)?.groupValues?.getOrNull(1)
                ?.takeIf(ENVIRONMENT_NAME::matches)
                ?: environmentName(name)
            add(AgentMcpEnvironmentReference(name, variable))
        }
    }

    private fun parseFixedArguments(array: JSONArray?): List<String> = buildList {
        for (index in 0 until minOf(array?.length() ?: 0, MAX_ARGUMENTS)) {
            val item = array?.optJSONObject(index) ?: continue
            val value = item.optString("value").takeIf(::safeArgument) ?: continue
            val type = item.optString("type").lowercase()
            val name = item.optString("name").takeIf(::safeArgument)
            if (type == "named" && name != null) add(name)
            add(value)
        }
    }

    private fun stringArray(array: JSONArray?): List<String> = buildList {
        for (index in 0 until minOf(array?.length() ?: 0, MAX_ARGUMENTS)) {
            array?.optString(index)?.takeIf(::safeArgument)?.let(::add)
        }
    }

    private fun versionedNpm(identifier: String, version: String?): String =
        version?.let { "$identifier@$it" } ?: identifier

    private fun versionedPython(identifier: String, version: String?): String =
        version?.let { "$identifier==$it" } ?: identifier

    private fun environmentName(name: String): String {
        val normalized = name.uppercase().replace(NON_ENVIRONMENT_CHARACTER, "_").trim('_')
        return normalized.take(128).takeIf(ENVIRONMENT_NAME::matches) ?: "MCP_SECRET"
    }

    private fun safeId(raw: String): String {
        val normalized = raw.substringAfterLast('/')
            .replace(NON_ID_CHARACTER, "-")
            .trim('-', '.', '_')
            .take(128)
        return normalized.takeIf(SAFE_ID::matches) ?: "mcp-server"
    }

    private fun safeHttpUrl(value: String): Boolean = runCatching {
        val uri = java.net.URI(value)
        uri.scheme?.lowercase() in setOf("http", "https") && !uri.host.isNullOrBlank() && uri.userInfo == null
    }.getOrDefault(false)

    private fun safeArgument(value: String): Boolean =
        value.isNotBlank() && value.length <= MAX_ARGUMENT_LENGTH && value.none(Char::isISOControl)

    private val SAFE_ID = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,127}")
    private val SAFE_REFERENCE_NAME = Regex("[A-Za-z0-9_.$-]{1,128}")
    private val ENVIRONMENT_NAME = Regex("[A-Z_][A-Z0-9_]{0,127}")
    private val NON_ID_CHARACTER = Regex("[^A-Za-z0-9._-]+")
    private val NON_ENVIRONMENT_CHARACTER = Regex("[^A-Z0-9_]+")
    private val PLACEHOLDER = Regex("\\{([A-Za-z_][A-Za-z0-9_-]{0,127})\\}")
    private val ENV_REFERENCE = Regex("\\$\\{([A-Z_][A-Z0-9_]{0,127})\\}")
    private const val MAX_PAYLOAD_BYTES = 2 * 1024 * 1024
    private const val MAX_DESCRIPTION = 1_000
    private const val MAX_SERVERS = 64
    private const val MAX_OPTIONS = 16
    private const val MAX_REFERENCES = 64
    private const val MAX_ARGUMENTS = 64
    private const val MAX_ARGUMENT_LENGTH = 2_048
}
