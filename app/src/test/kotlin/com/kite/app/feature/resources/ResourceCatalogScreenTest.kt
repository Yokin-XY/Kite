package com.kite.app.feature.resources

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.view.ContextThemeWrapper
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.kite.app.action.KiteResourceActionIntent
import com.kite.app.R
import com.kite.app.application.resources.ResourceFeatureDescriptor
import com.kite.app.resources.KiteResourceHomeLayout
import com.kite.app.resources.KiteResourceHomeSection
import com.kite.app.resources.KiteResourceHomeTab
import com.kite.app.resources.KiteResourceUiProjection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import android.os.Looper
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
class ResourceCatalogScreenTest {
    @Test
    fun `目录结构稳定时事实更新只重绑原按钮`() {
        val clicked = mutableListOf<String>()
        val screen = ResourceCatalogScreen(
            context = themedContext(),
            initialTabId = RESOURCE_HOME_TAB_ALL,
            initialScrollY = 0,
            onSearch = {},
            onManage = {},
            onOpenDetail = {},
            onPrimaryAction = clicked::add,
            onRetry = {}
        )
        attach(screen)
        val context = screen.root.context
        screen.render(state(action = "获取", state = "未获取", installed = false))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))

        val initialButton = screen.root.textViews().first {
            it.text.toString() == context.getString(R.string.resource_action_install)
        }
        initialButton.performClick()
        assertEquals(listOf("tool"), clicked)

        screen.render(state(action = "打开", state = "已获取", installed = true))
        shadowOf(Looper.getMainLooper()).idle()

        val reboundButton = screen.root.textViews().first {
            it.text.toString() == context.getString(R.string.resource_action_open)
        }
        assertSame(initialButton, reboundButton)
        assertTrue(screen.root.textViews().any {
            it.text.toString().contains(context.getString(R.string.resource_state_installed))
        })
    }

    @Test
    fun `动作受理会立即显示承诺状态`() {
        val screen = ResourceCatalogScreen(
            context = themedContext(),
            initialTabId = RESOURCE_HOME_TAB_ALL,
            initialScrollY = 0,
            onSearch = {},
            onManage = {},
            onOpenDetail = {},
            onPrimaryAction = {},
            onRetry = {}
        )
        attach(screen)
        val context = screen.root.context
        screen.render(state(action = "获取", state = "未获取", installed = false))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))

        screen.acknowledge("tool", KiteResourceActionIntent.Install)

        assertTrue(screen.root.textViews().any {
            it.text.toString() == context.getString(R.string.resource_state_preparing) && !it.isEnabled
        })
    }

    @Test
    fun `运行计划显示查看进度而失败资源显示重试`() {
        val screen = ResourceCatalogScreen(
            context = themedContext(),
            initialTabId = RESOURCE_HOME_TAB_ALL,
            initialScrollY = 0,
            onSearch = {},
            onManage = {},
            onOpenDetail = {},
            onPrimaryAction = {},
            onRetry = {}
        )
        attach(screen)
        val context = screen.root.context

        screen.render(state(
            action = "获取中",
            state = "获取中",
            installed = false,
            phase = ResourceItemPhase.Installing,
            intent = KiteResourceActionIntent.ReopenInstall,
        ))
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(screen.root.textViews().any {
            it.text.toString() == context.getString(R.string.resource_action_view_progress)
        })

        screen.render(state(
            action = "重新获取",
            state = "获取失败",
            installed = false,
            phase = ResourceItemPhase.InstallFailed,
            intent = KiteResourceActionIntent.ReopenInstall,
        ))
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(screen.root.textViews().any {
            it.text.toString() == context.getString(R.string.resource_action_retry)
        })
    }

    private fun state(
        action: String,
        state: String,
        installed: Boolean,
        phase: ResourceItemPhase = if (installed) ResourceItemPhase.Installed else ResourceItemPhase.NotInstalled,
        intent: KiteResourceActionIntent = if (installed) KiteResourceActionIntent.Open else KiteResourceActionIntent.Install,
    ): ResourceFeatureUiState {
        val item = ResourceItemUiState(
            descriptor = ResourceFeatureDescriptor("tool", "Tool"),
            phase = phase,
            projection = KiteResourceUiProjection(state, action, actionEnabled = true, secondaryActionLabel = null),
            primaryIntent = intent,
            secondaryIntent = null
        )
        return ResourceFeatureUiState(
            phase = ResourceCatalogPhase.Ready,
            items = listOf(item),
            homeLayout = KiteResourceHomeLayout(
                sections = listOf(KiteResourceHomeSection("main", "资源", "list", listOf("tool"))),
                hero = null,
                tabs = listOf(KiteResourceHomeTab(RESOURCE_HOME_TAB_ALL, "全部", emptyList())),
                chips = emptyList(),
                rawJson = org.json.JSONObject()
            )
        )
    }

    private fun View.textViews(): List<TextView> = buildList {
        if (this@textViews is TextView) add(this@textViews)
        if (this@textViews is ViewGroup) {
            for (index in 0 until childCount) addAll(getChildAt(index).textViews())
        }
    }

    private fun themedContext(): ContextThemeWrapper = ContextThemeWrapper(
        ApplicationProvider.getApplicationContext(),
        R.style.Theme_Kite
    )

    private fun attach(screen: ResourceCatalogScreen) {
        Robolectric.buildActivity(Activity::class.java)
            .setup()
            .get()
            .setContentView(screen.root)
    }
}
