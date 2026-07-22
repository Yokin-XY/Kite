package com.kite.app.feature.resources

import android.app.Activity
import android.os.Looper
import android.view.ContextThemeWrapper
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.kite.app.R
import com.kite.app.action.KiteInstallPlanActionIntent
import com.kite.app.action.KiteResourceActionIntent
import com.kite.app.application.resources.ResourceFeatureDescriptor
import com.kite.app.application.resources.ResourceFeatureRunSnapshot
import com.kite.app.resources.KiteResourceInstallStepUiProjection
import com.kite.app.resources.KiteResourceInstallStore
import com.kite.app.resources.KiteResourceStepTone
import com.kite.app.resources.KiteResourceUiProjection
import com.kite.app.run.CardRunStatus
import com.kite.app.run.CardRunSurface
import java.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class ResourceInstallWizardScreenTest {
    @Test
    fun `开始获取立即承诺准备中并只提交计划意图`() {
        val actions = mutableListOf<KiteInstallPlanActionIntent>()
        val screen = createScreen(onPlanAction = actions::add)
        attach(screen)
        val context = screen.root.context
        screen.render(pendingState())
        shadowOf(Looper.getMainLooper()).idle()

        val button = screen.root.textViews().first {
            it.text.toString() == context.getString(R.string.resource_wizard_action_start)
        }
        button.performClick()

        assertEquals(listOf(KiteInstallPlanActionIntent.StartNext), actions)
        assertEquals(context.getString(R.string.resource_state_preparing), button.text.toString())
        assertFalse(button.isEnabled)
    }

    @Test
    fun `运行事实变化只重绑原行并携带确定实例打开显示面`() {
        val requests = mutableListOf<ResourceInstallWizardRunRequest>()
        val screen = createScreen(onOpenRun = requests::add)
        attach(screen)
        val context = screen.root.context
        screen.render(runningState(surface = CardRunSurface.Report))
        shadowOf(Looper.getMainLooper()).idle()

        val initialStatus = screen.root.textViews().first {
            it.text.toString() == context.getString(R.string.resource_state_installing)
        }
        screen.render(runningState(surface = CardRunSurface.Terminal))
        shadowOf(Looper.getMainLooper()).idle()

        val reboundStatus = screen.root.textViews().first {
            it.text.toString() == context.getString(R.string.resource_state_installing)
        }
        assertSame(initialStatus, reboundStatus)
        screen.tick(now = 66_000L)
        val base = "${context.getString(R.string.resource_wizard_fallback_resource)} · 1/1"
        assertTrue(screen.root.textViews().any {
            it.text.toString() == context.getString(R.string.resource_wizard_subtitle_running, base, "01:05")
        })

        screen.root.textViews().first {
            it.text.toString() == context.getString(R.string.resource_wizard_open_terminal)
        }.performClick()
        screen.root.views().first {
            it.contentDescription?.toString() == context.getString(
                R.string.resource_wizard_row_description,
                "Tool",
                context.getString(R.string.resource_state_installing)
            )
        }.performClick()

        assertEquals(CardRunSurface.Terminal, requests[0].surface)
        assertEquals(CardRunSurface.Report, requests[1].surface)
        assertTrue(requests.all { it.instanceId == "install-instance" })
        assertTrue(requests.all { it.operation == KiteResourceInstallStore.OP_INSTALL })
    }

    @Test
    fun `失败步骤提交卸载处理且计划清空后仍保留完成队列`() {
        val failedResources = mutableListOf<String>()
        val actions = mutableListOf<KiteInstallPlanActionIntent>()
        val screen = createScreen(
            onPlanAction = actions::add,
            onUninstallFailedResource = failedResources::add
        )
        attach(screen)
        val context = screen.root.context
        screen.render(failedState())
        shadowOf(Looper.getMainLooper()).idle()

        screen.root.textViews().first {
            it.text.toString() == context.getString(R.string.resource_state_install_failed)
        }.performClick()
        assertEquals(listOf("tool"), failedResources)
        assertTrue(screen.root.textViews().any {
            it.text.toString() == context.getString(R.string.resource_wizard_detail_failure)
        })

        screen.render(finishedState())
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(screen.root.textViews().any {
            it.text.toString() == context.getString(R.string.resource_manage_queue_completed)
        })
        screen.root.textViews().first {
            it.text.toString() == context.getString(R.string.resource_wizard_action_complete)
        }.performClick()
        assertEquals(listOf(KiteInstallPlanActionIntent.Finish), actions)
    }

    private fun createScreen(
        onPlanAction: (KiteInstallPlanActionIntent) -> Unit = {},
        onOpenRun: (ResourceInstallWizardRunRequest) -> Unit = {},
        onUninstallFailedResource: (String) -> Unit = {}
    ): ResourceInstallWizardScreen = ResourceInstallWizardScreen(
        context = ContextThemeWrapper(
            ApplicationProvider.getApplicationContext(),
            R.style.Theme_Kite
        ),
        requestedTargetResourceId = "tool",
        seedResourceIds = listOf("tool"),
        onPlanAction = onPlanAction,
        onOpenRun = onOpenRun,
        onUninstallFailedResource = onUninstallFailedResource,
        onReportUnavailable = {},
        onRetry = {},
        onLiveTickRequired = {}
    )

    private fun pendingState(): ResourceFeatureUiState = ResourceFeatureUiState(
        phase = ResourceCatalogPhase.Ready,
        items = listOf(item(ResourceItemPhase.NotInstalled)),
        plan = ResourcePlanUiState(
            targetResourceId = "tool",
            resourceIds = listOf("tool"),
            pendingResourceIds = listOf("tool"),
            steps = listOf(step("待获取", KiteResourceStepTone.Neutral))
        )
    )

    private fun runningState(surface: CardRunSurface): ResourceFeatureUiState {
        val run = ResourceFeatureRunSnapshot(
            instanceId = "install-instance",
            operation = KiteResourceInstallStore.OP_INSTALL,
            status = CardRunStatus.Running,
            surface = surface,
            startedAt = 1_000L,
            updatedAt = 2_000L
        )
        return ResourceFeatureUiState(
            phase = ResourceCatalogPhase.Ready,
            items = listOf(item(ResourceItemPhase.Installing, run = run)),
            plan = ResourcePlanUiState(
                targetResourceId = "tool",
                resourceIds = listOf("tool"),
                runningResourceIds = listOf("tool"),
                steps = listOf(step("获取中", KiteResourceStepTone.Primary, run = run))
            )
        )
    }

    private fun failedState(): ResourceFeatureUiState = ResourceFeatureUiState(
        phase = ResourceCatalogPhase.Ready,
        items = listOf(item(ResourceItemPhase.InstallFailed)),
        plan = ResourcePlanUiState(
            targetResourceId = "tool",
            resourceIds = listOf("tool"),
            steps = listOf(step("需卸载", KiteResourceStepTone.Danger, failed = true))
        )
    )

    private fun finishedState(): ResourceFeatureUiState = ResourceFeatureUiState(
        phase = ResourceCatalogPhase.Ready,
        items = listOf(item(ResourceItemPhase.Installed))
    )

    private fun item(
        phase: ResourceItemPhase,
        run: ResourceFeatureRunSnapshot? = null
    ): ResourceItemUiState = ResourceItemUiState(
        descriptor = ResourceFeatureDescriptor("tool", "Tool"),
        phase = phase,
        projection = KiteResourceUiProjection(
            stateLabel = "",
            actionLabel = "",
            actionEnabled = true,
            secondaryActionLabel = null
        ),
        primaryIntent = KiteResourceActionIntent.Install,
        secondaryIntent = null,
        operation = KiteResourceInstallStore.OP_INSTALL,
        operationRun = run
    )

    private fun step(
        label: String,
        tone: KiteResourceStepTone,
        failed: Boolean = false,
        run: ResourceFeatureRunSnapshot? = null
    ): ResourcePlanStepUiState = ResourcePlanStepUiState(
        resourceId = "tool",
        projection = KiteResourceInstallStepUiProjection(
            statusLabel = label,
            tone = tone,
            failed = failed,
            uninstalling = false
        ),
        operation = KiteResourceInstallStore.OP_INSTALL,
        run = run
    )

    private fun attach(screen: ResourceInstallWizardScreen) {
        Robolectric.buildActivity(Activity::class.java)
            .setup()
            .get()
            .setContentView(screen.root)
    }

    private fun View.views(): List<View> = buildList {
        add(this@views)
        if (this@views is ViewGroup) {
            for (index in 0 until childCount) addAll(getChildAt(index).views())
        }
    }

    private fun View.textViews(): List<TextView> = views().filterIsInstance<TextView>()
}
