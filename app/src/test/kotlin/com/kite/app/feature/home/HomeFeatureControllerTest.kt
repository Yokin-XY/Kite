package com.kite.app.feature.home

import com.kite.app.action.KiteRecipeActionIntent
import com.kite.app.action.KiteRecipeActionSource
import com.kite.app.application.recipes.RecipeFeatureChange
import com.kite.app.application.recipes.RecipeExternalRefreshResult
import com.kite.app.application.recipes.RecipeDeleteResult
import com.kite.app.application.recipes.RecipeFeatureGateway
import com.kite.app.recipe.KiteCardGroup
import com.kite.app.recipe.KiteExecution
import com.kite.app.recipe.KiteLaunchConfig
import com.kite.app.recipe.KiteRecipe
import com.kite.app.recipe.KiteRecipeStep
import com.kite.app.recipe.NewRecipeInput
import com.kite.app.run.CardRunState
import com.kite.app.run.CardRunStatus
import com.kite.app.run.KiteRunPrimaryAction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeFeatureControllerTest {
    @Test
    fun `首页从配方分组与运行事实生成统一状态`() = runTest {
        val gateway = FakeGateway().apply {
            groups = listOf(KiteCardGroup("ai", "AI"))
            runs["tool"] = CardRunState(
                instanceId = "run-tool",
                recipeId = "tool",
                status = CardRunStatus.Running
            )
        }
        val controller = HomeFeatureController(gateway, initiallyBlocksUbuntuActions = false)

        controller.dispatch(HomeFeatureAction.Refresh())

        val item = controller.state.value.item("tool")!!
        assertEquals(HomeCatalogPhase.Ready, controller.state.value.phase)
        assertEquals("run-tool", item.run.instanceId)
        assertEquals(KiteRunPrimaryAction.Stop, item.projection.primaryAction)
        assertEquals(listOf("ai"), controller.state.value.groups.map(KiteCardGroup::id))
    }

    @Test
    fun `运行环境阻塞只影响需要Ubuntu的卡片`() = runTest {
        val gateway = FakeGateway().apply {
            recipes = listOf(shellRecipe(), webRecipe())
        }
        val controller = HomeFeatureController(gateway, initiallyBlocksUbuntuActions = true)

        controller.dispatch(HomeFeatureAction.Refresh())

        val shell = controller.state.value.item("tool")!!
        val web = controller.state.value.item("web")!!
        assertTrue(shell.runtimeBlocked)
        assertEquals(KiteRunPrimaryAction.Blocked, shell.projection.primaryAction)
        assertFalse(web.runtimeBlocked)
        assertEquals(KiteRunPrimaryAction.Start, web.projection.primaryAction)
    }

    @Test
    fun `首页主动作只提交稳定请求且保留独立运行窗口语义`() = runTest {
        val gateway = FakeGateway()
        val controller = HomeFeatureController(gateway, initiallyBlocksUbuntuActions = false)
        controller.dispatch(HomeFeatureAction.Refresh())

        val effect = controller.dispatch(HomeFeatureAction.Primary("tool"))
            as HomeFeatureEffect.ActionRequested

        assertEquals(KiteRecipeActionIntent.Primary, effect.request.intent)
        assertEquals(KiteRecipeActionSource.ConsoleCard, effect.request.source)
        assertTrue(effect.request.openTaskOnStart)
        assertEquals("tool", effect.request.recipe.id)
    }

    @Test
    fun `运行校准不重新读取配方目录`() = runTest {
        val gateway = FakeGateway()
        val controller = HomeFeatureController(gateway, initiallyBlocksUbuntuActions = false)
        controller.dispatch(HomeFeatureAction.Refresh())
        gateway.runs["tool"] = CardRunState(
            instanceId = "run-tool",
            recipeId = "tool",
            status = CardRunStatus.Failed
        )

        controller.dispatch(HomeFeatureAction.ReconcileRuns)

        assertEquals(1, gateway.loadCount)
        assertEquals(KiteRunPrimaryAction.Retry, controller.state.value.item("tool")!!.projection.primaryAction)
    }

    @Test
    fun `目录失败保留原有卡片并暴露错误`() = runTest {
        val gateway = FakeGateway()
        val controller = HomeFeatureController(gateway, initiallyBlocksUbuntuActions = false)
        controller.dispatch(HomeFeatureAction.Refresh())
        gateway.loadFailure = IllegalStateException("recipes_failed")

        controller.dispatch(HomeFeatureAction.Refresh(forceCatalogRefresh = true))

        assertEquals(HomeCatalogPhase.Failed, controller.state.value.phase)
        assertEquals("recipes_failed", controller.state.value.errorMessage)
        assertEquals(listOf("tool"), controller.state.value.items.map { it.recipeId })

        controller.dispatch(HomeFeatureAction.ReconcileRuns)

        assertEquals(HomeCatalogPhase.Failed, controller.state.value.phase)
        assertEquals("recipes_failed", controller.state.value.errorMessage)
    }

    @Test
    fun createGroupProjectsSharedGroupFacts() = runTest {
        val gateway = FakeGateway()
        val controller = HomeFeatureController(gateway, initiallyBlocksUbuntuActions = false)
        controller.dispatch(HomeFeatureAction.Refresh())

        val effect = controller.dispatch(HomeFeatureAction.CreateGroup("AI 工具"))

        assertTrue(effect is HomeFeatureEffect.GroupCreated)
        assertEquals(listOf("AI 工具"), controller.state.value.groups.map(KiteCardGroup::name))
        assertEquals(1, gateway.loadCount)
    }

    @Test
    fun externalRefreshLoadsNewCatalogAndReturnsResult() = runTest {
        val gateway = FakeGateway()
        val controller = HomeFeatureController(gateway, initiallyBlocksUbuntuActions = false)
        controller.dispatch(HomeFeatureAction.Refresh())
        gateway.recipesAfterExternalRefresh = listOf(webRecipe())

        val effect = controller.dispatch(HomeFeatureAction.RefreshExternalRecipes)

        assertEquals(HomeFeatureEffect.ExternalRefreshCompleted("已刷新"), effect)
        assertEquals(listOf("web"), controller.state.value.items.map { it.recipeId })
        assertEquals(2, gateway.loadCount)
    }

    private class FakeGateway : RecipeFeatureGateway {
        override val changes: Flow<RecipeFeatureChange> = emptyFlow()
        var recipes: List<KiteRecipe> = listOf(shellRecipe())
        var groups: List<KiteCardGroup> = emptyList()
        val runs = linkedMapOf<String, CardRunState>()
        var loadFailure: Throwable? = null
        var loadCount = 0
        var recipesAfterExternalRefresh: List<KiteRecipe>? = null

        override suspend fun loadRecipes(forceRefresh: Boolean): List<KiteRecipe> {
            loadCount += 1
            loadFailure?.let { throw it }
            return recipes
        }

        override fun groups(): List<KiteCardGroup> = groups

        override fun runSnapshot(recipeId: String): CardRunState? = runs[recipeId]

        override suspend fun saveRecipe(input: NewRecipeInput): KiteRecipe = error("not used")

        override suspend fun deleteRecipe(recipeId: String): RecipeDeleteResult =
            RecipeDeleteResult.Missing

        override suspend fun createGroup(name: String): KiteCardGroup =
            KiteCardGroup("group", name).also { groups = groups + it }

        override suspend fun refreshExternalRecipes(): RecipeExternalRefreshResult {
            recipesAfterExternalRefresh?.let { recipes = it }
            return RecipeExternalRefreshResult("已刷新", 0, 0, 0)
        }

        override fun invalidateCatalog(reason: String, affectedRecipeIds: Set<String>) = Unit

        override fun restoredEditorDraft(maxAgeMs: Long): String? = null

        override fun saveEditorDraft(rawJson: String?) = Unit

        override fun customEditorIconSources(): List<String> = emptyList()

        override fun readEditorIcon(source: String): ByteArray? = null

        override suspend fun saveEditorIcon(pngBytes: ByteArray): String = "recipe-icons/test.png"
    }

    private companion object {
        fun shellRecipe(): KiteRecipe = KiteRecipe(
            id = "tool",
            name = "Tool",
            description = "Shell tool",
            type = KiteRecipe.TYPE_START_SERVICE,
            defaultUrl = "",
            shortcut = false,
            launch = KiteLaunchConfig(openInstance = true),
            execution = KiteExecution.steps(
                listOf(KiteRecipeStep(id = "shell", type = KiteRecipe.STEP_SHELL, cmd = "echo ok"))
            )
        )

        fun webRecipe(): KiteRecipe = KiteRecipe(
            id = "web",
            name = "Web",
            description = "Web tool",
            type = KiteRecipe.TYPE_OPEN_URL,
            defaultUrl = "http://127.0.0.1",
            shortcut = false,
            launch = KiteLaunchConfig(openInstance = true),
            execution = KiteExecution.steps(
                listOf(KiteRecipeStep(id = "web", type = KiteRecipe.STEP_OPEN_WEB, url = "http://127.0.0.1"))
            )
        )
    }
}
