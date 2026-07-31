package com.kite.app.platform.resources

import com.kite.app.bridge.KiteBridgeClient
import com.kite.app.recipe.KiteExecution
import com.kite.app.recipe.KiteLaunchConfig
import com.kite.app.recipe.KiteRecipe
import com.kite.app.recipe.KiteRecipeIcon
import com.kite.app.recipe.KiteRecipeStep
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

internal data class ResourceManagedCommandRequirement(
    val resourceId: String,
    val commands: List<String>
)

internal interface ResourceInstalledStateProbe {
    suspend fun missingResourceIds(
        requirements: Collection<ResourceManagedCommandRequirement>
    ): Result<Set<String>>
}

/** 用户触发资源动作时，用一次原生 PRoot 调用核对登记事实，不进入页面渲染路径。 */
internal class AndroidResourceInstalledStateProbe(
    private val bridgeClient: KiteBridgeClient
) : ResourceInstalledStateProbe {
    override suspend fun missingResourceIds(
        requirements: Collection<ResourceManagedCommandRequirement>
    ): Result<Set<String>> {
        val normalized = ResourceManagedCommandProbeProtocol.normalize(requirements)
        if (normalized.isEmpty()) return Result.success(emptySet())
        val recipe = probeRecipe(ResourceManagedCommandProbeProtocol.command(normalized))
        return suspendCancellableCoroutine { continuation ->
            bridgeClient.runRecipe(recipe) { result ->
                if (!continuation.isActive) return@runRecipe
                if (!result.ok) {
                    continuation.resume(
                        Result.failure(
                            IllegalStateException(
                                result.message.ifBlank { result.status.ifBlank { "resource_state_probe_failed" } }
                            )
                        )
                    )
                    return@runRecipe
                }
                val output = result.runReport?.steps.orEmpty().joinToString("\n") { step ->
                    step.stdoutTail.ifBlank { step.lastMeaningfulOutput }
                }.ifBlank { result.message }
                continuation.resume(ResourceManagedCommandProbeProtocol.parse(output))
            }
        }
    }

    private fun probeRecipe(command: String): KiteRecipe = KiteRecipe(
        id = "resource-installed-state-probe-${System.nanoTime()}",
        name = "资源安装状态校验",
        description = "核对已登记资源的受管命令",
        type = KiteRecipe.TYPE_START_SERVICE,
        category = "resource",
        defaultUrl = "",
        shortcut = false,
        icon = KiteRecipeIcon(name = KiteRecipeIcon.ICON_TOOLS),
        launch = KiteLaunchConfig(openInstance = false),
        execution = KiteExecution.steps(
            listOf(
                KiteRecipeStep(
                    id = "managed_command_probe",
                    type = KiteRecipe.STEP_SHELL,
                    cmd = command,
                    surfaceMode = KiteRecipe.SURFACE_MODE_SILENT,
                    workdir = "/workspace",
                    timeoutMs = PROBE_TIMEOUT_MS
                )
            )
        ),
        runtimeSource = RUNTIME_SOURCE
    )

    companion object {
        private const val RUNTIME_SOURCE = "resource_installed_state_probe"
        private const val PROBE_TIMEOUT_MS = 10_000L
    }
}

internal object ResourceManagedCommandProbeProtocol {
    private const val BEGIN_MARKER = "KITE_RESOURCE_COMMAND_PROBE_BEGIN"
    private const val END_MARKER = "KITE_RESOURCE_COMMAND_PROBE_END"
    private const val MISSING_MARKER = "KITE_RESOURCE_COMMAND_MISSING"

    fun normalize(
        requirements: Collection<ResourceManagedCommandRequirement>
    ): List<ResourceManagedCommandRequirement> = requirements
        .mapNotNull { requirement ->
            val resourceId = requirement.resourceId.trim()
            val commands = requirement.commands
                .map(String::trim)
                .filter(String::isNotBlank)
                .distinct()
            resourceId.takeIf(String::isNotBlank)?.let {
                ResourceManagedCommandRequirement(it, commands)
            }
        }
        .filter { it.commands.isNotEmpty() }
        .distinctBy(ResourceManagedCommandRequirement::resourceId)

    fun command(requirements: Collection<ResourceManagedCommandRequirement>): String = buildString {
        appendLine("PATH=/workspace/.kf/bin:\"${'$'}PATH\"")
        appendLine("export PATH")
        appendLine("printf '%s\\n' ${shellQuote(BEGIN_MARKER)}")
        normalize(requirements).forEach { requirement ->
            val checks = requirement.commands.joinToString(" && ") { command ->
                "command -v ${shellQuote(command)} >/dev/null 2>&1"
            }
            append("if ").append(checks).appendLine("; then")
            appendLine("  :")
            appendLine("else")
            append("  printf '%s\\t%s\\n' ")
                .append(shellQuote(MISSING_MARKER))
                .append(' ')
                .appendLine(shellQuote(requirement.resourceId))
            appendLine("fi")
        }
        appendLine("printf '%s\\n' ${shellQuote(END_MARKER)}")
    }

    fun parse(output: String): Result<Set<String>> {
        val lines = output.lineSequence().map(String::trim).filter(String::isNotBlank).toList()
        if (BEGIN_MARKER !in lines || END_MARKER !in lines) {
            return Result.failure(IllegalStateException("resource_state_probe_incomplete"))
        }
        val missing = lines.mapNotNull { line ->
            val parts = line.split('\t')
            parts.getOrNull(1)?.trim()?.takeIf {
                parts.firstOrNull() == MISSING_MARKER && it.isNotBlank()
            }
        }.toSet()
        return Result.success(missing)
    }

    private fun shellQuote(value: String): String =
        "'" + value.replace("'", "'\"'\"'") + "'"
}
