package com.kite.app.feature.runsurface

import com.kite.app.agent.contract.AgentConfigCategory
import com.kite.app.agent.contract.AgentConfigChoice
import com.kite.app.agent.contract.AgentConfigOption
import com.kite.app.agent.config.AgentLiveConfigSnapshot
import com.kite.app.agent.runtime.AgentDraftModelSelection
import com.kite.app.agent.registration.AgentOfficialAccountSpec
import com.kite.app.agent.store.AgentModelLibrarySnapshot

internal object AgentDraftModelPolicy {
    const val CONFIG_ID = "kite.draft.model"

    fun defaultSelection(snapshot: AgentLiveConfigSnapshot): AgentDraftModelSelection? {
        val provider = snapshot.providers.firstOrNull { it.id == snapshot.activeProviderId }
            ?: return null
        val model = provider.models.firstOrNull { candidate ->
            snapshot.defaultModel == candidate.id ||
                snapshot.defaultModel == "${provider.id}/${candidate.id}"
        } ?: provider.models.firstOrNull() ?: return null
        return AgentDraftModelSelection(provider.id, model.id, usesAgentDefault = true)
    }

    fun contains(snapshot: AgentLiveConfigSnapshot, selection: AgentDraftModelSelection): Boolean =
        snapshot.providers.any { provider ->
            provider.id == selection.providerId && provider.models.any { it.id == selection.modelId }
        }

    fun option(
        snapshot: AgentLiveConfigSnapshot,
        selected: AgentDraftModelSelection?,
        library: AgentModelLibrarySnapshot = AgentModelLibrarySnapshot(),
        officialAccounts: List<AgentOfficialAccountSpec> = emptyList(),
    ): AgentConfigOption.Select? {
        val configuredChoices = snapshot.providers.flatMap { provider ->
            provider.models.map { model ->
                val sourceId = AgentModelLibraryPolicy.sourceIdForChoice(
                    AgentConfigChoice(
                        value = choiceValue(provider.id, model.id),
                        name = model.displayName,
                        groupId = provider.id,
                        groupName = provider.displayName,
                        modelSource = provider.source,
                    ),
                    officialAccounts,
                ) ?: provider.id
                val libraryModelId = when (provider.source) {
                    com.kite.app.agent.contract.AgentModelSource.UserConfigured -> model.id
                    com.kite.app.agent.contract.AgentModelSource.Free,
                    com.kite.app.agent.contract.AgentModelSource.OfficialLogin ->
                        model.id.takeIf { it.startsWith("${provider.id}/") }
                            ?: "${provider.id}/${model.id}"
                }
                val displayName = library.modelDisplayName(sourceId, libraryModelId, model.displayName)
                AgentConfigChoice(
                    value = choiceValue(provider.id, model.id),
                    name = displayName,
                    description = libraryModelId.takeIf { it != displayName },
                    groupId = provider.id,
                    groupName = provider.displayName,
                    modelSource = provider.source,
                )
            }
        }
        val choices = configuredChoices.distinctBy(AgentConfigChoice::value)
        if (choices.isEmpty()) return null
        val current = selected?.takeIf { selection -> contains(choices, selection) }
            ?: defaultSelection(snapshot)
            ?: return null
        return AgentConfigOption.Select(
            id = CONFIG_ID,
            name = "模型",
            category = AgentConfigCategory.Model,
            currentValue = choiceValue(current.providerId, current.modelId),
            choices = choices
        )
    }

    fun selection(
        snapshot: AgentLiveConfigSnapshot,
        value: String,
    ): AgentDraftModelSelection? {
        val default = defaultSelection(snapshot)
        snapshot.providers.forEach { provider ->
            provider.models.forEach { model ->
                if (choiceValue(provider.id, model.id) == value) {
                    return AgentDraftModelSelection(
                        provider.id,
                        model.id,
                        usesAgentDefault = default?.providerId == provider.id && default.modelId == model.id
                    )
                }
            }
        }
        return null
    }

    fun contains(option: AgentConfigOption.Select?, selection: AgentDraftModelSelection): Boolean =
        option?.choices.orEmpty().any { it.value == choiceValue(selection.providerId, selection.modelId) }

    private fun contains(choices: List<AgentConfigChoice>, selection: AgentDraftModelSelection): Boolean =
        choices.any { it.value == choiceValue(selection.providerId, selection.modelId) }

    private fun choiceValue(providerId: String, modelId: String): String =
        "${providerId.length}:$providerId$modelId"
}
