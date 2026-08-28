package com.kite.app.agent.registration

import android.content.Context
import com.kite.app.agent.runtime.AgentAttachProviderRegistry
import com.kite.app.resources.KiteResourceAgentProfile
import com.kite.app.resources.KiteResourceInstallStore
import com.kite.app.resources.KiteResourceManifest
import com.kite.app.resources.KiteResourceManifestLoader
import com.kite.app.run.CardRunStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge

data class KiteAgentRegistrySignal(
    val reason: String,
    val affectedAgentIds: List<String>,
    val resourceId: String? = null
)

interface AgentRegistryDependenciesOwner {
    val agentRegistry: KiteAgentRegistry
}

internal object AgentResourceRegistrationMapper {
    fun registrations(manifest: KiteResourceManifest): List<AgentRegistration> =
        manifest.agentProfiles.mapNotNull { profile -> profile.toRegistration(manifest) }

    private fun KiteResourceAgentProfile.toRegistration(
        manifest: KiteResourceManifest
    ): AgentRegistration? {
        val launch = when (launchMode) {
            MODE_MANAGED -> AgentLaunchSpec.Managed(
                providerId = providerId,
                protocol = protocol,
                transport = transport,
                argv = argv,
                runtimeGuarantees = runtimeGuarantees,
                runtimeGuaranteeEvidence = runtimeGuaranteeEvidence,
            )
            MODE_ATTACH -> AgentLaunchSpec.Attach(
                providerId = providerId,
                protocol = protocol,
                transport = transport,
                connectionReference = connectionReference
            )
            else -> return null
        }
        return AgentRegistration(
            definition = AgentDefinition(
                agentId = agentId,
                displayName = displayName.ifBlank { manifest.name.ifBlank { agentId } },
                description = description.ifBlank { manifest.description },
                iconText = manifest.iconText
            ),
            source = AgentRegistrationSource.Resource(manifest.id),
            launch = launch,
            configurationRequired = configurationRequired,
            configAdapterId = configAdapterId.ifBlank { null },
            sessionAdapterId = sessionAdapterId.ifBlank { null },
            officialAccounts = officialAccounts.map { account ->
                AgentOfficialAccountSpec(
                    id = account.id,
                    displayName = account.displayName,
                    modelGroupIds = account.modelGroupIds,
                    status = account.status?.toRegistrationCommand(),
                    login = account.login.toRegistrationCommand(),
                    logout = account.logout?.toRegistrationCommand(),
                )
            }
        ).takeIf { AgentRegistrationPolicy.problem(it) == null }
    }

    private fun com.kite.app.resources.KiteResourceAgentAccountCommand.toRegistrationCommand() =
        AgentOfficialAccountCommand(
            argv = argv,
            loggedInPatterns = loggedInPatterns,
            loggedOutPatterns = loggedOutPatterns,
            successPatterns = successPatterns,
            timeoutMs = timeoutMs,
        )

    private const val MODE_MANAGED = "managed"
    private const val MODE_ATTACH = "attach"
}

/**
 * Agent 名册的组合事实读取器。
 *
 * 资源声明和自定义登记拥有定义；KiteResourceInstallStore 拥有安装事实；CardRunStore 拥有运行事实。
 * 本类只组合投影，不把这些状态复制到另一份数据库。
 */
class KiteAgentRegistry(
    context: Context,
    private val manifestLoader: KiteResourceManifestLoader = KiteResourceManifestLoader(context.applicationContext),
    private val installStore: KiteResourceInstallStore = KiteResourceInstallStore(context.applicationContext),
    private val customStore: KiteCustomAgentRegistrationStore =
        KiteCustomAgentRegistrationStore(context.applicationContext),
    private val configurationStatus: (String) -> AgentConfigurationStatus? = { null },
    private val resourceRegistrationSource: (() -> List<AgentRegistration>)? = null,
    private val attachAvailable: (String) -> Boolean = AgentAttachProviderRegistry::contains,
    private val runningProviderIds: () -> Set<String> = {
        CardRunStore.snapshot()
            .mapNotNull { it.agentBinding?.takeIf { binding -> binding.isActive() }?.providerId }
            .toSet()
    }
) {
    private val definitionLock = Any()
    @Volatile
    private var cachedResourceRegistrations: List<AgentRegistration>? = null
    @Volatile
    private var cachedAgentIdsByResource: Map<String, List<String>>? = null

    private fun resourceRegistrations(): List<AgentRegistration> =
        cachedResourceRegistrations ?: synchronized(definitionLock) {
            cachedResourceRegistrations ?: (
                resourceRegistrationSource?.invoke()
                    ?: manifestLoader.manifests().values.flatMap(AgentResourceRegistrationMapper::registrations)
                ).also { cachedResourceRegistrations = it }
        }

    private fun agentIdsByResource(): Map<String, List<String>> =
        cachedAgentIdsByResource ?: synchronized(definitionLock) {
            cachedAgentIdsByResource ?: resourceRegistrations()
            .mapNotNull { registration ->
                val source = registration.source as? AgentRegistrationSource.Resource ?: return@mapNotNull null
                source.resourceId to registration.definition.agentId
            }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, agentIds) -> agentIds.distinct() }
            .also { cachedAgentIdsByResource = it }
        }

    val signals: Flow<KiteAgentRegistrySignal> = merge(
        installStore.signals.drop(1).map { signal ->
            val resourceIds = (signal.affectedResourceIds + signal.resourceId.orEmpty())
                .filter(String::isNotBlank)
                .distinct()
            KiteAgentRegistrySignal(
                reason = "resource:${signal.reason}",
                affectedAgentIds = resourceIds.flatMap { agentIdsByResource()[it].orEmpty() }.distinct(),
                resourceId = signal.resourceId
            )
        },
        customStore.signals.drop(1).map { signal ->
            KiteAgentRegistrySignal(
                reason = "custom:${signal.reason}",
                affectedAgentIds = listOfNotNull(signal.agentId)
            )
        }
    )

    fun snapshot(): AgentRegistrySnapshot {
        val custom = customStore.snapshot()
        val registrations = resourceRegistrations() + custom
        val installed = installStore.registrySnapshot().values
            .filter { it.installed }
            .map { it.resourceId }
            .toSet()
        val configurationByAgentId = registrations.associate { registration ->
            val agentId = registration.definition.agentId
            agentId to (
                configurationStatus(agentId)
                    ?: if (registration.configurationRequired) {
                        AgentConfigurationStatus.Required
                    } else {
                        AgentConfigurationStatus.NotRequired
                    }
                )
        }
        return AgentRegistryAssembler.assemble(
            registrations = registrations,
            installedResourceIds = installed,
            configurationByAgentId = configurationByAgentId,
            runningProviderIds = runningProviderIds(),
            launchStatus = { launch ->
                when (launch) {
                    is AgentLaunchSpec.Managed -> AgentLaunchStatus.Ready
                    is AgentLaunchSpec.Attach -> if (attachAvailable(launch.connectionReference)) {
                        AgentLaunchStatus.Ready
                    } else {
                        AgentLaunchStatus.Unsupported
                    }
                }
            }
        )
    }

    fun registerCustom(registration: AgentRegistration): AgentRegistrationWriteResult =
        customStore.register(registration, reservedAgentIds = resourceAgentIds())

    fun updateCustom(registration: AgentRegistration): AgentRegistrationWriteResult =
        customStore.update(registration, reservedAgentIds = resourceAgentIds())

    fun removeCustom(agentId: String): Boolean = customStore.remove(agentId)

    fun invalidateResourceDefinitions() {
        synchronized(definitionLock) {
            cachedResourceRegistrations = null
            cachedAgentIdsByResource = null
        }
    }

    private fun resourceAgentIds(): Set<String> =
        resourceRegistrations().map { it.definition.agentId }.toSet()

}
