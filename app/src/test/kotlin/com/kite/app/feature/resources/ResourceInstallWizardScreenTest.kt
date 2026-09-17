package com.kite.app.feature.resources

import android.app.Activity
import android.os.Looper
import android.view.ContextThemeWrapper
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
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
        val screen = createScreen(onPlanAction = { action, acknowledge ->
            actions += action
            acknowledge(ResourceInstallWizardPlanActionResult.Accepted)
        })
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
    fun `开始获取被拒绝后撤销准备中并恢复按钮`() {
        val screen = createScreen(onPlanAction = { _, acknowledge ->
            acknowledge(ResourceInstallWizardPlanActionResult.Rejected)
        })
        attach(screen)
        val context = screen.root.context
        screen.render(pendingState())
        shadowOf(Looper.getMainLooper()).idle()

        val button = screen.root.textViews().first {
            it.text.toString() == context.getString(R.string.resource_wizard_action_start)
        }
        button.performClick()

        assertEquals(context.getString(R.string.resource_wizard_action_start), button.text.toString())
        assertTrue(button.isEnabled)
    }

    @Test
    fun `权限等待保持准备中并在取消后恢复按钮`() {
        lateinit var acknowledge: (ResourceInstallWizardPlanActionResult) -> Unit
        val screen = createScreen(onPlanAction = { _, callback ->
            acknowledge = callback
            callback(ResourceInstallWizardPlanActionResult.Deferred)
        })
        attach(screen)
        val context = screen.root.context
        screen.render(pendingState())
        shadowOf(Looper.getMainLooper()).idle()

        val button = screen.root.textViews().first {
            it.text.toString() == context.getString(R.string.resource_wizard_action_start)
        }
        button.performClick()
        assertEquals(context.getString(R.string.resource_state_preparing), button.text.toString())
        assertFalse(button.isEnabled)

        acknowledge(ResourceInstallWizardPlanActionResult.Rejected)

        assertEquals(context.getString(R.string.resource_wizard_action_start), button.text.toString())
        assertTrue(button.isEnabled)
    }

    @Test
    fun `运行事实变化只重绑原行并更新耗时`() {
        val screen = createScreen()
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

        val row = screen.root.views().first {
            it.contentDescription?.toString() == context.getString(
                R.string.resource_wizard_row_description,
                "Tool",
                context.getString(R.string.resource_state_installing)
            )
        }
        assertTrue(row.isClickable)
        assertTrue(row.isFocusable)
    }

    @Test
    fun `失败步骤通过显式按钮提交清理且状态标签不执行动作`() {
        val failedResources = mutableListOf<String>()
        val actions = mutableListOf<KiteInstallPlanActionIntent>()
        val screen = createScreen(
            onPlanAction = { action, acknowledge ->
                actions += action
                acknowledge(ResourceInstallWizardPlanActionResult.Accepted)
            },
            onUninstallFailedResource = failedResources::add
        )
        attach(screen)
        val context = screen.root.context
        screen.render(failedState())
        shadowOf(Looper.getMainLooper()).idle()

        screen.root.textViews().first {
            it.text.toString() == context.getString(R.string.resource_state_install_failed)
        }.performClick()
        assertTrue(failedResources.isEmpty())

        screen.root.textViews().first {
            it.text.toString() == context.getString(R.string.resource_wizard_cleanup_and_retry)
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

    @Test
    fun `运行步骤只通过队列整行打开同一实例报告`() {
        val requests = mutableListOf<ResourceInstallWizardRunRequest>()
        val screen = createScreen(onOpenRun = requests::add)
        attach(screen)
        val context = screen.root.context
        screen.render(runningState(surface = CardRunSurface.Report))
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(
            1,
            screen.root.textViews().count {
                it.visibility == View.VISIBLE &&
                    it.text.toString() == context.getString(R.string.resource_state_installing)
            },
        )
        val row = screen.root.views().first {
            it.contentDescription?.toString() == context.getString(
                R.string.resource_wizard_row_description,
                "Tool",
                context.getString(R.string.resource_state_installing),
            )
        }
        assertTrue(row.isClickable)
        assertTrue(row.isFocusable)
        row.performClick()
        assertEquals(
            listOf(
                ResourceInstallWizardRunRequest(
                    resourceId = "tool",
                    operation = KiteResourceInstallStore.OP_INSTALL,
                    instanceId = "install-instance",
                    surface = CardRunSurface.Report,
                )
            ),
            requests,
        )
    }

    @Test
    fun `运行步骤直接展示实时阶段和下载来源`() {
        val screen = createScreen()
        attach(screen)
        screen.render(runningState(
            surface = CardRunSurface.Report,
            progressText = "KITE_RESOURCE_HEARTBEAT stage=acquire step=source elapsed=15",
            reportText = "Updating files:  76% (7971/10488)\n" +
                "KITE_RESOURCE_HEARTBEAT stage=acquire step=source elapsed=15",
        ))
        shadowOf(Looper.getMainLooper()).idle()

        assertTrue(screen.root.textViews().any { view ->
            val text = view.text.toString()
            text.contains("源码写入 76%")
        })
        assertFalse(screen.root.textViews().any { view ->
            view.text.toString().contains("已运行 15 秒") || view.text.toString().contains("HEARTBEAT")
        })
    }

    @Test
    fun `运行步骤摘要不会展示终端控制符`() {
        val screen = createScreen()
        attach(screen)
        screen.render(runningState(
            surface = CardRunSurface.Report,
            progressText = "\u001B[36m\u001B[1mDownloading\u001B[0m\u001B[39m codex-relay",
        ))
        shadowOf(Looper.getMainLooper()).idle()

        assertTrue(screen.root.textViews().any { view ->
            view.text.toString().contains("Downloading codex-relay")
        })
        assertFalse(screen.root.textViews().any { view ->
            view.text.toString().contains("[36m") || view.text.toString().contains("[1m")
        })
    }

    @Test
    fun `资源事实尚未齐全时显示校准态且禁止完成`() {
        val actions = mutableListOf<KiteInstallPlanActionIntent>()
        val screen = createScreen(onPlanAction = { action, _ -> actions += action })
        attach(screen)
        val context = screen.root.context

        screen.render(
            ResourceFeatureUiState(
                phase = ResourceCatalogPhase.Ready,
                items = emptyList(),
            )
        )
        shadowOf(Looper.getMainLooper()).idle()

        assertTrue(screen.root.textViews().any {
            it.text.toString() == context.getString(R.string.resource_wizard_detail_syncing)
        })
        assertTrue(screen.root.textViews().any {
            it.text.toString() == context.getString(R.string.resource_wizard_status_syncing)
        })
        val primary = screen.root.textViews().first {
            it.text.toString() == context.getString(R.string.resource_wizard_status_syncing)
        }
        assertFalse(primary.isEnabled)
        primary.performClick()
        assertTrue(actions.isEmpty())
        assertFalse(screen.root.textViews().any {
            it.text.toString() == context.getString(R.string.resource_wizard_action_complete)
        })

        screen.render(
            ResourceFeatureUiState(
                phase = ResourceCatalogPhase.Ready,
                items = listOf(item(ResourceItemPhase.NotInstalled)),
            )
        )
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(screen.root.textViews().any {
            it.text.toString() == context.getString(R.string.resource_wizard_detail_syncing)
        })
        assertFalse(screen.root.textViews().any {
            it.text.toString() == context.getString(R.string.resource_wizard_action_complete)
        })
    }

    @Test
    fun `准备计划没有步骤时显示正在准备而不是完成`() {
        val screen = createScreen(seedResourceIds = listOf("stale-resource"))
        attach(screen)
        val context = screen.root.context

        screen.render(
            ResourceFeatureUiState(
                phase = ResourceCatalogPhase.Ready,
                items = listOf(item(ResourceItemPhase.Preparing)),
                plan = ResourcePlanUiState(
                    targetResourceId = "tool",
                    isPreparing = true,
                ),
            )
        )
        shadowOf(Looper.getMainLooper()).idle()

        assertTrue(screen.root.textViews().any {
            it.text.toString() == context.getString(R.string.resource_wizard_detail_preparing)
        })
        val primary = screen.root.textViews().first {
            it.text.toString() == context.getString(R.string.resource_state_preparing)
        }
        assertFalse(primary.isEnabled)
        assertFalse(screen.root.textViews().any {
            it.text.toString() == context.getString(R.string.resource_wizard_action_complete)
        })
    }

    @Test
    fun `活动计划优先使用事实步骤而不是启动时旧种子`() {
        val context = ContextThemeWrapper(
            ApplicationProvider.getApplicationContext(),
            R.style.Theme_Kite
        )
        val state = ResourceFeatureUiState(
            phase = ResourceCatalogPhase.Ready,
            items = listOf(
                item(ResourceItemPhase.NotInstalled, resourceId = "fact-dependency"),
                item(ResourceItemPhase.NotInstalled, resourceId = "fact-target"),
            ),
            plan = ResourcePlanUiState(
                targetResourceId = "fact-target",
                isActive = true,
                resourceIds = listOf("fact-dependency", "fact-target"),
                pendingResourceIds = listOf("fact-dependency", "fact-target"),
                steps = listOf(
                    step("待获取", KiteResourceStepTone.Neutral, resourceId = "fact-dependency"),
                    step("待获取", KiteResourceStepTone.Neutral, resourceId = "fact-target"),
                ),
            ),
        )

        val projected = ResourceInstallWizardPresenter.project(
            context = context,
            state = state,
            requestedTargetResourceId = "stale-target",
            seedResourceIds = listOf("stale-dependency", "stale-target"),
        )

        assertEquals("fact-target", projected.targetResourceId)
        assertEquals(listOf("fact-dependency", "fact-target"), projected.rows.map { it.resourceId })
    }

    @Test
    fun `准备失败清除计划后仍用请求目标展示失败而不是完成`() {
        val context = ContextThemeWrapper(
            ApplicationProvider.getApplicationContext(),
            R.style.Theme_Kite
        )
        val projected = ResourceInstallWizardPresenter.project(
            context = context,
            state = ResourceFeatureUiState(
                phase = ResourceCatalogPhase.Ready,
                items = listOf(item(ResourceItemPhase.InstallFailed, resourceId = "tool")),
            ),
            requestedTargetResourceId = "tool",
            seedResourceIds = emptyList(),
        )

        assertEquals(listOf("tool"), projected.rows.map { it.resourceId })
        assertEquals(ResourceInstallWizardHeaderState.Failure, projected.headerState)
        assertEquals(context.getString(R.string.resource_wizard_detail_failure), projected.detail)
    }

    @Test
    fun `活动计划不显示后台继续取消计划或报告入口`() {
        val screen = createScreen()
        attach(screen)
        screen.render(pendingState())
        shadowOf(Looper.getMainLooper()).idle()

        val labels = screen.root.textViews().map { it.text.toString() }
        assertFalse("后台继续" in labels)
        assertFalse("取消获取计划" in labels)
        assertFalse("查看报告" in labels)
    }

    @Test
    fun `部分完成计划使用真实剩余数量`() {
        val screen = createScreen(seedResourceIds = listOf("runtime", "tool"))
        attach(screen)
        val context = screen.root.context
        screen.render(
            ResourceFeatureUiState(
                phase = ResourceCatalogPhase.Ready,
                items = listOf(
                    item(ResourceItemPhase.Installed, resourceId = "runtime"),
                    item(ResourceItemPhase.NotInstalled, resourceId = "tool"),
                ),
                plan = ResourcePlanUiState(
                    targetResourceId = "tool",
                    isActive = true,
                    resourceIds = listOf("runtime", "tool"),
                    pendingResourceIds = listOf("tool"),
                    steps = listOf(
                        step("已完成", KiteResourceStepTone.Success, resourceId = "runtime"),
                        step("待获取", KiteResourceStepTone.Neutral, resourceId = "tool"),
                    ),
                ),
            )
        )
        shadowOf(Looper.getMainLooper()).idle()

        assertTrue(screen.root.textViews().any {
            it.text.toString() == context.getString(R.string.resource_wizard_detail_pending, 1)
        })
        assertFalse(screen.root.textViews().any {
            it.text.toString() == context.getString(R.string.resource_wizard_detail_pending, 2)
        })
    }

    @Test
    fun `没有运行显示面的队列行不可点击也不可聚焦`() {
        val screen = createScreen()
        attach(screen)
        val context = screen.root.context
        screen.render(pendingState())
        shadowOf(Looper.getMainLooper()).idle()

        val row = screen.root.views().first {
            it.contentDescription?.toString() == context.getString(
                R.string.resource_wizard_row_description,
                "Tool",
                context.getString(R.string.resource_manage_queue_waiting),
            )
        }
        assertFalse(row.isClickable)
        assertFalse(row.isFocusable)
    }

    @Test
    fun `向导提供可见返回状态图标与标准触控尺寸`() {
        var exits = 0
        val screen = createScreen(onExit = { exits += 1 })
        attach(screen)
        val context = screen.root.context
        val minimumTouchTarget = (48 * context.resources.displayMetrics.density).toInt()

        screen.render(pendingState())
        shadowOf(Looper.getMainLooper()).idle()
        screen.root.views().first {
            it.contentDescription?.toString() == context.getString(R.string.common_back)
        }.performClick()
        assertEquals(1, exits)
        assertTrue(screen.root.imageViews().any {
            it.contentDescription?.toString() == context.getString(R.string.resource_wizard_header_state_pending)
        })
        assertTrue(screen.root.textViews().first {
            it.text.toString() == context.getString(R.string.resource_wizard_action_start)
        }.layoutParams.height >= minimumTouchTarget)

        screen.render(runningState(surface = CardRunSurface.Report))
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(screen.root.imageViews().any {
            it.contentDescription?.toString() == context.getString(R.string.resource_wizard_header_state_running)
        })

        screen.render(finishedState())
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(screen.root.imageViews().any {
            it.contentDescription?.toString() == context.getString(R.string.resource_wizard_header_state_completed)
        })
    }

    private fun createScreen(
        onPlanAction: (
            KiteInstallPlanActionIntent,
            (ResourceInstallWizardPlanActionResult) -> Unit,
        ) -> Unit = { _, acknowledge -> acknowledge(ResourceInstallWizardPlanActionResult.Accepted) },
        onUninstallFailedResource: (String) -> Unit = {},
        onOpenRun: (ResourceInstallWizardRunRequest) -> Unit = {},
        onExit: () -> Unit = {},
        seedResourceIds: List<String> = listOf("tool"),
    ): ResourceInstallWizardScreen = ResourceInstallWizardScreen(
        context = ContextThemeWrapper(
            ApplicationProvider.getApplicationContext(),
            R.style.Theme_Kite
        ),
        requestedTargetResourceId = "tool",
        seedResourceIds = seedResourceIds,
        onPlanAction = onPlanAction,
        onUninstallFailedResource = onUninstallFailedResource,
        onOpenRun = onOpenRun,
        onExit = onExit,
        onRetry = {},
        onLiveTickRequired = {}
    )

    private fun pendingState(): ResourceFeatureUiState = ResourceFeatureUiState(
        phase = ResourceCatalogPhase.Ready,
        items = listOf(item(ResourceItemPhase.NotInstalled)),
        plan = ResourcePlanUiState(
            targetResourceId = "tool",
            isActive = true,
            resourceIds = listOf("tool"),
            pendingResourceIds = listOf("tool"),
            steps = listOf(step("待获取", KiteResourceStepTone.Neutral))
        )
    )

    private fun runningState(
        surface: CardRunSurface,
        progressText: String = "",
        reportText: String = "",
    ): ResourceFeatureUiState {
        val run = ResourceFeatureRunSnapshot(
            instanceId = "install-instance",
            operation = KiteResourceInstallStore.OP_INSTALL,
            status = CardRunStatus.Running,
            surface = surface,
            startedAt = 1_000L,
            updatedAt = 2_000L,
            progressText = progressText,
            reportText = reportText,
        )
        return ResourceFeatureUiState(
            phase = ResourceCatalogPhase.Ready,
            items = listOf(item(ResourceItemPhase.Installing, run = run)),
            plan = ResourcePlanUiState(
                targetResourceId = "tool",
                isActive = true,
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
        run: ResourceFeatureRunSnapshot? = null,
        resourceId: String = "tool",
    ): ResourceItemUiState = ResourceItemUiState(
        descriptor = ResourceFeatureDescriptor(resourceId, resourceId.replaceFirstChar(Char::uppercase)),
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
        run: ResourceFeatureRunSnapshot? = null,
        resourceId: String = "tool",
    ): ResourcePlanStepUiState = ResourcePlanStepUiState(
        resourceId = resourceId,
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

    private fun View.imageViews(): List<ImageView> = views().filterIsInstance<ImageView>()
}
