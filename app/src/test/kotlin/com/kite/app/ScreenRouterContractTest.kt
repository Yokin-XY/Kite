package com.kite.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenRouterContractTest {
    @Test
    fun `每个 Screen 都必须有且只有一份目标合同`() {
        val router = router()

        val destinations = MainActivity.Screen.entries.map(router::destination)

        assertEquals(MainActivity.Screen.entries.size, destinations.size)
        assertEquals(MainActivity.Screen.entries.toSet(), destinations.map { it.screen }.toSet())
    }

    @Test
    fun `普通父子页面返回目标必须确定`() {
        val router = router()
        val expected = mapOf(
            MainActivity.Screen.Terminal to MainActivity.Screen.Console,
            MainActivity.Screen.Workbench to MainActivity.Screen.Console,
            MainActivity.Screen.Resources to MainActivity.Screen.Console,
            MainActivity.Screen.ResourceSearch to MainActivity.Screen.Resources,
            MainActivity.Screen.ResourceManage to MainActivity.Screen.Resources,
            MainActivity.Screen.ResourceDetail to MainActivity.Screen.Resources,
            MainActivity.Screen.Processes to MainActivity.Screen.Console,
            MainActivity.Screen.Settings to MainActivity.Screen.Console,
            MainActivity.Screen.ThemeSettings to MainActivity.Screen.Settings
        )

        expected.forEach { (screen, parent) ->
            router.enter(screen)
            assertEquals(NavigationBackAction.Navigate(parent), router.resolveBack(isCardRunTask = false))
        }
    }

    @Test
    fun `上下文页面优先执行登记的返回动作并在离开后清除`() {
        val router = router()
        var invoked = false
        router.enter(MainActivity.Screen.ResourceMore) { invoked = true }

        assertEquals(NavigationBackAction.Contextual, router.resolveBack(isCardRunTask = false))
        assertTrue(router.invokeContextualBack())
        assertTrue(invoked)

        router.enter(MainActivity.Screen.Settings)
        assertFalse(router.invokeContextualBack())
        assertEquals(
            NavigationBackAction.Navigate(MainActivity.Screen.Console),
            router.resolveBack(isCardRunTask = false)
        )
    }

    @Test
    fun `上下文缺失时必须回到安全父页面`() {
        val router = router()

        val expectedFallbacks = mapOf(
            MainActivity.Screen.CreateConfig to MainActivity.Screen.Console,
            MainActivity.Screen.RecipeDetail to MainActivity.Screen.Console,
            MainActivity.Screen.RecipeMore to MainActivity.Screen.Console,
            MainActivity.Screen.ResourceMore to MainActivity.Screen.Resources,
            MainActivity.Screen.ResourceRawJson to MainActivity.Screen.Resources
        )

        expectedFallbacks.forEach { (screen, fallback) ->
            router.enter(screen)
            assertEquals(
                NavigationBackAction.Navigate(fallback),
                router.resolveBack(isCardRunTask = false)
            )
        }
    }

    @Test
    fun `主根页交给系统而 CardRun 任务交给运行窗口合同`() {
        val router = router()
        router.enter(MainActivity.Screen.Console)
        assertEquals(NavigationBackAction.System, router.resolveBack(isCardRunTask = false))

        router.enter(MainActivity.Screen.CardRun)
        assertEquals(NavigationBackAction.CardRunTask, router.resolveBack(isCardRunTask = true))
        assertEquals(
            NavigationBackAction.Navigate(MainActivity.Screen.Console),
            router.resolveBack(isCardRunTask = false)
        )
    }

    @Test
    fun `恢复策略必须保持现有白名单边界`() {
        val router = router()

        assertEquals(RestorePolicy.Direct, router.destination(MainActivity.Screen.Terminal).restorePolicy)
        assertEquals(RestorePolicy.Direct, router.destination(MainActivity.Screen.Settings).restorePolicy)
        assertEquals(RestorePolicy.Direct, router.destination(MainActivity.Screen.ThemeSettings).restorePolicy)
        assertEquals(RestorePolicy.Direct, router.destination(MainActivity.Screen.Resources).restorePolicy)
        assertEquals(RestorePolicy.Direct, router.destination(MainActivity.Screen.ResourceManage).restorePolicy)
        assertEquals(
            RestorePolicy.AsParent(MainActivity.Screen.Resources),
            router.destination(MainActivity.Screen.ResourceSearch).restorePolicy
        )
        assertEquals(RestorePolicy.RecipeDraft, router.destination(MainActivity.Screen.CreateConfig).restorePolicy)
        assertEquals(RestorePolicy.WorkbenchUrl, router.destination(MainActivity.Screen.Workbench).restorePolicy)
        assertEquals(RestorePolicy.None, router.destination(MainActivity.Screen.ResourceDetail).restorePolicy)
    }

    @Test
    fun `统一导航入口仍委托现有渲染路径`() {
        val navigated = mutableListOf<MainActivity.Screen>()
        val router = ScreenRouter(ScreenRouter.LegacyScreenSink(navigated::add))

        router.navigate(MainActivity.Screen.Settings)
        router.navigate(MainActivity.Screen.Resources)

        assertEquals(listOf(MainActivity.Screen.Settings, MainActivity.Screen.Resources), navigated)
    }

    private fun router(): ScreenRouter = ScreenRouter(ScreenRouter.LegacyScreenSink { })
}
