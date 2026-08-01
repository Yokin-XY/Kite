package com.kite.app.platform.resources

import com.kite.app.resources.KiteResourceInstallStore
import com.kite.app.resources.KiteResourceManagementMode
import com.kite.app.resources.KiteResourceManifest
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal data class SystemManagedResourceFactsConvergence(
    val readyResourceIds: Set<String> = emptySet(),
    val restoredResourceIds: Set<String> = emptySet(),
)

/**
 * 在用户触发的资源动作路径中，把系统管理组件的真实命令事实收敛回资源注册表。
 *
 * 页面仍只消费 KiteResourceInstallStore；这里不持有第二份状态，也不进入 render/bind。
 */
internal class SystemManagedResourceFactsReconciler(
    private val installStore: KiteResourceInstallStore,
    private val installedStateProbe: ResourceInstalledStateProbe,
) {
    private val reconcileMutex = Mutex()

    suspend fun reconcile(
        manifests: Collection<KiteResourceManifest>,
        environmentId: String,
    ): Result<SystemManagedResourceFactsConvergence> = reconcileMutex.withLock {
        val candidates = manifests
            .asSequence()
            .filter { manifest ->
                manifest.management.mode == KiteResourceManagementMode.SYSTEM_COMPONENT &&
                    manifest.management.managedCommands.isNotEmpty()
            }
            .filter { manifest ->
                val entry = installStore.registryEntry(manifest.id, environmentId)
                entry?.installed != true
            }
            .distinctBy(KiteResourceManifest::id)
            .toList()
        if (candidates.isEmpty()) {
            return@withLock Result.success(SystemManagedResourceFactsConvergence())
        }

        val requirements = ResourceManagedCommandProbeProtocol.normalize(
            candidates.map { manifest ->
                ResourceManagedCommandRequirement(
                    resourceId = manifest.id,
                    commands = manifest.management.managedCommands,
                )
            }
        )
        if (requirements.isEmpty()) {
            return@withLock Result.success(SystemManagedResourceFactsConvergence())
        }

        installedStateProbe.missingResourceIds(requirements).map { missingResourceIds ->
            val missing = missingResourceIds.toSet()
            val ready = candidates
                .filterNot { manifest -> manifest.id in missing }
                .mapTo(linkedSetOf(), KiteResourceManifest::id)
            val restored = linkedSetOf<String>()
            candidates.forEach { manifest ->
                if (manifest.id !in ready) return@forEach
                val entry = installStore.registryEntry(manifest.id, environmentId)
                if (entry?.installed == true) return@forEach
                installStore.markInstalled(
                    resourceId = manifest.id,
                    version = manifest.version,
                    runId = null,
                    summary = "系统组件已通过真实命令校验，登记已恢复",
                    environmentId = environmentId,
                )
                restored += manifest.id
            }
            SystemManagedResourceFactsConvergence(
                readyResourceIds = ready,
                restoredResourceIds = restored,
            )
        }
    }
}

internal fun pendingInstallPlanResourceIds(
    resourceIds: Collection<String>,
    targetResourceId: String,
    installedResourceIds: Set<String>,
): List<String> = resourceIds
    .filter { resourceId -> resourceId == targetResourceId || resourceId !in installedResourceIds }
    .distinct()
