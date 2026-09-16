package com.kite.app.platform.resources

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.kite.app.action.KiteResourceActionIntent
import com.kite.app.action.KiteResourceActionSource
import com.kite.app.application.resources.ResourceActionGateway
import com.kite.app.application.resources.ResourceFeatureChange
import com.kite.app.application.resources.ResourceFeatureDescriptor
import com.kite.app.application.resources.ResourceFeatureGateway
import com.kite.app.application.resources.ResourceFeatureRunSnapshot
import com.kite.app.feature.resources.ResourceFeatureAction
import com.kite.app.feature.resources.ResourceFeatureController
import com.kite.app.feature.resources.ResourceFeatureEffect
import com.kite.app.feature.resources.ResourceMaintenanceUiState
import com.kite.app.resources.KiteResourceInstallRecipes
import com.kite.app.resources.KiteResourceInstallStore
import com.kite.app.resources.KiteResourceManifestLoader
import com.kite.app.resources.KiteResourcePlanSnapshot
import com.kite.app.resources.KiteResourceRegistry
import com.kite.app.resources.KiteResourceRegistryEntry
import com.kite.app.resources.KiteResourceSourcePlanFactory
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
class ResourceRepairContractTest {
    private val context by lazy { ApplicationProvider.getApplicationContext<Context>() }
    private val loader by lazy { KiteResourceManifestLoader(context, isDebugBuild = true) }
    private val factory by lazy { AndroidResourceRecipeFactory(loader) }

    @Test
    fun `损坏安装暴露候选修复并且只声明目标资源写入范围`() = runTest {
        val manifests = loader.manifests().values
            .filter { manifest ->
                manifest.management.userLifecycleEnabled &&
                    KiteResourceSourcePlanFactory.plan(manifest).installActions.isNotEmpty()
            }
        val target = manifests.first()
        val other = manifests.first { manifest -> manifest.id != target.id }

        assertTrue(KiteResourceActionIntent.entries.any { it == KiteResourceActionIntent.Repair })
        assertTrue(ResourceActionGateway::class.java.declaredMethods.any { it.name == "repair" })
        assertTrue(ResourceMaintenanceUiState::class.java.declaredFields.any { it.name == "repairEnabled" })

        val repair = factory.recipe(target.id, KiteResourceInstallRecipes.OP_REPAIR)
        assertNotNull(repair)
        val repairScopes = factory.writeScopes(target.id, KiteResourceInstallRecipes.OP_REPAIR)
        val installScopes = factory.writeScopes(target.id, KiteResourceInstallRecipes.OP_INSTALL)
        assertEquals(installScopes, repairScopes)
        assertTrue(repairScopes.contains("resource:${KiteResourceInstallRecipes.safeId(target.id)}"))
        assertFalse(repairScopes.contains("resource:${KiteResourceInstallRecipes.safeId(other.id)}"))

        val script = repair?.steps.orEmpty().joinToString("\n") { step -> step.cmd.orEmpty() }
        assertTrue(script.contains("KITE_RESOURCE_CANDIDATE"))
        assertTrue(script.contains("transactional_clean=\"1\""))

        val controller = ResourceFeatureController(
            RepairGateway(
                descriptor = ResourceFeatureDescriptor(target.id, target.name, manifest = target),
                entry = KiteResourceRegistryEntry(
                    resourceId = target.id,
                    status = KiteResourceRegistry.STATUS_INSTALLED,
                    operation = KiteResourceInstallRecipes.OP_REPAIR,
                    version = target.version,
                    updateStatus = KiteResourceInstallStore.UPDATE_STATUS_FAILED,
                    summary = "托管命令缺失，需要修复安装",
                ),
            )
        )
        controller.dispatch(ResourceFeatureAction.Refresh())
        val item = controller.state.value.item(target.id)!!
        assertEquals(KiteResourceActionIntent.Repair, item.primaryIntent)
        assertTrue(item.maintenance.repairEnabled)
        val effect = controller.dispatch(
            ResourceFeatureAction.Primary(target.id, KiteResourceActionSource.Detail)
        ) as ResourceFeatureEffect.ActionRequested
        assertEquals(KiteResourceActionIntent.Repair, effect.request.intent)
    }

    private class RepairGateway(
        private val descriptor: ResourceFeatureDescriptor,
        private val entry: KiteResourceRegistryEntry,
    ) : ResourceFeatureGateway {
        override val changes: Flow<ResourceFeatureChange> = emptyFlow()

        override suspend fun loadCatalog(forceRefresh: Boolean): List<ResourceFeatureDescriptor> =
            listOf(descriptor)

        override fun registrySnapshot(resourceIds: Collection<String>): Map<String, KiteResourceRegistryEntry> =
            mapOf(entry.resourceId to entry).filterKeys { resourceId -> resourceId in resourceIds }

        override fun planSnapshot(): KiteResourcePlanSnapshot = KiteResourcePlanSnapshot()

        override fun openRunStatus(resourceId: String): CardRunStatus? = null

        override fun operationRunSnapshot(
            resourceId: String,
            operation: String,
        ): ResourceFeatureRunSnapshot? = null

        override fun homeLayout() = null
    }
}
