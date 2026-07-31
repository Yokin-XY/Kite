package com.kite.app.feature.resources

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.kite.app.action.KiteResourceActionSource

/** 资源管理 Feature。队列、已获取列表、滚动与动作绑定均归本页面所有。 */
internal class ResourceManageFragment : ResourceFeatureFragment() {
    private var restoredScrollY = 0
    private var screen: ResourceManageScreen? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        restoredScrollY = savedInstanceState?.getInt(STATE_SCROLL_Y) ?: 0
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ResourceManageScreen(
            context = requireContext(),
            initialScrollY = restoredScrollY,
            onBack = { send(ResourceFeatureRequest.Back) },
            onOpenDetail = { resourceId -> send(ResourceFeatureRequest.OpenDetail(resourceId)) },
            onPrimaryAction = ::submitPrimaryAction,
            onOpenPlan = { targetResourceId ->
                send(ResourceFeatureRequest.OpenInstallPlan(targetResourceId))
            },
            onCancelPlan = { targetResourceId, resourceIds ->
                send(ResourceFeatureRequest.CancelInstallPlan(targetResourceId, resourceIds))
            },
            onCheckInstalledUpdates = { resourceIds ->
                screen?.acknowledgeUpdateCheck()
                send(ResourceFeatureRequest.CheckInstalledUpdates(resourceIds))
            },
            onRetry = { refreshResources(force = true) }
        ).also { screen = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        observeResourceState { state -> screen?.render(state) }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt(STATE_SCROLL_Y, screen?.scrollY() ?: restoredScrollY)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroyView() {
        restoredScrollY = screen?.scrollY() ?: restoredScrollY
        screen?.dispose()
        screen = null
        super.onDestroyView()
    }

    private fun submitPrimaryAction(resourceId: String) {
        submitPrimary(
            resourceId = resourceId,
            source = KiteResourceActionSource.Card,
            onAccepted = { intent -> screen?.acknowledge(resourceId, intent) },
            onUnavailable = { screen?.render(controller.state.value) }
        )
    }

    private companion object {
        const val STATE_SCROLL_Y = "resource_manage_scroll_y"
    }
}
