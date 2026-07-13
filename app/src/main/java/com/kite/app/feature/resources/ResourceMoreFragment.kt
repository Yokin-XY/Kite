package com.kite.app.feature.resources

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.kite.app.application.runs.RunHistoryDependenciesOwner
import com.kite.app.application.runs.RunHistoryGateway
import com.kite.app.resources.KiteResourceInstallRecipes
import com.kite.app.resources.KiteResourceInstallStore
import com.kite.app.run.CardRunHistoryEntry
import kotlinx.coroutines.launch

internal class ResourceMoreFragment : ResourceFeatureFragment() {
    private lateinit var resourceId: String
    private val historyGateway: RunHistoryGateway by lazy(LazyThreadSafetyMode.NONE) {
        val owner = requireContext().applicationContext as? RunHistoryDependenciesOwner
            ?: error("Application 必须提供 RunHistoryGateway")
        owner.runHistoryGateway
    }
    private var latestState = ResourceFeatureUiState()
    private var history: List<CardRunHistoryEntry> = emptyList()
    private var screen: ResourceMoreScreen? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        resourceId = requireArguments().getString(ARG_RESOURCE_ID).orEmpty()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = ResourceMoreScreen(
        context = requireContext(),
        onBack = { send(ResourceFeatureRequest.Back) },
        onCreateHomeCard = { send(ResourceFeatureRequest.CreateHomeCard(resourceId)) },
        onOpenHistory = { historyId ->
            send(ResourceFeatureRequest.OpenRunHistory(resourceId, installRecipeId(), historyId))
        }
    ).also { screen = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        observeResourceState { state ->
            latestState = state
            render()
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { historyGateway.changes.collect { refreshHistory() } }
                refreshHistory()
            }
        }
    }

    override fun onDestroyView() {
        screen = null
        super.onDestroyView()
    }

    private fun refreshHistory() {
        history = historyGateway.historyForRecipe(installRecipeId())
        render()
    }

    private fun render() {
        screen?.render(latestState.item(resourceId), history)
    }

    private fun installRecipeId(): String = KiteResourceInstallRecipes.recipeId(
        resourceId,
        KiteResourceInstallStore.OP_INSTALL
    )

    companion object {
        private const val ARG_RESOURCE_ID = "resource_id"

        fun newInstance(resourceId: String): ResourceMoreFragment = ResourceMoreFragment().apply {
            arguments = Bundle().apply { putString(ARG_RESOURCE_ID, resourceId) }
        }
    }
}
