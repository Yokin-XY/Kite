package com.kite.app.application.resources

import com.kite.app.resources.KiteResourceManifest
import com.kite.app.resources.KiteResourceCommandVersionProbe
import com.kite.app.resources.KiteResourceLatestVersionProbe
import com.kite.app.resources.KiteResourceRemoteVersionProbe
import com.kite.app.resources.KiteResourceRegistry
import com.kite.app.resources.KiteResourceSourcePlanFactory
import com.kite.app.resources.KiteResourceVersionProbeSpec
import org.json.JSONObject

internal interface ResourceVersionGateway {
    suspend fun readInstalledVersion(
        resourceId: String,
        probe: KiteResourceVersionProbeSpec,
        environmentId: String
    ): Result<String>

    suspend fun readLatestVersion(
        resourceId: String,
        probe: KiteResourceLatestVersionProbe,
        environmentId: String
    ): Result<String>
}

internal sealed interface ResourceVersionCheckResult {
    val resourceId: String

    data class UpdateAvailable(
        override val resourceId: String,
        val installedVersion: String,
        val latestVersion: String
    ) : ResourceVersionCheckResult

    data class Current(
        override val resourceId: String,
        val installedVersion: String,
        val latestVersion: String,
        val locallyAhead: Boolean = false
    ) : ResourceVersionCheckResult

    data class Unsupported(
        override val resourceId: String,
        val reason: String
    ) : ResourceVersionCheckResult

    data class Failed(
        override val resourceId: String,
        val stage: String,
        val reason: String
    ) : ResourceVersionCheckResult
}

/** 只产出版本事实，不直接修改 Store 或页面。 */
internal class ResourceVersionCoordinator(
    private val gateway: ResourceVersionGateway
) {
    suspend fun check(
        manifest: KiteResourceManifest,
        environmentId: String = KiteResourceRegistry.DEFAULT_ENVIRONMENT_ID
    ): ResourceVersionCheckResult {
        val plan = KiteResourceSourcePlanFactory.versionCheckPlan(manifest)
        val installedProbe = plan.installed
            ?: return ResourceVersionCheckResult.Unsupported(manifest.id, "installed_version_probe_missing")
        val latestProbe = plan.latest
            ?: return ResourceVersionCheckResult.Unsupported(manifest.id, "latest_version_probe_missing")

        val installedRaw = gateway.readInstalledVersion(manifest.id, installedProbe, environmentId).getOrElse { error ->
            return ResourceVersionCheckResult.Failed(
                manifest.id,
                stage = "installed",
                reason = error.message ?: error.javaClass.simpleName
            )
        }
        val installed = ResourceVersionParser.installed(installedRaw, installedProbe)
            ?: return ResourceVersionCheckResult.Failed(manifest.id, "installed", "installed_version_unrecognized")

        val latestRaw = gateway.readLatestVersion(manifest.id, latestProbe, environmentId).getOrElse { error ->
            return ResourceVersionCheckResult.Failed(
                manifest.id,
                stage = "latest",
                reason = error.message ?: error.javaClass.simpleName
            )
        }
        val latest = ResourceVersionParser.latest(latestRaw, latestProbe)
            ?: return ResourceVersionCheckResult.Failed(manifest.id, "latest", "latest_version_unrecognized")

        return when (ResourceVersionComparator.compare(installed, latest)) {
            ResourceVersionOrder.OLDER -> ResourceVersionCheckResult.UpdateAvailable(manifest.id, installed, latest)
            ResourceVersionOrder.EQUAL -> ResourceVersionCheckResult.Current(manifest.id, installed, latest)
            ResourceVersionOrder.NEWER -> ResourceVersionCheckResult.Current(
                manifest.id,
                installed,
                latest,
                locallyAhead = true
            )
            ResourceVersionOrder.UNKNOWN -> ResourceVersionCheckResult.Failed(
                manifest.id,
                stage = "compare",
                reason = "version_order_unknown"
            )
        }
    }
}

internal object ResourceVersionParser {
    fun installed(raw: String, probe: KiteResourceVersionProbeSpec): String? {
        val candidate = if (probe.pattern.isBlank()) {
            raw.lineSequence().map(String::trim).lastOrNull(String::isNotBlank)
        } else {
            val match = runCatching { Regex(probe.pattern).find(raw) }.getOrNull()
            match?.groupValues?.getOrNull(probe.group)
        }
        return candidate?.let(::normalize)
    }

    fun latest(raw: String, probe: KiteResourceLatestVersionProbe): String? = when (probe) {
        is KiteResourceCommandVersionProbe -> installed(raw, probe.probe)
        is KiteResourceRemoteVersionProbe -> {
            val value = when (probe.format) {
                "text" -> raw.trim()
                "json" -> runCatching { JSONObject(raw).optString(probe.jsonField) }.getOrNull().orEmpty()
                "github_release" -> {
                    runCatching { JSONObject(raw).optString(probe.jsonField) }.getOrNull().orEmpty()
                        .ifBlank { githubReleaseTagFromRedirect(raw) }
                }
                else -> ""
            }.takeIf(String::isNotBlank)
            value?.let { candidate ->
                val stripped = probe.stripPrefix.takeIf(String::isNotBlank)
                    ?.let(candidate::removePrefix)
                    ?: candidate
                normalize(stripped)
            }
        }
    }

    fun normalize(value: String): String? {
        val clean = value.trim().trim('"', '\'', ' ', '\t', '\r', '\n')
        return clean.takeIf { VERSION_TOKEN.matches(it) }
    }

    private fun githubReleaseTagFromRedirect(raw: String): String = raw
        .trim()
        .substringAfter("/releases/tag/", missingDelimiterValue = "")
        .substringBefore('?')
        .substringBefore('#')
        .trimEnd('/')

    private val VERSION_TOKEN = Regex("v?[0-9]+(?:\\.[0-9]+)*(?:[-+][0-9A-Za-z.-]+)?")
}

internal enum class ResourceVersionOrder { OLDER, EQUAL, NEWER, UNKNOWN }

internal object ResourceVersionComparator {
    fun compare(installed: String, latest: String): ResourceVersionOrder {
        val left = ParsedVersion.parse(installed) ?: return ResourceVersionOrder.UNKNOWN
        val right = ParsedVersion.parse(latest) ?: return ResourceVersionOrder.UNKNOWN
        val width = maxOf(left.numbers.size, right.numbers.size)
        repeat(width) { index ->
            val l = left.numbers.getOrElse(index) { 0 }
            val r = right.numbers.getOrElse(index) { 0 }
            if (l < r) return ResourceVersionOrder.OLDER
            if (l > r) return ResourceVersionOrder.NEWER
        }
        if (left.preRelease == right.preRelease) return ResourceVersionOrder.EQUAL
        if (left.preRelease.isEmpty()) return ResourceVersionOrder.NEWER
        if (right.preRelease.isEmpty()) return ResourceVersionOrder.OLDER
        return comparePreRelease(left.preRelease, right.preRelease)
    }

    private fun comparePreRelease(left: List<String>, right: List<String>): ResourceVersionOrder {
        val width = maxOf(left.size, right.size)
        repeat(width) { index ->
            val l = left.getOrNull(index) ?: return ResourceVersionOrder.OLDER
            val r = right.getOrNull(index) ?: return ResourceVersionOrder.NEWER
            if (l == r) return@repeat
            val lNumber = l.toLongOrNull()
            val rNumber = r.toLongOrNull()
            val order = when {
                lNumber != null && rNumber != null -> lNumber.compareTo(rNumber)
                lNumber != null -> -1
                rNumber != null -> 1
                else -> l.compareTo(r)
            }
            return if (order < 0) ResourceVersionOrder.OLDER else ResourceVersionOrder.NEWER
        }
        return ResourceVersionOrder.EQUAL
    }

    private data class ParsedVersion(
        val numbers: List<Long>,
        val preRelease: List<String>
    ) {
        companion object {
            fun parse(value: String): ParsedVersion? {
                val normalized = ResourceVersionParser.normalize(value)?.removePrefix("v") ?: return null
                val withoutBuild = normalized.substringBefore('+')
                val core = withoutBuild.substringBefore('-')
                val numbers = core.split('.').map { it.toLongOrNull() ?: return null }
                val preRelease = withoutBuild.substringAfter('-', missingDelimiterValue = "")
                    .split('.')
                    .filter(String::isNotBlank)
                return ParsedVersion(numbers, preRelease)
            }
        }
    }
}
