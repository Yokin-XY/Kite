package com.kite.app.platform.resources

import com.kite.app.application.resources.ResourceVersionGateway
import com.kite.app.application.resources.ResourceVersionBatchPreparationGateway
import com.kite.app.application.resources.ResourceVersionInstalledPreparation
import com.kite.app.bridge.BridgeResult
import com.kite.app.bridge.KiteBridgeClient
import com.kite.app.foundation.runtime.AndroidNativeStructuredJsonStringProvider
import com.kite.app.foundation.runtime.RuntimeProviderDecision
import com.kite.app.foundation.runtime.RuntimeProviderKind
import com.kite.app.foundation.runtime.StructuredJsonStringContext
import com.kite.app.foundation.runtime.StructuredJsonStringRequest
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

internal enum class InstalledVersionRoute {
    ANDROID_NATIVE,
    PROOT_FALLBACK,
    BLOCKED,
}

internal data class InstalledVersionRouteEvent(
    val route: InstalledVersionRoute,
    val reason: String,
)

/** 优先读取已声明的受控元数据；事实不足时才用 Ubuntu 命令读取本地版本。 */
internal class AndroidResourceVersionGateway(
    private val bridgeClient: KiteBridgeClient,
    private val environmentFor: (String) -> Map<String, String> = { emptyMap() },
    private val metadataContextProvider: () -> StructuredJsonStringContext? = { null },
    private val routeObserver: (InstalledVersionRouteEvent) -> Unit = {},
    private val recipeRunner: (
        KiteRecipe,
        Map<String, String>,
        (BridgeResult) -> Unit,
    ) -> Unit = { recipe, environment, callback ->
        bridgeClient.runRecipe(recipe, extraEnv = environment, callback = callback)
    },
) : ResourceVersionGateway, ResourceVersionBatchPreparationGateway {
    override suspend fun readInstalledVersion(
        resourceId: String,
        probe: KiteResourceVersionProbeSpec,
        environmentId: String
    ): Result<String> {
        return when (val preparation = prepareInstalledVersion(probe)) {
            is ResourceVersionInstalledPreparation.Ready -> {
                observe(InstalledVersionRoute.ANDROID_NATIVE, preparation.reason)
                Result.success(preparation.rawValue)
            }
            is ResourceVersionInstalledPreparation.Unsupported -> fallbackToCommand(
                resourceId,
                probe,
                environmentId,
                preparation.reason,
            )
            is ResourceVersionInstalledPreparation.Blocked -> {
                observe(InstalledVersionRoute.BLOCKED, preparation.reason)
                Result.failure(IllegalArgumentException("installed_version_blocked:${preparation.reason}"))
            }
        }
    }

    /** 只做受控文件事实预检；不会启动命令、网络请求或 PRoot。 */
    override suspend fun prepareInstalledVersion(
        probe: KiteResourceVersionProbeSpec,
    ): ResourceVersionInstalledPreparation {
        val metadata = probe.structuredMetadata
            ?: return ResourceVersionInstalledPreparation.Unsupported("structured_metadata_absent")
        val decision = withContext(Dispatchers.IO) {
            val nativeContext = runCatching { metadataContextProvider() }.getOrNull()
                ?: return@withContext RuntimeProviderDecision.Unsupported(
                    RuntimeProviderKind.ANDROID_NATIVE,
                    "structured_json_context_unavailable",
                )
            runCatching {
                AndroidNativeStructuredJsonStringProvider.prepare(
                    context = nativeContext,
                    request = StructuredJsonStringRequest(
                        containerPath = metadata.containerPath,
                        maximumBytes = metadata.maximumBytes,
                        jsonField = metadata.jsonField,
                    ),
                )
            }.getOrElse {
                RuntimeProviderDecision.Unsupported(
                    RuntimeProviderKind.ANDROID_NATIVE,
                    "structured_json_prepare_failed",
                )
            }
        }
        return when (decision) {
            is RuntimeProviderDecision.Ready ->
                ResourceVersionInstalledPreparation.Ready(decision.plan.value, decision.reason)
            is RuntimeProviderDecision.Unsupported ->
                ResourceVersionInstalledPreparation.Unsupported(decision.reason)
            is RuntimeProviderDecision.Blocked ->
                ResourceVersionInstalledPreparation.Blocked(decision.reason)
        }
    }

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
        recipeRunner(recipe, commandEnvironment) callback@{ result ->
            if (!continuation.isActive) return@callback
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

    private suspend fun fallbackToCommand(
        resourceId: String,
        probe: KiteResourceVersionProbeSpec,
        environmentId: String,
        reason: String,
    ): Result<String> {
        observe(InstalledVersionRoute.PROOT_FALLBACK, reason)
        return readCommandVersion(resourceId, "installed_version", probe, environmentId)
    }

    private fun observe(route: InstalledVersionRoute, reason: String) {
        runCatching { routeObserver(InstalledVersionRouteEvent(route, reason)) }
    }

    private suspend fun readRemoteVersion(probe: KiteResourceRemoteVersionProbe): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                var lastError: Throwable? = null
                probe.orderedUrls.forEach { url ->
                    val body = runCatching { readRemoteBody(url) }
                    if (body.isSuccess) return@runCatching body.getOrThrow()
                    lastError = body.exceptionOrNull()
                }
                val primaryError = lastError ?: IllegalStateException("remote_version_source_missing")
                val fallbackUrl = probe.fallbackUrl.takeIf(String::isNotBlank)
                    ?: throw primaryError
                runCatching { readRedirectTarget(fallbackUrl) }.getOrElse { fallbackError ->
                    throw IllegalStateException(
                        "remote_version_sources_${primaryError.message.orEmpty()}_fallback_${fallbackError.message.orEmpty()}",
                        fallbackError
                    )
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
