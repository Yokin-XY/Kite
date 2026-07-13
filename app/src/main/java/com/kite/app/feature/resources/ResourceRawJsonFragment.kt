package com.kite.app.feature.resources

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup

internal class ResourceRawJsonFragment : ResourceFeatureFragment() {
    private lateinit var resourceId: String
    private var screen: ResourceRawJsonScreen? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        resourceId = requireArguments().getString(ARG_RESOURCE_ID).orEmpty()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = ResourceRawJsonScreen(requireContext()) {
        send(ResourceFeatureRequest.Back)
    }.also { screen = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        observeResourceState { state -> screen?.render(state.item(resourceId)) }
    }

    override fun onDestroyView() {
        screen = null
        super.onDestroyView()
    }

    companion object {
        private const val ARG_RESOURCE_ID = "resource_id"

        fun newInstance(resourceId: String): ResourceRawJsonFragment =
            ResourceRawJsonFragment().apply {
                arguments = Bundle().apply { putString(ARG_RESOURCE_ID, resourceId) }
            }
    }
}
