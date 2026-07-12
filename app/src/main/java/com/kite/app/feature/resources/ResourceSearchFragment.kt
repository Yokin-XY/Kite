package com.kite.app.feature.resources

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.kite.app.action.KiteResourceActionSource

/** 资源搜索 Feature。查询、键盘、滚动和结果绑定均归本页面所有。 */
internal class ResourceSearchFragment : ResourceFeatureFragment() {
    private var screen: ResourceSearchScreen? = null
    private var restoredQuery = ""
    private var restoredScrollY = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        restoredQuery = savedInstanceState?.getString(STATE_QUERY)
            ?: arguments?.getString(ARG_QUERY).orEmpty()
        restoredScrollY = savedInstanceState?.getInt(STATE_SCROLL_Y) ?: 0
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ResourceSearchScreen(
            context = requireContext(),
            initialQuery = restoredQuery,
            initialScrollY = restoredScrollY,
            onBack = { send(ResourceFeatureRequest.Back) },
            onOpenDetail = { resourceId -> send(ResourceFeatureRequest.OpenDetail(resourceId)) },
            onPrimaryAction = ::submitPrimaryAction,
            onRetry = { refreshResources(force = true) }
        ).also { screen = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        observeResourceState { state -> screen?.render(state) }
        if (savedInstanceState == null) screen?.focusInput()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(STATE_QUERY, screen?.query() ?: restoredQuery)
        outState.putInt(STATE_SCROLL_Y, screen?.scrollY() ?: restoredScrollY)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroyView() {
        restoredQuery = screen?.query() ?: restoredQuery
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

    companion object {
        private const val ARG_QUERY = "initial_query"
        private const val STATE_QUERY = "resource_search_query"
        private const val STATE_SCROLL_Y = "resource_search_scroll_y"

        fun newInstance(initialQuery: String): ResourceSearchFragment =
            ResourceSearchFragment().apply {
                arguments = Bundle().apply { putString(ARG_QUERY, initialQuery) }
            }
    }
}
