package com.kite.app.feature.resources

import android.content.Context
import android.view.View
import com.kite.app.action.KiteInstallPlanActionIntent
import com.kite.app.application.resources.ResourceFeatureGateway
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/** 把 CardRun 显示面接到资源 Feature；不接管安装任务或 CardRun 生命周期。 */
internal class ResourceInstallWizardSurface(
    context: Context,
    private val gateway: ResourceFeatureGateway,
    val targetResourceId: String,
    val planResourceIds: List<String>,
    onPlanAction: (
        KiteInstallPlanActionIntent,
        (ResourceInstallWizardPlanActionResult) -> Unit,
    ) -> Unit,
    onOpenRun: (ResourceInstallWizardRunRequest) -> Unit,
    onUninstallFailedResource: (String) -> Unit,
    onContinueInBackground: () -> Unit,
    onCancelPlan: ((ResourceInstallWizardPlanActionResult) -> Unit) -> Unit,
    onLiveTickRequired: () -> Unit
) {
    private val controller = ResourceFeatureController(gateway)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var disposed = false
    private val screen = ResourceInstallWizardScreen(
        context = context,
        requestedTargetResourceId = targetResourceId,
        seedResourceIds = planResourceIds,
        onPlanAction = onPlanAction,
        onOpenRun = onOpenRun,
        onUninstallFailedResource = onUninstallFailedResource,
        onContinueInBackground = onContinueInBackground,
        onCancelPlan = onCancelPlan,
        onRetry = { refresh(forceCatalogRefresh = true) },
        onLiveTickRequired = onLiveTickRequired
    )

    val root: View get() = screen.root

    init {
        scope.launch {
            launch { controller.state.collect(screen::render) }
            launch {
                gateway.changes.collect { change ->
                    controller.dispatch(
                        if (change.catalogInvalidated) {
                            ResourceFeatureAction.Refresh(forceCatalogRefresh = false)
                        } else {
                            ResourceFeatureAction.ReconcileFacts
                        }
                    )
                }
            }
            controller.dispatch(ResourceFeatureAction.Refresh(forceCatalogRefresh = false))
        }
    }

    fun matches(targetId: String, resourceIds: List<String>): Boolean =
        targetResourceId == targetId && planResourceIds == resourceIds

    fun reconcile() {
        if (disposed) return
        scope.launch { controller.dispatch(ResourceFeatureAction.ReconcileFacts) }
    }

    fun tick(now: Long = System.currentTimeMillis()): Boolean =
        if (disposed) false else screen.tick(now)

    fun dispose() {
        if (disposed) return
        disposed = true
        screen.dispose()
        scope.cancel()
    }

    private fun refresh(forceCatalogRefresh: Boolean) {
        if (disposed) return
        scope.launch {
            controller.dispatch(ResourceFeatureAction.Refresh(forceCatalogRefresh))
        }
    }
}
