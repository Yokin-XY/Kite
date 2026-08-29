package com.kite.app.feature.recipeeditor

import com.kite.app.action.KiteRecipeActionIntent
import com.kite.app.action.KiteRecipeActionRequest
import com.kite.app.agent.registration.AgentRegistryEntry
import com.kite.app.recipe.KiteCardGroup
import com.kite.app.recipe.KiteRecipe
import com.kite.app.recipe.KiteRecipeIcon
import com.kite.app.recipe.KiteRecipeStep
import com.kite.app.recipe.NewRecipeInput
import com.kite.app.recipe.NewRecipeStepInput
import com.kite.app.run.CardRunState
import com.kite.app.run.KiteCardRunUiProjection
import org.json.JSONArray
import org.json.JSONObject

internal data class RecipeEditorStepDraft(
    val type: String,
    val command: String = "",
    val url: String = "",
    val workdir: String = "",
    val agentId: String = "",
    /** 只用于尚未完成 agentId 迁移的旧卡草稿，绝不写入新卡。 */
    val legacyProviderId: String = ""
) {
    fun normalized(): RecipeEditorStepDraft = copy(
        command = command.trim(),
        url = url.trim(),
        workdir = workdir.trim(),
        agentId = agentId.trim(),
        legacyProviderId = legacyProviderId.trim()
    )

    fun toInput(): NewRecipeStepInput = NewRecipeStepInput(
        type = type,
        command = command.trim(),
        url = url.trim(),
        workdir = workdir.trim(),
        agentId = agentId.trim()
    )

    companion object {
        fun terminal(command: String = ""): RecipeEditorStepDraft =
            RecipeEditorStepDraft(KiteRecipe.STEP_TERMINAL, command = command)

        fun shell(command: String = "", workdir: String = ""): RecipeEditorStepDraft =
            RecipeEditorStepDraft(KiteRecipe.STEP_SHELL, command = command, workdir = workdir)

        fun openWeb(url: String = ""): RecipeEditorStepDraft =
            RecipeEditorStepDraft(KiteRecipe.STEP_OPEN_WEB, url = url)

        fun agent(agentId: String, workdir: String = "/workspace"): RecipeEditorStepDraft =
            RecipeEditorStepDraft(KiteRecipe.STEP_AGENT, workdir = workdir, agentId = agentId)

        fun fromStep(
            step: KiteRecipeStep,
            agents: List<AgentRegistryEntry> = emptyList()
        ): RecipeEditorStepDraft {
            val explicitAgentId = step.agentId.orEmpty()
            val legacyProviderId = step.providerId.orEmpty().takeIf { explicitAgentId.isBlank() }.orEmpty()
            val migratedAgentId = if (legacyProviderId.isBlank()) {
                explicitAgentId
            } else {
                agents
                    .filter { it.registration.launch.providerId == legacyProviderId }
                    .singleOrNull()
                    ?.registration
                    ?.definition
                    ?.agentId
                    .orEmpty()
            }
            return RecipeEditorStepDraft(
                type = step.type,
                command = (step.cmd ?: step.text).orEmpty().trimEnd('\n'),
                url = step.url.orEmpty(),
                workdir = step.workdir.orEmpty(),
                agentId = migratedAgentId,
                legacyProviderId = legacyProviderId.takeIf { migratedAgentId.isBlank() }.orEmpty()
            )
        }
    }
}

internal data class RecipeEditorDraft(
    val editingRecipeId: String = "",
    val selectedType: String = KiteRecipe.TYPE_COMMAND_WEB,
    val selectedIconName: String = KiteRecipeIcon.defaultNameForType(KiteRecipe.TYPE_COMMAND_WEB),
    val selectedIconType: String = KiteRecipeIcon.TYPE_BUILTIN,
    val selectedIconSource: String = "",
    val groupId: String = "",
    val name: String = "",
    val description: String = "",
    val launchOpenInstance: Boolean = true,
    val keepFinishedNotification: Boolean = false,
    val steps: List<RecipeEditorStepDraft> = emptyList()
) {
    fun normalized(): RecipeEditorDraft {
        val type = inferType(steps)
        val imageIcon = selectedIconType == KiteRecipeIcon.TYPE_IMAGE && selectedIconSource.isNotBlank()
        return copy(
            selectedType = type,
            selectedIconName = if (imageIcon) {
                selectedIconName.ifBlank { "custom" }
            } else {
                KiteRecipeIcon.normalizeName(selectedIconName, type)
            },
            selectedIconType = if (imageIcon) KiteRecipeIcon.TYPE_IMAGE else KiteRecipeIcon.TYPE_BUILTIN,
            selectedIconSource = if (imageIcon) selectedIconSource.trim() else "",
            groupId = groupId.trim(),
            name = name.trim(),
            description = description.trim(),
            steps = steps.map(RecipeEditorStepDraft::normalized)
        )
    }

    fun validationErrors(agents: List<AgentRegistryEntry> = emptyList()): List<RecipeEditorValidationError> = buildList {
        val value = normalized()
        if (value.name.isBlank()) add(RecipeEditorValidationError("name", "请输入名称"))
        if (value.steps.isEmpty()) {
            add(RecipeEditorValidationError("steps", "请至少添加一个动作"))
        }
        value.steps.forEachIndexed { index, step ->
            when {
                step.type == KiteRecipe.STEP_OPEN_WEB && step.url.isBlank() ->
                    add(RecipeEditorValidationError("steps", "第 ${index + 1} 个打开网页步骤缺少地址", index))
                step.type == KiteRecipe.STEP_SHELL && step.command.isBlank() ->
                    add(RecipeEditorValidationError("steps", "第 ${index + 1} 个 sh 命令步骤缺少命令", index))
                step.type == KiteRecipe.STEP_AGENT && step.agentId.isBlank() -> add(
                    RecipeEditorValidationError(
                        "steps",
                        if (step.legacyProviderId.isNotBlank()) {
                            "第 ${index + 1} 个旧 Agent provider 无法唯一对应：${step.legacyProviderId}"
                        } else {
                            "第 ${index + 1} 个 Agent 步骤尚未选择 Agent"
                        },
                        index
                    )
                )
                step.type == KiteRecipe.STEP_AGENT && agents.none {
                    it.registration.definition.agentId == step.agentId
                } -> add(
                    RecipeEditorValidationError(
                        "steps",
                        "第 ${index + 1} 个 Agent 未登记或存在 ID 冲突：${step.agentId}",
                        index
                    )
                )
            }
        }
    }

    fun toInput(groups: List<KiteCardGroup>, original: KiteRecipe?): NewRecipeInput {
        val value = normalized()
        val group = groups.firstOrNull { it.id == value.groupId }
        return NewRecipeInput(
            id = original?.id ?: value.editingRecipeId.takeIf(String::isNotBlank),
            type = value.selectedType,
            name = value.name,
            category = group?.name ?: original?.category.orEmpty(),
            groupId = group?.id ?: original?.groupId.orEmpty(),
            url = value.steps.firstOrNull { it.type == KiteRecipe.STEP_OPEN_WEB }?.url.orEmpty(),
            command = "",
            shortcut = false,
            openInstanceOnStart = value.launchOpenInstance,
            keepFinishedNotification = value.keepFinishedNotification,
            iconName = value.selectedIconName,
            iconType = value.selectedIconType,
            iconSource = value.selectedIconSource,
            description = value.description,
            steps = value.steps.map(RecipeEditorStepDraft::toInput)
        )
    }

    fun reconcileAgents(agents: List<AgentRegistryEntry>): RecipeEditorDraft = copy(
        steps = steps.map { step ->
            if (
                step.type != KiteRecipe.STEP_AGENT || step.agentId.isNotBlank() ||
                step.legacyProviderId.isBlank()
            ) {
                step
            } else {
                val agentId = agents
                    .filter { it.registration.launch.providerId == step.legacyProviderId }
                    .singleOrNull()
                    ?.registration
                    ?.definition
                    ?.agentId
                    .orEmpty()
                if (agentId.isBlank()) step else step.copy(agentId = agentId, legacyProviderId = "")
            }
        }
    ).normalized()

    fun toJson(): JSONObject = JSONObject()
        .put("editingRecipeId", editingRecipeId)
        .put("selectedType", selectedType)
        .put("selectedIconName", selectedIconName)
        .put("selectedIconType", selectedIconType)
        .put("selectedIconSource", selectedIconSource)
        .put("groupId", groupId)
        .put("name", name)
        .put("description", description)
        .put("launchOpenInstance", launchOpenInstance)
        .put("keepFinishedNotification", keepFinishedNotification)
        .put("steps", JSONArray().apply {
            steps.forEach { step ->
                put(JSONObject()
                    .put("type", step.type)
                    .put("command", step.command)
                    .put("url", step.url)
                    .put("workdir", step.workdir)
                    .put("agentId", step.agentId)
                    .put("legacyProviderId", step.legacyProviderId))
            }
        })

    companion object {
        fun empty(): RecipeEditorDraft = RecipeEditorDraft()

        fun fromRecipe(
            recipe: KiteRecipe,
            agents: List<AgentRegistryEntry> = emptyList()
        ): RecipeEditorDraft {
            val iconType = recipe.icon.type.takeIf { it == KiteRecipeIcon.TYPE_IMAGE && recipe.icon.source.isNotBlank() }
                ?: KiteRecipeIcon.TYPE_BUILTIN
            val stepDrafts = recipe.steps.map { RecipeEditorStepDraft.fromStep(it, agents) }
            val inferred = inferType(stepDrafts)
            return RecipeEditorDraft(
                editingRecipeId = recipe.id,
                selectedType = inferred,
                selectedIconName = if (iconType == KiteRecipeIcon.TYPE_IMAGE) {
                    recipe.icon.name.ifBlank { "custom" }
                } else {
                    KiteRecipeIcon.normalizeName(recipe.icon.name, inferred)
                },
                selectedIconType = iconType,
                selectedIconSource = if (iconType == KiteRecipeIcon.TYPE_IMAGE) recipe.icon.source else "",
                groupId = recipe.groupId,
                name = recipe.name,
                description = recipe.description,
                launchOpenInstance = recipe.launch.openInstance,
                keepFinishedNotification = recipe.launch.keepFinishedNotification,
                steps = stepDrafts
            ).normalized()
        }

        fun fromJson(raw: String): RecipeEditorDraft? = runCatching {
            val json = JSONObject(raw)
            val stepsJson = json.optJSONArray("steps") ?: JSONArray()
            val steps = buildList {
                for (index in 0 until stepsJson.length()) {
                    val item = stepsJson.optJSONObject(index) ?: continue
                    add(RecipeEditorStepDraft(
                        type = item.optString("type").ifBlank { KiteRecipe.STEP_SHELL },
                        command = item.optString("command"),
                        url = item.optString("url"),
                        workdir = item.optString("workdir"),
                        agentId = item.optString("agentId"),
                        legacyProviderId = item.optString("legacyProviderId")
                    ))
                }
            }
            RecipeEditorDraft(
                editingRecipeId = json.optString("editingRecipeId"),
                selectedType = json.optString("selectedType").ifBlank { inferType(steps) },
                selectedIconName = json.optString("selectedIconName").ifBlank {
                    KiteRecipeIcon.defaultNameForType(json.optString("selectedType"))
                },
                selectedIconType = json.optString("selectedIconType").ifBlank { KiteRecipeIcon.TYPE_BUILTIN },
                selectedIconSource = json.optString("selectedIconSource"),
                groupId = json.optString("groupId"),
                name = json.optString("name"),
                description = json.optString("description"),
                launchOpenInstance = json.optBoolean("launchOpenInstance", true),
                keepFinishedNotification = json.optBoolean("keepFinishedNotification", false),
                steps = steps
            ).normalized()
        }.getOrNull()

        fun inferType(steps: List<RecipeEditorStepDraft>): String {
            val hasAgent = steps.any { it.type == KiteRecipe.STEP_AGENT }
            val hasShell = steps.any { it.type == KiteRecipe.STEP_SHELL || it.type == KiteRecipe.STEP_TERMINAL }
            val hasWeb = steps.any { it.type == KiteRecipe.STEP_OPEN_WEB && it.url.isNotBlank() }
            return when {
                hasAgent && !hasShell && !hasWeb -> KiteRecipe.TYPE_AGENT
                hasShell && hasWeb -> KiteRecipe.TYPE_COMMAND_WEB
                hasShell || hasAgent -> KiteRecipe.TYPE_START_SERVICE
                hasWeb -> KiteRecipe.TYPE_OPEN_URL
                else -> KiteRecipe.TYPE_TEMPLATE
            }
        }
    }
}

internal data class RecipeEditorValidationError(
    val field: String,
    val message: String,
    val stepIndex: Int? = null
)

internal enum class RecipeEditorPhase {
    Idle,
    Loading,
    Ready,
    Saving,
    Deleting,
    Failed
}

internal data class RecipeEditorUiState(
    val phase: RecipeEditorPhase = RecipeEditorPhase.Idle,
    val originalRecipe: KiteRecipe? = null,
    val baseline: RecipeEditorDraft = RecipeEditorDraft.empty(),
    val draft: RecipeEditorDraft = RecipeEditorDraft.empty(),
    val groups: List<KiteCardGroup> = emptyList(),
    val agents: List<AgentRegistryEntry> = emptyList(),
    val run: CardRunState? = null,
    val runProjection: KiteCardRunUiProjection? = null,
    val runtimeBlocked: Boolean = true,
    val validationErrors: List<RecipeEditorValidationError> = emptyList(),
    val errorMessage: String? = null,
    val revision: Long = 0L
) {
    val isNew: Boolean get() = originalRecipe == null
    val isDirty: Boolean get() = draft.normalized() != baseline.normalized()
}

internal sealed interface RecipeEditorAction {
    data class Initialize(val recipeId: String?, val restoredDraft: RecipeEditorDraft? = null) : RecipeEditorAction
    data class SetName(val value: String) : RecipeEditorAction
    data class SetDescription(val value: String) : RecipeEditorAction
    data class SelectBuiltinIcon(val name: String) : RecipeEditorAction
    data class SelectImageIcon(val source: String) : RecipeEditorAction
    data class SelectGroup(val groupId: String) : RecipeEditorAction
    data class SetLaunchOpenInstance(val enabled: Boolean) : RecipeEditorAction
    data class SetKeepFinishedNotification(val enabled: Boolean) : RecipeEditorAction
    data class ReconcileAgents(val agents: List<AgentRegistryEntry>) : RecipeEditorAction
    data class PutStep(val index: Int?, val step: RecipeEditorStepDraft) : RecipeEditorAction
    data class RemoveStep(val index: Int) : RecipeEditorAction
    data class MoveStep(val from: Int, val to: Int) : RecipeEditorAction
    data class CreateGroup(val name: String) : RecipeEditorAction
    data class SetRuntimeBlocked(val blocked: Boolean) : RecipeEditorAction
    data object ReconcileRun : RecipeEditorAction
    data class Run(val intent: KiteRecipeActionIntent) : RecipeEditorAction
    data object Save : RecipeEditorAction
    data object Delete : RecipeEditorAction
    data object PersistDraft : RecipeEditorAction
    data object DiscardDraft : RecipeEditorAction
}

internal sealed interface RecipeEditorEffect {
    data class Saved(val recipeId: String) : RecipeEditorEffect
    data class Deleted(val recipeId: String, val removedCardInstanceIds: Set<String>) : RecipeEditorEffect
    data class DeleteRequiresStop(val request: KiteRecipeActionRequest) : RecipeEditorEffect
    data class ActionRequested(val request: KiteRecipeActionRequest) : RecipeEditorEffect
    data class GroupCreated(val group: KiteCardGroup) : RecipeEditorEffect
    data class ValidationFailed(val errors: List<RecipeEditorValidationError>) : RecipeEditorEffect
    data class Failed(val operation: String, val message: String) : RecipeEditorEffect
    data object DraftPersisted : RecipeEditorEffect
    data object DraftDiscarded : RecipeEditorEffect
}
