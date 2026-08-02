package com.kite.app.feature.runsurface

import com.kite.app.agent.config.AgentLiveConfigSnapshot
import com.kite.app.agent.config.AgentProviderModelSummary
import com.kite.app.agent.config.AgentProviderSummary
import com.kite.app.agent.contract.AgentConfigCategory
import com.kite.app.agent.contract.AgentConfigChoice
import com.kite.app.agent.contract.AgentConfigOption
import com.kite.app.agent.contract.AgentModelSource
import com.kite.app.agent.store.AgentModelLibrarySnapshot
import com.kite.app.agent.store.AgentModelLibraryStore
import com.kite.app.agent.registration.AgentOfficialAccountSpec

internal data class AgentModelProviderProjection(
    val id: String,
    val name: String,
    val models: List<AgentConfigChoice>,
    val source: AgentModelSource,
    val editableProvider: AgentProviderSummary? = null,
    val officialAccount: AgentOfficialAccountSpec? = null,
    val selectedModelValue: String? = null,
    val libraryGroupId: String? = null,
    val visibleInConversation: Boolean = true
)

/** 只做模型库投影，不读取磁盘，也不替代 Agent 原生配置事实。 */
internal object AgentModelLibraryPolicy {
    fun projectProviders(
        snapshot: AgentLiveConfigSnapshot,
        modelOption: AgentConfigOption.Select?,
        library: AgentModelLibrarySnapshot,
        officialAccounts: List<AgentOfficialAccountSpec> = emptyList(),
    ): List<AgentModelProviderProjection> {
        val choicesByGroup = modelOption?.choices.orEmpty()
            .filter { it.groupId?.isNotBlank() == true || it.groupName?.isNotBlank() == true }
            .groupBy { it.groupId ?: it.groupName.orEmpty() }
        val configured = snapshot.providers.map { provider ->
            val displayProvider = provider.copy(
                models = provider.models.map { model ->
                    val libraryModelId = when (provider.source) {
                        AgentModelSource.UserConfigured -> model.id
                        AgentModelSource.Free,
                        AgentModelSource.OfficialLogin -> model.id.takeIf { it.startsWith("${provider.id}/") }
                            ?: "${provider.id}/${model.id}"
                    }
                    model.copy(
                        displayName = library.modelDisplayName(provider.id, libraryModelId, model.displayName)
                    )
                }
            )
            val protocolChoices = choicesByGroup[provider.id]
                ?: choicesByGroup.entries.firstOrNull { (_, choices) ->
                    choices.firstOrNull()?.groupName.equals(provider.displayName, ignoreCase = true)
                }?.value
            val models = protocolChoices
                ?.map { choice -> withDisplayName(choice, library, provider.id) }
                ?: displayProvider.models.map { model -> providerModelChoice(displayProvider, model) }
            val selectedModel = selectedModelValue(snapshot, provider.id, models)
            AgentModelProviderProjection(
                id = provider.id,
                name = provider.displayName,
                models = models,
                source = provider.source,
                editableProvider = displayProvider.takeIf { provider.source == AgentModelSource.UserConfigured },
                selectedModelValue = selectedModel,
                libraryGroupId = when (provider.source) {
                    AgentModelSource.Free -> AgentModelLibraryStore.FREE_GROUP_ID
                    AgentModelSource.OfficialLogin -> AgentModelLibraryStore.OFFICIAL_GROUP_ID
                    AgentModelSource.UserConfigured -> library.providerGroupId(provider.id)
                },
                visibleInConversation = library.isProviderVisible(provider.id)
            )
        }
        val configuredIds = configured.mapTo(linkedSetOf()) { it.id }
        val officialGroupIds = officialAccounts.flatMapTo(linkedSetOf()) { it.modelGroupIds }
        val legacyOfficialAccount = officialAccounts.singleOrNull()
        val legacyOfficialChoices = if (legacyOfficialAccount == null) {
            emptyList()
        } else {
            modelOption?.choices.orEmpty().filter { choice ->
                choice.modelSource == null && choice.providerGroupId() == null
            }
        }
        val official = officialAccounts.map { account ->
            val declaredChoices = account.modelGroupIds
                .flatMap { groupId -> choicesByGroup[groupId].orEmpty() }
            val choices = if (account == legacyOfficialAccount) {
                declaredChoices + legacyOfficialChoices
            } else {
                declaredChoices
            }
            val providerId = officialProviderId(account.id)
            val displayChoices = choices.map { choice -> withDisplayName(choice, library, providerId) }
            val selectedModel = selectedModelValue(snapshot, providerId, displayChoices)
                ?: displayChoices.firstOrNull { it.value == snapshot.defaultModel }?.value
            AgentModelProviderProjection(
                id = providerId,
                name = account.displayName,
                models = displayChoices,
                source = AgentModelSource.OfficialLogin,
                officialAccount = account,
                selectedModelValue = selectedModel,
                libraryGroupId = AgentModelLibraryStore.OFFICIAL_GROUP_ID,
                visibleInConversation = library.isProviderVisible(providerId),
            )
        }
        val free = modelOption?.choices.orEmpty()
            .filterNot { it in legacyOfficialChoices }
            .filter { it.modelSource == AgentModelSource.Free }
            .groupBy { choice -> choice.groupId ?: choice.groupName ?: FREE_PROVIDER_ID }
            .filterKeys { groupId -> groupId !in configuredIds && groupId !in officialGroupIds }
            .map { (groupId, choices) ->
                val providerId = groupId.ifBlank { FREE_PROVIDER_ID }
                val displayChoices = choices.map { choice -> withDisplayName(choice, library, providerId) }
                val selectedModel = selectedModelValue(snapshot, providerId, displayChoices)
                AgentModelProviderProjection(
                    id = providerId,
                    name = choices.firstOrNull()?.groupName?.takeIf(String::isNotBlank)
                        ?: "免费模型",
                    models = displayChoices,
                    source = AgentModelSource.Free,
                    selectedModelValue = selectedModel,
                    visibleInConversation = library.isProviderVisible(providerId)
                )
            }
        return configured + official + free
    }

    fun filterConversationModelOption(
        option: AgentConfigOption.Select,
        library: AgentModelLibrarySnapshot,
        activeProviderId: String? = null,
        officialAccounts: List<AgentOfficialAccountSpec> = emptyList(),
    ): AgentConfigOption.Select {
        if (option.category != AgentConfigCategory.Model) return option
        val currentSourceId = option.choices
            .firstOrNull { it.value == option.currentValue }
            ?.let { sourceIdForChoice(it, officialAccounts) }
        val filteredChoices = option.choices.filter { choice ->
            val sourceId = sourceIdForChoice(choice, officialAccounts)
            val sourceVisible = sourceId == null ||
                sourceId == currentSourceId ||
                sourceId == activeProviderId ||
                library.isProviderVisible(sourceId)
            val modelVisible = sourceId == null ||
                choice.value == option.currentValue ||
                library.isModelVisible(sourceId, choice.value)
            sourceVisible && modelVisible
        }.ifEmpty {
            option.choices.filter { it.value == option.currentValue }
        }
        val visibleChoices = filteredChoices.map { choice ->
            val sourceId = sourceIdForChoice(choice, officialAccounts)
            if (sourceId == null) choice else withDisplayName(choice, library, sourceId)
        }
        return option.copy(choices = visibleChoices)
    }

    internal fun sourceIdForChoice(
        choice: AgentConfigChoice,
        officialAccounts: List<AgentOfficialAccountSpec>
    ): String? {
        val groupId = choice.providerGroupId()
            ?: return officialAccounts.singleOrNull()
                ?.takeIf { choice.modelSource == null }
                ?.let { officialProviderId(it.id) }
        val official = officialAccounts.firstOrNull { groupId in it.modelGroupIds }
        return official?.let { officialProviderId(it.id) } ?: groupId
    }

    internal fun withDisplayName(
        choice: AgentConfigChoice,
        library: AgentModelLibrarySnapshot,
        sourceId: String
    ): AgentConfigChoice {
        val names = library.providers[sourceId]?.modelDisplayNames.orEmpty()
        val exactName = names[choice.value]
        val legacyModelId = choice.value.removePrefix("$sourceId/")
            .takeIf { it.isNotBlank() && it != choice.value }
        val legacyName = legacyModelId?.let(names::get)
        val displayName = exactName ?: legacyName ?: return choice
        val modelId = if (exactName != null) choice.value else legacyModelId ?: choice.value
        return choice.copy(
            name = displayName,
            description = modelId.takeIf { it != displayName }
        )
    }

    private fun selectedModelValue(
        snapshot: AgentLiveConfigSnapshot,
        providerId: String,
        choices: List<AgentConfigChoice>
    ): String? {
        val modelId = snapshot.defaultModel?.takeIf(String::isNotBlank) ?: return null
        choices.firstOrNull { it.value == modelId }?.let { return it.value }
        if (snapshot.activeProviderId != providerId) return null
        val qualifiedModelId = modelId.takeIf { it.startsWith("$providerId/") } ?: "$providerId/$modelId"
        return qualifiedModelId.takeIf { candidate ->
            choices.isEmpty() || choices.any { it.value == candidate }
        }
    }

    private fun providerModelChoice(
        provider: AgentProviderSummary,
        model: AgentProviderModelSummary
    ): AgentConfigChoice = AgentConfigChoice(
        value = "${provider.id}/${model.id}",
        name = model.displayName,
        description = model.id.takeIf { it != model.displayName },
        groupId = provider.id,
        groupName = provider.displayName,
        modelSource = provider.source,
    )

    private fun AgentConfigChoice.providerGroupId(): String? =
        groupId?.takeIf(String::isNotBlank) ?: groupName?.takeIf(String::isNotBlank)

    private const val FREE_PROVIDER_ID = "__kite_free__"
    internal fun officialProviderId(accountId: String): String = "__kite_official__:$accountId"
}
