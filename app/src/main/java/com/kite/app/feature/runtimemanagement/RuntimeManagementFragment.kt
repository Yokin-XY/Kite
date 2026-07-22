package com.kite.app.feature.runtimemanagement

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.activity.OnBackPressedCallback
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.kite.app.R
import com.kite.app.application.runtimemanagement.RuntimeManagementCommandPhase
import com.kite.app.application.runtimemanagement.RuntimeManagementDependenciesOwner
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/** 运行管理 Feature。收集事实与动作事务，Screen 只做可见绑定。 */
internal class RuntimeManagementFragment : Fragment() {
    private val dependencies: RuntimeManagementDependenciesOwner by lazy(LazyThreadSafetyMode.NONE) {
        requireContext().applicationContext as? RuntimeManagementDependenciesOwner
            ?: error("Application 必须提供 RuntimeManagementDependenciesOwner")
    }
    private val gateway by lazy(LazyThreadSafetyMode.NONE) { dependencies.runtimeManagementGateway }
    private val coordinator by lazy(LazyThreadSafetyMode.NONE) { dependencies.runtimeManagementCoordinator }
    private val controller by lazy(LazyThreadSafetyMode.NONE) {
        RuntimeManagementFeatureController(
            gateway,
            coordinator,
            RuntimeManagementText.from(requireContext()),
        )
    }
    private var screen: RuntimeManagementScreen? = null
    private var restoredScrollY = 0
    private var restoredScopeKey: String? = null
    private val backCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            if (screen?.navigateUp() == true) return
            isEnabled = false
            requireActivity().onBackPressedDispatcher.onBackPressed()
            isEnabled = true
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        restoredScrollY = savedInstanceState?.getInt(STATE_SCROLL_Y) ?: 0
        restoredScopeKey = savedInstanceState?.getString(STATE_SCOPE_KEY)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = RuntimeManagementScreen(
        context = requireContext(),
        initialScrollY = restoredScrollY,
        onBack = { send(RuntimeManagementRequest.Back) },
        onRefresh = { dispatch(RuntimeManagementFeatureAction.Refresh(force = true)) },
        onAction = { action -> dispatch(RuntimeManagementFeatureAction.Submit(action)) },
        initialScopeKey = restoredScopeKey,
    ).also { screen = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, backCallback)
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { controller.state.collect { state -> screen?.render(state) } }
                launch { gateway.snapshots.collect { snapshot -> controller.reconcile(snapshot) } }
                launch {
                    coordinator.commands.collectLatest { commands ->
                        controller.reconcile(gateway.currentSnapshot())
                        val deadlineAt = commands.values
                            .filter { it.phase != RuntimeManagementCommandPhase.Failed }
                            .minOfOrNull { it.deadlineAt }
                            ?: return@collectLatest
                        delay((deadlineAt - System.currentTimeMillis()).coerceAtLeast(1L))
                        coordinator.reconcile(gateway.currentSnapshot())
                        controller.reconcile(gateway.currentSnapshot())
                    }
                }
                controller.reconcile(gateway.currentSnapshot())
                controller.dispatch(
                    RuntimeManagementFeatureAction.Refresh(
                        force = arguments?.getBoolean(ARG_FORCE_REFRESH, true) ?: true
                    )
                )
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt(STATE_SCROLL_Y, screen?.scrollY() ?: restoredScrollY)
        outState.putString(STATE_SCOPE_KEY, screen?.scopeKey() ?: restoredScopeKey)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroyView() {
        restoredScrollY = screen?.scrollY() ?: restoredScrollY
        screen?.dispose()
        screen = null
        super.onDestroyView()
    }

    private fun dispatch(action: RuntimeManagementFeatureAction) {
        viewLifecycleOwner.lifecycleScope.launch {
            when (val effect = controller.dispatch(action)) {
                is RuntimeManagementFeatureEffect.OpenSurface -> send(
                    RuntimeManagementRequest.OpenSurface(
                        recipeId = effect.recipeId,
                        instanceId = effect.instanceId,
                        surface = effect.surface
                    )
                )
                is RuntimeManagementFeatureEffect.ActionRejected ->
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.runtime_management_action_rejected, effect.reason),
                        Toast.LENGTH_SHORT,
                    ).show()
                null -> Unit
            }
        }
    }

    private fun send(request: RuntimeManagementRequest) {
        RuntimeManagementResultContract.send(this, request)
    }

    companion object {
        private const val ARG_FORCE_REFRESH = "force_refresh"
        private const val STATE_SCROLL_Y = "scroll_y"
        private const val STATE_SCOPE_KEY = "scope_key"

        fun newInstance(forceRefresh: Boolean): RuntimeManagementFragment =
            RuntimeManagementFragment().apply {
                arguments = Bundle().apply { putBoolean(ARG_FORCE_REFRESH, forceRefresh) }
            }
    }
}
