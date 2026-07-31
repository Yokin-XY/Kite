package com.kite.app.platform.resources

import com.kite.app.application.resources.ResourceVersionGateway
import com.kite.app.bridge.KiteBridgeClient
import com.kite.app.recipe.KiteExecution
import com.kite.app.recipe.KiteLaunchConfig
import com.kite.app.recipe.KiteRecipe
import com.kite.app.recipe.KiteRecipeIcon
import com.kite.app.recipe.KiteRecipeStep
import com.kite.app.resources.KiteResourceInstallRecipes
import com.kite.app.resources.KiteResourceCommandVersionProbe
import com.kite.app.resources.KiteResourceLatestVersionProbe
import com.kite.app.resources.KiteResourceRemoteVersionProbe
import com.kite.app.resources.KiteResourceVersionProbeSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.resume

/** 使用 Ubuntu 实际命令读取本地版本，使用 App 网络栈查询来源端版本。 */
internal class AndroidResourceVersionGateway(
    private val bridgeClient: KiteBridgeClient,
    private val environmentFor: (String) -> Map<String, String> = { emptyMap() }
) : ResourceVersionGateway {
    override suspend fun readInstalledVersion(
        resourceId: String,
        probe: KiteResourceVersionProbeSpec,
        environmentId: String
    ): Result<String> = readCommandVersion(resourceId, "installed_version", probe, environmentId)

    override suspend fun readLatestVersion(
        resourceId: String,
        probe: KiteResourceLatestVersionProbe,
        environmentId: String
    ): Result<String> = when (probe) {
        is KiteResourceCommandVersionProbe ->
            readCommandVersion(resourceId, "latest_version", probe.probe, environmentId)
        is KiteResourceRemoteVersionProbe -> readRemoteVersion(probe)
    }

    private suspend fun readCommandVersion(
        resourceId: String,
        stepId: String,
        probe: KiteResourceVersionProbeSpec,
        environmentId: String
    ): Result<String> = suspendCancellableCoroutine { continuation ->
        val commandEnvironment = runCatching { environmentFor(environmentId) }.getOrElse { error ->
            continuation.resume(Result.failure(error))
            return@suspendCancellableCoroutine
        }
        val cleanId = KiteResourceInstallRecipes.safeId(resourceId)
        val step = KiteRecipeStep(
            id = stepId,
            type = KiteRecipe.STEP_SHELL,
            cmd = probe.command,
            surfaceMode = KiteRecipe.SURFACE_MODE_SILENT,
            workdir = "/workspace",
            timeoutMs = LOCAL_PROBE_TIMEOUT_MS
        )
        val recipe = KiteRecipe(
            id = "resource-version-$cleanId-${System.nanoTime()}",
            name = "资源版本查询",
            description = cleanId,
            type = KiteRecipe.TYPE_START_SERVICE,
            category = "resource",
            defaultUrl = "",
            shortcut = false,
            icon = KiteRecipeIcon(name = KiteRecipeIcon.ICON_TOOLS),
            launch = KiteLaunchConfig(openInstance = false),
            execution = KiteExecution.steps(listOf(step)),
            runtimeSource = RUNTIME_SOURCE
        )
        bridgeClient.runRecipe(recipe, extraEnv = commandEnvironment) { result ->
            if (!continuation.isActive) return@runRecipe
            if (result.ok) {
                val output = result.runReport?.lastMeaningfulOutput()
                    ?.takeIf(String::isNotBlank)
                    ?: result.message.takeIf(String::isNotBlank)
                continuation.resume(
                    output?.let(Result.Companion::success)
                        ?: Result.failure(IllegalStateException("${stepId}_empty"))
                )
            } else {
                continuation.resume(
                    Result.failure(
                        IllegalStateException(result.message.ifBlank { result.status.ifBlank { "${stepId}_failed" } })
                    )
                )
            }
        }
    }

    private suspend fun readRemoteVersion(probe: KiteResourceRemoteVersionProbe): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                runCatching { readRemoteBody(probe.url) }.getOrElse { primaryError ->
                    val fallbackUrl = probe.fallbackUrl.takeIf(String::isNotBlank)
                        ?: throw primaryError
                    runCatching { readRedirectTarget(fallbackUrl) }.getOrElse { fallbackError ->
                        throw IllegalStateException(
                            "remote_version_primary_${primaryError.message.orEmpty()}_fallback_${fallbackError.message.orEmpty()}",
                            fallbackError
                        )
                    }
                }
            }
        }

    private fun open(url: String, accept: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = REMOTE_CONNECT_TIMEOUT_MS
            readTimeout = REMOTE_READ_TIMEOUT_MS
            instanceFollowRedirects = true
            setRequestProperty("Accept", accept)
            setRequestProperty("User-Agent", "Kite-Resource-Version/1")
        }

    private fun readRemoteBody(url: String): String {
        val connection = open(url, "application/json")
        try {
            val code = connection.responseCode
            if (code !in 200..299) error("http_$code")
            connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                val payload = buildString {
                    val buffer = CharArray(8 * 1024)
                    while (true) {
                        val count = reader.read(buffer)
                        if (count < 0) break
                        append(buffer, 0, count)
                        if (length > MAX_RESPONSE_CHARS) error("response_too_large")
                    }
                }
                return payload
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun readRedirectTarget(url: String): String {
        val connection = open(url, "text/html")
        try {
            val code = connection.responseCode
            if (code !in 200..299) error("http_$code")
            val resolved = connection.url.toString()
            if (resolved == url || "/releases/tag/" !in resolved) error("redirect_target_missing")
            return resolved
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private const val RUNTIME_SOURCE = "resource_version_check"
        private const val LOCAL_PROBE_TIMEOUT_MS = 20_000L
        private const val REMOTE_CONNECT_TIMEOUT_MS = 10_000
        private const val REMOTE_READ_TIMEOUT_MS = 15_000
        private const val MAX_RESPONSE_CHARS = 512 * 1024
    }
}
