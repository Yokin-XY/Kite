package com.kite.app.feature.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.kite.app.application.settings.SettingsFeatureDependenciesOwner
import kotlinx.coroutines.launch

/** 设置首页。页面只投影 Gateway 快照，系统页面与主壳副作用通过 Result 上交。 */
internal class SettingsFragment : Fragment() {
    private val controller: SettingsFeatureController by lazy(LazyThreadSafetyMode.NONE) {
        val owner = requireContext().applicationContext as? SettingsFeatureDependenciesOwner
            ?: error("Application 必须提供 SettingsGateway")
        SettingsFeatureController(owner.settingsFeatureGateway, lifecycleScope)
    }
    private var screen: SettingsScreen? = null
    private val navigationUiState: SettingsNavigationUiState by lazy(LazyThreadSafetyMode.NONE) {
        ViewModelProvider(requireActivity())[SettingsNavigationUiState::class.java]
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = SettingsScreen(
        context = requireContext(),
        initialState = controller.state.value,
        initialScrollY = navigationUiState.indexScrollY,
        onBack = { send(SettingsFeatureRequest.Back) },
        onOpenCategory = { destination ->
            send(SettingsFeatureRequest.OpenCategory(destination))
        },
    ).also { screen = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                controller.state.collect { state -> screen?.render(state) }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    override fun onDestroyView() {
        screen?.let { navigationUiState.indexScrollY = it.currentScrollY() }
        screen = null
        super.onDestroyView()
    }

    fun refresh() {
        if (!isAdded) return
        lifecycleScope.launch { controller.dispatch(SettingsFeatureAction.Refresh) }
    }

    private fun send(request: SettingsFeatureRequest) {
        SettingsFeatureResultContract.send(this, request)
    }
}

/** 只保存设置目录的界面位置，不承载任何偏好或运行事实。 */
internal class SettingsNavigationUiState : ViewModel() {
    var indexScrollY: Int = 0
}
