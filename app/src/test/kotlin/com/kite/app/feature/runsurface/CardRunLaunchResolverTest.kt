package com.kite.app.feature.runsurface

import com.kite.app.recipe.KiteExecution
import com.kite.app.recipe.KiteRecipe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CardRunLaunchResolverTest {
    @Test
    fun `catalog recipe wins and launch fields are normalized`() {
        val recipe = recipe("demo")
        val resolver = CardRunLaunchResolver(
            catalogRecipes = { listOf(recipe) },
            registeredRecipe = { null },
            specialRecipe = { null }
        )

        val result = resolver.resolve(
                CardRunLaunchRequest(
                    recipeId = " demo ",
                    instanceId = " run-1 ",
                    autoStart = true,
                    launchSource = " card ",
                    installPlanResourceIds = listOf("a", " a ", "", "b")
                )
            )
        assertTrue(result is CardRunLaunchResolution.Resolved)
        val resolved = (result as CardRunLaunchResolution.Resolved).target

        assertEquals(recipe, resolved.recipe)
        assertEquals("run-1", resolved.instanceId)
        assertEquals("card", resolved.launchSource)
        assertEquals(CardRunMissingStatePolicy.Create, resolved.missingStatePolicy)
        assertEquals(listOf("a", "b"), resolved.installPlanResourceIds)
    }

    @Test
    fun `registered recipe is reusable without catalog reload`() {
        val registered = recipe("registered")
        val resolver = CardRunLaunchResolver(
            catalogRecipes = { emptyList() },
            registeredRecipe = { id -> registered.takeIf { id == registered.id } },
            specialRecipe = { null }
        )

        val result = resolver.resolve(CardRunLaunchRequest("registered", null, false, "notification"))
        assertTrue(result is CardRunLaunchResolution.Resolved)
        val resolved = (result as CardRunLaunchResolution.Resolved).target

        assertEquals("registered", resolved.instanceId)
        assertEquals(false, resolved.autoStart)
        assertEquals(CardRunMissingStatePolicy.RequireExisting, resolved.missingStatePolicy)
    }

    @Test
    fun `temporary web and install wizard may create state without auto start`() {
        val resolver = CardRunLaunchResolver(
            catalogRecipes = { emptyList() },
            registeredRecipe = { null },
            specialRecipe = { request -> recipe(request.recipeId) }
        )

        val web = resolver.resolve(
            CardRunLaunchRequest(
                recipeId = "web",
                instanceId = "web-run",
                autoStart = false,
                launchSource = "browser_proxy",
                temporaryUrl = "https://example.com"
            )
        ) as CardRunLaunchResolution.Resolved
        val wizard = resolver.resolve(
            CardRunLaunchRequest(
                recipeId = "wizard",
                instanceId = "wizard-run",
                autoStart = false,
                launchSource = "resource_install",
                installTargetResourceId = "kite.demo"
            )
        ) as CardRunLaunchResolution.Resolved

        assertEquals(CardRunMissingStatePolicy.Create, web.target.missingStatePolicy)
        assertEquals(CardRunMissingStatePolicy.Create, wizard.target.missingStatePolicy)
    }

    @Test
    fun `special recipe must preserve requested identity`() {
        val resolver = CardRunLaunchResolver(
            catalogRecipes = { emptyList() },
            registeredRecipe = { null },
            specialRecipe = { recipe("other") }
        )

        val result = resolver.resolve(CardRunLaunchRequest("wanted", null, false, "browser_proxy"))
        assertTrue(result is CardRunLaunchResolution.Rejected)
        val rejected = result as CardRunLaunchResolution.Rejected

        assertEquals("recipe_id_mismatch:other:wanted", rejected.reason)
    }

    @Test
    fun `special factories retain executable and wizard contracts`() {
        val web = CardRunSpecialRecipes.temporaryBrowser("temp", "https://example.com")
        val wizard = CardRunSpecialRecipes.installWizard("kite.demo", "Demo", "wizard-id")

        assertEquals(KiteRecipe.STEP_OPEN_WEB, web.steps.single().type)
        assertEquals("https://example.com", web.steps.single().url)
        assertEquals("wizard-id", wizard.id)
        assertEquals(CardRunSpecialRecipes.RESOURCE_INSTALL_WIZARD_RUNTIME_SOURCE, wizard.runtimeSource)
        assertEquals(true, wizard.launch.openInstance)
    }

    private fun recipe(id: String): KiteRecipe = KiteRecipe(
        id = id,
        name = id,
        description = "",
        type = KiteRecipe.TYPE_TEMPLATE,
        category = "test",
        defaultUrl = "",
        shortcut = false,
        execution = KiteExecution.steps(emptyList())
    )
}
