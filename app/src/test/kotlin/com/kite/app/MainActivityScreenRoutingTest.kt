package com.kite.app

import android.os.Bundle
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

/**
 * T6.0:P2 前置安全网 —— 锁死 MainActivity 的 Screen 路由契约。
 *
 * 这套测试是后续 T6-T9(拆 God Activity、引入 ScreenRouter、抽 Fragment)的防线:
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

    private fun createActivity(savedInstanceState: Bundle? = null): MainActivity =
        Robolectric.buildActivity(MainActivity::class.java)
            .create(savedInstanceState)
            .get()

    /** 反射调用 private 无参 show* 方法。 */
    private fun invokeShow(activity: MainActivity, methodName: String) {
        val method = MainActivity::class.java.getDeclaredMethod(methodName)
        method.isAccessible = true
        method.invoke(activity)
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

    // ------------------------------------------------------------------
    // back 回退映射契约
    // onBackPressed 对 Settings/Console 的行为是稳定契约。
    // ------------------------------------------------------------------

    @Test
    fun `从 Settings 按 back 应回到 Console`() {
        val activity = createActivity()
        invokeShow(activity, "showSettings")
        assertEquals("Settings", activity.currentScreenNameForTest())

        activity.onBackPressed()

        assertEquals("Console", activity.currentScreenNameForTest())
    }

    @Test
    fun `从 ThemeSettings 按 back 应回到 Settings`() {
        val activity = createActivity()
        invokeShow(activity, "showThemeSettings")
        assertEquals("ThemeSettings", activity.currentScreenNameForTest())

        activity.onBackPressed()

        assertEquals("Settings", activity.currentScreenNameForTest())
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
}
