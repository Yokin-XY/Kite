package com.kite.app.feature.resources

import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.kite.app.action.KiteResourceActionIntent
import com.kite.app.action.KiteResourceActionSource
import com.kite.app.application.resources.ResourceFeatureDependenciesOwner
import com.kite.app.application.resources.ResourceFeatureGateway
import kotlinx.coroutines.launch

/** 资源页面共享的生命周期接线；不持有任何具体页面 View。 */
internal abstract class ResourceFeatureFragment : Fragment() {
    protected val gateway: ResourceFeatureGateway by lazy(LazyThreadSafetyMode.NONE) {
        val owner = requireContext().applicationContext as? ResourceFeatureDependenciesOwner
            ?: error("Application 必须提供 ResourceFeatureGateway")
        owner.resourceFeatureGateway
    }
    protected val controller: ResourceFeatureController by lazy(LazyThreadSafetyMode.NONE) {
        ResourceFeatureController(gateway)
    }

    protected fun observeResourceState(render: (ResourceFeatureUiState) -> Unit) {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { controller.state.collect(render) }
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
                controller.dispatch(
                    if (controller.state.value.phase == ResourceCatalogPhase.Idle) {
                        ResourceFeatureAction.Refresh(forceCatalogRefresh = false)
                    } else {
                        ResourceFeatureAction.ReconcileFacts
                    }
                )
            }
        }
    }

    protected fun refreshResources(force: Boolean) {
        viewLifecycleOwner.lifecycleScope.launch {
            controller.dispatch(ResourceFeatureAction.Refresh(forceCatalogRefresh = force))
        }
    }

    protected fun submitPrimary(
        resourceId: String,
        source: KiteResourceActionSource,
        onAccepted: (KiteResourceActionIntent) -> Unit,
        onUnavailable: () -> Unit
    ) {
        val item = controller.state.value.item(resourceId) ?: return
        onAccepted(item.primaryIntent)
        viewLifecycleOwner.lifecycleScope.launch {
            when (val effect = controller.dispatch(ResourceFeatureAction.Primary(resourceId, source))) {
                is ResourceFeatureEffect.ActionRequested ->
                    send(ResourceFeatureRequest.SubmitAction(effect.request))
                is ResourceFeatureEffect.ActionUnavailable -> onUnavailable()
                null -> Unit
            }
        }
    }

    protected fun submitSecondary(
        resourceId: String,
        source: KiteResourceActionSource,
        onAccepted: (KiteResourceActionIntent) -> Unit,
        onUnavailable: () -> Unit
    ) {
        val item = controller.state.value.item(resourceId) ?: return
        val intent = item.secondaryIntent ?: return onUnavailable()
        onAccepted(intent)
        viewLifecycleOwner.lifecycleScope.launch {
            when (val effect = controller.dispatch(ResourceFeatureAction.Secondary(resourceId, source))) {
                is ResourceFeatureEffect.ActionRequested ->
                    send(ResourceFeatureRequest.SubmitAction(effect.request))
                is ResourceFeatureEffect.ActionUnavailable -> onUnavailable()
                null -> Unit
            }
        }
    }

    protected fun submitExplicit(
        resourceId: String,
        intent: KiteResourceActionIntent,
        source: KiteResourceActionSource,
        onAccepted: (KiteResourceActionIntent) -> Unit,
        onUnavailable: () -> Unit
    ) {
        onAccepted(intent)
        viewLifecycleOwner.lifecycleScope.launch {
            when (val effect = controller.dispatch(ResourceFeatureAction.Explicit(resourceId, intent, source))) {
                is ResourceFeatureEffect.ActionRequested -> send(ResourceFeatureRequest.SubmitAction(effect.request))
                is ResourceFeatureEffect.ActionUnavailable -> onUnavailable()
                null -> Unit
            }
        }
    }

    protected fun send(request: ResourceFeatureRequest) {
        ResourceFeatureResultContract.send(this, request)
    }
}
