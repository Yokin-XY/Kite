package com.kite.app.platform.resources

import com.kite.app.resources.KiteResourceManifest
import com.kite.app.resources.KiteResourceRelationTargets

internal data class ResourceOpenDependencyClosure(
    val manifests: List<KiteResourceManifest>,
    val missingInstalledResourceIds: Set<String>,
    val unresolvedRequirements: Set<String>,
)

/** 解析资源打开时真正需要的基础依赖；可选扩展和默认推荐不参与运行前校验。 */
internal object ResourceOpenDependencyResolver {
    fun resolve(
        targetResourceId: String,
        manifests: Collection<KiteResourceManifest>,
        installedResourceIds: Set<String>,
        relationTargetsFor: (String) -> KiteResourceRelationTargets,
    ): ResourceOpenDependencyClosure {
        val manifestsById = manifests.associateBy(KiteResourceManifest::id)
        val ordered = linkedMapOf<String, KiteResourceManifest>()
        val unresolved = linkedSetOf<String>()
        val visiting = linkedSetOf<String>()

        fun visit(resourceId: String) {
            if (resourceId in ordered || !visiting.add(resourceId)) return
            val manifest = manifestsById[resourceId]
            if (manifest == null) {
                unresolved += resourceId
                visiting.remove(resourceId)
                return
            }
            relationTargetsFor(resourceId).base.forEach { requirement ->
                val availableProviders = requirement.providerIds.filter(manifestsById::containsKey)
                val providerId = availableProviders.firstOrNull(installedResourceIds::contains)
                    ?: availableProviders.firstOrNull()
                if (providerId == null) unresolved += requirement.requirement else visit(providerId)
            }
            visiting.remove(resourceId)
            ordered[resourceId] = manifest
        }

        visit(targetResourceId)
        return ResourceOpenDependencyClosure(
            manifests = ordered.values.toList(),
            missingInstalledResourceIds = ordered.keys.filterNotTo(linkedSetOf(), installedResourceIds::contains),
            unresolvedRequirements = unresolved,
        )
    }
}
