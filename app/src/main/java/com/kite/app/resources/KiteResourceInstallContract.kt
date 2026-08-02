package com.kite.app.resources

import org.json.JSONArray
import org.json.JSONObject

/**
 * 资源安装结果所依赖的稳定合同。
 *
 * 显示文案、图标和打开页面变化不应触发重装；安装源、命令所有权、依赖、路径或安装动作变化时，
 * 旧安装必须重新收敛，不能只比较上游版本号。
 */
internal object KiteResourceInstallContract {
    fun hasDrift(currentManifest: JSONObject, installedManifestJson: String?): Boolean {
        if (installedManifestJson.isNullOrBlank()) return true
        val installedManifest = runCatching { JSONObject(installedManifestJson) }.getOrNull() ?: return true
        return canonicalContract(currentManifest) != canonicalContract(installedManifest)
    }

    internal fun canonicalContract(manifest: JSONObject): String =
        canonicalJson(installProjection(manifest))

    private fun installProjection(manifest: JSONObject): JSONObject = JSONObject().apply {
        manifest.optJSONObject("base")
            ?.takeIf { it.has("version") }
            ?.let { base -> put("base", JSONObject().put("version", base.opt("version"))) }
        copyIfPresent(manifest, "management")
        manifest.optJSONObject("relations")?.let { relations ->
            JSONObject().apply {
                copyIfPresent(relations, "base")
                copyIfPresent(relations, "defaults")
            }.takeIf { it.length() > 0 }?.let { put("relations", it) }
        }
        copyIfPresent(manifest, "source")
        copyIfPresent(manifest, "paths")
        manifest.optJSONObject("actions")
            ?.takeIf { it.has("install") }
            ?.let { actions -> put("actions", JSONObject().put("install", actions.opt("install"))) }
    }

    private fun JSONObject.copyIfPresent(source: JSONObject, key: String) {
        if (source.has(key)) put(key, source.opt(key))
    }

    private fun canonicalJson(value: Any?): String = when (value) {
        null, JSONObject.NULL -> "null"
        is JSONObject -> value.keys().asSequence()
            .toList()
            .sorted()
            .joinToString(prefix = "{", postfix = "}") { key ->
                "${JSONObject.quote(key)}:${canonicalJson(value.opt(key))}"
            }
        is JSONArray -> (0 until value.length())
            .joinToString(prefix = "[", postfix = "]") { index -> canonicalJson(value.opt(index)) }
        is String -> JSONObject.quote(value)
        is Number, is Boolean -> value.toString()
        else -> JSONObject.quote(value.toString())
    }
}
