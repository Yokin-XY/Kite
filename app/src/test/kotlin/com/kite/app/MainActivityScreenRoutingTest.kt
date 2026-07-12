package com.kite.app

import android.os.Bundle
import android.os.Looper
import android.view.ViewGroup
import android.webkit.WebView
import com.kite.app.feature.resources.ResourceFeatureRequest
import com.kite.app.feature.resources.ResourceFeatureResultContract
import com.kite.app.resources.KiteResourceInstallSignal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * T6.0:P2 前置安全网 —— 锁死 MainActivity 的 Screen 路由契约。
 *
 * 这套测试是拆分应用壳、AppNavigator 和 Feature 页面时的行为防线:
 * 任何路由行为变化(navigate 目标 Screen、back 回退映射、默认 Screen、state 恢复)
 * 都会被立刻发现,防止重构改坏用户可见的页面流转。
 *
 * 策略:
 * - 用字符串断言 currentScreenNameForTest(),不依赖 private 的 Screen 枚举可见性。
 * - show* 是 private,用反射调用(测试代码允许探测内部;避免改 19591 行的可见性)。
 * - 只测不触发真实 runtime gate 的只读展示型 Screen(Settings/ThemeSettings/Console)。
 *
 * 覆盖维度:默认 Screen / navigate / back 回退映射 / state 恢复白名单。
 */
@RunWith(RobolectricTestRunner::class)
class MainActivityScreenRoutingTest {

    private class BackTrackingWebView(activity: MainActivity) : WebView(activity) {
        var goBackCalls: Int = 0

        override fun canGoBack(): Boolean = true

        override fun goBack() {
            goBackCalls += 1
        }
    }

    private fun createActivity(savedInstanceState: Bundle? = null): MainActivity =
        Robolectric.buildActivity(MainActivity::class.java)
            .create(savedInstanceState)
            .get()

    private fun createResumedActivity(): MainActivity =
        Robolectric.buildActivity(MainActivity::class.java)
            .create()
            .start()
            .resume()
            .get()

    /** 反射调用 private 无参 show* 方法。 */
    private fun invokeShow(activity: MainActivity, methodName: String) {
        val method = MainActivity::class.java.getDeclaredMethod(methodName)
        method.isAccessible = true
        method.invoke(activity)
    }

    private fun invokeResourceSignal(activity: MainActivity, signal: KiteResourceInstallSignal) {
        val method = MainActivity::class.java.getDeclaredMethod(
            "consumeResourceInstallSignal",
            KiteResourceInstallSignal::class.java
        )
        method.isAccessible = true
        method.invoke(activity, signal)
    }

    private fun settleResourceMutation(activity: MainActivity, reason: String) {
        val method = MainActivity::class.java.getDeclaredMethod("settleVisibleResourceMutation", String::class.java)
        method.isAccessible = true
        method.invoke(activity, reason)
    }

    private fun setResourceCatalogDirty(activity: MainActivity, dirty: Boolean) {
        val field = MainActivity::class.java.getDeclaredField("resourceCatalogDirty")
        field.isAccessible = true
        field.setBoolean(activity, dirty)
    }

    private fun resourceCatalogDirty(activity: MainActivity): Boolean {
        val field = MainActivity::class.java.getDeclaredField("resourceCatalogDirty")
        field.isAccessible = true
        return field.getBoolean(activity)
    }

    private fun setCurrentScreen(activity: MainActivity, screenName: String) {
        activity.enterScreenForTest(screenName)
    }

    private fun replaceWebView(activity: MainActivity, webView: WebView) {
        val field = MainActivity::class.java.getDeclaredField("webView")
        field.isAccessible = true
        field.set(activity, webView)
        activity.addContentView(webView, ViewGroup.LayoutParams(1, 1))
    }

    // ------------------------------------------------------------------
    // 默认 Screen(冷启动契约)
    // ------------------------------------------------------------------

    @Test
    fun `冷启动后默认 Screen 为 Console`() {
        val activity = createActivity()
        assertEquals("Console", activity.currentScreenNameForTest())
    }

    // ------------------------------------------------------------------
    // navigate 契约:show* 方法切换 currentScreen
    // ------------------------------------------------------------------

    @Test
    fun `showSettings 后 currentScreen 为 Settings`() {
        val activity = createActivity()
        invokeShow(activity, "showSettings")
        assertEquals("Settings", activity.currentScreenNameForTest())
    }

    @Test
    fun `showThemeSettings 后 currentScreen 为 ThemeSettings`() {
        val activity = createActivity()
        invokeShow(activity, "showThemeSettings")
        assertEquals("ThemeSettings", activity.currentScreenNameForTest())
    }

    @Test
    fun `showConsole 总能回到 Console`() {
        val activity = createActivity()
        invokeShow(activity, "showSettings")
        invokeShow(activity, "showConsole")
        assertEquals("Console", activity.currentScreenNameForTest())
    }

    @Test
    fun `资源完成信号在非资源页面也必须标脏缓存`() {
        val activity = createActivity()
        setResourceCatalogDirty(activity, false)

        invokeResourceSignal(
            activity,
            KiteResourceInstallSignal(
                revision = 1L,
                reason = "markInstalled",
                resourceId = "kite.hermes.core"
            )
        )

        assertTrue(resourceCatalogDirty(activity))
        assertEquals("Console", activity.currentScreenNameForTest())
    }

    @Test
    fun `资源状态信号只标脏 Activity 缓存不再操作 Feature 控件`() {
        val screens = listOf("Resources", "ResourceSearch", "ResourceDetail", "ResourceMore", "ResourceManage")

        screens.forEachIndexed { index, screenName ->
            val activity = createActivity()
            setCurrentScreen(activity, screenName)
            setResourceCatalogDirty(activity, false)
            invokeResourceSignal(
                activity,
                KiteResourceInstallSignal(
                    revision = index + 1L,
                    reason = "state-transition-$screenName",
                    resourceId = "kite.hermes.core"
                )
            )

            assertTrue("$screenName must retain a dirty catalog until it can rebind", resourceCatalogDirty(activity))
            assertEquals(screenName, activity.currentScreenNameForTest())
        }
    }

    @Test
    fun `资源管理进入详情后返回资源管理`() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        invokeShow(activity, "showResourceManage")
        activity.supportFragmentManager.executePendingTransactions()
        val manageFragment = activity.supportFragmentManager
            .findFragmentByTag("kite-resource-manage")
            ?: error("资源管理 Fragment 未挂载")

        ResourceFeatureResultContract.send(
            manageFragment,
            ResourceFeatureRequest.OpenDetail("kite.codex.cli")
        )
        shadowOf(Looper.getMainLooper()).idle()
        activity.supportFragmentManager.executePendingTransactions()
        assertEquals("ResourceDetail", activity.currentScreenNameForTest())

        activity.onBackPressedDispatcher.onBackPressed()
        shadowOf(Looper.getMainLooper()).idle()
        activity.supportFragmentManager.executePendingTransactions()
        assertEquals("ResourceManage", activity.currentScreenNameForTest())
    }

    @Test
    fun `后台资源完成不得把非资源页面导航到资源首页`() {
        val activity = createActivity()
        invokeShow(activity, "showSettings")
        setResourceCatalogDirty(activity, false)

        settleResourceMutation(activity, "background_install_completed")

        assertEquals("Settings", activity.currentScreenNameForTest())
        assertTrue(resourceCatalogDirty(activity))
    }

    // ------------------------------------------------------------------
    // back 回退映射契约
    // onBackPressed 对 Settings/Console 的行为是稳定契约。
    // ------------------------------------------------------------------

    @Test
    fun `从 Settings 按 back 应回到 Console`() {
        val activity = createResumedActivity()
        invokeShow(activity, "showSettings")
        assertEquals("Settings", activity.currentScreenNameForTest())

        activity.onBackPressedDispatcher.onBackPressed()

        assertEquals("Console", activity.currentScreenNameForTest())
    }

    @Test
    fun `从 ThemeSettings 按 back 应回到 Settings`() {
        val activity = createResumedActivity()
        invokeShow(activity, "showThemeSettings")
        assertEquals("ThemeSettings", activity.currentScreenNameForTest())

        activity.onBackPressedDispatcher.onBackPressed()

        assertEquals("Settings", activity.currentScreenNameForTest())
    }

    @Test
    fun `从 ResourceSearch 按 back 应回到 Resources`() {
        val activity = createResumedActivity()
        setCurrentScreen(activity, "ResourceSearch")

        activity.onBackPressedDispatcher.onBackPressed()

        assertEquals("Resources", activity.currentScreenNameForTest())
    }

    @Test
    fun `从 ResourceDetail 按 back 应回到 Resources`() {
        val activity = createResumedActivity()
        setCurrentScreen(activity, "ResourceDetail")

        activity.onBackPressedDispatcher.onBackPressed()

        assertEquals("Resources", activity.currentScreenNameForTest())
    }

    @Test
    fun `从 Processes 按 back 应回到 Console`() {
        val activity = createResumedActivity()
        setCurrentScreen(activity, "Processes")

        activity.onBackPressedDispatcher.onBackPressed()

        assertEquals("Console", activity.currentScreenNameForTest())
    }

    @Test
    fun `Workbench 有网页历史时 back 应只回退网页`() {
        val activity = createResumedActivity()
        val webView = BackTrackingWebView(activity)
        replaceWebView(activity, webView)
        setCurrentScreen(activity, "Workbench")

        activity.onBackPressedDispatcher.onBackPressed()

        assertEquals(1, webView.goBackCalls)
        assertEquals("Workbench", activity.currentScreenNameForTest())
    }

    // ------------------------------------------------------------------
    // state 恢复白名单契约
    // restoreScreenFromBundle 只对特定 Screen 还原;Settings 在白名单内。
    // ------------------------------------------------------------------

    @Test
    fun `从 saved state 恢复 Settings Screen`() {
        val bundle = Bundle().apply { putString("kite_current_screen", "Settings") }

        val activity = createActivity(bundle)

        assertEquals("Settings", activity.currentScreenNameForTest())
    }

    @Test
    fun `ResourceSearch 恢复时应校准到 Resources`() {
        val bundle = Bundle().apply { putString("kite_current_screen", "ResourceSearch") }

        val activity = createActivity(bundle)

        assertEquals("Resources", activity.currentScreenNameForTest())
    }

    @Test
    fun `缺少资源上下文时不恢复 ResourceDetail`() {
        val bundle = Bundle().apply { putString("kite_current_screen", "ResourceDetail") }

        val activity = createActivity(bundle)

        assertEquals("Console", activity.currentScreenNameForTest())
    }

    @Test
    fun `Activity 销毁只释放显示面`() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).create().start().resume()
        val activity = controller.get()

        controller.pause().stop().destroy()

        assertTrue(activity.activityDisplaySurfacesReleasedForTest())
    }

    @Test
    fun `Console 回前台复用现有显示面`() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).create().start().resume()
        val activity = controller.get()
        activity.supportFragmentManager.executePendingTransactions()
        val beforeFragment = activity.supportFragmentManager.findFragmentByTag("kite-home")
        val beforeView = beforeFragment?.view

        controller.pause().resume()
        activity.supportFragmentManager.executePendingTransactions()

        val afterFragment = activity.supportFragmentManager.findFragmentByTag("kite-home")
        assertSame(beforeFragment, afterFragment)
        assertSame(beforeView, afterFragment?.view)
    }
}
