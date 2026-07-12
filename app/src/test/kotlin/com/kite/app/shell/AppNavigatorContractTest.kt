package com.kite.app.shell

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppNavigatorContractTest {
    @Test
    fun `每个目标页都必须有且只有一份导航合同`() {
        val navigator = navigator()

        val contracts = AppDestination.entries.map(navigator::contract)

        assertEquals(AppDestination.entries.size, contracts.size)
        assertEquals(AppDestination.entries.toSet(), contracts.map { it.destination }.toSet())
    }

    @Test
    fun `普通父子页面返回目标必须确定`() {
        val navigator = navigator()
        val expected = mapOf(
            AppDestination.Terminal to AppDestination.Console,
            AppDestination.Workbench to AppDestination.Console,
            AppDestination.Resources to AppDestination.Console,
            AppDestination.ResourceSearch to AppDestination.Resources,
            AppDestination.ResourceManage to AppDestination.Resources,
            AppDestination.ResourceDetail to AppDestination.Resources,
            AppDestination.Processes to AppDestination.Console,
            AppDestination.Settings to AppDestination.Console,
            AppDestination.ThemeSettings to AppDestination.Settings
        )

        expected.forEach { (destination, parent) ->
            navigator.enter(destination)
            assertEquals(NavigationBackAction.Navigate(parent), navigator.resolveBack(isCardRunTask = false))
        }
    }

    @Test
    fun `上下文页面优先执行登记的返回动作并在离开后清除`() {
        val navigator = navigator()
        var invoked = false
        navigator.enter(AppDestination.ResourceMore) { invoked = true }

        assertEquals(NavigationBackAction.Contextual, navigator.resolveBack(isCardRunTask = false))
        assertTrue(navigator.invokeContextualBack())
        assertTrue(invoked)

        navigator.enter(AppDestination.Settings)
        assertFalse(navigator.invokeContextualBack())
        assertEquals(
            NavigationBackAction.Navigate(AppDestination.Console),
            navigator.resolveBack(isCardRunTask = false)
        )
    }

    @Test
    fun `上下文缺失时必须回到安全父页面`() {
        val navigator = navigator()
        val expectedFallbacks = mapOf(
            AppDestination.CreateConfig to AppDestination.Console,
            AppDestination.RecipeDetail to AppDestination.Console,
            AppDestination.RecipeMore to AppDestination.Console,
            AppDestination.ResourceMore to AppDestination.Resources,
            AppDestination.ResourceRawJson to AppDestination.Resources
        )

        expectedFallbacks.forEach { (destination, fallback) ->
            navigator.enter(destination)
            assertEquals(
                NavigationBackAction.Navigate(fallback),
                navigator.resolveBack(isCardRunTask = false)
            )
        }
    }

    @Test
    fun `主根页交给系统而 CardRun 任务交给运行窗口合同`() {
        val navigator = navigator()
        navigator.enter(AppDestination.Console)
        assertEquals(NavigationBackAction.System, navigator.resolveBack(isCardRunTask = false))

        navigator.enter(AppDestination.CardRun)
        assertEquals(NavigationBackAction.CardRunTask, navigator.resolveBack(isCardRunTask = true))
        assertEquals(
            NavigationBackAction.Navigate(AppDestination.Console),
            navigator.resolveBack(isCardRunTask = false)
        )
    }

    @Test
    fun `恢复策略必须保持现有白名单边界`() {
        val navigator = navigator()

        assertEquals(RestorePolicy.Direct, navigator.contract(AppDestination.Terminal).restorePolicy)
        assertEquals(RestorePolicy.Direct, navigator.contract(AppDestination.Settings).restorePolicy)
        assertEquals(RestorePolicy.Direct, navigator.contract(AppDestination.ThemeSettings).restorePolicy)
        assertEquals(RestorePolicy.Direct, navigator.contract(AppDestination.Resources).restorePolicy)
        assertEquals(RestorePolicy.Direct, navigator.contract(AppDestination.ResourceManage).restorePolicy)
        assertEquals(
            RestorePolicy.AsParent(AppDestination.Resources),
            navigator.contract(AppDestination.ResourceSearch).restorePolicy
        )
        assertEquals(RestorePolicy.RecipeDraft, navigator.contract(AppDestination.CreateConfig).restorePolicy)
        assertEquals(RestorePolicy.WorkbenchUrl, navigator.contract(AppDestination.Workbench).restorePolicy)
        assertEquals(RestorePolicy.None, navigator.contract(AppDestination.ResourceDetail).restorePolicy)
    }

    @Test
    fun `统一导航入口仍委托当前渲染路径`() {
        val navigated = mutableListOf<AppDestination>()
        val navigator = AppNavigator(AppNavigator.DestinationSink(navigated::add))

        navigator.navigate(AppDestination.Settings)
        navigator.navigate(AppDestination.Resources)

        assertEquals(listOf(AppDestination.Settings, AppDestination.Resources), navigated)
    }

    private fun navigator(): AppNavigator = AppNavigator(AppNavigator.DestinationSink { })
}
