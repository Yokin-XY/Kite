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
import com.kite.app.resources.KiteResourceHomeLayout
import com.kite.app.resources.KiteResourceHomeSection
import com.kite.app.resources.KiteResourceHomeTab
import com.kite.app.resources.KiteResourceUiProjection
import java.time.Duration
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en")
class ResourceFeatureLocalizationTest {
    @Test
    fun `resource catalog uses English chrome and semantic action labels`() {
        val context = ContextThemeWrapper(
            ApplicationProvider.getApplicationContext(),
            R.style.Theme_Kite
        )
        val screen = ResourceCatalogScreen(
            context = context,
            initialTabId = RESOURCE_HOME_TAB_ALL,
            initialScrollY = 0,
            onSearch = {},
            onManage = {},
            onOpenDetail = {},
            onPrimaryAction = {},
            onRetry = {}
        )
        Robolectric.buildActivity(Activity::class.java).setup().get().setContentView(screen.root)
        screen.render(ResourceFeatureUiState(
            phase = ResourceCatalogPhase.Ready,
            items = listOf(ResourceItemUiState(
                descriptor = ResourceFeatureDescriptor("tool", "Tool"),
                phase = ResourceItemPhase.NotInstalled,
                projection = KiteResourceUiProjection("未获取", "获取", true, null),
                primaryIntent = KiteResourceActionIntent.Install,
                secondaryIntent = null
            )),
            homeLayout = KiteResourceHomeLayout(
                sections = listOf(KiteResourceHomeSection("foundation", "基础环境", "list", listOf("tool"))),
                hero = null,
                tabs = listOf(KiteResourceHomeTab(RESOURCE_HOME_TAB_ALL, "全部", emptyList())),
                chips = emptyList(),
                rawJson = org.json.JSONObject()
            )
        ))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))

        val texts = screen.root.textViews().map { it.text.toString() }
        assertTrue("Resources ›" in texts)
        assertTrue("Search" in texts)
        assertTrue("Install" in texts)
        assertTrue(texts.any { it.contains("Not installed") })
        assertTrue("All" in texts)
        assertTrue("Foundation" in texts)
    }

    private fun View.textViews(): List<TextView> = buildList {
        if (this@textViews is TextView) add(this@textViews)
        if (this@textViews is ViewGroup) {
            for (index in 0 until childCount) addAll(getChildAt(index).textViews())
        }
    }
}
