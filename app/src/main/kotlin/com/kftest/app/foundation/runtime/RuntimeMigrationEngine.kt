package com.kftest.app.foundation.runtime

import android.content.Context
import com.kftest.app.foundation.logging.Logger
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Runtime upgrade migrations are allowed to rewrite APK-owned runtime assets only.
 *
 * User-owned state stays outside this contract:
 * - containers/
 * - shared/
 * - containers.json
 * - terminal-sessions.json
 *
 * This is intentionally narrower than a plugin system. It is a guarded upgrade
 * path for repairing runtime-owned files while preserving user data.
 */
object RuntimeMigrationEngine {
    private const val TAG = "RuntimeMigrationEngine"
    private const val MIGRATION_LOG_FILE = "runtime-migrations.jsonl"

    private const val STOCK_PROOT_ASSET_ID = "stock-proot-arm64"
    private const val TERMUX_BASELINE_ASSET_ID =
        "termux-proot-baseline-arm64-58aad2c-telemetry-startup-v1"
    private const val TELEMETRY_DEBUG_JSONL_LIFECYCLE_V0 = "debug_jsonl_lifecycle_v0"
    private const val TELEMETRY_NONE_CURRENT = "none_current"
    private const val SUBSTRATE_PASS = "PASS"
    private const val SUBSTRATE_PARTIAL = "PARTIAL"
    private const val SUBSTRATE_BLOCKED = "BLOCKED"

    private val apkOwnedRuntimePaths = listOf(
        "bin/proot",
        "lib/libtalloc.so.2",
        "libexec/proot/loader",
        "libexec/proot/loader32",
        "proot-runtime.json"
    )

    private val preservedUserDataPaths = listOf(
        "containers/",
        "shared/",
        "containers.json",
        "terminal-sessions.json"
    )

    fun resolveProotDescriptor(
        context: Context,
        layout: AssetExtractor.RuntimeLayout,
        packagedDescriptor: JSONObject
    ): JSONObject {
        val selected = resolvePackagedProotDescriptor(
            packagedDescriptor = packagedDescriptor,
            assetExists = { assetPath -> assetExists(context, assetPath) }
        )
        val packagedAssetId = packagedDescriptor.optString("assetId")
        val selectedAssetId = selected.optString("assetId")
        val installedAssetId = readInstalledDescriptor(layout)?.optString("assetId").orEmpty()
        if (selectedAssetId.isNotBlank() && selectedAssetId != installedAssetId) {
            logMigrationIfNeeded(
                layout = layout,
                migrationId = "proot_runtime_selection_v2",
                fromAssetId = installedAssetId.ifBlank { packagedAssetId },
                toAssetId = selectedAssetId,
                reason = selected.optString("telemetrySubstrateReason")
                    .ifBlank { "runtime_selection" }
            )
        }
        return selected
    }

    internal fun resolvePackagedProotDescriptorForTesting(
        packagedDescriptor: JSONObject,
        assetExists: (String) -> Boolean = { true }
    ): JSONObject {
        return resolvePackagedProotDescriptor(
            packagedDescriptor = packagedDescriptor,
            assetExists = assetExists
        )
    }

    private fun resolvePackagedProotDescriptor(
        packagedDescriptor: JSONObject,
        assetExists: (String) -> Boolean
    ): JSONObject {
        val telemetryCandidate = findTelemetryCapableRuntime(packagedDescriptor)
        val stockRuntime = findAvailableRuntime(packagedDescriptor, STOCK_PROOT_ASSET_ID)
        if (telemetryCandidate != null && isRuntimeSelectable(telemetryCandidate, assetExists)) {
            return buildDescriptorFromRuntime(
                packagedDescriptor = packagedDescriptor,
                runtime = telemetryCandidate,
                substrateState = SUBSTRATE_PASS,
                reason = "telemetry_capable_runtime_selected"
            )
        }

        val fallback = if (stockRuntime != null && isRuntimeAssetPresent(stockRuntime, assetExists)) {
            buildDescriptorFromRuntime(
                packagedDescriptor = packagedDescriptor,
                runtime = stockRuntime,
                substrateState = if (telemetryCandidate != null) SUBSTRATE_PARTIAL else SUBSTRATE_BLOCKED,
                reason = if (telemetryCandidate != null) {
                    "telemetry_capable_candidate_quarantined_or_unusable"
                } else {
                    "no_telemetry_capable_proot_runtime_asset"
                }
            )
        } else {
            JSONObject(packagedDescriptor.toString())
                .put("telemetrySubstrateState", SUBSTRATE_BLOCKED)
                .put("telemetrySubstrateReason", "stock_fallback_runtime_missing")
                .put("telemetryMode", TELEMETRY_NONE_CURRENT)
        }
        telemetryCandidate?.let { candidate ->
            fallback.put("telemetryCapableCandidateRuntimeId", candidate.optString("runtimeId"))
            fallback.put("telemetryCapableCandidateValidationState", candidate.optString("validationState"))
            fallback.put("telemetryCapableCandidateTelemetryMode", candidateTelemetryMode(candidate, packagedDescriptor))
        }
        return fallback
    }

    private fun findAvailableRuntime(descriptor: JSONObject, assetId: String): JSONObject? {
        val runtimes = descriptor.optJSONArray("availableRuntimes") ?: return null
        for (index in 0 until runtimes.length()) {
            val runtime = runtimes.optJSONObject(index) ?: continue
            if (runtime.optString("assetId") == assetId) {
                return runtime
            }
        }
        return null
    }

    private fun findTelemetryCapableRuntime(descriptor: JSONObject): JSONObject? {
        val runtimes = descriptor.optJSONArray("availableRuntimes") ?: return null
        for (index in 0 until runtimes.length()) {
            val runtime = runtimes.optJSONObject(index) ?: continue
            if (candidateTelemetryMode(runtime, descriptor) == TELEMETRY_DEBUG_JSONL_LIFECYCLE_V0) {
                return runtime
            }
        }
        return null
    }

    private fun candidateTelemetryMode(runtime: JSONObject, descriptor: JSONObject): String {
        return runtime.optString("telemetryMode")
            .ifBlank {
                if (runtime.optString("runtimeId") == descriptor.optString("activeRuntimeId") ||
                    runtime.optString("assetId") == descriptor.optString("assetId")
                ) {
                    descriptor.optString("telemetryMode")
                } else {
                    TELEMETRY_NONE_CURRENT
                }
            }
            .ifBlank { TELEMETRY_NONE_CURRENT }
    }

    private fun isRuntimeSelectable(
        runtime: JSONObject,
        assetExists: (String) -> Boolean
    ): Boolean {
        val validationState = runtime.optString("validationState")
        if (validationState.startsWith("quarantined", ignoreCase = true)) {
            return false
        }
        return isRuntimeAssetPresent(runtime, assetExists)
    }

    private fun isRuntimeAssetPresent(
        runtime: JSONObject,
        assetExists: (String) -> Boolean
    ): Boolean {
        val executable = runtime.optString("executableAssetPath")
        return executable.isNotBlank() && assetExists(executable)
    }

    private fun buildDescriptorFromRuntime(
        packagedDescriptor: JSONObject,
        runtime: JSONObject,
        substrateState: String,
        reason: String
    ): JSONObject {
        val descriptor = JSONObject(packagedDescriptor.toString())
        descriptor.put("assetId", runtime.optString("assetId"))
        descriptor.put("activeRuntimeId", runtime.optString("runtimeId"))
        descriptor.put("selectionMode", "runtime_migration_guard_v1")
        descriptor.put("provider", runtime.optString("provider", descriptor.optString("provider")))
        descriptor.put("sourceKind", runtime.optString("sourceKind", descriptor.optString("sourceKind")))
        descriptor.put("sourceRepository", runtime.optString("sourceRepository", "unknown"))
        descriptor.put("sourceCommit", runtime.optString("sourceCommit", "unknown"))
        descriptor.put("sourceTag", runtime.optString("sourceTag", "unknown"))
        descriptor.put("binaryRole", "active_runtime")
        descriptor.put("executableAssetPath", runtime.optString("executableAssetPath"))
        descriptor.put("loaderMode", runtime.optString("loaderMode", "external"))
        descriptor.put(
            "telemetryMode",
            runtime.optString("telemetryMode").ifBlank { TELEMETRY_NONE_CURRENT }
        )
        descriptor.put("telemetrySubstrateState", substrateState)
        descriptor.put("telemetrySubstrateReason", reason)
        descriptor.put(
            "migration",
            JSONObject()
                .put("id", "proot_runtime_selection_v2")
                .put("reason", reason)
                .put("boundary", "rewrite_apk_owned_runtime_assets_only")
                .put("rewritablePaths", JSONArray(apkOwnedRuntimePaths))
                .put("preservedUserDataPaths", JSONArray(preservedUserDataPaths))
        )

        val notes = descriptor.optJSONArray("notes") ?: JSONArray()
        if (substrateState == SUBSTRATE_PASS) {
            notes.put("Runtime selection chose a telemetry-capable PRoot asset; user containers and shared data are preserved.")
        } else {
            notes.put("Runtime selection kept stock PRoot as a no-telemetry fallback; user containers and shared data are preserved.")
        }
        descriptor.put("notes", notes)
        markRuntimeRoles(descriptor, runtime.optString("runtimeId"))
        return descriptor
    }

    private fun markRuntimeRoles(descriptor: JSONObject, activeRuntimeId: String) {
        val runtimes = descriptor.optJSONArray("availableRuntimes") ?: return
        for (index in 0 until runtimes.length()) {
            val runtime = runtimes.optJSONObject(index) ?: continue
            val isActive = runtime.optString("runtimeId") == activeRuntimeId
            runtime.put("binaryRole", if (isActive) "active_runtime" else "packaged_candidate")
            if (!isActive &&
                runtime.optString("assetId") == TERMUX_BASELINE_ASSET_ID &&
                runtime.optString("validationState").isBlank()
            ) {
                runtime.put("validationState", "quarantined_after_execve_enosys")
            }
        }
    }

    private fun readInstalledDescriptor(layout: AssetExtractor.RuntimeLayout): JSONObject? {
        if (!layout.prootRuntimeDescriptorFile.exists()) {
            return null
        }
        return runCatching {
            JSONObject(layout.prootRuntimeDescriptorFile.readText())
        }.getOrNull()
    }

    private fun logMigrationIfNeeded(
        layout: AssetExtractor.RuntimeLayout,
        migrationId: String,
        fromAssetId: String,
        toAssetId: String,
        reason: String
    ) {
        if (fromAssetId == toAssetId) {
            return
        }
        runCatching {
            layout.runtimeRoot.mkdirs()
            val logFile = File(layout.runtimeRoot, MIGRATION_LOG_FILE)
            val event = JSONObject()
                .put("generatedAtUnixMs", System.currentTimeMillis())
                .put("migrationId", migrationId)
                .put("component", "proot")
                .put("fromAssetId", fromAssetId)
                .put("toAssetId", toAssetId)
                .put("reason", reason)
                .put("boundary", "rewrite_apk_owned_runtime_assets_only")
                .put("rewritablePaths", JSONArray(apkOwnedRuntimePaths))
                .put("preservedUserDataPaths", JSONArray(preservedUserDataPaths))
            logFile.appendText(event.toString() + "\n")
        }.onFailure { error ->
            Logger.d(TAG, "Failed to write runtime migration log: ${error.message}")
        }
    }

    private fun assetExists(context: Context, assetPath: String): Boolean {
        return runCatching {
            context.assets.open(assetPath).close()
            true
        }.getOrDefault(false)
    }
}
