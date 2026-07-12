package com.kite.app.feature.recipeeditor

import com.kite.app.action.KiteActionRouter
import com.kite.app.action.KiteRecipeActionCoordinator
import com.kite.app.action.KiteRecipeActionIntent
import com.kite.app.action.KiteRecipeActionPlan
import com.kite.app.action.KiteRecipeActionRequest
import com.kite.app.action.KiteRecipeActionSource
import com.kite.app.application.recipes.RecipeExternalRefreshResult
import com.kite.app.application.recipes.RecipeFeatureChange
import com.kite.app.application.recipes.RecipeFeatureGateway
import com.kite.app.recipe.KiteCardGroup
import com.kite.app.recipe.KiteExecution
import com.kite.app.recipe.KiteLaunchConfig
import com.kite.app.recipe.KiteRecipe
import com.kite.app.recipe.KiteRecipeStep
import com.kite.app.recipe.NewRecipeInput
import com.kite.app.run.CardRunState
import com.kite.app.run.CardRunStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RecipeEditorControllerTest {
    @Test
    fun initializeBuildsCleanDraftFromRecipeFacts() = runTest {
        val gateway = FakeGateway()
        val controller = RecipeEditorController(gateway, initiallyRuntimeBlocked = false)

        controller.dispatch(RecipeEditorAction.Initialize("tool"))

        val state = controller.state.value
        assertEquals(RecipeEditorPhase.Ready, state.phase)
        assertEquals("Tool", state.draft.name)
        assertEquals("ai", state.draft.groupId)
        assertFalse(state.isDirty)
        assertEquals(CardRunStatus.Running, state.run?.status)
    }

    @Test
    fun validationRejectsMissingNameAndIncompleteSteps() = runTest {
        val gateway = FakeGateway(recipes = emptyList())
        val controller = RecipeEditorController(gateway, initiallyRuntimeBlocked = false)
        controller.dispatch(RecipeEditorAction.Initialize(null))
        controller.dispatch(
            RecipeEditorAction.PutStep(
                null,
                RecipeEditorStepDraft.openWeb()
            )
        )

        val effect = controller.dispatch(RecipeEditorAction.Save)

        assertTrue(effect is RecipeEditorEffect.ValidationFailed)
        val messages = (effect as RecipeEditorEffect.ValidationFailed).errors.map { it.message }
        assertTrue(messages.any { it.contains("名称") })
        assertTrue(messages.any { it.contains("缺少地址") })
        assertEquals(null, gateway.savedInput)
    }

    @Test
    fun saveWritesNormalizedInputAndClearsPersistedDraft() = runTest {
        val gateway = FakeGateway(recipes = emptyList())
        val controller = RecipeEditorController(gateway, initiallyRuntimeBlocked = false)
        controller.dispatch(RecipeEditorAction.Initialize(null))
        controller.dispatch(RecipeEditorAction.SetName("  New Tool  "))
        controller.dispatch(RecipeEditorAction.SetDescription("  Description  "))
        controller.dispatch(RecipeEditorAction.SelectGroup("ai"))
        controller.dispatch(RecipeEditorAction.SetShortcutRequested(true))
        controller.dispatch(
            RecipeEditorAction.PutStep(
                null,
                RecipeEditorStepDraft.shell("  echo ok  ", " /workspace ")
            )
        )

        val effect = controller.dispatch(RecipeEditorAction.Save)

        assertEquals(RecipeEditorEffect.Saved("saved", true), effect)
        assertEquals("New Tool", gateway.savedInput?.name)
        assertEquals("echo ok", gateway.savedInput?.steps?.single()?.command)
        assertEquals("ai", gateway.savedInput?.groupId)
        assertEquals(null, gateway.persistedDraft)
        assertFalse(controller.state.value.isDirty)
    }

    @Test
    fun editorAndHomeStartRequestsProduceSameActionPlan() = runTest {
        val gateway = FakeGateway()
        val controller = RecipeEditorController(gateway, initiallyRuntimeBlocked = false)
        controller.dispatch(RecipeEditorAction.Initialize("tool"))

        val editor = controller.dispatch(RecipeEditorAction.Run(KiteRecipeActionIntent.Start))
            as RecipeEditorEffect.ActionRequested
        val home = KiteRecipeActionRequest(
            recipe = gateway.recipes.single(),
            intent = KiteRecipeActionIntent.Primary,
            source = KiteRecipeActionSource.ConsoleCard,
            openTaskOnStart = true
        )
        val coordinator = KiteRecipeActionCoordinator(KiteActionRouter())
        val run = CardRunState.fromRecipeStatus("tool", "unknown")

        val editorPlan = coordinator.plan(editor.request, run, runtimeBlocked = false)
        val homePlan = coordinator.plan(home, run, runtimeBlocked = false)

        assertEquals(KiteRecipeActionPlan.LaunchTask, editorPlan)
        assertEquals(homePlan, editorPlan)
    }

    @Test
    fun draftRoundTripPreservesIconLaunchAndStepOrder() {
        val draft = RecipeEditorDraft(
            editingRecipeId = "tool",
            selectedIconName = "custom",
            selectedIconType = "image",
            selectedIconSource = "recipe-icons/tool.png",
            launchOpenInstance = false,
            steps = listOf(
                RecipeEditorStepDraft.shell("echo one"),
                RecipeEditorStepDraft.openWeb("http://127.0.0.1")
            )
        )

        val restored = RecipeEditorDraft.fromJson(draft.toJson().toString())

        assertNotNull(restored)
        assertEquals(draft.normalized(), restored)
    }

    private class FakeGateway(
        var recipes: List<KiteRecipe> = listOf(recipe())
    ) : RecipeFeatureGateway {
        override val changes: Flow<RecipeFeatureChange> = emptyFlow()
        private val groupsState = mutableListOf(KiteCardGroup("ai", "AI"))
        val runs = linkedMapOf(
            "tool" to CardRunState(
                instanceId = "run-tool",
                recipeId = "tool",
                status = CardRunStatus.Running
            )
        )
        var savedInput: NewRecipeInput? = null
        var persistedDraft: String? = "old"

        override suspend fun loadRecipes(forceRefresh: Boolean): List<KiteRecipe> = recipes

        override fun groups(): List<KiteCardGroup> = groupsState.toList()

        override fun runSnapshot(recipeId: String): CardRunState? = runs[recipeId]

        override suspend fun saveRecipe(input: NewRecipeInput): KiteRecipe {
            savedInput = input
            return recipe().copy(
                id = "saved",
                name = input.name,
                description = input.description,
                category = input.category,
                groupId = input.groupId,
                defaultUrl = input.url,
                launch = KiteLaunchConfig(openInstance = input.openInstanceOnStart)
            )
        }

        override suspend fun deleteRecipe(recipeId: String): Boolean {
            recipes = recipes.filterNot { it.id == recipeId }
            return true
        }

        override suspend fun createGroup(name: String): KiteCardGroup =
            KiteCardGroup("new-group", name).also(groupsState::add)

        override suspend fun refreshExternalRecipes(): RecipeExternalRefreshResult =
            RecipeExternalRefreshResult("ok", 0, 0, 0)

        override fun restoredEditorDraft(maxAgeMs: Long): String? = persistedDraft

        override fun saveEditorDraft(rawJson: String?) {
            persistedDraft = rawJson
        }
    }

    private companion object {
        fun recipe(): KiteRecipe = KiteRecipe(
            id = "tool",
            name = "Tool",
            description = "Tool recipe",
            type = KiteRecipe.TYPE_START_SERVICE,
            category = "AI",
            groupId = "ai",
            defaultUrl = "",
            shortcut = false,
            launch = KiteLaunchConfig(openInstance = true),
            execution = KiteExecution.steps(
                listOf(KiteRecipeStep(id = "shell", type = KiteRecipe.STEP_SHELL, cmd = "echo ok"))
            )
        )
    }
}
