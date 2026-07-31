package com.kite.app.application.resources

import com.kite.app.resources.KiteResourceManifest

internal data class ResourceDependencyBlocker(
    val resourceId: String,
    val resourceName: String,
    val requirement: String
)

/**
 * 卸载前的反向依赖检查。只根据清单关系和当前安装集合判断，
 * 不在卡片或 UI 内维护第二份依赖状态。
 */
internal class ResourceDependencyGuard(
    private val providerIdsFor: (String) -> List<String>
) {
    fun blockers(
        targetResourceId: String,
        manifests: Collection<KiteResourceManifest>,
        installedResourceIds: Set<String>
    ): List<ResourceDependencyBlocker> {
        val targetId = targetResourceId.trim()
        if (targetId.isBlank() || targetId !in installedResourceIds) return emptyList()
        return manifests.asSequence()
            .filter { it.id != targetId && it.id in installedResourceIds }
            .flatMap { dependent ->
                (dependent.baseRequirements + dependent.defaultRequirements)
                    .asSequence()
                    .distinct()
                    .filter { requirement ->
                        val providers = providerIdsFor(requirement).toSet()
                        targetId in providers && providers.none { provider ->
                            provider != targetId && provider in installedResourceIds
                        }
                    }
                    .map { requirement ->
                        ResourceDependencyBlocker(
                            resourceId = dependent.id,
                            resourceName = dependent.name.ifBlank { dependent.id },
                            requirement = requirement
                        )
                    }
            }
            .distinctBy { Triple(it.resourceId, it.resourceName, it.requirement) }
            .sortedWith(compareBy(ResourceDependencyBlocker::resourceName, ResourceDependencyBlocker::requirement))
            .toList()
    }
}
