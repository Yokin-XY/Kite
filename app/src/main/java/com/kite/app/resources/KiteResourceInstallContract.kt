package com.kite.app.resources

import org.json.JSONArray
import org.json.JSONObject

internal sealed interface KiteResourceInstallContractResolution {
    data object Current : KiteResourceInstallContractResolution

    data class UpdateAvailable(
        val installedVersion: String,
        val currentVersion: String,
    ) : KiteResourceInstallContractResolution

    data object RepairRequired : KiteResourceInstallContractResolution
}

/**
 * 资源安装结果所依赖的稳定合同。
 *
 * 显示文案、图标和打开页面变化不应触发重装；安装源、命令所有权、依赖、路径或安装动作变化时，
 * 旧安装必须重新收敛，不能只比较上游版本号。
 */
internal object KiteResourceInstallContract {
    fun resolve(
        currentManifest: JSONObject,
        installedManifestJson: String?,
    ): KiteResourceInstallContractResolution {
        if (installedManifestJson.isNullOrBlank()) {
            return KiteResourceInstallContractResolution.RepairRequired
        }
        val installedManifest = runCatching { JSONObject(installedManifestJson) }.getOrNull()
            ?: return KiteResourceInstallContractResolution.RepairRequired
        if (canonicalContract(currentManifest) == canonicalContract(installedManifest)) {
            return KiteResourceInstallContractResolution.Current
        }
        val installedVersion = installedManifest.baseVersion()
        val currentVersion = currentManifest.baseVersion()
        val hasExplicitUpdate = currentManifest.optJSONObject("actions")
            ?.optJSONArray("update")
            ?.length()
            ?.let { it > 0 } == true
        return if (
            hasExplicitUpdate &&
            installedVersion.isNotBlank() &&
            currentVersion.isNotBlank() &&
            installedVersion != currentVersion
        ) {
            KiteResourceInstallContractResolution.UpdateAvailable(
                installedVersion = installedVersion,
                currentVersion = currentVersion,
            )
        } else {
            KiteResourceInstallContractResolution.RepairRequired
        }
    }

    fun hasDrift(currentManifest: JSONObject, installedManifestJson: String?): Boolean {
        return resolve(currentManifest, installedManifestJson) !=
            KiteResourceInstallContractResolution.Current
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

    private fun JSONObject.baseVersion(): String =
        optJSONObject("base")?.optString("version").orEmpty().trim()

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
