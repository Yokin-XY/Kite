package com.kite.app

internal enum class DestinationKind {
    Root,
    Child,
    Editor,
    RunSurface
}

internal sealed interface BackPolicy {
    data object System : BackPolicy
    data class Parent(val screen: MainActivity.Screen) : BackPolicy
    data class Contextual(val fallback: MainActivity.Screen) : BackPolicy
}

internal sealed interface RestorePolicy {
    data object None : RestorePolicy
    data object Direct : RestorePolicy
    data class AsParent(val screen: MainActivity.Screen) : RestorePolicy
    data object RecipeDraft : RestorePolicy
    data object WorkbenchUrl : RestorePolicy
}

internal data class Destination(
    val screen: MainActivity.Screen,
    val kind: DestinationKind,
    val backPolicy: BackPolicy,
    val restorePolicy: RestorePolicy = RestorePolicy.None
)

internal sealed interface NavigationBackAction {
    data object System : NavigationBackAction
    data object CardRunTask : NavigationBackAction
    data object Contextual : NavigationBackAction
    data class Navigate(val screen: MainActivity.Screen) : NavigationBackAction
}

/**
 * Kite 的导航合同中心。
 *
 * 这里仅拥有目标页、返回策略和恢复策略，不拥有页面渲染、业务状态或运行实例。
 * 过渡期仍由 MainActivity 的老 show* 方法完成渲染，但所有返回判断可以先依赖同一份合同。
 */
internal class ScreenRouter(
    private val legacySink: LegacyScreenSink,
    initialScreen: MainActivity.Screen = MainActivity.Screen.Console
) {
    private var contextualBackAction: (() -> Unit)? = null

    var currentScreen: MainActivity.Screen = initialScreen
        private set

    fun enter(screen: MainActivity.Screen, onBack: (() -> Unit)? = null) {
        currentScreen = screen
        contextualBackAction = onBack
    }

    fun navigate(screen: MainActivity.Screen) {
        legacySink.navigateToLegacy(screen)
    }

    fun destination(screen: MainActivity.Screen = currentScreen): Destination =
        destinations.getValue(screen)

    fun resolveBack(isCardRunTask: Boolean): NavigationBackAction {
        if (isCardRunTask) return NavigationBackAction.CardRunTask
        return when (val policy = destination().backPolicy) {
            BackPolicy.System -> NavigationBackAction.System
            is BackPolicy.Parent -> NavigationBackAction.Navigate(policy.screen)
            is BackPolicy.Contextual -> if (contextualBackAction != null) {
                NavigationBackAction.Contextual
            } else {
                NavigationBackAction.Navigate(policy.fallback)
            }
        }
    }

    fun invokeContextualBack(): Boolean {
        val action = contextualBackAction ?: return false
        action()
        return true
    }

    fun interface LegacyScreenSink {
        fun navigateToLegacy(screen: MainActivity.Screen)
    }

    companion object {
        private val destinations: Map<MainActivity.Screen, Destination> = listOf(
            Destination(
                MainActivity.Screen.Console,
                DestinationKind.Root,
                BackPolicy.System
            ),
            Destination(
                MainActivity.Screen.Terminal,
                DestinationKind.Root,
                BackPolicy.Parent(MainActivity.Screen.Console),
                RestorePolicy.Direct
            ),
            Destination(
                MainActivity.Screen.Workbench,
                DestinationKind.Child,
                BackPolicy.Parent(MainActivity.Screen.Console),
                RestorePolicy.WorkbenchUrl
            ),
            Destination(
                MainActivity.Screen.CardRun,
                DestinationKind.RunSurface,
                BackPolicy.Parent(MainActivity.Screen.Console)
            ),
            Destination(
                MainActivity.Screen.RecipeDetail,
                DestinationKind.Child,
                BackPolicy.Contextual(MainActivity.Screen.Console)
            ),
            Destination(
                MainActivity.Screen.CreateConfig,
                DestinationKind.Editor,
                BackPolicy.Contextual(MainActivity.Screen.Console),
                RestorePolicy.RecipeDraft
            ),
            Destination(
                MainActivity.Screen.RecipeMore,
                DestinationKind.Child,
                BackPolicy.Contextual(MainActivity.Screen.Console)
            ),
            Destination(
                MainActivity.Screen.Resources,
                DestinationKind.Root,
                BackPolicy.Parent(MainActivity.Screen.Console),
                RestorePolicy.Direct
            ),
            Destination(
                MainActivity.Screen.ResourceSearch,
                DestinationKind.Child,
                BackPolicy.Parent(MainActivity.Screen.Resources),
                RestorePolicy.AsParent(MainActivity.Screen.Resources)
            ),
            Destination(
                MainActivity.Screen.ResourceManage,
                DestinationKind.Child,
                BackPolicy.Parent(MainActivity.Screen.Resources),
                RestorePolicy.Direct
            ),
            Destination(
                MainActivity.Screen.ResourceDetail,
                DestinationKind.Child,
                BackPolicy.Parent(MainActivity.Screen.Resources)
            ),
            Destination(
                MainActivity.Screen.ResourceMore,
                DestinationKind.Child,
                BackPolicy.Contextual(MainActivity.Screen.Resources),
                RestorePolicy.AsParent(MainActivity.Screen.Resources)
            ),
            Destination(
                MainActivity.Screen.ResourceRawJson,
                DestinationKind.Child,
                BackPolicy.Contextual(MainActivity.Screen.Resources)
            ),
            Destination(
                MainActivity.Screen.Processes,
                DestinationKind.Child,
                BackPolicy.Parent(MainActivity.Screen.Console)
            ),
            Destination(
                MainActivity.Screen.Settings,
                DestinationKind.Root,
                BackPolicy.Parent(MainActivity.Screen.Console),
                RestorePolicy.Direct
            ),
            Destination(
                MainActivity.Screen.ThemeSettings,
                DestinationKind.Child,
                BackPolicy.Parent(MainActivity.Screen.Settings),
                RestorePolicy.Direct
            )
        ).associateBy(Destination::screen)
    }
}
