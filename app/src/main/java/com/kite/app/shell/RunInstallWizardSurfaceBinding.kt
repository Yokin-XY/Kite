package com.kite.app.shell

import android.content.Context
import android.view.View
import com.kite.app.action.KiteInstallPlanActionIntent
import com.kite.app.application.resources.ResourceFeatureGateway
import com.kite.app.feature.resources.ResourceInstallWizardPlanActionResult
import com.kite.app.feature.resources.ResourceInstallWizardSurface
import com.kite.app.feature.runsurface.RunSurfaceBinding
import com.kite.app.feature.runsurface.RunSurfaceUiState

/** Shell 适配器：组合两个 Feature，但不接管资源任务或 CardRun 生命周期。 */
internal class RunInstallWizardSurfaceBinding(
    context: Context,
    gateway: ResourceFeatureGateway,
    targetResourceId: String,
    planResourceIds: List<String>,
    onPlanAction: (
        KiteInstallPlanActionIntent,
        (ResourceInstallWizardPlanActionResult) -> Unit,
    ) -> Unit,
    onUninstallFailedResource: (String) -> Unit,
    onExit: () -> Unit,
    onLiveTickRequired: () -> Unit
) : RunSurfaceBinding {
    private val surface = ResourceInstallWizardSurface(
        context = context,
        gateway = gateway,
        targetResourceId = targetResourceId,
        planResourceIds = planResourceIds,
        onPlanAction = onPlanAction,
        onUninstallFailedResource = onUninstallFailedResource,
        onExit = onExit,
        onLiveTickRequired = onLiveTickRequired
    )

    override val root: View get() = surface.root

    override fun render(state: RunSurfaceUiState) {
        surface.reconcile()
    }

    override fun tick(now: Long): Boolean = surface.tick(now)

    override fun reconcile(): Boolean {
        surface.reconcile()
        return true
    }

    override fun dispose() {
        surface.dispose()
    }
}
