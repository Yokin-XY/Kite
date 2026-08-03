package com.kite.app.feature.resources

import com.kite.app.action.KiteResourceActionIntent
import com.kite.app.action.KiteResourceActionSource
import com.kite.app.application.resources.ResourceFeatureDescriptor
import com.kite.app.application.resources.ResourceFeatureChange
import com.kite.app.application.resources.ResourceFeatureGateway
import com.kite.app.application.resources.ResourceFeatureRunSnapshot
import com.kite.app.resources.KiteResourceInstallStore
import com.kite.app.resources.KiteResourcePlanSnapshot
import com.kite.app.resources.KiteResourceRegistry
import com.kite.app.resources.KiteResourceRegistryEntry
import com.kite.app.resources.KiteResourceManagementMode
import com.kite.app.resources.KiteResourceManagementSpec
import com.kite.app.resources.KiteResourceManifest
import com.kite.app.resources.KiteResourceSourceSpec
import com.kite.app.resources.KiteResourceVersionProbeSpec
import com.kite.app.run.CardRunStatus
import com.kite.app.run.CardRunSurface
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.json.JSONObject

class ResourceFeatureControllerTest {
    @Test
    fun `未获取资源投影为可获取且动作请求保持来源`() = runTest {
        val gateway = FakeGateway()
        val controller = ResourceFeatureController(gateway)

        controller.dispatch(ResourceFeatureAction.Refresh())

        val item = controller.state.value.item("tool")!!
        assertEquals(ResourceCatalogPhase.Ready, controller.state.value.phase)
        assertEquals(ResourceItemPhase.NotInstalled, item.phase)
        assertEquals("获取", item.projection.actionLabel)
        assertEquals(KiteResourceActionIntent.Install, item.primaryIntent)
        assertNull(item.secondaryIntent)

        val effect = controller.dispatch(
            ResourceFeatureAction.Primary("tool", KiteResourceActionSource.Detail)
        ) as ResourceFeatureEffect.ActionRequested
        assertEquals("tool", effect.request.resourceId)
        assertEquals(KiteResourceActionIntent.Install, effect.request.intent)
        assertEquals(KiteResourceActionSource.Detail, effect.request.source)
    }

    @Test
    fun `安装计划运行中统一投影为可恢复向导和可取消`() = runTest {
        val gateway = FakeGateway().apply {
            plan = KiteResourcePlanSnapshot(
                targetResourceId = "tool",
                status = KiteResourceInstallStore.PLAN_STATUS_ACTIVE,
                resourceIds = listOf("tool"),
                runningResourceIds = listOf("tool"),
                stepStatusByResourceId = mapOf("tool" to KiteResourceInstallStore.PLAN_STEP_RUNNING)
            )
        }
        val controller = ResourceFeatureController(gateway)

        controller.dispatch(ResourceFeatureAction.Refresh())

        val item = controller.state.value.item("tool")!!
        assertEquals(ResourceItemPhase.Installing, item.phase)
        assertEquals("获取中", item.projection.actionLabel)
        assertEquals(KiteResourceActionIntent.ReopenInstall, item.primaryIntent)
        assertEquals(KiteResourceActionIntent.CancelInstall, item.secondaryIntent)
        assertEquals("获取中", controller.state.value.plan.steps.single().projection.statusLabel)
    }

    @Test
    fun `准备计划尚无步骤时卡片与详情仍可重开和取消`() = runTest {
        val gateway = FakeGateway().apply {
            plan = KiteResourcePlanSnapshot(
                targetResourceId = "tool",
                status = KiteResourceInstallStore.PLAN_STATUS_PREPARING,
            )
        }
        val controller = ResourceFeatureController(gateway)

        controller.dispatch(ResourceFeatureAction.Refresh())

        val item = controller.state.value.item("tool")!!
        assertEquals(ResourceItemPhase.Installing, item.phase)
        assertEquals("获取中", item.projection.stateLabel)
        assertEquals("获取中", item.projection.actionLabel)
        assertTrue(item.projection.actionEnabled)
        assertEquals(KiteResourceActionIntent.ReopenInstall, item.primaryIntent)
        assertEquals(KiteResourceActionIntent.CancelInstall, item.secondaryIntent)
        assertTrue(controller.state.value.plan.isPreparing)

        val reopen = controller.dispatch(
            ResourceFeatureAction.Primary("tool", KiteResourceActionSource.Card)
        ) as ResourceFeatureEffect.ActionRequested
        assertEquals(KiteResourceActionIntent.ReopenInstall, reopen.request.intent)

        val cancel = controller.dispatch(
            ResourceFeatureAction.Secondary("tool", KiteResourceActionSource.Detail)
        ) as ResourceFeatureEffect.ActionRequested
        assertEquals(KiteResourceActionIntent.CancelInstall, cancel.request.intent)
    }

    @Test
    fun `安装失败统一提供重新获取和清理失败安装`() = runTest {
        val gateway = FakeGateway().apply {
            registry["tool"] = entry(
                status = KiteResourceRegistry.STATUS_FAILED,
                operation = KiteResourceInstallStore.OP_INSTALL,
                summary = "network_failed"
            )
        }
        val controller = ResourceFeatureController(gateway)

        controller.dispatch(ResourceFeatureAction.Refresh())

        val item = controller.state.value.item("tool")!!
        assertEquals(ResourceItemPhase.InstallFailed, item.phase)
        assertEquals("重新获取", item.projection.actionLabel)
        assertEquals(KiteResourceActionIntent.Install, item.primaryIntent)
        assertEquals(KiteResourceActionIntent.CancelFailedInstall, item.secondaryIntent)
        assertEquals("network_failed", item.registrySummary)
    }

    @Test
    fun `已安装且运行中的资源统一提供打开和中止`() = runTest {
        val gateway = FakeGateway().apply {
            registry["tool"] = entry(status = KiteResourceRegistry.STATUS_INSTALLED)
            runStatuses["tool"] = CardRunStatus.Running
        }
        val controller = ResourceFeatureController(gateway)

        controller.dispatch(ResourceFeatureAction.Refresh())

        val item = controller.state.value.item("tool")!!
        assertEquals(ResourceItemPhase.Running, item.phase)
        assertEquals("运行中", item.projection.actionLabel)
        assertEquals(KiteResourceActionIntent.Open, item.primaryIntent)
        assertEquals(KiteResourceActionIntent.Stop, item.secondaryIntent)

        val effect = controller.dispatch(
            ResourceFeatureAction.Secondary("tool", KiteResourceActionSource.Card)
        ) as ResourceFeatureEffect.ActionRequested
        assertEquals(KiteResourceActionIntent.Stop, effect.request.intent)
    }

    @Test
    fun `卸载失败进入全量重新获取而不是重复卸载`() = runTest {
        val gateway = FakeGateway().apply {
            registry["tool"] = entry(
                status = KiteResourceRegistry.STATUS_FAILED,
                operation = KiteResourceInstallStore.OP_UNINSTALL
            )
        }
        val controller = ResourceFeatureController(gateway)

        controller.dispatch(ResourceFeatureAction.Refresh())

        val item = controller.state.value.item("tool")!!
        assertEquals(ResourceItemPhase.UninstallFailed, item.phase)
        assertEquals("重新获取", item.projection.actionLabel)
        assertEquals(KiteResourceActionIntent.Install, item.primaryIntent)
    }

    @Test
    fun `准备安装卸载以及运行过渡态都有确定语义`() = runTest {
        val cases = listOf(
            Triple(KiteResourceRegistry.STATUS_PREPARING, null, ResourceItemPhase.Preparing),
            Triple(KiteResourceRegistry.STATUS_INSTALLING, null, ResourceItemPhase.Installing),
            Triple(KiteResourceRegistry.STATUS_UNINSTALLING, null, ResourceItemPhase.Uninstalling),
            Triple(KiteResourceRegistry.STATUS_INSTALLED, null, ResourceItemPhase.Installed),
            Triple(KiteResourceRegistry.STATUS_INSTALLED, CardRunStatus.Starting, ResourceItemPhase.Starting),
            Triple(KiteResourceRegistry.STATUS_INSTALLED, CardRunStatus.WaitingTerminal, ResourceItemPhase.Running),
            Triple(KiteResourceRegistry.STATUS_INSTALLED, CardRunStatus.Stopping, ResourceItemPhase.Stopping)
        )

        cases.forEach { (registryStatus, runStatus, expectedPhase) ->
            val gateway = FakeGateway().apply {
                registry["tool"] = entry(status = registryStatus)
                runStatus?.let { runStatuses["tool"] = it }
            }
            val controller = ResourceFeatureController(gateway)
            controller.dispatch(ResourceFeatureAction.Refresh())

            assertEquals(expectedPhase, controller.state.value.item("tool")!!.phase)
        }
    }

    @Test
    fun `事实校准只重投影现有目录而不重新加载目录`() = runTest {
        val gateway = FakeGateway()
        val controller = ResourceFeatureController(gateway)
        controller.dispatch(ResourceFeatureAction.Refresh())
        val initialRevision = controller.state.value.revision
        gateway.registry["tool"] = entry(status = KiteResourceRegistry.STATUS_PREPARING)

        controller.dispatch(ResourceFeatureAction.ReconcileFacts)

        assertEquals(1, gateway.loadCount)
        assertTrue(controller.state.value.revision > initialRevision)
        assertEquals(ResourceItemPhase.Preparing, controller.state.value.item("tool")!!.phase)
    }

    @Test
    fun `安装步骤携带当前操作的确定运行实例`() = runTest {
        val run = ResourceFeatureRunSnapshot(
            instanceId = "install-instance",
            operation = KiteResourceInstallStore.OP_INSTALL,
            status = CardRunStatus.Running,
            surface = CardRunSurface.Terminal,
            startedAt = 10L,
            updatedAt = 20L
        )
        val gateway = FakeGateway().apply {
            plan = KiteResourcePlanSnapshot(
                targetResourceId = "tool",
                resourceIds = listOf("tool"),
                runningResourceIds = listOf("tool"),
                stepStatusByResourceId = mapOf("tool" to KiteResourceInstallStore.PLAN_STEP_RUNNING)
            )
            operationRuns["tool" to KiteResourceInstallStore.OP_INSTALL] = run
        }
        val controller = ResourceFeatureController(gateway)

        controller.dispatch(ResourceFeatureAction.Refresh())

        val item = controller.state.value.item("tool")!!
        val step = controller.state.value.plan.steps.single()
        assertEquals(KiteResourceInstallStore.OP_INSTALL, item.operation)
        assertEquals(run, item.operationRun)
        assertEquals(run, step.run)
    }

    @Test
    fun `目录失败保留已有投影并暴露确定错误`() = runTest {
        val gateway = FakeGateway()
        val controller = ResourceFeatureController(gateway)
        controller.dispatch(ResourceFeatureAction.Refresh())
        gateway.loadFailure = IllegalStateException("catalog_failed")

        controller.dispatch(ResourceFeatureAction.Refresh(forceCatalogRefresh = true))

        assertEquals(ResourceCatalogPhase.Failed, controller.state.value.phase)
        assertEquals("catalog_failed", controller.state.value.errorMessage)
        assertEquals(listOf("tool"), controller.state.value.items.map(ResourceItemUiState::resourceId))
    }

    @Test
    fun `已安装外部扩展从共享版本事实开放检查和更新意图`() = runTest {
        val gateway = FakeGateway().apply {
            catalog = listOf(ResourceFeatureDescriptor("tool", "Tool", manifest = managedManifest()))
            registry["tool"] = KiteResourceRegistryEntry(
                resourceId = "tool",
                status = KiteResourceRegistry.STATUS_INSTALLED,
                version = "1.0.0",
                latestVersion = "2.0.0",
                updateStatus = KiteResourceInstallStore.UPDATE_STATUS_AVAILABLE
            )
        }
        val controller = ResourceFeatureController(gateway)
        controller.dispatch(ResourceFeatureAction.Refresh())

        val item = controller.state.value.item("tool")!!
        assertTrue(item.maintenance.checkUpdateEnabled)
        assertTrue(item.maintenance.updateEnabled)
        assertEquals(KiteResourceActionIntent.Open, item.primaryIntent)
        val effect = controller.dispatch(
            ResourceFeatureAction.Explicit(
                "tool",
                KiteResourceActionIntent.Update,
                KiteResourceActionSource.Detail
            )
        ) as ResourceFeatureEffect.ActionRequested
        assertEquals(KiteResourceActionIntent.Update, effect.request.intent)
    }

    private class FakeGateway : ResourceFeatureGateway {
        override val changes: Flow<ResourceFeatureChange> = emptyFlow()
        var catalog = listOf(ResourceFeatureDescriptor("tool", "Tool"))
        val registry = linkedMapOf<String, KiteResourceRegistryEntry>()
        var plan = KiteResourcePlanSnapshot()
        val runStatuses = linkedMapOf<String, CardRunStatus>()
        val operationRuns = linkedMapOf<Pair<String, String>, ResourceFeatureRunSnapshot>()
        var loadFailure: Throwable? = null
        var loadCount: Int = 0

        override suspend fun loadCatalog(forceRefresh: Boolean): List<ResourceFeatureDescriptor> {
            loadCount += 1
            loadFailure?.let { throw it }
            return catalog
        }

        override fun registrySnapshot(resourceIds: Collection<String>): Map<String, KiteResourceRegistryEntry> =
            registry.filterKeys { it in resourceIds }

        override fun planSnapshot(): KiteResourcePlanSnapshot = plan

        override fun openRunStatus(resourceId: String): CardRunStatus? = runStatuses[resourceId]

        override fun operationRunSnapshot(
            resourceId: String,
            operation: String
        ): ResourceFeatureRunSnapshot? = operationRuns[resourceId to operation]

        override fun homeLayout() = null
    }

    private fun entry(
        status: String,
        operation: String = "",
        summary: String = ""
    ): KiteResourceRegistryEntry = KiteResourceRegistryEntry(
        resourceId = "tool",
        status = status,
        operation = operation,
        summary = summary,
        updatedAt = 123L
    )

    private fun managedManifest() = KiteResourceManifest(
        id = "tool",
        name = "Tool",
        description = "",
        version = "",
        iconText = "",
        iconAsset = "",
        displayCategory = "",
        displayAccent = "",
        displaySizeLabel = "",
        displayLongDescription = "",
        displayBadge = null,
        displayMedia = null,
        displayPreviewCards = emptyList(),
        displayRequirementRows = emptyList(),
        displayRecommendations = emptyList(),
        sections = listOf("test"),
        tags = emptyList(),
        provides = emptyList(),
        baseRequirements = emptyList(),
        defaultRequirements = emptyList(),
        extensions = emptyList(),
        management = KiteResourceManagementSpec(
            mode = KiteResourceManagementMode.MANAGED_EXTENSION,
            managedCommands = listOf("tool"),
            versionProbe = KiteResourceVersionProbeSpec("tool --version")
        ),
        source = KiteResourceSourceSpec(type = "npm", packageName = "tool"),
        sourceType = "npm",
        installActions = emptyList(),
        updateActions = emptyList(),
        uninstallActions = emptyList(),
        openRecipe = null,
        homeCards = emptyList(),
        rawJson = JSONObject()
    )
}
