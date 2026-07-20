package com.kite.app.feature.home

import android.app.Activity
import android.view.ContextThemeWrapper
import androidx.test.core.app.ApplicationProvider
import com.kite.app.R
import com.kite.app.recipe.KiteCardGroup
import com.kite.app.recipe.KiteExecution
import com.kite.app.recipe.KiteLaunchConfig
import com.kite.app.recipe.KiteRecipe
import com.kite.app.recipe.KiteRecipeStep
import com.kite.app.run.CardRunState
import com.kite.app.run.CardRunStatus
import com.kite.app.run.KiteCardRunUiProjector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HomeScreenTest {
    @Test
    fun runFactChangeRebindsExistingActionView() {
        val clicked = mutableListOf<String>()
        val screen = screen(onPrimary = clicked::add)
        attach(screen)
        screen.render(state(CardRunStatus.Unknown))
        val initial = screen.actionViewForTest("tool")!!

        initial.performClick()
        assertEquals(listOf("tool"), clicked)

        screen.render(state(CardRunStatus.Running))
        val rebound = screen.actionViewForTest("tool")!!

        assertSame(initial, rebound)
        assertEquals(rebound.context.getString(R.string.home_action_stop), rebound.text.toString())
    }

    @Test
    fun acceptedActionImmediatelyLocksCurrentCard() {
        val screen = screen()
        attach(screen)
        screen.render(state(CardRunStatus.Unknown))

        screen.acknowledge("tool")

        val action = screen.actionViewForTest("tool")!!
        assertEquals(action.context.getString(R.string.home_action_starting), action.text.toString())
        assertTrue(!action.isEnabled)
    }

    @Test
    fun customGroupProjectsOnlyOwnedRecipes() {
        val group = KiteCardGroup("ai", "AI")
        val tool = recipe("tool", groupId = "ai")
        val web = recipe("web")
        val screen = screen()
        attach(screen)
        screen.render(
            HomeFeatureUiState(
                phase = HomeCatalogPhase.Ready,
                groups = listOf(group),
                items = listOf(item(tool), item(web))
            )
        )

        screen.selectGroup("ai")

        assertEquals(listOf("tool"), screen.visibleRecipeIdsForTest())
    }

    @Test
    fun legacyCategoryBecomesAVisibleFilterWithoutCopyingGroupState() {
        val screen = screen()
        attach(screen)
        screen.render(
            HomeFeatureUiState(
                phase = HomeCatalogPhase.Ready,
                items = listOf(item(recipe("audit", category = "验收")), item(recipe("tool")))
            )
        )

        assertTrue(screen.chipLabelsForTest().contains("验收"))
    }

    @Test
    fun legacyCategoryMergesIntoMatchingGroupWithoutCaseSensitiveGap() {
        val screen = screen()
        attach(screen)
        screen.render(
            HomeFeatureUiState(
                phase = HomeCatalogPhase.Ready,
                groups = listOf(KiteCardGroup(id = "audit", name = "Audit")),
                items = listOf(item(recipe("legacy", category = "audit")))
            )
        )

        assertEquals(1, screen.chipLabelsForTest().count { it.equals("Audit", ignoreCase = true) })
        screen.selectGroup("audit")
        assertEquals(listOf("legacy"), screen.visibleRecipeIdsForTest())
    }

    @Test
    fun runningChipReplacesStoppedPageAndShowsLiveCount() {
        val screen = screen()
        attach(screen)
        screen.render(
            HomeFeatureUiState(
                phase = HomeCatalogPhase.Ready,
                items = listOf(
                    item(recipe("live"), CardRunStatus.Running),
                    item(recipe("done"), CardRunStatus.Completed)
                )
            )
        )

        val labels = screen.chipLabelsForTest()
        val expected = ApplicationProvider.getApplicationContext<android.content.Context>()
            .getString(R.string.home_tab_running, 1)
        assertTrue(labels.contains(expected))
        assertEquals(3, labels.size)
        screen.dispose()
    }

    @Test
    fun searchFiltersOnlyCurrentInMemoryItems() {
        val screen = screen()
        attach(screen)
        screen.render(
            HomeFeatureUiState(
                phase = HomeCatalogPhase.Ready,
                items = listOf(item(recipe("terminal")), item(recipe("browser")))
            )
        )

        screen.searchViewForTest().setText("browser")

        assertEquals(listOf("browser"), screen.visibleRecipeIdsForTest())
    }

    @Test
    fun nameSortUsesPresentationStateWithoutChangingRunFacts() {
        val screen = screen(initialSortMode = HomeSortMode.Name)
        attach(screen)
        screen.render(
            HomeFeatureUiState(
                phase = HomeCatalogPhase.Ready,
                items = listOf(item(recipe("zeta")), item(recipe("alpha")))
            )
        )

        assertEquals(listOf("alpha", "zeta"), screen.visibleRecipeIdsForTest())
    }

    private fun screen(
        onPrimary: (String) -> Unit = {},
        initialSortMode: HomeSortMode = HomeSortMode.Default
    ): HomeScreen = HomeScreen(
        context = ContextThemeWrapper(
            ApplicationProvider.getApplicationContext(),
            R.style.Theme_Kite
        ),
        initialPageId = HOME_PAGE_ALL,
        initialScrollY = 0,
        initialSearchQuery = "",
        initialSortMode = initialSortMode,
        onOpenEditor = {},
        onPrimaryAction = onPrimary,
        onCreateGroup = {},
        onExternalRefresh = {},
        onRetry = {}
    )

    private fun attach(screen: HomeScreen) {
        Robolectric.buildActivity(Activity::class.java)
            .setup()
            .get()
            .setContentView(screen.root)
    }

    private fun state(status: CardRunStatus): HomeFeatureUiState =
        HomeFeatureUiState(
            phase = HomeCatalogPhase.Ready,
            items = listOf(item(recipe("tool"), status))
        )

    private fun item(
        recipe: KiteRecipe,
        status: CardRunStatus = CardRunStatus.Unknown
    ): HomeRecipeItemUiState {
        val run = CardRunState(
            instanceId = "run-" + recipe.id,
            recipeId = recipe.id,
            status = status
        )
        return HomeRecipeItemUiState(
            recipe = recipe,
            run = run,
            projection = KiteCardRunUiProjector.project(status),
            runtimeBlocked = false
        )
    }

    private fun recipe(
        id: String,
        groupId: String = "",
        category: String = ""
    ): KiteRecipe = KiteRecipe(
        id = id,
        name = id.replaceFirstChar(Char::uppercase),
        description = "Test recipe",
        type = KiteRecipe.TYPE_START_SERVICE,
        groupId = groupId,
        category = category,
        defaultUrl = "",
        shortcut = false,
        launch = KiteLaunchConfig(openInstance = true),
        execution = KiteExecution.steps(
            listOf(KiteRecipeStep(id = "shell", type = KiteRecipe.STEP_SHELL, cmd = "echo ok"))
        )
    )
}
