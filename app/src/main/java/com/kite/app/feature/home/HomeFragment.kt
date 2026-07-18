package com.kite.app.feature.home

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.kite.app.R
import com.kite.app.application.recipes.RecipeFeatureDependenciesOwner
import com.kite.app.application.recipes.RecipeFeatureGateway
import kotlinx.coroutines.launch

/** 首页卡片 Feature。视图和页面状态留在模块内，Shell 只接收导航与动作 Effect。 */
internal class HomeFragment : Fragment() {
    private val gateway: RecipeFeatureGateway by lazy(LazyThreadSafetyMode.NONE) {
        val owner = requireContext().applicationContext as? RecipeFeatureDependenciesOwner
            ?: error("Application 必须提供 RecipeFeatureGateway")
        owner.recipeFeatureGateway
    }
    private val controller: HomeFeatureController by lazy(LazyThreadSafetyMode.NONE) {
        HomeFeatureController(
            gateway = gateway,
            initiallyBlocksUbuntuActions = arguments?.getBoolean(ARG_RUNTIME_BLOCKED, true) ?: true
        )
    }
    private var screen: HomeScreen? = null
    private var restoredPageId = HOME_PAGE_ALL
    private var restoredScrollY = 0
    private var runtimeBlocked = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        restoredPageId = savedInstanceState?.getString(STATE_PAGE_ID).orEmpty().ifBlank { HOME_PAGE_ALL }
        restoredScrollY = savedInstanceState?.getInt(STATE_SCROLL_Y) ?: 0
        runtimeBlocked = arguments?.getBoolean(ARG_RUNTIME_BLOCKED, true) ?: true
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = HomeScreen(
        context = requireContext(),
        initialPageId = restoredPageId,
        initialScrollY = restoredScrollY,
        onOpenEditor = { recipeId -> send(HomeFeatureRequest.OpenEditor(recipeId)) },
        onPrimaryAction = ::submitPrimary,
        onCreateGroup = ::showCreateGroupDialog,
        onExternalRefresh = ::refreshExternalRecipes,
        onRetry = { refreshCatalog(force = true) }
    ).also { screen = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { controller.state.collect { state -> screen?.render(state) } }
                launch {
                    gateway.changes.collect { change ->
                        controller.dispatch(
                            if (change.catalogInvalidated) {
                                HomeFeatureAction.Refresh(forceCatalogRefresh = false)
                            } else {
                                HomeFeatureAction.ReconcileRuns
                            }
                        )
                    }
                }
                controller.dispatch(
                    HomeFeatureAction.SetRuntimeBlocked(
                        runtimeBlocked
                    )
                )
                controller.dispatch(
                    if (controller.state.value.phase == HomeCatalogPhase.Idle) {
                        HomeFeatureAction.Refresh(forceCatalogRefresh = false)
                    } else {
                        HomeFeatureAction.ReconcileRuns
                    }
                )
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(STATE_PAGE_ID, screen?.selectedPageId() ?: restoredPageId)
        outState.putInt(STATE_SCROLL_Y, screen?.scrollY() ?: restoredScrollY)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroyView() {
        restoredPageId = screen?.selectedPageId() ?: restoredPageId
        restoredScrollY = screen?.scrollY() ?: restoredScrollY
        screen?.dispose()
        screen = null
        super.onDestroyView()
    }

    fun updateRuntimeBlocked(blocked: Boolean) {
        runtimeBlocked = blocked
        if (isAdded) {
            lifecycleScope.launch { controller.dispatch(HomeFeatureAction.SetRuntimeBlocked(blocked)) }
        }
    }

    private fun submitPrimary(recipeId: String) {
        screen?.acknowledge(recipeId)
        viewLifecycleOwner.lifecycleScope.launch {
            when (val effect = controller.dispatch(HomeFeatureAction.Primary(recipeId))) {
                is HomeFeatureEffect.ActionRequested ->
                    send(HomeFeatureResultContract.actionRequest(effect.request))
                is HomeFeatureEffect.ActionUnavailable -> screen?.render(controller.state.value)
                else -> Unit
            }
        }
    }

    private fun refreshCatalog(force: Boolean) {
        viewLifecycleOwner.lifecycleScope.launch {
            controller.dispatch(HomeFeatureAction.Refresh(forceCatalogRefresh = force))
        }
    }

    private fun refreshExternalRecipes() {
        viewLifecycleOwner.lifecycleScope.launch {
            when (val effect = controller.dispatch(HomeFeatureAction.RefreshExternalRecipes)) {
                is HomeFeatureEffect.ExternalRefreshCompleted ->
                    Toast.makeText(requireContext(), effect.message, Toast.LENGTH_SHORT).show()
                is HomeFeatureEffect.ActionUnavailable ->
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.home_refresh_failed, effect.reason),
                        Toast.LENGTH_SHORT
                    ).show()
                else -> Unit
            }
        }
    }

    private fun showCreateGroupDialog() {
        val input = EditText(requireContext()).apply {
            hint = getString(R.string.home_create_group_hint)
            maxLines = 1
        }
        val dialog = AlertDialog.Builder(requireContext())
            .setTitle(R.string.home_create_group_title)
            .setView(input)
            .setNegativeButton(R.string.home_create_group_cancel, null)
            .setPositiveButton(R.string.home_create_group_confirm, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = input.text?.toString().orEmpty().trim()
                if (name.isBlank()) {
                    input.error = getString(R.string.home_create_group_name_required)
                    return@setOnClickListener
                }
                viewLifecycleOwner.lifecycleScope.launch {
                    when (val effect = controller.dispatch(HomeFeatureAction.CreateGroup(name))) {
                        is HomeFeatureEffect.GroupCreated -> {
                            screen?.selectGroup(effect.group.id)
                            dialog.dismiss()
                        }
                        is HomeFeatureEffect.ActionUnavailable -> input.error = effect.reason
                        else -> Unit
                    }
                }
            }
        }
        dialog.show()
    }

    private fun send(request: HomeFeatureRequest) {
        HomeFeatureResultContract.send(this, request)
    }

    companion object {
        private const val ARG_RUNTIME_BLOCKED = "runtime_blocked"
        private const val STATE_PAGE_ID = "home_page_id"
        private const val STATE_SCROLL_Y = "home_scroll_y"

        fun newInstance(runtimeBlocked: Boolean): HomeFragment = HomeFragment().apply {
            arguments = Bundle().apply { putBoolean(ARG_RUNTIME_BLOCKED, runtimeBlocked) }
        }
    }
}
