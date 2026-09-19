package com.kite.app.feature.resources

import android.content.Context
import android.view.View
import com.kite.app.action.KiteInstallPlanActionIntent
import com.kite.app.application.resources.ResourceFeatureGateway
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.transformLatest
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
    onUninstallFailedResource: (String) -> Unit,
    onOpenRun: (ResourceInstallWizardRunRequest) -> Unit,
    onExit: () -> Unit,
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
        onUninstallFailedResource = onUninstallFailedResource,
        onOpenRun = onOpenRun,
        onExit = onExit,
        onRetry = { refresh(forceCatalogRefresh = true) },
        onLiveTickRequired = onLiveTickRequired
    )

    val root: View get() = screen.root

    init {
        scope.launch {
            launch { controller.state.collect(screen::render) }
            launch {
                // 安装高峰（如 pip 流式输出）changes 发射密集：目录失效立即处理，
                // 对账类 ReconcileFacts 合并采样（250ms 静默窗口）——高频重发只落在
                // 一次 dispatch，避免主线程被全量 render 循环占满导致 ANR。
                gateway.changes.transformLatest { change ->
                    if (change.catalogInvalidated) {
                        emit(change)
                    } else {
                        delay(RECONCILE_CHANGES_SAMPLE_MS)
                        emit(change)
                    }
                }.collect { change ->
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

    private companion object {
        const val RECONCILE_CHANGES_SAMPLE_MS = 250L
    }
}
