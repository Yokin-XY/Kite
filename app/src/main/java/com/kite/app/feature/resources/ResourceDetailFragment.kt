package com.kite.app.feature.resources

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.kite.app.action.KiteResourceActionSource

/** 资源详情 Feature。内容结构、媒体、滚动和动作绑定均归本页面所有。 */
internal class ResourceDetailFragment : ResourceFeatureFragment() {
    private lateinit var resourceId: String
    private var restoredScrollY = 0
    private var screen: ResourceDetailScreen? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        resourceId = requireArguments().getString(ARG_RESOURCE_ID).orEmpty()
        restoredScrollY = savedInstanceState?.getInt(STATE_SCROLL_Y) ?: 0
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ResourceDetailScreen(
            context = requireContext(),
            resourceId = resourceId,
            initialScrollY = restoredScrollY,
            onBack = { send(ResourceFeatureRequest.Back) },
            onMore = { id -> send(ResourceFeatureRequest.OpenMore(id)) },
            onRawJson = { id -> send(ResourceFeatureRequest.OpenRawJson(id)) },
            onOpenDetail = { id -> send(ResourceFeatureRequest.OpenDetail(id)) },
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
        outState.putInt(STATE_SCROLL_Y, screen?.scrollY() ?: restoredScrollY)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroyView() {
        restoredScrollY = screen?.scrollY() ?: restoredScrollY
        screen?.dispose()
        screen = null
        super.onDestroyView()
    }

    private fun submitPrimaryAction(id: String) {
        submitPrimary(
            resourceId = id,
            source = KiteResourceActionSource.Detail,
            onAccepted = { intent -> screen?.acknowledgePrimary(intent) },
            onUnavailable = { screen?.render(controller.state.value) }
        )
    }

    private fun submitSecondaryAction(id: String) {
        submitSecondary(
            resourceId = id,
            source = KiteResourceActionSource.Detail,
            onAccepted = { intent -> screen?.acknowledgeSecondary(intent) },
            onUnavailable = { screen?.render(controller.state.value) }
        )
    }

    companion object {
        private const val ARG_RESOURCE_ID = "resource_id"
        private const val STATE_SCROLL_Y = "resource_detail_scroll_y"

        fun newInstance(resourceId: String): ResourceDetailFragment =
            ResourceDetailFragment().apply {
                arguments = Bundle().apply { putString(ARG_RESOURCE_ID, resourceId) }
            }
    }
}
