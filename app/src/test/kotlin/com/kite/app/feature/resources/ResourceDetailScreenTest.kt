package com.kite.app.feature.resources

import android.app.Activity
import android.os.Looper
import android.view.ContextThemeWrapper
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.kite.app.R
import com.kite.app.action.KiteResourceActionIntent
import com.kite.app.application.resources.ResourceFeatureDescriptor
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
class ResourceDetailScreenTest {
    @Test
    fun `详情结构稳定时事实更新只重绑动作与状态`() {
        val primaryActions = mutableListOf<String>()
        val screen = createScreen(onPrimaryAction = primaryActions::add)
        attach(screen)
        screen.render(state(installed = false))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))

        val initialButton = screen.root.textViews().first { it.text.toString() == "获取" }
        initialButton.performClick()
        assertEquals(listOf("tool"), primaryActions)

        screen.render(state(installed = true))
        shadowOf(Looper.getMainLooper()).idle()

        val reboundButton = screen.root.textViews().first { it.text.toString() == "打开" }
        assertSame(initialButton, reboundButton)
        assertTrue(screen.root.textViews().any { it.text.toString() == "已获取" })
    }

    @Test
    fun `详情动作即时承诺且导航事件完整上交`() {
        var backCount = 0
        val more = mutableListOf<String>()
        val rawJson = mutableListOf<String>()
        val secondary = mutableListOf<String>()
        val screen = createScreen(
            onBack = { backCount += 1 },
            onMore = more::add,
            onRawJson = rawJson::add,
            onSecondaryAction = secondary::add
        )
        attach(screen)
        screen.render(state(installed = true, running = true))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))

        screen.root.views().first { it.contentDescription?.toString() == "返回" }.performClick()
        screen.root.views().first { it.contentDescription?.toString() == "更多操作" }.performClick()
        screen.root.views().first { it.contentDescription?.toString() == "查看原始 JSON" }.performClick()
        val stopButton = screen.root.textViews().first { it.text.toString() == "终止" }
        stopButton.performClick()

        assertEquals(1, backCount)
        assertEquals(listOf("tool"), more)
        assertEquals(listOf("tool"), rawJson)
        assertEquals(listOf("tool"), secondary)

        screen.acknowledgeSecondary(KiteResourceActionIntent.Stop)

        assertEquals("停止中", stopButton.text.toString())
        assertFalse(stopButton.isEnabled)
    }

    private fun createScreen(
        onBack: () -> Unit = {},
        onMore: (String) -> Unit = {},
        onRawJson: (String) -> Unit = {},
        onPrimaryAction: (String) -> Unit = {},
        onSecondaryAction: (String) -> Unit = {}
    ): ResourceDetailScreen = ResourceDetailScreen(
        context = ContextThemeWrapper(
            ApplicationProvider.getApplicationContext(),
            R.style.Theme_Kite
        ),
        resourceId = "tool",
        initialScrollY = 0,
        onBack = onBack,
        onMore = onMore,
        onRawJson = onRawJson,
        onOpenDetail = {},
        onPrimaryAction = onPrimaryAction,
        onSecondaryAction = onSecondaryAction,
        onRetry = {}
    )

    private fun state(installed: Boolean, running: Boolean = false): ResourceFeatureUiState {
        val item = ResourceItemUiState(
            descriptor = ResourceFeatureDescriptor("tool", "Tool"),
            phase = when {
                running -> ResourceItemPhase.Running
                installed -> ResourceItemPhase.Installed
                else -> ResourceItemPhase.NotInstalled
            },
            projection = KiteResourceUiProjection(
                stateLabel = when {
                    running -> "运行中"
                    installed -> "已获取"
                    else -> "未获取"
                },
                actionLabel = if (installed) "打开" else "获取",
                actionEnabled = true,
                secondaryActionLabel = if (running) "终止" else null
            ),
            primaryIntent = if (installed) KiteResourceActionIntent.Open else KiteResourceActionIntent.Install,
            secondaryIntent = if (running) KiteResourceActionIntent.Stop else null
        )
        return ResourceFeatureUiState(
            phase = ResourceCatalogPhase.Ready,
            items = listOf(item)
        )
    }

    private fun attach(screen: ResourceDetailScreen) {
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
