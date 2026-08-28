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
import com.kite.app.run.CardRunSurface
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
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

    @Test
    fun `目录合同升级在点击打开前投影为可更新`() = runBlocking {
        val store = KiteResourceInstallStore(context)
        val loader = KiteResourceManifestLoader(context)
        val current = requireNotNull(loader.requestManifest(HERMES_RESOURCE_ID))
        val oldVersion = "v2026.8.27"
        val installed = JSONObject(current.rawJson.toString()).apply {
            getJSONObject("base").put("version", oldVersion)
            getJSONObject("actions").remove("update")
        }
        store.clear(HERMES_RESOURCE_ID)
        store.markInstalled(HERMES_RESOURCE_ID, oldVersion, "old-run", "done")
        store.saveInstalledSnapshot(
            resourceId = HERMES_RESOURCE_ID,
            name = current.name,
            iconJson = "{}",
            version = oldVersion,
            manifestJson = installed.toString(),
        )
        val gateway = AndroidResourceFeatureGateway.create(
            loader,
            store,
            nodeRuntimeInstalled = { false },
        )

        gateway.loadCatalog(forceRefresh = false)

        val reconciled = store.registryEntry(HERMES_RESOURCE_ID)
        assertEquals(KiteResourceInstallStore.UPDATE_STATUS_AVAILABLE, reconciled?.updateStatus)
        assertEquals(current.version, reconciled?.latestVersion)
        assertEquals(KiteResourceInstallRecipes.OP_UPDATE, reconciled?.operation)
        store.clear(HERMES_RESOURCE_ID)
    }

    @Test
    fun `安装进度优先绑定资源登记指向的向导子实例`() {
        val store = KiteResourceInstallStore(context)
        val resourceId = "test.resource.progress.${System.nanoTime()}"
        val recipe = KiteRecipe(
            id = KiteResourceInstallRecipes.recipeId(resourceId, KiteResourceInstallRecipes.OP_INSTALL),
            name = "Install",
            description = "",
            type = KiteRecipe.TYPE_START_SERVICE,
            category = "resource",
            defaultUrl = "",
            shortcut = false,
            icon = KiteRecipeIcon(name = KiteRecipeIcon.ICON_TOOLS),
            launch = KiteLaunchConfig(openInstance = false),
            execution = KiteExecution.steps(emptyList())
        )
        CardRunStore.start(recipe, instanceId = "old-root")
        CardRunStore.update(
            recipe,
            status = CardRunStatus.Failed,
            instanceId = "old-root",
            lastError = "old",
        )
        CardRunStore.start(recipe, instanceId = "live-child", parentInstanceId = "wizard")
        CardRunStore.update(
            recipe,
            status = CardRunStatus.Running,
            instanceId = "live-child",
            parentInstanceId = "wizard",
            surface = CardRunSurface.Report,
            lastMeaningfulOutput = "资源仍在下载",
            shellReportText = "Updating files:  42%",
        )
        store.markInstalling(
            resourceId,
            runId = "live-child",
            operation = KiteResourceInstallRecipes.OP_INSTALL,
        )
        val gateway = AndroidResourceFeatureGateway.create(
            KiteResourceManifestLoader(context),
            store,
            nodeRuntimeInstalled = { false },
        )

        val snapshot = gateway.operationRunSnapshot(resourceId, KiteResourceInstallRecipes.OP_INSTALL)

        assertEquals("live-child", snapshot?.instanceId)
        assertEquals("资源仍在下载", snapshot?.progressText)
        assertEquals("Updating files:  42%", snapshot?.reportText)
        store.clear(resourceId)
    }

    private companion object {
        const val HERMES_RESOURCE_ID = "kite.hermes.core"
    }
}
