package com.kite.app.feature.resources

import android.app.Activity
import android.os.Looper
import android.view.ContextThemeWrapper
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.kite.app.R
import com.kite.app.action.KiteResourceActionIntent
import com.kite.app.application.resources.ResourceFeatureDescriptor
import com.kite.app.resources.KiteResourceInstallStepUiProjection
import com.kite.app.resources.KiteResourceStepTone
import com.kite.app.resources.KiteResourceUiProjection
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
class ResourceManageScreenTest {
    @Test
    fun `已获取集合稳定时事实更新只重绑原行`() {
        val actions = mutableListOf<String>()
        val screen = createScreen(onPrimaryAction = actions::add)
        attach(screen)
        val context = screen.root.context
        screen.render(installedState(actionLabel = "打开", stateLabel = "已获取"))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))

        val initialButton = screen.root.textViews().first {
            it.text.toString() == context.getString(R.string.resource_action_open)
        }
        initialButton.performClick()
        assertEquals(listOf("tool"), actions)

        screen.render(installedState(actionLabel = "运行中", stateLabel = "运行中"))
        shadowOf(Looper.getMainLooper()).idle()

        val reboundButton = screen.root.textViews().first {
            it.text.toString() == context.getString(R.string.resource_action_open)
        }
        assertSame(initialButton, reboundButton)
        screen.acknowledge("tool", KiteResourceActionIntent.Open)
        assertEquals(context.getString(R.string.resource_state_starting), reboundButton.text.toString())
        assertFalse(reboundButton.isEnabled)
    }

    @Test
    fun `同一执行计划只更新状态并完整上交打开与取消`() {
        val opened = mutableListOf<String>()
        val cancelled = mutableListOf<Pair<String, List<String>>>()
        val screen = createScreen(
            onOpenPlan = opened::add,
            onCancelPlan = { target, ids -> cancelled += target to ids }
        )
        attach(screen)
        val context = screen.root.context
        screen.render(planState(statusLabel = "待获取", tone = KiteResourceStepTone.Neutral))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))

        val initialBadge = screen.root.textViews().first {
            it.text.toString() == context.getString(R.string.resource_manage_queue_waiting)
        }
        val card = screen.root.views().first {
            it.contentDescription?.toString() == context.getString(
                R.string.resource_manage_queue_description,
                "Tool",
                context.getString(R.string.resource_manage_queue_waiting)
            )
        }
        card.performClick()
        assertEquals(listOf("tool"), opened)

        screen.render(planState(statusLabel = "获取中", tone = KiteResourceStepTone.Primary))
        shadowOf(Looper.getMainLooper()).idle()

        val reboundBadge = screen.root.textViews().first {
            it.text.toString() == context.getString(R.string.resource_state_installing)
        }
        assertSame(initialBadge, reboundBadge)

        val down = MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_DOWN, 100f, 240f, 0)
        val up = MotionEvent.obtain(0L, 20L, MotionEvent.ACTION_UP, 100f, 0f, 0)
        card.dispatchTouchEvent(down)
        card.dispatchTouchEvent(up)
        down.recycle()
        up.recycle()

        assertEquals(listOf("tool" to listOf("tool")), cancelled)
    }

    @Test
    fun `加载和返回具有确定状态`() {
        var backCount = 0
        val screen = createScreen(onBack = { backCount += 1 })
        attach(screen)
        val context = screen.root.context
        screen.render(ResourceFeatureUiState(phase = ResourceCatalogPhase.Loading))
        shadowOf(Looper.getMainLooper()).idle()

        assertTrue(screen.root.textViews().any {
            it.text.toString() == context.getString(R.string.resource_manage_loading_title)
        })
        screen.root.views().first {
            it.contentDescription?.toString() == context.getString(R.string.common_back)
        }.performClick()
        assertEquals(1, backCount)
    }

    private fun createScreen(
        onBack: () -> Unit = {},
        onPrimaryAction: (String) -> Unit = {},
        onOpenPlan: (String) -> Unit = {},
        onCancelPlan: (String, List<String>) -> Unit = { _, _ -> }
    ): ResourceManageScreen = ResourceManageScreen(
        context = ContextThemeWrapper(
            ApplicationProvider.getApplicationContext(),
            R.style.Theme_Kite
        ),
        initialScrollY = 0,
        onBack = onBack,
        onOpenDetail = {},
        onPrimaryAction = onPrimaryAction,
        onOpenPlan = onOpenPlan,
        onCancelPlan = onCancelPlan,
        onRetry = {}
    )

    private fun installedState(actionLabel: String, stateLabel: String): ResourceFeatureUiState =
        ResourceFeatureUiState(
            phase = ResourceCatalogPhase.Ready,
            items = listOf(item(ResourceItemPhase.Installed, actionLabel, stateLabel))
        )

    private fun planState(
        statusLabel: String,
        tone: KiteResourceStepTone
    ): ResourceFeatureUiState = ResourceFeatureUiState(
        phase = ResourceCatalogPhase.Ready,
        items = listOf(item(ResourceItemPhase.NotInstalled, "获取", "未获取")),
        plan = ResourcePlanUiState(
            targetResourceId = "tool",
            resourceIds = listOf("tool"),
            steps = listOf(
                ResourcePlanStepUiState(
                    resourceId = "tool",
                    projection = KiteResourceInstallStepUiProjection(
                        statusLabel = statusLabel,
                        tone = tone,
                        failed = false,
                        uninstalling = false
                    )
                )
            )
        )
    )

    private fun item(
        phase: ResourceItemPhase,
        actionLabel: String,
        stateLabel: String
    ): ResourceItemUiState = ResourceItemUiState(
        descriptor = ResourceFeatureDescriptor("tool", "Tool"),
        phase = phase,
        projection = KiteResourceUiProjection(
            stateLabel = stateLabel,
            actionLabel = actionLabel,
            actionEnabled = true,
            secondaryActionLabel = null
        ),
        primaryIntent = if (phase == ResourceItemPhase.NotInstalled) {
            KiteResourceActionIntent.Install
        } else {
            KiteResourceActionIntent.Open
        },
        secondaryIntent = null
    )

    private fun attach(screen: ResourceManageScreen) {
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
