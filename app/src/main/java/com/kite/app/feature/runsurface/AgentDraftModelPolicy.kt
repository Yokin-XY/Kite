package com.kite.app.feature.runsurface

import com.kite.app.agent.contract.AgentConfigCategory
import com.kite.app.agent.contract.AgentConfigChoice
import com.kite.app.agent.contract.AgentConfigOption
import com.kite.app.agent.config.AgentLiveConfigSnapshot
import com.kite.app.agent.runtime.AgentDraftModelSelection
import com.kite.app.agent.store.AgentModelLibrarySnapshot

internal object AgentPersistentDefaultPolicy {
    fun savedMessage(providerName: String, currentAgent: Boolean): String = if (currentAgent) {
        "$providerName 已设为默认；正在进行的会话不会改变"
    } else {
        "$providerName 已设为默认；下次打开该 Agent 时使用"
    }

    fun configurationSavedMessage(successMessage: String, currentAgent: Boolean): String = if (currentAgent) {
        "$successMessage；正在进行的会话不会改变"
    } else {
        "$successMessage；下次打开该 Agent 时使用"
    }
}

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
        discovered: AgentConfigOption.Select? = null,
        library: AgentModelLibrarySnapshot = AgentModelLibrarySnapshot()
    ): AgentConfigOption.Select? {
        val configuredChoices = snapshot.providers.flatMap { provider ->
            provider.models.map { model ->
                AgentConfigChoice(
                    value = choiceValue(provider.id, model.id),
                    name = library.modelDisplayName(provider.id, model.id, model.displayName),
                    description = model.id.takeIf {
                        it != library.modelDisplayName(provider.id, model.id, model.displayName)
                    },
                    groupId = provider.id,
                    groupName = provider.displayName
                )
            }
        }
        val discoveredChoices = discovered?.choices.orEmpty().mapNotNull { choice ->
            val providerId = choice.groupId?.trim()?.takeIf(String::isNotBlank) ?: return@mapNotNull null
            val modelId = choice.value.removePrefix("$providerId/").trim().takeIf(String::isNotBlank)
                ?: return@mapNotNull null
            AgentConfigChoice(
                value = choiceValue(providerId, modelId),
                name = choice.name,
                description = choice.description,
                groupId = providerId,
                groupName = choice.groupName?.takeIf(String::isNotBlank) ?: providerId
            )
        }
        val choices = (configuredChoices + discoveredChoices).distinctBy(AgentConfigChoice::value)
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
        availableChoices: List<AgentConfigChoice> = emptyList()
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
        if (availableChoices.none { it.value == value }) return null
        val decoded = decodeChoiceValue(value) ?: return null
        return AgentDraftModelSelection(
            providerId = decoded.first,
            modelId = decoded.second,
            usesAgentDefault = default?.providerId == decoded.first && default.modelId == decoded.second
        )
    }

    fun contains(option: AgentConfigOption.Select?, selection: AgentDraftModelSelection): Boolean =
        option?.choices.orEmpty().any { it.value == choiceValue(selection.providerId, selection.modelId) }

    private fun contains(choices: List<AgentConfigChoice>, selection: AgentDraftModelSelection): Boolean =
        choices.any { it.value == choiceValue(selection.providerId, selection.modelId) }

    private fun decodeChoiceValue(value: String): Pair<String, String>? {
        val separator = value.indexOf(':')
        if (separator <= 0) return null
        val providerLength = value.substring(0, separator).toIntOrNull() ?: return null
        val providerStart = separator + 1
        val modelStart = providerStart + providerLength
        if (providerLength <= 0 || modelStart >= value.length) return null
        val providerId = value.substring(providerStart, modelStart)
        val modelId = value.substring(modelStart)
        return (providerId to modelId).takeIf { providerId.isNotBlank() && modelId.isNotBlank() }
    }

    private fun choiceValue(providerId: String, modelId: String): String =
        "${providerId.length}:$providerId$modelId"
}
