package com.kite.app.feature.resources

import android.app.Activity
import android.os.Looper
import android.view.ContextThemeWrapper
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
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
class ResourceSearchScreenTest {
    @Test
    fun `查询过滤与事实更新复用原结果按钮`() {
        val screen = createScreen(initialQuery = "toolx")
        attach(screen)
        val context = screen.root.context
        screen.render(state(installed = false))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))

        assertTrue(screen.root.textViews().any { it.text.toString() == "Tool" })
        assertFalse(screen.root.textViews().any { it.text.toString() == "Other" })
        val initialButton = screen.root.textViews().first {
            it.text.toString() == context.getString(R.string.resource_action_install)
        }

        screen.render(state(installed = true))
        shadowOf(Looper.getMainLooper()).idle()

        val reboundButton = screen.root.textViews().first {
            it.text.toString() == context.getString(R.string.resource_action_open)
        }
        assertSame(initialButton, reboundButton)
    }

    @Test
    fun `清空查询恢复完整结果且返回只发请求`() {
        var backCount = 0
        val screen = createScreen(initialQuery = "missing", onBack = { backCount += 1 })
        attach(screen)
        val context = screen.root.context
        screen.render(state(installed = false))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
        assertTrue(screen.root.textViews().any {
            it.text.toString() == context.getString(R.string.resource_search_empty)
        })

        screen.root.views().filterIsInstance<EditText>().single().setText("")
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
        assertTrue(screen.root.textViews().any { it.text.toString() == "Tool" })
        assertTrue(screen.root.textViews().any { it.text.toString() == "Other" })

        screen.root.views().first {
            it.contentDescription?.toString() == context.getString(R.string.common_back)
        }.performClick()
        assertEquals(1, backCount)
    }

    private fun createScreen(initialQuery: String, onBack: () -> Unit = {}): ResourceSearchScreen =
        ResourceSearchScreen(
            context = ContextThemeWrapper(
                ApplicationProvider.getApplicationContext(),
                R.style.Theme_Kite
            ),
            initialQuery = initialQuery,
            initialScrollY = 0,
            onBack = onBack,
            onOpenDetail = {},
            onPrimaryAction = {},
            onRetry = {}
        )

    private fun state(installed: Boolean): ResourceFeatureUiState = ResourceFeatureUiState(
        phase = ResourceCatalogPhase.Ready,
        items = listOf(
            item("toolx", "Tool", installed),
            item("other", "Other", installed = false)
        )
    )

    private fun item(id: String, name: String, installed: Boolean): ResourceItemUiState =
        ResourceItemUiState(
            descriptor = ResourceFeatureDescriptor(id, name),
            phase = if (installed) ResourceItemPhase.Installed else ResourceItemPhase.NotInstalled,
            projection = KiteResourceUiProjection(
                stateLabel = if (installed) "已获取" else "未获取",
                actionLabel = if (installed) "打开" else "获取",
                actionEnabled = true,
                secondaryActionLabel = null
            ),
            primaryIntent = if (installed) KiteResourceActionIntent.Open else KiteResourceActionIntent.Install,
            secondaryIntent = null
        )

    private fun attach(screen: ResourceSearchScreen) {
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
