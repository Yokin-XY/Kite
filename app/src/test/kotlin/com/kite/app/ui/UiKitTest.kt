package com.kite.app.ui

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.TextView
import com.kite.app.theme.KiteTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class UiKitTest {
    @Test
    fun `标准顶栏和文字角色来自同一主题环境`() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val environment = KiteTheme.resolve(KiteTheme.defaultSelection, systemDark = false)
        val ui = UiKit(activity, environment)
        val topBar = ui.topBar(activity, "运行管理", onBack = {}) as ViewGroup
        val title = topBar.getChildAt(1) as TextView

        assertEquals(environment.foundations.typography.pageTitle, title.textSize / activity.resources.displayMetrics.scaledDensity)
        assertEquals(
            environment.foundations.minimumTouchTarget.toFloat(),
            ui.dp(environment.foundations.minimumTouchTarget) / activity.resources.displayMetrics.density,
            0.01f,
        )
        assertTrue(topBar.getChildAt(0).contentDescription.isNotBlank())
    }

    @Test
    fun `辅助文字角色不依赖文字内容猜测样式`() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val environment = KiteTheme.resolve(KiteTheme.defaultSelection, systemDark = false)
        val ui = UiKit(activity, environment)
        val first = ui.applyTextRole(TextView(activity).apply { text = "保存" }, UiTextRole.Supporting)
        val second = ui.applyTextRole(TextView(activity).apply { text = "+" }, UiTextRole.Supporting)

        assertEquals(first.textSize, second.textSize, 0f)
        assertEquals(first.currentTextColor, second.currentTextColor)
    }

    @Test
    fun `标准确认弹层使用主题容器和危险动作而不是系统 AlertDialog`() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val environment = KiteTheme.resolve(KiteTheme.defaultSelection, systemDark = false)
        val ui = UiKit(activity, environment)
        var confirmed = false

        val dialog = ui.showConfirmDialog(
            context = activity,
            title = "关闭当前实例？",
            message = "将关闭这个实例产生的窗口和运行。",
            dismissLabel = "取消",
            primaryAction = UiDialogAction("关闭", UiActionRole.Danger) { confirmed = true },
        )
        val content = dialog.findViewById<View>(android.R.id.content)

        assertFalse(dialog is android.app.AlertDialog)
        assertNotNull(content.findByText("关闭当前实例？"))
        assertEquals(environment.tokens.danger, (content.findByText("关闭") as TextView).currentTextColor)
        content.findByText("关闭")!!.performClick()
        assertTrue(confirmed)
        assertFalse(dialog.isShowing)
    }

    @Test
    fun `标准单选弹层标记当前选项并在选择后回调`() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val ui = UiKit(activity, KiteTheme.resolve(KiteTheme.defaultSelection, systemDark = false))
        var selected = -1
        val dialog = ui.showChoiceDialog(
            context = activity,
            title = "终端主题",
            options = listOf("跟随系统", "深色", "浅色"),
            selectedIndex = 1,
            dismissLabel = "关闭",
        ) { selected = it }
        val content = dialog.findViewById<View>(android.R.id.content)

        assertTrue(content.findByText("深色")!!.isSelected)
        content.findByText("浅色")!!.performClick()
        assertEquals(2, selected)
        assertFalse(dialog.isShowing)
    }

    @Test
    fun `标准锚点菜单使用主题选中态并在点击后关闭`() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val ui = UiKit(activity, KiteTheme.resolve(KiteTheme.defaultSelection, systemDark = false))
        val anchor = TextView(activity).apply { text = "排列方式" }
        activity.setContentView(FrameLayout(activity).apply { addView(anchor) })
        var selected = ""

        val popup = ui.showAnchoredMenu(
            context = activity,
            anchor = anchor,
            items = listOf(
                UiMenuItem("默认顺序", selected = true, checkable = true) { selected = "default" },
                UiMenuItem("按名称", checkable = true) { selected = "name" },
            ),
        )

        assertTrue(popup.contentView.findByDescription("默认顺序")!!.isSelected)
        popup.contentView.findByDescription("按名称")!!.performClick()
        assertEquals("name", selected)
        assertFalse(popup.isShowing)
    }

    @Test
    fun `标准输入弹层允许异步校验后再关闭`() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val ui = UiKit(activity, KiteTheme.resolve(KiteTheme.defaultSelection, systemDark = false))
        var submitted = ""
        val handle = ui.showTextInputDialog(
            context = activity,
            title = "新建卡片分组",
            hint = "例如：AI 工具",
            dismissLabel = "取消",
            confirmLabel = "创建",
        ) { value, dialogHandle ->
            submitted = value
            if (value.isBlank()) dialogHandle.showError("请输入分组名称") else dialogHandle.dismiss()
        }
        val content = handle.dialog.findViewById<View>(android.R.id.content)
        val input = content.findFirstEditText()!!

        content.findByText("创建")!!.performClick()
        assertEquals("请输入分组名称", input.error?.toString())
        assertTrue(handle.dialog.isShowing)

        input.setText("AI 工具")
        content.findByText("创建")!!.performClick()
        assertEquals("AI 工具", submitted)
        assertFalse(handle.dialog.isShowing)
    }

    private fun View.findByText(value: String): View? {
        if (this is TextView && text?.toString() == value) return this
        if (this !is ViewGroup) return null
        repeat(childCount) { index -> getChildAt(index).findByText(value)?.let { return it } }
        return null
    }

    private fun View.findFirstEditText(): EditText? {
        if (this is EditText) return this
        if (this !is ViewGroup) return null
        repeat(childCount) { index -> getChildAt(index).findFirstEditText()?.let { return it } }
        return null
    }

    private fun View.findByDescription(value: String): View? {
        if (contentDescription?.toString() == value) return this
        if (this !is ViewGroup) return null
        repeat(childCount) { index -> getChildAt(index).findByDescription(value)?.let { return it } }
        return null
    }
}
