package com.kite.app.feature.resources

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.kite.app.action.KiteResourceActionSource
import com.kite.app.application.resources.ResourceFeatureDependenciesOwner
import com.kite.app.application.resources.ResourceFeatureGateway
import kotlinx.coroutines.launch

/** 资源目录 Feature。视图、滚动、分类和局部绑定全部归 Fragment/Screen 所有。 */
class ResourcesFragment : Fragment() {
    private val gateway: ResourceFeatureGateway by lazy(LazyThreadSafetyMode.NONE) {
        val owner = requireContext().applicationContext as? ResourceFeatureDependenciesOwner
            ?: error("Application 必须提供 ResourceFeatureGateway")
        owner.resourceFeatureGateway
    }
    private val controller: ResourceFeatureController by lazy(LazyThreadSafetyMode.NONE) {
        ResourceFeatureController(gateway)
    }
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
            onRetry = { refresh(force = true) }
        ).also { screen = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    controller.state.collect { state -> screen?.render(state) }
                }
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
            }
        }
        if (controller.state.value.phase == ResourceCatalogPhase.Idle) {
            refresh(force = false)
        } else {
            viewLifecycleOwner.lifecycleScope.launch {
                controller.dispatch(ResourceFeatureAction.ReconcileFacts)
            }
        }
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

    private fun refresh(force: Boolean) {
        viewLifecycleOwner.lifecycleScope.launch {
            controller.dispatch(ResourceFeatureAction.Refresh(forceCatalogRefresh = force))
        }
    }

    private fun submitPrimaryAction(resourceId: String) {
        val item = controller.state.value.item(resourceId) ?: return
        screen?.acknowledge(resourceId, item.primaryIntent)
        viewLifecycleOwner.lifecycleScope.launch {
            when (val effect = controller.dispatch(
                ResourceFeatureAction.Primary(resourceId, KiteResourceActionSource.Card)
            )) {
                is ResourceFeatureEffect.ActionRequested ->
                    send(ResourceFeatureRequest.SubmitAction(effect.request))
                is ResourceFeatureEffect.ActionUnavailable ->
                    screen?.render(controller.state.value)
                null -> Unit
            }
        }
    }

    private fun send(request: ResourceFeatureRequest) {
        ResourceFeatureResultContract.send(this, request)
    }

    private companion object {
        const val STATE_TAB_ID = "resource_tab_id"
        const val STATE_SCROLL_Y = "resource_scroll_y"
    }
}
