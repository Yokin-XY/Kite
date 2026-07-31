package com.kite.app.platform.resources

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.kite.app.recipe.KiteExecution
import com.kite.app.recipe.KiteLaunchConfig
import com.kite.app.recipe.KiteRecipe
import com.kite.app.recipe.KiteRecipeIcon
import com.kite.app.resources.KiteResourceInstallRecipes
import com.kite.app.resources.KiteResourceInstallStore
import com.kite.app.resources.KiteResourceManifestLoader
import com.kite.app.run.CardRunStatus
import com.kite.app.run.CardRunStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AndroidResourceFeatureGatewayTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        CardRunStore.resetForTest()
        context.getSharedPreferences("kite_card_run_store", Context.MODE_PRIVATE).edit().clear().commit()
        CardRunStore.initialize(context)
    }

    @After
    fun tearDown() {
        CardRunStore.resetForTest()
    }

    @Test
    fun `启动投影会把已终止的维护运行恢复成已安装失败状态`() {
        val store = KiteResourceInstallStore(context)
        val resourceId = "test.resource.reconcile.${System.nanoTime()}"
        store.clear(resourceId)
        store.markInstalled(resourceId, "1.0.0", "old-run", "done")
        store.markInstalling(resourceId, operation = KiteResourceInstallRecipes.OP_UPDATE)
        val recipe = KiteRecipe(
            id = KiteResourceInstallRecipes.recipeId(resourceId, KiteResourceInstallRecipes.OP_UPDATE),
            name = "Update",
            description = "",
            type = KiteRecipe.TYPE_START_SERVICE,
            category = "resource",
            defaultUrl = "",
            shortcut = false,
            icon = KiteRecipeIcon(name = KiteRecipeIcon.ICON_TOOLS),
            launch = KiteLaunchConfig(openInstance = false),
            execution = KiteExecution.steps(emptyList())
        )
        CardRunStore.start(recipe, instanceId = "failed-update")
        CardRunStore.update(
            recipe = recipe,
            status = CardRunStatus.Failed,
            instanceId = "failed-update",
            lastError = "download failed"
        )
        CardRunStore.removeRun("failed-update")

        AndroidResourceFeatureGateway.create(
            KiteResourceManifestLoader(context),
            store,
            nodeRuntimeInstalled = { false }
        )

        val reconciled = store.registryEntry(resourceId)
        assertTrue(reconciled?.installed == true)
        assertEquals(KiteResourceInstallStore.UPDATE_STATUS_FAILED, reconciled?.updateStatus)
        assertEquals("download failed", reconciled?.summary)
        store.clear(resourceId)
    }

    @Test
    fun `资源运行状态只投影当前环境`() {
        val store = KiteResourceInstallStore(context, "profile_2")
        val resourceId = "test.resource.environment"
        val recipe = KiteRecipe(
            id = KiteResourceInstallRecipes.recipeId(resourceId, KiteResourceInstallRecipes.OP_OPEN),
            name = "Open",
            description = "",
            type = KiteRecipe.TYPE_START_SERVICE,
            category = "resource",
            defaultUrl = "",
            shortcut = false,
            icon = KiteRecipeIcon(name = KiteRecipeIcon.ICON_TOOLS),
            launch = KiteLaunchConfig(openInstance = false),
            execution = KiteExecution.steps(emptyList())
        )
        CardRunStore.start(recipe, instanceId = "default-open", environmentId = "default")
        CardRunStore.update(
            recipe,
            status = CardRunStatus.Running,
            instanceId = "default-open",
            environmentId = "default"
        )
        CardRunStore.start(recipe, instanceId = "profile-open", environmentId = "profile_2")
        val gateway = AndroidResourceFeatureGateway.create(
            KiteResourceManifestLoader(context),
            store,
            nodeRuntimeInstalled = { false }
        )

        assertEquals(CardRunStatus.Starting, gateway.openRunStatus(resourceId))
        store.activateEnvironment("default")
        assertEquals(CardRunStatus.Running, gateway.openRunStatus(resourceId))
    }
}
