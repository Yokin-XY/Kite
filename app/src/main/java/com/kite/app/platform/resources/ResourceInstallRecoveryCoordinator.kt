package com.kite.app.platform.resources

import android.content.Context
import com.kite.app.foundation.bootstrap.StartupTraceStore
import com.kite.app.foundation.logging.Logger
import com.kite.app.foundation.toolchain.ResourceInstallRecoverySummary
import com.kite.app.foundation.workspace.KFWorkspaceManager
import com.kite.app.resources.KiteResourceInstallRecipes
import com.kite.app.resources.KiteResourceInstallStore
import com.kite.app.resources.KiteResourceManifestLoader
import com.kite.app.resources.ResourceInstallRecoveryDisposition
import com.kite.app.resources.ResourceInstallTransactionRecovery
import org.json.JSONObject
import java.io.File

/** 把资源目录事务的恢复结果收口回唯一的安装登记表。 */
internal class ResourceInstallRecoveryCoordinator(
    context: Context,
    private val installStore: KiteResourceInstallStore,
    private val manifestLoader: KiteResourceManifestLoader,
    private val transactionRecovery: ResourceInstallTransactionRecovery = ResourceInstallTransactionRecovery(),
) {
    private val appContext = context.applicationContext

    @Synchronized
    fun recover(): ResourceInstallRecoverySummary {
        val workspacePath = KFWorkspaceManager.getCurrentSpace(appContext)?.workspacePath
            ?.takeIf(String::isNotBlank)
            ?: return ResourceInstallRecoverySummary()
        val manifests = manifestLoader.manifests()
        val resourceIds = (manifests.keys + installStore.registrySnapshot().keys)
            .map(KiteResourceInstallRecipes::safeId)
            .filter(String::isNotBlank)
            .distinct()
            .sorted()
        val results = resourceIds.map { resourceId ->
            transactionRecovery.recover(File(workspacePath), resourceId).also { result ->
                val entry = installStore.registryEntry(resourceId)
                when (result.disposition) {
                    ResourceInstallRecoveryDisposition.RESTORED -> {
                        if (entry?.version?.isNotBlank() == true) {
                            installStore.markMaintenanceFailed(
                                resourceId = resourceId,
                                operation = result.operation,
                                explanation = result.message,
                            )
                        } else {
                            installStore.markFailed(
                                resourceId = resourceId,
                                operation = result.operation,
                                runId = entry?.runId,
                                reason = result.message,
                            )
                        }
                    }
                    ResourceInstallRecoveryDisposition.COMMITTED -> {
                        val installedVersion = result.targetVersion
                            .ifBlank { manifests[resourceId]?.version.orEmpty() }
                            .ifBlank { entry?.version.orEmpty() }
                        installStore.markInstalled(
                            resourceId = resourceId,
                            version = installedVersion,
                            runId = entry?.runId,
                            summary = result.message,
                        )
                        manifests[resourceId]?.let { manifest ->
                            val iconJson = JSONObject().apply {
                                if (manifest.iconAsset.isNotBlank()) {
                                    put("type", "asset")
                                    put("value", manifest.iconAsset)
                                    put("fallbackText", manifest.iconText)
                                    if (manifest.iconFit.isNotBlank()) put("fit", manifest.iconFit)
                                } else {
                                    put("type", "text")
                                    put("value", manifest.iconText)
                                }
                            }.toString()
                            installStore.saveInstalledSnapshot(
                                resourceId = resourceId,
                                name = manifest.name,
                                iconJson = iconJson,
                                version = installedVersion,
                                manifestJson = manifest.rawJson.toString(),
                            )
                        }
                    }
                    ResourceInstallRecoveryDisposition.FAILED -> {
                        if (entry?.version?.isNotBlank() == true) {
                            installStore.markMaintenanceFailed(
                                resourceId = resourceId,
                                operation = result.operation,
                                explanation = result.message,
                            )
                        } else {
                            installStore.markFailed(
                                resourceId = resourceId,
                                operation = result.operation,
                                runId = entry?.runId,
                                reason = result.message,
                            )
                        }
                        StartupTraceStore.recordSetupFailure(appContext, resourceId, result.message)
                    }
                    ResourceInstallRecoveryDisposition.NO_ACTION,
                    ResourceInstallRecoveryDisposition.ACTIVE -> Unit
                }
            }
        }
        return ResourceInstallRecoverySummary(
            examined = results.count { it.disposition != ResourceInstallRecoveryDisposition.NO_ACTION },
            restored = results.count { it.disposition == ResourceInstallRecoveryDisposition.RESTORED },
            committed = results.count { it.disposition == ResourceInstallRecoveryDisposition.COMMITTED },
            active = results.count { it.disposition == ResourceInstallRecoveryDisposition.ACTIVE },
            failed = results.count { it.disposition == ResourceInstallRecoveryDisposition.FAILED },
        ).also { summary ->
            if (summary.examined > 0) {
                Logger.i(
                    LOG_TAG,
                    "examined=${summary.examined} restored=${summary.restored} " +
                        "committed=${summary.committed} active=${summary.active} failed=${summary.failed}",
                )
            }
        }
    }

    companion object {
        private const val LOG_TAG = "ResourceInstallRecovery"
    }
}
