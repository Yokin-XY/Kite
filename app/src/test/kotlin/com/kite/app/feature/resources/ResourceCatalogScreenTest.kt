package com.kite.app.feature.resources

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.view.ContextThemeWrapper
import android.widget.TextView
import android.widget.HorizontalScrollView
import androidx.test.core.app.ApplicationProvider
import com.google.android.material.tabs.TabLayout
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
    fun `推荐保留两行队列并继续显示后续列表`() {
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
        val items = (1..10).map { index ->
            ResourceItemUiState(
                descriptor = ResourceFeatureDescriptor("tool-$index", "Tool $index"),
                phase = ResourceItemPhase.NotInstalled,
                projection = KiteResourceUiProjection("未获取", "获取", true, null),
                primaryIntent = KiteResourceActionIntent.Install,
                secondaryIntent = null
            )
        }
        screen.render(ResourceFeatureUiState(
            phase = ResourceCatalogPhase.Ready,
            items = items,
            homeLayout = KiteResourceHomeLayout(
                sections = emptyList(),
                hero = null,
                tabs = listOf(KiteResourceHomeTab(
                    "recommended",
                    "推荐",
                    listOf(
                        KiteResourceHomeSection(
                            "recommended-primary",
                            "推荐",
                            "shelf",
                            items.take(4).map { it.resourceId }
                        ),
                        KiteResourceHomeSection(
                            "recommended-secondary",
                            "推荐",
                            "shelf",
                            items.drop(4).take(4).map { it.resourceId }
                        ),
                        KiteResourceHomeSection(
                            "foundation",
                            "基础环境",
                            "list",
                            items.takeLast(2).map { it.resourceId }
                        )
                    )
                )),
                chips = emptyList(),
                rawJson = org.json.JSONObject()
            )
        ))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))

        assertEquals("recommended", screen.selectedTabId())
        assertEquals(10, screen.root.textViews().count {
            it.text.toString() == screen.root.context.getString(R.string.resource_action_install)
        })
        assertEquals(2, screen.root.views().count { it is HorizontalScrollView && it !is TabLayout })
        assertEquals(1, screen.root.textViews().count {
            it.text.toString() == screen.root.context.getString(R.string.resource_section_foundation)
        })
    }

    @Test
    fun `单列分类不重复显示与标签相同的大标题`() {
        val screen = ResourceCatalogScreen(
            context = themedContext(),
            initialTabId = "angel-cli",
            initialScrollY = 0,
            onSearch = {},
            onManage = {},
            onOpenDetail = {},
            onPrimaryAction = {},
            onRetry = {}
        )
        attach(screen)
        val item = ResourceItemUiState(
            descriptor = ResourceFeatureDescriptor("tool", "Tool"),
            phase = ResourceItemPhase.NotInstalled,
            projection = KiteResourceUiProjection("未获取", "获取", true, null),
            primaryIntent = KiteResourceActionIntent.Install,
            secondaryIntent = null
        )
        screen.render(ResourceFeatureUiState(
            phase = ResourceCatalogPhase.Ready,
            items = listOf(item),
            homeLayout = KiteResourceHomeLayout(
                sections = emptyList(),
                hero = null,
                tabs = listOf(KiteResourceHomeTab(
                    "angel-cli",
                    "Agent",
                    listOf(KiteResourceHomeSection("agent", "Agent", "plain-list", listOf("tool")))
                )),
                chips = emptyList(),
                rawJson = org.json.JSONObject()
            )
        ))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))

        assertEquals(1, screen.root.textViews().count { it.text.toString() == "Agent" })
    }

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

    private fun View.views(): List<View> = buildList {
        add(this@views)
        if (this@views is ViewGroup) {
            for (index in 0 until childCount) addAll(getChildAt(index).views())
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
