package com.kite.app.feature.resources

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.kite.app.action.KiteResourceActionSource

/** 资源目录 Feature。视图、滚动、分类和局部绑定全部归 Fragment/Screen 所有。 */
internal class ResourcesFragment : ResourceFeatureFragment() {
    private var screen: ResourceCatalogScreen? = null
    private var restoredTabId = RESOURCE_HOME_TAB_ALL
    private var restoredScrollY = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        restoredTabId = savedInstanceState?.getString(STATE_TAB_ID).orEmpty().ifBlank { RESOURCE_HOME_TAB_ALL }
        restoredScrollY = savedInstanceState?.getInt(STATE_SCROLL_Y) ?: 0
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ResourceCatalogScreen(
            context = requireContext(),
            initialTabId = restoredTabId,
            initialScrollY = restoredScrollY,
            onSearch = { send(ResourceFeatureRequest.OpenSearch("")) },
            onManage = { send(ResourceFeatureRequest.OpenManage) },
            onOpenDetail = { resourceId -> send(ResourceFeatureRequest.OpenDetail(resourceId)) },
            onPrimaryAction = ::submitPrimaryAction,
            onSecondaryAction = ::submitSecondaryAction,
            onRetry = { refreshResources(force = true) }
        ).also { screen = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        observeResourceState { state -> screen?.render(state) }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(STATE_TAB_ID, screen?.selectedTabId() ?: restoredTabId)
        outState.putInt(STATE_SCROLL_Y, screen?.scrollY() ?: restoredScrollY)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroyView() {
        restoredTabId = screen?.selectedTabId() ?: restoredTabId
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

    private fun submitSecondaryAction(resourceId: String) {
        submitSecondary(
            resourceId = resourceId,
            source = KiteResourceActionSource.Card,
            onAccepted = { intent -> screen?.acknowledge(resourceId, intent) },
            onUnavailable = { screen?.render(controller.state.value) }
        )
    }

    private companion object {
        const val STATE_TAB_ID = "resource_tab_id"
        const val STATE_SCROLL_Y = "resource_scroll_y"
    }
}
