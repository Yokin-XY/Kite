package com.kite.app.feature.recipeeditor

import com.kite.app.action.KiteRecipeActionIntent
import com.kite.app.action.KiteRecipeActionRequest
import com.kite.app.action.KiteRecipeActionSource
import com.kite.app.application.recipes.RecipeFeatureGateway
import com.kite.app.application.recipes.RecipeDeleteResult
import com.kite.app.recipe.KiteRecipe
import com.kite.app.recipe.KiteRecipeIcon
import com.kite.app.run.CardRunState
import com.kite.app.run.KiteCardRunUiProjector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** 配方编辑器的纯状态控制器；草稿、校验与配置写入均在这里收口。 */
internal class RecipeEditorController(
    private val gateway: RecipeFeatureGateway,
    initiallyRuntimeBlocked: Boolean = true
) {
    private val mutableState = MutableStateFlow(
        RecipeEditorUiState(runtimeBlocked = initiallyRuntimeBlocked)
    )
    private val dispatchMutex = Mutex()
    val state: StateFlow<RecipeEditorUiState> = mutableState.asStateFlow()

    suspend fun dispatch(action: RecipeEditorAction): RecipeEditorEffect? = dispatchMutex.withLock {
        when (action) {
            is RecipeEditorAction.Initialize -> initialize(action.recipeId, action.restoredDraft)
            is RecipeEditorAction.SetName -> updateDraft { copy(name = action.value) }
            is RecipeEditorAction.SetDescription -> updateDraft { copy(description = action.value) }
            is RecipeEditorAction.SelectBuiltinIcon -> updateDraft {
                copy(
                    selectedIconName = KiteRecipeIcon.normalizeName(action.name, selectedType),
                    selectedIconType = KiteRecipeIcon.TYPE_BUILTIN,
                    selectedIconSource = ""
                )
            }
            is RecipeEditorAction.SelectImageIcon -> updateDraft {
                copy(
                    selectedIconName = "custom",
                    selectedIconType = KiteRecipeIcon.TYPE_IMAGE,
                    selectedIconSource = action.source.trim()
                )
            }
            is RecipeEditorAction.SelectGroup -> updateDraft { copy(groupId = action.groupId) }
            is RecipeEditorAction.SetLaunchOpenInstance -> updateDraft {
                copy(launchOpenInstance = action.enabled)
            }
            is RecipeEditorAction.SetShortcutRequested -> updateDraft {
                copy(shortcutRequested = action.requested)
            }
            is RecipeEditorAction.PutStep -> putStep(action.index, action.step)
            is RecipeEditorAction.RemoveStep -> removeStep(action.index)
            is RecipeEditorAction.MoveStep -> moveStep(action.from, action.to)
            is RecipeEditorAction.ApplyTemplate -> applyTemplate(action.type)
            is RecipeEditorAction.CreateGroup -> createGroup(action.name)
            is RecipeEditorAction.SetRuntimeBlocked -> {
                if (mutableState.value.runtimeBlocked != action.blocked) {
                    mutableState.value = mutableState.value.copy(runtimeBlocked = action.blocked)
                    publishRun()
                }
                null
            }
            RecipeEditorAction.ReconcileRun -> {
                publishRun()
                null
            }
            is RecipeEditorAction.Run -> requestRun(action.intent)
            RecipeEditorAction.Save -> save()
            RecipeEditorAction.Delete -> delete()
            RecipeEditorAction.PersistDraft -> {
                gateway.saveEditorDraft(mutableState.value.draft.toJson().toString())
                RecipeEditorEffect.DraftPersisted
            }
            RecipeEditorAction.DiscardDraft -> {
                gateway.saveEditorDraft(null)
                RecipeEditorEffect.DraftDiscarded
            }
        }
    }

    private suspend fun initialize(
        recipeId: String?,
        restoredDraft: RecipeEditorDraft?
    ): RecipeEditorEffect? {
        mutableState.value = mutableState.value.copy(
            phase = RecipeEditorPhase.Loading,
            errorMessage = null
        )
        return runCatching { gateway.loadRecipes(forceRefresh = false) }
            .fold(
                onSuccess = { recipes ->
                    val normalizedId = recipeId?.trim().orEmpty()
                    val recipe = normalizedId.takeIf(String::isNotBlank)
                        ?.let { id -> recipes.firstOrNull { it.id == id } }
                    if (normalizedId.isNotBlank() && recipe == null) {
                        mutableState.value = mutableState.value.copy(
                            phase = RecipeEditorPhase.Failed,
                            errorMessage = "recipe_not_found:$normalizedId",
                            revision = mutableState.value.revision + 1L
                        )
                        return@fold RecipeEditorEffect.Failed("initialize", "未找到卡片：$normalizedId")
                    }
                    val baseline = recipe?.let(RecipeEditorDraft::fromRecipe) ?: RecipeEditorDraft.empty()
                    val restoredMatches = restoredDraft != null && when {
                        recipe != null -> restoredDraft.editingRecipeId == recipe.id
                        normalizedId.isBlank() -> restoredDraft.editingRecipeId.isBlank()
                        else -> false
                    }
                    val draft = restoredDraft
                        ?.takeIf { restoredMatches }
                        ?.normalized()
                        ?: baseline
                    val run = recipe?.let(::runFor)
                    mutableState.value = RecipeEditorUiState(
                        phase = RecipeEditorPhase.Ready,
                        originalRecipe = recipe,
                        baseline = baseline,
                        draft = draft,
                        groups = gateway.groups(),
                        run = run,
                        runProjection = run?.let {
                            KiteCardRunUiProjector.project(
                                it.status,
                                mutableState.value.runtimeBlocked && recipe.hasUbuntuStep()
                            )
                        },
                        runtimeBlocked = mutableState.value.runtimeBlocked,
                        revision = mutableState.value.revision + 1L
                    )
                    null
                },
                onFailure = { error ->
                    mutableState.value = mutableState.value.copy(
                        phase = RecipeEditorPhase.Failed,
                        errorMessage = error.message ?: error.javaClass.simpleName,
                        revision = mutableState.value.revision + 1L
                    )
                    RecipeEditorEffect.Failed(
                        "initialize",
                        error.message ?: error.javaClass.simpleName
                    )
                }
            )
    }

    private fun updateDraft(transform: RecipeEditorDraft.() -> RecipeEditorDraft): RecipeEditorEffect? {
        if (mutableState.value.phase != RecipeEditorPhase.Ready) return null
        val next = mutableState.value.draft.transform()
        if (next != mutableState.value.draft) {
            mutableState.value = mutableState.value.copy(
                draft = next,
                validationErrors = emptyList(),
                errorMessage = null,
                revision = mutableState.value.revision + 1L
            )
        }
        return null
    }

    private fun putStep(index: Int?, step: RecipeEditorStepDraft): RecipeEditorEffect? = updateDraft {
        val next = steps.toMutableList()
        if (index == null || index !in next.indices) next += step else next[index] = step
        copy(selectedType = RecipeEditorDraft.inferType(next), steps = next)
    }

    private fun removeStep(index: Int): RecipeEditorEffect? = updateDraft {
        if (index !in steps.indices) return@updateDraft this
        val next = steps.toMutableList().apply { removeAt(index) }
        copy(selectedType = RecipeEditorDraft.inferType(next), steps = next)
    }

    private fun moveStep(from: Int, to: Int): RecipeEditorEffect? = updateDraft {
        if (from !in steps.indices || to !in steps.indices || from == to) return@updateDraft this
        val next = steps.toMutableList()
        val item = next.removeAt(from)
        next.add(to, item)
        copy(steps = next)
    }

    private fun applyTemplate(type: String): RecipeEditorEffect? = updateDraft {
        val next = when (type) {
            KiteRecipe.TYPE_COMMAND_WEB -> listOf(
                RecipeEditorStepDraft.shell(),
                RecipeEditorStepDraft.openWeb()
            )
            KiteRecipe.TYPE_START_SERVICE -> listOf(RecipeEditorStepDraft.shell())
            else -> listOf(RecipeEditorStepDraft.openWeb())
        }
        copy(
            selectedType = type,
            selectedIconName = KiteRecipeIcon.defaultNameForType(type),
            selectedIconType = KiteRecipeIcon.TYPE_BUILTIN,
            selectedIconSource = "",
            steps = next
        )
    }

    private suspend fun createGroup(rawName: String): RecipeEditorEffect {
        val name = rawName.trim()
        if (name.isBlank()) return RecipeEditorEffect.Failed("create_group", "请输入分组名称")
        return runCatching { gateway.createGroup(name) }
            .fold(
                onSuccess = { group ->
                    mutableState.value = mutableState.value.copy(
                        draft = mutableState.value.draft.copy(groupId = group.id),
                        groups = gateway.groups(),
                        revision = mutableState.value.revision + 1L
                    )
                    RecipeEditorEffect.GroupCreated(group)
                },
                onFailure = { error ->
                    RecipeEditorEffect.Failed(
                        "create_group",
                        error.message ?: error.javaClass.simpleName
                    )
                }
            )
    }

    private fun publishRun() {
        val state = mutableState.value
        val recipe = state.originalRecipe
        val run = recipe?.let(::runFor)
        mutableState.value = state.copy(
            groups = gateway.groups(),
            run = run,
            runProjection = run?.let {
                KiteCardRunUiProjector.project(
                    it.status,
                    state.runtimeBlocked && recipe.hasUbuntuStep()
                )
            },
            revision = state.revision + 1L
        )
    }

    private fun requestRun(intent: KiteRecipeActionIntent): RecipeEditorEffect {
        val state = mutableState.value
        val recipe = state.originalRecipe
            ?: return RecipeEditorEffect.Failed("run", "请先保存卡片")
        if (intent == KiteRecipeActionIntent.Start && state.runtimeBlocked && recipe.hasUbuntuStep()) {
            return RecipeEditorEffect.Failed("run", "运行环境尚未就绪")
        }
        return RecipeEditorEffect.ActionRequested(
            KiteRecipeActionRequest(
                recipe = recipe,
                intent = if (intent == KiteRecipeActionIntent.Start) {
                    KiteRecipeActionIntent.Primary
                } else {
                    intent
                },
                source = KiteRecipeActionSource.Editor,
                openTaskOnStart = intent == KiteRecipeActionIntent.Start && recipe.launch.openInstance
            )
        )
    }

    private suspend fun save(): RecipeEditorEffect {
        val state = mutableState.value
        val normalized = state.draft.normalized()
        val errors = normalized.validationErrors()
        if (errors.isNotEmpty()) {
            mutableState.value = state.copy(
                draft = normalized,
                validationErrors = errors,
                revision = state.revision + 1L
            )
            return RecipeEditorEffect.ValidationFailed(errors)
        }
        mutableState.value = state.copy(
            phase = RecipeEditorPhase.Saving,
            draft = normalized,
            validationErrors = emptyList(),
            errorMessage = null
        )
        return runCatching {
            gateway.saveRecipe(normalized.toInput(state.groups, state.originalRecipe))
        }.fold(
            onSuccess = { saved ->
                gateway.saveEditorDraft(null)
                val baseline = RecipeEditorDraft.fromRecipe(saved)
                val run = runFor(saved)
                mutableState.value = mutableState.value.copy(
                    phase = RecipeEditorPhase.Ready,
                    originalRecipe = saved,
                    baseline = baseline,
                    draft = baseline,
                    run = run,
                    runProjection = run.let {
                        KiteCardRunUiProjector.project(
                            it.status,
                            mutableState.value.runtimeBlocked && saved.hasUbuntuStep()
                        )
                    },
                    revision = mutableState.value.revision + 1L
                )
                RecipeEditorEffect.Saved(saved.id, normalized.shortcutRequested)
            },
            onFailure = { error ->
                mutableState.value = mutableState.value.copy(
                    phase = RecipeEditorPhase.Ready,
                    errorMessage = error.message ?: error.javaClass.simpleName,
                    revision = mutableState.value.revision + 1L
                )
                RecipeEditorEffect.Failed("save", error.message ?: error.javaClass.simpleName)
            }
        )
    }

    private suspend fun delete(): RecipeEditorEffect {
        val state = mutableState.value
        val recipe = state.originalRecipe
            ?: return RecipeEditorEffect.Failed("delete", "新建卡片尚未保存")
        mutableState.value = state.copy(phase = RecipeEditorPhase.Deleting, errorMessage = null)
        return runCatching { gateway.deleteRecipe(recipe.id) }
            .fold(
                onSuccess = { result ->
                    when (result) {
                        is RecipeDeleteResult.RequiresStop -> {
                            mutableState.value = mutableState.value.copy(
                                phase = RecipeEditorPhase.Ready,
                                run = result.run,
                                errorMessage = "请等待运行停止后再删除",
                                revision = mutableState.value.revision + 1L
                            )
                            RecipeEditorEffect.DeleteRequiresStop(
                                KiteRecipeActionRequest(
                                    recipe = recipe,
                                    intent = KiteRecipeActionIntent.Stop,
                                    source = KiteRecipeActionSource.Editor,
                                    instanceId = result.run.instanceId
                                )
                            )
                        }
                        is RecipeDeleteResult.Deleted -> {
                            gateway.saveEditorDraft(null)
                            RecipeEditorEffect.Deleted(recipe.id, result.removedCardInstanceIds)
                        }
                        RecipeDeleteResult.Missing -> {
                            mutableState.value = mutableState.value.copy(
                                phase = RecipeEditorPhase.Ready,
                                errorMessage = "delete_failed",
                                revision = mutableState.value.revision + 1L
                            )
                            RecipeEditorEffect.Failed("delete", "删除失败")
                        }
                    }
                },
                onFailure = { error ->
                    mutableState.value = mutableState.value.copy(
                        phase = RecipeEditorPhase.Ready,
                        errorMessage = error.message ?: error.javaClass.simpleName,
                        revision = mutableState.value.revision + 1L
                    )
                    RecipeEditorEffect.Failed("delete", error.message ?: error.javaClass.simpleName)
                }
            )
    }

    private fun runFor(recipe: KiteRecipe): CardRunState =
        gateway.runSnapshot(recipe.id) ?: CardRunState.fromRecipeStatus(recipe.id, "unknown")
}
