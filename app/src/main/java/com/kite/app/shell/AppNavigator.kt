package com.kite.app.shell

internal enum class AppDestination {
    Console,
    Terminal,
    Workbench,
    CardRun,
    RecipeDetail,
    CreateConfig,
    RecipeMore,
    Resources,
    ResourceSearch,
    ResourceManage,
    ResourceDetail,
    ResourceMore,
    ResourceRawJson,
    Processes,
    Settings,
    ThemeSettings
}

internal enum class DestinationKind {
    Root,
    Child,
    Editor,
    RunSurface
}

internal sealed interface BackPolicy {
    data object System : BackPolicy
    data class Parent(val destination: AppDestination) : BackPolicy
    data class Contextual(val fallback: AppDestination) : BackPolicy
}

internal sealed interface RestorePolicy {
    data object None : RestorePolicy
    data object Direct : RestorePolicy
    data class AsParent(val destination: AppDestination) : RestorePolicy
    data object RecipeDraft : RestorePolicy
    data object WorkbenchUrl : RestorePolicy
}

internal data class DestinationContract(
    val destination: AppDestination,
    val kind: DestinationKind,
    val backPolicy: BackPolicy,
    val restorePolicy: RestorePolicy = RestorePolicy.None
)

internal sealed interface NavigationBackAction {
    data object System : NavigationBackAction
    data object CardRunTask : NavigationBackAction
    data object Contextual : NavigationBackAction
    data class Navigate(val destination: AppDestination) : NavigationBackAction
}

/**
 * 应用壳的导航合同中心，只拥有目标、返回和恢复策略。
 */
internal class AppNavigator(
    private val destinationSink: DestinationSink,
    initialDestination: AppDestination = AppDestination.Console
) {
    private var contextualBackAction: (() -> Unit)? = null

    var currentDestination: AppDestination = initialDestination
        private set

    fun enter(destination: AppDestination, onBack: (() -> Unit)? = null) {
        currentDestination = destination
        contextualBackAction = onBack
    }

    fun navigate(destination: AppDestination) {
        destinationSink.navigate(destination)
    }

    fun contract(destination: AppDestination = currentDestination): DestinationContract =
        contracts.getValue(destination)

    fun resolveBack(isCardRunTask: Boolean): NavigationBackAction {
        if (isCardRunTask) return NavigationBackAction.CardRunTask
        return when (val policy = contract().backPolicy) {
            BackPolicy.System -> NavigationBackAction.System
            is BackPolicy.Parent -> NavigationBackAction.Navigate(policy.destination)
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

    fun interface DestinationSink {
        fun navigate(destination: AppDestination)
    }

    companion object {
        private val contracts: Map<AppDestination, DestinationContract> = listOf(
            DestinationContract(
                AppDestination.Console,
                DestinationKind.Root,
                BackPolicy.System
            ),
            DestinationContract(
                AppDestination.Terminal,
                DestinationKind.Root,
                BackPolicy.Parent(AppDestination.Console),
                RestorePolicy.Direct
            ),
            DestinationContract(
                AppDestination.Workbench,
                DestinationKind.Child,
                BackPolicy.Parent(AppDestination.Console),
                RestorePolicy.WorkbenchUrl
            ),
            DestinationContract(
                AppDestination.CardRun,
                DestinationKind.RunSurface,
                BackPolicy.Parent(AppDestination.Console)
            ),
            DestinationContract(
                AppDestination.RecipeDetail,
                DestinationKind.Child,
                BackPolicy.Contextual(AppDestination.Console)
            ),
            DestinationContract(
                AppDestination.CreateConfig,
                DestinationKind.Editor,
                BackPolicy.Contextual(AppDestination.Console),
                RestorePolicy.RecipeDraft
            ),
            DestinationContract(
                AppDestination.RecipeMore,
                DestinationKind.Child,
                BackPolicy.Contextual(AppDestination.Console)
            ),
            DestinationContract(
                AppDestination.Resources,
                DestinationKind.Root,
                BackPolicy.Parent(AppDestination.Console),
                RestorePolicy.Direct
            ),
            DestinationContract(
                AppDestination.ResourceSearch,
                DestinationKind.Child,
                BackPolicy.Parent(AppDestination.Resources),
                RestorePolicy.AsParent(AppDestination.Resources)
            ),
            DestinationContract(
                AppDestination.ResourceManage,
                DestinationKind.Child,
                BackPolicy.Parent(AppDestination.Resources),
                RestorePolicy.Direct
            ),
            DestinationContract(
                AppDestination.ResourceDetail,
                DestinationKind.Child,
                BackPolicy.Contextual(AppDestination.Resources)
            ),
            DestinationContract(
                AppDestination.ResourceMore,
                DestinationKind.Child,
                BackPolicy.Contextual(AppDestination.Resources),
                RestorePolicy.AsParent(AppDestination.Resources)
            ),
            DestinationContract(
                AppDestination.ResourceRawJson,
                DestinationKind.Child,
                BackPolicy.Contextual(AppDestination.Resources)
            ),
            DestinationContract(
                AppDestination.Processes,
                DestinationKind.Child,
                BackPolicy.Parent(AppDestination.Console)
            ),
            DestinationContract(
                AppDestination.Settings,
                DestinationKind.Root,
                BackPolicy.Parent(AppDestination.Console),
                RestorePolicy.Direct
            ),
            DestinationContract(
                AppDestination.ThemeSettings,
                DestinationKind.Child,
                BackPolicy.Parent(AppDestination.Settings),
                RestorePolicy.Direct
            )
        ).associateBy(DestinationContract::destination)
    }
}
